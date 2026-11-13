import CoreFoundation
import Foundation

/// Candidate approved for this response. A server-provided song identity alone never authorizes playback.
struct ReflectionRecording {
    let id: String
    let title: String
    let artist: String
    let album: String?
    let durationMillis: Int
    let loadedAtFinish: Bool
}

struct ReflectionMusicSnippet: Codable, Equatable {
    let cueId: String
    let source: String
    let sourceId: String
    let title: String
    let artist: String
    let album: String?
    let trackDurationMillis: Int
    let startPositionMillis: Int
    let durationMillis: Int
    let selectionMethod: String
    let version: Int

    private enum CodingKeys: String, CodingKey, CaseIterable {
        case cueId, source, sourceId, trackDurationMillis, startPositionMillis, durationMillis, selectionMethod, version
        case title = "trackTitle", artist = "artistName", album = "albumName"
    }

    static func validated(in response: [String: Any], for recording: ReflectionRecording) -> Self? {
        guard let fields = response["songSnippet"] as? [String: Any],
              Set(fields.keys).isSubset(of: Set(CodingKeys.allCases.map(\.rawValue))),
              fields["albumName"] == nil || fields["albumName"] is String,
              ["trackDurationMillis", "startPositionMillis", "durationMillis", "version"].allSatisfy({
                  guard let number = fields[$0] as? NSNumber, CFGetTypeID(number) != CFBooleanGetTypeID()
                  else { return false }
                  return !["f", "d"].contains(String(cString: number.objCType))
              }),
              let bytes = try? JSONSerialization.data(withJSONObject: fields),
              let clip = try? JSONDecoder().decode(Self.self, from: bytes),
              clip.matches(recording), clip.validSourceAndRange else { return nil }
        if let acknowledgement = response["songCueId"] {
            guard acknowledgement as? String == clip.cueId, clip.selectionMethod != "RECORDING_OPENING_FALLBACK"
            else { return nil }
        } else if clip.version != 2 {
            return nil
        }
        return clip
    }

    private func matches(_ recording: ReflectionRecording) -> Bool {
        func normalized(_ value: String) -> String {
            value.split(whereSeparator: { $0.isWhitespace }).joined(separator: " ").lowercased()
        }
        let names = [title, artist] + (album.map { [$0] } ?? [])
        guard names.allSatisfy({ (1...200).contains($0.utf16.count) && !$0.trimmingCharacters(in: .whitespaces).isEmpty
            && $0.rangeOfCharacter(from: .controlCharacters) == nil }) else { return false }
        return normalized(title) == normalized(recording.title) && normalized(artist) == normalized(recording.artist)
            && album == recording.album && trackDurationMillis == recording.durationMillis
    }

    private var validSourceAndRange: Bool {
        guard (1...2).contains(version), (1_000...3_600_000).contains(trackDurationMillis),
              (2_000...10_000).contains(durationMillis), startPositionMillis >= 0,
              startPositionMillis <= trackDurationMillis - durationMillis - (version == 2 ? 2_000 : 0)
        else { return false }
        let cuePrefix: String
        switch selectionMethod {
        case "RECORDING_OPENING_FALLBACK":
            guard version == 2, source == "RUNSTATE_RECORDING", sourceId == "opening",
                  startPositionMillis == 0, durationMillis <= 8_000 else { return false }
            cuePrefix = "recording_v2"
        case "TITLE_HOOK", "REPEATED_HOOK", "TIMED_LINE_WINDOW":
            let digits = sourceId.utf8
            guard source == "LRCLIB", (1...19).contains(digits.count), digits.first != 48,
                  digits.allSatisfy({ (48...57).contains($0) }), version == 2 || selectionMethod != "TIMED_LINE_WINDOW"
            else { return false }
            cuePrefix = "lrclib_v\(version)"
        default: return false
        }
        return cueId == "\(cuePrefix)_\(sourceId)_\(startPositionMillis)_\(startPositionMillis + durationMillis)"
    }
}

#if RUNSTATE_FIELD_TOOLS
enum MusicFinishSample {
    static func response(in bundle: Bundle = .main) throws -> WrittenReflectionAnswers {
        guard let file = bundle.url(forResource: "music-finish-response", withExtension: "json")
        else { throw ReflectionServiceError.unreadableResponse }
        let recording = ReflectionRecording(id: "1", title: "Evening Stride", artist: "RunState Studio",
                                            album: nil, durationMillis: 30_000, loadedAtFinish: false)
        let answer = try ReflectionService.answers(from: Data(contentsOf: file),
                                                   requestID: "00000000-0000-0000-0000-000000000001",
                                                   runID: "00000000-0000-0000-0000-000000000002",
                                                   recordings: [recording])
        guard let clip = answer.snippet else { throw ReflectionServiceError.unreadableResponse }
        // The fixture exercises the real server response shape with entirely invented data.
        return WrittenReflectionAnswers(spent: answer.spent, feelingGood: answer.feelingGood,
                                        poweredUp: answer.poweredUp, noSelection: answer.noSelection,
                                        isTestData: true, selectedSongID: answer.selectedSongID, snippet: clip)
    }
}
#endif
