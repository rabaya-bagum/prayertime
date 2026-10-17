import Adhan
import Foundation

/// How the day's times were obtained, so the UI can explain unusual values.
public enum Adjustment: Equatable, Sendable {
    /// Normal calculation. `highLatitudeRule` is the rule in effect, after resolving `.auto`.
    case calculated(highLatitudeRule: HighLatitudeRule)
    /// The sun never set, never rose, or twilight never ended at this latitude on
    /// this date, so times were computed at `latitudeUsed` ("nearest latitude").
    case polarFallback(latitudeUsed: Double, highLatitudeRule: HighLatitudeRule)
}

public struct DaySchedule: Sendable {
    public let place: Place
    public let date: CivilDate
    public let times: [Prayer: Date]
    public let imsak: Date
    public let middleOfTheNight: Date
    public let lastThirdOfTheNight: Date
    public let hijri: HijriDate
    public let method: Method
    public let adjustment: Adjustment

    public subscript(prayer: Prayer) -> Date { times[prayer]! }
}

public struct PrayerEvent: Equatable, Sendable {
    public let prayer: Prayer
    public let time: Date
    public let date: CivilDate
}

/// On-device prayer-time calculation. Everything runs offline; times are
/// instants for the civil `date` at `Place`, which the UI renders in the
/// place's own zone. Mirrors android/core's PrayerCalculator.
public enum PrayerCalculator {

    /// Nearest-latitude fallback walks toward the equator in these steps until times exist.
    private static let fallbackStepDegrees = 0.5
    private static let minGapSeconds: TimeInterval = 5 * 60

    public static func day(at place: Place, on date: CivilDate, settings: PrayerSettings) -> DaySchedule {
        let hijri = HijriDate.of(date, offsetDays: settings.hijriOffsetDays)
        let params = adhanParameters(settings, ramadan: hijri.isRamadan)
        let (times, latitude) = solve(place, date, params)

        let rule = resolvedRule(settings.highLatitudeRule, latitude: latitude)
        let adjustment: Adjustment = latitude == place.latitude
            ? .calculated(highLatitudeRule: rule)
            : .polarFallback(latitudeUsed: latitude, highLatitudeRule: rule)
        // Adhan's SunnahTimes recomputes tomorrow at the original latitude and
        // fails after a polar fallback, so the night is measured here instead.
        let tomorrowFajr = solve(place, date.adding(days: 1), params).times.fajr
        let night = tomorrowFajr.timeIntervalSince(times.maghrib)

        return DaySchedule(
            place: place,
            date: date,
            times: [
                .fajr: times.fajr,
                .sunrise: times.sunrise,
                .dhuhr: times.dhuhr,
                .asr: times.asr,
                .maghrib: times.maghrib,
                .isha: times.isha,
            ],
            imsak: times.fajr.addingTimeInterval(-TimeInterval(settings.imsakMinutesBeforeFajr * 60)),
            middleOfTheNight: roundToMinute(times.maghrib.addingTimeInterval(night / 2)),
            lastThirdOfTheNight: roundToMinute(times.maghrib.addingTimeInterval(night * 2 / 3)),
            hijri: hijri,
            method: settings.method,
            adjustment: adjustment
        )
    }

    /// The next `count` prayer events strictly after `from`, in order. Sunrise is
    /// included because it ends Fajr; callers that only want adhans filter it out.
    /// This is what the notification schedulers consume.
    public static func upcoming(at place: Place, settings: PrayerSettings, from: Date, count: Int) -> [PrayerEvent] {
        precondition(count >= 0)
        var events: [PrayerEvent] = []
        // Start a day early: in zones far from UTC the previous local day's Isha can still be ahead.
        var date = CivilDate(from, in: place.timeZone).adding(days: -1)
        while events.count < count {
            let schedule = day(at: place, on: date, settings: settings)
            for prayer in Prayer.allCases where schedule[prayer] > from && events.count < count {
                events.append(PrayerEvent(prayer: prayer, time: schedule[prayer], date: date))
            }
            date = date.adding(days: 1)
        }
        return events
    }

    /// Initial great-circle bearing to the Kaaba, in degrees clockwise from true north.
    public static func qiblaBearing(latitude: Double, longitude: Double) -> Double {
        Qibla(coordinates: Coordinates(latitude: latitude, longitude: longitude)).direction
    }

    static func adhanParameters(_ settings: PrayerSettings, ramadan: Bool) -> CalculationParameters {
        var params: CalculationParameters
        switch settings.method {
        case .muslimWorldLeague: params = CalculationMethod.muslimWorldLeague.params
        case .isna: params = CalculationMethod.northAmerica.params
        case .moonsightingCommittee: params = CalculationMethod.moonsightingCommittee.params
        case .egyptian: params = CalculationMethod.egyptian.params
        case .karachi: params = CalculationMethod.karachi.params
        case .ummAlQura: params = CalculationMethod.ummAlQura.params
        case .dubai: params = CalculationMethod.dubai.params
        case .kuwait: params = CalculationMethod.kuwait.params
        case .qatar: params = CalculationMethod.qatar.params
        case .singapore: params = CalculationMethod.singapore.params
        case .turkey: params = CalculationMethod.turkey.params
        case .custom:
            let angles = settings.customAngles!
            params = CalculationMethod.other.params
            params.fajrAngle = angles.fajrAngle
            params.ishaAngle = angles.ishaAngle
            params.ishaInterval = angles.ishaIntervalMinutes
        }
        let defaultRamadanExtension = settings.method == .ummAlQura ? 30 : 0
        let ramadanExtension = ramadan ? (settings.ramadanIshaExtensionMinutes ?? defaultRamadanExtension) : 0
        let offsets = settings.offsetMinutes

        switch settings.madhab {
        case .shafi: params.madhab = .shafi
        case .hanafi: params.madhab = .hanafi
        }
        switch settings.highLatitudeRule {
        case .auto: params.highLatitudeRule = nil
        case .middleOfTheNight: params.highLatitudeRule = .middleOfTheNight
        case .seventhOfTheNight: params.highLatitudeRule = .seventhOfTheNight
        case .twilightAngle: params.highLatitudeRule = .twilightAngle
        }
        switch settings.shafaq {
        case .general: params.shafaq = .general
        case .ahmer: params.shafaq = .ahmer
        case .abyad: params.shafaq = .abyad
        }
        params.adjustments = PrayerAdjustments(
            fajr: offsets[.fajr] ?? 0,
            sunrise: offsets[.sunrise] ?? 0,
            dhuhr: offsets[.dhuhr] ?? 0,
            asr: offsets[.asr] ?? 0,
            maghrib: offsets[.maghrib] ?? 0,
            isha: (offsets[.isha] ?? 0) + ramadanExtension
        )
        return params
    }

    /// The date Adhan needs so that its Dhuhr lands on civil `date` in the place's
    /// zone. Adhan treats the date as the solar day at that longitude, which
    /// matches the civil date except where the zone offset is far from
    /// longitude / 15 hours: in Kiritimati (UTC+14, 157°W) or Apia (UTC+13, 171°W)
    /// passing the civil date returns the next day's times.
    static func solarDate(_ place: Place, _ date: CivilDate) -> CivilDate {
        let localNoon = date.at(hour: 12, in: place.timeZone)
        let longitudeShift = TimeInterval(Int(place.longitude / 15 * 3600))
        return CivilDate(localNoon.addingTimeInterval(longitudeShift), in: .utcZone)
    }

    private static func resolvedRule(_ rule: HighLatitudeRule, latitude: Double) -> HighLatitudeRule {
        guard rule == .auto else { return rule }
        switch Adhan.HighLatitudeRule.recommended(for: Coordinates(latitude: latitude, longitude: 0)) {
        case .middleOfTheNight: return .middleOfTheNight
        case .seventhOfTheNight: return .seventhOfTheNight
        case .twilightAngle: return .twilightAngle
        }
    }

    /// Times for `date`, walking toward the equator from the place's latitude
    /// until a usable set exists. Returns the times and the latitude used.
    private static func solve(
        _ place: Place, _ date: CivilDate, _ params: CalculationParameters
    ) -> (times: PrayerTimes, latitude: Double) {
        let components = solarDate(place, date).dateComponents
        var latitude = place.latitude
        var times = compute(latitude, place.longitude, components, params)
        while times == nil && abs(latitude) > fallbackStepDegrees {
            latitude -= (latitude > 0 ? 1 : -1) * fallbackStepDegrees
            times = compute(latitude, place.longitude, components, params)
        }
        guard let times else { preconditionFailure("No prayer times for \(place.name) on \(date)") }
        return (times, latitude)
    }

    /// Nil when this latitude has no usable times on this date. Adhan returns nil
    /// when the sun never rises or sets, but near the polar circles it can
    /// instead return times out of order, e.g. Asr after Maghrib in Tromsø in
    /// early January. So we also require every prayer to follow the previous one
    /// by at least `minGapSeconds`, the guard upstream adhan-kotlin added after 0.0.7.
    private static func compute(
        _ latitude: Double, _ longitude: Double, _ date: DateComponents, _ params: CalculationParameters
    ) -> PrayerTimes? {
        guard let times = PrayerTimes(
            coordinates: Coordinates(latitude: latitude, longitude: longitude),
            date: date,
            calculationParameters: params
        ) else { return nil }
        let ordered = [times.fajr, times.sunrise, times.dhuhr, times.asr, times.maghrib, times.isha]
        for (a, b) in zip(ordered, ordered.dropFirst()) where b.timeIntervalSince(a) < minGapSeconds {
            return nil
        }
        return times
    }

    private static func roundToMinute(_ date: Date) -> Date {
        Date(timeIntervalSince1970: ((date.timeIntervalSince1970 + 30) / 60).rounded(.down) * 60)
    }
}
