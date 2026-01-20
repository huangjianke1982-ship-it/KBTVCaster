#!/bin/bash
# Publish release to GitHub or create local archive as fallback
# Usage: ./publish_release.sh <version> <tag_commit_sha>

VERSION="$1"
TAG_COMMIT="$2"

# Try GitHub release first
publish_to_github() {
    if ! command -v gh &> /dev/null; then
        echo "GitHub CLI (gh) not installed"
        return 1
    fi

    # Check network connectivity
    if ! ping -c 1 github.com &> /dev/null; then
        echo "Cannot reach GitHub, using fallback"
        return 1
    fi

    # Create GitHub release
    local CHANGELOG_FILE="VERSION_v${VERSION}.md"
    if [ -f "$CHANGELOG_FILE" ]; then
        local BODY=$(cat "$CHANGELOG_FILE")
        gh release create "v$VERSION" --title "Release v$VERSION" --notes "$BODY"
        echo "✅ Published to GitHub Releases: v$VERSION"
        return 0
    else
        gh release create "v$VERSION"
        echo "✅ Published to GitHub Releases: v$VERSION (without changelog)"
        return 0
    fi
}

# Fallback: Create local archive
create_local_archive() {
    echo "Creating local release archive as fallback..."

    local ARCHIVE_DIR="releases/v$VERSION"
    mkdir -p "$ARCHIVE_DIR"

    # Copy changelog
    if [ -f "VERSION_v${VERSION}.md" ]; then
        cp "VERSION_v${VERSION}.md" "$ARCHIVE_DIR/"
    fi

    # Export files at this commit
    git archive "$TAG_COMMIT" --prefix="project-$VERSION/" -o "$ARCHIVE_DIR/project-$VERSION.tar.gz"

    # Create manifest
    cat > "$ARCHIVE_DIR/manifest.json" <<EOF
{
  "version": "$VERSION",
  "commit": "$TAG_COMMIT",
  "date": "$(date -Iseconds)",
  "type": "local_archive"
}
EOF

    echo "✅ Created local archive: $ARCHIVE_DIR/"
    echo "📦 Archive contents:"
    ls -la "$ARCHIVE_DIR/"

    # Update releases index
    mkdir -p releases
    cat >> releases/INDEX.md <<EOF
## v$VERSION
- Date: $(date +"%Y-%m-%d")
- Local Archive: [releases/v$VERSION/](releases/v$VERSION/)
- Commit: \`$TAG_COMMIT\`

EOF
}

# Main logic
if publish_to_github; then
    exit 0
else
    echo "⚠️  GitHub release failed, using local archive fallback"
    create_local_archive
    exit 0
fi
