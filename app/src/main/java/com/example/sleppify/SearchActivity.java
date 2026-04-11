package com.example.sleppify;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.imageview.ShapeableImageView;
import com.google.android.material.textfield.TextInputEditText;

import org.json.JSONArray;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class SearchActivity extends AppCompatActivity {

    public static final String EXTRA_RESULT_TYPE = "search_result_type";
    public static final String EXTRA_RESULT_VIDEO_ID = "search_result_video_id";
    public static final String EXTRA_RESULT_CONTENT_ID = "search_result_content_id";
    public static final String EXTRA_RESULT_TITLE = "search_result_title";
    public static final String EXTRA_RESULT_SUBTITLE = "search_result_subtitle";
    public static final String EXTRA_RESULT_THUMBNAIL = "search_result_thumbnail";
    public static final String EXTRA_RESULT_TRACKS_JSON = "search_result_tracks_json";
    public static final int RESULT_TRACK_SELECTED = 100;
    public static final int RESULT_PLAYLIST_SELECTED = 101;

    private static final String PREFS_STREAMING_CACHE = "streaming_cache";
    private static final String PREF_RECENT_SEARCH_QUERIES = "stream_recent_search_queries";
    private static final int SEARCH_PAGE_SIZE = 20;
    private static final int SEARCH_SUGGESTION_RECENT_LIMIT = 6;
    private static final int SEARCH_SCROLL_LOAD_MORE_THRESHOLD = 4;
    private static final String[] DEFAULT_SEARCH_SUGGESTIONS = {
            "musica para dormir",
            "lofi chill",
            "lluvia relajante",
            "piano instrumental",
            "white noise",
            "deep sleep music"
    };



    private final YouTubeMusicService youTubeMusicService = new YouTubeMusicService();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Map<String, String> normalizedFilterCache = new HashMap<>();
    private final List<YouTubeMusicService.TrackResult> allTracks = new ArrayList<>();
    private final List<YouTubeMusicService.TrackResult> tracks = new ArrayList<>();
    private final List<String> recentSearchQueries = new ArrayList<>();

    private TextInputEditText etSearchQuery;
    private ImageView ivSearchClear;
    private RecyclerView rvSearchResults;
    private TextView tvSearchState;
    private ProgressBar progressSearch;
    private ChipGroup cgSearchSuggestions;
    private LinearLayout llFeaturedResult;
    private ShapeableImageView ivFeaturedThumb;
    private TextView tvFeaturedTitle;
    private TextView tvFeaturedSubtitle;

    private SearchResultsAdapter adapter;
    private YouTubeMusicService.TrackResult featuredTrack;

    private boolean searching;
    private boolean searchPaginationInFlight;
    private boolean hasMoreSearchPages;
    private String nextSearchPageToken = "";
    private String activeSearchQuery = "";
    private long latestSearchRequestId;
    private View moduleLoadingOverlay;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search);

        getWindow().setStatusBarColor(ContextCompat.getColor(this, R.color.surface_dark));
        getWindow().setNavigationBarColor(ContextCompat.getColor(this, R.color.surface_dark));

        etSearchQuery = findViewById(R.id.etSearchQuery);
        ivSearchClear = findViewById(R.id.ivSearchClear);
        rvSearchResults = findViewById(R.id.rvSearchResults);
        tvSearchState = findViewById(R.id.tvSearchState);
        progressSearch = findViewById(R.id.progressSearch);
        cgSearchSuggestions = findViewById(R.id.cgSearchSuggestions);
        llFeaturedResult = findViewById(R.id.llFeaturedResult);
        ivFeaturedThumb = findViewById(R.id.ivFeaturedThumb);
        tvFeaturedTitle = findViewById(R.id.tvFeaturedTitle);
        tvFeaturedSubtitle = findViewById(R.id.tvFeaturedSubtitle);
        moduleLoadingOverlay = findViewById(R.id.moduleLoadingOverlay);



        setupRecyclerView();
        setupSearchInput();
        setupBackButton();

        restoreRecentSearchQueries();
        refreshSearchSuggestions("");

        // Auto-focus and show keyboard
        etSearchQuery.requestFocus();
        getWindow().getDecorView().postDelayed(() -> {
            if (!isDestroyed() && !isFinishing()) {
                InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                if (imm != null) {
                    etSearchQuery.requestFocus();
                    imm.showSoftInput(etSearchQuery, InputMethodManager.SHOW_IMPLICIT);
                }
            }
        }, 350);

        overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
    }

    private void setupRecyclerView() {
        adapter = new SearchResultsAdapter(this::onTrackClicked);
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        rvSearchResults.setLayoutManager(layoutManager);
        rvSearchResults.setHasFixedSize(true);
        rvSearchResults.setItemAnimator(null);
        rvSearchResults.setItemViewCacheSize(15);
        rvSearchResults.setAdapter(adapter);

        rvSearchResults.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                if (dy <= 0 || searching || searchPaginationInFlight || !hasMoreSearchPages) {
                    return;
                }
                int lastVisible = layoutManager.findLastVisibleItemPosition();
                int totalItems = adapter.getItemCount();
                if (lastVisible >= totalItems - SEARCH_SCROLL_LOAD_MORE_THRESHOLD) {
                    loadMoreSearchResults();
                }
            }
        });
    }

    private void setupSearchInput() {
        etSearchQuery.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                performSearch();
                hideKeyboard();
                return true;
            }
            return false;
        });

        etSearchQuery.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                String query = s != null ? s.toString().trim() : "";
                ivSearchClear.setVisibility(query.isEmpty() ? View.GONE : View.VISIBLE);
                refreshSearchSuggestions(query);
            }
        });

        ivSearchClear.setOnClickListener(v -> {
            etSearchQuery.setText("");
            etSearchQuery.requestFocus();
            allTracks.clear();
            tracks.clear();
            featuredTrack = null;
            llFeaturedResult.setVisibility(View.GONE);
            adapter.submitResults(new ArrayList<>());
            tvSearchState.setText("");
            activeSearchQuery = "";
            cgSearchSuggestions.setVisibility(View.VISIBLE);
            refreshSearchSuggestions("");
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.showSoftInput(etSearchQuery, InputMethodManager.SHOW_IMPLICIT);
            }
        });
    }



    private void setupBackButton() {
        findViewById(R.id.btnSearchBack).setOnClickListener(v -> {
            showModuleLoadingOverlay();
            onBackPressed();
        });
    }

    @Override
    public void onBackPressed() {
        showModuleLoadingOverlay();
        super.onBackPressed();
    }



    // ── Search ──────────────────────────────────────────

    private void performSearch() {
        if (searching) return;

        String query = etSearchQuery.getText() != null ? etSearchQuery.getText().toString().trim() : "";
        if (TextUtils.isEmpty(query)) return;

        startPagedSearch(query);
    }

    private void startPagedSearch(@NonNull String query) {
        String normalizedQuery = query.trim();
        if (normalizedQuery.isEmpty()) return;

        latestSearchRequestId++;
        activeSearchQuery = normalizedQuery;
        allTracks.clear();
        tracks.clear();
        featuredTrack = null;
        llFeaturedResult.setVisibility(View.GONE);
        adapter.submitResults(new ArrayList<>());
        hasMoreSearchPages = false;
        nextSearchPageToken = "";

        rememberRecentSearchQuery(normalizedQuery);
        refreshSearchSuggestions(normalizedQuery);
        requestPagedSearchResults(normalizedQuery, "", false);
    }

    private void requestPagedSearchResults(
            @NonNull String query,
            @NonNull String pageToken,
            boolean append
    ) {
        if (!isNetworkAvailable()) {
            setSearchLoadingState(false, "Sin internet.");
            return;
        }

        final long requestId = ++latestSearchRequestId;
        if (append) {
            searchPaginationInFlight = true;
            tvSearchState.setText("Cargando mas resultados...");
        } else {
            setSearchLoadingState(true, "Buscando musica en YouTube...");
        }

        youTubeMusicService.searchTracksPaged(
                query,
                SEARCH_PAGE_SIZE,
                TextUtils.isEmpty(pageToken) ? null : pageToken,
                new YouTubeMusicService.SearchPageCallback() {
                    @Override
                    public void onSuccess(@NonNull YouTubeMusicService.SearchPageResult pageResult) {
                        if (isFinishing() || requestId != latestSearchRequestId) return;

                        if (append) {
                            searchPaginationInFlight = false;
                        } else {
                            setSearchLoadingState(false, "");
                            revealModuleContent();
                            
                            // Fade in results
                            rvSearchResults.setAlpha(0f);
                            rvSearchResults.animate().alpha(1f).setDuration(250).start();
                            hideKeyboard();
                        }

                        List<YouTubeMusicService.TrackResult> incoming = pageResult.tracks;
                        if (!append) {
                            allTracks.clear();
                        }
                        appendUniqueTracks(incoming);

                        activeSearchQuery = query;
                        nextSearchPageToken = pageResult.nextPageToken;
                        hasMoreSearchPages = !TextUtils.isEmpty(nextSearchPageToken);

                        applyActiveFilter(query);

                        if (allTracks.isEmpty()) {
                            tvSearchState.setText("No encontre resultados para: " + query);
                        }
                    }

                    @Override
                    public void onError(@NonNull String error) {
                        if (isFinishing() || requestId != latestSearchRequestId) return;

                        if (append) {
                            searchPaginationInFlight = false;
                            tvSearchState.setText("No se pudieron cargar mas resultados.");
                            return;
                        }

                        setSearchLoadingState(false, "Error: " + error);
                        hasMoreSearchPages = false;
                        nextSearchPageToken = "";
                    }
                }
        );
    }

    private void loadMoreSearchResults() {
        if (searching || searchPaginationInFlight || !hasMoreSearchPages || TextUtils.isEmpty(activeSearchQuery)) {
            return;
        }
        requestPagedSearchResults(activeSearchQuery, nextSearchPageToken, true);
    }

    private void appendUniqueTracks(@NonNull List<YouTubeMusicService.TrackResult> incoming) {
        Set<String> seen = new HashSet<>();
        for (YouTubeMusicService.TrackResult existing : allTracks) {
            if (existing == null) continue;
            String key = (existing.resultType == null ? "" : existing.resultType)
                    + "|" + (existing.contentId == null ? "" : existing.contentId);
            seen.add(key);
        }
        for (YouTubeMusicService.TrackResult candidate : incoming) {
            if (candidate == null) continue;
            String key = (candidate.resultType == null ? "" : candidate.resultType)
                    + "|" + (candidate.contentId == null ? "" : candidate.contentId);
            if (!seen.add(key)) continue;
            allTracks.add(candidate);
        }
    }

    // ── Filter ──────────────────────────────────────────

    private void applyActiveFilter(@Nullable String query) {
        String normalizedQuery = query == null ? "" : query.trim();

        List<YouTubeMusicService.TrackResult> filtered = new ArrayList<>(allTracks);

        if (!TextUtils.isEmpty(normalizedQuery) && filtered.size() > 1) {
            sortSearchResultsByBestMatch(filtered, normalizedQuery);
        }

        tracks.clear();
        if (filtered.isEmpty()) {
            featuredTrack = null;
            llFeaturedResult.setVisibility(View.GONE);
        } else {
            featuredTrack = filtered.get(0);
            if (featuredTrack != null) {
                bindFeaturedTrack(featuredTrack);
                llFeaturedResult.setVisibility(View.VISIBLE);
                if (filtered.size() > 1) {
                    tracks.addAll(filtered.subList(1, filtered.size()));
                }
            } else {
                llFeaturedResult.setVisibility(View.GONE);
            }
        }
        adapter.submitResults(new ArrayList<>(tracks));

        if (!TextUtils.isEmpty(normalizedQuery)) {
            tvSearchState.setText(String.format(Locale.getDefault(), "%d resultados encontrados.", filtered.size()));
        }
    }

    private boolean matchesFilter(@NonNull YouTubeMusicService.TrackResult track) {
        return true;
    }

    // ── Featured result ─────────────────────────────────

    private void bindFeaturedTrack(@NonNull YouTubeMusicService.TrackResult track) {
        tvFeaturedTitle.setText(track.title);
        String typeLabel = searchTypeLabel(track);
        String subtitle = TextUtils.isEmpty(track.subtitle)
                ? typeLabel
                : typeLabel + " • " + track.subtitle;
        tvFeaturedSubtitle.setText(subtitle);
        loadArtworkInto(ivFeaturedThumb, track.thumbnailUrl);

        llFeaturedResult.setOnClickListener(v -> onTrackClicked(track));

        findViewById(R.id.btnFeaturedPlay).setOnClickListener(v -> onTrackClicked(track));
    }

    // ── Track click ─────────────────────────────────────
    
    public void showModuleLoadingOverlay() {
        if (moduleLoadingOverlay != null) {
            moduleLoadingOverlay.setVisibility(View.VISIBLE);
        }
    }

    public void revealModuleContent() {
        if (moduleLoadingOverlay != null) {
            moduleLoadingOverlay.setVisibility(View.GONE);
        }
    }

    private void onTrackClicked(@NonNull YouTubeMusicService.TrackResult track) {
        showModuleLoadingOverlay();
        Intent resultIntent = new Intent();
        resultIntent.putExtra(EXTRA_RESULT_TYPE, track.resultType == null ? "" : track.resultType);
        resultIntent.putExtra(EXTRA_RESULT_VIDEO_ID, track.videoId == null ? "" : track.videoId);
        resultIntent.putExtra(EXTRA_RESULT_CONTENT_ID, track.contentId == null ? "" : track.contentId);
        resultIntent.putExtra(EXTRA_RESULT_TITLE, track.title == null ? "" : track.title);
        resultIntent.putExtra(EXTRA_RESULT_SUBTITLE, track.subtitle == null ? "" : track.subtitle);
        resultIntent.putExtra(EXTRA_RESULT_THUMBNAIL, track.thumbnailUrl == null ? "" : track.thumbnailUrl);

        // Build queue JSON for playback context
        JSONArray queueJson = new JSONArray();
        List<YouTubeMusicService.TrackResult> fullQueue = new ArrayList<>();
        if (featuredTrack != null) fullQueue.add(featuredTrack);
        fullQueue.addAll(tracks);
        try {
            for (YouTubeMusicService.TrackResult t : fullQueue) {
                org.json.JSONObject obj = new org.json.JSONObject();
                obj.put("videoId", t.videoId == null ? "" : t.videoId);
                obj.put("title", t.title == null ? "" : t.title);
                obj.put("subtitle", t.subtitle == null ? "" : t.subtitle);
                obj.put("thumbnailUrl", t.thumbnailUrl == null ? "" : t.thumbnailUrl);
                obj.put("resultType", t.resultType == null ? "" : t.resultType);
                obj.put("contentId", t.contentId == null ? "" : t.contentId);
                queueJson.put(obj);
            }
        } catch (Exception ignored) {}
        resultIntent.putExtra(EXTRA_RESULT_TRACKS_JSON, queueJson.toString());

        boolean isPlaylist = "playlist".equals(track.resultType);
        setResult(isPlaylist ? RESULT_PLAYLIST_SELECTED : RESULT_TRACK_SELECTED, resultIntent);
        finish();
    }

    // ── Loading state ───────────────────────────────────

    private void setSearchLoadingState(boolean loading, @NonNull String stateMessage) {
        searching = loading;
        etSearchQuery.setEnabled(!loading);

        if (loading) {
            showModuleLoadingOverlay();
            cgSearchSuggestions.setVisibility(View.GONE);
            tvSearchState.setText("");
        } else {
            // Hide is usually handled by revealModuleContent in the result success
            if (!stateMessage.isEmpty()) {
                tvSearchState.setText(stateMessage);
            }
        }
    }

    // ── Suggestions ─────────────────────────────────────

    private void restoreRecentSearchQueries() {
        SharedPreferences prefs = getSharedPreferences(PREFS_STREAMING_CACHE, MODE_PRIVATE);
        String json = prefs.getString(PREF_RECENT_SEARCH_QUERIES, "[]");
        recentSearchQueries.clear();
        try {
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                recentSearchQueries.add(array.getString(i));
            }
        } catch (Exception ignored) {}
    }

    private void rememberRecentSearchQuery(@NonNull String query) {
        String normalized = query.trim();
        if (normalized.isEmpty()) return;

        recentSearchQueries.remove(normalized);
        recentSearchQueries.add(0, normalized);
        while (recentSearchQueries.size() > SEARCH_SUGGESTION_RECENT_LIMIT) {
            recentSearchQueries.remove(recentSearchQueries.size() - 1);
        }

        saveRecentSearchQueries();
    }

    private void saveRecentSearchQueries() {
        JSONArray array = new JSONArray();
        for (String query : recentSearchQueries) {
            array.put(query);
        }
        getSharedPreferences(PREFS_STREAMING_CACHE, MODE_PRIVATE)
                .edit()
                .putString(PREF_RECENT_SEARCH_QUERIES, array.toString())
                .apply();
    }

    private void refreshSearchSuggestions(@Nullable String queryDraft) {
        if (cgSearchSuggestions == null) return;

        String draft = queryDraft == null ? "" : queryDraft.trim();
        List<String> suggestions = buildSearchSuggestions(draft);
        cgSearchSuggestions.removeAllViews();

        for (String suggestion : suggestions) {
            Chip chip = new Chip(this);
            chip.setText(suggestion);
            chip.setCheckable(false);
            chip.setClickable(true);
            chip.setChipBackgroundColor(ColorStateList.valueOf(
                    ContextCompat.getColor(this, R.color.surface_low)));
            chip.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
            chip.setEnsureMinTouchTargetSize(false);
            chip.setOnClickListener(v -> {
                etSearchQuery.setText(suggestion);
                if (etSearchQuery.getText() != null) {
                    etSearchQuery.setSelection(etSearchQuery.getText().length());
                }
                performSearch();
                hideKeyboard();
            });
            cgSearchSuggestions.addView(chip);
        }

        cgSearchSuggestions.setVisibility(suggestions.isEmpty() ? View.GONE : View.VISIBLE);
    }

    @NonNull
    private List<String> buildSearchSuggestions(@NonNull String queryDraft) {
        LinkedHashSet<String> pool = new LinkedHashSet<>();
        for (String recent : recentSearchQueries) {
            if (!TextUtils.isEmpty(recent)) pool.add(recent);
        }
        for (String fallback : DEFAULT_SEARCH_SUGGESTIONS) {
            if (!TextUtils.isEmpty(fallback)) pool.add(fallback);
        }

        String normalizedDraft = normalizeForFilter(queryDraft);
        List<String> filtered = new ArrayList<>();
        for (String candidate : pool) {
            if (filtered.size() >= SEARCH_SUGGESTION_RECENT_LIMIT) break;
            if (!normalizedDraft.isEmpty()) {
                String normalizedCandidate = normalizeForFilter(candidate);
                if (!normalizedCandidate.contains(normalizedDraft)
                        && !normalizedDraft.contains(normalizedCandidate)) {
                    continue;
                }
            }
            filtered.add(candidate);
        }
        return filtered;
    }

    // ── Artwork ─────────────────────────────────────────

    private void loadArtworkInto(@NonNull ImageView target, @Nullable String imageUrl) {
        if (TextUtils.isEmpty(imageUrl)) {
            target.setImageResource(R.drawable.ic_music);
            return;
        }

        String safeUrl = imageUrl.trim();
        Glide.with(this)
                .load(safeUrl)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .skipMemoryCache(false)
                .dontAnimate()
                .centerCrop()
                .thumbnail(0.25f)
                .placeholder(R.drawable.ic_music)
                .error(R.drawable.ic_music)
                .into(target);
    }

    // ── Helpers ──────────────────────────────────────────

    @NonNull
    private String searchTypeLabel(@NonNull YouTubeMusicService.TrackResult track) {
        String type = track.resultType == null ? "" : track.resultType.trim().toLowerCase(Locale.US);
        switch (type) {
            case "video": return "Canción";
            case "channel": return "Artista";
            case "playlist": return "Playlist";
            default: return "Resultado";
        }
    }



    @NonNull
    private String normalizeForFilter(@Nullable String value) {
        if (TextUtils.isEmpty(value)) return "";
        String cached = normalizedFilterCache.get(value);
        if (cached != null) return cached;

        String decomposed = Normalizer.normalize(value, Normalizer.Form.NFD);
        StringBuilder builder = new StringBuilder(decomposed.length());
        for (int i = 0; i < decomposed.length(); i++) {
            char c = decomposed.charAt(i);
            if (Character.getType(c) != Character.NON_SPACING_MARK) {
                builder.append(Character.toLowerCase(c));
            }
        }
        String normalized = builder.toString().trim();
        if (normalizedFilterCache.size() > 256) normalizedFilterCache.clear();
        normalizedFilterCache.put(value, normalized);
        return normalized;
    }

    private boolean containsAny(@NonNull String text, @NonNull String... terms) {
        for (String term : terms) {
            if (text.contains(term)) return true;
        }
        return false;
    }

    private void sortSearchResultsByBestMatch(
            @NonNull List<YouTubeMusicService.TrackResult> source,
            @NonNull String query
    ) {
        String normalizedQuery = normalizeForFilter(query);
        if (normalizedQuery.isEmpty()) return;
        String[] queryTokens = normalizedQuery.split("\\s+");

        source.sort((left, right) -> {
            int leftScore = computeSearchMatchScore(left, normalizedQuery, queryTokens);
            int rightScore = computeSearchMatchScore(right, normalizedQuery, queryTokens);
            if (leftScore != rightScore) {
                return Integer.compare(rightScore, leftScore);
            }
            return normalizeForFilter(left.title).compareTo(normalizeForFilter(right.title));
        });
    }

    private int computeSearchMatchScore(
            @NonNull YouTubeMusicService.TrackResult track,
            @NonNull String normalizedQuery,
            @NonNull String[] queryTokens
    ) {
        String title = normalizeForFilter(track.title);
        String subtitle = normalizeForFilter(track.subtitle);
        int score = 0;

        if (title.equals(normalizedQuery)) score += 800;
        else if (title.startsWith(normalizedQuery + " ")) score += 760;
        else if (title.startsWith(normalizedQuery)) score += 560;
        else if (title.contains(normalizedQuery)) score += 340;

        if (subtitle.equals(normalizedQuery)) score += 220;
        else if (subtitle.startsWith(normalizedQuery)) score += 180;
        else if (subtitle.contains(normalizedQuery)) score += 120;

        for (String token : queryTokens) {
            if (token == null || token.isEmpty()) continue;
            if (title.contains(token)) score += 60;
            if (subtitle.contains(token)) score += 30;
        }
        return score;
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager cm = ContextCompat.getSystemService(this, ConnectivityManager.class);
        if (cm == null) return false;
        NetworkCapabilities capabilities = cm.getNetworkCapabilities(cm.getActiveNetwork());
        if (capabilities == null) return false;
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                || capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
                || capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
                || capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN);
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null && getCurrentFocus() != null) {
            imm.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(), 0);
        }
    }

    // ── Adapter ─────────────────────────────────────────

    public interface OnResultClick {
        void onClick(@NonNull YouTubeMusicService.TrackResult track);
    }

    private class SearchResultsAdapter extends RecyclerView.Adapter<SearchResultsAdapter.TrackViewHolder> {

        private final List<YouTubeMusicService.TrackResult> data = new ArrayList<>();
        private final OnResultClick onResultClick;

        SearchResultsAdapter(@NonNull OnResultClick onResultClick) {
            this.onResultClick = onResultClick;
            setHasStableIds(true);
        }

        @Override
        public long getItemId(int position) {
            if (position < 0 || position >= data.size()) return RecyclerView.NO_ID;
            YouTubeMusicService.TrackResult item = data.get(position);
            String identity = (item.resultType == null ? "" : item.resultType)
                    + "|" + (item.contentId == null ? "" : item.contentId)
                    + "|" + (item.title == null ? "" : item.title);
            return identity.hashCode();
        }

        void submitResults(@NonNull List<YouTubeMusicService.TrackResult> newData) {
            List<YouTubeMusicService.TrackResult> previous = new ArrayList<>(data);
            List<YouTubeMusicService.TrackResult> incoming = new ArrayList<>(newData);

            DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(new DiffUtil.Callback() {
                @Override public int getOldListSize() { return previous.size(); }
                @Override public int getNewListSize() { return incoming.size(); }

                @Override
                public boolean areItemsTheSame(int oldPos, int newPos) {
                    YouTubeMusicService.TrackResult oldItem = previous.get(oldPos);
                    YouTubeMusicService.TrackResult newItem = incoming.get(newPos);
                    if (!TextUtils.isEmpty(oldItem.contentId) && !TextUtils.isEmpty(newItem.contentId)) {
                        return TextUtils.equals(oldItem.contentId, newItem.contentId)
                                && TextUtils.equals(oldItem.resultType, newItem.resultType);
                    }
                    return TextUtils.equals(oldItem.title, newItem.title)
                            && TextUtils.equals(oldItem.subtitle, newItem.subtitle);
                }

                @Override
                public boolean areContentsTheSame(int oldPos, int newPos) {
                    YouTubeMusicService.TrackResult oldItem = previous.get(oldPos);
                    YouTubeMusicService.TrackResult newItem = incoming.get(newPos);
                    return TextUtils.equals(oldItem.resultType, newItem.resultType)
                            && TextUtils.equals(oldItem.contentId, newItem.contentId)
                            && TextUtils.equals(oldItem.title, newItem.title)
                            && TextUtils.equals(oldItem.subtitle, newItem.subtitle)
                            && TextUtils.equals(oldItem.thumbnailUrl, newItem.thumbnailUrl);
                }
            });

            data.clear();
            data.addAll(incoming);
            diffResult.dispatchUpdatesTo(this);
        }

        @NonNull
        @Override
        public TrackViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_music_search_result, parent, false);
            return new TrackViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull TrackViewHolder holder, int position) {
            YouTubeMusicService.TrackResult item = data.get(position);

            String title = TextUtils.isEmpty(item.title) ? "Resultado" : item.title;
            holder.tvTrackTitle.setText(title);

            String typeLabel = searchTypeLabel(item);
            String subtitle = TextUtils.isEmpty(item.subtitle)
                    ? typeLabel
                    : typeLabel + " • " + item.subtitle;
            holder.tvTrackSubtitle.setText(subtitle);

            loadArtworkInto(holder.ivTrackThumb, item.thumbnailUrl);

            holder.vTrackDivider.setVisibility(position == data.size() - 1 ? View.GONE : View.VISIBLE);
            holder.itemView.setOnClickListener(v -> onResultClick.onClick(item));
        }

        @Override
        public int getItemCount() {
            return data.size();
        }

        final class TrackViewHolder extends RecyclerView.ViewHolder {
            final ImageView ivTrackThumb;
            final TextView tvTrackTitle;
            final TextView tvTrackSubtitle;
            final View vTrackDivider;

            TrackViewHolder(@NonNull View itemView) {
                super(itemView);
                ivTrackThumb = itemView.findViewById(R.id.ivTrackThumb);
                tvTrackTitle = itemView.findViewById(R.id.tvTrackTitle);
                tvTrackSubtitle = itemView.findViewById(R.id.tvTrackSubtitle);
                vTrackDivider = itemView.findViewById(R.id.vTrackDivider);
            }
        }
    }
}
