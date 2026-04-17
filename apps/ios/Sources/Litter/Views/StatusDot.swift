import SwiftUI

/// Shared visual language for "this thing's current state" — used for task
/// rows (active / hydrating / hydrated / idle) and server pills (connected /
/// connecting / failed / idle). Colors are fixed green/orange/red so the
/// meaning reads the same across themes.
enum StatusDotState {
    /// Solid green. Something is done / healthy.
    case ok
    /// Pulsing green. Something is live and running right now.
    case active
    /// Pulsing orange. Work in flight (connecting, reconnecting, loading).
    case pending
    /// Solid red. Failed state that needs attention.
    case error
    /// Empty grey ring. Known-but-dormant state (disconnected, not-loaded).
    case idle
}

struct StatusDot: View {
    let state: StatusDotState
    var size: CGFloat = 10

    @State private var pulsing = false

    var body: some View {
        Group {
            switch state {
            case .ok:
                Circle().fill(Color.green).frame(width: size, height: size)
            case .active:
                pulsingDot(color: .green)
            case .pending:
                pulsingDot(color: .orange)
            case .error:
                Circle().fill(Color.red).frame(width: size, height: size)
            case .idle:
                Circle()
                    .stroke(LitterTheme.textMuted.opacity(0.6), lineWidth: 1.5)
                    .frame(width: size + 2, height: size + 2)
            }
        }
        .frame(width: size + 2, height: size + 2)
    }

    private func pulsingDot(color: Color) -> some View {
        Circle()
            .fill(color)
            .frame(width: size, height: size)
            .opacity(pulsing ? 0.35 : 1.0)
            .scaleEffect(pulsing ? 0.85 : 1.0)
            .onAppear {
                withAnimation(.easeInOut(duration: 0.8).repeatForever(autoreverses: true)) {
                    pulsing = true
                }
            }
    }
}
