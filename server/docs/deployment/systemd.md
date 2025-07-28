# Systemd Deployment Guide

This guide covers deploying Pocket Agent Server as a systemd service on Linux systems.

## Table of Contents

- [Prerequisites](#prerequisites)
- [Installation](#installation)
- [Service Configuration](#service-configuration)
- [TLS Certificate Setup](#tls-certificate-setup)
- [User and Permissions](#user-and-permissions)
- [Service Management](#service-management)
- [Logging](#logging)
- [Monitoring](#monitoring)
- [Security Hardening](#security-hardening)
- [Troubleshooting](#troubleshooting)

## Prerequisites

- Linux system with systemd (Ubuntu 20.04+, Debian 10+, RHEL 8+, etc.)
- Go 1.21+ (for building from source)
- Claude CLI installed system-wide
- Root or sudo access for installation

## Installation

### From Binary

```bash
# Download latest release (adjust URL for actual release)
wget https://github.com/boyd/pocket_agent/releases/download/v1.0.0/pocket-agent-server-linux-amd64.tar.gz

# Extract binary
sudo tar -xzf pocket-agent-server-linux-amd64.tar.gz -C /usr/local/bin/

# Make executable
sudo chmod +x /usr/local/bin/pocket-agent-server

# Verify installation
/usr/local/bin/pocket-agent-server --version
```

### From Source

```bash
# Clone repository
git clone https://github.com/boyd/pocket_agent.git
cd pocket_agent/server

# Build binary
make build

# Install binary
sudo cp ./bin/pocket-agent-server /usr/local/bin/
sudo chmod +x /usr/local/bin/pocket-agent-server

# Install configuration
sudo mkdir -p /etc/pocket-agent
sudo cp ./config/production.yaml /etc/pocket-agent/config.yaml

# Create directories
sudo mkdir -p /var/lib/pocket-agent/data
sudo mkdir -p /var/log/pocket-agent
sudo mkdir -p /etc/pocket-agent/certs
```

## Service Configuration

### Basic Service File

Create `/etc/systemd/system/pocket-agent-server.service`:

```ini
[Unit]
Description=Pocket Agent Server
Documentation=https://github.com/boyd/pocket_agent
After=network-online.target
Wants=network-online.target

[Service]
Type=simple
User=pocket-agent
Group=pocket-agent
WorkingDirectory=/var/lib/pocket-agent

# Executable and arguments
ExecStart=/usr/local/bin/pocket-agent-server \
    --config /etc/pocket-agent/config.yaml

# Restart configuration
Restart=always
RestartSec=5
StartLimitInterval=60
StartLimitBurst=3

# Environment
Environment="PA_DATA_DIR=/var/lib/pocket-agent/data"
Environment="PA_LOG_FILE=/var/log/pocket-agent/server.log"
Environment="PA_TLS_CERT=/etc/pocket-agent/certs/server.crt"
Environment="PA_TLS_KEY=/etc/pocket-agent/certs/server.key"

# Security
NoNewPrivileges=true
PrivateTmp=true

# Resource limits
LimitNOFILE=65536
LimitNPROC=4096

# Logging
StandardOutput=journal
StandardError=journal
SyslogIdentifier=pocket-agent

[Install]
WantedBy=multi-user.target
```

### Advanced Service File

```ini
[Unit]
Description=Pocket Agent Server
Documentation=https://github.com/boyd/pocket_agent
After=network-online.target
Wants=network-online.target
# Add dependencies if using external services
# After=postgresql.service redis.service

[Service]
Type=simple
User=pocket-agent
Group=pocket-agent
WorkingDirectory=/var/lib/pocket-agent

# Executable with all options
ExecStart=/usr/local/bin/pocket-agent-server \
    --config /etc/pocket-agent/config.yaml \
    --host 0.0.0.0 \
    --port 8443 \
    --data-dir /var/lib/pocket-agent/data \
    --log-level info

# Pre-start actions
ExecStartPre=/usr/bin/mkdir -p /var/lib/pocket-agent/data
ExecStartPre=/usr/bin/chown -R pocket-agent:pocket-agent /var/lib/pocket-agent
ExecStartPre=/usr/bin/chmod 750 /var/lib/pocket-agent/data

# Graceful shutdown
ExecStop=/bin/kill -TERM $MAINPID
TimeoutStopSec=30
KillMode=mixed
KillSignal=SIGTERM

# Restart configuration
Restart=always
RestartSec=5
StartLimitInterval=60
StartLimitBurst=3

# Environment
EnvironmentFile=-/etc/pocket-agent/environment
Environment="PA_DATA_DIR=/var/lib/pocket-agent/data"
Environment="PA_LOG_FILE=/var/log/pocket-agent/server.log"
Environment="PA_TLS_CERT=/etc/pocket-agent/certs/server.crt"
Environment="PA_TLS_KEY=/etc/pocket-agent/certs/server.key"
Environment="GOMAXPROCS=4"

# Security hardening
NoNewPrivileges=true
PrivateTmp=true
ProtectSystem=strict
ProtectHome=true
ReadWritePaths=/var/lib/pocket-agent /var/log/pocket-agent
ProtectKernelTunables=true
ProtectKernelModules=true
ProtectControlGroups=true
RestrictRealtime=true
RestrictNamespaces=true
RestrictSUIDSGID=true
RemoveIPC=true
PrivateMounts=true

# Capabilities
CapabilityBoundingSet=
AmbientCapabilities=

# System call filtering
SystemCallFilter=@system-service
SystemCallErrorNumber=EPERM

# Resource limits
LimitNOFILE=65536
LimitNPROC=4096
LimitCORE=0
MemoryMax=2G
CPUQuota=200%

# Logging
StandardOutput=journal+console
StandardError=journal+console
SyslogIdentifier=pocket-agent

[Install]
WantedBy=multi-user.target
```

### Environment File

Create `/etc/pocket-agent/environment`:

```bash
# Production environment variables
PA_LOG_LEVEL=info
PA_LOG_FORMAT=json
PA_MAX_CONNECTIONS=200
PA_MAX_PROJECTS=100
PA_EXECUTION_TIMEOUT=10m
PA_IDLE_TIMEOUT=10m

# Claude configuration
PA_CLAUDE_BINARY=/usr/local/bin/claude
PA_CLAUDE_DEFAULT_MODEL=claude-3-5-sonnet-20241022

# Performance tuning
GOMAXPROCS=4
GOGC=100
```

### Configuration File

Create `/etc/pocket-agent/config.yaml`:

```yaml
server:
  host: 0.0.0.0
  port: 8443
  tls:
    cert: /etc/pocket-agent/certs/server.crt
    key: /etc/pocket-agent/certs/server.key
    min_version: "1.2"

data:
  dir: /var/lib/pocket-agent/data

logging:
  level: info
  format: json
  file: /var/log/pocket-agent/server.log
  max_size: 100  # MB
  max_backups: 5
  max_age: 30    # days
  compress: true

limits:
  max_connections: 200
  max_projects: 100
  message_size: 1048576
  
timeouts:
  execution: 10m
  idle: 10m
  ping_interval: 30s
  pong_timeout: 60s

log_rotation:
  size: 104857600   # 100MB
  count: 10000
  compress: true

claude:
  binary: /usr/local/bin/claude
  default_model: claude-3-5-sonnet-20241022
  allowed_tools:
    - read
    - write
    - run

monitoring:
  metrics_enabled: true
  metrics_port: 9090
  health_check_interval: 30s
```

## TLS Certificate Setup

### Using Let's Encrypt

```bash
# Install certbot
sudo apt-get update
sudo apt-get install certbot

# Generate certificate
sudo certbot certonly --standalone \
    -d pocket-agent.example.com \
    --agree-tos \
    --email admin@example.com

# Copy certificates
sudo cp /etc/letsencrypt/live/pocket-agent.example.com/fullchain.pem \
    /etc/pocket-agent/certs/server.crt
sudo cp /etc/letsencrypt/live/pocket-agent.example.com/privkey.pem \
    /etc/pocket-agent/certs/server.key

# Set permissions
sudo chown pocket-agent:pocket-agent /etc/pocket-agent/certs/*
sudo chmod 644 /etc/pocket-agent/certs/server.crt
sudo chmod 600 /etc/pocket-agent/certs/server.key
```

### Auto-renewal with systemd timer

Create `/etc/systemd/system/pocket-agent-cert-renew.service`:

```ini
[Unit]
Description=Renew Pocket Agent TLS certificates
After=network-online.target

[Service]
Type=oneshot
ExecStart=/usr/bin/certbot renew --quiet --deploy-hook "/bin/systemctl reload pocket-agent-server"
```

Create `/etc/systemd/system/pocket-agent-cert-renew.timer`:

```ini
[Unit]
Description=Run Pocket Agent cert renewal twice daily

[Timer]
OnCalendar=*-*-* 00,12:00:00
RandomizedDelaySec=3600
Persistent=true

[Install]
WantedBy=timers.target
```

Enable the timer:

```bash
sudo systemctl enable pocket-agent-cert-renew.timer
sudo systemctl start pocket-agent-cert-renew.timer
```

### Self-Signed Certificates (Development)

```bash
# Generate self-signed certificate
sudo openssl req -x509 -newkey rsa:4096 \
    -keyout /etc/pocket-agent/certs/server.key \
    -out /etc/pocket-agent/certs/server.crt \
    -days 365 -nodes \
    -subj "/C=US/ST=State/L=City/O=Organization/CN=localhost"

# Set permissions
sudo chown pocket-agent:pocket-agent /etc/pocket-agent/certs/*
sudo chmod 644 /etc/pocket-agent/certs/server.crt
sudo chmod 600 /etc/pocket-agent/certs/server.key
```

## User and Permissions

### Create Service User

```bash
# Create system user
sudo useradd --system \
    --shell /bin/false \
    --home-dir /var/lib/pocket-agent \
    --comment "Pocket Agent Server" \
    pocket-agent

# Create directories
sudo mkdir -p /var/lib/pocket-agent/data
sudo mkdir -p /var/log/pocket-agent
sudo mkdir -p /etc/pocket-agent/certs

# Set ownership
sudo chown -R pocket-agent:pocket-agent /var/lib/pocket-agent
sudo chown -R pocket-agent:pocket-agent /var/log/pocket-agent
sudo chown -R pocket-agent:pocket-agent /etc/pocket-agent

# Set permissions
sudo chmod 750 /var/lib/pocket-agent
sudo chmod 750 /var/lib/pocket-agent/data
sudo chmod 750 /var/log/pocket-agent
sudo chmod 750 /etc/pocket-agent
sudo chmod 750 /etc/pocket-agent/certs
```

### File Permissions

```bash
# Configuration files
sudo chmod 640 /etc/pocket-agent/config.yaml
sudo chmod 640 /etc/pocket-agent/environment

# Binary
sudo chmod 755 /usr/local/bin/pocket-agent-server

# Certificates
sudo chmod 644 /etc/pocket-agent/certs/server.crt
sudo chmod 600 /etc/pocket-agent/certs/server.key

# Verify permissions
sudo -u pocket-agent ls -la /var/lib/pocket-agent/
sudo -u pocket-agent ls -la /etc/pocket-agent/
```

## Service Management

### Enable and Start

```bash
# Reload systemd
sudo systemctl daemon-reload

# Enable service to start on boot
sudo systemctl enable pocket-agent-server.service

# Start service
sudo systemctl start pocket-agent-server.service

# Check status
sudo systemctl status pocket-agent-server.service
```

### Common Operations

```bash
# Stop service
sudo systemctl stop pocket-agent-server.service

# Restart service
sudo systemctl restart pocket-agent-server.service

# Reload configuration (if supported)
sudo systemctl reload pocket-agent-server.service

# View full status
sudo systemctl status -l pocket-agent-server.service

# Follow logs
sudo journalctl -u pocket-agent-server.service -f
```

### Service Dependencies

```ini
# If dependent on other services, add to [Unit] section:
After=network-online.target postgresql.service redis.service
Wants=network-online.target
Requires=postgresql.service redis.service
```

## Logging

### Journal Logs

```bash
# View all logs
sudo journalctl -u pocket-agent-server.service

# Follow logs in real-time
sudo journalctl -u pocket-agent-server.service -f

# View logs since boot
sudo journalctl -u pocket-agent-server.service -b

# View logs from last hour
sudo journalctl -u pocket-agent-server.service --since "1 hour ago"

# Export logs
sudo journalctl -u pocket-agent-server.service > pocket-agent.log

# JSON format
sudo journalctl -u pocket-agent-server.service -o json-pretty
```

### File Logging

Configure rsyslog to separate logs:

Create `/etc/rsyslog.d/50-pocket-agent.conf`:

```
if $programname == 'pocket-agent' then /var/log/pocket-agent/server.log
& stop
```

Restart rsyslog:
```bash
sudo systemctl restart rsyslog
```

### Log Rotation

Create `/etc/logrotate.d/pocket-agent`:

```
/var/log/pocket-agent/*.log {
    daily
    missingok
    rotate 14
    compress
    delaycompress
    notifempty
    create 0640 pocket-agent pocket-agent
    sharedscripts
    postrotate
        /bin/kill -USR1 $(cat /var/run/pocket-agent/pocket-agent.pid 2>/dev/null) 2>/dev/null || true
    endscript
}
```

## Monitoring

### Basic Health Check

Create `/usr/local/bin/pocket-agent-health`:

```bash
#!/bin/bash
# Health check script

# Check if service is active
if ! systemctl is-active --quiet pocket-agent-server; then
    echo "ERROR: Service is not running"
    exit 1
fi

# Check WebSocket endpoint
if ! timeout 5 curl -k -s https://localhost:8443/health > /dev/null; then
    echo "ERROR: Health endpoint not responding"
    exit 1
fi

# Check disk space
DISK_USAGE=$(df /var/lib/pocket-agent | awk 'NR==2 {print int($5)}')
if [ "$DISK_USAGE" -gt 90 ]; then
    echo "WARNING: Disk usage is ${DISK_USAGE}%"
fi

echo "OK: Service is healthy"
exit 0
```

### Monitoring with Prometheus Node Exporter

```yaml
# /etc/prometheus/prometheus.yml
scrape_configs:
  - job_name: 'pocket-agent'
    static_configs:
      - targets: ['localhost:9090']
        labels:
          service: 'pocket-agent-server'
```

### Systemd Timer for Health Checks

Create `/etc/systemd/system/pocket-agent-health.service`:

```ini
[Unit]
Description=Pocket Agent Health Check

[Service]
Type=oneshot
ExecStart=/usr/local/bin/pocket-agent-health
StandardOutput=journal
StandardError=journal
```

Create `/etc/systemd/system/pocket-agent-health.timer`:

```ini
[Unit]
Description=Run Pocket Agent health check every 5 minutes

[Timer]
OnBootSec=5min
OnUnitActiveSec=5min

[Install]
WantedBy=timers.target
```

Enable timer:
```bash
sudo systemctl enable pocket-agent-health.timer
sudo systemctl start pocket-agent-health.timer
```

## Security Hardening

### Firewall Configuration

```bash
# UFW (Ubuntu/Debian)
sudo ufw allow 8443/tcp comment "Pocket Agent WebSocket"
sudo ufw reload

# firewalld (RHEL/CentOS)
sudo firewall-cmd --permanent --add-port=8443/tcp
sudo firewall-cmd --reload

# iptables
sudo iptables -A INPUT -p tcp --dport 8443 -j ACCEPT -m comment --comment "Pocket Agent"
sudo iptables-save > /etc/iptables/rules.v4
```

### SELinux (RHEL/CentOS)

```bash
# Create custom policy
cat > pocket-agent.te << 'EOF'
module pocket-agent 1.0;

require {
    type init_t;
    type user_home_t;
    class file { read write };
}

#============= init_t ==============
allow init_t user_home_t:file { read write };
EOF

# Compile and install
checkmodule -M -m -o pocket-agent.mod pocket-agent.te
semodule_package -o pocket-agent.pp -m pocket-agent.mod
sudo semodule -i pocket-agent.pp
```

### AppArmor (Ubuntu/Debian)

Create `/etc/apparmor.d/usr.local.bin.pocket-agent-server`:

```
#include <tunables/global>

/usr/local/bin/pocket-agent-server {
  #include <abstractions/base>
  #include <abstractions/nameservice>
  
  capability net_bind_service,
  capability chown,
  capability setuid,
  capability setgid,
  
  /usr/local/bin/pocket-agent-server mr,
  /etc/pocket-agent/** r,
  /var/lib/pocket-agent/** rw,
  /var/log/pocket-agent/** rw,
  /proc/sys/kernel/random/uuid r,
  /usr/local/bin/claude Px,
  
  # Network access
  network inet stream,
  network inet6 stream,
}
```

Enable profile:
```bash
sudo apparmor_parser -r /etc/apparmor.d/usr.local.bin.pocket-agent-server
```

### Resource Limits

Add to `/etc/security/limits.d/pocket-agent.conf`:

```
pocket-agent soft nofile 65536
pocket-agent hard nofile 65536
pocket-agent soft nproc 4096
pocket-agent hard nproc 4096
pocket-agent soft memlock unlimited
pocket-agent hard memlock unlimited
```

## Troubleshooting

### Service Won't Start

```bash
# Check status and errors
sudo systemctl status pocket-agent-server.service
sudo journalctl -u pocket-agent-server.service -n 50

# Common issues:
# - Port already in use
sudo lsof -i :8443

# - Permission denied
sudo -u pocket-agent /usr/local/bin/pocket-agent-server --config /etc/pocket-agent/config.yaml

# - Missing dependencies
ldd /usr/local/bin/pocket-agent-server

# - Configuration errors
/usr/local/bin/pocket-agent-server --config /etc/pocket-agent/config.yaml --validate
```

### Debug Mode

```bash
# Run manually in debug mode
sudo -u pocket-agent PA_LOG_LEVEL=debug /usr/local/bin/pocket-agent-server \
    --config /etc/pocket-agent/config.yaml

# Or update service temporarily
sudo systemctl edit pocket-agent-server.service

# Add under [Service]:
Environment="PA_LOG_LEVEL=debug"
Environment="PA_LOG_FORMAT=text"
```

### Performance Issues

```bash
# Check resource usage
systemctl status pocket-agent-server.service
systemd-cgtop

# Adjust limits if needed
sudo systemctl edit pocket-agent-server.service

# Add under [Service]:
LimitNOFILE=131072
MemoryMax=4G
CPUQuota=400%
```

### Backup and Restore

```bash
#!/bin/bash
# backup-pocket-agent.sh

BACKUP_DIR="/backup/pocket-agent/$(date +%Y%m%d-%H%M%S)"
mkdir -p "$BACKUP_DIR"

# Stop service
sudo systemctl stop pocket-agent-server.service

# Backup data
sudo tar -czf "$BACKUP_DIR/data.tar.gz" -C /var/lib/pocket-agent .
sudo tar -czf "$BACKUP_DIR/config.tar.gz" -C /etc/pocket-agent .

# Start service
sudo systemctl start pocket-agent-server.service

echo "Backup completed: $BACKUP_DIR"
```

### Emergency Recovery

```bash
# If service is stuck
sudo systemctl kill -s KILL pocket-agent-server.service

# Reset failed state
sudo systemctl reset-failed pocket-agent-server.service

# Clean start
sudo rm -f /var/lib/pocket-agent/.lock
sudo systemctl start pocket-agent-server.service
```

---

For more deployment options:
- [Docker Deployment](./docker.md)
- [Kubernetes Deployment](./kubernetes.md)
- [Cloud Deployment](./cloud.md)