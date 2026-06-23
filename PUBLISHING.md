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

All publishing credentials (GPG keys, Maven Central credentials, GitHub tokens) are configured as organizational secrets in the otto-de GitHub organization. You don't need to set up any keys or tokens locally.

**What you need:**
- Write access to the otto-de/otto-config repository
- Ability to trigger GitHub Actions workflows

**Note:** The `GITHUB_TOKEN` is automatically provided by GitHub Actions. All other credentials (GPG signing keys, Maven Central credentials) are configured at the organization level and are automatically available to the release workflow.

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

### Automated Release via GitHub Actions (Recommended)

**This is now the primary release method. The workflow creates the git tag automatically.**

```bash
# 1. Update version in build.gradle (remove -SNAPSHOT)
#    Edit: def otto_config_version = "0.1.0"
git add build.gradle
git commit -m "Release version 0.1.0"
git push

# 2. Go to GitHub Actions and manually trigger the Release workflow:
#    - Navigate to: Actions → Release → Run workflow
#    - Click "Run workflow"

# 3. After release completes, bump to next development version
#    Edit build.gradle: def otto_config_version = "0.2.0-SNAPSHOT"
git add build.gradle
git commit -m "Prepare for next development iteration"
git push
```

**What happens when you trigger the workflow:**

The `.github/workflows/release.yml` workflow will:
1. Build and test the project
2. **Create and push a git tag** (e.g., `v0.1.0`) based on the version in `build.gradle`
3. Publish artifacts to:
   - GitHub Packages: `de.otto.config:otto-config:0.1.0`
   - Maven Central Staging: `de.otto.config:otto-config:0.1.0`
4. Create a GitHub Release with auto-generated release notes

**Important Notes:**
- The workflow reads the version from `build.gradle` - this is the single source of truth
- The git tag is created automatically with a "v" prefix (e.g., `v0.1.0`)
- Published artifacts use the version without the "v" prefix (e.g., `otto-config-0.1.0.jar`)
- The workflow will fail if the git tag already exists (to retry, delete the tag first)

## Common Mistakes to Avoid

### ❌ Mistake 1: Not updating build.gradle before triggering release
```bash
# WRONG - Running the workflow while build.gradle still has "0.1.0-SNAPSHOT"
# This will create a tag v0.1.0-SNAPSHOT and publish snapshot artifacts
```

**✅ Correct:** Always update `build.gradle` to remove `-SNAPSHOT`, commit, and push BEFORE triggering the release workflow.

### ❌ Mistake 2: Trying to re-release without deleting the existing tag
```bash
# WRONG - Running the workflow again when v0.1.0 tag already exists
# The workflow will fail because the tag cannot be overwritten
```

**✅ Correct:** If you need to retry a release, first delete the tag:
```bash
git push --delete origin v0.1.0
# Then trigger the workflow again
```

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

## Handling Failed Releases

If a release workflow fails or you need to retry:

### If the Git Tag Was Not Created
Simply fix the issue and re-trigger the workflow.

### If the Git Tag Was Created But Publishing Failed

1. **Delete the git tag:**
   ```bash
   # Delete locally (if you have it)
   git tag -d v0.1.0
   
   # Delete from GitHub
   git push --delete origin v0.1.0
   ```

2. **Delete the GitHub Release** (if it was created):
   - Go to Releases page on GitHub
   - Find the release and delete it

3. **Fix the issue** (e.g., check secrets, fix build errors)

4. **Re-trigger the workflow**

### If Publishing Succeeded But You Need to Release Again

**Important:** You cannot overwrite versions in Maven Central. Once published, they're immutable.

Your options:
1. **For critical fixes:** Bump to a patch version (e.g., `0.1.1`) and release that
2. **If nothing was published to Maven Central yet:** You can delete the GitHub Packages artifact and retry with the same version

**Note:** SNAPSHOT versions can be overwritten, but release versions (without `-SNAPSHOT`) cannot.

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
- **Triggered on:** Manual workflow dispatch only
- **Actions:** Build, test, create git tag, publish to both GitHub Packages and Maven Central, create GitHub Release
- **Version source:** Always uses version from `build.gradle` (single source of truth)

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
