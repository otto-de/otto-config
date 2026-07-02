# Local demo infrastructure (moto + Vault)

This directory brings up a fully offline replacement for the real AWS
account + corporate Vault that the demo services normally use. It runs:

| Service | Image | Port | Purpose |
|---|---|---|---|
| `moto` | `motoserver/moto:latest` | `5000` | Mocks SecretsManager, SSM, SQS, EventBridge and STS. |
| `vault` | `hashicorp/vault:latest` | `8200` | Vault in dev mode (root token: `myroot`). |
| `appconfigdata-stub` | `eclipse-temurin:21-jdk` | `5001` | JDK-only Java HTTP server (single-file source, no build) that mimics the AWS AppConfigData data plane and serves the JSON files under `../terraform/`. Moto does not implement `appconfigdata`, so the SDK is pointed here for that one service via `AWS_ENDPOINT_URL_APPCONFIGDATA`. |
| `moto-init` | `amazon/aws-cli:latest` | – | Seeds moto with the Secrets Manager secret, SSM parameters, the SQS change-notification queue and the `otto-config-feature-toggles` S3 bucket (populated from the `on.*` / `off.*` marker files in `./s3-toggles/`). |
| `vault-init` | `hashicorp/vault:latest` | – | Seeds Vault with the KV mount, policy, AppRole and demo secret; writes `role_id` / `secret_id` and AWS-endpoint env vars into `./.env`. |

## Quick start

Run **every** command below from the repository root: the `./gradlew`
wrapper lives there, and the paths used here (`demo/local/...`) are
relative to it.

```bash
# 1. Start moto + vault and seed them
docker compose -f demo/local/docker-compose.yml up -d
docker compose -f demo/local/docker-compose.yml logs -f moto-init vault-init
#    ^ Ctrl-C once both print "bootstrap done."

# 2. Export the generated credentials + AWS endpoint override
source ./demo/local/.env

# 3. Run one of the demos (it talks to moto/vault, not real AWS)
./gradlew :demo:spring:bootRun  --args='--spring.profiles.active=moto'
# or
./gradlew :demo:helidon:run     -Pmp.config.profile=moto
# or
./gradlew :demo:java:run

# 4. Tear it all down
docker compose -f demo/local/docker-compose.yml down -v
```

## How the demo picks up the mocks

The AWS SDK v2 used by Otto Config natively honours the `AWS_ENDPOINT_URL`
environment variable, so pointing every AWS service at moto only needs the
following exports (all written to `./.env` by `init-vault.sh`):

```
AWS_ENDPOINT_URL=http://localhost:5000              # everything -> moto
AWS_ENDPOINT_URL_APPCONFIGDATA=http://localhost:5001 # AppConfigData -> stub
AWS_ACCESS_KEY_ID=test
AWS_SECRET_ACCESS_KEY=test
AWS_REGION=eu-central-1
```

The service-specific `AWS_ENDPOINT_URL_APPCONFIGDATA` takes precedence
over the generic `AWS_ENDPOINT_URL` for the AppConfigData client only,
so AppConfig data-plane traffic goes to the stub while every other
service still hits moto.

## The AppConfigData stub

`appconfigdata-stub/AppConfigDataStub.java` is a self-contained,
JDK-only implementation of the two endpoints that Otto Config calls:

* `POST /configurationsessions` — returns an opaque token that encodes
  the requested profile (`properties` or `toggles`).
* `GET /configuration?configuration_token=…` — returns the raw JSON
  content of the mounted file, or an empty body on subsequent polls
  when the file hasn't changed (which Otto Config treats as "no
  update").

It runs via JEP 330 single-file source launch (`java AppConfigDataStub.java`)
inside a stock `eclipse-temurin:21-jdk` container — no build step, no
third-party dependencies. Edit the JSON files under
`../terraform/appconfig_{properties,toggles}.json` and the next Otto
Config poll will pick up the change automatically.

Vault-specific settings (URL, `approle` auth, generated `role_id` /
`secret_id`) come from:

* `demo/spring/src/main/resources/application-moto.properties`
* `demo/helidon/src/main/resources/META-INF/microprofile-config-moto.properties`
* `$VAULT_ROLE_ID` / `$VAULT_SECRET_ID` exported by `./.env`.

> **Why `moto` and not `local`?** Otto Config's `CoreSourceFactory`
> treats the profile names `local`, `test` and `integration-test` as a
> signal to bypass all AWS/Vault sources and read from the local
> `properties.json` file instead. Running against the mocked services
> requires a profile name outside that set.

## Live-editing AppConfig via the stub

The `../terraform/` directory is bind-mounted read-only into the stub
container at `/data`, so editing `appconfig_properties.json` /
`appconfig_toggles.json` on the host changes what the stub serves on the
*next* Otto Config poll.

> **Why mount the parent directory and not the individual files?** On
> macOS Docker Desktop, single-file bind mounts pin the file's original
> inode: tools like `sed -i` (or any editor that atomically replaces the
> file) swap the inode on the host, and the container keeps serving the
> stale content. Mounting the parent directory sidesteps that.

Try it:

```bash
# 1. Start everything and run the Spring demo (from the repository root):
docker compose -f demo/local/docker-compose.yml up -d
source ./demo/local/.env
./gradlew :demo:spring:bootRun --args='--spring.profiles.active=moto'
```

Every 30 s the demo logs a line like:

```
DemoService : Property value for myKey1: myValue
DemoService : Toggle value for logging_enabled: false
```

In a second shell, edit the properties or toggles file and observe:

```bash
# --- change a property ---
sed -i.bak 's/"myKey1": "myValue"/"myKey1": "hello-from-stub"/' \
    demo/terraform/appconfig_properties.json

# --- flip the feature toggle (rewrite the file with jq or a small script;
#     the multi-line JSON makes `sed` brittle here) ---
python3 -c "import json,pathlib; p=pathlib.Path('demo/terraform/appconfig_toggles.json'); d=json.loads(p.read_text()); d['values']['logging_enabled']['enabled']=True; p.write_text(json.dumps(d, indent=2))"

# (Optional) trigger an immediate refresh instead of waiting up to 5 min
# for the background scheduler — this pushes an EventBridge-shaped event
# into the moto SQS queue that Otto Config polls every 10 s.
demo/local/notify-change.sh appconfig properties
demo/local/notify-change.sh appconfig toggles
```

Within ~10–40 s of running `notify-change.sh` the demo's next scheduled
`load()` will print the new values:

```
DemoService : Property value for myKey1: hello-from-stub
DemoService : Toggle value for logging_enabled: true
```

> **Only run one demo instance at a time.** All demo processes long-poll
> the same `otto-config-config-changes` SQS queue, and each SQS message
> is delivered to a single consumer. If you have leftover `bootRun`
> processes from earlier runs, they will steal change events from your
> current session — kill them first (`ps aux | grep spring:bootRun`).

Under the hood:

1. The bind mount means the stub sees the new file mtime immediately.
2. `notify-change.sh` sends an `aws.appconfig` EventBridge event to the
   `otto-config-config-changes` SQS queue in moto.
3. Otto Config's `AwsChangeEventListener` (polling every 10 s) picks up
   the message, matches it to the `AppConfigSource` for that profile,
   and calls `refresh()` on it.
4. `AppConfigSource` re-hits the stub; the stub sees a newer mtime than
   the last poll and returns the updated JSON content.
5. `@PropertyValue` / `@Value` bindings in `DemoService` observe the
   new values on the next scheduled `load()`.

The same trick works for `secrets` and `ssm`:

```bash
# Change a secret, then notify:
aws --endpoint-url http://localhost:5000 secretsmanager put-secret-value \
    --secret-id otto-config \
    --secret-string '{"some_secret":"rotated!","some_other_secret":"still there"}'
demo/local/notify-change.sh secrets

# Change an SSM parameter, then notify:
aws --endpoint-url http://localhost:5000 ssm put-parameter --overwrite \
    --name /search/develop/otto-config/config/some_ssm_value \
    --type String --value "changed-in-ssm"
demo/local/notify-change.sh ssm /search/develop/otto-config/config/some_ssm_value
```

## Re-seeding the mocks

`init-moto.sh` deletes and re-creates every resource, so simply running

```bash
docker compose -f demo/local/docker-compose.yml up -d --force-recreate moto-init
```

resets the Secrets Manager secret, SSM parameters and SQS queue.

The AppConfig properties/toggles are read live from
`../terraform/appconfig_properties.json` and
`../terraform/appconfig_toggles.json` by the stub — just edit the files
and the next Otto Config refresh cycle will pick up the changes.
