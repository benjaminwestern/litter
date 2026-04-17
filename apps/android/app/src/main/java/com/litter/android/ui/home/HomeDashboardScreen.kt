package com.litter.android.ui.home

import com.sigkitten.litter.android.BuildConfig
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.litter.android.state.AppLifecycleController
import com.litter.android.state.DebugSettings
import com.litter.android.state.SavedProjectStore
import com.litter.android.state.SavedServerStore
import com.litter.android.state.SavedThreadsStore
import com.litter.android.state.connectionModeLabel
import com.litter.android.state.displayTitle
import com.litter.android.state.isIpcConnected
import com.litter.android.state.statusColor
import com.litter.android.state.statusLabel
import com.litter.android.ui.ExperimentalFeatures
import com.litter.android.ui.LitterFeature
import com.litter.android.ui.LitterTheme
import com.litter.android.ui.LocalAppModel
import kotlinx.coroutines.launch
import uniffi.codex_mobile_client.AppProject
import uniffi.codex_mobile_client.AppServerSnapshot
import uniffi.codex_mobile_client.AppSessionSummary
import uniffi.codex_mobile_client.PinnedThreadKey
import uniffi.codex_mobile_client.ThreadKey
import uniffi.codex_mobile_client.deriveProjects
import uniffi.codex_mobile_client.projectIdFor

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HomeDashboardScreen(
    onOpenConversation: (ThreadKey) -> Unit,
    onShowDiscovery: () -> Unit,
    onShowSettings: () -> Unit,
    onOpenProjectPicker: () -> Unit,
    selectedProject: AppProject?,
    selectedServerId: String?,
    onSelectServer: (AppServerSnapshot) -> Unit,
    onThreadCreated: (ThreadKey) -> Unit,
    onStartVoice: (() -> Unit)? = null,
) {
    val appModel = LocalAppModel.current
    val context = LocalContext.current
    val snapshot by appModel.snapshot.collectAsState()
    val scope = rememberCoroutineScope()
    val voiceController = remember { com.litter.android.state.VoiceRuntimeController.shared }
    val lifecycleController = remember { AppLifecycleController() }

    var showTipJar by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<AppServerSnapshot?>(null) }
    var renameText by remember { mutableStateOf("") }
    val appVersionLabel = remember { "v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})" }

    val snap = snapshot
    val servers = remember(snap) {
        snap?.let { HomeDashboardSupport.sortedConnectedServers(it) } ?: emptyList()
    }
    // Every session across connected servers — unlimited, used by the search
    // view so the user can pin any thread.
    val allSessions = remember(snap) {
        snap?.let { HomeDashboardSupport.recentSessions(it, limit = Int.MAX_VALUE) } ?: emptyList()
    }

    // Pinned + hidden state. Refreshed when the user mutates via the UI.
    var pinnedKeys by remember { mutableStateOf(SavedThreadsStore.pinnedKeys(context)) }
    var hiddenKeys by remember { mutableStateOf(SavedThreadsStore.hiddenKeys(context)) }

    // Home list = pinned first (preserving pin order), filled from recent up
    // to 10 when pinnedCount < 10, else all pinned (unbounded). Hidden
    // threads are excluded from both halves.
    val homeSessions = remember(pinnedKeys, hiddenKeys, allSessions) {
        mergeHomeSessions(pinnedKeys, hiddenKeys, allSessions)
    }

    val scopedServerId = selectedProject?.serverId ?: selectedServerId
    val recentSessions = remember(homeSessions, scopedServerId) {
        if (scopedServerId.isNullOrEmpty()) homeSessions
        else homeSessions.filter { it.key.serverId == scopedServerId }
    }

    var confirmAction by remember { mutableStateOf<ConfirmAction?>(null) }
    var isComposerActive by remember { mutableStateOf(false) }
    var isSearchExpanded by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    val requestedHydrationKeys = remember { mutableSetOf<String>() }

    // Auto-hydrate any visible session that doesn't have stats yet. Runs on
    // first composition and whenever the visible set changes.
    val visibleIds = recentSessions.map { "${it.key.serverId}/${it.key.threadId}" }
    LaunchedEffect(visibleIds) {
        for (session in recentSessions) {
            if (session.stats != null) continue
            val id = "${session.key.serverId}/${session.key.threadId}"
            if (!requestedHydrationKeys.add(id)) continue
            scope.launch {
                runCatching {
                    appModel.client.readThread(
                        session.key.serverId,
                        uniffi.codex_mobile_client.AppReadThreadRequest(
                            threadId = session.key.threadId,
                            includeTurns = true,
                        ),
                    )
                    appModel.refreshSnapshot()
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Sessions list fills the whole screen, with top/bottom content padding
        // so items don't sit under the floating chrome.
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                top = 120.dp,
                bottom = 140.dp,
            ),
        ) {
            if (recentSessions.isNotEmpty()) {
                items(recentSessions, key = { "${it.key.serverId}/${it.key.threadId}" }) { session ->
                    val id = "${session.key.serverId}/${session.key.threadId}"
                    val isHydrating = session.stats == null && id in requestedHydrationKeys
                    SwipeToHideRow(
                        onHide = {
                            val key = PinnedThreadKey(
                                serverId = session.key.serverId,
                                threadId = session.key.threadId,
                            )
                            SavedThreadsStore.hide(context, key)
                            hiddenKeys = SavedThreadsStore.hiddenKeys(context)
                            pinnedKeys = SavedThreadsStore.pinnedKeys(context)
                        },
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .animateItem(),
                    ) {
                        SessionCard(
                            session = session,
                            isHydrating = isHydrating,
                            onClick = {
                                appModel.launchState.updateCurrentCwd(session.cwd)
                                onOpenConversation(session.key)
                            },
                            onDelete = {
                                confirmAction = ConfirmAction.ArchiveSession(session)
                            },
                        )
                    }
                }
            } else {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 48.dp, bottom = 8.dp, start = 16.dp, end = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = "No sessions yet",
                            color = LitterTheme.textSecondary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            text = if (servers.isEmpty())
                                "Connect a server to start your first session."
                            else
                                "Pick a project and send a message to start one.",
                            color = LitterTheme.textMuted,
                            fontSize = 12.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                    }
                }
            }
        }

        // Top chrome: header + server pill row, floating over the list with a scrim.
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        colors = listOf(
                            LitterTheme.background.copy(alpha = 0.7f),
                            LitterTheme.background.copy(alpha = 0.7f),
                            androidx.compose.ui.graphics.Color.Transparent,
                        ),
                    ),
                ),
        ) {
            Spacer(Modifier.height(16.dp))
            val tierIcons by com.litter.android.state.TipJarSupporterState.tierIcons
            LaunchedEffect(Unit) {
                com.litter.android.state.TipJarSupporterState.refresh(context)
            }
            val leftKitties = tierIcons.take(2).filterNotNull()
            val rightKitties = tierIcons.drop(2).filterNotNull()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onShowSettings, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = LitterTheme.textSecondary,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Spacer(Modifier.weight(1f))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    leftKitties.forEach { iconRes ->
                        androidx.compose.foundation.Image(
                            painter = androidx.compose.ui.res.painterResource(iconRes),
                            contentDescription = "Supporter",
                            modifier = Modifier
                                .size(28.dp)
                                .clickable { showTipJar = true },
                        )
                    }
                    if (leftKitties.isNotEmpty()) Spacer(Modifier.width(4.dp))
                    com.litter.android.ui.AnimatedLogo(size = 64.dp)
                    if (rightKitties.isNotEmpty()) Spacer(Modifier.width(4.dp))
                    rightKitties.forEach { iconRes ->
                        androidx.compose.foundation.Image(
                            painter = androidx.compose.ui.res.painterResource(iconRes),
                            contentDescription = "Supporter",
                            modifier = Modifier
                                .size(28.dp)
                                .clickable { showTipJar = true },
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                // Invisible spacer mirrors the settings button so the logo
                // stays centered.
                Spacer(Modifier.size(width = 32.dp, height = 32.dp))
            }
            Spacer(Modifier.height(8.dp))

            ServerPillRow(
                servers = servers,
                selectedServerId = selectedProject?.serverId ?: selectedServerId,
                onTap = onSelectServer,
                onReconnect = { server ->
                    scope.launch {
                        lifecycleController.reconnectServer(context, appModel, server.serverId)
                    }
                },
                onRename = { server ->
                    renameText = server.displayName
                    renameTarget = server
                },
                onRemove = { server ->
                    confirmAction = ConfirmAction.DisconnectServer(server)
                },
                onAdd = onShowDiscovery,
            )

            // Short fade at the bottom of the top scrim for a soft transition.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(16.dp)
                    .background(
                        androidx.compose.ui.graphics.Brush.verticalGradient(
                            colors = listOf(
                                LitterTheme.background.copy(alpha = 0.7f),
                                androidx.compose.ui.graphics.Color.Transparent,
                            ),
                        ),
                    ),
            )
        }

        // Search results overlay appears while the search bar is expanded.
        // Full-screen opaque background so the sessions list behind is hidden.
        if (isSearchExpanded) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(LitterTheme.background),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(
                            top = 120.dp,
                            bottom = 180.dp,
                            start = 12.dp,
                            end = 12.dp,
                        ),
                ) {
                    ThreadSearchResults(
                        sessions = allSessions,
                        pinnedKeys = pinnedKeys.toSet(),
                        query = searchQuery,
                        onPin = { session ->
                            val key = PinnedThreadKey(
                                serverId = session.key.serverId,
                                threadId = session.key.threadId,
                            )
                            SavedThreadsStore.add(context, key)
                            pinnedKeys = SavedThreadsStore.pinnedKeys(context)
                        },
                        onUnpin = { session ->
                            val key = PinnedThreadKey(
                                serverId = session.key.serverId,
                                threadId = session.key.threadId,
                            )
                            SavedThreadsStore.remove(context, key)
                            pinnedKeys = SavedThreadsStore.pinnedKeys(context)
                        },
                    )
                }
            }
        }

        // Bottom chrome: search bar + project chip + composer, floating over
        // the list with a scrim.
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(24.dp)
                    .background(
                        androidx.compose.ui.graphics.Brush.verticalGradient(
                            colors = listOf(
                                androidx.compose.ui.graphics.Color.Transparent,
                                LitterTheme.background.copy(alpha = 0.7f),
                            ),
                        ),
                    ),
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(LitterTheme.background.copy(alpha = 0.7f)),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ThreadSearchBar(
                        query = searchQuery,
                        isExpanded = isSearchExpanded,
                        onQueryChange = { searchQuery = it },
                        onExpandChange = { expanded ->
                            isSearchExpanded = expanded
                            if (!expanded) searchQuery = ""
                        },
                    )
                }
                androidx.compose.animation.AnimatedVisibility(
                    visible = isComposerActive && !isSearchExpanded,
                    enter = androidx.compose.animation.fadeIn() +
                        androidx.compose.animation.expandVertically(),
                    exit = androidx.compose.animation.fadeOut() +
                        androidx.compose.animation.shrinkVertically(),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ProjectChip(
                            project = selectedProject,
                            disabled = servers.isEmpty(),
                            onTap = onOpenProjectPicker,
                        )
                    }
                }
                HomeComposerBar(
                    project = selectedProject,
                    onThreadCreated = onThreadCreated,
                    onActiveChange = { active -> isComposerActive = active },
                )
            }
        }

    }

    confirmAction?.let { action ->
        AlertDialog(
            onDismissRequest = { confirmAction = null },
            title = { Text(action.title) },
            text = { Text(action.message) },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        when (action) {
                            is ConfirmAction.ArchiveSession -> {
                                voiceController.stopVoiceSessionIfActive(appModel, action.session.key)
                                voiceController.clearPinnedLocalVoiceThreadIfMatches(appModel, action.session.key)
                                if (appModel.snapshot.value?.activeThread == action.session.key) {
                                    appModel.store.setActiveThread(null)
                                }
                                try {
                                    appModel.client.archiveThread(
                                        action.session.key.serverId,
                                        uniffi.codex_mobile_client.AppArchiveThreadRequest(
                                            threadId = action.session.key.threadId,
                                        ),
                                    )
                                } catch (_: Exception) {}
                                kotlinx.coroutines.delay(400L)
                                appModel.refreshSnapshot()
                            }
                            is ConfirmAction.DisconnectServer -> {
                                SavedServerStore.remove(context, action.server.serverId)
                                appModel.sshSessionStore.close(action.server.serverId)
                                appModel.serverBridge.disconnectServer(action.server.serverId)
                                appModel.refreshSnapshot()
                            }
                        }
                    }
                    confirmAction = null
                }) {
                    Text("Confirm", color = LitterTheme.danger)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmAction = null }) {
                    Text("Cancel")
                }
            },
        )
    }
    renameTarget?.let { server ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("Rename Server") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    label = { Text("Name") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val trimmed = renameText.trim()
                    if (trimmed.isEmpty()) return@TextButton
                    scope.launch {
                        SavedServerStore.rename(context, server.serverId, trimmed)
                        appModel.refreshSnapshot()
                    }
                    renameTarget = null
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) {
                    Text("Cancel")
                }
            },
        )
    }
    if (showTipJar) {
        ModalBottomSheet(
            onDismissRequest = { showTipJar = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = LitterTheme.background,
        ) {
            com.litter.android.ui.settings.TipJarScreen(onBack = { showTipJar = false })
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SessionCard(
    session: AppSessionSummary,
    isHydrating: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }

    val dotState = when {
        session.hasActiveTurn -> com.litter.android.ui.common.StatusDotState.ACTIVE
        isHydrating -> com.litter.android.ui.common.StatusDotState.PENDING
        session.stats != null -> com.litter.android.ui.common.StatusDotState.OK
        else -> com.litter.android.ui.common.StatusDotState.IDLE
    }

    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(LitterTheme.surface, RoundedCornerShape(10.dp))
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = { showMenu = true },
                )
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            com.litter.android.ui.common.StatusDot(state = dotState, size = 8.dp)
            Spacer(Modifier.width(8.dp))

            Column(modifier = Modifier.weight(1f)) {
                // First line: title only.
                com.litter.android.ui.common.FormattedText(
                    text = session.displayTitle,
                    color = LitterTheme.textPrimary,
                    fontSize = 14.sp,
                    maxLines = 1,
                )
                // Second line: time · server · workspace.
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val relative = HomeDashboardSupport.relativeTime(session.updatedAt)
                    if (relative.isNotEmpty()) {
                        Text(
                            text = relative,
                            color = LitterTheme.textMuted,
                            fontSize = 11.sp,
                        )
                    }
                    Text(
                        text = session.serverDisplayName,
                        color = LitterTheme.textSecondary,
                        fontSize = 11.sp,
                    )
                    session.cwd?.let { cwd ->
                        Text(
                            text = HomeDashboardSupport.workspaceLabel(cwd),
                            color = LitterTheme.textMuted,
                            fontSize = 11.sp,
                        )
                    }
                    if (session.hasActiveTurn) {
                        Text(
                            text = "thinking",
                            color = LitterTheme.accent,
                            fontSize = 11.sp,
                        )
                    }
                }
            }

            Box {
                IconButton(
                    onClick = { showMenu = true },
                    modifier = Modifier.size(28.dp),
                ) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = "Session actions",
                        tint = LitterTheme.textSecondary,
                    )
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("Delete") },
                        onClick = { showMenu = false; onDelete() },
                    )
                }
            }
        }
    }
}

/**
 * Merge rule: pinned threads first (preserving pin order — newest-pinned at
 * top), then fill from recent sessions (dedup) to reach 10 total. If the
 * user has pinned more than 10 we show every pin and skip the fill.
 */
private fun mergeHomeSessions(
    pinned: List<PinnedThreadKey>,
    hidden: List<PinnedThreadKey>,
    allSessions: List<AppSessionSummary>,
): List<AppSessionSummary> {
    val hiddenSet = hidden.toSet()
    val candidates = allSessions.filter {
        PinnedThreadKey(serverId = it.key.serverId, threadId = it.key.threadId) !in hiddenSet
    }
    val byKey = candidates.associateBy {
        PinnedThreadKey(serverId = it.key.serverId, threadId = it.key.threadId)
    }
    val pinnedSessions = pinned.mapNotNull { byKey[it] }
    if (pinnedSessions.size >= 10) return pinnedSessions

    val pinnedSet = pinned.toSet()
    val fill = candidates
        .asSequence()
        .filter {
            PinnedThreadKey(serverId = it.key.serverId, threadId = it.key.threadId) !in pinnedSet
        }
        .take(10 - pinnedSessions.size)
        .toList()
    return pinnedSessions + fill
}

private sealed class ConfirmAction {
    abstract val title: String
    abstract val message: String

    data class ArchiveSession(val session: AppSessionSummary) : ConfirmAction() {
        override val title = "Delete Session"
        override val message = "Are you sure you want to delete this session?"
    }

    data class DisconnectServer(val server: AppServerSnapshot) : ConfirmAction() {
        override val title = "Disconnect Server"
        override val message = "Disconnect from ${server.displayName}?"
    }
}
