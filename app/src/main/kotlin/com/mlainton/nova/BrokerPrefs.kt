package com.mlainton.nova

import android.content.Context

object BrokerPrefs {
    private const val PREFS_NAME = "nova_broker_prefs"
    private const val KEY_BRAIN_MODE = "brain_mode"

    fun getBrainMode(context: Context): BrainMode {
        // Fallback is AUTO: safe default when no pref is set and the resolution
        // for unknown/removed enum names (a stored value from an older build
        // whose entry has since been retired). Falling back to a specific
        // provider would silently steer users onto a seat that may be dead.
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_BRAIN_MODE, BrainMode.AUTO.name)

        val resolved = try {
            BrainMode.valueOf(raw ?: BrainMode.AUTO.name)
        } catch (_: Exception) {
            BrainMode.AUTO
        }
        // Mothballed enum entries still deserialise cleanly (the name is
        // valid), so the valueOf try/catch above wouldn't catch them. Migrate
        // them to AUTO here so users persisted on a dead seat don't keep
        // routing to it silently between opens.
        return if (resolved in MOTHBALLED_BRAINS) BrainMode.AUTO else resolved
    }

    fun setBrainMode(context: Context, mode: BrainMode) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_BRAIN_MODE, mode.name)
            .apply()
    }
}
