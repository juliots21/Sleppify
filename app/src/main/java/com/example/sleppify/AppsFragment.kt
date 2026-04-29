package com.example.sleppify

import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.progressindicator.CircularProgressIndicator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * AppsFragment — RAM gauge + lista de apps en segundo plano + acción de force-stop.
 *
 * Diseño deliberadamente simple, siguiendo `tigaliang/ForceStop`:
 *  - Un solo permiso de usuario: el servicio de Accesibilidad.
 *  - La lista se construye con `PackageManager.getInstalledApplications` (apps de usuario,
 *    excluyendo del sistema y la propia app). No requiere `PACKAGE_USAGE_STATS`.
 *  - El FAB delega la cola al servicio vía `AppAccessibilityService.requestForceStop(...)`.
 *  - Recibimos `ACTION_FINISHED` por LocalBroadcast para refrescar la UI.
 */
class AppsFragment : Fragment() {

    // -------- Vistas --------
    private lateinit var cpiRamUsage: CircularProgressIndicator
    private lateinit var tvRamUsagePercent: TextView
    private lateinit var tvTotalRamValue: TextView
    private lateinit var tvUsedRamValue: TextView
    private lateinit var fabStopApps: ExtendedFloatingActionButton
    private lateinit var layoutBackgroundAppsList: LinearLayout
    private lateinit var tvBackgroundAppsEmpty: TextView

    // -------- Estado --------
    @Volatile private var automationInProgress = false

    private val activityManager: ActivityManager by lazy {
        requireContext().getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    }

    // -------- Receiver del servicio --------
    private val finishedReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            if (intent?.action != AppAccessibilityService.ACTION_FINISHED) return
            automationInProgress = false
            if (!isAdded) return
            val target = intent.getIntExtra(AppAccessibilityService.EXTRA_TARGET_COUNT, 0)
            val confirmed = intent.getIntExtra(AppAccessibilityService.EXTRA_CONFIRMED_COUNT, 0)
            fabStopApps.isEnabled = true
            Toast.makeText(
                requireContext(),
                "Detenidas $confirmed/$target aplicaciones",
                Toast.LENGTH_SHORT
            ).show()
            refreshBackgroundApps()
        }
    }

    // ---------------- Lifecycle ----------------

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_apps, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        bindViews(view)
        fabStopApps.setOnClickListener { onStopAppsClicked() }
        startMetricsLoop()
        refreshBackgroundApps()
    }

    override fun onStart() {
        super.onStart()
        LocalBroadcastManager.getInstance(requireContext())
            .registerReceiver(finishedReceiver, IntentFilter(AppAccessibilityService.ACTION_FINISHED))
    }

    override fun onStop() {
        super.onStop()
        runCatching {
            LocalBroadcastManager.getInstance(requireContext()).unregisterReceiver(finishedReceiver)
        }
    }

    override fun onResume() {
        super.onResume()
        refreshBackgroundApps()
    }

    private fun bindViews(v: View) {
        cpiRamUsage = v.findViewById(R.id.cpiRamUsage)
        tvRamUsagePercent = v.findViewById(R.id.tvRamUsagePercent)
        tvTotalRamValue = v.findViewById(R.id.tvTotalRamValue)
        tvUsedRamValue = v.findViewById(R.id.tvUsedRamValue)
        fabStopApps = v.findViewById(R.id.fabStopApps)
        layoutBackgroundAppsList = v.findViewById(R.id.layoutBackgroundAppsList)
        tvBackgroundAppsEmpty = v.findViewById(R.id.tvBackgroundAppsEmpty)
    }

    // ---------------- RAM ----------------

    private fun startMetricsLoop() {
        viewLifecycleOwner.lifecycleScope.launch {
            while (isAdded) {
                updateRam()
                delay(1200L)
            }
        }
    }

    private fun updateRam() {
        if (!isAdded) return
        val info = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(info)
        val total = info.totalMem.coerceAtLeast(1L)
        val used = (total - info.availMem).coerceAtLeast(0L)
        val percent = ((used * 100.0) / total).toInt().coerceIn(0, 100)
        cpiRamUsage.setProgress(percent)
        tvRamUsagePercent.text = "$percent%"
        tvTotalRamValue.text = formatGb(total)
        tvUsedRamValue.text = formatGb(used)
    }

    private fun formatGb(bytes: Long): String =
        "%.2f GB".format(bytes / (1024.0 * 1024.0 * 1024.0))

    // ---------------- Lista de apps ----------------

    private fun refreshBackgroundApps() {
        if (!isAdded) return
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val apps = loadCandidateApps()
            withContext(Dispatchers.Main) { if (isAdded) renderApps(apps) }
        }
    }

    /**
     * Apps candidatas a "detener". Estrategia sin permiso de Uso:
     *  1) Si el servicio de accesibilidad está bindeado, usamos los paquetes que han
     *     pasado por foreground recientemente.
     *  2) Si no hay datos aún, listamos apps de usuario instaladas (excluyendo del sistema
     *     y la propia Sleppify).
     */
    private fun loadCandidateApps(): List<AppEntry> {
        val ctx = requireContext()
        val pm = ctx.packageManager
        val selfPkg = ctx.packageName

        val recent = AppAccessibilityService.recentForegroundPackages()
            .filter { it != selfPkg }

        val packagesToShow: List<String> = if (recent.isNotEmpty()) {
            recent
        } else {
            pm.getInstalledApplications(PackageManager.GET_META_DATA)
                .asSequence()
                .filter { it.packageName != selfPkg }
                .filter { (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 }
                .filter { pm.getLaunchIntentForPackage(it.packageName) != null }
                .map { it.packageName }
                .toList()
        }

        return packagesToShow.mapNotNull { pkg ->
            try {
                val info = pm.getApplicationInfo(pkg, 0)
                AppEntry(
                    packageName = pkg,
                    appName = pm.getApplicationLabel(info).toString(),
                    icon = pm.getApplicationIcon(info)
                )
            } catch (_: Throwable) { null }
        }.distinctBy { it.packageName }
    }

    private fun renderApps(apps: List<AppEntry>) {
        layoutBackgroundAppsList.removeAllViews()
        if (apps.isEmpty()) {
            tvBackgroundAppsEmpty.visibility = View.VISIBLE
            tvBackgroundAppsEmpty.text = "No se detectaron aplicaciones."
            return
        }
        tvBackgroundAppsEmpty.visibility = View.GONE
        val inflater = LayoutInflater.from(requireContext())
        apps.forEach { app ->
            val item = inflater.inflate(R.layout.item_background_app, layoutBackgroundAppsList, false)
            item.findViewById<TextView>(R.id.tvBackgroundAppName).text = app.appName
            item.findViewById<TextView>(R.id.tvBackgroundAppPackage).text = app.packageName
            item.findViewById<ImageView>(R.id.ivBackgroundAppIcon).setImageDrawable(app.icon)
            item.setOnClickListener { openAppSettings(app.packageName) }
            layoutBackgroundAppsList.addView(item)
        }
    }

    private fun openAppSettings(pkg: String) {
        runCatching {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = android.net.Uri.fromParts("package", pkg, null)
            }
            startActivity(intent)
        }
    }

    // ---------------- Acción Detener ----------------

    private fun onStopAppsClicked() {
        if (automationInProgress) {
            Toast.makeText(requireContext(), "Ya hay una automatización en curso", Toast.LENGTH_SHORT).show()
            return
        }
        if (!isAccessibilityEnabled()) {
            promptAccessibility()
            return
        }
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val packages = loadCandidateApps().map { it.packageName }
            withContext(Dispatchers.Main) {
                if (packages.isEmpty()) {
                    Toast.makeText(requireContext(), "No hay aplicaciones para detener", Toast.LENGTH_SHORT).show()
                    return@withContext
                }
                fabStopApps.isEnabled = false
                automationInProgress = true
                val started = AppAccessibilityService.requestForceStop(packages)
                if (!started) {
                    automationInProgress = false
                    fabStopApps.isEnabled = true
                    promptAccessibility()
                }
            }
        }
    }

    private fun isAccessibilityEnabled(): Boolean {
        val ctx = requireContext()
        val expected = "${ctx.packageName}/${AppAccessibilityService::class.java.name}"
        val am = ctx.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
        am?.getEnabledAccessibilityServiceList(
            android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK
        )?.forEach { info ->
            if (info.id.equals(expected, ignoreCase = true)) return true
        }
        val flat = Settings.Secure.getString(
            ctx.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        if (TextUtils.isEmpty(flat)) return false
        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(flat)
        while (splitter.hasNext()) {
            if (splitter.next().equals(expected, ignoreCase = true)) return true
        }
        return false
    }

    private fun promptAccessibility() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Permiso de accesibilidad")
            .setMessage(
                "Para detener automáticamente las apps en segundo plano, activa el servicio de " +
                "accesibilidad de Sleppify.\n\nEsto permite pulsar «Forzar detención» y «Aceptar» " +
                "por ti, sin acceso root."
            )
            .setPositiveButton("Abrir ajustes") { _, _ ->
                runCatching { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private data class AppEntry(
        val packageName: String,
        val appName: String,
        val icon: Drawable
    )
}
