.PHONY: build test clean install

# Binary name
BINARY_NAME=claude-mock

# Build directory
BUILD_DIR=../bin

# Installation directory
INSTALL_DIR=$(HOME)/bin

# Build the binary
build:
	@echo "Building $(BINARY_NAME)..."
	@mkdir -p $(BUILD_DIR)
	@go build -o $(BUILD_DIR)/$(BINARY_NAME) .
	@echo "Built $(BUILD_DIR)/$(BINARY_NAME)"

# Run tests
test:
	@echo "Running tests..."
	@go test -v ./...

# Install binary to user bin directory
install: build
	@echo "Installing $(BINARY_NAME) to $(INSTALL_DIR)..."
	@mkdir -p $(INSTALL_DIR)
	@cp $(BUILD_DIR)/$(BINARY_NAME) $(INSTALL_DIR)/
	@echo "Installed to $(INSTALL_DIR)/$(BINARY_NAME)"

# Clean build artifacts
clean:
	@echo "Cleaning..."
	@rm -f $(BUILD_DIR)/$(BINARY_NAME)
	@go clean

# Run with example log file
run-example: build
	@if [ -z "$(LOG_FILE)" ]; then \
		echo "Usage: make run-example LOG_FILE=/path/to/messages.jsonl"; \
		exit 1; \
	fi
	@echo "Test prompt" | CLAUDE_MOCK_LOG_FILE=$(LOG_FILE) $(BUILD_DIR)/$(BINARY_NAME) -p

# Show help
help:
	@echo "Available targets:"
	@echo "  build       - Build the claude-mock binary"
	@echo "  test        - Run tests"
	@echo "  install     - Install binary to ~/bin"
	@echo "  clean       - Clean build artifacts"
	@echo "  run-example - Run with example (requires LOG_FILE=/path/to/log.jsonl)"
	@echo "  help        - Show this help message"