#!/bin/bash
# Rollback to a previous version
# Usage: ./rollback.sh <target_version>

TARGET_VERSION="$1"

if [ -z "$TARGET_VERSION" ]; then
    echo "Error: Target version required"
    echo "Usage: ./rollback.sh <version>"
    echo ""
    echo "Available versions:"
    git tag -l | sort -V | tail -10
    exit 1
fi

# Check if tag exists
if ! git rev-parse "v$TARGET_VERSION" &> /dev/null; then
    echo "Error: Tag v$TARGET_VERSION not found"
    exit 1
fi

echo "Rolling back to v$TARGET_VERSION..."
echo ""

# Get current version for confirmation
CURRENT_COMMIT=$(git rev-parse HEAD)
TARGET_COMMIT=$(git rev-parse "v$TARGET_VERSION")

echo "Current commit: $CURRENT_COMMIT"
echo "Target commit: $TARGET_COMMIT"
echo ""

read -p "Continue rollback? (y/n): " CONFIRM

if [ "$CONFIRM" = "y" ] || [ "$CONFIRM" = "Y" ]; then
    git checkout "v$TARGET_VERSION"
    git reset --hard "v$TARGET_VERSION"
    echo "✅ Rolled back to v$TARGET_VERSION"
else
    echo "Rollback cancelled"
    exit 0
fi
