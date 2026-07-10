package com.ojitos369.lumaloop.wear

import android.content.Context
import java.io.File
import org.json.JSONArray

/**
 * Watch-side state: the manifest (ordered item ids pushed by the phone),
 * the streamed-media temporary cache, and the watch face config.
 * Media files are NOT stored permanently: they are fetched on demand from
 * the phone and evicted once no longer near the playback position.
 */
object ImageStore {

    data class Entry(val id: String, val isVideo: Boolean)

    data class Config(
        val intervalSeconds: Int = 30,
        val shuffle: Boolean = false,
        val showDate: Boolean = true,
        val showComplications: Boolean = true,
        val clockPosition: String = "center",
        val ambientMedia: Boolean = true
    )

    private const val PREFS_NAME = "lumaloop_wear"
    private const val KEY_MANIFEST = "manifest_json"
    private const val KEY_MANIFEST_VERSION = "manifest_version"
    private const val KEY_CACHE_VERSION = "cache_version"
    private const val KEY_CONFIG_VERSION = "config_version"

    private const val KEY_INTERVAL = "cfg_interval"
    private const val KEY_SHUFFLE = "cfg_shuffle"
    private const val KEY_SHOW_DATE = "cfg_show_date"
    private const val KEY_SHOW_COMPLICATIONS = "cfg_show_complications"
    private const val KEY_CLOCK_POSITION = "cfg_clock_position"
    private const val KEY_AMBIENT_MEDIA = "cfg_ambient_media"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // ---------------------------------------------------------------- manifest

    fun setManifest(context: Context, itemsJson: String) {
        prefs(context).edit()
            .putString(KEY_MANIFEST, itemsJson)
            .putLong(KEY_MANIFEST_VERSION, manifestVersion(context) + 1)
            .apply()
        // Drop cached media that is no longer part of the set
        val ids = getManifest(context).map { it.id }.toSet()
        cacheDir(context).listFiles()?.forEach { f ->
            if (f.name.substringBefore('.') !in ids) f.delete()
        }
    }

    fun getManifest(context: Context): List<Entry> {
        val json = prefs(context).getString(KEY_MANIFEST, null) ?: return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Entry(o.getString("id"), o.optString("t") == "v")
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun manifestVersion(context: Context): Long =
        prefs(context).getLong(KEY_MANIFEST_VERSION, 0L)

    // ---------------------------------------------------------------- cache

    fun cacheDir(context: Context): File =
        File(context.filesDir, "stream_cache").apply { mkdirs() }

    fun cachedFile(context: Context, entry: Entry): File? {
        val ext = if (entry.isVideo) "mp4" else "jpg"
        val f = File(cacheDir(context), "${entry.id}.$ext")
        return if (f.isFile && f.length() > 0) f else null
    }

    fun cacheVersion(context: Context): Long =
        prefs(context).getLong(KEY_CACHE_VERSION, 0L)

    fun bumpCacheVersion(context: Context) {
        prefs(context).edit()
            .putLong(KEY_CACHE_VERSION, cacheVersion(context) + 1)
            .apply()
    }

    /** Temporary cache: keep only the ids near the playback position. */
    fun pruneCache(context: Context, keepIds: Set<String>) {
        cacheDir(context).listFiles()?.forEach { f ->
            if (f.name.substringBefore('.') !in keepIds) f.delete()
        }
    }

    // ---------------------------------------------------------------- config

    fun setConfig(
        context: Context,
        interval: Int,
        shuffle: Boolean,
        showDate: Boolean,
        showComplications: Boolean,
        clockPosition: String,
        ambientMedia: Boolean
    ) {
        prefs(context).edit()
            .putInt(KEY_INTERVAL, interval)
            .putBoolean(KEY_SHUFFLE, shuffle)
            .putBoolean(KEY_SHOW_DATE, showDate)
            .putBoolean(KEY_SHOW_COMPLICATIONS, showComplications)
            .putString(KEY_CLOCK_POSITION, clockPosition)
            .putBoolean(KEY_AMBIENT_MEDIA, ambientMedia)
            .putLong(KEY_CONFIG_VERSION, configVersion(context) + 1)
            .apply()
    }

    fun getConfig(context: Context): Config {
        val p = prefs(context)
        return Config(
            intervalSeconds = p.getInt(KEY_INTERVAL, 30),
            shuffle = p.getBoolean(KEY_SHUFFLE, false),
            showDate = p.getBoolean(KEY_SHOW_DATE, true),
            showComplications = p.getBoolean(KEY_SHOW_COMPLICATIONS, true),
            clockPosition = p.getString(KEY_CLOCK_POSITION, "center") ?: "center",
            ambientMedia = p.getBoolean(KEY_AMBIENT_MEDIA, true)
        )
    }

    fun configVersion(context: Context): Long =
        prefs(context).getLong(KEY_CONFIG_VERSION, 0L)
}
