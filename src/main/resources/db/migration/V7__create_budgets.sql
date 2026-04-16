CREATE TABLE budgets (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    family_id       UUID NOT NULL REFERENCES families(id),
    category_id     UUID NOT NULL REFERENCES categories(id),
    amount          NUMERIC(12, 2) NOT NULL DEFAULT 0.00,
    currency        CHAR(3) NOT NULL DEFAULT 'USD',
    period_start    DATE NOT NULL,
    period_end      DATE NOT NULL,

    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at      TIMESTAMPTZ,
    version         BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_budgets_family ON budgets(family_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_budgets_category ON budgets(category_id) WHERE deleted_at IS NULL;
