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
import androidx.work.Worker
import androidx.work.WorkerParameters

class TaskReminderNotifyWorker(context: Context, workerParams: WorkerParameters) : Worker(context, workerParams) {

    override fun doWork(): Result {
        val context = applicationContext
        val settingsPrefs = context.getSharedPreferences(CloudSyncManager.PREFS_SETTINGS, Context.MODE_PRIVATE)
        val smartSuggestionsEnabled = settingsPrefs.getBoolean(
            CloudSyncManager.KEY_SMART_SUGGESTIONS_ENABLED,
            settingsPrefs.getBoolean(CloudSyncManager.KEY_AI_SHIFT_ENABLED, true)
        )
        if (!smartSuggestionsEnabled) {
            return Result.success()
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            if (granted != PackageManager.PERMISSION_GRANTED) {
                return Result.success()
            }
        }

        val input: Data = inputData
        val reminderId = input.getString(TaskReminderConstants.INPUT_REMINDER_ID)
        val taskTitle = input.getString(TaskReminderConstants.INPUT_TASK_TITLE)
        val taskDesc = input.getString(TaskReminderConstants.INPUT_TASK_DESC)
        val taskTime = input.getString(TaskReminderConstants.INPUT_TASK_TIME)

        if (TextUtils.isEmpty(reminderId) || TextUtils.isEmpty(taskTitle)) {
            return Result.success()
        }

        val cachePrefs = context.getSharedPreferences(TaskReminderConstants.PREFS_CACHE, Context.MODE_PRIVATE)
        val deliveredAt = cachePrefs.getLong(TaskReminderConstants.KEY_DELIVERED_TS_PREFIX + reminderId, 0L)
        if (deliveredAt > 0L) {
            return Result.success()
        }

        var summary = cachePrefs.getString(TaskReminderConstants.KEY_SUMMARY_PREFIX + reminderId, "")
        if (summary.isNullOrBlank()) {
            summary = buildFallbackSummary(taskTitle!!, taskDesc, taskTime)
        }

        createChannelIfNeeded(context)

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        var pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            pendingFlags = pendingFlags or PendingIntent.FLAG_IMMUTABLE
        }

        val contentIntent = PendingIntent.getActivity(
            context,
            kotlin.math.abs(reminderId.hashCode()),
            openIntent,
            pendingFlags
        )

        val builder = NotificationCompat.Builder(context, TaskReminderConstants.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_cat)
            .setContentTitle("Recordatorio: $taskTitle")
            .setContentText(summary)
            .setStyle(NotificationCompat.BigTextStyle().bigText(summary))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)

        if (!taskTime.isNullOrBlank()) {
            builder.setSubText("Agenda • $taskTime")
        }

        try {
            val notificationId = TaskReminderConstants.NOTIFICATION_ID_BASE + kotlin.math.abs(reminderId.hashCode() % 100000)
            NotificationManagerCompat.from(context).notify(notificationId, builder.build())
        } catch (_: SecurityException) {
            return Result.success()
        }

        cachePrefs.edit()
            .putLong(TaskReminderConstants.KEY_DELIVERED_TS_PREFIX + reminderId, System.currentTimeMillis())
            .remove(TaskReminderConstants.KEY_SUMMARY_PREFIX + reminderId)
            .remove(TaskReminderConstants.KEY_SUMMARY_TS_PREFIX + reminderId)
            .apply()

        return Result.success()
    }

    private fun createChannelIfNeeded(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }

        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val existing = manager.getNotificationChannel(TaskReminderConstants.CHANNEL_ID)
        if (existing != null) {
            return
        }

        val channel = NotificationChannel(
            TaskReminderConstants.CHANNEL_ID,
            TaskReminderConstants.CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH
        )
        channel.description = "Recordatorios por tarea con resumen inteligente"
        manager.createNotificationChannel(channel)
    }

    private fun buildFallbackSummary(taskTitle: String, taskDesc: String?, taskTime: String?): String {
        val safeDesc = taskDesc?.trim().orEmpty()
        val safeTime = taskTime?.trim().orEmpty()

        return when {
            safeDesc.isNotEmpty() && safeTime.isNotEmpty() -> "En 1 minuto: $taskTitle ($safeTime). $safeDesc"
            safeTime.isNotEmpty() -> "En 1 minuto: $taskTitle ($safeTime)."
            safeDesc.isNotEmpty() -> "En 1 minuto: $taskTitle. $safeDesc"
            else -> "En 1 minuto: $taskTitle."
        }
    }
}
