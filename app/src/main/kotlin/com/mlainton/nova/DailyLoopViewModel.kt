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

class DailyLoopViewModel : ViewModel() {

    var today: ScreenLoadState<TodayBriefResult> by mutableStateOf(ScreenLoadState.Loading)
        private set

    var review: ScreenLoadState<DailyReviewResult> by mutableStateOf(ScreenLoadState.Loading)
        private set

    var quality: ScreenLoadState<DailyLoopQualityResult> by mutableStateOf(ScreenLoadState.Loading)
        private set

    var captureText: String by mutableStateOf("")
        private set

    var captureResult: CaptureNoteResult? by mutableStateOf(null)
        private set

    var captureError: String? by mutableStateOf(null)
        private set

    var captureInFlight: Boolean by mutableStateOf(false)
        private set

    init {
        refresh()
    }

    fun refresh() {
        loadToday()
        loadReview()
        loadQuality()
    }

    fun retryToday() = loadToday()
    fun retryReview() = loadReview()
    fun retryQuality() = loadQuality()

    fun updateCaptureText(value: String) {
        captureText = value
        if (captureError != null) captureError = null
    }

    fun saveCapture() {
        val text = captureText.trim()
        if (text.isBlank() || captureInFlight) return
        captureInFlight = true
        captureError = null
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                NovaApiClient.captureNote(text)
            }
            when (result) {
                is ApiCall.Success -> {
                    captureResult = result.body
                    if (result.body.saved) captureText = ""
                    loadReview()
                }
                is ApiCall.Failure -> captureError = result.message
            }
            captureInFlight = false
        }
    }

    private fun loadToday() {
        today = transition(today)
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { NovaApiClient.getTodayBrief() }
            today = mapToLoadState(result, today.previousData())
        }
    }

    private fun loadReview() {
        review = transition(review)
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { NovaApiClient.getDailyReview() }
            review = mapToLoadState(result, review.previousData())
        }
    }

    private fun loadQuality() {
        quality = transition(quality)
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { NovaApiClient.getDailyLoopQuality() }
            quality = mapToLoadState(result, quality.previousData())
        }
    }

    private fun <T> transition(current: ScreenLoadState<T>): ScreenLoadState<T> = when (current) {
        is ScreenLoadState.Loaded -> ScreenLoadState.Refreshing(current.data, current.refreshedAt)
        is ScreenLoadState.Refreshing -> current
        is ScreenLoadState.Error -> current.previousData?.let {
            ScreenLoadState.Refreshing(it, current.refreshedAt)
        } ?: ScreenLoadState.Loading
        ScreenLoadState.Loading -> ScreenLoadState.Loading
    }

    private fun <T> mapToLoadState(result: ApiCall<T>, previous: T?): ScreenLoadState<T> {
        val now = System.currentTimeMillis()
        return when (result) {
            is ApiCall.Success -> ScreenLoadState.Loaded(result.body, now)
            is ApiCall.Failure -> ScreenLoadState.Error(result.message, previous, now)
        }
    }
}

private fun <T> ScreenLoadState<T>.previousData(): T? = when (this) {
    is ScreenLoadState.Loaded -> data
    is ScreenLoadState.Refreshing -> data
    is ScreenLoadState.Error -> previousData
    ScreenLoadState.Loading -> null
}
