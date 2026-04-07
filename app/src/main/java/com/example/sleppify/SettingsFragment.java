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
import android.os.Handler;
import android.os.Looper;
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
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.imageview.ShapeableImageView;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.auth.FirebaseUser;

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
    private MaterialCardView cardSpatialAudio;
    private TextView tvSpatialAudioBadge;
    private MaterialCardView cardReverbControl;
    private TextView tvReverbStatus;
    private MaterialSwitch swAiShiftPermissions;
    private MaterialSwitch swAppsTurboMode;
    private View rowSummaryFrequency;
    private View rowAppsWhitelist;
    private TextView tvAppsWhitelistValue;

    private SharedPreferences settingsPrefs;
    private SharedPreferences audioPrefs;
    private AuthManager authManager;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService whitelistExecutor = Executors.newSingleThreadExecutor();
    private final Object whitelistCacheLock = new Object();
    @NonNull
    private List<WhitelistAppEntry> cachedWhitelistCandidates = Collections.emptyList();
    private long cachedWhitelistBuiltAtMs;
    private boolean whitelistDialogLoading;
    private boolean deleteAccountInFlight;
    private boolean settingsUiBound;
    private boolean hasSettingsSnapshot;
    private boolean lastSmartSuggestionsEnabled;
    private boolean lastAppsTurboEnabled;
    private int lastWhitelistCount = -1;
    private int lastSummaryFrequencyTimes = -1;
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
        audioPrefs = requireContext().getSharedPreferences(AudioEffectsService.PREFS_NAME, Context.MODE_PRIVATE);
        authManager = AuthManager.getInstance(requireContext());

        cardProfile = view.findViewById(R.id.cardProfile);
        ivProfilePhoto = view.findViewById(R.id.ivProfilePhoto);
        tvProfileName = view.findViewById(R.id.tvProfileName);
        tvProfileBadge = view.findViewById(R.id.tvProfileBadge);
        tvSummaryFrequencyValue = view.findViewById(R.id.tvSummaryFrequencyValue);
        cardSpatialAudio = view.findViewById(R.id.cardSpatialAudio);
        tvSpatialAudioBadge = view.findViewById(R.id.tvSpatialAudioBadge);
        cardReverbControl = view.findViewById(R.id.cardReverbControl);
        tvReverbStatus = view.findViewById(R.id.tvReverbStatus);
        swAiShiftPermissions = view.findViewById(R.id.swAiShiftPermissions);
        swAppsTurboMode = view.findViewById(R.id.swAppsTurboMode);
        rowSummaryFrequency = view.findViewById(R.id.rowSummaryFrequency);
        rowAppsWhitelist = view.findViewById(R.id.rowAppsWhitelist);
        tvAppsWhitelistValue = view.findViewById(R.id.tvAppsWhitelistValue);

        renderSettingsState();
        renderAudioCardsState();
        renderProfileState();
        setupCalendarSettingsInteractions();
        setupAppsSettingsInteractions();
        prewarmWhitelistCandidatesAsync();

        if (cardSpatialAudio != null) {
            cardSpatialAudio.setOnClickListener(v -> toggleSpatialCard());
        }
        if (cardReverbControl != null) {
            cardReverbControl.setOnClickListener(v -> toggleReverbCard());
        }

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
        renderAudioCardsState();
        renderProfileState();
    }

    @Override
    public void onDestroyView() {
        settingsUiBound = false;
        super.onDestroyView();
    }

    @Override
    public void onDestroy() {
        whitelistExecutor.shutdownNow();
        super.onDestroy();
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
        boolean appsTurboEnabled = settingsPrefs.getBoolean(CloudSyncManager.KEY_APPS_TURBO_MODE, false);
        int whitelistCount = getWhitelistedPackages().size();
        int summaryFrequencyTimes = settingsPrefs.getInt(CloudSyncManager.KEY_DAILY_SUMMARY_INTERVAL_HOURS, 2);

        if (settingsUiBound
            && hasSettingsSnapshot
                && lastSmartSuggestionsEnabled == smartSuggestionsEnabled
                && lastAppsTurboEnabled == appsTurboEnabled
                && lastWhitelistCount == whitelistCount
                && lastSummaryFrequencyTimes == summaryFrequencyTimes) {
            return;
        }

        hasSettingsSnapshot = true;
        settingsUiBound = true;
        lastSmartSuggestionsEnabled = smartSuggestionsEnabled;
        lastAppsTurboEnabled = appsTurboEnabled;
        lastWhitelistCount = whitelistCount;
        lastSummaryFrequencyTimes = summaryFrequencyTimes;

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

        if (swAppsTurboMode != null) {
            swAppsTurboMode.setOnCheckedChangeListener(null);
            swAppsTurboMode.setChecked(appsTurboEnabled);
            swAppsTurboMode.setOnCheckedChangeListener((buttonView, isChecked) ->
                    settingsPrefs.edit().putBoolean(CloudSyncManager.KEY_APPS_TURBO_MODE, isChecked).apply());
        }

        if (tvAppsWhitelistValue != null) {
            if (whitelistCount <= 0) {
                tvAppsWhitelistValue.setText(R.string.settings_apps_whitelist_none);
            } else {
                tvAppsWhitelistValue.setText(getString(R.string.settings_apps_whitelist_count, whitelistCount));
            }
        }

        tvSummaryFrequencyValue.setText(formatSummaryFrequency(summaryFrequencyTimes));
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

    private void renderAudioCardsState() {
        boolean spatialEnabled = audioPrefs.getBoolean(AudioEffectsService.KEY_SPATIAL_ENABLED, false);
        int reverbLevel = normalizeReverbLevel(
            audioPrefs.getInt(AudioEffectsService.KEY_REVERB_LEVEL, AudioEffectsService.REVERB_LEVEL_OFF)
        );
        boolean reverbEnabled = reverbLevel != AudioEffectsService.REVERB_LEVEL_OFF;

        applyAudioCardVisualState(cardSpatialAudio, tvSpatialAudioBadge, spatialEnabled);
        applyReverbCardVisualState(reverbEnabled);
    }

    private void applyReverbCardVisualState(boolean enabled) {
        // Reusa exactamente el mismo tratamiento visual de las tarjetas de EQ y Spatial.
        applyAudioCardVisualState(cardReverbControl, tvReverbStatus, enabled);

        if (tvReverbStatus != null) {
            tvReverbStatus.setText(enabled
                    ? R.string.settings_audio_reverb_enabled
                    : R.string.settings_audio_reverb_disabled);
        }
    }

    private int normalizeReverbLevel(int rawLevel) {
        if (rawLevel == AudioEffectsService.REVERB_LEVEL_OFF) {
            return AudioEffectsService.REVERB_LEVEL_OFF;
        }
        if (rawLevel == AudioEffectsService.REVERB_LEVEL_LIGHT) {
            return AudioEffectsService.REVERB_LEVEL_LIGHT;
        }
        if (rawLevel == AudioEffectsService.REVERB_LEVEL_MEDIUM) {
            return AudioEffectsService.REVERB_LEVEL_MEDIUM;
        }
        if (rawLevel == AudioEffectsService.REVERB_LEVEL_STRONG) {
            return AudioEffectsService.REVERB_LEVEL_STRONG;
        }
        return AudioEffectsService.REVERB_LEVEL_OFF;
    }

    private void applyAudioCardVisualState(
            @Nullable MaterialCardView card,
            @Nullable TextView badge,
            boolean enabled
    ) {
        if (card != null) {
            if (enabled) {
                card.setCardBackgroundColor(Color.parseColor("#1AAEC7FD"));
                card.setStrokeWidth(dp(1));
                card.setStrokeColor(Color.parseColor("#33AEC7FD"));
            } else {
                card.setCardBackgroundColor(ContextCompat.getColor(requireContext(), R.color.surface_low));
                card.setStrokeWidth(0);
                card.setStrokeColor(Color.TRANSPARENT);
            }
        }

        if (badge != null) {
            badge.setText(enabled ? "ON" : "OFF");
            badge.setBackgroundResource(enabled ? R.drawable.bg_status_badge_on : R.drawable.bg_status_badge_off);
            badge.setTextColor(enabled
                    ? ContextCompat.getColor(requireContext(), R.color.stitch_blue_light)
                    : Color.parseColor("#FFBA9A"));
        }
    }

    private void toggleSpatialCard() {
        boolean current = audioPrefs.getBoolean(AudioEffectsService.KEY_SPATIAL_ENABLED, false);
        boolean next = !current;

        SharedPreferences.Editor editor = audioPrefs.edit();
        editor.putBoolean(AudioEffectsService.KEY_SPATIAL_ENABLED, next);
        int currentStrength = audioPrefs.getInt(
            AudioEffectsService.KEY_SPATIAL_STRENGTH,
            AudioEffectsService.SPATIAL_STRENGTH_DEFAULT
        );
        if (!audioPrefs.contains(AudioEffectsService.KEY_SPATIAL_STRENGTH)
            || (next && currentStrength <= 0)) {
            editor.putInt(AudioEffectsService.KEY_SPATIAL_STRENGTH, AudioEffectsService.SPATIAL_STRENGTH_DEFAULT);
        }
        if (!audioPrefs.contains(AudioEffectsService.KEY_REVERB_LEVEL)) {
            editor.putInt(AudioEffectsService.KEY_REVERB_LEVEL, AudioEffectsService.REVERB_LEVEL_OFF);
        }
        editor.apply();

        updateAudioEffectsServiceState();
        renderAudioCardsState();
    }

    private void toggleReverbCard() {
        int currentLevel = normalizeReverbLevel(
                audioPrefs.getInt(AudioEffectsService.KEY_REVERB_LEVEL, AudioEffectsService.REVERB_LEVEL_OFF)
        );

        int nextLevel;
        if (currentLevel == AudioEffectsService.REVERB_LEVEL_OFF) {
            nextLevel = AudioEffectsService.REVERB_LEVEL_STRONG;
        } else {
            nextLevel = AudioEffectsService.REVERB_LEVEL_OFF;
        }

        audioPrefs.edit().putInt(AudioEffectsService.KEY_REVERB_LEVEL, nextLevel).apply();
        updateAudioEffectsServiceState();
        renderAudioCardsState();
    }

    private void updateAudioEffectsServiceState() {
        boolean eqEnabled = audioPrefs.getBoolean(AudioEffectsService.KEY_ENABLED, false);
        boolean spatialEnabled = audioPrefs.getBoolean(AudioEffectsService.KEY_SPATIAL_ENABLED, false);
        boolean reverbEnabled = normalizeReverbLevel(
                audioPrefs.getInt(AudioEffectsService.KEY_REVERB_LEVEL, AudioEffectsService.REVERB_LEVEL_OFF)
        ) != AudioEffectsService.REVERB_LEVEL_OFF;
        Context appContext = requireContext().getApplicationContext();

        Intent intent = new Intent(appContext, AudioEffectsService.class);
        if (eqEnabled || spatialEnabled || reverbEnabled) {
            intent.setAction(AudioEffectsService.ACTION_APPLY);
            try {
                ContextCompat.startForegroundService(appContext, intent);
            } catch (Throwable ignored) {
                // Fallback para OEMs que restringen foreground service en momentos puntuales.
                appContext.startService(intent);
            }
            return;
        }

        intent.setAction(AudioEffectsService.ACTION_STOP);
        appContext.startService(intent);
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

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
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

