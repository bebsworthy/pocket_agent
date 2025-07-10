# Code Quality Setup - Pocket Agent Mobile App

This document describes the comprehensive code quality tools and processes configured for the Pocket Agent mobile application.

## 🛠️ Tools Configured

### 1. **Ktlint** - Kotlin Code Formatting and Linting
- **Version**: 1.0.1
- **Purpose**: Enforces consistent Kotlin code formatting and style
- **Configuration**: Built-in Android-specific rules
- **Reports**: Plain text, Checkstyle XML, SARIF

### 2. **Detekt** - Static Code Analysis
- **Version**: 1.23.4
- **Purpose**: Detects code smells, complexity issues, and potential bugs
- **Configuration**: `/config/detekt/detekt.yml`
- **Baseline**: `/config/detekt/baseline.xml`
- **Reports**: HTML, XML, TXT, SARIF, Markdown

### 3. **Spotless** - Code Formatting Automation
- **Version**: 6.23.3
- **Purpose**: Automatically formats code according to defined rules
- **Features**: Trimming whitespace, proper indentation, wildcard import prevention
- **Integration**: Works with Ktlint for Kotlin formatting

### 4. **Android Lint** - Android-Specific Static Analysis
- **Purpose**: Identifies Android-specific issues and best practices
- **Configuration**: `/config/lint/lint.xml`
- **Baseline**: `/config/lint/lint-baseline.xml`
- **Focus Areas**: Security, performance, accessibility, internationalization

### 5. **JaCoCo** - Code Coverage Analysis
- **Version**: 0.8.11
- **Purpose**: Measures unit test code coverage
- **Reports**: XML, HTML
- **Exclusions**: Generated code, DI modules, Android framework classes

### 6. **SonarQube** - Comprehensive Code Quality Analysis (Optional)
- **Version**: 4.4.1.3373
- **Purpose**: Centralized code quality dashboard
- **Integration**: Consumes reports from other tools
- **Metrics**: Coverage, duplications, maintainability, reliability, security

## 📋 Available Gradle Tasks

### Core Quality Tasks
```bash
# Run all code quality checks
./gradlew codeQualityCheck

# Fix auto-fixable issues
./gradlew codeQualityFix

# Comprehensive quality check with tests
./gradlew fullQualityCheck

# Generate all quality reports
./gradlew generateQualityReports

# Display quality metrics
./gradlew qualityMetrics
```

### Pre-commit/Push Tasks
```bash
# Pre-commit validation
./gradlew prCommitCheck

# Pre-push validation
./gradlew prPushCheck

# CI/CD quality gate
./gradlew qualityGate
```

### Setup and Maintenance
```bash
# Initialize quality tools
./gradlew setupQualityTools

# Validate configurations
./gradlew validateQualityConfig

# Clean quality reports
./gradlew cleanQualityReports
```

### Individual Tool Tasks
```bash
# Ktlint
./gradlew ktlintCheck      # Check formatting
./gradlew ktlintFormat     # Fix formatting

# Detekt
./gradlew detekt           # Run static analysis
./gradlew detektBaseline   # Create baseline

# Spotless
./gradlew spotlessCheck    # Check formatting
./gradlew spotlessApply    # Apply formatting

# Android Lint
./gradlew lint             # Run lint checks
./gradlew lintBaseline     # Create baseline

# JaCoCo
./gradlew jacocoTestReport # Generate coverage report

# SonarQube
./gradlew sonarqube        # Upload to SonarQube
```

## 🔧 Configuration Files

### Detekt Configuration (`/config/detekt/detekt.yml`)
- Comprehensive rule configuration
- Android-specific optimizations
- Exclusions for generated code
- Customized complexity thresholds
- Security-focused rules

### Android Lint Configuration (`/config/lint/lint.xml`)
- Security-focused error rules
- Performance warning rules
- Accessibility checks
- Internationalization rules
- Disabled noisy rules

### Quality Tasks (`/gradle/quality-tasks.gradle`)
- Custom Gradle tasks for quality management
- Task dependencies and ordering
- Report generation and cleanup
- Quality gate enforcement

## 🪝 Git Hooks

### Pre-commit Hook
Automatically runs before each commit:
- Ktlint formatting check
- Detekt static analysis
- Spotless formatting check
- Android Lint (warnings only)
- Unit tests

### Pre-push Hook
Automatically runs before each push:
- All pre-commit checks
- Full test suite
- Code coverage generation
- Comprehensive quality validation

### Commit Message Hook
Validates commit message format:
- Conventional commit format
- Type prefixes: feat, fix, docs, style, refactor, test, chore, build, ci, perf, revert
- Scope and subject validation

### Setup Git Hooks
```bash
# Install all git hooks
./scripts/setup-hooks.sh

# Manual hook installation
cp scripts/pre-commit .git/hooks/pre-commit
chmod +x .git/hooks/pre-commit
```

## 📊 Quality Reports

### Code Coverage (JaCoCo)
- **Location**: `build/reports/jacoco/jacocoTestReport/html/index.html`
- **Format**: HTML, XML
- **Metrics**: Line coverage, branch coverage, complexity

### Static Analysis (Detekt)
- **Location**: `build/reports/detekt/detekt.html`
- **Format**: HTML, XML, TXT, SARIF, Markdown
- **Metrics**: Code smells, complexity, potential bugs

### Android Lint
- **Location**: `build/reports/lint-results.html`
- **Format**: HTML, XML, TXT
- **Metrics**: Security issues, performance problems, accessibility issues

### SonarQube Dashboard
- **URL**: `http://localhost:9000` (when running locally)
- **Metrics**: Overall quality gate, technical debt, coverage, duplications

## 🚀 CI/CD Integration

### Quality Gate Pipeline
1. **Pre-build**: Run code quality checks
2. **Build**: Standard Android build process
3. **Test**: Execute unit tests
4. **Coverage**: Generate code coverage reports
5. **Analysis**: Upload to SonarQube (optional)
6. **Gate**: Enforce quality thresholds

### Gradle Build Integration
```kotlin
// Quality checks run before build
tasks.named("preBuild") {
    dependsOn("codeQualityCheck")
}

// Coverage report generated after tests
tasks.named("testDebugUnitTest") {
    finalizedBy("jacocoTestReport")
}
```

## 📈 Quality Metrics and Thresholds

### Code Coverage Targets
- **Minimum**: 70% line coverage
- **Target**: 85% line coverage
- **Exclusions**: Generated code, DI modules, Android framework classes

### Detekt Rules
- **Complexity**: Max 15 cyclomatic complexity
- **Method Length**: Max 60 lines
- **Parameter Count**: Max 6 parameters
- **Class Size**: Max 600 lines

### Performance Targets
- **Build Time**: Quality checks should add < 2 minutes
- **Report Generation**: < 30 seconds
- **Hook Execution**: < 1 minute for pre-commit

## 🔧 Troubleshooting

### Common Issues and Solutions

#### Ktlint Formatting Errors
```bash
# Auto-fix formatting issues
./gradlew ktlintFormat

# Check specific files
./gradlew ktlintCheck --debug
```

#### Detekt Analysis Failures
```bash
# Generate baseline to ignore existing issues
./gradlew detektBaseline

# Run with detailed output
./gradlew detekt --info
```

#### Spotless Check Failures
```bash
# Apply automatic formatting
./gradlew spotlessApply

# Check specific file types
./gradlew spotlessKotlinCheck
```

#### Coverage Report Issues
```bash
# Clean and regenerate
./gradlew clean jacocoTestReport

# Debug coverage collection
./gradlew testDebugUnitTest --debug
```

### Hook Bypass (Emergency Only)
```bash
# Bypass pre-commit hooks
git commit --no-verify

# Bypass pre-push hooks
git push --no-verify
```

## 🎯 Best Practices

### Code Quality
1. **Run quality checks frequently** during development
2. **Fix issues immediately** rather than accumulating technical debt
3. **Use IDE plugins** for real-time feedback
4. **Review quality reports** regularly
5. **Maintain high test coverage** for critical code paths

### Development Workflow
1. **Configure IDE** with project code style settings
2. **Enable format on save** in your IDE
3. **Run pre-commit checks** before committing
4. **Review quality reports** before merging
5. **Address quality gate failures** promptly

### Team Collaboration
1. **Consistent configuration** across all development environments
2. **Regular quality metric reviews** in team meetings
3. **Shared responsibility** for code quality
4. **Documentation updates** when changing quality rules
5. **Knowledge sharing** about quality tools and practices

## 📝 Maintenance

### Regular Tasks
- **Monthly**: Review and update quality tool versions
- **Quarterly**: Reassess quality rules and thresholds
- **Per Release**: Generate comprehensive quality reports
- **Continuous**: Monitor quality metrics and trends

### Configuration Updates
- Update tool versions in `gradle/libs.versions.toml`
- Modify rules in configuration files
- Adjust thresholds based on project maturity
- Add new quality checks as needed

## 🔗 Resources

### Documentation
- [Ktlint Documentation](https://pinterest.github.io/ktlint/)
- [Detekt Documentation](https://detekt.dev/)
- [Spotless Documentation](https://github.com/diffplug/spotless)
- [Android Lint Documentation](https://developer.android.com/studio/write/lint)
- [JaCoCo Documentation](https://www.jacoco.org/jacoco/trunk/doc/)
- [SonarQube Documentation](https://docs.sonarqube.org/)

### IDE Integration
- **Android Studio**: Enable Ktlint and Detekt plugins
- **IntelliJ IDEA**: Configure code style and inspections
- **VS Code**: Install Kotlin and quality tool extensions

This comprehensive code quality setup ensures that the Pocket Agent mobile application maintains high standards of code quality, security, and maintainability throughout its development lifecycle.