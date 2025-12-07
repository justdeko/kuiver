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
4. **Submit a pull request** with a clear description of your changes

## Development Setup

```bash
# Clone the repository
git clone https://github.com/justdeko/kuiver.git
cd kuiver

# Run tests
./gradlew :core:allTests

# Run sample app
./gradlew :sample:composeApp:run
```

## Code Style

This project follows the official Kotlin coding conventions. Please ensure your code is formatted properly before submitting.

## Questions?

Feel free to open an issue if you have questions about contributing!
