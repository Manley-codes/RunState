import Foundation

struct SavedWrittenReflection: Codable, Equatable {
    let text: String
    let isTestData: Bool
}

struct PendingWrittenReflection: Codable, Equatable {
    let request: WrittenReflectionRequest
    let selectedEnergy: PostRunEnergy?
    let credentialID: UUID
    // Persist true before dispatch. A crash cannot tell whether the writer already ran.
    var replayOnly: Bool
    var firstAttemptAt: Date?
    var recoveryEnded: WrittenRecoveryEnd?
}

enum WrittenRecoveryEnd: String, Codable {
    case unsupportedService, noSavedAnswer

    var message: String {
        switch self {
        case .unsupportedService: return ReflectionServiceError.replayUnsupported.localizedDescription
        case .noSavedAnswer: return "The 24-hour recovery window has passed, and the service has no saved reflection for this run. Your run and check-in are still available."
        }
    }
}

struct RunWrittenReflection: Codable, Equatable {
    var pending: PendingWrittenReflection?
    var saved: SavedWrittenReflection?
}

enum WrittenReflectionState: Equatable {
    case available
    case needsStartTimeZone
    case notConnected
    case preparing
    case retry(String)
    case unavailable(String)
    case saved(text: String, isTestData: Bool)
}

enum RunStartZoneError: LocalizedError {
    case invalid, alreadyRecorded
    var errorDescription: String? {
        switch self {
        case .invalid: return "Choose the time zone where this run started."
        case .alreadyRecorded: return "This run already has a recorded starting time zone."
        }
    }
}

/// Only the in-flight work and its feedback are transient. On relaunch the journal alone
/// determines whether a new write is allowed or an existing answer may be recovered.
struct WrittenReflectionWork {
    struct Job {
        let token: UUID
        let task: Task<Void, Never>
    }
    var jobs: [UUID: Job] = [:]
    var issues: [UUID: WrittenReflectionState] = [:]
    var answersToSave: [UUID: SavedWrittenReflection] = [:]
}

extension RunTracker {
    func writtenReflectionState(for run: RecordedRun) -> WrittenReflectionState {
        if let saved = run.writtenReflection?.saved {
            return .saved(text: saved.text, isTestData: saved.isTestData)
        }
        if let ended = run.writtenReflection?.pending?.recoveryEnded { return .unavailable(ended.message) }
        guard run.finishedAt != nil else { return .unavailable("Finish and save your run first.") }
        if run.startTimezoneID == nil && run.writtenReflection?.pending == nil {
            return .needsStartTimeZone
        }
        guard canWrite else { return .unavailable("RunState couldn’t open the run journal.") }
        if reflectionWork.jobs[run.id] != nil { return .preparing }
        guard reflectionConnection.isEnabled && reflectionConnection.hasAccessCode else { return .notConnected }
        if let issue = reflectionWork.issues[run.id] { return issue }
        if let pending = run.writtenReflection?.pending {
            return .retry(pending.replayOnly
                          ? "Check for the answer from your previous request. The server keeps saved answers for up to 24 hours."
                          : "Your previous request stopped before the run was sent.")
        }
        return .available
    }

    /// Called only by an explicit tap. A second tap joins the operation already owned by the tracker.
    @MainActor
    func requestWrittenReflection(for id: UUID) async {
        if let job = reflectionWork.jobs[id] {
            await job.task.value
            return
        }
        guard let run = journal.history.first(where: { $0.id == id }) else { return }
        switch writtenReflectionState(for: run) {
        case .available, .retry: break
        default: return
        }
        let token = UUID()
        let revision = reflectionConnection.revision
        let task = Task { @MainActor in
            defer {
                if reflectionWork.jobs[id]?.token == token { reflectionWork.jobs.removeValue(forKey: id) }
            }
            guard !Task.isCancelled, reflectionConnection.revision == revision,
                  let latest = journal.history.first(where: { $0.id == id }) else { return }
            await performWrittenRequest(for: latest, token: token, revision: revision)
        }
        reflectionWork.jobs[id] = .init(token: token, task: task)
        await task.value
    }

    @MainActor
    private func performWrittenRequest(for run: RecordedRun, token: UUID, revision: Int) async {
        let id = run.id
        let credentials: ReflectionCredentials
        do { credentials = try reflectionConnection.credentials() }
        catch {
            reflectionWork.issues[id] = .retry("RunState couldn’t read the saved access code. Check Run Settings, then try again.")
            return
        }
        let previous = run.writtenReflection?.pending
        if let previous, previous.replayOnly && previous.credentialID != credentials.id {
            reflectionWork.issues[id] = .unavailable("This reflection was requested with another access code. Reconnect with the original code to recover it.")
            return
        }
        if let answer = reflectionWork.answersToSave[id], let previous {
            saveWrittenAnswer(answer, for: id, pending: previous)
            return
        }

        let pending: PendingWrittenReflection
        do {
            if let original = previous {
                pending = PendingWrittenReflection(request: original.request, selectedEnergy: original.selectedEnergy,
                                                   credentialID: credentials.id, replayOnly: true,
                                                   firstAttemptAt: original.replayOnly ? original.firstAttemptAt : Date())
            } else {
                let request = try WrittenReflectionRequest(run: run)
                pending = PendingWrittenReflection(request: request,
                                                   selectedEnergy: run.firstPostRunEnergy ?? run.postRunEnergy,
                                                   credentialID: credentials.id, replayOnly: true, firstAttemptAt: Date())
            }
        } catch {
            reflectionWork.issues[id] = .unavailable("This run doesn’t have the recorded details needed for a written reflection.")
            return
        }
        do {
            if previous != pending {
                try storeWrittenReflection(.init(pending: pending), for: id, replacing: previous)
            }
        } catch {
            reflectionWork.issues[id] = .retry(SavedRunError.storage.localizedDescription)
            return
        }
        let replayOnly = previous?.replayOnly == true
        do {
            let answers = try await reflectionService.generate(pending.request, accessCode: credentials.code, replayOnly: replayOnly)
            guard ownsWrittenResponse(for: id, token: token, revision: revision) else { return }
            let answer = SavedWrittenReflection(text: answers.text(for: pending.selectedEnergy), isTestData: answers.isTestData)
            saveWrittenAnswer(answer, for: id, pending: pending)
        } catch {
            guard ownsWrittenResponse(for: id, token: token, revision: revision) else { return }
            let serviceError = error as? ReflectionServiceError
            if replayOnly, let ended = recoveryEnd(for: serviceError, pending: pending) {
                var stopped = pending
                stopped.recoveryEnded = ended
                do { try storeWrittenReflection(.init(pending: stopped), for: id, replacing: pending) }
                catch {
                    reflectionWork.issues[id] = .retry(SavedRunError.storage.localizedDescription)
                    return
                }
                reflectionWork.issues.removeValue(forKey: id)
                return
            }
            if !replayOnly && serviceError?.canRetryAsNewRequest == true {
                var unsent = pending
                unsent.replayOnly = false
                do { try storeWrittenReflection(.init(pending: unsent), for: id, replacing: pending) }
                catch {
                    reflectionWork.issues[id] = .retry(SavedRunError.storage.localizedDescription)
                    return
                }
            }
            // Never put a raw server body, bearer token, or URLSession diagnostic in the UI.
            let text = serviceError?.localizedDescription ?? "The connection was interrupted. You can check for a saved answer."
            reflectionWork.issues[id] = .retry(text)
        }
    }

    private func ownsWrittenResponse(for id: UUID, token: UUID, revision: Int) -> Bool {
        !Task.isCancelled && reflectionWork.jobs[id]?.token == token && reflectionConnection.revision == revision
    }

    private func recoveryEnd(for error: ReflectionServiceError?, pending: PendingWrittenReflection) -> WrittenRecoveryEnd? {
        if error == .replayUnsupported { return .unsupportedService }
        // A replay lookup misses while the original writer is still working. Even after 24 hours,
        // ask the server first: its retention clock starts when the answer is saved.
        guard error == .replayUnavailable, let attempted = pending.firstAttemptAt,
              Date().timeIntervalSince(attempted) >= 24 * 60 * 60 else { return nil }
        return .noSavedAnswer
    }

    private func saveWrittenAnswer(_ answer: SavedWrittenReflection, for id: UUID, pending: PendingWrittenReflection) {
        do {
            try storeWrittenReflection(.init(saved: answer), for: id, replacing: pending)
            reflectionWork.answersToSave.removeValue(forKey: id)
            reflectionWork.issues.removeValue(forKey: id)
        } catch {
            reflectionWork.answersToSave[id] = answer
            reflectionWork.issues[id] = .retry("Your answer arrived, but it couldn’t be saved on this iPhone. Check your storage and try saving it again.")
        }
    }

    func cancelWrittenReflectionWork(for id: UUID? = nil) {
        if let id {
            reflectionWork.jobs.removeValue(forKey: id)?.task.cancel()
            reflectionWork.issues.removeValue(forKey: id)
            reflectionWork.answersToSave.removeValue(forKey: id)
        } else {
            for job in reflectionWork.jobs.values { job.task.cancel() }
            reflectionWork = WrittenReflectionWork()
        }
    }
}
