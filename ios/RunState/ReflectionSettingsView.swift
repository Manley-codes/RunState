import SwiftUI

struct ReflectionSettingsView: View {
    @ObservedObject var connection: ReflectionConnection
    @Environment(\.scenePhase) private var scenePhase
    @State private var code = ""
    @State private var replacing = false
    @State private var message: String?
    @FocusState private var enteringCode: Bool

    var body: some View {
        Form {
            Section {
                if connection.hasAccessCode {
                    Toggle("Enable written reflections", isOn: Binding(
                        get: { connection.isEnabled },
                        set: { enabled in perform { try connection.setEnabled(enabled) } }
                    ))
                    .accessibilityIdentifier("reflections.enabled")
                }
                Text("Choose Create reflection on a saved run to send its summary to RunState. You can read the saved answer again in History.")
            } footer: {
                Text("RunState’s writing service uses Anthropic. The summary includes timing, time zone, available distance and starting energy. Routes, shoes and Spotify history stay out of this request.")
            }
            Section("Listening") {
                Text("On a saved reflection, choose RunState voice to have ElevenLabs read the text. A new recording may use voice credits. The iPhone voice can read saved reflections offline.")
            }
            Section("RunState access code") {
                if !connection.hasAccessCode || replacing {
                    SecureField("Enter your private code", text: $code)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                        .focused($enteringCode)
                        .accessibilityIdentifier("reflections.accessCode")
                    Button("Save code and enable") {
                        perform {
                            try connection.save(code: code)
                            clearEntry()
                        }
                    }
                    .disabled(code.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                    if replacing {
                        Button("Cancel", action: clearEntry)
                    }
                } else {
                    Button("Replace access code") { replacing = true; enteringCode = true }
                    Button("Remove saved code", role: .destructive) {
                        perform {
                            try connection.forget()
                            clearEntry()
                        }
                    }
                }
            }
            if let error = message ?? connection.storageMessage {
                Text(error).font(.footnote).foregroundStyle(.secondary)
                    .accessibilityIdentifier("reflections.connectionError")
            }
        }
        .navigationTitle("Written reflections")
        .navigationBarTitleDisplayMode(.inline)
        .onAppear { connection.refresh() }
        .onDisappear { clearEntry() }
        .onChange(of: scenePhase) { phase in
            if phase == .active { connection.refresh() }
            if phase == .background { clearEntry() }
        }
    }

    private func perform(_ operation: () throws -> Void) {
        do {
            try operation()
            message = nil
        } catch {
            message = error.localizedDescription
        }
    }

    private func clearEntry() {
        code = ""
        replacing = false
        enteringCode = false
    }
}
