package com.example.sleppify;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class PlaybackHistoryStore {

    private static final String PREFS_NAME = "playback_history_store";
    private static final String KEY_SNAPSHOT_JSON = "snapshot_json";
    private static final ExecutorService IO_EXECUTOR = Executors.newSingleThreadExecutor();
    private static final Object CACHE_LOCK = new Object();
    @NonNull
    private static String cachedRawSnapshot = "";
    @NonNull
    private static Snapshot cachedSnapshot = emptySnapshot();

    private PlaybackHistoryStore() {
    }

    static final class QueueTrack {
        @NonNull
        final String videoId;
        @NonNull
        final String title;
        @NonNull
        final String artist;
        @NonNull
        final String duration;
        @NonNull
        final String imageUrl;

        QueueTrack(
                @NonNull String videoId,
                @NonNull String title,
                @NonNull String artist,
                @NonNull String duration,
                @NonNull String imageUrl
        ) {
            this.videoId = videoId;
            this.title = title;
            this.artist = artist;
            this.duration = duration;
            this.imageUrl = imageUrl;
        }
    }

    static final class Snapshot {
        @NonNull
        final List<QueueTrack> queue;
        final int currentIndex;
        final int currentSeconds;
        final int totalSeconds;
        final boolean isPlaying;
        final long updatedAtMs;

        Snapshot(
                @NonNull List<QueueTrack> queue,
                int currentIndex,
                int currentSeconds,
                int totalSeconds,
                boolean isPlaying,
                long updatedAtMs
        ) {
            this.queue = Collections.unmodifiableList(new ArrayList<>(queue));
            this.currentIndex = currentIndex;
            this.currentSeconds = currentSeconds;
            this.totalSeconds = totalSeconds;
            this.isPlaying = isPlaying;
            this.updatedAtMs = updatedAtMs;
        }

        boolean isValid() {
            return !queue.isEmpty() && currentIndex >= 0 && currentIndex < queue.size();
        }

        @Nullable
        QueueTrack currentTrack() {
            if (!isValid()) {
                return null;
            }
            return queue.get(currentIndex);
        }
    }

    @NonNull
    static Snapshot load(@NonNull Context context) {
        Context appContext = context.getApplicationContext();
        SharedPreferences prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String raw = prefs.getString(KEY_SNAPSHOT_JSON, "");
        if (TextUtils.isEmpty(raw)) {
            Snapshot empty = emptySnapshot();
            synchronized (CACHE_LOCK) {
                cachedRawSnapshot = "";
                cachedSnapshot = empty;
            }
            return empty;
        }

        synchronized (CACHE_LOCK) {
            if (TextUtils.equals(raw, cachedRawSnapshot)) {
                return cachedSnapshot;
            }
        }

        Snapshot parsed = parseSnapshot(raw);
        synchronized (CACHE_LOCK) {
            cachedRawSnapshot = raw;
            cachedSnapshot = parsed;
        }
        return parsed;
    }

    static void save(
            @NonNull Context context,
            @NonNull List<QueueTrack> queue,
            int currentIndex,
            int currentSeconds,
            int totalSeconds,
            boolean isPlaying
    ) {
        if (queue.isEmpty()) {
            return;
        }

        Context appContext = context.getApplicationContext();
        int safeIndex = Math.max(0, Math.min(currentIndex, queue.size() - 1));
        int safeCurrentSeconds = Math.max(0, currentSeconds);
        int safeTotalSeconds = Math.max(1, totalSeconds);
        List<QueueTrack> queueCopy = copyQueue(queue);
        long updatedAtMs = System.currentTimeMillis();

        IO_EXECUTOR.execute(() -> {
            try {
                Snapshot snapshot = new Snapshot(
                        queueCopy,
                        safeIndex,
                        safeCurrentSeconds,
                        safeTotalSeconds,
                        isPlaying,
                        updatedAtMs
                );

                String raw = serializeSnapshot(snapshot);
                if (raw.isEmpty()) {
                    return;
                }

                synchronized (CACHE_LOCK) {
                    if (TextUtils.equals(raw, cachedRawSnapshot)) {
                        cachedSnapshot = snapshot;
                        return;
                    }
                }

                SharedPreferences prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
                prefs.edit().putString(KEY_SNAPSHOT_JSON, raw).apply();

                synchronized (CACHE_LOCK) {
                    cachedRawSnapshot = raw;
                    cachedSnapshot = snapshot;
                }
            } catch (Exception ignored) {
            }
        });
    }

    @NonNull
    private static Snapshot parseSnapshot(@NonNull String raw) {
        try {
            JSONObject root = new JSONObject(raw);
            JSONArray queueArray = root.optJSONArray("queue");
            List<QueueTrack> queue = new ArrayList<>();
            if (queueArray != null) {
                for (int i = 0; i < queueArray.length(); i++) {
                    JSONObject item = queueArray.optJSONObject(i);
                    if (item == null) {
                        continue;
                    }

                    String videoId = safe(item.optString("videoId", ""));
                    if (videoId.isEmpty()) {
                        continue;
                    }

                    queue.add(new QueueTrack(
                            videoId,
                            safe(item.optString("title", "")),
                            safe(item.optString("artist", "")),
                            safe(item.optString("duration", "")),
                            safe(item.optString("imageUrl", ""))
                    ));
                }
            }

            int currentIndex = root.optInt("currentIndex", 0);
            int currentSeconds = Math.max(0, root.optInt("currentSeconds", 0));
            int totalSeconds = Math.max(1, root.optInt("totalSeconds", 1));
            boolean isPlaying = root.optBoolean("isPlaying", false);
            long updatedAtMs = root.optLong("updatedAtMs", 0L);

            if (queue.isEmpty()) {
                return emptySnapshot();
            }

            int safeIndex = Math.max(0, Math.min(currentIndex, queue.size() - 1));
            return new Snapshot(queue, safeIndex, currentSeconds, totalSeconds, isPlaying, updatedAtMs);
        } catch (Exception ignored) {
            return emptySnapshot();
        }
    }

    @NonNull
    private static String serializeSnapshot(@NonNull Snapshot snapshot) {
        try {
            JSONArray queueArray = new JSONArray();
            for (QueueTrack track : snapshot.queue) {
                JSONObject item = new JSONObject();
                item.put("videoId", safe(track.videoId));
                item.put("title", safe(track.title));
                item.put("artist", safe(track.artist));
                item.put("duration", safe(track.duration));
                item.put("imageUrl", safe(track.imageUrl));
                queueArray.put(item);
            }

            JSONObject root = new JSONObject();
            root.put("queue", queueArray);
            root.put("currentIndex", snapshot.currentIndex);
            root.put("currentSeconds", snapshot.currentSeconds);
            root.put("totalSeconds", snapshot.totalSeconds);
            root.put("isPlaying", snapshot.isPlaying);
            root.put("updatedAtMs", snapshot.updatedAtMs);
            return root.toString();
        } catch (Exception ignored) {
            return "";
        }
    }

    @NonNull
    private static List<QueueTrack> copyQueue(@NonNull List<QueueTrack> queue) {
        List<QueueTrack> copy = new ArrayList<>(queue.size());
        for (QueueTrack track : queue) {
            copy.add(new QueueTrack(
                    safe(track.videoId),
                    safe(track.title),
                    safe(track.artist),
                    safe(track.duration),
                    safe(track.imageUrl)
            ));
        }
        return copy;
    }

    @NonNull
    private static Snapshot emptySnapshot() {
        return new Snapshot(new ArrayList<>(), 0, 0, 1, false, 0L);
    }

    @NonNull
    private static String safe(@Nullable String value) {
        return value == null ? "" : value;
    }
}
