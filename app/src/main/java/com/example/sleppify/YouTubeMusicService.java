package com.example.sleppify;

import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class YouTubeMusicService {

    private static final String API_BASE_URL = "https://www.googleapis.com/youtube/v3";
    private static final String YT_SCOPE_READONLY = "https://www.googleapis.com/auth/youtube.readonly";
    private static final String MUSIC_LIKES_PLAYLIST_ID = "LM";
    public static final String SPECIAL_LIKED_VIDEOS_ID = "__liked_videos__";
    private static final String SPECIAL_LIKED_VIDEOS_TITLE = "Me gusta";
    private static final int YOUTUBE_PAGE_MAX_RESULTS = 50;
    private static final int MIN_PUBLIC_MUSIC_DURATION_SECONDS = 70;
    private static final ExecutorService SHARED_EXECUTOR = Executors.newFixedThreadPool(3);

    private final ExecutorService executor = SHARED_EXECUTOR;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private static final class ApiErrorDetails {
        final String reason;
        final String message;

        ApiErrorDetails(@NonNull String reason, @NonNull String message) {
            this.reason = reason;
            this.message = message;
        }
    }

    public interface SearchCallback {
        void onSuccess(@NonNull List<TrackResult> tracks);
        void onError(@NonNull String error);
    }

    public interface PlaylistsCallback {
        void onSuccess(@NonNull List<PlaylistResult> playlists);
        void onError(@NonNull String error);
    }

    public interface PlaylistTracksCallback {
        void onSuccess(@NonNull List<PlaylistTrackResult> tracks);
        void onError(@NonNull String error);
    }

    public interface PlaylistMetaCallback {
        void onSuccess(@NonNull PlaylistResult playlist);
        void onError(@NonNull String error);
    }

    public static final class TrackResult {
        public final String resultType;
        public final String contentId;
        public final String videoId;
        public final String title;
        public final String subtitle;
        public final String thumbnailUrl;

        public TrackResult(
                @NonNull String resultType,
                @NonNull String contentId,
                @NonNull String title,
                @NonNull String subtitle,
                @NonNull String thumbnailUrl
        ) {
            this.resultType = resultType;
            this.contentId = contentId;
            this.videoId = "video".equals(resultType) ? contentId : "";
            this.title = title;
            this.subtitle = subtitle;
            this.thumbnailUrl = thumbnailUrl;
        }

        public boolean isVideo() {
            return "video".equals(resultType) && !TextUtils.isEmpty(contentId);
        }

        @NonNull
        public String getWatchUrl() {
            if (TextUtils.isEmpty(contentId)) {
                return "https://music.youtube.com/";
            }

            if ("playlist".equals(resultType)) {
                return "https://music.youtube.com/playlist?list=" + safeUrlEncode(contentId);
            }
            if ("channel".equals(resultType)) {
                return "https://www.youtube.com/channel/" + safeUrlEncode(contentId);
            }
            return "https://music.youtube.com/watch?v=" + safeUrlEncode(contentId);
        }
    }

    public static final class PlaylistResult {
        public final String playlistId;
        public final String title;
        public final String ownerName;
        public final int itemCount;
        public final String thumbnailUrl;
        public final String privacyStatus;
        public final String publishedAt;

        public PlaylistResult(
                @NonNull String playlistId,
                @NonNull String title,
                @NonNull String ownerName,
                int itemCount,
            @NonNull String thumbnailUrl,
            @NonNull String privacyStatus,
            @NonNull String publishedAt
        ) {
            this.playlistId = playlistId;
            this.title = title;
            this.ownerName = ownerName;
            this.itemCount = itemCount;
            this.thumbnailUrl = thumbnailUrl;
            this.privacyStatus = privacyStatus;
            this.publishedAt = publishedAt;
        }

        @NonNull
        public String getOpenUrl() {
            return "https://music.youtube.com/playlist?list=" + safeUrlEncode(playlistId);
        }
    }

    public static final class PlaylistTrackResult {
        public final String videoId;
        public final String title;
        public final String artist;
        public final String duration;
        public final String thumbnailUrl;

        public PlaylistTrackResult(
                @NonNull String videoId,
                @NonNull String title,
                @NonNull String artist,
                @NonNull String duration,
                @NonNull String thumbnailUrl
        ) {
            this.videoId = videoId;
            this.title = title;
            this.artist = artist;
            this.duration = duration;
            this.thumbnailUrl = thumbnailUrl;
        }
    }

    public void searchTracks(@NonNull String query, int maxResults, @NonNull SearchCallback callback) {
        String normalized = query.trim();
        if (normalized.isEmpty()) {
            callback.onError("Escribe algo para buscar.");
            return;
        }

        String apiKey = BuildConfig.YOUTUBE_DATA_API_KEY == null
                ? ""
                : BuildConfig.YOUTUBE_DATA_API_KEY.trim();
        if (apiKey.isEmpty()) {
            callback.onError("Configura YOUTUBE_DATA_API_KEY para habilitar busqueda.");
            return;
        }

        executor.execute(() -> {
            try {
                List<TrackResult> tracks = performSearchRequest(normalized, Math.max(1, maxResults), apiKey);
                mainHandler.post(() -> callback.onSuccess(tracks));
            } catch (Exception e) {
                String error = e.getMessage() == null ? "No se pudo completar la busqueda." : e.getMessage();
                mainHandler.post(() -> callback.onError(error));
            }
        });
    }

    public void fetchMyPlaylists(@NonNull String accessToken, int maxResults, @NonNull PlaylistsCallback callback) {
        String token = accessToken.trim();
        if (token.isEmpty()) {
            callback.onError("No hay token OAuth para cargar la biblioteca.");
            return;
        }

        executor.execute(() -> {
            try {
                List<PlaylistResult> playlists = performMyPlaylistsRequest(token, Math.max(1, maxResults));
                mainHandler.post(() -> callback.onSuccess(playlists));
            } catch (Exception e) {
                String error = e.getMessage() == null ? "No se pudo cargar la biblioteca." : e.getMessage();
                mainHandler.post(() -> callback.onError(error));
            }
        });
    }

    public void fetchPlaylistTracks(
            @NonNull String accessToken,
            @NonNull String playlistId,
            int maxResults,
            @NonNull PlaylistTracksCallback callback
    ) {
        String token = accessToken.trim();
        if (token.isEmpty()) {
            callback.onError("No hay token OAuth para cargar canciones.");
            return;
        }

        String normalizedPlaylistId = playlistId.trim();
        if (normalizedPlaylistId.isEmpty()) {
            callback.onError("Playlist invalida.");
            return;
        }

        executor.execute(() -> {
            try {
                List<PlaylistTrackResult> tracks;
                if (SPECIAL_LIKED_VIDEOS_ID.equals(normalizedPlaylistId)) {
                    tracks = performMusicLikesTracksRequest(token, Math.max(1, maxResults));
                    if (tracks.isEmpty()) {
                        tracks = performLikedVideosTracksRequest(token, Math.max(1, maxResults));
                    }
                } else {
                    tracks = performPlaylistTracksRequest(
                            token,
                            normalizedPlaylistId,
                            Math.max(1, maxResults)
                    );
                }
                List<PlaylistTrackResult> finalTracks = tracks;
                mainHandler.post(() -> callback.onSuccess(finalTracks));
            } catch (Exception e) {
                String error = e.getMessage() == null ? "No se pudo cargar canciones." : e.getMessage();
                mainHandler.post(() -> callback.onError(error));
            }
        });
    }

    public void fetchPlaylistMeta(
            @NonNull String accessToken,
            @NonNull String playlistId,
            @NonNull PlaylistMetaCallback callback
    ) {
        String token = accessToken.trim();
        String normalizedPlaylistId = playlistId.trim();
        if (token.isEmpty() || normalizedPlaylistId.isEmpty()) {
            callback.onError("Sin datos para leer metadata de playlist.");
            return;
        }

        executor.execute(() -> {
            try {
                PlaylistResult result = fetchPlaylistById(token, normalizedPlaylistId);
                if (result == null) {
                    mainHandler.post(() -> callback.onError("No se encontro metadata de playlist."));
                    return;
                }
                mainHandler.post(() -> callback.onSuccess(result));
            } catch (Exception e) {
                String error = e.getMessage() == null ? "No se pudo leer metadata de playlist." : e.getMessage();
                mainHandler.post(() -> callback.onError(error));
            }
        });
    }

    @NonNull
    private List<TrackResult> performSearchRequest(
            @NonNull String query,
            int maxResults,
            @NonNull String apiKey
    ) throws Exception {
        String endpoint = API_BASE_URL
                + "/search?part=snippet"
            + "&type=video"
            + "&videoCategoryId=10"
            + "&videoEmbeddable=true"
                + "&maxResults=" + Math.min(50, maxResults)
                + "&q=" + safeUrlEncode(query)
                + "&key=" + safeUrlEncode(apiKey);

        HttpURLConnection connection = openGetConnection(endpoint);
        try {
            int statusCode = connection.getResponseCode();
            String body = readResponse(connection, statusCode >= 400);
            if (statusCode != HttpURLConnection.HTTP_OK) {
                throw new IllegalStateException(buildApiErrorMessage(body, statusCode));
            }

            JSONObject root = new JSONObject(body);
            JSONArray items = root.optJSONArray("items");
            List<TrackResult> result = new ArrayList<>();
            if (items == null) {
                return result;
            }

            for (int i = 0; i < items.length(); i++) {
                JSONObject item = items.optJSONObject(i);
                if (item == null) {
                    continue;
                }

                JSONObject idObject = item.optJSONObject("id");
                JSONObject snippet = item.optJSONObject("snippet");
                if (idObject == null || snippet == null) {
                    continue;
                }

                String kind = idObject.optString("kind", "");
                if (!kind.endsWith("#video")) {
                    continue;
                }

                String resultType = "video";
                String contentId = idObject.optString("videoId", "").trim();

                if (contentId.isEmpty()) {
                    continue;
                }

                String title = snippet.optString("title", "").trim();
                if (title.isEmpty()) {
                    continue;
                }

                String channelTitle = snippet.optString("channelTitle", "").trim();
                if (!shouldIncludeMusicSearchResult(title, channelTitle)) {
                    continue;
                }
                String subtitle = buildSearchSubtitle(resultType, channelTitle);
                String thumbnailUrl = extractYouTubeThumbnail(snippet.optJSONObject("thumbnails"));

                result.add(new TrackResult(resultType, contentId, title, subtitle, thumbnailUrl));
            }

            if (!result.isEmpty()) {
                ArrayList<String> videoIds = new ArrayList<>();
                for (TrackResult item : result) {
                    if (item != null && item.isVideo() && !TextUtils.isEmpty(item.videoId)) {
                        videoIds.add(item.videoId);
                    }
                }

                if (!videoIds.isEmpty()) {
                    try {
                        Map<String, PublicVideoFilterInfo> filterMap = fetchPublicVideoFiltersByIds(apiKey, videoIds);
                        if (!filterMap.isEmpty()) {
                            List<TrackResult> filtered = new ArrayList<>(result.size());
                            for (TrackResult item : result) {
                                if (item == null || !item.isVideo() || TextUtils.isEmpty(item.videoId)) {
                                    filtered.add(item);
                                    continue;
                                }

                                PublicVideoFilterInfo info = filterMap.get(item.videoId);
                                if (info == null) {
                                    filtered.add(item);
                                    continue;
                                }

                                if (!info.embeddable) {
                                    continue;
                                }

                                if (info.durationSeconds > 0
                                        && info.durationSeconds < MIN_PUBLIC_MUSIC_DURATION_SECONDS) {
                                    continue;
                                }

                                filtered.add(item);
                            }
                            result = filtered;
                        }
                    } catch (Exception ignored) {
                        // Si falla el filtro de embebibles, mantenemos resultados para no bloquear búsqueda.
                    }
                }
            }

            return result;
        } finally {
            connection.disconnect();
        }
    }

    @NonNull
    private List<PlaylistResult> performMyPlaylistsRequest(
            @NonNull String accessToken,
            int maxResults
    ) throws Exception {
        int targetCount = Math.max(1, maxResults);
        String pageToken = "";
        List<PlaylistResult> result = new ArrayList<>();

        while (result.size() < targetCount) {
            int pageSize = Math.min(YOUTUBE_PAGE_MAX_RESULTS, targetCount - result.size());
            String endpoint = API_BASE_URL
                    + "/playlists?part=snippet,contentDetails,status"
                    + "&mine=true"
                    + "&maxResults=" + pageSize;
            if (!pageToken.isEmpty()) {
                endpoint += "&pageToken=" + safeUrlEncode(pageToken);
            }

            HttpURLConnection connection = openGetConnection(endpoint, "Bearer " + accessToken);
            try {
                int statusCode = connection.getResponseCode();
                String body = readResponse(connection, statusCode >= 400);
                if (statusCode != HttpURLConnection.HTTP_OK) {
                    throw new IllegalStateException(buildApiErrorMessage(body, statusCode));
                }

                JSONObject root = new JSONObject(body);
                JSONArray items = root.optJSONArray("items");
                if (items == null) {
                    items = new JSONArray();
                }

                for (int i = 0; i < items.length(); i++) {
                    JSONObject item = items.optJSONObject(i);
                    if (item == null) {
                        continue;
                    }

                    String playlistId = item.optString("id", "").trim();
                    JSONObject snippet = item.optJSONObject("snippet");
                    JSONObject contentDetails = item.optJSONObject("contentDetails");
                    if (playlistId.isEmpty() || snippet == null || contentDetails == null) {
                        continue;
                    }

                    String title = snippet.optString("title", "").trim();
                    if (title.isEmpty()) {
                        continue;
                    }

                    int itemCount = contentDetails.optInt("itemCount", 0);
                    String owner = snippet.optString("channelTitle", "").trim();
                    String thumbnailUrl = extractYouTubeThumbnail(snippet.optJSONObject("thumbnails"));
                    JSONObject status = item.optJSONObject("status");
                    String privacyStatus = status == null ? "" : status.optString("privacyStatus", "").trim();
                    String publishedAt = snippet.optString("publishedAt", "").trim();

                    result.add(new PlaylistResult(
                            playlistId,
                            title,
                            owner,
                            itemCount,
                            thumbnailUrl,
                            privacyStatus,
                            publishedAt
                    ));
                    if (result.size() >= targetCount) {
                        break;
                    }
                }

                pageToken = root.optString("nextPageToken", "").trim();
                if (pageToken.isEmpty()) {
                    break;
                }
            } finally {
                connection.disconnect();
            }
        }

        PlaylistResult likesResult = null;
        PlaylistResult musicLikes = fetchPlaylistById(accessToken, MUSIC_LIKES_PLAYLIST_ID);
        if (musicLikes != null) {
            likesResult = toSpecialLikesCollection(musicLikes, "Tu cuenta de YouTube Music");
        }

        if (likesResult == null) {
            String likesPlaylistId = resolveLikesPlaylistId(accessToken);
            if (!likesPlaylistId.isEmpty()) {
                PlaylistResult youtubeLikes = fetchPlaylistById(accessToken, likesPlaylistId);
                if (youtubeLikes != null) {
                    likesResult = toSpecialLikesCollection(youtubeLikes, "Tu cuenta de YouTube");
                }
            }
        }

        if (likesResult == null) {
            likesResult = buildFallbackLikedVideosCollection(accessToken);
        }

        if (likesResult != null) {
            upsertPlaylistAtTop(result, likesResult);
        }

        return result;
    }

    @NonNull
    private String resolveLikesPlaylistId(@NonNull String accessToken) throws Exception {
        String endpoint = API_BASE_URL + "/channels?part=contentDetails&mine=true&maxResults=1";

        HttpURLConnection connection = openGetConnection(endpoint, "Bearer " + accessToken);
        try {
            int statusCode = connection.getResponseCode();
            String body = readResponse(connection, statusCode >= 400);
            if (statusCode != HttpURLConnection.HTTP_OK) {
                throw new IllegalStateException(buildApiErrorMessage(body, statusCode));
            }

            JSONObject root = new JSONObject(body);
            JSONArray items = root.optJSONArray("items");
            if (items == null || items.length() == 0) {
                return "";
            }

            JSONObject first = items.optJSONObject(0);
            if (first == null) {
                return "";
            }

            JSONObject contentDetails = first.optJSONObject("contentDetails");
            if (contentDetails == null) {
                return "";
            }

            JSONObject relatedPlaylists = contentDetails.optJSONObject("relatedPlaylists");
            if (relatedPlaylists == null) {
                return "";
            }

            return relatedPlaylists.optString("likes", "").trim();
        } finally {
            connection.disconnect();
        }
    }

    @Nullable
    private PlaylistResult fetchPlaylistById(@NonNull String accessToken, @NonNull String playlistId) throws Exception {
        String endpoint = API_BASE_URL
            + "/playlists?part=snippet,contentDetails,status"
                + "&id=" + safeUrlEncode(playlistId)
                + "&maxResults=1";

        HttpURLConnection connection = openGetConnection(endpoint, "Bearer " + accessToken);
        try {
            int statusCode = connection.getResponseCode();
            String body = readResponse(connection, statusCode >= 400);
            if (statusCode != HttpURLConnection.HTTP_OK) {
                throw new IllegalStateException(buildApiErrorMessage(body, statusCode));
            }

            JSONObject root = new JSONObject(body);
            JSONArray items = root.optJSONArray("items");
            if (items == null || items.length() == 0) {
                return null;
            }

            JSONObject item = items.optJSONObject(0);
            if (item == null) {
                return null;
            }

            String resolvedId = item.optString("id", "").trim();
            JSONObject snippet = item.optJSONObject("snippet");
            JSONObject contentDetails = item.optJSONObject("contentDetails");
            if (resolvedId.isEmpty() || snippet == null || contentDetails == null) {
                return null;
            }

            String title = snippet.optString("title", "").trim();
            if (title.isEmpty()) {
                title = SPECIAL_LIKED_VIDEOS_TITLE;
            }

            String owner = snippet.optString("channelTitle", "").trim();
            int itemCount = Math.max(0, contentDetails.optInt("itemCount", 0));
            String thumbnailUrl = extractYouTubeThumbnail(snippet.optJSONObject("thumbnails"));
            JSONObject status = item.optJSONObject("status");
            String privacyStatus = status == null ? "" : status.optString("privacyStatus", "").trim();
            String publishedAt = snippet.optString("publishedAt", "").trim();

            if (title.toLowerCase(Locale.US).contains("liked")
                    || title.toLowerCase(Locale.US).contains("gusta")) {
                title = SPECIAL_LIKED_VIDEOS_TITLE;
            }

                return new PlaylistResult(
                    resolvedId,
                    title,
                    owner,
                    itemCount,
                    thumbnailUrl,
                    privacyStatus,
                    publishedAt
                );
        } finally {
            connection.disconnect();
        }
    }

    @Nullable
    private PlaylistResult buildFallbackLikedVideosCollection(@NonNull String accessToken) throws Exception {
        String endpoint = API_BASE_URL
                + "/videos?part=snippet"
                + "&myRating=like"
                + "&maxResults=1";

        HttpURLConnection connection = openGetConnection(endpoint, "Bearer " + accessToken);
        try {
            int statusCode = connection.getResponseCode();
            String body = readResponse(connection, statusCode >= 400);
            if (statusCode != HttpURLConnection.HTTP_OK) {
                throw new IllegalStateException(buildApiErrorMessage(body, statusCode));
            }

            JSONObject root = new JSONObject(body);
            JSONObject pageInfo = root.optJSONObject("pageInfo");
            int total = pageInfo == null ? 0 : Math.max(0, pageInfo.optInt("totalResults", 0));

            JSONArray items = root.optJSONArray("items");
            if (total <= 0 && (items == null || items.length() == 0)) {
                return null;
            }

            String thumbnailUrl = "";
            if (items != null && items.length() > 0) {
                JSONObject first = items.optJSONObject(0);
                if (first != null) {
                    JSONObject snippet = first.optJSONObject("snippet");
                    if (snippet != null) {
                        thumbnailUrl = extractYouTubeThumbnail(snippet.optJSONObject("thumbnails"));
                    }
                }
            }

            return new PlaylistResult(
                    SPECIAL_LIKED_VIDEOS_ID,
                    SPECIAL_LIKED_VIDEOS_TITLE,
                    "Tu cuenta de YouTube",
                    total,
                    thumbnailUrl,
                    "private",
                    ""
            );
        } finally {
            connection.disconnect();
        }
    }

    private void upsertPlaylistAtTop(@NonNull List<PlaylistResult> list, @NonNull PlaylistResult playlist) {
        int existingIndex = -1;
        for (int i = 0; i < list.size(); i++) {
            if (playlist.playlistId.equals(list.get(i).playlistId)) {
                existingIndex = i;
                break;
            }
        }

        if (existingIndex >= 0) {
            list.remove(existingIndex);
        }
        list.add(0, playlist);
    }

    @NonNull
    private List<PlaylistTrackResult> performPlaylistTracksRequest(
            @NonNull String accessToken,
            @NonNull String playlistId,
            int maxResults
    ) throws Exception {
        int targetCount = Math.max(1, maxResults);
        String pageToken = "";
        LinkedHashMap<String, TrackTempData> videoMap = new LinkedHashMap<>();

        while (videoMap.size() < targetCount) {
            int pageSize = Math.min(YOUTUBE_PAGE_MAX_RESULTS, targetCount - videoMap.size());
            String endpoint = API_BASE_URL
                    + "/playlistItems?part=snippet,contentDetails"
                    + "&playlistId=" + safeUrlEncode(playlistId)
                    + "&maxResults=" + pageSize;
            if (!pageToken.isEmpty()) {
                endpoint += "&pageToken=" + safeUrlEncode(pageToken);
            }

            HttpURLConnection connection = openGetConnection(endpoint, "Bearer " + accessToken);
            try {
                int statusCode = connection.getResponseCode();
                String body = readResponse(connection, statusCode >= 400);
                if (statusCode != HttpURLConnection.HTTP_OK) {
                    throw new IllegalStateException(buildApiErrorMessage(body, statusCode));
                }

                JSONObject root = new JSONObject(body);
                JSONArray items = root.optJSONArray("items");
                if (items != null) {
                    for (int i = 0; i < items.length(); i++) {
                        JSONObject item = items.optJSONObject(i);
                        if (item == null) {
                            continue;
                        }

                        JSONObject contentDetails = item.optJSONObject("contentDetails");
                        JSONObject snippet = item.optJSONObject("snippet");
                        if (contentDetails == null || snippet == null) {
                            continue;
                        }

                        String videoId = contentDetails.optString("videoId", "").trim();
                        if (videoId.isEmpty()) {
                            continue;
                        }

                        String title = snippet.optString("title", "").trim();
                        if (title.isEmpty() || "deleted video".equalsIgnoreCase(title) || "private video".equalsIgnoreCase(title)) {
                            continue;
                        }

                        String artist = snippet.optString("videoOwnerChannelTitle", "").trim();
                        if (artist.isEmpty()) {
                            artist = snippet.optString("channelTitle", "").trim();
                        }
                        if (artist.isEmpty()) {
                            artist = "Unknown artist";
                        }

                        String thumbnailUrl = extractYouTubeThumbnail(snippet.optJSONObject("thumbnails"));
                        videoMap.put(videoId, new TrackTempData(title, artist, thumbnailUrl));
                        if (videoMap.size() >= targetCount) {
                            break;
                        }
                    }
                }

                pageToken = root.optString("nextPageToken", "").trim();
                if (pageToken.isEmpty()) {
                    break;
                }
            } finally {
                connection.disconnect();
            }
        }

        List<PlaylistTrackResult> result = new ArrayList<>();
        Map<String, VideoPlaybackInfo> playbackInfoMap = fetchVideoPlaybackInfoByIdsInBatches(accessToken, new ArrayList<>(videoMap.keySet()));
        for (Map.Entry<String, TrackTempData> entry : videoMap.entrySet()) {
            String videoId = entry.getKey();
            TrackTempData data = entry.getValue();
            VideoPlaybackInfo playbackInfo = playbackInfoMap.get(videoId);
            if (playbackInfo != null && !playbackInfo.embeddable) {
                continue;
            }

            String duration = playbackInfo == null ? "" : playbackInfo.duration;
            if (TextUtils.isEmpty(duration)) {
                duration = "--:--";
            }

            result.add(new PlaylistTrackResult(
                    videoId,
                    data.title,
                    data.artist,
                    duration,
                    data.thumbnailUrl
            ));
            if (result.size() >= targetCount) {
                break;
            }
        }

        return result;
    }

    @NonNull
    private List<PlaylistTrackResult> performLikedVideosTracksRequest(
            @NonNull String accessToken,
            int maxResults
    ) throws Exception {
        int targetCount = Math.max(1, maxResults);
        String pageToken = "";
        Set<String> seenVideoIds = new HashSet<>();
        List<PlaylistTrackResult> result = new ArrayList<>();

        while (result.size() < targetCount) {
            int pageSize = Math.min(YOUTUBE_PAGE_MAX_RESULTS, targetCount - result.size());
            String endpoint = API_BASE_URL
                    + "/videos?part=snippet,contentDetails,status"
                    + "&myRating=like"
                    + "&maxResults=" + pageSize;
            if (!pageToken.isEmpty()) {
                endpoint += "&pageToken=" + safeUrlEncode(pageToken);
            }

            HttpURLConnection connection = openGetConnection(endpoint, "Bearer " + accessToken);
            try {
                int statusCode = connection.getResponseCode();
                String body = readResponse(connection, statusCode >= 400);
                if (statusCode != HttpURLConnection.HTTP_OK) {
                    throw new IllegalStateException(buildApiErrorMessage(body, statusCode));
                }

                JSONObject root = new JSONObject(body);
                JSONArray items = root.optJSONArray("items");
                if (items != null) {
                    for (int i = 0; i < items.length(); i++) {
                        JSONObject item = items.optJSONObject(i);
                        if (item == null) {
                            continue;
                        }

                        String videoId = item.optString("id", "").trim();
                        JSONObject snippet = item.optJSONObject("snippet");
                        JSONObject contentDetails = item.optJSONObject("contentDetails");
                        JSONObject status = item.optJSONObject("status");
                        if (videoId.isEmpty() || snippet == null || !seenVideoIds.add(videoId)) {
                            continue;
                        }

                        if (status != null && !status.optBoolean("embeddable", true)) {
                            continue;
                        }

                        String title = snippet.optString("title", "").trim();
                        if (title.isEmpty() || "deleted video".equalsIgnoreCase(title) || "private video".equalsIgnoreCase(title)) {
                            continue;
                        }

                        String artist = snippet.optString("channelTitle", "").trim();
                        if (artist.isEmpty()) {
                            artist = "Unknown artist";
                        }

                        String rawDuration = contentDetails == null ? "" : contentDetails.optString("duration", "").trim();
                        String duration = formatYoutubeDuration(rawDuration);
                        if (duration.isEmpty()) {
                            duration = "--:--";
                        }

                        String thumbnailUrl = extractYouTubeThumbnail(snippet.optJSONObject("thumbnails"));
                        result.add(new PlaylistTrackResult(videoId, title, artist, duration, thumbnailUrl));
                        if (result.size() >= targetCount) {
                            break;
                        }
                    }
                }

                pageToken = root.optString("nextPageToken", "").trim();
                if (pageToken.isEmpty()) {
                    break;
                }
            } finally {
                connection.disconnect();
            }
        }

        return result;
    }

    @NonNull
    private List<PlaylistTrackResult> performMusicLikesTracksRequest(
            @NonNull String accessToken,
            int maxResults
    ) throws Exception {
        try {
            return performPlaylistTracksRequest(accessToken, MUSIC_LIKES_PLAYLIST_ID, maxResults);
        } catch (Exception ignored) {
            return new ArrayList<>();
        }
    }

    @NonNull
    private PlaylistResult toSpecialLikesCollection(
            @NonNull PlaylistResult source,
            @NonNull String ownerName
    ) {
        return new PlaylistResult(
                SPECIAL_LIKED_VIDEOS_ID,
                SPECIAL_LIKED_VIDEOS_TITLE,
                ownerName,
                source.itemCount,
                source.thumbnailUrl,
                source.privacyStatus,
                source.publishedAt
        );
    }

    @NonNull
    private Map<String, VideoPlaybackInfo> fetchVideoPlaybackInfoByIdsInBatches(
            @NonNull String accessToken,
            @NonNull List<String> videoIds
    ) throws Exception {
        Map<String, VideoPlaybackInfo> result = new HashMap<>();
        if (videoIds.isEmpty()) {
            return result;
        }

        for (int start = 0; start < videoIds.size(); start += YOUTUBE_PAGE_MAX_RESULTS) {
            int end = Math.min(start + YOUTUBE_PAGE_MAX_RESULTS, videoIds.size());
            List<String> batch = new ArrayList<>(videoIds.subList(start, end));
            result.putAll(fetchVideoPlaybackInfoByIds(accessToken, batch));
        }
        return result;
    }

    @NonNull
    private Map<String, VideoPlaybackInfo> fetchVideoPlaybackInfoByIds(
            @NonNull String accessToken,
            @NonNull List<String> videoIds
    ) throws Exception {
        Map<String, VideoPlaybackInfo> playbackInfoMap = new HashMap<>();
        if (videoIds.isEmpty()) {
            return playbackInfoMap;
        }

        StringBuilder idsBuilder = new StringBuilder();
        int maxIds = Math.min(videoIds.size(), 50);
        for (int i = 0; i < maxIds; i++) {
            if (i > 0) {
                idsBuilder.append(',');
            }
            idsBuilder.append(videoIds.get(i));
        }

        String endpoint = API_BASE_URL
            + "/videos?part=contentDetails,status"
                + "&id=" + safeUrlEncode(idsBuilder.toString());

        HttpURLConnection connection = openGetConnection(endpoint, "Bearer " + accessToken);
        try {
            int statusCode = connection.getResponseCode();
            String body = readResponse(connection, statusCode >= 400);
            if (statusCode != HttpURLConnection.HTTP_OK) {
                throw new IllegalStateException(buildApiErrorMessage(body, statusCode));
            }

            JSONObject root = new JSONObject(body);
            JSONArray items = root.optJSONArray("items");
            if (items == null) {
                return playbackInfoMap;
            }

            for (int i = 0; i < items.length(); i++) {
                JSONObject item = items.optJSONObject(i);
                if (item == null) {
                    continue;
                }
                String id = item.optString("id", "").trim();
                JSONObject contentDetails = item.optJSONObject("contentDetails");
                JSONObject status = item.optJSONObject("status");
                if (id.isEmpty()) {
                    continue;
                }

                String rawDuration = contentDetails == null
                        ? ""
                        : contentDetails.optString("duration", "").trim();
                String duration = formatYoutubeDuration(rawDuration);
                boolean embeddable = status == null || status.optBoolean("embeddable", true);
                playbackInfoMap.put(id, new VideoPlaybackInfo(duration, embeddable));
            }
            return playbackInfoMap;
        } finally {
            connection.disconnect();
        }
    }

    @NonNull
    private Map<String, PublicVideoFilterInfo> fetchPublicVideoFiltersByIds(
            @NonNull String apiKey,
            @NonNull List<String> videoIds
    ) throws Exception {
        Map<String, PublicVideoFilterInfo> filterMap = new HashMap<>();
        if (videoIds.isEmpty()) {
            return filterMap;
        }

        StringBuilder idsBuilder = new StringBuilder();
        int maxIds = Math.min(videoIds.size(), 50);
        for (int i = 0; i < maxIds; i++) {
            if (i > 0) {
                idsBuilder.append(',');
            }
            idsBuilder.append(videoIds.get(i));
        }

        String endpoint = API_BASE_URL
            + "/videos?part=contentDetails,status"
                + "&id=" + safeUrlEncode(idsBuilder.toString())
                + "&key=" + safeUrlEncode(apiKey);

        HttpURLConnection connection = openGetConnection(endpoint);
        try {
            int statusCode = connection.getResponseCode();
            String body = readResponse(connection, statusCode >= 400);
            if (statusCode != HttpURLConnection.HTTP_OK) {
                throw new IllegalStateException(buildApiErrorMessage(body, statusCode));
            }

            JSONObject root = new JSONObject(body);
            JSONArray items = root.optJSONArray("items");
            if (items == null) {
                return filterMap;
            }

            for (int i = 0; i < items.length(); i++) {
                JSONObject item = items.optJSONObject(i);
                if (item == null) {
                    continue;
                }

                String id = item.optString("id", "").trim();
                if (id.isEmpty()) {
                    continue;
                }

                JSONObject contentDetails = item.optJSONObject("contentDetails");
                String rawDuration = contentDetails == null
                        ? ""
                        : contentDetails.optString("duration", "").trim();
                int durationSeconds = parseYoutubeDurationSeconds(rawDuration);

                JSONObject status = item.optJSONObject("status");
                boolean embeddable = status == null || status.optBoolean("embeddable", true);
                filterMap.put(id, new PublicVideoFilterInfo(embeddable, durationSeconds));
            }

            return filterMap;
        } finally {
            connection.disconnect();
        }
    }

    private int parseYoutubeDurationSeconds(@Nullable String rawDuration) {
        if (TextUtils.isEmpty(rawDuration)) {
            return 0;
        }

        int hours = extractDurationComponent(rawDuration, 'H');
        int minutes = extractDurationComponent(rawDuration, 'M');
        int seconds = extractDurationComponent(rawDuration, 'S');
        return Math.max(0, (hours * 3600) + (minutes * 60) + seconds);
    }

    @NonNull
    private String formatYoutubeDuration(@Nullable String rawDuration) {
        if (TextUtils.isEmpty(rawDuration)) {
            return "--:--";
        }

        int hours = extractDurationComponent(rawDuration, 'H');
        int minutes = extractDurationComponent(rawDuration, 'M');
        int seconds = extractDurationComponent(rawDuration, 'S');

        if (hours > 0) {
            return String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds);
        }
        return String.format(Locale.US, "%d:%02d", minutes, seconds);
    }

    private int extractDurationComponent(@NonNull String rawDuration, char component) {
        int markerIndex = rawDuration.indexOf(component);
        if (markerIndex <= 0) {
            return 0;
        }

        int start = markerIndex - 1;
        while (start >= 0 && Character.isDigit(rawDuration.charAt(start))) {
            start--;
        }

        String number = rawDuration.substring(start + 1, markerIndex);
        if (number.isEmpty()) {
            return 0;
        }

        try {
            return Integer.parseInt(number);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static final class TrackTempData {
        final String title;
        final String artist;
        final String thumbnailUrl;

        TrackTempData(@NonNull String title, @NonNull String artist, @NonNull String thumbnailUrl) {
            this.title = title;
            this.artist = artist;
            this.thumbnailUrl = thumbnailUrl;
        }
    }

    private static final class VideoPlaybackInfo {
        final String duration;
        final boolean embeddable;

        VideoPlaybackInfo(@NonNull String duration, boolean embeddable) {
            this.duration = duration;
            this.embeddable = embeddable;
        }
    }

    private static final class PublicVideoFilterInfo {
        final boolean embeddable;
        final int durationSeconds;

        PublicVideoFilterInfo(boolean embeddable, int durationSeconds) {
            this.embeddable = embeddable;
            this.durationSeconds = durationSeconds;
        }
    }

    @NonNull
    private HttpURLConnection openGetConnection(@NonNull String endpoint) throws Exception {
        return openGetConnection(endpoint, null);
    }

    @NonNull
    private HttpURLConnection openGetConnection(@NonNull String endpoint, @Nullable String authorization) throws Exception {
        URL url = new URL(endpoint);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(14000);
        connection.setReadTimeout(18000);
        connection.setUseCaches(false);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("User-Agent", "Sleppify-Android/1.0");
        if (authorization != null && !authorization.isEmpty()) {
            connection.setRequestProperty("Authorization", authorization);
        }
        return connection;
    }

    @NonNull
    private String buildSearchSubtitle(@NonNull String resultType, @NonNull String channelTitle) {
        String typeLabel;
        if ("playlist".equals(resultType)) {
            typeLabel = "Playlist";
        } else if ("channel".equals(resultType)) {
            typeLabel = "Artista";
        } else {
            typeLabel = "Cancion";
        }

        if (channelTitle.isEmpty()) {
            return typeLabel;
        }
        return typeLabel + " • " + channelTitle;
    }

    private boolean shouldIncludeMusicSearchResult(
            @NonNull String title,
            @Nullable String channelTitle
    ) {
        String normalizedTitle = title.toLowerCase(Locale.US);

        if (containsAny(normalizedTitle, "#shorts", " shorts", "shorts ")) {
            return false;
        }

        if (containsAny(normalizedTitle,
                "podcast",
                "interview",
                "entrevista",
                "explica",
                "explains",
                "reaction",
                "trailer",
                "teaser",
                "documental",
                "noticias",
                "news")) {
            return false;
        }

            return true;
    }

    @NonNull
    private String extractYouTubeThumbnail(JSONObject thumbnails) {
        if (thumbnails == null) {
            return "";
        }

        JSONObject high = thumbnails.optJSONObject("high");
        if (high != null) {
            String url = high.optString("url", "").trim();
            if (!url.isEmpty()) {
                return url;
            }
        }

        JSONObject medium = thumbnails.optJSONObject("medium");
        if (medium != null) {
            String url = medium.optString("url", "").trim();
            if (!url.isEmpty()) {
                return url;
            }
        }

        JSONObject standard = thumbnails.optJSONObject("standard");
        if (standard != null) {
            String url = standard.optString("url", "").trim();
            if (!url.isEmpty()) {
                return url;
            }
        }

        JSONObject defaults = thumbnails.optJSONObject("default");
        return defaults == null ? "" : defaults.optString("url", "").trim();
    }

    @NonNull
    private String buildApiErrorMessage(@NonNull String rawBody, int statusCode) {
        ApiErrorDetails details = parseApiError(rawBody);
        String reasonNormalized = details.reason.toLowerCase(Locale.US);
        String messageNormalized = details.message.toLowerCase(Locale.US);

        if (statusCode == 401) {
            return "Token de YouTube expirado o invalido. Reconecta tu cuenta.";
        }
        if (statusCode == 403) {
            return buildForbiddenApiErrorMessage(reasonNormalized, messageNormalized, details);
        }

        if (!details.message.isEmpty()) {
            if (!details.reason.isEmpty()) {
                return "YouTube API " + statusCode + " (" + details.reason + "): " + details.message;
            }
            return "YouTube API " + statusCode + ": " + details.message;
        }
        return "YouTube API " + statusCode + ": solicitud no valida.";
    }

    @NonNull
    private String buildForbiddenApiErrorMessage(
            @NonNull String reasonNormalized,
            @NonNull String messageNormalized,
            @NonNull ApiErrorDetails details
    ) {
        if (containsAny(reasonNormalized,
                "quotaexceeded",
                "dailylimitexceeded",
                "ratelimitexceeded",
                "userratelimitexceeded")
                || containsAny(messageNormalized, "quota", "rate limit", "daily limit")) {
            return "Cuota de YouTube agotada. Intenta mas tarde o revisa la cuota en Google Cloud.";
        }

        if (containsAny(reasonNormalized, "accessnotconfigured", "servicedisabled")
                || containsAny(messageNormalized,
                "api has not been used",
                "api is disabled",
                "service disabled")) {
            return "YouTube Data API no esta habilitada para este proyecto en Google Cloud.";
        }

        if (containsAny(reasonNormalized,
                "insufficientpermissions",
                "forbidden",
                "playlistforbidden",
                "authorizationrequired")
                || containsAny(messageNormalized,
                "insufficient permission",
                "not properly authorized",
                "youtube.readonly")) {
            return "Faltan permisos OAuth de YouTube. Pulsa Conectar y acepta youtube.readonly.";
        }

        if (!details.message.isEmpty()) {
            String reasonLabel = details.reason.isEmpty() ? "forbidden" : details.reason;
            return "YouTube API 403 (" + reasonLabel + "): " + details.message;
        }

        return "Sin permiso para YouTube API (403).";
    }

    @NonNull
    private ApiErrorDetails parseApiError(@NonNull String rawBody) {
        if (rawBody.isEmpty()) {
            return new ApiErrorDetails("", "");
        }

        try {
            JSONObject root = new JSONObject(rawBody);
            JSONObject error = root.optJSONObject("error");
            if (error == null) {
                return new ApiErrorDetails("", "");
            }

            String message = error.optString("message", "").trim();
            String reason = "";

            JSONArray errors = error.optJSONArray("errors");
            if (errors != null && errors.length() > 0) {
                JSONObject first = errors.optJSONObject(0);
                if (first != null) {
                    reason = first.optString("reason", "").trim();
                    if (message.isEmpty()) {
                        message = first.optString("message", "").trim();
                    }
                }
            }

            return new ApiErrorDetails(reason, message);
        } catch (Exception ignored) {
            return new ApiErrorDetails("", "");
        }
    }

    private boolean containsAny(@NonNull String text, @NonNull String... terms) {
        for (String term : terms) {
            if (text.contains(term)) {
                return true;
            }
        }
        return false;
    }

    @NonNull
    private static String safeUrlEncode(@NonNull String value) {
        try {
            return URLEncoder.encode(value, StandardCharsets.UTF_8.name());
        } catch (Exception ignored) {
            return value;
        }
    }

    @NonNull
    private String readResponse(@NonNull HttpURLConnection connection, boolean fromErrorStream) {
        try {
            if (fromErrorStream && connection.getErrorStream() == null) {
                return "";
            }

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(
                            fromErrorStream ? connection.getErrorStream() : connection.getInputStream(),
                            StandardCharsets.UTF_8
                    ))) {
                StringBuilder builder = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    builder.append(line);
                }
                return builder.toString();
            }
        } catch (Exception ignored) {
            return "";
        }
    }

    @NonNull
    public String getYoutubeReadonlyScope() {
        return YT_SCOPE_READONLY;
    }
}
