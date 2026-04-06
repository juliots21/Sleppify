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
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class DailyAgendaNotificationWorker extends Worker {

    public static final String UNIQUE_WORK_NAME = "daily_agenda_ai_notifications";
    private static final String CHANNEL_ID = "daily_agenda_ai_channel";
    private static final String CHANNEL_NAME = "Agenda IA";
    private static final int NOTIFICATION_BASE_ID = 4200;
    private static final long PERIOD_HOURS_DEFAULT = 2L;

    public DailyAgendaNotificationWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    public static void schedule(@NonNull Context context) {
        SharedPreferences settingsPrefs = context.getSharedPreferences(CloudSyncManager.PREFS_SETTINGS, Context.MODE_PRIVATE);
        int configuredHours = settingsPrefs.getInt(
                CloudSyncManager.KEY_DAILY_SUMMARY_INTERVAL_HOURS,
                (int) PERIOD_HOURS_DEFAULT
        );
        schedule(context, configuredHours);
    }

    public static void schedule(@NonNull Context context, int configuredHours) {
        long safeHours = Math.max(1L, Math.min(4L, configuredHours));

        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();

        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(
                DailyAgendaNotificationWorker.class,
                safeHours,
                TimeUnit.HOURS
        )
                .setConstraints(constraints)
                .build();

        WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(
                        UNIQUE_WORK_NAME,
                        ExistingPeriodicWorkPolicy.UPDATE,
                        request
                );
    }

    public static void cancel(@NonNull Context context) {
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME);
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

        List<TodayTask> todayTasks = readTodayTasks(context);
        if (todayTasks.isEmpty()) {
            return Result.success();
        }

        String profileName = resolveProfileName(context);
        String todaySnapshot = buildTodaySnapshot(todayTasks);
        AtomicReference<String> aiMessageRef = new AtomicReference<>(null);
        CountDownLatch latch = new CountDownLatch(1);

        new GeminiIntelligenceService().generateTodayAgendaSummary(
                profileName,
                todaySnapshot,
                new GeminiIntelligenceService.TodayAgendaSummaryCallback() {
                    @Override
                    public void onSuccess(String message) {
                        aiMessageRef.set(message);
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
            completed = latch.await(25, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Result.retry();
        }

        String aiMessage = aiMessageRef.get();
        if (!completed || TextUtils.isEmpty(aiMessage)) {
            return Result.retry();
        }

        notifyTodaySummary(context, aiMessage, todayTasks.size());
        return Result.success();
    }

    @NonNull
    private List<TodayTask> readTodayTasks(@NonNull Context context) {
        List<TodayTask> tasks = new ArrayList<>();
        String agendaJson = CloudSyncManager.getInstance(context).getLocalAgendaJson();

        try {
            JSONObject root = new JSONObject(agendaJson);
            String todayKey = new SimpleDateFormat("yyyy-MM-dd", Locale.US)
                    .format(Calendar.getInstance().getTime());
            JSONArray dayArray = root.optJSONArray(todayKey);
            if (dayArray == null) {
                return tasks;
            }

            for (int i = 0; i < dayArray.length(); i++) {
                JSONObject obj = dayArray.optJSONObject(i);
                if (obj == null) {
                    continue;
                }

                String title = obj.optString("title", "").trim();
                if (title.isEmpty()) {
                    continue;
                }
                String desc = obj.optString("desc", "").trim();
                tasks.add(new TodayTask(title, desc));
            }
        } catch (Exception ignored) {
            return new ArrayList<>();
        }

        return tasks;
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
            // Si falla auth en background usamos nombre neutral.
        }
        return "usuario";
    }

    @NonNull
    private String buildTodaySnapshot(@NonNull List<TodayTask> tasks) {
        StringBuilder builder = new StringBuilder();
        builder.append("total_tareas_hoy=").append(tasks.size()).append("; ");
        builder.append("lista=[");

        for (int i = 0; i < tasks.size() && i < 12; i++) {
            TodayTask task = tasks.get(i);
            if (i > 0) {
                builder.append(" | ");
            }
            builder.append(i + 1)
                    .append(") ")
                    .append(task.title);
            if (!TextUtils.isEmpty(task.desc)) {
                builder.append(" - ").append(task.desc);
            }
        }

        builder.append("]");
        return builder.toString();
    }

    private void notifyTodaySummary(@NonNull Context context, @NonNull String message, int taskCount) {
        createChannelIfNeeded(context);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            int granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS);
            if (granted != PackageManager.PERMISSION_GRANTED) {
                return;
            }
        }

        Intent openIntent = new Intent(context, MainActivity.class);
        openIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        int pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            pendingFlags |= PendingIntent.FLAG_IMMUTABLE;
        }

        PendingIntent contentIntent = PendingIntent.getActivity(
                context,
                100,
                openIntent,
                pendingFlags
        );

        String title = "Agenda de hoy: " + taskCount + " tareas";
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_calendar_today)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .setContentIntent(contentIntent);

        try {
            NotificationManagerCompat.from(context).notify(
                    NOTIFICATION_BASE_ID + (int) (System.currentTimeMillis() % 1000),
                    builder.build()
            );
        } catch (SecurityException ignored) {
            // Runtime permission may be revoked between check and notify.
        }
    }

    private void createChannelIfNeeded(@NonNull Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }

        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null) {
            return;
        }

        NotificationChannel existing = manager.getNotificationChannel(CHANNEL_ID);
        if (existing != null) {
            return;
        }

        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
        );
        channel.setDescription("Resumen periodico con IA de tareas del dia");
        manager.createNotificationChannel(channel);
    }

    private static final class TodayTask {
        final String title;
        final String desc;

        TodayTask(@NonNull String title, @NonNull String desc) {
            this.title = title;
            this.desc = desc;
        }
    }
}
