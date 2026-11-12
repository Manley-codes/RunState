import Combine
import CryptoKit
import Foundation
import Security

/// Only this opaque connection ID is needed to bind a saved retry to its original credential.
struct ReflectionCredentials: Codable {
    let id: UUID
    let code: String

    init(code: String) {
        self.code = code
        // The host is fixed. Re-entering the same code may recover its pending request.
        let input = Data(("RunState written reflection connection v1\n" + code).utf8)
        let bytes = Array(SHA256.hash(data: input).prefix(16))
        id = UUID(uuid: (bytes[0], bytes[1], bytes[2], bytes[3], bytes[4], bytes[5], bytes[6], bytes[7],
                         bytes[8], bytes[9], bytes[10], bytes[11], bytes[12], bytes[13], bytes[14], bytes[15]))
    }
}

protocol ReflectionCredentialVault {
    func read() throws -> ReflectionCredentials?
    func write(_ value: ReflectionCredentials) throws
    func remove() throws
}

private enum ConnectionError: LocalizedError {
    case missing, invalid, storage

    var errorDescription: String? {
        switch self {
        case .missing: return "Enter your RunState access code to use written reflections."
        case .invalid: return "Use the private access code issued for RunState. It contains 43–128 letters, numbers, dashes or underscores."
        case .storage: return "RunState couldn’t access the saved code. Unlock your iPhone and try again."
        }
    }
}

struct KeychainReflectionVault: ReflectionCredentialVault {
    private let service: String

    init(service: String = "com.runstate.mobile.fieldtest.reflections") {
        self.service = service
    }

    private var item: [String: Any] {
        [kSecClass as String: kSecClassGenericPassword,
         kSecAttrService as String: service, kSecAttrAccount as String: "runner"]
    }

    func read() throws -> ReflectionCredentials? {
        var query = item
        query[kSecReturnData as String] = true
        query[kSecMatchLimit as String] = kSecMatchLimitOne
        var result: CFTypeRef?
        let status = SecItemCopyMatching(query as CFDictionary, &result)
        if status == errSecItemNotFound { return nil }
        guard status == errSecSuccess, let data = result as? Data,
              let value = try? JSONDecoder().decode(ReflectionCredentials.self, from: data),
              ReflectionService.validAccessCode(value.code) else { throw ConnectionError.storage }
        return value
    }

    func write(_ value: ReflectionCredentials) throws {
        let attributes: [String: Any] = [
            kSecValueData as String: try JSONEncoder().encode(value),
            kSecAttrAccessible as String: kSecAttrAccessibleWhenUnlockedThisDeviceOnly
        ]
        let status = SecItemUpdate(item as CFDictionary, attributes as CFDictionary)
        if status == errSecSuccess { return }
        guard status == errSecItemNotFound else { throw ConnectionError.storage }
        var newItem = item
        newItem.merge(attributes) { _, new in new }
        guard SecItemAdd(newItem as CFDictionary, nil) == errSecSuccess else { throw ConnectionError.storage }
    }

    func remove() throws {
        let status = SecItemDelete(item as CFDictionary)
        guard status == errSecSuccess || status == errSecItemNotFound else { throw ConnectionError.storage }
    }
}

final class ReflectionConnection: ObservableObject {
    @Published private(set) var isEnabled = false
    @Published private(set) var hasAccessCode = false
    @Published private(set) var revision = 0
    @Published private(set) var storageMessage: String?
    private let preferences: UserDefaults
    private let vault: ReflectionCredentialVault
    private let enabledKey = "run.writtenReflectionsEnabled"

    init(preferences: UserDefaults, vault: ReflectionCredentialVault = KeychainReflectionVault()) {
        self.preferences = preferences
        self.vault = vault
        refresh()
    }

    func refresh() {
        let previousEnabled = isEnabled
        let previousSaved = hasAccessCode
        do {
            hasAccessCode = try vault.read() != nil
            isEnabled = hasAccessCode && preferences.bool(forKey: enabledKey)
            storageMessage = nil
        } catch {
            isEnabled = false
            hasAccessCode = false
            storageMessage = ConnectionError.storage.localizedDescription
        }
        if previousEnabled != isEnabled || previousSaved != hasAccessCode { revision += 1 }
    }

    func credentials() throws -> ReflectionCredentials {
        guard isEnabled, let saved = try vault.read() else { throw ConnectionError.missing }
        return saved
    }

    func save(code: String) throws {
        let trimmed = code.trimmingCharacters(in: .whitespacesAndNewlines)
        guard ReflectionService.validAccessCode(trimmed) else { throw ConnectionError.invalid }
        let existing = try vault.read()
        if existing?.code != trimmed {
            try vault.write(ReflectionCredentials(code: trimmed))
        }
        hasAccessCode = true
        isEnabled = true
        storageMessage = nil
        preferences.set(true, forKey: enabledKey)
        revision += 1
    }

    func setEnabled(_ enabled: Bool) throws {
        if enabled {
            guard try vault.read() != nil else { throw ConnectionError.missing }
            hasAccessCode = true
        }
        preferences.set(enabled, forKey: enabledKey)
        isEnabled = enabled
        revision += 1
    }

    func forget() throws {
        try setEnabled(false)
        try vault.remove()
        hasAccessCode = false
        storageMessage = nil
    }
}
