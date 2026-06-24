package com.mlainton.nova

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Safe, read-only projection of GET /api/v1/approvals/pending. */
@Serializable
data class ApprovalInboxResult(
    val ok: Boolean,
    val count: Int,
    @SerialName("pending_approvals") val pendingApprovals: List<PendingApproval>,
)

/** Only fields explicitly approved for display are represented here. */
@Serializable
data class PendingApproval(
    @SerialName("pending_id") val pendingId: String,
    @SerialName("capability_key") val capabilityKey: String,
    val status: String,
    @SerialName("expires_at") val expiresAt: String,
    @SerialName("action_snapshot") val actionSnapshot: ApprovalActionSnapshot? = null,
)

@Serializable
data class ApprovalActionSnapshot(
    @SerialName("step_summary") val stepSummary: String? = null,
    @SerialName("action_type") val actionType: String? = null,
)

/** Sanitized response from the rejection endpoint. */
@Serializable
data class ApprovalRejectResult(
    val ok: Boolean,
    val rejected: Boolean,
    val status: String,
    val message: String,
)
