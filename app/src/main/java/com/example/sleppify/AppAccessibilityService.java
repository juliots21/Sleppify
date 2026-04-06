package com.example.sleppify;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Path;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public class AppAccessibilityService extends AccessibilityService {

    private static final String STOP_APPS_LOG_TAG = "SleppifyStopApps";

    public static final String ACTION_FORCE_STOP_AUTOMATION_FINISHED = "com.example.sleppify.action.FORCE_STOP_AUTOMATION_FINISHED";
    public static final String EXTRA_TARGET_COUNT = "extra_target_count";
    public static final String EXTRA_CONFIRMED_COUNT = "extra_confirmed_count";
    public static final String EXTRA_CONFIRMED_PACKAGES = "extra_confirmed_packages";
    public static final String KEY_ACCESSIBILITY_RECENT_APPS = "apps_accessibility_recent_apps";

    private static final long RECENT_APPS_TTL_MS = 6L * 60L * 60L * 1000L;
    private static final int MAX_RECENT_APPS = 40;
    private static final long RECENT_APPS_PERSIST_DEBOUNCE_MS = 1500L;

    private static final long INITIAL_CLEAR_DELAY_MS = 380L;
    private static final long CLEAR_RETRY_DELAY_MS = 260L;
    private static final int MAX_CLEAR_ATTEMPTS = 10;

    private static final long NORMAL_FORCE_STOP_OPEN_DELAY_MS = 1_000L;
    private static final long NORMAL_FORCE_STOP_RETRY_DELAY_MS = 500L;
    private static final long NORMAL_FORCE_STOP_NEXT_DELAY_MS = 1_000L;
    private static final long NORMAL_FORCE_STOP_AFTER_CLICK_DELAY_MS = 500L;
    private static final long NORMAL_FORCE_STOP_CONFIRM_MIN_DELAY_MS = 1_000L;
    private static final long NORMAL_FORCE_STOP_CONFIRM_VERIFY_DELAY_MS = 1_000L;
    private static final long NORMAL_FORCE_STOP_PER_PACKAGE_TIMEOUT_MS = 20_000L;

    private static final long TURBO_FORCE_STOP_OPEN_DELAY_MS = 800L;
    private static final long TURBO_FORCE_STOP_RETRY_DELAY_MS = 400L;
    private static final long TURBO_FORCE_STOP_NEXT_DELAY_MS = 800L;
    private static final long TURBO_FORCE_STOP_AFTER_CLICK_DELAY_MS = 400L;
    private static final long TURBO_FORCE_STOP_CONFIRM_MIN_DELAY_MS = 800L;
    private static final long TURBO_FORCE_STOP_CONFIRM_VERIFY_DELAY_MS = 800L;
    private static final long TURBO_FORCE_STOP_PER_PACKAGE_TIMEOUT_MS = 16_000L;
    private static final long FORCE_STOP_TRANSITION_HOME_DELAY_MS = 140L;
    private static final long FORCE_STOP_TRANSITION_BACK_DELAY_MS = 220L;
    private static final long TAP_FALLBACK_DURATION_MS = 90L;
    private static final int MAX_FORCE_STOP_STEP_ATTEMPTS = 12;
    private static final int MAX_CONFIRM_STEP_ATTEMPTS = 9;
    private static final Pattern PACKAGE_NAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_]+(?:\\.[a-zA-Z0-9_]+)+$");

    private static final String[] CLEAR_ALL_TEXT_CANDIDATES = {
            "Cerrar todo",
            "Cerrar todas",
            "Borrar todo",
            "Limpiar todo",
            "Clear all",
            "Close all",
            "Dismiss all",
            "Fermer tout",
            "Chiudi tutto"
    };

    private static final String[] CLEAR_ALL_VIEW_ID_CANDIDATES = {
            "com.android.systemui:id/clear_all",
            "com.android.systemui:id/dismiss_all",
            "com.android.launcher3:id/clear_all",
            "com.miui.home:id/clearAnimView",
            "com.sec.android.app.launcher:id/clear_all_button",
            "com.huawei.android.launcher:id/clear_all_recents"
    };

    private static final String[] FORCE_STOP_TEXT_CANDIDATES = {
            "Forzar detencion",
            "Forzar detención",
            "FORZAR DETENCION",
            "FORZAR DETENCIÓN",
            "Forzar detencion de app",
            "Forzar cierre",
            "Force stop",
            "FORCE STOP",
            "Detener"
    };

    private static final String[] FORCE_STOP_VIEW_ID_CANDIDATES = {
            "com.android.settings:id/force_stop_button",
            "com.android.settings:id/right_button",
            "com.miui.securitycenter:id/am_force_stop",
            "com.coloros.safecenter:id/force_stop",
            "com.huawei.systemmanager:id/force_stop",
            "com.samsung.android.sm:id/force_stop_button"
    };

    private static final String[] FORCE_STOP_CONFIRM_TEXT_CANDIDATES = {
            "Forzar detencion",
            "Forzar detención",
            "FORZAR DETENCION",
            "FORZAR DETENCIÓN",
            "Force stop",
            "FORCE STOP",
            "Aceptar",
            "OK"
    };

    private static final String[] FORCE_STOP_CONFIRM_VIEW_ID_CANDIDATES = {
            "android:id/button1",
            "com.android.settings:id/button1",
            "miuix.appcompat:id/buttonGroup"
    };

    private static final String[] FORCE_STOP_KEYWORDS = {
            "forzar",
            "force stop",
            "force-stop",
            "finalizar"
    };

    private static final String[] CONFIRM_KEYWORDS = {
            "aceptar",
            "ok",
            "confirm",
            "yes",
            "si",
            "sí"
    };

    @Nullable
    private static volatile AppAccessibilityService instance;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private boolean clearRecentsInProgress;
    private int clearAttempts;

    private long forceStopOpenDelayMs = NORMAL_FORCE_STOP_OPEN_DELAY_MS;
    private long forceStopRetryDelayMs = NORMAL_FORCE_STOP_RETRY_DELAY_MS;
    private long forceStopNextDelayMs = NORMAL_FORCE_STOP_NEXT_DELAY_MS;
    private long forceStopAfterClickDelayMs = NORMAL_FORCE_STOP_AFTER_CLICK_DELAY_MS;
    private long forceStopConfirmMinDelayMs = NORMAL_FORCE_STOP_CONFIRM_MIN_DELAY_MS;
    private long forceStopConfirmVerifyDelayMs = NORMAL_FORCE_STOP_CONFIRM_VERIFY_DELAY_MS;
    private long forceStopPerPackageTimeoutMs = NORMAL_FORCE_STOP_PER_PACKAGE_TIMEOUT_MS;

    private final ArrayDeque<String> forceStopQueue = new ArrayDeque<>();
    private final Set<String> forceStopConfirmedPackages = new LinkedHashSet<>();
    private boolean forceStopInProgress;
    private boolean forceStopTransitionInProgress;
    private boolean waitingForceStopConfirmation;
    private boolean forceStopReopenScheduled;
    private int forceStopAttempts;
    private int forceStopTargetCount;
    private int forceStopConfirmedCount;
    @Nullable
    private String currentForceStopPackage;
    private long currentForceStopStartedAtMs;
    private long currentForceStopClickAtMs;
    private long lastAttemptIncrementMs;

    private final Map<String, Long> recentAppTimestampsByPackage = new LinkedHashMap<>();
    private boolean recentAppsLoaded;
    private long lastRecentAppsPersistAtMs;

    private final Runnable clearRecentsRunnable = this::tryClearAllFromRecents;
    private final Runnable forceStopRunnable = this::processForceStopStep;
    private final Runnable reopenCurrentPackageRunnable = () -> {
        synchronized (this) {
            forceStopReopenScheduled = false;
        }
        reopenCurrentPackageDetails();
    };

    public static boolean requestClearRecentApps() {
        AppAccessibilityService service = instance;
        boolean started = service != null && service.startClearRecentsFlow();
        if (!started) {
            Log.w(STOP_APPS_LOG_TAG, "requestClearRecentApps rejected: service unavailable or busy.");
        }
        return started;
    }

    public static boolean requestForceStopPackages(@NonNull List<String> packageNames) {
        return requestForceStopPackages(packageNames, false);
    }

    public static boolean requestForceStopPackages(@NonNull List<String> packageNames, boolean turboMode) {
        AppAccessibilityService service = instance;
        boolean started = service != null && service.startForceStopFlow(packageNames, turboMode);
        if (!started) {
            Log.w(
                    STOP_APPS_LOG_TAG,
                    "requestForceStopPackages rejected: service unavailable or busy, requested="
                            + packageNames.size() + ", turboMode=" + turboMode
            );
        }
        return started;
    }

    @NonNull
    public static Map<String, Long> readRecentAppsSnapshot(@NonNull Context context) {
        SharedPreferences settingsPrefs = context.getSharedPreferences(CloudSyncManager.PREFS_SETTINGS, Context.MODE_PRIVATE);
        String serialized = settingsPrefs.getString(KEY_ACCESSIBILITY_RECENT_APPS, "");
        Map<String, Long> snapshot = parseRecentAppsSnapshot(serialized);
        pruneRecentAppsSnapshot(snapshot, System.currentTimeMillis());
        return snapshot;
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        Log.i(STOP_APPS_LOG_TAG, "Accessibility service connected.");
    }

    @Override
    public void onAccessibilityEvent(@NonNull AccessibilityEvent event) {
        int eventType = event.getEventType();
        if (eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                && eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
                && eventType != AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
            return;
        }

        recordRecentAppFromEvent(event);

        if (forceStopInProgress) {
            if (forceStopTransitionInProgress) {
                return;
            }
            processForceStopStep();
            return;
        }

        if (clearRecentsInProgress) {
            tryClearAllFromRecents();
        }
    }

    @Override
    public void onInterrupt() {
        // No-op
    }

    @Override
    public boolean onUnbind(Intent intent) {
        if (instance == this) {
            instance = null;
        }
        Log.w(STOP_APPS_LOG_TAG, "Accessibility service unbound. Resetting all states.");
        flushRecentAppsSnapshot();
        resetAllStates();
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        if (instance == this) {
            instance = null;
        }
        Log.w(STOP_APPS_LOG_TAG, "Accessibility service destroyed. Resetting all states.");
        flushRecentAppsSnapshot();
        resetAllStates();
        super.onDestroy();
    }

    private synchronized boolean startClearRecentsFlow() {
        if (forceStopInProgress) {
            Log.w(STOP_APPS_LOG_TAG, "Cannot start recents clear while force-stop flow is active.");
            return false;
        }

        clearRecentsInProgress = true;
        clearAttempts = 0;
        mainHandler.removeCallbacks(clearRecentsRunnable);

        boolean recentsOpened = performGlobalAction(GLOBAL_ACTION_RECENTS);
        Log.i(STOP_APPS_LOG_TAG, "Clear recents requested. openRecentsResult=" + recentsOpened);
        if (!recentsOpened) {
            clearRecentsInProgress = false;
            return false;
        }

        mainHandler.postDelayed(clearRecentsRunnable, INITIAL_CLEAR_DELAY_MS);
        return true;
    }

    private synchronized void tryClearAllFromRecents() {
        if (!clearRecentsInProgress) {
            return;
        }

        boolean clicked = clickClearAllNode();
        if (clicked) {
            Log.i(STOP_APPS_LOG_TAG, "Clear recents succeeded.");
            clearRecentsInProgress = false;
            mainHandler.removeCallbacks(clearRecentsRunnable);
            mainHandler.postDelayed(() -> performGlobalAction(GLOBAL_ACTION_HOME), 220L);
            return;
        }

        clearAttempts++;
        if (clearAttempts >= MAX_CLEAR_ATTEMPTS) {
            Log.w(STOP_APPS_LOG_TAG, "Clear recents exhausted retries without finding clear-all control.");
            clearRecentsInProgress = false;
            mainHandler.removeCallbacks(clearRecentsRunnable);
            return;
        }

        mainHandler.postDelayed(clearRecentsRunnable, CLEAR_RETRY_DELAY_MS);
    }

    private synchronized boolean startForceStopFlow(@NonNull List<String> packageNames, boolean turboMode) {
        if (forceStopInProgress) {
            Log.w(STOP_APPS_LOG_TAG, "Force-stop request rejected because another flow is already running.");
            return false;
        }

        configureForceStopSpeed(turboMode);

        Set<String> uniquePackages = new LinkedHashSet<>();
        Set<String> whitelistedPackages = getWhitelistedPackages();
        String ownPackage = getPackageName();
        for (String packageName : packageNames) {
            String cleanedPackage = sanitizePackageName(packageName);
            if (TextUtils.isEmpty(cleanedPackage) || ownPackage.equals(cleanedPackage)) {
                continue;
            }
            if (whitelistedPackages.contains(cleanedPackage)) {
                continue;
            }
            if (!isValidPackageName(cleanedPackage)) {
                continue;
            }
            uniquePackages.add(cleanedPackage);
        }

        if (uniquePackages.isEmpty()) {
            Log.w(STOP_APPS_LOG_TAG, "Force-stop request produced no valid packages after filtering.");
            return false;
        }

        Log.i(
                STOP_APPS_LOG_TAG,
                "Force-stop flow accepted. requested=" + packageNames.size()
                        + ", validTargets=" + uniquePackages.size()
                        + ", turboMode=" + turboMode
        );

        clearRecentsInProgress = false;
        mainHandler.removeCallbacks(clearRecentsRunnable);

        forceStopQueue.clear();
        forceStopQueue.addAll(uniquePackages);
        forceStopConfirmedPackages.clear();
        forceStopTargetCount = uniquePackages.size();
        forceStopConfirmedCount = 0;
        forceStopInProgress = true;
        forceStopTransitionInProgress = false;
        waitingForceStopConfirmation = false;
        forceStopReopenScheduled = false;
        forceStopAttempts = 0;
        currentForceStopPackage = null;
        currentForceStopStartedAtMs = 0L;
        currentForceStopClickAtMs = 0L;

        mainHandler.removeCallbacks(forceStopRunnable);
        mainHandler.removeCallbacks(reopenCurrentPackageRunnable);
        mainHandler.post(this::openNextPackageDetails);
        return true;
    }

    private synchronized void openNextPackageDetails() {
        if (!forceStopInProgress) {
            return;
        }

        forceStopTransitionInProgress = false;

        if (forceStopQueue.isEmpty()) {
            Log.i(STOP_APPS_LOG_TAG, "Force-stop queue drained. Finishing flow.");
            finishForceStopFlow();
            return;
        }

        String packageName = forceStopQueue.pollFirst();
        packageName = sanitizePackageName(packageName);
        if (TextUtils.isEmpty(packageName) || !isValidPackageName(packageName)) {
            Log.w(STOP_APPS_LOG_TAG, "Skipping invalid package in queue.");
            scheduleNextForceStopPackage(forceStopNextDelayMs);
            return;
        }

        currentForceStopPackage = packageName;
        currentForceStopStartedAtMs = System.currentTimeMillis();
        currentForceStopClickAtMs = 0L;
        lastAttemptIncrementMs = 0L;
        waitingForceStopConfirmation = false;
        forceStopAttempts = 0;

        try {
            Intent intent = buildAppDetailsIntent(packageName);
            if (intent.resolveActivity(getPackageManager()) == null) {
                Log.w(STOP_APPS_LOG_TAG, "App details intent not resolvable for " + packageName);
                scheduleNextForceStopPackage(forceStopNextDelayMs);
                return;
            }
            startActivity(intent);
            Log.i(
                    STOP_APPS_LOG_TAG,
                    "Opened app details for " + packageName + ". Remaining queue=" + forceStopQueue.size()
            );
            mainHandler.postDelayed(forceStopRunnable, forceStopOpenDelayMs);
        } catch (Throwable ignored) {
            Log.w(STOP_APPS_LOG_TAG, "Failed to open app details for " + packageName + ". Moving to next target.");
            scheduleNextForceStopPackage(forceStopNextDelayMs);
        }
    }

    private synchronized void processForceStopStep() {
        if (!forceStopInProgress) {
            return;
        }
        if (forceStopTransitionInProgress || TextUtils.isEmpty(currentForceStopPackage)) {
            return;
        }
        if (forceStopReopenScheduled) {
            return;
        }

        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) {
            retryForceStopStep();
            return;
        }

        try {
            if (isCurrentPackageTimedOut()) {
                Log.w(
                        STOP_APPS_LOG_TAG,
                        "Package timeout reached for " + currentForceStopPackage + ". Moving to next target."
                );
                waitingForceStopConfirmation = false;
                forceStopAttempts = 0;
                scheduleNextForceStopPackage(forceStopNextDelayMs);
                return;
            }

            CharSequence windowPackage = root.getPackageName();
            boolean likelyForceStopWindow = isLikelyForceStopWindow(windowPackage);
            boolean hasForceStopUiHints = hasForceStopUiHints(root);
            if (!likelyForceStopWindow && !hasForceStopUiHints) {
                long now = System.currentTimeMillis();
                if (now - lastAttemptIncrementMs >= 300) {
                    forceStopAttempts++;
                    lastAttemptIncrementMs = now;
                }

                if (forceStopAttempts >= MAX_FORCE_STOP_STEP_ATTEMPTS) {
                    Log.w(
                            STOP_APPS_LOG_TAG,
                            "Not in force-stop window after retries for " + currentForceStopPackage
                    );
                    waitingForceStopConfirmation = false;
                    forceStopAttempts = 0;
                    scheduleNextForceStopPackage(forceStopNextDelayMs);
                    return;
                }
                Log.i(
                        STOP_APPS_LOG_TAG,
                        "Window mismatch while processing " + currentForceStopPackage
                                + ". windowPkg=" + (windowPackage != null ? windowPackage : "null")
                                + ", attempt=" + forceStopAttempts
                );
                scheduleReopenCurrentPackageDetails();
                return;
            }

            forceStopAttempts = 0;
            lastAttemptIncrementMs = 0;

            if (!waitingForceStopConfirmation) {
                if (hasDisabledForceStopControl(root)) {
                    Log.i(
                            STOP_APPS_LOG_TAG,
                            "Force-stop control already disabled for " + currentForceStopPackage + ". Marking as done."
                    );
                    confirmCurrentPackageAndAdvance();
                    return;
                }

                boolean clickedForceStop = clickByViewIdCandidates(root, FORCE_STOP_VIEW_ID_CANDIDATES, true)
                        || clickByTextCandidates(root, FORCE_STOP_TEXT_CANDIDATES, true)
                        || clickByKeywordScan(root, FORCE_STOP_KEYWORDS, true);
                if (clickedForceStop) {
                    currentForceStopClickAtMs = System.currentTimeMillis();
                    waitingForceStopConfirmation = true;
                    forceStopAttempts = 0;
                    lastAttemptIncrementMs = 0;
                    Log.i(STOP_APPS_LOG_TAG, "Clicked force-stop button for " + currentForceStopPackage);
                    mainHandler.postDelayed(forceStopRunnable, forceStopAfterClickDelayMs);
                    return;
                }

                long now = System.currentTimeMillis();
                if (now - lastAttemptIncrementMs >= 300) {
                    forceStopAttempts++;
                    lastAttemptIncrementMs = now;
                }
                retryForceStopStep();
                return;
            }

            if (hasDisabledForceStopControl(root)) {
                Log.i(
                        STOP_APPS_LOG_TAG,
                        "Force-stop confirmed (disabled control) for " + currentForceStopPackage
                );
                confirmCurrentPackageAndAdvance();
                return;
            }

            if (currentForceStopClickAtMs > 0L) {
                long elapsedSinceForceStopClick = System.currentTimeMillis() - currentForceStopClickAtMs;
                if (elapsedSinceForceStopClick < forceStopConfirmMinDelayMs) {
                    long waitMore = forceStopConfirmMinDelayMs - elapsedSinceForceStopClick;
                    mainHandler.postDelayed(forceStopRunnable, Math.max(waitMore, forceStopRetryDelayMs));
                    return;
                }
            }

            boolean clickedConfirm = clickByViewIdCandidates(root, FORCE_STOP_CONFIRM_VIEW_ID_CANDIDATES, true)
                    || clickByTextCandidates(root, FORCE_STOP_CONFIRM_TEXT_CANDIDATES, true)
                    || clickByKeywordScan(root, CONFIRM_KEYWORDS, true);
            if (clickedConfirm) {
                Log.i(STOP_APPS_LOG_TAG, "Clicked confirmation button for " + currentForceStopPackage + ". Advancing immediately.");
                confirmCurrentPackageAndAdvance();
                return;
            }

            retryForceStopStep();
        } finally {
            root.recycle();
        }
    }

    private boolean isLikelyForceStopWindow(@Nullable CharSequence packageName) {
        if (TextUtils.isEmpty(packageName)) {
            return false;
        }

        String pkg = packageName.toString().toLowerCase(Locale.ROOT);
        return "android".equals(pkg)
                || pkg.contains("settings")
                || pkg.contains("securitycenter")
            || pkg.contains("miui")
                || pkg.contains("systemmanager")
            || pkg.contains("samsung")
                || pkg.contains("devicecare")
                || pkg.contains("packageinstaller")
                || pkg.contains("permissioncontroller");
    }

    private boolean isCurrentPackageTimedOut() {
        if (currentForceStopStartedAtMs <= 0L) {
            return false;
        }
        return System.currentTimeMillis() - currentForceStopStartedAtMs >= forceStopPerPackageTimeoutMs;
    }

    private void confirmCurrentPackageAndAdvance() {
        if (!TextUtils.isEmpty(currentForceStopPackage)) {
            forceStopConfirmedPackages.add(currentForceStopPackage);
            forceStopConfirmedCount = forceStopConfirmedPackages.size();
        }
        currentForceStopClickAtMs = 0L;
        waitingForceStopConfirmation = false;
        forceStopAttempts = 0;
        scheduleNextForceStopPackage(forceStopNextDelayMs);
    }

    private void reopenCurrentPackageDetails() {
        if (TextUtils.isEmpty(currentForceStopPackage)) {
            retryForceStopStep();
            return;
        }

        try {
            Intent intent = buildAppDetailsIntent(currentForceStopPackage);
            if (intent.resolveActivity(getPackageManager()) == null) {
                Log.w(STOP_APPS_LOG_TAG, "Reopen skipped, app details not resolvable for " + currentForceStopPackage);
                retryForceStopStep();
                return;
            }
            mainHandler.removeCallbacks(forceStopRunnable);
            startActivity(intent);
            Log.i(STOP_APPS_LOG_TAG, "Reopening app details for " + currentForceStopPackage);
            mainHandler.postDelayed(forceStopRunnable, forceStopOpenDelayMs);
        } catch (Throwable ignored) {
            Log.w(STOP_APPS_LOG_TAG, "Failed reopening app details for " + currentForceStopPackage);
            retryForceStopStep();
        }
    }

    private void scheduleReopenCurrentPackageDetails() {
        if (forceStopReopenScheduled) {
            return;
        }

        forceStopReopenScheduled = true;
        mainHandler.removeCallbacks(reopenCurrentPackageRunnable);
        mainHandler.postDelayed(reopenCurrentPackageRunnable, forceStopRetryDelayMs);
    }

    private void configureForceStopSpeed(boolean turboMode) {
        if (turboMode) {
            forceStopOpenDelayMs = TURBO_FORCE_STOP_OPEN_DELAY_MS;
            forceStopRetryDelayMs = TURBO_FORCE_STOP_RETRY_DELAY_MS;
            forceStopNextDelayMs = TURBO_FORCE_STOP_NEXT_DELAY_MS;
            forceStopAfterClickDelayMs = TURBO_FORCE_STOP_AFTER_CLICK_DELAY_MS;
            forceStopConfirmMinDelayMs = TURBO_FORCE_STOP_CONFIRM_MIN_DELAY_MS;
            forceStopConfirmVerifyDelayMs = TURBO_FORCE_STOP_CONFIRM_VERIFY_DELAY_MS;
            forceStopPerPackageTimeoutMs = TURBO_FORCE_STOP_PER_PACKAGE_TIMEOUT_MS;
            return;
        }

        forceStopOpenDelayMs = NORMAL_FORCE_STOP_OPEN_DELAY_MS;
        forceStopRetryDelayMs = NORMAL_FORCE_STOP_RETRY_DELAY_MS;
        forceStopNextDelayMs = NORMAL_FORCE_STOP_NEXT_DELAY_MS;
        forceStopAfterClickDelayMs = NORMAL_FORCE_STOP_AFTER_CLICK_DELAY_MS;
        forceStopConfirmMinDelayMs = NORMAL_FORCE_STOP_CONFIRM_MIN_DELAY_MS;
        forceStopConfirmVerifyDelayMs = NORMAL_FORCE_STOP_CONFIRM_VERIFY_DELAY_MS;
        forceStopPerPackageTimeoutMs = NORMAL_FORCE_STOP_PER_PACKAGE_TIMEOUT_MS;
    }

    private boolean hasDisabledForceStopControl(@NonNull AccessibilityNodeInfo root) {
        return hasDisabledNodeByViewIdCandidates(root, FORCE_STOP_VIEW_ID_CANDIDATES)
                || hasDisabledNodeByTextCandidates(root, FORCE_STOP_TEXT_CANDIDATES);
    }

    private boolean hasForceStopUiHints(@NonNull AccessibilityNodeInfo root) {
        return hasAnyNodeByViewIdCandidates(root, FORCE_STOP_VIEW_ID_CANDIDATES)
                || hasAnyNodeByTextCandidates(root, FORCE_STOP_TEXT_CANDIDATES)
                || hasAnyNodeByViewIdCandidates(root, FORCE_STOP_CONFIRM_VIEW_ID_CANDIDATES)
                || hasAnyNodeByTextCandidates(root, FORCE_STOP_CONFIRM_TEXT_CANDIDATES)
                || hasKeywordMatch(root, FORCE_STOP_KEYWORDS)
                || hasKeywordMatch(root, CONFIRM_KEYWORDS);
    }

    private boolean hasAnyNodeByViewIdCandidates(
            @NonNull AccessibilityNodeInfo root,
            @NonNull String[] viewIdCandidates
    ) {
        for (String viewId : viewIdCandidates) {
            List<AccessibilityNodeInfo> nodes = null;
            try {
                nodes = root.findAccessibilityNodeInfosByViewId(viewId);
                if (nodes != null && !nodes.isEmpty()) {
                    return true;
                }
            } catch (Throwable ignored) {
                // Algunos OEM restringen la búsqueda por view id.
            } finally {
                recycleNodes(nodes);
            }
        }
        return false;
    }

    private boolean hasAnyNodeByTextCandidates(
            @NonNull AccessibilityNodeInfo root,
            @NonNull String[] textCandidates
    ) {
        for (String candidate : textCandidates) {
            List<AccessibilityNodeInfo> nodes = null;
            try {
                nodes = root.findAccessibilityNodeInfosByText(candidate);
                if (nodes != null && !nodes.isEmpty()) {
                    return true;
                }
            } catch (Throwable ignored) {
                // Si falla la consulta textual, seguimos con el siguiente candidato.
            } finally {
                recycleNodes(nodes);
            }
        }
        return false;
    }

    private boolean hasKeywordMatch(
            @NonNull AccessibilityNodeInfo root,
            @NonNull String[] keywords
    ) {
        if (matchesAnyKeyword(root.getText(), keywords)
                || matchesAnyKeyword(root.getContentDescription(), keywords)) {
            return true;
        }

        for (int i = 0; i < root.getChildCount(); i++) {
            AccessibilityNodeInfo child = root.getChild(i);
            if (child == null) {
                continue;
            }
            try {
                if (hasKeywordMatch(child, keywords)) {
                    return true;
                }
            } finally {
                child.recycle();
            }
        }

        return false;
    }

    private boolean hasDisabledNodeByViewIdCandidates(
            @NonNull AccessibilityNodeInfo root,
            @NonNull String[] viewIdCandidates
    ) {
        for (String viewId : viewIdCandidates) {
            List<AccessibilityNodeInfo> nodes = null;
            try {
                nodes = root.findAccessibilityNodeInfosByViewId(viewId);
                if (containsDisabledNode(nodes)) {
                    return true;
                }
            } catch (Throwable ignored) {
                // Algunos OEM restringen la búsqueda por view id.
            } finally {
                recycleNodes(nodes);
            }
        }
        return false;
    }

    private boolean hasDisabledNodeByTextCandidates(
            @NonNull AccessibilityNodeInfo root,
            @NonNull String[] textCandidates
    ) {
        for (String candidate : textCandidates) {
            List<AccessibilityNodeInfo> nodes = null;
            try {
                nodes = root.findAccessibilityNodeInfosByText(candidate);
                if (containsDisabledNode(nodes)) {
                    return true;
                }
            } catch (Throwable ignored) {
                // Si falla la consulta textual, continuamos con el siguiente candidato.
            } finally {
                recycleNodes(nodes);
            }
        }
        return false;
    }

    private boolean containsDisabledNode(@Nullable List<AccessibilityNodeInfo> nodes) {
        if (nodes == null || nodes.isEmpty()) {
            return false;
        }

        for (AccessibilityNodeInfo node : nodes) {
            if (node != null && !node.isEnabled()) {
                return true;
            }
        }
        return false;
    }

    @NonNull
    private Intent buildAppDetailsIntent(@NonNull String packageName) {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        intent.setData(Uri.fromParts("package", packageName, null));
        intent.putExtra(Intent.EXTRA_PACKAGE_NAME, packageName);
        intent.putExtra(Settings.EXTRA_APP_PACKAGE, packageName);
        intent.putExtra("package", packageName);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return intent;
    }

    private boolean isValidPackageName(@NonNull String packageName) {
        return PACKAGE_NAME_PATTERN.matcher(packageName).matches();
    }

    @Nullable
    private String sanitizePackageName(@Nullable String packageName) {
        if (packageName == null) {
            return null;
        }
        return packageName.trim();
    }

    private void recordRecentAppFromEvent(@NonNull AccessibilityEvent event) {
        CharSequence packageValue = event.getPackageName();
        if (packageValue == null) {
            return;
        }

        String packageName = sanitizePackageName(packageValue.toString());
        if (TextUtils.isEmpty(packageName)
                || !isValidPackageName(packageName)
                || shouldIgnoreRecentApp(packageName)) {
            return;
        }

        long now = System.currentTimeMillis();
        synchronized (this) {
            ensureRecentAppsLoadedLocked();
            recentAppTimestampsByPackage.put(packageName, now);
            pruneRecentAppsSnapshot(recentAppTimestampsByPackage, now);
            if (now - lastRecentAppsPersistAtMs >= RECENT_APPS_PERSIST_DEBOUNCE_MS) {
                persistRecentAppsSnapshotLocked(now);
            }
        }
    }

    private boolean shouldIgnoreRecentApp(@NonNull String packageName) {
        if (getPackageName().equals(packageName)) {
            return true;
        }

        String normalized = packageName.toLowerCase(Locale.ROOT);
        return normalized.equals("android")
                || normalized.startsWith("com.android.systemui")
                || normalized.startsWith("com.android.settings")
                || normalized.contains("launcher")
                || normalized.contains("permissioncontroller")
                || normalized.contains("packageinstaller")
                || normalized.contains("securitycenter")
                || normalized.contains("devicecare");
    }

    private synchronized void flushRecentAppsSnapshot() {
        if (!recentAppsLoaded) {
            return;
        }
        persistRecentAppsSnapshotLocked(System.currentTimeMillis());
    }

    private void ensureRecentAppsLoadedLocked() {
        if (recentAppsLoaded) {
            return;
        }

        recentAppTimestampsByPackage.clear();
        recentAppTimestampsByPackage.putAll(readRecentAppsSnapshot(this));
        recentAppsLoaded = true;
        lastRecentAppsPersistAtMs = System.currentTimeMillis();
    }

    private void persistRecentAppsSnapshotLocked(long now) {
        SharedPreferences settingsPrefs = getSharedPreferences(CloudSyncManager.PREFS_SETTINGS, MODE_PRIVATE);
        String serialized = serializeRecentAppsSnapshot(recentAppTimestampsByPackage);
        settingsPrefs.edit().putString(KEY_ACCESSIBILITY_RECENT_APPS, serialized).apply();
        lastRecentAppsPersistAtMs = now;
    }

    @NonNull
    private static Map<String, Long> parseRecentAppsSnapshot(@Nullable String serialized) {
        if (TextUtils.isEmpty(serialized)) {
            return new LinkedHashMap<>();
        }

        Map<String, Long> result = new LinkedHashMap<>();
        String[] lines = serialized.split("\\n");
        for (String line : lines) {
            if (TextUtils.isEmpty(line)) {
                continue;
            }
            int separatorIndex = line.lastIndexOf('|');
            if (separatorIndex <= 0 || separatorIndex >= line.length() - 1) {
                continue;
            }

            String packageName = line.substring(0, separatorIndex).trim();
            if (TextUtils.isEmpty(packageName) || !PACKAGE_NAME_PATTERN.matcher(packageName).matches()) {
                continue;
            }

            String timestampValue = line.substring(separatorIndex + 1).trim();
            long timestamp;
            try {
                timestamp = Long.parseLong(timestampValue);
            } catch (NumberFormatException ignored) {
                continue;
            }

            if (timestamp <= 0L) {
                continue;
            }
            result.put(packageName, timestamp);
        }

        return result;
    }

    @NonNull
    private static String serializeRecentAppsSnapshot(@NonNull Map<String, Long> snapshot) {
        if (snapshot.isEmpty()) {
            return "";
        }

        StringBuilder builder = new StringBuilder(snapshot.size() * 24);
        for (Map.Entry<String, Long> entry : snapshot.entrySet()) {
            String packageName = entry.getKey();
            Long timestampValue = entry.getValue();
            if (TextUtils.isEmpty(packageName) || timestampValue == null || timestampValue <= 0L) {
                continue;
            }

            if (builder.length() > 0) {
                builder.append('\n');
            }
            builder.append(packageName)
                    .append('|')
                    .append(timestampValue);
        }
        return builder.toString();
    }

    private static void pruneRecentAppsSnapshot(@NonNull Map<String, Long> snapshot, long now) {
        if (snapshot.isEmpty()) {
            return;
        }

        List<String> packagesToRemove = new ArrayList<>();
        for (Map.Entry<String, Long> entry : snapshot.entrySet()) {
            String packageName = entry.getKey();
            Long timestampValue = entry.getValue();
            long timestamp = timestampValue != null ? timestampValue : 0L;
            if (TextUtils.isEmpty(packageName)
                    || timestamp <= 0L
                    || now - timestamp > RECENT_APPS_TTL_MS) {
                packagesToRemove.add(packageName);
            }
        }
        for (String packageName : packagesToRemove) {
            snapshot.remove(packageName);
        }

        if (snapshot.size() <= MAX_RECENT_APPS) {
            return;
        }

        List<Map.Entry<String, Long>> ordered = new ArrayList<>(snapshot.entrySet());
        ordered.sort((left, right) -> Long.compare(right.getValue(), left.getValue()));

        snapshot.clear();
        int keepCount = Math.min(MAX_RECENT_APPS, ordered.size());
        for (int i = 0; i < keepCount; i++) {
            Map.Entry<String, Long> entry = ordered.get(i);
            snapshot.put(entry.getKey(), entry.getValue());
        }
    }

    private synchronized void retryForceStopStep() {
        if (!forceStopInProgress) {
            return;
        }

        forceStopAttempts++;
        int maxAttempts = waitingForceStopConfirmation
                ? MAX_CONFIRM_STEP_ATTEMPTS
                : MAX_FORCE_STOP_STEP_ATTEMPTS;

        if (forceStopAttempts >= maxAttempts) {
            Log.w(
                    STOP_APPS_LOG_TAG,
                    "Retry limit reached for " + currentForceStopPackage + ", waitingConfirm=" + waitingForceStopConfirmation
            );
            waitingForceStopConfirmation = false;
            forceStopAttempts = 0;
            scheduleNextForceStopPackage(forceStopNextDelayMs);
            return;
        }

        mainHandler.postDelayed(forceStopRunnable, forceStopRetryDelayMs);
    }

    private void scheduleNextForceStopPackage(long delayMs) {
        String previousPackage = currentForceStopPackage;
        forceStopTransitionInProgress = true;
        forceStopReopenScheduled = false;
        currentForceStopPackage = null;
        currentForceStopStartedAtMs = 0L;
        currentForceStopClickAtMs = 0L;
        mainHandler.removeCallbacks(forceStopRunnable);
        mainHandler.removeCallbacks(reopenCurrentPackageRunnable);
        Log.i(
            STOP_APPS_LOG_TAG,
            "Scheduling next package. Previous=" + previousPackage
                + ", remainingQueue=" + forceStopQueue.size()
                + ", delayMs=" + delayMs
        );
        mainHandler.post(() -> performGlobalAction(GLOBAL_ACTION_BACK));
        mainHandler.postDelayed(() -> performGlobalAction(GLOBAL_ACTION_HOME), FORCE_STOP_TRANSITION_HOME_DELAY_MS);
        mainHandler.postDelayed(
                this::openNextPackageDetails,
                delayMs + FORCE_STOP_TRANSITION_BACK_DELAY_MS + FORCE_STOP_TRANSITION_HOME_DELAY_MS
        );
    }

    private synchronized void finishForceStopFlow() {
        if (!forceStopInProgress) {
            return;
        }

        Log.i(
                STOP_APPS_LOG_TAG,
                "Finishing force-stop flow. targetCount=" + forceStopTargetCount
                        + ", confirmedCount=" + forceStopConfirmedCount
                        + ", confirmedPackages=" + forceStopConfirmedPackages.size()
        );

        forceStopInProgress = false;
        forceStopTransitionInProgress = false;
        waitingForceStopConfirmation = false;
        forceStopReopenScheduled = false;
        forceStopAttempts = 0;
        forceStopQueue.clear();
        mainHandler.removeCallbacks(forceStopRunnable);
        mainHandler.removeCallbacks(reopenCurrentPackageRunnable);
        currentForceStopPackage = null;
        currentForceStopStartedAtMs = 0L;
        currentForceStopClickAtMs = 0L;

        Intent resultIntent = new Intent(ACTION_FORCE_STOP_AUTOMATION_FINISHED);
        resultIntent.setPackage(getPackageName());
        resultIntent.putExtra(EXTRA_TARGET_COUNT, forceStopTargetCount);
        resultIntent.putExtra(EXTRA_CONFIRMED_COUNT, forceStopConfirmedCount);
        resultIntent.putStringArrayListExtra(EXTRA_CONFIRMED_PACKAGES, new ArrayList<>(forceStopConfirmedPackages));
        sendBroadcast(resultIntent);

        forceStopTargetCount = 0;
        forceStopConfirmedCount = 0;
        forceStopConfirmedPackages.clear();

        mainHandler.postDelayed(() -> performGlobalAction(GLOBAL_ACTION_HOME), 240L);
    }

    @NonNull
    private Set<String> getWhitelistedPackages() {
        SharedPreferences settingsPrefs = getSharedPreferences(CloudSyncManager.PREFS_SETTINGS, MODE_PRIVATE);
        Set<String> values = settingsPrefs.getStringSet(CloudSyncManager.KEY_APPS_WHITELIST_PACKAGES, null);
        if (values == null || values.isEmpty()) {
            return java.util.Collections.emptySet();
        }
        return new LinkedHashSet<>(values);
    }

    private boolean clickClearAllNode() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) {
            return false;
        }

        try {
            if (clickByViewIdCandidates(root, CLEAR_ALL_VIEW_ID_CANDIDATES, true)) {
                return true;
            }
            return clickByTextCandidates(root, CLEAR_ALL_TEXT_CANDIDATES, true);
        } finally {
            root.recycle();
        }
    }

    private boolean clickByViewIdCandidates(
            @NonNull AccessibilityNodeInfo root,
            @NonNull String[] viewIdCandidates,
            boolean requireEnabled
    ) {
        for (String viewId : viewIdCandidates) {
            List<AccessibilityNodeInfo> nodes = null;
            try {
                nodes = root.findAccessibilityNodeInfosByViewId(viewId);
                if (clickFirstClickable(nodes, requireEnabled)) {
                    return true;
                }
            } catch (Throwable ignored) {
                // Algunos launchers bloquean búsqueda por viewId.
            } finally {
                recycleNodes(nodes);
            }
        }
        return false;
    }

    private boolean clickByTextCandidates(
            @NonNull AccessibilityNodeInfo root,
            @NonNull String[] textCandidates,
            boolean requireEnabled
    ) {
        for (String candidate : textCandidates) {
            List<AccessibilityNodeInfo> nodes = null;
            try {
                nodes = root.findAccessibilityNodeInfosByText(candidate);
                if (clickFirstClickable(nodes, requireEnabled)) {
                    return true;
                }
            } catch (Throwable ignored) {
                // Si falla la consulta textual, seguimos con el siguiente candidato.
            } finally {
                recycleNodes(nodes);
            }
        }
        return false;
    }

    private boolean clickByKeywordScan(
            @NonNull AccessibilityNodeInfo root,
            @NonNull String[] keywords,
            boolean requireEnabled
    ) {
        if (matchesAnyKeyword(root.getText(), keywords)
                || matchesAnyKeyword(root.getContentDescription(), keywords)) {
            if (clickNodeOrAncestor(root, requireEnabled)) {
                return true;
            }
        }

        for (int i = 0; i < root.getChildCount(); i++) {
            AccessibilityNodeInfo child = root.getChild(i);
            if (child == null) {
                continue;
            }
            try {
                if (clickByKeywordScan(child, keywords, requireEnabled)) {
                    return true;
                }
            } finally {
                child.recycle();
            }
        }

        return false;
    }

    private boolean matchesAnyKeyword(@Nullable CharSequence value, @NonNull String[] keywords) {
        if (value == null || value.length() == 0) {
            return false;
        }

        String text = value.toString().toLowerCase(Locale.ROOT);
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private boolean clickFirstClickable(@Nullable List<AccessibilityNodeInfo> nodes, boolean requireEnabled) {
        if (nodes == null || nodes.isEmpty()) {
            return false;
        }

        for (AccessibilityNodeInfo node : nodes) {
            if (node == null) {
                continue;
            }
            if (clickNodeOrAncestor(node, requireEnabled)) {
                return true;
            }
        }
        return false;
    }

    private boolean clickNodeOrAncestor(@NonNull AccessibilityNodeInfo node, boolean requireEnabled) {
        AccessibilityNodeInfo current = node;
        while (current != null) {
            if (current.isClickable() && (!requireEnabled || current.isEnabled())) {
                boolean clicked = current.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                if (!clicked) {
                    // Algunos OEM no respetan ACTION_CLICK; usamos tap real como respaldo.
                    clicked = tapNodeCenter(current);
                }
                if (current != node) {
                    current.recycle();
                }
                return clicked;
            }

            AccessibilityNodeInfo parent = current.getParent();
            if (current != node) {
                current.recycle();
            }
            current = parent;
        }
        return false;
    }

    private boolean tapNodeCenter(@NonNull AccessibilityNodeInfo node) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            return false;
        }

        Rect bounds = new Rect();
        node.getBoundsInScreen(bounds);
        if (bounds.isEmpty()) {
            return false;
        }

        float centerX = bounds.exactCenterX();
        float centerY = bounds.exactCenterY();
        if (!Float.isFinite(centerX) || !Float.isFinite(centerY)) {
            return false;
        }

        Path path = new Path();
        path.moveTo(centerX, centerY);

        GestureDescription.StrokeDescription stroke =
                new GestureDescription.StrokeDescription(path, 0L, TAP_FALLBACK_DURATION_MS);
        GestureDescription gesture = new GestureDescription.Builder()
                .addStroke(stroke)
                .build();
        return dispatchGesture(gesture, null, null);
    }

    private void recycleNodes(@Nullable List<AccessibilityNodeInfo> nodes) {
        if (nodes == null) {
            return;
        }
        for (AccessibilityNodeInfo node : nodes) {
            if (node != null) {
                node.recycle();
            }
        }
    }

    private synchronized void resetAllStates() {
        clearRecentsInProgress = false;
        clearAttempts = 0;

        forceStopInProgress = false;
        forceStopTransitionInProgress = false;
        waitingForceStopConfirmation = false;
        forceStopReopenScheduled = false;
        forceStopAttempts = 0;
        forceStopQueue.clear();
        forceStopTargetCount = 0;
        forceStopConfirmedCount = 0;
        forceStopConfirmedPackages.clear();
        currentForceStopPackage = null;
        currentForceStopStartedAtMs = 0L;
        currentForceStopClickAtMs = 0L;

        recentAppTimestampsByPackage.clear();
        recentAppsLoaded = false;
        lastRecentAppsPersistAtMs = 0L;

        mainHandler.removeCallbacksAndMessages(null);
    }
}
