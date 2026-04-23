package com.example.sleppify

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
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
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
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
    private lateinit var rvSearchSuggestions: RecyclerView
    private lateinit var llFeaturedResult: LinearLayout
    private lateinit var ivFeaturedThumb: ShapeableImageView
    private lateinit var tvFeaturedTitle: TextView
    private lateinit var tvFeaturedSubtitle: TextView
    private lateinit var tvModuleTitle: TextView
    private lateinit var btnSettings: View
    private lateinit var moduleLoadingOverlay: View

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

        overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
    }

    private fun initViews() {
        val root = findViewById<View>(R.id.llSearchRoot)
        val topAppBar = findViewById<View>(R.id.topAppBar)
        tvModuleTitle = findViewById(R.id.tvModuleTitle)
        btnSettings = findViewById(R.id.btnSettings)
        etSearchQuery = findViewById(R.id.etSearchQuery)
        ivSearchClear = findViewById(R.id.ivSearchClear)
        rvSearchResults = findViewById(R.id.rvSearchResults)
        tvSearchState = findViewById(R.id.tvSearchState)
        rvSearchSuggestions = findViewById(R.id.rvSearchSuggestions)
        llFeaturedResult = findViewById(R.id.llFeaturedResult)
        ivFeaturedThumb = findViewById(R.id.ivFeaturedThumb)
        tvFeaturedTitle = findViewById(R.id.tvFeaturedTitle)
        tvFeaturedSubtitle = findViewById(R.id.tvFeaturedSubtitle)
        moduleLoadingOverlay = findViewById(R.id.moduleLoadingOverlay)

        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val extraPadding = (8 * resources.displayMetrics.density).toInt()
            topAppBar.setPadding(topAppBar.paddingLeft, systemBars.top + extraPadding, topAppBar.paddingRight, topAppBar.paddingBottom)
            insets
        }
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

    private fun clearResults() {
        allTracks.clear()
        tracks.clear()
        featuredTrack = null
        llFeaturedResult.visibility = View.GONE
        adapter?.submitResults(emptyList())
        tvSearchState.text = ""
        activeSearchQuery = ""
        rvSearchSuggestions.visibility = View.VISIBLE
        refreshSearchSuggestions("")
    }

    private fun configureStandardHeader() {
        btnSettings.visibility = View.VISIBLE
        (btnSettings as ImageView).setImageResource(R.drawable.ic_settings)
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

    override fun onBackPressed() {
        showModuleLoadingOverlay()
        super.onBackPressed()
    }

    private fun performSearch() {
        if (searching) return
        val query = etSearchQuery.text?.toString()?.trim() ?: ""
        if (query.isNotEmpty()) startPagedSearch(query)
    }

    private fun startPagedSearch(query: String) {
        latestSearchRequestId++
        activeSearchQuery = query
        allTracks.clear()
        tracks.clear()
        featuredTrack = null
        llFeaturedResult.visibility = View.GONE
        adapter?.submitResults(emptyList())
        hasMoreSearchPages = false
        nextSearchPageToken = ""

        rememberRecentSearchQuery(query)
        refreshSearchSuggestions(query)
        rvSearchSuggestions.visibility = View.GONE
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

                    setSearchLoadingState(false, "")
                    revealModuleContent()
                    rvSearchResults.alpha = 0f
                    rvSearchResults.animate().alpha(1f).setDuration(250).start()
                    hideKeyboard()

                    if (!append) allTracks.clear()
                    appendUniqueTracks(offlineResults)

                    // No pagination for offline search
                    nextSearchPageToken = ""
                    hasMoreSearchPages = false
                    applyActiveFilter(query)

                    if (allTracks.isEmpty()) {
                        tvSearchState.text = "No encontré resultados para: $query"
                    }
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
                } else {
                    setSearchLoadingState(false, "")
                    revealModuleContent()
                    rvSearchResults.alpha = 0f
                    rvSearchResults.animate().alpha(1f).setDuration(250).start()
                    hideKeyboard()
                }

                if (!append) allTracks.clear()
                appendUniqueTracks(pageResult.tracks)
                
                nextSearchPageToken = pageResult.nextPageToken
                hasMoreSearchPages = nextSearchPageToken.isNotEmpty()
                applyActiveFilter(query)

                if (allTracks.isEmpty()) {
                    tvSearchState.text = "No encontré resultados para: $query"
                } else if (!append) {
                    // Predictive pre-fetching in IO dispatcher
                    allTracks.firstOrNull()?.videoId?.let { id ->
                        lifecycleScope.launch(Dispatchers.IO) {
                            Log.d("SearchActivity", "Predictive pre-fetch: Resolviendo stream...")
                            YouTubeStreamResolver.resolveStreamUrl(id)
                        }
                    }
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
        val results = mutableListOf<YouTubeMusicService.TrackResult>()
        val seenIds = mutableSetOf<String>()

        // Search in favorites
        val favorites = FavoritesPlaylistStore.loadFavorites(this)
        for (track in favorites) {
            if (matchesQuery(track.title, track.artist, normalizedQuery)) {
                val key = "video|${track.videoId}"
                if (key !in seenIds) {
                    seenIds.add(key)
                    results.add(YouTubeMusicService.TrackResult(
                        "video",
                        track.videoId,
                        track.title,
                        track.artist,
                        track.imageUrl
                    ))
                }
            }
        }

        // Search in custom playlists
        val playlistNames = CustomPlaylistsStore.getAllPlaylistNames(this)
        for (playlistName in playlistNames) {
            val tracks = CustomPlaylistsStore.getTracksFromPlaylist(this, playlistName)
            for (track in tracks) {
                if (matchesQuery(track.title, track.artist, normalizedQuery)) {
                    val key = "video|${track.videoId}"
                    if (key !in seenIds) {
                        seenIds.add(key)
                        results.add(YouTubeMusicService.TrackResult(
                            "video",
                            track.videoId,
                            track.title,
                            track.artist,
                            track.imageUrl
                        ))
                    }
                }
            }
        }

        return results
    }

    private fun matchesQuery(title: String, artist: String, query: String): Boolean {
        if (query.isEmpty()) return true
        val normalizedTitle = normalizeForFilter(title)
        val normalizedArtist = normalizeForFilter(artist)
        return normalizedTitle.contains(query) || normalizedArtist.contains(query)
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

    private fun applyActiveFilter(query: String?) {
        val normalizedQuery = query?.trim() ?: ""
        val filtered = allTracks.toMutableList()

        if (normalizedQuery.isNotEmpty() && filtered.size > 1) {
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
        if (t == query) score += 800
        else if (t.startsWith("$query ")) score += 760
        else if (t.startsWith(query)) score += 560
        else if (t.contains(query)) score += 340
        
        if (s == query) score += 220
        else if (s.startsWith(query)) score += 180
        else if (s.contains(query)) score += 120
        
        tokens.forEach { 
            if (t.contains(it)) score += 60
            if (s.contains(it)) score += 30
        }
        return score
    }

    private fun bindFeaturedTrack(track: YouTubeMusicService.TrackResult) {
        tvFeaturedTitle.text = track.title
        val type = searchTypeLabel(track)
        tvFeaturedSubtitle.text = if (track.subtitle.isNullOrEmpty()) type else "$type • ${track.subtitle}"
        loadArtworkInto(ivFeaturedThumb, track.thumbnailUrl)
        llFeaturedResult.setOnClickListener { onTrackClicked(track) }
        findViewById<View>(R.id.btnFeaturedPlay).setOnClickListener { onTrackClicked(track) }
    }

    private fun onTrackClicked(track: YouTubeMusicService.TrackResult) {
        showModuleLoadingOverlay()
        val queueJson = JSONArray().apply {
            (listOfNotNull(featuredTrack) + tracks).forEach { t ->
                put(JSONObject().apply {
                    put("videoId", t.videoId ?: "")
                    put("title", t.title ?: "")
                    put("subtitle", t.subtitle ?: "")
                    put("thumbnailUrl", t.thumbnailUrl ?: "")
                    put("resultType", t.resultType ?: "")
                    put("contentId", t.contentId ?: "")
                })
            }
        }

        Intent(this, MainActivity::class.java).apply {
            action = MainActivity.ACTION_PLAY_FROM_SEARCH
            flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_RESULT_TYPE, track.resultType ?: "")
            putExtra(EXTRA_RESULT_VIDEO_ID, track.videoId ?: "")
            putExtra(EXTRA_RESULT_CONTENT_ID, track.contentId ?: "")
            putExtra(EXTRA_RESULT_TITLE, track.title ?: "")
            putExtra(EXTRA_RESULT_SUBTITLE, track.subtitle ?: "")
            putExtra(EXTRA_RESULT_THUMBNAIL, track.thumbnailUrl ?: "")
            putExtra(EXTRA_RESULT_TRACKS_JSON, queueJson.toString())
            startActivity(this)
        }
        overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
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
        tvSubtitle.text = track.subtitle ?: "Artista"
        loadArtworkInto(ivArt, track.thumbnailUrl)

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

        dialog.show()
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

    private fun loadArtworkInto(target: ImageView, url: String?) {
        if (url.isNullOrEmpty()) {
            target.setImageResource(R.drawable.ic_music)
            return
        }
        val trimmed = url.trim()
        val density = resources.displayMetrics.density
        val displayPx = (64 * density).toInt().coerceAtLeast(1)
        val base = Glide.with(this).load(trimmed)
            .transform(YouTubeCropTransformation())
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .placeholder(R.drawable.ic_music)
            .error(R.drawable.ic_music)
        base.into(target)
    }

    private fun searchTypeLabel(track: YouTubeMusicService.TrackResult) = when (track.resultType?.lowercase(Locale.US)) {
        "video" -> "Canción"
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
            val type = searchTypeLabel(item)
            holder.subtitle.text = if (item.subtitle.isNullOrEmpty()) type else "$type • ${item.subtitle}"
            loadArtworkInto(holder.thumb, item.thumbnailUrl)
            holder.divider.visibility = if (position == data.size - 1) View.GONE else View.VISIBLE
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
            val divider: View = v.findViewById(R.id.vTrackDivider)
            val more: ImageView = v.findViewById(R.id.ivTrackMore)
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
