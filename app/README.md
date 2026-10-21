# PayDeck app

The PayDeck app learners build in the course, written in React Native with TypeScript. One codebase runs on **Android** and on **macOS** (through Microsoft's [react-native-macos](https://github.com/microsoft/react-native-macos)).

This is the app as it stands at the end of **Part 9**: people sign up or sign in (with a password or with Google), top up with a real (test mode) card through Stripe, pay pretend shops with their PayDeck cards, and see every change the moment it happens, with a live banner and, on Android, push notifications.

## Screens

| Screen | What it does | Course module |
| --- | --- | --- |
| Sign in | Sign in, create an account, or continue with Google | 28, 29 |
| Open your wallet | Pick a currency, right after signing up | 26, 28 |
| Wallet | Balance, number of active cards, recent activity | 10, 12, 26 |
| Top up | Pay by card on Stripe's page, or add pretend money while building | 13, 31 |
| Cards | Your open cards, and cards other people shared with you | 12, 30 |
| New card | Name a card, give it an optional spending limit, and optionally give it to someone | 13, 30 |
| Card settings | Freeze, unfreeze, rename, change the limit, cancel (a shared card can only be frozen), and recent payments | 13, 14, 30, 32 |
| Pay a shop | A pretend card machine: approved, or declined with the reason | 32 |
| Activity | Every transaction, newest first, in a fast scrolling list | 13 |
| You | Who you are signed in as, notification switches, and Sign out | 28, 34 |

On a phone the screens sit behind a tab bar. In a wide window, such as the Mac app, the tab bar becomes a sidebar.

## How it is put together

```
App.tsx                      Signed out: the sign-in screen. Signed in: the wallet
src/
  config.ts                  Where the API lives
  api/client.ts              One fetch wrapper: tokens, JSON, error messages, idempotency keys
  api/auth.ts                Sign-up, sign-in, refresh, sign-out and "who am I"
  api/paydeck.ts             One function per wallet and card endpoint
  api/payments.ts            Stripe top-ups, card payments, notification settings, devices
  api/hooks.ts               TanStack Query hooks the screens use to load and change data
  auth/AuthContext.tsx       Who is signed in; refreshes tokens; finishes Google sign-in
  auth/tokenStore.ts         Keeps the refresh token in the Keychain or Keystore
  auth/pkce.ts               The PKCE code verifier and challenge for Google sign-in
  state/AccountContext.tsx   Finds the signed-in person's account
  live/stomp.ts              A small STOMP client on top of React Native's WebSocket
  live/LiveUpdates.tsx       Listens for changes, refreshes the screens, shows the banner
  push/push.ts               Android push notifications through Firebase
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

## How payments and live updates work

- **Top up by card.** The app asks the API for a Stripe Checkout page and opens it in the browser. After paying (in test mode, use the card `4242 4242 4242 4242`), Stripe sends the browser to the API, which hands over to the app with `paydeck://top-up?result=success`. The money itself only arrives when Stripe's signed webhook reaches the API, a few seconds later. "Add pretend money" stays available while you build, until the API turns `paydeck.simulated-top-ups` off.
- **Pay a shop.** Real card payments come from a card network. PayDeck has none, so the Pay screen plays the shop's card machine and calls `POST /api/cards/{id}/payments`. The API checks the card's rules in order (frozen, cancelled, over the limit, not enough money) and answers approved or declined.
- **Live updates.** While someone is signed in, the app keeps a WebSocket open to `ws://…/ws` and speaks STOMP over it (`src/live/stomp.ts` is short enough to read in one sitting). It signs in with the access token and subscribes to the account's topic and the person's own topic. Every message refreshes the data on screen and shows a banner for five seconds. If the connection drops, it reconnects, waiting a little longer each time.
- **Push notifications (Android).** With Firebase set up, the app asks for permission (Android 13 and newer), sends its Firebase token to `POST /api/devices`, and removes it again on sign-out. The API then pushes "Money in" or "Card declined" even when the app is closed. Push needs your own Firebase project: create one at [console.firebase.google.com](https://console.firebase.google.com), add an Android app with the package name `com.paydeck`, and save its `google-services.json` in `android/app/` (it is in `.gitignore`, because every learner has their own). Without the file the app builds and runs normally, with push turned off. The Mac app relies on the live banner, because Apple push needs a paid Apple Developer account.

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

## Release it

Part 10 (Module 36) turns the app into something other people can install.

1. **Point it at your API.** Set `PRODUCTION_API_URL` in `src/config.ts` to your deployed API's `https://` address. Debug builds keep using your Mac; release builds use this address.
2. **Android.** Make an upload key once, and keep it safe (Google Play needs the same key for every update):

   ```sh
   keytool -genkeypair -v -storetype PKCS12 -keystore paydeck-upload.keystore \
     -alias paydeck -keyalg RSA -keysize 2048 -validity 10000
   ```

   Then tell Gradle where it is, in `~/.gradle/gradle.properties` (outside the project, so it never reaches git):

   ```properties
   PAYDECK_UPLOAD_STORE_FILE=/Users/you/keys/paydeck-upload.keystore
   PAYDECK_UPLOAD_STORE_PASSWORD=...
   PAYDECK_UPLOAD_KEY_ALIAS=paydeck
   PAYDECK_UPLOAD_KEY_PASSWORD=...
   ```

   `cd android && ./gradlew bundleRelease` makes the `.aab` file for Google Play, and `./gradlew assembleRelease` makes an `.apk` you can install directly. Without an upload key, release builds are signed with the debug key, which is fine for your own phone.
3. **macOS.** In Xcode, open `macos/PayDeck.xcworkspace`, choose **Product → Archive**, then **Distribute App → Custom → Copy App**. Without a paid Apple Developer account the app is unsigned, so people open it the first time with right-click → **Open**. Signing and notarizing, so it opens with a double-click, is an optional bonus lesson.
4. **Let CI do it.** Pushing a tag such as `app-v1.0.0` runs `.github/workflows/release-app.yml`, which tests and builds both, and attaches the `.apk`, `.aab` and zipped Mac app to the run. To sign Android builds there, add the repository secrets `ANDROID_UPLOAD_KEYSTORE_BASE64` (from `base64 -i paydeck-upload.keystore`), `ANDROID_UPLOAD_STORE_PASSWORD`, `ANDROID_UPLOAD_KEY_ALIAS` and `ANDROID_UPLOAD_KEY_PASSWORD`.

## Check it

```sh
npm run lint
npm run typecheck
npm test
```

The tests replace `fetch` with a small in-memory copy of the API (`testing/fakeApi.ts`), and `WebSocket` and Firebase with fakes (`jest.setup.js`), so they run without a server or a phone.
