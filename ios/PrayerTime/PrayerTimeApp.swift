import BackgroundTasks
import SwiftUI

@main
struct PrayerTimeApp: App {
    nonisolated static let refreshTaskId = "io.github.rabayabagum.prayertime.refresh"

    @State private var model = AppModel()
    @Environment(\.scenePhase) private var scenePhase

    var body: some Scene {
        WindowGroup {
            TodayView()
                .environment(model)
                .task { await model.requestNotificationsIfNeeded() }
                .onReceive(NotificationCenter.default.publisher(for: .NSSystemTimeZoneDidChange)) { _ in
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
