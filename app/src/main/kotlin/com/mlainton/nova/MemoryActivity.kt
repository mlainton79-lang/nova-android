package com.mlainton.nova

import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class MemoryActivity : AppCompatActivity() {

    private lateinit var memoryListLayout: LinearLayout
    private lateinit var memoryInput: EditText
    private lateinit var categorySpinner: Spinner
    private lateinit var saveButton: Button
    private lateinit var statusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val scroll = ScrollView(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
        }
        scroll.addView(root)
        setContentView(scroll)

        val title = TextView(this).apply {
            text = "Tony's Memory"
            textSize = 24f
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(0, 0, 0, 16)
        }
        root.addView(title)

        statusText = TextView(this).apply {
            textSize = 13f
            setTextColor(0xFFD6D0E2.toInt())
            setPadding(0, 0, 0, 12)
        }
        root.addView(statusText)

        val categories = arrayOf("projects", "preferences", "people", "instructions", "facts")
        categorySpinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@MemoryActivity, android.R.layout.simple_spinner_dropdown_item, categories)
        }
        root.addView(categorySpinner)

        memoryInput = EditText(this).apply {
            hint = "Add a memory..."
            setTextColor(0xFFFFFFFF.toInt())
            setHintTextColor(0xFF8E86A1.toInt())
            textSize = 16f
            setPadding(0, 12, 0, 12)
        }
        root.addView(memoryInput)

        saveButton = Button(this).apply {
            text = "Save memory"
            setOnClickListener { saveMemory() }
        }
        root.addView(saveButton)

        val divider = android.view.View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1
            ).apply { topMargin = 20; bottomMargin = 20 }
            setBackgroundColor(0x33FFFFFF)
        }
        root.addView(divider)

        val savedTitle = TextView(this).apply {
            text = "Saved memories"
            textSize = 16f
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(0, 0, 0, 12)
        }
        root.addView(savedTitle)

        memoryListLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        root.addView(memoryListLayout)

        loadMemories()
    }

    private fun loadMemories() {
        statusText.text = "Loading..."
        memoryListLayout.removeAllViews()

        Thread {
            val result = NovaApiClient.getMemories()
            runOnUiThread {
                if (result == null) {
                    statusText.text = "Could not load memories."
                    return@runOnUiThread
                }
                statusText.text = "${result.size} saved"
                if (result.isEmpty()) {
                    val empty = TextView(this).apply {
                        text = "No memories saved yet."
                        setTextColor(0xFF8E86A1.toInt())
                        textSize = 14f
                    }
                    memoryListLayout.addView(empty)
                    return@runOnUiThread
                }
                result.forEach { memory ->
                    addMemoryRow(memory)
                }
            }
        }.start()
    }

    private fun addMemoryRow(memory: NovaApiClient.MemoryEntry) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 8, 0, 8)
        }

        val text = TextView(this).apply {
            this.text = "[${memory.category}] ${memory.text}"
            textSize = 14f
            setTextColor(0xFFFFFFFF.toInt())
            layoutParams = LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
            )
        }

        val deleteBtn = Button(this).apply {
            this.text = "✕"
            textSize = 12f
            setPadding(12, 4, 12, 4)
            setOnClickListener {
                confirmDelete(memory)
            }
        }

        row.addView(text)
        row.addView(deleteBtn)
        memoryListLayout.addView(row)
    }

    private fun saveMemory() {
        val text = memoryInput.text.toString().trim()
        if (text.isEmpty()) {
            Toast.makeText(this, "Type a memory first.", Toast.LENGTH_SHORT).show()
            return
        }
        val category = categorySpinner.selectedItem.toString()
        statusText.text = "Saving..."

        Thread {
            val ok = NovaApiClient.addMemory(category, text)
            runOnUiThread {
                if (ok) {
                    memoryInput.setText("")
                    statusText.text = "Saved."
                    loadMemories()
                } else {
                    statusText.text = "Save failed. Check connection."
                }
            }
        }.start()
    }

    private fun confirmDelete(memory: NovaApiClient.MemoryEntry) {
        AlertDialog.Builder(this)
            .setTitle("Delete memory")
            .setMessage("Delete: \"${memory.text}\"?")
            .setPositiveButton("Delete") { _, _ ->
                Thread {
                    val ok = NovaApiClient.deleteMemory(memory.id)
                    runOnUiThread {
                        if (ok) loadMemories()
                        else Toast.makeText(this, "Delete failed.", Toast.LENGTH_SHORT).show()
                    }
                }.start()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}