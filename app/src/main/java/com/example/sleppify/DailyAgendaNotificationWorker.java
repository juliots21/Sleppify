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
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
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
    private static final String UNIQUE_WORK_PREFIX = "daily_agenda_ai_notifications_slot_";
    private static final String WORK_TAG = "daily_agenda_ai_notifications_tag";
    private static final String INPUT_SLOT_INDEX = "slot_index";
    private static final String INPUT_TIMES_PER_DAY = "times_per_day";
    private static final String CHANNEL_ID = "daily_agenda_ai_channel";
    private static final String CHANNEL_NAME = "Agenda IA";
    private static final int NOTIFICATION_BASE_ID = 4200;
    private static final int DEFAULT_TIMES_PER_DAY = 2;
    private static final int MIN_TIMES_PER_DAY = 1;
    private static final int MAX_TIMES_PER_DAY = 5;
    private static final int START_HOUR = 6;
    private static final int END_HOUR = 22;

    public DailyAgendaNotificationWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    public static void schedule(@NonNull Context context) {
        SharedPreferences settingsPrefs = context.getSharedPreferences(CloudSyncManager.PREFS_SETTINGS, Context.MODE_PRIVATE);
        int configuredTimesPerDay = settingsPrefs.getInt(
                CloudSyncManager.KEY_DAILY_SUMMARY_INTERVAL_HOURS,
                DEFAULT_TIMES_PER_DAY
        );
        schedule(context, configuredTimesPerDay);
    }

    public static void schedule(@NonNull Context context, int configuredTimesPerDay) {
        int safeTimesPerDay = sanitizeTimesPerDay(configuredTimesPerDay);
        cancel(context);

        for (int slotIndex = 0; slotIndex < safeTimesPerDay; slotIndex++) {
            enqueueSlotWork(context, safeTimesPerDay, slotIndex);
        }
    }

    public static void cancel(@NonNull Context context) {
        WorkManager manager = WorkManager.getInstance(context);
        manager.cancelUniqueWork(UNIQUE_WORK_NAME);
        manager.cancelAllWorkByTag(WORK_TAG);
        for (int slotIndex = 0; slotIndex < MAX_TIMES_PER_DAY; slotIndex++) {
            manager.cancelUniqueWork(uniqueWorkNameForSlot(slotIndex));
        }
    }

    @NonNull
    @Override
    public Result doWork() {
        Context context = getApplicationContext();
        SharedPreferences settingsPrefs = context.getSharedPreferences(CloudSyncManager.PREFS_SETTINGS, Context.MODE_PRIVATE);
        int configuredTimesPerDay = sanitizeTimesPerDay(
                settingsPrefs.getInt(CloudSyncManager.KEY_DAILY_SUMMARY_INTERVAL_HOURS, DEFAULT_TIMES_PER_DAY)
        );
        int slotIndexFromInput = getInputData().getInt(INPUT_SLOT_INDEX, 0);
        boolean smartSuggestionsEnabled = settingsPrefs.getBoolean(
                CloudSyncManager.KEY_SMART_SUGGESTIONS_ENABLED,
                settingsPrefs.getBoolean(CloudSyncManager.KEY_AI_SHIFT_ENABLED, true)
        );
        if (!smartSuggestionsEnabled) {
            return Result.success();
        }

        if (slotIndexFromInput >= 0 && slotIndexFromInput < configuredTimesPerDay) {
            // Keep the schedule alive by chaining the same slot for the next day.
            enqueueSlotWork(context, configuredTimesPerDay, slotIndexFromInput);
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
            completed = false;
        }

        String aiMessage = aiMessageRef.get();
        if (!completed || TextUtils.isEmpty(aiMessage)) {
            aiMessage = buildFallbackSummary(todayTasks);
        }

        notifyTodaySummary(context, aiMessage, todayTasks.size());
        return Result.success();
    }

    private static int sanitizeTimesPerDay(int value) {
        return Math.max(MIN_TIMES_PER_DAY, Math.min(MAX_TIMES_PER_DAY, value));
    }

    @NonNull
    private static String uniqueWorkNameForSlot(int slotIndex) {
        return UNIQUE_WORK_PREFIX + slotIndex;
    }

    private static void enqueueSlotWork(@NonNull Context context, int timesPerDay, int slotIndex) {
        int[] slotHours = buildSlotHours(timesPerDay);
        if (slotIndex < 0 || slotIndex >= slotHours.length) {
            return;
        }

        long delayMs = computeInitialDelayMs(slotHours[slotIndex]);
        Data input = new Data.Builder()
                .putInt(INPUT_TIMES_PER_DAY, timesPerDay)
                .putInt(INPUT_SLOT_INDEX, slotIndex)
                .build();

        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(DailyAgendaNotificationWorker.class)
                .setInputData(input)
                .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
                .addTag(WORK_TAG)
                .build();

        WorkManager.getInstance(context)
                .enqueueUniqueWork(
                        uniqueWorkNameForSlot(slotIndex),
                        ExistingWorkPolicy.REPLACE,
                        request
                );
    }

    private static long computeInitialDelayMs(int targetHour) {
        Calendar now = Calendar.getInstance();
        Calendar target = Calendar.getInstance();
        target.set(Calendar.HOUR_OF_DAY, targetHour);
        target.set(Calendar.MINUTE, 0);
        target.set(Calendar.SECOND, 0);
        target.set(Calendar.MILLISECOND, 0);

        if (!target.after(now)) {
            target.add(Calendar.DAY_OF_YEAR, 1);
        }

        long delay = target.getTimeInMillis() - now.getTimeInMillis();
        return Math.max(1000L, delay);
    }

    @NonNull
    private static int[] buildSlotHours(int timesPerDay) {
        int safeTimesPerDay = sanitizeTimesPerDay(timesPerDay);
        int[] hours = new int[safeTimesPerDay];
        int range = END_HOUR - START_HOUR;

        if (safeTimesPerDay == 1) {
            hours[0] = START_HOUR + (range / 2);
            return hours;
        }

        for (int i = 0; i < safeTimesPerDay; i++) {
            float ratio = (float) i / (float) (safeTimesPerDay - 1);
            int hour = Math.round(START_HOUR + (ratio * range));
            hours[i] = Math.max(START_HOUR, Math.min(END_HOUR, hour));
        }
        return hours;
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

    @NonNull
    private String buildFallbackSummary(@NonNull List<TodayTask> tasks) {
        if (tasks.isEmpty()) {
            return "Revisa tus tareas del dia en agenda.";
        }
        if (tasks.size() == 1) {
            return "Hoy tienes 1 tarea. Empieza por: " + tasks.get(0).title;
        }
        return "Hoy tienes " + tasks.size() + " tareas. Prioriza: " + tasks.get(0).title;
    }

    private void notifyTodaySummary(@NonNull Context context, @NonNull String message, int taskCount) {
        String title = "Agenda de hoy: " + taskCount + " tareas";
        postNotification(
                context,
                title,
                message,
                NOTIFICATION_BASE_ID + (int) (System.currentTimeMillis() % 1000)
        );
    }

    private static void postNotification(
            @NonNull Context context,
            @NonNull String title,
            @NonNull String message,
            int notificationId
    ) {
        createChannelIfNeeded(context);

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

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_calendar_today)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .setContentIntent(contentIntent);

        try {
            NotificationManagerCompat.from(context).notify(notificationId, builder.build());
        } catch (SecurityException ignored) {
            // Runtime permission may be revoked between check and notify.
        }
    }

    private static void createChannelIfNeeded(@NonNull Context context) {
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
