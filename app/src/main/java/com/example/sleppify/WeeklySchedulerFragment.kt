package com.example.sleppify

import android.annotation.SuppressLint
import android.app.Dialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.view.*
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.*
import androidx.core.content.ContextCompat
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.example.sleppify.CloudSyncManager
import com.example.sleppify.MainActivity
import com.example.sleppify.R
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.*

class WeeklySchedulerFragment : Fragment() {

    companion object {
        private const val WEEKS_AHEAD = 10
        private const val KEY_FUTURE_INSIGHT_LOCK_DATE = "future_insight_lock_date"
        private const val KEY_FUTURE_INSIGHT_LOCK_TASK = "future_insight_lock_task"
        private const val KEY_FUTURE_INSIGHT_LOCK_TITLE = "future_insight_lock_title"
        private const val KEY_FUTURE_INSIGHT_LOCK_MESSAGE = "future_insight_lock_message"
        private const val KEY_FUTURE_INSIGHT_LOCK_ACTION = "future_insight_lock_action"
        private const val KEY_FUTURE_INSIGHT_LOCK_MICRO = "future_insight_lock_micro"
        private const val FUTURE_INSIGHT_ACTION_SCHEME = "sleppify"
        private const val FUTURE_INSIGHT_ACTION_HOST = "future-insight"
        private const val FUTURE_INSIGHT_ACTION_PATH = "/action"
        private const val FUTURE_TASK_ACTION_HOST = "future-task"
        private const val FUTURE_TASK_ACTION_PATH = "/menu"
        private const val FUTURE_TASK_ADD_PATH = "/add"
        private const val FUTURE_INSIGHT_MODE_SCHEDULE = "schedule_time"
        private const val FUTURE_INSIGHT_MODE_FOCUS = "focus_block"
        private const val FUTURE_INSIGHT_REFRESH_INTERVAL_MS = 90L * 60L * 1000L
        private const val FUTURE_INSIGHT_RETRY_INTERVAL_MS = 60L * 1000L
        private val INSIGHT_MINUTE_VALUES = arrayOf(
            "00", "05", "10", "15", "20", "25", "30", "35", "40", "45", "50", "55"
        )
        private const val LEGACY_DEMO_TITLE = ""
        private const val DEFAULT_TASK_DESCRIPTION = "Sin detalles adicionales"
        private const val PENDING_TASK_DESCRIPTION = "Generando detalles con IA..."
        private const val DEFAULT_TASK_CATEGORY = ""
        private const val PENDING_TASK_CATEGORY = "Generando categoria con IA..."
        private const val FALLBACK_TASK_CATEGORY = "Otros"
        private const val TASK_METADATA_ERROR_DESCRIPTION = "Sin descripción"
        private const val TASK_METADATA_ERROR_CATEGORY = "Fallback"
        private const val TASK_METADATA_MAX_RETRIES = 2
        private const val TASK_METADATA_BACKFILL_LIMIT = 6
        private const val TASK_METADATA_PULL_REFRESH_LIMIT = 24
        private const val TASK_METADATA_BACKFILL_INTERVAL_MS = 2L * 60L * 1000L
        private const val FUTURE_TASKS_DYNAMIC_TOKEN = "__DYNAMIC_SECTIONS__"
        private const val FUTURE_TASKS_THEME_BODY_CLASS_TOKEN = "__THEME_BODY_CLASS__"
        private const val FUTURE_TASK_DAYS_LIMIT = 35
        private const val FUTURE_TASKS_PER_DAY_LIMIT = 12
        private const val AGENDA_CLOUD_REFRESH_MIN_INTERVAL_MS = 15000L
        private const val AGENDA_REMINDER_RESCHEDULE_MIN_INTERVAL_MS = 20000L
        private val SPANISH_LOCALE = Locale("es", "ES")
        private const val TASK_TIME_COLOR_DAY = 0xFFAEC7FD.toInt()
        private const val TASK_TIME_COLOR_NIGHT = 0xFFFFB59A.toInt()
        private const val TASK_CARD_COLOR_DEFAULT = 0xFF191B22.toInt()
        private const val TASK_CARD_COLOR_LONG_PRESS = 0xFF252833.toInt()
    }

    private val allDayCards = mutableListOf<MaterialCardView>()
    private val allDayLabels = mutableListOf<TextView>()
    private val allDayNumbers = mutableListOf<TextView>()
    private val allDayMonthLabels = mutableListOf<TextView>()
    private val allDayDates = mutableListOf<Calendar>()
    private val allDayDateKeys = mutableListOf<String>()

    private var daysCarouselScrollListener: View.OnScrollChangeListener? = null
    private val syncCarouselHeaderRunnable: Runnable = Runnable { syncWeekRangeAndActiveMonthFromCarousel() }
    private var isCarouselHeaderSyncQueued = false
    private var carouselFocusedDateKey = ""
    private var carouselFocusedWeekStartKey = ""
    private var activeMonthLabelIndex = -1
    private var daysCarouselTouchDownX = 0f
    private var daysCarouselTouchDownY = 0f
    private var daysCarouselHorizontalGesture = false
    private var daysCarouselTouchSlop = -1

    private var tvDateRange: TextView? = null
    private var tvSelectedDayTitle: TextView? = null
    private var tvFabHint: TextView? = null
    private var ivWeekCalendarPicker: ImageView? = null
    private var llDaysContainer: LinearLayout? = null
    private var scrollAgendaContent: NestedScrollView? = null
    private var hsvDaysCarousel: HorizontalScrollView? = null
    private var rvTasks: RecyclerView? = null
    private var swipeAgendaRefresh: SwipeRefreshLayout? = null
    private var fabAddTask: FloatingActionButton? = null
    private var cardFabHint: MaterialCardView? = null

    private var taskAdapter: TaskAdapter? = null
    private val tasksPerDay = HashMap<String, MutableList<Task>>()
    private var cloudSyncManager: CloudSyncManager? = null
    private var settingsPrefs: SharedPreferences? = null
    private var fabLockedForAuth = false
    private var rvFutureTasksLayer: RecyclerView? = null
    private var futureTasksAdapter: FutureTasksAdapter? = null
    private val aiTaskMetadataHandler = Handler(Looper.getMainLooper())
    private val pendingTaskMetadataRequests = HashSet<String>()
    
    private var futureTasksRenderScheduled = false
    private val renderFutureTasksRunnable = Runnable { renderFutureTasksLayerNow() }
    
    private var lastLoadedAgendaJson = ""
    private var lastAgendaLocalRefreshAtMs = 0L
    private var lastAgendaReminderRescheduleAtMs = 0L
    private var lastAgendaCloudRefreshAtMs = 0L
    private var lastTaskMetadataBackfillAtMs = 0L
    
    private var currentFutureInsightModel: FutureInsightModel? = null
    private var futureInsightRequestInFlight = false
    private var insightTimeDialog: Dialog? = null
    private var taskActionPopupWindow: PopupWindow? = null

    private var selectedDateKey: String? = null
    private var selectedDate: Calendar? = null
    private var todayDate: Calendar = Calendar.getInstance()

    private val dateKeyFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    data class Task(
        var title: String,
        var desc: String = DEFAULT_TASK_DESCRIPTION,
        var time: String = "",
        var category: String = DEFAULT_TASK_CATEGORY
    )

    private class FutureDayBlock(
        val dateKey: String,
        val displayDate: String,
        val tasks: List<Task>
    )

    private class TaskSelection(val dateKey: String, val task: Task)

    private class FutureInsightModel(
        val agendaSignature: String,
        val mode: String,
        targetDateKey: String?,
        targetTaskTitle: String?,
        val title: String,
        val message: String,
        val actionLabel: String,
        val microAction: String,
        val refreshAfterAtMs: Long
    ) {
        val targetDateKey = targetDateKey ?: ""
        val targetTaskTitle = targetTaskTitle ?: ""
    }

    private class InsightTimeSelection(val hourOfDay24: Int, val minute: Int)

    private sealed class FutureAgendaItem {
        data class Header(val displayDate: String, val cycleLabel: String, val dateKey: String) : FutureAgendaItem()
        data class TaskItem(val task: Task, val dateKey: String, val isFirst: Boolean, val isLast: Boolean, val globalIndex: Int) : FutureAgendaItem()
        data class InsightCard(val model: FutureInsightModel) : FutureAgendaItem()
        object Empty : FutureAgendaItem()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_weekly_scheduler, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        tvDateRange = view.findViewById(R.id.tvDateRange)
        tvSelectedDayTitle = view.findViewById(R.id.tvSelectedDayTitle)
        ivWeekCalendarPicker = view.findViewById(R.id.ivWeekCalendarPicker)
        llDaysContainer = view.findViewById(R.id.llDaysContainer)
        scrollAgendaContent = view.findViewById(R.id.scrollAgendaContent)
        hsvDaysCarousel = view.findViewById(R.id.hsvDaysCarousel)
        rvTasks = view.findViewById(R.id.rvTasks)
        swipeAgendaRefresh = view.findViewById(R.id.swipeAgendaRefresh)
        fabAddTask = view.findViewById(R.id.fabAddTask)
        cardFabHint = view.findViewById(R.id.cardFabHint)
        tvFabHint = view.findViewById(R.id.tvFabHint)
        rvFutureTasksLayer = view.findViewById(R.id.rvFutureTasksLayer)
        
        daysCarouselTouchSlop = ViewConfiguration.get(requireContext()).scaledTouchSlop
        cloudSyncManager = CloudSyncManager.getInstance(requireContext().applicationContext)
        settingsPrefs = requireContext().applicationContext.getSharedPreferences(CloudSyncManager.PREFS_AGENDA, Context.MODE_PRIVATE)

        todayDate = Calendar.getInstance()
        todayDate.set(Calendar.HOUR_OF_DAY, 0)
        todayDate.set(Calendar.MINUTE, 0)
        todayDate.set(Calendar.SECOND, 0)
        todayDate.set(Calendar.MILLISECOND, 0)

        setupDaysCarousel()
        setupTasksRecyclerView()
        setupAgendaSwipeRefresh()
        setupFutureTasksRecyclerView()
        setupAddTaskFab()
        setupAuthManagerIntegration()

        ivWeekCalendarPicker?.setOnClickListener { showWeekCalendarPicker() }

        loadAgendaFromLocal(false)
        val initialSelectedDateKey = selectedDateKey
        if (initialSelectedDateKey != null && selectedDate != null) {
            scrollCarouselToDate(initialSelectedDateKey)
        }
    }
    private fun setupTasksRecyclerView() {
        rvTasks?.let { rv ->
            rv.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.VERTICAL, false)
            taskAdapter = TaskAdapter(emptyList())
            rv.adapter = taskAdapter
            rv.addOnScrollListener(object : RecyclerView.OnScrollListener() {
                private var lastScrollY = 0

                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    lastScrollY += dy
                    val lockThreshold = dpToPx(16)
                    if (Math.abs(lastScrollY) > lockThreshold) {
                        lockAgendaParentsForHorizontalScroll(true)
                    } else if (lastScrollY <= 0) {
                        lockAgendaParentsForHorizontalScroll(false)
                        lastScrollY = 0
                    }
                }

                override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                    if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                        if (!recyclerView.canScrollVertically(-1)) {
                            lockAgendaParentsForHorizontalScroll(false)
                            lastScrollY = 0
                        }
                    }
                }
            })
        }
    }

    private fun lockAgendaParentsForHorizontalScroll(locked: Boolean) {
        hsvDaysCarousel?.requestDisallowInterceptTouchEvent(locked)
        setAgendaPullRefreshState(!locked)
    }

    private fun setAgendaPullRefreshState(enabled: Boolean) {
        // Only allow enabling if we're at the top of the content
        val atTop = scrollAgendaContent?.scrollY ?: 0 <= 0
        swipeAgendaRefresh?.isEnabled = enabled && atTop
    }

    private fun setupAgendaSwipeRefresh() {
        swipeAgendaRefresh?.let { swipe ->
            swipe.setColorSchemeColors(Color.parseColor("#FFFFFF"))
            swipe.setProgressBackgroundColorSchemeColor(Color.parseColor("#252833"))
            swipe.setOnRefreshListener {
                val now = System.currentTimeMillis()
                val isDebounced = (now - lastAgendaCloudRefreshAtMs) < AGENDA_CLOUD_REFRESH_MIN_INTERVAL_MS
                swipe.isRefreshing = true

                if (isDebounced) {
                    aiTaskMetadataHandler.postDelayed({
                        if (isAdded) {
                            loadAgendaFromLocal(true)
                            refreshSelectedDayTasks()
                            swipe.isRefreshing = false
                        }
                    }, 600)
                    return@setOnRefreshListener
                }

                if (cloudSyncManager == null) {
                    aiTaskMetadataHandler.postDelayed({
                        if (isAdded) {
                            loadAgendaFromLocal(true)
                            refreshSelectedDayTasks()
                            swipe.isRefreshing = false
                        }
                    }, 500)
                    return@setOnRefreshListener
                }

                cloudSyncManager?.syncNow(object : CloudSyncManager.SyncCallback {
                    override fun onComplete(success: Boolean, message: String?) {
                        if (isAdded) {
                            lastAgendaCloudRefreshAtMs = System.currentTimeMillis()
                            loadAgendaFromLocal(true)
                            refreshSelectedDayTasks()
                            swipe.isRefreshing = false
                        }
                    }
                })
            }
        }
    }

    private fun setupFutureTasksRecyclerView() {
        rvFutureTasksLayer?.let { rv ->
            rv.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.VERTICAL, false)
            futureTasksAdapter = FutureTasksAdapter()
            rv.adapter = futureTasksAdapter
        }
    }

    private fun setupAddTaskFab() {
        fabAddTask?.setOnClickListener {
            if (fabLockedForAuth) {
                showSubtleMessage("Sincronizando cuenta. Espera un momento.")
            } else {
                triggerAddTaskFlow()
            }
        }
    }

    private fun triggerAddTaskFlow() {
        if (!isAdded || context == null) return
        
        // Ensure both selectedDateKey AND selectedDate are initialized before showing dialog
        if (selectedDateKey == null || selectedDate == null) {
            val base = todayDate.clone() as Calendar
            selectedDate = base
            selectedDateKey = dateKeyFormat.format(base.time)
        }
        
        showAddTaskDialog(null, selectedDateKey)
    }

    private fun setupAuthManagerIntegration() {
        val authManager = (activity as? MainActivity)?.getAuthManager()
        setFabAuthLocked(authManager?.isSignedIn() != true)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupDaysCarousel() {
        llDaysContainer?.let { container ->
            generateDaysForTenWeeks(container)

            val initialSelectedCard = allDayCards.firstOrNull()
            if (initialSelectedCard != null && allDayDateKeys.isNotEmpty()) {
                selectDayCard(initialSelectedCard, allDayDateKeys.first(), allDayDates.first())
            }

            daysCarouselScrollListener = View.OnScrollChangeListener { v, scrollX, scrollY, oldScrollX, oldScrollY ->
                if (!isCarouselHeaderSyncQueued) {
                    isCarouselHeaderSyncQueued = true
                    v.post(syncCarouselHeaderRunnable)
                }
            }
            hsvDaysCarousel?.setOnScrollChangeListener(daysCarouselScrollListener)

            hsvDaysCarousel?.setOnTouchListener { v, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        daysCarouselTouchDownX = event.rawX
                        daysCarouselTouchDownY = event.rawY
                        daysCarouselHorizontalGesture = false
                        // Disable PSR early on touch to prevent unintended refresh during carousel swipe
                        setAgendaPullRefreshState(false)
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (!daysCarouselHorizontalGesture) {
                            val dx = Math.abs(event.rawX - daysCarouselTouchDownX)
                            val dy = Math.abs(event.rawY - daysCarouselTouchDownY)
                            if (dx > daysCarouselTouchSlop && dx > dy) {
                                daysCarouselHorizontalGesture = true
                            } else if (dy > daysCarouselTouchSlop) {
                                v.parent.requestDisallowInterceptTouchEvent(false)
                                // If vertical motion is clear, we could re-enable PSR, 
                                // but usually, we want to stay disabled until touch ends if on carousel
                            }
                        }
                        if (daysCarouselHorizontalGesture) {
                            v.parent.requestDisallowInterceptTouchEvent(true)
                        }
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        daysCarouselHorizontalGesture = false
                        setAgendaPullRefreshState(true)
                    }
                }
                false
            }

        }
    }

    private fun generateDaysForTenWeeks(container: LinearLayout) {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)

        // Adjust to Monday of this week
        val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
        val daysToMinus = if (dayOfWeek == Calendar.SUNDAY) 6 else dayOfWeek - Calendar.MONDAY
        cal.add(Calendar.DAY_OF_MONTH, -daysToMinus)

        val todayMillis = todayDate.timeInMillis

        for (w in 0 until WEEKS_AHEAD) {
            val weekContainer = LinearLayout(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                orientation = LinearLayout.HORIZONTAL
            }

            for (d in 0..6) {
                val dateMillis = cal.timeInMillis
                val dateKey = dateKeyFormat.format(cal.time)

                val colLayout = LinearLayout(requireContext()).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER_HORIZONTAL
                    val lp = LinearLayout.LayoutParams(dpToPx(48), dpToPx(88))
                    lp.marginStart = dpToPx(6)
                    lp.marginEnd = dpToPx(6)
                    layoutParams = lp
                }

                val dayLabel = getDayShortLabel(cal.get(Calendar.DAY_OF_WEEK))
                val dayNum = cal.get(Calendar.DAY_OF_MONTH).toString()
                
                val monthLabelFormat = SimpleDateFormat("MMM", Locale.getDefault())
                val tvMonthLabel = TextView(requireContext()).apply {
                    text = monthLabelFormat.format(cal.time).uppercase(Locale.getDefault())
                    textSize = 10f
                    setTextColor(Color.parseColor("#808080"))
                    gravity = Gravity.CENTER
                    val monthLp = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                    monthLp.bottomMargin = dpToPx(4)
                    layoutParams = monthLp
                    visibility = View.INVISIBLE
                }
                allDayMonthLabels.add(tvMonthLabel)

                val card = createDayCard(dayLabel, dayNum)

                if (dateMillis == todayMillis) {
                    val content = card.getChildAt(0) as? LinearLayout
                    val currentLabel = content?.getChildAt(0) as? TextView
                    currentLabel?.text = "Hoy"
                    currentLabel?.setTextColor(ContextCompat.getColor(requireContext(), R.color.stitch_blue))
                }

                if (dateMillis < todayMillis) {
                    card.alpha = 0.3f
                    card.isEnabled = false
                } else {
                    val finalDate = cal.clone() as Calendar
                    val finalDateKey = dateKey
                    card.setOnClickListener {
                        selectDayCard(card, finalDateKey, finalDate)
                    }
                }

                allDayCards.add(card)
                allDayDates.add(cal.clone() as Calendar)
                allDayDateKeys.add(dateKey)

                colLayout.addView(tvMonthLabel)
                colLayout.addView(card)
                weekContainer.addView(colLayout)

                cal.add(Calendar.DAY_OF_MONTH, 1)
            }

            container.addView(weekContainer)
        }
    }

    private fun getDayShortLabel(dayOfWeek: Int): String {
        return when (dayOfWeek) {
            Calendar.MONDAY -> "L"
            Calendar.TUESDAY -> "M"
            Calendar.WEDNESDAY -> "MI"
            Calendar.THURSDAY -> "J"
            Calendar.FRIDAY -> "V"
            Calendar.SATURDAY -> "S"
            Calendar.SUNDAY -> "D"
            else -> ""
        }
    }

    private fun getDayFullLabel(dayOfWeek: Int): String {
        return when (dayOfWeek) {
            Calendar.MONDAY -> "Lunes"
            Calendar.TUESDAY -> "Martes"
            Calendar.WEDNESDAY -> "Miércoles"
            Calendar.THURSDAY -> "Jueves"
            Calendar.FRIDAY -> "Viernes"
            Calendar.SATURDAY -> "Sábado"
            Calendar.SUNDAY -> "Domingo"
            else -> ""
        }
    }

    private fun selectDayCard(card: MaterialCardView, dateKey: String, date: Calendar) {
        val todayMillis = todayDate.timeInMillis
        if (date.timeInMillis < todayMillis) return

        for (i in allDayCards.indices) {
            val c = allDayCards[i]
            val cLabel = allDayLabels[i]
            val cNumber = allDayNumbers[i]
            val dateOfC = allDayDates[i]
            val isSelected = (c == card)

            if (isSelected) {
                c.setCardBackgroundColor(Color.parseColor("#1A2E5A"))
                c.strokeWidth = dpToPx(1)
                c.strokeColor = ContextCompat.getColor(requireContext(), R.color.stitch_blue)
                cLabel.setTextColor(ContextCompat.getColor(requireContext(), R.color.stitch_blue))
                cNumber.setTextColor(Color.WHITE)
            } else {
                c.setCardBackgroundColor(Color.parseColor("#111319"))
                c.strokeWidth = 0

                if (dateOfC.timeInMillis == todayMillis) {
                    cLabel.setTextColor(ContextCompat.getColor(requireContext(), R.color.stitch_blue))
                    cNumber.setTextColor(Color.WHITE)
                } else if (dateOfC.timeInMillis < todayMillis) {
                    cLabel.setTextColor(Color.parseColor("#808080"))
                    cNumber.setTextColor(Color.parseColor("#808080"))
                } else {
                    cLabel.setTextColor(Color.parseColor("#BDBDBD"))
                    cNumber.setTextColor(Color.WHITE)
                }
            }
        }

        selectedDateKey = dateKey
        selectedDate = date.clone() as Calendar

        tvSelectedDayTitle?.let { updateDayDisplay(it, date) }

        val dayTasks = tasksPerDay[dateKey]
        if (dayTasks != null) {
            dayTasks.sortWith { left, right -> extractTaskSortKey(left.time).compareTo(extractTaskSortKey(right.time)) }
            taskAdapter?.setTasks(dayTasks)
        } else {
            taskAdapter?.setTasks(emptyList())
        }

        renderFutureTasksLayer()
    }
    private fun syncWeekRangeAndActiveMonthFromCarousel() {
        if (!isAdded) return
        isCarouselHeaderSyncQueued = false

        val hsv = hsvDaysCarousel ?: return
        val llDays = llDaysContainer ?: return
        
        val scrollX = hsv.scrollX
        val viewportWidth = hsv.width
        val centerX = scrollX + (viewportWidth / 2)

        var closestWeekIndex = 0
        var minDistance = Int.MAX_VALUE

        for (w in 0 until llDays.childCount) {
            val weekView = llDays.getChildAt(w)
            val weekCenter = weekView.left + (weekView.width / 2)
            val dist = Math.abs(centerX - weekCenter)
            if (dist < minDistance) {
                minDistance = dist
                closestWeekIndex = w
            }
        }

        val weekIndexBounds = closestWeekIndex * 7
        if (weekIndexBounds >= 0 && weekIndexBounds < allDayDateKeys.size) {
            val weekStartKey = allDayDateKeys[weekIndexBounds]
            if (carouselFocusedWeekStartKey != weekStartKey) {
                carouselFocusedWeekStartKey = weekStartKey
                try {
                    val startDate = dateKeyFormat.parse(weekStartKey)
                    if (startDate != null) {
                        val cal = Calendar.getInstance()
                        cal.time = startDate
                        cal.add(Calendar.DAY_OF_MONTH, 6)
                        val endKey = dateKeyFormat.format(cal.time)

                        val fmt = SimpleDateFormat("MMM", Locale.getDefault())
                        val monthStart = fmt.format(startDate)
                        val monthEnd = fmt.format(cal.time)
                        
                        val startNum = weekStartKey.substring(weekStartKey.lastIndexOf('-') + 1)
                        val endNum = endKey.substring(endKey.lastIndexOf('-') + 1)
                        
                        val labelText = if (monthStart == monthEnd) {
                            "$startNum - $endNum $monthStart"
                        } else {
                            "$startNum $monthStart - $endNum $monthEnd"
                        }
                        
                        tvDateRange?.text = labelText.uppercase(Locale.getDefault())
                    }
                } catch (ignored: Exception) {}
            }
        }

        for (c in 0 until allDayCards.size) {
            val colView = allDayCards[c].parent as View
            val parentView = colView.parent as View
            // Calculate absolute left within the llDaysContainer
            val colLeft = colView.left + parentView.left
            
            if (colLeft >= scrollX) {
                if (activeMonthLabelIndex != c) {
                    if (activeMonthLabelIndex in allDayMonthLabels.indices) {
                        allDayMonthLabels[activeMonthLabelIndex].visibility = View.INVISIBLE
                    }
                    if (c in allDayMonthLabels.indices) {
                        allDayMonthLabels[c].visibility = View.VISIBLE
                    }
                    activeMonthLabelIndex = c
                }
                break
            }
        }
    }

    private fun showWeekCalendarPicker() {
        val datePicker = android.app.DatePickerDialog(
            requireContext(),
            { _, year, month, dayOfMonth ->
                val picked = Calendar.getInstance()
                picked.set(year, month, dayOfMonth, 0, 0, 0)
                val pickedKey = dateKeyFormat.format(picked.time)
                scrollCarouselToDate(pickedKey)
                selectDayByDate(pickedKey, picked.clone() as Calendar)
            },
            todayDate.get(Calendar.YEAR),
            todayDate.get(Calendar.MONTH),
            todayDate.get(Calendar.DAY_OF_MONTH)
        )
        
        datePicker.datePicker.minDate = todayDate.timeInMillis
        val maxDate = todayDate.clone() as Calendar
        maxDate.add(Calendar.WEEK_OF_YEAR, WEEKS_AHEAD)
        datePicker.datePicker.maxDate = maxDate.timeInMillis
        datePicker.show()
    }

    private fun scrollCarouselToDate(dateKey: String) {
        val pos = allDayDateKeys.indexOf(dateKey)
        if (pos >= 0 && pos < allDayCards.size) {
            val card = allDayCards[pos]
            val parent = card.parent as? View ?: return
            val scrollX = Math.max(0, parent.left - dpToPx(12))
            hsvDaysCarousel?.post {
                hsvDaysCarousel?.smoothScrollTo(scrollX, 0)
            }
        }
    }

    private fun selectDayByDate(dateKey: String, date: Calendar) {
        val pos = allDayDateKeys.indexOf(dateKey)
        if (pos >= 0 && pos < allDayCards.size) {
            selectDayCard(allDayCards[pos], dateKey, date)
        }
    }

    private fun createDayCard(label: String, dayNumber: String): MaterialCardView {
        val card = MaterialCardView(requireContext()).apply {
            setCardBackgroundColor(Color.parseColor("#111319"))
            radius = dpToPx(12).toFloat()
            strokeWidth = 0
            useCompatPadding = false
        }

        val content = LinearLayout(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            )
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }

        val tvLabel = TextView(requireContext()).apply {
            text = label
            textSize = 11f
            setTextColor(Color.parseColor("#BDBDBD"))
            gravity = Gravity.CENTER
        }

        val tvNumber = TextView(requireContext()).apply {
            text = dayNumber
            textSize = 18f
            setTextColor(Color.WHITE)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dpToPx(4)
            }
        }

        content.addView(tvLabel)
        content.addView(tvNumber)
        card.addView(content)

        allDayLabels.add(tvLabel)
        allDayNumbers.add(tvNumber)
        return card
    }

    private fun dpToPx(dp: Int): Int {
        return Math.round(dp * resources.displayMetrics.density)
    }

    private fun showAddTaskDialog(taskToEdit: Task?, taskDateKey: String?) {
        if (!isAdded || context == null) return
        
        val ctx = context ?: return
        val bottomSheetDialog = BottomSheetDialog(ctx)
        val sheetView = LayoutInflater.from(ctx).inflate(R.layout.dialog_add_task, null)
        if (sheetView == null) return
        
        bottomSheetDialog.setContentView(sheetView)

        val tvTitleLabel = sheetView.findViewById<TextView>(R.id.tvDialogTitle)
        val etTitle = sheetView.findViewById<TextInputEditText>(R.id.etTaskTitle)
        val etDesc = sheetView.findViewById<TextInputEditText>(R.id.etTaskDesc)
        val tvTaskDay = sheetView.findViewById<TextView>(R.id.tvTaskDay)
        val cardDayDisplay = sheetView.findViewById<MaterialCardView>(R.id.cardDayDisplay)
        val btnSaveTask = sheetView.findViewById<MaterialButton>(R.id.btnSaveTask)

        val isEditMode = taskToEdit != null
        if (isEditMode) {
            tvTitleLabel?.text = "Editar tarea"
            etTitle?.setText(taskToEdit?.title)
            etDesc?.setText(if (isPlaceholderDescription(taskToEdit?.desc)) "" else taskToEdit?.desc)
            btnSaveTask?.text = "Actualizar Tarea"
        } else {
            tvTitleLabel?.text = "Nueva tarea"
            btnSaveTask?.text = "Guardar Tarea"
        }

        var dialogDateKey = if (isEditMode) {
            taskDateKey ?: selectedDateKey ?: dateKeyFormat.format(todayDate.time)
        } else {
            selectedDateKey ?: dateKeyFormat.format(todayDate.time)
        }

        // Extremely safe date initialization for the dialog
        var dialogDate: Calendar = if (isEditMode) {
            parseDateFromKey(taskDateKey) ?: (selectedDate?.clone() as? Calendar ?: todayDate.clone() as Calendar)
        } else {
            selectedDate?.clone() as? Calendar ?: todayDate.clone() as Calendar
        }

        tvTaskDay?.let { updateDayDisplay(it, dialogDate) }

        cardDayDisplay?.setOnClickListener {
            val initial = dialogDate
            val datePicker = android.app.DatePickerDialog(
                requireContext(),
                { _, year, month, dayOfMonth ->
                    val picked = Calendar.getInstance()
                    picked.set(year, month, dayOfMonth, 0, 0, 0)
                    dialogDateKey = dateKeyFormat.format(picked.time)
                    dialogDate = picked
                    updateDayDisplay(tvTaskDay, picked)
                },
                initial.get(Calendar.YEAR),
                initial.get(Calendar.MONTH),
                initial.get(Calendar.DAY_OF_MONTH)
            )
            datePicker.datePicker.minDate = todayDate.timeInMillis
            val maxDate = todayDate.clone() as Calendar
            maxDate.add(Calendar.WEEK_OF_YEAR, WEEKS_AHEAD)
            datePicker.datePicker.maxDate = maxDate.timeInMillis
            datePicker.show()
        }

        etTitle?.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEND ||
                actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE) {
                btnSaveTask?.performClick()
                true
            } else {
                false
            }
        }

        btnSaveTask?.setOnClickListener {
            val titleText = etTitle?.text?.toString()?.trim() ?: ""
            if (titleText.isEmpty()) return@setOnClickListener

            val userDescription = etDesc?.text?.toString()?.trim() ?: ""
            val allowDescriptionOverride = userDescription.isEmpty()
            val resolvedDescription = if (allowDescriptionOverride) PENDING_TASK_DESCRIPTION else userDescription

            val saveKey = dialogDateKey ?: ""
            if (saveKey.isEmpty()) return@setOnClickListener

            if (isEditMode && taskToEdit != null) {
                if (taskDateKey != null && taskDateKey != saveKey) {
                    tasksPerDay[taskDateKey]?.remove(taskToEdit)
                }

                taskToEdit.title = titleText
                taskToEdit.desc = resolvedDescription

                val currentTasks = tasksPerDay.getOrPut(saveKey) { mutableListOf() }
                if (!currentTasks.contains(taskToEdit)) {
                    currentTasks.add(taskToEdit)
                }

                persistAgenda()
                renderFutureTasksLayer()
                selectDayByDate(saveKey, dialogDate)
                bottomSheetDialog.dismiss()

                if (allowDescriptionOverride || isPlaceholderCategory(taskToEdit.category)) {
                    requestTaskMetadataEnrichment(saveKey, taskToEdit, allowDescriptionOverride)
                }
            } else {
                val currentTasks = tasksPerDay.getOrPut(saveKey) { mutableListOf() }
                val createdTask = Task(titleText, resolvedDescription, "", PENDING_TASK_CATEGORY)
                currentTasks.add(createdTask)
                
                persistAgenda()
                renderFutureTasksLayer()
                selectDayByDate(saveKey, dialogDate)
                bottomSheetDialog.dismiss()

                requestTaskMetadataEnrichment(saveKey, createdTask, allowDescriptionOverride)
            }
        }

        bottomSheetDialog.show()
    }
    private fun requestTaskMetadataEnrichment(dateKey: String, task: Task, allowDescriptionOverride: Boolean) {
        val requestKey = buildTaskMetadataRequestKey(dateKey, task)
        if (!pendingTaskMetadataRequests.add(requestKey)) return
        enrichTaskWithAiMetadataInBackground(dateKey, task, allowDescriptionOverride, 0, requestKey)
    }

    private fun enrichTaskWithAiMetadataInBackground(
        dateKey: String,
        task: Task,
        allowDescriptionOverride: Boolean,
        retryAttempt: Int,
        requestKey: String
    ) {
        if (GeminiIntelligenceService.isSuspended()) {
            val targetTask = findTaskInDateBucket(dateKey, task)
            if (targetTask != null) {
                applyLocalMetadataFallback(targetTask, allowDescriptionOverride)
                persistAgenda()
                renderFutureTasksLayer()
                refreshSelectedDayTasks()
            }
            markTaskMetadataRequestFinished(requestKey)
            return
        }

        GeminiIntelligenceService().generateTaskMetadataWithCategory(task.title, object : GeminiIntelligenceService.TaskMetadataCallback {
            override fun onSuccess(metadata: GeminiIntelligenceService.TaskMetadata) {
                val targetTask = findTaskInDateBucket(dateKey, task)
                if (targetTask == null) {
                    markTaskMetadataRequestFinished(requestKey)
                    return
                }

                var changed = false
                if (isPlaceholderCategory(targetTask.category)) {
                    val normalizedCategory = normalizeTaskCategory(metadata.category)
                    if (normalizedCategory.isNotEmpty() && targetTask.category != normalizedCategory) {
                        targetTask.category = normalizedCategory
                        changed = true
                    }
                }

                if (allowDescriptionOverride && isPlaceholderDescription(targetTask.desc)) {
                    val cleanDescription = metadata.description?.trim() ?: ""
                    if (cleanDescription.isNotEmpty() && targetTask.desc != cleanDescription) {
                        targetTask.desc = cleanDescription
                        changed = true
                    }
                }

                if (changed) {
                    persistAgenda()
                    renderFutureTasksLayer()
                    refreshSelectedDayTasks()
                }
                markTaskMetadataRequestFinished(requestKey)
            }

            override fun onError(error: String) {
                val targetTask = findTaskInDateBucket(dateKey, task)
                if (targetTask == null) {
                    markTaskMetadataRequestFinished(requestKey)
                    return
                }

                if (applyIntelligentLocalFallback(targetTask, allowDescriptionOverride)) {
                    persistAgenda()
                    renderFutureTasksLayer()
                    refreshSelectedDayTasks()
                }
                markTaskMetadataRequestFinished(requestKey)
            }
        })
    }

    private fun applyLocalMetadataFallback(task: Task, allowDescriptionOverride: Boolean): Boolean {
        var changed = false
        if (allowDescriptionOverride && isPlaceholderDescription(task.desc)) {
            if (task.desc != TASK_METADATA_ERROR_DESCRIPTION) {
                task.desc = TASK_METADATA_ERROR_DESCRIPTION
                changed = true
            }
        }
        if (isPlaceholderCategory(task.category)) {
            if (task.category != TASK_METADATA_ERROR_CATEGORY) {
                task.category = TASK_METADATA_ERROR_CATEGORY
                changed = true
            }
        }
        return changed
    }

    private fun applyIntelligentLocalFallback(task: Task, allowDescriptionOverride: Boolean): Boolean {
        var changed = false
        if (allowDescriptionOverride && isPlaceholderDescription(task.desc)) {
            val fallbackDesc = buildLocalTaskDescription(task.title)
            if (task.desc != fallbackDesc) {
                task.desc = fallbackDesc
                changed = true
            }
        }
        if (isPlaceholderCategory(task.category)) {
            val fallbackCat = buildLocalTaskCategory(task.title)
            if (task.category != fallbackCat) {
                task.category = fallbackCat
                changed = true
            }
        }
        return changed
    }

    private fun buildLocalTaskDescription(taskTitle: String?): String {
        val cleanTitle = taskTitle?.trim()?.replace(Regex("[\\.;:,]+$"), "")?.trim() ?: ""
        return if (cleanTitle.isEmpty()) {
            "Definir un siguiente paso claro y completar esta tarea hoy."
        } else {
            "Completar $cleanTitle con un paso concreto y medible."
        }
    }

    private fun buildLocalTaskCategory(taskTitle: String?): String {
        if (taskTitle.isNullOrEmpty()) return "Pendiente"
        
        val normalized = taskTitle.lowercase(SPANISH_LOCALE)
            .replace(Regex("[^\\p{L}\\p{N} ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            
        if (normalized.isEmpty()) return "Pendiente"

        val stopwords = setOf(
            "de", "la", "el", "los", "las", "un", "una", "unos", "unas",
            "y", "o", "en", "con", "para", "por", "del", "al", "a", "mi",
            "tu", "su", "hoy", "manana", "mañana", "tarea", "tareas"
        )

        for (token in normalized.split(" ")) {
            if (token.length < 3 || stopwords.contains(token)) continue
            val candidate = normalizeTaskCategory(token)
            if (candidate.isNotEmpty()) return candidate
        }
        return "Pendiente"
    }

    private fun markTaskMetadataRequestFinished(requestKey: String) {
        pendingTaskMetadataRequests.remove(requestKey)
    }

    private fun findTaskInDateBucket(dateKey: String, fallbackTask: Task): Task? {
        val dayTasks = tasksPerDay[dateKey] ?: return null
        
        for (task in dayTasks) {
            if (task === fallbackTask) return task
        }

        val normalizedCategoryFallback = normalizeTaskCategory(fallbackTask.category)
        for (task in dayTasks) {
            val normalizedCategoryTask = normalizeTaskCategory(task.category)
            val sameCategory = normalizedCategoryTask == normalizedCategoryFallback
            val flexibleDescription = isPlaceholderDescription(task.desc) || isPlaceholderDescription(fallbackTask.desc)
            
            if (task.title == fallbackTask.title && task.time == fallbackTask.time &&
                (task.desc == fallbackTask.desc || sameCategory || flexibleDescription)
            ) {
                return task
            }
        }
        return null
    }

    private fun isPlaceholderDescription(description: String?): Boolean {
        if (description.isNullOrEmpty()) return true
        val normalized = description.trim()
        return normalized.isEmpty() ||
                normalized.equals(DEFAULT_TASK_DESCRIPTION, ignoreCase = true) ||
                normalized.equals(PENDING_TASK_DESCRIPTION, ignoreCase = true) ||
                normalized.equals(TASK_METADATA_ERROR_DESCRIPTION, ignoreCase = true) ||
                isLocalFallbackDescription(normalized)
    }

    private fun isLocalFallbackDescription(description: String?): Boolean {
        if (description.isNullOrEmpty()) return false
        val normalized = description.trim()
        if (normalized.isEmpty()) return false
        val lower = normalized.lowercase(SPANISH_LOCALE)
        return lower.startsWith("organizar y completar:") ||
                lower == "organizar esta tarea y completarla durante el dia." ||
                lower == "organizar esta tarea y completarla durante el dia"
    }

    private fun isPlaceholderCategory(category: String?): Boolean {
        if (category.isNullOrEmpty()) return true
        val raw = category.trim()
        if (raw.isEmpty()) return true
        
        val lowerRaw = raw.lowercase(SPANISH_LOCALE)
        if (raw.equals(PENDING_TASK_CATEGORY, ignoreCase = true) ||
            raw.equals(FALLBACK_TASK_CATEGORY, ignoreCase = true) ||
            raw.equals(TASK_METADATA_ERROR_CATEGORY, ignoreCase = true) ||
            lowerRaw.startsWith("generando")
        ) {
            return true
        }

        val normalized = normalizeTaskCategory(raw)
        if (normalized.isEmpty()) return true
        
        val lower = normalized.lowercase(Locale.US)
        return lower in listOf("otros", "otro", "organizacion", "organización", "general", "misc", "sin") ||
                lower.startsWith("generando")
    }

    private fun shouldGenerateTaskMetadata(task: Task): Boolean {
        return isPlaceholderDescription(task.desc) || isPlaceholderCategory(task.category)
    }

    private fun buildTaskMetadataRequestKey(dateKey: String, task: Task): String {
        return "$dateKey|${task.title.trim()}|${task.desc.trim()}"
    }

    private fun scheduleBackfillForIncompleteTaskMetadata(includePastDays: Boolean = false, maxRequests: Int = TASK_METADATA_BACKFILL_LIMIT) {
        if (tasksPerDay.isEmpty()) return

        var requested = 0
        val todayKey = dateKeyFormat.format(Calendar.getInstance().time)
        
        for ((dateKey, dayTasks) in TreeMap(tasksPerDay)) {
            if (!includePastDays && dateKey < todayKey) continue
            if (dayTasks.isEmpty()) continue

            for (task in dayTasks) {
                if (task.title.isEmpty() || !shouldGenerateTaskMetadata(task)) continue

                requestTaskMetadataEnrichment(dateKey, task, isPlaceholderDescription(task.desc))
                requested++
                if (maxRequests in 1..requested) return
            }
        }
    }

    private fun updateDayDisplay(tvTaskDay: TextView, date: Calendar) {
        val dayName = getDayFullLabel(date.get(Calendar.DAY_OF_WEEK))
        val dayNum = date.get(Calendar.DAY_OF_MONTH)
        val monthFmt = SimpleDateFormat("MMMM", Locale.getDefault())
        val monthName = monthFmt.format(date.time)
        tvTaskDay.text = "$dayName $dayNum de $monthName"
        tvTaskDay.setTextColor(Color.WHITE)
        tvTaskDay.textSize = 16f
    }

    fun onCloudAgendaHydrationCompleted() {
        if (!isAdded) return
        loadAgendaFromLocal(true)
        refreshSelectedDayTasks()
    }

    private fun loadAgendaFromLocal(forceRefresh: Boolean = false) {
        val mgr = cloudSyncManager ?: return
        val rawAgendaJson = mgr.localAgendaJson ?: ""
        val now = System.currentTimeMillis()
        val sameAgendaPayload = lastLoadedAgendaJson == rawAgendaJson

        if (!forceRefresh && sameAgendaPayload && tasksPerDay.isNotEmpty()) {
            clearLockedFutureInsightIfResolved()
            renderFutureTasksLayer()
            maybeScheduleTaskMetadataBackfill(forceRefresh, sameAgendaPayload, now)
            return
        }

        tasksPerDay.clear()
        tasksPerDay.putAll(parseAgendaJson(rawAgendaJson))
        lastLoadedAgendaJson = rawAgendaJson
        lastAgendaLocalRefreshAtMs = now

        if (removeLegacyApolloTasks(tasksPerDay)) {
            persistAgenda()
        }
        
        clearLockedFutureInsightIfResolved()
        
        if (forceRefresh || !sameAgendaPayload || (now - lastAgendaReminderRescheduleAtMs) >= AGENDA_REMINDER_RESCHEDULE_MIN_INTERVAL_MS) {
            TaskReminderScheduler.rescheduleAll(requireContext().applicationContext)
            lastAgendaReminderRescheduleAtMs = now
        }
        
        renderFutureTasksLayer()
        maybeScheduleTaskMetadataBackfill(forceRefresh, sameAgendaPayload, now)
    }

    private fun maybeScheduleTaskMetadataBackfill(forceRefresh: Boolean, sameAgendaPayload: Boolean, now: Long) {
        if (!forceRefresh) return
        lastTaskMetadataBackfillAtMs = now
        scheduleBackfillForIncompleteTaskMetadata()
    }

    private fun removeLegacyApolloTasks(data: MutableMap<String, MutableList<Task>>): Boolean {
        var removedAny = false
        val dayIterator = data.iterator()
        while (dayIterator.hasNext()) {
            val entry = dayIterator.next()
            val dayTasks = entry.value
            if (dayTasks.isEmpty()) continue

            val taskIterator = dayTasks.iterator()
            while (taskIterator.hasNext()) {
                val task = taskIterator.next()
                if (task.title.trim().equals(LEGACY_DEMO_TITLE, ignoreCase = true)) {
                    taskIterator.remove()
                    removedAny = true
                }
            }

            if (dayTasks.isEmpty()) {
                dayIterator.remove()
            }
        }
        return removedAny
    }

    private fun refreshSelectedDayTasks() {
        if (selectedDateKey != null && selectedDate != null) {
            selectDayByDate(selectedDateKey!!, selectedDate!!.clone() as Calendar)
        }
    }

    private fun setFabAuthLocked(locked: Boolean) {
        fabLockedForAuth = locked
        fabAddTask?.apply {
            isEnabled = !locked
            alpha = if (locked) 0.55f else 1f
        }
        cardFabHint?.visibility = View.VISIBLE
        tvFabHint?.text = if (locked) "Sincronizando cuenta..." else "Inicia sesion para usar +"
        
        if (!locked) {
            cardFabHint?.visibility = View.GONE
        }
    }

    private fun showSubtleMessage(message: String) {
        view?.let { Snackbar.make(it, message, Snackbar.LENGTH_SHORT).show() }
    }

    private fun resolveProfileNameForAi(): String {
        return (activity as? MainActivity)?.getAuthManager()?.let {
            if (it.isSignedIn()) it.getDisplayName() else "usuario"
        } ?: "usuario"
    }

    private fun buildBehaviorSignalsForAi(): String {
        val todayKey = dateKeyFormat.format(Calendar.getInstance().time)
        var upcomingDaysWithTasks = 0
        var tasksWithoutTime = 0
        var tasksWithTime = 0

        for ((dateKey, dayTasks) in TreeMap(tasksPerDay)) {
            if (dateKey < todayKey || dayTasks.isEmpty()) continue
            upcomingDaysWithTasks++
            for (task in dayTasks) {
                if (task.time.isEmpty()) tasksWithoutTime++ else tasksWithTime++
            }
        }
        return "dias_con_tareas=$upcomingDaysWithTasks, tareas_con_hora=$tasksWithTime, tareas_sin_hora=$tasksWithoutTime"
    }

    private fun buildAgendaSnapshotForAi(): String {
        val todayKey = dateKeyFormat.format(Calendar.getInstance().time)
        var totalUpcomingTasks = 0
        var morningTasks = 0
        var afternoonTasks = 0
        var nightTasks = 0
        val keywordCounts = HashMap<String, Int>()
        val examples = ArrayList<String>()

        for ((date, dayTasks) in TreeMap(tasksPerDay)) {
            if (date < todayKey || dayTasks.isEmpty()) continue

            for (task in dayTasks) {
                totalUpcomingTasks++
                val hour = parseHourFromTaskTime(task.time)
                if (hour >= 0) {
                    when {
                        hour in 5..11 -> morningTasks++
                        hour in 12..18 -> afternoonTasks++
                        else -> nightTasks++
                    }
                }

                accumulateKeywords("${task.title} ${task.desc}", keywordCounts)

                if (examples.size < 14) {
                    examples.add("$date - ${task.title}")
                }
            }
        }

        val topKeywords = buildTopKeywords(keywordCounts, 6)
        return "total_tareas=$totalUpcomingTasks; franjas{manana=$morningTasks, tarde=$afternoonTasks, noche=$nightTasks}; top_keywords=$topKeywords; ejemplos=$examples"
    }

    private fun parseHourFromTaskTime(rawTime: String?): Int {
        if (rawTime.isNullOrEmpty()) return -1
        return try {
            val value = rawTime.trim().uppercase(Locale.US)
            val isPm = value.contains("PM")
            val isAm = value.contains("AM")
            val numeric = value.replace("AM", "").replace("PM", "").trim()
            var hour = numeric.split(":")[0].trim().toInt()
            if (isPm && hour < 12) hour += 12
            if (isAm && hour == 12) hour = 0
            hour.coerceIn(0, 23)
        } catch (ignored: Exception) {
            -1
        }
    }

    private fun isDaytimeHour(hour24: Int): Boolean {
        return hour24 in 6..18
    }

    private fun accumulateKeywords(text: String, bucket: MutableMap<String, Int>) {
        val stopwords = setOf(
            "de", "la", "el", "y", "en", "con", "para", "por", "del", "las", "los",
            "una", "uno", "que", "sin", "hoy", "tarea", "tareas", "sesion", "proyecto"
        )
        val normalized = text.lowercase(Locale.US).replace(Regex("[^a-z0-9áéíóúñ ]"), " ")
        for (word in normalized.split("\\s+".toRegex())) {
            if (word.length < 4 || stopwords.contains(word)) continue
            bucket[word] = bucket.getOrDefault(word, 0) + 1
        }
    }

    private fun buildTopKeywords(keywordCounts: Map<String, Int>, maxItems: Int): String {
        if (keywordCounts.isEmpty()) return "sin_datos"
        return keywordCounts.entries.sortedByDescending { it.value }
            .take(maxItems)
            .joinToString(",") { it.key }
    }

    private fun persistAgenda() {
        val mgr = cloudSyncManager ?: return
        mgr.syncAgendaJson(serializeAgendaJson(tasksPerDay))
        TaskReminderScheduler.rescheduleAll(requireContext().applicationContext)
    }

    private fun serializeAgendaJson(data: Map<String, List<Task>>): String {
        return try {
            val root = JSONObject()
            for ((key, tasks) in data) {
                val tasksArr = JSONArray()
                for (task in tasks) {
                    val taskObj = JSONObject().apply {
                        put("title", task.title)
                        put("desc", task.desc)
                        put("time", task.time)
                        put("category", normalizeTaskCategory(task.category))
                    }
                    tasksArr.put(taskObj)
                }
                root.put(key, tasksArr)
            }
            root.toString()
        } catch (ignored: Exception) {
            "{}"
        }
    }

    private fun parseAgendaJson(rawJson: String?): MutableMap<String, MutableList<Task>> {
        val parsed = HashMap<String, MutableList<Task>>()
        if (rawJson.isNullOrEmpty()) return parsed

        try {
            val root = JSONObject(rawJson)
            for (dateKey in root.keys()) {
                val tasksArray = root.optJSONArray(dateKey) ?: continue
                val dayTasks = mutableListOf<Task>()

                for (i in 0 until tasksArray.length()) {
                    val taskObj = tasksArray.optJSONObject(i) ?: continue
                    val title = taskObj.optString("title", "").trim()
                    if (title.isEmpty()) continue

                    val desc = taskObj.optString("desc", "Sin detalles adicionales").trim()
                    val time = taskObj.optString("time", "").trim()
                    val category = normalizeTaskCategory(taskObj.optString("category", DEFAULT_TASK_CATEGORY))

                    dayTasks.add(Task(title, if (desc.isEmpty()) DEFAULT_TASK_DESCRIPTION else desc, time, category))
                }

                if (dayTasks.isNotEmpty()) {
                    parsed[dateKey] = dayTasks
                }
            }
        } catch (ignored: Exception) {}
        return parsed
    }
    @SuppressLint("SetJavaScriptEnabled")
    private fun configureFutureTasksWebView() {
        // WebView removed in native migration
    }


    private fun handleFutureTasksActionUrl(rawUrl: String?): Boolean {
        if (rawUrl.isNullOrEmpty()) return true

        val uri = try {
            Uri.parse(rawUrl)
        } catch (ignored: Exception) {
            return true
        }

        if (!FUTURE_INSIGHT_ACTION_SCHEME.equals(uri.scheme, ignoreCase = true)) return true

        val host = uri.host
        val path = uri.path

        if (FUTURE_INSIGHT_ACTION_HOST.equals(host, ignoreCase = true) && FUTURE_INSIGHT_ACTION_PATH.equals(path, ignoreCase = true)) {
            val mode = uri.getQueryParameter("mode")
            val dateKey = uri.getQueryParameter("date")
            val taskTitle = uri.getQueryParameter("task")
            handleFutureInsightAction(mode, dateKey, taskTitle)
            return true
        }

        if (FUTURE_TASK_ACTION_HOST.equals(host, ignoreCase = true) && FUTURE_TASK_ACTION_PATH.equals(path, ignoreCase = true)) {
            val dateKey = uri.getQueryParameter("date")
            val taskTitle = uri.getQueryParameter("task")
            val taskTime = uri.getQueryParameter("time")
            val xStr = uri.getQueryParameter("x")
            val yStr = uri.getQueryParameter("y")
            val x = xStr?.toFloatOrNull()
            val y = yStr?.toFloatOrNull()
            handleFutureTaskCardAction(dateKey, taskTitle, taskTime, x, y)
            return true
        }

        if (FUTURE_TASK_ACTION_HOST.equals(host, ignoreCase = true) && FUTURE_TASK_ADD_PATH.equals(path, ignoreCase = true)) {
            triggerAddTaskFlow()
            return true
        }

        return true
    }

    private fun handleFutureTaskCardAction(dateKey: String?, taskTitle: String?, taskTime: String?, x: Float?, y: Float?) {
        val selection = resolveFutureTaskSelection(dateKey, taskTitle, taskTime)
        if (selection == null) {
            showSubtleMessage(getString(R.string.agenda_task_not_found_toast))
            return
        }

        val anchorView = rvFutureTasksLayer ?: view
        showTaskActionTooltip(anchorView, selection, x, y)
    }


    private fun handleFutureInsightAction(mode: String?, dateKey: String?, taskTitle: String?) {
        if (mode == FUTURE_INSIGHT_MODE_SCHEDULE) {
            val selection = resolveScheduleTaskSelection(dateKey, taskTitle)
            if (selection == null) {
                showSubtleMessage("No hay tareas sin horario pendientes en este momento.")
                return
            }
            showSystemTimePickerForInsightTask(selection)
            return
        }

        val microAction = currentFutureInsightModel?.microAction?.takeIf { it.isNotEmpty() }
            ?: "Inicia un bloque de enfoque de 25 minutos para tu tarea mas importante."
        showSubtleMessage(microAction)
    }

    private fun resolveScheduleTaskSelection(dateKey: String?, taskTitle: String?): TaskSelection? {
        if (!dateKey.isNullOrEmpty() && !taskTitle.isNullOrEmpty()) {
            val dayTasks = tasksPerDay[dateKey]
            if (dayTasks != null) {
                dayTasks.find { it.title == taskTitle && it.time.isEmpty() }?.let { return TaskSelection(dateKey, it) }
                dayTasks.find { it.title == taskTitle }?.let { return TaskSelection(dateKey, it) }
            }
        }
        return findFirstTaskWithoutTimeFromToday()
    }

    private fun resolveFutureTaskSelection(dateKey: String?, taskTitle: String?, taskTime: String?): TaskSelection? {
        if (dateKey.isNullOrEmpty() || taskTitle.isNullOrEmpty()) return null
        val dayTasks = tasksPerDay[dateKey]
        if (dayTasks.isNullOrEmpty()) return null

        val normalizedTime = taskTime?.trim() ?: ""
        dayTasks.find { it.title == taskTitle && (it.time.trim() ?: "") == normalizedTime }?.let { return TaskSelection(dateKey, it) }
        dayTasks.find { it.title == taskTitle }?.let { return TaskSelection(dateKey, it) }
        return null
    }

    private fun showTaskActionTooltip(anchor: View?, selection: TaskSelection, rawTouchX: Float? = null, rawTouchY: Float? = null) {
        if (!isAdded) return

        val targetTask = findTaskInDateBucket(selection.dateKey, selection.task)
        if (targetTask == null) {
            showSubtleMessage(getString(R.string.agenda_task_not_found_toast))
            return
        }

        val menuAnchor = anchor ?: rvTasks ?: return
        dismissTaskActionTooltip()

        val contentView = LayoutInflater.from(requireContext()).inflate(R.layout.view_task_action_tooltip, null, false)
        contentView.findViewById<View>(R.id.llTaskTooltipEdit)?.setOnClickListener {
            dismissTaskActionTooltip()
            showAddTaskDialog(targetTask, selection.dateKey)
        }

        val hasTime = hasTaskScheduledTime(targetTask)
        contentView.findViewById<View>(R.id.llTaskTooltipSetTime)?.apply {
            visibility = if (hasTime) View.GONE else View.VISIBLE
            setOnClickListener {
                dismissTaskActionTooltip()
                showSystemTimePickerForInsightTask(selection)
            }
        }
        contentView.findViewById<View>(R.id.dividerTaskTooltip)?.visibility = if (hasTime) View.GONE else View.VISIBLE

        contentView.findViewById<View>(R.id.llTaskTooltipDelete)?.setOnClickListener {
            dismissTaskActionTooltip()
            showDeleteTaskConfirmation(selection)
        }

        val popupWindow = PopupWindow(contentView, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, true).apply {
            isOutsideTouchable = true
            isFocusable = false
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                elevation = dpToPx(8).toFloat()
            }
            setOnDismissListener {
                if (taskActionPopupWindow == this) taskActionPopupWindow = null
            }
        }
        taskActionPopupWindow = popupWindow

        if (rawTouchX != null && rawTouchY != null && view != null && !rawTouchX.isNaN() && !rawTouchY.isNaN()) {
            val rootView = requireView()
            val rootLocation = IntArray(2)
            rootView.getLocationOnScreen(rootLocation)

            contentView.measure(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
            val popupWidth = contentView.measuredWidth
            val popupHeight = contentView.measuredHeight
            val margin = dpToPx(12)

            var desiredX = Math.round(rawTouchX) + dpToPx(8)
            var desiredY = Math.round(rawTouchY) + dpToPx(8)

            if (desiredY + popupHeight > rootLocation[1] + rootView.height - margin) {
                desiredY = Math.round(rawTouchY) - popupHeight - dpToPx(12)
            }
            if (desiredX + popupWidth > rootLocation[0] + rootView.width - margin) {
                desiredX = Math.round(rawTouchX) - popupWidth - dpToPx(8)
            }

            val clampedX = Math.max(rootLocation[0] + margin, Math.min(desiredX, rootLocation[0] + rootView.width - popupWidth - margin))
            val clampedY = Math.max(rootLocation[1] + margin, Math.min(desiredY, rootLocation[1] + rootView.height - popupHeight - margin))

            popupWindow.showAtLocation(rootView, Gravity.NO_GRAVITY, clampedX, clampedY)
            return
        }

        val xOffset = dpToPx(8)
        val yOffset = dpToPx(4)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            popupWindow.showAsDropDown(menuAnchor, xOffset, yOffset, Gravity.START)
        } else {
            popupWindow.showAsDropDown(menuAnchor, xOffset, yOffset)
        }
    }

    private fun dismissTaskActionTooltip() {
        taskActionPopupWindow?.dismiss()
        taskActionPopupWindow = null
    }

    private fun hasTaskScheduledTime(task: Task): Boolean {
        return parseHourFromTaskTime(task.time) >= 0
    }

    private fun showDeleteTaskConfirmation(selection: TaskSelection) {
        if (!isAdded) return

        val targetTask = findTaskInDateBucket(selection.dateKey, selection.task)
        if (targetTask == null) {
            showSubtleMessage(getString(R.string.agenda_task_not_found_toast))
            return
        }

        val safeTitle = targetTask.title.takeIf { it.isNotEmpty() } ?: "tarea"
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.agenda_task_delete_title)
            .setMessage(getString(R.string.agenda_task_delete_confirm_message, safeTitle))
            .setNegativeButton(R.string.agenda_action_cancel, null)
            .setPositiveButton(R.string.agenda_action_delete) { _, _ -> deleteTaskFromAgenda(selection) }
            .show()
    }

    private fun deleteTaskFromAgenda(selection: TaskSelection) {
        val targetTask = findTaskInDateBucket(selection.dateKey, selection.task)
        if (targetTask == null) {
            showSubtleMessage(getString(R.string.agenda_task_not_found_toast))
            return
        }

        val dayTasks = tasksPerDay[selection.dateKey]
        if (dayTasks == null || dayTasks.isEmpty()) {
            showSubtleMessage(getString(R.string.agenda_task_not_found_toast))
            return
        }

        var removed = dayTasks.remove(targetTask)
        if (!removed) {
            val fallbackIndex = dayTasks.indexOfFirst {
                it.title == targetTask.title && it.time == targetTask.time &&
                it.desc == targetTask.desc && it.category == targetTask.category
            }
            if (fallbackIndex >= 0) {
                dayTasks.removeAt(fallbackIndex)
                removed = true
            }
        }

        if (!removed) {
            showSubtleMessage(getString(R.string.agenda_task_not_found_toast))
            return
        }

        if (dayTasks.isEmpty()) tasksPerDay.remove(selection.dateKey)
        persistAgenda()
        currentFutureInsightModel = null
        renderFutureTasksLayer()

        if (selectedDateKey == selection.dateKey) refreshSelectedDayTasks()

        val safeTitle = targetTask.title.takeIf { it.isNotEmpty() }?.trim() ?: "tarea"
        showSubtleMessage(getString(R.string.agenda_task_deleted_toast, safeTitle))
    }
    private fun showSystemTimePickerForInsightTask(selection: TaskSelection) {
        if (!isAdded) return

        insightTimeDialog?.takeIf { it.isShowing }?.dismiss()

        val dialog = Dialog(requireContext())
        dialog.setContentView(R.layout.dialog_insight_time_picker)
        dialog.setCancelable(true)

        dialog.window?.apply {
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setDimAmount(0.68f)
        }

        val root = dialog.findViewById<View>(R.id.insightDialogRoot)
        val card = dialog.findViewById<View>(R.id.cardInsightTimePicker)
        val tvTaskTitle = dialog.findViewById<TextView>(R.id.tvInsightDialogTaskTitle)
        val tvPreviewHour = dialog.findViewById<TextView>(R.id.tvInsightPreviewHour)
        val tvPreviewMinute = dialog.findViewById<TextView>(R.id.tvInsightPreviewMinute)
        val tvPreviewPeriod = dialog.findViewById<TextView>(R.id.tvInsightPreviewPeriod)
        val npHour = dialog.findViewById<NumberPicker>(R.id.npInsightHour)
        val npMinute = dialog.findViewById<NumberPicker>(R.id.npInsightMinute)
        val npPeriod = dialog.findViewById<NumberPicker>(R.id.npInsightPeriod)

        val btnChip15Min = dialog.findViewById<MaterialButton>(R.id.btnInsightChip15Min)
        val btnChip1Hour = dialog.findViewById<MaterialButton>(R.id.btnInsightChip1Hour)
        val btnChip12Hours = dialog.findViewById<MaterialButton>(R.id.btnInsightChip12Hours)
        val btnChip24Hours = dialog.findViewById<MaterialButton>(R.id.btnInsightChip24Hours)
        val btnConfirm = dialog.findViewById<MaterialButton>(R.id.btnInsightConfirm)
        val btnCancel = dialog.findViewById<MaterialButton>(R.id.btnInsightCancel)

        if (tvTaskTitle != null) {
            tvTaskTitle.text = selection.task.title.takeIf { it.isNotEmpty() }?.trim() ?: "Tarea"
        }

        if (npHour == null || npMinute == null || npPeriod == null) {
            dialog.dismiss()
            return
        }

        configureInsightHourPicker(npHour)
        configureInsightMinutePicker(npMinute)
        configureInsightPeriodPicker(npPeriod)

        val initialSelection = resolveInitialInsightSelection(selection.task.time)
        setInsightSelectionToPickers(npHour, npMinute, npPeriod, initialSelection)
        updateInsightTimePreview(tvPreviewHour, tvPreviewMinute, tvPreviewPeriod, npHour, npMinute, npPeriod)

        val listener = NumberPicker.OnValueChangeListener { _, _, _ ->
            updateInsightTimePreview(tvPreviewHour, tvPreviewMinute, tvPreviewPeriod, npHour, npMinute, npPeriod)
        }
        npHour.setOnValueChangedListener(listener)
        npMinute.setOnValueChangedListener(listener)
        npPeriod.setOnValueChangedListener(listener)

        root?.setOnClickListener { dialog.dismiss() }
        card?.setOnClickListener { }

        btnChip15Min?.setOnClickListener {
            val target = Calendar.getInstance().apply { add(Calendar.MINUTE, 15) }
            setInsightSelectionToPickers(npHour, npMinute, npPeriod, InsightTimeSelection(target.get(Calendar.HOUR_OF_DAY), target.get(Calendar.MINUTE)))
            updateInsightTimePreview(tvPreviewHour, tvPreviewMinute, tvPreviewPeriod, npHour, npMinute, npPeriod)
        }
        btnChip1Hour?.setOnClickListener {
            val target = Calendar.getInstance().apply { add(Calendar.HOUR_OF_DAY, 1) }
            setInsightSelectionToPickers(npHour, npMinute, npPeriod, InsightTimeSelection(target.get(Calendar.HOUR_OF_DAY), target.get(Calendar.MINUTE)))
            updateInsightTimePreview(tvPreviewHour, tvPreviewMinute, tvPreviewPeriod, npHour, npMinute, npPeriod)
        }
        btnChip12Hours?.setOnClickListener {
            val target = Calendar.getInstance().apply { add(Calendar.HOUR_OF_DAY, 12) }
            setInsightSelectionToPickers(npHour, npMinute, npPeriod, InsightTimeSelection(target.get(Calendar.HOUR_OF_DAY), target.get(Calendar.MINUTE)))
            updateInsightTimePreview(tvPreviewHour, tvPreviewMinute, tvPreviewPeriod, npHour, npMinute, npPeriod)
        }
        btnChip24Hours?.setOnClickListener {
            val target = Calendar.getInstance().apply { add(Calendar.HOUR_OF_DAY, 24) }
            setInsightSelectionToPickers(npHour, npMinute, npPeriod, InsightTimeSelection(target.get(Calendar.HOUR_OF_DAY), target.get(Calendar.MINUTE)))
            updateInsightTimePreview(tvPreviewHour, tvPreviewMinute, tvPreviewPeriod, npHour, npMinute, npPeriod)
        }

        btnCancel?.setOnClickListener { dialog.dismiss() }
        btnConfirm?.setOnClickListener {
            val chosen = readInsightSelectionFromPickers(npHour, npMinute, npPeriod)
            dialog.dismiss()
            applyInsightTaskSchedule(selection, chosen.hourOfDay24, chosen.minute)
        }

        dialog.setOnDismissListener { if (insightTimeDialog == dialog) insightTimeDialog = null }
        insightTimeDialog = dialog
        dialog.show()
    }

    private fun configureInsightHourPicker(picker: NumberPicker) {
        picker.apply {
            minValue = 1
            maxValue = 12
            wrapSelectorWheel = true
            descendantFocusability = NumberPicker.FOCUS_BLOCK_DESCENDANTS
        }
    }

    private fun configureInsightMinutePicker(picker: NumberPicker) {
        picker.apply {
            minValue = 0
            maxValue = INSIGHT_MINUTE_VALUES.size - 1
            displayedValues = INSIGHT_MINUTE_VALUES
            wrapSelectorWheel = true
            descendantFocusability = NumberPicker.FOCUS_BLOCK_DESCENDANTS
        }
    }

    private fun configureInsightPeriodPicker(picker: NumberPicker) {
        picker.apply {
            minValue = 0
            maxValue = 1
            displayedValues = arrayOf("AM", "PM")
            wrapSelectorWheel = true
            descendantFocusability = NumberPicker.FOCUS_BLOCK_DESCENDANTS
        }
    }

    private fun parseDateFromKey(dateKey: String?): Calendar {
        val cal = Calendar.getInstance()
        if (!dateKey.isNullOrEmpty()) {
            try {
                dateKeyFormat.parse(dateKey)?.let { cal.time = it }
            } catch (ignored: Exception) {}
        }
        return cal
    }

    private fun resolveInitialInsightSelection(rawTime: String?): InsightTimeSelection {
        var hour = parseHourFromTaskTime(rawTime)
        var minute = parseMinuteFromTaskTime(rawTime)

        if (hour < 0) {
            val now = Calendar.getInstance()
            hour = now.get(Calendar.HOUR_OF_DAY)
            minute = now.get(Calendar.MINUTE)
        }
        return InsightTimeSelection(hour, minute)
    }

    private fun parseMinuteFromTaskTime(rawTime: String?): Int {
        if (rawTime.isNullOrEmpty()) return 0
        return try {
            val value = rawTime.trim().uppercase(Locale.US)
            val numeric = value.replace("AM", "").replace("PM", "").trim()
            val split = numeric.split(":")
            if (split.size < 2) return 0
            val minute = split[1].replace(Regex("[^0-9]"), "").trim().toInt()
            minute.coerceIn(0, 59)
        } catch (ignored: Exception) {
            0
        }
    }

    private fun setInsightSelectionToPickers(npHour: NumberPicker, npMinute: NumberPicker, npPeriod: NumberPicker, selection: InsightTimeSelection) {
        val safeHour24 = selection.hourOfDay24.coerceIn(0, 23)
        val minuteRounded = selection.minute.coerceIn(0, 59)

        var hour12 = safeHour24 % 12
        if (hour12 == 0) hour12 = 12

        val periodValue = if (safeHour24 >= 12) 1 else 0
        var minuteIndex = minuteRounded / 5
        if (minuteIndex >= INSIGHT_MINUTE_VALUES.size) minuteIndex = INSIGHT_MINUTE_VALUES.size - 1

        npHour.value = hour12
        npMinute.value = minuteIndex
        npPeriod.value = periodValue
    }

    private fun readInsightSelectionFromPickers(npHour: NumberPicker, npMinute: NumberPicker, npPeriod: NumberPicker): InsightTimeSelection {
        val hour12 = npHour.value.coerceIn(1, 12)
        val minute = (npMinute.value * 5).coerceIn(0, 55)
        val isPm = npPeriod.value == 1

        var hour24 = hour12 % 12
        if (isPm) hour24 += 12

        return InsightTimeSelection(hour24, minute)
    }

    private fun updateInsightTimePreview(tvHour: TextView?, tvMinute: TextView?, tvPeriod: TextView?, npHour: NumberPicker, npMinute: NumberPicker, npPeriod: NumberPicker) {
        if (tvHour == null || tvMinute == null || tvPeriod == null) return

        val selection = readInsightSelectionFromPickers(npHour, npMinute, npPeriod)
        var hour12 = selection.hourOfDay24 % 12
        if (hour12 == 0) hour12 = 12

        tvHour.text = String.format(Locale.US, "%02d", hour12)
        tvMinute.text = String.format(Locale.US, "%02d", selection.minute)
        tvPeriod.text = if (selection.hourOfDay24 >= 12) "PM" else "AM"
    }

    private fun applyInsightTaskSchedule(selection: TaskSelection, hourOfDay: Int, minute: Int) {
        val targetTask = findTaskInDateBucket(selection.dateKey, selection.task)
        if (targetTask == null) {
            showSubtleMessage("La tarea seleccionada ya no esta disponible.")
            return
        }

        targetTask.time = formatTaskTimeForDisplay(hourOfDay, minute)
        tasksPerDay[selection.dateKey]?.sortWith { left, right -> extractTaskSortKey(left.time).compareTo(extractTaskSortKey(right.time)) }

        clearLockedFutureInsightForDate(selection.dateKey)
        persistAgenda()
        currentFutureInsightModel = null
        renderFutureTasksLayer()

        if (selectedDateKey == selection.dateKey) refreshSelectedDayTasks()

        if (isPlaceholderDescription(targetTask.desc) || isPlaceholderCategory(targetTask.category)) {
            requestTaskMetadataEnrichment(selection.dateKey, targetTask, isPlaceholderDescription(targetTask.desc))
        }

        showSubtleMessage("Horario guardado para \"${targetTask.title}\" a las ${targetTask.time}.")
    }

    private fun formatTaskTimeForDisplay(hourOfDay: Int, minute: Int): String {
        val safeHour = hourOfDay.coerceIn(0, 23)
        val safeMinute = minute.coerceIn(0, 59)
        var displayHour = safeHour % 12
        if (displayHour == 0) displayHour = 12
        val suffix = if (safeHour >= 12) "PM" else "AM"
        return String.format(Locale.US, "%d:%02d %s", displayHour, safeMinute, suffix)
    }

    override fun onDestroyView() {
        aiTaskMetadataHandler.removeCallbacksAndMessages(null)
        lockAgendaParentsForHorizontalScroll(false)
        setAgendaPullRefreshState(false)
        hsvDaysCarousel?.let {
            it.removeCallbacks(syncCarouselHeaderRunnable)
            if (daysCarouselScrollListener != null) {
                it.setOnScrollChangeListener(null)
            }
        }
        isCarouselHeaderSyncQueued = false
        daysCarouselScrollListener = null
        carouselFocusedDateKey = ""
        carouselFocusedWeekStartKey = ""
        activeMonthLabelIndex = -1
        futureInsightRequestInFlight = false
        futureTasksRenderScheduled = false

        insightTimeDialog?.dismiss()
        insightTimeDialog = null
        dismissTaskActionTooltip()

        super.onDestroyView()
    }

    private fun renderFutureTasksLayer() {
        if (!isAdded || rvFutureTasksLayer == null) return
        if (futureTasksRenderScheduled) return
        futureTasksRenderScheduled = true
        aiTaskMetadataHandler.removeCallbacks(renderFutureTasksRunnable)
        aiTaskMetadataHandler.postDelayed(renderFutureTasksRunnable, 50)
    }

    private fun renderFutureTasksLayerNow() {
        futureTasksRenderScheduled = false
        if (!isAdded || rvFutureTasksLayer == null) return

        val dayBlocks = collectFutureDayBlocks()
        if (dayBlocks.isEmpty()) {
            futureTasksAdapter?.submitList(listOf(FutureAgendaItem.Empty))
            return
        }

        val items = mutableListOf<FutureAgendaItem>()
        val noTimeSelection = findFirstTaskWithoutTimeFromToday()
        val insightSignature = buildFutureInsightSignature(dayBlocks, noTimeSelection)
        val insightModel = resolveFutureInsightModel(dayBlocks, noTimeSelection, insightSignature)
        requestFutureInsightFromAiIfNeeded(dayBlocks, noTimeSelection, insightSignature)
        val insightTargetDateKey = resolveFutureInsightTargetDateKey(dayBlocks, insightModel, noTimeSelection)

        val baseDateKey = dayBlocks.first().dateKey
        val totalFutureTasks = dayBlocks.sumOf { it.tasks.size }
        var globalTaskIndex = 0
        var insightInserted = false

        for (dayIndex in dayBlocks.indices) {
            val block = dayBlocks[dayIndex]
            val isCurrentCycle = dayIndex == 0
            val cycleLabel = buildFutureCycleLabel(baseDateKey, block.dateKey, isCurrentCycle)

            items.add(FutureAgendaItem.Header(block.displayDate, cycleLabel, block.dateKey))

            for (taskIndex in block.tasks.indices) {
                val task = block.tasks[taskIndex]
                val isFirst = globalTaskIndex == 0
                val isLast = globalTaskIndex == (totalFutureTasks - 1)
                
                items.add(FutureAgendaItem.TaskItem(task, block.dateKey, isFirst, isLast, globalTaskIndex))
                globalTaskIndex++
            }

            if (!insightInserted && block.dateKey == insightTargetDateKey) {
                items.add(FutureAgendaItem.InsightCard(insightModel))
                insightInserted = true
            }
        }

        futureTasksAdapter?.submitList(items)
    }

    // Cleaned up HTML building methods (Removed buildFutureTaskSectionsHtml, buildFutureTaskCardHtml, buildFutureInsightHtml)



    private fun resolveFutureInsightTargetDateKey(dayBlocks: List<FutureDayBlock>, insightModel: FutureInsightModel?, noTimeSelection: TaskSelection?): String {
        if (dayBlocks.isEmpty()) return ""

        if (insightModel != null && FUTURE_INSIGHT_MODE_SCHEDULE == insightModel.mode && insightModel.targetDateKey.isNotEmpty() && containsFutureDayBlock(dayBlocks, insightModel.targetDateKey)) {
            return insightModel.targetDateKey
        }

        if (noTimeSelection != null && containsFutureDayBlock(dayBlocks, noTimeSelection.dateKey)) {
            return noTimeSelection.dateKey
        }

        if (insightModel != null && insightModel.targetDateKey.isNotEmpty() && containsFutureDayBlock(dayBlocks, insightModel.targetDateKey)) {
            return insightModel.targetDateKey
        }

        return dayBlocks.first().dateKey
    }

    private fun containsFutureDayBlock(dayBlocks: List<FutureDayBlock>, dateKey: String): Boolean {
        return dayBlocks.any { it.dateKey == dateKey }
    }
    private fun resolveFutureInsightModel(dayBlocks: List<FutureDayBlock>, noTimeSelection: TaskSelection?, insightSignature: String): FutureInsightModel {
        val lockedModel = restoreLockedFutureInsightModel(noTimeSelection, insightSignature)
        if (lockedModel != null) {
            currentFutureInsightModel = lockedModel
            return lockedModel
        }

        if (currentFutureInsightModel != null && currentFutureInsightModel!!.agendaSignature == insightSignature) {
            return currentFutureInsightModel!!
        }

        val loadingMessage = if (noTimeSelection?.task?.title?.isNotEmpty() == true) {
            "Analizando con IA la mejor hora para \"${noTimeSelection.task.title}\"..."
        } else {
            "Analizando con IA tu agenda para recomendar el mejor bloque de enfoque..."
        }

        currentFutureInsightModel = FutureInsightModel(
            insightSignature,
            if (noTimeSelection != null) FUTURE_INSIGHT_MODE_SCHEDULE else FUTURE_INSIGHT_MODE_FOCUS,
            noTimeSelection?.dateKey,
            noTimeSelection?.task?.title,
            if (noTimeSelection != null) "Horario inteligente sugerido" else "Optimizacion de carga cognitiva",
            loadingMessage,
            if (noTimeSelection != null) "Elegir horario" else "Iniciar enfoque",
            "",
            System.currentTimeMillis() + 8000L
        )
        return currentFutureInsightModel!!
    }

    private fun requestFutureInsightFromAiIfNeeded(dayBlocks: List<FutureDayBlock>, noTimeSelection: TaskSelection?, insightSignature: String) {
        val lockedModel = restoreLockedFutureInsightModel(noTimeSelection, insightSignature)
        if (lockedModel != null) {
            currentFutureInsightModel = lockedModel
            futureInsightRequestInFlight = false
            return
        }

        if (futureInsightRequestInFlight) return

        val now = System.currentTimeMillis()
        if (currentFutureInsightModel != null && currentFutureInsightModel!!.agendaSignature == insightSignature && now < currentFutureInsightModel!!.refreshAfterAtMs) {
            return
        }

        futureInsightRequestInFlight = true
        val profileName = resolveProfileNameForAi()
        val agendaSnapshot = buildFutureInsightAgendaSnapshot(dayBlocks, noTimeSelection)
        val behaviorSignals = buildFutureInsightBehaviorSignals(noTimeSelection)

        GeminiIntelligenceService().generateScheduleSuggestion(
            profileName,
            agendaSnapshot,
            behaviorSignals,
            object : GeminiIntelligenceService.ScheduleSuggestionCallback {
                override fun onSuccess(suggestion: GeminiIntelligenceService.ScheduleSuggestion) {
                    futureInsightRequestInFlight = false
                    currentFutureInsightModel = buildAiFutureInsightModel(insightSignature, suggestion, noTimeSelection)
                    renderFutureTasksLayer()
                }

                override fun onError(error: String) {
                    futureInsightRequestInFlight = false
                    currentFutureInsightModel = buildFallbackFutureInsightModel(insightSignature, dayBlocks, noTimeSelection)
                    renderFutureTasksLayer()
                }
            }
        )
    }

    private fun buildAiFutureInsightModel(insightSignature: String, suggestion: GeminiIntelligenceService.ScheduleSuggestion, noTimeSelection: TaskSelection?): FutureInsightModel {
        val mode = if (noTimeSelection != null) FUTURE_INSIGHT_MODE_SCHEDULE else FUTURE_INSIGHT_MODE_FOCUS
        val title = if (noTimeSelection != null) "Horario inteligente sugerido" else "Optimizacion de carga cognitiva"
        val actionLabel = if (noTimeSelection != null) "Elegir horario" else "Iniciar enfoque"

        var message = suggestion.message
        if (noTimeSelection != null && suggestion.focusWindow?.isNotEmpty() == true) {
            message += " Te conviene hacerlo entre ${suggestion.focusWindow}."
        }

        val microAction = suggestion.microAction?.takeIf { it.isNotEmpty() }
            ?: if (noTimeSelection != null) "Abre Elegir horario para programar la tarea ahora." else "Reserva 25 minutos de enfoque para tu prioridad principal."

        val model = FutureInsightModel(
            insightSignature,
            mode,
            noTimeSelection?.dateKey,
            noTimeSelection?.task?.title,
            title,
            message,
            actionLabel,
            microAction,
            System.currentTimeMillis() + FUTURE_INSIGHT_REFRESH_INTERVAL_MS
        )
        persistLockedFutureInsightModel(model)
        return model
    }

    private fun buildFallbackFutureInsightModel(insightSignature: String, dayBlocks: List<FutureDayBlock>, noTimeSelection: TaskSelection?): FutureInsightModel {
        val localInsight = buildFutureTasksInsight(dayBlocks)
        val fallbackMessage: String
        val title: String
        val actionLabel: String
        val mode: String

        if (noTimeSelection?.task?.title?.isNotEmpty() == true) {
            title = "Horario inteligente sugerido"
            mode = FUTURE_INSIGHT_MODE_SCHEDULE
            actionLabel = "Elegir horario"
            fallbackMessage = "No pude consultar IA justo ahora. Te conviene asignar una hora a \"${noTimeSelection.task.title}\" para asegurar avance hoy."
        } else {
            title = "Optimizacion de carga cognitiva"
            mode = FUTURE_INSIGHT_MODE_FOCUS
            actionLabel = "Iniciar enfoque"
            fallbackMessage = localInsight
        }

        val model = FutureInsightModel(
            insightSignature,
            mode,
            noTimeSelection?.dateKey,
            noTimeSelection?.task?.title,
            title,
            fallbackMessage,
            actionLabel,
            "",
            System.currentTimeMillis() + FUTURE_INSIGHT_RETRY_INTERVAL_MS
        )
        persistLockedFutureInsightModel(model)
        return model
    }

    private fun buildFutureInsightAgendaSnapshot(dayBlocks: List<FutureDayBlock>, noTimeSelection: TaskSelection?): String {
        val builder = StringBuilder()
        builder.append(buildAgendaSnapshotForAi())

        if (noTimeSelection?.task != null) {
            builder.append("; tarea_sin_hora_objetivo=")
                .append(noTimeSelection.dateKey)
                .append("|")
                .append(noTimeSelection.task.title)
                .append("|categoria=")
                .append(inferTaskCategory(noTimeSelection.task))
        }

        var daySamples = 0
        for (block in dayBlocks) {
            if (daySamples >= 3) break
            for (task in block.tasks) {
                builder.append("; muestra=")
                    .append(block.dateKey)
                    .append("|")
                    .append(task.title)
                    .append("|")
                    .append(if (task.time.isEmpty()) "sin_hora" else task.time)
                daySamples++
                if (daySamples >= 3) break
            }
        }
        return builder.toString()
    }

    private fun buildFutureInsightBehaviorSignals(noTimeSelection: TaskSelection?): String {
        val base = buildBehaviorSignalsForAi()
        if (noTimeSelection?.task?.title?.isNotEmpty() == true) {
            return base + ", modo=fijar_horario, instruccion=La recomendacion debe sugerir asignar hora a la tarea objetivo usando formato AM/PM y texto intuitivo, sin usar la palabra franja, tarea_objetivo='${noTimeSelection.task.title}'"
        }
        return base + ", modo=enfoque, instruccion=Si no hay tareas sin hora, sugiere bloque de foco y microaccion breve"
    }

    private fun buildFutureInsightActionHref(insightModel: FutureInsightModel): String {
        val builder = Uri.Builder()
            .scheme(FUTURE_INSIGHT_ACTION_SCHEME)
            .authority(FUTURE_INSIGHT_ACTION_HOST)
            .path(FUTURE_INSIGHT_ACTION_PATH)
            .appendQueryParameter("mode", insightModel.mode)

        if (insightModel.targetDateKey.isNotEmpty()) builder.appendQueryParameter("date", insightModel.targetDateKey)
        if (insightModel.targetTaskTitle.isNotEmpty()) builder.appendQueryParameter("task", insightModel.targetTaskTitle)
        return builder.build().toString()
    }

    private fun findFirstTaskWithoutTimeFromToday(): TaskSelection? {
        if (tasksPerDay.isEmpty()) return null

        val todayKey = dateKeyFormat.format(Calendar.getInstance().time)
        for ((dateKey, dayTasks) in TreeMap(tasksPerDay)) {
            if (dateKey < todayKey) continue
            if (dayTasks.isEmpty()) continue

            dayTasks.find { it.title.isNotEmpty() && it.time.isEmpty() }?.let {
                return TaskSelection(dateKey, it)
            }
        }
        return null
    }

    private fun buildFutureInsightSignature(dayBlocks: List<FutureDayBlock>, noTimeSelection: TaskSelection?): String {
        val builder = StringBuilder()
        for (block in dayBlocks) {
            builder.append(block.dateKey).append("|")
            for (task in block.tasks) {
                builder.append(task.title).append("#").append(task.time).append("#").append(task.category).append(";")
            }
            builder.append("||")
        }

        if (noTimeSelection?.task != null) {
            builder.append("target=").append(noTimeSelection.dateKey).append("|").append(noTimeSelection.task.title)
        } else {
            builder.append("target=none")
        }

        return builder.toString().hashCode().toString()
    }

    private fun buildEmptyFutureTasksSectionHtml(): String {
        return """
            <section class="future-empty-section">
            <header>
            <h2>Tareas proximas</h2>
            <span class="cycle-label">Listo para iniciar</span>
            </header>
            <div class="future-empty-shell">
            <h3 class="future-empty-title">Tu agenda esta lista para construir foco real</h3>
            <p class="future-empty-copy">Cuando agregues tu primera tarea, esta capa te mostrara prioridades, horarios sugeridos y contexto de avance sin ruido visual.</p>
            <a class="future-empty-cta" href="sleppify://future-task/add">Crear primera tarea</a>
            </div>
            </section>
        """.trimIndent()
    }

    private fun collectFutureDayBlocks(): List<FutureDayBlock> {
        val todayKey = dateKeyFormat.format(Calendar.getInstance().time)
        val ordered = TreeMap(tasksPerDay)
        val blocks = ArrayList<FutureDayBlock>()

        for ((dateKey, dayTasks) in ordered) {
            if (dateKey < todayKey) continue
            if (dayTasks.isEmpty()) continue

            val sortedTasks = dayTasks.sortedBy { extractTaskSortKey(it.time) }.take(FUTURE_TASKS_PER_DAY_LIMIT)
            val formattedDate = formatDateKeyForFutureSection(dateKey)
            blocks.add(FutureDayBlock(dateKey, formattedDate, sortedTasks))
            if (blocks.size >= FUTURE_TASK_DAYS_LIMIT) break
        }
        return blocks
    }
    private fun parseDateKeyToCalendar(dateKey: String): Calendar? {
        return try {
            val date = dateKeyFormat.parse(dateKey)
            if (date != null) {
                val cal = Calendar.getInstance()
                cal.time = date
                cal
            } else null
        } catch (e: Exception) {
            null
        }
    }

    private fun buildFutureCycleLabel(baseDateKey: String, targetDateKey: String, isCurrentCycle: Boolean): String {
        if (isCurrentCycle) return "Semana actual"

        val baseDate = parseDateKeyToCalendar(baseDateKey) ?: return "Proximo ciclo"
        val targetDate = parseDateKeyToCalendar(targetDateKey) ?: return "Proximo ciclo"

        if (!targetDate.after(baseDate)) return "Proximo ciclo"

        val baseWeekStart = getWeekStart(baseDate)
        val targetWeekStart = getWeekStart(targetDate)
        val weekDiff = (targetWeekStart.timeInMillis - baseWeekStart.timeInMillis) / (7L * 24L * 60L * 60L * 1000L)
        val monthDiff = calculateMonthDifference(baseDate, targetDate)

        return when {
            weekDiff == 1L -> "Siguiente semana"
            weekDiff in 2L..3L -> "En $weekDiff semanas"
            monthDiff == 1 -> "Siguiente mes"
            monthDiff > 1 -> "En $monthDiff meses"
            weekDiff > 3L -> "En $weekDiff semanas"
            else -> "Esta semana"
        }
    }

    private fun getWeekStart(source: Calendar): Calendar {
        val weekStart = source.clone() as Calendar
        weekStart.firstDayOfWeek = Calendar.MONDAY
        val dayOfWeek = weekStart.get(Calendar.DAY_OF_WEEK)
        val offset = (7 + (dayOfWeek - Calendar.MONDAY)) % 7
        weekStart.add(Calendar.DAY_OF_MONTH, -offset)
        setCalendarToStartOfDay(weekStart)
        return weekStart
    }

    private fun setCalendarToStartOfDay(calendar: Calendar) {
        calendar.apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
    }

    private fun calculateMonthDifference(start: Calendar, end: Calendar): Int {
        val yearDiff = end.get(Calendar.YEAR) - start.get(Calendar.YEAR)
        val monthDiff = end.get(Calendar.MONTH) - start.get(Calendar.MONTH)
        return yearDiff * 12 + monthDiff
    }

    private fun extractTaskSortKey(rawTime: String?): Int {
        val hour = parseHourFromTaskTime(rawTime)
        if (hour < 0) return Int.MAX_VALUE
        val minute = parseMinuteFromTaskTime(rawTime)
        return hour * 60 + minute
    }

    private fun formatDateKeyForFutureSection(dateKey: String): String {
        return try {
            val parts = dateKey.split("-")
            if (parts.size != 3) return dateKey

            val cal = Calendar.getInstance().apply {
                set(Calendar.YEAR, parts[0].toInt())
                set(Calendar.MONTH, parts[1].toInt() - 1)
                set(Calendar.DAY_OF_MONTH, parts[2].toInt())
            }

            val raw = SimpleDateFormat("EEEE, dd 'de' MMM", SPANISH_LOCALE).format(cal.time)
            if (raw.isEmpty()) dateKey else raw.substring(0, 1).uppercase() + raw.substring(1)
        } catch (ignored: Exception) {
            dateKey
        }
    }

    private fun inferTaskCategory(task: Task): String {
        val stored = normalizeTaskCategory(task.category)
        return stored.takeIf { it.isNotEmpty() } ?: FALLBACK_TASK_CATEGORY
    }

    private fun normalizeTaskCategory(rawCategory: String?): String {
        if (rawCategory == null) return DEFAULT_TASK_CATEGORY

        var value = rawCategory.replace('\n', ' ').replace('\r', ' ').trim()
        if (value.isEmpty()) return DEFAULT_TASK_CATEGORY

        value = value.replace(Regex("\\s+"), " ")
            .replace(Regex("^[\\\"'`]+|[\\\"'`]+$"), "")
            .replace(Regex("[\\.;:,]+$"), "")

        val tokens = value.split("\\s+".toRegex())
        if (tokens.isNotEmpty()) value = tokens[0]

        value = value.replace(Regex("[^\\p{L}\\p{N}_-]"), "").trim()
        if (value.isEmpty()) return DEFAULT_TASK_CATEGORY

        val lower = value.lowercase(SPANISH_LOCALE)
        if (lower == "none") return TASK_METADATA_ERROR_CATEGORY

        value = lower.substring(0, 1).uppercase() + lower.substring(1)
        return value.take(24).trim()
    }

    private fun buildFutureTasksInsight(blocks: List<FutureDayBlock>): String {
        var total = 0
        var timedTasks = 0
        var morning = 0
        var afternoon = 0
        var night = 0

        for (block in blocks) {
            for (task in block.tasks) {
                total++
                val hour = parseHourFromTaskTime(task.time)
                if (hour >= 0) {
                    timedTasks++
                    if (hour in 5..11) morning++
                    else if (hour in 12..18) afternoon++
                    else night++
                }
            }
        }

        if (total == 0) return "No se detectaron tareas proximas. Agrega nuevas tareas para activar tu capa inteligente."
        if (timedTasks == 0) return "Tus tareas proximas no usan horarios fijos. Prioriza una tarea clave al inicio de cada dia para mantener continuidad."
        if (morning >= afternoon && morning >= night) return "Tu agenda proxima esta cargada en la manana. Mantener tu tarea mas exigente en el primer bloque mejora el enfoque."
        if (afternoon >= night) return "Tus tareas proximas se concentran en la tarde. Protege un bloque sin interrupciones para reducir cambios de contexto."
        return "Tu carga se inclina hacia la noche. Mover una tarea demandante a una hora mas temprana puede mejorar energia y calidad de decisiones."
    }

    private fun restoreLockedFutureInsightModel(noTimeSelection: TaskSelection?, insightSignature: String): FutureInsightModel? {
        if (settingsPrefs == null || noTimeSelection?.task == null) return null

        val lockedDate = settingsPrefs!!.getString(KEY_FUTURE_INSIGHT_LOCK_DATE, "")
        if (lockedDate != noTimeSelection.dateKey) return null

        val lockedMessage = settingsPrefs!!.getString(KEY_FUTURE_INSIGHT_LOCK_MESSAGE, "")?.trim()
        if (lockedMessage.isNullOrEmpty()) return null

        val lockedTitle = settingsPrefs!!.getString(KEY_FUTURE_INSIGHT_LOCK_TITLE, "Horario inteligente sugerido")
        val lockedAction = settingsPrefs!!.getString(KEY_FUTURE_INSIGHT_LOCK_ACTION, "Elegir horario")
        val lockedMicro = settingsPrefs!!.getString(KEY_FUTURE_INSIGHT_LOCK_MICRO, "")

        return FutureInsightModel(
            insightSignature,
            FUTURE_INSIGHT_MODE_SCHEDULE,
            noTimeSelection.dateKey,
            noTimeSelection.task.title,
            if (lockedTitle.isNullOrEmpty()) "Horario inteligente sugerido" else lockedTitle,
            lockedMessage,
            if (lockedAction.isNullOrEmpty()) "Elegir horario" else lockedAction,
            lockedMicro ?: "",
            System.currentTimeMillis() + FUTURE_INSIGHT_REFRESH_INTERVAL_MS
        )
    }

    private fun persistLockedFutureInsightModel(model: FutureInsightModel) {
        if (settingsPrefs == null || model.mode != FUTURE_INSIGHT_MODE_SCHEDULE) return
        if (model.targetDateKey.isEmpty() || model.message.isEmpty()) return

        settingsPrefs!!.edit()
            .putString(KEY_FUTURE_INSIGHT_LOCK_DATE, model.targetDateKey)
            .putString(KEY_FUTURE_INSIGHT_LOCK_TASK, model.targetTaskTitle)
            .putString(KEY_FUTURE_INSIGHT_LOCK_TITLE, model.title)
            .putString(KEY_FUTURE_INSIGHT_LOCK_MESSAGE, model.message)
            .putString(KEY_FUTURE_INSIGHT_LOCK_ACTION, model.actionLabel)
            .putString(KEY_FUTURE_INSIGHT_LOCK_MICRO, model.microAction)
            .apply()
    }

    private fun clearLockedFutureInsightIfResolved() {
        if (settingsPrefs == null) return

        val lockedDate = settingsPrefs!!.getString(KEY_FUTURE_INSIGHT_LOCK_DATE, "")
        if (lockedDate.isNullOrEmpty()) return

        val dayTasks = tasksPerDay[lockedDate]
        if (dayTasks.isNullOrEmpty()) {
            clearLockedFutureInsightForDate(lockedDate)
            return
        }

        if (dayTasks.any { it.title.isNotEmpty() && it.time.isEmpty() }) return

        clearLockedFutureInsightForDate(lockedDate)
    }

    private fun clearLockedFutureInsightForDate(dateKey: String?) {
        if (settingsPrefs == null || dateKey.isNullOrEmpty()) return

        val lockedDate = settingsPrefs!!.getString(KEY_FUTURE_INSIGHT_LOCK_DATE, "")
        if (lockedDate != dateKey) return

        settingsPrefs!!.edit()
            .remove(KEY_FUTURE_INSIGHT_LOCK_DATE)
            .remove(KEY_FUTURE_INSIGHT_LOCK_TASK)
            .remove(KEY_FUTURE_INSIGHT_LOCK_TITLE)
            .remove(KEY_FUTURE_INSIGHT_LOCK_MESSAGE)
            .remove(KEY_FUTURE_INSIGHT_LOCK_ACTION)
            .remove(KEY_FUTURE_INSIGHT_LOCK_MICRO)
            .apply()
    }

    private fun showInsightTimePicker(date: Calendar, dateKey: String, taskTitle: String) {
        val timePicker = TimePickerDialog(
            requireContext(),
            { _, hour: Int, minute: Int ->
                val timeStr = formatTimeForTask(hour, minute)
                updateTaskTimeAndPersist(dateKey, taskTitle, timeStr)
            },

            date.get(Calendar.HOUR_OF_DAY),
            date.get(Calendar.MINUTE),
            false
        )
        timePicker.show()
    }

    private fun updateTaskTimeAndPersist(dateKey: String, taskTitle: String, timeStr: String) {
         val dayTasks = tasksPerDay[dateKey] ?: return
         val task = dayTasks.find { it.title == taskTitle } ?: return
         task.time = timeStr
         persistAgenda()
         renderFutureTasksLayerNow()
         refreshSelectedDayTasks()
    }

    private fun formatTimeForTask(hour: Int, minute: Int): String {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, hour)
        calendar.set(Calendar.MINUTE, minute)
        val sdf = SimpleDateFormat("h:mm a", Locale.US)
        return sdf.format(calendar.time)
    }

    private inner class FutureTasksAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        private val VIEW_TYPE_HEADER = 1
        private val VIEW_TYPE_TASK = 2
        private val VIEW_TYPE_INSIGHT = 3
        private val VIEW_TYPE_EMPTY = 4

        private var items: List<FutureAgendaItem> = emptyList()

        fun submitList(newItems: List<FutureAgendaItem>) {
            this.items = newItems
            notifyDataSetChanged()
        }

        override fun getItemViewType(position: Int): Int = when (items[position]) {
            is FutureAgendaItem.Header -> VIEW_TYPE_HEADER
            is FutureAgendaItem.TaskItem -> VIEW_TYPE_TASK
            is FutureAgendaItem.InsightCard -> VIEW_TYPE_INSIGHT
            is FutureAgendaItem.Empty -> VIEW_TYPE_EMPTY
        }

        override fun getItemCount(): Int = items.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val inflater = LayoutInflater.from(parent.context)
            return when (viewType) {
                VIEW_TYPE_HEADER -> HeaderViewHolder(inflater.inflate(R.layout.item_future_header, parent, false))
                VIEW_TYPE_TASK -> FutureTaskViewHolder(inflater.inflate(R.layout.item_future_task, parent, false))
                VIEW_TYPE_INSIGHT -> InsightViewHolder(inflater.inflate(R.layout.item_future_insight, parent, false))
                else -> EmptyFutureViewHolder(inflater.inflate(R.layout.item_future_empty, parent, false))
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val item = items[position]
            when (holder) {
                is HeaderViewHolder -> {
                    val header = item as FutureAgendaItem.Header
                    holder.tvTitle.text = header.displayDate
                    holder.tvCycle.text = header.cycleLabel.uppercase(SPANISH_LOCALE)
                    holder.tvCycle.setTextColor(ContextCompat.getColor(requireContext(), R.color.stitch_blue))
                }
                is FutureTaskViewHolder -> {
                    val taskItem = item as FutureAgendaItem.TaskItem
                    val task = taskItem.task
                    holder.tvTitle.text = task.title
                    holder.tvDesc.text = task.desc
                    holder.tvCategory.text = inferTaskCategory(task)
                    
                    val taskTime = task.time.trim()
                    if (taskTime.isNotEmpty()) {
                        holder.tvTime.visibility = View.VISIBLE
                        holder.tvTime.text = taskTime
                        holder.tvTime.setTextColor(if (isDaytimeHour(parseHourFromTaskTime(taskTime))) 0xFFBCD0FF.toInt() else 0xFFFFC5AD.toInt())
                    } else {
                        holder.tvTime.visibility = View.GONE
                    }

                    holder.viewTopLine.visibility = if (taskItem.isFirst) View.INVISIBLE else View.VISIBLE
                    holder.viewBottomLine.visibility = if (taskItem.isLast) View.INVISIBLE else View.VISIBLE

                    val longPressHandler = Handler(Looper.getMainLooper())
                    val longPressRunnable = Runnable {
                        taskHolderHapticFeedback(holder.itemView)
                        showTaskActionTooltip(holder.itemView, TaskSelection(taskItem.dateKey, task), null, null)
                    }

                    holder.itemView.setOnTouchListener { _, event ->
                        when (event.actionMasked) {
                            MotionEvent.ACTION_DOWN -> {
                                holder.card.animate().scaleX(0.95f).scaleY(0.95f).setDuration(100).start()
                                longPressHandler.postDelayed(longPressRunnable, 400)
                            }
                            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                                holder.card.animate().scaleX(1.0f).scaleY(1.0f).setDuration(100).start()
                                longPressHandler.removeCallbacks(longPressRunnable)
                            }
                        }
                        true
                    }
                }
                is InsightViewHolder -> {
                    val insight = item as FutureAgendaItem.InsightCard
                    val model = insight.model
                    holder.tvTitle.text = model.title
                    holder.tvMessage.text = model.message
                    holder.btnAction.text = model.actionLabel
                    holder.btnAction.setOnClickListener {
                        handleFutureInsightAction(model)
                    }
                }
                is EmptyFutureViewHolder -> {
                    holder.itemView.setOnClickListener { triggerAddTaskFlow() }
                }
            }
        }

        private fun taskHolderHapticFeedback(view: View) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            }
        }

        inner class HeaderViewHolder(v: View) : RecyclerView.ViewHolder(v) {
            val tvTitle: TextView = v.findViewById(R.id.tvFutureHeaderTitle)
            val tvCycle: TextView = v.findViewById(R.id.tvFutureHeaderCycle)
        }
        inner class FutureTaskViewHolder(v: View) : RecyclerView.ViewHolder(v) {
            val card: MaterialCardView = v.findViewById(R.id.cardFutureTask)
            val tvTitle: TextView = v.findViewById(R.id.tvFutureTaskTitle)
            val tvTime: TextView = v.findViewById(R.id.tvFutureTaskTime)
            val tvCategory: TextView = v.findViewById(R.id.tvFutureTaskCategory)
            val tvDesc: TextView = v.findViewById(R.id.tvFutureTaskDesc)
            val viewTopLine: View = v.findViewById(R.id.viewTimelineTop)
            val viewBottomLine: View = v.findViewById(R.id.viewTimelineBottom)
        }
        inner class InsightViewHolder(v: View) : RecyclerView.ViewHolder(v) {
            val tvTitle: TextView = v.findViewById(R.id.tvInsightTitle)
            val tvMessage: TextView = v.findViewById(R.id.tvInsightMessage)
            val btnAction: Button = v.findViewById(R.id.btnInsightAction)
        }
        inner class EmptyFutureViewHolder(v: View) : RecyclerView.ViewHolder(v)
    }

    private fun handleFutureInsightAction(model: FutureInsightModel) {
        if (FUTURE_INSIGHT_MODE_SCHEDULE == model.mode) {
            val date = parseDateFromKey(model.targetDateKey)
            if (date != null) {
                showInsightTimePicker(date, model.targetDateKey, model.targetTaskTitle)
            }
        } else if (FUTURE_INSIGHT_MODE_FOCUS == model.mode) {
            // Trigger focus block or direct scheme action
            val uri = Uri.parse("sleppify://future-insight/action?mode=${model.mode}")
            try { handleDeepLink(uri) } catch (ignored: Exception) {}
        }
    }

    private fun handleDeepLink(uri: Uri): Boolean {
        // Implementation for deep links if needed, or direct calls
        return false
    }


    private inner class TaskAdapter(tasks: List<Task>) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        private val VIEW_TYPE_TASK = 1
        private val VIEW_TYPE_EMPTY = 2
        private var taskList: List<Task> = ArrayList(tasks)

        fun setTasks(newTasks: List<Task>) {
            val previousTasks = ArrayList(this.taskList)
            val updatedTasks = ArrayList(newTasks)
            val timelineStructureChanged = previousTasks.size != updatedTasks.size
            val emptyStateChanged = previousTasks.isEmpty() != updatedTasks.isEmpty()

            if (emptyStateChanged) {
                this.taskList = updatedTasks
                notifyDataSetChanged()
                return
            }

            val diffResult = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
                override fun getOldListSize(): Int = if (previousTasks.isEmpty()) 1 else previousTasks.size
                override fun getNewListSize(): Int = if (updatedTasks.isEmpty()) 1 else updatedTasks.size

                override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                    val oldIsPlaceholder = previousTasks.isEmpty()
                    val newIsPlaceholder = updatedTasks.isEmpty()
                    if (oldIsPlaceholder || newIsPlaceholder) {
                        return oldIsPlaceholder && newIsPlaceholder
                    }

                    val oldTask = previousTasks[oldItemPosition]
                    val newTask = updatedTasks[newItemPosition]
                    return oldTask.title == newTask.title && oldTask.time == newTask.time
                }

                override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                    val oldIsPlaceholder = previousTasks.isEmpty()
                    val newIsPlaceholder = updatedTasks.isEmpty()
                    if (oldIsPlaceholder || newIsPlaceholder) {
                        return oldIsPlaceholder && newIsPlaceholder
                    }

                    val oldTask = previousTasks[oldItemPosition]
                    val newTask = updatedTasks[newItemPosition]

                    if (timelineStructureChanged) return false

                    return oldTask.title == newTask.title &&
                            oldTask.desc == newTask.desc &&
                            oldTask.time == newTask.time &&
                            oldTask.category == newTask.category
                }
            })

            this.taskList = updatedTasks
            diffResult.dispatchUpdatesTo(this)
        }

        override fun getItemViewType(position: Int): Int {
            return if (taskList.isEmpty()) VIEW_TYPE_EMPTY else VIEW_TYPE_TASK
        }

        override fun getItemCount(): Int {
            return if (taskList.isEmpty()) 1 else taskList.size
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            if (viewType == VIEW_TYPE_EMPTY) {
                val emptyView = LayoutInflater.from(parent.context).inflate(R.layout.item_task_empty, parent, false)
                return EmptyViewHolder(emptyView)
            }
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_task, parent, false)
            return TaskViewHolder(view)
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            if (holder is EmptyViewHolder) {
                if (holder.container != null) {
                    holder.container.setOnClickListener { triggerAddTaskFlow() }
                } else {
                    holder.itemView.setOnClickListener { triggerAddTaskFlow() }
                }
                return
            }

            val task = taskList[position]
            val taskHolder = holder as TaskViewHolder
            
            taskHolder.tvTitle.text = task.title
            taskHolder.tvDesc.text = task.desc
            clearLongPressFeedback(taskHolder)

            if (taskHolder.tvCategory != null) {
                val categoryLabel = resolveTaskCategoryForList(task)
                if (categoryLabel.isEmpty()) {
                    taskHolder.tvCategory.visibility = View.GONE
                } else {
                    taskHolder.tvCategory.visibility = View.VISIBLE
                    taskHolder.tvCategory.text = categoryLabel
                }
            }

            val taskTime = task.time.trim()
            if (taskHolder.tvTime != null) {
                if (taskTime.isEmpty()) {
                    taskHolder.tvTime.visibility = View.GONE
                } else {
                    taskHolder.tvTime.visibility = View.VISIBLE
                    taskHolder.tvTime.text = taskTime
                    taskHolder.tvTime.setTextColor(if (isDaytimeHour(parseHourFromTaskTime(taskTime))) TASK_TIME_COLOR_DAY else TASK_TIME_COLOR_NIGHT)
                }
            }

            val showTimeline = taskList.size > 1
            taskHolder.timelineContainer?.visibility = if (showTimeline) View.VISIBLE else View.GONE
            taskHolder.timelineDot?.visibility = if (showTimeline) View.VISIBLE else View.GONE

            if (!showTimeline) {
                taskHolder.timelineLineTop?.visibility = View.GONE
                taskHolder.timelineLineBottom?.visibility = View.GONE
            } else {
                val lastPosition = taskList.size - 1
                taskHolder.timelineLineTop?.visibility = if (position == 0) View.INVISIBLE else View.VISIBLE
                taskHolder.timelineLineBottom?.visibility = if (position == lastPosition) View.INVISIBLE else View.VISIBLE
            }

            val lastTouchRaw = floatArrayOf(Float.NaN, Float.NaN)
            val longPressHandler = Handler(Looper.getMainLooper())
            val longPressRunnable = Runnable {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    taskHolder.itemView.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                }
                clearLongPressFeedback(taskHolder)
                val touchX = if (lastTouchRaw[0].isNaN()) null else lastTouchRaw[0]
                val touchY = if (lastTouchRaw[1].isNaN()) null else lastTouchRaw[1]
                showTaskActionTooltip(taskHolder.itemView, TaskSelection(selectedDateKey ?: "", task), touchX, touchY)
            }

            taskHolder.itemView.setOnTouchListener { _, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        lastTouchRaw[0] = event.rawX
                        lastTouchRaw[1] = event.rawY
                        applyLongPressFeedback(taskHolder)
                        longPressHandler.postDelayed(longPressRunnable, 400)
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = Math.abs(event.rawX - lastTouchRaw[0])
                        val dy = Math.abs(event.rawY - lastTouchRaw[1])
                        if (dx > 10 || dy > 10) {
                            longPressHandler.removeCallbacks(longPressRunnable)
                            clearLongPressFeedback(taskHolder)
                        }
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        longPressHandler.removeCallbacks(longPressRunnable)
                        clearLongPressFeedback(taskHolder)
                    }
                }
                false
            }
            
            taskHolder.itemView.setOnClickListener {
                // Do nothing
            }
        }

        private fun applyLongPressFeedback(holder: TaskViewHolder) {
            val target = holder.cardTask ?: holder.itemView
            target.animate().cancel()
            target.animate().scaleX(0.92f).scaleY(0.92f).setDuration(120).start()
            holder.cardTask?.setCardBackgroundColor(TASK_CARD_COLOR_LONG_PRESS)
        }

        private fun clearLongPressFeedback(holder: TaskViewHolder) {
            val target = holder.cardTask ?: holder.itemView
            target.animate().cancel()
            target.animate().scaleX(1.0f).scaleY(1.0f).setDuration(120).start()
            holder.cardTask?.setCardBackgroundColor(TASK_CARD_COLOR_DEFAULT)
        }

        inner class TaskViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val tvTitle: TextView = itemView.findViewById(R.id.tvTaskTitle)
            val tvDesc: TextView = itemView.findViewById(R.id.tvTaskDesc)
            val tvTime: TextView? = itemView.findViewById(R.id.tvTaskTime)
            val tvCategory: TextView? = itemView.findViewById(R.id.tvTaskCategory)
            val cardTask: MaterialCardView? = itemView.findViewById(R.id.cardTask)
            val timelineContainer: View? = itemView.findViewById(R.id.timelineContainer)
            val timelineDot: View? = itemView.findViewById(R.id.timelineDot)
            val timelineLineTop: View? = itemView.findViewById(R.id.timelineLineTop)
            val timelineLineBottom: View? = itemView.findViewById(R.id.timelineLineBottom)
        }

        inner class EmptyViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val container: View? = itemView.findViewById(R.id.containerEmptyTaskAction)
        }
    }

    private fun resolveTaskCategoryForList(task: Task): String {
        val raw = task.category.trim()
        if (raw.isEmpty()) return inferTaskCategory(task)
        if (PENDING_TASK_CATEGORY.equals(raw, ignoreCase = true)) return PENDING_TASK_CATEGORY
        return inferTaskCategory(task)
    }
}