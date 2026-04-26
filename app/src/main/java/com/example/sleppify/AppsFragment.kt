package com.example.sleppify

import android.app.ActivityManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.progressindicator.CircularProgressIndicator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*

/**
 * Optimized Kotlin version of AppsFragment.
 * Simplified as requested: Removed Deep Clean, Storage, and Thermal metrics.
 * Focuses on RAM monitoring and Background App management.
 */
class AppsFragment : Fragment() {

    private lateinit var cpiRamUsage: CircularProgressIndicator
    private lateinit var tvRamUsagePercent: TextView
    private lateinit var tvTotalRamValue: TextView
    private lateinit var tvUsedRamValue: TextView
    private lateinit var fabStopApps: ExtendedFloatingActionButton
    private lateinit var layoutBackgroundAppsList: LinearLayout
    private lateinit var tvBackgroundAppsEmpty: TextView

    private val activityManager: ActivityManager by lazy { 
        requireContext().getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager 
    }
    
    private val usageStatsManager: UsageStatsManager? by lazy { 
        requireContext().getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager 
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_apps, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initViews(view)
        setupInteractions()
        startMetricsLoop()
        refreshBackgroundApps()
    }

    private fun initViews(v: View) {
        cpiRamUsage = v.findViewById(R.id.cpiRamUsage)
        tvRamUsagePercent = v.findViewById(R.id.tvRamUsagePercent)
        tvTotalRamValue = v.findViewById(R.id.tvTotalRamValue)
        tvUsedRamValue = v.findViewById(R.id.tvUsedRamValue)
        fabStopApps = v.findViewById(R.id.fabStopApps)
        // cardAccessibilityRequest = v.findViewById(R.id.cardAccessibilityRequest)
        // btnGrantAccessibility = v.findViewById(R.id.btnGrantAccessibility)
        layoutBackgroundAppsList = v.findViewById(R.id.layoutBackgroundAppsList)
        tvBackgroundAppsEmpty = v.findViewById(R.id.tvBackgroundAppsEmpty)
    }

    private fun setupInteractions() {
        fabStopApps.setOnClickListener { runStopAppsAction() }
        // btnGrantAccessibility.setOnClickListener { showAccessibilityPermissionDialog() }
    }

    private fun startMetricsLoop() {
        viewLifecycleOwner.lifecycleScope.launch {
            while (true) {
                updateRamMetrics()
                delay(1200L)
            }
        }
    }

    private fun updateRamMetrics() {
        if (!isAdded) return
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)

        val total = memoryInfo.totalMem.coerceAtLeast(1L)
        val used = (total - memoryInfo.availMem).coerceAtLeast(0L)
        val percent = ((used * 100.0) / total).toInt().coerceIn(0, 100)

        cpiRamUsage.setProgress(percent)
        tvRamUsagePercent.text = "$percent%"
        tvTotalRamValue.text = formatGb(total)
        tvUsedRamValue.text = formatGb(used)
        
        // refreshAccessibilityPermissionUi()
    }

    private fun refreshBackgroundApps() {
        if (!isAdded) return
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val apps = fetchBackgroundApps()
            withContext(Dispatchers.Main) {
                if (isAdded) renderBackgroundApps(apps)
            }
        }
    }

    private fun fetchBackgroundApps(): List<BackgroundAppEntry> {
        val pm = requireContext().packageManager
        val now = System.currentTimeMillis()
        val stats = usageStatsManager?.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, now - 3600000, now) ?: return emptyList()
        
        val entries = mutableListOf<BackgroundAppEntry>()
        val selfPkg = requireContext().packageName
        
        stats.filter { it.totalTimeInForeground > 0 && it.packageName != selfPkg }
            .sortedByDescending { it.lastTimeUsed }
            .take(15)
            .forEach { stat ->
                try {
                    val info = pm.getApplicationInfo(stat.packageName, 0)
                    if ((info.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) == 0) {
                        entries.add(BackgroundAppEntry(
                            packageName = stat.packageName,
                            appName = pm.getApplicationLabel(info).toString(),
                            icon = pm.getApplicationIcon(info)
                        ))
                    }
                } catch (e: Exception) {}
            }
        return entries.distinctBy { it.packageName }
    }

    private fun renderBackgroundApps(apps: List<BackgroundAppEntry>) {
        layoutBackgroundAppsList.removeAllViews()
        if (apps.isEmpty()) {
            tvBackgroundAppsEmpty.visibility = View.VISIBLE
            tvBackgroundAppsEmpty.text = "No se detectaron aplicaciones de alto consumo."
            return
        }

        tvBackgroundAppsEmpty.visibility = View.GONE
        val inflater = LayoutInflater.from(requireContext())
        apps.forEach { app ->
            val itemView = inflater.inflate(R.layout.item_background_app, layoutBackgroundAppsList, false)
            itemView.findViewById<TextView>(R.id.tvBackgroundAppName).text = app.appName
            itemView.findViewById<TextView>(R.id.tvBackgroundAppPackage).text = app.packageName
            itemView.findViewById<ImageView>(R.id.ivBackgroundAppIcon).setImageDrawable(app.icon)
            itemView.setOnClickListener { showAppDetails(app.packageName) }
            layoutBackgroundAppsList.addView(itemView)
        }
    }

    private fun showAppDetails(packageName: String) {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = android.net.Uri.fromParts("package", packageName, null)
            }
            startActivity(intent)
        } catch (e: Exception) {}
    }

    private fun runStopAppsAction() {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val packages = fetchBackgroundApps().map { it.packageName }
            if (packages.isEmpty()) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "No hay aplicaciones para detener", Toast.LENGTH_SHORT).show()
                }
                return@launch
            }

            // Trigger automation via broadcast or direct call if static instance available
            val intent = Intent(requireContext(), AppAccessibilityService::class.java).apply {
                action = AppAccessibilityService.ACTION_START_AUTOMATION
                putStringArrayListExtra(AppAccessibilityService.EXTRA_PACKAGE_NAMES, ArrayList(packages))
            }
            requireContext().startService(intent)
            
            withContext(Dispatchers.Main) {
                // Return to main to show loading or just let it run
                (activity as? MainActivity)?.showLoadingOverlay(true)
                delay(2000)
                (activity as? MainActivity)?.showLoadingOverlay(false)
                refreshBackgroundApps()
            }
        }
    }

    private fun formatGb(bytes: Long): String {
        val gb = bytes / (1024.0 * 1024.0 * 1024.0)
        return "%.2f GB".format(gb)
    }

    private data class BackgroundAppEntry(
        val packageName: String,
        val appName: String,
        val icon: Drawable
    )
    
    // Fallback if MainActivity doesn't have showLoadingOverlay
    private fun MainActivity?.showLoadingOverlay(show: Boolean) {
        // This is a placeholder; real implementation would be in MainActivity.kt
    }
}
