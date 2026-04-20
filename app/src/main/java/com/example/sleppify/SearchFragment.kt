package com.example.sleppify

import android.content.Context
import android.graphics.Typeface
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
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.Fragment
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
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.text.Normalizer
import java.util.*

class SearchFragment : Fragment() {

    companion object {
        private const val PREFS_STREAMING_CACHE = "streaming_cache"
        private const val PREF_RECENT_SEARCH_QUERIES = "stream_recent_search_queries"
        private const val SEARCH_PAGE_SIZE = 20
        private const val SEARCH_SUGGESTION_RECENT_LIMIT = 6
        private const val SEARCH_SCROLL_LOAD_MORE_THRESHOLD = 4
        
        private val DEFAULT_SEARCH_SUGGESTIONS = arrayOf(
            "musica para dormir", "lofi chill", "lluvia relajante", 
            "piano instrumental", "white noise", "deep sleep music"
        )
    }

    private val youTubeMusicService = YouTubeMusicService()
    private val normalizedFilterCache = mutableMapOf<String, String>()
    private val allTracks = mutableListOf<YouTubeMusicService.TrackResult>()
    private val tracks = mutableListOf<YouTubeMusicService.TrackResult>()
    private val recentSearchQueries = mutableListOf<String>()

    private lateinit var etSearchQuery: TextInputEditText
    private lateinit var llSearchBar: View
    private lateinit var ivSearchClear: ImageView
    private lateinit var rvSearchResults: RecyclerView
    private lateinit var tvSearchState: TextView
    private lateinit var rvSearchSuggestions: RecyclerView
    private lateinit var llFeaturedResult: LinearLayout
    private lateinit var ivFeaturedThumb: ShapeableImageView
    private lateinit var tvFeaturedTitle: TextView
    private lateinit var tvFeaturedSubtitle: TextView
    private lateinit var moduleLoadingOverlay: View

    private var adapter: SearchResultsAdapter? = null
    private var featuredTrack: YouTubeMusicService.TrackResult? = null
    
    private var searching = false
    private var searchPaginationInFlight = false
    private var hasMoreSearchPages = false
    private var nextSearchPageToken = ""
    private var activeSearchQuery = ""
    private var latestSearchRequestId = 0L

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

        etSearchQuery.requestFocus()
        lifecycleScope.launch {
            delay(350)
            if (isAdded) {
                val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                imm?.showSoftInput(etSearchQuery, InputMethodManager.SHOW_IMPLICIT)
            }
        }
    }

    private fun initViews(view: View) {
        etSearchQuery = view.findViewById(R.id.etSearchQuery)
        llSearchBar = view.findViewById(R.id.llSearchBar)
        ivSearchClear = view.findViewById(R.id.ivSearchClear)
        rvSearchResults = view.findViewById(R.id.rvSearchResults)
        tvSearchState = view.findViewById(R.id.tvSearchState)
        rvSearchSuggestions = view.findViewById(R.id.rvSearchSuggestions)
        llFeaturedResult = view.findViewById(R.id.llFeaturedResult)
        ivFeaturedThumb = view.findViewById(R.id.ivFeaturedThumb)
        tvFeaturedTitle = view.findViewById(R.id.tvFeaturedTitle)
        tvFeaturedSubtitle = view.findViewById(R.id.tvFeaturedSubtitle)
        moduleLoadingOverlay = view.findViewById(R.id.moduleLoadingOverlay)
    }

    private fun setupRecyclerView() {
        val layoutManager = LinearLayoutManager(requireContext())
        adapter = SearchResultsAdapter { onTrackClicked(it) }
        rvSearchResults.apply {
            this.layoutManager = layoutManager
            setHasFixedSize(true)
            itemAnimator = null
            setItemViewCacheSize(15)
            adapter = this@SearchFragment.adapter
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
            layoutManager = LinearLayoutManager(requireContext())
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
            val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
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
        if (!isNetworkAvailable()) {
            searchOfflineTracks(query)
            return
        }

        val requestId = ++latestSearchRequestId
        if (append) {
            searchPaginationInFlight = true
            tvSearchState.text = "Cargando mas resultados..."
        } else {
            setSearchLoadingState(true, "Buscando música...")
        }

        youTubeMusicService.searchTracksPaged(query, SEARCH_PAGE_SIZE, pageToken.takeIf { it.isNotEmpty() }, object : YouTubeMusicService.SearchPageCallback {
            override fun onSuccess(pageResult: YouTubeMusicService.SearchPageResult) {
                if (!isAdded || requestId != latestSearchRequestId) return
                
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
                    allTracks.firstOrNull()?.videoId?.let { id ->
                        lifecycleScope.launch(Dispatchers.IO) {
                            YouTubeStreamResolver.resolveStreamUrl(id)
                        }
                    }
                }
            }

            override fun onError(error: String) {
                if (!isAdded || requestId != latestSearchRequestId) return
                if (append) {
                    searchPaginationInFlight = false
                    tvSearchState.text = "Error al cargar más resultados."
                } else {
                    setSearchLoadingState(false, "Error: $error")
                }
            }
        })
    }

    private fun loadMoreSearchResults() {
        if (searching || searchPaginationInFlight || !hasMoreSearchPages || activeSearchQuery.isEmpty()) return
        requestPagedSearchResults(activeSearchQuery, nextSearchPageToken, true)
    }

    private fun searchOfflineTracks(query: String) {
        setSearchLoadingState(true, "Buscando en canciones locales...")
        lifecycleScope.launch(Dispatchers.IO) {
            val results = mutableListOf<YouTubeMusicService.TrackResult>()
            val normQuery = normalizeForFilter(query)
            val tokens = normQuery.split(Regex("\\s+")).filter { it.isNotEmpty() }

            val favorites = FavoritesPlaylistStore.loadFavorites(requireContext())
            favorites.forEach {
                if (matchesOfflineTokens(it.title, it.artist, normQuery, tokens)) {
                    results.add(YouTubeMusicService.TrackResult("video", it.videoId, it.title, it.artist, it.imageUrl))
                }
            }

            val playlists = CustomPlaylistsStore.getAllPlaylistNames(requireContext())
            playlists.forEach { plName ->
                val tracks = CustomPlaylistsStore.getTracksFromPlaylist(requireContext(), plName)
                tracks.forEach {
                    if (matchesOfflineTokens(it.title, it.artist, normQuery, tokens)) {
                        if (results.none { res -> res.videoId == it.videoId }) {
                            results.add(YouTubeMusicService.TrackResult("video", it.videoId, it.title, it.artist, it.imageUrl))
                        }
                    }
                }
            }

            withContext(Dispatchers.Main) {
                setSearchLoadingState(false, "")
                if (!isAdded) return@withContext
                
                allTracks.clear()
                allTracks.addAll(results)
                hasMoreSearchPages = false
                nextSearchPageToken = ""
                applyActiveFilter(query)

                if (results.isEmpty()) {
                    tvSearchState.text = "No hay canciones locales que coincidan con: ${query}"
                    tvSearchState.visibility = View.VISIBLE
                }
                
                revealModuleContent()
                rvSearchResults.alpha = 0f
                rvSearchResults.animate().alpha(1f).setDuration(250).start()
                hideKeyboard()
            }
        }
    }

    private fun matchesOfflineTokens(title: String?, artist: String?, query: String, tokens: List<String>): Boolean {
        val t = normalizeForFilter(title)
        val a = normalizeForFilter(artist)
        if (t.contains(query) || a.contains(query)) return true
        if (tokens.isNotEmpty() && tokens.all { t.contains(it) || a.contains(it) }) return true
        return false
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
        view?.findViewById<View>(R.id.btnFeaturedPlay)?.setOnClickListener { onTrackClicked(track) }
    }

    private fun onTrackClicked(track: YouTubeMusicService.TrackResult) {
        val queue = listOfNotNull(featuredTrack) + tracks
        val selectedIndex = queue.indexOfFirst { TextUtils.equals(it.videoId, track.videoId) }.coerceAtLeast(0)
        
        val ids = ArrayList<String>(queue.size)
        val titles = ArrayList<String>(queue.size)
        val artists = ArrayList<String>(queue.size)
        val durations = ArrayList<String>(queue.size)
        val images = ArrayList<String>(queue.size)
        
        queue.forEach { item ->
            ids.add(item.videoId ?: "")
            titles.add(item.title ?: "")
            artists.add(item.subtitle ?: "")
            durations.add("0:00")
            images.add(item.thumbnailUrl ?: "")
        }
        
        if (ids.isNotEmpty()) {
            (requireActivity() as MainActivity).playQueue(ids, titles, artists, durations, images, selectedIndex)
        }
    }

    private fun setSearchLoadingState(loading: Boolean, msg: String) {
        searching = loading
        val llState = view?.findViewById<LinearLayout>(R.id.llSearchState)
        if (loading) {
            showModuleLoadingOverlay()
            tvSearchState.visibility = View.GONE
            llState?.visibility = View.VISIBLE
        } else {
            if (msg.isNotEmpty()) {
                tvSearchState.text = msg
                tvSearchState.visibility = View.VISIBLE
                llState?.visibility = View.VISIBLE
            } else {
                tvSearchState.visibility = View.GONE
                llState?.visibility = View.GONE
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
        val req = if (YouTubeImageProcessor.shouldProcess(trimmed)) {
            val side = YouTubeImageProcessor.decodeDimensionForSmartCrop(displayPx)
            base.override(side, side)
        } else {
            base
        }
        req.into(target)
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
        val cm = requireContext().getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) || caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
    }

    private fun hideKeyboard() {
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(view?.windowToken, 0)
    }

    private fun showModuleLoadingOverlay() { moduleLoadingOverlay.visibility = View.VISIBLE }
    private fun revealModuleContent() { moduleLoadingOverlay.visibility = View.GONE }

    private inner class SearchResultsAdapter(val onClick: (YouTubeMusicService.TrackResult) -> Unit) : RecyclerView.Adapter<SearchResultsAdapter.TrackViewHolder>() {
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
            data.clear(); data.addAll(newData); diff.dispatchUpdatesTo(this)
        }
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = TrackViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_music_search_result, parent, false))
        override fun onBindViewHolder(holder: TrackViewHolder, position: Int) = holder.bind(data[position])
        override fun getItemCount() = data.size
        inner class TrackViewHolder(v: View) : RecyclerView.ViewHolder(v) {
            val ivArt = v.findViewById<ImageView>(R.id.ivTrackThumb)
            val tvTitle = v.findViewById<TextView>(R.id.tvTrackTitle)
            val tvSubtitle = v.findViewById<TextView>(R.id.tvTrackSubtitle)
            fun bind(item: YouTubeMusicService.TrackResult) {
                tvTitle.text = item.title; tvSubtitle.text = item.subtitle; loadArtworkInto(ivArt, item.thumbnailUrl)
                itemView.setOnClickListener { onClick(item) }
            }
        }
    }

    private inner class SuggestionsAdapter(val onClick: (String) -> Unit) : RecyclerView.Adapter<SuggestionsAdapter.SuggestionViewHolder>() {
        private val data = mutableListOf<String>()
        fun updateSuggestions(newData: List<String>) {
            data.clear(); data.addAll(newData); notifyDataSetChanged()
        }
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = SuggestionViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_search_suggestion, parent, false))
        override fun onBindViewHolder(holder: SuggestionViewHolder, position: Int) {
            val item = data[position]
            holder.tvSuggestion.text = item
            holder.itemView.setOnClickListener { onClick(item) }
        }
        override fun getItemCount() = data.size
        inner class SuggestionViewHolder(v: View) : RecyclerView.ViewHolder(v) {
            val tvSuggestion = v.findViewById<TextView>(R.id.tvSuggestionText)
        }
    }
}
