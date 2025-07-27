#!/bin/bash

# Run detekt and fix violations iteratively until none remain

while true; do
    # Run detekt
    echo "Running detekt..."
    ./gradlew detekt
    
    # Check if the report file exists
    if [ ! -f "/Users/boyd/wip/pocket_agent/frontend/app/build/reports/detekt/detekt.txt" ]; then
        echo "No detekt report found or detekt completed successfully!"
        break
    fi
    
    # Check if the file is empty
    if [ ! -s "/Users/boyd/wip/pocket_agent/frontend/app/build/reports/detekt/detekt.txt" ]; then
        echo "No detekt violations found!"
        break
    fi
    
    # Get the first 10 violations after sorting
    echo "Processing next batch of violations..."
    echo "Fix the following issues:" | cat - <(sort /Users/boyd/wip/pocket_agent/frontend/app/build/reports/detekt/detekt.txt | head -10)
    echo "Fix the following issues:" | cat - <(sort /Users/boyd/wip/pocket_agent/frontend/app/build/reports/detekt/detekt.txt | head -10) | claude -p --verbose --output-format stream-json --permission-mode acceptEdits --continue | jq -r '
        select(.type == "assistant" and .message.content) |
        .message.content[] |
        if .type == "text" then
            .text
        elif .type == "tool_use" then
            "🔧 Using tool: " + .name
        else
            empty
        end'
    
    # Give Claude time to process and make changes
    echo "Waiting for changes to be applied..."
    sleep 5
done

echo "All detekt violations have been resolved!"