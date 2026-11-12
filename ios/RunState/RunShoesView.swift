import SwiftUI

struct RunShoesView: View {
    @ObservedObject var tracker: RunTracker
    @Environment(\.dismiss) private var dismiss
    @Environment(\.dynamicTypeSize) private var typeSize
    @State private var search = ""
    @State private var showArchived = false
    @State private var lastArchive: (receipt: ShoeArchiveUndo, name: String)?
    @State private var saveError: String?
    @State private var newPairID = UUID()

    private static let catalog = [
        (brand: "ASICS", model: "Novablast 6"), (brand: "Nike", model: "Pegasus 42"),
        (brand: "HOKA", model: "Clifton 11"), (brand: "Brooks", model: "Ghost 18"),
        (brand: "Saucony", model: "Ride 19"), (brand: "HOKA", model: "Speedgoat 7")
    ]

    var body: some View {
        NavigationStack {
            List {
                if !tracker.canWrite {
                    Text(tracker.errorMessage ?? "Your saved shoes could not be opened.")
                }
                if let lastArchive {
                    Section {
                        Text("\(lastArchive.name) archived.")
                        Button("Undo") {
                            perform {
                                try tracker.undoArchive(lastArchive.receipt)
                                self.lastArchive = nil
                            }
                        }
                        .accessibilityIdentifier("shoeUndo")
                    }
                }
                Section("Your shoes") {
                    let active = tracker.journal.shoePairs.filter { !$0.isArchived && matches($0.fullName) }
                    if active.isEmpty && tracker.canWrite {
                        Text(query.isEmpty ? "Add your first pair below." : "No saved shoes match your search.")
                            .foregroundStyle(.secondary)
                    }
                    ForEach(active) { savedRow($0) }
                    Button("Run without tracking shoes") {
                        perform { try tracker.selectShoe(nil); dismiss() }
                    }
                    .accessibilityIdentifier("shoeNoTracking")
                }
                Section {
                    let choices = Self.catalog.filter { choice in
                        matches(choice.brand + " " + choice.model) && !tracker.journal.shoePairs.contains { shoe in
                            shoe.brand.caseInsensitiveCompare(choice.brand) == .orderedSame &&
                            shoe.model.caseInsensitiveCompare(choice.model) == .orderedSame
                        }
                    }
                    ForEach(choices, id: \.model) { choice in
                        Button { add(brand: choice.brand, model: choice.model, nickname: nil) } label: {
                            HStack(spacing: 12) {
                                shoeLabel(brand: choice.brand, model: choice.model)
                                Spacer()
                                Image(systemName: "plus").accessibilityHidden(true)
                            }
                        }
                        .accessibilityLabel("Add \(choice.brand) \(choice.model)")
                        .accessibilityIdentifier("shoeCatalog_\(choice.brand)_\(choice.model.replacingOccurrences(of: " ", with: ""))")
                    }
                    if choices.isEmpty && !query.isEmpty {
                        Text("No catalog match.").foregroundStyle(.secondary)
                    }
                    NavigationLink {
                        CustomShoeForm(add: add)
                    } label: {
                        Label("Add a custom pair", systemImage: "plus")
                    }
                    .accessibilityIdentifier("shoeCustom")
                } header: {
                    Text("Browse shoes")
                } footer: {
                    Text("Mileage tracking begins with your next saved GPS run.")
                }
                if tracker.journal.shoePairs.contains(where: \.isArchived) {
                    Section {
                        Button(showArchived ? "Hide archived shoes" : "Show archived shoes") { showArchived.toggle() }
                            .accessibilityIdentifier("shoeShowArchived")
                        if showArchived {
                            let archived = tracker.journal.shoePairs.filter { $0.isArchived && matches($0.fullName) }
                            if archived.isEmpty { Text("No archived shoes match your search.").foregroundStyle(.secondary) }
                            ForEach(archived) { shoe in
                                Button {
                                    perform { try tracker.restoreShoe(shoe.id); dismiss() }
                                } label: {
                                    VStack(alignment: .leading, spacing: 4) {
                                        Text(shoe.fullName).foregroundStyle(.primary)
                                        Text(tracker.journal.shoeMileageText(for: shoe.id)).font(.caption).foregroundStyle(.secondary)
                                        Text("Restore and select").font(.subheadline)
                                    }
                                }
                                .accessibilityIdentifier("shoeRestore_\(shoe.id)")
                            }
                        }
                    }
                }
            }
            .disabled(!tracker.canWrite)
            .scrollContentBackground(.hidden)
            .background(Color(red: 0.97, green: 0.98, blue: 0.95))
            .searchable(text: $search, placement: .navigationBarDrawer(displayMode: .always), prompt: "Search brand or model")
            .navigationTitle("Add Shoes")
            .toolbar {
                ToolbarItem(placement: .confirmationAction) { Button("Done") { dismiss() } }
            }
        }
        .tint(Color(red: 0.27, green: 0.35, blue: 0.21))
        .preferredColorScheme(.light)
        .alert("Couldn’t save the shoe change", isPresented: Binding(
            get: { saveError != nil }, set: { if !$0 { saveError = nil } }
        )) {
            Button("OK", role: .cancel) {}
        } message: {
            Text(saveError ?? "")
        }
    }

    private func savedRow(_ shoe: ShoePair) -> some View {
        let selected = tracker.journal.selectedShoe?.id == shoe.id
        return HStack(spacing: 8) {
            Button {
                perform { try tracker.selectShoe(shoe.id); dismiss() }
            } label: {
                HStack(spacing: 6) {
                    shoeLabel(brand: shoe.brand, model: shoe.model, nickname: shoe.nickname,
                              mileage: tracker.journal.shoeMileageText(for: shoe.id))
                    Spacer(minLength: 0)
                    if selected { Image(systemName: "checkmark").accessibilityHidden(true) }
                }
                .contentShape(Rectangle())
            }
            .buttonStyle(.borderless)
            .accessibilityAddTraits(selected ? .isSelected : [])
            .accessibilityIdentifier("shoeSelect_\(shoe.id)")
            Button {
                perform {
                    let receipt = try tracker.archiveShoe(shoe.id)
                    lastArchive = (receipt, shoe.displayName)
                }
            } label: {
                Image(systemName: "archivebox").frame(width: 44, height: 44)
            }
            .buttonStyle(.borderless)
            .accessibilityLabel("Archive \(shoe.fullName)")
            .accessibilityIdentifier("shoeArchive_\(shoe.id)")
        }
        .listRowBackground(selected ? Color(red: 0.91, green: 0.94, blue: 0.87) : Color.white)
    }

    private func shoeLabel(brand: String, model: String, nickname: String? = nil, mileage: String? = nil) -> some View {
        HStack(spacing: 12) {
            if !typeSize.isAccessibilitySize {
                ShoeIllustration(withBacking: false).frame(width: 52, height: 52).accessibilityHidden(true)
            }
            VStack(alignment: .leading, spacing: 3) {
                Text(brand).font(.caption).foregroundStyle(.secondary)
                Text(model).font(.headline).foregroundStyle(.primary)
                if let nickname { Text(nickname).font(.subheadline).foregroundStyle(.secondary) }
                if let mileage { Text(mileage).font(.caption.monospacedDigit()).foregroundStyle(.secondary) }
            }
        }
    }

    private var query: String { search.trimmingCharacters(in: .whitespacesAndNewlines) }

    private func matches(_ name: String) -> Bool {
        query.isEmpty || name.localizedStandardContains(query) ||
            name.replacingOccurrences(of: " · ", with: " ").localizedStandardContains(query)
    }

    private func add(brand: String, model: String, nickname: String?) {
        perform {
            try tracker.addShoe(brand: brand, model: model, nickname: nickname, id: newPairID)
            dismiss()
        }
    }

    private func perform(_ change: () throws -> Void) {
        do { try change() }
        catch { saveError = error.localizedDescription }
    }
}

private struct CustomShoeForm: View {
    let add: (String, String, String?) -> Void
    @State private var brand = ""
    @State private var model = ""
    @State private var nickname = ""

    var body: some View {
        Form {
            Section {
                TextField("Brand", text: $brand).accessibilityIdentifier("shoeBrand")
                TextField("Model", text: $model).accessibilityIdentifier("shoeModel")
                TextField("Nickname (optional)", text: $nickname).accessibilityIdentifier("shoeNickname")
            } footer: {
                Text("A nickname helps distinguish two pairs of the same model.")
            }
            Section {
                Button("Add and select pair") { add(brand, model, nickname) }
                    .disabled(brand.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ||
                              model.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                    .accessibilityIdentifier("shoeAddCustom")
            }
        }
        .textInputAutocapitalization(.words)
        .autocorrectionDisabled()
        .navigationTitle("Custom pair")
        .navigationBarTitleDisplayMode(.inline)
    }
}
