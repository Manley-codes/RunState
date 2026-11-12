import SwiftUI

struct SavedRouteView: View {
    let route: RunRoute

    var body: some View {
        VStack(spacing: 0) {
            Canvas { context, size in
                for section in Self.sections(for: route, in: size) {
                    guard let first = section.first else { continue }
                    var path = Path()
                    path.move(to: first)
                    for point in section.dropFirst() { path.addLine(to: point) }
                    let ink = Color(red: 0.67, green: 0.90, blue: 0.49)
                    context.stroke(path, with: .color(ink), style: StrokeStyle(lineWidth: 3, lineCap: .round, lineJoin: .round))
                    // A section with a single anchor still represents one observed location.
                    context.fill(Path(ellipseIn: CGRect(x: first.x - 3, y: first.y - 3, width: 6, height: 6)),
                                 with: .color(ink))
                }
            }
            if route.isTruncated {
                Text("Only the first part of this route was saved.")
                    .font(.footnote).foregroundStyle(.secondary).padding(.bottom, 8)
            }
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("Recorded route")
        .accessibilityValue(accessibleDescription)
    }

    private var accessibleDescription: String {
        let sections = route.points.filter(\.beginsSegment).count
        let description = route.points.count == 1 ? "One recorded location."
            : sections == 1 ? "One recorded section." : "\(sections) separate recorded sections."
        return route.isTruncated ? description + " Only the first part was saved." : description
    }

    /// Fit the outline inside the drawing area at a single scale, north up. Unwrap longitudes
    /// so a runner crossing 180° draws a short local path, with no line across a GPS break.
    nonisolated static func sections(for route: RunRoute, in size: CGSize) -> [[CGPoint]] {
        guard let first = route.points.first, size.width > 0, size.height > 0 else { return [] }
        var points: [CGPoint] = []
        var previousLongitude = first.longitude
        var minX = CGFloat(first.longitude), maxX = minX
        var minY = CGFloat(first.latitude), maxY = minY
        for point in route.points {
            let delta = (point.longitude - previousLongitude).remainder(dividingBy: 360)
            previousLongitude += delta
            let location = CGPoint(x: previousLongitude, y: point.latitude)
            points.append(location)
            minX = min(minX, location.x)
            maxX = max(maxX, location.x)
            minY = min(minY, location.y)
            maxY = max(maxY, location.y)
        }
        let middleX = (minX + maxX) / 2
        let middleY = (minY + maxY) / 2
        let latitudeScale = max(cos(middleY * .pi / 180), 0)
        let width = (maxX - minX) * latitudeScale
        let height = maxY - minY
        let scaleX = width > 0 ? max(1, size.width - 32) / width : .infinity
        let scaleY = height > 0 ? max(1, size.height - 32) / height : .infinity
        let fit = min(scaleX, scaleY)
        let scale = fit.isFinite ? fit : 0

        var sections: [[CGPoint]] = []
        for (index, point) in points.enumerated() {
            if sections.isEmpty || route.points[index].beginsSegment { sections.append([]) }
            sections[sections.count - 1].append(CGPoint(
                x: size.width / 2 + (point.x - middleX) * latitudeScale * scale,
                y: size.height / 2 - (point.y - middleY) * scale
            ))
        }
        return sections
    }
}
