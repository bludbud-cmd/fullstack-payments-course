# PayDeck app

The PayDeck app learners build in the course, written in React Native with TypeScript. One codebase runs on **Android** and on **macOS** (through Microsoft's [react-native-macos](https://github.com/microsoft/react-native-macos)).

This is the app as it stands at the end of **Part 3**: every screen works, using sample data that is saved on the device. Part 7 connects it to the real API.

## Screens

| Screen | What it does | Course module |
| --- | --- | --- |
| Wallet | Balance, number of active cards, recent activity | 10, 12 |
| Top up | Add money, with quick amounts and validation | 13 |
| Cards | All open cards, drawn like real cards | 12 |
| New card | Name a card and give it an optional spending limit | 13 |
| Card settings | Freeze, unfreeze, rename, change the limit, cancel | 13, 14 |
| Activity | Every transaction, newest first, in a fast scrolling list | 13 |

On a phone the screens sit behind a tab bar. In a wide window, such as the Mac app, the tab bar becomes a sidebar.

## How it is put together

```
App.tsx                      Wraps everything in the wallet and the navigator
src/
  money.ts                   Formatting and reading money amounts (always whole cents)
  validation.ts              Form checks, matching the API's rules
  types.ts                   The shapes of accounts, cards and activity, as the API sends them
  data/sampleData.ts         Pretend data used until Part 7
  state/walletReducer.ts     Every change to the wallet, as a pure function
  state/WalletContext.tsx    Shares the wallet with every screen and saves it on the device
  navigation/                A small navigator: a stack of screens plus tabs or a sidebar
  components/                Buttons, fields, the card drawing, transaction rows
  screens/                   One file per screen
```

Navigation is written by hand on purpose: it is short, it teaches how navigation works, and it has no native code, so it behaves the same on Android and macOS.

## Run it

You need a Mac with Node.js 20 or newer, Xcode, Android Studio and CocoaPods. The course's setup pages walk through installing each one.

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
