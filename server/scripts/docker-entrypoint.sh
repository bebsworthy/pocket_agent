#!/bin/sh
set -e

# Create required directories
mkdir -p "${DATA_DIR}/projects"
mkdir -p "${DATA_DIR}/logs"

# Check if Claude CLI is available
if ! command -v claude >/dev/null 2>&1; then
    echo "WARNING: Claude CLI not found in PATH"
    echo "The server will start but Claude execution will fail"
    echo "Please ensure Claude CLI is installed and accessible"
fi

# Handle SIGTERM for graceful shutdown
trap 'echo "Received SIGTERM, shutting down..."; kill -TERM $PID' TERM

# Execute the server
exec "$@" &
PID=$!
wait $PID