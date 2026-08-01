# Contributing to Kuiver

Thanks for your interest in contributing to Kuiver!

## Reporting Issues

Found a bug or have a feature request? Please open an issue on [GitHub Issues](https://github.com/justdeko/kuiver/issues).

When reporting bugs, please include:
- Platform (Android/iOS/JVM/Wasm) and version
- Kuiver version
- Minimal reproducible example
- Expected vs actual behavior

## Contributing Code

1. **Fork the repository** and create a new branch for your feature or bug fix
2. **Make your changes** - keep commits focused and write clear commit messages
3. **Test your changes** - ensure all tests pass: `./gradlew :core:allTests`
4. **Document your changes** in `README.next.md`, see below
5. **Submit a pull request** with a clear description of your changes

## Documentation

There are two readmes in the repository:

- `README.md` documents the latest published version. It is what people installing kuiver from
  Maven Central read, so it must not describe anything they cannot use yet
- `README.next.md` documents the version being worked on. Everything merged into `main` since the
  last release is described there

If your change adds, removes or alters public API, document it in `README.next.md` in the same pull
request, not in `README.md`. When a release goes out, `README.next.md` becomes `README.md` and the
release notes are written from the diff between the two.

Small fixes to documentation of already released behavior go into both files, since otherwise the
fix is lost the next time `README.next.md` replaces `README.md`.

## Development Setup

```bash
# Clone the repository
git clone https://github.com/justdeko/kuiver.git
cd kuiver

# Run tests
./gradlew :core:allTests

# Run sample app
./gradlew :sample:composeApp:run

# Renderer frame cost report, see docs/benchmarks/layout-transition.md
./gradlew :core:benchmark
```

## Code Style

This project follows the official Kotlin coding conventions. Please ensure your code is formatted properly before submitting.

## Questions?

Feel free to open an issue if you have questions about contributing!
