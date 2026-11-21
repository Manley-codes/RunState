import Foundation
import XCTest
@testable import RunState

final class ReflectionConnectionTests: XCTestCase {
    private let first = String(repeating: "a", count: 48)
    private let second = String(repeating: "b", count: 48)

    func testKeychainConnectionSurvivesRelaunchAndCanBeDisabledOrForgotten() throws {
        let suite = UUID().uuidString
        let defaults = try XCTUnwrap(UserDefaults(suiteName: suite))
        let vault = KeychainReflectionVault(service: "runstate-test." + suite)
        defer {
            try? vault.remove()
            defaults.removePersistentDomain(forName: suite)
        }
        let connection = ReflectionConnection(preferences: defaults, vault: vault)
        XCTAssertFalse(connection.isEnabled)
        XCTAssertThrowsError(try connection.setEnabled(true))
        XCTAssertThrowsError(try connection.save(code: "sk-ant-" + first))
        XCTAssertNil(try vault.read())
        try connection.save(code: first)
        let original = try connection.credentials()
        let reopened = ReflectionConnection(preferences: defaults, vault: vault)
        XCTAssertTrue(reopened.isEnabled)
        XCTAssertEqual(try reopened.credentials().id, original.id)
        XCTAssertEqual(defaults.persistentDomain(forName: suite)?.count, 1)
        XCTAssertEqual(defaults.persistentDomain(forName: suite)?["run.writtenReflectionsEnabled"] as? Bool, true)
        try reopened.setEnabled(false)
        XCTAssertTrue(reopened.hasAccessCode)
        XCTAssertThrowsError(try reopened.credentials())
        try reopened.setEnabled(true)
        XCTAssertEqual(try reopened.credentials().id, original.id)
        try reopened.save(code: second)
        XCTAssertNotEqual(try reopened.credentials().id, original.id)
        try reopened.save(code: first)
        XCTAssertEqual(try reopened.credentials().id, original.id)
        try reopened.forget()
        XCTAssertNil(try vault.read())
        XCTAssertFalse(reopened.isEnabled)
        try reopened.save(code: first)
        XCTAssertEqual(try reopened.credentials().id, original.id)
    }
}
