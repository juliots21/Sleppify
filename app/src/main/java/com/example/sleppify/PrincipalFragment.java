package com.example.sleppify;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.PathInterpolator;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.CenterCrop;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;
import com.example.sleppify.utils.YouTubeCropTransformation;
import com.google.android.material.imageview.ShapeableImageView;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

import java.util.ArrayList;
import java.util.List;

public class PrincipalFragment extends Fragment implements PlaybackEventBus.Listener {

    private static final String TAG_SONG_PLAYER = "song_player";
    private static final String PREFS_PLAYER_STATE = "player_state";
    private static final String PREF_LAST_YOUTUBE_WEB_COOKIE = "stream_last_youtube_web_cookie";
    private static final String PREFS_STREAMING_CACHE = "streaming_cache";
    private static final String CACHE_KEY_HOME_MIXES = "home_mixes_data";
    private static final long MINI_PROGRESS_TICK_MS = 500L;
    private static final long MINI_SNAPSHOT_REFRESH_MS = 1200L;
    private static final int SHORTCUTS_PER_PAGE = 9;
    private static final int SHORTCUTS_MAX_PAGES = 3;
    private static final PathInterpolator MATERIAL_EASE = new PathInterpolator(0.2f, 0f, 0f, 1f);

    // Mini-player views
    private ImageView ivMiniPlayerArt;
    private TextView tvMiniPlayerTitle;
    private TextView tvMiniPlayerSubtitle;
    private SeekBar sbMiniPlayerProgress;
    private View llMiniPlayer;
    private ImageButton btnMiniPlayPause;

    // Content views
    private ViewPager2 vpShortcuts;
    private TabLayout tabDotsShortcuts;
    private RecyclerView rvMixes;
    private TextView tvMixesEmpty;
    private ShapeableImageView ivShortcutProfilePhoto;
    private TextView tvPersonalMixesLabel;
    private RecyclerView rvPersonalMixes;
    private View llCoversHeader;
    private TextView tvCoversLabel;
    private TextView btnCoversPlayAll;
    private ViewPager2 vpCovers;
    private TabLayout tabDotsCovers;

    // State
    private boolean miniPlaying;
    private PlaybackHistoryStore.Snapshot miniSnapshotCache;
    private long miniSnapshotCacheReadAtMs;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable miniProgressTicker;
    private SongPlayerFragment cachedSongPlayer;
    private long lastCachedSongPlayerTime;
    private YouTubeMusicService youTubeMusicService;
    private String lastMiniArtUrl = "";

    // Throttling fields to prevent redundant network API requests & battery drain
    private static final long NETWORK_REFRESH_THROTTLE_MS = 180000L; // 3 minutes
    private long lastMixesNetworkFetchTimeMs = 0L;
    private long lastCoversNetworkFetchTimeMs = 0L;

    // Database and disk I/O caching to guarantee 60fps scrolling & zero lag
    private long lastShortcutsFetchTimeMs = 0L;
    private final java.util.Map<String, List<String>> playlistGridUrlsCache = new java.util.HashMap<>();

    private static final YouTubeCropTransformation SHARED_YT_CROP = new YouTubeCropTransformation();
    private static final CenterCrop SHARED_CENTER_CROP = new CenterCrop();
    private static final RoundedCorners SHARED_ROUNDED_16 = new RoundedCorners(16);

    // Currently playing shortcut videoId (for EQ icon)
    private String currentlyPlayingShortcutVideoId = "";
    private String lastEqRefreshVideoId = "";

    // Data
    private final List<PlayCountStore.PlayCountEntry> shortcutEntries = new ArrayList<>();
    private final List<YouTubeMusicService.MixResult> mixResults = new ArrayList<>();
    private final List<YouTubeMusicService.MixResult> personalMixResults = new ArrayList<>();
    private final List<YouTubeMusicService.TrackResult> coversResults = new ArrayList<>();
    private static final int COVERS_PER_PAGE = 4;

    // Helper structure to map queue parameters for SongPlayerFragment
    private static class QueueData {
        final ArrayList<String> ids = new ArrayList<>();
        final ArrayList<String> titles = new ArrayList<>();
        final ArrayList<String> artists = new ArrayList<>();
        final ArrayList<String> durations = new ArrayList<>();
        final ArrayList<String> images = new ArrayList<>();
    }

    private QueueData extractQueueData(PlaybackHistoryStore.Snapshot snapshot) {
        QueueData data = new QueueData();
        for (PlaybackHistoryStore.QueueTrack item : snapshot.queue) {
            data.ids.add(item.videoId);
            data.titles.add(item.title);
            data.artists.add(item.artist);
            data.durations.add(item.duration);
            data.images.add(item.imageUrl);
        }
        return data;
    }

    private QueueData extractQueueData(List<YouTubeMusicService.TrackResult> tracks) {
        QueueData data = new QueueData();
        for (YouTubeMusicService.TrackResult t : tracks) {
            if (TextUtils.isEmpty(t.videoId)) continue;
            data.ids.add(t.videoId);
            data.titles.add(t.title);
            data.artists.add(t.subtitle);
            data.durations.add("--:--");
            data.images.add(t.thumbnailUrl);
        }
        return data;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_principal, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        ivMiniPlayerArt = view.findViewById(R.id.ivMiniPlayerArt);
        tvMiniPlayerTitle = view.findViewById(R.id.tvMiniPlayerTitle);
        tvMiniPlayerSubtitle = view.findViewById(R.id.tvMiniPlayerSubtitle);
        sbMiniPlayerProgress = view.findViewById(R.id.sbMiniPlayerProgress);
        llMiniPlayer = view.findViewById(R.id.llMiniPlayer);
        btnMiniPlayPause = view.findViewById(R.id.btnMiniPlayPause);
        vpShortcuts = view.findViewById(R.id.vpShortcuts);
        tabDotsShortcuts = view.findViewById(R.id.tabDotsShortcuts);
        rvMixes = view.findViewById(R.id.rvMixes);
        tvMixesEmpty = view.findViewById(R.id.tvMixesEmpty);
        ivShortcutProfilePhoto = view.findViewById(R.id.ivShortcutProfilePhoto);
        tvPersonalMixesLabel = view.findViewById(R.id.tvPersonalMixesLabel);
        rvPersonalMixes = view.findViewById(R.id.rvPersonalMixes);
        llCoversHeader = view.findViewById(R.id.llCoversHeader);
        tvCoversLabel = view.findViewById(R.id.tvCoversLabel);
        btnCoversPlayAll = view.findViewById(R.id.btnCoversPlayAll);
        vpCovers = view.findViewById(R.id.vpCovers);
        tabDotsCovers = view.findViewById(R.id.tabDotsCovers);

        youTubeMusicService = new YouTubeMusicService();

        llMiniPlayer.setOnClickListener(v -> openPlayerFromMiniBar());
        btnMiniPlayPause.setOnClickListener(v -> toggleMiniPlayback());

        setupShortcuts();
        setupMixes();
        setupPersonalMixes();
        setupCovers();
        loadCachedMixes(); // Moved to the very end of view creation so both generic and personal adapters are fully bound!

        maybeRestoreHiddenPlayerFromSnapshot();
        updateMiniPlayerUi();
        startMiniProgressTicker();
    }

    @Override
    public void onResume() {
        super.onResume();
        PlaybackEventBus.addListener(this);
        if (isHidden()) return;
        refreshShortcuts();
        refreshMixes();
        loadGoogleProfilePhoto();
        refreshCovers();
        updateMiniPlayerUi();
        startMiniProgressTicker();
    }

    @Override
    public void onPause() {
        super.onPause();
        stopMiniProgressTicker();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        PlaybackEventBus.removeListener(this);
        stopMiniProgressTicker();
        playlistGridUrlsCache.clear(); // Clear cache to prevent memory leaks!
        lastMiniArtUrl = "";
        ivMiniPlayerArt = null;
        tvMiniPlayerTitle = null;
        tvMiniPlayerSubtitle = null;
        sbMiniPlayerProgress = null;
        llMiniPlayer = null;
        btnMiniPlayPause = null;
        vpShortcuts = null;
        tabDotsShortcuts = null;
        rvMixes = null;
        tvMixesEmpty = null;
        ivShortcutProfilePhoto = null;
        tvPersonalMixesLabel = null;
        rvPersonalMixes = null;
        llCoversHeader = null;
        tvCoversLabel = null;
        btnCoversPlayAll = null;
        vpCovers = null;
        tabDotsCovers = null;
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (hidden) {
            stopMiniProgressTicker();
        } else {
            // Delay shortcut read by 200ms so any in-flight PlayCountStore async write has committed
            handler.postDelayed(() -> {
                if (isAdded() && !isHidden()) refreshShortcuts();
            }, 200);
            refreshMixes();
            loadGoogleProfilePhoto();
            refreshCovers();
            updateMiniPlayerUi();
            startMiniProgressTicker();
            // Delayed refresh to pick up player state that may settle after fragment transitions
            handler.postDelayed(() -> {
                if (isAdded() && !isHidden()) updateMiniPlayerUi();
            }, 300);
        }
    }

    @Override
    public void onPlaybackSnapshotUpdated() {
        if (isAdded() && getActivity() != null) {
            miniSnapshotCache = null; // Invalidate cache for instant UI refresh!
            getActivity().runOnUiThread(() -> {
                updateMiniPlayerUi();
                // Only refresh EQ icons when the playing videoId actually changes
                String nowId = getCurrentPlayingVideoId();
                if (!TextUtils.equals(nowId, lastEqRefreshVideoId)) {
                    lastEqRefreshVideoId = nowId;
                    refreshShortcutEqIcons();
                }
            });
        }
    }

    // ========== Shortcuts (ViewPager2 with 3x3 grids) ==========

    private void setupShortcuts() {
        vpShortcuts.setAdapter(new ShortcutsPagerAdapter());
        vpShortcuts.setOffscreenPageLimit(SHORTCUTS_MAX_PAGES);
        new TabLayoutMediator(tabDotsShortcuts, vpShortcuts, (tab, position) -> {}).attach();
    }

    private void refreshShortcuts() {
        long now = System.currentTimeMillis();
        // Throttle DB reads if we already have shortcuts loaded (saves disk I/O on navigation)
        if (now - lastShortcutsFetchTimeMs < 15000L && !shortcutEntries.isEmpty()) {
            return;
        }

        int totalNeeded = SHORTCUTS_PER_PAGE * SHORTCUTS_MAX_PAGES;
        lastShortcutsFetchTimeMs = now;

        // Merge top tracks and top playlists, sorted by count descending
        List<PlayCountStore.PlayCountEntry> topTracks = new ArrayList<>(PlayCountStore.getTopEntries(requireContext(), totalNeeded));
        List<PlayCountStore.PlayCountEntry> topPlaylists = new ArrayList<>(PlayCountStore.getTopPlaylists(requireContext(), 5));

        // Filter out local files playlist and limit to max 1 playlist (the most played one)
        java.util.Iterator<PlayCountStore.PlayCountEntry> playlistIter = topPlaylists.iterator();
        while (playlistIter.hasNext()) {
            PlayCountStore.PlayCountEntry p = playlistIter.next();
            if (LocalFilesStore.PLAYLIST_ID.equals(p.playlistId)) {
                playlistIter.remove();
            }
        }
        if (topPlaylists.size() > 1) {
            topPlaylists.subList(1, topPlaylists.size()).clear();
        }

        // Combine: interleave playlists among tracks by count
        java.util.Set<String> seenIds = new java.util.HashSet<>();
        List<PlayCountStore.PlayCountEntry> merged = new ArrayList<>();
        for (PlayCountStore.PlayCountEntry p : topPlaylists) {
            merged.add(p);
            seenIds.add(p.videoId);
        }
        for (PlayCountStore.PlayCountEntry t : topTracks) {
            if (seenIds.contains(t.videoId)) continue;
            seenIds.add(t.videoId);
            merged.add(t);
        }
        // Sort all by count descending, then by recency
        merged.sort((a, b) -> {
            if (b.count != a.count) return Integer.compare(b.count, a.count);
            return Long.compare(b.lastPlayedAtMs, a.lastPlayedAtMs);
        });

        List<PlayCountStore.PlayCountEntry> top = new ArrayList<>(merged.subList(0, Math.min(merged.size(), totalNeeded)));

        if (top.size() < totalNeeded) {
            fillShortcutsFromHistory(top, totalNeeded);
        }

        shortcutEntries.clear();
        shortcutEntries.addAll(top);

        if (vpShortcuts != null && vpShortcuts.getAdapter() != null) {
            vpShortcuts.getAdapter().notifyDataSetChanged();
        }

        if (tabDotsShortcuts != null) {
            int pageCount = Math.max(1, (int) Math.ceil(shortcutEntries.size() / (float) SHORTCUTS_PER_PAGE));
            tabDotsShortcuts.setVisibility(pageCount > 1 ? View.VISIBLE : View.GONE);
        }
    }

    private void fillShortcutsFromHistory(List<PlayCountStore.PlayCountEntry> existing, int totalNeeded) {
        if (!isAdded()) return;
        java.util.Set<String> existingIds = new java.util.HashSet<>();
        for (PlayCountStore.PlayCountEntry e : existing) existingIds.add(e.videoId);

        PlaybackHistoryStore.Snapshot snapshot = PlaybackHistoryStore.load(requireContext());
        for (PlaybackHistoryStore.QueueTrack track : snapshot.queue) {
            if (existing.size() >= totalNeeded) break;
            if (existingIds.contains(track.videoId)) continue;
            existingIds.add(track.videoId);
            existing.add(new PlayCountStore.PlayCountEntry(
                    track.videoId, track.title, track.artist, track.imageUrl,
                    "", "", 0, 0L
            ));
        }

        try {
            List<FavoritesPlaylistStore.FavoriteTrack> favs = FavoritesPlaylistStore.loadFavorites(requireContext());
            for (FavoritesPlaylistStore.FavoriteTrack fav : favs) {
                if (existing.size() >= totalNeeded) break;
                if (existingIds.contains(fav.videoId)) continue;
                existingIds.add(fav.videoId);
                existing.add(new PlayCountStore.PlayCountEntry(
                        fav.videoId, fav.title, fav.artist, fav.imageUrl,
                        FavoritesPlaylistStore.PLAYLIST_ID, "Favoritos", 0, 0L
                ));
            }
        } catch (Exception ignored) {}
    }

    // ========== Mixes ==========

    private void setupMixes() {
        rvMixes.setLayoutManager(new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false));
        rvMixes.setAdapter(new MixesAdapter(mixResults));
    }

    private void refreshMixes() {
        long now = System.currentTimeMillis();
        if (now - lastMixesNetworkFetchTimeMs < NETWORK_REFRESH_THROTTLE_MS && !mixResults.isEmpty()) {
            return; // Throttle redundant network requests
        }

        String cookie = getCookieHeader();
        if (cookie.isEmpty()) {
            // Don't show "Inicia sesión" — silently hide until cookie is available
            if (tvMixesEmpty != null) tvMixesEmpty.setVisibility(View.GONE);
            return;
        }

        youTubeMusicService.fetchHomeBrowse(cookie, new YouTubeMusicService.HomeBrowseCallback() {
            @Override
            public void onSuccess(YouTubeMusicService.HomeBrowseResult result) {
                if (!isAdded()) return;
                lastMixesNetworkFetchTimeMs = System.currentTimeMillis();

                mixResults.clear();
                mixResults.addAll(result.genericMixes);
                cacheMixes(result.genericMixes, result.personalMixes);
                if (rvMixes != null && rvMixes.getAdapter() != null) {
                    rvMixes.getAdapter().notifyDataSetChanged();
                }
                boolean empty = mixResults.isEmpty();
                if (tvMixesEmpty != null) tvMixesEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
                if (rvMixes != null) rvMixes.setVisibility(empty ? View.GONE : View.VISIBLE);

                personalMixResults.clear();
                personalMixResults.addAll(result.personalMixes);
                if (rvPersonalMixes != null && rvPersonalMixes.getAdapter() != null) {
                    rvPersonalMixes.getAdapter().notifyDataSetChanged();
                }
                boolean personalEmpty = personalMixResults.isEmpty();
                if (tvPersonalMixesLabel != null) tvPersonalMixesLabel.setVisibility(personalEmpty ? View.GONE : View.VISIBLE);
                if (rvPersonalMixes != null) rvPersonalMixes.setVisibility(personalEmpty ? View.GONE : View.VISIBLE);
            }

            @Override
            public void onError(String error) {
                if (!isAdded()) return;
                if (mixResults.isEmpty()) {
                    if (tvMixesEmpty != null) tvMixesEmpty.setVisibility(View.VISIBLE);
                    if (rvMixes != null) rvMixes.setVisibility(View.GONE);
                }
            }
        });
    }

    private void loadGoogleProfilePhoto() {
        if (!isAdded() || ivShortcutProfilePhoto == null) return;
        android.net.Uri photoUri = null;
        com.google.firebase.auth.FirebaseUser user = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            photoUri = user.getPhotoUrl();
        }
        if (photoUri == null) {
            String cached = requireContext().getSharedPreferences("streaming_cache", Context.MODE_PRIVATE)
                    .getString("cached_google_profile_photo_url", "");
            if (!TextUtils.isEmpty(cached)) {
                photoUri = android.net.Uri.parse(cached);
            }
        }
        if (photoUri != null) {
            try {
                Glide.with(this)
                        .load(photoUri)
                        .circleCrop()
                        .into(ivShortcutProfilePhoto);
            } catch (Exception ignored) {}
        }
    }

    private void setupPersonalMixes() {
        if (rvPersonalMixes == null) return;
        rvPersonalMixes.setLayoutManager(new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false));
        rvPersonalMixes.setAdapter(new MixesAdapter(personalMixResults));
    }

    private void setupCovers() {
        if (vpCovers == null) return;
        vpCovers.setAdapter(new CoversPagerAdapter());
        vpCovers.setOffscreenPageLimit(3);
        if (tabDotsCovers != null) {
            new TabLayoutMediator(tabDotsCovers, vpCovers, (tab, position) -> {}).attach();
        }
        if (btnCoversPlayAll != null) {
            btnCoversPlayAll.setOnClickListener(v -> {
                if (!coversResults.isEmpty()) playTrackList(coversResults, 0);
            });
        }
    }

    private void refreshCovers() {
        long now = System.currentTimeMillis();
        if (now - lastCoversNetworkFetchTimeMs < NETWORK_REFRESH_THROTTLE_MS && !coversResults.isEmpty()) {
            return; // Throttle redundant network requests
        }

        String cookie = getCookieHeader();
        if (cookie.isEmpty()) return;

        // Prioritize top 10 most played songs for covers/remixes search
        List<String> topTitles = new ArrayList<>();
        List<PlayCountStore.PlayCountEntry> top10 = PlayCountStore.getTopEntries(requireContext(), 10);
        for (PlayCountStore.PlayCountEntry e : top10) {
            if (!TextUtils.isEmpty(e.title)) topTitles.add(e.title);
        }

        // Collect additional titles from cached playlists for variety
        List<String> extraTitles = new ArrayList<>();
        try {
            android.content.SharedPreferences cachePrefs = requireContext()
                    .getSharedPreferences(PREFS_STREAMING_CACHE, Context.MODE_PRIVATE);
            String dataPrefix = "playlist_tracks_data_";
            java.util.Set<String> topSet = new java.util.HashSet<>(topTitles);
            for (String key : cachePrefs.getAll().keySet()) {
                if (!key.startsWith(dataPrefix)) continue;
                String raw = cachePrefs.getString(key, "");
                if (TextUtils.isEmpty(raw)) continue;
                try {
                    org.json.JSONArray arr = new org.json.JSONArray(raw);
                    for (int i = 0; i < arr.length(); i++) {
                        org.json.JSONObject obj = arr.optJSONObject(i);
                        if (obj == null) continue;
                        String title = obj.optString("title", "").trim();
                        if (!title.isEmpty() && !topSet.contains(title)) extraTitles.add(title);
                    }
                } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}

        // Build final selection: ~5-6 from top 10, ~2-3 random from extras
        List<String> selected = new ArrayList<>();
        java.util.Collections.shuffle(topTitles);
        int fromTop = Math.min(6, topTitles.size());
        selected.addAll(topTitles.subList(0, fromTop));

        if (!extraTitles.isEmpty()) {
            java.util.Collections.shuffle(extraTitles);
            int fromExtra = Math.min(8 - selected.size(), extraTitles.size());
            selected.addAll(extraTitles.subList(0, fromExtra));
        }

        // If still not enough, pad with remaining top titles
        if (selected.size() < 8) {
            for (String t : topTitles) {
                if (selected.size() >= 8) break;
                if (!selected.contains(t)) selected.add(t);
            }
        }

        if (selected.isEmpty()) return;

        youTubeMusicService.fetchCoversAndRemixes(cookie, selected, new YouTubeMusicService.CoversRemixesCallback() {
            @Override
            public void onSuccess(List<YouTubeMusicService.TrackResult> tracks) {
                if (!isAdded()) return;
                lastCoversNetworkFetchTimeMs = System.currentTimeMillis();

                // Shuffle results for variety on each refresh
                List<YouTubeMusicService.TrackResult> shuffled = new ArrayList<>(tracks);
                java.util.Collections.shuffle(shuffled);
                coversResults.clear();
                coversResults.addAll(shuffled);
                updateCoversUi();
            }

            @Override
            public void onError(String error) {}
        });
    }

    private void updateCoversUi() {
        boolean empty = coversResults.isEmpty();
        if (llCoversHeader != null) llCoversHeader.setVisibility(empty ? View.GONE : View.VISIBLE);
        if (vpCovers != null) {
            vpCovers.setVisibility(empty ? View.GONE : View.VISIBLE);
            if (vpCovers.getAdapter() != null) vpCovers.getAdapter().notifyDataSetChanged();
        }
        if (tabDotsCovers != null) {
            int pageCount = (int) Math.ceil(coversResults.size() / (float) COVERS_PER_PAGE);
            tabDotsCovers.setVisibility(pageCount > 1 ? View.VISIBLE : View.GONE);
        }
    }

    private void onMixClicked(YouTubeMusicService.MixResult mix) {
        if (!isAdded() || mix.playlistId.isEmpty()) return;
        openPlaylistDetailFromPrincipal(mix.playlistId, mix.title, mix.thumbnailUrl);
    }

    private void playTrackList(List<YouTubeMusicService.TrackResult> tracks, int startIndex) {
        if (!isAdded() || tracks.isEmpty()) return;

        QueueData data = extractQueueData(tracks);
        if (data.ids.isEmpty()) return;

        int safeIndex = Math.max(0, Math.min(startIndex, data.ids.size() - 1));

        animateHideMiniPlayer();

        SongPlayerFragment existingPlayer = findSongPlayerFragment();
        if (existingPlayer != null && existingPlayer.isAdded()) {
            existingPlayer.externalSetReturnTargetTag("module_principal");
            existingPlayer.externalReplaceQueue(data.ids, data.titles, data.artists, data.durations, data.images, safeIndex, true);
            showSongPlayerWithEnterAnimation(existingPlayer);
            return;
        }

        SongPlayerFragment playerFragment = SongPlayerFragment.newInstance(
                data.ids, data.titles, data.artists, data.durations, data.images, safeIndex, true
        );
        playerFragment.externalSetReturnTargetTag("module_principal");
        addSongPlayerWithEnterAnimation(playerFragment);
    }

    private void onShortcutClicked(PlayCountStore.PlayCountEntry entry) {
        if (!isAdded() || TextUtils.isEmpty(entry.videoId)) return;

        // If this is an aggregated playlist entry (videoId == playlistId), open playlist detail directly
        if (!TextUtils.isEmpty(entry.playlistId) && TextUtils.equals(entry.videoId, entry.playlistId)) {
            openPlaylistDetailFromPrincipal(entry.playlistId, entry.playlistName, entry.imageUrl);
            return;
        }

        // Track which shortcut is playing (for EQ icon)
        currentlyPlayingShortcutVideoId = entry.videoId;
        refreshShortcutEqIcons();

        // Create the clicked track as a TrackResult
        YouTubeMusicService.TrackResult clickedTrack = new YouTubeMusicService.TrackResult(
                "video", entry.videoId, entry.title, entry.artist, entry.imageUrl
        );

        // Play the clicked track immediately in mini-player (instant feedback)
        List<YouTubeMusicService.TrackResult> singleList = new ArrayList<>();
        singleList.add(clickedTrack);
        playTrackListInMiniPlayer(singleList, 0);

        // Then fetch radio in background, enrich the queue, and save to library
        String cookie = getCookieHeader();
        if (!cookie.isEmpty()) {
            String radioPlaylistId = "RDAMVM" + entry.videoId;
            final String selectedVideoId = entry.videoId;
            youTubeMusicService.fetchMixTracks(cookie, radioPlaylistId, new YouTubeMusicService.MixTracksCallback() {
                @Override
                public void onSuccess(List<YouTubeMusicService.TrackResult> tracks) {
                    if (!isAdded() || tracks.isEmpty()) return;
                    // Build full radio list: clicked track first, then radio tracks (deduplicated)
                    List<YouTubeMusicService.TrackResult> radioList = new ArrayList<>();
                    radioList.add(clickedTrack);
                    for (YouTubeMusicService.TrackResult t : tracks) {
                        if (!TextUtils.equals(t.videoId, selectedVideoId)) {
                            radioList.add(t);
                        }
                    }
                    // Replace queue keeping current position at 0 (same song continues)
                    SongPlayerFragment sp = findSongPlayerFragment();
                    if (sp != null && sp.isAdded()) {
                        QueueData data = extractQueueData(radioList);
                        if (!data.ids.isEmpty()) {
                            sp.externalReplaceQueue(data.ids, data.titles, data.artists, data.durations, data.images, 0, true);
                        }
                    }
                    // Save radio to history for library display (mirrors Search behavior)
                    List<RadioHistoryStore.RadioTrack> radioStoreTracks = new ArrayList<>();
                    radioStoreTracks.add(new RadioHistoryStore.RadioTrack(
                            selectedVideoId,
                            TextUtils.isEmpty(clickedTrack.title) ? "Tema" : clickedTrack.title,
                            clickedTrack.subtitle == null ? "" : clickedTrack.subtitle,
                            clickedTrack.thumbnailUrl == null ? "" : clickedTrack.thumbnailUrl
                    ));
                    for (YouTubeMusicService.TrackResult t : tracks) {
                        if (TextUtils.isEmpty(t.videoId) || TextUtils.equals(t.videoId, selectedVideoId)) continue;
                        radioStoreTracks.add(new RadioHistoryStore.RadioTrack(
                                t.videoId,
                                TextUtils.isEmpty(t.title) ? "" : t.title,
                                t.subtitle == null ? "" : t.subtitle,
                                t.thumbnailUrl == null ? "" : t.thumbnailUrl
                        ));
                    }
                    RadioHistoryStore.INSTANCE.saveRadio(
                            requireContext(),
                            radioPlaylistId,
                            TextUtils.isEmpty(clickedTrack.title) ? "Tema" : clickedTrack.title,
                            clickedTrack.thumbnailUrl == null ? "" : clickedTrack.thumbnailUrl,
                            radioStoreTracks
                    );
                }

                @Override
                public void onError(String error) {
                    // Radio fetch failed — single track already playing, nothing to do
                }
            });
        }
    }

    private void openPlaylistDetailFromPrincipal(@NonNull String playlistId,
                                                  @Nullable String playlistName,
                                                  @Nullable String thumbnailUrl) {
        if (!isAdded() || getParentFragmentManager().isStateSaved()) return;

        android.content.SharedPreferences prefs = requireContext().getSharedPreferences(PREFS_PLAYER_STATE, android.app.Activity.MODE_PRIVATE);
        String accessToken = prefs.getString("stream_last_youtube_access_token", "");
        if (accessToken == null) accessToken = "";

        PlaylistDetailFragment detailFragment = PlaylistDetailFragment.newInstance(
                playlistId,
                TextUtils.isEmpty(playlistName) ? "Playlist" : playlistName,
                "",
                TextUtils.isEmpty(thumbnailUrl) ? "" : thumbnailUrl,
                accessToken
        );

        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).showModuleLoadingOverlay();
            ((MainActivity) getActivity()).hideTopAppBarForPlaylistDetail();
        }

        androidx.fragment.app.Fragment existingDetail = getParentFragmentManager().findFragmentByTag("playlist_detail");
        androidx.fragment.app.FragmentTransaction transaction = getParentFragmentManager()
                .beginTransaction()
                .setReorderingAllowed(true);
        if (existingDetail != null && existingDetail.isAdded()) {
            transaction.remove(existingDetail);
        }
        transaction
                .add(R.id.fragmentContainer, detailFragment, "playlist_detail")
                .addToBackStack("playlist_detail")
                .commit();
    }

    // ========== Play in Mini-Player (no full player) ==========

    private void playTrackListInMiniPlayer(List<YouTubeMusicService.TrackResult> tracks, int startIndex) {
        if (!isAdded() || tracks.isEmpty()) return;

        QueueData data = extractQueueData(tracks);
        if (data.ids.isEmpty()) return;

        int safeIndex = Math.max(0, Math.min(startIndex, data.ids.size() - 1));

        // Always bypass cache for fresh lookup
        cachedSongPlayer = null;
        lastCachedSongPlayerTime = 0;

        // Direct lookup — no caching to avoid stale references
        Fragment rawPlayer = getParentFragmentManager().findFragmentByTag(TAG_SONG_PLAYER);
        SongPlayerFragment existingPlayer = rawPlayer instanceof SongPlayerFragment ? (SongPlayerFragment) rawPlayer : null;

        if (existingPlayer != null && existingPlayer.isAdded()) {
            existingPlayer.externalSetReturnTargetTag("module_principal");
            existingPlayer.externalReplaceQueue(data.ids, data.titles, data.artists, data.durations, data.images, safeIndex, true);
        } else {
            // Create new hidden player — use commitAllowingStateLoss to avoid silent abort
            SongPlayerFragment playerFragment = SongPlayerFragment.newInstance(
                    data.ids, data.titles, data.artists, data.durations, data.images, safeIndex, true
            );
            playerFragment.externalSetReturnTargetTag("module_principal");

            getParentFragmentManager()
                    .beginTransaction()
                    .setReorderingAllowed(true)
                    .add(R.id.playerContainer, playerFragment, TAG_SONG_PLAYER)
                    .hide(playerFragment)
                    .runOnCommit(() -> {
                        cachedSongPlayer = null;
                        lastCachedSongPlayerTime = 0;
                        updateMiniPlayerUi();
                        startMiniProgressTicker();
                    })
                    .commitAllowingStateLoss();
        }

        // Show mini-player immediately with track info (instant feedback)
        miniPlaying = true;
        if (llMiniPlayer != null) {
            tvMiniPlayerTitle.setText(data.titles.get(safeIndex));
            tvMiniPlayerSubtitle.setText(data.artists.get(safeIndex));
            btnMiniPlayPause.setImageResource(R.drawable.ic_mini_pause);
            if (!TextUtils.isEmpty(data.images.get(safeIndex)) && isAdded()) {
                try {
                    Glide.with(this)
                            .load(data.images.get(safeIndex))
                            .placeholder(R.color.surface_high)
                            .transform(SHARED_CENTER_CROP)
                            .into(ivMiniPlayerArt);
                } catch (Exception ignored) {}
            }
            animateShowMiniPlayer();
        }
        startMiniProgressTicker();
    }

    private void refreshShortcutEqIcons() {
        if (vpShortcuts == null) return;
        // ViewPager2 has an internal RecyclerView at child(0)
        View internalRv = vpShortcuts.getChildAt(0);
        if (!(internalRv instanceof RecyclerView)) return;
        RecyclerView pagerRv = (RecyclerView) internalRv;
        // Iterate laid-out page ViewHolders and notify their inner grid adapters
        for (int i = 0; i < pagerRv.getChildCount(); i++) {
            View pageView = pagerRv.getChildAt(i);
            if (pageView instanceof RecyclerView) {
                RecyclerView innerGrid = (RecyclerView) pageView;
                if (innerGrid.getAdapter() != null) {
                    innerGrid.getAdapter().notifyDataSetChanged();
                }
            }
        }
    }

    private String getCurrentPlayingVideoId() {
        SongPlayerFragment sp = findSongPlayerFragment();
        if (sp != null && sp.isAdded()) {
            return sp.externalGetCurrentVideoId();
        }
        return "";
    }

    // ========== Mini-Player ==========

    private void startMiniProgressTicker() {
        stopMiniProgressTicker();
        miniProgressTicker = new Runnable() {
            @Override
            public void run() {
                if (!isAdded() || isHidden()) return;
                updateMiniPlayerUi();
                handler.postDelayed(this, MINI_PROGRESS_TICK_MS);
            }
        };
        handler.postDelayed(miniProgressTicker, MINI_PROGRESS_TICK_MS);
    }

    private void stopMiniProgressTicker() {
        if (miniProgressTicker != null) {
            handler.removeCallbacks(miniProgressTicker);
            miniProgressTicker = null;
        }
    }

    private void updateMiniPlayerUi() {
        if (!isAdded() || getView() == null || llMiniPlayer == null
                || tvMiniPlayerTitle == null || tvMiniPlayerSubtitle == null
                || ivMiniPlayerArt == null || btnMiniPlayPause == null) {
            return;
        }

        if (getActivity() instanceof MainActivity) {
            if (((MainActivity) getActivity()).isSongPlayerVisible()) {
                llMiniPlayer.setVisibility(View.GONE);
                return;
            }
        }

        SongPlayerFragment songPlayer = findSongPlayerFragment();
        boolean playerAttached = songPlayer != null && songPlayer.isAdded();

        int currentSeconds = 0;
        int totalSeconds = 1;
        String title = null;
        String subtitle = null;
        String imageUrl = null;

        // HIGH LEVEL OPTIMIZATION: Bypasses loading snapshot from disk when actively playing.
        // Cuts unnecessary disk I/O reads to absolute zero!
        if (playerAttached) {
            miniPlaying = songPlayer.externalIsPlaying();
            currentSeconds = songPlayer.externalGetCurrentSeconds();
            totalSeconds = Math.max(1, songPlayer.externalGetTotalSeconds());

            String playerVideoId = songPlayer.externalGetCurrentVideoId();
            if (!TextUtils.isEmpty(playerVideoId)) {
                SharedPreferences prefs = getPlayerStatePrefs();
                title = prefs.getString("stream_last_track_title", "");
                subtitle = prefs.getString("stream_last_track_artist", "");
                imageUrl = prefs.getString("stream_last_track_image", "");
            }
            // Fallback: get directly from player queue (prefs may not be written yet)
            if (TextUtils.isEmpty(title)) {
                title = songPlayer.externalGetCurrentTitle();
            }
            if (TextUtils.isEmpty(subtitle)) {
                subtitle = songPlayer.externalGetCurrentArtist();
            }
            if (TextUtils.isEmpty(imageUrl)) {
                imageUrl = songPlayer.externalGetCurrentImageUrl();
            }
            if (TextUtils.isEmpty(title)) {
                int idx = songPlayer.externalGetCurrentIndex();
                title = "Track " + (idx + 1);
            }
        } else {
            PlaybackHistoryStore.Snapshot snapshot = loadPlaybackSnapshot();
            PlaybackHistoryStore.QueueTrack snapshotTrack = snapshot.currentTrack();
            if (snapshotTrack != null) {
                miniPlaying = snapshot.isPlaying;
                currentSeconds = 0;
                totalSeconds = 1;
                title = TextUtils.isEmpty(snapshotTrack.title) ? "Última reproducción" : snapshotTrack.title;
                subtitle = snapshotTrack.artist;
                imageUrl = snapshotTrack.imageUrl;
            }
        }

        if (sbMiniPlayerProgress != null) {
            int clampedCurrent = Math.max(0, Math.min(totalSeconds, currentSeconds));
            int progress = Math.round((clampedCurrent / (float) Math.max(1, totalSeconds)) * 1000f);
            sbMiniPlayerProgress.setProgress(Math.max(0, Math.min(1000, progress)));
        }

        if (TextUtils.isEmpty(title) && !playerAttached) {
            llMiniPlayer.setVisibility(View.GONE);
            miniPlaying = false;
            return;
        }

        animateShowMiniPlayer();

        tvMiniPlayerTitle.setText(TextUtils.isEmpty(title) ? "" : title);
        tvMiniPlayerSubtitle.setText(TextUtils.isEmpty(subtitle) ? "" : subtitle);
        btnMiniPlayPause.setImageResource(miniPlaying ? R.drawable.ic_mini_pause : R.drawable.ic_mini_play);

        if (!TextUtils.isEmpty(imageUrl) && isAdded() && !imageUrl.equals(lastMiniArtUrl)) {
            lastMiniArtUrl = imageUrl;
            try {
                Glide.with(this)
                        .load(imageUrl)
                        .placeholder(R.color.surface_high)
                        .transform(SHARED_CENTER_CROP)
                        .into(ivMiniPlayerArt);
            } catch (Exception ignored) {}
        }
    }

    private void openPlayerFromMiniBar() {
        animateHideMiniPlayer();

        SongPlayerFragment existingPlayer = findSongPlayerFragment();
        if (existingPlayer != null && existingPlayer.isAdded()) {
            existingPlayer.externalSetReturnTargetTag("module_principal");
            showSongPlayerWithEnterAnimation(existingPlayer);
            return;
        }

        miniSnapshotCache = null; // Invalidate cache!
        PlaybackHistoryStore.Snapshot snapshot = loadPlaybackSnapshot();
        if (snapshot.isValid()) {
            openPlayerFromSnapshot(snapshot, snapshot.isPlaying);
            return;
        }
    }

    private void toggleMiniPlayback() {
        SongPlayerFragment songPlayer = findSongPlayerFragment();
        if (songPlayer != null && songPlayer.isAdded()) {
            songPlayer.externalTogglePlayback();
            miniPlaying = songPlayer.externalIsPlayingIntent();
            updateMiniPlayerUi();
            return;
        }

        miniSnapshotCache = null; // Invalidate cache!
        PlaybackHistoryStore.Snapshot snapshot = loadPlaybackSnapshot();
        if (startHiddenPlayerFromSnapshot(snapshot, true)) return;
    }

    private boolean openPlayerFromSnapshot(@NonNull PlaybackHistoryStore.Snapshot snapshot, boolean startPlaying) {
        if (!snapshot.isValid()) return false;
        if (llMiniPlayer != null) llMiniPlayer.setVisibility(View.GONE);

        QueueData data = extractQueueData(snapshot);
        if (data.ids.isEmpty()) return false;
        int snapshotIndex = Math.max(0, Math.min(snapshot.currentIndex, data.ids.size() - 1));

        SongPlayerFragment existingPlayer = findSongPlayerFragment();
        if (existingPlayer != null && existingPlayer.isAdded()) {
            existingPlayer.externalSetReturnTargetTag("module_principal");
            existingPlayer.externalReplaceQueue(data.ids, data.titles, data.artists, data.durations, data.images, snapshotIndex, startPlaying);
            showSongPlayerWithEnterAnimation(existingPlayer);
        } else {
            SongPlayerFragment playerFragment = SongPlayerFragment.newInstance(
                    data.ids, data.titles, data.artists, data.durations, data.images, snapshotIndex, startPlaying
            );
            playerFragment.externalSetReturnTargetTag("module_principal");
            addSongPlayerWithEnterAnimation(playerFragment);
        }
        return true;
    }

    private boolean startHiddenPlayerFromSnapshot(@NonNull PlaybackHistoryStore.Snapshot snapshot, boolean startPlaying) {
        if (!snapshot.isValid() || !isAdded()) return false;

        QueueData data = extractQueueData(snapshot);
        if (data.ids.isEmpty()) return false;

        int snapshotIndex = Math.max(0, Math.min(snapshot.currentIndex, data.ids.size() - 1));

        SongPlayerFragment existingPlayer = findSongPlayerFragment();
        if (existingPlayer != null && existingPlayer.isAdded()) {
            existingPlayer.externalReplaceQueue(data.ids, data.titles, data.artists, data.durations, data.images, snapshotIndex, startPlaying);
            updateMiniPlayerUi();
            return true;
        }

        if (getParentFragmentManager().isStateSaved()) return false;

        SongPlayerFragment playerFragment = SongPlayerFragment.newInstance(
                data.ids, data.titles, data.artists, data.durations, data.images, snapshotIndex, startPlaying
        );
        playerFragment.externalSetReturnTargetTag("module_principal");

        getParentFragmentManager()
                .beginTransaction()
                .setReorderingAllowed(true)
                .add(R.id.playerContainer, playerFragment, TAG_SONG_PLAYER)
                .hide(playerFragment)
                .runOnCommit(this::updateMiniPlayerUi)
                .commit();

        miniPlaying = startPlaying;
        updateMiniPlayerUi();
        return true;
    }

    private void maybeRestoreHiddenPlayerFromSnapshot() {
        if (!isAdded() || isHidden()) return;
        SongPlayerFragment existingPlayer = findSongPlayerFragment();
        if (existingPlayer != null && existingPlayer.isAdded()) return;
        PlaybackHistoryStore.Snapshot snapshot = loadPlaybackSnapshot();
        if (!snapshot.isValid()) return;
        startHiddenPlayerFromSnapshot(snapshot, false);
    }

    // ========== Helpers ==========

    private void animateHideMiniPlayer() {
        if (llMiniPlayer != null) {
            llMiniPlayer.animate().cancel();
            float distance = llMiniPlayer.getHeight() > 0 ? llMiniPlayer.getHeight() : 300f;
            llMiniPlayer.animate().translationY(distance).setDuration(250)
                    .setInterpolator(MATERIAL_EASE)
                    .withEndAction(() -> llMiniPlayer.setVisibility(View.GONE)).start();
        }
    }

    private void animateShowMiniPlayer() {
        if (llMiniPlayer != null && llMiniPlayer.getVisibility() != View.VISIBLE) {
            float distance = llMiniPlayer.getHeight() > 0 ? llMiniPlayer.getHeight() : 300f;
            llMiniPlayer.setTranslationY(distance);
            llMiniPlayer.setVisibility(View.VISIBLE);
            llMiniPlayer.animate().cancel();
            llMiniPlayer.animate()
                    .translationY(0f)
                    .setDuration(250)
                    .setInterpolator(MATERIAL_EASE)
                    .withEndAction(null)
                    .start();
        }
    }

    @NonNull
    private PlaybackHistoryStore.Snapshot loadPlaybackSnapshot() {
        if (!isAdded()) {
            return new PlaybackHistoryStore.Snapshot(new ArrayList<>(), 0, 1, false, 0L);
        }
        long now = System.currentTimeMillis();
        if (miniSnapshotCache != null && (now - miniSnapshotCacheReadAtMs) < MINI_SNAPSHOT_REFRESH_MS) {
            return miniSnapshotCache;
        }
        PlaybackHistoryStore.Snapshot snapshot = PlaybackHistoryStore.load(requireContext());
        miniSnapshotCache = snapshot;
        miniSnapshotCacheReadAtMs = now;
        return snapshot;
    }

    @Nullable
    private SongPlayerFragment findSongPlayerFragment() {
        long now = System.currentTimeMillis();
        if (lastCachedSongPlayerTime > 0 && now - lastCachedSongPlayerTime < 100L) {
            return cachedSongPlayer;
        }
        Fragment fragment = getParentFragmentManager().findFragmentByTag(TAG_SONG_PLAYER);
        cachedSongPlayer = fragment instanceof SongPlayerFragment ? (SongPlayerFragment) fragment : null;
        lastCachedSongPlayerTime = now;
        return cachedSongPlayer;
    }

    @NonNull
    private List<String> resolvePlaylistGridUrls(@NonNull String playlistId) {
        if (TextUtils.isEmpty(playlistId) || !isAdded()) return java.util.Collections.emptyList();

        List<String> cached = playlistGridUrlsCache.get(playlistId);
        if (cached != null) {
            return cached;
        }

        SharedPreferences cachePrefs = requireContext().getSharedPreferences(PREFS_STREAMING_CACHE, Context.MODE_PRIVATE);

        // 1. Persisted grid URLs (generated by MusicPlayerFragment)
        String gridRaw = cachePrefs.getString("playlist_grid_urls_" + playlistId, "");
        if (!TextUtils.isEmpty(gridRaw)) {
            String[] parts = gridRaw.split("\\n");
            List<String> result = new ArrayList<>(parts.length);
            for (String part : parts) {
                String trimmed = part.trim();
                if (!trimmed.isEmpty()) result.add(trimmed);
            }
            if (result.size() >= 4) {
                playlistGridUrlsCache.put(playlistId, result);
                return result;
            }
        }

        // 2. Cached playlist tracks data
        String tracksRaw = cachePrefs.getString("playlist_tracks_data_" + playlistId, "");
        if (!TextUtils.isEmpty(tracksRaw)) {
            try {
                org.json.JSONArray arr = new org.json.JSONArray(tracksRaw);
                List<String> urls = new ArrayList<>(4);
                java.util.Set<String> seen = new java.util.HashSet<>();
                for (int i = 0; i < arr.length() && urls.size() < 4; i++) {
                    org.json.JSONObject obj = arr.optJSONObject(i);
                    if (obj == null) continue;
                    String imgUrl = obj.optString("imageUrl", "").trim();
                    if (!imgUrl.isEmpty() && seen.add(imgUrl)) urls.add(imgUrl);
                }
                if (urls.size() >= 4) {
                    playlistGridUrlsCache.put(playlistId, urls);
                    return urls;
                }
            } catch (Exception ignored) {}
        }

        // 3. PlayCountStore entries
        List<String> dbUrls = PlayCountStore.getPlaylistTrackImages(requireContext(), playlistId, 4);
        if (dbUrls != null) {
            playlistGridUrlsCache.put(playlistId, dbUrls);
            return dbUrls;
        }
        return java.util.Collections.emptyList();
    }

    @NonNull
    private SharedPreferences getPlayerStatePrefs() {
        return requireContext().getSharedPreferences(PREFS_PLAYER_STATE, Activity.MODE_PRIVATE);
    }

    private String getCookieHeader() {
        if (!isAdded()) return "";
        SharedPreferences prefs = requireContext().getSharedPreferences(PREFS_PLAYER_STATE, Activity.MODE_PRIVATE);
        String cookie = prefs.getString(PREF_LAST_YOUTUBE_WEB_COOKIE, "");
        return cookie != null ? cookie.trim() : "";
    }

    private void showSongPlayerWithEnterAnimation(@NonNull SongPlayerFragment player) {
        getParentFragmentManager()
                .beginTransaction()
                .setReorderingAllowed(true)
                .show(player)
                .runOnCommit(player::externalAnimateEnterSlide)
                .commit();
    }

    private void addSongPlayerWithEnterAnimation(@NonNull SongPlayerFragment player) {
        getParentFragmentManager()
                .beginTransaction()
                .setReorderingAllowed(true)
                .add(R.id.playerContainer, player, TAG_SONG_PLAYER)
                .runOnCommit(player::externalAnimateEnterSlide)
                .commit();
    }

    // ========== Cache Mixes ==========

    private void cacheMixes(List<YouTubeMusicService.MixResult> generic, List<YouTubeMusicService.MixResult> personal) {
        if (!isAdded()) return;
        try {
            org.json.JSONArray arr = new org.json.JSONArray();
            for (YouTubeMusicService.MixResult mix : generic) {
                org.json.JSONObject obj = new org.json.JSONObject();
                obj.put("playlistId", mix.playlistId);
                obj.put("title", mix.title);
                obj.put("subtitle", mix.subtitle);
                obj.put("thumbnailUrl", mix.thumbnailUrl);
                arr.put(obj);
            }
            org.json.JSONArray arrPers = new org.json.JSONArray();
            for (YouTubeMusicService.MixResult mix : personal) {
                org.json.JSONObject obj = new org.json.JSONObject();
                obj.put("playlistId", mix.playlistId);
                obj.put("title", mix.title);
                obj.put("subtitle", mix.subtitle);
                obj.put("thumbnailUrl", mix.thumbnailUrl);
                arrPers.put(obj);
            }
            requireContext().getSharedPreferences(PREFS_STREAMING_CACHE, Context.MODE_PRIVATE)
                    .edit()
                    .putString(CACHE_KEY_HOME_MIXES, arr.toString())
                    .putString("home_personal_mixes_data", arrPers.toString())
                    .apply();
        } catch (Exception ignored) {}
    }

    private void loadCachedMixes() {
        if (!isAdded()) return;
        try {
            SharedPreferences cache = requireContext().getSharedPreferences(PREFS_STREAMING_CACHE, Context.MODE_PRIVATE);
            String raw = cache.getString(CACHE_KEY_HOME_MIXES, "");
            if (raw != null && !raw.isEmpty()) {
                org.json.JSONArray arr = new org.json.JSONArray(raw);
                mixResults.clear();
                for (int i = 0; i < arr.length(); i++) {
                    org.json.JSONObject obj = arr.optJSONObject(i);
                    if (obj == null) continue;
                    mixResults.add(new YouTubeMusicService.MixResult(
                            obj.optString("playlistId", ""),
                            obj.optString("title", ""),
                            obj.optString("subtitle", ""),
                            obj.optString("thumbnailUrl", "")
                    ));
                }
                if (rvMixes != null && rvMixes.getAdapter() != null) {
                    rvMixes.getAdapter().notifyDataSetChanged();
                }
                boolean empty = mixResults.isEmpty();
                if (tvMixesEmpty != null) tvMixesEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
                if (rvMixes != null) rvMixes.setVisibility(empty ? View.GONE : View.VISIBLE);
            }

            String rawPers = cache.getString("home_personal_mixes_data", "");
            if (rawPers != null && !rawPers.isEmpty()) {
                org.json.JSONArray arr = new org.json.JSONArray(rawPers);
                personalMixResults.clear();
                for (int i = 0; i < arr.length(); i++) {
                    org.json.JSONObject obj = arr.optJSONObject(i);
                    if (obj == null) continue;
                    personalMixResults.add(new YouTubeMusicService.MixResult(
                            obj.optString("playlistId", ""),
                            obj.optString("title", ""),
                            obj.optString("subtitle", ""),
                            obj.optString("thumbnailUrl", "")
                    ));
                }
                if (rvPersonalMixes != null && rvPersonalMixes.getAdapter() != null) {
                    rvPersonalMixes.getAdapter().notifyDataSetChanged();
                }
                boolean personalEmpty = personalMixResults.isEmpty();
                if (tvPersonalMixesLabel != null) tvPersonalMixesLabel.setVisibility(personalEmpty ? View.GONE : View.VISIBLE);
                if (rvPersonalMixes != null) rvPersonalMixes.setVisibility(personalEmpty ? View.GONE : View.VISIBLE);
            }
        } catch (Exception ignored) {}
    }

    // ========== Adapters ==========

    private class ShortcutsPagerAdapter extends RecyclerView.Adapter<ShortcutsPagerAdapter.PageVH> {
        @NonNull
        @Override
        public PageVH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            RecyclerView rv = new RecyclerView(parent.getContext());
            rv.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            rv.setOverScrollMode(View.OVER_SCROLL_NEVER);
            rv.setNestedScrollingEnabled(false);
            return new PageVH(rv);
        }

        @Override
        public void onBindViewHolder(@NonNull PageVH holder, int position) {
            int start = position * SHORTCUTS_PER_PAGE;
            int end = Math.min(start + SHORTCUTS_PER_PAGE, shortcutEntries.size());
            List<PlayCountStore.PlayCountEntry> pageItems = start < shortcutEntries.size()
                    ? shortcutEntries.subList(start, end) : new ArrayList<>();
            holder.bind(pageItems);
        }

        @Override
        public int getItemCount() {
            if (shortcutEntries.isEmpty()) return 1;
            return (int) Math.ceil(shortcutEntries.size() / (float) SHORTCUTS_PER_PAGE);
        }

        class PageVH extends RecyclerView.ViewHolder {
            final RecyclerView rv;

            PageVH(@NonNull View itemView) {
                super(itemView);
                rv = (RecyclerView) itemView;
            }

            void bind(List<PlayCountStore.PlayCountEntry> items) {
                rv.setLayoutManager(new GridLayoutManager(rv.getContext(), 3));
                rv.setAdapter(new ShortcutCellAdapter(items));
            }
        }
    }

    private class ShortcutCellAdapter extends RecyclerView.Adapter<ShortcutCellAdapter.CellVH> {
        private final List<PlayCountStore.PlayCountEntry> items;

        ShortcutCellAdapter(List<PlayCountStore.PlayCountEntry> items) {
            this.items = items;
        }

        @NonNull
        @Override
        public CellVH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_shortcut_cell, parent, false);
            return new CellVH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull CellVH holder, int position) {
            PlayCountStore.PlayCountEntry entry = items.get(position);
            holder.tvTitle.setText(entry.title);

            boolean isPlaylist = !TextUtils.isEmpty(entry.playlistId)
                    && TextUtils.equals(entry.videoId, entry.playlistId);

            // Skip image reload if the same URL is already displayed (prevents flicker on EQ refresh)
            String currentTag = holder.ivThumb.getTag(R.id.tag_artwork_signature) instanceof String
                    ? (String) holder.ivThumb.getTag(R.id.tag_artwork_signature) : "";

            if (isPlaylist && isAdded()) {
                // Show 2x2 grid art for playlists
                List<String> gridUrls = resolvePlaylistGridUrls(entry.playlistId);
                if (gridUrls.size() >= 4) {
                    String signature = entry.playlistId + "_" + gridUrls.get(0);
                    if (!signature.equals(currentTag)) {
                        holder.ivThumb.setTag(R.id.tag_artwork_signature, signature);
                        float density = holder.itemView.getContext().getResources().getDisplayMetrics().density;
                        int sizePx = Math.round(120 * density);
                        PlaylistGridArtLoader.load(holder.ivThumb, gridUrls, sizePx);
                    }
                } else {
                    // Fewer than 4 grid images: use the first grid URL or first available image
                    String fallbackUrl = !gridUrls.isEmpty() ? gridUrls.get(0) : entry.imageUrl;
                    if (!TextUtils.isEmpty(fallbackUrl) && !fallbackUrl.equals(currentTag)) {
                        holder.ivThumb.setTag(R.id.tag_artwork_signature, fallbackUrl);
                        try {
                            Glide.with(PrincipalFragment.this)
                                    .load(fallbackUrl)
                                    .placeholder(R.color.surface_high)
                                    .transform(SHARED_YT_CROP, SHARED_CENTER_CROP)
                                    .into(holder.ivThumb);
                        } catch (Exception ignored) {}
                    }
                }
            } else if (!TextUtils.isEmpty(entry.imageUrl) && isAdded()) {
                if (!entry.imageUrl.equals(currentTag)) {
                    holder.ivThumb.setTag(R.id.tag_artwork_signature, entry.imageUrl);
                    try {
                        Glide.with(PrincipalFragment.this)
                                .load(entry.imageUrl)
                                .placeholder(R.color.surface_high)
                                .transform(SHARED_YT_CROP, SHARED_CENTER_CROP)
                                .into(holder.ivThumb);
                    } catch (Exception ignored) {}
                }
            }

            // EQ animated icon: show if this shortcut's song is currently playing
            String nowPlaying = getCurrentPlayingVideoId();
            boolean isThisPlaying = !isPlaylist
                    && !TextUtils.isEmpty(nowPlaying)
                    && (TextUtils.equals(entry.videoId, nowPlaying)
                        || TextUtils.equals(entry.videoId, currentlyPlayingShortcutVideoId));

            SongPlayerFragment sp = findSongPlayerFragment();
            boolean actuallyPlaying = sp != null && sp.isAdded() && sp.externalIsPlaying();

            if (isThisPlaying && actuallyPlaying) {
                holder.ivPlay.setVisibility(View.GONE);
                holder.eqView.setVisibility(View.VISIBLE);
                holder.eqView.setAnimating(true);
            } else {
                holder.eqView.setAnimating(false);
                holder.eqView.setVisibility(View.GONE);
                holder.ivPlay.setVisibility(View.VISIBLE);
            }

            holder.itemView.setOnClickListener(v -> onShortcutClicked(entry));
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        class CellVH extends RecyclerView.ViewHolder {
            final ImageView ivThumb;
            final TextView tvTitle;
            final ImageView ivPlay;
            final AnimatedEqualizerView eqView;

            CellVH(@NonNull View itemView) {
                super(itemView);
                ivThumb = itemView.findViewById(R.id.ivShortcutThumb);
                tvTitle = itemView.findViewById(R.id.tvShortcutTitle);
                ivPlay = itemView.findViewById(R.id.ivShortcutPlay);
                eqView = itemView.findViewById(R.id.eqShortcut);
            }
        }
    }

    private class MixesAdapter extends RecyclerView.Adapter<MixesAdapter.MixVH> {
        private final List<YouTubeMusicService.MixResult> items;

        MixesAdapter(List<YouTubeMusicService.MixResult> items) {
            this.items = items;
        }

        @NonNull
        @Override
        public MixVH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_mix_card, parent, false);
            return new MixVH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull MixVH holder, int position) {
            YouTubeMusicService.MixResult mix = items.get(position);
            holder.tvTitle.setText(mix.title);
            holder.tvSubtitle.setText(mix.subtitle);
            if (!TextUtils.isEmpty(mix.thumbnailUrl) && isAdded()) {
                try {
                    Glide.with(PrincipalFragment.this)
                            .load(mix.thumbnailUrl)
                            .placeholder(R.color.surface_high)
                            .transform(SHARED_CENTER_CROP, SHARED_ROUNDED_16)
                            .into(holder.ivThumb);
                } catch (Exception ignored) {}
            }
            holder.itemView.setOnClickListener(v -> onMixClicked(mix));
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        class MixVH extends RecyclerView.ViewHolder {
            final ImageView ivThumb;
            final TextView tvTitle;
            final TextView tvSubtitle;

            MixVH(@NonNull View itemView) {
                super(itemView);
                ivThumb = itemView.findViewById(R.id.ivMixThumb);
                tvTitle = itemView.findViewById(R.id.tvMixTitle);
                tvSubtitle = itemView.findViewById(R.id.tvMixSubtitle);
            }
        }
    }

    private class CoversPagerAdapter extends RecyclerView.Adapter<CoversPagerAdapter.PageVH> {
        @NonNull
        @Override
        public PageVH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LinearLayout page = new LinearLayout(parent.getContext());
            page.setOrientation(LinearLayout.VERTICAL);
            page.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            return new PageVH(page);
        }

        @Override
        public void onBindViewHolder(@NonNull PageVH holder, int position) {
            LinearLayout page = (LinearLayout) holder.itemView;
            page.removeAllViews();
            int start = position * COVERS_PER_PAGE;
            int end = Math.min(start + COVERS_PER_PAGE, coversResults.size());
            for (int i = start; i < end; i++) {
                YouTubeMusicService.TrackResult track = coversResults.get(i);
                View row = LayoutInflater.from(page.getContext())
                        .inflate(R.layout.item_cover_track_row, page, false);
                ImageView ivArt = row.findViewById(R.id.ivCoverArt);
                TextView tvTitle = row.findViewById(R.id.tvCoverTitle);
                TextView tvSubtitle = row.findViewById(R.id.tvCoverSubtitle);

                tvTitle.setText(track.title);
                tvSubtitle.setText(track.subtitle);
                if (!TextUtils.isEmpty(track.thumbnailUrl) && isAdded()) {
                    try {
                        Glide.with(PrincipalFragment.this)
                                .load(track.thumbnailUrl)
                                .placeholder(R.color.surface_high)
                                .transform(SHARED_YT_CROP, SHARED_CENTER_CROP)
                                .into(ivArt);
                    } catch (Exception ignored) {}
                }
                row.setOnClickListener(v -> onCoversTrackClicked(track));
                page.addView(row);
            }
        }

        @Override
        public int getItemCount() {
            return coversResults.isEmpty() ? 0 : (int) Math.ceil(coversResults.size() / (float) COVERS_PER_PAGE);
        }

        class PageVH extends RecyclerView.ViewHolder {
            PageVH(@NonNull View itemView) {
                super(itemView);
            }
        }
    }

    private void onCoversTrackClicked(YouTubeMusicService.TrackResult track) {
        if (!isAdded() || TextUtils.isEmpty(track.videoId)) return;
        // Play starting from this track through all covers
        int startIndex = 0;
        for (int i = 0; i < coversResults.size(); i++) {
            if (TextUtils.equals(coversResults.get(i).videoId, track.videoId)) {
                startIndex = i;
                break;
            }
        }
        playTrackList(coversResults, startIndex);
    }
}
