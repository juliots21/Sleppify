package com.example.sleppify

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.text.TextUtils
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
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

        private const val PREFS_PLAYER_STATE = "player_state"
        private const val PREFS_BOOTSTRAP = "sleppify_bootstrap"
        private const val KEY_INITIAL_OAUTH_GATE_COMPLETED = "initial_oauth_gate_completed"
        private const val PREF_LAST_STREAM_SCREEN = "stream_last_screen"
        private const val PREF_LAST_MAIN_MODULE = "last_main_module"
        private const val STREAM_SCREEN_LIBRARY = "library"
        private const val STREAM_SCREEN_PLAYLIST_DETAIL = "playlist_detail"

        private const val RESUME_WORK_DELAY_MS = 180L
        private const val PREFETCH_DEBOUNCE_MS = 800L
        private const val PREFETCH_MIN_INTERVAL_MS = 3 * 60 * 60 * 1000L // 3 hours
        private const val MODULE_LOAD_OVERLAY_MIN_MS = 120L
        private const val MODULE_CONTENT_FADE_IN_MS = 280L
        
        const val ACTION_PLAY_FROM_SEARCH = "com.example.sleppify.ACTION_PLAY_FROM_SEARCH"
        const val ACTION_PLAY_NEXT = "com.example.sleppify.ACTION_PLAY_NEXT"
        const val ACTION_ADD_TO_QUEUE = "com.example.sleppify.ACTION_ADD_TO_QUEUE"
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

    private var inSettings = false
    private var forceInitialOauthGate = false
    private var loginGateAuthInProgress = false
    
    private val authManagerLazy: AuthManager by lazy { AuthManager.getInstance(this) }

    /** Exposed for Java callers (e.g. [WeeklySchedulerFragment]). */
    fun getAuthManager(): AuthManager = authManagerLazy

    private val cloudSyncManager: CloudSyncManager by lazy { CloudSyncManager.getInstance(this) }
    
    private var scheduleFragment: Fragment? = null
    private var musicFragment: Fragment? = null
    private var scannerFragment: Fragment? = null
    private var equalizerFragment: Fragment? = null
    private var appsFragment: Fragment? = null
    private var settingsFragment: Fragment? = null
    private var playlistDetailFragment: Fragment? = null
    private var songPlayerFragment: Fragment? = null

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
    private var audioManager: AudioManager? = null

    private val outputDeviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<AudioDeviceInfo>?) {
            syncAudioProfile(true)
        }
        override fun onAudioDevicesRemoved(removedDevices: Array<AudioDeviceInfo>?) {
            syncAudioProfile(true)
        }
    }

    private val backPressedCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            if (inSettings) {
                exitSettings()
                return
            }

            if (handleSongPlayerBackPressed()) return
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
        audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        syncAudioProfile(false) // Initial sync on startup
        // Cargar cookies de la sesión web YouTube Music (si existen) lo más
        // temprano posible para que las primeras peticiones InnerTube ya estén
        // autenticadas y eviten LOGIN_REQUIRED / 403 en el CDN.
        InnertubeResolver.loadAuthCookiesFromPrefs(this)
        setupListeners()
        configureHeaderActionForMainModules()
        configureAudioAuthorizationFlow()
        restoreMainModuleReferences()

        forceInitialOauthGate = !isInitialOauthGateCompleted()
        val shouldShowLoginGate = shouldShowLoginGateAtStartup()

        if (!shouldShowLoginGate) {
            val prefs = getSharedPreferences(PREFS_BOOTSTRAP, Context.MODE_PRIVATE)
            val lastModuleId = prefs.getInt(PREF_LAST_MAIN_MODULE, R.id.nav_music)

            if (savedInstanceState == null) {
                // Siempre iniciar con music como módulo por defecto
                val targetModuleId = R.id.nav_music
                bottomNav.selectedItemId = targetModuleId
                currentMainNavItemId = targetModuleId
                switchToMainModule(targetModuleId)
            } else {
                val selectedId = bottomNav.selectedItemId
                currentMainNavItemId = selectedId
                if (!switchToMainModule(selectedId)) {
                    // Fallback a music si hay error
                    bottomNav.selectedItemId = R.id.nav_music
                    currentMainNavItemId = R.id.nav_music
                    switchToMainModule(R.id.nav_music)
                }
            }
            showMainShell()
        } else {
            showLoginGate()
        }

        // Defer heavy startup work to background thread
        lifecycleScope.launch {
            delay(100) // Allow UI to render first
            withContext(Dispatchers.IO) {
                syncAudioEffectsServiceFromPreferences(forceSync = true)
                syncDailyAgendaNotificationSchedule(forceSync = true)
            }
            // Handle signed in user async to avoid blocking UI
            if (!shouldShowLoginGate && authManagerLazy.isSignedIn()) {
                authManagerLazy.getCurrentUser()?.let { handleSignedInUser(it, null) }
            }
        }

        if (intent?.getBooleanExtra("SHOW_SETTINGS", false) == true) {
            enterSettings()
        }
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

        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val density = resources.displayMetrics.density
            val extraPadding = (8 * density).toInt()

            topAppBar.setPadding(topAppBar.paddingLeft, systemBars.top + extraPadding, topAppBar.paddingRight, topAppBar.paddingBottom)
            bottomNav.setPadding(systemBars.left, 0, systemBars.right, systemBars.bottom)
            
            val playerContainer = findViewById<View>(R.id.playerContainer)
            playerContainer?.setPadding(0, 0, 0, systemBars.bottom)
            
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
        cloudSyncManager.setSyncStateListener(this::setSyncOverlayVisible)
        onBackPressedDispatcher.addCallback(this, backPressedCallback)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.action == ACTION_PLAY_FROM_SEARCH || intent.action == ACTION_PLAY_NEXT || intent.action == ACTION_ADD_TO_QUEUE) {
            handlePlayFromSearchIntent(intent)
        }
        if (intent.getBooleanExtra("SHOW_SETTINGS", false)) {
            enterSettings()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("current_main_nav_item_id", currentMainNavItemId)
        outState.putBoolean("in_settings", inSettings)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        val savedNavId = savedInstanceState.getInt("current_main_nav_item_id", View.NO_ID)
        if (savedNavId != View.NO_ID) {
            currentMainNavItemId = savedNavId
        }
        inSettings = savedInstanceState.getBoolean("in_settings", false)
    }

    private fun handlePlayFromSearchIntent(intent: Intent) {
        val music = supportFragmentManager.findFragmentByTag(TAG_MODULE_MUSIC)
        if (music != null && music.javaClass.simpleName == "MusicPlayerFragment") {
            try {
                val methodName = when (intent.action) {
                    ACTION_PLAY_NEXT -> "playNextFromSearch"
                    ACTION_ADD_TO_QUEUE -> "addToQueueFromSearch"
                    else -> "playTrackFromSearch"
                }
                val method = music.javaClass.getMethod(methodName, Intent::class.java)
                method.invoke(music, intent)
                if (currentMainNavItemId != R.id.nav_music) {
                    bottomNav.selectedItemId = R.id.nav_music
                }
            } catch (e: Exception) {
                // Method not found or invocation failed
            }
        }
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

    override fun onStart() {
        super.onStart()
        runCatching { audioManager?.registerAudioDeviceCallback(outputDeviceCallback, null) }
    }

    override fun onStop() {
        runCatching { audioManager?.unregisterAudioDeviceCallback(outputDeviceCallback) }
        super.onStop()
    }

    fun refreshSessionUi() {
        runDeferredResumeWork()
    }

    private fun runDeferredResumeWork() {
        if (isFinishing || isDestroyed || loginGateAuthInProgress) return

        cloudSyncManager.setSyncStateListener(this::setSyncOverlayVisible)
        syncAudioEffectsServiceFromPreferences(false)
        syncDailyAgendaNotificationSchedule(false)

        val signedIn = authManagerLazy.isSignedIn() && authManagerLazy.getCurrentUser() != null
        if (!signedIn) {
            showLoginGate()
            return
        }

        if (loginGateContainer?.visibility == View.VISIBLE && !signedIn) return

        showMainShell()
        // AI prefetch deliberately disabled here: the only allowed triggers are
        // pull-to-refresh in the agenda and task creation (see WeeklySchedulerFragment).
    }

    private fun applyThemeModeFromSettings() {
        val settingsPrefs = getSharedPreferences(CloudSyncManager.PREFS_SETTINGS, MODE_PRIVATE)
        val amoledEnabled = settingsPrefs.getBoolean(CloudSyncManager.KEY_AMOLED_MODE_ENABLED, false)
        val targetMode = if (amoledEnabled) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
        if (AppCompatDelegate.getDefaultNightMode() != targetMode) {
            AppCompatDelegate.setDefaultNightMode(targetMode)
        }
    }

    @Suppress("DEPRECATION")
    private fun applySystemBarsStyle() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.statusBarColor = Color.TRANSPARENT
            window.navigationBarColor = Color.TRANSPARENT
        }
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

    private fun syncAudioProfile(applyIfChanged: Boolean) {
        val manager = audioManager ?: return
        val audioPrefs = getSharedPreferences(AudioEffectsService.PREFS_NAME, MODE_PRIVATE)
        val selected = AudioDeviceProfileStore.selectPreferredOutput(manager)

        val profileSwitched = AudioDeviceProfileStore.syncActiveProfileForOutput(audioPrefs, selected)

        if (profileSwitched && applyIfChanged) {
            syncAudioEffectsServiceFromPreferences(forceSync = true)
            // Notify EqualizerFragment if it's active
            (getMainModuleFragment(R.id.nav_equalizer) as? EqualizerFragment)?.onOutputProfileSwitchedLocally()
        }
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
                // AI prefetch intentionally removed here to avoid burning quota on sync.
                onSuccess?.run()
            }
        }

        if (amoledChanged) {
            lifecycleScope.launch { delay(120L); finalize() }
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
        fragmentContainer.alpha = 0f

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
        lifecycleScope.launch {
            delay(if (isNew) MODULE_LOAD_OVERLAY_MIN_MS + 80 else MODULE_LOAD_OVERLAY_MIN_MS)
            revealModuleContent()
        }
    }

    private fun exitSettings() {
        if (!inSettings) return
        inSettings = false
        showModuleLoadingOverlay()
        fragmentContainer.alpha = 0f

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
        lifecycleScope.launch {
            delay(if (isNew) MODULE_LOAD_OVERLAY_MIN_MS + 80 else MODULE_LOAD_OVERLAY_MIN_MS)
            revealModuleContent()
        }
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
    }

    private fun getOrCreateMainModuleFragment(itemId: Int): Fragment? {
        getMainModuleFragment(itemId)?.let { return it }
        val fragment: Fragment? = when (itemId) {
            R.id.nav_schedule -> WeeklySchedulerFragment()
            R.id.nav_music -> MusicPlayerFragment()
            R.id.nav_scanner -> ScannerFragment()
            R.id.nav_equalizer -> EqualizerFragment()
            R.id.nav_apps -> AppsFragment()
            else -> null
        }
        fragment?.let {
            when (itemId) {
                R.id.nav_schedule -> scheduleFragment = it
                R.id.nav_music -> musicFragment = it
                R.id.nav_scanner -> scannerFragment = it
                R.id.nav_equalizer -> equalizerFragment = it
                R.id.nav_apps -> appsFragment = it
            }
        }
        return fragment
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

        val isTrulySwitching = currentMainNavItemId != itemId || !target.isAdded
        if (!isTrulySwitching && !inSettings) {
            updateHeaderTitleForModule(itemId)
            return true
        }

        val isNew = !target.isAdded
        showModuleLoadingOverlay()
        fragmentContainer.alpha = 0f

        supportFragmentManager.beginTransaction().apply {
            setReorderingAllowed(true)
            playlistDetailFragment = supportFragmentManager.findFragmentByTag(TAG_PLAYLIST_DETAIL)
            songPlayerFragment = supportFragmentManager.findFragmentByTag(TAG_SONG_PLAYER)
            val current = getMainModuleFragment(currentMainNavItemId)
            
            hideIfVisible(this, current, target)
            hideIfVisible(this, playlistDetailFragment, target)
            hideIfVisible(this, songPlayerFragment, target)
            hideIfVisible(this, settingsFragment, target)
            
            // Ensure the SongPlayerFragment in playerContainer is always hidden when
            // switching modules. It lives in a separate elevated container, so hiding
            // it via the fragment transaction alone is not enough — we must explicitly
            // hide the fragment so the playerContainer doesn't cover the module content.
            if (songPlayerFragment != null && songPlayerFragment!!.isAdded && !songPlayerFragment!!.isHidden) {
                hide(songPlayerFragment!!)
            }
            
            if (target.isAdded) show(target) else add(R.id.fragmentContainer, target, tag)
            setMaxLifecycle(target, Lifecycle.State.RESUMED)
            commit()
        }

        currentMainNavItemId = itemId

        // Persistir el último módulo abierto
        getSharedPreferences(PREFS_BOOTSTRAP, Context.MODE_PRIVATE)
            .edit()
            .putInt(PREF_LAST_MAIN_MODULE, itemId)
            .apply()

        if (itemId == R.id.nav_music) markStreamingEntryAsLibrary()
        updateHeaderTitleForModule(itemId)

        lifecycleScope.launch {
            delay(if (isNew) MODULE_LOAD_OVERLAY_MIN_MS + 80 else MODULE_LOAD_OVERLAY_MIN_MS)
            revealModuleContent()
        }
        return true
    }

    fun showModuleLoadingOverlay() {
        moduleLoadingOverlay.alpha = 1f
        moduleLoadingOverlay.visibility = View.VISIBLE
    }

    fun revealModuleContent() {
        if (isFinishing || isDestroyed) return
        fragmentContainer.animate().cancel()
        moduleLoadingOverlay.animate().cancel()
        moduleLoadingOverlay.visibility = View.GONE
        fragmentContainer.animate().alpha(1f).setDuration(280L).start()
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

        snapshotStreamingScreenBeforeNavigation()
        val fallback = resolveSongPlayerReturnTarget(player.externalGetReturnTargetTag())
        supportFragmentManager.beginTransaction().apply {
            setReorderingAllowed(true)
            setCustomAnimations(
                R.anim.hold,
                R.anim.player_screen_exit
            )
            if (fallback != null && fallback.isAdded && fallback != player) {
                hide(player).show(fallback)
            } else {
                remove(player)
            }
            commit()
        }
        return true
    }

    /** Called by [SongPlayerFragment] when dismissed via swipe. */
    fun externalClosePlayerImmediately() {
        val player = supportFragmentManager.findFragmentByTag(TAG_SONG_PLAYER) as? SongPlayerFragment ?: return
        if (!player.isAdded || player.isHidden) return
        
        val fallback = resolveSongPlayerReturnTarget(player.externalGetReturnTargetTag())
        supportFragmentManager.beginTransaction().apply {
            setReorderingAllowed(true)
            if (fallback != null && fallback.isAdded && fallback != player) {
                hide(player).show(fallback)
            } else {
                remove(player)
            }
            commitNowAllowingStateLoss()
        }
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

        markStreamingEntryAsLibrary()
        supportFragmentManager.popBackStackImmediate(TAG_PLAYLIST_DETAIL, androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE)
        if (bottomNav.selectedItemId != R.id.nav_music) bottomNav.selectedItemId = R.id.nav_music
        currentMainNavItemId = R.id.nav_music
        updateHeaderTitleForModule(R.id.nav_music)
        return true
    }

    private fun updateHeaderTitleForModule(itemId: Int) {
        tvModuleTitle.text = if (inSettings) getString(R.string.header_title_settings) else getString(R.string.header_brand_title)
    }

    fun setContainerOverlayMode(enabled: Boolean) {
        // Obsolete: SongPlayerFragment now uses its own edge-to-edge container (playerContainer).
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
}
