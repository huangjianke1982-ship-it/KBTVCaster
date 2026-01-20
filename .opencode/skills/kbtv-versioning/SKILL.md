---
name: kbtv-versioning
description: KBTVCaster Android project version management. Record releases with semantic versioning, git tags, and changelogs tailored for Android/Kotlin projects. Use when user wants to (1) document KBTVCaster stable releases with DLNA/UPnP casting features, (2) create git tags for Android APK releases, (3) publish to GitHub with automatic local fallback, (4) rollback to previous versions, or (5) archive project snapshots. Customized for KBTVCaster project structure and versioning conventions. Trigger phrases: 记录项目, record project, version, 版本, create release, 发布版本, 归档项目, archive project, rollback, 回滚, KBTV版本, KBTVCaster发布.
license: MIT
compatibility: opencode
metadata:
  audience: kbtv-developers
  workflow: android-release-management
---

## What I Do

I manage KBTVCaster Android project versions throughout the development lifecycle:

- **Android Release Management**: Analyze changes in Kotlin/Java code, determine semantic version for APK releases
- **Changelog Generation**: Create VERSION_v{x.x.x}.md with DLNA/UPnP, ExoPlayer, and Android-specific features
- **Release Publishing**: Publish Android APK releases to GitHub with automatic local archive fallback
- **Project Archiving**: Create point-in-time snapshots of KBTVCaster source code and build configs
- **Version Rollback**: Safely rollback to previous stable releases via git tags

## When to Use Me

Use this skill when user asks to:

| Request Type | Examples |
|--------------|----------|
| Record version | "记录项目", "record project", "version", "版本", "create release", "发布版本", "KBTV版本", "标记KBTV版本" |
| Archive project | "归档项目", "archive project", "snapshot project" |
| Rollback version | "rollback", "回滚", "revert to previous version" |
| Create release | "发布新版本", "create a release", "标记版本", "KBTVCaster发布" |
| Android specific | "APK版本", "Android发布", "new release" |

## Workflow

### Version Recording (Semi-Automated)

1. **Analyze changes** since last version
2. **Suggest version bump** (major/minor/patch/prerelease)
3. **Wait for user confirmation** before proceeding
4. **Execute release**:
   - Create git commit
   - Create annotated tag
   - Generate changelog
   - Commit changelog
   - Update tag to include changelog
   - Publish to GitHub (or local archive fallback)

### Project Archiving

1. Capture git state (commit, branch, status)
2. Archive project files
3. Create manifest with metadata
4. Update archives index

### Rollback

1. List available versions
2. Confirm target version
3. Checkout and hard reset to target

## KBTVCaster Project Specifics

This skill is customized for the KBTVCaster Android DLNA/UPnP casting application:

- **Version Format**: Follows Android app conventions (e.g., v0.1.1, v1.0.0)
- **Changelog Categories**:
  - DLNA/UPnP casting features
  - ExoPlayer media playback improvements
  - Android service lifecycle changes
  - NSD/SSDP discovery updates
- **Build Integration**: Works with Gradle build system and APK outputs
- **Release Notes**: Include feature highlights like device discovery, playback control, and service stability

## Key Principles

- **Always confirm version** before creating release (semi-automated)
- **Generate changelog first** - works offline
- **Try GitHub first, fallback to local** - network resilient
- **Tag before pushing** - safe if network fails mid-release
- **Include rollback commands** - in every changelog

## Example Interaction

```
User: "记录项目"
System: Analyzes changes...
  Latest tag: v0.1.1
  Commits since: 5
  Changed files: 12

  Suggestion: Minor version bump (0.1.1 → 0.1.2)

  Plan:
  1. Create commit: "Release v0.1.2"
  2. Create tag: v0.1.2
  3. Generate changelog: VERSION_v0.1.2.md
  4. Publish to GitHub (fallback to local archive)

  Proceed with this plan? (confirm with "同意" or provide different version)

User: "同意"
System: [Executes all steps]
  ✅ Created commit: abc1234
  ✅ Created tag: v0.1.2
  ✅ Created changelog: VERSION_v0.1.2.md
  ✅ Published to GitHub Releases (or local archive)
```

## Output Format

On success:
```
✅ KBTVCaster version v{x.x.x} recorded successfully!

Git:
- Commit: {hash}
- Tag: v{x.x.x}

Files:
- VERSION_v{x.x.x}.md

Rollback:
git checkout v{x.x.x}
git reset --hard v{x.x.x}
```

On GitHub publish failure (fallback):
```
⚠️ GitHub unavailable, created local archive instead

Local Archive:
- releases/v{x.x.x}/project-{x.x.x}.tar.gz
- releases/v{x.x.x}/VERSION_v{x.x.x}.md
- releases/v{x.x.x}/manifest.json

Sync to GitHub when online:
gh release create v{x.x.x} --notes "$(cat VERSION_v{x.x.x}.md)"
```
