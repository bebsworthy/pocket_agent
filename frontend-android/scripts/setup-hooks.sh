#!/bin/bash

# Setup script for Git hooks in Pocket Agent Mobile App
# This script installs pre-commit hooks for code quality enforcement

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Function to print colored output
print_status() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Get the root directory of the git repository
ROOT_DIR=$(git rev-parse --show-toplevel)
HOOKS_DIR="$ROOT_DIR/.git/hooks"
SCRIPTS_DIR="$ROOT_DIR/frontend/scripts"

print_status "Setting up Git hooks for code quality..."

# Create .git/hooks directory if it doesn't exist
mkdir -p "$HOOKS_DIR"

# Copy pre-commit hook
if [ -f "$SCRIPTS_DIR/pre-commit" ]; then
    cp "$SCRIPTS_DIR/pre-commit" "$HOOKS_DIR/pre-commit"
    chmod +x "$HOOKS_DIR/pre-commit"
    print_status "✅ Pre-commit hook installed successfully"
else
    print_error "Pre-commit script not found at $SCRIPTS_DIR/pre-commit"
    exit 1
fi

# Create pre-push hook for additional checks
cat > "$HOOKS_DIR/pre-push" << 'EOF'
#!/bin/bash

# Pre-push hook for Pocket Agent Mobile App
# This script runs comprehensive quality checks before push

set -e

echo "🚀 Running pre-push quality checks..."

# Get the root directory of the git repository
ROOT_DIR=$(git rev-parse --show-toplevel)
cd "$ROOT_DIR/frontend"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Function to print colored output
print_status() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Check if gradlew exists
if [ ! -f "./gradlew" ]; then
    print_error "gradlew not found. Please run this script from the project root."
    exit 1
fi

# Make gradlew executable
chmod +x ./gradlew

# Run comprehensive quality checks
print_status "Running full quality check suite..."
if ! ./gradlew fullQualityCheck; then
    print_error "Full quality check failed. Please fix the issues before pushing."
    exit 1
fi

print_status "Generating code coverage report..."
if ! ./gradlew jacocoTestReport; then
    print_warning "Code coverage report generation failed."
fi

print_status "✅ All pre-push checks passed!"
echo "🚀 Push can proceed."
EOF

chmod +x "$HOOKS_DIR/pre-push"
print_status "✅ Pre-push hook installed successfully"

# Create commit-msg hook for commit message validation
cat > "$HOOKS_DIR/commit-msg" << 'EOF'
#!/bin/bash

# Commit message validation hook for Pocket Agent Mobile App
# This script validates commit message format

commit_regex='^(feat|fix|docs|style|refactor|test|chore|build|ci|perf|revert)(\(.+\))?: .{1,50}'

error_msg="Aborting commit. Your commit message is invalid. Please use the format:
<type>(<scope>): <subject>

Where <type> is one of: feat, fix, docs, style, refactor, test, chore, build, ci, perf, revert
Examples:
  feat(auth): add biometric authentication
  fix(ui): resolve layout issue on small screens
  docs(readme): update installation instructions
  test(login): add unit tests for login flow"

if ! grep -qE "$commit_regex" "$1"; then
    echo "$error_msg" >&2
    exit 1
fi
EOF

chmod +x "$HOOKS_DIR/commit-msg"
print_status "✅ Commit message validation hook installed successfully"

print_status "🎉 All Git hooks have been set up successfully!"
print_status "The following hooks are now active:"
print_status "  - pre-commit: Runs code quality checks before each commit"
print_status "  - pre-push: Runs comprehensive tests before each push"
print_status "  - commit-msg: Validates commit message format"
print_status ""
print_status "You can disable hooks temporarily by using:"
print_status "  git commit --no-verify"
print_status "  git push --no-verify"