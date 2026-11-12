#if RUNSTATE_FIELD_TOOLS
import Foundation
import XCTest
@testable import RunState

final class MusicReflectionResponseTests: XCTestCase {
    private let requestID = "00000000-0000-0000-0000-000000000001"
    private let runID = "00000000-0000-0000-0000-000000000002"
    private let sentence = "You found a steady rhythm over those two miles. Evening Stride carries that feeling into the finish."

    private func recording(id: String = "1", title: String = "Evening Stride", loadedAtFinish: Bool = false) -> ReflectionRecording {
        .init(id: id, title: title, artist: "RunState Studio", album: nil, durationMillis: 30_000,
              loadedAtFinish: loadedAtFinish)
    }

    private func fixture() throws -> [String: Any] {
        let url = try XCTUnwrap(Bundle.main.url(forResource: "music-finish-response", withExtension: "json"))
        return try XCTUnwrap(JSONSerialization.jsonObject(with: Data(contentsOf: url)) as? [String: Any])
    }

    private func decode(_ response: [String: Any], songs: [ReflectionRecording]) throws -> WrittenReflectionAnswers {
        try ReflectionService.answers(from: JSONSerialization.data(withJSONObject: response), requestID: requestID,
                                      runID: runID, recordings: songs)
    }

    func testJavaReceiptSuppliesExactSampleWordsAndFourSecondClip() throws {
        let answer = try MusicFinishSample.response()
        XCTAssertTrue(answer.isTestData)
        XCTAssertEqual(answer.feelingGood, sentence)
        XCTAssertEqual(answer.selectedSongID, "1")
        let clip = try XCTUnwrap(answer.snippet)
        XCTAssertEqual(clip.title, "Evening Stride")
        XCTAssertEqual(clip.artist, "RunState Studio")
        XCTAssertEqual(clip.startPositionMillis, 4_000)
        XCTAssertEqual(clip.durationMillis, 4_000)
    }

    func testUnrequestedOrMismatchedMusicCannotControlPlaybackOrInvalidateText() throws {
        let response = try fixture()
        for songs in [[], [recording(id: "7")], [recording(), recording()], [recording(title: "Another recording")]] {
            let answer = try decode(response, songs: songs)
            XCTAssertEqual(answer.feelingGood, sentence)
            XCTAssertNil(answer.snippet)
        }
        var fake = response
        fake["generationMode"] = "FAKE"
        XCTAssertNil(try decode(fake, songs: [recording()]).snippet)
    }

    func testSelectedSongAtFinishKeepsWordsWithoutAClip() throws {
        let answer = try decode(fixture(), songs: [recording(loadedAtFinish: true)])
        XCTAssertEqual(answer.selectedSongID, "1")
        XCTAssertEqual(answer.feelingGood, sentence)
        XCTAssertNil(answer.snippet)
    }

    func testMalformedCueFallsBackToTheWrittenReflection() throws {
        var response = try fixture()
        let valid = try XCTUnwrap(response["songSnippet"] as? [String: Any])
        let mutations: [[String: Any]] = [
            ["durationMillis": 1_999], ["durationMillis": "4000"], ["version": true], ["version": 3],
            ["startPositionMillis": Int.max], ["source": "OTHER"], ["trackDurationMillis": 30_001],
            ["sourceId": "0042"], ["artistName": "Another artist"], ["cueId": "wrong"], ["selectionMethod": "UNKNOWN"]
        ]
        for mutation in mutations {
            response["songSnippet"] = valid.merging(mutation) { _, new in new }
            let answer = try decode(response, songs: [recording()])
            XCTAssertEqual(answer.feelingGood, sentence)
            XCTAssertNil(answer.snippet, "Invalid receipt: \(mutation.keys)")
        }
        response["songSnippet"] = valid
        response["songCueId"] = "some_other_cue"
        XCTAssertNil(try decode(response, songs: [recording()]).snippet)
        response["songSelectionMethod"] = "UNKNOWN"
        XCTAssertNil(try decode(response, songs: [recording()]).selectedSongID)
    }

    func testLegacyWordsAndVersionTwoReceiptWithoutAcknowledgement() throws {
        var response = try fixture()
        response.removeValue(forKey: "songCueId")
        XCTAssertNotNil(try decode(response, songs: [recording()]).snippet)
        response.removeValue(forKey: "songSnippet")
        response.removeValue(forKey: "selectedSongId")
        response.removeValue(forKey: "songSelectionMethod")
        let answer = try decode(response, songs: [])
        XCTAssertEqual(answer.feelingGood, sentence)
        XCTAssertNil(answer.snippet)
        response["runId"] = requestID
        XCTAssertThrowsError(try decode(response, songs: []))
    }
}
#endif
