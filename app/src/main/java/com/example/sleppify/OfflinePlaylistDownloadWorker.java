package com.example.sleppify;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Build;
import android.os.SystemClock;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.StyleSpan;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.work.Data;
import androidx.work.ForegroundInfo;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONObject;
import org.schabi.newpipe.extractor.MediaFormat;
import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.StreamExtractor;

public final class OfflinePlaylistDownloadWorker extends Worker {

    private static final String TAG = "OfflineDlWorker";

    public static final String INPUT_PLAYLIST_ID = "input_playlist_id";
    public static final String INPUT_VIDEO_IDS = "input_video_ids";
    public static final String INPUT_TITLES = "input_titles";
    public static final String INPUT_ARTISTS = "input_artists";
    public static final String INPUT_ALREADY_OFFLINE_COUNT = "input_already_offline_count";
    public static final String INPUT_TOTAL_WITH_VIDEO_ID = "input_total_with_video_id";
    public static final String INPUT_PLAYLIST_TITLE = "input_playlist_title";

    public static final String PROGRESS_DONE = "progress_done";
    public static final String PROGRESS_TOTAL = "progress_total";
    public static final String PROGRESS_LAST_ID = "progress_last_id";
    public static final String PROGRESS_CURRENT_ID = "progress_current_id";
    public static final String PROGRESS_DOWNLOADED = "progress_downloaded";
    public static final String PROGRESS_PLAYLIST_TITLE = "progress_playlist_title";

    public static final String OUTPUT_DOWNLOADED = "output_downloaded";
    public static final String OUTPUT_TOTAL = "output_total";
    public static final String OUTPUT_REASON = "output_reason";
    public static final String OUTPUT_REASON_NONE = "none";
    public static final String OUTPUT_REASON_NO_NETWORK = "no_network";
    public static final String OUTPUT_REASON_NO_MATCH = "no_match";

    private static final int CONNECT_TIMEOUT_MS = 14000;
    private static final int READ_TIMEOUT_MS = 22000;
    private static final long DOWNLOAD_PROGRESS_LOG_STEP_BYTES = 2L * 1024L * 1024L;
    private static final int DOWNLOAD_MAX_RETRIES = 3;
    private static final long DOWNLOAD_RETRY_BACKOFF_MS = 1400L;
    private static final int TARGET_M4A_BITRATE = 192000;
    private static final long FOREGROUND_PROGRESS_UPDATE_MIN_INTERVAL_MS = 900L;

    private static final String NOTIFICATION_CHANNEL_ID = "offline_downloads";
    private static final String NOTIFICATION_CHANNEL_NAME = "Descargas offline";
    private static final int NOTIFICATION_ID = 9003;

    private int notificationDone;
    private int notificationTotal;
    private int notificationDownloaded;
    @NonNull
    private String notificationPlaylistTitle = "Playlist";
    @NonNull
    private String notificationTrackId = "";
    @NonNull
    private String notificationTrackTitle = "";
    private long notificationTrackBytes = -1L;
    private long notificationTrackTotalBytes = -1L;
    private long lastForegroundUpdateAtMs;

    public OfflinePlaylistDownloadWorker(
            @NonNull Context context,
            @NonNull WorkerParameters params
    ) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        String[] ids = getInputData().getStringArray(INPUT_VIDEO_IDS);
        if (ids == null || ids.length == 0) {
            Log.d(TAG, "doWork: no IDs received");
            return Result.failure(new Data.Builder()
                    .putInt(OUTPUT_TOTAL, 0)
                    .putInt(OUTPUT_DOWNLOADED, 0)
                    .putString(OUTPUT_REASON, OUTPUT_REASON_NONE)
                    .build());
        }

        String playlistTitle = normalizePlaylistTitle(
            getInputData().getString(INPUT_PLAYLIST_TITLE),
            getInputData().getString(INPUT_PLAYLIST_ID)
        );
        notificationPlaylistTitle = playlistTitle;
        String[] titles = getInputData().getStringArray(INPUT_TITLES);
        String[] artists = getInputData().getStringArray(INPUT_ARTISTS);

        int pendingTotal = ids.length;
        int alreadyOfflineCount = Math.max(0, getInputData().getInt(INPUT_ALREADY_OFFLINE_COUNT, 0));
        int totalWithVideoId = getInputData().getInt(INPUT_TOTAL_WITH_VIDEO_ID, pendingTotal + alreadyOfflineCount);
        int total = Math.max(pendingTotal + alreadyOfflineCount, totalWithVideoId);
        int done = Math.min(total, alreadyOfflineCount);
        int downloaded = Math.min(total, alreadyOfflineCount);
        boolean encounteredNoNetwork = false;
        Set<String> restrictedIds = new HashSet<>(OfflineRestrictionStore.getRestrictedVideoIds(getApplicationContext()));

        Log.d(TAG,
            "doWork:start total=" + total
                + " pending=" + pendingTotal
                + " alreadyOffline=" + alreadyOfflineCount);
        updateForegroundNotification(done, total, downloaded, null, null, true);

        for (int i = 0; i < ids.length; i++) {
            if (isStopped()) {
                Log.w(TAG, "doWork: worker stopped, retrying");
                return Result.retry();
            }

            String rawId = ids[i];
            String id = rawId == null ? "" : rawId.trim();
            String title = safeArrayValue(titles, i);

            Log.d(TAG, "track:start index=" + (i + 1) + "/" + total + " id=" + id);
            updateForegroundNotification(done, total, downloaded, id, title, true);

            if (!id.isEmpty() && OfflineRestrictionStore.isRestricted(restrictedIds, id)) {
                Log.d(TAG, "track:restricted_skip id=" + id);
                done = Math.min(total, done + 1);
                setProgressAsync(new Data.Builder()
                        .putInt(PROGRESS_DONE, done)
                        .putInt(PROGRESS_TOTAL, total)
                        .putString(PROGRESS_PLAYLIST_TITLE, playlistTitle)
                        .putString(PROGRESS_CURRENT_ID, id)
                        .putString(PROGRESS_LAST_ID, id)
                        .putInt(PROGRESS_DOWNLOADED, downloaded)
                        .build());
                updateForegroundNotification(done, total, downloaded, id, title, true);
                continue;
            }

            setProgressAsync(new Data.Builder()
                    .putInt(PROGRESS_DONE, done)
                    .putInt(PROGRESS_TOTAL, total)
                    .putString(PROGRESS_PLAYLIST_TITLE, playlistTitle)
                    .putString(PROGRESS_CURRENT_ID, id)
                    .putString(PROGRESS_LAST_ID, id)
                    .putInt(PROGRESS_DOWNLOADED, downloaded)
                    .build());

            if (!id.isEmpty()) {
                String artist = safeArrayValue(artists, i);

                boolean ok = OfflineAudioStore.hasOfflineAudio(getApplicationContext(), id);
                if (ok) {
                    Log.d(TAG, "track:already_offline id=" + id);
                }
                if (!ok) {
                    boolean internetReady = isInternetAvailable(getApplicationContext());
                    if (!internetReady) {
                        encounteredNoNetwork = true;
                        Log.w(TAG, "track:no_network id=" + id);
                    } else {
                        ok = downloadTrackFromYouTubeExtractor(getApplicationContext(), id);
                        Log.d(TAG, "track:newpipe_result id=" + id + " ok=" + ok);
                        if (!ok) {
                            ok = downloadTrackFromArchiveCatalog(getApplicationContext(), id, title, artist);
                            Log.d(TAG, "track:archive_result id=" + id + " ok=" + ok);
                        }
                        if (!ok) {
                            OfflineRestrictionStore.markRestricted(getApplicationContext(), id);
                            restrictedIds.add(id);
                            Log.w(TAG, "track:marked_restricted id=" + id);
                        }
                    }
                }

                if (ok) {
                    OfflineAudioStore.markOfflineAudioState(id, true);
                    downloaded = Math.min(total, downloaded + 1);
                    Log.d(TAG, "track:success id=" + id + " downloaded=" + downloaded + "/" + total);
                } else {
                    Log.w(TAG, "track:failed id=" + id);
                }
            }

            done = Math.min(total, done + 1);
            setProgressAsync(new Data.Builder()
                    .putInt(PROGRESS_DONE, done)
                    .putInt(PROGRESS_TOTAL, total)
                    .putString(PROGRESS_PLAYLIST_TITLE, playlistTitle)
                    .putString(PROGRESS_CURRENT_ID, id)
                    .putString(PROGRESS_LAST_ID, id)
                    .putInt(PROGRESS_DOWNLOADED, downloaded)
                    .build());
                updateForegroundNotification(done, total, downloaded, id, title, true);
        }

        String reason = OUTPUT_REASON_NONE;
        if (downloaded <= 0) {
            if (encounteredNoNetwork) {
                reason = OUTPUT_REASON_NO_NETWORK;
            } else {
                reason = OUTPUT_REASON_NO_MATCH;
            }
        }

        Log.d(TAG, "doWork:end downloaded=" + downloaded + "/" + total + " reason=" + reason);

        return Result.success(new Data.Builder()
                .putInt(OUTPUT_TOTAL, total)
                .putInt(OUTPUT_DOWNLOADED, downloaded)
                .putString(OUTPUT_REASON, reason)
                .build());
    }

    private void updateForegroundNotification(
            int done,
            int total,
            int downloaded,
            @Nullable String currentId,
            @Nullable String currentTitle,
            boolean force
    ) {
        notificationDone = done;
        notificationTotal = total;
        notificationDownloaded = downloaded;

        if (currentId != null) {
            String normalizedId = currentId.trim();
            if (!TextUtils.equals(notificationTrackId, normalizedId)) {
                notificationTrackBytes = -1L;
                notificationTrackTotalBytes = -1L;
            }
            notificationTrackId = normalizedId;
        }
        if (currentTitle != null) {
            String normalizedTitle = currentTitle.trim();
            if (!TextUtils.equals(notificationTrackTitle, normalizedTitle)) {
                notificationTrackBytes = -1L;
                notificationTrackTotalBytes = -1L;
            }
            notificationTrackTitle = normalizedTitle;
        }

        long now = SystemClock.elapsedRealtime();
        if (!force && (now - lastForegroundUpdateAtMs) < FOREGROUND_PROGRESS_UPDATE_MIN_INTERVAL_MS) {
            return;
        }

        lastForegroundUpdateAtMs = now;
        try {
            setForegroundAsync(createForegroundInfo());
        } catch (Exception e) {
            Log.w(TAG, "foreground:update_failed message=" + e.getMessage());
        }
    }

    @NonNull
    private ForegroundInfo createForegroundInfo() {
        Context context = getApplicationContext();
        ensureNotificationChannel(context);

        Intent openAppIntent = new Intent(context, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);

        int pendingIntentFlags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            pendingIntentFlags |= PendingIntent.FLAG_IMMUTABLE;
        }

        PendingIntent pendingIntent = PendingIntent.getActivity(
                context,
                0,
                openAppIntent,
                pendingIntentFlags
        );

        String resolvedTrackTitle = notificationTrackTitle;
        if (TextUtils.isEmpty(resolvedTrackTitle)) {
            resolvedTrackTitle = notificationTrackId;
        }

        int safeTotal = Math.max(0, notificationTotal);
        int safeDownloaded = Math.max(0, notificationDownloaded);
        if (safeTotal > 0) {
            safeDownloaded = Math.min(safeDownloaded, safeTotal);
        }

        CharSequence title = buildNotificationTitle(notificationPlaylistTitle, safeDownloaded, safeTotal);

        StringBuilder content = new StringBuilder();
        if (notificationTrackBytes >= 0 && notificationTrackTotalBytes > 0) {
            content.append(formatBytes(notificationTrackBytes))
                    .append(" / ")
                    .append(formatBytes(notificationTrackTotalBytes));
        } else if (notificationTrackBytes > 0) {
            content.append(formatBytes(notificationTrackBytes)).append(" descargados");
        } else if (!TextUtils.isEmpty(resolvedTrackTitle)) {
            content.append(resolvedTrackTitle);
        } else if (notificationTotal > 0) {
            content.append("Preparando siguiente canción");
        } else {
            content.append("Preparando descarga");
        }

        int progressMax = 100;
        int progressValue = 0;
        boolean indeterminate = true;
        if (notificationTrackTotalBytes > 0) {
            progressMax = (int) Math.min(Integer.MAX_VALUE, notificationTrackTotalBytes);
            progressValue = (int) Math.min((long) progressMax, Math.max(0L, notificationTrackBytes));
            indeterminate = false;
        }

        Notification notification = new NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_music)
                .setContentTitle(title)
                .setContentText(content.toString())
                .setContentIntent(pendingIntent)
                .setOnlyAlertOnce(true)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setProgress(progressMax, progressValue, indeterminate)
                .build();

        return new ForegroundInfo(NOTIFICATION_ID, notification);
    }

    @NonNull
    private CharSequence buildNotificationTitle(@NonNull String playlistTitle, int downloaded, int total) {
        String safeTitle = TextUtils.isEmpty(playlistTitle) ? "Playlist" : playlistTitle;
        String prefix = "Descargando playlist ";
        String suffix = " " + downloaded + "/" + total;

        SpannableStringBuilder builder = new SpannableStringBuilder(prefix)
                .append(safeTitle)
                .append(suffix);
        int start = prefix.length();
        int end = start + safeTitle.length();
        builder.setSpan(new StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return builder;
    }

    @NonNull
    private String normalizePlaylistTitle(@Nullable String playlistTitle, @Nullable String playlistId) {
        String resolved = playlistTitle == null ? "" : playlistTitle.trim();
        if (!TextUtils.isEmpty(resolved)) {
            return resolved;
        }

        resolved = playlistId == null ? "" : playlistId.trim();
        if (!TextUtils.isEmpty(resolved)) {
            return resolved;
        }

        return "Playlist";
    }

    @NonNull
    private String formatBytes(long bytes) {
        if (bytes < 1024L) {
            return bytes + " B";
        }

        double kb = bytes / 1024.0;
        if (kb < 1024.0) {
            return String.format(Locale.US, "%.1f KB", kb);
        }

        double mb = kb / 1024.0;
        if (mb < 1024.0) {
            return String.format(Locale.US, "%.1f MB", mb);
        }

        double gb = mb / 1024.0;
        return String.format(Locale.US, "%.2f GB", gb);
    }

    private void ensureNotificationChannel(@NonNull Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }

        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null) {
            return;
        }

        NotificationChannel existing = manager.getNotificationChannel(NOTIFICATION_CHANNEL_ID);
        if (existing != null) {
            return;
        }

        NotificationChannel channel = new NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                NOTIFICATION_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
        );
        channel.setDescription("Descargas de playlists para modo offline");
        manager.createNotificationChannel(channel);
    }

    private boolean isInternetAvailable(@NonNull Context context) {
        ConnectivityManager connectivityManager =
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivityManager == null) {
            return false;
        }

        android.net.Network network = connectivityManager.getActiveNetwork();
        if (network == null) {
            return false;
        }

        NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(network);
        if (capabilities == null) {
            return false;
        }

        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
    }

    @NonNull
    private String safeArrayValue(@Nullable String[] values, int index) {
        if (values == null || index < 0 || index >= values.length) {
            return "";
        }
        String raw = values[index];
        return raw == null ? "" : raw.trim();
    }

    private boolean downloadTrackFromYouTubeExtractor(
            @NonNull Context context,
            @NonNull String videoId
    ) {
        File target = OfflineAudioStore.getOfflineAudioFile(context, videoId);
        File parent = target.getParentFile();
        if (parent == null) {
            return false;
        }
        if (!parent.exists() && !parent.mkdirs()) {
            return false;
        }

        String[] sourceCandidates = buildYouTubeSourceCandidates(videoId);
        for (String sourceUrl : sourceCandidates) {
            try {
                StreamExtractor extractor = ServiceList.YouTube.getStreamExtractor(sourceUrl);
                extractor.fetchPage();

                AudioStream selected = selectBestAudioStream(extractor.getAudioStreams());
                if (selected == null || TextUtils.isEmpty(selected.getContent())) {
                    Log.w(TAG, "newpipe:no_audio_stream id=" + videoId + " url=" + sanitizeUrlForLog(sourceUrl));
                    continue;
                }

                String directUrl = selected.getContent();
                String format = selected.getFormat() == null
                        ? "unknown"
                        : selected.getFormat().name().toLowerCase(Locale.US);

                Log.d(TAG,
                        "newpipe:attempt id=" + videoId
                                + " source=" + sanitizeUrlForLog(sourceUrl)
                                + " format=" + format
                        + " target_bitrate=" + TARGET_M4A_BITRATE
                                + " bitrate=" + audioBitrate(selected)
                                + " direct=" + sanitizeUrlForLog(directUrl));

                if (downloadFromUrl(directUrl, target)) {
                    Log.d(TAG, "newpipe:success id=" + videoId + " format=" + format);
                    return true;
                }
            } catch (Exception e) {
                String errorType = e.getClass().getSimpleName();
                if ("SignInConfirmNotBotException".equals(errorType)) {
                    Log.w(TAG, "newpipe:blocked id=" + videoId + " message=" + e.getMessage());
                } else {
                    Log.w(TAG,
                            "newpipe:exception id=" + videoId
                                    + " url=" + sanitizeUrlForLog(sourceUrl)
                                    + " errorType=" + errorType
                                    + " message=" + e.getMessage(),
                            e);
                }
            }
        }

        Log.w(TAG, "newpipe:all_candidates_failed id=" + videoId);
        return false;
    }

    @NonNull
    private String[] buildYouTubeSourceCandidates(@NonNull String videoId) {
        String escapedId = Uri.encode(videoId);
        return new String[] {
                "https://www.youtube.com/watch?v=" + escapedId,
                "https://music.youtube.com/watch?v=" + escapedId,
                "https://www.youtube.com/embed/" + escapedId
        };
    }

    @Nullable
    private AudioStream selectBestAudioStream(@Nullable List<AudioStream> streams) {
        if (streams == null || streams.isEmpty()) {
            return null;
        }

        List<AudioStream> m4aCandidates = new ArrayList<>();
        List<AudioStream> fallbackCandidates = new ArrayList<>();
        for (AudioStream stream : streams) {
            if (stream == null || TextUtils.isEmpty(stream.getContent())) {
                continue;
            }

            if (stream.getFormat() == MediaFormat.M4A) {
                m4aCandidates.add(stream);
            } else {
                fallbackCandidates.add(stream);
            }
        }

        AudioStream selected = selectFastestCandidate(m4aCandidates, TARGET_M4A_BITRATE);
        if (selected != null) {
            return selected;
        }

        selected = selectFastestCandidate(fallbackCandidates, Integer.MAX_VALUE);
        if (selected != null) {
            return selected;
        }

        if (m4aCandidates.isEmpty() && fallbackCandidates.isEmpty()) {
            return null;
        }

        return !m4aCandidates.isEmpty() ? m4aCandidates.get(0) : fallbackCandidates.get(0);
    }

    @Nullable
    private AudioStream selectFastestCandidate(
            @NonNull List<AudioStream> candidates,
            int targetBitrate
    ) {
        if (candidates.isEmpty()) {
            return null;
        }

        AudioStream bestUnderTarget = null;
        int bestUnderTargetBitrate = -1;

        AudioStream lowestOverTarget = null;
        int lowestOverTargetBitrate = Integer.MAX_VALUE;

        AudioStream firstUnknownBitrate = null;

        for (AudioStream stream : candidates) {
            int bitrate = audioBitrate(stream);

            if (bitrate <= 0) {
                if (firstUnknownBitrate == null) {
                    firstUnknownBitrate = stream;
                }
                continue;
            }

            if (bitrate <= targetBitrate) {
                if (bitrate > bestUnderTargetBitrate) {
                    bestUnderTargetBitrate = bitrate;
                    bestUnderTarget = stream;
                }
                continue;
            }

            if (bitrate < lowestOverTargetBitrate) {
                lowestOverTargetBitrate = bitrate;
                lowestOverTarget = stream;
            }
        }

        if (bestUnderTarget != null) {
            return bestUnderTarget;
        }

        if (lowestOverTarget != null) {
            return lowestOverTarget;
        }

        return firstUnknownBitrate;
    }

    private int audioBitrate(@Nullable AudioStream stream) {
        if (stream == null) {
            return -1;
        }

        int bitrate = stream.getBitrate();
        if (bitrate > 0) {
            return bitrate;
        }

        bitrate = stream.getAverageBitrate();
        if (bitrate > 0) {
            return bitrate;
        }

        return -1;
    }

    private boolean downloadTrackFromArchiveCatalog(
            @NonNull Context context,
            @NonNull String videoId,
            @NonNull String title,
            @NonNull String artist
    ) {
        File target = OfflineAudioStore.getOfflineAudioFile(context, videoId);
        File parent = target.getParentFile();
        if (parent == null) {
            return false;
        }
        if (!parent.exists() && !parent.mkdirs()) {
            return false;
        }

        String archiveUrl = resolveArchiveAudioUrl(title, artist);
        if (TextUtils.isEmpty(archiveUrl)) {
            Log.w(TAG, "archive:no_match id=" + videoId + " title=" + title + " artist=" + artist);
            return false;
        }

        Log.d(TAG, "archive:downloading id=" + videoId + " url=" + sanitizeUrlForLog(archiveUrl));
        return downloadFromUrl(archiveUrl, target);
    }

    @NonNull
    private String resolveArchiveAudioUrl(@NonNull String title, @NonNull String artist) {
        String query = buildArchiveSearchQuery(title, artist);
        if (query.isEmpty()) {
            return "";
        }

        Log.d(TAG, "archive:query=" + query);

        Uri searchUri = Uri.parse("https://archive.org/advancedsearch.php")
                .buildUpon()
                .appendQueryParameter("q", query)
                .appendQueryParameter("fl[]", "identifier")
                .appendQueryParameter("rows", "5")
                .appendQueryParameter("page", "1")
                .appendQueryParameter("output", "json")
                .build();

        String body = readTextResponse(searchUri.toString(), "application/json");
        if (body.isEmpty()) {
            return "";
        }

        try {
            JSONObject response = new JSONObject(body).optJSONObject("response");
            if (response == null) {
                return "";
            }

            JSONArray docs = response.optJSONArray("docs");
            if (docs == null || docs.length() == 0) {
                Log.d(TAG, "archive:no_docs");
                return "";
            }

            Log.d(TAG, "archive:docs_count=" + docs.length());

            for (int i = 0; i < docs.length(); i++) {
                JSONObject doc = docs.optJSONObject(i);
                if (doc == null) {
                    continue;
                }

                String identifier = doc.optString("identifier", "").trim();
                if (identifier.isEmpty()) {
                    continue;
                }

                String audioUrl = resolveArchiveDownloadUrl(identifier, title, artist);
                if (!audioUrl.isEmpty()) {
                    Log.d(TAG, "archive:resolved identifier=" + identifier);
                    return audioUrl;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "archive:resolve_exception message=" + e.getMessage(), e);
            return "";
        }

        return "";
    }

    @NonNull
    private String buildArchiveSearchQuery(@NonNull String title, @NonNull String artist) {
        String cleanTitle = sanitizeSearchText(title);
        if (cleanTitle.isEmpty()) {
            return "";
        }

        StringBuilder query = new StringBuilder("mediatype:(audio)");
        query.append(" AND title:(\"").append(cleanTitle).append("\")");

        String cleanArtist = sanitizeSearchText(artist);
        if (!cleanArtist.isEmpty()) {
            query.append(" AND creator:(\"").append(cleanArtist).append("\")");
        }

        return query.toString();
    }

    @NonNull
    private String sanitizeSearchText(@NonNull String raw) {
        String input = raw.trim();
        if (input.isEmpty()) {
            return "";
        }

        StringBuilder builder = new StringBuilder(input.length());
        boolean previousWasSpace = false;

        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            boolean valid = Character.isLetterOrDigit(c)
                    || Character.isWhitespace(c)
                    || c == '-'
                    || c == '_'
                    || c == '.';

            if (!valid) {
                c = ' ';
            }

            if (Character.isWhitespace(c)) {
                if (!previousWasSpace) {
                    builder.append(' ');
                    previousWasSpace = true;
                }
            } else {
                builder.append(c);
                previousWasSpace = false;
            }
        }

        return builder.toString().trim();
    }

    @NonNull
    private String resolveArchiveDownloadUrl(
            @NonNull String identifier,
            @NonNull String expectedTitle,
            @NonNull String expectedArtist
    ) {
        String metadataUrl = "https://archive.org/metadata/" + Uri.encode(identifier);
        String body = readTextResponse(metadataUrl, "application/json");
        if (body.isEmpty()) {
            return "";
        }

        try {
            JSONObject root = new JSONObject(body);
            JSONObject metadata = root.optJSONObject("metadata");
            String metadataTitle = metadata == null ? "" : metadata.optString("title", "");
            String metadataCreator = metadata == null ? "" : metadata.optString("creator", "");
            if (!isLikelyMatch(expectedTitle, expectedArtist, metadataTitle, metadataCreator, identifier)) {
                return "";
            }

            JSONArray files = root.optJSONArray("files");
            if (files == null || files.length() == 0) {
                return "";
            }

            for (int i = 0; i < files.length(); i++) {
                JSONObject file = files.optJSONObject(i);
                if (file == null) {
                    continue;
                }

                String name = file.optString("name", "").trim();
                if (!isSupportedAudioFile(name)) {
                    continue;
                }

                return "https://archive.org/download/"
                        + Uri.encode(identifier)
                        + "/"
                        + Uri.encode(name, "/");
            }
        } catch (Exception ignored) {
            return "";
        }

        return "";
    }

    private boolean isSupportedAudioFile(@NonNull String fileName) {
        String lower = fileName.toLowerCase(Locale.US);
        return lower.endsWith(".mp3")
                || lower.endsWith(".m4a")
                || lower.endsWith(".aac")
                || lower.endsWith(".ogg")
                || lower.endsWith(".wav")
                || lower.endsWith(".flac");
    }

    private boolean isLikelyMatch(
            @NonNull String expectedTitle,
            @NonNull String expectedArtist,
            @NonNull String metadataTitle,
            @NonNull String metadataCreator,
            @NonNull String identifier
    ) {
        String target = normalizeForMatch(expectedTitle);
        if (target.isEmpty()) {
            return true;
        }

        String haystack = normalizeForMatch(metadataTitle + " " + metadataCreator + " " + identifier);
        if (haystack.isEmpty()) {
            return false;
        }

        String[] tokens = target.split(" ");
        int considered = 0;
        int hits = 0;

        for (String token : tokens) {
            if (token.length() < 3) {
                continue;
            }
            considered++;
            if (haystack.contains(token)) {
                hits++;
            }
        }

        if (considered == 0) {
            return haystack.contains(target);
        }

        int neededHits = Math.min(2, considered);
        if (hits >= neededHits) {
            return true;
        }

        String artistToken = normalizeForMatch(expectedArtist);
        return !artistToken.isEmpty() && haystack.contains(artistToken) && hits >= 1;
    }

    @NonNull
    private String normalizeForMatch(@Nullable String text) {
        if (text == null) {
            return "";
        }

        String raw = text.toLowerCase(Locale.US);
        StringBuilder builder = new StringBuilder(raw.length());
        boolean previousWasSpace = false;

        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (Character.isLetterOrDigit(c)) {
                builder.append(c);
                previousWasSpace = false;
                continue;
            }

            if (!previousWasSpace) {
                builder.append(' ');
                previousWasSpace = true;
            }
        }

        return builder.toString().trim();
    }

    @NonNull
    private String readTextResponse(@NonNull String urlValue, @NonNull String accept) {
        HttpURLConnection connection = null;

        try {
            URL url = new URL(urlValue);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setRequestProperty("User-Agent", "Sleppify-Offline/1.0");
            connection.setRequestProperty("Accept", accept);
            connection.setUseCaches(false);

            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) {
                Log.w(TAG, "http:get_non_2xx code=" + code + " url=" + sanitizeUrlForLog(urlValue));
                return "";
            }

            try (BufferedInputStream in = new BufferedInputStream(connection.getInputStream());
                 ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                }
                return out.toString(StandardCharsets.UTF_8.name());
            }
        } catch (Exception e) {
            Log.e(TAG, "http:get_exception url=" + sanitizeUrlForLog(urlValue) + " message=" + e.getMessage(), e);
            return "";
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private boolean downloadFromUrl(@NonNull String urlValue, @NonNull File target) {
        File temp = new File(target.getAbsolutePath() + ".tmp");

        for (int attempt = 1; attempt <= DOWNLOAD_MAX_RETRIES; attempt++) {
            HttpURLConnection connection = null;
            long startedAt = System.currentTimeMillis();

            try {
                long resumeFromBytes = temp.isFile() ? Math.max(0L, temp.length()) : 0L;

                URL url = new URL(urlValue);
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
                connection.setReadTimeout(READ_TIMEOUT_MS);
                connection.setRequestProperty("User-Agent", "Sleppify-Offline/1.0");
                connection.setRequestProperty("Accept-Encoding", "identity");
                connection.setRequestProperty("Connection", "close");
                if (resumeFromBytes > 0L) {
                    connection.setRequestProperty("Range", "bytes=" + resumeFromBytes + "-");
                }
                connection.setUseCaches(false);

                int code = connection.getResponseCode();

                // Some CDN links respond 416 when the requested range is already complete.
                if (code == 416 && resumeFromBytes > 0L) {
                    long totalBytesFromHeader = parseTotalBytesFromContentRange(connection.getHeaderField("Content-Range"));
                    if (totalBytesFromHeader > 0L && totalBytesFromHeader == resumeFromBytes) {
                        if (target.exists() && !target.delete()) {
                            return false;
                        }
                        if (temp.renameTo(target)) {
                            notificationTrackBytes = target.length();
                            notificationTrackTotalBytes = target.length();
                            updateForegroundNotification(
                                    notificationDone,
                                    notificationTotal,
                                    notificationDownloaded,
                                    notificationTrackId,
                                    notificationTrackTitle,
                                    true
                            );
                            Log.d(TAG,
                                    "http:download_ok_from_partial bytes=" + target.length()
                                            + " attempt=" + attempt
                                            + "/" + DOWNLOAD_MAX_RETRIES
                                            + " url=" + sanitizeUrlForLog(urlValue));
                            return true;
                        }
                    }
                    temp.delete();
                    resumeFromBytes = 0L;
                }

                if (code < 200 || code >= 300) {
                    boolean shouldRetry = attempt < DOWNLOAD_MAX_RETRIES && shouldRetryHttpCode(code);
                    Log.w(TAG,
                            "http:download_non_2xx code=" + code
                                    + " attempt=" + attempt
                                    + "/" + DOWNLOAD_MAX_RETRIES
                                    + " retry=" + shouldRetry
                                    + " resumeFrom=" + resumeFromBytes
                                    + " url=" + sanitizeUrlForLog(urlValue));

                    if (shouldRetry) {
                        SystemClock.sleep(DOWNLOAD_RETRY_BACKOFF_MS * attempt);
                        continue;
                    }
                    return false;
                }

                boolean appending = resumeFromBytes > 0L && code == HttpURLConnection.HTTP_PARTIAL;
                if (resumeFromBytes > 0L && !appending) {
                    // If server ignored range and sent 200, restart from scratch to avoid file corruption.
                    if (temp.exists() && !temp.delete()) {
                        return false;
                    }
                    resumeFromBytes = 0L;
                }

                long contentLength = connection.getContentLengthLong();
                long expectedTotalBytes = contentLength > 0L
                        ? (appending ? resumeFromBytes + contentLength : contentLength)
                        : -1L;

                notificationTrackBytes = resumeFromBytes;
                notificationTrackTotalBytes = expectedTotalBytes;
                updateForegroundNotification(
                        notificationDone,
                        notificationTotal,
                        notificationDownloaded,
                        notificationTrackId,
                        notificationTrackTitle,
                        true
                );

                try (BufferedInputStream in = new BufferedInputStream(connection.getInputStream());
                     FileOutputStream out = new FileOutputStream(temp, appending)) {
                    byte[] buffer = new byte[8192];
                    int read;
                    long writtenBytes = resumeFromBytes;
                    long nextProgressLogAt = Math.max(DOWNLOAD_PROGRESS_LOG_STEP_BYTES, writtenBytes + DOWNLOAD_PROGRESS_LOG_STEP_BYTES);
                    while ((read = in.read(buffer)) != -1) {
                        out.write(buffer, 0, read);
                        writtenBytes += read;
                        notificationTrackBytes = writtenBytes;
                        updateForegroundNotification(
                                notificationDone,
                                notificationTotal,
                                notificationDownloaded,
                                notificationTrackId,
                                notificationTrackTitle,
                                false
                        );
                        if (writtenBytes >= nextProgressLogAt) {
                            Log.d(TAG,
                                    "http:download_progress bytes=" + writtenBytes
                                            + " attempt=" + attempt
                                            + "/" + DOWNLOAD_MAX_RETRIES
                                            + " resume=" + appending
                                            + " url=" + sanitizeUrlForLog(urlValue));
                            nextProgressLogAt += DOWNLOAD_PROGRESS_LOG_STEP_BYTES;
                        }
                    }
                    out.flush();
                }

                if (!temp.isFile() || temp.length() <= 0L) {
                    temp.delete();
                    return false;
                }

                if (target.exists() && !target.delete()) {
                    return false;
                }

                if (!temp.renameTo(target)) {
                    return false;
                }

                long elapsedMs = Math.max(1L, System.currentTimeMillis() - startedAt);
                notificationTrackBytes = target.length();
                if (notificationTrackTotalBytes <= 0L) {
                    notificationTrackTotalBytes = target.length();
                }
                updateForegroundNotification(
                        notificationDone,
                        notificationTotal,
                        notificationDownloaded,
                        notificationTrackId,
                        notificationTrackTitle,
                        true
                );
                Log.d(TAG,
                        "http:download_ok bytes=" + target.length()
                                + " elapsedMs=" + elapsedMs
                                + " attempt=" + attempt
                                + "/" + DOWNLOAD_MAX_RETRIES
                                + " resumedFrom=" + resumeFromBytes
                                + " url=" + sanitizeUrlForLog(urlValue));
                return true;
            } catch (Exception e) {
                boolean shouldRetry = attempt < DOWNLOAD_MAX_RETRIES && isTransientDownloadException(e);
                Log.e(TAG,
                        "http:download_exception url=" + sanitizeUrlForLog(urlValue)
                                + " attempt=" + attempt
                                + "/" + DOWNLOAD_MAX_RETRIES
                                + " retry=" + shouldRetry
                                + " partialBytes=" + (temp.isFile() ? temp.length() : 0L)
                                + " message=" + e.getMessage(),
                        e);

                if (shouldRetry) {
                    SystemClock.sleep(DOWNLOAD_RETRY_BACKOFF_MS * attempt);
                    continue;
                }
                return false;
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }

        return false;
    }

    private long parseTotalBytesFromContentRange(@Nullable String contentRangeHeader) {
        if (TextUtils.isEmpty(contentRangeHeader)) {
            return -1L;
        }

        int slash = contentRangeHeader.lastIndexOf('/');
        if (slash < 0 || slash + 1 >= contentRangeHeader.length()) {
            return -1L;
        }

        String totalPart = contentRangeHeader.substring(slash + 1).trim();
        if (TextUtils.isEmpty(totalPart) || "*".equals(totalPart)) {
            return -1L;
        }

        try {
            return Long.parseLong(totalPart);
        } catch (Exception ignored) {
            return -1L;
        }
    }

    private boolean shouldRetryHttpCode(int code) {
        return code == 408 || code == 429 || (code >= 500 && code <= 599);
    }

    private boolean isTransientDownloadException(@NonNull Exception exception) {
        Throwable cursor = exception;
        while (cursor != null) {
            if (cursor instanceof java.net.SocketTimeoutException
                    || cursor instanceof java.net.SocketException
                    || cursor instanceof javax.net.ssl.SSLException
                    || cursor instanceof java.io.EOFException
                    || cursor instanceof java.io.InterruptedIOException) {
                return true;
            }
            cursor = cursor.getCause();
        }
        return false;
    }

    @NonNull
    private String sanitizeUrlForLog(@Nullable String rawUrl) {
        if (TextUtils.isEmpty(rawUrl)) {
            return "";
        }

        try {
            Uri uri = Uri.parse(rawUrl);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme();
            String host = uri.getHost() == null ? "" : uri.getHost();
            String path = uri.getPath() == null ? "" : uri.getPath();
            return scheme + "://" + host + path;
        } catch (Exception ignored) {
            return rawUrl;
        }
    }
}
