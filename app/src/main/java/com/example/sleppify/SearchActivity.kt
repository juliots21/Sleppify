package com.example.sleppify

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Typeface
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.FragmentContainerView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.example.sleppify.utils.YouTubeCropTransformation
import com.example.sleppify.utils.YouTubeImageProcessor
import com.google.android.material.imageview.ShapeableImageView
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.text.Normalizer
import java.util.*

/**
 * Optimized Kotlin version of SearchActivity.
 * Managed by Coroutines for high-performance search and predictive pre-fetching.
 */
class SearchActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_RESULT_TYPE = "search_result_type"
        const val EXTRA_RESULT_VIDEO_ID = "search_result_video_id"
        const val EXTRA_RESULT_CONTENT_ID = "search_result_content_id"
        const val EXTRA_RESULT_TITLE = "search_result_title"
        const val EXTRA_RESULT_SUBTITLE = "search_result_subtitle"
        const val EXTRA_RESULT_THUMBNAIL = "search_result_thumbnail"
        const val EXTRA_RESULT_TRACKS_JSON = "search_result_tracks_json"
        
        const val RESULT_TRACK_SELECTED = 100
        const val RESULT_PLAYLIST_SELECTED = 101

        private const val PREFS_STREAMING_CACHE = "streaming_cache"
        private const val PREF_RECENT_SEARCH_QUERIES = "stream_recent_search_queries"
        private const val SEARCH_PAGE_SIZE = 20
        private const val SEARCH_SUGGESTION_RECENT_LIMIT = 6
        private const val SEARCH_SCROLL_LOAD_MORE_THRESHOLD = 4
        private const val TAG_SONG_PLAYER = "search_song_player"
        
        private val DEFAULT_SEARCH_SUGGESTIONS = arrayOf(
            "Lofi Chill",
            "EDM House",
            "Trap Latino",
            "Pop Latino"
        )
    }

    private val youTubeMusicService = YouTubeMusicService()
    private val normalizedFilterCache = mutableMapOf<String, String>()
    private val allTracks = mutableListOf<YouTubeMusicService.TrackResult>()
    private val tracks = mutableListOf<YouTubeMusicService.TrackResult>()
    private val recentSearchQueries = mutableListOf<String>()

    private lateinit var etSearchQuery: TextInputEditText
    private lateinit var ivSearchClear: ImageView
    private lateinit var rvSearchResults: RecyclerView
    private lateinit var tvSearchState: TextView
    private lateinit var llSearchBar: LinearLayout
    private lateinit var nsvSearchContent: androidx.core.widget.NestedScrollView
    private lateinit var rvSearchSuggestions: RecyclerView
    private lateinit var llFeaturedResult: LinearLayout
    private lateinit var ivFeaturedThumb: ShapeableImageView
    private lateinit var tvFeaturedTitle: TextView
    private lateinit var tvFeaturedSubtitle: TextView
    private lateinit var ivFeaturedOfflineIndicator: ImageView
    
    // Mini Player components
    private lateinit var llMiniPlayer: LinearLayout
    private lateinit var ivMiniPlayerArt: ImageView
    private lateinit var tvMiniPlayerTitle: TextView
    private lateinit var tvMiniPlayerSubtitle: TextView
    private lateinit var btnMiniPlayPause: ImageButton
    private lateinit var sbMiniPlayerProgress: android.widget.SeekBar
    private lateinit var tvModuleTitle: TextView
    private lateinit var btnSettings: View
    private lateinit var moduleLoadingOverlay: View
    private lateinit var searchPlayerContainer: FragmentContainerView

    private lateinit var llSearchRoot: View
    private lateinit var bottomNavigation: com.google.android.material.bottomnavigation.BottomNavigationView
    private var isPlayerVisible = false
    private var pendingContainerHide: Runnable? = null
    // private lateinit var searchBottomSheetAdapter: SearchBottomSheetAdapter
    // private var sheetBottomSheetBehavior: BottomSheetBehavior<View>? = null

    private var adapter: SearchResultsAdapter? = null
    private var featuredTrack: YouTubeMusicService.TrackResult? = null
    
    private var searching = false
    private var searchPaginationInFlight = false
    private var hasMoreSearchPages = false
    private var nextSearchPageToken = ""
    private var activeSearchQuery = ""
    private var latestSearchRequestId = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_search)

        initViews()
        setupRecyclerView()
        setupSuggestionsRecyclerView()
        setupSearchInput()
        configureStandardHeader()
        
        restoreRecentSearchQueries()
        refreshSearchSuggestions("")

        etSearchQuery.requestFocus()
        lifecycleScope.launch {
            delay(350)
            if (!isFinishing && !isDestroyed) {
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                imm?.showSoftInput(etSearchQuery, InputMethodManager.SHOW_IMPLICIT)
            }
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // If full player is visible, collapse it back to mini
                if (isPlayerVisible) {
                    collapsePlayerToMini()
                    return
                }
                
                val hasResults = nsvSearchContent.visibility == View.VISIBLE
                
                if (hasResults) {
                    // Back from results → show suggestions
                    showSuggestionsMode()
                    etSearchQuery.requestFocus()
                    val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                    imm?.showSoftInput(etSearchQuery, InputMethodManager.SHOW_IMPLICIT)
                } else {
                    // Back from suggestions → exit search
                    isEnabled = false
                    finish()
                }
            }
        })
    }

    private fun initViews() {
        val root = findViewById<View>(R.id.llSearchRoot)
        val topAppBar = findViewById<View>(R.id.topAppBar)
        tvModuleTitle = findViewById(R.id.tvModuleTitle)
        btnSettings = findViewById(R.id.btnSettings)
        llSearchBar = findViewById(R.id.llSearchBar)
        etSearchQuery = findViewById(R.id.etSearchQuery)
        ivSearchClear = findViewById(R.id.ivSearchClear)
        rvSearchResults = findViewById(R.id.rvSearchResults)
        nsvSearchContent = findViewById(R.id.nsvSearchContent)
        tvSearchState = findViewById(R.id.tvSearchState)
        rvSearchSuggestions = findViewById(R.id.rvSearchSuggestions)
        llFeaturedResult = findViewById(R.id.llFeaturedResult)
        ivFeaturedThumb = findViewById(R.id.ivFeaturedThumb)
        tvFeaturedTitle = findViewById(R.id.tvFeaturedTitle)
        tvFeaturedSubtitle = findViewById(R.id.tvFeaturedSubtitle)
        ivFeaturedOfflineIndicator = findViewById(R.id.ivFeaturedOfflineIndicator)
        bottomNavigation = findViewById(R.id.bottomNavigation)
        bottomNavigation.setOnItemSelectedListener {
            finish()
            true
        }
        
        llMiniPlayer = findViewById(R.id.llMiniPlayer)
        ivMiniPlayerArt = findViewById(R.id.ivMiniPlayerArt)
        tvMiniPlayerTitle = findViewById(R.id.tvMiniPlayerTitle)
        tvMiniPlayerSubtitle = findViewById(R.id.tvMiniPlayerSubtitle)
        btnMiniPlayPause = findViewById(R.id.btnMiniPlayPause)
        sbMiniPlayerProgress = findViewById(R.id.sbMiniPlayerProgress)
        
        llMiniPlayer.setOnClickListener {
            openCurrentPlayerFromMiniBar()
        }
        btnMiniPlayPause.setOnClickListener {
            toggleCurrentMiniPlayback()
        }
        moduleLoadingOverlay = findViewById(R.id.moduleLoadingOverlay)
        searchPlayerContainer = findViewById(R.id.searchPlayerContainer)

        if (SystemType.isTv(this)) {
            moduleLoadingOverlay.setBackgroundColor(Color.TRANSPARENT)
            moduleLoadingOverlay.isClickable = false
            moduleLoadingOverlay.isFocusable = false
        }

        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val extraPadding = (8 * resources.displayMetrics.density).toInt()
            topAppBar.setPadding(topAppBar.paddingLeft, systemBars.top + extraPadding, topAppBar.paddingRight, topAppBar.paddingBottom)
            // NO aplicar padding bottom al mini player - el footer ya tiene su propio padding
            bottomNavigation.setPadding(systemBars.left, 0, systemBars.right, systemBars.bottom)
            insets
        }

        llSearchRoot = findViewById(R.id.llSearchRoot) // or whatever the id is, wait let me look at the file
        val rootTouchListener = View.OnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                // Hide keyboard if touch is outside of EditText
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                imm?.hideSoftInputFromWindow(etSearchQuery.windowToken, 0)
            }
            false
        }
        root.setOnTouchListener(rootTouchListener)
    }

    private fun setupRecyclerView() {
        val layoutManager = LinearLayoutManager(this)
        adapter = SearchResultsAdapter(
            onClick = { onTrackClicked(it) },
            onMoreClick = { track, anchor -> showTrackOptionsBottomSheet(track, anchor) }
        )
        rvSearchResults.apply {
            this.layoutManager = layoutManager
            setHasFixedSize(true)
            itemAnimator = null
            setItemViewCacheSize(15)
            adapter = this@SearchActivity.adapter
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                    if (dy <= 0 || searching || searchPaginationInFlight || !hasMoreSearchPages) return
                    val lastVisible = layoutManager.findLastVisibleItemPosition()
                    if (lastVisible >= (adapter?.itemCount ?: 0) - SEARCH_SCROLL_LOAD_MORE_THRESHOLD) {
                        loadMoreSearchResults()
                    }
                }
            })
        }
    }

    private fun setupSuggestionsRecyclerView() {
        val suggestionsAdapter = SuggestionsAdapter {
            etSearchQuery.setText(it)
            etSearchQuery.setSelection(it.length)
            performSearch()
            hideKeyboard()
        }
        rvSearchSuggestions.apply {
            layoutManager = LinearLayoutManager(this@SearchActivity)
            adapter = suggestionsAdapter
        }
    }

    private fun setupSearchInput() {
        etSearchQuery.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                showSuggestionsMode()
            }
        }
        
        etSearchQuery.setOnClickListener {
            showSuggestionsMode()
        }

        etSearchQuery.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                performSearch()
                hideKeyboard()
                true
            } else false
        }

        etSearchQuery.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val query = s?.toString()?.trim() ?: ""
                ivSearchClear.visibility = if (query.isEmpty()) View.GONE else View.VISIBLE
                refreshSearchSuggestions(query)
            }
        })

        ivSearchClear.setOnClickListener {
            etSearchQuery.setText("")
            etSearchQuery.requestFocus()
            clearResults()
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.showSoftInput(etSearchQuery, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun showSuggestionsMode() {
        val query = etSearchQuery.text?.toString()?.trim() ?: ""
        refreshSearchSuggestions(query)
        rvSearchSuggestions.visibility = View.VISIBLE

        nsvSearchContent.visibility = View.GONE
        findViewById<View>(R.id.llSearchState).visibility = View.GONE
        
        // Ocultar minireproductor y footer en sugerencias
        llMiniPlayer.visibility = View.GONE
        bottomNavigation.visibility = View.GONE

        // Forzar recálculo del layout
        llSearchRoot.requestLayout()
    }

    private fun clearResults() {
        allTracks.clear()
        tracks.clear()
        featuredTrack = null
        adapter?.submitResults(emptyList())
        tvSearchState.text = ""
        activeSearchQuery = ""
        nsvSearchContent.visibility = View.GONE
        rvSearchSuggestions.visibility = View.VISIBLE
        refreshSearchSuggestions("")
    }

    private fun configureStandardHeader() {
        btnSettings.visibility = View.VISIBLE
        (btnSettings as? ImageView)?.setImageResource(R.drawable.ic_settings)
        btnSettings.contentDescription = "Ajustes"
        btnSettings.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("SHOW_SETTINGS", true)
            }
            startActivity(intent)
            finish()
        }

        tvModuleTitle.apply {
            text = getString(R.string.header_brand_title)
            isAllCaps = true
            letterSpacing = 0.08f
            typeface = ResourcesCompat.getFont(this@SearchActivity, R.font.manrope_variable) ?: Typeface.DEFAULT_BOLD
            
            val density = resources.displayMetrics.density
            val iconSize = (26 * density).toInt()
            ContextCompat.getDrawable(this@SearchActivity, R.mipmap.ic_launcher)?.apply {
                setBounds(0, 0, iconSize, iconSize)
                setCompoundDrawablesRelative(this, null, null, null)
            }
            compoundDrawablePadding = (8 * density).toInt()
            setOnClickListener { onBackPressedDispatcher.onBackPressed() }
        }
    }

    private fun performSearch() {
        if (searching) return
        val query = etSearchQuery.text?.toString()?.trim() ?: ""
        if (query.isNotEmpty()) startPagedSearch(query
        )
    }

    private fun startPagedSearch(query: String) {
        latestSearchRequestId++
        activeSearchQuery = query
        allTracks.clear()
        tracks.clear()
        featuredTrack = null
        adapter?.submitResults(emptyList())
        hasMoreSearchPages = false
        nextSearchPageToken = ""

        rememberRecentSearchQuery(query)
        refreshSearchSuggestions(query)
        rvSearchSuggestions.visibility = View.GONE
        nsvSearchContent.visibility = View.VISIBLE
        bottomNavigation.visibility = View.VISIBLE
        updateMiniPlayerUi(forceVisibleInResults = true)

        // Forzar recálculo del layout del ConstraintLayout raíz
        llSearchRoot.post {
            llSearchRoot.invalidate()
            llSearchRoot.requestLayout()
        }

        requestPagedSearchResults(query, "", false)
    }

    private fun requestPagedSearchResults(query: String, pageToken: String, append: Boolean) {
        val requestId = ++latestSearchRequestId

        if (!isNetworkAvailable()) {
            // Offline mode: search in local stores
            if (!append) {
                setSearchLoadingState(true, "Buscando música...")
            }

            lifecycleScope.launch(Dispatchers.IO) {
                val offlineResults = performOfflineSearch(query)

                launch(Dispatchers.Main) {
                    if (isFinishing || isDestroyed || requestId != latestSearchRequestId) return@launch

                    if (!append) allTracks.clear()
                    appendUniqueTracks(offlineResults)

                    // No pagination for offline search
                    nextSearchPageToken = ""
                    hasMoreSearchPages = false
                    applyActiveFilter(query, forceSort = true)

                    if (allTracks.isEmpty()) {
                        setSearchLoadingState(false, "No encontré resultados para: $query")
                    } else {
                        setSearchLoadingState(false, "")
                    }
                    
                    revealModuleContent()
                    rvSearchResults.alpha = 0f
                    rvSearchResults.animate().alpha(1f).setDuration(250).start()
                    hideKeyboard()
                }
            }
            return
        }

        // Online mode: use YouTube API
        if (append) {
            searchPaginationInFlight = true
            tvSearchState.text = "Cargando mas resultados..."
        } else {
            setSearchLoadingState(true, "Buscando música...")
        }

        youTubeMusicService.searchTracksPaged(query, SEARCH_PAGE_SIZE, pageToken.takeIf { it.isNotEmpty() }, object : YouTubeMusicService.SearchPageCallback {
            override fun onSuccess(pageResult: YouTubeMusicService.SearchPageResult) {
                if (isFinishing || isDestroyed || requestId != latestSearchRequestId) return
                
                if (append) {
                    searchPaginationInFlight = false
                }
                if (!append) {
                    allTracks.clear()
                    // Also merge local playlist results into online search
                    lifecycleScope.launch(Dispatchers.IO) {
                        val localResults = performOfflineSearch(query)
                        launch(Dispatchers.Main) {
                            if (isFinishing || isDestroyed) return@launch
                            appendUniqueTracks(localResults)
                            applyActiveFilter(query, forceSort = true)
                        }
                    }
                }
                appendUniqueTracks(pageResult.tracks)
                
                nextSearchPageToken = pageResult.nextPageToken
                hasMoreSearchPages = nextSearchPageToken.isNotEmpty()
                applyActiveFilter(query, forceSort = true)

                if (allTracks.isEmpty()) {
                    if (!append) setSearchLoadingState(false, "No encontré resultados para: $query")
                } else if (!append) {
                    setSearchLoadingState(false, "")
                    // Predictive pre-fetching in IO dispatcher
                    allTracks.firstOrNull()?.videoId?.let { id ->
                        lifecycleScope.launch(Dispatchers.IO) {
                            Log.d("SearchActivity", "Predictive pre-fetch: Resolviendo stream...")
                            InnertubeResolver.resolveStreamUrl(id)
                        }
                    }
                }
                
                if (!append && !allTracks.isEmpty()) {
                    revealModuleContent()
                    rvSearchResults.alpha = 0f
                    rvSearchResults.animate().alpha(1f).setDuration(250).start()
                    hideKeyboard()
                } else if (!append) {
                    revealModuleContent()
                    hideKeyboard()
                }
            }

            override fun onError(error: String) {
                if (isFinishing || isDestroyed || requestId != latestSearchRequestId) return
                if (append) {
                    searchPaginationInFlight = false
                    tvSearchState.text = "Error al cargar más resultados."
                } else {
                    setSearchLoadingState(false, "Error: $error")
                }
            }
        })
    }

    private fun performOfflineSearch(query: String): List<YouTubeMusicService.TrackResult> {
        val normalizedQuery = normalizeForFilter(query)
        if (normalizedQuery.isEmpty()) return emptyList()
        val queryTokens = normalizedQuery.split(Regex("\\s+")).filter { it.isNotEmpty() }
        val scored = mutableListOf<Pair<YouTubeMusicService.TrackResult, Int>>()
        val seenIds = mutableSetOf<String>()

        fun tryAdd(track: FavoritesPlaylistStore.FavoriteTrack) {
            val key = "video|${track.videoId}"
            if (key in seenIds) return
            val score = computeFuzzyScore(track.title, track.artist, normalizedQuery, queryTokens)
            if (score <= 0) return
            seenIds.add(key)
            scored.add(YouTubeMusicService.TrackResult(
                "video", track.videoId, track.title, track.artist, track.imageUrl
            ) to score)
        }

        // Search in favorites
        FavoritesPlaylistStore.loadFavorites(this).forEach { tryAdd(it) }

        // Search in custom playlists
        for (name in CustomPlaylistsStore.getAllPlaylistNames(this)) {
            CustomPlaylistsStore.getTracksFromPlaylist(this, name).forEach { tryAdd(it) }
        }

        // Search in cached YouTube playlists
        try {
            val streamingCache = getSharedPreferences("streaming_cache", Context.MODE_PRIVATE)
            val allKeys = streamingCache.all
            for ((key, value) in allKeys) {
                if (key.startsWith("playlist_tracks_data_") && value is String) {
                    val arr = org.json.JSONArray(value)
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        val track = FavoritesPlaylistStore.FavoriteTrack(
                            obj.optString("videoId"),
                            obj.optString("title"),
                            obj.optString("artist"),
                            obj.optString("duration", ""),
                            obj.optString("imageUrl")
                        )
                        tryAdd(track)
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("SearchActivity", "Error searching cached playlists: ${e.message}")
        }

        // Search in playback history
        try {
            val history = PlaybackHistoryStore.load(this)
            history.queue.forEach { q ->
                val track = FavoritesPlaylistStore.FavoriteTrack(
                    q.videoId, q.title, q.artist, q.duration, q.imageUrl
                )
                tryAdd(track)
            }
        } catch (e: Exception) {
            android.util.Log.e("SearchActivity", "Error searching history: ${e.message}")
        }

        // Sort by score descending
        scored.sortByDescending { it.second }
        return scored.map { it.first }
    }

    /** Fuzzy score: returns >0 if any query token matches title or artist. Title scores much higher. */
    private fun computeFuzzyScore(title: String, artist: String, query: String, tokens: List<String>): Int {
        val t = normalizeForFilter(title)
        val a = normalizeForFilter(artist)
        var score = 0

        // Full query match in title (highest priority)
        if (t == query) score += 1000
        else if (t.startsWith("$query ")) score += 900
        else if (t.startsWith(query)) score += 700
        else if (t.contains(query)) score += 500

        // Helper for typo-tolerant matching
        fun isFuzzyMatch(tok: String, word: String): Boolean {
            if (word.startsWith(tok)) return true
            if (tok.length >= 4 && word.length >= 4) {
                return tok.substring(0, 4) == word.substring(0, 4)
            }
            return false
        }

        val titleWords = t.split(Regex("\\s+"))
        val artistWords = a.split(Regex("\\s+"))

        // Count how many query tokens match anywhere (title or artist)
        val anyHits = tokens.count { 
            titleWords.contains(it) || artistWords.contains(it) || 
            titleWords.any { w -> isFuzzyMatch(it, w) } || 
            artistWords.any { w -> isFuzzyMatch(it, w) } 
        }

        // Require at least majority of tokens to match, otherwise discard (returns 0)
        // Exception: if the query itself is fully contained in title or artist, keep it
        val exactMatchFallback = t.contains(query) || a.contains(query)
        if (anyHits < (tokens.size + 1) / 2 && !exactMatchFallback) {
            return 0
        }

        // Per-token matching in title
        for (tok in tokens) {
            if (titleWords.contains(tok)) {
                score += 100
            } else if (titleWords.any { isFuzzyMatch(tok, it) }) {
                score += 40
            } else if (t.contains(tok)) {
                score += 10
            }
        }

        // Artist match (much lower weight)
        if (a == query) score += 80
        else if (a.startsWith("$query ")) score += 70
        else if (a.startsWith(query)) score += 60
        else if (a.contains(query)) score += 40
        
        for (tok in tokens) {
            if (artistWords.contains(tok)) {
                score += 15
            } else if (artistWords.any { isFuzzyMatch(tok, it) }) {
                score += 5
            }
        }

        // Offline boost is handled in performOfflineSearch where videoId is available
        score += 5

        return score
    }

    private fun loadMoreSearchResults() {
        if (searching || searchPaginationInFlight || !hasMoreSearchPages || activeSearchQuery.isEmpty()) return
        requestPagedSearchResults(activeSearchQuery, nextSearchPageToken, true)
    }

    private fun appendUniqueTracks(incoming: List<YouTubeMusicService.TrackResult>) {
        val existingKeys = allTracks.map { "${it.resultType}|${it.contentId}" }.toSet()
        val newOnes = incoming.filterNot { "${it.resultType}|${it.contentId}" in existingKeys }
        allTracks.addAll(newOnes)
    }

    private fun applyActiveFilter(query: String?, forceSort: Boolean = false) {
        val normalizedQuery = query?.trim() ?: ""
        val filtered = allTracks.toMutableList()

        if (forceSort && normalizedQuery.isNotEmpty() && filtered.size > 1) {
            sortResults(filtered, normalizedQuery)
        }

        tracks.clear()
        if (filtered.isEmpty()) {
            featuredTrack = null
            llFeaturedResult.visibility = View.GONE
        } else {
            featuredTrack = filtered[0].also { bindFeaturedTrack(it) }
            llFeaturedResult.visibility = View.VISIBLE
            if (filtered.size > 1) tracks.addAll(filtered.subList(1, filtered.size))
        }
        adapter?.submitResults(tracks.toList())

        // Forzar recálculo del layout después de actualizar visibilidad del featured result
        val rootLayout = findViewById<androidx.constraintlayout.widget.ConstraintLayout>(R.id.llSearchRoot)
        rootLayout.post {
            rootLayout.invalidate()
            rootLayout.requestLayout()
        }
    }

    private fun sortResults(list: MutableList<YouTubeMusicService.TrackResult>, query: String) {
        val normalized = normalizeForFilter(query)
        val tokens = normalized.split(Regex("\\s+")).filter { it.isNotEmpty() }
        list.sortByDescending { computeScore(it, normalized, tokens) }
    }

    private fun computeScore(track: YouTubeMusicService.TrackResult, query: String, tokens: List<String>): Int {
        val t = normalizeForFilter(track.title)
        val s = normalizeForFilter(track.subtitle)
        var score = 0
        
        // Exact full title match (Absolute priority)
        if (t == query) {
            score += 10000 
        } else if (t.startsWith("$query ")) {
            score += 8000
        } else if (t.startsWith(query)) {
            score += 6000
        } else if (t.contains(query)) {
            score += 4000
        }

        // Token matching
        val titleWords = t.split(Regex("\\s+")).filter { it.isNotEmpty() }
        val subtitleWords = s.split(Regex("\\s+")).filter { it.isNotEmpty() }
        
        var titleHits = 0
        var subtitleHits = 0
        
        tokens.forEach { tok ->
            if (titleWords.contains(tok)) {
                titleHits++
                score += 500
            } else if (titleWords.any { it.startsWith(tok) }) {
                score += 200
            }
            
            if (subtitleWords.contains(tok)) {
                subtitleHits++
                score += 100
            }
        }
        
        // Boost for matching ALL tokens in title
        if (titleHits >= tokens.size && tokens.isNotEmpty()) {
            score += 2000
        } else if (titleHits + subtitleHits >= tokens.size && tokens.isNotEmpty()) {
            score += 1000
        }

        // Subtitle/Artist matches (low weight tiebreakers)
        if (s == query) score += 200
        else if (s.contains(query)) score += 100
        
        // Downloaded boost (significant to ensure local favorites rank first for same name)
        val isDownloaded = track.videoId.isNotEmpty() && OfflineAudioStore.hasValidatedOfflineAudio(this, track.videoId, null)
        if (isDownloaded) {
            score += 5000
        }
        
        return score
    }

    private fun bindFeaturedTrack(track: YouTubeMusicService.TrackResult) {
        tvFeaturedTitle.text = track.title
        val typeLabel = searchTypeLabel(track)
        if (typeLabel.isEmpty()) {
            tvFeaturedSubtitle.text = track.subtitle
        } else {
            tvFeaturedSubtitle.text = if (track.subtitle.isEmpty()) typeLabel else "$typeLabel • ${track.subtitle}"
        }
        
        // Estado de descarga para el resultado destacado
        val isDownloaded = track.videoId.isNotEmpty() && OfflineAudioStore.hasValidatedOfflineAudio(this, track.videoId, null)
        if (isDownloaded) {
            ivFeaturedOfflineIndicator.visibility = View.VISIBLE
            ivFeaturedOfflineIndicator.setImageResource(R.drawable.ic_check_small)
            ivFeaturedOfflineIndicator.setBackgroundResource(R.drawable.bg_offline_state_filled_primary)
            ivFeaturedOfflineIndicator.setColorFilter(ContextCompat.getColor(this, R.color.surface_dark))
        } else {
            ivFeaturedOfflineIndicator.visibility = View.GONE
        }
        
        loadArtworkInto(ivFeaturedThumb, track.thumbnailUrl, track.videoId)
        llFeaturedResult.setOnClickListener { onTrackClicked(track) }
        findViewById<View>(R.id.btnFeaturedPlay).setOnClickListener { onTrackClicked(track) }
    }

    private val playbackListener = object : PlaybackEventBus.Listener {
        override fun onPlaybackSnapshotUpdated() {
            // Solo actualizar si estamos en la vista de resultados (no en sugerencias)
            val showingResults = nsvSearchContent.visibility == View.VISIBLE
            if (showingResults) {
                runOnUiThread {
                    updateMiniPlayerUi(forceVisibleInResults = true)
                }
            }
        }
    }

    private fun onTrackClicked(track: YouTubeMusicService.TrackResult) {
        if ("playlist".equals(track.resultType, ignoreCase = true)) {
            // For playlists, delegate to MainActivity
            val playbackIntent = Intent(this, MainActivity::class.java).apply {
                action = MainActivity.ACTION_PLAY_FROM_SEARCH
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(MainActivity.EXTRA_OPENED_FROM_SEARCH_ACTIVITY, true)
                putExtra(EXTRA_RESULT_TYPE, track.resultType ?: "")
                putExtra(EXTRA_RESULT_CONTENT_ID, track.contentId ?: "")
                putExtra(EXTRA_RESULT_TITLE, track.title ?: "")
                putExtra(EXTRA_RESULT_SUBTITLE, track.subtitle ?: "")
                putExtra(EXTRA_RESULT_THUMBNAIL, track.thumbnailUrl ?: "")
            }
            startActivity(playbackIntent)
            return
        }

        // Build queue from all video results
        val allResults = listOfNotNull(featuredTrack) + tracks
        val videoResults = allResults.filter { it.videoId?.isNotEmpty() == true }
        if (videoResults.isEmpty()) return

        val ids = ArrayList(videoResults.map { it.videoId ?: "" })
        val titles = ArrayList(videoResults.map { it.title ?: "Tema" })
        val artists = ArrayList(videoResults.map { it.subtitle ?: "" })
        val durations = ArrayList(videoResults.map { "--:--" })
        val images = ArrayList(videoResults.map { it.thumbnailUrl ?: "" })

        var selectedIndex = videoResults.indexOfFirst { it.videoId == track.videoId }
        if (selectedIndex < 0) selectedIndex = 0

        val selectedVideoId = videoResults.getOrNull(selectedIndex)?.videoId.orEmpty()
        if (selectedVideoId.isNotEmpty()
            && OfflineAudioStore.hasValidatedOfflineAudio(this, selectedVideoId, null)
        ) {
            clearPersistedPositionFor(selectedVideoId)
        }

        // Play locally in this activity
        playInLocalPlayer(ids, titles, artists, durations, images, selectedIndex)
    }

    private fun clearPersistedPositionFor(videoId: String) {
        if (videoId.isBlank()) return
        getSharedPreferences("player_state", MODE_PRIVATE)
            .edit()
            .remove("yt_pos_${videoId}")
            .apply()
    }


    private fun playInLocalPlayer(
        ids: ArrayList<String>,
        titles: ArrayList<String>,
        artists: ArrayList<String>,
        durations: ArrayList<String>,
        images: ArrayList<String>,
        selectedIndex: Int
    ) {
        // Cancel any pending container hide from a previous call
        pendingContainerHide?.let { searchPlayerContainer.removeCallbacks(it) }
        pendingContainerHide = null

        val existingPlayer = supportFragmentManager.findFragmentByTag(TAG_SONG_PLAYER) as? SongPlayerFragment
        if (existingPlayer != null) {
            if (existingPlayer.isAdded) {
                // Stop current audio immediately before loading the new track
                existingPlayer.externalPause()
                // Reuse existing player - replace queue and start playing the new track
                existingPlayer.externalReplaceQueueFromStart(ids, titles, artists, durations, images, selectedIndex, true)
                // Ensure container is visible
                searchPlayerContainer.visibility = View.VISIBLE
                if (!isPlayerVisible) {
                    existingPlayer.externalTryEnterMiniMode()
                }
            }
        } else {
            // Remove any stale fragment first
            existingPlayer?.let {
                supportFragmentManager.beginTransaction().remove(it).commitNowAllowingStateLoss()
            }

            // Create new player in mini mode (hidden)
            val playerFragment = SongPlayerFragment.newInstance(ids, titles, artists, durations, images, selectedIndex, true, true)
            searchPlayerContainer.visibility = View.VISIBLE
            supportFragmentManager.beginTransaction()
                .setReorderingAllowed(true)
                .add(R.id.searchPlayerContainer, playerFragment, TAG_SONG_PLAYER)
                .hide(playerFragment)
                .commitNowAllowingStateLoss()

            // Let it start in mini mode - hide the container after initialization
            val hideRunnable = Runnable {
                if (!isPlayerVisible) {
                    searchPlayerContainer.visibility = View.GONE
                }
                pendingContainerHide = null
            }
            pendingContainerHide = hideRunnable
            searchPlayerContainer.postDelayed(hideRunnable, 100)
        }

        // Update mini player after a short delay to let playback initialize
        llMiniPlayer.postDelayed({
            updateMiniPlayerUi(forceVisibleInResults = true)
        }, 300)
    }

    private fun showTrackOptionsBottomSheet(track: YouTubeMusicService.TrackResult, anchor: View) {
        anchor.performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK)
        
        val dialog = com.google.android.material.bottomsheet.BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.bottom_sheet_track_options, null)
        dialog.setContentView(view)

        val tvTitle = view.findViewById<TextView>(R.id.tvBsTrackTitle)
        val tvSubtitle = view.findViewById<TextView>(R.id.tvBsTrackSubtitle)
        val ivArt = view.findViewById<ImageView>(R.id.ivBsTrackArt)
        
        tvTitle.text = track.title ?: "Tema"
        val typeLabel = searchTypeLabel(track)
        if (typeLabel.isEmpty()) {
            tvSubtitle.text = track.subtitle
        } else {
            tvSubtitle.text = if (track.subtitle.isEmpty()) typeLabel else "$typeLabel • ${track.subtitle}"
        }
        loadArtworkInto(ivArt, track.thumbnailUrl, track.videoId)

        val isPlaylist = "playlist".equals(track.resultType, ignoreCase = true)

        view.findViewById<View>(R.id.btnBsPlayNext).setOnClickListener {
            addToQueue(track, true)
            dialog.dismiss()
        }
        view.findViewById<View>(R.id.btnBsAddPrimary).setOnClickListener {
            addToQueue(track, false)
            dialog.dismiss()
        }
        view.findViewById<View>(R.id.btnBsShare).setOnClickListener {
            shareTrack(track)
            dialog.dismiss()
        }

        // List actions
        val btnPlayPlaylist = view.findViewById<View>(R.id.btnBsPlayPlaylist)
        val btnFavorite = view.findViewById<View>(R.id.btnBsFavorite)
        
        if (isPlaylist) {
            btnPlayPlaylist.visibility = View.VISIBLE
            btnPlayPlaylist.setOnClickListener {
                onTrackClicked(track)
                dialog.dismiss()
            }
        } else {
            btnPlayPlaylist.visibility = View.GONE
        }

        // Add Favorites to list
        btnFavorite.visibility = View.VISIBLE
        val ivFav = btnFavorite.findViewById<ImageView>(R.id.ivBsFavorite)
        val tvFav = btnFavorite.findViewById<TextView>(R.id.tvBsFavorite)
        ivFav.setImageResource(R.drawable.ic_favorite_star)
        tvFav.text = "Agregar a Favoritos"
        btnFavorite.setOnClickListener {
            addTrackToFavorites(track)
            dialog.dismiss()
        }

        // If it's a playlist and we can check its download status, we should show "Eliminar descargas"
        // But SearchActivity results are mostly online.

        val parent = view.parent as? View
        parent?.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        
        dialog.show()
    }

    private fun addTrackToFavorites(track: YouTubeMusicService.TrackResult) {
        if (track.videoId.isNullOrEmpty()) return
        FavoritesPlaylistStore.upsertFavorite(
            this,
            track.videoId,
            track.title ?: "Tema",
            track.subtitle ?: "Artista",
            "--:--",
            track.thumbnailUrl ?: ""
        )
    }

    private fun addToQueue(track: YouTubeMusicService.TrackResult, playNext: Boolean) {
        Intent(this, MainActivity::class.java).apply {
            action = if (playNext) MainActivity.ACTION_PLAY_NEXT else MainActivity.ACTION_ADD_TO_QUEUE
            flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_RESULT_TYPE, track.resultType ?: "")
            putExtra(EXTRA_RESULT_VIDEO_ID, track.videoId ?: "")
            putExtra(EXTRA_RESULT_TITLE, track.title ?: "")
            putExtra(EXTRA_RESULT_SUBTITLE, track.subtitle ?: "")
            putExtra(EXTRA_RESULT_THUMBNAIL, track.thumbnailUrl ?: "")
            startActivity(this)
        }
    }

    private fun shareTrack(track: YouTubeMusicService.TrackResult) {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "Escucha ${track.title} de ${track.subtitle} en Sleppify: https://music.youtube.com/watch?v=${track.videoId}")
        }
        startActivity(Intent.createChooser(shareIntent, "Compartir canción"))
    }

    private fun setSearchLoadingState(loading: Boolean, msg: String) {
        searching = loading
        val llState = findViewById<LinearLayout>(R.id.llSearchState)
        if (loading) {
            showModuleLoadingOverlay()
            tvSearchState.visibility = View.GONE
            llState.visibility = View.VISIBLE
        } else {
            if (msg.isNotEmpty()) {
                tvSearchState.text = msg
                tvSearchState.visibility = View.VISIBLE
                llState.visibility = View.VISIBLE
            } else {
                tvSearchState.visibility = View.GONE
                llState.visibility = View.GONE
            }
        }
    }

    private fun restoreRecentSearchQueries() {
        val prefs = getSharedPreferences(PREFS_STREAMING_CACHE, MODE_PRIVATE)
        val json = prefs.getString(PREF_RECENT_SEARCH_QUERIES, "[]")
        recentSearchQueries.clear()
        try {
            val array = JSONArray(json)
            for (i in 0 until array.length()) recentSearchQueries.add(array.getString(i))
        } catch (e: Exception) {}
    }

    private fun rememberRecentSearchQuery(query: String) {
        val norm = query.trim()
        if (norm.isEmpty()) return
        recentSearchQueries.run {
            remove(norm)
            add(0, norm)
            if (size > SEARCH_SUGGESTION_RECENT_LIMIT) removeAt(size - 1)
        }
        saveRecentSearchQueries()
    }

    private fun saveRecentSearchQueries() {
        val array = JSONArray(recentSearchQueries)
        getSharedPreferences(PREFS_STREAMING_CACHE, MODE_PRIVATE).edit().putString(PREF_RECENT_SEARCH_QUERIES, array.toString()).apply()
    }

    private fun refreshSearchSuggestions(draft: String?) {
        val norm = draft?.trim() ?: ""
        val suggestions = poolSuggestions(norm)
        (rvSearchSuggestions.adapter as? SuggestionsAdapter)?.updateSuggestions(suggestions)
        rvSearchSuggestions.visibility = if (suggestions.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun poolSuggestions(draft: String): List<String> {
        val normDraft = normalizeForFilter(draft)
        return (recentSearchQueries + DEFAULT_SEARCH_SUGGESTIONS).distinct()
            .filter { candidate ->
                if (normDraft.isEmpty()) true
                else {
                    val normCand = normalizeForFilter(candidate)
                    normCand.contains(normDraft) || normDraft.contains(normCand)
                }
            }
            .take(SEARCH_SUGGESTION_RECENT_LIMIT)
    }

    private val miniProgressHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val miniProgressTicker = object : Runnable {
        override fun run() {
            if (isFinishing || isDestroyed) return
            if (llMiniPlayer.visibility == View.VISIBLE) {
                updateMiniPlayerUi()
            }
            miniProgressHandler.postDelayed(this, 1000)
        }
    }

    override fun onResume() {
        super.onResume()
        PlaybackEventBus.addListener(playbackListener)
        val showingResults = nsvSearchContent.visibility == View.VISIBLE
        if (showingResults) {
            updateMiniPlayerUi(forceVisibleInResults = true)
        } else {
            llMiniPlayer.visibility = View.GONE
            bottomNavigation.visibility = View.GONE
        }
        miniProgressHandler.removeCallbacks(miniProgressTicker)
        miniProgressHandler.post(miniProgressTicker)
    }

    override fun onPause() {
        PlaybackEventBus.removeListener(playbackListener)
        miniProgressHandler.removeCallbacks(miniProgressTicker)
        super.onPause()
    }

    override fun onDestroy() {
        // player?.externalSnapshotForNavigation() is redundant and corrupts position (since fragment is already destroyed)
        super.onDestroy()
    }

    private fun updateMiniPlayerUi(forceVisibleInResults: Boolean = false) {
        val snapshot = PlaybackHistoryStore.load(this)
        bottomNavigation.visibility = View.VISIBLE

        if (snapshot.queue.isEmpty()) {
            llMiniPlayer.visibility = if (forceVisibleInResults) View.VISIBLE else View.GONE
            if (forceVisibleInResults) {
                tvMiniPlayerTitle.text = "Reproduce una canción"
                tvMiniPlayerSubtitle.text = "Selecciona un resultado"
                btnMiniPlayPause.setImageResource(R.drawable.ic_mini_play)
                sbMiniPlayerProgress.progress = 0
                loadArtworkInto(ivMiniPlayerArt, null)
            }
            return
        }

        if (llMiniPlayer.visibility != View.VISIBLE) {
            val distance = if (llMiniPlayer.height > 0) llMiniPlayer.height.toFloat() else 300f
            llMiniPlayer.translationY = distance
            llMiniPlayer.visibility = View.VISIBLE
            llMiniPlayer.animate().cancel()
            llMiniPlayer.animate()
                .translationY(0f)
                .setDuration(250)
                .setInterpolator(android.view.animation.PathInterpolator(0.4f, 0f, 0.2f, 1f))
                .withEndAction(null)
                .start()
        }

        val current = snapshot.currentTrack()
        tvMiniPlayerTitle.text = current?.title.orEmpty()
        tvMiniPlayerSubtitle.text = current?.artist.orEmpty()

        if (snapshot.isPlaying) {
            btnMiniPlayPause.setImageResource(R.drawable.ic_mini_pause)
        } else {
            btnMiniPlayPause.setImageResource(R.drawable.ic_mini_play)
        }

        sbMiniPlayerProgress.progress = 0

        loadArtworkInto(ivMiniPlayerArt, current?.imageUrl)
    }

    private fun openCurrentPlayerFromMiniBar() {
        // Cancel any pending container hide
        pendingContainerHide?.let { searchPlayerContainer.removeCallbacks(it) }
        pendingContainerHide = null

        val existingPlayer = supportFragmentManager.findFragmentByTag(TAG_SONG_PLAYER) as? SongPlayerFragment
        if (existingPlayer != null) {
            if (existingPlayer.isAdded) {
                // Show existing player full screen
                showPlayerFullScreen(existingPlayer)
            }
        } else {
            // Create player from persisted state
            val snapshot = PlaybackHistoryStore.load(this)
            if (snapshot.queue.isEmpty()) return

            val ids = ArrayList(snapshot.queue.map { it.videoId })
            val titles = ArrayList(snapshot.queue.map { it.title })
            val artists = ArrayList(snapshot.queue.map { it.artist })
            val durations = ArrayList(snapshot.queue.map { it.duration })
            val images = ArrayList(snapshot.queue.map { it.imageUrl })
            val selectedIndex = snapshot.currentIndex.coerceIn(0, ids.size - 1)

            val playerFragment = SongPlayerFragment.newInstance(ids, titles, artists, durations, images, selectedIndex, snapshot.isPlaying, true)
            searchPlayerContainer.visibility = View.VISIBLE
            isPlayerVisible = true
            
            llMiniPlayer.animate().cancel()
            val distance = if (llMiniPlayer.height > 0) llMiniPlayer.height.toFloat() else 300f
            llMiniPlayer.animate().translationY(distance).setDuration(250)
                .setInterpolator(android.view.animation.PathInterpolator(0.4f, 0f, 0.2f, 1f))
                .withEndAction {
                    llMiniPlayer.visibility = View.GONE
                }.start()
                
            supportFragmentManager.beginTransaction()
                .setReorderingAllowed(true)
                .add(R.id.searchPlayerContainer, playerFragment, TAG_SONG_PLAYER)
                .runOnCommit {
                    playerFragment.externalAnimateEnterSlide()
                }
                .commitAllowingStateLoss()
        }
    }

    private fun showPlayerFullScreen(player: SongPlayerFragment) {
        // Cancel any pending container hide
        pendingContainerHide?.let { searchPlayerContainer.removeCallbacks(it) }
        pendingContainerHide = null

        searchPlayerContainer.visibility = View.VISIBLE
        isPlayerVisible = true
        
        llMiniPlayer.animate().cancel()
        val distance = if (llMiniPlayer.height > 0) llMiniPlayer.height.toFloat() else 300f
        llMiniPlayer.animate().translationY(distance).setDuration(250)
            .setInterpolator(android.view.animation.PathInterpolator(0.4f, 0f, 0.2f, 1f))
            .withEndAction {
                llMiniPlayer.visibility = View.GONE
            }.start()
            
        supportFragmentManager.beginTransaction()
            .setReorderingAllowed(true)
            .show(player)
            .runOnCommit {
                player.externalAnimateEnterSlide()
            }
            .commitAllowingStateLoss()
    }

    private fun collapsePlayerToMini() {
        val player = supportFragmentManager.findFragmentByTag(TAG_SONG_PLAYER) as? SongPlayerFragment
        if (player != null && player.isAdded) {
            player.externalSnapshotForNavigation()
            supportFragmentManager.beginTransaction()
                .setReorderingAllowed(true)
                .hide(player)
                .commitNowAllowingStateLoss()
        }
        isPlayerVisible = false
        searchPlayerContainer.visibility = View.GONE
        updateMiniPlayerUi(forceVisibleInResults = true)
    }

    private fun toggleCurrentMiniPlayback() {
        val localPlayer = supportFragmentManager.findFragmentByTag(TAG_SONG_PLAYER) as? SongPlayerFragment
        if (localPlayer != null && localPlayer.isAdded) {
            // Toggle local player directly
            localPlayer.externalTogglePlayback()
            // Update mini player UI immediately
            llMiniPlayer.postDelayed({
                updateMiniPlayerUi(forceVisibleInResults = true)
            }, 150)
        } else {
            // No local player - dispatch toggle to MainActivity
            try {
                val intent = Intent().apply {
                    action = MainActivity.ACTION_TOGGLE_CURRENT_PLAYBACK
                    putExtra(MainActivity.EXTRA_OPENED_FROM_SEARCH_ACTIVITY, true)
                }
                MainActivity.dispatchSearchPlayback(intent)
            } catch (_: Exception) {}
        }
    }

    private fun loadArtworkInto(target: ImageView, url: String?, videoId: String? = null) {
        var finalUrl = url?.trim()
        
        if (finalUrl.isNullOrEmpty() && !videoId.isNullOrEmpty() && OfflineAudioStore.hasOfflineAudio(this, videoId)) {
            finalUrl = OfflineAudioStore.getThumbnailUri(this, videoId)?.toString()
        }

        if (finalUrl.isNullOrEmpty()) {
            target.setImageResource(R.drawable.ic_music)
            return
        }

        val safeUrl = if (finalUrl.startsWith("//")) "https:$finalUrl" else finalUrl
        val isLocalUri = safeUrl.startsWith("file://") || safeUrl.startsWith("content://")
        val offlineOnly = !isNetworkAvailable() && !isLocalUri
        
        val base = Glide.with(this).load(safeUrl)
            .transform(YouTubeCropTransformation())
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .onlyRetrieveFromCache(offlineOnly)
            .placeholder(R.drawable.ic_music)
            .error(R.drawable.ic_music)
        base.into(target)
    }

    private fun searchTypeLabel(track: YouTubeMusicService.TrackResult) = when (track.resultType?.lowercase(Locale.US)) {
        "video" -> ""
        "channel" -> "Artista"
        "playlist" -> "Playlist"
        else -> "Resultado"
    }

    private fun normalizeForFilter(value: String?): String {
        if (value.isNullOrEmpty()) return ""
        normalizedFilterCache[value]?.let { return it }
        val decomposed = Normalizer.normalize(value, Normalizer.Form.NFD)
        val norm = decomposed.filter { Character.getType(it) != Character.NON_SPACING_MARK.toInt() }.lowercase().trim()
        if (normalizedFilterCache.size > 256) normalizedFilterCache.clear()
        normalizedFilterCache[value] = norm
        return norm
    }

    private fun isNetworkAvailable(): Boolean {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) || caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
    }

    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(currentFocus?.windowToken, 0)
    }

    private fun showModuleLoadingOverlay() { moduleLoadingOverlay.visibility = View.VISIBLE }
    private fun revealModuleContent() { moduleLoadingOverlay.visibility = View.GONE }

    // ── Adapters ────────────────────────────────────────

    private inner class SearchResultsAdapter(
        val onClick: (YouTubeMusicService.TrackResult) -> Unit,
        val onMoreClick: (YouTubeMusicService.TrackResult, View) -> Unit
    ) : RecyclerView.Adapter<SearchResultsAdapter.TrackViewHolder>() {
        private val data = mutableListOf<YouTubeMusicService.TrackResult>()

        init { setHasStableIds(true) }

        override fun getItemId(position: Int) = data[position].let { "${it.resultType}|${it.contentId}|${it.title}".hashCode().toLong() }

        fun submitResults(newData: List<YouTubeMusicService.TrackResult>) {
            val old = data.toList()
            val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
                override fun getOldListSize() = old.size
                override fun getNewListSize() = newData.size
                override fun areItemsTheSame(op: Int, np: Int) = old[op].contentId == newData[np].contentId && old[op].resultType == newData[np].resultType
                override fun areContentsTheSame(op: Int, np: Int) = old[op] == newData[np]
            })
            data.clear()
            data.addAll(newData)
            diff.dispatchUpdatesTo(this)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = TrackViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_music_search_result, parent, false))

        override fun onBindViewHolder(holder: TrackViewHolder, position: Int) {
            val item = data[position]
            holder.title.text = item.title ?: "Resultado"
            val typeLabel = searchTypeLabel(item)
            if (typeLabel.isEmpty()) {
                holder.subtitle.text = item.subtitle
            } else {
                holder.subtitle.text = if (item.subtitle.isEmpty()) typeLabel else "$typeLabel • ${item.subtitle}"
            }
            loadArtworkInto(holder.thumb, item.thumbnailUrl, item.videoId)
            
            // Check offline status
            if (item.videoId?.isNotEmpty() == true && OfflineAudioStore.hasOfflineAudio(this@SearchActivity, item.videoId)) {
                holder.offlineIndicator.visibility = View.VISIBLE
                holder.offlineIndicator.setImageResource(R.drawable.ic_check_small)
                holder.offlineIndicator.setBackgroundResource(R.drawable.bg_offline_state_filled_primary)
                holder.offlineIndicator.setColorFilter(ContextCompat.getColor(this@SearchActivity, R.color.surface_dark))
            } else {
                holder.offlineIndicator.visibility = View.GONE
            }
            
            holder.itemView.setOnClickListener { onClick(item) }
            holder.itemView.setOnLongClickListener {
                onMoreClick(item, it)
                true
            }
            holder.more.setOnClickListener { onMoreClick(item, it) }
        }

        override fun getItemCount() = data.size

        inner class TrackViewHolder(v: View) : RecyclerView.ViewHolder(v) {
            val thumb: ImageView = v.findViewById(R.id.ivTrackThumb)
            val title: TextView = v.findViewById(R.id.tvTrackTitle)
            val subtitle: TextView = v.findViewById(R.id.tvTrackSubtitle)
            val more: ImageView = v.findViewById(R.id.ivTrackMore)
            val offlineIndicator: ImageView = v.findViewById(R.id.ivOfflineIndicator)
        }
    }

    private inner class SuggestionsAdapter(val onClick: (String) -> Unit) : RecyclerView.Adapter<SuggestionsAdapter.ViewHolder>() {
        private val data = mutableListOf<String>()

        fun updateSuggestions(newList: List<String>) {
            data.clear()
            data.addAll(newList)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(p: ViewGroup, t: Int) = ViewHolder(LayoutInflater.from(p.context).inflate(R.layout.item_search_suggestion, p, false))

        override fun onBindViewHolder(h: ViewHolder, p: Int) {
            val s = data[p]
            h.text.text = s
            val recent = recentSearchQueries.contains(s)
            h.icon.setImageResource(if (recent) R.drawable.ic_time_24 else android.R.drawable.ic_menu_search)
            h.icon.alpha = if (recent) 0.7f else 0.5f
            h.itemView.setOnClickListener { onClick(s) }
        }

        override fun getItemCount() = data.size

        inner class ViewHolder(v: View) : RecyclerView.ViewHolder(v) {
            val text: TextView = v.findViewById(R.id.tvSuggestionText)
            val icon: ImageView = v.findViewById(R.id.ivSuggestionIcon)
        }
    }
}
