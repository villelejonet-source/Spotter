import Shared
import SwiftUI
import UIKit

/// The iOS app shell around the shared Compose UI (docs/platform/ios-port.md): it starts the
/// shared code, shows its screens full-window, and passes on opened links and foreground events.
@main
struct SpotterApp: App {
    @Environment(\.scenePhase) private var scenePhase

    var body: some Scene {
        WindowGroup {
            // The shared screens lay themselves out around the status bar, home indicator and
            // keyboard, as on Android.
            ComposeView()
                .ignoresSafeArea()
                .task { Spotter.shared.start() }
                .onOpenURL { url in Spotter.shared.openUrl(url: url.absoluteString) }
        }
        .onChange(of: scenePhase) { _, phase in
            if phase == .active { Spotter.shared.onForeground() }
        }
    }
}

/// One shared-code entry point per process, configured from Info.plist (see Config/App.xcconfig).
enum Spotter {
    static let shared: SpotterIos = {
        let info = Bundle.main.infoDictionary ?? [:]
        return SpotterIos(
            supabaseUrl: info["SupabaseURL"] as? String ?? "",
            supabasePublishableKey: info["SupabasePublishableKey"] as? String ?? "",
            versionCode: Int32(info["CFBundleVersion"] as? String ?? "") ?? 0
        )
    }()
}

/// The shared Compose UI as a SwiftUI view.
private struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        Spotter.shared.mainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
