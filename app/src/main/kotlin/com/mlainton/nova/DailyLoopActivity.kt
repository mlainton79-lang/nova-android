package com.mlainton.nova

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier

class DailyLoopActivity : ComponentActivity() {

    private val viewModel: DailyLoopViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    DailyLoopScreen(
                        today = viewModel.today,
                        review = viewModel.review,
                        quality = viewModel.quality,
                        captureText = viewModel.captureText,
                        captureResult = viewModel.captureResult,
                        captureError = viewModel.captureError,
                        captureInFlight = viewModel.captureInFlight,
                        onCaptureTextChange = viewModel::updateCaptureText,
                        onSaveCapture = viewModel::saveCapture,
                        onRefresh = viewModel::refresh,
                        onRetryToday = viewModel::retryToday,
                        onRetryReview = viewModel::retryReview,
                        onRetryQuality = viewModel::retryQuality,
                    )
                }
            }
        }
    }
}
