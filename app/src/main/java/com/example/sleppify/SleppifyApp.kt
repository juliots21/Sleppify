package com.example.sleppify

import android.app.Application
import android.content.Intent
import android.util.Log
import java.util.concurrent.Executors

class SleppifyApp : Application() {
    @Volatile
    private var mediaProjectionPermissionData: Intent? = null

    override fun onCreate() {
        super.onCreate()
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
