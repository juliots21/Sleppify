package com.example.sleppify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class EqBootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) return

        val prefs = context.getSharedPreferences(AudioEffectsService.PREFS_NAME, Context.MODE_PRIVATE)
        val enabled = prefs.getBoolean(AudioEffectsService.KEY_ENABLED, false)
        if (!enabled) return

        AudioEffectsService.sendApply(context.applicationContext)
    }
}
