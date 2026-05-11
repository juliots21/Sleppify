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
        }
    }
}
