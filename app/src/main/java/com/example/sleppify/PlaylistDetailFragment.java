package com.example.sleppify;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.text.Html;
import android.graphics.Typeface;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.Build;
import com.example.sleppify.utils.YouTubeCropTransformation;
import com.example.sleppify.utils.YouTubeImageProcessor;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.content.Context;
import android.content.res.ColorStateList;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.FrameLayout;
import android.widget.PopupWindow;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Observer;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ConcatAdapter;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.Constraints;
import androidx.work.NetworkType;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.DecodeFormat;
import com.bumptech.glide.request.RequestOptions;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.imageview.ShapeableImageView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public class PlaylistDetailFragment extends Fragment {

    private static final String PREFS_STREAMING_CACHE = "streaming_cache";
    private static final long TRACKS_CACHE_TTL_MS = 24 * 60 * 60 * 1000L;
    private static final String PREF_TRACKS_UPDATED_AT_PREFIX = "playlist_tracks_updated_at_";
    private static final String PREF_TRACKS_DATA_PREFIX = "playlist_tracks_data_";
    private static final String PREF_TRACKS_FULL_CACHE_PREFIX = "playlist_tracks_cache_full_";
    private static final String PREF_PLAYLIST_OFFLINE_COMPLETE_PREFIX = "playlist_offline_complete_";
    private static final String PREF_CACHED_GOOGLE_PROFILE_PHOTO_URL = "cached_google_profile_photo_url";
    private static final String PREF_PLAYLIST_OFFLINE_AUTO_PREFIX = "playlist_offline_auto_";
    private static final String PREFS_PLAYER_STATE = "player_state";
    private static final String PREF_PLAYBACK_POS_PREFIX = "yt_pos_";
    private static final String PREF_LAST_PLAYLIST_ID = "stream_last_playlist_id";
    private static final String PREF_LAST_PLAYLIST_TITLE = "stream_last_playlist_title";
    private static final String PREF_LAST_PLAYLIST_SUBTITLE = "stream_last_playlist_subtitle";
    private static final String PREF_LAST_PLAYLIST_THUMBNAIL = "stream_last_playlist_thumbnail";
    private static final String PREF_LAST_VIDEO_ID = "stream_last_video_id";
    private static final String PREF_LAST_TRACK_TITLE = "stream_last_track_title";
    private static final String PREF_LAST_TRACK_ARTIST = "stream_last_track_artist";
    private static final String PREF_LAST_TRACK_IMAGE = "stream_last_track_image";
    private static final String PREF_LAST_TRACK_DURATION = "stream_last_track_duration";
    private static final String PREF_LAST_IS_PLAYING = "stream_last_is_playing";
    private static final String PREF_LAST_STREAM_SCREEN = "stream_last_screen";
    private static final String PREF_LAST_YOUTUBE_ACCESS_TOKEN = "stream_last_youtube_access_token";
    private static final String STREAM_SCREEN_LIBRARY = "library";
    private static final String STREAM_SCREEN_PLAYLIST_DETAIL = "playlist_detail";
    private static final long MINI_PROGRESS_TICK_MS = 850L;
    private static final long MINI_SNAPSHOT_REFRESH_MS = 1200L;
    private static final long TRACKS_TOKEN_RETRY_DELAY_MS = 1200L;
    private static final int MAX_TRACKS_TOKEN_RETRY = 3;
    private static final int PLAYLIST_TRACKS_INITIAL_FETCH_LIMIT = 280;
    private static final int PLAYLIST_TRACKS_FETCH_STEP = 220;
    private static final int PLAYLIST_TRACKS_FETCH_MAX_LIMIT = 1800;
    private static final int PLAYLIST_TRACKS_LOAD_MORE_THRESHOLD = 12;
    private static final String OFFLINE_DOWNLOAD_UNIQUE_PREFIX = "offline_playlist_";
    private static final String OFFLINE_DOWNLOAD_QUEUE_UNIQUE_NAME = "offline_playlist_queue";
    private static final String OFFLINE_DOWNLOAD_MANUAL_TRACK_QUEUE_UNIQUE_NAME = "offline_manual_track_queue";
    private static final String TAG_PLAYLIST_DETAIL = "playlist_detail";
    private static final String TAG_MODULE_MUSIC = "module_music";
    private static final String TAG_OFFLINE_DOWNLOAD = "OfflinePlaylistDl";
    private static final int ARTWORK_PREFETCH_MAX_ITEMS = 96;
    private static final int ARTWORK_PREFETCH_BATCH_SIZE = 8;
    private static final long ARTWORK_PREFETCH_BATCH_DELAY_MS = 90L;
    private static final long ARTWORK_PREFETCH_INITIAL_DELAY_MS = 650L;
    private static final long PLAYLIST_INITIAL_CONTENT_FADE_MS = 220L;

    public static final String ARG_PLAYLIST_ID = "arg_playlist_id";
    public static final String ARG_PLAYLIST_TITLE = "arg_playlist_title";
    public static final String ARG_PLAYLIST_SUBTITLE = "arg_playlist_subtitle";
    public static final String ARG_PLAYLIST_THUMBNAIL = "arg_playlist_thumbnail";
    public static final String ARG_YOUTUBE_ACCESS_TOKEN = "arg_youtube_access_token";

    private RecyclerView rvPlaylistContent;
    private SwipeRefreshLayout swipePlaylistRefresh;
    private View playlistLoadingOverlay;
    private ProgressBar pbPlaylistLoading;
    private ShapeableImageView ivMiniPlayerArt;
    private TextView tvMiniPlayerTitle;
    private TextView tvMiniPlayerSubtitle;
    private SeekBar sbMiniPlayerProgress;
    private LinearLayout llMiniPlayer;
    private ImageButton btnMiniPlayPause;

    private final YouTubeMusicService youTubeMusicService = new YouTubeMusicService();
    private final List<PlaylistTrack> originalTracks = new ArrayList<>();
    private final List<PlaylistTrack> currentTracks = new ArrayList<>();
    private final List<PlaylistTrack> playbackQueueTracks = new ArrayList<>();
    private PlaylistHeaderAdapter headerAdapter;
    private PlaylistTrackAdapter trackAdapter;
    private int currentTrackIndex = -1;
    private boolean miniPlaying;
    private boolean shuffleModeEnabled;
    private final Random random = new Random();
    private PlaylistMeta currentMeta = new PlaylistMeta("", 0, "", "", "");
    @NonNull
    private String currentPlaylistId = "";
    @NonNull
    private String currentPlaylistTitle = "";
    @NonNull
    private String currentPlaylistSubtitle = "";
    @NonNull
    private String currentPlaylistThumbnail = "";
    @NonNull
    private String lastPersistedVideoId = "";
    private int lastPersistedSecond = -1;
    private boolean lastPersistedPlaying;
    @NonNull
    private String headerPlaylistTitle = "Lista";
    @NonNull
    private String headerPlaylistInfo = "Lista";
    @NonNull
    private CharSequence headerTracksState = "Cargando Playlist...";
    @NonNull
    private String headerProfileName = "Tu cuenta";
    @Nullable
    private Uri headerProfilePhoto;
    @NonNull
    private String headerPlaylistThumbnail = "";
    private boolean headerAmoledModeEnabled;
    private int pendingTracksTokenRetry;
    private int playlistTracksRequestedLimit = PLAYLIST_TRACKS_INITIAL_FETCH_LIMIT;
    private boolean playlistTracksLoadMoreInFlight;
    private boolean playlistTracksCanLoadMore;
    @Nullable
    private Runnable pendingTracksTokenRetryRunnable;
    @Nullable
    private Observer<List<WorkInfo>> offlineDownloadObserver;
    @Nullable
    private String observingOfflineUniqueName;
    private boolean offlineObserverNotifyTerminalToasts;
    @Nullable
    private ActivityResultLauncher<String[]> offlineImportLauncher;
    @NonNull
    private final Set<String> pendingImportTrackIds = new HashSet<>();
    private boolean offlineDownloadRunning;
    private boolean offlineDownloadQueued;
    @NonNull
    private String offlineDownloadingTrackId = "";
    @NonNull
    private final Set<String> offlineDownloadingTrackIds = new HashSet<>();
    @NonNull
    private CharSequence offlineHeaderTracksState = "";
    private int offlineProgressDone = -1;
    private int offlineProgressDownloaded = -1;
    @NonNull
    private final Map<String, Float> offlineTrackProgressFractions = new HashMap<>();
    private boolean restoringHiddenPlayerFromSnapshot;
    @Nullable
    private PopupWindow trackActionPopupWindow;
    private boolean awaitingInitialPlaylistRender = true;
    private int lastMiniArtTrackIndex = -1;
    @NonNull
    private String lastMiniArtUrl = "";
    private int artworkPrefetchGeneration;
    @Nullable
    private PlaybackHistoryStore.Snapshot miniSnapshotCache;
    private long miniSnapshotCacheReadAtMs;
    private final ExecutorService offlineReadyStateExecutor = Executors.newSingleThreadExecutor();
    private final AtomicLong offlineReadyStateGeneration = new AtomicLong(0L);
    private final Handler miniProgressHandler = new Handler(Looper.getMainLooper());
    private final Runnable miniProgressTicker = new Runnable() {
        @Override
        public void run() {
            if (!isAdded() || isHidden()) {
                return;
            }
            updateMiniPlayerUi();
            miniProgressHandler.postDelayed(this, MINI_PROGRESS_TICK_MS);
        }
    };

    @NonNull
    public static PlaylistDetailFragment newInstance(
            @NonNull String playlistId,
            @NonNull String playlistTitle,
            @NonNull String playlistSubtitle,
            @NonNull String playlistThumbnail,
            @NonNull String accessToken
    ) {
        PlaylistDetailFragment fragment = new PlaylistDetailFragment();
        Bundle args = new Bundle();
        args.putString(ARG_PLAYLIST_ID, playlistId);
        args.putString(ARG_PLAYLIST_TITLE, playlistTitle);
        args.putString(ARG_PLAYLIST_SUBTITLE, playlistSubtitle);
        args.putString(ARG_PLAYLIST_THUMBNAIL, playlistThumbnail);
        args.putString(ARG_YOUTUBE_ACCESS_TOKEN, accessToken);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        offlineImportLauncher = registerForActivityResult(
                new ActivityResultContracts.OpenMultipleDocuments(),
                this::handleOfflineImportSelection
        );
        headerAmoledModeEnabled = isAmoledModeEnabled();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_playlist_detail, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).setContainerOverlayMode(false);
        }
        headerAmoledModeEnabled = isAmoledModeEnabled();

        rvPlaylistContent = view.findViewById(R.id.rvPlaylistContent);
        swipePlaylistRefresh = view.findViewById(R.id.swipePlaylistRefresh);
        playlistLoadingOverlay = view.findViewById(R.id.flPlaylistLoadingOverlay);
        pbPlaylistLoading = view.findViewById(R.id.pbPlaylistLoading);
        ivMiniPlayerArt = view.findViewById(R.id.ivMiniPlayerArt);
        tvMiniPlayerTitle = view.findViewById(R.id.tvMiniPlayerTitle);
        tvMiniPlayerSubtitle = view.findViewById(R.id.tvMiniPlayerSubtitle);
        sbMiniPlayerProgress = view.findViewById(R.id.sbMiniPlayerProgress);
        llMiniPlayer = view.findViewById(R.id.llMiniPlayer);
        btnMiniPlayPause = view.findViewById(R.id.btnMiniPlayPause);

        String playlistId = safeArg(ARG_PLAYLIST_ID);
        String playlistTitle = safeArg(ARG_PLAYLIST_TITLE);
        String playlistSubtitle = safeArg(ARG_PLAYLIST_SUBTITLE);
        String playlistThumbnail = safeArg(ARG_PLAYLIST_THUMBNAIL);
        String youtubeAccessToken = resolveYoutubeAccessToken(safeArg(ARG_YOUTUBE_ACCESS_TOKEN));
        playlistId = normalizeLikedPlaylistId(playlistId, playlistTitle, playlistSubtitle);
        if (isFavoritesPlaylistContext(playlistId)) {
            playlistTitle = FavoritesPlaylistStore.PLAYLIST_TITLE;
            playlistSubtitle = FavoritesPlaylistStore.buildSubtitle(
                FavoritesPlaylistStore.getFavoritesCount(requireContext())
            );
        }
        currentPlaylistId = playlistId;
        currentPlaylistTitle = playlistTitle;
        currentPlaylistSubtitle = playlistSubtitle;
        currentPlaylistThumbnail = playlistThumbnail;
        persistStreamingScreen(STREAM_SCREEN_PLAYLIST_DETAIL);

        if (playlistTitle.isEmpty()) {
            playlistTitle = "Lista";
            currentPlaylistTitle = playlistTitle;
        }

        PlaylistMeta meta = parseMeta(playlistSubtitle);
        currentMeta = meta;
        bindHeader(playlistTitle, meta, playlistThumbnail);
        showInitialLoadingOverlay();

        headerAdapter = new PlaylistHeaderAdapter();
        trackAdapter = new PlaylistTrackAdapter(new ArrayList<>(), new OnTrackTap() {
            @Override
            public void onTap(int position) {
                onTrackSelected(position);
            }

            @Override
            public void onMoreTap(int position, @NonNull View anchor) {
                onTrackMorePressed(position, anchor);
            }
        });

        LinearLayoutManager layoutManager = new LinearLayoutManager(requireContext());
        rvPlaylistContent.setLayoutManager(layoutManager);
        rvPlaylistContent.setHasFixedSize(true);
        rvPlaylistContent.setItemAnimator(null);
        rvPlaylistContent.setItemViewCacheSize(20);
        RecyclerView.RecycledViewPool pool = new RecyclerView.RecycledViewPool();
        pool.setMaxRecycledViews(0, 25);
        rvPlaylistContent.setRecycledViewPool(pool);
        rvPlaylistContent.setAdapter(new ConcatAdapter(
            headerAdapter,
            trackAdapter
        ));
        setupPlaylistLoadMoreScroll(layoutManager);

        playlistTracksRequestedLimit = PLAYLIST_TRACKS_INITIAL_FETCH_LIMIT;
        playlistTracksLoadMoreInFlight = false;
        playlistTracksCanLoadMore = false;

        setupPlaylistPullToRefresh();

        shuffleModeEnabled = loadPersistedShuffleMode();
        syncShuffleModeFromPlayer();
        restoreOfflineDownloadObservation();

        updateShuffleButtonState();

        llMiniPlayer.setOnClickListener(v -> openPlayerFromMiniBar());
        btnMiniPlayPause.setOnClickListener(v -> toggleMiniPlayback());
        maybeRestoreHiddenPlayerFromSnapshot();
        updateMiniPlayerUi();
        startMiniProgressTicker();

        final String requestPlaylistId = playlistId;
        final String requestToken = youtubeAccessToken;
        view.post(() -> {
            if (!isAdded() || getView() == null) {
                return;
            }
            refreshPlaylistMeta(requestPlaylistId, requestToken);
            bindTrackList(requestPlaylistId, requestToken);
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        boolean updatedAmoledMode = isAmoledModeEnabled();
        if (headerAmoledModeEnabled != updatedAmoledMode) {
            headerAmoledModeEnabled = updatedAmoledMode;
            notifyHeaderChanged();
        }
        persistStreamingScreen(STREAM_SCREEN_PLAYLIST_DETAIL);
        restoreOfflineDownloadObservation();
        invalidateMiniSnapshotCache();
        lastMiniArtTrackIndex = -1;
        lastMiniArtUrl = "";
        refreshVisibleTrackRows();
        maybeUpdateOfflineReadyState();
        startMiniProgressTicker();
        maybeRestoreHiddenPlayerFromSnapshot();
        syncShuffleModeFromPlayer();
        updateShuffleButtonState();
        updateMiniPlayerUi();
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).setContainerOverlayMode(false);
        }
        if (hidden) {
            stopMiniProgressTicker();
            return;
        }
        boolean updatedAmoledMode = isAmoledModeEnabled();
        if (headerAmoledModeEnabled != updatedAmoledMode) {
            headerAmoledModeEnabled = updatedAmoledMode;
            notifyHeaderChanged();
        }
        persistStreamingScreen(STREAM_SCREEN_PLAYLIST_DETAIL);
        restoreOfflineDownloadObservation();
        startMiniProgressTicker();
        maybeRestoreHiddenPlayerFromSnapshot();
        syncShuffleModeFromPlayer();
        updateShuffleButtonState();
        updateMiniPlayerUi();
    }

    private void persistStreamingScreen(@NonNull String screen) {
        if (!isAdded()) {
            return;
        }
        requireContext()
                .getSharedPreferences(PREFS_PLAYER_STATE, Activity.MODE_PRIVATE)
                .edit()
                .putString(PREF_LAST_STREAM_SCREEN, screen)
                .apply();
    }

    @Override
    public void onPause() {
        stopMiniProgressTicker();
        super.onPause();
    }

    @Override
    public void onDestroyView() {
        dismissTrackActionPopup();
        stopObservingOfflineDownload();
        setOfflineDownloadVisualState(false, "");
        pendingImportTrackIds.clear();
        cancelPendingTracksTokenRetry();
        persistLibraryScreenIfReturningToMusic();
        stopMiniProgressTicker();
        setPlaylistPullRefreshState(false);
        artworkPrefetchGeneration++;
        invalidateMiniSnapshotCache();
        offlineReadyStateGeneration.incrementAndGet();
        restoringHiddenPlayerFromSnapshot = false;
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).setContainerOverlayMode(false);
        }
        playlistLoadingOverlay = null;
        pbPlaylistLoading = null;
        awaitingInitialPlaylistRender = true;
        rvPlaylistContent = null;
        swipePlaylistRefresh = null;
        ivMiniPlayerArt = null;
        tvMiniPlayerTitle = null;
        tvMiniPlayerSubtitle = null;
        sbMiniPlayerProgress = null;
        llMiniPlayer = null;
        btnMiniPlayPause = null;
        super.onDestroyView();
    }

    @Override
    public void onDestroy() {
        offlineReadyStateExecutor.shutdownNow();
        super.onDestroy();
    }

    private void setupPlaylistPullToRefresh() {
        if (swipePlaylistRefresh == null || !isAdded()) {
            return;
        }

        swipePlaylistRefresh.setColorSchemeColors(
                ContextCompat.getColor(requireContext(), R.color.stitch_blue),
                ContextCompat.getColor(requireContext(), android.R.color.white)
        );
        swipePlaylistRefresh.setProgressBackgroundColorSchemeColor(
                ContextCompat.getColor(requireContext(), R.color.surface_low)
        );
        swipePlaylistRefresh.setDistanceToTriggerSync(Math.round(80f * getResources().getDisplayMetrics().density));
        swipePlaylistRefresh.setOnChildScrollUpCallback((parent, child) -> {
            if (rvPlaylistContent != null) {
                return rvPlaylistContent.canScrollVertically(-1);
            }
            return child != null && child.canScrollVertically(-1);
        });
        swipePlaylistRefresh.setOnRefreshListener(this::triggerPlaylistPullRefresh);
    }

    private void setupPlaylistLoadMoreScroll(@NonNull LinearLayoutManager layoutManager) {
        rvPlaylistContent.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                if (dy <= 0 || !playlistTracksCanLoadMore || playlistTracksLoadMoreInFlight) {
                    return;
                }

                int totalItems = layoutManager.getItemCount();
                int lastVisible = layoutManager.findLastVisibleItemPosition();
                if (totalItems <= 0 || lastVisible < 0) {
                    return;
                }

                int remaining = totalItems - lastVisible;
                if (remaining <= PLAYLIST_TRACKS_LOAD_MORE_THRESHOLD) {
                    requestMorePlaylistTracksIfNeeded();
                }
            }
        });
    }

    private void setPlaylistPullRefreshState(boolean refreshing) {
        if (swipePlaylistRefresh == null) {
            return;
        }
        if (swipePlaylistRefresh.isRefreshing() == refreshing) {
            return;
        }
        swipePlaylistRefresh.setRefreshing(refreshing);
    }

    private void showInitialLoadingOverlay() {
        awaitingInitialPlaylistRender = true;
        if (rvPlaylistContent != null) {
            rvPlaylistContent.animate().cancel();
            rvPlaylistContent.setAlpha(0f);
        }
        if (llMiniPlayer != null) {
            llMiniPlayer.animate().cancel();
            llMiniPlayer.setAlpha(0f);
        }
        if (playlistLoadingOverlay != null) {
            playlistLoadingOverlay.animate().cancel();
            playlistLoadingOverlay.setAlpha(1f);
            playlistLoadingOverlay.setVisibility(View.VISIBLE);
            playlistLoadingOverlay.bringToFront();
        }
        if (pbPlaylistLoading != null) {
            pbPlaylistLoading.setVisibility(View.VISIBLE);
        }
    }

    private void revealPlaylistContentIfNeeded(boolean animated) {
        if (!awaitingInitialPlaylistRender) {
            return;
        }
        awaitingInitialPlaylistRender = false;

        if (rvPlaylistContent != null) {
            rvPlaylistContent.animate().cancel();
            if (animated) {
                rvPlaylistContent.animate().alpha(1f).setDuration(PLAYLIST_INITIAL_CONTENT_FADE_MS).start();
            } else {
                rvPlaylistContent.setAlpha(1f);
            }
        }

        if (llMiniPlayer != null) {
            llMiniPlayer.animate().cancel();
            if (animated) {
                llMiniPlayer.animate().alpha(1f).setDuration(PLAYLIST_INITIAL_CONTENT_FADE_MS).start();
            } else {
                llMiniPlayer.setAlpha(1f);
            }
        }

        if (playlistLoadingOverlay != null) {
            playlistLoadingOverlay.animate().cancel();
            if (animated) {
                playlistLoadingOverlay.animate()
                        .alpha(0f)
                        .setDuration(PLAYLIST_INITIAL_CONTENT_FADE_MS)
                        .withEndAction(() -> {
                            if (playlistLoadingOverlay != null) {
                                playlistLoadingOverlay.setVisibility(View.GONE);
                            }
                        })
                        .start();
            } else {
                playlistLoadingOverlay.setAlpha(0f);
                playlistLoadingOverlay.setVisibility(View.GONE);
            }
        }

        if (pbPlaylistLoading != null) {
            pbPlaylistLoading.setVisibility(View.GONE);
        }
    }

    private void triggerPlaylistPullRefresh() {
        if (!isAdded()) {
            setPlaylistPullRefreshState(false);
            return;
        }
        if (TextUtils.isEmpty(currentPlaylistId)) {
            setPlaylistPullRefreshState(false);
            return;
        }

        setPlaylistPullRefreshState(true);
        playlistTracksRequestedLimit = PLAYLIST_TRACKS_INITIAL_FETCH_LIMIT;
        playlistTracksCanLoadMore = false;
        playlistTracksLoadMoreInFlight = false;
        String token = resolveYoutubeAccessToken("");
        if (!TextUtils.isEmpty(token)) {
            refreshPlaylistMeta(currentPlaylistId, token);
        }
        bindTrackList(currentPlaylistId, token, true);
    }

    private void requestMorePlaylistTracksIfNeeded() {
        if (!isAdded()
                || TextUtils.isEmpty(currentPlaylistId)
                || playlistTracksLoadMoreInFlight
                || !playlistTracksCanLoadMore) {
            return;
        }

        String token = resolveYoutubeAccessToken("");
        if (TextUtils.isEmpty(token)) {
            return;
        }

        playlistTracksLoadMoreInFlight = true;
        bindTrackList(currentPlaylistId, token, false, true);
    }

    private void persistLibraryScreenIfReturningToMusic() {
        Activity activity = getActivity();
        if (activity == null || activity.isFinishing()) {
            return;
        }

        Fragment music = getParentFragmentManager().findFragmentByTag(TAG_MODULE_MUSIC);
        if (music == null || !music.isAdded() || music.isHidden()) {
            return;
        }

        persistStreamingScreen(STREAM_SCREEN_LIBRARY);
    }

    private void startMiniProgressTicker() {
        if (!isAdded() || getView() == null || isHidden()) {
            return;
        }
        miniProgressHandler.removeCallbacks(miniProgressTicker);
        miniProgressHandler.post(miniProgressTicker);
    }

    private void stopMiniProgressTicker() {
        miniProgressHandler.removeCallbacks(miniProgressTicker);
    }

    private void notifyHeaderChanged() {
        if (headerAdapter == null) {
            return;
        }
        headerAdapter.notifyItemChanged(0);
    }

    private void refreshVisibleTrackRows() {
        if (trackAdapter == null) {
            return;
        }

        trackAdapter.invalidateTrackStateCache();
        int totalTracks = trackAdapter.getItemCount();
        if (totalTracks <= 0) {
            return;
        }

        int start = 0;
        int end = Math.min(totalTracks - 1, 23);

        if (rvPlaylistContent != null && rvPlaylistContent.getLayoutManager() instanceof LinearLayoutManager) {
            LinearLayoutManager layoutManager = (LinearLayoutManager) rvPlaylistContent.getLayoutManager();
            int firstVisibleGlobal = layoutManager.findFirstVisibleItemPosition();
            int lastVisibleGlobal = layoutManager.findLastVisibleItemPosition();
            if (firstVisibleGlobal >= 0 && lastVisibleGlobal >= firstVisibleGlobal) {
                start = Math.max(0, firstVisibleGlobal - 1);
                end = Math.max(start, Math.min(totalTracks - 1, lastVisibleGlobal - 1));
            }
        }

        trackAdapter.notifyItemRangeChanged(start, (end - start) + 1);
    }

    private void setOfflineDownloadVisualState(boolean running, @Nullable String currentTrackId) {
        setOfflineDownloadVisualState(running, currentTrackId, null, null);
    }

    private void setOfflineDownloadVisualState(
            boolean running,
            @Nullable String currentTrackId,
            @Nullable String[] activeTrackIds,
            @Nullable Map<String, Float> progressByTrackId
    ) {
        offlineDownloadRunning = running;
        offlineDownloadingTrackId = currentTrackId == null ? "" : currentTrackId.trim();
        offlineDownloadingTrackIds.clear();
        offlineTrackProgressFractions.clear();

        if (running && activeTrackIds != null && activeTrackIds.length > 0) {
            for (String trackId : activeTrackIds) {
                if (TextUtils.isEmpty(trackId)) {
                    continue;
                }
                offlineDownloadingTrackIds.add(trackId.trim());
            }
        }

        if (running
                && offlineDownloadingTrackIds.isEmpty()
                && !TextUtils.isEmpty(offlineDownloadingTrackId)) {
            offlineDownloadingTrackIds.add(offlineDownloadingTrackId);
        }

        if (running && progressByTrackId != null && !progressByTrackId.isEmpty()) {
            for (Map.Entry<String, Float> entry : progressByTrackId.entrySet()) {
                if (entry == null || TextUtils.isEmpty(entry.getKey())) {
                    continue;
                }
                float value = entry.getValue() == null ? 0f : entry.getValue();
                offlineTrackProgressFractions.put(entry.getKey().trim(), Math.max(0f, Math.min(1f, value)));
            }
        }

        if (!offlineDownloadRunning) {
            offlineProgressDone = -1;
            offlineProgressDownloaded = -1;
        }

        if (trackAdapter != null) {
            trackAdapter.setOfflineDownloadState(offlineDownloadRunning, offlineDownloadingTrackIds, offlineTrackProgressFractions);
        }
    }

    private boolean isOfflineStatusPinned() {
        return offlineDownloadRunning || offlineDownloadQueued || !TextUtils.isEmpty(offlineHeaderTracksState);
    }

    private void restoreOfflineDownloadObservation() {
        if (!isAdded() || TextUtils.isEmpty(currentPlaylistId)) {
            return;
        }
        observeOfflineDownload(OFFLINE_DOWNLOAD_QUEUE_UNIQUE_NAME, false);
    }

    @NonNull
    private String currentPlaylistOfflineTag() {
        return OFFLINE_DOWNLOAD_UNIQUE_PREFIX + (TextUtils.isEmpty(currentPlaylistId) ? "current" : currentPlaylistId);
    }

    private boolean isCurrentPlaylistOfflineAutoEnabled() {
        if (!isAdded() || TextUtils.isEmpty(currentPlaylistId)) {
            return false;
        }
        return getCachePrefs().getBoolean(PREF_PLAYLIST_OFFLINE_AUTO_PREFIX + currentPlaylistId, false);
    }

    private void setCurrentPlaylistOfflineAutoEnabled(boolean enabled) {
        if (!isAdded() || TextUtils.isEmpty(currentPlaylistId)) {
            return;
        }
        getCachePrefs().edit()
                .putBoolean(PREF_PLAYLIST_OFFLINE_AUTO_PREFIX + currentPlaylistId, enabled)
                .apply();
    }

    private void onOfflineTogglePressed() {
        if (!isAdded()) {
            return;
        }

        if (!isCurrentPlaylistOfflineAutoEnabled()) {
            setCurrentPlaylistOfflineAutoEnabled(true);
            notifyHeaderChanged();
            startOfflinePlaylistDownload(true);
            return;
        }

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Desactivar descargas offline")
                .setMessage("Se eliminaran todas las canciones descargadas de esta playlist. ¿Deseas continuar?")
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Aceptar", (dialog, which) -> disableOfflineForCurrentPlaylist())
                .show();
    }

    private void disableOfflineForCurrentPlaylist() {
        if (!isAdded()) {
            return;
        }

        setCurrentPlaylistOfflineAutoEnabled(false);
        WorkManager.getInstance(requireContext().getApplicationContext())
                .cancelAllWorkByTag(currentPlaylistOfflineTag());

        setOfflineDownloadVisualState(false, "");
        offlineDownloadQueued = false;
        offlineHeaderTracksState = "Eliminando descargas offline...";
        notifyHeaderChanged();

        ArrayList<String> idsToDelete = buildCurrentVideoIds();
        if (idsToDelete.isEmpty()) {
            offlineHeaderTracksState = "";
            notifyHeaderChanged();
            
            return;
        }

        final Activity hostActivity = getActivity();
        final android.content.Context appContext = requireContext().getApplicationContext();

        new Thread(() -> {
            int removed = OfflineAudioStore.deleteOfflineAudio(appContext, idsToDelete);
            if (hostActivity == null || hostActivity.isFinishing()) {
                return;
            }
            hostActivity.runOnUiThread(() -> {
                if (!isAdded()) {
                    return;
                }
                offlineHeaderTracksState = "";
                notifyHeaderChanged();
                if (trackAdapter != null) {
                    trackAdapter.invalidateTrackStateCache();
                    trackAdapter.notifyDataSetChanged();
                }
                maybeUpdateOfflineReadyState();
                
            });
        }, "playlist-offline-cleanup").start();
    }

    private void maybeAutoDownloadForCurrentPlaylist() {
        if (!isAdded() || !isCurrentPlaylistOfflineAutoEnabled() || currentTracks.isEmpty()) {
            return;
        }
        if (offlineDownloadRunning || offlineDownloadQueued) {
            return;
        }
        startOfflinePlaylistDownload(false);
    }


    @NonNull
    private String safeArg(@NonNull String key) {
        Bundle args = getArguments();
        if (args == null) {
            return "";
        }
        String value = args.getString(key);
        return value == null ? "" : value.trim();
    }

    private void bindHeader(@NonNull String playlistTitle, @NonNull PlaylistMeta meta, @NonNull String playlistThumbnail) {
        headerPlaylistTitle = playlistTitle;
        headerPlaylistInfo = buildPlaylistInfoLine(meta, currentTracks.isEmpty() ? 0 : currentTracks.size());
        headerPlaylistThumbnail = playlistThumbnail;
        bindGoogleProfile(meta.ownerLabel);
        notifyHeaderChanged();
    }

    private void bindGoogleProfile(@NonNull String fallbackName) {
        String profileName = resolvePrimaryUserName();
        FirebaseUser firebaseUser = FirebaseAuth.getInstance().getCurrentUser();
        GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(requireContext());
        Uri profilePhoto = null;
        String cachedPhotoUrl = getCachePrefs().getString(PREF_CACHED_GOOGLE_PROFILE_PHOTO_URL, "");

        if (account != null) {
            if (TextUtils.isEmpty(profileName) && !TextUtils.isEmpty(account.getDisplayName())) {
                profileName = account.getDisplayName();
            }
            profilePhoto = account.getPhotoUrl();
            if (profilePhoto != null && !TextUtils.isEmpty(profilePhoto.toString())) {
                getCachePrefs().edit().putString(PREF_CACHED_GOOGLE_PROFILE_PHOTO_URL, profilePhoto.toString()).apply();
            }
        }

        if (profilePhoto == null && firebaseUser != null && firebaseUser.getPhotoUrl() != null) {
            profilePhoto = firebaseUser.getPhotoUrl();
            if (!TextUtils.isEmpty(profilePhoto.toString())) {
                getCachePrefs().edit().putString(PREF_CACHED_GOOGLE_PROFILE_PHOTO_URL, profilePhoto.toString()).apply();
            }
        }

        if (profilePhoto == null && !TextUtils.isEmpty(cachedPhotoUrl)) {
            profilePhoto = Uri.parse(cachedPhotoUrl);
        }

        if (TextUtils.isEmpty(profileName)) {
            profileName = fallbackName;
        }

        headerProfileName = extractFirstName(profileName);
        headerProfilePhoto = profilePhoto;
        if (profilePhoto != null) {
            prefetchImageForOffline(profilePhoto.toString());
        }
        notifyHeaderChanged();
    }

    private void prefetchTrackArtworkForOffline(@NonNull List<PlaylistTrack> tracks, @Nullable String playlistThumbnail) {
        if (!isAdded() || !isInternetAvailable()) {
            return;
        }

        artworkPrefetchGeneration++;
        int generation = artworkPrefetchGeneration;

        HashSet<String> seen = new HashSet<>();
        List<String> pendingUrls = new ArrayList<>();
        if (!TextUtils.isEmpty(playlistThumbnail) && seen.add(playlistThumbnail)) {
            pendingUrls.add(playlistThumbnail);
        }

        for (PlaylistTrack track : tracks) {
            if (track == null || TextUtils.isEmpty(track.imageUrl)) {
                continue;
            }
            String imageUrl = track.imageUrl.trim();
            if (imageUrl.isEmpty() || !seen.add(imageUrl)) {
                continue;
            }
            pendingUrls.add(imageUrl);
            if (pendingUrls.size() >= ARTWORK_PREFETCH_MAX_ITEMS) {
                break;
            }
        }

        if (pendingUrls.isEmpty()) {
            return;
        }

        miniProgressHandler.postDelayed(
                () -> scheduleArtworkPrefetchBatch(pendingUrls, 0, generation),
                ARTWORK_PREFETCH_INITIAL_DELAY_MS
        );
    }

    private void scheduleArtworkPrefetchBatch(
            @NonNull List<String> imageUrls,
            int startIndex,
            int generation
    ) {
        if (!isAdded() || generation != artworkPrefetchGeneration || startIndex >= imageUrls.size()) {
            return;
        }

        int end = Math.min(startIndex + ARTWORK_PREFETCH_BATCH_SIZE, imageUrls.size());
        for (int i = startIndex; i < end; i++) {
            prefetchImageForOffline(imageUrls.get(i));
        }

        if (end < imageUrls.size()) {
            miniProgressHandler.postDelayed(
                    () -> scheduleArtworkPrefetchBatch(imageUrls, end, generation),
                    ARTWORK_PREFETCH_BATCH_DELAY_MS
            );
        }
    }

    private void prefetchImageForOffline(@Nullable String imageUrl) {
        if (!isAdded() || TextUtils.isEmpty(imageUrl)) {
            return;
        }

        Glide.with(this)
                .load(imageUrl)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .preload();
    }

    private static int bucketArtworkDimension(int value) {
        int safe = Math.max(1, value);
        return Math.max(64, ((safe + 63) / 64) * 64);
    }

    private static int resolveTargetDimension(int measured, int layoutValue) {
        if (measured > 0) {
            return measured;
        }
        if (layoutValue > 0) {
            return layoutValue;
        }
        return 0;
    }

    private static boolean hasValidatedInternet(@Nullable Context context) {
        if (context == null) {
            return false;
        }

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

    private static void loadArtworkInto(@NonNull ImageView target, @Nullable String imageUrl) {
        loadArtworkInto(target, imageUrl, 0);
    }

    private static void loadArtworkInto(@NonNull ImageView target, @Nullable String imageUrl, int fixedSizeDp) {
        if (TextUtils.isEmpty(imageUrl)) {
            target.setTag(R.id.tag_artwork_signature, null);
            target.setImageResource(R.drawable.ic_music);
            return;
        }

        String safeUrl = imageUrl.trim();
        Context context = target.getContext();
        float density = context.getResources().getDisplayMetrics().density;
        
        int targetWidth;
        int targetHeight;
        boolean hasTargetSize = false;
        int rawWidth = 0;
        int rawHeight = 0;

        if (fixedSizeDp > 0) {
            targetWidth = Math.round(fixedSizeDp * density);
            targetHeight = targetWidth;
        } else {
            ViewGroup.LayoutParams params = target.getLayoutParams();
            rawWidth = resolveTargetDimension(target.getWidth(), params == null ? 0 : params.width);
            rawHeight = resolveTargetDimension(target.getHeight(), params == null ? 0 : params.height);
            hasTargetSize = rawWidth > 0 && rawHeight > 0;

            if (hasTargetSize) {
                targetWidth = bucketArtworkDimension(rawWidth);
                targetHeight = bucketArtworkDimension(rawHeight);
            } else {
                // If not measured, use a sensible default for unmeasured views 
                // instead of full-screen to avoid memory spikes during initialization.
                targetWidth = Math.round(160 * density); 
                targetHeight = targetWidth;
            }
        }

        if (YouTubeImageProcessor.shouldProcess(safeUrl)) {
            if (fixedSizeDp > 0) {
                int displayPx = Math.round(fixedSizeDp * density);
                int side = YouTubeImageProcessor.decodeDimensionForSmartCrop(displayPx);
                targetWidth = side;
                targetHeight = side;
            } else if (hasTargetSize) {
                targetWidth = YouTubeImageProcessor.decodeDimensionForSmartCrop(rawWidth);
                targetHeight = YouTubeImageProcessor.decodeDimensionForSmartCrop(rawHeight);
            } else {
                int side = YouTubeImageProcessor.decodeDimensionForSmartCrop(targetWidth);
                targetWidth = side;
                targetHeight = side;
            }
        }

        String signature = safeUrl + "|" + targetWidth + "x" + targetHeight;
        Object previousSignature = target.getTag(R.id.tag_artwork_signature);
        if (previousSignature instanceof String && signature.equals(previousSignature)) {
            return;
        }
        target.setTag(R.id.tag_artwork_signature, signature);

        Glide.with(target)
            .load(safeUrl)
            .transform(new YouTubeCropTransformation())
            .format(DecodeFormat.PREFER_RGB_565)
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .placeholder(R.drawable.ic_music)
            .error(R.drawable.ic_music)
            .override(targetWidth, targetHeight)
            .thumbnail(0.15f)
            .dontAnimate()
            .into(target);
    }

    private boolean isInternetAvailable() {
        return isAdded() && hasValidatedInternet(requireContext());
    }

    private void refreshPlaylistMeta(@NonNull String playlistId, @NonNull String accessToken) {
        if (playlistId.isEmpty()
                || accessToken.isEmpty()
                || isLikedPlaylistContext(playlistId)
                || isFavoritesPlaylistContext(playlistId)) {
            return;
        }

        youTubeMusicService.fetchPlaylistMeta(accessToken, playlistId, new YouTubeMusicService.PlaylistMetaCallback() {
            @Override
            public void onSuccess(@NonNull YouTubeMusicService.PlaylistResult playlist) {
                if (!isAdded()) {
                    return;
                }
                currentMeta = new PlaylistMeta(
                        currentMeta.ownerLabel,
                        Math.max(currentMeta.songsCount, playlist.itemCount),
                        currentMeta.estimatedDuration,
                        buildVisibilityLabel(playlist.privacyStatus),
                        buildRelativeDateLabel(playlist.publishedAt)
                );
                headerPlaylistInfo = buildPlaylistInfoLine(currentMeta, currentTracks.isEmpty() ? 0 : currentTracks.size());
                notifyHeaderChanged();
            }

            @Override
            public void onError(@NonNull String error) {
                // Keep existing fallback metadata when API does not provide optional fields.
            }
        });
    }

    @NonNull
    private String resolvePrimaryUserName() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            return "";
        }

        String displayName = user.getDisplayName();
        if (!TextUtils.isEmpty(displayName)) {
            return displayName.trim();
        }

        String email = user.getEmail();
        if (!TextUtils.isEmpty(email)) {
            int atIndex = email.indexOf('@');
            if (atIndex > 0) {
                return email.substring(0, atIndex).trim();
            }
            return email.trim();
        }

        return "";
    }

    @NonNull
    private String extractFirstName(@NonNull String fullName) {
        String normalized = fullName.trim();
        if (normalized.isEmpty()) {
            return "Tu cuenta";
        }

        String[] bySpace = normalized.split("\\s+");
        if (bySpace.length > 0 && !TextUtils.isEmpty(bySpace[0])) {
            return capitalizeToken(bySpace[0]);
        }

        String[] byDot = normalized.split("[._-]");
        if (byDot.length > 0 && !TextUtils.isEmpty(byDot[0])) {
            return capitalizeToken(byDot[0]);
        }

        return capitalizeToken(normalized);
    }

    @NonNull
    private String capitalizeToken(@NonNull String token) {
        String value = token.trim();
        if (value.isEmpty()) {
            return "";
        }
        String lower = value.toLowerCase(Locale.US);
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    private void bindTrackList(@NonNull String playlistId, @NonNull String accessToken) {
        bindTrackList(playlistId, accessToken, false, false);
    }

    private void bindTrackList(@NonNull String playlistId, @NonNull String accessToken, boolean forceRefresh) {
        bindTrackList(playlistId, accessToken, forceRefresh, false);
    }

    private void bindTrackList(
            @NonNull String playlistId,
            @NonNull String accessToken,
            boolean forceRefresh,
            boolean loadMore
    ) {
        String effectiveAccessToken = resolveYoutubeAccessToken(accessToken);
        List<PlaylistTrack> cachedTracks = sanitizeTracksForPlaylist(playlistId, loadCachedTracks(playlistId));
        boolean favoritesContext = isFavoritesPlaylistContext(playlistId);
        boolean customContext = isCustomPlaylistContext(playlistId);
        int requestedLimit = resolveTrackFetchLimit(forceRefresh, loadMore);

        if (favoritesContext || customContext) {
            renderTracks(cachedTracks, playlistId, true);
            if (cachedTracks.isEmpty() && !isOfflineStatusPinned()) {
                headerTracksState = customContext ? "Aun no añades canciones a esta playlist." : "Aun no guardas canciones en Favoritos.";
                notifyHeaderChanged();
            }
            if (forceRefresh) {
                setPlaylistPullRefreshState(false);
            }
            playlistTracksLoadMoreInFlight = false;
            playlistTracksCanLoadMore = false;
            return;
        }

        boolean canRequestRemote = !playlistId.isEmpty() && !effectiveAccessToken.isEmpty();
        boolean hasCompleteCache = hasCompleteTracksCache(playlistId, cachedTracks);

        if (!cachedTracks.isEmpty()) {
            renderTracks(cachedTracks, playlistId, true);
            if (!forceRefresh && !loadMore) {
                playlistTracksLoadMoreInFlight = false;
                playlistTracksCanLoadMore = !hasCompleteCache;
                return;
            }
        }

        if (!canRequestRemote) {
            if (cachedTracks.isEmpty()) {
                List<PlaylistTrack> staleTracks = sanitizeTracksForPlaylist(
                        playlistId,
                        loadCachedTracksInternal(playlistId, true)
                );
                if (!staleTracks.isEmpty()) {
                    renderTracks(staleTracks, playlistId, true);
                    if (forceRefresh) {
                        setPlaylistPullRefreshState(false);
                    }
                    return;
                }

                if (!playlistId.isEmpty() && TextUtils.isEmpty(effectiveAccessToken)) {
                    scheduleTracksTokenRetry(playlistId);
                    headerTracksState = "Sincronizando canciones...";
                } else {
                    headerTracksState = "No se pudo cargar esta lista.";
                }
                notifyHeaderChanged();
                revealPlaylistContentIfNeeded(true);
            }
            if (forceRefresh) {
                setPlaylistPullRefreshState(false);
            }
            playlistTracksLoadMoreInFlight = false;
            return;
        }

        if (loadMore) {
            headerTracksState = "Cargando mas canciones...";
        } else {
            headerTracksState = currentTracks.isEmpty()
                    ? "Cargando Playlist..."
                    : "Sincronizando canciones...";
        }
        notifyHeaderChanged();
        youTubeMusicService.fetchPlaylistTracks(effectiveAccessToken, playlistId, requestedLimit, new YouTubeMusicService.PlaylistTracksCallback() {
            @Override
            public void onSuccess(@NonNull List<YouTubeMusicService.PlaylistTrackResult> tracks) {
                if (!isAdded()) {
                    return;
                }

                playlistTracksRequestedLimit = requestedLimit;
                playlistTracksLoadMoreInFlight = false;
                playlistTracksCanLoadMore = tracks.size() >= requestedLimit
                        && requestedLimit < PLAYLIST_TRACKS_FETCH_MAX_LIMIT;

                List<PlaylistTrack> mapped = sanitizeTracksForPlaylist(playlistId, mapTracks(tracks));
                cacheTracks(playlistId, mapped, isFetchResultComplete(tracks.size(), requestedLimit));
                renderTracks(mapped, playlistId, false);
                if (forceRefresh) {
                    setPlaylistPullRefreshState(false);
                }
            }

            @Override
            public void onError(@NonNull String error) {
                if (!isAdded()) {
                    return;
                }

                playlistTracksLoadMoreInFlight = false;

                if (!cachedTracks.isEmpty()) {
                    renderTracks(cachedTracks, playlistId, true);
                    if (forceRefresh) {
                        setPlaylistPullRefreshState(false);
                    }
                    return;
                }

                List<PlaylistTrack> staleTracks = sanitizeTracksForPlaylist(
                        playlistId,
                        loadCachedTracksInternal(playlistId, true)
                );
                if (!staleTracks.isEmpty()) {
                    renderTracks(staleTracks, playlistId, true);
                    if (forceRefresh) {
                        setPlaylistPullRefreshState(false);
                    }
                    return;
                }

                String refreshedToken = resolveYoutubeAccessToken("");
                if (!TextUtils.isEmpty(refreshedToken)
                        && !TextUtils.equals(refreshedToken, effectiveAccessToken)) {
                    bindTrackList(playlistId, refreshedToken, forceRefresh, loadMore);
                    return;
                }

                headerTracksState = "No se pudo leer la lista.";
                notifyHeaderChanged();
                currentTracks.clear();
                trackAdapter.submitTracks(currentTracks);
                currentTrackIndex = -1;
                miniPlaying = false;
                updateMiniPlayerUi();
                revealPlaylistContentIfNeeded(true);
                playlistTracksCanLoadMore = false;
                if (forceRefresh) {
                    setPlaylistPullRefreshState(false);
                }
            }
        });
    }

    private int resolveTrackFetchLimit(boolean forceRefresh, boolean loadMore) {
        if (forceRefresh) {
            playlistTracksRequestedLimit = PLAYLIST_TRACKS_INITIAL_FETCH_LIMIT;
            playlistTracksCanLoadMore = false;
            return playlistTracksRequestedLimit;
        }

        if (loadMore) {
            int next = Math.min(
                    PLAYLIST_TRACKS_FETCH_MAX_LIMIT,
                    Math.max(PLAYLIST_TRACKS_INITIAL_FETCH_LIMIT, playlistTracksRequestedLimit) + PLAYLIST_TRACKS_FETCH_STEP
            );
            return Math.max(PLAYLIST_TRACKS_INITIAL_FETCH_LIMIT, next);
        }

        return Math.max(PLAYLIST_TRACKS_INITIAL_FETCH_LIMIT, playlistTracksRequestedLimit);
    }

    private void maybeUpdateOfflineReadyState() {
        if (!isAdded()) {
            return;
        }

        final String playlistIdSnapshot = currentPlaylistId;
        final List<PlaylistTrack> tracksSnapshot = new ArrayList<>(currentTracks);
        final Context appContext = requireContext().getApplicationContext();
        final long generation = offlineReadyStateGeneration.incrementAndGet();

        offlineReadyStateExecutor.execute(() -> {
            OfflineReadyStateResult result = computeOfflineReadyState(appContext, tracksSnapshot);
            miniProgressHandler.post(() -> {
                if (!isAdded()
                        || generation != offlineReadyStateGeneration.get()
                        || !TextUtils.equals(playlistIdSnapshot, currentPlaylistId)) {
                    return;
                }

                persistOfflineCompleteStateForCurrentPlaylist(result.complete);
                if (!isOfflineStatusPinned()) {
                    headerTracksState = result.headerState;
                    notifyHeaderChanged();
                }
            });
        });
    }

    @NonNull
    private OfflineReadyStateResult computeOfflineReadyState(
            @NonNull Context appContext,
            @NonNull List<PlaylistTrack> tracksSnapshot
    ) {
        if (tracksSnapshot.isEmpty()) {
            return new OfflineReadyStateResult(
                    true,
                    buildStableTracksState(0, 0, 0, 0, false)
            );
        }

        List<String> eligibleIds = new ArrayList<>(tracksSnapshot.size());
        int restrictedCount = 0;
        int offlineCount = 0;

        for (PlaylistTrack track : tracksSnapshot) {
            if (track == null || TextUtils.isEmpty(track.videoId)) {
                continue;
            }

            if (OfflineRestrictionStore.isRestricted(appContext, track.videoId)) {
                restrictedCount++;
                continue;
            }

            eligibleIds.add(track.videoId);
            if (OfflineAudioStore.hasValidatedOfflineAudio(appContext, track.videoId, track.duration)) {
                offlineCount++;
            }
        }

        int totalTracks = tracksSnapshot.size();
        int eligibleCount = eligibleIds.size();
        boolean complete = eligibleCount <= 0 || offlineCount >= eligibleCount;

        CharSequence state = buildStableTracksState(
                totalTracks,
                offlineCount,
                eligibleCount,
                restrictedCount,
                false
        );

        return new OfflineReadyStateResult(complete, state);
    }

    private static final class OfflineReadyStateResult {
        final boolean complete;
        @NonNull
        final CharSequence headerState;

        private OfflineReadyStateResult(boolean complete, @NonNull CharSequence headerState) {
            this.complete = complete;
            this.headerState = headerState;
        }
    }

    @NonNull
    private String buildStableTracksState(int totalTracks, int offlineCount, int offlineTotal, int restrictedCount, boolean cacheReady) {
        int safeTotalTracks = Math.max(0, totalTracks);
        int safeRestricted = Math.max(0, restrictedCount);
        int safeOfflineTotal = Math.max(0, offlineTotal);
        int safeOfflineCount = Math.max(0, Math.min(offlineCount, safeOfflineTotal));

        StringBuilder state = new StringBuilder();
        state.append(safeTotalTracks).append(" canciones");
        if (safeOfflineCount > 0 && safeOfflineTotal > 0) {
            state.append(" • ")
                    .append(safeOfflineCount)
                    .append("/")
                    .append(safeOfflineTotal)
                    .append(" offline");
        }
        state.append(" • ")
                .append(safeRestricted)
                .append(" restringidas");
        return state.toString();
    }

    private boolean isPlaylistCacheReadyForOffline(@NonNull String playlistId, int expectedTracks) {
        if (!isAdded() || TextUtils.isEmpty(playlistId) || expectedTracks <= 0) {
            return false;
        }

        List<PlaylistTrack> cachedTracks = sanitizeTracksForPlaylist(
                playlistId,
                loadCachedTracksInternal(playlistId, true)
        );
        return cachedTracks.size() >= expectedTracks;
    }

    private void persistOfflineCompleteStateForCurrentPlaylist(boolean complete) {
        if (!isAdded() || TextUtils.isEmpty(currentPlaylistId)) {
            return;
        }
        getCachePrefs().edit()
                .putBoolean(PREF_PLAYLIST_OFFLINE_COMPLETE_PREFIX + currentPlaylistId, complete)
                .apply();
    }

    private boolean isPersistedOfflineCompleteStateForCurrentPlaylist() {
        if (!isAdded() || TextUtils.isEmpty(currentPlaylistId)) {
            return false;
        }

        return getCachePrefs().getBoolean(PREF_PLAYLIST_OFFLINE_COMPLETE_PREFIX + currentPlaylistId, false);
    }

    private void startOfflinePlaylistDownload() {
        startOfflinePlaylistDownload(true);
    }

    private void startOfflinePlaylistDownload(boolean userInitiated) {
        if (!isAdded()) {
            return;
        }
        if (currentTracks.isEmpty()) {
            if (userInitiated) {
                
            }
            return;
        }

        ArrayList<String> ids = new ArrayList<>();
        ArrayList<String> titles = new ArrayList<>();
        ArrayList<String> artists = new ArrayList<>();
        ArrayList<String> durations = new ArrayList<>();
        int skippedAlreadyOffline = 0;
        int skippedRestricted = 0;
        int totalWithVideoId = 0;
        for (PlaylistTrack track : currentTracks) {
            if (!TextUtils.isEmpty(track.videoId)) {
                totalWithVideoId++;
                if (isRestrictedTrack(track.videoId)) {
                    skippedRestricted++;
                    continue;
                }
                boolean alreadyOffline = OfflineAudioStore.hasValidatedOfflineAudio(requireContext(), track.videoId, track.duration);
                if (alreadyOffline) {
                    skippedAlreadyOffline++;
                    continue;
                }
                ids.add(track.videoId);
                titles.add(track.title);
                artists.add(track.artist);
                durations.add(track.duration);
            }
        }
        int totalEligibleWithVideoId = Math.max(0, totalWithVideoId - skippedRestricted);
        if (ids.isEmpty()) {
            if (userInitiated) {
                if (totalEligibleWithVideoId <= 0 && skippedRestricted > 0) {
                    
                } else if (totalEligibleWithVideoId > 0 && skippedAlreadyOffline >= totalEligibleWithVideoId) {
                    
                } else {
                    
                }
            }
            return;
        }

        String uniqueName = OFFLINE_DOWNLOAD_QUEUE_UNIQUE_NAME;
        Data input = new Data.Builder()
                .putString(OfflinePlaylistDownloadWorker.INPUT_PLAYLIST_ID, currentPlaylistId)
                .putString(OfflinePlaylistDownloadWorker.INPUT_PLAYLIST_TITLE, currentPlaylistTitle)
                .putStringArray(OfflinePlaylistDownloadWorker.INPUT_VIDEO_IDS, ids.toArray(new String[0]))
                .putStringArray(OfflinePlaylistDownloadWorker.INPUT_TITLES, titles.toArray(new String[0]))
                .putStringArray(OfflinePlaylistDownloadWorker.INPUT_ARTISTS, artists.toArray(new String[0]))
                .putStringArray(OfflinePlaylistDownloadWorker.INPUT_DURATIONS, durations.toArray(new String[0]))
                .putInt(OfflinePlaylistDownloadWorker.INPUT_ALREADY_OFFLINE_COUNT, skippedAlreadyOffline)
                .putInt(OfflinePlaylistDownloadWorker.INPUT_TOTAL_WITH_VIDEO_ID, totalEligibleWithVideoId)
            .putBoolean(OfflinePlaylistDownloadWorker.INPUT_USER_INITIATED, userInitiated)
            .putBoolean(OfflinePlaylistDownloadWorker.INPUT_MANUAL_QUEUE, false)
                .build();

        Log.d(TAG_OFFLINE_DOWNLOAD,
            "enqueue uniqueName=" + uniqueName
                + " playlistId=" + currentPlaylistId
                + " tracksPending=" + ids.size()
                + " skippedAlreadyOffline=" + skippedAlreadyOffline
                + " skippedRestricted=" + skippedRestricted);

        SharedPreferences prefs = requireContext().getSharedPreferences(CloudSyncManager.PREFS_SETTINGS, Context.MODE_PRIVATE);
        boolean allowMobileData = prefs.getBoolean(CloudSyncManager.KEY_OFFLINE_DOWNLOAD_ALLOW_MOBILE_DATA, false);
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(allowMobileData ? NetworkType.CONNECTED : NetworkType.UNMETERED)
                .build();

        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(OfflinePlaylistDownloadWorker.class)
                .setInputData(input)
                .setConstraints(constraints)
                .addTag(uniqueName)
                .addTag(currentPlaylistOfflineTag())
                .build();

        enqueueOfflineDownloadUniqueWork(uniqueName, request);

        offlineProgressDone = -1;
        offlineProgressDownloaded = -1;
        String optimisticCurrentId = ids.isEmpty() ? "" : ids.get(0);
        setOfflineDownloadVisualState(
            true,
            optimisticCurrentId,
            ids.toArray(new String[0]),
            null
        );
        offlineDownloadQueued = true;
        offlineHeaderTracksState = "Descarga offline en cola...";
        notifyHeaderChanged();
        observeOfflineDownload(uniqueName);
    }

    private void launchOfflineImportPicker(@NonNull List<String> videoIds) {
        if (!isAdded() || offlineImportLauncher == null) {
            return;
        }

        pendingImportTrackIds.clear();
        pendingImportTrackIds.addAll(videoIds);
        headerTracksState = "Selecciona los audios descargados para importarlos offline.";
        notifyHeaderChanged();

        try {
            offlineImportLauncher.launch(new String[] {
                    "audio/mpeg",
                    "audio/mp4",
                    "audio/*",
                    "application/octet-stream"
            });
        } catch (Exception e) {
            pendingImportTrackIds.clear();
            
        }
    }

    private void handleOfflineImportSelection(@Nullable List<Uri> uris) {
        if (!isAdded()) {
            return;
        }

        if (uris == null || uris.isEmpty()) {
            pendingImportTrackIds.clear();
            
            maybeUpdateOfflineReadyState();
            return;
        }

        if (pendingImportTrackIds.isEmpty()) {
            
            return;
        }

        int imported = 0;
        for (Uri uri : uris) {
            if (uri == null) {
                continue;
            }

            String trackId = resolveTrackIdFromUri(uri);
            if (TextUtils.isEmpty(trackId) || !pendingImportTrackIds.contains(trackId)) {
                continue;
            }

            if (copyUriToOfflineFile(uri, trackId)) {
                imported++;
            }
        }

        pendingImportTrackIds.clear();
        maybeUpdateOfflineReadyState();
        if (trackAdapter != null) {
            trackAdapter.invalidateTrackStateCache();
            trackAdapter.notifyDataSetChanged();
        }

        if (imported > 0) {
            
        } else {
            
        }
    }

    @NonNull
    private ArrayList<String> buildCurrentVideoIds() {
        ArrayList<String> ids = new ArrayList<>();
        for (PlaylistTrack track : currentTracks) {
            if (!TextUtils.isEmpty(track.videoId)) {
                ids.add(track.videoId);
            }
        }
        return ids;
    }

    private boolean isRestrictedTrack(@Nullable String videoId) {
        if (!isAdded() || TextUtils.isEmpty(videoId)) {
            return false;
        }
        return OfflineRestrictionStore.isRestricted(requireContext(), videoId);
    }

    private int countRestrictedTracks(@NonNull List<PlaylistTrack> source) {
        if (!isAdded() || source.isEmpty()) {
            return 0;
        }

        int count = 0;
        HashSet<String> seen = new HashSet<>();
        for (PlaylistTrack track : source) {
            if (track == null || TextUtils.isEmpty(track.videoId)) {
                continue;
            }
            if (!seen.add(track.videoId)) {
                continue;
            }
            if (OfflineRestrictionStore.isRestricted(requireContext(), track.videoId)) {
                count++;
            }
        }
        return count;
    }

    private boolean hasRestrictedTracksInCurrentPlaylist() {
        return countRestrictedTracks(currentTracks) > 0;
    }

    private boolean copyUriToOfflineFile(@NonNull Uri uri, @NonNull String trackId) {
        if (!isAdded()) {
            return false;
        }

        File target = OfflineAudioStore.getOfflineAudioFile(requireContext(), trackId);
        File parent = target.getParentFile();
        if (parent == null) {
            return false;
        }
        if (!parent.exists() && !parent.mkdirs()) {
            return false;
        }

        File temp = new File(target.getAbsolutePath() + ".tmp");
        ContentResolver resolver = requireContext().getContentResolver();
        boolean success = false;

        try (InputStream in = resolver.openInputStream(uri);
             FileOutputStream out = new FileOutputStream(temp, false)) {
            if (in == null) {
                return false;
            }

            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            out.flush();

            if (!temp.isFile() || temp.length() <= 0L) {
                return false;
            }

            if (target.exists() && !target.delete()) {
                return false;
            }

            success = temp.renameTo(target);
            if (success) {
                OfflineAudioStore.markOfflineAudioState(trackId, true);
            }
            return success;
        } catch (Exception ignored) {
            return false;
        } finally {
            if (!success && temp.exists()) {
                temp.delete();
            }
        }
    }

    @NonNull
    private String resolveTrackIdFromUri(@NonNull Uri uri) {
        String displayName = resolveDisplayName(uri);
        if (TextUtils.isEmpty(displayName)) {
            displayName = uri.getLastPathSegment();
        }

        if (TextUtils.isEmpty(displayName)) {
            return "";
        }

        String decoded = Uri.decode(displayName).trim();
        if (decoded.isEmpty()) {
            return "";
        }

        int lastDot = decoded.lastIndexOf('.');
        if (lastDot > 0) {
            decoded = decoded.substring(0, lastDot).trim();
        }

        return decoded;
    }

    @NonNull
    private String resolveDisplayName(@NonNull Uri uri) {
        ContentResolver resolver = requireContext().getContentResolver();
        try (Cursor cursor = resolver.query(uri, new String[] { OpenableColumns.DISPLAY_NAME }, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (idx >= 0) {
                    String name = cursor.getString(idx);
                    return name == null ? "" : name;
                }
            }
        } catch (Exception ignored) {
        }
        return "";
    }

    private void observeOfflineDownload(@NonNull String uniqueName) {
        observeOfflineDownload(uniqueName, true);
    }

    private void observeOfflineDownload(@NonNull String uniqueName, boolean notifyTerminalToasts) {
        if (!isAdded()) {
            return;
        }

        String observeTag = currentPlaylistOfflineTag();

        if (TextUtils.equals(observingOfflineUniqueName, observeTag) && offlineDownloadObserver != null) {
            if (notifyTerminalToasts) {
                offlineObserverNotifyTerminalToasts = true;
            }
            return;
        }

        stopObservingOfflineDownload();
        observingOfflineUniqueName = observeTag;
        offlineObserverNotifyTerminalToasts = notifyTerminalToasts;

        offlineDownloadObserver = workInfos -> {
            if (!isAdded()) {
                return;
            }

            if (workInfos == null || workInfos.isEmpty()) {
                setOfflineDownloadVisualState(false, "");
                offlineDownloadQueued = false;
                offlineHeaderTracksState = "";
                notifyHeaderChanged();
                maybeUpdateOfflineReadyState();
                return;
            }

            List<WorkInfo> runningInfos = new ArrayList<>();
            WorkInfo terminalInfo = null;
            int queuedCount = 0;
            int enqueuedCount = 0;
            int blockedCount = 0;

            for (WorkInfo candidate : workInfos) {
                WorkInfo.State state = candidate.getState();
                if (state == WorkInfo.State.RUNNING) {
                    runningInfos.add(candidate);
                }
                if (state == WorkInfo.State.ENQUEUED) {
                    enqueuedCount++;
                    queuedCount++;
                }
                if (state == WorkInfo.State.BLOCKED) {
                    blockedCount++;
                    queuedCount++;
                }
                if (state == WorkInfo.State.SUCCEEDED
                        || state == WorkInfo.State.FAILED
                        || state == WorkInfo.State.CANCELLED) {
                    terminalInfo = candidate;
                }
            }

            if (!runningInfos.isEmpty()) {
                int done = 0;
                int total = 0;
                int downloaded = 0;
                String currentId = "";
                String dlPlaylistTitle = "";
                Map<String, Float> progressByTrackId = new HashMap<>();

                for (WorkInfo runningInfo : runningInfos) {
                    Data progress = runningInfo.getProgress();
                    done = Math.max(done, progress.getInt(OfflinePlaylistDownloadWorker.PROGRESS_DONE, 0));
                    total = Math.max(total, progress.getInt(OfflinePlaylistDownloadWorker.PROGRESS_TOTAL, 0));
                    downloaded = Math.max(downloaded, progress.getInt(OfflinePlaylistDownloadWorker.PROGRESS_DOWNLOADED, 0));

                    String candidateCurrentId = progress.getString(OfflinePlaylistDownloadWorker.PROGRESS_CURRENT_ID);
                    if (TextUtils.isEmpty(currentId) && !TextUtils.isEmpty(candidateCurrentId)) {
                        currentId = candidateCurrentId.trim();
                    }

                    String candidatePlaylistTitle = progress.getString(OfflinePlaylistDownloadWorker.PROGRESS_PLAYLIST_TITLE);
                    if (TextUtils.isEmpty(dlPlaylistTitle) && !TextUtils.isEmpty(candidatePlaylistTitle)) {
                        dlPlaylistTitle = candidatePlaylistTitle.trim();
                    }

                    String[] activeIds = progress.getStringArray(OfflinePlaylistDownloadWorker.PROGRESS_ACTIVE_IDS);
                    float[] activeFractions = progress.getFloatArray(OfflinePlaylistDownloadWorker.PROGRESS_ACTIVE_FRACTIONS);
                    if (activeIds == null || activeIds.length == 0) {
                        continue;
                    }

                    for (int idx = 0; idx < activeIds.length; idx++) {
                        String activeId = activeIds[idx];
                        if (TextUtils.isEmpty(activeId)) {
                            continue;
                        }
                        String normalizedId = activeId.trim();
                        float value = (activeFractions != null && idx < activeFractions.length)
                                ? activeFractions[idx]
                                : 0f;
                        float normalizedValue = Math.max(0f, Math.min(1f, value));
                        Float previous = progressByTrackId.get(normalizedId);
                        if (previous == null || normalizedValue > previous) {
                            progressByTrackId.put(normalizedId, normalizedValue);
                        }
                    }
                }

                String[] activeIds = progressByTrackId.keySet().toArray(new String[0]);
                if (TextUtils.isEmpty(currentId) && activeIds.length > 0) {
                    currentId = activeIds[0];
                }

                Log.d(TAG_OFFLINE_DOWNLOAD,
                        "state=RUNNING"
                                + " runningCount=" + runningInfos.size()
                                + " done=" + done
                                + " total=" + total
                                + " downloaded=" + downloaded
                                + " dlPlaylistTitle=" + dlPlaylistTitle
                                + " currentId=" + currentId
                                + " activeIds=" + activeIds.length
                                + " queued=" + queuedCount);

                String effectivePlaylistTitle = dlPlaylistTitle == null ? "" : dlPlaylistTitle.trim();
                if (TextUtils.isEmpty(effectivePlaylistTitle)) {
                    effectivePlaylistTitle = currentPlaylistTitle == null ? "" : currentPlaylistTitle.trim();
                }
                if (TextUtils.isEmpty(effectivePlaylistTitle)) {
                    effectivePlaylistTitle = "Playlist";
                }

                int safeTotal = total > 0 ? total : Math.max(0, currentTracks.size());
                int safeDownloaded = Math.max(0, downloaded);
                if (safeTotal > 0) {
                    safeDownloaded = Math.min(safeDownloaded, safeTotal);
                }

                setOfflineDownloadVisualState(true, currentId, activeIds, progressByTrackId);
                offlineDownloadQueued = queuedCount > 0;

                if (!isInternetAvailable()) {
                    setOfflineDownloadVisualState(false, "");
                    offlineHeaderTracksState = "";
                    headerTracksState = "Offline listo: " + safeDownloaded + "/" + safeTotal + " canciones";
                    notifyHeaderChanged();

                    if (offlineProgressDone != done || offlineProgressDownloaded != downloaded) {
                        offlineProgressDone = done;
                        offlineProgressDownloaded = downloaded;
                    }
                    return;
                }

                String playlistNameMarkup = " <b>" + TextUtils.htmlEncode(effectivePlaylistTitle) + "</b>";
                String status = "Descargando playlist" + playlistNameMarkup + " " + safeDownloaded + "/" + safeTotal;
                if (done > safeDownloaded) {
                    status += " • " + done + " revisadas";
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    offlineHeaderTracksState = Html.fromHtml(status, Html.FROM_HTML_MODE_LEGACY);
                } else {
                    offlineHeaderTracksState = Html.fromHtml(status);
                }
                notifyHeaderChanged();

                if (offlineProgressDone != done || offlineProgressDownloaded != downloaded) {
                    offlineProgressDone = done;
                    offlineProgressDownloaded = downloaded;
                }
                return;
            }

            if (queuedCount > 0) {
                if (!offlineDownloadRunning) {
                    String optimisticCurrentId = offlineDownloadingTrackId;
                    if (TextUtils.isEmpty(optimisticCurrentId) && !offlineDownloadingTrackIds.isEmpty()) {
                        optimisticCurrentId = offlineDownloadingTrackIds.iterator().next();
                    }

                    String[] optimisticIds = offlineDownloadingTrackIds.isEmpty()
                            ? (TextUtils.isEmpty(optimisticCurrentId)
                            ? new String[0]
                            : new String[] { optimisticCurrentId })
                            : offlineDownloadingTrackIds.toArray(new String[0]);

                    setOfflineDownloadVisualState(true, optimisticCurrentId, optimisticIds, null);
                }
                offlineDownloadQueued = true;

                if (enqueuedCount == 0 && blockedCount > 0) {
                    Log.w(TAG_OFFLINE_DOWNLOAD,
                            "queue:block_detected blocked=" + blockedCount + " uniqueName=" + uniqueName + " -> resetting");

                    resetBlockedOfflineQueues();

                    offlineDownloadQueued = false;
                    offlineHeaderTracksState = "La cola offline se reinicio por bloqueo. Inicia de nuevo la descarga.";
                    notifyHeaderChanged();
                    return;
                }

                if (!isInternetAvailable()) {
                    int[] counts = resolveOfflineReadyCounts();
                    offlineHeaderTracksState = "";
                    headerTracksState = "Offline listo: " + counts[0] + "/" + counts[1] + " canciones";
                    notifyHeaderChanged();
                    return;
                }

                offlineHeaderTracksState = queuedCount > 1
                        ? "Descarga offline en cola (" + queuedCount + " pendientes)"
                        : "Descarga offline en cola...";
                notifyHeaderChanged();
                return;
            }

            offlineDownloadQueued = false;
            offlineHeaderTracksState = "";

            if (terminalInfo == null) {
                setOfflineDownloadVisualState(false, "");
                notifyHeaderChanged();
                return;
            }

            if (terminalInfo.getState() == WorkInfo.State.SUCCEEDED) {
                setOfflineDownloadVisualState(false, "");
                Data output = terminalInfo.getOutputData();
                int downloadedOut = output.getInt(OfflinePlaylistDownloadWorker.OUTPUT_DOWNLOADED, 0);
                int totalOut = output.getInt(OfflinePlaylistDownloadWorker.OUTPUT_TOTAL, currentTracks.size());
                String reason = output.getString(OfflinePlaylistDownloadWorker.OUTPUT_REASON);

                Log.d(TAG_OFFLINE_DOWNLOAD,
                        "succeeded downloaded=" + downloadedOut + "/" + totalOut + " reason=" + reason);

                if (!offlineObserverNotifyTerminalToasts) {
                    if (trackAdapter != null) {
                        trackAdapter.invalidateTrackStateCache();
                        trackAdapter.notifyDataSetChanged();
                    }
                    offlineProgressDone = -1;
                    offlineProgressDownloaded = -1;
                    stopObservingOfflineDownload();
                    maybeUpdateOfflineReadyState();
                    return;
                }

                headerTracksState = "Offline listo: " + downloadedOut + "/" + totalOut + " canciones";
                notifyHeaderChanged();

                if (trackAdapter != null) {
                    trackAdapter.invalidateTrackStateCache();
                    trackAdapter.notifyDataSetChanged();
                }
                offlineProgressDone = -1;
                offlineProgressDownloaded = -1;
                stopObservingOfflineDownload();
                return;
            }

            if (terminalInfo.getState() == WorkInfo.State.FAILED || terminalInfo.getState() == WorkInfo.State.CANCELLED) {
                Log.e(TAG_OFFLINE_DOWNLOAD, "terminal_state=" + terminalInfo.getState());
                setOfflineDownloadVisualState(false, "");

                if (!offlineObserverNotifyTerminalToasts) {
                    offlineProgressDone = -1;
                    offlineProgressDownloaded = -1;
                    stopObservingOfflineDownload();
                    maybeUpdateOfflineReadyState();
                    return;
                }

                if (!isInternetAvailable()) {
                    int[] counts = resolveOfflineReadyCounts();
                    headerTracksState = "Offline listo: " + counts[0] + "/" + counts[1] + " canciones";
                } else {
                    headerTracksState = "No se pudo completar la descarga offline.";
                }
                notifyHeaderChanged();
                offlineProgressDone = -1;
                offlineProgressDownloaded = -1;
                stopObservingOfflineDownload();
            }
        };

        WorkManager.getInstance(requireContext().getApplicationContext())
            .getWorkInfosByTagLiveData(observeTag)
                .observe(getViewLifecycleOwner(), offlineDownloadObserver);
    }

    private void stopObservingOfflineDownload() {
        if (!isAdded() || offlineDownloadObserver == null || TextUtils.isEmpty(observingOfflineUniqueName)) {
            offlineDownloadObserver = null;
            observingOfflineUniqueName = null;
            offlineObserverNotifyTerminalToasts = false;
            return;
        }

        WorkManager.getInstance(requireContext().getApplicationContext())
            .getWorkInfosByTagLiveData(observingOfflineUniqueName)
                .removeObserver(offlineDownloadObserver);

        offlineDownloadObserver = null;
        observingOfflineUniqueName = null;
        offlineObserverNotifyTerminalToasts = false;
    }

    private void enqueueOfflineDownloadUniqueWork(
            @NonNull String uniqueName,
            @NonNull OneTimeWorkRequest request
    ) {
        WorkManager manager = WorkManager.getInstance(requireContext().getApplicationContext());
        manager.enqueueUniqueWork(uniqueName, ExistingWorkPolicy.APPEND_OR_REPLACE, request);
    }

    private void resetBlockedOfflineQueues() {
        WorkManager manager = WorkManager.getInstance(requireContext().getApplicationContext());
        manager.cancelUniqueWork(OFFLINE_DOWNLOAD_QUEUE_UNIQUE_NAME);
        manager.cancelUniqueWork(OFFLINE_DOWNLOAD_MANUAL_TRACK_QUEUE_UNIQUE_NAME);
        manager.cancelAllWorkByTag(currentPlaylistOfflineTag());
        manager.pruneWork();
    }

    @NonNull
    private int[] resolveOfflineReadyCounts() {
        if (!isAdded() || currentTracks.isEmpty()) {
            return new int[] {0, 0};
        }

        int eligible = 0;
        int downloaded = 0;
        for (PlaylistTrack track : currentTracks) {
            if (track == null || TextUtils.isEmpty(track.videoId)) {
                continue;
            }
            if (isRestrictedTrack(track.videoId)) {
                continue;
            }
            eligible++;
            if (OfflineAudioStore.hasValidatedOfflineAudio(requireContext(), track.videoId, track.duration)) {
                downloaded++;
            }
        }

        if (eligible <= 0) {
            return new int[] {0, 0};
        }
        return new int[] {Math.max(0, Math.min(downloaded, eligible)), eligible};
    }

    @NonNull
    private String resolveYoutubeAccessToken(@Nullable String rawToken) {
        String token = rawToken == null ? "" : rawToken.trim();
        if (!token.isEmpty()) {
            return token;
        }
        if (!isAdded()) {
            return "";
        }

        SharedPreferences prefs = requireContext().getSharedPreferences(PREFS_PLAYER_STATE, Activity.MODE_PRIVATE);
        String persisted = prefs.getString(PREF_LAST_YOUTUBE_ACCESS_TOKEN, "");
        return persisted == null ? "" : persisted.trim();
    }

    private void scheduleTracksTokenRetry(@NonNull String playlistId) {
        if (!isAdded() || pendingTracksTokenRetryRunnable != null) {
            return;
        }

        if (pendingTracksTokenRetry >= MAX_TRACKS_TOKEN_RETRY) {
            return;
        }

        pendingTracksTokenRetry++;
        pendingTracksTokenRetryRunnable = new Runnable() {
            @Override
            public void run() {
                pendingTracksTokenRetryRunnable = null;
                if (!isAdded()) {
                    return;
                }

                String token = resolveYoutubeAccessToken("");
                if (!token.isEmpty()) {
                    pendingTracksTokenRetry = 0;
                    refreshPlaylistMeta(playlistId, token);
                    bindTrackList(playlistId, token);
                    return;
                }

                if (pendingTracksTokenRetry < MAX_TRACKS_TOKEN_RETRY) {
                    scheduleTracksTokenRetry(playlistId);
                    return;
                }

                headerTracksState = "No se pudo cargar esta lista.";
                notifyHeaderChanged();
                revealPlaylistContentIfNeeded(true);
            }
        };

        miniProgressHandler.postDelayed(pendingTracksTokenRetryRunnable, TRACKS_TOKEN_RETRY_DELAY_MS);
    }

    private void cancelPendingTracksTokenRetry() {
        if (pendingTracksTokenRetryRunnable != null) {
            miniProgressHandler.removeCallbacks(pendingTracksTokenRetryRunnable);
            pendingTracksTokenRetryRunnable = null;
        }
        pendingTracksTokenRetry = 0;
    }

    @NonNull
    private List<PlaylistTrack> mapTracks(@NonNull List<YouTubeMusicService.PlaylistTrackResult> tracks) {
        List<PlaylistTrack> mapped = new ArrayList<>();
        for (YouTubeMusicService.PlaylistTrackResult track : tracks) {
            mapped.add(new PlaylistTrack(
                    track.videoId,
                    track.title,
                    track.artist,
                    track.duration,
                    track.thumbnailUrl
            ));
        }
        return mapped;
    }

    @NonNull
    private List<PlaylistTrack> sanitizeTracksForPlaylist(
            @NonNull String playlistId,
            @NonNull List<PlaylistTrack> source
    ) {
        if (isFavoritesPlaylistContext(playlistId) && isAdded()) {
            List<FavoritesPlaylistStore.FavoriteTrack> favorites = FavoritesPlaylistStore.loadFavorites(requireContext());
            List<PlaylistTrack> mapped = new ArrayList<>(favorites.size());
            for (FavoritesPlaylistStore.FavoriteTrack track : favorites) {
                mapped.add(new PlaylistTrack(track.videoId, track.title, track.artist, track.duration, track.imageUrl));
            }
            return mapped;
        }

        if (isCustomPlaylistContext(playlistId) && isAdded()) {
            String name = playlistId.substring(CustomPlaylistsStore.CUSTOM_PLAYLIST_PREFIX.length());
            List<FavoritesPlaylistStore.FavoriteTrack> custom = CustomPlaylistsStore.INSTANCE.getTracksFromPlaylist(requireContext(), name);
            List<PlaylistTrack> mapped = new ArrayList<>(custom.size());
            for (FavoritesPlaylistStore.FavoriteTrack track : custom) {
                mapped.add(new PlaylistTrack(track.videoId, track.title, track.artist, track.duration, track.imageUrl));
            }
            return mapped;
        }

        return new ArrayList<>(source);
    }

    @NonNull
    private String normalizeLikedPlaylistId(
            @NonNull String playlistId,
            @NonNull String playlistTitle,
            @NonNull String playlistSubtitle
    ) {
        if (FavoritesPlaylistStore.PLAYLIST_ID.equals(playlistId)) {
            return playlistId;
        }

        String normalizedTitle = playlistTitle.trim().toLowerCase(Locale.US);
        String normalizedSubtitle = playlistSubtitle.trim().toLowerCase(Locale.US);
        if (YouTubeMusicService.SPECIAL_LIKED_VIDEOS_ID.equals(playlistId)) {
            return playlistId;
        }

        String title = normalizedTitle;
        String subtitle = normalizedSubtitle;
        if (title.contains("gusta")
                || title.contains("liked")
                || title.contains("musica que te gusto")
                || subtitle.contains("gusta")
                || subtitle.contains("liked")
                || subtitle.contains("autogenerada")) {
            return YouTubeMusicService.SPECIAL_LIKED_VIDEOS_ID;
        }

        return playlistId;
    }

    private boolean isLikedPlaylistContext(@NonNull String playlistId) {
        if (YouTubeMusicService.SPECIAL_LIKED_VIDEOS_ID.equals(playlistId)) {
            return true;
        }

        String title = currentPlaylistTitle == null ? "" : currentPlaylistTitle.toLowerCase(Locale.US);
        String subtitle = currentPlaylistSubtitle == null ? "" : currentPlaylistSubtitle.toLowerCase(Locale.US);
        return title.contains("gusta")
                || title.contains("liked")
                || title.contains("musica que te gusto")
                || subtitle.contains("gusta")
                || subtitle.contains("liked")
                || subtitle.contains("autogenerada");
    }

    private boolean isFavoritesPlaylistContext(@NonNull String playlistId) {
        return FavoritesPlaylistStore.PLAYLIST_ID.equals(playlistId);
    }

    private boolean isCustomPlaylistContext(@NonNull String playlistId) {
        return playlistId.startsWith(CustomPlaylistsStore.CUSTOM_PLAYLIST_PREFIX);
    }

    public void externalRefreshFavoritesIfActive() {
        if (!isAdded() || !isFavoritesPlaylistContext(currentPlaylistId)) {
            return;
        }

        List<PlaylistTrack> refreshed = sanitizeTracksForPlaylist(currentPlaylistId, Collections.emptyList());
        renderTracks(refreshed, currentPlaylistId, true);
        replacePlayerQueueWithCurrentOrder();
    }

    private void renderTracks(
            @NonNull List<PlaylistTrack> tracks,
            @NonNull String playlistId,
            boolean fromCache
    ) {
        String selectedVideoId = getCurrentTrackVideoId();
        originalTracks.clear();
        originalTracks.addAll(tracks);

        currentTracks.clear();
        currentTracks.addAll(originalTracks);

        String firstTrackArtwork = "";
        for (PlaylistTrack track : currentTracks) {
            if (track == null || TextUtils.isEmpty(track.imageUrl)) {
                continue;
            }
            firstTrackArtwork = track.imageUrl;
            break;
        }
        if (!TextUtils.isEmpty(firstTrackArtwork)
                && (isFavoritesPlaylistContext(playlistId) || TextUtils.isEmpty(headerPlaylistThumbnail))) {
            headerPlaylistThumbnail = firstTrackArtwork;
        }

        trackAdapter.submitTracks(currentTracks);
        rebuildPlaybackQueue();

        int totalSeconds = 0;
        for (PlaylistTrack track : currentTracks) {
            totalSeconds += parseDurationSeconds(track.duration);
        }
        currentMeta = new PlaylistMeta(
            currentMeta.ownerLabel,
            currentTracks.size(),
            formatTotalDuration(totalSeconds),
            currentMeta.visibilityLabel,
            currentMeta.ageLabel
        );
        headerPlaylistInfo = buildPlaylistInfoLine(currentMeta, currentTracks.size());
        notifyHeaderChanged();
        maybeUpdateOfflineReadyState();

        if (currentTracks.isEmpty()) {
            currentTrackIndex = -1;
            miniPlaying = false;
        } else {
            int mappedIndex = findTrackIndexByVideoId(currentTracks, selectedVideoId);
            if (mappedIndex >= 0) {
                currentTrackIndex = mappedIndex;
            } else {
                int persistedIndex = findTrackIndexByVideoId(currentTracks, loadPersistedVideoIdForCurrentPlaylist());
                if (persistedIndex >= 0) {
                    currentTrackIndex = persistedIndex;
                    miniPlaying = false;
                } else {
                    PlaybackHistoryStore.Snapshot snapshot = loadPlaybackSnapshot();
                    int historyIndex = findTrackIndexFromSnapshot(currentTracks, snapshot);
                    if (historyIndex >= 0) {
                        currentTrackIndex = historyIndex;
                        miniPlaying = false;
                    } else if (currentTrackIndex < 0 || currentTrackIndex >= currentTracks.size()) {
                        currentTrackIndex = -1;
                        miniPlaying = false;
                    }
                }
            }
        }

        if (trackAdapter != null) {
            trackAdapter.setActiveIndex(currentTrackIndex);
        }
        updateShuffleButtonState();
        maybeAutoDownloadForCurrentPlaylist();
        maybeRestoreHiddenPlayerFromSnapshot();
        prefetchTrackArtworkForOffline(currentTracks, headerPlaylistThumbnail);
        if (headerProfilePhoto != null) {
            prefetchImageForOffline(headerProfilePhoto.toString());
        }
        updateMiniPlayerUi();
        if (rvPlaylistContent != null) {
            rvPlaylistContent.post(() -> revealPlaylistContentIfNeeded(true));
        } else {
            revealPlaylistContentIfNeeded(true);
        }
    }

    private void toggleShuffleMode() {
        if (originalTracks.isEmpty() && currentTracks.isEmpty()) {
            return;
        }

        boolean targetEnabled = !shuffleModeEnabled;
        SongPlayerFragment player = findSongPlayerFragment();

        if (player != null && player.isAdded()) {
            ensurePlaybackQueue();
            if (player.externalMatchesQueue(buildCurrentVideoIdQueue())) {
                player.externalSetShuffleEnabled(targetEnabled);
                shuffleModeEnabled = player.externalIsShuffleEnabled();
                syncPlaybackQueueOrderFromPlayer(player);
            } else {
                shuffleModeEnabled = targetEnabled;
                rebuildPlaybackQueue();
                replacePlayerQueueWithCurrentOrder();
            }
            miniPlaying = player.externalIsPlaying();
        } else {
            shuffleModeEnabled = targetEnabled;
            rebuildPlaybackQueue();
        }

        persistShuffleModePreference();
        updateShuffleButtonState();
        updateMiniPlayerUi();
    }

    private void syncShuffleModeFromPlayer() {
        SongPlayerFragment player = findSongPlayerFragment();
        if (player == null || !player.isAdded()) {
            return;
        }

        boolean playerShuffleEnabled = player.externalIsShuffleEnabled();
        boolean changed = shuffleModeEnabled != playerShuffleEnabled;
        shuffleModeEnabled = playerShuffleEnabled;
        if (changed) {
            syncPlaybackQueueOrderFromPlayer(player);
        }
        persistShuffleModePreference();
    }

    private boolean loadPersistedShuffleMode() {
        if (!isAdded()) {
            return false;
        }
        SharedPreferences settings = requireContext()
                .getSharedPreferences(CloudSyncManager.PREFS_SETTINGS, Activity.MODE_PRIVATE);
        return settings.getBoolean(CloudSyncManager.KEY_PLAYER_SHUFFLE_ENABLED, false);
    }

    private void persistShuffleModePreference() {
        if (!isAdded()) {
            return;
        }

        SharedPreferences settings = requireContext()
                .getSharedPreferences(CloudSyncManager.PREFS_SETTINGS, Activity.MODE_PRIVATE);
        boolean currentValue = settings.getBoolean(CloudSyncManager.KEY_PLAYER_SHUFFLE_ENABLED, false);
        if (currentValue == shuffleModeEnabled) {
            return;
        }

        settings.edit()
                .putBoolean(CloudSyncManager.KEY_PLAYER_SHUFFLE_ENABLED, shuffleModeEnabled)
                .apply();
    }

    private void syncPlaybackQueueOrderFromPlayer(@NonNull SongPlayerFragment player) {
        List<String> queueVideoIds = player.externalGetQueueVideoIds();
        if (queueVideoIds.isEmpty()) {
            return;
        }

        List<PlaylistTrack> base = new ArrayList<>(originalTracks.isEmpty() ? currentTracks : originalTracks);
        if (base.isEmpty()) {
            return;
        }

        List<PlaylistTrack> remaining = new ArrayList<>(base);
        List<PlaylistTrack> ordered = new ArrayList<>(base.size());
        for (String videoId : queueVideoIds) {
            int index = findTrackIndexByVideoId(remaining, videoId == null ? "" : videoId);
            if (index < 0) {
                continue;
            }
            ordered.add(remaining.remove(index));
        }

        if (!remaining.isEmpty()) {
            ordered.addAll(remaining);
        }

        if (ordered.isEmpty()) {
            return;
        }

        playbackQueueTracks.clear();
        playbackQueueTracks.addAll(ordered);
    }

    private void replacePlayerQueueWithCurrentOrder() {
        SongPlayerFragment player = findSongPlayerFragment();
        if (player == null || !player.isAdded()) {
            return;
        }

        ensurePlaybackQueue();
        if (playbackQueueTracks.isEmpty()) {
            return;
        }

        boolean keepPlaying = player.externalIsPlaying() || miniPlaying;
        String selectedVideoId = getCurrentTrackVideoId();
        int startIndex = findTrackIndexByVideoId(playbackQueueTracks, selectedVideoId);
        if (startIndex < 0) {
            startIndex = 0;
        }

        ArrayList<String> ids = new ArrayList<>();
        ArrayList<String> titles = new ArrayList<>();
        ArrayList<String> artists = new ArrayList<>();
        ArrayList<String> durations = new ArrayList<>();
        ArrayList<String> images = new ArrayList<>();
        for (PlaylistTrack item : playbackQueueTracks) {
            ids.add(item.videoId);
            titles.add(item.title);
            artists.add(item.artist);
            durations.add(item.duration);
            images.add(item.imageUrl);
        }

        player.externalReplaceQueue(ids, titles, artists, durations, images, startIndex, keepPlaying);
        miniPlaying = keepPlaying;
    }

    private void rebuildPlaybackQueue() {
        List<PlaylistTrack> base = new ArrayList<>(originalTracks.isEmpty() ? currentTracks : originalTracks);
        if (shuffleModeEnabled && base.size() > 1) {
            Collections.shuffle(base, random);
        }
        playbackQueueTracks.clear();
        playbackQueueTracks.addAll(base);
    }

    private void ensurePlaybackQueue() {
        if (playbackQueueTracks.isEmpty() || playbackQueueTracks.size() != currentTracks.size()) {
            rebuildPlaybackQueue();
        }
    }

    private void updateShuffleButtonState() {
        if (!isAdded()) {
            return;
        }
        notifyHeaderChanged();
    }

    @NonNull
    private String getCurrentTrackVideoId() {
        if (currentTrackIndex < 0 || currentTrackIndex >= currentTracks.size()) {
            return "";
        }
        String value = currentTracks.get(currentTrackIndex).videoId;
        return value == null ? "" : value;
    }

    private int findTrackIndexByVideoId(@NonNull List<PlaylistTrack> source, @NonNull String videoId) {
        if (videoId.trim().isEmpty()) {
            return -1;
        }
        for (int i = 0; i < source.size(); i++) {
            if (videoId.equals(source.get(i).videoId)) {
                return i;
            }
        }
        return -1;
    }

    @NonNull
    private PlaybackHistoryStore.Snapshot loadPlaybackSnapshot() {
        if (!isAdded()) {
            return new PlaybackHistoryStore.Snapshot(new ArrayList<>(), 0, 0, 1, false, 0L);
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

    private void invalidateMiniSnapshotCache() {
        miniSnapshotCache = null;
        miniSnapshotCacheReadAtMs = 0L;
    }

    private int findTrackIndexFromSnapshot(
            @NonNull List<PlaylistTrack> source,
            @NonNull PlaybackHistoryStore.Snapshot snapshot
    ) {
        PlaybackHistoryStore.QueueTrack track = snapshot.currentTrack();
        if (track == null || TextUtils.isEmpty(track.videoId)) {
            return -1;
        }
        return findTrackIndexByVideoId(source, track.videoId);
    }

    @NonNull
    private List<PlaylistTrack> loadCachedTracks(@NonNull String playlistId) {
        return loadCachedTracksInternal(playlistId, true);
    }

    @NonNull
    private List<PlaylistTrack> loadCachedTracksInternal(@NonNull String playlistId, boolean allowStale) {
        List<PlaylistTrack> result = new ArrayList<>();
        if (!isAdded() || playlistId.isEmpty()) {
            return result;
        }

        long updatedAt = getCachePrefs().getLong(PREF_TRACKS_UPDATED_AT_PREFIX + playlistId, 0L);
        if (updatedAt <= 0L) {
            return result;
        }
        if (!allowStale && (System.currentTimeMillis() - updatedAt) > TRACKS_CACHE_TTL_MS) {
            return result;
        }

        String raw = getCachePrefs().getString(PREF_TRACKS_DATA_PREFIX + playlistId, "");
        if (TextUtils.isEmpty(raw)) {
            return result;
        }

        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.optJSONObject(i);
                if (obj == null) {
                    continue;
                }
                String videoId = obj.optString("videoId", "").trim();
                String title = obj.optString("title", "").trim();
                String artist = obj.optString("artist", "").trim();
                String duration = obj.optString("duration", "").trim();
                String imageUrl = obj.optString("imageUrl", "").trim();
                if (videoId.isEmpty() || title.isEmpty()) {
                    continue;
                }
                result.add(new PlaylistTrack(videoId, title, artist, duration, imageUrl));
            }
        } catch (Exception ignored) {
            return new ArrayList<>();
        }

        return result;
    }

    private boolean hasCompleteTracksCache(
            @NonNull String playlistId,
            @NonNull List<PlaylistTrack> cachedTracks
    ) {
        if (!isAdded() || playlistId.isEmpty() || cachedTracks.isEmpty()) {
            return false;
        }
        return getCachePrefs().getBoolean(PREF_TRACKS_FULL_CACHE_PREFIX + playlistId, false);
    }

    private boolean isFetchResultComplete(int fetchedCount, int requestedLimit) {
        return fetchedCount >= 0 && fetchedCount < Math.max(1, requestedLimit);
    }

    private void cacheTracks(
            @NonNull String playlistId,
            @NonNull List<PlaylistTrack> tracks,
            boolean cacheComplete
    ) {
        if (!isAdded() || playlistId.isEmpty()) {
            return;
        }
        try {
            JSONArray array = new JSONArray();
            for (PlaylistTrack track : tracks) {
                JSONObject obj = new JSONObject();
                obj.put("videoId", track.videoId);
                obj.put("title", track.title);
                obj.put("artist", track.artist);
                obj.put("duration", track.duration);
                obj.put("imageUrl", track.imageUrl);
                array.put(obj);
            }

            getCachePrefs().edit()
                    .putLong(PREF_TRACKS_UPDATED_AT_PREFIX + playlistId, System.currentTimeMillis())
                    .putBoolean(PREF_TRACKS_FULL_CACHE_PREFIX + playlistId, cacheComplete)
                    .putString(PREF_TRACKS_DATA_PREFIX + playlistId, array.toString())
                    .apply();
        } catch (Exception ignored) {
        }
    }

    @NonNull
    private SharedPreferences getCachePrefs() {
        return requireContext().getSharedPreferences(PREFS_STREAMING_CACHE, Activity.MODE_PRIVATE);
    }

    @Nullable
    private SongPlayerFragment findSongPlayerFragment() {
        Fragment fragment = getParentFragmentManager().findFragmentByTag("song_player");
        if (fragment instanceof SongPlayerFragment) {
            return (SongPlayerFragment) fragment;
        }
        return null;
    }

    public void syncMiniStateFromPlayer(int trackIndex, boolean playing) {
        int safeIndex = -1;
        SongPlayerFragment songPlayer = findSongPlayerFragment();
        if (!currentTracks.isEmpty() && songPlayer != null && songPlayer.isAdded()) {
            String playerVideoId = songPlayer.externalGetCurrentVideoId();
            safeIndex = findTrackIndexByVideoId(currentTracks, playerVideoId);
        }
        if (safeIndex < 0 && trackIndex >= 0 && trackIndex < currentTracks.size()) {
            safeIndex = trackIndex;
        }

        currentTrackIndex = safeIndex;
        miniPlaying = playing;
        if (trackAdapter != null) {
            trackAdapter.setActiveIndex(safeIndex);
        }
        syncShuffleModeFromPlayer();
        updateShuffleButtonState();
        if (getView() == null) {
            return;
        }
        updateMiniPlayerUi();
    }

    private void onTrackSelected(int position) {
        if (position < 0 || position >= currentTracks.size()) {
            return;
        }

        ensurePlaybackQueue();
        if (playbackQueueTracks.isEmpty()) {
            return;
        }

        String selectedVideoId = currentTracks.get(position).videoId;
        int queueIndex = findTrackIndexByVideoId(playbackQueueTracks, selectedVideoId);
        if (queueIndex < 0) {
            queueIndex = 0;
        }

        ArrayList<String> ids = new ArrayList<>();
        ArrayList<String> titles = new ArrayList<>();
        ArrayList<String> artists = new ArrayList<>();
        ArrayList<String> durations = new ArrayList<>();
        ArrayList<String> images = new ArrayList<>();
        for (PlaylistTrack item : playbackQueueTracks) {
            ids.add(item.videoId);
            titles.add(item.title);
            artists.add(item.artist);
            durations.add(item.duration);
            images.add(item.imageUrl);
        }

        SongPlayerFragment existingPlayer = findSongPlayerFragment();
        if (existingPlayer != null && existingPlayer.isAdded()) {
            existingPlayer.externalSetReturnTargetTag(TAG_PLAYLIST_DETAIL);
            if (existingPlayer.externalMatchesQueue(ids)) {
                existingPlayer.externalPlayTrackFromStart(queueIndex);
            } else {
                existingPlayer.externalReplaceQueueFromStart(ids, titles, artists, durations, images, queueIndex, true);
            }

            currentTrackIndex = position;
            miniPlaying = true;
            if (trackAdapter != null) {
                trackAdapter.setActiveIndex(position);
            }
            updateMiniPlayerUi();
            return;
        }

        startHiddenIntegratedPlayerAt(position, true);
    }

    private void onTrackMorePressed(int position, @NonNull View anchor) {
        if (!isAdded() || position < 0 || position >= currentTracks.size()) {
            return;
        }

        anchor.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK);
        anchor.animate().cancel();
        anchor.animate()
                .scaleX(0.88f)
                .scaleY(0.88f)
                .setDuration(70L)
                .withEndAction(() -> anchor.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(120L)
                        .start())
                .start();

        showTrackActionPopup(anchor, position);
    }

    private void showTrackActionPopup(@NonNull View anchor, int position) {
        if (!isAdded() || position < 0 || position >= currentTracks.size()) {
            return;
        }

        dismissTrackActionPopup();

        Context context = requireContext();
        PlaylistTrack selectedTrack = currentTracks.get(position);
        boolean hasOfflineAudio = !TextUtils.isEmpty(selectedTrack.videoId)
            && OfflineAudioStore.hasValidatedOfflineAudio(context, selectedTrack.videoId, selectedTrack.duration);
        int popupWidth = dp(238);
        int popupPadding = dp(6);

        LinearLayout popupRoot = new LinearLayout(context);
        popupRoot.setOrientation(LinearLayout.VERTICAL);
        popupRoot.setPadding(popupPadding, popupPadding, popupPadding, popupPadding);

        GradientDrawable popupBackground = new GradientDrawable();
        popupBackground.setColor(ContextCompat.getColor(context, R.color.surface_low));
        popupBackground.setCornerRadius(dp(14));
        popupRoot.setBackground(popupBackground);

        PopupWindow popupWindow = new PopupWindow(
                popupRoot,
                popupWidth,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                true
        );
        popupWindow.setOutsideTouchable(true);
        popupWindow.setFocusable(false);
        popupWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popupWindow.setElevation(dp(8));
        popupWindow.setOnDismissListener(() -> {
            if (trackActionPopupWindow == popupWindow) {
                trackActionPopupWindow = null;
            }
        });

        popupRoot.addView(createTrackActionRow(
            R.drawable.ic_stream_play_next,
                "Reproducir a continuación",
                () -> queueTrackAsNext(position),
                popupWindow
        ));
        popupRoot.addView(createTrackActionRow(
            R.drawable.ic_stream_queue_add,
                "Agregar a la cola",
                () -> queueTrackAtEnd(position),
                popupWindow
        ));
        popupRoot.addView(createTrackActionRow(
            R.drawable.ic_favorite_star,
                "Agregar a Favoritos",
                () -> addTrackToFavoritesFromRow(position),
                popupWindow
        ));
        popupRoot.addView(createTrackActionRow(
                hasOfflineAudio ? android.R.drawable.ic_menu_delete : R.drawable.ic_download_bold,
                hasOfflineAudio ? "Eliminar descarga" : "Descargar",
                () -> {
                    if (hasOfflineAudio) {
                        removeTrackDownloadFromRow(position);
                    } else {
                        downloadTrackFromRow(position);
                    }
                },
                popupWindow
        ));

        trackActionPopupWindow = popupWindow;

        int xOffset = -popupWidth + anchor.getWidth();
        int yOffset = dp(8);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            popupWindow.showAsDropDown(anchor, xOffset, yOffset, Gravity.START);
        } else {
            popupWindow.showAsDropDown(anchor, xOffset, yOffset);
        }
    }

    @NonNull
        private View createTrackActionRow(
            @DrawableRes int iconRes,
            @NonNull String label,
            @NonNull Runnable action,
            @NonNull PopupWindow popupWindow
    ) {
        Context context = requireContext();

        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(10), dp(12), dp(10));

        ImageView icon = new ImageView(context);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(18), dp(18));
        icon.setLayoutParams(iconParams);
        icon.setImageResource(iconRes);
        icon.setColorFilter(ContextCompat.getColor(context, android.R.color.white));

        TextView labelView = new TextView(context);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        );
        labelParams.leftMargin = dp(10);
        labelView.setLayoutParams(labelParams);
        labelView.setText(label);
        labelView.setSingleLine(false);
        labelView.setTextSize(13f);
        labelView.setTypeface(Typeface.DEFAULT_BOLD);
        labelView.setTextColor(ContextCompat.getColor(context, R.color.text_secondary));

        row.addView(icon);
        row.addView(labelView);

        GradientDrawable rowContent = new GradientDrawable();
        rowContent.setCornerRadius(dp(10));
        rowContent.setColor(Color.TRANSPARENT);

        int rippleColor = withAlpha(ContextCompat.getColor(context, R.color.stitch_blue_light), 0.32f);
        RippleDrawable rowRipple = new RippleDrawable(ColorStateList.valueOf(rippleColor), rowContent, null);
        row.setBackground(rowRipple);

        row.setOnClickListener(v -> {
            action.run();
            popupWindow.dismiss();
        });

        return row;
    }

    private int withAlpha(int color, float alpha) {
        float clamped = Math.max(0f, Math.min(1f, alpha));
        int alphaInt = Math.round(255f * clamped);
        return (color & 0x00FFFFFF) | (alphaInt << 24);
    }

    private int dp(int value) {
        if (!isAdded()) {
            return value;
        }
        return Math.round(requireContext().getResources().getDisplayMetrics().density * value);
    }

    private void dismissTrackActionPopup() {
        if (trackActionPopupWindow != null) {
            trackActionPopupWindow.dismiss();
            trackActionPopupWindow = null;
        }
    }

    private void queueTrackAsNext(int position) {
        if (!isAdded() || position < 0 || position >= currentTracks.size()) {
            return;
        }

        ensurePlaybackQueue();
        if (playbackQueueTracks.isEmpty()) {
            
            return;
        }

        PlaylistTrack selected = currentTracks.get(position);
        if (TextUtils.isEmpty(selected.videoId)) {
            
            return;
        }

        int currentQueueIndex = resolveCurrentQueueIndex();
        int existingIndex = findTrackIndexByVideoId(playbackQueueTracks, selected.videoId);
        if (existingIndex == currentQueueIndex && currentQueueIndex >= 0) {
            
            return;
        }

        PlaylistTrack movingTrack = existingIndex >= 0
                ? playbackQueueTracks.remove(existingIndex)
                : selected;

        if (existingIndex >= 0 && currentQueueIndex >= 0 && existingIndex < currentQueueIndex) {
            currentQueueIndex--;
        }

        int insertIndex = Math.max(0, Math.min(currentQueueIndex + 1, playbackQueueTracks.size()));
        playbackQueueTracks.add(insertIndex, movingTrack);

        syncPlayerQueueWithPlaybackOrder();
        
    }

    private void queueTrackAtEnd(int position) {
        if (!isAdded() || position < 0 || position >= currentTracks.size()) {
            return;
        }

        ensurePlaybackQueue();
        if (playbackQueueTracks.isEmpty()) {
            
            return;
        }

        PlaylistTrack selected = currentTracks.get(position);
        if (TextUtils.isEmpty(selected.videoId)) {
            
            return;
        }

        int currentQueueIndex = resolveCurrentQueueIndex();
        int existingIndex = findTrackIndexByVideoId(playbackQueueTracks, selected.videoId);
        if (existingIndex == currentQueueIndex && currentQueueIndex >= 0) {
            
            return;
        }

        if (existingIndex == playbackQueueTracks.size() - 1 && existingIndex >= 0) {
            
            return;
        }

        PlaylistTrack movingTrack = existingIndex >= 0
                ? playbackQueueTracks.remove(existingIndex)
                : selected;

        playbackQueueTracks.add(movingTrack);

        syncPlayerQueueWithPlaybackOrder();
        
    }

    private void addTrackToFavoritesFromRow(int position) {
        if (!isAdded() || position < 0 || position >= currentTracks.size()) {
            return;
        }

        PlaylistTrack selected = currentTracks.get(position);
        if (TextUtils.isEmpty(selected.videoId)) {
            return;
        }

        FavoritesPlaylistStore.upsertFavorite(
                requireContext(),
                selected.videoId,
                selected.title,
                selected.artist,
                selected.duration,
                selected.imageUrl
        );
    }

    private int resolveCurrentQueueIndex() {
        ensurePlaybackQueue();
        SongPlayerFragment player = findSongPlayerFragment();
        if (player != null && player.isAdded()) {
            int playerQueueIndex = findTrackIndexByVideoId(playbackQueueTracks, player.externalGetCurrentVideoId());
            if (playerQueueIndex >= 0) {
                return playerQueueIndex;
            }
        }

        int localIndex = findTrackIndexByVideoId(playbackQueueTracks, getCurrentTrackVideoId());
        if (localIndex >= 0) {
            return localIndex;
        }

        return -1;
    }

    private void syncPlayerQueueWithPlaybackOrder() {
        SongPlayerFragment player = findSongPlayerFragment();
        if (player == null || !player.isAdded()) {
            return;
        }

        ensurePlaybackQueue();
        if (playbackQueueTracks.isEmpty()) {
            return;
        }

        String selectedVideoId = player.externalGetCurrentVideoId();
        if (TextUtils.isEmpty(selectedVideoId)) {
            selectedVideoId = getCurrentTrackVideoId();
        }

        int selectedIndex = findTrackIndexByVideoId(playbackQueueTracks, selectedVideoId);
        if (selectedIndex < 0) {
            selectedIndex = 0;
        }

        ArrayList<String> ids = new ArrayList<>();
        ArrayList<String> titles = new ArrayList<>();
        ArrayList<String> artists = new ArrayList<>();
        ArrayList<String> durations = new ArrayList<>();
        ArrayList<String> images = new ArrayList<>();
        for (PlaylistTrack item : playbackQueueTracks) {
            ids.add(item.videoId);
            titles.add(item.title);
            artists.add(item.artist);
            durations.add(item.duration);
            images.add(item.imageUrl);
        }

        boolean keepPlaying = player.externalIsPlaying();
        player.externalReplaceQueue(ids, titles, artists, durations, images, selectedIndex, keepPlaying);
        miniPlaying = keepPlaying;
        updateMiniPlayerUi();
    }

    private void downloadTrackFromRow(int position) {
        if (!isAdded() || position < 0 || position >= currentTracks.size()) {
            return;
        }

        PlaylistTrack track = currentTracks.get(position);
        if (TextUtils.isEmpty(track.videoId)) {
            
            return;
        }

        if (isRestrictedTrack(track.videoId)) {
            return;
        }

        if (OfflineAudioStore.hasValidatedOfflineAudio(requireContext(), track.videoId, track.duration)) {
            
            return;
        }

        String uniqueName = OFFLINE_DOWNLOAD_MANUAL_TRACK_QUEUE_UNIQUE_NAME;
        Data input = new Data.Builder()
                .putString(OfflinePlaylistDownloadWorker.INPUT_PLAYLIST_ID, currentPlaylistId)
                .putString(OfflinePlaylistDownloadWorker.INPUT_PLAYLIST_TITLE, currentPlaylistTitle)
                .putStringArray(OfflinePlaylistDownloadWorker.INPUT_VIDEO_IDS, new String[] { track.videoId })
                .putStringArray(OfflinePlaylistDownloadWorker.INPUT_TITLES, new String[] { track.title })
                .putStringArray(OfflinePlaylistDownloadWorker.INPUT_ARTISTS, new String[] { track.artist })
            .putStringArray(OfflinePlaylistDownloadWorker.INPUT_DURATIONS, new String[] { track.duration })
                .putInt(OfflinePlaylistDownloadWorker.INPUT_ALREADY_OFFLINE_COUNT, 0)
                .putInt(OfflinePlaylistDownloadWorker.INPUT_TOTAL_WITH_VIDEO_ID, 1)
            .putBoolean(OfflinePlaylistDownloadWorker.INPUT_USER_INITIATED, true)
            .putBoolean(OfflinePlaylistDownloadWorker.INPUT_MANUAL_QUEUE, true)
                .build();

        SharedPreferences prefs = requireContext().getSharedPreferences(CloudSyncManager.PREFS_SETTINGS, Context.MODE_PRIVATE);
        boolean allowMobileData = prefs.getBoolean(CloudSyncManager.KEY_OFFLINE_DOWNLOAD_ALLOW_MOBILE_DATA, false);
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(allowMobileData ? NetworkType.CONNECTED : NetworkType.UNMETERED)
                .build();

        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(OfflinePlaylistDownloadWorker.class)
                .setInputData(input)
                .setConstraints(constraints)
                .addTag(uniqueName)
                .addTag(currentPlaylistOfflineTag())
                .build();

        enqueueOfflineDownloadUniqueWork(uniqueName, request);

        offlineProgressDone = -1;
        offlineProgressDownloaded = -1;
        setOfflineDownloadVisualState(
            true,
            track.videoId,
            new String[] { track.videoId },
            null
        );
        offlineDownloadQueued = true;
        offlineHeaderTracksState = "Descarga offline en cola...";
        notifyHeaderChanged();
        observeOfflineDownload(uniqueName);

    }

    private void removeTrackDownloadFromRow(int position) {
        if (!isAdded() || position < 0 || position >= currentTracks.size()) {
            return;
        }

        PlaylistTrack track = currentTracks.get(position);
        if (TextUtils.isEmpty(track.videoId)) {
            return;
        }

        Context appContext = requireContext().getApplicationContext();
        ArrayList<String> idsToDelete = new ArrayList<>(1);
        idsToDelete.add(track.videoId);
        OfflineAudioStore.deleteOfflineAudio(appContext, idsToDelete);

        if (trackAdapter != null) {
            trackAdapter.invalidateTrackStateCache(track.videoId);
            trackAdapter.notifyItemChanged(position);
        }

        maybeUpdateOfflineReadyState();
        maybeAutoDownloadForCurrentPlaylist();
    }

    private void playAllInOrder() {
        if (currentTracks.isEmpty()) {
            
            return;
        }

        if (shuffleModeEnabled) {
            ensurePlaybackQueue();
            if (!playbackQueueTracks.isEmpty()) {
                String firstQueueVideoId = playbackQueueTracks.get(0).videoId;
                int displayIndex = findTrackIndexByVideoId(currentTracks, firstQueueVideoId);
                openIntegratedPlayerAt(displayIndex >= 0 ? displayIndex : 0, true);
                return;
            }
        }

        openIntegratedPlayerAt(0, true);
    }

    private void openPlayerFromMiniBar() {
        SongPlayerFragment existingPlayer = findSongPlayerFragment();
        if (existingPlayer != null && existingPlayer.isAdded()) {
            existingPlayer.externalSetReturnTargetTag(TAG_PLAYLIST_DETAIL);
            getParentFragmentManager()
                    .beginTransaction()
                    .setReorderingAllowed(true)
                    .hide(this)
                    .show(existingPlayer)
                    .commit();
            return;
        }

        if (currentTrackIndex >= 0 && currentTrackIndex < currentTracks.size()) {
            openIntegratedPlayerAt(currentTrackIndex, false);
            return;
        }

        PlaybackHistoryStore.Snapshot snapshot = loadPlaybackSnapshot();
        if (openPlayerFromSnapshot(snapshot, snapshot.isPlaying)) {
            return;
        }

        boolean persistedPlaying = getPlayerStatePrefs().getBoolean(PREF_LAST_IS_PLAYING, false);
        if (launchPlayerFromLastTrackPrefs(persistedPlaying, true)) {
            return;
        }

        
    }

    private void openIntegratedPlayerAt(int position, boolean startFromBeginning) {
        if (position < 0 || position >= currentTracks.size()) {
            return;
        }

        ensurePlaybackQueue();
        if (playbackQueueTracks.isEmpty()) {
            return;
        }

        String selectedVideoId = currentTracks.get(position).videoId;
        int queueIndex = findTrackIndexByVideoId(playbackQueueTracks, selectedVideoId);
        if (queueIndex < 0) {
            queueIndex = 0;
        }
        final int effectiveQueueIndex = queueIndex;

        ArrayList<String> ids = new ArrayList<>();
        ArrayList<String> titles = new ArrayList<>();
        ArrayList<String> artists = new ArrayList<>();
        ArrayList<String> durations = new ArrayList<>();
        ArrayList<String> images = new ArrayList<>();
        for (PlaylistTrack item : playbackQueueTracks) {
            ids.add(item.videoId);
            titles.add(item.title);
            artists.add(item.artist);
            durations.add(item.duration);
            images.add(item.imageUrl);
        }

        SongPlayerFragment existingPlayer = findSongPlayerFragment();
        if (existingPlayer != null && existingPlayer.isAdded()) {
            existingPlayer.externalSetReturnTargetTag(TAG_PLAYLIST_DETAIL);
            if (existingPlayer.externalMatchesQueue(ids)) {
                if (startFromBeginning) {
                    existingPlayer.externalPlayTrackFromStart(queueIndex);
                } else {
                    existingPlayer.externalPlayTrack(queueIndex);
                }
            } else {
                if (startFromBeginning) {
                    existingPlayer.externalReplaceQueueFromStart(ids, titles, artists, durations, images, queueIndex, true);
                } else {
                    existingPlayer.externalReplaceQueue(ids, titles, artists, durations, images, queueIndex, true);
                }
            }

            getParentFragmentManager()
                    .beginTransaction()
                    .setReorderingAllowed(true)
                    .hide(this)
                    .show(existingPlayer)
                    .commit();

            currentTrackIndex = position;
            miniPlaying = true;
            if (trackAdapter != null) {
                trackAdapter.setActiveIndex(position);
            }
            updateMiniPlayerUi();
            return;
        }

        currentTrackIndex = position;
        miniPlaying = true;
        trackAdapter.setActiveIndex(position);
        updateMiniPlayerUi();

        if (startFromBeginning) {
            clearPersistedPositionForVideoId(selectedVideoId);
        }

        SongPlayerFragment playerFragment = SongPlayerFragment.newInstance(
                ids,
                titles,
                artists,
                durations,
                images,
                queueIndex,
                true
        );
        playerFragment.externalSetReturnTargetTag(TAG_PLAYLIST_DETAIL);

        getParentFragmentManager()
                .beginTransaction()
                .setReorderingAllowed(true)
                .hide(this)
                .add(R.id.fragmentContainer, playerFragment, "song_player")
                .commit();
    }

    private boolean openPlayerFromSnapshot(
            @NonNull PlaybackHistoryStore.Snapshot snapshot,
            boolean startPlaying
    ) {
        if (!snapshot.isValid()) {
            return false;
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
        if (ids.isEmpty()) {
            return false;
        }

        int snapshotIndex = Math.max(0, Math.min(snapshot.currentIndex, ids.size() - 1));
        persistSnapshotPositionToPlayerState(snapshot);

        SongPlayerFragment existingPlayer = findSongPlayerFragment();
        if (existingPlayer != null && existingPlayer.isAdded()) {
            existingPlayer.externalSetReturnTargetTag(TAG_PLAYLIST_DETAIL);
            existingPlayer.externalReplaceQueue(ids, titles, artists, durations, images, snapshotIndex, startPlaying);

            getParentFragmentManager()
                    .beginTransaction()
                // custom animations removed
                    .setReorderingAllowed(true)
                    .hide(this)
                    .show(existingPlayer)
                    .commit();
        } else {
            SongPlayerFragment playerFragment = SongPlayerFragment.newInstance(
                    ids,
                    titles,
                    artists,
                    durations,
                    images,
                    snapshotIndex,
                    startPlaying
            );
            playerFragment.externalSetReturnTargetTag(TAG_PLAYLIST_DETAIL);

            getParentFragmentManager()
                    .beginTransaction()
                    // custom animations removed
                    .setReorderingAllowed(true)
                    .hide(this)
                    .add(R.id.fragmentContainer, playerFragment, "song_player")
                    .commit();
        }

        int displayIndex = findTrackIndexFromSnapshot(currentTracks, snapshot);
        currentTrackIndex = displayIndex;
        miniPlaying = startPlaying;
        if (trackAdapter != null) {
            trackAdapter.setActiveIndex(displayIndex);
        }
        updateMiniPlayerUi();
        return true;
    }

    private void maybeRestoreHiddenPlayerFromSnapshot() {
        if (!isAdded() || isHidden() || restoringHiddenPlayerFromSnapshot) {
            return;
        }
        if (currentTracks.isEmpty()) {
            return;
        }

        SongPlayerFragment existingPlayer = findSongPlayerFragment();
        if (existingPlayer != null && existingPlayer.isAdded()) {
            return;
        }

        PlaybackHistoryStore.Snapshot snapshot = loadPlaybackSnapshot();
        if (!snapshot.isValid()) {
            return;
        }

        int displayIndex = findTrackIndexFromSnapshot(currentTracks, snapshot);
        if (displayIndex < 0) {
            return;
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
        if (ids.isEmpty()) {
            return;
        }

        if (getParentFragmentManager().isStateSaved()) {
            return;
        }

        int snapshotIndex = Math.max(0, Math.min(snapshot.currentIndex, ids.size() - 1));
        persistSnapshotPositionToPlayerState(snapshot);

        SongPlayerFragment playerFragment = SongPlayerFragment.newInstance(
                ids,
                titles,
                artists,
                durations,
                images,
                snapshotIndex,
            snapshot.isPlaying
        );
        playerFragment.externalSetReturnTargetTag(TAG_PLAYLIST_DETAIL);

        restoringHiddenPlayerFromSnapshot = true;
        getParentFragmentManager()
                .beginTransaction()
                .setReorderingAllowed(true)
                .add(R.id.fragmentContainer, playerFragment, "song_player")
                .hide(playerFragment)
                .runOnCommit(() -> {
                    restoringHiddenPlayerFromSnapshot = false;
                    updateMiniPlayerUi();
                })
                .commit();

        currentTrackIndex = displayIndex;
        miniPlaying = snapshot.isPlaying;
        if (trackAdapter != null) {
            trackAdapter.setActiveIndex(displayIndex);
        }
    }

    private void persistSnapshotPositionToPlayerState(@NonNull PlaybackHistoryStore.Snapshot snapshot) {
        if (!isAdded()) {
            return;
        }

        PlaybackHistoryStore.QueueTrack current = snapshot.currentTrack();
        if (current == null || TextUtils.isEmpty(current.videoId)) {
            return;
        }

        requireContext()
                .getSharedPreferences(PREFS_PLAYER_STATE, Activity.MODE_PRIVATE)
                .edit()
                .putInt(PREF_PLAYBACK_POS_PREFIX + current.videoId, Math.max(0, snapshot.currentSeconds))
                .apply();
    }

    private void toggleMiniPlayback() {
        SongPlayerFragment songPlayer = findSongPlayerFragment();
        if (songPlayer != null && songPlayer.isAdded()) {
            songPlayer.externalTogglePlayback();
            syncMiniStateFromPlayer(songPlayer.externalGetCurrentIndex(), songPlayer.externalIsPlaying());
            return;
        }

        PlaybackHistoryStore.Snapshot snapshot = loadPlaybackSnapshot();
        if (startHiddenPlayerFromSnapshot(snapshot, true)) {
            return;
        }

        if (currentTrackIndex >= 0 && currentTrackIndex < currentTracks.size()) {
            if (startHiddenIntegratedPlayerAt(currentTrackIndex, false)) {
                return;
            }
        }

        if (launchPlayerFromLastTrackPrefs(true, false)) {
            return;
        }

        
    }

    private boolean launchPlayerFromLastTrackPrefs(boolean startPlaying, boolean showPlayer) {
        if (!isAdded()) {
            return false;
        }

        SharedPreferences prefs = getPlayerStatePrefs();
        String persistedPlaylistId = prefs.getString(PREF_LAST_PLAYLIST_ID, "");
        if (!TextUtils.isEmpty(currentPlaylistId)
                && !TextUtils.equals(currentPlaylistId, persistedPlaylistId)) {
            return false;
        }

        String videoId = prefs.getString(PREF_LAST_VIDEO_ID, "");
        if (TextUtils.isEmpty(videoId)) {
            return false;
        }

        String title = prefs.getString(PREF_LAST_TRACK_TITLE, "");
        String artist = prefs.getString(PREF_LAST_TRACK_ARTIST, "");
        String duration = prefs.getString(PREF_LAST_TRACK_DURATION, "");
        String image = prefs.getString(PREF_LAST_TRACK_IMAGE, "");

        ArrayList<String> ids = new ArrayList<>();
        ArrayList<String> titles = new ArrayList<>();
        ArrayList<String> artists = new ArrayList<>();
        ArrayList<String> durations = new ArrayList<>();
        ArrayList<String> images = new ArrayList<>();
        ids.add(videoId);
        titles.add(TextUtils.isEmpty(title) ? "Ultima reproduccion" : title);
        artists.add(artist == null ? "" : artist);
        durations.add(duration == null ? "" : duration);
        images.add(image == null ? "" : image);

        SongPlayerFragment existingPlayer = findSongPlayerFragment();
        if (existingPlayer != null && existingPlayer.isAdded()) {
            existingPlayer.externalSetReturnTargetTag(TAG_PLAYLIST_DETAIL);
            existingPlayer.externalReplaceQueue(ids, titles, artists, durations, images, 0, startPlaying);

            if (showPlayer) {
                getParentFragmentManager()
                        .beginTransaction()
                        // custom animations removed
                        .setReorderingAllowed(true)
                        .hide(this)
                        .show(existingPlayer)
                        .commit();
            }

            currentTrackIndex = findTrackIndexByVideoId(currentTracks, videoId);
            miniPlaying = startPlaying;
            if (trackAdapter != null) {
                trackAdapter.setActiveIndex(currentTrackIndex);
            }
            updateMiniPlayerUi();
            return true;
        }

        if (getParentFragmentManager().isStateSaved()) {
            return false;
        }

        SongPlayerFragment playerFragment = SongPlayerFragment.newInstance(
                ids,
                titles,
                artists,
                durations,
                images,
                0,
                startPlaying
        );
        playerFragment.externalSetReturnTargetTag(TAG_PLAYLIST_DETAIL);

        androidx.fragment.app.FragmentTransaction transaction = getParentFragmentManager()
                .beginTransaction()
                .setReorderingAllowed(true)
                .add(R.id.fragmentContainer, playerFragment, "song_player");

        if (showPlayer) {
            transaction
                    // custom animations removed
                    .hide(this)
                    .commit();
        } else {
            transaction
                    .hide(playerFragment)
                    .runOnCommit(this::updateMiniPlayerUi)
                    .commit();
        }

        currentTrackIndex = findTrackIndexByVideoId(currentTracks, videoId);
        miniPlaying = startPlaying;
        if (trackAdapter != null) {
            trackAdapter.setActiveIndex(currentTrackIndex);
        }
        updateMiniPlayerUi();
        return true;
    }

    private boolean startHiddenPlayerFromSnapshot(
            @NonNull PlaybackHistoryStore.Snapshot snapshot,
            boolean startPlaying
    ) {
        if (!snapshot.isValid() || !isAdded()) {
            return false;
        }

        SongPlayerFragment existingPlayer = findSongPlayerFragment();
        if (existingPlayer != null && existingPlayer.isAdded()) {
            existingPlayer.externalSetReturnTargetTag(TAG_PLAYLIST_DETAIL);
            if (startPlaying && !existingPlayer.externalIsPlaying()) {
                existingPlayer.externalTogglePlayback();
            }
            updateMiniPlayerUi();
            return true;
        }

        if (getParentFragmentManager().isStateSaved()) {
            return false;
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
        if (ids.isEmpty()) {
            return false;
        }

        int snapshotIndex = Math.max(0, Math.min(snapshot.currentIndex, ids.size() - 1));
        persistSnapshotPositionToPlayerState(snapshot);

        SongPlayerFragment playerFragment = SongPlayerFragment.newInstance(
                ids,
                titles,
                artists,
                durations,
                images,
                snapshotIndex,
            false
        );
        playerFragment.externalSetReturnTargetTag(TAG_PLAYLIST_DETAIL);

        getParentFragmentManager()
                .beginTransaction()
                .setReorderingAllowed(true)
                .add(R.id.fragmentContainer, playerFragment, "song_player")
                .hide(playerFragment)
            .runOnCommit(() -> {
                playerFragment.externalReplaceQueue(
                    ids,
                    titles,
                    artists,
                    durations,
                    images,
                    snapshotIndex,
                    startPlaying
                );
                updateMiniPlayerUi();
            })
                .commit();

        int displayIndex = findTrackIndexFromSnapshot(currentTracks, snapshot);
        currentTrackIndex = displayIndex;
        miniPlaying = startPlaying;
        if (trackAdapter != null) {
            trackAdapter.setActiveIndex(displayIndex);
        }
        updateMiniPlayerUi();
        return true;
    }

    private boolean startHiddenIntegratedPlayerAt(int position, boolean startFromBeginning) {
        if (!isAdded() || position < 0 || position >= currentTracks.size()) {
            return false;
        }

        if (getParentFragmentManager().isStateSaved()) {
            return false;
        }

        ensurePlaybackQueue();
        if (playbackQueueTracks.isEmpty()) {
            return false;
        }

        String selectedVideoId = currentTracks.get(position).videoId;
        int queueIndex = findTrackIndexByVideoId(playbackQueueTracks, selectedVideoId);
        if (queueIndex < 0) {
            queueIndex = 0;
        }
        final int effectiveQueueIndex = queueIndex;

        ArrayList<String> ids = new ArrayList<>();
        ArrayList<String> titles = new ArrayList<>();
        ArrayList<String> artists = new ArrayList<>();
        ArrayList<String> durations = new ArrayList<>();
        ArrayList<String> images = new ArrayList<>();
        for (PlaylistTrack item : playbackQueueTracks) {
            ids.add(item.videoId);
            titles.add(item.title);
            artists.add(item.artist);
            durations.add(item.duration);
            images.add(item.imageUrl);
        }

        if (ids.isEmpty()) {
            return false;
        }

        if (startFromBeginning && !TextUtils.isEmpty(selectedVideoId)) {
            clearPersistedPositionForVideoId(selectedVideoId);
        }

        SongPlayerFragment playerFragment = SongPlayerFragment.newInstance(
                ids,
                titles,
                artists,
                durations,
                images,
                effectiveQueueIndex,
                true
        );
        playerFragment.externalSetReturnTargetTag(TAG_PLAYLIST_DETAIL);

        getParentFragmentManager()
                .beginTransaction()
                .setReorderingAllowed(true)
                .add(R.id.fragmentContainer, playerFragment, "song_player")
                .hide(playerFragment)
                .runOnCommit(this::updateMiniPlayerUi)
                .commit();

        currentTrackIndex = position;
        miniPlaying = true;
        if (trackAdapter != null) {
            trackAdapter.setActiveIndex(position);
        }
        updateMiniPlayerUi();
        return true;
    }

    private void updateMiniPlayerUi() {
        if (!isAdded()
                || getView() == null
                || llMiniPlayer == null
                || tvMiniPlayerTitle == null
                || tvMiniPlayerSubtitle == null
                || ivMiniPlayerArt == null
                || btnMiniPlayPause == null) {
            return;
        }

        SongPlayerFragment songPlayer = findSongPlayerFragment();
        boolean playerAttached = songPlayer != null && songPlayer.isAdded();
        String playerVideoId = "";
        PlaybackHistoryStore.Snapshot snapshot = loadPlaybackSnapshot();
        PlaybackHistoryStore.QueueTrack snapshotTrack = snapshot.currentTrack();

        if (playerAttached && !currentTracks.isEmpty()) {
            playerVideoId = songPlayer.externalGetCurrentVideoId();
            int mappedIndex = findTrackIndexByVideoId(currentTracks, playerVideoId);
            if (mappedIndex >= 0) {
                currentTrackIndex = mappedIndex;
            } else {
                int snapshotMappedIndex = snapshotTrack == null
                        ? -1
                        : findTrackIndexByVideoId(currentTracks, snapshotTrack.videoId);
                currentTrackIndex = snapshotMappedIndex >= 0 ? snapshotMappedIndex : -1;
            }
            miniPlaying = songPlayer.externalIsPlaying();
            if (trackAdapter != null) {
                trackAdapter.setActiveIndex(currentTrackIndex);
            }
        } else if (!playerAttached && snapshotTrack != null) {
            miniPlaying = snapshot.isPlaying;
            int mappedIndex = currentTracks.isEmpty()
                    ? -1
                    : findTrackIndexByVideoId(currentTracks, snapshotTrack.videoId);
            currentTrackIndex = mappedIndex >= 0 ? mappedIndex : -1;
            if (trackAdapter != null) {
                trackAdapter.setActiveIndex(currentTrackIndex);
            }
        }

        PlaylistTrack currentListTrack = null;
        if (currentTrackIndex >= 0 && currentTrackIndex < currentTracks.size()) {
            currentListTrack = currentTracks.get(currentTrackIndex);
        }

        if (currentListTrack == null && snapshotTrack == null && !playerAttached) {
            llMiniPlayer.setVisibility(View.GONE);
            miniPlaying = false;
            return;
        }

        llMiniPlayer.setVisibility(View.VISIBLE);

        String title;
        String subtitle;
        String imageUrl;
        int total;
        int saved;

        if (currentListTrack != null) {
            title = currentListTrack.title;
            subtitle = currentListTrack.artist;
            imageUrl = currentListTrack.imageUrl == null ? "" : currentListTrack.imageUrl.trim();
            total = Math.max(1, parseDurationSeconds(currentListTrack.duration));
            saved = getPersistedPositionSeconds(currentListTrack.videoId, total);

            if (playerAttached && TextUtils.equals(playerVideoId, currentListTrack.videoId)) {
                total = Math.max(1, songPlayer.externalGetTotalSeconds());
                saved = Math.max(0, songPlayer.externalGetCurrentSeconds());
            } else if (snapshotTrack != null && TextUtils.equals(snapshotTrack.videoId, currentListTrack.videoId)) {
                total = Math.max(1, snapshot.totalSeconds);
                saved = Math.max(0, snapshot.currentSeconds);
            }
        } else if (snapshotTrack != null) {
            title = TextUtils.isEmpty(snapshotTrack.title) ? "Última reproducción" : snapshotTrack.title;
            subtitle = snapshotTrack.artist;
            imageUrl = snapshotTrack.imageUrl == null ? "" : snapshotTrack.imageUrl.trim();
            if (playerAttached) {
                total = Math.max(1, songPlayer.externalGetTotalSeconds());
                saved = Math.max(0, songPlayer.externalGetCurrentSeconds());
            } else {
                total = Math.max(1, snapshot.totalSeconds);
                saved = Math.max(0, snapshot.currentSeconds);
            }
        } else if (playerAttached) {
            SharedPreferences prefs = getPlayerStatePrefs();
            String fallbackTitle = prefs.getString(PREF_LAST_TRACK_TITLE, "");
            String fallbackArtist = prefs.getString(PREF_LAST_TRACK_ARTIST, "");
            String fallbackImage = prefs.getString(PREF_LAST_TRACK_IMAGE, "");

            title = TextUtils.isEmpty(fallbackTitle) ? "Reproduciendo" : fallbackTitle;
            subtitle = fallbackArtist == null ? "" : fallbackArtist;
            imageUrl = fallbackImage == null ? "" : fallbackImage.trim();
            total = Math.max(1, songPlayer.externalGetTotalSeconds());
            saved = Math.max(0, songPlayer.externalGetCurrentSeconds());
        } else {
            title = "Selecciona una canción";
            subtitle = "";
            imageUrl = "";
            total = 1;
            saved = 0;
            miniPlaying = false;
        }

        tvMiniPlayerTitle.setText(title);
        tvMiniPlayerSubtitle.setText(subtitle == null ? "" : subtitle);
        btnMiniPlayPause.setImageResource(miniPlaying
                ? R.drawable.ic_mini_pause
                : R.drawable.ic_mini_play);

        if (sbMiniPlayerProgress != null) {
            int clamped = Math.max(0, Math.min(Math.max(1, total), saved));
            int progress = Math.round((clamped / (float) Math.max(1, total)) * 1000f);
            sbMiniPlayerProgress.setProgress(Math.max(0, Math.min(1000, progress)));
        }

        if (currentListTrack != null) {
            persistCurrentPlaybackState(currentListTrack, saved, miniPlaying);
        }

        int artTrackIndex = currentListTrack != null ? currentTrackIndex : -2;
        boolean shouldRefreshArt = artTrackIndex != lastMiniArtTrackIndex
                || !TextUtils.equals(imageUrl, lastMiniArtUrl);
        if (!shouldRefreshArt) {
            return;
        }

        if (!TextUtils.isEmpty(imageUrl)) {
            loadArtworkInto(ivMiniPlayerArt, imageUrl);
        } else {
            if (playerAttached && !TextUtils.isEmpty(lastMiniArtUrl)) {
                return;
            }
            ivMiniPlayerArt.setImageResource(R.drawable.ic_music);
        }
        lastMiniArtTrackIndex = artTrackIndex;
        lastMiniArtUrl = imageUrl;
    }

    @NonNull
    private List<String> buildCurrentVideoIdQueue() {
        ensurePlaybackQueue();
        List<String> ids = new ArrayList<>(playbackQueueTracks.size());
        for (PlaylistTrack track : playbackQueueTracks) {
            ids.add(track.videoId);
        }
        return ids;
    }

    private int getPersistedPositionSeconds(@NonNull String videoId, int maxSeconds) {
        if (!isAdded() || TextUtils.isEmpty(videoId)) {
            return 0;
        }
        int saved = getPlayerStatePrefs().getInt(PREF_PLAYBACK_POS_PREFIX + videoId, 0);
        if (saved < 0) {
            return 0;
        }
        return Math.min(Math.max(maxSeconds - 1, 0), saved);
    }

    @NonNull
    private SharedPreferences getPlayerStatePrefs() {
        return requireContext().getSharedPreferences(PREFS_PLAYER_STATE, Activity.MODE_PRIVATE);
    }

    private void clearPersistedPositionForVideoId(@Nullable String videoId) {
        if (!isAdded() || TextUtils.isEmpty(videoId)) {
            return;
        }
        getPlayerStatePrefs().edit().remove(PREF_PLAYBACK_POS_PREFIX + videoId).apply();
    }

    @NonNull
    private String loadPersistedVideoIdForCurrentPlaylist() {
        if (!isAdded() || TextUtils.isEmpty(currentPlaylistId)) {
            return "";
        }
        SharedPreferences prefs = getPlayerStatePrefs();
        String playlistId = prefs.getString(PREF_LAST_PLAYLIST_ID, "");
        if (!TextUtils.equals(currentPlaylistId, playlistId)) {
            return "";
        }
        String videoId = prefs.getString(PREF_LAST_VIDEO_ID, "");
        return videoId == null ? "" : videoId.trim();
    }

    private void persistCurrentPlaybackState(
            @NonNull PlaylistTrack current,
            int currentSeconds,
            boolean playing
    ) {
        if (!isAdded() || TextUtils.isEmpty(currentPlaylistId) || TextUtils.isEmpty(current.videoId)) {
            return;
        }

        int safeSeconds = Math.max(0, currentSeconds);
        if (TextUtils.equals(lastPersistedVideoId, current.videoId)
                && lastPersistedSecond == safeSeconds
                && lastPersistedPlaying == playing) {
            return;
        }

        lastPersistedVideoId = current.videoId;
        lastPersistedSecond = safeSeconds;
        lastPersistedPlaying = playing;

        getPlayerStatePrefs().edit()
                .putString(PREF_LAST_PLAYLIST_ID, currentPlaylistId)
                .putString(PREF_LAST_PLAYLIST_TITLE, currentPlaylistTitle)
                .putString(PREF_LAST_PLAYLIST_SUBTITLE, currentPlaylistSubtitle)
                .putString(PREF_LAST_PLAYLIST_THUMBNAIL, currentPlaylistThumbnail)
                .putString(PREF_LAST_VIDEO_ID, current.videoId)
                .putString(PREF_LAST_TRACK_TITLE, current.title)
                .putString(PREF_LAST_TRACK_ARTIST, current.artist)
                .putString(PREF_LAST_TRACK_IMAGE, current.imageUrl)
                .putString(PREF_LAST_TRACK_DURATION, current.duration)
                .putBoolean(PREF_LAST_IS_PLAYING, playing)
                .putInt(PREF_PLAYBACK_POS_PREFIX + current.videoId, safeSeconds)
                .apply();
    }

    @NonNull
    private PlaylistMeta parseMeta(@NonNull String subtitle) {
        String owner = "Nexus";
        int songs = 0;

        if (!subtitle.isEmpty()) {
            String[] tokens = subtitle.split("•");
            for (String token : tokens) {
                String value = token == null ? "" : token.trim();
                if (value.isEmpty()) {
                    continue;
                }

                String lower = value.toLowerCase(Locale.US);
                if (lower.contains("playlist")) {
                    continue;
                }
                if (lower.contains("song") || lower.contains("cancion")) {
                    songs = parseFirstNumber(value, songs);
                } else {
                    owner = value;
                }
            }
        }

        int totalMinutes = Math.max(0, songs * 3);
        int hours = totalMinutes / 60;
        int minutes = totalMinutes % 60;
        String duration = String.format(Locale.US, "%dh %02dm", hours, minutes);

        String visibility = "";
        String age = "";
        String lowerSubtitle = subtitle.toLowerCase(Locale.US);
        if (lowerSubtitle.contains("no listada") || lowerSubtitle.contains("unlisted")) {
            visibility = "No listada";
        } else if (lowerSubtitle.contains("listada") || lowerSubtitle.contains("public")) {
            visibility = "Listada";
        } else if (lowerSubtitle.contains("privada") || lowerSubtitle.contains("private")) {
            visibility = "Privada";
        }

        return new PlaylistMeta(owner, songs, duration, visibility, age);
    }

    @NonNull
    private String buildVisibilityLabel(@NonNull String privacyStatus) {
        String normalized = privacyStatus.trim().toLowerCase(Locale.US);
        if ("public".equals(normalized)) {
            return "Listada";
        }
        if ("unlisted".equals(normalized)) {
            return "No listada";
        }
        if ("private".equals(normalized)) {
            return "Privada";
        }
        return "";
    }

    @NonNull
    private String buildRelativeDateLabel(@NonNull String publishedAtIso) {
        long publishedAt = parseIsoDateMillis(publishedAtIso);
        if (publishedAt <= 0L) {
            return "";
        }

        long diffMs = Math.max(0L, System.currentTimeMillis() - publishedAt);
        long days = TimeUnit.MILLISECONDS.toDays(diffMs);
        if (days < 1) {
            return "hace hoy";
        }
        if (days < 7) {
            return "hace " + days + " dias";
        }
        long weeks = Math.max(1, days / 7);
        if (weeks < 5) {
            return "hace " + weeks + " sem.";
        }
        long months = Math.max(1, days / 30);
        if (months < 12) {
            return "hace " + months + " mes" + (months == 1 ? "" : "es");
        }
        long years = Math.max(1, days / 365);
        return "hace " + years + " a" + (years == 1 ? "n" : "nos");
    }

    private long parseIsoDateMillis(@NonNull String iso) {
        if (iso.trim().isEmpty()) {
            return -1L;
        }

        String[] patterns = new String[] {
                "yyyy-MM-dd'T'HH:mm:ss'Z'",
                "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"
        };
        for (String pattern : patterns) {
            try {
                SimpleDateFormat format = new SimpleDateFormat(pattern, Locale.US);
                format.setLenient(true);
                Date date = format.parse(iso);
                if (date != null) {
                    return date.getTime();
                }
            } catch (ParseException ignored) {
            }
        }
        return -1L;
    }

    @NonNull
    private String buildPlaylistInfoLine(@NonNull PlaylistMeta meta, int actualTrackCount) {
        List<String> parts = new ArrayList<>();

        int songs = actualTrackCount >= 0 ? actualTrackCount : meta.songsCount;
        if (songs > 0) {
            parts.add(songs + " canciones");
        }
        if (!TextUtils.isEmpty(meta.estimatedDuration)) {
            parts.add(meta.estimatedDuration);
        }
        if (!TextUtils.isEmpty(meta.ageLabel)) {
            parts.add(meta.ageLabel);
        }
        if (!TextUtils.isEmpty(meta.visibilityLabel)) {
            parts.add(meta.visibilityLabel);
        }

        if (parts.isEmpty()) {
            if (isFavoritesPlaylistContext(currentPlaylistId)) {
                return "0 canciones";
            }
            return "Lista";
        }
        return TextUtils.join(" • ", parts);
    }

    private int parseFirstNumber(@NonNull String text, int fallback) {
        String digits = text.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) {
            return fallback;
        }
        try {
            return Integer.parseInt(digits);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private int parseDurationSeconds(@NonNull String duration) {
        if (duration.isEmpty() || duration.contains("--")) {
            return 0;
        }

        String[] parts = duration.split(":");
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
        } catch (NumberFormatException ignored) {
            return 0;
        }
        return 0;
    }

    private boolean isAmoledModeEnabled() {
        if (!isAdded()) {
            return false;
        }
        SharedPreferences settingsPrefs = requireContext()
                .getSharedPreferences(CloudSyncManager.PREFS_SETTINGS, Activity.MODE_PRIVATE);
        return settingsPrefs.getBoolean(CloudSyncManager.KEY_AMOLED_MODE_ENABLED, false);
    }

    private void applyHeaderBackdropVisualState(@NonNull ImageView backdrop, boolean amoledModeEnabled) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            float blurRadius = amoledModeEnabled ? 24f : 44f;
            String blurTag = "header-blur-" + blurRadius;
            Object currentTag = backdrop.getTag();
            if (!(currentTag instanceof String) || !TextUtils.equals((String) currentTag, blurTag)) {
                backdrop.setRenderEffect(RenderEffect.createBlurEffect(blurRadius, blurRadius, Shader.TileMode.CLAMP));
                backdrop.setTag(blurTag);
            }
            backdrop.setAlpha(0.68f);
            if (amoledModeEnabled) {
                backdrop.setColorFilter(0x2EFFFFFF, PorterDuff.Mode.SRC_ATOP);
            } else {
                backdrop.clearColorFilter();
            }
            return;
        }

        backdrop.setTag(null);
        backdrop.setAlpha(0.68f);
        if (amoledModeEnabled) {
            backdrop.setColorFilter(0x2EFFFFFF, PorterDuff.Mode.SRC_ATOP);
        } else {
            backdrop.clearColorFilter();
        }
    }

    @NonNull
    private String formatTotalDuration(int totalSeconds) {
        int hours = totalSeconds / 3600;
        int minutes = (totalSeconds % 3600) / 60;
        if (hours > 0) {
            return String.format(Locale.US, "%dh %02dm", hours, minutes);
        }
        return String.format(Locale.US, "%dm", minutes);
    }

    private final class PlaylistHeaderAdapter extends RecyclerView.Adapter<PlaylistHeaderAdapter.HeaderViewHolder> {

        @NonNull
        @Override
        public HeaderViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_playlist_detail_header, parent, false);
            return new HeaderViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull HeaderViewHolder holder, int position) {
            boolean amoledModeEnabled = headerAmoledModeEnabled;
            holder.tvPlaylistName.setText(headerPlaylistTitle);
            holder.tvGoogleProfileName.setText(headerProfileName);
            holder.tvPlaylistInfo.setText(headerPlaylistInfo);
            holder.tvTracksState.setText(TextUtils.isEmpty(offlineHeaderTracksState) ? headerTracksState : offlineHeaderTracksState);
            holder.llOfflineLoadingIndicator.setVisibility((offlineDownloadRunning || offlineDownloadQueued) ? View.VISIBLE : View.GONE);
            holder.vPlaylistBackdropScrim.setVisibility(amoledModeEnabled ? View.GONE : View.VISIBLE);
            holder.vPlaylistBackdropBottomFade.setVisibility(amoledModeEnabled ? View.GONE : View.VISIBLE);
            holder.vPlaylistBackdropAmoledFade.setVisibility(amoledModeEnabled ? View.VISIBLE : View.GONE);
            boolean offlineAutoEnabled = isCurrentPlaylistOfflineAutoEnabled();
            boolean completeOffline = isPersistedOfflineCompleteStateForCurrentPlaylist();
            if (offlineAutoEnabled) {
            holder.btnDownload.setImageResource(R.drawable.ic_check_small);
            holder.btnDownload.setBackgroundResource(completeOffline
                ? R.drawable.bg_offline_state_filled_primary
                : R.drawable.bg_playlist_action_white);
            holder.btnDownload.setColorFilter(completeOffline
                ? ContextCompat.getColor(requireContext(), R.color.surface_dark)
                : 0xFF000000);
            } else {
            holder.btnDownload.setImageResource(R.drawable.ic_download_bold);
                holder.btnDownload.setBackgroundResource(R.drawable.bg_playlist_action_white);
                holder.btnDownload.setColorFilter(0xFF000000);
            }

            if (!TextUtils.isEmpty(headerPlaylistThumbnail)) {
                loadArtworkInto(holder.ivPlaylistCover, headerPlaylistThumbnail);
                loadArtworkInto(holder.ivPlaylistBackdrop, headerPlaylistThumbnail);
            } else {
                holder.ivPlaylistCover.setImageResource(R.drawable.ic_music);
                holder.ivPlaylistBackdrop.setImageResource(R.drawable.ic_music);
            }

            applyHeaderBackdropVisualState(holder.ivPlaylistBackdrop, amoledModeEnabled);

            if (headerProfilePhoto != null) {
                Glide.with(holder.itemView)
                        .load(headerProfilePhoto)
                        .diskCacheStrategy(DiskCacheStrategy.ALL)
                        .onlyRetrieveFromCache(!isInternetAvailable())
                        .circleCrop()
                        .placeholder(android.R.drawable.ic_menu_myplaces)
                        .error(android.R.drawable.ic_menu_myplaces)
                        .into(holder.ivGoogleProfile);
            } else {
                holder.ivGoogleProfile.setImageResource(android.R.drawable.ic_menu_myplaces);
            }

            int shuffleColor = shuffleModeEnabled
                    ? ContextCompat.getColor(requireContext(), R.color.stitch_blue)
                    : ContextCompat.getColor(requireContext(), android.R.color.black);
            holder.btnShuffle.setColorFilter(shuffleColor);
            holder.btnShuffle.setAlpha(1f);
            holder.vShuffleIndicator.setVisibility(View.GONE);

            holder.btnListenNow.setOnClickListener(v -> playAllInOrder());
            holder.btnShuffle.setOnClickListener(v -> toggleShuffleMode());
            holder.btnDownload.setOnClickListener(v -> onOfflineTogglePressed());
        }

        @Override
        public int getItemCount() {
            return 1;
        }

        final class HeaderViewHolder extends RecyclerView.ViewHolder {
            final ImageView ivPlaylistCover;
            final ImageView ivPlaylistBackdrop;
            final View vPlaylistBackdropScrim;
            final View vPlaylistBackdropBottomFade;
            final View vPlaylistBackdropAmoledFade;
            final ShapeableImageView ivGoogleProfile;
            final TextView tvPlaylistName;
            final TextView tvGoogleProfileName;
            final TextView tvPlaylistInfo;
            final TextView tvTracksState;
            final MaterialButton btnListenNow;
            final ImageButton btnShuffle;
            final View vShuffleIndicator;
            final ImageButton btnDownload;
            final View llOfflineLoadingIndicator;

            HeaderViewHolder(@NonNull View itemView) {
                super(itemView);
                ivPlaylistCover = itemView.findViewById(R.id.ivPlaylistCover);
                ivPlaylistBackdrop = itemView.findViewById(R.id.ivPlaylistBackdrop);
                vPlaylistBackdropScrim = itemView.findViewById(R.id.vPlaylistBackdropScrim);
                vPlaylistBackdropBottomFade = itemView.findViewById(R.id.vPlaylistBackdropBottomFade);
                vPlaylistBackdropAmoledFade = itemView.findViewById(R.id.vPlaylistBackdropAmoledFade);
                ivGoogleProfile = itemView.findViewById(R.id.ivGoogleProfile);
                tvPlaylistName = itemView.findViewById(R.id.tvPlaylistName);
                tvGoogleProfileName = itemView.findViewById(R.id.tvGoogleProfileName);
                tvPlaylistInfo = itemView.findViewById(R.id.tvPlaylistInfo);
                tvTracksState = itemView.findViewById(R.id.tvTracksState);
                btnListenNow = itemView.findViewById(R.id.btnListenNow);
                btnShuffle = itemView.findViewById(R.id.btnShuffle);
                vShuffleIndicator = itemView.findViewById(R.id.vHeaderShuffleIndicator);
                btnDownload = itemView.findViewById(R.id.btnDownload);
                llOfflineLoadingIndicator = itemView.findViewById(R.id.llOfflineLoadingIndicator);
            }
        }
    }

    private void openPlaylistExternal(@NonNull String playlistId) {
        if (YouTubeMusicService.SPECIAL_LIKED_VIDEOS_ID.equals(playlistId)) {
            
            return;
        }

        if (playlistId.isEmpty()) {
            
            return;
        }

        try {
            String url = "https://music.youtube.com/playlist?list=" + Uri.encode(playlistId);
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception ignored) {
            
        }
    }

    private static final class PlaylistMeta {
        final String ownerLabel;
        final int songsCount;
        final String estimatedDuration;
        final String visibilityLabel;
        final String ageLabel;

        PlaylistMeta(
                @NonNull String ownerLabel,
                int songsCount,
                @NonNull String estimatedDuration,
                @NonNull String visibilityLabel,
                @NonNull String ageLabel
        ) {
            this.ownerLabel = ownerLabel;
            this.songsCount = songsCount;
            this.estimatedDuration = estimatedDuration;
            this.visibilityLabel = visibilityLabel;
            this.ageLabel = ageLabel;
        }
    }

    private static final class PlaylistTrack {
        final String videoId;
        final String title;
        final String artist;
        final String duration;
        final String imageUrl;

        PlaylistTrack(
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

    private interface OnTrackTap {
        void onTap(int position);
        void onMoreTap(int position, @NonNull View anchor);
    }

    private final class PlaylistTrackAdapter extends RecyclerView.Adapter<PlaylistTrackAdapter.TrackViewHolder> {
        private final List<PlaylistTrack> items;
        private final OnTrackTap onTrackTap;
        private final Map<String, Boolean> offlineAvailabilityCache = new HashMap<>();
        private final Map<String, Boolean> restrictedTrackCache = new HashMap<>();
        private int activeIndex = -1;
        private boolean offlineDownloadRunning;
        @NonNull
        private final Set<String> downloadingTrackIds = new HashSet<>();
        @NonNull
        private final Map<String, Float> downloadingTrackProgressById = new HashMap<>();

        PlaylistTrackAdapter(@NonNull List<PlaylistTrack> items, @NonNull OnTrackTap onTrackTap) {
            this.items = new ArrayList<>(items);
            this.onTrackTap = onTrackTap;
            setHasStableIds(true);
        }

        void submitTracks(@NonNull List<PlaylistTrack> newItems) {
            final List<PlaylistTrack> previous = new ArrayList<>(items);
            final List<PlaylistTrack> incoming = new ArrayList<>(newItems);

            // Offload DiffUtil to background thread to avoid UI stutter on large lists during playback
            new Thread(() -> {
                final DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(new DiffUtil.Callback() {
                    @Override
                    public int getOldListSize() {
                        return previous.size();
                    }

                    @Override
                    public int getNewListSize() {
                        return incoming.size();
                    }

                    @Override
                    public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
                        PlaylistTrack oldItem = previous.get(oldItemPosition);
                        PlaylistTrack newItem = incoming.get(newItemPosition);
                        return TextUtils.equals(oldItem.videoId, newItem.videoId);
                    }

                    @Override
                    public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                        PlaylistTrack oldItem = previous.get(oldItemPosition);
                        PlaylistTrack newItem = incoming.get(newItemPosition);
                        return TextUtils.equals(oldItem.videoId, newItem.videoId)
                                && TextUtils.equals(oldItem.title, newItem.title)
                                && TextUtils.equals(oldItem.artist, newItem.artist)
                                && TextUtils.equals(oldItem.duration, newItem.duration)
                                && TextUtils.equals(oldItem.imageUrl, newItem.imageUrl);
                    }
                });

                new Handler(Looper.getMainLooper()).post(() -> {
                    if (!isAdded()) return;

                    items.clear();
                    items.addAll(incoming);
                    invalidateTrackStateCache();

                    if (activeIndex >= items.size()) {
                        activeIndex = -1;
                    }

                    diffResult.dispatchUpdatesTo(this);
                });
            }).start();
        }

        void setActiveIndex(int activeIndex) {
            if (this.activeIndex == activeIndex) {
                return;
            }

            int previous = this.activeIndex;
            this.activeIndex = activeIndex;

            if (previous >= 0 && previous < getItemCount()) {
                notifyItemChanged(previous);
            }
            if (activeIndex >= 0 && activeIndex < getItemCount()) {
                notifyItemChanged(activeIndex);
            }
        }

        void setOfflineDownloadState(
                boolean running,
                @NonNull Set<String> currentTrackIds,
                @NonNull Map<String, Float> progressByTrackId
        ) {
            Set<String> normalizedIds = new HashSet<>();
            for (String rawId : currentTrackIds) {
                if (TextUtils.isEmpty(rawId)) {
                    continue;
                }
                normalizedIds.add(rawId.trim());
            }

            Map<String, Float> normalizedProgressById = new HashMap<>();
            if (running) {
                for (Map.Entry<String, Float> entry : progressByTrackId.entrySet()) {
                    if (entry == null || TextUtils.isEmpty(entry.getKey())) {
                        continue;
                    }
                    String id = entry.getKey().trim();
                    float value = entry.getValue() == null ? 0f : entry.getValue();
                    normalizedProgressById.put(id, Math.max(0f, Math.min(1f, value)));
                }
            }

            boolean progressChanged = hasProgressChanged(normalizedIds, normalizedProgressById);

            if (offlineDownloadRunning == running
                    && downloadingTrackIds.equals(normalizedIds)
                    && !progressChanged) {
                return;
            }

            Set<String> previousIds = new HashSet<>(downloadingTrackIds);
            offlineDownloadRunning = running;
            downloadingTrackIds.clear();
            downloadingTrackProgressById.clear();
            if (running) {
                downloadingTrackIds.addAll(normalizedIds);
                downloadingTrackProgressById.putAll(normalizedProgressById);
            }

            for (String previousTrackId : previousIds) {
                invalidateTrackStateCache(previousTrackId);
            }
            for (String currentTrackId : downloadingTrackIds) {
                invalidateTrackStateCache(currentTrackId);
            }

            Set<String> changedIds = new HashSet<>(previousIds);
            changedIds.addAll(downloadingTrackIds);
            if (progressChanged) {
                changedIds.addAll(downloadingTrackIds);
            }
            for (String trackId : changedIds) {
                int index = indexOfTrackById(trackId);
                if (index >= 0 && index < getItemCount()) {
                    notifyItemChanged(index);
                }
            }
        }

        private boolean hasProgressChanged(
                @NonNull Set<String> normalizedIds,
                @NonNull Map<String, Float> normalizedProgressById
        ) {
            for (String id : normalizedIds) {
                float oldValue = progressForTrack(id, downloadingTrackProgressById);
                float newValue = progressForTrack(id, normalizedProgressById);
                if (Math.abs(oldValue - newValue) > 0.001f) {
                    return true;
                }
            }
            return false;
        }

        private float progressForTrack(@Nullable String id, @NonNull Map<String, Float> source) {
            if (TextUtils.isEmpty(id)) {
                return 0f;
            }
            Float value = source.get(id);
            if (value == null) {
                return 0f;
            }
            return Math.max(0f, Math.min(1f, value));
        }

        private int indexOfTrackById(@Nullable String trackId) {
            if (TextUtils.isEmpty(trackId)) {
                return -1;
            }

            for (int i = 0; i < items.size(); i++) {
                if (TextUtils.equals(trackId, items.get(i).videoId)) {
                    return i;
                }
            }
            return -1;
        }

        void invalidateTrackStateCache() {
            offlineAvailabilityCache.clear();
            restrictedTrackCache.clear();
        }

        void invalidateTrackStateCache(@Nullable String trackId) {
            if (TextUtils.isEmpty(trackId)) {
                return;
            }
            String normalized = trackId.trim();
            offlineAvailabilityCache.remove(normalized);
            restrictedTrackCache.remove(normalized);
        }

        private boolean isOfflineAvailable(
                @NonNull Context context,
                @Nullable String trackId,
                @Nullable String expectedDuration
        ) {
            if (TextUtils.isEmpty(trackId)) {
                return false;
            }
            String normalized = trackId.trim();
            Boolean cached = offlineAvailabilityCache.get(normalized);
            if (cached != null) {
                return cached;
            }
            boolean available = OfflineAudioStore.hasValidatedOfflineAudio(context, normalized, expectedDuration);
            offlineAvailabilityCache.put(normalized, available);
            return available;
        }

        private boolean isRestricted(@NonNull Context context, @Nullable String trackId) {
            if (TextUtils.isEmpty(trackId)) {
                return false;
            }
            String normalized = trackId.trim();
            Boolean cached = restrictedTrackCache.get(normalized);
            if (cached != null) {
                return cached;
            }
            boolean restricted = OfflineRestrictionStore.isRestricted(context, normalized);
            restrictedTrackCache.put(normalized, restricted);
            return restricted;
        }

        @Override
        public long getItemId(int position) {
            if (position < 0 || position >= items.size()) {
                return RecyclerView.NO_ID;
            }
            return items.get(position).videoId.hashCode();
        }

        @NonNull
        @Override
        public TrackViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_playlist_track, parent, false);
            RecyclerView.LayoutParams params = (RecyclerView.LayoutParams) view.getLayoutParams();
            int side = Math.round(parent.getResources().getDisplayMetrics().density * 12f);
            params.leftMargin = side;
            params.rightMargin = side;
            view.setLayoutParams(params);
            return new TrackViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull TrackViewHolder holder, int position) {
            PlaylistTrack track = items.get(position);
            holder.tvTrackTitle.setText(track.title);
            String subtitle = track.artist == null ? "" : track.artist.trim();
            String duration = track.duration == null ? "" : track.duration.trim();
            if (!duration.isEmpty()) {
                subtitle = subtitle.isEmpty() ? duration : subtitle + " • " + duration;
            }

                Context context = holder.itemView.getContext();
                boolean isOfflineAvailable = isOfflineAvailable(context, track.videoId, track.duration);
                boolean isRestrictedTrack = isRestricted(context, track.videoId);
            boolean isCurrentlyDownloading = offlineDownloadRunning
                    && !TextUtils.isEmpty(track.videoId)
                        && downloadingTrackIds.contains(track.videoId)
                    && !isOfflineAvailable;

            if (isOfflineAvailable) {
                subtitle = subtitle.isEmpty() ? "Offline" : subtitle + " • Offline";
            } else if (isRestrictedTrack) {
                subtitle = subtitle.isEmpty() ? "Restringida" : subtitle + " • Restringida";
            }
            holder.tvTrackSubtitle.setText(subtitle);

            int primary = ContextCompat.getColor(context, R.color.stitch_blue);
            int surface = ContextCompat.getColor(context, R.color.surface_dark);
            boolean showOfflineState = isOfflineAvailable || isCurrentlyDownloading || isRestrictedTrack;

            holder.ivOfflineState.setVisibility(showOfflineState ? View.VISIBLE : View.INVISIBLE);

            if (isRestrictedTrack) {
                holder.ivOfflineState.setImageResource(R.drawable.ic_restricted_small);
                holder.ivOfflineState.setBackgroundResource(R.drawable.bg_offline_state_filled_alert);
                holder.ivOfflineState.setColorFilter(ContextCompat.getColor(context, android.R.color.white));
            } else if (isOfflineAvailable) {
                holder.ivOfflineState.setImageResource(R.drawable.ic_check_small);
                holder.ivOfflineState.setBackgroundResource(R.drawable.bg_offline_state_filled_primary);
                holder.ivOfflineState.setColorFilter(surface);
            } else if (isCurrentlyDownloading) {
                holder.ivOfflineState.setImageResource(R.drawable.ic_check_small);
                holder.ivOfflineState.setBackgroundResource(R.drawable.bg_offline_state_outline_primary);
                holder.ivOfflineState.setColorFilter(primary);
            }

            if (isCurrentlyDownloading) {
                holder.flOfflineProgress.setVisibility(View.VISIBLE);
                holder.vOfflineProgressFill.setVisibility(View.VISIBLE);
                float target = progressForTrack(track.videoId, downloadingTrackProgressById);
                holder.vOfflineProgressFill.setPivotX(0f);
                holder.vOfflineProgressFill.animate().cancel();
                holder.vOfflineProgressFill.animate().scaleX(target).setDuration(180L).start();
            } else {
                holder.vOfflineProgressFill.animate().cancel();
                holder.vOfflineProgressFill.setScaleX(0f);
                holder.flOfflineProgress.setVisibility(View.GONE);
            }

            if (!TextUtils.isEmpty(track.imageUrl)) {
                loadArtworkInto(holder.ivTrackArt, track.imageUrl, 48);
            } else {
                holder.ivTrackArt.setImageResource(R.drawable.ic_music);
            }

            boolean isActive = position == activeIndex;
            int activeTitleColor = ContextCompat.getColor(holder.itemView.getContext(), R.color.stitch_blue);
            int defaultTitleColor = ContextCompat.getColor(holder.itemView.getContext(), R.color.text_primary);

            holder.rootTrackRow.setBackgroundResource(isActive
                    ? R.drawable.bg_playlist_track_active
                    : R.drawable.bg_playlist_track_default);
            holder.llNowPlayingOverlay.setVisibility(isActive ? View.VISIBLE : View.GONE);
            holder.tvTrackTitle.setTextColor(isActive ? activeTitleColor : defaultTitleColor);

            ViewGroup.LayoutParams layoutParams = holder.itemView.getLayoutParams();
            if (layoutParams instanceof RecyclerView.LayoutParams) {
                RecyclerView.LayoutParams recyclerParams = (RecyclerView.LayoutParams) layoutParams;
                int top = Math.round(holder.itemView.getResources().getDisplayMetrics().density * (position == 0 ? 10f : 0f));
                recyclerParams.topMargin = top;
                holder.itemView.setLayoutParams(recyclerParams);
            }

            holder.itemView.setOnClickListener(v -> {
                int adapterPosition = holder.getAdapterPosition();
                if (adapterPosition == RecyclerView.NO_POSITION) {
                    return;
                }
                onTrackTap.onTap(adapterPosition);
            });

            holder.ivMore.setOnClickListener(v -> {
                int adapterPosition = holder.getAdapterPosition();
                if (adapterPosition == RecyclerView.NO_POSITION) {
                    return;
                }
                onTrackTap.onMoreTap(adapterPosition, holder.ivMore);
            });
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        final class TrackViewHolder extends RecyclerView.ViewHolder {
            final LinearLayout rootTrackRow;
            final ImageView ivTrackArt;
            final LinearLayout llNowPlayingOverlay;
            final TextView tvTrackTitle;
            final TextView tvTrackSubtitle;
            final ImageView ivOfflineState;
            final ImageView ivMore;
            final FrameLayout flOfflineProgress;
            final View vOfflineProgressFill;

            TrackViewHolder(@NonNull View itemView) {
                super(itemView);
                rootTrackRow = itemView.findViewById(R.id.rootTrackRow);
                ivTrackArt = itemView.findViewById(R.id.ivTrackArt);
                llNowPlayingOverlay = itemView.findViewById(R.id.llNowPlayingOverlay);
                tvTrackTitle = itemView.findViewById(R.id.tvTrackTitle);
                tvTrackSubtitle = itemView.findViewById(R.id.tvTrackSubtitle);
                ivOfflineState = itemView.findViewById(R.id.ivOfflineState);
                ivMore = itemView.findViewById(R.id.ivMore);
                flOfflineProgress = itemView.findViewById(R.id.flOfflineProgress);
                vOfflineProgressFill = itemView.findViewById(R.id.vOfflineProgressFill);
            }
        }
    }
}

