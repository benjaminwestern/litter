import SwiftUI

struct EmptyStateView: View {
    @EnvironmentObject var serverManager: ServerManager
    @EnvironmentObject var appState: AppState

    var body: some View {
        if serverManager.hasAnyConnection {
            connectedEmptyState
        } else {
            disconnectedEmptyState
        }
    }

    // MARK: - Connected (no active thread)

    private var connectedEmptyState: some View {
        VStack(spacing: 24) {
            Spacer()
            BrandLogo(size: 80)

            VStack(spacing: 8) {
                Text("Start a session")
                    .font(LitterFont.monospaced(.title3, weight: .semibold))
                    .foregroundColor(.white)
                Text(connectionSummary)
                    .font(LitterFont.monospaced(.caption))
                    .foregroundColor(LitterTheme.accent)
            }

            Button {
                withAnimation(.easeInOut(duration: 0.25)) {
                    appState.sidebarOpen = true
                }
            } label: {
                HStack {
                    Image(systemName: "plus")
                        .font(.system(.subheadline, weight: .medium))
                    Text("New Session")
                        .font(LitterFont.monospaced(.subheadline))
                }
                .foregroundColor(.black)
                .frame(maxWidth: 220)
                .padding(.vertical, 12)
                .background(LitterTheme.accent)
                .clipShape(RoundedRectangle(cornerRadius: 8))
            }

            Button {
                appState.showServerPicker = true
            } label: {
                HStack(spacing: 6) {
                    Image(systemName: "plus.circle")
                        .font(.system(size: 13))
                    Text("Add Server")
                        .font(LitterFont.monospaced(.caption))
                }
                .foregroundColor(LitterTheme.textSecondary)
            }

            Spacer()
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    // MARK: - Disconnected (show discovery inline)

    private var disconnectedEmptyState: some View {
        NavigationStack {
            DiscoveryView(onServerSelected: { _ in
                appState.showServerPicker = false
                appState.sidebarOpen = true
            })
            .environmentObject(serverManager)
        }
    }

    // MARK: - Helpers

    private var connectionSummary: String {
        let names = serverManager.connections.values
            .filter { $0.isConnected }
            .map { $0.server.name }
            .sorted()
        guard let first = names.first else { return "Not connected" }
        if names.count == 1 { return first }
        return "\(first) +\(names.count - 1)"
    }
}

#if DEBUG
#Preview("Empty State / Disconnected") {
    LitterPreviewScene(
        serverManager: LitterPreviewData.makeServerManager(
            includeConnection: false,
            includeActiveThread: false
        )
    ) {
        EmptyStateView()
    }
}

#Preview("Empty State / Connected") {
    LitterPreviewScene(
        serverManager: LitterPreviewData.makeServerManager(includeActiveThread: false)
    ) {
        EmptyStateView()
    }
}
#endif
