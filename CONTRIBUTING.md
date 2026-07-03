# Contributing to Otto Config

Thank you for your interest in contributing to Otto Config! We welcome contributions from the community.

## How to Contribute

### Reporting Issues

If you find a bug or have a feature request:

1. Check the [existing issues](https://github.com/otto-de/otto-config/issues) to avoid duplicates
2. Create a new issue with a clear title and description
3. Include steps to reproduce (for bugs) or use cases (for features)
4. Add relevant labels if possible

### Submitting Changes

1. **Fork the repository** and create your branch from `main`:
   ```bash
   git checkout -b feature/your-feature-name
   ```

2. **Make your changes**:
   - Follow the existing code style
   - Add tests for new functionality
   - Update documentation as needed
   - Ensure all tests pass: `./gradlew clean test`

3. **Commit your changes**:
   - Write clear, descriptive commit messages
   - Reference relevant issue numbers (e.g., "Fixes #123")

4. **Push to your fork** and submit a pull request:
   ```bash
   git push origin feature/your-feature-name
   ```

5. **Respond to feedback** during the review process

### Code Style

- Use **Java 21** language features appropriately
- Follow standard Java naming conventions
- Use Lombok annotations where appropriate
- Keep methods focused and testable
- Add JavaDoc for public APIs

### Testing

- Write unit tests for new functionality
- Ensure integration tests pass
- Aim for good test coverage
- Test with multiple configuration sources

### Documentation

- Update the README for user-facing changes
- Add JavaDoc for public APIs
- Update relevant files in `docs/` for advanced topics
- Include code examples where helpful
- **Add an entry to [CHANGELOG.md](CHANGELOG.md) under the next `## <version>` heading for any user-facing change** — the release workflow requires it

## Release Process

Releases are automated via GitHub Actions using [JReleaser](https://jreleaser.org/). The workflow publishes to Maven Central and GitHub Packages, creates the git tag, and drafts the GitHub Release.

**Quick steps:**

1. Update `otto_config_version` in `build.gradle` (drop the `-SNAPSHOT` suffix)
2. Add a `## <version>` section in `CHANGELOG.md` describing the changes
3. Commit and push
4. Trigger the workflow: `gh workflow run release.yml -f version=<version>` (or via the Actions UI)
5. After success, bump `build.gradle` back to the next `X.Y.Z-SNAPSHOT`

See **[PUBLISHING.md](PUBLISHING.md)** for full details, credential requirements, and how to retry failed releases.

## Development Setup

See [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md) for detailed development environment setup instructions.

## Questions?

If you have questions about contributing, feel free to:
- Open an issue for discussion
- Reach out to the maintainers (see [MAINTAINERS](MAINTAINERS))

## License

By contributing, you agree that your contributions will be licensed under the Apache License 2.0.
