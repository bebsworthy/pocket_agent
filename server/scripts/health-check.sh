#!/bin/bash
# health-check.sh - Health check script for Pocket Agent Server
#
# Usage: ./health-check.sh [options]
# Options:
#   -h, --host HOST      Server host (default: localhost)
#   -p, --port PORT      Server port (default: 8443)
#   -t, --timeout SEC    Timeout in seconds (default: 5)
#   -v, --verbose        Enable verbose output
#   -j, --json           Output in JSON format

set -euo pipefail

# Default values
HOST="localhost"
PORT="8443"
TIMEOUT="5"
VERBOSE=false
JSON_OUTPUT=false

# Parse arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        -h|--host)
            HOST="$2"
            shift 2
            ;;
        -p|--port)
            PORT="$2"
            shift 2
            ;;
        -t|--timeout)
            TIMEOUT="$2"
            shift 2
            ;;
        -v|--verbose)
            VERBOSE=true
            shift
            ;;
        -j|--json)
            JSON_OUTPUT=true
            shift
            ;;
        *)
            echo "Unknown option: $1"
            echo "Usage: $0 [-h HOST] [-p PORT] [-t TIMEOUT] [-v] [-j]"
            exit 1
            ;;
    esac
done

# Initialize status
STATUS="unknown"
DETAILS=""
EXIT_CODE=0

# Function to output result
output_result() {
    local status=$1
    local message=$2
    local exit_code=$3
    
    if [[ "$JSON_OUTPUT" == "true" ]]; then
        echo "{\"status\":\"$status\",\"message\":\"$message\",\"timestamp\":\"$(date -u +%Y-%m-%dT%H:%M:%SZ)\"}"
    else
        echo "[$status] $message"
    fi
    
    exit $exit_code
}

# Check if server is reachable
if [[ "$VERBOSE" == "true" ]]; then
    echo "Checking server at https://$HOST:$PORT..."
fi

# Try WebSocket health endpoint
HEALTH_RESPONSE=$(timeout "$TIMEOUT" curl -s -k -f "https://$HOST:$PORT/health" 2>&1 || true)

if [[ -n "$HEALTH_RESPONSE" ]] && echo "$HEALTH_RESPONSE" | jq -e . >/dev/null 2>&1; then
    # Parse JSON response
    HEALTH_STATUS=$(echo "$HEALTH_RESPONSE" | jq -r '.status // "unknown"')
    
    if [[ "$HEALTH_STATUS" == "healthy" ]]; then
        output_result "healthy" "Server is healthy" 0
    elif [[ "$HEALTH_STATUS" == "degraded" ]]; then
        DETAILS=$(echo "$HEALTH_RESPONSE" | jq -r '.checks | to_entries[] | select(.value.status != "healthy") | "\(.key): \(.value.message)"' | tr '\n' '; ')
        output_result "degraded" "Server is degraded: $DETAILS" 1
    else
        output_result "unhealthy" "Server is unhealthy" 2
    fi
else
    # Fallback: Check if port is open
    if timeout "$TIMEOUT" bash -c "echo > /dev/tcp/$HOST/$PORT" 2>/dev/null; then
        output_result "degraded" "Server is running but health endpoint not responding" 1
    else
        output_result "unhealthy" "Server is not responding on port $PORT" 2
    fi
fi