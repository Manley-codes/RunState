import CoreLocation
import SwiftUI

struct ActiveRunView: View {
    @ObservedObject var tracker: RunTracker
    let onResume: () -> Void
    let onFinish: () -> Void

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.dynamicTypeSize) private var typeSize
    @Environment(\.scenePhase) private var scenePhase
    @State private var confirmingFinish = false
    @State private var shortRunID: UUID?

    private static let ink = Color(red: 245/255, green: 245/255, blue: 247/255)
    private static let label = Color(red: 167/255, green: 170/255, blue: 183/255)
    private static let panel = Color(red: 27/255, green: 29/255, blue: 32/255)
    private static let violet = Color(red: 115/255, green: 83/255, blue: 239/255)
    private static let stopFace = Color(red: 220/255, green: 231/255, blue: 207/255)
    private static let stopIcon = Color(red: 37/255, green: 58/255, blue: 44/255)

    init(tracker: RunTracker, onResume: @escaping () -> Void, onFinish: @escaping () -> Void) {
        self.tracker = tracker
        self.onResume = onResume
        self.onFinish = onFinish
    }

    private var resumeLabel: String {
        if tracker.hasLocationPermission { return "Resume" }
        return tracker.authorization == .notDetermined ? "Enable location" : "Open location settings"
    }

    var body: some View {
        GeometryReader { viewport in
            let width = min(viewport.size.width, 450)
            let unit = width / 393
            ZStack(alignment: .top) {
                RunInstrumentBackground()
                ScrollView {
                    VStack(spacing: 0) {
                        RunInstrumentHeader(status: tracker.running ? "Running" : "Paused",
                                            gps: tracker.running ? tracker.gpsStatus : "GPS paused", unit: unit)
                        if let message = tracker.errorMessage {
                            Text(message)
                                .font(.callout)
                                .foregroundStyle(Color(red: 1, green: 189/255, blue: 159/255))
                                .frame(maxWidth: .infinity, alignment: .leading)
                                .padding(.horizontal, 24 * unit)
                                .padding(.top, 8)
                        }
                        controls(unit: unit)
                            .padding(.horizontal, 24 * unit)
                            .padding(.top, 17 * unit)
                        distanceInstrument(unit: unit)
                            .padding(.top, 12 * unit)
                        pacePanel(unit: unit)
                            .padding(.top, 18 * unit)
                        if !tracker.running {
                            finishControl(unit: unit)
                                .padding(.top, 14 * unit)
                        }
                    }
                    .frame(width: width)
                    .padding(.bottom, 18)
                    .frame(maxWidth: .infinity)
                }
            }
        }
        .foregroundStyle(Self.ink)
        .confirmationDialog("Finish and save this run?", isPresented: $confirmingFinish, titleVisibility: .visible) {
            Button("Finish and save", action: onFinish)
            Button("Cancel", role: .cancel) {}
        }
        .alert("Save this short run?", isPresented: Binding(
            get: { shortRunID != nil }, set: { if !$0 { shortRunID = nil } }
        ), presenting: shortRunID) { id in
            Button("Save run") {
                if tracker.journal.active?.id == id && !tracker.running { onFinish() }
            }
            Button("Discard run", role: .destructive) { tracker.discardShortPausedRun(id) }
            Button("Keep paused", role: .cancel) {}
        } message: { _ in
            Text("You recorded less than 30 seconds of active time. Save it to History, or permanently discard it if you started by accident.")
        }
    }

    private func controls(unit: CGFloat) -> some View {
        let layout = typeSize.isAccessibilitySize ? AnyLayout(VStackLayout(alignment: .leading, spacing: 14)) :
            AnyLayout(HStackLayout(alignment: .center, spacing: 16 * unit))
        return layout {
            Button(action: tracker.running ? tracker.pause : onResume) {
                VStack(spacing: 5) {
                    Image(systemName: tracker.running ? "pause.fill" : "play.fill")
                        .font(.system(size: 25 * unit, weight: .semibold))
                        .foregroundStyle(.white)
                        .frame(width: max(44, 67 * unit), height: max(44, 67 * unit))
                        .background(Self.violet, in: Circle())
                    Text(tracker.running ? "Pause" : resumeLabel).font(.caption)
                        .multilineTextAlignment(.center)
                }
                .frame(minWidth: 74 * unit)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .disabled(!tracker.running && !tracker.canWrite)
            .accessibilityLabel(tracker.running ? "Pause run" : (tracker.hasLocationPermission ? "Resume run" : resumeLabel))

            if !typeSize.isAccessibilitySize {
                Rectangle().fill(Color.white.opacity(0.17))
                    .frame(width: 1, height: 72 * unit).accessibilityHidden(true)
            }
            TimelineView(.animation(minimumInterval: 1, paused: !tracker.running || scenePhase != .active)) { _ in
                let seconds = tracker.activeSeconds
                HStack(alignment: .top, spacing: 9 * unit) {
                    Image(systemName: "stopwatch")
                        .font(.system(size: 23 * unit, weight: .light))
                        .foregroundStyle(Self.label)
                        .padding(.top, 9 * unit)
                    VStack(alignment: .leading, spacing: 3) {
                        Text(verbatim: Self.clockText(seconds))
                            .font(.custom("BarlowCondensed-Medium", size: 43 * unit, relativeTo: .title))
                            .lineLimit(1).minimumScaleFactor(0.5)
                        Text("ACTIVE TIME").font(.caption2).tracking(1.7).foregroundStyle(Self.label)
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .accessibilityElement(children: .ignore)
                .accessibilityLabel("Active time")
                .accessibilityValue(Duration.seconds(seconds).formatted(.units(
                    allowed: [.hours, .minutes, .seconds], width: .wide)))
            }
        }
    }

    private func distanceInstrument(unit: CGFloat) -> some View {
        let miles = (tracker.journal.active?.meters ?? 0) / RunDistance.metersPerMile
        let diameter = (tracker.running ? 336 : 316) * unit
        return ZStack {
            TimelineView(.animation(minimumInterval: 1.0 / 30, paused: !tracker.running || reduceMotion || scenePhase != .active)) { _ in
                let time = reduceMotion ? 1.19 : tracker.activeSeconds
                InstrumentRing(time: tracker.running ? time : max(1.19, time))
            }
            .accessibilityHidden(true)
            VStack(spacing: 0) {
                Text(miles, format: .number.precision(.fractionLength(2)))
                    .font(.system(size: 98 * unit, weight: .semibold))
                    .monospacedDigit().lineLimit(1).minimumScaleFactor(0.4)
                    .frame(width: diameter * 0.70)
                Text("mi").font(.title2)
                Text("DISTANCE").font(.caption2).tracking(3.3)
                    .foregroundStyle(Self.label).padding(.top, 11 * unit)
            }
        }
        .frame(width: diameter, height: diameter)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("Distance")
        .accessibilityValue("\(miles.formatted(.number.precision(.fractionLength(2)))) miles")
    }

    private func pacePanel(unit: CGFloat) -> some View {
        TimelineView(.animation(minimumInterval: 1, paused: !tracker.running || scenePhase != .active)) { _ in
            let run = tracker.journal.active
            let elapsed = tracker.activeSeconds
            let meters = run?.meters ?? 0
            let pace = run.flatMap { Self.averagePaceText(for: $0, at: elapsed) }
            let mile = run?.splitProgress?.currentMile(meters: meters, activeSeconds: elapsed)
            let layout = typeSize.isAccessibilitySize ? AnyLayout(VStackLayout(spacing: 6)) :
                AnyLayout(HStackLayout(spacing: 8 * unit))
            layout {
                paceCard("Average pace", pace: pace, unit: unit, primary: true)
                if run?.splitProgress != nil {
                    paceCard("Current mile", pace: mile.flatMap { Self.paceText(meters: $0.meters, activeSeconds: $0.activeSeconds) },
                             unit: unit, primary: false)
                }
            }
        }
    }

    private func paceCard(_ title: String, pace: String?, unit: CGFloat, primary: Bool) -> some View {
        VStack(spacing: 3) {
            Text(title).font(.subheadline.weight(.medium)).foregroundStyle(Self.label)
            Text(verbatim: pace ?? "—")
                .font(.custom("BarlowCondensed-Medium", size: (primary ? 47 : 36) * unit, relativeTo: .title))
                .lineLimit(1).minimumScaleFactor(0.5)
        }
        .padding(.vertical, 10 * unit)
        .padding(.horizontal, 8)
        .frame(width: (typeSize.isAccessibilitySize ? 344 : (primary ? 195 : 141)) * unit)
        .frame(minHeight: (tracker.running ? 104 : 82) * unit)
        .background(Self.panel, in: RoundedRectangle(cornerRadius: 10 * unit))
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(title)
        .accessibilityValue(pace?.replacingOccurrences(of: " /mi", with: " per mile") ?? "Unavailable")
    }

    private func finishControl(unit: CGFloat) -> some View {
        Button {
            if let run = tracker.journal.active, run.canDiscardAsShortRun {
                shortRunID = run.id
            } else {
                confirmingFinish = true
            }
        } label: {
            VStack(spacing: 6) {
                Image(systemName: "stop.fill")
                    .font(.system(size: 19 * unit))
                    .foregroundStyle(Self.stopIcon)
                    .frame(width: max(44, 60 * unit), height: max(44, 60 * unit))
                    .background(Self.stopFace, in: Circle())
                Text("Finish and save").font(.caption)
            }
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .disabled(!tracker.canWrite)
    }

    nonisolated static func clockText(_ seconds: TimeInterval) -> String {
        guard seconds.isFinite, seconds >= 0, seconds < Double(Int64.max) else { return "—" }
        let whole = Int64(seconds)
        return String(format: "%02lld:%02lld:%02lld", whole / 3600, (whole / 60) % 60, whole % 60)
    }

    nonisolated static func paceText(meters: Double, activeSeconds: TimeInterval) -> String? {
        guard meters.isFinite, meters > 0, activeSeconds.isFinite, activeSeconds > 0 else { return nil }
        let pace = (activeSeconds / meters * RunDistance.metersPerMile).rounded()
        guard pace > 0, pace < Double(Int64.max) else { return nil }
        let seconds = Int64(pace)
        return String(format: "%lld:%02lld /mi", seconds / 60, seconds % 60)
    }

    nonisolated static func averagePaceText(for run: RecordedRun, at activeSeconds: TimeInterval) -> String? {
        guard !run.hasPartialDistance(at: activeSeconds) else { return nil }
        return paceText(meters: run.meters, activeSeconds: activeSeconds)
    }
}

/// The countdown frame opens into the Android dial's 14 arcs and 76 ambient bars.
private struct InstrumentRing: View {
    let time: TimeInterval

    var body: some View {
        Canvas { context, size in
            let scale = min(size.width, size.height) / 336
            let center = CGPoint(x: size.width / 2, y: size.height / 2)
            let morph = max(0, min(1, (time - 0.91) / 0.28))
            if time < 0.51 {
                let progress = CGFloat(easeOut(time / 0.51))
                let width = (112 + 197 * progress) * scale
                let height = (324 - 15 * progress) * scale
                let frame = CGRect(x: center.x - width / 2,
                                   y: center.y - height / 2 + 19.5 * (1 - progress) * scale,
                                   width: width, height: height)
                let radius = (1 + 153.5 * progress) * scale
                let outline = Path(roundedRect: frame, cornerSize: CGSize(width: radius, height: radius))
                context.stroke(outline, with: .color(.white.opacity(0.4 * Double(1 - progress))), lineWidth: scale)
            }
            if time > 0.45, morph < 1 {
                let turn = 360 * easeOut((time - 0.45) / 0.62)
                let opacity = 0.65 * easeOut((time - 0.45) / 0.18) * (1 - morph)
                var arcs = Path()
                for index in 0..<14 {
                    let angle = Double(index) * 360 / 14 - 90 + turn
                    var arc = Path()
                    arc.addArc(center: center, radius: 146 * scale,
                               startAngle: .degrees(angle - 9.34), endAngle: .degrees(angle + 9.34),
                               clockwise: false)
                    arcs.addPath(arc)
                }
                context.stroke(arcs, with: .color(.white.opacity(opacity)), lineWidth: 4.5 * scale)
            }
            guard morph > 0 else { return }
            let phase = 2 * Double.pi * max(0, time - 1.19) / 5.464
            for index in 0..<76 {
                let angle = Double(index) / 76 * 2 * Double.pi - Double.pi / 2
                let amplitude = 0.5 + 0.5 * sin(angle * 3 - phase)
                let texture = 0.7 * (0.5 + 0.5 * sin(Double(index) * 5.31)) +
                              0.3 * (0.5 + 0.5 * sin(angle * 11 + phase))
                let radius = 146 - 12 * morph
                let length = 2 + (6 + 16 * amplitude + 7 * texture) * morph
                let tint = 0.3 + 0.7 * amplitude
                let steel = Color(red: (137 + 106 * tint) / 255,
                                  green: (150 + 96 * tint) / 255,
                                  blue: (162 + 86 * tint) / 255)
                func point(_ distance: Double) -> CGPoint {
                    CGPoint(x: center.x + CGFloat(cos(angle) * distance) * scale,
                            y: center.y + CGFloat(sin(angle) * distance) * scale)
                }
                var bar = Path()
                bar.move(to: point(radius))
                bar.addLine(to: point(radius + length))
                context.stroke(bar, with: .color(steel.opacity(morph * (0.9 + 0.08 * amplitude))),
                               style: StrokeStyle(lineWidth: 3.2 * scale, lineCap: .round))
            }
        }
    }

    private func easeOut(_ fraction: Double) -> Double {
        let remaining = 1 - max(0, min(1, fraction))
        return 1 - remaining * remaining * remaining
    }
}
