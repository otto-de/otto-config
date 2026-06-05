#!/usr/bin/env bash
#
# Release script for otto-config
#
# This script automates the release process:
# 1. Validates environment variables
# 2. Detects SNAPSHOT vs. release version
# 3. Runs tests
# 4. Publishes artifacts to GitHub Packages and Maven Central
#
# Usage: ./release.sh
#

set -e
SCRIPT_DIR=$(dirname "$0")

# Color output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

info() {
    echo -e "${GREEN}INFO:${NC} $1"
}

warn() {
    echo -e "${YELLOW}WARN:${NC} $1"
}

error() {
    echo -e "${RED}ERROR:${NC} $1"
}

heading() {
    echo -e "${BLUE}$1${NC}"
}

check_configuration() {
    local missing=0
    
    heading "🔍 Checking configuration..."
    
    if [ -z "$GPG_SIGNING_KEY" ]; then
        error "GPG_SIGNING_KEY environment variable is not set"
        missing=1
    fi
    
    if [ -z "$GPG_SIGNING_PASSWORD" ]; then
        error "GPG_SIGNING_PASSWORD environment variable is not set"
        missing=1
    fi
    
    if [ -z "$GITHUB_TOKEN" ]; then
        error "GITHUB_TOKEN environment variable is not set"
        missing=1
    fi
    
    # Maven Central is optional for SNAPSHOT releases
    if [ -z "$MAVEN_USERNAME" ]; then
        warn "MAVEN_USERNAME not set (optional for SNAPSHOT, required for releases)"
    fi
    
    if [ -z "$MAVEN_PASSWORD" ]; then
        warn "MAVEN_PASSWORD not set (optional for SNAPSHOT, required for releases)"
    fi
    
    if [ $missing -eq 1 ]; then
        echo ""
        error "Required environment variables are missing!"
        echo ""
        echo "Please set the following variables:"
        echo "  export GPG_SIGNING_KEY='<your-gpg-key>'"
        echo "  export GPG_SIGNING_PASSWORD='<your-gpg-password>'"
        echo "  export GITHUB_TOKEN='<your-github-token>'"
        echo "  export MAVEN_USERNAME='<your-maven-username>'  # For Maven Central"
        echo "  export MAVEN_PASSWORD='<your-maven-password>'  # For Maven Central"
        echo ""
        echo "See PUBLISHING.md for detailed instructions."
        exit 1
    fi
    
    info "Configuration validation passed ✓"
}

detect_version() {
    VERSION=$(cd "$SCRIPT_DIR" && ./gradlew properties -q | grep "^version:" | awk '{print $2}')
    
    heading "📦 Version Information"
    info "Detected version: $VERSION"

    if [[ "$VERSION" == *-SNAPSHOT ]]; then
        warn "This is a SNAPSHOT release"
        warn "Artifacts will be published to:"
        warn "  - GitHub Packages"
        warn "  - Maven Central Snapshots (https://s01.oss.sonatype.org/content/repositories/snapshots/)"
        echo ""
        read -p "Continue with SNAPSHOT release? (y/N) " -n 1 -r
        echo ""
        if [[ ! $REPLY =~ ^[Yy]$ ]]; then
            info "Release cancelled"
            exit 0
        fi
    else
        info "This is a RELEASE version"
        info "Artifacts will be published to:"
        info "  - GitHub Packages"
        info "  - Maven Central Staging (requires manual promotion)"
        echo ""
        if [ -z "$MAVEN_USERNAME" ] || [ -z "$MAVEN_PASSWORD" ]; then
            error "Maven Central credentials are required for release versions!"
            exit 1
        fi
    fi
}

check_git_clean() {
    heading "🔍 Checking Git status..."
    
    if [ -n "$(git status --porcelain)" ]; then
        warn "Git working directory is not clean:"
        git status --short
        echo ""
        read -p "Continue anyway? (y/N) " -n 1 -r
        echo ""
        if [[ ! $REPLY =~ ^[Yy]$ ]]; then
            info "Release cancelled"
            exit 0
        fi
    else
        info "Git working directory is clean ✓"
    fi
}

main() {
    heading "🚀 Starting otto-config release process..."
    echo ""

    check_configuration
    echo ""
    
    detect_version
    echo ""
    
    check_git_clean
    echo ""

    heading "1️⃣  Running tests..."
    cd "$SCRIPT_DIR"
    ./gradlew clean check
    info "Tests passed ✓"
    echo ""

    heading "2️⃣  Publishing artifacts..."
    ./gradlew publish
    info "Artifacts published ✓"
    echo ""

    heading "🎉 Release complete!"
    echo ""
    echo "Next steps:"
    if [[ "$VERSION" == *-SNAPSHOT ]]; then
        echo "  ✓ SNAPSHOT published to:"
        echo "    - https://github.com/otto-de/otto-config/packages"
        echo "    - https://s01.oss.sonatype.org/content/repositories/snapshots/de/otto/config/otto-config/"
    else
        echo "  1. Create and push a git tag:"
        echo "       git tag -a v$VERSION -m 'Release $VERSION'"
        echo "       git push origin v$VERSION"
        echo ""
        echo "  2. Create a GitHub release (or push the tag to auto-create via workflow)"
        echo ""
        echo "  3. Verify artifacts:"
        echo "       - GitHub: https://github.com/otto-de/otto-config/packages"
        echo "       - Maven Central Staging: https://s01.oss.sonatype.org/"
        echo ""
        echo "  4. Promote from Maven Central staging (manual step required)"
    fi
}

# Run main function
main
