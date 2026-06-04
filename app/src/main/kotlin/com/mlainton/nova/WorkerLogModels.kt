package com.mlainton.nova

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Typed models for `GET /api/v1/admin/worker_log/recent`.
 *
 * Note the response shape is bimodal:
 * - Success: ok=true, all summary + rows + journal fields populated.
 * - Failure: ok=false, error set, rows + journal absent.
 *
 * We tolerate both by declaring `error`, `rows`, `journal` as nullable
 * with default null. Summary fields (hours/total/passed/failed) are
 * always present per the backend's bimodal contract.
 *
 * Backend source-of-truth:
 *   nova-backend/app/api/v1/endpoints/admin_worker_log.py
 */

@Serializable
data class WorkerLogResult(
    val ok: Boolean,
    val hours: Int,
    val total: Int = 0,
    val passed: Int = 0,
    val failed: Int = 0,
    val rows: List<WorkerLogRow> = emptyList(),
    val journal: JournalCounts? = null,
    val error: String? = null,
)

@Serializable
data class WorkerLogRow(
    @SerialName("task_name") val taskName: String,
    val success: Boolean? = null,
    @SerialName("duration_seconds") val durationSeconds: Double? = null,
    @SerialName("ran_at") val ranAt: String,
    @SerialName("detail_preview") val detailPreview: String,
    @SerialName("detail_truncated") val detailTruncated: Boolean,
)

/**
 * Journal counts.
 *
 * Backend may return either:
 * - The success shape: { total, in_window, latest_at } — all set
 * - The error shape: { error } — set when the journal-specific query
 *   failed but the worker_log primary query succeeded
 *
 * Both fields are nullable so deserialisation tolerates either shape.
 * Callers check `error` first.
 */
@Serializable
data class JournalCounts(
    val total: Int? = null,
    @SerialName("in_window") val inWindow: Int? = null,
    @SerialName("latest_at") val latestAt: String? = null,
    val error: String? = null,
)
