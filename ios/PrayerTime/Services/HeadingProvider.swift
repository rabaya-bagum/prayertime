import CoreLocation
import Observation

/// Streams the magnetometer heading. Heading updates need no location permission;
/// the app converts magnetic to true north itself with `MagneticModel`.
@MainActor
@Observable
final class HeadingProvider: NSObject, CLLocationManagerDelegate {
    let isAvailable: Bool
    /// Degrees clockwise from magnetic north, or nil before the first reading.
    private(set) var magneticHeading: Double?
    /// Core Location's error estimate in degrees; negative while uncalibrated.
    private(set) var accuracy: Double = -1

    @ObservationIgnored private let manager = CLLocationManager()
    @ObservationIgnored private var fixedHeading: Double?

    override init() {
        #if DEBUG
        // UI tests can't turn a simulator, so they pin the heading.
        if let value = ProcessInfo.processInfo.environment["QIBLA_TEST_HEADING"].flatMap(Double.init) {
            fixedHeading = value
        }
        #endif
        isAvailable = fixedHeading != nil || CLLocationManager.headingAvailable()
        super.init()
        manager.delegate = self
        manager.headingFilter = 1
    }

    func start() {
        if let fixedHeading {
            magneticHeading = fixedHeading
            accuracy = 5
            return
        }
        guard isAvailable else { return }
        manager.startUpdatingHeading()
    }

    func stop() {
        manager.stopUpdatingHeading()
    }

    nonisolated func locationManager(_ manager: CLLocationManager, didUpdateHeading heading: CLHeading) {
        let magnetic = heading.magneticHeading, accuracy = heading.headingAccuracy
        MainActor.assumeIsolated {
            self.accuracy = accuracy
            if accuracy >= 0 { magneticHeading = magnetic }
        }
    }

    /// Lets iOS show its own figure-eight prompt when the magnetometer needs it.
    nonisolated func locationManagerShouldDisplayHeadingCalibration(_ manager: CLLocationManager) -> Bool {
        true
    }
}
