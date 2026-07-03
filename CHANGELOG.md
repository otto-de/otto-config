# Release Notes

## 0.1.9
* **[core]**: Fix nondeterministic source ordering in `SourceDiscovery`. Sources are now returned in the order declared in `otto.config.sources.enabled`, matching the user's intent and no longer depending on JVM-specific `Class#getMethods()` ordering.
* **[build]**: Migrate release pipeline to JReleaser targeting the new Maven Central Portal (`central.sonatype.com`).
* **[ci]**: Run the build workflow on pushes to `feat/**`, `fix/**`, and `chore/**` branches.

## 0.1.7
* Previous release (see git history)

## 0.1.6
* Previous release (see git history)

## 0.1.4
* Previous release (see git history)

## 0.1.3
* Previous release (see git history)

## 0.1.1
* Previous release (see git history)

## 0.1.0
* Initial release
