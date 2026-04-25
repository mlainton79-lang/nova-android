package com.mlainton.nova

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.speech.tts.TextToSpeech
import android.speech.RecognizerIntent
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.exifinterface.media.ExifInterface
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.noties.markwon.Markwon
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Locale
import android.location.Location
import android.location.LocationManager

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var statusText: TextView
    private lateinit var inputText: EditText
    private lateinit var brainPillText: TextView
    private lateinit var photoPreview: ImageView
    private lateinit var chatScrollView: ScrollView
    private lateinit var chatContainer: LinearLayout
    private lateinit var chatListAdapter: ChatListAdapter

    private lateinit var menuButton: ImageButton
    private lateinit var plusButton: ImageButton
    private lateinit var micButton: ImageButton
    private lateinit var sendButton: ImageButton

    private lateinit var drawerBrainButton: Button
    private lateinit var drawerClearChatButton: Button
    private lateinit var drawerCloseButton: Button
    private lateinit var drawerNewChatButton: Button
    private lateinit var drawerSyncCodebaseButton: Button

    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var tonyMediaPlayer: android.media.MediaPlayer? = null
    private var currentBrainMode: BrainMode = BrainMode.LOCAL_TONY
    private lateinit var markwon: Markwon

    private var pendingCameraBase64: String? = null
    private var pendingImageBitmap: Bitmap? = null
    private var lastCameraBase64: String? = null
    private var photoUri: Uri? = null
    private var morningReportChecked = false
    private var lastKnownLocation: String? = null
    private var pendingVintedPlatform: String? = null

    companion object {
        private const val PICK_FILE_REQUEST = 1001
        private const val CAMERA_REQUEST = 1002
        private const val VOICE_REQUEST = 1003
        private const val VINTED_CAPTURE_REQUEST = 1004
        private const val CAMERA_PERMISSION_REQUEST = 2001
        private const val LOCATION_PERMISSION_REQUEST = 2002
        private const val DEFAULT_INPUT_HINT = "Message Tony..."
        private const val CAMERA_INPUT_HINT = "Ask Tony about this image..."
        private const val FILE_INPUT_HINT = "Ask Tony about this file..."
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        markwon = Markwon.create(this)

        drawerLayout = findViewById(R.id.drawerLayout)
        statusText = findViewById(R.id.statusText)
        inputText = findViewById(R.id.inputText)
        brainPillText = findViewById(R.id.brainPillText)
        photoPreview = findViewById(R.id.photoPreview)
        chatScrollView = findViewById(R.id.chatScrollView)
        chatContainer = findViewById(R.id.chatContainer)

        menuButton = findViewById(R.id.menuButton)
        plusButton = findViewById(R.id.plusButton)
        micButton = findViewById(R.id.micButton)
        sendButton = findViewById(R.id.sendButton)

        drawerBrainButton = findViewById(R.id.drawerBrainButton)
        drawerClearChatButton = findViewById(R.id.drawerClearChatButton)
        drawerCloseButton = findViewById(R.id.drawerCloseButton)
        drawerNewChatButton = findViewById(R.id.drawerNewChatButton)
        drawerSyncCodebaseButton = findViewById(R.id.drawerSyncCodebaseButton)

        currentBrainMode = BrokerPrefs.getBrainMode(this)
        renderBrainMode()

        val recycler = findViewById<RecyclerView>(R.id.chatListRecycler)
        recycler.layoutManager = LinearLayoutManager(this)
        chatListAdapter = ChatListAdapter(
            chats = ChatHistoryStore.listChats(this),
            activeChatId = ChatHistoryStore.getActiveChatId(this),
            onTap = { session ->
                ChatHistoryStore.openChatById(this, session.id)
                drawerLayout.closeDrawer(GravityCompat.START)
                renderChatHistory()
                refreshChatList()
                statusText.text = "Opened ${session.title}"
            },
            onLongPress = { session -> showChatOptions(session) }
        )
        recycler.adapter = chatListAdapter

        menuButton.setOnClickListener {
            refreshChatList()
            drawerLayout.openDrawer(GravityCompat.START)
        }

        drawerNewChatButton.setOnClickListener {
            val historySnapshot = buildFullHistory()
            ChatHistoryStore.createNewChat(this)
            DocumentStore.clearDocument(this)
            clearPendingCamera()
            clearLastCamera()
            morningReportChecked = false
            drawerLayout.closeDrawer(GravityCompat.START)
            renderChatHistory()
            refreshChatList()
            statusText.text = "New chat started"
            triggerSummarisationWithHistory(historySnapshot)
        }

        drawerSyncCodebaseButton.setOnClickListener {
            drawerLayout.closeDrawer(GravityCompat.START)
            syncCodebaseToTony()
        }


        plusButton.setOnClickListener { showPlusMenu(it) }
        plusButton.setOnLongClickListener { startVintedFlow(); true }
        micButton.setOnClickListener { openVoiceInput() }
        sendButton.setOnClickListener { sendCurrentMessage() }
        brainPillText.setOnClickListener { showBrainPicker() }

        drawerBrainButton.setOnClickListener {
            drawerLayout.closeDrawer(GravityCompat.START)
            showBrainPicker()
        }

        drawerClearChatButton.setOnClickListener {
            drawerLayout.closeDrawer(GravityCompat.START)
            clearChat()
        }

        drawerCloseButton.setOnClickListener {
            drawerLayout.closeDrawer(GravityCompat.START)
        }

        statusText.setOnClickListener {
            val current = statusText.text.toString().trim()
            if (current.isNotEmpty()) speakTony(current)
        }

        tts = TextToSpeech(this, this)
        fetchLocationInBackground()
        requestCalendarPermission()
        // Initialise on-device model if downloaded
        Thread {
            if (OnDeviceModel.isModelDownloaded(this)) {
                val ok = OnDeviceModel.initialise(this)
                android.util.Log.d("TONY", "On-device model: ${if (ok) "ready" else "failed"}")
            }
        }.start()
        renderChatHistory()
    }

    private fun syncCodebaseToTony() {
        statusText.text = "Syncing codebase to Tony..."
        Thread {
            val files = mutableMapOf<String, String>()
            val projectRoot = File("/storage/emulated/0/Download/Nova_phase3e")
            projectRoot.walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .forEach { file ->
                    try {
                        val content = file.readText(Charsets.UTF_8)
                        if (content.isNotBlank()) {
                            val shortPath = file.relativeTo(projectRoot).path
                            files[shortPath] = content
                        }
                    } catch (_: Exception) {}
                }
            try {
                val gradle = File("/storage/emulated/0/Download/Nova_phase3e/app/build.gradle")
                if (gradle.exists()) files["app/build.gradle"] = gradle.readText()
            } catch (_: Exception) {}
            val ok = if (files.isNotEmpty()) NovaApiClient.syncCodebase(files) else false
            runOnUiThread {
                if (ok) {
                    statusText.text = "Synced ${files.size} files to Tony"
                    Toast.makeText(this, "Tony now knows ${files.size} Nova source files.", Toast.LENGTH_LONG).show()
                } else {
                    statusText.text = "Sync failed — try again"
                    Toast.makeText(this, "Codebase sync failed.", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    override fun onResume() {
        super.onResume()
        currentBrainMode = BrokerPrefs.getBrainMode(this)
        renderBrainMode()
        renderChatHistory()
        refreshChatList()
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val engine = tts ?: return
            val ukResult = engine.setLanguage(Locale.UK)
            if (ukResult == TextToSpeech.LANG_MISSING_DATA || ukResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                engine.setLanguage(Locale.US)
            }
            engine.setPitch(0.85f)
            engine.setSpeechRate(0.92f)
            ttsReady = true
            statusText.text = "Tony ◆ ready"
        } else {
            ttsReady = false
            statusText.text = "Tony voice failed to start."
            Toast.makeText(this, "Text to speech could not initialise.", Toast.LENGTH_LONG).show()
        }
    }

    private fun refreshChatList() {
        chatListAdapter.update(
            ChatHistoryStore.listChats(this),
            ChatHistoryStore.getActiveChatId(this)
        )
    }

    private fun showChatOptions(session: ChatSessionSummary) {
        val pinLabel = if (session.pinned) "Unpin" else "Pin"
        val options = arrayOf("Share transcript", "Copy transcript", "Rename", "Delete", pinLabel)
        AlertDialog.Builder(this)
            .setTitle(session.title)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> shareChatTranscript(session)
                    1 -> copyChatTranscript(session)
                    2 -> showRenameDialog(session)
                    3 -> confirmDeleteChat(session)
                    4 -> { ChatHistoryStore.pinChat(this, session.id); refreshChatList() }
                }
            }
            .show()
    }

    private fun shareChatTranscript(session: ChatSessionSummary) {
        val progress = android.app.ProgressDialog(this).apply {
            setMessage("Building transcript...")
            setCancelable(false)
            show()
        }
        Thread {
            val chatJson = ChatHistoryStore.exportChatAsJson(this@MainActivity, session.id)
            val markdown = if (chatJson != null) {
                NovaApiClient.formatTranscript(chatJson.toString())
            } else null
            runOnUiThread {
                progress.dismiss()
                if (markdown == null) {
                    Toast.makeText(this@MainActivity, "Couldn't build transcript", Toast.LENGTH_LONG).show()
                    return@runOnUiThread
                }
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, markdown)
                    putExtra(Intent.EXTRA_SUBJECT, "Chat transcript — ${session.title}")
                }
                startActivity(Intent.createChooser(intent, "Share transcript"))
            }
        }.start()
    }

    private fun copyChatTranscript(session: ChatSessionSummary) {
        val progress = android.app.ProgressDialog(this).apply {
            setMessage("Building transcript...")
            setCancelable(false)
            show()
        }
        Thread {
            val chatJson = ChatHistoryStore.exportChatAsJson(this@MainActivity, session.id)
            val markdown = if (chatJson != null) {
                NovaApiClient.formatTranscript(chatJson.toString())
            } else null
            runOnUiThread {
                progress.dismiss()
                if (markdown == null) {
                    Toast.makeText(this@MainActivity, "Couldn't build transcript", Toast.LENGTH_LONG).show()
                    return@runOnUiThread
                }
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("Chat transcript", markdown)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(this@MainActivity, "Transcript copied to clipboard", Toast.LENGTH_SHORT).show()
            }
        }.start()
    }

    private fun showRenameDialog(session: ChatSessionSummary) {
        val input = EditText(this)
        input.setText(session.title)
        AlertDialog.Builder(this)
            .setTitle("Rename chat")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val newTitle = input.text.toString().trim()
                if (newTitle.isNotEmpty()) {
                    ChatHistoryStore.renameChat(this, session.id, newTitle)
                    refreshChatList()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun confirmDeleteChat(session: ChatSessionSummary) {
        AlertDialog.Builder(this)
            .setTitle("Delete chat")
            .setMessage("Delete \"${session.title}\"? This cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                ChatHistoryStore.deleteChat(this, session.id)
                refreshChatList()
                renderChatHistory()
                statusText.text = "Chat deleted."
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun renderBrainMode() {
        brainPillText.text = currentBrainMode.displayName
        drawerBrainButton.text = "Choose brain: ${currentBrainMode.displayName}"
    }

    private fun showBrainPicker() {
        val modes = BrainMode.entries.toTypedArray()
        val labels = modes.map { it.displayName }.toTypedArray()
        val selectedIndex = modes.indexOf(currentBrainMode)
        AlertDialog.Builder(this)
            .setTitle("Choose brain")
            .setSingleChoiceItems(labels, selectedIndex) { dialog, which ->
                currentBrainMode = modes[which]
                BrokerPrefs.setBrainMode(this, currentBrainMode)
                renderBrainMode()
                statusText.text = "Brain set to ${currentBrainMode.displayName}"
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showPlusMenu(anchor: View) {
        val popup = PopupMenu(this, anchor)
        popup.menu.add(0, 1, 0, "Camera / photo")
        popup.menu.add(0, 2, 1, "Upload file")
        popup.menu.add(0, 3, 2, "Vinted / eBay listing")
        popup.menu.add(0, 4, 3, "Check pending emails")
        popup.menu.add(0, 5, 4, "Get Tony's briefing now")
        popup.menu.add(0, 6, 5, "What has Tony built?")
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> { requestCameraPermissionAndOpen(); true }
                2 -> { openFilePicker(); true }
                3 -> { startVintedFlow(); true }
                4 -> { checkPendingEmails(); true }
                5 -> { requestFreshBriefing(); true }
                6 -> { checkTonyBuilds(); true }
                else -> false
            }
        }
        popup.show()
    }

    private fun checkTonyBuilds() {
        statusText.text = "Tony ◆ checking builds..."
        Thread {
            try {
                val url = java.net.URL("${NovaApiClient.BASE_URL}/api/v1/chat/stream")
                val conn = (url.openConnection() as java.net.HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 30000
                    readTimeout = 30000
                    doOutput = true
                    setRequestProperty("Authorization", "Bearer ${NovaApiClient.DEV_TOKEN}")
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("Accept", "text/event-stream")
                }
                val body = org.json.JSONObject().apply {
                    put("provider", "gemini")
                    put("message", "check pending builds")
                    put("history", org.json.JSONArray())
                }.toString()
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }

                val fullText = StringBuilder()
                if (conn.responseCode == 200) {
                    val reader = java.io.BufferedReader(java.io.InputStreamReader(conn.inputStream))
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        val l = line ?: continue
                        if (!l.startsWith("data:")) continue
                        val data = l.removePrefix("data:").trim()
                        if (data.isBlank()) continue
                        try {
                            val json = org.json.JSONObject(data)
                            when (json.optString("type")) {
                                "chunk" -> fullText.append(json.optString("text", ""))
                                "done" -> break
                            }
                        } catch (_: Exception) {}
                    }
                }
                val reply = fullText.toString().ifBlank { "Couldn't check builds right now." }
                runOnUiThread {
                    ChatHistoryStore.appendMessage(this, "tony", reply, provider = "builds")
                    statusText.text = "Tony is ready."
                    renderChatHistory()
                    refreshChatList()
                }
            } catch (e: Exception) {
                runOnUiThread { statusText.text = "Build check failed: ${e.message}" }
            }
        }.start()
    }

    private fun checkPendingEmails() {
        statusText.text = "Checking email queue..."
        Thread {
            try {
                val url = java.net.URL("${NovaApiClient.BASE_URL}/api/v1/email-agent/pending")
                val conn = (url.openConnection() as java.net.HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 8000
                    readTimeout = 15000
                    setRequestProperty("Authorization", "Bearer ${NovaApiClient.DEV_TOKEN}")
                }
                if (conn.responseCode == 200) {
                    val response = org.json.JSONObject(conn.inputStream.bufferedReader().readText())
                    val emails = response.optJSONArray("emails")
                    val count = emails?.length() ?: 0
                    val reply = if (count == 0) {
                        "No emails waiting for your approval right now."
                    } else {
                        val first = emails!!.getJSONObject(0)
                        "You have $count email${if (count > 1) "s" else ""} waiting for approval.\n\nMost recent: **${first.optString("subject")}**\nTo: ${first.optString("to")}\n\nSay 'approve email ${first.optInt("id")}' to send it, or ask me to show you the draft."
                    }
                    runOnUiThread {
                        ChatHistoryStore.appendMessage(this, "tony", reply, provider = "email")
                        statusText.text = "Tony ready"
                        renderChatHistory()
                        refreshChatList()
                    }
                } else {
                    runOnUiThread { statusText.text = "Tony ready" }
                }
            } catch (e: Exception) {
                runOnUiThread { statusText.text = "Tony ready" }
            }
        }.start()
    }

    private fun requestFreshBriefing() {
        getSharedPreferences("nova_prefs", MODE_PRIVATE).edit()
            .putLong("last_briefing", 0L).apply()
        fetchTonyBriefing()
    }

    private fun openVoiceInput() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.UK.toLanguageTag())
            putExtra(
                RecognizerIntent.EXTRA_PROMPT,
                if (pendingCameraBase64 != null) "Ask Tony about the image..." else "Speak to Tony"
            )
        }
        try {
            statusText.text = "Listening..."
            startActivityForResult(intent, VOICE_REQUEST)
        } catch (_: Exception) {
            statusText.text = "Voice input is unavailable on this device."
            Toast.makeText(this, "Voice input is unavailable.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun correctVoiceTranscription(text: String): String {
        // Call backend for intelligent AI-powered correction
        // Falls back to original if backend unavailable
        return try {
            val url = java.net.URL("${NovaApiClient.BASE_URL}/api/v1/voice/correct")
            val conn = (url.openConnection() as java.net.HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 3000
                readTimeout = 4000
                doOutput = true
                setRequestProperty("Authorization", "Bearer ${NovaApiClient.DEV_TOKEN}")
                setRequestProperty("Content-Type", "application/json")
            }
            val body = org.json.JSONObject().apply { put("text", text) }.toString()
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            if (conn.responseCode == 200) {
                val json = org.json.JSONObject(conn.inputStream.bufferedReader().readText())
                json.optString("corrected", text)
            } else text
        } catch (_: Exception) {
            text.trimStart().replaceFirstChar { it.uppercase() }
        }
    }

    private fun sendCurrentMessage() {
        val message = inputText.text.toString().trim()

        val cameraBase64 = pendingCameraBase64
        if (cameraBase64 != null) {
            val userPrompt = if (message.isNotEmpty()) message
                else "Please describe what you see in this image."
            inputText.setText("")
            inputText.hint = DEFAULT_INPUT_HINT
            hideKeyboard()
            clearPendingCamera()
            ChatHistoryStore.appendMessage(this, "user", "[Camera image]")
            if (message.isNotEmpty()) {
                ChatHistoryStore.appendMessage(this, "user", message)
            }
            renderChatHistory()
            refreshChatList()
            sendCameraImageToTony(userPrompt, cameraBase64)
            return
        }

        if (message.isEmpty()) {
            Toast.makeText(this, "Type a message first.", Toast.LENGTH_SHORT).show()
            return
        }
        if (handleChatCommand(message)) { inputText.setText(""); hideKeyboard(); return }
        if (handleMemoryCommand(message)) { inputText.setText(""); hideKeyboard(); return }

        HomeMemoryStore.saveLastMessage(this, message)
        ChatHistoryStore.appendMessage(this, "user", message)
        inputText.setText("")
        hideKeyboard()
        renderChatHistory()
        refreshChatList()
        processThroughBroker(message)
    }

    private fun handleChatCommand(message: String): Boolean {
        val lower = message.trim().lowercase(java.util.Locale.ROOT)
        return when {
            lower == "new chat" || lower == "start new chat" -> {
                val historySnapshot = buildFullHistory()
                val session = ChatHistoryStore.createNewChat(this)
                DocumentStore.clearDocument(this)
                clearPendingCamera()
                clearLastCamera()
                morningReportChecked = false
                ChatHistoryStore.appendMessage(this, "tony", "New chat ready.", bumpActivity = false)
                statusText.text = "Opened ${session.title}"
                renderChatHistory()
                refreshChatList()
                triggerSummarisationWithHistory(historySnapshot)
                true
            }
            lower == "show chats" || lower == "list chats" || lower == "chat list" -> {
                ChatHistoryStore.appendMessage(this, "user", message, bumpActivity = false)
                ChatHistoryStore.appendMessage(this, "tony", ChatHistoryStore.renderChatList(this), bumpActivity = false)
                statusText.text = "Chat list shown"
                renderChatHistory()
                true
            }
            lower.startsWith("open chat ") -> {
                val query = message.removePrefix("open chat ").removePrefix("Open chat ").trim()
                val number = query.toIntOrNull()
                val session = if (number != null) ChatHistoryStore.openChatByNumber(this, number)
                              else ChatHistoryStore.openChatByTitle(this, query)
                if (session == null) {
                    ChatHistoryStore.appendMessage(this, "user", message)
                    ChatHistoryStore.appendMessage(this, "tony", "I could not find a chat matching \"$query\". Say \"list chats\" to see what is available.")
                    statusText.text = "Chat not found"
                    renderChatHistory()
                } else {
                    statusText.text = "Opened ${session.title}"
                    renderChatHistory()
                    refreshChatList()
                }
                true
            }
            lower.startsWith("switch to ") -> {
                val query = message.removePrefix("switch to ").removePrefix("Switch to ").trim()
                val session = ChatHistoryStore.openChatByTitle(this, query)
                if (session == null) {
                    ChatHistoryStore.appendMessage(this, "user", message)
                    ChatHistoryStore.appendMessage(this, "tony", "I could not find a chat called \"$query\".")
                    renderChatHistory()
                } else {
                    statusText.text = "Opened ${session.title}"
                    renderChatHistory()
                    refreshChatList()
                }
                true
            }
            lower == "resume last chat" -> {
                val chats = ChatHistoryStore.listChats(this)
                val activeId = ChatHistoryStore.getActiveChatId(this)
                val last = chats.firstOrNull { it.id != activeId }
                if (last == null) {
                    ChatHistoryStore.appendMessage(this, "tony", "There is only one chat.", bumpActivity = false)
                    renderChatHistory()
                } else {
                    ChatHistoryStore.openChatById(this, last.id)
                    statusText.text = "Resumed ${last.title}"
                    renderChatHistory()
                    refreshChatList()
                }
                true
            }
            else -> false
        }
    }

    private fun handleMemoryCommand(message: String): Boolean {
        val lower = message.lowercase(Locale.ROOT)
        val forgetQuery = extractForgetQuery(message, lower)
        return when {
            lower.startsWith("remember that ") -> {
                val fact = message.substringAfter("remember that ").trim()
                if (fact.isEmpty()) false else {
                    MemoryStore.addMemory(this, fact)
                    Thread { NovaApiClient.addMemory("facts", fact) }.start()
                    ChatHistoryStore.appendMessage(this, "user", message)
                    ChatHistoryStore.appendMessage(this, "tony", "I will remember that.")
                    statusText.text = "Memory saved"
                    renderChatHistory()
                    speakTony("I will remember that.")
                    true
                }
            }
            lower.startsWith("remember ") -> {
                val fact = message.substringAfter("remember ").trim()
                if (fact.isEmpty()) false else {
                    MemoryStore.addMemory(this, fact)
                    Thread { NovaApiClient.addMemory("facts", fact) }.start()
                    ChatHistoryStore.appendMessage(this, "user", message)
                    ChatHistoryStore.appendMessage(this, "tony", "I will remember that.")
                    statusText.text = "Memory saved"
                    renderChatHistory()
                    speakTony("I will remember that.")
                    true
                }
            }
            forgetQuery != null -> {
                val localRemoved = MemoryStore.forgetMatching(this, forgetQuery)
                ChatHistoryStore.appendMessage(this, "user", message)
                statusText.text = "Checking cloud memory…"
                renderChatHistory()
                Thread {
                    val cloudRemoved = NovaApiClient.forgetFactsMatching(forgetQuery)
                    val total = localRemoved + cloudRemoved
                    runOnUiThread {
                        ChatHistoryStore.appendMessage(this, "tony",
                            "Removed $total matching memory item(s).")
                        statusText.text = "Memory updated"
                        renderChatHistory()
                    }
                }.start()
                true
            }
            else -> false
        }
    }

    private fun extractForgetQuery(message: String, lower: String): String? {
        val start: String
        val end: String
        when {
            lower.startsWith("forget about ") -> { start = "forget about "; end = "" }
            lower.startsWith("permanently forget ") -> { start = "permanently forget "; end = "" }
            lower.startsWith("delete memory of ") -> { start = "delete memory of "; end = "" }
            lower.startsWith("remove memory of ") -> { start = "remove memory of "; end = "" }
            lower.startsWith("forget ") && lower.endsWith(" permanently") ->
                { start = "forget "; end = " permanently" }
            lower.startsWith("remove ") && lower.endsWith(" from your memory") ->
                { start = "remove "; end = " from your memory" }
            lower.startsWith("remove ") && lower.endsWith(" from memory") ->
                { start = "remove "; end = " from memory" }
            else -> return null
        }
        return message.substring(start.length, message.length - end.length).trim().takeIf { it.isNotEmpty() }
    }

    private fun saveCurrentTextAsTask() {
        val message = inputText.text.toString().trim()
        if (message.isEmpty()) {
            Toast.makeText(this, "Type a task first.", Toast.LENGTH_SHORT).show()
            return
        }
        val savedTask = TaskStorage.saveTask(this, message)
        HomeMemoryStore.saveLastTask(this, savedTask.text)
        ChatHistoryStore.appendMessage(this, "tony", "Saved task: ${savedTask.text}")
        inputText.setText("")
        hideKeyboard()
        renderChatHistory()
        Toast.makeText(this, "Task saved locally.", Toast.LENGTH_SHORT).show()
    }

    private fun clearChat() {
        AlertDialog.Builder(this)
            .setTitle("Clear chat")
            .setMessage("Remove all current messages from this conversation view?")
            .setPositiveButton("Clear") { _, _ ->
                val historySnapshot = buildFullHistory()
                ChatHistoryStore.clearMessages(this)
                clearPendingCamera()
                clearLastCamera()
                morningReportChecked = false
                renderChatHistory()
                refreshChatList()
                statusText.text = "Chat cleared."
                triggerSummarisationWithHistory(historySnapshot)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun processThroughBroker(message: String) {
        val provider = backendProviderForCurrentBrain()
        if (provider != null) {
            sendMessageToNovaBackend(message, provider)
            return
        }
        if (currentBrainMode == BrainMode.LOCAL_TONY) {
            processOnDevice(message)
            return
        }
        if (currentBrainMode == BrainMode.OPENAI_LIVE) {
            processThroughLiveBroker(message)
            return
        }
        statusText.text = "Tony ◆ thinking..."
        val result = BrainBroker.reply(currentBrainMode, message)
        handleBrokerSuccess(result)
    }

    private fun processThroughLiveBroker(message: String) {
        statusText.text = "Thinking with OpenAI live..."
        Thread {
            try {
                val result = LiveBrokerClient.askOpenAi(this, message)
                runOnUiThread { handleBrokerSuccess(result) }
            } catch (e: Exception) {
                runOnUiThread {
                    statusText.text = "OpenAI live failed — try again"
                    ChatHistoryStore.appendMessage(this, "tony", "Live broker error: ${e.message}")
                    renderChatHistory()
                    Toast.makeText(this, "Live broker failed.", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    private fun handleBrokerSuccess(result: BrokerResult) {
        statusText.text = "Reply from ${result.providerLabel}"
        ChatHistoryStore.appendMessage(this, "tony", result.reply)
        renderChatHistory()
        refreshChatList()
        speakTony(result.reply)
    }

    private fun renderChatHistory() {
        chatContainer.removeAllViews()
        val messages = ChatHistoryStore.getMessages(this)

        if (messages.isEmpty()) {
            pendingImageBitmap?.let { addPendingImageBubble(it) }
            val helper = TextView(this).apply {
                text = "Tony is ready."
                textSize = 16f
                setPadding(12, 12, 12, 12)
                setTextColor(0xFF333355.toInt())
            }
            chatContainer.addView(helper)
            if (!morningReportChecked) {
                morningReportChecked = true
                Thread {
                    val report = NovaApiClient.getMorningReport()
                    if (report != null) {
                        runOnUiThread {
                            ChatHistoryStore.appendMessage(this, "tony", report, provider = "think_engine")
                            renderChatHistory()
                        }
                    }
                }.start()
            }
            return
        }

        for (message in messages) {
            if (message.role == "user" && message.text == "[Camera image]") {
                val b64 = lastCameraBase64
                if (b64 != null) {
                    val bytes = android.util.Base64.decode(b64, android.util.Base64.NO_WRAP)
                    val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    if (bmp != null) addSentImageBubble(bmp) else addImagePlaceholder()
                } else {
                    addImagePlaceholder()
                }
            } else {
                chatContainer.addView(createMessageBlock(message))
            }
        }

        pendingImageBitmap?.let { addPendingImageBubble(it) }
        scrollChatToBottom()
    }

    private fun addSentImageBubble(bitmap: Bitmap) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.END
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 6 }
        }
        val maxW = (resources.displayMetrics.widthPixels * 0.72f).toInt()
        val maxH = (resources.displayMetrics.heightPixels * 0.55f).toInt()
        val aspect = bitmap.height.toFloat() / bitmap.width.toFloat()
        val imgH = (maxW * aspect).toInt().coerceAtMost(maxH)
        val imageView = ImageView(this).apply {
            setImageBitmap(bitmap)
            layoutParams = LinearLayout.LayoutParams(maxW, imgH)
            scaleType = ImageView.ScaleType.CENTER_CROP
            clipToOutline = true
            background = getDrawable(R.drawable.chat_bubble_user)
            setOnClickListener { showFullScreenImage(bitmap) }
        }
        row.addView(imageView)
        chatContainer.addView(row)
    }

    private fun addPendingImageBubble(bitmap: Bitmap) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.END
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 4 }
        }
        val maxW = (resources.displayMetrics.widthPixels * 0.72f).toInt()
        val maxH = (resources.displayMetrics.heightPixels * 0.55f).toInt()
        val aspect = bitmap.height.toFloat() / bitmap.width.toFloat()
        val imgH = (maxW * aspect).toInt().coerceAtMost(maxH)
        val imageView = ImageView(this).apply {
            setImageBitmap(bitmap)
            layoutParams = LinearLayout.LayoutParams(maxW, imgH)
            scaleType = ImageView.ScaleType.CENTER_CROP
            clipToOutline = true
            background = getDrawable(R.drawable.chat_bubble_user)
            setOnClickListener { showFullScreenImage(bitmap) }
        }
        val cancelHint = TextView(this).apply {
            text = "Tap image to view full screen  ·  Tap × to cancel"
            textSize = 11f
            setTextColor(0xFF9B8FBF.toInt())
            setPadding(6, 2, 6, 6)
            gravity = Gravity.END
            setOnClickListener { confirmCancelImage() }
        }
        row.addView(imageView)
        row.addView(cancelHint)
        chatContainer.addView(row)
    }

    private fun addImagePlaceholder() {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.END
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 6 }
        }
        val placeholder = TextView(this).apply {
            text = "📷 Camera image"
            textSize = 15f
            setPadding(18, 12, 18, 12)
            setTextColor(0xFF241C34.toInt())
            background = getDrawable(R.drawable.chat_bubble_user)
        }
        row.addView(placeholder)
        chatContainer.addView(row)
    }

    private fun showFullScreenImage(bitmap: Bitmap) {
        val imageView = ImageView(this).apply {
            setImageBitmap(bitmap)
            scaleType = ImageView.ScaleType.FIT_CENTER
            adjustViewBounds = true
            setPadding(8, 8, 8, 8)
        }
        AlertDialog.Builder(this)
            .setView(imageView)
            .setNegativeButton("Close", null)
            .show()
    }

    private fun confirmCancelImage() {
        AlertDialog.Builder(this)
            .setTitle("Cancel image?")
            .setMessage("Remove this image without sending it?")
            .setPositiveButton("Cancel image") { _, _ ->
                clearPendingCamera()
                statusText.text = "Tony ◆ ready"
                renderChatHistory()
            }
            .setNegativeButton("Keep", null)
            .show()
    }

    private fun createMessageBlock(message: ChatEntry): View {
        val isUser = message.role == "user"
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = if (isUser) Gravity.END else Gravity.START
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 6 }
        }
        val bubble = TextView(this).apply {
            textSize = 16f
            setTextIsSelectable(true)
            maxWidth = (resources.displayMetrics.widthPixels * 0.76f).toInt()
            setPadding(18, 12, 18, 12)
            background = getDrawable(if (isUser) R.drawable.chat_bubble_user else R.drawable.chat_bubble_tony)
            setTextColor(if (isUser) 0xFFDDCCFF.toInt() else 0xFFDDDDFF.toInt())
        }
        if (isUser) bubble.text = message.text
        else markwon.setMarkdown(bubble, message.text)
        row.addView(bubble)

        if (!isUser && message.provider.isNotEmpty()) {
            row.addView(TextView(this).apply {
                text = message.provider
                textSize = 11f
                setTextColor(0xFF005566.toInt())
                setPadding(4, 2, 4, 0)
                letterSpacing = 0.08f
            })
        }

        if (!isUser && message.provider == "Council" && message.debugData.isNotEmpty()) {
            val debugPanel = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                visibility = View.GONE
                setBackgroundColor(0xFF08080F.toInt())
                setPadding(12, 10, 12, 10)
            }
            try {
                val d = org.json.JSONObject(message.debugData)
                val decidingBrain = d.optString("decidingBrain", "")
                val challenge = d.optString("challenge", "")
                val round1 = d.optJSONObject("round1")
                val round2 = d.optJSONObject("round2")
                val providersUsed = d.optJSONArray("providersUsed")
                val providersFailed = d.optJSONArray("providersFailed")

                fun debugLine(text: String, color: Int = 0xFFB0A0CC.toInt()): TextView = TextView(this).apply {
                    this.text = text
                    textSize = 12f
                    setTextColor(color)
                    setPadding(0, 4, 0, 4)
                    setTextIsSelectable(true)
                }

                debugPanel.addView(debugLine("🧠 Decided by: $decidingBrain", 0xFFE0D0FF.toInt()))

                if (providersUsed != null && providersUsed.length() > 0) {
                    val used = (0 until providersUsed.length()).map { providersUsed.getString(it) }
                    debugPanel.addView(debugLine("✅ Active: ${used.joinToString(", ")}"))
                }
                if (providersFailed != null && providersFailed.length() > 0) {
                    val failed = (0 until providersFailed.length()).map { providersFailed.getString(it) }
                    debugPanel.addView(debugLine("❌ Failed: ${failed.joinToString(", ")}", 0xFFCC8888.toInt()))
                }
                if (round1 != null && round1.length() > 0) {
                    debugPanel.addView(debugLine("── Round 1 ──", 0xFF9B8FBF.toInt()))
                    round1.keys().forEach { key ->
                        val v = round1.optString(key)
                        debugPanel.addView(debugLine("$key: $v"))
                    }
                }
                if (challenge.isNotEmpty()) {
                    debugPanel.addView(debugLine("── Challenge ──", 0xFF9B8FBF.toInt()))
                    debugPanel.addView(debugLine(challenge))
                }
                if (round2 != null && round2.length() > 0) {
                    debugPanel.addView(debugLine("── Round 2 ──", 0xFF9B8FBF.toInt()))
                    round2.keys().forEach { key ->
                        val v = round2.optString(key)
                        debugPanel.addView(debugLine("$key: $v"))
                    }
                }
            } catch (_: Exception) {
                debugPanel.addView(TextView(this).apply {
                    text = "Debug data unavailable"
                    textSize = 12f
                    setTextColor(0xFF9B8FBF.toInt())
                })
            }
            val toggle = TextView(this).apply {
                text = "▼ Council detail"
                textSize = 11f
                setTextColor(0xFF005566.toInt())
                setPadding(6, 4, 6, 0)
                setOnClickListener {
                    if (debugPanel.visibility == View.GONE) {
                        debugPanel.visibility = View.VISIBLE
                        text = "▲ Hide detail"
                    } else {
                        debugPanel.visibility = View.GONE
                        text = "▼ Council detail"
                    }
                }
            }
            row.addView(toggle)
            row.addView(debugPanel)
        }
        return row
    }

    private fun scrollChatToBottom() {
        chatScrollView.post { chatScrollView.fullScroll(View.FOCUS_DOWN) }
    }

    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(inputText.windowToken, 0)
    }

    private fun showKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.showSoftInput(inputText, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun fetchLocationInBackground() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                LOCATION_PERMISSION_REQUEST
            )
            return
        }
        Thread {
            try {
                val lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager
                val location = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                    ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                if (location != null) {
                    lastKnownLocation = "${location.latitude},${location.longitude}"
                }
            } catch (_: Exception) {}
        }.start()
    }

    private fun startVintedFlow() {
        // Autonomous Vinted listing flow
        // 1. Take photo, 2. Tony identifies and researches, 3. Creates full listing automatically
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Create Vinted/eBay listing")
            .setMessage("Tony will identify the item, suggest a price, and draft the listing for you.")
            .setPositiveButton("Vinted") { _, _ ->
                pendingVintedPlatform = "vinted"
                launchVintedCapture("vinted")
            }
            .setNegativeButton("eBay") { _, _ ->
                pendingVintedPlatform = "ebay"
                launchVintedCapture("ebay")
            }
            .setNeutralButton("Cancel", null)
            .show()
    }

    private fun launchVintedCapture(platform: String) {
        val intent = Intent(this, VintedCaptureActivity::class.java).apply {
            putExtra("platform", platform)
        }
        startActivityForResult(intent, VINTED_CAPTURE_REQUEST)
    }

    private fun fetchTonyBriefing() {
        val lastBriefing = getSharedPreferences("nova_prefs", MODE_PRIVATE).getLong("last_briefing", 0)
        if (System.currentTimeMillis() - lastBriefing < 4 * 60 * 60 * 1000L) return

        Thread {
            try {
                // Use fast briefing endpoint - pulls from live state, no LLM needed
                val url = java.net.URL("${NovaApiClient.BASE_URL}/api/v1/proactive/briefing")
                val conn = (url.openConnection() as java.net.HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 6000
                    readTimeout = 10000
                    setRequestProperty("Authorization", "Bearer ${NovaApiClient.DEV_TOKEN}")
                }
                if (conn.responseCode == 200) {
                    val response = org.json.JSONObject(conn.inputStream.bufferedReader().readText())
                    val briefing = response.optString("briefing", "").trim()
                    if (briefing.isNotEmpty()) {
                        runOnUiThread {
                            ChatHistoryStore.appendMessage(this, "tony", briefing, provider = "briefing")
                            renderChatHistory()
                            getSharedPreferences("nova_prefs", MODE_PRIVATE).edit()
                                .putLong("last_briefing", System.currentTimeMillis()).apply()
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("TONY_BRIEFING", "Briefing failed: ${e.message}")
            }
        }.start()
    }

    private fun generateFosComplaint() {
        statusText.text = "Tony is preparing your FOS complaint..."
        Thread {
            try {
                val url = java.net.URL("${NovaApiClient.BASE_URL}/api/v1/cases/fos-complaint")
                val conn = (url.openConnection() as java.net.HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 10000
                    readTimeout = 60000
                    setRequestProperty("Authorization", "Bearer ${NovaApiClient.DEV_TOKEN}")
                }
                if (conn.responseCode == 200) {
                    val response = org.json.JSONObject(conn.inputStream.bufferedReader().readText())
                    val nextStep = response.optString("next_step", "")
                    val complaintText = response.optString("complaint_text", "")

                    val reply = "FOS complaint prepared. It's ready to submit.\n\n**Next step:** $nextStep\n\nThe full complaint has been generated. Ask me to show you any part of it."

                    runOnUiThread {
                        ChatHistoryStore.appendMessage(this, "tony", reply, provider = "legal")
                        statusText.text = "FOS complaint ready ◆"
                        renderChatHistory()
                        refreshChatList()
                    }
                } else {
                    runOnUiThread { statusText.text = "FOS complaint generation failed" }
                }
            } catch (e: Exception) {
                runOnUiThread { statusText.text = "Error: ${e.message}" }
            }
        }.start()
    }

    private fun offerVintedListing(imageBase64: String, imageMime: String) {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Create a listing?")
            .setMessage("Tony can identify this item, research sold prices, and create a Vinted or eBay listing for you.")
            .setPositiveButton("Vinted") { _, _ -> createVintedListing(imageBase64, imageMime, "vinted") }
            .setNegativeButton("eBay") { _, _ -> createVintedListing(imageBase64, imageMime, "ebay") }
            .setNeutralButton("Just chat") { _, _ -> }
            .show()
    }

    private fun createVintedListing(imageBase64: String, imageMime: String, platform: String) {
        statusText.text = "Tony ◆ researching item..."
        Thread {
            try {
                val url = java.net.URL("${NovaApiClient.BASE_URL}/api/v1/vinted/create-listing")
                val conn = (url.openConnection() as java.net.HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 30000
                    readTimeout = 60000
                    doOutput = true
                    setRequestProperty("Authorization", "Bearer ${NovaApiClient.DEV_TOKEN}")
                    setRequestProperty("Content-Type", "application/json")
                }
                val body = org.json.JSONObject().apply {
                    put("image_base64", imageBase64)
                    put("image_mime", imageMime)
                    put("platform", platform)
                    put("condition", "good")
                }.toString()
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }

                if (conn.responseCode == 200) {
                    val response = org.json.JSONObject(conn.inputStream.bufferedReader().readText())
                    val listing = response.optJSONObject("listing")
                    val item = response.optJSONObject("item")
                    val itemName = item?.optString("item_name", "Item") ?: "Item"
                    val price = listing?.optString("suggested_price", "?") ?: "?"
                    val title = listing?.optString("title", "") ?: ""
                    val description = listing?.optString("description", "") ?: ""

                    val reply = buildString {
                        append("**$itemName** identified.\n\n")
                        append("**Suggested price:** \u00a3$price\n\n")
                        append("**Listing title:** $title\n\n")
                        append("**Description:**\n$description")
                    }

                    runOnUiThread {
                        ChatHistoryStore.appendMessage(this, "tony", reply, provider = "vinted")
                        statusText.text = "Listing ready ◆"
                        renderChatHistory()
                        refreshChatList()
                    }
                } else {
                    runOnUiThread { statusText.text = "Listing creation failed" }
                }
            } catch (e: Exception) {
                runOnUiThread { statusText.text = "Error: ${e.message}" }
            }
        }.start()
    }

    private fun renderVintedListingResult(result: VintedListingResult, platform: String) {
        if (!result.ok) {
            statusText.text = result.errorMessage ?: "Couldn't create listing draft. Photos kept."
            return
        }

        val sb = StringBuilder()
        sb.append("**${result.itemName}** identified.\n")
        if (!result.brand.isNullOrBlank()) {
            sb.append("Brand: ${result.brand}\n")
        }
        if (result.suggestedPrice.isNotBlank()) {
            sb.append("Suggested price: ${result.suggestedPrice}\n")
        }
        sb.append("\n**Title**\n")
        sb.append(result.title).append("\n\n")
        sb.append("**Description**\n")
        sb.append(result.description).append("\n")
        if (result.condition.isNotBlank()) {
            sb.append("\nCondition: ${result.condition}")
        }
        if (result.category.isNotBlank()) {
            sb.append("\nCategory: ${result.category}")
        }

        val warningFooters = mutableListOf<String>()
        if (result.warnings.contains("vision_identification")) {
            warningFooters.add("⚠ Vision fell back — please verify item name.")
        }
        if (result.warnings.contains("listing_draft")) {
            warningFooters.add("⚠ Draft generated locally — please review title/description.")
        }
        if (result.confidence.equals("low", ignoreCase = true) || result.needsManualVerification) {
            warningFooters.add("⚠ Low confidence — verify before posting on $platform.")
        }
        if (warningFooters.isNotEmpty()) {
            sb.append("\n\n")
            warningFooters.forEachIndexed { idx, w ->
                sb.append(w)
                if (idx < warningFooters.size - 1) sb.append("\n")
            }
        }

        val markdown = sb.toString().trimEnd()

        ChatHistoryStore.appendMessage(this, "tony", markdown, provider = "vinted")
        statusText.text = "Listing ready ◆"
        renderChatHistory()
        refreshChatList()
    }

    private fun showOnDeviceModelStatus() {
        val downloaded = OnDeviceModel.isModelDownloaded(this)
        val ready = OnDeviceModel.isReady()
        val message = when {
            ready -> "On-device model is ready. Switch to 'Local Tony' brain mode to use it offline."
            downloaded -> "Model downloaded but not initialised. Restart the app."
            else -> "On-device model not downloaded yet (1.5GB). Download it to use Tony offline?\n\nThis requires a Wi-Fi connection and will take several minutes."
        }
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("On-Device Model (Offline Tony)")
            .setMessage(message)
            .setPositiveButton(if (downloaded) "OK" else "Download") { _, _ ->
                if (!downloaded) startModelDownload()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun startModelDownload() {
        statusText.text = "Downloading Gemma model (1.5GB)..."
        OnDeviceModel.downloadModel(
            this,
            onProgress = { progress ->
                runOnUiThread { statusText.text = "Downloading: $progress%" }
            },
            onComplete = { success ->
                runOnUiThread {
                    if (success) {
                        statusText.text = "Model downloaded. Initialising..."
                        Thread {
                            val ok = OnDeviceModel.initialise(this)
                            runOnUiThread {
                                statusText.text = if (ok) "On-device model ready." else "Initialisation failed."
                                android.widget.Toast.makeText(this,
                                    if (ok) "Tony can now run offline." else "Model init failed — restart app.",
                                    android.widget.Toast.LENGTH_LONG).show()
                            }
                        }.start()
                    } else {
                        statusText.text = "Download failed. Check connection."
                    }
                }
            }
        )
    }

    private fun requestCalendarPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALENDAR)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.READ_CALENDAR), 2003
            )
        }
    }

    private fun readDeviceCalendar(): String {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALENDAR)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.READ_CALENDAR), 2003
            )
            return ""
        }
        return try {
            val now = System.currentTimeMillis()
            val weekAhead = now + (7 * 24 * 60 * 60 * 1000L)
            val uri = android.provider.CalendarContract.Events.CONTENT_URI
            val projection = arrayOf(
                android.provider.CalendarContract.Events.TITLE,
                android.provider.CalendarContract.Events.DTSTART,
                android.provider.CalendarContract.Events.DTEND,
                android.provider.CalendarContract.Events.EVENT_LOCATION,
                android.provider.CalendarContract.Events.DESCRIPTION,
                android.provider.CalendarContract.Events.ALL_DAY
            )
            val selection = "${android.provider.CalendarContract.Events.DTSTART} >= ? AND ${android.provider.CalendarContract.Events.DTSTART} <= ? AND ${android.provider.CalendarContract.Events.DELETED} = 0"
            val cursor = contentResolver.query(uri, projection, selection, arrayOf(now.toString(), weekAhead.toString()), "${android.provider.CalendarContract.Events.DTSTART} ASC")
            val events = StringBuilder()
            cursor?.use {
                val titleIdx = it.getColumnIndex(android.provider.CalendarContract.Events.TITLE)
                val startIdx = it.getColumnIndex(android.provider.CalendarContract.Events.DTSTART)
                val endIdx = it.getColumnIndex(android.provider.CalendarContract.Events.DTEND)
                val locationIdx = it.getColumnIndex(android.provider.CalendarContract.Events.EVENT_LOCATION)
                val allDayIdx = it.getColumnIndex(android.provider.CalendarContract.Events.ALL_DAY)
                while (it.moveToNext()) {
                    val title = it.getString(titleIdx) ?: "Untitled"
                    val start = it.getLong(startIdx)
                    val end = it.getLong(endIdx)
                    val location = it.getString(locationIdx) ?: ""
                    val allDay = it.getInt(allDayIdx) == 1
                    val fmt = java.text.SimpleDateFormat("EEE dd MMM HH:mm", java.util.Locale.UK)
                    val startStr = if (allDay) java.text.SimpleDateFormat("EEE dd MMM", java.util.Locale.UK).format(java.util.Date(start)) else fmt.format(java.util.Date(start))
                    val endStr = if (allDay) "" else " - ${java.text.SimpleDateFormat("HH:mm", java.util.Locale.UK).format(java.util.Date(end))}"
                    events.append("• $title: $startStr$endStr")
                    if (location.isNotEmpty()) events.append(" @ $location")
                    events.append("\n")
                }
            }
            events.toString().trim()
        } catch (e: Exception) {
            android.util.Log.e("TONY_CALENDAR", "Calendar read failed: ${e.message}")
            ""
        }
    }

    private fun speakTony(text: String) {
        if (text.isBlank()) return
        // Truncate to first 500 chars for voice — speak the start of the reply
        val speakText = text.take(500).trimEnd { !it.isLetterOrDigit() && it != '.' && it != '!' && it != '?' }
        Thread {
            try {
                val url = java.net.URL("${NovaApiClient.BASE_URL}/api/v1/voice/speak")
                val conn = (url.openConnection() as java.net.HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 10000
                    readTimeout = 30000
                    doOutput = true
                    setRequestProperty("Authorization", "Bearer ${NovaApiClient.DEV_TOKEN}")
                    setRequestProperty("Content-Type", "application/json")
                }
                val body = org.json.JSONObject().apply {
                    put("text", speakText)
                    put("voice", "en-GB-RyanNeural")
                }.toString()
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                val responseCode = conn.responseCode
                android.util.Log.d("TONY_VOICE", "Response code: $responseCode")
                if (responseCode == 200) {
                    val responseText = conn.inputStream.bufferedReader().readText()
                    val json = org.json.JSONObject(responseText)
                    val audioB64 = json.optString("audio_base64", "")
                    android.util.Log.d("TONY_VOICE", "Audio b64 length: ${audioB64.length}")
                    if (audioB64.isNotEmpty()) {
                        val bytes = android.util.Base64.decode(audioB64, android.util.Base64.DEFAULT)
                        val tmp = java.io.File(cacheDir, "tony_voice.mp3")
                        tmp.writeBytes(bytes)
                        runOnUiThread {
                            try {
                                tonyMediaPlayer?.release()
                                tonyMediaPlayer = null
                                val player = android.media.MediaPlayer()
                                player.setDataSource(tmp.absolutePath)
                                player.setOnPreparedListener { mp ->
                                    mp.start()
                                    statusText.text = "Tony ◆"
                                }
                                player.setOnCompletionListener { mp ->
                                    mp.release()
                                    if (tonyMediaPlayer == mp) tonyMediaPlayer = null
                                }
                                player.setOnErrorListener { mp, what, extra ->
                                    android.util.Log.e("TONY_VOICE", "MediaPlayer error: what=$what extra=$extra")
                                    mp.release()
                                    if (tonyMediaPlayer == mp) tonyMediaPlayer = null
                                    speakTonyFallback(speakText)
                                    true
                                }
                                tonyMediaPlayer = player
                                player.prepare()
                                player.start()
                                statusText.text = "Tony ◆"
                            } catch (e: Exception) {
                                android.util.Log.e("TONY_VOICE", "MediaPlayer failed: ${e.message}")
                                speakTonyFallback(speakText)
                            }
                        }
                        return@Thread
                    }
                } else {
                    val err = conn.errorStream?.bufferedReader()?.readText() ?: "no error body"
                    android.util.Log.e("TONY_VOICE", "Backend error $responseCode: $err")
                }
            } catch (e: Exception) {
                android.util.Log.e("TONY_VOICE", "Request failed: ${e.message}")
            }
            runOnUiThread { speakTonyFallback(speakText) }
        }.start()
    }

    private fun speakTonyFallback(text: String) {
        val engine = tts
        if (!ttsReady || engine == null) return
        statusText.text = "Tony ◆"
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "TONY_UTTERANCE")
    }

    private fun openFilePicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }
        statusText.text = "Opening file picker..."
        startActivityForResult(intent, PICK_FILE_REQUEST)
    }

    private fun requestCameraPermissionAndOpen() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED) {
            openCameraWithFileProvider()
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                CAMERA_PERMISSION_REQUEST
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_REQUEST) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                openCameraWithFileProvider()
            } else {
                Toast.makeText(this, "Camera permission is needed to take photos.", Toast.LENGTH_LONG).show()
            }
        }
        if (requestCode == LOCATION_PERMISSION_REQUEST) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                fetchLocationInBackground()
            }
        }
    }

    private fun openCameraWithFileProvider() {
        try {
            val photoFile = createImageFile()
            if (photoFile == null) { openCameraFallback(); return }
            val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", photoFile)
            photoUri = uri
            val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                putExtra(MediaStore.EXTRA_OUTPUT, uri)
            }
            statusText.text = "Opening camera..."
            startActivityForResult(intent, CAMERA_REQUEST)
        } catch (e: Exception) { openCameraFallback() }
    }

    private fun openCameraFallback() {
        try {
            photoUri = null
            val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            statusText.text = "Opening camera..."
            startActivityForResult(intent, CAMERA_REQUEST)
        } catch (e: Exception) {
            Toast.makeText(this, "Camera is unavailable on this device.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun createImageFile(): File? {
        return try {
            val dir = getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: return null
            File(dir, "nova_photo_${System.currentTimeMillis()}.jpg")
        } catch (_: Exception) { null }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == VOICE_REQUEST && resultCode == RESULT_OK) {
            val results = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val spokenText = results?.firstOrNull()?.trim().orEmpty()
            if (spokenText.isNotEmpty()) {
                // Set immediately so user sees something
                inputText.setText(spokenText)
                inputText.setSelection(spokenText.length)
                statusText.text = "Correcting transcription..."
                // Correct in background then update
                Thread {
                    val corrected = correctVoiceTranscription(spokenText)
                    runOnUiThread {
                        if (corrected != spokenText) {
                            inputText.setText(corrected)
                            inputText.setSelection(corrected.length)
                        }
                        statusText.text = if (pendingCameraBase64 != null)
                            "Image ready — tap send when ready."
                        else "Voice heard. Tap send when ready."
                    }
                }.start()
            } else {
                statusText.text = "I did not catch that."
            }
        }

        if (requestCode == PICK_FILE_REQUEST && resultCode == RESULT_OK) {
            val uri: Uri? = data?.data
            if (uri != null) {
                try {
                    val flags = data.flags and
                        (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                    contentResolver.takePersistableUriPermission(uri, flags)
                } catch (_: Exception) {}

                val fileName = getDisplayName(uri) ?: "Unknown file"
                val mimeType = contentResolver.getType(uri) ?: "unknown"
                val extractedText = tryReadSupportedTextFile(uri, fileName, mimeType)
                val extractedPdfBase64 = tryReadSupportedPdfBase64(uri, fileName, mimeType)
                val extractedImageBase64 = tryReadSupportedImageBase64(uri, fileName, mimeType)
                val extractedZipBase64 = tryReadSupportedZipBase64(uri, fileName, mimeType)
                val preferredBase64 = extractedPdfBase64 ?: extractedImageBase64 ?: extractedZipBase64
                val extractedDocxText = tryReadSupportedDocxText(uri, fileName, mimeType)
                val preferredText = extractedText ?: extractedDocxText

                HomeMemoryStore.saveLastFile(this, fileName, mimeType)
                if (preferredText != null || preferredBase64 != null) {
                    DocumentStore.saveDocument(this, fileName, mimeType, preferredText, preferredBase64)
                    ChatHistoryStore.appendMessage(this, "tony", "I've loaded $fileName. What do you want to know?")
                } else {
                    ChatHistoryStore.appendMessage(this, "tony", "I received $fileName but can't read this file type yet.")
                }
                statusText.text = "File ready — ask Tony about it"
                inputText.hint = FILE_INPUT_HINT
                inputText.requestFocus()
                showKeyboard()
                renderChatHistory()
            } else {
                statusText.text = "No file selected"
                Toast.makeText(this, "No file was returned.", Toast.LENGTH_SHORT).show()
            }
        }

        if (requestCode == CAMERA_REQUEST && resultCode == RESULT_OK) {
            var bitmap: Bitmap? = null
            val uri = photoUri
            if (uri != null) {
                try {
                    val input = contentResolver.openInputStream(uri)
                    bitmap = BitmapFactory.decodeStream(input)
                    input?.close()
                    bitmap = bitmap?.let { correctBitmapRotation(it, uri) }
                } catch (_: Exception) {}
            }
            if (bitmap == null) bitmap = data?.extras?.get("data") as? Bitmap
            if (bitmap != null) {
                HomeMemoryStore.saveLastCamera(this)
                val base64 = bitmapToBase64(bitmap)
                pendingCameraBase64 = base64
                pendingImageBitmap = bitmap
                lastCameraBase64 = base64
                renderChatHistory()
                // If Vinted flow active, go straight to listing pipeline — skip vision chat
                if (pendingVintedPlatform != null) {
                    val platform = pendingVintedPlatform!!
                    pendingVintedPlatform = null
                    clearPendingCamera()
                    statusText.text = "Tony ◆ identifying item and pricing..."
                    createVintedListing(base64, "image/jpeg", platform)
                } else {
                    statusText.text = "Image ready — ask Tony about it"
                    inputText.hint = CAMERA_INPUT_HINT
                    inputText.requestFocus()
                    showKeyboard()
                }
            } else {
                statusText.text = "No camera preview returned"
                Toast.makeText(this, "The camera did not return an image.", Toast.LENGTH_SHORT).show()
            }
        }

        if (requestCode == VINTED_CAPTURE_REQUEST) {
            if (resultCode != RESULT_OK || data == null) {
                return
            }

            val photoPaths = data.getStringArrayListExtra("photo_paths")
            val platform = data.getStringExtra("platform")

            if (photoPaths.isNullOrEmpty() || platform.isNullOrBlank()) {
                Toast.makeText(this, "No photos returned — try again.", Toast.LENGTH_SHORT).show()
                return
            }

            if (!NetworkUtil.isOnline(this)) {
                statusText.text = "Offline — photos kept. Try again when you're connected."
                return
            }

            statusText.text = "Tony ◆ creating listing draft from ${photoPaths.size} photos…"

            Thread {
                val request = VintedListingRequest(
                    imagePaths = photoPaths,
                    platform = platform
                )
                val result = NovaApiClient.createVintedListingMulti(request)

                runOnUiThread {
                    renderVintedListingResult(result, platform)
                }
            }.start()
        }
    }

    private fun correctBitmapRotation(bitmap: Bitmap, uri: Uri): Bitmap {
        return try {
            val input = contentResolver.openInputStream(uri) ?: return bitmap
            val exif = ExifInterface(input)
            input.close()
            val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            val matrix = Matrix()
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                else -> return bitmap
            }
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        } catch (_: Exception) { bitmap }
    }

    private fun bitmapToBase64(bitmap: Bitmap): String {
        val MAX_IMAGE_SIZE_PX = 2048
        val MAX_BYTE_SIZE_KB = 1024

        var scaledBitmap = bitmap
        val width = bitmap.width
        val height = bitmap.height
        if (width > MAX_IMAGE_SIZE_PX || height > MAX_IMAGE_SIZE_PX) {
            val ratio = Math.min(MAX_IMAGE_SIZE_PX.toFloat() / width, MAX_IMAGE_SIZE_PX.toFloat() / height)
            val newWidth = (width * ratio).toInt()
            val newHeight = (height * ratio).toInt()
            scaledBitmap = Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
        }

        val stream = ByteArrayOutputStream()
        var quality = 90
        var byteArray: ByteArray
        do {
            stream.reset()
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)
            byteArray = stream.toByteArray()
            quality -= 5
        } while (byteArray.size > MAX_BYTE_SIZE_KB * 1024 && quality >= 60)

        return android.util.Base64.encodeToString(byteArray, android.util.Base64.NO_WRAP)
    }

    private fun clearPendingCamera() {
        pendingCameraBase64 = null
        pendingImageBitmap = null
        inputText.hint = DEFAULT_INPUT_HINT
    }

    private fun clearLastCamera() { lastCameraBase64 = null }

    private fun sendCameraImageToTony(userPrompt: String, imageBase64: String) {
        val rawProvider = backendProviderForCurrentBrain() ?: "gemini"
        val provider = when (rawProvider) {
            "Council" -> "gemini"
            "auto" -> "gemini"
            else -> rawProvider
        }
        val history = buildBackendHistoryFor(userPrompt)
        val doc = DocumentStore.getDocument(this)
        statusText.text = "Analysing image..."

        val streamingBubble = TextView(this).apply {
            text = "▍"
            textSize = 16f
            setTextColor(0xFF111111.toInt())
            maxWidth = (resources.displayMetrics.widthPixels * 0.76f).toInt()
            setPadding(18, 12, 18, 12)
            background = ContextCompat.getDrawable(this@MainActivity, R.drawable.chat_bubble_tony)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.START
                setMargins(0, 0, 0, 6)
            }
        }

        runOnUiThread {
            chatContainer.addView(streamingBubble)
            chatScrollView.post { chatScrollView.fullScroll(View.FOCUS_DOWN) }
        }

        val fullText = StringBuilder()

        Thread {
            NovaApiClient.sendChatStream(
                provider = provider,
                message = userPrompt,
                history = history,
                context = null,
                documentText = doc?.text,
                documentBase64 = doc?.base64,
                documentName = doc?.name,
                documentMime = doc?.mimeType,
                imageBase64 = imageBase64,
                onChunk = { chunk ->
                    fullText.append(chunk)
                    runOnUiThread {
                        markwon.setMarkdown(streamingBubble, fullText.toString() + " ▍")
                        chatScrollView.post { chatScrollView.fullScroll(View.FOCUS_DOWN) }
                    }
                },
                onDone = { ok, completeText, error, _ ->
                    val finalReply = completeText.ifBlank {
                        "Tony couldn't analyse the image right now. Please try again."
                    }
                    runOnUiThread {
                        chatContainer.removeView(streamingBubble)
                        ChatHistoryStore.appendMessage(this, "tony", finalReply, provider = provider)
                        statusText.text = if (ok) "Tony ◆ ready" else "Vision failed — try again"
                        renderChatHistory()
                        refreshChatList()
                        speakTony(finalReply)
                        triggerSummarisationWithHistory(buildFullHistory())
                    }
                }
            )
        }.start()
    }

    private fun triggerSummarisationWithHistory(messages: List<NovaApiClient.HistoryItem>) {
        if (messages.size < 2) return
        Thread {
            try {
                val facts = NovaApiClient.summarise(messages)
                for (fact in facts) NovaApiClient.addMemory("auto", fact)
            } catch (_: Exception) {}
        }.start()
    }

    private fun getDisplayName(uri: Uri): String? {
        return try {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst() && nameIndex >= 0) cursor.getString(nameIndex) else null
            }
        } catch (_: Exception) { null }
    }

    private fun tryReadSupportedTextFile(uri: Uri, fileName: String, mimeType: String): String? {
        if (!isSupportedTextFile(fileName, mimeType)) return null
        return try {
            contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText().take(200000) }
        } catch (_: Exception) { null }
    }

    private fun isSupportedTextFile(fileName: String, mimeType: String): Boolean {
        val lowerName = fileName.lowercase(java.util.Locale.ROOT)
        val lowerMime = mimeType.lowercase(java.util.Locale.ROOT)
        return lowerName.endsWith(".txt") || lowerName.endsWith(".md") ||
            lowerName.endsWith(".json") || lowerName.endsWith(".csv") ||
            lowerMime.startsWith("text/") || lowerMime.contains("json") || lowerMime.contains("csv")
    }

    private fun tryReadSupportedPdfBase64(uri: Uri, fileName: String, mimeType: String): String? {
        val lowerName = fileName.lowercase(java.util.Locale.ROOT)
        val lowerMime = mimeType.lowercase(java.util.Locale.ROOT)
        if (!lowerName.endsWith(".pdf") && !lowerMime.contains("pdf")) return null
        return try {
            contentResolver.openInputStream(uri)?.use { input ->
                val bytes = input.readBytes()
                if (bytes.size > 5_000_000) null
                else android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
            }
        } catch (_: Exception) { null }
    }

    private fun tryReadSupportedDocxText(uri: Uri, fileName: String, mimeType: String): String? {
        val lowerName = fileName.lowercase(java.util.Locale.ROOT)
        val lowerMime = mimeType.lowercase(java.util.Locale.ROOT)
        val isDocx = lowerName.endsWith(".docx") || lowerMime.contains("wordprocessingml") ||
            lowerMime.contains("officedocument.wordprocessingml")
        if (!isDocx) return null
        return try {
            var foundText: String? = null
            contentResolver.openInputStream(uri)?.use { input ->
                java.util.zip.ZipInputStream(input).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null) {
                        if (entry.name == "word/document.xml") {
                            val xml = zip.reader(Charsets.UTF_8).readText()
                            foundText = xml
                                .replace(Regex("</w:p>"), "\n")
                                .replace(Regex("<[^>]+>"), " ")
                                .replace("&amp;", "&").replace("&lt;", "<")
                                .replace("&gt;", ">").replace("&quot;", "\"")
                                .replace(Regex("[ \\t\\x0B\\f\\r]+"), " ")
                                .replace(Regex("\\n\\s+"), "\n")
                                .trim().take(200000)
                            break
                        }
                        zip.closeEntry()
                        entry = zip.nextEntry
                    }
                }
            }
            foundText?.ifBlank { null }
        } catch (_: Exception) { null }
    }

    private fun tryReadSupportedZipBase64(uri: Uri, fileName: String, mimeType: String): String? {
        val isZip = mimeType.contains("zip", ignoreCase = true) ||
            mimeType == "application/x-zip-compressed" ||
            fileName.lowercase().endsWith(".zip")
        if (!isZip) return null
        return try {
            contentResolver.openInputStream(uri)?.use { input ->
                val bytes = input.readBytes()
                if (bytes.size > 50 * 1024 * 1024) null  // 50MB limit
                else android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
            }
        } catch (_: Exception) { null }
    }

    private fun tryReadSupportedImageBase64(uri: Uri, fileName: String, mimeType: String): String? {
        val lowerName = fileName.lowercase(java.util.Locale.ROOT)
        val lowerMime = mimeType.lowercase(java.util.Locale.ROOT)
        val isImage = lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg") ||
            lowerName.endsWith(".png") || lowerMime.startsWith("image/")
        if (!isImage) return null
        return try {
            contentResolver.openInputStream(uri)?.use { input ->
                val bytes = input.readBytes()
                if (bytes.size > 4_000_000) null
                else android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
            }
        } catch (_: Exception) { null }
    }

    override fun onDestroy() {
        tonyMediaPlayer?.release()
        tonyMediaPlayer = null
        tts?.stop()
        tts?.shutdown()
        OnDeviceModel.shutdown()
        super.onDestroy()
    }

    private fun backendProviderForCurrentBrain(): String? {
        return when (currentBrainMode) {
            BrainMode.AUTO -> "auto"
            BrainMode.OPENAI_LIVE -> "openai"
            BrainMode.GEMINI_MOCK -> "gemini"
            BrainMode.CLAUDE_MOCK -> "claude"
            BrainMode.GROQ -> "groq"
            BrainMode.MISTRAL -> "mistral"
            BrainMode.DEEPSEEK -> "deepseek"
            BrainMode.OPENROUTER -> "openrouter"
            BrainMode.COUNCIL_MOCK -> "Council"
            BrainMode.LOCAL_TONY -> null // handled by on-device model
            else -> null
        }
    }

    private fun processOnDevice(message: String) {
        if (!OnDeviceModel.isReady()) {
            // Fall back to Gemini if on-device not ready
            sendMessageToNovaBackend(message, "gemini")
            return
        }
        statusText.text = "Tony thinking (on-device)..."
        ChatHistoryStore.appendMessage(this, "user", message)
        renderChatHistory()

        Thread {
            val systemPrompt = "You are Tony, Matthew's personal AI. British English. Direct, warm, honest. No filler."
            val reply = OnDeviceModel.generate(message, systemPrompt)
                ?: "I couldn't process that on-device. Try switching to a cloud brain."
            runOnUiThread {
                ChatHistoryStore.appendMessage(this, "tony", reply, provider = "on-device")
                statusText.text = "Tony is ready · on-device"
                renderChatHistory()
                refreshChatList()
                speakTony(reply)
            }
        }.start()
    }

    private fun buildFullHistory(): List<NovaApiClient.HistoryItem> {
        return ChatHistoryStore.getMessages(this)
            .mapNotNull { entry ->
                val role = when (entry.role.lowercase()) {
                    "user" -> "user"
                    "assistant", "tony", "bot" -> "assistant"
                    else -> null
                }
                role?.let { NovaApiClient.HistoryItem(it, entry.text) }
            }
            .takeLast(30)
    }

    private fun buildBackendHistoryFor(currentMessage: String): List<NovaApiClient.HistoryItem> {
        val items = buildFullHistory().toMutableList()
        if (items.isNotEmpty()) {
            val last = items.last()
            if (last.role == "user" && last.content.trim() == currentMessage.trim()) {
                items.removeAt(items.lastIndex)
            }
        }
        return items.takeLast(20)
    }

    private fun sendMessageToNovaBackend(currentMessage: String, provider: String) {
        val history = buildBackendHistoryFor(currentMessage)
        val doc = DocumentStore.getDocument(this)
        val locationStr = lastKnownLocation  // raw "lat,lng" sent as dedicated field
        val calendarEvents = readDeviceCalendar()
        val calendarContext = if (calendarEvents.isNotEmpty()) "Matthew's upcoming calendar events (next 7 days):\n$calendarEvents" else null
        val fullContext = calendarContext  // context = calendar only; location sent separately
        statusText.text = "Tony ◆ thinking..."

        if (provider == "Council") {
            // Show a thinking bubble so the screen isn't blank for 20-40s
            val thinkingBubble = TextView(this).apply {
                text = "Tony is deliberating across all brains..."
                textSize = 14f
                setTextColor(0xFF888888.toInt())
                setTypeface(null, android.graphics.Typeface.ITALIC)
                maxWidth = (resources.displayMetrics.widthPixels * 0.76f).toInt()
                setPadding(18, 12, 18, 12)
                background = ContextCompat.getDrawable(this@MainActivity, R.drawable.chat_bubble_tony)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    gravity = Gravity.START
                    setMargins(0, 0, 0, 6)
                }
            }
            runOnUiThread {
                chatContainer.addView(thinkingBubble)
                chatContainer.requestLayout()
                chatScrollView.post {
                    chatScrollView.fullScroll(View.FOCUS_DOWN)
                }
            }

            Thread {
                val result = NovaApiClient.sendCouncil(
                    message = currentMessage,
                    history = history,
                    location = locationStr,
                    context = fullContext,
                    documentText = doc?.text,
                    documentBase64 = doc?.base64,
                    documentName = doc?.name,
                    documentMime = doc?.mimeType
                )
                runOnUiThread {
                    try { chatContainer.removeView(thinkingBubble) } catch (_: Exception) {}
                    val replyText = if (result.reply.isNotBlank()) result.reply
                        else "Tony couldn't reach the server. Check connection or switch brain."

                    val debugJson = result.councilDebug?.let { d ->
                        org.json.JSONObject().apply {
                            put("decidingBrain", d.decidingBrain)
                            put("challenge", d.challenge)
                            val r1 = org.json.JSONObject()
                            d.round1.forEach { (k, v) -> r1.put(k, v) }
                            put("round1", r1)
                            val r2 = org.json.JSONObject()
                            d.round2Refined.forEach { (k, v) -> r2.put(k, v) }
                            put("round2", r2)
                        }.toString()
                    } ?: ""

                    ChatHistoryStore.appendMessage(
                        this, "tony", replyText,
                        provider = provider,
                        debugData = debugJson
                    )
                    statusText.text = if (result.ok) "Tony is ready."
                        else "${currentBrainMode.displayName} couldn't connect. Try again."
                    renderChatHistory()
                    refreshChatList()
                    triggerSummarisationWithHistory(buildFullHistory())
                }
            }.start()
            return
        }

        val streamingBubble = TextView(this).apply {
            text = "▍"
            textSize = 16f
            setTextColor(0xFF111111.toInt())
            maxWidth = (resources.displayMetrics.widthPixels * 0.76f).toInt()
            setPadding(18, 12, 18, 12)
            background = ContextCompat.getDrawable(this@MainActivity, R.drawable.chat_bubble_tony)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.START
                setMargins(0, 0, 0, 6)
            }
        }

        runOnUiThread {
            chatContainer.addView(streamingBubble)
            chatScrollView.post { chatScrollView.fullScroll(View.FOCUS_DOWN) }
        }

        val fullText = StringBuilder()

        Thread {
            NovaApiClient.sendChatStream(
                provider = provider,
                message = currentMessage,
                history = history,
                location = locationStr,
                context = fullContext,
                documentText = doc?.text,
                documentBase64 = doc?.base64,
                documentName = doc?.name,
                documentMime = doc?.mimeType,
                onChunk = { chunk ->
                    fullText.append(chunk)
                    runOnUiThread {
                        markwon.setMarkdown(streamingBubble, fullText.toString() + " ▍")
                        chatScrollView.post { chatScrollView.fullScroll(View.FOCUS_DOWN) }
                    }
                },
                onDone = { ok, completeText, error, resolvedProvider ->
                    val finalReply = completeText.ifBlank {
                        "Tony couldn't reach the server. Check connection or switch brain."
                    }
                    runOnUiThread {
                        chatContainer.removeView(streamingBubble)
                        val providerLabel = if (provider == "auto" && !resolvedProvider.isNullOrBlank()) {
                            "auto → $resolvedProvider"
                        } else {
                            provider
                        }
                        ChatHistoryStore.appendMessage(
                            this, "tony", finalReply,
                            provider = providerLabel,
                            debugData = ""
                        )
                        statusText.text = if (ok) "Tony is ready."
                            else "${currentBrainMode.displayName} couldn't connect. Try again."
                        renderChatHistory()
                        speakTony(finalReply)
                        refreshChatList()
                        triggerSummarisationWithHistory(buildFullHistory())
                    }
                }
            )
        }.start()
    }
}
