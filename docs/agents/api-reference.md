# API Reference — All Live Endpoints

> Read this when implementing a new endpoint, writing integration tests, or verifying the contract matches the mobile client.

All endpoints except `/api/health` and `/api/auth/**` require `Authorization: Bearer <accessToken>`.

## Auth — `/api/auth`

```
POST /api/auth/register
  Body: { email, password, displayName, familyName }
  Returns 201: { accessToken, refreshToken, expiresIn, userId, familyIds }

POST /api/auth/login
  Body: { email, password }
  Returns 200: { accessToken, refreshToken, expiresIn, userId, familyIds }

POST /api/auth/refresh
  Header: X-Refresh-Token: <refreshToken>
  Returns 200: { accessToken, refreshToken, expiresIn, userId, familyIds }
```

## Accounts — `/api/accounts`

```
GET    /api/accounts              → all accounts for caller's family + personal
GET    /api/accounts/{id}         → single account (tenant-scoped, 404 if not owned)
POST   /api/accounts              Body: { name, type, balance, currency, familyId? }  → 201
PUT    /api/accounts/{id}         Body: same as POST
DELETE /api/accounts/{id}         → soft delete (sets deleted_at), 204
```

## Categories — `/api/categories`

```
GET    /api/categories            → all categories for caller's family + personal
GET    /api/categories/{id}
POST   /api/categories            Body: { name, type (INCOME|EXPENSE), familyId? }  → 201
PUT    /api/categories/{id}
DELETE /api/categories/{id}       → soft delete, 204
```

## Transactions — `/api/transactions`

```
GET    /api/transactions                        → all transactions visible to caller's tenant
GET    /api/transactions/{id}
GET    /api/transactions/account/{accountId}    → filtered by account (verifies account ownership)
POST   /api/transactions          Body: { accountId, categoryId?, amount, description?, date (YYYY-MM-DD) }  → 201
PUT    /api/transactions/{id}     Body: same as POST
DELETE /api/transactions/{id}     → soft delete, 204
```

## Budgets — `/api/budgets`

```
GET    /api/budgets                          → all budgets for caller's families
GET    /api/budgets/{id}
GET    /api/budgets/category/{categoryId}    → filtered by category
POST   /api/budgets               Body: { familyId, categoryId, amount, currency?, periodStart, periodEnd }  → 201
PUT    /api/budgets/{id}          Body: same as POST (familyId must match existing budget)
DELETE /api/budgets/{id}          → soft delete, 204
```

## Family — `/api/families`

```
POST   /api/families/{familyId}/invites
  Auth: caller must be ADMIN of familyId
  Returns 201: { id, code, expiresAt }
  Note: code is 8-char uppercase alphanumeric, expires in 7 days

POST   /api/families/join
  Body: { code }
  Returns 201: { familyId, familyName, role }
  Note: marks invite used_at + used_by; 409 if already a member

GET    /api/families/{familyId}/members
  Auth: caller must be a member of familyId
  Returns: [{ id, userId, email, displayName, role, joinedAt }]

PATCH  /api/families/{familyId}/members/{memberId}/role
  Auth: caller must be ADMIN
  Body: { role: "ADMIN"|"MEMBER" }
  Returns: FamilyMemberDto

DELETE /api/families/{familyId}/members/{memberId}
  Auth: caller must be ADMIN. Cannot remove self. Cannot remove last admin.
  Returns 204
```

## Sync — `/api/sync`

```
GET  /api/sync/pull?lastPulledAt=<epoch_ms>&schemaVersion=1
  Returns: {
    changes: {
      accounts:     { created: [...], updated: [...], deleted: ["uuid", ...] },
      categories:   { created: [...], updated: [...], deleted: ["uuid", ...] },
      transactions: { created: [...], updated: [...], deleted: ["uuid", ...] },
      budgets:      { created: [...], updated: [...], deleted: ["uuid", ...] }
    },
    timestamp: <epoch_ms>
  }
  Note: omitting lastPulledAt triggers full sync (all records since epoch).

POST /api/sync/push?lastPulledAt=<epoch_ms>
  Body: { changes: { <table>: { created: [...], updated: [...], deleted: ["uuid", ...] } } }
  Returns: { timestamp: <epoch_ms> }
```

## Import — `/api/import`

```
POST /api/import/coinkeeper
  Content-Type: multipart/form-data
  Params: file (CSV, max 10MB), accountId (UUID query param)
  CSV required columns: date (YYYY-MM-DD), amount, category, description, account
  Returns 200: { imported, duplicates, errors: [...] }
  Note: dedup via SHA-256(date + amount + description) stored in import_hash.
        Categories auto-created as EXPENSE type if not found.
```

## Health

```
GET /api/health   → { status: "UP", timestamp: "..." }  (no auth required)
```

## Not Yet Built

```
POST   /api/auth/forgot-password          ❌  Phase 4 — email with reset token
POST   /api/auth/reset-password           ❌  Phase 4 — consume token, set new password
DELETE /api/users/me                      ❌  Phase 6 — hard purge all user data (GDPR)

# Phase 8 web additions (not needed until web app is built):
GET /api/transactions?page=&size=&sort=             ❌  pagination for web data table
GET /api/transactions?from=&to=&categoryId=&q=      ❌  advanced filters
GET /api/reports/monthly-summary                    ❌
GET /api/reports/category-trend                     ❌
POST /api/transactions/{id}/split                   ❌
POST /api/budgets/from-template                     ❌
```
