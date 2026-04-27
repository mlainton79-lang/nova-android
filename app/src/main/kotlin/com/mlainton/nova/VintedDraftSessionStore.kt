package com.mlainton.nova

import java.util.UUID

/**
 * In-memory store for active Vinted draft payloads.
 *
 * Lifetime: process. Cleared on app/process death. Not persistent.
 *
 * Usage:
 * - MainActivity caches a draft after Stage 2c result via [put]
 * - VintedDraftReviewActivity retrieves payload via [get]
 * - Discard / Mark Posted call [remove]
 * - Retry replaces payload with a fresh one via [put] (same draftId)
 *
 * Stage 2d does not persist drafts across restarts. Pre-2d Vinted chat messages
 * have no payload here. Re-launching the review screen for a missing draftId
 * shows a "draft no longer available" toast and finishes.
 */
object VintedDraftSessionStore {

    data class Payload(
        val draftId: String,
        val itemName: String,
        val brand: String?,
        val title: String,
        val description: String,
        val suggestedPrice: String,
        val condition: String,
        val category: String,
        val confidence: String,
        val needsManualVerification: Boolean,
        val warnings: List<String>,
        val rawJson: String?,
        val platform: String,
        val photoPaths: List<String>
    )

    private val drafts = mutableMapOf<String, Payload>()

    fun newDraftId(): String = UUID.randomUUID().toString()

    @Synchronized
    fun put(payload: Payload) {
        drafts[payload.draftId] = payload
    }

    @Synchronized
    fun get(draftId: String): Payload? = drafts[draftId]

    @Synchronized
    fun remove(draftId: String) {
        drafts.remove(draftId)
    }

    @Synchronized
    fun has(draftId: String): Boolean = drafts.containsKey(draftId)
}
