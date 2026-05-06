package com.example.sleppify

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.example.sleppify.SystemType
import com.example.sleppify.utils.YouTubeCropTransformation
import com.google.android.material.imageview.ShapeableImageView
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import java.text.Normalizer
import java.util.Locale

class SearchFragment : Fragment() {

    companion object {
        const val TAG = "SearchFragment"
        
        const val EXTRA_RESULT_TYPE = "search_result_type"
        const val EXTRA_RESULT_VIDEO_ID = "search_result_video_id"
        const val EXTRA_RESULT_CONTENT_ID = "search_result_content_id"
        const val EXTRA_RESULT_TITLE = "search_result_title"
        const val EXTRA_RESULT_SUBTITLE = "search_result_subtitle"
        const val EXTRA_RESULT_THUMBNAIL = "search_result_thumbnail"
        const val EXTRA_RESULT_TRACKS_JSON = "search_result_tracks_json"

        private const val PREFS_STREAMING_CACHE = "streaming_cache"
        private const val PREF_RECENT_SEARCH_QUERIES = "stream_recent_search_queries"
        private const val SEARCH_PAGE_SIZE = 20
        private const val SEARCH_SUGGESTION_RECENT_LIMIT = 6
        private const val SEARCH_SCROLL_LOAD_MORE_THRESHOLD = 4

        private val DEFAULT_SEARCH_SUGGESTIONS = arrayOf(
            "Lofi Chill",
            "EDM House",
            "Trap Latino",
            "Pop Latino"
        )

        fun newInstance() = SearchFragment()
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
    private lateinit var llSearchState: View
    private lateinit var nsvSearchContent: View
    private lateinit var rvSearchSuggestions: RecyclerView
    private lateinit var moduleLoadingOverlay: View
    private lateinit var llFeaturedResult: View
    private lateinit var ivFeaturedThumb: ImageView
    private lateinit var tvFeaturedTitle: TextView
    private lateinit var tvFeaturedSubtitle: TextView
    private lateinit var ivFeaturedOfflineIndicator: ImageView
    private lateinit var llMiniPlayer: LinearLayout
    private lateinit var ivMiniPlayerArt: ImageView
    private lateinit var tvMiniPlayerTitle: TextView
    private lateinit var tvMiniPlayerSubtitle: TextView
    private lateinit var btnMiniPlayPause: android.widget.ImageButton
    private lateinit var sbMiniPlayerProgress: android.widget.SeekBar

    private var adapter: SearchResultsAdapter? = null
    private var featuredTrack: YouTubeMusicService.TrackResult? = null
    
    private var searching = false
    private var searchPaginationInFlight = false
    private var hasMoreSearchPages = false
    private var nextSearchPageToken = ""
    private var activeSearchQuery = ""
    private var latestSearchRequestId = 0L

    private val searchHandler = Handler(Looper.getMainLooper())
    private var searchRunnable: Runnable? = null
    private val miniProgressHandler = Handler(Looper.getMainLooper())
    private var miniProgressTicker: Runnable? = null

    private var lastMiniArtUrl = ""
    private var miniPlaying = false

    private var backPressedCallback: OnBackPressedCallback? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_search, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initViews(view)
        setupRecyclerView()
        setupSuggestionsRecyclerView()
        setupSearchInput()
        
        restoreRecentSearchQueries()
        refreshSearchSuggestions("")

        setupBackNavigation()
    }

    private fun initViews(root: View) {
        etSearchQuery = root.findViewById(R.id.etSearchQuery)
        ivSearchClear = root.findViewById(R.id.ivSearchClear)
        rvSearchResults = root.findViewById(R.id.rvSearchResults)
        tvSearchState = root.findViewById(R.id.tvSearchState)
        llSearchState = root.findViewById(R.id.llSearchState)
        nsvSearchContent = root.findViewById(R.id.nsvSearchContent)
        rvSearchSuggestions = root.findViewById(R.id.rvSearchSuggestions)
        moduleLoadingOverlay = root.findViewById(R.id.moduleLoadingOverlay)
        llFeaturedResult = root.findViewById(R.id.llFeaturedResult)
        ivFeaturedThumb = root.findViewById(R.id.ivFeaturedThumb)
        tvFeaturedTitle = root.findViewById(R.id.tvFeaturedTitle)
        tvFeaturedSubtitle = root.findViewById(R.id.tvFeaturedSubtitle)
        ivFeaturedOfflineIndicator = root.findViewById(R.id.ivFeaturedOfflineIndicator)
        llMiniPlayer = root.findViewById(R.id.llMiniPlayer)
        ivMiniPlayerArt = root.findViewById(R.id.ivMiniPlayerArt)
        tvMiniPlayerTitle = root.findViewById(R.id.tvMiniPlayerTitle)
        tvMiniPlayerSubtitle = root.findViewById(R.id.tvMiniPlayerSubtitle)
        btnMiniPlayPause = root.findViewById(R.id.btnMiniPlayPause)
        sbMiniPlayerProgress = root.findViewById(R.id.sbMiniPlayerProgress)

        llMiniPlayer.setOnClickListener { openPlayerFromMiniBar() }
        btnMiniPlayPause.setOnClickListener { toggleMiniPlayback() }

        ivSearchClear.setOnClickListener {
            etSearchQuery.setText("")
            showSuggestionsMode()
        }

        root.findViewById<View>(R.id.btnFeaturedPlay).setOnClickListener { featuredTrack?.let { onTrackClicked(it) } }
        
        (requireActivity() as? MainActivity)?.let { mainActivity ->
            mainActivity.findViewById<View>(R.id.btnSettings)?.setOnClickListener {
                mainActivity.enterSettings()
            }
            mainActivity.findViewById<TextView>(R.id.tvModuleTitle)?.setOnClickListener {
                mainActivity.closeSearchFragment()
            }
        }

        root.findViewById<View>(R.id.btnFeaturedShare).setOnClickListener {
            featuredTrack?.let { track ->
                val sendIntent: Intent = Intent().apply {
                    action = Intent.ACTION_SEND
                    putExtra(Intent.EXTRA_TEXT, "Escucha ${track.title} en Sleppify: https://music.youtube.com/watch?v=${track.videoId}")
                    type = "text/plain"
                }
                val shareIntent = Intent.createChooser(sendIntent, null)
                startActivity(shareIntent)
            }
        }
        
        if (SystemType.isTv(requireContext())) {
            moduleLoadingOverlay.setBackgroundColor(Color.TRANSPARENT)
            moduleLoadingOverlay.isClickable = false
            moduleLoadingOverlay.isFocusable = false
        }

        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            root.setPadding(systemBars.left, systemBars.top, systemBars.right, 0)
            insets
        }

        root.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                hideKeyboard()
            }
            false
        }
    }

    private fun setupBackNavigation() {
        backPressedCallback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (nsvSearchContent.visibility == View.VISIBLE) {
                    showSuggestionsMode()
                    etSearchQuery.requestFocus()
                    showKeyboard()
                } else {
                    isEnabled = false
                    if (requireActivity() is MainActivity) {
                        (requireActivity() as MainActivity).closeSearchFragment()
                    } else {
                        requireActivity().onBackPressedDispatcher.onBackPressed()
                    }
                }
            }
        }
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, backPressedCallback!!)
    }

    private fun setupRecyclerView() {
        rvSearchResults.layoutManager = LinearLayoutManager(requireContext())
        adapter = SearchResultsAdapter(
            onClick = { onTrackClicked(it) },
            onMoreClick = { track, anchor -> showTrackOptionsBottomSheet(track, anchor) }
        )
        rvSearchResults.adapter = adapter
        rvSearchResults.setHasFixedSize(true)
        rvSearchResults.itemAnimator = null
        rvSearchResults.setItemViewCacheSize(15)

        rvSearchResults.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (dy <= 0 || !hasMoreSearchPages || searchPaginationInFlight) return
                val layoutManager = recyclerView.layoutManager as LinearLayoutManager
                val lastVisible = layoutManager.findLastVisibleItemPosition()
                if (lastVisible >= (adapter?.itemCount ?: 0) - SEARCH_SCROLL_LOAD_MORE_THRESHOLD) {
                    loadMoreSearchResults()
                }
            }
        })
    }

    private fun setupSuggestionsRecyclerView() {
        val suggestionsAdapter = SuggestionsAdapter {
            etSearchQuery.setText(it)
            etSearchQuery.setSelection(it.length)
            performSearch(it)
            hideKeyboard()
        }
        rvSearchSuggestions.layoutManager = LinearLayoutManager(requireContext())
        rvSearchSuggestions.adapter = suggestionsAdapter
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
                performSearch(etSearchQuery.text.toString())
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
    }

    private fun showSuggestionsMode() {
        val query = etSearchQuery.text?.toString()?.trim() ?: ""
        refreshSearchSuggestions(query)
        rvSearchSuggestions.visibility = View.VISIBLE
        nsvSearchContent.visibility = View.GONE
        llSearchState.visibility = View.GONE
        view?.requestLayout()
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

    private fun performSearch(query: String) {
        if (searching) return
        val trimmedQuery = query.trim()
        if (trimmedQuery.isNotEmpty()) {
            startPagedSearch(trimmedQuery)
        }
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

        requestPagedSearchResults(query, "", false)
    }

    private fun requestPagedSearchResults(query: String, pageToken: String, append: Boolean) {
        val requestId = ++latestSearchRequestId

        // 1. Iniciar búsqueda local (Solo si no es scroll infinito/paginación)
        if (!append) {
            lifecycleScope.launch(Dispatchers.IO) {
                val localResults = performOfflineSearch(query)
                launch(Dispatchers.Main) {
                    if (activity == null || !isAdded || requestId != latestSearchRequestId) return@launch
                    appendUniqueTracks(localResults)
                    applyActiveFilter(query, forceSort = true)
                    
                    // Si no hay internet, cerramos el estado de carga aquí mismo
                    if (!isNetworkAvailable()) {
                        if (allTracks.isEmpty()) {
                            setSearchLoadingState(false, "No encontré resultados locales para: $query")
                        } else {
                            setSearchLoadingState(false, "")
                        }
                        revealModuleContent()
                        hideKeyboard()
                    }
                }
            }
        }

        if (!isNetworkAvailable()) return

        // 2. Proceso de búsqueda Online
        if (append) {
            searchPaginationInFlight = true
            tvSearchState.text = "Cargando mas resultados..."
        } else {
            setSearchLoadingState(true, "Buscando música...")
        }

        youTubeMusicService.searchTracksPaged(query, SEARCH_PAGE_SIZE, pageToken.takeIf { it.isNotEmpty() }, object : YouTubeMusicService.SearchPageCallback {
            override fun onSuccess(pageResult: YouTubeMusicService.SearchPageResult) {
                if (activity == null || !isAdded || requestId != latestSearchRequestId) return
                
                if (append) searchPaginationInFlight = false
                
                nextSearchPageToken = pageResult.nextPageToken
                hasMoreSearchPages = nextSearchPageToken.isNotEmpty()

                appendUniqueTracks(pageResult.tracks)
                applyActiveFilter(query, forceSort = true)

                if (allTracks.isEmpty()) {
                    if (!append) setSearchLoadingState(false, "No encontré resultados para: $query")
                } else if (!append) {
                    setSearchLoadingState(false, "")
                    // Pre-fetch opcional para mejor latencia
                    allTracks.firstOrNull()?.videoId?.let { id ->
                        lifecycleScope.launch(Dispatchers.IO) {
                            InnertubeResolver.resolveStreamUrl(requireContext(), id)
                        }
                    }
                }
                
                if (!append) {
                    revealModuleContent()
                    rvSearchResults.alpha = 0f
                    rvSearchResults.animate().alpha(1f).setDuration(250).start()
                    hideKeyboard()
                }
            }

            override fun onError(error: String) {
                if (activity == null || !isAdded || requestId != latestSearchRequestId) return
                if (append) searchPaginationInFlight = false
                
                if (allTracks.isEmpty()) {
                    setSearchLoadingState(false, "Error: $error")
                } else {
                    setSearchLoadingState(false, "")
                    if (append) Toast.makeText(requireContext(), "Error al cargar más resultados", Toast.LENGTH_SHORT).show()
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

        FavoritesPlaylistStore.loadFavorites(requireContext()).forEach { tryAdd(it) }

        for (name in CustomPlaylistsStore.getAllPlaylistNames(requireContext())) {
            CustomPlaylistsStore.getTracksFromPlaylist(requireContext(), name).forEach { tryAdd(it) }
        }

        try {
            val streamingCache = requireContext().getSharedPreferences("streaming_cache", Context.MODE_PRIVATE)
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
            Log.e("SearchFragment", "Error searching cached playlists: ${e.message}")
        }

        try {
            val history = PlaybackHistoryStore.load(requireContext())
            history.queue.forEach { q ->
                val track = FavoritesPlaylistStore.FavoriteTrack(
                    q.videoId, q.title, q.artist, q.duration, q.imageUrl
                )
                tryAdd(track)
            }
        } catch (e: Exception) {
            Log.e("SearchFragment", "Error searching history: ${e.message}")
        }

        scored.sortByDescending { it.second }
        return scored.map { it.first }
    }

    private fun computeFuzzyScore(title: String, artist: String, query: String, tokens: List<String>): Int {
        val t = normalizeForFilter(title)
        val a = normalizeForFilter(artist)
        var score = 0

        if (t == query) score += 1000
        else if (t.startsWith("$query ")) score += 900
        else if (t.startsWith(query)) score += 700
        else if (t.contains(query)) score += 500

        fun isFuzzyMatch(tok: String, word: String): Boolean {
            if (word.startsWith(tok)) return true
            if (tok.length >= 4 && word.length >= 4) {
                return tok.substring(0, 4) == word.substring(0, 4)
            }
            return false
        }

        val titleWords = t.split(Regex("\\s+"))
        val artistWords = a.split(Regex("\\s+"))

        val anyHits = tokens.count { 
            titleWords.contains(it) || artistWords.contains(it) || 
            titleWords.any { w -> isFuzzyMatch(it, w) } || 
            artistWords.any { w -> isFuzzyMatch(it, w) } 
        }

        val exactMatchFallback = t.contains(query) || a.contains(query)
        if (anyHits < (tokens.size + 1) / 2 && !exactMatchFallback) {
            return 0
        }

        for (tok in tokens) {
            if (titleWords.contains(tok)) {
                score += 100
            } else if (titleWords.any { isFuzzyMatch(tok, it) }) {
                score += 40
            } else if (t.contains(tok)) {
                score += 10
            }
        }

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

        view?.requestLayout()
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
        
        if (t == query) {
            score += 10000 
        } else if (t.startsWith("$query ")) {
            score += 8000
        } else if (t.startsWith(query)) {
            score += 6000
        } else if (t.contains(query)) {
            score += 4000
        }

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
        
        if (titleHits >= tokens.size && tokens.isNotEmpty()) {
            score += 2000
        } else if (titleHits + subtitleHits >= tokens.size && tokens.isNotEmpty()) {
            score += 1000
        }

        if (s == query) score += 200
        else if (s.contains(query)) score += 100
        
        val isDownloaded = track.videoId.isNotEmpty() && OfflineAudioStore.hasValidatedOfflineAudio(requireContext(), track.videoId, null)
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
        
        val isDownloaded = track.videoId.isNotEmpty() && OfflineAudioStore.hasValidatedOfflineAudio(requireContext(), track.videoId, null)
        if (isDownloaded) {
            ivFeaturedOfflineIndicator.visibility = View.VISIBLE
            ivFeaturedOfflineIndicator.setImageResource(R.drawable.ic_check_small)
            ivFeaturedOfflineIndicator.setBackgroundResource(R.drawable.bg_offline_state_filled_primary)
            ivFeaturedOfflineIndicator.setColorFilter(ContextCompat.getColor(requireContext(), R.color.surface_dark))
        } else {
            ivFeaturedOfflineIndicator.visibility = View.GONE
        }
        
        loadArtworkInto(ivFeaturedThumb, track.thumbnailUrl, track.videoId)
        llFeaturedResult.setOnClickListener { onTrackClicked(track) }
    }

    private fun onTrackClicked(track: YouTubeMusicService.TrackResult) {
        if ("playlist".equals(track.resultType, ignoreCase = true)) {
            val intent = Intent(requireContext(), MainActivity::class.java).apply {
                action = MainActivity.ACTION_PLAY_FROM_SEARCH
                putExtra(EXTRA_RESULT_TYPE, track.resultType ?: "")
                putExtra(EXTRA_RESULT_CONTENT_ID, track.contentId ?: "")
                putExtra(EXTRA_RESULT_TITLE, track.title ?: "")
                putExtra(EXTRA_RESULT_SUBTITLE, track.subtitle ?: "")
                putExtra(EXTRA_RESULT_THUMBNAIL, track.thumbnailUrl ?: "")
            }
            if (requireActivity() is MainActivity) {
                (requireActivity() as MainActivity).handlePlayFromSearchIntent(intent)
            }
            return
        }

        val allResults = listOfNotNull(featuredTrack) + tracks
        val videoResults = allResults.filter { it.videoId?.isNotEmpty() == true }
        
        val tracksArray = JSONArray()
        videoResults.forEach {
            val obj = JSONObject()
            obj.put("resultType", it.resultType)
            obj.put("videoId", it.videoId)
            obj.put("contentId", it.contentId)
            obj.put("title", it.title)
            obj.put("subtitle", it.subtitle)
            obj.put("thumbnailUrl", it.thumbnailUrl)
            tracksArray.put(obj)
        }

        val playbackIntent = Intent(requireContext(), MainActivity::class.java).apply {
            action = MainActivity.ACTION_PLAY_FROM_SEARCH
            putExtra(EXTRA_RESULT_TYPE, track.resultType ?: "")
            putExtra(EXTRA_RESULT_VIDEO_ID, track.videoId ?: "")
            putExtra(EXTRA_RESULT_CONTENT_ID, track.contentId ?: "")
            putExtra(EXTRA_RESULT_TITLE, track.title ?: "")
            putExtra(EXTRA_RESULT_SUBTITLE, track.subtitle ?: "")
            putExtra(EXTRA_RESULT_THUMBNAIL, track.thumbnailUrl ?: "")
            putExtra(EXTRA_RESULT_TRACKS_JSON, tracksArray.toString())
        }
        
        if (requireActivity() is MainActivity) {
            (requireActivity() as MainActivity).handlePlayFromSearchIntent(playbackIntent)
        }
    }

    private fun showTrackOptionsBottomSheet(track: YouTubeMusicService.TrackResult, anchor: View) {
        anchor.performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK)
        
        val dialog = com.google.android.material.bottomsheet.BottomSheetDialog(requireContext())
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

        btnFavorite.visibility = View.VISIBLE
        val ivFav = btnFavorite.findViewById<ImageView>(R.id.ivBsFavorite)
        val tvFav = btnFavorite.findViewById<TextView>(R.id.tvBsFavorite)
        ivFav.setImageResource(R.drawable.ic_favorite_star)
        tvFav.text = "Agregar a Favoritos"
        btnFavorite.setOnClickListener {
            addTrackToFavorites(track)
            dialog.dismiss()
        }

        val parent = view.parent as? View
        parent?.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        
        dialog.show()
    }

    private fun addTrackToFavorites(track: YouTubeMusicService.TrackResult) {
        if (track.videoId.isNullOrEmpty()) return
        FavoritesPlaylistStore.upsertFavorite(
            requireContext(),
            track.videoId,
            track.title ?: "Tema",
            track.subtitle ?: "Artista",
            "--:--",
            track.thumbnailUrl ?: ""
        )
    }

    private fun addToQueue(track: YouTubeMusicService.TrackResult, playNext: Boolean) {
        val intent = Intent(requireContext(), MainActivity::class.java).apply {
            action = if (playNext) MainActivity.ACTION_PLAY_NEXT else MainActivity.ACTION_ADD_TO_QUEUE
            putExtra(EXTRA_RESULT_TYPE, track.resultType ?: "")
            putExtra(EXTRA_RESULT_VIDEO_ID, track.videoId ?: "")
            putExtra(EXTRA_RESULT_TITLE, track.title ?: "")
            putExtra(EXTRA_RESULT_SUBTITLE, track.subtitle ?: "")
            putExtra(EXTRA_RESULT_THUMBNAIL, track.thumbnailUrl ?: "")
        }
        if (requireActivity() is MainActivity) {
            (requireActivity() as MainActivity).handlePlayFromSearchIntent(intent)
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
        if (loading) {
            showModuleLoadingOverlay()
            tvSearchState.visibility = View.GONE
            llSearchState.visibility = View.VISIBLE
        } else {
            if (msg.isNotEmpty()) {
                tvSearchState.text = msg
                tvSearchState.visibility = View.VISIBLE
                llSearchState.visibility = View.VISIBLE
            } else {
                tvSearchState.visibility = View.GONE
                llSearchState.visibility = View.GONE
            }
        }
    }

    private fun restoreRecentSearchQueries() {
        val prefs = requireContext().getSharedPreferences(PREFS_STREAMING_CACHE, Context.MODE_PRIVATE)
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
        requireContext().getSharedPreferences(PREFS_STREAMING_CACHE, Context.MODE_PRIVATE).edit().putString(PREF_RECENT_SEARCH_QUERIES, array.toString()).apply()
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

    private fun loadArtworkInto(target: ImageView, url: String?, videoId: String? = null) {
        var finalUrl = url?.trim()
        
        if (finalUrl.isNullOrEmpty() && !videoId.isNullOrEmpty() && OfflineAudioStore.hasOfflineAudio(requireContext(), videoId)) {
            finalUrl = OfflineAudioStore.getThumbnailUri(requireContext(), videoId)?.toString()
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
        val cm = requireContext().getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) || caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
    }

    private fun hideKeyboard() {
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(view?.windowToken, 0)
    }

    private fun showKeyboard() {
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.showSoftInput(etSearchQuery, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun showModuleLoadingOverlay() { moduleLoadingOverlay.visibility = View.VISIBLE }
    private fun revealModuleContent() { moduleLoadingOverlay.visibility = View.GONE }

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
            
            if (item.videoId?.isNotEmpty() == true && OfflineAudioStore.hasOfflineAudio(requireContext(), item.videoId)) {
                holder.offlineIndicator.visibility = View.VISIBLE
                holder.offlineIndicator.setImageResource(R.drawable.ic_check_small)
                holder.offlineIndicator.setBackgroundResource(R.drawable.bg_offline_state_filled_primary)
                holder.offlineIndicator.setColorFilter(ContextCompat.getColor(requireContext(), R.color.surface_dark))
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

    override fun onResume() {
        super.onResume()
        startMiniProgressTicker()
        updateMiniPlayerUi()
    }

    override fun onPause() {
        stopMiniProgressTicker()
        super.onPause()
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (hidden) {
            stopMiniProgressTicker()
        } else {
            if (llMiniPlayer.visibility != View.VISIBLE) {
                val distance = if (llMiniPlayer.height > 0) llMiniPlayer.height.toFloat() else 300f
                llMiniPlayer.translationY = distance
                llMiniPlayer.visibility = View.VISIBLE
                llMiniPlayer.animate().cancel()
                llMiniPlayer.animate().translationY(0f).setDuration(280).start()
            }
            startMiniProgressTicker()
            updateMiniPlayerUi()
        }
    }

    private fun startMiniProgressTicker() {
        if (miniProgressTicker == null) {
            miniProgressTicker = object : Runnable {
                override fun run() {
                    if (!isAdded || isHidden) return
                    val player = (requireActivity() as? MainActivity)?.findSongPlayerFragment()
                    if (player == null || !player.isVisible) {
                        updateMiniPlayerUi()
                    }
                    miniProgressHandler.postDelayed(this, 200L)
                }
            }
        }
        miniProgressHandler.removeCallbacks(miniProgressTicker!!)
        miniProgressHandler.post(miniProgressTicker!!)
    }

    private fun stopMiniProgressTicker() {
        miniProgressTicker?.let { miniProgressHandler.removeCallbacks(it) }
    }

    private fun updateMiniPlayerUi() {
        if (!isAdded || view == null) return
        
        val mainActivity = requireActivity() as? MainActivity ?: return

        if (mainActivity.isSongPlayerVisible()) {
            llMiniPlayer.visibility = View.GONE
            return
        }
        val songPlayer = mainActivity.findSongPlayerFragment()
        val playerAttached = songPlayer != null && songPlayer.isAdded
        
        val snapshot = PlaybackHistoryStore.load(requireContext())
        val snapshotTrack = snapshot.currentTrack()
        
        var currentSeconds = 0
        var totalSeconds = 1
        
        if (playerAttached) {
            miniPlaying = songPlayer.externalIsPlaying()
            currentSeconds = songPlayer.externalGetCurrentSeconds()
            totalSeconds = Math.max(1, songPlayer.externalGetTotalSeconds())
        } else {
            miniPlaying = snapshot.isPlaying
            currentSeconds = 0
            totalSeconds = 1
        }
        
        // Update progress bar
        val progress = (currentSeconds.toFloat() / totalSeconds.toFloat() * 1000f).toInt()
        sbMiniPlayerProgress.progress = progress.coerceIn(0, 1000)
        
        if (snapshotTrack == null && !playerAttached) {
            llMiniPlayer.visibility = View.GONE
            miniPlaying = false
            return
        }

        if (llMiniPlayer.visibility != View.VISIBLE) {
            // Se muestra de inmediato para evitar la sensación de "duplicación" o retraso
            // al entrar desde otra pantalla donde ya era visible
            llMiniPlayer.visibility = View.VISIBLE
            llMiniPlayer.translationY = 0f 
        }

        val displayTitle: String
        val displaySubtitle: String
        val displayImageUrl: String

        if (playerAttached) {
            displayTitle = songPlayer!!.externalGetCurrentTitle() ?: "Reproduciendo"
            displaySubtitle = songPlayer.externalGetCurrentArtist() ?: ""
            displayImageUrl = (songPlayer.externalGetCurrentImageUrl() ?: "").trim()
        } else if (snapshotTrack != null) {
            displayTitle = if (snapshotTrack.title.isEmpty()) "Última reproducción" else snapshotTrack.title
            displaySubtitle = snapshotTrack.artist
            displayImageUrl = snapshotTrack.imageUrl.trim()
        } else {
            displayTitle = "Selecciona una canción"
            displaySubtitle = ""
            displayImageUrl = ""
            miniPlaying = false
        }

        tvMiniPlayerTitle.text = displayTitle
        tvMiniPlayerSubtitle.text = displaySubtitle
        btnMiniPlayPause.setImageResource(if (miniPlaying) R.drawable.ic_mini_pause else R.drawable.ic_mini_play)

        if (displayImageUrl != lastMiniArtUrl) {
            lastMiniArtUrl = displayImageUrl
            if (displayImageUrl.isNotEmpty()) {
                loadArtworkInto(ivMiniPlayerArt, displayImageUrl, null)
            } else {
                ivMiniPlayerArt.setImageResource(R.drawable.ic_music)
            }
        }
    }

    private fun toggleMiniPlayback() {
        val mainActivity = requireActivity() as? MainActivity ?: return
        val player = mainActivity.findSongPlayerFragment()
        if (player != null && player.isAdded) {
            player.externalTogglePlayback()
            updateMiniPlayerUi()
        } else {
            mainActivity.sendBroadcast(Intent(MainActivity.ACTION_TOGGLE_CURRENT_PLAYBACK))
            miniPlaying = !miniPlaying
            btnMiniPlayPause.setImageResource(if (miniPlaying) R.drawable.ic_mini_pause else R.drawable.ic_mini_play)
        }
    }

    private fun openPlayerFromMiniBar() {
        if (requireActivity() is MainActivity) {
            (requireActivity() as MainActivity).openSongPlayer()
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
