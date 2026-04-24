package com.example.sleppify;

import android.content.Context;
import android.media.AudioAttributes;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.datasource.okhttp.OkHttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.exoplayer.source.ProgressiveMediaSource;
import androidx.media3.exoplayer.hls.HlsMediaSource;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;

/**
 * Thin adapter around ExoPlayer (Media3) that mimics the relevant subset of
 * {@link android.media.MediaPlayer} API used by {@link SongPlayerFragment}.
 *
 * <p>The goal is to swap MediaPlayer implementations with minimal changes to
 * the fragment. Only the listeners and APIs actually used are exposed.
 */
@UnstableApi
public class ExoMediaPlayer {

    public interface OnPreparedListener {
        void onPrepared(@NonNull ExoMediaPlayer mp);
    }

    public interface OnCompletionListener {
        void onCompletion(@NonNull ExoMediaPlayer mp);
    }

    public interface OnErrorListener {
        /**
         * @return true if the error has been handled
         */
        boolean onError(@NonNull ExoMediaPlayer mp, int what, int extra);
    }

    private static final String TAG = "ExoMediaPlayer";

    private final Context appContext;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private ExoPlayer exoPlayer;

    @Nullable private OnPreparedListener preparedListener;
    @Nullable private OnCompletionListener completionListener;
    @Nullable private OnErrorListener errorListener;

    @Nullable private Uri pendingUri;
    @Nullable private String pendingPath;
    @Nullable private Map<String, String> pendingHeaders;
    private boolean pendingIsHttpSource;
    private boolean prepared;
    private boolean released;
    private int audioSessionId;
    private float leftVolume = 1f;
    private float rightVolume = 1f;

    public ExoMediaPlayer(@NonNull Context context) {
        this.appContext = context.getApplicationContext();
        this.exoPlayer = new ExoPlayer.Builder(appContext).build();
        this.audioSessionId = this.exoPlayer.getAudioSessionId();
        this.exoPlayer.addListener(playerListener);
    }

    private final Player.Listener playerListener = new Player.Listener() {
        @Override
        public void onPlaybackStateChanged(int playbackState) {
            if (playbackState == Player.STATE_READY && !prepared) {
                prepared = true;
                if (preparedListener != null) {
                    mainHandler.post(() -> {
                        if (!released && preparedListener != null) {
                            preparedListener.onPrepared(ExoMediaPlayer.this);
                        }
                    });
                }
            } else if (playbackState == Player.STATE_ENDED) {
                if (completionListener != null) {
                    mainHandler.post(() -> {
                        if (!released && completionListener != null) {
                            completionListener.onCompletion(ExoMediaPlayer.this);
                        }
                    });
                }
            }
        }

        @Override
        public void onPlayerError(@NonNull PlaybackException error) {
            Log.w(TAG, "onPlayerError: code=" + error.errorCode + " message=" + error.getMessage());
            if (errorListener != null) {
                mainHandler.post(() -> {
                    if (!released && errorListener != null) {
                        errorListener.onError(ExoMediaPlayer.this, error.errorCode, 0);
                    }
                });
            }
        }
    };

    public void setAudioAttributes(@NonNull AudioAttributes attrs) {
        if (released || exoPlayer == null) return;
        // Map framework AudioAttributes to Media3
        androidx.media3.common.AudioAttributes media3Attrs = new androidx.media3.common.AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .build();
        exoPlayer.setAudioAttributes(media3Attrs, false);
    }

    public void setOnPreparedListener(@Nullable OnPreparedListener l) {
        this.preparedListener = l;
    }

    public void setOnCompletionListener(@Nullable OnCompletionListener l) {
        this.completionListener = l;
    }

    public void setOnErrorListener(@Nullable OnErrorListener l) {
        this.errorListener = l;
    }

    public void setDataSource(@NonNull Context context, @NonNull Uri uri, @Nullable Map<String, String> headers) {
        this.pendingUri = uri;
        this.pendingPath = null;
        this.pendingHeaders = headers != null ? new HashMap<>(headers) : null;
        String scheme = uri.getScheme();
        this.pendingIsHttpSource = "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
    }

    public void setDataSource(@NonNull String path) {
        this.pendingPath = path;
        this.pendingUri = null;
        this.pendingHeaders = null;
        this.pendingIsHttpSource = path.startsWith("http://") || path.startsWith("https://");
    }

    /**
     * Alias of {@link #prepareAsync()} — ExoPlayer always prepares asynchronously.
     * Callers that relied on MediaPlayer's blocking {@code prepare()} should
     * wait for {@link OnPreparedListener} before interacting.
     */
    public void prepare() {
        prepareAsync();
    }

    public void prepareAsync() {
        if (released || exoPlayer == null) {
            throw new IllegalStateException("ExoMediaPlayer released");
        }
        Uri uri = pendingUri != null ? pendingUri : (pendingPath != null ? Uri.parse(pendingPath) : null);
        if (uri == null) {
            throw new IllegalStateException("No data source set");
        }

        DataSource.Factory factory;
        if (pendingIsHttpSource) {
            OkHttpClient okHttpClient = new OkHttpClient.Builder()
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .readTimeout(15, TimeUnit.SECONDS)
                    .followRedirects(true)
                    .followSslRedirects(true)
                    .build();

            OkHttpDataSource.Factory httpFactory = new OkHttpDataSource.Factory(okHttpClient);

            if (pendingHeaders != null && !pendingHeaders.isEmpty()) {
                httpFactory.setDefaultRequestProperties(pendingHeaders);
            }

            factory = httpFactory;
        } else {
            factory = new DefaultDataSource.Factory(appContext);
        }

        MediaItem mediaItem = MediaItem.fromUri(uri);
        androidx.media3.exoplayer.source.MediaSource source = new ProgressiveMediaSource.Factory(factory)
                .createMediaSource(mediaItem);

        exoPlayer.setMediaSource(source);
        exoPlayer.setVolume(Math.max(leftVolume, rightVolume));
        exoPlayer.prepare();
        prepared = false;
    }

    public void start() {
        if (released || exoPlayer == null) return;
        exoPlayer.setPlayWhenReady(true);
        if (exoPlayer.getPlaybackState() == Player.STATE_IDLE) {
            exoPlayer.prepare();
        }
    }

    public void pause() {
        if (released || exoPlayer == null) return;
        exoPlayer.setPlayWhenReady(false);
    }

    public void stop() {
        if (released || exoPlayer == null) return;
        exoPlayer.stop();
    }

    public boolean isPlaying() {
        if (released || exoPlayer == null) return false;
        return exoPlayer.isPlaying()
                || (exoPlayer.getPlayWhenReady()
                    && exoPlayer.getPlaybackState() != Player.STATE_IDLE
                    && exoPlayer.getPlaybackState() != Player.STATE_ENDED);
    }

    public void seekTo(int msec) {
        if (released || exoPlayer == null) return;
        exoPlayer.seekTo(Math.max(0L, msec));
    }

    public int getCurrentPosition() {
        if (released || exoPlayer == null) return 0;
        return (int) Math.max(0L, exoPlayer.getCurrentPosition());
    }

    public int getDuration() {
        if (released || exoPlayer == null) return 0;
        long d = exoPlayer.getDuration();
        if (d == C.TIME_UNSET || d < 0) return 0;
        return (int) d;
    }

    public void setVolume(float left, float right) {
        this.leftVolume = left;
        this.rightVolume = right;
        if (released || exoPlayer == null) return;
        exoPlayer.setVolume(Math.max(left, right));
    }

    public int getAudioSessionId() {
        if (released || exoPlayer == null) return audioSessionId;
        return exoPlayer.getAudioSessionId();
    }

    public void release() {
        if (released) return;
        released = true;
        prepared = false;
        preparedListener = null;
        completionListener = null;
        errorListener = null;
        if (exoPlayer != null) {
            try {
                exoPlayer.removeListener(playerListener);
                exoPlayer.stop();
                exoPlayer.release();
            } catch (Exception e) {
                Log.w(TAG, "release: exception", e);
            }
            exoPlayer = null;
        }
    }
}
