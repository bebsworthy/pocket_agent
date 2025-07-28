#!/bin/bash
# Test runner for WebSocket API tests

set -e

echo "=== Running WebSocket API Tests ==="
echo

# Colors for output
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Build the Claude mock executable
echo "Building Claude mock..."
go build -o test/mocks/claude-mock test/mocks/cmd/claude-mock/main.go

# Run unit tests
echo
echo "Running unit tests..."
go test -v -race ./internal/validation/... -cover

# Run integration tests
echo
echo "Running integration tests..."
go test -v -race ./test/integration/... -timeout 30s

# Run performance tests (only if not in CI)
if [ -z "$CI" ]; then
    echo
    echo "Running performance tests (this may take a while)..."
    go test -v ./test/performance/... -timeout 5m
else
    echo
    echo -e "${YELLOW}Skipping performance tests in CI environment${NC}"
fi

# Run benchmarks (short version)
echo
echo "Running benchmarks..."
go test -bench=. -benchtime=10s ./test/performance/... -run=^$

# Generate coverage report
echo
echo "Generating coverage report..."
go test -coverprofile=coverage.out -covermode=atomic ./...
go tool cover -html=coverage.out -o coverage.html

echo
echo -e "${GREEN}All tests completed successfully!${NC}"
echo "Coverage report generated at: coverage.html"