-- A person who can sign in. password_hash is empty for people who only ever
-- sign in with Google; google_subject is Google's ID for them.
CREATE TABLE users (
    id              UUID PRIMARY KEY,
    email           VARCHAR(254) NOT NULL UNIQUE,
    display_name    VARCHAR(100) NOT NULL,
    password_hash   VARCHAR(100),
    google_subject  VARCHAR(255) UNIQUE,
    role            VARCHAR(10)  NOT NULL CHECK (role IN ('CUSTOMER', 'SUPPORT', 'ADMIN')),
    created_at      TIMESTAMPTZ  NOT NULL
);

-- Every customer account now belongs to a user. Accounts opened before login
-- existed (and the SYSTEM accounts) have no owner, so only staff can see them.
ALTER TABLE accounts ADD COLUMN owner_user_id UUID REFERENCES users (id);
CREATE INDEX accounts_owner_user_id ON accounts (owner_user_id);

-- An owner can give a card to someone else (a cardholder). Empty means the
-- owner holds the card themselves.
ALTER TABLE cards ADD COLUMN cardholder_user_id UUID REFERENCES users (id);
CREATE INDEX cards_cardholder_user_id ON cards (cardholder_user_id);

-- Long-lived tokens that get a new access token. We store a hash, never the
-- token itself, so a stolen database backup cannot be used to sign in.
CREATE TABLE refresh_tokens (
    id          UUID PRIMARY KEY,
    user_id     UUID         NOT NULL REFERENCES users (id),
    token_hash  VARCHAR(64)  NOT NULL UNIQUE,
    expires_at  TIMESTAMPTZ  NOT NULL,
    revoked_at  TIMESTAMPTZ,
    created_at  TIMESTAMPTZ  NOT NULL
);
CREATE INDEX refresh_tokens_user_id ON refresh_tokens (user_id);

-- One-time codes handed to the app after "Sign in with Google". The app
-- trades a code for tokens, proving with PKCE that it started the sign-in.
CREATE TABLE login_codes (
    id              UUID PRIMARY KEY,
    user_id         UUID         NOT NULL REFERENCES users (id),
    code_hash       VARCHAR(64)  NOT NULL UNIQUE,
    code_challenge  VARCHAR(128) NOT NULL,
    expires_at      TIMESTAMPTZ  NOT NULL,
    used_at         TIMESTAMPTZ,
    created_at      TIMESTAMPTZ  NOT NULL
);

-- A record of security-relevant actions: who did what, to what, and when.
CREATE TABLE audit_events (
    id             UUID PRIMARY KEY,
    actor_user_id  UUID         REFERENCES users (id),
    action         VARCHAR(40)  NOT NULL,
    target_type    VARCHAR(20),
    target_id      UUID,
    detail         VARCHAR(500),
    created_at     TIMESTAMPTZ  NOT NULL
);
CREATE INDEX audit_events_created_at ON audit_events (created_at DESC);
