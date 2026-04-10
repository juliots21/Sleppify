package com.example.sleppify

import android.content.Context
import android.text.TextUtils
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import org.json.JSONArray
import org.json.JSONObject
import java.util.Collections
import java.util.concurrent.TimeUnit

object TaskReminderScheduler {

    @JvmStatic
    fun rescheduleAll(context: Context) {
        val appContext = context.applicationContext
        val settingsPrefs = appContext.getSharedPreferences(CloudSyncManager.PREFS_SETTINGS, Context.MODE_PRIVATE)
        val smartSuggestionsEnabled = settingsPrefs.getBoolean(
            CloudSyncManager.KEY_SMART_SUGGESTIONS_ENABLED,
            settingsPrefs.getBoolean(CloudSyncManager.KEY_AI_SHIFT_ENABLED, true)
        )

        if (!smartSuggestionsEnabled) {
            cancelAll(appContext)
            return
        }

        var leadMinutes = settingsPrefs.getInt(
            CloudSyncManager.KEY_NOTIFICATION_LEAD_MINUTES,
            TaskReminderConstants.LEAD_MINUTES_ENFORCED
        )

        if (leadMinutes != TaskReminderConstants.LEAD_MINUTES_ENFORCED) {
            settingsPrefs.edit()
                .putInt(CloudSyncManager.KEY_NOTIFICATION_LEAD_MINUTES, TaskReminderConstants.LEAD_MINUTES_ENFORCED)
                .apply()
            leadMinutes = TaskReminderConstants.LEAD_MINUTES_ENFORCED
        }

        val agendaJson = CloudSyncManager.getInstance(appContext).localAgendaJson
        val nowMs = System.currentTimeMillis()
        val specs = buildReminderSpecs(agendaJson, nowMs, leadMinutes)

        val workManager = WorkManager.getInstance(appContext)
        val statePrefs = appContext.getSharedPreferences(TaskReminderConstants.PREFS_STATE, Context.MODE_PRIVATE)
        val previousIds = HashSet(
            statePrefs.getStringSet(TaskReminderConstants.KEY_ACTIVE_REMINDER_IDS, Collections.emptySet())
                ?: emptySet()
        )

        val nextIds = HashSet<String>()
        for (spec in specs) {
            nextIds.add(spec.reminderId)
            enqueuePrepareWork(workManager, spec)
            enqueueNotifyWork(workManager, spec)
        }

        for (previousId in previousIds) {
            if (!nextIds.contains(previousId)) {
                cancelReminderChain(workManager, previousId)
            }
        }

        statePrefs.edit()
            .putStringSet(TaskReminderConstants.KEY_ACTIVE_REMINDER_IDS, nextIds)
            .apply()
    }

    @JvmStatic
    fun cancelAll(context: Context) {
        val appContext = context.applicationContext
        val workManager = WorkManager.getInstance(appContext)
        val statePrefs = appContext.getSharedPreferences(TaskReminderConstants.PREFS_STATE, Context.MODE_PRIVATE)

        val previousIds = HashSet(
            statePrefs.getStringSet(TaskReminderConstants.KEY_ACTIVE_REMINDER_IDS, Collections.emptySet())
                ?: emptySet()
        )

        for (previousId in previousIds) {
            cancelReminderChain(workManager, previousId)
        }

        workManager.cancelAllWorkByTag(TaskReminderConstants.TAG_PREPARE)
        workManager.cancelAllWorkByTag(TaskReminderConstants.TAG_NOTIFY)

        statePrefs.edit()
            .remove(TaskReminderConstants.KEY_ACTIVE_REMINDER_IDS)
            .apply()
    }

    @JvmStatic
    fun buildReminderSpecs(agendaJson: String, nowMs: Long, leadMinutes: Int): List<ReminderSpec> {
        val specs = ArrayList<ReminderSpec>()
        if (TextUtils.isEmpty(agendaJson)) {
            return specs
        }

        val leadMs = TimeUnit.MINUTES.toMillis(kotlin.math.max(1, leadMinutes).toLong())

        try {
            val root = JSONObject(agendaJson)
            val dayIterator = root.keys()
            while (dayIterator.hasNext()) {
                val dateKey = dayIterator.next()
                val dayArray: JSONArray = root.optJSONArray(dateKey) ?: continue
                if (dayArray.length() == 0) {
                    continue
                }

                for (i in 0 until dayArray.length()) {
                    val taskObj = dayArray.optJSONObject(i) ?: continue
                    val title = taskObj.optString("title", "").trim()
                    val desc = taskObj.optString("desc", "").trim()
                    val time = taskObj.optString("time", "").trim()
                    if (title.isEmpty() || time.isEmpty()) {
                        continue
                    }

                    val dueAtMs = TaskReminderTimeUtils.parseDueAtMillis(dateKey, time)
                    if (dueAtMs <= nowMs) {
                        continue
                    }

                    var notifyAtMs = dueAtMs - leadMs
                    if (notifyAtMs <= nowMs) {
                        notifyAtMs = nowMs + 1_000L
                    }

                    val prepareAtMs = notifyAtMs - TaskReminderConstants.AI_PREFETCH_BEFORE_NOTIFY_MS
                    val prepareDelayMs = TaskReminderTimeUtils.computeDelayMs(prepareAtMs, nowMs)
                    val notifyDelayMs = TaskReminderTimeUtils.computeDelayMs(notifyAtMs, nowMs)

                    val rawReminderId = "$dateKey|$i|$title|$time|$desc"
                    val reminderId = TaskReminderTimeUtils.buildStableReminderId(rawReminderId)

                    specs.add(
                        ReminderSpec(
                            reminderId,
                            dateKey,
                            title,
                            desc,
                            time,
                            dueAtMs,
                            notifyAtMs,
                            prepareDelayMs,
                            notifyDelayMs
                        )
                    )
                }
            }
        } catch (_: Exception) {
            return specs
        }

        specs.sortBy { it.notifyAtMs }
        return specs
    }

    private fun enqueuePrepareWork(workManager: WorkManager, spec: ReminderSpec) {
        val inputData = buildInputData(spec)
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = OneTimeWorkRequest.Builder(TaskReminderPrepareWorker::class.java)
            .setInputData(inputData)
            .setInitialDelay(spec.prepareDelayMs, TimeUnit.MILLISECONDS)
            .setConstraints(constraints)
            .addTag(TaskReminderConstants.TAG_PREPARE)
            .build()

        val uniqueName = TaskReminderConstants.UNIQUE_PREPARE_PREFIX + spec.reminderId
        workManager.enqueueUniqueWork(uniqueName, ExistingWorkPolicy.REPLACE, request)
    }

    private fun enqueueNotifyWork(workManager: WorkManager, spec: ReminderSpec) {
        val inputData = buildInputData(spec)

        val request = OneTimeWorkRequest.Builder(TaskReminderNotifyWorker::class.java)
            .setInputData(inputData)
            .setInitialDelay(spec.notifyDelayMs, TimeUnit.MILLISECONDS)
            .addTag(TaskReminderConstants.TAG_NOTIFY)
            .build()

        val uniqueName = TaskReminderConstants.UNIQUE_NOTIFY_PREFIX + spec.reminderId
        workManager.enqueueUniqueWork(uniqueName, ExistingWorkPolicy.REPLACE, request)
    }

    private fun buildInputData(spec: ReminderSpec): Data {
        return Data.Builder()
            .putString(TaskReminderConstants.INPUT_REMINDER_ID, spec.reminderId)
            .putString(TaskReminderConstants.INPUT_TASK_TITLE, spec.title)
            .putString(TaskReminderConstants.INPUT_TASK_DESC, spec.desc)
            .putString(TaskReminderConstants.INPUT_TASK_TIME, spec.time)
            .putString(TaskReminderConstants.INPUT_DATE_KEY, spec.dateKey)
            .putLong(TaskReminderConstants.INPUT_DUE_AT_MS, spec.dueAtMs)
            .putLong(TaskReminderConstants.INPUT_NOTIFY_AT_MS, spec.notifyAtMs)
            .build()
    }

    private fun cancelReminderChain(workManager: WorkManager, reminderId: String) {
        workManager.cancelUniqueWork(TaskReminderConstants.UNIQUE_PREPARE_PREFIX + reminderId)
        workManager.cancelUniqueWork(TaskReminderConstants.UNIQUE_NOTIFY_PREFIX + reminderId)
    }

    class ReminderSpec(
        @JvmField val reminderId: String,
        @JvmField val dateKey: String,
        @JvmField val title: String,
        @JvmField val desc: String,
        @JvmField val time: String,
        @JvmField val dueAtMs: Long,
        @JvmField val notifyAtMs: Long,
        @JvmField val prepareDelayMs: Long,
        @JvmField val notifyDelayMs: Long
    )
}
