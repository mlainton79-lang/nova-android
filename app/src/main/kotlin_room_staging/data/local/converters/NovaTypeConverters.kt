package com.mlainton.nova.data.local.converters

import androidx.room.TypeConverter
import org.json.JSONObject

class NovaTypeConverters {
    @TypeConverter
    fun fromMetadata(value: Map<String, String>?): String? {
        if (value == null) return null
        return JSONObject(value).toString()
    }

    @TypeConverter
    fun toMetadata(value: String?): Map<String, String>? {
        if (value.isNullOrBlank()) return null
        val json = JSONObject(value)
        val out = linkedMapOf<String, String>()
        val keys = json.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            out[key] = json.optString(key)
        }
        return out
    }
}
