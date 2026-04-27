package com.example.sleppify

import android.util.Log

private const val SONG_PLAYER_INTEROP_TAG = "SongPlayerInterop"

private inline fun <T> SongPlayerFragment.tryInvoke(name: String, block: (java.lang.reflect.Method) -> T, fallback: T): T {
    return try {
        val method = javaClass.getMethod(name)
        block(method)
    } catch (e: Throwable) {
        Log.w(SONG_PLAYER_INTEROP_TAG, "Missing SongPlayerFragment.$name() at runtime", e)
        fallback
    }
}

private inline fun <T> SongPlayerFragment.tryInvokeStringArg(
    name: String,
    arg: String,
    block: (java.lang.reflect.Method) -> T,
    fallback: T
): T {
    return try {
        val method = javaClass.getMethod(name, String::class.java)
        block(method)
    } catch (e: Throwable) {
        Log.w(SONG_PLAYER_INTEROP_TAG, "Missing SongPlayerFragment.$name(String) at runtime", e)
        fallback
    }
}

fun SongPlayerFragment.externalPauseForSessionExit() {
    tryInvoke("externalPauseForSessionExit", { it.invoke(this); Unit }, Unit)
}

fun SongPlayerFragment.externalSetReturnTargetTag(targetTag: String) {
    tryInvokeStringArg("externalSetReturnTargetTag", targetTag, { it.invoke(this, targetTag); Unit }, Unit)
}

fun SongPlayerFragment.externalGetReturnTargetTag(): String {
    return tryInvoke("externalGetReturnTargetTag", { it.invoke(this) as? String ?: "" }, "")
}

fun SongPlayerFragment.externalSnapshotForNavigation() {
    tryInvoke("externalSnapshotForNavigation", { it.invoke(this); Unit }, Unit)
}

fun SongPlayerFragment.externalTryEnterMiniMode(): Boolean {
    return tryInvoke("externalTryEnterMiniMode", { (it.invoke(this) as? Boolean) ?: false }, false)
}
