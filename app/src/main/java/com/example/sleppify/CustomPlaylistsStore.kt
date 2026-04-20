package com.example.sleppify

import android.content.Context
import android.content.SharedPreferences
import android.text.TextUtils
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

object CustomPlaylistsStore {
    private const val PREFS_NAME = "sleppify_custom_playlists"
    const val CUSTOM_PLAYLIST_PREFIX = "custom_playlist_"
    private const val KEY_PLAYLIST_NAMES = "all_playlist_names"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getAllPlaylistNames(context: Context): List<String> {
        val prefs = getPrefs(context)
        val namesJson = prefs.getString(KEY_PLAYLIST_NAMES, "[]")
        val names = mutableListOf<String>()
        try {
            val arr = JSONArray(namesJson)
            for (i in 0 until arr.length()) {
                val name = arr.getString(i)
                if (!TextUtils.isEmpty(name)) {
                    names.add(name)
                }
            }
        } catch (e: JSONException) {
        }
        return names.sorted()
    }

    fun createPlaylist(context: Context, name: String): Boolean {
        val trimmed = name.trim()
        if (TextUtils.isEmpty(trimmed)) return false
        val names = getAllPlaylistNames(context).toMutableList()
        val exists = names.any { it.equals(trimmed, ignoreCase = true) }
        if (exists) return false
        
        names.add(trimmed)
        getPrefs(context).edit().putString(KEY_PLAYLIST_NAMES, JSONArray(names).toString()).apply()
        
        // Sync empty playlist to cloud if signed in
        if (AuthManager.getInstance(context).isSignedIn()) {
            CloudSyncManager.getInstance(context).syncPlaylistToCloud(trimmed, emptyList())
        }
        return true
    }

    fun deletePlaylist(context: Context, name: String): Boolean {
        val trimmed = name.trim()
        val names = getAllPlaylistNames(context).toMutableList()
        if (!names.remove(trimmed)) return false
        
        val prefs = getPrefs(context)
        prefs.edit().putString(KEY_PLAYLIST_NAMES, JSONArray(names).toString()).apply()
        
        val tracks = getTracksFromPlaylist(context, trimmed)
        val trackIds = tracks.map { it.videoId }
        if (trackIds.isNotEmpty()) {
            OfflineAudioStore.deleteOfflineAudio(context, trackIds)
        }
        
        prefs.edit().remove(CUSTOM_PLAYLIST_PREFIX + trimmed).apply()
        
        // Disable offline auto feature for this playlist
        val playerPrefs = context.getSharedPreferences("player_state", Context.MODE_PRIVATE)
        playerPrefs.edit().remove("offline_auto_sync_$trimmed").apply()
        
        if (AuthManager.getInstance(context).isSignedIn()) {
            CloudSyncManager.getInstance(context).deletePlaylistFromCloud(trimmed)
        }
        return true
    }

    fun addTrackToPlaylist(context: Context, playlistName: String, videoId: String, title: String, subtitle: String, duration: String, thumbnailUrl: String) {
        val prefs = getPrefs(context)
        val key = CUSTOM_PLAYLIST_PREFIX + playlistName
        val existingJson = prefs.getString(key, "[]")
        val arr = try { JSONArray(existingJson) } catch (e: JSONException) { JSONArray() }
        
        // Remove duplicates if exist
        val newArr = JSONArray()
        var added = false
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            if (obj.getString("videoId") == videoId) {
                // Update existing
                obj.put("title", title)
                obj.put("subtitle", subtitle)
                obj.put("duration", duration)
                obj.put("thumbnailUrl", thumbnailUrl)
                added = true
            }
            newArr.put(obj)
        }
        
        if (!added) {
            val trackObj = JSONObject()
            trackObj.put("videoId", videoId)
            trackObj.put("title", title)
            trackObj.put("subtitle", subtitle)
            trackObj.put("duration", duration)
            trackObj.put("thumbnailUrl", thumbnailUrl)
            newArr.put(trackObj)
        }
        
        prefs.edit().putString(key, newArr.toString()).apply()
        
        // Sync to cloud if signed in
        if (AuthManager.getInstance(context).isSignedIn()) {
            val updatedTracks = getTracksFromPlaylist(context, playlistName)
            CloudSyncManager.getInstance(context).syncPlaylistToCloud(playlistName, updatedTracks)
        }
    }

    fun removeTrackFromPlaylist(context: Context, playlistName: String, videoId: String) {
        val prefs = getPrefs(context)
        val key = CUSTOM_PLAYLIST_PREFIX + playlistName
        val existingJson = prefs.getString(key, "[]")
        val arr = try { JSONArray(existingJson) } catch (e: JSONException) { JSONArray() }
        
        val newArr = JSONArray()
        var found = false
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            if (obj.getString("videoId") == videoId) {
                found = true
                continue
            }
            newArr.put(obj)
        }
        
        if (found) {
            prefs.edit().putString(key, newArr.toString()).apply()
            
            // Sync to cloud if signed in
            if (AuthManager.getInstance(context).isSignedIn()) {
                val updatedTracks = getTracksFromPlaylist(context, playlistName)
                CloudSyncManager.getInstance(context).syncPlaylistToCloud(playlistName, updatedTracks)
            }
        }
    }

    fun getTracksFromPlaylist(context: Context, playlistName: String): List<FavoritesPlaylistStore.FavoriteTrack> {
        val prefs = getPrefs(context)
        val key = CUSTOM_PLAYLIST_PREFIX + playlistName
        val existingJson = prefs.getString(key, "[]")
        val tracks = mutableListOf<FavoritesPlaylistStore.FavoriteTrack>()
        try {
            val arr = JSONArray(existingJson)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                tracks.add(FavoritesPlaylistStore.FavoriteTrack(
                    obj.getString("videoId"),
                    obj.getString("title"),
                    obj.getString("subtitle"),
                    obj.getString("duration"),
                    obj.getString("thumbnailUrl")
                ))
            }
        } catch (e: JSONException) {
        }
        return tracks
    }

    fun importCloudPlaylists(context: Context, playlists: Map<String, List<FavoritesPlaylistStore.FavoriteTrack>>) {
        val prefs = getPrefs(context)
        val names = playlists.keys.toList().sorted()
        
        val editor = prefs.edit()
        
        // Update all_playlist_names
        editor.putString(KEY_PLAYLIST_NAMES, JSONArray(names).toString())
        
        // Update each playlist's tracks
        for ((name, tracks) in playlists) {
            val key = CUSTOM_PLAYLIST_PREFIX + name
            val arr = JSONArray()
            for (track in tracks) {
                val obj = JSONObject()
                try {
                    obj.put("videoId", track.videoId)
                    obj.put("title", track.title)
                    obj.put("subtitle", track.artist)
                    obj.put("duration", track.duration)
                    obj.put("thumbnailUrl", track.imageUrl)
                    arr.put(obj)
                } catch (e: JSONException) {
                }
            }
            editor.putString(key, arr.toString())
        }
        editor.apply()
    }
}
