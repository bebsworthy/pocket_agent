# TLS Certificate Setup Guide

This guide covers setting up TLS certificates for secure WebSocket connections with Pocket Agent Server.

## Table of Contents

- [Overview](#overview)
- [Certificate Requirements](#certificate-requirements)
- [Self-Signed Certificates](#self-signed-certificates)
- [Let's Encrypt Certificates](#lets-encrypt-certificates)
- [Commercial Certificates](#commercial-certificates)
- [Certificate Management](#certificate-management)
- [Client Configuration](#client-configuration)
- [Troubleshooting](#troubleshooting)

## Overview

Pocket Agent Server requires TLS certificates for secure WebSocket (WSS) connections. This guide covers various methods to obtain and configure certificates.

### Certificate Types

1. **Self-Signed**: For development and testing
2. **Let's Encrypt**: Free, automated certificates for production
3. **Commercial CA**: Traditional SSL certificates from providers

## Certificate Requirements

### Technical Requirements

- **Key Size**: Minimum 2048-bit RSA or 256-bit ECC
- **Signature Algorithm**: SHA-256 or higher
- **Validity Period**: At least 30 days remaining
- **Common Name (CN)**: Must match your server's domain name
- **Subject Alternative Names (SAN)**: Include all domains/IPs

### File Formats

- **Certificate**: PEM format (.crt, .pem)
- **Private Key**: PEM format (.key, .pem)
- **No password protection** on private key

## Self-Signed Certificates

### Quick Generation

```bash
# Generate self-signed certificate for localhost
openssl req -x509 -newkey rsa:4096 -keyout server.key -out server.crt \
    -days 365 -nodes -subj "/CN=localhost"
```

### Advanced Generation with SAN

Create a configuration file `cert.conf`:

```ini
[req]
distinguished_name = req_distinguished_name
x509_extensions = v3_req
prompt = no

[req_distinguished_name]
C = US
ST = State
L = City
O = Organization
OU = IT Department
CN = pocket-agent.local

[v3_req]
keyUsage = critical, digitalSignature, keyAgreement
extendedKeyUsage = serverAuth
subjectAltName = @alt_names

[alt_names]
DNS.1 = localhost
DNS.2 = pocket-agent.local
DNS.3 = *.pocket-agent.local
IP.1 = 127.0.0.1
IP.2 = 192.168.1.100
```

Generate certificate:

```bash
# Generate private key
openssl genrsa -out server.key 4096

# Generate certificate
openssl req -new -x509 -key server.key -out server.crt -days 365 -config cert.conf
```

### Verify Certificate

```bash
# View certificate details
openssl x509 -in server.crt -text -noout

# Verify certificate matches key
openssl x509 -noout -modulus -in server.crt | openssl md5
openssl rsa -noout -modulus -in server.key | openssl md5
# Output should match
```

## Let's Encrypt Certificates

### Using Certbot (Standalone)

```bash
# Install certbot
sudo apt-get update
sudo apt-get install certbot

# Generate certificate (requires port 80 to be open)
sudo certbot certonly --standalone \
    -d pocket-agent.example.com \
    -d www.pocket-agent.example.com \
    --agree-tos \
    --email admin@example.com \
    --non-interactive
```

### Using Certbot (DNS Challenge)

```bash
# For wildcard certificates or when port 80 is not available
sudo certbot certonly --manual \
    --preferred-challenges dns \
    -d "*.pocket-agent.example.com" \
    -d pocket-agent.example.com \
    --agree-tos \
    --email admin@example.com
```

### Using acme.sh

```bash
# Install acme.sh
curl https://get.acme.sh | sh

# Generate certificate with DNS API (Cloudflare example)
export CF_Token="your-cloudflare-api-token"
export CF_Zone_ID="your-zone-id"

~/.acme.sh/acme.sh --issue \
    -d pocket-agent.example.com \
    -d "*.pocket-agent.example.com" \
    --dns dns_cf
```

### Automated Renewal

Create `/etc/systemd/system/certbot-renew.service`:

```ini
[Unit]
Description=Certbot Renewal
After=network.target

[Service]
Type=oneshot
ExecStart=/usr/bin/certbot renew --quiet --deploy-hook "/bin/systemctl reload pocket-agent-server"
```

Create `/etc/systemd/system/certbot-renew.timer`:

```ini
[Unit]
Description=Run certbot twice daily

[Timer]
OnCalendar=*-*-* 00,12:00:00
RandomizedDelaySec=3600
Persistent=true

[Install]
WantedBy=timers.target
```

Enable automatic renewal:

```bash
sudo systemctl enable certbot-renew.timer
sudo systemctl start certbot-renew.timer
```

## Commercial Certificates

### Generate CSR (Certificate Signing Request)

```bash
# Generate private key
openssl genrsa -out server.key 4096

# Generate CSR
openssl req -new -key server.key -out server.csr \
    -subj "/C=US/ST=State/L=City/O=Organization/CN=pocket-agent.example.com"

# View CSR
openssl req -text -noout -verify -in server.csr
```

### CSR with SAN

Create `csr.conf`:

```ini
[req]
distinguished_name = req_distinguished_name
req_extensions = v3_req
prompt = no

[req_distinguished_name]
C = US
ST = State
L = City
O = Organization Name
OU = IT Department
CN = pocket-agent.example.com

[v3_req]
subjectAltName = @alt_names

[alt_names]
DNS.1 = pocket-agent.example.com
DNS.2 = www.pocket-agent.example.com
DNS.3 = api.pocket-agent.example.com
```

Generate CSR:

```bash
openssl req -new -key server.key -out server.csr -config csr.conf
```

### Install Certificate Chain

After receiving certificates from CA:

```bash
# Combine certificates (if needed)
cat server.crt intermediate.crt > fullchain.crt

# Verify certificate chain
openssl verify -CAfile ca-bundle.crt fullchain.crt
```

## Certificate Management

### File Permissions

```bash
# Set proper ownership and permissions
sudo chown pocket-agent:pocket-agent /etc/pocket-agent/certs/*
sudo chmod 644 /etc/pocket-agent/certs/server.crt
sudo chmod 600 /etc/pocket-agent/certs/server.key
```

### Certificate Rotation Script

```bash
#!/bin/bash
# rotate-certs.sh - Certificate rotation helper

CERT_DIR="/etc/pocket-agent/certs"
BACKUP_DIR="/etc/pocket-agent/certs/backup"
TIMESTAMP=$(date +%Y%m%d-%H%M%S)

# Create backup
mkdir -p "$BACKUP_DIR"
cp "$CERT_DIR/server.crt" "$BACKUP_DIR/server.crt.$TIMESTAMP"
cp "$CERT_DIR/server.key" "$BACKUP_DIR/server.key.$TIMESTAMP"

# Install new certificates (passed as arguments)
NEW_CERT="$1"
NEW_KEY="$2"

if [ -z "$NEW_CERT" ] || [ -z "$NEW_KEY" ]; then
    echo "Usage: $0 <new-cert> <new-key>"
    exit 1
fi

# Verify new certificate
openssl x509 -in "$NEW_CERT" -text -noout > /dev/null 2>&1
if [ $? -ne 0 ]; then
    echo "Invalid certificate file"
    exit 1
fi

# Install new certificates
cp "$NEW_CERT" "$CERT_DIR/server.crt"
cp "$NEW_KEY" "$CERT_DIR/server.key"

# Set permissions
chown pocket-agent:pocket-agent "$CERT_DIR"/*
chmod 644 "$CERT_DIR/server.crt"
chmod 600 "$CERT_DIR/server.key"

# Reload service
systemctl reload pocket-agent-server

echo "Certificates rotated successfully"
```

### Certificate Monitoring

```bash
#!/bin/bash
# check-cert-expiry.sh - Monitor certificate expiration

CERT_FILE="/etc/pocket-agent/certs/server.crt"
WARNING_DAYS=30

# Get expiration date
EXPIRY_DATE=$(openssl x509 -enddate -noout -in "$CERT_FILE" | cut -d= -f2)
EXPIRY_EPOCH=$(date -d "$EXPIRY_DATE" +%s)
CURRENT_EPOCH=$(date +%s)
DAYS_LEFT=$(( ($EXPIRY_EPOCH - $CURRENT_EPOCH) / 86400 ))

echo "Certificate expires in $DAYS_LEFT days ($EXPIRY_DATE)"

if [ $DAYS_LEFT -lt $WARNING_DAYS ]; then
    echo "WARNING: Certificate expires soon!"
    # Send alert (email, webhook, etc.)
    # mail -s "Certificate Expiry Warning" admin@example.com < /dev/null
    exit 1
fi

exit 0
```

Add to cron:

```bash
# Check daily at 9 AM
0 9 * * * /usr/local/bin/check-cert-expiry.sh
```

## Client Configuration

### Browser/JavaScript Clients

For self-signed certificates in development:

```javascript
// Development only - accepts self-signed certificates
const ws = new WebSocket('wss://localhost:8443/ws');

// For Node.js clients with self-signed certs
const WebSocket = require('ws');
const ws = new WebSocket('wss://localhost:8443/ws', {
    rejectUnauthorized: false // Development only!
});
```

### Adding CA Certificate to System

```bash
# Ubuntu/Debian
sudo cp ca-cert.crt /usr/local/share/ca-certificates/
sudo update-ca-certificates

# RHEL/CentOS
sudo cp ca-cert.crt /etc/pki/ca-trust/source/anchors/
sudo update-ca-trust

# macOS
sudo security add-trusted-cert -d -r trustRoot -k /Library/Keychains/System.keychain ca-cert.crt
```

### Android Client Configuration

For self-signed certificates:

1. Export certificate in DER format:
```bash
openssl x509 -outform der -in server.crt -out server.der
```

2. Install on Android:
   - Settings → Security → Install from storage
   - Select the .der file
   - Name the certificate
   - Choose "VPN and apps" for certificate use

### curl/wget Testing

```bash
# Test with self-signed certificate
curl -k https://localhost:8443/health

# Test with CA bundle
curl --cacert ca-bundle.crt https://pocket-agent.example.com:8443/health

# Verbose SSL debugging
curl -v --cacert ca-bundle.crt https://pocket-agent.example.com:8443/health
```

## Troubleshooting

### Common Issues

#### Certificate Verification Failed

```bash
# Check certificate details
openssl s_client -connect localhost:8443 -servername localhost

# Check certificate chain
openssl s_client -connect pocket-agent.example.com:8443 -showcerts

# Verify specific certificate
openssl verify -CAfile ca-bundle.crt server.crt
```

#### Certificate/Key Mismatch

```bash
# Check if certificate and key match
CERT_MD5=$(openssl x509 -noout -modulus -in server.crt | openssl md5)
KEY_MD5=$(openssl rsa -noout -modulus -in server.key | openssl md5)

if [ "$CERT_MD5" = "$KEY_MD5" ]; then
    echo "Certificate and key match"
else
    echo "ERROR: Certificate and key do not match!"
fi
```

#### Wrong Certificate Format

```bash
# Convert DER to PEM
openssl x509 -inform der -in server.der -out server.pem

# Convert PFX/P12 to PEM
openssl pkcs12 -in server.pfx -out server.pem -nodes

# Remove password from key
openssl rsa -in server-encrypted.key -out server.key
```

#### Permission Issues

```bash
# Check file permissions
ls -la /etc/pocket-agent/certs/

# Fix permissions
sudo chown pocket-agent:pocket-agent /etc/pocket-agent/certs/*
sudo chmod 644 /etc/pocket-agent/certs/server.crt
sudo chmod 600 /etc/pocket-agent/certs/server.key

# Test as service user
sudo -u pocket-agent openssl x509 -in /etc/pocket-agent/certs/server.crt -text -noout
```

### SSL/TLS Debugging

```bash
# Test TLS versions
openssl s_client -connect localhost:8443 -tls1_2
openssl s_client -connect localhost:8443 -tls1_3

# Check cipher suites
openssl s_client -connect localhost:8443 -cipher 'ECDHE-RSA-AES256-GCM-SHA384'

# Full SSL scan
sslscan localhost:8443

# Or using nmap
nmap --script ssl-enum-ciphers -p 8443 localhost
```

### Certificate Best Practices

1. **Use Strong Keys**: Minimum 2048-bit RSA or 256-bit ECC
2. **Secure Storage**: Protect private keys with appropriate permissions
3. **Regular Rotation**: Rotate certificates before expiration
4. **Monitor Expiration**: Set up automated monitoring and alerts
5. **Backup Keys**: Keep secure backups of private keys
6. **Use SAN**: Include all domains/IPs in Subject Alternative Names
7. **Pin Certificates**: Consider certificate pinning for mobile apps
8. **HSTS**: Enable HTTP Strict Transport Security headers

---

For more deployment guides:
- [Docker Deployment](./docker.md)
- [Systemd Deployment](./systemd.md)
- [Monitoring Setup](./monitoring.md)