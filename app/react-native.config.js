// Firebase push notifications are Android-only in PayDeck: the Mac app shows
// the live banner instead (Apple push needs a paid developer account). This
// keeps the Firebase native code out of the macOS build.
module.exports = {
  dependencies: {
    '@react-native-firebase/app': { platforms: { ios: null, macos: null } },
    '@react-native-firebase/messaging': {
      platforms: { ios: null, macos: null },
    },
  },
};
