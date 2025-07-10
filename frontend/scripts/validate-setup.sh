#!/bin/bash

# Validation script for Code Quality Setup
# This script validates that all code quality tools are properly configured

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Function to print colored output
print_status() {
    echo -e "${GREEN}[✓]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[⚠]${NC} $1"
}

print_error() {
    echo -e "${RED}[✗]${NC} $1"
}

echo "🔍 Validating Code Quality Setup for Pocket Agent Mobile App"
echo "=" * 60

# Check if we're in the right directory
if [ ! -f "build.gradle.kts" ]; then
    print_error "build.gradle.kts not found. Please run this script from the project root."
    exit 1
fi

# Check version catalog
if [ -f "gradle/libs.versions.toml" ]; then
    print_status "Version catalog found"
    
    # Check for quality tool versions
    if grep -q "ktlint.*=" gradle/libs.versions.toml; then
        print_status "Ktlint version configured"
    else
        print_warning "Ktlint version not found in version catalog"
    fi
    
    if grep -q "detekt.*=" gradle/libs.versions.toml; then
        print_status "Detekt version configured"
    else
        print_warning "Detekt version not found in version catalog"
    fi
    
    if grep -q "spotless.*=" gradle/libs.versions.toml; then
        print_status "Spotless version configured"
    else
        print_warning "Spotless version not found in version catalog"
    fi
    
    if grep -q "jacoco.*=" gradle/libs.versions.toml; then
        print_status "JaCoCo version configured"
    else
        print_warning "JaCoCo version not found in version catalog"
    fi
    
    if grep -q "sonarqube.*=" gradle/libs.versions.toml; then
        print_status "SonarQube version configured"
    else
        print_warning "SonarQube version not found in version catalog"
    fi
else
    print_error "Version catalog not found at gradle/libs.versions.toml"
fi

# Check build configuration
if grep -q "ktlint" build.gradle.kts; then
    print_status "Ktlint plugin configured in build.gradle.kts"
else
    print_warning "Ktlint plugin not found in build.gradle.kts"
fi

if grep -q "detekt" build.gradle.kts; then
    print_status "Detekt plugin configured in build.gradle.kts"
else
    print_warning "Detekt plugin not found in build.gradle.kts"
fi

if grep -q "spotless" build.gradle.kts; then
    print_status "Spotless plugin configured in build.gradle.kts"
else
    print_warning "Spotless plugin not found in build.gradle.kts"
fi

if grep -q "jacoco" build.gradle.kts; then
    print_status "JaCoCo plugin configured in build.gradle.kts"
else
    print_warning "JaCoCo plugin not found in build.gradle.kts"
fi

if grep -q "sonarqube" build.gradle.kts; then
    print_status "SonarQube plugin configured in build.gradle.kts"
else
    print_warning "SonarQube plugin not found in build.gradle.kts"
fi

# Check app module configuration
if [ -f "app/build.gradle.kts" ]; then
    print_status "App module build file found"
    
    if grep -q "ktlint" app/build.gradle.kts; then
        print_status "Ktlint configured in app module"
    else
        print_warning "Ktlint not found in app module"
    fi
    
    if grep -q "detekt" app/build.gradle.kts; then
        print_status "Detekt configured in app module"
    else
        print_warning "Detekt not found in app module"
    fi
    
    if grep -q "lint {" app/build.gradle.kts; then
        print_status "Android Lint configured in app module"
    else
        print_warning "Android Lint configuration not found in app module"
    fi
else
    print_warning "App module build file not found"
fi

# Check configuration files
if [ -f "config/detekt/detekt.yml" ]; then
    print_status "Detekt configuration file found"
else
    print_error "Detekt configuration file not found at config/detekt/detekt.yml"
fi

if [ -f "config/detekt/baseline.xml" ]; then
    print_status "Detekt baseline file found"
else
    print_warning "Detekt baseline file not found at config/detekt/baseline.xml"
fi

if [ -f "config/lint/lint.xml" ]; then
    print_status "Android Lint configuration file found"
else
    print_error "Android Lint configuration file not found at config/lint/lint.xml"
fi

if [ -f "config/lint/lint-baseline.xml" ]; then
    print_status "Android Lint baseline file found"
else
    print_warning "Android Lint baseline file not found at config/lint/lint-baseline.xml"
fi

# Check scripts
if [ -f "scripts/pre-commit" ]; then
    print_status "Pre-commit hook script found"
    if [ -x "scripts/pre-commit" ]; then
        print_status "Pre-commit script is executable"
    else
        print_warning "Pre-commit script is not executable"
    fi
else
    print_error "Pre-commit hook script not found at scripts/pre-commit"
fi

if [ -f "scripts/setup-hooks.sh" ]; then
    print_status "Hook setup script found"
    if [ -x "scripts/setup-hooks.sh" ]; then
        print_status "Hook setup script is executable"
    else
        print_warning "Hook setup script is not executable"
    fi
else
    print_error "Hook setup script not found at scripts/setup-hooks.sh"
fi

# Check quality tasks
if [ -f "gradle/quality-tasks.gradle" ]; then
    print_status "Quality tasks file found"
    
    if grep -q "apply.*quality-tasks.gradle" build.gradle.kts; then
        print_status "Quality tasks applied in build.gradle.kts"
    else
        print_warning "Quality tasks not applied in build.gradle.kts"
    fi
else
    print_error "Quality tasks file not found at gradle/quality-tasks.gradle"
fi

# Check documentation
if [ -f "CODE_QUALITY_SETUP.md" ]; then
    print_status "Code quality documentation found"
else
    print_warning "Code quality documentation not found at CODE_QUALITY_SETUP.md"
fi

echo ""
echo "=" * 60
echo "🎉 Code Quality Setup Validation Complete!"
echo ""
echo "Next steps:"
echo "1. Install Git hooks: ./scripts/setup-hooks.sh"
echo "2. Initialize quality tools: ./gradlew setupQualityTools"
echo "3. Run quality checks: ./gradlew codeQualityCheck"
echo "4. Review CODE_QUALITY_SETUP.md for usage instructions"
echo ""
echo "For issues or questions, refer to the documentation or run individual tool commands."