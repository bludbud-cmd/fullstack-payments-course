-- Two new kinds of ledger transaction: paying with a card, and a refund.
ALTER TABLE ledger_transactions DROP CONSTRAINT ledger_transactions_type_check;
ALTER TABLE ledger_transactions ADD CONSTRAINT ledger_transactions_type_check
    CHECK (type IN ('TOP_UP', 'TRANSFER', 'CARD_PAYMENT', 'REFUND'));

-- Every attempt to pay with a PayDeck card, approved or declined. A decline
-- moves no money, but we still keep it: people want to know why.
CREATE TABLE card_payments (
    id                      UUID PRIMARY KEY,
    card_id                 UUID         NOT NULL REFERENCES cards (id),
    account_id              UUID         NOT NULL REFERENCES accounts (id),
    merchant                VARCHAR(100) NOT NULL,
    amount_cents            BIGINT       NOT NULL CHECK (amount_cents > 0),
    status                  VARCHAR(10)  NOT NULL CHECK (status IN ('APPROVED', 'DECLINED', 'REFUNDED')),
    decline_reason          VARCHAR(30),
    transaction_id          UUID         REFERENCES ledger_transactions (id),
    refund_transaction_id   UUID         REFERENCES ledger_transactions (id),
    idempotency_key         VARCHAR(100) NOT NULL UNIQUE,
    request_fingerprint     VARCHAR(64)  NOT NULL,
    created_at              TIMESTAMPTZ  NOT NULL
);
CREATE INDEX card_payments_card_id ON card_payments (card_id, created_at DESC);

-- A Stripe Checkout page we opened for a top-up. The wallet is only credited
-- when Stripe's webhook says the payment succeeded.
CREATE TABLE stripe_checkouts (
    id            VARCHAR(255) PRIMARY KEY, -- Stripe's Checkout Session ID, cs_test_...
    account_id    UUID         NOT NULL REFERENCES accounts (id),
    amount_cents  BIGINT       NOT NULL CHECK (amount_cents > 0),
    currency      VARCHAR(3)   NOT NULL,
    status        VARCHAR(10)  NOT NULL CHECK (status IN ('OPEN', 'PAID', 'EXPIRED')),
    created_at    TIMESTAMPTZ  NOT NULL,
    completed_at  TIMESTAMPTZ
);

-- Phones that want push notifications. The token comes from Firebase.
CREATE TABLE devices (
    id          UUID PRIMARY KEY,
    user_id     UUID          NOT NULL REFERENCES users (id),
    platform    VARCHAR(10)   NOT NULL CHECK (platform IN ('ANDROID')),
    token       VARCHAR(4096) NOT NULL UNIQUE,
    created_at  TIMESTAMPTZ   NOT NULL
);
CREATE INDEX devices_user_id ON devices (user_id);

-- Which notifications each person wants.
ALTER TABLE users ADD COLUMN notify_payments BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE users ADD COLUMN notify_declines BOOLEAN NOT NULL DEFAULT TRUE;
