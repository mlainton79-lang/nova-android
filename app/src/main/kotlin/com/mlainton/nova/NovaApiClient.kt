package com.mlainton.nova

import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

object NovaApiClient {
    private val BASE_URL = "https://web-production-be42b.up.railway.app"
    private val DEV_TOKEN = "nova-dev-token"

    data class HistoryItem(val role: String, val content: String)

    data class CouncilDebugData(
        val decidingBrain: String,
        val round1: Map<String, String>,
        val challenge: String,
        val round2Refined: Map<String, String>
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
                connectTimeout = 30000
                readTimeout = 30000
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

    fun sendCouncil(
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
            val url = URL("$BASE_URL/api/v1/council")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 90000
                readTimeout = 90000
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

            val debugData = json.optJSONObject("debug")?.let { d ->
                val round1 = mutableMapOf<String, String>()
                d.optJSONObject("round1")?.let { r -> r.keys().forEach { k -> round1[k] = r.optString(k) } }
                val round2 = mutableMapOf<String, String>()
                d.optJSONObject("round2_refined")?.let { r -> r.keys().forEach { k -> round2[k] = r.optString(k) } }
                CouncilDebugData(
                    decidingBrain = d.optString("deciding_brain", "claude"),
                    round1 = round1,
                    challenge = d.optString("challenge", ""),
                    round2Refined = round2
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
                connectTimeout = 20000
                readTimeout = 20000
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
                connectTimeout = 60000
                readTimeout = 60000
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
            val url = URL("$BASE_URL/api/v1/memory")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 15000
                readTimeout = 15000
                instanceFollowRedirects = true
                setRequestProperty("Authorization", "Bearer $DEV_TOKEN")
                setRequestProperty("Accept", "application/json")
            }
            val responseText = readAll(connection.inputStream)
            val json = JSONObject(responseText)
            val array = json.optJSONArray("memories") ?: return emptyList()
            val result = mutableListOf<MemoryEntry>()
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                result.add(MemoryEntry(
                    id = obj.optString("id"),
                    category = obj.optString("category"),
                    text = obj.optString("text"),
                    createdAt = obj.optString("created_at")
                ))
            }
            result
        } catch (_: Exception) { null }
    }

    fun addMemory(category: String, text: String): Boolean {
        return try {
            val url = URL("$BASE_URL/api/v1/memory")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15000
                readTimeout = 15000
                doOutput = true
                instanceFollowRedirects = true
                setRequestProperty("Authorization", "Bearer $DEV_TOKEN")
                setRequestProperty("Content-Type", "application/json")
            }
            val json = JSONObject().apply {
                put("category", category)
                put("text", text)
            }
            connection.outputStream.use { it.write(json.toString().toByteArray()); it.flush() }
            connection.responseCode in 200..299
        } catch (_: Exception) { false }
    }

    fun deleteMemory(id: String): Boolean {
        return try {
            val url = URL("$BASE_URL/api/v1/memory/$id")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "DELETE"
                connectTimeout = 15000
                readTimeout = 15000
                instanceFollowRedirects = true
                setRequestProperty("Authorization", "Bearer $DEV_TOKEN")
            }
            connection.responseCode in 200..299
        } catch (_: Exception) { false }
    }

    fun getMorningReport(): String? {
        return try {
            val url = URL("$BASE_URL/api/v1/think/morning")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10000
                readTimeout = 10000
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