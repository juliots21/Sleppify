package com.example.sleppify

object TaskReminderConstants {
    const val PREFS_STATE = "sleppify_task_reminder_state"
    const val KEY_ACTIVE_REMINDER_IDS = "active_reminder_ids"

    const val PREFS_CACHE = "sleppify_task_reminder_cache"
    const val KEY_SUMMARY_PREFIX = "summary_"
    const val KEY_SUMMARY_TS_PREFIX = "summary_ts_"
    const val KEY_DELIVERED_TS_PREFIX = "delivered_ts_"

    const val INPUT_REMINDER_ID = "input_reminder_id"
    const val INPUT_TASK_TITLE = "input_task_title"
    const val INPUT_TASK_DESC = "input_task_desc"
    const val INPUT_TASK_TIME = "input_task_time"
    const val INPUT_DATE_KEY = "input_date_key"
    const val INPUT_DUE_AT_MS = "input_due_at_ms"
    const val INPUT_NOTIFY_AT_MS = "input_notify_at_ms"

    const val TAG_PREPARE = "agenda_task_prepare"
    const val TAG_NOTIFY = "agenda_task_notify"

    const val UNIQUE_PREPARE_PREFIX = "agenda_task_prepare_"
    const val UNIQUE_NOTIFY_PREFIX = "agenda_task_notify_"

    const val AI_PREFETCH_BEFORE_NOTIFY_MS = 60_000L
    const val LEAD_MINUTES_ENFORCED = 1

    const val CHANNEL_ID = "agenda_task_reminder_channel"
    const val CHANNEL_NAME = "Recordatorios Agenda"
    const val NOTIFICATION_ID_BASE = 5200
}
