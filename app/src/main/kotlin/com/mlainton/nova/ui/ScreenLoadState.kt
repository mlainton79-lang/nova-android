package com.mlainton.nova.ui

/**
 * The four observable states of a single asynchronous data source in an
 * engine screen. Used per-source — a screen with two HTTP calls holds two
 * `ScreenLoadState` values so partial success (one source loaded, the other
 * still loading or failing) renders naturally without all-or-nothing logic.
 *
 * `Refreshing<T>` carries the previously-loaded data so a refresh in flight
 * doesn't blank useful diagnostics under a spinner — operational screens
 * need "show old data while we re-fetch."
 *
 * `Error<T>` carries `previousData` for the same reason: an error after a
 * successful load can still surface the last-good payload alongside the
 * failure indicator.
 *
 * `refreshedAt` is epoch milliseconds (`System.currentTimeMillis()`). Null
 * means "never refreshed in this session."
 */
sealed interface ScreenLoadState<out T> {

    /** First load in flight; no data to render yet. */
    data object Loading : ScreenLoadState<Nothing>

    /** Data available and stable. */
    data class Loaded<out T>(
        val data: T,
        val refreshedAt: Long? = null,
    ) : ScreenLoadState<T>

    /** Data available, a refresh is in flight in the background. */
    data class Refreshing<out T>(
        val data: T,
        val refreshedAt: Long? = null,
    ) : ScreenLoadState<T>

    /**
     * Last load attempt failed. `previousData` carries the most recent
     * successful payload if any — sections can render it alongside the
     * error indicator instead of blanking.
     */
    data class Error<out T>(
        val message: String,
        val previousData: T? = null,
        val refreshedAt: Long? = null,
    ) : ScreenLoadState<T>
}
