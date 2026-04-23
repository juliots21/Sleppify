package com.example.sleppify

import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.LinkedHashMap
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class YouTubeMusicService @JvmOverloads constructor(
    private val executor: ExecutorService = SHARED_EXECUTOR
) {

    private val mainHandler = Handler(Looper.getMainLooper())

    // ----- Public interfaces -----

    interface SearchCallback {
        fun onSuccess(tracks: List<TrackResult>)
        fun onError(error: String)
    }

    interface SearchPageCallback {
        fun onSuccess(pageResult: SearchPageResult)
        fun onError(error: String)
    }

    interface PlaylistsCallback {
        fun onSuccess(playlists: List<PlaylistResult>)
        fun onError(error: String)
    }

    interface PlaylistTracksCallback {
        fun onSuccess(tracks: List<PlaylistTrackResult>)
        fun onError(error: String)
    }

    interface SimpleResultCallback {
        fun onResult(success: Boolean, error: String?)
    }

    interface PlaylistMetaCallback {
        fun onSuccess(playlist: PlaylistResult)
        fun onError(error: String)
    }

    // ----- Public data classes (field-accessible from Java via @JvmField) -----

    class TrackResult(
        @JvmField val resultType: String,
        @JvmField val contentId: String,
        @JvmField val title: String,
        @JvmField val subtitle: String,
        @JvmField val thumbnailUrl: String
    ) {
        @JvmField
        val videoId: String = if ("video" == resultType) contentId else ""

        fun isVideo(): Boolean = "video" == resultType && !TextUtils.isEmpty(contentId)

        fun getWatchUrl(): String {
            if (TextUtils.isEmpty(contentId)) return "https://music.youtube.com/"
            return when (resultType) {
                "playlist" -> "https://music.youtube.com/playlist?list=" + safeUrlEncode(contentId)
                "channel" -> "https://www.youtube.com/channel/" + safeUrlEncode(contentId)
                else -> "https://music.youtube.com/watch?v=" + safeUrlEncode(contentId)
            }
        }
    }

    class SearchPageResult(
        @JvmField val tracks: List<TrackResult>,
        @JvmField val nextPageToken: String
    )

    class PlaylistResult(
        @JvmField val playlistId: String,
        @JvmField val title: String,
        @JvmField val ownerName: String,
        @JvmField val itemCount: Int,
        @JvmField val thumbnailUrl: String,
        @JvmField val privacyStatus: String,
        @JvmField val publishedAt: String
    ) {
        fun getOpenUrl(): String =
            "https://music.youtube.com/playlist?list=" + safeUrlEncode(playlistId)
    }

    class PlaylistTrackResult(
        @JvmField val videoId: String,
        @JvmField val title: String,
        @JvmField val artist: String,
        @JvmField val duration: String,
        @JvmField val thumbnailUrl: String
    )

    // ----- Private data holders -----

    private class ApiErrorDetails(val reason: String, val message: String)

    private class TrackTempData(val title: String, val artist: String, val thumbnailUrl: String)

    private class VideoPlaybackInfo(val duration: String, val embeddable: Boolean)

    private class PublicVideoFilterInfo(val embeddable: Boolean, val durationSeconds: Int)

    // ----- Public API -----

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

        val apiKey = (BuildConfig.YOUTUBE_DATA_API_KEY ?: "").trim()
        if (apiKey.isEmpty()) {
            callback.onError("Configura YOUTUBE_DATA_API_KEY para habilitar busqueda.")
            return
        }

        executor.execute {
            try {
                val pageResult = performSearchRequest(normalized, maxOf(1, maxResults), apiKey, pageToken)
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
                var tracks: List<PlaylistTrackResult> =
                    if (SPECIAL_LIKED_VIDEOS_ID == normalizedPlaylistId) {
                        val musicLikes = performMusicLikesTracksRequest(token, maxOf(1, maxResults))
                        if (musicLikes.isEmpty()) {
                            performLikedVideosTracksRequest(token, maxOf(1, maxResults))
                        } else musicLikes
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

    fun getYoutubeReadonlyScope(): String = YT_SCOPE_READONLY

    // ----- HTTP helper -----

    @Throws(Exception::class)
    private inline fun <T> executeGet(
        endpoint: String,
        authorization: String? = null,
        parser: (JSONObject) -> T
    ): T {
        val connection = openGetConnection(endpoint, authorization)
        try {
            val statusCode = connection.responseCode
            val body = readResponse(connection, statusCode >= 400)
            if (statusCode != HttpURLConnection.HTTP_OK) {
                throw IllegalStateException(buildApiErrorMessage(body, statusCode))
            }
            return parser(JSONObject(body))
        } finally {
            connection.disconnect()
        }
    }

    // ----- Network request implementations -----

    @Throws(Exception::class)
    private fun performSearchRequest(
        query: String,
        maxResults: Int,
        apiKey: String,
        pageToken: String?
    ): SearchPageResult {
        var endpoint = "$API_BASE_URL/search?part=snippet" +
                "&type=video" +
                "&videoCategoryId=10" +
                "&videoEmbeddable=true" +
                "&maxResults=" + minOf(50, maxResults) +
                "&q=" + safeUrlEncode(query) +
                "&key=" + safeUrlEncode(apiKey)

        val normalizedPageToken = pageToken?.trim().orEmpty()
        if (normalizedPageToken.isNotEmpty()) {
            endpoint += "&pageToken=" + safeUrlEncode(normalizedPageToken)
        }

        return executeGet(endpoint) { root ->
            val items = root.optJSONArray("items")
            val nextPageToken = root.optString("nextPageToken", "").trim()
            var result = ArrayList<TrackResult>()
            if (items == null) return@executeGet SearchPageResult(result, nextPageToken)

            items.forEachObject { item ->
                val idObject = item.optJSONObject("id") ?: return@forEachObject
                val snippet = item.optJSONObject("snippet") ?: return@forEachObject

                val kind = idObject.optString("kind", "")
                if (!kind.endsWith("#video")) return@forEachObject

                val resultType = "video"
                val contentId = idObject.optString("videoId", "").trim()
                if (contentId.isEmpty()) return@forEachObject

                val title = snippet.optString("title", "").trim()
                if (title.isEmpty()) return@forEachObject

                val channelTitle = snippet.optString("channelTitle", "").trim()
                if (!shouldIncludeMusicSearchResult(title, channelTitle)) return@forEachObject

                val subtitle = buildSearchSubtitle(resultType, channelTitle)
                val thumbnailUrl = extractYouTubeThumbnail(snippet.optJSONObject("thumbnails"))

                result.add(TrackResult(resultType, contentId, title, subtitle, thumbnailUrl))
            }

            if (result.isNotEmpty()) {
                val videoIds = ArrayList<String>()
                for (item in result) {
                    if (item.isVideo() && !TextUtils.isEmpty(item.videoId)) {
                        videoIds.add(item.videoId)
                    }
                }

                if (videoIds.isNotEmpty()) {
                    try {
                        val filterMap = fetchPublicVideoFiltersByIds(apiKey, videoIds)
                        if (filterMap.isNotEmpty()) {
                            val filtered = ArrayList<TrackResult>(result.size)
                            for (item in result) {
                                if (!item.isVideo() || TextUtils.isEmpty(item.videoId)) {
                                    filtered.add(item)
                                    continue
                                }

                                val info = filterMap[item.videoId]
                                if (info == null) {
                                    filtered.add(item)
                                    continue
                                }

                                if (!info.embeddable) continue
                                if (info.durationSeconds in 1 until MIN_PUBLIC_MUSIC_DURATION_SECONDS) continue

                                filtered.add(item)
                            }
                            result = filtered
                        }
                    } catch (_: Exception) {
                        // Si falla el filtro de embebibles, mantenemos resultados para no bloquear búsqueda.
                    }
                }
            }

            SearchPageResult(result, nextPageToken)
        }
    }

    @Throws(Exception::class)
    private fun performMyPlaylistsRequest(accessToken: String, maxResults: Int): List<PlaylistResult> {
        val targetCount = maxOf(1, maxResults)
        var pageToken = ""
        val result = ArrayList<PlaylistResult>()

        while (result.size < targetCount) {
            val pageSize = minOf(YOUTUBE_PAGE_MAX_RESULTS, targetCount - result.size)
            var endpoint = "$API_BASE_URL/playlists?part=snippet,contentDetails,status" +
                    "&mine=true" +
                    "&maxResults=$pageSize"
            if (pageToken.isNotEmpty()) {
                endpoint += "&pageToken=" + safeUrlEncode(pageToken)
            }

            val nextToken = executeGet(endpoint, "Bearer $accessToken") { root ->
                val items = root.optJSONArray("items") ?: JSONArray()
                items.forEachObject { item ->
                    val playlistId = item.optString("id", "").trim()
                    val snippet = item.optJSONObject("snippet") ?: return@forEachObject
                    val contentDetails = item.optJSONObject("contentDetails") ?: return@forEachObject
                    if (playlistId.isEmpty()) return@forEachObject

                    val title = snippet.optString("title", "").trim()
                    if (title.isEmpty()) return@forEachObject

                    val itemCount = contentDetails.optInt("itemCount", 0)
                    val owner = snippet.optString("channelTitle", "").trim()
                    val thumbnailUrl = extractYouTubeThumbnail(snippet.optJSONObject("thumbnails"))
                    val status = item.optJSONObject("status")
                    val privacyStatus = status?.optString("privacyStatus", "")?.trim().orEmpty()
                    val publishedAt = snippet.optString("publishedAt", "").trim()

                    result.add(
                        PlaylistResult(
                            playlistId, title, owner, itemCount,
                            thumbnailUrl, privacyStatus, publishedAt
                        )
                    )
                    if (result.size >= targetCount) return@forEachObject
                }
                root.optString("nextPageToken", "").trim()
            }

            pageToken = nextToken
            if (pageToken.isEmpty()) break
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

    @Throws(Exception::class)
    private fun resolveLikesPlaylistId(accessToken: String): String {
        val endpoint = "$API_BASE_URL/channels?part=contentDetails&mine=true&maxResults=1"
        return executeGet(endpoint, "Bearer $accessToken") { root ->
            val items = root.optJSONArray("items")
            if (items == null || items.length() == 0) return@executeGet ""
            val first = items.optJSONObject(0) ?: return@executeGet ""
            val contentDetails = first.optJSONObject("contentDetails") ?: return@executeGet ""
            val relatedPlaylists = contentDetails.optJSONObject("relatedPlaylists") ?: return@executeGet ""
            relatedPlaylists.optString("likes", "").trim()
        }
    }

    @Throws(Exception::class)
    private fun fetchPlaylistById(accessToken: String, playlistId: String): PlaylistResult? {
        val endpoint = "$API_BASE_URL/playlists?part=snippet,contentDetails,status" +
                "&id=" + safeUrlEncode(playlistId) +
                "&maxResults=1"

        return executeGet<PlaylistResult?>(endpoint, "Bearer $accessToken") { root ->
            val items = root.optJSONArray("items")
            if (items == null || items.length() == 0) return@executeGet null

            val item = items.optJSONObject(0) ?: return@executeGet null

            val resolvedId = item.optString("id", "").trim()
            val snippet = item.optJSONObject("snippet") ?: return@executeGet null
            val contentDetails = item.optJSONObject("contentDetails") ?: return@executeGet null
            if (resolvedId.isEmpty()) return@executeGet null

            var title = snippet.optString("title", "").trim()
            if (title.isEmpty()) title = SPECIAL_LIKED_VIDEOS_TITLE

            val owner = snippet.optString("channelTitle", "").trim()
            val itemCount = maxOf(0, contentDetails.optInt("itemCount", 0))
            val thumbnailUrl = extractYouTubeThumbnail(snippet.optJSONObject("thumbnails"))
            val status = item.optJSONObject("status")
            val privacyStatus = status?.optString("privacyStatus", "")?.trim().orEmpty()
            val publishedAt = snippet.optString("publishedAt", "").trim()

            val lowered = title.lowercase(Locale.US)
            if (lowered.contains("liked") || lowered.contains("gusta")) {
                title = SPECIAL_LIKED_VIDEOS_TITLE
            }

            PlaylistResult(
                resolvedId, title, owner, itemCount,
                thumbnailUrl, privacyStatus, publishedAt
            )
        }
    }

    @Throws(Exception::class)
    private fun buildFallbackLikedVideosCollection(accessToken: String): PlaylistResult? {
        val endpoint = "$API_BASE_URL/videos?part=snippet&myRating=like&maxResults=1"
        return executeGet<PlaylistResult?>(endpoint, "Bearer $accessToken") { root ->
            val pageInfo = root.optJSONObject("pageInfo")
            val total = if (pageInfo == null) 0 else maxOf(0, pageInfo.optInt("totalResults", 0))

            val items = root.optJSONArray("items")
            if (total <= 0 && (items == null || items.length() == 0)) return@executeGet null

            var thumbnailUrl = ""
            if (items != null && items.length() > 0) {
                val first = items.optJSONObject(0)
                if (first != null) {
                    val snippet = first.optJSONObject("snippet")
                    if (snippet != null) {
                        thumbnailUrl = extractYouTubeThumbnail(snippet.optJSONObject("thumbnails"))
                    }
                }
            }

            PlaylistResult(
                SPECIAL_LIKED_VIDEOS_ID,
                SPECIAL_LIKED_VIDEOS_TITLE,
                "Tu cuenta de YouTube",
                total,
                thumbnailUrl,
                "private",
                ""
            )
        }
    }

    private fun upsertPlaylistAtTop(list: MutableList<PlaylistResult>, playlist: PlaylistResult) {
        val existingIndex = list.indexOfFirst { it.playlistId == playlist.playlistId }
        if (existingIndex >= 0) list.removeAt(existingIndex)
        list.add(0, playlist)
    }

    @Throws(Exception::class)
    private fun performPlaylistTracksRequest(
        accessToken: String,
        playlistId: String,
        maxResults: Int
    ): List<PlaylistTrackResult> {
        val targetCount = maxOf(1, maxResults)
        var pageToken = ""
        val videoMap = LinkedHashMap<String, TrackTempData>()

        while (videoMap.size < targetCount) {
            val pageSize = minOf(YOUTUBE_PAGE_MAX_RESULTS, targetCount - videoMap.size)
            var endpoint = "$API_BASE_URL/playlistItems?part=snippet,contentDetails" +
                    "&playlistId=" + safeUrlEncode(playlistId) +
                    "&maxResults=$pageSize"
            if (pageToken.isNotEmpty()) {
                endpoint += "&pageToken=" + safeUrlEncode(pageToken)
            }

            val nextToken = executeGet(endpoint, "Bearer $accessToken") { root ->
                val items = root.optJSONArray("items")
                items?.forEachObject { item ->
                    val contentDetails = item.optJSONObject("contentDetails") ?: return@forEachObject
                    val snippet = item.optJSONObject("snippet") ?: return@forEachObject

                    val videoId = contentDetails.optString("videoId", "").trim()
                    if (videoId.isEmpty()) return@forEachObject

                    val title = snippet.optString("title", "").trim()
                    if (title.isEmpty() ||
                        "deleted video".equals(title, ignoreCase = true) ||
                        "private video".equals(title, ignoreCase = true)
                    ) return@forEachObject

                    var artist = snippet.optString("videoOwnerChannelTitle", "").trim()
                    if (artist.isEmpty()) artist = snippet.optString("channelTitle", "").trim()
                    if (artist.isEmpty()) artist = "Unknown artist"

                    val thumbnailUrl = extractYouTubeThumbnail(snippet.optJSONObject("thumbnails"))
                    videoMap[videoId] = TrackTempData(title, artist, thumbnailUrl)
                    if (videoMap.size >= targetCount) return@forEachObject
                }
                root.optString("nextPageToken", "").trim()
            }

            pageToken = nextToken
            if (pageToken.isEmpty()) break
        }

        val result = ArrayList<PlaylistTrackResult>()
        val playbackInfoMap = fetchVideoPlaybackInfoByIdsInBatches(accessToken, ArrayList(videoMap.keys))
        for ((videoId, data) in videoMap) {
            val playbackInfo = playbackInfoMap[videoId]
            if (playbackInfo != null && !playbackInfo.embeddable) continue

            val durationSeconds = parseYoutubeDurationSeconds(playbackInfo?.duration)
            if (durationSeconds in 1 until MIN_PUBLIC_MUSIC_DURATION_SECONDS) continue
            if (!shouldIncludeMusicSearchResult(data.title, data.artist)) continue

            var duration = formatYoutubeDuration(playbackInfo?.duration)
            if (TextUtils.isEmpty(duration)) duration = "--:--"

            result.add(PlaylistTrackResult(videoId, data.title, data.artist, duration, data.thumbnailUrl))
            if (result.size >= targetCount) break
        }

        return result
    }

    @Throws(Exception::class)
    private fun performLikedVideosTracksRequest(
        accessToken: String,
        maxResults: Int
    ): List<PlaylistTrackResult> {
        val targetCount = maxOf(1, maxResults)
        var pageToken = ""
        val seenVideoIds = HashSet<String>()
        val result = ArrayList<PlaylistTrackResult>()

        while (result.size < targetCount) {
            val pageSize = minOf(YOUTUBE_PAGE_MAX_RESULTS, targetCount - result.size)
            var endpoint = "$API_BASE_URL/videos?part=snippet,contentDetails,status" +
                    "&myRating=like" +
                    "&maxResults=$pageSize"
            if (pageToken.isNotEmpty()) {
                endpoint += "&pageToken=" + safeUrlEncode(pageToken)
            }

            val nextToken = executeGet(endpoint, "Bearer $accessToken") { root ->
                val items = root.optJSONArray("items")
                items?.forEachObject { item ->
                    val videoId = item.optString("id", "").trim()
                    val snippet = item.optJSONObject("snippet") ?: return@forEachObject
                    val contentDetails = item.optJSONObject("contentDetails")
                    val status = item.optJSONObject("status")
                    if (videoId.isEmpty() || !seenVideoIds.add(videoId)) return@forEachObject

                    if (status != null && !status.optBoolean("embeddable", true)) return@forEachObject

                    val title = snippet.optString("title", "").trim()
                    if (title.isEmpty() ||
                        "deleted video".equals(title, ignoreCase = true) ||
                        "private video".equals(title, ignoreCase = true)
                    ) return@forEachObject

                    var artist = snippet.optString("channelTitle", "").trim()
                    if (artist.isEmpty()) artist = "Unknown artist"

                    val rawDuration = contentDetails?.optString("duration", "")?.trim().orEmpty()
                    val durationSec = parseYoutubeDurationSeconds(rawDuration)
                    if (durationSec in 1 until MIN_PUBLIC_MUSIC_DURATION_SECONDS) return@forEachObject
                    if (!shouldIncludeMusicSearchResult(title, artist)) return@forEachObject

                    var duration = formatYoutubeDuration(rawDuration)
                    if (duration.isEmpty()) duration = "--:--"

                    val thumbnailUrl = extractYouTubeThumbnail(snippet.optJSONObject("thumbnails"))
                    result.add(PlaylistTrackResult(videoId, title, artist, duration, thumbnailUrl))
                    if (result.size >= targetCount) return@forEachObject
                }
                root.optString("nextPageToken", "").trim()
            }

            pageToken = nextToken
            if (pageToken.isEmpty()) break
        }

        return result
    }

    private fun performMusicLikesTracksRequest(
        accessToken: String,
        maxResults: Int
    ): List<PlaylistTrackResult> {
        return try {
            performPlaylistTracksRequest(accessToken, MUSIC_LIKES_PLAYLIST_ID, maxResults)
        } catch (_: Exception) {
            ArrayList()
        }
    }

    private fun toSpecialLikesCollection(
        source: PlaylistResult,
        ownerName: String
    ): PlaylistResult = PlaylistResult(
        SPECIAL_LIKED_VIDEOS_ID,
        SPECIAL_LIKED_VIDEOS_TITLE,
        ownerName,
        source.itemCount,
        source.thumbnailUrl,
        source.privacyStatus,
        source.publishedAt
    )

    @Throws(Exception::class)
    private fun fetchVideoPlaybackInfoByIdsInBatches(
        accessToken: String,
        videoIds: List<String>
    ): Map<String, VideoPlaybackInfo> {
        val result = HashMap<String, VideoPlaybackInfo>()
        if (videoIds.isEmpty()) return result

        var start = 0
        while (start < videoIds.size) {
            val end = minOf(start + YOUTUBE_PAGE_MAX_RESULTS, videoIds.size)
            val batch = ArrayList(videoIds.subList(start, end))
            result.putAll(fetchVideoPlaybackInfoByIds(accessToken, batch))
            start += YOUTUBE_PAGE_MAX_RESULTS
        }
        return result
    }

    @Throws(Exception::class)
    private fun fetchVideoPlaybackInfoByIds(
        accessToken: String,
        videoIds: List<String>
    ): Map<String, VideoPlaybackInfo> {
        if (videoIds.isEmpty()) return emptyMap()

        val ids = videoIds.take(50).joinToString(",")
        val endpoint = "$API_BASE_URL/videos?part=contentDetails,status" +
                "&id=" + safeUrlEncode(ids)

        return executeGet(endpoint, "Bearer $accessToken") { root ->
            val playbackInfoMap = HashMap<String, VideoPlaybackInfo>()
            val items = root.optJSONArray("items") ?: return@executeGet playbackInfoMap

            items.forEachObject { item ->
                val id = item.optString("id", "").trim()
                if (id.isEmpty()) return@forEachObject

                val contentDetails = item.optJSONObject("contentDetails")
                val status = item.optJSONObject("status")

                val rawDuration = contentDetails?.optString("duration", "")?.trim().orEmpty()
                val duration = formatYoutubeDuration(rawDuration)
                val embeddable = status == null || status.optBoolean("embeddable", true)
                playbackInfoMap[id] = VideoPlaybackInfo(duration, embeddable)
            }
            playbackInfoMap
        }
    }

    @Throws(Exception::class)
    private fun fetchPublicVideoFiltersByIds(
        apiKey: String,
        videoIds: List<String>
    ): Map<String, PublicVideoFilterInfo> {
        if (videoIds.isEmpty()) return emptyMap()

        val ids = videoIds.take(50).joinToString(",")
        val endpoint = "$API_BASE_URL/videos?part=contentDetails,status" +
                "&id=" + safeUrlEncode(ids) +
                "&key=" + safeUrlEncode(apiKey)

        return executeGet(endpoint) { root ->
            val filterMap = HashMap<String, PublicVideoFilterInfo>()
            val items = root.optJSONArray("items") ?: return@executeGet filterMap

            items.forEachObject { item ->
                val id = item.optString("id", "").trim()
                if (id.isEmpty()) return@forEachObject

                val contentDetails = item.optJSONObject("contentDetails")
                val rawDuration = contentDetails?.optString("duration", "")?.trim().orEmpty()
                val durationSeconds = parseYoutubeDurationSeconds(rawDuration)

                val status = item.optJSONObject("status")
                val embeddable = status == null || status.optBoolean("embeddable", true)
                filterMap[id] = PublicVideoFilterInfo(embeddable, durationSeconds)
            }
            filterMap
        }
    }

    private fun parseYoutubeDurationSeconds(rawDuration: String?): Int {
        if (rawDuration.isNullOrEmpty()) return 0
        val hours = extractDurationComponent(rawDuration, 'H')
        val minutes = extractDurationComponent(rawDuration, 'M')
        val seconds = extractDurationComponent(rawDuration, 'S')
        return maxOf(0, hours * 3600 + minutes * 60 + seconds)
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
        } catch (_: NumberFormatException) {
            0
        }
    }

    @Throws(Exception::class)
    private fun openGetConnection(endpoint: String, authorization: String? = null): HttpURLConnection {
        val url = URL(endpoint)
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 14000
        connection.readTimeout = 18000
        connection.useCaches = false
        connection.setRequestProperty("Accept", "application/json")
        connection.setRequestProperty("User-Agent", "Sleppify-Android/1.0")
        if (!authorization.isNullOrEmpty()) {
            connection.setRequestProperty("Authorization", authorization)
        }
        return connection
    }

    private fun buildSearchSubtitle(resultType: String, channelTitle: String): String {
        val typeLabel = when (resultType) {
            "playlist" -> "Playlist"
            "channel" -> "Artista"
            else -> "Cancion"
        }
        return if (channelTitle.isEmpty()) typeLabel else "$typeLabel • $channelTitle"
    }

    private fun shouldIncludeMusicSearchResult(title: String, channelTitle: String?): Boolean {
        val normalizedTitle = title.lowercase(Locale.US)

        if (containsAny(normalizedTitle, "#shorts", " shorts", "shorts ")) return false

        if (containsAny(
                normalizedTitle,
                "podcast", "interview", "entrevista", "explica", "explains",
                "reaction", "trailer", "teaser", "documental", "noticias", "news"
            )
        ) return false

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

        if (statusCode == 401) {
            return "Token de YouTube expirado o invalido. Reconecta tu cuenta."
        }
        if (statusCode == 403) {
            return buildForbiddenApiErrorMessage(reasonNormalized, messageNormalized, details)
        }

        if (details.message.isNotEmpty()) {
            return if (details.reason.isNotEmpty()) {
                "YouTube API $statusCode (${details.reason}): ${details.message}"
            } else {
                "YouTube API $statusCode: ${details.message}"
            }
        }
        return "YouTube API $statusCode: solicitud no valida."
    }

    private fun buildForbiddenApiErrorMessage(
        reasonNormalized: String,
        messageNormalized: String,
        details: ApiErrorDetails
    ): String {
        if (containsAny(
                reasonNormalized,
                "quotaexceeded", "dailylimitexceeded", "ratelimitexceeded", "userratelimitexceeded"
            ) ||
            containsAny(messageNormalized, "quota", "rate limit", "daily limit")
        ) {
            return "Cuota de YouTube agotada. Intenta mas tarde o revisa la cuota en Google Cloud."
        }

        if (containsAny(reasonNormalized, "accessnotconfigured", "servicedisabled") ||
            containsAny(messageNormalized, "api has not been used", "api is disabled", "service disabled")
        ) {
            return "YouTube Data API no esta habilitada para este proyecto en Google Cloud."
        }

        if (containsAny(
                reasonNormalized,
                "insufficientpermissions", "forbidden", "playlistforbidden", "authorizationrequired"
            ) ||
            containsAny(
                messageNormalized,
                "insufficient permission", "not properly authorized", "youtube.readonly"
            )
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

        return try {
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

            ApiErrorDetails(reason, message)
        } catch (_: Exception) {
            ApiErrorDetails("", "")
        }
    }

    private fun readResponse(connection: HttpURLConnection, fromErrorStream: Boolean): String {
        return try {
            if (fromErrorStream && connection.errorStream == null) return ""

            val stream = if (fromErrorStream) connection.errorStream else connection.inputStream
            BufferedReader(InputStreamReader(stream, StandardCharsets.UTF_8)).use { reader ->
                val builder = StringBuilder()
                var line = reader.readLine()
                while (line != null) {
                    builder.append(line)
                    line = reader.readLine()
                }
                builder.toString()
            }
        } catch (_: Exception) {
            ""
        }
    }

    @Throws(Exception::class)
    private fun performInsertPlaylistTrackRequest(
        token: String,
        playlistId: String,
        videoId: String
    ): Boolean {
        val url = URL("$API_BASE_URL/playlistItems?part=snippet")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Authorization", "Bearer $token")
        conn.setRequestProperty("Content-Type", "application/json")
        conn.doOutput = true

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

    companion object {
        private const val API_BASE_URL = "https://www.googleapis.com/youtube/v3"
        private const val YT_SCOPE_READONLY = "https://www.googleapis.com/auth/youtube.readonly"
        private const val MUSIC_LIKES_PLAYLIST_ID = "LM"

        @JvmField
        val SPECIAL_LIKED_VIDEOS_ID = "__liked_videos__"

        private const val SPECIAL_LIKED_VIDEOS_TITLE = "Me gusta"
        private const val YOUTUBE_PAGE_MAX_RESULTS = 50
        private const val MIN_PUBLIC_MUSIC_DURATION_SECONDS = 70

        private val SHARED_EXECUTOR: ExecutorService = Executors.newFixedThreadPool(3)

        private fun safeUrlEncode(value: String): String = try {
            URLEncoder.encode(value, StandardCharsets.UTF_8.name())
        } catch (_: Exception) {
            value
        }

        private fun containsAny(text: String, vararg terms: String): Boolean {
            for (term in terms) if (text.contains(term)) return true
            return false
        }

        private inline fun JSONArray.forEachObject(action: (JSONObject) -> Unit) {
            for (i in 0 until length()) {
                val obj = optJSONObject(i) ?: continue
                action(obj)
            }
        }
    }
}
