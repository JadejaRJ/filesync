package com.syncbridge.app.data.mapper

import com.syncbridge.core.common.model.FileFilterConfig
import org.json.JSONArray
import org.json.JSONObject

/** Serializes [FileFilterConfig] to/from the JSON blob stored in `sync_profiles.filterConfigJson`. */
object FilterConfigJson {

    fun toJson(config: FileFilterConfig): String = JSONObject().apply {
        put("includeExtensions", JSONArray(config.includeExtensions.toList()))
        put("excludeExtensions", JSONArray(config.excludeExtensions.toList()))
        put("excludeHiddenFiles", config.excludeHiddenFiles)
        put("maxFileSizeBytes", config.maxFileSizeBytes ?: JSONObject.NULL)
        put("excludeFolderNames", JSONArray(config.excludeFolderNames.toList()))
        put("excludeWildcardPatterns", JSONArray(config.excludeWildcardPatterns.toList()))
    }.toString()

    fun fromJson(json: String): FileFilterConfig {
        val obj = JSONObject(json)
        return FileFilterConfig(
            includeExtensions = obj.optJSONArray("includeExtensions").toStringSet(),
            excludeExtensions = obj.optJSONArray("excludeExtensions").toStringSet(),
            excludeHiddenFiles = obj.optBoolean("excludeHiddenFiles", true),
            maxFileSizeBytes = if (obj.isNull("maxFileSizeBytes")) null else obj.optLong("maxFileSizeBytes"),
            excludeFolderNames = obj.optJSONArray("excludeFolderNames").toStringSet(),
            excludeWildcardPatterns = obj.optJSONArray("excludeWildcardPatterns").toStringSet(),
        )
    }

    private fun JSONArray?.toStringSet(): Set<String> {
        if (this == null) return emptySet()
        return (0 until length()).map { getString(it) }.toSet()
    }
}
