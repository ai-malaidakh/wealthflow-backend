# Git & Commit Strategy

> Read this before committing or tagging a release.

## Branching

- `main` is the only permanent branch — Railway auto-deploys from it. **Commit directly to `main`** for all normal work.
- No `develop`, `feature/*`, `release/*`, `hotfix/*` branches.

## Commit Format

`<type>(<scope>): <description>`

**Types:** `feat` · `fix` · `refactor` · `chore` · `test` · `docs` · `perf` · `db`

**Scopes:** `auth` · `sync` · `accounts` · `categories` · `transactions` · `budgets` · `family` · `import` · `security` · `db` · `ci` · `deps`

**Examples:**
```
feat(auth): add forgot-password and reset-password endpoints
feat(db): V13 add email_verification_tokens table
fix(sync): handle null familyIds in AccountSyncHandler pull query
fix(security): gate DebugController behind @Profile("dev")
test(family): assert non-member cannot list family members
chore(deps): add bucket4j for rate limiting
```

## Releases

SemVer annotated tags — Railway deploys are tagged before each promotion:
```bash
git tag v0.4.0 -m "Phase 4 complete — family sharing live"
```
