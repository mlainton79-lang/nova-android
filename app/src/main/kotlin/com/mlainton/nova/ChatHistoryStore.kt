package com.mlainton.nova

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

data class ChatEntry(
    val role: String,
    val text: String,
    val createdAt: String,
    val provider: String = "",
    val debugData: String = ""
)

data class ChatSessionSummary(
    val id: String,
    val title: String,
    val chatNumber: Int,
    val updatedAt: String,
    val lastMessage: String? = null,
    val pinned: Boolean = false
)

private data class StoredChat(
    var id: String,
    var title: String,
    var chatNumber: Int,
    var createdAt: String,
    var updatedAt: String,
    val messages: MutableList<ChatEntry>,
    var pinned: Boolean = false
)

object ChatHistoryStore {
    private const val PREFS = "nova_chat_history_store"
    private const val KEY_SESSIONS = "sessions"
    private const val KEY_ACTIVE_ID = "active_id"
    private const val KEY_NEXT_NUMBER = "next_chat_number"

    fun getActiveChatId(context: Context): String {
        ensureDefaultSession(context)
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getString(KEY_ACTIVE_ID, null) ?: loadSessions(context).first().id
    }

    fun getActiveChatTitle(context: Context): String {
        ensureDefaultSession(context)
        val activeId = getActiveChatId(context)
        return loadSessions(context).firstOrNull { it.id == activeId }?.title ?: "Chat"
    }

    fun getMessages(context: Context): List<ChatEntry> {
        ensureDefaultSession(context)
        val activeId = getActiveChatId(context)
        return loadSessions(context).firstOrNull { it.id == activeId }?.messages ?: emptyList()
    }

    fun appendMessage(
        context: Context,
        role: String,
        text: String,
        bumpActivity: Boolean = true,
        provider: String = "",
        debugData: String = ""
    ) {
        val clean = text.trim()
        if (clean.isEmpty()) return

        ensureDefaultSession(context)
        val chats = loadSessions(context)
        val activeId = getActiveChatId(context)

        val idx = chats.indexOfFirst { it.id == activeId }
        if (idx < 0) return

        val chat = chats[idx]
        chat.messages.add(ChatEntry(
            role = role,
            text = clean,
            createdAt = nowText(),
            provider = provider,
            debugData = debugData
        ))

        if (chat.title.startsWith("Chat ") && role == "user" && shouldUseForTitle(clean)) {
            chat.title = autoTitle(clean)
        }

        if (bumpActivity) {
            chat.updatedAt = nowText()
            chats.removeAt(idx)
            val insertAt = chats.indexOfFirst { !it.pinned }.takeIf { it >= 0 } ?: 0
            chats.add(insertAt, chat)
        }

        saveSessions(context, chats, chat.id)
    }

    fun createNewChat(context: Context, title: String? = null): ChatSessionSummary {
        val chats = loadSessions(context)
        val nextNumber = nextChatNumber(context)
        val chat = StoredChat(
            id = UUID.randomUUID().toString(),
            title = title?.trim()?.takeIf { it.isNotEmpty() } ?: "Chat $nextNumber",
            chatNumber = nextNumber,
            createdAt = nowText(),
            updatedAt = nowText(),
            messages = mutableListOf()
        )
        val insertAt = chats.indexOfFirst { !it.pinned }.takeIf { it >= 0 } ?: 0
        chats.add(insertAt, chat)
        saveSessions(context, chats, chat.id)
        return ChatSessionSummary(chat.id, chat.title, chat.chatNumber, chat.updatedAt)
    }

    fun listChats(context: Context): List<ChatSessionSummary> {
        ensureDefaultSession(context)
        return loadSessions(context).map {
            ChatSessionSummary(
                id = it.id,
                title = it.title,
                chatNumber = it.chatNumber,
                updatedAt = it.updatedAt,
                lastMessage = it.messages.lastOrNull()?.text,
                pinned = it.pinned
            )
        }
    }

    /**
     * Export a single chat as a JSONObject in the same shape that saveSessions
     * writes to SharedPreferences. Used by the transcript share/copy flow:
     * MainActivity posts this JSON to /api/v1/chat/transcript/format and
     * receives rendered Markdown back. Returns null if no chat matches chatId.
     */
    fun exportChatAsJson(context: Context, chatId: String): JSONObject? {
        val chat = loadSessions(context).firstOrNull { it.id == chatId } ?: return null
        val messagesArray = JSONArray()
        chat.messages.takeLast(300).forEach { msg ->
            messagesArray.put(JSONObject().apply {
                put("role", msg.role)
                put("text", msg.text)
                put("createdAt", msg.createdAt)
                put("provider", msg.provider)
                put("debugData", msg.debugData)
            })
        }
        return JSONObject().apply {
            put("id", chat.id)
            put("title", chat.title)
            put("chatNumber", chat.chatNumber)
            put("createdAt", chat.createdAt)
            put("updatedAt", chat.updatedAt)
            put("messages", messagesArray)
            put("pinned", chat.pinned)
        }
    }

    fun openChatByNumber(context: Context, number: Int): ChatSessionSummary? {
        ensureDefaultSession(context)
        val chats = loadSessions(context)
        val chat = chats.firstOrNull { it.chatNumber == number } ?: return null
        saveActiveId(context, chat.id)
        return ChatSessionSummary(chat.id, chat.title, chat.chatNumber, chat.updatedAt)
    }

    fun openChatByTitle(context: Context, query: String): ChatSessionSummary? {
        ensureDefaultSession(context)
        val lower = query.trim().lowercase(Locale.ROOT)
        val chats = loadSessions(context)
        val chat = chats.firstOrNull { it.title.lowercase(Locale.ROOT) == lower }
            ?: chats.firstOrNull { it.title.lowercase(Locale.ROOT).contains(lower) }
            ?: return null
        saveActiveId(context, chat.id)
        return ChatSessionSummary(chat.id, chat.title, chat.chatNumber, chat.updatedAt)
    }

    fun openChatById(context: Context, id: String): ChatSessionSummary? {
        ensureDefaultSession(context)
        val chats = loadSessions(context)
        val chat = chats.firstOrNull { it.id == id } ?: return null
        saveActiveId(context, chat.id)
        return ChatSessionSummary(chat.id, chat.title, chat.chatNumber, chat.updatedAt)
    }

    fun renameChat(context: Context, id: String, newTitle: String): Boolean {
        val clean = newTitle.trim()
        if (clean.isEmpty()) return false
        val chats = loadSessions(context)
        val idx = chats.indexOfFirst { it.id == id }
        if (idx < 0) return false
        chats[idx].title = clean
        saveSessions(context, chats, getActiveChatId(context))
        return true
    }

    fun deleteChat(context: Context, id: String) {
        val chats = loadSessions(context)
        val activeId = getActiveChatId(context)
        chats.removeAll { it.id == id }
        if (chats.isEmpty()) {
            val nextNumber = nextChatNumber(context)
            val fresh = StoredChat(
                id = UUID.randomUUID().toString(),
                title = "Chat $nextNumber",
                chatNumber = nextNumber,
                createdAt = nowText(),
                updatedAt = nowText(),
                messages = mutableListOf()
            )
            chats.add(fresh)
            saveSessions(context, chats, fresh.id)
        } else {
            val newActiveId = if (activeId == id) chats.first().id else activeId
            saveSessions(context, chats, newActiveId)
        }
    }

    fun pinChat(context: Context, id: String) {
        val chats = loadSessions(context)
        val idx = chats.indexOfFirst { it.id == id }
        if (idx < 0) return
        val chat = chats[idx]
        chat.pinned = !chat.pinned
        chats.removeAt(idx)
        if (chat.pinned) {
            chats.add(0, chat)
        } else {
            val insertAt = chats.indexOfFirst { !it.pinned }.takeIf { it >= 0 } ?: 0
            chats.add(insertAt, chat)
        }
        saveSessions(context, chats, getActiveChatId(context))
    }

    fun renameActiveChat(context: Context, newTitle: String): Boolean {
        val activeId = getActiveChatId(context)
        return renameChat(context, activeId, newTitle)
    }

    fun clearCurrentChat(context: Context) {
        ensureDefaultSession(context)
        val chats = loadSessions(context)
        val activeId = getActiveChatId(context)
        val idx = chats.indexOfFirst { it.id == activeId }
        if (idx < 0) return
        chats[idx].messages.clear()
        chats[idx].updatedAt = nowText()
        saveSessions(context, chats, activeId)
    }

    fun clearMessages(context: Context) = clearCurrentChat(context)
    fun clearHistory(context: Context) = clearCurrentChat(context)
    fun clearAllMessages(context: Context) = clearCurrentChat(context)

    fun renderChatList(context: Context): String {
        val activeId = getActiveChatId(context)
        val sessions = loadSessions(context)
        return buildString {
            append("Saved chats\n\n")
            sessions.forEach { chat ->
                append(chat.chatNumber)
                append(". ")
                append(chat.title)
                if (chat.pinned) append(" 📌")
                if (chat.id == activeId) append(" (current)")
                append(" — ")
                append(shortTime(chat.updatedAt))
                append("\n")
            }
            append("\nTo open: say \"open chat 2\" or \"open chat Notes\"")
        }.trim()
    }

    private fun ensureDefaultSession(context: Context) {
        val chats = loadSessions(context)
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val activeId = prefs.getString(KEY_ACTIVE_ID, null)

        if (chats.isEmpty()) {
            val first = StoredChat(
                id = UUID.randomUUID().toString(),
                title = "Chat 1",
                chatNumber = 1,
                createdAt = nowText(),
                updatedAt = nowText(),
                messages = mutableListOf()
            )
            prefs.edit().putInt(KEY_NEXT_NUMBER, 2).apply()
            saveSessions(context, mutableListOf(first), first.id)
            return
        }

        if (activeId == null || chats.none { it.id == activeId }) {
            saveActiveId(context, chats.first().id)
        }

        val used = mutableSetOf<Int>()
        var next = prefs.getInt(KEY_NEXT_NUMBER, 2)
        var changed = false
        chats.forEachIndexed { i, chat ->
            val num = chat.chatNumber
            if (num <= 0 || used.contains(num)) {
                val assigned = if (next > 0) next else (i + 1)
                chat.chatNumber = assigned
                next = assigned + 1
                changed = true
            }
            used.add(chat.chatNumber)
            if (chat.createdAt.isBlank()) {
                chat.createdAt = chat.updatedAt
                changed = true
            }
        }
        if (changed) {
            prefs.edit().putInt(KEY_NEXT_NUMBER, maxOf(next, used.maxOrNull()?.plus(1) ?: 2)).apply()
            saveSessions(context, chats, getActiveChatId(context))
        } else if (!prefs.contains(KEY_NEXT_NUMBER)) {
            prefs.edit().putInt(KEY_NEXT_NUMBER, (used.maxOrNull() ?: 1) + 1).apply()
        }
    }

    private fun saveActiveId(context: Context, activeId: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_ACTIVE_ID, activeId).apply()
    }

    private fun nextChatNumber(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val next = prefs.getInt(KEY_NEXT_NUMBER, 2)
        prefs.edit().putInt(KEY_NEXT_NUMBER, next + 1).apply()
        return next
    }

    private fun loadSessions(context: Context): MutableList<StoredChat> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_SESSIONS, "[]") ?: "[]"
        val array = JSONArray(raw)
        val out = mutableListOf<StoredChat>()
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val messagesArray = obj.optJSONArray("messages") ?: JSONArray()
            val messages = mutableListOf<ChatEntry>()
            for (j in 0 until messagesArray.length()) {
                val m = messagesArray.optJSONObject(j) ?: continue
                messages.add(ChatEntry(
                    role = m.optString("role", "assistant"),
                    text = m.optString("text", ""),
                    createdAt = m.optString("createdAt", ""),
                    provider = m.optString("provider", ""),
                    debugData = m.optString("debugData", "")
                ))
            }
            out.add(StoredChat(
                id = obj.optString("id", UUID.randomUUID().toString()),
                title = obj.optString("title", "Chat"),
                chatNumber = obj.optInt("chatNumber", i + 1),
                createdAt = obj.optString("createdAt", obj.optString("updatedAt", "")),
                updatedAt = obj.optString("updatedAt", nowText()),
                messages = messages,
                pinned = obj.optBoolean("pinned", false)
            ))
        }
        return out
    }

    private fun saveSessions(context: Context, chats: MutableList<StoredChat>, activeId: String) {
        val array = JSONArray()
        chats.takeLast(100).forEach { chat ->
            val messagesArray = JSONArray()
            chat.messages.takeLast(300).forEach { msg ->
                messagesArray.put(JSONObject().apply {
                    put("role", msg.role)
                    put("text", msg.text)
                    put("createdAt", msg.createdAt)
                    put("provider", msg.provider)
                    put("debugData", msg.debugData)
                })
            }
            array.put(JSONObject().apply {
                put("id", chat.id)
                put("title", chat.title)
                put("chatNumber", chat.chatNumber)
                put("createdAt", chat.createdAt)
                put("updatedAt", chat.updatedAt)
                put("messages", messagesArray)
                put("pinned", chat.pinned)
            })
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SESSIONS, array.toString())
            .putString(KEY_ACTIVE_ID, activeId)
            .apply()
    }

    private fun shouldUseForTitle(text: String): Boolean {
        val lower = text.trim().lowercase(Locale.ROOT)
        return lower.isNotEmpty() &&
            lower != "new chat" &&
            lower != "start new chat" &&
            lower != "show chats" &&
            lower != "list chats" &&
            lower != "chat list" &&
            !lower.startsWith("open chat ") &&
            !lower.startsWith("switch to ") &&
            lower != "resume last chat" &&
            !lower.startsWith("remember ") &&
            !lower.startsWith("forget ") &&
            lower != "show memory" &&
            lower != "what do you remember" &&
            lower != "what do you remember about me" &&
            lower != "what do you know about me"
    }

    private fun autoTitle(text: String): String {
        val clean = text.replace(Regex("\\s+"), " ").trim()
        val short = if (clean.length <= 32) clean else clean.take(32).trimEnd() + "…"
        return short.replaceFirstChar {
            if (it.isLowerCase()) it.titlecase(Locale.UK) else it.toString()
        }
    }

    private fun shortTime(dateText: String): String {
        return try {
            val sdf = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.UK)
            val date = sdf.parse(dateText) ?: return dateText
            val now = Date()
            val diffMs = now.time - date.time
            val diffMins = diffMs / 60000
            val diffHours = diffMs / 3600000
            val diffDays = diffMs / 86400000
            when {
                diffMins < 1 -> "just now"
                diffMins < 60 -> "${diffMins}m ago"
                diffHours < 24 -> "${diffHours}h ago"
                diffDays == 1L -> "yesterday"
                else -> SimpleDateFormat("dd MMM", Locale.UK).format(date)
            }
        } catch (_: Exception) { dateText }
    }

    private fun nowText(): String =
        SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.UK).format(Date())
}