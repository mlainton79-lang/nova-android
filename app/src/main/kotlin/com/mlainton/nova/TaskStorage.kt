package com.mlainton.nova

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

data class TaskItem(
    val id: String,
    val text: String,
    val createdAt: String
)

object TaskStorage {

    private const val PREFS_NAME = "nova_storage"
    private const val KEY_TASKS = "tasks_json"

    fun saveTask(context: Context, text: String): TaskItem {
        val item = TaskItem(
            id = UUID.randomUUID().toString(),
            text = text.trim(),
            createdAt = nowText()
        )

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val existing = readArray(prefs.getString(KEY_TASKS, "[]") ?: "[]")

        val newArray = JSONArray()
        val obj = JSONObject().apply {
            put("id", item.id)
            put("text", item.text)
            put("createdAt", item.createdAt)
        }

        newArray.put(obj)

        for (i in 0 until existing.length()) {
            newArray.put(existing.getJSONObject(i))
        }

        prefs.edit().putString(KEY_TASKS, newArray.toString()).apply()
        return item
    }

    fun getTasks(context: Context): List<TaskItem> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val array = readArray(prefs.getString(KEY_TASKS, "[]") ?: "[]")

        val items = mutableListOf<TaskItem>()

        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            items.add(
                TaskItem(
                    id = obj.optString("id"),
                    text = obj.optString("text"),
                    createdAt = obj.optString("createdAt")
                )
            )
        }

        return items
    }

    private fun readArray(raw: String): JSONArray {
        return try {
            JSONArray(raw)
        } catch (_: Exception) {
            JSONArray()
        }
    }

    private fun nowText(): String {
        return SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.UK).format(Date())
    }
}
