import Foundation
import XCTest
@testable import RunState

final class ReflectionServiceTests: XCTestCase {
    private let code = String(repeating: "a", count: 43)
    private let start = Date(timeIntervalSince1970: 1_800_000_000)

    private func completeRun() -> RecordedRun {
        var run = RecordedRun(id: UUID(), startedAt: start, meters: 100, activeSeconds: 30,
                              pausedWithCompleteTime: true, finishedAt: start.addingTimeInterval(45),
                              preRunEnergy: "Moderate", shoePairID: UUID(), postRunEnergy: .spent,
                              postRunEffort: 8, route: RunRoute(), startTimezoneID: "America/Chicago")
        var progress = RunSplitProgress()
        progress.startSegment(at: 0)
        for second in stride(from: 0, through: 30, by: 5) {
            progress.observe(meters: Double(second) * 100 / 30, at: Double(second))
        }
        progress.endSegment(at: 30)
        run.splitProgress = progress
        return run
    }

    private func object(_ data: Data?) throws -> [String: Any] {
        let value = try JSONSerialization.jsonObject(with: XCTUnwrap(data))
        return try XCTUnwrap(value as? [String: Any])
    }

    func testOnlyDurableSupportedFactsAreSentAndFrozenAcrossReload() throws {
        let run = completeRun()
        let request = try WrittenReflectionRequest(run: run)
        let json = try JSONEncoder().encode(request)
        let attachment = XCTAttachment(data: json, uniformTypeIdentifier: "public.json")
        attachment.name = "RunState-ReflectionRequest"
        attachment.lifetime = .keepAlways
        add(attachment)
        XCTAssertEqual(try JSONDecoder().decode(WrittenReflectionRequest.self, from: json), request)
        let root = try object(json)
        XCTAssertEqual(Set(root.keys), ["requestId", "runId", "evidence"])
        XCTAssertEqual(request.runId, run.id.uuidString.lowercased())
        XCTAssertEqual(request.requestId, request.requestId.lowercased())
        let evidence = try XCTUnwrap(root["evidence"] as? [String: Any])
        XCTAssertEqual(Set(evidence.keys), ["officialStartEpochMillis", "finishEpochMillis", "activeDurationMillis",
                                            "startTimezoneId", "metrics", "preRunEnergy"])
        XCTAssertEqual(evidence["activeDurationMillis"] as? Int, 30_000)
        XCTAssertEqual(evidence["startTimezoneId"] as? String, "America/Chicago")
        XCTAssertEqual(evidence["preRunEnergy"] as? String, "MODERATE")
        XCTAssertEqual(request.evidence.metrics.coverage, "COMPLETE")
        XCTAssertEqual(request.evidence.metrics.distanceMeters, 100)
    }

    func testPartialUnknownOrInconsistentMeasurementsCannotBecomeFullPaceEvidence() throws {
        var run = completeRun()
        run.splitProgress = nil
        var evidence = try WrittenReflectionRequest(run: run).evidence
        XCTAssertEqual(evidence.metrics.coverage, "PARTIAL")
        XCTAssertEqual(evidence.metrics.distanceMeters, 100)
        XCTAssertNil(evidence.activeDurationMillis)
        run.meters = 0
        evidence = try WrittenReflectionRequest(run: run).evidence
        XCTAssertEqual(evidence.metrics.coverage, "UNAVAILABLE")
        XCTAssertNil(evidence.metrics.distanceMeters)
        var implausibleClock = completeRun()
        implausibleClock.finishedAt = start.addingTimeInterval(20)
        XCTAssertNil(try WrittenReflectionRequest(run: implausibleClock).evidence.activeDurationMillis)
        run.startTimezoneID = nil
        XCTAssertThrowsError(try WrittenReflectionRequest(run: run))
        var active = completeRun()
        active.finishedAt = nil
        XCTAssertThrowsError(try WrittenReflectionRequest(run: active))
    }

    private typealias StubReply = (Data, HTTPURLResponse)

    private func reply(_ request: URLRequest, _ json: [String: Any], status: Int = 200,
                       headers: [String: String] = [:], url: URL? = nil) throws -> StubReply {
        var fields = ["Content-Type": "application/json; charset=utf-8"]
        fields.merge(headers) { _, new in new }
        let response = try XCTUnwrap(HTTPURLResponse(url: try XCTUnwrap(url ?? request.url), statusCode: status,
                                                     httpVersion: "HTTP/1.1", headerFields: fields))
        return (try JSONSerialization.data(withJSONObject: json), response)
    }

    private func success(_ request: WrittenReflectionRequest, mode: String = "ANTHROPIC") -> [String: Any] {
        ["requestId": request.requestId, "runId": request.runId, "generationMode": mode,
         "candidates": ["spent": "  Spent.  ", "feeling_good": "Good.", "powered_up": "Up.", "no_selection": "Finish."]]
    }

    @MainActor
    func testLegacyOwnerFirstRequestWorksAndReplayNeverSendsRunOrCode() async throws {
        let request = try WrittenReflectionRequest(run: completeRun())
        var paths: [String] = []
        let service = ReflectionService { http in
            let url = try XCTUnwrap(http.url)
            XCTAssertEqual(url.scheme, "https")
            XCTAssertEqual(url.host, "runstate-reflections.onrender.com")
            paths.append(url.path)
            if url.path == "/health" {
                XCTAssertNil(http.value(forHTTPHeaderField: "Authorization"))
                XCTAssertNil(http.httpBody)
                return try self.reply(http, ["status": "ok"])
            }
            XCTAssertEqual(http.httpMethod, "POST")
            XCTAssertEqual(http.value(forHTTPHeaderField: "Authorization"), "Bearer " + self.code)
            XCTAssertEqual(try JSONDecoder().decode(WrittenReflectionRequest.self, from: XCTUnwrap(http.httpBody)), request)
            return try self.reply(http, self.success(request, mode: "FAKE"))
        }
        let answers = try await service.generate(request, accessCode: code, replayOnly: false)
        XCTAssertEqual(paths, ["/health", "/api/reflections/candidates"])
        XCTAssertTrue(answers.isTestData)
        XCTAssertEqual(answers.text(for: .spent), "  Spent.  ")
        XCTAssertEqual(answers.text(for: .feelingGood), "Good.")
        XCTAssertEqual(answers.text(for: .poweredUp), "Up.")
        XCTAssertEqual(answers.text(for: .notSure), "Finish.")
        XCTAssertEqual(answers.text(for: nil), "Finish.")
        paths = []
        do {
            _ = try await service.generate(request, accessCode: code, replayOnly: true)
            XCTFail("A legacy service cannot guarantee a free replay")
        } catch {
            XCTAssertEqual(error as? ReflectionServiceError, .replayUnsupported)
        }
        XCTAssertEqual(paths, ["/health"])
    }

    @MainActor
    func testRecoveryRecognizesOnlyTheServersExplicitMissingAnswerCode() async throws {
        let request = try WrittenReflectionRequest(run: completeRun())
        for (status, reason, expected) in [(403, "reflection_replay_unavailable", ReflectionServiceError.replayUnavailable),
                                           (403, "reflection_access_required", .accessDenied),
                                           (503, "reflection_storage_unavailable", .preflightUnavailable)] {
            var paths: [String] = []
            let service = ReflectionService { http in
                let path = try XCTUnwrap(http.url?.path)
                paths.append(path)
                if path == "/health" {
                    return try self.reply(http, ["status": "ok"], headers: ["X-RunState-Reflection-Access": "1"])
                }
                if path == "/api/reflections/access" {
                    return try self.reply(http, ["tier": "owner", "status": "owner"])
                }
                return try self.reply(http, ["error": ["code": reason]], status: status)
            }
            do {
                _ = try await service.generate(request, accessCode: code, replayOnly: true)
                XCTFail("The saved answer was unavailable")
            } catch let error as ReflectionServiceError {
                XCTAssertEqual(error, expected)
            }
            XCTAssertEqual(paths, ["/health", "/api/reflections/access", "/api/reflections/run-access"])
        }
    }

    @MainActor
    func testLegacyUnauthorizedCodeCanBeCorrectedWithTheSameFrozenRequest() async throws {
        let request = try WrittenReflectionRequest(run: completeRun())
        let wrong = String(repeating: "b", count: 43)
        var sent: [WrittenReflectionRequest] = []
        let service = ReflectionService { http in
            if http.httpMethod == "GET" { return try self.reply(http, ["status": "ok"]) }
            sent.append(try JSONDecoder().decode(WrittenReflectionRequest.self, from: XCTUnwrap(http.httpBody)))
            if http.value(forHTTPHeaderField: "Authorization") == "Bearer " + wrong {
                return try self.reply(http, ["error": wrong], status: 401)
            }
            return try self.reply(http, self.success(request))
        }
        do {
            _ = try await service.generate(request, accessCode: wrong, replayOnly: false)
            XCTFail("The service rejected this code")
        } catch let error as ReflectionServiceError {
            XCTAssertEqual(error, .invalidCode)
            XCTAssertTrue(error.canRetryAsNewRequest)
            XCTAssertFalse(error.localizedDescription.contains(wrong))
        }
        let answers = try await service.generate(request, accessCode: code, replayOnly: false)
        XCTAssertFalse(answers.isTestData)
        XCTAssertEqual(sent, [request, request])
    }

    @MainActor
    func testGrantAndFreeReplayProbeUseOnlyRunIDBeforeTheCandidatesCall() async throws {
        let request = try WrittenReflectionRequest(run: completeRun())
        for replayOnly in [false, true] {
            var paths: [String] = []
            let service = ReflectionService { http in
                let path = try XCTUnwrap(http.url?.path)
                paths.append(path)
                if path == "/health" {
                    return try self.reply(http, ["status": "ok"], headers: ["X-RunState-Reflection-Access": "1"])
                }
                XCTAssertEqual(http.value(forHTTPHeaderField: "Authorization"), "Bearer " + self.code)
                let access: [String: Any] = ["tier": replayOnly ? "tracking" : "pilot", "status": replayOnly ? "tracking" : "active", "remaining": 0]
                if path == "/api/reflections/access" {
                    XCTAssertEqual(http.httpMethod, "GET")
                    return try self.reply(http, access)
                }
                if path == "/api/reflections/run-access" {
                    let grant = try self.object(http.httpBody)
                    XCTAssertEqual(Set(grant.keys), Set(replayOnly ? ["runId", "replayOnly"] : ["runId"]))
                    XCTAssertEqual(grant["runId"] as? String, request.runId)
                    XCTAssertEqual(grant["replayOnly"] as? Bool, replayOnly ? true : nil)
                    return try self.reply(http, access)
                }
                XCTAssertEqual(http.value(forHTTPHeaderField: "X-RunState-Replay-Only"), replayOnly ? "1" : nil)
                return try self.reply(http, self.success(request))
            }
            let result = try await service.generate(request, accessCode: code, replayOnly: replayOnly)
            XCTAssertFalse(result.isTestData)
            XCTAssertEqual(paths, ["/health", "/api/reflections/access", "/api/reflections/run-access", "/api/reflections/candidates"])
        }
    }

    @MainActor
    func testTrackingAndAvailableTrialCanNeverStartProvidersOrActivateTrialImplicitly() async throws {
        let request = try WrittenReflectionRequest(run: completeRun())
        for tier in ["tracking", "trial"] {
            var paths: [String] = []
            let service = ReflectionService { http in
                let path = try XCTUnwrap(http.url?.path)
                paths.append(path)
                if path == "/health" {
                    return try self.reply(http, ["status": "ok"], headers: ["X-RunState-Reflection-Access": "1"])
                }
                return try self.reply(http, ["tier": tier, "status": tier == "tracking" ? "tracking" : "available", "remaining": 0])
            }
            do {
                _ = try await service.generate(request, accessCode: code, replayOnly: false)
                XCTFail("No writing is authorized")
            } catch let error as ReflectionServiceError {
                XCTAssertEqual(error, tier == "tracking" ? .accessDenied : .trialAvailable)
                XCTAssertTrue(error.canRetryAsNewRequest)
            }
            XCTAssertEqual(paths, ["/health", "/api/reflections/access"])
        }
    }

    @MainActor
    func testWrongRunWrongKindOrIncompleteAnswerAndLostDeliveryAreReplayOnly() async throws {
        let request = try WrittenReflectionRequest(run: completeRun())
        let changed: [[String: Any]] = [["runId": UUID().uuidString.lowercased()], ["generationMode": "OTHER"],
                                        ["requestId": UUID().uuidString.lowercased()],
                                        ["candidates": ["spent": "Only one"]], ["songCueId": "unauthorized"]]
        for change in changed {
            let service = ReflectionService { http in
                if http.httpMethod == "GET" { return try self.reply(http, ["status": "ok"]) }
                return try self.reply(http, self.success(request).merging(change) { _, replacement in replacement })
            }
            do {
                _ = try await service.generate(request, accessCode: code, replayOnly: false)
                XCTFail("An unbound or incomplete answer should not be saved")
            } catch let error as ReflectionServiceError {
                XCTAssertEqual(error, .unreadableResponse)
                XCTAssertFalse(error.canRetryAsNewRequest)
            }
        }
        let service = ReflectionService { http in
            if http.httpMethod == "GET" { return try self.reply(http, ["status": "ok"]) }
            throw URLError(.timedOut)
        }
        do {
            _ = try await service.generate(request, accessCode: code, replayOnly: false)
            XCTFail("Lost response")
        } catch let error as ReflectionServiceError {
            XCTAssertFalse(error.canRetryAsNewRequest)
        }
    }

    @MainActor
    func testAccessValidationAndRedirectPolicyProtectTheCode() async throws {
        XCTAssertTrue(ReflectionService.validAccessCode(code))
        for invalid in [String(code.dropLast()), code + "\n", code + "é", code + " ", "SK-AnT-" + code,
                        String(repeating: "a", count: 129)] {
            XCTAssertFalse(ReflectionService.validAccessCode(invalid))
        }
        let request = try WrittenReflectionRequest(run: completeRun())
        var calls = 0
        let service = ReflectionService { http in
            calls += 1
            XCTAssertNil(http.value(forHTTPHeaderField: "Authorization"))
            return try self.reply(http, ["status": "ok"], url: URL(string: "https://example.invalid/health"))
        }
        do {
            _ = try await service.generate(request, accessCode: code, replayOnly: false)
            XCTFail("The service changed destinations")
        } catch let error as ReflectionServiceError {
            XCTAssertEqual(error, .preflightUnavailable)
            XCTAssertTrue(error.canRetryAsNewRequest)
        }
        XCTAssertEqual(calls, 1)
        // Exercise the same URLSession redirect delegate as the real transport without a connection.
        let target = try XCTUnwrap(URL(string: "https://example.invalid/redirect"))
        let redirect = try XCTUnwrap(HTTPURLResponse(url: target, statusCode: 307, httpVersion: nil, headerFields: nil))
        let session = URLSession(configuration: .ephemeral)
        defer { session.invalidateAndCancel() }
        let task = session.dataTask(with: target)
        var rejected = false
        ReflectionRedirectPolicy().urlSession(session, task: task, willPerformHTTPRedirection: redirect,
                                               newRequest: URLRequest(url: target)) { next in rejected = next == nil }
        XCTAssertTrue(rejected)
    }
}
