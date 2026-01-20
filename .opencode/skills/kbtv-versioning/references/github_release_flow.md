# GitHub Release Workflow

## Overview

This skill supports publishing releases to GitHub with automatic fallback to local archives.

## Publishing Options

### Option 1: GitHub Releases (Primary)

Requires:
- `gh` CLI installed and authenticated
- Network connectivity to GitHub
- Repository write permissions

Commands used:
```bash
# Check authentication
gh auth status

# Create release from tag
gh release create v1.0.0 --title "Release v1.0.0" --notes "$(cat CHANGELOG.md)"
```

### Option 2: Local Archive (Fallback)

When GitHub is unavailable, the skill creates a local archive:

```
releases/
├── v1.0.0/
│   ├── project-1.0.0.tar.gz    # Project snapshot
│   ├── VERSION_v1.0.0.md       # Changelog
│   └── manifest.json           # Release metadata
└── INDEX.md                    # Release index
```

## Release Process Flow

```
1. Analyze changes
   ↓
2. Determine version (user confirms)
   ↓
3. Create git commit
   ↓
4. Create git tag
   ↓
5. Generate changelog (VERSION_v{x.x.x}.md)
   ↓
6. Commit changelog
   ↓
7. Update tag (include changelog)
   ↓
8. Try GitHub release
   ↓
   ├─→ Success → ✅ Done
   ↓
   Failed → Create local archive → ✅ Done
```

## Handling Network Issues

The skill automatically detects network problems:

1. **Network unreachable**: Fallback to local archive immediately
2. **GitHub API error**: Retry once, then fallback
3. **gh CLI not installed**: Skip GitHub, use local archive

## Best Practices

### Before Creating Release

- [ ] All tests pass
- [ ] No uncommitted changes (or committed)
- [ ] Changelog reviewed
- [ ] Version number confirmed

### Release Checklist

- [ ] Tag points to correct commit
- [ ] Changelog includes all changes
- [ ] Release notes are clear
- [ ] Version is properly semantic

## Troubleshooting

### "gh: command not found"

Install GitHub CLI:
- **macOS**: `brew install gh`
- **Linux**: `apt install gh` or `yum install gh`
- **Windows**: `winget install GitHub.cli`

### "gh: authentication failed"

Authenticate:
```bash
gh auth login
```

### "remote: Repository not found"

Check repository URL and permissions.

### Tag already exists

Delete existing tag and recreate:
```bash
git tag -d v1.0.0
git tag -a v1.0.0 -m "Release v1.0.0"
```
