package com.example.sleppify;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.Lifecycle;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends AppCompatActivity {

    private static final String TAG_MODULE_SCHEDULE = "module_schedule";
    private static final String TAG_MODULE_MUSIC = "module_music";
    private static final String TAG_MODULE_EQUALIZER = "module_equalizer";
    private static final String TAG_MODULE_APPS = "module_apps";
    private static final String TAG_MODULE_SETTINGS = "module_settings";
    private static final String TAG_PLAYLIST_DETAIL = "playlist_detail";
    private static final String TAG_SONG_PLAYER = "song_player";
    private static final String PREFS_PLAYER_STATE = "player_state";
    private static final String PREF_LAST_STREAM_SCREEN = "stream_last_screen";
    private static final String STREAM_SCREEN_LIBRARY = "library";
    private static final String STREAM_SCREEN_PLAYLIST_DETAIL = "playlist_detail";
    private static final long RESUME_WORK_DELAY_MS = 180L;
    private static final long PREFETCH_DEBOUNCE_MS = 800L;
    private static final long PREFETCH_MIN_INTERVAL_MS = 12000L;

    private BottomNavigationView bottomNav;
    private TextView tvModuleTitle;
    private ImageView btnSettings;
    private View topAppBar;
    private View fragmentContainer;
    private View loginGateContainer;
    private TextView tvLoginStatus;
    private MaterialButton btnLoginGoogle;
    private boolean inSettings = false;
    private AuthManager authManager;
    private CloudSyncManager cloudSyncManager;
    private boolean loginGateAuthInProgress;
    private Fragment scheduleFragment;
    private Fragment musicFragment;
    private Fragment equalizerFragment;
    private Fragment appsFragment;
    private Fragment settingsFragment;
    private Fragment playlistDetailFragment;
    private Fragment songPlayerFragment;
    private int currentMainNavItemId = android.view.View.NO_ID;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private long lastSmartPrefetchAtMs;
    private boolean hasAudioServiceStateSnapshot;
    private boolean lastEqEnabled;
    private boolean lastSpatialEnabled;
    private int lastReverbLevel;
    private boolean hasAgendaScheduleSnapshot;
    private boolean lastSmartSuggestionsEnabled;
    private int lastSummaryIntervalHours;
    @Nullable
    private Typeface headerBrandTypeface;
    @Nullable
    private Typeface headerSettingsTypeface;
    private final CloudSyncManager.SyncStateListener syncStateListener = this::setSyncOverlayVisible;
    private final Runnable deferredResumeWorkRunnable = this::runDeferredResumeWork;
    private final Runnable smartPrefetchRunnable = this::runSmartPrefetch;
    private final OnBackPressedCallback backPressedCallback = new OnBackPressedCallback(true) {
        @Override
        public void handleOnBackPressed() {
            if (inSettings) {
                exitSettings();
                return;
            }

            if (handleSongPlayerBackPressed()) {
                return;
            }

            if (handlePlaylistDetailBackPressed()) {
                return;
            }

            if (shouldMoveTaskToBackForOngoingPlayback()) {
                moveTaskToBack(true);
                return;
            }

            setEnabled(false);
            getOnBackPressedDispatcher().onBackPressed();
            setEnabled(true);
        }
    };

    private boolean shouldMoveTaskToBackForOngoingPlayback() {
        PlaybackHistoryStore.Snapshot snapshot = PlaybackHistoryStore.load(this);
        return snapshot != null
                && snapshot.isPlaying
                && snapshot.queue != null
                && !snapshot.queue.isEmpty();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        android.view.View mainRoot = findViewById(R.id.main);
        bottomNav = findViewById(R.id.bottomNavigation);
        tvModuleTitle = findViewById(R.id.tvModuleTitle);
        btnSettings = findViewById(R.id.btnSettings);
        topAppBar = findViewById(R.id.topAppBar);
        fragmentContainer = findViewById(R.id.fragmentContainer);
        loginGateContainer = findViewById(R.id.loginGateContainer);
        tvLoginStatus = findViewById(R.id.tvLoginStatus);
        btnLoginGoogle = findViewById(R.id.btnLoginGoogle);
        configureHeaderActionForMainModules();

        if (btnLoginGoogle != null) {
            btnLoginGoogle.setOnClickListener(v -> startLoginFromGate());
        }

        authManager = AuthManager.getInstance(this);
        cloudSyncManager = CloudSyncManager.getInstance(this);
        cloudSyncManager.setSyncStateListener(syncStateListener);
        getOnBackPressedDispatcher().addCallback(this, backPressedCallback);
        restoreMainModuleReferences();
        syncAudioEffectsServiceFromPreferences(true);
        syncDailyAgendaNotificationSchedule(true);

        ViewCompat.setOnApplyWindowInsetsListener(mainRoot, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            float density = getResources().getDisplayMetrics().density;
            int extraPadding = Math.round(8 * density); // 8dp extra de respiro

            // Aplicamos padding superior al Header (Status Bar + 8dp)
            topAppBar.setPadding(topAppBar.getPaddingLeft(), systemBars.top + extraPadding, topAppBar.getPaddingRight(), topAppBar.getPaddingBottom());

            // Aplicamos padding al Bottom Nav para que no se pegue abajo
            bottomNav.setPadding(systemBars.left, 0, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Settings button
        btnSettings.setOnClickListener(v -> {
            if (inSettings) {
                exitSettings();
            } else {
                enterSettings();
            }
        });

        // Bottom nav
        bottomNav.setOnItemSelectedListener(item -> {
            if (inSettings) exitSettings();

            return switchToMainModule(item.getItemId());
        });

        // Default fragment
        if (savedInstanceState == null) {
            bottomNav.setSelectedItemId(R.id.nav_schedule);
        } else {
            int selectedId = bottomNav.getSelectedItemId();
            if (!switchToMainModule(selectedId)) {
                bottomNav.setSelectedItemId(R.id.nav_schedule);
            }
        }

        if (authManager.isSignedIn() && authManager.getCurrentUser() != null) {
            showMainShell();
            handleSignedInUser(authManager.getCurrentUser(), null);
            prefetchSmartSuggestions(false);
        } else {
            showLoginGate();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        scheduleDeferredResumeWork(RESUME_WORK_DELAY_MS);
    }

    @Override
    protected void onDestroy() {
        mainHandler.removeCallbacks(deferredResumeWorkRunnable);
        mainHandler.removeCallbacks(smartPrefetchRunnable);
        cloudSyncManager.setSyncStateListener(null);
        super.onDestroy();
    }

    private void scheduleDeferredResumeWork(long delayMs) {
        mainHandler.removeCallbacks(deferredResumeWorkRunnable);
        mainHandler.postDelayed(deferredResumeWorkRunnable, Math.max(0L, delayMs));
    }

    private void runDeferredResumeWork() {
        if (isFinishing() || isDestroyed()) {
            return;
        }

        // Keep the login gate button state while account picker/auth flow is in progress.
        if (loginGateAuthInProgress) {
            return;
        }

        cloudSyncManager.setSyncStateListener(syncStateListener);
        syncAudioEffectsServiceFromPreferences(false);
        syncDailyAgendaNotificationSchedule(false);

        if (authManager.isSignedIn() && authManager.getCurrentUser() != null) {
            showMainShell();
            prefetchSmartSuggestions(false);
            return;
        }

        showLoginGate();
    }

    private void syncAudioEffectsServiceFromPreferences(boolean forceSync) {
        SharedPreferences audioPrefs = getSharedPreferences(AudioEffectsService.PREFS_NAME, MODE_PRIVATE);
        boolean eqEnabled = audioPrefs.getBoolean(AudioEffectsService.KEY_ENABLED, false);
        boolean spatialEnabled = audioPrefs.getBoolean(AudioEffectsService.KEY_SPATIAL_ENABLED, false);
        int reverbLevel = audioPrefs.getInt(AudioEffectsService.KEY_REVERB_LEVEL, AudioEffectsService.REVERB_LEVEL_OFF);
        boolean reverbEnabled = reverbLevel == AudioEffectsService.REVERB_LEVEL_LIGHT
                || reverbLevel == AudioEffectsService.REVERB_LEVEL_MEDIUM
                || reverbLevel == AudioEffectsService.REVERB_LEVEL_STRONG;

        if (!forceSync
                && hasAudioServiceStateSnapshot
                && eqEnabled == lastEqEnabled
                && spatialEnabled == lastSpatialEnabled
                && reverbLevel == lastReverbLevel) {
            return;
        }

        hasAudioServiceStateSnapshot = true;
        lastEqEnabled = eqEnabled;
        lastSpatialEnabled = spatialEnabled;
        lastReverbLevel = reverbLevel;

        Intent intent = new Intent(getApplicationContext(), AudioEffectsService.class);
        if (eqEnabled || spatialEnabled || reverbEnabled) {
            intent.setAction(AudioEffectsService.ACTION_APPLY);
            ContextCompat.startForegroundService(getApplicationContext(), intent);
            return;
        }

        intent.setAction(AudioEffectsService.ACTION_STOP);
        startService(intent);
    }

    public void requireAuth(Runnable onSuccess) {
        requireAuth(onSuccess, null);
    }

    public void requireAuth(@Nullable Runnable onSuccess, @Nullable Runnable onError) {
        if (authManager.isSignedIn()) {
            com.google.firebase.auth.FirebaseUser currentUser = authManager.getCurrentUser();
            if (currentUser != null) {
                if (loginGateAuthInProgress) {
                    setLoginGateButtonState(true, "Sincronizando...");
                }
                handleSignedInUser(currentUser, onSuccess);
                return;
            }
        }

        authManager.signIn(this, new AuthManager.AuthCallback() {
            @Override
            public void onSuccess(@androidx.annotation.NonNull com.google.firebase.auth.FirebaseUser user) {
                setLoginGateButtonState(true, "Sincronizando...");
                handleSignedInUser(user, onSuccess);
            }

            @Override
            public void onError(@androidx.annotation.NonNull String message) {
                Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show();
                if (onError != null) {
                    onError.run();
                }
            }
        });
    }

    private void startLoginFromGate() {
        if (loginGateAuthInProgress) {
            return;
        }

        setLoginGateButtonState(true, "Conectando...");
        requireAuth(
                () -> {
                    setLoginGateButtonState(false, "Sign in with Google");
                    showMainShell();
                    if (bottomNav != null) {
                        bottomNav.setSelectedItemId(R.id.nav_schedule);
                    }
                    switchToMainModule(R.id.nav_schedule);
                },
                () -> setLoginGateButtonState(false, "Sign in with Google")
        );
    }

    private void setLoginGateButtonState(boolean loading, @NonNull String buttonText) {
        loginGateAuthInProgress = loading;
        if (btnLoginGoogle != null) {
            btnLoginGoogle.setEnabled(!loading);
            btnLoginGoogle.setAlpha(loading ? 0.56f : 1f);
            btnLoginGoogle.setText(buttonText);
        }
    }

    private void showLoginGate() {
        if (loginGateContainer != null) {
            loginGateContainer.setVisibility(View.VISIBLE);
        }
        if (topAppBar != null) {
            topAppBar.setVisibility(View.GONE);
        }
        if (fragmentContainer != null) {
            fragmentContainer.setVisibility(View.GONE);
        }
        if (bottomNav != null) {
            bottomNav.setVisibility(View.GONE);
        }
        if (tvLoginStatus != null) {
            tvLoginStatus.setText("Inicia sesion para cargar tu agenda.");
        }
        if (!loginGateAuthInProgress) {
            setLoginGateButtonState(false, "Sign in with Google");
        }
    }

    private void showMainShell() {
        if (loginGateContainer != null) {
            loginGateContainer.setVisibility(View.GONE);
        }
        if (topAppBar != null) {
            topAppBar.setVisibility(View.VISIBLE);
        }
        if (fragmentContainer != null) {
            fragmentContainer.setVisibility(View.VISIBLE);
        }
        if (bottomNav != null) {
            bottomNav.setVisibility(inSettings ? View.GONE : View.VISIBLE);
        }
    }

    private void handleSignedInUser(
            @NonNull com.google.firebase.auth.FirebaseUser user,
            @Nullable Runnable onSuccess
    ) {
        cloudSyncManager.onUserSignedIn(user.getUid(), (ok, message) -> {
            if (!ok && message != null) {
                Toast.makeText(MainActivity.this, "Sync parcial: " + message, Toast.LENGTH_SHORT).show();
            }
            notifyScheduleHydrationCompleted();
            syncAudioEffectsServiceFromPreferences(true);
            notifyEqualizerHydrationCompleted();
            prefetchSmartSuggestions(true);
            if (onSuccess != null) {
                onSuccess.run();
            }
        });
    }

    public AuthManager getAuthManager() {
        return authManager;
    }

    public CloudSyncManager getCloudSyncManager() {
        return cloudSyncManager;
    }

    public void refreshSessionUi() {
        if (!authManager.isSignedIn() || authManager.getCurrentUser() == null) {
            showLoginGate();
            return;
        }

        showMainShell();
        syncDailyAgendaNotificationSchedule(false);
        prefetchSmartSuggestions(false);
    }

    private void notifyEqualizerHydrationCompleted() {
        mainHandler.post(() -> {
            Fragment currentEqualizer = getMainModuleFragment(R.id.nav_equalizer);
            if (currentEqualizer instanceof EqualizerFragment) {
                ((EqualizerFragment) currentEqualizer).onCloudEqHydrationCompleted();
            }
        });
    }

    private void notifyScheduleHydrationCompleted() {
        mainHandler.post(() -> {
            Fragment currentSchedule = getMainModuleFragment(R.id.nav_schedule);
            if (currentSchedule instanceof WeeklySchedulerFragment) {
                ((WeeklySchedulerFragment) currentSchedule).onCloudAgendaHydrationCompleted();
            }
        });
    }

    private void setSyncOverlayVisible(boolean visible) {
        if (visible) {
            // Reservado para futuras señales visuales de sync en el header principal.
        }
    }

    private void configureHeaderActionForMainModules() {
        btnSettings.setVisibility(android.view.View.VISIBLE);
        btnSettings.setImageResource(R.drawable.ic_settings);
        btnSettings.setContentDescription(getString(R.string.header_action_settings));
        float density = getResources().getDisplayMetrics().density;
        int drawablePadding = Math.round(8f * density);
        int iconSizePx = Math.round(26f * density);
        tvModuleTitle.setText(R.string.header_brand_title);
        tvModuleTitle.setAllCaps(true);
        tvModuleTitle.setLetterSpacing(0.08f);
        tvModuleTitle.setTypeface(resolveHeaderBrandTypeface());
        tvModuleTitle.setContentDescription(getString(R.string.header_action_go_agenda));
        android.graphics.drawable.Drawable brandIcon = ContextCompat.getDrawable(this, R.mipmap.ic_launcher);
        if (brandIcon != null) {
            brandIcon.setBounds(0, 0, iconSizePx, iconSizePx);
            tvModuleTitle.setCompoundDrawablesRelative(brandIcon, null, null, null);
        } else {
            tvModuleTitle.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, 0, 0);
        }
        tvModuleTitle.setCompoundDrawablePadding(drawablePadding);
        tvModuleTitle.setClickable(true);
        tvModuleTitle.setFocusable(true);
        tvModuleTitle.setOnClickListener(v -> goToAgendaFromHeader());
    }

    private void configureHeaderActionForSettings() {
        btnSettings.setVisibility(android.view.View.GONE);
        int drawablePadding = Math.round(10f * getResources().getDisplayMetrics().density);
        tvModuleTitle.setText(R.string.header_title_settings);
        tvModuleTitle.setAllCaps(false);
        tvModuleTitle.setLetterSpacing(0f);
        tvModuleTitle.setTypeface(resolveHeaderSettingsTypeface());
        tvModuleTitle.setContentDescription(getString(R.string.header_action_back));
        tvModuleTitle.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_arrow_back, 0, 0, 0);
        tvModuleTitle.setCompoundDrawablePadding(drawablePadding);
        tvModuleTitle.setClickable(true);
        tvModuleTitle.setFocusable(true);
        tvModuleTitle.setOnClickListener(v -> exitSettings());
    }

    @NonNull
    private Typeface resolveHeaderBrandTypeface() {
        if (headerBrandTypeface == null) {
            Typeface loaded = ResourcesCompat.getFont(this, R.font.manrope_variable);
            headerBrandTypeface = loaded != null ? loaded : Typeface.DEFAULT_BOLD;
        }
        return headerBrandTypeface;
    }

    @NonNull
    private Typeface resolveHeaderSettingsTypeface() {
        if (headerSettingsTypeface == null) {
            Typeface loaded = ResourcesCompat.getFont(this, R.font.inter_variable);
            headerSettingsTypeface = loaded != null ? loaded : Typeface.DEFAULT_BOLD;
        }
        return headerSettingsTypeface;
    }

    private void goToAgendaFromHeader() {
        if (inSettings) {
            exitSettings();
        }

        if (bottomNav.getSelectedItemId() != R.id.nav_schedule) {
            bottomNav.setSelectedItemId(R.id.nav_schedule);
            return;
        }

        switchToMainModule(R.id.nav_schedule);
    }

    private void enterSettings() {
        if (inSettings) {
            return;
        }

        if (bottomNav != null && bottomNav.getSelectedItemId() == R.id.nav_music) {
            markStreamingEntryAsLibrary();
        }

        inSettings = true;

        Fragment target = getOrCreateSettingsFragment();
        FragmentTransaction transaction = getSupportFragmentManager()
                .beginTransaction()
                .setReorderingAllowed(true);

        playlistDetailFragment = getSupportFragmentManager().findFragmentByTag(TAG_PLAYLIST_DETAIL);
        songPlayerFragment = getSupportFragmentManager().findFragmentByTag(TAG_SONG_PLAYER);
        Fragment currentMainFragment = getMainModuleFragment(currentMainNavItemId);

        hideIfVisible(transaction, currentMainFragment, target);
        hideIfVisible(transaction, playlistDetailFragment, target);
        hideIfVisible(transaction, songPlayerFragment, target);

        if (target.isAdded()) {
            transaction.show(target);
        } else {
            transaction.add(R.id.fragmentContainer, target, TAG_MODULE_SETTINGS);
        }
        transaction.setMaxLifecycle(target, Lifecycle.State.RESUMED);
        transaction.commit();

        // Update header in settings
        configureHeaderActionForSettings();
        bottomNav.setVisibility(android.view.View.GONE);
    }

    private void exitSettings() {
        if (!inSettings) {
            return;
        }

        inSettings = false;

        int selectedId = bottomNav.getSelectedItemId();
        Fragment target = getMainModuleFragment(selectedId);
        String targetTag = moduleTagForItem(selectedId);
        if (target == null) {
            target = getOrCreateMainModuleFragment(selectedId);
        }

        FragmentTransaction transaction = getSupportFragmentManager()
                .beginTransaction()
                .setReorderingAllowed(true);

        if (settingsFragment != null && settingsFragment.isAdded()) {
            transaction.hide(settingsFragment);
            transaction.setMaxLifecycle(settingsFragment, Lifecycle.State.STARTED);
        }

        if (target != null) {
            if (target.isAdded()) {
                transaction.show(target);
            } else if (targetTag != null) {
                transaction.add(R.id.fragmentContainer, target, targetTag);
            }
            transaction.setMaxLifecycle(target, Lifecycle.State.RESUMED);
        }
        transaction.commit();

        // Restore header
        configureHeaderActionForMainModules();
        bottomNav.setVisibility(android.view.View.VISIBLE);

        // Restore title based on current nav selection
        updateHeaderTitleForModule(selectedId);
    }

    private void prefetchSmartSuggestions(boolean force) {
        long now = System.currentTimeMillis();
        if (!force && (now - lastSmartPrefetchAtMs) < PREFETCH_MIN_INTERVAL_MS) {
            return;
        }

        mainHandler.removeCallbacks(smartPrefetchRunnable);
        long delay = force ? 200L : PREFETCH_DEBOUNCE_MS;
        mainHandler.postDelayed(smartPrefetchRunnable, delay);
    }

    private void runSmartPrefetch() {
        if (isFinishing() || isDestroyed()) {
            return;
        }

        lastSmartPrefetchAtMs = System.currentTimeMillis();
        String profileName = authManager != null && authManager.isSignedIn()
                ? authManager.getDisplayName()
                : "usuario";
        new Thread(() -> SmartSuggestionPrefetcher.prefetchAll(getApplicationContext(), profileName), "prefetch-main").start();
    }

    private void syncDailyAgendaNotificationSchedule(boolean forceSync) {
        SharedPreferences settingsPrefs = getSharedPreferences(CloudSyncManager.PREFS_SETTINGS, MODE_PRIVATE);
        boolean smartSuggestionsEnabled = settingsPrefs.getBoolean(
                CloudSyncManager.KEY_SMART_SUGGESTIONS_ENABLED,
                settingsPrefs.getBoolean(CloudSyncManager.KEY_AI_SHIFT_ENABLED, true)
        );
        int intervalHours = settingsPrefs.getInt(CloudSyncManager.KEY_DAILY_SUMMARY_INTERVAL_HOURS, 2);

        if (!forceSync
                && hasAgendaScheduleSnapshot
                && lastSmartSuggestionsEnabled == smartSuggestionsEnabled
                && lastSummaryIntervalHours == intervalHours) {
            return;
        }

        hasAgendaScheduleSnapshot = true;
        lastSmartSuggestionsEnabled = smartSuggestionsEnabled;
        lastSummaryIntervalHours = intervalHours;

        if (!smartSuggestionsEnabled) {
            DailyAgendaNotificationWorker.cancel(getApplicationContext());
            TaskReminderScheduler.cancelAll(getApplicationContext());
            return;
        }

        DailyAgendaNotificationWorker.schedule(getApplicationContext(), intervalHours);
        TaskReminderScheduler.rescheduleAll(getApplicationContext());
    }

    private void restoreMainModuleReferences() {
        scheduleFragment = getSupportFragmentManager().findFragmentByTag(TAG_MODULE_SCHEDULE);
        musicFragment = getSupportFragmentManager().findFragmentByTag(TAG_MODULE_MUSIC);
        equalizerFragment = getSupportFragmentManager().findFragmentByTag(TAG_MODULE_EQUALIZER);
        appsFragment = getSupportFragmentManager().findFragmentByTag(TAG_MODULE_APPS);
        settingsFragment = getSupportFragmentManager().findFragmentByTag(TAG_MODULE_SETTINGS);
        playlistDetailFragment = getSupportFragmentManager().findFragmentByTag(TAG_PLAYLIST_DETAIL);
        songPlayerFragment = getSupportFragmentManager().findFragmentByTag(TAG_SONG_PLAYER);
    }

    @NonNull
    private Fragment getOrCreateSettingsFragment() {
        if (settingsFragment == null) {
            settingsFragment = new SettingsFragment();
        }
        return settingsFragment;
    }

    @Nullable
    private Fragment getOrCreateMainModuleFragment(int itemId) {
        Fragment existing = getMainModuleFragment(itemId);
        if (existing != null) {
            return existing;
        }

        if (itemId == R.id.nav_schedule) {
            scheduleFragment = new WeeklySchedulerFragment();
            return scheduleFragment;
        }
        if (itemId == R.id.nav_music) {
            musicFragment = new MusicPlayerFragment();
            return musicFragment;
        }
        if (itemId == R.id.nav_equalizer) {
            equalizerFragment = new EqualizerFragment();
            return equalizerFragment;
        }
        if (itemId == R.id.nav_apps) {
            appsFragment = new AppsFragment();
            return appsFragment;
        }
        return null;
    }

    @Nullable
    private Fragment getMainModuleFragment(int itemId) {
        if (itemId == R.id.nav_schedule) {
            return scheduleFragment;
        }
        if (itemId == R.id.nav_music) {
            return musicFragment;
        }
        if (itemId == R.id.nav_equalizer) {
            return equalizerFragment;
        }
        if (itemId == R.id.nav_apps) {
            return appsFragment;
        }
        return null;
    }

    @Nullable
    private String moduleTagForItem(int itemId) {
        if (itemId == R.id.nav_schedule) {
            return TAG_MODULE_SCHEDULE;
        }
        if (itemId == R.id.nav_music) {
            return TAG_MODULE_MUSIC;
        }
        if (itemId == R.id.nav_equalizer) {
            return TAG_MODULE_EQUALIZER;
        }
        if (itemId == R.id.nav_apps) {
            return TAG_MODULE_APPS;
        }
        return null;
    }

    private void hideIfAdded(@NonNull FragmentTransaction transaction, @Nullable Fragment fragment, @NonNull Fragment target) {
        if (fragment == null || fragment == target || !fragment.isAdded()) {
            return;
        }
        transaction.hide(fragment);
        transaction.setMaxLifecycle(fragment, Lifecycle.State.STARTED);
    }

    private void hideIfVisible(@NonNull FragmentTransaction transaction, @Nullable Fragment fragment, @NonNull Fragment target) {
        if (fragment == null || fragment == target || !fragment.isAdded() || fragment.isHidden()) {
            return;
        }
        transaction.hide(fragment);
        if (fragment.getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.RESUMED)) {
            transaction.setMaxLifecycle(fragment, Lifecycle.State.STARTED);
        }
    }

    private boolean switchToMainModule(int itemId) {
        Fragment target = getOrCreateMainModuleFragment(itemId);
        String targetTag = moduleTagForItem(itemId);
        if (target == null || targetTag == null) {
            return false;
        }

        if (currentMainNavItemId == R.id.nav_music && itemId != R.id.nav_music) {
            markStreamingEntryAsLibrary();
        }

        if (currentMainNavItemId == itemId && target.isAdded() && !inSettings) {
            updateHeaderTitleForModule(itemId);
            return true;
        }

        FragmentTransaction transaction = getSupportFragmentManager()
                .beginTransaction()
                .setReorderingAllowed(true);

        playlistDetailFragment = getSupportFragmentManager().findFragmentByTag(TAG_PLAYLIST_DETAIL);
        songPlayerFragment = getSupportFragmentManager().findFragmentByTag(TAG_SONG_PLAYER);
        Fragment currentMainFragment = getMainModuleFragment(currentMainNavItemId);

        hideIfVisible(transaction, currentMainFragment, target);
        hideIfVisible(transaction, playlistDetailFragment, target);
        hideIfVisible(transaction, songPlayerFragment, target);
        hideIfVisible(transaction, settingsFragment, target);

        if (target.isAdded()) {
            transaction.show(target);
        } else {
            transaction.add(R.id.fragmentContainer, target, targetTag);
        }
        transaction.setMaxLifecycle(target, Lifecycle.State.RESUMED);
        transaction.commit();

        currentMainNavItemId = itemId;
        if (itemId == R.id.nav_music) {
            markStreamingEntryAsLibrary();
        }
        updateHeaderTitleForModule(itemId);
        return true;
    }

    private void markStreamingEntryAsLibrary() {
        persistStreamingScreenState(STREAM_SCREEN_LIBRARY);

        songPlayerFragment = getSupportFragmentManager().findFragmentByTag(TAG_SONG_PLAYER);
        if (songPlayerFragment instanceof SongPlayerFragment
                && songPlayerFragment.isAdded()) {
            ((SongPlayerFragment) songPlayerFragment).externalSetReturnTargetTag(TAG_MODULE_MUSIC);
        }
    }

    private void persistStreamingScreenState(@NonNull String screen) {
        getSharedPreferences(PREFS_PLAYER_STATE, MODE_PRIVATE)
                .edit()
                .putString(PREF_LAST_STREAM_SCREEN, screen)
                .apply();
    }

    private void snapshotStreamingScreenBeforeNavigation() {
        songPlayerFragment = getSupportFragmentManager().findFragmentByTag(TAG_SONG_PLAYER);
        playlistDetailFragment = getSupportFragmentManager().findFragmentByTag(TAG_PLAYLIST_DETAIL);

        if (songPlayerFragment instanceof SongPlayerFragment
                && songPlayerFragment.isAdded()
                && !songPlayerFragment.isHidden()) {
            SongPlayerFragment player = (SongPlayerFragment) songPlayerFragment;
            player.externalSnapshotForNavigation();

            String targetTag = player.externalGetReturnTargetTag();
            if (TextUtils.equals(TAG_PLAYLIST_DETAIL, targetTag)) {
                persistStreamingScreenState(STREAM_SCREEN_PLAYLIST_DETAIL);
            } else {
                persistStreamingScreenState(STREAM_SCREEN_LIBRARY);
            }
            return;
        }

        boolean isDetailVisible = playlistDetailFragment != null
                && playlistDetailFragment.isAdded()
                && !playlistDetailFragment.isHidden();

        persistStreamingScreenState(isDetailVisible
                ? STREAM_SCREEN_PLAYLIST_DETAIL
                : STREAM_SCREEN_LIBRARY);
    }

    private void dismissPlaylistDetailIfPresent() {
        try {
            getSupportFragmentManager().popBackStackImmediate(TAG_PLAYLIST_DETAIL, androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE);
        } catch (Exception ignored) {
        }
        playlistDetailFragment = getSupportFragmentManager().findFragmentByTag(TAG_PLAYLIST_DETAIL);
    }

    private boolean shouldRestoreStreamingPlaylistDetail() {
        SharedPreferences prefs = getSharedPreferences(PREFS_PLAYER_STATE, MODE_PRIVATE);
        String lastScreen = prefs.getString(PREF_LAST_STREAM_SCREEN, "");
        return TextUtils.equals(STREAM_SCREEN_PLAYLIST_DETAIL, lastScreen);
    }

    private void restoreStreamingDetailIfNeeded() {
        if (inSettings || bottomNav == null || bottomNav.getSelectedItemId() != R.id.nav_music) {
            return;
        }
        if (!shouldRestoreStreamingPlaylistDetail()) {
            return;
        }

        playlistDetailFragment = getSupportFragmentManager().findFragmentByTag(TAG_PLAYLIST_DETAIL);
        songPlayerFragment = getSupportFragmentManager().findFragmentByTag(TAG_SONG_PLAYER);
        musicFragment = getSupportFragmentManager().findFragmentByTag(TAG_MODULE_MUSIC);

        if (playlistDetailFragment != null && playlistDetailFragment.isAdded()) {
            FragmentTransaction transaction = getSupportFragmentManager()
                    .beginTransaction()
                    .setReorderingAllowed(true);

            if (musicFragment != null && musicFragment.isAdded() && musicFragment != playlistDetailFragment) {
                transaction.hide(musicFragment);
                transaction.setMaxLifecycle(musicFragment, Lifecycle.State.STARTED);
            }

            if (songPlayerFragment != null && songPlayerFragment.isAdded() && songPlayerFragment != playlistDetailFragment) {
                transaction.hide(songPlayerFragment);
                transaction.setMaxLifecycle(songPlayerFragment, Lifecycle.State.STARTED);
            }

            transaction.show(playlistDetailFragment);
            transaction.setMaxLifecycle(playlistDetailFragment, Lifecycle.State.RESUMED);
            transaction.commit();
            return;
        }

        if (musicFragment instanceof MusicPlayerFragment && musicFragment.isAdded()) {
            ((MusicPlayerFragment) musicFragment).externalRestoreLastPlaylistDetailIfNeeded();
        }
    }

    private void dismissSongPlayerIfPresent() {
        try {
            getSupportFragmentManager().popBackStackImmediate(TAG_SONG_PLAYER, androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE);
        } catch (Exception ignored) {
        }
        songPlayerFragment = getSupportFragmentManager().findFragmentByTag(TAG_SONG_PLAYER);
    }

    private boolean handleSongPlayerBackPressed() {
        songPlayerFragment = getSupportFragmentManager().findFragmentByTag(TAG_SONG_PLAYER);
        if (!(songPlayerFragment instanceof SongPlayerFragment)) {
            return false;
        }
        if (!songPlayerFragment.isAdded() || songPlayerFragment.isHidden()) {
            return false;
        }

        markStreamingEntryAsLibrary();
        ((SongPlayerFragment) songPlayerFragment).enterMiniMode();
        return true;
    }

    private boolean handlePlaylistDetailBackPressed() {
        playlistDetailFragment = getSupportFragmentManager().findFragmentByTag(TAG_PLAYLIST_DETAIL);
        if (playlistDetailFragment == null
                || !playlistDetailFragment.isAdded()
                || playlistDetailFragment.isHidden()) {
            return false;
        }

        markStreamingEntryAsLibrary();
        dismissPlaylistDetailIfPresent();

        if (bottomNav != null && bottomNav.getSelectedItemId() != R.id.nav_music) {
            bottomNav.setSelectedItemId(R.id.nav_music);
        }
        currentMainNavItemId = R.id.nav_music;
        updateHeaderTitleForModule(R.id.nav_music);
        return true;
    }

    private void updateHeaderTitleForModule(int itemId) {
        if (inSettings) {
            tvModuleTitle.setText(R.string.header_title_settings);
            return;
        }

        tvModuleTitle.setText(R.string.header_brand_title);
    }

}