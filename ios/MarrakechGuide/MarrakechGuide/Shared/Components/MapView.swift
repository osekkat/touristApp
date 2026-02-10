import SwiftUI
import CoreLocation

/// Offline-capable map view component.
///
/// This is a placeholder implementation that renders:
/// - A grid background representing the map area
/// - Markers for places
/// - Routes as polylines
/// - User location dot
///
/// In production, this would be replaced with MapLibre or MapKit.
struct OfflineMapView: View {
    let state: MapViewState
    let tileSource: TileSource
    let onCameraChange: (MapCamera) -> Void
    let onMarkerClick: (MapMarker) -> Void
    let onMyLocationClick: () -> Void
    let onDownloadMapClick: () -> Void

    @State private var offset: CGSize = .zero
    @GestureState private var dragOffset: CGSize = .zero

    init(
        state: MapViewState,
        tileSource: TileSource = .placeholder,
        onCameraChange: @escaping (MapCamera) -> Void = { _ in },
        onMarkerClick: @escaping (MapMarker) -> Void = { _ in },
        onMyLocationClick: @escaping () -> Void = {},
        onDownloadMapClick: @escaping () -> Void = {}
    ) {
        self.state = state
        self.tileSource = tileSource
        self.onCameraChange = onCameraChange
        self.onMarkerClick = onMarkerClick
        self.onMyLocationClick = onMyLocationClick
        self.onDownloadMapClick = onDownloadMapClick
    }

    var body: some View {
        ZStack {
            // Map background
            Color(red: 0.96, green: 0.96, blue: 0.86)

            // Map canvas
            GeometryReader { geometry in
                Canvas { context, size in
                    let centerX = size.width / 2 + offset.width + dragOffset.width
                    let centerY = size.height / 2 + offset.height + dragOffset.height

                    // Draw grid lines (simulating streets)
                    let gridColor = Color(red: 0.88, green: 0.87, blue: 0.82)
                    let gridSpacing: CGFloat = 50

                    for x in stride(from: 0, to: size.width, by: gridSpacing) {
                        context.stroke(
                            Path { path in
                                path.move(to: CGPoint(x: x, y: 0))
                                path.addLine(to: CGPoint(x: x, y: size.height))
                            },
                            with: .color(gridColor),
                            lineWidth: 1
                        )
                    }
                    for y in stride(from: 0, to: size.height, by: gridSpacing) {
                        context.stroke(
                            Path { path in
                                path.move(to: CGPoint(x: 0, y: y))
                                path.addLine(to: CGPoint(x: size.width, y: y))
                            },
                            with: .color(gridColor),
                            lineWidth: 1
                        )
                    }

                    // Draw routes
                    for route in state.routes where route.points.count >= 2 {
                        var path = Path()
                        let firstPoint = coordinateToScreen(
                            route.points[0],
                            center: state.camera.center,
                            centerX: centerX,
                            centerY: centerY,
                            zoom: state.camera.zoom
                        )
                        path.move(to: firstPoint)

                        for point in route.points.dropFirst() {
                            let screenPoint = coordinateToScreen(
                                point,
                                center: state.camera.center,
                                centerX: centerX,
                                centerY: centerY,
                                zoom: state.camera.zoom
                            )
                            path.addLine(to: screenPoint)
                        }

                        context.stroke(
                            path,
                            with: .color(Color(red: 0.29, green: 0.56, blue: 0.85)),
                            lineWidth: CGFloat(route.width * 2)
                        )
                    }

                    // Draw markers
                    for marker in state.markers {
                        let screenPos = coordinateToScreen(
                            marker.coordinate,
                            center: state.camera.center,
                            centerX: centerX,
                            centerY: centerY,
                            zoom: state.camera.zoom
                        )

                        let markerColor = color(for: marker.category)
                        let radius: CGFloat = marker.isSelected ? 16 : 12

                        // Marker pin
                        context.fill(
                            Path(ellipseIn: CGRect(
                                x: screenPos.x - radius,
                                y: screenPos.y - radius,
                                width: radius * 2,
                                height: radius * 2
                            )),
                            with: .color(markerColor)
                        )

                        // White center
                        let innerRadius = marker.isSelected ? 8.0 : 6.0
                        context.fill(
                            Path(ellipseIn: CGRect(
                                x: screenPos.x - innerRadius,
                                y: screenPos.y - innerRadius,
                                width: innerRadius * 2,
                                height: innerRadius * 2
                            )),
                            with: .color(.white)
                        )
                    }

                    // Draw user location
                    if let location = state.userLocation {
                        let userPos = coordinateToScreen(
                            location,
                            center: state.camera.center,
                            centerX: centerX,
                            centerY: centerY,
                            zoom: state.camera.zoom
                        )

                        // Accuracy circle
                        context.fill(
                            Path(ellipseIn: CGRect(
                                x: userPos.x - 30,
                                y: userPos.y - 30,
                                width: 60,
                                height: 60
                            )),
                            with: .color(Color(red: 0.29, green: 0.56, blue: 0.85).opacity(0.2))
                        )

                        // User dot
                        context.fill(
                            Path(ellipseIn: CGRect(
                                x: userPos.x - 10,
                                y: userPos.y - 10,
                                width: 20,
                                height: 20
                            )),
                            with: .color(Color(red: 0.29, green: 0.56, blue: 0.85))
                        )

                        // White center
                        context.fill(
                            Path(ellipseIn: CGRect(
                                x: userPos.x - 6,
                                y: userPos.y - 6,
                                width: 12,
                                height: 12
                            )),
                            with: .color(.white)
                        )

                        // Heading indicator
                        if let heading = state.userHeading {
                            let radians = heading * .pi / 180
                            var trianglePath = Path()
                            trianglePath.move(to: CGPoint(x: userPos.x, y: userPos.y - 20))
                            trianglePath.addLine(to: CGPoint(x: userPos.x - 6, y: userPos.y - 8))
                            trianglePath.addLine(to: CGPoint(x: userPos.x + 6, y: userPos.y - 8))
                            trianglePath.closeSubpath()

                            context.drawLayer { ctx in
                                ctx.translateBy(x: userPos.x, y: userPos.y)
                                ctx.rotate(by: .radians(radians))
                                ctx.translateBy(x: -userPos.x, y: -userPos.y)
                                ctx.fill(trianglePath, with: .color(Color(red: 0.29, green: 0.56, blue: 0.85)))
                            }
                        }
                    }
                }
                .gesture(
                    DragGesture()
                        .updating($dragOffset) { value, state, _ in
                            state = value.translation
                        }
                        .onEnded { value in
                            offset = CGSize(
                                width: offset.width + value.translation.width,
                                height: offset.height + value.translation.height
                            )
                            // Update camera
                            let latDelta = -value.translation.height / 10000.0
                            let lngDelta = value.translation.width / 10000.0
                            onCameraChange(MapCamera(
                                center: MapCoordinate(
                                    latitude: state.camera.center.latitude + latDelta,
                                    longitude: state.camera.center.longitude + lngDelta
                                ),
                                zoom: state.camera.zoom,
                                bearing: state.camera.bearing,
                                tilt: state.camera.tilt
                            ))
                        }
                )
            }

            // Loading indicator
            if state.isLoading {
                ProgressView()
                    .progressViewStyle(CircularProgressViewStyle())
                    .scaleEffect(1.5)
            }

            // Offline map prompt
            if tileSource == .placeholder && !state.isLoading {
                VStack {
                    OfflineMapPrompt(onDownloadClick: onDownloadMapClick)
                        .padding(.top, Theme.Spacing.lg)
                    Spacer()
                }
            }

            // Map controls
            VStack {
                Spacer()
                HStack {
                    Spacer()
                    Button(action: onMyLocationClick) {
                        Image(systemName: "location.fill")
                            .font(.title3)
                            .foregroundStyle(Theme.Adaptive.primary)
                            .frame(width: 44, height: 44)
                            .background(Theme.Adaptive.cardBackground)
                            .clipShape(Circle())
                            .shadow(radius: 2)
                    }
                    .padding(Theme.Spacing.md)
                }
            }

            // Error message
            if let error = state.error {
                VStack {
                    Spacer()
                    Text(error)
                        .font(.themeCaption)
                        .foregroundStyle(.white)
                        .padding(Theme.Spacing.sm)
                        .background(Color.red)
                        .cornerRadius(Theme.CornerRadius.small)
                        .padding(Theme.Spacing.md)
                }
            }
        }
    }

    private func coordinateToScreen(
        _ coordinate: MapCoordinate,
        center: MapCoordinate,
        centerX: CGFloat,
        centerY: CGFloat,
        zoom: Double
    ) -> CGPoint {
        let scale = zoom * 10000
        let x = centerX + CGFloat((coordinate.longitude - center.longitude) * scale)
        let y = centerY - CGFloat((coordinate.latitude - center.latitude) * scale)
        return CGPoint(x: x, y: y)
    }

    private func color(for category: MarkerCategory) -> Color {
        switch category {
        case .homeBase:
            return Color(red: 0.30, green: 0.69, blue: 0.31) // Green
        case .routeStop:
            return Color(red: 0.13, green: 0.59, blue: 0.95) // Blue
        case .restaurant, .cafe:
            return Color(red: 1.0, green: 0.60, blue: 0.0) // Orange
        case .landmark, .museum:
            return Color(red: 0.61, green: 0.15, blue: 0.69) // Purple
        default:
            return Color(red: 0.90, green: 0.22, blue: 0.21) // Red
        }
    }
}

// MARK: - Offline Map Prompt

private struct OfflineMapPrompt: View {
    let onDownloadClick: () -> Void

    var body: some View {
        Button(action: onDownloadClick) {
            HStack(spacing: Theme.Spacing.sm) {
                Image(systemName: "icloud.and.arrow.down")
                    .font(.title3)
                    .foregroundStyle(Theme.Adaptive.primary)

                VStack(alignment: .leading, spacing: 2) {
                    Text("Offline Maps Available")
                        .font(.themeSubheadline.weight(.semibold))
                        .foregroundStyle(Theme.Adaptive.textPrimary)
                    Text("Download for navigation without internet")
                        .font(.themeCaption)
                        .foregroundStyle(Theme.Adaptive.textSecondary)
                }
            }
            .padding(Theme.Spacing.md)
            .background(Theme.Adaptive.primaryContainer)
            .cornerRadius(Theme.CornerRadius.medium)
        }
        .buttonStyle(.plain)
    }
}

// MARK: - Compass Indicator

/// Simple compass indicator component.
struct CompassIndicator: View {
    let bearing: Double

    var body: some View {
        ZStack {
            Circle()
                .fill(Theme.Adaptive.cardBackground)
                .frame(width: 40, height: 40)
                .shadow(radius: 2)

            Canvas { context, size in
                let center = CGPoint(x: size.width / 2, y: size.height / 2)

                context.drawLayer { ctx in
                    ctx.translateBy(x: center.x, y: center.y)
                    ctx.rotate(by: .degrees(-bearing))
                    ctx.translateBy(x: -center.x, y: -center.y)

                    // North indicator (red)
                    ctx.stroke(
                        Path { path in
                            path.move(to: CGPoint(x: center.x, y: center.y - 12))
                            path.addLine(to: CGPoint(x: center.x, y: center.y + 4))
                        },
                        with: .color(.red),
                        lineWidth: 3
                    )

                    // South indicator (gray)
                    ctx.stroke(
                        Path { path in
                            path.move(to: CGPoint(x: center.x, y: center.y + 4))
                            path.addLine(to: CGPoint(x: center.x, y: center.y + 12))
                        },
                        with: .color(.gray),
                        lineWidth: 3
                    )
                }
            }
        }
        .frame(width: 40, height: 40)
    }
}

// MARK: - Preview

#Preview {
    OfflineMapView(
        state: MapViewState(
            markers: [
                MapMarker(
                    id: "1",
                    coordinate: .medinaCenter,
                    title: "Jemaa el-Fna",
                    category: .landmark
                )
            ],
            userLocation: MapCoordinate(latitude: 31.6260, longitude: -7.9895),
            userHeading: 45
        )
    )
}
