#!/bin/bash

# Read input from stdin or file
input="${1:-/dev/stdin}"

# Process the input and group by issue type
awk '
{
    # Extract issue type (everything before first " - ")
    idx = index($0, " - ")
    if (idx > 0) {
        issue_type = substr($0, 1, idx - 1)
        
        # Extract location (everything after " at ")
        at_idx = index($0, " at ")
        if (at_idx > 0) {
            location_part = substr($0, at_idx + 4)
            # Remove " - Signature=..." part if it exists
            sig_idx = index(location_part, " - Signature=")
            if (sig_idx > 0) {
                location = substr(location_part, 1, sig_idx - 1)
            } else {
                location = location_part
            }
            
            # Store locations for each issue type
            if (!(issue_type in issues)) {
                issues[issue_type] = ""
            }
            issues[issue_type] = issues[issue_type] "  - " location "\n"
        }
    }
}
END {
    # Print grouped results
    for (issue in issues) {
        print issue
        printf "%s", issues[issue]
    }
}' "$input"