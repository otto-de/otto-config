package event

import "testing"

func TestParse_AppConfigNativeEvent(t *testing.T) {
	body := []byte(`{
		"source": "aws.appconfig",
		"detail-type": "AWS AppConfig Deployment Status",
		"detail": {
			"application-id": "app-1",
			"application-name": "my-app",
			"environment-id": "env-1",
			"environment-name": "prod",
			"configuration-profile-id": "prof-1",
			"configuration-profile-name": "properties"
		}
	}`)

	evt, err := Parse(body)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	appEvt, ok := evt.(AppConfigDeploymentEvent)
	if !ok {
		t.Fatalf("expected AppConfigDeploymentEvent, got %T", evt)
	}
	if appEvt.ApplicationName != "my-app" || appEvt.ConfigProfileName != "properties" {
		t.Fatalf("unexpected fields: %+v", appEvt)
	}
	if appEvt.EventSource() != "aws.appconfig" {
		t.Fatalf("unexpected EventSource(): %q", appEvt.EventSource())
	}
}

func TestParse_AppConfigCloudTrailEvent(t *testing.T) {
	body := []byte(`{
		"source": "aws.appconfig",
		"detail-type": "AWS API Call via CloudTrail",
		"detail": {
			"requestParameters": {
				"applicationId": "app-1",
				"environmentId": "env-1",
				"configurationProfileId": "prof-1"
			},
			"responseElements": {
				"configurationName": "properties"
			}
		}
	}`)

	evt, err := Parse(body)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	appEvt, ok := evt.(AppConfigDeploymentEvent)
	if !ok {
		t.Fatalf("expected AppConfigDeploymentEvent, got %T", evt)
	}
	if appEvt.ApplicationID != "app-1" || appEvt.ConfigProfileName != "properties" {
		t.Fatalf("unexpected fields: %+v", appEvt)
	}
}

func TestParse_SecretsManagerEvent(t *testing.T) {
	body := []byte(`{
		"source": "aws.secretsmanager",
		"detail-type": "AWS Service Event via CloudTrail",
		"detail": {"requestParameters": {"secretId": "my-secret"}}
	}`)

	evt, err := Parse(body)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	secretEvt, ok := evt.(SecretsManagerChangeEvent)
	if !ok {
		t.Fatalf("expected SecretsManagerChangeEvent, got %T", evt)
	}
	if secretEvt.SecretID != "my-secret" {
		t.Fatalf("unexpected SecretID: %q", secretEvt.SecretID)
	}
}

func TestParse_SsmEvent(t *testing.T) {
	body := []byte(`{
		"source": "aws.ssm",
		"detail-type": "Parameter Store Change",
		"detail": {"name": "/my/param", "operation": "Update"}
	}`)

	evt, err := Parse(body)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	ssmEvt, ok := evt.(SsmParameterChangeEvent)
	if !ok {
		t.Fatalf("expected SsmParameterChangeEvent, got %T", evt)
	}
	if ssmEvt.ParameterName != "/my/param" {
		t.Fatalf("unexpected ParameterName: %q", ssmEvt.ParameterName)
	}
}

func TestParse_UnknownSourceFallsBack(t *testing.T) {
	body := []byte(`{"source": "some.other.service", "detail-type": "Something", "detail": {}}`)

	evt, err := Parse(body)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if _, ok := evt.(UnknownChangeEvent); !ok {
		t.Fatalf("expected UnknownChangeEvent, got %T", evt)
	}
}

func TestParse_InvalidJSON(t *testing.T) {
	if _, err := Parse([]byte("not json")); err == nil {
		t.Fatal("expected an error for invalid JSON")
	}
}
