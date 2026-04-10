package com.example.sleppify

import android.app.Application
import android.content.Intent

class SleppifyApp : Application() {
    @Volatile
    private var mediaProjectionPermissionData: Intent? = null

    override fun onCreate() {
        super.onCreate()
        // Pre-warm Glide
        try {
            com.bumptech.glide.Glide.get(this)
        } catch (e: Exception) {}
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
