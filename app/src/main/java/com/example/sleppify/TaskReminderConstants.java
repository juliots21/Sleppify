package com.example.sleppify;

final class TaskReminderConstants {

    static final String PREFS_STATE = "sleppify_task_reminder_state";
    static final String KEY_ACTIVE_REMINDER_IDS = "active_reminder_ids";

    static final String PREFS_CACHE = "sleppify_task_reminder_cache";
    static final String KEY_SUMMARY_PREFIX = "summary_";
    static final String KEY_SUMMARY_TS_PREFIX = "summary_ts_";
    static final String KEY_DELIVERED_TS_PREFIX = "delivered_ts_";

    static final String INPUT_REMINDER_ID = "input_reminder_id";
    static final String INPUT_TASK_TITLE = "input_task_title";
    static final String INPUT_TASK_DESC = "input_task_desc";
    static final String INPUT_TASK_TIME = "input_task_time";
    static final String INPUT_DATE_KEY = "input_date_key";
    static final String INPUT_DUE_AT_MS = "input_due_at_ms";
    static final String INPUT_NOTIFY_AT_MS = "input_notify_at_ms";

    static final String TAG_PREPARE = "agenda_task_prepare";
    static final String TAG_NOTIFY = "agenda_task_notify";

    static final String UNIQUE_PREPARE_PREFIX = "agenda_task_prepare_";
    static final String UNIQUE_NOTIFY_PREFIX = "agenda_task_notify_";

    static final long AI_PREFETCH_BEFORE_NOTIFY_MS = 60_000L;
    static final int LEAD_MINUTES_ENFORCED = 1;

    static final String CHANNEL_ID = "agenda_task_reminder_channel";
    static final String CHANNEL_NAME = "Recordatorios Agenda";
    static final int NOTIFICATION_ID_BASE = 5200;

    private TaskReminderConstants() {
    }
}
