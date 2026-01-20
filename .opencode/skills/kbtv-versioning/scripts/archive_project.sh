#!/bin/bash
# Archive the project at current state
# Usage: ./archive_project.sh [--name <archive_name>]

ARCHIVE_NAME="${1:-project-archive}"

DATE=$(date +"%Y%m%d")
ARCHIVE_DIR="archives/$DATE-$ARCHIVE_NAME"
LATEST_LINK="archives/latest"

mkdir -p "$ARCHIVE_DIR"

# Get project info
echo "Archiving project..."
echo ""

# Git info
echo "Git Information:" > "$ARCHIVE_DIR/info.txt"
echo "===============" >> "$ARCHIVE_DIR/info.txt"
echo "Current commit: $(git rev-parse HEAD)" >> "$ARCHIVE_DIR/info.txt"
echo "Branch: $(git branch --show-current)" >> "$ARCHIVE_DIR/info.txt"
echo "Status: $(git status --short | head -5)" >> "$ARCHIVE_DIR/info.txt"
echo "" >> "$ARCHIVE_DIR/info.txt"

# Untracked files count
UNTRACKED=$(git ls-files --others --exclude-standard | wc -l)
echo "Untracked files: $UNTRACKED" >> "$ARCH"
IVE_DIR/info.txtecho "" >> "$ARCHIVE_DIR/info.txt"

# Latest tags
echo "Recent tags:" >> "$ARCHIVE_DIR/info.txt"
git tag -l --sort=-version:refname | head -5 >> "$ARCHIVE_DIR/info.txt"

# Archive git state (without .git directory)
git archive HEAD --prefix="project/" -o "$ARCHIVE_DIR/project.tar.gz"

# Copy important files
for file in package.json pyproject.toml requirements.txt Cargo.toml go.mod pom.xml build.gradle settings.gradle; do
    if [ -f "$file" ]; then
        cp "$file" "$ARCHIVE_DIR/"
    fi
done

# Create archive manifest
cat > "$ARCHIVE_DIR/MANIFEST.json" <<EOF
{
  "name": "$ARCHIVE_NAME",
  "date": "$(date -Iseconds)",
  "commit": "$(git rev-parse HEAD)",
  "branch": "$(git branch --show-current)",
  "files": {
    "project.tar.gz": "Complete project snapshot",
    "info.txt": "Git information",
    "MANIFEST.json": "This file"
  }
}
EOF

# Update latest symlink
rm -f "$LATEST_LINK"
ln -s "$DATE-$ARCHIVE_NAME" "$LATEST_LINK"

echo "✅ Created archive: $ARCHIVE_DIR/"
echo ""
echo "Archive contents:"
ls -la "$ARCHIVE_DIR/"

cat "$ARCHIVE_DIR/info.txt"
