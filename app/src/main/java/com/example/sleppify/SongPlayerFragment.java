package com.example.sleppify;

import android.Manifest;
import android.app.Activity;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.res.ColorStateList;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.net.Uri;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
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
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.SeekBar;
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

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import com.google.android.material.imageview.ShapeableImageView;
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants;
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer;
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.AbstractYouTubePlayerListener;
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.options.IFramePlayerOptions;
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.utils.YouTubePlayerUtils;
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views.YouTubePlayerView;

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
    private static final String TAG_PLAYLIST_DETAIL = "playlist_detail";
    private static final String TAG_MODULE_MUSIC = "module_music";
    private static final String MEDIA_NOTIFICATION_CHANNEL_ID = "sleppify_media_playback";
    private static final int MEDIA_NOTIFICATION_ID = 11031;
    private static final int REPEAT_MODE_OFF = 0;
    private static final int REPEAT_MODE_ALL = 1;
    private static final int REPEAT_MODE_ONE = 2;
    private static final int ENDED_FALLBACK_THRESHOLD_SECONDS = 2;
    private static final long AUTOPLAY_RECOVERY_DELAY_MS = 1400L;
    private static final long TRACK_ERROR_RETRY_DELAY_MS = 750L;
    private static final int MAX_TRACK_ERROR_RETRY = 1;
    private static final int MAX_PLAYER_ENGINE_RECOVERY_RETRY = 4;
    private static final int MAX_EMBED_RETRY_PER_TRACK = 1;
    private static final int COVER_TAPS_TO_UNLOCK_VIDEO_CONTROLS = 5;
    private static final long COVER_TAP_RESET_WINDOW_MS = 2000L;
    private static final int CONNECT_TIMEOUT_MS = 14000;
    private static final int READ_TIMEOUT_MS = 22000;
    private static final long SOURCE_PREPARE_TIMEOUT_MS = 30000L;
    private static final long PLAYBACK_BOOTSTRAP_GRACE_MS = 1800L;
    private static final int MAX_PLAYBACK_SOURCE_RETRY = 1;
    private static final long PLAYBACK_SOURCE_RETRY_DELAY_MS = 350L;
    private static final String STREAM_HTTP_USER_AGENT = "Sleppify-Stream/1.0";
    private static final String AUDIUS_API_BASE_URL = "https://discoveryprovider.audius.co/v1";
    private static final String AUDIUS_APP_NAME = "sleppify";
    private static final int AUDIUS_SEARCH_LIMIT = 6;
    private static final int PLAYER_HERO_DEFAULT_HEIGHT_DP = 370;
    private static final int PLAYER_HERO_MIN_HEIGHT_DP = 260;
    private static final int PLAYER_HERO_MAX_HEIGHT_DP = 560;

    private final List<PlayerTrack> tracks = new ArrayList<>();
    private final List<PlayerTrack> nextUpTracks = new ArrayList<>();
    private final List<PlayerTrack> originalQueueOrder = new ArrayList<>();

    private ShapeableImageView ivPlayerCover;
    private ImageView ivPlayerBackdrop;
    private FrameLayout flPlayerHero;
    private YouTubePlayerView youtubePlayerView;
    private TextView tvPlayerTitle;
    private TextView tvPlayerArtist;
    private TextView tvActionLikeCount;
    private TextView tvActionDislikeCount;
    private TextView tvActionCommentCount;
    private TextView tvCurrentTime;
    private TextView tvTotalTime;
    private SeekBar sbPlaybackProgress;
    private NestedScrollView svSongPlayerContent;
    private ImageButton btnShuffle;
    private ImageButton btnRepeat;
    private View vPlayerShuffleIndicator;
    private View vPlayerRepeatIndicator;
    private ImageButton btnPlayPause;
    private RecyclerView rvNextUp;
    private View actionLike;
    private View actionDislike;
    private View actionComments;

    private NextUpAdapter nextUpAdapter;
    @Nullable
    private ItemTouchHelper nextUpItemTouchHelper;

    @Nullable
    private OnBackPressedCallback backPressedCallback;

    @Nullable
    private YouTubePlayer youTubePlayer;
    private boolean youtubePlayerInitStarted;
    @Nullable
    private MediaPlayer localMediaPlayer;
    @Nullable
    private MediaSessionCompat mediaSession;
    @Nullable
    private SharedPreferences playerStatePrefs;
    @Nullable
    private SharedPreferences settingsPrefs;

    private boolean playerReady = false;
    private boolean userSeeking = false;

    private int currentIndex = 0;
    private boolean isPlaying = true;
    private int currentSeconds = 0;
    private int totalSeconds = 1;
    private int lastPersistedSecond = -1;
    private boolean pauseRequestedByUser = false;
    private boolean appInBackground = false;
    private long lastBackgroundResumeAttemptMs = 0L;
    @Nullable
    private ActivityResultLauncher<String> notificationPermissionLauncher;
    private boolean notificationPermissionRequested;
    private boolean shuffleEnabled = false;
    private int repeatMode = REPEAT_MODE_OFF;
    private boolean collapsingToMiniMode;
    private long lastHandledEndedAtMs = 0L;
    @NonNull
    private String lastHandledEndedVideoId = "";
    @NonNull
    private final Random random = new Random();
    @NonNull
    private String loadedVideoId = "";
    @NonNull
    private String returnTargetTag = TAG_PLAYLIST_DETAIL;
    private boolean usingOfflineSource = false;
    private boolean usingYoutubeFallbackSource = false;
    private final Handler localProgressHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService streamResolverExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService socialStatsExecutor = Executors.newSingleThreadExecutor();
    private final Map<String, SocialStats> socialStatsCache = new HashMap<>();
    @Nullable
    private Future<?> pendingStreamResolverFuture;
    @Nullable
    private Runnable sourcePrepareTimeoutRunnable;
    private boolean localSourcePreparing = false;
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
    private boolean videoModeEnabled = false;
    private boolean embedControlsUnlocked = false;
    private final Set<String> sessionEmbedBlockedVideoIds = new HashSet<>();
    private final Set<String> audiusFallbackAttemptedVideoIds = new HashSet<>();
    @NonNull
    private String pendingSocialStatsVideoId = "";
    @NonNull
    private String embedRetryVideoId = "";
    private int embedRetryCount = 0;
    @Nullable
    private CustomTarget<Bitmap> playerCoverTarget;
    @NonNull
    private String activeCoverVideoId = "";
    private final Runnable localProgressTicker = new Runnable() {
        @Override
        public void run() {
            if (!isAdded() || localMediaPlayer == null || !usingOfflineSource || userSeeking) {
                return;
            }

            try {
                int positionMs = Math.max(0, localMediaPlayer.getCurrentPosition());
                int durationMs = Math.max(1, localMediaPlayer.getDuration());

                currentSeconds = positionMs / 1000;
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

            localProgressHandler.postDelayed(this, 400L);
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
                        updateMediaNotification();
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
        setHostTopBarOverlayMode(true);

        ivPlayerCover = view.findViewById(R.id.ivPlayerCover);
        ivPlayerBackdrop = view.findViewById(R.id.ivPlayerBackdrop);
        flPlayerHero = view.findViewById(R.id.flPlayerHero);
        youtubePlayerView = view.findViewById(R.id.youtubePlayerView);
        if (youtubePlayerView != null) {
            youtubePlayerView.setVisibility(View.VISIBLE);
            youtubePlayerView.setAlpha(1f);
            youtubePlayerView.setClickable(false);
        }
        tvPlayerTitle = view.findViewById(R.id.tvPlayerTitle);
        tvPlayerArtist = view.findViewById(R.id.tvPlayerArtist);
        actionLike = view.findViewById(R.id.actionLike);
        actionDislike = view.findViewById(R.id.actionDislike);
        actionComments = view.findViewById(R.id.actionComments);
        tvActionLikeCount = view.findViewById(R.id.tvActionLikeCount);
        tvActionDislikeCount = view.findViewById(R.id.tvActionDislikeCount);
        tvActionCommentCount = view.findViewById(R.id.tvActionCommentCount);
        tvCurrentTime = view.findViewById(R.id.tvCurrentTime);
        tvTotalTime = view.findViewById(R.id.tvTotalTime);
        sbPlaybackProgress = view.findViewById(R.id.sbPlaybackProgress);
        svSongPlayerContent = view.findViewById(R.id.svSongPlayerContent);
        btnShuffle = view.findViewById(R.id.btnShuffle);
        btnRepeat = view.findViewById(R.id.btnRepeat);
        vPlayerShuffleIndicator = view.findViewById(R.id.vPlayerShuffleIndicator);
        vPlayerRepeatIndicator = view.findViewById(R.id.vPlayerRepeatIndicator);
        btnPlayPause = view.findViewById(R.id.btnPlayPause);
        rvNextUp = view.findViewById(R.id.rvNextUp);

        playerStatePrefs = requireContext().getSharedPreferences(PREFS_PLAYER_STATE, Activity.MODE_PRIVATE);
        settingsPrefs = requireContext().getSharedPreferences(CloudSyncManager.PREFS_SETTINGS, Activity.MODE_PRIVATE);
        refreshVideoModePreference();
        setupCoverTapUnlockGesture();
        setupSocialActions();
        updatePlayerSurfaceForSource();

        hydrateTracksFromArgs();
        setupMediaSession();

        rvNextUp.setLayoutManager(new LinearLayoutManager(requireContext()));
        nextUpAdapter = new NextUpAdapter(nextUpTracks, position -> {
            int selectedPosition = position;
            if (selectedPosition >= 0 && selectedPosition < tracks.size()) {
                int targetIndex = (currentIndex + selectedPosition + 1) % tracks.size();
                String targetVideoId = tracks.get(targetIndex).videoId;
                if (!TextUtils.equals(targetVideoId, loadedVideoId)) {
                    persistPositionForLoadedTrack();
                }
                if (!TextUtils.isEmpty(targetVideoId)) {
                    clearPersistedPositionFor(targetVideoId);
                }

                currentIndex = targetIndex;
                isPlaying = true;
                currentSeconds = 0;
                lastPersistedSecond = -1;
                bindCurrentTrack(false);
                playCurrentTrack();
            }
        }, holder -> {
            if (nextUpItemTouchHelper != null) {
                nextUpItemTouchHelper.startDrag(holder);
            }
        });
        rvNextUp.setAdapter(nextUpAdapter);

        ItemTouchHelper.SimpleCallback nextUpDragCallback = new ItemTouchHelper.SimpleCallback(
                ItemTouchHelper.UP | ItemTouchHelper.DOWN,
                0
        ) {
            private final float dragLiftElevation = 18f * requireContext().getResources().getDisplayMetrics().density;

            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
                if (nextUpAdapter == null) {
                    return false;
                }
                int from = viewHolder.getBindingAdapterPosition();
                int to = target.getBindingAdapterPosition();
                if (from == RecyclerView.NO_POSITION || to == RecyclerView.NO_POSITION) {
                    return false;
                }
                boolean moved = nextUpAdapter.moveItem(from, to);
                if (moved) {
                    applyNextUpOrderToQueue();
                }
                return moved;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                // Swipe is disabled for queue items.
            }

            @Override
            public boolean isLongPressDragEnabled() {
                return false;
            }

            @Override
            public boolean isItemViewSwipeEnabled() {
                return false;
            }

            @Override
            public void onSelectedChanged(@Nullable RecyclerView.ViewHolder viewHolder, int actionState) {
                super.onSelectedChanged(viewHolder, actionState);
                if (viewHolder == null || actionState != ItemTouchHelper.ACTION_STATE_DRAG) {
                    return;
                }

                View item = viewHolder.itemView;
                item.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK);
                item.setAlpha(1f);
                item.setScaleX(1f);
                item.setScaleY(1f);
                ViewCompat.setElevation(item, dragLiftElevation);
            }

            @Override
            public void onChildDraw(
                    @NonNull android.graphics.Canvas c,
                    @NonNull RecyclerView recyclerView,
                    @NonNull RecyclerView.ViewHolder viewHolder,
                    float dX,
                    float dY,
                    int actionState,
                    boolean isCurrentlyActive
            ) {
                super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);
                if (actionState != ItemTouchHelper.ACTION_STATE_DRAG) {
                    return;
                }

                View item = viewHolder.itemView;
                if (isCurrentlyActive) {
                    float normalized = Math.min(1f, Math.abs(dY) / Math.max(1f, recyclerView.getHeight() * 0.35f));
                    float targetRotation = Math.signum(dY) * (1.2f + (normalized * 1.6f));
                    item.setRotation(targetRotation);
                } else {
                    item.setRotation(0f);
                }
            }

            @Override
            public void clearView(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
                super.clearView(recyclerView, viewHolder);
                View item = viewHolder.itemView;
                item.setAlpha(1f);
                item.setScaleX(1f);
                item.setScaleY(1f);
                item.setRotation(0f);
                ViewCompat.setElevation(item, 0f);
                applyNextUpOrderToQueue();
                refreshNextUp();
            }
        };

        nextUpItemTouchHelper = new ItemTouchHelper(nextUpDragCallback);
        nextUpItemTouchHelper.attachToRecyclerView(rvNextUp);

        setupBackPressToMiniMode();
        ensureNotificationPermissionForPlayback();
        resetPlayerScrollToTop();

        currentIndex = Math.max(0, Math.min(currentIndex, tracks.size() - 1));
        bindCurrentTrack(true);
        playCurrentTrack();

        view.findViewById(R.id.btnPrev).setOnClickListener(v -> moveTrack(-1));
        view.findViewById(R.id.btnNext).setOnClickListener(v -> moveTrack(1));
        view.findViewById(R.id.tvViewAllQueue).setOnClickListener(v -> openQueuePlaylistDetail());
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
                if (usingOfflineSource && localMediaPlayer != null) {
                    try {
                        localMediaPlayer.seekTo(Math.max(0, currentSeconds) * 1000);
                    } catch (Exception ignored) {
                        shouldRecover = true;
                    }
                    if (isPlaying) {
                        try {
                            localMediaPlayer.start();
                            startLocalProgressTicker();
                        } catch (Exception ignored) {
                            shouldRecover = true;
                        }
                    }
                } else if (usingYoutubeFallbackSource && playerReady && youTubePlayer != null) {
                    try {
                        youTubePlayer.seekTo(currentSeconds);
                    } catch (Exception ignored) {
                        shouldRecover = true;
                    }
                    if (isPlaying) {
                        try {
                            youTubePlayer.play();
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
                updateMediaNotification();
                userSeeking = false;
            }
        });
    }

    @Override
    public void onStop() {
        persistPositionForLoadedTrack();
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
    public void onResume() {
        super.onResume();
        appInBackground = false;
        updateBackPressedCallbackEnabled(isHidden());
        refreshVideoModePreference();
        ensureNotificationPermissionForPlayback();
        resetPlayerScrollToTop();
        ensureActivePlaybackIfExpected("onResume");
        persistPlaybackSnapshot(false);
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        updateBackPressedCallbackEnabled(hidden);
        setHostTopBarOverlayMode(!hidden);
        if (hidden) {
            if (!(usingOfflineSource && localMediaPlayer != null && isPlaying)) {
                stopLocalProgressTicker();
            }
        } else {
            resetPlayerScrollToTop();
            ensureActivePlaybackIfExpected("onHiddenChanged-visible");
        }
    }

    private void ensureActivePlaybackIfExpected(@NonNull String reason) {
        if (!isPlaying) {
            return;
        }

        long now = SystemClock.elapsedRealtime();
        boolean bootstrapWindow = (now - lastPlaybackStartRequestAtMs) < PLAYBACK_BOOTSTRAP_GRACE_MS;

        if (usingOfflineSource && localMediaPlayer != null) {
            if (localSourcePreparing && bootstrapWindow) {
                Log.d(TAG, "ensureActivePlaybackIfExpected: waiting local prepare. reason=" + reason);
                return;
            }

            boolean alreadyPlaying = false;
            try {
                alreadyPlaying = localMediaPlayer.isPlaying();
            } catch (Exception ignored) {
            }

            if (alreadyPlaying) {
                startLocalProgressTicker();
                return;
            }

            try {
                localMediaPlayer.start();
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

        if (usingYoutubeFallbackSource) {
            if (playerReady && youTubePlayer != null) {
                try {
                    youTubePlayer.play();
                } catch (Exception e) {
                    if (bootstrapWindow) {
                        Log.d(TAG, "ensureActivePlaybackIfExpected: youtube still bootstrapping. reason=" + reason);
                        return;
                    }
                    Log.w(TAG, "ensureActivePlaybackIfExpected: youtube play failed, reloading track. reason=" + reason, e);
                    playCurrentTrack();
                }
                return;
            }

            if (youtubePlayerInitStarted && bootstrapWindow) {
                Log.d(TAG, "ensureActivePlaybackIfExpected: waiting youtube init. reason=" + reason);
                return;
            }
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
        if (usingOfflineSource && localMediaPlayer != null) {
            try {
                return localMediaPlayer.isPlaying();
            } catch (Exception ignored) {
                return false;
            }
        }
        if (usingYoutubeFallbackSource) {
            return playerReady && youTubePlayer != null;
        }
        return false;
    }

    @Override
    public void onDestroyView() {
        persistPositionForLoadedTrack();
        persistPlaybackSnapshot(true);
        cancelAutoplayRecovery();
        cancelPlaybackErrorRetry();
        cancelPendingStreamResolver();
        clearPlayerCoverRequest();
        resetPlayerHeroContainerHeight();
        stopLocalProgressTicker();
        releaseLocalMediaPlayer();
        setHostTopBarOverlayMode(false);
        releaseMediaSession();
        clearMediaNotification();
        if (backPressedCallback != null) {
            backPressedCallback.remove();
            backPressedCallback = null;
        }
        if (youtubePlayerView != null) {
            pauseYouTubePlaybackEngine("onDestroyView");
            youtubePlayerView.release();
            youtubePlayerView = null;
        }
        youTubePlayer = null;
        settingsPrefs = null;
        flPlayerHero = null;
        youtubePlayerInitStarted = false;
        usingYoutubeFallbackSource = false;
        playerReady = false;
        super.onDestroyView();
    }

    private void refreshVideoModePreference() {
        boolean enabled = settingsPrefs != null
                && settingsPrefs.getBoolean(CloudSyncManager.KEY_PLAYER_VIDEO_MODE_ENABLED, false);
        videoModeEnabled = enabled;
        if (!videoModeEnabled) {
            embedControlsUnlocked = false;
        }
        updatePlayerSurfaceForSource();
    }

    private void setupCoverTapUnlockGesture() {
        if (ivPlayerCover == null) {
            return;
        }

        ivPlayerCover.setClickable(true);
        ivPlayerCover.setFocusable(true);
        ivPlayerCover.setOnClickListener(v -> handleCoverTapUnlock());
    }

    private void handleCoverTapUnlock() {
        if (!usingYoutubeFallbackSource) {
            return;
        }

        long now = SystemClock.elapsedRealtime();
        if ((now - lastCoverTapAtMs) > COVER_TAP_RESET_WINDOW_MS) {
            coverTapCount = 0;
        }
        lastCoverTapAtMs = now;
        coverTapCount++;

        if (coverTapCount < COVER_TAPS_TO_UNLOCK_VIDEO_CONTROLS) {
            return;
        }

        coverTapCount = 0;
        if (!videoModeEnabled) {
            if (isAdded()) {
                
            }
            return;
        }

        if (!embedControlsUnlocked) {
            embedControlsUnlocked = true;
            updatePlayerSurfaceForSource();
            if (isAdded()) {
                
            }
        }
    }

    private void resetCoverTapUnlockState() {
        coverTapCount = 0;
        lastCoverTapAtMs = 0L;
        embedControlsUnlocked = false;
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

    private void setupYouTubePlayer() {
        if (youtubePlayerView == null) {
            return;
        }
        if (youtubePlayerInitStarted) {
            return;
        }
        youtubePlayerInitStarted = true;
        getLifecycle().addObserver(youtubePlayerView);

        youtubePlayerView.enableBackgroundPlayback(true);

        IFramePlayerOptions options = new IFramePlayerOptions.Builder(requireContext())
            .controls(1)
                .rel(0)
                .fullscreen(0)
                .build();

        youtubePlayerView.initialize(new AbstractYouTubePlayerListener() {
            @Override
            public void onReady(@NonNull YouTubePlayer initializedPlayer) {
                playerReady = true;
                youTubePlayer = initializedPlayer;
                if (!usingYoutubeFallbackSource) {
                    return;
                }
                if (tracks.isEmpty() || currentIndex < 0 || currentIndex >= tracks.size()) {
                    return;
                }
                playWithYouTubePlayer(tracks.get(currentIndex));
            }

            @Override
            public void onStateChange(@NonNull YouTubePlayer initializedPlayer, @NonNull PlayerConstants.PlayerState state) {
                if (!usingYoutubeFallbackSource) {
                    return;
                }
                if (state == PlayerConstants.PlayerState.ENDED) {
                    handleTrackEnded();
                    return;
                }
                if (state == PlayerConstants.PlayerState.PLAYING) {
                    cancelAutoplayRecovery();
                    resetPlaybackErrorState();
                    resetEmbedErrorAutoskipWindow();
                    playerEngineRecoveryAttempts = 0;
                    String currentVideoId = loadedVideoId;
                    if (TextUtils.isEmpty(currentVideoId)
                            && currentIndex >= 0
                            && currentIndex < tracks.size()) {
                        currentVideoId = tracks.get(currentIndex).videoId;
                    }
                    clearTrackRestrictionIfAny(currentVideoId);
                    isPlaying = true;
                    pauseRequestedByUser = false;
                    updatePlayPauseIcon();
                    updateMediaSessionState();
                    updateMediaNotification();
                    persistPlaybackSnapshot(false);
                    return;
                }
                if (state == PlayerConstants.PlayerState.PAUSED) {
                    if (!pauseRequestedByUser
                            && !usingOfflineSource
                            && totalSeconds > 1
                            && currentSeconds >= Math.max(0, totalSeconds - ENDED_FALLBACK_THRESHOLD_SECONDS)) {
                        handleTrackEnded();
                        return;
                    }

                    if (appInBackground && !pauseRequestedByUser) {
                        long now = SystemClock.elapsedRealtime();
                        if ((now - lastBackgroundResumeAttemptMs) > 1200L) {
                            lastBackgroundResumeAttemptMs = now;
                            initializedPlayer.play();
                            return;
                        }
                    }
                    isPlaying = false;
                    updatePlayPauseIcon();
                    persistPositionForLoadedTrack();
                    updateMediaSessionState();
                    updateMediaNotification();
                    persistPlaybackSnapshot(false);
                }
            }

            @Override
            public void onCurrentSecond(@NonNull YouTubePlayer initializedPlayer, float second) {
                if (!isAdded() || userSeeking || usingOfflineSource || !usingYoutubeFallbackSource) {
                    return;
                }

                int newCurrent = Math.max(0, Math.round(second));
                if (newCurrent == currentSeconds) {
                    return;
                }
                currentSeconds = newCurrent;
                tvCurrentTime.setText(formatSeconds(currentSeconds));

                int safeTotal = Math.max(1, totalSeconds);
                if (currentSeconds > safeTotal) {
                    safeTotal = currentSeconds + 1;
                }
                int progress = Math.round((currentSeconds / (float) safeTotal) * 1000f);
                sbPlaybackProgress.setProgress(Math.max(0, Math.min(1000, progress)));

                if (currentSeconds != lastPersistedSecond && currentSeconds % 2 == 0) {
                    lastPersistedSecond = currentSeconds;
                    persistPositionForLoadedTrack();
                    updateMediaSessionState();
                    persistPlaybackSnapshot(false);
                }
            }

            @Override
            public void onVideoDuration(@NonNull YouTubePlayer initializedPlayer, float duration) {
                if (!isAdded() || usingOfflineSource || !usingYoutubeFallbackSource) {
                    return;
                }

                int resolvedDuration = Math.max(1, Math.round(duration));
                totalSeconds = resolvedDuration;
                tvTotalTime.setText(formatSeconds(totalSeconds));

                if (currentSeconds > totalSeconds) {
                    currentSeconds = Math.max(0, totalSeconds - 1);
                }

                if (!userSeeking) {
                    int progress = Math.round((Math.max(0, currentSeconds) / (float) totalSeconds) * 1000f);
                    sbPlaybackProgress.setProgress(Math.max(0, Math.min(1000, progress)));
                }

                updateMediaSessionMetadata();
                updateMediaSessionState();
                updateMediaNotification();
            }

            @Override
            public void onError(@NonNull YouTubePlayer initializedPlayer, @NonNull PlayerConstants.PlayerError error) {
                if (!isAdded()) {
                    return;
                }
                if (!usingYoutubeFallbackSource) {
                    return;
                }
                handlePlaybackError(error);
            }
        }, true, options);
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
                if (usingOfflineSource && localMediaPlayer != null) {
                    try {
                        localMediaPlayer.pause();
                        stopLocalProgressTicker();
                    } catch (Exception ignored) {
                    }
                } else if (usingYoutubeFallbackSource && playerReady && youTubePlayer != null) {
                    try {
                        youTubePlayer.pause();
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
            public void onSeekTo(long pos) {
                currentSeconds = Math.max(0, (int) (pos / 1000L));
                if (usingOfflineSource && localMediaPlayer != null) {
                    try {
                        localMediaPlayer.seekTo(currentSeconds * 1000);
                    } catch (Exception ignored) {
                    }
                } else if (usingYoutubeFallbackSource && playerReady && youTubePlayer != null) {
                    try {
                        youTubePlayer.seekTo(currentSeconds);
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
                updateMediaNotification();
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
            resetEmbedErrorAutoskipWindow();
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
                updateMediaNotification();
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
        bindCurrentTrack(false);
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
        if (!OfflineRestrictionStore.isRestricted(requireContext(), videoId)) {
            return false;
        }
        // Con red permitimos reintento para limpiar falsos positivos.
        return !isNetworkAvailable();
    }

    private void clearTrackRestrictionIfAny(@Nullable String videoId) {
        if (!isAdded() || TextUtils.isEmpty(videoId)) {
            return;
        }
        OfflineRestrictionStore.unmarkRestricted(requireContext(), videoId);
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

            boolean hasOfflineLocal = OfflineAudioStore.hasOfflineAudio(requireContext(), candidate.videoId);
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

                if (playerReady && youTubePlayer != null) {
                    try {
                        youTubePlayer.play();
                    } catch (Exception ignored) {
                        playCurrentTrack();
                    }
                } else {
                    playCurrentTrack();
                }
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

    private void handlePlaybackError(@NonNull PlayerConstants.PlayerError error) {
        cancelAutoplayRecovery();

        String currentVideoId = loadedVideoId;
        if (TextUtils.isEmpty(currentVideoId)
                && currentIndex >= 0
                && currentIndex < tracks.size()) {
            currentVideoId = tracks.get(currentIndex).videoId;
        }

        if (TextUtils.isEmpty(currentVideoId)) {
            stopPlaybackAfterErrors("No se pudo reproducir este tema de YouTube.");
            return;
        }

        if (TextUtils.equals(currentVideoId, lastErroredVideoId)) {
            sameTrackErrorCount++;
        } else {
            lastErroredVideoId = currentVideoId;
            sameTrackErrorCount = 1;
        }

        Log.w(
                TAG,
                "PlaybackError error=" + error
                        + " videoId=" + currentVideoId
                        + " sameTrackCount=" + sameTrackErrorCount
                        + " engineRecoveryAttempts=" + playerEngineRecoveryAttempts
        );

        if (error == PlayerConstants.PlayerError.VIDEO_NOT_PLAYABLE_IN_EMBEDDED_PLAYER) {
            markTrackAsRestricted(currentVideoId);
            clearPersistedPositionFor(currentVideoId);

            if (consumeEmbedRetryForTrack(currentVideoId)) {
                currentSeconds = 0;
                lastPersistedSecond = -1;
                recoverYouTubePlayerEngine(true);
                return;
            }

            if (tryAutoSkipAfterEmbedRestriction(currentVideoId)) {
                return;
            }

            stopPlaybackAfterErrors("Este video no permite reproducción embebida en la app.");
            return;
        }

        if (error == PlayerConstants.PlayerError.UNKNOWN) {
            if (consumeEmbedRetryForTrack(currentVideoId)) {
                currentSeconds = 0;
                lastPersistedSecond = -1;
                recoverYouTubePlayerEngine(true);
                return;
            }

            stopPlaybackAfterErrors("No se pudo reproducir en el reproductor interno de YouTube.");
            return;
        }

        if (shouldRecoverPlayerEngine(error)
                && playerEngineRecoveryAttempts < MAX_PLAYER_ENGINE_RECOVERY_RETRY) {
            playerEngineRecoveryAttempts++;
            recoverYouTubePlayerEngine(playerEngineRecoveryAttempts > 1);
            return;
        }

        if (sameTrackErrorCount <= MAX_TRACK_ERROR_RETRY) {
            schedulePlaybackRetry(currentVideoId);
            return;
        }

        stopPlaybackAfterErrors(buildPlaybackErrorMessage(error));
    }

    private boolean shouldRecoverPlayerEngine(@NonNull PlayerConstants.PlayerError error) {
        return error == PlayerConstants.PlayerError.HTML_5_PLAYER;
    }

    private void recoverYouTubePlayerEngine() {
        recoverYouTubePlayerEngine(false);
    }

    private void recoverYouTubePlayerEngine(boolean forceHardRebuild) {
        cancelPlaybackErrorRetry();
        if (!isAdded() || youtubePlayerView == null || usingOfflineSource) {
            return;
        }

        if (forceHardRebuild) {
            if (!rebuildYouTubePlayerView()) {
                if (currentIndex >= 0 && currentIndex < tracks.size()) {
                    schedulePlaybackRetry(tracks.get(currentIndex).videoId);
                }
                return;
            }

            playerReady = false;
            youTubePlayer = null;
            setupYouTubePlayer();
            return;
        }

        if (!playerReady || youTubePlayer == null) {
            if (currentIndex >= 0 && currentIndex < tracks.size()) {
                schedulePlaybackRetry(tracks.get(currentIndex).videoId);
            }
            return;
        }

        if (currentIndex < 0 || currentIndex >= tracks.size()) {
            return;
        }

        PlayerTrack track = tracks.get(currentIndex);
        if (TextUtils.isEmpty(track.videoId)) {
            return;
        }

        float startAt = Math.max(0f, currentSeconds);
        try {
            if (isPlaying) {
                if (isResumed()) {
                    youTubePlayer.loadVideo(track.videoId, startAt);
                } else {
                    YouTubePlayerUtils.loadOrCueVideo(youTubePlayer, getLifecycle(), track.videoId, startAt);
                }
            } else {
                youTubePlayer.cueVideo(track.videoId, startAt);
            }
        } catch (Exception ignored) {
            schedulePlaybackRetry(track.videoId);
        }
    }

    private boolean rebuildYouTubePlayerView() {
        if (!isAdded() || youtubePlayerView == null) {
            return false;
        }

        ViewParent parent = youtubePlayerView.getParent();
        if (!(parent instanceof ViewGroup)) {
            Log.w(TAG, "No se pudo reconstruir YouTubePlayerView: parent invalido");
            return false;
        }

        ViewGroup container = (ViewGroup) parent;
        int childIndex = container.indexOfChild(youtubePlayerView);
        int viewId = youtubePlayerView.getId();
        ViewGroup.LayoutParams params = youtubePlayerView.getLayoutParams();

        try {
            youtubePlayerView.release();
        } catch (Exception ignored) {
        }

        container.removeView(youtubePlayerView);

        YouTubePlayerView freshPlayerView = new YouTubePlayerView(requireContext());
        freshPlayerView.setId(viewId);
        freshPlayerView.setEnableAutomaticInitialization(false);

        if (params == null) {
            params = new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
            );
        }

        if (childIndex < 0 || childIndex > container.getChildCount()) {
            childIndex = 0;
        }

        container.addView(freshPlayerView, childIndex, params);
        youtubePlayerView = freshPlayerView;
        youtubePlayerInitStarted = false;
        return true;
    }

    @NonNull
    private String buildPlaybackErrorMessage(@NonNull PlayerConstants.PlayerError error) {
        switch (error) {
            case VIDEO_NOT_FOUND:
                return "Este video ya no esta disponible en YouTube.";
            case VIDEO_NOT_PLAYABLE_IN_EMBEDDED_PLAYER:
                return "Ese video tiene una restriccion de YouTube para reproducirse dentro de la app.";
            case INVALID_PARAMETER_IN_REQUEST:
                return "No se pudo cargar este tema. Prueba con otra canción.";
            case HTML_5_PLAYER:
                return "Fallo el reproductor de YouTube. Intenta reproducir de nuevo.";
            case UNKNOWN:
            default:
                return "No se pudo reproducir este tema de YouTube.";
        }
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
    }

    private void stopPlaybackAfterErrors(@NonNull String message) {
        cancelPlaybackErrorRetry();
        isPlaying = false;
        pauseRequestedByUser = false;
        usingYoutubeFallbackSource = false;
        updatePlayerSurfaceForSource();
        Log.e(TAG, "Playback stopped after errors. videoId=" + loadedVideoId + " message=" + message);
        updatePlayPauseIcon();
        updateMediaSessionState();
        updateMediaNotification();
        persistPlaybackSnapshot(false);
        if (isAdded()) {
            
        }
    }

    private boolean tryAutoSkipAfterEmbedRestriction(@NonNull String blockedVideoId) {
        sessionEmbedBlockedVideoIds.add(blockedVideoId);

        if (tracks.size() <= 1) {
            return false;
        }

        int startIndex = currentIndex;
        for (int step = 1; step < tracks.size(); step++) {
            int candidateIndex = (startIndex + step) % tracks.size();
            PlayerTrack candidate = tracks.get(candidateIndex);
            if (candidate == null || TextUtils.isEmpty(candidate.videoId)) {
                continue;
            }
            if (sessionEmbedBlockedVideoIds.contains(candidate.videoId)) {
                continue;
            }

            currentIndex = candidateIndex;
            clearPersistedPositionFor(candidate.videoId);
            currentSeconds = 0;
            lastPersistedSecond = -1;
            isPlaying = true;
            bindCurrentTrack(true);
            playCurrentTrack();
            cancelAutoplayRecovery();
            syncMiniStateWithPlaylist();
            return true;
        }

        return false;
    }

    private void resetEmbedErrorAutoskipWindow() {
        sessionEmbedBlockedVideoIds.clear();
        resetEmbedRetryState();
    }

    private boolean consumeEmbedRetryForTrack(@NonNull String videoId) {
        if (!TextUtils.equals(embedRetryVideoId, videoId)) {
            embedRetryVideoId = videoId;
            embedRetryCount = 0;
        }

        if (embedRetryCount >= MAX_EMBED_RETRY_PER_TRACK) {
            return false;
        }

        embedRetryCount++;
        return true;
    }

    private void resetEmbedRetryState() {
        embedRetryVideoId = "";
        embedRetryCount = 0;
    }

    private void togglePlayback() {
        if (usingOfflineSource && localMediaPlayer != null) {
            if (isPlaying) {
                pauseRequestedByUser = true;
                try {
                    localMediaPlayer.pause();
                } catch (Exception ignored) {
                }
                stopLocalProgressTicker();
                isPlaying = false;
                persistPositionForLoadedTrack();
            } else {
                pauseRequestedByUser = false;
                isPlaying = true;
                try {
                    localMediaPlayer.start();
                    startLocalProgressTicker();
                } catch (Exception ignored) {
                    playCurrentTrack();
                }
            }

            updatePlayPauseIcon();
            updateMediaSessionState();
            updateMediaNotification();
            syncMiniStateWithPlaylist();
            persistPlaybackSnapshot(false);
            return;
        }

        if (usingYoutubeFallbackSource && playerReady && youTubePlayer != null) {
            if (isPlaying) {
                pauseRequestedByUser = true;
                isPlaying = false;
                try {
                    youTubePlayer.pause();
                } catch (Exception ignored) {
                }
                persistPositionForLoadedTrack();
            } else {
                pauseRequestedByUser = false;
                isPlaying = true;
                try {
                    youTubePlayer.play();
                } catch (Exception ignored) {
                    playCurrentTrack();
                }
            }

            updatePlayPauseIcon();
            updateMediaSessionState();
            updateMediaNotification();
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
            updateMediaNotification();
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
        shuffleEnabled = !shuffleEnabled;
        if (shuffleEnabled) {
            randomizeQueueFromCurrentTrack();
        } else {
            restoreOriginalQueueOrder();
        }
        refreshNextUp();
        persistPlaybackSnapshot(false);
        updatePlaybackModeButtons();
    }

    private void cycleRepeatMode() {
        if (repeatMode == REPEAT_MODE_OFF) {
            repeatMode = REPEAT_MODE_ALL;
        } else if (repeatMode == REPEAT_MODE_ALL) {
            repeatMode = REPEAT_MODE_ONE;
        } else {
            repeatMode = REPEAT_MODE_OFF;
        }
        updatePlaybackModeButtons();
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

        lastPlaybackStartRequestAtMs = SystemClock.elapsedRealtime();

        usingYoutubeFallbackSource = false;

        if (currentIndex < 0 || currentIndex >= tracks.size()) {
            currentIndex = 0;
        }

        PlayerTrack track = tracks.get(currentIndex);
        if (TextUtils.isEmpty(track.videoId)) {
            if (isAdded()) {
                
            }
            return;
        }

        String previousLoadedVideoId = loadedVideoId;
        if (!TextUtils.equals(previousLoadedVideoId, track.videoId)) {
            resetCoverTapUnlockState();
        }

        cancelPlaybackErrorRetry();
        if (hasPendingStreamResolution() && TextUtils.equals(loadedVideoId, track.videoId)) {
            Log.d(TAG, "playCurrentTrack: resolver already running for same videoId=" + track.videoId);
            return;
        }
        cancelPendingStreamResolver();

        loadedVideoId = track.videoId;
        long requestToken = ++activePlaybackRequestToken;

        boolean hasOfflineLocal = isAdded() && OfflineAudioStore.hasOfflineAudio(requireContext(), track.videoId);
        if (shouldSkipRestrictedTrack(track.videoId, hasOfflineLocal)) {
            Log.d(TAG, "playCurrentTrack: restricted track skipped in queue. videoId=" + track.videoId);
            if (skipRestrictedTrackInQueue()) {
                return;
            }
        }
        if (hasOfflineLocal) {
            Log.d(TAG, "playCurrentTrack: prioritizing local offline audio. videoId=" + track.videoId);
        } else if (isNetworkAvailable()) {
            Log.d(TAG, "playCurrentTrack: using embedded YouTube as primary online source. videoId=" + track.videoId);
            if (tryYouTubeFallbackForCurrentTrack("Modo online: reproductor de YouTube embebido.", false)) {
                return;
            }
        }

        List<String> directSources = buildDirectSourceCandidates(track.videoId);
        Log.d(TAG, "playCurrentTrack: videoId=" + track.videoId
            + " requestToken=" + requestToken
            + " directSources=" + directSources.size());
        attemptPlaybackFromSources(track, directSources, 0, requestToken, 0);
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

            if (isNetworkAvailable()
                    && tryYouTubeFallbackForCurrentTrack(
                    "No se encontro audio directo. Usando reproductor de YouTube embebido.",
                    false
            )) {
                return;
            }

            if (!isNetworkAvailable() && !OfflineAudioStore.hasOfflineAudio(requireContext(), track.videoId)) {
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
        pauseYouTubePlaybackEngine("switch_to_direct_source");
        stopLocalProgressTicker();
        releaseLocalMediaPlayer();
        usingOfflineSource = true;
        usingYoutubeFallbackSource = false;
        localSourcePreparing = true;
        updatePlayerSurfaceForSource();

        MediaPlayer player = new MediaPlayer();
        localMediaPlayer = player;
        player.setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build());

        player.setOnPreparedListener(mp -> {
            cancelSourcePrepareTimeout();
            localSourcePreparing = false;
            Log.d(TAG, "onPrepared: videoId=" + track.videoId + " token=" + requestToken);

            if (!isAdded()
                    || localMediaPlayer != mp
                    || requestToken != activePlaybackRequestToken
                    || !TextUtils.equals(track.videoId, loadedVideoId)) {
                Log.d(TAG, "onPrepared: released stale player for videoId=" + track.videoId
                        + " token=" + requestToken);
                releaseSingleMediaPlayer(mp);
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
            currentSeconds = Math.max(0, Math.min(currentSeconds, Math.max(0, totalSeconds - 1)));

            try {
                mp.seekTo(currentSeconds * 1000);
            } catch (Exception ignored) {
            }

            if (isPlaying) {
                try {
                    mp.start();
                    Log.d(TAG, "onPrepared: playback started videoId=" + track.videoId
                            + " durationSec=" + totalSeconds);
                    if (networkSource) {
                        clearTrackRestrictionIfAny(track.videoId);
                    }
                    startLocalProgressTicker();
                } catch (Exception startError) {
                    Log.e(TAG, "onPrepared: start failed for videoId=" + track.videoId, startError);
                    if (localMediaPlayer == mp) {
                        releaseLocalMediaPlayer();
                        usingOfflineSource = false;
                    } else {
                        releaseSingleMediaPlayer(mp);
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

            if (localMediaPlayer == mp) {
                stopLocalProgressTicker();
                releaseLocalMediaPlayer();
                usingOfflineSource = false;
            } else {
                releaseSingleMediaPlayer(mp);
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
        } catch (Exception e) {
            cancelSourcePrepareTimeout();
            localSourcePreparing = false;
            Log.e(TAG, "startMediaPlaybackFromSource: setDataSource/prepareAsync failed for source="
                    + maskUrlForLog(source), e);
            if (localMediaPlayer == player) {
                releaseLocalMediaPlayer();
                usingOfflineSource = false;
            } else {
                releaseSingleMediaPlayer(player);
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

    private void releaseSingleMediaPlayer(@Nullable MediaPlayer player) {
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
    private List<String> buildDirectSourceCandidates(@NonNull String videoId) {
        LinkedHashSet<String> orderedSources = new LinkedHashSet<>();

        if (isAdded() && OfflineAudioStore.hasOfflineAudio(requireContext(), videoId)) {
            File file = OfflineAudioStore.getExistingOfflineAudioFile(requireContext(), videoId);
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

    private void pauseYouTubePlaybackEngine(@NonNull String reason) {
        if (youTubePlayer == null) {
            return;
        }

        try {
            youTubePlayer.pause();
            Log.d(TAG, "pauseYouTubePlaybackEngine: paused reason=" + reason);
        } catch (Exception e) {
            Log.w(TAG, "pauseYouTubePlaybackEngine: pause failed reason=" + reason, e);
        }
    }

    private boolean tryYouTubeFallbackForCurrentTrack(@NonNull String reason) {
        return tryYouTubeFallbackForCurrentTrack(reason, false);
    }

    private boolean tryYouTubeFallbackForCurrentTrack(@NonNull String reason, boolean forceAttempt) {
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

        if (!forceAttempt
                && usingYoutubeFallbackSource
                && playerReady
                && youTubePlayer != null
                && TextUtils.equals(track.videoId, loadedVideoId)) {
            if (isPlaying) {
                try {
                    youTubePlayer.play();
                } catch (Exception ignored) {
                }
            }
            return true;
        }

        playerEngineRecoveryAttempts = 0;
        resetPlaybackErrorState();
        resetEmbedRetryState();
        Log.w(TAG, "tryYouTubeFallbackForCurrentTrack: starting embedded YouTube playback. reason="
                + reason + " videoId=" + track.videoId);

        cancelSourcePrepareTimeout();
        stopLocalProgressTicker();
        releaseLocalMediaPlayer();
        usingOfflineSource = false;
        usingYoutubeFallbackSource = true;
        updatePlayerSurfaceForSource();
        updatePlayPauseIcon();
        updateMediaSessionState();
        updateMediaNotification();
        syncMiniStateWithPlaylist();
        persistPlaybackSnapshot(false);

        playWithYouTubePlayer(track);
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

    private void playWithYouTubePlayer(@NonNull PlayerTrack track) {
        if (!isAdded()) {
            return;
        }
        if (youtubePlayerView == null) {
            Log.e(TAG, "playWithYouTubePlayer: youtubePlayerView is null");
            return;
        }

        if (!playerReady || youTubePlayer == null) {
            setupYouTubePlayer();
            return;
        }

        usingYoutubeFallbackSource = true;
        ensureProgressUiBeforeVideoLoad(track);
        updatePlayerSurfaceForSource();

        float startAt = Math.max(0f, currentSeconds);
        try {
            if (isPlaying) {
                // Cuando el usuario navega rapido fuera del modulo, evitamos quedar en "cue"
                // y forzamos carga/reproduccion para mantener el audio en segundo plano.
                boolean forceBackgroundPlayback = appInBackground || isHidden();
                if (isResumed() || forceBackgroundPlayback) {
                    youTubePlayer.loadVideo(track.videoId, startAt);
                } else {
                    YouTubePlayerUtils.loadOrCueVideo(youTubePlayer, getLifecycle(), track.videoId, startAt);
                }
            } else {
                youTubePlayer.cueVideo(track.videoId, startAt);
            }
            Log.d(TAG, "playWithYouTubePlayer: videoId=" + track.videoId + " startAt=" + startAt);
        } catch (Exception e) {
            Log.e(TAG, "playWithYouTubePlayer: failed to start fallback videoId=" + track.videoId, e);
        }
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
            @NonNull MediaPlayer player,
            @NonNull Runnable onFailure
    ) {
        cancelSourcePrepareTimeout();
        sourcePrepareTimeoutRunnable = () -> {
            if (!isAdded()) {
                return;
            }
            if (localMediaPlayer != player
                    || requestToken != activePlaybackRequestToken
                    || !TextUtils.equals(track.videoId, loadedVideoId)) {
                return;
            }

            Log.e(TAG, "scheduleSourcePrepareTimeout: prepare timeout reached for videoId=" + track.videoId
                    + " token=" + requestToken
                    + " timeoutMs=" + SOURCE_PREPARE_TIMEOUT_MS);
        localSourcePreparing = false;
            stopLocalProgressTicker();
            releaseLocalMediaPlayer();
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
        if (tryYouTubeFallbackForCurrentTrack(message)) {
            return;
        }

        pauseYouTubePlaybackEngine("markPlaybackUnavailable");
        usingYoutubeFallbackSource = false;
        Log.e(TAG, "markPlaybackUnavailable: " + message
            + " loadedVideoId=" + loadedVideoId
            + " activeToken=" + activePlaybackRequestToken);
        cancelSourcePrepareTimeout();
        stopLocalProgressTicker();
        releaseLocalMediaPlayer();
        usingOfflineSource = false;
        pauseRequestedByUser = true;
        isPlaying = false;
        updatePlayerSurfaceForSource();

        updatePlayPauseIcon();
        updateMediaSessionMetadata();
        updateMediaSessionState();
        updateMediaNotification();
        syncMiniStateWithPlaylist();
        persistPlaybackSnapshot(false);

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

    private void startLocalProgressTicker() {
        localProgressHandler.removeCallbacks(localProgressTicker);
        localProgressHandler.post(localProgressTicker);
    }

    private void stopLocalProgressTicker() {
        localProgressHandler.removeCallbacks(localProgressTicker);
    }

    private void releaseLocalMediaPlayer() {
        cancelSourcePrepareTimeout();
        localSourcePreparing = false;
        if (localMediaPlayer == null) {
            return;
        }
        try {
            localMediaPlayer.stop();
        } catch (Exception ignored) {
        }
        try {
            localMediaPlayer.release();
        } catch (Exception ignored) {
        }
        localMediaPlayer = null;
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

    private void resetPlayerHeroContainerHeight() {
        setPlayerHeroContainerHeight(dpToPx(PLAYER_HERO_DEFAULT_HEIGHT_DP));
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

    private void loadPlayerCover(@NonNull PlayerTrack track) {
        String imageUrl = track.imageUrl == null ? "" : track.imageUrl.trim();
        if (TextUtils.isEmpty(imageUrl)) {
            clearPlayerCoverRequest();
            activeCoverVideoId = "";
            resetPlayerHeroContainerHeight();
            ivPlayerCover.setImageResource(R.drawable.ic_music);
            return;
        }

        String requestVideoId = track.videoId == null ? "" : track.videoId;
        activeCoverVideoId = requestVideoId;
        clearPlayerCoverRequest();

        playerCoverTarget = new CustomTarget<Bitmap>() {
            @Override
            public void onResourceReady(@NonNull Bitmap resource, @Nullable Transition<? super Bitmap> transition) {
                if (!isAdded() || !TextUtils.equals(activeCoverVideoId, requestVideoId)) {
                    return;
                }
                ivPlayerCover.setImageBitmap(resource);
                adjustPlayerHeroForCover(resource);
            }

            @Override
            public void onLoadCleared(@Nullable Drawable placeholder) {
                if (!isAdded()) {
                    return;
                }
                if (placeholder != null) {
                    ivPlayerCover.setImageDrawable(placeholder);
                } else {
                    ivPlayerCover.setImageResource(R.drawable.ic_music);
                }
            }

            @Override
            public void onLoadFailed(@Nullable Drawable errorDrawable) {
                if (!isAdded() || !TextUtils.equals(activeCoverVideoId, requestVideoId)) {
                    return;
                }
                resetPlayerHeroContainerHeight();
                if (errorDrawable != null) {
                    ivPlayerCover.setImageDrawable(errorDrawable);
                } else {
                    ivPlayerCover.setImageResource(R.drawable.ic_music);
                }
            }
        };

        Glide.with(this)
                .asBitmap()
                .load(imageUrl)
                .placeholder(R.drawable.ic_music)
                .error(R.drawable.ic_music)
                .into(playerCoverTarget);
    }

    @Override
    public void onDestroy() {
        cancelPendingStreamResolver();
        streamResolverExecutor.shutdownNow();
        socialStatsExecutor.shutdownNow();
        super.onDestroy();
    }

    private void bindCurrentTrack(boolean resetProgress) {
        if (tracks.isEmpty()) {
            return;
        }
        if (currentIndex < 0 || currentIndex >= tracks.size()) {
            currentIndex = 0;
        }

        PlayerTrack track = tracks.get(currentIndex);
        tvPlayerTitle.setText(track.title);
        tvPlayerArtist.setText(track.artist);
        refreshSocialActionsForCurrentTrack(track);

        totalSeconds = Math.max(1, parseDurationSeconds(track.duration));
        if (resetProgress) {
            currentSeconds = getPersistedPositionFor(track.videoId, totalSeconds);
        } else {
            currentSeconds = Math.min(currentSeconds, totalSeconds);
        }

        tvCurrentTime.setText(formatSeconds(currentSeconds));
        tvTotalTime.setText(TextUtils.isEmpty(track.duration) ? formatSeconds(totalSeconds) : track.duration);

        int progress = Math.round((currentSeconds / (float) Math.max(1, totalSeconds)) * 1000f);
        sbPlaybackProgress.setProgress(Math.max(0, Math.min(1000, progress)));
        updatePlayPauseIcon();

        loadPlayerCover(track);

        if (!TextUtils.isEmpty(track.imageUrl)) {
            if (ivPlayerBackdrop != null) {
                Glide.with(this)
                        .load(track.imageUrl)
                    .centerCrop()
                        .placeholder(R.drawable.ic_music)
                        .error(R.drawable.ic_music)
                        .into(ivPlayerBackdrop);
                applyPlayerBackdropBlur();
            }
        } else if (ivPlayerBackdrop != null) {
            ivPlayerBackdrop.setImageResource(R.drawable.ic_music);
            applyPlayerBackdropBlur();
        }

        updateMediaSessionMetadata();
        updateMediaSessionState();
        updateMediaNotification();
        refreshNextUp();
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
    }

    private void refreshSocialActionsForCurrentTrack(@Nullable PlayerTrack track) {
        if (track == null || TextUtils.isEmpty(track.videoId)) {
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
            if (cached == null) {
                applySocialStatsToUi(SocialStats.unavailable());
            }
            return;
        }

        String requestVideoId = track.videoId;
        SocialStats cachedSnapshot = cached;
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

        resetEmbedErrorAutoskipWindow();
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
        pauseYouTubePlaybackEngine("session_exit");
        persistPositionForLoadedTrack();
        stopLocalProgressTicker();
        releaseLocalMediaPlayer();
        isPlaying = false;
        updatePlayPauseIcon();
        updateMediaSessionState();
        clearMediaNotification();
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
        boolean sameAsLoaded = !TextUtils.isEmpty(targetVideoId)
                && TextUtils.equals(targetVideoId, loadedVideoId);

        if (!sameAsLoaded) {
            persistPositionForLoadedTrack();
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
        loadedVideoId = "";
        lastPersistedSecond = -1;
        resetEmbedErrorAutoskipWindow();

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

        bindCurrentTrack(!startFromBeginning);
        playCurrentTrack();
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
        if (youtubePlayerView == null || ivPlayerCover == null) {
            return;
        }

        if (usingYoutubeFallbackSource) {
            boolean showVideoSurface = videoModeEnabled && embedControlsUnlocked;
            youtubePlayerView.setVisibility(View.VISIBLE);
            youtubePlayerView.setAlpha(showVideoSurface ? 1f : 0.01f);
            youtubePlayerView.setClickable(showVideoSurface);
            ivPlayerCover.setVisibility(showVideoSurface ? View.INVISIBLE : View.VISIBLE);
            ivPlayerCover.setClickable(!showVideoSurface);
            if (ivPlayerBackdrop != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                ivPlayerBackdrop.setRenderEffect(RenderEffect.createBlurEffect(8f, 8f, Shader.TileMode.CLAMP));
                ivPlayerBackdrop.setAlpha(0.52f);
            }
            return;
        }

        youtubePlayerView.setVisibility(View.INVISIBLE);
        youtubePlayerView.setAlpha(1f);
        youtubePlayerView.setClickable(false);
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
        if (animate) {
            transaction.setCustomAnimations(0, R.anim.player_screen_exit);
        }

        if (target != null && target != this && target.isAdded()) {
            transaction
                    .hide(this)
                    .show(target);
        } else {
            transaction.remove(this);
        }

        transaction.runOnCommit(() -> collapsingToMiniMode = false);
        transaction.commit();
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

    private void setHostTopBarOverlayMode(boolean enabled) {
        Activity activity = getActivity();
        if (activity == null) {
            return;
        }

        View topBar = activity.findViewById(R.id.topAppBar);
        if (topBar != null) {
            topBar.setVisibility(View.VISIBLE);
            topBar.setBackgroundColor(ContextCompat.getColor(
                    activity,
                    R.color.surface_dark
            ));
            float zOffset = enabled ? 24f * activity.getResources().getDisplayMetrics().density : 0f;
            topBar.setTranslationZ(zOffset);
            topBar.bringToFront();
        }

        View fragmentContainer = activity.findViewById(R.id.fragmentContainer);
        if (fragmentContainer != null && fragmentContainer.getLayoutParams() instanceof ConstraintLayout.LayoutParams) {
            ConstraintLayout.LayoutParams params = (ConstraintLayout.LayoutParams) fragmentContainer.getLayoutParams();
            if (enabled) {
                params.topToTop = ConstraintLayout.LayoutParams.PARENT_ID;
                params.topToBottom = ConstraintLayout.LayoutParams.UNSET;
            } else {
                params.topToTop = ConstraintLayout.LayoutParams.UNSET;
                params.topToBottom = R.id.topAppBar;
            }
            fragmentContainer.setLayoutParams(params);
            if (fragmentContainer.getParent() instanceof View) {
                ((View) fragmentContainer.getParent()).requestLayout();
            }
        }
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
        if (saved < 0) {
            return 0;
        }
        return Math.min(Math.max(maxSeconds - 1, 0), saved);
    }

    private void persistPositionForLoadedTrack() {
        if (playerStatePrefs == null || TextUtils.isEmpty(loadedVideoId)) {
            return;
        }
        int safeMax = Math.max(1, totalSeconds);
        int safeCurrent = Math.max(0, Math.min(safeMax - 1, currentSeconds));
        playerStatePrefs.edit().putInt(PREF_PLAYBACK_POS_PREFIX + loadedVideoId, safeCurrent).apply();
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
    }

    private void updateMediaSessionMetadata() {
        if (mediaSession == null || tracks.isEmpty() || currentIndex < 0 || currentIndex >= tracks.size()) {
            return;
        }

        PlayerTrack track = tracks.get(currentIndex);
        MediaMetadataCompat metadata = new MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, track.title)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, track.artist)
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, Math.max(1, totalSeconds) * 1000L)
                .build();
        mediaSession.setMetadata(metadata);
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
        if (!isAdded() || mediaSession == null || tracks.isEmpty() || currentIndex < 0 || currentIndex >= tracks.size()) {
            return;
        }
        if (!canPostNotifications()) {
            return;
        }

        PlayerTrack track = tracks.get(currentIndex);

        PendingIntent prevIntent = androidx.media.session.MediaButtonReceiver.buildMediaButtonPendingIntent(
                requireContext(), PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS);
        PendingIntent playPauseIntent = androidx.media.session.MediaButtonReceiver.buildMediaButtonPendingIntent(
                requireContext(), isPlaying ? PlaybackStateCompat.ACTION_PAUSE : PlaybackStateCompat.ACTION_PLAY);
        PendingIntent nextIntent = androidx.media.session.MediaButtonReceiver.buildMediaButtonPendingIntent(
                requireContext(), PlaybackStateCompat.ACTION_SKIP_TO_NEXT);

        Intent openAppIntent = new Intent(requireContext(), MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        int pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            pendingFlags |= PendingIntent.FLAG_IMMUTABLE;
        }
        PendingIntent contentIntent = PendingIntent.getActivity(requireContext(), 8701, openAppIntent, pendingFlags);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(requireContext(), MEDIA_NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_music)
                .setContentTitle(track.title)
                .setContentText(track.artist)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setContentIntent(contentIntent)
                .setOnlyAlertOnce(true)
                .setOngoing(isPlaying)
                .setPriority(NotificationCompat.PRIORITY_LOW)
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

        NotificationManagerCompat.from(requireContext()).notify(MEDIA_NOTIFICATION_ID, builder.build());
    }

    private void clearMediaNotification() {
        if (!isAdded()) {
            return;
        }
        NotificationManagerCompat.from(requireContext()).cancel(MEDIA_NOTIFICATION_ID);
    }

    private void ensureNotificationPermissionForPlayback() {
        if (!isAdded() || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return;
        }
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED) {
            return;
        }
        if (notificationPermissionRequested || notificationPermissionLauncher == null) {
            return;
        }

        notificationPermissionRequested = true;
        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
    }

    private boolean canPostNotifications() {
        if (!isAdded()) {
            return false;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS)
                    == PackageManager.PERMISSION_GRANTED;
        }
        return NotificationManagerCompat.from(requireContext()).areNotificationsEnabled();
    }

    private void ensureMediaNotificationChannel() {
        if (!isAdded() || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }

        NotificationManager manager = requireContext().getSystemService(NotificationManager.class);
        if (manager == null) {
            return;
        }

        NotificationChannel channel = manager.getNotificationChannel(MEDIA_NOTIFICATION_CHANNEL_ID);
        if (channel != null) {
            return;
        }

        NotificationChannel newChannel = new NotificationChannel(
                MEDIA_NOTIFICATION_CHANNEL_ID,
                "Reproduccion multimedia",
                NotificationManager.IMPORTANCE_LOW
        );
        newChannel.setDescription("Controles de reproduccion de Sleppify");
        manager.createNotificationChannel(newChannel);
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
        if (!isAdded() || tracks.isEmpty()) {
            return;
        }

        List<PlaybackHistoryStore.QueueTrack> queue = new ArrayList<>(tracks.size());
        for (PlayerTrack track : tracks) {
            queue.add(new PlaybackHistoryStore.QueueTrack(
                    track.videoId,
                    track.title,
                    track.artist,
                    track.duration,
                    track.imageUrl
            ));
        }

        PlaybackHistoryStore.save(
                requireContext(),
                queue,
                Math.max(0, Math.min(currentIndex, Math.max(0, tracks.size() - 1))),
                Math.max(0, currentSeconds),
                Math.max(1, totalSeconds),
            forcePaused ? false : isEffectivePlaying()
        );
    }

    private void refreshNextUp() {
        nextUpTracks.clear();
        if (nextUpAdapter == null) {
            return;
        }
        if (tracks.size() <= 1) {
            nextUpAdapter.notifyDataSetChanged();
            return;
        }

        for (int i = 1; i <= (tracks.size() - 1); i++) {
            int idx = (currentIndex + i) % tracks.size();
            nextUpTracks.add(tracks.get(idx));
        }
        nextUpAdapter.notifyDataSetChanged();
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
            return new SocialStats("0", "0", "0", true);
        }

        @NonNull
        static SocialStats unavailable() {
            return new SocialStats("0", "0", "0", true);
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
            holder.tvNextUpArtist.setText(item.artist);

            if (!TextUtils.isEmpty(item.imageUrl)) {
                Glide.with(holder.itemView)
                        .load(item.imageUrl)
                        .centerCrop()
                        .placeholder(R.drawable.ic_music)
                        .error(R.drawable.ic_music)
                        .into(holder.ivNextUpArt);
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
            return items.size();
        }

        static final class NextUpViewHolder extends RecyclerView.ViewHolder {
            final ShapeableImageView ivNextUpArt;
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
}

