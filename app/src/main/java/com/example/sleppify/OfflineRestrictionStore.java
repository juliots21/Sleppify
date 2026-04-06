package com.example.sleppify;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.text.TextUtils;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class OfflineRestrictionStore {

    private static final String PREFS_NAME = "offline_restrictions";
    private static final String KEY_RESTRICTED_IDS = "restricted_video_ids";

    private OfflineRestrictionStore() {
    }

    public static boolean isRestricted(@NonNull Context context, @NonNull String videoId) {
        String safeVideoId = sanitize(videoId);
        if (safeVideoId.isEmpty()) {
            return false;
        }
        return getRestrictedIds(context).contains(safeVideoId);
    }

    public static boolean isRestricted(@NonNull Set<String> restrictedIds, @NonNull String videoId) {
        if (restrictedIds.isEmpty()) {
            return false;
        }
        String safeVideoId = sanitize(videoId);
        if (safeVideoId.isEmpty()) {
            return false;
        }
        return restrictedIds.contains(safeVideoId);
    }

    @NonNull
    public static Set<String> getRestrictedIds(@NonNull Context context) {
        SharedPreferences prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        Set<String> stored = prefs.getStringSet(KEY_RESTRICTED_IDS, new HashSet<>());
        if (stored == null || stored.isEmpty()) {
            return new HashSet<>();
        }

        HashSet<String> normalized = new HashSet<>();
        boolean changed = false;
        for (String raw : stored) {
            String safe = sanitize(raw == null ? "" : raw);
            if (safe.isEmpty()) {
                changed = true;
                continue;
            }
            if (!TextUtils.equals(raw == null ? "" : raw.trim(), safe)) {
                changed = true;
            }
            normalized.add(safe);
        }

        if (changed) {
            prefs.edit().putStringSet(KEY_RESTRICTED_IDS, normalized).apply();
        }

        return normalized;
    }

    @NonNull
    public static Set<String> getRestrictedVideoIds(@NonNull Context context) {
        return getRestrictedIds(context);
    }

    public static void markRestricted(@NonNull Context context, @NonNull String videoId) {
        String safeVideoId = sanitize(videoId);
        if (safeVideoId.isEmpty()) {
            return;
        }

        Set<String> ids = getRestrictedIds(context);
        if (!ids.add(safeVideoId)) {
            return;
        }

        context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putStringSet(KEY_RESTRICTED_IDS, ids)
                .apply();
    }

    public static void addRestrictedVideoId(@NonNull Context context, @NonNull String videoId) {
        markRestricted(context, videoId);
    }

    public static int countRestricted(@NonNull Context context, @NonNull List<String> videoIds) {
        if (videoIds.isEmpty()) {
            return 0;
        }

        Set<String> restricted = getRestrictedIds(context);
        if (restricted.isEmpty()) {
            return 0;
        }

        int count = 0;
        HashSet<String> seen = new HashSet<>();
        for (String raw : videoIds) {
            String id = sanitize(raw);
            if (id.isEmpty() || !seen.add(id)) {
                continue;
            }
            if (restricted.contains(id)) {
                count++;
            }
        }
        return count;
    }

    @NonNull
    public static List<String> filterPlayableForOffline(
            @NonNull Context context,
            @NonNull List<String> videoIds
    ) {
        if (videoIds.isEmpty()) {
            return new ArrayList<>();
        }

        Set<String> restricted = getRestrictedIds(context);
        ArrayList<String> playable = new ArrayList<>(videoIds.size());
        HashSet<String> seen = new HashSet<>();
        for (String raw : videoIds) {
            String id = sanitize(raw);
            if (id.isEmpty() || !seen.add(id)) {
                continue;
            }
            if (!restricted.contains(id)) {
                playable.add(id);
            }
        }
        return playable;
    }

    @NonNull
    private static String sanitize(@NonNull String videoId) {
        String value = videoId == null ? "" : videoId.trim();
        if (TextUtils.isEmpty(value)) {
            return "";
        }

        String extracted = extractVideoId(value);
        if (!TextUtils.isEmpty(extracted)) {
            return extracted;
        }

        return value;
    }

    @NonNull
    private static String extractVideoId(@NonNull String raw) {
        String value = raw.trim();
        if (value.isEmpty()) {
            return "";
        }

        try {
            Uri uri = Uri.parse(value);
            String queryV = uri.getQueryParameter("v");
            if (!TextUtils.isEmpty(queryV)) {
                return queryV.trim();
            }

            List<String> segments = uri.getPathSegments();
            String host = uri.getHost();
            host = host == null ? "" : host.toLowerCase();

            if (host.contains("youtu.be") && !segments.isEmpty()) {
                return segments.get(0).trim();
            }

            if (host.contains("youtube.com") || host.contains("music.youtube.com")) {
                if (segments.size() >= 2 && "embed".equals(segments.get(0))) {
                    return segments.get(1).trim();
                }
                if (segments.size() >= 2 && "shorts".equals(segments.get(0))) {
                    return segments.get(1).trim();
                }
            }
        } catch (Exception ignored) {
        }

        int watchIndex = value.indexOf("watch?v=");
        if (watchIndex >= 0) {
            String tail = value.substring(watchIndex + 8);
            int amp = tail.indexOf('&');
            if (amp >= 0) {
                tail = tail.substring(0, amp);
            }
            return tail.trim();
        }

        int youtuIndex = value.indexOf("youtu.be/");
        if (youtuIndex >= 0) {
            String tail = value.substring(youtuIndex + 9);
            int slash = tail.indexOf('/');
            if (slash >= 0) {
                tail = tail.substring(0, slash);
            }
            int q = tail.indexOf('?');
            if (q >= 0) {
                tail = tail.substring(0, q);
            }
            return tail.trim();
        }

        return "";
    }
}
