import ImageIO
import SwiftUI
import UIKit

private func ink(_ rgb: Int, opacity: Double = 1) -> Color {
    Color(.sRGB, red: Double((rgb >> 16) & 255) / 255,
          green: Double((rgb >> 8) & 255) / 255, blue: Double(rgb & 255) / 255,
          opacity: opacity)
}

private struct OpeningPhoto {
    let name: String
    let width: CGFloat
    let cropY: CGFloat

    init(_ name: String, _ width: CGFloat = 114, _ cropY: CGFloat = 0.5) {
        self.name = name
        self.width = width
        self.cropY = cropY
    }
}

// Width and crop travel with each photograph, as in the accepted Android arrangement.
private let openingRows: [[OpeningPhoto]] = [
    [.init("pick-23"), .init("city-stride"), .init("track-partners", 126, 0.76), .init("motion-city"), .init("pick-11"), .init("motion-green")],
    [.init("pick-06"), .init("pick-03", 240), .init("forest-run"), .init("pick-14", 240), .init("pick-10", 240), .init("orange-bottle", 115)],
    [.init("headphone-runner", 114, 0.7), .init("pick-13"), .init("pick-19", 136), .init("race-celebration", 244), .init("pick-21", 124)],
    [.init("pick-22"), .init("pick-07"), .init("purple-sneakers", 230, 1), .init("pick-12"), .init("pick-04"), .init("pick-24")],
    [.init("pick-01", 244), .init("pick-02", 242), .init("pick-25"), .init("blue-shoes", 208)],
    [.init("pick-15", 240), .init("pick-20"), .init("hydration", 190), .init("run-friends", 238), .init("pick-09"), .init("motion-street")]
]
private let openingSpeeds: [CGFloat] = [51.75, 56.0625, 53.1875, 57.5, 52.46875, 56.0625]
private let openingPhases: [CGFloat] = [5, 2, 2, 2, 3, 3]

private func bundledPicture(_ name: String, _ ext: String) -> UIImage? {
    Bundle.main.url(forResource: name, withExtension: ext).flatMap { UIImage(contentsOfFile: $0.path) }
}

private func decodeOpeningPhotos() async -> [String: CGImage] {
    let options: [CFString: Any] = [
        kCGImageSourceCreateThumbnailFromImageAlways: true, kCGImageSourceThumbnailMaxPixelSize: 640,
        kCGImageSourceCreateThumbnailWithTransform: true, kCGImageSourceShouldCacheImmediately: true
    ]
    var pictures: [String: CGImage] = [:]
    for photo in openingRows.joined() {
        guard !Task.isCancelled,
              let url = Bundle.main.url(forResource: photo.name, withExtension: "webp", subdirectory: "opening"),
              let source = CGImageSourceCreateWithURL(url as CFURL, nil),
              let image = CGImageSourceCreateThumbnailAtIndex(source, 0, options as CFDictionary) else { return [:] }
        pictures[photo.name] = image
    }
    return pictures
}

struct RunOpeningView: View {
    let onFinished: () -> Void
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.scenePhase) private var scenePhase
    @State private var pictures: [String: CGImage] = [:]
    @State private var frameReady = false
    @State private var accumulated: Duration = .zero
    @State private var clockStart: ContinuousClock.Instant?
    @State private var finished = false
    private let clock = ContinuousClock()
    private static let firstFrame = bundledPicture("opening_first_frame", "webp")

    init(onFinished: @escaping () -> Void) {
        self.onFinished = onFinished
    }

    var body: some View {
        ZStack {
            TimelineView(.animation(minimumInterval: 1.0 / 60, paused: reduceMotion || scenePhase != .active)) { _ in
                GeometryReader { geometry in
                    let duration = (accumulated + (clockStart.map { $0.duration(to: clock.now) } ?? .zero)).components
                    let elapsed = Double(duration.seconds) + Double(duration.attoseconds) / 1e18
                    ZStack {
                        if pictures.isEmpty {
                            if let still = Self.firstFrame {
                                Image(uiImage: still).resizable().scaledToFill()
                                    .frame(width: geometry.size.width, height: geometry.size.height).clipped()
                            }
                        } else {
                            photoWall(elapsed: reduceMotion ? 0 : CGFloat(elapsed))
                        }
                        let t = reduceMotion ? 1.0 : min(max((elapsed - 0.6) / 0.85, 0), 1)
                        let arrival = 1 - pow(1 - t, 4)
                        let scale = min(1, geometry.size.width / 390)
                        wordmark.font(.custom("BarlowCondensed-Medium", fixedSize: 98 * scale))
                            .tracking(-3.8 * scale)
                            .transformEffect(CGAffineTransform(a: 1, b: 0, c: -0.08749, d: 1, tx: 0, ty: 0))
                            .scaleEffect(0.96 + 0.04 * arrival)
                            .offset(y: -8 + 14 * (1 - arrival)).opacity(arrival)
                            .shadow(color: ink(0x050e09, opacity: 0.25), radius: 14, y: 4)
                    }
                    .frame(width: geometry.size.width, height: geometry.size.height)
                }
            }
            .background(ink(0x17201b)).ignoresSafeArea()
            VStack { Spacer(); Text("Tap to skip").font(.footnote).foregroundColor(.white.opacity(0.85)).padding(.bottom, 12) }
        }
        .contentShape(Rectangle()).onTapGesture(perform: finish)
        .accessibilityElement(children: .ignore).accessibilityLabel("Skip RunState opening")
        .accessibilityAddTraits(.isButton).accessibilityAction(perform: finish)
        .accessibilityIdentifier("runOpening")
        .task {
            if !reduceMotion {
                let decoded = await decodeOpeningPhotos()
                guard !Task.isCancelled else { return }
                pictures = decoded
            }
            // Keep the baked still in place until every tile is decoded. Start the clock at
            // zero when replacing it, so a slower first decode cannot jump the moving rows.
            frameReady = true
        }
        .task(id: scenePhase == .active && frameReady) {
            guard scenePhase == .active, frameReady, !finished else { return }
            clockStart = clock.now
            do {
                try await clock.sleep(for: max(.zero, .seconds(4.8) - accumulated))
            } catch { return }
            if !Task.isCancelled { finish() }
        }
        .onChange(of: scenePhase) { phase in
            if phase != .active, let start = clockStart {
                accumulated += start.duration(to: clock.now)
                clockStart = nil
            }
        }
    }

    private func finish() {
        guard !finished else { return }
        finished = true
        onFinished()
    }

    private var wordmark: Text {
        Text("R").foregroundColor(ink(0x8f6fe3)) + Text("u").foregroundColor(ink(0xfffdf6)) +
        Text("n").foregroundColor(ink(0xf9a869)) + Text("St").foregroundColor(ink(0xe2edc9)) +
        Text("a").foregroundColor(ink(0x7f9c54)) + Text("te").foregroundColor(ink(0xe2edc9))
    }

    private func photoWall(elapsed: CGFloat) -> some View {
        Canvas { context, size in
            let gap: CGFloat = 6, overhang: CGFloat = 36
            let height = (size.height + 2 * overhang - 5 * gap) / 6
            for (index, row) in openingRows.enumerated() {
                let width = row.reduce(0) { $0 + $1.width + gap }
                let travel = (openingSpeeds[index] * (elapsed + openingPhases[index])).truncatingRemainder(dividingBy: width)
                var x: CGFloat = -20 + (index.isMultiple(of: 2) ? -travel : travel - width)
                let y = -overhang + CGFloat(index) * (height + gap)
                while x < size.width {
                    for tile in row {
                        if x + tile.width > 0, x < size.width, let picture = pictures[tile.name] {
                            let frame = CGRect(x: x, y: y, width: tile.width, height: height)
                            let scale = max(tile.width / CGFloat(picture.width), height / CGFloat(picture.height))
                            let w = CGFloat(picture.width) * scale, h = CGFloat(picture.height) * scale
                            let crop = CGRect(x: frame.midX - w / 2, y: y + (height - h) * tile.cropY, width: w, height: h)
                            var clipped = context
                            clipped.clip(to: Path(frame))
                            clipped.draw(Image(decorative: picture, scale: 1), in: crop)
                        }
                        x += tile.width + gap
                    }
                }
            }
        }
        .saturation(1.08).contrast(1.02)
        .overlay(LinearGradient(stops: [
            .init(color: ink(0x0a130f, opacity: 0.48), location: 0),
            .init(color: ink(0x0a130f, opacity: 0.09), location: 0.16),
            .init(color: ink(0x0a130f, opacity: 0.14), location: 0.29),
            .init(color: ink(0x0a130f, opacity: 0.65), location: 0.49),
            .init(color: ink(0x0a130f, opacity: 0.51), location: 0.56),
            .init(color: ink(0x0a130f, opacity: 0.12), location: 0.73),
            .init(color: ink(0x0a130f, opacity: 0.48), location: 1)
        ], startPoint: .top, endPoint: .bottom))
    }
}

struct RunStartView: View {
    let primaryTitle: String
    let locationStatus: String
    let canStart: Bool
    let selectedEnergy: String?
    let onSelectEnergy: (String) -> Void
    let onPrimary: () -> Void
    let onHistory: () -> Void
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.dynamicTypeSize) private var typeSize
    @State private var entered = false
    @Namespace private var intake
    private static let map = bundledPicture("start_map_reference", "jpg")

    var body: some View {
        GeometryReader { geometry in
            ScrollView {
                VStack(spacing: 0) {
                    VStack(spacing: 8) {
                        readyCard
                        HStack {
                            Spacer()
                            Button(action: onHistory) {
                                Image(systemName: "clock.arrow.circlepath")
                                    .font(.system(size: 23, weight: .medium))
                                    .frame(width: 48, height: 48)
                            }
                            .accessibilityLabel("Log history").accessibilityIdentifier("startHistory")
                        }
                    }
                    .padding(.horizontal, 20).padding(.top, 18)

                    Spacer(minLength: 32)
                    if selectedEnergy == nil || !typeSize.isAccessibilitySize {
                        energySection(width: geometry.size.width)
                            .padding(.horizontal, 20).padding(.bottom, 14)
                    }
                    footer
                }
                .frame(maxWidth: 660).frame(maxWidth: .infinity)
                .frame(minHeight: geometry.size.height)
            }
            .scrollIndicators(.hidden)
        }
        .background {
            GeometryReader { geometry in
                ZStack {
                    ink(0xd5e0d3)
                    if let map = Self.map {
                        Image(uiImage: map).resizable().scaledToFill()
                            .frame(width: geometry.size.width, height: geometry.size.height).clipped()
                    }
                    LinearGradient(stops: [.init(color: .white.opacity(0.08), location: 0),
                                           .init(color: .white.opacity(0.02), location: 0.55),
                                           .init(color: .white.opacity(0.52), location: 1)],
                                   startPoint: .top, endPoint: .bottom)
                }
            }.ignoresSafeArea().accessibilityHidden(true)
        }
        .foregroundColor(ink(0x293326)).buttonStyle(.plain)
        .onAppear { entered = true }
    }

    private var readyCard: some View {
        Group {
            if typeSize.isAccessibilitySize {
                VStack(spacing: 4) {
                    HStack { brand; agentRing.frame(width: 70, height: 70) }
                    readiness
                }
            } else {
                HStack(spacing: 4) {
                    brand
                    agentRing.frame(width: 108, height: 108)
                    readiness
                }
            }
        }
        .fixedSize(horizontal: false, vertical: true)
        .padding(.horizontal, 12).padding(.vertical, 4)
        .frame(maxWidth: .infinity)
        .background(RoundedRectangle(cornerRadius: 24).fill(.white)
            .shadow(color: ink(0x49645c, opacity: 0.2), radius: 8, y: 4))
        .overlay(RoundedRectangle(cornerRadius: 24).strokeBorder(ink(0xdde7e2), lineWidth: 1))
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("RunState. \(locationStatus)")
        .accessibilityValue(selectedEnergy.map { "Physical energy: " + $0 } ?? "")
    }

    private var brand: some View {
        VStack(alignment: .leading, spacing: 1) {
            Text("RUN").font(.headline.weight(.heavy)).tracking(2).foregroundColor(ink(0xacc638))
            Text("STATE").font(.title.weight(.black)).tracking(1)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private var readiness: some View {
        VStack(alignment: .trailing, spacing: 1) {
            Text("READY").font(.headline.weight(.heavy)).tracking(2).foregroundColor(ink(0x666d62))
            Text("TO RUN").font(.title3.weight(.black)).tracking(1)
            Text(locationStatus).font(.caption.weight(.bold)).multilineTextAlignment(.trailing)
                .foregroundColor(ink(0x596354))
        }
        .frame(maxWidth: .infinity, alignment: .trailing)
    }

    private var agentRing: some View {
        ZStack {
            TimelineView(.animation(minimumInterval: 1.0 / 30, paused: reduceMotion)) { timeline in
                let phase = reduceMotion ? 0 : timeline.date.timeIntervalSinceReferenceDate
                    .truncatingRemainder(dividingBy: 5.464) / 5.464 * 2 * .pi
                Canvas { context, size in
                    let span = min(size.width, size.height)
                    let barWidth = min(1.4, max(1, span * 0.014))
                    let unit = (span - barWidth) / (2 * (264 + 79.8 * 1.8))
                    let radius = 264 * unit
                    for index in 0..<76 {
                        let theta = Double(index) * 2 * .pi / 76
                        let wave = 0.5 + 0.5 * sin(theta * 3 - phase)
                        let length = (9 + wave * 7.5 + (sin(Double(index) * 5.31) * 0.5 + 0.5) * 2.5) * 4.2
                        let dx = sin(theta), dy = -cos(theta)
                        var bar = Path()
                        bar.move(to: CGPoint(x: size.width / 2 + dx * radius, y: size.height / 2 + dy * radius))
                        let end = radius + length * unit
                        bar.addLine(to: CGPoint(x: size.width / 2 + dx * end, y: size.height / 2 + dy * end))
                        let color = index * 56 / 76 < 18 ? 0x849b80 : index * 56 / 76 < 35 ? 0xa390b3 : 0xaca473
                        context.stroke(bar, with: .color(ink(color, opacity: 0.84 + wave * 0.12)),
                                       style: StrokeStyle(lineWidth: barWidth, lineCap: .round))
                    }
                }
            }
            if let selectedEnergy {
                Image(systemName: symbol(for: selectedEnergy)).font(.headline)
                    .foregroundColor(ink(0x596354)).matchedGeometryEffect(id: selectedEnergy, in: intake)
            }
        }
    }

    private static let energyLevels = ["Low", "Moderate", "High"]

    private func symbol(for energy: String) -> String {
        switch energy {
        case "Low": return "moon.fill"
        case "High": return "sun.max.fill"
        default: return "line.3.horizontal"
        }
    }

    private func energySection(width: CGFloat) -> some View {
        VStack(spacing: 8) {
            Text("Physical energy now · Optional").font(.caption.weight(.semibold))
                .foregroundColor(.white).padding(.horizontal, 16).padding(.vertical, 8)
                .background(ink(0x2d3827, opacity: 0.96), in: RoundedRectangle(cornerRadius: 12))
            let layout: AnyLayout = typeSize.isAccessibilitySize ? AnyLayout(VStackLayout(spacing: 7)) : AnyLayout(HStackLayout(spacing: 8))
            layout {
                ForEach(Self.energyLevels.indices, id: \.self) { index in
                    let energy = Self.energyLevels[index]
                    let accent = ink([0xcdb7ea, 0xb8d1b7, 0xeed378][index])
                    Button {
                        withAnimation(reduceMotion ? nil : .easeInOut(duration: 0.55)) { onSelectEnergy(energy) }
                    } label: {
                        VStack(spacing: 4) {
                            if selectedEnergy != energy {
                                Image(systemName: symbol(for: energy)).font(.subheadline.weight(.semibold))
                                    .foregroundColor(accent).matchedGeometryEffect(id: energy, in: intake)
                            }
                            Text(energy.uppercased()).font(.caption.weight(.black)).tracking(1).foregroundColor(.white)
                            Text(["Running low", "Steady", "Energized"][index])
                                .font(.caption2).foregroundColor(accent)
                        }
                        .frame(maxWidth: .infinity, minHeight: typeSize.isAccessibilitySize ? 60 : 82)
                        .padding(.vertical, typeSize.isAccessibilitySize ? 6 : 0)
                        .background(ink(0x293326, opacity: 0.91), in: RoundedRectangle(cornerRadius: 17))
                    }
                    .accessibilityLabel("\(energy) physical energy").accessibilityIdentifier("energy" + energy)
                    .offset(x: entered || reduceMotion ? 0 : width)
                    .animation(reduceMotion ? nil : .timingCurve(0.22, 1, 0.36, 1, duration: 0.68)
                        .delay(Double(index) * 0.12), value: entered)
                }
            }
        }
        .opacity(selectedEnergy == nil ? 1 : 0)
        .allowsHitTesting(selectedEnergy == nil).accessibilityHidden(selectedEnergy != nil)
    }

    private var footer: some View {
        Button(action: onPrimary) {
            VStack(spacing: 2) {
                StartDial(lit: selectedEnergy != nil).frame(width: 102, height: 102)
                Text(primaryTitle).font(.headline.weight(.heavy)).tracking(1.4)
                Text("GPS run").font(.caption2.weight(.semibold)).foregroundColor(.white.opacity(0.8))
            }
            .foregroundColor(.white).padding(.horizontal, 8).contentShape(Rectangle())
        }
        .disabled(!canStart).opacity(canStart ? 1 : 0.55)
        .accessibilityLabel(primaryTitle).accessibilityIdentifier("startPrimary")
        .frame(maxWidth: .infinity).padding(.top, 24).padding(.bottom, 8)
        .background {
            GeometryReader { g in
                Path { path in
                    let w = g.size.width, h = g.size.height
                    path.move(to: CGPoint(x: 0, y: h * 0.16))
                    path.addCurve(to: CGPoint(x: w * 0.5, y: h * 0.06), control1: CGPoint(x: w * 0.21, y: h * 0.24), control2: CGPoint(x: w * 0.37, y: h * 0.06))
                    path.addCurve(to: CGPoint(x: w, y: h * 0.16), control1: CGPoint(x: w * 0.63, y: h * 0.06), control2: CGPoint(x: w * 0.82, y: h * 0.23))
                    path.addLine(to: CGPoint(x: w, y: h)); path.addLine(to: CGPoint(x: 0, y: h)); path.closeSubpath()
                }
                .fill(LinearGradient(colors: [ink(0x2d3827), ink(0x1d261b)], startPoint: .leading, endPoint: .trailing))
            }.ignoresSafeArea(edges: .bottom)
        }
    }
}

private struct StartDial: View {
    let lit: Bool

    var body: some View {
        Canvas { context, size in
            let center = CGPoint(x: size.width / 2, y: size.height / 2)
            let radius = min(size.width, size.height) * 0.49 * 0.9
            func point(_ x: CGFloat, _ y: CGFloat) -> CGPoint {
                CGPoint(x: center.x + x * radius, y: center.y + y * radius)
            }
            let circle = Path(ellipseIn: CGRect(origin: point(-1, -1), size: CGSize(width: radius * 2, height: radius * 2)))
            context.fill(circle, with: .radialGradient(Gradient(colors: [ink(0x3b4733), ink(0x232e1e), ink(0x151e13)]),
                                                        center: point(-0.45, -0.7), startRadius: 0, endRadius: radius * 2.2))
            if lit { context.stroke(circle, with: .color(ink(0xb8d442, opacity: 0.7)), lineWidth: 1.4) }
            for index in 0..<12 {
                let mark = index.isMultiple(of: 3)
                let theta = CGFloat(index) * .pi / 6
                let dx = sin(theta), dy = -cos(theta)
                let end: CGFloat = mark ? 0.88 : 0.83
                var tick = Path()
                tick.move(to: point(dx * 0.68, dy * 0.68))
                tick.addLine(to: point(dx * end, dy * end))
                context.stroke(tick, with: .color(.white.opacity(mark ? 0.78 : 0.42)),
                               style: StrokeStyle(lineWidth: mark ? 1.8 : 1, lineCap: .round))
            }
            var triangle = Path()
            triangle.move(to: point(-0.23, -0.36)); triangle.addLine(to: point(0.42, 0))
            triangle.addLine(to: point(-0.23, 0.36)); triangle.closeSubpath()
            triangle.move(to: point(-0.12, -0.20)); triangle.addLine(to: point(0.24, 0))
            triangle.addLine(to: point(-0.12, 0.20)); triangle.closeSubpath()
            context.fill(triangle, with: .color(.white), style: FillStyle(eoFill: true))
            var strip = Path()
            strip.move(to: point(-0.13, 0))
            strip.addLine(to: point(0.06, 0))
            context.stroke(strip, with: .color(ink(0xf5be37)), lineWidth: 4)
        }
        .accessibilityHidden(true)
    }
}
