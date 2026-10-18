# Adhan scheduling

How adhans and reminders reach the user on each platform, and what each app
must wire up. All decisions (what fires, when, with which sound) are made by
pure planners in the cores and unit-tested on both platforms. The platform
code only applies the plan.

## Shared model (`core`)

| Type | Purpose |
|---|---|
| `NotificationPreferences` | Per prayer: `ADHAN`, `SILENT` or `OFF`; sound id; optional reminder 1–120 min before. Defaults: adhan for the five prayers, sunrise off. |
| `ScheduleInputs` | Place + calculation settings + notification preferences: everything a scheduler needs. |
| `AlertPlanner.alerts(inputs, now, until)` | Every alert in a time range, in order. |
| `PlannedAlert.identifier` | `kind\|date\|prayer\|epochSeconds\|sound`, identical on both platforms. A changed setting produces a changed identifier, so plans diff by identifier alone. |

## iOS: 64-slot rolling window

`NotificationWindowPlanner.plan` fills 63 slots in time order. The 64th is a
**keep-alive** notification 24 hours before the window ends: "Adhans are
scheduled until Fri, May 29. Open the app…". With five adhans a day the window
covers about 12.6 days, or about 6.3 days if every prayer also has a reminder.

`NotificationScheduler.refresh(inputs)` (target `PrayerNotifications`)
diffs the plan against pending requests and only adds or removes what changed.
Triggers are absolute (UTC components with the zone attached), so travel and
DST can't shift them.

The app (`ios/PrayerTime`, via `AppModel.refreshAlerts`) must:

1. Call `refresh` on launch, after any settings or place change, in a
   `BGAppRefreshTask` handler, and on `UIApplication.significantTimeChangeNotification`
   and `.NSSystemTimeZoneDidChange`. All wired in `PrayerTimeApp`.
2. Bundle one `<sound>_clip.caf` per reciter (takbir section, **under 30 s**,
   linear PCM or IMA4). Longer files silently fall back to the default sound.
   Done: `tools/make_adhan_audio.sh` builds them, `AdhanSoundTests` checks the
   length, and `docs/AUDIO_LICENSES.md` records each source.
3. Handle taps in `userNotificationCenter(_:didReceive:)`: read
   `UserInfoKey.sound` and play the full adhan in the foreground. Done in
   `AppDelegate`: opening an adhan within 10 minutes plays it in full, and an
   adhan arriving while the app is open plays in full instead of the clip.
   Fajr plays the reciter's Fajr recording (with "As-salatu khayrun min
   an-nawm"), or only the takbir clip if there is none, never the regular one.
4. Add the Time Sensitive Notifications capability so adhans can break
   through Focus (`interruptionLevel = .timeSensitive`). Done:
   `PrayerTime.entitlements`. Enable the capability for the App ID in the
   developer portal when signing for devices.
5. Ask for notification permission during onboarding, after the user has
   picked a method. Adhans aren't critical alerts, so don't request that.

Not yet built: an opt-in AlarmKit "loud adhan" mode (iOS 26+), pending a
check of App Review acceptance and sound-length limits (`docs/SPEC.md`, open
items).

## Android: 48-hour exact-alarm window

`AlarmWindowPlanner.plan` returns every alert in the next 48 hours (about 10
alarms with default settings). `PrayerScheduler.sync(context)` (module
`:notifications`) cancels alarms no longer wanted and re-arms the rest.
AlarmManager can't list pending alarms, so the armed request codes are kept in
SharedPreferences.

| Alert | API | When it fires |
|---|---|---|
| Adhan | `setAlarmClock` (never deferred by Doze) | Starts `AdhanPlaybackService` (mediaPlayback foreground service) to play the full recording. Exact alarms exempt this start from background limits. |
| Silent | `setExactAndAllowWhileIdle` | Notification on the silent channel. |
| Reminder | `setExactAndAllowWhileIdle` | "Asr at 3:42 PM" on the reminder channel. |
| Any, without exact-alarm permission | `setWindow` (5 min) | Adhans post a notification on the reciter's clip channel instead of starting the service. |

Other behaviour:
- Alerts delivered more than 10 minutes late (device off, deep sleep) never play
  audio. They post "Asr began at 3:42 PM" instead.
- The service respects the ringer. On silent or vibrate it posts a silent
  notification instead of playing. It stops on completion, on the Stop action,
  on audio-focus loss, or after 6 minutes.
- `RescheduleReceiver` re-syncs after boot, app update, clock or time-zone
  changes, and exact-alarm permission grants. It never starts audio, because
  apps targeting Android 15+ can't start a mediaPlayback service from
  BOOT_COMPLETED.
- Every alarm firing also re-syncs, so the window keeps rolling. Losing one
  re-arm doesn't silence the following prayers.

The app must:

1. Have its `Application` implement `PrayerScheduleSourceProvider`. Return
   current inputs from storage that is safe to read on a background thread,
   plus URIs for each reciter's full adhan and clip (e.g. `res/raw`).
   `adhanUri` gets the prayer: Fajr needs a Fajr recording, or the clip.
   `BundledAdhans` does both for the bundled files.
2. Call `PrayerScheduler.sync(context)` on launch and after any settings or
   place change.
3. In onboarding, request `POST_NOTIFICATIONS`, then send the user to
   `exactAlarmSettingsIntent` if `canScheduleExactAlarms` is false (re-check in
   `onResume`), then to `batteryOptimizationSettingsIntent`, with Xiaomi and
   Samsung instructions from dontkillmyapp.com.
4. Not request `USE_EXACT_ALARM`. Play reserves it for alarm-clock and calendar apps.

## Testing

| Layer | Where | Runs |
|---|---|---|
| Planners | `android/core` `NotificationsTest`, `ios/PrayerCore` `NotificationsTests` | Everywhere |
| iOS adapter | `PrayerNotificationsTests` (fake notification center) | macOS CI |
| Android adapter | Compile + lint in CI | Linux CI with SDK |
| On device | Launch checklist in `docs/SPEC.md`: Doze `force-idle`, reboot, exact-alarm deny path, Xiaomi/Samsung | Manual |

Android device checks:

```sh
adb shell dumpsys alarm | grep -A3 io.github.rabayabagum       # armed alarms
adb shell dumpsys deviceidle force-idle                          # then wait for the next prayer
adb shell cmd appops set <package> SCHEDULE_EXACT_ALARM deny     # exercise the inexact fallback
```
