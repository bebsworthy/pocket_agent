# Monitoring Setup Guide

This guide covers setting up comprehensive monitoring for Pocket Agent Server including metrics, logging, alerting, and dashboards.

## Table of Contents

- [Overview](#overview)
- [Metrics Collection](#metrics-collection)
- [Logging Strategy](#logging-strategy)
- [Health Monitoring](#health-monitoring)
- [Alerting](#alerting)
- [Dashboards](#dashboards)
- [Performance Monitoring](#performance-monitoring)
- [Troubleshooting](#troubleshooting)

## Overview

A comprehensive monitoring strategy for Pocket Agent Server includes:

1. **Metrics**: Performance and business metrics via Prometheus
2. **Logs**: Structured logging with aggregation
3. **Traces**: Distributed tracing (future)
4. **Health Checks**: Active and passive monitoring
5. **Alerts**: Proactive issue detection
6. **Dashboards**: Real-time visualization

## Metrics Collection

### Prometheus Setup

#### Install Prometheus

```bash
# Download Prometheus
wget https://github.com/prometheus/prometheus/releases/download/v2.45.0/prometheus-2.45.0.linux-amd64.tar.gz
tar xvf prometheus-2.45.0.linux-amd64.tar.gz
sudo cp prometheus-2.45.0.linux-amd64/prometheus /usr/local/bin/
sudo cp prometheus-2.45.0.linux-amd64/promtool /usr/local/bin/

# Create directories
sudo mkdir -p /etc/prometheus /var/lib/prometheus
sudo cp -r prometheus-2.45.0.linux-amd64/consoles /etc/prometheus
sudo cp -r prometheus-2.45.0.linux-amd64/console_libraries /etc/prometheus
```

#### Configure Prometheus

Create `/etc/prometheus/prometheus.yml`:

```yaml
global:
  scrape_interval: 15s
  evaluation_interval: 15s
  external_labels:
    monitor: 'pocket-agent-monitor'

# Alertmanager configuration
alerting:
  alertmanagers:
    - static_configs:
        - targets:
            - localhost:9093

# Load rules
rule_files:
  - "alerts/*.yml"

# Scrape configurations
scrape_configs:
  # Pocket Agent Server metrics
  - job_name: 'pocket-agent'
    static_configs:
      - targets: ['localhost:9090']
        labels:
          service: 'pocket-agent-server'
          environment: 'production'
    
    # Metric relabeling
    metric_relabel_configs:
      - source_labels: [__name__]
        regex: 'go_.*'
        action: drop  # Drop Go runtime metrics if not needed

  # Node Exporter for system metrics
  - job_name: 'node'
    static_configs:
      - targets: ['localhost:9100']

  # Blackbox Exporter for endpoint monitoring
  - job_name: 'blackbox'
    metrics_path: /probe
    params:
      module: [websocket_check]
    static_configs:
      - targets:
          - wss://localhost:8443/ws
    relabel_configs:
      - source_labels: [__address__]
        target_label: __param_target
      - source_labels: [__param_target]
        target_label: instance
      - target_label: __address__
        replacement: localhost:9115
```

#### Prometheus Service

Create `/etc/systemd/system/prometheus.service`:

```ini
[Unit]
Description=Prometheus Server
Documentation=https://prometheus.io/docs/introduction/overview/
After=network-online.target

[Service]
Type=simple
User=prometheus
Group=prometheus
ExecStart=/usr/local/bin/prometheus \
    --config.file=/etc/prometheus/prometheus.yml \
    --storage.tsdb.path=/var/lib/prometheus/ \
    --web.console.templates=/etc/prometheus/consoles \
    --web.console.libraries=/etc/prometheus/console_libraries \
    --web.enable-lifecycle \
    --storage.tsdb.retention.time=30d

Restart=always
RestartSec=5

[Install]
WantedBy=multi-user.target
```

### Application Metrics

#### Implement Metrics in Server

```go
// metrics/metrics.go
package metrics

import (
    "github.com/prometheus/client_golang/prometheus"
    "github.com/prometheus/client_golang/prometheus/promauto"
)

var (
    // Connection metrics
    ConnectionsActive = promauto.NewGauge(prometheus.GaugeOpts{
        Name: "pocket_agent_connections_active",
        Help: "Number of active WebSocket connections",
    })
    
    ConnectionsTotal = promauto.NewCounter(prometheus.CounterOpts{
        Name: "pocket_agent_connections_total",
        Help: "Total number of WebSocket connections",
    })
    
    // Project metrics
    ProjectsTotal = promauto.NewGauge(prometheus.GaugeOpts{
        Name: "pocket_agent_projects_total",
        Help: "Total number of projects",
    })
    
    ProjectsActive = promauto.NewGaugeVec(prometheus.GaugeOpts{
        Name: "pocket_agent_projects_active",
        Help: "Active projects by state",
    }, []string{"state"})
    
    // Execution metrics
    ExecutionsTotal = promauto.NewCounterVec(prometheus.CounterOpts{
        Name: "pocket_agent_executions_total",
        Help: "Total number of Claude executions",
    }, []string{"status"})
    
    ExecutionDuration = promauto.NewHistogramVec(prometheus.HistogramOpts{
        Name:    "pocket_agent_execution_duration_seconds",
        Help:    "Duration of Claude executions",
        Buckets: []float64{1, 5, 10, 30, 60, 120, 300, 600},
    }, []string{"status"})
    
    // Message metrics
    MessagesReceived = promauto.NewCounterVec(prometheus.CounterOpts{
        Name: "pocket_agent_messages_received_total",
        Help: "Total messages received",
    }, []string{"type"})
    
    MessagesSent = promauto.NewCounterVec(prometheus.CounterOpts{
        Name: "pocket_agent_messages_sent_total",
        Help: "Total messages sent",
    }, []string{"type"})
    
    // Error metrics
    ErrorsTotal = promauto.NewCounterVec(prometheus.CounterOpts{
        Name: "pocket_agent_errors_total",
        Help: "Total errors",
    }, []string{"code", "operation"})
)
```

#### Expose Metrics Endpoint

```go
// In server setup
import (
    "github.com/prometheus/client_golang/prometheus/promhttp"
)

// Add metrics endpoint
http.Handle("/metrics", promhttp.Handler())
go http.ListenAndServe(":9090", nil)
```

### Node Exporter

Install for system metrics:

```bash
# Download Node Exporter
wget https://github.com/prometheus/node_exporter/releases/download/v1.6.0/node_exporter-1.6.0.linux-amd64.tar.gz
tar xvf node_exporter-1.6.0.linux-amd64.tar.gz
sudo cp node_exporter-1.6.0.linux-amd64/node_exporter /usr/local/bin/

# Create service
cat > /etc/systemd/system/node_exporter.service << EOF
[Unit]
Description=Node Exporter
After=network.target

[Service]
Type=simple
User=node_exporter
Group=node_exporter
ExecStart=/usr/local/bin/node_exporter \
    --collector.filesystem.mount-points-exclude='^/(sys|proc|dev|host|etc)($|/)' \
    --collector.netclass.ignored-devices='^(veth.*|docker.*|br-.*)$'

[Install]
WantedBy=multi-user.target
EOF

sudo systemctl enable node_exporter
sudo systemctl start node_exporter
```

## Logging Strategy

### Structured Logging Setup

#### Configure Application Logging

```yaml
# config.yaml
logging:
  level: info
  format: json
  output: stdout
  fields:
    service: pocket-agent-server
    environment: production
    version: 1.0.0
```

#### Log Format

```json
{
  "timestamp": "2024-01-01T12:00:00.123Z",
  "level": "info",
  "msg": "Execution completed",
  "service": "pocket-agent-server",
  "environment": "production",
  "correlation_id": "req-123",
  "project_id": "proj-456",
  "duration_ms": 1234,
  "fields": {
    "session_id": "claude-789",
    "model": "claude-3-5-sonnet",
    "prompt_tokens": 100,
    "completion_tokens": 500
  }
}
```

### Log Aggregation with Loki

#### Install Loki

```bash
# Download Loki
wget https://github.com/grafana/loki/releases/download/v2.8.0/loki-linux-amd64.zip
unzip loki-linux-amd64.zip
sudo mv loki-linux-amd64 /usr/local/bin/loki

# Create config
cat > /etc/loki/config.yml << EOF
auth_enabled: false

server:
  http_listen_port: 3100
  grpc_listen_port: 9096

common:
  path_prefix: /var/lib/loki
  storage:
    filesystem:
      chunks_directory: /var/lib/loki/chunks
      rules_directory: /var/lib/loki/rules
  replication_factor: 1
  ring:
    kvstore:
      store: inmemory

schema_config:
  configs:
    - from: 2020-10-24
      store: boltdb-shipper
      object_store: filesystem
      schema: v11
      index:
        prefix: index_
        period: 24h

ruler:
  alertmanager_url: http://localhost:9093
EOF
```

#### Install Promtail

```bash
# Download Promtail
wget https://github.com/grafana/loki/releases/download/v2.8.0/promtail-linux-amd64.zip
unzip promtail-linux-amd64.zip
sudo mv promtail-linux-amd64 /usr/local/bin/promtail

# Create config
cat > /etc/promtail/config.yml << EOF
server:
  http_listen_port: 9080
  grpc_listen_port: 0

positions:
  filename: /var/lib/promtail/positions.yaml

clients:
  - url: http://localhost:3100/loki/api/v1/push

scrape_configs:
  - job_name: pocket-agent
    static_configs:
      - targets:
          - localhost
        labels:
          job: pocket-agent
          __path__: /var/log/pocket-agent/*.log
    pipeline_stages:
      - json:
          expressions:
            timestamp: timestamp
            level: level
            msg: msg
            correlation_id: correlation_id
            project_id: project_id
      - labels:
          level:
          correlation_id:
          project_id:
      - timestamp:
          source: timestamp
          format: RFC3339Nano
EOF
```

### ELK Stack Alternative

```yaml
# docker-compose.yml for ELK
version: '3.8'

services:
  elasticsearch:
    image: docker.elastic.co/elasticsearch/elasticsearch:8.8.0
    environment:
      - discovery.type=single-node
      - "ES_JAVA_OPTS=-Xms512m -Xmx512m"
    volumes:
      - elasticsearch-data:/usr/share/elasticsearch/data
    ports:
      - "9200:9200"

  logstash:
    image: docker.elastic.co/logstash/logstash:8.8.0
    volumes:
      - ./logstash/pipeline:/usr/share/logstash/pipeline:ro
    depends_on:
      - elasticsearch

  kibana:
    image: docker.elastic.co/kibana/kibana:8.8.0
    environment:
      - ELASTICSEARCH_HOSTS=http://elasticsearch:9200
    ports:
      - "5601:5601"
    depends_on:
      - elasticsearch

volumes:
  elasticsearch-data:
```

## Health Monitoring

### Health Check Endpoint

```go
// health/health.go
type HealthStatus struct {
    Status      string            `json:"status"`
    Version     string            `json:"version"`
    Uptime      int64            `json:"uptime"`
    Timestamp   time.Time        `json:"timestamp"`
    Checks      map[string]Check `json:"checks"`
}

type Check struct {
    Status  string `json:"status"`
    Message string `json:"message,omitempty"`
}

func HealthHandler(w http.ResponseWriter, r *http.Request) {
    status := HealthStatus{
        Status:    "healthy",
        Version:   version,
        Uptime:    time.Since(startTime).Seconds(),
        Timestamp: time.Now(),
        Checks:    make(map[string]Check),
    }
    
    // Check Claude CLI
    if _, err := exec.LookPath("claude"); err != nil {
        status.Status = "degraded"
        status.Checks["claude"] = Check{
            Status:  "unhealthy",
            Message: "Claude CLI not found",
        }
    } else {
        status.Checks["claude"] = Check{Status: "healthy"}
    }
    
    // Check disk space
    var stat syscall.Statfs_t
    syscall.Statfs("/var/lib/pocket-agent", &stat)
    freeSpace := stat.Bavail * uint64(stat.Bsize) / (1024 * 1024 * 1024) // GB
    
    if freeSpace < 1 {
        status.Status = "degraded"
        status.Checks["disk"] = Check{
            Status:  "warning",
            Message: fmt.Sprintf("Low disk space: %dGB", freeSpace),
        }
    } else {
        status.Checks["disk"] = Check{Status: "healthy"}
    }
    
    // Check database/storage
    if err := checkStorage(); err != nil {
        status.Status = "unhealthy"
        status.Checks["storage"] = Check{
            Status:  "unhealthy",
            Message: err.Error(),
        }
    } else {
        status.Checks["storage"] = Check{Status: "healthy"}
    }
    
    w.Header().Set("Content-Type", "application/json")
    if status.Status != "healthy" {
        w.WriteHeader(http.StatusServiceUnavailable)
    }
    json.NewEncoder(w).Encode(status)
}
```

### Blackbox Exporter

For external monitoring:

```yaml
# blackbox.yml
modules:
  websocket_check:
    prober: tcp
    timeout: 5s
    tcp:
      tls: true
      tls_config:
        insecure_skip_verify: true

  http_check:
    prober: http
    timeout: 5s
    http:
      valid_status_codes: [200]
      valid_http_versions: ["HTTP/1.1", "HTTP/2.0"]
      method: GET
      tls_config:
        insecure_skip_verify: false
```

## Alerting

### Alert Rules

Create `/etc/prometheus/alerts/pocket-agent.yml`:

```yaml
groups:
  - name: pocket-agent
    interval: 30s
    rules:
      # Service alerts
      - alert: ServiceDown
        expr: up{job="pocket-agent"} == 0
        for: 2m
        labels:
          severity: critical
          service: pocket-agent
        annotations:
          summary: "Pocket Agent Server is down"
          description: "{{ $labels.instance }} has been down for more than 2 minutes."

      - alert: HighErrorRate
        expr: rate(pocket_agent_errors_total[5m]) > 0.05
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "High error rate detected"
          description: "Error rate is {{ $value }} errors per second"

      # Connection alerts
      - alert: TooManyConnections
        expr: pocket_agent_connections_active > 90
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "Connection limit approaching"
          description: "{{ $value }} active connections (limit: 100)"

      # Performance alerts
      - alert: SlowExecutions
        expr: histogram_quantile(0.95, rate(pocket_agent_execution_duration_seconds_bucket[5m])) > 120
        for: 10m
        labels:
          severity: warning
        annotations:
          summary: "Claude executions are slow"
          description: "95th percentile execution time is {{ $value }} seconds"

      # Resource alerts
      - alert: HighMemoryUsage
        expr: process_resident_memory_bytes{job="pocket-agent"} / 1024 / 1024 / 1024 > 2
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "High memory usage"
          description: "Process using {{ $value }}GB of memory"

      - alert: DiskSpaceLow
        expr: node_filesystem_avail_bytes{mountpoint="/var/lib/pocket-agent"} / node_filesystem_size_bytes{mountpoint="/var/lib/pocket-agent"} < 0.1
        for: 5m
        labels:
          severity: critical
        annotations:
          summary: "Low disk space"
          description: "Only {{ $value | humanizePercentage }} disk space remaining"

      # Business alerts
      - alert: NoExecutionsRecently
        expr: increase(pocket_agent_executions_total[1h]) == 0
        for: 2h
        labels:
          severity: info
        annotations:
          summary: "No Claude executions in past hour"
          description: "No activity detected, possible issue"
```

### Alertmanager Configuration

Create `/etc/alertmanager/alertmanager.yml`:

```yaml
global:
  resolve_timeout: 5m
  slack_api_url: 'YOUR_SLACK_WEBHOOK_URL'

route:
  group_by: ['alertname', 'cluster', 'service']
  group_wait: 10s
  group_interval: 10s
  repeat_interval: 12h
  receiver: 'default'
  routes:
    - match:
        severity: critical
      receiver: pagerduty
      continue: true
    - match:
        severity: warning
      receiver: slack

receivers:
  - name: 'default'
    slack_configs:
      - channel: '#alerts'
        title: 'Pocket Agent Alert'
        text: '{{ range .Alerts }}{{ .Annotations.summary }}\n{{ .Annotations.description }}{{ end }}'

  - name: 'slack'
    slack_configs:
      - channel: '#pocket-agent-alerts'
        send_resolved: true
        title: 'Pocket Agent {{ .Status | toUpper }}'
        text: '{{ range .Alerts }}{{ .Annotations.summary }}{{ end }}'

  - name: 'pagerduty'
    pagerduty_configs:
      - service_key: 'YOUR_PAGERDUTY_SERVICE_KEY'
        description: '{{ range .Alerts }}{{ .Annotations.summary }}{{ end }}'
```

## Dashboards

### Grafana Setup

```bash
# Install Grafana
sudo apt-get install -y software-properties-common
sudo add-apt-repository "deb https://packages.grafana.com/oss/deb stable main"
wget -q -O - https://packages.grafana.com/gpg.key | sudo apt-key add -
sudo apt-get update
sudo apt-get install grafana

# Start Grafana
sudo systemctl enable grafana-server
sudo systemctl start grafana-server
```

### Pocket Agent Dashboard

Create dashboard JSON:

```json
{
  "dashboard": {
    "title": "Pocket Agent Server",
    "panels": [
      {
        "title": "Active Connections",
        "targets": [
          {
            "expr": "pocket_agent_connections_active",
            "legendFormat": "Active"
          }
        ],
        "gridPos": {"h": 8, "w": 12, "x": 0, "y": 0}
      },
      {
        "title": "Execution Rate",
        "targets": [
          {
            "expr": "rate(pocket_agent_executions_total[5m])",
            "legendFormat": "{{ status }}"
          }
        ],
        "gridPos": {"h": 8, "w": 12, "x": 12, "y": 0}
      },
      {
        "title": "Execution Duration (95th percentile)",
        "targets": [
          {
            "expr": "histogram_quantile(0.95, rate(pocket_agent_execution_duration_seconds_bucket[5m]))",
            "legendFormat": "p95"
          }
        ],
        "gridPos": {"h": 8, "w": 12, "x": 0, "y": 8}
      },
      {
        "title": "Error Rate",
        "targets": [
          {
            "expr": "rate(pocket_agent_errors_total[5m])",
            "legendFormat": "{{ code }}"
          }
        ],
        "gridPos": {"h": 8, "w": 12, "x": 12, "y": 8}
      }
    ]
  }
}
```

### System Dashboard

Key metrics to monitor:

1. **Resource Usage**
   - CPU usage
   - Memory usage
   - Disk I/O
   - Network traffic

2. **Application Metrics**
   - Request rate
   - Error rate
   - Response time
   - Active connections

3. **Business Metrics**
   - Projects created
   - Executions per hour
   - Average execution time
   - Success rate

## Performance Monitoring

### APM with OpenTelemetry (Future)

```go
// tracing/tracing.go
import (
    "go.opentelemetry.io/otel"
    "go.opentelemetry.io/otel/trace"
)

var tracer trace.Tracer

func init() {
    tracer = otel.Tracer("pocket-agent-server")
}

func ExecuteWithTracing(ctx context.Context, projectID string) error {
    ctx, span := tracer.Start(ctx, "claude.execute")
    defer span.End()
    
    span.SetAttributes(
        attribute.String("project.id", projectID),
    )
    
    // Execution logic...
    
    return nil
}
```

### Load Testing

```bash
# Install k6
sudo apt-key adv --keyserver hkp://keyserver.ubuntu.com:80 --recv-keys C5AD17C747E3415A3642D57D77C6C491D6AC1D69
echo "deb https://dl.k6.io/deb stable main" | sudo tee /etc/apt/sources.list.d/k6.list
sudo apt-get update
sudo apt-get install k6

# Create load test
cat > load-test.js << 'EOF'
import ws from 'k6/ws';
import { check } from 'k6';

export let options = {
  stages: [
    { duration: '2m', target: 10 },
    { duration: '5m', target: 50 },
    { duration: '2m', target: 100 },
    { duration: '5m', target: 100 },
    { duration: '2m', target: 0 },
  ],
};

export default function () {
  const url = 'wss://localhost:8443/ws';
  const params = { tags: { my_tag: 'hello' } };

  const res = ws.connect(url, params, function (socket) {
    socket.on('open', () => {
      socket.send(JSON.stringify({
        type: 'project_list'
      }));
    });

    socket.on('message', (data) => {
      const msg = JSON.parse(data);
      check(msg, {
        'received project list': (m) => m.type === 'project_list_response',
      });
    });

    socket.setTimeout(() => {
      socket.close();
    }, 10000);
  });

  check(res, { 'status is 101': (r) => r && r.status === 101 });
}
EOF

# Run load test
k6 run load-test.js
```

## Troubleshooting

### Metrics Not Appearing

```bash
# Check if metrics endpoint is accessible
curl http://localhost:9090/metrics

# Check Prometheus targets
curl http://localhost:9090/api/v1/targets

# Verify Prometheus config
promtool check config /etc/prometheus/prometheus.yml
```

### High Cardinality Issues

```yaml
# Limit label cardinality
metric_relabel_configs:
  - source_labels: [project_id]
    regex: '.*'
    target_label: project_id
    replacement: 'aggregated'
```

### Dashboard Performance

1. Use recording rules for complex queries
2. Limit time ranges
3. Use proper aggregation
4. Cache dashboard queries

---

For more guides:
- [Docker Deployment](./docker.md)
- [Systemd Deployment](./systemd.md)
- [TLS Setup](./tls-setup.md)