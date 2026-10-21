# PayDeck app

The PayDeck app learners build in the course, written in React Native with TypeScript. One codebase runs on **Android** and on **macOS** (through Microsoft's [react-native-macos](https://github.com/microsoft/react-native-macos)).

This is the app as it stands at the end of **Part 7**: every screen talks to the PayDeck API in [`../api`](../api). On first launch the app opens an account and remembers its ID on the device. Part 8 replaces that with real sign-in.

## Screens

| Screen | What it does | Course module |
| --- | --- | --- |
| Welcome | Open an account in your currency (first launch only) | 26 |
| Wallet | Balance, number of active cards, recent activity | 10, 12, 26 |
| Top up | Add money, with quick amounts and validation | 13 |
| Cards | All open cards, drawn like real cards | 12 |
| New card | Name a card and give it an optional spending limit | 13 |
| Card settings | Freeze, unfreeze, rename, change the limit, cancel | 13, 14 |
| Activity | Every transaction, newest first, in a fast scrolling list | 13 |

On a phone the screens sit behind a tab bar. In a wide window, such as the Mac app, the tab bar becomes a sidebar.

## How it is put together

```
App.tsx                      Wraps everything in the data cache, the account and the navigator
src/
  config.ts                  Where the API lives
  api/client.ts              One fetch wrapper: JSON, error messages, idempotency keys
  api/paydeck.ts             One function per API endpoint
  api/hooks.ts               TanStack Query hooks the screens use to load and change data
  state/AccountIdContext.tsx Remembers this device's account ID
  money.ts                   Formatting and reading money amounts (always whole cents)
  validation.ts              Form checks, matching the API's rules
  types.ts                   The shapes of accounts, cards and activity, as the API sends them
  navigation/                A small navigator: a stack of screens plus tabs or a sidebar
  components/                Buttons, fields, the card drawing, transaction rows
  screens/                   One file per screen
```

Navigation is written by hand on purpose: it is short, it teaches how navigation works, and it has no native code, so it behaves the same on Android and macOS.

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
