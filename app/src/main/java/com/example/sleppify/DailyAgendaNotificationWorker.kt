package com.example.sleppify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.text.TextUtils
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit

class DailyAgendaNotificationWorker(context: Context, workerParams: WorkerParameters) : Worker(context, workerParams) {

    override fun doWork(): Result {
        val context = applicationContext
        val settingsPrefs = context.getSharedPreferences(CloudSyncManager.PREFS_SETTINGS, Context.MODE_PRIVATE)
        val configuredTimesPerDay = sanitizeTimesPerDay(
            settingsPrefs.getInt(CloudSyncManager.KEY_DAILY_SUMMARY_INTERVAL_HOURS, DEFAULT_TIMES_PER_DAY)
        )

        val slotIndexFromInput = inputData.getInt(INPUT_SLOT_INDEX, 0)
        val smartSuggestionsEnabled = settingsPrefs.getBoolean(
            CloudSyncManager.KEY_SMART_SUGGESTIONS_ENABLED,
            settingsPrefs.getBoolean(CloudSyncManager.KEY_AI_SHIFT_ENABLED, true)
        )
        if (!smartSuggestionsEnabled) {
            return Result.success()
        }

        if (slotIndexFromInput >= 0 && slotIndexFromInput < configuredTimesPerDay) {
            // Keep the schedule alive by chaining the same slot for the next day.
            enqueueSlotWork(context, configuredTimesPerDay, slotIndexFromInput)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            if (granted != PackageManager.PERMISSION_GRANTED) {
                return Result.success()
            }
        }

        val todayTasks = readTodayTasks(context)
        if (todayTasks.isEmpty()) {
            return Result.success()
        }

        // AI is intentionally NOT invoked from background workers. Per product rule,
        // the only allowed Gemini triggers are pull-to-refresh and task creation.
        val summary = buildFallbackSummary(todayTasks)
        notifyTodaySummary(context, summary, todayTasks.size)
        return Result.success()
    }

    private fun readTodayTasks(context: Context): List<TodayTask> {
        val tasks = ArrayList<TodayTask>()
        val agendaJson = CloudSyncManager.getInstance(context).localAgendaJson

        try {
            val root = JSONObject(agendaJson)
            val todayKey = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Calendar.getInstance().time)
            val dayArray: JSONArray = root.optJSONArray(todayKey) ?: return tasks

            for (i in 0 until dayArray.length()) {
                val obj = dayArray.optJSONObject(i) ?: continue
                val title = obj.optString("title", "").trim()
                if (title.isEmpty()) {
                    continue
                }
                val desc = obj.optString("desc", "").trim()
                tasks.add(TodayTask(title, desc))
            }
        } catch (_: Exception) {
            return emptyList()
        }

        return tasks
    }

    private fun resolveProfileName(context: Context): String {
        return try {
            val authManager = AuthManager.getInstance(context)
            if (authManager.isSignedIn()) {
                val displayName = authManager.getDisplayName()
                if (!TextUtils.isEmpty(displayName)) {
                    return displayName
                }
            }
            "usuario"
        } catch (_: Throwable) {
            "usuario"
        }
    }

    private fun buildTodaySnapshot(tasks: List<TodayTask>): String {
        return buildString {
            append("total_tareas_hoy=")
            append(tasks.size)
            append("; ")
            append("lista=[")

            for (i in tasks.indices) {
                if (i >= 12) {
                    break
                }
                val task = tasks[i]
                if (i > 0) {
                    append(" | ")
                }
                append(i + 1)
                append(") ")
                append(task.title)
                if (!task.desc.isBlank()) {
                    append(" - ")
                    append(task.desc)
                }
            }
            append("]")
        }
    }

    private fun buildFallbackSummary(tasks: List<TodayTask>): String {
        return when {
            tasks.isEmpty() -> "Revisa tus tareas del dia en agenda."
            tasks.size == 1 -> "Hoy tienes 1 tarea. Empieza por: ${tasks[0].title}"
            else -> "Hoy tienes ${tasks.size} tareas. Prioriza: ${tasks[0].title}"
        }
    }

    private fun notifyTodaySummary(context: Context, message: String, taskCount: Int) {
        val title = "Agenda de hoy: $taskCount tareas"
        postNotification(
            context,
            title,
            message,
            NOTIFICATION_BASE_ID + (System.currentTimeMillis() % 1000).toInt()
        )
    }

    private class TodayTask(
        val title: String,
        val desc: String
    )

    companion object {
        const val UNIQUE_WORK_NAME = "daily_agenda_ai_notifications"
        private const val UNIQUE_WORK_PREFIX = "daily_agenda_ai_notifications_slot_"
        private const val WORK_TAG = "daily_agenda_ai_notifications_tag"
        private const val INPUT_SLOT_INDEX = "slot_index"
        private const val INPUT_TIMES_PER_DAY = "times_per_day"
        private const val CHANNEL_ID = "daily_agenda_ai_channel"
        private const val CHANNEL_NAME = "Agenda IA"
        private const val NOTIFICATION_BASE_ID = 4200
        private const val DEFAULT_TIMES_PER_DAY = 2
        private const val MIN_TIMES_PER_DAY = 1
        private const val MAX_TIMES_PER_DAY = 5
        private const val START_HOUR = 6
        private const val END_HOUR = 22

        @JvmStatic
        fun schedule(context: Context) {
            val settingsPrefs = context.getSharedPreferences(CloudSyncManager.PREFS_SETTINGS, Context.MODE_PRIVATE)
            val configuredTimesPerDay = settingsPrefs.getInt(
                CloudSyncManager.KEY_DAILY_SUMMARY_INTERVAL_HOURS,
                DEFAULT_TIMES_PER_DAY
            )
            schedule(context, configuredTimesPerDay)
        }

        @JvmStatic
        fun schedule(context: Context, configuredTimesPerDay: Int) {
            val safeTimesPerDay = sanitizeTimesPerDay(configuredTimesPerDay)
            cancel(context)

            for (slotIndex in 0 until safeTimesPerDay) {
                enqueueSlotWork(context, safeTimesPerDay, slotIndex)
            }
        }

        @JvmStatic
        fun cancel(context: Context) {
            val manager = WorkManager.getInstance(context)
            manager.cancelUniqueWork(UNIQUE_WORK_NAME)
            manager.cancelAllWorkByTag(WORK_TAG)

            for (slotIndex in 0 until MAX_TIMES_PER_DAY) {
                manager.cancelUniqueWork(uniqueWorkNameForSlot(slotIndex))
            }
        }

        private fun sanitizeTimesPerDay(value: Int): Int {
            return value.coerceIn(MIN_TIMES_PER_DAY, MAX_TIMES_PER_DAY)
        }

        private fun uniqueWorkNameForSlot(slotIndex: Int): String {
            return UNIQUE_WORK_PREFIX + slotIndex
        }

        private fun enqueueSlotWork(context: Context, timesPerDay: Int, slotIndex: Int) {
            val slotHours = buildSlotHours(timesPerDay)
            if (slotIndex < 0 || slotIndex >= slotHours.size) {
                return
            }

            val delayMs = computeInitialDelayMs(slotHours[slotIndex])
            val input = Data.Builder()
                .putInt(INPUT_TIMES_PER_DAY, timesPerDay)
                .putInt(INPUT_SLOT_INDEX, slotIndex)
                .build()

            val request = OneTimeWorkRequest.Builder(DailyAgendaNotificationWorker::class.java)
                .setInputData(input)
                .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
                .addTag(WORK_TAG)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                uniqueWorkNameForSlot(slotIndex),
                ExistingWorkPolicy.REPLACE,
                request
            )
        }

        private fun computeInitialDelayMs(targetHour: Int): Long {
            val now = Calendar.getInstance()
            val target = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, targetHour)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            if (!target.after(now)) {
                target.add(Calendar.DAY_OF_YEAR, 1)
            }

            val delay = target.timeInMillis - now.timeInMillis
            return kotlin.math.max(1000L, delay)
        }

        private fun buildSlotHours(timesPerDay: Int): IntArray {
            val safeTimesPerDay = sanitizeTimesPerDay(timesPerDay)
            val hours = IntArray(safeTimesPerDay)
            val range = END_HOUR - START_HOUR

            if (safeTimesPerDay == 1) {
                hours[0] = START_HOUR + (range / 2)
                return hours
            }

            for (i in 0 until safeTimesPerDay) {
                val ratio = i.toFloat() / (safeTimesPerDay - 1).toFloat()
                val hour = kotlin.math.round(START_HOUR + (ratio * range)).toInt()
                hours[i] = hour.coerceIn(START_HOUR, END_HOUR)
            }
            return hours
        }

        private fun postNotification(context: Context, title: String, message: String, notificationId: Int) {
            createChannelIfNeeded(context)

            val openIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }

            var pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                pendingFlags = pendingFlags or PendingIntent.FLAG_IMMUTABLE
            }

            val contentIntent = PendingIntent.getActivity(context, 100, openIntent, pendingFlags)

            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification_cat)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .setContentIntent(contentIntent)

            try {
                NotificationManagerCompat.from(context).notify(notificationId, builder.build())
            } catch (_: SecurityException) {
                // Runtime permission may be revoked between check and notify.
            }
        }

        private fun createChannelIfNeeded(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                return
            }

            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            val existing = manager.getNotificationChannel(CHANNEL_ID)
            if (existing != null) {
                return
            }

            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT)
            channel.description = "Resumen periodico con IA de tareas del dia"
            manager.createNotificationChannel(channel)
        }
    }
}
