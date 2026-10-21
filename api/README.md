# PayDeck API

The backend for PayDeck, built in Part 6 of the course, secured in Part 8, and connected to Stripe, WebSockets and push notifications in Part 9. It is a Spring Boot 4 app written in Java 25, with a PostgreSQL database.

## What it does so far

| Feature | Endpoints | Course module |
| --- | --- | --- |
| Sign up, sign in, refresh and sign out | `POST /api/auth/register`, `/login`, `/refresh`, `/logout` | 27, 28 |
| Sign in with Google (through the API, with PKCE) | `GET /api/auth/google`, `POST /api/auth/token` | 29 |
| Who am I, and what do I own? | `GET /api/me` | 27 |
| Open an account and see its balance | `POST /api/accounts`, `GET /api/accounts/{id}` | 22, 23 |
| Issue, rename, limit, freeze, unfreeze and cancel virtual cards, and give a card to someone else | `POST /api/accounts/{id}/cards`, `GET /api/accounts/{id}/cards`, `GET`/`PATCH`/`DELETE /api/cards/{id}`, `POST /api/cards/{id}/freeze`, `POST /api/cards/{id}/unfreeze` | 22, 23, 30 |
| Top up an account (simulated until Stripe arrives in Module 31) | `POST /api/accounts/{id}/top-ups` | 24 |
| Transfer money between accounts | `POST /api/transfers` | 24 |
| See an account's transactions, newest first | `GET /api/accounts/{id}/transactions` | 24 |
| Top up with a card through Stripe Checkout (test mode) | `POST /api/accounts/{id}/top-ups/checkout`, `GET /payments/return`, `POST /api/stripe/webhook` | 31 |
| Pay a shop with a card: approved or declined, with a reason | `POST /api/cards/{id}/payments`, `GET /api/cards/{id}/payments` | 32 |
| Refund a card payment (staff) | `POST /api/admin/card-payments/{id}/refund` | 32 |
| Live updates over WebSockets (STOMP) | `ws://…/ws`, topics `/topic/accounts/{id}` and `/topic/users/{id}` | 33 |
| Push notifications to Android phones, and notification settings | `POST /api/devices`, `POST /api/devices/unregister`, `GET`/`PUT /api/me/notifications` | 34 |
| Staff tools: find users, change roles, read the audit log | `GET /api/admin/users`, `GET /api/admin/users/{id}`, `PUT /api/admin/users/{id}/role`, `GET /api/admin/audit` | 30 |

Interactive API docs are at `/docs` while the app runs. Sign in, copy the `accessToken`, and paste it into the **Authorize** button to try the protected endpoints.

## How sign-in works

- **Passwords** are hashed with BCrypt and never stored or logged. A wrong password and an unknown email get the same answer, so nobody can use sign-in to find out who has an account.
- **Access tokens** are JWTs signed with `JWT_SECRET`. They last 15 minutes and carry the user's ID and role. Send one as `Authorization: Bearer <token>`.
- **Refresh tokens** are random strings that last 30 days and work once: `/api/auth/refresh` swaps one for a new pair. If a used one ever comes back, someone may have copied it, so every session of that user is signed out. Only a hash of each token is stored.
- **Sign in with Google** happens on the server. The app opens `/api/auth/google` in the browser with a PKCE code challenge; after Google, the API sends the browser to `paydeck://auth?code=...`, and the app trades that one-time code plus its code verifier for tokens at `/api/auth/token`. A Google account with the same verified email as an existing user is linked to it.

## How payments work

- **Card top-ups (Stripe).** The app asks for a Checkout page and opens it in the browser. The wallet is credited only when Stripe's webhook says the payment succeeded, never because the app says so. Every webhook's `Stripe-Signature` is checked (HMAC-SHA256 with the webhook secret, and no older than 5 minutes), and the Checkout Session ID is the ledger's idempotency key, so Stripe's retries never add money twice. After paying, `/payments/return` sends the browser back to `paydeck://top-up`.
- **Paying with a card.** Each payment is approved or declined in this order: frozen, cancelled, over the card's limit, not enough money. Declines move no money but are kept, with a reason the cardholder can act on. Payments need an `Idempotency-Key`. Only staff can refund, which is how disputes are settled.
- **The simulated top-up** from Part 6 still works for development. Set `PAYDECK_SIMULATED_TOP_UPS=false` once Stripe is live.

To try Stripe locally, install the [Stripe CLI](https://docs.stripe.com/stripe-cli) and forward webhooks to the API:

```sh
stripe listen --forward-to localhost:8080/api/stripe/webhook   # prints the whsec_… secret
STRIPE_SECRET_KEY=sk_test_… STRIPE_WEBHOOK_SECRET=whsec_… ./mvnw spring-boot:run
```

Pay with Stripe's test card `4242 4242 4242 4242`, any future date and any CVC.

## Live updates and notifications

- The app connects to `ws://localhost:8080/ws` with STOMP, sending `Authorization: Bearer <access token>` in the CONNECT frame. It may subscribe to `/topic/accounts/{id}` for accounts it can see, and to `/topic/users/{its own id}` for cards shared with it. Anything else is refused.
- Every money movement, card payment, decline, refund, freeze and unfreeze is sent out **after** its database transaction commits, so nobody hears about money that then failed to move.
- The same events become push notifications on the owner's and cardholder's Android phones, through Firebase Cloud Messaging's HTTP v1 API, unless they turned that kind off. Tokens Firebase reports as gone are deleted. Without `FIREBASE_SERVICE_ACCOUNT_FILE`, notifications are only written to the log.

## Who may do what

Every account and card check goes through `AccessPolicy`:

| | Owner | Cardholder | Support | Admin |
| --- | --- | --- | --- | --- |
| See an account, its activity and cards | yes | | yes | yes |
| Top up, transfer, issue cards | yes | | | |
| See a card | yes | yes | yes | yes |
| Pay with a card | yes | yes | | |
| Freeze a card | yes | yes | yes | yes |
| Unfreeze a card | yes | | yes | yes |
| Rename a card or change its limit | yes | | | |
| Cancel a card | yes | | | yes |

Someone who cannot see a thing gets `404`, so they do not learn that it exists; someone who can see it but not change it gets `403`. Staff never move customers' money. Whenever staff look at or change something that is not theirs, it is written to the audit log.

New users are customers. To make the first admin, put their email in `PAYDECK_ADMIN_EMAILS` before they sign up; admins can then give others the support or admin role.

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
# Sign up, and keep the access token
TOKEN=$(curl -s -X POST localhost:8080/api/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"email": "asha@example.com", "password": "a long password", "displayName": "Asha"}' \
  | jq -r .accessToken)

# Open an account with it
curl -s -X POST localhost:8080/api/accounts \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"currency": "USD"}'
```

### Settings

| Environment variable | What it is | Default |
| --- | --- | --- |
| `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD` | The PostgreSQL database | The one in `docker-compose.yml` |
| `JWT_SECRET` | Signs access tokens. At least 32 characters; use a long random one anywhere but your own Mac | A development-only value |
| `PAYDECK_ADMIN_EMAILS` | Comma-separated emails that become admins when they sign up | None |
| `SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_GOOGLE_CLIENT_ID`, `..._CLIENT_SECRET` | A Google OAuth client (type "Web application", redirect URI `http://localhost:8080/login/oauth2/code/google`). Leave unset to switch Google sign-in off | Unset |
| `PAYDECK_APP_REDIRECT_URIS` | Where Google sign-in may send people back to | `paydeck://auth` |
| `STRIPE_SECRET_KEY`, `STRIPE_WEBHOOK_SECRET` | Stripe test-mode keys. Leave unset to switch card top-ups off | Unset |
| `PAYDECK_PUBLIC_BASE_URL` | Where browsers can reach this API, for Stripe's return link | `http://localhost:8080` |
| `PAYDECK_APP_TOP_UP_RETURN_URL` | The app link people go back to after paying | `paydeck://top-up` |
| `PAYDECK_SIMULATED_TOP_UPS` | Allow the pretend top-up from Part 6 | `true` |
| `FIREBASE_SERVICE_ACCOUNT_FILE` | A Firebase service account key, to send real push notifications | Unset (log only) |

## Test it

```sh
./mvnw verify
```

The tests start their own PostgreSQL in Docker with Testcontainers, so Docker must be running. They include a test that fires 20 payments at one account at the same moment and checks it is never overspent.
