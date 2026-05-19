package com.example.sleppify

import android.app.Application
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import java.util.concurrent.Executors

class SleppifyApp : Application() {
    @Volatile
    private var mediaProjectionPermissionData: Intent? = null

    @Volatile
    private var heavyInitDone = false

    override fun onCreate() {
        super.onCreate()

        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)

        // Restore mono audio setting (cheap, no I/O)
        val settingsPrefs = getSharedPreferences(CloudSyncManager.PREFS_SETTINGS, MODE_PRIVATE)
        MonoAudioProcessor.enabled = settingsPrefs.getBoolean(CloudSyncManager.KEY_MONO_AUDIO, false)

        // Only run heavy initialization if the user already has a YouTube Music session.
        // On first install (no session), defer until after successful web login.
        if (hasExistingSession()) {
            performHeavyInit()
        }
    }

    /**
     * Performs all heavy initialization: ProviderInstaller, Glide, ExoPlayer, NewPipe.
     * Called immediately on startup if session exists, or after first login otherwise.
     * Safe to call multiple times — guards with [heavyInitDone].
     */
    fun performDeferredInit() {
        performHeavyInit()
    }

    private fun performHeavyInit() {
        if (heavyInitDone) return
        synchronized(this) {
            if (heavyInitDone) return
            heavyInitDone = true
        }

        try {
            com.google.android.gms.security.ProviderInstaller.installIfNeeded(this)
        } catch (e: Exception) {
            Log.w("SleppifyApp", "ProviderInstaller failed", e)
        }

        // Pre-warm Glide
        try {
            com.bumptech.glide.Glide.get(this)
        } catch (e: Exception) {}

        // Pre-initialize ExoPlayer to reduce playback startup latency
        ExoPlayerManager.initialize(this)

        // Pre-initialize NewPipeExtractor on a background thread so the first
        // stream resolution doesn't pay the cold-start penalty (~500-800ms).
        Executors.newSingleThreadExecutor().execute {
            try {
                org.schabi.newpipe.extractor.NewPipe.init(NewPipeHttpDownloader.getInstance())
            } catch (e: Exception) {
                Log.w("SleppifyApp", "NewPipeExtractor pre-init failed (will retry lazily)", e)
            }
        }
    }

    private fun hasExistingSession(): Boolean {
        val prefs = getSharedPreferences("player_state", MODE_PRIVATE)
        val cookie = prefs.getString("stream_last_youtube_web_cookie", "") ?: ""
        return cookie.trim().isNotEmpty()
    }

    @Synchronized
    fun setMediaProjectionPermissionData(data: Intent?) {
        mediaProjectionPermissionData = data?.let { Intent(it) }
    }

    @Synchronized
    fun getMediaProjectionPermissionData(): Intent? {
        return mediaProjectionPermissionData?.let { Intent(it) }
    }

    @Synchronized
    fun hasMediaProjectionPermissionData(): Boolean {
        return mediaProjectionPermissionData != null
    }
}
