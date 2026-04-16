CREATE TABLE categories (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(100) NOT NULL,
    type        VARCHAR(20) NOT NULL DEFAULT 'EXPENSE'
        CHECK (type IN ('INCOME', 'EXPENSE')),

    -- Ownership: exactly one of family_id or user_id must be set
    family_id   UUID REFERENCES families(id),
    user_id     UUID REFERENCES users(id),

    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at  TIMESTAMPTZ,
    version     BIGINT NOT NULL DEFAULT 0,

    -- Mutual exclusivity: exactly one owner
    CONSTRAINT chk_categories_has_owner
        CHECK (family_id IS NOT NULL OR user_id IS NOT NULL),
    CONSTRAINT chk_categories_single_owner
        CHECK (NOT (family_id IS NOT NULL AND user_id IS NOT NULL))
);

CREATE INDEX idx_categories_family ON categories(family_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_categories_user ON categories(user_id) WHERE deleted_at IS NULL;
