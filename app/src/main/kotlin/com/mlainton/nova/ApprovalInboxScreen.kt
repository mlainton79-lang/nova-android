package com.mlainton.nova

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mlainton.nova.ui.LoadStateSection
import com.mlainton.nova.ui.ScreenLoadState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApprovalInboxScreen(
    state: ScreenLoadState<ApprovalInboxResult>,
    approvingIds: Set<String>,
    rejectingIds: Set<String>,
    approvalError: String?,
    rejectionError: String?,
    onRefresh: () -> Unit,
    onApprove: (String) -> Unit,
    onReject: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Approval Inbox") },
                actions = {
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh approvals")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 12.dp,
                bottom = padding.calculateBottomPadding() + 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (approvalError != null) {
                item {
                    Text(
                        text = approvalError,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            if (rejectionError != null) {
                item {
                    Text(
                        text = rejectionError,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            item {
                LoadStateSection(
                    title = "Pending approvals",
                    state = state,
                    onRetry = onRefresh,
                    isEmpty = { it.pendingApprovals.isEmpty() },
                    empty = { Text("No approvals waiting", modifier = Modifier.padding(top = 12.dp)) },
                ) { result ->
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        result.pendingApprovals.forEach { approval ->
                            ApprovalCard(
                                approval = approval,
                                isApproving = approval.pendingId in approvingIds,
                                isRejecting = approval.pendingId in rejectingIds,
                                onApprove = { onApprove(approval.pendingId) },
                                onReject = { onReject(approval.pendingId) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ApprovalCard(
    approval: PendingApproval,
    isApproving: Boolean,
    isRejecting: Boolean,
    onApprove: () -> Unit,
    onReject: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            ApprovalField("Capability", approval.capabilityKey)
            ApprovalField("Status", approval.status)
            ApprovalField("Expires", approval.expiresAt)
            approval.actionSnapshot?.stepSummary
                ?.takeIf { it.isNotBlank() }
                ?.let { ApprovalField("Step", it) }
            approval.actionSnapshot?.actionType
                ?.takeIf { it.isNotBlank() }
                ?.let { ApprovalField("Action type", it) }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = onApprove,
                    enabled = !isApproving && !isRejecting,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(if (isApproving) "Approving…" else "Approve")
                }
                OutlinedButton(
                    onClick = onReject,
                    enabled = !isRejecting && !isApproving,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(if (isRejecting) "Rejecting…" else "Reject")
                }
            }
        }
    }
}

@Composable
private fun ApprovalField(label: String, value: String) {
    Column {
        Text(text = label, style = MaterialTheme.typography.labelMedium)
        Text(text = value, style = MaterialTheme.typography.bodyLarge)
    }
}
