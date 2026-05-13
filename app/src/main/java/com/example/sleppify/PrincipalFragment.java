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

    private static final String TAG_PRINCIPAL = "PrincipalFragment";
    private static final String TAG_SONG_PLAYER = "song_player";
    private static final String PREFS_PLAYER_STATE = "player_state";
    private static final String PREF_LAST_YOUTUBE_WEB_COOKIE = "stream_last_youtube_web_cookie";
    private static final String PREFS_STREAMING_CACHE = "streaming_cache";
    private static final String CACHE_KEY_HOME_MIXES = "home_mixes_data";
    private static final long MINI_PROGRESS_TICK_MS = 200L;
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
    private TextView tvShortcutChannelName;
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

    private static final YouTubeCropTransformation SHARED_YT_CROP = new YouTubeCropTransformation();

    // Data
    private final List<PlayCountStore.PlayCountEntry> shortcutEntries = new ArrayList<>();
    private final List<YouTubeMusicService.MixResult> mixResults = new ArrayList<>();
    private final List<YouTubeMusicService.MixResult> personalMixResults = new ArrayList<>();
    private final List<YouTubeMusicService.TrackResult> coversResults = new ArrayList<>();
    private static final int COVERS_PER_PAGE = 4;

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
        tvShortcutChannelName = view.findViewById(R.id.tvShortcutChannelName);
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

        maybeRestoreHiddenPlayerFromSnapshot();
        updateMiniPlayerUi();
        startMiniProgressTicker();
    }

    @Override
    public void onResume() {
        super.onResume();
        PlaybackEventBus.addListener(this);
        refreshShortcuts();
        refreshMixes();
        refreshChannelName();
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
        tvShortcutChannelName = null;
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
            refreshChannelName();
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
            getActivity().runOnUiThread(this::updateMiniPlayerUi);
        }
    }

    // ========== Shortcuts (ViewPager2 with 3x3 grids) ==========

    private void setupShortcuts() {
        vpShortcuts.setAdapter(new ShortcutsPagerAdapter());
        vpShortcuts.setOffscreenPageLimit(SHORTCUTS_MAX_PAGES);
        new TabLayoutMediator(tabDotsShortcuts, vpShortcuts, (tab, position) -> {}).attach();
    }

    private void refreshShortcuts() {
        int totalNeeded = SHORTCUTS_PER_PAGE * SHORTCUTS_MAX_PAGES;

        // Merge top tracks and top playlists, sorted by count descending
        List<PlayCountStore.PlayCountEntry> topTracks = new ArrayList<>(PlayCountStore.getTopEntries(requireContext(), totalNeeded));
        List<PlayCountStore.PlayCountEntry> topPlaylists = new ArrayList<>(PlayCountStore.getTopPlaylists(requireContext(), 5));

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
        rvMixes.setAdapter(new MixesAdapter());
        loadCachedMixes();
    }

    private void refreshMixes() {
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
                mixResults.clear();
                mixResults.addAll(result.genericMixes);
                cacheMixes(result.genericMixes);
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

    private void refreshChannelName() {
        String cookie = getCookieHeader();
        if (cookie.isEmpty()) return;
        youTubeMusicService.fetchYouTubeChannelName(cookie, new YouTubeMusicService.ChannelNameCallback() {
            @Override
            public void onSuccess(String channelName, String channelPhotoUrl) {
                if (!isAdded()) return;
                if (tvShortcutChannelName != null && !TextUtils.isEmpty(channelName)) {
                    tvShortcutChannelName.setText(channelName);
                    tvShortcutChannelName.setVisibility(View.VISIBLE);
                }
                if (ivShortcutProfilePhoto != null && !TextUtils.isEmpty(channelPhotoUrl)) {
                    try {
                        Glide.with(PrincipalFragment.this)
                                .load(channelPhotoUrl)
                                .circleCrop()
                                .into(ivShortcutProfilePhoto);
                    } catch (Exception ignored) {}
                }
            }

            @Override
            public void onError(String error) {}
        });
    }

    private void setupPersonalMixes() {
        if (rvPersonalMixes == null) return;
        rvPersonalMixes.setLayoutManager(new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false));
        rvPersonalMixes.setAdapter(new PersonalMixesAdapter());
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
        String cookie = getCookieHeader();
        if (cookie.isEmpty()) return;

        // Collect track titles from ALL cached YouTube playlists
        List<String> allTitles = new ArrayList<>();
        try {
            android.content.SharedPreferences cachePrefs = requireContext()
                    .getSharedPreferences(PREFS_STREAMING_CACHE, Context.MODE_PRIVATE);
            String dataPrefix = "playlist_tracks_data_";
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
                        if (!title.isEmpty()) allTitles.add(title);
                    }
                } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}

        if (allTitles.isEmpty()) {
            // Fallback to top play count entries
            List<PlayCountStore.PlayCountEntry> top = PlayCountStore.getTopEntries(requireContext(), 10);
            for (PlayCountStore.PlayCountEntry e : top) {
                if (!TextUtils.isEmpty(e.title)) allTitles.add(e.title);
            }
        }
        if (allTitles.isEmpty()) return;

        // Shuffle and pick up to 8 random titles for variety
        java.util.Collections.shuffle(allTitles);
        List<String> selected = allTitles.subList(0, Math.min(8, allTitles.size()));

        youTubeMusicService.fetchCoversAndRemixes(cookie, selected, new YouTubeMusicService.CoversRemixesCallback() {
            @Override
            public void onSuccess(List<YouTubeMusicService.TrackResult> tracks) {
                if (!isAdded()) return;
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
        String cookie = getCookieHeader();
        if (cookie.isEmpty() || mix.playlistId.isEmpty()) return;

        youTubeMusicService.fetchMixTracks(cookie, mix.playlistId, new YouTubeMusicService.MixTracksCallback() {
            @Override
            public void onSuccess(List<YouTubeMusicService.TrackResult> tracks) {
                if (!isAdded() || tracks.isEmpty()) return;
                playTrackList(tracks, 0);
            }

            @Override
            public void onError(String error) {}
        });
    }

    private void playTrackList(List<YouTubeMusicService.TrackResult> tracks, int startIndex) {
        if (!isAdded() || tracks.isEmpty()) return;

        ArrayList<String> ids = new ArrayList<>();
        ArrayList<String> titles = new ArrayList<>();
        ArrayList<String> artists = new ArrayList<>();
        ArrayList<String> durations = new ArrayList<>();
        ArrayList<String> images = new ArrayList<>();

        for (YouTubeMusicService.TrackResult t : tracks) {
            if (TextUtils.isEmpty(t.videoId)) continue;
            ids.add(t.videoId);
            titles.add(t.title);
            artists.add(t.subtitle);
            durations.add("--:--");
            images.add(t.thumbnailUrl);
        }
        if (ids.isEmpty()) return;

        int safeIndex = Math.max(0, Math.min(startIndex, ids.size() - 1));

        if (llMiniPlayer != null) {
            llMiniPlayer.animate().cancel();
            float distance = llMiniPlayer.getHeight() > 0 ? llMiniPlayer.getHeight() : 300f;
            llMiniPlayer.animate().translationY(distance).setDuration(250)
                    .setInterpolator(MATERIAL_EASE)
                    .withEndAction(() -> llMiniPlayer.setVisibility(View.GONE)).start();
        }

        SongPlayerFragment existingPlayer = findSongPlayerFragment();
        if (existingPlayer != null && existingPlayer.isAdded()) {
            existingPlayer.externalSetReturnTargetTag("module_principal");
            existingPlayer.externalReplaceQueue(ids, titles, artists, durations, images, safeIndex, true);
            showSongPlayerWithEnterAnimation(existingPlayer);
            return;
        }

        SongPlayerFragment playerFragment = SongPlayerFragment.newInstance(
                ids, titles, artists, durations, images, safeIndex, true
        );
        playerFragment.externalSetReturnTargetTag("module_principal");
        addSongPlayerWithEnterAnimation(playerFragment);
    }

    private void onShortcutClicked(PlayCountStore.PlayCountEntry entry) {
        if (!isAdded() || TextUtils.isEmpty(entry.videoId)) return;

        // If this is an aggregated playlist entry (videoId == playlistId), open playlist detail
        if (!TextUtils.isEmpty(entry.playlistId) && TextUtils.equals(entry.videoId, entry.playlistId)) {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).openPlaylistFromPrincipal(
                        entry.playlistId, entry.playlistName, entry.imageUrl);
            }
            return;
        }

        YouTubeMusicService.TrackResult track = new YouTubeMusicService.TrackResult(
                "video", entry.videoId, entry.title, entry.artist, entry.imageUrl
        );
        List<YouTubeMusicService.TrackResult> singleList = new ArrayList<>();
        singleList.add(track);
        playTrackList(singleList, 0);
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
        PlaybackHistoryStore.Snapshot snapshot = loadPlaybackSnapshot();
        PlaybackHistoryStore.QueueTrack snapshotTrack = snapshot.currentTrack();

        int currentSeconds = 0;
        int totalSeconds = 1;

        if (playerAttached) {
            miniPlaying = songPlayer.externalIsPlaying();
            currentSeconds = songPlayer.externalGetCurrentSeconds();
            totalSeconds = Math.max(1, songPlayer.externalGetTotalSeconds());
        } else if (snapshotTrack != null) {
            miniPlaying = snapshot.isPlaying;
            currentSeconds = 0;
            totalSeconds = 1;
        }

        if (sbMiniPlayerProgress != null) {
            int clampedCurrent = Math.max(0, Math.min(totalSeconds, currentSeconds));
            int progress = Math.round((clampedCurrent / (float) Math.max(1, totalSeconds)) * 1000f);
            sbMiniPlayerProgress.setProgress(Math.max(0, Math.min(1000, progress)));
        }

        String title = null;
        String subtitle = null;
        String imageUrl = null;

        if (playerAttached) {
            String playerVideoId = songPlayer.externalGetCurrentVideoId();
            if (!TextUtils.isEmpty(playerVideoId)) {
                SharedPreferences prefs = getPlayerStatePrefs();
                title = prefs.getString("stream_last_track_title", "");
                subtitle = prefs.getString("stream_last_track_artist", "");
                imageUrl = prefs.getString("stream_last_track_image", "");
            }
            if (TextUtils.isEmpty(title)) {
                int idx = songPlayer.externalGetCurrentIndex();
                title = "Track " + (idx + 1);
            }
        }

        if (TextUtils.isEmpty(title) && snapshotTrack != null) {
            title = TextUtils.isEmpty(snapshotTrack.title) ? "Última reproducción" : snapshotTrack.title;
            subtitle = snapshotTrack.artist;
            imageUrl = snapshotTrack.imageUrl;
        }

        if (TextUtils.isEmpty(title) && !playerAttached) {
            llMiniPlayer.setVisibility(View.GONE);
            miniPlaying = false;
            return;
        }

        if (llMiniPlayer.getVisibility() != View.VISIBLE) {
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

        tvMiniPlayerTitle.setText(TextUtils.isEmpty(title) ? "" : title);
        tvMiniPlayerSubtitle.setText(TextUtils.isEmpty(subtitle) ? "" : subtitle);
        btnMiniPlayPause.setImageResource(miniPlaying ? R.drawable.ic_mini_pause : R.drawable.ic_mini_play);

        if (!TextUtils.isEmpty(imageUrl) && isAdded()) {
            try {
                Glide.with(this)
                        .load(imageUrl)
                        .placeholder(R.color.surface_high)
                        .transform(new CenterCrop())
                        .into(ivMiniPlayerArt);
            } catch (Exception ignored) {}
        }
    }

    private void openPlayerFromMiniBar() {
        if (llMiniPlayer != null) {
            llMiniPlayer.animate().cancel();
            float distance = llMiniPlayer.getHeight() > 0 ? llMiniPlayer.getHeight() : 300f;
            llMiniPlayer.animate().translationY(distance).setDuration(250)
                    .setInterpolator(MATERIAL_EASE)
                    .withEndAction(() -> llMiniPlayer.setVisibility(View.GONE)).start();
        }

        SongPlayerFragment existingPlayer = findSongPlayerFragment();
        if (existingPlayer != null && existingPlayer.isAdded()) {
            existingPlayer.externalSetReturnTargetTag("module_principal");
            showSongPlayerWithEnterAnimation(existingPlayer);
            return;
        }

        PlaybackHistoryStore.Snapshot snapshot = loadPlaybackSnapshot();
        if (snapshot.isValid()) {
            openPlayerFromSnapshot(snapshot, snapshot.isPlaying);
            return;
        }

        boolean persistedPlaying = getPlayerStatePrefs().getBoolean("stream_last_is_playing", false);
        launchPlayerFromLastTrackPrefs(persistedPlaying, true);
    }

    private void toggleMiniPlayback() {
        SongPlayerFragment songPlayer = findSongPlayerFragment();
        if (songPlayer != null && songPlayer.isAdded()) {
            songPlayer.externalTogglePlayback();
            miniPlaying = songPlayer.externalIsPlaying();
            updateMiniPlayerUi();
            return;
        }

        PlaybackHistoryStore.Snapshot snapshot = loadPlaybackSnapshot();
        if (startHiddenPlayerFromSnapshot(snapshot, true)) return;
    }

    private boolean openPlayerFromSnapshot(@NonNull PlaybackHistoryStore.Snapshot snapshot, boolean startPlaying) {
        if (!snapshot.isValid()) return false;
        if (llMiniPlayer != null) llMiniPlayer.setVisibility(View.GONE);

        ArrayList<String> ids = new ArrayList<>();
        ArrayList<String> titles = new ArrayList<>();
        ArrayList<String> artists = new ArrayList<>();
        ArrayList<String> durations = new ArrayList<>();
        ArrayList<String> images = new ArrayList<>();
        for (PlaybackHistoryStore.QueueTrack item : snapshot.queue) {
            ids.add(item.videoId);
            titles.add(item.title);
            artists.add(item.artist);
            durations.add(item.duration);
            images.add(item.imageUrl);
        }
        if (ids.isEmpty()) return false;
        int snapshotIndex = Math.max(0, Math.min(snapshot.currentIndex, ids.size() - 1));

        SongPlayerFragment existingPlayer = findSongPlayerFragment();
        if (existingPlayer != null && existingPlayer.isAdded()) {
            existingPlayer.externalSetReturnTargetTag("module_principal");
            existingPlayer.externalReplaceQueue(ids, titles, artists, durations, images, snapshotIndex, startPlaying);
            showSongPlayerWithEnterAnimation(existingPlayer);
        } else {
            SongPlayerFragment playerFragment = SongPlayerFragment.newInstance(
                    ids, titles, artists, durations, images, snapshotIndex, startPlaying
            );
            playerFragment.externalSetReturnTargetTag("module_principal");
            addSongPlayerWithEnterAnimation(playerFragment);
        }
        return true;
    }

    private boolean startHiddenPlayerFromSnapshot(@NonNull PlaybackHistoryStore.Snapshot snapshot, boolean startPlaying) {
        if (!snapshot.isValid() || !isAdded()) return false;

        SongPlayerFragment existingPlayer = findSongPlayerFragment();
        if (existingPlayer != null && existingPlayer.isAdded()) {
            int snapshotIndex = Math.max(0, Math.min(snapshot.currentIndex, snapshot.queue.size() - 1));
            ArrayList<String> ids = new ArrayList<>();
            ArrayList<String> titles = new ArrayList<>();
            ArrayList<String> artists = new ArrayList<>();
            ArrayList<String> durations = new ArrayList<>();
            ArrayList<String> images = new ArrayList<>();
            for (PlaybackHistoryStore.QueueTrack item : snapshot.queue) {
                ids.add(item.videoId);
                titles.add(item.title);
                artists.add(item.artist);
                durations.add(item.duration);
                images.add(item.imageUrl);
            }
            existingPlayer.externalReplaceQueue(ids, titles, artists, durations, images, snapshotIndex, startPlaying);
            updateMiniPlayerUi();
            return true;
        }

        ArrayList<String> ids = new ArrayList<>();
        ArrayList<String> titles = new ArrayList<>();
        ArrayList<String> artists = new ArrayList<>();
        ArrayList<String> durations = new ArrayList<>();
        ArrayList<String> images = new ArrayList<>();
        for (PlaybackHistoryStore.QueueTrack item : snapshot.queue) {
            ids.add(item.videoId);
            titles.add(item.title);
            artists.add(item.artist);
            durations.add(item.duration);
            images.add(item.imageUrl);
        }
        if (ids.isEmpty()) return false;

        int snapshotIndex = Math.max(0, Math.min(snapshot.currentIndex, ids.size() - 1));

        if (getParentFragmentManager().isStateSaved()) return false;

        SongPlayerFragment playerFragment = SongPlayerFragment.newInstance(
                ids, titles, artists, durations, images, snapshotIndex, startPlaying
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

    private boolean launchPlayerFromLastTrackPrefs(boolean startPlaying, boolean showPlayer) {
        if (!isAdded()) return false;
        SharedPreferences prefs = getPlayerStatePrefs();
        String videoId = prefs.getString("stream_last_track_video_id", "");
        String title = prefs.getString("stream_last_track_title", "");
        String artist = prefs.getString("stream_last_track_artist", "");
        String image = prefs.getString("stream_last_track_image", "");
        String duration = prefs.getString("stream_last_track_duration", "");

        if (TextUtils.isEmpty(videoId)) return false;

        ArrayList<String> ids = new ArrayList<>();
        ArrayList<String> titles = new ArrayList<>();
        ArrayList<String> artists = new ArrayList<>();
        ArrayList<String> durations = new ArrayList<>();
        ArrayList<String> images = new ArrayList<>();
        ids.add(videoId);
        titles.add(title != null ? title : "");
        artists.add(artist != null ? artist : "");
        durations.add(duration != null ? duration : "");
        images.add(image != null ? image : "");

        SongPlayerFragment existingPlayer = findSongPlayerFragment();
        if (existingPlayer != null && existingPlayer.isAdded()) {
            existingPlayer.externalSetReturnTargetTag("module_principal");
            existingPlayer.externalReplaceQueue(ids, titles, artists, durations, images, 0, startPlaying);
            if (showPlayer) showSongPlayerWithEnterAnimation(existingPlayer);
            updateMiniPlayerUi();
            return true;
        }

        SongPlayerFragment playerFragment = SongPlayerFragment.newInstance(
                ids, titles, artists, durations, images, 0, startPlaying
        );
        playerFragment.externalSetReturnTargetTag("module_principal");
        if (showPlayer) {
            addSongPlayerWithEnterAnimation(playerFragment);
        } else {
            getParentFragmentManager()
                    .beginTransaction()
                    .setReorderingAllowed(true)
                    .add(R.id.playerContainer, playerFragment, TAG_SONG_PLAYER)
                    .hide(playerFragment)
                    .commit();
        }
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
        if (lastCachedSongPlayerTime > 0 && now - lastCachedSongPlayerTime < 500L) {
            return cachedSongPlayer;
        }
        Fragment fragment = getParentFragmentManager().findFragmentByTag(TAG_SONG_PLAYER);
        cachedSongPlayer = fragment instanceof SongPlayerFragment ? (SongPlayerFragment) fragment : null;
        lastCachedSongPlayerTime = now;
        return cachedSongPlayer;
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

    private void cacheMixes(List<YouTubeMusicService.MixResult> mixes) {
        if (!isAdded()) return;
        try {
            org.json.JSONArray arr = new org.json.JSONArray();
            for (YouTubeMusicService.MixResult mix : mixes) {
                org.json.JSONObject obj = new org.json.JSONObject();
                obj.put("playlistId", mix.playlistId);
                obj.put("title", mix.title);
                obj.put("subtitle", mix.subtitle);
                obj.put("thumbnailUrl", mix.thumbnailUrl);
                arr.put(obj);
            }
            requireContext().getSharedPreferences(PREFS_STREAMING_CACHE, Context.MODE_PRIVATE)
                    .edit()
                    .putString(CACHE_KEY_HOME_MIXES, arr.toString())
                    .apply();
        } catch (Exception ignored) {}
    }

    private void loadCachedMixes() {
        if (!isAdded()) return;
        try {
            String raw = requireContext().getSharedPreferences(PREFS_STREAMING_CACHE, Context.MODE_PRIVATE)
                    .getString(CACHE_KEY_HOME_MIXES, "");
            if (raw == null || raw.isEmpty()) return;
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
            if (!TextUtils.isEmpty(entry.imageUrl) && isAdded()) {
                try {
                    Glide.with(PrincipalFragment.this)
                            .load(entry.imageUrl)
                            .placeholder(R.color.surface_high)
                            .transform(SHARED_YT_CROP, new CenterCrop())
                            .into(holder.ivThumb);
                } catch (Exception ignored) {}
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

            CellVH(@NonNull View itemView) {
                super(itemView);
                ivThumb = itemView.findViewById(R.id.ivShortcutThumb);
                tvTitle = itemView.findViewById(R.id.tvShortcutTitle);
            }
        }
    }

    private class MixesAdapter extends RecyclerView.Adapter<MixesAdapter.MixVH> {
        @NonNull
        @Override
        public MixVH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_mix_card, parent, false);
            return new MixVH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull MixVH holder, int position) {
            YouTubeMusicService.MixResult mix = mixResults.get(position);
            holder.tvTitle.setText(mix.title);
            holder.tvSubtitle.setText(mix.subtitle);
            if (!TextUtils.isEmpty(mix.thumbnailUrl) && isAdded()) {
                try {
                    Glide.with(PrincipalFragment.this)
                            .load(mix.thumbnailUrl)
                            .placeholder(R.color.surface_high)
                            .transform(new CenterCrop(), new RoundedCorners(16))
                            .into(holder.ivThumb);
                } catch (Exception ignored) {}
            }
            holder.itemView.setOnClickListener(v -> onMixClicked(mix));
        }

        @Override
        public int getItemCount() {
            return mixResults.size();
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

    private class PersonalMixesAdapter extends RecyclerView.Adapter<PersonalMixesAdapter.MixVH> {
        @NonNull
        @Override
        public MixVH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_mix_card, parent, false);
            return new MixVH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull MixVH holder, int position) {
            YouTubeMusicService.MixResult mix = personalMixResults.get(position);
            holder.tvTitle.setText(mix.title);
            holder.tvSubtitle.setText(mix.subtitle);
            if (!TextUtils.isEmpty(mix.thumbnailUrl) && isAdded()) {
                try {
                    Glide.with(PrincipalFragment.this)
                            .load(mix.thumbnailUrl)
                            .placeholder(R.color.surface_high)
                            .transform(new CenterCrop(), new RoundedCorners(16))
                            .into(holder.ivThumb);
                } catch (Exception ignored) {}
            }
            holder.itemView.setOnClickListener(v -> onMixClicked(mix));
        }

        @Override
        public int getItemCount() {
            return personalMixResults.size();
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
                                .transform(SHARED_YT_CROP, new CenterCrop())
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
