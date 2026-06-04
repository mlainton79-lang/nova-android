package com.mlainton.nova.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Severity classes for status indicators. Maps backend status strings
 * (`"ok"`, `"missing_key"`, `"timeout"`, `"refused_governor"`, `"valid"`,
 * `"expired"`, etc.) to one of four visual buckets. Future screens get
 * the same visual language for free by reusing this enum.
 */
enum class Severity { OK, WARN, ERROR, NEUTRAL }

/** Plain-data carrier so badges can be passed as values rather than slots. */
data class StatusBadgeData(val text: String, val severity: Severity)

/**
 * Compact colored pill. Reused everywhere a section needs a status hint.
 *
 * Colors are derived from MaterialTheme so they follow theme changes
 * (light/dark) without per-call tuning. WARN currently borrows the tertiary
 * container — that's a Material3 design choice we may revisit when we add a
 * Nova-branded theme; for now it's distinct from OK and ERROR and that's
 * what matters.
 */
@Composable
fun StatusBadge(data: StatusBadgeData, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val (bg, fg) = when (data.severity) {
        Severity.OK      -> scheme.primaryContainer    to scheme.onPrimaryContainer
        Severity.WARN    -> scheme.tertiaryContainer   to scheme.onTertiaryContainer
        Severity.ERROR   -> scheme.errorContainer      to scheme.onErrorContainer
        Severity.NEUTRAL -> scheme.surfaceVariant      to scheme.onSurfaceVariant
    }
    Text(
        text = data.text,
        style = MaterialTheme.typography.labelSmall,
        color = fg,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .padding(horizontal = 8.dp, vertical = 2.dp)
    )
}

/**
 * Map a backend status string to a Severity. Defaults to NEUTRAL for
 * unknown values so a new backend status string never crashes the screen.
 * Extend as new status strings appear.
 */
fun String?.toSeverity(): Severity = when (this?.lowercase()) {
    "ok", "valid", "active", "ready", "completed", "success", "running" -> Severity.OK
    "expiring_soon", "warn", "warning", "pending", "queued", "refreshing" -> Severity.WARN
    "expired", "error", "failed", "timeout", "missing_key",
    "refused_governor", "refused_safe_mode", "crashed", "abandoned",
    "removed" -> Severity.ERROR
    else -> Severity.NEUTRAL
}

/** Convenience: build a StatusBadgeData from a status string, or null. */
fun String?.toBadge(): StatusBadgeData? =
    this?.takeIf { it.isNotBlank() }?.let { StatusBadgeData(it, it.toSeverity()) }

@Suppress("unused") // for ignoring Color import-style warnings on minimal previews
private val _retainImports: Color = Color.Transparent
