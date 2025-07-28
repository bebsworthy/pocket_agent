#!/bin/bash
# build.sh - Build script for Pocket Agent Server
#
# This script builds the Pocket Agent Server binary with version information
#
# Usage: ./scripts/build.sh [options]
# Options:
#   --version VERSION    Version string (default: git describe or 'dev')
#   --output PATH        Output path for binary (default: ./bin/pocket-agent-server)
#   --os OS              Target OS (default: current OS)
#   --arch ARCH          Target architecture (default: current arch)
#   --static             Build static binary
#   --debug              Include debug symbols
#   --clean              Clean before building

set -euo pipefail

# Default values
VERSION=""
OUTPUT="./bin/pocket-agent-server"
TARGET_OS=""
TARGET_ARCH=""
STATIC=false
DEBUG=false
CLEAN=false
BUILD_TIME=$(date -u +"%Y-%m-%dT%H:%M:%SZ")
GIT_COMMIT=$(git rev-parse --short HEAD 2>/dev/null || echo "unknown")

# Colors for output
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

# Parse arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        --version)
            VERSION="$2"
            shift 2
            ;;
        --output)
            OUTPUT="$2"
            shift 2
            ;;
        --os)
            TARGET_OS="$2"
            shift 2
            ;;
        --arch)
            TARGET_ARCH="$2"
            shift 2
            ;;
        --static)
            STATIC=true
            shift
            ;;
        --debug)
            DEBUG=true
            shift
            ;;
        --clean)
            CLEAN=true
            shift
            ;;
        *)
            echo "Unknown option: $1"
            echo "Usage: $0 [--version VERSION] [--output PATH] [--os OS] [--arch ARCH] [--static] [--debug] [--clean]"
            exit 1
            ;;
    esac
done

# Determine version if not specified
if [[ -z "$VERSION" ]]; then
    if [[ -f "VERSION" ]]; then
        VERSION=$(cat VERSION)
    elif command -v git &> /dev/null && git rev-parse --git-dir &> /dev/null; then
        VERSION=$(git describe --tags --always --dirty 2>/dev/null || echo "dev")
    else
        VERSION="dev"
    fi
fi

# Set target OS and architecture
if [[ -z "$TARGET_OS" ]]; then
    TARGET_OS=$(go env GOOS)
fi

if [[ -z "$TARGET_ARCH" ]]; then
    TARGET_ARCH=$(go env GOARCH)
fi

echo -e "${GREEN}Building Pocket Agent Server${NC}"
echo "Version: $VERSION"
echo "Target: ${TARGET_OS}/${TARGET_ARCH}"
echo "Output: $OUTPUT"
echo "Git Commit: $GIT_COMMIT"
echo "Build Time: $BUILD_TIME"

# Clean if requested
if [[ "$CLEAN" == "true" ]]; then
    echo -e "${YELLOW}Cleaning...${NC}"
    rm -rf ./bin
    go clean -cache
fi

# Create output directory
mkdir -p "$(dirname "$OUTPUT")"

# Build flags
BUILD_FLAGS="-trimpath"
if [[ "$STATIC" == "true" ]]; then
    BUILD_FLAGS="$BUILD_FLAGS -a -installsuffix cgo"
    export CGO_ENABLED=0
fi

# Linker flags
LDFLAGS="-X main.version=${VERSION}"
LDFLAGS="$LDFLAGS -X main.buildTime=${BUILD_TIME}"
LDFLAGS="$LDFLAGS -X main.gitCommit=${GIT_COMMIT}"

if [[ "$DEBUG" != "true" ]]; then
    LDFLAGS="$LDFLAGS -w -s"
fi

# Build the binary
echo -e "${YELLOW}Building...${NC}"
GOOS="$TARGET_OS" GOARCH="$TARGET_ARCH" go build \
    $BUILD_FLAGS \
    -ldflags="$LDFLAGS" \
    -o "$OUTPUT" \
    ./cmd/server

if [[ $? -ne 0 ]]; then
    echo -e "${RED}Build failed${NC}"
    exit 1
fi

# Strip binary if not debug
if [[ "$DEBUG" != "true" ]] && [[ "$TARGET_OS" == "$(go env GOOS)" ]]; then
    if command -v strip &> /dev/null; then
        echo "Stripping binary..."
        strip "$OUTPUT"
    fi
fi

# Make executable
chmod +x "$OUTPUT"

# Display binary info
echo -e "${GREEN}Build successful!${NC}"
echo "Binary: $OUTPUT"
echo "Size: $(ls -lh "$OUTPUT" | awk '{print $5}')"

# Test version output
if [[ "$TARGET_OS" == "$(go env GOOS)" ]] && [[ "$TARGET_ARCH" == "$(go env GOARCH)" ]]; then
    echo "Version check: $("$OUTPUT" --version 2>/dev/null || echo "version flag not implemented")"
fi