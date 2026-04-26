package com.example.sleppify

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Path
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.*
import java.util.*
import java.util.regex.Pattern

/**
 * Optimized Kotlin implementation of AppAccessibilityService.
 * Handles automation for force-stopping apps and clearing recents.
 * Now using Coroutines for cleaner sequential logic.
 */
class AppAccessibilityService : AccessibilityService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mainHandler = Handler(Looper.getMainLooper())

    private var clearRecentsInProgress = false
    private var clearAttempts = 0

    private var forceStopInProgress = false
    private var forceStopTransitionInProgress = false
    private var waitingForceStopConfirmation = false
    private var forceStopReopenScheduled = false
    private var forceStopAttempts = 0
    private var forceStopTargetCount = 0
    private var forceStopConfirmedCount = 0
    private val forceStopQueue = ArrayDeque<String>()
    private val forceStopConfirmedPackages = mutableSetOf<String>()

    private var currentForceStopPackage: String? = null
    private var currentForceStopStartedAtMs = 0L
    private var currentForceStopClickAtMs = 0L
    private var lastAttemptIncrementMs = 0L

    private val recentAppTimestampsByPackage = mutableMapOf<String, Long>()
    private var recentAppsLoaded = false
    private var lastRecentAppsPersistAtMs = 0L

    companion object {
        private const val STOP_APPS_LOG_TAG = "SleppifyStopApps"

        const val ACTION_FORCE_STOP_AUTOMATION_FINISHED = "com.example.sleppify.action.FORCE_STOP_AUTOMATION_FINISHED"
        const val ACTION_START_AUTOMATION = "com.example.sleppify.action.START_FORCE_STOP_AUTOMATION"
        const val EXTRA_PACKAGE_NAMES = "extra_package_names"
        const val EXTRA_TARGET_COUNT = "extra_target_count"
        const val EXTRA_CONFIRMED_COUNT = "extra_confirmed_count"
        const val EXTRA_CONFIRMED_PACKAGES = "extra_confirmed_packages"
        const val KEY_ACCESSIBILITY_RECENT_APPS = "apps_accessibility_recent_apps"

        private const val RECENT_APPS_TTL_MS = 6L * 60 * 60 * 1000
        private const val MAX_RECENT_APPS = 40
        private const val RECENT_APPS_PERSIST_DEBOUNCE_MS = 1500L

        private const val INITIAL_CLEAR_DELAY_MS = 380L
        private const val CLEAR_RETRY_DELAY_MS = 260L
        private const val MAX_CLEAR_ATTEMPTS = 10

        private const val NORMAL_FORCE_STOP_OPEN_DELAY_MS = 1000L
        private const val NORMAL_FORCE_STOP_RETRY_DELAY_MS = 500L
        private const val NORMAL_FORCE_STOP_NEXT_DELAY_MS = 1000L
        private const val NORMAL_FORCE_STOP_AFTER_CLICK_DELAY_MS = 500L
        private const val NORMAL_FORCE_STOP_CONFIRM_MIN_DELAY_MS = 1000L
        private const val NORMAL_FORCE_STOP_CONFIRM_VERIFY_DELAY_MS = 1000L
        private const val NORMAL_FORCE_STOP_PER_PACKAGE_TIMEOUT_MS = 20000L
        private const val FORCE_STOP_TRANSITION_HOME_DELAY_MS = 140L
        private const val FORCE_STOP_TRANSITION_BACK_DELAY_MS = 220L
        private const val TAP_FALLBACK_DURATION_MS = 90L
        private const val MAX_FORCE_STOP_STEP_ATTEMPTS = 12
        private const val MAX_CONFIRM_STEP_ATTEMPTS = 9

        private val PACKAGE_NAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_]+(?:\\.[a-zA-Z0-9_]+)+$")

        private val CLEAR_ALL_TEXT_CANDIDATES = arrayOf(
            "Cerrar todo", "Cerrar todas", "Borrar todo", "Limpiar todo",
            "Clear all", "Close all", "Dismiss all", "Fermer tout", "Chiudi tutto"
        )

        private val CLEAR_ALL_VIEW_ID_CANDIDATES = arrayOf(
            "com.android.systemui:id/clear_all", "com.android.systemui:id/dismiss_all",
            "com.android.launcher3:id/clear_all", "com.miui.home:id/clearAnimView",
            "com.sec.android.app.launcher:id/clear_all_button", "com.huawei.android.launcher:id/clear_all_recents"
        )

        private val FORCE_STOP_TEXT_CANDIDATES = arrayOf(
            "Forzar detencion", "Forzar detención", "FORZAR DETENCION", "FORZAR DETENCIÓN",
            "Forzar detencion de app", "Forzar cierre", "Force stop", "FORCE STOP", "Detener"
        )

        private val FORCE_STOP_VIEW_ID_CANDIDATES = arrayOf(
            "com.android.settings:id/force_stop_button", "com.android.settings:id/right_button",
            "com.miui.securitycenter:id/am_force_stop", "com.coloros.safecenter:id/force_stop",
            "com.huawei.systemmanager:id/force_stop", "com.samsung.android.sm:id/force_stop_button"
        )

        private val FORCE_STOP_CONFIRM_TEXT_CANDIDATES = arrayOf(
            "Forzar detencion", "Forzar detención", "FORZAR DETENCION", "FORZAR DETENCIÓN",
            "Force stop", "FORCE STOP", "Aceptar", "OK"
        )

        private val FORCE_STOP_CONFIRM_VIEW_ID_CANDIDATES = arrayOf(
            "android:id/button1", "com.android.settings:id/button1", "miuix.appcompat:id/buttonGroup"
        )

        private val FORCE_STOP_KEYWORDS = arrayOf("forzar", "force stop", "force-stop", "finalizar")
        private val CONFIRM_KEYWORDS = arrayOf("aceptar", "ok", "confirm", "yes", "si", "sí")

        @Volatile
        private var instance: AppAccessibilityService? = null

        @JvmStatic
        fun requestClearRecentApps(): Boolean {
            val service = instance ?: return false
            return service.startClearRecentsFlow()
        }

        @JvmStatic
        fun requestForceStopPackages(packageNames: List<String>): Boolean {
            val service = instance ?: return false
            return service.startForceStopFlow(packageNames)
        }

        @JvmStatic
        fun readRecentAppsSnapshot(context: Context): Map<String, Long> {
            val prefs = context.getSharedPreferences(CloudSyncManager.PREFS_SETTINGS, MODE_PRIVATE)
            val serialized = prefs.getString(KEY_ACCESSIBILITY_RECENT_APPS, "") ?: ""
            return parseRecentAppsSnapshot(serialized).apply {
                pruneRecentAppsSnapshot(this, System.currentTimeMillis())
            }
        }

        private fun parseRecentAppsSnapshot(serialized: String): MutableMap<String, Long> {
            val result = mutableMapOf<String, Long>()
            if (serialized.isEmpty()) return result
            
            serialized.split('\n').forEach { line ->
                val sep = line.lastIndexOf('|')
                if (sep > 0 && sep < line.length - 1) {
                    val pkg = line.substring(0, sep).trim()
                    val tsStr = line.substring(sep + 1).trim()
                    if (pkg.isNotEmpty() && PACKAGE_NAME_PATTERN.matcher(pkg).matches()) {
                        tsStr.toLongOrNull()?.let { if (it > 0) result[pkg] = it }
                    }
                }
            }
            return result
        }

        private fun serializeRecentAppsSnapshot(snapshot: Map<String, Long>): String {
            return snapshot.entries.joinToString("\n") { "${it.key}|${it.value}" }
        }

        private fun pruneRecentAppsSnapshot(snapshot: MutableMap<String, Long>, now: Long) {
            val toRemove = snapshot.filter { (_, ts) -> now - ts > RECENT_APPS_TTL_MS }.keys
            toRemove.forEach { snapshot.remove(it) }

            if (snapshot.size > MAX_RECENT_APPS) {
                val sorted = snapshot.entries.sortedByDescending { it.value }
                snapshot.clear()
                sorted.take(MAX_RECENT_APPS).forEach { snapshot[it.key] = it.value }
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.i(STOP_APPS_LOG_TAG, "Accessibility service connected.")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
            AccessibilityEvent.TYPE_WINDOWS_CHANGED -> {
                recordRecentAppFromEvent(event)
                if (forceStopInProgress) {
                    if (!forceStopTransitionInProgress) processForceStopStep()
                } else if (clearRecentsInProgress) {
                    tryClearAllFromRecents()
                }
            }
        }
    }

    override fun onInterrupt() {}

    override fun onUnbind(intent: Intent?): Boolean {
        if (instance == this) instance = null
        flushRecentAppsSnapshot()
        resetAllStates()
        return super.onUnbind(intent)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_START_AUTOMATION) {
            val list = intent.getStringArrayListExtra(EXTRA_PACKAGE_NAMES)
            if (!list.isNullOrEmpty()) {
                startForceStopFlow(list)
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        if (instance == this) instance = null
        serviceScope.cancel()
        flushRecentAppsSnapshot()
        resetAllStates()
        super.onDestroy()
    }

    private fun startClearRecentsFlow(): Boolean {
        if (forceStopInProgress) return false
        
        clearRecentsInProgress = true
        clearAttempts = 0
        
        val opened = performGlobalAction(GLOBAL_ACTION_RECENTS)
        if (!opened) {
            clearRecentsInProgress = false
            return false
        }
        
        serviceScope.launch {
            delay(INITIAL_CLEAR_DELAY_MS)
            tryClearAllFromRecents()
        }
        return true
    }

    private fun tryClearAllFromRecents() {
        if (!clearRecentsInProgress) return

        if (clickClearAllNode()) {
            Log.i(STOP_APPS_LOG_TAG, "Clear recents succeeded.")
            clearRecentsInProgress = false
            serviceScope.launch {
                delay(220L)
                performGlobalAction(GLOBAL_ACTION_HOME)
            }
            return
        }

        clearAttempts++
        if (clearAttempts >= MAX_CLEAR_ATTEMPTS) {
            clearRecentsInProgress = false
            return
        }

        serviceScope.launch {
            delay(CLEAR_RETRY_DELAY_MS)
            tryClearAllFromRecents()
        }
    }

    private fun startForceStopFlow(packageNames: List<String>): Boolean {
        if (forceStopInProgress) return false

        val own = packageName
        val whitelist = getWhitelistedPackages()
        val targets = packageNames.map { it.trim() }
            .filter { it.isNotEmpty() && it != own && !whitelist.contains(it) && isValidPackageName(it) }
            .distinct()

        if (targets.isEmpty()) return false

        Log.i(STOP_APPS_LOG_TAG, "Force-stop flow accepted. Targets: ${targets.size}")

        clearRecentsInProgress = false
        forceStopQueue.clear()
        forceStopQueue.addAll(targets)
        forceStopConfirmedPackages.clear()
        forceStopTargetCount = targets.size
        forceStopConfirmedCount = 0
        forceStopInProgress = true
        forceStopTransitionInProgress = false
        waitingForceStopConfirmation = false
        forceStopReopenScheduled = false
        forceStopAttempts = 0
        currentForceStopPackage = null

        openNextPackageDetails()
        return true
    }

    private fun openNextPackageDetails() {
        if (!forceStopInProgress) return
        forceStopTransitionInProgress = false

        val pkg = forceStopQueue.pollFirst() ?: run {
            finishForceStopFlow()
            return
        }

        currentForceStopPackage = pkg
        currentForceStopStartedAtMs = System.currentTimeMillis()
        currentForceStopClickAtMs = 0L
        lastAttemptIncrementMs = 0L
        waitingForceStopConfirmation = false
        forceStopAttempts = 0

        try {
            val intent = buildAppDetailsIntent(pkg)
            if (intent.resolveActivity(packageManager) == null) {
                scheduleNextForceStopPackage(NORMAL_FORCE_STOP_NEXT_DELAY_MS)
                return
            }
            startActivity(intent)
            serviceScope.launch {
                delay(NORMAL_FORCE_STOP_OPEN_DELAY_MS)
                processForceStopStep()
            }
        } catch (e: Exception) {
            scheduleNextForceStopPackage(NORMAL_FORCE_STOP_NEXT_DELAY_MS)
        }
    }

    private fun processForceStopStep() {
        if (!forceStopInProgress || forceStopTransitionInProgress || currentForceStopPackage == null || forceStopReopenScheduled) return

        val root = rootInActiveWindow ?: run {
            retryForceStopStep()
            return
        }

        try {
            if (isCurrentPackageTimedOut()) {
                scheduleNextForceStopPackage(NORMAL_FORCE_STOP_NEXT_DELAY_MS)
                return
            }

            val windowPackage = root.packageName?.toString()?.lowercase() ?: ""
            if (!isLikelyForceStopWindow(windowPackage) && !hasForceStopUiHints(root)) {
                val now = System.currentTimeMillis()
                if (now - lastAttemptIncrementMs >= 300) {
                    forceStopAttempts++
                    lastAttemptIncrementMs = now
                }

                if (forceStopAttempts >= MAX_FORCE_STOP_STEP_ATTEMPTS) {
                    scheduleNextForceStopPackage(NORMAL_FORCE_STOP_NEXT_DELAY_MS)
                    return
                }
                scheduleReopenCurrentPackageDetails()
                return
            }

            forceStopAttempts = 0
            lastAttemptIncrementMs = 0

            if (!waitingForceStopConfirmation) {
                if (hasDisabledForceStopControl(root)) {
                    confirmCurrentPackageAndAdvance()
                    return
                }

                val clicked = clickByViewIdCandidates(root, FORCE_STOP_VIEW_ID_CANDIDATES) ||
                              clickByTextCandidates(root, FORCE_STOP_TEXT_CANDIDATES) ||
                              clickByKeywordScan(root, FORCE_STOP_KEYWORDS)
                
                if (clicked) {
                    currentForceStopClickAtMs = System.currentTimeMillis()
                    waitingForceStopConfirmation = true
                    serviceScope.launch {
                        delay(NORMAL_FORCE_STOP_AFTER_CLICK_DELAY_MS)
                        processForceStopStep()
                    }
                } else {
                    retryForceStopStep()
                }
                return
            }

            if (hasDisabledForceStopControl(root)) {
                confirmCurrentPackageAndAdvance()
                return
            }

            val elapsedSinceClick = System.currentTimeMillis() - currentForceStopClickAtMs
            if (elapsedSinceClick < NORMAL_FORCE_STOP_CONFIRM_MIN_DELAY_MS) {
                serviceScope.launch {
                    delay(NORMAL_FORCE_STOP_CONFIRM_MIN_DELAY_MS - elapsedSinceClick)
                    processForceStopStep()
                }
                return
            }

            val confirmed = clickByViewIdCandidates(root, FORCE_STOP_CONFIRM_VIEW_ID_CANDIDATES) ||
                            clickByTextCandidates(root, FORCE_STOP_CONFIRM_TEXT_CANDIDATES) ||
                            clickByKeywordScan(root, CONFIRM_KEYWORDS)
            
            if (confirmed) confirmCurrentPackageAndAdvance() else retryForceStopStep()

        } catch (_: Exception) {
            retryForceStopStep()
        }
    }

    private fun isLikelyForceStopWindow(pkg: String): Boolean {
        return pkg == "android" || pkg.contains("settings") || pkg.contains("securitycenter") ||
               pkg.contains("miui") || pkg.contains("systemmanager") || pkg.contains("samsung") ||
               pkg.contains("devicecare") || pkg.contains("packageinstaller") || pkg.contains("permissioncontroller")
    }

    private fun isCurrentPackageTimedOut() = System.currentTimeMillis() - currentForceStopStartedAtMs >= NORMAL_FORCE_STOP_PER_PACKAGE_TIMEOUT_MS

    private fun confirmCurrentPackageAndAdvance() {
        currentForceStopPackage?.let { 
            forceStopConfirmedPackages.add(it)
            forceStopConfirmedCount = forceStopConfirmedPackages.size
        }
        scheduleNextForceStopPackage(NORMAL_FORCE_STOP_NEXT_DELAY_MS)
    }

    private fun scheduleReopenCurrentPackageDetails() {
        if (forceStopReopenScheduled) return
        forceStopReopenScheduled = true
        serviceScope.launch {
            delay(NORMAL_FORCE_STOP_RETRY_DELAY_MS)
            forceStopReopenScheduled = false
            currentForceStopPackage?.let { pkg ->
                try {
                    startActivity(buildAppDetailsIntent(pkg))
                    delay(NORMAL_FORCE_STOP_OPEN_DELAY_MS)
                    processForceStopStep()
                } catch (e: Exception) {
                    retryForceStopStep()
                }
            } ?: retryForceStopStep()
        }
    }

    private fun retryForceStopStep() {
        if (!forceStopInProgress) return
        forceStopAttempts++
        val max = if (waitingForceStopConfirmation) MAX_CONFIRM_STEP_ATTEMPTS else MAX_FORCE_STOP_STEP_ATTEMPTS
        if (forceStopAttempts >= max) {
            scheduleNextForceStopPackage(NORMAL_FORCE_STOP_NEXT_DELAY_MS)
        } else {
            serviceScope.launch {
                delay(NORMAL_FORCE_STOP_RETRY_DELAY_MS)
                processForceStopStep()
            }
        }
    }

    private fun scheduleNextForceStopPackage(delayMs: Long) {
        forceStopTransitionInProgress = true
        forceStopReopenScheduled = false
        currentForceStopPackage = null
        
        serviceScope.launch {
            performGlobalAction(GLOBAL_ACTION_BACK)
            delay(FORCE_STOP_TRANSITION_HOME_DELAY_MS)
            performGlobalAction(GLOBAL_ACTION_HOME)
            delay(delayMs + FORCE_STOP_TRANSITION_BACK_DELAY_MS)
            openNextPackageDetails()
        }
    }

    private fun finishForceStopFlow() {
        if (!forceStopInProgress) return
        Log.i(STOP_APPS_LOG_TAG, "Force-stop flow finished. Confirmed: $forceStopConfirmedCount")
        
        val intent = Intent(ACTION_FORCE_STOP_AUTOMATION_FINISHED).apply {
            setPackage(packageName)
            putExtra(EXTRA_TARGET_COUNT, forceStopTargetCount)
            putExtra(EXTRA_CONFIRMED_COUNT, forceStopConfirmedCount)
            putStringArrayListExtra(EXTRA_CONFIRMED_PACKAGES, ArrayList(forceStopConfirmedPackages))
        }
        sendBroadcast(intent)
        resetAllStates()
        serviceScope.launch {
            delay(240L)
            performGlobalAction(GLOBAL_ACTION_HOME)
        }
    }

    private fun hasDisabledForceStopControl(root: AccessibilityNodeInfo): Boolean {
        return hasDisabledNode(root, FORCE_STOP_VIEW_ID_CANDIDATES, true) ||
               hasDisabledNode(root, FORCE_STOP_TEXT_CANDIDATES, false)
    }

    private fun hasForceStopUiHints(root: AccessibilityNodeInfo): Boolean {
        return hasAnyNode(root, FORCE_STOP_VIEW_ID_CANDIDATES, true) ||
               hasAnyNode(root, FORCE_STOP_TEXT_CANDIDATES, false) ||
               hasAnyNode(root, FORCE_STOP_CONFIRM_VIEW_ID_CANDIDATES, true) ||
               hasAnyNode(root, FORCE_STOP_CONFIRM_TEXT_CANDIDATES, false) ||
               hasKeywordMatch(root, FORCE_STOP_KEYWORDS) ||
               hasKeywordMatch(root, CONFIRM_KEYWORDS)
    }

    private fun hasAnyNode(root: AccessibilityNodeInfo, candidates: Array<String>, isId: Boolean): Boolean {
        for (c in candidates) {
            val nodes = if (isId) try { root.findAccessibilityNodeInfosByViewId(c) } catch (e: Exception) { null }
                        else root.findAccessibilityNodeInfosByText(c)
            if (!nodes.isNullOrEmpty()) {
                return true
            }
        }
        return false
    }

    private fun hasDisabledNode(root: AccessibilityNodeInfo, candidates: Array<String>, isId: Boolean): Boolean {
        for (c in candidates) {
            val nodes = if (isId) try { root.findAccessibilityNodeInfosByViewId(c) } catch (e: Exception) { null }
                        else root.findAccessibilityNodeInfosByText(c)
            if (!nodes.isNullOrEmpty()) {
                val hasDisabled = nodes.any { !it.isEnabled }
                if (hasDisabled) return true
            }
        }
        return false
    }

    private fun hasKeywordMatch(root: AccessibilityNodeInfo, keywords: Array<String>): Boolean {
        if (matchesAnyKeyword(root.text, keywords) || matchesAnyKeyword(root.contentDescription, keywords)) return true
        for (i in 0 until root.childCount) {
            root.getChild(i)?.let { child ->
                if (hasKeywordMatch(child, keywords)) return true
            }
        }
        return false
    }

    private fun clickClearAllNode(): Boolean {
        val root = rootInActiveWindow ?: return false
        return clickByViewIdCandidates(root, CLEAR_ALL_VIEW_ID_CANDIDATES) ||
            clickByTextCandidates(root, CLEAR_ALL_TEXT_CANDIDATES)
    }

    private fun clickByViewIdCandidates(root: AccessibilityNodeInfo, candidates: Array<String>): Boolean {
        for (id in candidates) {
            val nodes = try { root.findAccessibilityNodeInfosByViewId(id) } catch (e: Exception) { null }
            if (clickFirstClickable(nodes)) return true
        }
        return false
    }

    private fun clickByTextCandidates(root: AccessibilityNodeInfo, candidates: Array<String>): Boolean {
        for (text in candidates) {
            val nodes = root.findAccessibilityNodeInfosByText(text)
            if (clickFirstClickable(nodes)) return true
        }
        return false
    }

    private fun clickByKeywordScan(root: AccessibilityNodeInfo, keywords: Array<String>): Boolean {
        if (matchesAnyKeyword(root.text, keywords) || matchesAnyKeyword(root.contentDescription, keywords)) {
            if (clickNodeOrAncestor(root)) return true
        }
        for (i in 0 until root.childCount) {
            root.getChild(i)?.let { child ->
                if (clickByKeywordScan(child, keywords)) return true
            }
        }
        return false
    }

    private fun matchesAnyKeyword(value: CharSequence?, keywords: Array<String>): Boolean {
        val text = value?.toString()?.lowercase() ?: return false
        return keywords.any { text.contains(it) }
    }

    private fun clickFirstClickable(nodes: List<AccessibilityNodeInfo>?): Boolean {
        nodes?.forEach { if (clickNodeOrAncestor(it)) return true }
        return false
    }

    private fun clickNodeOrAncestor(node: AccessibilityNodeInfo): Boolean {
        var curr: AccessibilityNodeInfo? = node
        while (curr != null) {
            if (curr.isClickable && curr.isEnabled) {
                return curr.performAction(AccessibilityNodeInfo.ACTION_CLICK) || tapNodeCenter(curr)
            }
            curr = curr.parent
        }
        return false
    }

    private fun tapNodeCenter(node: AccessibilityNodeInfo): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false
        val bounds = Rect().apply { node.getBoundsInScreen(this) }
        if (bounds.isEmpty) return false

        val path = Path().apply { moveTo(bounds.exactCenterX(), bounds.exactCenterY()) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0L, TAP_FALLBACK_DURATION_MS))
            .build()
        return dispatchGesture(gesture, null, null)
    }

    private fun recordRecentAppFromEvent(event: AccessibilityEvent) {
        val pkg = event.packageName?.toString()?.trim() ?: return
        if (pkg.isEmpty() || !isValidPackageName(pkg) || shouldIgnoreRecentApp(pkg)) return

        val now = System.currentTimeMillis()
        synchronized(this) {
            if (!recentAppsLoaded) {
                recentAppTimestampsByPackage.putAll(readRecentAppsSnapshot(this))
                recentAppsLoaded = true
            }
            recentAppTimestampsByPackage[pkg] = now
            pruneRecentAppsSnapshot(recentAppTimestampsByPackage, now)
            if (now - lastRecentAppsPersistAtMs >= RECENT_APPS_PERSIST_DEBOUNCE_MS) {
                lastRecentAppsPersistAtMs = now
                getSharedPreferences(CloudSyncManager.PREFS_SETTINGS, MODE_PRIVATE).edit()
                    .putString(KEY_ACCESSIBILITY_RECENT_APPS, serializeRecentAppsSnapshot(recentAppTimestampsByPackage))
                    .apply()
            }
        }
    }

    private fun shouldIgnoreRecentApp(pkg: String): Boolean {
        if (packageName == pkg) return true
        val low = pkg.lowercase()
        return low == "android" || low.startsWith("com.android.systemui") || low.startsWith("com.android.settings") ||
               low.contains("launcher") || low.contains("permissioncontroller") || low.contains("packageinstaller") ||
               low.contains("securitycenter") || low.contains("devicecare")
    }

    private fun flushRecentAppsSnapshot() {
        if (!recentAppsLoaded) return
        val ts = System.currentTimeMillis()
        getSharedPreferences(CloudSyncManager.PREFS_SETTINGS, MODE_PRIVATE).edit()
            .putString(KEY_ACCESSIBILITY_RECENT_APPS, serializeRecentAppsSnapshot(recentAppTimestampsByPackage))
            .apply()
    }

    private fun buildAppDetailsIntent(pkg: String) = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", pkg, null)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    private fun isValidPackageName(pkg: String) = PACKAGE_NAME_PATTERN.matcher(pkg).matches()

    private fun getWhitelistedPackages(): Set<String> {
        val prefs = getSharedPreferences(CloudSyncManager.PREFS_SETTINGS, MODE_PRIVATE)
        return prefs.getStringSet(CloudSyncManager.KEY_APPS_WHITELIST_PACKAGES, null) ?: emptySet()
    }


    private fun resetAllStates() {
        clearRecentsInProgress = false
        forceStopInProgress = false
        forceStopTransitionInProgress = false
        waitingForceStopConfirmation = false
        forceStopReopenScheduled = false
        forceStopAttempts = 0
        forceStopQueue.clear()
        forceStopConfirmedPackages.clear()
        currentForceStopPackage = null
        recentAppTimestampsByPackage.clear()
        recentAppsLoaded = false
        serviceScope.coroutineContext.cancelChildren()
    }
}
