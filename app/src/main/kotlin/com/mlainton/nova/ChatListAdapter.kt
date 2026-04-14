package com.mlainton.nova

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ChatListAdapter(
    private var chats: List<ChatSessionSummary>,
    private var activeChatId: String,
    private val onTap: (ChatSessionSummary) -> Unit,
    private val onLongPress: (ChatSessionSummary) -> Unit
) : RecyclerView.Adapter<ChatListAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.chatRowTitle)
        val preview: TextView = view.findViewById(R.id.chatRowPreview)
        val timestamp: TextView = view.findViewById(R.id.chatRowTimestamp)
        val activeIndicator: View = view.findViewById(R.id.chatRowActiveIndicator)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_chat_row, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val chat = chats[position]

        holder.title.text = if (chat.pinned) "📌 ${chat.title}" else chat.title
        holder.preview.text = chat.lastMessage?.take(60) ?: "No messages yet"
        holder.timestamp.text = formatRelative(chat.updatedAt)
        holder.activeIndicator.visibility =
            if (chat.id == activeChatId) View.VISIBLE else View.INVISIBLE

        holder.itemView.setOnClickListener { onTap(chat) }
        holder.itemView.setOnLongClickListener {
            onLongPress(chat)
            true
        }
    }

    override fun getItemCount() = chats.size

    fun update(newChats: List<ChatSessionSummary>, newActiveId: String) {
        chats = newChats
        activeChatId = newActiveId
        notifyDataSetChanged()
    }

    private fun formatRelative(dateText: String): String {
        return try {
            val sdf = java.text.SimpleDateFormat("dd MMM yyyy, HH:mm", java.util.Locale.UK)
            val date = sdf.parse(dateText) ?: return ""
            val diffMs = System.currentTimeMillis() - date.time
            val diffMins = diffMs / 60000
            val diffHours = diffMs / 3600000
            val diffDays = diffMs / 86400000
            when {
                diffMins < 1 -> "now"
                diffMins < 60 -> "${diffMins}m"
                diffHours < 24 -> "${diffHours}h"
                diffDays == 1L -> "yesterday"
                else -> java.text.SimpleDateFormat("dd MMM", java.util.Locale.UK).format(date)
            }
        } catch (_: Exception) { "" }
    }
}