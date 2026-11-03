-- Amounts and balances are BIGINT minor units (cents for EUR): exact, no rounding.
-- The CHECK constraints repeat the business rules, as a last line of defence against bugs.

CREATE TABLE accounts (
    id          UUID         PRIMARY KEY,
    owner_name  VARCHAR(100) NOT NULL,
    currency    CHAR(3)      NOT NULL CHECK (currency ~ '^[A-Z]{3}$'),
    balance     BIGINT       NOT NULL CHECK (balance >= 0),
    created_at  TIMESTAMPTZ  NOT NULL
);

-- The ledger: one row per deposit or transfer, never updated or deleted.
CREATE TABLE transactions (
    id                 UUID         PRIMARY KEY,
    type               VARCHAR(16)  NOT NULL CHECK (type IN ('DEPOSIT', 'TRANSFER')),
    source_account_id  UUID         REFERENCES accounts (id),
    target_account_id  UUID         NOT NULL REFERENCES accounts (id),
    amount             BIGINT       NOT NULL CHECK (amount > 0),
    currency           CHAR(3)      NOT NULL CHECK (currency ~ '^[A-Z]{3}$'),
    -- UNIQUE makes a second request with the same key fail instead of moving money twice.
    -- PostgreSQL allows many NULLs in a UNIQUE column, so requests without a key are not affected.
    idempotency_key    VARCHAR(255) UNIQUE,
    created_at         TIMESTAMPTZ  NOT NULL,
    CONSTRAINT transactions_source_matches_type CHECK (
        (type = 'DEPOSIT' AND source_account_id IS NULL)
        OR (type = 'TRANSFER' AND source_account_id IS NOT NULL AND source_account_id <> target_account_id)
    )
);

-- For account statements (per-account history), the next feature on the list.
CREATE INDEX transactions_source_account_id_idx ON transactions (source_account_id);
CREATE INDEX transactions_target_account_id_idx ON transactions (target_account_id);
