package com.mlainton.nova

import android.app.AlertDialog
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

/**
 * Review screen for one of Tony's pending email-draft replies.
 *
 * Receives full draft data via Intent extras (no GET /drafts/{id} endpoint
 * exists — the list activity is the source of truth). Shows the original
 * email context read-only, with editable subject + body pre-filled from
 * Tony's draft.
 *
 * Three actions:
 *   - Approve & Send: posts to /api/v1/drafts/{id}/send with optional
 *     final_subject / final_body overrides if Matthew edited them.
 *     Backend's trust anchors (account, recipient, original_message_id)
 *     are read from the DB row, never the request body.
 *   - Discard: confirms then posts /api/v1/drafts/{id}/dismiss.
 *   - Back: leaves the screen. Edits are local-only for v1
 *     (persistence-without-send is N1.email-draft-B.2 future work).
 *
 * Voice/sound: silent. The send tap is the approval gate.
 */
class EmailDraftReviewActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_ID = "email_draft_id"
        const val EXTRA_ACCOUNT = "email_draft_account"
        const val EXTRA_FROM = "email_draft_from"
        const val EXTRA_ORIGINAL_SUBJECT = "email_draft_original_subject"
        const val EXTRA_DRAFT_TO = "email_draft_to"
        const val EXTRA_DRAFT_SUBJECT = "email_draft_subject"
        const val EXTRA_DRAFT_BODY = "email_draft_body"
        const val EXTRA_REASONING = "email_draft_reasoning"
    }

    private var draftId: Int = 0
    private lateinit var account: String
    private lateinit var originalDraftSubject: String
    private lateinit var originalDraftBody: String

    private lateinit var accountPill: TextView
    private lateinit var replyingFromTo: TextView
    private lateinit var replyingSubject: TextView
    private lateinit var reasoningToggle: TextView
    private lateinit var reasoningText: TextView
    private lateinit var subjectEdit: EditText
    private lateinit var bodyEdit: EditText
    private lateinit var discardButton: Button
    private lateinit var approveButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_email_draft_review)

        draftId = intent.getIntExtra(EXTRA_ID, 0)
        if (draftId <= 0) {
            Toast.makeText(this, "Draft no longer available.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        account = intent.getStringExtra(EXTRA_ACCOUNT) ?: ""
        val fromAddr = intent.getStringExtra(EXTRA_FROM) ?: ""
        val originalSubject = intent.getStringExtra(EXTRA_ORIGINAL_SUBJECT) ?: ""
        val draftTo = intent.getStringExtra(EXTRA_DRAFT_TO) ?: ""
        originalDraftSubject = intent.getStringExtra(EXTRA_DRAFT_SUBJECT) ?: ""
        originalDraftBody = intent.getStringExtra(EXTRA_DRAFT_BODY) ?: ""
        val reasoning = intent.getStringExtra(EXTRA_REASONING) ?: ""

        bindViews()
        applyFields(fromAddr, originalSubject, draftTo, reasoning)
        wireButtons()
    }

    private fun bindViews() {
        accountPill = findViewById(R.id.emailDraftAccountPill)
        replyingFromTo = findViewById(R.id.emailDraftReplyingTo)
        replyingSubject = findViewById(R.id.emailDraftReplyingSubject)
        reasoningToggle = findViewById(R.id.emailDraftReasoningToggle)
        reasoningText = findViewById(R.id.emailDraftReasoningText)
        subjectEdit = findViewById(R.id.emailDraftSubjectEdit)
        bodyEdit = findViewById(R.id.emailDraftBodyEdit)
        discardButton = findViewById(R.id.emailDraftDiscardButton)
        approveButton = findViewById(R.id.emailDraftApproveButton)
    }

    private fun applyFields(
        fromAddr: String,
        originalSubject: String,
        draftTo: String,
        reasoning: String
    ) {
        accountPill.text = "Sending from: ${displayAccount(account)}"
        replyingFromTo.text = "Replying to: ${displaySender(fromAddr)}\nDraft to: $draftTo"
        replyingSubject.text = originalSubject.ifBlank { "(no original subject)" }

        if (reasoning.isBlank()) {
            reasoningToggle.visibility = View.GONE
            reasoningText.visibility = View.GONE
        } else {
            reasoningToggle.visibility = View.VISIBLE
            reasoningText.visibility = View.GONE
            reasoningText.text = reasoning
            reasoningToggle.setOnClickListener {
                if (reasoningText.visibility == View.VISIBLE) {
                    reasoningText.visibility = View.GONE
                    reasoningToggle.text = "▸ Tony's reasoning"
                } else {
                    reasoningText.visibility = View.VISIBLE
                    reasoningToggle.text = "▾ Tony's reasoning"
                }
            }
        }

        subjectEdit.setText(originalDraftSubject)
        bodyEdit.setText(originalDraftBody)
    }

    private fun wireButtons() {
        discardButton.setOnClickListener { confirmDiscard() }
        approveButton.setOnClickListener { confirmAndSend() }
    }

    private fun confirmAndSend() {
        val finalSubject = subjectEdit.text.toString()
        val finalBody = bodyEdit.text.toString()

        if (finalSubject.isBlank() || finalBody.isBlank()) {
            Toast.makeText(this, "Subject and body cannot be empty.", Toast.LENGTH_SHORT).show()
            return
        }

        // Send overrides only if Matthew edited them — let backend use stored
        // values otherwise (audit row stays accurate).
        val subjectOverride = if (finalSubject != originalDraftSubject) finalSubject else null
        val bodyOverride = if (finalBody != originalDraftBody) finalBody else null

        approveButton.isEnabled = false
        discardButton.isEnabled = false
        approveButton.text = "Sending…"

        Thread {
            val result = NovaApiClient.sendEmailDraft(draftId, subjectOverride, bodyOverride)
            runOnUiThread {
                if (result.ok) {
                    Toast.makeText(this, result.message ?: "Sent.", Toast.LENGTH_SHORT).show()
                    finish()
                } else {
                    approveButton.isEnabled = true
                    discardButton.isEnabled = true
                    approveButton.text = "Approve & Send"
                    AlertDialog.Builder(this)
                        .setTitle("Send failed")
                        .setMessage(result.error ?: "unknown error")
                        .setPositiveButton("OK", null)
                        .show()
                }
            }
        }.start()
    }

    private fun confirmDiscard() {
        AlertDialog.Builder(this)
            .setTitle("Discard draft?")
            .setMessage("Tony's draft will be dismissed. The original email stays in the inbox.")
            .setPositiveButton("Discard") { _, _ -> doDismiss() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun doDismiss() {
        approveButton.isEnabled = false
        discardButton.isEnabled = false
        discardButton.text = "Discarding…"

        Thread {
            val result = NovaApiClient.dismissEmailDraft(draftId)
            runOnUiThread {
                if (result.ok) {
                    Toast.makeText(this, result.message ?: "Discarded.", Toast.LENGTH_SHORT).show()
                    finish()
                } else {
                    approveButton.isEnabled = true
                    discardButton.isEnabled = true
                    discardButton.text = "Discard"
                    AlertDialog.Builder(this)
                        .setTitle("Dismiss failed")
                        .setMessage(result.error ?: "unknown error")
                        .setPositiveButton("OK", null)
                        .show()
                }
            }
        }.start()
    }

    private fun displayAccount(acc: String): String {
        val at = acc.indexOf('@')
        return if (at > 0) acc.substring(0, at) else acc
    }

    private fun displaySender(from: String): String {
        if (from.isBlank()) return "(unknown sender)"
        val angle = from.indexOf('<')
        return if (angle > 0) from.substring(0, angle).trim().trim('"') else from
    }
}
