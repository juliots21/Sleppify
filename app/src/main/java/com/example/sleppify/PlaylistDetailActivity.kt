package com.example.sleppify

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.sleppify.utils.YouTubeCropTransformation
import com.example.sleppify.utils.YouTubeImageProcessor
import com.google.android.material.button.MaterialButton
import com.google.android.material.imageview.ShapeableImageView
import java.util.Locale

class PlaylistDetailActivity : AppCompatActivity() {

    private val youTubeMusicService = YouTubeMusicService()

    private lateinit var ivPlaylistCover: ShapeableImageView
    private lateinit var tvPlaylistName: TextView
    private lateinit var tvPlaylistOwner: TextView
    private lateinit var tvPlaylistCount: TextView
    private lateinit var tvPlaylistDuration: TextView
    private lateinit var tvInsightText: TextView
    private lateinit var tvTracksState: TextView
    private lateinit var rvTrackList: RecyclerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_playlist_detail)

        ivPlaylistCover = findViewById(R.id.ivPlaylistCover)
        tvPlaylistName = findViewById(R.id.tvPlaylistName)
        tvPlaylistOwner = findViewById(R.id.tvPlaylistOwner)
        tvPlaylistCount = findViewById(R.id.tvPlaylistCount)
        tvPlaylistDuration = findViewById(R.id.tvPlaylistDuration)
        tvInsightText = findViewById(R.id.tvInsightText)
        tvTracksState = findViewById(R.id.tvTracksState)
        rvTrackList = findViewById(R.id.rvTrackList)

        val btnBack: ImageButton = findViewById(R.id.btnBack)
        val btnMore: ImageButton = findViewById(R.id.btnMore)
        val btnListenNow: MaterialButton = findViewById(R.id.btnListenNow)
        val btnShuffle: ImageButton = findViewById(R.id.btnShuffle)
        val btnDownload: ImageButton = findViewById(R.id.btnDownload)
        val btnEnhance: MaterialButton = findViewById(R.id.btnEnhance)

        val playlistId = safeExtra(EXTRA_PLAYLIST_ID)
        var playlistTitle = safeExtra(EXTRA_PLAYLIST_TITLE)
        val playlistSubtitle = safeExtra(EXTRA_PLAYLIST_SUBTITLE)
        val playlistThumbnail = safeExtra(EXTRA_PLAYLIST_THUMBNAIL)
        val youtubeAccessToken = safeExtra(EXTRA_YOUTUBE_ACCESS_TOKEN)

        if (playlistTitle.isEmpty()) {
            playlistTitle = "Trapcito"
        }

        val meta = parseMeta(playlistSubtitle)
        bindHeader(playlistTitle, meta, playlistThumbnail)
        bindInsight(playlistTitle)
        bindTrackList(playlistTitle, meta.ownerLabel, playlistId, youtubeAccessToken)

        btnBack.setOnClickListener { finish() }
        btnMore.setOnClickListener { }
        btnShuffle.setOnClickListener { }
        btnDownload.setOnClickListener { }
        btnEnhance.setOnClickListener { }
        btnListenNow.setOnClickListener { openPlaylistExternal(playlistId) }
    }

    private fun safeExtra(key: String): String {
        return intent.getStringExtra(key)?.trim().orEmpty()
    }

    private fun bindHeader(playlistTitle: String, meta: PlaylistMeta, playlistThumbnail: String) {
        tvPlaylistName.text = playlistTitle
        tvPlaylistOwner.text = meta.ownerLabel
        tvPlaylistCount.text = ""
        tvPlaylistDuration.text = meta.durationLabel

        if (playlistThumbnail.isNotEmpty()) {
            Glide.with(this)
                .load(playlistThumbnail)
                .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.ALL)
                .fitCenter()
                .thumbnail(0.25f)
                .placeholder(R.drawable.ic_music)
                .error(R.drawable.ic_music)
                .into(ivPlaylistCover)
        } else {
            ivPlaylistCover.setImageResource(R.drawable.ic_music)
        }
    }

    private fun bindInsight(playlistTitle: String) {
        tvInsightText.text = "Based on your rotation of $playlistTitle, we can add 15 high-fidelity deep bass tracks."
    }

    private fun bindTrackList(
        playlistTitle: String,
        ownerLabel: String,
        playlistId: String,
        accessToken: String
    ) {
        rvTrackList.layoutManager = LinearLayoutManager(this)
        rvTrackList.setHasFixedSize(true)
        rvTrackList.itemAnimator = null
        rvTrackList.setItemViewCacheSize(15)

        val cachedTracks = getCachedTracks(playlistId)
        val canRequestRemote = playlistId.isNotEmpty() && !playlistId.startsWith("preview-") && accessToken.isNotEmpty()
        if (cachedTracks.isNotEmpty() && !canRequestRemote) {
            renderTrackResults(playlistId, cachedTracks)
        }

        if (!canRequestRemote) {
            if (cachedTracks.isEmpty()) {
                tvTracksState.text = ""
                rvTrackList.adapter = PlaylistTrackAdapter(emptyList())
            }
            return
        }

        tvTracksState.text = "Cargando canciones..."
        youTubeMusicService.fetchPlaylistTracks(
            accessToken,
            playlistId,
            PLAYLIST_TRACKS_FETCH_LIMIT,
            object : YouTubeMusicService.PlaylistTracksCallback {
                override fun onSuccess(tracks: List<YouTubeMusicService.PlaylistTrackResult>) {
                    if (isFinishing || isDestroyed) {
                        return
                    }

                    if (tracks.isEmpty()) {
                        if (cachedTracks.isNotEmpty()) {
                            return
                        }
                        tvTracksState.text = if (YouTubeMusicService.SPECIAL_LIKED_VIDEOS_ID == playlistId) {
                            "No tienes canciones con me gusta visibles todavia."
                        } else {
                            "Esta playlist no tiene canciones visibles."
                        }
                        rvTrackList.adapter = PlaylistTrackAdapter(emptyList())
                        return
                    }

                    cacheTracks(playlistId, tracks)
                    renderTrackResults(playlistId, tracks)
                }

                override fun onError(error: String) {
                    if (isFinishing || isDestroyed) {
                        return
                    }

                    if (cachedTracks.isNotEmpty()) {
                        renderTrackResults(playlistId, cachedTracks)
                        return
                    }
                    tvTracksState.text = "No se pudo leer la playlist. Mostrando demo."
                    rvTrackList.adapter = PlaylistTrackAdapter(buildMockTracks(playlistTitle, ownerLabel))
                }
            }
        )
    }

    private fun renderTrackResults(playlistId: String, tracks: List<YouTubeMusicService.PlaylistTrackResult>) {
        val mapped = ArrayList<PlaylistTrack>()
        var totalSeconds = 0

        for ((index, track) in tracks.withIndex()) {
            mapped.add(
                PlaylistTrack(
                    track.title,
                    track.artist,
                    track.duration,
                    index == 0,
                    track.thumbnailUrl
                )
            )
            totalSeconds += parseDurationSeconds(track.duration)
        }

        rvTrackList.adapter = PlaylistTrackAdapter(mapped)
        tvTracksState.text = if (YouTubeMusicService.SPECIAL_LIKED_VIDEOS_ID == playlistId) {
            "${mapped.size} canciones con me gusta"
        } else {
            "${mapped.size} canciones cargadas"
        }

        tvPlaylistCount.text = "${mapped.size} Songs"
        tvPlaylistDuration.text = formatTotalDuration(totalSeconds)
    }

    private fun shouldRefreshPlaylistTracks(playlistId: String): Boolean {
        val updatedAt = TRACKS_CACHE_UPDATED_AT[playlistId] ?: return true
        val age = System.currentTimeMillis() - updatedAt
        return age > PLAYLIST_TRACKS_REFRESH_INTERVAL_MS
    }

    private fun getCachedTracks(playlistId: String): List<YouTubeMusicService.PlaylistTrackResult> {
        val cached = TRACKS_CACHE[playlistId]
        val updatedAt = TRACKS_CACHE_UPDATED_AT[playlistId]
        if (cached.isNullOrEmpty() || updatedAt == null) {
            return emptyList()
        }

        val age = System.currentTimeMillis() - updatedAt
        if (age > PLAYLIST_TRACKS_CACHE_TTL_MS) {
            return emptyList()
        }

        return ArrayList(cached)
    }

    private fun cacheTracks(playlistId: String, tracks: List<YouTubeMusicService.PlaylistTrackResult>) {
        TRACKS_CACHE[playlistId] = ArrayList(tracks)
        TRACKS_CACHE_UPDATED_AT[playlistId] = System.currentTimeMillis()
    }

    private fun parseMeta(subtitle: String): PlaylistMeta {
        var owner = "Alex Nexus"
        var songs = 124

        if (subtitle.isNotEmpty()) {
            val tokens = subtitle.split("•")
            for (token in tokens) {
                val value = token.trim()
                if (value.isEmpty()) {
                    continue
                }

                val lower = value.lowercase(Locale.US)
                if (lower.contains("playlist")) {
                    continue
                }
                if (lower.contains("song") || lower.contains("cancion")) {
                    songs = parseFirstNumber(value, songs)
                } else {
                    owner = value
                }
            }
        }

        val totalMinutes = maxOf(35, songs * 3)
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        val duration = String.format(Locale.US, "%dh %02dm", hours, minutes)

        return PlaylistMeta(owner, "$songs Songs", duration)
    }

    private fun parseFirstNumber(text: String, fallback: Int): Int {
        val digits = text.replace("[^0-9]".toRegex(), "")
        if (digits.isEmpty()) {
            return fallback
        }

        return try {
            digits.toInt()
        } catch (_: NumberFormatException) {
            fallback
        }
    }

    private fun parseDurationSeconds(duration: String): Int {
        if (duration.isEmpty() || duration.contains("--")) {
            return 0
        }

        val parts = duration.split(":")
        return try {
            when (parts.size) {
                2 -> {
                    val minutes = parts[0].toInt()
                    val seconds = parts[1].toInt()
                    (minutes * 60) + seconds
                }
                3 -> {
                    val hours = parts[0].toInt()
                    val minutes = parts[1].toInt()
                    val seconds = parts[2].toInt()
                    (hours * 3600) + (minutes * 60) + seconds
                }
                else -> 0
            }
        } catch (_: NumberFormatException) {
            0
        }
    }

    private fun formatTotalDuration(totalSeconds: Int): String {
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        return if (hours > 0) {
            String.format(Locale.US, "%dh %02dm", hours, minutes)
        } else {
            String.format(Locale.US, "%dm", minutes)
        }
    }

    private fun openPlaylistExternal(playlistId: String) {
        if (YouTubeMusicService.SPECIAL_LIKED_VIDEOS_ID == playlistId) {
            return
        }

        if (playlistId.isEmpty() || playlistId.startsWith("preview-")) {
            return
        }

        try {
            val url = "https://music.youtube.com/playlist?list=" + Uri.encode(playlistId)
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: Exception) {
        }
    }

    private fun buildMockTracks(playlistTitle: String, ownerLabel: String): List<PlaylistTrack> {
        return arrayListOf(
            PlaylistTrack(
                "Cyber Drift",
                "Vapor Ghost • Night Shift",
                "3:42",
                true,
                "https://images.unsplash.com/photo-1511379938547-c1f69419868d?w=200"
            ),
            PlaylistTrack(
                "Basement Frequency",
                "Urban Legends • Underground",
                "4:15",
                false,
                "https://images.unsplash.com/photo-1470229538611-16ba8c7ffbd7?w=200"
            ),
            PlaylistTrack(
                "High Fidelity",
                "Nexus AI • Synthesized Reality",
                "3:28",
                false,
                "https://images.unsplash.com/photo-1493225457124-a3eb161ffa5f?w=200"
            ),
            PlaylistTrack(
                "Midnight Rhythm",
                "The Architects • Skyline Sessions",
                "5:02",
                false,
                "https://images.unsplash.com/photo-1501386761578-eac5c94b800a?w=200"
            ),
            PlaylistTrack(
                "$playlistTitle Signal",
                "$ownerLabel • Dead Waves",
                "2:55",
                false,
                "https://images.unsplash.com/photo-1494232410401-ad00d5433cfa?w=200"
            )
        )
    }

    private class PlaylistMeta(
        val ownerLabel: String,
        val countLabel: String,
        val durationLabel: String
    )

    private class PlaylistTrack(
        val title: String,
        val subtitle: String,
        val duration: String,
        val active: Boolean,
        val imageUrl: String
    )

    private class PlaylistTrackAdapter(private val items: List<PlaylistTrack>) :
        RecyclerView.Adapter<PlaylistTrackAdapter.TrackViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TrackViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_playlist_track, parent, false)
            return TrackViewHolder(view)
        }

        override fun onBindViewHolder(holder: TrackViewHolder, position: Int) {
            val track = items[position]
            holder.tvTrackTitle.text = track.title

            var subtitle = track.subtitle.trim()
            val duration = track.duration.trim()
            if (duration.isNotEmpty()) {
                subtitle = if (subtitle.isEmpty()) duration else "$subtitle • $duration"
            }
            holder.tvTrackSubtitle.text = subtitle

            if (!TextUtils.isEmpty(track.imageUrl)) {
                val url = track.imageUrl.trim()
                val density = holder.itemView.resources.displayMetrics.density
                val displayPx = (44 * density).toInt().coerceAtLeast(1)
                val base = Glide.with(holder.itemView)
                    .load(url)
                    .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.ALL)
                base.transform(YouTubeCropTransformation())
                    .thumbnail(0.25f)
                    .placeholder(R.drawable.ic_music)
                    .error(R.drawable.ic_music)
                    .into(holder.ivTrackArt)
            } else {
                holder.ivTrackArt.setImageResource(R.drawable.ic_music)
            }

            val activeTitleColor = ContextCompat.getColor(holder.itemView.context, R.color.stitch_blue)
            val defaultTitleColor = ContextCompat.getColor(holder.itemView.context, R.color.text_primary)

            holder.rootTrackRow.setBackgroundResource(
                if (track.active) R.drawable.bg_playlist_track_active else R.drawable.bg_playlist_track_default
            )
            holder.llNowPlayingOverlay.visibility = if (track.active) View.VISIBLE else View.GONE
            holder.tvTrackTitle.setTextColor(if (track.active) activeTitleColor else defaultTitleColor)
        }

        override fun getItemCount(): Int = items.size

        class TrackViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val rootTrackRow: LinearLayout = itemView.findViewById(R.id.rootTrackRow)
            val ivTrackArt: ShapeableImageView = itemView.findViewById(R.id.ivTrackArt)
            val llNowPlayingOverlay: LinearLayout = itemView.findViewById(R.id.llNowPlayingOverlay)
            val tvTrackTitle: TextView = itemView.findViewById(R.id.tvTrackTitle)
            val tvTrackSubtitle: TextView = itemView.findViewById(R.id.tvTrackSubtitle)
        }
    }

    companion object {
        private const val PLAYLIST_TRACKS_CACHE_TTL_MS = 10 * 60 * 1000L
        private const val PLAYLIST_TRACKS_REFRESH_INTERVAL_MS = 90 * 1000L
        private const val PLAYLIST_TRACKS_FETCH_LIMIT = 5000
        private val TRACKS_CACHE = HashMap<String, List<YouTubeMusicService.PlaylistTrackResult>>()
        private val TRACKS_CACHE_UPDATED_AT = HashMap<String, Long>()

        const val EXTRA_PLAYLIST_ID = "extra_playlist_id"
        const val EXTRA_PLAYLIST_TITLE = "extra_playlist_title"
        const val EXTRA_PLAYLIST_SUBTITLE = "extra_playlist_subtitle"
        const val EXTRA_PLAYLIST_THUMBNAIL = "extra_playlist_thumbnail"
        const val EXTRA_YOUTUBE_ACCESS_TOKEN = "extra_youtube_access_token"
    }
}
