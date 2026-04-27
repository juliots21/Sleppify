package com.example.sleppify;

import com.example.sleppify.BuildConfig;
import android.Manifest;
import android.app.Activity;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.res.ColorStateList;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.media.AudioAttributes;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.widget.NestedScrollView;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
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

public class SongPlayerFragment extends Fragment {

    private static final String TAG = "SongPlayerFragment";

    public static final String ARG_VIDEO_IDS = "arg_video_ids";
    public static final String ARG_TITLES = "arg_titles";
    public static final String ARG_ARTISTS = "arg_artists";
    public static final String ARG_DURATIONS = "arg_durations";
    public static final String ARG_IMAGES = "arg_images";
    public static final String ARG_SELECTED_INDEX = "arg_selected_index";
    public static final String ARG_START_PLAYING = "arg_start_playing";

    private static final String PREFS_PLAYER_STATE = "player_state";
    private static final String PREF_PLAYBACK_POS_PREFIX = "yt_pos_";
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
    private static final int ENDED_FALLBACK_THRESHOLD_SECONDS = 2;
    private static final long AUTOPLAY_RECOVERY_DELAY_MS = 1400L;
    private static final long TRACK_ERROR_RETRY_DELAY_MS = 750L;
    private static final int MAX_TRACK_ERROR_RETRY = 1;
    private static final int MAX_PLAYER_ENGINE_RECOVERY_RETRY = 4;
    private static final int COVER_TAPS_TO_UNLOCK_VIDEO_CONTROLS = 5;
    private static final long COVER_TAP_RESET_WINDOW_MS = 2000L;
    private static final int CONNECT_TIMEOUT_MS = 14000;
    private static final int READ_TIMEOUT_MS = 22000;
    private static final long SOURCE_PREPARE_TIMEOUT_MS = 30000L;
    private static final long SOCIAL_STATS_FETCH_DEFER_MS = 1800L;
    private static final long PLAYBACK_BOOTSTRAP_GRACE_MS = 1800L;
    private static final int MAX_PLAYBACK_SOURCE_RETRY = 1;
    private static final long PLAYBACK_SOURCE_RETRY_DELAY_MS = 350L;
    // Match the primary InnerTube client (ANDROID_MUSIC) so the streaming server sees a
    // consistent identity between the URL resolution and the playback request.
    private static final String STREAM_HTTP_USER_AGENT = "com.google.android.apps.youtube.music/7.02.52 (Linux; U; Android 14; es_US; Pixel 7 Pro; Build/UQ1A.240205.002; Cronet/121.0.6167.71)";
    private static final String AUDIUS_API_BASE_URL = "https://discoveryprovider.audius.co/v1";
    private static final String AUDIUS_APP_NAME = "sleppify";
    private static final int AUDIUS_SEARCH_LIMIT = 6;
    private static final int OFFLINE_CROSSFADE_MAX_SECONDS = 12;
    private static final int OFFLINE_CROSSFADE_DEFAULT_SECONDS = 0;
    private static final int OFFLINE_CROSSFADE_FIRST_STEP_MS = 500;
    private static final int OFFLINE_CROSSFADE_STEP_MS = 40;
    private static final int PLAYER_HERO_DEFAULT_HEIGHT_DP = 370;
    private static final int PLAYER_HERO_MIN_HEIGHT_DP = 260;
    private static final int PLAYER_HERO_MAX_HEIGHT_DP = 560;
    private static final long PLAYER_BACKDROP_FADE_IN_DURATION_MS = 500L;
    private static final long PLAYER_BACKDROP_DEFER_LOAD_MS = 110L;
    private static final int NEXT_UP_FIRST_BATCH_SIZE = 14;
    private static final int NEXT_UP_BATCH_SIZE = 24;
    private static final long NEXT_UP_BATCH_DELAY_MS = 32L;

    private final List<PlayerTrack> tracks = new ArrayList<>();
    private final List<PlayerTrack> nextUpTracks = new ArrayList<>();
    private final List<PlayerTrack> originalQueueOrder = new ArrayList<>();

    private ShapeableImageView ivPlayerCover;
    private ShapeableImageView ivPlayerBackdrop;
    private AnimatedEqualizerView animatedEqPlayer;
    private FrameLayout flPlayerHero;
    private TextView tvPlayerTitle;
    private TextView tvPlayerArtist;
    private TextView tvActionLikeCount;
    private TextView tvActionDislikeCount;
    private TextView tvActionCommentCount;
    private TextView tvActionFavoriteLabel;
    private TextView tvCurrentTime;
    private TextView tvTotalTime;
    private TextView tvDeviceName;
    private ImageView ivDeviceIcon;
    private View llQueueTrigger;
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

    private NextUpAdapter nextUpAdapter;
    @Nullable
    private ItemTouchHelper nextUpItemTouchHelper;
    private RecyclerView rvQueueSheet; // For BottomSheet

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

    private int currentIndex = 0;
    private boolean isPlaying = true;

    public boolean isPlaying() {
        return isPlaying;
    }
    private int currentSeconds = 0;
    private int totalSeconds = 1;
    private int lastPersistedSecond = -1;
    private boolean isRestoringPosition = false;
    private int lastSeekTargetSeconds = -1;
    private boolean pauseRequestedByUser = false;
    private boolean appInBackground = false;
    private long lastBackgroundResumeAttemptMs = 0L;
    @Nullable
    private ActivityResultLauncher<String> notificationPermissionLauncher;
    private boolean notificationPermissionRequested;
    private boolean shuffleEnabled = false;
    private int repeatMode = REPEAT_MODE_ALL;
    private boolean collapsingToMiniMode;
    private long lastHandledEndedAtMs = 0L;
    @NonNull
    private String lastHandledEndedVideoId = "";
    /**
     * Tracks the videoId for which we already consumed a one-shot InnerTube re-resolution
     * retry after an ExoPlayer failure. If another error occurs for the same videoId,
     * we skip to the next track instead of retrying indefinitely.
     */
    @Nullable
    private String lastReresolveVideoId;
    @NonNull
    private final Random random = new Random();
    @NonNull
    private String loadedVideoId = "";

    @NonNull
    public String getLoadedVideoId() {
        return loadedVideoId;
    }
    @NonNull
    private String returnTargetTag = TAG_PLAYLIST_DETAIL;
    private boolean usingOfflineSource = false;
    private final Handler localProgressHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService streamResolverExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService socialStatsExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService persistenceExecutor = Executors.newSingleThreadExecutor();
    private final Map<String, SocialStats> socialStatsCache = new HashMap<>();

    // Direct streaming & pre-fetching
    private String prefetchedNextVideoId = null;
    private String prefetchedNextUrl = null;

    @Nullable
    private Future<?> pendingStreamResolverFuture;
    @Nullable
    private Runnable sourcePrepareTimeoutRunnable;
    private boolean localSourcePreparing = false;
    private boolean localCrossfadeInProgress = false;
    private Context persistentAppContext;
    private int localCrossfadeTargetIndex = -1;
    private long localCrossfadeStartedAtMs = 0L;
    @Nullable
    private Runnable localCrossfadeTicker;
    private long lastPlaybackStartRequestAtMs = 0L;
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
    private int coverTapCount = 0;
    private long lastCoverTapAtMs = 0L;
    private final Set<String> audiusFallbackAttemptedVideoIds = new HashSet<>();
    @NonNull
    private String pendingSocialStatsVideoId = "";
    @Nullable
    private Runnable pendingSocialStatsFetchRunnable;
    @Nullable
    private CustomTarget<Bitmap> playerCoverTarget;
    private CustomTarget<Bitmap> notificationArtworkTarget;
    @NonNull
    private String activeNotificationArtworkVideoId = "";
    private CustomTarget<Drawable> playerBackdropTarget;
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
    private Bitmap mediaNotificationLargeIcon;
    @NonNull
    private String mediaNotificationLargeIconVideoId = "";
    @Nullable
    private Bitmap mediaSessionArtwork;
    @NonNull
    private String mediaSessionArtworkVideoId = "";
    private final Runnable localProgressTicker = new Runnable() {
        @Override
        public void run() {
            if (!isAdded() || localExoMediaPlayer == null || !usingOfflineSource || userSeeking) {
                return;
            }

            try {
                int positionMs = Math.max(0, localExoMediaPlayer.getCurrentPosition());
                int durationMs = Math.max(1, localExoMediaPlayer.getDuration());

                maybeStartOfflineCrossfade(positionMs, durationMs);

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

                tvCurrentTime.setText(formatSeconds(currentSeconds));
                tvTotalTime.setText(formatSeconds(totalSeconds));

                int progress = Math.round((Math.max(0, currentSeconds) / (float) Math.max(1, totalSeconds)) * 1000f);
                sbPlaybackProgress.setProgress(Math.max(0, Math.min(1000, progress)));

                if (currentSeconds != lastPersistedSecond && currentSeconds % 2 == 0) {
                    lastPersistedSecond = currentSeconds;
                    persistPositionForLoadedTrack();
                    updateMediaSessionState();
                    persistPlaybackSnapshot(false);
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
        return newInstance(videoIds, titles, artists, durations, images, selectedIndex, true);
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
        SongPlayerFragment fragment = new SongPlayerFragment();
        Bundle args = new Bundle();
        args.putStringArrayList(ARG_VIDEO_IDS, videoIds);
        args.putStringArrayList(ARG_TITLES, titles);
        args.putStringArrayList(ARG_ARTISTS, artists);
        args.putStringArrayList(ARG_DURATIONS, durations);
        args.putStringArrayList(ARG_IMAGES, images);
        args.putInt(ARG_SELECTED_INDEX, selectedIndex);
        args.putBoolean(ARG_START_PLAYING, startPlaying);
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
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

    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        view.setTranslationY(0f);
        view.setAlpha(1f);

        persistentAppContext = requireContext().getApplicationContext();
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).setContainerOverlayMode(true);
        }

        ivPlayerCover = view.findViewById(R.id.ivPlayerCover);
        ivPlayerBackdrop = view.findViewById(R.id.ivPlayerBackdrop);
        animatedEqPlayer = view.findViewById(R.id.animatedEqPlayer);
        flPlayerHero = view.findViewById(R.id.flPlayerHero);
        playerArtworkBootstrapPending = true;
        tvPlayerTitle = view.findViewById(R.id.tvPlayerTitle);
        tvPlayerArtist = view.findViewById(R.id.tvPlayerArtist);
        actionLike = view.findViewById(R.id.actionLike);
        actionDislike = view.findViewById(R.id.actionDislike);
        actionComments = view.findViewById(R.id.actionComments);
        actionFavorite = view.findViewById(R.id.actionFavorite);
        tvActionLikeCount = view.findViewById(R.id.tvActionLikeCount);
        tvActionDislikeCount = view.findViewById(R.id.tvActionDislikeCount);
        tvActionCommentCount = view.findViewById(R.id.tvActionCommentCount);
        tvActionFavoriteLabel = view.findViewById(R.id.tvActionFavoriteLabel);
        ivActionFavoriteIcon = view.findViewById(R.id.ivActionFavoriteIcon);
        tvCurrentTime = view.findViewById(R.id.tvCurrentTime);
        tvTotalTime = view.findViewById(R.id.tvTotalTime);
        sbPlaybackProgress = view.findViewById(R.id.sbPlaybackProgress);
        svSongPlayerContent = view.findViewById(R.id.svSongPlayerContent);
        btnShuffle = view.findViewById(R.id.btnShuffle);
        btnRepeat = view.findViewById(R.id.btnRepeat);
        vPlayerShuffleIndicator = view.findViewById(R.id.vPlayerShuffleIndicator);
        vPlayerRepeatIndicator = view.findViewById(R.id.vPlayerRepeatIndicator);
        btnPlayPause = view.findViewById(R.id.btnPlayPause);
        llQueueTrigger = view.findViewById(R.id.llQueueTrigger);
        llQueueTrigger.setOnClickListener(v -> showQueueBottomSheet());

        playerStatePrefs = requireContext().getSharedPreferences(PREFS_PLAYER_STATE, Activity.MODE_PRIVATE);
        settingsPrefs = requireContext().getSharedPreferences(CloudSyncManager.PREFS_SETTINGS, Activity.MODE_PRIVATE);
        loadPlaybackModesFromSettings();
        setupSocialActions();
        updatePlayerSurfaceForSource();

        hydrateTracksFromArgs();
        setupMediaSession();

        tvDeviceName = view.findViewById(R.id.tvDeviceName);
        ivDeviceIcon = view.findViewById(R.id.ivDeviceIcon);
        // Defer non-critical setup to post-inflation to speed up fragment entry
        view.post(() -> {
            if (!isAdded()) return;
            loadPlaybackModesFromSettings();
            setupSocialActions();
            updatePlayerSurfaceForSource();
            updateDeviceInfo();
            updatePlaybackModeButtons();
        });











        setupSwipeToDismiss(view);
        setupBackPressToMiniMode();
        resetPlayerScrollToTop();

        currentIndex = Math.max(0, Math.min(currentIndex, tracks.size() - 1));
        bindCurrentTrack(true);

        if (isAdded()) {
            playCurrentTrack();
        }

        view.findViewById(R.id.btnPrev).setOnClickListener(v -> moveTrack(-1));
        view.findViewById(R.id.btnNext).setOnClickListener(v -> moveTrack(1));
        btnShuffle.setOnClickListener(v -> toggleShuffleMode());
        btnRepeat.setOnClickListener(v -> cycleRepeatMode());
        btnPlayPause.setOnClickListener(v -> togglePlayback());
        updatePlaybackModeButtons();

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
                boolean shouldRecover = false;
                if (usingOfflineSource && localExoMediaPlayer != null) {
                    try {
                        localExoMediaPlayer.seekTo(Math.max(0, currentSeconds) * 1000);
                    } catch (Exception ignored) {
                        shouldRecover = true;
                    }
                    if (isPlaying) {
                        try {
                            localExoMediaPlayer.start();
                            startLocalProgressTicker();
                        } catch (Exception ignored) {
                            shouldRecover = true;
                        }
                    }
                } else if (isPlaying) {
                    shouldRecover = true;
                }

                if (isPlaying && shouldRecover) {
                    ensureActivePlaybackIfExpected("seekbar-stop");
                }

                int progress = Math.round((Math.max(0, currentSeconds) / (float) Math.max(1, totalSeconds)) * 1000f);
                sbPlaybackProgress.setProgress(Math.max(0, Math.min(1000, progress)));
                persistPositionForLoadedTrack();
                updateMediaSessionState();
                userSeeking = false;
            }
        });
    }

    @Override
    public void onStop() {
        persistPositionForLoadedTrack();
        persistPlaybackSnapshot(false, true);
        super.onStop();
    }

    @Override
    public void onPause() {
        appInBackground = true;
        updateBackPressedCallbackEnabled(true);
        stopLocalProgressTicker();
        persistPlaybackSnapshot(false, true);
        super.onPause();
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        updateBackPressedCallbackEnabled(hidden);
        if (hidden) {
            if (!(usingOfflineSource && localExoMediaPlayer != null && isPlaying)) {
                stopLocalProgressTicker();
            }
        } else {
            if (getView() != null) {
                getView().setTranslationY(0f);
                getView().setVisibility(View.VISIBLE);
            }
            resetPlayerScrollToTop();
            ensureActivePlaybackIfExpected("onHiddenChanged-visible");
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getView() != null && !isHidden()) {
            getView().setTranslationY(0f);
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

        if (usingOfflineSource && localExoMediaPlayer != null) {
            if (localSourcePreparing && bootstrapWindow) {
                Log.d(TAG, "ensureActivePlaybackIfExpected: waiting local prepare. reason=" + reason);
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
                Log.d(TAG, "ensureActivePlaybackIfExpected: restarted local playback. reason=" + reason);
            } catch (Exception e) {
                if (localSourcePreparing || bootstrapWindow) {
                    Log.d(TAG, "ensureActivePlaybackIfExpected: local source still bootstrapping. reason=" + reason);
                    return;
                }
                Log.w(TAG, "ensureActivePlaybackIfExpected: local restart failed, reloading track. reason=" + reason, e);
                playCurrentTrack();
            }
            return;
        }

        if (!hasPendingStreamResolution()) {
            if (bootstrapWindow || localSourcePreparing) {
                Log.d(TAG, "ensureActivePlaybackIfExpected: skip replay during bootstrap. reason=" + reason);
                return;
            }
            playCurrentTrack();
        } else {
            Log.d(TAG, "ensureActivePlaybackIfExpected: resolver already running. reason=" + reason);
        }
    }

    private boolean isEffectivePlaying() {
        if (!isPlaying) {
            return false;
        }
        if (usingOfflineSource && localExoMediaPlayer != null) {
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
        persistPositionForLoadedTrack();
        persistPlaybackSnapshot(true);
        cancelAutoplayRecovery();
        cancelPlaybackErrorRetry();
        cancelDeferredBackdropLoad();
        cancelNextUpReveal();
        cancelPendingSocialStatsFetch();
        cancelPendingStreamResolver();
        clearPlayerCoverRequest();
        clearPlayerBackdropRequest();
        resetPlayerHeroContainerHeight();
        stopLocalProgressTicker();
        releaseLocalExoMediaPlayer();
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
                if (usingOfflineSource && localExoMediaPlayer != null) {
                    try {
                        localExoMediaPlayer.pause();
                        stopLocalProgressTicker();
                    } catch (Exception ignored) {
                    }
                }
                isPlaying = false;
                persistPositionForLoadedTrack();
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
                if (usingOfflineSource && localExoMediaPlayer != null) {
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
                persistPositionForLoadedTrack();
                updateMediaSessionState();
            }
        });
        mediaSession.setActive(true);
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
        moveTrack(delta, false);
    }

    private void moveTrack(int delta, boolean fromCompletion) {
        if (tracks.isEmpty()) {
            return;
        }

        if (!fromCompletion) {
            cancelOfflineCrossfade();
        }

        if (!fromCompletion) {
            persistPositionForLoadedTrack();
        }

        if (fromCompletion && repeatMode == REPEAT_MODE_ONE) {
            currentSeconds = 0;
            lastPersistedSecond = -1;
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
        String targetVideoId = tracks.get(currentIndex).videoId;
        if (!TextUtils.isEmpty(targetVideoId)) {
            clearPersistedPositionFor(targetVideoId);
        }
        currentSeconds = 0;
        lastPersistedSecond = -1;
        playCurrentTrack();
        if (fromCompletion) {
            scheduleAutoplayRecoveryForCurrentTrack();
        } else {
            cancelAutoplayRecovery();
        }
        syncMiniStateWithPlaylist();
    }

    private boolean shouldSkipRestrictedTrack(@NonNull String videoId, boolean hasOfflineLocal) {
        if (!isAdded() || TextUtils.isEmpty(videoId) || hasOfflineLocal) {
            return false;
        }
        return OfflineRestrictionStore.isRestricted(requireContext(), videoId);
    }

    private boolean skipRestrictedTrackInQueue() {
        if (tracks.size() <= 1) {
            markPlaybackUnavailable("Esta canción está restringida.");
            return false;
        }

        int startIndex = currentIndex;
        for (int step = 1; step < tracks.size(); step++) {
            int candidateIndex = (startIndex + step) % tracks.size();
            PlayerTrack candidate = tracks.get(candidateIndex);
            if (candidate == null || TextUtils.isEmpty(candidate.videoId)) {
                continue;
            }

            boolean hasOfflineLocal = OfflineAudioStore.hasValidatedOfflineAudio(
                    requireContext(),
                    candidate.videoId,
                    candidate.duration
            );
            if (shouldSkipRestrictedTrack(candidate.videoId, hasOfflineLocal)) {
                continue;
            }

            currentIndex = candidateIndex;
            currentSeconds = 0;
            lastPersistedSecond = -1;
            isPlaying = true;
            bindCurrentTrack(true);
            playCurrentTrack();
            syncMiniStateWithPlaylist();
            return true;
        }

        markPlaybackUnavailable("No hay canciones reproducibles en la cola.");
        return false;
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
        clearPersistedPositionFor(loadedVideoId);
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

    private void markTrackAsRestricted(@Nullable String videoId) {
        if (!isAdded() || TextUtils.isEmpty(videoId)) {
            return;
        }
        OfflineRestrictionStore.markRestricted(requireContext(), videoId);
    }

    @NonNull
    private String buildPlaybackErrorMessage() {
        return "No se pudo reproducir este tema.";
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
        if (isAdded()) {
            
        }
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
                persistPositionForLoadedTrack();
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
            persistPositionForLoadedTrack();
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
        int repeatColor = repeatMode == REPEAT_MODE_OFF
                ? ContextCompat.getColor(requireContext(), android.R.color.white)
                : ContextCompat.getColor(requireContext(), R.color.stitch_blue);

        if (btnShuffle != null) {
            btnShuffle.setImageTintList(ColorStateList.valueOf(iconColor));
            btnShuffle.setAlpha(1f);
        }
        if (vPlayerShuffleIndicator != null) {
            vPlayerShuffleIndicator.setVisibility(View.GONE);
        }

        if (btnRepeat != null) {
            btnRepeat.setImageTintList(ColorStateList.valueOf(repeatColor));
            btnRepeat.setAlpha(1f);
        }
        if (vPlayerRepeatIndicator != null) {
            vPlayerRepeatIndicator.setVisibility(repeatMode == REPEAT_MODE_ONE ? View.VISIBLE : View.INVISIBLE);
        }
    }

    private void playCurrentTrack() {
        if (tracks.isEmpty()) {
            return;
        }

        int resumeSeconds = currentSeconds; // preserve any position loaded by bindCurrentTrack(true)
        currentSeconds = 0;
        totalSeconds = 1;
        
        if (currentIndex < 0 || currentIndex >= tracks.size()) {
            currentIndex = 0;
        }

        PlayerTrack track = tracks.get(currentIndex);
        if (track == null || TextUtils.isEmpty(track.videoId)) {
            return;
        }

        // Bind metadata. forceZero=true resets UI to 0, but we restore resume position after.
        // NOTE: Do NOT call showPlayerArtworkLoadingState() here — keep old cover visible
        // until the new one loads via Glide, preventing the black flash.
        bindCurrentTrackInternal(false, true);
        // Reset state BEFORE restoring resumeSeconds
        cancelOfflineCrossfade();
        resetPlaybackStateForNewTrack();
        
        if (resumeSeconds > 0) {
            currentSeconds = resumeSeconds;
            isRestoringPosition = true;
            lastSeekTargetSeconds = resumeSeconds;
        } else {
            isRestoringPosition = false;
            lastSeekTargetSeconds = -1;
        }
        
        lastPlaybackStartRequestAtMs = SystemClock.elapsedRealtime();

        String previousLoadedVideoId = loadedVideoId;
        if (!TextUtils.equals(previousLoadedVideoId, track.videoId)) {
        }

        cancelPlaybackErrorRetry();
        if (hasPendingStreamResolution() && TextUtils.equals(loadedVideoId, track.videoId)) {
            Log.d(TAG, "playCurrentTrack: resolver already running for same videoId=" + track.videoId);
            return;
        }
        cancelPendingStreamResolver();

        loadedVideoId = track.videoId;
        long requestToken = ++activePlaybackRequestToken;

        boolean hasOfflineLocal = isAdded()
                && OfflineAudioStore.hasOfflineAudio(requireContext(), track.videoId);
        
        if (shouldSkipRestrictedTrack(track.videoId, hasOfflineLocal)) {
            Log.d(TAG, "playCurrentTrack: restricted track skipped in queue. videoId=" + track.videoId);
            if (skipRestrictedTrackInQueue()) {
                return;
            }
        }

        if (hasOfflineLocal) {
            Log.d(TAG, "playCurrentTrack: prioritizing local offline audio. videoId=" + track.videoId);
            List<String> directSources = buildDirectSourceCandidates(track);
            attemptPlaybackFromSources(track, directSources, 0, requestToken, 0);
            return;
        }

        // Check if we have a prefetched URL for THIS track
        if (TextUtils.equals(track.videoId, prefetchedNextVideoId) && !TextUtils.isEmpty(prefetchedNextUrl)) {
            Log.d(TAG, "playCurrentTrack: using prefetched stream for videoId=" + track.videoId);
            String url = prefetchedNextUrl;
            // Clear prefetch after use
            prefetchedNextVideoId = null;
            prefetchedNextUrl = null;
            
            startMediaPlaybackFromSource(track, url, requestToken, () -> {
                // If prefetched URL fails, try fresh resolution
                resolveAndPlayOnlineTrack(track, requestToken);
            });
            return;
        }

        // Fresh online resolution
        resolveAndPlayOnlineTrack(track, requestToken);
    }

    private void resolveAndPlayOnlineTrack(@NonNull PlayerTrack track, long requestToken) {
        resolveAndPlayOnlineTrack(track, requestToken, false);
    }

    private void resolveAndPlayOnlineTrack(@NonNull PlayerTrack track, long requestToken, boolean forceAlternativeClient) {
        if (!isNetworkAvailable()) {
            markPlaybackUnavailable("Sin conexión a internet.");
            return;
        }

        // Artwork is already being loaded by bindCurrentTrackInternal,
        // no need to wipe it here — keep old cover visible while resolving.

        pendingStreamResolverFuture = streamResolverExecutor.submit(() -> {
            String resolvedUrl = forceAlternativeClient
                    ? InnertubeResolver.resolveStreamUrl(track.videoId, true)
                    : InnertubeResolver.resolveStreamUrl(track.videoId);
            
            localProgressHandler.post(() -> {
                if (requestToken != activePlaybackRequestToken || !isAdded()) return;
                pendingStreamResolverFuture = null;

                if (!TextUtils.isEmpty(resolvedUrl)) {
                    startMediaPlaybackFromSource(track, resolvedUrl, requestToken, () -> {
                        // If direct playback fails, fallback to WebView
                        tryReresolveOrSkipCurrentTrack("Fallo de reproducción directa: re-resolviendo.", false);
                    });
                } else {
                    // Resolution failed, fallback to WebView
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

        // Skip if already offline
        if (isAdded() && OfflineAudioStore.hasOfflineAudio(requireContext(), nextTrack.videoId)) {
            return;
        }

        // Skip if already prefetched
        if (TextUtils.equals(nextTrack.videoId, prefetchedNextVideoId)) return;

        Log.d(TAG, "prefetchNextTrackStream: starting pre-fetch for videoId=" + nextTrack.videoId);
        streamResolverExecutor.submit(() -> {
            String url = InnertubeResolver.resolveStreamUrl(nextTrack.videoId);
            if (!TextUtils.isEmpty(url)) {
                prefetchedNextVideoId = nextTrack.videoId;
                prefetchedNextUrl = url;
                Log.d(TAG, "prefetchNextTrackStream: successfully prefetched videoId=" + nextTrack.videoId);
            }
        });
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
            Log.d(TAG, "attemptPlaybackFromSources: stale request ignored. token=" + requestToken
                    + " activeToken=" + activePlaybackRequestToken
                    + " trackVideoId=" + track.videoId
                    + " loadedVideoId=" + loadedVideoId);
            return;
        }

        if (sourceIndex >= sources.size()) {
            Log.d(TAG, "attemptPlaybackFromSources: exhausted sources for videoId=" + track.videoId
                    + " (stream directo agotado)");

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
        Log.d(TAG, "attemptPlaybackFromSources: trying sourceIndex=" + sourceIndex
            + " source=" + maskUrlForLog(source)
            + " retry=" + sourceRetryCount
            + " requestToken=" + requestToken);
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
        final boolean networkSource = isHttpStreamSource(source);
        Log.d(TAG, "startMediaPlaybackFromSource: source=" + maskUrlForLog(source)
            + " videoId=" + track.videoId
            + " token=" + requestToken);
        stopLocalProgressTicker();
        releaseLocalExoMediaPlayer();
        usingOfflineSource = true;
        localSourcePreparing = true;
        updatePlayerSurfaceForSource();

        // Try to use shared ExoPlayer to reduce startup latency
        ExoPlayer sharedExoPlayer = ExoPlayerManager.INSTANCE.getSharedExoPlayer();
        ExoMediaPlayer player;
        if (sharedExoPlayer != null) {
            try {
                player = new ExoMediaPlayer(requireContext().getApplicationContext(), sharedExoPlayer);
                Log.d(TAG, "Using shared ExoPlayer instance");
            } catch (Exception e) {
                Log.w(TAG, "Failed to use shared ExoPlayer, falling back", e);
                player = new ExoMediaPlayer(requireContext().getApplicationContext());
            }
        } else {
            player = new ExoMediaPlayer(requireContext().getApplicationContext());
        }
        localExoMediaPlayer = player;
        player.setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build());

        // Re-affirm EQ on the global session (session 0) — no per-player sessionId needed.
        try {
            if (isAdded()) {
                AudioEffectsService.sendApply(requireContext().getApplicationContext());
            }
        } catch (Exception ignored) {
        }


        player.setOnPreparedListener(mp -> {
            cancelSourcePrepareTimeout();
            localSourcePreparing = false;
            Log.d(TAG, "onPrepared: videoId=" + track.videoId + " token=" + requestToken);

            if (!isAdded()
                    || localExoMediaPlayer != mp
                    || requestToken != activePlaybackRequestToken
                    || !TextUtils.equals(track.videoId, loadedVideoId)) {
                Log.d(TAG, "onPrepared: released stale player for videoId=" + track.videoId
                        + " token=" + requestToken);
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

            if (currentSeconds > 0) {
                Log.d(TAG, "onPrepared: seeking to " + currentSeconds + "s for videoId=" + track.videoId);
                isRestoringPosition = true;
                lastSeekTargetSeconds = currentSeconds;
                try {
                    mp.seekTo(currentSeconds * 1000);
                } catch (Exception ignored) {
                }
            } else {
                isRestoringPosition = false;
                lastSeekTargetSeconds = -1;
                Log.d(TAG, "onPrepared: starting from 0s (no seek) for videoId=" + track.videoId);
            }

            if (isPlaying) {
                try {
                    mp.setVolume(1f, 1f);
                    mp.start();
                    Log.d(TAG, "onPrepared: playback started videoId=" + track.videoId
                            + " durationSec=" + totalSeconds);
                    startLocalProgressTicker();
                    
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
            if (requestToken != activePlaybackRequestToken) {
                return;
            }
            if (localCrossfadeInProgress && localCrossfadeIncomingPlayer != null) {
                return;
            }
            Log.d(TAG, "onCompletion: videoId=" + track.videoId + " token=" + requestToken);
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

            if (requestToken != activePlaybackRequestToken) {
                return true;
            }

            onFailure.run();
            return true;
        });

        try {
            if (isHttpStreamSource(source) && isAdded()) {
                Map<String, String> headers = new HashMap<>();
                headers.put("User-Agent", STREAM_HTTP_USER_AGENT);
                headers.put("Accept", "*/*");
                player.setDataSource(requireContext(), Uri.parse(source), headers);
            } else {
                player.setDataSource(source);
            }
            player.prepareAsync();
            Log.d(TAG, "startMediaPlaybackFromSource: prepareAsync requested for source=" + maskUrlForLog(source));
            scheduleSourcePrepareTimeout(track, requestToken, player, onFailure);
        } catch (IllegalStateException ise) {
            cancelSourcePrepareTimeout();
            localSourcePreparing = false;
            Log.e(TAG, "startMediaPlaybackFromSource: ExoPlayer thread is dead, reinitializing manager", ise);
            if (isAdded()) {
                ExoPlayerManager.INSTANCE.reinitialize(requireContext().getApplicationContext());
            }
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
        updatePlayPauseIcon();
        updateMediaSessionMetadata();
        updateMediaSessionState();
        updateMediaNotification();
        syncMiniStateWithPlaylist();
        persistPlaybackSnapshot(false);
    }

    private void resetPlaybackStateForNewTrack() {
        currentSeconds = 0;
        totalSeconds = 1;
        if (tvCurrentTime != null) tvCurrentTime.setText("00:00");
        if (tvTotalTime != null) tvTotalTime.setText("--:--");
        if (sbPlaybackProgress != null) sbPlaybackProgress.setProgress(0);
        
        updatePlayPauseIcon();
        updateMediaSessionMetadata();
        updateMediaSessionState();
        updateMediaNotification();
        syncMiniStateWithPlaylist();
        
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
            prefetchedNextUrl = null;
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
            Log.d(TAG, "tryResolveAudiusAndReplay: resolvedUrl=" + maskUrlForLog(resolved)
                    + " videoId=" + track.videoId);

            localProgressHandler.post(() -> {
                pendingStreamResolverFuture = null;
                if (!isAdded()) {
                    return;
                }
                if (requestToken != activePlaybackRequestToken
                        || !TextUtils.equals(track.videoId, loadedVideoId)) {
                    Log.d(TAG, "tryResolveAudiusAndReplay: stale callback ignored. token=" + requestToken
                            + " activeToken=" + activePlaybackRequestToken
                            + " trackVideoId=" + track.videoId
                            + " loadedVideoId=" + loadedVideoId);
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
            Log.d(TAG, "fetchAudiusStreamUrl: query='" + query + "' videoId=" + track.videoId);

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
            Log.d(TAG, "fetchAudiusStreamUrl: candidates=" + data.length() + " query='" + query + "'");

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
            Log.d(TAG, "fetchAudiusStreamUrl: selectedTrackId=" + selectedTrackId + " score=" + selectedScore);

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

    private void ensureProgressUiBeforeVideoLoad(@NonNull PlayerTrack track) {
        totalSeconds = Math.max(totalSeconds, Math.max(1, parseDurationSeconds(track.duration)));
        currentSeconds = Math.max(0, Math.min(currentSeconds, Math.max(0, totalSeconds - 1)));

        tvCurrentTime.setText(formatSeconds(currentSeconds));
        tvTotalTime.setText(TextUtils.isEmpty(track.duration) ? formatSeconds(totalSeconds) : track.duration);

        int progress = Math.round((Math.max(0, currentSeconds) / (float) Math.max(1, totalSeconds)) * 1000f);
        sbPlaybackProgress.setProgress(Math.max(0, Math.min(1000, progress)));
    }

    private boolean isHttpStreamSource(@NonNull String source) {
        return source.startsWith("https://") || source.startsWith("http://");
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
                || !usingOfflineSource
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
        int seconds = OFFLINE_CROSSFADE_DEFAULT_SECONDS;
        if (settingsPrefs != null) {
            seconds = settingsPrefs.getInt(
                    CloudSyncManager.KEY_OFFLINE_CROSSFADE_SECONDS,
                    OFFLINE_CROSSFADE_DEFAULT_SECONDS
            );
        }
        seconds = Math.max(0, Math.min(OFFLINE_CROSSFADE_MAX_SECONDS, seconds));
        if (seconds <= 0) {
            return 0;
        }
        if (seconds == 1) {
            return OFFLINE_CROSSFADE_FIRST_STEP_MS;
        }
        return seconds * 1000;
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

        String nextUrl = null;
        boolean nextIsNetwork = false;

        // Try offline first
        if (isAdded() && OfflineAudioStore.hasValidatedOfflineAudio(requireContext(), nextTrack.videoId, nextTrack.duration)) {
            File nextFile = OfflineAudioStore.getExistingOfflineAudioFile(requireContext(), nextTrack.videoId);
            if (nextFile != null && nextFile.isFile() && nextFile.length() > 0L) {
                nextUrl = nextFile.getAbsolutePath();
            }
        }

        // Try prefetched online
        if (nextUrl == null && TextUtils.equals(nextTrack.videoId, prefetchedNextVideoId) && !TextUtils.isEmpty(prefetchedNextUrl)) {
            nextUrl = prefetchedNextUrl;
            nextIsNetwork = true;
        }

        if (nextUrl == null) {
            // No direct source ready for crossfade (no offline, no prefetch)
            startOfflineFadeOutOnly(outgoing, crossfadeDurationMs);
            return;
        }

        ExoMediaPlayer incoming = new ExoMediaPlayer(requireContext().getApplicationContext());
        try {
            incoming.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build());
            
            if (nextIsNetwork) {
                Map<String, String> headers = new HashMap<>();
                headers.put("User-Agent", STREAM_HTTP_USER_AGENT);
                headers.put("Accept", "*/*");
                incoming.setDataSource(requireContext().getApplicationContext(), Uri.parse(nextUrl), headers);
            } else {
                incoming.setDataSource(nextUrl);
            }

            incoming.prepare();
            incoming.setVolume(0f, 0f);
            incoming.start();
        } catch (Exception e) {
            Log.e(TAG, "Crossfade: failed to prepare incoming player", e);
            releaseSingleExoMediaPlayer(incoming);
            startOfflineFadeOutOnly(outgoing, crossfadeDurationMs);
            return;
        }

        localCrossfadeIncomingPlayer = incoming;
        localCrossfadeInProgress = true;
        localCrossfadeTargetIndex = nextIndex;
        localCrossfadeStartedAtMs = SystemClock.elapsedRealtime();
        outgoing.setOnCompletionListener(null);

        localCrossfadeTicker = new Runnable() {
            @Override
            public void run() {
                if (!localCrossfadeInProgress || localExoMediaPlayer != outgoing) {
                    return;
                }

                float progress = Math.min(
                        1f,
                        Math.max(0f, (SystemClock.elapsedRealtime() - localCrossfadeStartedAtMs)
                        / (float) crossfadeDurationMs)
                );
                float outVolume = Math.max(0f, 1f - progress);
                float inVolume = Math.max(0f, progress);

                try {
                    outgoing.setVolume(outVolume, outVolume);
                } catch (Exception ignored) {
                }
                if (localCrossfadeIncomingPlayer != null) {
                    try {
                        localCrossfadeIncomingPlayer.setVolume(inVolume, inVolume);
                    } catch (Exception ignored) {
                    }
                }

                if (progress >= 1f) {
                    finalizeOfflineCrossfade(outgoing);
                    return;
                }

                localProgressHandler.postDelayed(this, OFFLINE_CROSSFADE_STEP_MS);
            }
        };

        localProgressHandler.post(localCrossfadeTicker);
    }

    private void startOfflineFadeOutOnly(@NonNull ExoMediaPlayer outgoing, int fadeDurationMs) {
        if (localCrossfadeInProgress || localExoMediaPlayer != outgoing) {
            return;
        }

        int safeFadeDurationMs = Math.max(1, fadeDurationMs);

        localCrossfadeIncomingPlayer = null;
        localCrossfadeInProgress = true;
        localCrossfadeTargetIndex = -1;
        localCrossfadeStartedAtMs = SystemClock.elapsedRealtime();

        localCrossfadeTicker = new Runnable() {
            @Override
            public void run() {
                if (!localCrossfadeInProgress || localExoMediaPlayer != outgoing) {
                    return;
                }

                float progress = Math.min(
                        1f,
                        Math.max(0f, (SystemClock.elapsedRealtime() - localCrossfadeStartedAtMs)
                        / (float) safeFadeDurationMs)
                );
                float outVolume = Math.max(0f, 1f - progress);

                try {
                    outgoing.setVolume(outVolume, outVolume);
                } catch (Exception ignored) {
                }

                if (progress >= 1f) {
                    localCrossfadeInProgress = false;
                    localCrossfadeStartedAtMs = 0L;
                    localCrossfadeTargetIndex = -1;
                    localCrossfadeTicker = null;
                    return;
                }

                localProgressHandler.postDelayed(this, OFFLINE_CROSSFADE_STEP_MS);
            }
        };

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

        localCrossfadeInProgress = false;
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
        usingOfflineSource = true;
        currentIndex = nextIndex;
        pauseRequestedByUser = false;
        isPlaying = true;
        currentSeconds = 0;
        lastPersistedSecond = -1;

        PlayerTrack track = tracks.get(currentIndex);
        loadedVideoId = track.videoId;
        if (TextUtils.isEmpty(loadedVideoId)) {
            loadedVideoId = "";
        }

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
        }

        bindCurrentTrack(false);
        startLocalProgressTicker();
    }

    private void cancelOfflineCrossfade() {
        localCrossfadeInProgress = false;
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
    }

    private void startLocalProgressTicker() {
        localProgressHandler.removeCallbacks(localProgressTicker);
        localProgressHandler.post(localProgressTicker);
    }

    private void stopLocalProgressTicker() {
        localProgressHandler.removeCallbacks(localProgressTicker);
    }

    private void releaseLocalExoMediaPlayer() {
        cancelOfflineCrossfade();
        cancelSourcePrepareTimeout();
        localSourcePreparing = false;
        if (localExoMediaPlayer == null) {
            return;
        }
        try {
            localExoMediaPlayer.stop();
        } catch (Exception ignored) {
        }
        try {
            localExoMediaPlayer.release();
        } catch (Exception ignored) {
        }
        localExoMediaPlayer = null;
        usingOfflineSource = false;
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

    private void clearPlayerBackdropRequest() {
        if (playerBackdropTarget == null) {
            return;
        }
        if (isAdded()) {
            Glide.with(this).clear(playerBackdropTarget);
        }
        playerBackdropTarget = null;
    }

    private void clearNotificationArtworkRequest() {
        if (notificationArtworkTarget == null) {
            return;
        }
        if (persistentAppContext != null) {
            Glide.with(persistentAppContext).clear(notificationArtworkTarget);
        }
        notificationArtworkTarget = null;
    }

    private void showPlayerArtworkLoadingState() {
        if (ivPlayerCover != null) {
            // Set to black instead of null as per user request
            ivPlayerCover.setImageResource(android.R.color.black);
        }
        if (ivPlayerBackdrop != null) {
            ivPlayerBackdrop.animate().cancel();
            ivPlayerBackdrop.setImageDrawable(null);
            ivPlayerBackdrop.setAlpha(0f);
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

    private void revealBackdropWithFade() {
        if (ivPlayerBackdrop == null) {
            return;
        }

        ivPlayerBackdrop.animate().cancel();
        applyPlayerBackdropBlur();
        float targetAlpha = ivPlayerBackdrop.getAlpha();
        if (targetAlpha <= 0f) {
            targetAlpha = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ? 0.68f : 0.35f;
        }
        ivPlayerBackdrop.setAlpha(targetAlpha);
    }

    private void clearMediaNotificationArtwork() {
        clearNotificationArtworkRequest();
        activeNotificationArtworkVideoId = "";
        mediaNotificationLargeIcon = null;
        mediaNotificationLargeIconVideoId = "";
        mediaSessionArtwork = null;
        mediaSessionArtworkVideoId = "";
    }

    private void cacheMediaNotificationArtwork(@NonNull String videoId, @NonNull Bitmap source) {
        if (source.isRecycled()) {
            return;
        }

        Bitmap notificationBitmap = scaleArtworkBitmap(source, 1024);
        Bitmap sessionBitmap = scaleArtworkBitmap(source, MEDIA_SESSION_ARTWORK_MAX_SIZE_PX);

        mediaNotificationLargeIcon = notificationBitmap;
        mediaNotificationLargeIconVideoId = videoId;
        mediaSessionArtwork = sessionBitmap;
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
        setPlayerHeroContainerHeight(dpToPx(PLAYER_HERO_DEFAULT_HEIGHT_DP));
    }

    private void setPlayerHeroContainerHeight(int heightPx) {
        // Disabled to allow ConstraintLayout 1:1 ratio to rule
        if (true) return;
        
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
        if (flPlayerHero == null) {
            return;
        }

        int width = flPlayerHero.getWidth();
        if (width <= 0) {
            flPlayerHero.post(() -> adjustPlayerHeroForCover(bitmap));
            return;
        }

        int bmpWidth = Math.max(1, bitmap.getWidth());
        int bmpHeight = Math.max(1, bitmap.getHeight());
        float aspect = bmpHeight / (float) bmpWidth;

        int targetHeight = dpToPx(PLAYER_HERO_DEFAULT_HEIGHT_DP);
        if (aspect > 1.02f) {
            targetHeight = Math.round(width * aspect);
            targetHeight = Math.max(dpToPx(PLAYER_HERO_MIN_HEIGHT_DP), targetHeight);
            targetHeight = Math.min(dpToPx(PLAYER_HERO_MAX_HEIGHT_DP), targetHeight);
        }

        setPlayerHeroContainerHeight(targetHeight);
    }

    private int dpToPx(int dp) {
        return Math.round(dp * requireContext().getResources().getDisplayMetrics().density);
    }

    private void loadMediaNotificationArtwork(@NonNull PlayerTrack track) {
        String requestVideoId = track.videoId == null ? "" : track.videoId.trim();
        if (TextUtils.isEmpty(requestVideoId)) return;

        String fallbackImageUrl = track.imageUrl == null ? "" : track.imageUrl.trim();
        String preferredImageUrl = resolvePreferredCoverUrl(requestVideoId, fallbackImageUrl);
        if (TextUtils.isEmpty(preferredImageUrl)) return;
        if (persistentAppContext == null) return;

        activeNotificationArtworkVideoId = requestVideoId;
        clearNotificationArtworkRequest();

        notificationArtworkTarget = new CustomTarget<Bitmap>() {
            @Override
            public void onResourceReady(@NonNull Bitmap resource, @Nullable Transition<? super Bitmap> transition) {
                if (!TextUtils.equals(activeNotificationArtworkVideoId, requestVideoId)) {
                    return;
                }
                cacheMediaNotificationArtwork(requestVideoId, resource);
                updateMediaSessionMetadata();
            }

            @Override
            public void onLoadCleared(@Nullable Drawable placeholder) {
            }

            @Override
            public void onLoadFailed(@Nullable Drawable errorDrawable) {
                if (!TextUtils.equals(activeNotificationArtworkVideoId, requestVideoId)) {
                    return;
                }
                if (TextUtils.equals(mediaNotificationLargeIconVideoId, requestVideoId)) {
                    clearMediaNotificationArtwork();
                    updateMediaSessionMetadata();
                    }
            }
        };

        Glide.with(persistentAppContext)
                .asBitmap()
                .load(preferredImageUrl)
                .transform(new YouTubeCropTransformation())
                .format(DecodeFormat.PREFER_RGB_565)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .into(notificationArtworkTarget);
    }

    private void loadPlayerCover(@NonNull PlayerTrack track, boolean bootstrapArtwork) {
        loadMediaNotificationArtwork(track);
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

        if (!TextUtils.equals(requestVideoId, mediaNotificationLargeIconVideoId)) {
            clearMediaNotificationArtwork();
        }

        activeCoverVideoId = requestVideoId;
        clearPlayerCoverRequest();

        playerCoverTarget = new CustomTarget<Bitmap>() {
            @Override
            public void onResourceReady(@NonNull Bitmap resource, @Nullable Transition<? super Bitmap> transition) {
                if (!isAdded() || !TextUtils.equals(activeCoverVideoId, requestVideoId)) {
                    return;
                }
                
                // Process the bitmap using the intelligent smart-crop utility
                Bitmap processedResource = YouTubeImageProcessor.smartCrop(resource);

                ivPlayerCover.setImageBitmap(processedResource);
                ivPlayerCover.setAlpha(1f);
                adjustPlayerHeroForCover(processedResource);
                cacheMediaNotificationArtwork(requestVideoId, processedResource);
                updateMediaSessionMetadata();
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
                if (TextUtils.equals(mediaNotificationLargeIconVideoId, requestVideoId)) {
                    clearMediaNotificationArtwork();
                }
                ivPlayerCover.setImageDrawable(null);
                updateMediaSessionMetadata();
                    }
        };

        Glide.with(this)
                .asBitmap()
                .load(preferredImageUrl)
                .transform(new YouTubeCropTransformation())
                .format(DecodeFormat.PREFER_ARGB_8888) // High quality
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .error(
                        Glide.with(this)
                                .asBitmap()
                                .load("https://i.ytimg.com/vi/" + Uri.encode(requestVideoId) + "/hqdefault.jpg")
                                .transform(new YouTubeCropTransformation())
                                .format(DecodeFormat.PREFER_ARGB_8888)
                                .diskCacheStrategy(DiskCacheStrategy.ALL)
                                .error(
                                        TextUtils.isEmpty(fallbackImageUrl)
                                                || TextUtils.equals(preferredImageUrl, fallbackImageUrl)
                                                ? null
                                                : Glide.with(this)
                                                        .asBitmap()
                                                        .load(fallbackImageUrl)
                                                        .transform(new YouTubeCropTransformation())
                                                        .format(DecodeFormat.PREFER_ARGB_8888)
                                                        .diskCacheStrategy(DiskCacheStrategy.ALL)
                                )
                )
                .into(playerCoverTarget);
    }

    private void loadPlayerBackdrop(@NonNull PlayerTrack track, boolean bootstrapArtwork) {
        if (ivPlayerBackdrop == null) {
            completePlayerArtworkBootstrap();
            return;
        }

        String requestVideoId = track.videoId == null ? "" : track.videoId.trim();
        String fallbackImageUrl = track.imageUrl == null ? "" : track.imageUrl.trim();
        String preferredImageUrl = resolveLowResBackdropUrl(requestVideoId, fallbackImageUrl);

        activeBackdropVideoId = requestVideoId;
        clearPlayerBackdropRequest();

        if (TextUtils.isEmpty(preferredImageUrl)) {
            ivPlayerBackdrop.setImageDrawable(null);
            ivPlayerBackdrop.setAlpha(0f);
            completePlayerArtworkBootstrap();
            return;
        }

        playerBackdropTarget = new CustomTarget<Drawable>() {
            @Override
            public void onResourceReady(@NonNull Drawable resource, @Nullable Transition<? super Drawable> transition) {
                if (!isAdded() || !TextUtils.equals(activeBackdropVideoId, requestVideoId)) {
                    return;
                }

                ivPlayerBackdrop.setImageDrawable(resource);
                if (bootstrapArtwork) {
                    revealBackdropWithFade();
                    completePlayerArtworkBootstrap();
                } else {
                    applyPlayerBackdropBlur();
                }
            }

            @Override
            public void onLoadCleared(@Nullable Drawable placeholder) {
                if (!isAdded()) {
                    return;
                }

                if (bootstrapArtwork) {
                    ivPlayerBackdrop.setImageDrawable(null);
                    ivPlayerBackdrop.setAlpha(0f);
                }
                // Non-bootstrap: keep old backdrop visible (don't wipe it)
            }

            @Override
            public void onLoadFailed(@Nullable Drawable errorDrawable) {
                if (!isAdded() || !TextUtils.equals(activeBackdropVideoId, requestVideoId)) {
                    return;
                }

                if (bootstrapArtwork) {
                    ivPlayerBackdrop.setImageDrawable(null);
                    ivPlayerBackdrop.setAlpha(0f);
                }
                // Non-bootstrap: keep old backdrop visible on failure
                completePlayerArtworkBootstrap();
            }
        };

        Glide.with(this)
                .asDrawable()
                .load(preferredImageUrl)
                .centerCrop()
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .error(
                        TextUtils.isEmpty(fallbackImageUrl)
                                || TextUtils.equals(preferredImageUrl, fallbackImageUrl)
                                ? null
                                : Glide.with(this)
                                        .asDrawable()
                                        .load(fallbackImageUrl)
                                        .centerCrop()
                                        .diskCacheStrategy(DiskCacheStrategy.ALL)
                )
                .into(playerBackdropTarget);
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

    private String resolvePreferredCoverUrl(
            @NonNull String videoId,
            @NonNull String fallbackImageUrl
    ) {
        if (!TextUtils.isEmpty(videoId)) {
            return "https://i.ytimg.com/vi/" + Uri.encode(videoId) + "/hqdefault.jpg";
        }
        return fallbackImageUrl;
    }

    /**
     * Intelligent edge detection to remove black bars (letterboxing) or 
     * blurred dominant color pillars from YouTube music thumbnails.
     */

    @Override
    public void onDestroy() {
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
        refreshSocialActionsForCurrentTrack(track);
        refreshFavoriteActionForCurrentTrack();

        totalSeconds = Math.max(1, parseDurationSeconds(track.duration));
        if (forceZero) {
            currentSeconds = 0;
        } else if (allowResume) {
            currentSeconds = getPersistedPositionFor(track.videoId, totalSeconds);
        } else {
            currentSeconds = Math.min(currentSeconds, totalSeconds);
        }

        tvCurrentTime.setText(formatSeconds(currentSeconds));
        tvTotalTime.setText(TextUtils.isEmpty(track.duration) ? formatSeconds(totalSeconds) : track.duration);

        int progress = Math.round((currentSeconds / (float) Math.max(1, totalSeconds)) * 1000f);
        sbPlaybackProgress.setProgress(Math.max(0, Math.min(1000, progress)));
        updatePlayPauseIcon();

        boolean bootstrapArtwork = playerArtworkBootstrapPending;
        if (bootstrapArtwork) {
            showPlayerArtworkLoadingState();
            if (!isAdded()) return;
            loadPlayerCover(track, bootstrapArtwork);
            scheduleBackdropLoad(track, bootstrapArtwork);
        } else {
            loadPlayerCover(track, bootstrapArtwork);
            scheduleBackdropLoad(track, bootstrapArtwork);
        }

        updateMediaSessionMetadata();
        updateMediaSessionState();
        if (bootstrapArtwork) {
            cancelNextUpReveal();
            nextUpTracks.clear();
            if (nextUpAdapter != null) {
                nextUpAdapter.notifyDataSetChanged();
            }
        } else {
            refreshNextUp();
        }
        syncMiniStateWithPlaylist();
        if (!forceZero) {
            persistPlaybackSnapshot(false);
        }
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
            actionFavorite.setOnClickListener(v -> toggleCurrentTrackFavorite());
        }
        refreshFavoriteActionForCurrentTrack();
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

        Fragment playlist = getParentFragmentManager().findFragmentByTag("playlist_detail");
        if (playlist instanceof PlaylistDetailFragment) {
            ((PlaylistDetailFragment) playlist).externalRefreshFavoritesIfActive();
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

    private void refreshFavoriteActionForCurrentTrack() {
        if (!isAdded()) {
            return;
        }

        boolean isFavorite = false;
        if (!tracks.isEmpty() && currentIndex >= 0 && currentIndex < tracks.size()) {
            String currentVideoId = tracks.get(currentIndex).videoId;
            if (!TextUtils.isEmpty(currentVideoId)) {
                isFavorite = FavoritesPlaylistStore.isFavorite(requireContext(), currentVideoId);
            }
        }

        if (tvActionFavoriteLabel != null) {
            tvActionFavoriteLabel.setText(isFavorite
                    ? "Guardado"
                    : "Guardar en favoritos");
        }

        if (ivActionFavoriteIcon != null) {
            int tint = ContextCompat.getColor(requireContext(), R.color.text_primary);
            ivActionFavoriteIcon.setColorFilter(tint);
        }

        if (actionFavorite != null) {
            actionFavorite.setAlpha(isFavorite ? 1f : 0.92f);
        }
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
        if (tvActionDislikeCount != null) {
            tvActionDislikeCount.setText(stats.dislikeCount);
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

        String targetVideoId = tracks.get(index).videoId;
        boolean sameAsLoaded = !TextUtils.isEmpty(targetVideoId)
                && TextUtils.equals(targetVideoId, loadedVideoId);

        if (!sameAsLoaded) {
            persistPositionForLoadedTrack();
        }

        if (!startFromBeginning
                && sameAsLoaded
                && currentIndex == index
                && isEffectivePlaying()) {
            syncMiniStateWithPlaylist();
            persistPlaybackSnapshot(false);
            return;
        }

        if (startFromBeginning && !TextUtils.isEmpty(targetVideoId)) {
            clearPersistedPositionFor(targetVideoId);
            currentSeconds = 0;
            lastPersistedSecond = -1;
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

    public void externalPauseForSessionExit() {
        pauseRequestedByUser = true;
        cancelAutoplayRecovery();
        cancelPendingStreamResolver();
        persistPositionForLoadedTrack();
        stopLocalProgressTicker();
        releaseLocalExoMediaPlayer();
        isPlaying = false;
        updatePlayPauseIcon();
        updateMediaSessionState();
        updateMediaNotification();
        syncMiniStateWithPlaylist();
        persistPlaybackSnapshot(true);
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
        persistPositionForLoadedTrack();
        persistPlaybackSnapshot(false);
    }

    public int externalGetCurrentIndex() {
        return currentIndex;
    }

    public boolean externalIsPlaying() {
        return isEffectivePlaying();
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
        return Math.max(0, currentSeconds);
    }

    public int externalGetTotalSeconds() {
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

        int targetIndexFromArgs = Math.max(0, selectedIndex);
        String targetVideoId = targetIndexFromArgs < videoIds.size()
                ? safeValue(videoIds.get(targetIndexFromArgs))
                : "";

        // Important: check if the track we ARE playing is the same as the one we WILL play
        boolean sameAsLoaded = !TextUtils.isEmpty(targetVideoId)
                && TextUtils.equals(targetVideoId, loadedVideoId);

        // ALWAYS stop previous audio before starting new playback to prevent overlap
        if (!sameAsLoaded) {
            persistPositionForLoadedTrack();
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
            lastPersistedSecond = -1;
        }

        cacheOriginalQueueOrder();
        if (shuffleEnabled) {
            randomizeQueueFromCurrentTrack();
        }

        if (startFromBeginning) {
            String selectedVideoId = tracks.get(currentIndex).videoId;
            if (!TextUtils.isEmpty(selectedVideoId)) {
                clearPersistedPositionFor(selectedVideoId);
            }
            currentSeconds = 0;
        }

        if (!sameAsLoaded || startFromBeginning) {
            bindCurrentTrack(!startFromBeginning);
            playCurrentTrack();
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
        return collapseToMiniMode(true);
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

    private void applyPlayerBackdropBlur() {
        if (ivPlayerBackdrop == null) {
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ivPlayerBackdrop.setRenderEffect(RenderEffect.createBlurEffect(16f, 16f, Shader.TileMode.CLAMP));
            ivPlayerBackdrop.setAlpha(0.68f);
        } else {
            ivPlayerBackdrop.setAlpha(0.35f);
        }
    }

    private void updatePlayerSurfaceForSource() {
        if (ivPlayerCover == null) {
            return;
        }
        ivPlayerCover.setVisibility(View.VISIBLE);
        ivPlayerCover.setClickable(true);
        applyPlayerBackdropBlur();
    }

    private boolean collapseToMiniMode(boolean animate) {
        if (!isAdded()) {
            return false;
        }
        if (collapsingToMiniMode) {
            return true;
        }

        FragmentManager fm = getParentFragmentManager();
        if (fm.isStateSaved()) {
            return false;
        }

        collapsingToMiniMode = true;
        persistPositionForLoadedTrack();
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
        } else {
            transaction
                    .setCustomAnimations(
                            R.anim.hold,
                            R.anim.player_screen_exit
                    )
                    .remove(this);
        }

        transaction.commit();
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

        Fragment schedule = findAddedByTag(fm, "module_schedule");
        if (schedule != null) {
            return schedule;
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

        persistPositionForLoadedTrack();
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

    private int getPersistedPositionFor(@NonNull String videoId, int maxSeconds) {
        if (playerStatePrefs == null || TextUtils.isEmpty(videoId)) {
            return 0;
        }
        int saved = playerStatePrefs.getInt(PREF_PLAYBACK_POS_PREFIX + videoId, 0);
        if (saved <= 0) {
            return 0;
        }
        // If we don't have a valid duration yet, just trust the saved value.
        // Otherwise, clamp it to ensure we don't start past the end.
        if (maxSeconds <= 5) {
            return saved;
        }
        return Math.min(maxSeconds, saved);
    }

    private void persistPositionForLoadedTrack() {
        if (playerStatePrefs == null || TextUtils.isEmpty(loadedVideoId)) {
            return;
        }
        int safeMax = Math.max(1, externalGetTotalSeconds());
        int safeCurrent = Math.max(0, Math.min(safeMax - 1, externalGetCurrentSeconds()));
        
        final String vid = loadedVideoId;
        final int pos = safeCurrent;
        
        persistenceExecutor.execute(() -> {
            try {
                playerStatePrefs.edit()
                        .putInt(PREF_PLAYBACK_POS_PREFIX + vid, pos)
                        .commit();
            } catch (Exception ignored) {}
        });
    }

    private void clearPersistedPositionFor(@NonNull String videoId) {
        if (playerStatePrefs == null || TextUtils.isEmpty(videoId)) {
            return;
        }
        playerStatePrefs.edit().remove(PREF_PLAYBACK_POS_PREFIX + videoId).apply();
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

        long actions = PlaybackStateCompat.ACTION_PLAY
                | PlaybackStateCompat.ACTION_PAUSE
                | PlaybackStateCompat.ACTION_PLAY_PAUSE
                | PlaybackStateCompat.ACTION_SKIP_TO_NEXT
                | PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
                | PlaybackStateCompat.ACTION_SEEK_TO;

        int state = isPlaying ? PlaybackStateCompat.STATE_PLAYING : PlaybackStateCompat.STATE_PAUSED;
        float speed = isPlaying ? 1f : 0f;
        PlaybackStateCompat playbackState = new PlaybackStateCompat.Builder()
                .setActions(actions)
                .setState(state, Math.max(0, currentSeconds) * 1000L, speed)
                .build();

        mediaSession.setPlaybackState(playbackState);
        mediaSession.setActive(true);
    }


    private void updateMediaNotification() {
        if (mediaSession == null || tracks.isEmpty() || currentIndex < 0 || currentIndex >= tracks.size()) {
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
        }

        NotificationManagerCompat.from(persistentAppContext).notify(MEDIA_NOTIFICATION_ID, builder.build());
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

        PlayerTrack currentTrack = tracks.get(currentIndex);
        ArrayList<PlayerTrack> reorderedQueue = new ArrayList<>(Math.max(1, nextUpTracks.size() + 1));
        reorderedQueue.add(currentTrack);
        reorderedQueue.addAll(nextUpTracks);

        tracks.clear();
        tracks.addAll(reorderedQueue);
        currentIndex = 0;

        if (!shuffleEnabled) {
            cacheOriginalQueueOrder();
        }

        persistPlaybackSnapshot(false);
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
                        Math.max(0, current),
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
                            .putInt("yt_pos_" + currentTrack.videoId, Math.max(0, current))
                            .commit();
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
            nextUpTracks.add(tracks.get(idx));
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
            nextUpAdapter.notifyDataSetChanged();
            return;
        }

        nextUpAdapter.notifyDataSetChanged();

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

        interface OnNextUpTap {
            void onTap(int position);
        }

        interface OnDragStart {
            void onStartDrag(@NonNull NextUpViewHolder holder);
        }

        private final List<PlayerTrack> items;
        private final OnNextUpTap onNextUpTap;
        private final OnDragStart onDragStart;

        NextUpAdapter(
                @NonNull List<PlayerTrack> items,
                @NonNull OnNextUpTap onNextUpTap,
                @NonNull OnDragStart onDragStart
        ) {
            this.items = items;
            this.onNextUpTap = onNextUpTap;
            this.onDragStart = onDragStart;
        }

        boolean moveItem(int fromPosition, int toPosition) {
            if (fromPosition < 0 || fromPosition >= items.size()) {
                return false;
            }
            if (toPosition < 0 || toPosition >= items.size()) {
                return false;
            }
            if (fromPosition == toPosition) {
                return false;
            }
            if (fromPosition == 0 || toPosition == 0) {
                return false; // Restriction: cannot move 'Now Playing' or move anything above it
            }

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
            holder.tvNextUpTitle.setText(item.title);
            
            // Position 0 is the currently playing track
            if (position == 0) {
                holder.tvNextUpArtist.setText("Reproduciendo ahora");
                holder.tvNextUpArtist.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.stitch_blue)); // Primary color
            } else {
                holder.tvNextUpArtist.setText(item.artist);
                holder.tvNextUpArtist.setTextColor(Color.parseColor("#A0A0A0")); // Default gray
            }

            if (!TextUtils.isEmpty(item.imageUrl)) {
            String imageUrl = item.imageUrl.trim();
            ViewGroup.LayoutParams params = holder.ivNextUpArt.getLayoutParams();
            int rawWidth = holder.ivNextUpArt.getWidth() > 0
                ? holder.ivNextUpArt.getWidth()
                : (params == null ? 0 : params.width);
            int rawHeight = holder.ivNextUpArt.getHeight() > 0
                ? holder.ivNextUpArt.getHeight()
                : (params == null ? 0 : params.height);

            if (rawWidth > 0 && rawHeight > 0) {
                int targetWidth;
                int targetHeight;
                Glide.with(holder.itemView)
                    .load(imageUrl)
                    .transform(new YouTubeCropTransformation())
                    .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.ALL)
                    .placeholder(R.drawable.ic_music)
                    .error(R.drawable.ic_music)
                    .into(holder.ivNextUpArt);
            } else {
                com.bumptech.glide.RequestBuilder<Drawable> nextUpRequest = Glide.with(holder.itemView)
                    .load(imageUrl)
                    .transform(new YouTubeCropTransformation())
                    .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.ALL)
                    .placeholder(R.drawable.ic_music)
                    .error(R.drawable.ic_music);
                nextUpRequest.into(holder.ivNextUpArt);
            }
            } else {
                holder.ivNextUpArt.setImageResource(R.drawable.ic_music);
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

        @Override
        public int getItemCount() {
            int count = items.size();
            // Log.v(TAG, "NextUpAdapter.getItemCount: " + count);
            return count;
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

    private void showQueueBottomSheet() {
        if (!isAdded()) return;
        
        Log.d(TAG, "showQueueBottomSheet: tracks.size=" + tracks.size() + ", currentIndex=" + currentIndex);
        
        BottomSheetDialog bottomSheetDialog = new BottomSheetDialog(requireContext());
        View bsv = getLayoutInflater().inflate(R.layout.bottom_sheet_player_queue, null);
        bottomSheetDialog.setContentView(bsv);

        RecyclerView rvQueue = bsv.findViewById(R.id.rvQueue);
        LinearLayoutManager layoutManager = new LinearLayoutManager(requireContext());
        rvQueue.setLayoutManager(layoutManager);
        rvQueue.setHasFixedSize(false);
        
        nextUpTracks.clear();
        // Include current track at position 0, then the rest
        if (!tracks.isEmpty() && currentIndex >= 0 && currentIndex < tracks.size()) {
            nextUpTracks.add(tracks.get(currentIndex)); // Now playing
        }
        for (int i = 1; i < tracks.size(); i++) {
            int idx = (currentIndex + i) % tracks.size();
            nextUpTracks.add(tracks.get(idx));
        }
        
        Log.d(TAG, "showQueueBottomSheet: nextUpTracks.size=" + nextUpTracks.size());
        for (int i = 0; i < Math.min(5, nextUpTracks.size()); i++) {
            Log.d(TAG, "  item[" + i + "]=" + nextUpTracks.get(i).title);
        }
        
        nextUpAdapter = new NextUpAdapter(nextUpTracks, position -> {
            Log.d(TAG, "Queue item tapped: position=" + position);
            // Position 0 is the currently playing track - ignore clicks on it
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
                    Log.d(TAG, "Switching to track from queue: realIdx=" + realIdx);
                    if (!TextUtils.equals(tapped.videoId, loadedVideoId)) {
                        persistPositionForLoadedTrack();
                    }
                    currentIndex = realIdx;
                    isPlaying = true;
                    currentSeconds = 0;
                    lastPersistedSecond = -1;
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
        nextUpAdapter.notifyDataSetChanged();
        Log.d(TAG, "showQueueBottomSheet: adapter set, count=" + nextUpAdapter.getItemCount());
        
        // Handle empty state
        TextView tvEmptyQueue = bsv.findViewById(R.id.tvEmptyQueue);
        if (nextUpTracks.isEmpty()) {
            Log.d(TAG, "showQueueBottomSheet: nextUpTracks is EMPTY, showing empty view");
            rvQueue.setVisibility(View.GONE);
            tvEmptyQueue.setVisibility(View.VISIBLE);
        } else {
            Log.d(TAG, "showQueueBottomSheet: nextUpTracks is NOT empty (" + nextUpTracks.size() + "), showing RecyclerView");
            rvQueue.setVisibility(View.VISIBLE);
            tvEmptyQueue.setVisibility(View.GONE);
        }

        bottomSheetDialog.setOnShowListener(dialog -> {
            BottomSheetDialog d = (BottomSheetDialog) dialog;
            View bottomSheet = d.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheet != null) {
                bottomSheet.setBackgroundResource(android.R.color.transparent);
                // Set minimum height for the bottom sheet
                BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(bottomSheet);
                behavior.setPeekHeight(400); // Minimum height when collapsed
                behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
            }
        });

        ItemTouchHelper.SimpleCallback dragCallback = new ItemTouchHelper.SimpleCallback(
                ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0
        ) {
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
                applyNextUpOrderToQueue();
            }
        };

        nextUpItemTouchHelper = new ItemTouchHelper(dragCallback);
        nextUpItemTouchHelper.attachToRecyclerView(rvQueue);
        
        bottomSheetDialog.show();
    }
    private void setupSwipeToDismiss(View root) {
        NestedScrollView scrollView = root.findViewById(R.id.svSongPlayerContent);
        if (scrollView == null) return;

        View.OnTouchListener swipeListener = new View.OnTouchListener() {
            private float initialTouchY;
            private float dragStartY;
            private boolean isDragging = false;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialTouchY = event.getRawY();
                        isDragging = false;
                        break;

                    case MotionEvent.ACTION_MOVE:
                        float currentY = event.getRawY();
                        float totalDeltaY = currentY - initialTouchY;

                        // Start dragging if we are at the top and moving down
                        if (!isDragging && totalDeltaY > 10 && scrollView.getScrollY() <= 10) {
                            isDragging = true;
                            dragStartY = currentY; 
                            scrollView.requestDisallowInterceptTouchEvent(true);
                        }

                        if (isDragging) {
                            float dragDeltaY = currentY - dragStartY;
                            root.setTranslationY(Math.max(0, dragDeltaY));
                            return true;
                        }
                        break;

                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        if (isDragging) {
                            float finalDragDeltaY = event.getRawY() - dragStartY;
                            if (finalDragDeltaY > root.getHeight() / 6 || finalDragDeltaY > 300) {
                                // Dismiss
                                root.animate()
                                        .translationY(root.getHeight())
                                        .setDuration(250)
                                        .withEndAction(() -> {
                                            isDragging = false;
                                            if (isAdded() && getActivity() instanceof MainActivity) {
                                                ((MainActivity) getActivity()).externalClosePlayerImmediately();
                                            }
                                        })
                                        .start();
                            } else {
                                // Restore
                                root.animate()
                                        .translationY(0)
                                        .setDuration(200)
                                        .start();
                            }
                            isDragging = false;
                            scrollView.requestDisallowInterceptTouchEvent(false);
                            return true;
                        }
                        break;
                }
                return false;
            }
        };

        // Attach to both to ensure we catch the gesture
        scrollView.setOnTouchListener(swipeListener);
        root.setOnTouchListener(swipeListener);
    }
}
    