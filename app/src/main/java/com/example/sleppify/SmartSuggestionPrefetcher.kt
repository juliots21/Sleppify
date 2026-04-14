package com.example.sleppify

import android.content.Context
import android.content.SharedPreferences
import android.text.TextUtils
import org.json.JSONObject
import java.util.Locale

/**
 * Prefetcher for Gemini-powered smart suggestions.
 * Optimized to use Kotlin conventions and integrated with the AI Circuit Breaker.
 */
object SmartSuggestionPrefetcher {

    private const val KEY_SCHEDULE_AI_ACCEPT_COUNT = "schedule_ai_accept_count"
    private const val KEY_SCHEDULE_AI_DISMISS_COUNT = "schedule_ai_dismiss_count"
    private const val KEY_SCHEDULE_AI_LAST_MESSAGE = "schedule_ai_last_message"
    private const val KEY_SCHEDULE_AI_LAST_ACTION = "schedule_ai_last_action"
    private const val KEY_SCHEDULE_AI_OPEN_COUNT = "schedule_ai_open_count"
    private const val KEY_SCHEDULE_AI_NEXT_AT = "schedule_ai_next_at"
    private const val KEY_SCHEDULE_AI_LAST_PROCESS_SESSION = "schedule_ai_last_process_session"
    private const val KEY_SCHEDULE_AI_HIDDEN_UNTIL_NEXT = "schedule_ai_hidden_until_next"
    private const val KEY_SCHEDULE_AI_ACTIONS_HIDDEN = "schedule_ai_actions_hidden"
    private const val KEY_SCHEDULE_AI_FEEDBACK_MESSAGE_HASH = "schedule_ai_feedback_message_hash"

    // Increased interval to 6 hours to further save tokens as requested per general AI logic
    const val SUGGESTION_INTERVAL_MS = 6L * 60L * 60L * 1000L
    private const val AGENDA_SNAPSHOT_CACHE_TTL_MS = 30_000L

    private val agendaSnapshotCacheLock = Any()
    private var cachedAgendaRaw: String? = null
    private var cachedAgendaSnapshot: String? = null
    private var cachedAgendaSnapshotAtMs: Long = 0

    /**
     * Legacy method preserved for compatibility but restricted.
     */
    @JvmStatic
    fun prefetchAll(context: Context, profileName: String?) {
        // No-op to prevent background leaks unless specifically for schedule
    }

    @JvmStatic
    fun prefetchScheduleSuggestion(context: Context, profileName: String?) {
        if (!isSmartSuggestionsEnabled(context)) return
        
        // OPTIMIZATION: Check if AI service is suspended (Circuit Breaker)
        if (GeminiIntelligenceService.isSuspended()) {
            return
        }

        val settingsPrefs = context.getSharedPreferences(CloudSyncManager.PREFS_SETTINGS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()

        val lastProcessSession = settingsPrefs.getLong(KEY_SCHEDULE_AI_LAST_PROCESS_SESSION, Long.MIN_VALUE)
        val isNewProcessSession = lastProcessSession != AiSuggestionSession.PROCESS_SESSION_ID
        
        if (isNewProcessSession) {
            settingsPrefs.edit()
                .putLong(KEY_SCHEDULE_AI_LAST_PROCESS_SESSION, AiSuggestionSession.PROCESS_SESSION_ID)
                .apply()
        }

        val lastMessage = settingsPrefs.getString(KEY_SCHEDULE_AI_LAST_MESSAGE, "")
        val nextAt = settingsPrefs.getLong(KEY_SCHEDULE_AI_NEXT_AT, 0L)
        
        val shouldGenerate = lastMessage.isNullOrEmpty() || isNewProcessSession || now >= nextAt
        if (!shouldGenerate) return

        val safeName = if (profileName.isNullOrBlank()) "usuario" else profileName.trim()
        val agendaSnapshot = buildAgendaSnapshotForAi(context)
        val behaviorSignals = buildBehaviorSignalsForAi(settingsPrefs)

        GeminiIntelligenceService().generateScheduleSuggestion(
            safeName,
            agendaSnapshot,
            behaviorSignals,
            object : GeminiIntelligenceService.ScheduleSuggestionCallback {
                override fun onSuccess(suggestion: GeminiIntelligenceService.ScheduleSuggestion) {
                    val rendered = renderScheduleSuggestionMessage(suggestion)
                    settingsPrefs.edit().apply {
                        putString(KEY_SCHEDULE_AI_LAST_MESSAGE, rendered)
                        putBoolean(KEY_SCHEDULE_AI_HIDDEN_UNTIL_NEXT, false)
                        putBoolean(KEY_SCHEDULE_AI_ACTIONS_HIDDEN, false)
                        remove(KEY_SCHEDULE_AI_FEEDBACK_MESSAGE_HASH)
                        putLong(KEY_SCHEDULE_AI_NEXT_AT, System.currentTimeMillis() + SUGGESTION_INTERVAL_MS)
                        apply()
                    }
                }

                override fun onError(error: String) {
                    val fallback = buildScheduleFallbackFromAgendaSnapshot(agendaSnapshot)
                    settingsPrefs.edit().apply {
                        putString(KEY_SCHEDULE_AI_LAST_MESSAGE, fallback)
                        putBoolean(KEY_SCHEDULE_AI_HIDDEN_UNTIL_NEXT, false)
                        putBoolean(KEY_SCHEDULE_AI_ACTIONS_HIDDEN, false)
                        remove(KEY_SCHEDULE_AI_FEEDBACK_MESSAGE_HASH)
                        putLong(KEY_SCHEDULE_AI_NEXT_AT, System.currentTimeMillis() + SUGGESTION_INTERVAL_MS)
                        apply()
                    }
                }
            }
        )
    }

    private fun renderScheduleSuggestionMessage(suggestion: GeminiIntelligenceService.ScheduleSuggestion): String {
        val message = suggestion.message ?: ""
        val microAction = suggestion.microAction?.trim().orEmpty()
        return if (microAction.isNotEmpty()) {
            "$message\n\nAcción sugerida: $microAction"
        } else {
            message
        }
    }

    private fun buildAgendaSnapshotForAi(context: Context): String {
        val rawAgenda = CloudSyncManager.getInstance(context).localAgendaJson ?: ""
        val now = System.currentTimeMillis()

        synchronized(agendaSnapshotCacheLock) {
            if (cachedAgendaSnapshot != null &&
                cachedAgendaRaw == rawAgenda &&
                (now - cachedAgendaSnapshotAtMs) <= AGENDA_SNAPSHOT_CACHE_TTL_MS
            ) {
                return cachedAgendaSnapshot!!
            }
        }

        var totalTasks = 0
        var morningTasks = 0
        var afternoonTasks = 0
        var nightTasks = 0

        try {
            val root = JSONObject(rawAgenda)
            val names = root.names()
            if (names != null) {
                for (i in 0 until names.length()) {
                    val day = names.optString(i, "")
                    val tasks = root.optJSONArray(day) ?: continue
                    for (j in 0 until tasks.length()) {
                        val task = tasks.optJSONObject(j) ?: continue
                        totalTasks++
                        val hour = parseHourFromTaskTime(task.optString("time", "12:00 PM"))
                        when {
                            hour in 5..11 -> morningTasks++
                            hour in 12..18 -> afternoonTasks++
                            else -> nightTasks++
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // Ignored
        }

        val snapshot = "total_tareas=$totalTasks; franjas{manana=$morningTasks, tarde=$afternoonTasks, noche=$nightTasks}"

        synchronized(agendaSnapshotCacheLock) {
            cachedAgendaRaw = rawAgenda
            cachedAgendaSnapshot = snapshot
            cachedAgendaSnapshotAtMs = now
        }
        return snapshot
    }

    private fun buildBehaviorSignalsForAi(settingsPrefs: SharedPreferences): String {
        val acceptCount = settingsPrefs.getInt(KEY_SCHEDULE_AI_ACCEPT_COUNT, 0)
        val dismissCount = settingsPrefs.getInt(KEY_SCHEDULE_AI_DISMISS_COUNT, 0)
        val openCount = settingsPrefs.getInt(KEY_SCHEDULE_AI_OPEN_COUNT, 0)
        val lastAction = settingsPrefs.getString(KEY_SCHEDULE_AI_LAST_ACTION, "ninguna")
        val lastMessage = settingsPrefs.getString(KEY_SCHEDULE_AI_LAST_MESSAGE, "")

        return "aprobadas=$acceptCount, descartadas=$dismissCount, aperturas_app=$openCount, ultima_accion=$lastAction, ultimo_mensaje='$lastMessage'"
    }

    private fun buildScheduleFallbackFromAgendaSnapshot(agendaSnapshot: String): String {
        return if (agendaSnapshot.contains("total_tareas=0")) {
            "Aún no tengo suficientes datos tuyos. Crea 2 o 3 tareas y al abrir de nuevo te daré una recomendación más precisa."
        } else if (agendaSnapshot.contains("manana=") && 
                   extractCount(agendaSnapshot, "manana=") >= extractCount(agendaSnapshot, "tarde=")) {
            "Tus tareas suelen concentrarse en la mañana. Reserva un bloque de foco de 45 min entre 9:00 AM y 11:00 AM para tu prioridad principal."
        } else {
            "Protege hoy un bloque sin interrupciones para la tarea más exigente y deja las tareas ligeras para después."
        }
    }

    private fun extractCount(raw: String, token: String): Int {
        val index = raw.indexOf(token)
        if (index < 0) return 0
        val start = index + token.length
        var end = start
        while (end < raw.length && raw[end].isDigit()) {
            end++
        }
        if (end <= start) return 0
        return try {
            raw.substring(start, end).toInt()
        } catch (e: Exception) {
            0
        }
    }

    private fun parseHourFromTaskTime(rawTime: String?): Int {
        if (rawTime.isNullOrBlank()) return 12

        val value = rawTime.trim().uppercase(Locale.US)
        return try {
            val isPm = value.contains("PM")
            val isAm = value.contains("AM")
            val numeric = value.replace("AM", "").replace("PM", "").trim()
            val split = numeric.split(":")
            var hour = split[0].trim().toInt()
            if (isPm && hour < 12) hour += 12
            if (isAm && hour == 12) hour = 0
            hour.coerceIn(0, 23)
        } catch (e: Exception) {
            12
        }
    }

    private fun isSmartSuggestionsEnabled(context: Context): Boolean {
        val settingsPrefs = context.getSharedPreferences(CloudSyncManager.PREFS_SETTINGS, Context.MODE_PRIVATE)
        return settingsPrefs.getBoolean(
            CloudSyncManager.KEY_SMART_SUGGESTIONS_ENABLED,
            settingsPrefs.getBoolean(CloudSyncManager.KEY_AI_SHIFT_ENABLED, true)
        )
    }
}
