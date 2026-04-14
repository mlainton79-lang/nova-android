package com.mlainton.nova

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class MemoryFact(
    val text: String,
    val createdAt: String
)

object MemoryStore {
    private const val PREFS = "nova_memory_store"
    private const val KEY_FACTS = "facts"

    fun addMemory(context: Context, fact: String) {
        val clean = normalizeFact(fact)
        if (clean.isEmpty()) return

        val current = getMemories(context).toMutableList()
        if (current.none { normalizeFact(it.text).equals(clean, ignoreCase = true) }) {
            current.add(
                MemoryFact(
                    text = clean,
                    createdAt = nowText()
                )
            )
            saveAll(context, current)
        }
    }

    fun getMemories(context: Context): List<MemoryFact> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_FACTS, "[]") ?: "[]"

        val array = JSONArray(raw)
        val out = mutableListOf<MemoryFact>()

        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val clean = normalizeFact(obj.optString("text", ""))
            if (clean.isNotEmpty()) {
                out.add(
                    MemoryFact(
                        text = clean,
                        createdAt = obj.optString("createdAt", "")
                    )
                )
            }
        }

        return out
    }

    fun forgetMatching(context: Context, query: String): Int {
        val clean = normalizeFact(query).lowercase(Locale.ROOT)
        if (clean.isEmpty()) return 0

        val current = getMemories(context)
        val kept = current.filterNot { normalizeFact(it.text).lowercase(Locale.ROOT).contains(clean) }
        val removed = current.size - kept.size
        saveAll(context, kept)
        return removed
    }

    fun renderSummary(context: Context): String {
        val facts = getMemories(context)
        if (facts.isEmpty()) return "I do not have any saved long-term memory yet."

        return buildString {
            append("Saved memory\n\n")
            facts.takeLast(20).forEach {
                append("• ").append(normalizeFact(it.text)).append("\n")
            }
        }.trim()
    }

    fun renderRecallSummary(context: Context): String {
        val facts = getMemories(context)
        if (facts.isEmpty()) {
            return "I do not know much about you yet."
        }

        val lines = facts.takeLast(5).map { toRecallLine(normalizeFact(it.text)) }

        return when (lines.size) {
            1 -> "From what you've told me, ${lowerFirst(lines[0])}."
            else -> buildString {
                append("From what you've told me, I know:\n\n")
                lines.forEach { append("• ").append(it).append("\n") }
            }.trim()
        }
    }

    private fun saveAll(context: Context, facts: List<MemoryFact>) {
        val array = JSONArray()
        facts.takeLast(100).forEach { fact ->
            array.put(
                JSONObject().apply {
                    put("text", normalizeFact(fact.text))
                    put("createdAt", fact.createdAt)
                }
            )
        }

        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_FACTS, array.toString())
            .apply()
    }

    private fun normalizeFact(input: String): String {
        var text = input.trim()

        text = text.replace(Regex("(?i)^remember\\s+that\\s+"), "")
        text = text.replace(Regex("(?i)^remember\\s+"), "")
        text = text.replace(Regex("\\s+"), " ")
        text = text.trim().trimEnd('.', '!', '?', ',')

        if (text.isBlank()) return ""

        return text.replaceFirstChar { ch ->
            if (ch.isLowerCase()) ch.titlecase(Locale.UK) else ch.toString()
        }
    }

    private fun toRecallLine(text: String): String {
        return when {
            text.startsWith("My name is ", ignoreCase = true) ->
                "Your name is " + text.removePrefix("My name is ").removePrefix("my name is ")
            text.startsWith("My ", ignoreCase = true) ->
                "Your " + text.drop(3)
            text.startsWith("I am ", ignoreCase = true) ->
                "You are " + text.drop(5)
            text.startsWith("I'm ", ignoreCase = true) ->
                "You're " + text.drop(4)
            text.startsWith("I work ", ignoreCase = true) ->
                "You work " + text.drop(7)
            text.startsWith("I live ", ignoreCase = true) ->
                "You live " + text.drop(7)
            text.startsWith("I like ", ignoreCase = true) ->
                "You like " + text.drop(7)
            text.startsWith("I love ", ignoreCase = true) ->
                "You love " + text.drop(7)
            else ->
                "Something important you've told me is: $text"
        }
    }

    private fun lowerFirst(text: String): String {
        if (text.isBlank()) return text
        return text.replaceFirstChar { it.lowercase(Locale.UK) }
    }

    private fun nowText(): String =
        SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.UK).format(Date())
}
