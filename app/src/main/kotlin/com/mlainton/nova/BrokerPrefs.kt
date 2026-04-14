package com.mlainton.nova

import android.content.Context

object BrokerPrefs {
    private const val PREFS_NAME = "nova_broker_prefs"
    private const val KEY_BRAIN_MODE = "brain_mode"

    fun getBrainMode(context: Context): BrainMode {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_BRAIN_MODE, BrainMode.LOCAL_TONY.name)

        return try {
            BrainMode.valueOf(raw ?: BrainMode.LOCAL_TONY.name)
        } catch (_: Exception) {
            BrainMode.LOCAL_TONY
        }
    }

    fun setBrainMode(context: Context, mode: BrainMode) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_BRAIN_MODE, mode.name)
            .apply()
    }
}
