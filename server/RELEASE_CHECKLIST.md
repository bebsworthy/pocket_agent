# Release Checklist

This checklist ensures consistent and reliable releases of Pocket Agent Server.

## Pre-Release Checklist

### Code Quality
- [ ] All tests pass (`make test`)
- [ ] Code coverage meets minimum threshold (80%)
- [ ] No linting errors (`make lint`)
- [ ] Security scan passes (`make security-scan`)
- [ ] All dependencies are up to date
- [ ] CHANGELOG.md is updated with all changes

### Documentation
- [ ] README.md is up to date
- [ ] API documentation reflects current implementation
- [ ] Configuration examples are current
- [ ] Installation instructions tested on clean system
- [ ] Deployment guides verified
- [ ] Breaking changes clearly documented

### Version Management
- [ ] Version number follows semantic versioning
- [ ] VERSION file updated
- [ ] Version constants in code updated
- [ ] Compatible with previous version (or breaking changes documented)

## Release Process

### 1. Prepare Release Branch
```bash
# Create release branch
git checkout -b release/v1.0.0

# Update version
echo "v1.0.0" > VERSION

# Update CHANGELOG.md
# Add release date and finalize notes

# Commit changes
git add VERSION CHANGELOG.md
git commit -m "chore: prepare release v1.0.0"
```

### 2. Final Testing
- [ ] Run full test suite
- [ ] Test binary on Linux (amd64)
- [ ] Test binary on Linux (arm64)
- [ ] Test binary on macOS (amd64)
- [ ] Test binary on macOS (arm64)
- [ ] Test Docker image
- [ ] Test systemd installation
- [ ] Test upgrade from previous version

### 3. Security Review
- [ ] No hardcoded secrets
- [ ] No sensitive data in logs
- [ ] Path traversal protection verified
- [ ] Input validation comprehensive
- [ ] TLS configuration secure
- [ ] Dependencies scanned for vulnerabilities

### 4. Performance Validation
- [ ] Load tests pass baseline metrics
- [ ] Memory usage within limits
- [ ] No goroutine leaks
- [ ] Response times meet SLA

### 5. Create Release
```bash
# Merge to main
git checkout main
git merge --no-ff release/v1.0.0

# Run release script
./scripts/release.sh v1.0.0

# Or manual steps:
# 1. Tag release
git tag -a v1.0.0 -m "Release v1.0.0"

# 2. Push changes
git push origin main
git push origin v1.0.0

# 3. Build artifacts
make release-artifacts

# 4. Create GitHub release
gh release create v1.0.0 --title "v1.0.0" --notes-file RELEASE_NOTES.md dist/*
```

### 6. Publish Docker Images
- [ ] Build multi-arch Docker images
- [ ] Test Docker images
- [ ] Push to Docker Hub
- [ ] Push to GitHub Container Registry
- [ ] Update latest tag

```bash
# Build and push
docker buildx build --platform linux/amd64,linux/arm64 \
  -t pocket-agent-server:v1.0.0 \
  -t pocket-agent-server:latest \
  --push .
```

### 7. Post-Release Tasks
- [ ] Verify GitHub release is published
- [ ] Verify Docker images are available
- [ ] Update documentation site
- [ ] Announce release (blog, social media, etc.)
- [ ] Update Homebrew formula (if applicable)
- [ ] Monitor for issues

## Rollback Plan

If critical issues are found post-release:

1. **Immediate Actions**
   - [ ] Yank Docker images if necessary
   - [ ] Mark GitHub release as pre-release
   - [ ] Post security advisory if needed

2. **Fix Process**
   - [ ] Create hotfix branch from tag
   - [ ] Apply minimal fixes
   - [ ] Test thoroughly
   - [ ] Release as patch version

3. **Communication**
   - [ ] Notify users via GitHub issues
   - [ ] Update status page
   - [ ] Send email to mailing list

## Release Notes Template

```markdown
# Pocket Agent Server vX.Y.Z

Released: YYYY-MM-DD

## Highlights
- Brief summary of major changes
- Key features or improvements

## Breaking Changes
- List any breaking changes
- Migration instructions

## New Features
- Feature 1 (#PR)
- Feature 2 (#PR)

## Improvements
- Improvement 1 (#PR)
- Improvement 2 (#PR)

## Bug Fixes
- Fix 1 (#PR)
- Fix 2 (#PR)

## Dependencies
- Updated X to vY.Z
- Added dependency Y

## Contributors
Thanks to all contributors!
- @user1
- @user2

## Checksums
[SHA256 checksums for all artifacts]
```

## Version Numbering

Follow semantic versioning (MAJOR.MINOR.PATCH):

- **MAJOR**: Breaking API changes
- **MINOR**: New features, backward compatible
- **PATCH**: Bug fixes only

Pre-release versions:
- Alpha: v1.0.0-alpha.1
- Beta: v1.0.0-beta.1
- Release Candidate: v1.0.0-rc.1

## Emergency Release

For security patches or critical bugs:

1. **Create Patch**
   ```bash
   git checkout -b hotfix/v1.0.1 v1.0.0
   # Apply fixes
   git commit -m "fix: critical security issue"
   ```

2. **Fast Track Testing**
   - [ ] Security fix verified
   - [ ] Regression tests pass
   - [ ] No new issues introduced

3. **Release**
   ```bash
   ./scripts/release.sh v1.0.1 --skip-docker
   ```

4. **Communicate**
   - [ ] Security advisory published
   - [ ] Users notified
   - [ ] Upgrade instructions provided

---

**Remember**: Quality over speed. A delayed release is better than a broken one.