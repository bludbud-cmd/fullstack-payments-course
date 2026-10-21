import SwiftUI
import React
import React_RCTAppDelegate
import ReactAppDependencyProvider

@main
struct PayDeckApp: App {
  @NSApplicationDelegateAdaptor(AppDelegate.self) var appDelegate

  var body: some Scene {
    Window("PayDeck", id: "main") {
      ReactNativeView(factory: appDelegate.reactNativeFactory)
    }
    .defaultSize(width: 1280, height: 720)
  }
}

// MARK: - App Delegate

class AppDelegate: NSObject, NSApplicationDelegate {
  private let reactNativeDelegate: ReactNativeDelegate
  let reactNativeFactory: RCTReactNativeFactory

  override init() {
    // Swift needs every property set before super.init().
    let delegate = ReactNativeDelegate()
    delegate.dependencyProvider = RCTAppDependencyProvider()
    reactNativeDelegate = delegate
    reactNativeFactory = RCTReactNativeFactory(delegate: delegate)
    super.init()
  }

  // macOS calls this when a paydeck:// link is opened, for example when the
  // browser comes back from "Sign in with Google". Passing it on as this
  // notification makes it arrive in JavaScript through Linking's "url" event.
  func application(_ application: NSApplication, open urls: [URL]) {
    for url in urls {
      NotificationCenter.default.post(
        name: NSNotification.Name("RCTOpenURLNotification"),
        object: self,
        userInfo: ["url": url.absoluteString]
      )
    }
  }
}

// MARK: - React Native Delegate

class ReactNativeDelegate: RCTDefaultReactNativeFactoryDelegate {
  override func sourceURL(for bridge: RCTBridge) -> URL? {
    bundleURL()
  }

  override func bundleURL() -> URL? {
#if DEBUG
    RCTBundleURLProvider.sharedSettings().jsBundleURL(forBundleRoot: "index")
#else
    Bundle.main.url(forResource: "main", withExtension: "jsbundle")
#endif
  }
}

// MARK: - React Native SwiftUI View

struct ReactNativeView: NSViewRepresentable {
  let factory: RCTReactNativeFactory

  func makeNSView(context: Context) -> NSView {
    factory.rootViewFactory.view(withModuleName: "PayDeck")
  }

  func updateNSView(_ nsView: NSView, context: Context) {}
}
