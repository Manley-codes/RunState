import Foundation

enum HostedSpokenAction: Equatable { case generate, replay, loading, unavailable }
enum SpokenVoice: Equatable { case recorded, device }

struct SpokenReflectionState: Equatable {
    let hosted: HostedSpokenAction
    let playing: SpokenVoice?
    let message: String?
}

/// Only the outcome and the credential identifier are written to the private journal.
/// A sent request with no receipt might already have used the voice provider.
struct SpokenReflectionRequestRecord: Codable, Equatable {
    enum Outcome: String, Codable { case sent, received }
    let credentialID: UUID
    let outcome: Outcome
}

struct SpokenReflectionWork {
    struct Audio {
        let runID: UUID
        let text: String
        let credentialID: UUID
        let data: Data
    }
    struct Request {
        let runID: UUID
        let token: UUID
        let revision: Int
    }
    struct Intent {
        let runID: UUID
        let token: UUID
        var playing: SpokenVoice?
    }
    var audio: Audio?
    var request: Request?
    var intent: Intent?
    var issue: (id: UUID, text: String)?
}

@MainActor
extension RunTracker {
    func spokenReflectionState(for run: RecordedRun) -> SpokenReflectionState? {
        guard run.finishedAt != nil, let answer = run.writtenReflection?.saved, !answer.text.isEmpty else { return nil }
        let id = run.id
        let playing = spokenWork.intent?.runID == id ? spokenWork.intent?.playing : nil
        let issue = spokenWork.issue.flatMap { $0.id == id ? $0.text : nil }
        func state(_ action: HostedSpokenAction, _ reason: String? = nil) -> SpokenReflectionState {
            .init(hosted: action, playing: playing, message: issue ?? reason)
        }
        guard !running else { return state(.unavailable) }
        if let request = spokenWork.request, request.runID == id {
            return state(spokenWork.intent?.token == request.token ? .loading : .unavailable)
        }
        if let audio = spokenWork.audio, audio.runID == id, audio.text == answer.text,
           reflectionConnection.credentialID == audio.credentialID {
            return state(.replay)
        }
        guard !answer.isTestData else { return state(.unavailable) }
        guard run.spokenRequest?.outcome != .sent else {
            return state(.unavailable, "RunState couldn’t confirm the previous voice request. Use the iPhone voice to hear your reflection.")
        }
        guard canWrite, spokenWork.request == nil, reflectionConnection.isEnabled,
              let credentialID = reflectionConnection.credentialID else { return state(.unavailable) }
        if let bound = answer.credentialID ?? run.spokenRequest?.credentialID, bound != credentialID {
            return state(.unavailable, "The RunState voice for this reflection uses its original access code.")
        }
        return state(.generate)
    }

    /// An explicit tap is the only path to the hosted speech service.
    func playSpokenReflection(for id: UUID) async {
        guard !running, let run = journal.history.first(where: { $0.id == id }),
              let answer = run.writtenReflection?.saved, let state = spokenReflectionState(for: run),
              state.playing == nil else { return }
        switch state.hosted {
        case .replay:
            if let audio = spokenWork.audio {
                beginSpokenPlayback(for: id, text: answer.text, audio: audio.data)
            }
        case .generate:
            guard let credentials = try? reflectionConnection.credentials() else { return }
            guard credentials.id == reflectionConnection.credentialID else { return }
            stopSpokenReflection()
            let token = UUID()
            let revision = reflectionConnection.revision
            spokenWork.issue = nil
            spokenWork.request = .init(runID: id, token: token, revision: revision)
            spokenWork.intent = .init(runID: id, token: token)
            defer {
                if spokenWork.request?.token == token { spokenWork.request = nil }
                if spokenWork.intent?.token == token && spokenWork.intent?.playing == nil { spokenWork.intent = nil }
            }
            do {
                let audio = try await speechService.synthesize(text: answer.text, runID: id, accessCode: credentials.code) {
                    guard self.ownsSpokenRequest(for: id, answer: answer, token: token, revision: revision),
                          self.spokenWork.intent?.token == token, !Task.isCancelled else { throw CancellationError() }
                    try self.storeSpokenRequest(.init(credentialID: credentials.id, outcome: .sent), for: id, answer: answer)
                }
                guard ownsSpokenRequest(for: id, answer: answer, token: token, revision: revision) else { return }
                guard !audio.isEmpty, audio.count <= ReflectionSpeechService.audioLimit else { throw ReflectionSpeechServiceError.unavailable }
                spokenWork.audio = .init(runID: id, text: answer.text, credentialID: credentials.id, data: audio)
                // If storage filled during the request, the earlier .sent still prevents
                // another charge after relaunch. The audio already here can still play.
                try? storeSpokenRequest(.init(credentialID: credentials.id, outcome: .received), for: id, answer: answer)
                // Leaving the screen or choosing the device voice never starts late audio.
                if spokenWork.intent?.token == token && !running {
                    beginSpokenPlayback(for: id, text: answer.text, audio: audio)
                }
            } catch {
                guard ownsSpokenRequest(for: id, answer: answer, token: token, revision: revision) else { return }
                let failure = error as? ReflectionSpeechServiceError
                let attempted = journal.history.first { $0.id == id }?.spokenRequest?.outcome == .sent
                if attempted, failure == .invalidCode || failure == .accessDenied {
                    // Both responses reject the request before the provider can be called.
                    try? storeSpokenRequest(run.spokenRequest, for: id, answer: answer)
                }
                guard spokenWork.intent?.token == token, !(error is CancellationError) else { return }
                if journal.history.first(where: { $0.id == id })?.spokenRequest?.outcome != .sent {
                    spokenWork.issue = (id, error.localizedDescription)
                }
            }
        case .loading, .unavailable:
            return
        }
    }

    func speakOnDevice(for id: UUID) {
        guard !running, let run = journal.history.first(where: { $0.id == id && $0.finishedAt != nil }),
              let answer = run.writtenReflection?.saved, !answer.text.isEmpty else { return }
        beginSpokenPlayback(for: id, text: answer.text, audio: nil)
    }

    private func ownsSpokenRequest(for id: UUID, answer: SavedWrittenReflection, token: UUID, revision: Int) -> Bool {
        spokenWork.request?.token == token && reflectionConnection.revision == revision
            && journal.history.contains { $0.id == id && $0.writtenReflection?.saved == answer }
    }

    private func beginSpokenPlayback(for id: UUID, text: String, audio: Data?) {
        stopSpokenReflection()
        voice.stop()
        let token = UUID()
        spokenWork.issue = nil
        spokenWork.intent = .init(runID: id, token: token, playing: audio == nil ? .device : .recorded)
        let ended: (ReflectionVoiceResult) -> Void = { [weak self] result in
            guard let self, self.spokenWork.intent?.token == token else { return }
            self.spokenWork.intent = nil
            switch result {
            case .finished: break
            case .interrupted: self.spokenWork.issue = (id, "Playback was interrupted. Tap to listen again.")
            case .failed: self.spokenWork.issue = (id, "This voice couldn’t finish playing.")
            }
        }
        do {
            if let audio { try reflectionPlayer.play(audio: audio, onEnd: ended) }
            else { try reflectionPlayer.speak(text: text, onEnd: ended) }
        } catch {
            ended(.failed)
        }
    }

    /// A dispatched server request may finish quietly and supply a later explicit replay.
    func stopSpokenReflection(for id: UUID? = nil) {
        guard let intent = spokenWork.intent, id == nil || intent.runID == id else { return }
        spokenWork.intent = nil
        if intent.playing != nil { reflectionPlayer.stop() }
    }

    func removeSpokenReflection(for id: UUID) {
        stopSpokenReflection(for: id)
        if spokenWork.audio?.runID == id { spokenWork.audio = nil }
        if spokenWork.request?.runID == id { spokenWork.request = nil }
        if spokenWork.issue?.id == id { spokenWork.issue = nil }
    }

    func spokenConnectionChanged(to revision: Int) {
        guard reflectionConnection.revision == revision else { return }
        if let request = spokenWork.request, request.revision != revision {
            if spokenWork.intent?.token == request.token { stopSpokenReflection(for: request.runID) }
            spokenWork.request = nil
        }
        if let audio = spokenWork.audio, reflectionConnection.credentialID != audio.credentialID {
            if spokenWork.intent?.playing == .recorded { stopSpokenReflection(for: audio.runID) }
            spokenWork.audio = nil
        }
    }
}

#if DEBUG && targetEnvironment(simulator)
struct SimulatorReflectionSpeechService: ReflectionSpeechServing {
    let audio: Data?
    func synthesize(text: String, runID: UUID, accessCode: String,
                    willSend: @escaping @MainActor () throws -> Void) async throws -> Data {
        guard let audio else { throw ReflectionSpeechServiceError.voiceUnavailable }
        try await willSend()
        return audio
    }
}
#endif
