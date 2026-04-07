package com.example.sleppify;

import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class GeminiIntelligenceService {

    private static final String TAG = "GeminiService";

    private static final String[] API_KEYS = {
            "AIzaSyCVaavBtTuJlj9aaHtFdBI_BXFUm1MiBXs",
            "AIzaSyD1x1MHXTsk7eWM76ZY8Z-FJGxGb5slp4o",
            "AIzaSyDRbfqY0ka_A06M1pcjB1TID-eQ1yUce_o",
            "AIzaSyDttUh3Lu8Iesh4DnE54Bs2WSWcdPK9EhM",
            "AIzaSyBiSKMB_R3CDMvTeM90ekaJepCRxCZ6Dvk"
    };

    private static final String[] MODELS = {
            "gemini-2.0-flash",
            "gemini-2.0-flash-lite"
    };

    private static final String BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/";
    private static final AtomicInteger KEY_ROTATION_CURSOR = new AtomicInteger(0);
        private static final String[] WAVELET_9_BAND_LABELS = {
            "62Hz", "125Hz", "250Hz", "500Hz", "1kHz", "2kHz", "4kHz", "8kHz", "16kHz"
        };
        private static final int EQ_BAND_COUNT = WAVELET_9_BAND_LABELS.length;

    // Shared executor avoids creating one thread pool per service instance.
    private static final ExecutorService SHARED_EXECUTOR = Executors.newFixedThreadPool(2);
    private final ExecutorService executor = SHARED_EXECUTOR;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private interface ResponseParser<T> {
        @NonNull
        T parse(@NonNull String rawText) throws Exception;
    }

    private interface ResponseSuccess<T> {
        void onSuccess(@NonNull T value);
    }

    private interface ResponseError {
        void onError(@NonNull String error);
    }

    public interface GeminiCallback {
        void onSuccess(String suggestedDescription);
        void onError(String error);
    }

    public interface TaskMetadataCallback {
        void onSuccess(TaskMetadata metadata);
        void onError(String error);
    }

    public interface AppsSuggestionCallback {
        void onSuccess(String message);
        void onError(String error);
    }

    public interface TodayAgendaSummaryCallback {
        void onSuccess(String message);
        void onError(String error);
    }

    public interface EqSuggestionCallback {
        void onSuccess(EqSuggestion suggestion);
        void onError(String error);
    }

    public interface ScheduleSuggestionCallback {
        void onSuccess(ScheduleSuggestion suggestion);
        void onError(String error);
    }

    public static class EqSuggestion {
        public final String message;
        public final float[] bands;
        public final float bassDb;
        public final float bassFrequencyHz;

        public EqSuggestion(String message, float[] bands, float bassDb, float bassFrequencyHz) {
            this.message = message;
            this.bands = bands;
            this.bassDb = bassDb;
            this.bassFrequencyHz = bassFrequencyHz;
        }
    }

    public static class ScheduleSuggestion {
        public final String message;
        public final String focusWindow;
        public final String microAction;

        public ScheduleSuggestion(String message, String focusWindow, String microAction) {
            this.message = message;
            this.focusWindow = focusWindow;
            this.microAction = microAction;
        }
    }

    public static class TaskMetadata {
        public final String description;
        public final String category;

        public TaskMetadata(String description, String category) {
            this.description = description;
            this.category = category;
        }
    }

    public void generateTaskMetadata(String taskTitle, GeminiCallback callback) {
        generateTaskMetadataWithCategory(taskTitle, new TaskMetadataCallback() {
            @Override
            public void onSuccess(TaskMetadata metadata) {
                callback.onSuccess(metadata.description);
            }

            @Override
            public void onError(String error) {
                callback.onError(error);
            }
        });
    }

    public void generateTaskMetadataWithCategory(String taskTitle, TaskMetadataCallback callback) {
        String promptText = "Estoy creando una tarea en mi agenda con el título: '" + taskTitle + "'. " +
                "Necesito una descripcion corta y util en espanol (1 oracion) para detallar esta tarea, " +
            "y tambien una categoria breve y especifica creada por la IA segun el contexto de la tarea. " +
            "La categoria DEBE tener exactamente una sola palabra. " +
                "NO uses categorias predefinidas forzadas ni listas cerradas. " +
                "NO incluyas hora ni formato horario. " +
                "Responde UNICAMENTE en formato JSON valido asi: " +
            "{\"description\":\"Preparar la mochila y revisar materiales para manana.\",\"category\":\"Estudio\"}";

        executePromptWithRetries(
                promptText,
                "task metadata",
                "Error al procesar respuesta de IA",
                rawText -> parseTaskMetadataResponse(taskTitle, rawText),
                callback::onSuccess,
                callback::onError
        );
    }

    @NonNull
    private TaskMetadata parseTaskMetadataResponse(@NonNull String taskTitle, @NonNull String rawText) throws Exception {
        String cleaned = rawText
                .replace("```json", "")
                .replace("```JSON", "")
                .replace("```", "")
                .trim();

        String description = "";
        String rawCategory = "";

        int jsonStart = cleaned.indexOf('{');
        int jsonEnd = cleaned.lastIndexOf('}');
        if (jsonStart >= 0 && jsonEnd > jsonStart) {
            try {
                JSONObject resultJson = new JSONObject(cleaned.substring(jsonStart, jsonEnd + 1));
                description = firstNonEmptyJsonValue(resultJson, "description", "descripcion", "details", "detalle");
                rawCategory = firstNonEmptyJsonValue(resultJson, "category", "categoria", "type", "label");
            } catch (Exception ignored) {
                // If JSON parsing fails, continue with labeled text extraction fallback.
            }
        }

        if (description.isEmpty()) {
            description = extractLabeledMetadataValue(cleaned, "description", "descripcion", "details", "detalle");
        }
        if (TextUtils.isEmpty(rawCategory)) {
            rawCategory = extractLabeledMetadataValue(cleaned, "category", "categoria", "type", "label");
        }

        if (description.isEmpty()) {
            description = buildFallbackTaskDescription(taskTitle);
        }

        String category = normalizeAiTaskCategory(rawCategory);
        if (category.isEmpty() || isGenericTaskCategory(category)) {
            category = normalizeAiTaskCategory(buildFallbackTaskCategory(taskTitle));
        }
        if (category.isEmpty()) {
            category = "Pendiente";
        }
        return new TaskMetadata(description, category);
    }

    private boolean isGenericTaskCategory(@NonNull String category) {
        String normalized = category.trim().toLowerCase(Locale.US);
        return "otros".equals(normalized)
                || "otro".equals(normalized)
                || "general".equals(normalized)
                || "organizacion".equals(normalized)
                || "organización".equals(normalized)
                || "varios".equals(normalized)
                || "misc".equals(normalized);
    }

    @NonNull
    private String firstNonEmptyJsonValue(@NonNull JSONObject jsonObject, @NonNull String... keys) {
        for (String key : keys) {
            String value = jsonObject.optString(key, "").trim();
            if (!value.isEmpty()) {
                return value;
            }
        }
        return "";
    }

    @NonNull
    private String extractLabeledMetadataValue(@NonNull String rawText, @NonNull String... labels) {
        String[] lines = rawText.split("\\r?\\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }

            String lowerLine = trimmed.toLowerCase(Locale.US);
            for (String label : labels) {
                String lowerLabel = label.toLowerCase(Locale.US);
                int labelIndex = lowerLine.indexOf(lowerLabel);
                if (labelIndex < 0) {
                    continue;
                }

                int separator = trimmed.indexOf(':', labelIndex + label.length());
                if (separator < 0) {
                    separator = trimmed.indexOf('=', labelIndex + label.length());
                }
                if (separator < 0) {
                    continue;
                }

                String value = trimmed.substring(separator + 1).trim();
                value = value.replaceAll("^[\\\"'`\\-]+|[\\\"'`]+$", "").trim();
                if (!value.isEmpty()) {
                    return value;
                }
            }
        }
        return "";
    }

    @NonNull
    private String buildFallbackTaskDescription(@Nullable String taskTitle) {
        String normalizedTitle = taskTitle == null ? "" : taskTitle.trim();
        normalizedTitle = normalizedTitle.replaceAll("[\\.;:,]+$", "").trim();
        if (normalizedTitle.isEmpty()) {
            return "Completar esta tarea pendiente con enfoque y continuidad.";
        }
        return "Organizar y completar: " + normalizedTitle + ".";
    }

    @NonNull
    private String buildFallbackTaskCategory(@Nullable String taskTitle) {
        String lowerTitle = taskTitle == null ? "" : taskTitle.toLowerCase(Locale.US);
        if (containsAnyKeyword(lowerTitle, "estudi", "colegio", "clase", "examen", "curso")) {
            return "Estudio";
        }
        if (containsAnyKeyword(lowerTitle, "trabaj", "proyecto", "reunion", "cliente", "entrega")) {
            return "Trabajo";
        }
        if (containsAnyKeyword(lowerTitle, "compr", "mercado", "pago", "factura", "tramite")) {
            return "Gestiones";
        }
        if (containsAnyKeyword(lowerTitle, "gimnas", "entren", "salud", "medico", "caminar")) {
            return "Bienestar";
        }
        return "Organizacion";
    }

    private boolean containsAnyKeyword(@NonNull String source, @NonNull String... keywords) {
        for (String keyword : keywords) {
            if (source.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    @NonNull
    private String normalizeAiTaskCategory(@Nullable String rawCategory) {
        return normalizeAiTaskCategoryValue(rawCategory);
    }

    @NonNull
    static String normalizeAiTaskCategoryValue(@Nullable String rawCategory) {
        if (rawCategory == null) {
            return "";
        }

        String value = rawCategory
                .replace('\n', ' ')
                .replace('\r', ' ')
                .trim();
        if (value.isEmpty()) {
            return "";
        }

        value = value.replaceAll("\\s+", " ");
        value = value.replaceAll("^[\\\"'`]+|[\\\"'`]+$", "").trim();
        value = value.replaceAll("[\\.;:,]+$", "").trim();

        String[] tokens = value.split("\\s+");
        value = tokens.length > 0 ? tokens[0] : value;
        value = value.replaceAll("[^\\p{L}\\p{N}_-]", "").trim();
        if (value.isEmpty()) {
            return "";
        }

        String lower = value.toLowerCase(Locale.US);
        value = Character.toUpperCase(lower.charAt(0)) + lower.substring(1);

        if (value.length() > 24) {
            value = value.substring(0, 24).trim();
        }

        return value;
    }

    public void generateAppsSuggestion(
            String profileName,
            String deviceContext,
            String behaviorSignals,
            AppsSuggestionCallback callback
    ) {
        String safeName = (profileName == null || profileName.trim().isEmpty()) ? "usuario" : profileName.trim();
        String safeContext = (deviceContext == null || deviceContext.trim().isEmpty())
                ? "Sin telemetria disponible."
                : deviceContext;
        String safeSignals = (behaviorSignals == null || behaviorSignals.trim().isEmpty())
                ? "Sin feedback previo."
                : behaviorSignals;

        String promptText = "Eres un asistente experto en optimizacion de Android que aprende del usuario. " +
                "Genera una recomendacion breve y accionable para el modulo Apps de " + safeName + ". " +
                "Debes basarte EXCLUSIVAMENTE en este contexto real del dispositivo: " + safeContext + ". " +
                "Tambien considera este historial de feedback: " + safeSignals + ". " +
                "Reglas: maximo 2 oraciones, en espanol natural, sin texto generico, sin inventar apps no presentes. " +
                "Si hay apps detectadas, menciona al menos 1 por nombre. " +
                "Responde UNICAMENTE JSON valido con este esquema exacto: {\"message\":\"...\"}.";

        executePromptWithRetries(
                promptText,
                "apps suggestion",
                "Error al procesar sugerencia de apps",
                this::parseAppsSuggestion,
                callback::onSuccess,
                callback::onError
        );
    }

    public void generateTodayAgendaSummary(
            String profileName,
            String todayAgendaSnapshot,
            TodayAgendaSummaryCallback callback
    ) {
        String safeName = (profileName == null || profileName.trim().isEmpty()) ? "usuario" : profileName.trim();
        String safeSnapshot = (todayAgendaSnapshot == null || todayAgendaSnapshot.trim().isEmpty())
                ? "Sin tareas para hoy."
                : todayAgendaSnapshot;

        String promptText = "Eres un asistente personal de productividad. " +
                "Debes crear un resumen breve para una notificacion Android del usuario " + safeName + ". " +
                "Usa solo esta informacion real de tareas de hoy: " + safeSnapshot + ". " +
                "Reglas: maximo 2 oraciones, tono claro y accionable, sin inventar tareas. " +
                "Si hay varias tareas, menciona 1 o 2 ejemplos por nombre. " +
                "Responde UNICAMENTE JSON valido con este esquema exacto: {\"message\":\"...\"}.";

        executePromptWithRetries(
                promptText,
                "agenda notification summary",
                "Error al procesar resumen de agenda",
                this::parseTodayAgendaSummary,
                callback::onSuccess,
                callback::onError
        );
    }

    public void generateEqSuggestion(
            String outputDevice,
            String phoneModel,
            float[] currentBands,
            float currentBassDb,
            float currentBassFrequencyHz,
            EqSuggestionCallback callback
    ) {
        float[] bands = new float[EQ_BAND_COUNT];
        if (currentBands != null) {
            for (int i = 0; i < bands.length && i < currentBands.length; i++) {
                bands[i] = currentBands[i];
            }
        }

        String bandState = buildBandStateForPrompt(bands);
        EqProfileDiagnostic diagnostic = analyzeEqProfile(bands, currentBassDb, currentBassFrequencyHz);
        String diagnosticSummary = buildEqDiagnosticSummary(diagnostic);

        String promptText = "Eres un ingeniero de audio experto en ecualizacion musical. " +
                "Analiza configuracion actual para el dispositivo de audio '" + outputDevice + "' y para el telefono '" + phoneModel + "'. " +
                "Usa conocimiento tecnico publico (incluyendo perfiles sonoros comunes documentados en internet) para este modelo cuando sea posible. " +
                "Estado actual: bandas en dB [" + bandState + "], bassDb=" + currentBassDb +
                ", bassFrequencyHz=" + currentBassFrequencyHz + ". " +
            "Diagnostico preliminar local: " + diagnosticSummary + ". " +
            "Genera una sugerencia profesional en espanol con tono consultivo, nunca en modo orden. " +
            "Debe sonar como propuesta, por ejemplo: Te gustaria probar este ajuste... o Podriamos probar.... " +
            "Incluye al menos 2 acciones concretas con frecuencia y direccion. " +
            "Evita mensajes vagos como 'mas claridad' o 'mejor sonido'. " +
                "Responde UNICAMENTE JSON valido con este esquema exacto: " +
                "{\"message\":\"texto corto\",\"bands\":[0,0,0,0,0,0,0,0,0],\"bassDb\":0.0,\"bassFrequencyHz\":" + AudioEffectsService.BASS_FREQUENCY_DEFAULT_HZ + "}. " +
                "Reglas: bands debe tener 9 valores entre " + AudioEffectsService.EQ_GAIN_MIN_DB + " y " + AudioEffectsService.EQ_GAIN_MAX_DB + "; bassDb entre 0 y 5; bassFrequencyHz entre " + AudioEffectsService.BASS_FREQUENCY_MIN_HZ + " y " + AudioEffectsService.BASS_FREQUENCY_MAX_HZ + ".";

        executePromptWithRetries(
                promptText,
                "EQ suggestion",
                "Error al procesar sugerencia de IA",
                this::parseEqSuggestion,
                suggestion -> {
                    EqSuggestion dynamicSuggestion = enforceDynamicEqSuggestion(
                        bands,
                        suggestion,
                        currentBassDb,
                        currentBassFrequencyHz,
                        diagnostic
                    );
                    String finalMessage = ensureSpecificEqMessage(
                        dynamicSuggestion.message,
                            outputDevice,
                            bands,
                        dynamicSuggestion.bands,
                            currentBassDb,
                        dynamicSuggestion.bassDb,
                            currentBassFrequencyHz,
                        dynamicSuggestion.bassFrequencyHz,
                        diagnostic
                    );
                    EqSuggestion finalSuggestion = new EqSuggestion(
                            finalMessage,
                        dynamicSuggestion.bands,
                        dynamicSuggestion.bassDb,
                        dynamicSuggestion.bassFrequencyHz
                    );
                    callback.onSuccess(finalSuggestion);
                },
                callback::onError
        );
    }

    public void generateScheduleSuggestion(
            String profileName,
            String agendaSnapshot,
            String behaviorSignals,
            ScheduleSuggestionCallback callback
    ) {
        String safeName = (profileName == null || profileName.trim().isEmpty()) ? "usuario" : profileName.trim();
        String safeAgenda = (agendaSnapshot == null || agendaSnapshot.trim().isEmpty())
                ? "No hay tareas registradas en agenda."
                : agendaSnapshot;
        String safeSignals = (behaviorSignals == null || behaviorSignals.trim().isEmpty())
                ? "Sin feedback historico."
                : behaviorSignals;

        String promptText = "Eres un estratega de productividad personal. " +
            "Genera una sola recomendacion realmente util para la agenda de " + safeName + ". " +
            "Usa SOLO este historial real: " + safeAgenda + ". " +
            "Considera estas senales previas: " + safeSignals + ". " +
            "Objetivo: sugerir la accion de mayor impacto para hoy y reducir friccion. " +
            "Reglas estrictas: " +
            "1) message debe mencionar al menos 1 tarea o patron concreto detectado. " +
            "2) message debe incluir una accion clara con hora AM/PM o bloque horario cuando aplique. " +
            "3) evita frases motivacionales genericas o vagas. " +
            "4) microAction debe ser un solo paso ejecutable (maximo 12 palabras) y empezar con verbo. " +
            "5) maximo 2 oraciones en message. " +
            "NO uses la palabra franja. " +
            "Responde UNICAMENTE JSON valido con este esquema exacto: " +
            "{\"message\":\"...\",\"focusWindow\":\"9:00 AM-11:00 AM\",\"microAction\":\"...\"}.";

        executePromptWithRetries(
                promptText,
                "schedule suggestion",
                "Error al procesar sugerencia de agenda",
                this::parseScheduleSuggestion,
                suggestion -> callback.onSuccess(sanitizeScheduleSuggestion(suggestion)),
                callback::onError
        );
    }

    private <T> void executePromptWithRetries(
            @NonNull String promptText,
            @NonNull String requestLabel,
            @NonNull String parseErrorMessage,
            @NonNull ResponseParser<T> parser,
            @NonNull ResponseSuccess<T> success,
            @NonNull ResponseError error
    ) {
        executor.execute(() -> {
            String lastError = "Sin respuesta del servidor";
            int startKeyIndex = nextRotatingStartKeyIndex();

            for (String model : MODELS) {
                for (int keyOffset = 0; keyOffset < API_KEYS.length; keyOffset++) {
                    int keyIdx = (startKeyIndex + keyOffset) % API_KEYS.length;
                    String apiKey = API_KEYS[keyIdx];
                    Log.d(TAG, "Trying " + requestLabel + " model=" + model + " key[" + keyIdx + "]=" + safeKeyPrefix(apiKey) + "...");

                    RequestResult result = tryRequest(model, apiKey, promptText);
                    if (result.text != null) {
                        try {
                            T parsed = parser.parse(result.text);
                            mainHandler.post(() -> success.onSuccess(parsed));
                            return;
                        } catch (Exception parseEx) {
                            Log.w(TAG, requestLabel + " parse error: " + parseEx.getMessage() + " | Raw: " + result.text);
                            lastError = parseErrorMessage;
                        }
                    } else {
                        Log.w(TAG, requestLabel + " request failed: HTTP " + result.httpCode + " model=" + model + " key[" + keyIdx + "]");
                        if (result.httpCode != 429 && result.httpCode != 503) {
                            lastError = "Error HTTP " + result.httpCode;
                        }
                    }
                }
            }

            String finalError = lastError;
            mainHandler.post(() -> error.onError(finalError));
        });
    }

    private int nextRotatingStartKeyIndex() {
        if (API_KEYS.length == 0) {
            return 0;
        }
        int raw = KEY_ROTATION_CURSOR.getAndIncrement();
        return Math.floorMod(raw, API_KEYS.length);
    }

    @NonNull
    private String safeKeyPrefix(@Nullable String apiKey) {
        if (TextUtils.isEmpty(apiKey)) {
            return "n/a";
        }
        int maxLength = Math.min(12, apiKey.length());
        return apiKey.substring(0, maxLength);
    }

    private ScheduleSuggestion parseScheduleSuggestion(String rawText) throws Exception {
        String cleaned = rawText
                .replace("```json", "")
                .replace("```JSON", "")
                .replace("```", "")
                .trim();

        int jsonStart = cleaned.indexOf('{');
        int jsonEnd = cleaned.lastIndexOf('}');
        if (jsonStart < 0 || jsonEnd <= jsonStart) {
            throw new IllegalArgumentException("No se encontro JSON en sugerencia de agenda");
        }

        JSONObject json = new JSONObject(cleaned.substring(jsonStart, jsonEnd + 1));
        String message = json.optString("message", "Organiza 1 tarea prioritaria para tu mejor momento del dia.").trim();
        String focusWindow = json.optString("focusWindow", "9:00 AM-11:00 AM").trim();
        String microAction = json.optString("microAction", "Elige una tarea clave y bloquela en tu agenda.").trim();

        if (message.isEmpty()) {
            message = "Organiza 1 tarea prioritaria para tu mejor momento del dia.";
        }
        if (focusWindow.isEmpty()) {
            focusWindow = "9:00 AM-11:00 AM";
        }
        if (microAction.isEmpty()) {
            microAction = "Elige una tarea clave y bloquela en tu agenda.";
        }
        return new ScheduleSuggestion(message, focusWindow, microAction);
    }

    @NonNull
    private String parseAppsSuggestion(String rawText) throws Exception {
        String cleaned = rawText
                .replace("```json", "")
                .replace("```JSON", "")
                .replace("```", "")
                .trim();

        int jsonStart = cleaned.indexOf('{');
        int jsonEnd = cleaned.lastIndexOf('}');
        if (jsonStart < 0 || jsonEnd <= jsonStart) {
            throw new IllegalArgumentException("No se encontro JSON en sugerencia de apps");
        }

        JSONObject json = new JSONObject(cleaned.substring(jsonStart, jsonEnd + 1));
        String message = json.optString("message", "").trim();
        if (message.isEmpty()) {
            throw new IllegalArgumentException("Respuesta de apps sin message");
        }
        return message;
    }

    @NonNull
    private String parseTodayAgendaSummary(String rawText) throws Exception {
        String cleaned = rawText
                .replace("```json", "")
                .replace("```JSON", "")
                .replace("```", "")
                .trim();

        int jsonStart = cleaned.indexOf('{');
        int jsonEnd = cleaned.lastIndexOf('}');
        if (jsonStart < 0 || jsonEnd <= jsonStart) {
            throw new IllegalArgumentException("No se encontro JSON en resumen de agenda");
        }

        JSONObject json = new JSONObject(cleaned.substring(jsonStart, jsonEnd + 1));
        String message = json.optString("message", "").trim();
        if (message.isEmpty()) {
            throw new IllegalArgumentException("Resumen de agenda sin message");
        }
        return message;
    }

    private ScheduleSuggestion sanitizeScheduleSuggestion(ScheduleSuggestion suggestion) {
        String message = suggestion.message == null ? "" : suggestion.message.trim();
        String focusWindow = normalizeFocusWindowToAmPm(suggestion.focusWindow);
        String microAction = suggestion.microAction == null ? "" : suggestion.microAction.trim();

        if (message.isEmpty()) {
            message = "Te recomiendo reservar un bloque de foco hoy para tu tarea mas importante.";
        }

        if (focusWindow.isEmpty()) {
            focusWindow = "9:00 AM - 11:00 AM";
        }

        message = normalizeScheduleMessageWording(message);
        message = convert24HourTimesToAmPm(message);

        String lower = message.toLowerCase(Locale.US);
        if (isGenericScheduleMessage(lower)) {
            message = "Prioriza una tarea exigente y bloqueala en " + focusWindow
                    + " para reducir cambios de contexto.";
            lower = message.toLowerCase(Locale.US);
        }

        boolean hasAction = lower.contains("agenda") || lower.contains("programa") || lower.contains("mueve")
                || lower.contains("reserva") || lower.contains("bloque") || lower.contains("prioriza");
        boolean hasTimeHint = lower.contains("am") || lower.contains("pm") || lower.contains(":") || lower.contains("hora");

        if (!hasAction) {
            message = message + " Programa ese bloque hoy para convertirlo en habito.";
        }
        if (!hasTimeHint && !focusWindow.isEmpty()) {
            message = message + " Momento recomendado: " + focusWindow + ".";
        }

        if (microAction.isEmpty()) {
            microAction = "Reserva un bloque de 45 minutos para la tarea clave de hoy.";
        }

        microAction = normalizeScheduleMessageWording(convert24HourTimesToAmPm(microAction));
        if (isGenericScheduleMicroAction(microAction.toLowerCase(Locale.US))) {
            microAction = "Bloquea 45 minutos para tu tarea clave en " + focusWindow + ".";
        }

        return new ScheduleSuggestion(message, focusWindow, microAction);
    }

    private boolean isGenericScheduleMessage(@NonNull String lowerText) {
        return lowerText.contains("organiza tu dia")
                || lowerText.contains("mantener consistencia")
                || lowerText.contains("mejorar productividad")
                || lowerText.contains("ser mas productivo")
                || lowerText.contains("prioriza tus tareas")
                || lowerText.contains("enfocate hoy")
                || lowerText.contains("sin datos");
    }

    private boolean isGenericScheduleMicroAction(@NonNull String lowerText) {
        return lowerText.length() < 8
                || lowerText.contains("hazlo")
                || lowerText.contains("empieza hoy")
                || lowerText.contains("se constante")
                || lowerText.contains("mejora tu dia");
    }

    @NonNull
    private String normalizeScheduleMessageWording(@NonNull String text) {
        String normalized = text;
        normalized = normalized.replaceAll("(?i)franja\\s+horaria", "momento del dia");
        normalized = normalized.replaceAll("(?i)franja", "momento");
        return normalized;
    }

    @NonNull
    private String convert24HourTimesToAmPm(@NonNull String text) {
        Pattern timePattern = Pattern.compile("\\b([01]?\\d|2[0-3]):([0-5]\\d)\\b");
        Matcher matcher = timePattern.matcher(text);
        StringBuffer buffer = new StringBuffer();

        while (matcher.find()) {
            int hour24;
            int minute;
            try {
                hour24 = Integer.parseInt(matcher.group(1));
                minute = Integer.parseInt(matcher.group(2));
            } catch (Exception ignored) {
                continue;
            }

            String replacement = formatHourMinuteAmPm(hour24, minute);
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    @NonNull
    private String normalizeFocusWindowToAmPm(@Nullable String rawFocusWindow) {
        if (TextUtils.isEmpty(rawFocusWindow)) {
            return "9:00 AM - 11:00 AM";
        }

        String cleaned = rawFocusWindow.trim()
                .replace("a. m.", "AM")
                .replace("p. m.", "PM")
                .replace("a.m.", "AM")
                .replace("p.m.", "PM")
                .replace("A.M.", "AM")
                .replace("P.M.", "PM")
                .replace("–", "-")
                .replace("—", "-");

        List<TimeToken> tokens = new ArrayList<>();
        Pattern tokenPattern = Pattern.compile("\\b\\d{1,2}(?::\\d{2})?\\s*(?:AM|PM)?\\b", Pattern.CASE_INSENSITIVE);
        Matcher matcher = tokenPattern.matcher(cleaned.toUpperCase(Locale.US));
        while (matcher.find()) {
            TimeToken token = parseTimeToken(matcher.group());
            if (token != null) {
                tokens.add(token);
            }
        }

        if (tokens.size() >= 2) {
            TimeToken first = tokens.get(0);
            TimeToken second = tokens.get(1);
            return formatHourMinuteAmPm(first.hour24, first.minute)
                    + " - "
                    + formatHourMinuteAmPm(second.hour24, second.minute);
        }
        if (tokens.size() == 1) {
            TimeToken only = tokens.get(0);
            return formatHourMinuteAmPm(only.hour24, only.minute);
        }
        return "9:00 AM - 11:00 AM";
    }

    @Nullable
    private TimeToken parseTimeToken(@Nullable String rawToken) {
        if (TextUtils.isEmpty(rawToken)) {
            return null;
        }

        String token = rawToken.trim().toUpperCase(Locale.US).replaceAll("\\s+", "");
        boolean isAm = token.endsWith("AM");
        boolean isPm = token.endsWith("PM");
        if (isAm || isPm) {
            token = token.substring(0, token.length() - 2);
        }

        String[] split = token.split(":");
        int hour;
        int minute = 0;
        try {
            hour = Integer.parseInt(split[0]);
            if (split.length > 1) {
                minute = Integer.parseInt(split[1]);
            }
        } catch (Exception ignored) {
            return null;
        }

        minute = Math.max(0, Math.min(59, minute));
        if (isAm || isPm) {
            hour = hour % 12;
            if (isPm) {
                hour += 12;
            }
        }
        hour = Math.max(0, Math.min(23, hour));
        return new TimeToken(hour, minute);
    }

    @NonNull
    private String formatHourMinuteAmPm(int hour24, int minute) {
        int safeHour = Math.max(0, Math.min(23, hour24));
        int safeMinute = Math.max(0, Math.min(59, minute));
        int displayHour = safeHour % 12;
        if (displayHour == 0) {
            displayHour = 12;
        }
        String suffix = safeHour >= 12 ? "PM" : "AM";
        return String.format(Locale.US, "%d:%02d %s", displayHour, safeMinute, suffix);
    }

    private static final class TimeToken {
        final int hour24;
        final int minute;

        TimeToken(int hour24, int minute) {
            this.hour24 = hour24;
            this.minute = minute;
        }
    }

    private EqSuggestion parseEqSuggestion(String rawText) throws Exception {
        String cleaned = rawText
                .replace("```json", "")
                .replace("```JSON", "")
                .replace("```", "")
                .trim();

        int jsonStart = cleaned.indexOf('{');
        int jsonEnd = cleaned.lastIndexOf('}');
        if (jsonStart < 0 || jsonEnd <= jsonStart) {
            throw new IllegalArgumentException("No se encontro JSON en respuesta de IA");
        }

        JSONObject json = new JSONObject(cleaned.substring(jsonStart, jsonEnd + 1));
        String message = json.optString("message", "Te gustaria probar un ajuste fino para equilibrar mejor tu curva?").trim();
        if (message.isEmpty()) {
            message = "Te gustaria probar un ajuste fino para equilibrar mejor tu curva?";
        }

        JSONArray bandsArray = json.optJSONArray("bands");
        if (bandsArray == null || bandsArray.length() < EQ_BAND_COUNT) {
            throw new IllegalArgumentException("La IA no devolvio 9 bandas validas");
        }

        float[] bands = new float[EQ_BAND_COUNT];
        for (int i = 0; i < bands.length; i++) {
            bands[i] = clamp(
                    (float) bandsArray.optDouble(i, 0d),
                    AudioEffectsService.EQ_GAIN_MIN_DB,
                    AudioEffectsService.EQ_GAIN_MAX_DB
            );
        }

        float bassDb = clamp((float) json.optDouble("bassDb", 0d), 0f, AudioEffectsService.BASS_DB_MAX);
        float bassFrequencyHz = clamp(
            (float) json.optDouble("bassFrequencyHz", AudioEffectsService.BASS_FREQUENCY_DEFAULT_HZ),
            AudioEffectsService.BASS_FREQUENCY_MIN_HZ,
            AudioEffectsService.BASS_FREQUENCY_MAX_HZ
        );

        return new EqSuggestion(message, bands, bassDb, bassFrequencyHz);
    }

    private String ensureSpecificEqMessage(
            String aiMessage,
            String outputDevice,
            float[] currentBands,
            float[] suggestedBands,
            float currentBassDb,
            float suggestedBassDb,
            float currentBassFrequencyHz,
            float suggestedBassFrequencyHz,
            EqProfileDiagnostic diagnostic
    ) {
        if (isSpecificEqMessage(aiMessage) && isConsultativeEqMessage(aiMessage)) {
            return aiMessage.trim();
        }

        float[] deltas = new float[EQ_BAND_COUNT];
        for (int i = 0; i < deltas.length; i++) {
            float current = i < currentBands.length ? currentBands[i] : 0f;
            float target = i < suggestedBands.length ? suggestedBands[i] : 0f;
            deltas[i] = target - current;
        }

        int first = -1;
        int second = -1;
        int third = -1;
        for (int i = 0; i < deltas.length; i++) {
            float absDelta = Math.abs(deltas[i]);
            if (absDelta < 0.35f) {
                continue;
            }
            if (first < 0 || absDelta > Math.abs(deltas[first])) {
                third = second;
                second = first;
                first = i;
            } else if (second < 0 || absDelta > Math.abs(deltas[second])) {
                third = second;
                second = i;
            } else if (third < 0 || absDelta > Math.abs(deltas[third])) {
                third = i;
            }
        }

        float bassDbDelta = suggestedBassDb - currentBassDb;
        float bassFreqDelta = suggestedBassFrequencyHz - currentBassFrequencyHz;
        boolean mentionBass = Math.abs(bassDbDelta) >= 0.6f || Math.abs(bassFreqDelta) >= 12f;

        String intro;
        if (diagnostic.excessiveBass) {
            intro = "Te gustaria probar un ajuste para controlar el exceso de graves y recuperar definicion";
        } else if (diagnostic.weakBass) {
            intro = "Te gustaria probar un ajuste para dar mas cuerpo sin enturbiar medios";
        } else if (diagnostic.harshHighs) {
            intro = "Te gustaria probar un ajuste para suavizar agudos duros sin perder detalle";
        } else if (diagnostic.muffledHighs) {
            intro = "Te gustaria probar un ajuste para abrir agudos y mejorar aire";
        } else if (diagnostic.scoopedMids) {
            intro = "Te gustaria probar un ajuste para recuperar presencia vocal y pegada";
        } else if (diagnostic.extremeCurve) {
            intro = "Te gustaria probar un ajuste para estabilizar una curva muy extrema";
        } else {
            intro = "Te gustaria probar un ajuste fino para equilibrar mejor tu perfil";
        }

        StringBuilder actionBuilder = new StringBuilder();
        appendBandAction(actionBuilder, first, deltas, false);
        appendBandAction(actionBuilder, second, deltas, actionBuilder.length() > 0);
        appendBandAction(actionBuilder, third, deltas, actionBuilder.length() > 0);

        if (mentionBass) {
            if (actionBuilder.length() > 0) {
                actionBuilder.append(", y ");
            }
            actionBuilder.append("dejar Bass Boost en ")
                    .append(formatSignedDb(suggestedBassDb))
                    .append(" dB en ")
                    .append(Math.round(suggestedBassFrequencyHz))
                    .append(" Hz");
        }

        if (actionBuilder.length() == 0) {
            actionBuilder.append("subir 4kHz 0.8 dB y 8kHz 0.7 dB");
        }

        return intro + "? Podriamos probar " + actionBuilder + " para " + outputDevice + ".";
    }

    private void appendBandAction(
            @NonNull StringBuilder builder,
            int bandIndex,
            @NonNull float[] deltas,
            boolean appendSeparator
    ) {
        if (bandIndex < 0 || bandIndex >= deltas.length) {
            return;
        }
        if (appendSeparator) {
            builder.append(" y ");
        }
        builder.append(buildBandAction(bandIndex, deltas[bandIndex]));
    }

    private EqSuggestion enforceDynamicEqSuggestion(
            @NonNull float[] currentBands,
            @NonNull EqSuggestion aiSuggestion,
            float currentBassDb,
            float currentBassFrequencyHz,
            @NonNull EqProfileDiagnostic diagnostic
    ) {
        float maxDelta = 0f;
        for (int i = 0; i < EQ_BAND_COUNT; i++) {
            float current = i < currentBands.length ? currentBands[i] : 0f;
            float target = i < aiSuggestion.bands.length ? aiSuggestion.bands[i] : 0f;
            maxDelta = Math.max(maxDelta, Math.abs(target - current));
        }

        if (maxDelta >= 0.4f) {
            return aiSuggestion;
        }

        float[] tunedBands = new float[EQ_BAND_COUNT];
        for (int i = 0; i < tunedBands.length; i++) {
            tunedBands[i] = i < currentBands.length ? currentBands[i] : 0f;
        }
        float tunedBassDb = currentBassDb;
        float tunedBassFrequencyHz = currentBassFrequencyHz;

        if (diagnostic.excessiveBass) {
            tunedBands[0] -= 1.8f;
            tunedBands[1] -= 1.2f;
            tunedBands[6] += 0.7f;
            tunedBands[7] += 0.5f;
            tunedBassDb -= 0.8f;
            tunedBassFrequencyHz -= 8f;
        } else if (diagnostic.weakBass) {
            tunedBands[0] += 1.4f;
            tunedBands[1] += 1.0f;
            tunedBands[3] -= 0.4f;
            tunedBands[6] += 0.3f;
            tunedBassDb += 0.7f;
            tunedBassFrequencyHz += 6f;
        } else if (diagnostic.harshHighs) {
            tunedBands[7] -= 1.0f;
            tunedBands[8] -= 1.3f;
            tunedBands[4] += 0.4f;
            tunedBands[5] += 0.3f;
        } else if (diagnostic.muffledHighs) {
            tunedBands[6] += 0.9f;
            tunedBands[7] += 1.1f;
            tunedBands[8] += 0.8f;
            tunedBands[2] -= 0.4f;
        } else if (diagnostic.scoopedMids) {
            tunedBands[3] += 0.8f;
            tunedBands[4] += 1.0f;
            tunedBands[5] += 0.7f;
            tunedBands[0] -= 0.5f;
            tunedBands[8] -= 0.4f;
        } else {
            tunedBands[0] -= 0.4f;
            tunedBands[4] += 0.5f;
            tunedBands[6] += 0.6f;
        }

        for (int i = 0; i < tunedBands.length; i++) {
            tunedBands[i] = clamp(tunedBands[i], AudioEffectsService.EQ_GAIN_MIN_DB, AudioEffectsService.EQ_GAIN_MAX_DB);
        }
        tunedBassDb = clamp(tunedBassDb, 0f, AudioEffectsService.BASS_DB_MAX);
        tunedBassFrequencyHz = clamp(
                tunedBassFrequencyHz,
                AudioEffectsService.BASS_FREQUENCY_MIN_HZ,
                AudioEffectsService.BASS_FREQUENCY_MAX_HZ
        );

        return new EqSuggestion(aiSuggestion.message, tunedBands, tunedBassDb, tunedBassFrequencyHz);
    }

    @NonNull
    private EqProfileDiagnostic analyzeEqProfile(
            @NonNull float[] bands,
            float bassDb,
            float bassFrequencyHz
    ) {
        float low = averageBandRange(bands, 0, 3);
        float mid = averageBandRange(bands, 3, 6);
        float high = averageBandRange(bands, 6, 9);

        float max = -Float.MAX_VALUE;
        float min = Float.MAX_VALUE;
        for (float band : bands) {
            max = Math.max(max, band);
            min = Math.min(min, band);
        }
        float spread = max - min;

        boolean excessiveBass = (low - ((mid + high) * 0.5f)) >= 1.3f || bassDb >= 3.2f;
        boolean weakBass = (((mid + high) * 0.5f) - low) >= 1.5f && bassDb <= 1.8f;
        boolean harshHighs = (high - mid) >= 1.4f;
        boolean muffledHighs = (mid - high) >= 1.1f;
        boolean scoopedMids = (((low + high) * 0.5f) - mid) >= 1.2f;
        boolean extremeCurve = spread >= 10.5f || Math.max(Math.abs(max), Math.abs(min)) >= 7f;
        boolean subVeryFocused = bassFrequencyHz <= 28f;

        return new EqProfileDiagnostic(
                excessiveBass,
                weakBass,
                harshHighs,
                muffledHighs,
                scoopedMids,
                extremeCurve,
                subVeryFocused
        );
    }

    private float averageBandRange(@NonNull float[] bands, int start, int endExclusive) {
        int safeStart = Math.max(0, start);
        int safeEnd = Math.min(bands.length, endExclusive);
        if (safeEnd <= safeStart) {
            return 0f;
        }
        float sum = 0f;
        for (int i = safeStart; i < safeEnd; i++) {
            sum += bands[i];
        }
        return sum / (safeEnd - safeStart);
    }

    @NonNull
    private String buildEqDiagnosticSummary(@NonNull EqProfileDiagnostic diagnostic) {
        List<String> findings = new ArrayList<>();
        if (diagnostic.excessiveBass) {
            findings.add("posible exceso de graves");
        }
        if (diagnostic.weakBass) {
            findings.add("graves con poco cuerpo");
        }
        if (diagnostic.harshHighs) {
            findings.add("agudos potencialmente agresivos");
        }
        if (diagnostic.muffledHighs) {
            findings.add("agudos apagados");
        }
        if (diagnostic.scoopedMids) {
            findings.add("medios retraidos");
        }
        if (diagnostic.extremeCurve) {
            findings.add("curva muy extrema");
        }
        if (diagnostic.subVeryFocused) {
            findings.add("enfoque de bass tuner en subgrave");
        }

        if (findings.isEmpty()) {
            return "curva relativamente balanceada";
        }
        return TextUtils.join(", ", findings);
    }

    private boolean isSpecificEqMessage(String message) {
        if (message == null) {
            return false;
        }
        String lower = message.toLowerCase(Locale.US);
        boolean hasFrequency = lower.contains("hz") || lower.contains("khz")
                || lower.contains("62") || lower.contains("125") || lower.contains("250")
                || lower.contains("500") || lower.contains("1k") || lower.contains("2k")
                || lower.contains("4k") || lower.contains("8k") || lower.contains("16k");
        boolean hasAction = lower.contains("subir") || lower.contains("bajar") || lower.contains("aument")
                || lower.contains("reduc") || lower.contains("recort") || lower.contains("realz")
                || lower.contains("ajust");
        return hasFrequency && hasAction;
    }

    private boolean isConsultativeEqMessage(String message) {
        if (message == null) {
            return false;
        }
        String lower = message.toLowerCase(Locale.US);
        return lower.contains("te gustaria")
                || lower.contains("podriamos")
                || lower.contains("si quieres")
                || lower.contains("te parece")
                || lower.contains("podrias probar");
    }

    private String buildBandAction(int bandIndex, float delta) {
        String[] frequencies = WAVELET_9_BAND_LABELS;
        String action = delta >= 0f ? "subir " : "bajar ";
        return action + frequencies[bandIndex] + " " + formatSignedDb(Math.abs(delta)) + " dB";
    }

    private static final class EqProfileDiagnostic {
        final boolean excessiveBass;
        final boolean weakBass;
        final boolean harshHighs;
        final boolean muffledHighs;
        final boolean scoopedMids;
        final boolean extremeCurve;
        final boolean subVeryFocused;

        EqProfileDiagnostic(
                boolean excessiveBass,
                boolean weakBass,
                boolean harshHighs,
                boolean muffledHighs,
                boolean scoopedMids,
                boolean extremeCurve,
                boolean subVeryFocused
        ) {
            this.excessiveBass = excessiveBass;
            this.weakBass = weakBass;
            this.harshHighs = harshHighs;
            this.muffledHighs = muffledHighs;
            this.scoopedMids = scoopedMids;
            this.extremeCurve = extremeCurve;
            this.subVeryFocused = subVeryFocused;
        }
    }

    private String buildBandStateForPrompt(float[] bands) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < EQ_BAND_COUNT; i++) {
            if (i > 0) {
                builder.append(", ");
            }
            float value = i < bands.length ? bands[i] : 0f;
            builder.append(WAVELET_9_BAND_LABELS[i]).append("=").append(String.format(Locale.US, "%.1f", value));
        }
        return builder.toString();
    }

    private String formatSignedDb(float value) {
        return String.format(Locale.US, "%.1f", value);
    }

    private float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    /** Tries a single request. Returns RequestResult with text on success, or just httpCode on failure. */
    private RequestResult tryRequest(String model, String apiKey, String promptText) {
        try {
            URL url = new URL(BASE_URL + model + ":generateContent?key=" + apiKey);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(15000);

            JSONObject payload = new JSONObject();
            JSONObject contents = new JSONObject();
            JSONObject parts = new JSONObject();
            parts.put("text", promptText);
            contents.put("parts", new JSONArray().put(parts));
            payload.put("contents", new JSONArray().put(contents));

            JSONObject generationConfig = new JSONObject();
            generationConfig.put("responseMimeType", "application/json");
            payload.put("generationConfig", generationConfig);

            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = payload.toString().getBytes("utf-8");
                os.write(input, 0, input.length);
            }

            int code = conn.getResponseCode();
            if (code == 200) {
                BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "utf-8"));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) {
                    response.append(line.trim());
                }

                JSONObject root = new JSONObject(response.toString());
                String text = extractResponseText(root);
                if (TextUtils.isEmpty(text)) {
                    Log.w(TAG, "Empty text for model=" + model + " key=" + safeKeyPrefix(apiKey));
                    return new RequestResult(null, -1);
                }
                return new RequestResult(text, 200);
            } else {
                // Try to read error body for logging
                try {
                    if (conn.getErrorStream() != null) {
                        BufferedReader errBr = new BufferedReader(new InputStreamReader(conn.getErrorStream(), "utf-8"));
                        StringBuilder errBody = new StringBuilder();
                        String errLine;
                        while ((errLine = errBr.readLine()) != null) errBody.append(errLine);
                        Log.w(TAG, "Error body (HTTP " + code + "): " + errBody.toString().substring(0, Math.min(200, errBody.length())));
                    }
                } catch (Exception ignored) {}
                return new RequestResult(null, code);
            }
        } catch (Exception e) {
            Log.e(TAG, "Exception in request: " + e.getMessage());
            return new RequestResult(null, -1);
        }
    }

    @NonNull
    private String extractResponseText(@NonNull JSONObject root) {
        JSONArray candidates = root.optJSONArray("candidates");
        if (candidates == null || candidates.length() == 0) {
            return "";
        }

        for (int i = 0; i < candidates.length(); i++) {
            JSONObject candidate = candidates.optJSONObject(i);
            if (candidate == null) {
                continue;
            }

            JSONObject content = candidate.optJSONObject("content");
            if (content == null) {
                continue;
            }

            JSONArray parts = content.optJSONArray("parts");
            if (parts == null || parts.length() == 0) {
                continue;
            }

            StringBuilder joined = new StringBuilder();
            for (int p = 0; p < parts.length(); p++) {
                JSONObject part = parts.optJSONObject(p);
                if (part == null) {
                    continue;
                }
                String partText = part.optString("text", "").trim();
                if (partText.isEmpty()) {
                    continue;
                }
                if (joined.length() > 0) {
                    joined.append('\n');
                }
                joined.append(partText);
            }

            String text = joined.toString().trim();
            if (!text.isEmpty()) {
                return text;
            }
        }
        return "";
    }

    private static class RequestResult {
        final String text;
        final int httpCode;
        RequestResult(String text, int httpCode) {
            this.text = text;
            this.httpCode = httpCode;
        }
    }
}
