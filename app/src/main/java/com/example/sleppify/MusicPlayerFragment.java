package com.example.sleppify;

import android.accounts.Account;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import com.example.sleppify.utils.YouTubeCropTransformation;
import com.example.sleppify.utils.YouTubeImageProcessor;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.PopupWindow;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.LinearLayout;
import android.content.res.ColorStateList;
import androidx.core.content.ContextCompat;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Observer;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.Constraints;
import androidx.work.NetworkType;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import com.bumptech.glide.Glide;
import com.bumptech.glide.RequestBuilder;
import com.bumptech.glide.load.engine.DiskCacheStrategy;

import android.graphics.drawable.Drawable;
import com.google.android.gms.auth.GoogleAuthException;
import com.google.android.gms.auth.GoogleAuthUtil;
import com.google.android.gms.auth.UserRecoverableAuthException;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.auth.api.signin.GoogleSignInStatusCodes;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.CommonStatusCodes;
import com.google.android.gms.common.api.Scope;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.imageview.ShapeableImageView;
import com.google.android.material.textfield.TextInputEditText;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.text.Normalizer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MusicPlayerFragment extends Fragment {

    private static final long LIBRARY_CACHE_TTL_MS = 20 * 60 * 1000L;
    private static final long LIBRARY_REFRESH_MIN_INTERVAL_MS = 90 * 1000L;
    private static final long TOKEN_CACHE_TTL_MS = 45 * 60 * 1000L;
    private static final String PREFS_PLAYER_STATE = "player_state";
    private static final String PREF_PLAYBACK_POS_PREFIX = "yt_pos_";
    private static final String PREF_LAST_PLAYLIST_ID = "stream_last_playlist_id";
    private static final String PREF_LAST_PLAYLIST_TITLE = "stream_last_playlist_title";
    private static final String PREF_LAST_PLAYLIST_SUBTITLE = "stream_last_playlist_subtitle";
    private static final String PREF_LAST_PLAYLIST_THUMBNAIL = "stream_last_playlist_thumbnail";
    private static final String PREF_LAST_VIDEO_ID = "stream_last_video_id";
    private static final String PREF_LAST_TRACK_TITLE = "stream_last_track_title";
    private static final String PREF_LAST_TRACK_ARTIST = "stream_last_track_artist";
    private static final String PREF_LAST_TRACK_IMAGE = "stream_last_track_image";
    private static final String PREF_LAST_TRACK_DURATION = "stream_last_track_duration";
    private static final String PREF_LAST_IS_PLAYING = "stream_last_is_playing";
    private static final String PREF_LAST_STREAM_SCREEN = "stream_last_screen";
    private static final String PREF_LAST_YOUTUBE_ACCESS_TOKEN = "stream_last_youtube_access_token";
    private static final String PREF_LAST_YOUTUBE_ACCESS_TOKEN_UPDATED_AT = "stream_last_youtube_access_token_updated_at";
    private static final String PREF_LAST_YOUTUBE_WEB_COOKIE = "stream_last_youtube_web_cookie";
    private static final String PREF_LAST_STREAMING_OAUTH_ACCOUNT_EMAIL = "stream_last_oauth_account_email";
    private static final String STREAM_SCREEN_LIBRARY = "library";
    private static final String STREAM_SCREEN_PLAYLIST_DETAIL = "playlist_detail";
    private static final String PREF_RECENT_SEARCH_QUERIES = "stream_recent_search_queries";
    private static final long MINI_PROGRESS_TICK_MS = 500L;
    private static final long MINI_SNAPSHOT_REFRESH_MS = 1200L;
    private static final long LIBRARY_INLINE_SEARCH_DEBOUNCE_MS = 320L;
    private static final String TAG_MODULE_MUSIC = "module_music";
    private static final String TAG_STREAMING = "SleppifyStreaming";
    private static final String PREFS_STREAMING_CACHE = "streaming_cache";
    private static final String PREF_LIBRARY_UPDATED_AT_PREFIX = "library_updated_at_";
    private static final String PREF_LIBRARY_DATA_PREFIX = "library_data_";
    private static final String PREF_TRACKS_UPDATED_AT_PREFIX = "playlist_tracks_updated_at_";
    private static final String PREF_TRACKS_DATA_PREFIX = "playlist_tracks_data_";
    private static final String PREF_TRACKS_FULL_CACHE_PREFIX = "playlist_tracks_cache_full_";
    private static final String PREF_PLAYLIST_OFFLINE_COMPLETE_PREFIX = "playlist_offline_complete_";
    private static final String PREF_PLAYLIST_OFFLINE_AUTO_PREFIX = "playlist_offline_auto_";
    private static final String PREF_LAST_STREAMING_ACCOUNT_KEY = "stream_last_library_account_key";
    private static final String ACTION_NEW_PLAYLIST_ID = "sleppify_action_new_playlist";
    private static final String OFFLINE_DOWNLOAD_UNIQUE_PREFIX = "offline_playlist_";
    private static final String OFFLINE_DOWNLOAD_QUEUE_UNIQUE_NAME = "offline_playlist_queue";
    private static final String OFFLINE_DOWNLOAD_MANUAL_TRACK_QUEUE_UNIQUE_NAME = "offline_manual_track_queue";
    private static final int OFFLINE_AUTO_BATCH_TARGET_TRACKS = 3;
    private static final Set<String> offlineSyncRecentlyProcessedPlaylistIds = Collections.synchronizedSet(new HashSet<>());
    private static boolean isOfflineSyncInFlight = false;
    private static final long OFFLINE_TRACKS_PREFETCH_STALE_MS = 7L * 24 * 60 * 60 * 1000L;
    private static final int OFFLINE_TRACKS_PREFETCH_LIMIT = 12;
    private static final int OFFLINE_TRACKS_PREFETCH_PER_PLAYLIST_LIMIT = 250;
    private static final int LIBRARY_PLAYLIST_FETCH_LIMIT = 150;
        private static final int SEARCH_PAGE_SIZE = 20;
        private static final int SEARCH_SUGGESTION_RECENT_LIMIT = 6;
        private static final int SEARCH_SCROLL_LOAD_MORE_THRESHOLD = 4;
        private static final String[] DEFAULT_SEARCH_SUGGESTIONS = new String[] {
            "musica para dormir",
            "lofi chill",
            "lluvia relajante",
            "piano instrumental",
            "white noise",
            "deep sleep music"
        };
    private static final int LIBRARY_INLINE_SEARCH_MAX_RESULTS = 220;
    private static final int LIBRARY_INLINE_ONLINE_MIN_QUERY_CHARS = 3;
    private static final List<YouTubeMusicService.TrackResult> LIBRARY_CACHE = new ArrayList<>();
    private static final ExecutorService STREAMING_WARMUP_EXECUTOR = Executors.newSingleThreadExecutor();
    private static String libraryCacheAccountKey = "";
    private static long libraryCacheUpdatedAtMs;
    private static String tokenCacheAccountKey = "";
    private static String cachedYoutubeAccessToken = "";
    private static long tokenCacheUpdatedAtMs;
    private static long lastLibrarySyncAtMs;
    private static boolean streamingWarmupInFlight;

    static void prewarmStreamingAfterAppSignIn(@NonNull Context context, @Nullable String primaryAppEmail) {
        String accountKey = normalizeAccountEmail(primaryAppEmail);
        if (TextUtils.isEmpty(accountKey)) {
            return;
        }

        Context appContext = context.getApplicationContext();
        GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(appContext);
        if (account == null) {
            return;
        }

        String signedEmail = normalizeAccountEmail(account.getEmail());
        if (TextUtils.isEmpty(signedEmail) || !accountKey.equals(signedEmail)) {
            return;
        }

        YouTubeMusicService warmupService = new YouTubeMusicService();
        Scope ytScope = new Scope(warmupService.getYoutubeReadonlyScope());
        if (!GoogleSignIn.hasPermissions(account, ytScope)) {
            return;
        }

        synchronized (MusicPlayerFragment.class) {
            if (streamingWarmupInFlight) {
                return;
            }

            boolean tokenReady = !TextUtils.isEmpty(cachedYoutubeAccessToken)
                    && accountKey.equals(tokenCacheAccountKey)
                    && (System.currentTimeMillis() - tokenCacheUpdatedAtMs) < TOKEN_CACHE_TTL_MS;
            boolean libraryReady = !LIBRARY_CACHE.isEmpty()
                    && accountKey.equals(libraryCacheAccountKey)
                    && (System.currentTimeMillis() - libraryCacheUpdatedAtMs) < LIBRARY_CACHE_TTL_MS;
            if (tokenReady && libraryReady) {
                return;
            }

            streamingWarmupInFlight = true;
        }

        Account accountRef = account.getAccount();
        if (accountRef == null) {
            accountRef = new Account(accountKey, "com.google");
        }

        final Account resolvedAccount = accountRef;

        STREAMING_WARMUP_EXECUTOR.execute(() -> {
            try {
                String scope = "oauth2:" + warmupService.getYoutubeReadonlyScope();
                String token = GoogleAuthUtil.getToken(appContext, resolvedAccount, scope);
                if (TextUtils.isEmpty(token)) {
                    finishStreamingWarmup();
                    return;
                }

                synchronized (MusicPlayerFragment.class) {
                    tokenCacheAccountKey = accountKey;
                    cachedYoutubeAccessToken = token;
                    tokenCacheUpdatedAtMs = System.currentTimeMillis();
                }

                appContext
                        .getSharedPreferences(PREFS_PLAYER_STATE, Activity.MODE_PRIVATE)
                        .edit()
                        .putString(PREF_LAST_YOUTUBE_ACCESS_TOKEN, token)
                    .putLong(PREF_LAST_YOUTUBE_ACCESS_TOKEN_UPDATED_AT, System.currentTimeMillis())
                        .apply();

                SharedPreferences cachePrefs = appContext.getSharedPreferences(PREFS_STREAMING_CACHE, Activity.MODE_PRIVATE);
                cachePrefs.edit().putString(PREF_LAST_STREAMING_ACCOUNT_KEY, accountKey).apply();

                String warmToken = token;
                warmupService.fetchMyPlaylists(warmToken, 25, new YouTubeMusicService.PlaylistsCallback() {
                    @Override
                    public void onSuccess(@NonNull List<YouTubeMusicService.PlaylistResult> playlists) {
                        List<YouTubeMusicService.TrackResult> mapped = mapPlaylistsToLibraryTracks(playlists);

                        long updatedAt = System.currentTimeMillis();
                        synchronized (MusicPlayerFragment.class) {
                            LIBRARY_CACHE.clear();
                            LIBRARY_CACHE.addAll(new ArrayList<>(mapped));
                            libraryCacheAccountKey = accountKey;
                            libraryCacheUpdatedAtMs = updatedAt;
                        }

                        try {
                            JSONArray array = new JSONArray();
                            for (YouTubeMusicService.TrackResult item : mapped) {
                                JSONObject obj = new JSONObject();
                                obj.put("resultType", item.resultType);
                                obj.put("contentId", item.contentId);
                                obj.put("title", item.title);
                                obj.put("subtitle", item.subtitle);
                                obj.put("thumbnailUrl", item.thumbnailUrl);
                                array.put(obj);
                            }

                            cachePrefs.edit()
                                    .putLong(PREF_LIBRARY_UPDATED_AT_PREFIX + accountKey, updatedAt)
                                    .putString(PREF_LIBRARY_DATA_PREFIX + accountKey, array.toString())
                                    .putString(PREF_LAST_STREAMING_ACCOUNT_KEY, accountKey)
                                    .apply();
                        } catch (Exception ignored) {
                        }

                        finishStreamingWarmup();
                    }

                    @Override
                    public void onError(@NonNull String error) {
                        finishStreamingWarmup();
                    }
                });
            } catch (UserRecoverableAuthException ignored) {
                // No abrimos UI aqui; este warmup es solo en segundo plano.
                finishStreamingWarmup();
            } catch (IOException | GoogleAuthException e) {
                finishStreamingWarmup();
            }
        });
    }

    private static void finishStreamingWarmup() {
        synchronized (MusicPlayerFragment.class) {
            streamingWarmupInFlight = false;
        }
    }

    @NonNull
    private static String normalizeAccountEmail(@Nullable String value) {
        if (TextUtils.isEmpty(value)) {
            return "";
        }
        return value.trim().toLowerCase(Locale.US);
    }

    @NonNull
    private static List<YouTubeMusicService.TrackResult> mapPlaylistsToLibraryTracks(
            @NonNull List<YouTubeMusicService.PlaylistResult> playlists
    ) {
        List<YouTubeMusicService.TrackResult> mapped = new ArrayList<>();
        for (YouTubeMusicService.PlaylistResult playlist : playlists) {
            String title = playlist.title;
            String subtitle;
            String titleLower = playlist.title == null ? "" : playlist.title.toLowerCase(Locale.US);
            boolean isLikedCollection = YouTubeMusicService.SPECIAL_LIKED_VIDEOS_ID.equals(playlist.playlistId)
                    || titleLower.contains("gusta")
                    || titleLower.contains("liked");
            String playlistContentId = isLikedCollection
                    ? YouTubeMusicService.SPECIAL_LIKED_VIDEOS_ID
                    : playlist.playlistId;

            if (isLikedCollection) {
                title = "Musica que te gusto";
                subtitle = playlist.itemCount > 0
                        ? "Playlist autogenerada • " + playlist.itemCount + " canciones"
                        : "Playlist autogenerada";
            } else if (playlist.itemCount > 0) {
                subtitle = "Playlist • " + playlist.itemCount + " canciones";
            } else {
                subtitle = "Playlist";
            }

            mapped.add(new YouTubeMusicService.TrackResult(
                    "playlist",
                    playlistContentId,
                    title,
                    subtitle,
                    playlist.thumbnailUrl
            ));
        }
        return mapped;
    }

    private void ensureFavoritesPlaylistInLibraryTracks() {
        if (!isAdded()) {
            return;
        }

        List<FavoritesPlaylistStore.FavoriteTrack> favorites = FavoritesPlaylistStore.loadFavorites(requireContext());
        int count = favorites.size();
        String subtitle = FavoritesPlaylistStore.buildSubtitle(count);
        String coverUrl = "";
        for (FavoritesPlaylistStore.FavoriteTrack favorite : favorites) {
            if (favorite == null || TextUtils.isEmpty(favorite.imageUrl)) {
                continue;
            }
            coverUrl = favorite.imageUrl;
            break;
        }

        int existingIndex = -1;
        for (int i = 0; i < libraryTracks.size(); i++) {
            YouTubeMusicService.TrackResult item = libraryTracks.get(i);
            if (item == null) {
                continue;
            }
            if (TextUtils.equals(FavoritesPlaylistStore.PLAYLIST_ID, item.contentId)) {
                existingIndex = i;
                break;
            }
        }

        YouTubeMusicService.TrackResult favoritesTrack = new YouTubeMusicService.TrackResult(
                "playlist",
                FavoritesPlaylistStore.PLAYLIST_ID,
                FavoritesPlaylistStore.PLAYLIST_TITLE,
                subtitle,
            coverUrl
        );

        if (existingIndex >= 0) {
            libraryTracks.remove(existingIndex);
        }
        libraryTracks.add(0, favoritesTrack);

        List<String> customPlaylists = CustomPlaylistsStore.INSTANCE.getAllPlaylistNames(requireContext());
        int injectionIndex = 1;

        for (String customName : customPlaylists) {
            List<FavoritesPlaylistStore.FavoriteTrack> customTracks = CustomPlaylistsStore.INSTANCE.getTracksFromPlaylist(requireContext(), customName);
            int countCustom = customTracks.size();
            String customSubtitle = countCustom == 1 ? "1 canción" : countCustom + " canciones";
            String customThumb = "";
            for (FavoritesPlaylistStore.FavoriteTrack t : customTracks) {
                if (!TextUtils.isEmpty(t.imageUrl)) {
                    customThumb = t.imageUrl;
                    break;
                }
            }
            YouTubeMusicService.TrackResult customTrackResult = new YouTubeMusicService.TrackResult(
                "playlist",
                CustomPlaylistsStore.CUSTOM_PLAYLIST_PREFIX + customName,
                customName,
                customSubtitle,
                customThumb
            );

            // remove previous version if exists
            int eIdx = -1;
            for (int i=0; i<libraryTracks.size(); i++) {
                if (TextUtils.equals(customTrackResult.contentId, libraryTracks.get(i).contentId)) {
                    eIdx = i; break;
                }
            }
            if (eIdx >= 0) libraryTracks.remove(eIdx);

            libraryTracks.add(injectionIndex++, customTrackResult);
        }


        // Fetch Cloud Playlists if signed in
        if (AuthManager.getInstance(requireContext()).isSignedIn()) {
            final int finalInjectionIdx = injectionIndex;
            CloudSyncManager.getInstance(requireContext()).fetchCloudPlaylists(cloudMap -> {
                if (!isAdded()) return;
                mainHandler.post(() -> {
                    if (!isAdded()) return;
                    for (Map.Entry<String, ? extends List<FavoritesPlaylistStore.FavoriteTrack>> entry : cloudMap.entrySet()) {
                        String name = entry.getKey();
                        String contentId = CustomPlaylistsStore.CUSTOM_PLAYLIST_PREFIX + name;
                        
                        // Check if already in library
                        boolean alreadyHandled = false;
                        for (YouTubeMusicService.TrackResult r : libraryTracks) {
                            if (TextUtils.equals(r.contentId, contentId)) {
                                alreadyHandled = true;
                                break;
                            }
                        }
                        if (alreadyHandled) continue;

                        List<FavoritesPlaylistStore.FavoriteTrack> tracks = entry.getValue();
                        String sub = tracks.size() == 1 ? "1 canción" : tracks.size() + " canciones";
                        String thumb = "";
                        for (FavoritesPlaylistStore.FavoriteTrack t : tracks) {
                            if (!TextUtils.isEmpty(t.imageUrl)) { thumb = t.imageUrl; break; }
                        }

                        YouTubeMusicService.TrackResult cloudTr = new YouTubeMusicService.TrackResult(
                            "playlist", contentId, name, sub, thumb
                        );
                        libraryTracks.add(finalInjectionIdx, cloudTr);
                        if (adapter != null) adapter.notifyDataSetChanged();
                    }
                });
            });
        }
    }

    private void refreshCurrentPlayingPlaylistState() {
        if (!isAdded()) {
            return;
        }

        boolean previousActive = currentPlayingPlaylistActive;
        String previousPlaylistId = currentPlayingPlaylistId;

        SharedPreferences prefs = requireContext().getSharedPreferences(PREFS_PLAYER_STATE, Activity.MODE_PRIVATE);
        boolean isPlaying = prefs.getBoolean(PREF_LAST_IS_PLAYING, false);
        String playlistId = prefs.getString(PREF_LAST_PLAYLIST_ID, "");
        String normalizedPlaylistId = playlistId == null ? "" : playlistId.trim();
        if (!isPlaying || TextUtils.isEmpty(normalizedPlaylistId)) {
            normalizedPlaylistId = "";
        }

        boolean changed = currentPlayingPlaylistActive != isPlaying
                || !TextUtils.equals(currentPlayingPlaylistId, normalizedPlaylistId);
        currentPlayingPlaylistActive = isPlaying;
        currentPlayingPlaylistId = normalizedPlaylistId;

        if (changed && adapter != null) {
            adapter.notifyPlayingPlaylistChanged(
                    previousActive ? previousPlaylistId : "",
                    currentPlayingPlaylistActive ? currentPlayingPlaylistId : ""
            );
        }
    }

    private boolean isPlaylistCurrentlyPlaying(@Nullable String playlistId) {
        if (!currentPlayingPlaylistActive || TextUtils.isEmpty(playlistId)) {
            return false;
        }
        return TextUtils.equals(currentPlayingPlaylistId, playlistId == null ? "" : playlistId.trim());
    }

    private LinearLayout llSearchRow;
    private View llLibraryInlineSearch;

    private View llMusicState;
    private View hsvFilterChips;
    private View llSearchOverlay;
    private ChipGroup cgSearchSuggestions;
    private ImageView ivLibraryQuickClear;
    private TextInputEditText etMusicQuery;
    private TextInputEditText etLibraryQuickSearch;
    private MaterialButton btnYoutubeLogin;
    private LinearLayout llLibraryEmptyState;
    private ProgressBar progressMusic;
    private TextView tvMusicState;
    private RecyclerView rvMusicResults;
    private LinearLayout llFeaturedResult;
    private ImageView ivFeaturedThumb;
    private TextView tvFeaturedTitle;
    private TextView tvFeaturedSubtitle;
    private MaterialButton btnFeaturedPlay;
    private MaterialButton btnFeaturedSave;
    private MaterialButton btnChipVideos;
    private MaterialButton btnChipSongs;
    private MaterialButton btnChipArtists;
    private MaterialButton btnChipPlaylists;
    private SwipeRefreshLayout swipeLibraryRefresh;
    private TextView tvLibraryTitle;
    private View btnSearchMusic;

    private final YouTubeMusicService youTubeMusicService = new YouTubeMusicService();
    private final ExecutorService offlinePrefetchExecutor = Executors.newSingleThreadExecutor();
    private final YouTubeMusicService offlinePrefetchService = new YouTubeMusicService(offlinePrefetchExecutor);
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private final ExecutorService authExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService offlineStateExecutor = Executors.newSingleThreadExecutor();
    private final List<YouTubeMusicService.TrackResult> tracks = new ArrayList<>();
    private final List<YouTubeMusicService.TrackResult> allTracks = new ArrayList<>();
    private final List<YouTubeMusicService.TrackResult> libraryTracks = new ArrayList<>();
    private final Map<String, String> normalizedFilterCache = new HashMap<>();
    private final Set<String> offlinePrefetchInFlight = new HashSet<>();
    private final Object cachedPlaylistTracksLock = new Object();
    private final Map<String, ArrayList<CachedPlaylistTrack>> cachedPlaylistTracksById = new HashMap<>();
    private final Map<String, Long> cachedPlaylistTracksUpdatedAtById = new HashMap<>();
    private final List<String> recentSearchQueries = new ArrayList<>();
    private MusicResultsAdapter adapter;
    private LinearLayoutManager musicResultsLayoutManager;
    private boolean searching;
    private boolean searchPaginationInFlight;
    private boolean hasMoreSearchPages;
    @NonNull
    private String nextSearchPageToken = "";
    @NonNull
    private String activeSearchQuery = "";
    private boolean loadingLibrary;
    @Nullable
    private Runnable pendingLibraryInlineSearchRunnable;
    @Nullable
    private String queuedSearchQuery;
    private boolean queuedSearchFromUser;
    private boolean queuedSearchKeepLibraryLayout;
    private long latestSearchRequestId;
    @NonNull
    private String lastLibraryInlineDispatchedQuery = "";
    private boolean libraryInlineManualModeActive;
    private boolean libraryInlinePendingReveal;
    @Nullable
    private GoogleSignInClient googleSignInClient;
    @Nullable
    private GoogleSignInAccount pendingRecoverableAccount;
    private boolean pendingWebSessionAuthRecovery;
    private boolean pendingWebSessionUserTriggered;
    private boolean pendingWebSessionBackgroundRefresh;
    private boolean pendingWebSessionForceRefresh;
    @Nullable
    private YouTubeMusicService.TrackResult featuredTrack;
    private String youtubeAccessToken = "";
    private boolean youtubeAuthRefreshInProgress;
    private boolean streamingOauthCompleted;
    private long lastAutoWebSessionLaunchAtMs;
    @Nullable
    private PlaybackHistoryStore.Snapshot miniSnapshotCache;
    @Nullable
    private PopupWindow playlistActionPopupWindow;
    private long miniSnapshotCacheReadAtMs;
    private boolean restoringHiddenMiniPlayerFromSnapshot;
    @NonNull
    private String streamingOauthAccountEmail = "";
    @NonNull
    private String lastMiniArtworkTrackId = "";
    @NonNull
    private String lastMiniArtworkUrl = "";
    private int lastMiniProgressValue = -1;
    @NonNull
    private String currentPlayingPlaylistId = "";
    private boolean currentPlayingPlaylistActive;
    @Nullable
    private Observer<List<WorkInfo>> offlineQueueObserver;
    @Nullable
    private Observer<List<WorkInfo>> offlineManualQueueObserver;
    private boolean offlineAutoQueueScanInFlight;
    private boolean offlineQueueHadActiveWork;
    private boolean offlineManualQueueHadActiveWork;
    @Nullable
    private OnBackPressedCallback clearSearchBackPressedCallback;

    private ActivityResultLauncher<Intent> signInLauncher;
    private ActivityResultLauncher<Intent> recoverAuthLauncher;
    private ActivityResultLauncher<Intent> webSessionLauncher;
    private ActivityResultLauncher<Intent> searchActivityLauncher;

    private enum ScreenMode {
        SEARCH,
        LIBRARY
    }

    private ScreenMode activeScreen = ScreenMode.LIBRARY;

    private enum ChipFilter {
        VIDEOS,
        SONGS,
        ARTISTS,
        PLAYLISTS
    }

    private interface OnLibraryTrackClick {
        void onTrackClick(@NonNull YouTubeMusicService.TrackResult track);
    }

    private interface OnLibraryTrackMoreClick {
        void onTrackMoreClick(@NonNull YouTubeMusicService.TrackResult track, @NonNull View anchor);
    }

    private ChipFilter activeFilter = ChipFilter.SONGS;

    private final RecyclerView.OnScrollListener searchPaginationScrollListener = new RecyclerView.OnScrollListener() {
        @Override
        public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
            if (dy <= 0) {
                return;
            }
            if (activeScreen != ScreenMode.SEARCH
                    || musicResultsLayoutManager == null
                    || searchPaginationInFlight
                    || searching
                    || !hasMoreSearchPages
                    || TextUtils.isEmpty(activeSearchQuery)) {
                return;
            }

            int totalItems = musicResultsLayoutManager.getItemCount();
            if (totalItems <= 0) {
                return;
            }

            int lastVisible = musicResultsLayoutManager.findLastVisibleItemPosition();
            if (lastVisible >= (totalItems - SEARCH_SCROLL_LOAD_MORE_THRESHOLD)) {
                loadMoreSearchResultsIfNeeded();
            }
        }
    };

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        signInLauncher = registerForActivityResult(
                new androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult(),
                result -> handleGoogleSignInResult(result.getData())
        );

        recoverAuthLauncher = registerForActivityResult(
                new androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult(),
                result -> {
                    GoogleSignInAccount account = pendingRecoverableAccount;
                    boolean retryWebSessionFlow = pendingWebSessionAuthRecovery;
                    boolean retryUserTriggered = pendingWebSessionUserTriggered;
                    boolean retryBackgroundRefresh = pendingWebSessionBackgroundRefresh;
                    boolean retryForceRefresh = pendingWebSessionForceRefresh;
                    clearPendingRecoverableAuthState();
                    pendingRecoverableAccount = null;

                    if (result.getResultCode() == Activity.RESULT_OK) {
                        if (account != null) {
                            requestYoutubeAccessToken(account, true);
                            return;
                        }
                        if (retryWebSessionFlow) {
                            requestYoutubeAccessTokenFromPrimaryAccountAfterWebSession(
                                    retryUserTriggered,
                                    retryBackgroundRefresh,
                                    retryForceRefresh
                            );
                            return;
                        }
                    }

                    setLibraryLoading(false, "Permiso de YouTube no concedido.");
                }
        );

        webSessionLauncher = registerForActivityResult(
                new androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (!isAdded()) {
                        return;
                    }
                    if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null) {
                        setLibraryLoading(false, "Sesion web cancelada.");
                        return;
                    }
                    handleWebSessionAuthSuccess(result.getData());
                }
        );

        searchActivityLauncher = registerForActivityResult(
                new androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (!isAdded()) {
                        return;
                    }
                    if (result.getData() == null) {
                        return;
                    }
                    handleSearchActivityResult(result.getResultCode(), result.getData());
                }
        );
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_music_player, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        llLibraryInlineSearch = view.findViewById(R.id.llLibraryInlineSearch);

        llMusicState = view.findViewById(R.id.llMusicState);
        ivLibraryQuickClear = view.findViewById(R.id.ivLibraryQuickClear);
        etLibraryQuickSearch = view.findViewById(R.id.etLibraryQuickSearch);
        btnYoutubeLogin = view.findViewById(R.id.btnYoutubeLogin);
        llLibraryEmptyState = view.findViewById(R.id.llLibraryEmptyState);
        progressMusic = view.findViewById(R.id.progressMusic);
        tvMusicState = view.findViewById(R.id.tvMusicState);
        rvMusicResults = view.findViewById(R.id.rvMusicResults);
        llFeaturedResult = view.findViewById(R.id.llFeaturedResult);
        ivFeaturedThumb = view.findViewById(R.id.ivFeaturedThumb);
        tvFeaturedTitle = view.findViewById(R.id.tvFeaturedTitle);
        tvFeaturedSubtitle = view.findViewById(R.id.tvFeaturedSubtitle);
        btnFeaturedPlay = view.findViewById(R.id.btnFeaturedPlay);
        btnFeaturedSave = view.findViewById(R.id.btnFeaturedSave);
        swipeLibraryRefresh = view.findViewById(R.id.swipeLibraryRefresh);

        llSearchOverlay = view.findViewById(R.id.llSearchOverlay);
        llSearchRow = view.findViewById(R.id.llSearchRow);
        cgSearchSuggestions = view.findViewById(R.id.cgSearchSuggestions);
        etMusicQuery = view.findViewById(R.id.etMusicQuery);
        hsvFilterChips = view.findViewById(R.id.hsvFilterChips);
        btnChipVideos = view.findViewById(R.id.btnChipVideos);
        btnChipSongs = view.findViewById(R.id.btnChipSongs);
        btnChipArtists = view.findViewById(R.id.btnChipArtists);
        btnChipPlaylists = view.findViewById(R.id.btnChipPlaylists);

        View fabAddPlaylist = view.findViewById(R.id.fabAddPlaylist);
        if (fabAddPlaylist != null) {
            fabAddPlaylist.setOnClickListener(v -> showCreatePlaylistDialog());
        }

        adapter = new MusicResultsAdapter(this::openTrack, this::onLibraryPlaylistMorePressed);
        musicResultsLayoutManager = new LinearLayoutManager(requireContext());
        rvMusicResults.setLayoutManager(musicResultsLayoutManager);
        rvMusicResults.setAdapter(adapter);
        rvMusicResults.addOnScrollListener(searchPaginationScrollListener);

        rebuildGoogleSignInClient();
        restoreCachedStreamingSessionState();
        restoreRecentSearchQueries();
        ensureFavoritesPlaylistInLibraryTracks();
        refreshCurrentPlayingPlaylistState();
        setupLibraryPullToRefresh();
        startObservingOfflineQueue();

        llFeaturedResult.setVisibility(View.GONE);
        btnYoutubeLogin.setVisibility(View.GONE);

        btnYoutubeLogin.setOnClickListener(v -> onYoutubeLoginClicked());

        if (btnChipVideos != null) btnChipVideos.setOnClickListener(v -> setActiveFilter(ChipFilter.VIDEOS));
        if (btnChipSongs != null) btnChipSongs.setOnClickListener(v -> setActiveFilter(ChipFilter.SONGS));
        if (btnChipArtists != null) btnChipArtists.setOnClickListener(v -> setActiveFilter(ChipFilter.ARTISTS));
        if (btnChipPlaylists != null) btnChipPlaylists.setOnClickListener(v -> setActiveFilter(ChipFilter.PLAYLISTS));

        btnFeaturedPlay.setOnClickListener(v -> {
            if (featuredTrack != null) {
                openTrack(featuredTrack);
            }
        });

        btnFeaturedSave.setOnClickListener(v -> {
            if (featuredTrack == null) {
                return;
            }
            shareTrackResult(featuredTrack);
        });

        if (etMusicQuery != null) {
            etMusicQuery.setFocusable(false);
            etMusicQuery.setFocusableInTouchMode(false);
            etMusicQuery.setOnClickListener(v -> {
                launchSearchActivity();
            });
            etMusicQuery.setOnEditorActionListener((textView, actionId, event) -> {
                launchSearchActivity();
                return true;
            });
        }

        if (etLibraryQuickSearch != null) {
            etLibraryQuickSearch.setFocusable(false);
            etLibraryQuickSearch.setFocusableInTouchMode(false);
            etLibraryQuickSearch.setOnClickListener(v -> {
                launchSearchActivity();
            });
        }

        if (ivLibraryQuickClear != null) {
            ivLibraryQuickClear.setOnClickListener(v -> {
                if (etLibraryQuickSearch == null) {
                    return;
                }
                etLibraryQuickSearch.setText("");
                etLibraryQuickSearch.clearFocus();
                updateLibraryInlineClearButton();
                applyLibraryInlineSongSearch();
                updateClearSearchBackPressedEnabled();
            });
        }

        setupBackPressToClearSearchInput();

        applyChipStyles();
        updateYoutubeButtonLabel();
        refreshSearchSuggestions("");
        switchScreen(ScreenMode.LIBRARY);
        if (!isPlaylistDetailStatePending()) {
            persistStreamingScreen(STREAM_SCREEN_LIBRARY);
        }
        updateClearSearchBackPressedEnabled();
        maybeAutoLaunchWebSessionIfNeeded();
    }

    @Override
    public void onResume() {
        super.onResume();
        startObservingOfflineQueue();
        offlineQueueHadActiveWork = false;
        offlineManualQueueHadActiveWork = false;
        updateClearSearchBackPressedEnabled();
        ensureFavoritesPlaylistInLibraryTracks();
        refreshCurrentPlayingPlaylistState();
        if (adapter != null) {
            adapter.invalidatePlaylistOfflineState(null);
        }
        maybeEnqueueNextOfflineAutoPlaylist();
        maybeAutoLaunchWebSessionIfNeeded();
        
        if (getActivity() instanceof MainActivity) {
            MainActivity mainActivity = (MainActivity) getActivity();
            mainActivity.revealModuleContent();
        }
    }

    @Override
    public void onPause() {
        if (clearSearchBackPressedCallback != null) {
            clearSearchBackPressedCallback.setEnabled(false);
        }
        super.onPause();
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        updateClearSearchBackPressedEnabled();
        if (hidden) {
            return;
        }
        startObservingOfflineQueue();
        ensureFavoritesPlaylistInLibraryTracks();
        refreshCurrentPlayingPlaylistState();
        if (adapter != null) {
            adapter.invalidatePlaylistOfflineState(null);
        }
        maybeEnqueueNextOfflineAutoPlaylist();
        maybeAutoLaunchWebSessionIfNeeded();
    }

    private void maybeAutoLaunchWebSessionIfNeeded() {
        if (!isAdded()) {
            return;
        }
        if (streamingOauthCompleted || loadingLibrary || webSessionLauncher == null) {
            return;
        }

        // Throttle auto-launch to avoid spamming the user if they cancel or if something fails
        long now = System.currentTimeMillis();
        if (now - lastAutoWebSessionLaunchAtMs < 5000) {
            return;
        }
        lastAutoWebSessionLaunchAtMs = now;
        if (!isNetworkAvailable()) {
            return;
        }

        long now = System.currentTimeMillis();
        if ((now - lastAutoWebSessionLaunchAtMs) < 3000L) {
            return;
        }

        lastAutoWebSessionLaunchAtMs = now;
        onYoutubeLoginClicked();
    }

    private boolean isPlaylistDetailStatePending() {
        if (!isAdded()) {
            return false;
        }
        SharedPreferences prefs = requireContext().getSharedPreferences(PREFS_PLAYER_STATE, Activity.MODE_PRIVATE);
        String lastScreen = prefs.getString(PREF_LAST_STREAM_SCREEN, STREAM_SCREEN_LIBRARY);
        return TextUtils.equals(STREAM_SCREEN_PLAYLIST_DETAIL, lastScreen);
    }

    private void persistStreamingScreen(@NonNull String screen) {
        if (!isAdded()) {
            return;
        }
        requireContext()
                .getSharedPreferences(PREFS_PLAYER_STATE, Activity.MODE_PRIVATE)
                .edit()
                .putString(PREF_LAST_STREAM_SCREEN, screen)
                .apply();
    }

    private void switchScreen(@NonNull ScreenMode mode) {
        activeScreen = mode;
        updateLibraryPullRefreshAvailability();
        updateClearSearchBackPressedEnabled();
        if (adapter != null) {
            adapter.setSearchMode(mode == ScreenMode.SEARCH
                    || (mode == ScreenMode.LIBRARY && !TextUtils.isEmpty(getLibraryInlineQuery())));
        }

        boolean searchVisible = mode == ScreenMode.SEARCH;
        if (llSearchOverlay != null) llSearchOverlay.setVisibility(searchVisible ? View.VISIBLE : View.GONE);
        if (llSearchRow != null) llSearchRow.setVisibility(searchVisible ? View.VISIBLE : View.GONE);
        if (hsvFilterChips != null) hsvFilterChips.setVisibility(searchVisible ? View.VISIBLE : View.GONE);
        if (cgSearchSuggestions != null) cgSearchSuggestions.setVisibility(searchVisible ? View.VISIBLE : View.GONE);
        llLibraryEmptyState.setVisibility(View.GONE);
        if (llLibraryInlineSearch != null) {
            llLibraryInlineSearch.setVisibility(searchVisible ? View.GONE : View.VISIBLE);
        }
        updateLibraryInlineClearButton();

        updateYoutubeButtonLabel();

        if (searchVisible) {
            String query = etMusicQuery.getText() != null ? etMusicQuery.getText().toString().trim() : "";
            refreshSearchSuggestions(query);
            if (!TextUtils.isEmpty(query)
                    && !allTracks.isEmpty()
                    && TextUtils.equals(query, activeSearchQuery)) {
                applyActiveFilter(query);
            } else {
                resetSearchResultsForTypingState(query);
            }
            return;
        }

        llFeaturedResult.setVisibility(View.GONE);
        String inlineQuery = getLibraryInlineQuery();
        if (!TextUtils.isEmpty(inlineQuery)) {
            libraryInlineManualModeActive = true;
            showLibraryInlineBlankState();
            return;
        } else if (!libraryTracks.isEmpty()) {
            libraryInlineManualModeActive = false;
            renderLibraryResults();
        } else {
            libraryInlineManualModeActive = false;
            adapter.submitResults(new ArrayList<>());
            tvMusicState.setText("");
            showLibraryEmptyState();
        }

        maybeSyncLibraryIfAuthorized();
    }

    private void maybeSyncLibraryIfAuthorized() {
        if (!isAdded() || loadingLibrary || youtubeAuthRefreshInProgress || !streamingOauthCompleted) {
            return;
        }
        if (!isNetworkAvailable()) {
            return;
        }

        long now = System.currentTimeMillis();
        boolean hasLocalData = !libraryTracks.isEmpty();
        if (hasLocalData && (now - lastLibrarySyncAtMs) < LIBRARY_REFRESH_MIN_INTERVAL_MS) {
            return;
        }

        String accountKey = resolveAccountKey();
        boolean backgroundRefresh = hasLocalData;
        if (hasValidCachedToken(accountKey)) {
            youtubeAccessToken = cachedYoutubeAccessToken;
            fetchLibraryPlaylists(false, backgroundRefresh, false);
            return;
        }

        if (!TextUtils.isEmpty(youtubeAccessToken)) {
            fetchLibraryPlaylists(false, backgroundRefresh, false);
        }
    }

    private void setupLibraryPullToRefresh() {
        if (swipeLibraryRefresh == null || !isAdded()) {
            return;
        }
        swipeLibraryRefresh.setColorSchemeColors(
                ContextCompat.getColor(requireContext(), R.color.stitch_blue),
                ContextCompat.getColor(requireContext(), android.R.color.white)
        );
        swipeLibraryRefresh.setProgressBackgroundColorSchemeColor(
                ContextCompat.getColor(requireContext(), R.color.surface_low)
        );
        swipeLibraryRefresh.setDistanceToTriggerSync(dpToPx(80));
        swipeLibraryRefresh.setOnRefreshListener(this::triggerLibraryPullRefresh);
        updateLibraryPullRefreshAvailability();
    }

    private void updateLibraryPullRefreshAvailability() {
        if (swipeLibraryRefresh == null) {
            return;
        }
        swipeLibraryRefresh.setEnabled(activeScreen == ScreenMode.LIBRARY);
    }

    private void setLibraryPullRefreshState(boolean refreshing) {
        if (swipeLibraryRefresh == null) {
            return;
        }
        if (swipeLibraryRefresh.isRefreshing() == refreshing) {
            return;
        }
        swipeLibraryRefresh.setRefreshing(refreshing);
    }

    private void triggerLibraryPullRefresh() {
        if (!isAdded()) {
            setLibraryPullRefreshState(false);
            return;
        }
        if (activeScreen != ScreenMode.LIBRARY) {
            setLibraryPullRefreshState(false);
            return;
        }

        setLibraryPullRefreshState(true);
        lastLibrarySyncAtMs = 0L;

        if (!isNetworkAvailable()) {
            setLibraryLoading(false, "Sin internet. No se pudo refrescar la biblioteca.");
            return;
        }

        if (!streamingOauthCompleted) {
            onYoutubeLoginClicked();
            return;
        }

        if (!TextUtils.isEmpty(youtubeAccessToken)) {
            fetchLibraryPlaylists(true, false, true);
            return;
        }

        String accountKey = resolveAccountKey();
        if (hasValidCachedToken(accountKey)) {
            youtubeAccessToken = cachedYoutubeAccessToken;
            fetchLibraryPlaylists(true, false, true);
            return;
        }

        requestYoutubeAccessTokenFromPrimaryAccountAfterWebSession(true, false, true);
    }

    private void setupBackPressToClearSearchInput() {
        if (!isAdded()) {
            return;
        }

        clearSearchBackPressedCallback = new OnBackPressedCallback(false) {
            @Override
            public void handleOnBackPressed() {
                if (clearSearchInputsIfAny()) {
                    updateClearSearchBackPressedEnabled();
                    return;
                }

                setEnabled(false);
                requireActivity().getOnBackPressedDispatcher().onBackPressed();
                setEnabled(true);
            }
        };

        requireActivity().getOnBackPressedDispatcher().addCallback(
                getViewLifecycleOwner(),
                clearSearchBackPressedCallback
        );
    }

    private void updateClearSearchBackPressedEnabled() {
        if (clearSearchBackPressedCallback == null) {
            return;
        }
        boolean shouldEnable = isResumed()
                && !isHidden()
                && (hasAnySearchInputText() || isLibraryInlineAwaitingSubmitState());
        clearSearchBackPressedCallback.setEnabled(shouldEnable);
    }

    private boolean isLibraryInlineAwaitingSubmitState() {
        return activeScreen == ScreenMode.LIBRARY
                && libraryInlineManualModeActive
                && etLibraryQuickSearch != null
                && etLibraryQuickSearch.hasFocus();
    }

    private boolean hasAnySearchInputText() {
        boolean hasLibraryQuery = etLibraryQuickSearch != null
                && etLibraryQuickSearch.getText() != null
                && !TextUtils.isEmpty(etLibraryQuickSearch.getText().toString().trim());
        boolean hasSearchQuery = etMusicQuery != null
                && etMusicQuery.getText() != null
                && !TextUtils.isEmpty(etMusicQuery.getText().toString().trim());
        return hasLibraryQuery || hasSearchQuery;
    }

    private boolean clearSearchInputsIfAny() {
        boolean cleared = false;

        if (etLibraryQuickSearch != null
                && etLibraryQuickSearch.getText() != null
                && !TextUtils.isEmpty(etLibraryQuickSearch.getText().toString().trim())) {
            etLibraryQuickSearch.setText("");
            etLibraryQuickSearch.clearFocus();
            updateLibraryInlineClearButton();
            applyLibraryInlineSongSearch();
            cleared = true;
        }

        if (etMusicQuery != null
                && etMusicQuery.getText() != null
                && !TextUtils.isEmpty(etMusicQuery.getText().toString().trim())) {
            etMusicQuery.setText("");
            etMusicQuery.clearFocus();
            allTracks.clear();
            tracks.clear();
            featuredTrack = null;
            if (llFeaturedResult != null) {
                llFeaturedResult.setVisibility(View.GONE);
            }
            if (adapter != null) {
                adapter.submitResults(new ArrayList<>());
            }
            if (tvMusicState != null && activeScreen == ScreenMode.SEARCH) {
                tvMusicState.setText("Escribe y busca para cargar resultados.");
            }
            cleared = true;
        }

        if (!cleared && activeScreen == ScreenMode.LIBRARY && libraryInlineManualModeActive) {
            if (etLibraryQuickSearch != null) {
                etLibraryQuickSearch.setText("");
                etLibraryQuickSearch.clearFocus();
            }
            libraryInlineManualModeActive = false;
            libraryInlinePendingReveal = false;
            renderLibraryResults();
            cleared = true;
        }

        return cleared;
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    private void restoreCachedStreamingSessionState() {
        if (!isAdded()) {
            return;
        }
        SharedPreferences prefs = requireContext()
                .getSharedPreferences(PREFS_PLAYER_STATE, Activity.MODE_PRIVATE);
        String cookie = prefs.getString(PREF_LAST_YOUTUBE_WEB_COOKIE, "");
        String email = prefs.getString(PREF_LAST_STREAMING_OAUTH_ACCOUNT_EMAIL, "");

        if (!TextUtils.isEmpty(cookie)) {
            streamingOauthCompleted = true;
            streamingOauthAccountEmail = email;
            updateYoutubeButtonLabel();
            Log.i(TAG_STREAMING, "restored_streaming_session cookieLength=" + cookie.length() + " email=" + email);
        } else {
            Log.i(TAG_STREAMING, "no_cached_streaming_session_found");
        }
    }

    private void rebuildGoogleSignInClient() {
        Scope ytScope = new Scope(youTubeMusicService.getYoutubeReadonlyScope());
        
        String serverClientId = "";
        try {
            int resId = requireContext().getResources().getIdentifier(
                    "default_web_client_id",
                    "string",
                    requireContext().getPackageName()
            );
            if (resId != 0) {
                serverClientId = getString(resId);
            }
        } catch (Exception ignored) {}

        GoogleSignInOptions.Builder optionsBuilder = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .requestScopes(ytScope);

        if (!TextUtils.isEmpty(serverClientId)) {
            optionsBuilder.requestIdToken(serverClientId);
            optionsBuilder.requestServerAuthCode(serverClientId);
        }

        String preferredEmail = getPrimaryAppGoogleEmail();
        if (!TextUtils.isEmpty(preferredEmail)) {
            optionsBuilder.setAccountName(preferredEmail);
        }

        googleSignInClient = GoogleSignIn.getClient(requireContext(), optionsBuilder.build());
    }

    @NonNull
    private String getPrimaryAppGoogleEmail() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null || TextUtils.isEmpty(user.getEmail())) {
            return "";
        }
        return user.getEmail().trim();
    }

    private boolean isMatchingPrimaryAppGoogleAccount(@NonNull GoogleSignInAccount account) {
        String appEmail = getPrimaryAppGoogleEmail();
        if (TextUtils.isEmpty(appEmail)) {
            return true;
        }

        String accountEmail = account.getEmail();
        if (TextUtils.isEmpty(accountEmail)) {
            return false;
        }
        return appEmail.equalsIgnoreCase(accountEmail.trim());
    }

    private void onYoutubeLoginClicked() {
        if (!isAdded() || loadingLibrary || youtubeAuthRefreshInProgress) {
            return;
        }

        if (!isNetworkAvailable()) {
            setLibraryLoading(false, "Sin conexion a internet.");
            return;
        }

        Log.i(TAG_STREAMING, "login:web_session_launch requested");

        streamingOauthCompleted = false;
        youtubeAccessToken = "";
        clearCachedToken();
        if (isAdded()) {
            requireContext()
                .getSharedPreferences(PREFS_PLAYER_STATE, Activity.MODE_PRIVATE)
                .edit()
                .remove(PREF_LAST_YOUTUBE_WEB_COOKIE)
                .apply();
        }
        updateYoutubeButtonLabel();
        setLibraryLoading(true, "Abriendo sesion web de YouTube Music...");

        if (webSessionLauncher == null) {
            setLibraryLoading(false, "No se pudo abrir el mininavegador de sesion.");
            return;
        }

        Intent intent = new Intent(requireContext(), YouTubeMusicWebSessionActivity.class);
        webSessionLauncher.launch(intent);
    }

    private void handleWebSessionAuthSuccess(@NonNull Intent data) {
        String cookieHeader = data.getStringExtra(YouTubeMusicWebSessionActivity.EXTRA_SESSION_COOKIE_HEADER);
        String userAgent = data.getStringExtra(YouTubeMusicWebSessionActivity.EXTRA_SESSION_USER_AGENT);
        if (TextUtils.isEmpty(cookieHeader)) {
            Log.w(TAG_STREAMING, "web_session_result missing_cookie_header");
            setLibraryLoading(false, "No se detecto sesion web valida.");
            return;
        }

        Log.i(
                TAG_STREAMING,
                "web_session_result authenticated cookieLength=" + cookieHeader.length()
        );

        if (isAdded()) {
            requireContext()
                    .getSharedPreferences(PREFS_PLAYER_STATE, Activity.MODE_PRIVATE)
                    .edit()
                    .putString(PREF_LAST_YOUTUBE_WEB_COOKIE, cookieHeader)
                    .apply();
        }

        streamingOauthCompleted = true;
        updateYoutubeButtonLabel();

        // Keep user-agent available for future web-session API extensions.
        if (TextUtils.isEmpty(userAgent)) {
            userAgent = "";
        }

        requestYoutubeAccessTokenFromPrimaryAccountAfterWebSession(true, false, true);
    }

    private void requestYoutubeAccessTokenFromPrimaryAccountAfterWebSession(
            boolean userTriggered,
            boolean backgroundRefresh,
            boolean forceRefresh
    ) {
        if (!isAdded()) {
            return;
        }

        StreamingAccountCandidate accountCandidate = resolveStreamingOAuthAccountCandidate();
        if (accountCandidate == null || TextUtils.isEmpty(accountCandidate.email)) {
            Log.w(TAG_STREAMING, "oauth_account unresolved; trying account chooser fallback");
            if (googleSignInClient != null) {
                setLibraryLoading(true, "Selecciona tu cuenta Google para Streaming...");
                launchGoogleSignInChooser(false);
                return;
            }
            setLibraryLoading(false, "No se encontro cuenta Google para validar YouTube.");
            return;
        }

        Log.i(
                TAG_STREAMING,
                "oauth_account selected email=" + accountCandidate.email + " source=" + accountCandidate.source
        );

        if (!backgroundRefresh) {
            setLibraryLoading(true, "Validando sesion web y permisos YouTube...");
        }

        Account resolvedAccount = new Account(accountCandidate.email, "com.google");
        android.content.Context appContext = requireContext().getApplicationContext();
        authExecutor.execute(() -> {
            try {
                String scope = "oauth2:" + youTubeMusicService.getYoutubeReadonlyScope();
                String token = GoogleAuthUtil.getToken(appContext, resolvedAccount, scope);
                if (TextUtils.isEmpty(token)) {
                    throw new IllegalStateException("No se recibio token OAuth.");
                }

                String finalToken = token;
                mainHandler.post(() -> {
                    streamingOauthAccountEmail = accountCandidate.email;
                    rememberStreamingOauthAccountEmail(accountCandidate.email);
                    youtubeAccessToken = finalToken;
                    streamingOauthCompleted = true;
                    cacheTokenForCurrentAccount(finalToken);
                    updateYoutubeButtonLabel();
                    Log.i(TAG_STREAMING, "oauth_token acquired; loading playlists");
                    fetchLibraryPlaylists(userTriggered, backgroundRefresh, forceRefresh);
                });
            } catch (UserRecoverableAuthException recoverableException) {
                mainHandler.post(() -> {
                    youtubeAuthRefreshInProgress = false;
                    Log.w(TAG_STREAMING, "oauth_token requires user consent");
                    setLibraryLoading(false, "Sesion web lista. Falta permiso OAuth de YouTube.");
                    Intent recoverIntent = recoverableException.getIntent();
                    if (recoverIntent != null) {
                        try {
                            pendingWebSessionAuthRecovery = true;
                            pendingWebSessionUserTriggered = userTriggered;
                            pendingWebSessionBackgroundRefresh = backgroundRefresh;
                            pendingWebSessionForceRefresh = forceRefresh;
                            recoverAuthLauncher.launch(recoverIntent);
                        } catch (Exception launchError) {
                            clearPendingRecoverableAuthState();
                            try {
                                startActivity(recoverIntent);
                            } catch (Exception ignored) {
                            }
                        }
                    }
                });
            } catch (IOException | GoogleAuthException | IllegalStateException e) {
                mainHandler.post(() -> {
                    youtubeAuthRefreshInProgress = false;
                    Log.e(TAG_STREAMING, "oauth_token failed: " + e.getMessage());
                    setLibraryLoading(false, "No se pudo validar YouTube: " + e.getMessage());
                    if (userTriggered && isAdded()) {
                        
                    }
                });
            }
        });
    }

    private void forcePrimaryAccountReSignIn() {
        if (googleSignInClient == null) {
            setLibraryLoading(false, "No se pudo sincronizar la cuenta de YouTube.");
            return;
        }

        setLibraryLoading(true, "Usando la misma cuenta con la que entraste a la app...");
        launchGoogleSignInChooser(true);
    }

    private void launchGoogleSignInChooser(boolean forceFreshSelection) {
        if (googleSignInClient == null) {
            setLibraryLoading(false, "No se pudo abrir Google Sign-In.");
            return;
        }

        Runnable launchIntent = () -> {
            if (!isAdded()) {
                return;
            }
            signInLauncher.launch(googleSignInClient.getSignInIntent());
        };

        if (!forceFreshSelection) {
            launchIntent.run();
            return;
        }

        googleSignInClient.signOut().addOnCompleteListener(task -> launchIntent.run());
    }

    private void handleGoogleSignInResult(@Nullable Intent data) {
        try {
            GoogleSignInAccount account = GoogleSignIn.getSignedInAccountFromIntent(data).getResult(ApiException.class);
            if (account == null) {
                setLibraryLoading(false, "No se pudo obtener la cuenta.");
                return;
            }

            if (!isMatchingPrimaryAppGoogleAccount(account)) {
                if (googleSignInClient != null) {
                    googleSignInClient.signOut();
                }
                setLibraryLoading(false, "Selecciona la misma cuenta Google con la que iniciaste sesion en la app.");
                
                return;
            }

            requestYoutubeAccessToken(account, true);
        } catch (ApiException e) {
            String failureMessage = buildGoogleSignInFailureMessage(e);
            setLibraryLoading(false, failureMessage);
            if (isAdded()) {
                
            }
        }
    }

    @NonNull
    private String buildGoogleSignInFailureMessage(@NonNull ApiException exception) {
        int statusCode = exception.getStatusCode();
        if (statusCode == GoogleSignInStatusCodes.SIGN_IN_CANCELLED) {
            return "Inicio de sesion cancelado.";
        }
        if (statusCode == GoogleSignInStatusCodes.SIGN_IN_CURRENTLY_IN_PROGRESS) {
            return "Google Sign-In sigue en progreso. Intenta de nuevo en unos segundos.";
        }
        if (statusCode == GoogleSignInStatusCodes.SIGN_IN_FAILED) {
            return "No se pudo completar Google Sign-In en este dispositivo.";
        }
        if (statusCode == CommonStatusCodes.NETWORK_ERROR) {
            return "Sin conexion a internet. Revisa tu red e intenta de nuevo.";
        }
        if (statusCode == CommonStatusCodes.DEVELOPER_ERROR) {
            return "Configuracion OAuth invalida para Streaming (DEVELOPER_ERROR). Revisa SHA y client ID en Google Cloud.";
        }
        return "No se pudo iniciar sesion con Google (codigo " + statusCode + ").";
    }

    private void requestYoutubeAccessToken(@NonNull GoogleSignInAccount account, boolean userTriggered) {
        requestYoutubeAccessToken(account, userTriggered, false, false);
    }

    private void requestYoutubeAccessToken(
            @NonNull GoogleSignInAccount account,
            boolean userTriggered,
            boolean backgroundRefresh
    ) {
        requestYoutubeAccessToken(account, userTriggered, backgroundRefresh, false);
    }

    private void requestYoutubeAccessToken(
            @NonNull GoogleSignInAccount account,
            boolean userTriggered,
            boolean backgroundRefresh,
            boolean forceRefresh
    ) {
        if (!isAdded()) {
            return;
        }

        if (!backgroundRefresh) {
            setLibraryLoading(true, "Solicitando permiso de biblioteca...");
        }
        Account accountRef = account.getAccount();
        if (accountRef == null && !TextUtils.isEmpty(account.getEmail())) {
            accountRef = new Account(account.getEmail(), "com.google");
        }

        if (accountRef == null) {
            setLibraryLoading(false, "No se pudo resolver la cuenta de Google.");
            return;
        }

        Account resolvedAccount = accountRef;
        android.content.Context appContext = requireContext().getApplicationContext();
        authExecutor.execute(() -> {
            try {
                String scope = "oauth2:" + youTubeMusicService.getYoutubeReadonlyScope();
                String token = GoogleAuthUtil.getToken(appContext, resolvedAccount, scope);
                if (TextUtils.isEmpty(token)) {
                    throw new IllegalStateException("No se recibio token OAuth.");
                }

                String finalToken = token;
                mainHandler.post(() -> {
                    youtubeAccessToken = finalToken;
                    streamingOauthCompleted = true;
                    cacheTokenForCurrentAccount(finalToken);
                    updateYoutubeButtonLabel();
                    fetchLibraryPlaylists(userTriggered, backgroundRefresh, forceRefresh);
                });
            } catch (UserRecoverableAuthException recoverableException) {
                mainHandler.post(() -> {
                    youtubeAuthRefreshInProgress = false;
                    if (backgroundRefresh && !userTriggered) {
                        return;
                    }
                    clearPendingRecoverableAuthState();
                    pendingRecoverableAccount = account;
                    setLibraryLoading(false, "Confirma el permiso para leer tu biblioteca.");
                    Intent recoverIntent = recoverableException.getIntent();
                    if (recoverIntent == null) {
                        if (isAdded()) {
                            
                        }
                        launchGoogleSignInChooser(true);
                        return;
                    }
                    try {
                        recoverAuthLauncher.launch(recoverIntent);
                    } catch (Exception launchError) {
                        if (isAdded()) {
                            
                        }
                        launchGoogleSignInChooser(true);
                    }
                });
            } catch (IOException | GoogleAuthException | IllegalStateException e) {
                mainHandler.post(() -> {
                    youtubeAuthRefreshInProgress = false;
                    if (!backgroundRefresh) {
                        setLibraryLoading(false, "No se pudo validar YouTube: " + e.getMessage());
                    }
                    if (userTriggered && isAdded()) {
                        
                    }
                });
            }
        });
    }

    private void clearPendingRecoverableAuthState() {
        pendingWebSessionAuthRecovery = false;
        pendingWebSessionUserTriggered = false;
        pendingWebSessionBackgroundRefresh = false;
        pendingWebSessionForceRefresh = false;
    }

    private void fetchLibraryPlaylists(boolean userTriggered) {
        fetchLibraryPlaylists(userTriggered, false, false);
    }

    private void fetchLibraryPlaylists(boolean userTriggered, boolean backgroundRefresh) {
        fetchLibraryPlaylists(userTriggered, backgroundRefresh, false);
    }

    private void fetchLibraryPlaylists(boolean userTriggered, boolean backgroundRefresh, boolean forceRefresh) {
        if (TextUtils.isEmpty(youtubeAccessToken)) {
            Log.w(TAG_STREAMING, "playlist_fetch skipped: empty_token");
            if (restoreLibraryFromPersistentCache()) {
                if (!backgroundRefresh) {
                    setLibraryLoading(false, "Modo offline: biblioteca guardada.");
                }
                if (activeScreen == ScreenMode.LIBRARY) {
                    renderLibraryResults();
                }
                updateYoutubeButtonLabel();
                return;
            }
            setLibraryLoading(false, "Conecta YouTube para cargar tu biblioteca.");
            return;
        }

        if (!isNetworkAvailable()) {
            Log.w(TAG_STREAMING, "playlist_fetch network_unavailable");
            if (restoreLibraryFromPersistentCache() || !libraryTracks.isEmpty()) {
                if (!backgroundRefresh) {
                    setLibraryLoading(false, "Sin internet. Mostrando biblioteca guardada.");
                }
                if (activeScreen == ScreenMode.LIBRARY) {
                    renderLibraryResults();
                }
                return;
            }

            setLibraryLoading(false, "Sin internet y sin cache de biblioteca.");
            return;
        }

        if (!backgroundRefresh) {
            setLibraryLoading(true, "Cargando playlists de tu biblioteca...");
        }
        Log.i(TAG_STREAMING, "playlist_fetch start limit=" + LIBRARY_PLAYLIST_FETCH_LIMIT);
        youTubeMusicService.fetchMyPlaylists(youtubeAccessToken, LIBRARY_PLAYLIST_FETCH_LIMIT, new YouTubeMusicService.PlaylistsCallback() {
            @Override
            public void onSuccess(@NonNull List<YouTubeMusicService.PlaylistResult> playlists) {
                if (!isAdded()) {
                    return;
                }

                Log.i(TAG_STREAMING, "playlist_fetch success playlists=" + playlists.size());

                libraryTracks.clear();
                libraryTracks.addAll(mapPlaylistsToLibraryTracks(playlists));
                ensureFavoritesPlaylistInLibraryTracks();

                cacheLibraryForCurrentAccount(libraryTracks);
                prefetchLibraryTracksForOffline(libraryTracks, youtubeAccessToken);
                lastLibrarySyncAtMs = System.currentTimeMillis();

                youtubeAuthRefreshInProgress = false;
                if (!backgroundRefresh) {
                    setLibraryLoading(false, "");
                }
                if (activeScreen == ScreenMode.LIBRARY) {
                    renderLibraryResults();
                }
            }

            @Override
            public void onError(@NonNull String error) {
                if (!isAdded()) {
                    return;
                }

                Log.e(TAG_STREAMING, "playlist_fetch error: " + error);

                if (isLikelyNetworkError(error)) {
                    boolean restored = restoreLibraryFromPersistentCache();
                    if (restored || !libraryTracks.isEmpty()) {
                        youtubeAuthRefreshInProgress = false;
                        if (!backgroundRefresh) {
                            setLibraryLoading(false, "Sin internet. Mostrando biblioteca guardada.");
                        }
                        if (activeScreen == ScreenMode.LIBRARY) {
                            renderLibraryResults();
                        }
                        return;
                    }
                }

                if (shouldRetryYouTubeAuth(error) && !youtubeAuthRefreshInProgress) {
                    if (streamingOauthCompleted) {
                        youtubeAuthRefreshInProgress = true;
                        youtubeAccessToken = "";
                        clearCachedToken();
                        updateYoutubeButtonLabel();
                        requestYoutubeAccessTokenFromPrimaryAccountAfterWebSession(false, backgroundRefresh, forceRefresh);
                        return;
                    }
                }

                youtubeAuthRefreshInProgress = false;
                if (shouldInvalidateStoredYoutubeToken(error)) {
                    youtubeAccessToken = "";
                    streamingOauthCompleted = false;
                    clearCachedToken();
                    updateYoutubeButtonLabel();
                }

                if (!backgroundRefresh || libraryTracks.isEmpty()) {
                    setLibraryLoading(false, "Error al cargar biblioteca: " + error);
                }
                if (userTriggered) {
                    
                }
                if (forceRefresh) {
                    setLibraryPullRefreshState(false);
                }
            }
        });
    }

    private void renderLibraryResults() {
        ensureFavoritesPlaylistInLibraryTracks();
        libraryInlinePendingReveal = false;

        String inlineQuery = getLibraryInlineQuery();
        if (!TextUtils.isEmpty(inlineQuery)) {
            showLibraryInlineBlankState();
            return;
        }

        if (adapter != null) {
            adapter.setSearchMode(false);
        }

        featuredTrack = null;
        llFeaturedResult.setVisibility(View.GONE);
        if (tvLibraryTitle != null) tvLibraryTitle.setVisibility(View.VISIBLE);

        if (llLibraryInlineSearch != null) llLibraryInlineSearch.setVisibility(View.VISIBLE);
        llLibraryEmptyState.setVisibility(View.GONE);
        updateLibraryInlineClearButton();
        rvMusicResults.setVisibility(View.VISIBLE);
        llMusicStateVisible(false);
        tracks.clear();
        adapter.submitResults(new ArrayList<>(libraryTracks));

        if (libraryTracks.isEmpty()) {
            tvMusicState.setText("");
            showLibraryEmptyState();
        } else {
            tvMusicState.setText("");
        }

        maybeEnqueueNextOfflineAutoPlaylist();
    }



    private void showLibraryEmptyState() {
        if (tvLibraryTitle != null) tvLibraryTitle.setVisibility(View.GONE);

        if (llLibraryInlineSearch != null) llLibraryInlineSearch.setVisibility(View.GONE);
        if (ivLibraryQuickClear != null) ivLibraryQuickClear.setVisibility(View.GONE);
        llMusicStateVisible(false);
        rvMusicResults.setVisibility(View.GONE);
        llLibraryEmptyState.setVisibility(View.VISIBLE);
        updateYoutubeButtonLabel();
    }

    @NonNull
    private String getLibraryInlineQuery() {
        if (etLibraryQuickSearch == null || etLibraryQuickSearch.getText() == null) {
            return "";
        }
        return etLibraryQuickSearch.getText().toString().trim();
    }

    private void applyLibraryInlineSongSearch() {
        if (activeScreen != ScreenMode.LIBRARY) {
            return;
        }

        String query = getLibraryInlineQuery();
        if (TextUtils.isEmpty(query)) {
            cancelPendingLibraryInlineSearch();
            lastLibraryInlineDispatchedQuery = "";
            libraryInlineManualModeActive = false;
            renderLibraryResults();
            return;
        }

        libraryInlineManualModeActive = true;
        showLibraryInlineBlankState();
    }

    private void submitLibraryInlineSongSearch() {
        if (activeScreen != ScreenMode.LIBRARY) {
            return;
        }

        String query = getLibraryInlineQuery();
        if (TextUtils.isEmpty(query)) {
            libraryInlineManualModeActive = false;
            renderLibraryResults();
            return;
        }

        libraryInlineManualModeActive = true;
        showLibraryInlineLoadingState();
        triggerOnlineMusicSearchFromLibrary(query);
    }

    private void showLibraryInlineBlankState() {
        if (activeScreen != ScreenMode.LIBRARY) {
            return;
        }

        cancelPendingLibraryInlineSearch();
        libraryInlinePendingReveal = false;

        if (tvLibraryTitle != null) tvLibraryTitle.setVisibility(View.VISIBLE);

        if (llLibraryInlineSearch != null) llLibraryInlineSearch.setVisibility(View.VISIBLE);
        llLibraryEmptyState.setVisibility(View.GONE);
        updateLibraryInlineClearButton();

        featuredTrack = null;
        llFeaturedResult.setVisibility(View.GONE);
        tracks.clear();
        allTracks.clear();

        if (adapter != null) {
            adapter.setSearchMode(true);
            adapter.submitResults(new ArrayList<>());
        }

        if (rvMusicResults != null) {
            rvMusicResults.animate().cancel();
            rvMusicResults.setAlpha(0f);
            rvMusicResults.setVisibility(View.GONE);
        }

        llMusicStateVisible(false);
        tvMusicState.setText("");
    }

    private void showLibraryInlineLoadingState() {
        if (activeScreen != ScreenMode.LIBRARY) {
            return;
        }

        if (tvLibraryTitle != null) tvLibraryTitle.setVisibility(View.VISIBLE);

        if (llLibraryInlineSearch != null) llLibraryInlineSearch.setVisibility(View.VISIBLE);
        llLibraryEmptyState.setVisibility(View.GONE);
        updateLibraryInlineClearButton();

        featuredTrack = null;
        llFeaturedResult.setVisibility(View.GONE);
        tracks.clear();

        if (adapter != null) {
            adapter.setSearchMode(true);
            adapter.submitResults(new ArrayList<>());
        }

        if (rvMusicResults != null) {
            rvMusicResults.animate().cancel();
            rvMusicResults.setAlpha(0f);
            rvMusicResults.setVisibility(View.GONE);
        }

        libraryInlinePendingReveal = true;
        llMusicStateVisible(true);
        tvMusicState.setText("Buscando musica en YouTube...");
    }

    private void revealLibraryInlineResultsWithFade() {
        if (rvMusicResults == null) {
            return;
        }

        if (!libraryInlinePendingReveal) {
            rvMusicResults.setVisibility(View.VISIBLE);
            rvMusicResults.setAlpha(1f);
            return;
        }

        libraryInlinePendingReveal = false;
        rvMusicResults.animate().cancel();
        rvMusicResults.setAlpha(0f);
        rvMusicResults.setVisibility(View.VISIBLE);
        rvMusicResults.animate()
                .alpha(1f)
                .setDuration(210L)
                .start();
    }

    private void cancelPendingLibraryInlineSearch() {
        if (pendingLibraryInlineSearchRunnable == null) {
            return;
        }
        mainHandler.removeCallbacks(pendingLibraryInlineSearchRunnable);
        pendingLibraryInlineSearchRunnable = null;
    }

    private void triggerOnlineMusicSearchFromLibrary(@NonNull String query) {
        if (!isAdded()) {
            return;
        }
        lastLibraryInlineDispatchedQuery = query;

        if (activeFilter != ChipFilter.SONGS) {
            activeFilter = ChipFilter.SONGS;
            applyChipStyles();
        }

        if (etMusicQuery != null) {
            String currentSearchQuery = etMusicQuery.getText() == null
                    ? ""
                    : etMusicQuery.getText().toString().trim();
            if (!TextUtils.equals(currentSearchQuery, query)) {
                etMusicQuery.setText(query);
            }
            etMusicQuery.setSelection(etMusicQuery.getText() == null ? 0 : etMusicQuery.getText().length());
        }

        if (tvLibraryTitle != null) tvLibraryTitle.setVisibility(View.VISIBLE);

        if (llLibraryInlineSearch != null) llLibraryInlineSearch.setVisibility(View.VISIBLE);
        llLibraryEmptyState.setVisibility(View.GONE);
        updateLibraryInlineClearButton();
        llMusicStateVisible(true);
        if (adapter != null) {
            adapter.setSearchMode(true);
        }

        performYoutubeMusicSearch(query, false, true);
    }

    private void renderLibrarySongSearchResults(@NonNull String query) {
        if (activeScreen != ScreenMode.LIBRARY) {
            return;
        }

        if (adapter != null) {
            adapter.setSearchMode(true);
        }

        if (libraryTracks.isEmpty()) {
            restoreLibraryFromPersistentCache();
        }

        List<YouTubeMusicService.TrackResult> resultTracks = findSongsAcrossCachedPlaylists(query);
        tracks.clear();

        if (resultTracks.isEmpty()) {
            featuredTrack = null;
            llFeaturedResult.setVisibility(View.GONE);
        } else {
            featuredTrack = resultTracks.get(0);
            bindFeaturedTrack(featuredTrack);
            llFeaturedResult.setVisibility(View.VISIBLE);
            if (resultTracks.size() > 1) {
                tracks.addAll(resultTracks.subList(1, resultTracks.size()));
            }
        }

        if (tvLibraryTitle != null) tvLibraryTitle.setVisibility(View.VISIBLE);

        if (llLibraryInlineSearch != null) llLibraryInlineSearch.setVisibility(View.VISIBLE);
        llLibraryEmptyState.setVisibility(View.GONE);
        updateLibraryInlineClearButton();
        llMusicStateVisible(true);
        if (adapter != null) {
            adapter.submitResults(new ArrayList<>(tracks));
        }
        revealLibraryInlineResultsWithFade();

        if (resultTracks.isEmpty()) {
            tvMusicState.setText("Sin coincidencias en playlists guardadas para: " + query);
        } else {
            tvMusicState.setText(String.format(
                    Locale.getDefault(),
                    "%d canciones encontradas en tus playlists.",
                    resultTracks.size()
            ));
        }
    }

    @NonNull
    private List<YouTubeMusicService.TrackResult> findSongsAcrossCachedPlaylists(@NonNull String query) {
        List<YouTubeMusicService.TrackResult> result = new ArrayList<>();
        if (!isAdded()) {
            return result;
        }

        ensureFavoritesPlaylistInLibraryTracks();

        String[] queryTokens = extractSearchTokens(query);
        if (queryTokens.length == 0) {
            return result;
        }

        Set<String> seenVideoIds = new HashSet<>();

        for (YouTubeMusicService.TrackResult playlist : libraryTracks) {
            if (playlist == null || !"playlist".equals(playlist.resultType)) {
                continue;
            }

            String playlistId = playlist.contentId == null ? "" : playlist.contentId.trim();
            if (playlistId.isEmpty()) {
                continue;
            }

            String playlistTitle = TextUtils.isEmpty(playlist.title) ? "Playlist" : playlist.title;
            ArrayList<CachedPlaylistTrack> cachedTracks = loadCachedPlaylistTracksForOffline(playlistId);
            if (cachedTracks.isEmpty()) {
                continue;
            }

            for (CachedPlaylistTrack cachedTrack : cachedTracks) {
                if (cachedTrack == null || TextUtils.isEmpty(cachedTrack.videoId)) {
                    continue;
                }

                String normalizedTitle = normalizeForFilter(cachedTrack.title);
                String normalizedArtist = normalizeForFilter(cachedTrack.artist);
                String normalizedPlaylistTitle = normalizeForFilter(playlistTitle);
                boolean matches = matchesOrderedTokens(normalizedTitle, queryTokens)
                        || matchesOrderedTokens(normalizedArtist, queryTokens)
                        || matchesOrderedTokens(normalizedPlaylistTitle, queryTokens)
                        || matchesOrderedTokens(
                        (normalizedTitle + " " + normalizedArtist + " " + normalizedPlaylistTitle).trim(),
                        queryTokens
                );
                if (!matches || !seenVideoIds.add(cachedTrack.videoId)) {
                    continue;
                }

                String displayTitle = TextUtils.isEmpty(cachedTrack.title) ? "Cancion" : cachedTrack.title;
                String subtitle = TextUtils.isEmpty(cachedTrack.artist)
                        ? "Playlist: " + playlistTitle
                        : cachedTrack.artist + " • " + playlistTitle;

                result.add(new YouTubeMusicService.TrackResult(
                        "video",
                        cachedTrack.videoId,
                        displayTitle,
                        subtitle,
                        cachedTrack.imageUrl
                ));

                if (result.size() >= LIBRARY_INLINE_SEARCH_MAX_RESULTS) {
                    return result;
                }
            }
        }

        sortSearchResultsByBestMatch(result, query);

        return result;
    }

    private void updateLibraryInlineClearButton() {
        if (ivLibraryQuickClear == null) {
            return;
        }

        boolean visible = activeScreen == ScreenMode.LIBRARY
                && etLibraryQuickSearch != null
                && etLibraryQuickSearch.getText() != null
                && !TextUtils.isEmpty(etLibraryQuickSearch.getText().toString().trim());
        ivLibraryQuickClear.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    private void llMusicStateVisible(boolean visible) {
        if (llMusicState != null) {
            llMusicState.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
    }

    private void invalidatePendingSearchState() {
        queuedSearchQuery = null;
        queuedSearchFromUser = false;
        queuedSearchKeepLibraryLayout = false;
        latestSearchRequestId++;
        hasMoreSearchPages = false;
        nextSearchPageToken = "";
        setSearchLoadingState(false, "");
        setSearchPaginationLoading(false);
    }

    private void resetSearchResultsForTypingState(@Nullable String currentQuery) {
        allTracks.clear();
        tracks.clear();
        activeSearchQuery = "";
        featuredTrack = null;
        llFeaturedResult.setVisibility(View.GONE);
        if (adapter != null) {
            adapter.submitResults(new ArrayList<>());
        }

        String query = currentQuery == null ? "" : currentQuery.trim();
        if (TextUtils.isEmpty(query)) {
            tvMusicState.setText("Escribe y presiona Buscar o Enter.");
        } else {
            tvMusicState.setText("Presiona Buscar o Enter para ver resultados.");
        }
    }

    private void startPagedSearch(@NonNull String query, boolean fromUserAction) {
        String normalizedQuery = query.trim();
        if (normalizedQuery.isEmpty()) {
            resetSearchResultsForTypingState("");
            return;
        }

        invalidatePendingSearchState();
        activeSearchQuery = normalizedQuery;
        rememberRecentSearchQuery(normalizedQuery);
        refreshSearchSuggestions(normalizedQuery);
        requestPagedSearchResults(normalizedQuery, "", false, fromUserAction);
    }

    private void requestPagedSearchResults(
            @NonNull String query,
            @NonNull String pageToken,
            boolean append,
            boolean fromUserAction
    ) {
        if (!isNetworkAvailable()) {
            if (append) {
                setSearchPaginationLoading(false);
                return;
            }

            if (activeFilter != ChipFilter.SONGS) {
                activeFilter = ChipFilter.SONGS;
                applyChipStyles();
            }

            allTracks.clear();
            allTracks.addAll(findSongsAcrossCachedPlaylists(query));
            applyActiveFilter(query);
            hasMoreSearchPages = false;
            nextSearchPageToken = "";
            activeSearchQuery = query;

            if (allTracks.isEmpty()) {
                tvMusicState.setText("Sin internet. No encontre canciones en tus playlists para: " + query);
            } else {
                tvMusicState.setText(String.format(
                        Locale.getDefault(),
                        "%d canciones encontradas en tus playlists (offline).",
                        allTracks.size()
                ));
            }
            return;
        }

        final long requestId = ++latestSearchRequestId;
        if (append) {
            setSearchPaginationLoading(true);
            tvMusicState.setText("Cargando mas resultados...");
        } else {
            setSearchLoadingState(true, "Buscando musica en YouTube...");
        }

        youTubeMusicService.searchTracksPaged(
                query,
                SEARCH_PAGE_SIZE,
                TextUtils.isEmpty(pageToken) ? null : pageToken,
                new YouTubeMusicService.SearchPageCallback() {
                    @Override
                    public void onSuccess(@NonNull YouTubeMusicService.SearchPageResult pageResult) {
                        if (!isAdded()) {
                            return;
                        }
                        if (requestId != latestSearchRequestId) {
                            return;
                        }

                        if (append) {
                            setSearchPaginationLoading(false);
                        } else {
                            setSearchLoadingState(false, "");
                        }

                        List<YouTubeMusicService.TrackResult> incoming = filterTracksByOrderedQuery(pageResult.tracks, query);
                        if (!append) {
                            allTracks.clear();
                        }
                        appendUniqueTracks(incoming);

                        activeSearchQuery = query;
                        nextSearchPageToken = pageResult.nextPageToken;
                        hasMoreSearchPages = !TextUtils.isEmpty(nextSearchPageToken);

                        applyActiveFilter(query);

                        if (allTracks.isEmpty()) {
                            tvMusicState.setText("No encontre resultados para: " + query);
                        }
                    }

                    @Override
                    public void onError(@NonNull String error) {
                        if (!isAdded()) {
                            return;
                        }
                        if (requestId != latestSearchRequestId) {
                            return;
                        }

                        if (append) {
                            setSearchPaginationLoading(false);
                            tvMusicState.setText("No se pudieron cargar mas resultados.");
                            return;
                        }

                        setSearchLoadingState(false, "");

                        if (!isNetworkAvailable()) {
                            if (activeFilter != ChipFilter.SONGS) {
                                activeFilter = ChipFilter.SONGS;
                                applyChipStyles();
                            }
                            allTracks.clear();
                            allTracks.addAll(findSongsAcrossCachedPlaylists(query));
                            applyActiveFilter(query);
                            hasMoreSearchPages = false;
                            nextSearchPageToken = "";
                            activeSearchQuery = query;
                            if (allTracks.isEmpty()) {
                                tvMusicState.setText("Sin internet. No encontre canciones en tus playlists para: " + query);
                            } else {
                                tvMusicState.setText(String.format(
                                        Locale.getDefault(),
                                        "%d canciones encontradas en tus playlists (offline).",
                                        allTracks.size()
                                ));
                            }
                            return;
                        }

                        hasMoreSearchPages = false;
                        nextSearchPageToken = "";
                        setSearchLoadingState(false, "Error: " + error);
                        if (fromUserAction) {

                        }
                    }
                }
        );
    }

    private void appendUniqueTracks(@NonNull List<YouTubeMusicService.TrackResult> incoming) {
        Set<String> seen = new HashSet<>();
        for (YouTubeMusicService.TrackResult existing : allTracks) {
            if (existing == null) {
                continue;
            }
            String key = (existing.resultType == null ? "" : existing.resultType)
                    + "|"
                    + (existing.contentId == null ? "" : existing.contentId);
            seen.add(key);
        }

        for (YouTubeMusicService.TrackResult candidate : incoming) {
            if (candidate == null) {
                continue;
            }
            String key = (candidate.resultType == null ? "" : candidate.resultType)
                    + "|"
                    + (candidate.contentId == null ? "" : candidate.contentId);
            if (!seen.add(key)) {
                continue;
            }
            allTracks.add(candidate);
        }
    }

    private void loadMoreSearchResultsIfNeeded() {
        if (activeScreen != ScreenMode.SEARCH
                || searching
                || searchPaginationInFlight
                || !hasMoreSearchPages
                || TextUtils.isEmpty(activeSearchQuery)) {
            return;
        }

        requestPagedSearchResults(activeSearchQuery, nextSearchPageToken, true, false);
    }

    private void performSearch(boolean fromUserAction) {
        if (activeScreen != ScreenMode.SEARCH || searching) {
            return;
        }

        String query = etMusicQuery.getText() != null ? etMusicQuery.getText().toString().trim() : "";
        if (TextUtils.isEmpty(query)) {
            if (fromUserAction) {
                resetSearchResultsForTypingState("");
            }
            return;
        }

        if (activeFilter != ChipFilter.SONGS) {
            activeFilter = ChipFilter.SONGS;
            applyChipStyles();
        }

        startPagedSearch(query, fromUserAction);
    }

    private void performYoutubeMusicSearch(
            @NonNull String query,
            boolean fromUserAction,
            boolean keepLibraryLayout
    ) {
        if (searching) {
            queuedSearchQuery = query;
            queuedSearchFromUser = fromUserAction;
            queuedSearchKeepLibraryLayout = keepLibraryLayout;
            return;
        }

        if (!isNetworkAvailable()) {
            if (keepLibraryLayout && activeScreen == ScreenMode.LIBRARY) {
                renderLibrarySongSearchResults(query);
                tvMusicState.setText("Sin internet. Mostrando canciones de tus playlists.");
                return;
            }

            if (activeFilter != ChipFilter.SONGS) {
                activeFilter = ChipFilter.SONGS;
                applyChipStyles();
            }

            allTracks.clear();
            allTracks.addAll(findSongsAcrossCachedPlaylists(query));
            applyActiveFilter(query);

            if (allTracks.isEmpty()) {
                tvMusicState.setText("Sin internet. No encontre canciones en tus playlists para: " + query);
            } else {
                tvMusicState.setText(String.format(
                        Locale.getDefault(),
                        "%d canciones encontradas en tus playlists (offline).",
                        allTracks.size()
                ));
            }
            return;
        }

        final long requestId = ++latestSearchRequestId;
        setSearchLoadingState(true, "Buscando musica en YouTube...");
        youTubeMusicService.searchTracks(query, 20, new YouTubeMusicService.SearchCallback() {
            @Override
            public void onSuccess(@NonNull List<YouTubeMusicService.TrackResult> resultTracks) {
                if (!isAdded()) {
                    return;
                }
                if (requestId != latestSearchRequestId) {
                    dispatchQueuedSearchIfAny();
                    return;
                }
                setSearchLoadingState(false, "");

                allTracks.clear();
                allTracks.addAll(filterTracksByOrderedQuery(resultTracks, query));

                if (keepLibraryLayout && activeScreen == ScreenMode.LIBRARY) {
                    renderOnlineSearchResultsInLibrary(query, allTracks);
                    return;
                }

                applyActiveFilter(query);

                if (allTracks.isEmpty()) {
                    tvMusicState.setText("No encontre resultados para: " + query);
                }

                dispatchQueuedSearchIfAny();
            }

            @Override
            public void onError(@NonNull String error) {
                if (!isAdded()) {
                    return;
                }
                if (requestId != latestSearchRequestId) {
                    dispatchQueuedSearchIfAny();
                    return;
                }
                setSearchLoadingState(false, "");
                if (keepLibraryLayout && activeScreen == ScreenMode.LIBRARY) {
                    renderLibrarySongSearchResults(query);
                    if (!isNetworkAvailable()) {
                        tvMusicState.setText("Sin internet. Mostrando canciones de tus playlists.");
                    } else {
                        tvMusicState.setText("Mostrando canciones de tus playlists guardadas.");
                    }
                    dispatchQueuedSearchIfAny();
                    return;
                }

                if (!isNetworkAvailable()) {
                    if (activeFilter != ChipFilter.SONGS) {
                        activeFilter = ChipFilter.SONGS;
                        applyChipStyles();
                    }
                    allTracks.clear();
                    allTracks.addAll(findSongsAcrossCachedPlaylists(query));
                    applyActiveFilter(query);
                    if (allTracks.isEmpty()) {
                        tvMusicState.setText("Sin internet. No encontre canciones en tus playlists para: " + query);
                    } else {
                        tvMusicState.setText(String.format(
                                Locale.getDefault(),
                                "%d canciones encontradas en tus playlists (offline).",
                                allTracks.size()
                        ));
                    }
                    dispatchQueuedSearchIfAny();
                    return;
                }

                setSearchLoadingState(false, "Error: " + error);
                if (fromUserAction) {
                    
                }

                dispatchQueuedSearchIfAny();
            }
        });
    }

    private void dispatchQueuedSearchIfAny() {
        if (TextUtils.isEmpty(queuedSearchQuery)) {
            return;
        }
        if (!isAdded()) {
            queuedSearchQuery = null;
            return;
        }

        String nextQuery = queuedSearchQuery;
        boolean nextFromUser = queuedSearchFromUser;
        boolean nextKeepLibraryLayout = queuedSearchKeepLibraryLayout;
        queuedSearchQuery = null;

        mainHandler.post(() -> performYoutubeMusicSearch(nextQuery, nextFromUser, nextKeepLibraryLayout));
    }

    private void renderOnlineSearchResultsInLibrary(
            @NonNull String query,
            @NonNull List<YouTubeMusicService.TrackResult> resultTracks
    ) {
        List<YouTubeMusicService.TrackResult> onlineSongs = new ArrayList<>();
        for (YouTubeMusicService.TrackResult track : resultTracks) {
            if (matchesFilter(track, ChipFilter.SONGS) && trackMatchesOrderedQuery(track, query)) {
                onlineSongs.add(track);
            }
        }

        List<YouTubeMusicService.TrackResult> filtered = mergeSongResultsWithCachedPlaylists(onlineSongs, query);

        tracks.clear();
        if (filtered.isEmpty()) {
            featuredTrack = null;
            llFeaturedResult.setVisibility(View.GONE);
        } else {
            featuredTrack = filtered.get(0);
            bindFeaturedTrack(featuredTrack);
            llFeaturedResult.setVisibility(View.VISIBLE);
            if (filtered.size() > 1) {
                tracks.addAll(filtered.subList(1, filtered.size()));
            }
        }

        if (adapter != null) {
            adapter.setSearchMode(true);
            adapter.submitResults(new ArrayList<>(tracks));
        }
        revealLibraryInlineResultsWithFade();

        if (filtered.isEmpty()) {
            tvMusicState.setText("No encontre canciones para: " + query);
        } else {
            tvMusicState.setText(String.format(
                    Locale.getDefault(),
                    "%d canciones encontradas (YouTube + playlists).",
                    filtered.size()
            ));
        }
    }

    private void setSearchLoadingState(boolean loading, @NonNull String stateMessage) {
        searching = loading;
        if (btnSearchMusic != null) {
            btnSearchMusic.setEnabled(!loading);
        }
        if (etMusicQuery != null) {
            etMusicQuery.setEnabled(!loading);
        }
        refreshGlobalLoadingIndicator();
        if (!stateMessage.isEmpty()) {
            tvMusicState.setText(stateMessage);
        }
    }

    private void setSearchPaginationLoading(boolean loading) {
        searchPaginationInFlight = loading;
        refreshGlobalLoadingIndicator();
    }

    private void setLibraryLoading(boolean loading, @NonNull String stateMessage) {
        loadingLibrary = loading;
        if (btnYoutubeLogin != null) {
            btnYoutubeLogin.setEnabled(!loading);
        }
        refreshGlobalLoadingIndicator();
        if (!loading) {
            setLibraryPullRefreshState(false);
        }
        if (!stateMessage.isEmpty()) {
            tvMusicState.setText(stateMessage);
        }
    }

    private void refreshGlobalLoadingIndicator() {
        if (progressMusic == null) {
            return;
        }

        boolean visible = searching || searchPaginationInFlight || loadingLibrary;
        progressMusic.animate().cancel();
        if (visible) {
            if (progressMusic.getVisibility() != View.VISIBLE) {
                progressMusic.setAlpha(0f);
                progressMusic.setVisibility(View.VISIBLE);
            }
            progressMusic.animate()
                    .alpha(1f)
                    .setDuration(160L)
                    .start();
            return;
        }

        if (progressMusic.getVisibility() != View.VISIBLE) {
            return;
        }

        progressMusic.animate()
                .alpha(0f)
                .setDuration(140L)
                .withEndAction(() -> {
                    if (progressMusic != null) {
                        progressMusic.setVisibility(View.GONE);
                    }
                })
                .start();
    }

    private void rememberRecentSearchQuery(@Nullable String query) {
        String normalized = query == null ? "" : query.trim();
        if (normalized.isEmpty()) {
            return;
        }

        for (int i = recentSearchQueries.size() - 1; i >= 0; i--) {
            if (normalized.equalsIgnoreCase(recentSearchQueries.get(i))) {
                recentSearchQueries.remove(i);
            }
        }

        recentSearchQueries.add(0, normalized);
        while (recentSearchQueries.size() > SEARCH_SUGGESTION_RECENT_LIMIT) {
            recentSearchQueries.remove(recentSearchQueries.size() - 1);
        }
        
        saveRecentSearchQueries();
    }

    private void restoreRecentSearchQueries() {
        if (!isAdded()) return;
        SharedPreferences prefs = requireContext().getSharedPreferences(PREFS_STREAMING_CACHE, Activity.MODE_PRIVATE);
        String json = prefs.getString(PREF_RECENT_SEARCH_QUERIES, "[]");
        recentSearchQueries.clear();
        try {
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                recentSearchQueries.add(array.getString(i));
            }
        } catch (Exception ignored) {
        }
    }

    private void saveRecentSearchQueries() {
        if (!isAdded()) return;
        JSONArray array = new JSONArray();
        for (String query : recentSearchQueries) {
            array.put(query);
        }
        requireContext()
                .getSharedPreferences(PREFS_STREAMING_CACHE, Activity.MODE_PRIVATE)
                .edit()
                .putString(PREF_RECENT_SEARCH_QUERIES, array.toString())
                .apply();
    }

    private void refreshSearchSuggestions(@Nullable String queryDraft) {
        if (!isAdded() || cgSearchSuggestions == null) {
            return;
        }

        String draft = queryDraft == null ? "" : queryDraft.trim();
        List<String> suggestions = buildSearchSuggestions(draft);
        cgSearchSuggestions.removeAllViews();

        for (String suggestion : suggestions) {
            Chip chip = new Chip(requireContext());
            chip.setText(suggestion);
            chip.setCheckable(false);
            chip.setClickable(true);
            chip.setChipBackgroundColor(ColorStateList.valueOf(
                    ContextCompat.getColor(requireContext(), R.color.surface_low)
            ));
            chip.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary));
            chip.setEnsureMinTouchTargetSize(false);
            chip.setOnClickListener(v -> {
                if (etMusicQuery == null) {
                    return;
                }
                etMusicQuery.setText(suggestion);
                etMusicQuery.setSelection(etMusicQuery.getText() == null ? 0 : etMusicQuery.getText().length());
                performSearch(true);
            });
            cgSearchSuggestions.addView(chip);
        }

        if (cgSearchSuggestions != null) cgSearchSuggestions.setVisibility(suggestions.isEmpty() ? View.GONE : View.VISIBLE);
    }

    @NonNull
    private List<String> buildSearchSuggestions(@NonNull String queryDraft) {
        LinkedHashSet<String> pool = new LinkedHashSet<>();
        for (String recent : recentSearchQueries) {
            if (!TextUtils.isEmpty(recent)) {
                pool.add(recent);
            }
        }
        for (String fallback : DEFAULT_SEARCH_SUGGESTIONS) {
            if (!TextUtils.isEmpty(fallback)) {
                pool.add(fallback);
            }
        }

        String normalizedDraft = normalizeForFilter(queryDraft);
        List<String> result = new ArrayList<>();
        for (String candidate : pool) {
            String normalizedCandidate = normalizeForFilter(candidate);
            if (!TextUtils.isEmpty(normalizedDraft)
                    && !normalizedCandidate.contains(normalizedDraft)
                    && !normalizedCandidate.startsWith(normalizedDraft)) {
                continue;
            }
            if (!TextUtils.isEmpty(normalizedDraft)
                    && TextUtils.equals(normalizedCandidate, normalizedDraft)) {
                continue;
            }

            result.add(candidate);
            if (result.size() >= SEARCH_SUGGESTION_RECENT_LIMIT) {
                break;
            }
        }
        return result;
    }

    private void updateYoutubeButtonLabel() {
        if (!isAdded() || btnYoutubeLogin == null) {
            return;
        }
        if (streamingOauthCompleted) {
            btnYoutubeLogin.setVisibility(View.GONE);
        } else {
            btnYoutubeLogin.setVisibility(View.VISIBLE);
            btnYoutubeLogin.setText("Sign in to YouTube Music");
        }
    }

    private boolean hasLoggedInStreamingAccount() {
        return streamingOauthCompleted;
    }

    private boolean shouldRetryYouTubeAuth(@NonNull String error) {
        String normalized = normalizeForFilter(error);
        if (isYouTubeQuotaOrConfigIssue(normalized)) {
            return false;
        }
        return containsAny(normalized,
                "token de youtube expirado",
                "token de youtube invalido",
                "insufficientpermissions",
                "insufficient permission",
                "not properly authorized",
                "youtube.readonly",
                "authorizationrequired");
    }

    private boolean shouldInvalidateStoredYoutubeToken(@NonNull String error) {
        String normalized = normalizeForFilter(error);
        return containsAny(normalized,
                "token de youtube expirado",
                "token de youtube invalido",
                "insufficientpermissions",
                "insufficient permission",
                "not properly authorized",
                "youtube.readonly",
                "authorizationrequired");
    }

    private boolean isYouTubeQuotaOrConfigIssue(@NonNull String normalizedError) {
        return containsAny(normalizedError,
                "quota",
                "rate limit",
                "dailylimit",
                "daily limit",
                "accessnotconfigured",
                "api no esta habilitada",
                "api has not been used",
                "api is disabled",
                "service disabled");
    }

    private void restoreCachedStreamingSessionState() {
        if (!isAdded()) {
            streamingOauthCompleted = false;
            return;
        }

        SharedPreferences playerPrefs = requireContext().getSharedPreferences(PREFS_PLAYER_STATE, Activity.MODE_PRIVATE);
        streamingOauthAccountEmail = normalizeAccountEmail(
                playerPrefs.getString(PREF_LAST_STREAMING_OAUTH_ACCOUNT_EMAIL, "")
        );
        String accountKey = resolveAccountKey();
        String persistedToken = playerPrefs.getString(PREF_LAST_YOUTUBE_ACCESS_TOKEN, "");
        long persistedTokenUpdatedAt = playerPrefs.getLong(PREF_LAST_YOUTUBE_ACCESS_TOKEN_UPDATED_AT, 0L);

        if (!TextUtils.isEmpty(accountKey) && !TextUtils.isEmpty(persistedToken)) {
            tokenCacheAccountKey = accountKey;
            cachedYoutubeAccessToken = persistedToken.trim();
            tokenCacheUpdatedAtMs = persistedTokenUpdatedAt > 0L
                    ? persistedTokenUpdatedAt
                    : System.currentTimeMillis();

            if (hasValidCachedToken(accountKey)) {
                youtubeAccessToken = cachedYoutubeAccessToken;
            }
        }

        hydrateCachedSessionState();

        String webCookie = playerPrefs.getString(PREF_LAST_YOUTUBE_WEB_COOKIE, "");
        boolean hasWebSession = !TextUtils.isEmpty(webCookie == null ? "" : webCookie.trim());
        boolean hasCachedLibrary = !libraryTracks.isEmpty();
        streamingOauthCompleted = hasWebSession || hasCachedLibrary || !TextUtils.isEmpty(youtubeAccessToken);

        Log.i(
            TAG_STREAMING,
            "restore_session web=" + hasWebSession
                + " cachedLibrary=" + hasCachedLibrary
                + " token=" + !TextUtils.isEmpty(youtubeAccessToken)
                + " oauthAccount=" + streamingOauthAccountEmail
        );
    }

    private void hydrateCachedSessionState() {
        String accountKey = resolveAccountKey();
        if (hasValidCachedToken(accountKey)) {
            youtubeAccessToken = cachedYoutubeAccessToken;
        }

        if (hasValidLibraryCache(accountKey) && libraryTracks.isEmpty()) {
            libraryTracks.clear();
            libraryTracks.addAll(new ArrayList<>(LIBRARY_CACHE));
            ensureFavoritesPlaylistInLibraryTracks();
            return;
        }

        List<YouTubeMusicService.TrackResult> persisted = loadPersistedLibrary(accountKey);
        if (!persisted.isEmpty() && libraryTracks.isEmpty()) {
            libraryTracks.clear();
            libraryTracks.addAll(persisted);
            ensureFavoritesPlaylistInLibraryTracks();
            LIBRARY_CACHE.clear();
            LIBRARY_CACHE.addAll(libraryTracks);
            libraryCacheAccountKey = accountKey;
            libraryCacheUpdatedAtMs = getPersistedLibraryUpdatedAt(accountKey);
        }
    }

    private boolean restoreLibraryFromPersistentCache() {
        String accountKey = resolveAccountKey();
        if (TextUtils.isEmpty(accountKey)) {
            return false;
        }

        List<YouTubeMusicService.TrackResult> persisted = loadPersistedLibrary(accountKey);
        if (persisted.isEmpty()) {
            return false;
        }

        libraryTracks.clear();
        libraryTracks.addAll(persisted);
        ensureFavoritesPlaylistInLibraryTracks();
        LIBRARY_CACHE.clear();
        LIBRARY_CACHE.addAll(libraryTracks);
        libraryCacheAccountKey = accountKey;
        libraryCacheUpdatedAtMs = getPersistedLibraryUpdatedAt(accountKey);
        return true;
    }

    @NonNull
    private String resolveAccountKey() {
        String appEmail = getPrimaryAppGoogleEmail();
        if (!TextUtils.isEmpty(appEmail)) {
            return appEmail.toLowerCase(Locale.US).trim();
        }

        if (!isAdded()) {
            return "";
        }
        GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(requireContext());
        if (account != null && !TextUtils.isEmpty(account.getEmail())) {
            return account.getEmail().toLowerCase(Locale.US).trim();
        }

        String persistedKey = resolvePersistedLibraryAccountKey();
        return persistedKey == null ? "" : persistedKey;
    }

    @Nullable
    private StreamingAccountCandidate resolveStreamingOAuthAccountCandidate() {
        LinkedHashMap<String, String> candidates = new LinkedHashMap<>();
        addStreamingAccountCandidate(candidates, streamingOauthAccountEmail, "session_cached_oauth");
        addStreamingAccountCandidate(candidates, getPrimaryAppGoogleEmail(), "firebase_primary");

        if (isAdded()) {
            GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(requireContext());
            if (account != null) {
                addStreamingAccountCandidate(candidates, account.getEmail(), "google_signin_last");
            }

            SharedPreferences playerPrefs = requireContext().getSharedPreferences(PREFS_PLAYER_STATE, Activity.MODE_PRIVATE);
            addStreamingAccountCandidate(
                    candidates,
                    playerPrefs.getString(PREF_LAST_STREAMING_OAUTH_ACCOUNT_EMAIL, ""),
                    "prefs_oauth"
            );
        }

        addStreamingAccountCandidate(candidates, resolvePersistedLibraryAccountKey(), "library_cache_key");
        addStreamingAccountCandidate(candidates, tokenCacheAccountKey, "token_cache_key");

        if (candidates.isEmpty()) {
            return null;
        }

        Map.Entry<String, String> first = candidates.entrySet().iterator().next();
        return new StreamingAccountCandidate(first.getKey(), first.getValue());
    }

    private void addStreamingAccountCandidate(
            @NonNull LinkedHashMap<String, String> candidates,
            @Nullable String email,
            @NonNull String source
    ) {
        String normalized = normalizeAccountEmail(email);
        if (TextUtils.isEmpty(normalized) || candidates.containsKey(normalized)) {
            return;
        }
        candidates.put(normalized, source);
    }

    @NonNull
    private String resolvePersistedLibraryAccountKey() {
        if (!isAdded()) {
            return "";
        }

        SharedPreferences prefs = getCachePrefs();
        String direct = prefs.getString(PREF_LAST_STREAMING_ACCOUNT_KEY, "");
        if (!TextUtils.isEmpty(direct)) {
            return direct.trim().toLowerCase(Locale.US);
        }

        Map<String, ?> all = prefs.getAll();
        long latest = 0L;
        String latestAccountKey = "";
        for (Map.Entry<String, ?> entry : all.entrySet()) {
            String key = entry.getKey();
            if (TextUtils.isEmpty(key) || !key.startsWith(PREF_LIBRARY_UPDATED_AT_PREFIX)) {
                continue;
            }

            String candidate = key.substring(PREF_LIBRARY_UPDATED_AT_PREFIX.length()).trim();
            if (candidate.isEmpty()) {
                continue;
            }

            Object value = entry.getValue();
            long updatedAt = value instanceof Number ? ((Number) value).longValue() : 0L;
            if (updatedAt >= latest) {
                latest = updatedAt;
                latestAccountKey = candidate;
            }
        }

        if (!latestAccountKey.isEmpty()) {
            prefs.edit().putString(PREF_LAST_STREAMING_ACCOUNT_KEY, latestAccountKey).apply();
        }

        return latestAccountKey;
    }

    private boolean hasValidCachedToken(@NonNull String accountKey) {
        if (TextUtils.isEmpty(accountKey) || TextUtils.isEmpty(cachedYoutubeAccessToken)) {
            return false;
        }
        if (!accountKey.equals(tokenCacheAccountKey)) {
            return false;
        }
        return (System.currentTimeMillis() - tokenCacheUpdatedAtMs) < TOKEN_CACHE_TTL_MS;
    }

    private boolean hasValidLibraryCache(@NonNull String accountKey) {
        if (TextUtils.isEmpty(accountKey) || LIBRARY_CACHE.isEmpty()) {
            return false;
        }
        if (!accountKey.equals(libraryCacheAccountKey)) {
            return false;
        }
        return (System.currentTimeMillis() - libraryCacheUpdatedAtMs) < LIBRARY_CACHE_TTL_MS;
    }

    private void cacheTokenForCurrentAccount(@NonNull String token) {
        String accountKey = resolveActiveStreamingAccountKey();
        if (TextUtils.isEmpty(accountKey)) {
            return;
        }
        tokenCacheAccountKey = accountKey;
        cachedYoutubeAccessToken = token;
        tokenCacheUpdatedAtMs = System.currentTimeMillis();
        rememberStreamingAccountKey(accountKey);
        if (isAdded()) {
            requireContext()
                    .getSharedPreferences(PREFS_PLAYER_STATE, Activity.MODE_PRIVATE)
                    .edit()
                    .putString(PREF_LAST_YOUTUBE_ACCESS_TOKEN, token)
                    .putLong(PREF_LAST_YOUTUBE_ACCESS_TOKEN_UPDATED_AT, tokenCacheUpdatedAtMs)
                    .putString(PREF_LAST_STREAMING_OAUTH_ACCOUNT_EMAIL, accountKey)
                    .apply();
        }
    }

    private void clearCachedToken() {
        cachedYoutubeAccessToken = "";
        tokenCacheAccountKey = "";
        tokenCacheUpdatedAtMs = 0L;
        if (isAdded()) {
            requireContext()
                    .getSharedPreferences(PREFS_PLAYER_STATE, Activity.MODE_PRIVATE)
                    .edit()
                    .remove(PREF_LAST_YOUTUBE_ACCESS_TOKEN)
                    .remove(PREF_LAST_YOUTUBE_ACCESS_TOKEN_UPDATED_AT)
                    .apply();
        }
    }

    private void cacheLibraryForCurrentAccount(@NonNull List<YouTubeMusicService.TrackResult> source) {
        String accountKey = resolveActiveStreamingAccountKey();
        if (TextUtils.isEmpty(accountKey)) {
            return;
        }

        LIBRARY_CACHE.clear();
        LIBRARY_CACHE.addAll(new ArrayList<>(source));
        libraryCacheAccountKey = accountKey;
        libraryCacheUpdatedAtMs = System.currentTimeMillis();
        rememberStreamingAccountKey(accountKey);
        persistLibrary(accountKey, source, libraryCacheUpdatedAtMs);
    }

    @NonNull
    private String resolveActiveStreamingAccountKey() {
        String oauthEmail = normalizeAccountEmail(streamingOauthAccountEmail);
        if (!TextUtils.isEmpty(oauthEmail)) {
            return oauthEmail;
        }
        return resolveAccountKey();
    }

    private void rememberStreamingOauthAccountEmail(@Nullable String email) {
        String normalized = normalizeAccountEmail(email);
        streamingOauthAccountEmail = normalized;
        if (!isAdded()) {
            return;
        }

        requireContext()
                .getSharedPreferences(PREFS_PLAYER_STATE, Activity.MODE_PRIVATE)
                .edit()
                .putString(PREF_LAST_STREAMING_OAUTH_ACCOUNT_EMAIL, normalized)
                .apply();
    }

    private static final class StreamingAccountCandidate {
        @NonNull
        final String email;
        @NonNull
        final String source;

        private StreamingAccountCandidate(@NonNull String email, @NonNull String source) {
            this.email = email;
            this.source = source;
        }
    }

    private void rememberStreamingAccountKey(@NonNull String accountKey) {
        if (!isAdded() || TextUtils.isEmpty(accountKey)) {
            return;
        }

        getCachePrefs().edit()
                .putString(PREF_LAST_STREAMING_ACCOUNT_KEY, accountKey)
                .apply();
    }

    @NonNull
    private SharedPreferences getCachePrefs() {
        return getCachePrefs(requireContext());
    }

    @NonNull
    private SharedPreferences getCachePrefs(@NonNull Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS_STREAMING_CACHE, Activity.MODE_PRIVATE);
    }

    private void persistLibrary(
            @NonNull String accountKey,
            @NonNull List<YouTubeMusicService.TrackResult> data,
            long updatedAt
    ) {
        try {
            JSONArray array = new JSONArray();
            for (YouTubeMusicService.TrackResult item : data) {
                JSONObject obj = new JSONObject();
                obj.put("resultType", item.resultType);
                obj.put("contentId", item.contentId);
                obj.put("title", item.title);
                obj.put("subtitle", item.subtitle);
                obj.put("thumbnailUrl", item.thumbnailUrl);
                array.put(obj);
            }

            getCachePrefs().edit()
                    .putLong(PREF_LIBRARY_UPDATED_AT_PREFIX + accountKey, updatedAt)
                    .putString(PREF_LIBRARY_DATA_PREFIX + accountKey, array.toString())
                    .apply();
        } catch (Exception ignored) {
        }
    }

    private long getPersistedLibraryUpdatedAt(@NonNull String accountKey) {
        if (TextUtils.isEmpty(accountKey) || !isAdded()) {
            return 0L;
        }
        return getCachePrefs().getLong(PREF_LIBRARY_UPDATED_AT_PREFIX + accountKey, 0L);
    }

    @NonNull
    private List<YouTubeMusicService.TrackResult> loadPersistedLibrary(@NonNull String accountKey) {
        List<YouTubeMusicService.TrackResult> result = new ArrayList<>();
        if (TextUtils.isEmpty(accountKey) || !isAdded()) {
            return result;
        }

        long updatedAt = getPersistedLibraryUpdatedAt(accountKey);
        if (updatedAt <= 0L) {
            return result;
        }

        String raw = getCachePrefs().getString(PREF_LIBRARY_DATA_PREFIX + accountKey, "");
        if (TextUtils.isEmpty(raw)) {
            return result;
        }

        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.optJSONObject(i);
                if (obj == null) {
                    continue;
                }

                String resultType = obj.optString("resultType", "playlist").trim();
                String contentId = obj.optString("contentId", "").trim();
                String title = obj.optString("title", "").trim();
                String subtitle = obj.optString("subtitle", "").trim();
                String thumbnailUrl = obj.optString("thumbnailUrl", "").trim();

                if (contentId.isEmpty() || title.isEmpty()) {
                    continue;
                }

                result.add(new YouTubeMusicService.TrackResult(
                        resultType,
                        contentId,
                        title,
                        subtitle,
                        thumbnailUrl
                ));
            }
        } catch (Exception ignored) {
            return new ArrayList<>();
        }

        return result;
    }

    private void prefetchLibraryTracksForOffline(
            @NonNull List<YouTubeMusicService.TrackResult> playlists,
            @NonNull String accessToken
    ) {
        if (!isAdded() || playlists.isEmpty() || TextUtils.isEmpty(accessToken)) {
            return;
        }

        long now = System.currentTimeMillis();
        SharedPreferences cachePrefs = getCachePrefs();
        int requested = 0;

        for (YouTubeMusicService.TrackResult item : playlists) {
            if (!"playlist".equals(item.resultType)) {
                continue;
            }

            String playlistId = item.contentId == null ? "" : item.contentId.trim();
            if (playlistId.isEmpty()) {
                continue;
            }

            long updatedAt = cachePrefs.getLong(PREF_TRACKS_UPDATED_AT_PREFIX + playlistId, 0L);
            if (updatedAt > 0L && (now - updatedAt) < OFFLINE_TRACKS_PREFETCH_STALE_MS) {
                continue;
            }

            synchronized (offlinePrefetchInFlight) {
                if (offlinePrefetchInFlight.contains(playlistId)) {
                    continue;
                }
                offlinePrefetchInFlight.add(playlistId);
            }

            final String targetPlaylistId = playlistId;
            requested++;
                offlinePrefetchService.fetchPlaylistTracks(
                    accessToken,
                    targetPlaylistId,
                    OFFLINE_TRACKS_PREFETCH_PER_PLAYLIST_LIMIT,
                    new YouTubeMusicService.PlaylistTracksCallback() {
                @Override
                public void onSuccess(@NonNull List<YouTubeMusicService.PlaylistTrackResult> tracks) {
                    if (isAdded() && !tracks.isEmpty()) {
                        cachePlaylistTracksForOffline(targetPlaylistId, tracks);
                    }
                    markOfflinePrefetchFinished(targetPlaylistId);
                }

                @Override
                public void onError(@NonNull String error) {
                    markOfflinePrefetchFinished(targetPlaylistId);
                }
            });

            if (requested >= OFFLINE_TRACKS_PREFETCH_LIMIT) {
                break;
            }
        }
    }

    private void cachePlaylistTracksForOffline(
            @NonNull String playlistId,
            @NonNull List<YouTubeMusicService.PlaylistTrackResult> tracks
    ) {
        if (!isAdded() || playlistId.isEmpty()) {
            return;
        }

        try {
            JSONArray array = new JSONArray();
            for (YouTubeMusicService.PlaylistTrackResult track : tracks) {
                String videoId = track.videoId == null ? "" : track.videoId.trim();
                String title = track.title == null ? "" : track.title.trim();
                if (videoId.isEmpty() || title.isEmpty()) {
                    continue;
                }

                JSONObject obj = new JSONObject();
                obj.put("videoId", videoId);
                obj.put("title", title);
                obj.put("artist", track.artist == null ? "" : track.artist);
                obj.put("duration", track.duration == null ? "" : track.duration);
                obj.put("imageUrl", track.thumbnailUrl == null ? "" : track.thumbnailUrl);
                array.put(obj);
            }

            if (array.length() == 0) {
                return;
            }

            getCachePrefs().edit()
                    .putLong(PREF_TRACKS_UPDATED_AT_PREFIX + playlistId, System.currentTimeMillis())
                    .putBoolean(
                        PREF_TRACKS_FULL_CACHE_PREFIX + playlistId,
                        tracks.size() < OFFLINE_TRACKS_PREFETCH_PER_PLAYLIST_LIMIT
                    )
                    .putString(PREF_TRACKS_DATA_PREFIX + playlistId, array.toString())
                    .apply();

            synchronized (cachedPlaylistTracksLock) {
                cachedPlaylistTracksById.remove(playlistId);
                cachedPlaylistTracksUpdatedAtById.remove(playlistId);
            }
        } catch (Exception ignored) {
        }
    }

    private void markOfflinePrefetchFinished(@NonNull String playlistId) {
        synchronized (offlinePrefetchInFlight) {
            offlinePrefetchInFlight.remove(playlistId);
        }
    }

    private void setActiveFilter(@NonNull ChipFilter filter) {
        if (activeFilter == filter) {
            return;
        }
        activeFilter = filter;
        applyChipStyles();

        if (activeScreen != ScreenMode.SEARCH) {
            return;
        }

        String query = etMusicQuery.getText() != null ? etMusicQuery.getText().toString().trim() : "";
        applyActiveFilter(query);
    }

    private void applyActiveFilter(@Nullable String query) {
        if (activeScreen != ScreenMode.SEARCH) {
            return;
        }

        String normalizedQuery = query == null ? "" : query.trim();

        List<YouTubeMusicService.TrackResult> filtered = new ArrayList<>();
        for (YouTubeMusicService.TrackResult track : allTracks) {
            if (!matchesFilter(track, activeFilter)) {
                continue;
            }
            if (!TextUtils.isEmpty(normalizedQuery) && !trackMatchesOrderedQuery(track, normalizedQuery)) {
                continue;
            }
            filtered.add(track);
        }

        if (!TextUtils.isEmpty(normalizedQuery) && filtered.size() > 1) {
            sortSearchResultsByBestMatch(filtered, normalizedQuery);
        }

        tracks.clear();
        if (filtered.isEmpty()) {
            featuredTrack = null;
            llFeaturedResult.setVisibility(View.GONE);
        } else {
            featuredTrack = filtered.get(0);
            bindFeaturedTrack(featuredTrack);
            llFeaturedResult.setVisibility(View.VISIBLE);
            if (filtered.size() > 1) {
                tracks.addAll(filtered.subList(1, filtered.size()));
            }
        }
        adapter.submitResults(new ArrayList<>(tracks));

        if (!TextUtils.isEmpty(normalizedQuery)) {
            tvMusicState.setText(String.format(Locale.getDefault(), "%d resultados (%s).", filtered.size(), filterLabel(activeFilter)));
        }
    }

    @NonNull
    private List<YouTubeMusicService.TrackResult> filterTracksByOrderedQuery(
            @NonNull List<YouTubeMusicService.TrackResult> source,
            @NonNull String query
    ) {
        List<YouTubeMusicService.TrackResult> filtered = new ArrayList<>();
        for (YouTubeMusicService.TrackResult track : source) {
            if (trackMatchesOrderedQuery(track, query)) {
                filtered.add(track);
            }
        }
        return filtered;
    }

    @NonNull
    private List<YouTubeMusicService.TrackResult> mergeSongResultsWithCachedPlaylists(
            @NonNull List<YouTubeMusicService.TrackResult> onlineSongs,
            @NonNull String query
    ) {
        List<YouTubeMusicService.TrackResult> merged = new ArrayList<>();
        Set<String> seenVideoIds = new HashSet<>();

        for (YouTubeMusicService.TrackResult track : onlineSongs) {
            if (!matchesFilter(track, ChipFilter.SONGS) || !trackMatchesOrderedQuery(track, query)) {
                continue;
            }
            String videoId = track.videoId == null ? "" : track.videoId.trim();
            if (!videoId.isEmpty() && !seenVideoIds.add(videoId)) {
                continue;
            }
            merged.add(track);
        }

        List<YouTubeMusicService.TrackResult> playlistSongs = findSongsAcrossCachedPlaylists(query);
        for (YouTubeMusicService.TrackResult localTrack : playlistSongs) {
            String localVideoId = localTrack.videoId == null ? "" : localTrack.videoId.trim();
            if (localVideoId.isEmpty() || seenVideoIds.add(localVideoId)) {
                merged.add(localTrack);
            }
        }

        if (merged.size() > 1) {
            sortSearchResultsByBestMatch(merged, query);
        }

        return merged;
    }

    private boolean trackMatchesOrderedQuery(
            @NonNull YouTubeMusicService.TrackResult track,
            @NonNull String query
    ) {
        String[] queryTokens = extractSearchTokens(query);
        if (queryTokens.length == 0) {
            return true;
        }

        String normalizedTitle = normalizeForFilter(track.title);
        String normalizedSubtitle = normalizeForFilter(track.subtitle);
        if (matchesOrderedTokens(normalizedTitle, queryTokens)
                || matchesOrderedTokens(normalizedSubtitle, queryTokens)) {
            return true;
        }

        return matchesOrderedTokens((normalizedTitle + " " + normalizedSubtitle).trim(), queryTokens);
    }

    @NonNull
    private String[] extractSearchTokens(@Nullable String value) {
        String normalized = normalizeForFilter(value);
        if (TextUtils.isEmpty(normalized)) {
            return new String[0];
        }

        String[] rawTokens = normalized.split("[^\\p{L}\\p{N}]+");
        List<String> tokens = new ArrayList<>();
        for (String token : rawTokens) {
            if (!TextUtils.isEmpty(token)) {
                tokens.add(token);
            }
        }
        return tokens.toArray(new String[0]);
    }

    private boolean matchesOrderedTokens(
            @NonNull String normalizedSource,
            @NonNull String[] queryTokens
    ) {
        if (queryTokens.length == 0) {
            return true;
        }
        if (TextUtils.isEmpty(normalizedSource)) {
            return false;
        }

        String[] sourceTokens = normalizedSource.split("[^\\p{L}\\p{N}]+");
        int queryIndex = 0;
        for (String sourceToken : sourceTokens) {
            if (TextUtils.isEmpty(sourceToken)) {
                continue;
            }
            if (sourceToken.startsWith(queryTokens[queryIndex])) {
                queryIndex++;
                if (queryIndex >= queryTokens.length) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean matchesFilter(@NonNull YouTubeMusicService.TrackResult track, @NonNull ChipFilter filter) {
        String title = normalizeForFilter(track.title);
        String type = normalizeForFilter(track.resultType);
        String subtitle = normalizeForFilter(track.subtitle);

        boolean looksLikeVideoClip = containsAny(title,
                "official video",
                "music video",
                " videoclip",
                " clip",
                " mv",
                "visualizer");
        boolean looksLikeShort = containsAny(title,
                "#shorts",
                " shorts",
                "shorts ");
        boolean looksLikeNonMusic = containsAny(title,
                "podcast",
                "interview",
                "entrevista",
                "explica",
                "explains",
                "trailer",
                "teaser",
                "reaction",
                "news",
                "documental");
        switch (filter) {
            case VIDEOS:
                return "video".equals(type) && looksLikeVideoClip;
            case SONGS:
                return "video".equals(type)
                        && !looksLikeShort
                        && !looksLikeNonMusic;
            case ARTISTS:
                return "channel".equals(type);
            case PLAYLISTS:
                return "playlist".equals(type);
            default:
                return true;
        }
    }

    @NonNull
    private String normalizeForFilter(@Nullable String value) {
        if (TextUtils.isEmpty(value)) {
            return "";
        }

        String cached = normalizedFilterCache.get(value);
        if (cached != null) {
            return cached;
        }

        String decomposed = Normalizer.normalize(value, Normalizer.Form.NFD);
        StringBuilder builder = new StringBuilder(decomposed.length());
        for (int i = 0; i < decomposed.length(); i++) {
            char c = decomposed.charAt(i);
            if (Character.getType(c) != Character.NON_SPACING_MARK) {
                builder.append(Character.toLowerCase(c));
            }
        }

        String normalized = builder.toString().trim();
        if (normalizedFilterCache.size() > 512) {
            normalizedFilterCache.clear();
        }
        normalizedFilterCache.put(value, normalized);
        return normalized;
    }

    private boolean containsAny(@NonNull String text, @NonNull String... terms) {
        for (String term : terms) {
            if (text.contains(term)) {
                return true;
            }
        }
        return false;
    }

    @NonNull
    private String filterLabel(@NonNull ChipFilter filter) {
        switch (filter) {
            case VIDEOS:
                return "Videos";
            case SONGS:
                return "Canciones";
            case ARTISTS:
                return "Artistas";
            case PLAYLISTS:
                return "Playlists";
            default:
                return "Todos";
        }
    }

    private void applyChipStyles() {
        styleChip(btnChipVideos, activeFilter == ChipFilter.VIDEOS);
        styleChip(btnChipSongs, activeFilter == ChipFilter.SONGS);
        styleChip(btnChipArtists, activeFilter == ChipFilter.ARTISTS);
        styleChip(btnChipPlaylists, activeFilter == ChipFilter.PLAYLISTS);
    }

    private void styleChip(@NonNull MaterialButton chip, boolean selected) {
        if (chip == null) return;
        int bg = selected ? ContextCompat.getColor(requireContext(), R.color.stitch_blue) : ContextCompat.getColor(requireContext(), R.color.surface_low);
        int fg = selected ? ContextCompat.getColor(requireContext(), android.R.color.white) : ContextCompat.getColor(requireContext(), R.color.text_primary);
        chip.setBackgroundTintList(ColorStateList.valueOf(bg));
        chip.setTextColor(fg);
    }

    private int bucketArtworkDimension(int value) {
        int safe = Math.max(1, value);
        return Math.max(64, ((safe + 63) / 64) * 64);
    }

    private int resolveArtworkTargetSize(int measured, int layoutValue) {
        if (measured > 0) {
            return measured;
        }
        if (layoutValue > 0) {
            return layoutValue;
        }
        return 0;
    }

    private void loadArtworkInto(@NonNull ImageView target, @Nullable String imageUrl) {
        if (TextUtils.isEmpty(imageUrl)) {
            target.setTag(R.id.tag_artwork_signature, null);
            target.setImageResource(R.drawable.ic_music);
            return;
        }

        String safeUrl = imageUrl.trim();
        ViewGroup.LayoutParams params = target.getLayoutParams();
        int rawWidth = resolveArtworkTargetSize(target.getWidth(), params == null ? 0 : params.width);
        int rawHeight = resolveArtworkTargetSize(target.getHeight(), params == null ? 0 : params.height);

        String signature;
        if (rawWidth > 0 && rawHeight > 0) {
            int targetWidth;
            int targetHeight;
            if (YouTubeImageProcessor.shouldProcess(safeUrl)) {
                targetWidth = YouTubeImageProcessor.decodeDimensionForSmartCrop(rawWidth);
                targetHeight = YouTubeImageProcessor.decodeDimensionForSmartCrop(rawHeight);
            } else {
                targetWidth = bucketArtworkDimension(rawWidth);
                targetHeight = bucketArtworkDimension(rawHeight);
            }
            signature = safeUrl + "|" + targetWidth + "x" + targetHeight;
            Object previousSignature = target.getTag(R.id.tag_artwork_signature);
            if (previousSignature instanceof String && signature.equals(previousSignature)) {
                return;
            }
            target.setTag(R.id.tag_artwork_signature, signature);
            Glide.with(target)
                    .load(safeUrl)
                    .transform(new YouTubeCropTransformation())
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .override(targetWidth, targetHeight)
                    .placeholder(R.drawable.ic_music)
                    .error(R.drawable.ic_music)
                    .into(target);
            return;
        }

        signature = safeUrl + "|auto";
        Object previousSignature = target.getTag(R.id.tag_artwork_signature);
        if (previousSignature instanceof String && signature.equals(previousSignature)) {
            return;
        }
        target.setTag(R.id.tag_artwork_signature, signature);

        RequestBuilder<Drawable> request = Glide.with(target)
                .load(safeUrl)
                .transform(new YouTubeCropTransformation())
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .placeholder(R.drawable.ic_music)
                .error(R.drawable.ic_music);
        if (YouTubeImageProcessor.shouldProcess(safeUrl)) {
            int side = YouTubeImageProcessor.decodeDimensionForSmartCrop(64);
            request = request.override(side, side);
        }
        request.into(target);
    }


    private void bindFeaturedTrack(@NonNull YouTubeMusicService.TrackResult track) {
        tvFeaturedTitle.setText(track.title);
        String typeLabel = searchTypeLabel(track);
        String subtitle = TextUtils.isEmpty(track.subtitle)
            ? typeLabel
            : typeLabel + " • " + track.subtitle;
        tvFeaturedSubtitle.setText(subtitle);

        loadArtworkInto(ivFeaturedThumb, track.thumbnailUrl);
    }

    private void sortSearchResultsByBestMatch(
            @NonNull List<YouTubeMusicService.TrackResult> source,
            @NonNull String query
    ) {
        String normalizedQuery = normalizeForFilter(query);
        if (normalizedQuery.isEmpty()) {
            return;
        }
        String[] queryTokens = normalizedQuery.split("\\s+");

        source.sort((left, right) -> {
            int leftScore = computeSearchMatchScore(left, normalizedQuery, queryTokens);
            int rightScore = computeSearchMatchScore(right, normalizedQuery, queryTokens);
            if (leftScore != rightScore) {
                return Integer.compare(rightScore, leftScore);
            }

            String leftTitle = normalizeForFilter(left.title);
            String rightTitle = normalizeForFilter(right.title);
            return leftTitle.compareTo(rightTitle);
        });
    }

    private int computeSearchMatchScore(
            @NonNull YouTubeMusicService.TrackResult track,
            @NonNull String normalizedQuery,
            @NonNull String[] queryTokens
    ) {
        String title = normalizeForFilter(track.title);
        String subtitle = normalizeForFilter(track.subtitle);
        int score = 0;

        if (title.equals(normalizedQuery)) {
            score += 800;
        } else if (title.startsWith(normalizedQuery + " ")) {
            score += 760;
        } else if (title.startsWith(normalizedQuery)) {
            score += 560;
        } else if (title.contains(normalizedQuery)) {
            score += 340;
        }

        if (title.startsWith(normalizedQuery)
                && title.length() > normalizedQuery.length()) {
            char next = title.charAt(normalizedQuery.length());
            if (Character.isLetterOrDigit(next)) {
                score -= 180;
            }
        }

        if (containsWordToken(title, normalizedQuery)) {
            score += 320;
        }

        if (subtitle.equals(normalizedQuery)) {
            score += 220;
        } else if (subtitle.startsWith(normalizedQuery)) {
            score += 180;
        } else if (subtitle.contains(normalizedQuery)) {
            score += 120;
        }

        for (String token : queryTokens) {
            if (token == null || token.isEmpty()) {
                continue;
            }
            if (title.startsWith(token + " ")) {
                score += 92;
            } else if (title.startsWith(token)) {
                score += 75;
            } else if (title.contains(token)) {
                score += 45;
            }

            if (containsWordToken(title, token)) {
                score += 58;
            }

            if (subtitle.startsWith(token)) {
                score += 24;
            } else if (subtitle.contains(token)) {
                score += 14;
            }
        }

        if ("video".equals(track.resultType)) {
            score += 12;
        }
        return score;
    }

    private boolean containsWordToken(@NonNull String source, @NonNull String token) {
        if (token.isEmpty()) {
            return false;
        }

        if (source.equals(token)
                || source.startsWith(token + " ")
                || source.contains(" " + token + " ")
                || source.endsWith(" " + token)) {
            return true;
        }

        return source.contains("(" + token + ")")
                || source.contains("-" + token + " ")
                || source.contains(" " + token + "-");
    }

    @NonNull
    private String searchTypeLabel(@NonNull YouTubeMusicService.TrackResult track) {
        if ("playlist".equals(track.resultType)) {
            return "Playlist";
        }
        if ("channel".equals(track.resultType)) {
            return "Artista";
        }

        return "Cancion";
    }

    private void showSearchTrackOptions(@NonNull YouTubeMusicService.TrackResult track, @NonNull View anchor) {
        if (!isAdded()) {
            return;
        }

        com.google.android.material.bottomsheet.BottomSheetDialog sheet = new com.google.android.material.bottomsheet.BottomSheetDialog(requireContext());
        View sheetRoot = getLayoutInflater().inflate(R.layout.bottom_sheet_track_options, null);

        TextView tvSheetTitle = sheetRoot.findViewById(R.id.tvSheetTitle);
        TextView tvSheetSubtitle = sheetRoot.findViewById(R.id.tvSheetSubtitle);

        tvSheetSubtitle.setText(track.subtitle);
        
        ImageView ivSheetClose = sheetRoot.findViewById(R.id.ivSheetClose);
        ivSheetClose.setOnClickListener(v -> sheet.dismiss());

        // Configure Grid Actions for Search Results
        // Map play action to the first grid button (Play Next visually, but acts as Reproducir)
        sheetRoot.findViewById(R.id.btnGridPlayNext).setOnClickListener(v -> {
            openTrack(track);
            sheet.dismiss();
        });
        TextView tvGridPlayNext = (TextView) ((ViewGroup) sheetRoot.findViewById(R.id.btnGridPlayNext).getParent()).getChildAt(1);
        tvGridPlayNext.setText("Reproducir");

        // Hide others since Search doesn't support them yet
        ((View) sheetRoot.findViewById(R.id.btnGridQueue).getParent()).setVisibility(View.GONE);
        ((View) sheetRoot.findViewById(R.id.btnGridDownload).getParent()).setVisibility(View.GONE);

        LinearLayout listContainer = sheetRoot.findViewById(R.id.llSheetListActions);

        // Add additional list options
        if (track.isVideo()) {
            listContainer.addView(createPlaylistActionRow(
                    R.drawable.ic_favorite_star,
                    "Agregar a favoritos",
                    () -> {
                        addSearchTrackToFavorites(track);
                        sheet.dismiss();
                    },
                    sheet
            ));
            listContainer.addView(createPlaylistActionRow(
                    R.drawable.ic_stream_queue_add,
                    "Añadir a playlist",
                    () -> {
                        showAddToPlaylistDialog(track);
                        sheet.dismiss();
                    },
                    sheet
            ));
        } else if (track.isPlaylist()) {
            listContainer.addView(createPlaylistActionRow(
                    R.drawable.ic_favorite_star,
                    "Agregar a biblioteca",
                    () -> {
                        addSearchPlaylistToLibrary(track);
                        sheet.dismiss();
                    },
                    sheet
            ));
        }

        sheet.setContentView(sheetRoot);
        View parent = (View) sheetRoot.getParent();
        if (parent != null) {
            parent.setBackgroundColor(Color.TRANSPARENT);
        }
        sheet.show();
    }

    private void showAddToPlaylistDialog(@NonNull YouTubeMusicService.TrackResult track) {
        if (!isAdded()) return;

        List<String> localNames = CustomPlaylistsStore.INSTANCE.getAllPlaylistNames(requireContext());
        String token = resolveAccessTokenForPlaylistDetail();

        if (TextUtils.isEmpty(token)) {
            // Only local
            if (localNames.isEmpty()) {
                android.widget.Toast.makeText(requireContext(), "No tienes playlists. Crea una primero.", android.widget.Toast.LENGTH_SHORT).show();
                return;
            }
            showAddToPlaylistDialogInternal(track, localNames, Collections.emptyList());
            return;
        }

        // Show loading and fetch YT playlists
        android.widget.Toast.makeText(requireContext(), "Cargando tus listas de YouTube...", android.widget.Toast.LENGTH_SHORT).show();
        youTubeMusicService.fetchMyPlaylists(token, 50, new YouTubeMusicService.PlaylistsCallback() {
            @Override
            public void onSuccess(@NonNull List<YouTubeMusicService.PlaylistResult> playlists) {
                if (!isAdded()) return;
                showAddToPlaylistDialogInternal(track, localNames, playlists);
            }

            @Override
            public void onError(@NonNull String error) {
                if (!isAdded()) return;
                showAddToPlaylistDialogInternal(track, localNames, Collections.emptyList());
            }
        });
    }

    private void showAddToPlaylistDialogInternal(@NonNull YouTubeMusicService.TrackResult track, @NonNull List<String> localNames, @NonNull List<YouTubeMusicService.PlaylistResult> ytPlaylists) {
        List<String> displayNames = new ArrayList<>();
        for (String name : localNames) {
            displayNames.add(name);
        }
        for (YouTubeMusicService.PlaylistResult playlist : ytPlaylists) {
            displayNames.add(playlist.title);
        }

        if (displayNames.isEmpty()) {
            android.widget.Toast.makeText(requireContext(), "No tienes playlists. Crea una primero.", android.widget.Toast.LENGTH_SHORT).show();
            return;
        }

        String[] array = displayNames.toArray(new String[0]);
        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Añadir a playlist")
            .setItems(array, (dialog, which) -> {
                if (which < localNames.size()) {
                    String selected = localNames.get(which);
                    CustomPlaylistsStore.INSTANCE.addTrackToPlaylist(
                        requireContext(),
                        selected,
                        track.videoId,
                        TextUtils.isEmpty(track.title) ? "Tema" : track.title,
                        track.subtitle == null ? "" : track.subtitle,
                        "",
                        track.thumbnailUrl == null ? "" : track.thumbnailUrl
                    );
                    android.widget.Toast.makeText(requireContext(), "Añadida a " + selected, android.widget.Toast.LENGTH_SHORT).show();
                    fetchLibraryPlaylists(true);
                } else {
                    int ytIdx = which - localNames.size();
                    YouTubeMusicService.PlaylistResult selected = ytPlaylists.get(ytIdx);
                    String token = resolveAccessTokenForPlaylistDetail();
                    youTubeMusicService.insertTrackToPlaylist(token, selected.playlistId, track.videoId, (success, error) -> {
                        if (!isAdded()) return;
                        if (success) {
                            android.widget.Toast.makeText(requireContext(), "Añadida a YouTube: " + selected.title, android.widget.Toast.LENGTH_SHORT).show();
                        } else {
                            android.widget.Toast.makeText(requireContext(), "Error YouTube: " + error, android.widget.Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            })
            .setNegativeButton("Cancelar", null)
            .show();
    }

    private void addSearchTrackToFavorites(@NonNull YouTubeMusicService.TrackResult track) {
        if (!isAdded() || !track.isVideo()) {
            return;
        }

        FavoritesPlaylistStore.upsertFavorite(
                requireContext(),
                track.videoId,
                TextUtils.isEmpty(track.title) ? "Tema" : track.title,
                track.subtitle == null ? "" : track.subtitle,
                "--:--",
                track.thumbnailUrl == null ? "" : track.thumbnailUrl
        );

        ensureFavoritesPlaylistInLibraryTracks();
    }

    private void shareTrackResult(@NonNull YouTubeMusicService.TrackResult track) {
        if (!isAdded()) {
            return;
        }

        try {
            String watchUrl = track.getWatchUrl();
            String title = TextUtils.isEmpty(track.title) ? "Mira este resultado" : track.title;
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("text/plain");
            shareIntent.putExtra(Intent.EXTRA_SUBJECT, title);
            shareIntent.putExtra(Intent.EXTRA_TEXT, title + "\n" + watchUrl);
            startActivity(Intent.createChooser(shareIntent, "Compartir"));
        } catch (Exception e) {
            
        }
    }

    private int dp(int value) {
        if (!isAdded()) {
            return value;
        }
        return Math.round(requireContext().getResources().getDisplayMetrics().density * value);
    }

    private void launchSearchActivity() {
        if (!isAdded()) return;
        Intent intent = new Intent(requireContext(), SearchActivity.class);
        searchActivityLauncher.launch(intent);
    }

    private void handleSearchActivityResult(int resultCode, @NonNull Intent data) {
        if (resultCode == SearchActivity.RESULT_TRACK_SELECTED || resultCode == SearchActivity.RESULT_PLAYLIST_SELECTED) {
            playTrackFromSearchInternal(data);
        }
    }

    public void playTrackFromSearch(@NonNull Intent data) {
        if (!isAdded()) return;
        playTrackFromSearchInternal(data);
    }

    private void playTrackFromSearchInternal(@NonNull Intent data) {
        String resultType = data.getStringExtra(SearchActivity.EXTRA_RESULT_TYPE);
        String videoId = data.getStringExtra(SearchActivity.EXTRA_RESULT_VIDEO_ID);
        String contentId = data.getStringExtra(SearchActivity.EXTRA_RESULT_CONTENT_ID);
        String title = data.getStringExtra(SearchActivity.EXTRA_RESULT_TITLE);
        String subtitle = data.getStringExtra(SearchActivity.EXTRA_RESULT_SUBTITLE);
        String thumbnailUrl = data.getStringExtra(SearchActivity.EXTRA_RESULT_THUMBNAIL);
        String queueJson = data.getStringExtra(SearchActivity.EXTRA_RESULT_TRACKS_JSON);

        YouTubeMusicService.TrackResult track = new YouTubeMusicService.TrackResult(
                resultType, contentId, title, subtitle, thumbnailUrl);
        
        // Populate tracks for queue finding
        tracks.clear();
        featuredTrack = track;
        try {
            if (queueJson != null) {
                org.json.JSONArray array = new org.json.JSONArray(queueJson);
                for (int i = 0; i < array.length(); i++) {
                    org.json.JSONObject obj = array.getJSONObject(i);
                    YouTubeMusicService.TrackResult qTrack = new YouTubeMusicService.TrackResult(
                            obj.optString("resultType", ""),
                            obj.optString("contentId", ""),
                            obj.optString("title", ""),
                            obj.optString("subtitle", ""),
                            obj.optString("thumbnailUrl", "")
                    );
                    tracks.add(qTrack);
                }
            }
        } catch (Exception ignored) {
        }

        openTrack(track);
    }


    private void openTrack(@NonNull YouTubeMusicService.TrackResult track) {
        if (ACTION_NEW_PLAYLIST_ID.equals(track.contentId)) {
            showCreatePlaylistDialog();
            return;
        }

        if ("playlist".equals(track.resultType)) {
            openPlaylistDetail(track);
            return;
        }

        if (track.isVideo()) {
            openTrackInIntegratedPlayer(track);
            return;
        }

        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(track.getWatchUrl()));
            startActivity(intent);
        } catch (Exception ignored) {
            if (isAdded()) {
                
            }
        }
    }

    private boolean shouldUseRadioQueueForTrack(@NonNull YouTubeMusicService.TrackResult selectedTrack) {
        if (!selectedTrack.isVideo()) {
            return false;
        }

        if (activeScreen == ScreenMode.SEARCH) {
            return true;
        }

        return activeScreen == ScreenMode.LIBRARY && !TextUtils.isEmpty(getLibraryInlineQuery());
    }

    @NonNull
    private ArrayList<YouTubeMusicService.TrackResult> buildPlaybackQueueCandidates(
            @NonNull YouTubeMusicService.TrackResult selectedTrack,
            boolean radioMode
    ) {
        ArrayList<YouTubeMusicService.TrackResult> queue = new ArrayList<>();
        HashSet<String> seenVideoIds = new HashSet<>();

        if (!TextUtils.isEmpty(selectedTrack.videoId)) {
            queue.add(selectedTrack);
            seenVideoIds.add(selectedTrack.videoId);
        }

        if (featuredTrack != null
                && featuredTrack.isVideo()
                && !TextUtils.isEmpty(featuredTrack.videoId)
                && !seenVideoIds.contains(featuredTrack.videoId)) {
            queue.add(featuredTrack);
            seenVideoIds.add(featuredTrack.videoId);
        }

        for (YouTubeMusicService.TrackResult item : tracks) {
            if (item == null || !item.isVideo() || TextUtils.isEmpty(item.videoId)) {
                continue;
            }
            if (seenVideoIds.contains(item.videoId)) {
                continue;
            }
            queue.add(item);
            seenVideoIds.add(item.videoId);
        }

        if (!radioMode || queue.size() <= 2) {
            return queue;
        }

        YouTubeMusicService.TrackResult anchor = queue.get(0);
        ArrayList<YouTubeMusicService.TrackResult> tail = new ArrayList<>(queue.subList(1, queue.size()));
        tail.sort((left, right) -> {
            int leftScore = computeRadioQueueScore(anchor, left);
            int rightScore = computeRadioQueueScore(anchor, right);
            if (leftScore != rightScore) {
                return Integer.compare(rightScore, leftScore);
            }
            String leftTitle = normalizeForFilter(left.title);
            String rightTitle = normalizeForFilter(right.title);
            return leftTitle.compareTo(rightTitle);
        });

        ArrayList<YouTubeMusicService.TrackResult> radioQueue = new ArrayList<>(queue.size());
        radioQueue.add(anchor);
        radioQueue.addAll(tail);
        return radioQueue;
    }

    private int computeRadioQueueScore(
            @NonNull YouTubeMusicService.TrackResult anchor,
            @NonNull YouTubeMusicService.TrackResult candidate
    ) {
        if (TextUtils.equals(anchor.videoId, candidate.videoId)) {
            return Integer.MAX_VALUE;
        }

        String anchorTitle = normalizeForFilter(anchor.title);
        String anchorSubtitle = normalizeForFilter(anchor.subtitle);
        String candidateTitle = normalizeForFilter(candidate.title);
        String candidateSubtitle = normalizeForFilter(candidate.subtitle);

        int score = 0;
        if (!anchorSubtitle.isEmpty() && candidateSubtitle.contains(anchorSubtitle)) {
            score += 240;
        }

        String[] tokens = extractSearchTokens((anchorTitle + " " + anchorSubtitle).trim());
        for (String token : tokens) {
            if (token.length() < 3) {
                continue;
            }
            if (candidateTitle.contains(token)) {
                score += 85;
            }
            if (candidateSubtitle.contains(token)) {
                score += 40;
            }
        }

        if (candidateTitle.startsWith(anchorTitle) || anchorTitle.startsWith(candidateTitle)) {
            score += 110;
        }
        if (candidateSubtitle.startsWith(anchorSubtitle) || anchorSubtitle.startsWith(candidateSubtitle)) {
            score += 60;
        }
        return score;
    }



    private void openTrackInIntegratedPlayer(@NonNull YouTubeMusicService.TrackResult selectedTrack) {
        if (!isAdded()) {
            return;
        }

        if (TextUtils.isEmpty(selectedTrack.videoId)) {
            
            return;
        }

        ArrayList<String> ids = new ArrayList<>();
        ArrayList<String> titles = new ArrayList<>();
        ArrayList<String> artists = new ArrayList<>();
        ArrayList<String> durations = new ArrayList<>();
        ArrayList<String> images = new ArrayList<>();

        boolean radioMode = shouldUseRadioQueueForTrack(selectedTrack);
        ArrayList<YouTubeMusicService.TrackResult> queueCandidates = buildPlaybackQueueCandidates(selectedTrack, radioMode);

        int selectedIndex = -1;
        for (YouTubeMusicService.TrackResult item : queueCandidates) {
            ids.add(item.videoId);
            titles.add(TextUtils.isEmpty(item.title) ? "Tema" : item.title);
            artists.add(item.subtitle == null ? "" : item.subtitle);
            durations.add("--:--");
            images.add(item.thumbnailUrl == null ? "" : item.thumbnailUrl);

            if (selectedIndex < 0 && TextUtils.equals(item.videoId, selectedTrack.videoId)) {
                selectedIndex = ids.size() - 1;
            }
        }

        if (selectedIndex < 0) {
            // El "mejor resultado" no vive en `tracks` (la lista secundaria),
            // por eso lo inyectamos al inicio para reproducir exactamente ese.
            ids.add(0, selectedTrack.videoId);
            titles.add(0, TextUtils.isEmpty(selectedTrack.title) ? "Tema" : selectedTrack.title);
            artists.add(0, selectedTrack.subtitle == null ? "" : selectedTrack.subtitle);
            durations.add(0, "--:--");
            images.add(0, selectedTrack.thumbnailUrl == null ? "" : selectedTrack.thumbnailUrl);
            selectedIndex = 0;
        }

        SongPlayerFragment existingPlayer = findSongPlayerFragment();
        if (existingPlayer != null && existingPlayer.isAdded()) {
            existingPlayer.externalSetReturnTargetTag(TAG_MODULE_MUSIC);
            existingPlayer.externalReplaceQueueFromStart(ids, titles, artists, durations, images, selectedIndex, true);

            getParentFragmentManager().beginTransaction()
                    .setReorderingAllowed(true)
                    .hide(this)
                    .show(existingPlayer)
                    .commit();
        } else {
            if (selectedIndex >= 0 && selectedIndex < ids.size()) {
                clearPersistedPositionForVideoId(ids.get(selectedIndex));
            }

            SongPlayerFragment playerFragment = SongPlayerFragment.newInstance(
                    ids,
                    titles,
                    artists,
                    durations,
                    images,
                    selectedIndex,
                    true
            );
            playerFragment.externalSetReturnTargetTag(TAG_MODULE_MUSIC);

            getParentFragmentManager().beginTransaction()
                    .setReorderingAllowed(true)
                    .hide(this)
                    .add(R.id.fragmentContainer, playerFragment, "song_player")
                    .commit();
        }

        invalidateMiniSnapshotCache();
        updateMiniPlayerUi();
    }

    private void openPlaylistDetail(@NonNull YouTubeMusicService.TrackResult track) {
        if (!isAdded()) {
            return;
        }

        if (getParentFragmentManager().isStateSaved()) {
            return;
        }

        String accessTokenForDetail = resolveAccessTokenForPlaylistDetail();

        PlaylistDetailFragment detailFragment = PlaylistDetailFragment.newInstance(
                track.contentId,
                track.title,
                track.subtitle,
                track.thumbnailUrl,
            accessTokenForDetail
        );

        androidx.fragment.app.Fragment existingDetail = getParentFragmentManager().findFragmentByTag("playlist_detail");

        androidx.fragment.app.FragmentTransaction transaction = getParentFragmentManager()
            .beginTransaction()
                .setReorderingAllowed(true)
            .hide(this);

        if (existingDetail != null && existingDetail.isAdded() && existingDetail != this) {
            transaction.remove(existingDetail);
        }

        transaction
            .add(R.id.fragmentContainer, detailFragment, "playlist_detail")
            .addToBackStack("playlist_detail")
            .commit();
    }

    @NonNull
    private String resolveAccessTokenForPlaylistDetail() {
        String inMemory = youtubeAccessToken == null ? "" : youtubeAccessToken.trim();
        if (!TextUtils.isEmpty(inMemory)) {
            return inMemory;
        }

        String accountKey = resolveAccountKey();
        if (hasValidCachedToken(accountKey)) {
            youtubeAccessToken = cachedYoutubeAccessToken;
            return cachedYoutubeAccessToken;
        }

        if (!isAdded()) {
            return "";
        }

        SharedPreferences prefs = requireContext().getSharedPreferences(PREFS_PLAYER_STATE, Activity.MODE_PRIVATE);
        String persisted = prefs.getString(PREF_LAST_YOUTUBE_ACCESS_TOKEN, "");
        if (persisted == null) {
            return "";
        }
        String trimmed = persisted.trim();
        if (!trimmed.isEmpty()) {
            youtubeAccessToken = trimmed;
        }
        return trimmed;
    }

    private boolean isNetworkAvailable() {
        if (!isAdded()) {
            return false;
        }

        ConnectivityManager cm = ContextCompat.getSystemService(requireContext(), ConnectivityManager.class);
        if (cm == null) {
            return false;
        }

        NetworkCapabilities capabilities = cm.getNetworkCapabilities(cm.getActiveNetwork());
        if (capabilities == null) {
            return false;
        }

        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                || capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
                || capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
                || capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN);
    }

    private boolean isLikelyNetworkError(@NonNull String error) {
        String normalized = normalizeForFilter(error);
        return containsAny(normalized,
                "network",
                "sin internet",
                "unable to resolve host",
                "failed to connect",
                "timeout",
                "timed out",
                "connection reset",
                "no address associated");
    }

    private void showCreatePlaylistDialog() {
        if (!isAdded()) return;
        android.widget.EditText input = new android.widget.EditText(requireContext());
        input.setHint("Nombre de la playlist");
        input.setSingleLine(true);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        input.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        LinearLayout container = new LinearLayout(requireContext());
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(dp(24), dp(6), dp(24), 0);
        container.addView(input);

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Nueva Playlist")
                .setView(container)
                .setPositiveButton("Crear", (dialog, which) -> {
                    String name = input.getText().toString().trim();
                    if (!TextUtils.isEmpty(name)) {
                        boolean success = CustomPlaylistsStore.INSTANCE.createPlaylist(requireContext(), name);
                        if (success) {
                            android.widget.Toast.makeText(requireContext(), "Playlist creada en Firebase", android.widget.Toast.LENGTH_SHORT).show();
                            fetchLibraryPlaylists(true); // reload playlists
                        } else {
                            android.widget.Toast.makeText(requireContext(), "La playlist ya existe", android.widget.Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    @Override
    public void onDestroyView() {
        if (clearSearchBackPressedCallback != null) {
            clearSearchBackPressedCallback.remove();
            clearSearchBackPressedCallback = null;
        }
        cancelPendingLibraryInlineSearch();
        queuedSearchQuery = null;
        lastLibraryInlineDispatchedQuery = "";
        dismissPlaylistActionTooltip();
        stopObservingOfflineQueue();
        stopMiniProgressTicker();
        invalidateMiniSnapshotCache();
        resetMiniPlayerRenderCache();
        normalizedFilterCache.clear();
        super.onDestroyView();
    }

    @Override
    public void onDestroy() {
        authExecutor.shutdownNow();
        offlineStateExecutor.shutdownNow();
        offlinePrefetchExecutor.shutdownNow();
        super.onDestroy();
    }

    private static final class PendingOfflinePlaylistCandidate {
        @NonNull
        final String playlistId;
        @NonNull
        final String playlistTitle;
        final int pendingCount;
        @NonNull
        final List<CachedPlaylistTrack> cachedTracks;

        private PendingOfflinePlaylistCandidate(
                @NonNull String playlistId,
                @NonNull String playlistTitle,
                int pendingCount,
                @NonNull List<CachedPlaylistTrack> cachedTracks
        ) {
            this.playlistId = playlistId;
            this.playlistTitle = playlistTitle;
            this.pendingCount = Math.max(0, pendingCount);
            this.cachedTracks = cachedTracks;
        }
    }

    private static final class PendingOfflineTrackCandidate {
        @NonNull
        final String videoId;
        @NonNull
        final String title;
        @NonNull
        final String artist;
        @NonNull
        final String duration;

        private PendingOfflineTrackCandidate(
                @NonNull String videoId,
                @Nullable String title,
                @Nullable String artist,
                @Nullable String duration
        ) {
            this.videoId = videoId;
            this.title = title == null ? "" : title;
            this.artist = artist == null ? "" : artist;
            this.duration = duration == null ? "" : duration;
        }
    }

    private static final class PendingOfflineBatchCandidate {
        @NonNull
        final String primaryPlaylistId;
        @NonNull
        final String primaryPlaylistTitle;
        @NonNull
        final List<String> playlistIds;
        @NonNull
        final List<PendingOfflineTrackCandidate> tracks;

        private PendingOfflineBatchCandidate(
                @NonNull String primaryPlaylistId,
                @NonNull String primaryPlaylistTitle,
                @NonNull List<String> playlistIds,
                @NonNull List<PendingOfflineTrackCandidate> tracks
        ) {
            this.primaryPlaylistId = primaryPlaylistId;
            this.primaryPlaylistTitle = primaryPlaylistTitle;
            this.playlistIds = playlistIds;
            this.tracks = tracks;
        }
    }

    private final class MusicResultsAdapter extends RecyclerView.Adapter<MusicResultsAdapter.TrackViewHolder> {

        private static final int VIEW_TYPE_LIST = 0;
        private static final int VIEW_TYPE_SEARCH = 2;

        private final List<YouTubeMusicService.TrackResult> data = new ArrayList<>();
        private final OnLibraryTrackClick onTrackClick;
        private final OnLibraryTrackMoreClick onTrackMoreClick;
        private final Map<String, Boolean> playlistOfflineCompleteCache = new HashMap<>();
        private final Set<String> playlistOfflineRefreshInFlight = new HashSet<>();
        private long playlistOfflineStateGeneration;
        private boolean searchMode;

        MusicResultsAdapter(
                @NonNull OnLibraryTrackClick onTrackClick,
                @NonNull OnLibraryTrackMoreClick onTrackMoreClick
        ) {
            this.onTrackClick = onTrackClick;
            this.onTrackMoreClick = onTrackMoreClick;
            setHasStableIds(true);
        }

        void invalidatePlaylistOfflineState(@Nullable String playlistId) {
            if (TextUtils.isEmpty(playlistId)) {
                playlistOfflineStateGeneration++;
                playlistOfflineCompleteCache.clear();
                playlistOfflineRefreshInFlight.clear();

                long generation = playlistOfflineStateGeneration;
                for (YouTubeMusicService.TrackResult item : data) {
                    if (!"playlist".equals(item.resultType)) {
                        continue;
                    }

                    String candidateId = item.contentId == null ? "" : item.contentId.trim();
                    if (candidateId.isEmpty()) {
                        continue;
                    }

                    playlistOfflineCompleteCache.put(candidateId, isPersistedPlaylistOfflineComplete(candidateId));
                    requestPlaylistOfflineStateRefreshAsync(candidateId, generation);
                }

                if (!data.isEmpty()) {
                    notifyItemRangeChanged(0, data.size());
                }
            } else {
                playlistOfflineCompleteCache.remove(playlistId);
                requestPlaylistOfflineStateRefreshAsync(playlistId, playlistOfflineStateGeneration);
                int updatedPosition = findPlaylistPositionById(playlistId);
                if (updatedPosition >= 0) {
                    notifyItemChanged(updatedPosition);
                }
            }
        }

        void notifyPlayingPlaylistChanged(@Nullable String previousPlaylistId, @Nullable String currentPlaylistId) {
            String previous = previousPlaylistId == null ? "" : previousPlaylistId.trim();
            String current = currentPlaylistId == null ? "" : currentPlaylistId.trim();

            int previousPosition = findPlaylistPositionById(previous);
            int currentPosition = findPlaylistPositionById(current);

            if (previousPosition >= 0) {
                notifyItemChanged(previousPosition);
            }
            if (currentPosition >= 0 && currentPosition != previousPosition) {
                notifyItemChanged(currentPosition);
            }
        }

        @Override
        public long getItemId(int position) {
            if (position < 0 || position >= data.size()) {
                return RecyclerView.NO_ID;
            }
            YouTubeMusicService.TrackResult item = data.get(position);
            String identity = (item.resultType == null ? "" : item.resultType)
                    + "|"
                    + (item.contentId == null ? "" : item.contentId)
                    + "|"
                    + (item.title == null ? "" : item.title);
            return identity.hashCode();
        }

        void setSearchMode(boolean searchMode) {
            if (this.searchMode == searchMode) {
                return;
            }
            this.searchMode = searchMode;
            notifyDataSetChanged();
        }

        void submitResults(@NonNull List<YouTubeMusicService.TrackResult> newData) {
            List<YouTubeMusicService.TrackResult> previous = new ArrayList<>(data);
            List<YouTubeMusicService.TrackResult> incoming = new ArrayList<>(newData);

            DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(new DiffUtil.Callback() {
                @Override
                public int getOldListSize() {
                    return previous.size();
                }

                @Override
                public int getNewListSize() {
                    return incoming.size();
                }

                @Override
                public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
                    YouTubeMusicService.TrackResult oldItem = previous.get(oldItemPosition);
                    YouTubeMusicService.TrackResult newItem = incoming.get(newItemPosition);

                    if (!TextUtils.isEmpty(oldItem.contentId) && !TextUtils.isEmpty(newItem.contentId)) {
                        return TextUtils.equals(oldItem.contentId, newItem.contentId)
                                && TextUtils.equals(oldItem.resultType, newItem.resultType);
                    }
                    return TextUtils.equals(oldItem.title, newItem.title)
                            && TextUtils.equals(oldItem.subtitle, newItem.subtitle);
                }

                @Override
                public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                    YouTubeMusicService.TrackResult oldItem = previous.get(oldItemPosition);
                    YouTubeMusicService.TrackResult newItem = incoming.get(newItemPosition);
                    return TextUtils.equals(oldItem.resultType, newItem.resultType)
                            && TextUtils.equals(oldItem.contentId, newItem.contentId)
                            && TextUtils.equals(oldItem.title, newItem.title)
                            && TextUtils.equals(oldItem.subtitle, newItem.subtitle)
                            && TextUtils.equals(oldItem.thumbnailUrl, newItem.thumbnailUrl);
                }
            });

            data.clear();
            data.addAll(incoming);
            playlistOfflineStateGeneration++;
            playlistOfflineCompleteCache.clear();
            playlistOfflineRefreshInFlight.clear();

            for (YouTubeMusicService.TrackResult item : incoming) {
                if (!"playlist".equals(item.resultType)) {
                    continue;
                }
                String playlistId = item.contentId == null ? "" : item.contentId.trim();
                if (playlistId.isEmpty()) {
                    continue;
                }
                playlistOfflineCompleteCache.put(playlistId, isPersistedPlaylistOfflineComplete(playlistId));
                requestPlaylistOfflineStateRefreshAsync(playlistId, playlistOfflineStateGeneration);
            }

            diffResult.dispatchUpdatesTo(this);
        }

        @NonNull
        @Override
        public TrackViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            int layoutRes;
            if (viewType == VIEW_TYPE_SEARCH) {
                layoutRes = R.layout.item_music_search_result;
            } else {
                layoutRes = R.layout.item_music_result;
            }
            View view = LayoutInflater.from(parent.getContext()).inflate(layoutRes, parent, false);
            return new TrackViewHolder(view);
        }

        @Override
        public int getItemViewType(int position) {
            if (searchMode) {
                return VIEW_TYPE_SEARCH;
            }
            return VIEW_TYPE_LIST;
        }

        @Override
        public void onBindViewHolder(@NonNull TrackViewHolder holder, int position) {
            YouTubeMusicService.TrackResult item = data.get(position);

            if (searchMode) {
                bindSearchRow(holder, item, position);
                return;
            }

            boolean isFavoritesPlaylistStyle = isFavoritesPlaylistStyle(item);
            boolean isLikedPlaylistStyle = !isFavoritesPlaylistStyle && isLikedPlaylistStyle(item);
                boolean useFavoritesSpecialStyle = isFavoritesPlaylistStyle
                    && TextUtils.isEmpty(item.thumbnailUrl);

            if (isFavoritesPlaylistStyle) {
                holder.tvTrackTitle.setText(FavoritesPlaylistStore.PLAYLIST_TITLE);
                holder.tvTrackSubtitle.setText(TextUtils.isEmpty(item.subtitle)
                        ? FavoritesPlaylistStore.buildSubtitle(0)
                        : item.subtitle);
            } else if (isLikedPlaylistStyle) {
                holder.tvTrackTitle.setText("Musica que te gusto");
                if (TextUtils.isEmpty(item.subtitle)) {
                    holder.tvTrackSubtitle.setText("Playlist autogenerada • 0 canciones");
                } else {
                    holder.tvTrackSubtitle.setText(item.subtitle);
                }
            } else {
                holder.tvTrackTitle.setText(item.title);
                if (TextUtils.isEmpty(item.subtitle)) {
                    holder.tvTrackSubtitle.setText("Playlist");
                } else {
                    holder.tvTrackSubtitle.setText(item.subtitle);
                }
            }

            if (TextUtils.isEmpty(item.subtitle)
                    && !isLikedPlaylistStyle
                    && !ACTION_NEW_PLAYLIST_ID.equals(item.contentId)
                    && !"action".equals(item.resultType)) {
                holder.tvTrackSubtitle.setText("Playlist");
            }

            boolean specialPlaylistStyle = isLikedPlaylistStyle || useFavoritesSpecialStyle;
            holder.vLikedBackground.setVisibility(specialPlaylistStyle ? View.VISIBLE : View.GONE);
            holder.ivLikedIcon.setVisibility(specialPlaylistStyle ? View.VISIBLE : View.GONE);
            holder.ivTrackThumb.setVisibility(specialPlaylistStyle ? View.GONE : View.VISIBLE);

            if (specialPlaylistStyle) {
                holder.ivLikedIcon.setImageResource(useFavoritesSpecialStyle
                        ? R.drawable.ic_favorite_star
                        : R.drawable.ic_thumb_up_liked);
                holder.ivLikedIcon.setColorFilter(Color.WHITE);
            }

            if (!specialPlaylistStyle) {
                loadArtworkInto(holder.ivTrackThumb, item.thumbnailUrl);
            }

            holder.vTrackDivider.setVisibility(position == data.size() - 1 ? View.GONE : View.VISIBLE);

            boolean isPlaylistItem = "playlist".equals(item.resultType);
            holder.ivTrackMore.setVisibility(isPlaylistItem ? View.VISIBLE : View.GONE);
            holder.ivPlaylistOfflineAll.setVisibility(isPlaylistItem ? View.VISIBLE : View.GONE);
            if (holder.llNowPlayingOverlay != null) {
                holder.llNowPlayingOverlay.setVisibility(View.GONE);
            }

            int defaultTitleColor = ContextCompat.getColor(holder.itemView.getContext(), R.color.text_primary);
            int defaultSubtitleColor = ContextCompat.getColor(holder.itemView.getContext(), R.color.text_secondary);
            int activeTitleColor = ContextCompat.getColor(holder.itemView.getContext(), R.color.active_track_green);
            holder.tvTrackTitle.setTextColor(defaultTitleColor);
            holder.tvTrackSubtitle.setTextColor(defaultSubtitleColor);

            if (isPlaylistItem) {
                String playlistId = item.contentId == null ? "" : item.contentId.trim();
                boolean offlineAutoEnabled = false;
                boolean completeOffline = false;
                if (!TextUtils.isEmpty(playlistId)) {
                    offlineAutoEnabled = isPersistedPlaylistOfflineAutoEnabled(playlistId);
                    Boolean cachedState = playlistOfflineCompleteCache.get(playlistId);
                    if (cachedState == null) {
                        cachedState = isPersistedPlaylistOfflineComplete(playlistId);
                        playlistOfflineCompleteCache.put(playlistId, cachedState);
                        requestPlaylistOfflineStateRefreshAsync(playlistId, playlistOfflineStateGeneration);
                    }
                    completeOffline = cachedState != null && cachedState;
                }

                if (offlineAutoEnabled) {
                    holder.ivPlaylistOfflineAll.setImageResource(R.drawable.ic_check_small);
                    holder.ivPlaylistOfflineAll.setBackgroundResource(completeOffline
                            ? R.drawable.bg_offline_state_filled_primary
                            : R.drawable.bg_playlist_action_white);
                    holder.ivPlaylistOfflineAll.setColorFilter(completeOffline
                            ? ContextCompat.getColor(holder.itemView.getContext(), R.color.surface_dark)
                            : Color.BLACK);
                    holder.ivPlaylistOfflineAll.setAlpha(1f);
                } else {
                    holder.ivPlaylistOfflineAll.setImageResource(R.drawable.ic_download_bold);
                    holder.ivPlaylistOfflineAll.setBackgroundResource(R.drawable.bg_offline_state_outline_muted);
                    holder.ivPlaylistOfflineAll.setColorFilter(ContextCompat.getColor(
                            holder.itemView.getContext(),
                        R.color.text_secondary
                    ));
                    holder.ivPlaylistOfflineAll.setAlpha(0.85f);
                }

                if (isPlaylistCurrentlyPlaying(playlistId) && !specialPlaylistStyle) {
                    if (holder.llNowPlayingOverlay != null) {
                        holder.llNowPlayingOverlay.setVisibility(View.VISIBLE);
                    }
                    holder.tvTrackTitle.setTextColor(activeTitleColor);
                }

                holder.ivTrackMore.setOnClickListener(v -> onTrackMoreClick.onTrackMoreClick(item, holder.ivTrackMore));
            } else {
                holder.ivTrackMore.setOnClickListener(null);
            }

            holder.itemView.setOnClickListener(v -> onTrackClick.onTrackClick(item));
            holder.itemView.setOnLongClickListener(v -> {
                if (searchMode) {
                    showSearchTrackOptions(item, holder.ivTrackMore);
                } else {
                    onTrackMoreClick.onTrackMoreClick(item, holder.ivTrackMore);
                }
                v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                return true;
            });
        }

        private void bindSearchRow(
                @NonNull TrackViewHolder holder,
                @NonNull YouTubeMusicService.TrackResult item,
                int position
        ) {
            holder.vLikedBackground.setVisibility(View.GONE);
            holder.ivLikedIcon.setVisibility(View.GONE);
            holder.ivPlaylistOfflineAll.setVisibility(View.GONE);
            holder.ivTrackThumb.setVisibility(View.VISIBLE);
            if (holder.llNowPlayingOverlay != null) {
                holder.llNowPlayingOverlay.setVisibility(View.GONE);
            }

            String title = TextUtils.isEmpty(item.title) ? "Resultado" : item.title;
            holder.tvTrackTitle.setText(title);

            String typeLabel = searchTypeLabel(item);
            String subtitle = TextUtils.isEmpty(item.subtitle)
                    ? typeLabel
                    : typeLabel + " • " + item.subtitle;
            holder.tvTrackSubtitle.setText(subtitle);

            loadArtworkInto(holder.ivTrackThumb, item.thumbnailUrl);

            holder.vTrackDivider.setVisibility(position == data.size() - 1 ? View.GONE : View.VISIBLE);
            holder.ivTrackMore.setVisibility(View.VISIBLE);
            holder.ivTrackMore.setOnClickListener(v -> showSearchTrackOptions(item, holder.ivTrackMore));
            holder.itemView.setOnClickListener(v -> onTrackClick.onTrackClick(item));
            holder.itemView.setOnLongClickListener(v -> {
                showSearchTrackOptions(item, holder.ivTrackMore);
                v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                return true;
            });
        }

        private boolean isLikedPlaylistStyle(@NonNull YouTubeMusicService.TrackResult item) {
            if (YouTubeMusicService.SPECIAL_LIKED_VIDEOS_ID.equals(item.contentId)) {
                return true;
            }

            String normalizedTitle = item.title == null ? "" : item.title.toLowerCase(Locale.US);
            return normalizedTitle.contains("gusto")
                    || normalizedTitle.contains("liked")
                    || normalizedTitle.contains("me gusta");
        }

        private boolean isFavoritesPlaylistStyle(@NonNull YouTubeMusicService.TrackResult item) {
            return TextUtils.equals(FavoritesPlaylistStore.PLAYLIST_ID, item.contentId);
        }

        @Override
        public int getItemCount() {
            return data.size();
        }

        private void requestPlaylistOfflineStateRefreshAsync(
                @NonNull String playlistId,
                long generation
        ) {
            if (playlistId.isEmpty() || playlistOfflineRefreshInFlight.contains(playlistId)) {
                return;
            }

            playlistOfflineRefreshInFlight.add(playlistId);
            offlineStateExecutor.execute(() -> {
                boolean computed = isPlaylistFullyOffline(playlistId);
                mainHandler.post(() -> {
                    playlistOfflineRefreshInFlight.remove(playlistId);
                    if (!isAdded() || generation != playlistOfflineStateGeneration) {
                        return;
                    }

                    Boolean previous = playlistOfflineCompleteCache.put(playlistId, computed);
                    if (previous != null && previous == computed) {
                        return;
                    }

                    int updatedPosition = findPlaylistPositionById(playlistId);
                    if (updatedPosition >= 0) {
                        notifyItemChanged(updatedPosition);
                    } else {
                        notifyDataSetChanged();
                    }
                });
            });
        }

        private int findPlaylistPositionById(@NonNull String playlistId) {
            for (int i = 0; i < data.size(); i++) {
                YouTubeMusicService.TrackResult item = data.get(i);
                if (!"playlist".equals(item.resultType)) {
                    continue;
                }
                if (TextUtils.equals(playlistId, item.contentId)) {
                    return i;
                }
            }
            return -1;
        }

        final class TrackViewHolder extends RecyclerView.ViewHolder {
            final ImageView ivTrackThumb;
            final View vLikedBackground;
            final ImageView ivLikedIcon;
            final ImageView ivPlaylistOfflineAll;
            final TextView tvTrackTitle;
            final TextView tvTrackSubtitle;
            final ImageView ivTrackMore;
            final View vTrackDivider;
            final View llNowPlayingOverlay;

            TrackViewHolder(@NonNull View itemView) {
                super(itemView);
                ivTrackThumb = itemView.findViewById(R.id.ivTrackThumb);
                vLikedBackground = itemView.findViewById(R.id.vLikedBackground);
                ivLikedIcon = itemView.findViewById(R.id.ivLikedIcon);
                ivPlaylistOfflineAll = itemView.findViewById(R.id.ivPlaylistOfflineAll);
                tvTrackTitle = itemView.findViewById(R.id.tvTrackTitle);
                tvTrackSubtitle = itemView.findViewById(R.id.tvTrackSubtitle);
                ivTrackMore = itemView.findViewById(R.id.ivTrackMore);
                vTrackDivider = itemView.findViewById(R.id.vTrackDivider);
                llNowPlayingOverlay = itemView.findViewById(R.id.llNowPlayingOverlay);
            }
        }
    }

    private static final class CachedPlaylistTrack {
        final String videoId;
        final String title;
        final String artist;
        final String duration;
        final String imageUrl;

        CachedPlaylistTrack(
                @NonNull String videoId,
                @NonNull String title,
                @NonNull String artist,
                @NonNull String duration,
                @NonNull String imageUrl
        ) {
            this.videoId = videoId;
            this.title = title;
            this.artist = artist;
            this.duration = duration;
            this.imageUrl = imageUrl;
        }
    }

    private void startObservingOfflineQueue() {}
    private void stopObservingOfflineQueue() {}
    private void dismissPlaylistActionTooltip() {}
    private void stopMiniProgressTicker() {}
    private void invalidateMiniSnapshotCache() {}
    private void resetMiniPlayerRenderCache() {}
    private void onLibraryPlaylistMorePressed(@NonNull YouTubeMusicService.TrackResult track, @NonNull View anchor) {
        if (!isAdded()) return;

        com.google.android.material.bottomsheet.BottomSheetDialog sheet = new com.google.android.material.bottomsheet.BottomSheetDialog(requireContext());
        View sheetRoot = getLayoutInflater().inflate(R.layout.bottom_sheet_track_options, null);

        TextView tvSheetTitle = sheetRoot.findViewById(R.id.tvSheetTitle);
        TextView tvSheetSubtitle = sheetRoot.findViewById(R.id.tvSheetSubtitle);

        tvSheetTitle.setText(track.title);
        tvSheetSubtitle.setText(track.subtitle);
        
        ImageView ivSheetClose = sheetRoot.findViewById(R.id.ivSheetClose);
        ivSheetClose.setOnClickListener(v -> sheet.dismiss());

        // Configure Grid Actions
        // Reproducir
        sheetRoot.findViewById(R.id.btnGridPlayNext).setOnClickListener(v -> {
            openTrack(track);
            sheet.dismiss();
        });
        TextView tvGridPlayNext = (TextView) ((ViewGroup) sheetRoot.findViewById(R.id.btnGridPlayNext).getParent()).getChildAt(1);
        tvGridPlayNext.setText("Reproducir");

        // Hide Queue
        ((View) sheetRoot.findViewById(R.id.btnGridQueue).getParent()).setVisibility(View.GONE);
        
        // Sync/Download
        View btnDownload = sheetRoot.findViewById(R.id.btnGridDownload);
        if (track.isPlaylist()) {
            btnDownload.setOnClickListener(v -> {
                togglePlaylistOfflineSync(track.contentId);
                sheet.dismiss();
            });
            TextView tvDownloadLabel = sheetRoot.findViewById(R.id.tvGridDownloadLabel);
            tvDownloadLabel.setText("Sincronizar");
            
            ImageView ivDownloadIcon = sheetRoot.findViewById(R.id.ivGridDownloadIcon);
            boolean currentlyEnabled = isPersistedPlaylistOfflineAutoEnabled(track.contentId);
            ivDownloadIcon.setImageResource(currentlyEnabled ? R.drawable.ic_check_small : R.drawable.ic_download_bold);
        } else {
             ((View) btnDownload.getParent()).setVisibility(View.GONE);
        }

        LinearLayout listContainer = sheetRoot.findViewById(R.id.llSheetListActions);

        // Delete playlist (if it's a custom one or we want to allow removing from library)
        boolean canDelete = !FavoritesPlaylistStore.PLAYLIST_ID.equals(track.contentId)
                && (track.contentId.startsWith(CustomPlaylistsStore.CUSTOM_PLAYLIST_PREFIX) || !isLikedPlaylistStyle(track));

        if (canDelete) {
            listContainer.addView(createPlaylistActionRow(
                    R.drawable.ic_delete_24,
                    "Eliminar de la biblioteca",
                    () -> {
                        deletePlaylistFromLibrary(track);
                        sheet.dismiss();
                    },
                    sheet
            ));
        }

        sheet.setContentView(sheetRoot);
        View parent = (View) sheetRoot.getParent();
        if (parent != null) {
            parent.setBackgroundColor(Color.TRANSPARENT);
        }
        sheet.show();
    }

    private void addSearchPlaylistToLibrary(YouTubeMusicService.TrackResult track) {
        if (!isAdded()) return;
        // Search playlists are usually from YouTube. We'll simulate adding it to our library.
        // In a real app, this might call YT API. Here we'll ensure it shows up in our library cache.
        android.widget.Toast.makeText(requireContext(), "Agregado a tu biblioteca", android.widget.Toast.LENGTH_SHORT).show();
        // Force refresh library to show new content if we were to actually persist it
        refreshLibraryPullAsync();
    }

    private void deletePlaylistFromLibrary(YouTubeMusicService.TrackResult track) {
        if (!isAdded()) return;
        
        String id = track.contentId;
        if (id.startsWith(CustomPlaylistsStore.CUSTOM_PLAYLIST_PREFIX)) {
            String name = id.substring(CustomPlaylistsStore.CUSTOM_PLAYLIST_PREFIX.length());
            CustomPlaylistsStore.INSTANCE.deletePlaylist(requireContext(), name);
            android.widget.Toast.makeText(requireContext(), "Playlist eliminada", android.widget.Toast.LENGTH_SHORT).show();
            refreshLibraryPullAsync();
        } else {
            // For YouTube playlists, we'd normally call the API.
            // For now, we'll just indicate it's removed from local view.
            android.widget.Toast.makeText(requireContext(), "Removido de la vista", android.widget.Toast.LENGTH_SHORT).show();
            refreshLibraryPullAsync();
        }
    }

    private void togglePlaylistOfflineSync(String playlistId) {
        if (!isAdded()) return;
        boolean nowEnabled = !isPersistedPlaylistOfflineAutoEnabled(playlistId);
        setPlaylistOfflineAutoEnabled(playlistId, nowEnabled);
        
        String msg = nowEnabled ? "Sincronización activada" : "Sincronización desactivada";
        android.widget.Toast.makeText(requireContext(), msg, android.widget.Toast.LENGTH_SHORT).show();
        
        if (adapter != null) {
            adapter.invalidatePlaylistOfflineState(playlistId);
        }
    }

    private boolean isPersistedPlaylistOfflineAutoEnabled(String id) {
        if (TextUtils.isEmpty(id)) return false;
        SharedPreferences prefs = requireContext().getSharedPreferences(PREFS_PLAYER_STATE, Activity.MODE_PRIVATE);
        return prefs.getBoolean(PREF_PLAYLIST_OFFLINE_AUTO_PREFIX + id, false);
    }

    private void setPlaylistOfflineAutoEnabled(String id, boolean enabled) {
        if (TextUtils.isEmpty(id)) return;
        SharedPreferences prefs = requireContext().getSharedPreferences(PREFS_PLAYER_STATE, Activity.MODE_PRIVATE);
        prefs.edit().putBoolean(PREF_PLAYLIST_OFFLINE_AUTO_PREFIX + id, enabled).apply();
    }


    private View createPlaylistActionRow(int icon, String title, Runnable action, com.google.android.material.bottomsheet.BottomSheetDialog sheet) {
        Context context = requireContext();
        LinearLayout row = new LinearLayout(context);
        row.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) (56 * context.getResources().getDisplayMetrics().density)));
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding((int) (24 * context.getResources().getDisplayMetrics().density), 0, (int) (24 * context.getResources().getDisplayMetrics().density), 0);
        row.setGravity(Gravity.CENTER_VERTICAL);
        
        // Ripple background
        int[] attrs = new int[]{android.R.attr.selectableItemBackground};
        android.content.res.TypedArray ta = context.obtainStyledAttributes(attrs);
        row.setBackground(ta.getDrawable(0));
        ta.recycle();
        row.setClickable(true);
        row.setFocusable(true);

        ImageView iv = new ImageView(context);
        int iconSize = (int) (24 * context.getResources().getDisplayMetrics().density);
        LinearLayout.LayoutParams ivParams = new LinearLayout.LayoutParams(iconSize, iconSize);
        iv.setLayoutParams(ivParams);
        iv.setImageResource(icon);
        iv.setColorFilter(Color.WHITE);
        iv.setAlpha(0.85f);
        row.addView(iv);

        TextView tv = new TextView(context);
        LinearLayout.LayoutParams tvParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tvParams.setMarginStart((int) (20 * context.getResources().getDisplayMetrics().density));
        tv.setLayoutParams(tvParams);
        tv.setText(title);
        tv.setTextColor(Color.WHITE);
        tv.setTextSize(16);
        tv.setAlpha(0.9f);
        row.addView(tv);

        row.setOnClickListener(v -> {
            if (action != null) action.run();
            if (sheet != null) sheet.dismiss();
        });

        return row;
    }

    private void maybeEnqueueNextOfflineAutoPlaylist() {}
    private ArrayList<CachedPlaylistTrack> loadCachedPlaylistTracksForOffline(String id) { return new ArrayList<>(); }
    private SongPlayerFragment findSongPlayerFragment() { return null; }
    private void clearPersistedPositionForVideoId(String id) {}
    private void updateMiniPlayerUi() {}
    private boolean isPersistedPlaylistOfflineComplete(String id) { return false; }
    private boolean isPlaylistFullyOffline(String id) { return false; }
    private boolean isLikedPlaylistStyle(YouTubeMusicService.TrackResult t) { return false; }
    private void refreshLibraryPullAsync() {
        if (!isAdded()) return;
        mainHandler.post(() -> {
            if (swipeLibraryRefresh != null) {
                swipeLibraryRefresh.setRefreshing(true);
            }
            fetchLibraryPlaylists(true, false, true);
        });
    }
}


