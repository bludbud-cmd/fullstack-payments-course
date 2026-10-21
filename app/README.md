# PayDeck app

The PayDeck app learners build in the course, written in React Native with TypeScript. One codebase runs on **Android** and on **macOS** (through Microsoft's [react-native-macos](https://github.com/microsoft/react-native-macos)).

This is the app as it stands at the end of **Part 8**: people sign up or sign in (with a password or with Google), and every screen talks to the PayDeck API in [`../api`](../api) on their behalf.

## Screens

| Screen | What it does | Course module |
| --- | --- | --- |
| Sign in | Sign in, create an account, or continue with Google | 28, 29 |
| Open your wallet | Pick a currency, right after signing up | 26, 28 |
| Wallet | Balance, number of active cards, recent activity | 10, 12, 26 |
| Top up | Add money, with quick amounts and validation | 13 |
| Cards | Your open cards, and cards other people shared with you | 12, 30 |
| New card | Name a card, give it an optional spending limit, and optionally give it to someone | 13, 30 |
| Card settings | Freeze, unfreeze, rename, change the limit, cancel (a shared card can only be frozen) | 13, 14, 30 |
| Activity | Every transaction, newest first, in a fast scrolling list | 13 |
| You | Who you are signed in as, and Sign out | 28 |

On a phone the screens sit behind a tab bar. In a wide window, such as the Mac app, the tab bar becomes a sidebar.

## How it is put together

```
App.tsx                      Signed out: the sign-in screen. Signed in: the wallet
src/
  config.ts                  Where the API lives
  api/client.ts              One fetch wrapper: tokens, JSON, error messages, idempotency keys
  api/auth.ts                Sign-up, sign-in, refresh, sign-out and "who am I"
  api/paydeck.ts             One function per wallet and card endpoint
  api/hooks.ts               TanStack Query hooks the screens use to load and change data
  auth/AuthContext.tsx       Who is signed in; refreshes tokens; finishes Google sign-in
  auth/tokenStore.ts         Keeps the refresh token in the Keychain or Keystore
  auth/pkce.ts               The PKCE code verifier and challenge for Google sign-in
  state/AccountContext.tsx   Finds the signed-in person's account
  money.ts                   Formatting and reading money amounts (always whole cents)
  validation.ts              Form checks, matching the API's rules
  types.ts                   The shapes of accounts, cards and activity, as the API sends them
  navigation/                A small navigator: a stack of screens plus tabs or a sidebar
  components/                Buttons, fields, the card drawing, transaction rows
  screens/                   One file per screen
```

Navigation is written by hand on purpose: it is short, it teaches how navigation works, and it has no native code, so it behaves the same on Android and macOS.

## How sign-in works

- After signing in, the app holds two tokens. The **access token** lasts 15 minutes and is kept only in memory. The **refresh token** lasts 30 days and is kept in the macOS Keychain or the Android Keystore (through [react-native-keychain](https://github.com/oblador/react-native-keychain)), never in plain storage.
- Every request carries `Authorization: Bearer <access token>`. When the API answers `401`, the client swaps the refresh token for a new pair and tries the request once more. If several requests fail at once they share one refresh, because each refresh token works only once.
- On the next launch the app uses the saved refresh token to sign straight back in.
- **Continue with Google** makes a random code verifier, opens the API's `/api/auth/google` link in the browser with its SHA-256 (the code challenge), and waits. After Google, the API opens `paydeck://auth?code=...`; the app trades that code plus the verifier for tokens. The `paydeck://` link is registered in `android/app/src/main/AndroidManifest.xml` and `macos/PayDeck-macOS/Info.plist`. Google sign-in only works once the API has a Google client configured (see the API README).

Top-ups send an `Idempotency-Key` header. The key stays the same until the amount changes, so tapping "Add" again after a dropped connection can never add the money twice.

## Run it

You need a Mac with Node.js 20 or newer, Xcode, Android Studio and CocoaPods. The course's setup pages walk through installing each one.

Start the API first (see [`../api/README.md`](../api/README.md)); it listens on port 8080.

```sh
cd api
docker compose up -d   # PostgreSQL
./mvnw spring-boot:run
```

The app finds the API at `http://localhost:8080` on macOS and at `http://10.0.2.2:8080` on the Android emulator, because inside the emulator `10.0.2.2` means "the Mac running me". Change `src/config.ts` to use a real phone or a deployed API.

Then, in another terminal:

```sh
cd app
npm install

# Android: start an emulator in Android Studio first
npm run android

# macOS
cd macos && pod install && cd ..
npm run macos
```

## Check it

```sh
npm run lint
npm run typecheck
npm test
```

The tests replace `fetch` with a small in-memory copy of the API (`testing/fakeApi.ts`), so they run without a server.
