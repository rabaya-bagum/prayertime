import BackgroundTasks
import SwiftUI

@main
struct PrayerTimeApp: App {
    nonisolated static let refreshTaskId = "io.github.rabayabagum.prayertime.refresh"

    @State private var model = AppModel()
    @Environment(\.scenePhase) private var scenePhase

    var body: some Scene {
        WindowGroup {
            TabView {
                TodayView()
                    .tabItem { Label("Times", systemImage: "clock") }
                QiblaView()
                    .tabItem { Label("Qibla", systemImage: "location.north.circle") }
            }
            // NSAccentColorName alone doesn't reach the tab bar or Color.accentColor on iOS 26.
            .tint(Color("AccentColor"))
            .environment(model)
            .task { await model.requestNotificationsIfNeeded() }
            .onReceive(NotificationCenter.default.publisher(for: .NSSystemTimeZoneDidChange)) { _ in
                Task { await model.refreshAlerts() }
            }
            // Midnight, a manual clock change or a DST switch while the app is running.
            .onReceive(NotificationCenter.default.publisher(for: UIApplication.significantTimeChangeNotification)) { _ in
                Task { await model.refreshAlerts() }
            }
        }
        .onChange(of: scenePhase) { _, phase in
            switch phase {
            case .active: Task { await model.refreshAlerts() }
            case .background: Self.scheduleBackgroundRefresh()
            default: break
            }
        }
        .backgroundTask(.appRefresh(Self.refreshTaskId)) {
            await model.refreshAlerts()
            Self.scheduleBackgroundRefresh()
        }
    }

    /// Best effort: iOS decides when (or whether) this runs, which is why the last
    /// pending notification also asks the user to open the app.
    nonisolated static func scheduleBackgroundRefresh() {
        let request = BGAppRefreshTaskRequest(identifier: refreshTaskId)
        request.earliestBeginDate = Date().addingTimeInterval(12 * 3600)
        try? BGTaskScheduler.shared.submit(request)
    }
}
