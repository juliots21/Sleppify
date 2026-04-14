package com.example.sleppify

import android.content.Context
import android.text.TextUtils
import org.json.JSONArray
import org.json.JSONObject
import java.util.ArrayList
import java.util.Collections
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class PlaybackHistoryStore private constructor() {
    class QueueTrack(
        @JvmField val videoId: String,
        @JvmField val title: String,
        @JvmField val artist: String,
        @JvmField val duration: String,
        @JvmField val imageUrl: String
    )

    class Snapshot(
        queue: List<QueueTrack>,
        @JvmField val currentIndex: Int,
        @JvmField val currentSeconds: Int,
        @JvmField val totalSeconds: Int,
        @JvmField val isPlaying: Boolean,
        @JvmField val updatedAtMs: Long
    ) {
        @JvmField
        val queue: List<QueueTrack> = Collections.unmodifiableList(ArrayList(queue))

        fun isValid(): Boolean {
            return queue.isNotEmpty() && currentIndex >= 0 && currentIndex < queue.size
        }

        fun currentTrack(): QueueTrack? {
            if (!isValid()) {
                return null
            }
            return queue[currentIndex]
        }
    }

    companion object {
        private const val PREFS_NAME = "playback_history_store"
        private const val KEY_SNAPSHOT_JSON = "snapshot_json"
        private val IO_EXECUTOR: ExecutorService = Executors.newSingleThreadExecutor()
        private val CACHE_LOCK = Any()

        @Volatile
        private var cachedRawSnapshot: String = ""

        @Volatile
        private var cachedSnapshot: Snapshot = emptySnapshot()

        @JvmStatic
        fun load(context: Context): Snapshot {
            val appContext = context.applicationContext
            val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val raw = prefs.getString(KEY_SNAPSHOT_JSON, "").orEmpty()
            if (raw.isEmpty()) {
                val empty = emptySnapshot()
                synchronized(CACHE_LOCK) {
                    cachedRawSnapshot = ""
                    cachedSnapshot = empty
                }
                return empty
            }

            synchronized(CACHE_LOCK) {
                if (TextUtils.equals(raw, cachedRawSnapshot)) {
                    return cachedSnapshot
                }
            }

            val parsed = parseSnapshot(raw)
            synchronized(CACHE_LOCK) {
                cachedRawSnapshot = raw
                cachedSnapshot = parsed
            }
            return parsed
        }

        @JvmStatic
        fun save(
            context: Context,
            queue: List<QueueTrack>,
            currentIndex: Int,
            currentSeconds: Int,
            totalSeconds: Int,
            isPlaying: Boolean
        ) {
            if (queue.isEmpty()) {
                return
            }

            val appContext = context.applicationContext
            val safeIndex = currentIndex.coerceIn(0, queue.size - 1)
            val safeCurrentSeconds = currentSeconds.coerceAtLeast(0)
            val safeTotalSeconds = totalSeconds.coerceAtLeast(1)
            val updatedAtMs = System.currentTimeMillis()

            IO_EXECUTOR.execute {
                try {
                    val queueCopy = copyQueue(queue)
                    val snapshot = Snapshot(
                        queueCopy,
                        safeIndex,
                        safeCurrentSeconds,
                        safeTotalSeconds,
                        isPlaying,
                        updatedAtMs
                    )

                    val raw = serializeSnapshot(snapshot)
                    if (raw.isEmpty()) {
                        return@execute
                    }

                    synchronized(CACHE_LOCK) {
                        if (TextUtils.equals(raw, cachedRawSnapshot)) {
                            cachedSnapshot = snapshot
                            return@execute
                        }
                    }

                    val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    prefs.edit().putString(KEY_SNAPSHOT_JSON, raw).apply()

                    synchronized(CACHE_LOCK) {
                        cachedRawSnapshot = raw
                        cachedSnapshot = snapshot
                    }
                } catch (_: Exception) {
                }
            }
        }

        private fun parseSnapshot(raw: String): Snapshot {
            return try {
                val root = JSONObject(raw)
                val queueArray = root.optJSONArray("queue")
                val queue = ArrayList<QueueTrack>()

                if (queueArray != null) {
                    for (i in 0 until queueArray.length()) {
                        val item = queueArray.optJSONObject(i) ?: continue
                        val videoId = safe(item.optString("videoId", ""))
                        if (videoId.isEmpty()) {
                            continue
                        }

                        queue.add(
                            QueueTrack(
                                videoId,
                                safe(item.optString("title", "")),
                                safe(item.optString("artist", "")),
                                safe(item.optString("duration", "")),
                                safe(item.optString("imageUrl", ""))
                            )
                        )
                    }
                }

                val currentIndex = root.optInt("currentIndex", 0)
                val currentSeconds = root.optInt("currentSeconds", 0).coerceAtLeast(0)
                val totalSeconds = root.optInt("totalSeconds", 1).coerceAtLeast(1)
                val isPlaying = root.optBoolean("isPlaying", false)
                val updatedAtMs = root.optLong("updatedAtMs", 0L)

                if (queue.isEmpty()) {
                    emptySnapshot()
                } else {
                    val safeIndex = currentIndex.coerceIn(0, queue.size - 1)
                    Snapshot(queue, safeIndex, currentSeconds, totalSeconds, isPlaying, updatedAtMs)
                }
            } catch (_: Exception) {
                emptySnapshot()
            }
        }

        private fun serializeSnapshot(snapshot: Snapshot): String {
            return try {
                val queueArray = JSONArray()
                for (track in snapshot.queue) {
                    val item = JSONObject()
                    item.put("videoId", safe(track.videoId))
                    item.put("title", safe(track.title))
                    item.put("artist", safe(track.artist))
                    item.put("duration", safe(track.duration))
                    item.put("imageUrl", safe(track.imageUrl))
                    queueArray.put(item)
                }

                val root = JSONObject()
                root.put("queue", queueArray)
                root.put("currentIndex", snapshot.currentIndex)
                root.put("currentSeconds", snapshot.currentSeconds)
                root.put("totalSeconds", snapshot.totalSeconds)
                root.put("isPlaying", snapshot.isPlaying)
                root.put("updatedAtMs", snapshot.updatedAtMs)
                root.toString()
            } catch (_: Exception) {
                ""
            }
        }

        private fun copyQueue(queue: List<QueueTrack>): List<QueueTrack> {
            val copy = ArrayList<QueueTrack>(queue.size)
            for (track in queue) {
                copy.add(
                    QueueTrack(
                        safe(track.videoId),
                        safe(track.title),
                        safe(track.artist),
                        safe(track.duration),
                        safe(track.imageUrl)
                    )
                )
            }
            return copy
        }

        private fun emptySnapshot(): Snapshot {
            return Snapshot(emptyList(), 0, 0, 1, false, 0L)
        }

        private fun safe(value: String?): String {
            return value.orEmpty()
        }
    }
}
