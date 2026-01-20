#!/bin/bash
# Create git commit for release
# Usage: ./create_commit.sh <version>

VERSION="$1"

if [ -z "$VERSION" ]; then
    echo "Error: Version required"
    exit 1
fi

git add -A
git commit -m "Release v$VERSION"
echo "Created commit for v$VERSION"
