package com.example.sleppify;

import android.content.Context;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class OfflineAudioStore {

    private static final String OFFLINE_AUDIO_DIR = "offline_audio";
    private static final String OFFLINE_AUDIO_EXTENSION = ".m4a";
    private static final String LEGACY_OFFLINE_AUDIO_EXTENSION = ".mp3";
    private static final String LEGACY_BIN_OFFLINE_AUDIO_EXTENSION = ".bin";
    private static final int OFFLINE_STATE_CACHE_MAX_ENTRIES = 4096;
    private static final Object OFFLINE_STATE_CACHE_LOCK = new Object();
    private static final LinkedHashMap<String, Boolean> OFFLINE_STATE_CACHE =
            new LinkedHashMap<String, Boolean>(128, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
                    return size() > OFFLINE_STATE_CACHE_MAX_ENTRIES;
                }
            };

    private OfflineAudioStore() {
    }

    @NonNull
    public static File getOfflineAudioDir(@NonNull Context context) {
        return new File(context.getFilesDir(), OFFLINE_AUDIO_DIR);
    }

    @NonNull
    public static File getOfflineAudioFile(@NonNull Context context, @NonNull String trackId) {
        String normalized = normalizeTrackId(trackId);
        return new File(getOfflineAudioDir(context), normalized + OFFLINE_AUDIO_EXTENSION);
    }

    @NonNull
    public static File getExistingOfflineAudioFile(@NonNull Context context, @NonNull String trackId) {
        String normalized = normalizeTrackId(trackId);
        File preferred = new File(getOfflineAudioDir(context), normalized + OFFLINE_AUDIO_EXTENSION);
        if (preferred.isFile() && preferred.length() > 0L) {
            return preferred;
        }

        File legacy = new File(getOfflineAudioDir(context), normalized + LEGACY_OFFLINE_AUDIO_EXTENSION);
        if (legacy.isFile() && legacy.length() > 0L) {
            return legacy;
        }

        File legacyBin = new File(getOfflineAudioDir(context), normalized + LEGACY_BIN_OFFLINE_AUDIO_EXTENSION);
        if (legacyBin.isFile() && legacyBin.length() > 0L) {
            return legacyBin;
        }

        return preferred;
    }

    public static boolean hasOfflineAudio(@NonNull Context context, @NonNull String trackId) {
        String normalized = normalizeTrackId(trackId);
        Boolean cached = getCachedOfflineState(normalized);
        if (cached != null) {
            return cached;
        }

        boolean available = hasOfflineAudioOnDisk(context, normalized);
        putCachedOfflineState(normalized, available);
        return available;
    }

    public static int countOfflineAvailable(@NonNull Context context, @NonNull List<String> trackIds) {
        int count = 0;
        for (String id : trackIds) {
            if (!TextUtils.isEmpty(id) && hasOfflineAudio(context, id)) {
                count++;
            }
        }
        return count;
    }

    public static boolean deleteOfflineAudio(@NonNull Context context, @NonNull String trackId) {
        String normalized = normalizeTrackId(trackId);
        File dir = getOfflineAudioDir(context);
        File preferred = new File(dir, normalized + OFFLINE_AUDIO_EXTENSION);
        File legacy = new File(dir, normalized + LEGACY_OFFLINE_AUDIO_EXTENSION);
        File legacyBin = new File(dir, normalized + LEGACY_BIN_OFFLINE_AUDIO_EXTENSION);

        boolean removedAny = false;
        if (preferred.exists()) {
            removedAny = preferred.delete() || removedAny;
        }
        if (legacy.exists()) {
            removedAny = legacy.delete() || removedAny;
        }
        if (legacyBin.exists()) {
            removedAny = legacyBin.delete() || removedAny;
        }
        putCachedOfflineState(normalized, false);
        return removedAny;
    }

    public static int deleteOfflineAudio(@NonNull Context context, @NonNull List<String> trackIds) {
        int removed = 0;
        for (String id : trackIds) {
            if (TextUtils.isEmpty(id)) {
                continue;
            }
            if (deleteOfflineAudio(context, id)) {
                removed++;
            }
        }
        return removed;
    }

    @NonNull
    public static String normalizeTrackId(@NonNull String trackId) {
        String raw = trackId.trim();
        if (raw.isEmpty()) {
            return "track_0";
        }

        StringBuilder builder = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (Character.isLetterOrDigit(c) || c == '_' || c == '-' || c == '.') {
                builder.append(c);
            } else {
                builder.append('_');
            }
        }

        String normalized = builder.toString();
        if (normalized.isEmpty()) {
            normalized = "track_" + Integer.toHexString(raw.hashCode());
        }
        return normalized;
    }

    public static void markOfflineAudioState(@NonNull String trackId, boolean available) {
        putCachedOfflineState(normalizeTrackId(trackId), available);
    }

    public static void clearOfflineAudioStateCache() {
        synchronized (OFFLINE_STATE_CACHE_LOCK) {
            OFFLINE_STATE_CACHE.clear();
        }
    }

    private static boolean hasOfflineAudioOnDisk(@NonNull Context context, @NonNull String normalizedTrackId) {
        File dir = getOfflineAudioDir(context);

        File preferred = new File(dir, normalizedTrackId + OFFLINE_AUDIO_EXTENSION);
        if (preferred.isFile() && preferred.length() > 0L) {
            return true;
        }

        File legacy = new File(dir, normalizedTrackId + LEGACY_OFFLINE_AUDIO_EXTENSION);
        if (legacy.isFile() && legacy.length() > 0L) {
            return true;
        }

        File legacyBin = new File(dir, normalizedTrackId + LEGACY_BIN_OFFLINE_AUDIO_EXTENSION);
        return legacyBin.isFile() && legacyBin.length() > 0L;
    }

    @Nullable
    private static Boolean getCachedOfflineState(@NonNull String normalizedTrackId) {
        synchronized (OFFLINE_STATE_CACHE_LOCK) {
            return OFFLINE_STATE_CACHE.get(normalizedTrackId);
        }
    }

    private static void putCachedOfflineState(@NonNull String normalizedTrackId, boolean available) {
        synchronized (OFFLINE_STATE_CACHE_LOCK) {
            OFFLINE_STATE_CACHE.put(normalizedTrackId, available);
        }
    }
}
