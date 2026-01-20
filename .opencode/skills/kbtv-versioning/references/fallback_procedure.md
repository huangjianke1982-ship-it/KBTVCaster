# Fallback Procedures

When GitHub is unavailable, this skill uses local archiving as a reliable fallback.

## Fallback Scenarios

| Scenario | Detection | Action |
|----------|-----------|--------|
| No network | `ping github.com` fails | Create local archive |
| GitHub API timeout | `gh release create` times out | Create local archive |
| gh CLI not installed | `command -v gh` fails | Create local archive |
| Auth failure | `gh auth status` fails | Create local archive |

## Local Archive Structure

```
releases/
└── v{x.x.x}/
    ├── manifest.json          # Release metadata
    ├── project-{version}.tar.gz  # Project snapshot
    └── VERSION_v{x.x.x}.md    # Changelog

archives/
└── {date}-{archive-name}/
    ├── project.tar.gz
    ├── info.txt
    └── MANIFEST.json
```

## Manifest Format

```json
{
  "version": "1.0.0",
  "commit": "abc123def",
  "date": "2026-01-20T10:00:00+08:00",
  "type": "local_archive"
}
```

## Recovery Procedures

### Restore from Local Archive

```bash
# Extract project
tar -xzf releases/v1.0.0/project-1.0.0.tar.gz

# Or checkout specific tag
git checkout v1.0.0
```

### Sync to GitHub Later

When network is available:

```bash
# Create GitHub release from local changelog
gh release create v1.0.0 --title "Release v1.0.0" --notes "$(cat VERSION_v1.0.0.md)"
```

### Push Tag to Remote

```bash
# Push tag to remote
git push origin v1.0.0

# Or push all tags
git push --tags
```

## Network Resilience Best Practices

1. **Always generate changelog first** - Works offline
2. **Tag locally before pushing** - Safe if network fails mid-release
3. **Keep local archives** - Permanent record even if GitHub has issues
4. **Document in INDEX.md** - Track all releases locally

## Checking Network Status

```bash
# Quick connectivity check
ping -c 1 github.com

# GitHub API check
curl -Is https://github.com | head -1

# gh CLI status
gh auth status
```
