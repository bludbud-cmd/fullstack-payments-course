# PayDeck API

The backend for PayDeck, built in Part 6 of the course. It is a Spring Boot 4 app written in Java 25, with a PostgreSQL database.

## What it does so far

| Feature | Endpoints | Course module |
| --- | --- | --- |
| Open an account and see its balance | `POST /api/accounts`, `GET /api/accounts/{id}` | 22, 23 |
| Issue, rename, limit, freeze, unfreeze and cancel virtual cards | `POST /api/accounts/{id}/cards`, `GET /api/accounts/{id}/cards`, `GET`/`PATCH`/`DELETE /api/cards/{id}`, `POST /api/cards/{id}/freeze`, `POST /api/cards/{id}/unfreeze` | 22, 23 |
| Top up an account (simulated until Stripe arrives in Module 31) | `POST /api/accounts/{id}/top-ups` | 24 |
| Transfer money between accounts | `POST /api/transfers` | 24 |
| See an account's transactions, newest first | `GET /api/accounts/{id}/transactions` | 24 |

Interactive API docs are at `/docs` while the app runs. Login and permissions arrive in Part 8, so for now every endpoint is open. Only run it on your own computer.

## How money stays correct

- **Whole numbers only.** Amounts are stored in the currency's smallest unit (`amountCents`: 1050 means 10.50), never as decimals, so there are no rounding errors.
- **A double-entry ledger.** Every movement of money is a ledger transaction whose entries add up to zero. Balances are never stored; they are the sum of an account's entries. Money cannot appear or vanish.
- **Locks.** A transfer locks both accounts (`SELECT ... FOR UPDATE`) in a fixed order before checking the balance, so two payments at the same moment cannot both spend the same money, and they never deadlock.
- **Idempotency keys.** Top-ups and transfers need an `Idempotency-Key` header. Sending the same request again with the same key returns the original result (`200` and `Idempotent-Replayed: true`) instead of moving money twice. Reusing a key for a different request gets a `409`.

## Run it

You need JDK 25 and Docker Desktop.

```sh
cd api
docker compose up -d      # start PostgreSQL
./mvnw spring-boot:run    # start the API on http://localhost:8080
```

Try it:

```sh
curl -s -X POST localhost:8080/api/accounts \
  -H 'Content-Type: application/json' \
  -d '{"ownerName": "Asha", "currency": "USD"}'
```

## Test it

```sh
./mvnw verify
```

The tests start their own PostgreSQL in Docker with Testcontainers, so Docker must be running. They include a test that fires 20 payments at one account at the same moment and checks it is never overspent.
