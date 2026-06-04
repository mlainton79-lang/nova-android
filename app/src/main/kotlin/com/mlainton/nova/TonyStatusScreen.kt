package com.mlainton.nova

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mlainton.nova.ui.LoadStateSection
import com.mlainton.nova.ui.ScreenLoadState
import com.mlainton.nova.ui.StatusRow
import com.mlainton.nova.ui.toBadge

/**
 * Tony Status screen.
 *
 * Composition:
 *   Scaffold (top app bar with refresh)
 *   └── LazyColumn (vertical scroll)
 *       ├── LoadStateSection<TonyStatusResult>(Health)
 *       ├── LoadStateSection<TonyStatusResult>(State)
 *       ├── LoadStateSection<TonyStatusResult>(Infrastructure)
 *       ├── LoadStateSection<TonyStatusResult>(Identity)
 *       └── LoadStateSection<WorkerLogResult>(Overnight cron)
 *
 * Each LoadStateSection passes status / workerLog independently so partial
 * success renders the moment one source returns. Per-source retry routes
 * through ViewModel.retryStatus() / retryWorkerLog().
 *
 * Section content composables (HealthBlockContent, etc.) render only the
 * loaded happy path — they never see the load-state switch.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TonyStatusScreen(
    status: ScreenLoadState<TonyStatusResult>,
    workerLog: ScreenLoadState<WorkerLogResult>,
    onRefresh: () -> Unit,
    onRetryStatus: () -> Unit,
    onRetryWorkerLog: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Tony Status") },
                actions = {
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                    }
                },
            )
        },
    ) { padding ->
        TonyStatusBody(
            status = status,
            workerLog = workerLog,
            onRetryStatus = onRetryStatus,
            onRetryWorkerLog = onRetryWorkerLog,
            contentPadding = padding,
        )
    }
}

@Composable
private fun TonyStatusBody(
    status: ScreenLoadState<TonyStatusResult>,
    workerLog: ScreenLoadState<WorkerLogResult>,
    onRetryStatus: () -> Unit,
    onRetryWorkerLog: () -> Unit,
    contentPadding: PaddingValues,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 8.dp + contentPadding.calculateTopPadding(),
            bottom = 16.dp + contentPadding.calculateBottomPadding(),
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            LoadStateSection(
                title = "Health",
                state = status,
                onRetry = onRetryStatus,
            ) { result -> HealthBlockContent(result.health) }
        }
        item {
            LoadStateSection(
                title = "State",
                state = status,
                onRetry = onRetryStatus,
            ) { result -> StateBlockContent(result.state) }
        }
        item {
            LoadStateSection(
                title = "Infrastructure",
                state = status,
                onRetry = onRetryStatus,
            ) { result -> InfraBlockContent(result.infrastructure) }
        }
        item {
            LoadStateSection(
                title = "Identity",
                state = status,
                onRetry = onRetryStatus,
            ) { result -> IdentityBlockContent(result.identity) }
        }
        item {
            LoadStateSection(
                title = "Overnight cron",
                state = workerLog,
                onRetry = onRetryWorkerLog,
                isEmpty = { it.total == 0 },
                empty = { WorkerLogEmpty() },
            ) { result -> WorkerLogContent(result) }
        }
    }
}

// ── Section content composables ─────────────────────────────────────
// Each renders only the loaded happy path. Null sub-fields surface as
// StatusRow's "—" placeholder per the graceful-degrade contract.

@Composable
private fun HealthBlockContent(health: HealthBlock) {
    Column {
        StatusRow("Backend uptime", value = formatUptime(health.backend.uptimeSeconds))
        StatusRow(
            label = "Database",
            value = health.database.latencyMs?.let { "${it}ms" },
            badge = health.database.status.toBadge(),
        )
        if (health.providers.isNotEmpty()) {
            SectionSubheader("Providers")
            health.providers.forEach { provider ->
                StatusRow(
                    label = provider.name,
                    value = if (provider.configured) "configured" else "missing key",
                    badge = provider.status.toBadge(),
                )
            }
        }
        if (health.externalServices.isNotEmpty()) {
            SectionSubheader("External services")
            health.externalServices.forEach { service ->
                StatusRow(
                    label = service.name,
                    value = null,
                    badge = service.status.toBadge(),
                )
            }
        }
    }
}

@Composable
private fun StateBlockContent(state: StateBlock) {
    Column {
        StatusRow("Living memory", value = state.lastMemoryWrite.tonyLivingMemory)
        StatusRow("Facts", value = state.lastMemoryWrite.tonyFacts)
        StatusRow("Episodic memory", value = state.lastMemoryWrite.tonyEpisodicMemory)
        StatusRow("Semantic memories", value = state.lastMemoryWrite.semanticMemories)
        StatusRow("Codebase (frontend)", value = state.lastCodebaseSyncFrontend)
        StatusRow("Codebase (backend)", value = state.lastCodebaseSyncBackend)
        StatusRow("Pending actions", value = state.pendingActionsCount?.toString())

        if (state.gmailAccounts.isNotEmpty()) {
            SectionSubheader("Gmail")
            state.gmailAccounts.forEach { account ->
                StatusRow(
                    label = account.email,
                    value = account.tokenExpiry,
                    badge = account.status.toBadge(),
                )
            }
        }

        if (state.recentActivity.isNotEmpty()) {
            SectionSubheader("Recent activity")
            state.recentActivity.forEach { activity ->
                StatusRow(
                    label = activity.actionType ?: "(unknown)",
                    value = activity.summary ?: activity.trigger,
                    badge = activity.status.toBadge(),
                )
            }
        }
    }
}

@Composable
private fun InfraBlockContent(infra: InfraBlock) {
    Column {
        WorkflowRowGroup("Backup workflow", infra.backupWorkflow)
        WorkflowRowGroup("Restore drill workflow", infra.restoreDrillWorkflow)
        StatusRow(
            label = "ElevenLabs",
            value = infra.elevenlabs?.latencyMs?.let { "${it}ms" },
            badge = infra.elevenlabs?.status?.toBadge(),
        )
        infra.elevenlabs?.error?.let { StatusRow(" → error", value = it) }
    }
}

@Composable
private fun WorkflowRowGroup(label: String, workflow: WorkflowStatus?) {
    if (workflow == null) {
        StatusRow(label, value = null)
        return
    }
    val run = workflow.lastRun
    StatusRow(
        label = label,
        value = run?.conclusion ?: run?.status ?: workflow.status,
        badge = (run?.conclusion ?: workflow.status).toBadge(),
    )
    if (run?.ageHours != null) {
        StatusRow(" → age", value = formatHoursAgo(run.ageHours))
    }
    workflow.error?.let { StatusRow(" → error", value = it) }
}

@Composable
private fun IdentityBlockContent(identity: IdentityBlock) {
    Column {
        StatusRow("Version", value = identity.backendVersion)
        StatusRow("Commit", value = identity.backendCommitSha.take(12))
        StatusRow("Deployed", value = identity.backendDeployTime)
        if (identity.activeFeatureFlags.isNotEmpty()) {
            SectionSubheader("Feature flags")
            identity.activeFeatureFlags.forEach { (key, value) ->
                StatusRow(
                    label = key,
                    value = if (value) "on" else "off",
                    badge = (if (value) "on" else "off").toBadge(),
                )
            }
        }
    }
}

@Composable
private fun WorkerLogContent(result: WorkerLogResult) {
    Column {
        StatusRow(
            label = "Summary",
            value = "${result.passed}/${result.total} passed, ${result.failed} failed",
            badge = if (result.failed == 0 && result.passed > 0) "ok".toBadge()
                else if (result.failed > 0) "failed".toBadge()
                else null,
        )
        result.journal?.let { journal ->
            if (journal.error != null) {
                StatusRow("Journal", value = "query failed", badge = "error".toBadge())
                StatusRow(" → error", value = journal.error)
            } else {
                StatusRow(
                    label = "Journal entries",
                    value = "${journal.inWindow ?: 0} in window / ${journal.total ?: 0} total",
                )
                StatusRow(" → latest", value = journal.latestAt)
            }
        }
        if (result.rows.isNotEmpty()) {
            SectionSubheader("Tasks")
            result.rows.forEach { row ->
                StatusRow(
                    label = row.taskName,
                    value = row.durationSeconds?.let { "%.1fs".format(it) },
                    badge = when (row.success) {
                        true  -> "ok".toBadge()
                        false -> "failed".toBadge()
                        null  -> null
                    },
                )
            }
        }
    }
}

@Composable
private fun WorkerLogEmpty() {
    Text(
        text = "No worker runs in window.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 8.dp),
    )
}

@Composable
private fun SectionSubheader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
    )
}

// ── Formatters ──────────────────────────────────────────────────────

private fun formatUptime(seconds: Int): String {
    if (seconds < 60) return "${seconds}s"
    val minutes = seconds / 60
    if (minutes < 60) return "${minutes}m"
    val hours = minutes / 60
    if (hours < 24) return "${hours}h ${minutes % 60}m"
    val days = hours / 24
    return "${days}d ${hours % 24}h"
}

private fun formatHoursAgo(hours: Double): String = when {
    hours < 1.0 -> "${(hours * 60).toInt()}m ago"
    hours < 48.0 -> "%.1fh ago".format(hours)
    else -> "%.1fd ago".format(hours / 24.0)
}
