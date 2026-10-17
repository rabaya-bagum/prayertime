import Foundation

/// Turns a true heading into "turn left/right" guidance toward the Qibla.
struct QiblaGuidance: Equatable, Sendable {
    /// Inside this the user is facing the Qibla; they must drift past `exitDegrees`
    /// before it switches off, so the haptic doesn't chatter at the edge.
    static let enterDegrees = 3.0
    static let exitDegrees = 6.0

    /// Degrees to turn, (−180, 180]: positive is clockwise (right).
    let turn: Double
    let isAligned: Bool

    init(qiblaBearing: Double, trueHeading: Double, wasAligned: Bool = false) {
        turn = Self.normalized(qiblaBearing - trueHeading)
        isAligned = abs(turn) <= (wasAligned ? Self.exitDegrees : Self.enterDegrees)
    }

    var instruction: String {
        if isAligned { return "Facing the Qibla" }
        let degrees = Int(abs(turn).rounded())
        return turn > 0 ? "Turn right \(degrees)°" : "Turn left \(degrees)°"
    }

    static func normalized(_ degrees: Double) -> Double {
        var d = degrees.truncatingRemainder(dividingBy: 360)
        if d > 180 { d -= 360 }
        if d <= -180 { d += 360 }
        return d
    }
}

/// How far to trust the magnetometer, from Core Location's `headingAccuracy`.
enum HeadingQuality: Equatable, Sendable {
    case calibrating
    case good(Double)
    case fair(Double)
    case poor(Double)

    init(accuracy: Double) {
        switch accuracy {
        case ..<0: self = .calibrating
        case ...10: self = .good(accuracy)
        case ...25: self = .fair(accuracy)
        default: self = .poor(accuracy)
        }
    }

    var needsCalibration: Bool {
        switch self {
        case .calibrating, .poor: return true
        case .good, .fair: return false
        }
    }

    var summary: String {
        switch self {
        case .calibrating: return "Calibrating"
        case .good(let a), .fair(let a), .poor(let a): return "±\(Int(a.rounded()))°"
        }
    }
}

extension Double {
    /// "10.2° W" / "3.4° E" for declination, east positive.
    var declinationText: String {
        let value = String(format: "%.1f°", abs(self))
        return self < 0 ? "\(value) W" : "\(value) E"
    }
}
