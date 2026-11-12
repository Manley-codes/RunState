import Foundation
import XCTest
@testable import RunState

final class ReflectionSpeechServiceTests: XCTestCase {
    private let code = String(repeating: "r", count: 43)
    private let runID = UUID()
    private let text = "  Your exact reflection.\nA second line — unchanged.  "
    private let audio = Data([0xff, 0xfb, 0x90, 0x64])

    private func response(to request: URLRequest, status: Int = 200,
                          headers: [String: String], url: URL? = nil) throws -> HTTPURLResponse {
        try XCTUnwrap(HTTPURLResponse(url: try XCTUnwrap(url ?? request.url), statusCode: status,
                                      httpVersion: "HTTP/1.1", headerFields: headers))
    }

    private func health(_ request: URLRequest, voice: String? = "1",
                        access: String? = nil) throws -> (Data, HTTPURLResponse) {
        XCTAssertEqual(request.url?.absoluteString, "https://runstate-reflections.onrender.com/health")
        XCTAssertEqual(request.httpMethod, "GET")
        XCTAssertNil(request.value(forHTTPHeaderField: "Authorization"))
        XCTAssertNil(request.value(forHTTPHeaderField: "X-RunState-Run-Id"))
        XCTAssertNil(request.httpBody)
        var fields = ["Content-Type": "application/json"]
        fields["X-RunState-Reflection-Voice"] = voice
        fields["X-RunState-Reflection-Access"] = access
        return (Data(#"{"status":"ok"}"#.utf8), try response(to: request, headers: fields))
    }

    @MainActor
    func testLegacyAndScopedServersReceiveOnlyTheExactSelectedTextAndSavedRunID() async throws {
        for explicitAccess in [false, true] {
            var order: [String] = []
            let service = ReflectionSpeechService { request in
                if request.httpMethod == "GET" {
                    order.append("health")
                    return try self.health(request, access: explicitAccess ? "1" : nil)
                }
                order.append("speech")
                XCTAssertEqual(request.httpMethod, "POST")
                XCTAssertEqual(request.url?.absoluteString, "https://runstate-reflections.onrender.com/api/reflections/speech")
                XCTAssertEqual(request.value(forHTTPHeaderField: "Authorization"), "Bearer " + self.code)
                XCTAssertEqual(request.value(forHTTPHeaderField: "X-RunState-Run-Id"), self.runID.uuidString.lowercased())
                XCTAssertNil(request.value(forHTTPHeaderField: "X-RunState-Replay-Only"))
                XCTAssertEqual(request.value(forHTTPHeaderField: "Content-Type"), "application/json; charset=utf-8")
                XCTAssertEqual(request.value(forHTTPHeaderField: "Accept"), "audio/mpeg")
                let body = try XCTUnwrap(request.httpBody)
                XCTAssertEqual(try JSONDecoder().decode([String: String].self, from: body), ["text": self.text])
                if explicitAccess {
                    let attachment = XCTAttachment(data: body, uniformTypeIdentifier: "public.json")
                    attachment.name = "RunState-SpeechRequest"
                    attachment.lifetime = .keepAlways
                    self.add(attachment)
                }
                var headers = ["Content-Type": "audio/mpeg"]
                if explicitAccess { headers["Content-Length"] = String(self.audio.count) }
                return (self.audio, try self.response(to: request, headers: headers))
            }
            let result = try await service.synthesize(text: text, runID: runID, accessCode: code) { order.append("persist") }
            XCTAssertEqual(result, audio)
            XCTAssertEqual(order, ["health", "persist", "speech"])
        }
    }

    @MainActor
    func testPreflightAndPersistenceCanPreventSendingTheText() async throws {
        for scenario in ["no voice", "unknown voice", "unhealthy", "redirect", "oversize", "persistence"] {
            var calls = 0
            var attempts = 0
            let service = ReflectionSpeechService { request in
                calls += 1
                let healthy = try self.health(request)
                switch scenario {
                case "no voice": return try self.health(request, voice: nil)
                case "unknown voice": return try self.health(request, voice: "2")
                case "unhealthy": return (Data(#"{"status":"error"}"#.utf8), healthy.1)
                case "redirect":
                    return (healthy.0, try self.response(to: request, headers: [:], url: URL(string: "https://example.invalid/health")))
                case "oversize": return (Data(count: ReflectionService.responseLimit + 1), healthy.1)
                default: return healthy
                }
            }
            do {
                _ = try await service.synthesize(text: text, runID: runID, accessCode: code) {
                    attempts += 1
                    throw CancellationError()
                }
                XCTFail(scenario)
            } catch {
                if scenario == "persistence" { XCTAssertTrue(error is CancellationError) }
                else { XCTAssertEqual(error as? ReflectionSpeechServiceError, .voiceUnavailable) }
            }
            XCTAssertEqual(calls, 1)
            XCTAssertEqual(attempts, scenario == "persistence" ? 1 : 0)
        }
    }

    @MainActor
    func testInvalidCodeBlankTextAndOversizeUTF16NeverConnect() async throws {
        let service = ReflectionSpeechService { _ in
            XCTFail("Unexpected network request")
            throw URLError(.unsupportedURL)
        }
        for (text, code, expected) in [("   ", self.code, ReflectionSpeechServiceError.invalidText),
                                      (String(repeating: "🏃", count: 2_001), self.code, .invalidText),
                                      (self.text, "invalid", .invalidCode)] {
            do {
                _ = try await service.synthesize(text: text, runID: runID, accessCode: code) { XCTFail("Sent invalid input") }
                XCTFail("Expected rejection")
            } catch { XCTAssertEqual(error as? ReflectionSpeechServiceError, expected) }
        }
    }

    @MainActor
    func testMalformedDeniedInterruptedAndOversizeAudioNeverTriggersSecondPost() async throws {
        for scenario in ["401", "403", "503", "empty", "type", "oversize", "length", "redirect", "lost"] {
            var sends = 0
            let service = ReflectionSpeechService { request in
                if request.httpMethod == "GET" { return try self.health(request) }
                sends += 1
                if scenario == "lost" { throw URLError(.networkConnectionLost) }
                var data = self.audio
                var url = request.url
                var headers = ["Content-Type": "audio/mpeg"]
                switch scenario {
                case "empty": data = Data()
                case "type": headers["Content-Type"] = "application/json"
                case "oversize": data = Data(count: ReflectionSpeechService.audioLimit + 1)
                case "length": headers["Content-Length"] = String(data.count + 1)
                case "redirect": url = URL(string: "https://example.invalid/speech")
                default: break
                }
                return (data, try self.response(to: request, status: Int(scenario) ?? 200, headers: headers, url: url))
            }
            do {
                _ = try await service.synthesize(text: text, runID: runID, accessCode: code) {}
                XCTFail(scenario)
            } catch {
                let expected: ReflectionSpeechServiceError
                switch scenario {
                case "401": expected = .invalidCode
                case "403": expected = .accessDenied
                default: expected = .unavailable
                }
                XCTAssertEqual(error as? ReflectionSpeechServiceError, expected, scenario)
            }
            XCTAssertEqual(sends, 1)
        }
    }
}
