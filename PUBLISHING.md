# Publishing otto-config

otto-config is released via [JReleaser](https://jreleaser.org/), which uploads to:

- **Maven Central** (via the new Central Portal: `https://central.sonatype.com`)
- **GitHub Packages** (`https://maven.pkg.github.com/otto-de/otto-config`)

The release also creates a Git tag and a GitHub Release automatically.

## Release Process (Recommended)

### 1. Prepare the release

Update the version in [build.gradle](build.gradle) — remove the `-SNAPSHOT` suffix:

```gradle
def otto_config_version = "0.2.0"  // was "0.2.0-SNAPSHOT"
```

Add an entry to [CHANGELOG.md](CHANGELOG.md) describing what's changed:

```markdown
## 0.2.0
* New feature X
* Bugfix Y
```

Commit and push:

```bash
git add build.gradle CHANGELOG.md
git commit -m "chore: prepare release 0.2.0"
git push
```

### 2. Trigger the Release workflow

```bash
gh workflow run release.yml -f version=0.2.0
```

Or via the GitHub UI: **Actions → Release → Run workflow**, and enter the version.

The [`.github/workflows/release.yml`](.github/workflows/release.yml) workflow will:

1. Validate that the input `version` matches `otto_config_version` in `build.gradle`
2. Build release notes from the matching `## <version>` section of `CHANGELOG.md`
3. Run `./gradlew jreleaserConfig` to validate the JReleaser configuration
4. Run `./gradlew check` (all tests)
5. Run `./gradlew publish` — stages artifacts (jar, sources, javadoc, pom) to `build/staging-deploy`
6. Run `./gradlew jreleaserFullRelease` — signs artifacts, creates the git tag (`v0.2.0`),
   creates the GitHub Release, and uploads to GitHub Packages + Maven Central Portal

### 3. Bump to next SNAPSHOT

After a successful release, update `build.gradle` to the next development version:

```gradle
def otto_config_version = "0.3.0-SNAPSHOT"
```

Commit and push.

## Credentials

All publishing credentials are configured as **organizational secrets** in the `otto-de` GitHub org
and are automatically available to the release workflow:

| Secret                   | Purpose                                    |
| ------------------------ | ------------------------------------------ |
| `GITHUB_TOKEN`           | Auto-provided by Actions; publishes to GitHub Packages & creates releases |
| `GPG_SECRET_KEY`         | ASCII-armored GPG private key used to sign artifacts |
| `GPG_PUBLIC_KEY`         | ASCII-armored GPG public key |
| `GPG_PASSPHRASE`         | Passphrase for the GPG key |
| `MAVENCENTRAL_USERNAME`  | User token username from https://central.sonatype.com |
| `MAVENCENTRAL_PASSWORD`  | User token password from https://central.sonatype.com |

## Version Rules

- ⚠️ Release versions must be `X.Y.Z` — no `-SNAPSHOT` suffix
- ⚠️ Git tags use a `v` prefix (`v0.2.0`); artifacts do not (`otto-config-0.2.0.jar`) — JReleaser handles this
- ⚠️ Never put `v` in `build.gradle`
- ⚠️ Maven Central releases are **immutable** — a released version cannot be overwritten.
  If a release is bad, bump to the next patch (`0.2.1`) and re-release.

## Local Testing

### Publish to local Maven repo

```bash
./gradlew publishToMavenLocal
ls ~/.m2/repository/de/otto/config/otto-config/$(grep otto_config_version build.gradle | grep -o '"[^"]*"' | tr -d '"')/
```

You should see the jar, sources jar, javadoc jar, pom, and Gradle module file.

### Publish to the staging directory

```bash
./gradlew clean publish
ls build/staging-deploy/de/otto/config/otto-config/<version>/
```

This mirrors what CI does before handing off to JReleaser.

### Validate the JReleaser config

Requires the JReleaser env vars listed above (they can be dummy values for a syntax check):

```bash
JRELEASER_GITHUB_TOKEN=dummy \
JRELEASER_GPG_SECRET_KEY=dummy \
JRELEASER_GPG_PUBLIC_KEY=dummy \
JRELEASER_GPG_PASSPHRASE=dummy \
JRELEASER_MAVENCENTRAL_USERNAME=dummy \
JRELEASER_MAVENCENTRAL_PASSWORD=dummy \
./gradlew jreleaserConfig
```

## Retrying a Failed Release

If `jreleaserFullRelease` failed **before** the git tag was created — just fix the issue and re-run.

If the git tag was created but publishing failed:

```bash
# Delete the tag locally and remotely
git tag -d v0.2.0
git push --delete origin v0.2.0
# Delete the GitHub Release from the Releases page if it was created
# Fix the issue, then re-run the workflow
```

If artifacts were published to Maven Central: you cannot overwrite them. Bump the patch version and release again.

The failed run's `build/jreleaser/trace.log` is uploaded as a workflow artifact for debugging.

## Consuming the Published Artifact

### From Maven Central

```gradle
repositories { mavenCentral() }
dependencies { implementation 'de.otto.config:otto-config:0.2.0' }
```

### From GitHub Packages

```gradle
repositories {
    maven {
        url = uri("https://maven.pkg.github.com/otto-de/otto-config")
        credentials {
            username = project.findProperty("gpr.user") ?: System.getenv("GITHUB_ACTOR")
            password = project.findProperty("gpr.token") ?: System.getenv("GITHUB_TOKEN")
        }
    }
}
dependencies { implementation 'de.otto.config:otto-config:0.2.0' }
```

## References

- [JReleaser documentation](https://jreleaser.org/guide/latest/)
- [Maven Central Portal](https://central.sonatype.com/)
- [edison-microservice release setup](https://github.com/otto-de/edison-microservice) — the reference implementation this project follows
