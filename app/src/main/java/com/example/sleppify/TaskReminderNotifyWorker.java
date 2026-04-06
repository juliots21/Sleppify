package com.example.sleppify;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.work.Data;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

public class TaskReminderNotifyWorker extends Worker {

    public TaskReminderNotifyWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            int granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS);
            if (granted != PackageManager.PERMISSION_GRANTED) {
                return Result.success();
            }
        }

        Data input = getInputData();
        String reminderId = input.getString(TaskReminderConstants.INPUT_REMINDER_ID);
        String taskTitle = input.getString(TaskReminderConstants.INPUT_TASK_TITLE);
        String taskDesc = input.getString(TaskReminderConstants.INPUT_TASK_DESC);
        String taskTime = input.getString(TaskReminderConstants.INPUT_TASK_TIME);

        if (TextUtils.isEmpty(reminderId) || TextUtils.isEmpty(taskTitle)) {
            return Result.success();
        }

        SharedPreferences cachePrefs = context.getSharedPreferences(TaskReminderConstants.PREFS_CACHE, Context.MODE_PRIVATE);
        long deliveredAt = cachePrefs.getLong(TaskReminderConstants.KEY_DELIVERED_TS_PREFIX + reminderId, 0L);
        if (deliveredAt > 0L) {
            return Result.success();
        }

        String summary = cachePrefs.getString(TaskReminderConstants.KEY_SUMMARY_PREFIX + reminderId, "");
        if (TextUtils.isEmpty(summary)) {
            summary = buildFallbackSummary(taskTitle, taskDesc, taskTime);
        }

        createChannelIfNeeded(context);

        Intent openIntent = new Intent(context, MainActivity.class);
        openIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        int pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            pendingFlags |= PendingIntent.FLAG_IMMUTABLE;
        }

        PendingIntent contentIntent = PendingIntent.getActivity(
                context,
                Math.abs(reminderId.hashCode()),
                openIntent,
                pendingFlags
        );

        String title = "Recordatorio: " + taskTitle;
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, TaskReminderConstants.CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_calendar_today)
                .setContentTitle(title)
                .setContentText(summary)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(summary))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(contentIntent);

        if (!TextUtils.isEmpty(taskTime)) {
            builder.setSubText("Agenda • " + taskTime);
        }

        try {
            int notificationId = TaskReminderConstants.NOTIFICATION_ID_BASE + Math.abs(reminderId.hashCode() % 100000);
            NotificationManagerCompat.from(context).notify(notificationId, builder.build());
        } catch (SecurityException ignored) {
            return Result.success();
        }

        cachePrefs.edit()
                .putLong(TaskReminderConstants.KEY_DELIVERED_TS_PREFIX + reminderId, System.currentTimeMillis())
                .remove(TaskReminderConstants.KEY_SUMMARY_PREFIX + reminderId)
                .remove(TaskReminderConstants.KEY_SUMMARY_TS_PREFIX + reminderId)
                .apply();

        return Result.success();
    }

    private void createChannelIfNeeded(@NonNull Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }

        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null) {
            return;
        }

        NotificationChannel existing = manager.getNotificationChannel(TaskReminderConstants.CHANNEL_ID);
        if (existing != null) {
            return;
        }

        NotificationChannel channel = new NotificationChannel(
                TaskReminderConstants.CHANNEL_ID,
                TaskReminderConstants.CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
        );
        channel.setDescription("Recordatorios por tarea con resumen inteligente");
        manager.createNotificationChannel(channel);
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
            return "En 1 minuto: " + taskTitle + " (" + safeTime + "). " + safeDesc;
        }
        if (!safeTime.isEmpty()) {
            return "En 1 minuto: " + taskTitle + " (" + safeTime + ").";
        }
        if (!safeDesc.isEmpty()) {
            return "En 1 minuto: " + taskTitle + ". " + safeDesc;
        }
        return "En 1 minuto: " + taskTitle + ".";
    }
}
