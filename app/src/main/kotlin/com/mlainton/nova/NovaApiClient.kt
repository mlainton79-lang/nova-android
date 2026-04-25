package com.mlainton.nova

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStreamReader
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.util.UUID

data class VintedListingRequest(
    val imagePaths: List<String>,
    val platform: String,
    val condition: String = "good",
    val userNotes: String = "",
    val idempotencyKey: String = UUID.randomUUID().toString()
)

data class VintedListingResult(
    val ok: Boolean,
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
    val errorCode: String?,
    val errorMessage: String?
)

object NovaApiClient {
    const val BASE_URL = "https://web-production-be42b.up.railway.app"
    const val DEV_TOKEN = "S0QEE3gtVr-rK8D3L5vVLyph8OSF3py9B4qYUyU1jBssGUa4ZEdXP5w185VpIwDs"

    data class HistoryItem(val role: String, val content: String)

    data class CouncilDebugData(
        val decidingBrain: String,
        val round1: Map<String, String>,
        val challenge: String,
        val round2Refined: Map<String, String>,
        val failures: Map<String, String> = emptyMap()
    )

    data class ChatResult(
        val ok: Boolean,
        val provider: String,
        val reply: String,
        val latencyMs: Int? = null,
        val error: String? = null,
        val councilDebug: CouncilDebugData? = null
    )

    data class MemoryEntry(
        val id: String,
        val category: String,
        val text: String,
        val createdAt: String
    )

    fun sendChat(
        provider: String,
        message: String,
        history: List<HistoryItem>,
        context: String? = null,
        documentText: String? = null,
        documentBase64: String? = null,
        documentName: String? = null,
        documentMime: String? = null,
        imageBase64: String? = null
    ): ChatResult {
        return try {
            val url = URL("$BASE_URL/api/v1/chat")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 300000
                readTimeout = 300000
                doOutput = true
                instanceFollowRedirects = true
                setRequestProperty("Authorization", "Bearer $DEV_TOKEN")
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
            }

            val historyJson = JSONArray()
            history.forEach { item ->
                historyJson.put(JSONObject().apply {
                    put("role", item.role)
                    put("content", item.content)
                })
            }

            val body = JSONObject().apply {
                put("provider", provider)
                put("message", message)
                put("history", historyJson)
                if (!context.isNullOrBlank()) put("context", context)
                if (!documentText.isNullOrBlank()) put("document_text", documentText)
                if (!documentBase64.isNullOrBlank()) put("document_base64", documentBase64)
                if (!documentName.isNullOrBlank()) put("document_name", documentName)
                if (!documentMime.isNullOrBlank()) put("document_mime", documentMime)
                if (!imageBase64.isNullOrBlank()) put("image_base64", imageBase64)
            }

            connection.outputStream.use { it.write(body.toString().toByteArray()); it.flush() }

            val statusCode = connection.responseCode
            val responseText = readAll(if (statusCode in 200..299) connection.inputStream else connection.errorStream)
            val json = JSONObject(responseText.ifBlank { "{}" })

            ChatResult(
                ok = json.optBoolean("ok", statusCode in 200..299),
                provider = provider,
                reply = json.optString("reply", if (statusCode in 200..299) "" else "Tony is having trouble connecting right now. Please try again."),
                latencyMs = if (json.has("latency_ms")) json.optInt("latency_ms") else null,
                error = if (json.has("error")) json.optString("error") else null
            )
        } catch (e: Exception) {
            ChatResult(ok = false, provider = provider, reply = "Tony is having trouble connecting right now. Please try again or switch provider.", error = e.message)
        }
    }

    // Streaming chat — calls onChunk for each word/chunk as it arrives, onDone when complete
    fun sendChatStream(
        provider: String,
        message: String,
        history: List<HistoryItem>,
        location: String? = null,
        context: String? = null,
        documentText: String? = null,
        documentBase64: String? = null,
        documentName: String? = null,
        documentMime: String? = null,
        imageBase64: String? = null,
        onChunk: (String) -> Unit,
        onDone: (ok: Boolean, fullText: String, error: String?, resolvedProvider: String?) -> Unit
    ) {
        try {
            val url = URL("$BASE_URL/api/v1/chat/stream")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 300000
                readTimeout = 300000
                doOutput = true
                instanceFollowRedirects = true
                setRequestProperty("Authorization", "Bearer $DEV_TOKEN")
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "text/event-stream")
            }

            val historyJson = JSONArray()
            history.forEach { item ->
                historyJson.put(JSONObject().apply {
                    put("role", item.role)
                    put("content", item.content)
                })
            }

            val body = JSONObject().apply {
                put("provider", provider)
                put("message", message)
                put("history", historyJson)
                if (!location.isNullOrBlank()) put("location", location)
                if (!context.isNullOrBlank()) put("context", context)
                if (!documentText.isNullOrBlank()) put("document_text", documentText)
                if (!documentBase64.isNullOrBlank()) put("document_base64", documentBase64)
                if (!documentName.isNullOrBlank()) put("document_name", documentName)
                if (!documentMime.isNullOrBlank()) put("document_mime", documentMime)
                if (!imageBase64.isNullOrBlank()) put("image_base64", imageBase64)
            }

            connection.outputStream.use { it.write(body.toString().toByteArray()); it.flush() }

            val statusCode = connection.responseCode
            if (statusCode !in 200..299) {
                val err = readAll(connection.errorStream)
                onDone(false, "", "HTTP $statusCode: $err", null)
                return
            }

            val fullText = StringBuilder()
            var resolvedProvider: String? = null
            val reader = BufferedReader(InputStreamReader(connection.inputStream))
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                val l = line ?: continue
                if (!l.startsWith("data:")) continue
                val data = l.removePrefix("data:").trim()
                if (data.isBlank()) continue
                try {
                    val json = JSONObject(data)
                    val type = json.optString("type")
                    val providerHint = if (type == "provider") {
                        json.optString("name").ifBlank { json.optString("provider") }.ifBlank { json.optString("text") }
                    } else {
                        json.optString("provider")
                    }
                    if (providerHint.isNotBlank()) resolvedProvider = providerHint
                    when (type) {
                        "chunk" -> {
                            val chunk = json.optString("text", "")
                            if (chunk.isNotEmpty()) {
                                fullText.append(chunk)
                                onChunk(chunk)
                            }
                        }
                        "error" -> {
                            val errText = json.optString("text", "Unknown error")
                            onDone(false, fullText.toString(), errText, resolvedProvider)
                            return
                        }
                        "done" -> {
                            onDone(true, fullText.toString(), null, resolvedProvider)
                            return
                        }
                    }
                } catch (_: Exception) { }
            }
            onDone(true, fullText.toString(), null, resolvedProvider)
        } catch (e: Exception) {
            onDone(false, "", e.message, null)
        }
    }

    fun sendCouncil(
        message: String,
        history: List<HistoryItem>,
        location: String? = null,
        context: String? = null,
        documentText: String? = null,
        documentBase64: String? = null,
        documentName: String? = null,
        documentMime: String? = null,
        imageBase64: String? = null
    ): ChatResult {
        return try {
            val url = URL("$BASE_URL/api/v1/council")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 300000
                readTimeout = 300000
                doOutput = true
                doInput = true
                instanceFollowRedirects = true
                setRequestProperty("Authorization", "Bearer $DEV_TOKEN")
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
            }

            val historyJson = JSONArray()
            history.forEach { item ->
                historyJson.put(JSONObject().apply {
                    put("role", item.role)
                    put("content", item.content)
                })
            }

            val body = JSONObject().apply {
                put("message", message)
                put("history", historyJson)
                put("debug", true)
                if (!location.isNullOrBlank()) put("location", location)
                if (!context.isNullOrBlank()) put("context", context)
                if (!documentText.isNullOrBlank()) put("document_text", documentText)
                if (!documentBase64.isNullOrBlank()) put("document_base64", documentBase64)
                if (!documentName.isNullOrBlank()) put("document_name", documentName)
                if (!documentMime.isNullOrBlank()) put("document_mime", documentMime)
                if (!imageBase64.isNullOrBlank()) put("image_base64", imageBase64)
            }

            connection.outputStream.use { it.write(body.toString().toByteArray()); it.flush() }

            val statusCode = connection.responseCode
            val responseText = readAll(if (statusCode in 200..299) connection.inputStream else connection.errorStream)
            val json = JSONObject(responseText.ifBlank { "{}" })

            val failures = mutableMapOf<String, String>()
            json.optJSONObject("failures")?.let { f ->
                f.keys().forEach { k -> failures[k] = f.optString(k) }
            }

            val debugData = json.optJSONObject("debug")?.let { d ->
                val round1 = mutableMapOf<String, String>()
                d.optJSONObject("round1")?.let { r -> r.keys().forEach { k -> round1[k] = r.optString(k) } }
                val round2 = mutableMapOf<String, String>()
                d.optJSONObject("round2_refined")?.let { r -> r.keys().forEach { k -> round2[k] = r.optString(k) } }
                CouncilDebugData(
                    decidingBrain = d.optString("deciding_brain", "gemini"),
                    round1 = round1,
                    challenge = d.optString("challenge", ""),
                    round2Refined = round2,
                    failures = failures
                )
            }

            ChatResult(
                ok = json.optBoolean("ok", statusCode in 200..299),
                provider = "council",
                reply = json.optString("reply", "Council is unavailable right now."),
                latencyMs = if (json.has("latency_ms")) json.optInt("latency_ms") else null,
                error = if (json.has("error")) json.optString("error") else null,
                councilDebug = debugData
            )
        } catch (e: Exception) {
            ChatResult(ok = false, provider = "council", reply = "Council is having trouble connecting right now. Please try again.", error = e.message)
        }
    }

    fun summarise(messages: List<HistoryItem>): List<String> {
        return try {
            val url = URL("$BASE_URL/api/v1/summarise/")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 300000
                readTimeout = 300000
                doOutput = true
                instanceFollowRedirects = true
                setRequestProperty("Authorization", "Bearer $DEV_TOKEN")
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
            }

            val messagesJson = JSONArray()
            messages.forEach { item ->
                messagesJson.put(JSONObject().apply {
                    put("role", item.role)
                    put("content", item.content)
                })
            }

            val body = JSONObject().apply { put("messages", messagesJson) }
            connection.outputStream.use { it.write(body.toString().toByteArray()); it.flush() }

            val statusCode = connection.responseCode
            val responseText = readAll(if (statusCode in 200..299) connection.inputStream else connection.errorStream)
            val json = JSONObject(responseText.ifBlank { "{}" })
            val factsArray = json.optJSONArray("facts") ?: return emptyList()
            val facts = mutableListOf<String>()
            for (i in 0 until factsArray.length()) {
                val fact = factsArray.optString(i)
                if (fact.isNotBlank()) facts.add(fact)
            }
            facts
        } catch (_: Exception) { emptyList() }
    }

    fun syncCodebase(files: Map<String, String>): Boolean {
        return try {
            val url = URL("$BASE_URL/api/v1/codebase/sync")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 300000
                readTimeout = 300000
                doOutput = true
                instanceFollowRedirects = true
                setRequestProperty("Authorization", "Bearer $DEV_TOKEN")
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
            }

            val filesArray = JSONArray()
            files.forEach { (path, content) ->
                filesArray.put(JSONObject().apply {
                    put("path", path)
                    put("content", content)
                })
            }

            val body = JSONObject().apply { put("files", filesArray) }
            connection.outputStream.use { it.write(body.toString().toByteArray()); it.flush() }

            val statusCode = connection.responseCode
            val responseText = readAll(if (statusCode in 200..299) connection.inputStream else connection.errorStream)
            val json = JSONObject(responseText.ifBlank { "{}" })
            json.optBoolean("ok", false)
        } catch (_: Exception) { false }
    }

    fun getMemories(): List<MemoryEntry>? {
        return try {
            val url = URL("$BASE_URL/api/v1/facts?limit=100")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 300000
                readTimeout = 300000
                instanceFollowRedirects = true
                setRequestProperty("Authorization", "Bearer $DEV_TOKEN")
                setRequestProperty("Accept", "application/json")
            }
            val responseText = readAll(connection.inputStream)
            val json = JSONObject(responseText)
            val array = json.optJSONArray("facts") ?: return emptyList()
            val result = mutableListOf<MemoryEntry>()
            for (i in 0 until array.length()) {
                val fact = array.optJSONObject(i) ?: continue
                val subject = fact.optString("subject", "")
                val predicate = fact.optString("predicate", "").replace('_', ' ')
                val value = fact.optString("object", "")
                val text = listOf(subject, predicate, value).filter { it.isNotBlank() }.joinToString(" ")
                result.add(MemoryEntry(
                    id = fact.optInt("id", 0).toString(),
                    category = "facts",
                    text = text,
                    createdAt = fact.optString("last_confirmed_at", "")
                ))
            }
            result
        } catch (_: Exception) { null }
    }

    fun addMemory(category: String, text: String): Boolean {
        // /api/v1/memory doesn't exist on production. /api/v1/facts/extract
        // is the closest available endpoint: it runs LLM extraction over a
        // synthesized conversation turn and saves any facts it infers. The
        // server may save zero or multiple facts from one call, and the
        // stored form is structured (subject/predicate/object) rather than
        // the raw text. Local MemoryStore remains the source of truth for
        // the verbatim string.
        return try {
            val url = URL("$BASE_URL/api/v1/facts/extract")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 300000
                readTimeout = 300000
                doOutput = true
                instanceFollowRedirects = true
                setRequestProperty("Authorization", "Bearer $DEV_TOKEN")
                setRequestProperty("Content-Type", "application/json")
            }
            val json = JSONObject().apply {
                put("user_message", "Remember: $text")
                put("assistant_reply", "I will remember that.")
                put("save", true)
            }
            connection.outputStream.use { it.write(json.toString().toByteArray()); it.flush() }
            connection.responseCode in 200..299
        } catch (_: Exception) { false }
    }

    fun deleteMemory(id: String): Boolean {
        val factId = id.toIntOrNull() ?: return false
        return try {
            val url = URL("$BASE_URL/api/v1/facts/delete")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 300000
                readTimeout = 300000
                doOutput = true
                instanceFollowRedirects = true
                setRequestProperty("Authorization", "Bearer $DEV_TOKEN")
                setRequestProperty("Content-Type", "application/json")
            }
            val json = JSONObject().apply { put("fact_id", factId) }
            connection.outputStream.use { it.write(json.toString().toByteArray()); it.flush() }
            val statusCode = connection.responseCode
            // The backend returns HTTP 200 even when the delete fails (it wraps
            // DB errors in {"ok": false, ...}). Parse the body and trust the
            // ok field — not just the status code.
            val responseText = readAll(
                if (statusCode in 200..299) connection.inputStream else connection.errorStream
            )
            val body = JSONObject(responseText.ifBlank { "{}" })
            statusCode in 200..299 && body.optBoolean("ok", false)
        } catch (_: Exception) { false }
    }

    fun forgetFactsMatching(query: String): Int {
        val needle = query.trim().lowercase(java.util.Locale.ROOT)
        if (needle.isEmpty()) return 0
        val facts = getMemories() ?: return 0
        var removed = 0
        for (fact in facts) {
            if (fact.text.lowercase(java.util.Locale.ROOT).contains(needle)) {
                if (deleteMemory(fact.id)) removed++
            }
        }
        return removed
    }

    fun getMorningReport(): String? {
        return try {
            val url = URL("$BASE_URL/api/v1/think/morning")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 300000
                readTimeout = 300000
                instanceFollowRedirects = true
                setRequestProperty("Authorization", "Bearer $DEV_TOKEN")
                setRequestProperty("Accept", "application/json")
            }
            val responseText = readAll(connection.inputStream)
            val json = JSONObject(responseText)
            if (json.optBoolean("ok", false)) {
                val report = json.optJSONObject("report")
                if (report != null && report.has("summary")) {
                    val summary = report.optString("summary", "")
                    if (summary.isNotBlank()) summary else null
                } else null
            } else null
        } catch (_: Exception) { null }
    }

    fun formatTranscript(chatJson: String): String? {
        return try {
            val url = URL("$BASE_URL/api/v1/chat/transcript/format")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 300000
                readTimeout = 300000
                doOutput = true
                instanceFollowRedirects = true
                setRequestProperty("Authorization", "Bearer $DEV_TOKEN")
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "text/markdown")
            }
            connection.outputStream.use { it.write(chatJson.toByteArray()); it.flush() }
            val statusCode = connection.responseCode
            if (statusCode !in 200..299) {
                null
            } else {
                readAll(connection.inputStream).ifBlank { null }
            }
        } catch (_: Exception) {
            null
        }
    }

    fun createVintedListingMulti(req: VintedListingRequest): VintedListingResult {
        val imagesArray = JSONArray()
        for (path in req.imagePaths) {
            val file = File(path)
            if (!file.exists()) continue
            val b64 = downscaleJpegToBase64(file) ?: continue
            imagesArray.put(JSONObject().apply {
                put("base64", b64)
                put("mime", "image/jpeg")
            })
        }

        if (imagesArray.length() == 0) {
            return VintedListingResult(
                ok = false, itemName = "", brand = null, title = "", description = "",
                suggestedPrice = "", condition = "", category = "", confidence = "",
                needsManualVerification = true, warnings = emptyList(),
                rawJson = null, errorCode = "no_images_readable",
                errorMessage = "Couldn't read any of your photos. Try retaking."
            )
        }

        val body = JSONObject().apply {
            put("platform", req.platform)
            put("condition", req.condition)
            put("user_notes", req.userNotes)
            put("images", imagesArray)
        }.toString()

        return try {
            val url = URL("$BASE_URL/api/v1/vinted/create-listing")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 30000
                readTimeout = 300000
                doOutput = true
                instanceFollowRedirects = true
                setRequestProperty("Authorization", "Bearer $DEV_TOKEN")
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
                setRequestProperty("X-Idempotency-Key", req.idempotencyKey)
            }

            connection.outputStream.use { it.write(body.toByteArray()); it.flush() }

            val statusCode = connection.responseCode
            when {
                statusCode in 200..299 -> {
                    val responseText = readAll(connection.inputStream)
                    parseVintedListingResult(responseText)
                }
                statusCode == 401 || statusCode == 403 -> VintedListingResult(
                    ok = false, itemName = "", brand = null, title = "", description = "",
                    suggestedPrice = "", condition = "", category = "", confidence = "",
                    needsManualVerification = true, warnings = emptyList(),
                    rawJson = null, errorCode = "auth_error",
                    errorMessage = "Auth issue — check token. Photos kept."
                )
                statusCode == 422 -> VintedListingResult(
                    ok = false, itemName = "", brand = null, title = "", description = "",
                    suggestedPrice = "", condition = "", category = "", confidence = "",
                    needsManualVerification = true, warnings = emptyList(),
                    rawJson = null, errorCode = "invalid_payload",
                    errorMessage = "Photos couldn't be sent (invalid format). Photos kept."
                )
                statusCode in 500..599 -> VintedListingResult(
                    ok = false, itemName = "", brand = null, title = "", description = "",
                    suggestedPrice = "", condition = "", category = "", confidence = "",
                    needsManualVerification = true, warnings = emptyList(),
                    rawJson = null, errorCode = "backend_error",
                    errorMessage = "Tony's having trouble. Photos kept — try again."
                )
                else -> VintedListingResult(
                    ok = false, itemName = "", brand = null, title = "", description = "",
                    suggestedPrice = "", condition = "", category = "", confidence = "",
                    needsManualVerification = true, warnings = emptyList(),
                    rawJson = null, errorCode = "unexpected_status",
                    errorMessage = "Unexpected response from Tony (HTTP $statusCode). Photos kept."
                )
            }
        } catch (e: SocketTimeoutException) {
            android.util.Log.e("NovaApiClient", "createVintedListingMulti timeout", e)
            VintedListingResult(
                ok = false, itemName = "", brand = null, title = "", description = "",
                suggestedPrice = "", condition = "", category = "", confidence = "",
                needsManualVerification = true, warnings = emptyList(),
                rawJson = null, errorCode = "timeout",
                errorMessage = "Tony took too long. Photos kept — try again."
            )
        } catch (e: java.io.IOException) {
            android.util.Log.e("NovaApiClient", "createVintedListingMulti IO error", e)
            VintedListingResult(
                ok = false, itemName = "", brand = null, title = "", description = "",
                suggestedPrice = "", condition = "", category = "", confidence = "",
                needsManualVerification = true, warnings = emptyList(),
                rawJson = null, errorCode = "network_error",
                errorMessage = "Couldn't reach Tony. Photos kept — try again."
            )
        } catch (e: Exception) {
            android.util.Log.e("NovaApiClient", "createVintedListingMulti unknown error", e)
            VintedListingResult(
                ok = false, itemName = "", brand = null, title = "", description = "",
                suggestedPrice = "", condition = "", category = "", confidence = "",
                needsManualVerification = true, warnings = emptyList(),
                rawJson = null, errorCode = "unknown_error",
                errorMessage = "Something went wrong. Photos kept."
            )
        }
    }

    private fun parseVintedListingResult(rawBody: String): VintedListingResult {
        return try {
            val root = JSONObject(rawBody)
            val item = root.optJSONObject("item") ?: JSONObject()
            val listing = root.optJSONObject("listing") ?: JSONObject()
            val warningsArray = root.optJSONArray("warnings")
            val warnings = mutableListOf<String>()
            if (warningsArray != null) {
                for (i in 0 until warningsArray.length()) {
                    val w = warningsArray.optString(i, "")
                    if (w.isNotBlank()) warnings.add(w)
                }
            }

            val itemName = item.optString("item_name", "").takeIf { it.isNotBlank() }
                ?: listing.optString("title", "").takeIf { it.isNotBlank() }
                ?: "Unknown item"

            val brand = item.optString("brand", "").takeIf { it.isNotBlank() }

            val title = listing.optString("title", "").takeIf { it.isNotBlank() } ?: itemName
            val description = listing.optString("description", "")

            val rawPrice: Any? = if (listing.has("suggested_price") && !listing.isNull("suggested_price")) {
                listing.opt("suggested_price")
            } else if (item.has("suggested_uk_resale_price") && !item.isNull("suggested_uk_resale_price")) {
                item.opt("suggested_uk_resale_price")
            } else null
            val suggestedPrice = when (rawPrice) {
                is Int -> "£$rawPrice"
                is Double -> "£${rawPrice.toInt()}"
                is Long -> "£$rawPrice"
                is String -> if (rawPrice.startsWith("£")) rawPrice else "£$rawPrice"
                null -> ""
                else -> "£$rawPrice"
            }

            val condition = listing.optString("condition", "").takeIf { it.isNotBlank() }
                ?: item.optString("condition_visible", "").takeIf { it.isNotBlank() }
                ?: "good"

            val category = listing.optString("category_suggestion", "").takeIf { it.isNotBlank() }
                ?: item.optString("category", "").takeIf { it.isNotBlank() }
                ?: ""

            val confidence = item.optString("confidence", "")
            val needsManualVerification = item.optBoolean("needs_manual_verification", false)

            val parseOk = title.isNotBlank() && description.isNotBlank()

            VintedListingResult(
                ok = parseOk,
                itemName = itemName,
                brand = brand,
                title = title,
                description = description,
                suggestedPrice = suggestedPrice,
                condition = condition,
                category = category,
                confidence = confidence,
                needsManualVerification = needsManualVerification,
                warnings = warnings,
                rawJson = rawBody,
                errorCode = if (parseOk) null else "incomplete_response",
                errorMessage = if (parseOk) null else "Tony returned an incomplete draft. Photos kept — try again."
            )
        } catch (e: Exception) {
            android.util.Log.e("NovaApiClient", "parseVintedListingResult failed", e)
            VintedListingResult(
                ok = false, itemName = "", brand = null, title = "", description = "",
                suggestedPrice = "", condition = "", category = "", confidence = "",
                needsManualVerification = true, warnings = emptyList(),
                rawJson = rawBody, errorCode = "parse_error",
                errorMessage = "Couldn't read Tony's response. Photos kept — try again."
            )
        }
    }

    private fun downscaleJpegToBase64(file: File, maxEdgePx: Int = 1600, quality: Int = 85): String? {
        return try {
            val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, boundsOptions)

            if (boundsOptions.outWidth <= 0 || boundsOptions.outHeight <= 0) return null

            var sampleSize = 1
            while ((boundsOptions.outWidth / sampleSize) > maxEdgePx * 2 ||
                   (boundsOptions.outHeight / sampleSize) > maxEdgePx * 2) {
                sampleSize *= 2
            }

            val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val sampledBitmap = BitmapFactory.decodeFile(file.absolutePath, decodeOptions) ?: return null

            val w = sampledBitmap.width
            val h = sampledBitmap.height
            val scale = if (w >= h) maxEdgePx.toFloat() / w else maxEdgePx.toFloat() / h
            val finalBitmap = if (scale < 1.0f) {
                Bitmap.createScaledBitmap(sampledBitmap, (w * scale).toInt(), (h * scale).toInt(), true)
            } else {
                sampledBitmap
            }

            val baos = ByteArrayOutputStream()
            finalBitmap.compress(Bitmap.CompressFormat.JPEG, quality, baos)
            val bytes = baos.toByteArray()

            if (finalBitmap !== sampledBitmap) sampledBitmap.recycle()
            finalBitmap.recycle()

            Base64.encodeToString(bytes, Base64.NO_WRAP)
        } catch (e: Exception) {
            android.util.Log.e("NovaApiClient", "downscaleJpegToBase64 failed for ${file.absolutePath}", e)
            null
        }
    }

    private fun readAll(stream: InputStream?): String {
        if (stream == null) return ""
        return BufferedReader(InputStreamReader(stream)).use { reader ->
            val sb = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                sb.append(line).append("\n")
            }
            sb.toString()
        }
    }
}