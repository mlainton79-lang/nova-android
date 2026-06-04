package com.mlainton.nova

/**
 * Result envelope for HTTP calls to the Nova backend.
 *
 * Wraps transport/parsing failures OUTSIDE the domain payload — the domain
 * payload (e.g. TonyStatusResult) carries only fields the backend actually
 * sent. Transport errors live here in `Failure`.
 *
 * Discipline:
 * - Methods that return `ApiCall<T>` MUST NOT throw. Every failure path
 *   wraps into `Failure(message, cause, statusCode)`.
 * - Callers (ViewModels) map `ApiCall<T>` → `ScreenLoadState<T>` at the
 *   use site; the section composables never see ApiCall directly.
 */
sealed interface ApiCall<out T> {

    data class Success<T>(val body: T) : ApiCall<T>

    data class Failure(
        val message: String,
        val cause: Throwable? = null,
        val statusCode: Int? = null,
    ) : ApiCall<Nothing>
}
