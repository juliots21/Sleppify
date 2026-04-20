package com.example.sleppify

import android.os.Handler
import android.os.Looper
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class YouTubeMusicService(
    private val executor: ExecutorService = SHARED_EXECUTOR
) {

    private val mainHandler = Handler(Looper.getMainLooper())

    companion object {
        private const val API_BASE_URL = "https://www.googleapis.com/youtube/v3"
        private const val YT_SCOPE_READONLY = "https://www.googleapis.com/auth/youtube.readonly"
        private const val MUSIC_LIKES_PLAYLIST_ID = "LM"
        const val SPECIAL_LIKED_VIDEOS_ID = "__liked_videos__"
        private const val SPECIAL_LIKED_VIDEOS_TITLE = "Me gusta"
        private const val YOUTUBE_PAGE_MAX_RESULTS = 50
        private const val MIN_PUBLIC_MUSIC_DURATION_SECONDS = 70
        private val SHARED_EXECUTOR = Executors.newFixedThreadPool(3)

        private fun safeUrlEncode(value: String): String {
            return try {
                URLEncoder.encode(value, StandardCharsets.UTF_8.name())
            } catch (ignored: Exception) {
                value
            }
        }
    }

    private data class ApiErrorDetails(val reason: String, val message: String)

    interface SearchCallback {
        fun onSuccess(tracks: List<TrackResult>)
        fun onError(error: String)
    }

    interface SearchPageCallback {
        fun onSuccess(pageResult: SearchPageResult)
        fun onError(error: String)
    }

    data class SearchPageResult(
        @JvmField val tracks: List<TrackResult>,
        @JvmField val nextPageToken: String
    )

    interface PlaylistsCallback {
        fun onSuccess(playlists: List<PlaylistResult>)
        fun onError(error: String)
    }

    interface PlaylistTracksCallback {
        fun onSuccess(tracks: List<PlaylistTrackResult>)
        fun onError(error: String)
    }

    fun interface SimpleResultCallback {
        fun onResult(success: Boolean, error: String?)
    }

    interface PlaylistMetaCallback {
        fun onSuccess(playlist: PlaylistResult)
        fun onError(error: String)
    }

    data class TrackResult(
        @JvmField val resultType: String,
        @JvmField val contentId: String,
        @JvmField val title: String,
        @JvmField val subtitle: String,
        @JvmField val thumbnailUrl: String
    ) {
        @JvmField val videoId: String = if (resultType == "video") contentId else ""

        fun isVideo(): Boolean = resultType == "video" && contentId.isNotEmpty()
        fun isPlaylist(): Boolean = resultType == "playlist" && contentId.isNotEmpty()

        fun getWatchUrl(): String {
            if (contentId.isEmpty()) return "https://music.youtube.com/"
            return when (resultType) {
                "playlist" -> "https://music.youtube.com/playlist?list=${safeUrlEncode(contentId)}"
                "channel" -> "https://www.youtube.com/channel/${safeUrlEncode(contentId)}"
                else -> "https://music.youtube.com/watch?v=${safeUrlEncode(contentId)}"
            }
        }
    }

    data class PlaylistResult(
        @JvmField val playlistId: String,
        @JvmField val title: String,
        @JvmField val ownerName: String,
        @JvmField val itemCount: Int,
        @JvmField val thumbnailUrl: String,
        @JvmField val privacyStatus: String,
        @JvmField val publishedAt: String
    ) {
        fun getOpenUrl(): String = "https://music.youtube.com/playlist?list=${safeUrlEncode(playlistId)}"
    }

    data class PlaylistTrackResult(
        @JvmField val videoId: String,
        @JvmField val title: String,
        @JvmField val artist: String,
        @JvmField val duration: String,
        @JvmField val thumbnailUrl: String
    )

    fun searchTracks(query: String, maxResults: Int, callback: SearchCallback) {
        searchTracksPaged(query, maxResults, null, object : SearchPageCallback {
            override fun onSuccess(pageResult: SearchPageResult) {
                callback.onSuccess(pageResult.tracks)
            }

            override fun onError(error: String) {
                callback.onError(error)
            }
        })
    }

    fun searchTracksPaged(
        query: String,
        maxResults: Int,
        pageToken: String?,
        callback: SearchPageCallback
    ) {
        val normalized = query.trim()
        if (normalized.isEmpty()) {
            callback.onError("Escribe algo para buscar.")
            return
        }

        val apiKey = BuildConfig.YOUTUBE_DATA_API_KEY?.trim() ?: ""
        if (apiKey.isEmpty()) {
            callback.onError("Configura YOUTUBE_DATA_API_KEY para habilitar busqueda.")
            return
        }

        executor.execute {
            try {
                val pageResult = performSearchRequest(
                    normalized,
                    maxOf(1, maxResults),
                    apiKey,
                    pageToken
                )
                mainHandler.post { callback.onSuccess(pageResult) }
            } catch (e: Exception) {
                val error = e.message ?: "No se pudo completar la busqueda."
                mainHandler.post { callback.onError(error) }
            }
        }
    }

    fun fetchMyPlaylists(accessToken: String, maxResults: Int, callback: PlaylistsCallback) {
        val token = accessToken.trim()
        if (token.isEmpty()) {
            callback.onError("No hay token OAuth para cargar la biblioteca.")
            return
        }

        executor.execute {
            try {
                val playlists = performMyPlaylistsRequest(token, maxOf(1, maxResults))
                mainHandler.post { callback.onSuccess(playlists) }
            } catch (e: Exception) {
                val error = e.message ?: "No se pudo cargar la biblioteca."
                mainHandler.post { callback.onError(error) }
            }
        }
    }

    fun insertTrackToPlaylist(
        accessToken: String,
        playlistId: String,
        videoId: String,
        callback: SimpleResultCallback
    ) {
        val token = accessToken.trim()
        if (token.isEmpty() || playlistId.isEmpty() || videoId.isEmpty()) {
            callback.onResult(false, "Parametros invalidos.")
            return
        }

        executor.execute {
            try {
                val ok = performInsertPlaylistTrackRequest(token, playlistId, videoId)
                mainHandler.post { callback.onResult(ok, if (ok) null else "No se pudo añadir a la playlist.") }
            } catch (e: Exception) {
                Log.e("YouTubeMusicService", "Error inserting playlist track", e)
                mainHandler.post { callback.onResult(false, e.message) }
            }
        }
    }

    fun fetchPlaylistTracks(
        accessToken: String,
        playlistId: String,
        maxResults: Int,
        callback: PlaylistTracksCallback
    ) {
        val token = accessToken.trim()
        if (token.isEmpty()) {
            callback.onError("No hay token OAuth para cargar canciones.")
            return
        }

        val normalizedPlaylistId = playlistId.trim()
        if (normalizedPlaylistId.isEmpty()) {
            callback.onError("Playlist invalida.")
            return
        }

        executor.execute {
            try {
                var tracks = if (SPECIAL_LIKED_VIDEOS_ID == normalizedPlaylistId) {
                    var likes = performMusicLikesTracksRequest(token, maxOf(1, maxResults))
                    if (likes.isEmpty()) {
                        likes = performLikedVideosTracksRequest(token, maxOf(1, maxResults))
                    }
                    likes
                } else {
                    performPlaylistTracksRequest(token, normalizedPlaylistId, maxOf(1, maxResults))
                }
                mainHandler.post { callback.onSuccess(tracks) }
            } catch (e: Exception) {
                val error = e.message ?: "No se pudo cargar canciones."
                mainHandler.post { callback.onError(error) }
            }
        }
    }

    fun fetchPlaylistMeta(
        accessToken: String,
        playlistId: String,
        callback: PlaylistMetaCallback
    ) {
        val token = accessToken.trim()
        val normalizedPlaylistId = playlistId.trim()
        if (token.isEmpty() || normalizedPlaylistId.isEmpty()) {
            callback.onError("Sin datos para leer metadata de playlist.")
            return
        }

        executor.execute {
            try {
                val result = fetchPlaylistById(token, normalizedPlaylistId)
                if (result == null) {
                    mainHandler.post { callback.onError("No se encontro metadata de playlist.") }
                    return@execute
                }
                mainHandler.post { callback.onSuccess(result) }
            } catch (e: Exception) {
                val error = e.message ?: "No se pudo leer metadata de playlist."
                mainHandler.post { callback.onError(error) }
            }
        }
    }

    private fun performSearchRequest(
        query: String,
        maxResults: Int,
        apiKey: String,
        pageToken: String?
    ): SearchPageResult {
        var endpoint = "$API_BASE_URL/search?part=snippet&type=video&videoCategoryId=10&videoEmbeddable=true&maxResults=${minOf(50, maxResults)}&q=${safeUrlEncode(query)}&key=${safeUrlEncode(apiKey)}"

        val normalizedPageToken = pageToken?.trim() ?: ""
        if (normalizedPageToken.isNotEmpty()) {
            endpoint += "&pageToken=${safeUrlEncode(normalizedPageToken)}"
        }

        val connection = openGetConnection(endpoint)
        try {
            val statusCode = connection.responseCode
            val body = readResponse(connection, statusCode >= 400)
            if (statusCode != HttpURLConnection.HTTP_OK) {
                throw IllegalStateException(buildApiErrorMessage(body, statusCode))
            }

            val root = JSONObject(body)
            val items = root.optJSONArray("items")
            val nextPageToken = root.optString("nextPageToken", "").trim()
            var result = mutableListOf<TrackResult>()
            
            if (items == null) {
                return SearchPageResult(result, nextPageToken)
            }

            for (i in 0 until items.length()) {
                val item = items.optJSONObject(i) ?: continue
                val idObject = item.optJSONObject("id")
                val snippet = item.optJSONObject("snippet")
                if (idObject == null || snippet == null) continue

                val kind = idObject.optString("kind", "")
                if (!kind.endsWith("#video")) continue

                val resultType = "video"
                val contentId = idObject.optString("videoId", "").trim()
                if (contentId.isEmpty()) continue

                val title = snippet.optString("title", "").trim()
                if (title.isEmpty()) continue

                val channelTitle = snippet.optString("channelTitle", "").trim()
                // Initial keyword filter before duration fetch
                if (!isPotentiallyMusic(title)) continue

                val subtitle = buildSearchSubtitle(resultType, channelTitle)
                val thumbnailUrl = extractYouTubeThumbnail(snippet.optJSONObject("thumbnails"))

                result.add(TrackResult(resultType, contentId, title, subtitle, thumbnailUrl))
            }

            if (result.isNotEmpty()) {
                val videoIds = result.mapNotNull { if (it.isVideo() && it.videoId.isNotEmpty()) it.videoId else null }

                if (videoIds.isNotEmpty()) {
                    try {
                        val filterMap = fetchPublicVideoFiltersByIds(apiKey, videoIds)
                        if (filterMap.isNotEmpty()) {
                            val filtered = mutableListOf<TrackResult>()
                            for (item in result) {
                                if (!item.isVideo() || item.videoId.isEmpty()) {
                                    filtered.add(item)
                                    continue
                                }

                                val info = filterMap[item.videoId]
                                if (info == null) {
                                    filtered.add(item)
                                    continue
                                }

                                if (!info.embeddable) continue

                                // Strict duration and keyword check
                                if (!isMusicContent(item.title, info.durationSeconds)) {
                                    continue
                                }

                                filtered.add(item)
                            }
                            result = filtered
                        }
                    } catch (ignored: Exception) {
                    }
                }
            }

            return SearchPageResult(result, nextPageToken)
        } finally {
            connection.disconnect()
        }
    }

    private fun performMyPlaylistsRequest(
        accessToken: String,
        maxResults: Int
    ): List<PlaylistResult> {
        val targetCount = maxOf(1, maxResults)
        var pageToken = ""
        val result = mutableListOf<PlaylistResult>()

        while (result.size < targetCount) {
            val pageSize = minOf(YOUTUBE_PAGE_MAX_RESULTS, targetCount - result.size)
            var endpoint = "$API_BASE_URL/playlists?part=snippet,contentDetails,status&mine=true&maxResults=$pageSize"
            if (pageToken.isNotEmpty()) {
                endpoint += "&pageToken=${safeUrlEncode(pageToken)}"
            }

            val connection = openGetConnection(endpoint, "Bearer $accessToken")
            try {
                val statusCode = connection.responseCode
                val body = readResponse(connection, statusCode >= 400)
                if (statusCode != HttpURLConnection.HTTP_OK) {
                    throw IllegalStateException(buildApiErrorMessage(body, statusCode))
                }

                val root = JSONObject(body)
                val items = root.optJSONArray("items") ?: JSONArray()

                for (i in 0 until items.length()) {
                    val item = items.optJSONObject(i) ?: continue

                    val playlistId = item.optString("id", "").trim()
                    val snippet = item.optJSONObject("snippet")
                    val contentDetails = item.optJSONObject("contentDetails")
                    if (playlistId.isEmpty() || snippet == null || contentDetails == null) continue

                    val title = snippet.optString("title", "").trim()
                    if (title.isEmpty()) continue

                    val itemCount = contentDetails.optInt("itemCount", 0)
                    val owner = snippet.optString("channelTitle", "").trim()
                    val thumbnailUrl = extractYouTubeThumbnail(snippet.optJSONObject("thumbnails"))
                    val status = item.optJSONObject("status")
                    val privacyStatus = status?.optString("privacyStatus", "")?.trim() ?: ""
                    val publishedAt = snippet.optString("publishedAt", "").trim()

                    result.add(
                        PlaylistResult(
                            playlistId,
                            title,
                            owner,
                            itemCount,
                            thumbnailUrl,
                            privacyStatus,
                            publishedAt
                        )
                    )
                    if (result.size >= targetCount) break
                }

                pageToken = root.optString("nextPageToken", "").trim()
                if (pageToken.isEmpty()) break
            } finally {
                connection.disconnect()
            }
        }

        var likesResult: PlaylistResult? = null
        val musicLikes = fetchPlaylistById(accessToken, MUSIC_LIKES_PLAYLIST_ID)
        if (musicLikes != null) {
            likesResult = toSpecialLikesCollection(musicLikes, "Tu cuenta de YouTube Music")
        }

        if (likesResult == null) {
            val likesPlaylistId = resolveLikesPlaylistId(accessToken)
            if (likesPlaylistId.isNotEmpty()) {
                val youtubeLikes = fetchPlaylistById(accessToken, likesPlaylistId)
                if (youtubeLikes != null) {
                    likesResult = toSpecialLikesCollection(youtubeLikes, "Tu cuenta de YouTube")
                }
            }
        }

        if (likesResult == null) {
            likesResult = buildFallbackLikedVideosCollection(accessToken)
        }

        if (likesResult != null) {
            upsertPlaylistAtTop(result, likesResult)
        }

        return result
    }

    private fun resolveLikesPlaylistId(accessToken: String): String {
        val endpoint = "$API_BASE_URL/channels?part=contentDetails&mine=true&maxResults=1"

        val connection = openGetConnection(endpoint, "Bearer $accessToken")
        try {
            val statusCode = connection.responseCode
            val body = readResponse(connection, statusCode >= 400)
            if (statusCode != HttpURLConnection.HTTP_OK) {
                throw IllegalStateException(buildApiErrorMessage(body, statusCode))
            }

            val root = JSONObject(body)
            val items = root.optJSONArray("items")
            if (items == null || items.length() == 0) return ""

            val first = items.optJSONObject(0) ?: return ""
            val contentDetails = first.optJSONObject("contentDetails") ?: return ""
            val relatedPlaylists = contentDetails.optJSONObject("relatedPlaylists") ?: return ""

            return relatedPlaylists.optString("likes", "").trim()
        } finally {
            connection.disconnect()
        }
    }

    private fun fetchPlaylistById(accessToken: String, playlistId: String): PlaylistResult? {
        val endpoint = "$API_BASE_URL/playlists?part=snippet,contentDetails,status&id=${safeUrlEncode(playlistId)}&maxResults=1"

        val connection = openGetConnection(endpoint, "Bearer $accessToken")
        try {
            val statusCode = connection.responseCode
            val body = readResponse(connection, statusCode >= 400)
            if (statusCode != HttpURLConnection.HTTP_OK) {
                throw IllegalStateException(buildApiErrorMessage(body, statusCode))
            }

            val root = JSONObject(body)
            val items = root.optJSONArray("items")
            if (items == null || items.length() == 0) return null

            val item = items.optJSONObject(0) ?: return null

            val resolvedId = item.optString("id", "").trim()
            val snippet = item.optJSONObject("snippet")
            val contentDetails = item.optJSONObject("contentDetails")
            if (resolvedId.isEmpty() || snippet == null || contentDetails == null) return null

            var title = snippet.optString("title", "").trim()
            if (title.isEmpty()) {
                title = SPECIAL_LIKED_VIDEOS_TITLE
            }

            val owner = snippet.optString("channelTitle", "").trim()
            val itemCount = maxOf(0, contentDetails.optInt("itemCount", 0))
            val thumbnailUrl = extractYouTubeThumbnail(snippet.optJSONObject("thumbnails"))
            val status = item.optJSONObject("status")
            val privacyStatus = status?.optString("privacyStatus", "")?.trim() ?: ""
            val publishedAt = snippet.optString("publishedAt", "").trim()

            val normalizedTitle = title.lowercase(Locale.US)
            if (normalizedTitle.contains("liked") || normalizedTitle.contains("gusta")) {
                title = SPECIAL_LIKED_VIDEOS_TITLE
            }

            return PlaylistResult(
                resolvedId,
                title,
                owner,
                itemCount,
                thumbnailUrl,
                privacyStatus,
                publishedAt
            )
        } finally {
            connection.disconnect()
        }
    }

    private fun buildFallbackLikedVideosCollection(accessToken: String): PlaylistResult? {
        val endpoint = "$API_BASE_URL/videos?part=snippet,contentDetails&myRating=like&maxResults=10"

        val connection = openGetConnection(endpoint, "Bearer $accessToken")
        try {
            val statusCode = connection.responseCode
            val body = readResponse(connection, statusCode >= 400)
            if (statusCode != HttpURLConnection.HTTP_OK) {
                throw IllegalStateException(buildApiErrorMessage(body, statusCode))
            }

            val root = JSONObject(body)
            val pageInfo = root.optJSONObject("pageInfo")
            val total = if (pageInfo == null) 0 else maxOf(0, pageInfo.optInt("totalResults", 0))

            val items = root.optJSONArray("items")
            if (total <= 0 && (items == null || items.length() == 0)) return null

            var thumbnailUrl = ""
            if (items != null && items.length() > 0) {
                for (i in 0 until items.length()) {
                    val item = items.optJSONObject(i) ?: continue
                    val snippet = item.optJSONObject("snippet")
                    val contentDetails = item.optJSONObject("contentDetails")
                    
                    if (snippet != null && contentDetails != null) {
                        val rawDuration = contentDetails.optString("duration", "").trim()
                        if (parseYoutubeDurationSeconds(rawDuration) > 62) {
                            thumbnailUrl = extractYouTubeThumbnail(snippet.optJSONObject("thumbnails"))
                            break
                        }
                    }
                }
                
                // Fallback to first if all were shorts
                if (thumbnailUrl.isEmpty()) {
                    val first = items.optJSONObject(0)
                    val snippet = first?.optJSONObject("snippet")
                    if (snippet != null) {
                        thumbnailUrl = extractYouTubeThumbnail(snippet.optJSONObject("thumbnails"))
                    }
                }
            }

            return PlaylistResult(
                SPECIAL_LIKED_VIDEOS_ID,
                SPECIAL_LIKED_VIDEOS_TITLE,
                "Tu cuenta de YouTube",
                total,
                thumbnailUrl,
                "private",
                ""
            )
        } finally {
            connection.disconnect()
        }
    }

    private fun upsertPlaylistAtTop(list: MutableList<PlaylistResult>, playlist: PlaylistResult) {
        val existingIndex = list.indexOfFirst { it.playlistId == playlist.playlistId }
        if (existingIndex >= 0) {
            list.removeAt(existingIndex)
        }
        list.add(0, playlist)
    }

    private fun performPlaylistTracksRequest(
        accessToken: String,
        playlistId: String,
        maxResults: Int
    ): List<PlaylistTrackResult> {
        val targetCount = maxOf(1, maxResults)
        var pageToken = ""
        val videoMap = linkedMapOf<String, TrackTempData>()

        while (videoMap.size < targetCount) {
            val pageSize = minOf(YOUTUBE_PAGE_MAX_RESULTS, targetCount - videoMap.size)
            var endpoint = "$API_BASE_URL/playlistItems?part=snippet,contentDetails&playlistId=${safeUrlEncode(playlistId)}&maxResults=$pageSize"
            if (pageToken.isNotEmpty()) {
                endpoint += "&pageToken=${safeUrlEncode(pageToken)}"
            }

            val connection = openGetConnection(endpoint, "Bearer $accessToken")
            try {
                val statusCode = connection.responseCode
                val body = readResponse(connection, statusCode >= 400)
                if (statusCode != HttpURLConnection.HTTP_OK) {
                    throw IllegalStateException(buildApiErrorMessage(body, statusCode))
                }

                val root = JSONObject(body)
                val items = root.optJSONArray("items")
                if (items != null) {
                    for (i in 0 until items.length()) {
                        val item = items.optJSONObject(i) ?: continue

                        val contentDetails = item.optJSONObject("contentDetails")
                        val snippet = item.optJSONObject("snippet")
                        if (contentDetails == null || snippet == null) continue

                        val videoId = contentDetails.optString("videoId", "").trim()
                        if (videoId.isEmpty()) continue

                        val title = snippet.optString("title", "").trim()
                        if (title.isEmpty() || title.equals("deleted video", ignoreCase = true) || title.equals("private video", ignoreCase = true)) {
                            continue
                        }

                        var artist = snippet.optString("videoOwnerChannelTitle", "").trim()
                        if (artist.isEmpty()) {
                            artist = snippet.optString("channelTitle", "").trim()
                        }
                        if (artist.isEmpty()) {
                            artist = "Unknown artist"
                        }

                        val thumbnailUrl = extractYouTubeThumbnail(snippet.optJSONObject("thumbnails"))
                        videoMap.put(videoId, TrackTempData(title, artist, thumbnailUrl))
                        if (videoMap.size >= targetCount) break
                    }
                }

                pageToken = root.optString("nextPageToken", "").trim()
                if (pageToken.isEmpty()) break
            } finally {
                connection.disconnect()
            }
        }

        val result = mutableListOf<PlaylistTrackResult>()
        val playbackInfoMap = fetchVideoPlaybackInfoByIdsInBatches(accessToken, videoMap.keys.toList())
        
        for ((videoId, data) in videoMap) {
            val playbackInfo = playbackInfoMap[videoId]
            if (playbackInfo != null && !playbackInfo.embeddable) continue
            
            val durationSeconds = parseYoutubeDurationSeconds(playbackInfo?.duration ?: "")
            if (!isMusicContent(data.title, durationSeconds)) continue

            var duration = playbackInfo?.duration ?: ""
            if (duration.isEmpty()) duration = "--:--"

            result.add(PlaylistTrackResult(videoId, data.title, data.artist, duration, data.thumbnailUrl))
            if (result.size >= targetCount) break
        }

        return result
    }

    private fun performLikedVideosTracksRequest(
        accessToken: String,
        maxResults: Int
    ): List<PlaylistTrackResult> {
        val targetCount = maxOf(1, maxResults)
        var pageToken = ""
        val seenVideoIds = mutableSetOf<String>()
        val result = mutableListOf<PlaylistTrackResult>()

        while (result.size < targetCount) {
            val pageSize = minOf(YOUTUBE_PAGE_MAX_RESULTS, targetCount - result.size)
            var endpoint = "$API_BASE_URL/videos?part=snippet,contentDetails,status&myRating=like&maxResults=$pageSize"
            if (pageToken.isNotEmpty()) {
                endpoint += "&pageToken=${safeUrlEncode(pageToken)}"
            }

            val connection = openGetConnection(endpoint, "Bearer $accessToken")
            try {
                val statusCode = connection.responseCode
                val body = readResponse(connection, statusCode >= 400)
                if (statusCode != HttpURLConnection.HTTP_OK) {
                    throw IllegalStateException(buildApiErrorMessage(body, statusCode))
                }

                val root = JSONObject(body)
                val items = root.optJSONArray("items")
                if (items != null) {
                    for (i in 0 until items.length()) {
                        val item = items.optJSONObject(i) ?: continue

                        val videoId = item.optString("id", "").trim()
                        val snippet = item.optJSONObject("snippet")
                        val contentDetails = item.optJSONObject("contentDetails")
                        val status = item.optJSONObject("status")
                        if (videoId.isEmpty() || snippet == null || !seenVideoIds.add(videoId)) continue

                        if (status != null && !status.optBoolean("embeddable", true)) continue

                        val title = snippet.optString("title", "").trim()
                        if (title.isEmpty() || title.equals("deleted video", ignoreCase = true) || title.equals("private video", ignoreCase = true)) {
                            continue
                        }

                        var artist = snippet.optString("channelTitle", "").trim()
                        if (artist.isEmpty()) artist = "Unknown artist"

                        val rawDuration = contentDetails?.optString("duration", "")?.trim() ?: ""
                        val durationSeconds = parseYoutubeDurationSeconds(rawDuration)
                        if (!isMusicContent(title, durationSeconds)) continue

                        var duration = formatYoutubeDuration(rawDuration)
                        if (duration.isEmpty()) duration = "--:--"

                        val thumbnailUrl = extractYouTubeThumbnail(snippet.optJSONObject("thumbnails"))
                        result.add(PlaylistTrackResult(videoId, title, artist, duration, thumbnailUrl))
                        if (result.size >= targetCount) break
                    }
                }

                pageToken = root.optString("nextPageToken", "").trim()
                if (pageToken.isEmpty()) break
            } finally {
                connection.disconnect()
            }
        }

        return result
    }

    private fun performMusicLikesTracksRequest(
        accessToken: String,
        maxResults: Int
    ): List<PlaylistTrackResult> {
        return try {
            performPlaylistTracksRequest(accessToken, MUSIC_LIKES_PLAYLIST_ID, maxResults)
        } catch (ignored: Exception) {
            emptyList()
        }
    }

    private fun toSpecialLikesCollection(
        source: PlaylistResult,
        ownerName: String
    ): PlaylistResult {
        return PlaylistResult(
            SPECIAL_LIKED_VIDEOS_ID,
            SPECIAL_LIKED_VIDEOS_TITLE,
            ownerName,
            source.itemCount,
            source.thumbnailUrl,
            source.privacyStatus,
            source.publishedAt
        )
    }

    private fun fetchVideoPlaybackInfoByIdsInBatches(
        accessToken: String,
        videoIds: List<String>
    ): Map<String, VideoPlaybackInfo> {
        val result = mutableMapOf<String, VideoPlaybackInfo>()
        if (videoIds.isEmpty()) return result

        for (start in videoIds.indices step YOUTUBE_PAGE_MAX_RESULTS) {
            val end = minOf(start + YOUTUBE_PAGE_MAX_RESULTS, videoIds.size)
            val batch = videoIds.subList(start, end)
            result.putAll(fetchVideoPlaybackInfoByIds(accessToken, batch))
        }
        return result
    }

    private fun fetchVideoPlaybackInfoByIds(
        accessToken: String,
        videoIds: List<String>
    ): Map<String, VideoPlaybackInfo> {
        val playbackInfoMap = mutableMapOf<String, VideoPlaybackInfo>()
        if (videoIds.isEmpty()) return playbackInfoMap

        val idsBuilder = StringBuilder()
        val maxIds = minOf(videoIds.size, 50)
        for (i in 0 until maxIds) {
            if (i > 0) idsBuilder.append(',')
            idsBuilder.append(videoIds[i])
        }

        val endpoint = "$API_BASE_URL/videos?part=contentDetails,status&id=${safeUrlEncode(idsBuilder.toString())}"
        val connection = openGetConnection(endpoint, "Bearer $accessToken")
        
        try {
            val statusCode = connection.responseCode
            val body = readResponse(connection, statusCode >= 400)
            if (statusCode != HttpURLConnection.HTTP_OK) {
                throw IllegalStateException(buildApiErrorMessage(body, statusCode))
            }

            val root = JSONObject(body)
            val items = root.optJSONArray("items") ?: return playbackInfoMap

            for (i in 0 until items.length()) {
                val item = items.optJSONObject(i) ?: continue
                val id = item.optString("id", "").trim()
                val contentDetails = item.optJSONObject("contentDetails")
                val status = item.optJSONObject("status")
                if (id.isEmpty()) continue

                val rawDuration = contentDetails?.optString("duration", "")?.trim() ?: ""
                val duration = formatYoutubeDuration(rawDuration)
                val embeddable = status?.optBoolean("embeddable", true) ?: true
                playbackInfoMap[id] = VideoPlaybackInfo(duration, embeddable)
            }
            return playbackInfoMap
        } finally {
            connection.disconnect()
        }
    }

    private fun fetchPublicVideoFiltersByIds(
        apiKey: String,
        videoIds: List<String>
    ): Map<String, PublicVideoFilterInfo> {
        val filterMap = mutableMapOf<String, PublicVideoFilterInfo>()
        if (videoIds.isEmpty()) return filterMap

        val idsBuilder = StringBuilder()
        val maxIds = minOf(videoIds.size, 50)
        for (i in 0 until maxIds) {
            if (i > 0) idsBuilder.append(',')
            idsBuilder.append(videoIds[i])
        }

        val endpoint = "$API_BASE_URL/videos?part=contentDetails,status&id=${safeUrlEncode(idsBuilder.toString())}&key=${safeUrlEncode(apiKey)}"
        val connection = openGetConnection(endpoint)
        
        try {
            val statusCode = connection.responseCode
            val body = readResponse(connection, statusCode >= 400)
            if (statusCode != HttpURLConnection.HTTP_OK) {
                throw IllegalStateException(buildApiErrorMessage(body, statusCode))
            }

            val root = JSONObject(body)
            val items = root.optJSONArray("items") ?: return filterMap

            for (i in 0 until items.length()) {
                val item = items.optJSONObject(i) ?: continue
                val id = item.optString("id", "").trim()
                if (id.isEmpty()) continue

                val contentDetails = item.optJSONObject("contentDetails")
                val rawDuration = contentDetails?.optString("duration", "")?.trim() ?: ""
                val durationSeconds = parseYoutubeDurationSeconds(rawDuration)

                val status = item.optJSONObject("status")
                val embeddable = status?.optBoolean("embeddable", true) ?: true
                filterMap[id] = PublicVideoFilterInfo(embeddable, durationSeconds)
            }

            return filterMap
        } finally {
            connection.disconnect()
        }
    }

    private fun parseYoutubeDurationSeconds(rawDuration: String?): Int {
        if (rawDuration.isNullOrEmpty()) return 0

        val hours = extractDurationComponent(rawDuration, 'H')
        val minutes = extractDurationComponent(rawDuration, 'M')
        val seconds = extractDurationComponent(rawDuration, 'S')
        return maxOf(0, (hours * 3600) + (minutes * 60) + seconds)
    }

    private fun formatYoutubeDuration(rawDuration: String?): String {
        if (rawDuration.isNullOrEmpty()) return "--:--"

        val hours = extractDurationComponent(rawDuration, 'H')
        val minutes = extractDurationComponent(rawDuration, 'M')
        val seconds = extractDurationComponent(rawDuration, 'S')

        return if (hours > 0) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%d:%02d", minutes, seconds)
        }
    }

    private fun extractDurationComponent(rawDuration: String, component: Char): Int {
        val markerIndex = rawDuration.indexOf(component)
        if (markerIndex <= 0) return 0

        var start = markerIndex - 1
        while (start >= 0 && rawDuration[start].isDigit()) {
            start--
        }

        val number = rawDuration.substring(start + 1, markerIndex)
        if (number.isEmpty()) return 0

        return try {
            number.toInt()
        } catch (ignored: NumberFormatException) {
            0
        }
    }

    private data class TrackTempData(
        val title: String,
        val artist: String,
        val thumbnailUrl: String
    )

    private data class VideoPlaybackInfo(
        val duration: String,
        val embeddable: Boolean
    )

    private data class PublicVideoFilterInfo(
        val embeddable: Boolean,
        val durationSeconds: Int
    )

    private fun openGetConnection(endpoint: String, authorization: String? = null): HttpURLConnection {
        val url = URL(endpoint)
        val connection = url.openConnection() as HttpURLConnection
        connection.apply {
            requestMethod = "GET"
            connectTimeout = 14000
            readTimeout = 18000
            useCaches = false
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "Sleppify-Android/1.0")
            if (!authorization.isNullOrEmpty()) {
                setRequestProperty("Authorization", authorization)
            }
        }
        return connection
    }

    private fun buildSearchSubtitle(resultType: String, channelTitle: String): String {
        val typeLabel = when (resultType) {
            "playlist" -> "Playlist"
            "channel" -> "Artista"
            else -> "Cancion"
        }

        if (channelTitle.isEmpty()) return typeLabel
        return "$typeLabel • $channelTitle"
    }

    private fun isMusicContent(title: String, durationSeconds: Int): Boolean {
        // Enforce duration limit (70s)
        if (durationSeconds > 0 && durationSeconds < MIN_PUBLIC_MUSIC_DURATION_SECONDS) {
            return false
        }
        
        // Fallback or additional keyword filter
        return isPotentiallyMusic(title)
    }

    private fun isPotentiallyMusic(title: String): Boolean {
        val normalizedTitle = title.lowercase(Locale.US)

        // Specifically block Shorts tags
        if (containsAny(normalizedTitle, "#shorts", " shorts", "shorts ", "/shorts")) return false

        // Block other non-music categories
        if (containsAny(
                normalizedTitle,
                "podcast", "interview", "entrevista", "explica", "explains",
                "reaction", "trailer", "teaser", "documental", "noticias", "news", "official clip"
            )
        ) {
            return false
        }

        return true
    }

    private fun extractYouTubeThumbnail(thumbnails: JSONObject?): String {
        if (thumbnails == null) return ""

        val qualityOrder = arrayOf("maxres", "standard", "high", "medium", "default")
        for (quality in qualityOrder) {
            val url = readThumbnailUrl(thumbnails, quality)
            if (url.isNotEmpty()) return url
        }
        return ""
    }

    private fun readThumbnailUrl(thumbnails: JSONObject, quality: String): String {
        val obj = thumbnails.optJSONObject(quality) ?: return ""
        return obj.optString("url", "").trim()
    }

    private fun buildApiErrorMessage(rawBody: String, statusCode: Int): String {
        val details = parseApiError(rawBody)
        val reasonNormalized = details.reason.lowercase(Locale.US)
        val messageNormalized = details.message.lowercase(Locale.US)

        if (statusCode == 401) return "Token de YouTube expirado o invalido. Reconecta tu cuenta."
        if (statusCode == 403) return buildForbiddenApiErrorMessage(reasonNormalized, messageNormalized, details)

        if (details.message.isNotEmpty()) {
            if (details.reason.isNotEmpty()) {
                return "YouTube API $statusCode (${details.reason}): ${details.message}"
            }
            return "YouTube API $statusCode: ${details.message}"
        }
        return "YouTube API $statusCode: solicitud no valida."
    }

    private fun buildForbiddenApiErrorMessage(
        reasonNormalized: String,
        messageNormalized: String,
        details: ApiErrorDetails
    ): String {
        if (containsAny(reasonNormalized, "quotaexceeded", "dailylimitexceeded", "ratelimitexceeded", "userratelimitexceeded") ||
            containsAny(messageNormalized, "quota", "rate limit", "daily limit")
        ) {
            return "Cuota de YouTube agotada. Intenta mas tarde o revisa la cuota en Google Cloud."
        }

        if (containsAny(reasonNormalized, "accessnotconfigured", "servicedisabled") ||
            containsAny(messageNormalized, "api has not been used", "api is disabled", "service disabled")
        ) {
            return "YouTube Data API no esta habilitada para este proyecto en Google Cloud."
        }

        if (containsAny(reasonNormalized, "insufficientpermissions", "forbidden", "playlistforbidden", "authorizationrequired") ||
            containsAny(messageNormalized, "insufficient permission", "not properly authorized", "youtube.readonly")
        ) {
            return "Faltan permisos OAuth de YouTube. Pulsa Conectar y acepta youtube.readonly."
        }

        if (details.message.isNotEmpty()) {
            val reasonLabel = if (details.reason.isEmpty()) "forbidden" else details.reason
            return "YouTube API 403 ($reasonLabel): ${details.message}"
        }

        return "Sin permiso para YouTube API (403)."
    }

    private fun parseApiError(rawBody: String): ApiErrorDetails {
        if (rawBody.isEmpty()) return ApiErrorDetails("", "")

        try {
            val root = JSONObject(rawBody)
            val error = root.optJSONObject("error") ?: return ApiErrorDetails("", "")

            var message = error.optString("message", "").trim()
            var reason = ""

            val errors = error.optJSONArray("errors")
            if (errors != null && errors.length() > 0) {
                val first = errors.optJSONObject(0)
                if (first != null) {
                    reason = first.optString("reason", "").trim()
                    if (message.isEmpty()) {
                        message = first.optString("message", "").trim()
                    }
                }
            }

            return ApiErrorDetails(reason, message)
        } catch (ignored: Exception) {
            return ApiErrorDetails("", "")
        }
    }

    private fun containsAny(text: String, vararg terms: String): Boolean {
        for (term in terms) {
            if (text.contains(term)) return true
        }
        return false
    }

    private fun readResponse(connection: HttpURLConnection, fromErrorStream: Boolean): String {
        try {
            if (fromErrorStream && connection.errorStream == null) return ""

            val stream = if (fromErrorStream) connection.errorStream else connection.inputStream
            BufferedReader(InputStreamReader(stream, StandardCharsets.UTF_8)).use { reader ->
                val builder = StringBuilder()
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    builder.append(line)
                }
                return builder.toString()
            }
        } catch (ignored: Exception) {
            return ""
        }
    }

    fun getYoutubeReadonlyScope(): String {
        return YT_SCOPE_READONLY
    }

    private fun performInsertPlaylistTrackRequest(token: String, playlistId: String, videoId: String): Boolean {
        val urlString = "$API_BASE_URL/playlistItems?part=snippet"
        val url = URL(urlString)
        val conn = url.openConnection() as HttpURLConnection
        conn.apply {
            requestMethod = "POST"
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Content-Type", "application/json")
            doOutput = true
        }

        val snippet = JSONObject()
        snippet.put("playlistId", playlistId)
        
        val resourceId = JSONObject()
        resourceId.put("kind", "youtube#video")
        resourceId.put("videoId", videoId)
        snippet.put("resourceId", resourceId)

        val root = JSONObject()
        root.put("snippet", snippet)

        conn.outputStream.use { os ->
            val input = root.toString().toByteArray(StandardCharsets.UTF_8)
            os.write(input, 0, input.size)
        }

        val code = conn.responseCode
        return code in 200..299
    }
}
