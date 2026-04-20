package com.example.sleppify

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.text.TextUtils
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import com.bumptech.glide.Glide
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentTransaction
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.*
import java.util.ArrayList

/**
 * Optimized Kotlin version of MainActivity.
 * Central hub for navigation, authentication, and core UI orchestration.
 */
class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG_MODULE_SCHEDULE = "module_schedule"
        private const val TAG_MODULE_MUSIC = "module_music"
        private const val TAG_MODULE_SCANNER = "module_scanner"
        private const val TAG_MODULE_EQUALIZER = "module_equalizer"
        private const val TAG_MODULE_APPS = "module_apps"
        private const val TAG_MODULE_SETTINGS = "module_settings"
        private const val TAG_PLAYLIST_DETAIL = "playlist_detail"
        private const val TAG_SONG_PLAYER = "song_player"
        private const val TAG_SEARCH = "search_fragment"

        private const val PREFS_PLAYER_STATE = "player_state"
        private const val PREFS_BOOTSTRAP = "sleppify_bootstrap"
        private const val KEY_INITIAL_OAUTH_GATE_COMPLETED = "initial_oauth_gate_completed"
        private const val PREF_LAST_STREAM_SCREEN = "stream_last_screen"
        private const val STREAM_SCREEN_LIBRARY = "library"
        private const val STREAM_SCREEN_PLAYLIST_DETAIL = "playlist_detail"

        private const val RESUME_WORK_DELAY_MS = 180L
        private const val PREFETCH_DEBOUNCE_MS = 800L
        private const val PREFETCH_MIN_INTERVAL_MS = 3 * 60 * 60 * 1000L // 3 hours
        
        const val ACTION_PLAY_FROM_SEARCH = "com.example.sleppify.ACTION_PLAY_FROM_SEARCH"
        const val EXTRA_TARGET_NAV_ITEM_ID = "TARGET_NAV_ITEM_ID"
        private const val REQUEST_CODE_RECORD_AUDIO = 4107
    }

    private lateinit var bottomNav: BottomNavigationView
    private lateinit var tvModuleTitle: TextView
    private lateinit var btnSettings: ImageView
    private lateinit var topAppBar: View
    private lateinit var fragmentContainer: View
    private lateinit var moduleLoadingOverlay: View
    private var loginGateContainer: View? = null
    private var tvLoginStatus: TextView? = null
    private var btnLoginGoogle: MaterialButton? = null
    
    private lateinit var llMiniPlayerCentral: View
    private lateinit var ivMiniPlayerArt: ImageView
    private lateinit var tvMiniPlayerTitle: TextView
    private lateinit var tvMiniPlayerSubtitle: TextView
    private lateinit var btnMiniPlayPause: ImageView
    private lateinit var sbMiniPlayerProgress: android.widget.SeekBar

    private var inSettings = false
    private var forceInitialOauthGate = false
    private var loginGateAuthInProgress = false
    
    private val authManagerLazy: AuthManager by lazy { AuthManager.getInstance(this) }

    /** Exposed for Java callers (e.g. [WeeklySchedulerFragment]). */
    fun getAuthManager(): AuthManager = authManagerLazy

    private val cloudSyncManager: CloudSyncManager by lazy { CloudSyncManager.getInstance(this) }

    private val stopMainPlayerReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.example.sleppify.ACTION_STOP_MAIN_PLAYER") {
                pauseActiveMediaAndDownloadsForSessionChange()
            }
        }
    }
    
    private var scheduleFragment: Fragment? = null
    private var musicFragment: Fragment? = null
    private var scannerFragment: Fragment? = null
    private var equalizerFragment: Fragment? = null
    private var appsFragment: Fragment? = null
    private var settingsFragment: Fragment? = null
    private var playlistDetailFragment: Fragment? = null
    private var songPlayerFragment: Fragment? = null
    private var searchFragment: Fragment? = null

    private var currentMainNavItemId = View.NO_ID
    private var lastSmartPrefetchAtMs = 0L
    private var hasAudioServiceStateSnapshot = false
    private var lastEqEnabled = false
    private var lastSpatialEnabled = false
    private var lastReverbLevel = 0
    
    private var mediaProjectionManager: MediaProjectionManager? = null
    private var mediaProjectionPermissionLauncher: ActivityResultLauncher<Intent>? = null
    private var pendingAudioProcessingAuthorization = false
    private var hasAgendaScheduleSnapshot = false
    private var lastSmartSuggestionsEnabled = false
    private var lastSummaryTimesPerDay = 0
    private var headerBrandTypeface: Typeface? = null
    private var headerSettingsTypeface: Typeface? = null

    private val backPressedCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            if (inSettings) {
                exitSettings()
                return
            }

            if (handleSongPlayerBackPressed()) return
            if (handleSearchBackPressed()) return
            if (handlePlaylistDetailBackPressed()) return

            if (shouldMoveTaskToBackForOngoingPlayback()) {
                moveTaskToBack(true)
                return
            }

            isEnabled = false
            onBackPressedDispatcher.onBackPressed()
            isEnabled = true
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyThemeModeFromSettings()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_main)
        applySystemBarsStyle()

        initViews()
        setupListeners()
        configureHeaderActionForMainModules()
        configureAudioAuthorizationFlow()
        restoreMainModuleReferences()
        
        syncAudioEffectsServiceFromPreferences(forceSync = true)
        syncDailyAgendaNotificationSchedule(forceSync = true)

        LocalBroadcastManager.getInstance(this).registerReceiver(
            stopMainPlayerReceiver, IntentFilter("com.example.sleppify.ACTION_STOP_MAIN_PLAYER")
        )

        forceInitialOauthGate = !isInitialOauthGateCompleted()
        val shouldShowLoginGate = shouldShowLoginGateAtStartup()

        if (!shouldShowLoginGate) {
            if (savedInstanceState == null) {
                bottomNav.selectedItemId = R.id.nav_schedule
            } else {
                val selectedId = bottomNav.selectedItemId
                if (!switchToMainModule(selectedId)) {
                    bottomNav.selectedItemId = R.id.nav_schedule
                }
            }
            showMainShell()
            if (authManagerLazy.isSignedIn()) {
                authManagerLazy.getCurrentUser()?.let { handleSignedInUser(it, null) }
            }
        } else {
            showLoginGate()
        }

        if (intent?.getBooleanExtra("SHOW_SETTINGS", false) == true) {
            enterSettings()
        }
        applyTargetNavIntent(intent)
        handleOpenPlayerIntent(intent)
    }

    private fun initViews() {
        val root = findViewById<View>(R.id.main)
        bottomNav = findViewById(R.id.bottomNavigation)
        tvModuleTitle = findViewById(R.id.tvModuleTitle)
        btnSettings = findViewById(R.id.btnSettings)
        topAppBar = findViewById(R.id.topAppBar)
        fragmentContainer = findViewById(R.id.fragmentContainer)
        moduleLoadingOverlay = findViewById(R.id.moduleLoadingOverlay)
        loginGateContainer = findViewById(R.id.loginGateContainer)
        tvLoginStatus = findViewById(R.id.tvLoginStatus)
        btnLoginGoogle = findViewById(R.id.btnLoginGoogle)
        
        llMiniPlayerCentral = findViewById(R.id.llMiniPlayer)
        ivMiniPlayerArt = findViewById(R.id.ivMiniPlayerArt)
        tvMiniPlayerTitle = findViewById(R.id.tvMiniPlayerTitle)
        tvMiniPlayerSubtitle = findViewById(R.id.tvMiniPlayerSubtitle)
        btnMiniPlayPause = findViewById(R.id.btnMiniPlayPause)
        sbMiniPlayerProgress = findViewById(R.id.sbMiniPlayerProgress)

        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val density = resources.displayMetrics.density
            val extraPadding = (8 * density).toInt()

            topAppBar.setPadding(topAppBar.paddingLeft, systemBars.top + extraPadding, topAppBar.paddingRight, topAppBar.paddingBottom)
            bottomNav.setPadding(systemBars.left, 0, systemBars.right, systemBars.bottom)
            insets
        }
    }

    private fun setupListeners() {
        btnLoginGoogle?.setOnClickListener { startLoginFromGate() }
        btnSettings.setOnClickListener { if (inSettings) exitSettings() else enterSettings() }
        bottomNav.setOnItemSelectedListener { item ->
            if (inSettings) exitSettings()
            switchToMainModule(item.itemId)
        }
        cloudSyncManager.setSyncStateListener { syncing -> setSyncOverlayVisible(syncing) }
        onBackPressedDispatcher.addCallback(this, backPressedCallback)
        
        llMiniPlayerCentral.setOnClickListener { openPlayerFromMiniBar() }
        btnMiniPlayPause.setOnClickListener { togglePlaybackFromMiniBar() }
        
        // Start a ticker to update progress if playing
        lifecycleScope.launch {
            while (isActive) {
                if (llMiniPlayerCentral.visibility == View.VISIBLE) {
                    refreshSharedMiniPlayerUi()
                }
                delay(1000)
            }
        }
    }

    fun refreshSharedMiniPlayerUi() {
        val snapshot = PlaybackHistoryStore.load(this)
        val current = snapshot.currentTrack()
        if (current == null) {
            llMiniPlayerCentral.visibility = View.GONE
            return
        }
        
        // Don't show mini-player if full player is visible
        val player = supportFragmentManager.findFragmentByTag(TAG_SONG_PLAYER) as? SongPlayerFragment
        if (player != null && player.isAdded && !player.isHidden) {
            llMiniPlayerCentral.visibility = View.GONE
            return
        }

        llMiniPlayerCentral.visibility = View.VISIBLE
        tvMiniPlayerTitle.text = current.title
        tvMiniPlayerSubtitle.text = current.artist
        btnMiniPlayPause.setImageResource(if (snapshot.isPlaying) R.drawable.ic_mini_pause else R.drawable.ic_mini_play)
        
        val total = snapshot.totalSeconds.coerceAtLeast(1)
        val progress = ((snapshot.currentSeconds.toFloat() / total.toFloat()) * 1000f).toInt().coerceIn(0, 1000)
        sbMiniPlayerProgress.progress = progress

        if (!isDestroyed && !isFinishing) {
            Glide.with(this)
                .load(current.imageUrl)
                .placeholder(R.drawable.ic_music)
                .error(R.drawable.ic_music)
                .into(ivMiniPlayerArt)
        }
    }

    private fun togglePlaybackFromMiniBar() {
        (supportFragmentManager.findFragmentByTag(TAG_SONG_PLAYER) as? SongPlayerFragment)?.externalTogglePlayback()
        refreshSharedMiniPlayerUi()
    }

    fun openPlayerFromMiniBar() {
        if (inSettings) exitSettings()
        
        val existing = supportFragmentManager.findFragmentByTag(TAG_SONG_PLAYER) as? SongPlayerFragment
        if (existing != null && existing.isAdded) {
            supportFragmentManager.beginTransaction()
                .show(existing)
                .commit()
            setContainerOverlayMode(true)
            llMiniPlayerCentral.visibility = View.GONE
            return
        }
        
        // Restore if possible
        handleOpenPlayerIntent(Intent().putExtra("OPEN_PLAYER", true))
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.action == ACTION_PLAY_FROM_SEARCH) {
            handlePlayFromSearchIntent(intent)
        }
        if (intent.getBooleanExtra("SHOW_SETTINGS", false)) {
            enterSettings()
        }
        applyTargetNavIntent(intent)
        handleOpenPlayerIntent(intent)
    }

    private fun applyTargetNavIntent(intent: Intent?) {
        val targetNav = intent?.getIntExtra(EXTRA_TARGET_NAV_ITEM_ID, View.NO_ID) ?: View.NO_ID
        if (targetNav != View.NO_ID && bottomNav.selectedItemId != targetNav) {
            bottomNav.selectedItemId = targetNav
        }
    }

    private fun handlePlayFromSearchIntent(intent: Intent) {
        val music = supportFragmentManager.findFragmentByTag(TAG_MODULE_MUSIC)
        if (music is MusicPlayerFragment) {
            music.playTrackFromSearch(intent)
            if (currentMainNavItemId != R.id.nav_music) {
                bottomNav.selectedItemId = R.id.nav_music
            }
        }
    }

    private fun handleOpenPlayerIntent(intent: Intent?) {
        if (intent?.getBooleanExtra("OPEN_PLAYER", false) != true) return
        // Consume the flag so it doesn't fire again on config change
        intent.removeExtra("OPEN_PLAYER")

        // Ensure we're on the music tab
        if (bottomNav.selectedItemId != R.id.nav_music) {
            bottomNav.selectedItemId = R.id.nav_music
        }
        if (inSettings) exitSettings()

        // Try to show the existing SongPlayerFragment
        val existing = supportFragmentManager.findFragmentByTag(TAG_SONG_PLAYER) as? SongPlayerFragment
        if (existing != null && existing.isAdded) {
            if (existing.isHidden) {
                supportFragmentManager.beginTransaction()
                    .setReorderingAllowed(true)
                    .show(existing)
                    .commit()
            }
            setContainerOverlayMode(true)
            return
        }

        // Restore player from PlaybackHistoryStore if there was an active session
        val snapshot = PlaybackHistoryStore.load(this)
        if (snapshot.queue.isEmpty()) return
        val ids = ArrayList<String>(snapshot.queue.size)
        val titles = ArrayList<String>(snapshot.queue.size)
        val artists = ArrayList<String>(snapshot.queue.size)
        val durations = ArrayList<String>(snapshot.queue.size)
        val images = ArrayList<String>(snapshot.queue.size)
        snapshot.queue.forEach { item ->
            ids.add(item.videoId)
            titles.add(item.title)
            artists.add(item.artist)
            durations.add(item.duration)
            images.add(item.imageUrl)
        }
        val player = SongPlayerFragment.newInstance(ids, titles, artists, durations, images, snapshot.currentIndex, snapshot.isPlaying)
        player.externalSetReturnTargetTag(TAG_MODULE_MUSIC)
        songPlayerFragment = player
        supportFragmentManager.beginTransaction()
            .setReorderingAllowed(true)
            .add(R.id.fragmentContainer, player, TAG_SONG_PLAYER)
            .commit()
        setContainerOverlayMode(true)
    }

    override fun onResume() {
        super.onResume()
        applyThemeModeFromSettings()
        applySystemBarsStyle()
        lifecycleScope.launch {
            delay(RESUME_WORK_DELAY_MS)
            runDeferredResumeWork()
        }
    }

    fun refreshSessionUi() {
        runDeferredResumeWork()
    }

    private fun runDeferredResumeWork() {
        if (isFinishing || isDestroyed || loginGateAuthInProgress) return

        cloudSyncManager.setSyncStateListener { syncing -> setSyncOverlayVisible(syncing) }
        syncAudioEffectsServiceFromPreferences(false)
        syncDailyAgendaNotificationSchedule(false)

        val signedIn = authManagerLazy.isSignedIn() && authManagerLazy.getCurrentUser() != null
        if (!signedIn) {
            showLoginGate()
            return
        }

        if (loginGateContainer?.visibility == View.VISIBLE && !signedIn) return

        showMainShell()
        if (signedIn) prefetchSmartSuggestions(false)

        // EQ verification: re-apply if enabled but potentially dead after background kill
        val audioPrefs = getSharedPreferences(AudioEffectsService.PREFS_NAME, MODE_PRIVATE)
        if (audioPrefs.getBoolean(AudioEffectsService.KEY_ENABLED, false)) {
            AudioEffectsService.sendApply(applicationContext)
        }
    }

    private fun applyThemeModeFromSettings() {
        val settingsPrefs = getSharedPreferences(CloudSyncManager.PREFS_SETTINGS, MODE_PRIVATE)
        val amoledEnabled = settingsPrefs.getBoolean(CloudSyncManager.KEY_AMOLED_MODE_ENABLED, false)
        val targetMode = if (amoledEnabled) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
        if (AppCompatDelegate.getDefaultNightMode() != targetMode) {
            AppCompatDelegate.setDefaultNightMode(targetMode)
        }
    }

    private fun applySystemBarsStyle() {
        val window = window
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isStatusBarContrastEnforced = false
            window.isNavigationBarContrastEnforced = false
        }
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
    }

    override fun onDestroy() {
        cloudSyncManager.setSyncStateListener(null)
        LocalBroadcastManager.getInstance(this).unregisterReceiver(stopMainPlayerReceiver)
        super.onDestroy()
    }

    private fun shouldShowLoginGateAtStartup() = forceInitialOauthGate || !(authManagerLazy.isSignedIn() && authManagerLazy.getCurrentUser() != null)

    private fun isInitialOauthGateCompleted() = getSharedPreferences(PREFS_BOOTSTRAP, MODE_PRIVATE).getBoolean(KEY_INITIAL_OAUTH_GATE_COMPLETED, false)

    private fun markInitialOauthGateCompleted() {
        forceInitialOauthGate = false
        getSharedPreferences(PREFS_BOOTSTRAP, MODE_PRIVATE).edit().putBoolean(KEY_INITIAL_OAUTH_GATE_COMPLETED, true).apply()
    }

    fun requestAudioEffectsApplyFromUi() = syncAudioEffectsServiceFromPreferences(true, true)

    fun requestAudioEffectsStopFromUi() {
        hasAudioServiceStateSnapshot = false
        AudioEffectsService.sendStop(applicationContext)
    }

    private fun syncAudioEffectsServiceFromPreferences(forceSync: Boolean, allowAuthorizationPrompt: Boolean = false) {
        val audioPrefs = getSharedPreferences(AudioEffectsService.PREFS_NAME, MODE_PRIVATE)
        val eqEnabled = audioPrefs.getBoolean(AudioEffectsService.KEY_ENABLED, false)
        val spatialEnabled = audioPrefs.getBoolean(AudioEffectsService.KEY_SPATIAL_ENABLED, false)
        val reverbLevel = audioPrefs.getInt(AudioEffectsService.KEY_REVERB_LEVEL, AudioEffectsService.REVERB_LEVEL_OFF)

        if (!forceSync && hasAudioServiceStateSnapshot && eqEnabled == lastEqEnabled && spatialEnabled == lastSpatialEnabled && reverbLevel == lastReverbLevel) {
            return
        }

        hasAudioServiceStateSnapshot = true
        lastEqEnabled = eqEnabled
        lastSpatialEnabled = spatialEnabled
        lastReverbLevel = reverbLevel

        if (eqEnabled) AudioEffectsService.sendApply(applicationContext) else AudioEffectsService.sendStop(applicationContext)
    }

    private fun configureAudioAuthorizationFlow() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            mediaProjectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            mediaProjectionPermissionLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                onMediaProjectionPermissionResult(result.resultCode, result.data)
            }
        }
    }

    private fun onMediaProjectionPermissionResult(resultCode: Int, data: Intent?) {
        pendingAudioProcessingAuthorization = false
        if (resultCode == Activity.RESULT_OK && data != null) {
            (application as? SleppifyApp)?.setMediaProjectionPermissionData(data)
            syncAudioEffectsServiceFromPreferences(true)
        } else {
            AudioEffectsService.sendStop(applicationContext)
            Toast.makeText(this, "Permiso denegado para el EQ system-wide.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_RECORD_AUDIO) {
            val granted = grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
            if (granted) {
                if (pendingAudioProcessingAuthorization && ensureAudioProcessingAuthorization(true)) {
                    pendingAudioProcessingAuthorization = false
                    syncAudioEffectsServiceFromPreferences(true)
                }
            } else {
                pendingAudioProcessingAuthorization = false
                AudioEffectsService.sendStop(applicationContext)
                Toast.makeText(this, "Se requiere permiso de micrófono.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun ensureAudioProcessingAuthorization(allowPrompt: Boolean): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return true

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            if (allowPrompt) {
                pendingAudioProcessingAuthorization = true
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_CODE_RECORD_AUDIO)
            }
            return false
        }

        if ((application as? SleppifyApp)?.hasMediaProjectionPermissionData() == true) return true

        if (allowPrompt) {
            mediaProjectionManager?.let { mgr ->
                pendingAudioProcessingAuthorization = true
                mediaProjectionPermissionLauncher?.launch(mgr.createScreenCaptureIntent())
            }
        }
        return false
    }

    fun requireAuth(onSuccess: Runnable? = null, onError: Runnable? = null) {
        if (authManagerLazy.isSignedIn()) {
            authManagerLazy.getCurrentUser()?.let {
                if (loginGateAuthInProgress) setLoginGateButtonState(true, "Sincronizando...")
                handleSignedInUser(it, onSuccess)
                return
            }
        }

        authManagerLazy.signIn(this, object : AuthManager.AuthCallback {
            override fun onSuccess(user: FirebaseUser) {
                setLoginGateButtonState(true, "Sincronizando...")
                handleSignedInUser(user, onSuccess)
            }
            override fun onError(message: String) {
                onError?.run()
            }
        })
    }

    private fun startLoginFromGate() {
        if (loginGateAuthInProgress) return
        setLoginGateButtonState(true, "Conectando...")
        requireAuth(
            onSuccess = {
                setLoginGateButtonState(false, "Sign in with Google")
                showMainShell()
                bottomNav.selectedItemId = R.id.nav_music
                switchToMainModule(R.id.nav_music)
            },
            onError = { setLoginGateButtonState(false, "Sign in with Google") }
        )
    }

    private fun setLoginGateButtonState(loading: Boolean, buttonText: String) {
        loginGateAuthInProgress = loading
        btnLoginGoogle?.apply {
            isEnabled = !loading
            alpha = if (loading) 0.56f else 1f
            text = buttonText
        }
    }

    private fun showLoginGate() {
        loginGateContainer?.visibility = View.VISIBLE
        topAppBar.visibility = View.GONE
        fragmentContainer.visibility = View.GONE
        bottomNav.visibility = View.GONE
        tvLoginStatus?.text = if (forceInitialOauthGate) "Inicia sesión para la configuración." else "Inicia sesión para sincronizar."
        if (!loginGateAuthInProgress) setLoginGateButtonState(false, "Sign in with Google")
    }

    private fun showMainShell() {
        loginGateContainer?.visibility = View.GONE
        topAppBar.visibility = View.VISIBLE
        fragmentContainer.visibility = View.VISIBLE
        bottomNav.visibility = if (inSettings) View.GONE else View.VISIBLE
    }

    private fun handleSignedInUser(user: FirebaseUser, onSuccess: Runnable?) {
        cloudSyncManager.onUserSignedIn(user.uid) { ok, message ->
            if (!ok) {
                tvLoginStatus?.text = if (TextUtils.isEmpty(message)) "Error de sincronización." else message
                setLoginGateButtonState(false, "Sign in with Google")
                return@onUserSignedIn
            }
            markInitialOauthGateCompleted()
            applyHydratedUserState(onSuccess)
        }
    }

    private fun applyHydratedUserState(onSuccess: Runnable?) {
        tvLoginStatus?.text = "Sincronizando ajustes..."
        val previousNightMode = AppCompatDelegate.getDefaultNightMode()
        applyThemeModeFromSettings()
        val amoledChanged = previousNightMode != AppCompatDelegate.getDefaultNightMode()

        if (amoledChanged) tvLoginStatus?.text = "Aplicando modo AMOLED..."

        val finalize = {
            if (!isFinishing && !isDestroyed) {
                notifyHydrationCompleted()
                syncAudioEffectsServiceFromPreferences(true)
                syncDailyAgendaNotificationSchedule(true)
                prefetchSmartSuggestions(true)
                onSuccess?.run()
            }
        }

        if (amoledChanged) {
            lifecycleScope.launch { delay(700L); finalize() }
        } else {
            finalize()
        }
    }

    private fun notifyHydrationCompleted() {
        (getMainModuleFragment(R.id.nav_equalizer) as? EqualizerFragment)?.onCloudEqHydrationCompleted()
        (getMainModuleFragment(R.id.nav_schedule) as? WeeklySchedulerFragment)?.onCloudAgendaHydrationCompleted()
    }

    private fun setSyncOverlayVisible(visible: Boolean) { /* Signals visual sync in header */ }

    private fun configureHeaderActionForMainModules() {
        btnSettings.visibility = View.VISIBLE
        btnSettings.setImageResource(R.drawable.ic_settings)
        btnSettings.contentDescription = getString(R.string.header_action_settings)
        
        tvModuleTitle.apply {
            text = getString(R.string.header_brand_title)
            isAllCaps = true
            letterSpacing = 0.08f
            typeface = resolveHeaderBrandTypeface()
            
            val density = resources.displayMetrics.density
            val iconSize = (26 * density).toInt()
            ContextCompat.getDrawable(this@MainActivity, R.mipmap.ic_launcher)?.apply {
                setBounds(0, 0, iconSize, iconSize)
                setCompoundDrawablesRelative(this, null, null, null)
            }
            compoundDrawablePadding = (8 * density).toInt()
            setOnClickListener { goToAgendaFromHeader() }
        }
    }

    private fun configureHeaderActionForSettings() {
        btnSettings.visibility = View.GONE
        tvModuleTitle.apply {
            text = getString(R.string.header_title_settings)
            isAllCaps = false
            letterSpacing = 0f
            typeface = resolveHeaderSettingsTypeface()
            setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_arrow_back, 0, 0, 0)
            compoundDrawablePadding = (10 * resources.displayMetrics.density).toInt()
            setOnClickListener { exitSettings() }
        }
    }

    private fun resolveHeaderBrandTypeface() = headerBrandTypeface ?: ResourcesCompat.getFont(this, R.font.manrope_variable).also { headerBrandTypeface = it } ?: Typeface.DEFAULT_BOLD

    private fun resolveHeaderSettingsTypeface() = headerSettingsTypeface ?: ResourcesCompat.getFont(this, R.font.inter_variable).also { headerSettingsTypeface = it } ?: Typeface.DEFAULT_BOLD

    private fun goToAgendaFromHeader() {
        if (inSettings) exitSettings()
        if (bottomNav.selectedItemId != R.id.nav_schedule) {
            bottomNav.selectedItemId = R.id.nav_schedule
        } else {
            switchToMainModule(R.id.nav_schedule)
        }
    }

    private fun enterSettings() {
        if (inSettings) return
        if (bottomNav.selectedItemId == R.id.nav_music) markStreamingEntryAsLibrary()

        inSettings = true
        showModuleLoadingOverlay()
        // fragmentContainer.alpha = 0f  // REMOVED: Instant transition

        val target = settingsFragment ?: SettingsFragment().also { settingsFragment = it }
        val isNew = !target.isAdded
        
        supportFragmentManager.beginTransaction().apply {
            setReorderingAllowed(true)
            playlistDetailFragment = supportFragmentManager.findFragmentByTag(TAG_PLAYLIST_DETAIL)
            songPlayerFragment = supportFragmentManager.findFragmentByTag(TAG_SONG_PLAYER)
            val current = getMainModuleFragment(currentMainNavItemId)
            
            hideIfVisible(this, current, target)
            hideIfVisible(this, playlistDetailFragment, target)
            hideIfVisible(this, songPlayerFragment, target)
            
            if (target.isAdded) show(target) else add(R.id.fragmentContainer, target, TAG_MODULE_SETTINGS)
            setMaxLifecycle(target, Lifecycle.State.RESUMED)
            commit()
        }

        configureHeaderActionForSettings()
        bottomNav.visibility = View.GONE
        revealModuleContent()
    }

    private fun exitSettings() {
        if (!inSettings) return
        inSettings = false
        showModuleLoadingOverlay()
        // fragmentContainer.alpha = 0f // REMOVED: Instant transition

        val selectedId = bottomNav.selectedItemId
        val target = getMainModuleFragment(selectedId) ?: getOrCreateMainModuleFragment(selectedId)
        val isNew = target?.isAdded == false

        supportFragmentManager.beginTransaction().apply {
            setReorderingAllowed(true)
            settingsFragment?.let { if (it.isAdded) { hide(it); setMaxLifecycle(it, Lifecycle.State.STARTED) } }
            target?.let { 
                if (it.isAdded) show(it) else moduleTagForItem(selectedId)?.let { tag -> add(R.id.fragmentContainer, it, tag) }
                setMaxLifecycle(it, Lifecycle.State.RESUMED)
            }
            commit()
        }

        configureHeaderActionForMainModules()
        bottomNav.visibility = View.VISIBLE
        updateHeaderTitleForModule(selectedId)
        revealModuleContent()
    }

    private fun prefetchSmartSuggestions(force: Boolean) {
        val now = System.currentTimeMillis()
        if (!force && (now - lastSmartPrefetchAtMs) < PREFETCH_MIN_INTERVAL_MS) return
        
        lifecycleScope.launch {
            delay(if (force) 200L else PREFETCH_DEBOUNCE_MS)
            runSmartPrefetch()
        }
    }

    private fun runSmartPrefetch() {
        if (isFinishing || isDestroyed) return
        lastSmartPrefetchAtMs = System.currentTimeMillis()
        val name = if (authManagerLazy.isSignedIn()) authManagerLazy.getDisplayName() else null
        SmartSuggestionPrefetcher.prefetchScheduleSuggestion(this, name)
    }

    private fun syncDailyAgendaNotificationSchedule(forceSync: Boolean) {
        val settingsPrefs = getSharedPreferences(CloudSyncManager.PREFS_SETTINGS, MODE_PRIVATE)
        val enabled = settingsPrefs.getBoolean(CloudSyncManager.KEY_SMART_SUGGESTIONS_ENABLED, settingsPrefs.getBoolean(CloudSyncManager.KEY_AI_SHIFT_ENABLED, true))
        val times = settingsPrefs.getInt(CloudSyncManager.KEY_DAILY_SUMMARY_INTERVAL_HOURS, 2)

        if (!forceSync && hasAgendaScheduleSnapshot && lastSmartSuggestionsEnabled == enabled && lastSummaryTimesPerDay == times) return

        hasAgendaScheduleSnapshot = true
        lastSmartSuggestionsEnabled = enabled
        lastSummaryTimesPerDay = times

        if (!enabled) {
            DailyAgendaNotificationWorker.cancel(applicationContext)
            TaskReminderScheduler.cancelAll(applicationContext)
        } else {
            DailyAgendaNotificationWorker.schedule(applicationContext, times)
            TaskReminderScheduler.rescheduleAll(applicationContext)
        }
    }

    private fun restoreMainModuleReferences() {
        scheduleFragment = supportFragmentManager.findFragmentByTag(TAG_MODULE_SCHEDULE)
        musicFragment = supportFragmentManager.findFragmentByTag(TAG_MODULE_MUSIC)
        scannerFragment = supportFragmentManager.findFragmentByTag(TAG_MODULE_SCANNER)
        equalizerFragment = supportFragmentManager.findFragmentByTag(TAG_MODULE_EQUALIZER)
        appsFragment = supportFragmentManager.findFragmentByTag(TAG_MODULE_APPS)
        settingsFragment = supportFragmentManager.findFragmentByTag(TAG_MODULE_SETTINGS)
        playlistDetailFragment = supportFragmentManager.findFragmentByTag(TAG_PLAYLIST_DETAIL)
        songPlayerFragment = supportFragmentManager.findFragmentByTag(TAG_SONG_PLAYER)
        searchFragment = supportFragmentManager.findFragmentByTag(TAG_SEARCH)
    }

    private fun getOrCreateMainModuleFragment(itemId: Int): Fragment? {
        getMainModuleFragment(itemId)?.let { return it }
        return when (itemId) {
            R.id.nav_schedule -> WeeklySchedulerFragment().also { scheduleFragment = it }
            R.id.nav_music -> MusicPlayerFragment().also { musicFragment = it }
            R.id.nav_scanner -> ScannerFragment().also { scannerFragment = it }
            R.id.nav_equalizer -> EqualizerFragment().also { equalizerFragment = it }
            R.id.nav_apps -> AppsFragment().also { appsFragment = it }
            else -> null
        }
    }

    private fun getMainModuleFragment(itemId: Int): Fragment? = when (itemId) {
        R.id.nav_schedule -> scheduleFragment
        R.id.nav_music -> musicFragment
        R.id.nav_scanner -> scannerFragment
        R.id.nav_equalizer -> equalizerFragment
        R.id.nav_apps -> appsFragment
        else -> null
    }

    private fun moduleTagForItem(itemId: Int) = when (itemId) {
        R.id.nav_schedule -> TAG_MODULE_SCHEDULE
        R.id.nav_music -> TAG_MODULE_MUSIC
        R.id.nav_scanner -> TAG_MODULE_SCANNER
        R.id.nav_equalizer -> TAG_MODULE_EQUALIZER
        R.id.nav_apps -> TAG_MODULE_APPS
        else -> null
    }

    private fun hideIfVisible(transaction: FragmentTransaction, fragment: Fragment?, target: Fragment) {
        if (fragment == null || fragment == target || !fragment.isAdded || fragment.isHidden) return
        transaction.hide(fragment)
        if (fragment.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            transaction.setMaxLifecycle(fragment, Lifecycle.State.STARTED)
        }
    }

    private fun switchToMainModule(itemId: Int): Boolean {
        supportFragmentManager.executePendingTransactions()
        setContainerOverlayMode(false)
        val tag = moduleTagForItem(itemId) ?: return false
        val target = supportFragmentManager.findFragmentByTag(tag) ?: getOrCreateMainModuleFragment(itemId) ?: return false

        if (currentMainNavItemId == R.id.nav_music && itemId != R.id.nav_music) markStreamingEntryAsLibrary()
        if (currentMainNavItemId == itemId && target.isAdded && !inSettings) {
            updateHeaderTitleForModule(itemId)
            return true
        }

        val isNew = !target.isAdded
        showModuleLoadingOverlay()
        // fragmentContainer.alpha = 0f // REMOVED: Instant transition

        supportFragmentManager.beginTransaction().apply {
            setReorderingAllowed(true)
            playlistDetailFragment = supportFragmentManager.findFragmentByTag(TAG_PLAYLIST_DETAIL)
            songPlayerFragment = supportFragmentManager.findFragmentByTag(TAG_SONG_PLAYER)
            val current = getMainModuleFragment(currentMainNavItemId)
            
            hideIfVisible(this, current, target)
            hideIfVisible(this, playlistDetailFragment, target)
            hideIfVisible(this, songPlayerFragment, target)
            hideIfVisible(this, settingsFragment, target)
            
            if (target.isAdded) show(target) else add(R.id.fragmentContainer, target, tag)
            setMaxLifecycle(target, Lifecycle.State.RESUMED)
            commit()
        }

        currentMainNavItemId = itemId
        if (itemId == R.id.nav_music) markStreamingEntryAsLibrary()
        updateHeaderTitleForModule(itemId)

        revealModuleContent()
        return true
    }

    fun showModuleLoadingOverlay() {
        moduleLoadingOverlay.alpha = 1f
        moduleLoadingOverlay.visibility = View.VISIBLE
    }

    fun revealModuleContent() {
        if (isFinishing || isDestroyed) return
        fragmentContainer.alpha = 1f
        moduleLoadingOverlay.alpha = 0f
        moduleLoadingOverlay.visibility = View.GONE
    }

    private fun markStreamingEntryAsLibrary() {
        persistStreamingScreenState(STREAM_SCREEN_LIBRARY)
        (supportFragmentManager.findFragmentByTag(TAG_SONG_PLAYER) as? SongPlayerFragment)?.externalSetReturnTargetTag(TAG_MODULE_MUSIC)
    }

    private fun persistStreamingScreenState(screen: String) {
        getSharedPreferences(PREFS_PLAYER_STATE, MODE_PRIVATE).edit().putString(PREF_LAST_STREAM_SCREEN, screen).apply()
    }

    private fun snapshotStreamingScreenBeforeNavigation() {
        supportFragmentManager.findFragmentByTag(TAG_SONG_PLAYER)?.let { fragment ->
            if (fragment is SongPlayerFragment && fragment.isAdded && !fragment.isHidden) {
                fragment.externalSnapshotForNavigation()
                val target = if (fragment.externalGetReturnTargetTag() == TAG_PLAYLIST_DETAIL) STREAM_SCREEN_PLAYLIST_DETAIL else STREAM_SCREEN_LIBRARY
                persistStreamingScreenState(target)
                return
            }
        }
        val isDetailVisible = supportFragmentManager.findFragmentByTag(TAG_PLAYLIST_DETAIL).let { it != null && it.isAdded && !it.isHidden }
        persistStreamingScreenState(if (isDetailVisible) STREAM_SCREEN_PLAYLIST_DETAIL else STREAM_SCREEN_LIBRARY)
    }

    private fun handleSongPlayerBackPressed(): Boolean {
        val player = supportFragmentManager.findFragmentByTag(TAG_SONG_PLAYER) as? SongPlayerFragment ?: return false
        if (!player.isAdded || player.isHidden) return false
        if (player.externalTryEnterMiniMode()) return true

        showModuleLoadingOverlay()
        fragmentContainer.alpha = 0f
        
        lifecycleScope.launch {
            snapshotStreamingScreenBeforeNavigation()
            val fallback = resolveSongPlayerReturnTarget(player.externalGetReturnTargetTag())
            
            // UI should be updated AFTER removing/hiding the player
            llMiniPlayerCentral.visibility = View.VISIBLE
            refreshSharedMiniPlayerUi()
            
            supportFragmentManager.beginTransaction().apply {
                setReorderingAllowed(true)
                if (fallback != null && fallback.isAdded && fallback != player) {
                    hide(player).show(fallback)
                } else {
                    remove(player)
                }
                commit()
            }
            revealModuleContent()
        }
        return true
    }

    private fun resolveSongPlayerReturnTarget(preferredTag: String?): Fragment? {
        if (!preferredTag.isNullOrEmpty()) {
            supportFragmentManager.findFragmentByTag(preferredTag)?.let { if (it.isAdded) return it }
        }
        supportFragmentManager.findFragmentByTag(TAG_PLAYLIST_DETAIL)?.let { if (it.isAdded) return it }
        supportFragmentManager.findFragmentByTag(TAG_MODULE_MUSIC)?.let { if (it.isAdded) return it }
        return getMainModuleFragment(currentMainNavItemId)?.takeIf { it.isAdded }
    }

    private fun handlePlaylistDetailBackPressed(): Boolean {
        val detail = supportFragmentManager.findFragmentByTag(TAG_PLAYLIST_DETAIL) ?: return false
        if (!detail.isAdded || detail.isHidden) return false

        showModuleLoadingOverlay()
        fragmentContainer.alpha = 0f
        lifecycleScope.launch {
            markStreamingEntryAsLibrary()
            supportFragmentManager.popBackStackImmediate(TAG_PLAYLIST_DETAIL, androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE)
            if (bottomNav.selectedItemId != R.id.nav_music) bottomNav.selectedItemId = R.id.nav_music
            currentMainNavItemId = R.id.nav_music
            updateHeaderTitleForModule(R.id.nav_music)
            revealModuleContent()
        }
        return true
    }

    private fun updateHeaderTitleForModule(itemId: Int) {
        tvModuleTitle.text = if (inSettings) getString(R.string.header_title_settings) else getString(R.string.header_brand_title)
    }

    fun setContainerOverlayMode(enabled: Boolean) {
        (fragmentContainer.layoutParams as? ConstraintLayout.LayoutParams)?.apply {
            if (enabled) {
                topToTop = ConstraintLayout.LayoutParams.PARENT_ID
                topToBottom = ConstraintLayout.LayoutParams.UNSET
            } else {
                topToTop = ConstraintLayout.LayoutParams.UNSET
                topToBottom = R.id.topAppBar
            }
            fragmentContainer.layoutParams = this
            (fragmentContainer.parent as? View)?.requestLayout()
        }
        topAppBar.translationZ = if (enabled) 24f * resources.displayMetrics.density else 0f
        topAppBar.bringToFront()
    }

    fun pauseActiveMediaAndDownloadsForSessionChange() {
        // 1. Halt background work like downloads
        cloudSyncManager.pauseUserScopedBackgroundWork()
        
        // 2. Stop music playback if the player is active
        (supportFragmentManager.findFragmentByTag(TAG_SONG_PLAYER) as? SongPlayerFragment)?.externalPauseForSessionExit()
    }

    private fun shouldMoveTaskToBackForOngoingPlayback(): Boolean {
        val snapshot = PlaybackHistoryStore.load(this)
        return snapshot.isPlaying && snapshot.queue.isNotEmpty()
    }

    fun playQueue(ids: ArrayList<String>, titles: ArrayList<String>, artists: ArrayList<String>, durations: ArrayList<String>, images: ArrayList<String>, startIndex: Int) {
        // 1. Remove existing player if any to ensure fresh state
        val existing = supportFragmentManager.findFragmentByTag(TAG_SONG_PLAYER)
        if (existing != null) {
            supportFragmentManager.beginTransaction().remove(existing).commitNow()
        }
        
        // 2. Create new player
        val player = SongPlayerFragment.newInstance(ids, titles, artists, durations, images, startIndex, true)
        player.externalSetReturnTargetTag(currentMainNavItemId.toString())
        songPlayerFragment = player
        
        // 3. Show player instantly
        supportFragmentManager.beginTransaction()
            .setReorderingAllowed(true)
            .add(R.id.fragmentContainer, player, TAG_SONG_PLAYER)
            .commit()
        
        setContainerOverlayMode(true)
        llMiniPlayerCentral.visibility = View.GONE
    }

    fun switchToSearchFragment() {
        if (inSettings) exitSettings()
        showModuleLoadingOverlay()
        
        val target = searchFragment ?: SearchFragment().also { searchFragment = it }
        supportFragmentManager.beginTransaction()
            .setReorderingAllowed(true)
            .hideIfVisible(supportFragmentManager.findFragmentByTag(moduleTagForItem(currentMainNavItemId) ?: ""))
            .hideIfVisible(playlistDetailFragment)
            .hideIfVisible(songPlayerFragment)
            .addOrShow(target, TAG_SEARCH)
            .commit()
            
        revealModuleContent()
    }

    private fun FragmentTransaction.hideIfVisible(fragment: Fragment?): FragmentTransaction {
        if (fragment != null && fragment.isAdded && !fragment.isHidden) {
            hide(fragment)
            setMaxLifecycle(fragment, androidx.lifecycle.Lifecycle.State.STARTED)
        }
        return this
    }

    private fun FragmentTransaction.addOrShow(fragment: Fragment, tag: String): FragmentTransaction {
        if (fragment.isAdded) show(fragment) else add(R.id.fragmentContainer, fragment, tag)
        setMaxLifecycle(fragment, androidx.lifecycle.Lifecycle.State.RESUMED)
        return this
    }

    private fun handleSearchBackPressed(): Boolean {
        val search = supportFragmentManager.findFragmentByTag(TAG_SEARCH) ?: return false
        if (!search.isAdded || search.isHidden) return false
        
        showModuleLoadingOverlay()
        supportFragmentManager.beginTransaction()
            .remove(search)
            .show(getOrCreateMainModuleFragment(currentMainNavItemId)!!)
            .commit()
        revealModuleContent()
        return true
    }
}
