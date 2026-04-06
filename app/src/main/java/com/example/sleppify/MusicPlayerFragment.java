package com.example.sleppify;

import android.accounts.Account;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.PopupWindow;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.LinearLayout;
import android.content.res.ColorStateList;
import androidx.core.content.ContextCompat;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import com.bumptech.glide.Glide;
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
import com.google.android.material.imageview.ShapeableImageView;
import com.google.android.material.textfield.TextInputEditText;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
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
    private static final String STREAM_SCREEN_LIBRARY = "library";
    private static final String STREAM_SCREEN_PLAYLIST_DETAIL = "playlist_detail";
    private static final long MINI_PROGRESS_TICK_MS = 850L;
    private static final long MINI_SNAPSHOT_REFRESH_MS = 1200L;
    private static final String TAG_MODULE_MUSIC = "module_music";
    private static final String PREFS_STREAMING_CACHE = "streaming_cache";
    private static final String PREF_LIBRARY_UPDATED_AT_PREFIX = "library_updated_at_";
    private static final String PREF_LIBRARY_DATA_PREFIX = "library_data_";
    private static final String PREF_TRACKS_UPDATED_AT_PREFIX = "playlist_tracks_updated_at_";
    private static final String PREF_TRACKS_DATA_PREFIX = "playlist_tracks_data_";
    private static final String PREF_PLAYLIST_OFFLINE_COMPLETE_PREFIX = "playlist_offline_complete_";
    private static final String PREF_LAST_STREAMING_ACCOUNT_KEY = "stream_last_library_account_key";
    private static final String PREF_LIBRARY_GRID_MODE = "library_grid_mode";
    private static final String OFFLINE_DOWNLOAD_UNIQUE_PREFIX = "offline_playlist_";
    private static final String OFFLINE_DOWNLOAD_QUEUE_UNIQUE_NAME = "offline_playlist_queue";
    private static final long OFFLINE_TRACKS_PREFETCH_STALE_MS = 7L * 24 * 60 * 60 * 1000L;
    private static final int OFFLINE_TRACKS_PREFETCH_LIMIT = 200;
    private static final List<YouTubeMusicService.TrackResult> LIBRARY_CACHE = new ArrayList<>();
    private static String libraryCacheAccountKey = "";
    private static long libraryCacheUpdatedAtMs;
    private static String tokenCacheAccountKey = "";
    private static String cachedYoutubeAccessToken = "";
    private static long tokenCacheUpdatedAtMs;
    private static long lastLibrarySyncAtMs;

    private LinearLayout llSearchRow;
    private View tvLibraryTitle;
    private View hsvLibraryFilters;
    private View llLibrarySortRow;
    private View llMusicState;
    private View hsvFilterChips;
    private View llSearchOverlay;
    private ImageView ivLibraryViewToggle;
    private TextInputEditText etMusicQuery;
    private MaterialButton btnSearchMusic;
    private MaterialButton btnTabSearch;
    private MaterialButton btnTabLibrary;
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
    private LinearLayout llMiniPlayer;
    private ShapeableImageView ivMiniPlayerArt;
    private TextView tvMiniPlayerTitle;
    private TextView tvMiniPlayerSubtitle;
    private SeekBar sbMiniPlayerProgress;
    private ImageButton btnMiniPlayPause;

    private final YouTubeMusicService youTubeMusicService = new YouTubeMusicService();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Handler miniProgressHandler = new Handler(Looper.getMainLooper());
    private final Runnable miniProgressTicker = new Runnable() {
        @Override
        public void run() {
            if (!isAdded() || getView() == null || isHidden()) {
                return;
            }
            updateMiniPlayerUi();
            miniProgressHandler.postDelayed(this, MINI_PROGRESS_TICK_MS);
        }
    };
    private final ExecutorService authExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService offlineStateExecutor = Executors.newSingleThreadExecutor();
    private final List<YouTubeMusicService.TrackResult> tracks = new ArrayList<>();
    private final List<YouTubeMusicService.TrackResult> allTracks = new ArrayList<>();
    private final List<YouTubeMusicService.TrackResult> libraryTracks = new ArrayList<>();
    private final Map<String, String> normalizedFilterCache = new HashMap<>();
    private final Set<String> offlinePrefetchInFlight = new HashSet<>();
    private MusicResultsAdapter adapter;
    private boolean searching;
    private boolean loadingLibrary;
    @Nullable
    private GoogleSignInClient googleSignInClient;
    @Nullable
    private GoogleSignInAccount pendingRecoverableAccount;
    @Nullable
    private YouTubeMusicService.TrackResult featuredTrack;
    private String youtubeAccessToken = "";
    private boolean youtubeAuthRefreshInProgress;
    private boolean libraryGridMode;
    @Nullable
    private Boolean lastAppliedLibraryLayoutGridMode;
    @Nullable
    private PlaybackHistoryStore.Snapshot miniSnapshotCache;
    @Nullable
    private PopupWindow playlistActionPopupWindow;
    private long miniSnapshotCacheReadAtMs;
    @NonNull
    private String lastMiniArtworkTrackId = "";
    @NonNull
    private String lastMiniArtworkUrl = "";
    private int lastMiniProgressValue = -1;

    private ActivityResultLauncher<Intent> signInLauncher;
    private ActivityResultLauncher<Intent> recoverAuthLauncher;

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

    private ChipFilter activeFilter = ChipFilter.VIDEOS;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        signInLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> handleGoogleSignInResult(result.getData())
        );

        recoverAuthLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    GoogleSignInAccount account = pendingRecoverableAccount;
                    pendingRecoverableAccount = null;

                    if (result.getResultCode() == Activity.RESULT_OK && account != null) {
                        requestYoutubeAccessToken(account, true);
                    } else {
                        setLibraryLoading(false, "Permiso de YouTube no concedido.");
                    }
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

        llSearchRow = view.findViewById(R.id.llSearchRow);
        tvLibraryTitle = view.findViewById(R.id.tvLibraryTitle);
        hsvLibraryFilters = view.findViewById(R.id.hsvLibraryFilters);
        llLibrarySortRow = view.findViewById(R.id.llLibrarySortRow);
        llMusicState = view.findViewById(R.id.llMusicState);
        hsvFilterChips = view.findViewById(R.id.hsvFilterChips);
        llSearchOverlay = view.findViewById(R.id.llSearchOverlay);
        ivLibraryViewToggle = view.findViewById(R.id.ivLibraryViewToggle);
        etMusicQuery = view.findViewById(R.id.etMusicQuery);
        btnSearchMusic = view.findViewById(R.id.btnSearchMusic);
        btnTabSearch = view.findViewById(R.id.btnTabSearch);
        btnTabLibrary = view.findViewById(R.id.btnTabLibrary);
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
        btnChipVideos = view.findViewById(R.id.btnChipVideos);
        btnChipSongs = view.findViewById(R.id.btnChipSongs);
        btnChipArtists = view.findViewById(R.id.btnChipArtists);
        btnChipPlaylists = view.findViewById(R.id.btnChipPlaylists);
        llMiniPlayer = view.findViewById(R.id.llMiniPlayer);
        ivMiniPlayerArt = view.findViewById(R.id.ivMiniPlayerArt);
        tvMiniPlayerTitle = view.findViewById(R.id.tvMiniPlayerTitle);
        tvMiniPlayerSubtitle = view.findViewById(R.id.tvMiniPlayerSubtitle);
        sbMiniPlayerProgress = view.findViewById(R.id.sbMiniPlayerProgress);
        btnMiniPlayPause = view.findViewById(R.id.btnMiniPlayPause);

        libraryGridMode = getCachePrefs().getBoolean(PREF_LIBRARY_GRID_MODE, false);
        adapter = new MusicResultsAdapter(this::openTrack, this::onLibraryPlaylistMorePressed);
        adapter.setGridMode(libraryGridMode);
        rvMusicResults.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvMusicResults.setAdapter(adapter);
        applyLibraryLayoutMode();

        rebuildGoogleSignInClient();
        hydrateCachedSessionState();

        llFeaturedResult.setVisibility(View.GONE);

        btnSearchMusic.setOnClickListener(v -> performSearch(true));
        btnTabSearch.setOnClickListener(v -> switchScreen(ScreenMode.SEARCH));
        btnTabLibrary.setOnClickListener(v -> switchScreen(ScreenMode.LIBRARY));
        btnYoutubeLogin.setOnClickListener(v -> onYoutubeLoginClicked());

        btnChipVideos.setOnClickListener(v -> setActiveFilter(ChipFilter.VIDEOS));
        btnChipSongs.setOnClickListener(v -> setActiveFilter(ChipFilter.SONGS));
        btnChipArtists.setOnClickListener(v -> setActiveFilter(ChipFilter.ARTISTS));
        btnChipPlaylists.setOnClickListener(v -> setActiveFilter(ChipFilter.PLAYLISTS));

        View.OnClickListener toggleListener = v -> {
            if (activeScreen != ScreenMode.LIBRARY) {
                return;
            }
            libraryGridMode = !libraryGridMode;
            getCachePrefs().edit().putBoolean(PREF_LIBRARY_GRID_MODE, libraryGridMode).apply();
            applyLibraryLayoutMode();
        };
        if (ivLibraryViewToggle != null) {
            ivLibraryViewToggle.setOnClickListener(toggleListener);
        }

        btnFeaturedPlay.setOnClickListener(v -> {
            if (featuredTrack != null) {
                openTrack(featuredTrack);
            }
        });

        btnFeaturedSave.setOnClickListener(v -> {
            if (featuredTrack == null) {
                return;
            }
            Toast.makeText(requireContext(), "Guardado en tu lista local.", Toast.LENGTH_SHORT).show();
        });

        etMusicQuery.setOnEditorActionListener((textView, actionId, event) -> {
            performSearch(true);
            return true;
        });

        applyChipStyles();
        applyTopTabStyles();
        updateYoutubeButtonLabel();
        switchScreen(ScreenMode.LIBRARY);
        if (!isPlaylistDetailStatePending()) {
            persistStreamingScreen(STREAM_SCREEN_LIBRARY);
        }

        llMiniPlayer.setOnClickListener(v -> openPlayerFromMiniBar());
        btnMiniPlayPause.setOnClickListener(v -> toggleMiniPlayback());
        updateMiniPlayerUi();
        startMiniProgressTicker();
    }

    @Override
    public void onResume() {
        super.onResume();
        resetMiniPlayerRenderCache();
        if (adapter != null) {
            adapter.invalidatePlaylistOfflineState(null);
        }
        startMiniProgressTicker();
        updateMiniPlayerUi();
    }

    @Override
    public void onPause() {
        stopMiniProgressTicker();
        super.onPause();
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (hidden) {
            stopMiniProgressTicker();
            return;
        }
        startMiniProgressTicker();
        updateMiniPlayerUi();
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

    public boolean externalRestoreLastPlaylistDetailIfNeeded() {
        if (!isAdded()) {
            return false;
        }

        SharedPreferences prefs = requireContext().getSharedPreferences(PREFS_PLAYER_STATE, Activity.MODE_PRIVATE);
        String lastScreen = prefs.getString(PREF_LAST_STREAM_SCREEN, STREAM_SCREEN_LIBRARY);
        if (!TextUtils.equals(STREAM_SCREEN_PLAYLIST_DETAIL, lastScreen)) {
            return false;
        }

        androidx.fragment.app.Fragment existing = getParentFragmentManager().findFragmentByTag("playlist_detail");
        if (existing != null && existing.isAdded()) {
            getParentFragmentManager()
                    .beginTransaction()
                    .setReorderingAllowed(true)
                    .hide(this)
                    .show(existing)
                    .commit();
            return true;
        }

        boolean restored = openLastPlaylistDetailFromPrefs();
        if (!restored) {
            persistStreamingScreen(STREAM_SCREEN_LIBRARY);
        }
        return restored;
    }

    private void startMiniProgressTicker() {
        if (!isAdded() || getView() == null || isHidden()) {
            return;
        }
        miniProgressHandler.removeCallbacks(miniProgressTicker);
        miniProgressHandler.post(miniProgressTicker);
    }

    private void stopMiniProgressTicker() {
        miniProgressHandler.removeCallbacks(miniProgressTicker);
    }

    private void switchScreen(@NonNull ScreenMode mode) {
        activeScreen = mode;
        applyLibraryLayoutMode();

        boolean searchVisible = mode == ScreenMode.SEARCH;
        if (llSearchOverlay != null) {
            llSearchOverlay.setVisibility(searchVisible ? View.VISIBLE : View.GONE);
        }
        llSearchRow.setVisibility(searchVisible ? View.VISIBLE : View.GONE);
        hsvFilterChips.setVisibility(searchVisible ? View.VISIBLE : View.GONE);
        llLibraryEmptyState.setVisibility(View.GONE);

        applyTopTabStyles();
        updateYoutubeButtonLabel();

        if (searchVisible) {
            String query = etMusicQuery.getText() != null ? etMusicQuery.getText().toString().trim() : "";
            if (!allTracks.isEmpty()) {
                applyActiveFilter(query);
            } else {
                featuredTrack = null;
                llFeaturedResult.setVisibility(View.GONE);
                adapter.submitResults(new ArrayList<>());
                tvMusicState.setText("Escribe y busca para cargar resultados.");
            }
            return;
        }

        llFeaturedResult.setVisibility(View.GONE);
        if (!libraryTracks.isEmpty()) {
            renderLibraryResults();
        } else {
            adapter.submitResults(new ArrayList<>());
            tvMusicState.setText("");
            showLibraryEmptyState();
        }

        maybeSyncLibraryIfAuthorized();
    }

    private void maybeSyncLibraryIfAuthorized() {
        if (!isAdded() || loadingLibrary) {
            return;
        }
        if (!isNetworkAvailable()) {
            return;
        }

        GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(requireContext());
        Scope ytScope = new Scope(youTubeMusicService.getYoutubeReadonlyScope());
        if (account != null
                && GoogleSignIn.hasPermissions(account, ytScope)
                && isMatchingPrimaryAppGoogleAccount(account)) {
            long now = System.currentTimeMillis();
            boolean hasLocalData = !libraryTracks.isEmpty();
            if (hasLocalData && (now - lastLibrarySyncAtMs) < LIBRARY_REFRESH_MIN_INTERVAL_MS) {
                return;
            }

            String accountKey = resolveAccountKey();
            boolean backgroundRefresh = hasLocalData;
            if (hasValidCachedToken(accountKey)) {
                youtubeAccessToken = cachedYoutubeAccessToken;
                fetchLibraryPlaylists(false, backgroundRefresh);
            } else {
                requestYoutubeAccessToken(account, false, backgroundRefresh);
            }
        }
    }

    private void applyLibraryLayoutMode() {
        if (rvMusicResults == null || adapter == null || !isAdded()) {
            return;
        }

        boolean useGrid = activeScreen == ScreenMode.LIBRARY && libraryGridMode;
        boolean modeChanged = lastAppliedLibraryLayoutGridMode != null
                && lastAppliedLibraryLayoutGridMode != useGrid;

        if (activeScreen == ScreenMode.LIBRARY && modeChanged) {
            animateLibraryLayoutSwitch(useGrid);
        } else {
            applyRecyclerLayoutManager(useGrid);
            adapter.setGridMode(useGrid);
            rvMusicResults.setAlpha(1f);
            rvMusicResults.setTranslationY(0f);
        }

        if (ivLibraryViewToggle != null) {
            int tint = ContextCompat.getColor(requireContext(), R.color.text_secondary);
            ivLibraryViewToggle.setActivated(false);
            ivLibraryViewToggle.setContentDescription(useGrid
                    ? "Cambiar a vista de lista"
                    : "Cambiar a vista de cuadrícula");

            if (activeScreen == ScreenMode.LIBRARY && modeChanged) {
            animateToggleIconMorph(useGrid, tint);
            } else {
            ivLibraryViewToggle.setImageResource(useGrid ? R.drawable.ic_view_list_mode : R.drawable.ic_view_grid_mode);
            ivLibraryViewToggle.setColorFilter(tint);
            ivLibraryViewToggle.setRotation(0f);
            ivLibraryViewToggle.setAlpha(1f);
                ivLibraryViewToggle.setScaleX(1f);
                ivLibraryViewToggle.setScaleY(1f);
            }
        }

        lastAppliedLibraryLayoutGridMode = useGrid;
    }

    private void applyRecyclerLayoutManager(boolean useGrid) {
        if (useGrid) {
            if (!(rvMusicResults.getLayoutManager() instanceof GridLayoutManager)) {
                rvMusicResults.setLayoutManager(new GridLayoutManager(requireContext(), 2));
            }
        } else {
            if (!(rvMusicResults.getLayoutManager() instanceof LinearLayoutManager)
                    || rvMusicResults.getLayoutManager() instanceof GridLayoutManager) {
                rvMusicResults.setLayoutManager(new LinearLayoutManager(requireContext()));
            }
        }
    }

    private void animateLibraryLayoutSwitch(boolean useGrid) {
        rvMusicResults.animate().cancel();
        rvMusicResults.animate()
                .alpha(0f)
                .translationY(dpToPx(6))
                .setDuration(90)
                .withEndAction(() -> {
                    if (!isAdded()) {
                        return;
                    }
                    applyRecyclerLayoutManager(useGrid);
                    adapter.setGridMode(useGrid);
                    rvMusicResults.setTranslationY(dpToPx(4));
                    rvMusicResults.animate()
                            .alpha(1f)
                            .translationY(0f)
                            .setDuration(170)
                            .start();
                })
                .start();
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    private void animateToggleIconMorph(boolean useGrid, int tint) {
        if (ivLibraryViewToggle == null) {
            return;
        }

        ivLibraryViewToggle.animate().cancel();
        ivLibraryViewToggle.animate()
                .alpha(0.25f)
                .scaleX(0.72f)
                .scaleY(0.72f)
                .setDuration(90)
                .withEndAction(() -> {
                    if (ivLibraryViewToggle == null || !isAdded()) {
                        return;
                    }
                    ivLibraryViewToggle.setImageResource(useGrid ? R.drawable.ic_view_list_mode : R.drawable.ic_view_grid_mode);
                    ivLibraryViewToggle.setColorFilter(tint);
                    ivLibraryViewToggle.animate()
                            .alpha(1f)
                            .scaleX(1f)
                            .scaleY(1f)
                            .setDuration(130)
                            .start();
                })
                .start();
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

    private void applyTopTabStyles() {
        styleTopTab(btnTabSearch, activeScreen == ScreenMode.SEARCH);
        styleTopTab(btnTabLibrary, activeScreen == ScreenMode.LIBRARY);
    }

    private void styleTopTab(@NonNull MaterialButton tab, boolean selected) {
        int bg = selected
                ? ContextCompat.getColor(requireContext(), R.color.stitch_blue)
                : ContextCompat.getColor(requireContext(), R.color.surface_low);
        int fg = selected
                ? ContextCompat.getColor(requireContext(), android.R.color.white)
                : ContextCompat.getColor(requireContext(), R.color.text_primary);
        tab.setBackgroundTintList(ColorStateList.valueOf(bg));
        tab.setTextColor(fg);
    }

    private void onYoutubeLoginClicked() {
        if (!isAdded()) {
            return;
        }

        rebuildGoogleSignInClient();

        if (googleSignInClient == null) {
            Toast.makeText(requireContext(), "No se pudo iniciar sesion de YouTube.", Toast.LENGTH_SHORT).show();
            return;
        }

        // Force a fresh account chooser to avoid silent reuse of stale Google sessions.
        youtubeAccessToken = "";
        clearCachedToken();
        updateYoutubeButtonLabel();
        setLibraryLoading(true, "Abriendo selector de cuenta...");
        launchGoogleSignInChooser(true);
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
                Toast.makeText(requireContext(), "Esa cuenta no coincide con tu sesion actual.", Toast.LENGTH_SHORT).show();
                return;
            }

            requestYoutubeAccessToken(account, true);
        } catch (ApiException e) {
            String failureMessage = buildGoogleSignInFailureMessage(e);
            setLibraryLoading(false, failureMessage);
            if (isAdded()) {
                Toast.makeText(requireContext(), failureMessage, Toast.LENGTH_LONG).show();
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
        requestYoutubeAccessToken(account, userTriggered, false);
    }

    private void requestYoutubeAccessToken(
            @NonNull GoogleSignInAccount account,
            boolean userTriggered,
            boolean backgroundRefresh
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
                    cacheTokenForCurrentAccount(finalToken);
                    updateYoutubeButtonLabel();
                    fetchLibraryPlaylists(userTriggered, backgroundRefresh);
                });
            } catch (UserRecoverableAuthException recoverableException) {
                mainHandler.post(() -> {
                    youtubeAuthRefreshInProgress = false;
                    if (backgroundRefresh && !userTriggered) {
                        return;
                    }
                    pendingRecoverableAccount = account;
                    setLibraryLoading(false, "Confirma el permiso para leer tu biblioteca.");
                    Intent recoverIntent = recoverableException.getIntent();
                    if (recoverIntent == null) {
                        if (isAdded()) {
                            Toast.makeText(requireContext(), "No se pudo abrir la pantalla de permisos de Google.", Toast.LENGTH_LONG).show();
                        }
                        launchGoogleSignInChooser(true);
                        return;
                    }
                    try {
                        recoverAuthLauncher.launch(recoverIntent);
                    } catch (Exception launchError) {
                        if (isAdded()) {
                            Toast.makeText(requireContext(), "No se pudo abrir la autorizacion de Google.", Toast.LENGTH_LONG).show();
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
                        Toast.makeText(requireContext(), "Error de autenticacion con YouTube.", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }

    private void fetchLibraryPlaylists(boolean userTriggered) {
        fetchLibraryPlaylists(userTriggered, false);
    }

    private void fetchLibraryPlaylists(boolean userTriggered, boolean backgroundRefresh) {
        if (TextUtils.isEmpty(youtubeAccessToken)) {
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
        youTubeMusicService.fetchMyPlaylists(youtubeAccessToken, 25, new YouTubeMusicService.PlaylistsCallback() {
            @Override
            public void onSuccess(@NonNull List<YouTubeMusicService.PlaylistResult> playlists) {
                if (!isAdded()) {
                    return;
                }

                libraryTracks.clear();
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
                                ? "Playlist autogenerada. " + playlist.itemCount + " canciones"
                                : "Playlist autogenerada";
                    } else if (playlist.itemCount > 0) {
                        subtitle = "Playlist • " + playlist.itemCount + " canciones";
                    } else {
                        subtitle = "Playlist";
                    }
                    libraryTracks.add(new YouTubeMusicService.TrackResult(
                            "playlist",
                            playlistContentId,
                            title,
                            subtitle,
                            playlist.thumbnailUrl
                    ));
                }

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
                    GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(requireContext());
                    Scope ytScope = new Scope(youTubeMusicService.getYoutubeReadonlyScope());
                    if (account != null
                            && GoogleSignIn.hasPermissions(account, ytScope)
                            && isMatchingPrimaryAppGoogleAccount(account)) {
                        youtubeAuthRefreshInProgress = true;
                        youtubeAccessToken = "";
                        clearCachedToken();
                        updateYoutubeButtonLabel();
                        requestYoutubeAccessToken(account, false, backgroundRefresh);
                        return;
                    }
                }

                youtubeAuthRefreshInProgress = false;
                if (shouldInvalidateStoredYoutubeToken(error)) {
                    youtubeAccessToken = "";
                    clearCachedToken();
                    updateYoutubeButtonLabel();
                }

                if (!backgroundRefresh || libraryTracks.isEmpty()) {
                    setLibraryLoading(false, "Error al cargar biblioteca: " + error);
                }
                if (userTriggered) {
                    Toast.makeText(requireContext(), "No se pudo cargar tu biblioteca.", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void renderLibraryResults() {
        featuredTrack = null;
        llFeaturedResult.setVisibility(View.GONE);
        if (tvLibraryTitle != null) tvLibraryTitle.setVisibility(View.VISIBLE);
        if (hsvLibraryFilters != null) hsvLibraryFilters.setVisibility(View.VISIBLE);
        llLibraryEmptyState.setVisibility(View.GONE);
        llLibrarySortRowVisible(true);
        rvMusicResults.setVisibility(View.VISIBLE);
        llMusicStateVisible(true);
        adapter.submitResults(new ArrayList<>(libraryTracks));

        if (libraryTracks.isEmpty()) {
            tvMusicState.setText("");
            showLibraryEmptyState();
        } else {
            tvMusicState.setText(String.format(Locale.getDefault(), "%d playlists", libraryTracks.size()));
        }
    }

    private void showLibraryEmptyState() {
        if (tvLibraryTitle != null) tvLibraryTitle.setVisibility(View.GONE);
        if (hsvLibraryFilters != null) hsvLibraryFilters.setVisibility(View.GONE);
        llMusicStateVisible(false);
        rvMusicResults.setVisibility(View.GONE);
        llLibrarySortRowVisible(false);
        llLibraryEmptyState.setVisibility(View.VISIBLE);
        updateYoutubeButtonLabel();
    }

    private void llLibrarySortRowVisible(boolean visible) {
        if (llLibrarySortRow != null) {
            llLibrarySortRow.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
    }

    private void llMusicStateVisible(boolean visible) {
        if (llMusicState != null) {
            llMusicState.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
    }

    private void performSearch(boolean fromUserAction) {
        if (activeScreen != ScreenMode.SEARCH || searching) {
            return;
        }

        String query = etMusicQuery.getText() != null ? etMusicQuery.getText().toString().trim() : "";
        if (TextUtils.isEmpty(query)) {
            if (fromUserAction) {
                Toast.makeText(requireContext(), "Escribe algo para buscar.", Toast.LENGTH_SHORT).show();
            }
            return;
        }

        setSearchLoadingState(true, "Buscando en YouTube...");
        youTubeMusicService.searchTracks(query, 20, new YouTubeMusicService.SearchCallback() {
            @Override
            public void onSuccess(@NonNull List<YouTubeMusicService.TrackResult> resultTracks) {
                if (!isAdded()) {
                    return;
                }
                setSearchLoadingState(false, "");

                allTracks.clear();
                allTracks.addAll(resultTracks);
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
                setSearchLoadingState(false, "Error: " + error);
                if (fromUserAction) {
                    Toast.makeText(requireContext(), "No se pudo buscar ahora.", Toast.LENGTH_SHORT).show();
                }
            }
        });
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

    private void setLibraryLoading(boolean loading, @NonNull String stateMessage) {
        loadingLibrary = loading;
        if (btnYoutubeLogin != null) {
            btnYoutubeLogin.setEnabled(!loading);
        }
        refreshGlobalLoadingIndicator();
        if (!stateMessage.isEmpty()) {
            tvMusicState.setText(stateMessage);
        }
    }

    private void refreshGlobalLoadingIndicator() {
        if (progressMusic == null) {
            return;
        }
        progressMusic.setVisibility((searching || loadingLibrary) ? View.VISIBLE : View.GONE);
    }

    private void updateYoutubeButtonLabel() {
        if (!isAdded() || btnYoutubeLogin == null) {
            return;
        }

        if (!hasLoggedInStreamingAccount()) {
            btnYoutubeLogin.setText(libraryTracks.isEmpty()
                    ? "Conectar"
                    : "Conectar para sincronizar");
            btnYoutubeLogin.setVisibility(View.VISIBLE);
            return;
        }

        if (TextUtils.isEmpty(youtubeAccessToken) && libraryTracks.isEmpty()) {
            btnYoutubeLogin.setText("Conectar");
            btnYoutubeLogin.setVisibility(View.VISIBLE);
            return;
        }

        btnYoutubeLogin.setVisibility(View.GONE);
    }

    private boolean hasLoggedInStreamingAccount() {
        if (!isAdded()) {
            return false;
        }

        GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(requireContext());
        if (account == null) {
            return false;
        }

        Scope ytScope = new Scope(youTubeMusicService.getYoutubeReadonlyScope());
        return GoogleSignIn.hasPermissions(account, ytScope)
                && isMatchingPrimaryAppGoogleAccount(account);
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

    private void hydrateCachedSessionState() {
        String accountKey = resolveAccountKey();
        if (hasValidCachedToken(accountKey)) {
            youtubeAccessToken = cachedYoutubeAccessToken;
        }

        if (hasValidLibraryCache(accountKey) && libraryTracks.isEmpty()) {
            libraryTracks.clear();
            libraryTracks.addAll(new ArrayList<>(LIBRARY_CACHE));
            return;
        }

        List<YouTubeMusicService.TrackResult> persisted = loadPersistedLibrary(accountKey);
        if (!persisted.isEmpty() && libraryTracks.isEmpty()) {
            libraryTracks.clear();
            libraryTracks.addAll(persisted);
            LIBRARY_CACHE.clear();
            LIBRARY_CACHE.addAll(persisted);
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
        LIBRARY_CACHE.clear();
        LIBRARY_CACHE.addAll(persisted);
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
        String accountKey = resolveAccountKey();
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
                    .apply();
        }
    }

    private void cacheLibraryForCurrentAccount(@NonNull List<YouTubeMusicService.TrackResult> source) {
        String accountKey = resolveAccountKey();
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
            youTubeMusicService.fetchPlaylistTracks(accessToken, targetPlaylistId, 50, new YouTubeMusicService.PlaylistTracksCallback() {
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
                    .putString(PREF_TRACKS_DATA_PREFIX + playlistId, array.toString())
                    .apply();
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

        List<YouTubeMusicService.TrackResult> filtered = new ArrayList<>();
        for (YouTubeMusicService.TrackResult track : allTracks) {
            if (matchesFilter(track, activeFilter)) {
                filtered.add(track);
            }
        }

        boolean fallbackToAll = false;
        if (filtered.isEmpty() && !allTracks.isEmpty()) {
            filtered.addAll(allTracks);
            fallbackToAll = true;
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

        if (!TextUtils.isEmpty(query)) {
            if (fallbackToAll) {
                tvMusicState.setText(String.format(Locale.getDefault(), "%d resultados (sin coincidencias exactas en %s, mostrando todo).", filtered.size(), filterLabel(activeFilter)));
            } else {
                tvMusicState.setText(String.format(Locale.getDefault(), "%d resultados (%s).", filtered.size(), filterLabel(activeFilter)));
            }
        }
    }

    private boolean matchesFilter(@NonNull YouTubeMusicService.TrackResult track, @NonNull ChipFilter filter) {
        String title = normalizeForFilter(track.title);
        String type = normalizeForFilter(track.resultType);

        boolean looksLikeVideoClip = containsAny(title,
                "official video",
                "music video",
                " videoclip",
                " clip",
                " mv",
                "visualizer");

        switch (filter) {
            case VIDEOS:
                return "video".equals(type) && looksLikeVideoClip;
            case SONGS:
                return "video".equals(type) && !looksLikeVideoClip;
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
        int bg = selected ? ContextCompat.getColor(requireContext(), R.color.stitch_blue) : ContextCompat.getColor(requireContext(), R.color.surface_low);
        int fg = selected ? ContextCompat.getColor(requireContext(), android.R.color.white) : ContextCompat.getColor(requireContext(), R.color.text_primary);
        chip.setBackgroundTintList(ColorStateList.valueOf(bg));
        chip.setTextColor(fg);
    }

    private void bindFeaturedTrack(@NonNull YouTubeMusicService.TrackResult track) {
        tvFeaturedTitle.setText(track.title);
        tvFeaturedSubtitle.setText(TextUtils.isEmpty(track.subtitle) ? "Cancion" : track.subtitle);

        if (!TextUtils.isEmpty(track.thumbnailUrl)) {
            Glide.with(this)
                    .load(track.thumbnailUrl)
                    .transform(ArtworkTrimTransformation.obtain(), new com.bumptech.glide.load.resource.bitmap.CenterCrop())
                    .placeholder(R.drawable.ic_music)
                    .error(R.drawable.ic_music)
                    .into(ivFeaturedThumb);
        } else {
            ivFeaturedThumb.setImageResource(R.drawable.ic_music);
        }
    }

    @Nullable
    private SongPlayerFragment findSongPlayerFragment() {
        androidx.fragment.app.Fragment fragment = getParentFragmentManager().findFragmentByTag("song_player");
        if (fragment instanceof SongPlayerFragment) {
            return (SongPlayerFragment) fragment;
        }
        return null;
    }

    @NonNull
    private PlaybackHistoryStore.Snapshot loadPlaybackSnapshot() {
        if (!isAdded()) {
            return new PlaybackHistoryStore.Snapshot(new ArrayList<>(), 0, 0, 1, false, 0L);
        }

        long now = System.currentTimeMillis();
        if (miniSnapshotCache != null && (now - miniSnapshotCacheReadAtMs) < MINI_SNAPSHOT_REFRESH_MS) {
            return miniSnapshotCache;
        }

        PlaybackHistoryStore.Snapshot snapshot = PlaybackHistoryStore.load(requireContext());
        miniSnapshotCache = snapshot;
        miniSnapshotCacheReadAtMs = now;
        return snapshot;
    }

    private void invalidateMiniSnapshotCache() {
        miniSnapshotCache = null;
        miniSnapshotCacheReadAtMs = 0L;
    }

    private void resetMiniPlayerRenderCache() {
        lastMiniArtworkTrackId = "";
        lastMiniArtworkUrl = "";
        lastMiniProgressValue = -1;
    }

    private void openPlayerFromMiniBar() {
        SongPlayerFragment existingPlayer = findSongPlayerFragment();
        if (existingPlayer != null && existingPlayer.isAdded()) {
            existingPlayer.externalSetReturnTargetTag(TAG_MODULE_MUSIC);
            getParentFragmentManager()
                    .beginTransaction()
                .setCustomAnimations(R.anim.player_screen_enter, 0)
                    .setReorderingAllowed(true)
                    .hide(this)
                    .show(existingPlayer)
                    .commit();
                    invalidateMiniSnapshotCache();
            return;
        }

        PlaybackHistoryStore.Snapshot snapshot = loadPlaybackSnapshot();
        if (openPlayerFromSnapshot(snapshot, false)) {
            return;
        }

        if (!openLastPlaylistDetailFromPrefs()) {
            Toast.makeText(requireContext(), "No hay reproducción reciente.", Toast.LENGTH_SHORT).show();
        }
    }

    private void toggleMiniPlayback() {
        SongPlayerFragment existingPlayer = findSongPlayerFragment();
        if (existingPlayer != null && existingPlayer.isAdded()) {
            existingPlayer.externalTogglePlayback();
            invalidateMiniSnapshotCache();
            updateMiniPlayerUi();
            return;
        }

        PlaybackHistoryStore.Snapshot snapshot = loadPlaybackSnapshot();
        if (startHiddenPlayerFromSnapshot(snapshot, true)) {
            return;
        }

        if (!openLastPlaylistDetailFromPrefs()) {
            Toast.makeText(requireContext(), "No hay canciones para reproducir.", Toast.LENGTH_SHORT).show();
        }
    }

    private boolean startHiddenPlayerFromSnapshot(
            @NonNull PlaybackHistoryStore.Snapshot snapshot,
            boolean startPlaying
    ) {
        if (!snapshot.isValid() || !isAdded()) {
            return false;
        }

        ArrayList<String> ids = new ArrayList<>();
        ArrayList<String> titles = new ArrayList<>();
        ArrayList<String> artists = new ArrayList<>();
        ArrayList<String> durations = new ArrayList<>();
        ArrayList<String> images = new ArrayList<>();
        for (PlaybackHistoryStore.QueueTrack item : snapshot.queue) {
            ids.add(item.videoId);
            titles.add(item.title);
            artists.add(item.artist);
            durations.add(item.duration);
            images.add(item.imageUrl);
        }
        if (ids.isEmpty()) {
            return false;
        }

        int snapshotIndex = Math.max(0, Math.min(snapshot.currentIndex, ids.size() - 1));
        persistSnapshotPositionToPlayerState(snapshot);

        SongPlayerFragment existingPlayer = findSongPlayerFragment();
        if (existingPlayer != null && existingPlayer.isAdded()) {
            existingPlayer.externalSetReturnTargetTag(TAG_MODULE_MUSIC);
            existingPlayer.externalReplaceQueue(ids, titles, artists, durations, images, snapshotIndex, startPlaying);
            invalidateMiniSnapshotCache();
            updateMiniPlayerUi();
            return true;
        }

        if (getParentFragmentManager().isStateSaved()) {
            return false;
        }

        SongPlayerFragment playerFragment = SongPlayerFragment.newInstance(
                ids,
                titles,
                artists,
                durations,
                images,
                snapshotIndex,
                startPlaying
        );
        playerFragment.externalSetReturnTargetTag(TAG_MODULE_MUSIC);

        getParentFragmentManager()
                .beginTransaction()
                .setReorderingAllowed(true)
                .add(R.id.fragmentContainer, playerFragment, "song_player")
                .hide(playerFragment)
                .runOnCommit(this::updateMiniPlayerUi)
                .commit();

        invalidateMiniSnapshotCache();
        updateMiniPlayerUi();
        return true;
    }

    private boolean openLastPlaylistDetailFromPrefs() {
        if (!isAdded()) {
            return false;
        }

        SharedPreferences prefs = requireContext().getSharedPreferences(PREFS_PLAYER_STATE, Activity.MODE_PRIVATE);
        String playlistId = prefs.getString(PREF_LAST_PLAYLIST_ID, "");
        if (TextUtils.isEmpty(playlistId)) {
            return false;
        }

        String playlistTitle = prefs.getString(PREF_LAST_PLAYLIST_TITLE, "Lista");
        String playlistSubtitle = prefs.getString(PREF_LAST_PLAYLIST_SUBTITLE, "Playlist");
        String playlistThumbnail = prefs.getString(PREF_LAST_PLAYLIST_THUMBNAIL, "");
        String normalizedPlaylistId = playlistId;
        String titleLower = playlistTitle == null ? "" : playlistTitle.toLowerCase(Locale.US);
        String subtitleLower = playlistSubtitle == null ? "" : playlistSubtitle.toLowerCase(Locale.US);
        if (titleLower.contains("gusta")
                || titleLower.contains("liked")
                || subtitleLower.contains("gusta")
                || subtitleLower.contains("liked")
                || subtitleLower.contains("autogenerada")) {
            normalizedPlaylistId = YouTubeMusicService.SPECIAL_LIKED_VIDEOS_ID;
        }
        String accessTokenForDetail = resolveAccessTokenForPlaylistDetail();

        PlaylistDetailFragment detailFragment = PlaylistDetailFragment.newInstance(
                normalizedPlaylistId,
                TextUtils.isEmpty(playlistTitle) ? "Lista" : playlistTitle,
                TextUtils.isEmpty(playlistSubtitle) ? "Playlist" : playlistSubtitle,
                playlistThumbnail == null ? "" : playlistThumbnail,
            accessTokenForDetail
        );

        getParentFragmentManager()
                .beginTransaction()
                .setReorderingAllowed(true)
                .hide(this)
                .add(R.id.fragmentContainer, detailFragment, "playlist_detail")
                .addToBackStack("playlist_detail")
                .commit();
        return true;
    }

    private boolean openPlayerFromSnapshot(
            @NonNull PlaybackHistoryStore.Snapshot snapshot,
            boolean startPlaying
    ) {
        if (!snapshot.isValid()) {
            return false;
        }

        ArrayList<String> ids = new ArrayList<>();
        ArrayList<String> titles = new ArrayList<>();
        ArrayList<String> artists = new ArrayList<>();
        ArrayList<String> durations = new ArrayList<>();
        ArrayList<String> images = new ArrayList<>();
        for (PlaybackHistoryStore.QueueTrack item : snapshot.queue) {
            ids.add(item.videoId);
            titles.add(item.title);
            artists.add(item.artist);
            durations.add(item.duration);
            images.add(item.imageUrl);
        }
        if (ids.isEmpty()) {
            return false;
        }

        int snapshotIndex = Math.max(0, Math.min(snapshot.currentIndex, ids.size() - 1));
        persistSnapshotPositionToPlayerState(snapshot);

        SongPlayerFragment existingPlayer = findSongPlayerFragment();
        if (existingPlayer != null && existingPlayer.isAdded()) {
            existingPlayer.externalSetReturnTargetTag(TAG_MODULE_MUSIC);
            existingPlayer.externalReplaceQueue(ids, titles, artists, durations, images, snapshotIndex, startPlaying);

            getParentFragmentManager()
                    .beginTransaction()
                .setCustomAnimations(R.anim.player_screen_enter, 0)
                    .setReorderingAllowed(true)
                    .hide(this)
                    .show(existingPlayer)
                    .commit();
        } else {
            SongPlayerFragment playerFragment = SongPlayerFragment.newInstance(
                    ids,
                    titles,
                    artists,
                    durations,
                    images,
                    snapshotIndex,
                    startPlaying
            );
            playerFragment.externalSetReturnTargetTag(TAG_MODULE_MUSIC);

            getParentFragmentManager()
                    .beginTransaction()
                    .setCustomAnimations(R.anim.player_screen_enter, 0)
                    .setReorderingAllowed(true)
                    .hide(this)
                    .add(R.id.fragmentContainer, playerFragment, "song_player")
                    .commit();
        }

        invalidateMiniSnapshotCache();
        updateMiniPlayerUi();
        return true;
    }

    private void persistSnapshotPositionToPlayerState(@NonNull PlaybackHistoryStore.Snapshot snapshot) {
        if (!isAdded()) {
            return;
        }

        PlaybackHistoryStore.QueueTrack current = snapshot.currentTrack();
        if (current == null || TextUtils.isEmpty(current.videoId)) {
            return;
        }

        requireContext()
                .getSharedPreferences(PREFS_PLAYER_STATE, Activity.MODE_PRIVATE)
                .edit()
                .putInt(PREF_PLAYBACK_POS_PREFIX + current.videoId, Math.max(0, snapshot.currentSeconds))
                .apply();
    }

    private void clearPersistedPositionForVideoId(@Nullable String videoId) {
        if (!isAdded() || TextUtils.isEmpty(videoId)) {
            return;
        }

        requireContext()
                .getSharedPreferences(PREFS_PLAYER_STATE, Activity.MODE_PRIVATE)
                .edit()
                .remove(PREF_PLAYBACK_POS_PREFIX + videoId)
                .apply();
    }

    private void updateMiniPlayerUi() {
        if (!isAdded() || llMiniPlayer == null) {
            return;
        }

        SongPlayerFragment songPlayer = findSongPlayerFragment();
        boolean playerAttached = songPlayer != null && songPlayer.isAdded();
        PlaybackHistoryStore.Snapshot snapshot = loadPlaybackSnapshot();
        SharedPreferences playerPrefs = requireContext().getSharedPreferences(PREFS_PLAYER_STATE, Activity.MODE_PRIVATE);

        PlaybackHistoryStore.QueueTrack currentTrack = snapshot.currentTrack();
        int currentSeconds = Math.max(0, snapshot.currentSeconds);
        int totalSeconds = Math.max(1, snapshot.totalSeconds);
        boolean miniPlaying = snapshot.isPlaying;

        if (currentTrack == null || TextUtils.isEmpty(currentTrack.videoId)) {
            String fallbackVideoId = playerPrefs.getString(PREF_LAST_VIDEO_ID, "");
            if (!TextUtils.isEmpty(fallbackVideoId)) {
                String fallbackTitle = playerPrefs.getString(PREF_LAST_TRACK_TITLE, "");
                String fallbackArtist = playerPrefs.getString(PREF_LAST_TRACK_ARTIST, "");
                String fallbackDuration = playerPrefs.getString(PREF_LAST_TRACK_DURATION, "");
                String fallbackImage = playerPrefs.getString(PREF_LAST_TRACK_IMAGE, "");

                currentTrack = new PlaybackHistoryStore.QueueTrack(
                        fallbackVideoId,
                        TextUtils.isEmpty(fallbackTitle) ? "Ultima reproducción" : fallbackTitle,
                        fallbackArtist == null ? "" : fallbackArtist,
                        fallbackDuration == null ? "" : fallbackDuration,
                        fallbackImage == null ? "" : fallbackImage
                );
                currentSeconds = Math.max(0, playerPrefs.getInt(PREF_PLAYBACK_POS_PREFIX + fallbackVideoId, currentSeconds));
                totalSeconds = Math.max(1, parseDurationSeconds(fallbackDuration));
                miniPlaying = playerPrefs.getBoolean(PREF_LAST_IS_PLAYING, false);
            }
        }

        if (playerAttached) {
            String playerVideoId = songPlayer.externalGetCurrentVideoId();
            PlaybackHistoryStore.QueueTrack fromQueue = findSnapshotTrackByVideoId(snapshot, playerVideoId);
            if (fromQueue != null) {
                currentTrack = fromQueue;
            } else if (!TextUtils.isEmpty(playerVideoId)) {
                currentTrack = new PlaybackHistoryStore.QueueTrack(playerVideoId, "Reproduciendo", "", "", "");
            }

            currentSeconds = Math.max(0, songPlayer.externalGetCurrentSeconds());
            totalSeconds = Math.max(1, songPlayer.externalGetTotalSeconds());
            miniPlaying = songPlayer.externalIsPlaying();
        } else if (currentTrack != null && totalSeconds <= 1) {
            totalSeconds = Math.max(1, parseDurationSeconds(currentTrack.duration));
        }

        if (!playerAttached) {
            miniPlaying = false;
        }

        if (currentTrack == null || TextUtils.isEmpty(currentTrack.videoId)) {
            llMiniPlayer.setVisibility(View.VISIBLE);
            tvMiniPlayerTitle.setText("Selecciona una canción");
            tvMiniPlayerSubtitle.setText("");
            btnMiniPlayPause.setImageResource(R.drawable.ic_mini_play);
            ivMiniPlayerArt.setImageResource(R.drawable.ic_music);
            if (sbMiniPlayerProgress != null) {
                if (lastMiniProgressValue != 0) {
                    sbMiniPlayerProgress.setProgress(0);
                    lastMiniProgressValue = 0;
                }
            }
            lastMiniArtworkTrackId = "";
            lastMiniArtworkUrl = "";
            return;
        }

        llMiniPlayer.setVisibility(View.VISIBLE);
        tvMiniPlayerTitle.setText(TextUtils.isEmpty(currentTrack.title) ? "Ultima reproducción" : currentTrack.title);
        tvMiniPlayerSubtitle.setText(TextUtils.isEmpty(currentTrack.artist) ? "" : currentTrack.artist);
        btnMiniPlayPause.setImageResource(miniPlaying
                ? R.drawable.ic_mini_pause
                : R.drawable.ic_mini_play);

        if (sbMiniPlayerProgress != null) {
            int clampedCurrent = Math.max(0, Math.min(totalSeconds, currentSeconds));
            int progress = Math.round((clampedCurrent / (float) Math.max(1, totalSeconds)) * 1000f);
            int boundedProgress = Math.max(0, Math.min(1000, progress));
            if (lastMiniProgressValue != boundedProgress) {
                sbMiniPlayerProgress.setProgress(boundedProgress);
                lastMiniProgressValue = boundedProgress;
            }
        }

        String imageUrl = currentTrack.imageUrl == null ? "" : currentTrack.imageUrl.trim();
        String trackId = currentTrack.videoId == null ? "" : currentTrack.videoId;
        if (!TextUtils.equals(lastMiniArtworkTrackId, trackId)
                || !TextUtils.equals(lastMiniArtworkUrl, imageUrl)) {
            if (!TextUtils.isEmpty(imageUrl)) {
                Glide.with(this)
                        .load(imageUrl)
                        .transform(ArtworkTrimTransformation.obtain(), new com.bumptech.glide.load.resource.bitmap.CenterCrop())
                        .placeholder(R.drawable.ic_music)
                        .error(R.drawable.ic_music)
                        .into(ivMiniPlayerArt);
            } else {
                ivMiniPlayerArt.setImageResource(R.drawable.ic_music);
            }
            lastMiniArtworkTrackId = trackId;
            lastMiniArtworkUrl = imageUrl;
        }
    }

    @Nullable
    private PlaybackHistoryStore.QueueTrack findSnapshotTrackByVideoId(
            @NonNull PlaybackHistoryStore.Snapshot snapshot,
            @Nullable String videoId
    ) {
        if (TextUtils.isEmpty(videoId)) {
            return null;
        }
        for (PlaybackHistoryStore.QueueTrack track : snapshot.queue) {
            if (TextUtils.equals(track.videoId, videoId)) {
                return track;
            }
        }
        return null;
    }

    private int parseDurationSeconds(@Nullable String duration) {
        if (TextUtils.isEmpty(duration) || duration.contains("--")) {
            return 1;
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
            return 1;
        }

        return 1;
    }

    private void onLibraryPlaylistMorePressed(
            @NonNull YouTubeMusicService.TrackResult track,
            @NonNull View anchor
    ) {
        if (!isAdded() || !"playlist".equals(track.resultType)) {
            return;
        }

        anchor.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK);
        anchor.animate().cancel();
        anchor.animate()
                .scaleX(0.88f)
                .scaleY(0.88f)
                .setDuration(70L)
                .withEndAction(() -> anchor.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(120L)
                        .start())
                .start();

        showPlaylistActionTooltip(anchor, track);
    }

    private void showPlaylistActionTooltip(
            @NonNull View anchor,
            @NonNull YouTubeMusicService.TrackResult track
    ) {
        if (!isAdded()) {
            return;
        }

        dismissPlaylistActionTooltip();

        int popupWidth = dp(188);
        LinearLayout popupRoot = new LinearLayout(requireContext());
        popupRoot.setOrientation(LinearLayout.VERTICAL);
        popupRoot.setPadding(dp(6), dp(6), dp(6), dp(6));

        GradientDrawable popupBackground = new GradientDrawable();
        popupBackground.setColor(ContextCompat.getColor(requireContext(), R.color.surface_low));
        popupBackground.setCornerRadius(dp(14));
        popupRoot.setBackground(popupBackground);

        PopupWindow popupWindow = new PopupWindow(
                popupRoot,
                popupWidth,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                true
        );
        popupWindow.setOutsideTouchable(true);
        popupWindow.setFocusable(false);
        popupWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popupWindow.setElevation(dp(8));
        popupWindow.setOnDismissListener(() -> {
            if (playlistActionPopupWindow == popupWindow) {
                playlistActionPopupWindow = null;
            }
        });

        popupRoot.addView(createPlaylistActionRow(
                R.drawable.ic_download_bold,
                "Descargar",
                () -> enqueuePlaylistOfflineDownload(track),
                popupWindow
        ));

        playlistActionPopupWindow = popupWindow;

        int xOffset = -popupWidth + anchor.getWidth();
        int yOffset = dp(8);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            popupWindow.showAsDropDown(anchor, xOffset, yOffset, Gravity.START);
        } else {
            popupWindow.showAsDropDown(anchor, xOffset, yOffset);
        }
    }

    @NonNull
    private View createPlaylistActionRow(
            int iconRes,
            @NonNull String label,
            @NonNull Runnable action,
            @NonNull PopupWindow popupWindow
    ) {
        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(10), dp(12), dp(10));

        ImageView icon = new ImageView(requireContext());
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(18), dp(18));
        icon.setLayoutParams(iconParams);
        icon.setImageResource(iconRes);
        icon.setColorFilter(ContextCompat.getColor(requireContext(), android.R.color.white));

        TextView labelView = new TextView(requireContext());
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        labelParams.leftMargin = dp(10);
        labelView.setLayoutParams(labelParams);
        labelView.setText(label);
        labelView.setTextSize(13f);
        labelView.setTypeface(Typeface.DEFAULT_BOLD);
        labelView.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary));

        row.addView(icon);
        row.addView(labelView);

        GradientDrawable rowContent = new GradientDrawable();
        rowContent.setCornerRadius(dp(10));
        rowContent.setColor(Color.TRANSPARENT);
        int rippleColor = withAlpha(ContextCompat.getColor(requireContext(), R.color.stitch_blue_light), 0.32f);
        row.setBackground(new RippleDrawable(ColorStateList.valueOf(rippleColor), rowContent, null));

        row.setOnClickListener(v -> {
            action.run();
            popupWindow.dismiss();
        });

        return row;
    }

    private void dismissPlaylistActionTooltip() {
        if (playlistActionPopupWindow != null) {
            playlistActionPopupWindow.dismiss();
            playlistActionPopupWindow = null;
        }
    }

    private void enqueuePlaylistOfflineDownload(@NonNull YouTubeMusicService.TrackResult playlistTrack) {
        if (!isAdded()) {
            return;
        }

        String playlistId = playlistTrack.contentId == null ? "" : playlistTrack.contentId.trim();
        if (TextUtils.isEmpty(playlistId)) {
            Toast.makeText(requireContext(), "Playlist invalida para descargar.", Toast.LENGTH_SHORT).show();
            return;
        }

        ArrayList<CachedPlaylistTrack> cachedTracks = loadCachedPlaylistTracksForOffline(playlistId);
        if (cachedTracks.isEmpty()) {
            Toast.makeText(requireContext(), "Aún no hay cache de esta playlist para descargar.", Toast.LENGTH_SHORT).show();
            return;
        }

        Set<String> restrictedIds = OfflineRestrictionStore.getRestrictedVideoIds(requireContext());
        ArrayList<String> ids = new ArrayList<>();
        ArrayList<String> titles = new ArrayList<>();
        ArrayList<String> artists = new ArrayList<>();
        HashSet<String> seen = new HashSet<>();
        int skippedAlreadyOffline = 0;
        int skippedRestricted = 0;
        int totalEligibleWithVideoId = 0;

        for (CachedPlaylistTrack track : cachedTracks) {
            if (TextUtils.isEmpty(track.videoId)) {
                continue;
            }

            if (!seen.add(track.videoId)) {
                continue;
            }

            if (OfflineRestrictionStore.isRestricted(restrictedIds, track.videoId)) {
                skippedRestricted++;
                continue;
            }

            totalEligibleWithVideoId++;

            if (OfflineAudioStore.hasOfflineAudio(requireContext(), track.videoId)) {
                skippedAlreadyOffline++;
                continue;
            }

            ids.add(track.videoId);
            titles.add(track.title);
            artists.add(track.artist);
        }

        int totalWithVideoId = totalEligibleWithVideoId;
        if (totalWithVideoId <= 0) {
            if (skippedRestricted > 0) {
                Toast.makeText(requireContext(), "Esta playlist solo tiene canciones restringidas.", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(requireContext(), "No hay canciones validas para descargar.", Toast.LENGTH_SHORT).show();
            }
            if (adapter != null) {
                adapter.invalidatePlaylistOfflineState(playlistId);
            }
            return;
        }

        if (ids.isEmpty()) {
            String message = skippedRestricted > 0
                    ? "Ya tienes las canciones disponibles offline. Se omitieron restringidas."
                    : "Ya tienes todas las canciones offline.";
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
            if (adapter != null) {
                adapter.invalidatePlaylistOfflineState(playlistId);
            }
            return;
        }

        Data input = new Data.Builder()
                .putString(OfflinePlaylistDownloadWorker.INPUT_PLAYLIST_ID, playlistId)
                .putStringArray(OfflinePlaylistDownloadWorker.INPUT_VIDEO_IDS, ids.toArray(new String[0]))
                .putStringArray(OfflinePlaylistDownloadWorker.INPUT_TITLES, titles.toArray(new String[0]))
                .putStringArray(OfflinePlaylistDownloadWorker.INPUT_ARTISTS, artists.toArray(new String[0]))
                .putInt(OfflinePlaylistDownloadWorker.INPUT_ALREADY_OFFLINE_COUNT, skippedAlreadyOffline)
                .putInt(OfflinePlaylistDownloadWorker.INPUT_TOTAL_WITH_VIDEO_ID, totalWithVideoId)
                .build();

        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(OfflinePlaylistDownloadWorker.class)
                .setInputData(input)
                .addTag(OFFLINE_DOWNLOAD_QUEUE_UNIQUE_NAME)
                .addTag(OFFLINE_DOWNLOAD_UNIQUE_PREFIX + playlistId)
                .build();

        WorkManager.getInstance(requireContext().getApplicationContext())
                .enqueueUniqueWork(OFFLINE_DOWNLOAD_QUEUE_UNIQUE_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request);

        if (adapter != null) {
            adapter.invalidatePlaylistOfflineState(playlistId);
        }

        Toast.makeText(requireContext(), "Descarga agregada a la cola offline.", Toast.LENGTH_SHORT).show();
    }

    private boolean isPlaylistFullyOffline(@NonNull String playlistId) {
        Context context = getContext();
        if (context == null || TextUtils.isEmpty(playlistId)) {
            return false;
        }
        Context appContext = context.getApplicationContext();

        ArrayList<CachedPlaylistTrack> cachedTracks = loadCachedPlaylistTracksForOffline(playlistId);
        if (cachedTracks.isEmpty()) {
            return isPersistedPlaylistOfflineComplete(appContext, playlistId);
        }

        ArrayList<String> ids = new ArrayList<>();
        HashSet<String> seen = new HashSet<>();
        Set<String> restrictedIds = OfflineRestrictionStore.getRestrictedVideoIds(appContext);
        for (CachedPlaylistTrack track : cachedTracks) {
            if (TextUtils.isEmpty(track.videoId) || seen.contains(track.videoId)) {
                continue;
            }
            seen.add(track.videoId);
            if (OfflineRestrictionStore.isRestricted(restrictedIds, track.videoId)) {
                continue;
            }
            ids.add(track.videoId);
        }

        if (ids.isEmpty()) {
            persistPlaylistOfflineComplete(appContext, playlistId, true);
            return true;
        }

        int offlineCount = OfflineAudioStore.countOfflineAvailable(appContext, ids);
        boolean complete = offlineCount >= ids.size();
        persistPlaylistOfflineComplete(appContext, playlistId, complete);
        return complete;
    }

    private void persistPlaylistOfflineComplete(@NonNull String playlistId, boolean complete) {
        Context context = getContext();
        if (context == null || TextUtils.isEmpty(playlistId)) {
            return;
        }

        persistPlaylistOfflineComplete(context.getApplicationContext(), playlistId, complete);
    }

    private void persistPlaylistOfflineComplete(
            @NonNull Context context,
            @NonNull String playlistId,
            boolean complete
    ) {
        if (TextUtils.isEmpty(playlistId)) {
            return;
        }

        getCachePrefs(context).edit()
                .putBoolean(PREF_PLAYLIST_OFFLINE_COMPLETE_PREFIX + playlistId, complete)
                .apply();
    }

    private boolean isPersistedPlaylistOfflineComplete(@NonNull String playlistId) {
        Context context = getContext();
        if (context == null || TextUtils.isEmpty(playlistId)) {
            return false;
        }

        return isPersistedPlaylistOfflineComplete(context.getApplicationContext(), playlistId);
    }

    private boolean isPersistedPlaylistOfflineComplete(
            @NonNull Context context,
            @NonNull String playlistId
    ) {
        if (TextUtils.isEmpty(playlistId)) {
            return false;
        }

        return getCachePrefs(context).getBoolean(PREF_PLAYLIST_OFFLINE_COMPLETE_PREFIX + playlistId, false);
    }

    @NonNull
    private ArrayList<CachedPlaylistTrack> loadCachedPlaylistTracksForOffline(@NonNull String playlistId) {
        Context context = getContext();
        if (context == null) {
            return new ArrayList<>();
        }
        return loadCachedPlaylistTracksForOffline(context.getApplicationContext(), playlistId);
    }

    @NonNull
    private ArrayList<CachedPlaylistTrack> loadCachedPlaylistTracksForOffline(
            @NonNull Context context,
            @NonNull String playlistId
    ) {
        ArrayList<CachedPlaylistTrack> result = new ArrayList<>();
        if (TextUtils.isEmpty(playlistId)) {
            return result;
        }

        String raw = getCachePrefs(context).getString(PREF_TRACKS_DATA_PREFIX + playlistId, "");
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

                String videoId = obj.optString("videoId", "").trim();
                String title = obj.optString("title", "").trim();
                String artist = obj.optString("artist", "").trim();
                if (TextUtils.isEmpty(videoId)) {
                    continue;
                }

                result.add(new CachedPlaylistTrack(videoId, title, artist));
            }
        } catch (Exception ignored) {
            return new ArrayList<>();
        }

        return result;
    }

    private int withAlpha(int color, float alpha) {
        float clamped = Math.max(0f, Math.min(1f, alpha));
        int alphaInt = Math.round(255f * clamped);
        return (color & 0x00FFFFFF) | (alphaInt << 24);
    }

    private int dp(int value) {
        if (!isAdded()) {
            return value;
        }
        return Math.round(requireContext().getResources().getDisplayMetrics().density * value);
    }

    private void openTrack(@NonNull YouTubeMusicService.TrackResult track) {
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
                Toast.makeText(requireContext(), "No se pudo abrir este contenido.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void openTrackInIntegratedPlayer(@NonNull YouTubeMusicService.TrackResult selectedTrack) {
        if (!isAdded()) {
            return;
        }

        if (TextUtils.isEmpty(selectedTrack.videoId)) {
            Toast.makeText(requireContext(), "Ese resultado no tiene reproduccion directa.", Toast.LENGTH_SHORT).show();
            return;
        }

        ArrayList<String> ids = new ArrayList<>();
        ArrayList<String> titles = new ArrayList<>();
        ArrayList<String> artists = new ArrayList<>();
        ArrayList<String> durations = new ArrayList<>();
        ArrayList<String> images = new ArrayList<>();

        int selectedIndex = -1;
        for (YouTubeMusicService.TrackResult item : tracks) {
            if (item == null || !item.isVideo() || TextUtils.isEmpty(item.videoId)) {
                continue;
            }

            ids.add(item.videoId);
            titles.add(TextUtils.isEmpty(item.title) ? "Tema" : item.title);
            artists.add(item.subtitle == null ? "" : item.subtitle);
            durations.add("--:--");
            images.add(item.thumbnailUrl == null ? "" : item.thumbnailUrl);

            if (selectedIndex < 0 && TextUtils.equals(item.videoId, selectedTrack.videoId)) {
                selectedIndex = ids.size() - 1;
            }
        }

        if (ids.isEmpty()) {
            ids.add(selectedTrack.videoId);
            titles.add(TextUtils.isEmpty(selectedTrack.title) ? "Tema" : selectedTrack.title);
            artists.add(selectedTrack.subtitle == null ? "" : selectedTrack.subtitle);
            durations.add("--:--");
            images.add(selectedTrack.thumbnailUrl == null ? "" : selectedTrack.thumbnailUrl);
            selectedIndex = 0;
        }

        if (selectedIndex < 0) {
            selectedIndex = 0;
        }

        SongPlayerFragment existingPlayer = findSongPlayerFragment();
        if (existingPlayer != null && existingPlayer.isAdded()) {
            existingPlayer.externalSetReturnTargetTag(TAG_MODULE_MUSIC);
            existingPlayer.externalReplaceQueueFromStart(ids, titles, artists, durations, images, selectedIndex, true);

            getParentFragmentManager()
                    .beginTransaction()
                    .setCustomAnimations(R.anim.player_screen_enter, 0)
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

            getParentFragmentManager()
                    .beginTransaction()
                    .setCustomAnimations(R.anim.player_screen_enter, 0)
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

        String accessTokenForDetail = resolveAccessTokenForPlaylistDetail();

        PlaylistDetailFragment detailFragment = PlaylistDetailFragment.newInstance(
                track.contentId,
                track.title,
                track.subtitle,
                track.thumbnailUrl,
            accessTokenForDetail
        );

        getParentFragmentManager()
                .beginTransaction()
                .setReorderingAllowed(true)
                .hide(this)
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

    @Override
    public void onDestroyView() {
        dismissPlaylistActionTooltip();
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
        super.onDestroy();
    }

    private final class MusicResultsAdapter extends RecyclerView.Adapter<MusicResultsAdapter.TrackViewHolder> {

        private static final int VIEW_TYPE_LIST = 0;
        private static final int VIEW_TYPE_GRID = 1;

        private final List<YouTubeMusicService.TrackResult> data = new ArrayList<>();
        private final OnLibraryTrackClick onTrackClick;
        private final OnLibraryTrackMoreClick onTrackMoreClick;
        private final Map<String, Boolean> playlistOfflineCompleteCache = new HashMap<>();
        private final Set<String> playlistOfflineRefreshInFlight = new HashSet<>();
        private long playlistOfflineStateGeneration;
        private boolean gridMode;

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
            } else {
                playlistOfflineCompleteCache.remove(playlistId);
                requestPlaylistOfflineStateRefreshAsync(playlistId, playlistOfflineStateGeneration);
            }
            notifyDataSetChanged();
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

        void setGridMode(boolean gridMode) {
            if (this.gridMode == gridMode) {
                return;
            }
            this.gridMode = gridMode;
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
            int layoutRes = viewType == VIEW_TYPE_GRID
                    ? R.layout.item_music_result_grid
                    : R.layout.item_music_result;
            View view = LayoutInflater.from(parent.getContext()).inflate(layoutRes, parent, false);
            return new TrackViewHolder(view);
        }

        @Override
        public int getItemViewType(int position) {
            return gridMode ? VIEW_TYPE_GRID : VIEW_TYPE_LIST;
        }

        @Override
        public void onBindViewHolder(@NonNull TrackViewHolder holder, int position) {
            YouTubeMusicService.TrackResult item = data.get(position);
            boolean isLikedPlaylistStyle = isLikedPlaylistStyle(item, position);

            if (isLikedPlaylistStyle) {
                holder.tvTrackTitle.setText("Musica que te gusto");
                if (TextUtils.isEmpty(item.subtitle)) {
                    holder.tvTrackSubtitle.setText("Playlist autogenerada");
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

            if (TextUtils.isEmpty(item.subtitle) && !isLikedPlaylistStyle) {
                holder.tvTrackSubtitle.setText("Playlist");
            }

            holder.vLikedBackground.setVisibility(isLikedPlaylistStyle ? View.VISIBLE : View.GONE);
            holder.ivLikedIcon.setVisibility(isLikedPlaylistStyle ? View.VISIBLE : View.GONE);
            holder.ivTrackThumb.setVisibility(isLikedPlaylistStyle ? View.GONE : View.VISIBLE);

            if (!isLikedPlaylistStyle) {
                if (!TextUtils.isEmpty(item.thumbnailUrl)) {
                    Glide.with(holder.itemView)
                            .load(item.thumbnailUrl)
                            .transform(ArtworkTrimTransformation.obtain(), new com.bumptech.glide.load.resource.bitmap.CenterCrop())
                            .placeholder(R.drawable.ic_music)
                            .error(R.drawable.ic_music)
                            .into(holder.ivTrackThumb);
                } else {
                    holder.ivTrackThumb.setImageResource(R.drawable.ic_music);
                }
            }

            if (gridMode) {
                holder.vTrackDivider.setVisibility(View.GONE);
            } else {
                holder.vTrackDivider.setVisibility(position == data.size() - 1 ? View.GONE : View.VISIBLE);
            }

            boolean isPlaylistItem = "playlist".equals(item.resultType);
            holder.ivTrackMore.setVisibility(isPlaylistItem ? View.VISIBLE : View.GONE);
            holder.ivPlaylistOfflineAll.setVisibility(isPlaylistItem ? View.VISIBLE : View.GONE);

            if (isPlaylistItem) {
                String playlistId = item.contentId == null ? "" : item.contentId.trim();
                boolean fullyOffline = false;
                if (!TextUtils.isEmpty(playlistId)) {
                    Boolean cachedState = playlistOfflineCompleteCache.get(playlistId);
                    if (cachedState == null) {
                        cachedState = isPersistedPlaylistOfflineComplete(playlistId);
                        playlistOfflineCompleteCache.put(playlistId, cachedState);
                        requestPlaylistOfflineStateRefreshAsync(playlistId, playlistOfflineStateGeneration);
                    }
                    fullyOffline = cachedState;
                }

                if (fullyOffline) {
                    holder.ivPlaylistOfflineAll.setImageResource(R.drawable.ic_check_small);
                    holder.ivPlaylistOfflineAll.setBackgroundResource(R.drawable.bg_offline_state_filled_primary);
                    holder.ivPlaylistOfflineAll.setColorFilter(ContextCompat.getColor(
                            holder.itemView.getContext(),
                            R.color.surface_dark
                    ));
                    holder.ivPlaylistOfflineAll.setAlpha(1f);
                } else {
                    holder.ivPlaylistOfflineAll.setImageResource(R.drawable.ic_check_small);
                    holder.ivPlaylistOfflineAll.setBackgroundResource(R.drawable.bg_offline_state_outline_muted);
                    holder.ivPlaylistOfflineAll.setColorFilter(ContextCompat.getColor(
                            holder.itemView.getContext(),
                        R.color.text_secondary
                    ));
                    holder.ivPlaylistOfflineAll.setAlpha(0.55f);
                }

                holder.ivTrackMore.setOnClickListener(v -> onTrackMoreClick.onTrackMoreClick(item, holder.ivTrackMore));
            } else {
                holder.ivTrackMore.setOnClickListener(null);
            }

            holder.itemView.setOnClickListener(v -> onTrackClick.onTrackClick(item));
        }

        private boolean isLikedPlaylistStyle(@NonNull YouTubeMusicService.TrackResult item, int position) {
            if (position != 0) {
                return false;
            }

            if (YouTubeMusicService.SPECIAL_LIKED_VIDEOS_ID.equals(item.contentId)) {
                return true;
            }

            String normalizedTitle = item.title == null ? "" : item.title.toLowerCase(Locale.US);
            return normalizedTitle.contains("gusto")
                    || normalizedTitle.contains("liked")
                    || normalizedTitle.contains("me gusta");
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
            }
        }
    }

    private static final class CachedPlaylistTrack {
        final String videoId;
        final String title;
        final String artist;

        CachedPlaylistTrack(@NonNull String videoId, @NonNull String title, @NonNull String artist) {
            this.videoId = videoId;
            this.title = title;
            this.artist = artist;
        }
    }
}
