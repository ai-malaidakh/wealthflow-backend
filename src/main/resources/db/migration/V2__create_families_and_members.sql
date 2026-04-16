CREATE TABLE families (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(100) NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    deleted_at  TIMESTAMPTZ,
    version     BIGINT       NOT NULL DEFAULT 0
);

CREATE TABLE family_members (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    family_id   UUID         NOT NULL REFERENCES families(id),
    user_id     UUID         NOT NULL REFERENCES users(id),
    role        VARCHAR(20)  NOT NULL DEFAULT 'MEMBER'
                    CHECK (role IN ('ADMIN', 'MEMBER')),
    joined_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    deleted_at  TIMESTAMPTZ,

    UNIQUE (family_id, user_id)
);

CREATE INDEX idx_family_members_user ON family_members(user_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_family_members_family ON family_members(family_id) WHERE deleted_at IS NULL;
