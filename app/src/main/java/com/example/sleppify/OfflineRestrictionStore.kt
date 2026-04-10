package com.example.sleppify

import android.content.Context
import android.net.Uri
import android.text.TextUtils

object OfflineRestrictionStore {
    private const val PREFS_NAME = "offline_restrictions"
    private const val KEY_RESTRICTED_IDS = "restricted_video_ids"
    private const val KEY_NEWPIPE_AUTO_FAIL_PREFIX = "newpipe_auto_fail_"

    @JvmStatic
    fun isRestricted(context: Context, videoId: String): Boolean {
        val safeVideoId = sanitize(videoId)
        if (safeVideoId.isEmpty()) {
            return false
        }
        return getRestrictedIds(context).contains(safeVideoId)
    }

    @JvmStatic
    fun isRestricted(restrictedIds: Set<String>, videoId: String): Boolean {
        if (restrictedIds.isEmpty()) {
            return false
        }
        val safeVideoId = sanitize(videoId)
        if (safeVideoId.isEmpty()) {
            return false
        }
        return restrictedIds.contains(safeVideoId)
    }

    @JvmStatic
    fun getRestrictedIds(context: Context): Set<String> {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val stored = prefs.getStringSet(KEY_RESTRICTED_IDS, emptySet())
        if (stored.isNullOrEmpty()) {
            return emptySet()
        }

        val normalized = LinkedHashSet<String>()
        var changed = false
        for (raw in stored) {
            val safe = sanitize(raw ?: "")
            if (safe.isEmpty()) {
                changed = true
                continue
            }
            if (!TextUtils.equals(raw?.trim().orEmpty(), safe)) {
                changed = true
            }
            normalized.add(safe)
        }

        if (changed) {
            prefs.edit().putStringSet(KEY_RESTRICTED_IDS, normalized).apply()
        }

        return normalized
    }

    @JvmStatic
    fun getRestrictedVideoIds(context: Context): Set<String> {
        return getRestrictedIds(context)
    }

    @JvmStatic
    fun markRestricted(context: Context, videoId: String) {
        val safeVideoId = sanitize(videoId)
        if (safeVideoId.isEmpty()) {
            return
        }

        val ids = getRestrictedIds(context).toMutableSet()
        if (!ids.add(safeVideoId)) {
            return
        }

        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putStringSet(KEY_RESTRICTED_IDS, ids)
            .apply()
    }

    @JvmStatic
    fun getAutomaticNewPipeFailureCount(context: Context, videoId: String): Int {
        val safeVideoId = sanitize(videoId)
        if (safeVideoId.isEmpty()) {
            return 0
        }

        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_NEWPIPE_AUTO_FAIL_PREFIX + safeVideoId, 0).coerceAtLeast(0)
    }

    @JvmStatic
    fun incrementAutomaticNewPipeFailure(context: Context, videoId: String): Int {
        val safeVideoId = sanitize(videoId)
        if (safeVideoId.isEmpty()) {
            return 0
        }

        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val next = (prefs.getInt(KEY_NEWPIPE_AUTO_FAIL_PREFIX + safeVideoId, 0).coerceAtLeast(0)) + 1
        prefs.edit().putInt(KEY_NEWPIPE_AUTO_FAIL_PREFIX + safeVideoId, next).apply()
        return next
    }

    @JvmStatic
    fun clearAutomaticNewPipeFailures(context: Context, videoId: String) {
        val safeVideoId = sanitize(videoId)
        if (safeVideoId.isEmpty()) {
            return
        }

        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_NEWPIPE_AUTO_FAIL_PREFIX + safeVideoId)
            .apply()
    }

    @JvmStatic
    fun addRestrictedVideoId(context: Context, videoId: String) {
        markRestricted(context, videoId)
    }

    @JvmStatic
    fun unmarkRestricted(context: Context, videoId: String) {
        val safeVideoId = sanitize(videoId)
        if (safeVideoId.isEmpty()) {
            return
        }

        val ids = getRestrictedIds(context).toMutableSet()
        if (!ids.remove(safeVideoId)) {
            return
        }

        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putStringSet(KEY_RESTRICTED_IDS, ids)
            .apply()
    }

    @JvmStatic
    fun countRestricted(context: Context, videoIds: List<String>): Int {
        if (videoIds.isEmpty()) {
            return 0
        }

        val restricted = getRestrictedIds(context)
        if (restricted.isEmpty()) {
            return 0
        }

        var count = 0
        val seen = HashSet<String>()
        for (raw in videoIds) {
            val id = sanitize(raw)
            if (id.isEmpty() || !seen.add(id)) {
                continue
            }
            if (restricted.contains(id)) {
                count++
            }
        }
        return count
    }

    @JvmStatic
    fun filterPlayableForOffline(context: Context, videoIds: List<String>): List<String> {
        if (videoIds.isEmpty()) {
            return emptyList()
        }

        val restricted = getRestrictedIds(context)
        val playable = ArrayList<String>(videoIds.size)
        val seen = HashSet<String>()
        for (raw in videoIds) {
            val id = sanitize(raw)
            if (id.isEmpty() || !seen.add(id)) {
                continue
            }
            if (!restricted.contains(id)) {
                playable.add(id)
            }
        }
        return playable
    }

    private fun sanitize(videoId: String): String {
        val value = videoId.trim()
        if (value.isEmpty()) {
            return ""
        }

        val extracted = extractVideoId(value)
        if (extracted.isNotEmpty()) {
            return extracted
        }

        return value
    }

    private fun extractVideoId(raw: String): String {
        val value = raw.trim()
        if (value.isEmpty()) {
            return ""
        }

        try {
            val uri = Uri.parse(value)
            val queryV = uri.getQueryParameter("v")
            if (!queryV.isNullOrBlank()) {
                return queryV.trim()
            }

            val segments = uri.pathSegments
            val host = uri.host.orEmpty().lowercase()

            if (host.contains("youtu.be") && segments.isNotEmpty()) {
                return segments[0].trim()
            }

            if (host.contains("youtube.com") || host.contains("music.youtube.com")) {
                if (segments.size >= 2 && segments[0] == "embed") {
                    return segments[1].trim()
                }
                if (segments.size >= 2 && segments[0] == "shorts") {
                    return segments[1].trim()
                }
            }
        } catch (_: Exception) {
        }

        val watchIndex = value.indexOf("watch?v=")
        if (watchIndex >= 0) {
            var tail = value.substring(watchIndex + 8)
            val amp = tail.indexOf('&')
            if (amp >= 0) {
                tail = tail.substring(0, amp)
            }
            return tail.trim()
        }

        val youtuIndex = value.indexOf("youtu.be/")
        if (youtuIndex >= 0) {
            var tail = value.substring(youtuIndex + 9)
            val slash = tail.indexOf('/')
            if (slash >= 0) {
                tail = tail.substring(0, slash)
            }
            val q = tail.indexOf('?')
            if (q >= 0) {
                tail = tail.substring(0, q)
            }
            return tail.trim()
        }

        return ""
    }
}
