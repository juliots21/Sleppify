package com.example.sleppify;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.imageview.ShapeableImageView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class PlaylistDetailActivity extends AppCompatActivity {

    private static final long PLAYLIST_TRACKS_CACHE_TTL_MS = 10 * 60 * 1000L;
    private static final long PLAYLIST_TRACKS_REFRESH_INTERVAL_MS = 90 * 1000L;
    private static final Map<String, List<YouTubeMusicService.PlaylistTrackResult>> TRACKS_CACHE = new HashMap<>();
    private static final Map<String, Long> TRACKS_CACHE_UPDATED_AT = new HashMap<>();

    public static final String EXTRA_PLAYLIST_ID = "extra_playlist_id";
    public static final String EXTRA_PLAYLIST_TITLE = "extra_playlist_title";
    public static final String EXTRA_PLAYLIST_SUBTITLE = "extra_playlist_subtitle";
    public static final String EXTRA_PLAYLIST_THUMBNAIL = "extra_playlist_thumbnail";
    public static final String EXTRA_YOUTUBE_ACCESS_TOKEN = "extra_youtube_access_token";

    private ShapeableImageView ivPlaylistCover;
    private TextView tvPlaylistName;
    private TextView tvPlaylistOwner;
    private TextView tvPlaylistCount;
    private TextView tvPlaylistDuration;
    private TextView tvInsightText;
    private TextView tvTracksState;
    private RecyclerView rvTrackList;

    private final YouTubeMusicService youTubeMusicService = new YouTubeMusicService();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_playlist_detail);

        ivPlaylistCover = findViewById(R.id.ivPlaylistCover);
        tvPlaylistName = findViewById(R.id.tvPlaylistName);
        tvPlaylistOwner = findViewById(R.id.tvPlaylistOwner);
        tvPlaylistCount = findViewById(R.id.tvPlaylistCount);
        tvPlaylistDuration = findViewById(R.id.tvPlaylistDuration);
        tvInsightText = findViewById(R.id.tvInsightText);
        tvTracksState = findViewById(R.id.tvTracksState);
        rvTrackList = findViewById(R.id.rvTrackList);

        ImageButton btnBack = findViewById(R.id.btnBack);
        ImageButton btnMore = findViewById(R.id.btnMore);
        MaterialButton btnListenNow = findViewById(R.id.btnListenNow);
        ImageButton btnShuffle = findViewById(R.id.btnShuffle);
        ImageButton btnDownload = findViewById(R.id.btnDownload);
        MaterialButton btnEnhance = findViewById(R.id.btnEnhance);

        String playlistId = safeExtra(EXTRA_PLAYLIST_ID);
        String playlistTitle = safeExtra(EXTRA_PLAYLIST_TITLE);
        String playlistSubtitle = safeExtra(EXTRA_PLAYLIST_SUBTITLE);
        String playlistThumbnail = safeExtra(EXTRA_PLAYLIST_THUMBNAIL);
        String youtubeAccessToken = safeExtra(EXTRA_YOUTUBE_ACCESS_TOKEN);

        if (playlistTitle.isEmpty()) {
            playlistTitle = "Trapcito";
        }

        PlaylistMeta meta = parseMeta(playlistSubtitle);
        bindHeader(playlistTitle, meta, playlistThumbnail);
        bindInsight(playlistTitle);
        bindTrackList(playlistTitle, meta.ownerLabel, playlistId, youtubeAccessToken);

        btnBack.setOnClickListener(v -> finish());
        btnMore.setOnClickListener(v -> Toast.makeText(this, "Mas opciones pronto", Toast.LENGTH_SHORT).show());
        btnShuffle.setOnClickListener(v -> Toast.makeText(this, "Modo shuffle activado", Toast.LENGTH_SHORT).show());
        btnDownload.setOnClickListener(v -> Toast.makeText(this, "Descarga offline en desarrollo", Toast.LENGTH_SHORT).show());
        btnEnhance.setOnClickListener(v -> Toast.makeText(this, "Nexus AI mejorando playlist", Toast.LENGTH_SHORT).show());
        btnListenNow.setOnClickListener(v -> openPlaylistExternal(playlistId));
    }

    @NonNull
    private String safeExtra(@NonNull String key) {
        String value = getIntent().getStringExtra(key);
        return value == null ? "" : value.trim();
    }

    private void bindHeader(@NonNull String playlistTitle, @NonNull PlaylistMeta meta, @NonNull String playlistThumbnail) {
        tvPlaylistName.setText(playlistTitle);
        tvPlaylistOwner.setText(meta.ownerLabel);
        tvPlaylistCount.setText(meta.countLabel);
        tvPlaylistDuration.setText(meta.durationLabel);

        if (!playlistThumbnail.isEmpty()) {
            Glide.with(this)
                    .load(playlistThumbnail)
                    .transform(ArtworkTrimTransformation.obtain(), new com.bumptech.glide.load.resource.bitmap.CenterCrop())
                    .placeholder(R.drawable.ic_music)
                    .error(R.drawable.ic_music)
                    .into(ivPlaylistCover);
        } else {
            ivPlaylistCover.setImageResource(R.drawable.ic_music);
        }
    }

    private void bindInsight(@NonNull String playlistTitle) {
        tvInsightText.setText("Based on your rotation of "
                + playlistTitle
                + ", we can add 15 high-fidelity deep bass tracks.");
    }

    private void bindTrackList(
            @NonNull String playlistTitle,
            @NonNull String ownerLabel,
            @NonNull String playlistId,
            @NonNull String accessToken
    ) {
        rvTrackList.setLayoutManager(new LinearLayoutManager(this));

        List<YouTubeMusicService.PlaylistTrackResult> cachedTracks = getCachedTracks(playlistId);
        if (!cachedTracks.isEmpty()) {
            renderTrackResults(playlistId, cachedTracks);
        }

        if (playlistId.isEmpty() || playlistId.startsWith("preview-") || accessToken.isEmpty()) {
            if (cachedTracks.isEmpty()) {
                tvTracksState.setText("");
                rvTrackList.setAdapter(new PlaylistTrackAdapter(new ArrayList<>()));
            }
            return;
        }

        if (!cachedTracks.isEmpty() && !shouldRefreshPlaylistTracks(playlistId)) {
            return;
        }

        if (cachedTracks.isEmpty()) {
            tvTracksState.setText("Cargando canciones...");
        }
        youTubeMusicService.fetchPlaylistTracks(accessToken, playlistId, 50, new YouTubeMusicService.PlaylistTracksCallback() {
            @Override
            public void onSuccess(@NonNull List<YouTubeMusicService.PlaylistTrackResult> tracks) {
                if (isFinishing() || isDestroyed()) {
                    return;
                }

                if (tracks.isEmpty()) {
                    if (!cachedTracks.isEmpty()) {
                        return;
                    }
                    if (YouTubeMusicService.SPECIAL_LIKED_VIDEOS_ID.equals(playlistId)) {
                        tvTracksState.setText("No tienes canciones con me gusta visibles todavia.");
                    } else {
                        tvTracksState.setText("Esta playlist no tiene canciones visibles.");
                    }
                    rvTrackList.setAdapter(new PlaylistTrackAdapter(new ArrayList<>()));
                    return;
                }

                cacheTracks(playlistId, tracks);
                renderTrackResults(playlistId, tracks);
            }

            @Override
            public void onError(@NonNull String error) {
                if (isFinishing() || isDestroyed()) {
                    return;
                }

                if (!cachedTracks.isEmpty()) {
                    return;
                }
                tvTracksState.setText("No se pudo leer la playlist. Mostrando demo.");
                rvTrackList.setAdapter(new PlaylistTrackAdapter(new ArrayList<>()));
            }
        });
    }

    private void renderTrackResults(
            @NonNull String playlistId,
            @NonNull List<YouTubeMusicService.PlaylistTrackResult> tracks
    ) {
        List<PlaylistTrack> mapped = new ArrayList<>();
        int totalSeconds = 0;
        for (int i = 0; i < tracks.size(); i++) {
            YouTubeMusicService.PlaylistTrackResult track = tracks.get(i);
            mapped.add(new PlaylistTrack(
                    track.title,
                    track.artist,
                    track.duration,
                    i == 0,
                    track.thumbnailUrl
            ));
            totalSeconds += parseDurationSeconds(track.duration);
        }

        rvTrackList.setAdapter(new PlaylistTrackAdapter(mapped));
        if (YouTubeMusicService.SPECIAL_LIKED_VIDEOS_ID.equals(playlistId)) {
            tvTracksState.setText(mapped.size() + " canciones con me gusta");
        } else {
            tvTracksState.setText(mapped.size() + " canciones cargadas");
        }
        tvPlaylistCount.setText(mapped.size() + " Songs");
        tvPlaylistDuration.setText(formatTotalDuration(totalSeconds));
    }

    private boolean shouldRefreshPlaylistTracks(@NonNull String playlistId) {
        Long updatedAt = TRACKS_CACHE_UPDATED_AT.get(playlistId);
        if (updatedAt == null) {
            return true;
        }
        long age = System.currentTimeMillis() - updatedAt;
        return age > PLAYLIST_TRACKS_REFRESH_INTERVAL_MS;
    }

    @NonNull
    private List<YouTubeMusicService.PlaylistTrackResult> getCachedTracks(@NonNull String playlistId) {
        List<YouTubeMusicService.PlaylistTrackResult> cached = TRACKS_CACHE.get(playlistId);
        Long updatedAt = TRACKS_CACHE_UPDATED_AT.get(playlistId);
        if (cached == null || cached.isEmpty() || updatedAt == null) {
            return new ArrayList<>();
        }

        long age = System.currentTimeMillis() - updatedAt;
        if (age > PLAYLIST_TRACKS_CACHE_TTL_MS) {
            return new ArrayList<>();
        }

        return new ArrayList<>(cached);
    }

    private void cacheTracks(
            @NonNull String playlistId,
            @NonNull List<YouTubeMusicService.PlaylistTrackResult> tracks
    ) {
        TRACKS_CACHE.put(playlistId, new ArrayList<>(tracks));
        TRACKS_CACHE_UPDATED_AT.put(playlistId, System.currentTimeMillis());
    }

    @NonNull
    private PlaylistMeta parseMeta(@NonNull String subtitle) {
        String owner = "Alex Nexus";
        int songs = 124;

        if (!subtitle.isEmpty()) {
            String[] tokens = subtitle.split("•");
            for (String token : tokens) {
                String value = token == null ? "" : token.trim();
                if (value.isEmpty()) {
                    continue;
                }

                String lower = value.toLowerCase(Locale.US);
                if (lower.contains("playlist")) {
                    continue;
                }
                if (lower.contains("song") || lower.contains("cancion")) {
                    songs = parseFirstNumber(value, songs);
                } else {
                    owner = value;
                }
            }
        }

        int totalMinutes = Math.max(35, songs * 3);
        int hours = totalMinutes / 60;
        int minutes = totalMinutes % 60;
        String duration = String.format(Locale.US, "%dh %02dm", hours, minutes);

        return new PlaylistMeta(owner, songs + " Songs", duration);
    }

    private int parseFirstNumber(@NonNull String text, int fallback) {
        String digits = text.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) {
            return fallback;
        }
        try {
            return Integer.parseInt(digits);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private int parseDurationSeconds(@NonNull String duration) {
        if (duration.isEmpty() || duration.contains("--")) {
            return 0;
        }

        String[] parts = duration.split(":");
        try {
            if (parts.length == 2) {
                int minutes = Integer.parseInt(parts[0]);
                int seconds = Integer.parseInt(parts[1]);
                return (minutes * 60) + seconds;
            }
            if (parts.length == 3) {
                int hours = Integer.parseInt(parts[0]);
                int minutes = Integer.parseInt(parts[1]);
                int seconds = Integer.parseInt(parts[2]);
                return (hours * 3600) + (minutes * 60) + seconds;
            }
        } catch (NumberFormatException ignored) {
            return 0;
        }
        return 0;
    }

    @NonNull
    private String formatTotalDuration(int totalSeconds) {
        int hours = totalSeconds / 3600;
        int minutes = (totalSeconds % 3600) / 60;
        if (hours > 0) {
            return String.format(Locale.US, "%dh %02dm", hours, minutes);
        }
        return String.format(Locale.US, "%dm", minutes);
    }

    private void openPlaylistExternal(@NonNull String playlistId) {
        if (YouTubeMusicService.SPECIAL_LIKED_VIDEOS_ID.equals(playlistId)) {
            Toast.makeText(this, "Abre YouTube para gestionar tus Me gusta.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (playlistId.isEmpty() || playlistId.startsWith("preview-")) {
            Toast.makeText(this, "Vista demo de playlist", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            String url = "https://music.youtube.com/playlist?list=" + Uri.encode(playlistId);
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception ignored) {
            Toast.makeText(this, "No se pudo abrir la playlist", Toast.LENGTH_SHORT).show();
        }
    }

    @NonNull
    private List<PlaylistTrack> buildMockTracks(@NonNull String playlistTitle, @NonNull String ownerLabel) {
        List<PlaylistTrack> tracks = new ArrayList<>();
        tracks.add(new PlaylistTrack(
                "Cyber Drift",
                "Vapor Ghost • Night Shift",
                "3:42",
                true,
                "https://images.unsplash.com/photo-1511379938547-c1f69419868d?w=200"
        ));
        tracks.add(new PlaylistTrack(
                "Basement Frequency",
                "Urban Legends • Underground",
                "4:15",
                false,
                "https://images.unsplash.com/photo-1470229538611-16ba8c7ffbd7?w=200"
        ));
        tracks.add(new PlaylistTrack(
                "High Fidelity",
                "Nexus AI • Synthesized Reality",
                "3:28",
                false,
                "https://images.unsplash.com/photo-1493225457124-a3eb161ffa5f?w=200"
        ));
        tracks.add(new PlaylistTrack(
                "Midnight Rhythm",
                "The Architects • Skyline Sessions",
                "5:02",
                false,
                "https://images.unsplash.com/photo-1501386761578-eac5c94b800a?w=200"
        ));
        tracks.add(new PlaylistTrack(
                playlistTitle + " Signal",
                ownerLabel + " • Dead Waves",
                "2:55",
                false,
                "https://images.unsplash.com/photo-1494232410401-ad00d5433cfa?w=200"
        ));
        return tracks;
    }

    private static final class PlaylistMeta {
        final String ownerLabel;
        final String countLabel;
        final String durationLabel;

        PlaylistMeta(@NonNull String ownerLabel, @NonNull String countLabel, @NonNull String durationLabel) {
            this.ownerLabel = ownerLabel;
            this.countLabel = countLabel;
            this.durationLabel = durationLabel;
        }
    }

    private static final class PlaylistTrack {
        final String title;
        final String subtitle;
        final String duration;
        final boolean active;
        final String imageUrl;

        PlaylistTrack(
                @NonNull String title,
                @NonNull String subtitle,
                @NonNull String duration,
                boolean active,
                @NonNull String imageUrl
        ) {
            this.title = title;
            this.subtitle = subtitle;
            this.duration = duration;
            this.active = active;
            this.imageUrl = imageUrl;
        }
    }

    private static final class PlaylistTrackAdapter extends RecyclerView.Adapter<PlaylistTrackAdapter.TrackViewHolder> {

        private final List<PlaylistTrack> items;

        PlaylistTrackAdapter(@NonNull List<PlaylistTrack> items) {
            this.items = items;
        }

        @NonNull
        @Override
        public TrackViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_playlist_track, parent, false);
            return new TrackViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull TrackViewHolder holder, int position) {
            PlaylistTrack track = items.get(position);
            holder.tvTrackTitle.setText(track.title);
            String subtitle = track.subtitle == null ? "" : track.subtitle.trim();
            String duration = track.duration == null ? "" : track.duration.trim();
            if (!duration.isEmpty()) {
                subtitle = subtitle.isEmpty() ? duration : subtitle + " • " + duration;
            }
            holder.tvTrackSubtitle.setText(subtitle);

            if (!TextUtils.isEmpty(track.imageUrl)) {
                Glide.with(holder.itemView)
                        .load(track.imageUrl)
                        .transform(ArtworkTrimTransformation.obtain(), new com.bumptech.glide.load.resource.bitmap.CenterCrop())
                        .placeholder(R.drawable.ic_music)
                        .error(R.drawable.ic_music)
                        .into(holder.ivTrackArt);
            } else {
                holder.ivTrackArt.setImageResource(R.drawable.ic_music);
            }

            int activeTitleColor = ContextCompat.getColor(holder.itemView.getContext(), R.color.stitch_blue);
            int defaultTitleColor = ContextCompat.getColor(holder.itemView.getContext(), R.color.text_primary);

            holder.rootTrackRow.setBackgroundResource(track.active
                    ? R.drawable.bg_playlist_track_active
                    : R.drawable.bg_playlist_track_default);
            holder.llNowPlayingOverlay.setVisibility(track.active ? View.VISIBLE : View.GONE);
            holder.tvTrackTitle.setTextColor(track.active ? activeTitleColor : defaultTitleColor);
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        static final class TrackViewHolder extends RecyclerView.ViewHolder {
            final LinearLayout rootTrackRow;
            final ShapeableImageView ivTrackArt;
            final LinearLayout llNowPlayingOverlay;
            final TextView tvTrackTitle;
            final TextView tvTrackSubtitle;

            TrackViewHolder(@NonNull View itemView) {
                super(itemView);
                rootTrackRow = itemView.findViewById(R.id.rootTrackRow);
                ivTrackArt = itemView.findViewById(R.id.ivTrackArt);
                llNowPlayingOverlay = itemView.findViewById(R.id.llNowPlayingOverlay);
                tvTrackTitle = itemView.findViewById(R.id.tvTrackTitle);
                tvTrackSubtitle = itemView.findViewById(R.id.tvTrackSubtitle);
            }
        }
    }
}
