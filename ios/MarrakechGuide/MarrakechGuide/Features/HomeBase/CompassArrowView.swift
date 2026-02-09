import SwiftUI

/// Animated compass arrow that points toward a destination.
///
/// Features:
/// - Smooth rotation animation
/// - Confidence indicator ring
/// - Optional pulsing when heading is weak
struct CompassArrowView: View {

    /// Rotation angle in degrees (0 = pointing up/north)
    let rotationDegrees: Double

    /// Confidence level of the heading
    let confidence: HeadingConfidence

    /// Size of the compass
    var size: CGFloat = 200

    /// Whether to show the confidence ring
    var showConfidenceRing: Bool = true

    @State private var isPulsing = false

    /// Respect system Reduce Motion preference
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        ZStack {
            // Confidence ring
            if showConfidenceRing {
                Circle()
                    .stroke(confidenceColor.opacity(0.3), lineWidth: 8)
                    .frame(width: size + 20, height: size + 20)

                Circle()
                    .stroke(confidenceColor, lineWidth: 3)
                    .frame(width: size + 20, height: size + 20)
                    .opacity(isPulsing ? 0.5 : 1.0)
            }

            // Outer circle
            Circle()
                .fill(Color(.systemBackground))
                .frame(width: size, height: size)
                .shadow(color: .black.opacity(0.1), radius: 10, x: 0, y: 4)

            // Cardinal direction markers
            cardinalMarkers

            // Arrow
            arrowShape
                .fill(arrowGradient)
                .frame(width: size * 0.35, height: size * 0.6)
                .rotationEffect(.degrees(rotationDegrees))
                .animation(reduceMotion ? .none : .easeInOut(duration: 0.15), value: rotationDegrees)

            // Center dot
            Circle()
                .fill(Color.primary.opacity(0.2))
                .frame(width: 12, height: 12)

            // Unavailable indicator
            if confidence == .unavailable {
                unavailableOverlay
            }
        }
        .onChange(of: confidence) { _, newValue in
            if reduceMotion {
                // Skip pulsing animation when Reduce Motion is enabled
                isPulsing = false
            } else {
                withAnimation(.easeInOut(duration: 0.3).repeatForever(autoreverses: true)) {
                    isPulsing = newValue == .weak
                }
            }
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(accessibilityDescription)
    }

    // MARK: - Subviews

    private var cardinalMarkers: some View {
        ZStack {
            // N marker
            Text("N")
                .font(.system(size: 14, weight: .bold))
                .foregroundColor(.secondary)
                .offset(y: -(size / 2 - 16))

            // E marker
            Text("E")
                .font(.system(size: 12, weight: .medium))
                .foregroundColor(.secondary.opacity(0.7))
                .offset(x: size / 2 - 16)

            // S marker
            Text("S")
                .font(.system(size: 12, weight: .medium))
                .foregroundColor(.secondary.opacity(0.7))
                .offset(y: size / 2 - 16)

            // W marker
            Text("W")
                .font(.system(size: 12, weight: .medium))
                .foregroundColor(.secondary.opacity(0.7))
                .offset(x: -(size / 2 - 16))
        }
    }

    private var arrowShape: some Shape {
        ArrowShape()
    }

    private var arrowGradient: LinearGradient {
        LinearGradient(
            colors: [.orange, .red],
            startPoint: .top,
            endPoint: .bottom
        )
    }

    private var unavailableOverlay: some View {
        ZStack {
            Circle()
                .fill(Color(.systemBackground).opacity(0.8))
                .frame(width: size, height: size)

            VStack(spacing: 8) {
                Image(systemName: "location.slash")
                    .font(.system(size: 40))
                    .foregroundColor(.secondary)

                Text("Heading unavailable")
                    .font(.subheadline)
                    .foregroundColor(.secondary)
            }
        }
    }

    // MARK: - Computed Properties

    private var confidenceColor: Color {
        switch confidence {
        case .good:
            return .green
        case .weak:
            return .orange
        case .unavailable:
            return .gray
        }
    }

    private var accessibilityDescription: String {
        switch confidence {
        case .unavailable:
            return "Compass unavailable"
        case .weak:
            return "Compass pointing \(directionFromRotation), heading may be inaccurate"
        case .good:
            return "Compass pointing \(directionFromRotation)"
        }
    }

    private var directionFromRotation: String {
        let normalized = rotationDegrees.truncatingRemainder(dividingBy: 360)
        let adjusted = normalized < 0 ? normalized + 360 : normalized

        switch adjusted {
        case 337.5..<360, 0..<22.5: return "north"
        case 22.5..<67.5: return "northeast"
        case 67.5..<112.5: return "east"
        case 112.5..<157.5: return "southeast"
        case 157.5..<202.5: return "south"
        case 202.5..<247.5: return "southwest"
        case 247.5..<292.5: return "west"
        case 292.5..<337.5: return "northwest"
        default: return "unknown direction"
        }
    }
}

// MARK: - Arrow Shape

/// Custom arrow shape for the compass
struct ArrowShape: Shape {
    func path(in rect: CGRect) -> Path {
        var path = Path()

        let width = rect.width
        let height = rect.height

        // Arrow pointing up
        path.move(to: CGPoint(x: width / 2, y: 0))
        path.addLine(to: CGPoint(x: width, y: height * 0.4))
        path.addLine(to: CGPoint(x: width * 0.6, y: height * 0.4))
        path.addLine(to: CGPoint(x: width * 0.6, y: height))
        path.addLine(to: CGPoint(x: width * 0.4, y: height))
        path.addLine(to: CGPoint(x: width * 0.4, y: height * 0.4))
        path.addLine(to: CGPoint(x: 0, y: height * 0.4))
        path.closeSubpath()

        return path
    }
}

// MARK: - Previews

#Preview("Good Heading") {
    CompassArrowView(
        rotationDegrees: 45,
        confidence: .good
    )
    .padding()
}

#Preview("Weak Heading") {
    CompassArrowView(
        rotationDegrees: 120,
        confidence: .weak
    )
    .padding()
}

#Preview("Unavailable") {
    CompassArrowView(
        rotationDegrees: 0,
        confidence: .unavailable
    )
    .padding()
}
