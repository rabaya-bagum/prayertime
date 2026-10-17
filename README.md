# Prayer Time

A free prayer-time app for iOS and Android. Its goals: times that match your
local mosque, an adhan that fires every time, and nothing leaving your phone.
No ads, no accounts, no analytics, no paywall.

**Status:** pre-alpha. The calculation core for both platforms is in place and
tested. The apps themselves aren't built yet.

## Repository layout

| Path | What it is |
|---|---|
| `docs/SPEC.md` | Product spec, with revisions from building the core |
| `docs/adr/` | Architecture decisions |
| `android/core` | Kotlin calculation core (plain JVM, no Android dependencies) |
| `ios/PrayerCore` | Swift package with the same API |
| `shared/testdata` | Fixtures both cores must pass |
| `tools/` | Fixture generators |

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

CI runs both on every push (`.github/workflows/ci.yml`).

## Privacy

Everything is calculated on the device. The app has no server and makes no
network calls, and it will never ship ad, analytics or attribution SDKs. See
`docs/SPEC.md` for the reasoning.
