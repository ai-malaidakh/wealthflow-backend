CREATE TABLE family_invites (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    family_id   UUID         NOT NULL REFERENCES families(id),
    created_by  UUID         NOT NULL REFERENCES users(id),
    code        VARCHAR(12)  NOT NULL UNIQUE,
    expires_at  TIMESTAMPTZ  NOT NULL,
    used_at     TIMESTAMPTZ,
    used_by     UUID         REFERENCES users(id),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_family_invites_code      ON family_invites(code) WHERE used_at IS NULL;
CREATE INDEX idx_family_invites_family_id ON family_invites(family_id);
