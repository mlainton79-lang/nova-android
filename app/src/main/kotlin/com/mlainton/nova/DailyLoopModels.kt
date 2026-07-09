package com.mlainton.nova

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TodayBriefResult(
    val ok: Boolean = true,
    val briefing: String = "",
    @SerialName("next_actions") val nextActions: List<String> = emptyList(),
    @SerialName("health_flags") val healthFlags: List<DailyFlag> = emptyList(),
    @SerialName("approval_cards") val approvalCards: List<ApprovalCard> = emptyList(),
)

@Serializable
data class DailyFlag(
    val key: String? = null,
    val code: String? = null,
    val title: String? = null,
    val message: String? = null,
    val severity: String? = null,
)

@Serializable
data class ApprovalCard(
    @SerialName("pending_id") val pendingId: String? = null,
    val title: String? = null,
    val summary: String? = null,
    @SerialName("risk_level") val riskLevel: String? = null,
    val status: String? = null,
)

@Serializable
data class DailyReviewResult(
    val ok: Boolean = true,
    val review: String = "",
    @SerialName("follow_up_actions") val followUpActions: List<String> = emptyList(),
)

@Serializable
data class DailyLoopQualityResult(
    val ok: Boolean = true,
    val score: Double = 0.0,
    val status: String = "unknown",
    val surfaces: List<QualitySurface> = emptyList(),
)

@Serializable
data class QualitySurface(
    val surface: String = "unknown",
    val score: Double? = null,
    val passed: Int? = null,
    val total: Int? = null,
    val status: String? = null,
    val judge: String? = null,
)

@Serializable
data class CaptureNoteResult(
    val ok: Boolean = false,
    val saved: Boolean = false,
    val status: String = "unknown",
    val error: String? = null,
    val category: String? = null,
)
