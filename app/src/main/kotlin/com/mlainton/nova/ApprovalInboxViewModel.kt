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

class ApprovalInboxViewModel : ViewModel() {

    var state: ScreenLoadState<ApprovalInboxResult> by mutableStateOf(ScreenLoadState.Loading)
        private set

    var rejectingIds: Set<String> by mutableStateOf(emptySet())
        private set

    var rejectionError: String? by mutableStateOf(null)
        private set

    init {
        refresh()
    }

    fun refresh() {
        rejectionError = null
        val previous = when (val current = state) {
            is ScreenLoadState.Loaded -> current.data
            is ScreenLoadState.Refreshing -> current.data
            is ScreenLoadState.Error -> current.previousData
            ScreenLoadState.Loading -> null
        }
        state = previous?.let { ScreenLoadState.Refreshing(it) } ?: ScreenLoadState.Loading

        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { NovaApiClient.getPendingApprovals() }
            state = when (result) {
                is ApiCall.Success -> ScreenLoadState.Loaded(
                    data = result.body,
                    refreshedAt = System.currentTimeMillis(),
                )
                is ApiCall.Failure -> ScreenLoadState.Error(
                    message = result.message,
                    previousData = previous,
                    refreshedAt = System.currentTimeMillis(),
                )
            }
        }
    }

    fun reject(pendingId: String) {
        if (pendingId in rejectingIds) return

        rejectingIds = rejectingIds + pendingId
        rejectionError = null
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                NovaApiClient.rejectPendingApproval(pendingId)
            }
            rejectingIds = rejectingIds - pendingId
            when (result) {
                is ApiCall.Success -> {
                    if (result.body.ok && result.body.rejected) {
                        removeFromCurrentInbox(pendingId)
                        refresh()
                    } else {
                        rejectionError = "This approval could not be rejected. Refresh and try again."
                    }
                }
                is ApiCall.Failure -> {
                    rejectionError = result.message
                }
            }
        }
    }

    private fun removeFromCurrentInbox(pendingId: String) {
        val current = state
        val data = when (current) {
            is ScreenLoadState.Loaded -> current.data
            is ScreenLoadState.Refreshing -> current.data
            is ScreenLoadState.Error -> current.previousData
            ScreenLoadState.Loading -> null
        } ?: return
        val approvals = data.pendingApprovals.filterNot { it.pendingId == pendingId }
        state = ScreenLoadState.Loaded(
            data = data.copy(count = approvals.size, pendingApprovals = approvals),
            refreshedAt = System.currentTimeMillis(),
        )
    }
}
