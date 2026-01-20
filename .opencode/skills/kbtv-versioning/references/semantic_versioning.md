# Semantic Versioning Reference

Semantic Versioning (SemVer) uses the format: `MAJOR.MINOR.PATCH`

## Version Components

| Component | Meaning | Example |
|-----------|---------|---------|
| MAJOR | Incompatible API changes | `1.0.0 → 2.0.0` |
| MINOR | New functionality (backward compatible) | `1.0.0 → 1.1.0` |
| PATCH | Backward compatible bug fixes | `1.0.0 → 1.0.1` |

## Pre-release Versions

Format: `MAJOR.MINOR.PATCH-<prerelease>`

| Pre-release | Meaning |
|-------------|---------|
| `alpha` | Alpha release (unstable, may have bugs) |
| `beta` | Beta release (feature complete, testing) |
| `rc` | Release Candidate (final testing) |

Examples:
- `1.0.0-alpha.1` - First alpha
- `1.0.0-beta.2` - Second beta
- `1.0.0-rc.1` - First release candidate

## Version Bump Guidelines

### Major (MAJOR.MINOR.PATCH → (MAJOR+1).0.0)

- Remove public API
- Change API behavior in a backward-incompatible way
- Rename public methods/classes
- Change function signatures
- Remove configuration options

### Minor ((MAJOR).MINOR.PATCH → (MAJOR).(MINOR+1).0)

- Add new public API
- Add new optional parameters with defaults
- Add new configuration options
- Deprecate old API (with warnings)

### Patch ((MAJOR).(MINOR).PATCH → (MAJOR).(MINOR).(PATCH+1))

- Fix bugs
- Performance improvements
- Internal refactoring
- Documentation updates

## Automatic Version Detection

The skill analyzes commits to suggest version bump:

```bash
# Check for breaking changes in commits
git log --oneline --grep="BREAKING" --grep="breaking" -i

# Check for feature commits
git log --oneline --grep="feat" --grep="feature"

# Check for fix commits
git log --oneline --grep="fix" --grep="bug" -i
```

## Examples

| Commit Messages | Suggested Version |
|-----------------|-------------------|
| "fix: resolve login bug" | Patch |
| "feat: add dark mode" | Minor |
| "feat!: remove deprecated API" | Major |
| "feat: add API\nfix: fix bug" | Minor (features take precedence) |
| "chore: update docs" | Patch (or no version) |
