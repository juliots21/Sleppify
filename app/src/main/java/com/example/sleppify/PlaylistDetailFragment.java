package com.example.sleppify;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.PorterDuff;
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
import android.widget.RelativeLayout;
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

public class PlaylistDetailFragment extends Fragment
        implements PlaybackEventBus.Listener,
                   RestrictedTrackReplacementSheet.OnReplacementConfirmedListener,
                   RestrictedTrackReplacementSheet.OnReplacementUndoneListener {

    private static final String PREFS_STREAMING_CACHE = "streaming_cache";
    private static final long TRACKS_CACHE_TTL_MS = 24 * 60 * 60 * 1000L;
    private static final String PREF_TRACKS_UPDATED_AT_PREFIX = "playlist_tracks_updated_at_";
    private static final String PREF_TRACKS_DATA_PREFIX = "playlist_tracks_data_";
    private static final String PREF_TRACKS_FULL_CACHE_PREFIX = "playlist_tracks_cache_full_";
    private static final String PREF_PLAYLIST_OFFLINE_COMPLETE_PREFIX = "playlist_offline_complete_";
    private static final String PREF_CACHED_GOOGLE_PROFILE_PHOTO_URL = "cached_google_profile_photo_url";
    private static final String PREF_PLAYLIST_OFFLINE_AUTO_PREFIX = "playlist_offline_auto_";
    private static final String PREFS_PLAYER_STATE = "player_state";
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
    private static final long MINI_PROGRESS_TICK_MS = 200L;
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
    private static final int ARTWORK_PREFETCH_MAX_ITEMS = PLAYLIST_TRACKS_FETCH_MAX_LIMIT;
    private static final int ARTWORK_PREFETCH_BATCH_SIZE = 8;
    private static final long ARTWORK_PREFETCH_BATCH_DELAY_MS = 90L;
    private static final long ARTWORK_PREFETCH_INITIAL_DELAY_MS = 650L;
    private static final long OFFLINE_STATE_LOOKUP_DEBOUNCE_MS = 120L;
    private static final long PLAYLIST_INITIAL_CONTENT_FADE_MS = 220L;

    /** Shared singleton — avoids allocation per bind and ensures consistent Glide cache keys. */
    private static final YouTubeCropTransformation SHARED_YT_CROP = new YouTubeCropTransformation();
    /** Payload marker for state-only adapter updates (skip image reload). */
    private static final String PAYLOAD_STATE_ONLY = "state_only";

    public static final String ARG_PLAYLIST_ID = "arg_playlist_id";
    public static final String ARG_PLAYLIST_TITLE = "arg_playlist_title";
    public static final String ARG_PLAYLIST_SUBTITLE = "arg_playlist_subtitle";
    public static final String ARG_PLAYLIST_THUMBNAIL = "arg_playlist_thumbnail";
    public static final String ARG_YOUTUBE_ACCESS_TOKEN = "arg_youtube_access_token";

    private RecyclerView rvPlaylistContent;
    private SwipeRefreshLayout swipePlaylistRefresh;
    private View playlistLoadingOverlay;
    private ProgressBar pbPlaylistLoading;
    private ImageView ivMiniPlayerArt;
    private TextView tvMiniPlayerTitle;
    private TextView tvMiniPlayerSubtitle;
    private SeekBar sbMiniPlayerProgress;
    private LinearLayout llMiniPlayer;
    private ImageButton btnMiniPlayPause;

    // Playlist toolbar members (back + search + scroll-aware title)
    private View llPlaylistToolbar;
    private ImageView btnPlaylistBack;
    private ImageView btnPlaylistSearch;
    private TextView tvToolbarPlaylistTitle;
    private android.graphics.drawable.ColorDrawable toolbarBgDrawable;

    // TV-specific split player members
    private boolean isTv;
    private View llTvPlayerPane;
    private ImageView ivTvPlayerCover;
    private TextView tvTvPlayerTitle;
    private TextView tvTvPlayerSubtitle;
    private SeekBar sbTvProgress;
    private ImageButton btnTvPlayPause;
    private ImageButton btnTvNext;
    private ImageButton btnTvPrev;
    private ImageView ivTvPlayerBackground;

    private final YouTubeMusicService youTubeMusicService = new YouTubeMusicService();
    private final ExecutorService urlPrefetchExecutor = Executors.newFixedThreadPool(2);
    private final ExecutorService trackStateLookupExecutor = Executors.newFixedThreadPool(2);
    private final List<PlaylistTrack> originalTracks = new ArrayList<>();
    private final List<PlaylistTrack> currentTracks = new ArrayList<>();
    private final List<PlaylistTrack> playbackQueueTracks = new ArrayList<>();
    private PlaylistHeaderAdapter headerAdapter;
    private PlaylistTrackAdapter trackAdapter;
    private long lastScrollStateLoadMs = 0L;
    private int currentTrackIndex = -1;
    private boolean miniPlaying;
    private boolean shuffleModeEnabled;
    private final Random random = new Random();
    private int lastPlaybackQueueSize = -1;
    private boolean lastPlaybackQueueShuffleState = false;
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
    private String lastPersistedPlaylistId = "";
    private boolean lastPersistedPlaying = false;
    @NonNull
    private String headerPlaylistTitle = "Lista";
    @NonNull
    private String headerPlaylistInfo = "Lista";
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
    private final Map<String, Float> offlineTrackProgressFractions = new HashMap<>();
    private boolean restoringHiddenPlayerFromSnapshot;
    @Nullable
    private PopupWindow trackActionPopupWindow;
    private boolean awaitingInitialPlaylistRender = true;
    private int lastMiniArtTrackIndex = -1;
    @NonNull
    private String lastMiniArtUrl = "";
    private int artworkPrefetchGeneration;
    private boolean isScrolling = false;
    private boolean pendingOfflineToggle = false;
    private final Map<String, Long> lastOfflineStateLookupTimeByTrack = new HashMap<>();
    private final Map<String, Long> lastRestrictedStateLookupTimeByTrack = new HashMap<>();
    private SongPlayerFragment cachedSongPlayer = null;
    private long lastCachedSongPlayerTime = 0;
    @Nullable
    private PlaybackHistoryStore.Snapshot miniSnapshotCache;
    private long miniSnapshotCacheReadAtMs;
    private final ExecutorService offlineReadyStateExecutor = Executors.newSingleThreadExecutor();
    private final AtomicLong offlineReadyStateGeneration = new AtomicLong(0L);
    private final Handler miniProgressHandler = new Handler(Looper.getMainLooper());
    private int lastTickerTrackIndex = -1;
    private boolean lastTickerPlaying = false;
    private final Runnable miniProgressTicker = new Runnable() {
        @Override
        public void run() {
            if (!isAdded() || isHidden()) {
                miniProgressHandler.removeCallbacks(this);
                return;
            }

            // Skip update if the full player is covering the screen
            SongPlayerFragment player = findSongPlayerFragment();
            if (player == null || !player.isVisible()) {
                // Only do full UI update when track or play state changed
                boolean trackChanged = currentTrackIndex != lastTickerTrackIndex;
                boolean playStateChanged = miniPlaying != lastTickerPlaying;
                if (trackChanged || playStateChanged) {
                    lastTickerTrackIndex = currentTrackIndex;
                    lastTickerPlaying = miniPlaying;
                    updateMiniPlayerUi();
                    if (trackAdapter != null && currentTrackIndex >= 0) {
                        trackAdapter.setActiveIndex(currentTrackIndex);
                    }
                } else {
                    // Lightweight: only update progress bar and equalizer
                    updateMiniProgressBarOnly(player);
                }
                refreshActiveEqualizerState();
            }

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
        // Hide global header immediately
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).hideTopAppBarForPlaylistDetail();
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

        // Playlist toolbar initialization (back + search + scroll title)
        llPlaylistToolbar = view.findViewById(R.id.llPlaylistToolbar);
        btnPlaylistBack = view.findViewById(R.id.btnPlaylistBack);
        btnPlaylistSearch = view.findViewById(R.id.btnPlaylistSearch);
        tvToolbarPlaylistTitle = view.findViewById(R.id.tvToolbarPlaylistTitle);

        // Transparent background controlled via alpha for scroll effect
        int surfaceColor = androidx.core.content.ContextCompat.getColor(requireContext(), R.color.surface_dark);
        toolbarBgDrawable = new android.graphics.drawable.ColorDrawable(surfaceColor);
        toolbarBgDrawable.setAlpha(0);
        llPlaylistToolbar.setBackground(toolbarBgDrawable);

        if (btnPlaylistBack != null) {
            btnPlaylistBack.setOnClickListener(v -> {
                if (getActivity() != null) getActivity().onBackPressed();
            });
        }
        if (btnPlaylistSearch != null) {
            btnPlaylistSearch.setOnClickListener(v -> launchSearchActivity());
        }

        // TV Player initialization
        llTvPlayerPane = view.findViewById(R.id.llTvPlayerPane);
        ivTvPlayerCover = view.findViewById(R.id.ivTvPlayerCover);
        tvTvPlayerTitle = view.findViewById(R.id.tvTvPlayerTitle);
        tvTvPlayerSubtitle = view.findViewById(R.id.tvTvPlayerSubtitle);
        sbTvProgress = view.findViewById(R.id.sbTvProgress);
        btnTvPlayPause = view.findViewById(R.id.btnTvPlayPause);
        btnTvNext = view.findViewById(R.id.btnTvNext);
        btnTvPrev = view.findViewById(R.id.btnTvPrev);

        isTv = SystemType.INSTANCE.isTv(requireContext());
        if (isTv) {
            if (playlistLoadingOverlay != null) {
                playlistLoadingOverlay.setBackgroundColor(Color.TRANSPARENT);
                playlistLoadingOverlay.setClickable(false);
                playlistLoadingOverlay.setFocusable(false);
            }

            if (btnTvNext != null) {
                btnTvNext.setNextFocusRightId(R.id.rvPlaylistContent);
            }
            if (btnTvPrev != null) {
                btnTvPrev.setNextFocusLeftId(R.id.navigationRail);
            }

            if (llTvPlayerPane != null) {
                llTvPlayerPane.setVisibility(View.VISIBLE);
                llTvPlayerPane.setNextFocusLeftId(R.id.navigationRail);
                
                btnTvPlayPause.setOnClickListener(v -> toggleMiniPlayback());
                btnTvNext.setOnClickListener(v -> {
                    SongPlayerFragment player = findSongPlayerFragment();
                    if (player != null) player.externalSkipNext();
                });
                btnTvPrev.setOnClickListener(v -> {
                    SongPlayerFragment player = findSongPlayerFragment();
                    if (player != null) player.externalSkipPrevious();
                });

                // Apply TV focus highlights
                View.OnFocusChangeListener tvFocusHighlight = (v, hasFocus) -> {
                    if (hasFocus) {
                        v.setBackgroundResource(R.drawable.bg_tv_item_focused);
                    } else {
                        v.setBackgroundResource(0);
                    }
                };
                btnTvPlayPause.setOnFocusChangeListener(tvFocusHighlight);
                btnTvNext.setOnFocusChangeListener(tvFocusHighlight);
                btnTvPrev.setOnFocusChangeListener(tvFocusHighlight);
                if (sbTvProgress != null) sbTvProgress.setOnFocusChangeListener(tvFocusHighlight);

                // TV focus for toolbar elements
                if (btnPlaylistSearch != null) {
                    btnPlaylistSearch.setOnFocusChangeListener(tvFocusHighlight);
                    btnPlaylistSearch.setNextFocusLeftId(R.id.navigationRail);
                }
                if (llMiniPlayer != null) llMiniPlayer.setOnFocusChangeListener(tvFocusHighlight);

                // Apply correct background based on Amoled mode
                if (isAmoledModeEnabled()) {
                    llTvPlayerPane.setBackgroundResource(R.drawable.bg_tv_player_pane_amoled);
                } else {
                    llTvPlayerPane.setBackgroundResource(R.drawable.bg_tv_player_pane);
                }

                ivTvPlayerBackground = view.findViewById(R.id.ivTvPlayerBackground);
                if (ivTvPlayerBackground != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    ivTvPlayerBackground.setRenderEffect(android.graphics.RenderEffect.createBlurEffect(50f, 50f, android.graphics.Shader.TileMode.CLAMP));
                }

                // Fix D-pad focus navigation
                if (rvPlaylistContent != null) {
                    rvPlaylistContent.setNextFocusLeftId(R.id.btnTvPlayPause);
                    // Cuando presionas abajo en la lista, ve al navigation rail (módulos)
                    rvPlaylistContent.setNextFocusDownId(R.id.navigationRail);
                    // Cuando presionas arriba en la lista, ve al toolbar
                    rvPlaylistContent.setNextFocusUpId(R.id.btnPlaylistSearch);
                }
                // Desde el toolbar, al presionar abajo va a la lista
                if (llPlaylistToolbar != null) {
                    llPlaylistToolbar.setNextFocusDownId(R.id.rvPlaylistContent);
                }
                // Desde el mini player, al presionar arriba va a la lista
                if (llMiniPlayer != null) {
                    llMiniPlayer.setNextFocusUpId(R.id.rvPlaylistContent);
                }
                llTvPlayerPane.setNextFocusRightId(R.id.rvPlaylistContent);
                btnTvNext.setNextFocusRightId(R.id.rvPlaylistContent);
                if (sbTvProgress != null) sbTvProgress.setNextFocusRightId(R.id.rvPlaylistContent);
            }
        }

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
        rvPlaylistContent.setItemViewCacheSize(0);
        RecyclerView.RecycledViewPool pool = new RecyclerView.RecycledViewPool();
        pool.setMaxRecycledViews(0, 5);
        rvPlaylistContent.setRecycledViewPool(pool);

        // Single unified scroll listener — reduces per-frame dispatch overhead vs. 3 separate listeners
        rvPlaylistContent.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                if (!isAdded()) return;
                isScrolling = newState != RecyclerView.SCROLL_STATE_IDLE;
                // Load offline/restricted state only when scroll stops
                if (newState == RecyclerView.SCROLL_STATE_IDLE
                        && trackAdapter != null
                        && recyclerView.getLayoutManager() instanceof LinearLayoutManager) {
                    LinearLayoutManager lm = (LinearLayoutManager) recyclerView.getLayoutManager();
                    // ConcatAdapter: header es posición 0, tracks empiezan en 1
                    int firstVisible = lm.findFirstVisibleItemPosition() - 1;
                    int lastVisible = lm.findLastVisibleItemPosition() - 1;
                    if (firstVisible >= 0) {
                        trackAdapter.loadStateForVisibleRange(firstVisible, lastVisible);
                    }
                }
            }

            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                // Throttled offline state trigger during scroll so icons appear
                // as items enter the viewport, not only when scroll stops.
                if (trackAdapter != null) {
                    long now = android.os.SystemClock.uptimeMillis();
                    if (now - lastScrollStateLoadMs > 80L) {
                        lastScrollStateLoadMs = now;
                        RecyclerView.LayoutManager lm2 = recyclerView.getLayoutManager();
                        if (lm2 instanceof LinearLayoutManager) {
                            int f = ((LinearLayoutManager) lm2).findFirstVisibleItemPosition() - 1;
                            int l = ((LinearLayoutManager) lm2).findLastVisibleItemPosition() - 1;
                            if (f >= 0) trackAdapter.loadStateForVisibleRange(f, l);
                        }
                    }
                }

                // Toolbar scroll transition: transparent → solid with title fade
                updateToolbarScrollState(recyclerView);

                if (dy <= 0 || !playlistTracksCanLoadMore || playlistTracksLoadMoreInFlight) return;
                LinearLayoutManager lm = layoutManager;
                int totalItems = lm.getItemCount();
                int lastVisible = lm.findLastVisibleItemPosition();
                if (totalItems <= 0 || lastVisible < 0) return;
                if (totalItems - lastVisible <= PLAYLIST_TRACKS_LOAD_MORE_THRESHOLD) {
                    requestMorePlaylistTracksIfNeeded();
                }
            }
        });

        rvPlaylistContent.setAdapter(new ConcatAdapter(
            headerAdapter,
            trackAdapter
        ));

        playlistTracksRequestedLimit = PLAYLIST_TRACKS_INITIAL_FETCH_LIMIT;
        playlistTracksLoadMoreInFlight = false;
        playlistTracksCanLoadMore = false;

        setupPlaylistPullToRefresh();

        shuffleModeEnabled = loadPersistedShuffleMode();
        syncShuffleModeFromPlayer();

        llMiniPlayer.setOnClickListener(v -> openPlayerFromMiniBar());
        btnMiniPlayPause.setOnClickListener(v -> toggleMiniPlayback());

        final String requestPlaylistId = playlistId;
        final String requestToken = youtubeAccessToken;
        view.post(() -> {
            if (!isAdded() || getView() == null) {
                return;
            }
            // Deferred: these were blocking fragment entry with snapshot loads,
            // fragment transactions, and Glide calls before the overlay was visible
            restoreOfflineDownloadObservation();
            maybeRestoreHiddenPlayerFromSnapshot();
            updateMiniPlayerUi();
            startMiniProgressTicker();
            refreshPlaylistMeta(requestPlaylistId, requestToken);
            bindTrackList(requestPlaylistId, requestToken);
        });
        PlaybackEventBus.addListener(this);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).hideTopAppBarForPlaylistDetail();
        }
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
        lastTickerTrackIndex = -1;
        lastTickerPlaying = false;
        startMiniProgressTicker();
        maybeRestoreHiddenPlayerFromSnapshot();
        syncShuffleModeFromPlayer();
        updateMiniPlayerUi();

        // Defer cache invalidation + re-bind to avoid blocking resume transition
        if (rvPlaylistContent != null) {
            rvPlaylistContent.post(() -> {
                if (!isAdded()) return;
                refreshVisibleTrackRows();
                maybeUpdateOfflineReadyState();
            });
        }
    }

    @Override
    public void onPlaybackSnapshotUpdated() {
        if (isAdded() && getActivity() != null) {
            getActivity().runOnUiThread(this::updateMiniPlayerUi);
        }
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).setContainerOverlayMode(false);
            if (!hidden) {
                ((MainActivity) getActivity()).hideTopAppBarForPlaylistDetail();
            }
        }
        if (hidden) {
            stopMiniProgressTicker();
            return;
        }

        if (llMiniPlayer != null && !isTv) {
            if (llMiniPlayer.getVisibility() == View.VISIBLE && llMiniPlayer.getTranslationY() == 0f) {
                // Already visible and in place (e.g. returning from search) — no animation
            } else {
                float distance = llMiniPlayer.getHeight() > 0 ? llMiniPlayer.getHeight() : 300f;
                llMiniPlayer.setTranslationY(distance);
                llMiniPlayer.setVisibility(View.VISIBLE);
                llMiniPlayer.animate().cancel();
                llMiniPlayer.animate().translationY(0f).setDuration(280).start();
            }
        }

        boolean updatedAmoledMode = isAmoledModeEnabled();
        if (headerAmoledModeEnabled != updatedAmoledMode) {
            headerAmoledModeEnabled = updatedAmoledMode;
            notifyHeaderChanged();
        }
        persistStreamingScreen(STREAM_SCREEN_PLAYLIST_DETAIL);
        startMiniProgressTicker();
        updateMiniPlayerUi();

        // Defer heavy work to avoid competing with the player close animation
        View v = getView();
        if (v != null) {
            v.postDelayed(() -> {
                if (!isAdded() || isHidden()) return;
                restoreOfflineDownloadObservation();
                maybeRestoreHiddenPlayerFromSnapshot();
                syncShuffleModeFromPlayer();
            }, 140);
        } else {
            restoreOfflineDownloadObservation();
            maybeRestoreHiddenPlayerFromSnapshot();
            syncShuffleModeFromPlayer();
        }
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
        cachedSongPlayer = null;
        lastCachedSongPlayerTime = 0;
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).setContainerOverlayMode(false);
        }
        if (rvPlaylistContent != null) {
            try {
                rvPlaylistContent.clearOnScrollListeners();
            } catch (Exception ignored) {}
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
        // Cleanup toolbar views
        llPlaylistToolbar = null;
        btnPlaylistBack = null;
        btnPlaylistSearch = null;
        tvToolbarPlaylistTitle = null;
        toolbarBgDrawable = null;
        PlaybackEventBus.removeListener(this);
        super.onDestroyView();
    }

    @Override
    public void onDestroy() {
        offlineReadyStateExecutor.shutdownNow();
        trackStateLookupExecutor.shutdownNow();
        urlPrefetchExecutor.shutdownNow();
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
        // Scroll listener consolidated into onViewCreated — this method intentionally left empty.
    }

    private void updateToolbarScrollState(@NonNull RecyclerView recyclerView) {
        if (llPlaylistToolbar == null || toolbarBgDrawable == null) return;

        float fraction = 0f;
        // Header is at adapter position 0 in ConcatAdapter
        RecyclerView.ViewHolder headerVH = recyclerView.findViewHolderForAdapterPosition(0);
        if (headerVH != null) {
            View headerView = headerVH.itemView;
            // Find the btnListenNow (play button) — its bottom edge is our transition trigger
            View btnListenNow = headerView.findViewById(R.id.btnListenNow);
            if (btnListenNow != null && btnListenNow.getHeight() > 0) {
                int toolbarHeight = llPlaylistToolbar.getHeight();
                if (toolbarHeight <= 0) toolbarHeight = llPlaylistToolbar.getMeasuredHeight();
                if (toolbarHeight <= 0) {
                    // Toolbar not measured yet — keep fully transparent
                    toolbarBgDrawable.setAlpha(0);
                    if (tvToolbarPlaylistTitle != null) tvToolbarPlaylistTitle.setAlpha(0f);
                    return;
                }
                // Bottom of btnListenNow relative to the RecyclerView top
                int buttonBottom = getDescendantBottom(headerView, btnListenNow);
                // Transition starts when buttonBottom falls below toolbarHeight + transitionZone,
                // and finishes (fully solid) when buttonBottom <= toolbarHeight.
                // Use a shorter zone (60dp) so the transition is snappy.
                float transitionZone = toolbarHeight * 0.7f;
                if (buttonBottom <= toolbarHeight) {
                    fraction = 1f;
                } else if (buttonBottom >= toolbarHeight + transitionZone) {
                    fraction = 0f;
                } else {
                    fraction = 1f - (buttonBottom - toolbarHeight) / transitionZone;
                }
            }
        } else {
            // Header scrolled completely off-screen — fully solid
            fraction = 1f;
        }

        int bgAlpha = Math.round(fraction * 255f);
        toolbarBgDrawable.setAlpha(bgAlpha);
        if (tvToolbarPlaylistTitle != null) {
            tvToolbarPlaylistTitle.setAlpha(fraction);
        }
    }

    private int getDescendantBottom(@NonNull View ancestor, @NonNull View descendant) {
        int offset = descendant.getBottom();
        android.view.ViewParent parent = descendant.getParent();
        while (parent != ancestor && parent instanceof View) {
            offset += ((View) parent).getTop();
            parent = ((View) parent).getParent();
        }
        // Add ancestor's own top (its position within the RecyclerView)
        offset += ancestor.getTop();
        return offset;
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
        // No afectar el minireproductor durante el loading state
        if (playlistLoadingOverlay != null) {
            playlistLoadingOverlay.animate().cancel();
            playlistLoadingOverlay.setAlpha(1f);
            playlistLoadingOverlay.setVisibility(View.VISIBLE);
            // No usar bringToFront() para no afectar al minireproductor
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
                float distance = llMiniPlayer.getHeight() > 0 ? llMiniPlayer.getHeight() : 300f;
                if (llMiniPlayer.getTranslationY() == 0f && llMiniPlayer.getVisibility() != View.VISIBLE) {
                    llMiniPlayer.setTranslationY(distance);
                } else if (llMiniPlayer.getVisibility() == View.VISIBLE) {
                     distance = llMiniPlayer.getTranslationY();
                } else {
                     llMiniPlayer.setTranslationY(distance);
                }
                llMiniPlayer.animate().translationY(0f).setDuration(PLAYLIST_INITIAL_CONTENT_FADE_MS).start();
            } else {
                llMiniPlayer.setTranslationY(0f);
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
        if (headerAdapter != null) {
            headerAdapter.notifyItemChanged(0);
        }
    }

    private void notifyHeaderStateChanged() {
        if (headerAdapter != null) {
            headerAdapter.notifyItemChanged(0, PAYLOAD_STATE_ONLY);
        }
    }

    private void refreshActiveEqualizerState() {
        if (trackAdapter == null || rvPlaylistContent == null || currentTrackIndex < 0) {
            return;
        }
        // The active track's position in the ConcatAdapter = headerAdapter(1) + currentTrackIndex
        int globalPosition = 1 + currentTrackIndex;
        RecyclerView.ViewHolder vh = rvPlaylistContent.findViewHolderForAdapterPosition(globalPosition);
        if (vh == null) {
            return;
        }
        AnimatedEqualizerView eq = vh.itemView.findViewById(R.id.animatedEq);
        if (eq == null) {
            return;
        }
        SongPlayerFragment songPlayer = findSongPlayerFragment();
        boolean isActuallyPlaying = songPlayer != null && songPlayer.isPlaying();
        eq.setAnimating(isActuallyPlaying);
    }

    private void refreshVisibleTrackRows() {
        if (trackAdapter == null) {
            return;
        }

        // Only invalidate cache for visible tracks, not all tracks — avoids re-triggering
        // disk I/O lookups for hundreds of off-screen items.
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

        // Invalidate only visible track state cache, then use PAYLOAD_STATE_ONLY
        // so onBindViewHolder skips image reload and click listener re-setup.
        trackAdapter.invalidateVisibleTrackStateCache(currentTracks, start, end);
        trackAdapter.notifyItemRangeChanged(start, (end - start) + 1, PAYLOAD_STATE_ONLY);
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

        if (trackAdapter != null) {
            trackAdapter.setOfflineDownloadState(offlineDownloadRunning, offlineDownloadingTrackIds, offlineTrackProgressFractions);
        }
    }

    private boolean isOfflineStatusPinned() {
        return offlineDownloadRunning || offlineDownloadQueued;
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

        if (currentTracks.isEmpty()) {
            pendingOfflineToggle = true;
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
        notifyHeaderChanged();

        ArrayList<String> idsToDelete = buildCurrentVideoIds();
        if (idsToDelete.isEmpty()) {
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
                notifyHeaderChanged();
                if (trackAdapter != null) {
                    trackAdapter.invalidateTrackStateCache();
                    trackAdapter.submitTracks(currentTracks);
                }
                maybeUpdateOfflineReadyState();

                // Notify MusicPlayerFragment to update its offline state for this playlist
                notifyMusicPlayerOfflineChanged();
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
        if (tvToolbarPlaylistTitle != null) {
            tvToolbarPlaylistTitle.setText(headerPlaylistTitle);
        }
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

    private void prefetchStreamUrlsForTracks(@NonNull List<PlaylistTrack> tracks, int limit) {
        if (!isAdded() || !isInternetAvailable()) {
            return;
        }

        Context appContext = requireContext().getApplicationContext();
        int count = 0;
        for (PlaylistTrack track : tracks) {
            if (track == null || TextUtils.isEmpty(track.videoId)) {
                continue;
            }
            
            final String videoId = track.videoId;
            final String duration = track.duration;
            urlPrefetchExecutor.submit(() -> {
                try {
                    // Skip if already has offline audio (disk I/O — must be off main thread)
                    if (OfflineAudioStore.hasValidatedOfflineAudio(appContext, videoId, duration)) {
                        return;
                    }
                    // This will cache the URL in InnertubeResolver
                    InnertubeResolver.resolveStreamUrl(appContext, videoId);
                } catch (Exception ignored) {
                }
            });
            
            count++;
            if (count >= limit) {
                break;
            }
        }
    }

    private void scheduleArtworkPrefetchBatch(
            @NonNull List<String> imageUrls,
            int startIndex,
            int generation
    ) {
        if (!isAdded() || generation != artworkPrefetchGeneration || startIndex >= imageUrls.size()) {
            return;
        }

        if (isScrolling) {
            miniProgressHandler.postDelayed(
                () -> scheduleArtworkPrefetchBatch(imageUrls, startIndex, generation),
                150L
            );
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

        // MUST use the same transformation + format + override as loadArtworkInto(50dp)
        // so that Glide's cache key matches and the prefetched bitmap is actually reused
        // when the row becomes visible. Without this, every image was fetched
        // TWICE from YouTube (once for prefetch, once for display).
        Glide.with(this)
                .load(imageUrl)
                .transform(SHARED_YT_CROP)
                .format(DecodeFormat.PREFER_RGB_565)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .skipMemoryCache(false)
                .override(160, 160)
                .priority(com.bumptech.glide.Priority.LOW)
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

    private static long sInternetCheckMs = 0L;
    private static boolean sInternetCheckResult = true;
    private static final long INTERNET_CHECK_TTL_MS = 5_000L;

    private static boolean cachedHasValidatedInternet(@Nullable Context context) {
        long now = System.currentTimeMillis();
        if (now - sInternetCheckMs < INTERNET_CHECK_TTL_MS) {
            return sInternetCheckResult;
        }
        sInternetCheckMs = now;
        sInternetCheckResult = hasValidatedInternet(context);
        return sInternetCheckResult;
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
        loadArtworkInto(target, imageUrl, 0, false);
    }

    private static void loadArtworkInto(@NonNull ImageView target, @Nullable String imageUrl, int fixedSizeDp) {
        loadArtworkInto(target, imageUrl, fixedSizeDp, false);
    }

    private static void loadArtworkInto(@NonNull ImageView target, @Nullable String imageUrl, int fixedSizeDp, boolean highQuality) {
        if (TextUtils.isEmpty(imageUrl)) {
            target.setTag(R.id.tag_artwork_signature, null);
            target.setImageDrawable(null);
            return;
        }

        String safeUrl = imageUrl.trim();
        Context context = target.getContext();
        float density = context.getResources().getDisplayMetrics().density;
        
        int targetWidth;
        int targetHeight;

        if (fixedSizeDp > 0) {
            targetWidth = Math.round(fixedSizeDp * density);
            targetHeight = targetWidth;
        } else {
            ViewGroup.LayoutParams params = target.getLayoutParams();
            int rawWidth = resolveTargetDimension(target.getWidth(), params == null ? 0 : params.width);
            int rawHeight = resolveTargetDimension(target.getHeight(), params == null ? 0 : params.height);
            boolean hasTargetSize = rawWidth > 0 && rawHeight > 0;

            if (hasTargetSize) {
                targetWidth = bucketArtworkDimension(rawWidth);
                targetHeight = bucketArtworkDimension(rawHeight);
            } else {
                targetWidth = Math.round(160 * density); 
                targetHeight = targetWidth;
            }
        }

        // Use a fixed decode size for smart crop so the crop result is always
        // identical between thumbnail and full load — preventing the visible
        // "re-crop flash" that happens when Glide thumbnail loads a smaller
        // bitmap that produces different crop margins.
        // Exception: for small list thumbnails (<=64dp) the smartCrop benefit is negligible
        // and forcing MIN_DECODE_PX_FOR_SMART_CROP (320px) would decode 16x more pixels
        // than needed, causing severe lag on first scroll of large playlists.
        boolean applySmartCropDecode = fixedSizeDp <= 0 || fixedSizeDp > 64;
        if (applySmartCropDecode && YouTubeImageProcessor.shouldProcess(safeUrl)) {
            int side = YouTubeImageProcessor.decodeDimensionForSmartCrop(
                    fixedSizeDp > 0 ? Math.round(fixedSizeDp * density) : targetWidth);
            targetWidth = side;
            targetHeight = side;
        }

        // For small thumbnails, apply the same 160px minimum here so the signature
        // matches the actual Glide .override() used below — preventing duplicate loads.
        if (!highQuality && fixedSizeDp > 0 && fixedSizeDp <= 64) {
            targetWidth = Math.max(targetWidth, 160);
            targetHeight = Math.max(targetHeight, 160);
        }

        String signature = safeUrl + "|" + targetWidth + "x" + targetHeight;
        Object previousSignature = target.getTag(R.id.tag_artwork_signature);
        if (previousSignature instanceof String && signature.equals(previousSignature)) {
            return;
        }
        target.setTag(R.id.tag_artwork_signature, signature);

        boolean offlineOnly = !cachedHasValidatedInternet(context);

        // DO NOT call target.setImageDrawable(null) here — it causes a visible
        // white flash before the new image loads. Glide's crossFade transition
        // handles the swap smoothly from old→new image.
        
        com.bumptech.glide.Priority priority = com.bumptech.glide.Priority.NORMAL;
        if (fixedSizeDp > 0 && fixedSizeDp <= 64) {
            priority = com.bumptech.glide.Priority.HIGH;
        }
        
        // For small list thumbnails (<=64dp), use a lightweight load path.
        // Override minimum 200px so images stay sharp on high-density screens.
        if (!highQuality && fixedSizeDp > 0 && fixedSizeDp <= 64) {
            Glide.with(target)
                .load(safeUrl)
                .transform(SHARED_YT_CROP)
                .format(DecodeFormat.PREFER_RGB_565)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .onlyRetrieveFromCache(offlineOnly)
                .override(targetWidth, targetHeight)
                .transition(com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions.withCrossFade(100))
                .into(target);
        } else {
            Glide.with(target)
                .load(safeUrl)
                .transform(SHARED_YT_CROP)
                .format(highQuality ? DecodeFormat.PREFER_ARGB_8888 : DecodeFormat.PREFER_RGB_565)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .skipMemoryCache(false)
                .priority(priority)
                .onlyRetrieveFromCache(offlineOnly)
                .override(highQuality ? targetWidth : Math.max(targetWidth, 320), 
                         highQuality ? targetHeight : Math.max(targetHeight, 320))
                .transition(com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions.withCrossFade())
                .into(target);
        }
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
        if (playlistId.isEmpty()) {
            playlistTracksLoadMoreInFlight = false;
            playlistTracksCanLoadMore = false;
            if (forceRefresh) {
                setPlaylistPullRefreshState(false);
            }
            return;
        }

        cancelPendingTracksTokenRetry();
        playlistTracksLoadMoreInFlight = true;

        String effectiveAccessToken = resolveYoutubeAccessToken(accessToken);
        int requestedLimit = resolveTrackFetchLimit(forceRefresh, loadMore);

        boolean favoritesContext = isFavoritesPlaylistContext(playlistId);
        boolean customContext = isCustomPlaylistContext(playlistId);

        List<PlaylistTrack> cachedTracks = sanitizeTracksForPlaylist(
                playlistId,
                loadCachedTracks(playlistId)
        );

        if (favoritesContext || customContext) {
            // Clean HTML entities from stored titles/artists
            List<PlaylistTrack> cleaned = new ArrayList<>(cachedTracks.size());
            for (PlaylistTrack track : cachedTracks) {
                String cleanTitle = decodeHtmlEntities(track.title);
                String cleanArtist = decodeHtmlEntities(track.artist);
                cleaned.add(new PlaylistTrack(track.videoId, cleanTitle, cleanArtist, track.duration, track.imageUrl));
            }
            List<PlaylistTrack> cleanedCachedTracks = cleaned;

            renderTracks(cleanedCachedTracks, playlistId, true);
            if (cleanedCachedTracks.isEmpty() && !isOfflineStatusPinned()) {
                notifyHeaderChanged();
            }

            // On pull-to-refresh, try to enrich tracks missing durations
            if (forceRefresh && !cleanedCachedTracks.isEmpty() && !effectiveAccessToken.isEmpty()) {
                List<String> missingDurationIds = new ArrayList<>();
                for (PlaylistTrack track : cleanedCachedTracks) {
                    if (TextUtils.isEmpty(track.duration) || "--:--".equals(track.duration)) {
                        if (!TextUtils.isEmpty(track.videoId)) {
                            missingDurationIds.add(track.videoId);
                        }
                    }
                }

                final List<PlaylistTrack> tracksToEnrich = new ArrayList<>(cleanedCachedTracks);
                final String enrichPlaylistId = playlistId;
                final boolean isFavContext = favoritesContext;

                if (!missingDurationIds.isEmpty()) {
                    youTubeMusicService.fetchVideoDurations(effectiveAccessToken, missingDurationIds, new YouTubeMusicService.VideoDurationCallback() {
                        @Override
                        public void onSuccess(@NonNull Map<String, String> durations) {
                            if (!isAdded()) return;
                            if (!durations.isEmpty()) {
                                List<PlaylistTrack> enriched = new ArrayList<>(tracksToEnrich.size());
                                boolean changed = false;
                                for (PlaylistTrack track : tracksToEnrich) {
                                    String newDuration = durations.get(track.videoId);
                                    if (newDuration != null && !newDuration.isEmpty()) {
                                        enriched.add(new PlaylistTrack(track.videoId, track.title, track.artist, newDuration, track.imageUrl));
                                        changed = true;
                                    } else {
                                        enriched.add(track);
                                    }
                                }
                                if (changed) {
                                    persistEnrichedLocalTracks(enrichPlaylistId, enriched, isFavContext);
                                    renderTracks(enriched, enrichPlaylistId, false);
                                }
                            }
                            setPlaylistPullRefreshState(false);
                        }

                        @Override
                        public void onError(@NonNull String error) {
                            if (!isAdded()) return;
                            setPlaylistPullRefreshState(false);
                        }
                    });
                } else {
                    // No missing durations, but still persist cleaned titles
                    persistEnrichedLocalTracks(enrichPlaylistId, tracksToEnrich, isFavContext);
                    setPlaylistPullRefreshState(false);
                }
            } else if (forceRefresh) {
                setPlaylistPullRefreshState(false);
            }

            playlistTracksLoadMoreInFlight = false;
            playlistTracksCanLoadMore = false;
            return;
        }

        boolean canRequestRemote = !playlistId.isEmpty() && !effectiveAccessToken.isEmpty();
        boolean hasCompleteCache = hasCompleteTracksCache(playlistId, cachedTracks);

        if (!cachedTracks.isEmpty()) {
            // On force refresh, skip rendering cached data when we know a network
            // fetch will immediately follow and replace it. This avoids a wasted
            // renderTracks → DiffUtil → prefetch cycle that gets discarded ~1s later.
            if (!forceRefresh) {
                renderTracks(cachedTracks, playlistId, true);
            }
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

                List<PlaylistTrack> raw = mergeTrackMetadataFromCache(playlistId, mapTracks(tracks));
                cacheTracks(playlistId, raw, isFetchResultComplete(tracks.size(), requestedLimit));
                List<PlaylistTrack> mapped = sanitizeTracksForPlaylist(playlistId, raw);
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
            boolean complete = computeOfflineReadyState(appContext, tracksSnapshot);
            miniProgressHandler.post(() -> {
                if (!isAdded()
                        || generation != offlineReadyStateGeneration.get()
                        || !TextUtils.equals(playlistIdSnapshot, currentPlaylistId)) {
                    return;
                }

                persistOfflineCompleteStateForCurrentPlaylist(complete);
                notifyHeaderStateChanged();
            });
        });
    }

    private boolean computeOfflineReadyState(
            @NonNull Context appContext,
            @NonNull List<PlaylistTrack> tracksSnapshot
    ) {
        if (tracksSnapshot.isEmpty()) {
            return true;
        }

        // ✅ Batch cache restrictions and offline availability instead of per-track I/O
        // Extract all videoIds for batch queries
        List<String> videoIds = new ArrayList<>(tracksSnapshot.size());
        for (PlaylistTrack track : tracksSnapshot) {
            if (track != null && !TextUtils.isEmpty(track.videoId)) {
                videoIds.add(track.videoId);
            }
        }

        // ✅ Get all restricted tracks in one operation (cache batch result)
        Set<String> restrictedIds = new HashSet<>();
        if (!videoIds.isEmpty()) {
            // Use batch query if available, otherwise fall back to individual checks
            // This is still better because we're doing lookups on a local set in-memory
            for (String videoId : videoIds) {
                if (OfflineRestrictionStore.isRestricted(appContext, videoId)) {
                    restrictedIds.add(videoId);
                }
            }
        }

        int eligibleCount = 0;
        int offlineCount = 0;

        for (PlaylistTrack track : tracksSnapshot) {
            if (track == null || TextUtils.isEmpty(track.videoId)) {
                continue;
            }

            // ✅ Check cached restrictions first (O(1) set lookup vs disk I/O)
            if (restrictedIds.contains(track.videoId)) {
                continue;
            }

            eligibleCount++;
            if (OfflineAudioStore.hasValidatedOfflineAudio(appContext, track.videoId, track.duration)) {
                offlineCount++;
            }
        }

        return eligibleCount <= 0 || offlineCount >= eligibleCount;
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

        offlineDownloadQueued = true;
        notifyHeaderChanged();
        observeOfflineDownload(uniqueName);

        // Notify MusicPlayerFragment to update its offline state for this playlist
        notifyMusicPlayerOfflineChanged();
    }

    private void launchOfflineImportPicker(@NonNull List<String> videoIds) {
        if (!isAdded() || offlineImportLauncher == null) {
            return;
        }

        pendingImportTrackIds.clear();
        pendingImportTrackIds.addAll(videoIds);
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
            trackAdapter.submitTracks(currentTracks);
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

    @NonNull
    private String resolvePlaylistType(@NonNull String playlistId) {
        if (isFavoritesPlaylistContext(playlistId)) return "favorites";
        if (isCustomPlaylistContext(playlistId)) return "custom";
        return "youtube";
    }

    /**
     * If {@code videoId} is currently a replacement in any override for the
     * current playlist, return the <b>original</b> videoId that it replaced.
     * Otherwise return {@code videoId} unchanged.  This is needed so that
     * re-replacing an already-replaced track correctly updates the existing
     * override (original → newReplacement) instead of creating a dangling
     * one (oldReplacement → newReplacement) that never matches the cache.
     */
    @NonNull
    private String resolveOriginalVideoId(@NonNull String videoId) {
        if (!isAdded() || TextUtils.isEmpty(currentPlaylistId) || TextUtils.isEmpty(videoId))
            return videoId;
        java.util.Map<String, PlaylistOverrideStore.Override> overrides =
                PlaylistOverrideStore.INSTANCE.getOverrides(requireContext(), currentPlaylistId);
        for (java.util.Map.Entry<String, PlaylistOverrideStore.Override> entry : overrides.entrySet()) {
            if (videoId.equals(entry.getValue().getReplacementVideoId())) {
                return entry.getKey();
            }
        }
        return videoId;
    }

    @Override
    public void onReplacementUndone(@NonNull String playlistId, @NonNull String originalVideoId) {
        if (!isAdded()) return;
        Context ctx = requireContext();

        // 1. Delete offline audio for the replacement that is being undone
        PlaylistOverrideStore.Override existing =
                PlaylistOverrideStore.INSTANCE.getOverrides(ctx, playlistId).get(originalVideoId);
        String replacementVideoId = existing != null ? existing.getReplacementVideoId() : "";
        if (!TextUtils.isEmpty(replacementVideoId)) {
            OfflineAudioStore.deleteOfflineAudio(ctx, replacementVideoId);
        }

        // 2. Remove the override
        PlaylistOverrideStore.INSTANCE.removeOverride(ctx, playlistId, originalVideoId);

        // 3. Re-render from existing cached data (override is now removed, so the
        //    original track reappears). Do NOT invalidate the cache.
        List<PlaylistTrack> updatedTracks = sanitizeTracksForPlaylist(playlistId, loadCachedTracks(playlistId));
        renderTracks(updatedTracks, playlistId, false);

        // 4. Refresh adapter download-state indicators
        if (trackAdapter != null) {
            trackAdapter.invalidateTrackStateCache(replacementVideoId);
            trackAdapter.invalidateTrackStateCache(originalVideoId);
            trackAdapter.submitTracks(updatedTracks);
        }

        android.widget.Toast.makeText(ctx, "Reemplazo deshecho", android.widget.Toast.LENGTH_SHORT).show();

        // 5. Notify MusicPlayerFragment to update its offline state (overrides changed)
        notifyMusicPlayerOfflineChanged();
    }

    @Override
    public void onReplacementConfirmed(
            @NonNull String playlistId,
            @NonNull String playlistType,
            @NonNull String originalVideoId,
            @NonNull YouTubeMusicService.ReplacementCandidate candidate
    ) {
        if (!isAdded()) return;

        Context ctx = requireContext();

        // 1. Delete offline audio for the version being replaced (original or previous replacement)
        PlaylistOverrideStore.Override previousOverride =
                PlaylistOverrideStore.INSTANCE.getOverrides(ctx, playlistId).get(originalVideoId);
        String previousVideoId = previousOverride != null
                ? previousOverride.getReplacementVideoId()
                : originalVideoId;
        if (!TextUtils.isEmpty(previousVideoId)) {
            OfflineAudioStore.deleteOfflineAudio(ctx, previousVideoId);
        }

        // 2. Persist the override locally + cloud
        PlaylistOverrideStore.Override override = new PlaylistOverrideStore.Override(
                originalVideoId,
                candidate.videoId,
                candidate.title,
                candidate.artist,
                candidate.duration,
                candidate.thumbnailUrl,
                System.currentTimeMillis()
        );
        PlaylistOverrideStore.INSTANCE.putOverride(ctx, playlistId, override);

        // 3. Re-render the track list from existing data with the new override applied.
        //    Do NOT invalidate the cache — that destroys data and the subsequent
        //    network re-fetch may fail (no token / no connectivity), leaving the list empty.
        List<PlaylistTrack> updatedTracks = sanitizeTracksForPlaylist(playlistId, loadCachedTracks(playlistId));
        renderTracks(updatedTracks, playlistId, false);

        // 4. Refresh adapter download-state indicators for old and new tracks
        if (trackAdapter != null) {
            trackAdapter.invalidateTrackStateCache(previousVideoId);
            trackAdapter.invalidateTrackStateCache(candidate.videoId);
            trackAdapter.submitTracks(updatedTracks);
        }

        // 5. Auto-download if offline subscription is active
        if (isCurrentPlaylistOfflineAutoEnabled()) {
            enqueueSingleTrackForOfflineDownload(candidate.videoId, candidate.title, candidate.artist, candidate.duration);
        }

        // 6. Sync overrides to cloud
        if (AuthManager.getInstance(ctx).isSignedIn()) {
            CloudSyncManager.getInstance(ctx).syncPlaylistOverridesToCloud(
                    playlistId,
                    new ArrayList<>(PlaylistOverrideStore.INSTANCE.getOverrides(ctx, playlistId).values())
            );
        }

        // 7. Notify MusicPlayerFragment to update its offline state (overrides changed)
        notifyMusicPlayerOfflineChanged();
    }

    private void enqueueSingleTrackForOfflineDownload(
            @NonNull String videoId,
            @NonNull String title,
            @NonNull String artist,
            @NonNull String duration
    ) {
        if (!isAdded() || TextUtils.isEmpty(videoId)) return;
        try {
            Data inputData = new Data.Builder()
                    .putStringArray(OfflinePlaylistDownloadWorker.INPUT_VIDEO_IDS, new String[]{videoId})
                    .putStringArray(OfflinePlaylistDownloadWorker.INPUT_TITLES, new String[]{title})
                    .putStringArray(OfflinePlaylistDownloadWorker.INPUT_ARTISTS, new String[]{artist})
                    .putStringArray(OfflinePlaylistDownloadWorker.INPUT_DURATIONS, new String[]{duration})
                    .putBoolean(OfflinePlaylistDownloadWorker.INPUT_MANUAL_QUEUE, true)
                    .build();

            androidx.work.OneTimeWorkRequest request = new androidx.work.OneTimeWorkRequest.Builder(
                    OfflinePlaylistDownloadWorker.class)
                    .setInputData(inputData)
                    .addTag(currentPlaylistOfflineTag())
                    .build();

            androidx.work.WorkManager.getInstance(requireContext())
                    .enqueue(request);
            Log.d("PlaylistDetailFragment", "enqueueSingleTrackForOfflineDownload: queued videoId=" + videoId);
        } catch (Exception e) {
            Log.w("PlaylistDetailFragment", "enqueueSingleTrackForOfflineDownload: failed", e);
        }
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

        String observeTag = uniqueName;

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
                if (!offlineDownloadQueued) {
                    setOfflineDownloadVisualState(false, "");
                    notifyHeaderChanged();
                    maybeUpdateOfflineReadyState();
                }
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
                    notifyHeaderChanged();
                    return;
                }

                String playlistNameMarkup = " <b>" + TextUtils.htmlEncode(effectivePlaylistTitle) + "</b>";
                String status = "Descargando playlist" + playlistNameMarkup + " " + safeDownloaded + "/" + safeTotal;
                if (done > safeDownloaded) {
                    status += " • " + done + " revisadas";
                }
                notifyHeaderChanged();
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
                    notifyHeaderChanged();
                    return;
                }

                if (!isInternetAvailable()) {
                    notifyHeaderChanged();
                    return;
                }

                notifyHeaderChanged();
                return;
            }

            offlineDownloadQueued = false;

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
                    stopObservingOfflineDownload();
                    refreshVisibleTrackRows();
                    maybeUpdateOfflineReadyState();
                    return;
                }

                notifyHeaderChanged();
                stopObservingOfflineDownload();
                refreshVisibleTrackRows();
                maybeUpdateOfflineReadyState();
                return;
            }

            if (terminalInfo.getState() == WorkInfo.State.FAILED || terminalInfo.getState() == WorkInfo.State.CANCELLED) {
                Log.e(TAG_OFFLINE_DOWNLOAD, "terminal_state=" + terminalInfo.getState());
                setOfflineDownloadVisualState(false, "");

                if (!offlineObserverNotifyTerminalToasts) {
                    stopObservingOfflineDownload();
                    maybeUpdateOfflineReadyState();
                    return;
                }

                notifyHeaderChanged();
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
            String duration = normalizeDurationLabel(track.duration);
            mapped.add(new PlaylistTrack(
                    track.videoId,
                    track.title,
                    track.artist,
                    duration,
                    track.thumbnailUrl
            ));
        }
        return mapped;
    }

    @NonNull
    private List<PlaylistTrack> mergeTrackMetadataFromCache(
            @NonNull String playlistId,
            @NonNull List<PlaylistTrack> source
    ) {
        if (!isAdded() || source.isEmpty() || playlistId.isEmpty()) {
            return source;
        }

        List<PlaylistTrack> cached = loadCachedTracksInternal(playlistId, true);
        if (cached.isEmpty()) {
            return source;
        }

        Map<String, PlaylistTrack> cachedById = new HashMap<>();
        for (PlaylistTrack track : cached) {
            if (track == null || TextUtils.isEmpty(track.videoId)) {
                continue;
            }
            cachedById.put(track.videoId, track);
        }

        if (cachedById.isEmpty()) {
            return source;
        }

        List<PlaylistTrack> merged = new ArrayList<>(source.size());
        for (PlaylistTrack track : source) {
            if (track == null || TextUtils.isEmpty(track.videoId)) {
                continue;
            }

            PlaylistTrack cachedTrack = cachedById.get(track.videoId);
            String mergedDuration = normalizeDurationLabel(track.duration);
            if (mergedDuration.isEmpty() && cachedTrack != null) {
                mergedDuration = normalizeDurationLabel(cachedTrack.duration);
            }

            String mergedImageUrl = track.imageUrl == null ? "" : track.imageUrl.trim();
            if (mergedImageUrl.isEmpty() && cachedTrack != null && !TextUtils.isEmpty(cachedTrack.imageUrl)) {
                mergedImageUrl = cachedTrack.imageUrl.trim();
            }

            merged.add(new PlaylistTrack(
                    track.videoId,
                    track.title,
                    track.artist,
                    mergedDuration,
                    mergedImageUrl
            ));
        }

        return merged;
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
            java.util.Map<String, PlaylistOverrideStore.Override> favOverrides =
                    PlaylistOverrideStore.INSTANCE.getOverrides(requireContext(), playlistId);
            if (!favOverrides.isEmpty()) {
                mapped = PlaylistOverrideStore.INSTANCE.applyOverridesTo(
                        mapped, favOverrides,
                        track -> track.videoId,
                        ovr -> new PlaylistTrack(
                                ovr.getReplacementVideoId(), ovr.getTitle(),
                                ovr.getArtist(), ovr.getDuration(), ovr.getImageUrl()
                        )
                );
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
            java.util.Map<String, PlaylistOverrideStore.Override> customOverrides =
                    PlaylistOverrideStore.INSTANCE.getOverrides(requireContext(), playlistId);
            if (!customOverrides.isEmpty()) {
                mapped = PlaylistOverrideStore.INSTANCE.applyOverridesTo(
                        mapped, customOverrides,
                        track -> track.videoId,
                        ovr -> new PlaylistTrack(
                                ovr.getReplacementVideoId(), ovr.getTitle(),
                                ovr.getArtist(), ovr.getDuration(), ovr.getImageUrl()
                        )
                );
            }
            return mapped;
        }

        // For YouTube (non-local) playlists, apply persisted overrides
        if (isAdded() && !playlistId.isEmpty()) {
            java.util.Map<String, PlaylistOverrideStore.Override> overridesMap =
                    PlaylistOverrideStore.INSTANCE.getOverrides(requireContext(), playlistId);
            
            List<PlaylistTrack> overridden = PlaylistOverrideStore.INSTANCE.applyOverridesTo(
                    new ArrayList<>(source),
                    overridesMap,
                    track -> track.videoId,
                    ovr -> new PlaylistTrack(
                            ovr.getReplacementVideoId(),
                            ovr.getTitle(),
                            ovr.getArtist(),
                            ovr.getDuration(),
                            ovr.getImageUrl()
                    )
            );
            return overridden;
        }
        return new ArrayList<>(source);
    }

    @NonNull
    private static String decodeHtmlEntities(@Nullable String text) {
        if (TextUtils.isEmpty(text)) return "";
        if (!text.contains("&")) return text;
        return androidx.core.text.HtmlCompat.fromHtml(text, androidx.core.text.HtmlCompat.FROM_HTML_MODE_LEGACY).toString().trim();
    }

    private void persistEnrichedLocalTracks(
            @NonNull String playlistId,
            @NonNull List<PlaylistTrack> tracks,
            boolean isFavorites
    ) {
        if (!isAdded() || tracks.isEmpty()) return;
        try {
            if (isFavorites) {
                List<FavoritesPlaylistStore.FavoriteTrack> updated = new ArrayList<>(tracks.size());
                for (PlaylistTrack track : tracks) {
                    updated.add(new FavoritesPlaylistStore.FavoriteTrack(
                            track.videoId, track.title, track.artist, track.duration, track.imageUrl
                    ));
                }
                FavoritesPlaylistStore.storeFavorites(requireContext(), updated);
            } else if (isCustomPlaylistContext(playlistId)) {
                String name = playlistId.substring(CustomPlaylistsStore.CUSTOM_PLAYLIST_PREFIX.length());
                List<FavoritesPlaylistStore.FavoriteTrack> updated = new ArrayList<>(tracks.size());
                for (PlaylistTrack track : tracks) {
                    updated.add(new FavoritesPlaylistStore.FavoriteTrack(
                            track.videoId, track.title, track.artist, track.duration, track.imageUrl
                    ));
                }
                CustomPlaylistsStore.INSTANCE.savePlaylist(requireContext(), name, updated);
            }
        } catch (Exception e) {
            Log.e("PlaylistDetailFragment", "Error persisting enriched tracks", e);
        }
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
        externalRefreshFavoritesIfActive(null);
    }

    public void externalRefreshFavoritesIfActive(@Nullable String videoId) {
        if (!isAdded()) return;

        if (isFavoritesPlaylistContext(currentPlaylistId)) {
            List<PlaylistTrack> refreshed = sanitizeTracksForPlaylist(currentPlaylistId, Collections.emptyList());
            renderTracks(refreshed, currentPlaylistId, true);
            replacePlayerQueueWithCurrentOrder();
        } else if (videoId != null && currentTracks != null) {
            for (int i = 0; i < currentTracks.size(); i++) {
                if (TextUtils.equals(currentTracks.get(i).videoId, videoId)) {
                    if (trackAdapter != null) {
                        trackAdapter.invalidateTrackStateCache(videoId);
                        trackAdapter.notifyItemChanged(i);
                    }
                    break;
                }
            }
        }
    }

    private void renderTracks(
            @NonNull List<PlaylistTrack> tracks,
            @NonNull String playlistId,
            boolean fromCache
    ) {
        String selectedVideoId = getCurrentTrackVideoId();
        originalTracks.clear();
        for (PlaylistTrack t : tracks) {
            if (!isLikelyShort(t)) {
                originalTracks.add(t);
            }
        }

        currentTracks.clear();
        boolean offlineModeActive = isAdded() && requireContext()
                .getSharedPreferences(CloudSyncManager.PREFS_SETTINGS, Activity.MODE_PRIVATE)
                .getBoolean(CloudSyncManager.KEY_OFFLINE_MODE_ENABLED, false);
        if (offlineModeActive) {
            // Offload disk I/O to background thread to avoid blocking the UI
            final List<PlaylistTrack> snapshot = new ArrayList<>(originalTracks);
            final Context offlineCtx = requireContext().getApplicationContext();
            trackStateLookupExecutor.execute(() -> {
                List<PlaylistTrack> filtered = new ArrayList<>();
                for (PlaylistTrack t : snapshot) {
                    if (t == null || TextUtils.isEmpty(t.videoId)) continue;
                    String vid = t.videoId.trim();
                    if (!OfflineRestrictionStore.isRestricted(offlineCtx, vid)
                            && OfflineAudioStore.hasOfflineAudio(offlineCtx, vid)) {
                        filtered.add(t);
                    }
                }
                new Handler(Looper.getMainLooper()).post(() -> {
                    if (!isAdded()) return;
                    currentTracks.clear();
                    currentTracks.addAll(filtered);
                    finalizeRenderTracks(playlistId, fromCache, selectedVideoId);
                });
            });
            return;
        } else {
            currentTracks.addAll(originalTracks);
        }

        finalizeRenderTracks(playlistId, fromCache, selectedVideoId);
    }

    private void finalizeRenderTracks(
            @NonNull String playlistId,
            boolean fromCache,
            @Nullable String selectedVideoId
    ) {
        if (pendingOfflineToggle && !currentTracks.isEmpty()) {
            pendingOfflineToggle = false;
            onOfflineTogglePressed();
        }

        String firstTrackArtwork = "";
        for (PlaylistTrack track : currentTracks) {
            if (track == null || TextUtils.isEmpty(track.imageUrl)) {
                continue;
            }
            firstTrackArtwork = track.imageUrl;
            break;
        }
        // Update header artwork for favorites, custom playlists, and when empty
        // For other playlists (YouTube Music), also update to reflect current first track
        if (!TextUtils.isEmpty(firstTrackArtwork)) {
            boolean shouldUpdate = isFavoritesPlaylistContext(playlistId)
                    || isCustomPlaylistContext(playlistId)
                    || TextUtils.isEmpty(headerPlaylistThumbnail);
            if (shouldUpdate) {
                headerPlaylistThumbnail = firstTrackArtwork;
            }
        }

        trackAdapter.submitTracks(currentTracks);
        rebuildPlaybackQueue();

        // Prefetch stream URLs for first few tracks to reduce playback start delay
        prefetchStreamUrlsForTracks(currentTracks, 5);

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

        maybeAutoDownloadForCurrentPlaylist();
        maybeRestoreHiddenPlayerFromSnapshot();
        // Only prefetch artwork when data comes from the network (fresh fetch).
        // When loading from cache, images are already in Glide's disk cache
        // from the previous session — re-prefetching wastes CPU and I/O.
        if (!fromCache) {
            prefetchTrackArtworkForOffline(currentTracks, headerPlaylistThumbnail);
        }
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
        List<PlaylistTrack> ordered = new ArrayList<>();
        for (String videoId : queueVideoIds) {
            if (videoId == null) continue;
            int index = findTrackIndexByVideoId(remaining, videoId);
            if (index >= 0) {
                ordered.add(remaining.remove(index));
            } else {
                // If not in remaining anymore (duplicate), find in base to get metadata
                int baseIdx = findTrackIndexByVideoId(base, videoId);
                if (baseIdx >= 0) {
                    ordered.add(base.get(baseIdx));
                }
            }
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
        lastPlaybackQueueSize = currentTracks.size();
        lastPlaybackQueueShuffleState = shuffleModeEnabled;
    }

    private void ensurePlaybackQueue() {
        boolean needsRebuild = false;
        
        // Rebuild if queue is empty
        if (playbackQueueTracks.isEmpty()) {
            needsRebuild = true;
        }
        // Rebuild only if the actual size of tracks changed (not on every call)
        else if (lastPlaybackQueueSize != currentTracks.size()) {
            needsRebuild = true;
        }
        // Rebuild if shuffle mode was toggled
        else if (lastPlaybackQueueShuffleState != shuffleModeEnabled) {
            needsRebuild = true;
        }
        
        if (needsRebuild) {
            rebuildPlaybackQueue();
        }
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

    private void invalidateTracksCache(@NonNull String playlistId) {
        if (!isAdded() || playlistId.isEmpty()) return;
        getCachePrefs().edit()
                .remove(PREF_TRACKS_DATA_PREFIX + playlistId)
                .remove(PREF_TRACKS_UPDATED_AT_PREFIX + playlistId)
                .remove(PREF_TRACKS_FULL_CACHE_PREFIX + playlistId)
                .commit();
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
                String duration = normalizeDurationLabel(obj.optString("duration", ""));
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
                obj.put("duration", normalizeDurationLabel(track.duration));
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
        long now = System.currentTimeMillis();
        if (lastCachedSongPlayerTime > 0 && now - lastCachedSongPlayerTime < 500L) {
            return cachedSongPlayer;
        }
        Fragment fragment = getParentFragmentManager().findFragmentByTag("song_player");
        cachedSongPlayer = fragment instanceof SongPlayerFragment ? (SongPlayerFragment) fragment : null;
        lastCachedSongPlayerTime = now;
        return cachedSongPlayer;
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

        if (getView() == null) {
            return;
        }
        updateMiniPlayerUi();
    }

    private void onTrackSelected(int position) {
        if (position < 0 || position >= currentTracks.size()) {
            return;
        }

        // Intercept restricted tracks: show replacement sheet instead of playing
        PlaylistTrack tappedTrack = currentTracks.get(position);
        if (!TextUtils.isEmpty(tappedTrack.videoId) && isRestrictedTrack(tappedTrack.videoId)) {
            String playlistType = resolvePlaylistType(currentPlaylistId);
            boolean offlineSub = isCurrentPlaylistOfflineAutoEnabled();
            String resolvedId = resolveOriginalVideoId(tappedTrack.videoId);
            boolean hasOverride = PlaylistOverrideStore.INSTANCE.getOverrides(requireContext(), currentPlaylistId)
                    .containsKey(resolvedId);
            RestrictedTrackReplacementSheet.show(
                    getChildFragmentManager(),
                    currentPlaylistId,
                    playlistType,
                    resolvedId,
                    tappedTrack.title,
                    tappedTrack.artist,
                    tappedTrack.duration,
                    tappedTrack.imageUrl,
                    offlineSub,
                    hasOverride
            );
            return;
        }

        if (sbMiniPlayerProgress != null) {
            sbMiniPlayerProgress.setProgress(0);
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
        if (existingPlayer != null) {
            if (existingPlayer.isAdded()) {
                existingPlayer.externalSetReturnTargetTag(TAG_PLAYLIST_DETAIL);
                
                // If the selected track is already playing, just open the player
                if (TextUtils.equals(selectedVideoId, existingPlayer.getLoadedVideoId())) {
                    openPlayerFromMiniBar();
                    return;
                }

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
            }
            return;
        }

        startHiddenIntegratedPlayerAt(position, true);
    }

    private void onTrackMorePressed(int position, @NonNull View anchor) {
        if (!isAdded() || position < 0 || position >= currentTracks.size()) {
            return;
        }

        anchor.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        if (isAdded()) {
            showTrackActionPopup(anchor, position);
        }
    }

    private void showTrackActionPopup(@NonNull View anchor, int position) {
        if (!isAdded() || position < 0 || position >= currentTracks.size()) return;

        Context context = requireContext();
        PlaylistTrack selectedTrack = currentTracks.get(position);
        boolean hasOfflineAudio = !TextUtils.isEmpty(selectedTrack.videoId)
            && OfflineAudioStore.hasValidatedOfflineAudio(context, selectedTrack.videoId, selectedTrack.duration);

        com.google.android.material.bottomsheet.BottomSheetDialog dialog = new com.google.android.material.bottomsheet.BottomSheetDialog(context);
        View view = LayoutInflater.from(context).inflate(R.layout.bottom_sheet_track_options, null);
        dialog.setContentView(view);

        TextView tvTitle = view.findViewById(R.id.tvBsTrackTitle);
        TextView tvSubtitle = view.findViewById(R.id.tvBsTrackSubtitle);
        ImageView ivArt = view.findViewById(R.id.ivBsTrackArt);
        
        tvTitle.setText(TextUtils.isEmpty(selectedTrack.title) ? "Tema" : selectedTrack.title);
        tvSubtitle.setText(TextUtils.isEmpty(selectedTrack.artist) ? "Artista" : selectedTrack.artist);
        loadArtworkInto(ivArt, selectedTrack.imageUrl);

        View btnPlayNext = view.findViewById(R.id.btnBsPlayNext);
        View btnAddPrimary = view.findViewById(R.id.btnBsAddPrimary);
        View btnShare = view.findViewById(R.id.btnBsShare);
        
        ImageView ivPlayNext = view.findViewById(R.id.ivBsPlayNextIcon);
        TextView tvPlayNext = view.findViewById(R.id.tvBsPlayNextLabel);
        ImageView ivAddPrimary = view.findViewById(R.id.ivBsAddPrimary);
        TextView tvAddPrimary = view.findViewById(R.id.tvBsAddPrimary);
        ImageView ivShare = view.findViewById(R.id.ivBsShareIcon);
        TextView tvShare = view.findViewById(R.id.tvBsShareLabel);
        
        // Slot 1: Play (Reproducir)
        btnPlayNext.setVisibility(View.VISIBLE);
        ivPlayNext.setImageResource(R.drawable.ic_player_play);
        tvPlayNext.setText("Reproducir");
        btnPlayNext.setOnClickListener(v -> {
            dialog.dismiss();
            onTrackSelected(position);
        });
        
        // Slot 2: Play Next (Reproducir a continuación)
        ivAddPrimary.setImageResource(R.drawable.ic_stream_play_next);
        tvAddPrimary.setText("Reproducir a\ncontinuación");
        btnAddPrimary.setOnClickListener(v -> {
            dialog.dismiss();
            queueTrackAsNext(position);
        });
        
        // Slot 3: Add to Queue (Agregar a la fila)
        ivShare.setImageResource(R.drawable.ic_stream_queue_add);
        tvShare.setText("Agregar a\nla fila");
        btnShare.setOnClickListener(v -> {
            dialog.dismiss();
            queueTrackAtEnd(position);
        });

        View btnPlayPlaylist = view.findViewById(R.id.btnBsPlayPlaylist);
        View btnPlay = view.findViewById(R.id.btnBsPlay);
        View btnFavorite = view.findViewById(R.id.btnBsFavorite);
        View btnDownload = view.findViewById(R.id.btnBsDownload);
        View btnAddToQueue = view.findViewById(R.id.btnBsAddToQueue);
        ImageView ivDownload = view.findViewById(R.id.ivBsDownload);
        TextView tvDownload = view.findViewById(R.id.tvBsDownload);
        
        // Share as a row (Compartir)
        btnPlayPlaylist.setVisibility(View.VISIBLE);
        ImageView ivShareRow = btnPlayPlaylist.findViewById(R.id.ivBsPlayPlaylist);
        TextView tvShareRow = btnPlayPlaylist.findViewById(R.id.tvBsPlayPlaylist);
        ivShareRow.setImageResource(R.drawable.ic_playlist_share);
        tvShareRow.setText("Compartir");
        btnPlayPlaylist.setOnClickListener(v -> {
            dialog.dismiss();
            shareTrack(selectedTrack);
        });

        btnPlay.setVisibility(View.GONE);
        btnAddToQueue.setVisibility(View.GONE);
        
        // Favorite (now in list)
        btnFavorite.setVisibility(View.VISIBLE);
        ImageView ivFav = btnFavorite.findViewById(R.id.ivBsFavorite);
        TextView tvFav = btnFavorite.findViewById(R.id.tvBsFavorite);
        
        boolean isFavoritesPlaylist = FavoritesPlaylistStore.PLAYLIST_ID.equals(currentPlaylistId);
        boolean isAlreadyFavorite = FavoritesPlaylistStore.isFavorite(requireContext(), selectedTrack.videoId);

        if (isFavoritesPlaylist || isAlreadyFavorite) {
            ivFav.setImageResource(R.drawable.ic_favorite_star);
            tvFav.setText("Quitar de Favoritos");
            btnFavorite.setOnClickListener(v -> {
                dialog.dismiss();
                removeTrackFromFavoritesFromRow(position);
            });
        } else {
            ivFav.setImageResource(R.drawable.ic_favorite_star);
            tvFav.setText("Agregar a Favoritos");
            btnFavorite.setOnClickListener(v -> {
                dialog.dismiss();
                addTrackToFavoritesFromRow(position);
            });
        }
        
        // Download / Delete track download
        btnDownload.setVisibility(View.VISIBLE);
        if (hasOfflineAudio) {
            ivDownload.setImageResource(R.drawable.ic_delete_modern);
            tvDownload.setText("Eliminar descarga");
        } else {
            ivDownload.setImageResource(R.drawable.ic_download_bold);
            tvDownload.setText("Descargar");
        }
        
        btnDownload.setOnClickListener(v -> {
            dialog.dismiss();
            if (hasOfflineAudio) {
                removeTrackDownloadFromRow(position);
            } else {
                downloadTrackFromRow(position);
            }
        });

        // Add to Playlist (local only)
        btnAddToQueue.setVisibility(View.VISIBLE);
        ImageView ivAddPlaylist = btnAddToQueue.findViewById(R.id.ivBsAddToQueue);
        TextView tvAddPlaylist = btnAddToQueue.findViewById(R.id.tvBsAddToQueue);
        ivAddPlaylist.setImageResource(R.drawable.ic_stream_queue_add);
        tvAddPlaylist.setText("Añadir a playlist");
        btnAddToQueue.setOnClickListener(v -> {
            dialog.dismiss();
            showAddToLocalPlaylistDialog(selectedTrack);
        });

        // Replace track (always available)
        View btnReplace = view.findViewById(R.id.btnBsReplace);
        btnReplace.setVisibility(View.VISIBLE);
        ImageView ivReplace = view.findViewById(R.id.ivBsReplace);
        TextView tvReplace = view.findViewById(R.id.tvBsReplace);
        ivReplace.setImageResource(R.drawable.ic_player_repeat);
        tvReplace.setText("Reemplazar");
        btnReplace.setOnClickListener(v -> {
            dialog.dismiss();
            String playlistType = resolvePlaylistType(currentPlaylistId);
            boolean offlineSub = isCurrentPlaylistOfflineAutoEnabled();
            String resolvedId = resolveOriginalVideoId(selectedTrack.videoId);
            boolean hasOverride = PlaylistOverrideStore.INSTANCE.getOverrides(requireContext(), currentPlaylistId)
                    .containsKey(resolvedId);
            RestrictedTrackReplacementSheet.show(
                    getChildFragmentManager(),
                    currentPlaylistId,
                    playlistType,
                    resolvedId,
                    selectedTrack.title,
                    selectedTrack.artist,
                    selectedTrack.duration,
                    selectedTrack.imageUrl,
                    offlineSub,
                    hasOverride
            );
        });


        View parent = (View) view.getParent();
        if (parent != null) {
            parent.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        }

        dialog.show();
    }

    private void showAddToLocalPlaylistDialog(@NonNull PlaylistTrack track) {
        if (!isAdded()) return;

        List<String> localNames = CustomPlaylistsStore.INSTANCE.getAllPlaylistNames(requireContext());

        if (localNames.isEmpty()) {
            android.widget.Toast.makeText(requireContext(), "No tienes playlists. Crea una primero.", android.widget.Toast.LENGTH_SHORT).show();
            return;
        }

        String[] array = localNames.toArray(new String[0]);
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setTitle("Añadir a playlist")
            .setItems(array, (dialog, which) -> {
                String selected = localNames.get(which);
                CustomPlaylistsStore.INSTANCE.addTrackToPlaylist(
                    requireContext(),
                    selected,
                    track.videoId,
                    TextUtils.isEmpty(track.title) ? "Tema" : track.title,
                    track.artist == null ? "" : track.artist,
                    track.duration == null ? "" : track.duration,
                    track.imageUrl == null ? "" : track.imageUrl
                );
                android.widget.Toast.makeText(requireContext(), "Añadida a: " + selected, android.widget.Toast.LENGTH_SHORT).show();
            })
            .setNegativeButton("Cancelar", null)
            .show();
    }

    private void shareTrack(PlaylistTrack track) {
        if (track == null) return;
        String shareText = "Escucha " + track.title + " de " + track.artist + " en Sleppify: https://music.youtube.com/watch?v=" + track.videoId;
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TEXT, shareText);
        startActivity(Intent.createChooser(intent, "Compartir canción"));
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
        row.setPadding(dp(8), dp(10), dp(8), dp(10));

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
        PlaylistTrack movingTrack = selected;

        int insertIndex = Math.max(0, Math.min(currentQueueIndex + 1, playbackQueueTracks.size()));
        playbackQueueTracks.add(insertIndex, movingTrack);

        SongPlayerFragment player = findSongPlayerFragment();
        if (player != null && player.isAdded()) {
            player.externalInsertNext(movingTrack.videoId, movingTrack.title, movingTrack.artist, movingTrack.duration, movingTrack.imageUrl);
        }
        
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

        PlaylistTrack movingTrack = selected;
        playbackQueueTracks.add(movingTrack);

        SongPlayerFragment player = findSongPlayerFragment();
        if (player != null && player.isAdded()) {
            player.externalEnqueue(movingTrack.videoId, movingTrack.title, movingTrack.artist, movingTrack.duration, movingTrack.imageUrl);
        }
        
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

    private void removeTrackFromFavoritesFromRow(int position) {
        if (!isAdded() || position < 0 || position >= currentTracks.size()) {
            return;
        }

        PlaylistTrack selected = currentTracks.get(position);
        if (TextUtils.isEmpty(selected.videoId)) {
            return;
        }

        FavoritesPlaylistStore.removeFavorite(requireContext(), selected.videoId);
        
        if (FavoritesPlaylistStore.PLAYLIST_ID.equals(currentPlaylistId)) {
            currentTracks.remove(position);
            renderTracks(currentTracks, currentPlaylistId, false);
        } else {
            if (trackAdapter != null) {
                trackAdapter.invalidateTrackStateCache(selected.videoId);
                trackAdapter.notifyItemChanged(position);
            }
        }
    }

    private int resolveCurrentQueueIndex() {
        ensurePlaybackQueue();
        SongPlayerFragment player = findSongPlayerFragment();
        if (player != null && player.isAdded()) {
            return player.externalGetCurrentIndex();
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

        setOfflineDownloadVisualState(
            true,
            track.videoId,
            new String[] { track.videoId },
            null
        );
        offlineDownloadQueued = true;
        notifyHeaderChanged();
        observeOfflineDownload(uniqueName);

        // Notify MusicPlayerFragment to update its offline state for this playlist
        notifyMusicPlayerOfflineChanged();
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

        // Notify MusicPlayerFragment to update its offline state for this playlist
        notifyMusicPlayerOfflineChanged();
    }

    /**
     * Notifies MusicPlayerFragment that the offline state of the current playlist has changed.
     * This ensures the library view shows the correct offline indicator.
     */
    private void notifyMusicPlayerOfflineChanged() {
        if (!isAdded() || TextUtils.isEmpty(currentPlaylistId)) {
            return;
        }
        Fragment music = getParentFragmentManager().findFragmentByTag(TAG_MODULE_MUSIC);
        if (music instanceof MusicPlayerFragment) {
            ((MusicPlayerFragment) music).notifyPlaylistOfflineChanged(currentPlaylistId);
        }
    }

    private void playAllInOrder() {
        if (currentTracks.isEmpty()) {
            
            return;
        }

        if (sbMiniPlayerProgress != null) {
            sbMiniPlayerProgress.setProgress(0);
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
        if (llMiniPlayer != null) {
            llMiniPlayer.animate().cancel();
            float distance = llMiniPlayer.getHeight() > 0 ? llMiniPlayer.getHeight() : 300f;
            llMiniPlayer.animate().translationY(distance).setDuration(250)
                .setInterpolator(new android.view.animation.PathInterpolator(0.4f, 0f, 0.2f, 1f))
                .withEndAction(() -> {
                    llMiniPlayer.setVisibility(View.GONE);
                }).start();
        }

        SongPlayerFragment existingPlayer = findSongPlayerFragment();
        if (existingPlayer != null && existingPlayer.isAdded()) {
            existingPlayer.externalSetReturnTargetTag(TAG_PLAYLIST_DETAIL);
            showSongPlayerWithEnterAnimation(existingPlayer);
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

        if (llMiniPlayer != null) {
            if (sbMiniPlayerProgress != null) {
                sbMiniPlayerProgress.setProgress(0);
            }
            llMiniPlayer.animate().cancel();
            float distance = llMiniPlayer.getHeight() > 0 ? llMiniPlayer.getHeight() : 300f;
            llMiniPlayer.animate().translationY(distance).setDuration(250)
                .setInterpolator(new android.view.animation.PathInterpolator(0.4f, 0f, 0.2f, 1f))
                .withEndAction(() -> {
                    llMiniPlayer.setVisibility(View.GONE);
                }).start();
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
        if (existingPlayer != null) {
            if (existingPlayer.isAdded()) {
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

                    showSongPlayerWithEnterAnimation(existingPlayer);

                currentTrackIndex = position;
                miniPlaying = true;
                if (trackAdapter != null) {
                    trackAdapter.setActiveIndex(position);
                }
            }
            // Don't update mini-player UI - keep it hidden while full player is visible
            return;
        }

        currentTrackIndex = position;
        miniPlaying = true;
        trackAdapter.setActiveIndex(position);

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
        addSongPlayerWithEnterAnimation(playerFragment);
        // Don't update mini-player UI - keep it hidden while full player is visible
    }

    private boolean openPlayerFromSnapshot(
            @NonNull PlaybackHistoryStore.Snapshot snapshot,
            boolean startPlaying
    ) {
        if (!snapshot.isValid()) {
            return false;
        }

        // Hide mini-player immediately to prevent UI overlap
        if (llMiniPlayer != null) {
            llMiniPlayer.setVisibility(View.GONE);
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

        SongPlayerFragment existingPlayer = findSongPlayerFragment();
        if (existingPlayer != null) {
            if (existingPlayer.isAdded()) {
                existingPlayer.externalSetReturnTargetTag(TAG_PLAYLIST_DETAIL);
                existingPlayer.externalReplaceQueue(ids, titles, artists, durations, images, snapshotIndex, startPlaying);
                showSongPlayerWithEnterAnimation(existingPlayer);
            }
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
                addSongPlayerWithEnterAnimation(playerFragment);
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
                .add(R.id.playerContainer, playerFragment, "song_player")
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
        if (existingPlayer != null) {
            if (existingPlayer.isAdded()) {
                existingPlayer.externalSetReturnTargetTag(TAG_PLAYLIST_DETAIL);
                existingPlayer.externalReplaceQueue(ids, titles, artists, durations, images, 0, startPlaying);

                if (showPlayer) {
                    showSongPlayerWithEnterAnimation(existingPlayer);
                }

                currentTrackIndex = findTrackIndexByVideoId(currentTracks, videoId);
                miniPlaying = startPlaying;
                if (trackAdapter != null) {
                    trackAdapter.setActiveIndex(currentTrackIndex);
                }
                updateMiniPlayerUi();
            }
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
            if (showPlayer) {
                addSongPlayerWithEnterAnimation(playerFragment);
            } else {
                getParentFragmentManager()
                    .beginTransaction()
                    .setReorderingAllowed(true)
                    .add(R.id.playerContainer, playerFragment, "song_player")
                    .hide(playerFragment)
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
                .add(R.id.playerContainer, player, "song_player")
                .runOnCommit(player::externalAnimateEnterSlide)
                .commit();
    }

    private boolean startHiddenPlayerFromSnapshot(
            @NonNull PlaybackHistoryStore.Snapshot snapshot,
            boolean startPlaying
    ) {
        if (!snapshot.isValid() || !isAdded()) {
            return false;
        }

        SongPlayerFragment existingPlayer = findSongPlayerFragment();
        if (existingPlayer != null) {
            if (existingPlayer.isAdded()) {
                existingPlayer.externalSetReturnTargetTag(TAG_PLAYLIST_DETAIL);
                if (startPlaying && !existingPlayer.externalIsPlaying()) {
                    existingPlayer.externalTogglePlayback();
                }
                updateMiniPlayerUi();
            }
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

        final ArrayList<String> replayIds = ids;
        final ArrayList<String> replayTitles = titles;
        final ArrayList<String> replayArtists = artists;
        final ArrayList<String> replayDurations = durations;
        final ArrayList<String> replayImages = images;
        final int replaySnapshotIndex = snapshotIndex;
        final boolean replayStartPlaying = startPlaying;
        getParentFragmentManager()
                .beginTransaction()
                .setReorderingAllowed(true)
                .add(R.id.playerContainer, playerFragment, "song_player")
                .hide(playerFragment)
                .runOnCommit(() -> {
                    playerFragment.externalReplaceQueue(
                            replayIds,
                            replayTitles,
                            replayArtists,
                            replayDurations,
                            replayImages,
                            replaySnapshotIndex,
                            replayStartPlaying
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
                .add(R.id.playerContainer, playerFragment, "song_player")
                .hide(playerFragment)
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

        if (getActivity() instanceof MainActivity) {
            if (((MainActivity) getActivity()).isSongPlayerVisible()) {
                llMiniPlayer.setVisibility(View.GONE);
                return;
            }
        }
        SongPlayerFragment songPlayer = findSongPlayerFragment();
        boolean playerAttached = songPlayer != null && songPlayer.isAdded();
        String playerVideoId = "";
        PlaybackHistoryStore.Snapshot snapshot = loadPlaybackSnapshot();
        PlaybackHistoryStore.QueueTrack snapshotTrack = snapshot.currentTrack();

        int currentSeconds = 0;
        int totalSeconds = 1;

        if (playerAttached) {
            miniPlaying = songPlayer.externalIsPlaying();
            currentSeconds = songPlayer.externalGetCurrentSeconds();
            totalSeconds = Math.max(1, songPlayer.externalGetTotalSeconds());

            if (!currentTracks.isEmpty()) {
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
                if (trackAdapter != null) {
                    trackAdapter.setActiveIndex(currentTrackIndex);
                }
            }
        } else if (snapshotTrack != null) {
            miniPlaying = snapshot.isPlaying;
            int mappedIndex = currentTracks.isEmpty()
                    ? -1
                    : findTrackIndexByVideoId(currentTracks, snapshotTrack.videoId);
            currentTrackIndex = mappedIndex >= 0 ? mappedIndex : -1;
            currentSeconds = 0;
            totalSeconds = 1;
            if (trackAdapter != null) {
                trackAdapter.setActiveIndex(currentTrackIndex);
            }
        }

        // Update progress bar
        if (sbMiniPlayerProgress != null) {
            int clampedCurrent = Math.max(0, Math.min(totalSeconds, currentSeconds));
            int progress = Math.round((clampedCurrent / (float) Math.max(1, totalSeconds)) * 1000f);
            int boundedProgress = Math.max(0, Math.min(1000, progress));
            sbMiniPlayerProgress.setProgress(boundedProgress);
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

        if (llMiniPlayer.getVisibility() != View.VISIBLE && !isTv) {
            float distance = llMiniPlayer.getHeight() > 0 ? llMiniPlayer.getHeight() : 300f;
            llMiniPlayer.setTranslationY(distance);
            llMiniPlayer.setVisibility(View.VISIBLE);
            llMiniPlayer.animate().cancel();
            llMiniPlayer.animate()
                    .translationY(0f)
                    .setDuration(250)
                    .setInterpolator(new android.view.animation.PathInterpolator(0.4f, 0f, 0.2f, 1f))
                    .withEndAction(null)
                    .start();
        } else if (isTv && llMiniPlayer.getVisibility() != View.GONE) {
            llMiniPlayer.setVisibility(View.GONE);
        }

        String title;
        String subtitle;
        String imageUrl;

        if (currentListTrack != null) {
            title = currentListTrack.title;
            subtitle = currentListTrack.artist;
            imageUrl = currentListTrack.imageUrl == null ? "" : currentListTrack.imageUrl.trim();
        } else if (snapshotTrack != null) {
            title = TextUtils.isEmpty(snapshotTrack.title) ? "Última reproducción" : snapshotTrack.title;
            subtitle = snapshotTrack.artist;
            imageUrl = snapshotTrack.imageUrl == null ? "" : snapshotTrack.imageUrl.trim();
        } else if (playerAttached) {
            SharedPreferences prefs = getPlayerStatePrefs();
            String fallbackTitle = prefs.getString(PREF_LAST_TRACK_TITLE, "");
            String fallbackArtist = prefs.getString(PREF_LAST_TRACK_ARTIST, "");
            String fallbackImage = prefs.getString(PREF_LAST_TRACK_IMAGE, "");

            title = TextUtils.isEmpty(fallbackTitle) ? "Reproduciendo" : fallbackTitle;
            subtitle = fallbackArtist == null ? "" : fallbackArtist;
            imageUrl = fallbackImage == null ? "" : fallbackImage.trim();
        } else {
            title = "Selecciona una canción";
            subtitle = "";
            imageUrl = "";
            miniPlaying = false;
        }

        tvMiniPlayerTitle.setText(title);
        tvMiniPlayerSubtitle.setText(subtitle == null ? "" : subtitle);
        btnMiniPlayPause.setImageResource(miniPlaying
                ? R.drawable.ic_mini_pause
                : R.drawable.ic_mini_play);

        // Update TV player if visible
        if (isTv && llTvPlayerPane != null) {
            tvTvPlayerTitle.setText(title);
            tvTvPlayerSubtitle.setText(subtitle);
            btnTvPlayPause.setImageResource(miniPlaying ? R.drawable.ic_player_pause : R.drawable.ic_player_play);

            if (currentTrackIndex != lastMiniArtTrackIndex || !TextUtils.equals(imageUrl, lastMiniArtUrl)) {
                lastMiniArtTrackIndex = currentTrackIndex;
                lastMiniArtUrl = imageUrl;

                if (!TextUtils.isEmpty(imageUrl)) {
                    Glide.with(this)
                        .load(imageUrl)
                        .diskCacheStrategy(DiskCacheStrategy.ALL)
                        .into(ivTvPlayerCover);

                    if (ivTvPlayerBackground != null) {
                        Glide.with(this)
                            .load(imageUrl)
                            .diskCacheStrategy(DiskCacheStrategy.ALL)
                            .into(ivTvPlayerBackground);
                    }
                } else {
                    ivTvPlayerCover.setImageDrawable(null);
                    if (ivTvPlayerBackground != null) ivTvPlayerBackground.setImageDrawable(null);
                }
            }
        }

        if (currentListTrack != null) {
            persistCurrentPlaybackState(currentListTrack, miniPlaying);
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
            ivMiniPlayerArt.setImageDrawable(null);
        }
        lastMiniArtTrackIndex = artTrackIndex;
        lastMiniArtUrl = imageUrl;
    }

    private void updateMiniProgressBarOnly(SongPlayerFragment songPlayer) {
        if (sbMiniPlayerProgress == null) {
            return;
        }
        if (songPlayer == null || !songPlayer.isAdded()) {
            return;
        }
        int currentSeconds = songPlayer.externalGetCurrentSeconds();
        int totalSeconds = Math.max(1, songPlayer.externalGetTotalSeconds());
        int clampedCurrent = Math.max(0, Math.min(totalSeconds, currentSeconds));
        int progress = Math.round((clampedCurrent / (float) Math.max(1, totalSeconds)) * 1000f);
        sbMiniPlayerProgress.setProgress(Math.max(0, Math.min(1000, progress)));
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

    @NonNull
    private SharedPreferences getPlayerStatePrefs() {
        return requireContext().getSharedPreferences(PREFS_PLAYER_STATE, Activity.MODE_PRIVATE);
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
            boolean playing
    ) {
        if (!isAdded() || TextUtils.isEmpty(currentPlaylistId) || TextUtils.isEmpty(current.videoId)) {
            return;
        }

        if (TextUtils.equals(lastPersistedPlaylistId, currentPlaylistId)
                && TextUtils.equals(lastPersistedVideoId, current.videoId)
                && lastPersistedPlaying == playing) {
            return;
        }

        lastPersistedPlaylistId = currentPlaylistId;
        lastPersistedVideoId = current.videoId;
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

    private static int parseDurationSeconds(@NonNull String duration) {
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

    @NonNull
    private static String normalizeDurationLabel(@Nullable String rawDuration) {
        if (rawDuration == null) {
            return "";
        }

        String normalized = rawDuration.trim();
        if (normalized.isEmpty() || normalized.contains("--")) {
            return "";
        }

        return parseDurationSeconds(normalized) > 0 ? normalized : "";
    }

    private boolean isLikelyShort(@Nullable PlaylistTrack track) {
        if (track == null) return false;
        String lowerTitle = track.title.toLowerCase(Locale.US);
        if (lowerTitle.contains("#shorts") || lowerTitle.contains(" shorts") || lowerTitle.contains("shorts ")) {
            return true;
        }
        int seconds = parseDurationSeconds(track.duration);
        return seconds > 0 && seconds < 70;
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
        public void onBindViewHolder(@NonNull HeaderViewHolder holder, int position, @NonNull List<Object> payloads) {
            if (!payloads.isEmpty()) {
                for (Object payload : payloads) {
                    if (PAYLOAD_STATE_ONLY.equals(payload)) {
                        bindOfflineState(holder);
                    }
                }
                return;
            }
            onBindViewHolder(holder, position);
        }

        @Override
        public void onBindViewHolder(@NonNull HeaderViewHolder holder, int position) {
            boolean amoledModeEnabled = headerAmoledModeEnabled;
            holder.tvPlaylistName.setText(headerPlaylistTitle);
            holder.tvGoogleProfileName.setText(headerProfileName);
            holder.tvPlaylistInfo.setText(headerPlaylistInfo);
            holder.vPlaylistBackdropScrim.setVisibility(amoledModeEnabled ? View.GONE : View.VISIBLE);
            holder.vPlaylistBackdropBottomFade.setVisibility(amoledModeEnabled ? View.GONE : View.VISIBLE);
            holder.vPlaylistBackdropAmoledFade.setVisibility(amoledModeEnabled ? View.VISIBLE : View.GONE);
            
            bindOfflineState(holder);

            if (!TextUtils.isEmpty(headerPlaylistThumbnail)) {
                String currentCoverSig = (String) holder.ivPlaylistCover.getTag(R.id.tag_artwork_signature);
                String expectedSig = headerPlaylistThumbnail.trim();
                boolean coverChanged = currentCoverSig == null || !currentCoverSig.startsWith(expectedSig);
                if (coverChanged) {
                    loadArtworkInto(holder.ivPlaylistCover, headerPlaylistThumbnail, 800, true);
                    // Backdrop solo necesita baja resolución para blur (ahorro de memoria y carga más rápida)
                    loadArtworkInto(holder.ivPlaylistBackdrop, headerPlaylistThumbnail, 320, false);
                }
            } else {
                holder.ivPlaylistCover.setImageDrawable(null);
                holder.ivPlaylistBackdrop.setImageDrawable(null);
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

            // TV Focus navigation setup
            if (isTv) {
                holder.itemView.setFocusable(false);
                holder.itemView.setFocusableInTouchMode(false);
                if (llPlaylistToolbar != null) {
                    llPlaylistToolbar.setNextFocusDownId(holder.btnListenNow.getId());
                }
                holder.btnListenNow.setNextFocusUpId(R.id.btnPlaylistSearch);
            }
        }

        private void bindOfflineState(@NonNull HeaderViewHolder holder) {
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
        }

        @Override
        public int getItemCount() {
            return 1;
        }

        final class HeaderViewHolder extends RecyclerView.ViewHolder {
            final LinearLayout llPlaylistHeaderRoot;
            final ConstraintLayout flPlaylistHeaderContainer;
            final ImageView ivPlaylistCover;
            final ImageView ivPlaylistBackdrop;
            final View vPlaylistBackdropScrim;
            final View vPlaylistBackdropBottomFade;
            final View vPlaylistBackdropAmoledFade;
            final ShapeableImageView ivGoogleProfile;
            final TextView tvPlaylistName;
            final TextView tvGoogleProfileName;
            final TextView tvPlaylistInfo;
            final MaterialButton btnListenNow;
            final ImageButton btnDownload;

            HeaderViewHolder(@NonNull View itemView) {
                super(itemView);
                llPlaylistHeaderRoot = itemView.findViewById(R.id.llPlaylistHeaderRoot);
                flPlaylistHeaderContainer = itemView.findViewById(R.id.flPlaylistHeaderContainer);
                ivPlaylistCover = itemView.findViewById(R.id.ivPlaylistCover);
                ivPlaylistBackdrop = itemView.findViewById(R.id.ivPlaylistBackdrop);
                vPlaylistBackdropScrim = itemView.findViewById(R.id.vPlaylistBackdropScrim);
                vPlaylistBackdropBottomFade = itemView.findViewById(R.id.vPlaylistBackdropBottomFade);
                vPlaylistBackdropAmoledFade = itemView.findViewById(R.id.vPlaylistBackdropAmoledFade);
                ivGoogleProfile = itemView.findViewById(R.id.ivGoogleProfile);
                tvPlaylistName = itemView.findViewById(R.id.tvPlaylistName);
                tvGoogleProfileName = itemView.findViewById(R.id.tvGoogleProfileName);
                tvPlaylistInfo = itemView.findViewById(R.id.tvPlaylistInfo);
                btnListenNow = itemView.findViewById(R.id.btnListenNow);
                btnDownload = itemView.findViewById(R.id.btnDownload);
                btnDownload.setOnClickListener(v -> onOfflineTogglePressed());
                btnListenNow.setOnClickListener(v -> {
                    if (!currentTracks.isEmpty()) {
                        onTrackSelected(0);
                    }
                });
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
        final String normalizedSubtitle;

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
            String sub = artist.trim();
            String dur = normalizeDurationLabel(duration);
            if (!dur.isEmpty()) {
                sub = sub.isEmpty() ? dur : sub + " \u2022 " + dur;
            }
            this.normalizedSubtitle = sub;
        }
    }

    private interface OnTrackTap {
        void onTap(int position);
        void onMoreTap(int position, @NonNull View anchor);
    }

    private static final int TRACK_STATE_CACHE_MAX_SIZE = 200;

    private final class PlaylistTrackAdapter extends RecyclerView.Adapter<PlaylistTrackAdapter.TrackViewHolder> {
        private final List<PlaylistTrack> items;
        private final OnTrackTap onTrackTap;
        private final Map<String, Boolean> offlineAvailabilityCache = new HashMap<>();
        private final Map<String, Boolean> restrictedTrackCache = new HashMap<>();
        private int activeIndex = -1;
        private int submitGeneration = 0;
        private boolean offlineDownloadRunning;
        @NonNull
        private final Set<String> downloadingTrackIds = new HashSet<>();
        @NonNull
        private final Map<String, Float> downloadingTrackProgressById = new HashMap<>();
        private final Set<Integer> pendingNotifyPositions = new HashSet<>();
        private boolean notifyCoalesceScheduled = false;
        // Track which items have pending state lookups to avoid duplicate requests
        private final Set<String> pendingOfflineLookups = new HashSet<>();
        private final Set<String> pendingRestrictedLookups = new HashSet<>();
        // Cached colors — resolved once to avoid Resources lock on every bind
        private final int colorPrimary;
        private final int colorSurface;
        private final int colorTextDefault;
        // Fixed handler — avoids allocating a new Handler object on every coalesced notify
        private final Handler mainHandler = new Handler(Looper.getMainLooper());

        PlaylistTrackAdapter(@NonNull List<PlaylistTrack> items, @NonNull OnTrackTap onTrackTap) {
            this.items = new ArrayList<>(items);
            this.onTrackTap = onTrackTap;
            setHasStableIds(true);
            Context ctx = requireContext();
            colorPrimary = ContextCompat.getColor(ctx, R.color.stitch_blue);
            colorSurface = ContextCompat.getColor(ctx, R.color.surface_dark);
            colorTextDefault = ContextCompat.getColor(ctx, R.color.text_primary);
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        private void coalescedNotifyItemChanged(int position) {
            if (position < 0 || position >= getItemCount()) return;
            pendingNotifyPositions.add(position);
            if (!notifyCoalesceScheduled) {
                notifyCoalesceScheduled = true;
                mainHandler.post(() -> {
                    notifyCoalesceScheduled = false;
                    if (pendingNotifyPositions.isEmpty()) return;
                    int min = Integer.MAX_VALUE;
                    int max = Integer.MIN_VALUE;
                    for (int pos : pendingNotifyPositions) {
                        if (pos < min) min = pos;
                        if (pos > max) max = pos;
                    }
                    pendingNotifyPositions.clear();
                    int count = (max - min) + 1;
                    if (min >= 0 && min < getItemCount() && count > 0) {
                        final int fMin = min;
                        final int fSafeCount = Math.min(count, getItemCount() - min);
                        dispatchWhenIdle(() -> {
                            if (fMin < getItemCount()) {
                                int sc = Math.min(fSafeCount, getItemCount() - fMin);
                                notifyItemRangeChanged(fMin, sc, PAYLOAD_STATE_ONLY);
                            }
                        }, 0);
                    }
                });
            }
        }

        void submitTracks(@NonNull List<PlaylistTrack> newItems) {
            final List<PlaylistTrack> previous = new ArrayList<>(items);
            final List<PlaylistTrack> incoming = new ArrayList<>(newItems);
            final int generation = ++submitGeneration;

            // Offload DiffUtil to background thread to avoid UI stutter on large lists during playback
            trackStateLookupExecutor.execute(() -> {
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

                mainHandler.post(() -> {
                    if (!isAdded()) return;
                    if (generation != submitGeneration) return;

                    items.clear();
                    items.addAll(incoming);

                    if (activeIndex >= items.size()) {
                        activeIndex = -1;
                    }

                    diffResult.dispatchUpdatesTo(this);

                    // Trigger offline/restricted state lookup for the initial visible range.
                    // Use a one-shot OnLayoutChangeListener: fires exactly after the RV
                    // completes its first layout pass, guaranteeing valid item positions.
                    if (rvPlaylistContent != null) {
                        rvPlaylistContent.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
                            @Override
                            public void onLayoutChange(View v, int l, int t, int r, int b,
                                    int ol, int ot, int or, int ob) {
                                rvPlaylistContent.removeOnLayoutChangeListener(this);
                                if (!isAdded() || trackAdapter == null) return;
                                RecyclerView.LayoutManager lm = rvPlaylistContent.getLayoutManager();
                                if (lm instanceof LinearLayoutManager) {
                                    LinearLayoutManager llm = (LinearLayoutManager) lm;
                                    int first = llm.findFirstVisibleItemPosition() - 1;
                                    int last = llm.findLastVisibleItemPosition() - 1;
                                    if (first >= 0) trackAdapter.loadStateForVisibleRange(first, last);
                                }
                            }
                        });
                    }
                });
            });
        }

        void setActiveIndex(int activeIndex) {
            int previous = this.activeIndex;
            this.activeIndex = activeIndex;

            if (previous >= 0 && previous < getItemCount() && previous != activeIndex) {
                dispatchWhenIdle(() -> { if (previous < getItemCount()) notifyItemChanged(previous); }, 0);
            }
            if (activeIndex >= 0 && activeIndex < getItemCount()) {
                dispatchWhenIdle(() -> { if (activeIndex < getItemCount()) notifyItemChanged(activeIndex); }, 0);
            }
        }

        private void dispatchWhenIdle(@NonNull Runnable action, int attempt) {
            if (rvPlaylistContent == null) { action.run(); return; }
            if (!rvPlaylistContent.isComputingLayout() && !rvPlaylistContent.isLayoutRequested()) {
                action.run();
            } else {
                int delay = attempt < 3 ? 16 : 32;
                mainHandler.postDelayed(() -> dispatchWhenIdle(action, attempt + 1), delay);
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
            // Clear debounce timestamp so re-lookup is not blocked after invalidation
            lastOfflineStateLookupTimeByTrack.remove(normalized);
            lastRestrictedStateLookupTimeByTrack.remove(normalized);
        }

        /**
         * Invalidates track state cache only for tracks in the visible range.
         * This avoids clearing the entire cache (hundreds of tracks) when only
         * 10-15 visible items need refreshing.
         */
        void invalidateVisibleTrackStateCache(
                @NonNull List<PlaylistTrack> allTracks, int startIndex, int endIndex
        ) {
            int safeStart = Math.max(0, startIndex);
            int safeEnd = Math.min(allTracks.size() - 1, endIndex);
            for (int i = safeStart; i <= safeEnd; i++) {
                PlaylistTrack track = allTracks.get(i);
                if (track != null && !TextUtils.isEmpty(track.videoId)) {
                    String normalized = track.videoId.trim();
                    offlineAvailabilityCache.remove(normalized);
                    restrictedTrackCache.remove(normalized);
                }
            }
        }

        private boolean isOfflineAvailable(
                @NonNull Context context,
                @Nullable String trackId,
                @Nullable String expectedDuration,
                int position
        ) {
            if (TextUtils.isEmpty(trackId)) {
                return false;
            }
            String normalized = trackId.trim();
            Boolean cached = offlineAvailabilityCache.get(normalized);
            // Solo devolver valor cacheado; NO iniciar lookup aquí
            // Los lookups se inician desde loadStateForVisibleRange() en el scroll listener
            return cached != null ? cached : false;
        }

        /**
         * Inicia lookup de estado offline para items visibles.
         * Llamado desde scroll listener para cargar estado solo de items en pantalla.
         */
        void triggerOfflineStateLookup(int position, @NonNull PlaylistTrack track) {
            if (position < 0 || position >= items.size()) return;
            String normalized = track.videoId == null ? "" : track.videoId.trim();
            if (normalized.isEmpty()) return;
            
            Boolean cached = offlineAvailabilityCache.get(normalized);
            if (cached != null) return; // Ya está en cache
            if (pendingOfflineLookups.contains(normalized)) return; // Ya hay lookup en progreso
            
            long now = System.currentTimeMillis();
            Long lastLookup = lastOfflineStateLookupTimeByTrack.get(normalized);
            if (lastLookup != null && now - lastLookup < OFFLINE_STATE_LOOKUP_DEBOUNCE_MS) return;
            
            lastOfflineStateLookupTimeByTrack.put(normalized, now);
            pendingOfflineLookups.add(normalized);
            
            Context context = requireContext();
            trackStateLookupExecutor.execute(() -> {
                boolean available = OfflineAudioStore.hasValidatedOfflineAudio(context, normalized, track.duration);
                mainHandler.post(() -> {
                    pendingOfflineLookups.remove(normalized);
                    Boolean current = offlineAvailabilityCache.get(normalized);
                    if (current == null || current != available) {
                        offlineAvailabilityCache.put(normalized, available);
                        // Only notify if this position still shows the same track
                        // (ViewHolder may have been recycled and rebound to a different track)
                        if (position >= 0 && position < items.size()
                                && TextUtils.equals(items.get(position).videoId, track.videoId)) {
                            coalescedNotifyItemChanged(position);
                        }
                    }
                });
            });
        }

        private boolean isRestricted(@NonNull Context context, @Nullable String trackId, int position) {
            if (TextUtils.isEmpty(trackId)) {
                return false;
            }
            String normalized = trackId.trim();
            Boolean cached = restrictedTrackCache.get(normalized);
            // Solo devolver valor cacheado; NO iniciar lookup aquí
            // Los lookups se inician desde loadStateForVisibleRange() en el scroll listener
            return cached != null ? cached : false;
        }

        /**
         * Inicia lookup de estado restringido para items visibles.
         * Llamado desde scroll listener para cargar estado solo de items en pantalla.
         */
        void triggerRestrictedStateLookup(int position, @NonNull PlaylistTrack track) {
            if (position < 0 || position >= items.size()) return;
            String normalized = track.videoId == null ? "" : track.videoId.trim();
            if (normalized.isEmpty()) return;
            
            Boolean cached = restrictedTrackCache.get(normalized);
            if (cached != null) return; // Ya está en cache
            if (pendingRestrictedLookups.contains(normalized)) return; // Ya hay lookup en progreso
            
            long now = System.currentTimeMillis();
            Long lastLookup = lastRestrictedStateLookupTimeByTrack.get(normalized);
            if (lastLookup != null && now - lastLookup < OFFLINE_STATE_LOOKUP_DEBOUNCE_MS) return;
            
            lastRestrictedStateLookupTimeByTrack.put(normalized, now);
            pendingRestrictedLookups.add(normalized);
            
            Context context = requireContext();
            trackStateLookupExecutor.execute(() -> {
                boolean restricted = OfflineRestrictionStore.isRestricted(context, normalized);
                mainHandler.post(() -> {
                    pendingRestrictedLookups.remove(normalized);
                    Boolean current = restrictedTrackCache.get(normalized);
                    if (current == null || current != restricted) {
                        restrictedTrackCache.put(normalized, restricted);
                        // Only notify if this position still shows the same track
                        if (position >= 0 && position < items.size()
                                && TextUtils.equals(items.get(position).videoId, track.videoId)) {
                            coalescedNotifyItemChanged(position);
                        }
                    }
                });
            });
        }

        /**
         * Carga el estado (offline y restringido) solo para items visibles en pantalla.
         * Llamado desde el scroll listener del RecyclerView.
         */
        void loadStateForVisibleRange(int firstVisible, int lastVisible) {
            if (items.isEmpty()) return;
            int safeStart = Math.max(0, firstVisible);
            int safeEnd = Math.min(items.size() - 1, lastVisible);
            for (int i = safeStart; i <= safeEnd; i++) {
                PlaylistTrack track = items.get(i);
                if (track != null && !TextUtils.isEmpty(track.videoId)) {
                    triggerOfflineStateLookup(i, track);
                    triggerRestrictedStateLookup(i, track);
                }
            }
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
            return new TrackViewHolder(view);
        }

        @Override
        public void onViewRecycled(@NonNull TrackViewHolder holder) {
            super.onViewRecycled(holder);
            // Cancel pending Glide request and clear signature so loadArtworkInto()
            // reloads the image when this ViewHolder is rebound.
            // Do NOT call setImageDrawable(null) — it causes a visible white flash
            // before the new image loads. Glide handles the swap from old→new smoothly.
            if (holder.ivTrackArt != null) {
                Glide.with(holder.itemView.getContext()).clear(holder.ivTrackArt);
                holder.ivTrackArt.setTag(R.id.tag_artwork_signature, null);
            }
            // Evict state cache for this track so off-screen items don't occupy memory.
            // State will be reloaded lazily via loadStateForVisibleRange() when the item
            // scrolls back into view.
            int pos = holder.getAdapterPosition();
            if (pos >= 0 && pos < items.size()) {
                String videoId = items.get(pos).videoId;
                if (!TextUtils.isEmpty(videoId)) {
                    String normalized = videoId.trim();
                    offlineAvailabilityCache.remove(normalized);
                    restrictedTrackCache.remove(normalized);
                    pendingOfflineLookups.remove(normalized);
                    pendingRestrictedLookups.remove(normalized);
                    // Also clear the debounce timestamp so triggerOfflineStateLookup
                    // is not blocked when this item scrolls back into view.
                    lastOfflineStateLookupTimeByTrack.remove(normalized);
                    lastRestrictedStateLookupTimeByTrack.remove(normalized);
                }
            }
            // Safety cap: evict oldest entries if cache grows beyond limit.
            // NOTE: do NOT call .clear() here — that destroys state for currently-visible
            // items that are still loading, which is the root cause of the missing icons bug.
            while (offlineAvailabilityCache.size() > TRACK_STATE_CACHE_MAX_SIZE) {
                offlineAvailabilityCache.remove(offlineAvailabilityCache.keySet().iterator().next());
            }
            while (restrictedTrackCache.size() > TRACK_STATE_CACHE_MAX_SIZE) {
                restrictedTrackCache.remove(restrictedTrackCache.keySet().iterator().next());
            }
            // CRITICAL: Reset download progress bar to prevent showing stale progress on recycled views
            if (holder.vOfflineProgressFill != null) {
                holder.vOfflineProgressFill.animate().cancel();
                holder.vOfflineProgressFill.setScaleX(0f);
            }
            if (holder.flOfflineProgress != null) {
                holder.flOfflineProgress.setVisibility(View.GONE);
            }
            // Reset offline state icon to prevent showing stale state on recycled views
            if (holder.ivOfflineState != null) {
                holder.ivOfflineState.setVisibility(View.INVISIBLE);
            }
        }

        @Override
        public void onBindViewHolder(@NonNull TrackViewHolder holder, int position, @NonNull List<Object> payloads) {
            if (!payloads.isEmpty() && payloads.contains(PAYLOAD_STATE_ONLY)) {
                // State-only partial update — only refresh offline/restricted indicators
                // WITHOUT reloading the image, re-creating click listeners, or re-resolving colors.
                // This dramatically reduces work during cascading rebinds from async state lookups.
                PlaylistTrack track = items.get(position);
                Context context = holder.itemView.getContext();
                boolean isOfflineAvailable = isOfflineAvailable(context, track.videoId, track.duration, position);
                boolean isRestrictedTrack = isRestricted(context, track.videoId, position);
                boolean isCurrentlyDownloading = offlineDownloadRunning
                        && !TextUtils.isEmpty(track.videoId)
                        && downloadingTrackIds.contains(track.videoId)
                        && !isOfflineAvailable;

                boolean showOfflineState = isOfflineAvailable || isCurrentlyDownloading || isRestrictedTrack;
                holder.ivOfflineState.setVisibility(showOfflineState ? View.VISIBLE : View.INVISIBLE);

                if (isRestrictedTrack) {
                    holder.ivOfflineState.setImageResource(R.drawable.ic_restricted_small);
                    holder.ivOfflineState.setBackgroundResource(R.drawable.bg_offline_state_filled_alert);
                    holder.ivOfflineState.setColorFilter(ContextCompat.getColor(context, android.R.color.white));
                } else if (isOfflineAvailable) {
                    holder.ivOfflineState.setImageResource(R.drawable.ic_check_small);
                    holder.ivOfflineState.setBackgroundResource(R.drawable.bg_offline_state_filled_primary);
                    holder.ivOfflineState.setColorFilter(colorSurface);
                } else if (isCurrentlyDownloading) {
                    holder.ivOfflineState.setImageResource(R.drawable.ic_check_small);
                    holder.ivOfflineState.setBackgroundResource(R.drawable.bg_offline_state_outline_primary);
                    holder.ivOfflineState.setColorFilter(colorPrimary);
                }

                // Update subtitle with restricted label if needed
                String subtitle = !isOfflineAvailable && isRestrictedTrack
                        ? (track.normalizedSubtitle.isEmpty() ? "Restringida" : track.normalizedSubtitle + " \u2022 Restringida")
                        : track.normalizedSubtitle;
                holder.tvTrackSubtitle.setText(subtitle);

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

                // If artwork signature was cleared (recycled view), reload image
                if (holder.ivTrackArt != null
                        && holder.ivTrackArt.getTag(R.id.tag_artwork_signature) == null
                        && !TextUtils.isEmpty(track.imageUrl)) {
                    loadArtworkInto(holder.ivTrackArt, track.imageUrl, 50);
                }

                // Refresh active/playing state
                boolean isActive = position == activeIndex;
                SongPlayerFragment sp = cachedSongPlayer;
                if (sp == null) sp = findSongPlayerFragment();
                boolean isActuallyPlaying = sp != null && sp.isPlaying();
                String currentVideoId = sp != null ? sp.getLoadedVideoId() : "";
                boolean shouldShowOverlay = isActive && !TextUtils.isEmpty(currentVideoId) && TextUtils.equals(currentVideoId, track.videoId);
                holder.llNowPlayingOverlay.setVisibility(shouldShowOverlay ? View.VISIBLE : View.GONE);
                if (holder.animatedEq != null) {
                    holder.animatedEq.setAnimating(shouldShowOverlay && isActuallyPlaying);
                }
                return;
            }
            onBindViewHolder(holder, position);
        }

        @Override
        public void onBindViewHolder(@NonNull TrackViewHolder holder, int position) {
            PlaylistTrack track = items.get(position);
            if (isTv) {
                holder.itemView.setFocusable(true);
                holder.itemView.setNextFocusLeftId(R.id.btnTvNext);
            }
            holder.tvTrackTitle.setText(track.title);
            Context context = holder.itemView.getContext();

            // Eagerly trigger state lookup on full bind so first-visible items
            // don't have to wait for the scroll listener / layout listener.
            if (!TextUtils.isEmpty(track.videoId)) {
                triggerOfflineStateLookup(position, track);
                triggerRestrictedStateLookup(position, track);
            }

            boolean isOfflineAvailable = isOfflineAvailable(context, track.videoId, track.duration, position);
            boolean isRestrictedTrack = isRestricted(context, track.videoId, position);
            boolean isCurrentlyDownloading = offlineDownloadRunning
                    && !TextUtils.isEmpty(track.videoId)
                    && downloadingTrackIds.contains(track.videoId)
                    && !isOfflineAvailable;

            String subtitle = !isOfflineAvailable && isRestrictedTrack
                    ? (track.normalizedSubtitle.isEmpty() ? "Restringida" : track.normalizedSubtitle + " \u2022 Restringida")
                    : track.normalizedSubtitle;
            holder.tvTrackSubtitle.setText(subtitle);

            boolean showOfflineState = isOfflineAvailable || isCurrentlyDownloading || isRestrictedTrack;

            holder.ivOfflineState.setVisibility(showOfflineState ? View.VISIBLE : View.INVISIBLE);

            if (isRestrictedTrack) {
                holder.ivOfflineState.setImageResource(R.drawable.ic_restricted_small);
                holder.ivOfflineState.setBackgroundResource(R.drawable.bg_offline_state_filled_alert);
                holder.ivOfflineState.setColorFilter(ContextCompat.getColor(context, android.R.color.white));
            } else if (isOfflineAvailable) {
                holder.ivOfflineState.setImageResource(R.drawable.ic_check_small);
                holder.ivOfflineState.setBackgroundResource(R.drawable.bg_offline_state_filled_primary);
                holder.ivOfflineState.setColorFilter(colorSurface);
            } else if (isCurrentlyDownloading) {
                holder.ivOfflineState.setImageResource(R.drawable.ic_check_small);
                holder.ivOfflineState.setBackgroundResource(R.drawable.bg_offline_state_outline_primary);
                holder.ivOfflineState.setColorFilter(colorPrimary);
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
                loadArtworkInto(holder.ivTrackArt, track.imageUrl, 50);
            }

            boolean isActive = position == activeIndex;

            if (isTv) {
                holder.itemView.setFocusable(true);
                holder.itemView.setNextFocusLeftId(R.id.btnTvNext);
                
                holder.itemView.setOnFocusChangeListener((v, hasFocus) -> {
                    if (hasFocus) {
                        v.setBackgroundResource(R.drawable.bg_tv_item_focused);
                    } else {
                        v.setBackgroundResource(isActive
                                ? R.drawable.bg_playlist_track_active
                                : R.drawable.bg_playlist_track_default);
                    }
                });
            } else {
                int bgRes = isActive ? R.drawable.bg_playlist_track_active : R.drawable.bg_playlist_track_default;
                Object prevBg = holder.rootTrackRow.getTag(R.id.tag_artwork_signature);
                if (!Integer.valueOf(bgRes).equals(prevBg)) {
                    holder.rootTrackRow.setTag(R.id.tag_artwork_signature, bgRes);
                    holder.rootTrackRow.setBackgroundResource(bgRes);
                }
            }
            SongPlayerFragment songPlayer = cachedSongPlayer;
            if (songPlayer == null) songPlayer = findSongPlayerFragment();
            boolean isActuallyPlaying = songPlayer != null && songPlayer.isPlaying();
            String currentVideoId = songPlayer != null ? songPlayer.getLoadedVideoId() : "";
            boolean shouldShowOverlay = isActive && !TextUtils.isEmpty(currentVideoId) && TextUtils.equals(currentVideoId, track.videoId);
            holder.llNowPlayingOverlay.setVisibility(shouldShowOverlay ? View.VISIBLE : View.GONE);
            if (holder.animatedEq != null) {
                holder.animatedEq.setAnimating(shouldShowOverlay && isActuallyPlaying);
            }
            holder.tvTrackTitle.setTextColor(colorTextDefault);

            ViewGroup.LayoutParams layoutParams = holder.itemView.getLayoutParams();
            if (layoutParams instanceof RecyclerView.LayoutParams) {
                RecyclerView.LayoutParams recyclerParams = (RecyclerView.LayoutParams) layoutParams;
                recyclerParams.topMargin = 0;
                holder.itemView.setLayoutParams(recyclerParams);
            }

        }

        final class TrackViewHolder extends RecyclerView.ViewHolder {
            final ViewGroup rootTrackRow;
            final ImageView ivTrackArt;
            final FrameLayout llNowPlayingOverlay;
            final AnimatedEqualizerView animatedEq;
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
                animatedEq = itemView.findViewById(R.id.animatedEq);
                tvTrackTitle = itemView.findViewById(R.id.tvTrackTitle);
                tvTrackSubtitle = itemView.findViewById(R.id.tvTrackSubtitle);
                ivOfflineState = itemView.findViewById(R.id.ivOfflineState);
                ivMore = itemView.findViewById(R.id.ivMore);
                flOfflineProgress = itemView.findViewById(R.id.flOfflineProgress);
                vOfflineProgressFill = itemView.findViewById(R.id.vOfflineProgressFill);

                itemView.setOnClickListener(v -> {
                    int pos = getAdapterPosition();
                    if (pos != RecyclerView.NO_POSITION) onTrackTap.onTap(pos);
                });
                ivMore.setOnClickListener(v -> {
                    int pos = getAdapterPosition();
                    if (pos != RecyclerView.NO_POSITION) onTrackTap.onMoreTap(pos, ivMore);
                });
                itemView.setOnLongClickListener(v -> {
                    int pos = getAdapterPosition();
                    if (pos == RecyclerView.NO_POSITION) return false;
                    onTrackTap.onMoreTap(pos, itemView);
                    return true;
                });
            }
        }
    } // end PlaylistTrackAdapter

    private void launchSearchActivity() {
        if (!isAdded()) return;
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).openSearchFragment();
        }
    }
}

