# Publishing Setup Summary for otto-config

## ✅ What Was Configured

This project has been configured to publish JAR artifacts to both **GitHub Packages** and **Maven Central** using standard Gradle plugins.

### Core Configuration

1. **[build.gradle](build.gradle)** - Main build configuration
   - Added `maven-publish` plugin for artifact publishing
   - Added `signing` plugin for GPG signing
   - Configured publication with POM metadata
   - Set up repositories for GitHub Packages and Maven Central
   - Version: `0.1.0-SNAPSHOT` (update this for releases)

2. **[gradle/maven.gradle](gradle/maven.gradle)** - JAR manifest configuration
   - Configures JAR manifest attributes
   - Sets up Javadoc options

3. **[release.sh](release.sh)** - Local release automation script
   - Validates environment variables
   - Detects SNAPSHOT vs release versions
   - Runs tests and publishes artifacts

4. **[PUBLISHING.md](PUBLISHING.md)** - Complete publishing documentation
   - Setup instructions for GPG, GitHub, and Maven Central
   - Step-by-step release process
   - Troubleshooting guide

### GitHub Actions Workflows

1. **[.github/workflows/build-main.yml](.github/workflows/build-main.yml)**
   - Triggers: Push/PR to main/master, manual dispatch
   - Actions: Build and test (no publishing)

2. **[.github/workflows/release.yml](.github/workflows/release.yml)**
   - Triggers: Manual workflow dispatch only
   - Actions: Build, test, create git tag, publish, create GitHub release

## 📦 Published Artifacts

When published, the following artifacts are created:

- `otto-config-X.Y.Z.jar` - Main library JAR
- `otto-config-X.Y.Z-sources.jar` - Source code JAR
- `otto-config-X.Y.Z-javadoc.jar` - Javadoc JAR
- `otto-config-X.Y.Z.pom` - Maven POM file
- `.asc` signature files for all artifacts

**Verified:** ✅ Local publishing test successful. All artifacts generated correctly.

## 🔐 Required Secrets

Before you can publish, you need to configure these secrets:

### For Local Publishing

Set environment variables:
```bash
export GPG_SIGNING_KEY='<your-gpg-private-key>'
export GPG_SIGNING_PASSWORD='<your-gpg-passphrase>'
export GITHUB_TOKEN='<your-github-token>'
export MAVEN_USERNAME='<maven-central-username>'
export MAVEN_PASSWORD='<maven-central-password>'
```

### For GitHub Actions

Add repository secrets (Settings → Secrets and variables → Actions):
- `GPG_SIGNING_KEY`
- `GPG_SIGNING_PASSWORD`
- `MAVEN_USERNAME`
- `MAVEN_PASSWORD`

**Note:** `GITHUB_TOKEN` is automatically provided by GitHub Actions.

## 🚀 How to Release

### Quick Release (via GitHub Actions)

**⚠️ Important: This project uses manual version management!**

```bash
# 1. Update version in build.gradle (remove -SNAPSHOT)
# Change: def otto_config_version = "0.1.0-SNAPSHOT"
# To:     def otto_config_version = "0.1.0"

# 2. Commit and push the version change
git add build.gradle
git commit -m "Release version 0.1.0"
git push

# 3. Trigger the Release workflow manually:
#    Go to: GitHub → Actions → Release → Run workflow
#    Click "Run workflow"

# 4. After release, bump to next development version
# Edit build.gradle: def otto_config_version = "0.2.0-SNAPSHOT"
git add build.gradle
git commit -m "Prepare for next development iteration"
git push
```

**Note:** The workflow creates git tags automatically with "v" prefix (`v0.1.0`), but published artifacts do NOT have the "v" (`otto-config-0.1.0.jar`).

The GitHub Actions workflow will automatically:
- ✅ Run all tests
- ✅ Create git tag (e.g., v0.1.0)
- ✅ Publish to GitHub Packages
- ✅ Publish to Maven Central
- ✅ Create GitHub Release with notes

### Local Release

```bash
# Set up environment variables (see above)
./release.sh
```

## 📍 Where Artifacts Are Published

### GitHub Packages
- **URL:** https://github.com/otto-de/otto-config/packages
- **Contains:** All versions (snapshots and releases)
- **Access:** Requires GitHub token with `read:packages` scope

### Maven Central Snapshots
- **URL:** https://s01.oss.sonatype.org/content/repositories/snapshots/
- **Contains:** SNAPSHOT versions only
- **Access:** Public (no authentication needed to download)

### Maven Central Releases
- **URL:** https://s01.oss.sonatype.org/ (staging), then synced to Maven Central
- **Contains:** Release versions (non-SNAPSHOT)
- **Access:** Public via https://central.sonatype.com/ and https://repo1.maven.org/
- **Note:** Requires manual promotion from staging to release

## 📚 Using the Published Library

### From Maven Central (Recommended for Public Use)

```gradle
repositories {
    mavenCentral()
}

dependencies {
    implementation 'de.otto.config:otto-config:0.1.0'
}
```

### From GitHub Packages

```gradle
repositories {
    maven {
        url = uri("https://maven.pkg.github.com/otto-de/otto-config")
        credentials {
            username = System.getenv("GITHUB_ACTOR")
            password = System.getenv("GITHUB_TOKEN")
        }
    }
}

dependencies {
    implementation 'de.otto.config:otto-config:0.1.0'
}
```

## ✅ Verification Status

| Component | Status |
|-----------|--------|
| Build configuration | ✅ Complete |
| Publishing configuration | ✅ Complete |
| Signing configuration | ✅ Complete |
| GitHub Actions workflows | ✅ Complete |
| Documentation | ✅ Complete |
| Local test publish | ✅ Successful |
| Full build and test | ✅ Successful |

## 🎯 Next Steps

To complete the setup:

1. **Set up GPG key** (see [PUBLISHING.md](PUBLISHING.md))
2. **Create GitHub token** with `write:packages` scope
3. **Set up Maven Central account** and get user token
4. **Configure GitHub repository secrets** (see above)
5. **Test a snapshot release** locally with `./release.sh`
6. **Create your first release** by pushing a tag

## 📖 Additional Resources

- [PUBLISHING.md](PUBLISHING.md) - Complete publishing guide
- [build.gradle](build.gradle) - Build configuration
- [release.sh](release.sh) - Release automation script
- [Gradle Publishing Documentation](https://docs.gradle.org/current/userguide/publishing_maven.html)
- [Maven Central Publishing Guide](https://central.sonatype.org/publish/)
- [GitHub Packages Documentation](https://docs.github.com/en/packages)

## 🛠️ Technical Details

- **Group ID:** `de.otto.config`
- **Artifact ID:** `otto-config`
- **Current Version:** `0.1.0-SNAPSHOT`
- **Java Version:** 21 (source and target compatibility)
- **Gradle Version:** 9.4.1
- **Publishing Plugin:** `maven-publish` (standard Gradle)
- **Signing Plugin:** `signing` (standard Gradle)
- **Signing Method:** In-memory PGP keys via environment variables

## 💡 Tips

- **SNAPSHOT versions** publish to snapshot repositories automatically
- **Release versions** (without -SNAPSHOT) publish to staging and require manual promotion in Sonatype
- **Always test locally** with `./gradlew publishToMavenLocal` before pushing
- **Keep your GPG key secure** - never commit it to the repository
- **Use GitHub secrets** for CI/CD - don't hardcode credentials
- **Update the version** in build.gradle for each release
- **Create git tags** matching the version (e.g., `v0.1.0`)
- **Check GitHub Actions logs** if automated releases fail

---

**Setup completed on:** 2024-06-05  
**Status:** ✅ Ready to publish (pending secrets configuration)
