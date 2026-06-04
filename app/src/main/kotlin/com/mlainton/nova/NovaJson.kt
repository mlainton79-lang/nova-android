package com.mlainton.nova

import kotlinx.serialization.json.Json

/**
 * Shared JSON configuration for Nova backend payloads.
 *
 * `strict` deliberately fails on contract drift:
 * - `ignoreUnknownKeys = false` — a backend that adds a new field without
 *   matching a model surfaces a SerializationException at parse time. For
 *   internal engine/admin endpoints this is the right posture; we control
 *   both ends.
 * - `explicitNulls = false` — when writing, fields with null values are
 *   omitted. Read-side: missing keys for nullable-with-default fields are
 *   treated as null. Required-non-default fields still throw if missing.
 * - `isLenient = false` — JSON syntax must be valid; no quote-less keys,
 *   no trailing commas. The backend emits standard JSON; we don't accept
 *   anything else.
 *
 * If backend evolution ever requires additive-tolerant clients (e.g. a
 * shipped APK has to keep working when the backend adds a field), split
 * this into a second `lenient` config used at production call sites, with
 * `strict` reserved for tests / debug builds. Not needed yet.
 */
object NovaJson {

    val strict: Json = Json {
        ignoreUnknownKeys = false
        explicitNulls = false
        isLenient = false
    }
}
