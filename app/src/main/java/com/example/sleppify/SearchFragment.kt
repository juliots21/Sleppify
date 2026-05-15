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
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import android.graphics.drawable.Drawable
import com.example.sleppify.utils.YouTubeCropTransformation
import com.google.android.material.imageview.ShapeableImageView
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import android.graphics.Typeface
import android.view.animation.PathInterpolator
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
        private const val PREF_RECENT_SEARCH_DATA = "stream_recent_search_data"
        private const val SEARCH_PAGE_SIZE = 20
        private const val SEARCH_SUGGESTION_RECENT_LIMIT = 6
        private const val SEARCH_SCROLL_LOAD_MORE_THRESHOLD = 4

        private val DEFAULT_SEARCH_SUGGESTIONS = emptyArray<String>()

        private val SHARED_YT_CROP = YouTubeCropTransformation()

        fun newInstance() = SearchFragment()
    }

    data class RecentSearch(
        val query: String,
        val videoId: String = "",
        val title: String = "",
        val thumbnail: String = "",
        val artist: String = ""
    )

    private val youTubeMusicService = YouTubeMusicService()
    private val normalizedFilterCache = mutableMapOf<String, String>()
    private val allTracks = mutableListOf<YouTubeMusicService.TrackResult>()
    private val tracks = mutableListOf<YouTubeMusicService.TrackResult>()
    private val recentSearchData = mutableListOf<RecentSearch>()
    private val localTrackIndex = mutableListOf<FavoritesPlaylistStore.FavoriteTrack>()

    private lateinit var etSearchQuery: TextInputEditText
    private lateinit var ivSearchClear: ImageView
    private lateinit var ivSearchBack: ImageView
    private lateinit var rvSearchResults: RecyclerView
    private lateinit var tvSearchState: TextView
    private lateinit var llSearchState: View
    private lateinit var nsvSearchContent: View
    private lateinit var rvSearchSuggestions: RecyclerView
    private lateinit var llSearchSuggestionsContainer: View
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

    private var autoPlayOnFirstResult = false
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
        setupBackButton()

        restoreRecentSearchQueries()
        loadRecentSearchesFromFirebase()
        refreshSearchSuggestions("")

        setupBackNavigation()

        // Overlay starts visible from XML; onResume will hide it after layout is complete.
    }

    private fun initViews(root: View) {
        etSearchQuery = root.findViewById(R.id.etSearchQuery)
        ivSearchClear = root.findViewById(R.id.ivSearchClear)
        ivSearchBack = root.findViewById(R.id.ivSearchBack)
        rvSearchResults = root.findViewById(R.id.rvSearchResults)
        tvSearchState = root.findViewById(R.id.tvSearchState)
        llSearchState = root.findViewById(R.id.llSearchState)
        nsvSearchContent = root.findViewById(R.id.nsvSearchContent)
        rvSearchSuggestions = root.findViewById(R.id.rvSearchSuggestions)
        llSearchSuggestionsContainer = root.findViewById(R.id.llSearchSuggestionsContainer)
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
            mainActivity.findViewById<View>(R.id.btnProfilePhoto)?.setOnClickListener {
                mainActivity.enterSettings()
            }
            mainActivity.findViewById<TextView>(R.id.tvModuleTitle)?.setOnClickListener {
                mainActivity.closeSearchFragment()
            }
        }

        root.findViewById<View>(R.id.btnFeaturedShare).setOnClickListener {
            featuredTrack?.let { track ->
                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, "https://youtu.be/${track.videoId}")
                }
                startActivity(Intent.createChooser(sendIntent, "Compartir"))
            }
        }
        
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            root.setPadding(systemBars.left, 0, systemBars.right, 0)
            insets
        }

        root.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                hideKeyboard()
            }
            false
        }
    }

    private fun setupBackButton() {
        ivSearchBack.setOnClickListener {
            if (requireActivity() is MainActivity) {
                (requireActivity() as MainActivity).closeSearchFragment()
            }
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


    private fun loadLocalTrackIndex() {
        lifecycleScope.launch(Dispatchers.IO) {
            val ctx = context ?: return@launch
            val seen = mutableSetOf<String>()
            val result = mutableListOf<FavoritesPlaylistStore.FavoriteTrack>()

            fun tryAdd(videoId: String, title: String, artist: String, duration: String, imageUrl: String) {
                if (videoId.isEmpty() || videoId in seen) return
                seen.add(videoId)
                result.add(FavoritesPlaylistStore.FavoriteTrack(videoId, title, artist, duration, imageUrl))
            }

            // 1. Favorites
            try { FavoritesPlaylistStore.loadFavorites(ctx).forEach { tryAdd(it.videoId, it.title, it.artist, it.duration, it.imageUrl) } } catch (_: Exception) {}

            // 2. Custom playlists
            try {
                for (name in CustomPlaylistsStore.getAllPlaylistNames(ctx)) {
                    CustomPlaylistsStore.getTracksFromPlaylist(ctx, name).forEach { tryAdd(it.videoId, it.title, it.artist, it.duration, it.imageUrl) }
                }
            } catch (_: Exception) {}

            // 3. All cached YT Music playlists (playlist_tracks_data_*)
            try {
                val cache = ctx.getSharedPreferences("streaming_cache", Context.MODE_PRIVATE)
                for ((key, value) in cache.all) {
                    if (key.startsWith("playlist_tracks_data_") && value is String) {
                        val arr = org.json.JSONArray(value)
                        for (i in 0 until arr.length()) {
                            val obj = arr.getJSONObject(i)
                            tryAdd(obj.optString("videoId"), obj.optString("title"), obj.optString("artist"), obj.optString("duration", ""), obj.optString("imageUrl"))
                        }
                    }
                }
            } catch (_: Exception) {}

            // 4. Playback history
            try {
                PlaybackHistoryStore.load(ctx).queue.forEach { tryAdd(it.videoId, it.title, it.artist, it.duration, it.imageUrl) }
            } catch (_: Exception) {}

            launch(Dispatchers.Main) {
                if (!isAdded) return@launch
                localTrackIndex.clear()
                localTrackIndex.addAll(result)
            }
        }
    }


    private fun showSuggestionsMode() {
        val query = etSearchQuery.text?.toString()?.trim() ?: ""
        refreshSearchSuggestions(query)
        llSearchSuggestionsContainer.visibility = View.VISIBLE
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

        refreshSearchSuggestions(query)
        rvSearchSuggestions.visibility = View.GONE
        llSearchSuggestionsContainer.visibility = View.GONE
        nsvSearchContent.visibility = View.VISIBLE
        rememberRecentSearchQuery(query)

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

        if (!isNetworkAvailable()) {
            if (!append) setSearchLoadingState(true, "Buscando en tu biblioteca...")
            return
        }

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
                
                // Fallback: try Innertube (no API key needed)
                if (!append && allTracks.isEmpty()) {
                    youTubeMusicService.searchTracksViaInnertube(query, SEARCH_PAGE_SIZE, object : YouTubeMusicService.SearchCallback {
                        override fun onSuccess(tracks: List<YouTubeMusicService.TrackResult>) {
                            if (activity == null || !isAdded || requestId != latestSearchRequestId) return
                            appendUniqueTracks(tracks)
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
                        override fun onError(error: String) {
                            if (activity == null || !isAdded || requestId != latestSearchRequestId) return
                            setSearchLoadingState(false, "Error: $error")
                            revealModuleContent()
                        }
                    })
                } else {
                    if (allTracks.isEmpty()) {
                        setSearchLoadingState(false, "Error: $error")
                    } else {
                        setSearchLoadingState(false, "")
                        if (append) Toast.makeText(requireContext(), "Error al cargar más resultados", Toast.LENGTH_SHORT).show()
                    }
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

        fun tryAdd(track: FavoritesPlaylistStore.FavoriteTrack, sourceBonus: Int = 0) {
            val key = "video|${track.videoId}"
            if (key in seenIds) return
            val score = computeFuzzyScore(track.title, track.artist, normalizedQuery, queryTokens, sourceBonus)
            if (score <= 0) return
            seenIds.add(key)
            scored.add(YouTubeMusicService.TrackResult(
                "video", track.videoId, track.title, track.artist, track.imageUrl
            ) to score)
        }

        // Favorites: highest user signal (matches Spotify "liked songs" priority)
        FavoritesPlaylistStore.loadFavorites(requireContext()).forEach { tryAdd(it, sourceBonus = 500) }

        // Custom playlists: strong user signal
        for (name in CustomPlaylistsStore.getAllPlaylistNames(requireContext())) {
            CustomPlaylistsStore.getTracksFromPlaylist(requireContext(), name).forEach { tryAdd(it, sourceBonus = 300) }
        }

        // Cached online playlists: moderate signal
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
                        tryAdd(track, sourceBonus = 100)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("SearchFragment", "Error searching cached playlists: ${e.message}")
        }

        // Playback history: listening behavior signal
        try {
            val history = PlaybackHistoryStore.load(requireContext())
            history.queue.forEach { q ->
                val track = FavoritesPlaylistStore.FavoriteTrack(
                    q.videoId, q.title, q.artist, q.duration, q.imageUrl
                )
                tryAdd(track, sourceBonus = 200)
            }
        } catch (e: Exception) {
            Log.e("SearchFragment", "Error searching history: ${e.message}")
        }

        scored.sortByDescending { it.second }
        return scored.map { it.first }
    }

    private fun computeFuzzyScore(title: String, artist: String, query: String, tokens: List<String>, sourceBonus: Int = 0): Int {
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

        // Artist match — explicit bonus (Spotify-style: artist relevance matters)
        if (a == query) score += 200
        else if (a.startsWith("$query ")) score += 150
        else if (a.startsWith(query)) score += 120
        else if (a.contains(query)) score += 80

        for (tok in tokens) {
            if (artistWords.contains(tok)) {
                score += 30
            } else if (artistWords.any { isFuzzyMatch(tok, it) }) {
                score += 10
            }
        }

        // Source bonus: favorites > custom playlists > history > cached
        score += sourceBonus
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

    private data class UserSignals(
        val favoriteIds: Set<String>,
        val customPlaylistIds: Set<String>,
        val historyIds: Set<String>
    )

    private fun loadUserSignals(): UserSignals {
        val ctx = context ?: return UserSignals(emptySet(), emptySet(), emptySet())
        val favIds = try {
            FavoritesPlaylistStore.loadFavorites(ctx).map { it.videoId }.toSet()
        } catch (e: Exception) { emptySet() }
        val customIds = try {
            val ids = mutableSetOf<String>()
            for (name in CustomPlaylistsStore.getAllPlaylistNames(ctx)) {
                CustomPlaylistsStore.getTracksFromPlaylist(ctx, name).forEach { ids.add(it.videoId) }
            }
            ids
        } catch (e: Exception) { emptySet() }
        val historyIds = try {
            PlaybackHistoryStore.load(ctx).queue.map { it.videoId }.toSet()
        } catch (e: Exception) { emptySet() }
        return UserSignals(favIds, customIds, historyIds)
    }

    private fun applyActiveFilter(query: String?, forceSort: Boolean = false) {
        val normalizedQuery = query?.trim() ?: ""
        val filtered = allTracks.toMutableList()

        if (forceSort && normalizedQuery.isNotEmpty() && filtered.size > 1) {
            val signals = loadUserSignals()
            sortResults(filtered, normalizedQuery, signals)
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

    private fun sortResults(list: MutableList<YouTubeMusicService.TrackResult>, query: String, signals: UserSignals = UserSignals(emptySet(), emptySet(), emptySet())) {
        val normalized = normalizeForFilter(query)
        val tokens = normalized.split(Regex("\\s+")).filter { it.isNotEmpty() }
        list.sortByDescending { computeScore(it, normalized, tokens, signals) }
    }

    private fun computeScore(track: YouTubeMusicService.TrackResult, query: String, tokens: List<String>, signals: UserSignals = UserSignals(emptySet(), emptySet(), emptySet())): Int {
        val t = normalizeForFilter(track.title)
        val s = normalizeForFilter(track.subtitle)
        var score = 0

        // --- Result type priority (Spotify/YT Music: tracks first, playlists last) ---
        val type = track.resultType?.lowercase() ?: ""
        when {
            type == "track" || type == "video" || type == "song" -> score += 3000
            type == "artist" -> score += 500
            type == "album" -> score += 200
            type == "playlist" -> score -= 2000
        }

        // --- Exact and prefix title matches ---
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

        // --- User signals (Spotify-style: content the user knows floats up) ---
        val vid = track.videoId ?: ""
        if (vid.isNotEmpty()) {
            if (vid in signals.favoriteIds) score += 2000
            if (vid in signals.customPlaylistIds) score += 1500
            if (vid in signals.historyIds) score += 800
        }

        // --- Offline availability boost ---
        val isDownloaded = vid.isNotEmpty() && OfflineAudioStore.hasValidatedOfflineAudio(requireContext(), vid, null)
        if (isDownloaded) score += 5000

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

        // First, try to find the track in a local playlist for full playlist playback
        val videoId = track.videoId ?: ""
        val playlistQueue = buildPlaylistQueueForTrack(videoId)

        val tracksArray: JSONArray
        if (playlistQueue.length() > 0) {
            // Use the playlist queue (contains the full playlist starting from selected track)
            tracksArray = playlistQueue
        } else {
            // Fall back to search results as queue
            val allResults = listOfNotNull(featuredTrack) + tracks
            val videoResults = allResults.filter { it.videoId?.isNotEmpty() == true }
            tracksArray = JSONArray()
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
        
        // Save to recent searches only when user plays a track from search results
        if (activeSearchQuery.isNotEmpty()) {
            rememberRecentSearchQuery(activeSearchQuery, track)
        }

        if (requireActivity() is MainActivity) {
            (requireActivity() as MainActivity).handlePlayFromSearchIntent(playbackIntent)
        }
    }

    private fun playTrackDirectly(track: YouTubeMusicService.TrackResult) {
        val videoId = track.videoId ?: ""
        val tracksArray = buildPlaylistQueueForTrack(videoId)

        // If no playlist was found, fall back to just the single track
        if (tracksArray.length() == 0) {
            val obj = JSONObject()
            obj.put("resultType", track.resultType)
            obj.put("videoId", track.videoId)
            obj.put("contentId", track.contentId)
            obj.put("title", track.title)
            obj.put("subtitle", track.subtitle)
            obj.put("thumbnailUrl", track.thumbnailUrl)
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

    /**
     * Searches through all local playlists to find the best one containing the given videoId.
     * Priority order: Favoritos > Custom playlists > Cached YT playlists > Playback history.
     * Returns the full playlist as a JSONArray with the selected track positioned first,
     * or an empty JSONArray if no playlist contains the track.
     */
    private fun buildPlaylistQueueForTrack(videoId: String): JSONArray {
        if (videoId.isEmpty()) return JSONArray()
        val ctx = context ?: return JSONArray()

        // 1. Check Favoritos
        try {
            val favs = FavoritesPlaylistStore.loadFavorites(ctx)
            if (favs.any { it.videoId == videoId }) {
                return buildQueueFromFavoriteTracks(favs, videoId)
            }
        } catch (_: Exception) {}

        // 2. Check custom playlists
        try {
            for (name in CustomPlaylistsStore.getAllPlaylistNames(ctx)) {
                val playlistTracks = CustomPlaylistsStore.getTracksFromPlaylist(ctx, name)
                if (playlistTracks.any { it.videoId == videoId }) {
                    return buildQueueFromFavoriteTracks(playlistTracks, videoId)
                }
            }
        } catch (_: Exception) {}

        // 3. Check cached YouTube playlists
        try {
            val cache = ctx.getSharedPreferences("streaming_cache", Context.MODE_PRIVATE)
            for ((key, value) in cache.all) {
                if (key.startsWith("playlist_tracks_data_") && value is String) {
                    val arr = org.json.JSONArray(value)
                    var found = false
                    for (i in 0 until arr.length()) {
                        if (arr.getJSONObject(i).optString("videoId") == videoId) {
                            found = true
                            break
                        }
                    }
                    if (found) {
                        return buildQueueFromCachedPlaylist(arr, videoId)
                    }
                }
            }
        } catch (_: Exception) {}

        // 4. Check playback history queue
        try {
            val snapshot = PlaybackHistoryStore.load(ctx)
            if (snapshot.queue.any { it.videoId == videoId }) {
                return buildQueueFromHistoryTracks(snapshot.queue, videoId)
            }
        } catch (_: Exception) {}

        return JSONArray()
    }

    private fun buildQueueFromFavoriteTracks(
        tracks: List<FavoritesPlaylistStore.FavoriteTrack>,
        selectedVideoId: String
    ): JSONArray {
        val result = JSONArray()
        // Place selected track first, then the rest in order
        val selectedIdx = tracks.indexOfFirst { it.videoId == selectedVideoId }
        if (selectedIdx < 0) return result

        val ordered = tracks.subList(selectedIdx, tracks.size) + tracks.subList(0, selectedIdx)
        for (t in ordered) {
            val obj = JSONObject()
            obj.put("resultType", "video")
            obj.put("videoId", t.videoId)
            obj.put("contentId", t.videoId)
            obj.put("title", t.title)
            obj.put("subtitle", t.artist)
            obj.put("thumbnailUrl", t.imageUrl)
            result.put(obj)
        }
        return result
    }

    private fun buildQueueFromCachedPlaylist(arr: org.json.JSONArray, selectedVideoId: String): JSONArray {
        val result = JSONArray()
        var selectedIdx = -1
        val items = mutableListOf<JSONObject>()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            items.add(obj)
            if (obj.optString("videoId") == selectedVideoId && selectedIdx < 0) {
                selectedIdx = i
            }
        }
        if (selectedIdx < 0) return result

        val ordered = items.subList(selectedIdx, items.size) + items.subList(0, selectedIdx)
        for (cached in ordered) {
            val obj = JSONObject()
            obj.put("resultType", "video")
            obj.put("videoId", cached.optString("videoId"))
            obj.put("contentId", cached.optString("videoId"))
            obj.put("title", cached.optString("title"))
            obj.put("subtitle", cached.optString("artist"))
            obj.put("thumbnailUrl", cached.optString("imageUrl"))
            result.put(obj)
        }
        return result
    }

    private fun buildQueueFromHistoryTracks(
        tracks: List<PlaybackHistoryStore.QueueTrack>,
        selectedVideoId: String
    ): JSONArray {
        val result = JSONArray()
        val selectedIdx = tracks.indexOfFirst { it.videoId == selectedVideoId }
        if (selectedIdx < 0) return result

        val ordered = tracks.subList(selectedIdx, tracks.size) + tracks.subList(0, selectedIdx)
        for (t in ordered) {
            val obj = JSONObject()
            obj.put("resultType", "video")
            obj.put("videoId", t.videoId)
            obj.put("contentId", t.videoId)
            obj.put("title", t.title)
            obj.put("subtitle", t.artist)
            obj.put("thumbnailUrl", t.imageUrl)
            result.put(obj)
        }
        return result
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
        ivFav.setImageResource(R.drawable.ic_stream_queue_add)
        tvFav.text = "Añadir a playlist"
        btnFavorite.setOnClickListener {
            dialog.dismiss()
            showSaveToPlaylistSheet(track)
        }

        val parent = view.parent as? View
        parent?.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        
        dialog.show()
    }

    private fun showSaveToPlaylistSheet(track: YouTubeMusicService.TrackResult) {
        if (!isAdded()) return
        val ctx = requireContext()
        val saveDialog = com.google.android.material.bottomsheet.BottomSheetDialog(ctx)
        val sheet = layoutInflater.inflate(R.layout.bottom_sheet_save_to_playlist, null)
        saveDialog.setContentView(sheet)

        val parent = sheet.parent as? View
        parent?.setBackgroundColor(android.graphics.Color.TRANSPARENT)

        sheet.findViewById<ImageView>(R.id.ivSaveClose).setOnClickListener { saveDialog.dismiss() }

        val llList = sheet.findViewById<android.widget.LinearLayout>(R.id.llSavePlaylistList)
        llList.removeAllViews()

        val entries = mutableListOf<Triple<String, String, String>>()
        val favs = FavoritesPlaylistStore.loadFavorites(ctx)
        val favThumb = favs.firstOrNull { !it.imageUrl.isNullOrEmpty() }?.imageUrl ?: ""
        entries.add(Triple(FavoritesPlaylistStore.PLAYLIST_ID, FavoritesPlaylistStore.PLAYLIST_TITLE, favThumb))

        val customNames = CustomPlaylistsStore.getAllPlaylistNames(ctx)
        for (name in customNames) {
            val customTracks = CustomPlaylistsStore.getTracksFromPlaylist(ctx, name)
            val thumb = customTracks.firstOrNull { !it.imageUrl.isNullOrEmpty() }?.imageUrl ?: ""
            entries.add(Triple(CustomPlaylistsStore.CUSTOM_PLAYLIST_PREFIX + name, name, thumb))
        }

        for ((playlistKey, playlistName, thumbUrl) in entries) {
            val row = layoutInflater.inflate(R.layout.item_save_playlist_row, llList, false)
            val ivThumb = row.findViewById<ImageView>(R.id.ivSavePlaylistThumb)
            val tvName = row.findViewById<TextView>(R.id.tvSavePlaylistName)
            val tvCount = row.findViewById<TextView>(R.id.tvSavePlaylistCount)
            tvName.text = playlistName
            val count = if (playlistKey == FavoritesPlaylistStore.PLAYLIST_ID)
                favs.size else CustomPlaylistsStore.getTracksFromPlaylist(ctx, playlistName).size
            tvCount.text = "$count pistas"
            if (thumbUrl.isNotEmpty()) {
                loadArtworkInto(ivThumb, thumbUrl)
            }
            row.setOnClickListener {
                saveDialog.dismiss()
                addTrackToPlaylistByKey(playlistKey, track)
                android.widget.Toast.makeText(ctx, "Guardado en $playlistName", android.widget.Toast.LENGTH_SHORT).show()
            }
            llList.addView(row)
        }

        saveDialog.show()
    }

    private fun addTrackToPlaylistByKey(playlistKey: String, track: YouTubeMusicService.TrackResult) {
        if (track.videoId.isNullOrEmpty()) return
        val ctx = requireContext()
        val title = track.title?.takeIf { it.isNotEmpty() } ?: "Tema"
        val artist = track.subtitle ?: ""
        val imageUrl = track.thumbnailUrl ?: ""
        if (playlistKey == FavoritesPlaylistStore.PLAYLIST_ID) {
            FavoritesPlaylistStore.upsertFavorite(ctx, track.videoId, title, artist, "--:--", imageUrl)
        } else if (playlistKey.startsWith(CustomPlaylistsStore.CUSTOM_PLAYLIST_PREFIX)) {
            val name = playlistKey.removePrefix(CustomPlaylistsStore.CUSTOM_PLAYLIST_PREFIX)
            CustomPlaylistsStore.addTrackToPlaylist(ctx, name, track.videoId, title, artist, "--:--", imageUrl)
        }
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
            putExtra(Intent.EXTRA_TEXT, "https://youtu.be/${track.videoId}")
        }
        startActivity(Intent.createChooser(shareIntent, "Compartir"))
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
        // Try to load extended data first
        val dataJson = prefs.getString(PREF_RECENT_SEARCH_DATA, "[]")
        recentSearchData.clear()
        try {
            val array = JSONArray(dataJson)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                recentSearchData.add(RecentSearch(
                    query = obj.optString("query", ""),
                    videoId = obj.optString("videoId", ""),
                    title = obj.optString("title", ""),
                    thumbnail = obj.optString("thumbnail", ""),
                    artist = obj.optString("artist", "")
                ))
            }
        } catch (e: Exception) {
            // Fallback: try old format
            val oldJson = prefs.getString(PREF_RECENT_SEARCH_QUERIES, "[]")
            try {
                val array = JSONArray(oldJson)
                for (i in 0 until array.length()) {
                    recentSearchData.add(RecentSearch(query = array.getString(i)))
                }
            } catch (e2: Exception) {}
        }
        // Back-fill missing artist for entries that have a videoId
        var dirty = false
        for (i in recentSearchData.indices) {
            val entry = recentSearchData[i]
            if (entry.videoId.isNotEmpty() && entry.artist.isEmpty()) {
                val resolved = resolveArtistForVideoId(entry.videoId)
                if (resolved.isNotEmpty()) {
                    recentSearchData[i] = entry.copy(artist = resolved)
                    dirty = true
                }
            }
        }
        if (dirty) saveRecentSearchQueries()
    }

    private fun rememberRecentSearchQuery(query: String, firstResult: YouTubeMusicService.TrackResult? = null) {
        val norm = query.trim()
        if (norm.isEmpty()) return
        
        val newSearch = RecentSearch(
            query = norm,
            videoId = firstResult?.videoId ?: "",
            title = firstResult?.title ?: "",
            thumbnail = firstResult?.thumbnailUrl ?: "",
            artist = firstResult?.subtitle ?: ""
        )
        
        recentSearchData.run {
            removeAll { it.query == norm }
            add(0, newSearch)
            if (size > SEARCH_SUGGESTION_RECENT_LIMIT) removeAt(size - 1)
        }
        saveRecentSearchQueries()
    }

    private fun saveRecentSearchQueries() {
        val array = JSONArray()
        recentSearchData.forEach { search ->
            val obj = JSONObject()
            obj.put("query", search.query)
            obj.put("videoId", search.videoId)
            obj.put("title", search.title)
            obj.put("thumbnail", search.thumbnail)
            obj.put("artist", search.artist)
            array.put(obj)
        }
        requireContext().getSharedPreferences(PREFS_STREAMING_CACHE, Context.MODE_PRIVATE).edit()
            .putString(PREF_RECENT_SEARCH_DATA, array.toString())
            .apply()
        saveRecentSearchesToFirebase()
    }

    private fun saveRecentSearchesToFirebase() {
        val ctx = context ?: return
        if (!AuthManager.getInstance(ctx).isSignedIn()) return
        val uid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: return
        val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
        val searches = recentSearchData.map { search ->
            hashMapOf(
                "query" to search.query,
                "videoId" to search.videoId,
                "title" to search.title,
                "thumbnail" to search.thumbnail,
                "artist" to search.artist
            )
        }
        db.collection("users").document(uid)
            .collection("sleppify").document("recent_searches")
            .set(hashMapOf("searches" to searches))
            .addOnFailureListener { Log.e("SearchFragment", "Failed to save recent searches to Firebase", it) }
    }

    private fun loadRecentSearchesFromFirebase() {
        val ctx = context ?: return
        if (!AuthManager.getInstance(ctx).isSignedIn()) return
        val uid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: return
        val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
        db.collection("users").document(uid)
            .collection("sleppify").document("recent_searches")
            .get()
            .addOnSuccessListener { doc ->
                if (!isAdded || doc == null || !doc.exists()) return@addOnSuccessListener
                val list = doc.get("searches") as? List<*> ?: return@addOnSuccessListener
                val parsed = list.mapNotNull { item ->
                    val map = item as? Map<*, *> ?: return@mapNotNull null
                    RecentSearch(
                        query = map["query"] as? String ?: return@mapNotNull null,
                        videoId = map["videoId"] as? String ?: "",
                        title = map["title"] as? String ?: "",
                        thumbnail = map["thumbnail"] as? String ?: "",
                        artist = map["artist"] as? String ?: ""
                    )
                }
                if (parsed.isNotEmpty() && recentSearchData.isEmpty()) {
                    recentSearchData.clear()
                    recentSearchData.addAll(parsed)
                    saveRecentSearchQueries()
                    refreshSearchSuggestions(etSearchQuery.text?.toString()?.trim() ?: "")
                }
            }
    }

    private fun showDeleteSearchDialog(query: String) {
        if (!isAdded) return
        com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setTitle("Eliminar búsqueda")
            .setMessage("¿Eliminar \"$query\" de las búsquedas recientes?")
            .setPositiveButton("Sí") { _, _ ->
                recentSearchData.removeAll { it.query == query }
                saveRecentSearchQueries()
                saveRecentSearchesToFirebase()
                refreshSearchSuggestions(etSearchQuery.text?.toString()?.trim() ?: "")
            }
            .setNegativeButton("No", null)
            .show()
    }

    private fun refreshSearchSuggestions(draft: String?) {
        val norm = draft?.trim() ?: ""
        val items = poolSuggestionItems(norm)
        (rvSearchSuggestions.adapter as? SuggestionsAdapter)?.updateItems(items)
        rvSearchSuggestions.visibility = if (items.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun poolSuggestionItems(draft: String): List<SuggestionItem> {
        val normDraft = normalizeForFilter(draft)
        val recentQueries = recentSearchData.map { it.query }

        val matchingRecent = recentSearchData.filter { candidate ->
            if (normDraft.isEmpty()) true
            else normalizeForFilter(candidate.query).let { it.contains(normDraft) || normDraft.contains(it) }
        }.take(SEARCH_SUGGESTION_RECENT_LIMIT)

        val smartSuggestions = buildSmartSuggestions(normDraft, recentQueries)

        val result = mutableListOf<SuggestionItem>()

        // Show recent images carousel only when bar is empty (no query typed)
        if (normDraft.isEmpty()) {
            val itemsWithImages = recentSearchData.filter { it.thumbnail.isNotEmpty() }.take(5)
            if (itemsWithImages.isNotEmpty()) {
                result.add(SuggestionItem.RecentImages(itemsWithImages))
            }
        }

        if (matchingRecent.isNotEmpty()) {
            if (normDraft.isNotEmpty()) result.add(SuggestionItem.Header("Búsquedas recientes"))
            matchingRecent.forEach { result.add(SuggestionItem.Recent(it.query, it.thumbnail)) }
        }

        if (smartSuggestions.isNotEmpty()) {
            result.add(SuggestionItem.Header("Temas relacionados"))
            smartSuggestions.forEach { result.add(SuggestionItem.Suggestion(it)) }
        }

        if (normDraft.length >= 3 && localTrackIndex.isNotEmpty()) {
            val trackMatches = localTrackIndex
                .filter { normalizeForFilter(it.title).contains(normDraft) || normalizeForFilter(it.artist).contains(normDraft) }
                .sortedByDescending {
                    when {
                        normalizeForFilter(it.title).startsWith(normDraft) -> 2
                        normalizeForFilter(it.artist).startsWith(normDraft) -> 1
                        else -> 0
                    }
                }
                .take(5)
            if (trackMatches.isNotEmpty()) {
                result.add(SuggestionItem.Header("En tu biblioteca"))
                trackMatches.forEach { result.add(SuggestionItem.Track(it)) }
            }
        }

        return result
    }

    private fun buildSmartSuggestions(normDraft: String, recentQueries: List<String>): List<String> {
        val ctx = context ?: return emptyList()
        val pool = LinkedHashSet<String>()
        val recentNorm = recentQueries.map { normalizeForFilter(it) }.toSet()

        val snapshot = PlaybackHistoryStore.load(ctx)

        // Add artists and titles from playback queue (current track first, then others)
        val current = snapshot.currentTrack()
        if (current != null) {
            val artist = current.artist.trim()
            val title = current.title.trim()
            if (artist.isNotEmpty()) pool.add(artist)
            if (title.isNotEmpty()) pool.add(title)
        }
        for (track in snapshot.queue) {
            val artist = track.artist.trim()
            val title = track.title.trim()
            if (artist.isNotEmpty()) pool.add(artist)
            if (title.isNotEmpty() && pool.size < 20) pool.add(title)
        }

        return pool
            .filter { candidate ->
                val norm = normalizeForFilter(candidate)
                !recentNorm.contains(norm) && (
                    if (normDraft.isEmpty()) true
                    else norm.contains(normDraft) || normDraft.contains(norm)
                )
            }
            .take(12)
    }

    private fun resolveArtistForVideoId(videoId: String): String {
        val ctx = context ?: return ""
        // 1. Playback history (most likely to have full metadata)
        try {
            val history = PlaybackHistoryStore.load(ctx)
            history.queue.firstOrNull { it.videoId == videoId }?.let {
                if (it.artist.isNotEmpty()) return it.artist
            }
        } catch (_: Exception) {}
        // 2. Favorites
        try {
            FavoritesPlaylistStore.loadFavorites(ctx).firstOrNull { it.videoId == videoId }?.let {
                if (it.artist.isNotEmpty()) return it.artist
            }
        } catch (_: Exception) {}
        // 3. Custom playlists
        try {
            for (name in CustomPlaylistsStore.getAllPlaylistNames(ctx)) {
                CustomPlaylistsStore.getTracksFromPlaylist(ctx, name).firstOrNull { it.videoId == videoId }?.let {
                    if (it.artist.isNotEmpty()) return it.artist
                }
            }
        } catch (_: Exception) {}
        // 4. Cached online playlists
        try {
            val cache = ctx.getSharedPreferences("streaming_cache", Context.MODE_PRIVATE)
            for ((key, value) in cache.all) {
                if (key.startsWith("playlist_tracks_data_") && value is String) {
                    val arr = org.json.JSONArray(value)
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        if (obj.optString("videoId") == videoId) {
                            val artist = obj.optString("artist", "")
                            if (artist.isNotEmpty()) return artist
                        }
                    }
                }
            }
        } catch (_: Exception) {}
        return ""
    }

    private fun loadArtworkInto(target: ImageView, url: String?, videoId: String? = null) {
        var finalUrl = url?.trim()
        
        if (finalUrl.isNullOrEmpty() && !videoId.isNullOrEmpty() && OfflineAudioStore.hasOfflineAudio(requireContext(), videoId)) {
            finalUrl = OfflineAudioStore.getThumbnailUri(requireContext(), videoId)?.toString()
        }

        if (finalUrl.isNullOrEmpty()) {
            target.setImageDrawable(null)
            return
        }

        val safeUrl = if (finalUrl.startsWith("//")) "https:$finalUrl" else finalUrl
        val isLocalUri = safeUrl.startsWith("file://") || safeUrl.startsWith("content://")
        val offlineOnly = !isNetworkAvailable() && !isLocalUri

        val density = target.context.resources.displayMetrics.density
        val params = target.layoutParams
        val rawW = if (target.width > 0) target.width else if (params != null && params.width > 0) params.width else 0
        val rawH = if (target.height > 0) target.height else if (params != null && params.height > 0) params.height else 0
        val side = if (rawW > 0 && rawH > 0) maxOf(rawW, rawH) else Math.round(160 * density)
        val overrideSize = maxOf(side, 320)

        Glide.with(this)
            .load(safeUrl)
            .transform(SHARED_YT_CROP)
            .format(com.bumptech.glide.load.DecodeFormat.PREFER_RGB_565)
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .onlyRetrieveFromCache(offlineOnly)
            .override(overrideSize, overrideSize)
            .transition(com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions.withCrossFade())
            .into(target)
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

    private var overlayShownAtMs = 0L
    private var hasBeenVisible = false

    private fun showModuleLoadingOverlay() {
        moduleLoadingOverlay.alpha = 1f
        moduleLoadingOverlay.visibility = View.VISIBLE
        overlayShownAtMs = System.currentTimeMillis()
    }

    private fun revealModuleContent() {
        moduleLoadingOverlay.animate().cancel()
        moduleLoadingOverlay.animate()
            .alpha(0f)
            .setDuration(200)
            .withEndAction { moduleLoadingOverlay.visibility = View.GONE }
            .start()
    }

    private fun scheduleOverlayRevealAfterDraw() {
        val v = view ?: return
        v.viewTreeObserver.addOnPreDrawListener(object : android.view.ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                v.viewTreeObserver.removeOnPreDrawListener(this)
                val elapsed = System.currentTimeMillis() - overlayShownAtMs
                val minVisibleMs = 180L
                val delay = (minVisibleMs - elapsed).coerceAtLeast(0L)
                v.postDelayed({
                    if (isAdded && !isHidden) revealModuleContent()
                }, delay)
                return true
            }
        })
    }

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
        // Only hide the global header when this fragment is actually visible (not hidden).
        // Hidden fragments still receive onResume() — without this guard, SearchFragment
        // would unconditionally kill the header every time the activity resumes, even when
        // the user is on Biblioteca or Principal.
        if (isHidden) return
        (activity as? MainActivity)?.hideTopAppBarForSearch()
        // Clear input on every entry
        etSearchQuery.setText("")
        showSuggestionsMode()
        loadLocalTrackIndex()
        lastMiniArtUrl = "" // Resetear para forzar recarga de imagen
        startMiniProgressTicker()
        updateMiniPlayerUi()
        // Show overlay only on first creation; skip on re-entry to avoid delay
        if (!hasBeenVisible) {
            showModuleLoadingOverlay()
            scheduleOverlayRevealAfterDraw()
            hasBeenVisible = true
        } else {
            moduleLoadingOverlay.visibility = View.GONE
        }
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
            // Re-enable back pressed callback when fragment becomes visible again
            backPressedCallback?.isEnabled = true
            // Hide global header when fragment becomes visible
            (activity as? MainActivity)?.findViewById<View>(R.id.topAppBar)?.visibility = View.GONE
            // Only reset to suggestions mode if there is no active search (e.g. entering fresh).
            // When returning from the player after a search, preserve results and query.
            if (activeSearchQuery.isEmpty()) {
                etSearchQuery.setText("")
                showSuggestionsMode()
            }
            loadLocalTrackIndex()
            if (!hasBeenVisible) {
                showModuleLoadingOverlay()
                scheduleOverlayRevealAfterDraw()
                hasBeenVisible = true
            } else {
                moduleLoadingOverlay.visibility = View.GONE
            }
            lastMiniArtUrl = "" // Resetear para forzar recarga de imagen
            // Animate mini player slide-up if not already visible and in place
            // (mirrors MusicPlayerFragment / PlaylistDetailFragment onHiddenChanged logic)
            if (llMiniPlayer.visibility == View.VISIBLE && llMiniPlayer.translationY == 0f) {
                // Already visible and in position — no animation needed
            } else {
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
            val distance = if (llMiniPlayer.height > 0) llMiniPlayer.height.toFloat() else 300f
            llMiniPlayer.translationY = distance
            llMiniPlayer.visibility = View.VISIBLE
            llMiniPlayer.animate().cancel()
            llMiniPlayer.animate()
                .translationY(0f)
                .setDuration(250)
                .setInterpolator(PathInterpolator(0.4f, 0f, 0.2f, 1f))
                .withEndAction(null)
                .start()
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
                ivMiniPlayerArt.setImageDrawable(null)
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
        llMiniPlayer.animate().cancel()
        val distance = if (llMiniPlayer.height > 0) llMiniPlayer.height.toFloat() else 300f
        llMiniPlayer.animate()
            .translationY(distance)
            .setDuration(250)
            .setInterpolator(PathInterpolator(0.4f, 0f, 0.2f, 1f))
            .withEndAction {
                llMiniPlayer.visibility = View.GONE
            }
            .start()

        val mainActivity = requireActivity() as? MainActivity ?: return
        val player = mainActivity.findSongPlayerFragment() ?: return
        if (!player.isAdded) return

        player.externalSetReturnTargetTag(MainActivity.TAG_MODULE_SEARCH)
        parentFragmentManager.beginTransaction()
            .setReorderingAllowed(true)
            .show(player)
            .setMaxLifecycle(player, androidx.lifecycle.Lifecycle.State.RESUMED)
            .runOnCommit { player.externalAnimateEnterSlide() }
            .commit()
    }


    sealed class SuggestionItem {
        data class Header(val label: String) : SuggestionItem()
        data class Recent(val query: String, val thumbnail: String = "") : SuggestionItem()
        data class Suggestion(val query: String) : SuggestionItem()
        data class Track(val track: FavoritesPlaylistStore.FavoriteTrack) : SuggestionItem()
        data class RecentImages(val items: List<RecentSearch>) : SuggestionItem()
    }

    private inner class SuggestionsAdapter(val onClick: (String) -> Unit) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        private val data = mutableListOf<SuggestionItem>()

        private val TYPE_HEADER = 0
        private val TYPE_RECENT = 1
        private val TYPE_SUGGESTION = 2
        private val TYPE_TRACK = 3
        private val TYPE_RECENT_IMAGES = 4

        fun updateItems(newList: List<SuggestionItem>) {
            data.clear()
            data.addAll(newList)
            notifyDataSetChanged()
        }

        override fun getItemViewType(position: Int) = when (data[position]) {
            is SuggestionItem.Header -> TYPE_HEADER
            is SuggestionItem.Recent -> TYPE_RECENT
            is SuggestionItem.Suggestion -> TYPE_SUGGESTION
            is SuggestionItem.Track -> TYPE_TRACK
            is SuggestionItem.RecentImages -> TYPE_RECENT_IMAGES
        }

        override fun onCreateViewHolder(p: ViewGroup, t: Int): RecyclerView.ViewHolder {
            val inflater = LayoutInflater.from(p.context)
            return when (t) {
                TYPE_HEADER -> HeaderViewHolder(inflater.inflate(R.layout.item_search_suggestion_header, p, false))
                TYPE_TRACK -> TrackViewHolder(inflater.inflate(R.layout.item_autocomplete_track, p, false))
                TYPE_RECENT_IMAGES -> RecentImagesViewHolder(inflater.inflate(R.layout.item_suggestion_recent_images, p, false))
                else -> RowViewHolder(inflater.inflate(R.layout.item_search_suggestion, p, false))
            }
        }

        override fun onBindViewHolder(h: RecyclerView.ViewHolder, p: Int) {
            when (val item = data[p]) {
                is SuggestionItem.Header -> (h as HeaderViewHolder).label.text = item.label
                is SuggestionItem.Recent -> {
                    (h as RowViewHolder).apply {
                        text.text = item.query
                        if (item.thumbnail.isNotEmpty()) {
                            icon.clearColorFilter()
                            icon.scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
                            icon.setPadding(0, 0, 0, 0)
                            Glide.with(this@SearchFragment)
                                .load(item.thumbnail)
                                .transform(SHARED_YT_CROP)
                                .diskCacheStrategy(DiskCacheStrategy.ALL)
                                .placeholder(android.R.color.darker_gray)
                                .into(icon)
                        } else {
                            icon.setImageResource(R.drawable.ic_time_24)
                            icon.setColorFilter(android.graphics.Color.WHITE)
                            icon.scaleType = android.widget.ImageView.ScaleType.CENTER
                            val pad = (11 * resources.displayMetrics.density).toInt()
                            icon.setPadding(pad, pad, pad, pad)
                        }
                        itemView.setOnClickListener { onClick(item.query) }
                        itemView.setOnLongClickListener { showDeleteSearchDialog(item.query); true }
                    }
                }
                is SuggestionItem.Suggestion -> {
                    (h as RowViewHolder).apply {
                        text.text = item.query
                        icon.setImageResource(R.drawable.ic_search)
                        icon.setColorFilter(android.graphics.Color.WHITE)
                        icon.scaleType = android.widget.ImageView.ScaleType.CENTER
                        val pad = (11 * resources.displayMetrics.density).toInt()
                        icon.setPadding(pad, pad, pad, pad)
                        itemView.setOnClickListener { onClick(item.query) }
                        itemView.setOnLongClickListener { true }
                    }
                }
                is SuggestionItem.Track -> {
                    (h as TrackViewHolder).apply {
                        title.text = item.track.title
                        artist.text = item.track.artist
                        if (item.track.imageUrl.isNotEmpty()) {
                            Glide.with(this@SearchFragment)
                                .load(item.track.imageUrl)
                                .transform(SHARED_YT_CROP)
                                .diskCacheStrategy(DiskCacheStrategy.ALL)
                                .placeholder(android.R.color.darker_gray)
                                .into(thumb)
                            thumb.visibility = View.VISIBLE
                        } else {
                            thumb.visibility = View.GONE
                        }
                        itemView.setOnClickListener {
                            val result = YouTubeMusicService.TrackResult(
                                "video", item.track.videoId, item.track.title, item.track.artist, item.track.imageUrl
                            )
                            playTrackDirectly(result)
                        }
                    }
                }
                is SuggestionItem.RecentImages -> {
                    val vh = h as RecentImagesViewHolder
                    val imgAdapter = vh.rv.adapter as? RecentSearchImageAdapter
                        ?: RecentSearchImageAdapter { recentSearch ->
                            if (recentSearch.videoId.isNotEmpty()) {
                                var artist = recentSearch.artist
                                if (artist.isEmpty()) artist = resolveArtistForVideoId(recentSearch.videoId)
                                val track = YouTubeMusicService.TrackResult(
                                    "video", recentSearch.videoId, recentSearch.title, artist, recentSearch.thumbnail
                                )
                                if (recentSearch.artist.isEmpty() && artist.isNotEmpty()) {
                                    val idx = recentSearchData.indexOfFirst { it.videoId == recentSearch.videoId }
                                    if (idx >= 0) {
                                        recentSearchData[idx] = recentSearch.copy(artist = artist)
                                        saveRecentSearchQueries()
                                    }
                                }
                                playTrackDirectly(track)
                            } else {
                                etSearchQuery.setText(recentSearch.query)
                                etSearchQuery.setSelection(recentSearch.query.length)
                            }
                        }.also {
                            vh.rv.layoutManager = LinearLayoutManager(vh.rv.context, LinearLayoutManager.HORIZONTAL, false)
                            vh.rv.adapter = it
                            vh.rv.itemAnimator = null
                        }
                    imgAdapter.updateItems(item.items)
                }
            }
        }

        override fun getItemCount() = data.size

        inner class HeaderViewHolder(v: View) : RecyclerView.ViewHolder(v) {
            val label: TextView = v.findViewById(R.id.tvSuggestionHeader)
        }

        inner class RowViewHolder(v: View) : RecyclerView.ViewHolder(v) {
            val text: TextView = v.findViewById(R.id.tvSuggestionText)
            val icon: ImageView = v.findViewById(R.id.ivSuggestionIcon)
        }

        inner class TrackViewHolder(v: View) : RecyclerView.ViewHolder(v) {
            val title: TextView = v.findViewById(R.id.tvAutoCompleteTitle)
            val artist: TextView = v.findViewById(R.id.tvAutoCompleteArtist)
            val thumb: ImageView = v.findViewById(R.id.ivAutoCompleteThumb)
        }

        inner class RecentImagesViewHolder(v: View) : RecyclerView.ViewHolder(v) {
            val rv: RecyclerView = v.findViewById(R.id.rvRecentImagesInline)
        }
    }

    private inner class RecentSearchImageAdapter(val onClick: (RecentSearch) -> Unit) : RecyclerView.Adapter<RecentSearchImageAdapter.ViewHolder>() {
        private val data = mutableListOf<RecentSearch>()

        fun updateItems(newList: List<RecentSearch>) {
            data.clear()
            data.addAll(newList)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(p: ViewGroup, t: Int): ViewHolder {
            val view = LayoutInflater.from(p.context).inflate(R.layout.item_recent_search_image, p, false)
            // Calculate width for 2 compact items (~40% of screen each)
            val density = p.context.resources.displayMetrics.density
            val parentWidth = p.measuredWidth.takeIf { it > 0 }
                ?: p.context.resources.displayMetrics.widthPixels
            val itemWidth = ((parentWidth - (32 * density).toInt()) * 0.40f).toInt()
            val marginEnd = (12 * density).toInt()
            view.layoutParams = RecyclerView.LayoutParams(itemWidth, RecyclerView.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, 0, marginEnd, 0)
            }
            // Set image container height for 16:9
            val imageHeight = (itemWidth * 9f / 16f).toInt()
            val container = view.findViewById<View>(R.id.flImageContainer)
            container.layoutParams = (container.layoutParams).also { it.height = imageHeight }
            return ViewHolder(view)
        }

        override fun onBindViewHolder(h: ViewHolder, p: Int) {
            val item = data[p]
            h.title.text = item.title.ifEmpty { item.query }
            
            // Show loading placeholder
            h.loadingPlaceholder.visibility = View.VISIBLE
            h.image.alpha = 0f
            
            if (item.thumbnail.isNotEmpty()) {
                Glide.with(this@SearchFragment)
                    .load(item.thumbnail)
                    .transform(YouTubeCropTransformation())
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .listener(object : RequestListener<Drawable> {
                        override fun onLoadFailed(
                            e: GlideException?,
                            model: Any?,
                            target: Target<Drawable>,
                            isFirstResource: Boolean
                        ): Boolean {
                            h.loadingPlaceholder.visibility = View.GONE
                            return false
                        }

                        override fun onResourceReady(
                            resource: Drawable,
                            model: Any,
                            target: Target<Drawable>?,
                            dataSource: DataSource,
                            isFirstResource: Boolean
                        ): Boolean {
                            h.loadingPlaceholder.animate()
                                .alpha(0f)
                                .setDuration(250)
                                .withEndAction { h.loadingPlaceholder.visibility = View.GONE }
                                .start()
                            h.image.animate()
                                .alpha(1f)
                                .setDuration(300)
                                .start()
                            return false
                        }
                    })
                    .into(h.image)
            } else {
                h.loadingPlaceholder.visibility = View.GONE
            }
            
            h.itemView.setOnClickListener { onClick(item) }
        }

        override fun getItemCount() = data.size

        inner class ViewHolder(v: View) : RecyclerView.ViewHolder(v) {
            val image: ImageView = v.findViewById(R.id.ivRecentSearchImage)
            val loadingPlaceholder: View = v.findViewById(R.id.vLoadingPlaceholder)
            val title: TextView = v.findViewById(R.id.tvRecentSearchTitle)
        }
    }
}
