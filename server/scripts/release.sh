#!/bin/bash
# release.sh - Release automation script for Pocket Agent Server
#
# This script automates the release process including building, packaging, and creating releases
#
# Usage: ./scripts/release.sh VERSION [options]
# Options:
#   --skip-tests         Skip running tests
#   --skip-docker        Skip Docker image build
#   --draft              Create draft release
#   --prerelease         Mark as pre-release
#   --dry-run            Perform dry run without creating release

set -euo pipefail

# Check if version is provided
if [[ $# -lt 1 ]]; then
    echo "Usage: $0 VERSION [options]"
    echo "Example: $0 v1.0.0 --draft"
    exit 1
fi

VERSION="$1"
shift

# Default values
SKIP_TESTS=false
SKIP_DOCKER=false
DRAFT=false
PRERELEASE=false
DRY_RUN=false

# Parse options
while [[ $# -gt 0 ]]; do
    case $1 in
        --skip-tests)
            SKIP_TESTS=true
            shift
            ;;
        --skip-docker)
            SKIP_DOCKER=true
            shift
            ;;
        --draft)
            DRAFT=true
            shift
            ;;
        --prerelease)
            PRERELEASE=true
            shift
            ;;
        --dry-run)
            DRY_RUN=true
            shift
            ;;
        *)
            echo "Unknown option: $1"
            exit 1
            ;;
    esac
done

# Colors for output
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

# Validate version format
if ! [[ "$VERSION" =~ ^v[0-9]+\.[0-9]+\.[0-9]+(-[a-zA-Z0-9.-]+)?$ ]]; then
    echo -e "${RED}Invalid version format. Use: v1.0.0 or v1.0.0-beta.1${NC}"
    exit 1
fi

echo -e "${GREEN}Preparing release $VERSION${NC}"

# Check for required tools
echo "Checking dependencies..."
MISSING_TOOLS=()

for tool in git go docker gh; do
    if ! command -v $tool &> /dev/null; then
        MISSING_TOOLS+=($tool)
    fi
done

if [[ ${#MISSING_TOOLS[@]} -gt 0 ]]; then
    echo -e "${RED}Missing required tools: ${MISSING_TOOLS[*]}${NC}"
    echo "Please install missing tools and try again."
    exit 1
fi

# Check git status
if [[ -n $(git status --porcelain) ]]; then
    echo -e "${RED}Working directory not clean. Commit or stash changes first.${NC}"
    exit 1
fi

# Update VERSION file
echo "$VERSION" > VERSION
git add VERSION

# Run tests
if [[ "$SKIP_TESTS" != "true" ]]; then
    echo -e "${YELLOW}Running tests...${NC}"
    make test
    if [[ $? -ne 0 ]]; then
        echo -e "${RED}Tests failed. Fix issues before releasing.${NC}"
        exit 1
    fi
fi

# Build binaries for multiple platforms
echo -e "${YELLOW}Building binaries...${NC}"
PLATFORMS=(
    "linux/amd64"
    "linux/arm64"
    "darwin/amd64"
    "darwin/arm64"
    "windows/amd64"
)

BUILD_DIR="dist/${VERSION}"
mkdir -p "$BUILD_DIR"

for platform in "${PLATFORMS[@]}"; do
    IFS='/' read -r -a parts <<< "$platform"
    OS="${parts[0]}"
    ARCH="${parts[1]}"
    
    OUTPUT="$BUILD_DIR/pocket-agent-server-${OS}-${ARCH}"
    if [[ "$OS" == "windows" ]]; then
        OUTPUT="${OUTPUT}.exe"
    fi
    
    echo "Building for ${OS}/${ARCH}..."
    ./scripts/build.sh \
        --version "$VERSION" \
        --output "$OUTPUT" \
        --os "$OS" \
        --arch "$ARCH" \
        --static
    
    # Create tarball
    TARBALL="$BUILD_DIR/pocket-agent-server-${VERSION}-${OS}-${ARCH}.tar.gz"
    tar -czf "$TARBALL" -C "$BUILD_DIR" "$(basename "$OUTPUT")"
    
    # Create checksum
    (cd "$BUILD_DIR" && shasum -a 256 "$(basename "$TARBALL")" > "$(basename "$TARBALL").sha256")
done

# Build Docker image
if [[ "$SKIP_DOCKER" != "true" ]]; then
    echo -e "${YELLOW}Building Docker image...${NC}"
    docker build \
        --build-arg VERSION="$VERSION" \
        --build-arg BUILD_TIME="$(date -u +"%Y-%m-%dT%H:%M:%SZ")" \
        --build-arg GIT_COMMIT="$(git rev-parse --short HEAD)" \
        -t "pocket-agent-server:$VERSION" \
        -t "pocket-agent-server:latest" \
        .
    
    if [[ "$DRY_RUN" != "true" ]]; then
        # Tag for registry (adjust registry URL)
        docker tag "pocket-agent-server:$VERSION" "ghcr.io/boyd/pocket-agent-server:$VERSION"
        docker tag "pocket-agent-server:latest" "ghcr.io/boyd/pocket-agent-server:latest"
        
        # Push to registry (requires authentication)
        echo "Pushing Docker images..."
        docker push "ghcr.io/boyd/pocket-agent-server:$VERSION"
        docker push "ghcr.io/boyd/pocket-agent-server:latest"
    fi
fi

# Create release notes
echo -e "${YELLOW}Generating release notes...${NC}"
RELEASE_NOTES="$BUILD_DIR/RELEASE_NOTES.md"

cat > "$RELEASE_NOTES" << EOF
# Pocket Agent Server $VERSION

Released: $(date -u +"%Y-%m-%d")

## Changes

$(git log --pretty=format:"- %s" $(git describe --tags --abbrev=0 2>/dev/null || echo "")..HEAD 2>/dev/null || echo "- Initial release")

## Downloads

### Binaries

| Platform | Architecture | Download |
|----------|--------------|----------|
| Linux | amd64 | [pocket-agent-server-${VERSION}-linux-amd64.tar.gz](https://github.com/boyd/pocket_agent/releases/download/${VERSION}/pocket-agent-server-${VERSION}-linux-amd64.tar.gz) |
| Linux | arm64 | [pocket-agent-server-${VERSION}-linux-arm64.tar.gz](https://github.com/boyd/pocket_agent/releases/download/${VERSION}/pocket-agent-server-${VERSION}-linux-arm64.tar.gz) |
| macOS | amd64 | [pocket-agent-server-${VERSION}-darwin-amd64.tar.gz](https://github.com/boyd/pocket_agent/releases/download/${VERSION}/pocket-agent-server-${VERSION}-darwin-amd64.tar.gz) |
| macOS | arm64 | [pocket-agent-server-${VERSION}-darwin-arm64.tar.gz](https://github.com/boyd/pocket_agent/releases/download/${VERSION}/pocket-agent-server-${VERSION}-darwin-arm64.tar.gz) |
| Windows | amd64 | [pocket-agent-server-${VERSION}-windows-amd64.tar.gz](https://github.com/boyd/pocket_agent/releases/download/${VERSION}/pocket-agent-server-${VERSION}-windows-amd64.tar.gz) |

### Docker

\`\`\`bash
docker pull ghcr.io/boyd/pocket-agent-server:$VERSION
\`\`\`

## Installation

### Binary

\`\`\`bash
# Download and extract
curl -L https://github.com/boyd/pocket_agent/releases/download/${VERSION}/pocket-agent-server-${VERSION}-linux-amd64.tar.gz | tar -xz

# Install
sudo mv pocket-agent-server /usr/local/bin/
sudo chmod +x /usr/local/bin/pocket-agent-server

# Run
pocket-agent-server --config config.yaml
\`\`\`

### Docker

\`\`\`bash
docker run -d \\
  --name pocket-agent \\
  -p 8443:8443 \\
  -v \$(pwd)/data:/data \\
  -v \$(pwd)/certs:/certs:ro \\
  ghcr.io/boyd/pocket-agent-server:$VERSION
\`\`\`

### Systemd

\`\`\`bash
# Use the installation script
sudo ./scripts/install.sh
\`\`\`

## Checksums

Verify downloads with SHA256 checksums available for each file.

## Documentation

- [Installation Guide](https://github.com/boyd/pocket_agent/blob/${VERSION}/server/README.md)
- [API Documentation](https://github.com/boyd/pocket_agent/blob/${VERSION}/server/docs/api/README.md)
- [Deployment Guide](https://github.com/boyd/pocket_agent/blob/${VERSION}/server/docs/deployment/README.md)
EOF

if [[ "$DRY_RUN" == "true" ]]; then
    echo -e "${YELLOW}Dry run complete. Release artifacts in: $BUILD_DIR${NC}"
    echo "Release notes:"
    cat "$RELEASE_NOTES"
    exit 0
fi

# Commit version change
git commit -m "chore: release $VERSION"

# Create and push tag
echo -e "${YELLOW}Creating git tag...${NC}"
git tag -a "$VERSION" -m "Release $VERSION"
git push origin main
git push origin "$VERSION"

# Create GitHub release
echo -e "${YELLOW}Creating GitHub release...${NC}"
GH_FLAGS=""
if [[ "$DRAFT" == "true" ]]; then
    GH_FLAGS="$GH_FLAGS --draft"
fi
if [[ "$PRERELEASE" == "true" ]]; then
    GH_FLAGS="$GH_FLAGS --prerelease"
fi

gh release create "$VERSION" \
    --title "Pocket Agent Server $VERSION" \
    --notes-file "$RELEASE_NOTES" \
    $GH_FLAGS \
    "$BUILD_DIR"/*.tar.gz \
    "$BUILD_DIR"/*.sha256

echo -e "${GREEN}Release $VERSION created successfully!${NC}"
echo "View release: https://github.com/boyd/pocket_agent/releases/tag/$VERSION"