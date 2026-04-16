CREATE TABLE accounts (
    id          UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(100)   NOT NULL,
    type        VARCHAR(20)    NOT NULL DEFAULT 'CHECKING'
                    CHECK (type IN ('CHECKING', 'SAVINGS', 'CREDIT', 'CASH', 'INVESTMENT')),
    balance     NUMERIC(12, 2) NOT NULL DEFAULT 0.00,
    currency    CHAR(3)        NOT NULL DEFAULT 'USD',

    -- Ownership: exactly one of family_id or user_id must be set
    family_id   UUID           REFERENCES families(id),
    user_id     UUID           REFERENCES users(id),

    created_at  TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    deleted_at  TIMESTAMPTZ,
    version     BIGINT         NOT NULL DEFAULT 0,

    -- Mutual exclusivity: exactly one owner
    CONSTRAINT chk_accounts_has_owner
        CHECK (family_id IS NOT NULL OR user_id IS NOT NULL),
    CONSTRAINT chk_accounts_single_owner
        CHECK (NOT (family_id IS NOT NULL AND user_id IS NOT NULL))
);

CREATE INDEX idx_accounts_family ON accounts(family_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_accounts_user   ON accounts(user_id)   WHERE deleted_at IS NULL;
