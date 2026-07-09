package com.mlainton.nova

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mlainton.nova.ui.LoadStateSection
import com.mlainton.nova.ui.ScreenLoadState
import com.mlainton.nova.ui.StatusBadge
import com.mlainton.nova.ui.toBadge

@Composable
fun DailyLoopScreen(
    today: ScreenLoadState<TodayBriefResult>,
    review: ScreenLoadState<DailyReviewResult>,
    quality: ScreenLoadState<DailyLoopQualityResult>,
    captureText: String,
    captureResult: CaptureNoteResult?,
    captureError: String?,
    captureInFlight: Boolean,
    onCaptureTextChange: (String) -> Unit,
    onSaveCapture: () -> Unit,
    onRefresh: () -> Unit,
    onRetryToday: () -> Unit,
    onRetryReview: () -> Unit,
    onRetryQuality: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Nova Daily", style = MaterialTheme.typography.headlineSmall)
                Text("Capture, Now, Review, Quality", style = MaterialTheme.typography.bodySmall)
            }
            IconButton(onClick = onRefresh) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh")
            }
        }

        CaptureSection(
            text = captureText,
            result = captureResult,
            error = captureError,
            inFlight = captureInFlight,
            onTextChange = onCaptureTextChange,
            onSave = onSaveCapture,
        )

        LoadStateSection(
            title = "Now",
            state = today,
            onRetry = onRetryToday,
            content = { TodayBriefContent(it) },
        )

        LoadStateSection(
            title = "Review",
            state = review,
            onRetry = onRetryReview,
            content = { DailyReviewContent(it) },
        )

        LoadStateSection(
            title = "Quality",
            state = quality,
            onRetry = onRetryQuality,
            content = { DailyQualityContent(it) },
        )
    }
}

@Composable
private fun CaptureSection(
    text: String,
    result: CaptureNoteResult?,
    error: String?,
    inFlight: Boolean,
    onTextChange: (String) -> Unit,
    onSave: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Capture", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = text,
                onValueChange = onTextChange,
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 5,
                label = { Text("Low-risk note") },
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = onSave,
                    enabled = text.isNotBlank() && !inFlight,
                ) {
                    Icon(Icons.Default.Save, contentDescription = null)
                    Text("Save")
                }
                if (inFlight) {
                    CircularProgressIndicator(strokeWidth = 2.dp)
                }
            }
            result?.let {
                val message = when {
                    it.saved -> "Saved."
                    !it.error.isNullOrBlank() -> it.error
                    else -> it.status
                }
                Text(message, style = MaterialTheme.typography.bodySmall)
            }
            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun TodayBriefContent(result: TodayBriefResult) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(result.briefing.ifBlank { "No briefing text returned." })
        if (result.nextActions.isNotEmpty()) {
            LabeledList("Next", result.nextActions)
        }
        if (result.healthFlags.isNotEmpty()) {
            Text("Health", fontWeight = FontWeight.SemiBold)
            result.healthFlags.take(6).forEach { flag ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    flag.severity.toBadge()?.let { StatusBadge(it) }
                    Text(flag.title ?: flag.message ?: flag.key ?: flag.code ?: "Flag")
                }
            }
        }
        if (result.approvalCards.isNotEmpty()) {
            LabeledList("Approvals", result.approvalCards.map { it.displayText() })
        }
    }
}

@Composable
private fun DailyReviewContent(result: DailyReviewResult) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(result.review.ifBlank { "No review text returned." })
        if (result.followUpActions.isNotEmpty()) {
            LabeledList("Follow-ups", result.followUpActions)
        }
    }
}

@Composable
private fun DailyQualityContent(result: DailyLoopQualityResult) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            result.status.toBadge()?.let { StatusBadge(it) }
            Text("Score ${(result.score * 100).toInt()}%")
        }
        result.surfaces.take(8).forEach { surface ->
            val detail = when {
                surface.score != null -> "${(surface.score * 100).toInt()}%"
                surface.passed != null && surface.total != null -> "${surface.passed}/${surface.total}"
                else -> surface.status ?: surface.judge ?: ""
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(surface.surface)
                Text(detail, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun LabeledList(title: String, items: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, fontWeight = FontWeight.SemiBold)
        items.take(6).forEach { item ->
            Text("- $item", style = MaterialTheme.typography.bodyMedium)
        }
    }
    Spacer(modifier = Modifier.height(2.dp))
}

private fun ApprovalCard.displayText(): String {
    val label = title ?: summary ?: pendingId ?: "Approval"
    return riskLevel?.takeIf { it.isNotBlank() }?.let { "$label ($it)" } ?: label
}
