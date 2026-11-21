#if RUNSTATE_FIELD_TOOLS
import AVFoundation
import Foundation
import ShazamKit

@MainActor
protocol FieldSongRecognizing: AnyObject {
    func listen(onReady: @escaping @MainActor () -> Void,
                onMatch: @escaping @MainActor (String) -> Void) async throws
    func stop()
}

@available(iOS 17.0, *)
@MainActor
final class FieldSongRecognition: FieldSongRecognizing {
    private static let tracks = [
        (file: "field-finish-original", title: "RunState Original"),
        (file: "field-finish-excerpt", title: "RunState Excerpt")
    ]

    private enum RecognitionError: LocalizedError {
        case microphoneDenied, missingTrack, catalogUnavailable, captureEnded

        var errorDescription: String? {
            switch self {
            case .microphoneDenied:
                return "Allow microphone access for RunState in iPhone Settings to try this test."
            case .missingTrack:
                return "One of the two test tracks is missing from this field build."
            case .catalogUnavailable:
                return "RunState couldn’t prepare the two test tracks for recognition."
            case .captureEnded:
                return "Song recognition ended. You can start the test again."
            }
        }
    }

    private var attempt: UUID?
    private var capture: SHManagedSession?

    static func makeCatalog(bundle: Bundle = .main) async throws -> SHCustomCatalog {
        let catalog = SHCustomCatalog()
        for track in tracks {
            try Task.checkCancellation()
            guard let url = bundle.url(forResource: track.file, withExtension: "mp3") else {
                throw RecognitionError.missingTrack
            }
            // The asset API handles MP3 decoding and conversion to the format ShazamKit needs.
            let signature = try await SHSignatureGenerator.signature(from: AVURLAsset(url: url))
            try catalog.addReferenceSignature(signature, representing: [SHMediaItem(properties: [.title: track.title])])
        }
        return catalog
    }

    func listen(onReady: @escaping @MainActor () -> Void,
                onMatch: @escaping @MainActor (String) -> Void) async throws {
        stop()
        try Task.checkCancellation()
        let id = UUID()
        attempt = id
        defer { stopIfCurrent(id) }

        let granted = await AVAudioApplication.requestRecordPermission()
        try checkCurrent(id)
        guard granted else { throw RecognitionError.microphoneDenied }

        let catalog: SHCustomCatalog
        do {
            catalog = try await Self.makeCatalog()
        } catch {
            try checkCurrent(id)
            throw (error as? RecognitionError) ?? RecognitionError.catalogUnavailable
        }
        try checkCurrent(id)
        let session = SHManagedSession(catalog: catalog)
        capture = session
        // An old prepare() may return after a new test starts. Release only this attempt's session.
        defer { session.cancel() }

        // Cancel the managed recording even if the next result never arrives while locked.
        try await withTaskCancellationHandler {
            try checkCurrent(id)
            await session.prepare()
            try checkCurrent(id)
            onReady()
            try checkCurrent(id)
            for await result in session.results {
                try checkCurrent(id)
                switch result {
                case .match(let match):
                    if let title = match.mediaItems.first?.title {
                        onMatch(title)
                    }
                case .noMatch:
                    continue
                case .error(let error, _):
                    throw error
                @unknown default:
                    throw RecognitionError.captureEnded
                }
            }
            try checkCurrent(id)
            throw RecognitionError.captureEnded
        } onCancel: {
            Task { @MainActor [weak self] in self?.stopIfCurrent(id) }
        }
    }

    private func checkCurrent(_ id: UUID) throws {
        try Task.checkCancellation()
        guard attempt == id else { throw CancellationError() }
    }

    private func stopIfCurrent(_ id: UUID) {
        if attempt == id { stop() }
    }

    func stop() {
        attempt = nil
        capture?.cancel()
        capture = nil
    }
}
#endif
