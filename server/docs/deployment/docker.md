# Docker Deployment Guide

This guide covers deploying Pocket Agent Server using Docker and Docker Compose.

## Table of Contents

- [Prerequisites](#prerequisites)
- [Quick Start](#quick-start)
- [Building the Image](#building-the-image)
- [Running with Docker](#running-with-docker)
- [Docker Compose Setup](#docker-compose-setup)
- [Configuration](#configuration)
- [Persistent Storage](#persistent-storage)
- [Networking](#networking)
- [Security](#security)
- [Monitoring](#monitoring)
- [Troubleshooting](#troubleshooting)

## Prerequisites

- Docker 20.10 or later
- Docker Compose 2.0 or later (optional)
- TLS certificates (can be self-signed for development)
- Claude CLI accessible in the container

## Quick Start

```bash
# Clone the repository
git clone https://github.com/boyd/pocket_agent.git
cd pocket_agent/server

# Build the Docker image
docker build -t pocket-agent-server:latest .

# Generate self-signed certificates (development only)
mkdir -p certs
openssl req -x509 -newkey rsa:4096 -keyout certs/server.key -out certs/server.crt \
    -days 365 -nodes -subj "/CN=localhost"

# Run the container
docker run -d \
    --name pocket-agent \
    -p 8443:8443 \
    -v $(pwd)/data:/data \
    -v $(pwd)/certs:/certs:ro \
    pocket-agent-server:latest
```

## Building the Image

### Multi-Stage Dockerfile

```dockerfile
# Dockerfile
# Build stage
FROM golang:1.21-alpine AS builder

# Install build dependencies
RUN apk add --no-cache git make gcc musl-dev

# Set working directory
WORKDIR /build

# Copy go mod files
COPY go.mod go.sum ./
RUN go mod download

# Copy source code
COPY . .

# Build the binary
RUN CGO_ENABLED=0 GOOS=linux GOARCH=amd64 go build \
    -ldflags="-w -s -X main.version=$(git describe --tags --always)" \
    -o pocket-agent-server \
    ./cmd/server

# Runtime stage
FROM alpine:3.19

# Install runtime dependencies
RUN apk add --no-cache \
    ca-certificates \
    tzdata \
    curl \
    bash

# Install Claude CLI
# Note: Adjust this based on actual Claude CLI installation method
RUN curl -fsSL https://claude.ai/cli/install.sh | sh && \
    mv /root/.local/bin/claude /usr/local/bin/

# Create non-root user
RUN addgroup -g 1000 pocket-agent && \
    adduser -D -u 1000 -G pocket-agent pocket-agent

# Create necessary directories
RUN mkdir -p /data /certs /config && \
    chown -R pocket-agent:pocket-agent /data /config

# Copy binary from builder
COPY --from=builder /build/pocket-agent-server /usr/local/bin/

# Copy default config
COPY --chown=pocket-agent:pocket-agent config/docker.yaml /config/default.yaml

# Switch to non-root user
USER pocket-agent

# Set environment variables
ENV PA_DATA_DIR=/data \
    PA_CONFIG_FILE=/config/config.yaml \
    PA_TLS_CERT=/certs/server.crt \
    PA_TLS_KEY=/certs/server.key

# Expose WebSocket port
EXPOSE 8443

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=5s --retries=3 \
    CMD ["/usr/local/bin/pocket-agent-server", "health"]

# Entry point
ENTRYPOINT ["/usr/local/bin/pocket-agent-server"]
```

### Build Arguments

```bash
# Build with custom arguments
docker build \
    --build-arg VERSION=1.0.0 \
    --build-arg GOPROXY=https://proxy.golang.org \
    --build-arg CLAUDE_VERSION=latest \
    -t pocket-agent-server:1.0.0 .

# Multi-platform build
docker buildx build \
    --platform linux/amd64,linux/arm64 \
    -t pocket-agent-server:latest \
    --push .
```

### Build Optimization

```dockerfile
# .dockerignore
.git
.github
*.md
docs/
test/
scripts/
.env*
data/
certs/
*.log
coverage.*
.DS_Store
```

## Running with Docker

### Basic Run Command

```bash
# Run with default settings
docker run -d \
    --name pocket-agent \
    -p 8443:8443 \
    pocket-agent-server:latest

# Run with custom configuration
docker run -d \
    --name pocket-agent \
    -p 8443:8443 \
    -v $(pwd)/config.yaml:/config/config.yaml:ro \
    -v $(pwd)/data:/data \
    -v $(pwd)/certs:/certs:ro \
    -e PA_LOG_LEVEL=debug \
    pocket-agent-server:latest
```

### Advanced Run Options

```bash
# Run with resource limits
docker run -d \
    --name pocket-agent \
    -p 8443:8443 \
    --memory="1g" \
    --memory-swap="2g" \
    --cpus="2.0" \
    --restart=unless-stopped \
    -v $(pwd)/data:/data \
    -v $(pwd)/certs:/certs:ro \
    pocket-agent-server:latest

# Run with custom network
docker network create pocket-net
docker run -d \
    --name pocket-agent \
    --network pocket-net \
    -p 8443:8443 \
    -v $(pwd)/data:/data \
    -v $(pwd)/certs:/certs:ro \
    pocket-agent-server:latest
```

### Container Management

```bash
# View logs
docker logs -f pocket-agent

# Execute commands in container
docker exec -it pocket-agent sh

# View resource usage
docker stats pocket-agent

# Restart container
docker restart pocket-agent

# Stop and remove
docker stop pocket-agent
docker rm pocket-agent
```

## Docker Compose Setup

### Basic docker-compose.yml

```yaml
version: '3.8'

services:
  pocket-agent:
    image: pocket-agent-server:latest
    container_name: pocket-agent
    ports:
      - "8443:8443"
    volumes:
      - ./data:/data
      - ./certs:/certs:ro
      - ./config/production.yaml:/config/config.yaml:ro
    environment:
      - PA_LOG_LEVEL=info
      - PA_LOG_FORMAT=json
    restart: unless-stopped
    healthcheck:
      test: ["CMD", "/usr/local/bin/pocket-agent-server", "health"]
      interval: 30s
      timeout: 10s
      retries: 3
    networks:
      - pocket-net

networks:
  pocket-net:
    driver: bridge

volumes:
  data:
    driver: local
```

### Production docker-compose.yml

```yaml
version: '3.8'

services:
  pocket-agent:
    image: pocket-agent-server:${VERSION:-latest}
    container_name: pocket-agent
    ports:
      - "8443:8443"
    volumes:
      - pocket-data:/data
      - ./certs:/certs:ro
      - ./config/production.yaml:/config/config.yaml:ro
    environment:
      - PA_LOG_LEVEL=${LOG_LEVEL:-info}
      - PA_LOG_FORMAT=json
      - PA_MAX_CONNECTIONS=200
      - PA_EXECUTION_TIMEOUT=10m
    deploy:
      resources:
        limits:
          cpus: '2.0'
          memory: 2G
        reservations:
          cpus: '0.5'
          memory: 512M
    restart: always
    logging:
      driver: "json-file"
      options:
        max-size: "100m"
        max-file: "5"
    healthcheck:
      test: ["CMD", "/usr/local/bin/pocket-agent-server", "health"]
      interval: 30s
      timeout: 10s
      retries: 3
      start_period: 40s
    networks:
      - pocket-net
      - monitoring

  # Optional: Reverse proxy
  nginx:
    image: nginx:alpine
    container_name: pocket-nginx
    ports:
      - "443:443"
      - "80:80"
    volumes:
      - ./nginx/nginx.conf:/etc/nginx/nginx.conf:ro
      - ./nginx/certs:/etc/nginx/certs:ro
    depends_on:
      - pocket-agent
    restart: always
    networks:
      - pocket-net

  # Optional: Monitoring
  prometheus:
    image: prom/prometheus:latest
    container_name: pocket-prometheus
    volumes:
      - ./prometheus/prometheus.yml:/etc/prometheus/prometheus.yml:ro
      - prometheus-data:/prometheus
    command:
      - '--config.file=/etc/prometheus/prometheus.yml'
      - '--storage.tsdb.path=/prometheus'
    restart: always
    networks:
      - monitoring

networks:
  pocket-net:
    driver: bridge
  monitoring:
    driver: bridge

volumes:
  pocket-data:
    driver: local
    driver_opts:
      type: none
      o: bind
      device: /opt/pocket-agent/data
  prometheus-data:
    driver: local
```

### Environment File

```bash
# .env
VERSION=1.0.0
LOG_LEVEL=info
TLS_CERT_PATH=./certs/server.crt
TLS_KEY_PATH=./certs/server.key
DATA_PATH=/opt/pocket-agent/data
```

### Docker Compose Commands

```bash
# Start services
docker-compose up -d

# View logs
docker-compose logs -f pocket-agent

# Scale services (if configured)
docker-compose up -d --scale pocket-agent=3

# Stop services
docker-compose down

# Stop and remove volumes
docker-compose down -v

# Update and restart
docker-compose pull
docker-compose up -d

# Run one-off commands
docker-compose exec pocket-agent /bin/sh
```

## Configuration

### Container Configuration

```yaml
# config/docker.yaml
server:
  host: 0.0.0.0
  port: 8443
  tls:
    cert: /certs/server.crt
    key: /certs/server.key

data:
  dir: /data

logging:
  level: info
  format: json
  output: stdout

limits:
  max_connections: 100
  max_projects: 100
  message_size: 1048576

timeouts:
  execution: 5m
  idle: 5m
  ping_interval: 30s

claude:
  binary: /usr/local/bin/claude
```

### Environment Variables

All configuration options can be overridden with environment variables:

```bash
docker run -d \
    -e PA_SERVER_PORT=8444 \
    -e PA_LOG_LEVEL=debug \
    -e PA_MAX_CONNECTIONS=200 \
    -e PA_EXECUTION_TIMEOUT=10m \
    pocket-agent-server:latest
```

### Secrets Management

```yaml
# docker-compose with secrets
version: '3.8'

services:
  pocket-agent:
    image: pocket-agent-server:latest
    secrets:
      - server_cert
      - server_key
    environment:
      - PA_TLS_CERT=/run/secrets/server_cert
      - PA_TLS_KEY=/run/secrets/server_key

secrets:
  server_cert:
    file: ./certs/server.crt
  server_key:
    file: ./certs/server.key
```

## Persistent Storage

### Volume Configuration

```bash
# Create named volume
docker volume create pocket-agent-data

# Run with named volume
docker run -d \
    --name pocket-agent \
    -v pocket-agent-data:/data \
    pocket-agent-server:latest

# Backup volume
docker run --rm \
    -v pocket-agent-data:/data \
    -v $(pwd)/backup:/backup \
    alpine tar -czf /backup/data-$(date +%Y%m%d).tar.gz -C /data .

# Restore volume
docker run --rm \
    -v pocket-agent-data:/data \
    -v $(pwd)/backup:/backup \
    alpine tar -xzf /backup/data-20240101.tar.gz -C /data
```

### Bind Mounts vs Volumes

```yaml
# Bind mount (development)
volumes:
  - ./data:/data
  - ./certs:/certs:ro

# Named volumes (production)
volumes:
  - pocket-data:/data
  - pocket-certs:/certs:ro

volumes:
  pocket-data:
    driver: local
    driver_opts:
      type: nfs
      o: addr=nfs-server.local,rw
      device: ":/exports/pocket-agent"
```

### Data Persistence Strategies

```bash
# Regular backups with cron
# backup-cron.sh
#!/bin/bash
BACKUP_DIR="/backup/pocket-agent"
CONTAINER="pocket-agent"
DATE=$(date +%Y%m%d-%H%M%S)

# Create backup
docker exec $CONTAINER tar -czf - /data | \
    cat > "$BACKUP_DIR/data-$DATE.tar.gz"

# Keep only last 7 days
find "$BACKUP_DIR" -name "data-*.tar.gz" -mtime +7 -delete

# Add to crontab
# 0 2 * * * /path/to/backup-cron.sh
```

## Networking

### Port Configuration

```yaml
# Multiple port bindings
ports:
  - "8443:8443"        # WebSocket
  - "127.0.0.1:6060:6060"  # pprof (local only)
  - "9090:9090"        # Metrics (future)
```

### Custom Networks

```yaml
# Isolated network setup
networks:
  frontend:
    driver: bridge
  backend:
    driver: bridge
    internal: true

services:
  pocket-agent:
    networks:
      - frontend
      - backend
  
  claude-backend:
    networks:
      - backend
```

### Reverse Proxy Configuration

```nginx
# nginx/nginx.conf
events {
    worker_connections 1024;
}

http {
    upstream pocket-agent {
        server pocket-agent:8443;
    }

    server {
        listen 443 ssl http2;
        server_name pocket-agent.example.com;

        ssl_certificate /etc/nginx/certs/server.crt;
        ssl_certificate_key /etc/nginx/certs/server.key;
        ssl_protocols TLSv1.2 TLSv1.3;
        ssl_ciphers HIGH:!aNULL:!MD5;

        location /ws {
            proxy_pass https://pocket-agent;
            proxy_http_version 1.1;
            proxy_set_header Upgrade $http_upgrade;
            proxy_set_header Connection "upgrade";
            proxy_set_header Host $host;
            proxy_set_header X-Real-IP $remote_addr;
            proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
            proxy_set_header X-Forwarded-Proto $scheme;
            
            # Timeouts
            proxy_connect_timeout 7d;
            proxy_send_timeout 7d;
            proxy_read_timeout 7d;
        }

        location /health {
            proxy_pass https://pocket-agent;
        }
    }
}
```

## Security

### Running as Non-Root

```dockerfile
# Already configured in Dockerfile
USER pocket-agent

# Verify in container
docker exec pocket-agent whoami
# Output: pocket-agent
```

### Read-Only Root Filesystem

```yaml
# docker-compose.yml
services:
  pocket-agent:
    image: pocket-agent-server:latest
    read_only: true
    tmpfs:
      - /tmp
    volumes:
      - pocket-data:/data
      - ./certs:/certs:ro
```

### Security Options

```yaml
# Enhanced security
services:
  pocket-agent:
    image: pocket-agent-server:latest
    security_opt:
      - no-new-privileges:true
    cap_drop:
      - ALL
    cap_add:
      - CHOWN
      - SETUID
      - SETGID
```

### Secret Management

```bash
# Using Docker secrets
echo "your-secret-key" | docker secret create server_key -

# Or with Kubernetes secrets
kubectl create secret tls pocket-agent-tls \
    --cert=server.crt \
    --key=server.key
```

### Network Policies

```yaml
# Restrict network access
services:
  pocket-agent:
    networks:
      pocket-net:
        ipv4_address: 172.20.0.10
    extra_hosts:
      - "host.docker.internal:host-gateway"

networks:
  pocket-net:
    driver: bridge
    ipam:
      config:
        - subnet: 172.20.0.0/16
```

## Monitoring

### Health Checks

```bash
# Manual health check
docker exec pocket-agent /usr/local/bin/pocket-agent-server health

# Or via HTTP (if implemented)
curl -k https://localhost:8443/health
```

### Logging

```yaml
# Centralized logging
services:
  pocket-agent:
    logging:
      driver: "fluentd"
      options:
        fluentd-address: "localhost:24224"
        tag: "pocket-agent"

# Or JSON file with rotation
    logging:
      driver: "json-file"
      options:
        max-size: "100m"
        max-file: "5"
        labels: "app=pocket-agent"
```

### Metrics Collection

```yaml
# prometheus/prometheus.yml
global:
  scrape_interval: 15s

scrape_configs:
  - job_name: 'pocket-agent'
    static_configs:
      - targets: ['pocket-agent:9090']
```

### Container Monitoring

```bash
# Real-time stats
docker stats pocket-agent

# Detailed inspection
docker inspect pocket-agent

# Resource usage over time
docker run -d \
    --name cadvisor \
    --volume=/:/rootfs:ro \
    --volume=/var/run:/var/run:ro \
    --volume=/sys:/sys:ro \
    --volume=/var/lib/docker/:/var/lib/docker:ro \
    --publish=8080:8080 \
    google/cadvisor:latest
```

## Troubleshooting

### Common Issues

#### Container Exits Immediately

```bash
# Check logs
docker logs pocket-agent

# Run in foreground to see errors
docker run --rm -it pocket-agent-server:latest

# Check exit code
docker inspect pocket-agent --format='{{.State.ExitCode}}'
```

#### Permission Denied

```bash
# Fix volume permissions
docker exec pocket-agent chown -R pocket-agent:pocket-agent /data

# Or set permissions on host
sudo chown -R 1000:1000 ./data
```

#### Cannot Connect to WebSocket

```bash
# Check if container is listening
docker exec pocket-agent netstat -tlnp

# Test from inside container
docker exec pocket-agent curl -k https://localhost:8443/ws

# Check firewall rules
docker exec pocket-agent iptables -L -n
```

#### High Memory Usage

```bash
# Limit memory
docker update --memory="1g" --memory-swap="2g" pocket-agent

# Or in docker-compose
deploy:
  resources:
    limits:
      memory: 1G
```

### Debug Mode

```bash
# Run with debug logging
docker run -it --rm \
    -e PA_LOG_LEVEL=debug \
    -e PA_LOG_FORMAT=text \
    pocket-agent-server:latest

# Attach to running container
docker attach pocket-agent

# Execute debug commands
docker exec -it pocket-agent sh
```

### Container Shell Access

```bash
# Get shell in running container
docker exec -it pocket-agent sh

# Debug commands inside container
ps aux
netstat -tlnp
df -h
cat /proc/meminfo
```

### Rebuild and Update

```bash
# Rebuild with no cache
docker build --no-cache -t pocket-agent-server:latest .

# Update running container
docker-compose pull
docker-compose up -d

# Rolling update (if using Swarm)
docker service update --image pocket-agent-server:new pocket-agent
```

---

For more deployment options:
- [Systemd Deployment](./systemd.md)
- [Kubernetes Deployment](./kubernetes.md)
- [Cloud Deployment](./cloud.md)