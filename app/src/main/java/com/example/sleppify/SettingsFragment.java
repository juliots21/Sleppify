package com.example.sleppify;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.StatFs;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.imageview.ShapeableImageView;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.auth.FirebaseUser;

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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SettingsFragment extends Fragment {

    private static final long WHITELIST_CACHE_TTL_MS = 2L * 60L * 1000L;
    private static final String DELETE_CONFIRM_WORD = "eliminar";

    private MaterialCardView cardProfile;
    private ShapeableImageView ivProfilePhoto;
    private TextView tvProfileName;
    private TextView tvProfileBadge;
    private TextView tvSummaryFrequencyValue;
    private MaterialSwitch swAiShiftPermissions;
    private MaterialSwitch swAmoledMode;
    private MaterialSwitch swDownloadOnMobileData;
    private View rowSummaryFrequency;
    private View rowAppsWhitelist;
    private View rowDownloadQuality;
    private TextView tvAppsWhitelistValue;
    private TextView tvDownloadQualityValue;
    private TextView tvOfflineCrossfadeThumbValue;
    private TextView tvStorageOtherAppsValue;
    private TextView tvStorageDownloadsValue;
    private TextView tvStorageCacheValue;
    private TextView tvStorageFreeValue;
    private SeekBar sbOfflineCrossfade;
    private View vStorageOtherApps;
    private View vStorageDownloads;
    private View vStorageCache;
    private View vStorageFree;
    private MaterialButton btnDeleteSettingsCache;

    private SharedPreferences settingsPrefs;
    private AuthManager authManager;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService whitelistExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService storageExecutor = Executors.newSingleThreadExecutor();
    private final Object whitelistCacheLock = new Object();
    @NonNull
    private List<WhitelistAppEntry> cachedWhitelistCandidates = Collections.emptyList();
    private long cachedWhitelistBuiltAtMs;
    private boolean whitelistDialogLoading;
    private boolean deleteAccountInFlight;
    private boolean settingsUiBound;
    private boolean hasSettingsSnapshot;
    private boolean lastSmartSuggestionsEnabled;
    private boolean lastAmoledModeEnabled;
    private int lastWhitelistCount = -1;
    private int lastSummaryFrequencyTimes = -1;
    private int lastOfflineCrossfadeSeconds = -1;
    private boolean lastAllowMobileDataDownloads;
    @Nullable
    private String lastDownloadQuality;
    @Nullable
    private StorageBreakdown lastStorageBreakdown;
    private boolean storageCleanupInFlight;
    private boolean hasProfileSnapshot;
    private boolean lastProfileSignedIn;
    @Nullable
    private String lastProfileDisplayName;
    @Nullable
    private String lastProfileEmail;
    @Nullable
    private String lastProfilePhotoUrl;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        settingsPrefs = requireContext().getSharedPreferences(CloudSyncManager.PREFS_SETTINGS, Context.MODE_PRIVATE);
        authManager = AuthManager.getInstance(requireContext());

        cardProfile = view.findViewById(R.id.cardProfile);
        ivProfilePhoto = view.findViewById(R.id.ivProfilePhoto);
        tvProfileName = view.findViewById(R.id.tvProfileName);
        tvProfileBadge = view.findViewById(R.id.tvProfileBadge);
        tvSummaryFrequencyValue = view.findViewById(R.id.tvSummaryFrequencyValue);
        swAiShiftPermissions = view.findViewById(R.id.swAiShiftPermissions);
        swAmoledMode = view.findViewById(R.id.swAmoledMode);
        swDownloadOnMobileData = view.findViewById(R.id.swDownloadOnMobileData);
        rowSummaryFrequency = view.findViewById(R.id.rowSummaryFrequency);
        rowAppsWhitelist = view.findViewById(R.id.rowAppsWhitelist);
        rowDownloadQuality = view.findViewById(R.id.rowDownloadQuality);
        tvAppsWhitelistValue = view.findViewById(R.id.tvAppsWhitelistValue);
        tvDownloadQualityValue = view.findViewById(R.id.tvDownloadQualityValue);
        tvOfflineCrossfadeThumbValue = view.findViewById(R.id.tvOfflineCrossfadeThumbValue);
        sbOfflineCrossfade = view.findViewById(R.id.sbOfflineCrossfade);
        tvStorageOtherAppsValue = view.findViewById(R.id.tvStorageOtherAppsValue);
        tvStorageDownloadsValue = view.findViewById(R.id.tvStorageDownloadsValue);
        tvStorageCacheValue = view.findViewById(R.id.tvStorageCacheValue);
        tvStorageFreeValue = view.findViewById(R.id.tvStorageFreeValue);
        vStorageOtherApps = view.findViewById(R.id.vStorageOtherApps);
        vStorageDownloads = view.findViewById(R.id.vStorageDownloads);
        vStorageCache = view.findViewById(R.id.vStorageCache);
        vStorageFree = view.findViewById(R.id.vStorageFree);
        btnDeleteSettingsCache = view.findViewById(R.id.btnDeleteSettingsCache);

        renderSettingsState();
        renderProfileState();
        setupCalendarSettingsInteractions();
        setupAppsSettingsInteractions();
        setupStreamingSettingsInteractions();
        setupStorageInteractions();
        prewarmWhitelistCandidatesAsync();
        refreshStorageBreakdownAsync();

        cardProfile.setOnClickListener(v -> {
            if (authManager.isSignedIn()) {
                showAccountActionsDialog();
                return;
            }

            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).requireAuth(this::renderProfileState, this::renderProfileState);
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        renderSettingsState();
        renderProfileState();
        refreshStorageBreakdownAsync();
    }

    @Override
    public void onDestroyView() {
        settingsUiBound = false;
        super.onDestroyView();
    }

    @Override
    public void onDestroy() {
        whitelistExecutor.shutdownNow();
        storageExecutor.shutdownNow();
        super.onDestroy();
    }

    private void setupStorageInteractions() {
        if (btnDeleteSettingsCache != null) {
            btnDeleteSettingsCache.setOnClickListener(v -> showDeleteCacheConfirmation());
        }
        updateDeleteCacheButtonState();
    }

    private void showDeleteCacheConfirmation() {
        if (!isAdded() || storageCleanupInFlight) {
            return;
        }

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.settings_storage_delete_cache_title)
                .setMessage(R.string.settings_storage_delete_cache_message)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.settings_storage_delete_cache_confirm, (dialog, which) -> clearSettingsCacheAsync())
                .show();
    }

    private void clearSettingsCacheAsync() {
        if (!isAdded() || storageCleanupInFlight) {
            return;
        }

        storageCleanupInFlight = true;
        updateDeleteCacheButtonState();

        Context appContext = requireContext().getApplicationContext();
        storageExecutor.execute(() -> {
            long freedBytes = clearAppCache(appContext);

            mainHandler.post(() -> {
                if (!isAdded()) {
                    storageCleanupInFlight = false;
                    return;
                }

                storageCleanupInFlight = false;
                updateDeleteCacheButtonState();
                if (freedBytes >= 0L) {
                    Toast.makeText(requireContext(), R.string.settings_storage_delete_cache_done, Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(requireContext(), R.string.settings_storage_delete_cache_error, Toast.LENGTH_SHORT).show();
                }
                refreshStorageBreakdownAsync();
            });
        });
    }

    private long clearAppCache(@NonNull Context context) {
        try {
            long deletedBytes = 0L;
            for (File cacheRoot : collectAppCacheRoots(context)) {
                deletedBytes += clearDirectoryContents(cacheRoot);
            }

            try {
                Glide.get(context).clearDiskCache();
            } catch (Exception ignored) {
            }

            mainHandler.post(() -> {
                try {
                    Glide.get(context).clearMemory();
                } catch (Exception ignored) {
                }
            });

            return Math.max(0L, deletedBytes);
        } catch (Exception ignored) {
            return -1L;
        }
    }

    private void refreshStorageBreakdownAsync() {
        if (!isAdded()) {
            return;
        }

        Context appContext = requireContext().getApplicationContext();
        storageExecutor.execute(() -> {
            StorageBreakdown breakdown = calculateStorageBreakdown(appContext);
            mainHandler.post(() -> {
                if (!isAdded()) {
                    return;
                }
                renderStorageBreakdown(breakdown);
            });
        });
    }

    private void renderStorageBreakdown(@Nullable StorageBreakdown breakdown) {
        if (breakdown == null
                || tvStorageOtherAppsValue == null
                || tvStorageDownloadsValue == null
                || tvStorageCacheValue == null
                || tvStorageFreeValue == null) {
            return;
        }

        lastStorageBreakdown = breakdown;

        tvStorageOtherAppsValue.setText(getString(
                R.string.settings_storage_row_value_format,
                getString(R.string.settings_storage_other_apps),
                formatStorageSize(breakdown.otherAppsBytes)
        ));
        tvStorageDownloadsValue.setText(getString(
                R.string.settings_storage_row_value_format,
                getString(R.string.settings_storage_downloads),
                formatStorageSize(breakdown.downloadsBytes)
        ));
        tvStorageCacheValue.setText(getString(
                R.string.settings_storage_row_value_format,
                getString(R.string.settings_storage_cache),
                formatStorageSize(breakdown.cacheBytes)
        ));
        tvStorageFreeValue.setText(getString(
                R.string.settings_storage_row_value_format,
                getString(R.string.settings_storage_free),
                formatStorageSize(breakdown.freeBytes)
        ));

        long shownTotal = Math.max(1L,
                breakdown.otherAppsBytes + breakdown.downloadsBytes + breakdown.cacheBytes + breakdown.freeBytes
        );
        applyStorageSegmentWeight(vStorageOtherApps, breakdown.otherAppsBytes, shownTotal);
        applyStorageSegmentWeight(vStorageDownloads, breakdown.downloadsBytes, shownTotal);
        applyStorageSegmentWeight(vStorageCache, breakdown.cacheBytes, shownTotal);
        applyStorageSegmentWeight(vStorageFree, breakdown.freeBytes, shownTotal);
    }

    private void applyStorageSegmentWeight(@Nullable View segment, long bytes, long total) {
        if (segment == null) {
            return;
        }
        ViewGroup.LayoutParams rawParams = segment.getLayoutParams();
        if (!(rawParams instanceof LinearLayout.LayoutParams)) {
            return;
        }

        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) rawParams;
        if (bytes <= 0L || total <= 0L) {
            params.weight = 0f;
            segment.setLayoutParams(params);
            segment.setVisibility(View.GONE);
            return;
        }

        float ratio = bytes / (float) total;
        params.weight = Math.max(0.025f, ratio);
        segment.setLayoutParams(params);
        segment.setVisibility(View.VISIBLE);
    }

    @Nullable
    private StorageBreakdown calculateStorageBreakdown(@NonNull Context context) {
        try {
            StatFs statFs = new StatFs(Environment.getDataDirectory().getAbsolutePath());
            long totalBytes = Math.max(0L, statFs.getTotalBytes());
            long freeBytes = Math.max(0L, statFs.getAvailableBytes());
            long usedBytes = Math.max(0L, totalBytes - freeBytes);

            long downloadsBytes = calculateDirectorySize(resolveDownloadsRoot(context));

            long cacheBytes = 0L;
            for (File cacheRoot : collectAppCacheRoots(context)) {
                cacheBytes += calculateDirectorySize(cacheRoot);
            }

            long appPersistentBytes = 0L;
            for (File persistentRoot : collectAppPersistentRoots(context)) {
                appPersistentBytes += calculateDirectorySize(persistentRoot);
            }

            // Excluye el uso de Sleppify para aproximar "Otras apps" como almacenamiento de terceros.
            long otherAppsBytes = Math.max(0L, usedBytes - downloadsBytes - cacheBytes - appPersistentBytes);

            return new StorageBreakdown(
                    totalBytes,
                    freeBytes,
                    downloadsBytes,
                    cacheBytes,
                    otherAppsBytes,
                    appPersistentBytes
            );
        } catch (Exception ignored) {
            return null;
        }
    }

    @Nullable
    private File resolveDownloadsRoot(@NonNull Context context) {
        File publicDownloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        if (publicDownloads != null && publicDownloads.exists()) {
            return publicDownloads;
        }
        return context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
    }

    @NonNull
    private List<File> collectAppCacheRoots(@NonNull Context context) {
        List<File> roots = new ArrayList<>();
        LinkedHashSet<String> seen = new LinkedHashSet<>();

        addDistinctDirectory(roots, seen, context.getCacheDir());
        addDistinctDirectory(roots, seen, context.getCodeCacheDir());

        File[] externalCacheDirs = context.getExternalCacheDirs();
        if (externalCacheDirs != null) {
            for (File dir : externalCacheDirs) {
                addDistinctDirectory(roots, seen, dir);
            }
        }

        return roots;
    }

    @NonNull
    private List<File> collectAppPersistentRoots(@NonNull Context context) {
        List<File> roots = new ArrayList<>();
        LinkedHashSet<String> seen = new LinkedHashSet<>();

        addDistinctDirectory(roots, seen, context.getFilesDir());
        addDistinctDirectory(roots, seen, context.getNoBackupFilesDir());
        addDistinctDirectory(roots, seen, new File(context.getApplicationInfo().dataDir, "databases"));
        addDistinctDirectory(roots, seen, new File(context.getApplicationInfo().dataDir, "shared_prefs"));

        File[] externalFilesDirs = context.getExternalFilesDirs(null);
        if (externalFilesDirs != null) {
            for (File dir : externalFilesDirs) {
                addDistinctDirectory(roots, seen, dir);
            }
        }

        File[] externalMediaDirs = context.getExternalMediaDirs();
        if (externalMediaDirs != null) {
            for (File dir : externalMediaDirs) {
                addDistinctDirectory(roots, seen, dir);
            }
        }

        addDistinctDirectory(roots, seen, context.getObbDir());
        return roots;
    }

    private void addDistinctDirectory(
            @NonNull List<File> output,
            @NonNull LinkedHashSet<String> seenPaths,
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

    private long calculateDirectorySize(@Nullable File root) {
        if (root == null || !root.exists()) {
            return 0L;
        }
        if (root.isFile()) {
            return Math.max(0L, root.length());
        }

        long total = 0L;
        File[] children;
        try {
            children = root.listFiles();
        } catch (SecurityException ignored) {
            return 0L;
        }
        if (children == null) {
            return 0L;
        }

        for (File child : children) {
            total += calculateDirectorySize(child);
        }
        return total;
    }

    private long clearDirectoryContents(@Nullable File directory) {
        if (directory == null || !directory.exists() || !directory.isDirectory()) {
            return 0L;
        }

        long deletedBytes = 0L;
        File[] children;
        try {
            children = directory.listFiles();
        } catch (SecurityException ignored) {
            return 0L;
        }
        if (children == null) {
            return 0L;
        }

        for (File child : children) {
            deletedBytes += deleteRecursively(child);
        }
        return deletedBytes;
    }

    private long deleteRecursively(@Nullable File file) {
        if (file == null || !file.exists()) {
            return 0L;
        }

        if (file.isFile()) {
            long size = Math.max(0L, file.length());
            if (file.delete()) {
                return size;
            }
            return 0L;
        }

        long deletedBytes = 0L;
        File[] children;
        try {
            children = file.listFiles();
        } catch (SecurityException ignored) {
            children = null;
        }
        if (children != null) {
            for (File child : children) {
                deletedBytes += deleteRecursively(child);
            }
        }

        file.delete();
        return deletedBytes;
    }

    @NonNull
    private String formatStorageSize(long bytes) {
        long safeBytes = Math.max(0L, bytes);
        double mb = safeBytes / (1024d * 1024d);
        if (mb < 1024d) {
            return String.format(Locale.getDefault(), "%.0f MB", mb);
        }
        double gb = mb / 1024d;
        return String.format(Locale.getDefault(), "%.2f GB", gb);
    }

    private void updateDeleteCacheButtonState() {
        if (btnDeleteSettingsCache == null) {
            return;
        }

        btnDeleteSettingsCache.setEnabled(!storageCleanupInFlight);
        btnDeleteSettingsCache.setText(storageCleanupInFlight
                ? R.string.settings_storage_delete_cache_running
                : R.string.settings_storage_delete_cache);
    }

    private void prewarmWhitelistCandidatesAsync() {
        Context appContext = requireContext().getApplicationContext();
        whitelistExecutor.execute(() -> {
            try {
                getCachedOrLoadWhitelistCandidates(appContext);
            } catch (Throwable ignored) {
                // Cache warm-up is best-effort.
            }
        });
    }

    private void renderSettingsState() {
        boolean smartSuggestionsEnabled = settingsPrefs.getBoolean(
                CloudSyncManager.KEY_SMART_SUGGESTIONS_ENABLED,
                settingsPrefs.getBoolean(CloudSyncManager.KEY_AI_SHIFT_ENABLED, true)
        );
        boolean amoledModeEnabled = settingsPrefs.getBoolean(CloudSyncManager.KEY_AMOLED_MODE_ENABLED, false);
        int whitelistCount = getWhitelistedPackages().size();
        int summaryFrequencyTimes = settingsPrefs.getInt(CloudSyncManager.KEY_DAILY_SUMMARY_INTERVAL_HOURS, 2);
        int offlineCrossfadeSeconds = Math.max(
            0,
            Math.min(12, settingsPrefs.getInt(CloudSyncManager.KEY_OFFLINE_CROSSFADE_SECONDS, 0))
        );
        boolean allowMobileDataDownloads = settingsPrefs.getBoolean(
                CloudSyncManager.KEY_OFFLINE_DOWNLOAD_ALLOW_MOBILE_DATA,
                false
        );
        String downloadQuality = normalizeDownloadQuality(
            settingsPrefs.getString(
                CloudSyncManager.KEY_OFFLINE_DOWNLOAD_QUALITY,
                CloudSyncManager.DOWNLOAD_QUALITY_MEDIUM
            )
        );

        if (settingsUiBound
            && hasSettingsSnapshot
                && lastSmartSuggestionsEnabled == smartSuggestionsEnabled
                && lastAmoledModeEnabled == amoledModeEnabled
                && lastWhitelistCount == whitelistCount
            && lastSummaryFrequencyTimes == summaryFrequencyTimes
            && lastOfflineCrossfadeSeconds == offlineCrossfadeSeconds
            && lastAllowMobileDataDownloads == allowMobileDataDownloads
            && TextUtils.equals(lastDownloadQuality, downloadQuality)) {
            return;
        }

        hasSettingsSnapshot = true;
        settingsUiBound = true;
        lastSmartSuggestionsEnabled = smartSuggestionsEnabled;
        lastAmoledModeEnabled = amoledModeEnabled;
        lastWhitelistCount = whitelistCount;
        lastSummaryFrequencyTimes = summaryFrequencyTimes;
        lastOfflineCrossfadeSeconds = offlineCrossfadeSeconds;
        lastAllowMobileDataDownloads = allowMobileDataDownloads;
        lastDownloadQuality = downloadQuality;

        swAiShiftPermissions.setOnCheckedChangeListener(null);
        swAiShiftPermissions.setChecked(smartSuggestionsEnabled);
        swAiShiftPermissions.setOnCheckedChangeListener((buttonView, isChecked) -> {
            settingsPrefs.edit()
                    .putBoolean(CloudSyncManager.KEY_SMART_SUGGESTIONS_ENABLED, isChecked)
                    .putBoolean(CloudSyncManager.KEY_AI_SHIFT_ENABLED, isChecked)
                    .apply();

            Context appContext = requireContext().getApplicationContext();
            if (isChecked) {
                int timesPerDay = settingsPrefs.getInt(CloudSyncManager.KEY_DAILY_SUMMARY_INTERVAL_HOURS, 2);
                DailyAgendaNotificationWorker.schedule(appContext, timesPerDay);
                TaskReminderScheduler.rescheduleAll(appContext);
            } else {
                DailyAgendaNotificationWorker.cancel(appContext);
                TaskReminderScheduler.cancelAll(appContext);
            }

            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).refreshSessionUi();
            }
        });

        if (swAmoledMode != null) {
            swAmoledMode.setOnCheckedChangeListener(null);
            swAmoledMode.setChecked(amoledModeEnabled);
            swAmoledMode.setOnCheckedChangeListener((buttonView, isChecked) -> {
                settingsPrefs.edit()
                        .putBoolean(CloudSyncManager.KEY_AMOLED_MODE_ENABLED, isChecked)
                        .apply();
                CloudSyncManager.getInstance(requireContext()).syncSettingsNowIfSignedIn();

                int targetMode = isChecked
                        ? AppCompatDelegate.MODE_NIGHT_YES
                        : AppCompatDelegate.MODE_NIGHT_NO;
                if (AppCompatDelegate.getDefaultNightMode() != targetMode) {
                    AppCompatDelegate.setDefaultNightMode(targetMode);
                }
            });
        }

        if (swDownloadOnMobileData != null) {
            swDownloadOnMobileData.setOnCheckedChangeListener(null);
            swDownloadOnMobileData.setChecked(allowMobileDataDownloads);
            swDownloadOnMobileData.setOnCheckedChangeListener((buttonView, isChecked) -> {
                settingsPrefs.edit()
                        .putBoolean(CloudSyncManager.KEY_OFFLINE_DOWNLOAD_ALLOW_MOBILE_DATA, isChecked)
                        .apply();
                renderSettingsState();
            });
        }

        if (tvAppsWhitelistValue != null) {
            if (whitelistCount <= 0) {
                tvAppsWhitelistValue.setText(R.string.settings_apps_whitelist_none);
            } else {
                tvAppsWhitelistValue.setText(getString(R.string.settings_apps_whitelist_count, whitelistCount));
            }
        }

        tvSummaryFrequencyValue.setText(formatSummaryFrequency(summaryFrequencyTimes));

        if (tvDownloadQualityValue != null) {
            tvDownloadQualityValue.setText(labelForDownloadQuality(downloadQuality));
        }

        if (sbOfflineCrossfade != null) {
            sbOfflineCrossfade.setOnSeekBarChangeListener(null);
            sbOfflineCrossfade.setMax(12);
            sbOfflineCrossfade.setProgress(offlineCrossfadeSeconds);
            updateOfflineCrossfadeThumbIndicator(offlineCrossfadeSeconds);
            sbOfflineCrossfade.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                private int pendingValue = offlineCrossfadeSeconds;

                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    int safeValue = Math.max(0, Math.min(12, progress));
                    pendingValue = safeValue;
                    updateOfflineCrossfadeThumbIndicator(safeValue);
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {
                    // No-op.
                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                    settingsPrefs.edit()
                            .putInt(CloudSyncManager.KEY_OFFLINE_CROSSFADE_SECONDS, pendingValue)
                            .apply();
                    renderSettingsState();
                }
            });
        }
    }

    private void updateOfflineCrossfadeThumbIndicator(int seconds) {
        int safeSeconds = Math.max(0, Math.min(12, seconds));
        if (sbOfflineCrossfade == null || tvOfflineCrossfadeThumbValue == null) {
            return;
        }

        tvOfflineCrossfadeThumbValue.setText(formatCrossfadeSeconds(safeSeconds));

        sbOfflineCrossfade.post(() -> {
            if (sbOfflineCrossfade == null || tvOfflineCrossfadeThumbValue == null) {
                return;
            }

            View parent = (View) tvOfflineCrossfadeThumbValue.getParent();
            if (parent == null) {
                return;
            }

            int parentWidth = parent.getWidth();
            if (parentWidth <= 0) {
                return;
            }

            int max = Math.max(1, sbOfflineCrossfade.getMax());
            float ratio = safeSeconds / (float) max;
            float trackWidth = Math.max(0f, sbOfflineCrossfade.getWidth()
                    - sbOfflineCrossfade.getPaddingLeft()
                    - sbOfflineCrossfade.getPaddingRight());
            float thumbCenterX = sbOfflineCrossfade.getPaddingLeft() + (trackWidth * ratio);

            int textWidth = tvOfflineCrossfadeThumbValue.getWidth();
            if (textWidth <= 0) {
                tvOfflineCrossfadeThumbValue.measure(
                        View.MeasureSpec.UNSPECIFIED,
                        View.MeasureSpec.UNSPECIFIED
                );
                textWidth = tvOfflineCrossfadeThumbValue.getMeasuredWidth();
            }

            float targetX = thumbCenterX - (textWidth / 2f);
            float maxX = Math.max(0f, parentWidth - textWidth);
            float clampedX = Math.max(0f, Math.min(targetX, maxX));
            tvOfflineCrossfadeThumbValue.setTranslationX(clampedX);
        });
    }

    private void setupCalendarSettingsInteractions() {
        if (rowSummaryFrequency != null) {
            rowSummaryFrequency.setOnClickListener(v -> showSummaryFrequencyPicker());
        }
    }

    private void setupAppsSettingsInteractions() {
        if (rowAppsWhitelist != null) {
            rowAppsWhitelist.setOnClickListener(v -> showAppsWhitelistDialog());
        }
    }

    private void setupStreamingSettingsInteractions() {
        if (rowDownloadQuality != null) {
            rowDownloadQuality.setOnClickListener(v -> showDownloadQualityPicker());
        }
    }

    private void showDownloadQualityPicker() {
        final String[] values = new String[]{
                CloudSyncManager.DOWNLOAD_QUALITY_LOW,
                CloudSyncManager.DOWNLOAD_QUALITY_MEDIUM,
                CloudSyncManager.DOWNLOAD_QUALITY_HIGH,
                CloudSyncManager.DOWNLOAD_QUALITY_VERY_HIGH
        };
        final String[] labels = new String[]{
                getString(R.string.settings_stream_download_quality_low),
                getString(R.string.settings_stream_download_quality_medium),
                getString(R.string.settings_stream_download_quality_high),
                getString(R.string.settings_stream_download_quality_very_high)
        };

        String current = normalizeDownloadQuality(
                settingsPrefs.getString(
                        CloudSyncManager.KEY_OFFLINE_DOWNLOAD_QUALITY,
                        CloudSyncManager.DOWNLOAD_QUALITY_MEDIUM
                )
        );
        int checked = indexOf(values, current);

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.settings_stream_download_quality_title)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    settingsPrefs.edit()
                            .putString(CloudSyncManager.KEY_OFFLINE_DOWNLOAD_QUALITY, values[which])
                            .apply();
                    renderSettingsState();
                    dialog.dismiss();
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void showAppsWhitelistDialog() {
        if (!isAdded() || whitelistDialogLoading) {
            return;
        }

        final Set<String> selectedPackages = new LinkedHashSet<>(getWhitelistedPackages());
        List<WhitelistAppEntry> cachedCandidates = getFreshWhitelistCandidatesFromCache();
        if (!cachedCandidates.isEmpty()) {
            showAppsWhitelistSelectionDialog(cachedCandidates, selectedPackages);
            return;
        }

        whitelistDialogLoading = true;

        ProgressBar progressBar = new ProgressBar(requireContext());
        int progressPadding = dp(18);
        progressBar.setPadding(progressPadding, progressPadding, progressPadding, progressPadding);

        AlertDialog loadingDialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.settings_apps_whitelist_title)
                .setView(progressBar)
                .setCancelable(false)
                .create();
        loadingDialog.show();

        Context appContext = requireContext().getApplicationContext();

        whitelistExecutor.execute(() -> {
            List<WhitelistAppEntry> candidates;
            try {
                candidates = getCachedOrLoadWhitelistCandidates(appContext);
            } catch (Throwable ignored) {
                candidates = Collections.emptyList();
            }
            final List<WhitelistAppEntry> finalCandidates = candidates;
            mainHandler.post(() -> {
                whitelistDialogLoading = false;
                if (loadingDialog.isShowing()) {
                    loadingDialog.dismiss();
                }

                if (!isAdded()) {
                    return;
                }

                if (finalCandidates.isEmpty()) {
                    
                    return;
                }

                showAppsWhitelistSelectionDialog(finalCandidates, selectedPackages);
            });
        });
    }

    private void showAppsWhitelistSelectionDialog(
            @NonNull List<WhitelistAppEntry> candidates,
            @NonNull Set<String> selectedPackages
    ) {
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_apps_whitelist, null, false);
        RecyclerView recyclerView = dialogView.findViewById(R.id.rvWhitelistAppsDialog);

        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerView.setHasFixedSize(true);
        recyclerView.setItemAnimator(null);
        recyclerView.setItemViewCacheSize(16);
        recyclerView.getRecycledViewPool().setMaxRecycledViews(0, 24);
        recyclerView.setAdapter(new WhitelistAppsAdapter(candidates, selectedPackages));

        ViewGroup.LayoutParams listParams = recyclerView.getLayoutParams();
        listParams.height = Math.min(dp(420), Math.max(dp(220), candidates.size() * dp(72)));
        recyclerView.setLayoutParams(listParams);

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.settings_apps_whitelist_title)
            .setMessage(R.string.settings_apps_whitelist_dialog_hint)
                .setView(dialogView)
                .setNeutralButton(R.string.settings_apps_whitelist_clear, (dialog, which) -> {
                    settingsPrefs.edit()
                            .putStringSet(CloudSyncManager.KEY_APPS_WHITELIST_PACKAGES, new LinkedHashSet<>())
                            .apply();
                CloudSyncManager.getInstance(requireContext()).syncSettingsNowIfSignedIn();
                    renderSettingsState();
                    
                })
                .setPositiveButton(R.string.settings_apps_whitelist_save, (dialog, which) -> {
                    settingsPrefs.edit()
                            .putStringSet(CloudSyncManager.KEY_APPS_WHITELIST_PACKAGES, new LinkedHashSet<>(selectedPackages))
                            .apply();
                CloudSyncManager.getInstance(requireContext()).syncSettingsNowIfSignedIn();
                    renderSettingsState();
                    
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    @NonNull
    private List<WhitelistAppEntry> getFreshWhitelistCandidatesFromCache() {
        long now = System.currentTimeMillis();
        synchronized (whitelistCacheLock) {
            if (cachedWhitelistCandidates.isEmpty()
                    || now - cachedWhitelistBuiltAtMs > WHITELIST_CACHE_TTL_MS) {
                return Collections.emptyList();
            }
            return new ArrayList<>(cachedWhitelistCandidates);
        }
    }

    @NonNull
    private List<WhitelistAppEntry> getCachedOrLoadWhitelistCandidates(@NonNull Context context) {
        long now = System.currentTimeMillis();
        synchronized (whitelistCacheLock) {
            if (!cachedWhitelistCandidates.isEmpty()
                    && now - cachedWhitelistBuiltAtMs <= WHITELIST_CACHE_TTL_MS) {
                return new ArrayList<>(cachedWhitelistCandidates);
            }
        }

        List<WhitelistAppEntry> loaded = loadWhitelistCandidates(context);
        synchronized (whitelistCacheLock) {
            cachedWhitelistCandidates = new ArrayList<>(loaded);
            cachedWhitelistBuiltAtMs = now;
        }
        return new ArrayList<>(loaded);
    }

    private void applyWhitelistCardSelectionState(
            @NonNull MaterialCardView card,
            @NonNull TextView selectedBadge,
            boolean selected
    ) {
        if (selected) {
            card.setCardBackgroundColor(Color.parseColor("#1A0047AB"));
            card.setStrokeWidth(dp(1));
            card.setStrokeColor(Color.parseColor("#5C0047AB"));
            selectedBadge.setVisibility(View.VISIBLE);
        } else {
            card.setCardBackgroundColor(ContextCompat.getColor(requireContext(), R.color.surface_low));
            card.setStrokeWidth(dp(1));
            card.setStrokeColor(Color.parseColor("#140047AB"));
            selectedBadge.setVisibility(View.GONE);
        }
    }

    @NonNull
    private List<WhitelistAppEntry> loadWhitelistCandidates(@NonNull Context context) {
        PackageManager packageManager = context.getPackageManager();
        String ownPackage = context.getPackageName();

        Intent launcherIntent = new Intent(Intent.ACTION_MAIN);
        launcherIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> resolveInfos = packageManager.queryIntentActivities(launcherIntent, 0);

        Map<String, WhitelistAppEntry> byPackage = new HashMap<>();
        for (ResolveInfo resolveInfo : resolveInfos) {
            if (resolveInfo == null || resolveInfo.activityInfo == null) {
                continue;
            }

            String packageName = resolveInfo.activityInfo.packageName;
            if (TextUtils.isEmpty(packageName) || ownPackage.equals(packageName)) {
                continue;
            }

            CharSequence labelValue = resolveInfo.loadLabel(packageManager);
            String label = TextUtils.isEmpty(labelValue) ? packageName : labelValue.toString();
            Drawable icon = resolveInfo.loadIcon(packageManager);

            WhitelistAppEntry current = byPackage.get(packageName);
            if (current == null || label.compareToIgnoreCase(current.label) < 0) {
                byPackage.put(packageName, new WhitelistAppEntry(label, packageName, icon));
            }
        }

        List<WhitelistAppEntry> entries = new ArrayList<>(byPackage.values());
        Collections.sort(entries, (left, right) -> {
            int byLabel = left.label.compareToIgnoreCase(right.label);
            if (byLabel != 0) {
                return byLabel;
            }
            return left.packageName.compareToIgnoreCase(right.packageName);
        });
        return entries;
    }

    private final class WhitelistAppsAdapter extends RecyclerView.Adapter<WhitelistAppsAdapter.WhitelistViewHolder> {
        @NonNull
        private final List<WhitelistAppEntry> entries;
        @NonNull
        private final Set<String> selectedPackages;

        WhitelistAppsAdapter(@NonNull List<WhitelistAppEntry> entries, @NonNull Set<String> selectedPackages) {
            this.entries = entries;
            this.selectedPackages = selectedPackages;
            setHasStableIds(true);
        }

        @Override
        public long getItemId(int position) {
            return entries.get(position).packageName.hashCode();
        }

        @NonNull
        @Override
        public WhitelistViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View itemView = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_whitelist_app, parent, false);
            return new WhitelistViewHolder(itemView);
        }

        @Override
        public void onBindViewHolder(@NonNull WhitelistViewHolder holder, int position) {
            WhitelistAppEntry entry = entries.get(position);
            holder.labelView.setText(entry.label);
            holder.packageView.setText(entry.packageName);

            if (entry.icon != null) {
                holder.iconView.setImageDrawable(entry.icon);
            } else {
                holder.iconView.setImageResource(R.mipmap.ic_launcher);
            }

            boolean selected = selectedPackages.contains(entry.packageName);
            applyWhitelistCardSelectionState(holder.card, holder.selectedBadge, selected);

            holder.card.setOnClickListener(v -> {
                int adapterPosition = holder.getAdapterPosition();
                if (adapterPosition == RecyclerView.NO_POSITION) {
                    return;
                }

                WhitelistAppEntry selectedEntry = entries.get(adapterPosition);
                if (selectedPackages.contains(selectedEntry.packageName)) {
                    selectedPackages.remove(selectedEntry.packageName);
                } else {
                    selectedPackages.add(selectedEntry.packageName);
                }
                notifyItemChanged(adapterPosition);
            });
        }

        @Override
        public int getItemCount() {
            return entries.size();
        }

        final class WhitelistViewHolder extends RecyclerView.ViewHolder {
            final MaterialCardView card;
            final ImageView iconView;
            final TextView labelView;
            final TextView packageView;
            final TextView selectedBadge;

            WhitelistViewHolder(@NonNull View itemView) {
                super(itemView);
                card = itemView.findViewById(R.id.cardWhitelistAppRow);
                iconView = itemView.findViewById(R.id.ivWhitelistAppIcon);
                labelView = itemView.findViewById(R.id.tvWhitelistAppLabel);
                packageView = itemView.findViewById(R.id.tvWhitelistAppPackage);
                selectedBadge = itemView.findViewById(R.id.tvWhitelistAppSelected);
            }
        }
    }

    @NonNull
    private Set<String> getWhitelistedPackages() {
        Set<String> values = settingsPrefs.getStringSet(
                CloudSyncManager.KEY_APPS_WHITELIST_PACKAGES,
                Collections.emptySet()
        );
        if (values == null || values.isEmpty()) {
            return Collections.emptySet();
        }
        return new LinkedHashSet<>(values);
    }

    private void showSummaryFrequencyPicker() {
        final int[] values = new int[]{1, 2, 3, 4, 5};
        final String[] labels = new String[]{"1 vez", "2 veces", "3 veces", "4 veces", "5 veces"};
        int current = settingsPrefs.getInt(CloudSyncManager.KEY_DAILY_SUMMARY_INTERVAL_HOURS, 2);
        int checked = indexOf(values, current);

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Frecuencia resumen IA")
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    settingsPrefs.edit().putInt(CloudSyncManager.KEY_DAILY_SUMMARY_INTERVAL_HOURS, values[which]).apply();

                    boolean smartSuggestionsEnabled = settingsPrefs.getBoolean(
                            CloudSyncManager.KEY_SMART_SUGGESTIONS_ENABLED,
                            settingsPrefs.getBoolean(CloudSyncManager.KEY_AI_SHIFT_ENABLED, true)
                    );
                    Context appContext = requireContext().getApplicationContext();
                    if (smartSuggestionsEnabled) {
                        DailyAgendaNotificationWorker.schedule(appContext, values[which]);
                    }

                    renderSettingsState();
                    dialog.dismiss();
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private int indexOf(@NonNull int[] values, int target) {
        for (int i = 0; i < values.length; i++) {
            if (values[i] == target) {
                return i;
            }
        }
        return 0;
    }

    private int indexOf(@NonNull String[] values, @NonNull String target) {
        for (int i = 0; i < values.length; i++) {
            if (TextUtils.equals(values[i], target)) {
                return i;
            }
        }
        return 1;
    }

    @NonNull
    private String normalizeDownloadQuality(@Nullable String rawValue) {
        if (CloudSyncManager.DOWNLOAD_QUALITY_LOW.equals(rawValue)) {
            return CloudSyncManager.DOWNLOAD_QUALITY_LOW;
        }
        if (CloudSyncManager.DOWNLOAD_QUALITY_HIGH.equals(rawValue)) {
            return CloudSyncManager.DOWNLOAD_QUALITY_HIGH;
        }
        if (CloudSyncManager.DOWNLOAD_QUALITY_VERY_HIGH.equals(rawValue)) {
            return CloudSyncManager.DOWNLOAD_QUALITY_VERY_HIGH;
        }
        return CloudSyncManager.DOWNLOAD_QUALITY_MEDIUM;
    }

    @NonNull
    private String labelForDownloadQuality(@NonNull String quality) {
        if (CloudSyncManager.DOWNLOAD_QUALITY_LOW.equals(quality)) {
            return getString(R.string.settings_stream_download_quality_low);
        }
        if (CloudSyncManager.DOWNLOAD_QUALITY_HIGH.equals(quality)) {
            return getString(R.string.settings_stream_download_quality_high);
        }
        if (CloudSyncManager.DOWNLOAD_QUALITY_VERY_HIGH.equals(quality)) {
            return getString(R.string.settings_stream_download_quality_very_high);
        }
        return getString(R.string.settings_stream_download_quality_medium);
    }

    private void renderProfileState() {
        boolean signedIn = authManager.isSignedIn();
        String displayName = signedIn ? authManager.getDisplayName() : null;
        String email = signedIn ? authManager.getEmail() : null;
        String photoUrl = signedIn && authManager.getPhotoUrl() != null
                ? authManager.getPhotoUrl().toString()
                : null;

        if (hasProfileSnapshot
                && lastProfileSignedIn == signedIn
                && TextUtils.equals(lastProfileDisplayName, displayName)
                && TextUtils.equals(lastProfileEmail, email)
                && TextUtils.equals(lastProfilePhotoUrl, photoUrl)) {
            return;
        }

        hasProfileSnapshot = true;
        lastProfileSignedIn = signedIn;
        lastProfileDisplayName = displayName;
        lastProfileEmail = email;
        lastProfilePhotoUrl = photoUrl;

        if (!signedIn) {
            tvProfileName.setText("Sleppify");
            tvProfileBadge.setText("Inicia sesion para sincronizar");
            ivProfilePhoto.setImageDrawable(new ColorDrawable(ContextCompat.getColor(requireContext(), R.color.surface_high)));
            return;
        }

        tvProfileName.setText(TextUtils.isEmpty(displayName) ? "Usuario" : displayName);
        tvProfileBadge.setText(TextUtils.isEmpty(email) ? "Cuenta conectada · toca para cerrar sesion" : email);

        if (!TextUtils.isEmpty(photoUrl)) {
            Glide.with(this)
                    .load(photoUrl)
                    .circleCrop()
                    .placeholder(new ColorDrawable(ContextCompat.getColor(requireContext(), R.color.surface_high)))
                    .error(new ColorDrawable(ContextCompat.getColor(requireContext(), R.color.surface_high)))
                    .into(ivProfilePhoto);
        } else {
            ivProfilePhoto.setImageDrawable(new ColorDrawable(ContextCompat.getColor(requireContext(), R.color.surface_high)));
        }
    }

    private void showAccountActionsDialog() {
        String displayName = authManager.getDisplayName();
        String email = authManager.getEmail();

        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(TextUtils.isEmpty(displayName) ? "Cuenta conectada" : displayName)
                .setMessage(TextUtils.isEmpty(email) ? "¿Quieres cerrar sesion?" : email)
                .setNeutralButton("Eliminar cuenta", null)
                .setNegativeButton("Cancelar", null)
            .setPositiveButton("Cerrar sesion", (d, which) -> performSignOut())
                .show();

        Button deleteButton = dialog.getButton(AlertDialog.BUTTON_NEUTRAL);
        if (deleteButton != null) {
            deleteButton.setTextColor(Color.parseColor("#FF6E6E"));
            deleteButton.setOnClickListener(v -> {
                dialog.dismiss();
                showDeleteAccountDataConfirmation();
            });
        }
    }

    private void performSignOut() {
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).pauseActiveMediaAndDownloadsForSessionChange();
        }

        authManager.signOut((success, message) -> {
            CloudSyncManager.getInstance(requireContext()).onUserSignedOut();
            renderProfileState();

            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).refreshSessionUi();
            }

            
        });
    }

    private void showDeleteAccountDataConfirmation() {
        if (!authManager.isSignedIn() || authManager.getCurrentUser() == null) {
            
            return;
        }

        final EditText confirmInput = new EditText(requireContext());
        confirmInput.setHint("Escribe \"eliminar\"");
        confirmInput.setSingleLine(true);
        confirmInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        confirmInput.setLayoutParams(new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        LinearLayout confirmContainer = new LinearLayout(requireContext());
        confirmContainer.setOrientation(LinearLayout.VERTICAL);
        confirmContainer.setPadding(dp(24), dp(6), dp(24), 0);
        confirmContainer.addView(confirmInput);

        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
            .setTitle("Eliminar cuenta y borrar todo")
            .setMessage("Esta accion eliminara de forma permanente tu cuenta, tus datos en la nube y todos los datos locales de la app en este dispositivo.\n\nPara confirmar, escribe \"eliminar\".")
            .setView(confirmContainer)
            .setPositiveButton("Eliminar cuenta y todo", null)
                .setNegativeButton("Cancelar", null)
                .show();

        Button positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        positiveButton.setEnabled(false);

        TextWatcher watcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                // No-op.
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // No-op.
            }

            @Override
            public void afterTextChanged(Editable s) {
                boolean matches = DELETE_CONFIRM_WORD.equals(normalizeDeleteConfirmWord(s == null ? "" : s.toString()));
                positiveButton.setEnabled(matches);
                if (matches || s == null || s.length() == 0) {
                    confirmInput.setError(null);
                } else {
                    confirmInput.setError("Debes escribir eliminar");
                }
            }
        };
        confirmInput.addTextChangedListener(watcher);

        positiveButton.setOnClickListener(v -> {
            String typed = confirmInput.getText() == null ? "" : confirmInput.getText().toString();
            if (!DELETE_CONFIRM_WORD.equals(normalizeDeleteConfirmWord(typed))) {
                confirmInput.setError("Debes escribir eliminar");
                return;
            }

            dialog.dismiss();
            performDeleteAccountAndData();
        });
    }

    @NonNull
    private String normalizeDeleteConfirmWord(@Nullable String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private void performDeleteAccountAndData() {
        if (deleteAccountInFlight || !isAdded()) {
            return;
        }

        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).pauseActiveMediaAndDownloadsForSessionChange();
        }

        FirebaseUser currentUser = authManager.getCurrentUser();
        if (currentUser == null || TextUtils.isEmpty(currentUser.getUid())) {
            
            return;
        }

        deleteAccountInFlight = true;

        Context appContext = requireContext().getApplicationContext();
        CloudSyncManager cloudSync = CloudSyncManager.getInstance(appContext);
        String uid = currentUser.getUid();

        cloudSync.deleteUserDataFromCloud(uid, (cloudOk, cloudMessage) -> {
            if (!isAdded()) {
                deleteAccountInFlight = false;
                return;
            }

            if (!cloudOk) {
                deleteAccountInFlight = false;
                String safeMessage = TextUtils.isEmpty(cloudMessage)
                        ? "No se pudieron eliminar tus datos en la nube."
                        : cloudMessage;
                
                return;
            }

            authManager.deleteCurrentUser(requireActivity(), (authOk, authMessage) -> {
                if (!isAdded()) {
                    deleteAccountInFlight = false;
                    return;
                }

                deleteAccountInFlight = false;

                if (!authOk) {
                    String safeMessage = TextUtils.isEmpty(authMessage)
                            ? "No se pudo eliminar la cuenta. Intenta iniciar sesion de nuevo y repetir."
                            : authMessage;
                    
                    return;
                }

                cloudSync.onUserSignedOut();
                cloudSync.clearLocalUserDataCompletely();

                hasProfileSnapshot = false;
                hasSettingsSnapshot = false;
                renderProfileState();
                renderSettingsState();

                if (getActivity() instanceof MainActivity) {
                    ((MainActivity) getActivity()).refreshSessionUi();
                }

                
            });
        });
    }

    @NonNull
    private String formatSummaryFrequency(int timesPerDay) {
        int safeTimes = Math.max(1, Math.min(5, timesPerDay));
        return safeTimes == 1 ? "1 vez" : safeTimes + " veces";
    }

    @NonNull
    private String formatCrossfadeSeconds(int seconds) {
        int safeSeconds = Math.max(0, Math.min(12, seconds));
        return String.valueOf(safeSeconds);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static final class StorageBreakdown {
        final long totalBytes;
        final long freeBytes;
        final long downloadsBytes;
        final long cacheBytes;
        final long otherAppsBytes;
        final long appPersistentBytes;

        StorageBreakdown(
                long totalBytes,
                long freeBytes,
                long downloadsBytes,
                long cacheBytes,
                long otherAppsBytes,
                long appPersistentBytes
        ) {
            this.totalBytes = totalBytes;
            this.freeBytes = freeBytes;
            this.downloadsBytes = downloadsBytes;
            this.cacheBytes = cacheBytes;
            this.otherAppsBytes = otherAppsBytes;
            this.appPersistentBytes = appPersistentBytes;
        }
    }

    private static final class WhitelistAppEntry {
        final String label;
        final String packageName;
        @Nullable
        final Drawable icon;

        WhitelistAppEntry(@NonNull String label, @NonNull String packageName, @Nullable Drawable icon) {
            this.label = label;
            this.packageName = packageName;
            this.icon = icon;
        }
    }
}

