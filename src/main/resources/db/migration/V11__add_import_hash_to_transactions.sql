ALTER TABLE transactions ADD COLUMN import_hash CHAR(64);

CREATE INDEX idx_transactions_import_hash ON transactions(import_hash) WHERE import_hash IS NOT NULL;
