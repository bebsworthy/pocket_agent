#!/bin/bash
# install.sh - Installation script for Pocket Agent Server
#
# This script installs Pocket Agent Server as a systemd service on Linux systems
#
# Usage: sudo ./install.sh [options]
# Options:
#   --prefix PATH        Installation prefix (default: /usr/local)
#   --user USER          Service user (default: pocket-agent)
#   --group GROUP        Service group (default: pocket-agent)
#   --skip-user          Skip user creation
#   --skip-systemd       Skip systemd service installation
#   --uninstall          Uninstall Pocket Agent Server

set -euo pipefail

# Default values
PREFIX="/usr/local"
SERVICE_USER="pocket-agent"
SERVICE_GROUP="pocket-agent"
SKIP_USER=false
SKIP_SYSTEMD=false
UNINSTALL=false

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Parse arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        --prefix)
            PREFIX="$2"
            shift 2
            ;;
        --user)
            SERVICE_USER="$2"
            shift 2
            ;;
        --group)
            SERVICE_GROUP="$2"
            shift 2
            ;;
        --skip-user)
            SKIP_USER=true
            shift
            ;;
        --skip-systemd)
            SKIP_SYSTEMD=true
            shift
            ;;
        --uninstall)
            UNINSTALL=true
            shift
            ;;
        *)
            echo "Unknown option: $1"
            echo "Usage: $0 [--prefix PATH] [--user USER] [--group GROUP] [--skip-user] [--skip-systemd] [--uninstall]"
            exit 1
            ;;
    esac
done

# Check if running as root
if [[ $EUID -ne 0 ]]; then
   echo -e "${RED}This script must be run as root${NC}"
   exit 1
fi

# Uninstall function
uninstall() {
    echo -e "${YELLOW}Uninstalling Pocket Agent Server...${NC}"
    
    # Stop and disable service
    if systemctl is-active --quiet pocket-agent-server; then
        echo "Stopping service..."
        systemctl stop pocket-agent-server
    fi
    
    if systemctl is-enabled --quiet pocket-agent-server 2>/dev/null; then
        echo "Disabling service..."
        systemctl disable pocket-agent-server
    fi
    
    # Remove files
    echo "Removing files..."
    rm -f /etc/systemd/system/pocket-agent-server.service
    rm -f "$PREFIX/bin/pocket-agent-server"
    rm -rf /etc/pocket-agent
    rm -rf /var/lib/pocket-agent
    rm -rf /var/log/pocket-agent
    
    # Reload systemd
    systemctl daemon-reload
    
    echo -e "${GREEN}Uninstallation complete${NC}"
    exit 0
}

# Run uninstall if requested
if [[ "$UNINSTALL" == "true" ]]; then
    uninstall
fi

echo -e "${GREEN}Installing Pocket Agent Server...${NC}"

# Check if binary exists
if [[ ! -f "./bin/pocket-agent-server" ]]; then
    echo -e "${RED}Error: pocket-agent-server binary not found in ./bin/${NC}"
    echo "Please build the binary first with: make build"
    exit 1
fi

# Create user and group
if [[ "$SKIP_USER" != "true" ]]; then
    echo "Creating service user..."
    if ! id "$SERVICE_USER" &>/dev/null; then
        groupadd -r "$SERVICE_GROUP" 2>/dev/null || true
        useradd -r -g "$SERVICE_GROUP" -s /bin/false -d /var/lib/pocket-agent -c "Pocket Agent Server" "$SERVICE_USER"
    else
        echo -e "${YELLOW}User $SERVICE_USER already exists${NC}"
    fi
fi

# Create directories
echo "Creating directories..."
mkdir -p "$PREFIX/bin"
mkdir -p /etc/pocket-agent/certs
mkdir -p /var/lib/pocket-agent/data
mkdir -p /var/log/pocket-agent

# Install binary
echo "Installing binary..."
cp ./bin/pocket-agent-server "$PREFIX/bin/"
chmod 755 "$PREFIX/bin/pocket-agent-server"

# Install configuration
echo "Installing configuration..."
if [[ -f "./configs/production.yaml" ]]; then
    cp ./configs/production.yaml /etc/pocket-agent/config.yaml
else
    # Create default configuration
    cat > /etc/pocket-agent/config.yaml << EOF
server:
  host: 0.0.0.0
  port: 8443
  tls:
    cert: /etc/pocket-agent/certs/server.crt
    key: /etc/pocket-agent/certs/server.key

data:
  dir: /var/lib/pocket-agent/data

logging:
  level: info
  format: json
  file: /var/log/pocket-agent/server.log

limits:
  max_connections: 100
  max_projects: 100
  
timeouts:
  execution: 5m
  idle: 5m
EOF
fi

# Create environment file
cat > /etc/pocket-agent/environment << EOF
# Pocket Agent Server environment variables
PA_LOG_LEVEL=info
PA_LOG_FORMAT=json
EOF

# Set permissions
echo "Setting permissions..."
chown -R "$SERVICE_USER:$SERVICE_GROUP" /etc/pocket-agent
chown -R "$SERVICE_USER:$SERVICE_GROUP" /var/lib/pocket-agent
chown -R "$SERVICE_USER:$SERVICE_GROUP" /var/log/pocket-agent
chmod 750 /etc/pocket-agent
chmod 640 /etc/pocket-agent/config.yaml
chmod 640 /etc/pocket-agent/environment
chmod 750 /etc/pocket-agent/certs

# Install systemd service
if [[ "$SKIP_SYSTEMD" != "true" ]]; then
    echo "Installing systemd service..."
    if [[ -f "./scripts/systemd/pocket-agent-server.service" ]]; then
        cp ./scripts/systemd/pocket-agent-server.service /etc/systemd/system/
    else
        echo -e "${RED}Warning: systemd service file not found${NC}"
    fi
    
    # Update service file with correct paths
    sed -i "s|/usr/local/bin/pocket-agent-server|$PREFIX/bin/pocket-agent-server|g" /etc/systemd/system/pocket-agent-server.service
    sed -i "s|User=pocket-agent|User=$SERVICE_USER|g" /etc/systemd/system/pocket-agent-server.service
    sed -i "s|Group=pocket-agent|Group=$SERVICE_GROUP|g" /etc/systemd/system/pocket-agent-server.service
    
    # Reload systemd
    systemctl daemon-reload
fi

# Generate self-signed certificates for testing
if [[ ! -f /etc/pocket-agent/certs/server.crt ]]; then
    echo "Generating self-signed certificates..."
    openssl req -x509 -newkey rsa:4096 \
        -keyout /etc/pocket-agent/certs/server.key \
        -out /etc/pocket-agent/certs/server.crt \
        -days 365 -nodes \
        -subj "/C=US/ST=State/L=City/O=Organization/CN=localhost" \
        2>/dev/null
    chown "$SERVICE_USER:$SERVICE_GROUP" /etc/pocket-agent/certs/*
    chmod 600 /etc/pocket-agent/certs/server.key
    chmod 644 /etc/pocket-agent/certs/server.crt
    echo -e "${YELLOW}Note: Self-signed certificates generated. Replace with proper certificates for production.${NC}"
fi

echo -e "${GREEN}Installation complete!${NC}"
echo
echo "Next steps:"
echo "1. Review and edit the configuration: /etc/pocket-agent/config.yaml"
echo "2. Replace the self-signed certificates in /etc/pocket-agent/certs/"
echo "3. Start the service: systemctl start pocket-agent-server"
echo "4. Enable auto-start: systemctl enable pocket-agent-server"
echo "5. Check status: systemctl status pocket-agent-server"
echo
echo "To test the installation:"
echo "  $PREFIX/bin/pocket-agent-server --version"
echo
echo "To uninstall:"
echo "  sudo $0 --uninstall"