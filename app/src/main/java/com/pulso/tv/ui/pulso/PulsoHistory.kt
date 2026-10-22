package com.pulso.tv.ui.pulso

import android.content.Context
import com.pulso.tv.model.ui.Video
import org.json.JSONArray
import org.json.JSONObject

/** Where the user stopped in recorded videos, so Pulso can resume and list them. */
object PulsoHistory {
    private const val FILE = "pulso_history"
    private const val KEY = "resume"
    private const val MAX = 12

    class ResumeItem(val video: Video, val positionMs: Long)

    private fun read(ctx: Context): JSONArray =
        runCatching { JSONArray(ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(KEY, "[]")) }
            .getOrDefault(JSONArray())

    private fun write(ctx: Context, items: List<JSONObject>) {
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putString(KEY, JSONArray(items).toString()).apply()
    }

    private fun items(ctx: Context): List<JSONObject> {
        val a = read(ctx)
        return (0 until a.length()).mapNotNull { a.optJSONObject(it) }
    }

    fun position(ctx: Context, videoId: String?): Long =
        items(ctx).firstOrNull { it.optString("id") == videoId }?.optLong("pos", 0L) ?: 0L

    /** Saves progress; finished videos (or ones barely started) drop out of the list. */
    fun saveProgress(ctx: Context, video: Video, positionMs: Long, durationMs: Long) {
        val id = video.id ?: return
        val rest = items(ctx).filter { it.optString("id") != id }
        val finished = durationMs > 0 && positionMs >= durationMs - 15_000
        if (finished || positionMs < 10_000) {
            write(ctx, rest)
            return
        }
        val entry = JSONObject()
            .put("id", id)
            .put("channelId", video.channelId)
            .put("channelLogin", video.channelLogin)
            .put("channelName", video.channelName)
            .put("title", video.title)
            .put("thumb", video.thumbnailURL)
            .put("createdAt", video.createdAt)
            .put("duration", video.durationSeconds ?: 0)
            .put("views", video.viewCount ?: 0)
            .put("type", video.type)
            .put("pos", positionMs)
        write(ctx, (listOf(entry) + rest).take(MAX))
    }

    fun loadResume(ctx: Context): List<ResumeItem> = items(ctx).map {
        ResumeItem(
            Video(
                id = it.optString("id"),
                channelId = it.optString("channelId").ifBlank { null },
                channelLogin = it.optString("channelLogin").ifBlank { null },
                channelName = it.optString("channelName").ifBlank { null },
                title = it.optString("title").ifBlank { null },
                thumbnailURL = it.optString("thumb").ifBlank { null },
                createdAt = it.optString("createdAt").ifBlank { null },
                viewCount = it.optInt("views"),
                durationSeconds = it.optInt("duration"),
                type = it.optString("type").ifBlank { null },
            ),
            it.optLong("pos", 0L),
        )
    }
}
