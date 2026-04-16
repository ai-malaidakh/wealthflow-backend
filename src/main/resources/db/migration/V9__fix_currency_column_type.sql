-- Fix currency columns: CHAR(3) → VARCHAR(3) to match Hibernate entity mapping.
-- PostgreSQL stores CHAR(n) as bpchar which fails schema-validation against varchar(n).
ALTER TABLE accounts     ALTER COLUMN currency TYPE VARCHAR(3);
ALTER TABLE transactions ALTER COLUMN currency TYPE VARCHAR(3);
ALTER TABLE budgets      ALTER COLUMN currency TYPE VARCHAR(3);
