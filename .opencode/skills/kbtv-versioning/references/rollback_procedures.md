# Rollback Procedures

## When to Rollback

- Critical bug introduced in release
- Compatibility issues with dependencies
- Breaking changes discovered post-release
- Failed deployment requiring previous version

## Rollback Methods

### Method 1: Git Tag Rollback (Recommended)

```bash
# Checkout the previous version
git checkout v0.9.0

# Reset working directory
git reset --hard v0.9.0

# Verify
git log --oneline -3
```

### Method 2: Commit-based Rollback

```bash
# Find the commit to rollback to
git log --oneline

# Hard reset to previous commit
git reset --hard abc123def
```

### Method 3: Revert Changes

```bash
# Revert a specific commit
git revert abc123def

# Revert range of commits
git revert abc123def..fed987cba

# Create revert commit
git commit -m "Revert: Revert feature X due to bug"
```

## Rollback Safety Checklist

Before rolling back:

- [ ] Confirm target version works
- [ ] Notify team of rollback
- [ ] Document reason for rollback
- [ ] Test target version locally

## After Rollback

1. **Create hotfix release** with bug fix
2. **Update changelog** documenting the rollback
3. **Post-mortem** on why the bug was missed

## Version Recovery Commands

```bash
# List available versions
git tag -l | sort -V

# View version details
git log v1.0.0 --oneline -1

# Compare versions
git diff v0.9.0 v1.0.0

# Find commits between versions
git log v0.9.0..v1.0.0 --oneline
```

## Preventing Need for Rollback

1. **Test thoroughly** before release
2. **Use pre-release versions** (alpha/beta/rc)
3. **Gradual rollout** for critical changes
4. **Monitor metrics** after release

## Rollback Script Usage

```bash
# Interactive rollback
./scripts/rollback.sh

# List available versions
./scripts/rollback.sh --list

# Direct rollback (not recommended - skips confirmation)
./scripts/rollback.sh 0.9.0
```
