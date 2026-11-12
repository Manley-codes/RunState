import SwiftUI

struct SavedRunSplitsView: View {
    let run: RecordedRun

    var body: some View {
        let rows = run.mileSplits
        if !rows.isEmpty {
            Section {
                ForEach(rows) { split in
                    let time = Duration.seconds(split.activeSeconds).formatted(.time(pattern: .minuteSecond))
                    LabeledContent {
                        VStack(alignment: .trailing, spacing: 3) {
                            Text(time).monospacedDigit()
                            if let pace = ActiveRunView.paceText(meters: split.meters, activeSeconds: split.activeSeconds) {
                                Text(verbatim: pace).font(.footnote).foregroundStyle(.secondary)
                            }
                        }
                    } label: {
                        if split.isPartial {
                            let miles = split.meters / RunDistance.metersPerMile
                            let distance = miles < 0.01 ? "<0.01" : miles.formatted(.number.precision(.fractionLength(2)))
                            Text("Final \(distance) mi")
                        } else {
                            Text("Mile \(split.number)")
                        }
                    }
                    .accessibilityElement(children: .combine)
                }
            } header: {
                Text("Mile splits")
            } footer: {
                Text("Estimated from recorded GPS. Times exclude pauses.")
            }
        }
    }
}
