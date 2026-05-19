package com.example.sleppify;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.media.MediaMetadataRetriever;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ProgressBar;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media3.ui.PlayerView;

import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Centralised video surface manager.
 * <p>
 * Owns a <b>single</b> {@link PlayerView} (SurfaceView) and physically reparents it
 * between the hero (full-player) and mini-player containers.  Because the same
 * SurfaceView is reused, ExoPlayer never rebuilds its video decoder pipeline —
 * eliminating all lag when opening/closing the full player.
 * <p>
 * Cover-art (for audio tracks and static-image videos) stays entirely in
 * {@link SongPlayerFragment} / {@link GlobalMiniPlayerController} and is not
 * touched by this class.
 */
@androidx.annotation.OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
public final class VideoSurfaceRouter {

    private static final String TAG = "VideoSurfaceRouter";
    private static final String PREFS_VIDEO_STATIC_CACHE = "video_static_cache";

    // ── References ─────────────────────────────────────────────────────
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService bgExecutor = Executors.newSingleThreadExecutor();

    @Nullable private FrameLayout heroContainer;
    @Nullable private FrameLayout miniContainer;
    @Nullable private ProgressBar heroSpinner;
    @Nullable private ProgressBar miniSpinner;
    @Nullable private ImageView heroArt;   // ivPlayerCover
    @Nullable private ImageView miniArt;   // ivGlobalMiniPlayerArt
    @Nullable private Context appContext;

    // The one and only PlayerView — created programmatically
    @Nullable private PlayerView playerView;

    // ── State ──────────────────────────────────────────────────────────
    private boolean videoActive = false;    // true when a real (non-static) video is playing
    private boolean detecting = false;      // true during async static detection
    private boolean playerVisible = true;   // true when full player is shown
    @Nullable private String activeVideoId;
    @Nullable private ExoMediaPlayer activePlayer;

    // Callback so SongPlayerFragment can react to detection results
    @Nullable private Callback callback;

    public interface Callback {
        void onVideoConfirmed();      // Real video — hide cover art, adjust hero layout
        void onVideoStatic();         // Static image — revert to cover art mode
    }

    // ── Init ───────────────────────────────────────────────────────────

    public void init(
            @NonNull Context context,
            @Nullable FrameLayout heroContainer,
            @Nullable FrameLayout miniContainer,
            @Nullable ProgressBar heroSpinner,
            @Nullable ProgressBar miniSpinner,
            @Nullable ImageView heroArt,
            @Nullable ImageView miniArt
    ) {
        this.appContext = context.getApplicationContext();
        this.heroContainer = heroContainer;
        this.miniContainer = miniContainer;
        this.heroSpinner = heroSpinner;
        this.miniSpinner = miniSpinner;
        this.heroArt = heroArt;
        this.miniArt = miniArt;

        // Create the single PlayerView programmatically
        if (playerView == null && appContext != null) {
            playerView = new PlayerView(appContext);
            playerView.setUseController(false);
            playerView.setBackgroundColor(Color.BLACK);
            playerView.setVisibility(View.GONE);
            // resize_mode = fit
            playerView.setResizeMode(androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT);
        }
    }

    public void setCallback(@Nullable Callback callback) {
        this.callback = callback;
    }

    // ── Track lifecycle ────────────────────────────────────────────────

    /**
     * Called when a new track starts playback.
     * If the source is a .mp4 file, starts static detection with overlay.
     * If not .mp4, clears any video state.
     */
    public void onTrackStarted(
            @NonNull ExoMediaPlayer player,
            @Nullable String filePath,
            @NonNull String videoId
    ) {
        // Clear previous state
        detachAndHide();

        if (filePath == null || !filePath.toLowerCase(Locale.ROOT).endsWith(".mp4")) {
            videoActive = false;
            detecting = false;
            activeVideoId = null;
            activePlayer = null;
            return;
        }

        activePlayer = player;
        activeVideoId = videoId;

        // Check cache first — no overlay needed if already known
        if (appContext != null) {
            SharedPreferences cache = appContext.getSharedPreferences(
                    PREFS_VIDEO_STATIC_CACHE, Context.MODE_PRIVATE);
            if (cache.contains(videoId)) {
                boolean isStatic = cache.getBoolean(videoId, true);
                if (isStatic) {
                    videoActive = false;
                    detecting = false;
                    activePlayer = null;
                    if (callback != null) callback.onVideoStatic();
                } else {
                    // Known real video — attach immediately, no overlay
                    videoActive = true;
                    detecting = false;
                    attachToCurrentContainer(player);
                    if (callback != null) callback.onVideoConfirmed();
                }
                return;
            }
        }

        // Unknown — show overlay + spinner, run async detection
        detecting = true;
        showOverlay();

        // Attach player to PlayerView immediately (it will render under the overlay)
        attachToCurrentContainer(player);

        // Run detection in background
        final String checkVideoId = videoId;
        final String checkPath = filePath;
        bgExecutor.execute(() -> {
            boolean isStatic = detectStatic(checkPath, checkVideoId);
            handler.post(() -> onDetectionComplete(checkVideoId, isStatic));
        });
    }

    /**
     * Called when the ExoMediaPlayer is being released.
     */
    public void onPlayerReleased() {
        detachAndHide();
        videoActive = false;
        detecting = false;
        activeVideoId = null;
        activePlayer = null;
    }

    // ── Player visibility ──────────────────────────────────────────────

    /**
     * Called when the full player becomes visible (onHiddenChanged(false)).
     * Reparents the PlayerView from mini to hero container.
     */
    public void onPlayerVisible() {
        playerVisible = true;
        if (!videoActive || playerView == null) return;

        // Reparent: mini → hero
        removeFromParent(playerView);
        if (heroContainer != null) {
            heroContainer.addView(playerView,
                    new FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT));
            playerView.setVisibility(View.VISIBLE);
        }
        // Hide hero art (video covers it)
        if (heroArt != null) heroArt.setVisibility(View.GONE);
        // Show mini art again (video left mini)
        if (miniArt != null) miniArt.setVisibility(View.VISIBLE);
    }

    /**
     * Called when the full player is hidden (onHiddenChanged(true)).
     * Reparents the PlayerView from hero to mini container.
     */
    public void onPlayerHidden() {
        playerVisible = false;
        if (!videoActive || playerView == null) return;

        // Reparent: hero → mini
        removeFromParent(playerView);
        if (miniContainer != null) {
            playerView.setLayoutParams(new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
            miniContainer.addView(playerView);
            playerView.setVisibility(View.VISIBLE);
        }
        // Hide mini art (video covers it)
        if (miniArt != null) miniArt.setVisibility(View.GONE);
        // Show hero art again
        if (heroArt != null) heroArt.setVisibility(View.VISIBLE);
    }

    // ── State queries ──────────────────────────────────────────────────

    public boolean isVideoActive() {
        return videoActive;
    }

    public boolean isDetecting() {
        return detecting;
    }

    // ── Internal ───────────────────────────────────────────────────────

    private void attachToCurrentContainer(@NonNull ExoMediaPlayer player) {
        if (playerView == null) return;

        removeFromParent(playerView);

        FrameLayout target = playerVisible ? heroContainer : miniContainer;
        if (target != null) {
            target.addView(playerView,
                    new FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT));
        }

        player.attachPlayerView(playerView);
        playerView.setVisibility(View.VISIBLE);

        // Hide artwork in the active container
        if (playerVisible) {
            if (heroArt != null) heroArt.setVisibility(View.GONE);
        } else {
            if (miniArt != null) miniArt.setVisibility(View.GONE);
        }
    }

    private void detachAndHide() {
        if (playerView == null) return;

        if (activePlayer != null) {
            activePlayer.detachPlayerView(playerView);
        }
        playerView.setVisibility(View.GONE);
        removeFromParent(playerView);

        // Restore artwork visibility and reset alpha to avoid stale transparent state
        if (heroArt != null) {
            heroArt.animate().cancel();
            heroArt.setAlpha(1f);
            heroArt.setVisibility(View.VISIBLE);
        }
        if (miniArt != null) {
            miniArt.animate().cancel();
            miniArt.setAlpha(1f);
            miniArt.setVisibility(View.VISIBLE);
        }

        hideOverlay();
    }

    private void removeFromParent(@NonNull View view) {
        ViewGroup parent = (ViewGroup) view.getParent();
        if (parent != null) {
            parent.removeView(view);
        }
    }

    private void showOverlay() {
        if (playerVisible) {
            if (heroSpinner != null) heroSpinner.setVisibility(View.VISIBLE);
        } else {
            if (miniSpinner != null) miniSpinner.setVisibility(View.VISIBLE);
        }
    }

    private void hideOverlay() {
        if (heroSpinner != null) heroSpinner.setVisibility(View.GONE);
        if (miniSpinner != null) miniSpinner.setVisibility(View.GONE);
    }

    private void onDetectionComplete(@NonNull String videoId, boolean isStatic) {
        detecting = false;
        hideOverlay();

        if (!TextUtils.equals(videoId, activeVideoId)) {
            // Stale detection — ignore
            return;
        }

        // Cache result
        if (appContext != null) {
            appContext.getSharedPreferences(PREFS_VIDEO_STATIC_CACHE, Context.MODE_PRIVATE)
                    .edit().putBoolean(videoId, isStatic).apply();
        }

        if (isStatic) {
            // Static image — remove PlayerView, let cover art show through
            videoActive = false;
            detachAndHide();
            if (callback != null) callback.onVideoStatic();
        } else {
            // Real video — PlayerView is already attached and visible
            videoActive = true;
            if (callback != null) callback.onVideoConfirmed();
        }
    }

    // ── Static detection (runs on background thread) ───────────────────

    private boolean detectStatic(@NonNull String filePath, @NonNull String videoId) {
        MediaMetadataRetriever retriever = null;
        try {
            retriever = new MediaMetadataRetriever();
            retriever.setDataSource(filePath);

            String durationStr = retriever.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_DURATION);
            long durationMs = 0;
            try { durationMs = Long.parseLong(durationStr); } catch (Exception ignored) {}

            // Sample 3 frames at 5%, 30%, 60% of duration
            long timeUs0, timeUs1, timeUs2;
            if (durationMs > 20_000) {
                timeUs0 = (long) (durationMs * 0.05) * 1000L;
                timeUs1 = (long) (durationMs * 0.30) * 1000L;
                timeUs2 = (long) (durationMs * 0.60) * 1000L;
            } else if (durationMs > 5_000) {
                timeUs0 = 1_000_000L;
                timeUs1 = (long) (durationMs * 0.40) * 1000L;
                timeUs2 = (long) (durationMs * 0.75) * 1000L;
            } else {
                timeUs0 = 0L;
                timeUs1 = Math.max(500_000L, (durationMs / 3) * 1000L);
                timeUs2 = Math.max(1_000_000L, (durationMs * 2 / 3) * 1000L);
            }

            Bitmap frame0 = retriever.getFrameAtTime(timeUs0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
            Bitmap frame1 = retriever.getFrameAtTime(timeUs1, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
            Bitmap frame2 = retriever.getFrameAtTime(timeUs2, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);

            if (frame0 != null && frame1 != null && frame2 != null) {
                Bitmap small0 = Bitmap.createScaledBitmap(frame0, 64, 64, true);
                Bitmap small1 = Bitmap.createScaledBitmap(frame1, 64, 64, true);
                Bitmap small2 = Bitmap.createScaledBitmap(frame2, 64, 64, true);

                int pixelCount = 64 * 64;
                long diff01 = 0, diff02 = 0;
                for (int y = 0; y < 64; y++) {
                    for (int x = 0; x < 64; x++) {
                        int p0 = small0.getPixel(x, y);
                        int p1 = small1.getPixel(x, y);
                        int p2 = small2.getPixel(x, y);
                        diff01 += Math.abs(((p0 >> 16) & 0xFF) - ((p1 >> 16) & 0xFF));
                        diff01 += Math.abs(((p0 >> 8) & 0xFF) - ((p1 >> 8) & 0xFF));
                        diff01 += Math.abs((p0 & 0xFF) - (p1 & 0xFF));
                        diff02 += Math.abs(((p0 >> 16) & 0xFF) - ((p2 >> 16) & 0xFF));
                        diff02 += Math.abs(((p0 >> 8) & 0xFF) - ((p2 >> 8) & 0xFF));
                        diff02 += Math.abs((p0 & 0xFF) - (p2 & 0xFF));
                    }
                }
                double avgDiff01 = diff01 / (double) (pixelCount * 3);
                double avgDiff02 = diff02 / (double) (pixelCount * 3);
                boolean isStatic = avgDiff01 < 1.0 && avgDiff02 < 1.0;

                Log.d(TAG, "detectStatic: videoId=" + videoId
                        + " avgDiff01=" + String.format(Locale.US, "%.2f", avgDiff01)
                        + " avgDiff02=" + String.format(Locale.US, "%.2f", avgDiff02)
                        + " isStatic=" + isStatic
                        + " duration=" + durationMs + "ms");

                small0.recycle(); small1.recycle(); small2.recycle();
                frame0.recycle(); frame1.recycle(); frame2.recycle();
                return isStatic;
            } else {
                Log.w(TAG, "detectStatic: null frames for videoId=" + videoId);
                if (frame0 != null) frame0.recycle();
                if (frame1 != null) frame1.recycle();
                if (frame2 != null) frame2.recycle();
                return false; // Assume real video
            }
        } catch (Exception e) {
            Log.w(TAG, "detectStatic: error for videoId=" + videoId, e);
            return false; // Assume real video
        } finally {
            if (retriever != null) {
                try { retriever.release(); } catch (Exception ignored) {}
            }
        }
    }
}
