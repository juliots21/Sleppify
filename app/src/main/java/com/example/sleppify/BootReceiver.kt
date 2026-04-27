package com.example.sleppify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (Intent.ACTION_BOOT_COMPLETED == action || "android.intent.action.QUICKBOOT_POWERON" == action) {
            Log.d("BootReceiver", "System boot completed. Starting AudioEffectsService.")
            AudioEffectsService.sendApply(context)
            
            // Check if TV mode is enabled, if so, start the UI too
            val localPrefs = context.getSharedPreferences("sleppify_local_config", Context.MODE_PRIVATE)
            if (localPrefs.getBoolean("tv_mode_enabled", false)) {
                Log.d("BootReceiver", "TV mode detected. Starting MainActivity.")
                val startIntent = Intent(context, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
                context.startActivity(startIntent)
            }
        }
    }
}
