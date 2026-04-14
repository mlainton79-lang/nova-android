package com.mlainton.nova

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object HomeMemoryStore {

    private const val PREFS_NAME = "nova_home_memory"
    private const val KEY_LAST_FILE = "last_file"
    private const val KEY_LAST_CAMERA = "last_camera"
    private const val KEY_LAST_MESSAGE = "last_message"
    private const val KEY_LAST_TASK = "last_task"

    fun saveLastFile(context: Context, fileName: String, mimeType: String) {
        prefs(context).edit()
            .putString(KEY_LAST_FILE, "$fileName ($mimeType) • ${nowText()}")
            .apply()
    }

    fun saveLastCamera(context: Context) {
        prefs(context).edit()
            .putString(KEY_LAST_CAMERA, "Camera preview captured • ${nowText()}")
            .apply()
    }

    fun saveLastMessage(context: Context, message: String) {
        prefs(context).edit()
            .putString(KEY_LAST_MESSAGE, "${shorten(message)} • ${nowText()}")
            .apply()
    }

    fun saveLastTask(context: Context, task: String) {
        prefs(context).edit()
            .putString(KEY_LAST_TASK, "${shorten(task)} • ${nowText()}")
            .apply()
    }

    fun buildSummary(context: Context, tasks: List<TaskItem>): String {
        val prefs = prefs(context)

        val lastFile = prefs.getString(KEY_LAST_FILE, null)
        val lastCamera = prefs.getString(KEY_LAST_CAMERA, null)
        val lastMessage = prefs.getString(KEY_LAST_MESSAGE, null)
        val lastTask = prefs.getString(KEY_LAST_TASK, null)

        val sb = StringBuilder()
        sb.append("Recent memory\n\n")

        if (tasks.isEmpty()) {
            sb.append("Latest tasks:\n- none yet\n")
        } else {
            sb.append("Latest tasks:\n")
            for ((index, task) in tasks.take(3).withIndex()) {
                sb.append("- ").append(shorten(task.text))
                if (index == 0) {
                    sb.append(" • ").append(task.createdAt)
                }
                sb.append("\n")
            }
        }

        sb.append("\nLast task saved:\n")
        sb.append(lastTask ?: "none yet")

        sb.append("\n\nLast message:\n")
        sb.append(lastMessage ?: "none yet")

        sb.append("\n\nLast file:\n")
        sb.append(lastFile ?: "none yet")

        sb.append("\n\nLast camera action:\n")
        sb.append(lastCamera ?: "none yet")

        return sb.toString().trim()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun nowText(): String =
        SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.UK).format(Date())

    private fun shorten(text: String, max: Int = 60): String {
        val clean = text.trim().replace("\n", " ")
        return if (clean.length <= max) clean else clean.take(max - 3) + "..."
    }
}
