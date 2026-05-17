package com.mlainton.nova

import android.app.Application
import io.sentry.SentryOptions
import io.sentry.android.core.SentryAndroid

/**
 * NovaApplication — the app-level entry point.
 *
 * Runs once at process start, before any Activity. Its only job right now is
 * to initialise Sentry crash reporting (R1.4 / brick A3). Future app-wide
 * setup (feature flags, etc.) can also live here.
 *
 * ─────────────────────────────────────────────────────────────────────────
 * MEMORIAL / PRIVACY SAFETY — read before changing the Sentry config below.
 * ─────────────────────────────────────────────────────────────────────────
 * Nova handles grief, family, banking and other deeply personal content.
 * Sentry is wired for ONE purpose: telling Matthew when the app crashed and
 * where. It must NEVER become a channel that ships that personal content to
 * an external dashboard.
 *
 * The config below is deliberately locked down:
 *   - isSendDefaultPii = false  — no personally identifiable info, ever.
 *   - beforeBreadcrumb returns null — every breadcrumb is dropped. Breadcrumbs
 *     would otherwise capture navigation, network calls and log lines, which
 *     can contain Gmail subjects, prompt text, OAuth state, etc.
 *   - beforeSend strips the user object and the server-name field — defence
 *     in depth on top of the above.
 *   - Only the crash itself ships: exception type, message, stack trace,
 *     app version, OS version, device model. Nothing else.
 *
 * Do NOT add: setUser(...), auto user-interaction tracing, attachScreenshot,
 * attachViewHierarchy, session replay, or capture of request/response bodies.
 * If richer context is ever genuinely needed, it gets its own reviewed brick.
 * (R1.4 brick A3.)
 * ─────────────────────────────────────────────────────────────────────────
 */
class NovaApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        initSentry()
    }

    private fun initSentry() {
        val dsn = BuildConfig.SENTRY_DSN
        // No DSN configured -> Sentry stays off, app runs normally.
        if (dsn.isBlank()) {
            return
        }

        SentryAndroid.init(this) { options ->
            options.dsn = dsn

            // No personally identifiable information, ever.
            options.isSendDefaultPii = false

            // Keep the crash stack trace — that is the whole point.
            options.isAttachStacktrace = true

            // No screenshots, no view hierarchy — these can capture
            // on-screen personal content (emails, chats, photos).
            options.isAttachScreenshot = false
            options.isAttachViewHierarchy = false

            // Drop every breadcrumb. Breadcrumbs record navigation,
            // network requests and log output — all of which can carry
            // personal content. Bare crash reports only.
            options.beforeBreadcrumb =
                SentryOptions.BeforeBreadcrumbCallback { _, _ -> null }

            // Defence in depth: strip identity + machine-name fields from
            // every event before it leaves the device.
            options.beforeSend =
                SentryOptions.BeforeSendCallback { event, _ ->
                    event.user = null
                    event.serverName = null
                    event
                }

            // Debug log output off in normal use.
            options.isDebug = false
        }
    }
}
