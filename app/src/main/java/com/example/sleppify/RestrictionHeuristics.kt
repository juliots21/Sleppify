package com.example.sleppify

import android.content.Context
import android.text.TextUtils
import android.util.Log

/**
 * Shared heuristics to determine if a track should be marked as permanently restricted.
 * Used by both background downloads and the foreground player.
 */
object RestrictionHeuristics {
    private const val TAG = "RestrictionHeuristics"
    const val MAX_AUTOMATIC_FAILURES = 3

    /**
     * Process a playback or download failure.
     * Returns true if the track was marked as permanently restricted as a result of this failure.
     */
    @JvmStatic
    fun processFailure(
        context: Context,
        videoId: String?,
        isNetworkError: Boolean,
        isExplicitlyRestricted: Boolean = false
    ): Boolean {
        if (TextUtils.isEmpty(videoId)) return false
        val safeId = videoId!!.trim()

        if (isExplicitlyRestricted) {
            OfflineRestrictionStore.markRestricted(context, safeId)
            OfflineRestrictionStore.clearAutomaticNewPipeFailures(context, safeId)
            Log.w(TAG, "track:explicit_restriction id=$safeId")
            return true
        }

        if (!isNetworkError) {
            val failures = OfflineRestrictionStore.incrementAutomaticNewPipeFailure(context, safeId)
            Log.w(TAG, "track:auto_fail id=$safeId failures=$failures/$MAX_AUTOMATIC_FAILURES")
            if (failures >= MAX_AUTOMATIC_FAILURES) {
                OfflineRestrictionStore.markRestricted(context, safeId)
                Log.w(TAG, "track:auto_blocked_after_failures id=$safeId")
                return true
            }
        }
        return false
    }

    @JvmStatic
    fun processSuccess(context: Context, videoId: String?) {
        if (TextUtils.isEmpty(videoId)) return
        val safeId = videoId!!.trim()
        OfflineRestrictionStore.unmarkRestricted(context, safeId)
        OfflineRestrictionStore.clearAutomaticNewPipeFailures(context, safeId)
    }
}
