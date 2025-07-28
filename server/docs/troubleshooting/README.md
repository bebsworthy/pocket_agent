# Pocket Agent Server Troubleshooting Guide

This guide provides detailed troubleshooting steps for common issues with the Pocket Agent Server.

## Table of Contents

- [Quick Diagnostics](#quick-diagnostics)
- [Connection Issues](#connection-issues)
- [Execution Problems](#execution-problems)
- [Performance Issues](#performance-issues)
- [Data and Storage Issues](#data-and-storage-issues)
- [Process Management](#process-management)
- [Security Issues](#security-issues)
- [Debug Tools](#debug-tools)
- [Common Error Codes](#common-error-codes)
- [Health Checks](#health-checks)
- [Recovery Procedures](#recovery-procedures)

## Quick Diagnostics

### Server Health Check Script

```bash
#!/bin/bash
# health-check.sh - Quick server diagnostics

echo "=== Pocket Agent Server Diagnostics ==="
echo

# Check if server is running
if pgrep -f pocket-agent-server > /dev/null; then
    echo "✓ Server process is running"
    echo "  PID: $(pgrep -f pocket-agent-server)"
else
    echo "✗ Server process is NOT running"
fi

# Check port availability
if nc -z localhost 8443 2>/dev/null; then
    echo "✓ Port 8443 is listening"
else
    echo "✗ Port 8443 is NOT listening"
fi

# Check Claude CLI
if command -v claude &> /dev/null; then
    echo "✓ Claude CLI found: $(which claude)"
    echo "  Version: $(claude --version 2>/dev/null || echo 'unknown')"
else
    echo "✗ Claude CLI NOT found"
fi

# Check disk space
DISK_USAGE=$(df -h . | awk 'NR==2 {print $5}' | sed 's/%//')
if [ "$DISK_USAGE" -lt 90 ]; then
    echo "✓ Disk usage: ${DISK_USAGE}%"
else
    echo "✗ High disk usage: ${DISK_USAGE}%"
fi

# Check data directory
if [ -d "./data" ]; then
    echo "✓ Data directory exists"
    PROJECT_COUNT=$(find ./data/projects -maxdepth 1 -type d 2>/dev/null | wc -l)
    echo "  Projects: $((PROJECT_COUNT - 1))"
else
    echo "✗ Data directory NOT found"
fi

# Check TLS certificates
if [ -f "./certs/server.crt" ] && [ -f "./certs/server.key" ]; then
    echo "✓ TLS certificates found"
    # Check expiration
    CERT_END=$(openssl x509 -enddate -noout -in ./certs/server.crt | cut -d= -f2)
    echo "  Expires: $CERT_END"
else
    echo "✗ TLS certificates NOT found"
fi

echo
echo "=== End Diagnostics ==="
```

### WebSocket Test Script

```javascript
// ws-test.js - Test WebSocket connectivity

const WebSocket = require('ws');

const url = 'wss://localhost:8443/ws';
const ws = new WebSocket(url, {
    rejectUnauthorized: false // For self-signed certs in dev
});

console.log(`Connecting to ${url}...`);

ws.on('open', () => {
    console.log('✓ Connected successfully');
    
    // Send health check
    ws.send(JSON.stringify({ type: 'health' }));
    
    // Test project list
    setTimeout(() => {
        ws.send(JSON.stringify({ type: 'project_list' }));
    }, 1000);
});

ws.on('message', (data) => {
    const msg = JSON.parse(data);
    console.log('Received:', msg.type);
    
    if (msg.type === 'health_status') {
        console.log('✓ Health check passed');
        console.log('  Status:', msg.data.status);
        console.log('  Claude:', msg.data.claude.available ? '✓' : '✗');
    }
    
    if (msg.type === 'project_list_response') {
        console.log('✓ Project list received');
        console.log('  Count:', msg.data.projects.length);
        ws.close();
    }
});

ws.on('error', (err) => {
    console.error('✗ WebSocket error:', err.message);
});

ws.on('close', () => {
    console.log('Connection closed');
});
```

## Connection Issues

### WebSocket Connection Fails

#### Symptom: "WebSocket connection failed" or immediate disconnect

**1. Check TLS Certificates**

```bash
# Verify certificate validity
openssl s_client -connect localhost:8443 -servername localhost < /dev/null

# Check certificate details
openssl x509 -in ./certs/server.crt -text -noout

# Verify certificate and key match
openssl x509 -noout -modulus -in ./certs/server.crt | openssl md5
openssl rsa -noout -modulus -in ./certs/server.key | openssl md5
# These should match

# Generate new self-signed certificates for testing
openssl req -x509 -newkey rsa:4096 -keyout server.key -out server.crt -days 365 -nodes \
    -subj "/C=US/ST=State/L=City/O=Organization/CN=localhost"
```

**2. Check Port Availability**

```bash
# Check if port is in use
sudo lsof -i :8443
# or
sudo netstat -tlnp | grep 8443

# Check firewall rules
# Linux (iptables)
sudo iptables -L -n | grep 8443

# Linux (firewalld)
sudo firewall-cmd --list-ports

# macOS
sudo pfctl -s rules | grep 8443

# Add firewall rule if needed
# Linux (iptables)
sudo iptables -A INPUT -p tcp --dport 8443 -j ACCEPT

# Linux (firewalld)
sudo firewall-cmd --permanent --add-port=8443/tcp
sudo firewall-cmd --reload
```

**3. Check Server Logs**

```bash
# View real-time logs
tail -f server.log | jq '.'

# Search for binding errors
grep -i "bind\|listen\|address" server.log

# Common binding errors:
# - "bind: address already in use" - Port conflict
# - "bind: permission denied" - Need higher privileges or different port
# - "bind: cannot assign requested address" - Invalid host IP
```

**4. Network Configuration**

```bash
# Test local connectivity
curl -k https://localhost:8443/ws \
  -H "Connection: Upgrade" \
  -H "Upgrade: websocket" \
  -H "Sec-WebSocket-Version: 13" \
  -H "Sec-WebSocket-Key: x3JJHMbDL1EzLkh9GBhXDw=="

# Check DNS resolution
nslookup your-server.com

# Test from different machine
wscat -c wss://your-server.com:8443/ws --no-check
```

### Connection Drops/Timeouts

#### Symptom: Connection established but drops after period of inactivity

**1. Adjust Timeout Settings**

```yaml
# config.yaml
timeouts:
  idle: 10m         # Increase idle timeout
  ping_interval: 30s # More frequent pings
  pong_timeout: 60s  # More lenient pong timeout
```

**2. Check Proxy Settings**

If behind a reverse proxy (nginx):

```nginx
# nginx.conf
location /ws {
    proxy_pass https://localhost:8443;
    proxy_http_version 1.1;
    proxy_set_header Upgrade $http_upgrade;
    proxy_set_header Connection "upgrade";
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    
    # Important timeout settings
    proxy_connect_timeout 7d;
    proxy_send_timeout 7d;
    proxy_read_timeout 7d;
}
```

**3. Client-Side Keep-Alive**

```javascript
// Implement client-side ping
let pingInterval;

ws.on('open', () => {
    // Send ping every 30 seconds
    pingInterval = setInterval(() => {
        if (ws.readyState === WebSocket.OPEN) {
            ws.ping();
        }
    }, 30000);
});

ws.on('close', () => {
    clearInterval(pingInterval);
});
```

### Maximum Connections Reached

#### Symptom: New connections rejected with "resource limit" error

**1. Check Current Connections**

```bash
# Count active connections
lsof -p $(pgrep pocket-agent-server) | grep -c "TCP.*ESTABLISHED"

# List all connections
ss -tn | grep :8443
```

**2. Increase Connection Limit**

```yaml
# config.yaml
limits:
  max_connections: 200  # Increase from default 100
```

**3. System Limits**

```bash
# Check current limits
ulimit -n  # File descriptors

# Increase for current session
ulimit -n 65536

# Permanent increase
# Add to /etc/security/limits.conf
* soft nofile 65536
* hard nofile 65536

# For systemd service
# Add to service file
[Service]
LimitNOFILE=65536
```

## Execution Problems

### Claude CLI Not Found

#### Symptom: "Claude CLI not found" error when executing

**1. Verify Installation**

```bash
# Check if Claude is installed
which claude

# Check PATH
echo $PATH

# Try common locations
ls -la /usr/local/bin/claude
ls -la ~/bin/claude
ls -la ~/.local/bin/claude

# Test execution as server user
sudo -u pocket-agent which claude
```

**2. Configure Claude Path**

```yaml
# config.yaml
claude:
  binary: /absolute/path/to/claude
```

**3. Install Claude CLI**

```bash
# Follow official installation
# Example (adjust based on actual installation method):
curl -fsSL https://claude.ai/cli/install.sh | sh

# Add to PATH
export PATH=$PATH:$HOME/.local/bin
echo 'export PATH=$PATH:$HOME/.local/bin' >> ~/.bashrc
```

### Execution Timeouts

#### Symptom: Claude executions fail with timeout error

**1. Increase Timeout**

```yaml
# config.yaml
timeouts:
  execution: 10m  # Increase from default 5m
```

**2. Check Long-Running Processes**

```bash
# Find Claude processes
ps aux | grep claude

# Check process tree
pstree -p $(pgrep pocket-agent-server)

# Monitor execution time
time claude -p "test prompt"
```

**3. Debug Execution**

```bash
# Enable execution debug logging
PA_LOG_LEVEL=debug ./bin/pocket-agent-server 2>&1 | grep -i "execute\|claude"

# Test Claude directly
claude -p "simple test" -o verbose
```

### Process Not Killed

#### Symptom: Kill command doesn't stop execution

**1. Check Process Groups**

```bash
# Find process group
ps -eo pid,ppid,pgid,cmd | grep claude

# Kill entire process group
kill -TERM -<PGID>
```

**2. Force Kill Implementation**

```go
// Ensure proper process termination
// Check implementation in executor.go
cmd.SysProcAttr = &syscall.SysProcAttr{
    Setpgid: true,
}

// Kill process group
syscall.Kill(-cmd.Process.Pid, syscall.SIGKILL)
```

### JSON Parsing Errors

#### Symptom: "Failed to parse Claude output" errors

**1. Capture Raw Output**

```bash
# Enable raw output logging
PA_LOG_RAW_OUTPUT=true ./bin/pocket-agent-server

# Test Claude output format
claude -p "test" 2>&1 | tee claude-output.log

# Validate JSON
claude -p "test" | jq '.'
```

**2. Handle Mixed Output**

```go
// Check parser implementation
// Separate stdout and stderr
var stdout, stderr bytes.Buffer
cmd.Stdout = &stdout
cmd.Stderr = &stderr

// Log stderr for debugging
if stderr.Len() > 0 {
    log.Debug("Claude stderr:", stderr.String())
}
```

## Performance Issues

### High CPU Usage

#### Symptom: Server consuming excessive CPU

**1. Profile CPU Usage**

```bash
# Enable profiling
PA_ENABLE_PPROF=true ./bin/pocket-agent-server

# Capture CPU profile
go tool pprof -http=:8080 http://localhost:6060/debug/pprof/profile?seconds=30

# Top functions
go tool pprof http://localhost:6060/debug/pprof/profile?seconds=30
(pprof) top10
(pprof) list functionName
```

**2. Check for Busy Loops**

```bash
# Monitor goroutines
curl http://localhost:6060/debug/pprof/goroutine?debug=2 | less

# Look for stuck goroutines
grep -A 10 "goroutine [0-9]* \[running\]" goroutine.txt
```

**3. Optimize Hot Paths**

Common causes:
- JSON marshaling/unmarshaling in loops
- Excessive logging in hot paths
- Inefficient broadcast loops
- Missing breaks in select statements

### High Memory Usage

#### Symptom: Server memory constantly growing

**1. Memory Profile**

```bash
# Capture heap profile
curl http://localhost:6060/debug/pprof/heap > heap.prof
go tool pprof -http=:8080 heap.prof

# Check allocations
go tool pprof http://localhost:6060/debug/pprof/allocs

# In pprof
(pprof) top10
(pprof) list functionName
```

**2. Check for Leaks**

```bash
# Monitor memory over time
while true; do
    ps aux | grep pocket-agent-server | grep -v grep
    sleep 60
done | tee memory-log.txt

# Graph memory usage
awk '{print $6}' memory-log.txt | gnuplot -e "plot '-' with lines"
```

**3. Common Memory Leaks**

- Unclosed WebSocket connections
- Growing maps without cleanup
- Large message buffers
- Unclosed file handles

```go
// Check for cleanup
defer func() {
    close(session.send)
    delete(sessions, session.ID)
}()

// Limit buffer sizes
session.send = make(chan Message, 100) // Bounded channel
```

### Slow Message Processing

#### Symptom: High latency between receive and response

**1. Enable Timing Logs**

```go
// Add timing to critical paths
start := time.Now()
defer func() {
    log.WithFields(log.Fields{
        "duration_ms": time.Since(start).Milliseconds(),
        "message_type": msg.Type,
    }).Debug("Message processed")
}()
```

**2. Check Blocking Operations**

```bash
# Look for blocking calls
go tool pprof http://localhost:6060/debug/pprof/block

# Mutex contention
go tool pprof http://localhost:6060/debug/pprof/mutex
```

**3. Optimize Broadcasting**

```go
// Use buffered channels
broadcast := make(chan Message, 1000)

// Non-blocking send
select {
case subscriber.send <- msg:
default:
    // Handle slow consumer
    log.Warn("Dropping message for slow consumer")
}
```

## Data and Storage Issues

### Corrupted Project Metadata

#### Symptom: "Failed to load project metadata" errors

**1. Identify Corrupted Files**

```bash
# Check all metadata files
for f in ./data/projects/*/metadata.json; do
    if ! jq empty < "$f" 2>/dev/null; then
        echo "Corrupted: $f"
    fi
done

# Backup corrupted files
mkdir -p ./data/corrupted
mv "$corrupted_file" ./data/corrupted/
```

**2. Recover from Backup**

```bash
# If you have backups
cp /backup/metadata.json ./data/projects/PROJECT_ID/

# Recreate minimal metadata
cat > ./data/projects/PROJECT_ID/metadata.json << EOF
{
    "id": "PROJECT_ID",
    "path": "/path/to/project",
    "created_at": "$(date -u +%Y-%m-%dT%H:%M:%SZ)",
    "last_active": "$(date -u +%Y-%m-%dT%H:%M:%SZ)",
    "session_id": null
}
EOF
```

**3. Prevent Future Corruption**

```go
// Ensure atomic writes
func saveMetadata(metadata interface{}, path string) error {
    data, err := json.MarshalIndent(metadata, "", "  ")
    if err != nil {
        return err
    }
    
    // Write to temp file first
    tmpPath := path + ".tmp"
    if err := ioutil.WriteFile(tmpPath, data, 0644); err != nil {
        return err
    }
    
    // Atomic rename
    return os.Rename(tmpPath, path)
}
```

### Message Log Issues

#### Symptom: Missing messages or can't retrieve history

**1. Check Log Files**

```bash
# List all log files
ls -la ./data/projects/*/logs/

# Check file sizes
du -h ./data/projects/*/logs/*

# Verify log format
head -n 5 ./data/projects/PROJECT_ID/logs/messages_*.jsonl | jq '.'
```

**2. Repair Log Files**

```bash
# Remove invalid lines
for f in ./data/projects/*/logs/*.jsonl; do
    # Create backup
    cp "$f" "$f.bak"
    
    # Filter valid JSON lines
    jq -c '.' < "$f.bak" > "$f" 2>/dev/null || true
done
```

**3. Manual Log Rotation**

```bash
# If rotation is stuck
cd ./data/projects/PROJECT_ID/logs/
mv messages_current.jsonl "messages_$(date +%Y-%m-%d_%H-%M-%S).jsonl"
touch messages_current.jsonl
```

### Disk Space Issues

#### Symptom: "No space left on device" errors

**1. Check Disk Usage**

```bash
# Overall disk usage
df -h

# Find large directories
du -h ./data | sort -h | tail -20

# Find large log files
find ./data -name "*.jsonl" -size +100M -ls
```

**2. Clean Up Safely**

```bash
#!/bin/bash
# cleanup.sh - Safe cleanup script

# Archive old logs (older than 30 days)
find ./data/projects/*/logs -name "*.jsonl" -mtime +30 -exec gzip {} \;

# Remove very old archives (older than 90 days)
find ./data/projects/*/logs -name "*.gz" -mtime +90 -delete

# Remove orphaned projects (no metadata.json)
for dir in ./data/projects/*/; do
    if [ ! -f "$dir/metadata.json" ]; then
        echo "Orphaned project: $dir"
        # rm -rf "$dir"  # Uncomment after verification
    fi
done
```

**3. Configure Log Rotation**

```yaml
# config.yaml
log_rotation:
  size: 52428800      # 50MB instead of 100MB
  count: 5000         # Fewer messages per file
  compress: true      # Enable compression
  max_age: 30         # Auto-delete after 30 days
```

## Process Management

### Zombie Processes

#### Symptom: Defunct Claude processes accumulating

**1. Identify Zombies**

```bash
# Find zombie processes
ps aux | grep defunct
ps aux | grep '<defunct>'

# Check parent process
ps -ef | grep pocket-agent-server
```

**2. Proper Process Reaping**

```go
// Ensure proper wait
cmd := exec.CommandContext(ctx, "claude", args...)
if err := cmd.Start(); err != nil {
    return err
}

// Always wait for process
defer func() {
    cmd.Wait() // This reaps the zombie
}()
```

**3. Signal Handling**

```go
// Handle SIGCHLD to reap children
signal.Notify(sigChan, syscall.SIGCHLD)
go func() {
    for range sigChan {
        var status syscall.WaitStatus
        syscall.Wait4(-1, &status, syscall.WNOHANG, nil)
    }
}()
```

### Server Won't Stop

#### Symptom: Server doesn't shut down cleanly

**1. Graceful Shutdown**

```bash
# Send SIGTERM for graceful shutdown
kill -TERM $(pgrep pocket-agent-server)

# Wait 30 seconds, then force
sleep 30
kill -KILL $(pgrep pocket-agent-server)
```

**2. Debug Shutdown**

```bash
# Enable shutdown logging
PA_LOG_LEVEL=debug ./bin/pocket-agent-server

# In another terminal
kill -TERM $(pgrep pocket-agent-server)

# Watch logs for shutdown sequence
```

**3. Common Shutdown Issues**

- Goroutines not responding to context cancellation
- Unclosed channels blocking
- Active WebSocket connections
- Running Claude processes

## Security Issues

### Permission Denied Errors

#### Symptom: Various permission-related failures

**1. File Permissions**

```bash
# Check data directory permissions
ls -la ./data

# Fix permissions
sudo chown -R pocket-agent:pocket-agent ./data
sudo chmod -R 750 ./data

# Check TLS certificate permissions
ls -la ./certs/
chmod 600 ./certs/server.key
chmod 644 ./certs/server.crt
```

**2. Process Permissions**

```bash
# Check server user
ps aux | grep pocket-agent-server

# Test as server user
sudo -u pocket-agent ls -la ./data
sudo -u pocket-agent claude -p "test"
```

### Path Traversal Attempts

#### Symptom: Security warnings in logs about invalid paths

**1. Review Path Validation**

```go
// Proper path validation
func validatePath(p string) error {
    // Must be absolute
    if !filepath.IsAbs(p) {
        return errors.New("path must be absolute")
    }
    
    // No traversal
    clean := filepath.Clean(p)
    if strings.Contains(clean, "..") {
        return errors.New("path traversal detected")
    }
    
    // Within allowed directories
    if !strings.HasPrefix(clean, "/allowed/base/path") {
        return errors.New("path outside allowed directory")
    }
    
    return nil
}
```

**2. Audit Logs**

```bash
# Find path validation failures
grep -i "invalid.*path\|traversal" server.log | jq '.'

# Check for patterns
grep "project_create" server.log | jq '.data.path' | sort | uniq -c
```

## Debug Tools

### Enable Debug Mode

```bash
# Maximum debugging
PA_LOG_LEVEL=debug \
PA_LOG_FORMAT=text \
PA_ENABLE_PPROF=true \
PA_TRACE_ENABLED=true \
PA_LOG_RAW_OUTPUT=true \
./bin/pocket-agent-server 2>&1 | tee debug.log
```

### Performance Profiling

```bash
# CPU profile
go tool pprof -http=:8080 http://localhost:6060/debug/pprof/profile?seconds=30

# Memory profile
go tool pprof -http=:8080 http://localhost:6060/debug/pprof/heap

# Goroutine profile
curl http://localhost:6060/debug/pprof/goroutine?debug=2 > goroutines.txt

# Trace
curl http://localhost:6060/debug/pprof/trace?seconds=5 > trace.out
go tool trace trace.out
```

### Network Debugging

```bash
# Capture WebSocket traffic
sudo tcpdump -i lo -w websocket.pcap 'port 8443'

# Analyze with Wireshark
wireshark websocket.pcap

# Or use tshark
tshark -r websocket.pcap -Y "websocket"

# Monitor connections in real-time
watch -n 1 'ss -tn | grep :8443'
```

### Structured Log Analysis

```bash
# Extract errors
jq 'select(.level == "error")' server.log

# Group by error type
jq -r 'select(.level == "error") | .error' server.log | sort | uniq -c

# Execution statistics
jq 'select(.msg == "Execution completed") | .duration_ms' server.log | \
    awk '{sum+=$1; count++} END {print "Avg:", sum/count, "ms"}'

# Message flow
jq 'select(.msg | contains("Message")) | {time: .timestamp, type: .message_type}' server.log
```

## Common Error Codes

### Reference Table

| Code | Description | Common Causes | Solutions |
|------|-------------|---------------|-----------|
| `INVALID_MESSAGE` | Message format invalid | Malformed JSON, missing required fields | Validate client code, check message schema |
| `INVALID_PATH` | Path validation failed | Relative path, contains .., outside allowed dirs | Use absolute paths, check validation rules |
| `PROJECT_NESTING` | Project would nest | Creating project inside existing project path | Choose different path |
| `PROJECT_NOT_FOUND` | Project ID not found | Invalid ID, project deleted | Verify project exists, check ID format |
| `PROJECT_LIMIT` | Max projects exceeded | Too many projects (default 100) | Delete unused projects, increase limit |
| `EXECUTION_TIMEOUT` | Execution exceeded timeout | Long-running Claude command | Increase timeout, optimize prompts |
| `CLAUDE_NOT_FOUND` | Claude CLI not found | Not installed, not in PATH | Install Claude, configure path |
| `PROCESS_ACTIVE` | Operation blocked by active process | Trying to delete/modify during execution | Wait for completion or kill process |
| `RESOURCE_LIMIT` | Resource limit hit | Max connections, memory limit | Increase limits, optimize resource usage |
| `INTERNAL_ERROR` | Unexpected error | Various | Check logs for stack trace |

### Error Response Handling

```javascript
// Client-side error handling
ws.on('message', (data) => {
    const msg = JSON.parse(data);
    
    if (msg.type === 'error') {
        console.error(`Error ${msg.data.code}: ${msg.data.message}`);
        
        switch(msg.data.code) {
            case 'PROJECT_NOT_FOUND':
                // Remove from local cache
                removeProject(msg.project_id);
                break;
                
            case 'EXECUTION_TIMEOUT':
                // Notify user, offer to retry
                showTimeoutDialog();
                break;
                
            case 'RESOURCE_LIMIT':
                // Back off and retry later
                setTimeout(retry, 5000);
                break;
                
            default:
                // Generic error handling
                showErrorNotification(msg.data.message);
        }
    }
});
```

## Health Checks

### Automated Health Monitoring

```bash
#!/bin/bash
# health-monitor.sh - Continuous health monitoring

WEBHOOK_URL="https://monitoring.example.com/webhook"
CHECK_INTERVAL=60

check_health() {
    # WebSocket health check
    RESPONSE=$(timeout 5 node ws-test.js 2>&1)
    
    if echo "$RESPONSE" | grep -q "Health check passed"; then
        return 0
    else
        return 1
    fi
}

while true; do
    if ! check_health; then
        # Alert
        curl -X POST "$WEBHOOK_URL" \
            -H "Content-Type: application/json" \
            -d '{"text":"Pocket Agent Server health check failed"}'
        
        # Try restart
        systemctl restart pocket-agent-server
    fi
    
    sleep "$CHECK_INTERVAL"
done
```

### Metrics Collection

```javascript
// Prometheus metrics format
// GET /metrics endpoint (future)

# HELP pocket_agent_connections_active Active WebSocket connections
# TYPE pocket_agent_connections_active gauge
pocket_agent_connections_active 42

# HELP pocket_agent_projects_total Total number of projects
# TYPE pocket_agent_projects_total gauge
pocket_agent_projects_total 15

# HELP pocket_agent_executions_total Total executions
# TYPE pocket_agent_executions_total counter
pocket_agent_executions_total{status="success"} 1234
pocket_agent_executions_total{status="failed"} 56
pocket_agent_executions_total{status="timeout"} 12

# HELP pocket_agent_execution_duration_seconds Execution duration
# TYPE pocket_agent_execution_duration_seconds histogram
pocket_agent_execution_duration_seconds_bucket{le="1"} 100
pocket_agent_execution_duration_seconds_bucket{le="5"} 800
pocket_agent_execution_duration_seconds_bucket{le="10"} 1000
```

## Recovery Procedures

### Emergency Restart

```bash
#!/bin/bash
# emergency-restart.sh

echo "Starting emergency restart procedure..."

# 1. Save current state
echo "Backing up current state..."
tar -czf "backup-$(date +%Y%m%d-%H%M%S).tar.gz" ./data

# 2. Kill all processes
echo "Stopping server..."
systemctl stop pocket-agent-server
pkill -f pocket-agent-server
pkill -f claude

# 3. Clean up resources
echo "Cleaning up..."
rm -f ./data/.lock
find ./data -name "*.tmp" -delete

# 4. Verify clean state
if pgrep -f pocket-agent-server > /dev/null; then
    echo "ERROR: Server still running!"
    exit 1
fi

# 5. Start fresh
echo "Starting server..."
systemctl start pocket-agent-server

# 6. Verify startup
sleep 5
if ! systemctl is-active pocket-agent-server; then
    echo "ERROR: Server failed to start!"
    journalctl -u pocket-agent-server -n 50
    exit 1
fi

echo "Emergency restart complete!"
```

### Data Recovery

```bash
#!/bin/bash
# data-recovery.sh

echo "Starting data recovery..."

# 1. Check for corrupted files
echo "Scanning for corruption..."
find ./data -name "*.json" -o -name "*.jsonl" | while read -r file; do
    if ! jq empty < "$file" 2>/dev/null; then
        echo "Corrupted: $file"
        mv "$file" "$file.corrupted"
    fi
done

# 2. Rebuild project index
echo "Rebuilding project index..."
> ./data/project-index.json
for dir in ./data/projects/*/; do
    if [ -f "$dir/metadata.json" ]; then
        project_id=$(basename "$dir")
        jq --arg id "$project_id" '. + {id: $id}' "$dir/metadata.json" >> ./data/project-index.json
    fi
done

# 3. Verify message logs
echo "Verifying message logs..."
for log_dir in ./data/projects/*/logs/; do
    if [ -d "$log_dir" ]; then
        # Ensure current log exists
        touch "$log_dir/messages_current.jsonl"
        
        # Fix permissions
        chmod 644 "$log_dir"/*.jsonl 2>/dev/null || true
    fi
done

echo "Data recovery complete!"
```

### Rollback Procedure

```bash
#!/bin/bash
# rollback.sh - Rollback to previous version

BACKUP_DIR="/backup/pocket-agent"
CURRENT_VERSION=$(./bin/pocket-agent-server --version)

echo "Current version: $CURRENT_VERSION"
echo "Available backups:"
ls -la "$BACKUP_DIR"

read -p "Enter backup date (YYYYMMDD): " BACKUP_DATE

if [ ! -d "$BACKUP_DIR/$BACKUP_DATE" ]; then
    echo "Backup not found!"
    exit 1
fi

# 1. Stop current server
systemctl stop pocket-agent-server

# 2. Backup current state
tar -czf "pre-rollback-$(date +%Y%m%d-%H%M%S).tar.gz" .

# 3. Restore backup
cp "$BACKUP_DIR/$BACKUP_DATE/pocket-agent-server" ./bin/
tar -xzf "$BACKUP_DIR/$BACKUP_DATE/data.tar.gz"

# 4. Restart
systemctl start pocket-agent-server

echo "Rollback complete!"
```

---

For additional help:
- Check the [FAQ](../faq/README.md)
- Report issues on [GitHub](https://github.com/boyd/pocket_agent/issues)
- Join the [community Discord](https://discord.gg/pocket-agent)