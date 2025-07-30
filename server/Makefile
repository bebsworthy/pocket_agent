# Makefile for Pocket Agent Server

# Variables
BINARY_NAME := pocket-agent-server
MAIN_PATH := ./cmd/server
BUILD_DIR := ./bin
COVERAGE_DIR := ./coverage
DOCKER_IMAGE := pocket-agent/server

# Version information
VERSION := $(shell git describe --tags --always --dirty 2>/dev/null || echo "dev")
BUILD_TIME := $(shell date -u +"%Y-%m-%dT%H:%M:%SZ")
GIT_COMMIT := $(shell git rev-parse --short HEAD 2>/dev/null || echo "unknown")

# Build flags
LDFLAGS := -X main.Version=$(VERSION) -X main.BuildTime=$(BUILD_TIME) -X main.GitCommit=$(GIT_COMMIT)
GOFLAGS := -v -ldflags "$(LDFLAGS)"

# Go commands
GOCMD := go
GOBUILD := $(GOCMD) build
GOTEST := $(GOCMD) test
GOGET := $(GOCMD) get
GOMOD := $(GOCMD) mod
GOFMT := gofumpt
GOLINT := golangci-lint

# Colors for output
COLOR_RESET := \033[0m
COLOR_GREEN := \033[32m
COLOR_YELLOW := \033[33m
COLOR_BLUE := \033[34m

.PHONY: help
help: ## Display this help screen
	@echo "$(COLOR_BLUE)Pocket Agent Server - Available targets:$(COLOR_RESET)"
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | awk 'BEGIN {FS = ":.*?## "}; {printf "  $(COLOR_GREEN)%-20s$(COLOR_RESET) %s\n", $$1, $$2}'

.PHONY: build
build: ## Build the application
	@echo "$(COLOR_BLUE)Building $(BINARY_NAME)...$(COLOR_RESET)"
	@mkdir -p $(BUILD_DIR)
	$(GOBUILD) $(GOFLAGS) -o $(BUILD_DIR)/$(BINARY_NAME) $(MAIN_PATH)
	@echo "$(COLOR_GREEN)Build complete: $(BUILD_DIR)/$(BINARY_NAME)$(COLOR_RESET)"

.PHONY: build-all
build-all: ## Build for all platforms
	@echo "$(COLOR_BLUE)Building for all platforms...$(COLOR_RESET)"
	@mkdir -p $(BUILD_DIR)
	GOOS=linux GOARCH=amd64 $(GOBUILD) $(GOFLAGS) -o $(BUILD_DIR)/$(BINARY_NAME)-linux-amd64 $(MAIN_PATH)
	GOOS=linux GOARCH=arm64 $(GOBUILD) $(GOFLAGS) -o $(BUILD_DIR)/$(BINARY_NAME)-linux-arm64 $(MAIN_PATH)
	GOOS=darwin GOARCH=amd64 $(GOBUILD) $(GOFLAGS) -o $(BUILD_DIR)/$(BINARY_NAME)-darwin-amd64 $(MAIN_PATH)
	GOOS=darwin GOARCH=arm64 $(GOBUILD) $(GOFLAGS) -o $(BUILD_DIR)/$(BINARY_NAME)-darwin-arm64 $(MAIN_PATH)
	@echo "$(COLOR_GREEN)Multi-platform build complete$(COLOR_RESET)"

.PHONY: test
test: ## Run tests with coverage
	@echo "$(COLOR_BLUE)Running tests...$(COLOR_RESET)"
	@mkdir -p $(COVERAGE_DIR)
	$(GOTEST) -v -race -coverprofile=$(COVERAGE_DIR)/coverage.out -covermode=atomic ./...
	@echo "$(COLOR_GREEN)Tests complete$(COLOR_RESET)"

.PHONY: test-integration
test-integration: ## Run integration tests
	@echo "$(COLOR_BLUE)Running integration tests...$(COLOR_RESET)"
	$(GOTEST) -v -race -tags=integration ./test/integration/...
	@echo "$(COLOR_GREEN)Integration tests complete$(COLOR_RESET)"

.PHONY: coverage
coverage: test ## Generate coverage report
	@echo "$(COLOR_BLUE)Generating coverage report...$(COLOR_RESET)"
	$(GOCMD) tool cover -html=$(COVERAGE_DIR)/coverage.out -o $(COVERAGE_DIR)/coverage.html
	@echo "$(COLOR_GREEN)Coverage report: $(COVERAGE_DIR)/coverage.html$(COLOR_RESET)"

.PHONY: coverage-text
coverage-text: test ## Display coverage in terminal
	@echo "$(COLOR_BLUE)Coverage summary:$(COLOR_RESET)"
	$(GOCMD) tool cover -func=$(COVERAGE_DIR)/coverage.out

.PHONY: bench
bench: ## Run benchmarks
	@echo "$(COLOR_BLUE)Running benchmarks...$(COLOR_RESET)"
	$(GOTEST) -bench=. -benchmem ./...

.PHONY: lint
lint: ## Run linters
	@echo "$(COLOR_BLUE)Running linters...$(COLOR_RESET)"
	$(GOLINT) run ./...
	@echo "$(COLOR_GREEN)Linting complete$(COLOR_RESET)"

.PHONY: fmt
fmt: ## Format code
	@echo "$(COLOR_BLUE)Formatting code...$(COLOR_RESET)"
	$(GOFMT) -w -l .
	$(GOCMD) fmt ./...
	@echo "$(COLOR_GREEN)Formatting complete$(COLOR_RESET)"

.PHONY: vet
vet: ## Run go vet
	@echo "$(COLOR_BLUE)Running go vet...$(COLOR_RESET)"
	$(GOCMD) vet ./...
	@echo "$(COLOR_GREEN)Vet complete$(COLOR_RESET)"

.PHONY: sec
sec: ## Run security scan
	@echo "$(COLOR_BLUE)Running security scan...$(COLOR_RESET)"
	@which gosec > /dev/null || (echo "Installing gosec..." && go install github.com/securego/gosec/v2/cmd/gosec@latest)
	gosec -fmt=json -out=$(COVERAGE_DIR)/security-report.json ./... || true
	gosec ./...
	@which govulncheck > /dev/null || (echo "Installing govulncheck..." && go install golang.org/x/vuln/cmd/govulncheck@latest)
	govulncheck ./...
	@echo "$(COLOR_GREEN)Security scan complete$(COLOR_RESET)"

.PHONY: deps
deps: ## Download dependencies
	@echo "$(COLOR_BLUE)Downloading dependencies...$(COLOR_RESET)"
	$(GOMOD) download
	$(GOMOD) verify
	@echo "$(COLOR_GREEN)Dependencies downloaded$(COLOR_RESET)"

.PHONY: deps-update
deps-update: ## Update dependencies
	@echo "$(COLOR_BLUE)Updating dependencies...$(COLOR_RESET)"
	$(GOGET) -u ./...
	$(GOMOD) tidy
	@echo "$(COLOR_GREEN)Dependencies updated$(COLOR_RESET)"

.PHONY: deps-tidy
deps-tidy: ## Tidy dependencies
	@echo "$(COLOR_BLUE)Tidying dependencies...$(COLOR_RESET)"
	$(GOMOD) tidy
	@echo "$(COLOR_GREEN)Dependencies tidied$(COLOR_RESET)"

.PHONY: install-tools
install-tools: ## Install development tools
	@echo "$(COLOR_BLUE)Installing development tools...$(COLOR_RESET)"
	@echo "Installing golangci-lint..."
	@curl -sSfL https://raw.githubusercontent.com/golangci/golangci-lint/master/install.sh | sh -s -- -b $$(go env GOPATH)/bin v2.3.0
	@echo "Installing gofumpt..."
	$(GOCMD) install mvdan.cc/gofumpt@v0.8.0
	@echo "Installing gosec..."
	$(GOCMD) install github.com/securego/gosec/v2/cmd/gosec@v2.22.7
	@echo "Installing govulncheck..."
	$(GOCMD) install golang.org/x/vuln/cmd/govulncheck@v1.1.4
	@echo "Installing godoc..."
	$(GOCMD) install golang.org/x/tools/cmd/godoc@latest
	@echo "Installing air..."
	$(GOCMD) install github.com/air-verse/air@v1.62.0
	@echo "Installing pre-commit..."
	@which pre-commit > /dev/null || pip install pre-commit
	@echo "$(COLOR_GREEN)Tools installed$(COLOR_RESET)"

.PHONY: pre-commit
pre-commit: fmt lint test ## Run pre-commit checks
	@echo "$(COLOR_GREEN)Pre-commit checks passed$(COLOR_RESET)"

.PHONY: pre-commit-install
pre-commit-install: ## Install pre-commit hooks
	@echo "$(COLOR_BLUE)Installing pre-commit hooks...$(COLOR_RESET)"
	pre-commit install
	@echo "$(COLOR_GREEN)Pre-commit hooks installed$(COLOR_RESET)"

.PHONY: docs
docs: ## Generate documentation
	@echo "$(COLOR_BLUE)Generating documentation...$(COLOR_RESET)"
	@which godoc > /dev/null || go install golang.org/x/tools/cmd/godoc@latest
	@echo "Documentation server will start at http://localhost:6060"
	@echo "Press Ctrl+C to stop"
	godoc -http=:6060

.PHONY: clean
clean: ## Clean build artifacts
	@echo "$(COLOR_BLUE)Cleaning build artifacts...$(COLOR_RESET)"
	@rm -rf $(BUILD_DIR)
	@rm -rf $(COVERAGE_DIR)
	@rm -rf vendor/
	@rm -rf dist/
	@echo "$(COLOR_GREEN)Clean complete$(COLOR_RESET)"

.PHONY: docker-build
docker-build: ## Build Docker image
	@echo "$(COLOR_BLUE)Building Docker image...$(COLOR_RESET)"
	docker build -t $(DOCKER_IMAGE):$(VERSION) -t $(DOCKER_IMAGE):latest .
	@echo "$(COLOR_GREEN)Docker build complete$(COLOR_RESET)"

.PHONY: docker-push
docker-push: ## Push Docker image
	@echo "$(COLOR_BLUE)Pushing Docker image...$(COLOR_RESET)"
	docker push $(DOCKER_IMAGE):$(VERSION)
	docker push $(DOCKER_IMAGE):latest
	@echo "$(COLOR_GREEN)Docker push complete$(COLOR_RESET)"

.PHONY: run
run: build ## Build and run the application
	@echo "$(COLOR_BLUE)Running $(BINARY_NAME)...$(COLOR_RESET)"
	$(BUILD_DIR)/$(BINARY_NAME) -config run/config.json

.PHONY: dev
dev: ## Run with hot reload (requires air)
	@echo "$(COLOR_BLUE)Running in development mode...$(COLOR_RESET)"
	@which air > /dev/null || (echo "Installing air..." && go install github.com/air-verse/air@latest)
	air

.PHONY: version
version: ## Display version information
	@echo "Version:    $(VERSION)"
	@echo "Build Time: $(BUILD_TIME)"
	@echo "Git Commit: $(GIT_COMMIT)"

.PHONY: check
check: fmt lint vet test ## Run all checks
	@echo "$(COLOR_GREEN)All checks passed$(COLOR_RESET)"

.PHONY: ci
ci: deps check build ## Run CI pipeline
	@echo "$(COLOR_GREEN)CI pipeline complete$(COLOR_RESET)"

# Default target
.DEFAULT_GOAL := help