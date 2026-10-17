# Prayer Time

A free prayer-time app for iOS and Android. Its goals: times that match your
local mosque, an adhan that fires every time, and nothing leaving your phone.
No ads, no accounts, no analytics, no paywall.

**Status:** pre-alpha. The calculation core and adhan schedulers for both
platforms are in place and tested. The iOS app has a Today screen, offline
city search, approximate GPS, calculation settings, adhan notifications (system
sound until reciter clips are bundled) and a Qibla compass. The Android app UI
isn't started.

## Repository layout

| Path | What it is |
|---|---|
| `docs/SPEC.md` | Product spec, with revisions from building the core |
| `docs/adr/` | Architecture decisions |
| `docs/NOTIFICATIONS.md` | How adhans are scheduled on each platform, and what the apps must wire up |
| `android/core` | Kotlin calculation core and alert planners (plain JVM, no Android dependencies) |
| `android/notifications` | Android alarms, receivers, channels and the adhan playback service |
| `ios/PrayerCore` | Swift package: `PrayerCore` (same API as the Kotlin core) and `PrayerNotifications` |
| `ios/PrayerTime.xcodeproj` | SwiftUI app (iOS 17+), sources in `ios/PrayerTime`, tests in `ios/PrayerTimeTests` and `ios/PrayerTimeUITests` |
| `shared/testdata` | Fixtures both cores must pass |
| `tools/` | Generators for fixtures, the city database and the app icon |

## The calculation core

Both cores wrap [Adhan](https://github.com/batoulapps/adhan) (MIT) and add the
behaviour the spec requires:

- 11 method presets plus custom Fajr/Isha angles or Isha interval; Shafi'i or
  Hanafi Asr; per-prayer ± minute offsets.
- An automatic high-latitude rule (seventh of the night above 48°), plus a
  nearest-latitude fallback for days with midnight sun or polar night. The
  result reports which one applied.
- Every place carries an IANA timezone, so DST, +5:30/+5:45 zones and the
  date line come out right.
- Umm al-Qura Hijri date with a ±2-day offset. Ramadan Isha (+30 min for Umm
  al-Qura) follows the adjusted date.
- Configurable Imsak, middle and last third of the night, and Qibla bearing.
- `upcoming(...)`, the ordered list of next prayer events that the
  notification schedulers will consume.

```kotlin
val toronto = Place("Toronto", 43.6532, -79.3832, "America/Toronto")
val settings = Defaults.settingsFor("CA")            // Moonsighting Committee
val today = PrayerCalculator.day(toronto, LocalDate.now(toronto.zone), settings)
today[Prayer.FAJR]                                   // java.time.Instant
```

```swift
let toronto = Place(name: "Toronto", latitude: 43.6532, longitude: -79.3832, timeZoneId: "America/Toronto")
let today = PrayerCalculator.day(at: toronto, on: CivilDate(Date(), in: toronto.timeZone),
                                 settings: Defaults.settings(forCountry: "CA"))
today[.fajr]                                         // Date
```

## Running the tests

```sh
cd android && ./gradlew :core:test                                   # JDK 17+
cd android && ./gradlew :notifications:assembleDebug :notifications:lintDebug   # needs ANDROID_HOME
cd ios/PrayerCore && swift test                                      # Xcode 16+ / Swift 6 toolchain
```

CI runs both test suites on every push (`.github/workflows/ci.yml`).

## Running the iOS app

Open `ios/PrayerTime.xcodeproj` in Xcode 16 or later and run the `PrayerTime`
scheme. To build and run the app's unit and UI tests from the command line:

```sh
cd ios && xcodebuild -scheme PrayerTime -destination 'platform=iOS Simulator,name=iPhone 17 Pro' test
```

The project uses folder-synced groups, so new files under `ios/PrayerTime` join
the app target without editing the project.

How the app behaves:

- **Location.** First launch uses the largest city in the device's time zone,
  so it never asks for location up front. Users can search the bundled GeoNames
  cities offline, or use an approximate GPS fix, which takes the name and IANA
  time zone of the nearest city.
- **Method.** The method follows the chosen city's country (`Defaults`) until
  the user changes any calculation setting.
- **Notifications.** Scheduling uses `PrayerNotifications` (see
  `docs/NOTIFICATIONS.md`). iOS keeps at most 64 pending notifications. The app
  brings them up to date on launch, on returning to the foreground, on
  background refresh, on a time-zone change and after any edit, touching only
  alerts that changed. The 64th slot, a day before the window runs out, tells
  the user to open the app. Adhans use the system sound until reciter clips
  (`<sound>_clip.caf`) are bundled.
- **Qibla.** The compass reads the magnetometer's magnetic heading and converts
  it to true north with the World Magnetic Model (WMM2025, bundled from NOAA,
  valid to 2030) at the saved location. This works without location permission,
  and the model is tested against NOAA's published test values. It shows
  compass accuracy, a calibration and metal warning, and a haptic and VoiceOver
  announcement when the phone faces the Qibla. In DEBUG builds,
  `QIBLA_TEST_HEADING` pins the heading so UI tests can drive it on a simulator.

Regenerating bundled assets:

```sh
# City database (inputs from https://download.geonames.org/export/dump/)
python3 tools/gen_cities.py cities15000.txt admin1CodesASCII.txt countryInfo.txt \
    > ios/PrayerTime/Resources/cities.tsv
# Magnetic model: replace with the next WMM.COF from NOAA when WMM2025 expires (2030)
#   https://www.ncei.noaa.gov/products/world-magnetic-model -> ios/PrayerTime/Resources/WMM2025.COF
# App icon
swift tools/make_icon.swift ios/PrayerTime/Assets.xcassets/AppIcon.appiconset/AppIcon.png
```

## Privacy

Everything is calculated on the device. The app has no server and makes no
network calls, and it will never ship ad, analytics or attribution SDKs. See
`docs/SPEC.md` for the reasoning.

City data is from [GeoNames](https://www.geonames.org) (CC BY 4.0) and is
credited on the app's About screen.

## License

MIT. See `LICENSE`. Adhan and its test data are also MIT (`shared/testdata/adhan/LICENSE`).
