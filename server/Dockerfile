# Multi-stage build for Pocket Agent Server
# Build stage
FROM golang:1.21-alpine AS builder

# Install build dependencies
RUN apk add --no-cache git make gcc musl-dev

# Set working directory
WORKDIR /build

# Copy go mod files first for better caching
COPY go.mod go.sum ./
RUN go mod download

# Copy source code
COPY . .

# Build the binary with version information
ARG VERSION=dev
ARG BUILD_TIME
ARG GIT_COMMIT
RUN CGO_ENABLED=0 GOOS=linux GOARCH=amd64 go build \
    -ldflags="-w -s \
    -X main.version=${VERSION} \
    -X main.buildTime=${BUILD_TIME} \
    -X main.gitCommit=${GIT_COMMIT}" \
    -a -installsuffix cgo \
    -o pocket-agent-server \
    ./cmd/server

# Runtime stage
FROM alpine:3.19

# Install runtime dependencies
RUN apk add --no-cache \
    ca-certificates \
    tzdata \
    curl \
    bash \
    tini

# Install Claude CLI
# NOTE: This is a placeholder - adjust based on actual Claude CLI installation
# In production, you might want to copy a pre-downloaded binary or use a different method
RUN echo "#!/bin/sh" > /usr/local/bin/claude && \
    echo 'echo "Claude CLI placeholder - install actual CLI"' >> /usr/local/bin/claude && \
    chmod +x /usr/local/bin/claude

# Create non-root user
RUN addgroup -g 1000 pocket-agent && \
    adduser -D -u 1000 -G pocket-agent pocket-agent

# Create necessary directories
RUN mkdir -p /data /certs /config && \
    chown -R pocket-agent:pocket-agent /data /config

# Copy binary from builder
COPY --from=builder /build/pocket-agent-server /usr/local/bin/

# Copy default configuration
COPY --chown=pocket-agent:pocket-agent configs/docker.yaml /config/default.yaml

# Create health check script
RUN echo '#!/bin/sh' > /usr/local/bin/healthcheck && \
    echo 'curl -k -f https://localhost:8443/health || exit 1' >> /usr/local/bin/healthcheck && \
    chmod +x /usr/local/bin/healthcheck

# Switch to non-root user
USER pocket-agent

# Set environment variables
ENV PA_DATA_DIR=/data \
    PA_CONFIG_FILE=/config/config.yaml \
    PA_TLS_CERT=/certs/server.crt \
    PA_TLS_KEY=/certs/server.key \
    PA_LOG_LEVEL=info \
    PA_LOG_FORMAT=json

# Expose WebSocket port
EXPOSE 8443

# Expose metrics port (optional)
EXPOSE 9090

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=5s --retries=3 \
    CMD ["/usr/local/bin/healthcheck"]

# Use tini for proper signal handling
ENTRYPOINT ["/sbin/tini", "--"]

# Run the server
CMD ["/usr/local/bin/pocket-agent-server"]