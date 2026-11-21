import Foundation

protocol ReflectionSpeechServing {
    func synthesize(text: String, runID: UUID, accessCode: String,
                    willSend: @escaping @MainActor () throws -> Void) async throws -> Data
}

enum ReflectionSpeechServiceError: LocalizedError, Equatable {
    case invalidText, invalidCode, voiceUnavailable, accessDenied, unavailable

    var errorDescription: String? {
        switch self {
        case .invalidText: return "This reflection can’t be sent to the RunState voice service."
        case .invalidCode: return "Check your RunState access code in Run Settings."
        case .voiceUnavailable: return "The RunState voice is currently unavailable."
        case .accessDenied: return "Your access code doesn’t include the RunState voice for this run."
        case .unavailable: return "RunState couldn’t retrieve the voice recording."
        }
    }
}

final class ReflectionSpeechService: ReflectionSpeechServing {
    static let audioLimit = 2 * 1_024 * 1_024
    private let transport: ReflectionService.Transport

    init() {
        transport = { request in
            let limit = request.httpMethod == "POST" ? ReflectionSpeechService.audioLimit : ReflectionService.responseLimit
            return try await ReflectionService.send(request, limit: limit)
        }
    }

    init(transport: @escaping ReflectionService.Transport) { self.transport = transport }

    func synthesize(text: String, runID: UUID, accessCode: String,
                    willSend: @escaping @MainActor () throws -> Void) async throws -> Data {
        guard !text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty, text.utf16.count <= 4_000
        else { throw ReflectionSpeechServiceError.invalidText }
        guard ReflectionService.validAccessCode(accessCode) else { throw ReflectionSpeechServiceError.invalidCode }
        try Task.checkCancellation()

        // Capability is public. It says a voice is available, not that a repeat POST is free.
        let healthURL = ReflectionService.origin.appendingPathComponent("health")
        var health = URLRequest(url: healthURL, cachePolicy: .reloadIgnoringLocalCacheData, timeoutInterval: 5)
        health.setValue("application/json", forHTTPHeaderField: "Accept")
        let (body, status) = try await exchange(health)
        guard status.url == healthURL, status.statusCode == 200, status.mimeType?.lowercased() == "application/json",
              body.count <= ReflectionService.responseLimit,
              (try? JSONDecoder().decode([String: String].self, from: body))?["status"] == "ok",
              status.value(forHTTPHeaderField: "X-RunState-Reflection-Voice") == "1"
        else { throw ReflectionSpeechServiceError.voiceUnavailable }

        let url = ReflectionService.origin.appendingPathComponent("api/reflections/speech")
        var request = URLRequest(url: url, cachePolicy: .reloadIgnoringLocalCacheData, timeoutInterval: 16)
        request.httpMethod = "POST"
        request.httpBody = try JSONEncoder().encode(["text": text])
        request.setValue("application/json; charset=utf-8", forHTTPHeaderField: "Content-Type")
        request.setValue("audio/mpeg", forHTTPHeaderField: "Accept")
        request.setValue("identity", forHTTPHeaderField: "Accept-Encoding")
        request.setValue("Bearer " + accessCode, forHTTPHeaderField: "Authorization")
        request.setValue(runID.uuidString.lowercased(), forHTTPHeaderField: "X-RunState-Run-Id")
        try Task.checkCancellation()
        try await willSend()
        try Task.checkCancellation()
        let (audio, response) = try await exchange(request)
        guard response.url == url else { throw ReflectionSpeechServiceError.unavailable }
        switch response.statusCode {
        case 401: throw ReflectionSpeechServiceError.invalidCode
        case 403: throw ReflectionSpeechServiceError.accessDenied
        case 200: break
        default: throw ReflectionSpeechServiceError.unavailable
        }
        guard response.mimeType?.lowercased() == "audio/mpeg", !audio.isEmpty, audio.count <= Self.audioLimit
        else { throw ReflectionSpeechServiceError.unavailable }
        if let length = response.value(forHTTPHeaderField: "Content-Length"), Int(length) != audio.count {
            throw ReflectionSpeechServiceError.unavailable
        }
        return audio
    }

    private func exchange(_ request: URLRequest) async throws -> (Data, HTTPURLResponse) {
        do { return try await transport(request) }
        catch {
            if Task.isCancelled || error is CancellationError { throw CancellationError() }
            throw ReflectionSpeechServiceError.unavailable
        }
    }
}
