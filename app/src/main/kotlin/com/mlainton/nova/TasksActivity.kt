package com.mlainton.nova

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class TasksActivity : AppCompatActivity() {

    private lateinit var summaryText: TextView
    private lateinit var tasksListView: ListView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tasks)

        summaryText = findViewById(R.id.summaryText)
        tasksListView = findViewById(R.id.tasksListView)
    }

    override fun onResume() {
        super.onResume()
        loadTasks()
    }

    private fun loadTasks() {
        val tasks = TaskStorage.getTasks(this)

        if (tasks.isEmpty()) {
            summaryText.text = "No tasks saved yet."
            tasksListView.adapter = null
            return
        }

        summaryText.text = "Saved tasks: ${tasks.size}"

        val displayItems = tasks.map {
            "${it.createdAt}\n${it.text}"
        }

        tasksListView.adapter = ArrayAdapter(
            this,
            R.layout.item_task,
            R.id.taskItemText,
            displayItems
        )
    }
}
