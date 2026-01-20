#!/bin/bash
# Determine semantic version number based on changes
# Usage: ./determine_version.sh <current_version> <change_type>
# change_type: major|minor|patch|prerelease

CURRENT_VERSION="${1:-0.0.0}"
CHANGE_TYPE="${2:-patch}"

# Parse current version
IFS='.' read -ra VERSION_PARTS <<< "$CURRENT_VERSION"
MAJOR="${VERSION_PARTS[0]:-0}"
MINOR="${VERSION_PARTS[1]:-0}"
PATCH="${VERSION_PARTS[2]:-0}"
PRERELEASE="${VERSION_PARTS[3]:-}"

case "$CHANGE_TYPE" in
    major)
        NEW_VERSION="$((MAJOR + 1)).0.0"
        ;;
    minor)
        NEW_VERSION="$MAJOR.$((MINOR + 1)).0"
        ;;
    patch)
        NEW_VERSION="$MAJOR.$MINOR.$((PATCH + 1))"
        ;;
    prerelease)
        # Auto-increment pre-release version
        if [ -n "$PRERELEASE" ]; then
            IFS='-' read -ra PRERELEASE_PARTS <<< "$PRERELEASE"
            PRE_BASE="${PRERELEASE_PARTS[0]}"
            PRE_NUM="${PRERELEASE_PARTS[1]:-0}"
            NEW_VERSION="$MAJOR.$MINOR.$PATCH-$PRE_BASE$((PRE_NUM + 1))"
        else
            NEW_VERSION="$MAJOR.$MINOR.$PATCH-alpha.1"
        fi
        ;;
    *)
        NEW_VERSION="$CURRENT_VERSION"
        ;;
esac

echo "$NEW_VERSION"
