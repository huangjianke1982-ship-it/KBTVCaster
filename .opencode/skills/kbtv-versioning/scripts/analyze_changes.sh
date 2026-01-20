#!/bin/bash
# Analyze git changes since last version
# Usage: ./analyze_changes.sh [--output json]

OUTPUT_FORMAT="${1:-text}"

# Get latest tag
LATEST_TAG=$(git describe --tags --abbrev=0 2>/dev/null || echo "")

if [ -z "$LATEST_TAG" ]; then
    echo "No previous tag found. This is a new project."
    COMMITgit rev-list --_COUNT=$(count HEAD)
    CHANGED_FILES=$(git diff --name-only HEAD | wc -l)
    COMMITS=$(git log --oneline -10)
else
    echo "Latest tag: $LATEST_TAG"
    COMMIT_COUNT=$(git rev-list "$LATEST_TAG"..HEAD --count)
    CHANGED_FILES=$(git diff --name-only "$LATEST_TAG" HEAD | wc -l)
    COMMITS=$(git log "$LATEST_TAG"..HEAD --oneline)
fi

if [ "$OUTPUT_FORMAT" = "--output" ] && [ "$2" = "json" ]; then
    cat <<EOF
{
  "latest_tag": "$LATEST_TAG",
  "commit_count": $COMMIT_COUNT,
  "changed_files": $CHANGED_FILES,
  "recent_commits": $(echo "$COMMITS" | head -20 | while IFS= read -r line; do echo "\"$line\""; done | tr '\n' ',' | sed 's/,$//')
}
EOF
else
    echo "Commits since last version: $COMMIT_COUNT"
    echo "Changed files: $CHANGED_FILES"
    echo ""
    echo "Recent commits:"
    echo "$COMMITS"
fi
