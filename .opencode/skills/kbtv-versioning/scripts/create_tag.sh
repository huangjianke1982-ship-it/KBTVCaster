#!/bin/bash
# Create git annotated tag for release
# Usage: ./create_tag.sh <version> [message]

VERSION="$1"
MESSAGE="${2:-Release v$VERSION}"

if [ -z "$VERSION" ]; then
    echo "Error: Version required"
    exit 1
fi

git tag -a "v$VERSION" -m "$MESSAGE"
echo "Created tag v$VERSION"
