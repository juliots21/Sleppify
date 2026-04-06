package com.example.sleppify;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public final class TaskReminderScheduler {

    private TaskReminderScheduler() {
    }

    public static void rescheduleAll(@NonNull Context context) {
        Context appContext = context.getApplicationContext();
        SharedPreferences settingsPrefs = appContext.getSharedPreferences(CloudSyncManager.PREFS_SETTINGS, Context.MODE_PRIVATE);
        boolean smartSuggestionsEnabled = settingsPrefs.getBoolean(
                CloudSyncManager.KEY_SMART_SUGGESTIONS_ENABLED,
                settingsPrefs.getBoolean(CloudSyncManager.KEY_AI_SHIFT_ENABLED, true)
        );

        if (!smartSuggestionsEnabled) {
            cancelAll(appContext);
            return;
        }

        int leadMinutes = settingsPrefs.getInt(
                CloudSyncManager.KEY_NOTIFICATION_LEAD_MINUTES,
                TaskReminderConstants.LEAD_MINUTES_ENFORCED
        );

        if (leadMinutes != TaskReminderConstants.LEAD_MINUTES_ENFORCED) {
            settingsPrefs.edit()
                    .putInt(CloudSyncManager.KEY_NOTIFICATION_LEAD_MINUTES, TaskReminderConstants.LEAD_MINUTES_ENFORCED)
                    .apply();
            leadMinutes = TaskReminderConstants.LEAD_MINUTES_ENFORCED;
        }

        String agendaJson = CloudSyncManager.getInstance(appContext).getLocalAgendaJson();
        long nowMs = System.currentTimeMillis();
        List<ReminderSpec> specs = buildReminderSpecs(agendaJson, nowMs, leadMinutes);

        WorkManager workManager = WorkManager.getInstance(appContext);
        SharedPreferences statePrefs = appContext.getSharedPreferences(TaskReminderConstants.PREFS_STATE, Context.MODE_PRIVATE);
        Set<String> previousIds = new HashSet<>(statePrefs.getStringSet(
                TaskReminderConstants.KEY_ACTIVE_REMINDER_IDS,
                Collections.emptySet()
        ));

        Set<String> nextIds = new HashSet<>();
        for (ReminderSpec spec : specs) {
            nextIds.add(spec.reminderId);
            enqueuePrepareWork(workManager, spec);
            enqueueNotifyWork(workManager, spec);
        }

        for (String previousId : previousIds) {
            if (!nextIds.contains(previousId)) {
                cancelReminderChain(workManager, previousId);
            }
        }

        statePrefs.edit()
                .putStringSet(TaskReminderConstants.KEY_ACTIVE_REMINDER_IDS, nextIds)
                .apply();
    }

    public static void cancelAll(@NonNull Context context) {
        Context appContext = context.getApplicationContext();
        WorkManager workManager = WorkManager.getInstance(appContext);
        SharedPreferences statePrefs = appContext.getSharedPreferences(TaskReminderConstants.PREFS_STATE, Context.MODE_PRIVATE);

        Set<String> previousIds = new HashSet<>(statePrefs.getStringSet(
                TaskReminderConstants.KEY_ACTIVE_REMINDER_IDS,
                Collections.emptySet()
        ));

        for (String previousId : previousIds) {
            cancelReminderChain(workManager, previousId);
        }

        workManager.cancelAllWorkByTag(TaskReminderConstants.TAG_PREPARE);
        workManager.cancelAllWorkByTag(TaskReminderConstants.TAG_NOTIFY);

        statePrefs.edit()
                .remove(TaskReminderConstants.KEY_ACTIVE_REMINDER_IDS)
                .apply();
    }

    @NonNull
    static List<ReminderSpec> buildReminderSpecs(
            @NonNull String agendaJson,
            long nowMs,
            int leadMinutes
    ) {
        List<ReminderSpec> specs = new ArrayList<>();
        if (TextUtils.isEmpty(agendaJson)) {
            return specs;
        }

        long leadMs = TimeUnit.MINUTES.toMillis(Math.max(1, leadMinutes));

        try {
            JSONObject root = new JSONObject(agendaJson);
            Iterator<String> dayIterator = root.keys();
            while (dayIterator.hasNext()) {
                String dateKey = dayIterator.next();
                JSONArray dayArray = root.optJSONArray(dateKey);
                if (dayArray == null || dayArray.length() == 0) {
                    continue;
                }

                for (int i = 0; i < dayArray.length(); i++) {
                    JSONObject taskObj = dayArray.optJSONObject(i);
                    if (taskObj == null) {
                        continue;
                    }

                    String title = taskObj.optString("title", "").trim();
                    String desc = taskObj.optString("desc", "").trim();
                    String time = taskObj.optString("time", "").trim();
                    if (TextUtils.isEmpty(title) || TextUtils.isEmpty(time)) {
                        continue;
                    }

                    long dueAtMs = TaskReminderTimeUtils.parseDueAtMillis(dateKey, time);
                    if (dueAtMs <= nowMs) {
                        continue;
                    }

                    long notifyAtMs = dueAtMs - leadMs;
                    if (notifyAtMs <= nowMs) {
                        notifyAtMs = nowMs + 1_000L;
                    }

                    long prepareAtMs = notifyAtMs - TaskReminderConstants.AI_PREFETCH_BEFORE_NOTIFY_MS;
                    long prepareDelayMs = TaskReminderTimeUtils.computeDelayMs(prepareAtMs, nowMs);
                    long notifyDelayMs = TaskReminderTimeUtils.computeDelayMs(notifyAtMs, nowMs);

                    String rawReminderId = dateKey + "|" + i + "|" + title + "|" + time + "|" + desc;
                    String reminderId = TaskReminderTimeUtils.buildStableReminderId(rawReminderId);

                    specs.add(new ReminderSpec(
                            reminderId,
                            dateKey,
                            title,
                            desc,
                            time,
                            dueAtMs,
                            notifyAtMs,
                            prepareDelayMs,
                            notifyDelayMs
                    ));
                }
            }
        } catch (Exception ignored) {
            return specs;
        }

        Collections.sort(specs, (left, right) -> Long.compare(left.notifyAtMs, right.notifyAtMs));
        return specs;
    }

    private static void enqueuePrepareWork(@NonNull WorkManager workManager, @NonNull ReminderSpec spec) {
        Data inputData = buildInputData(spec);
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();

        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(TaskReminderPrepareWorker.class)
                .setInputData(inputData)
                .setInitialDelay(spec.prepareDelayMs, TimeUnit.MILLISECONDS)
                .setConstraints(constraints)
                .addTag(TaskReminderConstants.TAG_PREPARE)
                .build();

        String uniqueName = TaskReminderConstants.UNIQUE_PREPARE_PREFIX + spec.reminderId;
        workManager.enqueueUniqueWork(uniqueName, ExistingWorkPolicy.REPLACE, request);
    }

    private static void enqueueNotifyWork(@NonNull WorkManager workManager, @NonNull ReminderSpec spec) {
        Data inputData = buildInputData(spec);

        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(TaskReminderNotifyWorker.class)
                .setInputData(inputData)
                .setInitialDelay(spec.notifyDelayMs, TimeUnit.MILLISECONDS)
                .addTag(TaskReminderConstants.TAG_NOTIFY)
                .build();

        String uniqueName = TaskReminderConstants.UNIQUE_NOTIFY_PREFIX + spec.reminderId;
        workManager.enqueueUniqueWork(uniqueName, ExistingWorkPolicy.REPLACE, request);
    }

    @NonNull
    private static Data buildInputData(@NonNull ReminderSpec spec) {
        return new Data.Builder()
                .putString(TaskReminderConstants.INPUT_REMINDER_ID, spec.reminderId)
                .putString(TaskReminderConstants.INPUT_TASK_TITLE, spec.title)
                .putString(TaskReminderConstants.INPUT_TASK_DESC, spec.desc)
                .putString(TaskReminderConstants.INPUT_TASK_TIME, spec.time)
                .putString(TaskReminderConstants.INPUT_DATE_KEY, spec.dateKey)
                .putLong(TaskReminderConstants.INPUT_DUE_AT_MS, spec.dueAtMs)
                .putLong(TaskReminderConstants.INPUT_NOTIFY_AT_MS, spec.notifyAtMs)
                .build();
    }

    private static void cancelReminderChain(@NonNull WorkManager workManager, @NonNull String reminderId) {
        workManager.cancelUniqueWork(TaskReminderConstants.UNIQUE_PREPARE_PREFIX + reminderId);
        workManager.cancelUniqueWork(TaskReminderConstants.UNIQUE_NOTIFY_PREFIX + reminderId);
    }

    static final class ReminderSpec {
        final String reminderId;
        final String dateKey;
        final String title;
        final String desc;
        final String time;
        final long dueAtMs;
        final long notifyAtMs;
        final long prepareDelayMs;
        final long notifyDelayMs;

        ReminderSpec(
                @NonNull String reminderId,
                @NonNull String dateKey,
                @NonNull String title,
                @NonNull String desc,
                @NonNull String time,
                long dueAtMs,
                long notifyAtMs,
                long prepareDelayMs,
                long notifyDelayMs
        ) {
            this.reminderId = reminderId;
            this.dateKey = dateKey;
            this.title = title;
            this.desc = desc;
            this.time = time;
            this.dueAtMs = dueAtMs;
            this.notifyAtMs = notifyAtMs;
            this.prepareDelayMs = prepareDelayMs;
            this.notifyDelayMs = notifyDelayMs;
        }
    }
}
