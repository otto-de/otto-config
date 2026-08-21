package vault

import (
	"context"
	"crypto/sha256"
	"encoding/base64"
	"encoding/hex"
	"fmt"
	"net/http"
	"strings"
	"time"

	"github.com/aws/aws-sdk-go-v2/aws"
	v4 "github.com/aws/aws-sdk-go-v2/aws/signer/v4"
	awsconfig "github.com/aws/aws-sdk-go-v2/config"
	"github.com/aws/aws-sdk-go-v2/service/sts"
	vaultapi "github.com/hashicorp/vault/api"
)

const (
	stsRequestBody    = "Action=GetCallerIdentity&Version=2011-06-15"
	iamServerIDHeader = "X-Vault-AWS-IAM-Server-ID"
	vaultLoginSession = "vault-login-session"
)

// assumeRoleIAMAuth authenticates against Vault's AWS auth backend using
// credentials obtained via sts:AssumeRole, mirroring Java's
// VaultAwsAuthenticator when otto.config.hashicorp.vault.auth.aws.role.arn
// is set. The official vault/api aws auth method can only sign with the
// ambient credential chain, so cross-account role setups need this.
type assumeRoleIAMAuth struct {
	role        string
	roleARN     string
	region      string
	headerValue string
}

var _ vaultapi.AuthMethod = (*assumeRoleIAMAuth)(nil)

func (a *assumeRoleIAMAuth) Login(ctx context.Context, client *vaultapi.Client) (*vaultapi.Secret, error) {
	var opts []func(*awsconfig.LoadOptions) error
	if a.region != "" {
		opts = append(opts, awsconfig.WithRegion(a.region))
	}
	cfg, err := awsconfig.LoadDefaultConfig(ctx, opts...)
	if err != nil {
		return nil, fmt.Errorf("unable to load AWS configuration: %w", err)
	}
	region := a.region
	if region == "" {
		region = cfg.Region
	}
	if region == "" {
		return nil, fmt.Errorf("unable to determine AWS region for Vault AWS auth; set otto.config.hashicorp.vault.auth.aws.region")
	}

	assumed, err := sts.NewFromConfig(cfg).AssumeRole(ctx, &sts.AssumeRoleInput{
		RoleArn:         aws.String(a.roleARN),
		RoleSessionName: aws.String(vaultLoginSession),
	})
	if err != nil {
		return nil, fmt.Errorf("failed to load credentials from role %s: %w", a.roleARN, err)
	}

	payload, err := a.signedLoginPayload(ctx, region, aws.Credentials{
		AccessKeyID:     aws.ToString(assumed.Credentials.AccessKeyId),
		SecretAccessKey: aws.ToString(assumed.Credentials.SecretAccessKey),
		SessionToken:    aws.ToString(assumed.Credentials.SessionToken),
	})
	if err != nil {
		return nil, err
	}

	return client.Logical().WriteWithContext(ctx, "auth/aws/login", payload)
}

// signedLoginPayload builds the Vault AWS login payload from a SigV4-signed
// STS GetCallerIdentity request, which Vault replays to verify the caller.
func (a *assumeRoleIAMAuth) signedLoginPayload(ctx context.Context, region string, creds aws.Credentials) (map[string]any, error) {
	stsURL := "https://sts." + region + ".amazonaws.com/"

	req, err := http.NewRequestWithContext(ctx, http.MethodPost, stsURL, strings.NewReader(stsRequestBody))
	if err != nil {
		return nil, err
	}
	req.ContentLength = int64(len(stsRequestBody))
	req.Header.Set("Content-Type", "application/x-www-form-urlencoded; charset=utf-8")
	if a.headerValue != "" {
		req.Header.Set(iamServerIDHeader, a.headerValue)
	}

	sum := sha256.Sum256([]byte(stsRequestBody))
	if err := v4.NewSigner().SignHTTP(ctx, creds, req, hex.EncodeToString(sum[:]), "sts", region, time.Now()); err != nil {
		return nil, fmt.Errorf("failed to sign STS request: %w", err)
	}

	// Host is signed but lives on req.Host rather than req.Header, and Vault
	// needs it to reconstruct the request.
	headers := []string{"Host:" + req.Host}
	for name, values := range req.Header {
		for _, value := range values {
			headers = append(headers, name+":"+value)
		}
	}

	return map[string]any{
		"role":                    a.role,
		"iam_http_request_method": http.MethodPost,
		"iam_request_url":         base64.StdEncoding.EncodeToString([]byte(stsURL)),
		"iam_request_body":        base64.StdEncoding.EncodeToString([]byte(stsRequestBody)),
		"iam_request_headers":     headers,
	}, nil
}
