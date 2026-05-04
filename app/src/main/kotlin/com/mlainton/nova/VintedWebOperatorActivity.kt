package com.mlainton.nova

import android.os.Bundle
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.LinearLayout
import android.widget.ProgressBar
import androidx.appcompat.app.AppCompatActivity

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

        // Build the layout programmatically — no XML needed for 3B.1.
        // A vertical LinearLayout with a ProgressBar on top, WebView below.
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
        }

        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            max = 100
            progress = 0
            visibility = View.GONE
        }

        webView = WebView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }

        root.addView(progressBar)
        root.addView(webView)
        setContentView(root)

        configureWebView()

        if (savedInstanceState != null) {
            // Activity was recreated (rotation, etc.) — restore WebView state
            webView.restoreState(savedInstanceState)
        } else {
            webView.loadUrl(VINTED_URL)
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

    companion object {
        private const val VINTED_URL = "https://www.vinted.co.uk/"

        // Real Chrome for Android UA string. Matches a recent stable
        // Chrome on a generic Android phone. If Vinted starts blocking,
        // bump the version to whatever's current in real Chrome.
        private const val CHROME_MOBILE_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; SM-S918B) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/130.0.0.0 Mobile Safari/537.36"
    }
}
