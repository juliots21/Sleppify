package com.example.sleppify

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import java.util.ArrayDeque

/**
 * AppAccessibilityService — automatización de "Forzar detención" sin root.
 *
 * Inspirado al pie de la letra en https://github.com/tigaliang/ForceStop:
 *  1) Se abre `Settings.ACTION_APPLICATION_DETAILS_SETTINGS` para el paquete objetivo.
 *  2) Cuando el sistema entrega `TYPE_WINDOW_STATE_CHANGED` desde `com.android.settings`,
 *     buscamos un botón con texto "Force stop"/"Forzar detención" (en varios idiomas)
 *     o por id (`com.android.settings:id/force_stop_button`) y le hacemos click.
 *  3) Esperamos el siguiente cambio de ventana (diálogo de confirmación) y clickeamos
 *     el botón "OK"/"Aceptar" — preferimos el id estable `android:id/button1`.
 *  4) Procesamos el siguiente paquete de la cola, hasta vaciarla; al terminar, emitimos
 *     un broadcast local para que la UI se refresque.
 */
class AppAccessibilityService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private val queue = ArrayDeque<String>()
    private var currentPkg: String? = null
    private var step: Step = Step.IDLE
    private var targetCount: Int = 0
    private var confirmedCount: Int = 0
    private var perStepRetry = 0

    /** Snapshot en memoria de paquetes que han pasado por foreground (para la UI). */
    private val recentForeground = LinkedHashSet<String>()

    private enum class Step { IDLE, AWAITING_FORCE_STOP, AWAITING_CONFIRM }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d(TAG, "onServiceConnected")
    }

    override fun onUnbind(intent: Intent?): Boolean {
        if (instance === this) instance = null
        reset()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance === this) instance = null
    }

    override fun onInterrupt() { /* no-op */ }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val ev = event ?: return
        val pkg = ev.packageName?.toString().orEmpty()

        // Track recientes (apps que han estado en foreground) para la lista de la UI
        if (pkg.isNotEmpty() && pkg != packageName && pkg != SETTINGS_PKG) {
            synchronized(recentForeground) {
                recentForeground.remove(pkg)
                recentForeground.add(pkg)
                while (recentForeground.size > MAX_RECENT) {
                    val it = recentForeground.iterator()
                    if (it.hasNext()) { it.next(); it.remove() }
                }
            }
        }

        if (step == Step.IDLE) return
        if (pkg != SETTINGS_PKG) return
        if (ev.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            ev.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) return

        val root = rootInActiveWindow ?: return
        when (step) {
            Step.AWAITING_FORCE_STOP -> tryClickForceStop(root)
            Step.AWAITING_CONFIRM -> tryClickConfirm(root)
            else -> Unit
        }
    }

    // ---------------- Flow ----------------

    private fun startQueue(packages: List<String>) {
        reset()
        queue.addAll(packages.distinct().filter { it.isNotBlank() && it != packageName })
        targetCount = queue.size
        confirmedCount = 0
        Log.d(TAG, "startQueue size=$targetCount")
        processNext()
    }

    private fun processNext() {
        currentPkg = queue.pollFirst()
        if (currentPkg == null) {
            finish()
            return
        }
        step = Step.AWAITING_FORCE_STOP
        perStepRetry = 0
        openAppDetails(currentPkg!!)
        scheduleStepTimeout()
    }

    private fun openAppDetails(pkg: String) {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", pkg, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        } catch (t: Throwable) {
            Log.e(TAG, "openAppDetails failed", t)
            advance(false)
        }
    }

    private fun tryClickForceStop(root: AccessibilityNodeInfo) {
        // Preferimos id estable; fallback a texto multilingüe.
        val byId = findFirstByViewId(root, FORCE_STOP_IDS)
        val node = byId ?: findFirstClickableByText(root, FORCE_STOP_TEXTS)
        if (node != null && node.isEnabled) {
            if (performClick(node)) {
                step = Step.AWAITING_CONFIRM
                perStepRetry = 0
                scheduleStepTimeout()
            }
        }
        // Si el botón no está habilitado, la app probablemente ya está detenida → avanzar.
        else if (byId != null && !byId.isEnabled) {
            advance(false)
        }
    }

    private fun tryClickConfirm(root: AccessibilityNodeInfo) {
        val byId = findFirstByViewId(root, CONFIRM_IDS)
        val node = byId ?: findFirstClickableByText(root, CONFIRM_TEXTS)
        if (node != null && performClick(node)) {
            advance(true)
        }
    }

    private fun advance(confirmed: Boolean) {
        if (confirmed) confirmedCount++
        handler.removeCallbacks(stepTimeoutRunnable)
        // Pequeño respiro para dejar que Settings se cierre antes del siguiente
        handler.postDelayed({ processNext() }, 350)
    }

    private fun finish() {
        step = Step.IDLE
        currentPkg = null
        handler.removeCallbacks(stepTimeoutRunnable)
        val intent = Intent(ACTION_FINISHED).apply {
            putExtra(EXTRA_TARGET_COUNT, targetCount)
            putExtra(EXTRA_CONFIRMED_COUNT, confirmedCount)
        }
        LocalBroadcastManager.getInstance(applicationContext).sendBroadcast(intent)
        Log.d(TAG, "finish target=$targetCount confirmed=$confirmedCount")
    }

    private fun reset() {
        queue.clear()
        currentPkg = null
        step = Step.IDLE
        perStepRetry = 0
        targetCount = 0
        confirmedCount = 0
        handler.removeCallbacks(stepTimeoutRunnable)
    }

    // ---------------- Timeout / retry ----------------

    private val stepTimeoutRunnable = Runnable {
        when (step) {
            Step.AWAITING_FORCE_STOP, Step.AWAITING_CONFIRM -> {
                perStepRetry++
                if (perStepRetry >= MAX_RETRIES_PER_STEP) {
                    Log.w(TAG, "step $step timeout for $currentPkg → skip")
                    advance(false)
                } else {
                    rootInActiveWindow?.let {
                        if (step == Step.AWAITING_FORCE_STOP) tryClickForceStop(it)
                        else tryClickConfirm(it)
                    }
                    scheduleStepTimeout()
                }
            }
            else -> Unit
        }
    }

    private fun scheduleStepTimeout() {
        handler.removeCallbacks(stepTimeoutRunnable)
        handler.postDelayed(stepTimeoutRunnable, STEP_TIMEOUT_MS)
    }

    // ---------------- Node helpers ----------------

    private fun performClick(node: AccessibilityNodeInfo): Boolean {
        var n: AccessibilityNodeInfo? = node
        while (n != null) {
            if (n.isClickable) return n.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            n = n.parent
        }
        return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }

    private fun findFirstByViewId(root: AccessibilityNodeInfo, ids: Array<String>): AccessibilityNodeInfo? {
        for (id in ids) {
            val list = root.findAccessibilityNodeInfosByViewId(id) ?: continue
            for (n in list) if (n != null) return n
        }
        return null
    }

    private fun findFirstClickableByText(root: AccessibilityNodeInfo, texts: Array<String>): AccessibilityNodeInfo? {
        for (t in texts) {
            val list = root.findAccessibilityNodeInfosByText(t) ?: continue
            for (n in list) {
                if (n == null) continue
                val txt = n.text?.toString().orEmpty()
                if (txt.equals(t, ignoreCase = true) || txt.contains(t, ignoreCase = true)) {
                    if (n.isVisibleToUser) return n
                }
            }
        }
        return null
    }

    companion object {
        private const val TAG = "AppA11yService"
        private const val SETTINGS_PKG = "com.android.settings"
        private const val MAX_RECENT = 60
        private const val STEP_TIMEOUT_MS = 1500L
        private const val MAX_RETRIES_PER_STEP = 6 // ~9s por paso

        const val ACTION_FINISHED = "com.example.sleppify.action.FORCE_STOP_FINISHED"
        const val EXTRA_TARGET_COUNT = "extra_target_count"
        const val EXTRA_CONFIRMED_COUNT = "extra_confirmed_count"

        // IDs comunes en AOSP / OEMs para el botón Forzar detención
        private val FORCE_STOP_IDS = arrayOf(
            "com.android.settings:id/force_stop_button",
            "com.android.settings:id/right_button",
            "com.miui.securitycenter:id/force_stop_button"
        )
        // android:id/button1 = botón "OK/Aceptar" estándar de AlertDialog
        private val CONFIRM_IDS = arrayOf(
            "android:id/button1",
            "com.android.settings:id/dlg_ok"
        )
        private val FORCE_STOP_TEXTS = arrayOf(
            "Force stop", "FORCE STOP",
            "Forzar detención", "Forzar parada", "Forzar detener",
            "Forçar paragem", "Forçar parada",
            "Arrêter", "Forcer l'arrêt",
            "Beenden erzwingen",
            "强行停止", "强制停止",
            "強制停止"
        )
        private val CONFIRM_TEXTS = arrayOf(
            "OK", "Ok",
            "Aceptar",
            "Forzar detención", "Forzar parada",
            "Force stop",
            "确定", "確定", "強制停止"
        )

        @Volatile private var instance: AppAccessibilityService? = null

        @JvmStatic fun isRunning(): Boolean = instance != null

        /** Inicia la cola de force-stop. Devuelve false si el servicio no está bindeado. */
        @JvmStatic
        fun requestForceStop(packages: List<String>): Boolean {
            val svc = instance ?: return false
            svc.handler.post { svc.startQueue(packages) }
            return true
        }

        /** Lista de paquetes que han pasado recientemente por foreground (mientras el servicio estaba activo). */
        @JvmStatic
        fun recentForegroundPackages(): List<String> {
            val svc = instance ?: return emptyList()
            return synchronized(svc.recentForeground) { svc.recentForeground.toList().asReversed() }
        }
    }
}
