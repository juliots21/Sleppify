package com.example.sleppify;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.WebSettings;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.DiffUtil;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

public class WeeklySchedulerFragment extends Fragment {

    private static final int WEEKS_AHEAD = 10;
    private static final String KEY_FUTURE_INSIGHT_LOCK_DATE = "future_insight_lock_date";
    private static final String KEY_FUTURE_INSIGHT_LOCK_TASK = "future_insight_lock_task";
    private static final String KEY_FUTURE_INSIGHT_LOCK_TITLE = "future_insight_lock_title";
    private static final String KEY_FUTURE_INSIGHT_LOCK_MESSAGE = "future_insight_lock_message";
    private static final String KEY_FUTURE_INSIGHT_LOCK_ACTION = "future_insight_lock_action";
    private static final String KEY_FUTURE_INSIGHT_LOCK_MICRO = "future_insight_lock_micro";
    private static final String FUTURE_INSIGHT_ACTION_SCHEME = "sleppify";
    private static final String FUTURE_INSIGHT_ACTION_HOST = "future-insight";
    private static final String FUTURE_INSIGHT_ACTION_PATH = "/action";
    private static final String FUTURE_TASK_ACTION_HOST = "future-task";
    private static final String FUTURE_TASK_ACTION_PATH = "/menu";
    private static final String FUTURE_TASK_ADD_PATH = "/add";
    private static final String FUTURE_INSIGHT_MODE_SCHEDULE = "schedule_time";
    private static final String FUTURE_INSIGHT_MODE_FOCUS = "focus_block";
    private static final long FUTURE_INSIGHT_REFRESH_INTERVAL_MS = 90L * 60L * 1000L;
    private static final long FUTURE_INSIGHT_RETRY_INTERVAL_MS = 60L * 1000L;
        private static final String[] INSIGHT_MINUTE_VALUES = {
            "00", "05", "10", "15", "20", "25", "30", "35", "40", "45", "50", "55"
        };
    private static final String LEGACY_DEMO_TITLE = "";
    private static final String DEFAULT_TASK_DESCRIPTION = "Sin detalles adicionales";
    private static final String PENDING_TASK_DESCRIPTION = "Generando detalles con IA...";
    private static final String DEFAULT_TASK_CATEGORY = "";
    private static final String PENDING_TASK_CATEGORY = "Generando categoria con IA...";
    private static final String FALLBACK_TASK_CATEGORY = "Otros";
    private static final String TASK_METADATA_ERROR_DESCRIPTION = "Sin descripción";
    private static final String TASK_METADATA_ERROR_CATEGORY = "Fallback";
    private static final int TASK_METADATA_MAX_RETRIES = 2;
    private static final int TASK_METADATA_BACKFILL_LIMIT = 6;
    private static final int TASK_METADATA_PULL_REFRESH_LIMIT = 24;
    private static final long TASK_METADATA_BACKFILL_INTERVAL_MS = 2L * 60L * 1000L;
    private static final String FUTURE_TASKS_DYNAMIC_TOKEN = "__DYNAMIC_SECTIONS__";
    private static final String FUTURE_TASKS_THEME_BODY_CLASS_TOKEN = "__THEME_BODY_CLASS__";
    private static final int FUTURE_TASK_DAYS_LIMIT = 35;
    private static final int FUTURE_TASKS_PER_DAY_LIMIT = 12;
    private static final long AGENDA_CLOUD_REFRESH_MIN_INTERVAL_MS = 15000L;
    private static final long AGENDA_REMINDER_RESCHEDULE_MIN_INTERVAL_MS = 20000L;
    private static final Locale SPANISH_LOCALE = new Locale("es", "ES");
    private static final int TASK_TIME_COLOR_DAY = 0xFFAEC7FD;
    private static final int TASK_TIME_COLOR_NIGHT = 0xFFFFB59A;
    private static final int TASK_CARD_COLOR_DEFAULT = 0xFF191B22;
    private static final int TASK_CARD_COLOR_LONG_PRESS = 0xFF252833;

    private List<MaterialCardView> allDayCards = new ArrayList<>();
    private List<TextView> allDayLabels = new ArrayList<>();
    private List<TextView> allDayNumbers = new ArrayList<>();
    private List<TextView> allDayMonthLabels = new ArrayList<>();
    private List<Calendar> allDayDates = new ArrayList<>();
    private List<String> allDayDateKeys = new ArrayList<>();
    @Nullable
    private View.OnScrollChangeListener daysCarouselScrollListener;
    @NonNull
    private final Runnable syncCarouselHeaderRunnable = this::syncWeekRangeAndActiveMonthFromCarousel;
    private boolean isCarouselHeaderSyncQueued;
    @NonNull
    private String carouselFocusedDateKey = "";
    @NonNull
    private String carouselFocusedWeekStartKey = "";
    private int activeMonthLabelIndex = -1;
    private float daysCarouselTouchDownX;
    private float daysCarouselTouchDownY;
    private boolean daysCarouselHorizontalGesture;
    private int daysCarouselTouchSlop = -1;

    private TextView tvDateRange, tvSelectedDayTitle;
    private TextView tvFabHint;
    private ImageView ivWeekCalendarPicker;
    private LinearLayout llDaysContainer;
    private ScrollView scrollAgendaContent;
    private HorizontalScrollView hsvDaysCarousel;
    private RecyclerView rvTasks;
    private SwipeRefreshLayout swipeAgendaRefresh;
    private FloatingActionButton fabAddTask;
    private MaterialCardView cardFabHint;

    private TaskAdapter taskAdapter;
    private Map<String, List<Task>> tasksPerDay = new HashMap<>();
    private CloudSyncManager cloudSyncManager;
    private SharedPreferences settingsPrefs;
    private boolean fabLockedForAuth;
    private WebView wvFutureTasksLayer;
    private final Handler aiTaskMetadataHandler = new Handler(Looper.getMainLooper());
    private final HashSet<String> pendingTaskMetadataRequests = new HashSet<>();
    @Nullable
    private String futureTasksTemplateHtml;
    @Nullable
    private String lastFutureTasksHtmlSignature;
    private boolean futureTasksRenderScheduled;
    @NonNull
    private final Runnable renderFutureTasksRunnable = this::renderFutureTasksLayerNow;
    @NonNull
    private String lastLoadedAgendaJson = "";
    private long lastAgendaLocalRefreshAtMs;
    private long lastAgendaReminderRescheduleAtMs;
    private long lastAgendaCloudRefreshAtMs;
    private long lastTaskMetadataBackfillAtMs;
    @Nullable
    private FutureInsightModel currentFutureInsightModel;
    private boolean futureInsightRequestInFlight;
    @Nullable
    private Dialog insightTimeDialog;
    @Nullable
    private PopupWindow taskActionPopupWindow;

    private String selectedDateKey;
    private Calendar selectedDate;
    private Calendar todayDate;

    private final SimpleDateFormat dateKeyFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);

    public static class Task {
        String id;
        String title;
        String desc;
        String time;
        String category;

        public Task(String title, String desc, String time) {
            this(title, desc, time, DEFAULT_TASK_CATEGORY);
        }

        public Task(String title, String desc, String time, String category) {
            this(UUID.randomUUID().toString(), title, desc, time, category);
        }

        public Task(String id, String title, String desc, String time, String category) {
            this.id = (id == null || id.trim().isEmpty()) ? UUID.randomUUID().toString() : id.trim();
            this.title = title;
            this.desc = desc;
            this.time = time;
            this.category = category;
        }
    }

    @NonNull
    private Task copyTask(@NonNull Task source) {
        return new Task(source.id, source.title, source.desc, source.time, source.category);
    }

    @NonNull
    private List<Task> copyTaskList(@Nullable List<Task> source) {
        List<Task> copy = new ArrayList<>();
        if (source == null || source.isEmpty()) {
            return copy;
        }

        for (Task task : source) {
            if (task != null) {
                copy.add(copyTask(task));
            }
        }
        return copy;
    }

    private String getDayShortLabel(int dayOfWeek) {
        switch (dayOfWeek) {
            case Calendar.MONDAY:    return "LUN";
            case Calendar.TUESDAY:   return "MAR";
            case Calendar.WEDNESDAY: return "MIÉ";
            case Calendar.THURSDAY:  return "JUE";
            case Calendar.FRIDAY:    return "VIE";
            case Calendar.SATURDAY:  return "SÁB";
            case Calendar.SUNDAY:    return "DOM";
            default: return "";
        }
    }

    private String getDayFullLabel(int dayOfWeek) {
        switch (dayOfWeek) {
            case Calendar.MONDAY:    return "Lunes";
            case Calendar.TUESDAY:   return "Martes";
            case Calendar.WEDNESDAY: return "Miércoles";
            case Calendar.THURSDAY:  return "Jueves";
            case Calendar.FRIDAY:    return "Viernes";
            case Calendar.SATURDAY:  return "Sábado";
            case Calendar.SUNDAY:    return "Domingo";
            default: return "";
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_weekly_scheduler, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        tvDateRange = view.findViewById(R.id.tvDateRange);
        tvSelectedDayTitle = view.findViewById(R.id.tvSelectedDayTitle);
        ivWeekCalendarPicker = view.findViewById(R.id.ivWeekCalendarPicker);
        llDaysContainer = view.findViewById(R.id.llDaysContainer);
        scrollAgendaContent = view.findViewById(R.id.scrollAgendaContent);
        hsvDaysCarousel = view.findViewById(R.id.hsvDaysCarousel);
        rvTasks = view.findViewById(R.id.rvTasks);
        swipeAgendaRefresh = view.findViewById(R.id.swipeAgendaRefresh);
        fabAddTask = view.findViewById(R.id.fabAddTask);
        cardFabHint = view.findViewById(R.id.cardFabHint);
        tvFabHint = view.findViewById(R.id.tvFabHint);
        wvFutureTasksLayer = view.findViewById(R.id.wvFutureTasksLayer);

        rvTasks.setLayoutManager(new LinearLayoutManager(getContext()));
        taskAdapter = new TaskAdapter(new ArrayList<>());
        rvTasks.setAdapter(taskAdapter);
        rvTasks.setItemAnimator(null);
        rvTasks.setHasFixedSize(false);
        cloudSyncManager = CloudSyncManager.getInstance(requireContext());
        settingsPrefs = requireContext().getSharedPreferences(CloudSyncManager.PREFS_SETTINGS, Context.MODE_PRIVATE);
        configureFutureTasksWebView();
        setupAgendaPullToRefresh();
        setupDaysCarouselTouchInterception();

        setupCalendarAndDays();

        if (ivWeekCalendarPicker != null) {
            ivWeekCalendarPicker.setOnClickListener(v -> showSystemCalendarDayPicker());
        }

        loadAgendaFromLocal(true);

        String todayKey = dateKeyFormat.format(todayDate.getTime());
        selectDayByDate(todayKey, (Calendar) todayDate.clone());
        updateFabLoginState();
        fabAddTask.setOnClickListener(v -> triggerAddTaskFlow());
    }

    private void triggerAddTaskFlow() {
        showAddTaskDialog();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (cloudSyncManager == null) {
            return;
        }
        if (cloudSyncManager.isCloudEnabledForCurrentUser()) {
            long now = System.currentTimeMillis();
            if ((now - lastAgendaCloudRefreshAtMs) < AGENDA_CLOUD_REFRESH_MIN_INTERVAL_MS) {
                loadAgendaFromLocal(false);
                refreshSelectedDayTasks();
                updateFabLoginState();
                return;
            }
            lastAgendaCloudRefreshAtMs = now;
            cloudSyncManager.refreshAgendaFromCloud((success, message) -> {
                loadAgendaFromLocal(false);
                refreshSelectedDayTasks();
                updateFabLoginState();
            });
            return;
        }
        loadAgendaFromLocal(false);
        refreshSelectedDayTasks();
        updateFabLoginState();
    }

    public void onCloudAgendaHydrationCompleted() {
        if (!isAdded() || cloudSyncManager == null) {
            setAgendaPullRefreshState(false);
            return;
        }

        lastAgendaCloudRefreshAtMs = System.currentTimeMillis();
        loadAgendaFromLocal(false);
        refreshSelectedDayTasks();
        updateFabLoginState();
        setAgendaPullRefreshState(false);
    }

    private void setupAgendaPullToRefresh() {
        if (swipeAgendaRefresh == null || !isAdded()) {
            return;
        }

        swipeAgendaRefresh.setColorSchemeColors(
                ContextCompat.getColor(requireContext(), R.color.stitch_blue),
                ContextCompat.getColor(requireContext(), android.R.color.white)
        );
        swipeAgendaRefresh.setProgressBackgroundColorSchemeColor(
                ContextCompat.getColor(requireContext(), R.color.surface_low)
        );
        swipeAgendaRefresh.setDistanceToTriggerSync(Math.round(80f * getResources().getDisplayMetrics().density));
        swipeAgendaRefresh.setOnChildScrollUpCallback((parent, child) -> {
            if (scrollAgendaContent != null) {
                return scrollAgendaContent.canScrollVertically(-1);
            }
            return child != null && child.canScrollVertically(-1);
        });
        swipeAgendaRefresh.setOnRefreshListener(this::triggerAgendaPullRefresh);
    }

    private void setupDaysCarouselTouchInterception() {
        if (hsvDaysCarousel == null) {
            return;
        }

        if (daysCarouselTouchSlop < 0) {
            daysCarouselTouchSlop = ViewConfiguration.get(requireContext()).getScaledTouchSlop();
        }

        hsvDaysCarousel.setOnTouchListener((v, event) -> {
            int action = event.getActionMasked();
            switch (action) {
                case MotionEvent.ACTION_DOWN:
                    daysCarouselTouchDownX = event.getX();
                    daysCarouselTouchDownY = event.getY();
                    daysCarouselHorizontalGesture = false;
                    lockAgendaParentsForHorizontalScroll(true);
                    break;

                case MotionEvent.ACTION_MOVE:
                    float dx = event.getX() - daysCarouselTouchDownX;
                    float dy = event.getY() - daysCarouselTouchDownY;
                    if (!daysCarouselHorizontalGesture
                            && (Math.abs(dx) > daysCarouselTouchSlop || Math.abs(dy) > daysCarouselTouchSlop)) {
                        daysCarouselHorizontalGesture = Math.abs(dx) >= Math.abs(dy);
                    }

                    boolean lockParents = daysCarouselHorizontalGesture || Math.abs(dx) >= Math.abs(dy);
                    lockAgendaParentsForHorizontalScroll(lockParents);
                    break;

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    daysCarouselHorizontalGesture = false;
                    lockAgendaParentsForHorizontalScroll(false);
                    break;

                default:
                    break;
            }

            return false;
        });
    }

    private void lockAgendaParentsForHorizontalScroll(boolean lock) {
        if (scrollAgendaContent != null) {
            scrollAgendaContent.requestDisallowInterceptTouchEvent(lock);
        }
        if (swipeAgendaRefresh != null) {
            swipeAgendaRefresh.requestDisallowInterceptTouchEvent(lock);
            if (!swipeAgendaRefresh.isRefreshing()) {
                swipeAgendaRefresh.setEnabled(!lock);
            }
        }
    }

    private void setAgendaPullRefreshState(boolean refreshing) {
        if (swipeAgendaRefresh == null) {
            return;
        }
        if (swipeAgendaRefresh.isRefreshing() == refreshing) {
            return;
        }
        swipeAgendaRefresh.setRefreshing(refreshing);
    }

    private void triggerAgendaPullRefresh() {
        if (!isAdded() || cloudSyncManager == null) {
            setAgendaPullRefreshState(false);
            return;
        }

        setAgendaPullRefreshState(true);
        forceRefreshIntelligentNotesOnPullRefresh();
        if (cloudSyncManager.isCloudEnabledForCurrentUser()) {
            cloudSyncManager.refreshAgendaFromCloud((success, message) -> {
                loadAgendaFromLocal(true);
                refreshSelectedDayTasks();
                scheduleBackfillForIncompleteTaskMetadata(true, TASK_METADATA_PULL_REFRESH_LIMIT);
                updateFabLoginState();
                setAgendaPullRefreshState(false);
            });
            return;
        }

        loadAgendaFromLocal(true);
        refreshSelectedDayTasks();
        scheduleBackfillForIncompleteTaskMetadata(true, TASK_METADATA_PULL_REFRESH_LIMIT);
        updateFabLoginState();
        setAgendaPullRefreshState(false);
    }

    private void forceRefreshIntelligentNotesOnPullRefresh() {
        clearFutureInsightLocksForRefresh();
    }

    private void clearFutureInsightLocksForRefresh() {
        if (settingsPrefs == null) {
            return;
        }

        settingsPrefs.edit()
                .remove(KEY_FUTURE_INSIGHT_LOCK_DATE)
                .remove(KEY_FUTURE_INSIGHT_LOCK_TASK)
                .remove(KEY_FUTURE_INSIGHT_LOCK_TITLE)
                .remove(KEY_FUTURE_INSIGHT_LOCK_MESSAGE)
                .remove(KEY_FUTURE_INSIGHT_LOCK_ACTION)
                .remove(KEY_FUTURE_INSIGHT_LOCK_MICRO)
                .apply();

        currentFutureInsightModel = null;
        futureInsightRequestInFlight = false;
        lastFutureTasksHtmlSignature = null;
        renderFutureTasksLayer();
    }

    private void setupCalendarAndDays() {
        todayDate = Calendar.getInstance();

        // Find Monday of current week
        Calendar monday = (Calendar) todayDate.clone();
        while (monday.get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY) {
            monday.add(Calendar.DAY_OF_YEAR, -1);
        }

        int totalDays = 7 * (WEEKS_AHEAD + 1);
        SimpleDateFormat monthFmt = new SimpleDateFormat("MMM", SPANISH_LOCALE);

        llDaysContainer.removeAllViews();
        allDayCards.clear();
        allDayLabels.clear();
        allDayNumbers.clear();
        allDayMonthLabels.clear();
        allDayDates.clear();
        allDayDateKeys.clear();
        activeMonthLabelIndex = -1;

        String todayKey = dateKeyFormat.format(todayDate.getTime());

        for (int i = 0; i < totalDays; i++) {
            Calendar dayDate = (Calendar) monday.clone();
            dayDate.add(Calendar.DAY_OF_YEAR, i);

            String dateKey = dateKeyFormat.format(dayDate.getTime());
            String dayLabel = getDayShortLabel(dayDate.get(Calendar.DAY_OF_WEEK));
            String dayNumber = String.valueOf(dayDate.get(Calendar.DAY_OF_MONTH));

            allDayDates.add(dayDate);
            allDayDateKeys.add(dateKey);

            // Each day is a vertical column: [optional month label] + [card]
            LinearLayout dayColumn = new LinearLayout(requireContext());
            dayColumn.setOrientation(LinearLayout.VERTICAL);
            dayColumn.setGravity(Gravity.CENTER_HORIZONTAL);

                // Month label above every day; selected day keeps it highlighted.
                TextView monthLabel = new TextView(requireContext());
                monthLabel.setText(monthFmt.format(dayDate.getTime()).toUpperCase(SPANISH_LOCALE));
                monthLabel.setTextColor(Color.parseColor("#5B6782"));
                monthLabel.setTextSize(10f);
                monthLabel.setTypeface(monthLabel.getTypeface(), android.graphics.Typeface.BOLD);
                monthLabel.setGravity(Gravity.CENTER);
                monthLabel.setAlpha(0.55f);
                LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                mlp.bottomMargin = dpToPx(4);
                monthLabel.setLayoutParams(mlp);
                dayColumn.addView(monthLabel);
                allDayMonthLabels.add(monthLabel);

            MaterialCardView card = createDayCard(dayLabel, dayNumber);
            LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(dpToPx(60), dpToPx(76));
            card.setLayoutParams(cardParams);
            dayColumn.addView(card);

            // Column params with week separator
            LinearLayout.LayoutParams colParams = new LinearLayout.LayoutParams(
                    dpToPx(60), ViewGroup.LayoutParams.WRAP_CONTENT);
            if (dayDate.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY && i < totalDays - 1) {
                colParams.setMarginEnd(dpToPx(16));
            } else {
                colParams.setMarginEnd(dpToPx(6));
            }
            dayColumn.setLayoutParams(colParams);

            // Past days dimmed
            if (dateKey.compareTo(todayKey) < 0) {
                dayColumn.setAlpha(0.3f);
                card.setClickable(false);
            } else {
                dayColumn.setAlpha(1.0f);
                card.setClickable(true);
                final String fKey = dateKey;
                final Calendar fDate = (Calendar) dayDate.clone();
                card.setOnClickListener(v -> selectDayByDate(fKey, fDate));
            }

            allDayCards.add(card);
            llDaysContainer.addView(dayColumn);
        }

        // Scroll position: always start from Monday (index 0)
        // Exception: if today is Sunday, start from Wednesday (index 2)
        int scrollToIndex = 0; // Monday
        if (todayDate.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY) {
            scrollToIndex = 2; // Wednesday
        }
        final int finalScrollIdx = scrollToIndex;
        hsvDaysCarousel.post(() -> {
            if (finalScrollIdx < allDayCards.size()) {
                View target = allDayCards.get(finalScrollIdx);
                if (target.getParent() instanceof View) {
                    View column = (View) target.getParent();
                    int scrollX = column.getLeft();
                    hsvDaysCarousel.scrollTo(Math.max(0, scrollX), 0);
                }
            }
        });

        attachDaysCarouselScrollTracking();
        scheduleCarouselHeaderSync();
    }

    private void selectDayByDate(String dateKey, Calendar date) {
        selectedDateKey = dateKey;
        selectedDate = (Calendar) date.clone();
        carouselFocusedDateKey = dateKey;
        String todayKey = dateKeyFormat.format(todayDate.getTime());
        updateSelectedWeekRange(date);

        for (int i = 0; i < allDayCards.size(); i++) {
            String cardKey = allDayDateKeys.get(i);
            boolean isSelected = cardKey.equals(dateKey);
            boolean isToday = cardKey.equals(todayKey);
            boolean isPast = cardKey.compareTo(todayKey) < 0;

            View column = (View) allDayCards.get(i).getParent();

            if (isSelected) {
                allDayCards.get(i).setCardBackgroundColor(Color.parseColor("#6B8FF2"));
                allDayCards.get(i).setStrokeWidth(0);
                allDayLabels.get(i).setTextColor(Color.WHITE);
                allDayNumbers.get(i).setTextColor(Color.WHITE);
                if (column != null) column.setAlpha(1.0f);
            } else if (isToday) {
                allDayCards.get(i).setCardBackgroundColor(Color.parseColor("#111319"));
                allDayCards.get(i).setStrokeColor(Color.parseColor("#6B8FF2"));
                allDayCards.get(i).setStrokeWidth(dpToPx(2));
                allDayLabels.get(i).setTextColor(Color.parseColor("#AEC7FD"));
                allDayNumbers.get(i).setTextColor(Color.parseColor("#AEC7FD"));
                if (column != null) column.setAlpha(1.0f);
            } else {
                allDayCards.get(i).setCardBackgroundColor(Color.parseColor("#111319"));
                allDayCards.get(i).setStrokeWidth(0);
                allDayLabels.get(i).setTextColor(Color.parseColor("#BDBDBD"));
                allDayNumbers.get(i).setTextColor(Color.WHITE);
                if (column != null) column.setAlpha(isPast ? 0.3f : 1.0f);
            }
        }
        updateMonthLabelsForActiveDate(dateKey);

        String dayName = getDayFullLabel(date.get(Calendar.DAY_OF_WEEK));
        int dayNum = date.get(Calendar.DAY_OF_MONTH);
        if (dateKey.equals(todayKey)) {
            tvSelectedDayTitle.setText("Tareas de hoy — " + dayName);
        } else {
            tvSelectedDayTitle.setText("Tareas del " + dayName + " " + dayNum);
        }

        List<Task> dayTasks = tasksPerDay.getOrDefault(dateKey, new ArrayList<>());
        taskAdapter.setTasks(dayTasks);
        updateTasksRecyclerHeight();
    }

    private void attachDaysCarouselScrollTracking() {
        if (hsvDaysCarousel == null || daysCarouselScrollListener != null) {
            return;
        }

        daysCarouselScrollListener = (v, scrollX, scrollY, oldScrollX, oldScrollY) -> {
            if (scrollX != oldScrollX) {
                scheduleCarouselHeaderSync();
            }
        };
        hsvDaysCarousel.setOnScrollChangeListener(daysCarouselScrollListener);
    }

    private void scheduleCarouselHeaderSync() {
        if (hsvDaysCarousel == null) {
            return;
        }

        if (isCarouselHeaderSyncQueued) {
            return;
        }

        isCarouselHeaderSyncQueued = true;
        hsvDaysCarousel.postOnAnimation(syncCarouselHeaderRunnable);
    }

    private void syncWeekRangeAndActiveMonthFromCarousel() {
        isCarouselHeaderSyncQueued = false;

        if (hsvDaysCarousel == null || allDayCards.isEmpty() || allDayDates.isEmpty() || allDayDateKeys.isEmpty()) {
            return;
        }

        int focusedIndex = resolveFocusedDayIndexFromCarousel();
        if (focusedIndex < 0 || focusedIndex >= allDayDates.size() || focusedIndex >= allDayDateKeys.size()) {
            return;
        }

        Calendar focusedDate = allDayDates.get(focusedIndex);
        if (focusedDate != null) {
            int focusedWeekStartIndex = focusedIndex - (focusedIndex % 7);
            String focusedWeekStartKey = focusedWeekStartIndex >= 0 && focusedWeekStartIndex < allDayDateKeys.size()
                    ? allDayDateKeys.get(focusedWeekStartIndex)
                    : "";
            if (!TextUtils.equals(carouselFocusedWeekStartKey, focusedWeekStartKey)) {
                carouselFocusedWeekStartKey = focusedWeekStartKey;
                Calendar weekStartDate = focusedWeekStartIndex >= 0 && focusedWeekStartIndex < allDayDates.size()
                        ? allDayDates.get(focusedWeekStartIndex)
                        : focusedDate;
                updateSelectedWeekRange(weekStartDate);
            }
        }

        String focusedDateKey = allDayDateKeys.get(focusedIndex);
        if (!TextUtils.equals(carouselFocusedDateKey, focusedDateKey)) {
            carouselFocusedDateKey = focusedDateKey;
            updateMonthLabelsForActiveDate(focusedDateKey);
        }
    }

    private int resolveFocusedDayIndexFromCarousel() {
        if (hsvDaysCarousel == null || allDayCards.isEmpty()) {
            return -1;
        }

        int viewportCenterX = hsvDaysCarousel.getScrollX() + (hsvDaysCarousel.getWidth() / 2);
        int bestIndex = -1;
        int bestDistance = Integer.MAX_VALUE;

        for (int i = 0; i < allDayCards.size(); i++) {
            View card = allDayCards.get(i);
            if (card == null || !(card.getParent() instanceof View)) {
                continue;
            }

            View column = (View) card.getParent();
            int columnCenterX = column.getLeft() + (column.getWidth() / 2);
            int distance = Math.abs(columnCenterX - viewportCenterX);
            if (distance < bestDistance) {
                bestDistance = distance;
                bestIndex = i;
            }
        }

        return bestIndex;
    }

    private void updateMonthLabelsForActiveDate(@Nullable String activeDateKey) {
        if (allDayMonthLabels.isEmpty() || allDayDateKeys.isEmpty()) {
            return;
        }

        int targetIndex = -1;
        if (!TextUtils.isEmpty(activeDateKey)) {
            targetIndex = allDayDateKeys.indexOf(activeDateKey);
        }

        if (targetIndex == activeMonthLabelIndex) {
            return;
        }

        if (activeMonthLabelIndex >= 0 && activeMonthLabelIndex < allDayMonthLabels.size()) {
            TextView previous = allDayMonthLabels.get(activeMonthLabelIndex);
            if (previous != null) {
                previous.setTextColor(Color.parseColor("#5B6782"));
                previous.setAlpha(0.55f);
            }
        }

        if (targetIndex >= 0 && targetIndex < allDayMonthLabels.size()) {
            TextView active = allDayMonthLabels.get(targetIndex);
            if (active != null) {
                active.setTextColor(Color.parseColor("#AEC7FD"));
                active.setAlpha(1f);
            }
        }

        activeMonthLabelIndex = targetIndex;
    }

    private void updateSelectedWeekRange(@NonNull Calendar selectedDateValue) {
        if (tvDateRange == null) {
            return;
        }

        Calendar weekStart = getWeekStart(selectedDateValue);
        Calendar weekEnd = (Calendar) weekStart.clone();
        weekEnd.add(Calendar.DAY_OF_MONTH, 6);

        SimpleDateFormat rangeFmt = new SimpleDateFormat("d MMMM", SPANISH_LOCALE);
        tvDateRange.setText(rangeFmt.format(weekStart.getTime()) + " - " + rangeFmt.format(weekEnd.getTime()));

        carouselFocusedWeekStartKey = dateKeyFormat.format(weekStart.getTime());
    }

    private void updateTasksRecyclerHeight() {
        if (rvTasks == null) {
            return;
        }

        ViewGroup.LayoutParams params = rvTasks.getLayoutParams();
        if (params != null && params.height != ViewGroup.LayoutParams.WRAP_CONTENT) {
            params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            rvTasks.setLayoutParams(params);
        }
    }

    private void showSystemCalendarDayPicker() {
        Calendar initial = selectedDate != null ? (Calendar) selectedDate.clone() : (Calendar) todayDate.clone();

        android.app.DatePickerDialog datePicker = new android.app.DatePickerDialog(
                requireContext(),
                (view, year, month, dayOfMonth) -> {
                    Calendar picked = Calendar.getInstance();
                    picked.set(year, month, dayOfMonth, 0, 0, 0);
                    picked.set(Calendar.MILLISECOND, 0);
                    String pickedKey = dateKeyFormat.format(picked.getTime());
                    selectDayByDate(pickedKey, picked);
                    scrollCarouselToDate(pickedKey);
                },
                initial.get(Calendar.YEAR),
                initial.get(Calendar.MONTH),
                initial.get(Calendar.DAY_OF_MONTH)
        );

        datePicker.getDatePicker().setMinDate(todayDate.getTimeInMillis());
        Calendar maxDate = (Calendar) todayDate.clone();
        maxDate.add(Calendar.WEEK_OF_YEAR, WEEKS_AHEAD);
        datePicker.getDatePicker().setMaxDate(maxDate.getTimeInMillis());
        datePicker.show();
    }

    private void scrollCarouselToDate(@NonNull String dateKey) {
        for (int i = 0; i < allDayDateKeys.size(); i++) {
            String key = allDayDateKeys.get(i);
            if (!dateKey.equals(key)) {
                continue;
            }

            View card = allDayCards.get(i);
            if (!(card.getParent() instanceof View)) {
                return;
            }

            View column = (View) card.getParent();
            int scrollX = Math.max(0, column.getLeft() - dpToPx(12));
            hsvDaysCarousel.post(() -> hsvDaysCarousel.smoothScrollTo(scrollX, 0));
            return;
        }
    }

    private MaterialCardView createDayCard(String label, String dayNumber) {
        MaterialCardView card = new MaterialCardView(requireContext());
        card.setCardBackgroundColor(Color.parseColor("#111319"));
        card.setRadius(dpToPx(12));
        card.setStrokeWidth(0);
        card.setUseCompatPadding(false);

        LinearLayout content = new LinearLayout(requireContext());
        content.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER);

        TextView tvLabel = new TextView(requireContext());
        tvLabel.setText(label);
        tvLabel.setTextSize(11f);
        tvLabel.setTextColor(Color.parseColor("#BDBDBD"));
        tvLabel.setGravity(Gravity.CENTER);

        TextView tvNumber = new TextView(requireContext());
        tvNumber.setText(dayNumber);
        tvNumber.setTextSize(18f);
        tvNumber.setTextColor(Color.WHITE);
        tvNumber.setTypeface(tvNumber.getTypeface(), android.graphics.Typeface.BOLD);
        tvNumber.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams numParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        numParams.topMargin = dpToPx(4);
        tvNumber.setLayoutParams(numParams);

        content.addView(tvLabel);
        content.addView(tvNumber);
        card.addView(content);

        allDayLabels.add(tvLabel);
        allDayNumbers.add(tvNumber);
        return card;
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    private void showAddTaskDialog() {
        showAddTaskDialog(null, null);
    }

    private void showAddTaskDialog(@Nullable Task taskToEdit, @Nullable String taskDateKey) {
        com.google.android.material.bottomsheet.BottomSheetDialog bottomSheetDialog =
                new com.google.android.material.bottomsheet.BottomSheetDialog(requireContext());

        View sheetView = LayoutInflater.from(getContext()).inflate(R.layout.dialog_add_task, null);
        bottomSheetDialog.setContentView(sheetView);

        TextView tvTitleLabel = sheetView.findViewById(R.id.tvDialogTitle); // Need to check if this ID exists or add it
        com.google.android.material.textfield.TextInputEditText etTitle = sheetView.findViewById(R.id.etTaskTitle);
        com.google.android.material.textfield.TextInputEditText etDesc = sheetView.findViewById(R.id.etTaskDesc);
        TextView tvTaskDay = sheetView.findViewById(R.id.tvTaskDay);
        com.google.android.material.card.MaterialCardView cardDayDisplay = sheetView.findViewById(R.id.cardDayDisplay);
        com.google.android.material.button.MaterialButton btnSaveTask = sheetView.findViewById(R.id.btnSaveTask);

        boolean isEditMode = taskToEdit != null;
        if (isEditMode) {
            if (tvTitleLabel != null) tvTitleLabel.setText("Editar tarea");
            etTitle.setText(taskToEdit.title);
            etDesc.setText(isPlaceholderDescription(taskToEdit.desc) ? "" : taskToEdit.desc);
            btnSaveTask.setText("Actualizar Tarea");
        } else {
            if (tvTitleLabel != null) tvTitleLabel.setText("Nueva tarea");
            btnSaveTask.setText("Guardar Tarea");
        }

        // Local date for this dialog (can be changed via date picker)
        final String[] dialogDateKey = { isEditMode ? taskDateKey : selectedDateKey };
        final Calendar[] dialogDate = { isEditMode ? parseDateFromKey(taskDateKey) : (Calendar) selectedDate.clone() };

        // Show selected day
        updateDayDisplay(tvTaskDay, dialogDate[0]);

        // Date picker on day card tap
        cardDayDisplay.setOnClickListener(v -> {
            Calendar initial = dialogDate[0];
            android.app.DatePickerDialog datePicker = new android.app.DatePickerDialog(
                    requireContext(),
                    (view, year, month, dayOfMonth) -> {
                        Calendar picked = Calendar.getInstance();
                        picked.set(year, month, dayOfMonth, 0, 0, 0);
                        dialogDateKey[0] = dateKeyFormat.format(picked.getTime());
                        dialogDate[0] = picked;
                        updateDayDisplay(tvTaskDay, picked);
                    },
                    initial.get(Calendar.YEAR),
                    initial.get(Calendar.MONTH),
                    initial.get(Calendar.DAY_OF_MONTH)
            );
            datePicker.getDatePicker().setMinDate(todayDate.getTimeInMillis());
            // Max: 10 weeks ahead
            Calendar maxDate = (Calendar) todayDate.clone();
            maxDate.add(Calendar.WEEK_OF_YEAR, WEEKS_AHEAD);
            datePicker.getDatePicker().setMaxDate(maxDate.getTimeInMillis());
            datePicker.show();
        });

        // Enter acts as button
        etTitle.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEND ||
                    actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE) {
                btnSaveTask.performClick();
                return true;
            }
            return false;
        });

        // Guardado inmediato + enriquecimiento IA en background
        btnSaveTask.setOnClickListener(v -> {
            String title = etTitle.getText().toString().trim();
            if (title.isEmpty()) {
                
                return;
            }

            String userDescription = etDesc.getText().toString().trim();
            
            // Logic: if it already has description (manual or previous AI), do not override.
            // If it's a new task with no description, AI will generate it.
            boolean allowDescriptionOverride = userDescription.isEmpty();
            String resolvedDescription = allowDescriptionOverride ? PENDING_TASK_DESCRIPTION : userDescription;

            String saveKey = dialogDateKey[0];
            
            if (isEditMode) {
                // Remove old if date changed
                if (!taskDateKey.equals(saveKey)) {
                    List<Task> oldDayTasks = tasksPerDay.get(taskDateKey);
                    if (oldDayTasks != null) oldDayTasks.remove(taskToEdit);
                }
                
                taskToEdit.title = title;
                taskToEdit.desc = resolvedDescription;
                
                List<Task> currentTasks = tasksPerDay.get(saveKey);
                if (currentTasks == null) {
                    currentTasks = new ArrayList<>();
                    tasksPerDay.put(saveKey, currentTasks);
                }
                if (!currentTasks.contains(taskToEdit)) {
                    currentTasks.add(taskToEdit);
                }
                
                persistAgenda();
                renderFutureTasksLayer();
                selectDayByDate(saveKey, dialogDate[0]);
                bottomSheetDialog.dismiss();
            } else {
                List<Task> currentTasks = tasksPerDay.get(saveKey);
                if (currentTasks == null) {
                    currentTasks = new ArrayList<>();
                    tasksPerDay.put(saveKey, currentTasks);
                }

                Task createdTask = new Task(title, resolvedDescription, "", PENDING_TASK_CATEGORY);
                currentTasks.add(createdTask);
                persistAgenda();
                renderFutureTasksLayer();

                selectDayByDate(saveKey, dialogDate[0]);
                bottomSheetDialog.dismiss();
            }
        });

        bottomSheetDialog.show();
    }

    private void requestTaskMetadataEnrichment(
            @NonNull String dateKey,
            @NonNull Task task,
            boolean allowDescriptionOverride
    ) {
        String requestKey = buildTaskMetadataRequestKey(dateKey, task);
        if (!pendingTaskMetadataRequests.add(requestKey)) {
            return;
        }
        enrichTaskWithAiMetadataInBackground(dateKey, task, allowDescriptionOverride, 0, requestKey);
    }

    private void enrichTaskWithAiMetadataInBackground(
            @NonNull String dateKey,
            @NonNull Task task,
            boolean allowDescriptionOverride,
            int retryAttempt,
            @NonNull String requestKey
    ) {
        if (GeminiIntelligenceService.isSuspended()) {
            Task targetTask = findTaskInDateBucket(dateKey, task);
            if (targetTask != null) {
                applyLocalMetadataFallback(targetTask, allowDescriptionOverride);
                persistAgenda();
                renderFutureTasksLayer();
                refreshSelectedDayTasks();
            }
            markTaskMetadataRequestFinished(requestKey);
            return;
        }

        new GeminiIntelligenceService().generateTaskMetadataWithCategory(task.title, new GeminiIntelligenceService.TaskMetadataCallback() {
            @Override
            public void onSuccess(GeminiIntelligenceService.TaskMetadata metadata) {
                Task targetTask = findTaskInDateBucket(dateKey, task);
                if (targetTask == null) {
                    markTaskMetadataRequestFinished(requestKey);
                    return;
                }

                boolean changed = false;
                boolean shouldOverrideCategory = isPlaceholderCategory(targetTask.category);

                if (shouldOverrideCategory) {
                    String normalizedCategory = normalizeTaskCategory(metadata.getCategory());
                    if (!TextUtils.isEmpty(normalizedCategory)
                            && !TextUtils.equals(targetTask.category, normalizedCategory)) {
                        targetTask.category = normalizedCategory;
                        changed = true;
                    }
                }

                boolean shouldOverrideDescription = allowDescriptionOverride
                        && isPlaceholderDescription(targetTask.desc);
                if (shouldOverrideDescription && !TextUtils.isEmpty(metadata.getDescription())) {
                    String cleanDescription = metadata.getDescription().trim();
                    if (!TextUtils.isEmpty(cleanDescription) && !TextUtils.equals(targetTask.desc, cleanDescription)) {
                        targetTask.desc = cleanDescription;
                        changed = true;
                    }
                }

                if (!changed) {
                    markTaskMetadataRequestFinished(requestKey);
                    return;
                }

                persistAgenda();
                renderFutureTasksLayer();
                refreshSelectedDayTasks();
                markTaskMetadataRequestFinished(requestKey);
            }

            @Override
            public void onError(String error) {
                Task targetTask = findTaskInDateBucket(dateKey, task);
                if (targetTask == null) {
                    markTaskMetadataRequestFinished(requestKey);
                    return;
                }

                boolean retryable = isRetryableTaskMetadataError(error);
                // Removed automatic retry loop to respect user request for 'Pull to Refresh only' logic.
                // Every failure now immediately applies the local fallback.
                boolean changed = applyLocalMetadataFallback(targetTask, allowDescriptionOverride);
                if (changed) {
                    persistAgenda();
                    renderFutureTasksLayer();
                    refreshSelectedDayTasks();
                }
                markTaskMetadataRequestFinished(requestKey);
            }
        });
    }

    private long computeTaskMetadataRetryDelayMs(int retryAttempt) {
        return 1200L + (retryAttempt * 1300L);
    }

    private boolean isRetryableTaskMetadataError(@Nullable String error) {
        if (TextUtils.isEmpty(error)) {
            return true;
        }

        String normalized = error.toLowerCase(Locale.US);
        if (normalized.contains("429")
                || normalized.contains("503")
                || normalized.contains("timeout")
                || normalized.contains("timed out")
                || normalized.contains("network")
                || normalized.contains("sin respuesta")) {
            return true;
        }

        if (normalized.contains("404")
                || normalized.contains("403")
                || normalized.contains("401")
                || normalized.contains("400")
                || normalized.contains("parse")
                || normalized.contains("json")) {
            return false;
        }

        return true;
    }

    private boolean applyLocalMetadataFallback(@NonNull Task task, boolean allowDescriptionOverride) {
        boolean changed = false;

        if (allowDescriptionOverride && isPlaceholderDescription(task.desc)) {
            String fallbackDescription = TASK_METADATA_ERROR_DESCRIPTION;
            if (!TextUtils.equals(task.desc, fallbackDescription)) {
                task.desc = fallbackDescription;
                changed = true;
            }
        }

        if (isPlaceholderCategory(task.category)) {
            String fallbackCategory = TASK_METADATA_ERROR_CATEGORY;
            if (!TextUtils.equals(task.category, fallbackCategory)) {
                task.category = fallbackCategory;
                changed = true;
            }
        }

        return changed;
    }

    @NonNull
    private String buildLocalTaskDescription(@Nullable String taskTitle) {
        String cleanTitle = taskTitle == null ? "" : taskTitle.trim();
        cleanTitle = cleanTitle.replaceAll("[\\.;:,]+$", "").trim();
        if (cleanTitle.isEmpty()) {
            return "Definir un siguiente paso claro y completar esta tarea hoy.";
        }
        return "Completar " + cleanTitle + " con un paso concreto y medible.";
    }

    @NonNull
    private String buildLocalTaskCategory(@Nullable String taskTitle) {
        if (TextUtils.isEmpty(taskTitle)) {
            return "Pendiente";
        }

        String normalized = taskTitle
                .toLowerCase(SPANISH_LOCALE)
                .replaceAll("[^\\p{L}\\p{N} ]", " ")
                .replaceAll("\\s+", " ")
                .trim();
        if (normalized.isEmpty()) {
            return "Pendiente";
        }

        String[] stopwords = {
                "de", "la", "el", "los", "las", "un", "una", "unos", "unas",
                "y", "o", "en", "con", "para", "por", "del", "al", "a", "mi",
                "tu", "su", "hoy", "manana", "mañana", "tarea", "tareas"
        };
        HashSet<String> blocked = new HashSet<>();
        Collections.addAll(blocked, stopwords);

        String[] tokens = normalized.split(" ");
        for (String token : tokens) {
            if (token.length() < 3 || blocked.contains(token)) {
                continue;
            }
            String candidate = normalizeTaskCategory(token);
            if (!TextUtils.isEmpty(candidate)) {
                return candidate;
            }
        }
        return "Pendiente";
    }

    private void markTaskMetadataRequestFinished(@NonNull String requestKey) {
        pendingTaskMetadataRequests.remove(requestKey);
    }

    @Nullable
    private Task findTaskInDateBucket(@NonNull String dateKey, @NonNull Task fallbackTask) {
        List<Task> dayTasks = tasksPerDay.get(dateKey);
        if (dayTasks == null || dayTasks.isEmpty()) {
            return null;
        }

        for (Task task : dayTasks) {
            if (task == fallbackTask) {
                return task;
            }
        }

        for (Task task : dayTasks) {
            String normalizedCategoryTask = normalizeTaskCategory(task.category);
            String normalizedCategoryFallback = normalizeTaskCategory(fallbackTask.category);
            boolean sameCategory = TextUtils.equals(normalizedCategoryTask, normalizedCategoryFallback);
            boolean flexibleDescription = isPlaceholderDescription(task.desc)
                    || isPlaceholderDescription(fallbackTask.desc);
            if (TextUtils.equals(task.title, fallbackTask.title)
                    && TextUtils.equals(task.time, fallbackTask.time)
                    && (TextUtils.equals(task.desc, fallbackTask.desc) || sameCategory || flexibleDescription)) {
                return task;
            }
        }

        return null;
    }

    private boolean isPlaceholderDescription(@Nullable String description) {
        if (TextUtils.isEmpty(description)) {
            return true;
        }
        String normalized = description.trim();
        return normalized.isEmpty()
                || DEFAULT_TASK_DESCRIPTION.equalsIgnoreCase(normalized)
                || PENDING_TASK_DESCRIPTION.equalsIgnoreCase(normalized)
                || TASK_METADATA_ERROR_DESCRIPTION.equalsIgnoreCase(normalized)
                || isLocalFallbackDescription(normalized);
    }

    private boolean isLocalFallbackDescription(@Nullable String description) {
        if (TextUtils.isEmpty(description)) {
            return false;
        }

        String normalized = description.trim();
        if (normalized.isEmpty()) {
            return false;
        }

        String lower = normalized.toLowerCase(SPANISH_LOCALE);
        return lower.startsWith("organizar y completar:")
                || lower.equals("organizar esta tarea y completarla durante el dia.")
                || lower.equals("organizar esta tarea y completarla durante el dia");
    }

    private boolean isPlaceholderCategory(@Nullable String category) {
        if (TextUtils.isEmpty(category)) {
            return true;
        }

        String raw = category.trim();
        if (raw.isEmpty()) {
            return true;
        }
        String lowerRaw = raw.toLowerCase(SPANISH_LOCALE);
        if (PENDING_TASK_CATEGORY.equalsIgnoreCase(raw)
                || FALLBACK_TASK_CATEGORY.equalsIgnoreCase(raw)
                || TASK_METADATA_ERROR_CATEGORY.equalsIgnoreCase(raw)
                || lowerRaw.startsWith("generando")) {
            return true;
        }

        String normalized = normalizeTaskCategory(raw);
        if (TextUtils.isEmpty(normalized)) {
            return true;
        }

        String lower = normalized.toLowerCase(Locale.US);
        return "otros".equals(lower)
            || "otro".equals(lower)
            || "organizacion".equals(lower)
            || "organización".equals(lower)
            || "general".equals(lower)
            || "misc".equals(lower)
            || "sin".equals(lower)
            || lower.startsWith("generando");
    }

    private boolean shouldGenerateTaskMetadata(@NonNull Task task) {
        return isPlaceholderDescription(task.desc) || isPlaceholderCategory(task.category);
    }

    @NonNull
    private String buildTaskMetadataRequestKey(@NonNull String dateKey, @NonNull Task task) {
        String id = task.id == null ? "" : task.id.trim();
        if (!id.isEmpty()) {
            return dateKey + "|" + id;
        }
        String title = task.title == null ? "" : task.title.trim();
        String time = task.time == null ? "" : task.time.trim();
        return dateKey + "|" + title + "|" + time;
    }

    private void scheduleBackfillForIncompleteTaskMetadata() {
        scheduleBackfillForIncompleteTaskMetadata(false, TASK_METADATA_BACKFILL_LIMIT);
    }

    private void scheduleBackfillForIncompleteTaskMetadata(boolean includePastDays, int maxRequests) {
        if (tasksPerDay.isEmpty()) {
            return;
        }

        int requested = 0;
        String todayKey = dateKeyFormat.format(Calendar.getInstance().getTime());
        TreeMap<String, List<Task>> ordered = new TreeMap<>(tasksPerDay);

        for (Map.Entry<String, List<Task>> entry : ordered.entrySet()) {
            String dateKey = entry.getKey();
            if (!includePastDays && dateKey.compareTo(todayKey) < 0) {
                continue;
            }

            List<Task> dayTasks = entry.getValue();
            if (dayTasks == null || dayTasks.isEmpty()) {
                continue;
            }

            for (Task task : dayTasks) {
                if (task == null || TextUtils.isEmpty(task.title) || !shouldGenerateTaskMetadata(task)) {
                    continue;
                }

                requestTaskMetadataEnrichment(dateKey, task, isPlaceholderDescription(task.desc));
                requested++;
                if (maxRequests > 0 && requested >= maxRequests) {
                    return;
                }
            }
        }
    }

    private void updateDayDisplay(TextView tvTaskDay, Calendar date) {
        String dayName = getDayFullLabel(date.get(Calendar.DAY_OF_WEEK));
        int dayNum = date.get(Calendar.DAY_OF_MONTH);
        SimpleDateFormat monthFmt = new SimpleDateFormat("MMMM", Locale.getDefault());
        String monthName = monthFmt.format(date.getTime());
        tvTaskDay.setText(dayName + " " + dayNum + " de " + monthName);
        tvTaskDay.setTextColor(Color.WHITE);
        tvTaskDay.setTextSize(16);
    }

    private void loadAgendaFromLocal() {
        loadAgendaFromLocal(false);
    }

    private void loadAgendaFromLocal(boolean forceRefresh) {
        if (cloudSyncManager == null) {
            return;
        }

        String rawAgendaJson = cloudSyncManager.getLocalAgendaJson();
        if (rawAgendaJson == null) {
            rawAgendaJson = "";
        }

        long now = System.currentTimeMillis();
        boolean sameAgendaPayload = TextUtils.equals(lastLoadedAgendaJson, rawAgendaJson);
        if (!forceRefresh
                && sameAgendaPayload
            && !tasksPerDay.isEmpty()) {
            clearLockedFutureInsightIfResolved();
            renderFutureTasksLayer();
            maybeScheduleTaskMetadataBackfill(forceRefresh, sameAgendaPayload, now);
            return;
        }

        Map<String, List<Task>> parsed = parseAgendaJson(rawAgendaJson);
        tasksPerDay.clear();
        tasksPerDay.putAll(parsed);
        lastLoadedAgendaJson = rawAgendaJson;
        lastAgendaLocalRefreshAtMs = now;

        boolean removedLegacyDemo = removeLegacyApolloTasks(tasksPerDay);
        if (removedLegacyDemo) {
            persistAgenda();
        }
        clearLockedFutureInsightIfResolved();
        if (forceRefresh
                || !sameAgendaPayload
                || (now - lastAgendaReminderRescheduleAtMs) >= AGENDA_REMINDER_RESCHEDULE_MIN_INTERVAL_MS) {
            TaskReminderScheduler.rescheduleAll(requireContext().getApplicationContext());
            lastAgendaReminderRescheduleAtMs = now;
        }
        renderFutureTasksLayer();
        maybeScheduleTaskMetadataBackfill(forceRefresh, sameAgendaPayload, now);
    }

    private void maybeScheduleTaskMetadataBackfill(
            boolean forceRefresh,
            boolean sameAgendaPayload,
            long now
    ) {
        // AI enrichment only runs if manually triggered via forceRefresh (Pull to Refresh)
        if (cloudSyncManager == null || !cloudSyncManager.isCloudEnabledForCurrentUser() || !forceRefresh) {
            return;
        }
        lastTaskMetadataBackfillAtMs = now;
        scheduleBackfillForIncompleteTaskMetadata();
    }

    private boolean removeLegacyApolloTasks(@NonNull Map<String, List<Task>> data) {
        boolean removedAny = false;

        Iterator<Map.Entry<String, List<Task>>> dayIterator = data.entrySet().iterator();
        while (dayIterator.hasNext()) {
            Map.Entry<String, List<Task>> entry = dayIterator.next();
            List<Task> dayTasks = entry.getValue();
            if (dayTasks == null || dayTasks.isEmpty()) {
                continue;
            }

            Iterator<Task> taskIterator = dayTasks.iterator();
            while (taskIterator.hasNext()) {
                Task task = taskIterator.next();
                String title = task != null && task.title != null ? task.title.trim() : "";
                if (title.equalsIgnoreCase(LEGACY_DEMO_TITLE)) {
                    taskIterator.remove();
                    removedAny = true;
                }
            }

            if (dayTasks.isEmpty()) {
                dayIterator.remove();
            }
        }
        return removedAny;
    }

    private void refreshSelectedDayTasks() {
        if (selectedDateKey != null && selectedDate != null) {
            selectDayByDate(selectedDateKey, (Calendar) selectedDate.clone());
        }
    }

    private void setFabAuthLocked(boolean locked) {
        fabLockedForAuth = locked;
        if (fabAddTask != null) {
            fabAddTask.setEnabled(!locked);
            fabAddTask.setAlpha(locked ? 0.55f : fabAddTask.getAlpha());
        }
        if (cardFabHint != null) {
            cardFabHint.setVisibility(View.VISIBLE);
        }
        if (tvFabHint != null) {
            tvFabHint.setText(locked ? "Sincronizando cuenta..." : "Inicia sesion para usar +");
        }
    }

    private void updateFabLoginState() {
        if (fabAddTask == null) {
            return;
        }

        if (fabLockedForAuth) {
            fabAddTask.setEnabled(false);
            fabAddTask.setAlpha(0.55f);
            if (cardFabHint != null) {
                cardFabHint.setVisibility(View.VISIBLE);
            }
            if (tvFabHint != null) {
                tvFabHint.setText("Sincronizando cuenta...");
            }
            return;
        }

        fabAddTask.setEnabled(true);
        fabAddTask.setAlpha(1f);
        if (cardFabHint != null) {
            cardFabHint.setVisibility(View.GONE);
        }
    }

    private void showSubtleMessage(@NonNull String message) {
        View root = getView();
        if (root == null) {
            return;
        }
        Snackbar.make(root, message, Snackbar.LENGTH_SHORT).show();
    }

    @NonNull
    private String resolveProfileNameForAi() {
        if (getActivity() instanceof MainActivity) {
            MainActivity mainActivity = (MainActivity) getActivity();
            if (mainActivity.getAuthManager() != null && mainActivity.getAuthManager().isSignedIn()) {
                return mainActivity.getAuthManager().getDisplayName();
            }
        }
        return "usuario";
    }

    @NonNull
    private String buildBehaviorSignalsForAi() {
        String todayKey = dateKeyFormat.format(Calendar.getInstance().getTime());
        int upcomingDaysWithTasks = 0;
        int tasksWithoutTime = 0;
        int tasksWithTime = 0;

        for (Map.Entry<String, List<Task>> entry : new TreeMap<>(tasksPerDay).entrySet()) {
            if (entry.getKey().compareTo(todayKey) < 0) {
                continue;
            }

            List<Task> dayTasks = entry.getValue();
            if (dayTasks == null || dayTasks.isEmpty()) {
                continue;
            }

            upcomingDaysWithTasks++;
            for (Task task : dayTasks) {
                if (task == null || TextUtils.isEmpty(task.time)) {
                    tasksWithoutTime++;
                } else {
                    tasksWithTime++;
                }
            }
        }

        return "dias_con_tareas=" + upcomingDaysWithTasks
                + ", tareas_con_hora=" + tasksWithTime
                + ", tareas_sin_hora=" + tasksWithoutTime;
    }

    @NonNull
    private String buildAgendaSnapshotForAi() {
        String todayKey = dateKeyFormat.format(Calendar.getInstance().getTime());

        int totalUpcomingTasks = 0;
        int morningTasks = 0;
        int afternoonTasks = 0;
        int nightTasks = 0;
        Map<String, Integer> keywordCounts = new HashMap<>();
        List<String> examples = new ArrayList<>();
        TreeMap<String, List<Task>> ordered = new TreeMap<>(tasksPerDay);

        for (Map.Entry<String, List<Task>> entry : ordered.entrySet()) {
            String date = entry.getKey();
            if (date.compareTo(todayKey) < 0) {
                continue;
            }

            List<Task> dayTasks = entry.getValue();
            if (dayTasks == null || dayTasks.isEmpty()) {
                continue;
            }

            for (Task task : dayTasks) {
                totalUpcomingTasks++;
                int hour = parseHourFromTaskTime(task.time);
                if (hour >= 0) {
                    if (hour >= 5 && hour < 12) {
                        morningTasks++;
                    } else if (hour >= 12 && hour < 19) {
                        afternoonTasks++;
                    } else {
                        nightTasks++;
                    }
                }

                accumulateKeywords(task.title + " " + task.desc, keywordCounts);

                if (examples.size() < 14) {
                    examples.add(date + " - " + task.title);
                }
            }
        }

        String topKeywords = buildTopKeywords(keywordCounts, 6);
        return "total_tareas=" + totalUpcomingTasks +
                "; franjas{manana=" + morningTasks + ", tarde=" + afternoonTasks + ", noche=" + nightTasks + "}" +
                "; top_keywords=" + topKeywords +
                "; ejemplos=" + examples;
    }

    private int parseHourFromTaskTime(@Nullable String rawTime) {
        if (TextUtils.isEmpty(rawTime)) {
            return -1;
        }

        String value = rawTime.trim().toUpperCase(Locale.US);
        try {
            boolean isPm = value.contains("PM");
            boolean isAm = value.contains("AM");

            String numeric = value.replace("AM", "").replace("PM", "").trim();
            String[] split = numeric.split(":");
            int hour = Integer.parseInt(split[0].trim());
            if (isPm && hour < 12) {
                hour += 12;
            }
            if (isAm && hour == 12) {
                hour = 0;
            }
            return Math.max(0, Math.min(23, hour));
        } catch (Exception ignored) {
            return -1;
        }
    }

    private boolean isDaytimeHour(int hour24) {
        return hour24 >= 6 && hour24 < 19;
    }

    private void accumulateKeywords(@NonNull String text, @NonNull Map<String, Integer> bucket) {
        String[] stopwords = {
                "de", "la", "el", "y", "en", "con", "para", "por", "del", "las", "los",
                "una", "uno", "que", "sin", "hoy", "tarea", "tareas", "sesion", "proyecto"
        };
        HashSet<String> stopwordSet = new HashSet<>();
        Collections.addAll(stopwordSet, stopwords);

        String normalized = text.toLowerCase(Locale.US).replaceAll("[^a-z0-9áéíóúñ ]", " ");
        String[] parts = normalized.split("\\s+");
        for (String word : parts) {
            if (word.length() < 4 || stopwordSet.contains(word)) {
                continue;
            }
            int current = bucket.containsKey(word) ? bucket.get(word) : 0;
            bucket.put(word, current + 1);
        }
    }

    @NonNull
    private String buildTopKeywords(@NonNull Map<String, Integer> keywordCounts, int maxItems) {
        if (keywordCounts.isEmpty()) {
            return "sin_datos";
        }

        List<Map.Entry<String, Integer>> entries = new ArrayList<>(keywordCounts.entrySet());
        Collections.sort(entries, (a, b) -> Integer.compare(b.getValue(), a.getValue()));

        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < entries.size() && i < maxItems; i++) {
            if (i > 0) {
                builder.append(",");
            }
            builder.append(entries.get(i).getKey());
        }
        return builder.toString();
    }

    private void persistAgenda() {
        if (cloudSyncManager == null) {
            return;
        }
        cloudSyncManager.syncAgendaJson(serializeAgendaJson(tasksPerDay));
        TaskReminderScheduler.rescheduleAll(requireContext().getApplicationContext());
    }

    @NonNull
    private String serializeAgendaJson(@NonNull Map<String, List<Task>> data) {
        try {
            JSONObject root = new JSONObject();
            for (Map.Entry<String, List<Task>> entry : data.entrySet()) {
                JSONArray tasks = new JSONArray();
                for (Task task : entry.getValue()) {
                    JSONObject taskObj = new JSONObject();
                    taskObj.put("id", task.id);
                    taskObj.put("title", task.title);
                    taskObj.put("desc", task.desc);
                    taskObj.put("time", task.time);
                    taskObj.put("category", normalizeTaskCategory(task.category));
                    tasks.put(taskObj);
                }
                root.put(entry.getKey(), tasks);
            }
            return root.toString();
        } catch (Exception ignored) {
            return "{}";
        }
    }

    @NonNull
    private Map<String, List<Task>> parseAgendaJson(@Nullable String rawJson) {
        Map<String, List<Task>> parsed = new HashMap<>();
        if (TextUtils.isEmpty(rawJson)) {
            return parsed;
        }

        try {
            JSONObject root = new JSONObject(rawJson);
            Iterator<String> keyIterator = root.keys();
            while (keyIterator.hasNext()) {
                String dateKey = keyIterator.next();
                JSONArray tasksArray = root.optJSONArray(dateKey);
                if (tasksArray == null) {
                    continue;
                }

                List<Task> dayTasks = new ArrayList<>();
                for (int i = 0; i < tasksArray.length(); i++) {
                    JSONObject taskObj = tasksArray.optJSONObject(i);
                    if (taskObj == null) {
                        continue;
                    }

                    String id = taskObj.optString("id", "").trim();

                    String title = taskObj.optString("title", "").trim();
                    if (title.isEmpty()) {
                        continue;
                    }

                    String desc = taskObj.optString("desc", "Sin detalles adicionales").trim();
                        String time = taskObj.optString("time", "").trim();
                        String category = normalizeTaskCategory(taskObj.optString("category", DEFAULT_TASK_CATEGORY));
                    dayTasks.add(new Task(
                            id,
                            title,
                            desc.isEmpty() ? DEFAULT_TASK_DESCRIPTION : desc,
                            time,
                            category
                    ));
                }

                if (!dayTasks.isEmpty()) {
                    parsed.put(dateKey, dayTasks);
                }
            }
        } catch (Exception ignored) {
            return parsed;
        }
        return parsed;
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void configureFutureTasksWebView() {
        if (wvFutureTasksLayer == null) {
            return;
        }

        WebSettings settings = wvFutureTasksLayer.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setSupportMultipleWindows(false);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            settings.setAllowFileAccessFromFileURLs(false);
            settings.setAllowUniversalAccessFromFileURLs(false);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            settings.setSafeBrowsingEnabled(true);
        }

        wvFutureTasksLayer.setVerticalScrollBarEnabled(false);
        wvFutureTasksLayer.setHorizontalScrollBarEnabled(false);
        wvFutureTasksLayer.setBackgroundColor(Color.TRANSPARENT);
        wvFutureTasksLayer.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String rawUrl = request != null && request.getUrl() != null
                        ? request.getUrl().toString()
                        : null;
                return handleFutureTasksActionUrl(rawUrl);
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return handleFutureTasksActionUrl(url);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                adjustFutureTasksWebViewHeight(view);
            }
        });
        renderFutureTasksLayer();
    }

    private boolean handleFutureTasksActionUrl(@Nullable String rawUrl) {
        if (TextUtils.isEmpty(rawUrl)) {
            return true;
        }

        Uri uri;
        try {
            uri = Uri.parse(rawUrl);
        } catch (Exception ignored) {
            return true;
        }

        if (!FUTURE_INSIGHT_ACTION_SCHEME.equalsIgnoreCase(uri.getScheme())) {
            return true;
        }

        String host = uri.getHost();
        String path = uri.getPath();

        if (FUTURE_INSIGHT_ACTION_HOST.equalsIgnoreCase(host)
                && FUTURE_INSIGHT_ACTION_PATH.equalsIgnoreCase(path)) {
            String mode = uri.getQueryParameter("mode");
            String dateKey = uri.getQueryParameter("date");
            String taskTitle = uri.getQueryParameter("task");
            handleFutureInsightAction(mode, dateKey, taskTitle);
            return true;
        }

        if (FUTURE_TASK_ACTION_HOST.equalsIgnoreCase(host)
                && FUTURE_TASK_ACTION_PATH.equalsIgnoreCase(path)) {
            String dateKey = uri.getQueryParameter("date");
            String taskTitle = uri.getQueryParameter("task");
            String taskTime = uri.getQueryParameter("time");
            String xStr = uri.getQueryParameter("x");
            String yStr = uri.getQueryParameter("y");
            Float x = null;
            Float y = null;
            try {
                if (xStr != null) x = Float.parseFloat(xStr);
                if (yStr != null) y = Float.parseFloat(yStr);
            } catch (Exception ignored) {}
            handleFutureTaskCardAction(dateKey, taskTitle, taskTime, x, y);
            return true;
        }

        if (FUTURE_TASK_ACTION_HOST.equalsIgnoreCase(host)
                && FUTURE_TASK_ADD_PATH.equalsIgnoreCase(path)) {
            triggerAddTaskFlow();
            return true;
        }

        return true;
    }

    private void handleFutureTaskCardAction(
            @Nullable String dateKey,
            @Nullable String taskTitle,
            @Nullable String taskTime,
            @Nullable Float x,
            @Nullable Float y
    ) {
        TaskSelection selection = resolveFutureTaskSelection(dateKey, taskTitle, taskTime);
        if (selection == null) {
            showSubtleMessage(getString(R.string.agenda_task_not_found_toast));
            return;
        }

        Float absoluteX = x;
        Float absoluteY = y;
        if (x != null && y != null && wvFutureTasksLayer != null) {
            float density = getResources().getDisplayMetrics().density;
            int[] loc = new int[2];
            wvFutureTasksLayer.getLocationInWindow(loc);
            
            // WebView clientX/Y are logical pixels (DP), convert to physical pixels
            absoluteX = (x * density) + loc[0];
            absoluteY = (y * density) + loc[1];
        }
        showTaskActionTooltip(wvFutureTasksLayer, selection, absoluteX, absoluteY);
    }

    private void handleFutureInsightAction(
            @Nullable String mode,
            @Nullable String dateKey,
            @Nullable String taskTitle
    ) {
        if (FUTURE_INSIGHT_MODE_SCHEDULE.equals(mode)) {
            TaskSelection selection = resolveScheduleTaskSelection(dateKey, taskTitle);
            if (selection == null) {
                showSubtleMessage("No hay tareas sin horario pendientes en este momento.");
                return;
            }
            showSystemTimePickerForInsightTask(selection);
            return;
        }

        String microAction = currentFutureInsightModel != null ? currentFutureInsightModel.microAction : "";
        if (TextUtils.isEmpty(microAction)) {
            microAction = "Inicia un bloque de enfoque de 25 minutos para tu tarea mas importante.";
        }
        showSubtleMessage(microAction);
    }

    @Nullable
    private TaskSelection resolveScheduleTaskSelection(@Nullable String dateKey, @Nullable String taskTitle) {
        if (!TextUtils.isEmpty(dateKey) && !TextUtils.isEmpty(taskTitle)) {
            List<Task> dayTasks = tasksPerDay.get(dateKey);
            if (dayTasks != null) {
                for (Task task : dayTasks) {
                    if (TextUtils.equals(task.title, taskTitle) && TextUtils.isEmpty(task.time)) {
                        return new TaskSelection(dateKey, task);
                    }
                }
                for (Task task : dayTasks) {
                    if (TextUtils.equals(task.title, taskTitle)) {
                        return new TaskSelection(dateKey, task);
                    }
                }
            }
        }
        return findFirstTaskWithoutTimeFromToday();
    }

    @Nullable
    private TaskSelection resolveFutureTaskSelection(
            @Nullable String dateKey,
            @Nullable String taskTitle,
            @Nullable String taskTime
    ) {
        if (TextUtils.isEmpty(dateKey) || TextUtils.isEmpty(taskTitle)) {
            return null;
        }

        List<Task> dayTasks = tasksPerDay.get(dateKey);
        if (dayTasks == null || dayTasks.isEmpty()) {
            return null;
        }

        String normalizedTime = taskTime == null ? "" : taskTime.trim();
        for (Task task : dayTasks) {
            String candidateTime = task.time == null ? "" : task.time.trim();
            if (TextUtils.equals(task.title, taskTitle) && TextUtils.equals(candidateTime, normalizedTime)) {
                return new TaskSelection(dateKey, task);
            }
        }

        for (Task task : dayTasks) {
            if (TextUtils.equals(task.title, taskTitle)) {
                return new TaskSelection(dateKey, task);
            }
        }

        return null;
    }

    private void showTaskActionTooltip(@Nullable View anchor, @NonNull TaskSelection selection) {
        showTaskActionTooltip(anchor, selection, null, null);
    }

    private void showTaskActionTooltip(
            @Nullable View anchor,
            @NonNull TaskSelection selection,
            @Nullable Float rawTouchX,
            @Nullable Float rawTouchY
    ) {
        if (!isAdded()) {
            return;
        }

        Task targetTask = findTaskInDateBucket(selection.dateKey, selection.task);
        if (targetTask == null) {
            showSubtleMessage(getString(R.string.agenda_task_not_found_toast));
            return;
        }

        View menuAnchor = anchor != null ? anchor : rvTasks;
        if (menuAnchor == null) {
            return;
        }

        dismissTaskActionTooltip();

        View contentView = LayoutInflater.from(requireContext()).inflate(R.layout.view_task_action_tooltip, null, false);
        View llEdit = contentView.findViewById(R.id.llTaskTooltipEdit);
        View llSetTime = contentView.findViewById(R.id.llTaskTooltipSetTime);
        View llDelete = contentView.findViewById(R.id.llTaskTooltipDelete);
        View divider = contentView.findViewById(R.id.dividerTaskTooltip);
        View dividerEdit = contentView.findViewById(R.id.dividerTaskTooltipEdit);

        if (llEdit != null) {
            llEdit.setOnClickListener(v -> {
                dismissTaskActionTooltip();
                showAddTaskDialog(targetTask, selection.dateKey);
            });
        }

        boolean hasTime = hasTaskScheduledTime(targetTask);
        if (llSetTime != null) {
            llSetTime.setVisibility(hasTime ? View.GONE : View.VISIBLE);
            llSetTime.setOnClickListener(v -> {
                dismissTaskActionTooltip();
                showSystemTimePickerForInsightTask(selection);
            });
        }

        if (divider != null) {
            divider.setVisibility(hasTime ? View.GONE : View.VISIBLE);
        }

        if (llDelete != null) {
            llDelete.setOnClickListener(v -> {
                dismissTaskActionTooltip();
                showDeleteTaskConfirmation(selection);
            });
        }

        PopupWindow popupWindow = new PopupWindow(
                contentView,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                true
        );
        popupWindow.setOutsideTouchable(true);
        popupWindow.setFocusable(false);
        popupWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            popupWindow.setElevation(dpToPx(8));
        }
        popupWindow.setOnDismissListener(() -> {
            if (taskActionPopupWindow == popupWindow) {
                taskActionPopupWindow = null;
            }
        });

        taskActionPopupWindow = popupWindow;

        if (rawTouchX != null && rawTouchY != null && getView() != null
                && !Float.isNaN(rawTouchX) && !Float.isNaN(rawTouchY)) {
            View rootView = requireView();
            int[] rootLocation = new int[2];
            rootView.getLocationOnScreen(rootLocation);

            contentView.measure(
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            );
            int popupWidth = contentView.getMeasuredWidth();
            int popupHeight = contentView.getMeasuredHeight();

            int margin = dpToPx(12);
            int screenWidth = rootView.getWidth();
            int screenHeight = rootView.getHeight();

            // Initial desired position: Below-Right of touch point
            int desiredX = Math.round(rawTouchX) + dpToPx(8);
            int desiredY = Math.round(rawTouchY) + dpToPx(8);

            // Vertical Flip Logic: If it hits the bottom, show above the touch point
            if (desiredY + popupHeight > rootLocation[1] + screenHeight - margin) {
                desiredY = Math.round(rawTouchY) - popupHeight - dpToPx(12);
            }

            // Horizontal Flip Logic: If it hits the right edge, show to the left of the touch point
            if (desiredX + popupWidth > rootLocation[0] + screenWidth - margin) {
                desiredX = Math.round(rawTouchX) - popupWidth - dpToPx(8);
            }

            // Final Safety Clamping: Ensure it stays within root view bounds with margin
            int clampedX = Math.max(rootLocation[0] + margin, 
                                   Math.min(desiredX, rootLocation[0] + screenWidth - popupWidth - margin));
            int clampedY = Math.max(rootLocation[1] + margin, 
                                   Math.min(desiredY, rootLocation[1] + screenHeight - popupHeight - margin));

            popupWindow.showAtLocation(rootView, Gravity.NO_GRAVITY, clampedX, clampedY);
            return;
        }

        int xOffset = dpToPx(8);
        int yOffset = dpToPx(4);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            popupWindow.showAsDropDown(menuAnchor, xOffset, yOffset, Gravity.START);
        } else {
            popupWindow.showAsDropDown(menuAnchor, xOffset, yOffset);
        }
    }

    private void dismissTaskActionTooltip() {
        if (taskActionPopupWindow != null) {
            taskActionPopupWindow.dismiss();
            taskActionPopupWindow = null;
        }
    }

    private boolean hasTaskScheduledTime(@NonNull Task task) {
        return parseHourFromTaskTime(task.time) >= 0;
    }

    private void showDeleteTaskConfirmation(@NonNull TaskSelection selection) {
        if (!isAdded()) {
            return;
        }

        Task targetTask = findTaskInDateBucket(selection.dateKey, selection.task);
        if (targetTask == null) {
            showSubtleMessage(getString(R.string.agenda_task_not_found_toast));
            return;
        }

        String safeTitle = TextUtils.isEmpty(targetTask.title) ? "tarea" : targetTask.title.trim();
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.agenda_task_delete_title)
                .setMessage(getString(R.string.agenda_task_delete_confirm_message, safeTitle))
                .setNegativeButton(R.string.agenda_action_cancel, null)
                .setPositiveButton(R.string.agenda_action_delete, (dialog, which) -> deleteTaskFromAgenda(selection))
                .show();
    }

    private void deleteTaskFromAgenda(@NonNull TaskSelection selection) {
        Task targetTask = findTaskInDateBucket(selection.dateKey, selection.task);
        if (targetTask == null) {
            showSubtleMessage(getString(R.string.agenda_task_not_found_toast));
            return;
        }

        List<Task> dayTasks = tasksPerDay.get(selection.dateKey);
        if (dayTasks == null || dayTasks.isEmpty()) {
            showSubtleMessage(getString(R.string.agenda_task_not_found_toast));
            return;
        }

        boolean removed = dayTasks.remove(targetTask);
        if (!removed) {
            int fallbackIndex = -1;
            for (int i = 0; i < dayTasks.size(); i++) {
                Task candidate = dayTasks.get(i);
                if (TextUtils.equals(candidate.title, targetTask.title)
                        && TextUtils.equals(candidate.time, targetTask.time)
                        && TextUtils.equals(candidate.desc, targetTask.desc)
                        && TextUtils.equals(candidate.category, targetTask.category)) {
                    fallbackIndex = i;
                    break;
                }
            }
            if (fallbackIndex >= 0) {
                dayTasks.remove(fallbackIndex);
                removed = true;
            }
        }

        if (!removed) {
            showSubtleMessage(getString(R.string.agenda_task_not_found_toast));
            return;
        }

        if (dayTasks.isEmpty()) {
            tasksPerDay.remove(selection.dateKey);
        }

        persistAgenda();
        currentFutureInsightModel = null;
        renderFutureTasksLayer();

        if (TextUtils.equals(selectedDateKey, selection.dateKey)) {
            refreshSelectedDayTasks();
        }

        String safeTitle = TextUtils.isEmpty(targetTask.title) ? "tarea" : targetTask.title.trim();
        showSubtleMessage(getString(R.string.agenda_task_deleted_toast, safeTitle));
    }

    private void showSystemTimePickerForInsightTask(@NonNull TaskSelection selection) {
        if (!isAdded()) {
            return;
        }

        if (insightTimeDialog != null && insightTimeDialog.isShowing()) {
            insightTimeDialog.dismiss();
        }

        Dialog dialog = new Dialog(requireContext());
        dialog.setContentView(R.layout.dialog_insight_time_picker);
        dialog.setCancelable(true);

        Window window = dialog.getWindow();
        if (window != null) {
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setDimAmount(0.68f);
        }

        View root = dialog.findViewById(R.id.insightDialogRoot);
        View card = dialog.findViewById(R.id.cardInsightTimePicker);
        TextView tvTaskTitle = dialog.findViewById(R.id.tvInsightDialogTaskTitle);
        TextView tvPreviewHour = dialog.findViewById(R.id.tvInsightPreviewHour);
        TextView tvPreviewMinute = dialog.findViewById(R.id.tvInsightPreviewMinute);
        TextView tvPreviewPeriod = dialog.findViewById(R.id.tvInsightPreviewPeriod);
        NumberPicker npHour = dialog.findViewById(R.id.npInsightHour);
        NumberPicker npMinute = dialog.findViewById(R.id.npInsightMinute);
        NumberPicker npPeriod = dialog.findViewById(R.id.npInsightPeriod);

        MaterialButton btnChip15Min = dialog.findViewById(R.id.btnInsightChip15Min);
        MaterialButton btnChip1Hour = dialog.findViewById(R.id.btnInsightChip1Hour);
        MaterialButton btnChip12Hours = dialog.findViewById(R.id.btnInsightChip12Hours);
        MaterialButton btnChip24Hours = dialog.findViewById(R.id.btnInsightChip24Hours);
        MaterialButton btnConfirm = dialog.findViewById(R.id.btnInsightConfirm);
        MaterialButton btnCancel = dialog.findViewById(R.id.btnInsightCancel);

        if (tvTaskTitle != null) {
            String safeTitle = TextUtils.isEmpty(selection.task.title) ? "Tarea" : selection.task.title.trim();
            tvTaskTitle.setText(safeTitle);
        }

        if (npHour == null || npMinute == null || npPeriod == null) {
            dialog.dismiss();
            return;
        }

        configureInsightHourPicker(npHour);
        configureInsightMinutePicker(npMinute);
        configureInsightPeriodPicker(npPeriod);

        InsightTimeSelection initialSelection = resolveInitialInsightSelection(selection.task.time);
        setInsightSelectionToPickers(npHour, npMinute, npPeriod, initialSelection);
        updateInsightTimePreview(tvPreviewHour, tvPreviewMinute, tvPreviewPeriod, npHour, npMinute, npPeriod);

        NumberPicker.OnValueChangeListener listener = (picker, oldVal, newVal) ->
                updateInsightTimePreview(tvPreviewHour, tvPreviewMinute, tvPreviewPeriod, npHour, npMinute, npPeriod);
        npHour.setOnValueChangedListener(listener);
        npMinute.setOnValueChangedListener(listener);
        npPeriod.setOnValueChangedListener(listener);

        if (root != null) {
            root.setOnClickListener(v -> dialog.dismiss());
        }
        if (card != null) {
            card.setOnClickListener(v -> {
                // Consume clicks so tap outside closes without closing on card tap.
            });
        }

        if (btnChip15Min != null) {
            btnChip15Min.setOnClickListener(v -> {
                Calendar target = Calendar.getInstance();
                target.add(Calendar.MINUTE, 15);
                setInsightSelectionToPickers(npHour, npMinute, npPeriod,
                        new InsightTimeSelection(target.get(Calendar.HOUR_OF_DAY), target.get(Calendar.MINUTE)));
                updateInsightTimePreview(tvPreviewHour, tvPreviewMinute, tvPreviewPeriod, npHour, npMinute, npPeriod);
            });
        }

        if (btnChip1Hour != null) {
            btnChip1Hour.setOnClickListener(v -> {
                Calendar target = Calendar.getInstance();
                target.add(Calendar.HOUR_OF_DAY, 1);
                setInsightSelectionToPickers(npHour, npMinute, npPeriod,
                        new InsightTimeSelection(target.get(Calendar.HOUR_OF_DAY), target.get(Calendar.MINUTE)));
                updateInsightTimePreview(tvPreviewHour, tvPreviewMinute, tvPreviewPeriod, npHour, npMinute, npPeriod);
            });
        }

        if (btnChip12Hours != null) {
            btnChip12Hours.setOnClickListener(v -> {
                Calendar target = Calendar.getInstance();
                target.add(Calendar.HOUR_OF_DAY, 12);
                setInsightSelectionToPickers(npHour, npMinute, npPeriod,
                        new InsightTimeSelection(target.get(Calendar.HOUR_OF_DAY), target.get(Calendar.MINUTE)));
                updateInsightTimePreview(tvPreviewHour, tvPreviewMinute, tvPreviewPeriod, npHour, npMinute, npPeriod);
            });
        }

        if (btnChip24Hours != null) {
            btnChip24Hours.setOnClickListener(v -> {
                Calendar target = Calendar.getInstance();
                target.add(Calendar.HOUR_OF_DAY, 24);
                setInsightSelectionToPickers(npHour, npMinute, npPeriod,
                        new InsightTimeSelection(target.get(Calendar.HOUR_OF_DAY), target.get(Calendar.MINUTE)));
                updateInsightTimePreview(tvPreviewHour, tvPreviewMinute, tvPreviewPeriod, npHour, npMinute, npPeriod);
            });
        }

        if (btnCancel != null) {
            btnCancel.setOnClickListener(v -> dialog.dismiss());
        }

        if (btnConfirm != null) {
            btnConfirm.setOnClickListener(v -> {
                InsightTimeSelection chosen = readInsightSelectionFromPickers(npHour, npMinute, npPeriod);
                dialog.dismiss();
                applyInsightTaskSchedule(selection, chosen.hourOfDay24, chosen.minute);
            });
        }

        dialog.setOnDismissListener(d -> {
            if (insightTimeDialog == dialog) {
                insightTimeDialog = null;
            }
        });

        insightTimeDialog = dialog;
        dialog.show();
    }

    private void configureInsightHourPicker(@NonNull NumberPicker picker) {
        picker.setMinValue(1);
        picker.setMaxValue(12);
        picker.setWrapSelectorWheel(true);
        picker.setDescendantFocusability(NumberPicker.FOCUS_BLOCK_DESCENDANTS);
    }

    private void configureInsightMinutePicker(@NonNull NumberPicker picker) {
        picker.setMinValue(0);
        picker.setMaxValue(INSIGHT_MINUTE_VALUES.length - 1);
        picker.setDisplayedValues(null);
        picker.setDisplayedValues(INSIGHT_MINUTE_VALUES);
        picker.setWrapSelectorWheel(true);
        picker.setDescendantFocusability(NumberPicker.FOCUS_BLOCK_DESCENDANTS);
    }

    private void configureInsightPeriodPicker(@NonNull NumberPicker picker) {
        picker.setMinValue(0);
        picker.setMaxValue(1);
        picker.setDisplayedValues(null);
        picker.setDisplayedValues(new String[]{"AM", "PM"});
        picker.setWrapSelectorWheel(true);
        picker.setDescendantFocusability(NumberPicker.FOCUS_BLOCK_DESCENDANTS);
    }

    @NonNull
    private Calendar parseDateFromKey(@Nullable String dateKey) {
        Calendar cal = Calendar.getInstance();
        if (TextUtils.isEmpty(dateKey)) {
            return cal;
        }
        try {
            Date date = dateKeyFormat.parse(dateKey);
            if (date != null) {
                cal.setTime(date);
            }
        } catch (Exception ignored) {}
        return cal;
    }

    @NonNull
    private InsightTimeSelection resolveInitialInsightSelection(@Nullable String rawTime) {
        int hour = parseHourFromTaskTime(rawTime);
        int minute = parseMinuteFromTaskTime(rawTime);

        if (hour < 0) {
            Calendar now = Calendar.getInstance();
            hour = now.get(Calendar.HOUR_OF_DAY);
            minute = now.get(Calendar.MINUTE);
        }

        return new InsightTimeSelection(hour, minute);
    }

    private int parseMinuteFromTaskTime(@Nullable String rawTime) {
        if (TextUtils.isEmpty(rawTime)) {
            return 0;
        }

        String value = rawTime.trim().toUpperCase(Locale.US);
        try {
            String numeric = value.replace("AM", "").replace("PM", "").trim();
            String[] split = numeric.split(":");
            if (split.length < 2) {
                return 0;
            }

            int minute = Integer.parseInt(split[1].replaceAll("[^0-9]", "").trim());
            return Math.max(0, Math.min(59, minute));
        } catch (Exception ignored) {
            return 0;
        }
    }

    private void setInsightSelectionToPickers(
            @NonNull NumberPicker npHour,
            @NonNull NumberPicker npMinute,
            @NonNull NumberPicker npPeriod,
            @NonNull InsightTimeSelection selection
    ) {
        int safeHour24 = Math.max(0, Math.min(23, selection.hourOfDay24));
        int minuteRounded = Math.max(0, Math.min(59, selection.minute));

        int hour12 = safeHour24 % 12;
        if (hour12 == 0) {
            hour12 = 12;
        }

        int periodValue = safeHour24 >= 12 ? 1 : 0;
        int minuteIndex = minuteRounded / 5;
        if (minuteIndex >= INSIGHT_MINUTE_VALUES.length) {
            minuteIndex = INSIGHT_MINUTE_VALUES.length - 1;
        }

        npHour.setValue(hour12);
        npMinute.setValue(minuteIndex);
        npPeriod.setValue(periodValue);
    }

    @NonNull
    private InsightTimeSelection readInsightSelectionFromPickers(
            @NonNull NumberPicker npHour,
            @NonNull NumberPicker npMinute,
            @NonNull NumberPicker npPeriod
    ) {
        int hour12 = Math.max(1, Math.min(12, npHour.getValue()));
        int minute = Math.max(0, Math.min(55, npMinute.getValue() * 5));
        boolean isPm = npPeriod.getValue() == 1;

        int hour24 = hour12 % 12;
        if (isPm) {
            hour24 += 12;
        }

        return new InsightTimeSelection(hour24, minute);
    }

    private void updateInsightTimePreview(
            @Nullable TextView tvHour,
            @Nullable TextView tvMinute,
            @Nullable TextView tvPeriod,
            @NonNull NumberPicker npHour,
            @NonNull NumberPicker npMinute,
            @NonNull NumberPicker npPeriod
    ) {
        if (tvHour == null || tvMinute == null || tvPeriod == null) {
            return;
        }

        InsightTimeSelection selection = readInsightSelectionFromPickers(npHour, npMinute, npPeriod);
        int hour12 = selection.hourOfDay24 % 12;
        if (hour12 == 0) {
            hour12 = 12;
        }

        tvHour.setText(String.format(Locale.US, "%02d", hour12));
        tvMinute.setText(String.format(Locale.US, "%02d", selection.minute));
        tvPeriod.setText(selection.hourOfDay24 >= 12 ? "PM" : "AM");
    }

    private void applyInsightTaskSchedule(
            @NonNull TaskSelection selection,
            int hourOfDay,
            int minute
    ) {
        Task targetTask = findTaskInDateBucket(selection.dateKey, selection.task);
        if (targetTask == null) {
            showSubtleMessage("La tarea seleccionada ya no esta disponible.");
            return;
        }

        targetTask.time = formatTaskTimeForDisplay(hourOfDay, minute);
        List<Task> dayTasks = tasksPerDay.get(selection.dateKey);
        if (dayTasks != null) {
            dayTasks.sort((left, right) -> Integer.compare(extractTaskSortKey(left.time), extractTaskSortKey(right.time)));
        }

        clearLockedFutureInsightForDate(selection.dateKey);
        persistAgenda();
        currentFutureInsightModel = null;
        renderFutureTasksLayer();

        if (TextUtils.equals(selectedDateKey, selection.dateKey)) {
            refreshSelectedDayTasks();
        }

        showSubtleMessage("Horario guardado para \"" + targetTask.title + "\" a las " + targetTask.time + ".");
    }

    @NonNull
    private String formatTaskTimeForDisplay(int hourOfDay, int minute) {
        int safeHour = Math.max(0, Math.min(23, hourOfDay));
        int safeMinute = Math.max(0, Math.min(59, minute));
        int displayHour = safeHour % 12;
        if (displayHour == 0) {
            displayHour = 12;
        }
        String suffix = safeHour >= 12 ? "PM" : "AM";
        return String.format(Locale.US, "%d:%02d %s", displayHour, safeMinute, suffix);
    }

    private void adjustFutureTasksWebViewHeight(@NonNull WebView webView) {
        webView.post(() -> {
            if (!isAdded()) {
                return;
            }

            ViewGroup.LayoutParams params = webView.getLayoutParams();
            if (params == null) {
                return;
            }

            float scale = Math.max(1f, webView.getScale());
            int contentHeight = (int) Math.ceil(webView.getContentHeight() * scale);
            if (contentHeight <= 0) {
                return;
            }

            int targetHeight = contentHeight + dpToPx(24);
            if (params.height != targetHeight) {
                params.height = targetHeight;
                webView.setLayoutParams(params);
            }
        });
    }

    @Override
    public void onDestroyView() {
        aiTaskMetadataHandler.removeCallbacksAndMessages(null);
        lockAgendaParentsForHorizontalScroll(false);
        setAgendaPullRefreshState(false);
        if (hsvDaysCarousel != null) {
            hsvDaysCarousel.removeCallbacks(syncCarouselHeaderRunnable);
            if (daysCarouselScrollListener != null) {
                hsvDaysCarousel.setOnScrollChangeListener(null);
            }
        }
        isCarouselHeaderSyncQueued = false;
        daysCarouselScrollListener = null;
        carouselFocusedDateKey = "";
        carouselFocusedWeekStartKey = "";
        activeMonthLabelIndex = -1;
        lastFutureTasksHtmlSignature = null;
        futureTasksTemplateHtml = null;
        futureInsightRequestInFlight = false;
        futureTasksRenderScheduled = false;

        if (insightTimeDialog != null) {
            insightTimeDialog.dismiss();
            insightTimeDialog = null;
        }

        dismissTaskActionTooltip();

        if (wvFutureTasksLayer != null) {
            try {
                wvFutureTasksLayer.removeCallbacks(renderFutureTasksRunnable);
                wvFutureTasksLayer.stopLoading();
                wvFutureTasksLayer.loadUrl("about:blank");
                wvFutureTasksLayer.removeAllViews();
                wvFutureTasksLayer.destroy();
            } catch (Exception ignored) {
            }
            wvFutureTasksLayer = null;
        }

        super.onDestroyView();
    }

    private void renderFutureTasksLayer() {
        if (!isAdded() || wvFutureTasksLayer == null) {
            return;
        }

        if (futureTasksRenderScheduled) {
            return;
        }

        futureTasksRenderScheduled = true;
        wvFutureTasksLayer.removeCallbacks(renderFutureTasksRunnable);
        wvFutureTasksLayer.post(renderFutureTasksRunnable);
    }

    private void renderFutureTasksLayerNow() {
        futureTasksRenderScheduled = false;
        if (!isAdded() || wvFutureTasksLayer == null) {
            return;
        }

        String template = getFutureTasksTemplateHtml();
        if (TextUtils.isEmpty(template)) {
            return;
        }

        String sectionsHtml = buildFutureTaskSectionsHtml();
        String themeBodyClass = isAmoledModeEnabled() ? "amoled" : "";
        String html = template
            .replace(FUTURE_TASKS_DYNAMIC_TOKEN, sectionsHtml)
            .replace(FUTURE_TASKS_THEME_BODY_CLASS_TOKEN, themeBodyClass);
        String signature = String.valueOf(html.hashCode());
        if (TextUtils.equals(signature, lastFutureTasksHtmlSignature)) {
            return;
        }

        lastFutureTasksHtmlSignature = signature;
        wvFutureTasksLayer.loadDataWithBaseURL("https://sleppify.local/", html, "text/html", "utf-8", null);
    }

    @NonNull
    private String getFutureTasksTemplateHtml() {
        if (!TextUtils.isEmpty(futureTasksTemplateHtml)) {
            return futureTasksTemplateHtml;
        }

        try (InputStream inputStream = requireContext().getResources().openRawResource(R.raw.agenda_future_tasks_template);
             InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
             BufferedReader bufferedReader = new BufferedReader(inputStreamReader)) {

            StringBuilder builder = new StringBuilder();
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                builder.append(line).append('\n');
            }
            futureTasksTemplateHtml = builder.toString();
            return futureTasksTemplateHtml;
        } catch (Exception ignored) {
            return "";
        }
    }

    private boolean isAmoledModeEnabled() {
        return settingsPrefs != null
                && settingsPrefs.getBoolean(CloudSyncManager.KEY_AMOLED_MODE_ENABLED, false);
    }

    @NonNull
    private String buildFutureTaskSectionsHtml() {
        List<FutureDayBlock> dayBlocks = collectFutureDayBlocks();
        if (dayBlocks.isEmpty()) {
            return buildEmptyFutureTasksSectionHtml();
        }

        TaskSelection noTimeSelection = findFirstTaskWithoutTimeFromToday();
        String insightSignature = buildFutureInsightSignature(dayBlocks, noTimeSelection);
        FutureInsightModel insightModel = resolveFutureInsightModel(dayBlocks, noTimeSelection, insightSignature);
        requestFutureInsightFromAiIfNeeded(dayBlocks, noTimeSelection, insightSignature);
        String insightTargetDateKey = resolveFutureInsightTargetDateKey(dayBlocks, insightModel, noTimeSelection);
        boolean insightInserted = false;

        StringBuilder builder = new StringBuilder();
        String baseDateKey = dayBlocks.get(0).dateKey;
        int totalFutureTasks = 0;
        for (FutureDayBlock dayBlock : dayBlocks) {
            totalFutureTasks += dayBlock.tasks.size();
        }
        boolean showGlobalTimeline = totalFutureTasks > 1;
        int globalTaskIndex = 0;

        for (int dayIndex = 0; dayIndex < dayBlocks.size(); dayIndex++) {
            FutureDayBlock block = dayBlocks.get(dayIndex);
            boolean isCurrentCycle = dayIndex == 0;
            String cycleLabel = buildFutureCycleLabel(baseDateKey, block.dateKey, isCurrentCycle);

            builder.append("<section class=\"")
                    .append(isCurrentCycle ? "mb-16" : "")
                    .append("\">");
            builder.append("<header class=\"mb-8 flex items-baseline justify-between\">");
            builder.append("<h2 class=\"font-headline text-3xl font-bold tracking-tight ")
                    .append(isCurrentCycle ? "text-on-surface" : "text-on-surface/60")
                    .append("\">")
                    .append(TextUtils.htmlEncode(block.displayDate))
                    .append("</h2>");
                builder.append("<span class=\"cycle-label\">")
                    .append(TextUtils.htmlEncode(cycleLabel.toUpperCase(SPANISH_LOCALE)))
                    .append("</span>");
            builder.append("</header>");

            builder.append("<div class=\"task-list\">");
            for (int taskIndex = 0; taskIndex < block.tasks.size(); taskIndex++) {
                Task task = block.tasks.get(taskIndex);
                builder.append(buildFutureTaskCardHtml(block.dateKey, task, globalTaskIndex, totalFutureTasks, showGlobalTimeline));
                globalTaskIndex++;
            }

            if (!insightInserted && TextUtils.equals(block.dateKey, insightTargetDateKey)) {
                builder.append(buildFutureInsightHtml(insightModel));
                insightInserted = true;
            }
            builder.append("</div>");
            builder.append("</section>");
        }
        return builder.toString();
    }

    @NonNull
    private String buildFutureTaskCardHtml(
            @NonNull String dateKey,
            @NonNull Task task,
            int globalTaskIndex,
            int totalFutureTasks,
            boolean showTimeline
    ) {
        String category = inferTaskCategory(task);
        String taskTime = task.time == null ? "" : task.time.trim();

        StringBuilder cardBuilder = new StringBuilder();
        cardBuilder.append("<div class=\"task-card\" ")
            .append("data-date=\"").append(TextUtils.htmlEncode(dateKey)).append("\" ")
            .append("data-title=\"").append(TextUtils.htmlEncode(task.title)).append("\" ")
            .append("data-time=\"").append(TextUtils.htmlEncode(taskTime)).append("\">")
            .append("<div class=\"task-card-head\">")
            .append("<h3 class=\"task-title\">")
                .append(TextUtils.htmlEncode(task.title))
                .append("</h3>")
            .append("<div class=\"task-meta\">");

        if (!TextUtils.isEmpty(taskTime)) {
            String taskTimeClass = "task-time " + (isDaytimeHour(parseHourFromTaskTime(taskTime)) ? "task-time-day" : "task-time-night");
            cardBuilder.append("<span class=\"")
                .append(taskTimeClass)
                .append("\">")
                    .append(TextUtils.htmlEncode(taskTime))
                    .append("</span>");
        }

        cardBuilder.append("<span class=\"task-category\">")
            .append(TextUtils.htmlEncode(category))
                .append("</span>")
                .append("</div>")
                .append("</div>")
            .append("<p class=\"task-description\">")
                .append(TextUtils.htmlEncode(task.desc))
                .append("</p>")
                .append("</div>");

        if (!showTimeline) {
            return cardBuilder.toString();
        }

        boolean isFirstTaskOfFutureList = globalTaskIndex == 0;
        boolean isLastTaskOfFutureList = globalTaskIndex == (totalFutureTasks - 1);
        StringBuilder rowBuilder = new StringBuilder();
        rowBuilder.append("<div class=\"task-row\">")
            .append("<div class=\"task-timeline\">")
            .append("<span class=\"task-line")
            .append(isFirstTaskOfFutureList ? " task-line-hidden" : "")
            .append("\"></span>")
            .append("<span class=\"task-dot\"></span>")
            .append("<span class=\"task-line")
            .append(isLastTaskOfFutureList ? " task-line-hidden" : "")
            .append("\"></span>")
            .append("</div>")
            .append(cardBuilder)
            .append("</div>");
        return rowBuilder.toString();
    }

    @NonNull
    private String buildFutureInsightHtml(@NonNull FutureInsightModel insightModel) {
        String actionHref = buildFutureInsightActionHref(insightModel);
        return "<div class=\"future-insight-card\">"
            + "<div class=\"future-insight-mark\"></div>"
            + "<div class=\"future-insight-head\">"
            + "<span class=\"future-insight-icon material-symbols-outlined\">insights</span>"
            + "<span class=\"future-insight-label\">Nota inteligente</span>"
            + "</div>"
            + "<h4 class=\"future-insight-title\">"
            + TextUtils.htmlEncode(insightModel.title)
            + "</h4>"
            + "<p class=\"future-insight-text\">"
            + TextUtils.htmlEncode(insightModel.message)
            + "</p>"
            + "<a class=\"future-insight-action\" href=\""
            + TextUtils.htmlEncode(actionHref)
            + "\">"
            + TextUtils.htmlEncode(insightModel.actionLabel)
            + "</a>"
            + "</div>";
    }

    @NonNull
    private String resolveFutureInsightTargetDateKey(
            @NonNull List<FutureDayBlock> dayBlocks,
            @Nullable FutureInsightModel insightModel,
            @Nullable TaskSelection noTimeSelection
    ) {
        if (dayBlocks.isEmpty()) {
            return "";
        }

        if (insightModel != null
                && FUTURE_INSIGHT_MODE_SCHEDULE.equals(insightModel.mode)
                && !TextUtils.isEmpty(insightModel.targetDateKey)
                && containsFutureDayBlock(dayBlocks, insightModel.targetDateKey)) {
            return insightModel.targetDateKey;
        }

        if (noTimeSelection != null && containsFutureDayBlock(dayBlocks, noTimeSelection.dateKey)) {
            return noTimeSelection.dateKey;
        }

        if (insightModel != null
                && !TextUtils.isEmpty(insightModel.targetDateKey)
                && containsFutureDayBlock(dayBlocks, insightModel.targetDateKey)) {
            return insightModel.targetDateKey;
        }

        return dayBlocks.get(0).dateKey;
    }

    private boolean containsFutureDayBlock(@NonNull List<FutureDayBlock> dayBlocks, @NonNull String dateKey) {
        for (FutureDayBlock block : dayBlocks) {
            if (TextUtils.equals(block.dateKey, dateKey)) {
                return true;
            }
        }
        return false;
    }

    @NonNull
    private FutureInsightModel resolveFutureInsightModel(
            @NonNull List<FutureDayBlock> dayBlocks,
            @Nullable TaskSelection noTimeSelection,
            @NonNull String insightSignature
    ) {
        FutureInsightModel lockedModel = restoreLockedFutureInsightModel(noTimeSelection, insightSignature);
        if (lockedModel != null) {
            currentFutureInsightModel = lockedModel;
            return lockedModel;
        }

        if (currentFutureInsightModel != null
                && TextUtils.equals(currentFutureInsightModel.agendaSignature, insightSignature)) {
            return currentFutureInsightModel;
        }

        String loadingMessage;
        if (noTimeSelection != null && noTimeSelection.task != null && !TextUtils.isEmpty(noTimeSelection.task.title)) {
            loadingMessage = "Analizando con IA la mejor hora para \"" + noTimeSelection.task.title + "\"...";
        } else {
            loadingMessage = "Analizando con IA tu agenda para recomendar el mejor bloque de enfoque...";
        }

        currentFutureInsightModel = new FutureInsightModel(
                insightSignature,
                noTimeSelection != null ? FUTURE_INSIGHT_MODE_SCHEDULE : FUTURE_INSIGHT_MODE_FOCUS,
                noTimeSelection != null ? noTimeSelection.dateKey : "",
                noTimeSelection != null && noTimeSelection.task != null ? noTimeSelection.task.title : "",
                noTimeSelection != null ? "Horario inteligente sugerido" : "Optimizacion de carga cognitiva",
                loadingMessage,
                noTimeSelection != null ? "Elegir horario" : "Iniciar enfoque",
                "",
                System.currentTimeMillis() + 8000L
        );
        return currentFutureInsightModel;
    }

    private void requestFutureInsightFromAiIfNeeded(
            @NonNull List<FutureDayBlock> dayBlocks,
            @Nullable TaskSelection noTimeSelection,
            @NonNull String insightSignature
    ) {
        FutureInsightModel lockedModel = restoreLockedFutureInsightModel(noTimeSelection, insightSignature);
        if (lockedModel != null) {
            currentFutureInsightModel = lockedModel;
            futureInsightRequestInFlight = false;
            return;
        }

        if (futureInsightRequestInFlight) {
            return;
        }

        long now = System.currentTimeMillis();
        if (currentFutureInsightModel != null
                && TextUtils.equals(currentFutureInsightModel.agendaSignature, insightSignature)
                && now < currentFutureInsightModel.refreshAfterAtMs) {
            return;
        }

        futureInsightRequestInFlight = true;

        String profileName = resolveProfileNameForAi();
        String agendaSnapshot = buildFutureInsightAgendaSnapshot(dayBlocks, noTimeSelection);
        String behaviorSignals = buildFutureInsightBehaviorSignals(noTimeSelection);

        new GeminiIntelligenceService().generateScheduleSuggestion(
                profileName,
                agendaSnapshot,
                behaviorSignals,
                new GeminiIntelligenceService.ScheduleSuggestionCallback() {
                    @Override
                    public void onSuccess(GeminiIntelligenceService.ScheduleSuggestion suggestion) {
                        futureInsightRequestInFlight = false;
                        currentFutureInsightModel = buildAiFutureInsightModel(
                                insightSignature,
                                suggestion,
                                noTimeSelection
                        );
                        renderFutureTasksLayer();
                    }

                    @Override
                    public void onError(String error) {
                        futureInsightRequestInFlight = false;
                        currentFutureInsightModel = buildFallbackFutureInsightModel(
                                insightSignature,
                                dayBlocks,
                                noTimeSelection
                        );
                        renderFutureTasksLayer();
                    }
                }
        );
    }

    @NonNull
    private FutureInsightModel buildAiFutureInsightModel(
            @NonNull String insightSignature,
            @NonNull GeminiIntelligenceService.ScheduleSuggestion suggestion,
            @Nullable TaskSelection noTimeSelection
    ) {
        String mode = noTimeSelection != null ? FUTURE_INSIGHT_MODE_SCHEDULE : FUTURE_INSIGHT_MODE_FOCUS;
        String title = noTimeSelection != null ? "Horario inteligente sugerido" : "Optimizacion de carga cognitiva";
        String actionLabel = noTimeSelection != null ? "Elegir horario" : "Iniciar enfoque";

        String message = suggestion.getMessage();
        if (noTimeSelection != null && !TextUtils.isEmpty(suggestion.getFocusWindow())) {
            message = message + " Te conviene hacerlo entre " + suggestion.getFocusWindow() + ".";
        }

        String microAction = suggestion.getMicroAction();
        if (TextUtils.isEmpty(microAction)) {
            microAction = noTimeSelection != null
                    ? "Abre Elegir horario para programar la tarea ahora."
                    : "Reserva 25 minutos de enfoque para tu prioridad principal.";
        }

        FutureInsightModel model = new FutureInsightModel(
                insightSignature,
                mode,
                noTimeSelection != null ? noTimeSelection.dateKey : "",
                noTimeSelection != null && noTimeSelection.task != null ? noTimeSelection.task.title : "",
                title,
                message,
                actionLabel,
                microAction,
                System.currentTimeMillis() + FUTURE_INSIGHT_REFRESH_INTERVAL_MS
        );
        persistLockedFutureInsightModel(model);
        return model;
    }

    @NonNull
    private FutureInsightModel buildFallbackFutureInsightModel(
            @NonNull String insightSignature,
            @NonNull List<FutureDayBlock> dayBlocks,
            @Nullable TaskSelection noTimeSelection
    ) {
        String localInsight = buildFutureTasksInsight(dayBlocks);
        String fallbackMessage;
        String title;
        String actionLabel;
        String mode;

        if (noTimeSelection != null && noTimeSelection.task != null && !TextUtils.isEmpty(noTimeSelection.task.title)) {
            title = "Horario inteligente sugerido";
            mode = FUTURE_INSIGHT_MODE_SCHEDULE;
            actionLabel = "Elegir horario";
            fallbackMessage = "No pude consultar IA justo ahora. Te conviene asignar una hora a \""
                    + noTimeSelection.task.title
                    + "\" para asegurar avance hoy.";
        } else {
            title = "Optimizacion de carga cognitiva";
            mode = FUTURE_INSIGHT_MODE_FOCUS;
            actionLabel = "Iniciar enfoque";
            fallbackMessage = localInsight;
        }

        FutureInsightModel model = new FutureInsightModel(
                insightSignature,
                mode,
                noTimeSelection != null ? noTimeSelection.dateKey : "",
                noTimeSelection != null && noTimeSelection.task != null ? noTimeSelection.task.title : "",
                title,
                fallbackMessage,
                actionLabel,
                "",
                System.currentTimeMillis() + FUTURE_INSIGHT_RETRY_INTERVAL_MS
        );
        persistLockedFutureInsightModel(model);
        return model;
    }

    @NonNull
    private String buildFutureInsightAgendaSnapshot(
            @NonNull List<FutureDayBlock> dayBlocks,
            @Nullable TaskSelection noTimeSelection
    ) {
        StringBuilder builder = new StringBuilder();
        builder.append(buildAgendaSnapshotForAi());

        if (noTimeSelection != null && noTimeSelection.task != null) {
            builder.append("; tarea_sin_hora_objetivo=")
                    .append(noTimeSelection.dateKey)
                    .append("|")
                    .append(noTimeSelection.task.title)
                    .append("|categoria=")
                    .append(inferTaskCategory(noTimeSelection.task));
        }

        int daySamples = 0;
        for (FutureDayBlock block : dayBlocks) {
            if (daySamples >= 3) {
                break;
            }
            for (Task task : block.tasks) {
                builder.append("; muestra=")
                        .append(block.dateKey)
                        .append("|")
                        .append(task.title)
                        .append("|")
                        .append(TextUtils.isEmpty(task.time) ? "sin_hora" : task.time);
                daySamples++;
                if (daySamples >= 3) {
                    break;
                }
            }
        }
        return builder.toString();
    }

    @NonNull
    private String buildFutureInsightBehaviorSignals(@Nullable TaskSelection noTimeSelection) {
        String base = buildBehaviorSignalsForAi();
        if (noTimeSelection != null && noTimeSelection.task != null && !TextUtils.isEmpty(noTimeSelection.task.title)) {
            return base
                    + ", modo=fijar_horario"
                    + ", instruccion=La recomendacion debe sugerir asignar hora a la tarea objetivo usando formato AM/PM y texto intuitivo, sin usar la palabra franja"
                    + ", tarea_objetivo='" + noTimeSelection.task.title + "'";
        }
        return base
                + ", modo=enfoque"
                + ", instruccion=Si no hay tareas sin hora, sugiere bloque de foco y microaccion breve";
    }

    @NonNull
    private String buildFutureInsightActionHref(@NonNull FutureInsightModel insightModel) {
        Uri.Builder builder = new Uri.Builder()
                .scheme(FUTURE_INSIGHT_ACTION_SCHEME)
                .authority(FUTURE_INSIGHT_ACTION_HOST)
                .path(FUTURE_INSIGHT_ACTION_PATH)
                .appendQueryParameter("mode", insightModel.mode);

        if (!TextUtils.isEmpty(insightModel.targetDateKey)) {
            builder.appendQueryParameter("date", insightModel.targetDateKey);
        }
        if (!TextUtils.isEmpty(insightModel.targetTaskTitle)) {
            builder.appendQueryParameter("task", insightModel.targetTaskTitle);
        }
        return builder.build().toString();
    }

    @Nullable
    private TaskSelection findFirstTaskWithoutTimeFromToday() {
        if (tasksPerDay.isEmpty()) {
            return null;
        }

        String todayKey = dateKeyFormat.format(Calendar.getInstance().getTime());
        TreeMap<String, List<Task>> ordered = new TreeMap<>(tasksPerDay);
        for (Map.Entry<String, List<Task>> entry : ordered.entrySet()) {
            if (entry.getKey().compareTo(todayKey) < 0) {
                continue;
            }

            List<Task> dayTasks = entry.getValue();
            if (dayTasks == null || dayTasks.isEmpty()) {
                continue;
            }

            for (Task task : dayTasks) {
                if (task == null || TextUtils.isEmpty(task.title) || !TextUtils.isEmpty(task.time)) {
                    continue;
                }
                return new TaskSelection(entry.getKey(), task);
            }
        }
        return null;
    }

    @NonNull
    private String buildFutureInsightSignature(
            @NonNull List<FutureDayBlock> dayBlocks,
            @Nullable TaskSelection noTimeSelection
    ) {
        StringBuilder builder = new StringBuilder();
        for (FutureDayBlock block : dayBlocks) {
            builder.append(block.dateKey).append("|");
            for (Task task : block.tasks) {
                builder.append(task.title).append("#")
                        .append(task.time).append("#")
                        .append(task.category).append(";");
            }
            builder.append("||");
        }

        if (noTimeSelection != null && noTimeSelection.task != null) {
            builder.append("target=")
                    .append(noTimeSelection.dateKey)
                    .append("|")
                    .append(noTimeSelection.task.title);
        } else {
            builder.append("target=none");
        }

        return String.valueOf(builder.toString().hashCode());
    }

    @NonNull
    private String buildEmptyFutureTasksSectionHtml() {
        return "<section class=\"future-empty-section\">"
            + "<header>"
            + "<h2>Tareas proximas</h2>"
            + "<span class=\"cycle-label\">Listo para iniciar</span>"
            + "</header>"
            + "<div class=\"future-empty-shell\">"
            + "<h3 class=\"future-empty-title\">Tu agenda esta lista para construir foco real</h3>"
            + "<p class=\"future-empty-copy\">"
            + "Cuando agregues tu primera tarea, esta capa te mostrara prioridades, horarios sugeridos y contexto de avance sin ruido visual."
            + "</p>"
            + "<a class=\"future-empty-cta\" href=\"sleppify://future-task/add\">"
            + "Crear primera tarea"
            + "</a>"
            + "</div>"
            + "</section>";
    }

    @NonNull
    private List<FutureDayBlock> collectFutureDayBlocks() {
        String todayKey = dateKeyFormat.format(Calendar.getInstance().getTime());
        TreeMap<String, List<Task>> ordered = new TreeMap<>(tasksPerDay);
        List<FutureDayBlock> blocks = new ArrayList<>();

        for (Map.Entry<String, List<Task>> entry : ordered.entrySet()) {
            if (entry.getKey().compareTo(todayKey) < 0) {
                continue;
            }

            List<Task> dayTasks = entry.getValue();
            if (dayTasks == null || dayTasks.isEmpty()) {
                continue;
            }

            List<Task> sortedTasks = new ArrayList<>(dayTasks);
            sortedTasks.sort((left, right) -> Integer.compare(extractTaskSortKey(left.time), extractTaskSortKey(right.time)));
            if (sortedTasks.size() > FUTURE_TASKS_PER_DAY_LIMIT) {
                sortedTasks = new ArrayList<>(sortedTasks.subList(0, FUTURE_TASKS_PER_DAY_LIMIT));
            }

            String formattedDate = formatDateKeyForFutureSection(entry.getKey());
            blocks.add(new FutureDayBlock(entry.getKey(), formattedDate, sortedTasks));
            if (blocks.size() >= FUTURE_TASK_DAYS_LIMIT) {
                break;
            }
        }
        return blocks;
    }

    @NonNull
    private String buildFutureCycleLabel(@NonNull String baseDateKey, @NonNull String targetDateKey, boolean isCurrentCycle) {
        if (isCurrentCycle) {
            return "Semana actual";
        }

        Calendar baseDate = parseDateKeyToCalendar(baseDateKey);
        Calendar targetDate = parseDateKeyToCalendar(targetDateKey);
        if (baseDate == null || targetDate == null) {
            return "Proximo ciclo";
        }

        if (!targetDate.after(baseDate)) {
            return "Proximo ciclo";
        }

        Calendar baseWeekStart = getWeekStart(baseDate);
        Calendar targetWeekStart = getWeekStart(targetDate);
        long weekDiff = (targetWeekStart.getTimeInMillis() - baseWeekStart.getTimeInMillis())
                / (7L * 24L * 60L * 60L * 1000L);
        int monthDiff = calculateMonthDifference(baseDate, targetDate);

        if (weekDiff == 1L) {
            return "Siguiente semana";
        }
        if (weekDiff == 2L || weekDiff == 3L) {
            return "En " + weekDiff + " semanas";
        }
        if (monthDiff == 1) {
            return "Siguiente mes";
        }
        if (monthDiff > 1) {
            return "En " + monthDiff + " meses";
        }
        if (weekDiff > 3L) {
            return "En " + weekDiff + " semanas";
        }
        return "Esta semana";
    }

    @Nullable
    private FutureInsightModel restoreLockedFutureInsightModel(
            @Nullable TaskSelection noTimeSelection,
            @NonNull String insightSignature
    ) {
        if (settingsPrefs == null || noTimeSelection == null || noTimeSelection.task == null) {
            return null;
        }

        String lockedDate = settingsPrefs.getString(KEY_FUTURE_INSIGHT_LOCK_DATE, "");
        if (!TextUtils.equals(lockedDate, noTimeSelection.dateKey)) {
            return null;
        }

        String lockedMessage = settingsPrefs.getString(KEY_FUTURE_INSIGHT_LOCK_MESSAGE, "").trim();
        if (TextUtils.isEmpty(lockedMessage)) {
            return null;
        }

        String lockedTitle = settingsPrefs.getString(KEY_FUTURE_INSIGHT_LOCK_TITLE, "Horario inteligente sugerido");
        String lockedAction = settingsPrefs.getString(KEY_FUTURE_INSIGHT_LOCK_ACTION, "Elegir horario");
        String lockedMicro = settingsPrefs.getString(KEY_FUTURE_INSIGHT_LOCK_MICRO, "");

        return new FutureInsightModel(
                insightSignature,
                FUTURE_INSIGHT_MODE_SCHEDULE,
                noTimeSelection.dateKey,
                noTimeSelection.task.title,
                TextUtils.isEmpty(lockedTitle) ? "Horario inteligente sugerido" : lockedTitle,
                lockedMessage,
                TextUtils.isEmpty(lockedAction) ? "Elegir horario" : lockedAction,
                lockedMicro,
                System.currentTimeMillis() + FUTURE_INSIGHT_REFRESH_INTERVAL_MS
        );
    }

    private void persistLockedFutureInsightModel(@NonNull FutureInsightModel model) {
        if (settingsPrefs == null || !FUTURE_INSIGHT_MODE_SCHEDULE.equals(model.mode)) {
            return;
        }
        if (TextUtils.isEmpty(model.targetDateKey) || TextUtils.isEmpty(model.message)) {
            return;
        }

        settingsPrefs.edit()
                .putString(KEY_FUTURE_INSIGHT_LOCK_DATE, model.targetDateKey)
                .putString(KEY_FUTURE_INSIGHT_LOCK_TASK, model.targetTaskTitle)
                .putString(KEY_FUTURE_INSIGHT_LOCK_TITLE, model.title)
                .putString(KEY_FUTURE_INSIGHT_LOCK_MESSAGE, model.message)
                .putString(KEY_FUTURE_INSIGHT_LOCK_ACTION, model.actionLabel)
                .putString(KEY_FUTURE_INSIGHT_LOCK_MICRO, model.microAction)
                .apply();
    }

    private void clearLockedFutureInsightForDate(@Nullable String dateKey) {
        if (settingsPrefs == null || TextUtils.isEmpty(dateKey)) {
            return;
        }

        String lockedDate = settingsPrefs.getString(KEY_FUTURE_INSIGHT_LOCK_DATE, "");
        if (!TextUtils.equals(lockedDate, dateKey)) {
            return;
        }

        settingsPrefs.edit()
                .remove(KEY_FUTURE_INSIGHT_LOCK_DATE)
                .remove(KEY_FUTURE_INSIGHT_LOCK_TASK)
                .remove(KEY_FUTURE_INSIGHT_LOCK_TITLE)
                .remove(KEY_FUTURE_INSIGHT_LOCK_MESSAGE)
                .remove(KEY_FUTURE_INSIGHT_LOCK_ACTION)
                .remove(KEY_FUTURE_INSIGHT_LOCK_MICRO)
                .apply();
    }

    private void clearLockedFutureInsightIfResolved() {
        if (settingsPrefs == null) {
            return;
        }

        String lockedDate = settingsPrefs.getString(KEY_FUTURE_INSIGHT_LOCK_DATE, "");
        if (TextUtils.isEmpty(lockedDate)) {
            return;
        }

        List<Task> dayTasks = tasksPerDay.get(lockedDate);
        if (dayTasks == null || dayTasks.isEmpty()) {
            clearLockedFutureInsightForDate(lockedDate);
            return;
        }

        for (Task task : dayTasks) {
            if (task != null && !TextUtils.isEmpty(task.title) && TextUtils.isEmpty(task.time)) {
                return;
            }
        }

        clearLockedFutureInsightForDate(lockedDate);
    }

    @Nullable
    private Calendar parseDateKeyToCalendar(@NonNull String dateKey) {
        try {
            String[] parts = dateKey.split("-");
            if (parts.length != 3) {
                return null;
            }

            int year = Integer.parseInt(parts[0]);
            int month = Integer.parseInt(parts[1]);
            int day = Integer.parseInt(parts[2]);

            Calendar calendar = Calendar.getInstance();
            calendar.set(Calendar.YEAR, year);
            calendar.set(Calendar.MONTH, month - 1);
            calendar.set(Calendar.DAY_OF_MONTH, day);
            setCalendarToStartOfDay(calendar);
            return calendar;
        } catch (Exception ignored) {
            return null;
        }
    }

    @NonNull
    private Calendar getWeekStart(@NonNull Calendar source) {
        Calendar weekStart = (Calendar) source.clone();
        weekStart.setFirstDayOfWeek(Calendar.MONDAY);

        int dayOfWeek = weekStart.get(Calendar.DAY_OF_WEEK);
        int offset = (7 + (dayOfWeek - Calendar.MONDAY)) % 7;
        weekStart.add(Calendar.DAY_OF_MONTH, -offset);
        setCalendarToStartOfDay(weekStart);
        return weekStart;
    }

    private void setCalendarToStartOfDay(@NonNull Calendar calendar) {
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
    }

    private int calculateMonthDifference(@NonNull Calendar start, @NonNull Calendar end) {
        int yearDiff = end.get(Calendar.YEAR) - start.get(Calendar.YEAR);
        int monthDiff = end.get(Calendar.MONTH) - start.get(Calendar.MONTH);
        return yearDiff * 12 + monthDiff;
    }

    private int extractTaskSortKey(@Nullable String rawTime) {
        int hour = parseHourFromTaskTime(rawTime);
        if (hour < 0) {
            return Integer.MAX_VALUE;
        }

        int minute = 0;
        if (!TextUtils.isEmpty(rawTime)) {
            String value = rawTime.trim().toUpperCase(Locale.US);
            String numeric = value.replace("AM", "").replace("PM", "").trim();
            String[] split = numeric.split(":");
            if (split.length > 1) {
                try {
                    minute = Integer.parseInt(split[1].replaceAll("[^0-9]", "").trim());
                } catch (Exception ignored) {
                    minute = 0;
                }
            }
        }
        return hour * 60 + minute;
    }

    @NonNull
    private String formatDateKeyForFutureSection(@NonNull String dateKey) {
        try {
            String[] parts = dateKey.split("-");
            if (parts.length != 3) {
                return dateKey;
            }

            int year = Integer.parseInt(parts[0]);
            int month = Integer.parseInt(parts[1]);
            int day = Integer.parseInt(parts[2]);

            Calendar calendar = Calendar.getInstance();
            calendar.set(Calendar.YEAR, year);
            calendar.set(Calendar.MONTH, month - 1);
            calendar.set(Calendar.DAY_OF_MONTH, day);
            String raw = new SimpleDateFormat("EEEE, dd 'de' MMM", SPANISH_LOCALE).format(calendar.getTime());
            if (raw.isEmpty()) {
                return dateKey;
            }
            return Character.toUpperCase(raw.charAt(0)) + raw.substring(1);
        } catch (Exception ignored) {
            return dateKey;
        }
    }

    @NonNull
    private String inferTaskCategory(@NonNull Task task) {
        String stored = normalizeTaskCategory(task.category);
        if (!TextUtils.isEmpty(stored)) {
            return stored;
        }
        return FALLBACK_TASK_CATEGORY;
    }

    @NonNull
    private String normalizeTaskCategory(@Nullable String rawCategory) {
        if (rawCategory == null) {
            return DEFAULT_TASK_CATEGORY;
        }

        String value = rawCategory
                .replace('\n', ' ')
                .replace('\r', ' ')
                .trim();
        if (value.isEmpty()) {
            return DEFAULT_TASK_CATEGORY;
        }

        value = value.replaceAll("\\s+", " ");
        value = value.replaceAll("^[\\\"'`]+|[\\\"'`]+$", "").trim();
        value = value.replaceAll("[\\.;:,]+$", "").trim();

        String[] tokens = value.split("\\s+");
        value = tokens.length > 0 ? tokens[0] : value;
        value = value.replaceAll("[^\\p{L}\\p{N}_-]", "").trim();
        if (value.isEmpty()) {
            return DEFAULT_TASK_CATEGORY;
        }

        String lower = value.toLowerCase(SPANISH_LOCALE);
        if ("none".equals(lower)) {
            return TASK_METADATA_ERROR_CATEGORY;
        }
        value = Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
        if (value.length() > 24) {
            value = value.substring(0, 24).trim();
        }
        return value;
    }

    @NonNull
    private String buildFutureTasksInsight(@NonNull List<FutureDayBlock> blocks) {
        int total = 0;
        int timedTasks = 0;
        int morning = 0;
        int afternoon = 0;
        int night = 0;

        for (FutureDayBlock block : blocks) {
            for (Task task : block.tasks) {
                total++;
                int hour = parseHourFromTaskTime(task.time);
                if (hour >= 0) {
                    timedTasks++;
                    if (hour >= 5 && hour < 12) {
                        morning++;
                    } else if (hour >= 12 && hour < 19) {
                        afternoon++;
                    } else {
                        night++;
                    }
                }
            }
        }

        if (total == 0) {
            return "No se detectaron tareas proximas. Agrega nuevas tareas para activar tu capa inteligente.";
        }
        if (timedTasks == 0) {
            return "Tus tareas proximas no usan horarios fijos. Prioriza una tarea clave al inicio de cada dia para mantener continuidad.";
        }
        if (morning >= afternoon && morning >= night) {
            return "Tu agenda proxima esta cargada en la manana. Mantener tu tarea mas exigente en el primer bloque mejora el enfoque.";
        }
        if (afternoon >= night) {
            return "Tus tareas proximas se concentran en la tarde. Protege un bloque sin interrupciones para reducir cambios de contexto.";
        }
        return "Tu carga se inclina hacia la noche. Mover una tarea demandante a una hora mas temprana puede mejorar energia y calidad de decisiones.";
    }

    private static final class FutureDayBlock {
        final String dateKey;
        final String displayDate;
        final List<Task> tasks;

        FutureDayBlock(@NonNull String dateKey, @NonNull String displayDate, @NonNull List<Task> tasks) {
            this.dateKey = dateKey;
            this.displayDate = displayDate;
            this.tasks = tasks;
        }
    }

    private static final class TaskSelection {
        final String dateKey;
        final Task task;

        TaskSelection(@NonNull String dateKey, @NonNull Task task) {
            this.dateKey = dateKey;
            this.task = task;
        }
    }

    private static final class FutureInsightModel {
        final String agendaSignature;
        final String mode;
        final String targetDateKey;
        final String targetTaskTitle;
        final String title;
        final String message;
        final String actionLabel;
        final String microAction;
        final long refreshAfterAtMs;

        FutureInsightModel(
                @NonNull String agendaSignature,
                @NonNull String mode,
                @Nullable String targetDateKey,
                @Nullable String targetTaskTitle,
                @NonNull String title,
                @NonNull String message,
                @NonNull String actionLabel,
                @NonNull String microAction,
                long refreshAfterAtMs
        ) {
            this.agendaSignature = agendaSignature;
            this.mode = mode;
            this.targetDateKey = targetDateKey == null ? "" : targetDateKey;
            this.targetTaskTitle = targetTaskTitle == null ? "" : targetTaskTitle;
            this.title = title;
            this.message = message;
            this.actionLabel = actionLabel;
            this.microAction = microAction;
            this.refreshAfterAtMs = refreshAfterAtMs;
        }
    }

    private static final class InsightTimeSelection {
        final int hourOfDay24;
        final int minute;

        InsightTimeSelection(int hourOfDay24, int minute) {
            this.hourOfDay24 = hourOfDay24;
            this.minute = minute;
        }
    }

    // RecyclerView Adapter
    private class TaskAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private static final int VIEW_TYPE_TASK = 1;
        private static final int VIEW_TYPE_EMPTY = 2;
        private List<Task> taskList;

        public TaskAdapter(List<Task> taskList) {
            this.taskList = copyTaskList(taskList);
        }

        public void setTasks(List<Task> newTasks) {
            List<Task> previousTasks = copyTaskList(this.taskList);
            List<Task> updatedTasks = copyTaskList(newTasks);
            final boolean timelineStructureChanged = previousTasks.size() != updatedTasks.size();
            final boolean emptyStateChanged = previousTasks.isEmpty() != updatedTasks.isEmpty();

            if (emptyStateChanged) {
                this.taskList = updatedTasks;
                notifyDataSetChanged();
                return;
            }

            DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(new DiffUtil.Callback() {
                @Override
                public int getOldListSize() {
                    return previousTasks.isEmpty() ? 1 : previousTasks.size();
                }

                @Override
                public int getNewListSize() {
                    return updatedTasks.isEmpty() ? 1 : updatedTasks.size();
                }

                @Override
                public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
                    boolean oldIsPlaceholder = previousTasks.isEmpty();
                    boolean newIsPlaceholder = updatedTasks.isEmpty();
                    if (oldIsPlaceholder || newIsPlaceholder) {
                        return oldIsPlaceholder && newIsPlaceholder;
                    }

                    Task oldTask = previousTasks.get(oldItemPosition);
                    Task newTask = updatedTasks.get(newItemPosition);
                    return TextUtils.equals(oldTask.title, newTask.title)
                            && TextUtils.equals(oldTask.time, newTask.time);
                }

                @Override
                public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                    boolean oldIsPlaceholder = previousTasks.isEmpty();
                    boolean newIsPlaceholder = updatedTasks.isEmpty();
                    if (oldIsPlaceholder || newIsPlaceholder) {
                        return oldIsPlaceholder && newIsPlaceholder;
                    }

                    Task oldTask = previousTasks.get(oldItemPosition);
                    Task newTask = updatedTasks.get(newItemPosition);

                    if (timelineStructureChanged) {
                        return false;
                    }

                    return TextUtils.equals(oldTask.title, newTask.title)
                            && TextUtils.equals(oldTask.desc, newTask.desc)
                            && TextUtils.equals(oldTask.time, newTask.time)
                            && TextUtils.equals(oldTask.category, newTask.category);
                }
            });

            this.taskList = updatedTasks;
            diffResult.dispatchUpdatesTo(this);
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            if (viewType == VIEW_TYPE_EMPTY) {
                View emptyView = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_task_empty, parent, false);
                return new EmptyViewHolder(emptyView);
            }
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_task, parent, false);
            return new TaskViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            if (holder instanceof EmptyViewHolder) {
                EmptyViewHolder emptyHolder = (EmptyViewHolder) holder;
                View container = emptyHolder.container;
                if (container != null) {
                    container.setOnClickListener(v -> triggerAddTaskFlow());
                } else {
                    holder.itemView.setOnClickListener(v -> triggerAddTaskFlow());
                }
                return;
            }

            Task task = taskList.get(position);
            TaskViewHolder taskHolder = (TaskViewHolder) holder;
            taskHolder.tvTitle.setText(task.title);
            taskHolder.tvDesc.setText(task.desc);
            clearLongPressFeedback(taskHolder);

            if (taskHolder.tvCategory != null) {
                String categoryLabel = resolveTaskCategoryForList(task);
                if (TextUtils.isEmpty(categoryLabel)) {
                    taskHolder.tvCategory.setVisibility(View.GONE);
                } else {
                    taskHolder.tvCategory.setVisibility(View.VISIBLE);
                    taskHolder.tvCategory.setText(categoryLabel);
                }
            }

            String taskTime = task.time == null ? "" : task.time.trim();
            if (taskHolder.tvTime != null) {
                if (TextUtils.isEmpty(taskTime)) {
                    taskHolder.tvTime.setVisibility(View.GONE);
                } else {
                    taskHolder.tvTime.setVisibility(View.VISIBLE);
                    taskHolder.tvTime.setText(taskTime);
                    taskHolder.tvTime.setTextColor(isDaytimeHour(parseHourFromTaskTime(taskTime))
                            ? TASK_TIME_COLOR_DAY
                            : TASK_TIME_COLOR_NIGHT);
                }
            }

            boolean showTimeline = taskList.size() > 1;
            taskHolder.timelineContainer.setVisibility(showTimeline ? View.VISIBLE : View.GONE);
            taskHolder.timelineDot.setVisibility(showTimeline ? View.VISIBLE : View.GONE);

            if (!showTimeline) {
                taskHolder.timelineLineTop.setVisibility(View.GONE);
                taskHolder.timelineLineBottom.setVisibility(View.GONE);
            } else {
                int lastPosition = taskList.size() - 1;
                taskHolder.timelineLineTop.setVisibility(position == 0 ? View.INVISIBLE : View.VISIBLE);
                taskHolder.timelineLineBottom.setVisibility(position == lastPosition ? View.INVISIBLE : View.VISIBLE);
            }

            final float[] lastTouchRaw = new float[]{Float.NaN, Float.NaN};
            final Handler longPressHandler = new Handler(Looper.getMainLooper());
            final Runnable longPressRunnable = () -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    taskHolder.itemView.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
                }
                clearLongPressFeedback(taskHolder);
                Float touchX = Float.isNaN(lastTouchRaw[0]) ? null : lastTouchRaw[0];
                Float touchY = Float.isNaN(lastTouchRaw[1]) ? null : lastTouchRaw[1];
                showTaskActionTooltip(taskHolder.itemView, new TaskSelection(selectedDateKey, task), touchX, touchY);
            };

            taskHolder.itemView.setOnTouchListener((v, event) -> {
                if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                    lastTouchRaw[0] = event.getRawX();
                    lastTouchRaw[1] = event.getRawY();
                    applyLongPressFeedback(taskHolder);
                    longPressHandler.postDelayed(longPressRunnable, 400); // 400ms for faster trigger
                } else if (event.getActionMasked() == MotionEvent.ACTION_UP
                        || event.getActionMasked() == MotionEvent.ACTION_CANCEL
                        || event.getActionMasked() == MotionEvent.ACTION_MOVE) {
                    if (event.getActionMasked() == MotionEvent.ACTION_MOVE) {
                        float dx = Math.abs(event.getRawX() - lastTouchRaw[0]);
                        float dy = Math.abs(event.getRawY() - lastTouchRaw[1]);
                        if (dx > 10 || dy > 10) {
                            longPressHandler.removeCallbacks(longPressRunnable);
                            clearLongPressFeedback(taskHolder);
                        }
                    } else {
                        longPressHandler.removeCallbacks(longPressRunnable);
                        clearLongPressFeedback(taskHolder);
                    }
                }
                return false;
            });

            taskHolder.itemView.setOnClickListener(v -> {
                // Do nothing on simple click for now, as requested.
            });
        }

        private void applyLongPressFeedback(@NonNull TaskViewHolder holder) {
            View target = holder.cardTask != null ? holder.cardTask : holder.itemView;
            target.animate().cancel();
            target.animate()
                    .scaleX(0.92f)
                    .scaleY(0.92f)
                    .setDuration(120)
                    .start();
            if (holder.cardTask != null) {
                holder.cardTask.setCardBackgroundColor(TASK_CARD_COLOR_LONG_PRESS);
            }
        }

        private void clearLongPressFeedback(@NonNull TaskViewHolder holder) {
            View target = holder.cardTask != null ? holder.cardTask : holder.itemView;
            target.animate().cancel();
            target.animate()
                    .scaleX(1.0f)
                    .scaleY(1.0f)
                    .setDuration(120)
                    .start();
            if (holder.cardTask != null) {
                holder.cardTask.setCardBackgroundColor(TASK_CARD_COLOR_DEFAULT);
            }
        }

        @Override
        public int getItemViewType(int position) {
            return taskList.isEmpty() ? VIEW_TYPE_EMPTY : VIEW_TYPE_TASK;
        }

        @Override
        public int getItemCount() {
            return taskList.isEmpty() ? 1 : taskList.size();
        }

        class TaskViewHolder extends RecyclerView.ViewHolder {
            TextView tvTitle, tvDesc, tvTime, tvCategory;
            MaterialCardView cardTask;
            View timelineContainer, timelineDot, timelineLineTop, timelineLineBottom, metaRow;

            public TaskViewHolder(@NonNull View itemView) {
                super(itemView);
                tvTitle = itemView.findViewById(R.id.tvTaskTitle);
                tvDesc = itemView.findViewById(R.id.tvTaskDesc);
                tvTime = itemView.findViewById(R.id.tvTaskTime);
                tvCategory = itemView.findViewById(R.id.tvTaskCategory);
                cardTask = itemView.findViewById(R.id.cardTask);
                timelineContainer = itemView.findViewById(R.id.timelineContainer);
                timelineDot = itemView.findViewById(R.id.timelineDot);
                timelineLineTop = itemView.findViewById(R.id.timelineLineTop);
                timelineLineBottom = itemView.findViewById(R.id.timelineLineBottom);
            }
        }

        class EmptyViewHolder extends RecyclerView.ViewHolder {
            final View container;

            EmptyViewHolder(@NonNull View itemView) {
                super(itemView);
                container = itemView.findViewById(R.id.containerEmptyTaskAction);
            }
        }
    }

    @NonNull
    private String resolveTaskCategoryForList(@NonNull Task task) {
        String raw = task.category == null ? "" : task.category.trim();
        if (TextUtils.isEmpty(raw)) {
            return inferTaskCategory(task);
        }
        if (PENDING_TASK_CATEGORY.equalsIgnoreCase(raw)) {
            return PENDING_TASK_CATEGORY;
        }
        return inferTaskCategory(task);
    }
}

