package com.example.sleppify;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.media.MediaExtractor;
import android.media.MediaMetadataRetriever;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.work.Data;
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
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.schabi.newpipe.extractor.MediaFormat;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.services.youtube.ItagItem;
import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.StreamExtractor;

import org.json.JSONArray;
import org.json.JSONObject;

public final class OfflinePlaylistDownloadWorker extends Worker {

    private static final String TAG = "OfflineDlWorker";

    public static final String INPUT_PLAYLIST_ID = "input_playlist_id";
    public static final String INPUT_VIDEO_IDS = "input_video_ids";
    public static final String INPUT_TITLES = "input_titles";
    public static final String INPUT_ARTISTS = "input_artists";
    public static final String INPUT_DURATIONS = "input_durations";
    public static final String INPUT_ALREADY_OFFLINE_COUNT = "input_already_offline_count";
    public static final String INPUT_TOTAL_WITH_VIDEO_ID = "input_total_with_video_id";
    public static final String INPUT_PLAYLIST_TITLE = "input_playlist_title";
    public static final String INPUT_USER_INITIATED = "input_user_initiated";
    public static final String INPUT_MANUAL_QUEUE = "input_manual_queue";

    public static final String PROGRESS_DONE = "progress_done";
    public static final String PROGRESS_TOTAL = "progress_total";
    public static final String PROGRESS_LAST_ID = "progress_last_id";
    public static final String PROGRESS_CURRENT_ID = "progress_current_id";
    public static final String PROGRESS_ACTIVE_IDS = "progress_active_ids";
    public static final String PROGRESS_ACTIVE_FRACTIONS = "progress_active_fractions";
    public static final String PROGRESS_DOWNLOADED = "progress_downloaded";
    public static final String PROGRESS_PLAYLIST_TITLE = "progress_playlist_title";

    public static final String OUTPUT_DOWNLOADED = "output_downloaded";
    public static final String OUTPUT_TOTAL = "output_total";
    public static final String OUTPUT_REASON = "output_reason";
    public static final String OUTPUT_REASON_NONE = "none";
    public static final String OUTPUT_REASON_NO_NETWORK = "no_network";
    public static final String OUTPUT_REASON_NO_MATCH = "no_match";

    private static final int CONNECT_TIMEOUT_MS = 12000;
    private static final int READ_TIMEOUT_MS = 35000;
    private static final long DOWNLOAD_PROGRESS_LOG_STEP_BYTES = 2L * 1024L * 1024L;
    private static final int DOWNLOAD_MAX_RETRIES = 15;
    private static final long DOWNLOAD_RETRY_BACKOFF_MS = 1200L;
    private static final int MAX_PARALLEL_DOWNLOADS_AUTO = 3;
    private static final int MAX_PARALLEL_DOWNLOADS_MANUAL = 1;
    private static final int MAX_NEWPIPE_AUTOMATIC_FAILURES = 3;
    private static final int TARGET_M4A_BITRATE_LOW = 64_000;
    private static final int TARGET_M4A_BITRATE_MEDIUM = 128_000;
    private static final int TARGET_M4A_BITRATE_HIGH = 256_000;
    private static final int TARGET_M4A_BITRATE_VERY_HIGH = 320_000;
    private static final long MIN_VALID_AUDIO_FILE_BYTES = 24L * 1024L;
    private static final int DOWNLOAD_DURATION_MIN_SAFE_SECONDS = 6;
    private static final float DOWNLOAD_DURATION_MATCH_RATIO = 0.88f;
    private static final int DOWNLOAD_DURATION_LEEWAY_SECONDS = 8;
    private static final int MIN_STEREO_CHANNELS = 2;
    private static final Object NEWPIPE_INIT_LOCK = new Object();
    private static volatile boolean newPipeInitialized;

    public OfflinePlaylistDownloadWorker(
            @NonNull Context context,
            @NonNull WorkerParameters params
    ) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context appContext = getApplicationContext();
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
        String[] titles = getInputData().getStringArray(INPUT_TITLES);
        String[] artists = getInputData().getStringArray(INPUT_ARTISTS);
        String[] durations = getInputData().getStringArray(INPUT_DURATIONS);

        int pendingTotal = ids.length;
        int alreadyOfflineCount = Math.max(0, getInputData().getInt(INPUT_ALREADY_OFFLINE_COUNT, 0));
        int totalWithVideoId = getInputData().getInt(INPUT_TOTAL_WITH_VIDEO_ID, pendingTotal + alreadyOfflineCount);
        int total = Math.max(pendingTotal + alreadyOfflineCount, totalWithVideoId);
        int done = Math.min(total, alreadyOfflineCount);
        int downloaded = Math.min(total, alreadyOfflineCount);
        boolean userInitiated = getInputData().getBoolean(INPUT_USER_INITIATED, false);
        boolean manualQueue = getInputData().getBoolean(INPUT_MANUAL_QUEUE, false);
        int maxParallelDownloads = manualQueue
            ? MAX_PARALLEL_DOWNLOADS_MANUAL
            : MAX_PARALLEL_DOWNLOADS_AUTO;
        boolean encounteredNoNetwork = false;
        Set<String> restrictedIds = Collections.synchronizedSet(
                new HashSet<>(OfflineRestrictionStore.getRestrictedVideoIds(appContext))
        );
        Set<String> activeTrackIds = Collections.synchronizedSet(new HashSet<>());
        Map<String, Float> activeTrackProgressFractions = Collections.synchronizedMap(new HashMap<>());

        Log.d(TAG,
            "doWork:start total=" + total
                + " pending=" + pendingTotal
                + " alreadyOffline=" + alreadyOfflineCount
                + " manualQueue=" + manualQueue
                + " maxParallel=" + maxParallelDownloads);
        ActiveProgressSnapshot initialSnapshot = snapshotActiveProgress(activeTrackIds, activeTrackProgressFractions);
        publishProgress(playlistTitle, done, total, downloaded, "", initialSnapshot.trackIds, initialSnapshot.fractions);

        ExecutorService executor = Executors.newFixedThreadPool(maxParallelDownloads);
        ExecutorCompletionService<TrackDownloadResult> completionService =
                new ExecutorCompletionService<>(executor);
        int submittedTasks = 0;

        for (int i = 0; i < ids.length; i++) {
            if (isStopped()) {
                executor.shutdownNow();
                Log.w(TAG, "doWork: worker stopped before enqueue, retrying");
                return Result.retry();
            }

            final int trackIndex = i;
            completionService.submit(
                    () -> processSingleTrackDownload(
                            appContext,
                            userInitiated,
                            trackIndex,
                            ids,
                            titles,
                            artists,
                                durations,
                            restrictedIds,
                            activeTrackIds,
                            activeTrackProgressFractions
                    )
            );
            submittedTasks++;
        }

        for (int i = 0; i < submittedTasks; i++) {
            if (isStopped()) {
                executor.shutdownNow();
                Log.w(TAG, "doWork: worker stopped, retrying");
                return Result.retry();
            }

            TrackDownloadResult trackResult = null;
            while (trackResult == null) {
                if (isStopped()) {
                    executor.shutdownNow();
                    Log.w(TAG, "doWork: worker stopped while waiting for result, retrying");
                    return Result.retry();
                }

                try {
                    Future<TrackDownloadResult> future = completionService.poll(650L, TimeUnit.MILLISECONDS);
                    if (future == null) {
                        ActiveProgressSnapshot activeSnapshot = snapshotActiveProgress(activeTrackIds, activeTrackProgressFractions);
                        String currentId = activeSnapshot.trackIds.length > 0 ? activeSnapshot.trackIds[0] : "";
                        publishProgress(
                                playlistTitle,
                                done,
                                total,
                                downloaded,
                                currentId,
                                activeSnapshot.trackIds,
                                activeSnapshot.fractions
                        );
                        continue;
                    }

                    trackResult = future.get();
                    if (trackResult == null) {
                        trackResult = TrackDownloadResult.failed("", "");
                    }
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    trackResult = TrackDownloadResult.failed("", "");
                } catch (Exception e) {
                    Log.e(TAG, "track:future_exception message=" + e.getMessage(), e);
                    trackResult = TrackDownloadResult.failed("", "");
                }
            }

            if (trackResult == null) {
                continue;
            }

            if (trackResult.noNetworkEncountered) {
                encounteredNoNetwork = true;
            }
            if (trackResult.downloaded) {
                downloaded = Math.min(total, downloaded + 1);
            }

            done = Math.min(total, done + 1);
        ActiveProgressSnapshot activeSnapshot = snapshotActiveProgress(activeTrackIds, activeTrackProgressFractions);
        String currentId = activeSnapshot.trackIds.length > 0 ? activeSnapshot.trackIds[0] : trackResult.trackId;
            publishProgress(
                    playlistTitle,
                    done,
                    total,
                    downloaded,
                    currentId,
            activeSnapshot.trackIds,
            activeSnapshot.fractions
            );
        }

        executor.shutdownNow();

        String reason = OUTPUT_REASON_NONE;
        if (encounteredNoNetwork) {
            reason = OUTPUT_REASON_NO_NETWORK;
        } else if (downloaded <= 0) {
            reason = OUTPUT_REASON_NO_MATCH;
        }

        Log.d(TAG, "doWork:end downloaded=" + downloaded + "/" + total + " reason=" + reason);

        if (OUTPUT_REASON_NO_NETWORK.equals(reason)) {
            Log.w(TAG, "doWork:network_unavailable retrying");
            return Result.retry();
        }

        return Result.success(new Data.Builder()
                .putInt(OUTPUT_TOTAL, total)
                .putInt(OUTPUT_DOWNLOADED, downloaded)
                .putString(OUTPUT_REASON, reason)
                .build());
    }

    private void publishProgress(
            @NonNull String playlistTitle,
            int done,
            int total,
            int downloaded,
            @Nullable String currentId,
            @NonNull String[] activeTrackIds,
            @NonNull float[] activeFractions
    ) {
        setProgressAsync(new Data.Builder()
                .putInt(PROGRESS_DONE, done)
                .putInt(PROGRESS_TOTAL, total)
                .putString(PROGRESS_PLAYLIST_TITLE, playlistTitle)
                .putString(PROGRESS_CURRENT_ID, currentId == null ? "" : currentId)
                .putString(PROGRESS_LAST_ID, currentId == null ? "" : currentId)
                .putStringArray(PROGRESS_ACTIVE_IDS, activeTrackIds)
            .putFloatArray(PROGRESS_ACTIVE_FRACTIONS, activeFractions)
                .putInt(PROGRESS_DOWNLOADED, downloaded)
                .build());
    }

    @NonNull
    private TrackDownloadResult processSingleTrackDownload(
            @NonNull Context context,
            boolean userInitiated,
            int trackIndex,
            @NonNull String[] ids,
            @Nullable String[] titles,
            @Nullable String[] artists,
            @Nullable String[] durations,
            @NonNull Set<String> restrictedIds,
            @NonNull Set<String> activeTrackIds,
            @NonNull Map<String, Float> activeTrackProgressFractions
    ) {
        String rawId = ids[trackIndex];
        String id = rawId == null ? "" : rawId.trim();
        String title = safeArrayValue(titles, trackIndex);
        String artist = safeArrayValue(artists, trackIndex);
        String expectedDurationLabel = safeArrayValue(durations, trackIndex);

        if (TextUtils.isEmpty(id)) {
            return TrackDownloadResult.failed(id, title);
        }

        boolean wasRestricted = OfflineRestrictionStore.isRestricted(restrictedIds, id)
                || OfflineRestrictionStore.isRestricted(context, id);
        if (wasRestricted) {
            restrictedIds.add(id);
        }
        if (wasRestricted) {
            Log.d(TAG, "track:restricted_skip id=" + id + " userInitiated=" + userInitiated);
            return TrackDownloadResult.failed(id, title);
        }

        int automaticFailCount = OfflineRestrictionStore.getAutomaticNewPipeFailureCount(context, id);
        if (!userInitiated && automaticFailCount >= MAX_NEWPIPE_AUTOMATIC_FAILURES) {
            OfflineRestrictionStore.markRestricted(context, id);
            restrictedIds.add(id);
            Log.w(TAG,
                    "track:auto_blocked_after_newpipe_failures id=" + id
                            + " fails=" + automaticFailCount);
            return TrackDownloadResult.failed(id, title);
        }

        boolean downloaded = OfflineAudioStore.hasValidatedOfflineAudio(context, id, expectedDurationLabel);
        boolean noNetworkEncountered = false;
        boolean addedToActive = false;
        NetworkIssueTracker networkIssueTracker = new NetworkIssueTracker();

        if (downloaded) {
            Log.d(TAG, "track:already_offline id=" + id);
            return TrackDownloadResult.downloaded(id, title, false);
        }

        try {
            addedToActive = activeTrackIds.add(id);
            activeTrackProgressFractions.put(id, 0f);

            ProgressReporter progressReporter = progressFraction -> {
                float safeProgress = Math.max(0f, Math.min(1f, progressFraction));
                activeTrackProgressFractions.put(id, safeProgress);
            };

            boolean internetReady = isInternetAvailable(context);
            if (!internetReady) {
                noNetworkEncountered = true;
                Log.w(TAG, "track:no_network id=" + id);
                return TrackDownloadResult.failed(id, title, noNetworkEncountered);
            }

            if (!isDownloadAllowedByCurrentNetworkPolicy(context, networkIssueTracker)) {
                noNetworkEncountered = true;
                Log.w(TAG, "track:blocked_mobile_data_policy id=" + id);
                return TrackDownloadResult.failed(id, title, true);
            }

            int targetBitrate = resolveTargetBitrate(context);
            downloaded = downloadTrackFromYouTubeNewPipe(context, id, targetBitrate, networkIssueTracker, progressReporter);
            noNetworkEncountered = noNetworkEncountered || networkIssueTracker.hasIssue();
            Log.d(TAG, "track:newpipe_result id=" + id + " ok=" + downloaded);
            if (downloaded) {
                OfflineRestrictionStore.clearAutomaticNewPipeFailures(context, id);
            } else if (!networkIssueTracker.hasIssue() && !userInitiated) {
                int failures = OfflineRestrictionStore.incrementAutomaticNewPipeFailure(context, id);
                Log.w(TAG,
                        "track:newpipe_auto_fail id=" + id
                                + " failures=" + failures
                                + "/" + MAX_NEWPIPE_AUTOMATIC_FAILURES);
                if (failures >= MAX_NEWPIPE_AUTOMATIC_FAILURES) {
                    OfflineRestrictionStore.markRestricted(context, id);
                    restrictedIds.add(id);
                    Log.w(TAG, "track:newpipe_auto_restricted id=" + id);
                    return TrackDownloadResult.failed(id, title, noNetworkEncountered);
                }
            }

            if (!downloaded && !networkIssueTracker.hasIssue() && userInitiated) {
                downloaded = downloadTrackFromArchiveCatalog(context, id, title, artist, networkIssueTracker, progressReporter);
                noNetworkEncountered = noNetworkEncountered || networkIssueTracker.hasIssue();
                Log.d(TAG, "track:archive_result id=" + id + " ok=" + downloaded);
            } else if (!downloaded) {
                if (networkIssueTracker.hasIssue()) {
                    Log.w(TAG, "track:skip_archive_due_network id=" + id);
                } else {
                    Log.d(TAG, "track:skip_archive_auto_mode id=" + id);
                }
            }

            if (!downloaded) {
                if (noNetworkEncountered) {
                    Log.w(TAG, "track:failed_network id=" + id);
                    return TrackDownloadResult.failed(id, title, true);
                }
                if (userInitiated) {
                    Log.w(TAG, "track:failed_manual_retry id=" + id);
                } else {
                    int failures = OfflineRestrictionStore.getAutomaticNewPipeFailureCount(context, id);
                    Log.w(TAG,
                            "track:failed_auto id=" + id
                                    + " auto_failures=" + failures
                                    + "/" + MAX_NEWPIPE_AUTOMATIC_FAILURES);
                }
                return TrackDownloadResult.failed(id, title, noNetworkEncountered);
            }

            if (!validateDownloadedTrackFile(context, id, expectedDurationLabel)) {
                downloaded = false;
                Log.w(TAG,
                        "track:validation_failed id=" + id
                                + " expectedDuration='" + expectedDurationLabel + "'");
                return TrackDownloadResult.failed(id, title, noNetworkEncountered);
            }

            progressReporter.onProgress(1f);
            OfflineRestrictionStore.unmarkRestricted(context, id);
            restrictedIds.remove(id);
            OfflineRestrictionStore.clearAutomaticNewPipeFailures(context, id);
            OfflineAudioStore.markOfflineAudioState(id, true);
            Log.d(TAG, "track:success id=" + id);
            return TrackDownloadResult.downloaded(id, title, noNetworkEncountered);
        } finally {
            if (addedToActive) {
                activeTrackIds.remove(id);
            }
            activeTrackProgressFractions.remove(id);
        }
    }

    @NonNull
    private ActiveProgressSnapshot snapshotActiveProgress(
            @NonNull Set<String> activeTrackIds,
            @NonNull Map<String, Float> activeTrackProgressFractions
    ) {
        synchronized (activeTrackIds) {
            String[] ids = activeTrackIds.toArray(new String[0]);
            float[] fractions = new float[ids.length];
            for (int i = 0; i < ids.length; i++) {
                String id = ids[i];
                Float value = activeTrackProgressFractions.get(id);
                fractions[i] = value == null ? 0f : Math.max(0f, Math.min(1f, value));
            }
            return new ActiveProgressSnapshot(ids, fractions);
        }
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

    private boolean isDownloadAllowedByCurrentNetworkPolicy(
            @NonNull Context context,
            @NonNull NetworkIssueTracker networkIssueTracker
    ) {
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

        boolean internetReady = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
        if (!internetReady) {
            return false;
        }

        SharedPreferences prefs = context.getSharedPreferences(CloudSyncManager.PREFS_SETTINGS, Context.MODE_PRIVATE);
        boolean allowMobileData = prefs.getBoolean(
                CloudSyncManager.KEY_OFFLINE_DOWNLOAD_ALLOW_MOBILE_DATA,
                false
        );

        if (allowMobileData) {
            return true;
        }

        boolean usesCellular = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR);
        boolean usesWifi = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
        boolean usesEthernet = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET);

        if (usesCellular && !usesWifi && !usesEthernet) {
            networkIssueTracker.markIssue();
            return false;
        }

        return true;
    }

    @NonNull
    private String safeArrayValue(@Nullable String[] values, int index) {
        if (values == null || index < 0 || index >= values.length) {
            return "";
        }
        String raw = values[index];
        return raw == null ? "" : raw.trim();
    }

    private boolean downloadTrackFromYouTubeNewPipe(
            @NonNull Context context,
            @NonNull String videoId,
            int targetBitrate,
            @NonNull NetworkIssueTracker networkIssueTracker,
            @Nullable ProgressReporter progressReporter
    ) {
        if (!ensureNewPipeInitialized()) {
            Log.w(TAG, "newpipe:init_failed id=" + videoId);
            return false;
        }

        String[] sourceCandidates = buildYouTubeSourceCandidates(videoId);
        for (String sourceUrl : sourceCandidates) {
            try {
                if (!isDownloadAllowedByCurrentNetworkPolicy(context, networkIssueTracker)) {
                    networkIssueTracker.markIssue();
                    Log.w(TAG,
                            "newpipe:blocked_mobile_data_policy id=" + videoId
                                    + " source=" + sanitizeUrlForLog(sourceUrl));
                    return false;
                }

                StreamExtractor extractor = ServiceList.YouTube.getStreamExtractor(sourceUrl);
                extractor.fetchPage();

                List<AudioStream> rawStreams = extractor.getAudioStreams();
                if (rawStreams != null) {
                    for (AudioStream rs : rawStreams) {
                        String fName = rs.getFormat() == null ? "unknown" : rs.getFormat().name();
                        boolean isOpus = fName.contains("WEBMA") || fName.contains("OPUS");
                        Log.d(TAG, "newpipe:raw_stream videoId=" + videoId 
                                + " format=" + fName
                                + " isWebm=" + isOpus
                                + " bitrate=" + rs.getBitrate()
                                + " averageBitrate=" + rs.getAverageBitrate()
                                + " channels=" + (rs.getItagItem() != null ? rs.getItagItem().getAudioChannels() : -1));
                    }
                }

                List<AudioStream> candidates = selectAudioStreamsByPriority(rawStreams, targetBitrate);
                if (candidates.isEmpty()) {
                    Log.w(TAG, "newpipe:no_audio_stream (This track might be restricted or region-locked) id=" + videoId + " url=" + sanitizeUrlForLog(sourceUrl));
                    continue;
                }

                for (AudioStream selected : candidates) {
                    if (selected == null || TextUtils.isEmpty(selected.getContent())) {
                        continue;
                    }

                    String directUrl = selected.getContent();
                    String format = selected.getFormat() == null
                            ? "unknown"
                            : selected.getFormat().name().toLowerCase(Locale.US);

                    String fName = selected.getFormat() == null ? "unknown" : selected.getFormat().name().toUpperCase(Locale.US);
                    boolean isWebm = fName.contains("WEBMA") || fName.contains("OPUS");
                    File target = OfflineAudioStore.getOfflineAudioFileForFormat(context, videoId, isWebm);
                    File parent = target.getParentFile();
                    if (parent != null && !parent.exists()) {
                        parent.mkdirs();
                    }

                    Log.d(TAG,
                            "newpipe:attempt id=" + videoId
                                    + " source=" + sanitizeUrlForLog(sourceUrl)
                                    + " format=" + format
                                    + " target_bitrate=" + targetBitrate
                                    + " bitrate=" + audioBitrate(selected)
                                    + " direct=" + sanitizeUrlForLog(directUrl));

                    if (!downloadFromUrl(context, directUrl, target, null, networkIssueTracker, progressReporter)) {
                        continue;
                    }

                    int channelCount = readAudioChannelCount(target);
                    if (channelCount >= MIN_STEREO_CHANNELS) {
                        Log.d(TAG,
                                "newpipe:success id=" + videoId
                                        + " format=" + format
                                        + " channels=" + channelCount);
                        return true;
                    }

                    Log.w(TAG,
                            "newpipe:reject_non_stereo id=" + videoId
                                    + " format=" + format
                                    + " channels=" + channelCount);
                    OfflineAudioStore.deleteOfflineAudio(context, videoId);
                }
            } catch (Exception e) {
                if (isLikelyNetworkException(e)) {
                    networkIssueTracker.markIssue();
                }
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

    private int resolveTargetBitrate(@NonNull Context context) {
        SharedPreferences prefs = context.getSharedPreferences(CloudSyncManager.PREFS_SETTINGS, Context.MODE_PRIVATE);
        String configured = prefs.getString(
                CloudSyncManager.KEY_OFFLINE_DOWNLOAD_QUALITY,
                CloudSyncManager.DOWNLOAD_QUALITY_VERY_HIGH
        );
        if (CloudSyncManager.DOWNLOAD_QUALITY_LOW.equals(configured)) {
            return TARGET_M4A_BITRATE_LOW;
        }
        if (CloudSyncManager.DOWNLOAD_QUALITY_MEDIUM.equals(configured)) {
            return TARGET_M4A_BITRATE_MEDIUM;
        }
        if (CloudSyncManager.DOWNLOAD_QUALITY_HIGH.equals(configured)) {
            return TARGET_M4A_BITRATE_HIGH;
        }
        if (CloudSyncManager.DOWNLOAD_QUALITY_VERY_HIGH.equals(configured)) {
            return TARGET_M4A_BITRATE_VERY_HIGH;
        }
        return TARGET_M4A_BITRATE_MEDIUM;
    }


    private boolean ensureNewPipeInitialized() {
        if (newPipeInitialized) {
            return true;
        }

        synchronized (NEWPIPE_INIT_LOCK) {
            if (newPipeInitialized) {
                return true;
            }

            try {
                NewPipe.init(NewPipeHttpDownloader.getInstance());
                newPipeInitialized = true;
                Log.d(TAG, "newpipe:init_success");
                return true;
            } catch (Exception e) {
                Log.e(TAG, "newpipe:init_exception message=" + e.getMessage(), e);
                return false;
            }
        }
    }

    @Nullable
    private AudioStream selectBestAudioStream(@Nullable List<AudioStream> streams, int targetBitrate) {
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

        AudioStream selected = selectFastestCandidate(m4aCandidates, targetBitrate);
        if (selected != null) {
            return selected;
        }

        selected = selectFastestCandidate(fallbackCandidates, targetBitrate);
        if (selected != null) {
            return selected;
        }

        if (m4aCandidates.isEmpty() && fallbackCandidates.isEmpty()) {
            return null;
        }

        return !m4aCandidates.isEmpty() ? m4aCandidates.get(0) : fallbackCandidates.get(0);
    }

    @NonNull
    private List<AudioStream> selectAudioStreamsByPriority(@Nullable List<AudioStream> streams, int targetBitrate) {
        if (streams == null || streams.isEmpty()) {
            return Collections.emptyList();
        }

        List<AudioStream> opusCandidates = new ArrayList<>();
        List<AudioStream> m4aCandidates = new ArrayList<>();
        List<AudioStream> fallbackCandidates = new ArrayList<>();
        
        for (AudioStream stream : streams) {
            if (stream == null || TextUtils.isEmpty(stream.getContent())) {
                continue;
            }

            String fName = stream.getFormat() == null ? "unknown" : stream.getFormat().name().toUpperCase(Locale.US);
            boolean isWebm = fName.contains("WEBMA") || fName.contains("OPUS");

            int declaredChannels = extractDeclaredAudioChannelCount(stream);
            if (declaredChannels > 0 && declaredChannels < MIN_STEREO_CHANNELS) {
                if (isWebm) {
                    Log.d(TAG, "newpipe:bypassing_channel_check_for_opus channels=" + declaredChannels);
                } else {
                    Log.d(TAG,
                            "newpipe:skip_declared_non_stereo format="
                                    + (stream.getFormat() == null ? "unknown" : stream.getFormat().name())
                                    + " channels=" + declaredChannels
                                    + " bitrate=" + audioBitrate(stream));
                    continue;
                }
            }

            if (isWebm) {
                opusCandidates.add(stream);
            } else if (fName.contains("M4A")) {
                m4aCandidates.add(stream);
            } else {
                fallbackCandidates.add(stream);
            }
        }

        List<AudioStream> ordered = new ArrayList<>();
        // 1. Force prioritizing Opus for True Stereo guarantee
        appendCandidatePriorityOrder(ordered, opusCandidates, targetBitrate);
        
        // 2. M4A as strict fallback just in case Opus is missing (extremely rare)
        appendCandidatePriorityOrder(ordered, m4aCandidates, targetBitrate);
        
        // 3. Any other format
        appendCandidatePriorityOrder(ordered, fallbackCandidates, targetBitrate);

        if (ordered.isEmpty()) {
            if (!opusCandidates.isEmpty()) {
                ordered.addAll(opusCandidates);
            }
            if (!m4aCandidates.isEmpty()) {
                ordered.addAll(m4aCandidates);
            }
            if (!fallbackCandidates.isEmpty()) {
                ordered.addAll(fallbackCandidates);
            }
        }

        return ordered;
    }

    private void appendCandidatePriorityOrder(
            @NonNull List<AudioStream> destination,
            @NonNull List<AudioStream> source,
            int targetBitrate
    ) {
        AudioStream bestUnderTarget = null;
        int bestUnderTargetBitrate = -1;

        AudioStream lowestOverTarget = null;
        int lowestOverTargetBitrate = Integer.MAX_VALUE;

        AudioStream firstUnknownBitrate = null;

        for (AudioStream stream : source) {
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
            destination.add(bestUnderTarget);
        }
        if (lowestOverTarget != null && lowestOverTarget != bestUnderTarget) {
            destination.add(lowestOverTarget);
        }
        if (firstUnknownBitrate != null
                && firstUnknownBitrate != bestUnderTarget
                && firstUnknownBitrate != lowestOverTarget) {
            destination.add(firstUnknownBitrate);
        }

        for (AudioStream stream : source) {
            if (!destination.contains(stream)) {
                destination.add(stream);
            }
        }
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

    private int extractDeclaredAudioChannelCount(@Nullable AudioStream stream) {
        if (stream == null) {
            return -1;
        }

        try {
            ItagItem itagItem = stream.getItagItem();
            if (itagItem == null) {
                return -1;
            }

            int channels = itagItem.getAudioChannels();
            return channels > 0 ? channels : -1;
        } catch (Throwable ignored) {
            return -1;
        }
    }

    @NonNull
    private String[] buildYouTubeSourceCandidates(@NonNull String videoId) {
        String escapedId = Uri.encode(videoId);
        return new String[] {
                "https://music.youtube.com/watch?v=" + escapedId,
                "https://www.youtube.com/watch?v=" + escapedId,
                "https://www.youtube.com/embed/" + escapedId
        };
    }

    private boolean downloadTrackFromArchiveCatalog(
            @NonNull Context context,
            @NonNull String videoId,
            @NonNull String title,
            @NonNull String artist,
            @NonNull NetworkIssueTracker networkIssueTracker,
            @Nullable ProgressReporter progressReporter
    ) {
        File target = OfflineAudioStore.getOfflineAudioFile(context, videoId);
        File parent = target.getParentFile();
        if (parent == null) {
            return false;
        }
        if (!parent.exists() && !parent.mkdirs()) {
            return false;
        }

        String archiveUrl = resolveArchiveAudioUrl(title, artist, networkIssueTracker);
        if (TextUtils.isEmpty(archiveUrl)) {
            Log.w(TAG, "archive:no_match id=" + videoId + " title=" + title + " artist=" + artist);
            return false;
        }

        Log.d(TAG, "archive:downloading id=" + videoId + " url=" + sanitizeUrlForLog(archiveUrl));
        return downloadFromUrl(context, archiveUrl, target, null, networkIssueTracker, progressReporter);
    }

    @NonNull
    private String resolveArchiveAudioUrl(
            @NonNull String title,
            @NonNull String artist,
            @NonNull NetworkIssueTracker networkIssueTracker
    ) {
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

        String body = readTextResponse(searchUri.toString(), "application/json", networkIssueTracker);
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

                String audioUrl = resolveArchiveDownloadUrl(identifier, title, artist, networkIssueTracker);
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
            @NonNull String expectedArtist,
            @NonNull NetworkIssueTracker networkIssueTracker
    ) {
        String metadataUrl = "https://archive.org/metadata/" + Uri.encode(identifier);
        String body = readTextResponse(metadataUrl, "application/json", networkIssueTracker);
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
        private String readTextResponse(
            @NonNull String urlValue,
            @NonNull String accept,
            @NonNull NetworkIssueTracker networkIssueTracker
        ) {
        HttpURLConnection connection = null;

        try {
            if (!isDownloadAllowedByCurrentNetworkPolicy(getApplicationContext(), networkIssueTracker)) {
                return "";
            }

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
            if (isLikelyNetworkException(e)) {
                networkIssueTracker.markIssue();
            }
            Log.e(TAG, "http:get_exception url=" + sanitizeUrlForLog(urlValue) + " message=" + e.getMessage(), e);
            return "";
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private boolean downloadFromUrl(
            @NonNull Context context,
            @NonNull String urlValue,
            @NonNull File target,
            @Nullable Map<String, String> requestHeaders,
            @NonNull NetworkIssueTracker networkIssueTracker,
            @Nullable ProgressReporter progressReporter
    ) {
        File temp = new File(target.getAbsolutePath() + ".tmp");

        for (int attempt = 1; attempt <= DOWNLOAD_MAX_RETRIES; attempt++) {
            HttpURLConnection connection = null;
            long startedAt = System.currentTimeMillis();

            try {
                if (!isDownloadAllowedByCurrentNetworkPolicy(context, networkIssueTracker)) {
                    Log.w(TAG, "http:blocked_mobile_data_policy attempt=" + attempt + "/" + DOWNLOAD_MAX_RETRIES);
                    return false;
                }

                long resumeFromBytes = temp.isFile() ? Math.max(0L, temp.length()) : 0L;

                URL url = new URL(urlValue);
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
                connection.setReadTimeout(READ_TIMEOUT_MS);
                connection.setRequestProperty("User-Agent", "Sleppify-Offline/1.0");
                connection.setRequestProperty("Accept-Encoding", "identity");
                connection.setRequestProperty("Connection", attempt > 1 ? "close" : "keep-alive");
                applyResolvedRequestHeaders(connection, requestHeaders);
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

                try (BufferedInputStream in = new BufferedInputStream(connection.getInputStream());
                     FileOutputStream out = new FileOutputStream(temp, appending)) {
                    byte[] buffer = new byte[65536];
                    int read;
                    long writtenBytes = resumeFromBytes;
                    long nextProgressLogAt = Math.max(DOWNLOAD_PROGRESS_LOG_STEP_BYTES, writtenBytes + DOWNLOAD_PROGRESS_LOG_STEP_BYTES);
                    while ((read = in.read(buffer)) != -1) {
                        out.write(buffer, 0, read);
                        writtenBytes += read;

                        if (!isDownloadAllowedByCurrentNetworkPolicy(context, networkIssueTracker)) {
                            Log.w(TAG,
                                    "http:blocked_mobile_data_policy_mid_download bytes=" + writtenBytes
                                            + " url=" + sanitizeUrlForLog(urlValue));
                            return false;
                        }

                        if (progressReporter != null && expectedTotalBytes > 0L) {
                            progressReporter.onProgress(writtenBytes / (float) expectedTotalBytes);
                        }
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

                    if (expectedTotalBytes > 0L && writtenBytes < expectedTotalBytes) {
                        boolean shouldRetry = attempt < DOWNLOAD_MAX_RETRIES;
                        Log.w(TAG,
                                "http:truncated_download bytes=" + writtenBytes
                                        + " expected=" + expectedTotalBytes
                                        + " attempt=" + attempt
                                        + "/" + DOWNLOAD_MAX_RETRIES
                                        + " retry=" + shouldRetry
                                        + " url=" + sanitizeUrlForLog(urlValue));
                        if (shouldRetry) {
                            SystemClock.sleep(DOWNLOAD_RETRY_BACKOFF_MS * attempt);
                            continue;
                        }
                        return false;
                    }
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
                Log.d(TAG,
                        "http:download_ok bytes=" + target.length()
                                + " elapsedMs=" + elapsedMs
                                + " attempt=" + attempt
                                + "/" + DOWNLOAD_MAX_RETRIES
                                + " resumedFrom=" + resumeFromBytes
                                + " url=" + sanitizeUrlForLog(urlValue));
                return true;
            } catch (Exception e) {
                if (isCancellationInterruption(e)) {
                    Log.w(TAG,
                            "http:download_interrupted_cancelled url=" + sanitizeUrlForLog(urlValue)
                                    + " attempt=" + attempt
                                    + "/" + DOWNLOAD_MAX_RETRIES
                                    + " partialBytes=" + (temp.isFile() ? temp.length() : 0L)
                                    + " message=" + e.getMessage());
                    Thread.currentThread().interrupt();
                    return false;
                }

                if (isLikelyNetworkException(e)) {
                    networkIssueTracker.markIssue();
                }
                boolean isNoSpace = isNoSpaceLeftOnDeviceException(e);
                boolean shouldRetry = !isNoSpace && attempt < DOWNLOAD_MAX_RETRIES && isTransientDownloadException(e);
                
                Log.e(TAG,
                        "http:download_exception url=" + sanitizeUrlForLog(urlValue)
                                + " attempt=" + attempt
                                + "/" + DOWNLOAD_MAX_RETRIES
                                + " retry=" + shouldRetry
                                + " nospace=" + isNoSpace
                                + " partialBytes=" + (temp.isFile() ? temp.length() : 0L)
                                + " message=" + e.getMessage(),
                        e);

                if (shouldRetry) {
                    SystemClock.sleep(DOWNLOAD_RETRY_BACKOFF_MS * attempt);
                    continue;
                }
                
                // Cleanup partial file on permanent failure
                if (temp.exists() && !shouldRetry) {
                    temp.delete();
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

    private boolean isCancellationInterruption(@Nullable Throwable throwable) {
        if (isStopped() || Thread.currentThread().isInterrupted()) {
            return true;
        }

        Throwable cursor = throwable;
        while (cursor != null) {
            if (cursor instanceof InterruptedException) {
                return true;
            }

            if (cursor instanceof java.io.InterruptedIOException
                    && !(cursor instanceof java.net.SocketTimeoutException)) {
                String message = cursor.getMessage();
                if (TextUtils.isEmpty(message)
                        || message.toLowerCase(Locale.US).contains("thread interrupted")) {
                    return true;
                }
            }

            cursor = cursor.getCause();
        }

        return false;
    }

    private boolean validateDownloadedTrackFile(
            @NonNull Context context,
            @NonNull String videoId,
            @Nullable String expectedDurationLabel
    ) {
        File audioFile = OfflineAudioStore.getExistingOfflineAudioFile(context, videoId);
        if (!audioFile.isFile()) {
            return false;
        }

        long sizeBytes = Math.max(0L, audioFile.length());
        if (sizeBytes < MIN_VALID_AUDIO_FILE_BYTES) {
            OfflineAudioStore.deleteOfflineAudio(context, videoId);
            return false;
        }

        int actualDurationSeconds = readAudioDurationSeconds(audioFile);
        if (actualDurationSeconds < DOWNLOAD_DURATION_MIN_SAFE_SECONDS) {
            OfflineAudioStore.deleteOfflineAudio(context, videoId);
            return false;
        }

        int channelCount = readAudioChannelCount(audioFile);
        if (channelCount < MIN_STEREO_CHANNELS) {
            Log.w(TAG,
                    "track:non_stereo_rejected id=" + videoId
                            + " channels=" + channelCount);
            OfflineAudioStore.deleteOfflineAudio(context, videoId);
            return false;
        }

        int expectedDurationSeconds = parseDurationSeconds(expectedDurationLabel);
        if (expectedDurationSeconds <= 0) {
            return true;
        }

        int minimumAllowedDuration = Math.max(
                DOWNLOAD_DURATION_MIN_SAFE_SECONDS,
                Math.round(expectedDurationSeconds * DOWNLOAD_DURATION_MATCH_RATIO) - DOWNLOAD_DURATION_LEEWAY_SECONDS
        );

        if (actualDurationSeconds >= minimumAllowedDuration) {
            return true;
        }

        Log.w(TAG,
                "track:duration_mismatch id=" + videoId
                        + " expectedSec=" + expectedDurationSeconds
                        + " actualSec=" + actualDurationSeconds
                        + " minAllowedSec=" + minimumAllowedDuration);
        OfflineAudioStore.deleteOfflineAudio(context, videoId);
        return false;
    }

    private int readAudioDurationSeconds(@NonNull File file) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(file.getAbsolutePath());
            String durationMsValue = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            if (TextUtils.isEmpty(durationMsValue)) {
                return -1;
            }
            long durationMs = Long.parseLong(durationMsValue.trim());
            if (durationMs <= 0L) {
                return -1;
            }
            return (int) Math.max(1L, durationMs / 1000L);
        } catch (Exception e) {
            return -1;
        } finally {
            try {
                retriever.release();
            } catch (Exception ignored) {
            }
        }
    }

    private int readAudioChannelCount(@NonNull File file) {
        MediaExtractor extractor = new MediaExtractor();
        try {
            extractor.setDataSource(file.getAbsolutePath());
            int trackCount = extractor.getTrackCount();
            for (int i = 0; i < trackCount; i++) {
                android.media.MediaFormat format = extractor.getTrackFormat(i);
                if (format == null || !format.containsKey(android.media.MediaFormat.KEY_MIME)) {
                    continue;
                }
                String mime = format.getString(android.media.MediaFormat.KEY_MIME);
                if (TextUtils.isEmpty(mime) || !mime.startsWith("audio/")) {
                    continue;
                }
                if (format.containsKey(android.media.MediaFormat.KEY_CHANNEL_COUNT)) {
                    int channels = format.getInteger(android.media.MediaFormat.KEY_CHANNEL_COUNT);
                    if (channels > 0) {
                        return channels;
                    }
                }
            }
            return -1;
        } catch (Exception e) {
            return -1;
        } finally {
            try {
                extractor.release();
            } catch (Exception ignored) {
            }
        }
    }

    private int parseDurationSeconds(@Nullable String durationLabel) {
        if (TextUtils.isEmpty(durationLabel)) {
            return -1;
        }

        String[] parts = durationLabel.trim().split(":");
        try {
            if (parts.length == 2) {
                int minutes = Integer.parseInt(parts[0]);
                int seconds = Integer.parseInt(parts[1]);
                return (minutes * 60) + seconds;
            }
            if (parts.length == 3) {
                int hours = Integer.parseInt(parts[0]);
                int minutes = Integer.parseInt(parts[1]);
                int seconds = Integer.parseInt(parts[2]);
                return (hours * 3600) + (minutes * 60) + seconds;
            }
        } catch (Exception ignored) {
            return -1;
        }

        return -1;
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
        return code == 403 || code == 408 || code == 429 || (code >= 500 && code <= 599);
    }

    private void applyResolvedRequestHeaders(
            @NonNull HttpURLConnection connection,
            @Nullable Map<String, String> requestHeaders
    ) {
        if (requestHeaders == null || requestHeaders.isEmpty()) {
            return;
        }

        for (Map.Entry<String, String> entry : requestHeaders.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            if (TextUtils.isEmpty(key) || TextUtils.isEmpty(value)) {
                continue;
            }

            String normalized = key.trim().toLowerCase(Locale.US);
            if ("host".equals(normalized)
                    || "content-length".equals(normalized)
                    || "connection".equals(normalized)
                    || "accept-encoding".equals(normalized)) {
                continue;
            }

            connection.setRequestProperty(key, value);
        }
    }

    private boolean isTransientDownloadException(@NonNull Exception exception) {
        if (isNoSpaceLeftOnDeviceException(exception)) {
            return false;
        }

        Throwable cursor = exception;
        while (cursor != null) {
            if (cursor instanceof java.net.SocketTimeoutException
                    || cursor instanceof java.net.SocketException
                    || cursor instanceof java.net.UnknownHostException
                    || cursor instanceof java.net.ConnectException
                    || cursor instanceof javax.net.ssl.SSLException
                    || cursor instanceof java.io.EOFException
                    || cursor instanceof java.io.InterruptedIOException) {
                return true;
            }
            cursor = cursor.getCause();
        }
        return false;
    }

    private boolean isNoSpaceLeftOnDeviceException(@Nullable Throwable throwable) {
        Throwable cursor = throwable;
        while (cursor != null) {
            String message = cursor.getMessage();
            if (message != null) {
                String lower = message.toLowerCase(Locale.US);
                if (lower.contains("enospc") || lower.contains("no space left on device")) {
                    return true;
                }
            }
            cursor = cursor.getCause();
        }
        return false;
    }

    private boolean isLikelyNetworkException(@Nullable Throwable throwable) {
        Throwable cursor = throwable;
        while (cursor != null) {
            if (cursor instanceof java.net.UnknownHostException
                    || cursor instanceof java.net.SocketTimeoutException
                    || cursor instanceof java.net.ConnectException
                    || cursor instanceof java.net.SocketException
                    || cursor instanceof java.io.InterruptedIOException
                    || cursor instanceof javax.net.ssl.SSLException) {
                return true;
            }

            String message = cursor.getMessage();
            if (message != null) {
                String normalized = message.toLowerCase(Locale.US);
                if (normalized.contains("unable to resolve host")
                        || normalized.contains("no address associated with hostname")
                        || normalized.contains("failed to connect")
                        || normalized.contains("connection timed out")
                        || normalized.contains("network is unreachable")) {
                    return true;
                }
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

    private interface ProgressReporter {
        void onProgress(float progressFraction);
    }

    private static final class ActiveProgressSnapshot {
        @NonNull
        final String[] trackIds;
        @NonNull
        final float[] fractions;

        private ActiveProgressSnapshot(@NonNull String[] trackIds, @NonNull float[] fractions) {
            this.trackIds = trackIds;
            this.fractions = fractions;
        }
    }

    private static final class TrackDownloadResult {
        @NonNull
        final String trackId;
        @NonNull
        final String trackTitle;
        final boolean downloaded;
        final boolean noNetworkEncountered;

        private TrackDownloadResult(
                @NonNull String trackId,
                @NonNull String trackTitle,
                boolean downloaded,
                boolean noNetworkEncountered
        ) {
            this.trackId = trackId;
            this.trackTitle = trackTitle;
            this.downloaded = downloaded;
            this.noNetworkEncountered = noNetworkEncountered;
        }

        @NonNull
        static TrackDownloadResult downloaded(
                @Nullable String trackId,
                @Nullable String trackTitle,
                boolean noNetworkEncountered
        ) {
            return new TrackDownloadResult(
                    trackId == null ? "" : trackId,
                    trackTitle == null ? "" : trackTitle,
                    true,
                    noNetworkEncountered
            );
        }

        @NonNull
        static TrackDownloadResult failed(@Nullable String trackId, @Nullable String trackTitle) {
            return failed(trackId, trackTitle, false);
        }

        @NonNull
        static TrackDownloadResult failed(
                @Nullable String trackId,
                @Nullable String trackTitle,
                boolean noNetworkEncountered
        ) {
            return new TrackDownloadResult(
                    trackId == null ? "" : trackId,
                    trackTitle == null ? "" : trackTitle,
                    false,
                    noNetworkEncountered
            );
        }
    }

    private static final class NetworkIssueTracker {
        private volatile boolean issue;

        void markIssue() {
            issue = true;
        }

        boolean hasIssue() {
            return issue;
        }
    }

}
