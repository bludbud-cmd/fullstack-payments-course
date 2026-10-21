-- Every movement of money is one ledger transaction made of two or more
-- entries whose amounts add up to zero (double-entry bookkeeping).
-- A balance is never stored: it is the sum of an account's entries.
CREATE TABLE ledger_transactions (
    id                   UUID PRIMARY KEY,
    type                 VARCHAR(20)  NOT NULL CHECK (type IN ('TOP_UP', 'TRANSFER')),
    description          VARCHAR(140) NOT NULL,
    -- The same Idempotency-Key always means the same request, so a retry
    -- can never move money twice.
    idempotency_key      VARCHAR(100) NOT NULL UNIQUE,
    request_fingerprint  VARCHAR(64)  NOT NULL,
    created_at           TIMESTAMPTZ  NOT NULL
);

CREATE TABLE ledger_entries (
    id              UUID PRIMARY KEY,
    transaction_id  UUID    NOT NULL REFERENCES ledger_transactions (id),
    account_id      UUID    NOT NULL REFERENCES accounts (id),
    amount_cents    BIGINT  NOT NULL CHECK (amount_cents <> 0)
);

CREATE INDEX ledger_entries_account_id ON ledger_entries (account_id);
CREATE INDEX ledger_entries_transaction_id ON ledger_entries (transaction_id);
