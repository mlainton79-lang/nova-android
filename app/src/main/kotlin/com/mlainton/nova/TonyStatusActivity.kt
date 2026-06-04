package com.mlainton.nova

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize

/**
 * Tony Status — the first real Compose screen and the foundation pattern
 * every future engine screen (capabilities, gaps, builder-pending,
 * agent-run) will reuse.
 *
 * Architecture lock-in (2026-06-04, post-Codex review):
 * - Compose UI via ComponentActivity + setContent
 * - kotlinx.serialization for typed JSON parsing (strict config)
 * - ApiCall<T> sealed for HTTP results, mapped to ScreenLoadState<T> in
 *   the ViewModel
 * - ScreenLoadState includes Refreshing<T> so refreshes never blank prior
 *   diagnostics
 * - mutableStateOf for ViewModel state exposure (StateFlow deferred)
 * - Per-source state for partial render
 *
 * Launch from the drawer button in MainActivity (single tap).
 */
class TonyStatusActivity : ComponentActivity() {

    private val viewModel: TonyStatusViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    TonyStatusScreen(
                        status = viewModel.status,
                        workerLog = viewModel.workerLog,
                        onRefresh = viewModel::refresh,
                        onRetryStatus = viewModel::retryStatus,
                        onRetryWorkerLog = viewModel::retryWorkerLog,
                    )
                }
            }
        }
    }
}
