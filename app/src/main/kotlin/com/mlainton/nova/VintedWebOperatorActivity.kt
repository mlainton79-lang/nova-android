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

        val fillTitleButton = Button(this).apply {
            text = "Fill Title"
            setOnClickListener { fillTitleTest() }
        }
        val fillTitleButtonParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.START
            val margin = (16 * resources.displayMetrics.density).toInt()
            setMargins(margin, margin, margin, margin)
        }

        val fillDescButton = Button(this).apply {
            text = "Fill Desc"
            setOnClickListener { fillDescriptionTest() }
        }
        val fillDescButtonParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            val margin = (16 * resources.displayMetrics.density).toInt()
            setMargins(margin, margin, margin, margin)
        }

        // 3B.7.1 — Inspect Price (read-only diagnostic). Bottom-LEFT,
        // stacked above Fill Title with 72dp bottom margin to clear it.
        val inspectPriceButton = Button(this).apply {
            text = "Inspect Price"
            setOnClickListener { inspectPriceTest() }
        }
        val inspectPriceButtonParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.START
            val margin = (16 * resources.displayMetrics.density).toInt()
            val stackedBottom = (72 * resources.displayMetrics.density).toInt()
            setMargins(margin, margin, margin, stackedBottom)
        }

        // 3B.7.2 — Fill Price (write-mode). Bottom-CENTER, stacked above
        // Fill Desc with 72dp bottom margin to clear it.
        val fillPriceButton = Button(this).apply {
            text = "Fill Price"
            setOnClickListener { fillPriceTest() }
        }
        val fillPriceButtonParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            val margin = (16 * resources.displayMetrics.density).toInt()
            val stackedBottom = (72 * resources.displayMetrics.density).toInt()
            setMargins(margin, margin, margin, stackedBottom)
        }

        root.addView(webView)
        root.addView(progressBar)
        root.addView(inspectButton, inspectButtonParams)
        root.addView(fillTitleButton, fillTitleButtonParams)
        root.addView(fillDescButton, fillDescButtonParams)
        root.addView(inspectPriceButton, inspectPriceButtonParams)
        root.addView(fillPriceButton, fillPriceButtonParams)
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
     * greps to contain no click, submit, value-assign, or dispatchEvent calls.
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

    /**
     * 3B.5 — First write-mode fill: Title only.
     *
     * Resolves the Title input from a list of selector candidates that
     * 3B.4.1 confirmed unique on /items/new. Uses the React-aware native
     * setter trick (Object.getOwnPropertyDescriptor on
     * HTMLInputElement.prototype 'value' setter) to write the value past
     * React's controlled-input shadow state, then dispatches input/change/
     * blur events so React picks up the change. After 300ms — long enough
     * for React's next render cycle — we run a separate JS to read the
     * value back. If it survives, React accepted our write.
     *
     * NEVER clicks any button. The write is the only action this method
     * takes against the page.
     *
     * Hardcoded test value: "Hollister hoodie M camo". Plausible to
     * Vinted's content moderation, identifiable to Matthew, no synthetic
     * markers. Manual cleanup via WebView UI after test.
     *
     * Failure modes (all return early without retry):
     *  - selector_not_found: no candidate matched a unique element
     *  - identity_mismatch: resolved element didn't match title field
     *  - not_writable: element was disabled/readonly when checked
     *  - native_setter_missing: prototype descriptor lookup returned null
     *  - page_guard_failed: not on /items/new or title doesn't say sell
     *  - react_wiped_value: value vanished within 300ms
     */
    private fun fillTitleTest() {
        val testValue = "Hollister hoodie M camo"

        val fillJs = """
            (function() {
              'use strict';

              // Page guard — refuse to fill on the wrong page.
              var url = location.href.toLowerCase();
              var pageTitle = (document.title || '').toLowerCase();
              if (url.indexOf('/items/new') === -1 || pageTitle.indexOf('sell') === -1) {
                return JSON.stringify({
                  ok: false,
                  error: 'page_guard_failed',
                  url: location.href,
                  title: document.title
                });
              }

              // Selector candidates from 3B.4.1 inspector, priority order.
              var selectors = [
                '[data-testid="title--input"]',
                'input[name="title"]',
                'input[placeholder="Tell buyers what you\u0027re selling"]',
                '#title',
                'input#title'
              ];

              // Resolver: first selector that uniquely matches a visible,
              // writable input/textarea wins.
              var resolved = null;
              var resolvedSelector = null;
              for (var i = 0; i < selectors.length; i++) {
                var sel = selectors[i];
                var matches;
                try {
                  matches = document.querySelectorAll(sel);
                } catch (e) {
                  continue;
                }
                if (matches.length !== 1) continue;
                var el = matches[0];
                // Visibility check (basic — same shape as inspector).
                var rect = el.getBoundingClientRect();
                if (rect.width === 0 || rect.height === 0) continue;
                // Writability check.
                if (el.disabled === true) continue;
                if (el.readOnly === true) continue;
                // Tag check — must be input or textarea.
                var tag = el.tagName.toLowerCase();
                if (tag !== 'input' && tag !== 'textarea') continue;
                resolved = el;
                resolvedSelector = sel;
                break;
              }

              if (!resolved) {
                return JSON.stringify({ ok: false, error: 'selector_not_found' });
              }

              // Identity guard — must actually be the title field, not a
              // resolver fallback that grabbed the wrong element.
              var dt = resolved.getAttribute('data-testid') || '';
              var name = resolved.getAttribute('name') || '';
              var id = resolved.id || '';
              var isTitle = (dt === 'title--input') || (name === 'title') || (id === 'title');
              if (!isTitle) {
                return JSON.stringify({
                  ok: false,
                  error: 'identity_mismatch',
                  dataTestId: dt,
                  name: name,
                  id: id
                });
              }

              // Native setter trick — bypass React's controlled-input shadow
              // state by calling the prototype's value setter directly.
              var proto = (resolved instanceof HTMLTextAreaElement)
                ? window.HTMLTextAreaElement.prototype
                : window.HTMLInputElement.prototype;
              var descriptor = Object.getOwnPropertyDescriptor(proto, 'value');
              if (!descriptor || !descriptor.set) {
                return JSON.stringify({ ok: false, error: 'native_setter_missing' });
              }

              var testValue = ${jsString(testValue)};
              descriptor.set.call(resolved, testValue);

              // Dispatch the events React listens for.
              resolved.dispatchEvent(new Event('input', { bubbles: true }));
              resolved.dispatchEvent(new Event('change', { bubbles: true }));
              resolved.dispatchEvent(new Event('blur', { bubbles: true }));

              // Read back immediately — first proof the write happened.
              // Verification of survival happens in a separate evaluateJavascript
              // call from Kotlin after 300ms.
              return JSON.stringify({
                ok: true,
                selector: resolvedSelector,
                tag: resolved.tagName.toLowerCase(),
                valueAfterWrite: resolved.value,
                valueLengthAfterWrite: resolved.value.length,
                testValue: testValue
              });
            })();
        """.trimIndent()

        Toast.makeText(this, "3B.5 fill running…", Toast.LENGTH_SHORT).show()

        webView.evaluateJavascript(fillJs) { rawResult ->
            val unwrapped = decodeEvaluateResult(rawResult)
            if (unwrapped == null) {
                Toast.makeText(
                    this,
                    "3B.5 fill: no JSON returned (check logcat)",
                    Toast.LENGTH_LONG
                ).show()
                android.util.Log.w(TAG_3B5, "fill JS returned null. raw=$rawResult")
                return@evaluateJavascript
            }

            // Parse the immediate fill result.
            val fillResult = try {
                org.json.JSONObject(unwrapped)
            } catch (e: Exception) {
                android.util.Log.e(TAG_3B5, "fill result not parseable as JSON", e)
                Toast.makeText(this, "3B.5 fill: bad JSON (check logcat)", Toast.LENGTH_LONG).show()
                return@evaluateJavascript
            }

            val ok = fillResult.optBoolean("ok", false)
            if (!ok) {
                val error = fillResult.optString("error", "unknown_error")
                Toast.makeText(this, "3B.5 fill failed: $error", Toast.LENGTH_LONG).show()
                android.util.Log.w(TAG_3B5, "fill failed: $unwrapped")
                saveTitleFillResult(fillResult, verifyResult = null)
                return@evaluateJavascript
            }

            // Immediate write succeeded. Now schedule the 300ms verify.
            val resolvedSelector = fillResult.optString("selector", "")
            android.util.Log.i(
                TAG_3B5,
                "fill ok | selector=$resolvedSelector | valueLen=${fillResult.optInt("valueLengthAfterWrite", -1)}"
            )

            webView.postDelayed({
                verifyTitleFill(resolvedSelector, testValue, fillResult)
            }, 300L)
        }
    }

    /**
     * 3B.5 verify pass — runs 300ms after fill. Reads the title value back
     * via a separate evaluateJavascript and compares to testValue. If
     * React preserved the write, Toast confirms. Otherwise reports
     * react_wiped_value.
     */
    private fun verifyTitleFill(
        resolvedSelector: String,
        testValue: String,
        fillResult: org.json.JSONObject
    ) {
        if (resolvedSelector.isBlank()) {
            Toast.makeText(this, "3B.5 verify: missing selector", Toast.LENGTH_LONG).show()
            return
        }

        val verifyJs = """
            (function() {
              var sel = ${jsString(resolvedSelector)};
              var el = document.querySelector(sel);
              if (!el) {
                return JSON.stringify({ ok: false, error: 'verify_selector_lost' });
              }
              return JSON.stringify({
                ok: true,
                value: el.value,
                valueLength: el.value.length
              });
            })();
        """.trimIndent()

        webView.evaluateJavascript(verifyJs) { rawVerify ->
            val unwrapped = decodeEvaluateResult(rawVerify)
            val verifyResult = try {
                if (unwrapped == null) null else org.json.JSONObject(unwrapped)
            } catch (e: Exception) {
                android.util.Log.e(TAG_3B5, "verify result not parseable", e)
                null
            }

            if (verifyResult == null) {
                Toast.makeText(this, "3B.5 verify: bad JSON", Toast.LENGTH_LONG).show()
                saveTitleFillResult(fillResult, verifyResult = null)
                return@evaluateJavascript
            }

            val verifyOk = verifyResult.optBoolean("ok", false)
            if (!verifyOk) {
                val verifyError = verifyResult.optString("error", "unknown")
                Toast.makeText(this, "3B.5 verify failed: $verifyError", Toast.LENGTH_LONG).show()
                saveTitleFillResult(fillResult, verifyResult)
                return@evaluateJavascript
            }

            val currentValue = verifyResult.optString("value", "")
            val survived = currentValue == testValue

            if (survived) {
                Toast.makeText(
                    this,
                    "3B.5 PASS: Title filled and verified",
                    Toast.LENGTH_LONG
                ).show()
                android.util.Log.i(TAG_3B5, "verify ok, value preserved: '$currentValue'")
            } else {
                Toast.makeText(
                    this,
                    "3B.5 FAIL: react_wiped_value",
                    Toast.LENGTH_LONG
                ).show()
                android.util.Log.w(
                    TAG_3B5,
                    "react wiped. expected='$testValue', actual='$currentValue'"
                )
            }

            saveTitleFillResult(fillResult, verifyResult)
        }
    }

    /**
     * Persist the combined fill+verify result to filesDir for later
     * inspection. Optional but useful for post-mortem when something
     * goes weird.
     */
    private fun saveTitleFillResult(
        fillResult: org.json.JSONObject,
        verifyResult: org.json.JSONObject?
    ) {
        try {
            val combined = org.json.JSONObject().apply {
                put("phase", "3B.5")
                put("timestamp", java.text.SimpleDateFormat(
                    "yyyy-MM-dd'T'HH:mm:ss'Z'",
                    java.util.Locale.UK
                ).apply {
                    timeZone = java.util.TimeZone.getTimeZone("UTC")
                }.format(java.util.Date()))
                put("fill", fillResult)
                if (verifyResult != null) put("verify", verifyResult)
            }
            val dir = java.io.File(filesDir, "vinted_operator")
            if (!dir.exists()) dir.mkdirs()
            val file = java.io.File(dir, "last_title_fill_result.json")
            file.writeText(combined.toString(2))
            android.util.Log.i(TAG_3B5, "saved fill result: ${file.absolutePath}")
        } catch (e: Exception) {
            android.util.Log.w(TAG_3B5, "saveTitleFillResult failed", e)
        }
    }

    /**
     * 3B.5 helper — escape a Kotlin string for safe interpolation as a JS
     * string literal. Wraps in double quotes, escapes backslashes, double
     * quotes, and newlines. Used for testValue and resolvedSelector
     * interpolation into the fill/verify JS sources.
     */
    private fun jsString(value: String): String {
        val escaped = value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
        return "\"" + escaped + "\""
    }

    /**
     * 3B.6 — Second write-mode fill: Description only.
     *
     * Mirrors 3B.5's fillTitleTest pattern. Resolves the Description
     * textarea from candidates that 3B.4.1 confirmed unique on /items/new.
     * Native setter trick via HTMLTextAreaElement.prototype 'value' setter,
     * dispatch input/change/blur, then two-stage Kotlin-side verification:
     * first at 300ms, fallback at 700ms total if first reports
     * react_wiped_value.
     *
     * NEVER clicks any button. The write is the only action this method
     * takes against the page.
     *
     * Test value: a plausible listing description for the Hollister hoodie
     * referenced by 3B.5's title. Same item, fields constructed in tandem
     * for visual sanity-check during manual review.
     *
     * Failure modes (all return early without retry):
     *  - selector_not_found: no candidate matched a unique element
     *  - identity_mismatch: resolved element didn't match description field
     *  - not_writable: textarea was disabled or readonly when checked
     *  - native_setter_missing: HTMLTextAreaElement.prototype 'value' missing
     *  - page_guard_failed: not on /items/new or title doesn't say sell
     *  - react_wiped_value: value vanished within 700ms (fallback verify)
     *  - verify_selector_lost: textarea disappeared between fill and verify
     *  - bad JSON: result wasn't parseable
     */
    private fun fillDescriptionTest() {
        val testValue = "Hollister hoodie size M, camo print, worn a few times, no marks, smoke-free home. Open to offers."

        val fillJs = """
            (function() {
              'use strict';

              // Page guard — refuse to fill on the wrong page.
              var url = location.href.toLowerCase();
              var pageTitle = (document.title || '').toLowerCase();
              if (url.indexOf('/items/new') === -1 || pageTitle.indexOf('sell') === -1) {
                return JSON.stringify({
                  ok: false,
                  error: 'page_guard_failed',
                  url: location.href,
                  title: document.title
                });
              }

              // Selector candidates from 3B.4.1 inspector, priority order.
              var selectors = [
                '[data-testid="description--input"]',
                'textarea[name="description"]',
                'textarea[placeholder="Tell buyers more about it"]',
                '#description',
                'textarea#description'
              ];

              // Resolver: first selector that uniquely matches a visible,
              // writable textarea wins.
              var resolved = null;
              var resolvedSelector = null;
              for (var i = 0; i < selectors.length; i++) {
                var sel = selectors[i];
                var matches;
                try {
                  matches = document.querySelectorAll(sel);
                } catch (e) {
                  continue;
                }
                if (matches.length !== 1) continue;
                var el = matches[0];
                var rect = el.getBoundingClientRect();
                if (rect.width === 0 || rect.height === 0) continue;
                if (el.disabled === true) continue;
                if (el.readOnly === true) continue;
                // Tag check — must be textarea for description.
                var tag = el.tagName.toLowerCase();
                if (tag !== 'textarea') continue;
                resolved = el;
                resolvedSelector = sel;
                break;
              }

              if (!resolved) {
                return JSON.stringify({ ok: false, error: 'selector_not_found' });
              }

              // Identity guard — must actually be the description field.
              var dt = resolved.getAttribute('data-testid') || '';
              var name = resolved.getAttribute('name') || '';
              var id = resolved.id || '';
              var isDescription = (dt === 'description--input') || (name === 'description') || (id === 'description');
              if (!isDescription) {
                return JSON.stringify({
                  ok: false,
                  error: 'identity_mismatch',
                  dataTestId: dt,
                  name: name,
                  id: id
                });
              }

              // Native setter trick — HTMLTextAreaElement prototype, no
              // input branch needed since description is always textarea.
              var proto = window.HTMLTextAreaElement.prototype;
              var descriptor = Object.getOwnPropertyDescriptor(proto, 'value');
              if (!descriptor || !descriptor.set) {
                return JSON.stringify({ ok: false, error: 'native_setter_missing' });
              }

              var testValue = ${jsString(testValue)};
              descriptor.set.call(resolved, testValue);

              // Dispatch the events React listens for.
              resolved.dispatchEvent(new Event('input', { bubbles: true }));
              resolved.dispatchEvent(new Event('change', { bubbles: true }));
              resolved.dispatchEvent(new Event('blur', { bubbles: true }));

              return JSON.stringify({
                ok: true,
                selector: resolvedSelector,
                tag: resolved.tagName.toLowerCase(),
                valueAfterWrite: resolved.value,
                valueLengthAfterWrite: resolved.value.length,
                testValue: testValue
              });
            })();
        """.trimIndent()

        Toast.makeText(this, "3B.6 fill running…", Toast.LENGTH_SHORT).show()

        webView.evaluateJavascript(fillJs) { rawResult ->
            val unwrapped = decodeEvaluateResult(rawResult)
            if (unwrapped == null) {
                Toast.makeText(
                    this,
                    "3B.6 fill: no JSON returned (check logcat)",
                    Toast.LENGTH_LONG
                ).show()
                android.util.Log.w(TAG_3B6, "fill JS returned null. raw=$rawResult")
                return@evaluateJavascript
            }

            val fillResult = try {
                org.json.JSONObject(unwrapped)
            } catch (e: Exception) {
                android.util.Log.e(TAG_3B6, "fill result not parseable as JSON", e)
                Toast.makeText(this, "3B.6 fill: bad JSON (check logcat)", Toast.LENGTH_LONG).show()
                return@evaluateJavascript
            }

            val ok = fillResult.optBoolean("ok", false)
            if (!ok) {
                val error = fillResult.optString("error", "unknown_error")
                Toast.makeText(this, "3B.6 fill failed: $error", Toast.LENGTH_LONG).show()
                android.util.Log.w(TAG_3B6, "fill failed: $unwrapped")
                saveDescriptionFillResult(fillResult, verifyResult = null, verifyAttempt = 0)
                return@evaluateJavascript
            }

            val resolvedSelector = fillResult.optString("selector", "")
            android.util.Log.i(
                TAG_3B6,
                "fill ok | selector=$resolvedSelector | valueLen=${fillResult.optInt("valueLengthAfterWrite", -1)}"
            )

            // Two-stage verification: first attempt at 300ms.
            webView.postDelayed({
                verifyDescriptionFill(resolvedSelector, testValue, fillResult, attempt = 1)
            }, 300L)
        }
    }

    /**
     * 3B.6 verify pass — runs at attempt 1 (300ms after fill) or attempt 2
     * (400ms after attempt 1, 700ms total). Reads the description value
     * back via separate evaluateJavascript and compares to testValue.
     *
     * If attempt 1 reports react_wiped_value, schedules attempt 2 at +400ms.
     * If attempt 2 also fails, reports react_wiped_value as final.
     * If either attempt passes, no further verification.
     *
     * @param attempt 1 = first verify (300ms), 2 = fallback verify (700ms total)
     */
    private fun verifyDescriptionFill(
        resolvedSelector: String,
        testValue: String,
        fillResult: org.json.JSONObject,
        attempt: Int
    ) {
        if (resolvedSelector.isBlank()) {
            Toast.makeText(this, "3B.6 verify: missing selector", Toast.LENGTH_LONG).show()
            return
        }

        val verifyJs = """
            (function() {
              var sel = ${jsString(resolvedSelector)};
              var el = document.querySelector(sel);
              if (!el) {
                return JSON.stringify({ ok: false, error: 'verify_selector_lost' });
              }
              return JSON.stringify({
                ok: true,
                value: el.value,
                valueLength: el.value.length
              });
            })();
        """.trimIndent()

        webView.evaluateJavascript(verifyJs) { rawVerify ->
            val unwrapped = decodeEvaluateResult(rawVerify)
            val verifyResult = try {
                if (unwrapped == null) null else org.json.JSONObject(unwrapped)
            } catch (e: Exception) {
                android.util.Log.e(TAG_3B6, "verify result not parseable (attempt=$attempt)", e)
                null
            }

            if (verifyResult == null) {
                Toast.makeText(this, "3B.6 verify: bad JSON", Toast.LENGTH_LONG).show()
                saveDescriptionFillResult(fillResult, verifyResult = null, verifyAttempt = attempt)
                return@evaluateJavascript
            }

            val verifyOk = verifyResult.optBoolean("ok", false)
            if (!verifyOk) {
                val verifyError = verifyResult.optString("error", "unknown")
                Toast.makeText(this, "3B.6 verify failed: $verifyError", Toast.LENGTH_LONG).show()
                saveDescriptionFillResult(fillResult, verifyResult, verifyAttempt = attempt)
                return@evaluateJavascript
            }

            val currentValue = verifyResult.optString("value", "")
            val survived = currentValue == testValue

            if (survived) {
                val verifyLabel = if (attempt == 1) "verified" else "verified (fallback)"
                Toast.makeText(
                    this,
                    "3B.6 PASS: Description filled and $verifyLabel",
                    Toast.LENGTH_LONG
                ).show()
                android.util.Log.i(
                    TAG_3B6,
                    "verify ok (attempt=$attempt), value preserved (len=${currentValue.length})"
                )
                saveDescriptionFillResult(fillResult, verifyResult, verifyAttempt = attempt)
                return@evaluateJavascript
            }

            // Value didn't match. If this is attempt 1, schedule attempt 2.
            if (attempt == 1) {
                android.util.Log.w(
                    TAG_3B6,
                    "verify attempt 1 mismatch — scheduling fallback at +400ms"
                )
                webView.postDelayed({
                    verifyDescriptionFill(resolvedSelector, testValue, fillResult, attempt = 2)
                }, 400L)
                return@evaluateJavascript
            }

            // Attempt 2 failed too — final react_wiped_value.
            Toast.makeText(
                this,
                "3B.6 FAIL: react_wiped_value (700ms)",
                Toast.LENGTH_LONG
            ).show()
            android.util.Log.w(
                TAG_3B6,
                "react wiped after 700ms. expected len=${testValue.length}, actual='$currentValue'"
            )
            saveDescriptionFillResult(fillResult, verifyResult, verifyAttempt = attempt)
        }
    }

    /**
     * Persist the combined fill+verify result to filesDir for later
     * inspection. Tracks which verify attempt succeeded (1, 2, or 0 for
     * fail-before-verify).
     */
    private fun saveDescriptionFillResult(
        fillResult: org.json.JSONObject,
        verifyResult: org.json.JSONObject?,
        verifyAttempt: Int
    ) {
        try {
            val combined = org.json.JSONObject().apply {
                put("phase", "3B.6")
                put("timestamp", java.text.SimpleDateFormat(
                    "yyyy-MM-dd'T'HH:mm:ss'Z'",
                    java.util.Locale.UK
                ).apply {
                    timeZone = java.util.TimeZone.getTimeZone("UTC")
                }.format(java.util.Date()))
                put("verifyAttempt", verifyAttempt)
                put("fill", fillResult)
                if (verifyResult != null) put("verify", verifyResult)
            }
            val dir = java.io.File(filesDir, "vinted_operator")
            if (!dir.exists()) dir.mkdirs()
            val file = java.io.File(dir, "last_description_fill_result.json")
            file.writeText(combined.toString(2))
            android.util.Log.i(TAG_3B6, "saved fill result: ${file.absolutePath}")
        } catch (e: Exception) {
            android.util.Log.w(TAG_3B6, "saveDescriptionFillResult failed", e)
        }
    }

    /**
     * 3B.7.1 — Price mask inspector. Read-only diagnostic.
     *
     * Resolves the price input on /items/new and captures structural
     * intel about how the field is wired (React props, value tracker,
     * native descriptors, parent wrapper, ARIA state) without touching
     * a single byte of state. Output feeds 3B.7.2's mechanism choice
     * for the eventual fill: which masking library is in play (e.g.
     * react-number-format), whether the input setter is intercepted at
     * the element level, and what events the mask listens for.
     * 3B.7.1.5: also dumps element-own and prototype value setter source.
     *
     * NEVER writes to the page. No setter call, no event dispatch, no
     * value assignment. Pure inspection. JSON returned to Kotlin,
     * persisted to filesDir, and displayed in a copyable AlertDialog.
     */
    private fun inspectPriceTest() {
        val inspectJs = """
            (function() {
              'use strict';

              // Page guard — refuse to inspect on the wrong page.
              var url = location.href.toLowerCase();
              var pageTitle = (document.title || '').toLowerCase();
              if (url.indexOf('/items/new') === -1 || pageTitle.indexOf('sell') === -1) {
                return JSON.stringify({
                  ok: false,
                  error: 'page_guard_failed',
                  url: location.href,
                  title: document.title
                });
              }

              // Selector candidates — same set 3B.7 had.
              var selectors = [
                '[data-testid="price-input--input"]',
                'input[name="price"]',
                'input[placeholder="£0.00"]',
                '#price',
                'input#price'
              ];

              var resolved = null;
              var resolvedSelector = null;
              for (var i = 0; i < selectors.length; i++) {
                var sel = selectors[i];
                var matches;
                try {
                  matches = document.querySelectorAll(sel);
                } catch (e) {
                  continue;
                }
                if (matches.length !== 1) continue;
                var el = matches[0];
                var rect0 = el.getBoundingClientRect();
                if (rect0.width === 0 || rect0.height === 0) continue;
                var tag0 = el.tagName.toLowerCase();
                if (tag0 !== 'input') continue;
                resolved = el;
                resolvedSelector = sel;
                break;
              }

              if (!resolved) {
                return JSON.stringify({ ok: false, error: 'selector_not_found' });
              }

              // Identity guard — must actually be the price field.
              var dt = resolved.getAttribute('data-testid') || '';
              var nameAttr = resolved.getAttribute('name') || '';
              var idAttr = resolved.id || '';
              var isPrice = (dt === 'price-input--input') || (nameAttr === 'price') || (idAttr === 'price');
              if (!isPrice) {
                return JSON.stringify({
                  ok: false,
                  error: 'identity_mismatch',
                  dataTestId: dt,
                  name: nameAttr,
                  id: idAttr
                });
              }

              // Element attributes — strict read.
              var attributes = {
                tag: resolved.tagName.toLowerCase(),
                type: resolved.getAttribute('type') || '',
                name: nameAttr,
                id: idAttr,
                dataTestId: dt,
                placeholder: resolved.getAttribute('placeholder') || '',
                inputmode: resolved.getAttribute('inputmode') || '',
                pattern: resolved.getAttribute('pattern') || '',
                autocomplete: resolved.getAttribute('autocomplete') || '',
                maxlength: resolved.getAttribute('maxlength') || '',
                ariaLabel: resolved.getAttribute('aria-label') || '',
                ariaInvalid: resolved.getAttribute('aria-invalid') || '',
                ariaDescribedBy: resolved.getAttribute('aria-describedby') || '',
                role: resolved.getAttribute('role') || '',
                readonly: resolved.readOnly === true,
                disabled: resolved.disabled === true,
                required: resolved.required === true
              };

              var r = resolved.getBoundingClientRect();
              var boundingRect = { x: r.left, y: r.top, w: r.width, h: r.height };

              var currentValue = resolved.value;
              var currentValueLength = currentValue.length;

              // Two levels of parent wrapper info — field wrapper plus container.
              var p1 = resolved.parentNode;
              var p2 = p1 ? p1.parentNode : null;
              function parentInfo(p) {
                if (!p) return null;
                var dtid = (p.getAttribute && p.nodeType === 1) ? (p.getAttribute('data-testid') || '') : '';
                var cls = (typeof p.className === 'string') ? p.className : '';
                return {
                  tag: p.tagName ? p.tagName.toLowerCase() : null,
                  dataTestId: dtid,
                  className: cls
                };
              }
              var parents = {
                parent1: parentInfo(p1),
                parent2: parentInfo(p2)
              };

              // React detection — keys only, never serialise the values themselves.
              var reactKeys = Object.keys(resolved).filter(function(k) {
                return k.indexOf('__react') === 0;
              });
              var reactKeyTypes = {};
              for (var ri = 0; ri < reactKeys.length; ri++) {
                var rk = reactKeys[ri];
                reactKeyTypes[rk] = typeof resolved[rk];
              }

              // __reactProps prop NAMES only — never the values.
              var reactPropNames = null;
              var reactPropsKey = null;
              for (var rj = 0; rj < reactKeys.length; rj++) {
                if (reactKeys[rj].indexOf('__reactProps') === 0) {
                  reactPropsKey = reactKeys[rj];
                  try {
                    reactPropNames = Object.keys(resolved[reactKeys[rj]]);
                  } catch (e) {
                    reactPropNames = null;
                  }
                  break;
                }
              }

              // Value tracker detection — getValue() is a pure read, safe.
              var hasValueTracker = (typeof resolved._valueTracker === 'object' && resolved._valueTracker !== null);
              var trackerGetValueType = null;
              var trackerValue = null;
              if (hasValueTracker) {
                trackerGetValueType = typeof resolved._valueTracker.getValue;
                if (trackerGetValueType === 'function') {
                  try {
                    trackerValue = resolved._valueTracker.getValue();
                  } catch (e) {
                    trackerValue = null;
                  }
                }
              }

              // Input descriptor inspection — metadata only. Bracket-notation
              // lookups so this stays a pure read with no setter invocation.
              function descInfo(d) {
                if (!d) return null;
                return {
                  hasGet: typeof d['get'] === 'function',
                  hasSet: typeof d['set'] === 'function',
                  configurable: d.configurable === true,
                  enumerable: d.enumerable === true
                };
              }
              var elementOwnDescriptorRaw = Object.getOwnPropertyDescriptor(resolved, 'value');
              var prototypeDescriptorRaw = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value');
              var elementOwnDescriptor = descInfo(elementOwnDescriptorRaw);
              var prototypeDescriptor = descInfo(prototypeDescriptorRaw);

              // 3B.7.1.5 — capture the setter source strings so 3B.7.2 can
              // tell React's native-delegating stub from a synthetic mask
              // interceptor. Reflection only — the setter is never invoked.
              var elementOwnSetterSource = null;
              if (elementOwnDescriptorRaw && typeof elementOwnDescriptorRaw['set'] === 'function') {
                try {
                  var s1 = Function.prototype.toString.call(elementOwnDescriptorRaw['set']);
                  elementOwnSetterSource = (s1.length > 500) ? s1.substring(0, 500) : s1;
                } catch (e) {
                  elementOwnSetterSource = null;
                }
              }
              var prototypeSetterSource = null;
              if (prototypeDescriptorRaw && typeof prototypeDescriptorRaw['set'] === 'function') {
                try {
                  var s2 = Function.prototype.toString.call(prototypeDescriptorRaw['set']);
                  prototypeSetterSource = (s2.length > 500) ? s2.substring(0, 500) : s2;
                } catch (e) {
                  prototypeSetterSource = null;
                }
              }
              var elementOwnSetterIsNative = (typeof elementOwnSetterSource === 'string' &&
                                              elementOwnSetterSource.indexOf('[native code]') !== -1);

              // DOM-level event listener detection — DevTools-only API,
              // unavailable in production WebView. Report the support flag.
              var listenersDetectionSupported = (typeof resolved.getEventListeners === 'function');

              return JSON.stringify({
                ok: true,
                phase: '3B.7.1',
                selector: resolvedSelector,
                attributes: attributes,
                boundingRect: boundingRect,
                currentValue: currentValue,
                currentValueLength: currentValueLength,
                parents: parents,
                reactKeys: reactKeys,
                reactKeyTypes: reactKeyTypes,
                reactPropsKey: reactPropsKey,
                reactPropNames: reactPropNames,
                valueTracker: {
                  hasValueTracker: hasValueTracker,
                  getValueType: trackerGetValueType,
                  trackerValue: trackerValue
                },
                elementOwnDescriptor: elementOwnDescriptor,
                prototypeDescriptor: prototypeDescriptor,
                elementOwnSetterSource: elementOwnSetterSource,
                prototypeSetterSource: prototypeSetterSource,
                elementOwnSetterIsNative: elementOwnSetterIsNative,
                listenersDetectionSupported: listenersDetectionSupported
              });
            })();
        """.trimIndent()

        Toast.makeText(this, "3B.7.1 inspector running…", Toast.LENGTH_SHORT).show()

        webView.evaluateJavascript(inspectJs) { rawResult ->
            val unwrapped = decodeEvaluateResult(rawResult)
            if (unwrapped == null) {
                Toast.makeText(
                    this,
                    "3B.7.1 inspect: no JSON returned (check logcat)",
                    Toast.LENGTH_LONG
                ).show()
                android.util.Log.w(TAG_3B71, "inspect JS returned null. raw=$rawResult")
                return@evaluateJavascript
            }

            val pretty = prettyPrintJson(unwrapped)
            showAndSavePriceMaskResult(pretty)

            val parsed = try {
                org.json.JSONObject(unwrapped)
            } catch (e: Exception) {
                null
            }
            if (parsed != null && parsed.optBoolean("ok", false)) {
                android.util.Log.i(
                    TAG_3B71,
                    "inspect ok | selector=${parsed.optString("selector")} | chars=${pretty.length}"
                )
                Toast.makeText(
                    this,
                    "3B.7.1 captured price mask intel",
                    Toast.LENGTH_LONG
                ).show()
            } else {
                val err = parsed?.optString("error", "unknown") ?: "bad_json"
                Toast.makeText(this, "3B.7.1 failed: $err", Toast.LENGTH_LONG).show()
                android.util.Log.w(TAG_3B71, "inspect failed: $unwrapped")
            }
        }
    }

    /**
     * 3B.7.1 helper — persist the price-mask inspect JSON to filesDir
     * and surface it in a scrollable, copyable AlertDialog. Same UX as
     * 3B.4's showInspectorDialog but distinct title and storage path so
     * the price-mask snapshot doesn't overwrite the form-wide one.
     */
    private fun showAndSavePriceMaskResult(jsonText: String) {
        try {
            val dir = File(filesDir, "vinted_operator")
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, "last_price_mask_inspect.json")
            file.writeText(jsonText)
            android.util.Log.i(TAG_3B71, "saved price mask inspect: ${file.absolutePath}")
        } catch (e: Exception) {
            android.util.Log.w(TAG_3B71, "save price mask inspect failed", e)
        }

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
            .setTitle("3B.7.1 Price mask inspector")
            .setView(scroll)
            .setPositiveButton("Copy") { _, _ ->
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("vinted_price_mask_inspect", jsonText)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(this, "Copied ${jsonText.length} chars", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Close", null)
            .show()
    }

    /**
     * 3B.7.2 — Third write-mode fill: Price only.
     *
     * 3B.7 attempts 1 (raw "25") and 2 ("£25.00") both failed the verify
     * with £NaN / price_unparseable because they used the prototype value
     * setter — which writes the DOM value past React's tracker but never
     * synchronises React's internal state. The price handler then read
     * an empty internal value, parsed it, and produced NaN.
     *
     * 3B.7.1.5's inspector confirmed the price input has an ELEMENT-OWN
     * 'value' descriptor with hasSet:true, source not "[native code]" —
     * i.e. React's tracker wrapper. Calling that setter routes through
     * the tracker so React's internal state stays in sync. This brick
     * uses the element-own setter as the primary path, with prototype
     * fallback only if the element-own descriptor disappears.
     *
     * Test value: "£25.00", expectedPence 2500. Verify is pence-normalised:
     * strip £/commas/whitespace, parseFloat, NaN-check, round to pence.
     * Two-stage verify (300ms then +400ms = 700ms total) mirrors 3B.6.
     *
     * NEVER clicks any button. The setter call + event dispatch is the
     * only action this method takes against the page.
     *
     * Failure modes (all return early without retry):
     *  - selector_not_found / identity_mismatch / page_guard_failed
     *  - native_setter_missing: neither own nor prototype descriptor has a setter
     *  - verify_selector_lost: input vanished between fill and verify
     *  - price_unparseable: value parses to NaN (the 3B.7 failure shape)
     *  - price_mismatch: parsed pence != expectedPence
     */
    private fun fillPriceTest() {
        val testValue = "£25.00"
        val expectedPence = 2500

        val fillJs = """
            (function() {
              'use strict';

              // Page guard — refuse to fill on the wrong page.
              var url = location.href.toLowerCase();
              var pageTitle = (document.title || '').toLowerCase();
              if (url.indexOf('/items/new') === -1 || pageTitle.indexOf('sell') === -1) {
                return JSON.stringify({
                  ok: false,
                  error: 'page_guard_failed',
                  url: location.href,
                  title: document.title
                });
              }

              // Selector candidates from 3B.7.1 / 3B.7.1.5 inspector.
              var selectors = [
                '[data-testid="price-input--input"]',
                'input[name="price"]',
                'input[placeholder="£0.00"]',
                '#price',
                'input#price'
              ];

              var resolved = null;
              var resolvedSelector = null;
              for (var i = 0; i < selectors.length; i++) {
                var sel = selectors[i];
                var matches;
                try {
                  matches = document.querySelectorAll(sel);
                } catch (e) {
                  continue;
                }
                if (matches.length !== 1) continue;
                var el = matches[0];
                var rect = el.getBoundingClientRect();
                if (rect.width === 0 || rect.height === 0) continue;
                if (el.disabled === true) continue;
                if (el.readOnly === true) continue;
                // Tag check — must be input for price.
                var tag = el.tagName.toLowerCase();
                if (tag !== 'input') continue;
                resolved = el;
                resolvedSelector = sel;
                break;
              }

              if (!resolved) {
                return JSON.stringify({ ok: false, error: 'selector_not_found' });
              }

              // Identity guard — must actually be the price field.
              var dt = resolved.getAttribute('data-testid') || '';
              var nameAttr = resolved.getAttribute('name') || '';
              var idAttr = resolved.id || '';
              var isPrice = (dt === 'price-input--input') || (nameAttr === 'price') || (idAttr === 'price');
              if (!isPrice) {
                return JSON.stringify({
                  ok: false,
                  error: 'identity_mismatch',
                  dataTestId: dt,
                  name: nameAttr,
                  id: idAttr
                });
              }

              // 3B.7.1.5 discovery: the element-own value setter is React's
              // tracker wrapper. Calling it keeps React's internal state in
              // sync. Use it as primary; prototype fallback only.
              var ownDescriptor = Object.getOwnPropertyDescriptor(resolved, 'value');
              var protoDescriptor = Object.getOwnPropertyDescriptor(
                HTMLInputElement.prototype, 'value'
              );

              var testValue = ${jsString(testValue)};
              var setterUsed = null;
              if (ownDescriptor && typeof ownDescriptor.set === 'function') {
                ownDescriptor.set.call(resolved, testValue);
                setterUsed = 'element_own';
              } else if (protoDescriptor && typeof protoDescriptor.set === 'function') {
                protoDescriptor.set.call(resolved, testValue);
                setterUsed = 'prototype_fallback';
              } else {
                return JSON.stringify({ ok: false, error: 'native_setter_missing' });
              }

              // Dispatch the events React listens for.
              resolved.dispatchEvent(new Event('input', { bubbles: true }));
              resolved.dispatchEvent(new Event('change', { bubbles: true }));
              resolved.dispatchEvent(new Event('blur', { bubbles: true }));

              return JSON.stringify({
                ok: true,
                selector: resolvedSelector,
                tag: resolved.tagName.toLowerCase(),
                setterUsed: setterUsed,
                valueAfterWrite: resolved.value,
                valueLengthAfterWrite: resolved.value.length,
                testValue: testValue
              });
            })();
        """.trimIndent()

        Toast.makeText(this, "3B.7.2 fill running…", Toast.LENGTH_SHORT).show()

        webView.evaluateJavascript(fillJs) { rawResult ->
            val unwrapped = decodeEvaluateResult(rawResult)
            if (unwrapped == null) {
                Toast.makeText(
                    this,
                    "3B.7.2 fill: no JSON returned (check logcat)",
                    Toast.LENGTH_LONG
                ).show()
                android.util.Log.w(TAG_3B7, "fill JS returned null. raw=$rawResult")
                return@evaluateJavascript
            }

            val fillResult = try {
                org.json.JSONObject(unwrapped)
            } catch (e: Exception) {
                android.util.Log.e(TAG_3B7, "fill result not parseable as JSON", e)
                Toast.makeText(this, "3B.7.2 fill: bad JSON (check logcat)", Toast.LENGTH_LONG).show()
                return@evaluateJavascript
            }

            val ok = fillResult.optBoolean("ok", false)
            if (!ok) {
                val error = fillResult.optString("error", "unknown_error")
                Toast.makeText(this, "3B.7.2 fill failed: $error", Toast.LENGTH_LONG).show()
                android.util.Log.w(TAG_3B7, "fill failed: $unwrapped")
                savePriceFillResult(
                    fillResult,
                    verifyResult = null,
                    verifyAttempt = 0,
                    expectedPence = expectedPence
                )
                return@evaluateJavascript
            }

            val resolvedSelector = fillResult.optString("selector", "")
            val setterUsed = fillResult.optString("setterUsed", "")
            android.util.Log.i(
                TAG_3B7,
                "fill ok | selector=$resolvedSelector | setterUsed=$setterUsed | valueLen=${fillResult.optInt("valueLengthAfterWrite", -1)}"
            )

            // Two-stage verification: first attempt at 300ms.
            webView.postDelayed({
                verifyPriceFill(
                    resolvedSelector,
                    testValue,
                    expectedPence,
                    fillResult,
                    attempt = 1
                )
            }, 300L)
        }
    }

    /**
     * 3B.7.2 verify pass — runs at attempt 1 (300ms after fill) or attempt
     * 2 (400ms after attempt 1, 700ms total). Reads the price value back,
     * strips £/commas/whitespace, parseFloat, rounds to pence, compares
     * to expectedPence.
     *
     * NaN at attempt 1 → schedule attempt 2 at +400ms.
     * NaN at attempt 2 → final price_unparseable.
     * Mismatch at attempt 1 → schedule attempt 2 at +400ms.
     * Mismatch at attempt 2 → final price_mismatch.
     */
    private fun verifyPriceFill(
        resolvedSelector: String,
        testValue: String,
        expectedPence: Int,
        fillResult: org.json.JSONObject,
        attempt: Int
    ) {
        if (resolvedSelector.isBlank()) {
            Toast.makeText(this, "3B.7.2 verify: missing selector", Toast.LENGTH_LONG).show()
            return
        }

        val verifyJs = """
            (function() {
              var sel = ${jsString(resolvedSelector)};
              var el = document.querySelector(sel);
              if (!el) {
                return JSON.stringify({ ok: false, error: 'verify_selector_lost' });
              }
              var raw = el.value || '';
              // Strip £, commas, and whitespace; parseFloat; round to pence.
              var stripped = raw.replace(/[£,\s]/g, '');
              var asFloat = parseFloat(stripped);
              var isNan = isNaN(asFloat);
              var actualPence = isNan ? null : Math.round(asFloat * 100);
              return JSON.stringify({
                ok: true,
                value: raw,
                valueLength: raw.length,
                stripped: stripped,
                asFloat: isNan ? null : asFloat,
                isNan: isNan,
                actualPence: actualPence
              });
            })();
        """.trimIndent()

        webView.evaluateJavascript(verifyJs) { rawVerify ->
            val unwrapped = decodeEvaluateResult(rawVerify)
            val verifyResult = try {
                if (unwrapped == null) null else org.json.JSONObject(unwrapped)
            } catch (e: Exception) {
                android.util.Log.e(TAG_3B7, "verify result not parseable (attempt=$attempt)", e)
                null
            }

            if (verifyResult == null) {
                Toast.makeText(this, "3B.7.2 verify: bad JSON", Toast.LENGTH_LONG).show()
                savePriceFillResult(
                    fillResult,
                    verifyResult = null,
                    verifyAttempt = attempt,
                    expectedPence = expectedPence
                )
                return@evaluateJavascript
            }

            val verifyOk = verifyResult.optBoolean("ok", false)
            if (!verifyOk) {
                val verifyError = verifyResult.optString("error", "unknown")
                Toast.makeText(this, "3B.7.2 verify failed: $verifyError", Toast.LENGTH_LONG).show()
                savePriceFillResult(
                    fillResult,
                    verifyResult,
                    verifyAttempt = attempt,
                    expectedPence = expectedPence
                )
                return@evaluateJavascript
            }

            val isNan = verifyResult.optBoolean("isNan", false)
            if (isNan) {
                if (attempt == 1) {
                    android.util.Log.w(
                        TAG_3B7,
                        "verify attempt 1 NaN — scheduling fallback at +400ms"
                    )
                    webView.postDelayed({
                        verifyPriceFill(
                            resolvedSelector,
                            testValue,
                            expectedPence,
                            fillResult,
                            attempt = 2
                        )
                    }, 400L)
                    return@evaluateJavascript
                }
                Toast.makeText(
                    this,
                    "3B.7.2 FAIL: price_unparseable (700ms)",
                    Toast.LENGTH_LONG
                ).show()
                android.util.Log.w(
                    TAG_3B7,
                    "price_unparseable after 700ms. raw='${verifyResult.optString("value", "")}'"
                )
                savePriceFillResult(
                    fillResult,
                    verifyResult,
                    verifyAttempt = attempt,
                    expectedPence = expectedPence
                )
                return@evaluateJavascript
            }

            val actualPence = verifyResult.optInt("actualPence", -1)
            if (actualPence == expectedPence) {
                val verifyLabel = if (attempt == 1) "verified" else "verified (fallback)"
                Toast.makeText(
                    this,
                    "3B.7.2 PASS: Price filled and $verifyLabel (${actualPence}p)",
                    Toast.LENGTH_LONG
                ).show()
                android.util.Log.i(
                    TAG_3B7,
                    "verify ok (attempt=$attempt), pence=$actualPence"
                )
                savePriceFillResult(
                    fillResult,
                    verifyResult,
                    verifyAttempt = attempt,
                    expectedPence = expectedPence
                )
                return@evaluateJavascript
            }

            // Pence mismatch.
            if (attempt == 1) {
                android.util.Log.w(
                    TAG_3B7,
                    "verify attempt 1 mismatch (expected=$expectedPence, actual=$actualPence) — scheduling fallback at +400ms"
                )
                webView.postDelayed({
                    verifyPriceFill(
                        resolvedSelector,
                        testValue,
                        expectedPence,
                        fillResult,
                        attempt = 2
                    )
                }, 400L)
                return@evaluateJavascript
            }

            Toast.makeText(
                this,
                "3B.7.2 FAIL: price_mismatch (expected=$expectedPence, actual=$actualPence)",
                Toast.LENGTH_LONG
            ).show()
            android.util.Log.w(
                TAG_3B7,
                "price_mismatch after 700ms. expected=$expectedPence, actual=$actualPence"
            )
            savePriceFillResult(
                fillResult,
                verifyResult,
                verifyAttempt = attempt,
                expectedPence = expectedPence
            )
        }
    }

    /**
     * Persist the combined fill+verify result to filesDir. Records the
     * setter that ran (element_own vs prototype_fallback) and which
     * verify attempt closed the result, plus the expected pence baseline.
     */
    private fun savePriceFillResult(
        fillResult: org.json.JSONObject,
        verifyResult: org.json.JSONObject?,
        verifyAttempt: Int,
        expectedPence: Int
    ) {
        try {
            val combined = org.json.JSONObject().apply {
                put("phase", "3B.7.2")
                put("timestamp", java.text.SimpleDateFormat(
                    "yyyy-MM-dd'T'HH:mm:ss'Z'",
                    java.util.Locale.UK
                ).apply {
                    timeZone = java.util.TimeZone.getTimeZone("UTC")
                }.format(java.util.Date()))
                put("expectedPence", expectedPence)
                put("verifyAttempt", verifyAttempt)
                put("setterUsed", fillResult.optString("setterUsed", "unknown"))
                put("fill", fillResult)
                if (verifyResult != null) put("verify", verifyResult)
            }
            val dir = java.io.File(filesDir, "vinted_operator")
            if (!dir.exists()) dir.mkdirs()
            val file = java.io.File(dir, "last_price_fill_result.json")
            file.writeText(combined.toString(2))
            android.util.Log.i(TAG_3B7, "saved fill result: ${file.absolutePath}")
        } catch (e: Exception) {
            android.util.Log.w(TAG_3B7, "savePriceFillResult failed", e)
        }
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
        private const val TAG_3B5 = "VintedOperator-3B5"
        private const val TAG_3B6 = "VintedOperator-3B6"
        private const val TAG_3B71 = "VintedOperator-3B71"
        private const val TAG_3B7 = "VintedOperator-3B7"
    }
}
