package com.mlainton.nova

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import java.io.File
import java.util.UUID

class VintedDraftReviewActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_DRAFT_ID = "draft_id"
    }

    private lateinit var draftId: String
    private var photoPaths: List<String> = emptyList()
    private var platform: String = "vinted"

    private lateinit var itemNameValue: TextView
    private lateinit var brandEdit: EditText
    private lateinit var titleEdit: EditText
    private lateinit var descriptionEdit: EditText
    private lateinit var priceEdit: EditText
    private lateinit var conditionEdit: EditText
    private lateinit var categoryEdit: EditText
    private lateinit var warningBanner: LinearLayout
    private lateinit var warningText: TextView
    private lateinit var copyTitleButton: Button
    private lateinit var copyDescriptionButton: Button
    private lateinit var copyPriceButton: Button
    private lateinit var copyAllButton: Button
    private lateinit var openVintedButton: Button
    private lateinit var retryButton: Button
    private lateinit var discardButton: Button
    private lateinit var markPostedButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_vinted_draft_review)

        val incomingDraftId = intent.getStringExtra(EXTRA_DRAFT_ID)
        if (incomingDraftId == null) {
            Toast.makeText(this, "Draft no longer available — capture again to create a new listing.", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        draftId = incomingDraftId

        val payload = VintedDraftSessionStore.get(draftId) ?: run {
            // Memory miss — try disk fallback (process death, re-entry from drafts list)
            val diskPayload = VintedDraftStore.load(this, draftId)
            if (diskPayload != null) {
                // Repopulate session store so subsequent operations are fast
                VintedDraftSessionStore.put(diskPayload)
            }
            diskPayload
        }
        if (payload == null) {
            Toast.makeText(this, "Draft no longer available — capture again to create a new listing.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        bindViews()
        applyPayload(payload)
        wireButtons()
    }

    private fun bindViews() {
        itemNameValue = findViewById(R.id.itemNameValue)
        brandEdit = findViewById(R.id.brandEdit)
        titleEdit = findViewById(R.id.titleEdit)
        descriptionEdit = findViewById(R.id.descriptionEdit)
        priceEdit = findViewById(R.id.priceEdit)
        conditionEdit = findViewById(R.id.conditionEdit)
        categoryEdit = findViewById(R.id.categoryEdit)
        warningBanner = findViewById(R.id.warningBanner)
        warningText = findViewById(R.id.warningText)
        copyTitleButton = findViewById(R.id.copyTitleButton)
        copyDescriptionButton = findViewById(R.id.copyDescriptionButton)
        copyPriceButton = findViewById(R.id.copyPriceButton)
        copyAllButton = findViewById(R.id.copyAllButton)
        openVintedButton = findViewById(R.id.openVintedButton)
        retryButton = findViewById(R.id.retryButton)
        discardButton = findViewById(R.id.discardButton)
        markPostedButton = findViewById(R.id.markPostedButton)
    }

    private fun applyPayload(p: VintedDraftSessionStore.Payload) {
        itemNameValue.text = p.itemName
        brandEdit.setText(p.brand ?: "")
        titleEdit.setText(p.title)
        descriptionEdit.setText(p.description)
        priceEdit.setText(p.suggestedPrice)
        conditionEdit.setText(p.condition)
        categoryEdit.setText(p.category)

        photoPaths = p.photoPaths
        platform = p.platform

        val warningsToShow = mutableListOf<String>()
        if (p.needsManualVerification) {
            warningsToShow.add("Low confidence — verify before posting on $platform.")
        }
        for (w in p.warnings) {
            warningsToShow.add(formatWarning(w))
        }
        if (warningsToShow.isNotEmpty()) {
            warningText.text = warningsToShow.joinToString("\n")
            warningBanner.visibility = View.VISIBLE
        } else {
            warningBanner.visibility = View.GONE
        }
    }

    private fun formatWarning(w: String): String = when (w) {
        "vision_identification_fallback" -> "Vision fell back — item identification may be inaccurate."
        "listing_draft_fallback" -> "Draft generator fell back — content may be generic."
        else -> w
    }

    private fun wireButtons() {
        copyTitleButton.setOnClickListener {
            copyToClipboard("Title", titleEdit.text.toString())
            Toast.makeText(this, "Title copied", Toast.LENGTH_SHORT).show()
        }
        copyDescriptionButton.setOnClickListener {
            copyToClipboard("Description", descriptionEdit.text.toString())
            Toast.makeText(this, "Description copied", Toast.LENGTH_SHORT).show()
        }
        copyPriceButton.setOnClickListener {
            copyToClipboard("Price", priceEdit.text.toString())
            Toast.makeText(this, "Price copied", Toast.LENGTH_SHORT).show()
        }
        copyAllButton.setOnClickListener {
            val full = buildString {
                appendLine(titleEdit.text.toString())
                appendLine()
                appendLine(descriptionEdit.text.toString())
                appendLine()
                append("Price: ").append(priceEdit.text.toString())
            }
            copyToClipboard("Vinted draft", full)
            Toast.makeText(this, "Full draft copied", Toast.LENGTH_SHORT).show()
        }

        openVintedButton.setOnClickListener { onOpenVintedClicked() }

        retryButton.setOnClickListener { onRetryClicked() }
        discardButton.setOnClickListener { onDiscardClicked() }
        markPostedButton.setOnClickListener { onMarkPostedClicked() }
    }

    private fun copyToClipboard(label: String, text: String) {
        val cb = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cb.setPrimaryClip(ClipData.newPlainText(label, text))
    }

    private fun onRetryClicked() {
        AlertDialog.Builder(this)
            .setTitle("Retry?")
            .setMessage("Tony will redraft this listing. Your current edits will be lost. Continue?")
            .setPositiveButton("Continue") { _, _ -> performRetry() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun performRetry() {
        retryButton.isEnabled = false
        retryButton.text = "Tony's redrafting…"

        Thread {
            try {
                val request = VintedListingRequest(
                    imagePaths = photoPaths,
                    platform = platform,
                    idempotencyKey = UUID.randomUUID().toString()
                )
                val result = NovaApiClient.createVintedListingMulti(request)

                runOnUiThread {
                    if (result.errorCode != null) {
                        Toast.makeText(this, result.errorMessage ?: "Retry failed", Toast.LENGTH_LONG).show()
                        retryButton.isEnabled = true
                        retryButton.text = "Retry — get a fresh draft"
                    } else {
                        val newPayload = VintedDraftSessionStore.Payload(
                            draftId = draftId,
                            itemName = result.itemName,
                            brand = result.brand,
                            title = result.title,
                            description = result.description,
                            suggestedPrice = result.suggestedPrice,
                            condition = result.condition,
                            category = result.category,
                            confidence = result.confidence,
                            needsManualVerification = result.needsManualVerification,
                            warnings = result.warnings,
                            rawJson = result.rawJson,
                            platform = platform,
                            photoPaths = photoPaths
                        )
                        VintedDraftSessionStore.put(newPayload)
                        Thread {
                            VintedDraftStore.save(applicationContext, newPayload)
                        }.start()
                        applyPayload(newPayload)
                        retryButton.isEnabled = true
                        retryButton.text = "Retry — get a fresh draft"
                        Toast.makeText(this, "Fresh draft ready", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this, "Retry error: ${e.message ?: "unknown"}", Toast.LENGTH_LONG).show()
                    retryButton.isEnabled = true
                    retryButton.text = "Retry — get a fresh draft"
                }
            }
        }.start()
    }

    private fun onDiscardClicked() {
        AlertDialog.Builder(this)
            .setTitle("Discard draft?")
            .setMessage("Photos will be deleted. Chat history is kept.")
            .setPositiveButton("Discard") { _, _ ->
                deleteDraftPhotosSafely()
                VintedDraftSessionStore.remove(draftId)
                VintedDraftStore.delete(this@VintedDraftReviewActivity, draftId)
                finish()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun onMarkPostedClicked() {
        deleteDraftPhotosSafely()
        VintedDraftSessionStore.remove(draftId)
        VintedDraftStore.delete(this, draftId)

        ChatHistoryStore.appendMessage(
            context = this,
            role = "tony",
            text = "Marked as posted on $platform.",
            provider = "vinted"
        )
        Toast.makeText(this, "Marked as posted", Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun deleteDraftPhotosSafely() {
        val safePrefix = File(cacheDir, "vinted").absolutePath + File.separator
        for (path in photoPaths) {
            try {
                val f = File(path)
                if (f.absolutePath.startsWith(safePrefix) && f.exists()) {
                    f.delete()
                }
            } catch (_: Exception) {
                // best-effort cleanup
            }
        }
    }

    private fun buildVintedShareText(): String {
        val title = titleEdit.text.toString().trim()
        val description = descriptionEdit.text.toString().trim()
        val price = priceEdit.text.toString().trim()
        return buildString {
            if (title.isNotEmpty()) {
                append(title)
                append("\n\n")
            }
            if (description.isNotEmpty()) {
                append(description)
                append("\n\n")
            }
            if (price.isNotEmpty()) {
                append("Price: ")
                append(price)
            }
        }.trimEnd()
    }

    private fun onOpenVintedClicked() {
        val shareText = buildVintedShareText()
        copyToClipboard("Vinted draft", shareText)

        val existingFiles = photoPaths
            .map { File(it) }
            .filter { it.exists() }

        if (existingFiles.isEmpty()) {
            Toast.makeText(this, "No photos available — draft text copied to clipboard", Toast.LENGTH_LONG).show()
            return
        }

        try {
            val uris = existingFiles.map { file ->
                FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
            }

            val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "image/jpeg"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
                putExtra(Intent.EXTRA_TEXT, shareText)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

                val clip = ClipData.newUri(contentResolver, "Vinted photos", uris[0])
                for (i in 1 until uris.size) {
                    clip.addItem(ClipData.Item(uris[i]))
                }
                clipData = clip
            }

            val handlers = packageManager.queryIntentActivities(intent, 0)
            if (handlers.isEmpty()) {
                Toast.makeText(this, "No app available — draft text copied to clipboard", Toast.LENGTH_LONG).show()
                return
            }

            startActivity(Intent.createChooser(intent, "Open in Vinted"))
        } catch (e: Exception) {
            Toast.makeText(this, "Could not open Vinted — draft text copied to clipboard", Toast.LENGTH_LONG).show()
        }
    }
}
