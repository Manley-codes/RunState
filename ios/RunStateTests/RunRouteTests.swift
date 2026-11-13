import CoreLocation
import CoreGraphics
import XCTest
@testable import RunState

final class RunRouteTests: XCTestCase {
    private func fix(_ latitude: Double, _ longitude: Double) -> CLLocationCoordinate2D {
        CLLocationCoordinate2D(latitude: latitude, longitude: longitude)
    }

    func testRestoredRouteKeepsPausesAndInvalidFixesAsSeparateSections() throws {
        var route = RunRoute()
        route.append(fix(32, -96), beginsSegment: false)
        route.append(fix(32, -96), beginsSegment: false)
        route.append(fix(32.001, -96), beginsSegment: false)
        route.append(fix(32.010, -96), beginsSegment: true)
        route.append(fix(.nan, -96), beginsSegment: false)
        let saved = try JSONEncoder().encode(route)
        var restored = try JSONDecoder().decode(RunRoute.self, from: saved)
        XCTAssertEqual(restored, route)
        restored.append(fix(32.040, -96), beginsSegment: false)
        restored.append(fix(32.041, -96), beginsSegment: false)
        let sections = SavedRouteView.sections(for: restored, in: CGSize(width: 210, height: 210))
        XCTAssertEqual(sections.map(\.count), [2, 1, 2])
    }

    func testDateLineCrossingIsShortAndNorthPointsUp() {
        var route = RunRoute()
        route.append(fix(0, 179.99), beginsSegment: true)
        route.append(fix(0, -179.99), beginsSegment: false)
        route.append(fix(0.01, -179.99), beginsSegment: false)
        let sections = SavedRouteView.sections(for: route, in: CGSize(width: 232, height: 232))
        guard sections.count == 1, let path = sections.first, path.count == 3 else {
            return XCTFail("Expected one continuous three-point section")
        }
        let east = path[1].x - path[0].x
        let north = path[1].y - path[2].y
        XCTAssertGreaterThan(east, 0)
        XCTAssertGreaterThan(north, 0)
        XCTAssertEqual(east, 2 * north, accuracy: 0.1)
    }

    func testLongRunRetainsSectionEndpointsAndResumesAtItsSavedSamplingRate() throws {
        var route = RunRoute()
        let count = RunRoute.pointLimit * 2 + 19
        for index in 0..<count {
            route.append(fix(Double(index) / 10_000, 0), beginsSegment: index == 250)
        }
        XCTAssertFalse(route.isTruncated)
        XCTAssertLessThanOrEqual(route.points.count, RunRoute.pointLimit)
        let gap = try XCTUnwrap(route.points.lastIndex(where: \.beginsSegment))
        XCTAssertGreaterThan(gap, 0)
        XCTAssertEqual(route.points[gap - 1].latitude, 0.0249)
        XCTAssertEqual(route.points[gap].latitude, 0.025)
        XCTAssertEqual(route.points.first?.latitude, 0)
        XCTAssertEqual(route.points.last?.latitude, Double(count - 1) / 10_000)
        let data = try JSONEncoder().encode(route)
        var restored = try JSONDecoder().decode(RunRoute.self, from: data)
        for index in count..<(count + 5) {
            let next = fix(Double(index) / 10_000, 0)
            restored.append(next, beginsSegment: false)
            route.append(next, beginsSegment: false)
        }
        XCTAssertEqual(restored, route)
    }

    func testDisconnectedFixesCannotBecomeAnInventedLineAtTheLimit() {
        var route = RunRoute()
        for index in 0...RunRoute.pointLimit {
            route.append(fix(Double(index) / 10_000, 0), beginsSegment: true)
        }
        XCTAssertTrue(route.isTruncated)
        XCTAssertEqual(route.points.count, RunRoute.pointLimit)
        XCTAssertTrue(route.points.allSatisfy(\.beginsSegment))
    }
}
