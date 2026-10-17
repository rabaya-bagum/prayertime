import CoreLocation

/// One-shot, approximate, when-in-use location. Never "Always": the app refreshes on
/// open instead (SPEC revisions), and a manual city works with permission denied.
@MainActor
final class LocationProvider: NSObject, CLLocationManagerDelegate {
    enum Failure: LocalizedError {
        case denied, unavailable

        var errorDescription: String? {
            switch self {
            case .denied: return "Location access is off. You can turn it on in Settings, or search for your city instead."
            case .unavailable: return "Couldn't find your location. Search for your city instead."
            }
        }
    }

    private let manager = CLLocationManager()
    private var continuation: CheckedContinuation<CLLocation, Error>?

    override init() {
        super.init()
        manager.delegate = self
        // Prayer times move about 30 s per few km, so city-level accuracy is plenty.
        manager.desiredAccuracy = kCLLocationAccuracyReduced
    }

    func currentLocation() async throws -> CLLocation {
        continuation?.resume(throwing: CancellationError())
        return try await withCheckedThrowingContinuation { continuation in
            self.continuation = continuation
            proceed()
        }
    }

    private func proceed() {
        switch manager.authorizationStatus {
        case .notDetermined: manager.requestWhenInUseAuthorization()
        case .authorizedWhenInUse, .authorizedAlways: manager.requestLocation()
        default: finish(.failure(Failure.denied))
        }
    }

    private func finish(_ result: Result<CLLocation, Error>) {
        continuation?.resume(with: result)
        continuation = nil
    }

    nonisolated func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        let status = manager.authorizationStatus
        MainActor.assumeIsolated {
            if continuation != nil && status != .notDetermined { proceed() }
        }
    }

    nonisolated func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        MainActor.assumeIsolated {
            if let location = locations.last { finish(.success(location)) }
        }
    }

    nonisolated func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
        MainActor.assumeIsolated {
            let denied = (error as? CLError)?.code == .denied
            finish(.failure(denied ? Failure.denied : Failure.unavailable))
        }
    }
}
