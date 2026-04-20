package com.example.sleppify

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat as AndroidMediaFormat
import android.media.MediaMetadataRetriever
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import androidx.work.Data
import androidx.work.Worker
import androidx.work.WorkerParameters
import org.json.JSONObject
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.AudioStream
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.Collections
import java.util.Locale
import java.util.concurrent.ExecutorCompletionService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.max

class OfflinePlaylistDownloadWorker(
    context: Context,
    params: WorkerParameters
) : Worker(context, params) {

    companion object {
        private const val TAG = "OfflineDlWorker"

        const val INPUT_PLAYLIST_ID = "input_playlist_id"
        const val INPUT_VIDEO_IDS = "input_video_ids"
        const val INPUT_TITLES = "input_titles"
        const val INPUT_ARTISTS = "input_artists"
        const val INPUT_DURATIONS = "input_durations"
        const val INPUT_ALREADY_OFFLINE_COUNT = "input_already_offline_count"
        const val INPUT_TOTAL_WITH_VIDEO_ID = "input_total_with_video_id"
        const val INPUT_PLAYLIST_TITLE = "input_playlist_title"
        const val INPUT_USER_INITIATED = "input_user_initiated"
        const val INPUT_MANUAL_QUEUE = "input_manual_queue"

        const val PROGRESS_DONE = "progress_done"
        const val PROGRESS_TOTAL = "progress_total"
        const val PROGRESS_LAST_ID = "progress_last_id"
        const val PROGRESS_CURRENT_ID = "progress_current_id"
        const val PROGRESS_ACTIVE_IDS = "progress_active_ids"
        const val PROGRESS_ACTIVE_FRACTIONS = "progress_active_fractions"
        const val PROGRESS_DOWNLOADED = "progress_downloaded"
        const val PROGRESS_PLAYLIST_TITLE = "progress_playlist_title"

        const val OUTPUT_DOWNLOADED = "output_downloaded"
        const val OUTPUT_TOTAL = "output_total"
        const val OUTPUT_REASON = "output_reason"
        const val OUTPUT_REASON_NONE = "none"
        const val OUTPUT_REASON_NO_NETWORK = "no_network"
        const val OUTPUT_REASON_NO_MATCH = "no_match"

        private const val CONNECT_TIMEOUT_MS = 12000
        private const val READ_TIMEOUT_MS = 35000
        private const val DOWNLOAD_PROGRESS_LOG_STEP_BYTES = 2L * 1024L * 1024L
        private const val DOWNLOAD_MAX_RETRIES = 15
        private const val DOWNLOAD_RETRY_BACKOFF_MS = 1200L
        private const val MAX_PARALLEL_DOWNLOADS_AUTO = 3
        private const val MAX_PARALLEL_DOWNLOADS_MANUAL = 1
        private const val MAX_NEWPIPE_AUTOMATIC_FAILURES = 3
        private const val TARGET_M4A_BITRATE_LOW = 64_000
        private const val TARGET_M4A_BITRATE_MEDIUM = 128_000
        private const val TARGET_M4A_BITRATE_HIGH = 256_000
        private const val TARGET_M4A_BITRATE_VERY_HIGH = 320_000
        private const val MIN_VALID_AUDIO_FILE_BYTES = 24L * 1024L
        private const val DOWNLOAD_DURATION_MIN_SAFE_SECONDS = 6
        private const val DOWNLOAD_DURATION_MATCH_RATIO = 0.88f
        private const val DOWNLOAD_DURATION_LEEWAY_SECONDS = 8
        private const val MIN_STEREO_CHANNELS = 2
        private val NEWPIPE_INIT_LOCK = Any()

        @Volatile
        private var newPipeInitialized = false
    }

    override fun doWork(): Result {
        val appContext = applicationContext
        val ids = inputData.getStringArray(INPUT_VIDEO_IDS)
        if (ids.isNullOrEmpty()) {
            Log.d(TAG, "doWork: no IDs received")
            return Result.failure(
                Data.Builder()
                    .putInt(OUTPUT_TOTAL, 0)
                    .putInt(OUTPUT_DOWNLOADED, 0)
                    .putString(OUTPUT_REASON, OUTPUT_REASON_NONE)
                    .build()
            )
        }

        val playlistTitle = normalizePlaylistTitle(
            inputData.getString(INPUT_PLAYLIST_TITLE),
            inputData.getString(INPUT_PLAYLIST_ID)
        )
        val titles = inputData.getStringArray(INPUT_TITLES)
        val artists = inputData.getStringArray(INPUT_ARTISTS)
        val durations = inputData.getStringArray(INPUT_DURATIONS)

        val pendingTotal = ids.size
        val alreadyOfflineCount = max(0, inputData.getInt(INPUT_ALREADY_OFFLINE_COUNT, 0))
        val totalWithVideoId = inputData.getInt(INPUT_TOTAL_WITH_VIDEO_ID, pendingTotal + alreadyOfflineCount)
        val total = maxOf(pendingTotal + alreadyOfflineCount, totalWithVideoId)
        var done = minOf(total, alreadyOfflineCount)
        var downloaded = minOf(total, alreadyOfflineCount)
        val userInitiated = inputData.getBoolean(INPUT_USER_INITIATED, false)
        val manualQueue = inputData.getBoolean(INPUT_MANUAL_QUEUE, false)
        val maxParallelDownloads = if (manualQueue) MAX_PARALLEL_DOWNLOADS_MANUAL else MAX_PARALLEL_DOWNLOADS_AUTO
        var encounteredNoNetwork = false

        val restrictedIds: MutableSet<String> = Collections.synchronizedSet(
            HashSet(OfflineRestrictionStore.getRestrictedVideoIds(appContext))
        )
        val activeTrackIds: MutableSet<String> = Collections.synchronizedSet(HashSet())
        val activeTrackProgressFractions: MutableMap<String, Float> = Collections.synchronizedMap(HashMap())

        Log.d(TAG, "doWork:start total=$total pending=$pendingTotal alreadyOffline=$alreadyOfflineCount manualQueue=$manualQueue maxParallel=$maxParallelDownloads")
        val initialSnapshot = snapshotActiveProgress(activeTrackIds, activeTrackProgressFractions)
        publishProgress(playlistTitle, done, total, downloaded, "", initialSnapshot.trackIds, initialSnapshot.fractions)

        val executor = Executors.newFixedThreadPool(maxParallelDownloads)
        val completionService = ExecutorCompletionService<TrackDownloadResult>(executor)
        var submittedTasks = 0

        for (i in ids.indices) {
            if (isStopped) {
                executor.shutdownNow()
                Log.w(TAG, "doWork: worker stopped before enqueue, retrying")
                return Result.retry()
            }

            completionService.submit {
                processSingleTrackDownload(
                    appContext,
                    userInitiated,
                    i,
                    ids,
                    titles,
                    artists,
                    durations,
                    restrictedIds,
                    activeTrackIds,
                    activeTrackProgressFractions
                )
            }
            submittedTasks++
        }

        for (i in 0 until submittedTasks) {
            if (isStopped) {
                executor.shutdownNow()
                Log.w(TAG, "doWork: worker stopped, retrying")
                return Result.retry()
            }

            var trackResult: TrackDownloadResult? = null
            while (trackResult == null) {
                if (isStopped) {
                    executor.shutdownNow()
                    Log.w(TAG, "doWork: worker stopped while waiting for result, retrying")
                    return Result.retry()
                }

                try {
                    val future = completionService.poll(650L, TimeUnit.MILLISECONDS)
                    if (future == null) {
                        val activeSnapshot = snapshotActiveProgress(activeTrackIds, activeTrackProgressFractions)
                        val currentId = if (activeSnapshot.trackIds.isNotEmpty()) activeSnapshot.trackIds[0] else ""
                        publishProgress(
                            playlistTitle,
                            done,
                            total,
                            downloaded,
                            currentId,
                            activeSnapshot.trackIds,
                            activeSnapshot.fractions
                        )
                        continue
                    }

                    trackResult = future.get() ?: TrackDownloadResult.failed("", "")
                } catch (interrupted: InterruptedException) {
                    Thread.currentThread().interrupt()
                    trackResult = TrackDownloadResult.failed("", "")
                } catch (e: Exception) {
                    Log.e(TAG, "track:future_exception message=${e.message}", e)
                    trackResult = TrackDownloadResult.failed("", "")
                }
            }

            if (trackResult.noNetworkEncountered) {
                encounteredNoNetwork = true
            }
            if (trackResult.downloaded) {
                downloaded = minOf(total, downloaded + 1)
            }

            done = minOf(total, done + 1)
            val activeSnapshot = snapshotActiveProgress(activeTrackIds, activeTrackProgressFractions)
            val currentId = if (activeSnapshot.trackIds.isNotEmpty()) activeSnapshot.trackIds[0] else trackResult.trackId
            publishProgress(
                playlistTitle,
                done,
                total,
                downloaded,
                currentId,
                activeSnapshot.trackIds,
                activeSnapshot.fractions
            )
        }

        executor.shutdownNow()

        val reason = when {
            encounteredNoNetwork -> OUTPUT_REASON_NO_NETWORK
            downloaded <= 0 -> OUTPUT_REASON_NO_MATCH
            else -> OUTPUT_REASON_NONE
        }

        Log.d(TAG, "doWork:end downloaded=$downloaded/$total reason=$reason")

        if (reason == OUTPUT_REASON_NO_NETWORK) {
            Log.w(TAG, "doWork:network_unavailable retrying")
            return Result.retry()
        }

        return Result.success(
            Data.Builder()
                .putInt(OUTPUT_TOTAL, total)
                .putInt(OUTPUT_DOWNLOADED, downloaded)
                .putString(OUTPUT_REASON, reason)
                .build()
        )
    }

    private fun publishProgress(
        playlistTitle: String,
        done: Int,
        total: Int,
        downloaded: Int,
        currentId: String?,
        activeTrackIds: Array<String>,
        activeFractions: FloatArray
    ) {
        setProgressAsync(
            Data.Builder()
                .putInt(PROGRESS_DONE, done)
                .putInt(PROGRESS_TOTAL, total)
                .putString(PROGRESS_PLAYLIST_TITLE, playlistTitle)
                .putString(PROGRESS_CURRENT_ID, currentId ?: "")
                .putString(PROGRESS_LAST_ID, currentId ?: "")
                .putStringArray(PROGRESS_ACTIVE_IDS, activeTrackIds)
                .putFloatArray(PROGRESS_ACTIVE_FRACTIONS, activeFractions)
                .putInt(PROGRESS_DOWNLOADED, downloaded)
                .build()
        )
    }

    private fun processSingleTrackDownload(
        context: Context,
        userInitiated: Boolean,
        trackIndex: Int,
        ids: Array<String>,
        titles: Array<String>?,
        artists: Array<String>?,
        durations: Array<String>?,
        restrictedIds: MutableSet<String>,
        activeTrackIds: MutableSet<String>,
        activeTrackProgressFractions: MutableMap<String, Float>
    ): TrackDownloadResult {
        val rawId = ids.getOrNull(trackIndex)
        val id = rawId?.trim() ?: ""
        val title = safeArrayValue(titles, trackIndex)
        val artist = safeArrayValue(artists, trackIndex)
        val expectedDurationLabel = safeArrayValue(durations, trackIndex)

        if (id.isEmpty()) {
            return TrackDownloadResult.failed(id, title)
        }

        val wasRestricted = OfflineRestrictionStore.isRestricted(restrictedIds, id) ||
                            OfflineRestrictionStore.isRestricted(context, id)
        if (wasRestricted) {
            restrictedIds.add(id)
            Log.d(TAG, "track:restricted_skip id=$id userInitiated=$userInitiated")
            return TrackDownloadResult.failed(id, title)
        }

        val automaticFailCount = OfflineRestrictionStore.getAutomaticNewPipeFailureCount(context, id)
        if (!userInitiated && automaticFailCount >= MAX_NEWPIPE_AUTOMATIC_FAILURES) {
            OfflineRestrictionStore.markRestricted(context, id)
            restrictedIds.add(id)
            Log.w(TAG, "track:auto_blocked_after_newpipe_failures id=$id fails=$automaticFailCount")
            return TrackDownloadResult.failed(id, title)
        }

        var downloaded = OfflineAudioStore.hasValidatedOfflineAudio(context, id, expectedDurationLabel)
        var noNetworkEncountered = false
        var addedToActive = false
        val networkIssueTracker = NetworkIssueTracker()

        if (downloaded) {
            Log.d(TAG, "track:already_offline id=$id")
            return TrackDownloadResult.downloaded(id, title, false)
        }

        try {
            addedToActive = activeTrackIds.add(id)
            activeTrackProgressFractions[id] = 0f

            val progressReporter = ProgressReporter { progressFraction ->
                val safeProgress = progressFraction.coerceIn(0f, 1f)
                activeTrackProgressFractions[id] = safeProgress
            }

            if (!isInternetAvailable(context)) {
                noNetworkEncountered = true
                Log.w(TAG, "track:no_network id=$id")
                return TrackDownloadResult.failed(id, title, true)
            }

            if (!isDownloadAllowedByCurrentNetworkPolicy(context, networkIssueTracker)) {
                noNetworkEncountered = true
                Log.w(TAG, "track:blocked_mobile_data_policy id=$id")
                return TrackDownloadResult.failed(id, title, true)
            }

            val targetBitrate = resolveTargetBitrate(context)
            downloaded = downloadTrackFromYouTubeNewPipe(context, id, targetBitrate, networkIssueTracker, progressReporter)
            noNetworkEncountered = noNetworkEncountered || networkIssueTracker.hasIssue()
            Log.d(TAG, "track:newpipe_result id=$id ok=$downloaded")
            
            if (downloaded) {
                OfflineRestrictionStore.clearAutomaticNewPipeFailures(context, id)
            } else if (!networkIssueTracker.hasIssue() && !userInitiated) {
                val failures = OfflineRestrictionStore.incrementAutomaticNewPipeFailure(context, id)
                Log.w(TAG, "track:newpipe_auto_fail id=$id failures=$failures/$MAX_NEWPIPE_AUTOMATIC_FAILURES")
                if (failures >= MAX_NEWPIPE_AUTOMATIC_FAILURES) {
                    OfflineRestrictionStore.markRestricted(context, id)
                    restrictedIds.add(id)
                    Log.w(TAG, "track:newpipe_auto_restricted id=$id")
                    return TrackDownloadResult.failed(id, title, noNetworkEncountered)
                }
            }

            if (!downloaded && !networkIssueTracker.hasIssue() && userInitiated) {
                downloaded = downloadTrackFromArchiveCatalog(context, id, title, artist, networkIssueTracker, progressReporter)
                noNetworkEncountered = noNetworkEncountered || networkIssueTracker.hasIssue()
                Log.d(TAG, "track:archive_result id=$id ok=$downloaded")
            } else if (!downloaded) {
                if (networkIssueTracker.hasIssue()) {
                    Log.w(TAG, "track:skip_archive_due_network id=$id")
                } else {
                    Log.d(TAG, "track:skip_archive_auto_mode id=$id")
                }
            }

            if (!downloaded) {
                if (noNetworkEncountered) {
                    Log.w(TAG, "track:failed_network id=$id")
                    return TrackDownloadResult.failed(id, title, true)
                }
                if (userInitiated) {
                    Log.w(TAG, "track:failed_manual_retry id=$id")
                } else {
                    val failures = OfflineRestrictionStore.getAutomaticNewPipeFailureCount(context, id)
                    Log.w(TAG, "track:failed_auto id=$id auto_failures=$failures/$MAX_NEWPIPE_AUTOMATIC_FAILURES")
                }
                return TrackDownloadResult.failed(id, title, noNetworkEncountered)
            }

            if (!validateDownloadedTrackFile(context, id, expectedDurationLabel)) {
                Log.w(TAG, "track:validation_failed id=$id expectedDuration='$expectedDurationLabel'")
                return TrackDownloadResult.failed(id, title, noNetworkEncountered)
            }

            progressReporter.onProgress(1f)
            OfflineRestrictionStore.unmarkRestricted(context, id)
            restrictedIds.remove(id)
            OfflineRestrictionStore.clearAutomaticNewPipeFailures(context, id)
            OfflineAudioStore.markOfflineAudioState(id, true)
            Log.d(TAG, "track:success id=$id")
            return TrackDownloadResult.downloaded(id, title, noNetworkEncountered)

        } finally {
            if (addedToActive) {
                activeTrackIds.remove(id)
            }
            activeTrackProgressFractions.remove(id)
        }
    }

    private fun snapshotActiveProgress(
        activeTrackIds: Set<String>,
        activeTrackProgressFractions: Map<String, Float>
    ): ActiveProgressSnapshot {
        synchronized(activeTrackIds) {
            val ids = activeTrackIds.toTypedArray()
            val fractions = FloatArray(ids.size) { i ->
                val value = activeTrackProgressFractions[ids[i]]
                value?.coerceIn(0f, 1f) ?: 0f
            }
            return ActiveProgressSnapshot(ids, fractions)
        }
    }

    private fun normalizePlaylistTitle(playlistTitle: String?, playlistId: String?): String {
        val resolvedTitle = playlistTitle?.trim() ?: ""
        if (resolvedTitle.isNotEmpty()) return resolvedTitle

        val resolvedId = playlistId?.trim() ?: ""
        if (resolvedId.isNotEmpty()) return resolvedId

        return "Playlist"
    }

    private fun isInternetAvailable(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false

        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
               capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    private fun isDownloadAllowedByCurrentNetworkPolicy(context: Context, networkIssueTracker: NetworkIssueTracker): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false

        val internetReady = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        if (!internetReady) return false

        val prefs = context.getSharedPreferences(CloudSyncManager.PREFS_SETTINGS, Context.MODE_PRIVATE)
        val allowMobileData = prefs.getBoolean(CloudSyncManager.KEY_OFFLINE_DOWNLOAD_ALLOW_MOBILE_DATA, false)

        if (allowMobileData) return true

        val usesCellular = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
        val usesWifi = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        val usesEthernet = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)

        if (usesCellular && !usesWifi && !usesEthernet) {
            networkIssueTracker.markIssue()
            return false
        }

        return true
    }

    private fun safeArrayValue(values: Array<String>?, index: Int): String {
        if (values == null || index < 0 || index >= values.size) return ""
        return values[index].trim()
    }

    private fun downloadTrackFromYouTubeNewPipe(
        context: Context,
        videoId: String,
        targetBitrate: Int,
        networkIssueTracker: NetworkIssueTracker,
        progressReporter: ProgressReporter?
    ): Boolean {
        if (!ensureNewPipeInitialized()) {
            Log.w(TAG, "newpipe:init_failed id=$videoId")
            return false
        }

        val sourceCandidates = buildYouTubeSourceCandidates(videoId)
        for (sourceUrl in sourceCandidates) {
            try {
                if (!isDownloadAllowedByCurrentNetworkPolicy(context, networkIssueTracker)) {
                    networkIssueTracker.markIssue()
                    Log.w(TAG, "newpipe:blocked_mobile_data_policy id=$videoId source=${sanitizeUrlForLog(sourceUrl)}")
                    return false
                }

                val extractor = ServiceList.YouTube.getStreamExtractor(sourceUrl)
                extractor.fetchPage()

                val rawStreams = extractor.audioStreams
                rawStreams?.forEach { rs ->
                    val fName = rs.format?.name ?: "unknown"
                    val isOpus = "WEBMA" in fName || "OPUS" in fName
                    Log.d(TAG, "newpipe:raw_stream videoId=$videoId format=$fName isWebm=$isOpus bitrate=${rs.bitrate} averageBitrate=${rs.averageBitrate} channels=${rs.itagItem?.audioChannels ?: -1}")
                }

                val candidates = selectAudioStreamsByPriority(rawStreams, targetBitrate)
                if (candidates.isEmpty()) {
                    Log.w(TAG, "newpipe:no_audio_stream (This track might be restricted or region-locked) id=$videoId url=${sanitizeUrlForLog(sourceUrl)}")
                    continue
                }

                for (selected in candidates) {
                    if (selected == null || selected.content.isNullOrEmpty()) continue

                    val directUrl = selected.content
                    val format = selected.format?.name?.lowercase(Locale.US) ?: "unknown"
                    val fName = selected.format?.name?.uppercase(Locale.US) ?: "unknown"
                    val isWebm = "WEBMA" in fName || "OPUS" in fName
                    
                    val target = OfflineAudioStore.getOfflineAudioFileForFormat(context, videoId, isWebm)
                    val parent = target.parentFile
                    if (parent != null && !parent.exists()) {
                        parent.mkdirs()
                    }

                    Log.d(TAG, "newpipe:attempt id=$videoId source=${sanitizeUrlForLog(sourceUrl)} format=$format target_bitrate=$targetBitrate bitrate=${audioBitrate(selected)} direct=${sanitizeUrlForLog(directUrl)}")

                    if (!downloadFromUrl(context, directUrl, target, null, networkIssueTracker, progressReporter)) continue

                    val channelCount = readAudioChannelCount(target)
                    if (channelCount >= MIN_STEREO_CHANNELS) {
                        Log.d(TAG, "newpipe:success id=$videoId format=$format channels=$channelCount")
                        return true
                    }

                    Log.w(TAG, "newpipe:reject_non_stereo id=$videoId format=$format channels=$channelCount")
                    OfflineAudioStore.deleteOfflineAudio(context, videoId)
                }
            } catch (e: Exception) {
                if (isLikelyNetworkException(e)) {
                    networkIssueTracker.markIssue()
                }
                val errorType = e.javaClass.simpleName
                if ("SignInConfirmNotBotException" == errorType) {
                    Log.w(TAG, "newpipe:blocked id=$videoId message=${e.message}")
                } else {
                    Log.w(TAG, "newpipe:exception id=$videoId url=${sanitizeUrlForLog(sourceUrl)} errorType=$errorType message=${e.message}", e)
                }
            }
        }

        Log.w(TAG, "newpipe:all_candidates_failed id=$videoId")
        return false
    }

    private fun resolveTargetBitrate(context: Context): Int {
        val prefs = context.getSharedPreferences(CloudSyncManager.PREFS_SETTINGS, Context.MODE_PRIVATE)
        return when (prefs.getString(CloudSyncManager.KEY_OFFLINE_DOWNLOAD_QUALITY, CloudSyncManager.DOWNLOAD_QUALITY_VERY_HIGH)) {
            CloudSyncManager.DOWNLOAD_QUALITY_LOW -> TARGET_M4A_BITRATE_LOW
            CloudSyncManager.DOWNLOAD_QUALITY_MEDIUM -> TARGET_M4A_BITRATE_MEDIUM
            CloudSyncManager.DOWNLOAD_QUALITY_HIGH -> TARGET_M4A_BITRATE_HIGH
            CloudSyncManager.DOWNLOAD_QUALITY_VERY_HIGH -> TARGET_M4A_BITRATE_VERY_HIGH
            else -> TARGET_M4A_BITRATE_MEDIUM
        }
    }

    private fun ensureNewPipeInitialized(): Boolean {
        if (newPipeInitialized) return true

        synchronized(NEWPIPE_INIT_LOCK) {
            if (newPipeInitialized) return true

            return try {
                NewPipe.init(NewPipeHttpDownloader.getInstance())
                newPipeInitialized = true
                Log.d(TAG, "newpipe:init_success")
                true
            } catch (e: Exception) {
                Log.e(TAG, "newpipe:init_exception message=${e.message}", e)
                false
            }
        }
    }

    private fun selectBestAudioStream(streams: List<AudioStream>?, targetBitrate: Int): AudioStream? {
        if (streams.isNullOrEmpty()) return null

        val m4aCandidates = mutableListOf<AudioStream>()
        val fallbackCandidates = mutableListOf<AudioStream>()
        
        for (stream in streams) {
            if (stream.content.isNullOrEmpty()) continue

            if (stream.format == MediaFormat.M4A) {
                m4aCandidates.add(stream)
            } else {
                fallbackCandidates.add(stream)
            }
        }

        selectFastestCandidate(m4aCandidates, targetBitrate)?.let { return it }
        selectFastestCandidate(fallbackCandidates, targetBitrate)?.let { return it }

        if (m4aCandidates.isEmpty() && fallbackCandidates.isEmpty()) return null

        return if (m4aCandidates.isNotEmpty()) m4aCandidates[0] else fallbackCandidates[0]
    }

    private fun selectAudioStreamsByPriority(streams: List<AudioStream>?, targetBitrate: Int): List<AudioStream> {
        if (streams.isNullOrEmpty()) return emptyList()

        val opusCandidates = mutableListOf<AudioStream>()
        val m4aCandidates = mutableListOf<AudioStream>()
        val fallbackCandidates = mutableListOf<AudioStream>()
        
        for (stream in streams) {
            if (stream.content.isNullOrEmpty()) continue

            val fName = stream.format?.name?.uppercase(Locale.US) ?: "unknown"
            val isWebm = "WEBMA" in fName || "OPUS" in fName

            val declaredChannels = extractDeclaredAudioChannelCount(stream)
            if (declaredChannels in 1 until MIN_STEREO_CHANNELS) {
                if (isWebm) {
                    Log.d(TAG, "newpipe:bypassing_channel_check_for_opus channels=$declaredChannels")
                } else {
                    Log.d(TAG, "newpipe:skip_declared_non_stereo format=${stream.format?.name ?: "unknown"} channels=$declaredChannels bitrate=${audioBitrate(stream)}")
                    continue
                }
            }

            if (isWebm) {
                opusCandidates.add(stream)
            } else if ("M4A" in fName) {
                m4aCandidates.add(stream)
            } else {
                fallbackCandidates.add(stream)
            }
        }

        val ordered = mutableListOf<AudioStream>()
        appendCandidatePriorityOrder(ordered, opusCandidates, targetBitrate)
        appendCandidatePriorityOrder(ordered, m4aCandidates, targetBitrate)
        appendCandidatePriorityOrder(ordered, fallbackCandidates, targetBitrate)

        if (ordered.isEmpty()) {
            ordered.addAll(opusCandidates)
            ordered.addAll(m4aCandidates)
            ordered.addAll(fallbackCandidates)
        }

        return ordered
    }

    private fun appendCandidatePriorityOrder(
        destination: MutableList<AudioStream>,
        source: List<AudioStream>,
        targetBitrate: Int
    ) {
        var bestUnderTarget: AudioStream? = null
        var bestUnderTargetBitrate = -1

        var lowestOverTarget: AudioStream? = null
        var lowestOverTargetBitrate = Int.MAX_VALUE

        var firstUnknownBitrate: AudioStream? = null

        for (stream in source) {
            val bitrate = audioBitrate(stream)
            if (bitrate <= 0) {
                if (firstUnknownBitrate == null) firstUnknownBitrate = stream
                continue
            }

            if (bitrate <= targetBitrate) {
                if (bitrate > bestUnderTargetBitrate) {
                    bestUnderTargetBitrate = bitrate
                    bestUnderTarget = stream
                }
                continue
            }

            if (bitrate < lowestOverTargetBitrate) {
                lowestOverTargetBitrate = bitrate
                lowestOverTarget = stream
            }
        }

        bestUnderTarget?.let { destination.add(it) }
        
        if (lowestOverTarget != null && lowestOverTarget != bestUnderTarget) {
            destination.add(lowestOverTarget)
        }
        
        if (firstUnknownBitrate != null && firstUnknownBitrate != bestUnderTarget && firstUnknownBitrate != lowestOverTarget) {
            destination.add(firstUnknownBitrate)
        }

        for (stream in source) {
            if (!destination.contains(stream)) {
                destination.add(stream)
            }
        }
    }

    private fun selectFastestCandidate(candidates: List<AudioStream>, targetBitrate: Int): AudioStream? {
        if (candidates.isEmpty()) return null

        var bestUnderTarget: AudioStream? = null
        var bestUnderTargetBitrate = -1

        var lowestOverTarget: AudioStream? = null
        var lowestOverTargetBitrate = Int.MAX_VALUE

        var firstUnknownBitrate: AudioStream? = null

        for (stream in candidates) {
            val bitrate = audioBitrate(stream)

            if (bitrate <= 0) {
                if (firstUnknownBitrate == null) firstUnknownBitrate = stream
                continue
            }

            if (bitrate <= targetBitrate) {
                if (bitrate > bestUnderTargetBitrate) {
                    bestUnderTargetBitrate = bitrate
                    bestUnderTarget = stream
                }
                continue
            }

            if (bitrate < lowestOverTargetBitrate) {
                lowestOverTargetBitrate = bitrate
                lowestOverTarget = stream
            }
        }

        return bestUnderTarget ?: lowestOverTarget ?: firstUnknownBitrate
    }

    private fun audioBitrate(stream: AudioStream?): Int {
        if (stream == null) return -1

        val bitrate = stream.bitrate
        if (bitrate > 0) return bitrate

        val averageBitrate = stream.averageBitrate
        if (averageBitrate > 0) return averageBitrate

        return -1
    }

    private fun extractDeclaredAudioChannelCount(stream: AudioStream?): Int {
        if (stream == null) return -1
        return try {
            val channels = stream.itagItem?.audioChannels ?: -1
            if (channels > 0) channels else -1
        } catch (ignored: Throwable) {
            -1
        }
    }

    private fun buildYouTubeSourceCandidates(videoId: String): Array<String> {
        val escapedId = Uri.encode(videoId)
        return arrayOf(
            "https://music.youtube.com/watch?v=$escapedId",
            "https://www.youtube.com/watch?v=$escapedId",
            "https://www.youtube.com/embed/$escapedId"
        )
    }

    private fun downloadTrackFromArchiveCatalog(
        context: Context,
        videoId: String,
        title: String,
        artist: String,
        networkIssueTracker: NetworkIssueTracker,
        progressReporter: ProgressReporter?
    ): Boolean {
        val target = OfflineAudioStore.getOfflineAudioFile(context, videoId)
        val parent = target.parentFile ?: return false
        
        if (!parent.exists() && !parent.mkdirs()) return false

        val archiveUrl = resolveArchiveAudioUrl(title, artist, networkIssueTracker)
        if (archiveUrl.isEmpty()) {
            Log.w(TAG, "archive:no_match id=$videoId title=$title artist=$artist")
            return false
        }

        Log.d(TAG, "archive:downloading id=$videoId url=${sanitizeUrlForLog(archiveUrl)}")
        return downloadFromUrl(context, archiveUrl, target, null, networkIssueTracker, progressReporter)
    }

    private fun resolveArchiveAudioUrl(title: String, artist: String, networkIssueTracker: NetworkIssueTracker): String {
        val query = buildArchiveSearchQuery(title, artist)
        if (query.isEmpty()) return ""

        Log.d(TAG, "archive:query=$query")

        val searchUri = Uri.parse("https://archive.org/advancedsearch.php")
            .buildUpon()
            .appendQueryParameter("q", query)
            .appendQueryParameter("fl[]", "identifier")
            .appendQueryParameter("rows", "5")
            .appendQueryParameter("page", "1")
            .appendQueryParameter("output", "json")
            .build()

        val body = readTextResponse(searchUri.toString(), "application/json", networkIssueTracker)
        if (body.isEmpty()) return ""

        try {
            val response = JSONObject(body).optJSONObject("response") ?: return ""
            val docs = response.optJSONArray("docs")

            if (docs == null || docs.length() == 0) {
                Log.d(TAG, "archive:no_docs")
                return ""
            }

            Log.d(TAG, "archive:docs_count=${docs.length()}")

            for (i in 0 until docs.length()) {
                val doc = docs.optJSONObject(i) ?: continue
                val identifier = doc.optString("identifier", "").trim()
                if (identifier.isEmpty()) continue

                val audioUrl = resolveArchiveDownloadUrl(identifier, title, artist, networkIssueTracker)
                if (audioUrl.isNotEmpty()) {
                    Log.d(TAG, "archive:resolved identifier=$identifier")
                    return audioUrl
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "archive:resolve_exception message=${e.message}", e)
        }

        return ""
    }

    private fun buildArchiveSearchQuery(title: String, artist: String): String {
        val cleanTitle = sanitizeSearchText(title)
        if (cleanTitle.isEmpty()) return ""

        val query = java.lang.StringBuilder("mediatype:(audio)")
        query.append(" AND title:(\"").append(cleanTitle).append("\")")

        val cleanArtist = sanitizeSearchText(artist)
        if (cleanArtist.isNotEmpty()) {
            query.append(" AND creator:(\"").append(cleanArtist).append("\")")
        }

        return query.toString()
    }

    private fun sanitizeSearchText(raw: String): String {
        val input = raw.trim()
        if (input.isEmpty()) return ""

        val builder = java.lang.StringBuilder(input.length)
        var previousWasSpace = false

        for (i in input.indices) {
            var c = input[i]
            val valid = c.isLetterOrDigit() || c.isWhitespace() || c == '-' || c == '_' || c == '.'

            if (!valid) c = ' '

            if (c.isWhitespace()) {
                if (!previousWasSpace) {
                    builder.append(' ')
                    previousWasSpace = true
                }
            } else {
                builder.append(c)
                previousWasSpace = false
            }
        }

        return builder.toString().trim()
    }

    private fun resolveArchiveDownloadUrl(
        identifier: String,
        expectedTitle: String,
        expectedArtist: String,
        networkIssueTracker: NetworkIssueTracker
    ): String {
        val metadataUrl = "https://archive.org/metadata/${Uri.encode(identifier)}"
        val body = readTextResponse(metadataUrl, "application/json", networkIssueTracker)
        if (body.isEmpty()) return ""

        try {
            val root = JSONObject(body)
            val metadata = root.optJSONObject("metadata")
            val metadataTitle = metadata?.optString("title", "") ?: ""
            val metadataCreator = metadata?.optString("creator", "") ?: ""
            
            if (!isLikelyMatch(expectedTitle, expectedArtist, metadataTitle, metadataCreator, identifier)) {
                return ""
            }

            val files = root.optJSONArray("files")
            if (files == null || files.length() == 0) return ""

            for (i in 0 until files.length()) {
                val file = files.optJSONObject(i) ?: continue
                val name = file.optString("name", "").trim()
                if (!isSupportedAudioFile(name)) continue

                return "https://archive.org/download/${Uri.encode(identifier)}/${Uri.encode(name, "/")}"
            }
        } catch (ignored: Exception) {
        }
        return ""
    }

    private fun isSupportedAudioFile(fileName: String): Boolean {
        val lower = fileName.lowercase(Locale.US)
        return lower.endsWith(".mp3") ||
               lower.endsWith(".m4a") ||
               lower.endsWith(".aac") ||
               lower.endsWith(".ogg") ||
               lower.endsWith(".wav") ||
               lower.endsWith(".flac")
    }

    private fun isLikelyMatch(
        expectedTitle: String,
        expectedArtist: String,
        metadataTitle: String,
        metadataCreator: String,
        identifier: String
    ): Boolean {
        val target = normalizeForMatch(expectedTitle)
        if (target.isEmpty()) return true

        val haystack = normalizeForMatch("$metadataTitle $metadataCreator $identifier")
        if (haystack.isEmpty()) return false

        val tokens = target.split(" ")
        var considered = 0
        var hits = 0

        for (token in tokens) {
            if (token.length < 3) continue
            considered++
            if (haystack.contains(token)) hits++
        }

        if (considered == 0) return haystack.contains(target)

        val neededHits = minOf(2, considered)
        if (hits >= neededHits) return true

        val artistToken = normalizeForMatch(expectedArtist)
        return artistToken.isNotEmpty() && haystack.contains(artistToken) && hits >= 1
    }

    private fun normalizeForMatch(text: String?): String {
        if (text == null) return ""

        val raw = text.lowercase(Locale.US)
        val builder = java.lang.StringBuilder(raw.length)
        var previousWasSpace = false

        for (i in raw.indices) {
            val c = raw[i]
            if (c.isLetterOrDigit()) {
                builder.append(c)
                previousWasSpace = false
                continue
            }

            if (!previousWasSpace) {
                builder.append(' ')
                previousWasSpace = true
            }
        }

        return builder.toString().trim()
    }

    private fun readTextResponse(
        urlValue: String,
        accept: String,
        networkIssueTracker: NetworkIssueTracker
    ): String {
        var connection: HttpURLConnection? = null

        try {
            if (!isDownloadAllowedByCurrentNetworkPolicy(applicationContext, networkIssueTracker)) return ""

            val url = URL(urlValue)
            connection = url.openConnection() as HttpURLConnection
            connection.apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                setRequestProperty("User-Agent", "Sleppify-Offline/1.0")
                setRequestProperty("Accept", accept)
                useCaches = false
            }

            val code = connection.responseCode
            if (code !in 200..299) {
                Log.w(TAG, "http:get_non_2xx code=$code url=${sanitizeUrlForLog(urlValue)}")
                return ""
            }

            BufferedInputStream(connection.inputStream).use { `in` ->
                ByteArrayOutputStream().use { out ->
                    val buffer = ByteArray(8192)
                    var read: Int
                    while (`in`.read(buffer).also { read = it } != -1) {
                        out.write(buffer, 0, read)
                    }
                    return out.toString(StandardCharsets.UTF_8.name())
                }
            }
        } catch (e: Exception) {
            if (isLikelyNetworkException(e)) {
                networkIssueTracker.markIssue()
            }
            Log.e(TAG, "http:get_exception url=${sanitizeUrlForLog(urlValue)} message=${e.message}", e)
            return ""
        } finally {
            connection?.disconnect()
        }
    }

    private fun downloadFromUrl(
        context: Context,
        urlValue: String,
        target: File,
        requestHeaders: Map<String, String>?,
        networkIssueTracker: NetworkIssueTracker,
        progressReporter: ProgressReporter?
    ): Boolean {
        val temp = File(target.absolutePath + ".tmp")

        for (attempt in 1..DOWNLOAD_MAX_RETRIES) {
            var connection: HttpURLConnection? = null
            val startedAt = System.currentTimeMillis()

            try {
                if (!isDownloadAllowedByCurrentNetworkPolicy(context, networkIssueTracker)) {
                    Log.w(TAG, "http:blocked_mobile_data_policy attempt=$attempt/$DOWNLOAD_MAX_RETRIES")
                    return false
                }

                var resumeFromBytes = if (temp.isFile) maxOf(0L, temp.length()) else 0L

                val url = URL(urlValue)
                connection = url.openConnection() as HttpURLConnection
                connection.apply {
                    requestMethod = "GET"
                    connectTimeout = CONNECT_TIMEOUT_MS
                    readTimeout = READ_TIMEOUT_MS
                    setRequestProperty("User-Agent", "Sleppify-Offline/1.0")
                    setRequestProperty("Accept-Encoding", "identity")
                    setRequestProperty("Connection", if (attempt > 1) "close" else "keep-alive")
                    applyResolvedRequestHeaders(this, requestHeaders)
                    if (resumeFromBytes > 0L) {
                        setRequestProperty("Range", "bytes=$resumeFromBytes-")
                    }
                    useCaches = false
                }

                val code = connection.responseCode

                if (code == 416 && resumeFromBytes > 0L) {
                    val totalBytesFromHeader = parseTotalBytesFromContentRange(connection.getHeaderField("Content-Range"))
                    if (totalBytesFromHeader > 0L && totalBytesFromHeader == resumeFromBytes) {
                        if (target.exists() && !target.delete()) return false
                        if (temp.renameTo(target)) {
                            Log.d(TAG, "http:download_ok_from_partial bytes=${target.length()} attempt=$attempt/$DOWNLOAD_MAX_RETRIES url=${sanitizeUrlForLog(urlValue)}")
                            return true
                        }
                    }
                    temp.delete()
                    resumeFromBytes = 0L
                }

                if (code !in 200..299) {
                    val shouldRetry = attempt < DOWNLOAD_MAX_RETRIES && shouldRetryHttpCode(code)
                    Log.w(TAG, "http:download_non_2xx code=$code attempt=$attempt/$DOWNLOAD_MAX_RETRIES retry=$shouldRetry resumeFrom=$resumeFromBytes url=${sanitizeUrlForLog(urlValue)}")

                    if (shouldRetry) {
                        SystemClock.sleep(DOWNLOAD_RETRY_BACKOFF_MS * attempt)
                        continue
                    }
                    return false
                }

                val appending = resumeFromBytes > 0L && code == HttpURLConnection.HTTP_PARTIAL
                if (resumeFromBytes > 0L && !appending) {
                    if (temp.exists() && !temp.delete()) return false
                    resumeFromBytes = 0L
                }

                val contentLength = connection.contentLength.toLong()
                val expectedTotalBytes = if (contentLength > 0L) {
                    if (appending) resumeFromBytes + contentLength else contentLength
                } else -1L

                BufferedInputStream(connection.inputStream).use { `in` ->
                    FileOutputStream(temp, appending).use { out ->
                        val buffer = ByteArray(65536)
                        var read: Int
                        var writtenBytes = resumeFromBytes
                        var nextProgressLogAt = maxOf(DOWNLOAD_PROGRESS_LOG_STEP_BYTES, writtenBytes + DOWNLOAD_PROGRESS_LOG_STEP_BYTES)
                        
                        while (`in`.read(buffer).also { read = it } != -1) {
                            out.write(buffer, 0, read)
                            writtenBytes += read

                            if (!isDownloadAllowedByCurrentNetworkPolicy(context, networkIssueTracker)) {
                                Log.w(TAG, "http:blocked_mobile_data_policy_mid_download bytes=$writtenBytes url=${sanitizeUrlForLog(urlValue)}")
                                return false
                            }

                            if (progressReporter != null && expectedTotalBytes > 0L) {
                                progressReporter.onProgress(writtenBytes / expectedTotalBytes.toFloat())
                            }
                            
                            if (writtenBytes >= nextProgressLogAt) {
                                Log.d(TAG, "http:download_progress bytes=$writtenBytes attempt=$attempt/$DOWNLOAD_MAX_RETRIES resume=$appending url=${sanitizeUrlForLog(urlValue)}")
                                nextProgressLogAt += DOWNLOAD_PROGRESS_LOG_STEP_BYTES
                            }
                        }
                        out.flush()

                        if (expectedTotalBytes > 0L && writtenBytes < expectedTotalBytes) {
                            val shouldRetry = attempt < DOWNLOAD_MAX_RETRIES
                            Log.w(TAG, "http:truncated_download bytes=$writtenBytes expected=$expectedTotalBytes attempt=$attempt/$DOWNLOAD_MAX_RETRIES retry=$shouldRetry url=${sanitizeUrlForLog(urlValue)}")
                            if (shouldRetry) {
                                SystemClock.sleep(DOWNLOAD_RETRY_BACKOFF_MS * attempt)
                                continue // Need to break inner, but Kotlin continue labeled is not robust here. It's safe since it continues the outer loop visually... wait, continue in use{} block doesn't work for outer 'for'.
                                // We must use a label or return.
                            }
                        }
                    }
                } // End of inputStream use 
                
                // Handling truncated case outside the use block 
                if (expectedTotalBytes > 0L && temp.length() < expectedTotalBytes) {
                    if (attempt < DOWNLOAD_MAX_RETRIES) {
                        SystemClock.sleep(DOWNLOAD_RETRY_BACKOFF_MS * attempt)
                        continue
                    }
                    return false
                }

                if (!temp.isFile || temp.length() <= 0L) {
                    temp.delete()
                    return false
                }

                if (target.exists() && !target.delete()) return false

                if (!temp.renameTo(target)) return false

                val elapsedMs = maxOf(1L, System.currentTimeMillis() - startedAt)
                Log.d(TAG, "http:download_ok bytes=${target.length()} elapsedMs=$elapsedMs attempt=$attempt/$DOWNLOAD_MAX_RETRIES resumedFrom=$resumeFromBytes url=${sanitizeUrlForLog(urlValue)}")
                return true
            } catch (e: Exception) {
                if (isCancellationInterruption(e)) {
                    Log.w(TAG, "http:download_interrupted_cancelled url=${sanitizeUrlForLog(urlValue)} attempt=$attempt/$DOWNLOAD_MAX_RETRIES partialBytes=${if (temp.isFile) temp.length() else 0L} message=${e.message}")
                    Thread.currentThread().interrupt()
                    return false
                }

                if (isLikelyNetworkException(e)) {
                    networkIssueTracker.markIssue()
                }
                val isNoSpace = isNoSpaceLeftOnDeviceException(e)
                val shouldRetry = !isNoSpace && attempt < DOWNLOAD_MAX_RETRIES && isTransientDownloadException(e)

                Log.e(TAG, "http:download_exception url=${sanitizeUrlForLog(urlValue)} attempt=$attempt/$DOWNLOAD_MAX_RETRIES retry=$shouldRetry nospace=$isNoSpace partialBytes=${if (temp.isFile) temp.length() else 0L} message=${e.message}", e)

                if (shouldRetry) {
                    SystemClock.sleep(DOWNLOAD_RETRY_BACKOFF_MS * attempt)
                    continue
                }

                if (temp.exists() && !shouldRetry) {
                    temp.delete()
                }
                return false
            } finally {
                connection?.disconnect()
            }
        }

        return false
    }

    private fun isCancellationInterruption(throwable: Throwable?): Boolean {
        if (isStopped || Thread.currentThread().isInterrupted) return true

        var cursor = throwable
        while (cursor != null) {
            if (cursor is InterruptedException) return true

            if (cursor is java.io.InterruptedIOException && cursor !is java.net.SocketTimeoutException) {
                val message = cursor.message
                if (message.isNullOrEmpty() || message.lowercase(Locale.US).contains("thread interrupted")) {
                    return true
                }
            }
            cursor = cursor.cause
        }
        return false
    }

    private fun validateDownloadedTrackFile(context: Context, videoId: String, expectedDurationLabel: String?): Boolean {
        val audioFile = OfflineAudioStore.getExistingOfflineAudioFile(context, videoId)
        if (!audioFile.isFile) return false

        val sizeBytes = maxOf(0L, audioFile.length())
        if (sizeBytes < MIN_VALID_AUDIO_FILE_BYTES) {
            OfflineAudioStore.deleteOfflineAudio(context, videoId)
            return false
        }

        val actualDurationSeconds = readAudioDurationSeconds(audioFile)
        if (actualDurationSeconds in 0..<DOWNLOAD_DURATION_MIN_SAFE_SECONDS) {
            OfflineAudioStore.deleteOfflineAudio(context, videoId)
            return false
        }

        val channelCount = readAudioChannelCount(audioFile)
        if (channelCount < MIN_STEREO_CHANNELS) {
            Log.w(TAG, "track:non_stereo_rejected id=$videoId channels=$channelCount")
            OfflineAudioStore.deleteOfflineAudio(context, videoId)
            return false
        }

        val expectedDurationSeconds = parseDurationSeconds(expectedDurationLabel)
        if (expectedDurationSeconds <= 0) return true

        val minimumAllowedDuration = maxOf(
            DOWNLOAD_DURATION_MIN_SAFE_SECONDS,
            Math.round(expectedDurationSeconds * DOWNLOAD_DURATION_MATCH_RATIO) - DOWNLOAD_DURATION_LEEWAY_SECONDS
        )

        if (actualDurationSeconds < 0 || actualDurationSeconds >= minimumAllowedDuration) return true

        Log.w(TAG, "track:duration_mismatch id=$videoId expectedSec=$expectedDurationSeconds actualSec=$actualDurationSeconds minAllowedSec=$minimumAllowedDuration")
        OfflineAudioStore.deleteOfflineAudio(context, videoId)
        return false
    }

    private fun readAudioDurationSeconds(file: File): Int {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            val durationMsValue = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            if (durationMsValue.isNullOrEmpty()) return -1
            val durationMs = durationMsValue.trim().toLong()
            if (durationMs <= 0L) -1 else maxOf(1L, durationMs / 1000L).toInt()
        } catch (e: Exception) {
            -1
        } finally {
            try {
                retriever.release()
            } catch (ignored: Exception) {
            }
        }
    }

    private fun readAudioChannelCount(file: File): Int {
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(file.absolutePath)
            val trackCount = extractor.trackCount
            for (i in 0 until trackCount) {
                val format = extractor.getTrackFormat(i)
                if (!format.containsKey(AndroidMediaFormat.KEY_MIME)) continue
                
                val mime = format.getString(AndroidMediaFormat.KEY_MIME)
                if (mime.isNullOrEmpty() || !mime.startsWith("audio/")) continue
                
                if (format.containsKey(AndroidMediaFormat.KEY_CHANNEL_COUNT)) {
                    val channels = format.getInteger(AndroidMediaFormat.KEY_CHANNEL_COUNT)
                    if (channels > 0) return channels
                }
            }
            -1
        } catch (e: Exception) {
            -1
        } finally {
            try {
                extractor.release()
            } catch (ignored: Exception) {
            }
        }
    }

    private fun parseDurationSeconds(durationLabel: String?): Int {
        if (durationLabel.isNullOrEmpty()) return -1

        val parts = durationLabel.trim().split(":")
        return try {
            when (parts.size) {
                2 -> {
                    val minutes = parts[0].toInt()
                    val seconds = parts[1].toInt()
                    (minutes * 60) + seconds
                }
                3 -> {
                    val hours = parts[0].toInt()
                    val minutes = parts[1].toInt()
                    val seconds = parts[2].toInt()
                    (hours * 3600) + (minutes * 60) + seconds
                }
                else -> -1
            }
        } catch (ignored: Exception) {
            -1
        }
    }

    private fun parseTotalBytesFromContentRange(contentRangeHeader: String?): Long {
        if (contentRangeHeader.isNullOrEmpty()) return -1L

        val slash = contentRangeHeader.lastIndexOf('/')
        if (slash < 0 || slash + 1 >= contentRangeHeader.length) return -1L

        val totalPart = contentRangeHeader.substring(slash + 1).trim()
        if (totalPart.isEmpty() || "*" == totalPart) return -1L

        return try {
            totalPart.toLong()
        } catch (ignored: Exception) {
            -1L
        }
    }

    private fun shouldRetryHttpCode(code: Int): Boolean {
        return code == 403 || code == 408 || code == 429 || code in 500..599
    }

    private fun applyResolvedRequestHeaders(connection: HttpURLConnection, requestHeaders: Map<String, String>?) {
        if (requestHeaders.isNullOrEmpty()) return

        for ((key, value) in requestHeaders) {
            if (key.isEmpty() || value.isEmpty()) continue

            val normalized = key.trim().lowercase(Locale.US)
            if ("host" == normalized || "content-length" == normalized || "connection" == normalized || "accept-encoding" == normalized) {
                continue
            }

            connection.setRequestProperty(key, value)
        }
    }

    private fun isTransientDownloadException(exception: Exception): Boolean {
        if (isNoSpaceLeftOnDeviceException(exception)) return false

        var cursor: Throwable? = exception
        while (cursor != null) {
            if (cursor is java.net.SocketTimeoutException ||
                cursor is java.net.SocketException ||
                cursor is java.net.UnknownHostException ||
                cursor is java.net.ConnectException ||
                cursor is javax.net.ssl.SSLException ||
                cursor is java.io.EOFException ||
                cursor is java.io.InterruptedIOException) {
                return true
            }
            cursor = cursor.cause
        }
        return false
    }

    private fun isNoSpaceLeftOnDeviceException(throwable: Throwable?): Boolean {
        var cursor = throwable
        while (cursor != null) {
            val message = cursor.message
            if (message != null) {
                val lower = message.lowercase(Locale.US)
                if ("enospc" in lower || "no space left on device" in lower) {
                    return true
                }
            }
            cursor = cursor.cause
        }
        return false
    }

    private fun isLikelyNetworkException(throwable: Throwable?): Boolean {
        var cursor = throwable
        while (cursor != null) {
            if (cursor is java.net.UnknownHostException ||
                cursor is java.net.SocketTimeoutException ||
                cursor is java.net.ConnectException ||
                cursor is java.net.SocketException ||
                cursor is java.io.InterruptedIOException ||
                cursor is javax.net.ssl.SSLException) {
                return true
            }

            val message = cursor.message
            if (message != null) {
                val normalized = message.lowercase(Locale.US)
                if ("unable to resolve host" in normalized ||
                    "no address associated with hostname" in normalized ||
                    "failed to connect" in normalized ||
                    "connection timed out" in normalized ||
                    "network is unreachable" in normalized) {
                    return true
                }
            }

            cursor = cursor.cause
        }
        return false
    }

    private fun sanitizeUrlForLog(rawUrl: String?): String {
        if (rawUrl.isNullOrEmpty()) return ""

        return try {
            val uri = Uri.parse(rawUrl)
            val scheme = uri.scheme ?: ""
            val host = uri.host ?: ""
            val path = uri.path ?: ""
            "$scheme://$host$path"
        } catch (ignored: Exception) {
            rawUrl
        }
    }

    private fun interface ProgressReporter {
        fun onProgress(progressFraction: Float)
    }

    private class ActiveProgressSnapshot(
        val trackIds: Array<String>,
        val fractions: FloatArray
    )

    private class TrackDownloadResult private constructor(
        val trackId: String,
        val trackTitle: String,
        val downloaded: Boolean,
        val noNetworkEncountered: Boolean
    ) {
        companion object {
            fun downloaded(trackId: String?, trackTitle: String?, noNetworkEncountered: Boolean): TrackDownloadResult {
                return TrackDownloadResult(trackId ?: "", trackTitle ?: "", true, noNetworkEncountered)
            }

            fun failed(trackId: String?, trackTitle: String?, noNetworkEncountered: Boolean = false): TrackDownloadResult {
                return TrackDownloadResult(trackId ?: "", trackTitle ?: "", false, noNetworkEncountered)
            }
        }
    }

    private class NetworkIssueTracker {
        @Volatile
        private var issue = false

        fun markIssue() {
            issue = true
        }

        fun hasIssue(): Boolean = issue
    }
}
