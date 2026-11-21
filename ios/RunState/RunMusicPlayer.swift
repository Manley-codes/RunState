import SwiftUI
import UIKit

/// Android's in-run player artwork choice: RunState's headphones picture (the default) or
/// the current song's album cover.
enum MusicPlayerLayout: String, CaseIterable {
    case headphones, album
    static let preferenceKey = "run.musicPlayerLayout"
    var label: String { self == .headphones ? "Headphones" : "Album cover" }
}

let musicHeadphonesArt = Bundle.main.url(forResource: "start_music_orb", withExtension: "jpg")
    .flatMap { UIImage(contentsOfFile: $0.path) }

private extension Color {
    init(musicHex value: UInt32, alpha: Double = 1) {
        self.init(red: Double((value >> 16) & 0xFF) / 255, green: Double((value >> 8) & 0xFF) / 255,
                  blue: Double(value & 0xFF) / 255, opacity: alpha)
    }
}

/// What the player shows for the current Spotify state.
@MainActor
private struct MusicPlayerContent {
    let spotify: SpotifyObservation
    let slowConnection: Bool

    var track: (title: String, artist: String, url: URL)? {
        switch spotify.state {
        case let .playing(title, artist, url), let .paused(title, artist, url): return (title, artist, url)
        default: return nil
        }
    }

    var paused: Bool {
        if case .playing = spotify.state { return false }
        return true
    }

    var status: String? {
        switch spotify.state {
        case .connecting: return slowConnection ? "Connecting to Spotify…" : nil
        case .authorizing: return "Waiting for Spotify…"
        case .unavailable(let message): return message
        case .suspended: return "Updates resume when RunState is open."
        case .off: return "Spotify not connected"
        case .playing, .paused: return spotify.commandMessage
        }
    }

    /// Reconnect a known authorization, or authorize when RunState has the public client ID.
    var connectTitle: String? {
        switch spotify.state {
        case .off, .unavailable:
            if spotify.isAuthorized { return "Try connection again" }
            return spotify.clientID == nil ? nil : "Connect Spotify"
        default: return nil
        }
    }

    var canOpenSpotify: Bool { spotify.state != .connecting && spotify.state != .authorizing }
}

/// The Android in-run player, drawn inside the distance ring: artwork, large previous and next
/// areas, the song, its progress and play/pause. All state comes from Spotify. Opening the
/// player never starts music or changes the run; only an explicit control sends a command.
struct RunMusicPlayer: View {
    @ObservedObject var spotify: SpotifyObservation
    let layout: MusicPlayerLayout
    /// The ring's clock. The arrows pulse with it, so a paused run or Reduce Motion stills them.
    let ringTime: TimeInterval
    /// Text too large for the circle: the controls move to a scrollable sheet instead.
    let compact: Bool
    let onClose: () -> Void
    let onConnect: () -> Void
    let onOpenSpotify: () -> Void

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.scenePhase) private var scenePhase
    @Environment(\.openURL) private var openURL
    @State private var slowConnection = false

    private var content: MusicPlayerContent { MusicPlayerContent(spotify: spotify, slowConnection: slowConnection) }

    var body: some View {
        GeometryReader { proxy in
            let u = min(proxy.size.width, proxy.size.height) / 309
            ZStack(alignment: .topLeading) {
                circle(u)
                if !compact { controls(u) }
            }
            .frame(width: 309 * u, height: 309 * u, alignment: .topLeading)
        }
        .aspectRatio(1, contentMode: .fit)
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("musicPlayer")
        .task(id: spotify.state == .connecting) {
            slowConnection = false
            guard spotify.state == .connecting else { return }
            try? await Task.sleep(for: .milliseconds(1_500))
            if !Task.isCancelled { slowConnection = true }
        }
        .onAppear { if layout == .album { spotify.loadArtwork() } }
        .onChange(of: spotify.playback?.trackURI) { _ in if layout == .album { spotify.loadArtwork() } }
        .onChange(of: layout) { next in if next == .album { spotify.loadArtwork() } }
    }

    private func circle(_ u: CGFloat) -> some View {
        ZStack(alignment: .topLeading) {
            MusicArtwork(spotify: spotify, layout: layout, size: 248 * u)
            LinearGradient(stops: [
                .init(color: Color(musicHex: 0x0C130F, alpha: 0x1F / 255), location: 0),
                .init(color: .clear, location: 0.28),
                .init(color: Color(musicHex: 0x0D1411, alpha: 0x1F / 255), location: 0.43),
                .init(color: Color(musicHex: 0x0D1411, alpha: 0x9E / 255), location: 0.67),
                .init(color: Color(musicHex: 0x151D19), location: 1)
            ], startPoint: .top, endPoint: .bottom)
            .allowsHitTesting(false)
            .accessibilityHidden(true)
            // Broad targets beneath the header, song and play control, as on Android.
            if !compact && content.track != nil {
                arrow(next: false).frame(width: 88 * u, height: 218 * u).offset(y: 15 * u)
                arrow(next: true).frame(width: 88 * u, height: 218 * u).offset(x: 160 * u, y: 15 * u)
            }
        }
        .frame(width: 248 * u, height: 248 * u, alignment: .topLeading)
        .background(Color(musicHex: 0x17211D))
        .clipShape(Circle())
        .offset(x: 30.5 * u, y: 30.5 * u)
    }

    private func arrow(next: Bool) -> some View {
        let enabled = next ? spotify.canSkipNext : spotify.canSkipPrevious
        let phase = reduceMotion ? 0 : (ringTime / 5.464).truncatingRemainder(dividingBy: 1)
        return Button {
            if next { spotify.next() } else { spotify.previous() }
        } label: {
            Color.clear
        }
        .buttonStyle(MusicArrowStyle(next: next, enabled: enabled, phase: phase, reduceMotion: reduceMotion))
        .disabled(!enabled)
        .accessibilityLabel(next ? "Next song" : "Previous song")
        .accessibilityIdentifier(next ? "musicNext" : "musicPrevious")
    }

    @ViewBuilder private func controls(_ u: CGFloat) -> some View {
        // The visible Spotify mark keeps its official proportions inside a 48-point target.
        Button {
            if let url = content.track?.url { openURL(url) } else { onOpenSpotify() }
        } label: {
            Image("spotify_mark_white").renderingMode(.template).resizable().scaledToFit()
                .foregroundStyle(.white).frame(width: 24, height: 24)
                .frame(width: 48, height: 48).contentShape(Circle())
        }
        .buttonStyle(.plain)
        .disabled(!content.canOpenSpotify)
        .accessibilityLabel("Open in Spotify")
        .position(x: 116 * u, y: 80 * u)

        Button(action: onClose) {
            Canvas { context, size in
                var cross = Path()
                cross.move(to: CGPoint(x: 3, y: 3))
                cross.addLine(to: CGPoint(x: size.width - 3, y: size.height - 3))
                cross.move(to: CGPoint(x: size.width - 3, y: 3))
                cross.addLine(to: CGPoint(x: 3, y: size.height - 3))
                context.stroke(cross, with: .color(.white), style: StrokeStyle(lineWidth: 1.5, lineCap: .round))
            }
            .frame(width: 16, height: 16)
            .frame(width: 48, height: 48).contentShape(Circle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel("Back to distance")
        .accessibilityIdentifier("musicClose")
        .position(x: 193 * u, y: 80 * u)

        if let track = content.track {
            Link(destination: track.url) {
                VStack(spacing: u) {
                    Text(verbatim: track.title).font(.system(size: 16 * u, weight: .medium)).foregroundStyle(.white)
                    Text(verbatim: track.artist).font(.system(size: 12 * u)).foregroundStyle(Color(musicHex: 0xE0E3E6))
                }
                .lineLimit(1).multilineTextAlignment(.center)
                .shadow(color: .black.opacity(0.9), radius: 2.5 * u, x: 0, y: u)
                .padding(.top, 6 * u)
                .frame(width: 112 * u, height: 48 * u, alignment: .top)
                .contentShape(Rectangle())
            }
            .accessibilityElement(children: .ignore)
            .accessibilityLabel("\(track.title), \(track.artist). Open in Spotify")
            .accessibilityIdentifier("musicTrack")
            .offset(x: 98.5 * u, y: 132 * u)

            TimelineView(.animation(minimumInterval: reduceMotion ? 1 : 0.1,
                                    paused: content.paused || scenePhase != .active)) { _ in
                let duration = spotify.playback?.durationMillis ?? 0
                let position = spotify.playback?.position(at: .now, paused: content.paused) ?? 0
                MusicProgress(position: position, duration: duration, playing: !content.paused, reduceMotion: reduceMotion)
            }
            .frame(width: 62 * u, height: 12 * u)
            .offset(x: 123.5 * u, y: 181 * u)

            MusicPlayButton(paused: content.paused, enabled: spotify.canTogglePlayback, circular: true,
                            action: spotify.togglePlayback)
                .frame(width: 55 * u, height: 55 * u)
                .offset(x: 127 * u, y: 211 * u)

            if let status = content.status {
                Text(verbatim: status).font(.system(size: 10 * u)).foregroundStyle(.white)
                    .lineLimit(1).multilineTextAlignment(.center)
                    .frame(width: 170 * u)
                    .offset(x: 70 * u, y: max(106 * u, 82 * u + 24))
                    .accessibilityAddTraits(.updatesFrequently)
            }
        } else {
            VStack(spacing: 4) {
                if let status = content.status {
                    Text(verbatim: status).font(.system(size: 13)).foregroundStyle(.white)
                        .lineLimit(3).multilineTextAlignment(.center)
                        .accessibilityIdentifier("musicStatus")
                }
                if let title = content.connectTitle {
                    Button(title, action: onConnect)
                        .font(.system(size: 14)).foregroundStyle(Color(musicHex: 0xDFE8D2))
                        .frame(minHeight: 48)
                        .disabled(spotify.commandPending)
                }
                if content.canOpenSpotify {
                    Button("Open Spotify", action: onOpenSpotify)
                        .font(.system(size: 12)).foregroundStyle(.white)
                        .frame(minHeight: 48)
                }
            }
            .frame(width: 181 * u)
            .offset(x: 64 * u, y: 109 * u)
        }
    }
}

/// The same player as a scrollable sheet when text is too large for the fixed circle.
struct RunMusicPlayerDetails: View {
    @ObservedObject var spotify: SpotifyObservation
    let layout: MusicPlayerLayout
    let onClose: () -> Void
    let onConnect: () -> Void
    let onOpenSpotify: () -> Void
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.scenePhase) private var scenePhase
    @Environment(\.openURL) private var openURL

    private var content: MusicPlayerContent { MusicPlayerContent(spotify: spotify, slowConnection: true) }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    MusicArtwork(spotify: spotify, layout: layout, size: 160)
                        .clipShape(Circle())
                        .frame(maxWidth: .infinity)
                    if let track = content.track {
                        Text(verbatim: track.title).font(.headline)
                        Text(verbatim: track.artist).foregroundStyle(.secondary)
                        HStack {
                            Spacer()
                            skip(next: false)
                            Spacer()
                            MusicPlayButton(paused: content.paused, enabled: spotify.canTogglePlayback,
                                            circular: false, action: spotify.togglePlayback)
                                .frame(width: 56, height: 56)
                            Spacer()
                            skip(next: true)
                            Spacer()
                        }
                        TimelineView(.animation(minimumInterval: reduceMotion ? 1 : 0.1,
                                                paused: content.paused || scenePhase != .active)) { _ in
                            MusicProgress(position: spotify.playback?.position(at: .now, paused: content.paused) ?? 0,
                                          duration: spotify.playback?.durationMillis ?? 0,
                                          playing: !content.paused, reduceMotion: reduceMotion)
                        }
                        .frame(height: 16)
                    }
                    if let status = content.status { Text(verbatim: status).foregroundStyle(.secondary) }
                    if let title = content.connectTitle {
                        Button(title, action: onConnect).disabled(spotify.commandPending)
                    }
                    if content.canOpenSpotify {
                        Button {
                            if let url = content.track?.url { openURL(url) } else { onOpenSpotify() }
                        } label: {
                            Label {
                                Text("Open Spotify")
                            } icon: {
                                Image("spotify_mark_white").renderingMode(.template).resizable().scaledToFit()
                                    .frame(width: 24, height: 24)
                            }
                        }
                    }
                }
                .padding()
            }
            .navigationTitle("Music")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Back to distance", action: onClose)
                }
            }
        }
        .preferredColorScheme(.dark)
        .onAppear { if layout == .album { spotify.loadArtwork() } }
        .onChange(of: spotify.playback?.trackURI) { _ in if layout == .album { spotify.loadArtwork() } }
    }

    private func skip(next: Bool) -> some View {
        let enabled = next ? spotify.canSkipNext : spotify.canSkipPrevious
        return Button {
            if next { spotify.next() } else { spotify.previous() }
        } label: {
            Image(systemName: next ? "forward.end.fill" : "backward.end.fill")
                .font(.title2).frame(width: 56, height: 56).contentShape(Circle())
        }
        .buttonStyle(.plain)
        .disabled(!enabled)
        .opacity(enabled ? 1 : 0.35)
        .accessibilityLabel(next ? "Next song" : "Previous song")
        .accessibilityIdentifier(next ? "musicNext" : "musicPrevious")
    }
}

/// Headphones artwork, the current song's cover, or the honest empty circle.
private struct MusicArtwork: View {
    @ObservedObject var spotify: SpotifyObservation
    let layout: MusicPlayerLayout
    let size: CGFloat

    private func description(headphones: Bool, cover: Bool) -> String {
        if headphones { return "RunState headphones artwork" }
        return cover ? "Current song album artwork" : "Album artwork unavailable"
    }

    var body: some View {
        let cover = layout == .album ? spotify.currentArtwork : nil
        let headphones = layout == .headphones || (cover == nil && (spotify.state == .connecting || spotify.loadingArtwork))
        ZStack {
            Color(musicHex: 0x17211D)
            if headphones, let art = musicHeadphonesArt {
                Image(uiImage: art).resizable().scaledToFill()
                    .scaleEffect(1.16).offset(y: -12)
            } else if let cover {
                Image(uiImage: cover).resizable().scaledToFill()
            }
        }
        .frame(width: size, height: size)
        .clipped()
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Text(verbatim: description(headphones: headphones, cover: cover != nil)))
        .accessibilityIdentifier("musicArtwork")
    }
}

private struct MusicPlayButton: View {
    let paused: Bool
    let enabled: Bool
    let circular: Bool
    let action: @MainActor () -> Void

    var body: some View {
        Button(action: action) {
            ZStack {
                if circular || paused {
                    Circle().fill(face)
                } else {
                    RoundedRectangle(cornerRadius: 16).fill(face)
                }
                Canvas { context, size in
                    let ink = GraphicsContext.Shading.color(Color(musicHex: 0x253A2C))
                    if paused {
                        var triangle = Path()
                        triangle.move(to: CGPoint(x: size.width * 0.3, y: size.height * 0.15))
                        triangle.addLine(to: CGPoint(x: size.width * 0.83, y: size.height * 0.5))
                        triangle.addLine(to: CGPoint(x: size.width * 0.3, y: size.height * 0.85))
                        triangle.closeSubpath()
                        context.fill(triangle, with: ink)
                    } else {
                        for left in [0.25, 0.6] {
                            let bar = CGRect(x: size.width * left, y: size.height * 0.2,
                                             width: size.width * 0.15, height: size.height * 0.6)
                            context.fill(Path(roundedRect: bar, cornerRadius: 1), with: ink)
                        }
                    }
                }
                .frame(width: 24, height: 24)
            }
            .contentShape(Circle())
        }
        .buttonStyle(.plain)
        .disabled(!enabled)
        .accessibilityLabel(paused ? "Play music" : "Pause music")
        .accessibilityIdentifier("musicPlayPause")
    }

    private var face: Color {
        Color(musicHex: 0xDCE7CF).opacity(!enabled ? 0.5 : circular ? 0.82 : 1)
    }
}

/// The song's progress as Android draws it: a gentle wave while playing, flat when paused.
private struct MusicProgress: View {
    let position: Int
    let duration: Int
    let playing: Bool
    let reduceMotion: Bool

    var body: some View {
        Canvas { context, size in
            let fraction = duration > 0 ? min(max(Double(position) / Double(duration), 0), 1) : 0
            let end = size.width * fraction
            let phase = reduceMotion ? 0 : Double(position) / 1_000 * Double.pi * 1.5
            let amplitude: Double = playing ? 1.6 : 0
            var wave = Path()
            var x = 0.0
            while x <= end {
                let edge = max(0, min(1, min(x / 4, (end - x) / 4)))
                let y = size.height / 2 + sin(x / 16 * 2 * Double.pi - phase) * amplitude * edge
                if x == 0 { wave.move(to: CGPoint(x: x, y: y)) } else { wave.addLine(to: CGPoint(x: x, y: y)) }
                x += 1
            }
            context.stroke(wave, with: .color(Color(musicHex: 0xF2F6EB)), style: StrokeStyle(lineWidth: 1.5, lineCap: .round))
            if end + 4 < size.width {
                var rest = Path()
                rest.move(to: CGPoint(x: end + 4, y: size.height / 2))
                rest.addLine(to: CGPoint(x: size.width, y: size.height / 2))
                context.stroke(rest, with: .color(.white.opacity(0.3)), style: StrokeStyle(lineWidth: 1.5, lineCap: .round))
            }
        }
        .accessibilityElement()
        .accessibilityLabel("Song progress")
        .accessibilityValue(duration > 0 ? "\(position / 1_000) of \(duration / 1_000) seconds" : "Duration unavailable")
    }
}

/// Android's reviewed chevron with its inner trail and the light that travels with the ring.
private struct MusicArrowStyle: ButtonStyle {
    let next: Bool
    let enabled: Bool
    /// Position within the ring's 5.464-second cycle, from 0 to 1.
    let phase: Double
    let reduceMotion: Bool

    func makeBody(configuration: Configuration) -> some View {
        let pressed = configuration.isPressed
        return Canvas { context, size in
            let scale = size.width / 88
            context.scaleBy(x: scale, y: scale)
            let (chevron, trail) = musicChevron(next: next)
            let pulse = reduceMotion ? 0.85 : musicArrowPulse(phase)
            let availability = enabled ? 1.0 : 0.35
            if enabled && pressed {
                context.fill(Path(CGRect(x: 0, y: 0, width: 88, height: 218)),
                             with: .radialGradient(Gradient(colors: [.white.opacity(0.2), .clear]),
                                                   center: CGPoint(x: next ? 88 : 0, y: 109), startRadius: 0, endRadius: 95))
            }
            context.fill(chevron, with: .color(Color(musicHex: 0xF0F4EA).opacity(0.5 * pulse * availability)))
            let trailAlpha = 0.24 * pulse * availability
            context.stroke(trail, with: .linearGradient(Gradient(stops: [
                .init(color: .clear, location: 0), .init(color: .white.opacity(trailAlpha), location: 0.3),
                .init(color: .white.opacity(trailAlpha), location: 0.7), .init(color: .clear, location: 1)
            ]), startPoint: CGPoint(x: 0, y: 22), endPoint: CGPoint(x: 0, y: 194)),
                           style: StrokeStyle(lineWidth: 0.8, lineCap: .round, lineJoin: .round))
            guard !reduceMotion && enabled else { return }
            let strength: Double
            switch phase {
            case ..<0.18: strength = musicEase(phase / 0.18) * 0.64
            case ..<0.62: strength = 0.64
            case ..<0.84: strength = (1 - musicEase((phase - 0.62) / 0.22)) * 0.64
            default: strength = 0
            }
            guard strength > 0 else { return }
            let lightY = 218 * (1.35 - 1.7 * musicEase(min(max(phase / 0.84, 0), 1)))
            func light(_ alpha: Double) -> GraphicsContext.Shading {
                .linearGradient(Gradient(colors: [.clear, .white.opacity(strength * pulse * alpha), .clear]),
                                startPoint: CGPoint(x: 0, y: lightY - 65), endPoint: CGPoint(x: 0, y: lightY + 65))
            }
            context.stroke(chevron, with: light(0.10), style: StrokeStyle(lineWidth: 8, lineJoin: .round))
            context.stroke(chevron, with: light(0.18), style: StrokeStyle(lineWidth: 4, lineJoin: .round))
            context.fill(chevron, with: light(1))
        }
        .contentShape(Rectangle())
    }
}

/// CSS-style cubic-bezier(.42, 0, .58, 1), the easing Android uses for the arrows.
private func musicEase(_ value: Double) -> Double {
    let x = min(max(value, 0), 1)
    var low = 0.0
    var high = 1.0
    for _ in 0..<24 {
        let t = (low + high) / 2
        let curveX = 3 * (1 - t) * (1 - t) * t * 0.42 + 3 * (1 - t) * t * t * 0.58 + t * t * t
        if curveX < x { low = t } else { high = t }
    }
    let t = (low + high) / 2
    return 3 * (1 - t) * t * t + t * t * t
}

private func musicArrowPulse(_ phase: Double) -> Double {
    func between(_ start: Double, _ end: Double, _ from: Double, _ to: Double) -> Double {
        from + (to - from) * musicEase((phase - start) / (end - start))
    }
    switch phase {
    case ..<0.24: return between(0, 0.24, 0.68, 0.94)
    case ..<0.52: return between(0.24, 0.52, 0.94, 0.78)
    case ..<0.76: return between(0.52, 0.76, 0.78, 0.9)
    default: return between(0.76, 1, 0.9, 0.68)
    }
}

/// The exact reviewed Radix chevron silhouette in an 88 × 218 area, plus its inner-edge trail.
private func musicChevron(next: Bool) -> (Path, Path) {
    func point(_ x: Double, _ y: Double, inset: Double = 0) -> CGPoint {
        let right = 18 + (x - 5.9) * 56 / 5.1 - inset
        return CGPoint(x: next ? right : 88 - right, y: 6 + (y - 2.9) * 204 / 9.2)
    }
    var body = Path()
    body.move(to: point(6.1584, 3.13508))
    body.addCurve(to: point(6.86514, 3.15788), control1: point(6.35985, 2.94621), control2: point(6.67627, 2.95642))
    body.addLine(to: point(10.6151, 7.15788))
    body.addCurve(to: point(10.6151, 7.84182), control1: point(10.7954, 7.3502), control2: point(10.7954, 7.64949))
    body.addLine(to: point(6.86514, 11.8418))
    body.addCurve(to: point(6.1584, 11.8646), control1: point(6.67627, 12.0433), control2: point(6.35985, 12.0535))
    body.addCurve(to: point(6.1356, 11.1579), control1: point(5.95694, 11.6757), control2: point(5.94673, 11.3593))
    body.addLine(to: point(9.565, 7.49985))
    body.addLine(to: point(6.1356, 3.84182))
    body.addCurve(to: point(6.1584, 3.13508), control1: point(5.94673, 3.64036), control2: point(5.95694, 3.32394))
    body.closeSubpath()
    var trail = Path()
    trail.move(to: point(6.1356, 3.84182, inset: 10))
    trail.addLine(to: point(9.565, 7.49985, inset: 10))
    trail.addLine(to: point(6.1356, 11.1579, inset: 10))
    return (body, trail)
}
