package com.mlainton.nova

import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

object NovaApiClient {
    const val BASE_URL = "https://web-production-be42b.up.railway.app"
    const val DEV_TOKEN = "JsR_s1r8XfaI-BwupHX8NzYhCVO11rflcQvTlNMfrZF4LcCVTYsqX82XZ3cE_Gll"

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