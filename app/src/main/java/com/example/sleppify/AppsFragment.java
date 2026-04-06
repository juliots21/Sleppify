package com.example.sleppify;

import android.Manifest;
import android.app.ActivityManager;
import android.app.AppOpsManager;
import android.content.DialogInterface;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ResolveInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.StatFs;
import android.os.storage.StorageManager;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import androidx.appcompat.app.AlertDialog;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AppsFragment extends Fragment {

    private static final String STOP_APPS_LOG_TAG = "SleppifyStopApps";

    private static final long METRICS_REFRESH_MS = 1200L;
    private static final long RUNNING_EVENTS_LOOKBACK_MS = 45L * 60L * 1000L;
    private static final long VERIFICATION_LOOKBACK_MS = 12L * 60L * 60L * 1000L;
    private static final long RECENT_FORCE_STOP_HIDE_MS = 3L * 60L * 1000L;
    private static final long BACKGROUND_APPS_CACHE_TTL_MS = 25_000L;
    private static final long BACKGROUND_APPS_REFRESH_INTERVAL_MS = 12_000L;
    private static final long APPS_SUGGESTION_PREFETCH_MIN_INTERVAL_MS = 15_000L;
    private static final long FORCE_STOP_START_RETRY_INTERVAL_MS = 450L;
    private static final int FORCE_STOP_START_MAX_ATTEMPTS = 7;
    private static final long FORCE_STOP_TIMEOUT_BASE_MS = 12_000L;
    private static final long FORCE_STOP_TIMEOUT_PER_APP_MS = 18_000L;
    private static final long FORCE_STOP_TIMEOUT_MIN_MS = 45_000L;
    private static final long FORCE_STOP_TIMEOUT_MAX_MS = 8L * 60L * 1000L;
    private static final int MAX_TARGET_PACKAGES = 40;
    private static final int FALLBACK_LAUNCHABLE_APPS_LIMIT = 80;
    private static final int REQUEST_READ_EXTERNAL_STORAGE_PERMISSION = 4_201;
    private static final String ACTION_APPLY = "apply";
    private static final String ACTION_DISMISS = "dismiss";
    private static final long SYSTEM_CACHE_RECLAIM_REQUEST_BYTES = 96L * 1024L * 1024L;
    private static final String[] TEMP_FILE_SUFFIXES = {".tmp", ".temp", ".log", ".lck", ".pid"};
    private static final String[] JUNK_FILE_SUFFIXES = {".bak", ".old", ".dmp", ".dump", ".stacktrace", ".trace", ".chk"};
    private static final String[] TEMP_NAME_TOKENS = {"temp", "tmp", "session", "log"};
    private static final String[] JUNK_NAME_TOKENS = {"dump", "junk", "trash", "residual", "crash", "anr", "tombstone", "obsolete"};
    private static final String[] TEMP_DIR_NAMES = {"tmp", "temp", "logs", "log", "sessions", "cache"};
    private static final String[] JUNK_DIR_NAMES = {"crash", "crashes", "anr", "dump", "dumps", "junk", "trash", "tombstone", "residual", "reports"};
        private static final String[] SHARED_STORAGE_SCAN_RELATIVE_DIRS = {
            Environment.DIRECTORY_DOWNLOADS,
            Environment.DIRECTORY_DOCUMENTS,
            Environment.DIRECTORY_DCIM,
            Environment.DIRECTORY_PICTURES,
            Environment.DIRECTORY_MOVIES,
            Environment.DIRECTORY_MUSIC,
            Environment.DIRECTORY_PODCASTS,
            Environment.DIRECTORY_RECORDINGS,
            Environment.DIRECTORY_NOTIFICATIONS,
            Environment.DIRECTORY_RINGTONES,
            "Android/media"
        };

    private CircularProgressIndicator cpiRamUsage;
    private TextView tvRamUsagePercent;
    private TextView tvTotalRamValue;
    private TextView tvUsedRamValue;
    private TextView tvStorageUsageValue;
    private TextView tvThermalValue;
    private TextView tvThermalStatus;
    private ProgressBar progressStorageUsage;
    private ExtendedFloatingActionButton fabStopApps;
    private View cardAccessibilityRequest;
    private MaterialButton btnGrantAccessibility;
    private LinearLayout layoutPerformanceRow;
    private View cardStorageUsage;
    private View cardThermal;
    private View cardDeepClean;
    private View cardAppsSmartSuggestion;
    private TextView tvAppsSmartSuggestion;
    private MaterialButton btnAppsSuggestionApply;
    private MaterialButton btnAppsSuggestionDismiss;
    private LinearLayout layoutBackgroundAppsList;
    private TextView tvBackgroundAppsEmpty;
    private SharedPreferences settingsPrefs;

    private int defaultStorageCardMarginEnd;
    private int defaultThermalCardMarginStart;

    private ActivityManager activityManager;
    private final Handler metricsHandler = new Handler(Looper.getMainLooper());
    private boolean storageCardVisible = true;
    private boolean thermalCardVisible = true;
    private boolean showSystemInfoEnabled = true;
    private BroadcastReceiver forceStopAutomationReceiver;
    private boolean forceStopReceiverRegistered;
    private final Map<String, Long> recentlyForceStoppedByPackage = new HashMap<>();
    private final ExecutorService backgroundExecutor = Executors.newSingleThreadExecutor();
    private final Object backgroundAppsCacheLock = new Object();
    private List<BackgroundAppEntry> cachedBackgroundApps = Collections.emptyList();
    private boolean cachedBackgroundHasUsageAccess;
    private long cachedBackgroundBuiltAtMs;
    private int backgroundAppsRenderVersion;
    private long lastBackgroundAppsRefreshAtMs;
    private long lastAppsSuggestionPrefetchAtMs;
    private boolean forceStopActionInProgress;
    @Nullable
    private Runnable pendingForceStopStartRunnable;
    @Nullable
    private Runnable forceStopSafetyTimeoutRunnable;

    private final Runnable metricsRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isAdded()) {
                return;
            }
            updateRamMetrics();
            updateStorageMetrics();
            updateThermalMetrics();

            long now = System.currentTimeMillis();
            if (now - lastBackgroundAppsRefreshAtMs >= BACKGROUND_APPS_REFRESH_INTERVAL_MS) {
                lastBackgroundAppsRefreshAtMs = now;
                refreshBackgroundAppsVerificationList();
            }

            metricsHandler.postDelayed(this, METRICS_REFRESH_MS);
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_apps, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        activityManager = (ActivityManager) requireContext().getSystemService(Context.ACTIVITY_SERVICE);

        cpiRamUsage = view.findViewById(R.id.cpiRamUsage);
        tvRamUsagePercent = view.findViewById(R.id.tvRamUsagePercent);
        tvTotalRamValue = view.findViewById(R.id.tvTotalRamValue);
        tvUsedRamValue = view.findViewById(R.id.tvUsedRamValue);
        tvStorageUsageValue = view.findViewById(R.id.tvStorageUsageValue);
        tvThermalValue = view.findViewById(R.id.tvThermalValue);
        tvThermalStatus = view.findViewById(R.id.tvThermalStatus);
        progressStorageUsage = view.findViewById(R.id.progressStorageUsage);
        fabStopApps = view.findViewById(R.id.fabStopApps);
        cardAccessibilityRequest = view.findViewById(R.id.cardAccessibilityRequest);
        btnGrantAccessibility = view.findViewById(R.id.btnGrantAccessibility);
        layoutPerformanceRow = view.findViewById(R.id.layoutPerformanceRow);
        cardStorageUsage = view.findViewById(R.id.cardStorageUsage);
        cardThermal = view.findViewById(R.id.cardThermal);
        cardDeepClean = view.findViewById(R.id.cardDeepClean);
        cardAppsSmartSuggestion = view.findViewById(R.id.cardAppsSmartSuggestion);
        tvAppsSmartSuggestion = view.findViewById(R.id.tvAppsSmartSuggestion);
        btnAppsSuggestionApply = view.findViewById(R.id.btnAppsSuggestionApply);
        btnAppsSuggestionDismiss = view.findViewById(R.id.btnAppsSuggestionDismiss);
        layoutBackgroundAppsList = view.findViewById(R.id.layoutBackgroundAppsList);
        tvBackgroundAppsEmpty = view.findViewById(R.id.tvBackgroundAppsEmpty);
        settingsPrefs = requireContext().getSharedPreferences(CloudSyncManager.PREFS_SETTINGS, Context.MODE_PRIVATE);

        if (cardStorageUsage != null && cardStorageUsage.getLayoutParams() instanceof LinearLayout.LayoutParams) {
            LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) cardStorageUsage.getLayoutParams();
            defaultStorageCardMarginEnd = params.rightMargin;
        }
        if (cardThermal != null && cardThermal.getLayoutParams() instanceof LinearLayout.LayoutParams) {
            LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) cardThermal.getLayoutParams();
            defaultThermalCardMarginStart = params.leftMargin;
        }
        syncSystemInfoPreference();
        updatePerformanceRowLayout();

        if (fabStopApps != null) {
            fabStopApps.setOnClickListener(v -> runStopAppsAction());
        }
        if (btnGrantAccessibility != null) {
            btnGrantAccessibility.setOnClickListener(v -> showAccessibilityPermissionDialog());
        }
        if (cardDeepClean != null) {
            cardDeepClean.setOnClickListener(v -> showDeepCleanDialog());
        }
        setupAppsSuggestionActions();
        loadStoredAppsSuggestion();

        updateRamMetrics();
        updateStorageMetrics();
        updateThermalMetrics();
        refreshAccessibilityPermissionUi();
        refreshBackgroundAppsVerificationList();

        forceStopAutomationReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent == null) {
                    return;
                }

                finishForceStopUiFlow();
                if (!isAdded()) {
                    return;
                }

                int targetCount = intent.getIntExtra(AppAccessibilityService.EXTRA_TARGET_COUNT, 0);
                int confirmedCount = intent.getIntExtra(AppAccessibilityService.EXTRA_CONFIRMED_COUNT, 0);
                ArrayList<String> confirmedPackages = intent.getStringArrayListExtra(AppAccessibilityService.EXTRA_CONFIRMED_PACKAGES);
                Log.i(
                        STOP_APPS_LOG_TAG,
                        "Receiver finish: targetCount=" + targetCount
                                + ", confirmedCount=" + confirmedCount
                                + ", confirmedPackages=" + (confirmedPackages != null ? confirmedPackages.size() : 0)
                );
                registerRecentlyForceStoppedPackages(confirmedPackages);
                invalidateBackgroundAppsCache();

                Toast.makeText(
                        requireContext(),
                    getString(R.string.apps_force_stop_finished_toast, confirmedCount, targetCount),
                        Toast.LENGTH_LONG
                ).show();
                updateRamMetrics();
                refreshBackgroundAppsVerificationList();
            }
        };
    }

    @Override
    public void onResume() {
        super.onResume();
        metricsHandler.removeCallbacks(metricsRunnable);
        metricsHandler.post(metricsRunnable);
        refreshAccessibilityPermissionUi();
        refreshBackgroundAppsVerificationList();
        lastBackgroundAppsRefreshAtMs = System.currentTimeMillis();
        refreshAppsSuggestionState();
        syncSystemInfoPreference();
    }

    @Override
    public void onPause() {
        metricsHandler.removeCallbacks(metricsRunnable);
        super.onPause();
    }

    @Override
    public void onStart() {
        super.onStart();
        registerForceStopAutomationReceiver();
    }

    @Override
    public void onStop() {
        if (!forceStopActionInProgress) {
            unregisterForceStopAutomationReceiver();
        }
        super.onStop();
    }

    @Override
    public void onDestroyView() {
        finishForceStopUiFlow();
        unregisterForceStopAutomationReceiver();
        backgroundAppsRenderVersion++;
        forceStopAutomationReceiver = null;
        super.onDestroyView();
    }

    @Override
    public void onDestroy() {
        backgroundExecutor.shutdownNow();
        super.onDestroy();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQUEST_READ_EXTERNAL_STORAGE_PERMISSION || !isAdded()) {
            return;
        }

        boolean granted = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
        if (!granted) {
            Toast.makeText(requireContext(), getString(R.string.apps_storage_permission_denied_toast), Toast.LENGTH_SHORT).show();
        }
    }

    private void updateRamMetrics() {
        if (activityManager == null || cpiRamUsage == null) {
            return;
        }

        ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();
        activityManager.getMemoryInfo(memoryInfo);

        long total = Math.max(1L, memoryInfo.totalMem);
        long used = Math.max(0L, total - memoryInfo.availMem);
        int percent = (int) Math.round((used * 100d) / total);
        percent = Math.max(0, Math.min(100, percent));

        cpiRamUsage.setProgress(percent);
        if (tvRamUsagePercent != null) {
            tvRamUsagePercent.setText(String.format(Locale.getDefault(), "%d%%", percent));
        }
        if (tvTotalRamValue != null) {
            tvTotalRamValue.setText(formatGb(total));
        }
        if (tvUsedRamValue != null) {
            tvUsedRamValue.setText(formatGb(used));
        }
    }

    private void updateStorageMetrics() {
        if (!showSystemInfoEnabled) {
            setStorageCardVisible(false);
            return;
        }

        if (progressStorageUsage == null) {
            return;
        }

        StorageUsageSnapshot snapshot = readStorageUsageSnapshot();
        if (snapshot == null) {
            setStorageCardVisible(false);
            return;
        }

        setStorageCardVisible(true);
        progressStorageUsage.setProgress(snapshot.percent);
        if (tvStorageUsageValue != null) {
            tvStorageUsageValue.setText(String.format(Locale.getDefault(), "%d%%", snapshot.percent));
        }
    }

    private void updateThermalMetrics() {
        if (!showSystemInfoEnabled) {
            setThermalCardVisible(false);
            return;
        }

        Intent batteryIntent = requireContext().registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (batteryIntent == null) {
            setThermalCardVisible(false);
            return;
        }

        int tempTenths = batteryIntent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Integer.MIN_VALUE);
        if (tempTenths <= 0) {
            setThermalCardVisible(false);
            return;
        }

        float tempC = tempTenths / 10f;
        setThermalCardVisible(true);
        if (tvThermalValue != null) {
            tvThermalValue.setText(String.format(Locale.getDefault(), "%.1f°C", tempC));
        }
        if (tvThermalStatus != null) {
            String status;
            if (tempC <= 38f) {
                status = getString(R.string.apps_thermal_status_normal);
            } else if (tempC <= 44f) {
                status = getString(R.string.apps_thermal_status_warm);
            } else {
                status = getString(R.string.apps_thermal_status_hot);
            }
            tvThermalStatus.setText(status);
        }
    }

    private void setStorageCardVisible(boolean visible) {
        storageCardVisible = visible;
        if (cardStorageUsage != null) {
            cardStorageUsage.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
        updatePerformanceRowLayout();
    }

    private void setThermalCardVisible(boolean visible) {
        thermalCardVisible = visible;
        if (cardThermal != null) {
            cardThermal.setVisibility(visible ? View.VISIBLE : View.GONE);
        }

        updatePerformanceRowLayout();
    }

    private void updatePerformanceRowLayout() {
        if (layoutPerformanceRow == null) {
            return;
        }

        boolean showStorage = showSystemInfoEnabled && storageCardVisible && cardStorageUsage != null;
        boolean showThermal = showSystemInfoEnabled && thermalCardVisible && cardThermal != null;
        layoutPerformanceRow.setVisibility((showStorage || showThermal) ? View.VISIBLE : View.GONE);

        if (showStorage && cardStorageUsage != null && cardStorageUsage.getLayoutParams() instanceof LinearLayout.LayoutParams) {
            LinearLayout.LayoutParams storageParams = (LinearLayout.LayoutParams) cardStorageUsage.getLayoutParams();
            if (showThermal) {
                storageParams.width = 0;
                storageParams.weight = 1f;
                storageParams.rightMargin = defaultStorageCardMarginEnd;
            } else {
                storageParams.width = ViewGroup.LayoutParams.MATCH_PARENT;
                storageParams.weight = 0f;
                storageParams.rightMargin = 0;
            }
            cardStorageUsage.setLayoutParams(storageParams);
        }

        if (showThermal && cardThermal != null && cardThermal.getLayoutParams() instanceof LinearLayout.LayoutParams) {
            LinearLayout.LayoutParams thermalParams = (LinearLayout.LayoutParams) cardThermal.getLayoutParams();
            if (showStorage) {
                thermalParams.width = 0;
                thermalParams.weight = 1f;
                thermalParams.leftMargin = defaultThermalCardMarginStart;
            } else {
                thermalParams.width = ViewGroup.LayoutParams.MATCH_PARENT;
                thermalParams.weight = 0f;
                thermalParams.leftMargin = 0;
            }
            cardThermal.setLayoutParams(thermalParams);
        }

        layoutPerformanceRow.setWeightSum((showStorage && showThermal) ? 2f : 1f);
    }

    private void showDeepCleanDialog() {
        if (!isAdded()) {
            return;
        }

        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_deep_clean, null, false);
        CircularProgressIndicator ring = dialogView.findViewById(R.id.cpiDeepCleanProgress);
        TextView tvPercent = dialogView.findViewById(R.id.tvDeepCleanProgressPercent);
        TextView tvSpaceValue = dialogView.findViewById(R.id.tvDeepCleanSpaceValue);
        TextView tvCacheValue = dialogView.findViewById(R.id.tvDeepCleanCacheValue);
        TextView tvTempValue = dialogView.findViewById(R.id.tvDeepCleanTempValue);
        TextView tvJunkValue = dialogView.findViewById(R.id.tvDeepCleanJunkValue);
        MaterialButton btnConfirm = dialogView.findViewById(R.id.btnDeepCleanConfirm);
        MaterialButton btnCancel = dialogView.findViewById(R.id.btnDeepCleanCancel);

        Context appContext = requireContext().getApplicationContext();
        @Nullable StorageUsageSnapshot initialUsage = readStorageUsageSnapshot();
        bindDeepCleanDialogMetrics(
                ring,
                tvPercent,
                tvSpaceValue,
                tvCacheValue,
                tvTempValue,
                tvJunkValue,
                initialUsage,
                new DeepCleanStats(0L, 0L, 0L)
        );

        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setView(dialogView)
                .setCancelable(true)
                .create();

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        btnConfirm.setOnClickListener(v -> {
            if (!hasSharedStorageAccess(appContext)) {
                showStoragePermissionDialog();
                return;
            }

            btnConfirm.setEnabled(false);
            btnCancel.setEnabled(false);
            btnConfirm.setText(R.string.apps_deep_clean_running);

            backgroundExecutor.execute(() -> {
                long freedBytes = performDeepClean(appContext);
                DeepCleanStats refreshedStats = calculateDeepCleanStats(appContext);
                @Nullable StorageUsageSnapshot refreshedUsage = readStorageUsageSnapshot();

                metricsHandler.post(() -> {
                    if (!isAdded()) {
                        return;
                    }

                    bindDeepCleanDialogMetrics(
                            ring,
                            tvPercent,
                            tvSpaceValue,
                            tvCacheValue,
                            tvTempValue,
                            tvJunkValue,
                            refreshedUsage,
                            refreshedStats
                    );

                    updateStorageMetrics();
                    updateRamMetrics();

                    Context context = getContext();
                    if (context != null) {
                        Toast.makeText(
                                context,
                                getString(R.string.apps_deep_clean_done_toast, formatStorageSize(freedBytes)),
                                Toast.LENGTH_LONG
                        ).show();
                    }
                    dialog.dismiss();
                });
            });
        });

        dialog.setOnShowListener((DialogInterface ignored) -> {
            if (dialog.getWindow() == null) {
                return;
            }

            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(android.graphics.Color.TRANSPARENT));
            int maxWidthPx = dpToPx(400);
            int requestedWidth = (int) (getResources().getDisplayMetrics().widthPixels * 0.92f);
            int finalWidth = Math.min(requestedWidth, maxWidthPx);
            dialog.getWindow().setLayout(finalWidth, ViewGroup.LayoutParams.WRAP_CONTENT);
        });

        dialog.show();

        backgroundExecutor.execute(() -> {
            DeepCleanStats detectedStats = calculateDeepCleanStats(appContext);
            @Nullable StorageUsageSnapshot detectedUsage = readStorageUsageSnapshot();

            metricsHandler.post(() -> {
                if (!isAdded() || !dialog.isShowing()) {
                    return;
                }
                bindDeepCleanDialogMetrics(
                        ring,
                        tvPercent,
                        tvSpaceValue,
                        tvCacheValue,
                        tvTempValue,
                        tvJunkValue,
                        detectedUsage,
                        detectedStats
                );
            });
        });
    }

    private void bindDeepCleanDialogMetrics(
            @Nullable CircularProgressIndicator ring,
            @Nullable TextView tvPercent,
            @Nullable TextView tvSpaceValue,
            @Nullable TextView tvCacheValue,
            @Nullable TextView tvTempValue,
            @Nullable TextView tvJunkValue,
            @Nullable StorageUsageSnapshot usageSnapshot,
            @NonNull DeepCleanStats stats
    ) {
        int storagePercent = usageSnapshot != null ? usageSnapshot.percent : 0;
        if (ring != null) {
            ring.setProgress(storagePercent);
        }
        if (tvPercent != null) {
            tvPercent.setText(String.format(Locale.getDefault(), "%d%%", storagePercent));
        }
        if (tvSpaceValue != null) {
            tvSpaceValue.setText(formatStorageSize(stats.totalBytes()));
        }
        if (tvCacheValue != null) {
            tvCacheValue.setText(formatStorageSize(stats.cacheBytes));
        }
        if (tvTempValue != null) {
            tvTempValue.setText(formatStorageSize(stats.tempBytes));
        }
        if (tvJunkValue != null) {
            tvJunkValue.setText(formatStorageSize(stats.junkBytes));
        }
    }

    @NonNull
    private DeepCleanStats calculateDeepCleanStats(@NonNull Context context) {
        return calculateDeepCleanStats(context, true);
    }

    @NonNull
    private DeepCleanStats calculateDeepCleanStats(@NonNull Context context, boolean includeSystemCacheEstimate) {
        long cacheBytesManual = 0L;
        for (File cacheRoot : collectAppCacheRoots(context)) {
            cacheBytesManual += calculateDirectorySize(cacheRoot);
        }

        long cacheBytes = Math.max(cacheBytesManual, readAppCacheSizeBytes(context));
        if (includeSystemCacheEstimate) {
            cacheBytes += estimateReclaimableSystemCacheBytes(context);
        }

        long tempBytes = 0L;
        long junkBytes = 0L;
        for (File root : collectTempAndJunkRoots(context)) {
            long[] buckets = calculateTempAndJunkSizes(root);
            tempBytes += buckets[0];
            junkBytes += buckets[1];
        }

        return new DeepCleanStats(cacheBytes, tempBytes, junkBytes);
    }

    @Nullable
    private StorageUsageSnapshot readStorageUsageSnapshot() {
        try {
            StatFs statFs = new StatFs(Environment.getDataDirectory().getAbsolutePath());
            long totalBytes = statFs.getTotalBytes();
            long availableBytes = statFs.getAvailableBytes();
            if (totalBytes <= 0L) {
                return null;
            }

            long usedBytes = Math.max(0L, totalBytes - availableBytes);
            int percent = (int) Math.round((usedBytes * 100d) / totalBytes);
            percent = Math.max(0, Math.min(100, percent));
            return new StorageUsageSnapshot(usedBytes, totalBytes, percent);
        } catch (Exception ignored) {
            return null;
        }
    }

    private long performDeepClean(@NonNull Context context) {
        long freedBytes = 0L;

        for (File cacheRoot : collectAppCacheRoots(context)) {
            freedBytes += clearDirectoryContents(cacheRoot);
        }
        for (File root : collectTempAndJunkRoots(context)) {
            freedBytes += deleteTempAndJunkFiles(root);
        }

        freedBytes += reclaimSystemCacheIfPossible(context);
        return Math.max(0L, freedBytes);
    }

    private long readAppCacheSizeBytes(@NonNull Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return 0L;
        }

        try {
            StorageManager storageManager = (StorageManager) context.getSystemService(Context.STORAGE_SERVICE);
            File cacheDir = context.getCacheDir();
            if (storageManager == null || cacheDir == null) {
                return 0L;
            }

            UUID storageUuid = storageManager.getUuidForPath(cacheDir);
            return Math.max(0L, storageManager.getCacheSizeBytes(storageUuid));
        } catch (Exception ignored) {
            return 0L;
        }
    }

    private long estimateReclaimableSystemCacheBytes(@NonNull Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return 0L;
        }

        try {
            StorageManager storageManager = (StorageManager) context.getSystemService(Context.STORAGE_SERVICE);
            File filesDir = context.getFilesDir();
            if (storageManager == null || filesDir == null) {
                return 0L;
            }

            UUID storageUuid = storageManager.getUuidForPath(filesDir);
            long allocatableBytes = storageManager.getAllocatableBytes(storageUuid);
            long usableBytes = Math.max(0L, Environment.getDataDirectory().getUsableSpace());
            return Math.max(0L, allocatableBytes - usableBytes);
        } catch (Exception ignored) {
            return 0L;
        }
    }

    private long reclaimSystemCacheIfPossible(@NonNull Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return 0L;
        }

        try {
            StorageManager storageManager = (StorageManager) context.getSystemService(Context.STORAGE_SERVICE);
            File filesDir = context.getFilesDir();
            if (storageManager == null || filesDir == null) {
                return 0L;
            }

            UUID storageUuid = storageManager.getUuidForPath(filesDir);
            long reclaimableBefore = estimateReclaimableSystemCacheBytes(context);
            if (reclaimableBefore <= 0L) {
                return 0L;
            }

            long usableBefore = Math.max(0L, Environment.getDataDirectory().getUsableSpace());
            long requestedExtra = Math.min(reclaimableBefore, SYSTEM_CACHE_RECLAIM_REQUEST_BYTES);
            long requestedBytes = usableBefore + Math.max(1L, requestedExtra);

            storageManager.allocateBytes(storageUuid, requestedBytes);

            long usableAfter = Math.max(0L, Environment.getDataDirectory().getUsableSpace());
            long freedByFreeSpaceDelta = Math.max(0L, usableAfter - usableBefore);
            if (freedByFreeSpaceDelta > 0L) {
                return freedByFreeSpaceDelta;
            }

            long reclaimableAfter = estimateReclaimableSystemCacheBytes(context);
            return Math.max(0L, reclaimableBefore - reclaimableAfter);
        } catch (Exception ignored) {
            return 0L;
        }
    }

    @NonNull
    private List<File> collectAppCacheRoots(@NonNull Context context) {
        List<File> roots = new ArrayList<>();
        Set<String> seenPaths = new LinkedHashSet<>();

        addDistinctDirectory(roots, seenPaths, context.getCacheDir());
        addDistinctDirectory(roots, seenPaths, context.getCodeCacheDir());

        File[] externalCacheDirs = context.getExternalCacheDirs();
        if (externalCacheDirs != null) {
            for (File dir : externalCacheDirs) {
                addDistinctDirectory(roots, seenPaths, dir);
            }
        }

        return roots;
    }

    @NonNull
    private List<File> collectTempAndJunkRoots(@NonNull Context context) {
        List<File> roots = new ArrayList<>();
        Set<String> seenPaths = new LinkedHashSet<>();

        addDistinctDirectory(roots, seenPaths, context.getFilesDir());
        addDistinctDirectory(roots, seenPaths, context.getNoBackupFilesDir());

        File[] externalFilesDirs = context.getExternalFilesDirs(null);
        if (externalFilesDirs != null) {
            for (File dir : externalFilesDirs) {
                addDistinctDirectory(roots, seenPaths, dir);
            }
        }

        addSharedStorageTempAndJunkRoots(context, roots, seenPaths);

        return roots;
    }

    private void addSharedStorageTempAndJunkRoots(
            @NonNull Context context,
            @NonNull List<File> roots,
            @NonNull Set<String> seenPaths
    ) {
        if (!hasSharedStorageAccess(context)) {
            return;
        }

        File externalRoot = Environment.getExternalStorageDirectory();
        if (externalRoot == null || !externalRoot.exists()) {
            return;
        }

        for (String relativePath : SHARED_STORAGE_SCAN_RELATIVE_DIRS) {
            if (TextUtils.isEmpty(relativePath)) {
                continue;
            }
            addDistinctDirectory(roots, seenPaths, new File(externalRoot, relativePath));
        }
    }

    private boolean hasSharedStorageAccess(@NonNull Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager();
        }

        return ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void addDistinctDirectory(
            @NonNull List<File> output,
            @NonNull Set<String> seenPaths,
            @Nullable File directory
    ) {
        if (directory == null) {
            return;
        }

        String key;
        try {
            key = directory.getCanonicalPath();
        } catch (IOException ignored) {
            key = directory.getAbsolutePath();
        }

        if (seenPaths.add(key)) {
            output.add(directory);
        }
    }

    private long clearDirectoryContents(@Nullable File directory) {
        if (directory == null || !directory.exists() || !directory.isDirectory()) {
            return 0L;
        }

        long deletedBytes = 0L;
        File[] children = directory.listFiles();
        if (children == null) {
            return 0L;
        }

        for (File child : children) {
            deletedBytes += deleteRecursively(child);
        }
        return deletedBytes;
    }

    private long deleteTempAndJunkFiles(@Nullable File root) {
        if (root == null || !root.exists()) {
            return 0L;
        }

        if (root.isFile()) {
            return shouldDeleteAsTempOrJunk(root.getName()) ? deleteFileIfPossible(root) : 0L;
        }

        String rootName = toLower(root.getName());
        if (isTempDirectoryCandidate(rootName) || isJunkDirectoryCandidate(rootName)) {
            return deleteRecursively(root);
        }

        long deletedBytes = 0L;
        File[] children = root.listFiles();
        if (children == null) {
            return 0L;
        }

        for (File child : children) {
            if (child == null) {
                continue;
            }

            if (child.isDirectory()) {
                String dirName = toLower(child.getName());
                if (isTempDirectoryCandidate(dirName) || isJunkDirectoryCandidate(dirName)) {
                    deletedBytes += deleteRecursively(child);
                    continue;
                }

                deletedBytes += deleteTempAndJunkFiles(child);
                File[] nested = child.listFiles();
                if (nested != null && nested.length == 0) {
                    child.delete();
                }
                continue;
            }

            if (shouldDeleteAsTempOrJunk(child.getName())) {
                deletedBytes += deleteFileIfPossible(child);
            }
        }

        return deletedBytes;
    }

    private long deleteRecursively(@Nullable File file) {
        if (file == null || !file.exists()) {
            return 0L;
        }

        if (file.isFile()) {
            return deleteFileIfPossible(file);
        }

        long deletedBytes = 0L;
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                deletedBytes += deleteRecursively(child);
            }
        }

        file.delete();
        return deletedBytes;
    }

    private long deleteFileIfPossible(@NonNull File file) {
        long fileSize = Math.max(0L, file.length());
        if (file.delete()) {
            return fileSize;
        }
        return 0L;
    }

    private long calculateDirectorySize(@Nullable File root) {
        if (root == null || !root.exists()) {
            return 0L;
        }
        if (root.isFile()) {
            return Math.max(0L, root.length());
        }

        long total = 0L;
        File[] children = root.listFiles();
        if (children == null) {
            return 0L;
        }
        for (File child : children) {
            total += calculateDirectorySize(child);
        }
        return total;
    }

    @NonNull
    private long[] calculateTempAndJunkSizes(@Nullable File root) {
        long[] buckets = new long[]{0L, 0L};
        collectTempAndJunkSizes(root, buckets);
        return buckets;
    }

    private void collectTempAndJunkSizes(@Nullable File root, @NonNull long[] buckets) {
        if (root == null || !root.exists()) {
            return;
        }

        if (root.isFile()) {
            long size = Math.max(0L, root.length());
            String name = root.getName();
            if (isTempCandidate(name)) {
                buckets[0] += size;
            } else if (isJunkCandidate(name)) {
                buckets[1] += size;
            }
            return;
        }

        String dirName = toLower(root.getName());
        if (isTempDirectoryCandidate(dirName)) {
            buckets[0] += calculateDirectorySize(root);
            return;
        }
        if (isJunkDirectoryCandidate(dirName)) {
            buckets[1] += calculateDirectorySize(root);
            return;
        }

        File[] children = root.listFiles();
        if (children == null) {
            return;
        }
        for (File child : children) {
            collectTempAndJunkSizes(child, buckets);
        }
    }

    private boolean shouldDeleteAsTempOrJunk(@Nullable String fileName) {
        return isTempCandidate(fileName) || isJunkCandidate(fileName);
    }

    private boolean isTempCandidate(@Nullable String fileName) {
        if (TextUtils.isEmpty(fileName)) {
            return false;
        }
        String lower = toLower(fileName);
        return hasAnySuffix(lower, TEMP_FILE_SUFFIXES)
                || containsAnyToken(lower, TEMP_NAME_TOKENS);
    }

    private boolean isJunkCandidate(@Nullable String fileName) {
        if (TextUtils.isEmpty(fileName)) {
            return false;
        }
        String lower = toLower(fileName);
        return hasAnySuffix(lower, JUNK_FILE_SUFFIXES)
                || containsAnyToken(lower, JUNK_NAME_TOKENS);
    }

    private boolean isTempDirectoryCandidate(@Nullable String directoryName) {
        if (TextUtils.isEmpty(directoryName)) {
            return false;
        }
        return isDirectoryNameCandidate(toLower(directoryName), TEMP_DIR_NAMES);
    }

    private boolean isJunkDirectoryCandidate(@Nullable String directoryName) {
        if (TextUtils.isEmpty(directoryName)) {
            return false;
        }
        return isDirectoryNameCandidate(toLower(directoryName), JUNK_DIR_NAMES);
    }

    private boolean isDirectoryNameCandidate(@NonNull String lowerName, @NonNull String[] acceptedNames) {
        for (String candidate : acceptedNames) {
            if (lowerName.equals(candidate)
                    || lowerName.startsWith(candidate + "_")
                    || lowerName.startsWith(candidate + "-")) {
                return true;
            }
        }
        return false;
    }

    private boolean hasAnySuffix(@NonNull String value, @NonNull String[] suffixes) {
        for (String suffix : suffixes) {
            if (value.endsWith(suffix)) {
                return true;
            }
        }
        return false;
    }

    private boolean containsAnyToken(@NonNull String value, @NonNull String[] tokens) {
        for (String token : tokens) {
            if (containsToken(value, token)) {
                return true;
            }
        }
        return false;
    }

    private boolean containsToken(@NonNull String value, @NonNull String token) {
        int fromIndex = 0;
        while (fromIndex < value.length()) {
            int index = value.indexOf(token, fromIndex);
            if (index < 0) {
                return false;
            }

            int end = index + token.length();
            boolean startBoundary = index == 0 || !Character.isLetterOrDigit(value.charAt(index - 1));
            boolean endBoundary = end >= value.length() || !Character.isLetterOrDigit(value.charAt(end));
            if (startBoundary && endBoundary) {
                return true;
            }
            fromIndex = index + 1;
        }
        return false;
    }

    @NonNull
    private String toLower(@Nullable String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private int dpToPx(int valueDp) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(valueDp * density);
    }

    @NonNull
    private String formatStorageSize(long bytes) {
        if (bytes <= 0L) {
            return "0 MB";
        }

        double mb = bytes / (1024d * 1024d);
        if (mb < 1024d) {
            return String.format(Locale.getDefault(), "%.0f MB", mb);
        }

        double gb = mb / 1024d;
        return String.format(Locale.getDefault(), "%.2f GB", gb);
    }

    @NonNull
    private String formatGb(long bytes) {
        double gb = bytes / (1024d * 1024d * 1024d);
        return String.format(Locale.getDefault(), "%.2f GB", gb);
    }

    private void runStopAppsAction() {
        if (forceStopActionInProgress) {
            Log.w(STOP_APPS_LOG_TAG, "Stop apps ignored because flow is already in progress.");
            return;
        }

        pruneRecentlyForceStoppedPackages();
        refreshBackgroundAppsVerificationList();
        Log.i(STOP_APPS_LOG_TAG, "Stop apps tapped. Checking accessibility and collecting targets.");

        if (!isAccessibilityServiceEnabled()) {
            Log.w(STOP_APPS_LOG_TAG, "Accessibility service disabled. Prompting user.");
            showAccessibilityPermissionDialog();
            return;
        }

        final TargetPackagesResult targetResult = collectTargetPackages();
        final Set<String> targetPackages = targetResult.packages;
        Log.i(
                STOP_APPS_LOG_TAG,
                "Target packages collected: size=" + targetPackages.size()
                        + ", usageAccessMissing=" + targetResult.usageAccessMissing
        );

        if (targetPackages.isEmpty()) {
            boolean clearRecentsStarted = AppAccessibilityService.requestClearRecentApps();
            Log.i(STOP_APPS_LOG_TAG, "No direct targets. Recents fallback started=" + clearRecentsStarted);
            if (clearRecentsStarted) {
                Toast.makeText(
                        requireContext(),
                        getString(R.string.apps_force_stop_clear_recents_fallback_toast),
                        Toast.LENGTH_LONG
                ).show();
                metricsHandler.postDelayed(this::refreshBackgroundAppsVerificationList, 1200L);
                return;
            }

            Toast.makeText(
                    requireContext(),
                    getString(R.string.apps_force_stop_no_targets_toast),
                    Toast.LENGTH_LONG
            ).show();
            return;
        }

        startForceStopAutomationWithRetry(new ArrayList<>(targetPackages));
    }

    private void startForceStopAutomationWithRetry(@NonNull List<String> targetPackages) {
        forceStopActionInProgress = true;
        cancelPendingForceStopStartAttempt();
        cancelForceStopSafetyTimeout();
        Log.i(
                STOP_APPS_LOG_TAG,
                "Starting force-stop flow with " + targetPackages.size() + " targets, turboMode=" + isTurboModeEnabled()
        );

        if (fabStopApps != null) {
            fabStopApps.setEnabled(false);
            fabStopApps.setText(R.string.apps_force_stopping_label);
        }

        attemptStartForceStopAutomation(targetPackages, 0);
    }

    private void attemptStartForceStopAutomation(@NonNull List<String> targetPackages, int attemptNumber) {
        pendingForceStopStartRunnable = null;
        if (!forceStopActionInProgress) {
            return;
        }

        boolean started = AppAccessibilityService.requestForceStopPackages(targetPackages, isTurboModeEnabled());
        Log.i(
                STOP_APPS_LOG_TAG,
                "Start attempt " + (attemptNumber + 1) + "/" + FORCE_STOP_START_MAX_ATTEMPTS + " result=" + started
        );
        if (started) {
            onForceStopAutomationStarted(targetPackages);
            return;
        }

        if (attemptNumber + 1 >= FORCE_STOP_START_MAX_ATTEMPTS) {
            Log.e(STOP_APPS_LOG_TAG, "Failed to start force-stop automation after max retries.");
            finishForceStopUiFlow();
            if (isAdded()) {
                Toast.makeText(
                        requireContext(),
                        getString(R.string.apps_force_stop_start_failed_toast),
                        Toast.LENGTH_LONG
                ).show();
            }
            return;
        }

        Runnable retryRunnable = () -> attemptStartForceStopAutomation(targetPackages, attemptNumber + 1);
        pendingForceStopStartRunnable = retryRunnable;
        metricsHandler.postDelayed(retryRunnable, FORCE_STOP_START_RETRY_INTERVAL_MS);
    }

    private void onForceStopAutomationStarted(@NonNull List<String> targetPackages) {
        invalidateBackgroundAppsCache();
        refreshBackgroundAppsVerificationList();
        scheduleForceStopSafetyTimeout(targetPackages.size());
        Log.i(STOP_APPS_LOG_TAG, "Force-stop automation started. Waiting for completion broadcast.");

        Toast.makeText(
                requireContext(),
                getString(R.string.apps_force_stop_starting_toast, targetPackages.size()),
                Toast.LENGTH_LONG
        ).show();
    }

    private void restoreStopButtonIdleState() {
        if (fabStopApps != null) {
            fabStopApps.setEnabled(true);
            fabStopApps.setText(R.string.apps_stop_button_label);
        }
    }

    private void finishForceStopUiFlow() {
        forceStopActionInProgress = false;
        cancelPendingForceStopStartAttempt();
        cancelForceStopSafetyTimeout();
        restoreStopButtonIdleState();
        Log.i(STOP_APPS_LOG_TAG, "UI flow reset to idle state.");
    }

    private void cancelPendingForceStopStartAttempt() {
        if (pendingForceStopStartRunnable == null) {
            return;
        }
        metricsHandler.removeCallbacks(pendingForceStopStartRunnable);
        pendingForceStopStartRunnable = null;
    }

    private void cancelForceStopSafetyTimeout() {
        if (forceStopSafetyTimeoutRunnable == null) {
            return;
        }
        metricsHandler.removeCallbacks(forceStopSafetyTimeoutRunnable);
        forceStopSafetyTimeoutRunnable = null;
    }

    private void scheduleForceStopSafetyTimeout(int targetCount) {
        cancelForceStopSafetyTimeout();
        long timeoutMs = computeForceStopSafetyTimeoutMs(targetCount);
        Log.i(STOP_APPS_LOG_TAG, "Scheduling safety timeout: " + timeoutMs + "ms for targets=" + targetCount);

        Runnable timeoutRunnable = () -> {
            if (!forceStopActionInProgress) {
                return;
            }

            Log.w(STOP_APPS_LOG_TAG, "Safety timeout reached before completion broadcast.");
            finishForceStopUiFlow();
            invalidateBackgroundAppsCache();

            if (!isAdded()) {
                return;
            }

            Toast.makeText(
                    requireContext(),
                    getString(R.string.apps_force_stop_timeout_toast),
                    Toast.LENGTH_LONG
            ).show();
            refreshBackgroundAppsVerificationList();
        };
        forceStopSafetyTimeoutRunnable = timeoutRunnable;
        metricsHandler.postDelayed(timeoutRunnable, timeoutMs);
    }

    private long computeForceStopSafetyTimeoutMs(int targetCount) {
        long normalizedCount = Math.max(1, targetCount);
        long calculatedTimeout = FORCE_STOP_TIMEOUT_BASE_MS + (normalizedCount * FORCE_STOP_TIMEOUT_PER_APP_MS);
        long clampedTimeout = Math.min(FORCE_STOP_TIMEOUT_MAX_MS, calculatedTimeout);
        return Math.max(FORCE_STOP_TIMEOUT_MIN_MS, clampedTimeout);
    }

    private void refreshAccessibilityPermissionUi() {
        if (cardAccessibilityRequest != null) {
            cardAccessibilityRequest.setVisibility(View.GONE);
        }
    }

    private void refreshBackgroundAppsVerificationList() {
        if (!isAdded() || layoutBackgroundAppsList == null || tvBackgroundAppsEmpty == null) {
            return;
        }

        final Context appContext = requireContext().getApplicationContext();
        CachedBackgroundAppsSnapshot cachedSnapshot = readCachedBackgroundAppsSnapshot(appContext);
        if (cachedSnapshot != null) {
            renderBackgroundAppsVerificationList(cachedSnapshot.entries, cachedSnapshot.hasUsageAccess);
            return;
        }

        final int renderVersion = ++backgroundAppsRenderVersion;

        backgroundExecutor.execute(() -> {
            List<BackgroundAppEntry> entries = collectBackgroundAppsForVerification(appContext);
            boolean hasUsageAccess = hasUsageStatsAccess(appContext);
            cacheBackgroundAppsSnapshot(entries, hasUsageAccess);

            metricsHandler.post(() -> {
                if (!isAdded()
                        || renderVersion != backgroundAppsRenderVersion
                        || layoutBackgroundAppsList == null
                        || tvBackgroundAppsEmpty == null) {
                    return;
                }
                renderBackgroundAppsVerificationList(entries, hasUsageAccess);
            });
        });
    }

    private void renderBackgroundAppsVerificationList(@NonNull List<BackgroundAppEntry> entries, boolean hasUsageAccess) {
        layoutBackgroundAppsList.removeAllViews();
        if (entries.isEmpty()) {
            tvBackgroundAppsEmpty.setVisibility(View.VISIBLE);
            tvBackgroundAppsEmpty.setText(
                    hasUsageAccess
                            ? R.string.apps_background_empty_none
                            : R.string.apps_background_empty_no_usage_access
            );
            tvBackgroundAppsEmpty.setOnClickListener(null);
            tvBackgroundAppsEmpty.setClickable(false);
            return;
        }

        tvBackgroundAppsEmpty.setVisibility(View.GONE);
        tvBackgroundAppsEmpty.setOnClickListener(null);
        tvBackgroundAppsEmpty.setClickable(false);
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        for (BackgroundAppEntry entry : entries) {
            View row = inflater.inflate(R.layout.item_background_app, layoutBackgroundAppsList, false);
            ImageView iconView = row.findViewById(R.id.ivBackgroundAppIcon);
            TextView nameView = row.findViewById(R.id.tvBackgroundAppName);
            TextView packageView = row.findViewById(R.id.tvBackgroundAppPackage);

            iconView.setImageDrawable(entry.icon);
            nameView.setText(entry.label);
            packageView.setText(entry.packageName);

            layoutBackgroundAppsList.addView(row);
        }
    }

    @NonNull
    private List<BackgroundAppEntry> collectBackgroundAppsForVerification(@NonNull Context context) {
        PackageManager packageManager = context.getPackageManager();
        String ownPackage = context.getPackageName();
        String defaultHomePackage = getDefaultHomePackage(packageManager);
        long now = System.currentTimeMillis();
        pruneRecentlyForceStoppedPackages(now);
        Set<String> whitelistedPackages = getWhitelistedPackages();
        Set<String> runningPackages = collectRunningPackagesSnapshot();
        boolean hasUsageAccess = hasUsageStatsAccess(context);
        boolean canValidateRunning = hasUsageAccess && !runningPackages.isEmpty();

        Map<String, Long> scoreByPackage = new HashMap<>();
        if (hasUsageAccess) {
            collectFromUsageEvents(context, scoreByPackage, VERIFICATION_LOOKBACK_MS);
            collectFromUsageStats(context, scoreByPackage);
            collectFromAggregatedUsageStats(context, scoreByPackage);
        } else {
            collectFromAccessibilityRecentApps(context, scoreByPackage);
        }

        if (scoreByPackage.isEmpty()) {
            collectFromRunningProcesses(scoreByPackage);
        }

        if (scoreByPackage.isEmpty()) {
            collectFromLaunchableAppsHeuristic(
                    context,
                    scoreByPackage,
                    whitelistedPackages,
                    ownPackage,
                    defaultHomePackage
            );
        }

        List<Map.Entry<String, Long>> orderedEntries = new ArrayList<>(scoreByPackage.entrySet());
        orderedEntries.sort((left, right) -> Long.compare(right.getValue(), left.getValue()));

        List<BackgroundAppEntry> result = new ArrayList<>();
        for (Map.Entry<String, Long> entry : orderedEntries) {
            String packageName = entry.getKey();
            if (isRecentlyForceStopped(packageName, now)) {
                continue;
            }
            if (canValidateRunning && !runningPackages.contains(packageName)) {
                continue;
            }
            if (whitelistedPackages.contains(packageName)) {
                continue;
            }
            if (isPackageStopped(packageManager, packageName)) {
                continue;
            }
            if (!isVerificationCandidate(packageName, ownPackage, defaultHomePackage)) {
                continue;
            }

            try {
                ApplicationInfo appInfo = packageManager.getApplicationInfo(packageName, 0);
                CharSequence labelValue = packageManager.getApplicationLabel(appInfo);
                String label = TextUtils.isEmpty(labelValue) ? packageName : labelValue.toString();
                Drawable icon = packageManager.getApplicationIcon(appInfo);
                result.add(new BackgroundAppEntry(label, packageName, icon));
            } catch (PackageManager.NameNotFoundException ignored) {
                BackgroundAppEntry fallbackEntry = buildBackgroundEntryFromLaunchIntent(packageManager, packageName);
                if (fallbackEntry != null) {
                    result.add(fallbackEntry);
                } else {
                    Drawable fallbackIcon = packageManager.getDefaultActivityIcon();
                    result.add(new BackgroundAppEntry(packageName, packageName, fallbackIcon));
                }
            }

            if (result.size() >= 30) {
                break;
            }
        }

        if (result.isEmpty()) {
            List<String> fallbackPackages = collectLaunchableCandidatePackages(
                    context,
                    whitelistedPackages,
                    ownPackage,
                    defaultHomePackage,
                    30,
                    false
            );
            for (String packageName : fallbackPackages) {
                if (isRecentlyForceStopped(packageName, now)) {
                    continue;
                }

                try {
                    ApplicationInfo appInfo = packageManager.getApplicationInfo(packageName, 0);
                    CharSequence labelValue = packageManager.getApplicationLabel(appInfo);
                    String label = TextUtils.isEmpty(labelValue) ? packageName : labelValue.toString();
                    Drawable icon = packageManager.getApplicationIcon(appInfo);
                    result.add(new BackgroundAppEntry(label, packageName, icon));
                } catch (PackageManager.NameNotFoundException ignored) {
                    BackgroundAppEntry fallbackEntry = buildBackgroundEntryFromLaunchIntent(packageManager, packageName);
                    if (fallbackEntry != null) {
                        result.add(fallbackEntry);
                    }
                }

                if (result.size() >= 30) {
                    break;
                }
            }
        }

        Collections.sort(result, (left, right) -> {
            int byLabel = left.label.compareToIgnoreCase(right.label);
            if (byLabel != 0) {
                return byLabel;
            }
            return left.packageName.compareToIgnoreCase(right.packageName);
        });

        return result;
    }

    private void setupAppsSuggestionActions() {
        if (btnAppsSuggestionApply != null) {
            btnAppsSuggestionApply.setOnClickListener(v -> {
                registerAppsSuggestionFeedback(ACTION_APPLY);
                showAppsSuggestionCard();
                setAppsSuggestionActionsVisible(false);
            });
        }

        if (btnAppsSuggestionDismiss != null) {
            btnAppsSuggestionDismiss.setOnClickListener(v -> {
                registerAppsSuggestionFeedback(ACTION_DISMISS);
                hideAppsSuggestionCard();
                setAppsSuggestionActionsVisible(true);
            });
        }
    }

    private void loadStoredAppsSuggestion() {
        if (tvAppsSmartSuggestion == null || settingsPrefs == null) {
            return;
        }

        if (!areSmartSuggestionsEnabled()) {
            hideAppsSuggestionCard();
            return;
        }

        if (isAppsSuggestionHiddenUntilNextCycle()) {
            hideAppsSuggestionCard();
            return;
        }

        showAppsSuggestionCard();

        String lastMessage = settingsPrefs.getString(SmartSuggestionPrefetcher.KEY_APPS_AI_LAST_MESSAGE, "");
        if (TextUtils.isEmpty(lastMessage)) {
            tvAppsSmartSuggestion.setText(R.string.apps_suggestion_generating);
            setAppsSuggestionActionsVisible(false);
            return;
        }
        tvAppsSmartSuggestion.setText(lastMessage);
        boolean actionsHidden = settingsPrefs.getBoolean(SmartSuggestionPrefetcher.KEY_APPS_AI_ACTIONS_HIDDEN, false);
        setAppsSuggestionActionsVisible(!actionsHidden);
    }

    private void refreshAppsSuggestionState() {
        if (!isAdded() || settingsPrefs == null || tvAppsSmartSuggestion == null) {
            return;
        }

        if (!areSmartSuggestionsEnabled()) {
            hideAppsSuggestionCard();
            return;
        }

        if (isAppsSuggestionHiddenUntilNextCycle()) {
            hideAppsSuggestionCard();
            return;
        }

        showAppsSuggestionCard();

        Context appContext = requireContext().getApplicationContext();
        long now = System.currentTimeMillis();
        if (now - lastAppsSuggestionPrefetchAtMs >= APPS_SUGGESTION_PREFETCH_MIN_INTERVAL_MS) {
            lastAppsSuggestionPrefetchAtMs = now;
            backgroundExecutor.execute(() -> SmartSuggestionPrefetcher.prefetchAppsSuggestion(appContext));
        }
        String lastMessage = settingsPrefs.getString(SmartSuggestionPrefetcher.KEY_APPS_AI_LAST_MESSAGE, "");
        if (TextUtils.isEmpty(lastMessage)) {
            tvAppsSmartSuggestion.setText(R.string.apps_suggestion_generating);
            setAppsSuggestionActionsVisible(false);
        } else {
            tvAppsSmartSuggestion.setText(lastMessage);
            boolean actionsHidden = settingsPrefs.getBoolean(SmartSuggestionPrefetcher.KEY_APPS_AI_ACTIONS_HIDDEN, false);
            setAppsSuggestionActionsVisible(!actionsHidden);
        }

        if (cardAppsSmartSuggestion != null) {
            cardAppsSmartSuggestion.setAlpha(1f);
        }
    }

    private void registerAppsSuggestionFeedback(@NonNull String action) {
        if (settingsPrefs == null) {
            return;
        }

        int acceptCount = settingsPrefs.getInt(SmartSuggestionPrefetcher.KEY_APPS_AI_ACCEPT_COUNT, 0);
        int dismissCount = settingsPrefs.getInt(SmartSuggestionPrefetcher.KEY_APPS_AI_DISMISS_COUNT, 0);

        if (ACTION_APPLY.equals(action)) {
            acceptCount++;
        } else if (ACTION_DISMISS.equals(action)) {
            dismissCount++;
        }

        String renderedMessage = tvAppsSmartSuggestion != null
                ? tvAppsSmartSuggestion.getText().toString()
                : settingsPrefs.getString(SmartSuggestionPrefetcher.KEY_APPS_AI_LAST_MESSAGE, "");

        boolean hideUntilNext = ACTION_DISMISS.equals(action);
        boolean hideActions = ACTION_APPLY.equals(action);

        settingsPrefs.edit()
                .putInt(SmartSuggestionPrefetcher.KEY_APPS_AI_ACCEPT_COUNT, acceptCount)
                .putInt(SmartSuggestionPrefetcher.KEY_APPS_AI_DISMISS_COUNT, dismissCount)
                .putString(SmartSuggestionPrefetcher.KEY_APPS_AI_LAST_ACTION, action)
                .putString(SmartSuggestionPrefetcher.KEY_APPS_AI_LAST_MESSAGE, renderedMessage)
            .putBoolean(SmartSuggestionPrefetcher.KEY_APPS_AI_HIDDEN_UNTIL_NEXT, hideUntilNext)
            .putBoolean(SmartSuggestionPrefetcher.KEY_APPS_AI_ACTIONS_HIDDEN, hideActions)
                .putLong(SmartSuggestionPrefetcher.KEY_APPS_AI_NEXT_AT, System.currentTimeMillis() + SmartSuggestionPrefetcher.SUGGESTION_INTERVAL_MS)
                .apply();
    }

    private boolean isAppsSuggestionHiddenUntilNextCycle() {
        if (settingsPrefs == null) {
            return false;
        }

        long lastProcessSession = settingsPrefs.getLong(
                SmartSuggestionPrefetcher.KEY_APPS_AI_LAST_PROCESS_SESSION,
                Long.MIN_VALUE
        );
        if (lastProcessSession != AiSuggestionSession.PROCESS_SESSION_ID) {
            settingsPrefs.edit()
                .putBoolean(SmartSuggestionPrefetcher.KEY_APPS_AI_HIDDEN_UNTIL_NEXT, false)
                .putBoolean(SmartSuggestionPrefetcher.KEY_APPS_AI_ACTIONS_HIDDEN, false)
                .apply();
            return false;
        }

        boolean hidden = settingsPrefs.getBoolean(SmartSuggestionPrefetcher.KEY_APPS_AI_HIDDEN_UNTIL_NEXT, false);
        if (!hidden) {
            return false;
        }

        long nextAt = settingsPrefs.getLong(SmartSuggestionPrefetcher.KEY_APPS_AI_NEXT_AT, 0L);
        if (System.currentTimeMillis() >= nextAt) {
            settingsPrefs.edit()
                    .putBoolean(SmartSuggestionPrefetcher.KEY_APPS_AI_HIDDEN_UNTIL_NEXT, false)
                    .putBoolean(SmartSuggestionPrefetcher.KEY_APPS_AI_ACTIONS_HIDDEN, false)
                    .apply();
            return false;
        }

        return true;
    }

    private void hideAppsSuggestionCard() {
        if (cardAppsSmartSuggestion != null) {
            cardAppsSmartSuggestion.setVisibility(View.GONE);
        }
    }

    private void showAppsSuggestionCard() {
        if (cardAppsSmartSuggestion != null) {
            cardAppsSmartSuggestion.setVisibility(View.VISIBLE);
        }
    }

    private void setAppsSuggestionActionsVisible(boolean visible) {
        int visibility = visible ? View.VISIBLE : View.GONE;
        if (btnAppsSuggestionApply != null) {
            btnAppsSuggestionApply.setVisibility(visibility);
            btnAppsSuggestionApply.setEnabled(visible);
        }
        if (btnAppsSuggestionDismiss != null) {
            btnAppsSuggestionDismiss.setVisibility(visibility);
            btnAppsSuggestionDismiss.setEnabled(visible);
        }
    }

    private boolean areSmartSuggestionsEnabled() {
        if (settingsPrefs == null) {
            return true;
        }
        return settingsPrefs.getBoolean(
                CloudSyncManager.KEY_SMART_SUGGESTIONS_ENABLED,
                settingsPrefs.getBoolean(CloudSyncManager.KEY_AI_SHIFT_ENABLED, true)
        );
    }

    private boolean isAccessibilityServiceEnabled() {
        Context context = requireContext().getApplicationContext();
        ComponentName expectedComponent = new ComponentName(context, AppAccessibilityService.class);

        int accessibilityEnabled = 0;
        try {
            accessibilityEnabled = Settings.Secure.getInt(
                    context.getContentResolver(),
                    Settings.Secure.ACCESSIBILITY_ENABLED
            );
        } catch (Settings.SettingNotFoundException ignored) {
            // Si no existe el valor, asumimos deshabilitado.
        }

        if (accessibilityEnabled != 1) {
            return false;
        }

        String enabledServices = Settings.Secure.getString(
                context.getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        );
        if (TextUtils.isEmpty(enabledServices)) {
            return false;
        }

        String expectedService = expectedComponent.flattenToString();
        String expectedShortService = expectedComponent.flattenToShortString();
        TextUtils.SimpleStringSplitter splitter = new TextUtils.SimpleStringSplitter(':');
        splitter.setString(enabledServices);
        while (splitter.hasNext()) {
            String service = splitter.next();
            if (TextUtils.isEmpty(service)) {
                continue;
            }

            ComponentName parsedService = ComponentName.unflattenFromString(service);
            if (expectedComponent.equals(parsedService)
                    || expectedService.equalsIgnoreCase(service)
                    || expectedShortService.equalsIgnoreCase(service)) {
                return true;
            }
        }
        return false;
    }

    private void showAccessibilityPermissionDialog() {
        if (!isAdded()) {
            return;
        }

        new MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.apps_accessibility_permission_title)
            .setMessage(R.string.apps_accessibility_permission_message)
            .setPositiveButton(R.string.apps_accessibility_permission_positive, (dialog, which) -> openAccessibilitySettings())
            .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void openAccessibilitySettings() {
        try {
            Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            startActivity(intent);
        } catch (Throwable throwable) {
            Toast.makeText(requireContext(), getString(R.string.apps_accessibility_open_failed), Toast.LENGTH_SHORT).show();
        }
    }

    private void showUsageAccessPermissionDialog() {
        if (!isAdded()) {
            return;
        }

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.apps_usage_access_title)
                .setMessage(R.string.apps_usage_access_message)
                .setPositiveButton(R.string.apps_usage_access_positive, (dialog, which) -> openUsageAccessSettings())
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void openUsageAccessSettings() {
        try {
            Intent intent = new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS);
            startActivity(intent);
        } catch (Throwable throwable) {
            Toast.makeText(requireContext(), getString(R.string.apps_usage_access_open_failed), Toast.LENGTH_SHORT).show();
        }
    }

    private void showStoragePermissionDialog() {
        if (!isAdded()) {
            return;
        }

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.apps_storage_permission_title)
                .setMessage(R.string.apps_storage_permission_message)
                .setPositiveButton(R.string.apps_storage_permission_positive, (dialog, which) -> openStoragePermissionSettings())
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void openStoragePermissionSettings() {
        Context context = requireContext();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                Intent appSpecificIntent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                appSpecificIntent.setData(Uri.fromParts("package", context.getPackageName(), null));
                if (appSpecificIntent.resolveActivity(context.getPackageManager()) != null) {
                    startActivity(appSpecificIntent);
                    return;
                }
            } catch (Throwable ignored) {
                // Intent alternativo debajo.
            }

            try {
                Intent fallbackIntent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                startActivity(fallbackIntent);
                return;
            } catch (Throwable ignored) {
                // Fallback final debajo.
            }
        } else {
            try {
                requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, REQUEST_READ_EXTERNAL_STORAGE_PERMISSION);
                return;
            } catch (Throwable ignored) {
                // Fallback final debajo.
            }
        }

        try {
            Intent appDetailsIntent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            appDetailsIntent.setData(Uri.fromParts("package", context.getPackageName(), null));
            startActivity(appDetailsIntent);
        } catch (Throwable throwable) {
            Toast.makeText(context, getString(R.string.apps_storage_permission_open_failed), Toast.LENGTH_SHORT).show();
        }
    }

    private void registerForceStopAutomationReceiver() {
        if (forceStopReceiverRegistered || forceStopAutomationReceiver == null) {
            return;
        }

        Context context = getContext();
        if (context == null) {
            return;
        }

        IntentFilter filter = new IntentFilter(AppAccessibilityService.ACTION_FORCE_STOP_AUTOMATION_FINISHED);
        ContextCompat.registerReceiver(context, forceStopAutomationReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED);
        forceStopReceiverRegistered = true;
    }

    private void unregisterForceStopAutomationReceiver() {
        if (!forceStopReceiverRegistered || forceStopAutomationReceiver == null) {
            return;
        }

        Context context = getContext();
        if (context == null) {
            forceStopReceiverRegistered = false;
            return;
        }

        try {
            context.unregisterReceiver(forceStopAutomationReceiver);
        } catch (IllegalArgumentException ignored) {
            // Si ya estaba desregistrado, ignoramos.
        }
        forceStopReceiverRegistered = false;
    }

    @NonNull
    private TargetPackagesResult collectTargetPackages() {
        Context context = requireContext().getApplicationContext();
        PackageManager packageManager = context.getPackageManager();
        String ownPackage = context.getPackageName();
        long now = System.currentTimeMillis();
        pruneRecentlyForceStoppedPackages(now);
        Set<String> whitelistedPackages = getWhitelistedPackages();
        Set<String> runningPackages = collectRunningPackagesSnapshot();

        String defaultHomePackage = getDefaultHomePackage(packageManager);
        boolean usageAccessMissing = !hasUsageStatsAccess(context);
        boolean canValidateRunning = !usageAccessMissing && !runningPackages.isEmpty();

        Set<String> targetPackages = new LinkedHashSet<>();
        List<BackgroundAppEntry> visibleEntries = getCachedOrCollectBackgroundAppsForVerification(context);
        for (BackgroundAppEntry entry : visibleEntries) {
            String packageName = entry.packageName;
            if (isRecentlyForceStopped(packageName, now)) {
                continue;
            }
            if (canValidateRunning && !runningPackages.contains(packageName)) {
                continue;
            }
            if (whitelistedPackages.contains(packageName)) {
                continue;
            }
            if (!isForceStopVerificationCandidate(packageManager, packageName, ownPackage, defaultHomePackage)) {
                continue;
            }
            targetPackages.add(packageName);
            if (targetPackages.size() >= MAX_TARGET_PACKAGES) {
                break;
            }
        }

        if (!targetPackages.isEmpty()) {
            return new TargetPackagesResult(targetPackages, usageAccessMissing);
        }

        Map<String, Long> scoreByPackage = new HashMap<>();

        if (usageAccessMissing) {
            collectFromAccessibilityRecentApps(context, scoreByPackage);
        }

        collectFromRunningProcesses(scoreByPackage);

        if (!usageAccessMissing) {
            collectFromUsageEvents(context, scoreByPackage);
            collectFromUsageStats(context, scoreByPackage);
            collectFromAggregatedUsageStats(context, scoreByPackage);
        }

        if (scoreByPackage.isEmpty()) {
            collectFromLaunchableAppsHeuristic(
                    context,
                    scoreByPackage,
                    whitelistedPackages,
                    ownPackage,
                    defaultHomePackage
            );
        }

        List<Map.Entry<String, Long>> orderedEntries = new ArrayList<>(scoreByPackage.entrySet());
        orderedEntries.sort((left, right) -> Long.compare(right.getValue(), left.getValue()));

        for (Map.Entry<String, Long> entry : orderedEntries) {
            String packageName = entry.getKey();
            if (isRecentlyForceStopped(packageName, now)) {
                continue;
            }
            if (canValidateRunning && !runningPackages.contains(packageName)) {
                continue;
            }
            if (whitelistedPackages.contains(packageName)) {
                continue;
            }
            if (isPackageStopped(packageManager, packageName)) {
                continue;
            }
            if (!isForceStopCandidate(packageManager, packageName, ownPackage, defaultHomePackage)) {
                continue;
            }
            targetPackages.add(packageName);
            if (targetPackages.size() >= MAX_TARGET_PACKAGES) {
                break;
            }
        }

        if (targetPackages.isEmpty()) {
            List<String> fallbackPackages = collectLaunchableCandidatePackages(
                    context,
                    whitelistedPackages,
                    ownPackage,
                    defaultHomePackage,
                    MAX_TARGET_PACKAGES,
                    true
            );
            for (String packageName : fallbackPackages) {
                if (isRecentlyForceStopped(packageName, now)) {
                    continue;
                }
                targetPackages.add(packageName);
                if (targetPackages.size() >= MAX_TARGET_PACKAGES) {
                    break;
                }
            }

            if (targetPackages.isEmpty()) {
                List<String> relaxedFallbackPackages = collectLaunchableCandidatePackages(
                        context,
                        whitelistedPackages,
                        ownPackage,
                        defaultHomePackage,
                        MAX_TARGET_PACKAGES,
                        false
                );
                for (String packageName : relaxedFallbackPackages) {
                    if (isRecentlyForceStopped(packageName, now)) {
                        continue;
                    }
                    targetPackages.add(packageName);
                    if (targetPackages.size() >= MAX_TARGET_PACKAGES) {
                        break;
                    }
                }
            }
        }

        return new TargetPackagesResult(targetPackages, usageAccessMissing);
    }

    private void collectFromAccessibilityRecentApps(
            @NonNull Context context,
            @NonNull Map<String, Long> scoreByPackage
    ) {
        Map<String, Long> snapshot = AppAccessibilityService.readRecentAppsSnapshot(context);
        if (snapshot.isEmpty()) {
            return;
        }

        long now = System.currentTimeMillis();
        for (Map.Entry<String, Long> entry : snapshot.entrySet()) {
            String packageName = entry.getKey();
            if (TextUtils.isEmpty(packageName)) {
                continue;
            }

            long timestamp = entry.getValue() != null ? entry.getValue() : 0L;
            if (timestamp <= 0L) {
                continue;
            }

            long ageMs = Math.max(0L, now - timestamp);
            long recencyScore = Math.max(1L, 900_000L - ageMs);
            mergeScore(scoreByPackage, packageName, recencyScore);
        }
    }

    private void collectFromRunningProcesses(@NonNull Map<String, Long> scoreByPackage) {
        if (activityManager == null) {
            return;
        }

        List<ActivityManager.RunningAppProcessInfo> processes = activityManager.getRunningAppProcesses();
        if (processes == null || processes.isEmpty()) {
            return;
        }

        for (ActivityManager.RunningAppProcessInfo info : processes) {
            if (info == null) {
                continue;
            }

            if (info.importance >= ActivityManager.RunningAppProcessInfo.IMPORTANCE_EMPTY) {
                continue;
            }

            long baseScore = 1_000_000L - info.importance;
            if (info.pkgList != null) {
                for (String packageName : info.pkgList) {
                    mergeScore(scoreByPackage, packageName, baseScore);
                }
            }

            String processName = info.processName;
            if (!TextUtils.isEmpty(processName)) {
                int split = processName.indexOf(':');
                String packageName = split > 0 ? processName.substring(0, split) : processName;
                mergeScore(scoreByPackage, packageName, baseScore - 1L);
            }
        }
    }

    private void collectFromLaunchableAppsHeuristic(
            @NonNull Context context,
            @NonNull Map<String, Long> scoreByPackage,
            @NonNull Set<String> whitelistedPackages,
            @NonNull String ownPackage,
            @Nullable String defaultHomePackage
    ) {
        List<String> launchablePackages = collectLaunchableCandidatePackages(
                context,
                whitelistedPackages,
                ownPackage,
                defaultHomePackage,
            FALLBACK_LAUNCHABLE_APPS_LIMIT,
            true
        );
        if (launchablePackages.isEmpty()) {
            launchablePackages = collectLaunchableCandidatePackages(
                context,
                whitelistedPackages,
                ownPackage,
                defaultHomePackage,
                FALLBACK_LAUNCHABLE_APPS_LIMIT,
                false
            );
        }
        if (launchablePackages.isEmpty()) {
            return;
        }

        long baseScore = 750_000L;
        int addedCount = 0;

        for (String packageName : launchablePackages) {

            long score = baseScore - (addedCount * 1000L);
            mergeScore(scoreByPackage, packageName, Math.max(1L, score));
            addedCount++;
            if (addedCount >= FALLBACK_LAUNCHABLE_APPS_LIMIT) {
                break;
            }
        }
    }

    @NonNull
    private List<String> collectLaunchableCandidatePackages(
            @NonNull Context context,
            @NonNull Set<String> whitelistedPackages,
            @NonNull String ownPackage,
            @Nullable String defaultHomePackage,
            int limit,
            boolean strictForceStopFilter
    ) {
        PackageManager packageManager = context.getPackageManager();
        Intent launcherIntent = new Intent(Intent.ACTION_MAIN);
        launcherIntent.addCategory(Intent.CATEGORY_LAUNCHER);

        List<ResolveInfo> launchableActivities = packageManager.queryIntentActivities(
                launcherIntent,
                PackageManager.MATCH_DEFAULT_ONLY
        );
        if (launchableActivities == null || launchableActivities.isEmpty()) {
            return Collections.emptyList();
        }

        Set<String> seenPackages = new LinkedHashSet<>();
        List<String> result = new ArrayList<>();

        for (ResolveInfo resolveInfo : launchableActivities) {
            if (resolveInfo == null || resolveInfo.activityInfo == null) {
                continue;
            }

            String packageName = resolveInfo.activityInfo.packageName;
            if (TextUtils.isEmpty(packageName) || !seenPackages.add(packageName)) {
                continue;
            }
            if (whitelistedPackages.contains(packageName)) {
                continue;
            }
            if (strictForceStopFilter) {
                if (!isForceStopCandidate(packageManager, packageName, ownPackage, defaultHomePackage)) {
                    continue;
                }
            } else {
                if (!isVerificationCandidate(packageName, ownPackage, defaultHomePackage)) {
                    continue;
                }
                if (packageManager.getLaunchIntentForPackage(packageName) == null) {
                    continue;
                }
            }

            result.add(packageName);
            if (result.size() >= Math.max(1, limit)) {
                break;
            }
        }

        return result;
    }

    private void collectFromUsageEvents(@NonNull Context context, @NonNull Map<String, Long> scoreByPackage) {
        collectFromUsageEvents(context, scoreByPackage, RUNNING_EVENTS_LOOKBACK_MS);
    }

    private void collectFromUsageEvents(
            @NonNull Context context,
            @NonNull Map<String, Long> scoreByPackage,
            long lookbackMs
    ) {
        UsageStatsManager usageStatsManager = (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);
        if (usageStatsManager == null) {
            return;
        }

        long now = System.currentTimeMillis();
        long begin = Math.max(0L, now - lookbackMs);
        UsageEvents usageEvents = usageStatsManager.queryEvents(begin, now);
        if (usageEvents == null) {
            return;
        }

        Map<String, Long> lastForeground = new HashMap<>();
        Map<String, Long> lastBackground = new HashMap<>();
        UsageEvents.Event event = new UsageEvents.Event();

        while (usageEvents.hasNextEvent()) {
            usageEvents.getNextEvent(event);
            String packageName = event.getPackageName();
            if (TextUtils.isEmpty(packageName)) {
                continue;
            }

            int eventType = event.getEventType();
            long timestamp = event.getTimeStamp();

            if (eventType == UsageEvents.Event.ACTIVITY_RESUMED
                    || eventType == UsageEvents.Event.FOREGROUND_SERVICE_START) {
                updateLatestTimestamp(lastForeground, packageName, timestamp);
                continue;
            }

            if (eventType == UsageEvents.Event.ACTIVITY_PAUSED
                    || eventType == UsageEvents.Event.ACTIVITY_STOPPED
                    || eventType == UsageEvents.Event.FOREGROUND_SERVICE_STOP) {
                updateLatestTimestamp(lastBackground, packageName, timestamp);
            }
        }

        Set<String> allPackages = new LinkedHashSet<>();
        allPackages.addAll(lastForeground.keySet());
        allPackages.addAll(lastBackground.keySet());

        for (String packageName : allPackages) {
            long foregroundTs = readTimestamp(lastForeground, packageName);
            long backgroundTs = readTimestamp(lastBackground, packageName);
            if (backgroundTs <= foregroundTs) {
                continue;
            }
            long referenceTs = Math.max(foregroundTs, backgroundTs);
            if (referenceTs <= 0L || (now - referenceTs) > lookbackMs) {
                continue;
            }

            long score = backgroundTs > foregroundTs
                    ? 1_800_000L + backgroundTs
                    : 2_000_000L + foregroundTs;
            mergeScore(scoreByPackage, packageName, score);
        }
    }

    private void collectFromUsageStats(
            @NonNull Context context,
            @NonNull Map<String, Long> scoreByPackage
    ) {
        UsageStatsManager usageStatsManager = (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);
        if (usageStatsManager == null) {
            return;
        }

        long lookbackMs = VERIFICATION_LOOKBACK_MS;
        long now = System.currentTimeMillis();
        long begin = Math.max(0L, now - lookbackMs);
        List<UsageStats> usageStatsList = usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                begin,
                now
        );
        if (usageStatsList == null || usageStatsList.isEmpty()) {
            return;
        }

        for (UsageStats usageStats : usageStatsList) {
            if (usageStats == null) {
                continue;
            }

            String packageName = usageStats.getPackageName();
            if (TextUtils.isEmpty(packageName)) {
                continue;
            }

            long referenceTs = usageStats.getLastTimeUsed();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                referenceTs = Math.max(referenceTs, usageStats.getLastTimeVisible());
                referenceTs = Math.max(referenceTs, usageStats.getLastTimeForegroundServiceUsed());
            }

            if (referenceTs <= 0L || (now - referenceTs) > lookbackMs) {
                continue;
            }

            long score = 1_900_000L + referenceTs;
            mergeScore(scoreByPackage, packageName, score);
        }
    }

    private void collectFromAggregatedUsageStats(
            @NonNull Context context,
            @NonNull Map<String, Long> scoreByPackage
    ) {
        UsageStatsManager usageStatsManager = (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);
        if (usageStatsManager == null) {
            return;
        }

        long lookbackMs = VERIFICATION_LOOKBACK_MS;
        long now = System.currentTimeMillis();
        long begin = Math.max(0L, now - lookbackMs);
        Map<String, UsageStats> aggregated = usageStatsManager.queryAndAggregateUsageStats(begin, now);
        if (aggregated == null || aggregated.isEmpty()) {
            return;
        }

        for (Map.Entry<String, UsageStats> entry : aggregated.entrySet()) {
            String packageName = entry.getKey();
            UsageStats usageStats = entry.getValue();
            if (TextUtils.isEmpty(packageName) || usageStats == null) {
                continue;
            }

            long referenceTs = usageStats.getLastTimeUsed();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                referenceTs = Math.max(referenceTs, usageStats.getLastTimeVisible());
                referenceTs = Math.max(referenceTs, usageStats.getLastTimeForegroundServiceUsed());
            }

            if (referenceTs <= 0L || (now - referenceTs) > lookbackMs) {
                continue;
            }

            long score = 1_850_000L + referenceTs;
            mergeScore(scoreByPackage, packageName, score);
        }
    }

    private void mergeScore(@NonNull Map<String, Long> scoreByPackage, @Nullable String packageName, long score) {
        if (TextUtils.isEmpty(packageName)) {
            return;
        }

        Long previous = scoreByPackage.get(packageName);
        if (previous == null || score > previous) {
            scoreByPackage.put(packageName, score);
        }
    }

    private boolean isTurboModeEnabled() {
        if (settingsPrefs == null) {
            return false;
        }
        return settingsPrefs.getBoolean(CloudSyncManager.KEY_APPS_TURBO_MODE, false);
    }

    @NonNull
    private Set<String> getWhitelistedPackages() {
        if (settingsPrefs == null) {
            return Collections.emptySet();
        }

        Set<String> values = settingsPrefs.getStringSet(
                CloudSyncManager.KEY_APPS_WHITELIST_PACKAGES,
                Collections.emptySet()
        );
        if (values == null || values.isEmpty()) {
            return Collections.emptySet();
        }
        return new LinkedHashSet<>(values);
    }

    private void syncSystemInfoPreference() {
        showSystemInfoEnabled = settingsPrefs == null
                || settingsPrefs.getBoolean(CloudSyncManager.KEY_APPS_SHOW_SYSTEM_INFO, true);

        if (!showSystemInfoEnabled) {
            setStorageCardVisible(false);
            setThermalCardVisible(false);
            return;
        }

        setStorageCardVisible(true);
        updateStorageMetrics();
        updateThermalMetrics();
    }

    private void registerRecentlyForceStoppedPackages(@Nullable List<String> packageNames) {
        if (packageNames == null || packageNames.isEmpty()) {
            return;
        }

        invalidateBackgroundAppsCache();
        long now = System.currentTimeMillis();
        for (String packageName : packageNames) {
            if (!TextUtils.isEmpty(packageName)) {
                recentlyForceStoppedByPackage.put(packageName, now);
            }
        }
        pruneRecentlyForceStoppedPackages(now);
    }

    private void pruneRecentlyForceStoppedPackages() {
        pruneRecentlyForceStoppedPackages(System.currentTimeMillis());
    }

    private void pruneRecentlyForceStoppedPackages(long now) {
        if (recentlyForceStoppedByPackage.isEmpty()) {
            return;
        }

        List<String> expiredPackages = new ArrayList<>();
        for (Map.Entry<String, Long> entry : recentlyForceStoppedByPackage.entrySet()) {
            Long timestamp = entry.getValue();
            if (timestamp == null || now - timestamp > RECENT_FORCE_STOP_HIDE_MS) {
                expiredPackages.add(entry.getKey());
            }
        }

        for (String packageName : expiredPackages) {
            recentlyForceStoppedByPackage.remove(packageName);
        }
    }

    private boolean isRecentlyForceStopped(@Nullable String packageName, long now) {
        if (TextUtils.isEmpty(packageName)) {
            return false;
        }

        Long timestamp = recentlyForceStoppedByPackage.get(packageName);
        if (timestamp == null) {
            return false;
        }

        if (now - timestamp > RECENT_FORCE_STOP_HIDE_MS) {
            recentlyForceStoppedByPackage.remove(packageName);
            return false;
        }

        return true;
    }

    private void invalidateBackgroundAppsCache() {
        synchronized (backgroundAppsCacheLock) {
            cachedBackgroundApps = Collections.emptyList();
            cachedBackgroundHasUsageAccess = false;
            cachedBackgroundBuiltAtMs = 0L;
        }
    }

    private void cacheBackgroundAppsSnapshot(@NonNull List<BackgroundAppEntry> entries, boolean hasUsageAccess) {
        synchronized (backgroundAppsCacheLock) {
            cachedBackgroundApps = new ArrayList<>(entries);
            cachedBackgroundHasUsageAccess = hasUsageAccess;
            cachedBackgroundBuiltAtMs = System.currentTimeMillis();
        }
    }

    @Nullable
    private CachedBackgroundAppsSnapshot readCachedBackgroundAppsSnapshot(@NonNull Context context) {
        long now = System.currentTimeMillis();
        boolean currentHasUsageAccess = hasUsageStatsAccess(context);

        synchronized (backgroundAppsCacheLock) {
            if (cachedBackgroundBuiltAtMs <= 0L) {
                return null;
            }
            if ((now - cachedBackgroundBuiltAtMs) > BACKGROUND_APPS_CACHE_TTL_MS) {
                return null;
            }
            if (cachedBackgroundHasUsageAccess != currentHasUsageAccess) {
                return null;
            }
            return new CachedBackgroundAppsSnapshot(cachedBackgroundApps, currentHasUsageAccess);
        }
    }

    @NonNull
    private List<BackgroundAppEntry> getCachedOrCollectBackgroundAppsForVerification(@NonNull Context context) {
        CachedBackgroundAppsSnapshot cachedSnapshot = readCachedBackgroundAppsSnapshot(context);
        if (cachedSnapshot != null) {
            return cachedSnapshot.entries;
        }

        List<BackgroundAppEntry> freshEntries = collectBackgroundAppsForVerification(context);
        cacheBackgroundAppsSnapshot(freshEntries, hasUsageStatsAccess(context));
        return freshEntries;
    }

    private void updateLatestTimestamp(
            @NonNull Map<String, Long> timestampsByPackage,
            @NonNull String packageName,
            long timestamp
    ) {
        Long previousValue = timestampsByPackage.get(packageName);
        long previousTimestamp = previousValue != null ? previousValue : 0L;
        if (timestamp > previousTimestamp) {
            timestampsByPackage.put(packageName, timestamp);
        }
    }

    private long readTimestamp(@NonNull Map<String, Long> timestampsByPackage, @NonNull String packageName) {
        Long value = timestampsByPackage.get(packageName);
        return value != null ? value : 0L;
    }

    @NonNull
    private Set<String> collectRunningPackagesSnapshot() {
        if (activityManager == null) {
            return Collections.emptySet();
        }

        List<ActivityManager.RunningAppProcessInfo> processes = activityManager.getRunningAppProcesses();
        if (processes == null || processes.isEmpty()) {
            return Collections.emptySet();
        }

        Set<String> result = new LinkedHashSet<>();
        for (ActivityManager.RunningAppProcessInfo info : processes) {
            if (info == null || info.importance >= ActivityManager.RunningAppProcessInfo.IMPORTANCE_EMPTY) {
                continue;
            }

            if (info.pkgList != null) {
                for (String packageName : info.pkgList) {
                    if (!TextUtils.isEmpty(packageName)) {
                        result.add(packageName);
                    }
                }
            }

            String processName = info.processName;
            if (!TextUtils.isEmpty(processName)) {
                int split = processName.indexOf(':');
                String packageName = split > 0 ? processName.substring(0, split) : processName;
                if (!TextUtils.isEmpty(packageName)) {
                    result.add(packageName);
                }
            }
        }

        return result;
    }

    private boolean isPackageStopped(@NonNull PackageManager packageManager, @NonNull String packageName) {
        try {
            ApplicationInfo info = packageManager.getApplicationInfo(packageName, 0);
            return (info.flags & ApplicationInfo.FLAG_STOPPED) != 0;
        } catch (PackageManager.NameNotFoundException ignored) {
            return false;
        }
    }

    private boolean hasUsageStatsAccess(@NonNull Context context) {
        AppOpsManager appOpsManager = (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);
        if (appOpsManager == null) {
            return false;
        }

        int mode = appOpsManager.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                context.getPackageName()
        );
        return mode == AppOpsManager.MODE_ALLOWED;
    }

    private boolean isForceStopCandidate(
            @NonNull PackageManager packageManager,
            @Nullable String packageName,
            @NonNull String ownPackage,
            @Nullable String defaultHomePackage
    ) {
        if (TextUtils.isEmpty(packageName)) {
            return false;
        }
        if (ownPackage.equals(packageName)) {
            return false;
        }
        if (defaultHomePackage != null && defaultHomePackage.equals(packageName)) {
            return false;
        }
        if (isProtectedPackage(packageName)) {
            return false;
        }
        if (isSystemPackage(packageManager, packageName)) {
            return false;
        }
        if (isPackageStopped(packageManager, packageName)) {
            return false;
        }

        return packageManager.getLaunchIntentForPackage(packageName) != null;
    }

    private boolean isForceStopVerificationCandidate(
            @NonNull PackageManager packageManager,
            @Nullable String packageName,
            @NonNull String ownPackage,
            @Nullable String defaultHomePackage
    ) {
        if (packageName == null) {
            return false;
        }
        return isVerificationCandidate(packageName, ownPackage, defaultHomePackage)
                && !isSystemPackage(packageManager, packageName)
                && !isPackageStopped(packageManager, packageName);
    }

    @Nullable
    private BackgroundAppEntry buildBackgroundEntryFromLaunchIntent(
            @NonNull PackageManager packageManager,
            @NonNull String packageName
    ) {
        Intent launchIntent = packageManager.getLaunchIntentForPackage(packageName);
        if (launchIntent == null) {
            return null;
        }

        ResolveInfo resolveInfo = packageManager.resolveActivity(launchIntent, PackageManager.MATCH_DEFAULT_ONLY);
        if (resolveInfo == null) {
            return null;
        }

        CharSequence labelValue = resolveInfo.loadLabel(packageManager);
        String label = TextUtils.isEmpty(labelValue) ? packageName : labelValue.toString();
        Drawable icon = resolveInfo.loadIcon(packageManager);
        return new BackgroundAppEntry(label, packageName, icon);
    }

    private boolean isVerificationCandidate(
            @Nullable String packageName,
            @NonNull String ownPackage,
            @Nullable String defaultHomePackage
    ) {
        if (TextUtils.isEmpty(packageName)) {
            return false;
        }
        if (ownPackage.equals(packageName)) {
            return false;
        }
        if (defaultHomePackage != null && defaultHomePackage.equals(packageName)) {
            return false;
        }
        if (isProtectedPackage(packageName)) {
            return false;
        }

        return !packageName.equals("android")
                && !packageName.startsWith("com.android.settings")
                && !packageName.startsWith("com.android.systemui")
                && !packageName.startsWith("com.android.mtp")
                && !packageName.startsWith("com.sec.android.app.launcher")
                && !packageName.startsWith("com.samsung.android.app.launcher")
                && !packageName.startsWith("com.google.android.permissioncontroller");
    }

    @Nullable
    private String getDefaultHomePackage(@NonNull PackageManager packageManager) {
        Intent homeIntent = new Intent(Intent.ACTION_MAIN);
        homeIntent.addCategory(Intent.CATEGORY_HOME);
        ResolveInfo resolveInfo = packageManager.resolveActivity(homeIntent, PackageManager.MATCH_DEFAULT_ONLY);
        if (resolveInfo == null || resolveInfo.activityInfo == null) {
            return null;
        }
        return resolveInfo.activityInfo.packageName;
    }

    private boolean isProtectedPackage(@NonNull String packageName) {
        return packageName.startsWith("com.android.launcher")
                || packageName.startsWith("com.google.android.apps.nexuslauncher")
                || packageName.equals("com.android.settings")
                || packageName.equals("com.android.systemui")
                || packageName.equals("com.google.android.permissioncontroller");
    }

    private boolean isSystemPackage(@NonNull PackageManager packageManager, @NonNull String packageName) {
        try {
            ApplicationInfo info = packageManager.getApplicationInfo(packageName, 0);
            boolean isSystem = (info.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
            boolean isUpdatedSystem = (info.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0;
            return isSystem && !isUpdatedSystem;
        } catch (PackageManager.NameNotFoundException ignored) {
            return false;
        }
    }

    private static final class TargetPackagesResult {
        final Set<String> packages;
        final boolean usageAccessMissing;

        TargetPackagesResult(@NonNull Set<String> packages, boolean usageAccessMissing) {
            this.packages = new LinkedHashSet<>(packages);
            this.usageAccessMissing = usageAccessMissing;
        }
    }

    private static final class DeepCleanStats {
        final long cacheBytes;
        final long tempBytes;
        final long junkBytes;

        DeepCleanStats(long cacheBytes, long tempBytes, long junkBytes) {
            this.cacheBytes = Math.max(0L, cacheBytes);
            this.tempBytes = Math.max(0L, tempBytes);
            this.junkBytes = Math.max(0L, junkBytes);
        }

        long totalBytes() {
            return cacheBytes + tempBytes + junkBytes;
        }
    }

    private static final class StorageUsageSnapshot {
        final long usedBytes;
        final long totalBytes;
        final int percent;

        StorageUsageSnapshot(long usedBytes, long totalBytes, int percent) {
            this.usedBytes = Math.max(0L, usedBytes);
            this.totalBytes = Math.max(0L, totalBytes);
            this.percent = Math.max(0, Math.min(100, percent));
        }
    }

    private static final class BackgroundAppEntry {
        final String label;
        final String packageName;
        final Drawable icon;

        BackgroundAppEntry(@NonNull String label, @NonNull String packageName, @NonNull Drawable icon) {
            this.label = label;
            this.packageName = packageName;
            this.icon = icon;
        }
    }

    private static final class CachedBackgroundAppsSnapshot {
        final List<BackgroundAppEntry> entries;
        final boolean hasUsageAccess;

        CachedBackgroundAppsSnapshot(@NonNull List<BackgroundAppEntry> entries, boolean hasUsageAccess) {
            this.entries = new ArrayList<>(entries);
            this.hasUsageAccess = hasUsageAccess;
        }
    }
}
