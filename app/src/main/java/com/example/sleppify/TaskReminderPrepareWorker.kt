package com.example.sleppify

import android.content.Context
import android.text.TextUtils
import androidx.work.Data
import androidx.work.Worker
import androidx.work.WorkerParameters

class TaskReminderPrepareWorker(context: Context, workerParams: WorkerParameters) : Worker(context, workerParams) {

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

        val input: Data = inputData
        val reminderId = input.getString(TaskReminderConstants.INPUT_REMINDER_ID)
        val taskTitle = input.getString(TaskReminderConstants.INPUT_TASK_TITLE)
        val taskDesc = input.getString(TaskReminderConstants.INPUT_TASK_DESC)
        val taskTime = input.getString(TaskReminderConstants.INPUT_TASK_TIME)
        val dateKey = input.getString(TaskReminderConstants.INPUT_DATE_KEY)
        val notifyAtMs = input.getLong(TaskReminderConstants.INPUT_NOTIFY_AT_MS, 0L)
        val cachePrefs = context.getSharedPreferences(TaskReminderConstants.PREFS_CACHE, Context.MODE_PRIVATE)

        if (TextUtils.isEmpty(reminderId) || TextUtils.isEmpty(taskTitle)) {
            return Result.success()
        }

        // AI is intentionally NOT invoked from background workers. Per product rule,
        // the only allowed Gemini triggers are pull-to-refresh and task creation.
        // This worker now always builds a local fallback summary.
        val summary = buildFallbackSummary(taskTitle!!, taskDesc, taskTime)

        cachePrefs.edit()
            .putString(TaskReminderConstants.KEY_SUMMARY_PREFIX + reminderId, summary)
            .putLong(TaskReminderConstants.KEY_SUMMARY_TS_PREFIX + reminderId, System.currentTimeMillis())
            .apply()

        return Result.success()
    }

    private fun resolveProfileName(context: Context): String {
        return try {
            val authManager = AuthManager.getInstance(context)
            if (authManager.isSignedIn()) {
                val displayName = authManager.getDisplayName()
                if (!displayName.isNullOrBlank()) {
                    return displayName
                }
            }
            "usuario"
        } catch (_: Throwable) {
            "usuario"
        }
    }

    private fun buildTaskSnapshot(
        taskTitle: String,
        taskDesc: String?,
        taskTime: String?,
        dateKey: String?
    ): String {
        val safeDesc = taskDesc?.trim().orEmpty()
        val safeTime = taskTime?.trim().orEmpty()
        val safeDate = dateKey?.trim().orEmpty()

        return buildString {
            append("total_tareas_hoy=1; ")
            append("lista=[1) ")
            append(taskTitle)
            if (safeDesc.isNotEmpty()) {
                append(" - ")
                append(safeDesc)
            }
            if (safeTime.isNotEmpty()) {
                append(" - hora ")
                append(safeTime)
            }
            if (safeDate.isNotEmpty()) {
                append(" - fecha ")
                append(safeDate)
            }
            append("]")
        }
    }

    private fun buildFallbackSummary(taskTitle: String, taskDesc: String?, taskTime: String?): String {
        val safeDesc = taskDesc?.trim().orEmpty()
        val safeTime = taskTime?.trim().orEmpty()

        return when {
            safeDesc.isNotEmpty() && safeTime.isNotEmpty() -> "Recordatorio breve: $taskTitle a las $safeTime. $safeDesc"
            safeTime.isNotEmpty() -> "Recordatorio breve: $taskTitle a las $safeTime."
            safeDesc.isNotEmpty() -> "Recordatorio breve: $taskTitle. $safeDesc"
            else -> "Recordatorio breve: toca avanzar en \"$taskTitle\"."
        }
    }
}
