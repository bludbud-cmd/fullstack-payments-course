-- An account holds money in one currency. SYSTEM accounts stand for the
-- outside world (for example, a card network paying money in during a top-up),
-- so the ledger always balances.
CREATE TABLE accounts (
    id          UUID PRIMARY KEY,
    owner_name  VARCHAR(100) NOT NULL,
    currency    VARCHAR(3)   NOT NULL,
    kind        VARCHAR(10)  NOT NULL CHECK (kind IN ('CUSTOMER', 'SYSTEM')),
    created_at  TIMESTAMPTZ  NOT NULL
);

-- Exactly one SYSTEM account per currency.
CREATE UNIQUE INDEX accounts_one_system_account_per_currency
    ON accounts (currency) WHERE kind = 'SYSTEM';

INSERT INTO accounts (id, owner_name, currency, kind, created_at) VALUES
    ('00000000-0000-0000-0000-000000000840', 'Outside world (USD)', 'USD', 'SYSTEM', now()),
    ('00000000-0000-0000-0000-000000000978', 'Outside world (EUR)', 'EUR', 'SYSTEM', now()),
    ('00000000-0000-0000-0000-000000000826', 'Outside world (GBP)', 'GBP', 'SYSTEM', now()),
    ('00000000-0000-0000-0000-000000000356', 'Outside world (INR)', 'INR', 'SYSTEM', now());

-- A virtual card spends from one account. We only ever store the last four
-- digits: full card numbers never touch our database.
CREATE TABLE cards (
    id                    UUID PRIMARY KEY,
    account_id            UUID         NOT NULL REFERENCES accounts (id),
    nickname              VARCHAR(50)  NOT NULL,
    last4                 VARCHAR(4)   NOT NULL,
    status                VARCHAR(10)  NOT NULL CHECK (status IN ('ACTIVE', 'FROZEN', 'CANCELLED')),
    spending_limit_cents  BIGINT       CHECK (spending_limit_cents > 0),
    created_at            TIMESTAMPTZ  NOT NULL
);

CREATE INDEX cards_account_id ON cards (account_id);
