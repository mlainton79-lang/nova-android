package com.mlainton.nova

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import kotlin.concurrent.thread

class MemoryAuditActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 48, 32, 32)
            setBackgroundColor(0xFF1a1a2e.toInt())
        }

        val title = TextView(this).apply {
            text = "Memory Audit"
            textSize = 22f
            setTextColor(0xFFc084fc.toInt())
            setPadding(0, 0, 0, 24)
        }
        layout.addView(title)

        val status = TextView(this).apply {
            text = "Loading memories..."
            setTextColor(0xFFaaaaaa.toInt())
        }
        layout.addView(status)

        val scrollView = ScrollView(this)
        val listLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        scrollView.addView(listLayout)
        layout.addView(scrollView)

        setContentView(layout)
        loadMemories(status, listLayout)
    }

    private fun loadMemories(status: TextView, container: LinearLayout) {
        thread {
            val memories = NovaApiClient.getMemories()
            runOnUiThread {
                status.visibility = View.GONE
                container.removeAllViews()
                when {
                    memories == null -> status.apply {
                        text = "Error loading memories."
                        visibility = View.VISIBLE
                    }
                    memories.isEmpty() -> container.addView(TextView(this).apply {
                        text = "No memories stored yet."
                        setTextColor(0xFFaaaaaa.toInt())
                    })
                    else -> memories.forEach {
                        addMemoryCard(container, it.id, it.category, it.text)
                    }
                }
            }
        }
    }

    private fun addMemoryCard(container: LinearLayout, id: String, category: String, text: String) {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF2a2a3e.toInt())
            setPadding(24, 16, 24, 16)
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.setMargins(0, 0, 0, 16)
            layoutParams = params
        }

        val cat = TextView(this).apply {
            this.text = category
            textSize = 12f
            setTextColor(0xFFc084fc.toInt())
        }

        val body = TextView(this).apply {
            this.text = text
            textSize = 15f
            setTextColor(0xFFffffff.toInt())
            setPadding(0, 8, 0, 8)
        }

        val deleteBtn = Button(this).apply {
            this.text = "Delete"
            setTextColor(0xFFff6b6b.toInt())
            setBackgroundColor(0x00000000)
            setOnClickListener {
                deleteMemory(id, card, container)
            }
        }

        card.addView(cat)
        card.addView(body)
        card.addView(deleteBtn)
        container.addView(card)
    }

    private fun deleteMemory(id: String, card: LinearLayout, container: LinearLayout) {
        thread {
            val ok = NovaApiClient.deleteMemory(id)
            runOnUiThread {
                if (ok) {
                    container.removeView(card)
                } else {
                    Log.w("MemoryAudit", "delete failed for id=$id")
                    Toast.makeText(this, "Delete failed", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
