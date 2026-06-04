package com.mlainton.nova.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Generic load-state-aware section card. The load-bearing foundation
 * primitive that every engine screen reuses for any asynchronous data
 * source — capabilities, gaps, builder-pending, agent-run, etc.
 *
 * Owns: section title, Material card chrome, Loading / Loaded /
 * Refreshing / Error rendering branches, optional empty-state slot,
 * optional per-source retry button.
 *
 * Caller owns: the typed `content` composable that knows how to render
 * the loaded data. Caller never reimplements the load-state switch.
 *
 * `Refreshing<T>` renders content the same way `Loaded<T>` does, but
 * with a small spinner next to the title so the user sees "stale data
 * being refreshed" without losing the diagnostics underneath.
 *
 * `empty` slot fires when `isEmpty(data) == true` AND `empty != null`.
 * Default `isEmpty = { false }` means content always renders unless a
 * caller opts in. Worker_log uses it: zero rows is a legitimate empty
 * state, not an error.
 */
@Composable
fun <T> LoadStateSection(
    title: String,
    state: ScreenLoadState<T>,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
    isEmpty: (T) -> Boolean = { false },
    empty: (@Composable () -> Unit)? = null,
    content: @Composable (T) -> Unit,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                if (state is ScreenLoadState.Refreshing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                    )
                }
            }
            when (state) {
                ScreenLoadState.Loading -> LoadingPlaceholder()
                is ScreenLoadState.Loaded -> renderData(state.data, isEmpty, empty, content)
                is ScreenLoadState.Refreshing -> renderData(state.data, isEmpty, empty, content)
                is ScreenLoadState.Error -> {
                    // Honour the previousData contract from ScreenLoadState.Error<T>:
                    // a failed refresh after a successful load shows the stale data
                    // first (exactly as it rendered when Loaded), then the error
                    // indicator with retry. The user sees what was last good plus
                    // what failed, instead of losing the diagnostics under the error.
                    state.previousData?.let { prev ->
                        renderData(prev, isEmpty, empty, content)
                    }
                    ErrorContent(message = state.message, onRetry = onRetry)
                }
            }
        }
    }
}

@Composable
private fun <T> renderData(
    data: T,
    isEmpty: (T) -> Boolean,
    empty: (@Composable () -> Unit)?,
    content: @Composable (T) -> Unit,
) {
    if (empty != null && isEmpty(data)) {
        empty()
    } else {
        content(data)
    }
}
