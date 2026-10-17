import XCTest

/// Drives the real app through its main flows and keeps screenshots in the result bundle.
@MainActor
final class PrayerTimeUITests: XCTestCase {
    private let app = XCUIApplication()

    override func setUp() async throws {
        continueAfterFailure = false
        addUIInterruptionMonitor(withDescription: "Permission alerts") { alert in
            for label in ["Allow While Using App", "Allow"] where alert.buttons[label].exists {
                alert.buttons[label].tap()
                return true
            }
            return false
        }
    }

    func testSearchForACityAndCustomizeTheMethod() {
        app.launch()
        app.buttons["Change location"].tap()
        let search = app.searchFields["Search cities"]
        search.tap()
        search.typeText("tromso")
        let tromso = app.buttons.matching(NSPredicate(format: "label BEGINSWITH %@", "Tromsø")).firstMatch
        XCTAssertTrue(tromso.waitForExistence(timeout: 5))
        screenshot("1-search")
        tromso.tap()

        XCTAssertTrue(app.navigationBars["Tromsø"].waitForExistence(timeout: 5))
        screenshot("2-tromso")
        // Times must be in Tromsø's zone whatever the device's: Dhuhr there is always near midday.
        let dhuhr = app.descendants(matching: .any).matching(NSPredicate(format: "label BEGINSWITH 'Dhuhr,'")).firstMatch
        XCTAssertTrue(dhuhr.exists)
        XCTAssertNotNil(dhuhr.label.range(of: #"Dhuhr, (11|12|1):\d\d"#, options: .regularExpression), dhuhr.label)

        app.swipeUp() // List rows below the fold aren't in the hierarchy until shown
        XCTAssertTrue(element(containing: "Seventh of the night").waitForExistence(timeout: 5), "Tromsø is above 48°")

        app.buttons["Settings"].tap()
        app.buttons.matching(NSPredicate(format: "label BEGINSWITH %@", "Method")).firstMatch.tap()
        app.buttons["ISNA (North America)"].tap()
        XCTAssertTrue(app.buttons["Use defaults for Troms, Norway"].waitForExistence(timeout: 5))
        screenshot("3-settings")
        app.swipeUp()
        screenshot("4-settings-alerts")

        app.swipeDown()
        app.buttons["Use defaults for Troms, Norway"].tap()
        app.buttons["Done"].tap()
        XCTAssertTrue(element(containing: "Muslim World League").waitForExistence(timeout: 5))
    }

    func testUseCurrentLocation() {
        app.launch()
        app.buttons["Change location"].tap()
        app.buttons["Use current location"].tap()
        app.tap() // lets the interruption monitor handle a first-time permission alert
        // The simulator's location is set with `xcrun simctl location`; CI uses its default (Cupertino).
        let closed = waitForDisappearance(app.searchFields["Search cities"], timeout: 10)
        screenshot("5-current-location")
        XCTAssertTrue(closed, "picker should close after a fix")
    }

    func testQiblaCompassGuidesToTheQibla() {
        // Simulators have no magnetometer; the app pins the magnetic heading in DEBUG.
        app.launchEnvironment["QIBLA_TEST_HEADING"] = "0"
        app.launch()
        choose(city: "toronto", named: "Toronto")
        app.tabBars.buttons["Qibla"].tap()
        // The dial carries the guidance as its accessibility value: what VoiceOver reads.
        let dial = app.otherElements["Qibla compass"]
        XCTAssertTrue(dial.waitForExistence(timeout: 5))
        XCTAssertTrue((dial.value as? String)?.hasPrefix("Turn right") == true, "\(dial.value ?? "nil")")
        XCTAssertTrue(element(containing: "° W").exists, "Toronto's declination is west")
        screenshot("6-qibla-turn")

        // Magnetic 65° in Toronto is ~55° true, the Qibla bearing.
        app.terminate()
        app.launchEnvironment["QIBLA_TEST_HEADING"] = "65"
        app.launch()
        app.tabBars.buttons["Qibla"].tap()
        XCTAssertTrue(app.otherElements["Qibla compass"].waitForExistence(timeout: 5))
        XCTAssertEqual(app.otherElements["Qibla compass"].value as? String, "Facing the Qibla")
        screenshot("7-qibla-aligned")
    }

    func testQiblaWithoutCompassShowsBearing() {
        app.launch()
        app.tabBars.buttons["Qibla"].tap()
        XCTAssertTrue(element(containing: "This device has no compass").waitForExistence(timeout: 5))
        screenshot("8-qibla-no-compass")
    }

    private func choose(city query: String, named name: String) {
        app.buttons["Change location"].tap()
        let search = app.searchFields["Search cities"]
        search.tap()
        search.typeText(query)
        let result = app.buttons.matching(NSPredicate(format: "label BEGINSWITH %@", name)).firstMatch
        XCTAssertTrue(result.waitForExistence(timeout: 5))
        result.tap()
        XCTAssertTrue(app.navigationBars[name].waitForExistence(timeout: 5))
    }

    private func waitForDisappearance(_ element: XCUIElement, timeout: TimeInterval) -> Bool {
        let gone = expectation(for: NSPredicate(format: "exists == false"), evaluatedWith: element)
        return XCTWaiter.wait(for: [gone], timeout: timeout) == .completed
    }

    private func element(containing text: String) -> XCUIElement {
        app.descendants(matching: .any).matching(NSPredicate(format: "label CONTAINS %@", text)).firstMatch
    }

    private func screenshot(_ name: String) {
        let attachment = XCTAttachment(screenshot: app.screenshot())
        attachment.name = name
        attachment.lifetime = .keepAlways
        add(attachment)
    }
}
