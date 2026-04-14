package com.mlainton.nova

import android.content.Context
import org.json.JSONObject

data class LoadedDocument(
    val name: String,
    val mimeType: String,
    val text: String?,
    val base64: String?
)

object DocumentStore {
    private const val PREFS = "nova_document_store"
    private const val KEY_DOCS = "docs_by_chat"

    fun saveDocument(context: Context, name: String, mimeType: String, text: String?, base64: String?) {
        val root = loadRoot(context)
        root.put(
            ChatHistoryStore.getActiveChatId(context),
            JSONObject().apply {
                put("name", name)
                put("mimeType", mimeType)
                put("text", text)
                put("base64", base64)
            }
        )
        saveRoot(context, root)
    }

    fun getDocument(context: Context): LoadedDocument? {
        val root = loadRoot(context)
        val obj = root.optJSONObject(ChatHistoryStore.getActiveChatId(context)) ?: return null
        return LoadedDocument(
            name = obj.optString("name", "unknown"),
            mimeType = obj.optString("mimeType", "unknown"),
            text = if (obj.has("text")) obj.optString("text", null) else null,
            base64 = if (obj.has("base64")) obj.optString("base64", null) else null
        )
    }

    fun clearDocument(context: Context) {
        val root = loadRoot(context)
        root.remove(ChatHistoryStore.getActiveChatId(context))
        saveRoot(context, root)
    }

    private fun loadRoot(context: Context): JSONObject {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_DOCS, "{}") ?: "{}"
        return JSONObject(raw)
    }

    private fun saveRoot(context: Context, root: JSONObject) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_DOCS, root.toString())
            .apply()
    }
}
