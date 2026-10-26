import Shared
import SwiftUI

/// The iOS app shell. The screens come from Compose Multiplatform in phase 4
/// (docs/platform/ios-port.md); for now it starts the shared data layer and shows that it runs.
@main
struct SpotterApp: App {
    @Environment(\.scenePhase) private var scenePhase
    @State private var exerciseCount: Int?

    var body: some Scene {
        WindowGroup {
            StatusView(exerciseCount: exerciseCount, cloudSync: Spotter.shared.cloudSyncAvailable)
                .task {
                    Spotter.shared.start { count in exerciseCount = count.intValue }
                }
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

private struct StatusView: View {
    let exerciseCount: Int?
    let cloudSync: Bool

    var body: some View {
        VStack(spacing: 12) {
            Text("Spotter").font(.largeTitle.bold())
            if let exerciseCount {
                Text("\(exerciseCount) exercises in the library")
            } else {
                ProgressView()
            }
            Text(cloudSync ? "Cloud sync configured" : "Offline build (no Supabase config)")
                .font(.footnote)
                .foregroundStyle(.secondary)
        }
        .padding()
    }
}
