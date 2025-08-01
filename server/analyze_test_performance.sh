#!/bin/bash

echo "=== Go Test Performance Analysis ==="
echo "Generated at: $(date)"
echo ""

# Run tests with JSON output
echo "Running tests..."
go test -json ./... 2>/dev/null > test_results.json

echo ""
echo "=== Slowest Test Packages ==="
jq -r 'select(.Action=="pass" and .Elapsed and .Test == null) | [.Elapsed, .Package] | @tsv' test_results.json | \
    sort -n -r | head -10 | \
    awk '{printf "%.3fs\t%s\n", $1, $2}'

echo ""
echo "=== Slowest Individual Tests ==="
jq -r 'select(.Action=="pass" and .Elapsed and .Test) | [.Elapsed, .Package, .Test] | @tsv' test_results.json | \
    sort -n -r | head -20 | \
    awk '{printf "%.3fs\t%-60s\t%s\n", $1, $3, $2}'

echo ""
echo "=== Tests Taking > 1 Second ==="
jq -r 'select(.Action=="pass" and .Elapsed and .Test and .Elapsed > 1) | [.Elapsed, .Package, .Test] | @tsv' test_results.json | \
    sort -n -r | \
    awk '{printf "%.3fs\t%-60s\t%s\n", $1, $3, $2}'

echo ""
echo "=== Summary Statistics ==="
total_time=$(jq -r 'select(.Action=="pass" and .Elapsed and .Test == null) | .Elapsed' test_results.json | awk '{sum+=$1} END {print sum}')
test_count=$(jq -r 'select(.Action=="pass" and .Test) | .Test' test_results.json | wc -l)
echo "Total test execution time: ${total_time}s"
echo "Total number of tests: $test_count"

# Clean up
rm -f test_results.json