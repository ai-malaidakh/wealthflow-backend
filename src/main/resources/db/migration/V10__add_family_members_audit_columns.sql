ALTER TABLE family_members ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ;

CREATE OR REPLACE FUNCTION set_updated_at_family_member()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS set_updated_at_family_member ON family_members;
CREATE TRIGGER set_updated_at_family_member
BEFORE UPDATE ON family_members
FOR EACH ROW
EXECUTE FUNCTION set_updated_at_family_member();
