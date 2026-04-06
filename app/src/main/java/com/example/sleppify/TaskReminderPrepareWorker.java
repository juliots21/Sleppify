package com.example.sleppify;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.work.Data;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class TaskReminderPrepareWorker extends Worker {

    public TaskReminderPrepareWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context context = getApplicationContext();
        SharedPreferences settingsPrefs = context.getSharedPreferences(CloudSyncManager.PREFS_SETTINGS, Context.MODE_PRIVATE);
        boolean smartSuggestionsEnabled = settingsPrefs.getBoolean(
                CloudSyncManager.KEY_SMART_SUGGESTIONS_ENABLED,
                settingsPrefs.getBoolean(CloudSyncManager.KEY_AI_SHIFT_ENABLED, true)
        );
        if (!smartSuggestionsEnabled) {
            return Result.success();
        }

        Data input = getInputData();
        String reminderId = input.getString(TaskReminderConstants.INPUT_REMINDER_ID);
        String taskTitle = input.getString(TaskReminderConstants.INPUT_TASK_TITLE);
        String taskDesc = input.getString(TaskReminderConstants.INPUT_TASK_DESC);
        String taskTime = input.getString(TaskReminderConstants.INPUT_TASK_TIME);
        String dateKey = input.getString(TaskReminderConstants.INPUT_DATE_KEY);
        long notifyAtMs = input.getLong(TaskReminderConstants.INPUT_NOTIFY_AT_MS, 0L);

        if (TextUtils.isEmpty(reminderId) || TextUtils.isEmpty(taskTitle)) {
            return Result.success();
        }

        String profileName = resolveProfileName(context);
        String snapshot = buildTaskSnapshot(taskTitle, taskDesc, taskTime, dateKey);

        AtomicReference<String> messageRef = new AtomicReference<>(null);
        CountDownLatch latch = new CountDownLatch(1);

        new GeminiIntelligenceService().generateTodayAgendaSummary(
                profileName,
                snapshot,
                new GeminiIntelligenceService.TodayAgendaSummaryCallback() {
                    @Override
                    public void onSuccess(String message) {
                        messageRef.set(message);
                        latch.countDown();
                    }

                    @Override
                    public void onError(String error) {
                        latch.countDown();
                    }
                }
        );

        boolean completed;
        try {
            completed = latch.await(20, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Result.retry();
        }

        String summary = messageRef.get();
        if (!completed || TextUtils.isEmpty(summary)) {
            long nowMs = System.currentTimeMillis();
            if (notifyAtMs > nowMs + 15_000L) {
                return Result.retry();
            }
            summary = buildFallbackSummary(taskTitle, taskDesc, taskTime);
        }

        SharedPreferences cachePrefs = context.getSharedPreferences(TaskReminderConstants.PREFS_CACHE, Context.MODE_PRIVATE);
        cachePrefs.edit()
                .putString(TaskReminderConstants.KEY_SUMMARY_PREFIX + reminderId, summary)
                .putLong(TaskReminderConstants.KEY_SUMMARY_TS_PREFIX + reminderId, System.currentTimeMillis())
                .apply();

        return Result.success();
    }

    @NonNull
    private String resolveProfileName(@NonNull Context context) {
        try {
            AuthManager authManager = AuthManager.getInstance(context);
            if (authManager != null && authManager.isSignedIn()) {
                String displayName = authManager.getDisplayName();
                if (!TextUtils.isEmpty(displayName)) {
                    return displayName;
                }
            }
        } catch (Throwable ignored) {
            // Sin sesion valida en background usamos valor neutro.
        }
        return "usuario";
    }

    @NonNull
    private String buildTaskSnapshot(
            @NonNull String taskTitle,
            @Nullable String taskDesc,
            @Nullable String taskTime,
            @Nullable String dateKey
    ) {
        String safeDesc = taskDesc == null ? "" : taskDesc.trim();
        String safeTime = taskTime == null ? "" : taskTime.trim();
        String safeDate = dateKey == null ? "" : dateKey.trim();

        StringBuilder builder = new StringBuilder();
        builder.append("total_tareas_hoy=1; ");
        builder.append("lista=[1) ").append(taskTitle);
        if (!safeDesc.isEmpty()) {
            builder.append(" - ").append(safeDesc);
        }
        if (!safeTime.isEmpty()) {
            builder.append(" - hora ").append(safeTime);
        }
        if (!safeDate.isEmpty()) {
            builder.append(" - fecha ").append(safeDate);
        }
        builder.append("]");
        return builder.toString();
    }

    @NonNull
    private String buildFallbackSummary(
            @NonNull String taskTitle,
            @Nullable String taskDesc,
            @Nullable String taskTime
    ) {
        String safeDesc = taskDesc == null ? "" : taskDesc.trim();
        String safeTime = taskTime == null ? "" : taskTime.trim();

        if (!safeDesc.isEmpty() && !safeTime.isEmpty()) {
            return "Recordatorio breve: " + taskTitle + " a las " + safeTime + ". " + safeDesc;
        }
        if (!safeTime.isEmpty()) {
            return "Recordatorio breve: " + taskTitle + " a las " + safeTime + ".";
        }
        if (!safeDesc.isEmpty()) {
            return "Recordatorio breve: " + taskTitle + ". " + safeDesc;
        }
        return "Recordatorio breve: toca avanzar en \"" + taskTitle + "\".";
    }
}
