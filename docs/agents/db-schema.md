# Database Schema

> Read this when writing migrations, adding entities, or debugging data issues.

## Golden Rules

- Money is `NUMERIC(12,2)`. Never `FLOAT` or `DOUBLE`.
- Every mutable table has `created_at TIMESTAMPTZ`, `updated_at TIMESTAMPTZ`, `deleted_at TIMESTAMPTZ` (soft delete), and `version BIGINT`.
- `deleted_at` is a one-way latch — once set, it cannot be cleared by a sync push or any normal code path.
- IDs are `UUID`. Client-generated UUIDs are accepted on sync push (idempotent create).
- Tenant ownership on `accounts` and `categories`: exactly one of `family_id` or `user_id` is non-null, enforced by a DB CHECK constraint.
- `@PreUpdate` on every entity auto-increments `version` and sets `updatedAt`. Every new entity must have this hook — sync conflict detection depends on it.

## Applied Migrations (V1–V12)

**Next migration number: `V13__...`**

| Version | File | What it does |
|---|---|---|
| V1 | `V1__create_users.sql` | `users` table |
| V2 | `V2__create_families_and_members.sql` | `families` + `family_members` tables |
| V3 | `V3__create_accounts.sql` | `accounts` table with ownership CHECK constraint |
| V4 | `V4__create_audit_columns.sql` | Audit columns backfilled |
| V5 | `V5__create_categories.sql` | `categories` table |
| V6 | `V6__create_transactions.sql` | `transactions` table (`amount NUMERIC(12,2)`, `currency CHAR(3)` default `USD`) |
| V7 | `V7__create_budgets.sql` | `budgets` table |
| V8 | `V8__create_sync_conflicts.sql` | `sync_conflicts` table (JSONB client_data/server_data, resolution enum) |
| V9 | `V9__fix_currency_column_type.sql` | Currency column type fix |
| V10 | `V10__add_family_members_audit_columns.sql` | Audit columns on `family_members` |
| V11 | `V11__add_import_hash_to_transactions.sql` | `import_hash` on `transactions` (SHA-256 dedup for CSV import) |
| V12 | `V12__create_family_invites.sql` | `family_invites` — code VARCHAR(12), expires_at, used_at, used_by |
