import UIKit

/// Asks iOS for a short period of background time so a reflection request that already
/// started can finish if the runner locks the iPhone or switches apps. Without it, iOS can
/// suspend RunState mid-request and leave the outcome unknown: writing then needs a manual
/// check for the saved answer, and the duplicate-charge guard withholds the RunState voice.
@MainActor
protocol ReflectionBackgroundTime: AnyObject {
    func begin(_ name: String) -> UUID
    func end(_ token: UUID)
}

@MainActor
final class ApplicationBackgroundTime: ReflectionBackgroundTime {
    private var tasks: [UUID: UIBackgroundTaskIdentifier] = [:]

    func begin(_ name: String) -> UUID {
        let token = UUID()
        let task = UIApplication.shared.beginBackgroundTask(withName: name) { [weak self] in
            // iOS ends the extra time here. The request can still finish when RunState returns.
            self?.end(token)
        }
        if task != .invalid { tasks[token] = task }
        return token
    }

    func end(_ token: UUID) {
        guard let task = tasks.removeValue(forKey: token) else { return }
        UIApplication.shared.endBackgroundTask(task)
    }
}
