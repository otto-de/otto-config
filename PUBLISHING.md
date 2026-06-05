# Publishing otto-config to GitHub Packages and Maven Central

This document describes how to publish the otto-config library to GitHub Packages and Maven Central using Gradle's standard maven-publish plugin.

## Overview

The publishing setup uses:
- **Gradle maven-publish plugin** for artifact publishing
- **Gradle signing plugin** for artifact signing
- **GitHub Packages** for snapshot and release artifacts
- **Maven Central** for release artifacts (via Sonatype OSSRH)
- **GitHub Actions** for CI/CD automation

## Prerequisites

### 1. GPG Key Setup

You need a GPG key for signing artifacts.

#### Generate a new GPG key:
```bash
gpg --full-generate-key
```

Follow the prompts:
- Select RSA and RSA
- Key size: 4096 bits
- Expiration: 0 (no expiration) or your preference
- Use your real name and email associated with your GitHub account

#### Export your GPG keys:
```bash
# Find your key ID
gpg --list-secret-keys --keyid-format=long

# Export private key in ASCII-armored format
gpg --armor --export-secret-keys YOUR_KEY_ID

# The output should start with -----BEGIN PGP PRIVATE KEY BLOCK-----
# Copy the entire output including the header and footer
```

#### Get your passphrase ready
You'll need the passphrase you set when creating the GPG key.

### 2. GitHub Token

Create a GitHub personal access token:
1. Go to https://github.com/settings/tokens
2. Click "Generate new token (classic)"
3. Select scopes:
   - `repo` (full control of private repositories)
   - `write:packages` (upload packages to GitHub Package Registry)
4. Generate and save the token securely

### 3. Maven Central Credentials

Otto should already have an organizational Maven Central account with the `de.otto.*` namespace verified.

**For Otto developers:**
- Contact your team lead or DevOps to get access to the existing Maven Central credentials
- These are typically shared organizational credentials stored in a secure vault
- The `de.otto.config` namespace is already covered under the `de.otto.*` namespace

**If you need to verify or manage the account:**
1. Log in to https://s01.oss.sonatype.org/ with Otto's organizational credentials
2. To generate a user token for CI/CD:
   - Click your username → Profile
   - Select "User Token" from dropdown
   - Click "Access User Token"
3. Save the username and password token securely

**Note:** Do not create a personal Maven Central account for Otto packages. Use the organizational credentials.

## Configuration

**Important:** This project publishes under Otto's organizational accounts. Contact your team lead or DevOps team to get:
- Otto's Maven Central credentials (username/password token)
- Otto's GPG signing key and passphrase
- Access to the otto-de GitHub organization secrets

### Environment Variables

The build.gradle file expects these environment variables for signing and publishing:

```bash
# Required for signing
export GPG_SIGNING_KEY='-----BEGIN PGP PRIVATE KEY BLOCK-----
...your key...
-----END PGP PRIVATE KEY BLOCK-----'
export GPG_SIGNING_PASSWORD='your_gpg_passphrase'

# Required for GitHub Packages
export GITHUB_TOKEN='your_github_token'

# Required for Maven Central (optional for SNAPSHOT)
export MAVEN_USERNAME='your_maven_central_username'
export MAVEN_PASSWORD='your_maven_central_password'
```

**Tip:** You can add these to your shell profile (~/.bashrc, ~/.zshrc) for persistence, but be careful with sensitive data.

### GitHub Repository Secrets

Configure secrets in your GitHub repository for automated releases:

1. **Check for existing organization-level secrets first:**
   - Otto may already have some secrets configured at the organization level
   - Contact your DevOps team to check what's already available
   
2. **If not already configured, add repository secrets:**
   - Go to repository Settings → Secrets and variables → Actions
   - Add the following repository secrets:
     - `GPG_SIGNING_KEY` - Otto's GPG private key for signing (ASCII-armored)
     - `GPG_SIGNING_PASSWORD` - GPG key passphrase
     - `MAVEN_USERNAME` - Otto's Maven Central username token
     - `MAVEN_PASSWORD` - Otto's Maven Central password token

**Note:** 
- `GITHUB_TOKEN` is automatically provided by GitHub Actions
- Contact your team lead or DevOps for the actual credential values
- These should be Otto's organizational credentials, not personal ones

## Version Management

**This project uses manual version management.** You must update the version in [build.gradle](build.gradle) before creating a release.

```gradle
def otto_config_version = "0.1.0-SNAPSHOT"  // For development
def otto_config_version = "0.1.0"           // For release
```

**Version Strategy:**
- Development: `X.Y.Z-SNAPSHOT` - Publishes to snapshot repositories
- Release: `X.Y.Z` - Publishes to release/staging repositories

**Important Notes:**
- ⚠️ The version in `build.gradle` at the time of publishing is what gets published to Maven Central and GitHub Packages
- ⚠️ Git tags should use the "v" prefix (e.g., `v0.1.0`), but artifacts will NOT have the "v" (e.g., `otto-config-0.1.0.jar`)
- ⚠️ Always update `build.gradle` and commit it BEFORE creating and pushing the git tag
- If you forget to update the version, you'll publish the wrong version (e.g., `0.1.0-SNAPSHOT` instead of `0.1.0`)

## Release Process

### Option 1: Local Release (using release.sh)

**For testing or when you can't use GitHub Actions:**

```bash
# 1. Update version in build.gradle
#    For release: def otto_config_version = "0.1.0"
#    For snapshot: def otto_config_version = "0.1.0-SNAPSHOT"

# 2. Commit your changes (for releases)
git add build.gradle
git commit -m "Release version 0.1.0"

# 3. Set up environment variables (if not already in your shell profile)
export GPG_SIGNING_KEY='...'
export GPG_SIGNING_PASSWORD='...'
export GITHUB_TOKEN='...'
export MAVEN_USERNAME='...'  # Required for Maven Central
export MAVEN_PASSWORD='...'  # Required for Maven Central

# 4. Run the release script (publishes artifacts)
./release.sh

# 5. For releases: Create and push git tag
git tag -a v0.1.0 -m "Release version 0.1.0"
git push origin v0.1.0
git push

# 6. Update to next development version
#    Edit build.gradle: def otto_config_version = "0.2.0-SNAPSHOT"
git add build.gradle
git commit -m "Prepare for next development iteration"
git push
```

**Note:** The `release.sh` script publishes artifacts but does NOT create git tags or GitHub releases. You must do those steps manually.

### Option 2: Automated Release via GitHub Actions

#### Tag-based Release (Recommended)

**Critical: Update version in build.gradle BEFORE tagging!**

```bash
# 1. Update version in build.gradle (remove -SNAPSHOT)
#    Edit: def otto_config_version = "0.1.0"
#    The version MUST match the tag (without the "v" prefix)

# 2. Commit the version change
git add build.gradle
git commit -m "Release version 0.1.0"

# 3. Push the commit first (before creating tag)
git push

# 4. Create and push the git tag (with "v" prefix)
git tag -a v0.1.0 -m "Release version 0.1.0"
git push origin v0.1.0

# 5. After release completes, bump to next development version
#    Edit build.gradle: def otto_config_version = "0.2.0-SNAPSHOT"
git add build.gradle
git commit -m "Prepare for next development iteration"
git push
```

**What happens next:**

The `.github/workflows/release.yml` workflow will automatically:
1. Build and test the project
2. Publish artifacts with version `0.1.0` (no "v") to:
   - GitHub Packages: `de.otto.config:otto-config:0.1.0`
   - Maven Central Staging: `de.otto.config:otto-config:0.1.0`
3. Create a GitHub Release titled "Release 0.1.0" (extracts version from tag by removing "v")

#### Manual Workflow

**For ad-hoc releases or testing without creating a git tag first:**

1. **First:** Update version in `build.gradle` if needed, commit and push
2. Go to the Actions tab in GitHub
3. Select "Manual Release" workflow
4. Click "Run workflow"
5. Options:
   - **Version**: Leave empty to use version from `build.gradle`, or specify a version for the GitHub release title
   - **Create git tag**: Check this to automatically create a git tag (recommended for real releases)
6. Click "Run workflow" button

**Important:** The manual workflow publishes whatever version is currently in `build.gradle`. If `build.gradle` says `0.1.0-SNAPSHOT`, that's what gets published, regardless of what you type in the version field.

## Common Mistakes to Avoid

### ❌ Mistake 1: Tagging before updating build.gradle
```bash
# WRONG - This will publish 0.1.0-SNAPSHOT instead of 0.1.0
git tag -a v0.1.0 -m "Release 0.1.0"
git push origin v0.1.0  # build.gradle still has "0.1.0-SNAPSHOT"
```

**✅ Correct:** Always update `build.gradle`, commit, and push BEFORE creating the tag.

### ❌ Mistake 2: Version mismatch between build.gradle and git tag
```bash
# WRONG - build.gradle says "0.1.0" but tag says "v0.2.0"
# This publishes artifacts as 0.1.0 but creates a GitHub release for 0.2.0
```

**✅ Correct:** Ensure `build.gradle` version matches the git tag (without the "v").

### ❌ Mistake 3: Using "v" in build.gradle
```gradle
// WRONG
def otto_config_version = "v0.1.0"
```

**✅ Correct:** Never use "v" in `build.gradle`. Use `"0.1.0"` (git tag will have the "v").

### ❌ Mistake 4: Forgetting to remove -SNAPSHOT for releases
```gradle
// WRONG - This publishes to snapshot repos, not release repos
def otto_config_version = "0.1.0-SNAPSHOT"  // Still has -SNAPSHOT
```

**✅ Correct:** Remove `-SNAPSHOT` for release versions.

### ❌ Mistake 5: Not bumping version after release
After releasing `0.1.0`, if you don't update to `0.2.0-SNAPSHOT`, your next commits will still build as `0.1.0`, causing confusion.

**✅ Correct:** Immediately after a release, update `build.gradle` to the next development version with `-SNAPSHOT`.

## Testing the Setup

### Test Local Publishing

```bash
# Publish to local Maven repository
./gradlew publishToMavenLocal

# Check the output
ls -la ~/.m2/repository/de/otto/config/otto-config/0.1.0-SNAPSHOT/
```

You should see:
- `otto-config-0.1.0-SNAPSHOT.jar` - Main JAR
- `otto-config-0.1.0-SNAPSHOT-sources.jar` - Sources JAR
- `otto-config-0.1.0-SNAPSHOT-javadoc.jar` - Javadoc JAR
- `otto-config-0.1.0-SNAPSHOT.pom` - POM file
- `.asc` files for each artifact (signatures)

### Dry Run

```bash
# See what tasks would be executed
./gradlew publish --dry-run
```

### Full Test Build

```bash
# Run all tests and validation
./gradlew clean check
```

## Publishing Targets

### GitHub Packages
- **URL:** https://maven.pkg.github.com/otto-de/otto-config
- **What:** All versions (snapshots and releases)
- **Authentication:** GitHub token required

### Maven Central - Snapshots
- **URL:** https://s01.oss.sonatype.org/content/repositories/snapshots/
- **What:** SNAPSHOT versions only
- **Authentication:** Maven Central credentials required
- **Retention:** Snapshots may be cleaned up periodically

### Maven Central - Releases
- **URL:** https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/
- **What:** Release versions (non-SNAPSHOT)
- **Authentication:** Maven Central credentials required
- **Process:** Artifacts are deployed to a staging repository
- **Manual Step:** You must log in to https://s01.oss.sonatype.org/ and promote the staging repository to release

## Consuming Published Artifacts

### From GitHub Packages

Add to your `build.gradle` or `settings.gradle`:

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

dependencies {
    implementation 'de.otto.config:otto-config:0.1.0'
}
```

Create `~/.gradle/gradle.properties`:
```properties
gpr.user=your-github-username
gpr.token=your-github-token
```

### From Maven Central

Add to your `build.gradle`:

```gradle
repositories {
    mavenCentral()
    
    // For SNAPSHOT versions
    maven {
        url = uri("https://s01.oss.sonatype.org/content/repositories/snapshots/")
    }
}

dependencies {
    implementation 'de.otto.config:otto-config:0.1.0'
}
```

## Troubleshooting

### GPG Signing Fails

**Error:** `gpg: signing failed: No secret key`

**Solution:**
1. Verify GPG key is properly exported (include BEGIN/END blocks)
2. Ensure passphrase is correct
3. Check key hasn't expired: `gpg --list-secret-keys`

**Error:** `Could not initialize class org.bouncycastle.jcajce.provider.BouncyCastleFipsProvider`

**Solution:** Ensure your GPG key is in ASCII-armored format (exported with `--armor` flag)

### GitHub Packages Authentication Fails

**Error:** `401 Unauthorized` or `403 Forbidden`

**Solution:**
1. Verify `GITHUB_TOKEN` has `write:packages` scope
2. Check token hasn't expired
3. Verify repository name is correct in URL (`otto-de/otto-config`)
4. Ensure you have write permissions to the otto-de organization

### Maven Central Publishing Fails

**Error:** `401 Unauthorized`

**Solution:**
1. Verify you're using Otto's organizational user token (not a personal account password)
2. Contact your team to get/regenerate the token from: https://s01.oss.sonatype.org/ → Profile → User Token
3. Check username and password token are correct in your environment variables or GitHub secrets

**Error:** `403 Forbidden` or `400 Bad Request`

**Solution:**
1. The `de.otto.*` namespace should already be verified for Otto. If you're getting permission errors, verify:
   - You're using the correct organizational credentials
   - The credentials have publishing rights to `de.otto.config`
2. Contact your team lead if you need access granted to the Otto Maven Central account
3. Verify the POM contains all required metadata (check build logs for specific validation errors)

### Build Fails in GitHub Actions

**Solution:**
1. Check all repository secrets are configured correctly
2. Verify Java version matches (JDK 21)
3. Check GitHub Actions logs for specific errors
4. Test locally: `./gradlew clean check publish --stacktrace`
5. Ensure secrets don't contain leading/trailing whitespace

### Signature Verification Fails

**Error:** Signatures don't match

**Solution:**
1. Verify you're using the correct GPG key
2. Ensure key hasn't been revoked
3. Check passphrase is correct
4. Try uploading your public key to key servers:
   ```bash
   gpg --keyserver keys.openpgp.org --send-keys YOUR_KEY_ID
   ```

## CI/CD Workflows

### [.github/workflows/build-main.yml](.github/workflows/build-main.yml)
- **Triggered on:** Push/PR to main/master branch, manual dispatch
- **Actions:** Build, test, and validate (no publishing)

### [.github/workflows/release.yml](.github/workflows/release.yml)
- **Triggered on:** Git tag push matching `v*.*.*`
- **Actions:** Build, test, publish to both GitHub Packages and Maven Central, create GitHub Release

### [.github/workflows/manual-release.yml](.github/workflows/manual-release.yml)
- **Triggered on:** Manual workflow dispatch
- **Actions:** Build, test, publish, optionally create tag and GitHub Release

## Maven Central Release Promotion

For non-SNAPSHOT releases, artifacts are first deployed to a staging repository. You must manually promote them:

1. Log in to https://s01.oss.sonatype.org/
2. Click "Staging Repositories" in the left menu
3. Find your staging repository (usually named `deotto-XXXX`)
4. Select it and click "Close" to trigger validation
5. Wait for validation to complete (checks signatures, POM metadata, etc.)
6. If validation passes, click "Release" to promote to Maven Central
7. Artifacts will sync to Maven Central within a few hours

## Verification Checklist

After your first release, verify:

- [ ] Artifacts appear in [GitHub Packages](https://github.com/otto-de/otto-config/packages)
- [ ] POM file contains correct metadata (name, description, URL, licenses, developers, SCM)
- [ ] All three JARs are present: main, sources, javadoc
- [ ] Artifacts are properly signed (.asc signature files present)
- [ ] GitHub Release was created with correct tag and notes
- [ ] For Maven Central releases:
  - [ ] Artifacts appear in staging repository
  - [ ] Validation passes (close succeeds)
  - [ ] Successfully promoted to Maven Central
  - [ ] Searchable on https://central.sonatype.com/ (may take a few hours)
- [ ] Can download and use artifact in a test project

## Additional Resources

- [Gradle Maven Publish Plugin](https://docs.gradle.org/current/userguide/publishing_maven.html)
- [Gradle Signing Plugin](https://docs.gradle.org/current/userguide/signing_plugin.html)
- [GitHub Packages Documentation](https://docs.github.com/en/packages)
- [Maven Central Publishing Guide](https://central.sonatype.org/publish/)
- [GPG Documentation](https://www.gnupg.org/documentation/)
- [Sonatype OSSRH Guide](https://central.sonatype.org/publish/publish-guide/)
