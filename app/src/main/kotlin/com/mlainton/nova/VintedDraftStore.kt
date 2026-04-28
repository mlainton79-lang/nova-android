package com.mlainton.nova

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Persistent disk-backed store for Vinted draft payloads.
 *
 * Storage layout:
 *   filesDir/vinted_drafts/<draftId>.json
 *
 * JSON wrapper format:
 *   {
 *     "draftId": "uuid",
 *     "createdAt": <epoch_ms>,
 *     "updatedAt": <epoch_ms>,
 *     "payload": { <full VintedDraftSessionStore.Payload> }
 *   }
 *
 * Pairs with VintedDraftSessionStore (in-memory cache) using a write-through pattern:
 * - MainActivity Vinted result -> SessionStore.put + VintedDraftStore.save
 * - Review screen lookup -> SessionStore.get, fallback to VintedDraftStore.load
 * - Discard / Mark Posted -> SessionStore.remove + VintedDraftStore.delete
 * - Retry -> SessionStore.put + VintedDraftStore.save
 *
 * Stale cleanup runs from MainActivity.onCreate (background thread) and
 * VintedDraftListActivity.onResume. Entries older than [STALE_AGE_MS] are removed.
 */
object VintedDraftStore {

    private const val TAG = "VintedDraftStore"
    private const val DRAFTS_DIR_NAME = "vinted_drafts"
    private const val STALE_AGE_MS = 7L * 24 * 60 * 60 * 1000

    private fun draftsDir(context: Context): File {
        val dir = File(context.filesDir, DRAFTS_DIR_NAME)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun draftFile(context: Context, draftId: String): File {
        return File(draftsDir(context), "$draftId.json")
    }

    /**
     * Save (or overwrite) a draft payload to disk.
     * Updates updatedAt; preserves createdAt if file already exists.
     */
    @Synchronized
    fun save(context: Context, payload: VintedDraftSessionStore.Payload): Boolean {
        return try {
            val file = draftFile(context, payload.draftId)
            val now = System.currentTimeMillis()
            val createdAt = if (file.exists()) {
                try {
                    val existing = JSONObject(file.readText())
                    existing.optLong("createdAt", now)
                } catch (e: Exception) {
                    now
                }
            } else {
                now
            }

            val wrapper = JSONObject().apply {
                put("draftId", payload.draftId)
                put("createdAt", createdAt)
                put("updatedAt", now)
                put("payload", payloadToJson(payload))
            }

            file.writeText(wrapper.toString())
            true
        } catch (e: Exception) {
            Log.e(TAG, "save failed for ${payload.draftId}: ${e.message}")
            false
        }
    }

    /**
     * Load a single draft from disk. Returns null if missing or unreadable.
     */
    @Synchronized
    fun load(context: Context, draftId: String): VintedDraftSessionStore.Payload? {
        return try {
            val file = draftFile(context, draftId)
            if (!file.exists()) return null
            val wrapper = JSONObject(file.readText())
            payloadFromJson(wrapper.getJSONObject("payload"))
        } catch (e: Exception) {
            Log.w(TAG, "load failed for $draftId: ${e.message}")
            null
        }
    }

    /**
     * Load all drafts from disk, sorted by updatedAt descending (most recent first).
     * Skips corrupt or unreadable files.
     */
    @Synchronized
    fun loadAll(context: Context): List<DraftEntry> {
        val dir = draftsDir(context)
        if (!dir.exists()) return emptyList()

        val files = dir.listFiles { f -> f.isFile && f.name.endsWith(".json") } ?: return emptyList()
        val entries = mutableListOf<DraftEntry>()

        for (file in files) {
            try {
                val wrapper = JSONObject(file.readText())
                val draftId = wrapper.getString("draftId")
                val createdAt = wrapper.optLong("createdAt", file.lastModified())
                val updatedAt = wrapper.optLong("updatedAt", file.lastModified())
                val payload = payloadFromJson(wrapper.getJSONObject("payload"))
                entries.add(DraftEntry(draftId, createdAt, updatedAt, payload))
            } catch (e: Exception) {
                Log.w(TAG, "loadAll skipping corrupt file ${file.name}: ${e.message}")
            }
        }

        return entries.sortedByDescending { it.updatedAt }
    }

    /**
     * Delete a single draft's JSON file.
     *
     * Photos are deleted separately by VintedDraftReviewActivity when the user
     * discards or marks a draft as posted. Keep this JSON-only so future call sites
     * can remove draft records without unexpectedly deleting photos.
     */
    @Synchronized
    fun delete(context: Context, draftId: String): Boolean {
        return try {
            val file = draftFile(context, draftId)
            if (file.exists()) file.delete() else true
        } catch (e: Exception) {
            Log.w(TAG, "delete failed for $draftId: ${e.message}")
            false
        }
    }

    /**
     * Remove drafts older than [STALE_AGE_MS] (default 7 days).
     *
     * For readable stale drafts, linked cache photos are deleted safely before
     * the JSON file is removed. Photo deletion is restricted to cacheDir/vinted/
     * so this cleanup cannot delete arbitrary files.
     *
     * Corrupt JSON files older than STALE_AGE_MS are deleted as JSON-only because
     * linked photo paths cannot be read safely. Corrupt newer files are skipped.
     */
    @Synchronized
    fun cleanStale(context: Context): Int {
        val dir = draftsDir(context)
        if (!dir.exists()) return 0

        val files = dir.listFiles { f -> f.isFile && f.name.endsWith(".json") } ?: return 0
        val cutoff = System.currentTimeMillis() - STALE_AGE_MS
        var deleted = 0

        for (file in files) {
            try {
                val wrapper = JSONObject(file.readText())
                val updatedAt = wrapper.optLong("updatedAt", file.lastModified())

                if (updatedAt < cutoff) {
                    val payload = payloadFromJson(wrapper.getJSONObject("payload"))
                    deleteLinkedPhotosSafely(context, payload)

                    if (file.delete()) deleted++
                }
            } catch (e: Exception) {
                if (file.lastModified() < cutoff) {
                    if (file.delete()) deleted++
                }
            }
        }

        return deleted
    }

    private fun deleteLinkedPhotosSafely(context: Context, payload: VintedDraftSessionStore.Payload) {
        val safeRoot = File(context.cacheDir, "vinted").absolutePath + File.separator

        for (path in payload.photoPaths) {
            try {
                val file = File(path)
                if (file.absolutePath.startsWith(safeRoot) && file.exists()) {
                    file.delete()
                }
            } catch (e: Exception) {
                Log.w(TAG, "deleteLinkedPhotosSafely failed for a photo path: ${e.message}")
            }
        }
    }

    private fun payloadToJson(p: VintedDraftSessionStore.Payload): JSONObject {
        return JSONObject().apply {
            put("draftId", p.draftId)
            put("itemName", p.itemName)
            put("brand", p.brand ?: JSONObject.NULL)
            put("title", p.title)
            put("description", p.description)
            put("suggestedPrice", p.suggestedPrice)
            put("condition", p.condition)
            put("category", p.category)
            put("confidence", p.confidence)
            put("needsManualVerification", p.needsManualVerification)
            put("warnings", JSONArray(p.warnings))
            put("rawJson", p.rawJson ?: JSONObject.NULL)
            put("platform", p.platform)
            put("photoPaths", JSONArray(p.photoPaths))
        }
    }

    private fun payloadFromJson(json: JSONObject): VintedDraftSessionStore.Payload {
        val warnings = mutableListOf<String>()
        val warningsArray = json.optJSONArray("warnings")
        if (warningsArray != null) {
            for (i in 0 until warningsArray.length()) {
                warnings.add(warningsArray.getString(i))
            }
        }

        val photoPaths = mutableListOf<String>()
        val photoPathsArray = json.optJSONArray("photoPaths")
        if (photoPathsArray != null) {
            for (i in 0 until photoPathsArray.length()) {
                photoPaths.add(photoPathsArray.getString(i))
            }
        }

        return VintedDraftSessionStore.Payload(
            draftId = json.getString("draftId"),
            itemName = json.getString("itemName"),
            brand = if (json.isNull("brand")) null else json.optString("brand", ""),
            title = json.getString("title"),
            description = json.getString("description"),
            suggestedPrice = json.getString("suggestedPrice"),
            condition = json.getString("condition"),
            category = json.getString("category"),
            confidence = json.getString("confidence"),
            needsManualVerification = json.getBoolean("needsManualVerification"),
            warnings = warnings,
            rawJson = if (json.isNull("rawJson")) null else json.optString("rawJson", ""),
            platform = json.getString("platform"),
            photoPaths = photoPaths
        )
    }

    /**
     * Wrapper holding draft metadata + payload. Returned by [loadAll] for use by
     * VintedDraftListActivity which needs createdAt/updatedAt for sorting and display.
     */
    data class DraftEntry(
        val draftId: String,
        val createdAt: Long,
        val updatedAt: Long,
        val payload: VintedDraftSessionStore.Payload
    )
}
