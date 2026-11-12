import SwiftUI

// Coordinates follow the Android instrument's 393-point artboard.
private func instrumentColor(_ rgb: UInt32) -> Color {
    Color(red: Double(rgb >> 16) / 255, green: Double((rgb >> 8) & 255) / 255,
          blue: Double(rgb & 255) / 255)
}

struct RunInstrumentBackground: View {
    private static let specks: [CGPoint] = [
        .init(x: 23, y: 109), .init(x: 96, y: 125), .init(x: 281, y: 104), .init(x: 369, y: 129),
        .init(x: 15, y: 205), .init(x: 110, y: 231), .init(x: 291, y: 191), .init(x: 378, y: 261),
        .init(x: 42, y: 302), .init(x: 327, y: 315), .init(x: 19, y: 373), .init(x: 109, y: 418),
        .init(x: 287, y: 393), .init(x: 374, y: 450), .init(x: 31, y: 495), .init(x: 91, y: 530),
        .init(x: 301, y: 509), .init(x: 361, y: 539), .init(x: 14, y: 599), .init(x: 381, y: 620),
        .init(x: 23, y: 676), .init(x: 374, y: 709), .init(x: 77, y: 759), .init(x: 144, y: 731),
        .init(x: 244, y: 766), .init(x: 322, y: 751), .init(x: 44, y: 782), .init(x: 361, y: 783)
    ]

    var body: some View {
        ZStack {
            instrumentColor(0x101113).ignoresSafeArea()
            Canvas { context, size in
                for (index, speck) in Self.specks.enumerated() {
                    let radius = (0.65 + CGFloat(index % 4) * 0.12) * size.width / 393
                    let dot = CGRect(x: speck.x / 393 * size.width - radius,
                                     y: speck.y / 794 * size.height - radius,
                                     width: 2 * radius, height: 2 * radius)
                    context.fill(Path(ellipseIn: dot), with: .color(.white.opacity(0.30 + Double(index % 6) * 0.045)))
                }
            }
        }
        .accessibilityHidden(true)
        .allowsHitTesting(false)
    }
}

struct RunInstrumentHeader: View {
    let status: String
    let gps: String
    let unit: CGFloat
    @ScaledMetric(relativeTo: .caption) private var statusSize: CGFloat = 11

    init(status: String, gps: String, unit: CGFloat) {
        self.status = status
        self.gps = gps
        self.unit = unit
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Text("RUNSTATE")
                .font(.system(size: 21 * unit, weight: .semibold))
                .tracking(4.5 * unit)
                .foregroundStyle(instrumentColor(0xF5F5F7))
            HStack(alignment: .center, spacing: 7 * unit) {
                Circle().fill(instrumentColor(0x795BFA)).frame(width: 6, height: 6)
                Text(status).layoutPriority(1)
                Rectangle().fill(instrumentColor(0x53545A)).frame(width: 1, height: 12 * unit)
                Text(gps)
            }
            .font(.system(size: statusSize * unit))
            .foregroundStyle(instrumentColor(0xA9ACB8))
            .padding(.top, 10 * unit)
            .fixedSize(horizontal: false, vertical: true)
            Spacer(minLength: 10 * unit)
            Rectangle().fill(instrumentColor(0x35363C)).frame(height: unit)
        }
        .padding(.horizontal, 24 * unit)
        .padding(.top, 20 * unit)
        .frame(minHeight: 86 * unit)
        .fixedSize(horizontal: false, vertical: true)
        .accessibilityElement(children: .combine)
    }
}

struct RunCountdownView: View {
    let seconds: Int
    let cadenceActive: Bool
    let onStartNow: () -> Void
    let onCancel: () -> Void

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.scenePhase) private var scenePhase
    @ScaledMetric(relativeTo: .callout) private var startLabelSize: CGFloat = 14
    @ScaledMetric(relativeTo: .callout) private var cancelSize: CGFloat = 14
    @State private var beatStarted = Date()

    init(seconds: Int, cadenceActive: Bool = true, onStartNow: @escaping () -> Void,
         onCancel: @escaping () -> Void) {
        self.seconds = seconds
        self.cadenceActive = cadenceActive
        self.onStartNow = onStartNow
        self.onCancel = onCancel
    }

    var body: some View {
        GeometryReader { bounds in
            let widthUnit = min(bounds.size.width / 393, 1.2)
            let unit = min(widthUnit, max(0.9, bounds.size.height / 774))
            let still = reduceMotion || !cadenceActive || scenePhase != .active
            ScrollView {
                VStack(spacing: 0) {
                    RunInstrumentHeader(status: "Ready to run", gps: "GPS at start", unit: unit)
                    Color.clear.frame(height: 140 * unit).accessibilityHidden(true)
                    TimelineView(.animation(minimumInterval: 1.0 / 60, paused: still)) { tick in
                        let beat = CountdownBeat(elapsed: tick.date.timeIntervalSince(beatStarted), still: still)
                        VStack(spacing: 0) {
                            CountdownGrid(seconds: seconds, beat: beat)
                                .frame(height: 324 * unit)
                                .accessibilityElement(children: .ignore)
                                .accessibilityLabel("Countdown")
                                .accessibilityValue("\(seconds)")
                            Button(action: onStartNow) {
                                VStack(spacing: 5 * unit) {
                                    CountdownDial(beat: beat)
                                        .frame(width: 88 * unit, height: 88 * unit)
                                    Text("Start now")
                                        .font(.system(size: startLabelSize * unit, weight: .semibold))
                                        .tracking(1.1 * unit)
                                        .foregroundStyle(instrumentColor(0xCFD3DC))
                                }
                                .padding(.horizontal, 8)
                                .contentShape(Rectangle())
                            }
                            .buttonStyle(.plain)
                            .accessibilityLabel("Start now")
                            .padding(.top, 24 * unit)
                        }
                    }
                    Button(action: onCancel) {
                        Text("Cancel")
                            .font(.system(size: cancelSize * unit))
                            .foregroundStyle(instrumentColor(0xADB1BE))
                            .frame(minWidth: 80, minHeight: 48)
                            .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    .padding(.top, 20 * unit)
                }
                .frame(width: 393 * unit)
                .padding(.bottom, 20 * unit)
                .frame(maxWidth: .infinity)
                .frame(minHeight: bounds.size.height)
            }
            .scrollIndicators(.hidden)
        }
        .background(RunInstrumentBackground())
        .onChange(of: seconds) { _ in beatStarted = Date() }
        .onChange(of: cadenceActive) { _ in beatStarted = Date() }
    }
}

// This clock only articulates a displayed digit. The run owner advances the countdown.
private struct CountdownBeat {
    let phase: CGFloat
    let scale: CGFloat
    let movement: CGFloat
    let arrival: CGFloat
    let visibility: Double
    let still: Bool

    init(elapsed: TimeInterval, still: Bool) {
        self.still = still
        phase = CGFloat(min(1, max(0, elapsed)))
        if still {
            scale = 1
            movement = 0
            arrival = 0
            visibility = 1
        } else {
            if phase < 0.24 {
                scale = 1.48 - 0.455 * Self.curve(phase / 0.24, 0.48, 0.04, 0.85, 0.60)
            } else {
                scale = 1.025 - 0.025 * Self.curve((phase - 0.24) / 0.06, 0.18, 0.75, 0.28, 1)
            }
            movement = sin(.pi * min(1, max(0, (phase - 0.28) / 0.49)))
            arrival = 1 - Self.ease(phase / 0.30)
            visibility = Double(1 - Self.ease((phase - 0.80) / 0.08))
        }
    }

    static func ease(_ progress: CGFloat) -> CGFloat {
        curve(progress, 0.22, 0.85, 0.16, 1)
    }

    // Solve the Android timing curve's x coordinate, then evaluate its y coordinate.
    private static func curve(_ x: CGFloat, _ x1: CGFloat, _ y1: CGFloat, _ x2: CGFloat, _ y2: CGFloat) -> CGFloat {
        if x <= 0 { return 0 }
        if x >= 1 { return 1 }
        func cubic(_ t: CGFloat, _ p1: CGFloat, _ p2: CGFloat) -> CGFloat {
            let u = 1 - t
            return 3 * u * u * t * p1 + 3 * u * t * t * p2 + t * t * t
        }
        var low: CGFloat = 0, high: CGFloat = 1
        for _ in 0..<9 {
            let t = (low + high) / 2
            if cubic(t, x1, x2) < x { low = t } else { high = t }
        }
        return cubic((low + high) / 2, y1, y2)
    }
}

private struct CountdownGrid: View {
    let seconds: Int
    let beat: CountdownBeat

    var body: some View {
        Canvas { original, size in
            var context = original
            let unit = size.width / 393
            context.scaleBy(x: unit, y: unit)
            let elapsedDigits = max(0, 10 - seconds)
            let step = min(4, elapsedDigits / 2)
            let progress = beat.still ? 1 : CountdownBeat.ease((CGFloat(elapsedDigits % 2) + beat.phase) / 0.42)
            let grid = instrumentColor(0xB8BACC)
            for index in 0..<6 {
                let previous = row(index, step: max(0, step - 1))
                let current = row(index, step: step)
                let y = 112 * (CGFloat(previous) + CGFloat(current - previous) * progress)
                let box = CGRect(x: index < 3 ? 22 : 265, y: y, width: 106, height: 100)
                let tone = beat.still ? 0.57 : 0.16 + 0.68 * beat.arrival + 0.20 * beat.movement
                context.stroke(Path(box), with: .color(grid.opacity(Double(0.13 + 0.33 * min(1, tone)))), lineWidth: 1)
                var mark = context
                mark.translateBy(x: box.midX, y: box.midY)
                let scale = beat.scale * (1 + 0.07 * beat.movement)
                mark.scaleBy(x: scale, y: scale)
                let opacity = beat.still ? 0.85 : beat.visibility * min(1, 0.67 + Double(0.30 * beat.arrival + 0.1 * beat.movement))
                drawMark(index, in: mark, color: instrumentColor(0xECEFF2).opacity(opacity))
            }
            let frame = CGRect(x: 141, y: 0.5, width: 112, height: 323)
            let frameTone = beat.still ? 0.6 : 0.28 + 0.6 * beat.arrival + 0.12 * beat.movement
            context.stroke(Path(roundedRect: frame, cornerRadius: 1),
                           with: .color(grid.opacity(Double(0.32 + 0.14 * frameTone))), lineWidth: 1)
            context.translateBy(x: 196.5, y: 162)
            context.scaleBy(x: beat.scale, y: beat.scale)
            context.opacity = beat.visibility
            let digit = Text(verbatim: String(seconds))
                .font(.custom("Anton-Regular", fixedSize: seconds >= 10 ? 118 : 142))
                .foregroundColor(instrumentColor(0xF5F5F7))
            context.draw(context.resolve(digit), at: .zero)
        }
    }

    private func row(_ index: Int, step: Int) -> Int {
        ((index % 3) + (index < 3 ? step : 6 - step)) % 3
    }

    private func drawMark(_ index: Int, in drawing: GraphicsContext, color: Color) {
        var context = drawing
        let direction: CGFloat = index < 3 ? 1 : -1
        let ink = GraphicsContext.Shading.color(color)
        switch index {
        case 0, 4:
            context.rotate(by: .degrees(Double(-13 * direction * beat.movement)))
            let outer = Path(ellipseIn: CGRect(x: -10.08, y: -10.08, width: 20.16, height: 20.16))
            context.clip(to: outer)
            var crescent = outer
            crescent.addEllipse(in: CGRect(x: -3.84, y: -13.20, width: 18.72, height: 18.72))
            context.fill(crescent, with: ink, style: FillStyle(eoFill: true))
        case 1, 5:
            let gap = beat.still ? 5.28 : 3.1 + 3.9 * beat.movement
            var bars = Path()
            for bar in -1...1 {
                let y = 0.24 + CGFloat(bar) * gap
                bars.move(to: CGPoint(x: -7.2, y: y))
                bars.addLine(to: CGPoint(x: 7.2, y: y))
            }
            context.stroke(bars, with: ink, style: StrokeStyle(lineWidth: 2.667, lineCap: .round))
        default:
            context.fill(Path(ellipseIn: CGRect(x: -4.8, y: -4.8, width: 9.6, height: 9.6)), with: ink)
            context.rotate(by: .degrees(Double(35 * direction * beat.movement)))
            var rays = Path()
            for ray in 0..<8 {
                let angle = CGFloat(ray) * .pi / 4
                rays.move(to: CGPoint(x: cos(angle) * 7.92, y: sin(angle) * 7.92))
                rays.addLine(to: CGPoint(x: cos(angle) * 11.52, y: sin(angle) * 11.52))
            }
            context.stroke(rays, with: ink, style: StrokeStyle(lineWidth: 2.4, lineCap: .round))
        }
    }
}

private struct CountdownDial: View {
    let beat: CountdownBeat

    var body: some View {
        Canvas { original, size in
            var context = original
            let unit = size.width / 88
            context.scaleBy(x: unit, y: unit)
            context.translateBy(x: 44, y: 44)
            let radius: CGFloat = 40.48
            let face = Path(ellipseIn: CGRect(x: -radius, y: -radius, width: 2 * radius, height: 2 * radius))
            context.fill(face, with: .color(instrumentColor(0x262A31)))
            context.stroke(face, with: .color(instrumentColor(0x6E757E)), lineWidth: 1)
            let ink = instrumentColor(0xEFF2F6)
            for tick in 0..<12 {
                let major = tick % 3 == 0
                let angle = CGFloat(tick) * .pi / 6 - .pi / 2 + beat.movement * .pi / 15
                let inner = radius * (0.66 - 0.10 * beat.movement + 0.07 * beat.arrival)
                let outer = radius * ((major ? 0.85 : 0.81) + 0.07 * beat.arrival - 0.035 * beat.movement)
                var mark = Path()
                mark.move(to: CGPoint(x: cos(angle) * inner, y: sin(angle) * inner))
                mark.addLine(to: CGPoint(x: cos(angle) * outer, y: sin(angle) * outer))
                let alpha = major ? 0.4 + 0.5 * beat.visibility : 0.2 + 0.4 * beat.visibility
                context.stroke(mark, with: .color(ink.opacity(alpha)),
                               style: StrokeStyle(lineWidth: major ? 1.7 : 1.1, lineCap: .round))
            }
            var play = Path()
            let triangles: [(CGFloat, CGFloat, CGFloat)] = [(0.23, 0.42, 0.36), (0.12, 0.24, 0.20)]
            for (left, right, height) in triangles {
                play.move(to: CGPoint(x: -radius * left, y: -radius * height))
                play.addLine(to: CGPoint(x: radius * right, y: 0))
                play.addLine(to: CGPoint(x: -radius * left, y: radius * height))
                play.closeSubpath()
            }
            context.fill(play, with: .color(ink), style: FillStyle(eoFill: true))
            context.fill(Path(CGRect(x: -radius * 0.05, y: -radius * 0.05,
                                     width: radius * 0.17, height: radius * 0.10)),
                         with: .color(instrumentColor(0xAB9FDE)))
        }
        .accessibilityHidden(true)
    }
}
