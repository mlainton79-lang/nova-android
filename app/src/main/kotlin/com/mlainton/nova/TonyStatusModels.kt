package com.mlainton.nova

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Typed models for `GET /api/v1/status`.
 *
 * Discipline (matches NovaJson.strict):
 * - Required-by-contract fields: non-nullable, no default. Missing key →
 *   SerializationException (caught at the API-layer boundary as
 *   ApiCall.Failure). This surfaces backend contract drift instead of
 *   silently rendering "—" everywhere.
 * - Graceful-degrade sub-blocks (whole block can be null when a sub-check
 *   timed out): `T? = null`. Backend either sends the block or omits it /
 *   sends null.
 * - Lists from the backend are always present (possibly empty). Declared
 *   without default so missing-key surfaces as a parse failure.
 *
 * Backend shape source-of-truth: nova-backend/app/api/v1/endpoints/status.py
 */

@Serializable
data class TonyStatusResult(
    val ok: Boolean,
    @SerialName("generated_at") val generatedAt: String,
    val health: HealthBlock,
    val state: StateBlock,
    val infrastructure: InfraBlock,
    val identity: IdentityBlock,
)

// ── Health ───────────────────────────────────────────────────────────

@Serializable
data class HealthBlock(
    val backend: BackendHealth,
    val database: DatabaseHealth,
    val providers: List<ProviderHealth>,
    @SerialName("external_services") val externalServices: List<ServiceHealth>,
)

@Serializable
data class BackendHealth(
    val status: String,
    @SerialName("uptime_seconds") val uptimeSeconds: Int,
)

@Serializable
data class DatabaseHealth(
    val status: String,
    @SerialName("latency_ms") val latencyMs: Int? = null,
    val error: String? = null,
)

@Serializable
data class ProviderHealth(
    val name: String,
    val configured: Boolean,
    val status: String,
)

@Serializable
data class ServiceHealth(
    val name: String,
    val status: String,
)

// ── State ────────────────────────────────────────────────────────────

@Serializable
data class StateBlock(
    @SerialName("last_memory_write") val lastMemoryWrite: LastMemoryWrite,
    @SerialName("last_codebase_sync_frontend") val lastCodebaseSyncFrontend: String? = null,
    @SerialName("last_codebase_sync_backend") val lastCodebaseSyncBackend: String? = null,
    @SerialName("pending_actions_count") val pendingActionsCount: Int? = null,
    @SerialName("gmail_accounts") val gmailAccounts: List<GmailAccount>,
    @SerialName("recent_activity") val recentActivity: List<ActivityRow>,
)

@Serializable
data class LastMemoryWrite(
    @SerialName("tony_living_memory") val tonyLivingMemory: String? = null,
    @SerialName("tony_facts") val tonyFacts: String? = null,
    @SerialName("tony_episodic_memory") val tonyEpisodicMemory: String? = null,
    @SerialName("semantic_memories") val semanticMemories: String? = null,
)

@Serializable
data class GmailAccount(
    val email: String,
    @SerialName("token_expiry") val tokenExpiry: String? = null,
    val status: String,
)

@Serializable
data class ActivityRow(
    val id: Int? = null,
    @SerialName("action_type") val actionType: String? = null,
    val trigger: String? = null,
    val summary: String? = null,
    val status: String? = null,
    val result: String? = null,
    @SerialName("trace_id") val traceId: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("completed_at") val completedAt: String? = null,
    // metadata can be any JSON shape (object / array / primitive / null
    // per the backend pass-through at status.py's _recent_activity). A
    // String? here would make strict-mode deserialisation reject any
    // non-string value and fail the whole /status payload. JsonElement
    // accepts every JSON kind without coercion; rendering converts to
    // string form for display when a screen wants to surface it.
    val metadata: JsonElement? = null,
)

// ── Infrastructure ───────────────────────────────────────────────────

@Serializable
data class InfraBlock(
    @SerialName("backup_workflow") val backupWorkflow: WorkflowStatus? = null,
    @SerialName("restore_drill_workflow") val restoreDrillWorkflow: WorkflowStatus? = null,
    val elevenlabs: ServiceCheck? = null,
)

@Serializable
data class WorkflowStatus(
    val status: String,
    val workflow: String? = null,
    @SerialName("last_run") val lastRun: WorkflowRun? = null,
    val error: String? = null,
)

@Serializable
data class WorkflowRun(
    val id: Long? = null,
    val status: String? = null,
    val conclusion: String? = null,
    @SerialName("started_at") val startedAt: String? = null,
    @SerialName("completed_at") val completedAt: String? = null,
    @SerialName("age_hours") val ageHours: Double? = null,
    @SerialName("html_url") val htmlUrl: String? = null,
    val event: String? = null,
)

@Serializable
data class ServiceCheck(
    val status: String,
    @SerialName("latency_ms") val latencyMs: Int? = null,
    @SerialName("http_code") val httpCode: Int? = null,
    val error: String? = null,
)

// ── Identity ─────────────────────────────────────────────────────────

@Serializable
data class IdentityBlock(
    @SerialName("backend_version") val backendVersion: String,
    @SerialName("backend_commit_sha") val backendCommitSha: String,
    @SerialName("backend_deploy_time") val backendDeployTime: String? = null,
    @SerialName("active_feature_flags") val activeFeatureFlags: Map<String, Boolean>,
)
