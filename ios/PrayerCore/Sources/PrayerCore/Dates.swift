import Foundation

/// A calendar day with no time or zone, the Swift counterpart of java.time.LocalDate.
public struct CivilDate: Hashable, Comparable, Codable, Sendable, CustomStringConvertible {
    public let year: Int
    public let month: Int
    public let day: Int

    public init(year: Int, month: Int, day: Int) {
        self.year = year
        self.month = month
        self.day = day
    }

    /// Parses "yyyy-MM-dd".
    public init?(_ iso: String) {
        let parts = iso.split(separator: "-").compactMap { Int($0) }
        guard parts.count == 3 else { return nil }
        self.init(year: parts[0], month: parts[1], day: parts[2])
    }

    /// The civil date of `date` as seen in `timeZone`.
    public init(_ date: Date, in timeZone: TimeZone) {
        var calendar = Calendar.civilGregorian
        calendar.timeZone = timeZone
        let c = calendar.dateComponents([.year, .month, .day], from: date)
        self.init(year: c.year!, month: c.month!, day: c.day!)
    }

    public func adding(days: Int) -> CivilDate {
        let next = Calendar.civilGregorian.date(byAdding: .day, value: days, to: at(hour: 12, in: .utcZone))!
        return CivilDate(next, in: .utcZone)
    }

    /// The instant this date shows `hour`:`minute` on a wall clock in `timeZone`.
    public func at(hour: Int, minute: Int = 0, in timeZone: TimeZone) -> Date {
        var calendar = Calendar.civilGregorian
        calendar.timeZone = timeZone
        return calendar.date(from: DateComponents(year: year, month: month, day: day, hour: hour, minute: minute))!
    }

    var dateComponents: DateComponents { DateComponents(year: year, month: month, day: day) }

    public var description: String { String(format: "%04d-%02d-%02d", year, month, day) }

    public static func < (lhs: CivilDate, rhs: CivilDate) -> Bool {
        (lhs.year, lhs.month, lhs.day) < (rhs.year, rhs.month, rhs.day)
    }
}

/// Hijri date from the platform's Umm al-Qura calendar (CLDR islamic-umalqura),
/// shifted by the user's ±2-day offset. Months that begin by moon sighting can't
/// be computed in advance, which is why the offset exists.
public struct HijriDate: Equatable, Sendable {
    public static let ramadan = 9

    public let year: Int
    public let month: Int
    public let day: Int

    public init(year: Int, month: Int, day: Int) {
        self.year = year
        self.month = month
        self.day = day
    }

    public var isRamadan: Bool { month == HijriDate.ramadan }

    public static func of(_ date: CivilDate, offsetDays: Int = 0) -> HijriDate {
        var calendar = Calendar(identifier: .islamicUmmAlQura)
        calendar.timeZone = .utcZone
        let c = calendar.dateComponents([.year, .month, .day], from: date.adding(days: offsetDays).at(hour: 12, in: .utcZone))
        return HijriDate(year: c.year!, month: c.month!, day: c.day!)
    }
}

extension TimeZone {
    static let utcZone = TimeZone(identifier: "UTC")!
}

extension Calendar {
    static let civilGregorian: Calendar = {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = .utcZone
        return calendar
    }()
}
