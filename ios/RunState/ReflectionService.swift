import Foundation

/// The only run facts shared with the reflection writer. A request is created from a
/// saved activity once and persisted before its first send so all retries use the same facts.
struct WrittenReflectionRequest: Codable, Equatable {
    struct Metrics: Codable, Equatable {
        let distanceMeters: Double?
        let source: String
        let coverage: String
        let displayUnit: String
    }

    struct Evidence: Codable, Equatable {
        let officialStartEpochMillis: Int64
        let finishEpochMillis: Int64
        let startTimezoneId: String
        let activeDurationMillis: Int64?
        let metrics: Metrics
        let preRunEnergy: String
        // The iPhone Spotify shortcut does not observe songs. An absent musicContext
        // is interpreted as UNKNOWN by the server, including on its older versions.
    }

    let requestId: String
    let runId: String
    let evidence: Evidence

    init(run: RecordedRun, requestID: UUID = UUID()) throws {
        guard let zone = run.startTimezoneID, TimeZone(identifier: zone) != nil else {
            throw ReflectionServiceError.missingStartZone
        }
        guard let finish = run.finishedAt,
              let startMS = Self.milliseconds(run.startedAt.timeIntervalSince1970),
              let finishMS = Self.milliseconds(finish.timeIntervalSince1970), finishMS >= startMS,
              run.meters.isFinite, run.meters >= 0 else { throw ReflectionServiceError.invalidRun }
        // Zero on this device can also mean that no GPS fix was obtained.
        let hasDistance = run.meters > 0
        let complete = hasDistance && run.splitProgress != nil && !run.hasPartialDistance(at: run.activeSeconds)
        let elapsed = finishMS - startMS
        let measured = Self.milliseconds(run.activeSeconds)
        let active = complete && run.pausedWithCompleteTime == true ? measured.flatMap { $0 <= elapsed ? $0 : nil } : nil
        let energy = run.preRunEnergy?.uppercased() ?? ""
        requestId = requestID.uuidString.lowercased()
        runId = run.id.uuidString.lowercased()
        evidence = Evidence(officialStartEpochMillis: startMS, finishEpochMillis: finishMS,
                            startTimezoneId: zone, activeDurationMillis: active,
                            metrics: Metrics(distanceMeters: hasDistance ? run.meters : nil, source: "GPS",
                                             coverage: hasDistance ? (complete ? "COMPLETE" : "PARTIAL") : "UNAVAILABLE",
                                             displayUnit: "MILES"),
                            preRunEnergy: ["LOW", "MODERATE", "HIGH"].contains(energy) ? energy : "UNKNOWN")
    }

    private static func milliseconds(_ seconds: Double) -> Int64? {
        let ms = (seconds * 1_000).rounded()
        guard ms.isFinite, ms >= 0, ms < Double(Int64.max) else { return nil }
        return Int64(ms)
    }
}

struct WrittenReflectionAnswers: Codable, Equatable {
    let spent: String
    let feelingGood: String
    let poweredUp: String
    let noSelection: String
    let isTestData: Bool

    func text(for energy: PostRunEnergy?) -> String {
        switch energy {
        case .spent: return spent
        case .feelingGood: return feelingGood
        case .poweredUp: return poweredUp
        case .notSure, nil: return noSelection
        }
    }
}

enum ReflectionServiceError: LocalizedError, Equatable {
    case missingStartZone, invalidRun, invalidCode, preflightUnavailable, accessDenied, trialAvailable
    case replayUnsupported, replayUnavailable, unreadableResponse, deliveryFailed(Int?)

    /// A network failure after sharing the run may mean a provider already completed it.
    var canRetryAsNewRequest: Bool {
        switch self {
        case .invalidCode, .preflightUnavailable, .accessDenied, .trialAvailable: return true
        default: return false
        }
    }

    var errorDescription: String? {
        switch self {
        case .missingStartZone: return "This run was saved without its starting time zone. Its local recap is still available."
        case .invalidRun: return "This run doesn’t have the saved timing needed for a written reflection."
        case .invalidCode: return "Check your personal RunState access code in Run Settings."
        case .accessDenied: return "This code doesn’t have an available reflection for this run."
        case .trialAvailable: return "Your reflection preview hasn’t been started."
        case .preflightUnavailable: return "RunState couldn’t check reflection access. Your run details were kept on this iPhone."
        case .replayUnsupported: return "This version of the reflection service can’t recover an interrupted request. Your run is saved. A second reflection won’t be requested."
        case .replayUnavailable: return "There’s no saved answer yet. If your request was recent, you can check again later."
        case .unreadableResponse: return "The service returned an unreadable reflection. Your run is saved."
        case .deliveryFailed(let status):
            switch status {
            case 401: return "The service rejected your access code. Check Run Settings."
            case 403: return "The service didn’t grant this reflection request."
            case 429: return "The service has reached its request limit. Check for a saved reflection later."
            case 502, 503, 504: return "The reflection service is unavailable. You can check for a saved reflection later."
            default: return "RunState couldn’t retrieve the reflection. Your run is saved."
            }
        }
    }
}

protocol WrittenReflectionServing {
    func generate(_ request: WrittenReflectionRequest, accessCode: String, replayOnly: Bool) async throws -> WrittenReflectionAnswers
}

#if DEBUG && targetEnvironment(simulator)
/// UI tests exercise the real consent, journal and presentation flow with an isolated sample answer.
struct SimulatorWrittenReflectionService: WrittenReflectionServing {
    let answers: WrittenReflectionAnswers?
    func generate(_ request: WrittenReflectionRequest, accessCode: String,
                  replayOnly: Bool) async throws -> WrittenReflectionAnswers {
        guard let answers, answers.isTestData else { throw ReflectionServiceError.preflightUnavailable }
        return answers
    }
}
#endif

final class ReflectionService: WrittenReflectionServing {
    typealias Transport = (URLRequest) async throws -> (Data, HTTPURLResponse)

    private let transport: Transport
    static let origin = URL(string: "https://runstate-reflections.onrender.com")!
    static let responseLimit = 64 * 1_024

    init() { transport = { try await Self.send($0, limit: Self.responseLimit) } }
    init(transport: @escaping Transport) { self.transport = transport }

    static func validAccessCode(_ value: String) -> Bool {
        let bytes = value.utf8
        guard (43...128).contains(bytes.count), !value.lowercased().hasPrefix("sk-ant-") else { return false }
        return bytes.allSatisfy { byte in
            (65...90).contains(byte) || (97...122).contains(byte) || (48...57).contains(byte) || byte == 45 || byte == 95
        }
    }

    func generate(_ request: WrittenReflectionRequest, accessCode: String, replayOnly: Bool) async throws -> WrittenReflectionAnswers {
        guard Self.validAccessCode(accessCode) else { throw ReflectionServiceError.invalidCode }
        let body = try JSONEncoder().encode(request)
        // These endpoints only inspect access or reserve the run ID. None receives run facts or calls a writer.
        do {
            try await checkAccess(runID: request.runId, code: accessCode, replayOnly: replayOnly)
        } catch is CancellationError {
            throw CancellationError()
        } catch let error as ReflectionServiceError {
            throw error
        } catch {
            throw ReflectionServiceError.preflightUnavailable
        }

        try Task.checkCancellation()
        do {
            let result = try await exchange("api/reflections/candidates", code: accessCode, body: body, replayOnly: replayOnly)
            // Remote authentication runs before the backend reads run facts or dispatches a writer.
            // On older deployments this POST is the first authenticated call.
            if result.response.statusCode == 401 { throw ReflectionServiceError.invalidCode }
            if replayOnly && Self.isReplayMiss(result) { throw ReflectionServiceError.replayUnavailable }
            guard result.response.statusCode == 200 else { throw ReflectionServiceError.deliveryFailed(result.response.statusCode) }
            return try Self.answers(from: result.data, request: request)
        } catch is CancellationError {
            throw CancellationError()
        } catch let error as ReflectionServiceError {
            throw error
        } catch {
            throw ReflectionServiceError.deliveryFailed(nil)
        }
    }

    private func checkAccess(runID: String, code: String, replayOnly: Bool) async throws {
        let health = try await exchange("health")
        guard health.response.statusCode == 200,
              let status = try? JSONDecoder().decode([String: String].self, from: health.data), status["status"] == "ok"
        else { throw ReflectionServiceError.preflightUnavailable }
        switch health.response.value(forHTTPHeaderField: "X-RunState-Reflection-Access") {
        case nil:
            // Older owner servers authenticate the candidates request itself and cannot promise a free replay.
            if replayOnly { throw ReflectionServiceError.replayUnsupported }
            return
        case "1": break
        default: throw ReflectionServiceError.preflightUnavailable
        }

        let result = try await exchange("api/reflections/access", code: code)
        let access = try Self.access(from: result)
        if replayOnly {
            let probe = try JSONSerialization.data(withJSONObject: ["runId": runID, "replayOnly": true])
            let replay = try await exchange("api/reflections/run-access", code: code, body: probe)
            if Self.isReplayMiss(replay) { throw ReflectionServiceError.replayUnavailable }
            _ = try Self.access(from: replay)
            return
        }
        if access.tier == "owner" { return }
        if access.status == "available" { throw ReflectionServiceError.trialAvailable }
        guard access.status == "active" else { throw ReflectionServiceError.accessDenied }
        // Even when remaining is zero, a run could already hold the last slot.
        let reservation = try JSONEncoder().encode(["runId": runID])
        let grant = try await exchange("api/reflections/run-access", code: code, body: reservation)
        let admitted = try Self.access(from: grant)
        guard access.tier == admitted.tier, admitted.status == "active" else { throw ReflectionServiceError.accessDenied }
    }

    private typealias Reply = (data: Data, response: HTTPURLResponse)

    private static func isReplayMiss(_ reply: Reply) -> Bool {
        guard reply.response.statusCode == 403,
              let body = try? JSONDecoder().decode([String: [String: String]].self, from: reply.data)
        else { return false }
        return body["error"]?["code"] == "reflection_replay_unavailable"
    }

    private struct Access: Decodable {
        let tier: String
        let status: String
        let remaining: Int?
    }

    private static func access(from reply: Reply) throws -> Access {
        switch reply.response.statusCode {
        case 401: throw ReflectionServiceError.invalidCode
        case 403: throw ReflectionServiceError.accessDenied
        case 200: break
        default: throw ReflectionServiceError.preflightUnavailable
        }
        let access = try JSONDecoder().decode(Access.self, from: reply.data)
        switch (access.tier, access.status) {
        case ("owner", "owner") where access.remaining == nil,
             ("tracking", "tracking") where access.remaining == 0:
            return access
        case ("trial", "available"), ("trial", "active"), ("trial", "expired"), ("pilot", "active"):
            if let remaining = access.remaining, remaining >= 0 { return access }
        default: break
        }
        throw ReflectionServiceError.preflightUnavailable
    }

    private func exchange(_ path: String, code: String? = nil, body: Data? = nil, replayOnly: Bool = false) async throws -> Reply {
        try Task.checkCancellation()
        let url = Self.origin.appendingPathComponent(path)
        var request = URLRequest(url: url, cachePolicy: .reloadIgnoringLocalCacheData,
                                 timeoutInterval: path == "api/reflections/candidates" ? 105 : 15)
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        if let code { request.setValue("Bearer " + code, forHTTPHeaderField: "Authorization") }
        if let body {
            request.httpMethod = "POST"
            request.httpBody = body
            request.setValue("application/json; charset=utf-8", forHTTPHeaderField: "Content-Type")
        }
        if replayOnly { request.setValue("1", forHTTPHeaderField: "X-RunState-Replay-Only") }
        let (data, response) = try await transport(request)
        guard response.url == url, data.count <= Self.responseLimit else { throw URLError(.badServerResponse) }
        if response.statusCode == 200 && response.mimeType?.lowercased() != "application/json" {
            throw URLError(.cannotParseResponse)
        }
        return (data, response)
    }

    static func send(_ request: URLRequest, limit: Int) async throws -> (Data, HTTPURLResponse) {
        let config = URLSessionConfiguration.ephemeral
        config.urlCache = nil
        config.httpShouldSetCookies = false
        config.httpCookieStorage = nil
        config.urlCredentialStorage = nil
        config.timeoutIntervalForResource = request.timeoutInterval + 10
        let session = URLSession(configuration: config, delegate: ReflectionRedirectPolicy(), delegateQueue: nil)
        defer { session.invalidateAndCancel() }
        let (bytes, response) = try await session.bytes(for: request)
        guard let http = response as? HTTPURLResponse else { throw URLError(.badServerResponse) }
        // Inspect the stable 403 error code only. Provider messages are never shown or logged.
        guard http.statusCode == 200 || (http.statusCode == 403 && http.mimeType == "application/json") else {
            return (Data(), http)
        }
        guard http.expectedContentLength <= Int64(limit) else { throw URLError(.dataLengthExceedsMaximum) }
        var data = Data()
        if http.expectedContentLength > 0 { data.reserveCapacity(Int(http.expectedContentLength)) }
        for try await byte in bytes {
            guard data.count < limit else { throw URLError(.dataLengthExceedsMaximum) }
            data.append(byte)
        }
        return (data, http)
    }

    private static func answers(from data: Data, request: WrittenReflectionRequest) throws -> WrittenReflectionAnswers {
        guard let json = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any],
              Set(json.keys) == Set(["requestId", "runId", "generationMode", "candidates"]),
              json["requestId"] as? String == request.requestId,
              json["runId"] as? String == request.runId,
              let mode = json["generationMode"] as? String, ["FAKE", "ANTHROPIC"].contains(mode),
              let values = json["candidates"] as? [String: String],
              Set(values.keys) == Set(["spent", "feeling_good", "powered_up", "no_selection"]),
              values.values.allSatisfy({ !$0.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }),
              let spent = values["spent"], let good = values["feeling_good"], let powered = values["powered_up"],
              let unsure = values["no_selection"] else { throw ReflectionServiceError.unreadableResponse }
        return WrittenReflectionAnswers(spent: spent, feelingGood: good, poweredUp: powered, noSelection: unsure,
                                        isTestData: mode == "FAKE")
    }
}

final class ReflectionRedirectPolicy: NSObject, URLSessionTaskDelegate {
    func urlSession(_ session: URLSession, task: URLSessionTask, willPerformHTTPRedirection response: HTTPURLResponse,
                    newRequest request: URLRequest, completionHandler: @escaping (URLRequest?) -> Void) {
        completionHandler(nil)
    }
}
