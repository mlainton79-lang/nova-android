package com.mlainton.nova

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mlainton.nova.ui.ScreenLoadState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ViewModel for the Tony Status screen.
 *
 * Architecture pattern that every future engine screen reuses:
 * - Per-source `ScreenLoadState` (status + worker_log here; future
 *   screens will have their own per-source fields).
 * - Public state via `mutableStateOf` for now (StateFlow deferred — see
 *   2026-06-04 architecture lock-in). Compose snapshot system handles
 *   atomicity for each independent var field; migrating to StateFlow
 *   later doesn't touch any of the foundation primitives.
 * - `refresh()` fires every source in parallel via separate
 *   viewModelScope.launch blocks. `withContext(Dispatchers.IO)` wraps
 *   the synchronous NovaApiClient methods.
 * - Per-source retry methods (`retryStatus`, `retryWorkerLog`) for the
 *   LoadStateSection's onRetry callback.
 * - ApiCall<T> → ScreenLoadState<T> mapping is the single switch point.
 *   Domain payloads never carry transport errors.
 *
 * The Refreshing state preserves prior data during in-flight refreshes so
 * the screen never blanks under a spinner.
 */
class TonyStatusViewModel : ViewModel() {

    var status: ScreenLoadState<TonyStatusResult> by mutableStateOf(ScreenLoadState.Loading)
        private set

    var workerLog: ScreenLoadState<WorkerLogResult> by mutableStateOf(ScreenLoadState.Loading)
        private set

    init {
        refresh()
    }

    /** Fire both sources in parallel. Each source transitions independently. */
    fun refresh() {
        loadStatus()
        loadWorkerLog()
    }

    fun retryStatus() = loadStatus()
    fun retryWorkerLog() = loadWorkerLog()

    private fun loadStatus() {
        status = transitionToRefreshingOrLoading(status)
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { NovaApiClient.getTonyStatus() }
            status = mapToLoadState(result, previous = status.previousData())
        }
    }

    private fun loadWorkerLog() {
        workerLog = transitionToRefreshingOrLoading(workerLog)
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { NovaApiClient.getWorkerLogRecent() }
            workerLog = mapToLoadState(result, previous = workerLog.previousData())
        }
    }

    /**
     * If we already have data, transition to Refreshing (preserving the
     * data + refreshedAt). Otherwise stay at Loading. This is the
     * stale-data refresh behaviour: an in-flight refresh never blanks
     * useful diagnostics.
     */
    private fun <T> transitionToRefreshingOrLoading(current: ScreenLoadState<T>): ScreenLoadState<T> {
        return when (current) {
            is ScreenLoadState.Loaded -> ScreenLoadState.Refreshing(
                data = current.data,
                refreshedAt = current.refreshedAt,
            )
            is ScreenLoadState.Refreshing -> current // already refreshing — no-op
            is ScreenLoadState.Error -> {
                val prev = current.previousData
                if (prev != null) {
                    ScreenLoadState.Refreshing(data = prev, refreshedAt = current.refreshedAt)
                } else {
                    ScreenLoadState.Loading
                }
            }
            ScreenLoadState.Loading -> ScreenLoadState.Loading
        }
    }

    private fun <T> mapToLoadState(
        result: ApiCall<T>,
        previous: T?,
    ): ScreenLoadState<T> {
        val now = System.currentTimeMillis()
        return when (result) {
            is ApiCall.Success -> ScreenLoadState.Loaded(
                data = result.body,
                refreshedAt = now,
            )
            is ApiCall.Failure -> ScreenLoadState.Error(
                message = result.message,
                previousData = previous,
                refreshedAt = now,
            )
        }
    }
}

/** Extract the most-recently-known data payload, if any. */
private fun <T> ScreenLoadState<T>.previousData(): T? = when (this) {
    is ScreenLoadState.Loaded -> data
    is ScreenLoadState.Refreshing -> data
    is ScreenLoadState.Error -> previousData
    ScreenLoadState.Loading -> null
}
