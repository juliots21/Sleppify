package com.example.sleppify

import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.WorkerThread
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Service for Gemini-powered smart intelligence.
 * Optimized with Coroutines, Global Circuit Breaker, and Key Rotation.
 */
class GeminiIntelligenceService {

    companion object {
        private const val TAG = "GeminiService"

        private val API_KEYS = arrayOf(
            "AIzaSyCVaavBtTuJlj9aaHtFdBI_BXFUm1MiBXs",
            "AIzaSyD1x1MHXTsk7eWM76ZY8Z-FJGxGb5slp4o",
            "AIzaSyDRbfqY0ka_A06M1pcjB1TID-eQ1yUce_o",
            "AIzaSyDttUh3Lu8Iesh4DnE54Bs2WSWcdPK9EhM",
            "AIzaSyBiSKMB_R3CDMvTeM90ekaJepCRxCZ6Dvk"
        )

        private val MODELS = arrayOf(
            "gemini-2.0-flash",
            "gemini-2.0-flash-lite"
        )

        private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/"
        private val KEY_ROTATION_CURSOR = AtomicInteger(0)

        // CIRCUIT BREAKER STATE
        @Volatile private var serviceSuspendedUntilMs: Long = 0
        private val KEY_COOLDOWNS = ConcurrentHashMap<Int, Long>()
        private const val GLOBAL_COOLDOWN_MS = 5 * 60 * 1000L // 5 minutes
        private const val KEY_COOLDOWN_MS = 3 * 60 * 1000L    // 3 minutes

        // USER-INITIATED CALL BUDGET GATE
        // The only way to reach the network is via explicit user action
        // (pull-to-refresh or creating a task), which "arms" a budget.
        // Every network call consumes exactly one unit. No units left => no call.
        private val armedBudget = AtomicInteger(0)

        /**
         * Arms up to [n] future network calls. Callers must invoke this from an
         * explicit user action (pull-to-refresh or task creation). Also clears
         * the global suspension so the user can retry after quota cooldowns.
         */
        @JvmStatic
        fun armCalls(n: Int) {
            if (n <= 0) return
            armedBudget.updateAndGet { current -> (current + n).coerceAtMost(32) }
            serviceSuspendedUntilMs = 0
        }

        @JvmStatic
        fun clearArmedCalls() { armedBudget.set(0) }

        @JvmStatic
        fun armedCallsRemaining(): Int = armedBudget.get()

        private fun consumeArmedCall(): Boolean {
            while (true) {
                val c = armedBudget.get()
                if (c <= 0) return false
                if (armedBudget.compareAndSet(c, c - 1)) return true
            }
        }

        @JvmStatic
        fun isSuspended(): Boolean = System.currentTimeMillis() < serviceSuspendedUntilMs

        private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }

    // --- DATA MODELS ---

    data class TaskMetadata(val description: String, val category: String)
    data class ScheduleSuggestion(val message: String, val focusWindow: String, val microAction: String)

    // --- CALLBACK INTERFACES (JAVA COMPATIBILITY) ---

    interface GeminiCallback {
        fun onSuccess(suggestedDescription: String)
        fun onError(error: String)
    }

    interface TaskMetadataCallback {
        fun onSuccess(metadata: TaskMetadata)
        fun onError(error: String)
    }

    interface TodayAgendaSummaryCallback {
        fun onSuccess(message: String)
        fun onError(error: String)
    }

    interface ScheduleSuggestionCallback {
        fun onSuccess(suggestion: ScheduleSuggestion)
        fun onError(error: String)
    }

    // --- PUBLIC API ---

    /**
     * Java compat: Generate basic task description.
     */
    fun generateTaskMetadata(taskTitle: String, callback: GeminiCallback) {
        generateTaskMetadataWithCategory(taskTitle, object : TaskMetadataCallback {
            override fun onSuccess(metadata: TaskMetadata) {
                callback.onSuccess(metadata.description)
            }
            override fun onError(error: String) {
                callback.onError(error)
            }
        })
    }

    /**
     * Kotlin & Java: Generate full task metadata (description + category).
     */
    fun generateTaskMetadataWithCategory(taskTitle: String, callback: TaskMetadataCallback) {
        val promptText = """
            Estoy creando una tarea en mi agenda con el título: '$taskTitle'. 
            Necesito una descripcion corta y util en espanol (1 oracion) para detallar esta tarea, 
            y tambien una categoria breve y especifica creada por la IA segun el contexto de la tarea. 
            La categoria DEBE tener exactamente una sola palabra. 
            NO uses categorias predefinidas forzadas ni listas cerradas. 
            NO incluyas hora ni formato horario. 
            Responde UNICAMENTE en formato JSON valido asi: 
            {"description":"Preparar la mochila y revisar materiales para mañana.","category":"Estudio"}
        """.trimIndent()

        executePromptWithRetries(
            promptText,
            "task metadata",
            "Error al procesar respuesta de IA",
            { raw -> parseTaskMetadata(taskTitle, raw) },
            { metadata -> callback.onSuccess(metadata) },
            { err -> callback.onError(err) }
        )
    }

    /**
     * Kotlin & Java: Agenda summary for notifications.
     */
    fun generateTodayAgendaSummary(profileName: String?, todayAgendaSnapshot: String?, callback: TodayAgendaSummaryCallback) {
        val safeName = if (profileName.isNullOrBlank()) "usuario" else profileName.trim()
        val safeSnapshot = if (todayAgendaSnapshot.isNullOrBlank()) "Sin tareas para hoy." else todayAgendaSnapshot

        val promptText = """
            Eres un asistente personal de productividad. 
            Debes crear un resumen breve para una notificacion Android del usuario $safeName. 
            Usa solo esta informacion real de tareas de hoy: $safeSnapshot. 
            Reglas: maximo 2 oraciones, tono claro y accionable, sin inventar tareas. 
            Si hay varias tareas, menciona 1 o 2 ejemplos por nombre. 
            Responde UNICAMENTE JSON valido con este esquema exacto: {"message":"..."}.
        """.trimIndent()

        executePromptWithRetries(
            promptText,
            "agenda summary",
            "Error al procesar resumen de agenda",
            { raw ->
                val json = JSONObject(cleanJsonResponse(raw))
                json.optString("message", "Hoy tienes tareas interesantes planificadas.")
            },
            { msg -> callback.onSuccess(msg) },
            { err -> callback.onError(err) }
        )
    }

    /**
     * Kotlin & Java: Schedule intelligent suggestion.
     */
    fun generateScheduleSuggestion(
        profileName: String?,
        agendaSnapshot: String?,
        behaviorSignals: String?,
        callback: ScheduleSuggestionCallback
    ) {
        val safeName = if (profileName.isNullOrBlank()) "usuario" else profileName.trim()
        val safeAgenda = if (agendaSnapshot.isNullOrBlank()) "No hay tareas registradas." else agendaSnapshot
        val safeSignals = if (behaviorSignals.isNullOrBlank()) "Sin feedback historico." else behaviorSignals

        val promptText = """
            Eres un estratega de productividad personal. 
            Genera una sola recomendacion realmente util para la agenda de $safeName. 
            Usa SOLO este historial real: $safeAgenda. 
            Considera estas senales previas: $safeSignals. 
            Objetivo: sugerir la accion de mayor impacto para hoy y reducir friccion. 
            Reglas: 
            1) message debe mencionar al menos 1 tarea o patron concreto detectado. 
            2) maximo 2 oraciones en message. 
            3) microAction debe ser un solo paso ejecutable (maximo 12 palabras). 
            Responde UNICAMENTE JSON valido con este esquema exacto: 
            {"message":"...","focusWindow":"9:00 AM-11:00 AM","microAction":"..."}.
        """.trimIndent()

        executePromptWithRetries(
            promptText,
            "schedule suggestion",
            "Error al procesar sugerencia",
            { raw ->
                val json = JSONObject(cleanJsonResponse(raw))
                ScheduleSuggestion(
                    json.optString("message", "Organiza 1 tarea prioritaria."),
                    json.optString("focusWindow", "9:00 AM-11:00 AM"),
                    json.optString("microAction", "Elige una tarea clave.")
                )
            },
            { suggestion -> callback.onSuccess(suggestion) },
            { err -> callback.onError(err) }
        )
    }

    // --- PRIVATE IMPLEMENTATION ---

    private fun <T> executePromptWithRetries(
        promptText: String,
        label: String,
        parseErr: String,
        parser: (String) -> T,
        onSuccess: (T) -> Unit,
        onError: (String) -> Unit
    ) {
        serviceScope.launch {
            if (!consumeArmedCall()) {
                Log.w(TAG, "AI call blocked (no armed budget). Skipping $label.")
                withContext(Dispatchers.Main) { onError("IA solo se actualiza al crear una tarea o al hacer pull to refresh.") }
                return@launch
            }

            if (isSuspended()) {
                Log.w(TAG, "AI Service suspended. Skipping $label.")
                withContext(Dispatchers.Main) { onError("IA en pausa temporal (Cuota agotada)") }
                return@launch
            }

            val result = withContext(Dispatchers.IO) {
                performRotationRequest(promptText, label)
            }

            withContext(Dispatchers.Main) {
                when (result) {
                    is RequestResponse.Success -> {
                        try {
                            val parsed = parser(result.text)
                            onSuccess(parsed)
                        } catch (e: Exception) {
                            Log.e(TAG, "$label parse error: ${e.message}")
                            onError(parseErr)
                        }
                    }
                    is RequestResponse.Error -> {
                        onError(result.message)
                    }
                }
            }
        }
    }

    @WorkerThread
    private fun performRotationRequest(promptText: String, label: String): RequestResponse {
        var lastHttpError = "Sin respuesta del servidor"
        val startIdx = nextRotatingIdx()
        val now = System.currentTimeMillis()
        var consecutiveConnErrors = 0

        // Rotation is user-gated upstream (armCalls). Try every available key.
        var attempts = 0
        for (model in MODELS) {
            for (offset in API_KEYS.indices) {
                val keyIdx = (startIdx + offset) % API_KEYS.size

                // Key cooldown check
                if ((KEY_COOLDOWNS[keyIdx] ?: 0) > now) continue

                attempts++
                val apiKey = API_KEYS[keyIdx]
                Log.d(TAG, "Trying $label [model=$model, key=$keyIdx, attempt=$attempts]...")

                val result = tryOneRequest(model, apiKey, promptText)
                if (result.isSuccess) {
                    return RequestResponse.Success(result.text ?: "")
                } else {
                    if (result.code == 429 || result.code == 503) {
                        Log.e(TAG, "Rate limit hit on key $keyIdx. Suspending.")
                        serviceSuspendedUntilMs = now + GLOBAL_COOLDOWN_MS
                        KEY_COOLDOWNS[keyIdx] = now + KEY_COOLDOWN_MS
                        return RequestResponse.Error("Cuota de IA excedida. Reintentando en breve.")
                    }
                    
                    if (result.code <= 0) {
                        consecutiveConnErrors++
                        if (consecutiveConnErrors >= 2) {
                            Log.e(TAG, "Too many consecutive connection errors. Aborting rotation.")
                            return RequestResponse.Error("Error de conexión con el servicio de IA.")
                        }
                    } else {
                        consecutiveConnErrors = 0
                    }
                    
                    if (result.code > 0) lastHttpError = "Error HTTP ${result.code}"
                }
            }
        }
        return RequestResponse.Error(lastHttpError)
    }

    private fun tryOneRequest(model: String, apiKey: String, promptText: String): RawResult {
        var conn: HttpURLConnection? = null
        return try {
            val url = URL("${BASE_URL}${model}:generateContent?key=$apiKey")
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                doOutput = true
                connectTimeout = 4000
                readTimeout = 8000
            }

            val payload = JSONObject().apply {
                put("contents", JSONArray().put(JSONObject().apply {
                    put("parts", JSONArray().put(JSONObject().apply {
                        put("text", promptText)
                    }))
                }))
                put("generationConfig", JSONObject().apply {
                    put("responseMimeType", "application/json")
                })
            }

            conn.outputStream.use { it.write(payload.toString().toByteArray()) }

            val code = conn.responseCode
            if (code == 200) {
                val text = conn.inputStream.bufferedReader().use { it.readText() }
                val root = JSONObject(text)
                val responseText = extractText(root)
                RawResult(responseText, 200, true)
            } else {
                RawResult(null, code, false)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Request exception: ${e.message}")
            RawResult(null, -1, false)
        } finally {
            conn?.disconnect()
        }
    }

    private fun extractText(root: JSONObject): String {
        return root.optJSONArray("candidates")?.optJSONObject(0)
            ?.optJSONObject("content")?.optJSONArray("parts")
            ?.optJSONObject(0)?.optString("text", "") ?: ""
    }

    private fun cleanJsonResponse(raw: String): String {
        return raw.replace("```json", "", true)
            .replace("```JSON", "", true)
            .replace("```", "")
            .trim()
    }

    private fun parseTaskMetadata(title: String, raw: String): TaskMetadata {
        val clean = cleanJsonResponse(raw)
        var desc = ""
        var cat = ""

        val start = clean.indexOf('{')
        val end = clean.lastIndexOf('}')
        if (start >= 0 && end > start) {
            try {
                val json = JSONObject(clean.substring(start, end + 1))
                desc = firstVal(json, "description", "descripcion", "details")
                cat = firstVal(json, "category", "categoria")
            } catch (ignored: Exception) {}
        }

        if (desc.isEmpty()) desc = "Organizar y completar: ${title.trim()}."
        if (cat.isEmpty()) cat = "Pendiente"

        val normalizedCat = cat.filter { it.isLetterOrDigit() || it == '_' || it == '-' }
            .lowercase().replaceFirstChar { it.uppercase() }
            .ifEmpty { "Organizacion" }

        return TaskMetadata(desc, normalizedCat)
    }

    private fun firstVal(json: JSONObject, vararg keys: String): String {
        for (k in keys) {
            val v = json.optString(k, "").trim()
            if (v.isNotEmpty()) return v
        }
        return ""
    }

    private fun nextRotatingIdx(): Int {
        val raw = KEY_ROTATION_CURSOR.getAndIncrement()
        return Math.floorMod(raw, API_KEYS.size.coerceAtLeast(1))
    }

    // --- REUSEABLE INTERNAL TYPES ---

    private sealed class RequestResponse {
        data class Success(val text: String) : RequestResponse()
        data class Error(val message: String) : RequestResponse()
    }

    private data class RawResult(val text: String?, val code: Int, val isSuccess: Boolean)
}
