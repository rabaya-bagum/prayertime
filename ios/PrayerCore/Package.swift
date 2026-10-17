// swift-tools-version:6.0
// Platform-neutral prayer-time logic for the iOS app, widgets and watch app.
// Mirrors android/core; both are tested against /shared/testdata.

import PackageDescription

let package = Package(
    name: "PrayerCore",
    platforms: [.iOS(.v17), .macOS(.v14), .watchOS(.v10)],
    products: [
        .library(name: "PrayerCore", targets: ["PrayerCore"]),
        .library(name: "PrayerNotifications", targets: ["PrayerNotifications"]),
    ],
    dependencies: [
        .package(url: "https://github.com/batoulapps/adhan-swift", exact: "1.5.0"),
    ],
    targets: [
        .target(
            name: "PrayerCore",
            dependencies: [.product(name: "Adhan", package: "adhan-swift")]
        ),
        // UserNotifications glue for the iOS app; the planning logic lives in PrayerCore.
        .target(
            name: "PrayerNotifications",
            dependencies: ["PrayerCore"]
        ),
        .testTarget(
            name: "PrayerCoreTests",
            dependencies: ["PrayerCore"]
        ),
        .testTarget(
            name: "PrayerNotificationsTests",
            dependencies: ["PrayerNotifications"]
        ),
    ],
    swiftLanguageModes: [.v5]
)
