package com.example.sleppify

import android.app.Application
import android.content.Intent
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import java.util.concurrent.Executors

class SleppifyApp : Application() {
    @Volatile
    private var mediaProjectionPermissionData: Intent? = null

    override fun onCreate() {
        super.onCreate()
        
        try {
            com.google.android.gms.security.ProviderInstaller.installIfNeeded(this)
            Log.d("SleppifyApp", "ProviderInstaller succeeded")
        } catch (e: Exception) {
            Log.w("SleppifyApp", "ProviderInstaller failed", e)
        }

        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        // Pre-warm Glide
        try {
            com.bumptech.glide.Glide.get(this)
        } catch (e: Exception) {}
        
        // Restore mono audio setting before ExoPlayer init
        val settingsPrefs = getSharedPreferences(CloudSyncManager.PREFS_SETTINGS, MODE_PRIVATE)
        MonoAudioProcessor.enabled = settingsPrefs.getBoolean(CloudSyncManager.KEY_MONO_AUDIO, false)

        // Pre-initialize ExoPlayer to reduce playback startup latency
        ExoPlayerManager.initialize(this)

        // Pre-initialize NewPipeExtractor on a background thread so the first
        // stream resolution doesn't pay the cold-start penalty (~500-800ms).
        Executors.newSingleThreadExecutor().execute {
            try {
                org.schabi.newpipe.extractor.NewPipe.init(NewPipeHttpDownloader.getInstance())
                Log.d("SleppifyApp", "NewPipeExtractor pre-initialized successfully")
            } catch (e: Exception) {
                Log.w("SleppifyApp", "NewPipeExtractor pre-init failed (will retry lazily)", e)
            }
        }
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
