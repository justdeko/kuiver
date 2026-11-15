# Contributing to Kuiver

Thanks for your interest in contributing to Kuiver!

## Reporting Issues

Found a bug or have a feature request? Please open an issue
on [GitHub Issues](https://github.com/justdeko/kuiver/issues).

When reporting bugs, please include:

- Platform (Android/iOS/JVM/Wasm) and version
- Kuiver version
- Minimal reproducible example
- Expected vs actual behavior

## Contributing Code

1. **Fork the repository** and create a new branch for your feature or bug fix
2. **Make your changes** - keep commits focused and write clear commit messages
3. **Test your changes** - ensure all tests pass: `./gradlew :core:allTests`
4. **Update the ABI dumps** if you changed the public API, see below
5. **Document your changes** if they affect documentation in the README, see below
6. **Submit a pull request** with a clear description of your changes

## Public API

In `:core`, every public declaration needs the `public` keyword and a return type.

The files in `core/api` list the public API. The build checks your code against them. If you change
the public API, update these files and commit them with your change:

```bash
./gradlew :core:updateKotlinAbi
```

## Documentation

If you want changes made to `README.md`, please say so in your PR. They will be added with a
release, combined with other potential changes and the version bump.

## Development Setup

```bash
# Clone the repository
git clone https://github.com/justdeko/kuiver.git
cd kuiver

# Run tests
./gradlew :core:allTests

# Build, including the ABI check
./gradlew :core:build

# Run sample app
./gradlew :sample:composeApp:run

# Renderer frame cost report, see docs/benchmarks/layout-transition.md
./gradlew :core:benchmark
```

## Code Style

This project follows the official Kotlin coding conventions. Please ensure your code is formatted
properly before submitting.

## Questions?

Feel free to open an issue if you have questions about contributing!
