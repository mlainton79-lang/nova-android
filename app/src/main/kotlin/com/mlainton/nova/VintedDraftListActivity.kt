package com.mlainton.nova

import android.content.Intent
import android.os.Bundle
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/**
 * Displays the user's persistent Vinted drafts loaded from VintedDraftStore.
 *
 * Mirrors TasksActivity pattern: ListView + custom ArrayAdapter + empty state TextView.
 * Loads in onResume so returning from VintedDraftReviewActivity (e.g. after discard
 * or mark-posted) automatically refreshes the list — disk is the source of truth.
 *
 * Tap behaviour: launches VintedDraftReviewActivity with EXTRA_DRAFT_ID. The review
 * activity already falls back to disk on session-store miss (Stage 2e-A.3 wiring),
 * so cold-start re-entry works.
 *
 * Stale cleanup runs at the start of onResume, removing drafts older than 7 days
 * before the load. Cleanup is also triggered from MainActivity.onCreate as a
 * background task (Phase 2e-A.8) for defense in depth.
 */
class VintedDraftListActivity : AppCompatActivity() {

    private lateinit var listView: ListView
    private lateinit var emptyText: TextView
    private lateinit var titleText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_vinted_drafts)

        listView = findViewById(R.id.draftListView)
        emptyText = findViewById(R.id.draftListEmpty)
        titleText = findViewById(R.id.draftListTitle)

        titleText.text = "Recent Vinted Drafts"
    }

    override fun onResume() {
        super.onResume()
        VintedDraftStore.cleanStale(this)
        loadAndDisplay()
    }

    private fun loadAndDisplay() {
        val entries = VintedDraftStore.loadAll(this)

        if (entries.isEmpty()) {
            listView.visibility = View.GONE
            emptyText.visibility = View.VISIBLE
            emptyText.text = "No drafts yet — capture a Vinted listing to start"
            return
        }

        listView.visibility = View.VISIBLE
        emptyText.visibility = View.GONE

        val adapter = DraftAdapter(this, entries)
        listView.adapter = adapter

        listView.setOnItemClickListener { _, _, position, _ ->
            val entry = entries[position]
            val intent = Intent(this, VintedDraftReviewActivity::class.java)
            intent.putExtra(VintedDraftReviewActivity.EXTRA_DRAFT_ID, entry.draftId)
            startActivity(intent)
        }
    }

    private class DraftAdapter(
        private val activity: VintedDraftListActivity,
        private val entries: List<VintedDraftStore.DraftEntry>
    ) : ArrayAdapter<VintedDraftStore.DraftEntry>(activity, 0, entries) {

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView ?: LayoutInflater.from(activity)
                .inflate(R.layout.item_vinted_draft, parent, false)

            val entry = entries[position]
            val titleTv = view.findViewById<TextView>(R.id.draftItemTitle)
            val ageTv = view.findViewById<TextView>(R.id.draftItemAge)

            titleTv.text = displayTitle(entry)
            ageTv.text = displayAge(entry.updatedAt)

            return view
        }

        private fun displayTitle(entry: VintedDraftStore.DraftEntry): String {
            val title = entry.payload.title
            if (title.isNotBlank()) return title

            val itemName = entry.payload.itemName
            if (itemName.isNotBlank()) return itemName

            return "Untitled draft"
        }

        private fun displayAge(updatedAt: Long): String {
            if (updatedAt <= 0) return "Saved draft"

            return try {
                DateUtils.getRelativeTimeSpanString(
                    updatedAt,
                    System.currentTimeMillis(),
                    DateUtils.MINUTE_IN_MILLIS
                ).toString()
            } catch (e: Exception) {
                "Saved draft"
            }
        }
    }
}
