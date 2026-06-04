package com.mlainton.nova.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Single label-value row, optionally with a trailing status badge.
 *
 * Used everywhere a section needs to render "label: value" pairs. Null
 * value renders as "—" so callers don't need to handle the null case in
 * every section — the graceful-degrade contract from the backend
 * propagates through to the UI without extra plumbing.
 *
 * Foundation primitive: reused by every engine screen.
 */
@Composable
fun StatusRow(
    label: String,
    value: String?,
    badge: StatusBadgeData? = null,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(160.dp),
        )
        Text(
            text = value?.takeIf { it.isNotBlank() } ?: "—",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        if (badge != null) {
            Spacer(modifier = Modifier.width(4.dp))
            StatusBadge(data = badge)
        }
    }
}
