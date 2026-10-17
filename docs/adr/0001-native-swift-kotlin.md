# ADR 0001: Native Swift and Kotlin, with a shared test corpus

**Status:** Accepted, Oct 2026

## Context

The app's differentiators are correct times, an adhan that always fires, and
collecting nothing. Every surface tied to the second one needs native code
anyway: exact alarms and foreground services on Android; the 64-slot
notification window, AlarmKit, WidgetKit, Live Activities and watch
complications on iOS. A cross-platform UI framework would only save effort on
the main screens.

Options considered:

| Option | Calculation library | Cost |
|---|---|---|
| Native Swift + Kotlin | Official adhan-swift and adhan-kotlin | Two codebases for the UI and the core wrapper |
| Kotlin Multiplatform core + native UI | adhan-kotlin on both platforms | One core, but KMP build tooling inside Xcode |
| React Native + native modules | Official adhan-js | One UI, but the riskiest code is native modules anyway |

## Decision

Build native apps: SwiftUI on iOS and Jetpack Compose on Android. Each platform
has a small core library (`ios/PrayerCore`, `android/core`) wrapping its Adhan
port behind the same API: `day`, `upcoming`, `qiblaBearing`, `HijriDate`,
`Defaults`.

To keep the apps from drifting:

- Both cores run the same fixtures in `shared/testdata`: Adhan's published
  timetables plus independent PrayTimes references.
- Both suites have the same property tests, case for case.
- Method ids, enum names and behaviour (polar fallback, solar-date shift,
  Ramadan Isha) are kept identical. A change to one core lands with the
  matching change to the other in the same PR.
- CI runs both suites on every push.

## Consequences

- Platform features (alarms, widgets, AlarmKit) get first-class APIs with no
  bridging layer.
- Wrapper logic is duplicated, roughly 250 lines per platform, so the shared
  tests matter. If the cores grow much (e.g. a notification planner with
  complex rules), revisit KMP for the core only. adhan-kotlin already publishes
  iOS targets.
- Swift code can't be built in Linux-only environments. CI runs it on macOS.
