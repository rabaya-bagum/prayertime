import Foundation

/// The World Magnetic Model (NOAA/BGS, public domain), used to turn the compass's
/// magnetic heading into a true heading without location permission (SPEC
/// revisions: iOS only reports `trueHeading` with location services on).
///
/// Follows "The US/UK World Magnetic Model for 2025–2030" technical report:
/// geodetic → geocentric coordinates, a degree-12 spherical-harmonic sum with
/// Schmidt semi-normalized Legendre functions, then rotation back to geodetic.
/// Checked against NOAA's published test values in MagneticModelTests.
struct MagneticModel: Sendable {
    static let shared = MagneticModel(url: Bundle.main.url(forResource: "WMM2025", withExtension: "COF")!)!

    let epoch: Double
    let name: String
    private let maxDegree: Int
    // Indexed [n][m]; nT and nT/year.
    private let g: [[Double]], h: [[Double]], gDot: [[Double]], hDot: [[Double]]

    /// The model is only valid for five years from its epoch; NOAA publishes the next one.
    var validYears: ClosedRange<Double> { epoch...(epoch + 5) }

    init?(url: URL) {
        guard let text = try? String(contentsOf: url, encoding: .utf8) else { return nil }
        self.init(cof: text)
    }

    /// Parses NOAA's WMM.COF format: a header line, then "n m g h ġ ḣ" rows, ending in 9s.
    init?(cof text: String) {
        let lines = text.split(whereSeparator: \.isNewline)
        guard let header = lines.first?.split(separator: " "), header.count >= 2,
              let epoch = Double(header[0]) else { return nil }
        var rows: [(Int, Int, Double, Double, Double, Double)] = []
        for line in lines.dropFirst() {
            let f = line.split(separator: " ")
            guard f.count == 6, let n = Int(f[0]), let m = Int(f[1]),
                  let g = Double(f[2]), let h = Double(f[3]), let gd = Double(f[4]), let hd = Double(f[5]) else { break }
            rows.append((n, m, g, h, gd, hd))
        }
        guard let maxDegree = rows.map(\.0).max(), maxDegree >= 1 else { return nil }
        var g = Array(repeating: Array(repeating: 0.0, count: maxDegree + 1), count: maxDegree + 1)
        var h = g, gDot = g, hDot = g
        for (n, m, gv, hv, gd, hd) in rows where m <= n {
            g[n][m] = gv; h[n][m] = hv; gDot[n][m] = gd; hDot[n][m] = hd
        }
        self.epoch = epoch
        self.name = String(header[1])
        self.maxDegree = maxDegree
        self.g = g; self.h = h; self.gDot = gDot; self.hDot = hDot
    }

    /// Magnetic declination in degrees, east positive: true heading = magnetic heading + declination.
    func declination(latitude: Double, longitude: Double, altitudeKm: Double = 0, decimalYear: Double) -> Double {
        let (x, y, _) = field(latitude: latitude, longitude: longitude, altitudeKm: altitudeKm, decimalYear: decimalYear)
        return atan2(y, x) * 180 / .pi
    }

    func declination(latitude: Double, longitude: Double, at date: Date) -> Double {
        declination(latitude: latitude, longitude: longitude, decimalYear: Self.decimalYear(date))
    }

    /// North, east and down components in nT, in the geodetic frame.
    func field(latitude: Double, longitude: Double, altitudeKm: Double, decimalYear: Double) -> (x: Double, y: Double, z: Double) {
        let deg = Double.pi / 180
        // WGS-84 ellipsoid and the model's reference radius.
        let a = 6378.137, f = 1 / 298.257223563, e2 = f * (2 - f), referenceRadius = 6371.2

        // Geodetic → geocentric spherical (report eq. 7).
        let phi = latitude * deg, lambda = longitude * deg
        let rc = a / sqrt(1 - e2 * sin(phi) * sin(phi))
        let p = (rc + altitudeKm) * cos(phi)
        let z = (rc * (1 - e2) + altitudeKm) * sin(phi)
        let r = sqrt(p * p + z * z)
        let phiPrime = asin(z / r)

        // Gauss coefficients at the requested time (eq. 9).
        let dt = decimalYear - epoch
        let n0 = maxDegree

        // Schmidt semi-normalized P(n,m)(sin φ') and dP/dφ', built from Gauss-normalized
        // recursions then scaled (Wertz, Spacecraft Attitude Determination, app. H).
        let theta = Double.pi / 2 - phiPrime        // colatitude
        let cosT = cos(theta), sinT = sin(theta)
        var P = Array(repeating: Array(repeating: 0.0, count: n0 + 1), count: n0 + 1)
        var dP = P
        P[0][0] = 1
        for n in 1...n0 {
            for m in 0...n {
                if n == m {
                    P[n][m] = sinT * P[n - 1][m - 1]
                    dP[n][m] = sinT * dP[n - 1][m - 1] + cosT * P[n - 1][m - 1]
                } else {
                    let k = n == 1 ? 0 : Double((n - 1) * (n - 1) - m * m) / Double((2 * n - 1) * (2 * n - 3))
                    let p2 = n >= 2 ? P[n - 2][m] : 0, dp2 = n >= 2 ? dP[n - 2][m] : 0
                    P[n][m] = cosT * P[n - 1][m] - k * p2
                    dP[n][m] = cosT * dP[n - 1][m] - sinT * P[n - 1][m] - k * dp2
                }
            }
        }
        var S = P
        S[0][0] = 1
        for n in 1...n0 {
            S[n][0] = S[n - 1][0] * Double(2 * n - 1) / Double(n)
            for m in 1...n {
                let factor = Double((n - m + 1) * (m == 1 ? 2 : 1)) / Double(n + m)
                S[n][m] = S[n][m - 1] * sqrt(factor)
            }
        }

        // Spherical-harmonic sums (eq. 10). With B = −∇V and θ the colatitude:
        // north X' = (1/r)∂V/∂θ, east Y' = −(1/(r sin θ))∂V/∂λ, down Z' = ∂V/∂r.
        var bNorth = 0.0, bEast = 0.0, bDown = 0.0
        var ratio = (referenceRadius / r) * (referenceRadius / r)
        for n in 1...n0 {
            ratio *= referenceRadius / r // (a/r)^(n+2)
            for m in 0...n {
                let gnm = g[n][m] + dt * gDot[n][m], hnm = h[n][m] + dt * hDot[n][m]
                let cosM = cos(Double(m) * lambda), sinM = sin(Double(m) * lambda)
                let pnm = S[n][m] * P[n][m], dpnm = S[n][m] * dP[n][m]
                let gh = gnm * cosM + hnm * sinM
                bNorth += ratio * gh * dpnm
                bEast += ratio * Double(m) * (gnm * sinM - hnm * cosM) * pnm
                bDown -= Double(n + 1) * ratio * gh * pnm
            }
        }
        // Guard the poles, where declination is undefined anyway.
        bEast /= max(sinT, 1e-10)

        // Rotate from geocentric to geodetic (eq. 17).
        let psi = phiPrime - phi
        let x = bNorth * cos(psi) - bDown * sin(psi)
        let zDown = bNorth * sin(psi) + bDown * cos(psi)
        return (x, bEast, zDown)
    }

    static func decimalYear(_ date: Date) -> Double {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(identifier: "UTC")!
        let year = calendar.component(.year, from: date)
        let start = calendar.date(from: DateComponents(year: year))!
        let end = calendar.date(from: DateComponents(year: year + 1))!
        return Double(year) + date.timeIntervalSince(start) / end.timeIntervalSince(start)
    }
}
