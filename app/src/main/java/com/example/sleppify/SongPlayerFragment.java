package com.example.sleppify;

import com.example.sleppify.BuildConfig;
import android.app.Activity;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.res.ColorStateList;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.media.AudioAttributes;
import android.media.AudioDeviceCallback;
import android.media.AudioManager;
import android.media.AudioDeviceInfo;
import android.net.Uri;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import androidx.media3.exoplayer.ExoPlayer;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.SeekBar;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.example.sleppify.utils.YouTubeCropTransformation;
import com.example.sleppify.utils.YouTubeImageProcessor;
import com.bumptech.glide.load.DecodeFormat;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import android.widget.TextView;
import android.widget.FrameLayout;
import android.widget.ProgressBar;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.core.widget.NestedScrollView;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.palette.graphics.Palette;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import com.google.android.material.imageview.ShapeableImageView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

@androidx.annotation.OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
public class SongPlayerFragment extends Fragment {

    private static final String TAG = "SongPlayerFragment";
    private static final YouTubeCropTransformation SHARED_YT_CROP = new YouTubeCropTransformation();

    public static final String ARG_VIDEO_IDS = "arg_video_ids";
    public static final String ARG_TITLES = "arg_titles";
    public static final String ARG_ARTISTS = "arg_artists";
    public static final String ARG_DURATIONS = "arg_durations";
    public static final String ARG_IMAGES = "arg_images";
    public static final String ARG_SELECTED_INDEX = "arg_selected_index";
    public static final String ARG_START_PLAYING = "arg_start_playing";
    public static final String ARG_IS_TEMPORARY_PLAYER = "arg_is_temporary_player";

    private static final long PROGRESS_TICK_MS = 200L;
    private static final String PREFS_PLAYER_STATE = "player_state";
    private static final String PREFS_BACKDROP_COLORS = "sleppify_backdrop_colors";
    private static final int BACKDROP_COLOR_PREFS_MAX = 200;
    private static final String PREF_SOCIAL_STATS_PREFIX = "yt_social_stats_";
    private static final String PREF_LAST_PLAYLIST_ID = "stream_last_playlist_id";
    private static final String PREF_LAST_PLAYLIST_TITLE = "stream_last_playlist_title";
    private static final String PREF_LAST_PLAYLIST_SUBTITLE = "stream_last_playlist_subtitle";
    private static final String PREF_LAST_PLAYLIST_THUMBNAIL = "stream_last_playlist_thumbnail";
    private static final String PREF_LAST_YOUTUBE_ACCESS_TOKEN = "stream_last_youtube_access_token";
    private static final String MEDIA_NOTIFICATION_CHANNEL_ID = "sleppify_media_playback";
    private static final int MEDIA_NOTIFICATION_ID = 11031;
    private static final String TAG_PLAYLIST_DETAIL = "playlist_detail";
    private static final String TAG_MODULE_MUSIC = "module_music";
    private static final int MEDIA_SESSION_ARTWORK_MAX_SIZE_PX = 1400;
    private static final int REPEAT_MODE_OFF = 0;
    private static final int REPEAT_MODE_ALL = 1;
    private static final int REPEAT_MODE_ONE = 2;
    private static final long AUTOPLAY_RECOVERY_DELAY_MS = 1400L;
    private static final long TRACK_ERROR_RETRY_DELAY_MS = 750L;
    private static final int CONNECT_TIMEOUT_MS = 8000;
    private static final int READ_TIMEOUT_MS = 15000;
    private static final long SOURCE_PREPARE_TIMEOUT_MS = 15000L;
    private static final long SOCIAL_STATS_FETCH_DEFER_MS = 1800L;
    private static final long PLAYBACK_BOOTSTRAP_GRACE_MS = 1800L;
    private static final int MAX_PLAYBACK_SOURCE_RETRY = 1;
    private static final long PLAYBACK_SOURCE_RETRY_DELAY_MS = 350L;
    // Match the primary InnerTube client (ANDROID_MUSIC) so the CDN sees a
    // consistent identity between the URL resolution and the playback HTTP request.
    private static final String STREAM_HTTP_USER_AGENT = "com.google.android.apps.youtube.music/7.27.52 (Linux; U; Android 11; en_US; Pixel 4; Build/RQ3A.210705.001; Cronet/112.0.5615.49) gzip";
    private static final String AUDIUS_API_BASE_URL = "https://discoveryprovider.audius.co/v1";
    private static final String AUDIUS_APP_NAME = "sleppify";
    private static final int AUDIUS_SEARCH_LIMIT = 6;
    private static final int OFFLINE_CROSSFADE_MAX_SECONDS = 12;
    private static final int OFFLINE_CROSSFADE_DEFAULT_SECONDS = 6;
    private static final int OFFLINE_CROSSFADE_FIRST_STEP_MS = 500;
    private static final int OFFLINE_CROSSFADE_STEP_MS = 40;
    private static final int PLAYER_HERO_DEFAULT_HEIGHT_DP = 370;

    private final List<PlayerTrack> tracks = new ArrayList<>();
    private static final int MAX_NEXT_UP = 50;
    private final List<PlayerTrack> nextUpTracks = new ArrayList<>();
    private final List<PlayerTrack> originalQueueOrder = new ArrayList<>();
    private static final int MAX_GLOBAL_HISTORY = 50;
    private final java.util.ArrayDeque<PlayerTrack> globalPlaybackHistory = new java.util.ArrayDeque<>();

    private ImageView ivPlayerCover;
    private ShapeableImageView ivPlayerBackdrop;
    private AnimatedEqualizerView animatedEqPlayer;
    private FrameLayout flPlayerHero;
    @Nullable
    private android.widget.ProgressBar pbVideoLoading;
    private boolean currentSourceIsVideo = false;
    @Nullable
    private String currentVideoFilePath = null;
    @NonNull
    private final VideoSurfaceRouter videoRouter = new VideoSurfaceRouter();
    private TextView tvPlayerTitle;
    private TextView tvPlayerArtist;
    private TextView tvActionLikeCount;
    private TextView tvActionCommentCount;
    private TextView tvActionFavoriteLabel;
    private TextView tvCurrentTime;
    private TextView tvTotalTime;
    private TextView tvDeviceName;
    private ImageView ivDeviceIcon;
    @Nullable
    private AudioDeviceCallback audioDeviceCallback;
    private boolean audioDeviceCallbackRegistered = false;
    private View llQueueTrigger;
    private View llPlayerNavBar;
    private ImageButton btnPlayerClose;
    private ImageButton btnPlayerMore;
    private SeekBar sbPlaybackProgress;
    private NestedScrollView svSongPlayerContent;
    private ImageButton btnShuffle;
    private ImageButton btnRepeat;
    private View vPlayerShuffleIndicator;
    private View vPlayerRepeatIndicator;
    private ImageButton btnPlayPause;
    private View actionLike;
    private View actionDislike;
    private View actionComments;
    private View actionFavorite;
    private ImageView ivActionFavoriteIcon;
    private View actionRadio;
    private View actionShare;
    private View actionDownloadTrack;
    private ImageView ivActionDownloadIcon;
    private TextView tvActionDownloadLabel;

    private NextUpAdapter nextUpAdapter;
    @Nullable
    private ItemTouchHelper nextUpItemTouchHelper;

    @Nullable
    private OnBackPressedCallback backPressedCallback;
    @Nullable
    private ExoMediaPlayer localExoMediaPlayer;
    @Nullable
    private ExoMediaPlayer localCrossfadeIncomingPlayer;
    @Nullable
    private MediaSessionCompat mediaSession;
    @Nullable
    private SharedPreferences playerStatePrefs;
    @Nullable
    private SharedPreferences settingsPrefs;

    private boolean userSeeking = false;
    private boolean isTemporaryPlayer = false;

    private int currentIndex = 0;
    private boolean isPlaying = true;

    public boolean isPlaying() {
        return isPlaying;
    }
    private int currentSeconds = 0;
    private int totalSeconds = 1;
    private boolean isRestoringPosition = false;
    private int lastSeekTargetSeconds = -1;
    private boolean pauseRequestedByUser = false;
    private boolean appInBackground = false;
    private boolean swipeDismissGestureActive = false;
    private boolean swipeDismissAnimationRunning = false;
    private boolean playerEnterAnimationRunning = false;
    private int swipeDismissTouchSlopPx = 12;
    private int swipeDismissMinDistancePx = 120;
    private int cachedCrossfadeDurationMs = -1;
    private int consecutiveStreamFailures = 0;
    @Nullable
    private ActivityResultLauncher<String> notificationPermissionLauncher;
    private boolean shuffleEnabled = false;
    private int repeatMode = REPEAT_MODE_ALL;
    private boolean collapsingToMiniMode;
    private long lastHandledEndedAtMs = 0L;
    @NonNull
    private String lastHandledEndedVideoId = "";
    private boolean playCountRecordedForCurrentTrack = false;
    @NonNull
    private String currentPlaylistContextId = "";
    @NonNull
    private String currentPlaylistContextName = "";
    @NonNull
    private String pendingDownloadVideoId = "";
    /**
     * Tracks the videoId for which we already consumed a one-shot InnerTube re-resolution
     * retry after an ExoPlayer failure. If another error occurs for the same videoId,
     * we skip to the next track instead of retrying indefinitely.
     */
    @Nullable
    private String lastReresolveVideoId;
    /**
     * Tracks whether we already attempted an ExoPlayer AudioTrack reinit for the
     * current playback request token. Prevents infinite reinit loops on low-memory
     * devices where AudioFlinger cannot reclaim resources quickly enough.
     */
    private long audioTrackReinitToken = -1;
    @NonNull
    private final Random random = new Random();
    @NonNull
    private String loadedVideoId = "";

    @NonNull
    public String getLoadedVideoId() {
        return loadedVideoId;
    }
    public void externalSetPlaylistContext(@NonNull String playlistId, @NonNull String playlistName) {
        currentPlaylistContextId = playlistId;
        currentPlaylistContextName = playlistName;
    }
    @NonNull
    private String returnTargetTag = TAG_PLAYLIST_DETAIL;
    private boolean usingOfflineSource = false;
    private final Handler localProgressHandler = new Handler(Looper.getMainLooper());
    private final YouTubeMusicService radioMusicService = new YouTubeMusicService();
    private final ExecutorService streamResolverExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService socialStatsExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService persistenceExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService imageProcessingExecutor = Executors.newSingleThreadExecutor();
    private final Map<String, SocialStats> socialStatsCache = new HashMap<>();

    // Direct streaming & pre-fetching
    private volatile String prefetchedNextVideoId = null;
    private volatile String prefetchedNextUrl = null;

    // Gapless pre-buffer: an ExoMediaPlayer prepared silently for the next track
    private static final long GAPLESS_PRE_BUFFER_LISTEN_THRESHOLD_MS = 5_000L;
    private long accumulatedListenMs = 0;
    @Nullable
    private ExoMediaPlayer gaplessPreBufferedPlayer = null;
    @NonNull
    private String gaplessPreBufferedVideoId = "";
    private boolean gaplessPreBufferTriggered = false;

    @Nullable
    private Future<?> pendingStreamResolverFuture;
    @Nullable
    private Runnable sourcePrepareTimeoutRunnable;
    private boolean localSourcePreparing = false;
    private boolean localCrossfadeInProgress = false;
    private boolean localCrossfadeIsNetwork = false;
    private Context persistentAppContext;
    private int localCrossfadeTargetIndex = -1;
    private long localCrossfadeStartedAtMs = 0L;
    @Nullable
    private Runnable localCrossfadeTicker;
    private long lastPlaybackStartRequestAtMs = 0L;
    private long lastPrevPressAtMs = 0L;
    private static final long PREV_DOUBLE_TAP_WINDOW_MS = 3000L;
    private static final int PREV_RESTART_THRESHOLD_SECONDS = 3;
    private long activePlaybackRequestToken = 0L;
    @Nullable
    private Runnable autoplayRecoveryRunnable;
    @NonNull
    private String autoplayRecoveryVideoId = "";
    @Nullable
    private Runnable playbackErrorRetryRunnable;
    @NonNull
    private String playbackErrorRetryVideoId = "";
    @NonNull
    private String lastErroredVideoId = "";
    private int sameTrackErrorCount = 0;
    private int playerEngineRecoveryAttempts = 0;
    private final Set<String> audiusFallbackAttemptedVideoIds = new HashSet<>();
    @NonNull
    private String pendingSocialStatsVideoId = "";
    @Nullable
    private Runnable pendingSocialStatsFetchRunnable;
    @Nullable
    private CustomTarget<Bitmap> playerCoverTarget;

    private View playerBackgroundContainer;
    private final java.util.LinkedHashMap<String, Integer> dominantColorCache = new java.util.LinkedHashMap<String, Integer>(50, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry eldest) {
            return size() > 50;
        }
    };
    @Nullable
    private Runnable deferredBackdropLoadRunnable;
    @Nullable
    private Runnable nextUpRevealRunnable;
    private int nextUpRevealCursor = 0;
    @NonNull
    private String activeCoverVideoId = "";
    @NonNull
    private String activeBackdropVideoId = "";
    private boolean playerArtworkBootstrapPending = true;
    @Nullable
    private Bitmap mediaSessionArtwork;
    @NonNull
    private String mediaSessionArtworkVideoId = "";
    
    private final PlaybackStateCompat.Builder playbackStateBuilder = new PlaybackStateCompat.Builder()
            .setActions(PlaybackStateCompat.ACTION_PLAY
                    | PlaybackStateCompat.ACTION_PAUSE
                    | PlaybackStateCompat.ACTION_PLAY_PAUSE
                    | PlaybackStateCompat.ACTION_SKIP_TO_NEXT
                    | PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
                    | PlaybackStateCompat.ACTION_SEEK_TO);

    private final Runnable localProgressTicker = new Runnable() {
        @Override
        public void run() {
            if (!isAdded() || localExoMediaPlayer == null || userSeeking) {
                localProgressHandler.removeCallbacks(this);
                return;
            }

            try {
                int positionMs = Math.max(0, localExoMediaPlayer.getCurrentPosition());
                int durationMs = Math.max(1, localExoMediaPlayer.getDuration());

                accumulatedListenMs += PROGRESS_TICK_MS;
                maybeStartOfflineCrossfade(positionMs, durationMs);
                maybeStartGaplessPreBuffer(positionMs, durationMs);

                int playerSeconds = positionMs / 1000;

                // Position Guard: If we are restoring a position, don't let a "0" from the player
                // (which happens during prep/buffer) overwrite our target until the player actually advances.
                if (isRestoringPosition) {
                    if (playerSeconds > 0 || (lastSeekTargetSeconds <= 0)) {
                        // Player has advanced or we didn't have a target anyway
                        currentSeconds = playerSeconds;
                        isRestoringPosition = false;
                    } else {
                        // Keep our target currentSeconds for now, don't update from player
                    }
                } else {
                    currentSeconds = playerSeconds;
                }

                totalSeconds = Math.max(1, durationMs / 1000);

                // Only update UI if player is visible - reduces GPU/CPU load when hidden
                if (!isHidden()) {
                    tvCurrentTime.setText(formatSeconds(currentSeconds));
                    tvTotalTime.setText(formatSeconds(totalSeconds));

                    int progress = Math.round((Math.max(0, currentSeconds) / (float) Math.max(1, totalSeconds)) * 1000f);
                    sbPlaybackProgress.setProgress(Math.max(0, Math.min(1000, progress)));
                }

                if (!playCountRecordedForCurrentTrack && totalSeconds > 0
                        && currentSeconds >= Math.min(30, Math.max(1, totalSeconds / 2))) {
                    playCountRecordedForCurrentTrack = true;
                    if (isAdded() && currentIndex >= 0 && currentIndex < tracks.size()) {
                        PlayerTrack _t = tracks.get(currentIndex);
                        PlayCountStore.incrementPlayCount(
                                requireContext(),
                                _t.videoId, _t.title, _t.artist, _t.imageUrl,
                                currentPlaylistContextId.isEmpty() ? null : currentPlaylistContextId,
                                currentPlaylistContextName.isEmpty() ? null : currentPlaylistContextName
                        );
                    }
                }
                if (currentSeconds % 2 == 0) {
                    persistPlaybackSnapshot(false);
                    updateMediaSessionState();
                }
            } catch (Exception ignored) {
            }

            localProgressHandler.postDelayed(this, 200L);
        }
    };


    @NonNull
    public static SongPlayerFragment newInstance(
            @NonNull ArrayList<String> videoIds,
            @NonNull ArrayList<String> titles,
            @NonNull ArrayList<String> artists,
            @NonNull ArrayList<String> durations,
            @NonNull ArrayList<String> images,
            int selectedIndex
    ) {
        return newInstance(videoIds, titles, artists, durations, images, selectedIndex, true, false);
    }

    @NonNull
    public static SongPlayerFragment newInstance(
            @NonNull ArrayList<String> videoIds,
            @NonNull ArrayList<String> titles,
            @NonNull ArrayList<String> artists,
            @NonNull ArrayList<String> durations,
            @NonNull ArrayList<String> images,
            int selectedIndex,
            boolean startPlaying
    ) {
        return newInstance(videoIds, titles, artists, durations, images, selectedIndex, startPlaying, false);
    }

    @NonNull
    public static SongPlayerFragment newInstance(
            @NonNull ArrayList<String> videoIds,
            @NonNull ArrayList<String> titles,
            @NonNull ArrayList<String> artists,
            @NonNull ArrayList<String> durations,
            @NonNull ArrayList<String> images,
            int selectedIndex,
            boolean startPlaying,
            boolean isTemporaryPlayer
    ) {
        SongPlayerFragment fragment = new SongPlayerFragment();
        Bundle args = new Bundle();
        args.putStringArrayList(ARG_VIDEO_IDS, videoIds);
        args.putStringArrayList(ARG_TITLES, titles);
        args.putStringArrayList(ARG_ARTISTS, artists);
        args.putStringArrayList(ARG_DURATIONS, durations);
        args.putStringArrayList(ARG_IMAGES, images);
        args.putInt(ARG_SELECTED_INDEX, selectedIndex);
        args.putBoolean(ARG_START_PLAYING, startPlaying);
        args.putBoolean(ARG_IS_TEMPORARY_PLAYER, isTemporaryPlayer);
        fragment.setArguments(args);
        return fragment;
    }



    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        persistentAppContext = requireContext().getApplicationContext();
        if (savedInstanceState != null) {
            // Prevent auto-playback when restoring from a crash/process death
            isPlaying = false;
        }
        notificationPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                granted -> {
                    if (granted) {
                        // Notification removed
                    }
                }
        );
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_song_player, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        view.setTranslationY(0f);
        view.setAlpha(1f);

        persistentAppContext = requireContext().getApplicationContext();
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).setContainerOverlayMode(true);
        }

        // ✅ CRITICAL: Only findViewById here (fast path)
        ivPlayerCover = view.findViewById(R.id.ivPlayerCover);
        // ivPlayerBackdrop now only used as container for background color
        ivPlayerBackdrop = view.findViewById(R.id.ivPlayerBackdrop);
        playerBackgroundContainer = ivPlayerBackdrop;
        animatedEqPlayer = view.findViewById(R.id.animatedEqPlayer);
        flPlayerHero = view.findViewById(R.id.flPlayerHero);
        pbVideoLoading = view.findViewById(R.id.pbVideoLoading);
        // Init video router with hero container
        {
            FrameLayout miniContainer = null;
            ProgressBar miniSpinner = null;
            ImageView miniArt = null;
            if (getActivity() instanceof MainActivity) {
                GlobalMiniPlayerController mini = ((MainActivity) getActivity()).getGlobalMiniPlayer();
                if (mini != null) {
                    View miniRoot = ((MainActivity) getActivity()).findViewById(R.id.llGlobalMiniPlayer);
                    if (miniRoot != null) {
                        miniContainer = miniRoot.getRootView().findViewById(R.id.flMiniPlayerVideoContainer);
                        miniSpinner = miniRoot.getRootView().findViewById(R.id.pbMiniPlayerLoading);
                        miniArt = mini.getArtView();
                    }
                }
            }
            videoRouter.init(requireContext(), flPlayerHero, miniContainer,
                    pbVideoLoading, miniSpinner, ivPlayerCover, miniArt);
            videoRouter.setCallback(new VideoSurfaceRouter.Callback() {
                @Override
                public void onVideoConfirmed() {
                    // Real video — hero layout adjustments
                    updatePlayerSurfaceForSource();
                }
                @Override
                public void onVideoStatic() {
                    // Static image — revert to cover art
                    revertVideoToStaticCover();
                }
            });
        }
        playerArtworkBootstrapPending = true;
        tvPlayerTitle = view.findViewById(R.id.tvPlayerTitle);
        tvPlayerTitle.setSelected(true);
        tvPlayerArtist = view.findViewById(R.id.tvPlayerArtist);
        actionLike = view.findViewById(R.id.actionLike);
        actionDislike = view.findViewById(R.id.actionDislike);
        actionComments = view.findViewById(R.id.actionComments);
        actionFavorite = view.findViewById(R.id.actionFavorite);
        actionRadio = view.findViewById(R.id.actionRadio);
        actionShare = view.findViewById(R.id.actionShare);
        actionDownloadTrack = view.findViewById(R.id.actionDownloadTrack);
        ivActionDownloadIcon = view.findViewById(R.id.ivActionDownloadIcon);
        tvActionDownloadLabel = view.findViewById(R.id.tvActionDownloadLabel);
        tvActionLikeCount = view.findViewById(R.id.tvActionLikeCount);
        tvActionCommentCount = view.findViewById(R.id.tvActionCommentCount);
        tvActionFavoriteLabel = view.findViewById(R.id.tvActionFavoriteLabel);
        ivActionFavoriteIcon = view.findViewById(R.id.ivActionFavoriteIcon);
        tvCurrentTime = view.findViewById(R.id.tvCurrentTime);
        tvTotalTime = view.findViewById(R.id.tvTotalTime);
        sbPlaybackProgress = view.findViewById(R.id.sbPlaybackProgress);
        svSongPlayerContent = view.findViewById(R.id.svSongPlayerContent);
        disablePlayerContentScroll();
        final View clPlayerContent = view.findViewById(R.id.clPlayerContent);
        if (clPlayerContent != null && svSongPlayerContent != null) {
            svSongPlayerContent.addOnLayoutChangeListener((v, left, top, right, bottom,
                    oldLeft, oldTop, oldRight, oldBottom) -> {
                int scrollH = bottom - top;
                if (scrollH > 0 && clPlayerContent.getMinimumHeight() != scrollH) {
                    clPlayerContent.setMinimumHeight(scrollH);
                }
            });
        }
        btnShuffle = view.findViewById(R.id.btnShuffle);
        btnRepeat = view.findViewById(R.id.btnRepeat);
        vPlayerShuffleIndicator = view.findViewById(R.id.vPlayerShuffleIndicator);
        vPlayerRepeatIndicator = view.findViewById(R.id.vPlayerRepeatIndicator);
        btnPlayPause = view.findViewById(R.id.btnPlayPause);
        llQueueTrigger = view.findViewById(R.id.llQueueTrigger);
        llQueueTrigger.setOnClickListener(v -> showQueueBottomSheet());
        llPlayerNavBar = view.findViewById(R.id.llPlayerNavBar);
        btnPlayerClose = view.findViewById(R.id.btnPlayerClose);
        btnPlayerMore = view.findViewById(R.id.btnPlayerMore);
        if (btnPlayerClose != null) {
            btnPlayerClose.setOnClickListener(v -> collapseToMiniMode(true));
        }
        if (btnPlayerMore != null) {
            btnPlayerMore.setOnClickListener(v -> showPlayerOptionsSheet());
        }

        // Apply status-bar inset to the internal nav bar so buttons sit below the status bar
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(view, (v, insets) -> {
            int statusBarHeight = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.statusBars()).top;
            if (llPlayerNavBar != null) {
                llPlayerNavBar.setPadding(
                    llPlayerNavBar.getPaddingLeft(),
                    statusBarHeight,
                    llPlayerNavBar.getPaddingRight(),
                    llPlayerNavBar.getPaddingBottom()
                );
            }
            return insets;
        });
        tvDeviceName = view.findViewById(R.id.tvDeviceName);
        ivDeviceIcon = view.findViewById(R.id.ivDeviceIcon);

        // ✅ PHASE 1: Lightweight UI wiring only — runs during first frame, no heavy I/O
        view.post(() -> {
            if (!isAdded()) return;

            setupSwipeToDismiss(view);
            setupBackPressToMiniMode();
            resetPlayerScrollToTop();

            view.findViewById(R.id.btnPrev).setOnClickListener(v -> moveTrack(-1));
            view.findViewById(R.id.btnNext).setOnClickListener(v -> moveTrack(1));
            btnShuffle.setOnClickListener(v -> toggleShuffleMode());
            btnRepeat.setOnClickListener(v -> cycleRepeatMode());
            btnPlayPause.setOnClickListener(v -> togglePlayback());

            sbPlaybackProgress.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (!fromUser || totalSeconds <= 0) {
                        return;
                    }
                    currentSeconds = Math.max(0, Math.min(totalSeconds, Math.round((progress / 1000f) * totalSeconds)));
                    tvCurrentTime.setText(formatSeconds(currentSeconds));
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {
                    userSeeking = true;
                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                    userSeeking = false;
                    if (localExoMediaPlayer != null) {
                        try {
                            localExoMediaPlayer.seekTo(currentSeconds * 1000);
                        } catch (Exception ignored) {
                        }
                    }
                    if (isPlaying) {
                        ensureActivePlaybackIfExpected("seek-bar-seekTo");
                    }
                }
            });
        });

        // ✅ PHASE 2: Heavy initialization — deferred until after entry animation (280ms) finishes
        // SharedPreferences reads, track hydration, MediaSession, and playback start here
        view.postDelayed(() -> {
            if (!isAdded()) return;

            playerStatePrefs = requireContext().getSharedPreferences(PREFS_PLAYER_STATE, Activity.MODE_PRIVATE);
            settingsPrefs = requireContext().getSharedPreferences(CloudSyncManager.PREFS_SETTINGS, Activity.MODE_PRIVATE);
            invalidateCrossfadeDurationCache();
            loadBackdropColorCache();
            loadPlaybackModesFromSettings();
            setupSocialActions();
            setupAudioDeviceCallback();
            updatePlaybackModeButtons();

            hydrateTracksFromArgs();
            setupMediaSession();

            currentIndex = Math.max(0, Math.min(currentIndex, tracks.size() - 1));
            bindCurrentTrack(true);
            playCurrentTrack();
        }, 320L);
    }

    @Override
    public void onStop() {
        persistPlaybackSnapshot(false);
        super.onStop();
    }

    @Override
    public void onPause() {
        appInBackground = true;
        updateBackPressedCallbackEnabled(true);
        stopLocalProgressTicker();
        persistPlaybackSnapshot(false);
        super.onPause();
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        updateBackPressedCallbackEnabled(hidden);
        if (hidden) {
            swipeDismissGestureActive = false;
            swipeDismissAnimationRunning = false;
            // ✅ ALWAYS pause ticker when hidden to reduce CPU waste
            stopLocalProgressTicker();
            // Reparent video surface to mini-player
            videoRouter.onPlayerHidden();
        } else {
            if (getView() != null) {
                if (!playerEnterAnimationRunning) {
                    getView().animate().cancel();
                    getView().setTranslationY(0f);
                }
                getView().setVisibility(View.VISIBLE);
            }
            resetPlayerScrollToTop();
            // Reparent video surface back to hero
            videoRouter.onPlayerVisible();
            // ✅ Restart ticker when visible if playing
            if (isPlaying) {
                startLocalProgressTicker();
            }
            ensureActivePlaybackIfExpected("onHiddenChanged-visible");
            // ✅ Re-bind artwork if the player was re-shown and artwork doesn't match current track
            // Skip when video is active — video surface handles display, no artwork rebind needed
            if (!currentSourceIsVideo && !tracks.isEmpty() && currentIndex >= 0 && currentIndex < tracks.size()) {
                PlayerTrack current = tracks.get(currentIndex);
                String currentVideoId = current.videoId == null ? "" : current.videoId;
                if (!TextUtils.equals(activeCoverVideoId, currentVideoId)
                        || !TextUtils.equals(activeBackdropVideoId, currentVideoId)) {
                    bindCurrentTrackInternal(false, false);
                }
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        swipeDismissGestureActive = false;
        swipeDismissAnimationRunning = false;
        if (getView() != null && !isHidden()) {
            if (!playerEnterAnimationRunning) {
                getView().animate().cancel();
                getView().setTranslationY(0f);
            }
            getView().setVisibility(View.VISIBLE);
        }
        appInBackground = false;
        updateBackPressedCallbackEnabled(isHidden());
        resetPlayerScrollToTop();
        ensureActivePlaybackIfExpected("onResume");
        persistPlaybackSnapshot(false);
    }



    private void ensureActivePlaybackIfExpected(@NonNull String reason) {
        if (!isPlaying) {
            return;
        }

        long now = SystemClock.elapsedRealtime();
        boolean bootstrapWindow = (now - lastPlaybackStartRequestAtMs) < PLAYBACK_BOOTSTRAP_GRACE_MS;

        if (localExoMediaPlayer != null) {
            if (localSourcePreparing && bootstrapWindow) {
                return;
            }

            boolean alreadyPlaying = false;
            try {
                alreadyPlaying = localExoMediaPlayer.isPlaying();
            } catch (Exception ignored) {
            }

            if (alreadyPlaying) {
                startLocalProgressTicker();
                return;
            }

            try {
                localExoMediaPlayer.start();
                startLocalProgressTicker();
            } catch (Exception e) {
                if (localSourcePreparing || bootstrapWindow) {
                    return;
                }
                Log.w(TAG, "ensureActivePlaybackIfExpected: local restart failed, reloading track. reason=" + reason, e);
                playCurrentTrack();
            }
            return;
        }

        if (!hasPendingStreamResolution()) {
            if (bootstrapWindow || localSourcePreparing) {
                return;
            }
            playCurrentTrack();
        }
    }

    private boolean isEffectivePlaying() {
        if (!isPlaying) {
            return false;
        }
        if (localExoMediaPlayer != null) {
            try {
                return localExoMediaPlayer.isPlaying();
            } catch (Exception ignored) {
                return false;
            }
        }
        return false;
    }

    @Override
    public void onDestroyView() {
        cleanupAudioDeviceCallback();
        if (!isTemporaryPlayer) {
            persistPlaybackSnapshot(true);
        }
        cancelAutoplayRecovery();
        cancelPlaybackErrorRetry();
        cancelDeferredBackdropLoad();
        cancelNextUpReveal();
        cancelPendingSocialStatsFetch();
        cancelPendingStreamResolver();
        clearPlayerCoverRequest();
        // Release video surface before destroying view
        videoRouter.onPlayerReleased();
        resetPlayerHeroContainerHeight();
        stopLocalProgressTicker();
        
        // Si es un reproductor temporal (e.g., en SearchActivity), NO liberar el player
        // Mantenerlo en su estado actual para que la reproducción continúe
        if (!isTemporaryPlayer) {
            releaseLocalExoMediaPlayer();
        }
        
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).setContainerOverlayMode(false);
        }
        releaseMediaSession();
        clearMediaNotificationArtwork();
        if (backPressedCallback != null) {
            backPressedCallback.remove();
            backPressedCallback = null;
        }
        settingsPrefs = null;
        flPlayerHero = null;
        streamResolverExecutor.shutdownNow();
        socialStatsExecutor.shutdownNow();
        persistenceExecutor.shutdownNow();
        imageProcessingExecutor.shutdownNow();
        super.onDestroyView();
    }

    private void resetPlayerScrollToTop() {
        if (svSongPlayerContent == null) {
            return;
        }
        svSongPlayerContent.post(() -> {
            if (svSongPlayerContent != null) {
                svSongPlayerContent.scrollTo(0, 0);
            }
        });
    }

    private void disablePlayerContentScroll() {
        if (svSongPlayerContent == null) return;
        svSongPlayerContent.setNestedScrollingEnabled(false);
        // Force scroll position back to top any time it drifts
        svSongPlayerContent.setOnScrollChangeListener(
                (NestedScrollView.OnScrollChangeListener) (v, scrollX, scrollY, oldScrollX, oldScrollY) -> {
                    if (scrollY != 0) v.scrollTo(0, 0);
                });
    }

    private void setupMediaSession() {
        if (!isAdded()) {
            return;
        }

        if (mediaSession != null) {
            mediaSession.release();
        }

        mediaSession = new MediaSessionCompat(requireContext().getApplicationContext(), "SleppifySongSession");
        mediaSession.setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS | MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS);
        mediaSession.setCallback(new MediaSessionCompat.Callback() {
            @Override
            public void onPlay() {
                if (tracks.isEmpty()) {
                    return;
                }

                pauseRequestedByUser = false;
                isPlaying = true;
                ensureActivePlaybackIfExpected("media-session-play");
                updatePlayPauseIcon();
                updateMediaSessionState();
                updateMediaNotification();
                persistPlaybackSnapshot(false);
            }

            @Override
            public void onPause() {
                pauseRequestedByUser = true;
                if (localExoMediaPlayer != null) {
                    try {
                        localExoMediaPlayer.pause();
                        stopLocalProgressTicker();
                    } catch (Exception ignored) {
                    }
                }
                isPlaying = false;
                updatePlayPauseIcon();
                updateMediaSessionState();
                updateMediaNotification();
                persistPlaybackSnapshot(false);
            }

            @Override
            public void onSkipToNext() {
                moveTrack(1);
            }

            @Override
            public void onSkipToPrevious() {
                moveTrack(-1);
            }

            @Override
            public void onSetShuffleMode(int shuffleMode) {
                toggleShuffleMode();
            }

            @Override
            public void onSeekTo(long pos) {
                currentSeconds = Math.max(0, (int) (pos / 1000L));
                if (localExoMediaPlayer != null) {
                    try {
                        localExoMediaPlayer.seekTo(currentSeconds * 1000);
                    } catch (Exception ignored) {
                    }
                }
                if (isPlaying) {
                    ensureActivePlaybackIfExpected("media-session-seek");
                }
                tvCurrentTime.setText(formatSeconds(currentSeconds));
                int progress = Math.round((Math.max(0, currentSeconds) / (float) Math.max(1, totalSeconds)) * 1000f);
                sbPlaybackProgress.setProgress(Math.max(0, Math.min(1000, progress)));
                updateMediaSessionState();
            }
        });
        ensureMediaNotificationChannel();
    }

    private void releaseMediaSession() {
        if (mediaSession != null) {
            mediaSession.setActive(false);
            mediaSession.release();
            mediaSession = null;
        }
    }

    private void moveTrack(int delta) {
        moveTrack(delta, false, false);
    }

    private void moveTrack(int delta, boolean fromCompletion) {
        moveTrack(delta, fromCompletion, false);
    }

    private void moveTrack(int delta, boolean fromCompletion, boolean skipRestartCheck) {
        if (tracks.isEmpty()) {
            return;
        }

        if (!fromCompletion) {
            consecutiveStreamFailures = 0; // Manual track change by user resets the loop protector
            cancelOfflineCrossfade();

            // Spotify/YT Music behavior for "previous" button:
            // - If current position > threshold OR it's not a double-tap: restart current song
            // - If pressed again within PREV_DOUBLE_TAP_WINDOW_MS: go to previous track
            if (delta == -1 && !skipRestartCheck) {
                long now = SystemClock.elapsedRealtime();
                boolean isDoubleTap = (now - lastPrevPressAtMs) < PREV_DOUBLE_TAP_WINDOW_MS;
                lastPrevPressAtMs = now;
                if (!isDoubleTap || currentSeconds > PREV_RESTART_THRESHOLD_SECONDS) {
                    // Restart current track
                    currentSeconds = 0;
                    isPlaying = true;
                    if (localExoMediaPlayer != null) {
                        try {
                            localExoMediaPlayer.seekTo(0);
                        } catch (Exception ignored) {
                            playCurrentTrack();
                        }
                    } else {
                        playCurrentTrack();
                    }
                    syncMiniStateWithPlaylist();
                    return;
                }
                // Double-tap within window: fall through to go to previous track
                lastPrevPressAtMs = 0L; // reset so a third press restarts again
            }

            // Going back from first track: pop global history
            if (delta == -1 && currentIndex == 0 && !globalPlaybackHistory.isEmpty()) {
                PlayerTrack histTrack = globalPlaybackHistory.pollFirst();
                if (histTrack != null && !TextUtils.isEmpty(histTrack.videoId)) {
                    tracks.clear();
                    tracks.add(histTrack);
                    currentIndex = 0;
                    isPlaying = true;
                    currentSeconds = 0;
                    playCurrentTrack();
                    syncMiniStateWithPlaylist();
                    return;
                }
            }
        }

        if (fromCompletion && repeatMode == REPEAT_MODE_ONE) {
            currentSeconds = 0;
            isPlaying = true;
            playCurrentTrack();
            scheduleAutoplayRecoveryForCurrentTrack();
            syncMiniStateWithPlaylist();
            return;
        }

        int targetIndex;
        if (fromCompletion) {
            if (currentIndex < tracks.size() - 1) {
                targetIndex = currentIndex + 1;
            } else if (repeatMode == REPEAT_MODE_ALL) {
                // Re-shuffle on wrap-around so each cycle plays in a different order
                if (shuffleEnabled && tracks.size() > 1) {
                    String lastVideoId = tracks.get(currentIndex).videoId;
                    randomizeQueueFromCurrentTrack(lastVideoId);
                }
                targetIndex = 0;
            } else {
                isPlaying = false;
                currentSeconds = Math.max(0, totalSeconds);
                updatePlayPauseIcon();
                updateMediaSessionState();
                syncMiniStateWithPlaylist();
                return;
            }
        } else {
            targetIndex = (currentIndex + delta + tracks.size()) % tracks.size();
        }

        currentIndex = targetIndex;
        isPlaying = true;
        currentSeconds = 0;
        playCurrentTrack();
        if (fromCompletion) {
            scheduleAutoplayRecoveryForCurrentTrack();
        } else {
            cancelAutoplayRecovery();
        }
        syncMiniStateWithPlaylist();
    }


    private void handleTrackEnded() {
        if (TextUtils.isEmpty(loadedVideoId)) {
            return;
        }

        long now = SystemClock.elapsedRealtime();
        if (TextUtils.equals(lastHandledEndedVideoId, loadedVideoId)
                && (now - lastHandledEndedAtMs) < 1200L) {
            return;
        }

        lastHandledEndedVideoId = loadedVideoId;
        lastHandledEndedAtMs = now;

        if (isAdded() && currentIndex >= 0 && currentIndex < tracks.size()) {
            PlayerTrack finished = tracks.get(currentIndex);
            PlayCountStore.incrementPlayCount(
                    requireContext(),
                    finished.videoId,
                    finished.title,
                    finished.artist,
                    finished.imageUrl,
                    null, null
            );
        }

        moveTrack(1, true);
    }

    private void scheduleAutoplayRecoveryForCurrentTrack() {
        cancelAutoplayRecovery();
        if (!isAdded() || usingOfflineSource || !isPlaying || tracks.isEmpty()) {
            return;
        }
        if (currentIndex < 0 || currentIndex >= tracks.size()) {
            return;
        }

        autoplayRecoveryVideoId = tracks.get(currentIndex).videoId;
        autoplayRecoveryRunnable = new Runnable() {
            @Override
            public void run() {
                autoplayRecoveryRunnable = null;
                if (!isAdded() || usingOfflineSource || !isPlaying || tracks.isEmpty()) {
                    return;
                }
                if (currentIndex < 0 || currentIndex >= tracks.size()) {
                    return;
                }

                String currentVideoId = tracks.get(currentIndex).videoId;
                if (!TextUtils.equals(currentVideoId, autoplayRecoveryVideoId)) {
                    return;
                }

                playCurrentTrack();
            }
        };
        localProgressHandler.postDelayed(autoplayRecoveryRunnable, AUTOPLAY_RECOVERY_DELAY_MS);
    }

    private void cancelAutoplayRecovery() {
        if (autoplayRecoveryRunnable != null) {
            localProgressHandler.removeCallbacks(autoplayRecoveryRunnable);
            autoplayRecoveryRunnable = null;
        }
        autoplayRecoveryVideoId = "";
    }


    private void schedulePlaybackRetry(@NonNull String videoId) {
        cancelPlaybackErrorRetry();
        playbackErrorRetryVideoId = videoId;
        playbackErrorRetryRunnable = new Runnable() {
            @Override
            public void run() {
                playbackErrorRetryRunnable = null;
                if (!isAdded() || usingOfflineSource || !isPlaying) {
                    return;
                }

                if (currentIndex < 0 || currentIndex >= tracks.size()) {
                    return;
                }

                String currentVideoId = tracks.get(currentIndex).videoId;
                if (!TextUtils.equals(currentVideoId, playbackErrorRetryVideoId)) {
                    return;
                }

                playCurrentTrack();
            }
        };

        localProgressHandler.postDelayed(playbackErrorRetryRunnable, TRACK_ERROR_RETRY_DELAY_MS);
    }

    private void cancelPlaybackErrorRetry() {
        if (playbackErrorRetryRunnable != null) {
            localProgressHandler.removeCallbacks(playbackErrorRetryRunnable);
            playbackErrorRetryRunnable = null;
        }
        playbackErrorRetryVideoId = "";
    }

    private void resetPlaybackErrorState() {
        cancelPlaybackErrorRetry();
        lastErroredVideoId = "";
        sameTrackErrorCount = 0;
        lastReresolveVideoId = null;
    }

    private void stopPlaybackAfterErrors(@NonNull String message) {
        cancelPlaybackErrorRetry();
        isPlaying = false;
        pauseRequestedByUser = false;
        updatePlayerSurfaceForSource();
        Log.e(TAG, "Playback stopped after errors. videoId=" + loadedVideoId + " message=" + message);
        updatePlayPauseIcon();
        updateMediaSessionState();
        persistPlaybackSnapshot(false);
        
    }

    private void togglePlayback() {
        if (usingOfflineSource && localExoMediaPlayer != null) {
            if (isPlaying) {
                cancelOfflineCrossfade();
                pauseRequestedByUser = true;
                try {
                    localExoMediaPlayer.pause();
                } catch (Exception ignored) {
                }
                stopLocalProgressTicker();
                isPlaying = false;
            } else {
                pauseRequestedByUser = false;
                isPlaying = true;
                try {
                    localExoMediaPlayer.start();
                    startLocalProgressTicker();
                } catch (Exception ignored) {
                    playCurrentTrack();
                }
            }

            updatePlayPauseIcon();
            updateMediaSessionState();
                syncMiniStateWithPlaylist();
            persistPlaybackSnapshot(false);
            return;
        }

        if (isPlaying) {
            pauseRequestedByUser = true;
            isPlaying = false;
            if (localExoMediaPlayer != null) {
                try {
                    localExoMediaPlayer.pause();
                } catch (Exception ignored) {
                }
            }
            stopLocalProgressTicker();
            updatePlayPauseIcon();
            updateMediaSessionState();
                syncMiniStateWithPlaylist();
            persistPlaybackSnapshot(false);
            return;
        }

        pauseRequestedByUser = false;
        isPlaying = true;
        playCurrentTrack();
        updatePlayPauseIcon();
        updateMediaSessionState();
        updateMediaNotification();
        syncMiniStateWithPlaylist();
        persistPlaybackSnapshot(false);
    }

    private void toggleShuffleMode() {
        setShuffleEnabled(!shuffleEnabled);
    }

    private void cycleRepeatMode() {
        if (repeatMode == REPEAT_MODE_OFF) {
            repeatMode = REPEAT_MODE_ALL;
        } else if (repeatMode == REPEAT_MODE_ALL) {
            repeatMode = REPEAT_MODE_ONE;
        } else {
            repeatMode = REPEAT_MODE_OFF;
        }
        persistPlaybackModePreferences();
        updatePlaybackModeButtons();
        updateMediaNotification();
        syncMiniStateWithPlaylist();
    }

    private void setShuffleEnabled(boolean enabled) {
        if (shuffleEnabled == enabled) {
            updatePlaybackModeButtons();
            return;
        }

        shuffleEnabled = enabled;
        if (shuffleEnabled) {
            randomizeQueueFromCurrentTrack();
        } else {
            restoreOriginalQueueOrder();
        }

        refreshNextUp();
        persistPlaybackModePreferences();
        persistPlaybackSnapshot(false);
        updatePlaybackModeButtons();
        updateMediaNotification();
        syncMiniStateWithPlaylist();
    }

    private int normalizeRepeatMode(int mode) {
        if (mode == REPEAT_MODE_ALL || mode == REPEAT_MODE_ONE || mode == REPEAT_MODE_OFF) {
            return mode;
        }
        return REPEAT_MODE_ALL;
    }

    private void loadPlaybackModesFromSettings() {
        boolean persistNormalized = false;

        if (settingsPrefs != null) {
            boolean hasShuffle = settingsPrefs.contains(CloudSyncManager.KEY_PLAYER_SHUFFLE_ENABLED);
            boolean hasRepeat = settingsPrefs.contains(CloudSyncManager.KEY_PLAYER_REPEAT_MODE);

            shuffleEnabled = settingsPrefs.getBoolean(CloudSyncManager.KEY_PLAYER_SHUFFLE_ENABLED, false);
            int storedRepeatMode = settingsPrefs.getInt(CloudSyncManager.KEY_PLAYER_REPEAT_MODE, REPEAT_MODE_ALL);
            repeatMode = normalizeRepeatMode(storedRepeatMode);

            persistNormalized = !hasShuffle || !hasRepeat || storedRepeatMode != repeatMode;
        } else {
            shuffleEnabled = false;
            repeatMode = REPEAT_MODE_ALL;
        }

        if (persistNormalized) {
            persistPlaybackModePreferences();
        }
    }

    private void persistPlaybackModePreferences() {
        if (settingsPrefs == null) {
            return;
        }

        int safeRepeatMode = normalizeRepeatMode(repeatMode);
        if (repeatMode != safeRepeatMode) {
            repeatMode = safeRepeatMode;
        }

        boolean storedShuffle = settingsPrefs.getBoolean(CloudSyncManager.KEY_PLAYER_SHUFFLE_ENABLED, false);
        int storedRepeat = normalizeRepeatMode(settingsPrefs.getInt(CloudSyncManager.KEY_PLAYER_REPEAT_MODE, REPEAT_MODE_ALL));
        if (storedShuffle == shuffleEnabled && storedRepeat == safeRepeatMode) {
            return;
        }

        settingsPrefs.edit()
                .putBoolean(CloudSyncManager.KEY_PLAYER_SHUFFLE_ENABLED, shuffleEnabled)
                .putInt(CloudSyncManager.KEY_PLAYER_REPEAT_MODE, safeRepeatMode)
                .apply();
    }

    private void updatePlaybackModeButtons() {
        if (!isAdded()) {
            return;
        }

        int iconColor = shuffleEnabled
            ? ContextCompat.getColor(requireContext(), R.color.stitch_blue)
            : ContextCompat.getColor(requireContext(), android.R.color.white);
        int white = ContextCompat.getColor(requireContext(), android.R.color.white);

        if (btnShuffle != null) {
            btnShuffle.setImageTintList(ColorStateList.valueOf(iconColor));
            btnShuffle.setAlpha(1f);
        }
        if (vPlayerShuffleIndicator != null) {
            vPlayerShuffleIndicator.setVisibility(shuffleEnabled ? View.VISIBLE : View.INVISIBLE);
        }

        if (btnRepeat != null) {
            btnRepeat.setImageTintList(ColorStateList.valueOf(white));
            btnRepeat.setAlpha(repeatMode == REPEAT_MODE_OFF ? 0.4f : 1f);
        }
        if (vPlayerRepeatIndicator != null) {
            vPlayerRepeatIndicator.setVisibility(repeatMode == REPEAT_MODE_ONE ? View.VISIBLE : View.INVISIBLE);
        }
    }

    private void playCurrentTrack() {
        if (!isAdded() || tracks.isEmpty()) {
            return;
        }

        // Sync mono audio setting before starting playback
        boolean mono = settingsPrefs != null && settingsPrefs.getBoolean(CloudSyncManager.KEY_MONO_AUDIO, false);
        MonoAudioProcessor.setEnabled(mono);

        // Safety: ensure no crossfade is active and no duplicate players exist
        cancelOfflineCrossfade();
        // Also ensure any lingering incoming player from a failed crossfade is released
        if (localCrossfadeIncomingPlayer != null) {
            releaseSingleExoMediaPlayer(localCrossfadeIncomingPlayer);
            localCrossfadeIncomingPlayer = null;
        }

        final PlayerTrack track = tracks.get(currentIndex);
        loadedVideoId = track.videoId;
        playCountRecordedForCurrentTrack = false;
        currentSeconds = 0;
        lastSeekTargetSeconds = -1;
        isRestoringPosition = false;

        long requestToken = ++activePlaybackRequestToken;

        PlaybackHistoryStore.Snapshot snapshot = PlaybackHistoryStore.load(requireContext());
        androidx.media3.exoplayer.ExoPlayer sharedExoPlayer = ExoPlayerManager.INSTANCE.getSharedExoPlayer();
        boolean isSharedPlayerActive = sharedExoPlayer != null 
                && (sharedExoPlayer.getPlaybackState() == androidx.media3.exoplayer.ExoPlayer.STATE_READY 
                    || sharedExoPlayer.getPlaybackState() == androidx.media3.exoplayer.ExoPlayer.STATE_BUFFERING)
                && snapshot.currentTrack() != null 
                && snapshot.currentTrack().videoId.equals(track.videoId);

        if (isSharedPlayerActive && localExoMediaPlayer == null) {
            PlaybackLoadingBus.clearLoading();
            bindCurrentTrackInternal(true, false); // Keep current time and UI intact
            usingOfflineSource = true;
            localSourcePreparing = false;
            localExoMediaPlayer = new ExoMediaPlayer(requireContext().getApplicationContext(), sharedExoPlayer);
            localExoMediaPlayer.setOnPreparedListener(mp -> {
                localSourcePreparing = false;
            });
            localExoMediaPlayer.setOnCompletionListener(mp -> handleLocalPlaybackCompletion());
            localExoMediaPlayer.setOnErrorListener((mp, what, extra) -> {
                Log.e(TAG, "Local ExoMediaPlayer error (seamless): " + what + ", " + extra);
                handlePlaybackError();
                return true;
            });
            updatePlayPauseIcon();
            startLocalProgressTicker();
            lastPlaybackStartRequestAtMs = android.os.SystemClock.elapsedRealtime();
            return;
        }

        // Bind metadata. forceZero=true resets UI to 0, but we restore resume position after.
        // NOTE: Do NOT call showPlayerArtworkLoadingState() here — keep old cover visible
        // until the new one loads via Glide, preventing the black flash.
        bindCurrentTrackInternal(false, true);
        // Reset state BEFORE restoring resumeSeconds
        cancelOfflineCrossfade();
        resetPlaybackStateForNewTrack();
        
        lastPlaybackStartRequestAtMs = SystemClock.elapsedRealtime();

        cancelPlaybackErrorRetry();
        if (hasPendingStreamResolution() && TextUtils.equals(loadedVideoId, track.videoId)) {
            return;
        }
        cancelPendingStreamResolver();

        // requestToken already incremented above
        final long token = activePlaybackRequestToken;

        // ✅ FAST-PATH: check offline on the main thread immediately — no executor queue wait.
        // OfflineAudioStore.hasOfflineAudio is a simple SharedPrefs/file existence check.
        boolean hasOfflineLocal = LocalFilesStore.isLocalVideoId(track.videoId)
                || OfflineAudioStore.hasOfflineAudio(requireContext(), track.videoId);
        if (hasOfflineLocal) {
            PlaybackLoadingBus.clearLoading();
            List<String> directSources = buildDirectSourceCandidates(track);
            attemptPlaybackFromSources(track, directSources, 0, requestToken, 0);
            return;
        }

        // GAPLESS: if pre-buffered player is ready for this track, use it instantly
        if (gaplessPreBufferedPlayer != null && TextUtils.equals(gaplessPreBufferedVideoId, track.videoId)) {
            ExoMediaPlayer preBuffered = gaplessPreBufferedPlayer;
            gaplessPreBufferedPlayer = null;
            gaplessPreBufferedVideoId = "";
            gaplessPreBufferTriggered = false;
            adoptGaplessPlayer(track, preBuffered, requestToken);
            return;
        }
        cancelGaplessPreBuffer();

        // Not offline — check prefetch (main-thread fields), then resolve online via executor.
        if (TextUtils.equals(track.videoId, prefetchedNextVideoId) && !TextUtils.isEmpty(prefetchedNextUrl)) {
            String url = prefetchedNextUrl;
            prefetchedNextVideoId = null;
            prefetchedNextUrl = null;
            startMediaPlaybackFromSource(track, url, requestToken, () -> {
                resolveAndPlayOnlineTrack(track, requestToken);
            });
            return;
        }
        resolveAndPlayOnlineTrack(track, requestToken);
    }

    private void adoptGaplessPlayer(@NonNull PlayerTrack track, @NonNull ExoMediaPlayer preBuffered, long requestToken) {
        // Release current player
        releaseLocalExoMediaPlayer();
        usingOfflineSource = false;
        localSourcePreparing = false;

        localExoMediaPlayer = preBuffered;
        preBuffered.isCrossfadeComponent = false;
        preBuffered.setVolume(1f, 1f);
        preBuffered.setOnCompletionListener(mp -> handleLocalPlaybackCompletion());
        preBuffered.setOnErrorListener((mp, what, extra) -> {
            Log.e(TAG, "adoptGaplessPlayer: error what=" + what + " extra=" + extra);
            handlePlaybackError();
            return true;
        });

        try {
            preBuffered.start();
        } catch (Exception e) {
            Log.e(TAG, "adoptGaplessPlayer: start failed", e);
            releaseLocalExoMediaPlayer();
            resolveAndPlayOnlineTrack(track, requestToken);
            return;
        }

        int durationMs;
        try {
            durationMs = Math.max(0, preBuffered.getDuration());
        } catch (Exception ignored) {
            durationMs = 0;
        }
        totalSeconds = durationMs > 0 ? Math.max(1, durationMs / 1000) : Math.max(1, parseDurationSeconds(track.duration));

        isPlaying = true;
        updatePlayPauseIcon();
        startLocalProgressTicker();
        updateMediaSessionMetadata();
        updateMediaSessionState();
        syncMiniStateWithPlaylist();
        persistPlaybackSnapshot(false);
        prefetchNextTrackStream();
    }

    private void handleLocalPlaybackCompletion() {
        if (!isAdded()) {
            return;
        }
        if (localCrossfadeInProgress && localCrossfadeIncomingPlayer != null) {
            return;
        }
        stopLocalProgressTicker();
        handleTrackEnded();
    }

    private void handlePlaybackError() {
        if (!isAdded()) {
            return;
        }
        tryReresolveOrSkipCurrentTrack("Error en reproductor compartido. Reintentando.", false);
    }

    private void resolveAndPlayOnlineTrack(@NonNull PlayerTrack track, long requestToken) {
        resolveAndPlayOnlineTrack(track, requestToken, false);
    }

    private void resolveAndPlayOnlineTrack(@NonNull PlayerTrack track, long requestToken, boolean forceAlternativeClient) {
        if (!isNetworkAvailable()) {
            markPlaybackUnavailable("Sin conexión a internet.");
            return;
        }

        PlaybackLoadingBus.notifyLoadingStarted(track.videoId);
        pendingStreamResolverFuture = streamResolverExecutor.submit(() -> {
            String resolvedUrl = forceAlternativeClient
                    ? InnertubeResolver.resolveStreamUrl(requireContext(), track.videoId, true)
                    : InnertubeResolver.resolveStreamUrl(requireContext(), track.videoId);

            localProgressHandler.post(() -> {
                if (requestToken != activePlaybackRequestToken || !isAdded()) return;
                pendingStreamResolverFuture = null;

                if (!TextUtils.isEmpty(resolvedUrl)) {
                    startMediaPlaybackFromSource(track, resolvedUrl, requestToken, () -> {
                        InnertubeResolver.invalidate(track.videoId);
                        tryReresolveOrSkipCurrentTrack("Fallo de reproducción directa: re-resolviendo.", false);
                    });
                } else {
                    tryReresolveOrSkipCurrentTrack("No se pudo resolver el stream directo. Reintentando.", false);
                }
            });
        });
    }

    private void prefetchNextTrackStream() {
        if (tracks.size() <= 1) return;

        int nextIndex = (currentIndex + 1) % tracks.size();
        PlayerTrack nextTrack = tracks.get(nextIndex);
        if (nextTrack == null || TextUtils.isEmpty(nextTrack.videoId)) return;

        // Skip if local device file or already offline
        if (LocalFilesStore.isLocalVideoId(nextTrack.videoId)) return;
        if (isAdded() && OfflineAudioStore.hasOfflineAudio(requireContext(), nextTrack.videoId)) {
            return;
        }

        // Skip if already prefetched
        if (TextUtils.equals(nextTrack.videoId, prefetchedNextVideoId)) return;

        streamResolverExecutor.submit(() -> {
            String url = InnertubeResolver.resolveStreamUrl(requireContext(), nextTrack.videoId);
            if (!TextUtils.isEmpty(url)) {
                prefetchedNextVideoId = nextTrack.videoId;
                prefetchedNextUrl = url;
            }
        });
    }

    private boolean isGaplessPlaybackEnabled() {
        return settingsPrefs != null
                && settingsPrefs.getBoolean(CloudSyncManager.KEY_GAPLESS_PLAYBACK, true);
    }

    private void maybeStartGaplessPreBuffer(int positionMs, int durationMs) {
        if (!isAdded()
                || !isGaplessPlaybackEnabled()
                || localExoMediaPlayer == null
                || localSourcePreparing
                || localCrossfadeInProgress
                || gaplessPreBufferTriggered
                || !isPlaying
                || usingOfflineSource) {
            return;
        }

        // Trigger after 5s of cumulative listening (not position-based)
        if (accumulatedListenMs < GAPLESS_PRE_BUFFER_LISTEN_THRESHOLD_MS) {
            return;
        }

        // Determine next track
        int nextIndex = resolveNextIndexForCompletionCrossfade();
        if (nextIndex < 0 || nextIndex >= tracks.size()) {
            return;
        }

        PlayerTrack nextTrack = tracks.get(nextIndex);
        if (nextTrack == null || TextUtils.isEmpty(nextTrack.videoId)) {
            return;
        }

        // Skip if local device file or already offline (offline playback is already instant)
        if (LocalFilesStore.isLocalVideoId(nextTrack.videoId)) return;
        if (OfflineAudioStore.hasOfflineAudio(requireContext(), nextTrack.videoId)) {
            return;
        }

        // Skip if already pre-buffered for this track
        if (gaplessPreBufferedPlayer != null && TextUtils.equals(gaplessPreBufferedVideoId, nextTrack.videoId)) {
            return;
        }

        gaplessPreBufferTriggered = true;

        // Find a URL: use prefetched if available, otherwise resolve in background
        String url = null;
        if (TextUtils.equals(nextTrack.videoId, prefetchedNextVideoId) && !TextUtils.isEmpty(prefetchedNextUrl)) {
            url = prefetchedNextUrl;
        }

        if (!TextUtils.isEmpty(url)) {
            prepareGaplessPlayer(nextTrack, url);
        } else {
            streamResolverExecutor.submit(() -> {
                String resolved = InnertubeResolver.resolveStreamUrl(requireContext(), nextTrack.videoId);
                localProgressHandler.post(() -> {
                    if (!isAdded() || TextUtils.isEmpty(resolved)) return;
                    prepareGaplessPlayer(nextTrack, resolved);
                });
            });
        }
    }

    private void prepareGaplessPlayer(@NonNull PlayerTrack nextTrack, @NonNull String url) {
        if (!isAdded()) return;

        // Release any existing pre-buffer player
        if (gaplessPreBufferedPlayer != null) {
            releaseSingleExoMediaPlayer(gaplessPreBufferedPlayer);
            gaplessPreBufferedPlayer = null;
            gaplessPreBufferedVideoId = "";
        }

        Context appCtx = requireContext().getApplicationContext();
        ExoMediaPlayer player = new ExoMediaPlayer(appCtx, true); // low buffer for pre-buffer
        player.isCrossfadeComponent = true; // Mark so it doesn't interfere with media session

        try {
            player.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build());

            Map<String, String> headers = new HashMap<>();
            headers.put("User-Agent", STREAM_HTTP_USER_AGENT);
            headers.put("Accept", "*/*");
            Map<String, String> authHeaders = InnertubeResolver.getHeadersFor(nextTrack.videoId);
            if (authHeaders != null) {
                headers.putAll(authHeaders);
            }
            player.setDataSource(appCtx, Uri.parse(url), headers);
            player.setVolume(0f, 0f);

            player.setOnPreparedListener(mp -> {
                // Prepared and buffering — keep silent, ready to use
                gaplessPreBufferedPlayer = mp;
                gaplessPreBufferedVideoId = nextTrack.videoId;
            });

            player.setOnErrorListener((mp, what, extra) -> {
                // Pre-buffer failed — silent cleanup, normal flow will handle it
                if (gaplessPreBufferedPlayer == mp) {
                    gaplessPreBufferedPlayer = null;
                    gaplessPreBufferedVideoId = "";
                }
                releaseSingleExoMediaPlayer(mp);
                return true;
            });

            player.prepareAsync();
        } catch (Exception e) {
            releaseSingleExoMediaPlayer(player);
        }
    }

    private void cancelGaplessPreBuffer() {
        gaplessPreBufferTriggered = false;
        if (gaplessPreBufferedPlayer != null) {
            releaseSingleExoMediaPlayer(gaplessPreBufferedPlayer);
            gaplessPreBufferedPlayer = null;
            gaplessPreBufferedVideoId = "";
        }
    }

    private void attemptPlaybackFromSources(
            @NonNull PlayerTrack track,
            @NonNull List<String> sources,
            int sourceIndex,
            long requestToken,
            int sourceRetryCount
    ) {
        if (!isAdded()) {
            return;
        }

        if (requestToken != activePlaybackRequestToken
                || !TextUtils.equals(track.videoId, loadedVideoId)) {
            return;
        }

        if (sourceIndex >= sources.size()) {
            if (isNetworkAvailable() && tryReresolveOrSkipCurrentTrack("No se encontro audio directo.", false)) {
                return;
            }

            if (!isNetworkAvailable()
                    && !OfflineAudioStore.hasValidatedOfflineAudio(requireContext(), track.videoId, track.duration)) {
                markPlaybackUnavailable("Sin internet y sin descarga offline para esta canción.");
            } else {
                markPlaybackUnavailable("No se encontro una fuente de audio para esta canción.");
            }
            return;
        }

        String source = sources.get(sourceIndex);
        startMediaPlaybackFromSource(
                track,
                source,
                requestToken,
            () -> {
                if (sourceRetryCount < MAX_PLAYBACK_SOURCE_RETRY) {
                Log.w(TAG,
                    "attemptPlaybackFromSources: retrying same source sourceIndex=" + sourceIndex
                        + " source=" + maskUrlForLog(source)
                        + " nextRetry=" + (sourceRetryCount + 1)
                        + " requestToken=" + requestToken);

                localProgressHandler.postDelayed(
                    () -> attemptPlaybackFromSources(
                        track,
                        sources,
                        sourceIndex,
                        requestToken,
                        sourceRetryCount + 1
                    ),
                    PLAYBACK_SOURCE_RETRY_DELAY_MS
                );
                return;
                }

                attemptPlaybackFromSources(track, sources, sourceIndex + 1, requestToken, 0);
            }
        );
    }

    private void startMediaPlaybackFromSource(
            @NonNull PlayerTrack track,
            @NonNull String source,
            long requestToken,
            @NonNull Runnable onFailure
    ) {
        final boolean networkSource = isHttpStreamSource(source) || isInnertubeSource(source);
        stopLocalProgressTicker();
        releaseLocalExoMediaPlayer();
        usingOfflineSource = !networkSource;
        currentSourceIsVideo = !networkSource && isVideoSource(source);
        currentVideoFilePath = currentSourceIsVideo ? source : null;
        localSourcePreparing = true;
        updatePlayerSurfaceForSource();

        Context playbackAppContext = getPlaybackAppContext();
        if (playbackAppContext == null) {
            Log.w(TAG, "startMediaPlaybackFromSource: missing app context, aborting playback start");
            localSourcePreparing = false;
            usingOfflineSource = false;
            onFailure.run();
            return;
        }

        // Try to use shared ExoPlayer to reduce startup latency
        ExoPlayer sharedExoPlayer = ExoPlayerManager.INSTANCE.getSharedExoPlayer();
        ExoMediaPlayer player;
        if (sharedExoPlayer != null) {
            try {
                player = new ExoMediaPlayer(playbackAppContext, sharedExoPlayer);
            } catch (Exception e) {
                Log.w(TAG, "Failed to use shared ExoPlayer, falling back", e);
                player = new ExoMediaPlayer(playbackAppContext);
            }
        } else {
            player = new ExoMediaPlayer(playbackAppContext);
        }
        localExoMediaPlayer = player;
        player.setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build());

        // Re-affirm EQ on the global session (session 0) — no per-player sessionId needed.
        try {
            AudioEffectsService.sendApply(playbackAppContext);
        } catch (Exception ignored) {
        }

        // Route video to VideoSurfaceRouter (handles detection + reparenting)
        if (currentSourceIsVideo && currentVideoFilePath != null) {
            videoRouter.onTrackStarted(player, currentVideoFilePath, track.videoId);
        } else {
            videoRouter.onTrackStarted(player, null, track.videoId);
        }

        player.setOnPreparedListener(mp -> {
            cancelSourcePrepareTimeout();
            localSourcePreparing = false;

            if (!isAdded()
                    || localExoMediaPlayer != mp
                    || requestToken != activePlaybackRequestToken
                    || !TextUtils.equals(track.videoId, loadedVideoId)) {
                releaseSingleExoMediaPlayer(mp);
                return;
            }

            int durationMs;
            try {
                durationMs = Math.max(0, mp.getDuration());
            } catch (Exception ignored) {
                durationMs = 0;
            }

            int resolvedTotal = durationMs > 0
                    ? Math.max(1, durationMs / 1000)
                    : Math.max(1, parseDurationSeconds(track.duration));

            totalSeconds = resolvedTotal;

            if (isPlaying) {
                try {
                    mp.setVolume(1f, 1f);
                    mp.start();
                    Log.d(TAG, "[PLAYBACK_FLOW] mp.start() called, AUDIO PLAYING for videoId=" + track.videoId);
                    PlaybackLoadingBus.notifyAudioConfirmed(track.videoId);
                    consecutiveStreamFailures = 0; // Reset counter on successful playback
                    audioTrackReinitToken = -1;
                    startLocalProgressTicker();

                    // Video detection is handled by VideoSurfaceRouter.onTrackStarted
                    if (!currentSourceIsVideo) {
                        if (pbVideoLoading != null) pbVideoLoading.setVisibility(View.GONE);
                    }

                    // Start pre-fetching the next track once this one is successfully playing
                    prefetchNextTrackStream();
                } catch (Exception startError) {
                    Log.e(TAG, "onPrepared: start failed for videoId=" + track.videoId, startError);
                    if (localExoMediaPlayer == mp) {
                        releaseLocalExoMediaPlayer();
                        usingOfflineSource = false;
                    } else {
                        releaseSingleExoMediaPlayer(mp);
                    }
                    onFailure.run();
                    return;
                }
            }

            setPlaybackUiForPreparedSource();
        });

        player.setOnCompletionListener(mp -> {
            if (!isAdded()) {
                return;
            }
            if (requestToken != activePlaybackRequestToken) {
                return;
            }
            if (localCrossfadeInProgress && localCrossfadeIncomingPlayer != null) {
                return;
            }
            stopLocalProgressTicker();
            handleTrackEnded();
        });

        player.setOnErrorListener((mp, what, extra) -> {
            cancelSourcePrepareTimeout();
            localSourcePreparing = false;
            Log.e(TAG, "onError: what=" + what
                    + " extra=" + extra
                    + " source=" + maskUrlForLog(source)
                    + " videoId=" + track.videoId
                    + " token=" + requestToken);

            if (localExoMediaPlayer == mp) {
                stopLocalProgressTicker();
                releaseLocalExoMediaPlayer();
                usingOfflineSource = false;
            } else {
                releaseSingleExoMediaPlayer(mp);
            }

            // AudioTrack init failure (status -12 / ENOMEM): reinitialize shared player
            // to force release stale AudioTrack resources held by the OS.
            // Only attempt reinit ONCE per playback token to prevent infinite loops on
            // low-memory devices where AudioFlinger cannot reclaim resources quickly.
            if (what == 5001 && isAdded()) {
                if (audioTrackReinitToken == requestToken) {
                    Log.w(TAG, "AudioTrack init failed again after reinit — falling through to next source/track");
                    // Fall through to normal onFailure handling below
                } else {
                    audioTrackReinitToken = requestToken;
                    Log.w(TAG, "AudioTrack init failed — reinitializing shared ExoPlayer");
                    ExoPlayerManager.INSTANCE.reinitialize(requireContext().getApplicationContext());
                    // Give the OS time to reclaim AudioTrack resources before retrying
                    // (3s is needed on low-end devices like SM-A032M with 2GB RAM)
                    if (requestToken == activePlaybackRequestToken) {
                        localProgressHandler.postDelayed(() -> {
                            if (!isAdded() || requestToken != activePlaybackRequestToken) return;
                            onFailure.run();
                        }, 3000L);
                    }
                    return true;
                }
            }

            // Codec crash (DEAD_OBJECT from mediaserver): error code 4003.
            // The system codec process died — this is external to the app. Reinitialize
            // the shared ExoPlayer and resume from the saved position instead of restarting
            // from 0 via onFailure (which would invalidate the URL and lose the position).
            if (what == 4003 && isAdded()) {
                final int savedPositionMs = currentSeconds * 1000;
                Log.w(TAG, "Codec DEAD_OBJECT (4003) — reinitializing ExoPlayer and resuming from "
                        + savedPositionMs + "ms for videoId=" + track.videoId);
                ExoPlayerManager.INSTANCE.reinitialize(requireContext().getApplicationContext());
                if (requestToken == activePlaybackRequestToken) {
                    localProgressHandler.postDelayed(() -> {
                        if (!isAdded() || requestToken != activePlaybackRequestToken) return;
                        // Resume from saved position: temporarily override currentSeconds so
                        // the seek is applied after prepare in attemptPlaybackFromSources.
                        int resumeSec = savedPositionMs / 1000;
                        currentSeconds = resumeSec;
                        onFailure.run();
                    }, 800L);
                }
                return true;
            }

            if (requestToken != activePlaybackRequestToken || !isAdded()) {
                return true;
            }

            onFailure.run();
            return true;
        });

        try {
            if ((isHttpStreamSource(source) || isInnertubeSource(source)) && isAdded()) {
                Map<String, String> headers = new HashMap<>();
                headers.put("User-Agent", STREAM_HTTP_USER_AGENT);
                headers.put("Accept", "*/*");
                Map<String, String> authHeaders = InnertubeResolver.getHeadersFor(track.videoId);
                if (authHeaders != null) {
                    headers.putAll(authHeaders);
                }
                player.setDataSource(playbackAppContext, Uri.parse(source), headers);
            } else if (source.startsWith("content://") && isAdded()) {
                player.setDataSource(playbackAppContext, Uri.parse(source), null);
            } else {
                player.setDataSource(source);
            }
            player.prepareAsync();
            scheduleSourcePrepareTimeout(track, requestToken, player, onFailure);
        } catch (IllegalStateException ise) {
            cancelSourcePrepareTimeout();
            localSourcePreparing = false;
            Log.e(TAG, "startMediaPlaybackFromSource: ExoPlayer thread is dead, reinitializing manager", ise);
            ExoPlayerManager.INSTANCE.reinitialize(playbackAppContext);
            onFailure.run();
        } catch (Exception e) {
            cancelSourcePrepareTimeout();
            localSourcePreparing = false;
            Log.e(TAG, "startMediaPlaybackFromSource: setDataSource/prepareAsync failed for source="
                    + maskUrlForLog(source), e);
            if (localExoMediaPlayer == player) {
                releaseLocalExoMediaPlayer();
                usingOfflineSource = false;
            } else {
                releaseSingleExoMediaPlayer(player);
            }
            onFailure.run();
            return;
        }

        notifyPlaybackStateChanged();
    }

    private void notifyPlaybackStateChanged() {
        updatePlayPauseIcon();
        updateMediaSessionMetadata();
        updateMediaSessionState();
        updateMediaNotification();
        syncMiniStateWithPlaylist();
        persistPlaybackSnapshot(false);
    }

    private void setPlaybackUiForPreparedSource() {
        tvCurrentTime.setText(formatSeconds(currentSeconds));
        tvTotalTime.setText(formatSeconds(totalSeconds));
        int progress = Math.round((Math.max(0, currentSeconds) / (float) Math.max(1, totalSeconds)) * 1000f);
        sbPlaybackProgress.setProgress(Math.max(0, Math.min(1000, progress)));
        notifyPlaybackStateChanged();
    }

    private void resetPlaybackStateForNewTrack() {
        currentSeconds = 0;
        accumulatedListenMs = 0;
        if (tvCurrentTime != null) tvCurrentTime.setText("00:00");
        if (tvTotalTime != null) tvTotalTime.setText("--:--");
        if (sbPlaybackProgress != null) sbPlaybackProgress.setProgress(0);
        
        notifyPlaybackStateChanged();
        
        // showPlayerArtworkLoadingState() moved to playCurrentTrack() to avoid race conditions
    }

    private void releaseSingleExoMediaPlayer(@Nullable ExoMediaPlayer player) {
        if (player == null) {
            return;
        }
        try {
            player.stop();
        } catch (Exception ignored) {
        }
        try {
            player.release();
        } catch (Exception ignored) {
        }
    }

    @NonNull
    private List<String> buildDirectSourceCandidates(@NonNull PlayerTrack track) {
        LinkedHashSet<String> orderedSources = new LinkedHashSet<>();

        // Local device files — use content URI directly
        if (isAdded() && LocalFilesStore.isLocalVideoId(track.videoId)) {
            String contentUri = LocalFilesStore.getContentUriForVideoId(requireContext(), track.videoId);
            if (contentUri != null && !contentUri.isEmpty()) {
                orderedSources.add(contentUri);
            }
            return new ArrayList<>(orderedSources);
        }

        if (isAdded()
                && OfflineAudioStore.hasValidatedOfflineAudio(requireContext(), track.videoId, track.duration)) {
            File file = OfflineAudioStore.getExistingOfflineAudioFile(requireContext(), track.videoId);
            if (file.isFile() && file.length() > 0L) {
                orderedSources.add(file.getAbsolutePath());
            }
        }
        return new ArrayList<>(orderedSources);
    }

    private void cancelPendingStreamResolver() {
        if (pendingStreamResolverFuture != null) {
            pendingStreamResolverFuture.cancel(true);
            pendingStreamResolverFuture = null;
        }
    }

    private boolean hasPendingStreamResolution() {
        return pendingStreamResolverFuture != null && !pendingStreamResolverFuture.isDone();
    }

    private boolean tryReresolveOrSkipCurrentTrack(@NonNull String reason, boolean forceAttempt) {
        if (!isAdded() || !isNetworkAvailable()) {
            return false;
        }
        
        consecutiveStreamFailures++;
        if (consecutiveStreamFailures >= 6 && !forceAttempt) {
            Log.e(TAG, "Too many consecutive failures (" + consecutiveStreamFailures + "). Pausing playback to avoid API bans.");
            markPlaybackUnavailable("Múltiples fallos consecutivos. Reproducción pausada para evitar bloqueos del servidor.");
            return true;
        }

        if (tracks.isEmpty() || currentIndex < 0 || currentIndex >= tracks.size()) {
            return false;
        }

        PlayerTrack track = tracks.get(currentIndex);
        if (TextUtils.isEmpty(track.videoId)) {
            return false;
        }

        // If this video already consumed its single re-resolution retry, skip to next track.
        if (TextUtils.equals(lastReresolveVideoId, track.videoId)) {
            Log.w(TAG, "tryYouTubeFallbackForCurrentTrack: re-resolve already attempted for "
                    + track.videoId + ", skipping to next. reason=" + reason);
            lastReresolveVideoId = null;
            handleTrackEnded();
            return true;
        }

        Log.w(TAG, "tryYouTubeFallbackForCurrentTrack: invalidating URL cache and re-resolving. reason="
                + reason + " videoId=" + track.videoId);
        lastReresolveVideoId = track.videoId;

        // Drop cached URL so the next resolveStreamUrl fetches a fresh one.
        InnertubeResolver.invalidate(track.videoId);

        cancelSourcePrepareTimeout();
        cancelPendingStreamResolver();
        stopLocalProgressTicker();
        releaseLocalExoMediaPlayer();
        usingOfflineSource = false;

        // Clear any prefetched URL for this track (it may also be stale).
        if (TextUtils.equals(track.videoId, prefetchedNextVideoId)) {
            prefetchedNextVideoId = null;
        }

        // Retry forcing the alternative client chain (TVHTML5_SIMPLYEMBEDDED first) to
        // bypass LOGIN_REQUIRED / age-gated responses from ANDROID_MUSIC.
        resolveAndPlayOnlineTrack(track, activePlaybackRequestToken, true);
        return true;
    }

    private boolean tryResolveAudiusAndReplay(@NonNull PlayerTrack track, long requestToken) {
        if (!isAdded() || !isNetworkAvailable()) {
            return false;
        }
        if (audiusFallbackAttemptedVideoIds.contains(track.videoId)) {
            return false;
        }

        audiusFallbackAttemptedVideoIds.add(track.videoId);
        
        Log.w(TAG, "tryResolveAudiusAndReplay: start videoId=" + track.videoId + " requestToken=" + requestToken);

        cancelPendingStreamResolver();
        pendingStreamResolverFuture = streamResolverExecutor.submit(() -> {
            String resolved = fetchAudiusStreamUrl(track);

            localProgressHandler.post(() -> {
                pendingStreamResolverFuture = null;
                if (!isAdded()) {
                    return;
                }
                if (requestToken != activePlaybackRequestToken
                        || !TextUtils.equals(track.videoId, loadedVideoId)) {
                    return;
                }

                if (TextUtils.isEmpty(resolved)) {
                    stopPlaybackAfterErrors("No se pudo reproducir en YouTube ni encontrar audio alternativo gratuito.");
                    return;
                }

                resetPlaybackErrorState();
                playerEngineRecoveryAttempts = 0;
                startMediaPlaybackFromSource(
                        track,
                        resolved,
                        requestToken,
                        () -> stopPlaybackAfterErrors("No se pudo abrir la fuente de audio alternativa.")
                );
            });
        });
        return true;
    }

    @NonNull
    private String fetchAudiusStreamUrl(@NonNull PlayerTrack track) {
        try {
            String query = buildAudiusSearchQuery(track);
            if (TextUtils.isEmpty(query)) {
                Log.w(TAG, "fetchAudiusStreamUrl: empty query for videoId=" + track.videoId);
                return "";
            }
            Uri searchUri = Uri.parse(AUDIUS_API_BASE_URL)
                    .buildUpon()
                    .appendPath("tracks")
                    .appendPath("search")
                    .appendQueryParameter("query", query)
                    .appendQueryParameter("limit", String.valueOf(AUDIUS_SEARCH_LIMIT))
                    .appendQueryParameter("app_name", AUDIUS_APP_NAME)
                    .build();

            String searchBody = readTextResponse(searchUri.toString(), "application/json");
            if (TextUtils.isEmpty(searchBody)) {
                return "";
            }

            JSONObject root = new JSONObject(searchBody);
            JSONArray data = root.optJSONArray("data");
            if (data == null || data.length() == 0) {
                Log.w(TAG, "fetchAudiusStreamUrl: no results for query='" + query + "'");
                return "";
            }
            String selectedTrackId = "";
            int selectedScore = Integer.MIN_VALUE;
            for (int i = 0; i < data.length(); i++) {
                JSONObject item = data.optJSONObject(i);
                if (item == null) {
                    continue;
                }

                String candidateId = item.optString("id", "").trim();
                if (TextUtils.isEmpty(candidateId)) {
                    continue;
                }

                String candidateTitle = item.optString("title", "");
                JSONObject userObj = item.optJSONObject("user");
                String candidateArtist = userObj == null ? "" : userObj.optString("name", "");

                int score = scoreAudiusCandidate(track, candidateTitle, candidateArtist);
                if (score > selectedScore) {
                    selectedScore = score;
                    selectedTrackId = candidateId;
                }
            }

            if (TextUtils.isEmpty(selectedTrackId)) {
                Log.w(TAG, "fetchAudiusStreamUrl: no candidate selected for query='" + query + "'");
                return "";
            }
            Uri streamUri = Uri.parse(AUDIUS_API_BASE_URL)
                    .buildUpon()
                    .appendPath("tracks")
                    .appendPath(selectedTrackId)
                    .appendPath("stream")
                    .appendQueryParameter("app_name", AUDIUS_APP_NAME)
                    .build();
            return streamUri.toString();
        } catch (Exception e) {
            Log.e(TAG, "fetchAudiusStreamUrl: failed for videoId=" + track.videoId, e);
            return "";
        }
    }

    @NonNull
    private String buildAudiusSearchQuery(@NonNull PlayerTrack track) {
        String title = track.title == null ? "" : track.title.trim();
        String artist = track.artist == null ? "" : track.artist.trim();
        if (TextUtils.isEmpty(title) && TextUtils.isEmpty(artist)) {
            return "";
        }
        if (TextUtils.isEmpty(artist)) {
            return title;
        }
        if (TextUtils.isEmpty(title)) {
            return artist;
        }
        return title + " " + artist;
    }

    private int scoreAudiusCandidate(
            @NonNull PlayerTrack track,
            @NonNull String candidateTitle,
            @NonNull String candidateArtist
    ) {
        String wantedTitle = normalizeForMatch(track.title);
        String wantedArtist = normalizeForMatch(track.artist);
        String foundTitle = normalizeForMatch(candidateTitle);
        String foundArtist = normalizeForMatch(candidateArtist);

        int score = 0;
        if (!TextUtils.isEmpty(wantedTitle) && !TextUtils.isEmpty(foundTitle)) {
            if (TextUtils.equals(wantedTitle, foundTitle)) {
                score += 120;
            } else if (foundTitle.contains(wantedTitle) || wantedTitle.contains(foundTitle)) {
                score += 70;
            }
        }

        if (!TextUtils.isEmpty(wantedArtist) && !TextUtils.isEmpty(foundArtist)) {
            if (TextUtils.equals(wantedArtist, foundArtist)) {
                score += 70;
            } else if (foundArtist.contains(wantedArtist) || wantedArtist.contains(foundArtist)) {
                score += 45;
            }
        }

        if (score == 0) {
            score = Math.max(0, foundTitle.length() - 2);
        }
        return score;
    }

    @NonNull
    private String normalizeForMatch(@Nullable String value) {
        if (value == null) {
            return "";
        }
        return value
                .toLowerCase(Locale.US)
                .replaceAll("[^a-z0-9]+", " ")
                .trim();
    }

    private boolean isHttpStreamSource(@NonNull String source) {
        return source.startsWith("https://") || source.startsWith("http://");
    }

    private boolean isInnertubeSource(@NonNull String source) {
        return false;
    }

    private void scheduleSourcePrepareTimeout(
            @NonNull PlayerTrack track,
            long requestToken,
            @NonNull ExoMediaPlayer player,
            @NonNull Runnable onFailure
    ) {
        cancelSourcePrepareTimeout();
        sourcePrepareTimeoutRunnable = () -> {
            if (!isAdded()) {
                return;
            }
            if (localExoMediaPlayer != player
                    || requestToken != activePlaybackRequestToken
                    || !TextUtils.equals(track.videoId, loadedVideoId)) {
                return;
            }

            Log.e(TAG, "scheduleSourcePrepareTimeout: prepare timeout reached for videoId=" + track.videoId
                    + " token=" + requestToken
                    + " timeoutMs=" + SOURCE_PREPARE_TIMEOUT_MS);
        localSourcePreparing = false;
            stopLocalProgressTicker();
            releaseLocalExoMediaPlayer();
            onFailure.run();
        };
        localProgressHandler.postDelayed(sourcePrepareTimeoutRunnable, SOURCE_PREPARE_TIMEOUT_MS);
    }

    private void cancelSourcePrepareTimeout() {
        if (sourcePrepareTimeoutRunnable != null) {
            localProgressHandler.removeCallbacks(sourcePrepareTimeoutRunnable);
            sourcePrepareTimeoutRunnable = null;
        }
    }

    private void markPlaybackUnavailable(@NonNull String message) {
        Log.e(TAG, "markPlaybackUnavailable: " + message
            + " loadedVideoId=" + loadedVideoId
            + " activeToken=" + activePlaybackRequestToken);
        cancelSourcePrepareTimeout();
        stopLocalProgressTicker();
        releaseLocalExoMediaPlayer();
        usingOfflineSource = false;
        pauseRequestedByUser = true;
        isPlaying = false;
        updatePlayerSurfaceForSource();

        updatePlayPauseIcon();
        updateMediaSessionMetadata();
        updateMediaSessionState();
        syncMiniStateWithPlaylist();
        persistPlaybackSnapshot(false);

        // No further fallbacks — skip forward.
        handleTrackEnded();

        if (isAdded() && !message.trim().isEmpty()) {
            
        }
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
            connection.setRequestProperty("User-Agent", "Sleppify-Stream/1.0");
            connection.setRequestProperty("Accept", accept);
            connection.setUseCaches(false);

            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) {
                Log.e(TAG, "readTextResponse: non-2xx code=" + code
                        + " url=" + maskUrlForLog(urlValue)
                        + " body=" + safeTrim(readErrorResponse(connection), 320));
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
            Log.e(TAG, "readTextResponse: exception url=" + maskUrlForLog(urlValue), e);
            return "";
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    @NonNull
    private String readErrorResponse(@NonNull HttpURLConnection connection) {
        InputStream errorStream = null;
        try {
            errorStream = connection.getErrorStream();
            if (errorStream == null) {
                return "";
            }
            try (BufferedInputStream in = new BufferedInputStream(errorStream);
                 ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[4096];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                }
                return out.toString(StandardCharsets.UTF_8.name());
            }
        } catch (Exception ignored) {
            return "";
        }
    }

    @NonNull
    private String safeTrim(@Nullable String value, int maxLen) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        if (trimmed.length() <= maxLen) {
            return trimmed;
        }
        return trimmed.substring(0, Math.max(0, maxLen)) + "...";
    }

    @NonNull
    private String maskUrlForLog(@Nullable String value) {
        if (value == null) {
            return "";
        }
        int tokenIndex = value.indexOf("token=");
        if (tokenIndex < 0) {
            return value;
        }
        int amp = value.indexOf('&', tokenIndex);
        if (amp < 0) {
            return value.substring(0, tokenIndex) + "token=***";
        }
        return value.substring(0, tokenIndex) + "token=***" + value.substring(amp);
    }

    private boolean isNetworkAvailable() {
        if (!isAdded()) {
            return false;
        }
        ConnectivityManager cm = ContextCompat.getSystemService(requireContext(), ConnectivityManager.class);
        if (cm == null) {
            return false;
        }
        android.net.Network network = cm.getActiveNetwork();
        if (network == null) {
            return false;
        }
        NetworkCapabilities capabilities = cm.getNetworkCapabilities(network);
        if (capabilities == null) {
            return false;
        }
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                || capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
                || capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
                || capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN);
    }

    private void maybeStartOfflineCrossfade(int positionMs, int durationMs) {
        int crossfadeDurationMs = getOfflineCrossfadeDurationMs();
        if (!isAdded()
                || localExoMediaPlayer == null
                || localSourcePreparing
                || localCrossfadeInProgress
                || userSeeking
                || !isPlaying
                || durationMs <= 0
                || crossfadeDurationMs <= 0) {
            return;
        }

        int remainingMs = Math.max(0, durationMs - Math.max(0, positionMs));
        
        // Safety: if we have very little time left (e.g. < 500ms) and no crossfade has started,
        // it might be too late to prepare a new ExoMediaPlayer, but we should try anyway if remainingMs <= crossfadeDurationMs.
        if (remainingMs > crossfadeDurationMs) {
            return;
        }

        startOfflineCrossfadeTransition(crossfadeDurationMs);
    }

    private int getOfflineCrossfadeDurationMs() {
        if (cachedCrossfadeDurationMs >= 0) {
            return cachedCrossfadeDurationMs;
        }
        int seconds = OFFLINE_CROSSFADE_DEFAULT_SECONDS;
        if (settingsPrefs != null) {
            seconds = settingsPrefs.getInt(
                    CloudSyncManager.KEY_OFFLINE_CROSSFADE_SECONDS,
                    OFFLINE_CROSSFADE_DEFAULT_SECONDS
            );
        }
        seconds = Math.max(0, Math.min(OFFLINE_CROSSFADE_MAX_SECONDS, seconds));
        int result;
        if (seconds <= 0) {
            result = 0;
        } else if (seconds == 1) {
            result = OFFLINE_CROSSFADE_FIRST_STEP_MS;
        } else {
            result = seconds * 1000;
        }
        cachedCrossfadeDurationMs = result;
        return result;
    }

    private void invalidateCrossfadeDurationCache() {
        cachedCrossfadeDurationMs = -1;
    }

    private void startOfflineCrossfadeTransition(int crossfadeDurationMs) {
        if (!isAdded() || localExoMediaPlayer == null || tracks.isEmpty() || currentIndex < 0 || currentIndex >= tracks.size()) {
            return;
        }

        ExoMediaPlayer outgoing = localExoMediaPlayer;
        int nextIndex = resolveNextIndexForCompletionCrossfade();
        if (nextIndex < 0 || nextIndex >= tracks.size()) {
            startOfflineFadeOutOnly(outgoing, crossfadeDurationMs);
            return;
        }

        PlayerTrack nextTrack = tracks.get(nextIndex);
        if (nextTrack == null || TextUtils.isEmpty(nextTrack.videoId)) {
            startOfflineFadeOutOnly(outgoing, crossfadeDurationMs);
            return;
        }

        // Use gapless pre-buffered player if available for this next track
        if (gaplessPreBufferedPlayer != null && TextUtils.equals(gaplessPreBufferedVideoId, nextTrack.videoId)) {
            ExoMediaPlayer preBuffered = gaplessPreBufferedPlayer;
            gaplessPreBufferedPlayer = null;
            gaplessPreBufferedVideoId = "";
            gaplessPreBufferTriggered = false;
            executeAutomaticCrossfadeWithPlayer(outgoing, nextIndex, preBuffered, crossfadeDurationMs);
            return;
        }

        // Try local device files first
        if (isAdded() && LocalFilesStore.isLocalVideoId(nextTrack.videoId)) {
            String contentUri = LocalFilesStore.getContentUriForVideoId(requireContext(), nextTrack.videoId);
            if (contentUri != null && !contentUri.isEmpty()) {
                executeAutomaticCrossfade(outgoing, nextIndex, contentUri, false, crossfadeDurationMs);
                return;
            }
        }

        // Try offline first
        if (isAdded() && OfflineAudioStore.hasValidatedOfflineAudio(requireContext(), nextTrack.videoId, nextTrack.duration)) {
            File nextFile = OfflineAudioStore.getExistingOfflineAudioFile(requireContext(), nextTrack.videoId);
            if (nextFile != null && nextFile.isFile() && nextFile.length() > 0L) {
                executeAutomaticCrossfade(outgoing, nextIndex, nextFile.getAbsolutePath(), false, crossfadeDurationMs);
                return;
            }
        }

        // Try prefetched URL (already resolved)
        if (TextUtils.equals(nextTrack.videoId, prefetchedNextVideoId) && !TextUtils.isEmpty(prefetchedNextUrl)) {
            executeAutomaticCrossfade(outgoing, nextIndex, prefetchedNextUrl, true, crossfadeDurationMs);
            return;
        }

        // Resolve online URL on background thread to avoid blocking UI
        if (isNetworkAvailable()) {
            final ExoMediaPlayer fadeOutgoing = outgoing;
            final int fadeNextIndex = nextIndex;
            final int fadeDuration = crossfadeDurationMs;
            streamResolverExecutor.submit(() -> {
                String resolved = InnertubeResolver.resolveStreamUrl(requireContext(), nextTrack.videoId);
                localProgressHandler.post(() -> {
                    if (!isAdded() || localExoMediaPlayer != fadeOutgoing) return;
                    if (!TextUtils.isEmpty(resolved)) {
                        executeAutomaticCrossfade(fadeOutgoing, fadeNextIndex, resolved, true, fadeDuration);
                    } else {
                        startOfflineFadeOutOnly(fadeOutgoing, fadeDuration);
                    }
                });
            });
            return;
        }

        startOfflineFadeOutOnly(outgoing, crossfadeDurationMs);
    }

    private void executeAutomaticCrossfade(ExoMediaPlayer outgoing, int nextIndex, String nextUrl, boolean nextIsNetwork, int crossfadeDurationMs) {
        executeCrossfade(outgoing, nextIndex, nextUrl, nextIsNetwork, crossfadeDurationMs);
    }

    private void executeAutomaticCrossfadeWithPlayer(ExoMediaPlayer outgoing, int nextIndex, ExoMediaPlayer preBuffered, int crossfadeDurationMs) {
        preBuffered.isCrossfadeComponent = true;
        try {
            preBuffered.setVolume(0f, 0f);
            preBuffered.start();
        } catch (Exception e) {
            Log.e(TAG, "executeAutomaticCrossfadeWithPlayer: start failed", e);
            releaseSingleExoMediaPlayer(preBuffered);
            startOfflineFadeOutOnly(outgoing, crossfadeDurationMs);
            return;
        }

        localCrossfadeIncomingPlayer = preBuffered;
        localCrossfadeInProgress = true;
        localCrossfadeIsNetwork = !usingOfflineSource;
        localCrossfadeTargetIndex = nextIndex;
        localCrossfadeStartedAtMs = SystemClock.elapsedRealtime();
        outgoing.setOnCompletionListener(null);
        localCrossfadeTicker = buildCrossfadeTicker(outgoing, crossfadeDurationMs);
        localProgressHandler.post(localCrossfadeTicker);
    }

    private void executeCrossfade(ExoMediaPlayer outgoing, int nextIndex, String nextUrl, boolean nextIsNetwork, int crossfadeDurationMs) {
        ExoMediaPlayer incoming = new ExoMediaPlayer(requireContext().getApplicationContext());
        incoming.isCrossfadeComponent = true;
        try {
            incoming.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build());
            if (nextIsNetwork) {
                Map<String, String> headers = new HashMap<>();
                headers.put("User-Agent", STREAM_HTTP_USER_AGENT);
                headers.put("Accept", "*/*");
                String videoId = nextIndex >= 0 && nextIndex < tracks.size() && tracks.get(nextIndex) != null ? tracks.get(nextIndex).videoId : null;
                Map<String, String> authHeaders = InnertubeResolver.getHeadersFor(videoId);
                if (authHeaders != null) {
                    headers.putAll(authHeaders);
                }
                incoming.setDataSource(requireContext().getApplicationContext(), Uri.parse(nextUrl), headers);
            } else if (nextUrl.startsWith("content://")) {
                incoming.setDataSource(requireContext().getApplicationContext(), Uri.parse(nextUrl), null);
            } else {
                incoming.setDataSource(nextUrl);
            }
            incoming.prepare();
            incoming.setVolume(0f, 0f);
            incoming.start();
        } catch (Exception e) {
            Log.e(TAG, "executeCrossfade: failed to prepare incoming player", e);
            releaseSingleExoMediaPlayer(incoming);
            if (nextIndex >= 0) {
                playCurrentTrack();
            } else {
                startOfflineFadeOutOnly(outgoing, crossfadeDurationMs);
            }
            return;
        }

        localCrossfadeIncomingPlayer = incoming;
        localCrossfadeInProgress = true;
        localCrossfadeIsNetwork = nextIsNetwork;
        localCrossfadeTargetIndex = nextIndex;
        localCrossfadeStartedAtMs = SystemClock.elapsedRealtime();
        outgoing.setOnCompletionListener(null);
        localCrossfadeTicker = buildCrossfadeTicker(outgoing, crossfadeDurationMs);
        localProgressHandler.post(localCrossfadeTicker);
    }

    @NonNull
    private Runnable buildCrossfadeTicker(@NonNull ExoMediaPlayer outgoing, int crossfadeDurationMs) {
        return new Runnable() {
            @Override
            public void run() {
                if (!localCrossfadeInProgress || localExoMediaPlayer != outgoing) {
                    return;
                }
                float progress = Math.min(1f, Math.max(0f,
                        (SystemClock.elapsedRealtime() - localCrossfadeStartedAtMs) / (float) crossfadeDurationMs));
                try { outgoing.setVolume(1f - progress, 1f - progress); } catch (Exception ignored) {}
                if (localCrossfadeIncomingPlayer != null) {
                    try { localCrossfadeIncomingPlayer.setVolume(progress, progress); } catch (Exception ignored) {}
                }
                if (progress >= 1f) {
                    finalizeOfflineCrossfade(outgoing);
                    return;
                }
                localProgressHandler.postDelayed(this, OFFLINE_CROSSFADE_STEP_MS);
            }
        };
    }

    private void startOfflineFadeOutOnly(@NonNull ExoMediaPlayer outgoing, int fadeDurationMs) {
        if (localCrossfadeInProgress || localExoMediaPlayer != outgoing) {
            return;
        }
        localCrossfadeIncomingPlayer = null;
        localCrossfadeInProgress = true;
        localCrossfadeTargetIndex = -1;
        localCrossfadeStartedAtMs = SystemClock.elapsedRealtime();
        localCrossfadeTicker = buildCrossfadeTicker(outgoing, Math.max(1, fadeDurationMs));
        localProgressHandler.post(localCrossfadeTicker);
    }

    private int resolveNextIndexForCompletionCrossfade() {
        if (tracks.isEmpty()) {
            return -1;
        }

        if (repeatMode == REPEAT_MODE_ONE) {
            return -1;
        }

        if (currentIndex < tracks.size() - 1) {
            return currentIndex + 1;
        }

        if (repeatMode == REPEAT_MODE_ALL) {
            return 0;
        }

        return -1;
    }

    private void finalizeOfflineCrossfade(@NonNull ExoMediaPlayer outgoing) {
        ExoMediaPlayer incoming = localCrossfadeIncomingPlayer;
        int nextIndex = localCrossfadeTargetIndex;
        boolean wasNetwork = localCrossfadeIsNetwork;

        localCrossfadeInProgress = false;
        localCrossfadeIsNetwork = false;
        localCrossfadeStartedAtMs = 0L;
        localCrossfadeTargetIndex = -1;
        if (localCrossfadeTicker != null) {
            localProgressHandler.removeCallbacks(localCrossfadeTicker);
            localCrossfadeTicker = null;
        }
        localCrossfadeIncomingPlayer = null;

        releaseSingleExoMediaPlayer(outgoing);

        if (incoming == null || nextIndex < 0 || nextIndex >= tracks.size()) {
            return;
        }

        localExoMediaPlayer = incoming;
        if (localExoMediaPlayer != null) {
            localExoMediaPlayer.isCrossfadeComponent = false;
        }
        usingOfflineSource = !wasNetwork;
        accumulatedListenMs = 0;
        gaplessPreBufferTriggered = false;
        currentIndex = nextIndex;
        pauseRequestedByUser = false;
        isPlaying = true;
        currentSeconds = 0;

        PlayerTrack track = tracks.get(currentIndex);
        loadedVideoId = track.videoId;
        if (TextUtils.isEmpty(loadedVideoId)) {
            loadedVideoId = "";
        }

        // Update video surface for incoming crossfade track
        boolean nextIsVideo = false;
        if (isAdded() && OfflineAudioStore.hasOfflineVideo(requireContext(), track.videoId)) {
            nextIsVideo = true;
        }
        currentSourceIsVideo = nextIsVideo;
        currentVideoFilePath = nextIsVideo
                ? OfflineAudioStore.getOfflineVideoFile(requireContext(), track.videoId).getAbsolutePath()
                : null;
        updatePlayerSurfaceForSource();

        incoming.setOnCompletionListener(mp -> {
            if (localExoMediaPlayer != mp) {
                return;
            }
            stopLocalProgressTicker();
            handleTrackEnded();
        });
        incoming.setOnErrorListener((mp, what, extra) -> {
            if (localExoMediaPlayer == mp) {
                stopLocalProgressTicker();
                releaseLocalExoMediaPlayer();
                usingOfflineSource = false;
            } else {
                releaseSingleExoMediaPlayer(mp);
            }
            playCurrentTrack();
            return true;
        });

        try {
            totalSeconds = Math.max(1, incoming.getDuration() / 1000);
        } catch (Exception ignored) {
            totalSeconds = 1;
        }

        bindCurrentTrackInternal(false, true);
        startLocalProgressTicker();
        updatePlayPauseIcon();
        updateMediaSessionState();

        // Route crossfaded video through router (handles detection + reparenting)
        if (currentSourceIsVideo && currentVideoFilePath != null && localExoMediaPlayer != null) {
            videoRouter.onTrackStarted(localExoMediaPlayer, currentVideoFilePath, track.videoId);
        }
        
        prefetchedNextVideoId = null;
        prefetchNextTrackStream();
    }

    private void cancelOfflineCrossfade() {
        localCrossfadeInProgress = false;
        localCrossfadeIsNetwork = false;
        localCrossfadeStartedAtMs = 0L;
        localCrossfadeTargetIndex = -1;
        if (localCrossfadeTicker != null) {
            localProgressHandler.removeCallbacks(localCrossfadeTicker);
            localCrossfadeTicker = null;
        }

        if (localCrossfadeIncomingPlayer != null) {
            releaseSingleExoMediaPlayer(localCrossfadeIncomingPlayer);
            localCrossfadeIncomingPlayer = null;
        }

        if (localExoMediaPlayer != null) {
            try {
                localExoMediaPlayer.setVolume(1f, 1f);
            } catch (Exception ignored) {
            }
        }

        cancelGaplessPreBuffer();
    }

    private void startLocalProgressTicker() {
        localProgressHandler.removeCallbacks(localProgressTicker);
        localProgressHandler.post(localProgressTicker);
    }

    private void stopLocalProgressTicker() {
        localProgressHandler.removeCallbacks(localProgressTicker);
    }

    private void loadNotificationArtworkOnly(@NonNull PlayerTrack track) {
        if (!isAdded() && getContext() == null) return;
        String videoId = track.videoId == null ? "" : track.videoId.trim();
        String imageUrl = track.imageUrl == null ? "" : track.imageUrl.trim();
        if (TextUtils.isEmpty(videoId) && TextUtils.isEmpty(imageUrl)) return;

        // Skip if already cached for this exact track — disk cache will serve it instantly anyway.
        if (TextUtils.equals(videoId, mediaSessionArtworkVideoId)
                && mediaSessionArtwork != null
                && !mediaSessionArtwork.isRecycled()) {
            return;
        }

        // Primary URL: track's own imageUrl. Fallback: YouTube hqdefault thumbnail.
        final String primaryUrl = !TextUtils.isEmpty(imageUrl) ? imageUrl
                : "https://i.ytimg.com/vi/" + Uri.encode(videoId) + "/hqdefault.jpg";
        final String fallbackUrl = "https://i.ytimg.com/vi/" + Uri.encode(videoId) + "/hqdefault.jpg";
        final String notifVideoId = videoId;

        // Use applicationContext so this request outlives fragment hide/show.
        Context appCtx = getContext() != null ? getContext().getApplicationContext() : null;
        if (appCtx == null) return;

        com.bumptech.glide.RequestManager rm = Glide.with(appCtx);
        rm.asBitmap()
            .load(primaryUrl)
            .transform(SHARED_YT_CROP)
            .format(DecodeFormat.PREFER_RGB_565)
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .override(256, 256)
            .error(
                rm.asBitmap()
                    .load(fallbackUrl)
                    .transform(SHARED_YT_CROP)
                    .format(DecodeFormat.PREFER_RGB_565)
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .override(256, 256)
            )
            .into(new com.bumptech.glide.request.target.CustomTarget<Bitmap>() {
                @Override
                public void onResourceReady(@NonNull Bitmap resource,
                        @Nullable Transition<? super Bitmap> transition) {
                    if (!isAdded()) return;
                    cacheMediaNotificationArtwork(notifVideoId, resource);
                    updateMediaSessionMetadata();
                    updateMediaNotification();
                }
                @Override
                public void onLoadCleared(@Nullable android.graphics.drawable.Drawable placeholder) {}
                @Override
                public void onLoadFailed(@Nullable android.graphics.drawable.Drawable errorDrawable) {
                    Log.w(TAG, "loadNotificationArtworkOnly: failed for videoId=" + notifVideoId);
                }
            });
    }

    private void loadPlayerCover(@NonNull PlayerTrack track, boolean bootstrapArtwork, int animationDirection) {
        if (!isAdded() || getActivity() == null || ivPlayerCover == null) {
            return;
        }
        // When playing video, don't load cover art — video surface handles display
        if (currentSourceIsVideo) {
            clearPlayerCoverRequest();
            ivPlayerCover.setImageDrawable(null);
            ivPlayerCover.setVisibility(View.GONE);
            completePlayerArtworkBootstrap();
            return;
        }

        String requestVideoId = track.videoId == null ? "" : track.videoId.trim();
        String fallbackImageUrl = track.imageUrl == null ? "" : track.imageUrl.trim();
        String preferredImageUrl = resolveHighResCoverUrl(requestVideoId, fallbackImageUrl);
        if (TextUtils.isEmpty(preferredImageUrl)) {
            clearPlayerCoverRequest();
            clearMediaNotificationArtwork();
            activeCoverVideoId = "";
            resetPlayerHeroContainerHeight();
            ivPlayerCover.setImageDrawable(null);
            updateMediaSessionMetadata();
            return;
        }

        activeCoverVideoId = requestVideoId;
        clearPlayerCoverRequest();

        if (!bootstrapArtwork) {
            ivPlayerCover.animate().cancel();
            // Keep old image visible until new one arrives (prevents blank cover on non-cached tracks)
            ivPlayerCover.setAlpha(1f);
        }

        playerCoverTarget = new CustomTarget<Bitmap>() {
            @Override
            public void onResourceReady(@NonNull Bitmap resource, @Nullable Transition<? super Bitmap> transition) {
                if (!isAdded() || !TextUtils.equals(activeCoverVideoId, requestVideoId)) {
                    return;
                }

                // Move smartCrop to background thread to avoid UI lag
                imageProcessingExecutor.execute(() -> {
                    Bitmap processedResource = YouTubeImageProcessor.smartCrop(resource);
                    
                    localProgressHandler.post(() -> {
                        if (!isAdded() || !TextUtils.equals(activeCoverVideoId, requestVideoId)) {
                            return;
                        }
                        ivPlayerCover.animate().cancel();
                        ivPlayerCover.setTranslationX(0f);
                        ivPlayerCover.setAlpha(0f);
                        // Adjust scaleType based on bitmap aspect ratio after crop:
                        // wide/panoramic → fitCenter (show full width); square/tall → centerCrop
                        if (processedResource != null && processedResource.getWidth() > processedResource.getHeight() * 1.2f) {
                            ivPlayerCover.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
                        } else {
                            ivPlayerCover.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
                        }
                        // Set image and promote to hardware layer BEFORE starting the animation.
                        // This uploads the bitmap to the GPU in the current vsync so the first
                        // animated frame has zero upload cost — eliminating the "10fps" jank.
                        ivPlayerCover.setImageBitmap(processedResource);
                        ivPlayerCover.setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null);
                        ivPlayerCover.animate()
                            .alpha(1f)
                            .setDuration(420L)
                            .setInterpolator(new android.view.animation.DecelerateInterpolator())
                            .withEndAction(() -> ivPlayerCover.setLayerType(
                                    android.view.View.LAYER_TYPE_NONE, null))
                            .start();
                        adjustPlayerHeroForCover(processedResource);
                        cacheMediaNotificationArtwork(requestVideoId, processedResource);
                        updateMediaSessionMetadata();
                        updateMediaNotification();
                    });
                });
            }

            @Override
            public void onLoadCleared(@Nullable Drawable placeholder) {
                if (!isAdded()) {
                    return;
                }
                ivPlayerCover.setImageDrawable(null);
            }

            @Override
            public void onLoadFailed(@Nullable Drawable errorDrawable) {
                if (!isAdded() || !TextUtils.equals(activeCoverVideoId, requestVideoId)) {
                    return;
                }
                resetPlayerHeroContainerHeight();
                clearMediaNotificationArtwork();
                ivPlayerCover.animate().cancel();
                ivPlayerCover.setAlpha(1f);
                ivPlayerCover.setImageDrawable(null);
                updateMediaSessionMetadata();
                updateMediaNotification();
            }
        };

        // Limit image size to reduce memory pressure and GPU load during fade animations
        final int maxCoverSize = 640;

        com.bumptech.glide.RequestManager requestManager = Glide.with(this);
        requestManager
                .asBitmap()
                .load(preferredImageUrl)
                .transform(SHARED_YT_CROP)
                .format(DecodeFormat.PREFER_ARGB_8888)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .override(maxCoverSize, maxCoverSize)
                .error(
                        requestManager
                                .asBitmap()
                                .load("https://i.ytimg.com/vi/" + Uri.encode(requestVideoId) + "/hqdefault.jpg")
                                .transform(SHARED_YT_CROP)
                                .format(DecodeFormat.PREFER_ARGB_8888)
                                .diskCacheStrategy(DiskCacheStrategy.ALL)
                                .override(maxCoverSize, maxCoverSize)
                                .error(
                                        TextUtils.isEmpty(fallbackImageUrl)
                                                || TextUtils.equals(preferredImageUrl, fallbackImageUrl)
                                                ? null
                                                : requestManager
                                                        .asBitmap()
                                                        .load(fallbackImageUrl)
                                                        .transform(SHARED_YT_CROP)
                                                        .format(DecodeFormat.PREFER_ARGB_8888)
                                                        .diskCacheStrategy(DiskCacheStrategy.ALL)
                                                        .override(maxCoverSize, maxCoverSize)
                                )
                )
                .into(playerCoverTarget);
    }

    private void loadPlayerBackdrop(@NonNull PlayerTrack track, boolean bootstrapArtwork) {
        if (!isAdded() || getActivity() == null || playerBackgroundContainer == null) {
            completePlayerArtworkBootstrap();
            return;
        }

        String requestVideoId = track.videoId == null ? "" : track.videoId.trim();
        String fallbackImageUrl = track.imageUrl == null ? "" : track.imageUrl.trim();
        String preferredImageUrl = resolveLowResBackdropUrl(requestVideoId, fallbackImageUrl);

        activeBackdropVideoId = requestVideoId;

        if (TextUtils.isEmpty(preferredImageUrl)) {
            if (bootstrapArtwork) fadeOutBackdropToBlack();
            completePlayerArtworkBootstrap();
            return;
        }

        // Verificar si el color ya está cacheado
        Integer cachedColor = dominantColorCache.get(requestVideoId);
        if (cachedColor != null) {
            revealBackdropWithFadeToColor(cachedColor);
            if (bootstrapArtwork) {
                completePlayerArtworkBootstrap();
            }
            return;
        }

        com.bumptech.glide.request.target.CustomTarget<Bitmap> backdropTarget = new com.bumptech.glide.request.target.CustomTarget<Bitmap>() {
            @Override
            public void onResourceReady(@NonNull Bitmap bitmap, @Nullable Transition<? super Bitmap> transition) {
                if (!isAdded() || !TextUtils.equals(activeBackdropVideoId, requestVideoId)) {
                    return;
                }

                // Extraer color dominante usando Palette
                Palette.from(bitmap).generate(palette -> {
                    if (!isAdded() || !TextUtils.equals(activeBackdropVideoId, requestVideoId)) {
                        return;
                    }

                    int dominantColor = palette.getDominantColor(Color.BLACK);

                    // Detect B&W images robustly:
                    // JPEG compression artifacts can introduce a warm/red tint in B&W photos,
                    // so we cross-check dominant color saturation, vibrant swatch saturation,
                    // and the average saturation across all available swatches.
                    final int NEUTRAL_GRAY = 0xFF1A1A1A;
                    float[] hsvDominant = new float[3];
                    Color.colorToHSV(dominantColor, hsvDominant);
                    float dominantSat = hsvDominant[1];

                    // Collect max saturation from all swatches to confirm color richness
                    float maxSwatchSat = dominantSat;
                    java.util.List<Palette.Swatch> swatches = palette.getSwatches();
                    for (Palette.Swatch swatch : swatches) {
                        if (swatch == null) continue;
                        float[] h = new float[3];
                        Color.colorToHSV(swatch.getRgb(), h);
                        if (h[1] > maxSwatchSat) maxSwatchSat = h[1];
                    }

                    // Vibrant swatch — most saturated by Palette's definition
                    Palette.Swatch vibrant = palette.getVibrantSwatch();
                    float vibrantSat = 0f;
                    if (vibrant != null) {
                        float[] hv = new float[3];
                        Color.colorToHSV(vibrant.getRgb(), hv);
                        vibrantSat = hv[1];
                    }

                    // Image is B&W if both the dominant AND vibrant swatches are low-saturation.
                    // Threshold raised to 0.22f to catch JPEG-tinted B&W images.
                    boolean isBW = dominantSat < 0.22f && vibrantSat < 0.30f && maxSwatchSat < 0.30f;
                    int finalColor = isBW ? NEUTRAL_GRAY : dominantColor;

                    dominantColorCache.put(requestVideoId, finalColor);
                    saveBackdropColor(requestVideoId, finalColor);
                    revealBackdropWithFadeToColor(finalColor);

                    if (bootstrapArtwork) {
                        completePlayerArtworkBootstrap();
                    }
                });
            }

            @Override
            public void onLoadCleared(@Nullable android.graphics.drawable.Drawable placeholder) {
                if (!isAdded()) {
                    return;
                }
                fadeOutBackdropToBlack();
            }

            @Override
            public void onLoadFailed(@Nullable android.graphics.drawable.Drawable errorDrawable) {
                if (!isAdded() || !TextUtils.equals(activeBackdropVideoId, requestVideoId)) {
                    return;
                }

                fadeOutBackdropToBlack();
                completePlayerArtworkBootstrap();
            }
        };

        com.bumptech.glide.RequestManager requestManager = Glide.with(this);
        // Load small bitmap (16x16) to extract dominant color only - not for display
        requestManager
                .asBitmap()
                .load(preferredImageUrl)
                .priority(com.bumptech.glide.Priority.LOW)
                .centerCrop()
                .override(64, 64)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .skipMemoryCache(true)
                .error(
                        TextUtils.isEmpty(fallbackImageUrl)
                                || TextUtils.equals(preferredImageUrl, fallbackImageUrl)
                                ? null
                                : requestManager
                                        .asBitmap()
                                        .load(fallbackImageUrl)
                                        .priority(com.bumptech.glide.Priority.LOW)
                                        .centerCrop()
                                        .override(64, 64)
                                        .diskCacheStrategy(DiskCacheStrategy.ALL)
                                        .skipMemoryCache(true)
                )
                .into(backdropTarget);
    }

    @NonNull
    private String resolveHighResCoverUrl(
            @NonNull String videoId,
            @NonNull String fallbackImageUrl
    ) {
        if (!TextUtils.isEmpty(videoId)) {
            return "https://i.ytimg.com/vi/" + Uri.encode(videoId) + "/maxresdefault.jpg";
        }
        return fallbackImageUrl;
    }

    private String resolveLowResBackdropUrl(
            @NonNull String videoId,
            @NonNull String fallbackImageUrl
    ) {
        if (!TextUtils.isEmpty(videoId)) {
            return "https://i.ytimg.com/vi/" + Uri.encode(videoId) + "/mqdefault.jpg";
        }
        return fallbackImageUrl;
    }

    // ... (rest of the code remains the same)

    @Override
    public void onDestroy() {
        cancelSourcePrepareTimeout();
        cancelAutoplayRecovery();
        cancelPlaybackErrorRetry();
        stopLocalProgressTicker();
        releaseLocalExoMediaPlayer();
        cancelPendingStreamResolver();
        streamResolverExecutor.shutdownNow();
        socialStatsExecutor.shutdownNow();
        super.onDestroy();
    }

    private void bindCurrentTrack(boolean allowResume) {
        bindCurrentTrackInternal(allowResume, false);
    }

    private void bindCurrentTrackInternal(boolean allowResume, boolean forceZero) {
        if (tracks.isEmpty()) {
            return;
        }
        if (currentIndex < 0 || currentIndex >= tracks.size()) {
            currentIndex = 0;
        }

        PlayerTrack track = tracks.get(currentIndex);
        tvPlayerTitle.setText(track.title);
        tvPlayerTitle.setSelected(true);
        tvPlayerArtist.setText(track.artist);
        boolean isLocalFile = LocalFilesStore.isLocalVideoId(track.videoId);
        // Hide irrelevant chips for local files
        View likeDislikeChip = (actionLike != null) ? (View) actionLike.getParent() : null;
        if (likeDislikeChip != null) likeDislikeChip.setVisibility(isLocalFile ? View.GONE : View.VISIBLE);
        if (actionComments != null) actionComments.setVisibility(isLocalFile ? View.GONE : View.VISIBLE);
        if (actionRadio != null) actionRadio.setVisibility(isLocalFile ? View.GONE : View.VISIBLE);
        if (actionShare != null) actionShare.setVisibility(isLocalFile ? View.GONE : View.VISIBLE);
        if (actionDownloadTrack != null) actionDownloadTrack.setVisibility(isLocalFile ? View.GONE : View.VISIBLE);

        refreshSocialActionsForCurrentTrack(track);
        refreshFavoriteActionForCurrentTrack();
        if (!isLocalFile) refreshDownloadChipState();

        totalSeconds = Math.max(1, parseDurationSeconds(track.duration));
        currentSeconds = 0;

        tvCurrentTime.setText(formatSeconds(currentSeconds));
        tvTotalTime.setText(TextUtils.isEmpty(track.duration) ? formatSeconds(totalSeconds) : track.duration);

        int progress = Math.round((currentSeconds / (float) Math.max(1, totalSeconds)) * 1000f);
        sbPlaybackProgress.setProgress(Math.max(0, Math.min(1000, progress)));
        updatePlayPauseIcon();

        boolean bootstrapArtwork = playerArtworkBootstrapPending;
        if (bootstrapArtwork) {
            showPlayerArtworkLoadingState();
            if (!isAdded()) return;
        } else {
            // Don't fade out — keep old cover visible until new one arrives in loadPlayerCover
            if (ivPlayerCover != null) {
                ivPlayerCover.animate().cancel();
            }
        }

        // Always load artwork for notification/MediaSession regardless of player visibility.
        // Uses applicationContext so it survives fragment hide/show cycles.
        loadNotificationArtworkOnly(track);

        // ✅ Only load artwork if player is actually visible (not hidden/miniplayer mode)
        if (!isHidden()) {
            // ✅ PRIORITY 1: Load cover art (visible immediately)
            loadPlayerCover(track, bootstrapArtwork, 1);

            // ✅ BACKDROP: Only fade to black on first bootstrap, otherwise crossfade to new dominant color
            if (bootstrapArtwork && playerBackgroundContainer != null) {
                fadeOutBackdropToBlack();
            }

            // ✅ DEFERRED: Backdrop and notification artwork (not immediately visible)
            // Defer by 500ms to avoid saturating Glide thread pool while cover is loading
            if (getView() != null) {
                getView().postDelayed(() -> {
                    if (!isAdded() || isHidden()) return;
                    scheduleBackdropLoad(track, bootstrapArtwork);
                }, 500L);
            }
        } else {
            // Player is hidden: clear activeCoverVideoId so onHiddenChanged forces a re-bind
            activeCoverVideoId = "";
        }

        updateMediaSessionMetadata();
        updateMediaSessionState();
        if (bootstrapArtwork) {
            cancelNextUpReveal();
            nextUpTracks.clear();
            if (nextUpAdapter != null) {
                nextUpAdapter.setItems(nextUpTracks);
            }
        } else {
            refreshNextUp();
        }
        syncMiniStateWithPlaylist();
        persistPlaybackSnapshot(false);
    }

    private void setupSocialActions() {
        applySocialStatsToUi(SocialStats.unavailable());

        if (actionLike != null) {
            actionLike.setOnClickListener(v -> {
                // Visual only for now.
            });
        }
        if (actionDislike != null) {
            actionDislike.setOnClickListener(v -> {
                // Visual only for now.
            });
        }
        if (actionComments != null) {
            actionComments.setOnClickListener(v -> {
                // Visual only for now.
            });
        }
        if (actionFavorite != null) {
            actionFavorite.setOnClickListener(v -> showSaveToPlaylistSheetFromPlayer());
        }
        if (actionRadio != null) {
            actionRadio.setOnClickListener(v -> openRadioForCurrentTrack());
        }
        if (actionShare != null) {
            actionShare.setOnClickListener(v -> shareCurrentTrack());
        }
        if (actionDownloadTrack != null) {
            actionDownloadTrack.setOnClickListener(v -> toggleOfflineDownloadForCurrentTrack());
        }
        refreshFavoriteActionForCurrentTrack();
        refreshDownloadChipState();
        observeManualDownloadWork();
    }

    private void observeManualDownloadWork() {
        if (!isAdded()) return;
        try {
            androidx.work.WorkManager.getInstance(requireContext().getApplicationContext())
                    .getWorkInfosForUniqueWorkLiveData("offline_manual_track_queue")
                    .observe(getViewLifecycleOwner(), workInfos -> {
                        if (workInfos == null || workInfos.isEmpty()) return;
                        if (!isAdded() || tracks.isEmpty() || currentIndex < 0 || currentIndex >= tracks.size()) return;
                        String currentVideoId = tracks.get(currentIndex).videoId;
                        if (TextUtils.isEmpty(currentVideoId) || !currentVideoId.equals(pendingDownloadVideoId)) return;
                        for (androidx.work.WorkInfo info : workInfos) {
                            if (info.getState() == androidx.work.WorkInfo.State.RUNNING) {
                                String[] activeIds = info.getProgress().getStringArray(
                                        OfflinePlaylistDownloadWorker.PROGRESS_ACTIVE_IDS);
                                if (activeIds != null) {
                                    for (String id : activeIds) {
                                        if (currentVideoId.equals(id)) {
                                            return;
                                        }
                                    }
                                }
                            }
                            if (info.getState() == androidx.work.WorkInfo.State.SUCCEEDED
                                    || info.getState() == androidx.work.WorkInfo.State.FAILED
                                    || info.getState() == androidx.work.WorkInfo.State.CANCELLED) {
                                boolean nowOffline = OfflineAudioStore.hasValidatedOfflineAudio(
                                        requireContext(), currentVideoId, null);
                                if (nowOffline || info.getState() != androidx.work.WorkInfo.State.SUCCEEDED) {
                                    pendingDownloadVideoId = "";
                                }
                                refreshDownloadChipState();
                                return;
                            }
                        }
                    });
        } catch (Exception ignored) {
        }
    }

    private void toggleCurrentTrackFavorite() {
        if (!isAdded() || tracks.isEmpty() || currentIndex < 0 || currentIndex >= tracks.size()) {
            return;
        }

        PlayerTrack current = tracks.get(currentIndex);
        if (TextUtils.isEmpty(current.videoId)) {
            return;
        }

        boolean isFavorite = FavoritesPlaylistStore.isFavorite(requireContext(), current.videoId);
        if (isFavorite) {
            FavoritesPlaylistStore.removeFavorite(requireContext(), current.videoId);
        } else {
            FavoritesPlaylistStore.upsertFavorite(
                    requireContext(),
                    current.videoId,
                    current.title,
                    current.artist,
                    resolveDurationLabelForFavorite(current),
                    current.imageUrl
            );
        }

        refreshFavoriteActionForCurrentTrack();
        notifyFavoritesPlaylistIfVisible();
    }

    private void notifyFavoritesPlaylistIfVisible() {
        if (!isAdded()) {
            return;
        }

        String videoId = null;
        if (!tracks.isEmpty() && currentIndex >= 0 && currentIndex < tracks.size()) {
            videoId = tracks.get(currentIndex).videoId;
        }

        Fragment playlist = getParentFragmentManager().findFragmentByTag("playlist_detail");
        if (playlist instanceof PlaylistDetailFragment) {
            ((PlaylistDetailFragment) playlist).externalRefreshFavoritesIfActive(videoId);
        }
    }

    @NonNull
    private String resolveDurationLabelForFavorite(@NonNull PlayerTrack track) {
        String baseDuration = track.duration == null ? "" : track.duration.trim();
        if (parseDurationSeconds(baseDuration) > 0) {
            return baseDuration;
        }

        if (totalSeconds > 1) {
            return formatSeconds(totalSeconds);
        }

        if (tvTotalTime != null && tvTotalTime.getText() != null) {
            String fromUi = tvTotalTime.getText().toString().trim();
            if (parseDurationSeconds(fromUi) > 0) {
                return fromUi;
            }
        }

        return "--:--";
    }

    private void persistResolvedDurationForCurrentFavorite() {
        if (!isAdded() || totalSeconds <= 1 || tracks.isEmpty() || currentIndex < 0 || currentIndex >= tracks.size()) {
            return;
        }

        PlayerTrack current = tracks.get(currentIndex);
        if (TextUtils.isEmpty(current.videoId)
                || !FavoritesPlaylistStore.isFavorite(requireContext(), current.videoId)
                || parseDurationSeconds(current.duration) > 0) {
            return;
        }

        FavoritesPlaylistStore.upsertFavorite(
                requireContext(),
                current.videoId,
                current.title,
                current.artist,
                formatSeconds(totalSeconds),
                current.imageUrl
        );
    }

    @NonNull
    private java.util.List<String[]> getPlaylistsContainingTrack(@NonNull String videoId) {
        java.util.List<String[]> result = new ArrayList<>();
        if (!isAdded()) return result;
        Context ctx = requireContext();
        if (FavoritesPlaylistStore.isFavorite(ctx, videoId)) {
            result.add(new String[]{FavoritesPlaylistStore.PLAYLIST_ID, FavoritesPlaylistStore.PLAYLIST_TITLE});
        }
        java.util.List<String> customNames = CustomPlaylistsStore.INSTANCE.getAllPlaylistNames(ctx);
        for (String name : customNames) {
            java.util.List<FavoritesPlaylistStore.FavoriteTrack> playlistTracks =
                    CustomPlaylistsStore.INSTANCE.getTracksFromPlaylist(ctx, name);
            for (FavoritesPlaylistStore.FavoriteTrack t : playlistTracks) {
                if (videoId.equals(t.videoId)) {
                    result.add(new String[]{CustomPlaylistsStore.CUSTOM_PLAYLIST_PREFIX + name, name});
                    break;
                }
            }
        }
        return result;
    }

    private void refreshFavoriteActionForCurrentTrack() {
        if (!isAdded()) {
            return;
        }

        if (tvActionFavoriteLabel != null) {
            tvActionFavoriteLabel.setText("Añadir a playlist");
        }

        if (ivActionFavoriteIcon != null) {
            ivActionFavoriteIcon.setImageResource(R.drawable.ic_stream_queue_add);
            int tint = ContextCompat.getColor(requireContext(), R.color.text_primary);
            ivActionFavoriteIcon.setColorFilter(tint);
        }

        if (actionFavorite != null) {
            actionFavorite.setAlpha(1f);
        }
    }

    private void shareCurrentTrack() {
        if (!isAdded() || tracks.isEmpty() || currentIndex < 0 || currentIndex >= tracks.size()) return;
        PlayerTrack track = tracks.get(currentIndex);
        if (TextUtils.isEmpty(track.videoId)) return;
        String shareText = "https://youtu.be/" + track.videoId;
        android.content.Intent shareIntent = new android.content.Intent(android.content.Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(android.content.Intent.EXTRA_TEXT, shareText);
        startActivity(android.content.Intent.createChooser(shareIntent, "Compartir"));
    }

    private void openRadioForCurrentTrack() {
        if (!isAdded() || tracks.isEmpty() || currentIndex < 0 || currentIndex >= tracks.size()) return;
        if (getParentFragmentManager().isStateSaved()) return;
        PlayerTrack track = tracks.get(currentIndex);
        if (TextUtils.isEmpty(track.videoId)) return;
        String radioPlaylistId = "RDAMVM" + track.videoId;
        String radioTitle = TextUtils.isEmpty(track.title) ? "Radio" : track.title;
        SharedPreferences prefs = requireContext().getSharedPreferences(PREFS_PLAYER_STATE, Activity.MODE_PRIVATE);
        String accessToken = safeValue(prefs.getString(PREF_LAST_YOUTUBE_ACCESS_TOKEN, ""));

        // Close player with animation
        collapseToMiniMode(true);

        // Open radio PlaylistDetail after a short delay for animation
        View view = getView();
        if (view != null) {
            view.postDelayed(() -> {
                if (getActivity() == null) return;
                androidx.fragment.app.FragmentManager fm;
                try { fm = getParentFragmentManager(); } catch (Exception e) { return; }
                if (fm.isStateSaved()) return;
                PlaylistDetailFragment detailFragment = PlaylistDetailFragment.newInstance(
                        radioPlaylistId,
                        radioTitle,
                        TextUtils.isEmpty(track.artist) ? "" : track.artist,
                        TextUtils.isEmpty(track.imageUrl) ? "" : track.imageUrl,
                        accessToken
                );
                if (getActivity() instanceof MainActivity) {
                    ((MainActivity) getActivity()).showModuleLoadingOverlay();
                }
                Fragment existingDetail = fm.findFragmentByTag(TAG_PLAYLIST_DETAIL);
                androidx.fragment.app.FragmentTransaction transaction = fm.beginTransaction()
                        .setReorderingAllowed(true);
                if (existingDetail != null && existingDetail.isAdded() && existingDetail != this) {
                    transaction.remove(existingDetail);
                }
                transaction
                        .add(R.id.fragmentContainer, detailFragment, TAG_PLAYLIST_DETAIL)
                        .addToBackStack(TAG_PLAYLIST_DETAIL)
                        .commit();
            }, 350L);
        }

        // Fetch radio tracks and save to RadioHistoryStore for library display
        String cookie = safeValue(prefs.getString("stream_last_youtube_web_cookie", ""));
        final String selectedVideoId = track.videoId;
        final String selectedTitle = TextUtils.isEmpty(track.title) ? "Tema" : track.title;
        final String selectedArtist = TextUtils.isEmpty(track.artist) ? "" : track.artist;
        final String selectedThumb = TextUtils.isEmpty(track.imageUrl) ? "" : track.imageUrl;
        radioMusicService.fetchMixTracks(cookie, radioPlaylistId, new YouTubeMusicService.MixTracksCallback() {
            @Override
            public void onSuccess(@NonNull java.util.List<YouTubeMusicService.TrackResult> radioTracks) {
                if (radioTracks.isEmpty()) return;
                java.util.List<RadioHistoryStore.RadioTrack> radioStoreTracks = new ArrayList<>();
                radioStoreTracks.add(new RadioHistoryStore.RadioTrack(
                        selectedVideoId, selectedTitle, selectedArtist, selectedThumb));
                for (YouTubeMusicService.TrackResult t : radioTracks) {
                    if (TextUtils.isEmpty(t.videoId) || TextUtils.equals(t.videoId, selectedVideoId)) continue;
                    radioStoreTracks.add(new RadioHistoryStore.RadioTrack(
                            t.videoId,
                            TextUtils.isEmpty(t.title) ? "" : t.title,
                            t.subtitle == null ? "" : t.subtitle,
                            t.thumbnailUrl == null ? "" : t.thumbnailUrl));
                }
                Context ctx = persistentAppContext;
                if (ctx != null) {
                    RadioHistoryStore.INSTANCE.saveRadio(ctx, radioPlaylistId, selectedTitle, selectedThumb, radioStoreTracks);
                }
                localProgressHandler.post(() -> {
                    if (getActivity() instanceof MainActivity) {
                        ((MainActivity) getActivity()).refreshMusicLibrary();
                    }
                });
            }

            @Override
            public void onError(@NonNull String error) {
                // Radio fetch failed — no action needed
            }
        });
    }

    private void refreshDownloadChipState() {
        if (actionDownloadTrack == null || ivActionDownloadIcon == null || tvActionDownloadLabel == null) return;
        if (!isAdded() || tracks.isEmpty() || currentIndex < 0 || currentIndex >= tracks.size()) {
            actionDownloadTrack.setVisibility(View.GONE);
            return;
        }
        PlayerTrack track = tracks.get(currentIndex);
        if (TextUtils.isEmpty(track.videoId)) {
            actionDownloadTrack.setVisibility(View.GONE);
            return;
        }

        actionDownloadTrack.setVisibility(View.VISIBLE);
        boolean isPending = track.videoId.equals(pendingDownloadVideoId);
        if (isPending) {
            tvActionDownloadLabel.setText("Descargando...");
            ivActionDownloadIcon.setImageResource(R.drawable.ic_download_bold);
            actionDownloadTrack.setAlpha(0.6f);
            actionDownloadTrack.setClickable(false);
            return;
        }

        // Default state while checking
        tvActionDownloadLabel.setText("Descargar");
        ivActionDownloadIcon.setImageResource(R.drawable.ic_download_bold);
        actionDownloadTrack.setAlpha(1f);
        actionDownloadTrack.setClickable(true);

        final String videoId = track.videoId;
        final Context appCtx = requireContext().getApplicationContext();
        localProgressHandler.post(() -> {
            new Thread(() -> {
                boolean isDownloaded = OfflineAudioStore.hasValidatedOfflineAudio(appCtx, videoId, null);
                localProgressHandler.post(() -> {
                    if (!isAdded() || actionDownloadTrack == null) return;
                    if (currentIndex < 0 || currentIndex >= tracks.size()) return;
                    if (!TextUtils.equals(tracks.get(currentIndex).videoId, videoId)) return;
                    applyDownloadChipVisual(isDownloaded, videoId.equals(pendingDownloadVideoId));
                });
            }).start();
        });
    }

    private void applyDownloadChipVisual(boolean isDownloaded, boolean isPending) {
        if (actionDownloadTrack == null || ivActionDownloadIcon == null || tvActionDownloadLabel == null) return;
        if (isDownloaded) {
            pendingDownloadVideoId = "";
            tvActionDownloadLabel.setText("Descargado");
            ivActionDownloadIcon.setImageResource(R.drawable.ic_download_bold);
            actionDownloadTrack.setAlpha(1f);
            actionDownloadTrack.setClickable(true);
        } else if (isPending) {
            tvActionDownloadLabel.setText("Descargando...");
            ivActionDownloadIcon.setImageResource(R.drawable.ic_download_bold);
            actionDownloadTrack.setAlpha(0.6f);
            actionDownloadTrack.setClickable(false);
        } else {
            tvActionDownloadLabel.setText("Descargar");
            ivActionDownloadIcon.setImageResource(R.drawable.ic_download_bold);
            actionDownloadTrack.setAlpha(1f);
            actionDownloadTrack.setClickable(true);
        }
    }

    private void toggleOfflineDownloadForCurrentTrack() {
        if (!isAdded() || tracks.isEmpty() || currentIndex < 0 || currentIndex >= tracks.size()) return;
        PlayerTrack track = tracks.get(currentIndex);
        if (TextUtils.isEmpty(track.videoId)) return;

        boolean isDownloaded = OfflineAudioStore.hasValidatedOfflineAudio(requireContext(), track.videoId, null);
        if (isDownloaded) {
            pendingDownloadVideoId = "";
            OfflineAudioStore.deleteOfflineAudio(requireContext(), track.videoId);
            android.widget.Toast.makeText(requireContext(), "Descarga eliminada", android.widget.Toast.LENGTH_SHORT).show();
            refreshDownloadChipState();
        } else {
            pendingDownloadVideoId = track.videoId;
            enqueueOfflineDownloadForTrack(track);
            android.widget.Toast.makeText(requireContext(), "Descarga en cola", android.widget.Toast.LENGTH_SHORT).show();
            refreshDownloadChipState();
        }
    }

    private void enqueueOfflineDownloadForTrack(PlayerTrack track) {
        androidx.work.Data input = new androidx.work.Data.Builder()
                .putString(OfflinePlaylistDownloadWorker.INPUT_PLAYLIST_ID, "")
                .putString(OfflinePlaylistDownloadWorker.INPUT_PLAYLIST_TITLE, "Descargas Manuales")
                .putStringArray(OfflinePlaylistDownloadWorker.INPUT_VIDEO_IDS, new String[]{track.videoId})
                .putStringArray(OfflinePlaylistDownloadWorker.INPUT_TITLES, new String[]{track.title})
                .putStringArray(OfflinePlaylistDownloadWorker.INPUT_ARTISTS, new String[]{track.artist})
                .putStringArray(OfflinePlaylistDownloadWorker.INPUT_DURATIONS, new String[]{track.duration})
                .putInt(OfflinePlaylistDownloadWorker.INPUT_ALREADY_OFFLINE_COUNT, 0)
                .putInt(OfflinePlaylistDownloadWorker.INPUT_TOTAL_WITH_VIDEO_ID, 1)
                .putBoolean(OfflinePlaylistDownloadWorker.INPUT_USER_INITIATED, true)
                .putBoolean(OfflinePlaylistDownloadWorker.INPUT_MANUAL_QUEUE, true)
                .build();
        SharedPreferences prefs = requireContext().getSharedPreferences(CloudSyncManager.PREFS_SETTINGS, Context.MODE_PRIVATE);
        boolean allowMobileData = prefs.getBoolean(CloudSyncManager.KEY_OFFLINE_DOWNLOAD_ALLOW_MOBILE_DATA, false);
        androidx.work.Constraints constraints = new androidx.work.Constraints.Builder()
                .setRequiredNetworkType(allowMobileData ? androidx.work.NetworkType.CONNECTED : androidx.work.NetworkType.UNMETERED)
                .build();
        androidx.work.OneTimeWorkRequest request = new androidx.work.OneTimeWorkRequest.Builder(OfflinePlaylistDownloadWorker.class)
                .setInputData(input)
                .setConstraints(constraints)
                .addTag("offline_manual_track_queue")
                .build();
        androidx.work.WorkManager.getInstance(requireContext().getApplicationContext())
                .enqueueUniqueWork("offline_manual_track_queue", androidx.work.ExistingWorkPolicy.APPEND_OR_REPLACE, request);
    }

    private void refreshSocialActionsForCurrentTrack(@Nullable PlayerTrack track) {
        if (track == null || TextUtils.isEmpty(track.videoId)) {
            cancelPendingSocialStatsFetch();
            pendingSocialStatsVideoId = "";
            applySocialStatsToUi(SocialStats.unavailable());
            return;
        }

        pendingSocialStatsVideoId = track.videoId;
        SocialStats cached = socialStatsCache.get(track.videoId);
        if (cached == null) {
            cached = readSocialStatsFromCache(track.videoId);
            if (cached != null) {
                socialStatsCache.put(track.videoId, cached);
            }
        }
        if (cached != null) {
            applySocialStatsToUi(cached);
        } else {
            applySocialStatsToUi(SocialStats.loading());
        }

        String apiKey = BuildConfig.YOUTUBE_DATA_API_KEY == null
                ? ""
                : BuildConfig.YOUTUBE_DATA_API_KEY.trim();
        if (apiKey.isEmpty()) {
            cancelPendingSocialStatsFetch();
            if (cached == null) {
                applySocialStatsToUi(SocialStats.unavailable());
            }
            return;
        }

        String requestVideoId = track.videoId;
        SocialStats cachedSnapshot = cached;
        boolean deferFetch = cached == null && isPlaying && !usingOfflineSource;
        scheduleSocialStatsFetch(requestVideoId, apiKey, cachedSnapshot, deferFetch);
    }

    private void scheduleSocialStatsFetch(
            @NonNull String requestVideoId,
            @NonNull String apiKey,
            @Nullable SocialStats cachedSnapshot,
            boolean deferFetch
    ) {
        cancelPendingSocialStatsFetch();

        pendingSocialStatsFetchRunnable = () -> {
            pendingSocialStatsFetchRunnable = null;
            if (!isAdded() || !TextUtils.equals(pendingSocialStatsVideoId, requestVideoId)) {
                return;
            }

            socialStatsExecutor.execute(() -> {
                SocialStats stats = fetchSocialStats(requestVideoId, apiKey);
                localProgressHandler.post(() -> {
                    if (!isAdded() || !TextUtils.equals(pendingSocialStatsVideoId, requestVideoId)) {
                        return;
                    }

                    if (stats.unavailable && cachedSnapshot != null) {
                        applySocialStatsToUi(cachedSnapshot);
                        return;
                    }

                    socialStatsCache.put(requestVideoId, stats);
                    persistSocialStatsToCache(requestVideoId, stats);
                    applySocialStatsToUi(stats);
                });
            });
        };

        if (deferFetch) {
            localProgressHandler.postDelayed(pendingSocialStatsFetchRunnable, SOCIAL_STATS_FETCH_DEFER_MS);
        } else {
            localProgressHandler.post(pendingSocialStatsFetchRunnable);
        }
    }

    private void cancelPendingSocialStatsFetch() {
        if (pendingSocialStatsFetchRunnable != null) {
            localProgressHandler.removeCallbacks(pendingSocialStatsFetchRunnable);
            pendingSocialStatsFetchRunnable = null;
        }
    }

    @Nullable
    private SocialStats readSocialStatsFromCache(@NonNull String videoId) {
        if (playerStatePrefs == null || TextUtils.isEmpty(videoId)) {
            return null;
        }

        String raw = playerStatePrefs.getString(PREF_SOCIAL_STATS_PREFIX + videoId, "");
        if (TextUtils.isEmpty(raw)) {
            return null;
        }

        try {
            JSONObject json = new JSONObject(raw);
            String like = json.optString("like", "");
            String dislike = json.optString("dislike", "");
            String comments = json.optString("comments", "");
            if (TextUtils.isEmpty(like) || TextUtils.isEmpty(dislike) || TextUtils.isEmpty(comments)) {
                return null;
            }
            return new SocialStats(like, dislike, comments, false);
        } catch (Exception ignored) {
            return null;
        }
    }

    private void persistSocialStatsToCache(@NonNull String videoId, @NonNull SocialStats stats) {
        if (playerStatePrefs == null || TextUtils.isEmpty(videoId) || stats.unavailable) {
            return;
        }

        try {
            JSONObject json = new JSONObject();
            json.put("like", stats.likeCount);
            json.put("dislike", stats.dislikeCount);
            json.put("comments", stats.commentCount);
            playerStatePrefs.edit()
                    .putString(PREF_SOCIAL_STATS_PREFIX + videoId, json.toString())
                    .apply();
        } catch (Exception ignored) {
        }
    }

    @NonNull
    private SocialStats fetchSocialStats(@NonNull String videoId, @NonNull String apiKey) {
        String endpoint = "https://www.googleapis.com/youtube/v3/videos?part=statistics&id="
                + Uri.encode(videoId)
                + "&key="
                + Uri.encode(apiKey);
        String body = readTextResponse(endpoint, "application/json");
        if (body.isEmpty()) {
            return SocialStats.unavailable();
        }

        try {
            JSONObject root = new JSONObject(body);
            JSONArray items = root.optJSONArray("items");
            if (items == null || items.length() == 0) {
                return SocialStats.unavailable();
            }

            JSONObject first = items.optJSONObject(0);
            JSONObject statistics = first == null ? null : first.optJSONObject("statistics");
            if (statistics == null) {
                return SocialStats.unavailable();
            }

            long likeCount = parseSafeLong(statistics.optString("likeCount", ""));
            long commentCount = parseSafeLong(statistics.optString("commentCount", ""));
            return new SocialStats(
                    formatCompactCount(likeCount),
                    "0",
                    formatCompactCount(commentCount),
                    false
            );
        } catch (Exception ignored) {
            return SocialStats.unavailable();
        }
    }

    private void applySocialStatsToUi(@NonNull SocialStats stats) {
        if (tvActionLikeCount != null) {
            tvActionLikeCount.setText(stats.likeCount);
        }
        if (tvActionCommentCount != null) {
            tvActionCommentCount.setText(stats.commentCount);
        }
    }

    @NonNull
    private String formatCompactCount(long value) {
        if (value <= 0) {
            return "0";
        }
        if (value < 1000L) {
            return String.valueOf(value);
        }

        double compact = value;
        String suffix = "";
        if (value >= 1_000_000_000L) {
            compact = value / 1_000_000_000d;
            suffix = "B";
        } else if (value >= 1_000_000L) {
            compact = value / 1_000_000d;
            suffix = "M";
        } else {
            compact = value / 1000d;
            suffix = "K";
        }

        if (compact >= 100d) {
            return String.format(Locale.US, "%.0f%s", compact, suffix);
        }
        if (compact >= 10d) {
            return String.format(Locale.US, "%.1f%s", compact, suffix);
        }
        return String.format(Locale.US, "%.2f%s", compact, suffix);
    }

    private long parseSafeLong(@Nullable String raw) {
        if (TextUtils.isEmpty(raw)) {
            return -1L;
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (Exception ignored) {
            return -1L;
        }
    }

    public void externalPlayTrack(int index) {
        externalPlayTrackInternal(index, false);
    }

    public void externalPlayTrackFromStart(int index) {
        externalPlayTrackInternal(index, true);
    }

    private void externalPlayTrackInternal(int index, boolean startFromBeginning) {
        if (index < 0 || index >= tracks.size()) {
            return;
        }

        consecutiveStreamFailures = 0; // Reset failures on explicit user action

        String targetVideoId = tracks.get(index).videoId;
        boolean sameAsLoaded = !TextUtils.isEmpty(targetVideoId)
                && TextUtils.equals(targetVideoId, loadedVideoId);

        if (!startFromBeginning
                && sameAsLoaded
                && currentIndex == index
                && isEffectivePlaying()) {
            syncMiniStateWithPlaylist();
            persistPlaybackSnapshot(false);
            return;
        }

        currentIndex = index;
        isPlaying = true;
        bindCurrentTrack(!startFromBeginning);
        playCurrentTrack();
        persistPlaybackSnapshot(false);
    }

    public void externalSkipNext() {
        moveTrack(1);
    }

    public void externalSkipPrevious() {
        moveTrack(-1);
    }

    public void externalTogglePlayback() {
        togglePlayback();
    }

    public void externalPause() {
        if (isPlaying) {
            togglePlayback();
        }
    }

    public void externalPauseForSessionExit() {
        pauseRequestedByUser = true;
        cancelAutoplayRecovery();
        cancelPendingStreamResolver();
        stopLocalProgressTicker();
        releaseLocalExoMediaPlayer();
        isPlaying = false;
        updatePlayPauseIcon();
        updateMediaSessionState();
        updateMediaNotification();
        syncMiniStateWithPlaylist();
        persistPlaybackSnapshot(true);
    }

    public void externalAnimateEnterSlide() {
        View root = getView();
        if (root == null) {
            return;
        }

        playerEnterAnimationRunning = true;

        // Use cached height or resources fallback for first frame
        int distance = root.getHeight();
        if (distance <= 0) {
            distance = root.getResources().getDisplayMetrics().heightPixels;
        }

        root.animate().cancel();
        root.setVisibility(View.VISIBLE);
        root.setAlpha(1f);
        root.setTranslationY(distance);

        // Perform animation immediately
        root.animate()
                .translationY(0f)
                .setDuration(280L)
                .setInterpolator(new android.view.animation.PathInterpolator(0.4f, 0f, 0.2f, 1f))
                .withEndAction(() -> {
                    playerEnterAnimationRunning = false;
                    View currentView = getView();
                    if (currentView != null) {
                        currentView.setTranslationY(0f);
                    }
                })
                .start();
    }

    public void externalSetReturnTargetTag(@NonNull String targetTag) {
        String safeTarget = targetTag == null ? "" : targetTag.trim();
        if (TextUtils.isEmpty(safeTarget)) {
            return;
        }
        returnTargetTag = safeTarget;
    }

    @NonNull
    public String externalGetReturnTargetTag() {
        return returnTargetTag;
    }


    public void externalSnapshotForNavigation() {
        persistPlaybackSnapshot(false);
    }

    public int externalGetCurrentIndex() {
        return currentIndex;
    }

    public boolean externalIsPlaying() {
        return isEffectivePlaying();
    }

    public boolean externalIsPlayingIntent() {
        return isPlaying;
    }

    public boolean externalIsShuffleEnabled() {
        return shuffleEnabled;
    }

    public int externalGetRepeatMode() {
        return repeatMode;
    }

    public void externalSetShuffleEnabled(boolean enabled) {
        setShuffleEnabled(enabled);
    }

    public int externalGetCurrentSeconds() {
        // When hidden, localProgressTicker is stopped so currentSeconds is stale.
        // Read live position from ExoPlayer if it's still active.
        if (isHidden() && localExoMediaPlayer != null) {
            try {
                return Math.max(0, localExoMediaPlayer.getCurrentPosition() / 1000);
            } catch (Exception ignored) {
            }
        }
        return Math.max(0, currentSeconds);
    }

    public int externalGetTotalSeconds() {
        if (isHidden() && localExoMediaPlayer != null) {
            try {
                int durationMs = localExoMediaPlayer.getDuration();
                if (durationMs > 0) return Math.max(1, durationMs / 1000);
            } catch (Exception ignored) {
            }
        }
        return Math.max(1, totalSeconds);
    }

    @NonNull
    public String externalGetCurrentVideoId() {
        if (currentIndex < 0 || currentIndex >= tracks.size()) {
            return "";
        }
        String value = tracks.get(currentIndex).videoId;
        return value == null ? "" : value;
    }

    public String externalGetCurrentTitle() {
        if (currentIndex < 0 || currentIndex >= tracks.size()) {
            return "";
        }
        String value = tracks.get(currentIndex).title;
        return value == null ? "" : value;
    }

    public String externalGetCurrentArtist() {
        if (currentIndex < 0 || currentIndex >= tracks.size()) {
            return "";
        }
        String value = tracks.get(currentIndex).artist;
        return value == null ? "" : value;
    }

    public String externalGetCurrentImageUrl() {
        if (currentIndex < 0 || currentIndex >= tracks.size()) {
            return "";
        }
        String value = tracks.get(currentIndex).imageUrl;
        return value == null ? "" : value;
    }

    @NonNull
    public List<String> externalGetQueueVideoIds() {
        List<String> queueVideoIds = new ArrayList<>(tracks.size());
        for (PlayerTrack track : tracks) {
            queueVideoIds.add(track.videoId == null ? "" : track.videoId);
        }
        return queueVideoIds;
    }

    public boolean externalMatchesQueue(@Nullable List<String> videoIds) {
        if (videoIds == null || videoIds.size() != tracks.size()) {
            return false;
        }

        for (int i = 0; i < tracks.size(); i++) {
            if (!TextUtils.equals(tracks.get(i).videoId, videoIds.get(i))) {
                return false;
            }
        }
        return true;
    }

    public void externalInsertNext(

            @NonNull String videoId,
            @NonNull String title,
            @NonNull String artist,
            @NonNull String duration,
            @NonNull String imageUrl
    ) {
        if (!isAdded()) return;
        PlayerTrack track = new PlayerTrack(
                safeValue(videoId),
                safeValue(title),
                safeValue(artist),
                safeValue(duration),
                safeValue(imageUrl)
        );

        int insertIndex = Math.max(0, Math.min(currentIndex + 1, tracks.size()));
        tracks.add(insertIndex, track);

        if (shuffleEnabled && originalQueueOrder.size() > 0) {
            String currentOriginalVideoId = externalGetCurrentVideoId();
            int origIndex = -1;
            for (int i = 0; i < originalQueueOrder.size(); i++) {
                if (TextUtils.equals(originalQueueOrder.get(i).videoId, currentOriginalVideoId)) {
                    origIndex = i;
                    break;
                }
            }
            int origInsertIndex = origIndex >= 0 ? Math.min(origIndex + 1, originalQueueOrder.size()) : originalQueueOrder.size();
            originalQueueOrder.add(origInsertIndex, track);
        } else {
            cacheOriginalQueueOrder();
        }

        refreshNextUp();
        persistPlaybackSnapshot(false);
        syncMiniStateWithPlaylist();
    }

    public void externalEnqueue(
            @NonNull String videoId,
            @NonNull String title,
            @NonNull String artist,
            @NonNull String duration,
            @NonNull String imageUrl
    ) {
        if (!isAdded()) return;
        PlayerTrack track = new PlayerTrack(
                safeValue(videoId),
                safeValue(title),
                safeValue(artist),
                safeValue(duration),
                safeValue(imageUrl)
        );

        tracks.add(track);

        if (shuffleEnabled && originalQueueOrder.size() > 0) {
            originalQueueOrder.add(track);
        } else {
            cacheOriginalQueueOrder();
        }

        refreshNextUp();
        persistPlaybackSnapshot(false);
        syncMiniStateWithPlaylist();
    }

    public void externalReplaceQueue(
            @NonNull ArrayList<String> videoIds,
            @NonNull ArrayList<String> titles,
            @NonNull ArrayList<String> artists,
            @NonNull ArrayList<String> durations,
            @NonNull ArrayList<String> images,
            int selectedIndex,
            boolean keepPlaying
    ) {
        externalReplaceQueueInternal(
                videoIds,
                titles,
                artists,
                durations,
                images,
                selectedIndex,
                keepPlaying,
                false
        );
    }

    public void externalReplaceQueueFromStart(
            @NonNull ArrayList<String> videoIds,
            @NonNull ArrayList<String> titles,
            @NonNull ArrayList<String> artists,
            @NonNull ArrayList<String> durations,
            @NonNull ArrayList<String> images,
            int selectedIndex,
            boolean keepPlaying
    ) {
        externalReplaceQueueInternal(
                videoIds,
                titles,
                artists,
                durations,
                images,
                selectedIndex,
                keepPlaying,
                true
        );
    }

    private void externalReplaceQueueInternal(
            @NonNull ArrayList<String> videoIds,
            @NonNull ArrayList<String> titles,
            @NonNull ArrayList<String> artists,
            @NonNull ArrayList<String> durations,
            @NonNull ArrayList<String> images,
            int selectedIndex,
            boolean keepPlaying,
            boolean startFromBeginning
    ) {
        if (!isAdded()) {
            return;
        }

        consecutiveStreamFailures = 0; // Reset failures on external queue replacement

        int targetIndexFromArgs = Math.max(0, selectedIndex);
        String targetVideoId = targetIndexFromArgs < videoIds.size()
                ? safeValue(videoIds.get(targetIndexFromArgs))
                : "";

        // Important: check if the track we ARE playing is the same as the one we WILL play
        boolean sameAsLoaded = !TextUtils.isEmpty(targetVideoId)
                && TextUtils.equals(targetVideoId, loadedVideoId);

        // Push current track to global history before replacing queue
        if (!sameAsLoaded && currentIndex >= 0 && currentIndex < tracks.size()) {
            PlayerTrack prev = tracks.get(currentIndex);
            if (prev != null && !TextUtils.isEmpty(prev.videoId)) {
                if (globalPlaybackHistory.size() >= MAX_GLOBAL_HISTORY) {
                    globalPlaybackHistory.pollLast();
                }
                globalPlaybackHistory.addFirst(prev);
            }
        }

        // ALWAYS stop previous audio before starting new playback to prevent overlap
        if (!sameAsLoaded) {
            // Stop any playing audio immediately to prevent mixing
            stopLocalProgressTicker();
            releaseLocalExoMediaPlayer();
            usingOfflineSource = false;
        }

        tracks.clear();

        int count = Math.min(videoIds.size(), Math.min(titles.size(), Math.min(artists.size(), Math.min(durations.size(), images.size()))));
        for (int i = 0; i < count; i++) {
            tracks.add(new PlayerTrack(
                    safeValue(videoIds.get(i)),
                    safeValue(titles.get(i)),
                    safeValue(artists.get(i)),
                    safeValue(durations.get(i)),
                    safeValue(images.get(i))
            ));
        }

        if (tracks.isEmpty()) {
            tracks.add(new PlayerTrack("", "Track", "Artist", "0:00", ""));
            currentIndex = 0;
            isPlaying = false;
            loadedVideoId = "";
            bindCurrentTrack(true);
            return;
        }

        currentIndex = Math.max(0, Math.min(selectedIndex, tracks.size() - 1));
        isPlaying = keepPlaying;
        
        // Only clear loadedVideoId if we are actually switching tracks OR starting from beginning.
        // This prevents the current song from RESTARTING when only the rest of the queue changed.
        if (!sameAsLoaded || startFromBeginning) {
            loadedVideoId = "";
        }

        cacheOriginalQueueOrder();
        if (shuffleEnabled) {
            randomizeQueueFromCurrentTrack();
        }

        if (startFromBeginning) {
            currentSeconds = 0;
        }

        if (!sameAsLoaded || startFromBeginning) {
            bindCurrentTrack(!startFromBeginning);
            if (isPlaying) {
                playCurrentTrack();
            }
        } else {
            // Queue changed but current song is the same: lightweight sync only.
            // Do NOT call bindCurrentTrack or playCurrentTrack to avoid any lag/pause.
            refreshNextUp();
            prefetchNextTrackStream();
        }
        
        persistPlaybackSnapshot(false);
    }

    public void enterMiniMode() {
        collapseToMiniMode(true);
    }

    public boolean externalTryEnterMiniMode() {
        if (!isHidden()) {
            return collapseToMiniMode(true);
        }
        return false;
    }

    private void setupBackPressToMiniMode() {
        if (!isAdded()) {
            return;
        }
        backPressedCallback = new OnBackPressedCallback(false) {
            @Override
            public void handleOnBackPressed() {
                collapseToMiniMode(true);
            }
        };
        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(), backPressedCallback);
        updateBackPressedCallbackEnabled(isHidden());
    }

    private void updateBackPressedCallbackEnabled(boolean hidden) {
        if (backPressedCallback == null) {
            return;
        }
        backPressedCallback.setEnabled(!hidden && isResumed());
    }

    private static boolean isVideoSource(@Nullable String source) {
        return source != null && source.toLowerCase(java.util.Locale.ROOT).endsWith(".mp4");
    }

    private void updatePlayerSurfaceForSource() {
        if (ivPlayerCover == null) return;
        if (currentSourceIsVideo) {
            ivPlayerCover.setVisibility(View.GONE);
            // Fixed height (same as cover art) so controls never shift.
            // Full-width (no margins) for video — better rendering performance.
            if (flPlayerHero != null && getContext() != null) {
                int heroHeightPx = dpToPx(PLAYER_HERO_DEFAULT_HEIGHT_DP);
                androidx.constraintlayout.widget.ConstraintLayout.LayoutParams p =
                        (androidx.constraintlayout.widget.ConstraintLayout.LayoutParams) flPlayerHero.getLayoutParams();
                if (p != null) {
                    p.leftMargin = 0;
                    p.rightMargin = 0;
                    p.height = heroHeightPx;
                    flPlayerHero.setLayoutParams(p);
                }
                flPlayerHero.setBackground(androidx.core.content.ContextCompat.getDrawable(getContext(), R.drawable.bg_player_cover_flat));
            }
            // Pure black backdrop for video
            if (playerBackgroundContainer != null) {
                playerBackgroundContainer.animate().cancel();
                playerBackgroundContainer.setBackgroundColor(0xFF000000);
                playerBackgroundContainer.setTag(R.id.tag_backdrop_top_color, 0xFF000000);
            }
        } else {
            ivPlayerCover.setVisibility(View.VISIBLE);
            ivPlayerCover.setClickable(true);
            if (pbVideoLoading != null) pbVideoLoading.setVisibility(View.GONE);
        }
    }

    private boolean collapseToMiniMode(boolean animate) {
        if (!isAdded()) {
            return false;
        }
        if (swipeDismissGestureActive || swipeDismissAnimationRunning) {
            return true;
        }
        if (collapsingToMiniMode) {
            return true;
        }

        FragmentManager fm = getParentFragmentManager();
        if (fm.isStateSaved()) {
            return false;
        }

        collapsingToMiniMode = true;
        persistPlaybackSnapshot(false);
        syncMiniStateWithPlaylist();
        Fragment target = resolveReturnTarget(fm);

        androidx.fragment.app.FragmentTransaction transaction = fm.beginTransaction()
                .setReorderingAllowed(true);

        if (target != null && target != this && target.isAdded()) {
            transaction
                    .setCustomAnimations(
                            R.anim.hold,
                            R.anim.player_screen_exit
                    )
                    .hide(this)
                    .show(target);
            transaction.commit();
        } else {
            transaction
                    .setCustomAnimations(
                            R.anim.hold,
                            R.anim.player_screen_exit
                    )
                    .remove(this);
            transaction.commit();
        }

        collapsingToMiniMode = false;
        return true;
    }

    @Nullable
    private Fragment findAddedByTag(@NonNull FragmentManager fm, @NonNull String tag) {
        Fragment fragment = fm.findFragmentByTag(tag);
        if (fragment != null && fragment.isAdded() && fragment != this) {
            return fragment;
        }
        return null;
    }

    @Nullable
    private Fragment resolveReturnTarget(@NonNull FragmentManager fm) {
        if (!TextUtils.isEmpty(returnTargetTag)) {
            Fragment preferred = fm.findFragmentByTag(returnTargetTag);
            if (preferred != null && preferred.isAdded()) {
                return preferred;
            }
        }

        Fragment playlist = fm.findFragmentByTag(TAG_PLAYLIST_DETAIL);
        if (playlist != null && playlist.isAdded()) {
            return playlist;
        }

        Fragment music = fm.findFragmentByTag(TAG_MODULE_MUSIC);
        if (music != null && music.isAdded()) {
            return music;
        }

        Fragment equalizer = findAddedByTag(fm, "module_equalizer");
        if (equalizer != null) {
            return equalizer;
        }

        Fragment apps = findAddedByTag(fm, "module_apps");
        if (apps != null) {
            return apps;
        }

        Fragment settings = findAddedByTag(fm, "module_settings");
        if (settings != null) {
            return settings;
        }

        return null;
    }


    private void openQueuePlaylistDetail() {
        if (!isAdded()) {
            return;
        }

        FragmentManager fm = getParentFragmentManager();
        if (fm.isStateSaved()) {
            return;
        }

        persistPlaybackSnapshot(false);
        syncMiniStateWithPlaylist();

        Fragment existingDetail = fm.findFragmentByTag(TAG_PLAYLIST_DETAIL);
        if (existingDetail != null && existingDetail.isAdded()) {
            fm.beginTransaction()
                    .setReorderingAllowed(true)
                    .hide(this)
                    .show(existingDetail)
                    .commit();
            return;
        }

        SharedPreferences prefs = requireContext().getSharedPreferences(PREFS_PLAYER_STATE, Activity.MODE_PRIVATE);
        String playlistId = safeValue(prefs.getString(PREF_LAST_PLAYLIST_ID, ""));
        if (TextUtils.isEmpty(playlistId)) {
            
            return;
        }

        String playlistTitle = safeValue(prefs.getString(PREF_LAST_PLAYLIST_TITLE, "Lista"));
        String playlistSubtitle = safeValue(prefs.getString(PREF_LAST_PLAYLIST_SUBTITLE, "Playlist"));
        String playlistThumbnail = safeValue(prefs.getString(PREF_LAST_PLAYLIST_THUMBNAIL, ""));
        String accessToken = safeValue(prefs.getString(PREF_LAST_YOUTUBE_ACCESS_TOKEN, ""));
        String normalizedPlaylistId = normalizeLikedPlaylistId(playlistId, playlistTitle, playlistSubtitle);

        PlaylistDetailFragment detailFragment = PlaylistDetailFragment.newInstance(
                normalizedPlaylistId,
                TextUtils.isEmpty(playlistTitle) ? "Lista" : playlistTitle,
                TextUtils.isEmpty(playlistSubtitle) ? "Playlist" : playlistSubtitle,
                playlistThumbnail,
                accessToken
        );

        fm.beginTransaction()
                .setReorderingAllowed(true)
                .hide(this)
                .add(R.id.fragmentContainer, detailFragment, TAG_PLAYLIST_DETAIL)
                .addToBackStack(TAG_PLAYLIST_DETAIL)
                .commit();
    }

    @NonNull
    private String normalizeLikedPlaylistId(
            @NonNull String playlistId,
            @NonNull String playlistTitle,
            @NonNull String playlistSubtitle
    ) {
        if (YouTubeMusicService.SPECIAL_LIKED_VIDEOS_ID.equals(playlistId)) {
            return playlistId;
        }

        String title = playlistTitle.toLowerCase(Locale.US);
        String subtitle = playlistSubtitle.toLowerCase(Locale.US);
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

    private void syncMiniStateWithPlaylist() {
        if (!isAdded()) {
            return;
        }
        Fragment playlist = getParentFragmentManager().findFragmentByTag("playlist_detail");
        if (playlist instanceof PlaylistDetailFragment) {
            ((PlaylistDetailFragment) playlist).syncMiniStateFromPlayer(currentIndex, isEffectivePlaying());
        }
    }

    private void updatePlayPauseIcon() {
        if (btnPlayPause == null) {
            return;
        }
        btnPlayPause.setImageResource(isPlaying
                ? R.drawable.ic_player_pause
                : R.drawable.ic_player_play);
        
        if (animatedEqPlayer != null) {
            animatedEqPlayer.setAnimating(isEffectivePlaying());
        }
    }

    private void updateMediaSessionMetadata() {
        if (mediaSession == null || tracks.isEmpty() || currentIndex < 0 || currentIndex >= tracks.size()) {
            return;
        }

        PlayerTrack track = tracks.get(currentIndex);
        MediaMetadataCompat.Builder metadataBuilder = new MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, track.title)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, track.artist)
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, Math.max(1, totalSeconds) * 1000L);

        if (!TextUtils.isEmpty(track.videoId)
                && TextUtils.equals(track.videoId, mediaSessionArtworkVideoId)
                && mediaSessionArtwork != null
                && !mediaSessionArtwork.isRecycled()) {
            metadataBuilder
                    .putBitmap(MediaMetadataCompat.METADATA_KEY_ART, mediaSessionArtwork)
                    .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, mediaSessionArtwork)
                    .putBitmap(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON, mediaSessionArtwork);
        } else {
            Log.w(TAG, "updateMediaSessionMetadata: artwork MISSING for videoId=" + track.videoId
                    + " cachedVideoId=" + mediaSessionArtworkVideoId
                    + " artworkNull=" + (mediaSessionArtwork == null)
                    + " recycled=" + (mediaSessionArtwork != null && mediaSessionArtwork.isRecycled()));
            // Fallback to app icon if no artwork is available
            try {
                Drawable iconDrawable = ContextCompat.getDrawable(requireContext(), R.mipmap.ic_launcher);
                if (iconDrawable instanceof android.graphics.drawable.BitmapDrawable) {
                    Bitmap iconBitmap = ((android.graphics.drawable.BitmapDrawable) iconDrawable).getBitmap();
                    metadataBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ART, iconBitmap)
                                   .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, iconBitmap)
                                   .putBitmap(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON, iconBitmap);
                }
            } catch (Exception ignored) {}
        }

        if (!TextUtils.isEmpty(track.imageUrl)) {
            metadataBuilder.putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI, track.imageUrl);
        }
        if (!TextUtils.isEmpty(track.videoId)) {
            metadataBuilder.putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, track.videoId);
        }

        mediaSession.setMetadata(metadataBuilder.build());
    }

    private void updateMediaSessionState() {
        if (mediaSession == null) {
            return;
        }

        int state = isPlaying ? PlaybackStateCompat.STATE_PLAYING : PlaybackStateCompat.STATE_PAUSED;
        float speed = isPlaying ? 1f : 0f;
        
        playbackStateBuilder.setState(state, Math.max(0, currentSeconds) * 1000L, speed);
        mediaSession.setPlaybackState(playbackStateBuilder.build());
        mediaSession.setActive(true);
    }


    private void updateMediaNotification() {
        if (mediaSession == null || tracks.isEmpty() || currentIndex < 0 || currentIndex >= tracks.size()) {
            if (persistentAppContext != null) {
                PlaybackKeepAliveService.stop(persistentAppContext);
            }
            return;
        }
        if (persistentAppContext == null) return;
        PlayerTrack track = tracks.get(currentIndex);

        // Intento de abrir la app al tocar la notificacion
        Intent openAppIntent = new Intent(persistentAppContext, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        int pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            pendingFlags |= PendingIntent.FLAG_IMMUTABLE;
        }
        PendingIntent contentIntent = PendingIntent.getActivity(persistentAppContext, 8701, openAppIntent, pendingFlags);

        PendingIntent prevIntent = androidx.media.session.MediaButtonReceiver.buildMediaButtonPendingIntent(
                persistentAppContext, PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS);
        PendingIntent playPauseIntent = androidx.media.session.MediaButtonReceiver.buildMediaButtonPendingIntent(
                persistentAppContext, isPlaying ? PlaybackStateCompat.ACTION_PAUSE : PlaybackStateCompat.ACTION_PLAY);
        PendingIntent nextIntent = androidx.media.session.MediaButtonReceiver.buildMediaButtonPendingIntent(
                persistentAppContext, PlaybackStateCompat.ACTION_SKIP_TO_NEXT);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(persistentAppContext, MEDIA_NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification_cat)
                .setContentTitle(track.title)
                .setContentText(track.artist)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
                .setContentIntent(contentIntent)
                .setSilent(true)
                .setOnlyAlertOnce(true)
                .setOngoing(isPlaying)
                .addAction(android.R.drawable.ic_media_previous, "Anterior", prevIntent)
                .addAction(
                        isPlaying ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play,
                        isPlaying ? "Pausar" : "Reproducir",
                        playPauseIntent
                )
                .addAction(android.R.drawable.ic_media_next, "Siguiente", nextIntent)
                .setStyle(new androidx.media.app.NotificationCompat.MediaStyle()
                        .setMediaSession(mediaSession.getSessionToken())
                        .setShowActionsInCompactView(0, 1, 2));

        if (mediaSessionArtwork != null && !mediaSessionArtwork.isRecycled()) {
            builder.setLargeIcon(mediaSessionArtwork);
        } else {
            Log.w(TAG, "updateMediaNotification: NO artwork for videoId=" + track.videoId
                    + " cachedId=" + mediaSessionArtworkVideoId);
        }

        android.app.Notification notification = builder.build();
        NotificationManagerCompat.from(persistentAppContext).notify(MEDIA_NOTIFICATION_ID, notification);

        // Keep process alive while playing so the MediaSession binder stays valid in background
        if (isPlaying) {
            PlaybackKeepAliveService.start(persistentAppContext, notification);
        } else {
            PlaybackKeepAliveService.stop(persistentAppContext);
        }
    }

    private void ensureMediaNotificationChannel() {
        if (persistentAppContext == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        NotificationManager manager = persistentAppContext.getSystemService(NotificationManager.class);
        if (manager == null || manager.getNotificationChannel(MEDIA_NOTIFICATION_CHANNEL_ID) != null) {
            return;
        }
        NotificationChannel channel = new NotificationChannel(
                MEDIA_NOTIFICATION_CHANNEL_ID,
                "Reproducción",
                NotificationManager.IMPORTANCE_LOW
        );
        channel.setShowBadge(false);
        manager.createNotificationChannel(channel);
    }

    private void hydrateTracksFromArgs() {
        Bundle args = getArguments();
        if (args == null) {
            tracks.add(new PlayerTrack("", "Track", "Artist", "0:00", ""));
            currentIndex = 0;
            cacheOriginalQueueOrder();
            return;
        }

        ArrayList<String> ids = safeList(args.getStringArrayList(ARG_VIDEO_IDS));
        ArrayList<String> titles = safeList(args.getStringArrayList(ARG_TITLES));
        ArrayList<String> artists = safeList(args.getStringArrayList(ARG_ARTISTS));
        ArrayList<String> durations = safeList(args.getStringArrayList(ARG_DURATIONS));
        ArrayList<String> images = safeList(args.getStringArrayList(ARG_IMAGES));
        currentIndex = args.getInt(ARG_SELECTED_INDEX, 0);
        isPlaying = args.getBoolean(ARG_START_PLAYING, true);
        isTemporaryPlayer = args.getBoolean(ARG_IS_TEMPORARY_PLAYER, false);

        int count = Math.min(ids.size(), Math.min(titles.size(), Math.min(artists.size(), Math.min(durations.size(), images.size()))));
        for (int i = 0; i < count; i++) {
            tracks.add(new PlayerTrack(
                    safeValue(ids.get(i)),
                    safeValue(titles.get(i)),
                    safeValue(artists.get(i)),
                    safeValue(durations.get(i)),
                    safeValue(images.get(i))
            ));
        }

        if (tracks.isEmpty()) {
            tracks.add(new PlayerTrack("", "Track", "Artist", "0:00", ""));
            currentIndex = 0;
        }

        cacheOriginalQueueOrder();
        if (shuffleEnabled) {
            currentIndex = Math.max(0, Math.min(currentIndex, tracks.size() - 1));
            randomizeQueueFromCurrentTrack();
        }
    }

    private void cacheOriginalQueueOrder() {
        originalQueueOrder.clear();
        originalQueueOrder.addAll(tracks);
    }

    private void randomizeQueueFromCurrentTrack() {
        randomizeQueueFromCurrentTrack(null);
    }

    private void randomizeQueueFromCurrentTrack(@Nullable String avoidFirstVideoId) {
        if (tracks.size() <= 1 || currentIndex < 0 || currentIndex >= tracks.size()) {
            return;
        }

        PlayerTrack currentTrack = tracks.get(currentIndex);
        ArrayList<PlayerTrack> upcoming = new ArrayList<>();
        for (int i = 0; i < tracks.size(); i++) {
            if (i == currentIndex) {
                continue;
            }
            upcoming.add(tracks.get(i));
        }

        Collections.shuffle(upcoming, random);

        // Avoid starting the new cycle with the track that just finished
        if (!TextUtils.isEmpty(avoidFirstVideoId) && !upcoming.isEmpty()) {
            if (TextUtils.equals(upcoming.get(0).videoId, avoidFirstVideoId)) {
                // Swap it with a random position further in the list
                int swapIdx = 1 + random.nextInt(Math.max(1, upcoming.size() - 1));
                if (swapIdx < upcoming.size()) {
                    PlayerTrack tmp = upcoming.get(0);
                    upcoming.set(0, upcoming.get(swapIdx));
                    upcoming.set(swapIdx, tmp);
                }
            }
        }

        tracks.clear();
        tracks.add(currentTrack);
        tracks.addAll(upcoming);
        currentIndex = 0;
    }

    private void restoreOriginalQueueOrder() {
        if (originalQueueOrder.isEmpty()) {
            return;
        }

        String currentVideoId = externalGetCurrentVideoId();
        tracks.clear();
        tracks.addAll(originalQueueOrder);

        int restoredIndex = findTrackIndexByVideoId(currentVideoId);
        currentIndex = restoredIndex >= 0
                ? restoredIndex
                : Math.max(0, Math.min(currentIndex, tracks.size() - 1));
    }

    private int findTrackIndexByVideoId(@Nullable String videoId) {
        if (TextUtils.isEmpty(videoId)) {
            return -1;
        }

        for (int i = 0; i < tracks.size(); i++) {
            if (TextUtils.equals(videoId, tracks.get(i).videoId)) {
                return i;
            }
        }
        return -1;
    }

    private void applyNextUpOrderToQueue() {
        if (tracks.isEmpty() || currentIndex < 0 || currentIndex >= tracks.size()) {
            return;
        }

        tracks.clear();
        tracks.addAll(nextUpTracks);
        currentIndex = 0;

        if (!shuffleEnabled) {
            cacheOriginalQueueOrder();
        }

        persistPlaybackSnapshot(false);
        
        // CRITICAL: Clear prefetch cache because the "next" song has changed
        prefetchedNextVideoId = null;
        
        syncMiniStateWithPlaylist();
    }

    @NonNull
    private ArrayList<String> safeList(@Nullable ArrayList<String> list) {
        return list == null ? new ArrayList<>() : list;
    }

    @NonNull
    private String safeValue(@Nullable String value) {
        return value == null ? "" : value;
    }

    private void persistPlaybackSnapshot(boolean forcePaused) {
        persistPlaybackSnapshot(forcePaused, false);
    }

    private void persistPlaybackSnapshot(boolean forcePaused, boolean synchronous) {
        if (!isAdded() || tracks.isEmpty()) {
            return;
        }

        final List<PlayerTrack> tracksCopy = new ArrayList<>(tracks);
        final int index = currentIndex;
        final int current = externalGetCurrentSeconds();
        final int total = externalGetTotalSeconds();
        final boolean effectivelyPlaying = forcePaused ? false : isEffectivePlaying();
        final Context context = requireContext().getApplicationContext();

        Runnable task = () -> {
            try {
                List<PlaybackHistoryStore.QueueTrack> queue = new ArrayList<>(tracksCopy.size());
                for (PlayerTrack track : tracksCopy) {
                    queue.add(new PlaybackHistoryStore.QueueTrack(
                            track.videoId,
                            track.title,
                            track.artist,
                            track.duration,
                            track.imageUrl
                    ));
                }

                int safeIndex = Math.max(0, Math.min(index, Math.max(0, tracksCopy.size() - 1)));
                PlaybackHistoryStore.save(
                        context,
                        queue,
                        safeIndex,
                        Math.max(1, total),
                        effectivelyPlaying,
                        synchronous
                );

                // Also persist fallback prefs so the mini-player can restore
                // even if the snapshot gets corrupted. Uses commit() because
                // this task already runs on a background thread.
                PlayerTrack currentTrack = (safeIndex >= 0 && safeIndex < tracksCopy.size())
                        ? tracksCopy.get(safeIndex) : null;
                if (currentTrack != null && !TextUtils.isEmpty(currentTrack.videoId)) {
                    context.getSharedPreferences("player_state", Activity.MODE_PRIVATE)
                            .edit()
                            .putString("stream_last_video_id", currentTrack.videoId)
                            .putString("stream_last_track_title", currentTrack.title != null ? currentTrack.title : "")
                            .putString("stream_last_track_artist", currentTrack.artist != null ? currentTrack.artist : "")
                            .putString("stream_last_track_duration", currentTrack.duration != null ? currentTrack.duration : "")
                            .putString("stream_last_track_image", currentTrack.imageUrl != null ? currentTrack.imageUrl : "")
                            .putBoolean("stream_last_is_playing", effectivelyPlaying)
                            .commit();
                }
                // Notify listeners that the playback snapshot was persisted
                try {
                    PlaybackEventBus.notifyPlaybackSnapshotUpdated();
                } catch (Exception ignored) {
                }
            } catch (Exception ignored) {
            }
        };

        if (synchronous) {
            task.run();
        } else {
            persistenceExecutor.execute(task);
        }
    }

    private void cancelNextUpReveal() {
        if (nextUpRevealRunnable != null) {
            localProgressHandler.removeCallbacks(nextUpRevealRunnable);
            nextUpRevealRunnable = null;
        }
        nextUpRevealCursor = 0;
    }

    private void appendNextUpBatch(int totalCount, int batchSize) {
        if (nextUpAdapter == null || tracks.size() <= 1 || batchSize <= 0) {
            return;
        }

        int start = nextUpRevealCursor;
        int end = Math.min(totalCount, start + batchSize);
        if (end <= start) {
            return;
        }

        for (int offset = start + 1; offset <= end; offset++) {
            int idx = (currentIndex + offset) % tracks.size();
            if (nextUpTracks.size() < MAX_NEXT_UP) {
                PlayerTrack t = tracks.get(idx);
                nextUpTracks.add(t);
                nextUpAdapter.getItems().add(t);
            }
        }

        nextUpAdapter.notifyItemRangeInserted(start, end - start);
        nextUpRevealCursor = end;
    }

    private void refreshNextUp() {
        cancelNextUpReveal();
        nextUpTracks.clear();
        if (nextUpAdapter == null) {
            return;
        }

        if (tracks.size() <= 1) {
            nextUpAdapter.setItems(nextUpTracks);
            return;
        }

        nextUpAdapter.setItems(nextUpTracks);

        int maxQueue = 10;
        int total = Math.min(tracks.size() - 1, maxQueue);
        appendNextUpBatch(total, maxQueue);
    }

    private int parseDurationSeconds(@NonNull String duration) {
        if (duration.isEmpty() || duration.contains("--")) {
            return 1;
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
            return 1;
        }
        return 1;
    }

    @NonNull
    private String formatSeconds(int seconds) {
        int safe = Math.max(0, seconds);
        int hours = safe / 3600;
        int mins = (safe % 3600) / 60;
        int secs = safe % 60;
        if (hours > 0) {
            return String.format(Locale.US, "%02d:%02d:%02d", hours, mins, secs);
        }
        return String.format(Locale.US, "%02d:%02d", mins, secs);
    }

    private static final class PlayerTrack {
        final String videoId;
        final String title;
        final String artist;
        final String duration;
        final String imageUrl;

        PlayerTrack(@NonNull String videoId, @NonNull String title, @NonNull String artist, @NonNull String duration, @NonNull String imageUrl) {
            this.videoId = videoId;
            this.title = title;
            this.artist = artist;
            this.duration = duration;
            this.imageUrl = imageUrl;
        }
    }

    private static final class SocialStats {
        final String likeCount;
        final String dislikeCount;
        final String commentCount;
        final boolean unavailable;

        SocialStats(
                @NonNull String likeCount,
                @NonNull String dislikeCount,
                @NonNull String commentCount,
                boolean unavailable
        ) {
            this.likeCount = likeCount;
            this.dislikeCount = dislikeCount;
            this.commentCount = commentCount;
            this.unavailable = unavailable;
        }

        @NonNull
        static SocialStats loading() {
            return new SocialStats("0", "", "0", true);
        }

        @NonNull
        static SocialStats unavailable() {
            return new SocialStats("0", "", "0", true);
        }
    }

    private static final class NextUpAdapter extends RecyclerView.Adapter<NextUpAdapter.NextUpViewHolder> {
        // ✅ Cache transformation to avoid creating new object per bind
        private static final YouTubeCropTransformation SHARED_YT_CROP = new YouTubeCropTransformation();

        interface OnNextUpTap {
            void onTap(int position);
        }

        interface OnDragStart {
            void onStartDrag(@NonNull NextUpViewHolder holder);
        }

        private final OnNextUpTap onNextUpTap;
        private final OnDragStart onDragStart;

        // Single mutable list — the direct source of truth, no DiffUtil interference
        private final List<PlayerTrack> items = new ArrayList<>();

        NextUpAdapter(
                @NonNull OnNextUpTap onNextUpTap,
                @NonNull OnDragStart onDragStart
        ) {
            this.onNextUpTap = onNextUpTap;
            this.onDragStart = onDragStart;
        }

        void setItems(@NonNull List<PlayerTrack> newItems) {
            items.clear();
            items.addAll(newItems);
            notifyDataSetChanged();
        }

        @NonNull
        List<PlayerTrack> getItems() {
            return items;
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        boolean moveItem(int fromPosition, int toPosition) {
            if (fromPosition == toPosition) {
                return false;
            }
            if (fromPosition == 0 || toPosition < 1) {
                return false; // Restriction: cannot move 'Now Playing' or above
            }
            if (fromPosition >= items.size() || toPosition >= items.size()) {
                return false;
            }
            // Mutate items directly — single source of truth, no DiffUtil interference
            PlayerTrack moved = items.remove(fromPosition);
            items.add(toPosition, moved);
            notifyItemMoved(fromPosition, toPosition);
            return true;
        }

        @NonNull
        @Override
        public NextUpViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_song_next_up, parent, false);
            return new NextUpViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull NextUpViewHolder holder, int position) {
            PlayerTrack item = items.get(position);
            if (item == null) return;
            
            holder.tvNextUpTitle.setText(item.title);
            
            // Position 0 is the currently playing track
            if (position == 0) {
                holder.tvNextUpArtist.setText("Reproduciendo ahora");
                holder.tvNextUpArtist.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.stitch_blue)); // Primary color
            } else {
                holder.tvNextUpArtist.setText(item.artist);
                holder.tvNextUpArtist.setTextColor(Color.parseColor("#A0A0A0")); // Default gray
            }

            holder.ivNextUpArt.setImageDrawable(null);
            if (!TextUtils.isEmpty(item.imageUrl)) {
                String imageUrl = item.imageUrl.trim();
                // ✅ Use cached transformation instead of creating new one
                Glide.with(holder.itemView)
                    .load(imageUrl)
                    .transform(SHARED_YT_CROP)
                    .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.ALL)
                    .transition(com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions.withCrossFade())
                    .into(holder.ivNextUpArt);
            }

            holder.itemView.setOnClickListener(v -> {
                int adapterPosition = holder.getAdapterPosition();
                if (adapterPosition == RecyclerView.NO_POSITION) {
                    return;
                }
                onNextUpTap.onTap(adapterPosition);
            });

            holder.ivQueueDragHandle.setOnTouchListener((v, event) -> {
                if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                    onDragStart.onStartDrag(holder);
                    return true;
                }
                return false;
            });
        }

        static final class NextUpViewHolder extends RecyclerView.ViewHolder {
            final ImageView ivNextUpArt;
            final TextView tvNextUpTitle;
            final TextView tvNextUpArtist;
            final ImageView ivQueueDragHandle;

            NextUpViewHolder(@NonNull View itemView) {
                super(itemView);
                ivNextUpArt = itemView.findViewById(R.id.ivNextUpArt);
                tvNextUpTitle = itemView.findViewById(R.id.tvNextUpTitle);
                tvNextUpArtist = itemView.findViewById(R.id.tvNextUpArtist);
                ivQueueDragHandle = itemView.findViewById(R.id.ivQueueDragHandle);
            }
        }
    }

    private void updateDeviceInfo() {
        if (!isAdded()) return;
        AudioManager audioManager = (AudioManager) requireContext().getSystemService(Context.AUDIO_SERVICE);
        if (audioManager == null) {
            return;
        }
        String deviceName = "Este dispositivo";
        int iconRes = R.drawable.ic_output_speaker;

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            AudioDeviceInfo[] devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS);
            for (AudioDeviceInfo device : devices) {
                if (device.getType() == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || 
                    device.getType() == AudioDeviceInfo.TYPE_BLUETOOTH_SCO) {
                    deviceName = device.getProductName().toString();
                    iconRes = R.drawable.ic_output_bluetooth;
                    break;
                } else if (device.getType() == AudioDeviceInfo.TYPE_WIRED_HEADPHONES || 
                           device.getType() == AudioDeviceInfo.TYPE_WIRED_HEADSET) {
                    deviceName = "Auriculares";
                    iconRes = R.drawable.ic_output_wired;
                    break;
                }
            }
        }
        
        if (tvDeviceName != null) tvDeviceName.setText(deviceName);
        if (ivDeviceIcon != null) ivDeviceIcon.setImageResource(iconRes);
    }

    private void setupAudioDeviceCallback() {
        if (!isAdded() || Build.VERSION.SDK_INT < Build.VERSION_CODES.M || audioDeviceCallbackRegistered) {
            return;
        }
        AudioManager audioManager = (AudioManager) requireContext().getSystemService(Context.AUDIO_SERVICE);
        if (audioManager == null) {
            return;
        }
        if (audioDeviceCallback == null) {
            audioDeviceCallback = new AudioDeviceCallback() {
                @Override
                public void onAudioDevicesAdded(AudioDeviceInfo[] addedDevices) {
                    localProgressHandler.post(() -> {
                        if (isAdded()) {
                            updateDeviceInfo();
                        }
                    });
                }

                @Override
                public void onAudioDevicesRemoved(AudioDeviceInfo[] removedDevices) {
                    localProgressHandler.post(() -> {
                        if (isAdded()) {
                            updateDeviceInfo();
                        }
                    });
                }
            };
        }
        audioManager.registerAudioDeviceCallback(audioDeviceCallback, localProgressHandler);
        audioDeviceCallbackRegistered = true;
    }

    private void cleanupAudioDeviceCallback() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || !audioDeviceCallbackRegistered) {
            return;
        }
        Context context = getContext();
        if (context == null) {
            return;
        }
        AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        if (audioManager != null && audioDeviceCallback != null) {
            audioManager.unregisterAudioDeviceCallback(audioDeviceCallback);
        }
        audioDeviceCallbackRegistered = false;
    }

    private void showPlayerOptionsSheet() {
        if (!isAdded() || btnPlayerMore == null) return;

        float density = getResources().getDisplayMetrics().density;

        // Build popup content
        android.widget.LinearLayout popupContent = new android.widget.LinearLayout(requireContext());
        popupContent.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        popupContent.setGravity(android.view.Gravity.CENTER_VERTICAL);
        popupContent.setBackgroundResource(R.drawable.bg_popup_menu);
        int hPad = (int) (16 * density);
        int vPad = (int) (14 * density);
        popupContent.setPadding(hPad, vPad, hPad, vPad);

        ImageView ivEq = new ImageView(requireContext());
        ivEq.setImageResource(R.drawable.ic_equalizer);
        ivEq.setColorFilter(android.graphics.Color.WHITE);
        int iconSize = (int) (20 * density);
        android.widget.LinearLayout.LayoutParams iconParams = new android.widget.LinearLayout.LayoutParams(iconSize, iconSize);
        ivEq.setLayoutParams(iconParams);
        popupContent.addView(ivEq);

        TextView tvEq = new TextView(requireContext());
        tvEq.setText("Ecualizador");
        tvEq.setTextColor(android.graphics.Color.WHITE);
        tvEq.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 14);
        tvEq.setTypeface(null, android.graphics.Typeface.BOLD);
        tvEq.setPadding((int) (10 * density), 0, 0, 0);
        popupContent.addView(tvEq);

        android.widget.PopupWindow popup = new android.widget.PopupWindow(
                popupContent,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                true);
        popup.setElevation(12 * density);
        popup.setOutsideTouchable(true);
        popup.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));

        popupContent.setOnClickListener(v -> {
            popup.dismiss();
            if (getActivity() instanceof MainActivity) {
                collapseToMiniMode(true);
                ((MainActivity) getActivity()).openEqualizerFromPlayer();
            }
        });

        // Show below the anchor button
        popup.showAsDropDown(btnPlayerMore, 0, (int) (4 * density), android.view.Gravity.END);
    }

    private void showSaveToPlaylistSheetFromPlayer() {
        if (!isAdded() || tracks.isEmpty() || currentIndex < 0 || currentIndex >= tracks.size()) return;
        PlayerTrack current = tracks.get(currentIndex);
        if (TextUtils.isEmpty(current.videoId)) return;
        showSaveToPlaylistSheet(current, null);
    }

    private void showSaveToPlaylistSheet(@NonNull PlayerTrack track, @Nullable String previousPlaylistKey) {
        if (!isAdded()) return;
        Context ctx = requireContext();

        BottomSheetDialog saveDialog = new BottomSheetDialog(ctx);
        View sheet = getLayoutInflater().inflate(R.layout.bottom_sheet_save_to_playlist, null);
        saveDialog.setContentView(sheet);

        View parent = (View) sheet.getParent();
        if (parent != null) parent.setBackgroundColor(android.graphics.Color.TRANSPARENT);

        ImageView ivClose = sheet.findViewById(R.id.ivSaveClose);
        ivClose.setOnClickListener(v -> saveDialog.dismiss());

        final String[] lastAddedKey = {null};
        final String[] lastAddedName = {null};
        final boolean[] didRemove = {false};

        sheet.findViewById(R.id.btnSaveCancel).setOnClickListener(v -> saveDialog.dismiss());
        sheet.findViewById(R.id.btnSaveConfirm).setOnClickListener(v -> {
            saveDialog.dismiss();
            if (lastAddedKey[0] != null && lastAddedName[0] != null) {
                showSavedInPlaylistBarPlayer(track, lastAddedKey[0], lastAddedName[0]);
            } else if (didRemove[0]) {
                showRemovedFromPlaylistBarPlayer();
            }
        });

        android.widget.LinearLayout llList = sheet.findViewById(R.id.llSavePlaylistList);
        llList.removeAllViews();

        float density = ctx.getResources().getDisplayMetrics().density;
        int thumbSizePx = (int) (48 * density);

        // Favoritos
        java.util.List<FavoritesPlaylistStore.FavoriteTrack> favs = FavoritesPlaylistStore.loadFavorites(ctx);
        java.util.List<String> favUrls = new ArrayList<>();
        for (FavoritesPlaylistStore.FavoriteTrack f : favs) {
            if (f != null && !TextUtils.isEmpty(f.imageUrl)) {
                if (!favUrls.contains(f.imageUrl)) favUrls.add(f.imageUrl);
                if (favUrls.size() >= 4) break;
            }
        }

        {
            View row = LayoutInflater.from(ctx).inflate(R.layout.item_save_playlist_row, llList, false);
            ImageView ivThumb = row.findViewById(R.id.ivSavePlaylistThumb);
            android.widget.TextView tvName = row.findViewById(R.id.tvSavePlaylistName);
            android.widget.TextView tvCount = row.findViewById(R.id.tvSavePlaylistCount);
            ImageView ivCheck = row.findViewById(R.id.ivSaveCheck);
            tvName.setText(FavoritesPlaylistStore.PLAYLIST_TITLE);
            tvCount.setText(favs.size() + " pistas");
            if (favUrls.size() >= 4) {
                PlaylistGridArtLoader.load(ivThumb, favUrls, thumbSizePx);
            } else if (!favUrls.isEmpty()) {
                Glide.with(this).load(favUrls.get(0)).centerCrop().into(ivThumb);
            }
            boolean isIn = isTrackInPlaylist(ctx, track.videoId, FavoritesPlaylistStore.PLAYLIST_ID);
            if (ivCheck != null) ivCheck.setVisibility(isIn ? View.VISIBLE : View.GONE);
            final boolean[] checked = {isIn};
            row.setOnClickListener(v -> {
                if (checked[0]) {
                    removeTrackFromPlaylistByKey(FavoritesPlaylistStore.PLAYLIST_ID, track.videoId);
                    checked[0] = false;
                    didRemove[0] = true;
                    if (ivCheck != null) ivCheck.setVisibility(View.GONE);
                    if (FavoritesPlaylistStore.PLAYLIST_ID.equals(lastAddedKey[0])) {
                        lastAddedKey[0] = null;
                        lastAddedName[0] = null;
                    }
                } else {
                    addTrackToPlaylistByKey(FavoritesPlaylistStore.PLAYLIST_ID, track);
                    checked[0] = true;
                    if (ivCheck != null) ivCheck.setVisibility(View.VISIBLE);
                    lastAddedKey[0] = FavoritesPlaylistStore.PLAYLIST_ID;
                    lastAddedName[0] = FavoritesPlaylistStore.PLAYLIST_TITLE;
                }
                int count = getPlaylistTrackCount(ctx, FavoritesPlaylistStore.PLAYLIST_ID);
                tvCount.setText(count + " pistas");
            });
            llList.addView(row);
        }

        // Custom playlists
        java.util.List<String> customNames = CustomPlaylistsStore.INSTANCE.getAllPlaylistNames(ctx);
        for (String name : customNames) {
            String playlistKey = CustomPlaylistsStore.CUSTOM_PLAYLIST_PREFIX + name;
            java.util.List<FavoritesPlaylistStore.FavoriteTrack> customTracks =
                    CustomPlaylistsStore.INSTANCE.getTracksFromPlaylist(ctx, name);
            java.util.List<String> urls = new ArrayList<>();
            for (FavoritesPlaylistStore.FavoriteTrack t : customTracks) {
                if (!TextUtils.isEmpty(t.imageUrl)) {
                    if (!urls.contains(t.imageUrl)) urls.add(t.imageUrl);
                    if (urls.size() >= 4) break;
                }
            }

            View row = LayoutInflater.from(ctx).inflate(R.layout.item_save_playlist_row, llList, false);
            ImageView ivThumb = row.findViewById(R.id.ivSavePlaylistThumb);
            android.widget.TextView tvName = row.findViewById(R.id.tvSavePlaylistName);
            android.widget.TextView tvCount = row.findViewById(R.id.tvSavePlaylistCount);
            ImageView ivCheck = row.findViewById(R.id.ivSaveCheck);
            tvName.setText(name);
            tvCount.setText(customTracks.size() + " pistas");
            if (urls.size() >= 4) {
                PlaylistGridArtLoader.load(ivThumb, urls, thumbSizePx);
            } else if (!urls.isEmpty()) {
                Glide.with(this).load(urls.get(0)).centerCrop().into(ivThumb);
            }
            boolean isIn = isTrackInPlaylist(ctx, track.videoId, playlistKey);
            if (ivCheck != null) ivCheck.setVisibility(isIn ? View.VISIBLE : View.GONE);
            final boolean[] checked = {isIn};
            final String pName = name;
            row.setOnClickListener(v -> {
                if (checked[0]) {
                    removeTrackFromPlaylistByKey(playlistKey, track.videoId);
                    checked[0] = false;
                    didRemove[0] = true;
                    if (ivCheck != null) ivCheck.setVisibility(View.GONE);
                    if (playlistKey.equals(lastAddedKey[0])) {
                        lastAddedKey[0] = null;
                        lastAddedName[0] = null;
                    }
                } else {
                    addTrackToPlaylistByKey(playlistKey, track);
                    checked[0] = true;
                    if (ivCheck != null) ivCheck.setVisibility(View.VISIBLE);
                    lastAddedKey[0] = playlistKey;
                    lastAddedName[0] = pName;
                }
                int count = getPlaylistTrackCount(ctx, playlistKey);
                tvCount.setText(count + " pistas");
            });
            llList.addView(row);
        }

        saveDialog.show();
    }

    private boolean isTrackInPlaylist(@NonNull Context ctx, @NonNull String videoId, @NonNull String playlistKey) {
        if (TextUtils.isEmpty(videoId)) return false;
        if (FavoritesPlaylistStore.PLAYLIST_ID.equals(playlistKey)) {
            return FavoritesPlaylistStore.isFavorite(ctx, videoId);
        } else if (playlistKey.startsWith(CustomPlaylistsStore.CUSTOM_PLAYLIST_PREFIX)) {
            String name = playlistKey.substring(CustomPlaylistsStore.CUSTOM_PLAYLIST_PREFIX.length());
            java.util.List<FavoritesPlaylistStore.FavoriteTrack> tracks =
                    CustomPlaylistsStore.INSTANCE.getTracksFromPlaylist(ctx, name);
            for (FavoritesPlaylistStore.FavoriteTrack t : tracks) {
                if (videoId.equals(t.videoId)) return true;
            }
        }
        return false;
    }

    private int getPlaylistTrackCount(@NonNull Context ctx, @NonNull String playlistKey) {
        if (FavoritesPlaylistStore.PLAYLIST_ID.equals(playlistKey)) {
            return FavoritesPlaylistStore.loadFavorites(ctx).size();
        } else if (playlistKey.startsWith(CustomPlaylistsStore.CUSTOM_PLAYLIST_PREFIX)) {
            String name = playlistKey.substring(CustomPlaylistsStore.CUSTOM_PLAYLIST_PREFIX.length());
            return CustomPlaylistsStore.INSTANCE.getTracksFromPlaylist(ctx, name).size();
        }
        return 0;
    }

    private void addTrackToPlaylistByKey(@NonNull String playlistKey, @NonNull PlayerTrack track) {
        if (!isAdded()) return;
        String title = TextUtils.isEmpty(track.title) ? "Tema" : track.title;
        String artist = track.artist == null ? "" : track.artist;
        String duration = track.duration == null ? "" : track.duration;
        String imageUrl = track.imageUrl == null ? "" : track.imageUrl;

        if (FavoritesPlaylistStore.PLAYLIST_ID.equals(playlistKey)) {
            FavoritesPlaylistStore.upsertFavorite(requireContext(), track.videoId, title, artist, duration, imageUrl);
        } else if (playlistKey.startsWith(CustomPlaylistsStore.CUSTOM_PLAYLIST_PREFIX)) {
            String name = playlistKey.substring(CustomPlaylistsStore.CUSTOM_PLAYLIST_PREFIX.length());
            CustomPlaylistsStore.INSTANCE.addTrackToPlaylist(requireContext(), name,
                    track.videoId, title, artist, duration, imageUrl);
        }
    }

    private void removeTrackFromPlaylistByKey(@NonNull String playlistKey, @NonNull String videoId) {
        if (!isAdded() || TextUtils.isEmpty(videoId)) return;
        if (FavoritesPlaylistStore.PLAYLIST_ID.equals(playlistKey)) {
            FavoritesPlaylistStore.removeFavorite(requireContext(), videoId);
        } else if (playlistKey.startsWith(CustomPlaylistsStore.CUSTOM_PLAYLIST_PREFIX)) {
            String name = playlistKey.substring(CustomPlaylistsStore.CUSTOM_PLAYLIST_PREFIX.length());
            CustomPlaylistsStore.INSTANCE.removeTrackFromPlaylist(requireContext(), name, videoId);
        }
    }

    private void showSavedInPlaylistBarPlayer(@NonNull PlayerTrack track, @NonNull String playlistKey, @NonNull String playlistName) {
        if (!isAdded() || getView() == null) return;

        android.view.ViewGroup rootView = (android.view.ViewGroup) getView();

        // Remove any existing saved bar
        View existing = rootView.findViewWithTag("saved_bar");
        if (existing != null) rootView.removeView(existing);

        float density = getResources().getDisplayMetrics().density;

        android.widget.LinearLayout bar = new android.widget.LinearLayout(requireContext());
        bar.setTag("saved_bar");
        bar.setId(View.generateViewId());
        bar.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        bar.setGravity(android.view.Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(android.graphics.Color.parseColor("#FF1E1E1E"));
        int hPad = (int) (16 * density);
        int vPad = (int) (20 * density);
        bar.setPadding(hPad, vPad, hPad, vPad);
        bar.setElevation(8 * density);

        TextView tvSaved = new TextView(requireContext());
        tvSaved.setText("Se guardó en " + playlistName);
        tvSaved.setTextColor(android.graphics.Color.WHITE);
        tvSaved.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 14);
        tvSaved.setTypeface(null, android.graphics.Typeface.BOLD);
        android.widget.LinearLayout.LayoutParams tvParams = new android.widget.LinearLayout.LayoutParams(
                0, android.view.ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tvSaved.setLayoutParams(tvParams);
        bar.addView(tvSaved);

        TextView btnChange = new TextView(requireContext());
        btnChange.setText("Cambiar");
        btnChange.setTextColor(android.graphics.Color.parseColor("#8AB4F8"));
        btnChange.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 14);
        btnChange.setTypeface(null, android.graphics.Typeface.BOLD);
        btnChange.setPadding((int) (12 * density), 0, 0, 0);
        btnChange.setOnClickListener(v -> {
            rootView.removeView(bar);
            showSaveToPlaylistSheet(track, playlistKey);
        });
        bar.addView(btnChange);

        int barBottomMargin = (int) (12 * density);
        if (rootView instanceof androidx.constraintlayout.widget.ConstraintLayout) {
            androidx.constraintlayout.widget.ConstraintLayout.LayoutParams clp =
                    new androidx.constraintlayout.widget.ConstraintLayout.LayoutParams(
                            androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.MATCH_PARENT,
                            androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.WRAP_CONTENT);
            clp.bottomToBottom = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID;
            clp.bottomMargin = barBottomMargin;
            clp.startToStart = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID;
            clp.endToEnd = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID;
            rootView.addView(bar, clp);
        } else {
            FrameLayout.LayoutParams flp = new FrameLayout.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
            flp.gravity = android.view.Gravity.BOTTOM;
            flp.bottomMargin = barBottomMargin;
            rootView.addView(bar, flp);
        }

        // Auto-dismiss after 4 seconds
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (bar.getParent() != null) {
                ((android.view.ViewGroup) bar.getParent()).removeView(bar);
            }
        }, 4000L);
    }

    private void showRemovedFromPlaylistBarPlayer() {
        if (!isAdded() || getView() == null) return;

        android.view.ViewGroup rootView = (android.view.ViewGroup) getView();
        View existing = rootView.findViewWithTag("saved_bar");
        if (existing != null) rootView.removeView(existing);

        float density = getResources().getDisplayMetrics().density;

        android.widget.LinearLayout bar = new android.widget.LinearLayout(requireContext());
        bar.setTag("saved_bar");
        bar.setId(View.generateViewId());
        bar.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        bar.setGravity(android.view.Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(android.graphics.Color.parseColor("#FF1E1E1E"));
        int hPad = (int) (16 * density);
        int vPad = (int) (20 * density);
        bar.setPadding(hPad, vPad, hPad, vPad);
        bar.setElevation(8 * density);

        TextView tvMsg = new TextView(requireContext());
        tvMsg.setText("Se eliminó correctamente");
        tvMsg.setTextColor(android.graphics.Color.WHITE);
        tvMsg.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 14);
        tvMsg.setTypeface(null, android.graphics.Typeface.BOLD);
        android.widget.LinearLayout.LayoutParams tvParams = new android.widget.LinearLayout.LayoutParams(
                0, android.view.ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tvMsg.setLayoutParams(tvParams);
        bar.addView(tvMsg);

        TextView btnChange = new TextView(requireContext());
        btnChange.setText("Cambiar");
        btnChange.setTextColor(android.graphics.Color.parseColor("#8AB4F8"));
        btnChange.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 14);
        btnChange.setTypeface(null, android.graphics.Typeface.BOLD);
        btnChange.setPadding((int) (12 * density), 0, 0, 0);
        btnChange.setOnClickListener(v -> {
            rootView.removeView(bar);
            showSaveToPlaylistSheetFromPlayer();
        });
        bar.addView(btnChange);

        int barBM = (int) (12 * density);
        if (rootView instanceof androidx.constraintlayout.widget.ConstraintLayout) {
            androidx.constraintlayout.widget.ConstraintLayout.LayoutParams clp =
                    new androidx.constraintlayout.widget.ConstraintLayout.LayoutParams(
                            androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.MATCH_PARENT,
                            androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.WRAP_CONTENT);
            clp.bottomToBottom = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID;
            clp.bottomMargin = barBM;
            clp.startToStart = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID;
            clp.endToEnd = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID;
            rootView.addView(bar, clp);
        } else {
            FrameLayout.LayoutParams flp = new FrameLayout.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
            flp.gravity = android.view.Gravity.BOTTOM;
            flp.bottomMargin = barBM;
            rootView.addView(bar, flp);
        }

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (bar.getParent() != null) {
                ((android.view.ViewGroup) bar.getParent()).removeView(bar);
            }
        }, 4000L);
    }

    private void showQueueBottomSheet() {
        if (!isAdded()) return;

        BottomSheetDialog bottomSheetDialog = new BottomSheetDialog(requireContext());
        View bsv = getLayoutInflater().inflate(R.layout.bottom_sheet_player_queue, null);
        bottomSheetDialog.setContentView(bsv);

        RecyclerView rvQueue = bsv.findViewById(R.id.rvQueue);
        TextView tvEmptyQueue = bsv.findViewById(R.id.tvEmptyQueue);
        ProgressBar pbQueueLoading = bsv.findViewById(R.id.pbQueueLoading);
        LinearLayoutManager layoutManager = new LinearLayoutManager(requireContext());
        rvQueue.setLayoutManager(layoutManager);
        rvQueue.setHasFixedSize(false);

        pbQueueLoading.setVisibility(View.VISIBLE);
        rvQueue.setVisibility(View.GONE);
        tvEmptyQueue.setVisibility(View.GONE);

        nextUpAdapter = new NextUpAdapter(position -> {
            if (position == 0) {
                return;
            }
            if (position >= 0 && position < nextUpTracks.size()) {
                PlayerTrack tapped = nextUpTracks.get(position);
                int realIdx = -1;
                for (int j = 0; j < tracks.size(); j++) {
                    if (tracks.get(j) == tapped) {
                        realIdx = j;
                        break;
                    }
                }
                
                if (realIdx != -1) {
                    currentIndex = realIdx;
                    isPlaying = true;
                    currentSeconds = 0;
                    bindCurrentTrack(false);
                    playCurrentTrack();
                    bottomSheetDialog.dismiss();
                }
            }
        }, holder -> {
            if (nextUpItemTouchHelper != null) {
                nextUpItemTouchHelper.startDrag(holder);
            }
        });
        
        rvQueue.setAdapter(nextUpAdapter);

        bottomSheetDialog.setOnShowListener(dialog -> {
            BottomSheetDialog d = (BottomSheetDialog) dialog;
            View bottomSheet = d.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheet != null) {
                bottomSheet.setBackgroundResource(android.R.color.transparent);
                BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(bottomSheet);
                behavior.setSkipCollapsed(true);
                bottomSheet.setAlpha(0f);
                bottomSheet.setVisibility(View.INVISIBLE);
                bottomSheet.post(() -> {
                    behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
                    bottomSheet.post(() -> {
                        bottomSheet.setVisibility(View.VISIBLE);
                        bottomSheet.animate().alpha(1f).setDuration(120L).start();
                    });
                });
            }
        });

        ItemTouchHelper.SimpleCallback dragCallback = new ItemTouchHelper.SimpleCallback(
                ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0
        ) {
            @Override
            public int getMovementFlags(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
                if (viewHolder.getBindingAdapterPosition() == 0) return 0; // Now Playing: no drag
                return super.getMovementFlags(recyclerView, viewHolder);
            }

            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
                int from = viewHolder.getBindingAdapterPosition();
                int to = target.getBindingAdapterPosition();
                if (from == RecyclerView.NO_POSITION || to == RecyclerView.NO_POSITION) return false;
                return nextUpAdapter.moveItem(from, to);
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {}

            @Override
            public void clearView(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
                super.clearView(recyclerView, viewHolder);
                // Sync nextUpTracks from the drag-mutated list before applying to queue
                nextUpTracks.clear();
                nextUpTracks.addAll(nextUpAdapter.getItems());
                applyNextUpOrderToQueue();
            }
        };

        nextUpItemTouchHelper = new ItemTouchHelper(dragCallback);
        nextUpItemTouchHelper.attachToRecyclerView(rvQueue);
        
        bottomSheetDialog.show();

        streamResolverExecutor.execute(() -> {
            List<PlayerTrack> computedQueue = new ArrayList<>();
            if (!tracks.isEmpty() && currentIndex >= 0 && currentIndex < tracks.size()) {
                computedQueue.add(tracks.get(currentIndex));
            }
            for (int i = 1; i < tracks.size(); i++) {
                int idx = (currentIndex + i) % tracks.size();
                computedQueue.add(tracks.get(idx));
            }

            localProgressHandler.post(() -> {
                if (!isAdded() || !bottomSheetDialog.isShowing()) {
                    return;
                }

                nextUpTracks.clear();
                // Limit queue size to avoid unbounded memory growth
                if (computedQueue.size() > MAX_NEXT_UP) {
                    nextUpTracks.addAll(computedQueue.subList(0, MAX_NEXT_UP));
                } else {
                    nextUpTracks.addAll(computedQueue);
                }
                nextUpAdapter.setItems(nextUpTracks);

                pbQueueLoading.setVisibility(View.GONE);
                if (nextUpTracks.isEmpty()) {
                    rvQueue.setVisibility(View.GONE);
                    tvEmptyQueue.setVisibility(View.VISIBLE);
                } else {
                    rvQueue.setVisibility(View.VISIBLE);
                    tvEmptyQueue.setVisibility(View.GONE);
                }
            });
        });
    }
    private void setupSwipeToDismiss(View root) {
        if (!(root instanceof SwipeInterceptLayout)) return;
        NestedScrollView scrollView = root.findViewById(R.id.svSongPlayerContent);
        if (scrollView == null) return;

        swipeDismissGestureActive = false;
        swipeDismissAnimationRunning = false;
        swipeDismissTouchSlopPx = ViewConfiguration.get(requireContext()).getScaledTouchSlop();
        swipeDismissMinDistancePx = Math.max(dpToPx(96),
                Math.round(root.getResources().getDisplayMetrics().heightPixels * 0.12f));

        final float[] initialTouchY = {0f};
        final float[] dragStartY = {0f};
        final float[] lastTouchY = {0f};
        final long[] initialTouchTimeMs = {0L};
        final boolean[] isDragging = {false};
        final boolean[] canStart = {false};

        ((SwipeInterceptLayout) root).setSwipeInterceptCallback(new SwipeInterceptLayout.SwipeInterceptCallback() {
            @Override
            public boolean onInterceptSwipe(MotionEvent e) {
                return handleSwipeDismissTouch(root, scrollView, e,
                        initialTouchY, dragStartY, lastTouchY, initialTouchTimeMs, isDragging, canStart);
            }
            @Override
            public boolean onSwipeTouch(MotionEvent e) {
                if (swipeDismissAnimationRunning) return isDragging[0];
                return handleSwipeDismissTouch(root, scrollView, e,
                        initialTouchY, dragStartY, lastTouchY, initialTouchTimeMs, isDragging, canStart);
            }
        });
    }

    private boolean handleSwipeDismissTouch(
            View root, NestedScrollView scrollView, MotionEvent event,
            float[] initialTouchY, float[] dragStartY, float[] lastTouchY,
            long[] initialTouchTimeMs, boolean[] isDragging, boolean[] canStart) {

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                playerEnterAnimationRunning = false;
                root.animate().cancel();
                initialTouchY[0] = event.getRawY();
                dragStartY[0] = initialTouchY[0];
                lastTouchY[0] = initialTouchY[0];
                initialTouchTimeMs[0] = event.getEventTime();
                isDragging[0] = false;
                swipeDismissGestureActive = false;
                canStart[0] = scrollView.getScrollY() <= swipeDismissTouchSlopPx;
                return false;

            case MotionEvent.ACTION_MOVE:
                float currentY = event.getRawY();
                float totalDeltaY = currentY - initialTouchY[0];
                lastTouchY[0] = currentY;

                if (!canStart[0] && scrollView.getScrollY() <= swipeDismissTouchSlopPx && totalDeltaY > 0f) {
                    canStart[0] = true;
                    dragStartY[0] = currentY;
                    totalDeltaY = 0f;
                }

                if (!isDragging[0] && canStart[0] && totalDeltaY > swipeDismissTouchSlopPx) {
                    isDragging[0] = true;
                    swipeDismissGestureActive = true;
                    dragStartY[0] = currentY - swipeDismissTouchSlopPx;
                    scrollView.requestDisallowInterceptTouchEvent(true);
                }

                if (isDragging[0]) {
                    float dragDeltaY = currentY - dragStartY[0];
                    root.setTranslationY(Math.max(0f, dragDeltaY * 0.92f));
                    return true;
                }
                return false;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (isDragging[0]) {
                    float finalDragDeltaY = Math.max(0f, lastTouchY[0] - dragStartY[0]);
                    long elapsedMs = Math.max(1L, event.getEventTime() - initialTouchTimeMs[0]);
                    float velocityY = finalDragDeltaY / elapsedMs * 1000f;
                    boolean shouldDismiss = event.getActionMasked() == MotionEvent.ACTION_UP
                            && (finalDragDeltaY >= swipeDismissMinDistancePx
                                || finalDragDeltaY > root.getHeight() * 0.18f
                                || (finalDragDeltaY > dpToPx(48) && velocityY > dpToPx(900)));

                    isDragging[0] = false;
                    swipeDismissGestureActive = false;
                    scrollView.requestDisallowInterceptTouchEvent(false);

                    if (shouldDismiss) {
                        swipeDismissAnimationRunning = true;
                        root.animate().cancel();
                        root.animate()
                                .translationY(root.getHeight())
                                .setDuration(250)
                                .withEndAction(() -> {
                                    swipeDismissAnimationRunning = false;
                                    if (isAdded() && getActivity() instanceof MainActivity) {
                                        ((MainActivity) getActivity()).externalClosePlayerImmediately();
                                    }
                                })
                                .start();
                    } else {
                        root.animate().cancel();
                        root.animate()
                                .translationY(0)
                                .setDuration(200)
                                .withEndAction(() -> {
                                    swipeDismissGestureActive = false;
                                    swipeDismissAnimationRunning = false;
                                })
                                .start();
                    }
                    return true;
                }
                return false;
        }
        return false;
    }


    private void releaseLocalExoMediaPlayer() {
        cancelOfflineCrossfade();
        if (localExoMediaPlayer == null) {
            return;
        }
        // Release video surface from router
        videoRouter.onPlayerReleased();
        try {
            localExoMediaPlayer.release();
        } catch (Exception ignored) {
        }
        localExoMediaPlayer = null;
        usingOfflineSource = false;
    }

    @Nullable
    private Context getPlaybackAppContext() {
        Context currentContext = getContext();
        if (currentContext != null) {
            persistentAppContext = currentContext.getApplicationContext();
        }
        return persistentAppContext;
    }

    private void clearPlayerCoverRequest() {
        if (playerCoverTarget == null) {
            return;
        }
        if (isAdded()) {
            Glide.with(this).clear(playerCoverTarget);
        }
        playerCoverTarget = null;
    }

    private void showPlayerArtworkLoadingState() {
        if (ivPlayerCover != null) {
            ivPlayerCover.setImageResource(android.R.color.black);
        }
        if (ivPlayerBackdrop != null) {
            ivPlayerBackdrop.animate().cancel();
            ivPlayerBackdrop.setBackgroundColor(Color.BLACK);
        }
    }

    private void completePlayerArtworkBootstrap() {
        if (!playerArtworkBootstrapPending) {
            return;
        }
        playerArtworkBootstrapPending = false;
        refreshNextUp();
    }

    private void cancelDeferredBackdropLoad() {
        if (deferredBackdropLoadRunnable != null) {
            localProgressHandler.removeCallbacks(deferredBackdropLoadRunnable);
            deferredBackdropLoadRunnable = null;
        }
    }

    private void scheduleBackdropLoad(
            @NonNull PlayerTrack requestedTrack,
            boolean bootstrapArtwork
    ) {
        cancelDeferredBackdropLoad();
        // Keep pure black backdrop when video is playing
        if (currentSourceIsVideo) {
            completePlayerArtworkBootstrap();
            return;
        }

        String requestVideoId = requestedTrack.videoId == null ? "" : requestedTrack.videoId;
        deferredBackdropLoadRunnable = () -> {
            deferredBackdropLoadRunnable = null;
            if (!isAdded() || tracks.isEmpty() || currentIndex < 0 || currentIndex >= tracks.size()) {
                return;
            }

            PlayerTrack current = tracks.get(currentIndex);
            String currentVideoId = current.videoId == null ? "" : current.videoId;
            if (!TextUtils.equals(currentVideoId, requestVideoId)) {
                return;
            }

            loadPlayerBackdrop(current, bootstrapArtwork);
        };

        deferredBackdropLoadRunnable.run();
    }

    /**
     * Tones down a raw dominant color so it is not too vivid:
     * - Reduces saturation to ~45% max
     * - Reduces lightness to ~28% max (dark but not black)
     */
    private int muteBackdropColor(int rawColor) {
        float[] hsl = new float[3];
        androidx.core.graphics.ColorUtils.colorToHSL(rawColor, hsl);
        // Keep hue intact. Boost saturation slightly for dull colors, cap vivid ones.
        hsl[1] = Math.max(hsl[1], 0.35f);   // boost minimum saturation so grey covers look tinted
        hsl[1] = Math.min(hsl[1], 0.80f);   // cap so neon colors don’t burn
        // Target lightness: ~32–40% — dark enough to read text, light enough to feel like YouTube Music
        hsl[2] = Math.max(hsl[2], 0.18f);   // floor: never completely black
        hsl[2] = Math.min(hsl[2], 0.40f);   // ceiling: never too bright
        return androidx.core.graphics.ColorUtils.HSLToColor(hsl);
    }

    /**
     * Builds a top→bottom gradient from {@code topColor} to a darker variant of it.
     * The bottom colour is derived by dropping lightness ~40 % further, but never
     * below 6 % (very dark, not pure black), so there is always a visible gradient.
     */
    private android.graphics.drawable.GradientDrawable buildBackdropGradient(int topColor) {
        float[] hsl = new float[3];
        androidx.core.graphics.ColorUtils.colorToHSL(topColor, hsl);
        // Mid stop: same hue/sat, lightness cut to ~45% of top
        float midL = Math.max(0.07f, hsl[2] * 0.45f);
        hsl[2] = midL;
        int midColor = androidx.core.graphics.ColorUtils.HSLToColor(hsl);
        // Bottom stop: near-black but tinted (keeps gradient from looking totally dead)
        float bottomL = Math.max(0.04f, hsl[2] * 0.35f);
        hsl[2] = bottomL;
        int bottomColor = androidx.core.graphics.ColorUtils.HSLToColor(hsl);

        android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{topColor, midColor, bottomColor}
        );
        gd.setGradientType(android.graphics.drawable.GradientDrawable.LINEAR_GRADIENT);
        return gd;
    }

    private void revealBackdropWithFadeToColor(int rawDominantColor) {
        if (playerBackgroundContainer == null) return;

        final int mutedColor = muteBackdropColor(rawDominantColor);
        final android.graphics.drawable.GradientDrawable targetGradient = buildBackdropGradient(mutedColor);

        // Snapshot the current background for the cross-fade start point
        android.graphics.drawable.Drawable currentBg = playerBackgroundContainer.getBackground();
        final int startColor;
        if (currentBg instanceof android.graphics.drawable.GradientDrawable) {
            // Extract the top color stored in the tag, fallback to near-black
            Object tag = playerBackgroundContainer.getTag(R.id.tag_backdrop_top_color);
            startColor = (tag instanceof Integer) ? (int) tag : 0xFF0A0A0A;
        } else if (currentBg instanceof android.graphics.drawable.ColorDrawable) {
            startColor = ((android.graphics.drawable.ColorDrawable) currentBg).getColor();
        } else {
            startColor = 0xFF0A0A0A;
        }

        android.animation.ValueAnimator anim = android.animation.ValueAnimator
                .ofObject(new android.animation.ArgbEvaluator(), startColor, mutedColor);
        anim.setDuration(600L);
        anim.setInterpolator(new android.view.animation.DecelerateInterpolator());
        anim.addUpdateListener(va -> {
            if (playerBackgroundContainer == null) return;
            int interpolated = (int) va.getAnimatedValue();
            android.graphics.drawable.GradientDrawable frame = buildBackdropGradient(interpolated);
            playerBackgroundContainer.setBackground(frame);
            playerBackgroundContainer.setTag(R.id.tag_backdrop_top_color, interpolated);
        });
        anim.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                if (playerBackgroundContainer == null) return;
                playerBackgroundContainer.setBackground(targetGradient);
                playerBackgroundContainer.setTag(R.id.tag_backdrop_top_color, mutedColor);
            }
        });
        anim.start();
    }

    /** Load up to BACKDROP_COLOR_PREFS_MAX persisted dominant colors into the in-memory LRU. */
    private void loadBackdropColorCache() {
        if (persistentAppContext == null) return;
        SharedPreferences prefs = persistentAppContext.getSharedPreferences(PREFS_BACKDROP_COLORS, Activity.MODE_PRIVATE);
        Map<String, ?> all = prefs.getAll();
        for (Map.Entry<String, ?> entry : all.entrySet()) {
            if (entry.getValue() instanceof Integer && !dominantColorCache.containsKey(entry.getKey())) {
                dominantColorCache.put(entry.getKey(), (Integer) entry.getValue());
            }
        }
    }

    /** Persist a newly extracted dominant color so future sessions skip Palette extraction. */
    private void saveBackdropColor(@NonNull String videoId, int rawColor) {
        if (persistentAppContext == null || videoId.isEmpty()) return;
        SharedPreferences prefs = persistentAppContext.getSharedPreferences(PREFS_BACKDROP_COLORS, Activity.MODE_PRIVATE);
        // Evict oldest entries if we are over the limit
        Map<String, ?> all = prefs.getAll();
        if (all.size() >= BACKDROP_COLOR_PREFS_MAX) {
            SharedPreferences.Editor editor = prefs.edit();
            int toRemove = all.size() - BACKDROP_COLOR_PREFS_MAX + 1;
            for (String key : all.keySet()) {
                editor.remove(key);
                if (--toRemove <= 0) break;
            }
            editor.apply();
        }
        prefs.edit().putInt(videoId, rawColor).apply();
    }

    private void clearMediaNotificationArtwork() {
        mediaSessionArtwork = null;
        mediaSessionArtworkVideoId = "";
    }

    private void cacheMediaNotificationArtwork(@NonNull String videoId, @NonNull Bitmap source) {
        if (source.isRecycled()) {
            Log.w(TAG, "cacheMediaNotificationArtwork: bitmap already recycled videoId=" + videoId);
            return;
        }
        mediaSessionArtwork = scaleArtworkBitmap(source, MEDIA_SESSION_ARTWORK_MAX_SIZE_PX);
        mediaSessionArtworkVideoId = videoId;
    }

    @NonNull
    private Bitmap scaleArtworkBitmap(@NonNull Bitmap source, int maxEdgePx) {
        int width = Math.max(1, source.getWidth());
        int height = Math.max(1, source.getHeight());
        int largestEdge = Math.max(width, height);
        if (largestEdge <= Math.max(1, maxEdgePx)) {
            return source;
        }

        float scale = maxEdgePx / (float) largestEdge;
        int targetWidth = Math.max(1, Math.round(width * scale));
        int targetHeight = Math.max(1, Math.round(height * scale));
        try {
            return Bitmap.createScaledBitmap(source, targetWidth, targetHeight, true);
        } catch (Throwable ignored) {
            return source;
        }
    }

    private void resetPlayerHeroContainerHeight() {
        if (flPlayerHero == null) return;
        int defaultMargin = dpToPx(20);
        int defaultHeight = dpToPx(PLAYER_HERO_DEFAULT_HEIGHT_DP);
        androidx.constraintlayout.widget.ConstraintLayout.LayoutParams p =
                (androidx.constraintlayout.widget.ConstraintLayout.LayoutParams) flPlayerHero.getLayoutParams();
        if (p != null) {
            p.leftMargin = defaultMargin;
            p.rightMargin = defaultMargin;
            p.height = defaultHeight;
            flPlayerHero.setLayoutParams(p);
            if (getContext() != null) {
                flPlayerHero.setBackground(androidx.core.content.ContextCompat.getDrawable(getContext(), R.drawable.bg_player_cover_rounded));
            }
        }
    }

    private void setPlayerHeroContainerHeight(int heightPx) {
        if (flPlayerHero == null || heightPx <= 0) {
            return;
        }
        ViewGroup.LayoutParams params = flPlayerHero.getLayoutParams();
        if (params == null || params.height == heightPx) {
            return;
        }
        params.height = heightPx;
        flPlayerHero.setLayoutParams(params);
    }

    private void adjustPlayerHeroForCover(@NonNull Bitmap bitmap) {
        if (flPlayerHero == null || getContext() == null) return;

        int bmpWidth = Math.max(1, bitmap.getWidth());
        int bmpHeight = Math.max(1, bitmap.getHeight());
        boolean isWide = bmpWidth > bmpHeight * 1.2f;

        // clPlayerContent has NO horizontal padding now; hero default margin=20dp.
        // Wide images: margin=0 → full screen width. Square/tall: margin=20dp.
        int heroMarginPx = isWide ? 0 : dpToPx(20);

        // Always keep the same height so controls below don't jump.
        // Wide images use fitCenter scaleType so they show fully inside the fixed container.
        int targetHeight = dpToPx(PLAYER_HERO_DEFAULT_HEIGHT_DP);

        androidx.constraintlayout.widget.ConstraintLayout.LayoutParams heroParams =
                (androidx.constraintlayout.widget.ConstraintLayout.LayoutParams) flPlayerHero.getLayoutParams();
        if (heroParams != null) {
            heroParams.leftMargin = heroMarginPx;
            heroParams.rightMargin = heroMarginPx;
            heroParams.height = targetHeight;
            flPlayerHero.setLayoutParams(heroParams);
            if (flPlayerHero.getParent() instanceof android.view.View) {
                ((android.view.View) flPlayerHero.getParent()).requestLayout();
            }
        }
        // Rounded corners only for square/tall images; flat edges for full-width wide images
        if (isWide) {
            flPlayerHero.setBackground(androidx.core.content.ContextCompat.getDrawable(getContext(), R.drawable.bg_player_cover_flat));
        } else {
            flPlayerHero.setBackground(androidx.core.content.ContextCompat.getDrawable(getContext(), R.drawable.bg_player_cover_rounded));
        }

    }

    private int dpToPx(int dp) {
        return Math.round(dp * requireContext().getResources().getDisplayMetrics().density);
    }


    private void fadeOutBackdropToBlack() {
        if (playerBackgroundContainer == null) return;
        playerBackgroundContainer.animate().cancel();
        // Use a very dark gradient instead of solid black so the transition looks smooth
        android.graphics.drawable.GradientDrawable darkGrad = new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xFF111111, 0xFF050505}
        );
        playerBackgroundContainer.setBackground(darkGrad);
        playerBackgroundContainer.setTag(R.id.tag_backdrop_top_color, 0xFF0D0D0D);
    }


    /**
     * Called when the static check determines the video is just a static image.
     * Hides the PlayerView, detaches the video surface, shows the cover art,
     * and resets the hero layout to its normal cover art mode.
     */
    private void revertVideoToStaticCover() {
        currentSourceIsVideo = false;
        currentVideoFilePath = null;
        if (pbVideoLoading != null) pbVideoLoading.setVisibility(View.GONE);
        if (ivPlayerCover != null) {
            ivPlayerCover.animate().cancel();
            ivPlayerCover.setAlpha(1f);
            ivPlayerCover.setVisibility(View.VISIBLE);
            ivPlayerCover.setClickable(true);
        }
        // Reset hero to default cover art layout
        resetPlayerHeroContainerHeight();
        // Reload cover art and backdrop for the current track (instant, no fade)
        if (!tracks.isEmpty() && currentIndex >= 0 && currentIndex < tracks.size()) {
            PlayerTrack track = tracks.get(currentIndex);
            loadPlayerCover(track, false, 1);
            scheduleBackdropLoad(track, false);
        }
    }

}
    
