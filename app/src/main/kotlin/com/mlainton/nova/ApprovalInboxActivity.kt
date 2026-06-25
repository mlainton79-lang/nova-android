package com.mlainton.nova

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier

class ApprovalInboxActivity : ComponentActivity() {

    private val viewModel: ApprovalInboxViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ApprovalInboxScreen(
                        state = viewModel.state,
                        approvingIds = viewModel.approvingIds,
                        rejectingIds = viewModel.rejectingIds,
                        approvalError = viewModel.approvalError,
                        rejectionError = viewModel.rejectionError,
                        onRefresh = viewModel::refresh,
                        onApprove = viewModel::approve,
                        onReject = viewModel::reject,
                    )
                }
            }
        }
    }
}
