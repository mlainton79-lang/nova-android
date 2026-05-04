package com.mlainton.nova

import android.content.Intent
import android.os.Bundle
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

/**
 * Lists Tony's pending email-draft replies (autonomous + on-demand).
 *
 * Mirrors VintedDraftListActivity pattern: ListView + custom ArrayAdapter
 * + empty-state TextView. Network fetch in a Thread to keep main thread free.
 *
 * Tap behaviour: launches EmailDraftReviewActivity with full draft data
 * passed via Intent extras (no GET /drafts/{id} endpoint exists, so the
 * list view is the source of truth for the draft body shown next).
 *
 * Refresh on resume so dismissed/sent drafts disappear after returning.
 *
 * "Scan inbox now" button manually triggers the autonomous drafter so
 * Matthew can pull a fresh batch without waiting for the 6h cron tick.
 */
class EmailDraftListActivity : AppCompatActivity() {

    private lateinit var listView: ListView
    private lateinit var emptyText: TextView
    private lateinit var titleText: TextView
    private lateinit var scanButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_email_drafts)

        listView = findViewById(R.id.emailDraftListView)
        emptyText = findViewById(R.id.emailDraftListEmpty)
        titleText = findViewById(R.id.emailDraftListTitle)
        scanButton = findViewById(R.id.emailDraftScanButton)

        titleText.text = "Email Drafts"

        scanButton.setOnClickListener { triggerInboxScan() }
    }

    override fun onResume() {
        super.onResume()
        loadAndDisplay()
    }

    private fun loadAndDisplay() {
        emptyText.visibility = View.VISIBLE
        emptyText.text = "Loading…"
        listView.visibility = View.GONE

        Thread {
            val result = NovaApiClient.listEmailDrafts()
            runOnUiThread {
                if (!result.ok) {
                    listView.visibility = View.GONE
                    emptyText.visibility = View.VISIBLE
                    emptyText.text = "Couldn't load drafts: ${result.error ?: "unknown error"}"
                    return@runOnUiThread
                }
                if (result.drafts.isEmpty()) {
                    listView.visibility = View.GONE
                    emptyText.visibility = View.VISIBLE
                    emptyText.text = "No drafts yet. Tap 'Scan inbox now' or ask Tony to draft a reply."
                    return@runOnUiThread
                }
                listView.visibility = View.VISIBLE
                emptyText.visibility = View.GONE

                val adapter = DraftAdapter(this, result.drafts)
                listView.adapter = adapter

                listView.setOnItemClickListener { _, _, position, _ ->
                    val draft = result.drafts[position]
                    val intent = Intent(this, EmailDraftReviewActivity::class.java).apply {
                        putExtra(EmailDraftReviewActivity.EXTRA_ID, draft.id)
                        putExtra(EmailDraftReviewActivity.EXTRA_ACCOUNT, draft.account)
                        putExtra(EmailDraftReviewActivity.EXTRA_FROM, draft.from)
                        putExtra(EmailDraftReviewActivity.EXTRA_ORIGINAL_SUBJECT, draft.originalSubject)
                        putExtra(EmailDraftReviewActivity.EXTRA_DRAFT_TO, draft.draftTo)
                        putExtra(EmailDraftReviewActivity.EXTRA_DRAFT_SUBJECT, draft.draftSubject)
                        putExtra(EmailDraftReviewActivity.EXTRA_DRAFT_BODY, draft.draftBody)
                        putExtra(EmailDraftReviewActivity.EXTRA_REASONING, draft.reasoning)
                    }
                    startActivity(intent)
                }
            }
        }.start()
    }

    private fun triggerInboxScan() {
        scanButton.isEnabled = false
        scanButton.text = "Scanning…"
        Thread {
            val result = NovaApiClient.scanInboxForDrafts()
            runOnUiThread {
                scanButton.isEnabled = true
                scanButton.text = "Scan inbox now"
                if (!result.ok) {
                    Toast.makeText(this, "Scan failed: ${result.error ?: "unknown"}", Toast.LENGTH_LONG).show()
                    return@runOnUiThread
                }
                val msg = if (result.draftsCreated > 0) {
                    "${result.draftsCreated} new draft(s) from ${result.emailsChecked} emails."
                } else {
                    "Checked ${result.emailsChecked} emails. No new drafts."
                }
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                loadAndDisplay()
            }
        }.start()
    }

    private class DraftAdapter(
        private val activity: EmailDraftListActivity,
        private val drafts: List<NovaApiClient.EmailDraft>
    ) : ArrayAdapter<NovaApiClient.EmailDraft>(activity, 0, drafts) {

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView ?: LayoutInflater.from(activity)
                .inflate(R.layout.item_email_draft, parent, false)

            val draft = drafts[position]
            val senderTv = view.findViewById<TextView>(R.id.emailDraftItemSender)
            val subjectTv = view.findViewById<TextView>(R.id.emailDraftItemSubject)
            val metaTv = view.findViewById<TextView>(R.id.emailDraftItemMeta)

            senderTv.text = displaySender(draft.from)
            subjectTv.text = draft.draftSubject.ifBlank { "(no subject)" }
            metaTv.text = "${displayAccount(draft.account)} · ${displayAge(draft.createdAt)}"

            return view
        }

        private fun displaySender(from: String): String {
            if (from.isBlank()) return "(unknown sender)"
            // "Bob Foo <bob@example.com>" -> "Bob Foo"
            val angle = from.indexOf('<')
            return if (angle > 0) from.substring(0, angle).trim().trim('"') else from
        }

        private fun displayAccount(account: String): String {
            // "matthew.lainton@gmail.com" -> "matthew.lainton"
            val at = account.indexOf('@')
            return if (at > 0) account.substring(0, at) else account
        }

        private fun displayAge(createdAt: String): String {
            if (createdAt.isBlank()) return ""
            return try {
                // Backend stringifies "2026-05-04 10:40:12.123456+00:00" or similar.
                // Best-effort parse — fall back to raw string on failure.
                val isoLike = createdAt.replace(' ', 'T').take(19)
                val ts = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.UK).apply {
                    timeZone = java.util.TimeZone.getTimeZone("UTC")
                }.parse(isoLike)?.time ?: return createdAt
                DateUtils.getRelativeTimeSpanString(
                    ts,
                    System.currentTimeMillis(),
                    DateUtils.MINUTE_IN_MILLIS
                ).toString()
            } catch (e: Exception) {
                createdAt
            }
        }
    }
}
