package com.example.sleppify

import android.content.Context
import android.media.AudioAttributes
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource

/**
 * Thin adapter around ExoPlayer (Media3) that mimics the relevant subset of
 * [android.media.MediaPlayer] API used by [SongPlayerFragment].
 *
 * The goal is to swap MediaPlayer implementations with minimal changes to
 * the fragment. Only the listeners and APIs actually used are exposed.
 */
@UnstableApi
class ExoMediaPlayer {

    interface OnPreparedListener {
        fun onPrepared(mp: ExoMediaPlayer)
    }

    interface OnCompletionListener {
        fun onCompletion(mp: ExoMediaPlayer)
    }

    interface OnErrorListener {
        /**
         * @return true if the error has been handled
         */
        fun onError(mp: ExoMediaPlayer, what: Int, extra: Int): Boolean
    }

    companion object {
        private const val TAG = "ExoMediaPlayer"
    }

    private val appContext: Context
    private val mainHandler = Handler(Looper.getMainLooper())
    private var exoPlayer: ExoPlayer? = null

    private var preparedListener: OnPreparedListener? = null
    private var completionListener: OnCompletionListener? = null
    private var errorListener: OnErrorListener? = null

    private var pendingUri: Uri? = null
    private var pendingPath: String? = null
    private var pendingHeaders: Map<String, String>? = null
    private var pendingIsHttpSource: Boolean = false
    private var prepared: Boolean = false
    private var released: Boolean = false
    private var audioSessionId: Int = 0
    private var leftVolume = 1f
    private var rightVolume = 1f

    constructor(context: Context) {
        this.appContext = context.applicationContext
        val player = ExoPlayer.Builder(appContext).build()
        this.exoPlayer = player
        this.audioSessionId = player.audioSessionId
        player.addListener(playerListener)
    }

    /**
     * Constructor that uses a shared ExoPlayer instance to reduce startup latency.
     * @param context Application context
     * @param sharedExoPlayer Pre-initialized ExoPlayer instance
     */
    constructor(context: Context, sharedExoPlayer: ExoPlayer) {
        this.appContext = context.applicationContext
        this.exoPlayer = sharedExoPlayer
        this.audioSessionId = sharedExoPlayer.audioSessionId
        sharedExoPlayer.addListener(playerListener)
    }

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_READY && !prepared) {
                prepared = true
                preparedListener?.let { listener ->
                    mainHandler.post {
                        if (!released) {
                            listener.onPrepared(this@ExoMediaPlayer)
                        }
                    }
                }
            } else if (playbackState == Player.STATE_ENDED) {
                completionListener?.let { listener ->
                    mainHandler.post {
                        if (!released) {
                            listener.onCompletion(this@ExoMediaPlayer)
                        }
                    }
                }
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            Log.w(TAG, "onPlayerError: code=${error.errorCode} message=${error.message}")
            errorListener?.let { listener ->
                mainHandler.post {
                    if (!released) {
                        listener.onError(this@ExoMediaPlayer, error.errorCode, 0)
                    }
                }
            }
        }
    }

    fun setAudioAttributes(attrs: AudioAttributes) {
        if (released) return
        val player = exoPlayer ?: return
        // Map framework AudioAttributes to Media3
        val media3Attrs = androidx.media3.common.AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()
        player.setAudioAttributes(media3Attrs, false)
    }

    fun setOnPreparedListener(l: OnPreparedListener?) {
        this.preparedListener = l
    }

    fun setOnCompletionListener(l: OnCompletionListener?) {
        this.completionListener = l
    }

    fun setOnErrorListener(l: OnErrorListener?) {
        this.errorListener = l
    }

    fun setDataSource(context: Context, uri: Uri, headers: Map<String, String>?) {
        this.pendingUri = uri
        this.pendingPath = null
        this.pendingHeaders = headers?.let { HashMap(it) }
        val scheme = uri.scheme
        this.pendingIsHttpSource = "http".equals(scheme, ignoreCase = true) || "https".equals(scheme, ignoreCase = true)
    }

    fun setDataSource(path: String) {
        this.pendingPath = path
        this.pendingUri = null
        this.pendingHeaders = null
        this.pendingIsHttpSource = path.startsWith("http://") || path.startsWith("https://")
    }

    /**
     * Alias of [prepareAsync] — ExoPlayer always prepares asynchronously.
     * Callers that relied on MediaPlayer's blocking `prepare()` should
     * wait for [OnPreparedListener] before interacting.
     */
    fun prepare() {
        prepareAsync()
    }

    fun prepareAsync() {
        if (released) {
            throw IllegalStateException("ExoMediaPlayer released")
        }
        val player = exoPlayer ?: throw IllegalStateException("ExoMediaPlayer released")
        val uri = pendingUri ?: pendingPath?.let { Uri.parse(it) } ?: throw IllegalStateException("No data source set")

        val factory: DataSource.Factory = if (pendingIsHttpSource) {
            // Usar DefaultHttpDataSource (HttpURLConnection nativo de Android)
            // en vez de OkHttpDataSource — el TLS fingerprint de OkHttp es detectado
            // por el CDN de YouTube causando 403, mientras que HttpURLConnection
            // tiene un fingerprint consistente con el de una app Android real.
            val httpFactory = DefaultHttpDataSource.Factory()
                .setConnectTimeoutMs(15_000)
                .setReadTimeoutMs(15_000)
                .setAllowCrossProtocolRedirects(true)

            pendingHeaders?.let { headers ->
                if (headers.isNotEmpty()) {
                    httpFactory.setDefaultRequestProperties(headers)
                    Log.d(TAG, "prepareAsync: setting ${headers.size} headers on DefaultHttpDataSource: ${headers.keys}")
                }
            }
            httpFactory
        } else {
            DefaultDataSource.Factory(appContext)
        }

        val mediaItem = MediaItem.fromUri(uri)
        val source = ProgressiveMediaSource.Factory(factory)
            .createMediaSource(mediaItem)

        player.setMediaSource(source)
        player.volume = Math.max(leftVolume, rightVolume)
        player.prepare()
        prepared = false
    }

    fun start() {
        if (released) return
        val player = exoPlayer ?: return
        player.playWhenReady = true
        if (player.playbackState == Player.STATE_IDLE) {
            player.prepare()
        }
    }

    fun pause() {
        if (released) return
        exoPlayer?.playWhenReady = false
    }

    fun stop() {
        if (released) return
        exoPlayer?.stop()
    }

    fun isPlaying(): Boolean {
        if (released) return false
        val player = exoPlayer ?: return false
        return player.isPlaying || (player.playWhenReady &&
                player.playbackState != Player.STATE_IDLE &&
                player.playbackState != Player.STATE_ENDED)
    }

    fun seekTo(msec: Int) {
        if (released) return
        exoPlayer?.seekTo(Math.max(0L, msec.toLong()))
    }

    fun getCurrentPosition(): Int {
        if (released) return 0
        return (exoPlayer?.currentPosition?.coerceAtLeast(0L)?.toInt()) ?: 0
    }

    fun getDuration(): Int {
        if (released) return 0
        val d = exoPlayer?.duration ?: 0L
        if (d == C.TIME_UNSET || d < 0) return 0
        return d.toInt()
    }

    fun setVolume(left: Float, right: Float) {
        this.leftVolume = left
        this.rightVolume = right
        if (released) return
        exoPlayer?.volume = Math.max(left, right)
    }

    fun getAudioSessionId(): Int {
        if (released) return audioSessionId
        return exoPlayer?.audioSessionId ?: audioSessionId
    }

    fun release() {
        if (released) return
        released = true
        prepared = false
        preparedListener = null
        completionListener = null
        errorListener = null
        exoPlayer?.let { player ->
            try {
                player.removeListener(playerListener)
                player.stop()
                player.release()
            } catch (e: Exception) {
                Log.w(TAG, "release: exception", e)
            }
            exoPlayer = null
        }
    }
}
