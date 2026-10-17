# Prayer Time

A free prayer-time app for iOS and Android. Its goals: times that match your
local mosque, an adhan that fires every time, and nothing leaving your phone.
No ads, no accounts, no analytics, no paywall.

**Status:** pre-alpha. The calculation core for both platforms is in place and
tested. The iOS app has a Today screen, offline city search, approximate GPS,
calculation settings and adhan notifications (system sound for now). The
Android app isn't started.

## Repository layout

| Path | What it is |
|---|---|
| `docs/SPEC.md` | Product spec, with revisions from building the core |
| `docs/adr/` | Architecture decisions |
| `android/core` | Kotlin calculation core (plain JVM, no Android dependencies) |
| `ios/PrayerCore` | Swift package with the same API |
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
cd android && ./gradlew :core:test      # JDK 17+
cd ios/PrayerCore && swift test         # Xcode 16+ / Swift 6 toolchain
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
- **Notifications.** iOS keeps at most 64 pending notifications. The app refills
  them on launch, on returning to the foreground, on background refresh and on a
  time-zone change. The 64th slot tells the user to open the app.

Regenerating bundled assets:

```sh
# City database (inputs from https://download.geonames.org/export/dump/)
python3 tools/gen_cities.py cities15000.txt admin1CodesASCII.txt countryInfo.txt \
    > ios/PrayerTime/Resources/cities.tsv
# App icon
swift tools/make_icon.swift ios/PrayerTime/Assets.xcassets/AppIcon.appiconset/AppIcon.png
```

## Privacy

Everything is calculated on the device. The app has no server and makes no
network calls, and it will never ship ad, analytics or attribution SDKs. See
`docs/SPEC.md` for the reasoning.

City data is from [GeoNames](https://www.geonames.org) (CC BY 4.0) and is
credited on the app's About screen.
