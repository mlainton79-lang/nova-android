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

    init {
        refresh()
    }

    fun refresh() {
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
}
