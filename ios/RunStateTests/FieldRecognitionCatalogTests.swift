#if RUNSTATE_FIELD_TOOLS
import AVFAudio
import ShazamKit
import XCTest
@testable import RunState

final class FieldRecognitionCatalogTests: XCTestCase {
    @MainActor
    func testBothOriginalsMatchTheirOwnTitleFromAnInteriorPassage() async throws {
        guard #available(iOS 17.0, *) else {
            throw XCTSkip("The field song test uses managed recognition on iOS 17 or later.")
        }
        let catalog = try await FieldSongRecognition.makeCatalog()
        let cases = [
            ("field-finish-original", 1.25, "RunState Original"),
            ("field-finish-excerpt", 9.25, "RunState Excerpt")
        ]
        for (name, offset, expectedTitle) in cases {
            let url = try XCTUnwrap(Bundle.main.url(forResource: name, withExtension: "mp3"))
            let query = try passageSignature(url: url, offset: offset)
            switch await SHSession(catalog: catalog).result(from: query) {
            case .match(let match):
                XCTAssertEqual(match.mediaItems.first?.title, expectedTitle)
            case .noMatch:
                XCTFail("The eight-second passage of \(name) did not match the field catalog.")
            case .error(let error, _):
                throw error
            @unknown default:
                XCTFail("Unrecognized matching result for \(name).")
            }
        }
    }

    private func passageSignature(url: URL, offset: TimeInterval) throws -> SHSignature {
        let file = try AVAudioFile(forReading: url, commonFormat: .pcmFormatFloat32, interleaved: false)
        let format = file.processingFormat
        let frameCount = AVAudioFrameCount(8 * format.sampleRate)
        let passage = try XCTUnwrap(AVAudioPCMBuffer(pcmFormat: format, frameCapacity: frameCount))
        file.framePosition = AVAudioFramePosition(offset * format.sampleRate)
        try file.read(into: passage, frameCount: frameCount)
        XCTAssertEqual(passage.frameLength, frameCount, "The full test passage must exist in the source.")
        let generator = SHSignatureGenerator()
        try generator.append(passage, at: nil)
        return generator.signature()
    }
}
#endif
