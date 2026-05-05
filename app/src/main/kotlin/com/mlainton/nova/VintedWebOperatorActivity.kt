package com.mlainton.nova

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.io.File

/**
 * VintedWebOperatorActivity — Phase 3B.1.
 *
 * Opens vinted.co.uk in a full-screen WebView with persistent
 * cookies/localStorage. Matthew logs in once manually inside this
 * WebView. The session is owned by Nova (separate from the user's
 * Chrome browser cookie jar) and survives Activity restart and
 * process death.
 *
 * NO AUTOMATION YET. NO JS INJECTION. NO POSTING. This Activity is
 * a session-holding shell. Later phases (3B.2+) add JS injection
 * for filling, screenshotting, and the approval gate.
 *
 * Safety:
 *  - User-Agent set to mimic real Chrome on Android, so Vinted
 *    doesn't show stricter wall to WebViews.
 *  - WebView never auto-clicks Upload/Publish. (Enforced in
 *    Phase 3B.4+ via verify_no_publish patterns.)
 *  - No JS bridge from the page back to Nova in this phase.
 */
class VintedWebOperatorActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Build the layout programmatically — no XML needed.
        // 3B.4: switched root from LinearLayout (stacks) to FrameLayout
        // (overlays) so the Inspect button can sit on top of the WebView
        // bottom-right without taking layout space from it. WebView fills
        // the frame; ProgressBar sits at the top edge; Inspect button at
        // the bottom-right corner.
        val root = FrameLayout(this)

        webView = WebView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.TOP
            }
            max = 100
            progress = 0
            visibility = View.GONE
        }

        val inspectButton = Button(this).apply {
            text = "Inspect"
            setOnClickListener { runDomInspector() }
        }
        val inspectButtonParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.END
            val margin = (16 * resources.displayMetrics.density).toInt()
            setMargins(margin, margin, margin, margin)
        }

        root.addView(webView)
        root.addView(progressBar)
        root.addView(inspectButton, inspectButtonParams)
        setContentView(root)

        configureWebView()

        if (savedInstanceState != null) {
            // Activity was recreated (rotation, etc.) — restore WebView state
            webView.restoreState(savedInstanceState)
        } else {
            val launchMode = intent.getStringExtra(EXTRA_LAUNCH_MODE) ?: LAUNCH_MODE_HOME
            val targetUrl = when (launchMode) {
                LAUNCH_MODE_SELL -> VINTED_SELL_URL
                else -> VINTED_URL
            }
            android.util.Log.i(TAG_3B2, "Launching in mode=$launchMode → $targetUrl")
            webView.loadUrl(targetUrl)
        }
    }

    private fun configureWebView() {
        // Persist cookies across Activity restart and process death.
        // CookieManager has app-wide persistent storage by default;
        // we just need to enable it explicitly.
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, true)
        }

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true              // localStorage / sessionStorage
            databaseEnabled = true
            cacheMode = WebSettings.LOAD_DEFAULT
            loadWithOverviewMode = true
            useWideViewPort = true
            // Mimic real Chrome on Android so Vinted treats this as a
            // first-class mobile browser, not a sketchy WebView.
            // The version string here matches modern Chrome for Android.
            // If Vinted adds tighter detection, this is the lever to adjust.
            userAgentString = CHROME_MOBILE_USER_AGENT
            // File access is needed later for photo upload (Phase 3B.6).
            // Off for now — explicit decision to revisit.
            allowFileAccess = false
            allowContentAccess = false
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                url: String?
            ): Boolean {
                // Stay inside the WebView for all vinted.co.uk navigation.
                // Don't hand off to external apps or browsers.
                return false
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                // 3B.2 plumbing test: ask the page for its title via JS,
                // then surface what we got back to Matthew. No field fills,
                // no automation — purely a round-trip proof.
                runJsTitleProbe(url)
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                progressBar.progress = newProgress
                progressBar.visibility = if (newProgress in 1..99) {
                    View.VISIBLE
                } else {
                    View.GONE
                }
            }

            override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
                // 3B.4: surface JS console.log/warn/error for the
                // inspector and any future JS that needs visible logs.
                android.util.Log.d(
                    "VintedOperator-JS",
                    "[${consoleMessage.messageLevel()}] " +
                        "${consoleMessage.sourceId()}:${consoleMessage.lineNumber()} " +
                        "${consoleMessage.message()}"
                )
                return true
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        webView.saveState(outState)
    }

    override fun onBackPressed() {
        // Allow back-navigation within the WebView's history before
        // closing the Activity. Standard WebView pattern.
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        // Flush cookies to persistent storage on Activity destroy,
        // so login state survives even if the OS kills the process.
        CookieManager.getInstance().flush()
        super.onDestroy()
    }

    /**
     * 3B.2 — JS injection round-trip plumbing test.
     *
     * Asks the WebView's JS context for document.title, then surfaces
     * the returned value via Toast. No DOM mutation, no event firing,
     * no field interaction. Just proves the Kotlin <-> JS bridge works
     * inside the Vinted page context.
     *
     * Vinted's CSP and SOP will not block this — evaluateJavascript
     * runs in the page's isolated world but has full DOM read access
     * by design. If document.title comes back as the real Vinted page
     * title (e.g. "Vinted | Buy & sell..."), Phase 3B.4 form filling
     * is unblocked.
     *
     * Limitations of this probe (intentional):
     *  - Reads only, never writes.
     *  - Single primitive (String). No JSON serialisation yet.
     *  - Toast is dev-only output. Phase 3B.5+ will replace with
     *    proper backend reporting.
     */
    private fun runJsTitleProbe(loadedUrl: String?) {
        val js = "document.title"
        webView.evaluateJavascript(js) { rawResult ->
            // evaluateJavascript wraps the result as a JSON-encoded
            // string. For a String result, that means quoted: "..."
            // Strip the quotes for human-readable display.
            val display = rawResult
                ?.removeSurrounding("\"")
                ?.takeIf { it.isNotBlank() }
                ?: "(empty)"

            Toast.makeText(
                this,
                "3B.3 title probe: $display",
                Toast.LENGTH_LONG
            ).show()

            // Also log to Android system log for terminal-side
            // verification via logcat if needed.
            android.util.Log.i(
                TAG_3B2,
                "JS round-trip OK | url=$loadedUrl | title=$display"
            )
        }
    }

    /**
     * 3B.4 — Read-only DOM inspector for the Vinted Sell form.
     *
     * Loads the inspector JS from app assets, runs it inside the WebView's
     * JS context, parses the JSON-encoded return value, persists it to
     * app private storage, and presents a scrollable AlertDialog with a
     * Copy button so Matthew can extract the snapshot for selector design
     * in 3B.5+.
     *
     * No DOM mutation. The inspector JS is verified by Phase 3 safety
     * greps to contain no .click(/.submit(/.value=/dispatchEvent calls.
     */
    private fun runDomInspector() {
        val inspectorJs = try {
            assets.open("vinted_dom_inspector.js").bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            android.util.Log.e(TAG_3B4, "Failed to load inspector asset", e)
            Toast.makeText(this, "Inspector asset load failed: ${e.message}", Toast.LENGTH_LONG).show()
            return
        }

        Toast.makeText(this, "3B.4 inspector running…", Toast.LENGTH_SHORT).show()

        webView.evaluateJavascript(inspectorJs) { rawResult ->
            // evaluateJavascript wraps the return as a JSON string. Our JS
            // returns JSON.stringify(payload), so rawResult is a JSON-encoded
            // JSON string — i.e. JSON.parse it once to unwrap to the actual
            // JSON text, then a second time would parse to an object. We
            // pretty-print the unwrapped JSON text directly.
            val unwrapped = decodeEvaluateResult(rawResult)
            if (unwrapped == null) {
                Toast.makeText(this, "Inspector returned null — check page guard", Toast.LENGTH_LONG).show()
                android.util.Log.w(TAG_3B4, "Inspector raw=$rawResult")
                return@evaluateJavascript
            }

            // Pretty-print for human reading
            val pretty = prettyPrintJson(unwrapped)

            // Save to file
            val savedPath = saveInspectionJson(pretty)

            // Extract field count for Toast (best-effort regex, no full parse)
            val fieldCount = Regex("\"fields\"\\s*:\\s*(\\d+)").find(pretty)?.groupValues?.get(1)
                ?: Regex("\"fields\"\\s*:\\s*\\[([^\\]]*)").find(pretty)?.let {
                    it.groupValues[1].split(",\"").size.toString()
                }
                ?: "?"

            Toast.makeText(this, "3B.4 captured fields=$fieldCount", Toast.LENGTH_LONG).show()
            android.util.Log.i(TAG_3B4, "Inspection saved to $savedPath, ${pretty.length} chars")

            showInspectorDialog(pretty)
        }
    }

    /**
     * Strip the outer JSON-string wrapping that evaluateJavascript adds.
     * Our JS returns JSON.stringify(payload), so the raw result is the
     * JSON string with all internal quotes escaped. We decode the outer
     * JSON-string layer to get the real JSON text.
     */
    private fun decodeEvaluateResult(raw: String?): String? {
        if (raw.isNullOrBlank() || raw == "null") return null
        return try {
            // raw looks like: "{\"phase\":\"3B.4\",...}"
            // After parsing the outer JSON string, we get: {"phase":"3B.4",...}
            org.json.JSONTokener(raw).nextValue() as? String
        } catch (e: Exception) {
            android.util.Log.e(TAG_3B4, "decodeEvaluateResult failed", e)
            null
        }
    }

    private fun prettyPrintJson(jsonText: String): String {
        return try {
            val obj = org.json.JSONObject(jsonText)
            obj.toString(2)
        } catch (e: Exception) {
            android.util.Log.w(TAG_3B4, "prettyPrintJson fallback to raw", e)
            jsonText
        }
    }

    private fun saveInspectionJson(jsonText: String): String {
        val dir = File(filesDir, "vinted_operator")
        if (!dir.exists()) dir.mkdirs()
        val file = File(dir, "last_dom_inspection.json")
        file.writeText(jsonText)
        return file.absolutePath
    }

    private fun showInspectorDialog(jsonText: String) {
        val scroll = ScrollView(this)
        val tv = TextView(this).apply {
            text = jsonText
            setPadding(32, 32, 32, 32)
            setTextIsSelectable(true)
            textSize = 11f
            typeface = android.graphics.Typeface.MONOSPACE
        }
        scroll.addView(tv)

        AlertDialog.Builder(this)
            .setTitle("3B.4 DOM inspector")
            .setView(scroll)
            .setPositiveButton("Copy") { _, _ ->
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("vinted_dom_inspection", jsonText)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(this, "Copied ${jsonText.length} chars", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Close", null)
            .show()
    }

    companion object {
        private const val VINTED_URL = "https://www.vinted.co.uk/"
        private const val VINTED_SELL_URL = "https://www.vinted.co.uk/items/new"

        private const val EXTRA_LAUNCH_MODE = "extra_launch_mode"
        private const val LAUNCH_MODE_HOME = "home"
        private const val LAUNCH_MODE_SELL = "sell"

        // Real Chrome for Android UA string. Matches a recent stable
        // Chrome on a generic Android phone. If Vinted starts blocking,
        // bump the version to whatever's current in real Chrome.
        private const val CHROME_MOBILE_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; SM-S918B) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/130.0.0.0 Mobile Safari/537.36"

        private const val TAG_3B2 = "VintedOperator-3B2"
        private const val TAG_3B4 = "VintedOperator-3B4"
    }
}
