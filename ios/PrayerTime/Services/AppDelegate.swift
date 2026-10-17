import PrayerCore
import PrayerNotifications
import UIKit
import UserNotifications

/// Becomes the notification delegate at launch, so an adhan opened from a cold
/// start still reaches the app.
final class AppDelegate: NSObject, UIApplicationDelegate, UNUserNotificationCenterDelegate {
    func application(_ application: UIApplication,
                     didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil) -> Bool {
        UNUserNotificationCenter.current().delegate = self
        return true
    }

    /// In the foreground, an adhan plays in full instead of its 30-second clip.
    nonisolated func userNotificationCenter(_ center: UNUserNotificationCenter,
                                            willPresent notification: UNNotification) async -> UNNotificationPresentationOptions {
        let (kind, sound) = Self.details(notification)
        guard kind == AlertKind.adhan.rawValue else { return [.banner, .list, .sound] }
        await AdhanPlayer.shared.play(AdhanSound.named(sound))
        return [.banner, .list]
    }

    nonisolated func userNotificationCenter(_ center: UNUserNotificationCenter,
                                            didReceive response: UNNotificationResponse) async {
        guard response.actionIdentifier == UNNotificationDefaultActionIdentifier else { return }
        let (kind, sound) = Self.details(response.notification)
        guard AdhanSound.shouldPlayFull(kind: kind, firedAt: response.notification.date) else { return }
        await AdhanPlayer.shared.play(AdhanSound.named(sound))
    }

    private nonisolated static func details(_ notification: UNNotification) -> (kind: String, sound: String) {
        let info = notification.request.content.userInfo
        return (info[NotificationScheduler.UserInfoKey.kind] as? String ?? "",
                info[NotificationScheduler.UserInfoKey.sound] as? String ?? "")
    }
}
