// swift-tools-version:6.0
// Platform-neutral prayer-time logic for the iOS app, widgets and watch app.
// Mirrors android/core; both are tested against /shared/testdata.

import PackageDescription

let package = Package(
    name: "PrayerCore",
    platforms: [.iOS(.v17), .macOS(.v14), .watchOS(.v10)],
    products: [
        .library(name: "PrayerCore", targets: ["PrayerCore"]),
    ],
    dependencies: [
        .package(url: "https://github.com/batoulapps/adhan-swift", exact: "1.5.0"),
    ],
    targets: [
        .target(
            name: "PrayerCore",
            dependencies: [.product(name: "Adhan", package: "adhan-swift")]
        ),
        .testTarget(
            name: "PrayerCoreTests",
            dependencies: ["PrayerCore"]
        ),
    ],
    swiftLanguageModes: [.v5]
)
