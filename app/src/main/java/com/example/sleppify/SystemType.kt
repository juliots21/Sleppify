package com.example.sleppify

import android.content.Context
import android.app.UiModeManager
import android.content.res.Configuration

object SystemType {
    private var isTvCache: Boolean? = null

    @JvmStatic
    fun isTv(context: Context): Boolean {
        if (isTvCache == null) {
            val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
            isTvCache = uiModeManager?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
        }
        return isTvCache!!
    }
}
