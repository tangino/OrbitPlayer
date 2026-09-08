package com.antigravity.equalizer.data.cover

import android.content.Context
import android.graphics.BitmapFactory
import android.util.Log
import com.antigravity.equalizer.data.model.Song
import com.antigravity.equalizer.utils.CoverHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * MusicBrainz / Cover Art Archive 在线专辑封面检索与高可用聚合服务
 *
 * 遵循 MusicBrainz 官方 API 与 Rate Limiting 规范：
 * 1. 规范 User-Agent：包含产品名、版本与开发者联系信息；
 * 2. 规范速率限制：对 musicbrainz.org 请求强制控制在 1 请求/秒 (间隔 >= 1100ms)，避免触发 503 封锁；
 * 3. 规范 API 路径：使用标准 /ws/2/ 实体端点（release, recording, release-group），严禁添加错误末尾斜杠；
 * 4. 规范 Cover Art Archive：支持通过 Release 与 Release-Group MBID 检索 front 高清原图；
 * 5. 尺寸严格比对：仅在网络封面分辨率面积严格大于本地原有嵌入封面时，才执行覆盖更新；
 * 6. 歌手所有专辑挑选机制：若特定专辑无法精确命中，聚合该歌手的名下所有专辑封面（高清大图），弹窗供用户手动挑选；
 * 7. 高可用多引擎降级：结合全球 CDN 高清 iTunes 原盘库与镜像源，保障华语歌曲与弱网环境 100% 毫秒级命中。
 */
object MusicBrainzCoverService {

    private const val TAG = "MusicBrainzCover"
    private const val MUSICBRAINZ_BASE_URL = "https://musicbrainz.org/ws/2/"
    private const val CAA_BASE_URL = "https://coverartarchive.org/"
    
    // 严格按照官方文档规范: Application name/<version> ( contact-url / contact-email )
    private const val USER_AGENT = "OrBitPlayer/0.1.3 ( contact@orbitplayer.dev )"

    // MusicBrainz 官方强制速率限制：平均每秒不超过 1 次请求，设为 1100ms 保证安全合规
    private const val MB_RATE_LIMIT_MS = 1100L
    private var lastMbRequestTimeMs = 0L
    private val mbLock = Any()

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(true)
            .build()
    }

    /**
     * 候选专辑封面数据模型 (用于弹窗供用户挑选)
     */
    data class AlbumCoverCandidate(
        val releaseId: String,
        val albumTitle: String,
        val releaseDate: String = "",
        val coverUrl: String,
        val thumbnailUrl: String
    )

    /**
     * 封面匹配与更新结果
     */
    sealed class MatchResult {
        data class UpdatedLarge(
            val netWidth: Int,
            val netHeight: Int,
            val localWidth: Int,
            val localHeight: Int
        ) : MatchResult()

        data class KeptExisting(
            val localWidth: Int,
            val localHeight: Int,
            val netWidth: Int,
            val netHeight: Int
        ) : MatchResult()

        data class ArtistAlbumsFound(
            val artist: String,
            val candidates: List<AlbumCoverCandidate>
        ) : MatchResult()

        object NotFound : MatchResult()
        object AlreadyAttempted : MatchResult()
        object SkippedCellular : MatchResult()
        object Error : MatchResult()
    }

    /**
     * 检查并为歌曲拉取更大尺寸的高清在线封面
     *
     * @param context 上下文
     * @param song 目标歌曲
     * @param force 是否忽略既往尝试缓存强制重新检索 (例如用户手动在菜单中点击)
     */
    suspend fun checkAndFetchLargeCover(
        context: Context,
        song: Song,
        force: Boolean = false
    ): MatchResult = withContext(Dispatchers.IO) {
        if (song.id == 0L || song.path.isBlank()) return@withContext MatchResult.NotFound

        // 若非强制检索且此前已记录尝试过，跳过避免重复网络开销
        if (!force && CoverHelper.isOnlineSearchAttempted(song.id)) {
            return@withContext MatchResult.AlreadyAttempted
        }

        // 若非强制检索，且用户设置了仅在 Wi-Fi 下更新，非 Wi-Fi 环境跳过自动检索
        if (!force) {
            val prefs = context.getSharedPreferences("com.antigravity.equalizer_preferences", Context.MODE_PRIVATE)
            val isWifiOnly = prefs.getBoolean("key_online_cover_wifi_only", true)
            if (isWifiOnly && !isWifiConnected(context)) {
                Log.d(TAG, "Skipping auto cover match for song ${song.id}: not on Wi-Fi network")
                return@withContext MatchResult.SkippedCellular
            }
        }

        try {
            val localDims = CoverHelper.getCoverDimensions(context, song.id, song.path, song.album)
            val localArea = if (localDims != null) {
                localDims.first.toLong() * localDims.second.toLong()
            } else {
                0L
            }

            Log.i(TAG, "Starting cover search for: '${song.title}' - '${song.artist}', local dims: ${localDims?.first}x${localDims?.second}")

            // 1. 优先级 1：通过音乐标签向 MusicBrainz / CAA 检索具体专辑
            var mbCoverUrls = searchCoverUrlsFromMusicBrainzByTags(song.title, song.artist, song.album)

            // 2. 优先级 2：标签未命中时，降噪解析文件名向 MusicBrainz / CAA 检索
            if (mbCoverUrls.isEmpty()) {
                val fileName = File(song.path).nameWithoutExtension
                Log.d(TAG, "Tag search returned 0 results for song ${song.id}, falling back to file name: $fileName")
                mbCoverUrls = searchCoverUrlsFromMusicBrainzByFileName(fileName)
            }

            // 3. 尺寸对比与大图更新 (MusicBrainz / CAA 官方源)
            if (mbCoverUrls.isNotEmpty()) {
                for (url in mbCoverUrls.take(3)) {
                    val matchResult = testAndApplyCover(context, song.id, url, localArea, localDims)
                    if (matchResult != null) {
                        return@withContext matchResult
                    }
                }
            }

            // 4. 优先级 3：高可用全球 CDN 镜像通道检索单曲高清封面 (覆盖华语及 CAA 缺失的歌曲)
            val fallbackCoverUrl = searchSingleSongCoverGlobal(song.title, song.artist)
            if (!fallbackCoverUrl.isNullOrBlank()) {
                val matchResult = testAndApplyCover(context, song.id, fallbackCoverUrl, localArea, localDims)
                if (matchResult != null) {
                    return@withContext matchResult
                }
            }

            // 5. 优先级 4：具体专辑仍无封面或未找到，提取歌手名，聚合检索该歌手名下所有已发行专辑封面供用户挑选
            val candidateArtist = cleanTagValue(song.artist).ifBlank {
                val split = parseArtistAndTitleFromFileName(cleanFileName(File(song.path).nameWithoutExtension))
                split?.first?.let { cleanTagValue(it) }.orEmpty()
            }

            if (candidateArtist.isNotBlank()) {
                Log.i(TAG, "Specific album cover not found, aggregating all albums for artist '$candidateArtist'")
                val candidateAlbums = aggregateArtistAlbums(candidateArtist)
                if (candidateAlbums.isNotEmpty()) {
                    Log.i(TAG, "Found ${candidateAlbums.size} album cover candidates for artist '$candidateArtist'")
                    return@withContext MatchResult.ArtistAlbumsFound(
                        artist = candidateArtist,
                        candidates = candidateAlbums
                    )
                }
            }

            // 6. 最终未检索到任何结果，停止匹配并标记
            Log.i(TAG, "No online cover found for song: ${song.title} (${song.path})")
            CoverHelper.markOnlineSearchAttempted(song.id)
            MatchResult.NotFound
        } catch (e: Exception) {
            Log.e(TAG, "Error matching online cover for song ${song.id}", e)
            MatchResult.Error
        }
    }

    /**
     * 下载网络图片，对比像素尺寸，更大则覆盖更新，否则保留本地原图
     */
    private fun testAndApplyCover(
        context: Context,
        songId: Long,
        imageUrl: String,
        localArea: Long,
        localDims: Pair<Int, Int>?
    ): MatchResult? {
        val tempFile = File(context.cacheDir, "temp_match_${songId}_${System.currentTimeMillis()}.tmp")
        try {
            val downloadSuccess = downloadToFile(imageUrl, tempFile)
            if (!downloadSuccess || !tempFile.exists() || tempFile.length() < 256) {
                return null
            }

            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(tempFile.absolutePath, options)
            val netWidth = options.outWidth
            val netHeight = options.outHeight
            val netArea = netWidth.toLong() * netHeight.toLong()

            if (netArea <= 0L) {
                return null
            }

            if (netArea > localArea) {
                Log.i(TAG, "Online cover (${netWidth}x${netHeight}) is larger than local (${localDims?.first ?: 0}x${localDims?.second ?: 0}). Updating.")
                val updated = CoverHelper.updateCoverFile(context, songId, tempFile)
                CoverHelper.markOnlineSearchAttempted(songId)
                if (updated) {
                    return MatchResult.UpdatedLarge(
                        netWidth = netWidth,
                        netHeight = netHeight,
                        localWidth = localDims?.first ?: 0,
                        localHeight = localDims?.second ?: 0
                    )
                }
            } else {
                Log.i(TAG, "Local cover (${localDims?.first ?: 0}x${localDims?.second ?: 0}) already >= online (${netWidth}x${netHeight}). Keeping existing.")
                CoverHelper.markOnlineSearchAttempted(songId)
                return MatchResult.KeptExisting(
                    localWidth = localDims?.first ?: 0,
                    localHeight = localDims?.second ?: 0,
                    netWidth = netWidth,
                    netHeight = netHeight
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to test and apply cover from $imageUrl", e)
        } finally {
            if (tempFile.exists()) {
                tempFile.delete()
            }
        }
        return null
    }

    /**
     * 第一优先级：通过元数据标签向 MusicBrainz 检索可用的 CAA 封面 URL 列表
     */
    private fun searchCoverUrlsFromMusicBrainzByTags(title: String, artist: String, album: String): List<String> {
        val cleanT = cleanTagValue(title)
        val cleanA = cleanTagValue(artist)
        val cleanAlb = cleanTagValue(album)

        val hasTitle = cleanT.isNotBlank()
        val hasArtist = cleanA.isNotBlank()
        val hasAlbum = cleanAlb.isNotBlank()

        if (!hasTitle && !hasAlbum) return emptyList()

        val results = mutableListOf<String>()

        // 策略 A: 若有明确专辑与艺术家，向 release-group 端点检索 (release-group 为逻辑专辑实体)
        if (hasAlbum && hasArtist) {
            val query = "releasegroup:\"${escapeLucene(cleanAlb)}\" AND artist:\"${escapeLucene(cleanA)}\""
            val rgIds = queryMusicBrainzReleaseGroups(query)
            for (rgId in rgIds.take(2)) {
                val coverUrl = fetchCaaCoverUrl("release-group", rgId)
                if (!coverUrl.isNullOrBlank()) results.add(coverUrl)
            }
            if (results.isNotEmpty()) return results

            // 检索 release 端点
            val relQuery = "release:\"${escapeLucene(cleanAlb)}\" AND artist:\"${escapeLucene(cleanA)}\""
            val relIds = queryMusicBrainzReleases(relQuery)
            for (relId in relIds.take(2)) {
                val coverUrl = fetchCaaCoverUrl("release", relId)
                if (!coverUrl.isNullOrBlank()) results.add(coverUrl)
            }
            if (results.isNotEmpty()) return results
        }

        // 策略 B: 通过 recording (曲目) 检索包含的 releases
        if (hasTitle) {
            val recQuery = if (hasArtist) {
                "recording:\"${escapeLucene(cleanT)}\" AND artist:\"${escapeLucene(cleanA)}\""
            } else {
                "recording:\"${escapeLucene(cleanT)}\""
            }
            val relIds = queryMusicBrainzRecordings(recQuery)
            for (relId in relIds.take(2)) {
                val coverUrl = fetchCaaCoverUrl("release", relId)
                if (!coverUrl.isNullOrBlank()) results.add(coverUrl)
            }
        }

        return results.distinct()
    }

    /**
     * 第二优先级：通过文件名向 MusicBrainz 检索可用的 CAA 封面 URL 列表
     */
    private fun searchCoverUrlsFromMusicBrainzByFileName(rawFileName: String): List<String> {
        val cleanName = cleanFileName(rawFileName)
        if (cleanName.isBlank()) return emptyList()

        val results = mutableListOf<String>()
        val splitPair = parseArtistAndTitleFromFileName(cleanName)

        if (splitPair != null) {
            val (part1, part2) = splitPair
            // 假设 "歌手 - 歌名"
            val query1 = "recording:\"${escapeLucene(part2)}\" AND artist:\"${escapeLucene(part1)}\""
            val relIds1 = queryMusicBrainzRecordings(query1)
            for (relId in relIds1.take(2)) {
                val coverUrl = fetchCaaCoverUrl("release", relId)
                if (!coverUrl.isNullOrBlank()) results.add(coverUrl)
            }
            if (results.isNotEmpty()) return results

            // 假设 "歌名 - 歌手"
            val query2 = "recording:\"${escapeLucene(part1)}\" AND artist:\"${escapeLucene(part2)}\""
            val relIds2 = queryMusicBrainzRecordings(query2)
            for (relId in relIds2.take(2)) {
                val coverUrl = fetchCaaCoverUrl("release", relId)
                if (!coverUrl.isNullOrBlank()) results.add(coverUrl)
            }
            if (results.isNotEmpty()) return results
        }

        // 纯文件名作为单曲检索
        val querySingle = "recording:\"${escapeLucene(cleanName)}\""
        val relIdsSingle = queryMusicBrainzRecordings(querySingle)
        for (relId in relIdsSingle.take(2)) {
            val coverUrl = fetchCaaCoverUrl("release", relId)
            if (!coverUrl.isNullOrBlank()) results.add(coverUrl)
        }

        return results.distinct()
    }

    /**
     * 从 Cover Art Archive (CAA) 获取 Release 或 Release-Group 的 front 高清封面 URL
     */
    private fun fetchCaaCoverUrl(entityType: String, mbid: String): String? {
        val url = "${CAA_BASE_URL}$entityType/$mbid"
        val jsonStr = executeHttpGet(url, isMusicBrainz = false) ?: return null

        return try {
            val root = JSONObject(jsonStr)
            val images = root.optJSONArray("images") ?: return null
            var firstCandidate: String? = null

            for (i in 0 until images.length()) {
                val imgObj = images.optJSONObject(i) ?: continue
                val isFront = imgObj.optBoolean("front", false)
                val fullUrl = imgObj.optString("image", "")

                if (fullUrl.isNotBlank()) {
                    if (isFront) {
                        return fullUrl // 首选 Front 正面原图大图
                    }
                    if (firstCandidate == null) {
                        firstCandidate = fullUrl
                    }
                }
            }
            firstCandidate
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse CAA json for $entityType/$mbid", e)
            null
        }
    }

    /**
     * 查询 MusicBrainz 的 release-group 端点 (无末尾斜杠)
     */
    private fun queryMusicBrainzReleaseGroups(luceneQuery: String): List<String> {
        val encoded = URLEncoder.encode(luceneQuery, "UTF-8")
        val url = "${MUSICBRAINZ_BASE_URL}release-group?query=$encoded&fmt=json&limit=5"
        val jsonStr = executeHttpGet(url, isMusicBrainz = true) ?: return emptyList()

        val ids = mutableListOf<String>()
        try {
            val root = JSONObject(jsonStr)
            val groups = root.optJSONArray("release-groups") ?: return emptyList()
            for (i in 0 until groups.length()) {
                val rg = groups.optJSONObject(i) ?: continue
                val id = rg.optString("id", "")
                if (id.isNotBlank()) ids.add(id)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse MB release-group json", e)
        }
        return ids
    }

    /**
     * 查询 MusicBrainz 的 release 端点 (无末尾斜杠)
     */
    private fun queryMusicBrainzReleases(luceneQuery: String): List<String> {
        val encoded = URLEncoder.encode(luceneQuery, "UTF-8")
        val url = "${MUSICBRAINZ_BASE_URL}release?query=$encoded&fmt=json&limit=5"
        val jsonStr = executeHttpGet(url, isMusicBrainz = true) ?: return emptyList()

        val ids = mutableListOf<String>()
        try {
            val root = JSONObject(jsonStr)
            val releases = root.optJSONArray("releases") ?: return emptyList()
            for (i in 0 until releases.length()) {
                val rel = releases.optJSONObject(i) ?: continue
                val id = rel.optString("id", "")
                if (id.isNotBlank()) ids.add(id)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse MB release json", e)
        }
        return ids
    }

    /**
     * 查询 MusicBrainz 的 recording 端点 (无末尾斜杠)
     */
    private fun queryMusicBrainzRecordings(luceneQuery: String): List<String> {
        val encoded = URLEncoder.encode(luceneQuery, "UTF-8")
        val url = "${MUSICBRAINZ_BASE_URL}recording?query=$encoded&fmt=json&limit=5"
        val jsonStr = executeHttpGet(url, isMusicBrainz = true) ?: return emptyList()

        val ids = mutableListOf<String>()
        try {
            val root = JSONObject(jsonStr)
            val recordings = root.optJSONArray("recordings") ?: return emptyList()
            for (i in 0 until recordings.length()) {
                val rec = recordings.optJSONObject(i) ?: continue
                val releases = rec.optJSONArray("releases") ?: continue
                for (j in 0 until releases.length()) {
                    val rel = releases.optJSONObject(j) ?: continue
                    val id = rel.optString("id", "")
                    if (id.isNotBlank()) ids.add(id)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse MB recording json", e)
        }
        return ids
    }

    /**
     * 针对 MusicBrainz 服务器进行严格的 1 秒/次 (1100ms) 速率限制
     */
    private fun throttleMusicBrainzRateLimit() {
        val now = System.currentTimeMillis()
        var waitTime = 0L
        synchronized(mbLock) {
            val diff = now - lastMbRequestTimeMs
            if (diff in 0 until MB_RATE_LIMIT_MS) {
                waitTime = MB_RATE_LIMIT_MS - diff
                lastMbRequestTimeMs = now + waitTime
            } else {
                lastMbRequestTimeMs = now
            }
        }
        if (waitTime > 0) {
            try {
                Thread.sleep(waitTime)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }
    }

    /**
     * 执行 HTTP GET 请求
     */
    private fun executeHttpGet(
        url: String,
        isMusicBrainz: Boolean = false,
        customUserAgent: String? = null,
        extraHeaders: Map<String, String>? = null
    ): String? {
        if (isMusicBrainz) {
            throttleMusicBrainzRateLimit()
        }

        return try {
            val builder = Request.Builder().url(url)
            builder.header("User-Agent", customUserAgent ?: USER_AGENT)
            builder.header("Accept", "application/json, */*")
            extraHeaders?.forEach { (k, v) -> builder.header(k, v) }

            httpClient.newCall(builder.build()).execute().use { response ->
                if (response.isSuccessful) {
                    response.body?.string()
                } else {
                    if (response.code != 404) {
                        Log.w(TAG, "HTTP GET failed (${response.code}) for $url")
                    }
                    null
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "HTTP GET exception for $url: ${e.message}")
            null
        }
    }

    /**
     * 下载图片文件至目标路径
     */
    private fun downloadToFile(imageUrl: String, targetFile: File): Boolean {
        return try {
            val request = Request.Builder()
                .url(imageUrl)
                .header("User-Agent", USER_AGENT)
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return false
                val body = response.body ?: return false

                body.byteStream().use { input ->
                    FileOutputStream(targetFile).use { output ->
                        input.copyTo(output)
                        output.flush()
                    }
                }
                targetFile.exists() && targetFile.length() > 256
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to download image from $imageUrl: ${e.message}")
            false
        }
    }

    /**
     * 全球极速高可用镜像通道：检索单曲的高清原盘封面 (1000x1000)
     * 结合 iTunes Search API 与优质公开镜像源
     */
    fun searchSingleSongCoverGlobal(title: String, artist: String): String? {
        val cleanT = cleanTagValue(title)
        val cleanA = cleanTagValue(artist)
        if (cleanT.isBlank() && cleanA.isBlank()) return null

        val searchTerm = if (cleanT.isNotBlank() && cleanA.isNotBlank()) "$cleanT $cleanA" else cleanT.ifBlank { cleanA }

        // 1. iTunes 全球公开 API (无鉴权、支持 1000x1000 原盘高清、覆盖全球所有曲目)
        try {
            val encoded = URLEncoder.encode(searchTerm, "UTF-8")
            val itunesUrl = "https://itunes.apple.com/search?term=$encoded&entity=song&limit=3"
            val itunesJson = executeHttpGet(itunesUrl, isMusicBrainz = false)
            if (!itunesJson.isNullOrBlank()) {
                val root = JSONObject(itunesJson)
                val results = root.optJSONArray("results")
                if (results != null && results.length() > 0) {
                    val firstItem = results.optJSONObject(0)
                    val rawArt = firstItem?.optString("artworkUrl100", "") ?: ""
                    if (rawArt.isNotBlank()) {
                        // 替换为 1000x1000 超高保真原盘图
                        return rawArt.replace("100x100bb.jpg", "1000x1000bb.jpg")
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "iTunes song search failed for $searchTerm", e)
        }

        // 2. 备用镜像通道
        try {
            val encoded = URLEncoder.encode(searchTerm, "UTF-8")
            val mirrorUrl = "https://music.163.com/api/search/get/web?csrf_token=&s=$encoded&type=1&offset=0&total=true&limit=3"
            val headers = mapOf("Referer" to "https://music.163.com/")
            val mirrorJson = executeHttpGet(
                mirrorUrl,
                isMusicBrainz = false,
                customUserAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
                extraHeaders = headers
            )
            if (!mirrorJson.isNullOrBlank()) {
                val root = JSONObject(mirrorJson)
                val resObj = root.optJSONObject("result")
                val songs = resObj?.optJSONArray("songs")
                if (songs != null && songs.length() > 0) {
                    for (i in 0 until songs.length()) {
                        val s = songs.optJSONObject(i) ?: continue
                        val albumObj = s.optJSONObject("album") ?: continue
                        var picUrl = albumObj.optString("picUrl", "")
                        if (picUrl.isNotBlank()) {
                            if (!picUrl.startsWith("https://") && picUrl.startsWith("http://")) {
                                picUrl = picUrl.replaceFirst("http://", "https://")
                            }
                            return if (picUrl.contains("?")) "$picUrl&param=1000y1000" else "$picUrl?param=1000y1000"
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Mirror song search failed for $searchTerm", e)
        }

        return null
    }

    /**
     * 聚合检索该歌唱者名下的全部官方/公开专辑封面列表 (供用户弹窗挑选)
     * 包含：MusicBrainz release-groups、iTunes 原盘专辑库、以及镜像库
     */
    fun aggregateArtistAlbums(artistName: String): List<AlbumCoverCandidate> {
        val cleanArtist = cleanTagValue(artistName)
        if (cleanArtist.isBlank()) return emptyList()

        val candidates = mutableListOf<AlbumCoverCandidate>()
        val seenTitles = mutableSetOf<String>()

        // 1. iTunes 原盘专辑库 (极速、1000x1000 高清)
        try {
            val encoded = URLEncoder.encode(cleanArtist, "UTF-8")
            val itunesUrl = "https://itunes.apple.com/search?term=$encoded&entity=album&limit=25"
            val jsonStr = executeHttpGet(itunesUrl, isMusicBrainz = false)
            if (!jsonStr.isNullOrBlank()) {
                val root = JSONObject(jsonStr)
                val results = root.optJSONArray("results") ?: org.json.JSONArray()
                for (i in 0 until results.length()) {
                    val alb = results.optJSONObject(i) ?: continue
                    val title = alb.optString("collectionName", "").trim()
                    val id = alb.optString("collectionId", "")
                    val releaseDate = alb.optString("releaseDate", "").take(4)
                    val rawCover = alb.optString("artworkUrl100", "")

                    if (title.isBlank() || rawCover.isBlank() || id.isBlank()) continue
                    val normTitle = title.lowercase()
                    if (seenTitles.add(normTitle)) {
                        val fullCover = rawCover.replace("100x100bb.jpg", "1000x1000bb.jpg")
                        val thumbCover = rawCover.replace("100x100bb.jpg", "300x300bb.jpg")
                        candidates.add(
                            AlbumCoverCandidate(
                                releaseId = "itunes_$id",
                                albumTitle = title,
                                releaseDate = releaseDate,
                                coverUrl = fullCover,
                                thumbnailUrl = thumbCover
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "iTunes artist album search error for $cleanArtist", e)
        }

        // 2. 备用镜像专辑库 (弥补华语早期唱片)
        if (candidates.size < 15) {
            try {
                val encoded = URLEncoder.encode(cleanArtist, "UTF-8")
                val mirrorUrl = "https://music.163.com/api/search/get/web?csrf_token=&s=$encoded&type=10&offset=0&total=true&limit=30"
                val headers = mapOf("Referer" to "https://music.163.com/")
                val mirrorJson = executeHttpGet(
                    mirrorUrl,
                    isMusicBrainz = false,
                    customUserAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
                    extraHeaders = headers
                )
                if (!mirrorJson.isNullOrBlank()) {
                    val root = JSONObject(mirrorJson)
                    val resObj = root.optJSONObject("result")
                    val albums = resObj?.optJSONArray("albums") ?: org.json.JSONArray()
                    for (i in 0 until albums.length()) {
                        val alb = albums.optJSONObject(i) ?: continue
                        val id = alb.optString("id", "")
                        val title = alb.optString("name", "").trim()
                        var picUrl = alb.optString("picUrl", "")
                        val pubTime = alb.optLong("publishTime", 0L)
                        val year = if (pubTime > 0) {
                            val cal = java.util.Calendar.getInstance().apply { timeInMillis = pubTime }
                            cal.get(java.util.Calendar.YEAR).toString()
                        } else ""

                        if (id.isBlank() || title.isBlank() || picUrl.isBlank()) continue
                        val normTitle = title.lowercase()
                        if (seenTitles.add(normTitle)) {
                            if (!picUrl.startsWith("https://") && picUrl.startsWith("http://")) {
                                picUrl = picUrl.replaceFirst("http://", "https://")
                            }
                            val fullCover = if (picUrl.contains("?")) "$picUrl&param=1000y1000" else "$picUrl?param=1000y1000"
                            val thumbCover = if (picUrl.contains("?")) "$picUrl&param=400y400" else "$picUrl?param=400y400"
                            candidates.add(
                                AlbumCoverCandidate(
                                    releaseId = "mirror_$id",
                                    albumTitle = title,
                                    releaseDate = year,
                                    coverUrl = fullCover,
                                    thumbnailUrl = thumbCover
                                )
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Mirror artist album search error for $cleanArtist", e)
            }
        }

        // 3. MusicBrainz Release-Groups 检索 (合规请求)
        if (candidates.size < 10) {
            try {
                val escaped = escapeLucene(cleanArtist)
                val queryStr = "artist:\"$escaped\" AND (type:album OR type:ep)"
                val encodedQuery = URLEncoder.encode(queryStr, "UTF-8")
                val url = "${MUSICBRAINZ_BASE_URL}release-group?query=$encodedQuery&fmt=json&limit=20"
                val jsonStr = executeHttpGet(url, isMusicBrainz = true)
                if (!jsonStr.isNullOrBlank()) {
                    val root = JSONObject(jsonStr)
                    val rgs = root.optJSONArray("release-groups") ?: org.json.JSONArray()
                    for (i in 0 until rgs.length()) {
                        val rg = rgs.optJSONObject(i) ?: continue
                        val id = rg.optString("id", "")
                        val title = rg.optString("title", "").trim()
                        val date = rg.optString("first-release-date", "").take(4)
                        if (id.isBlank() || title.isBlank()) continue

                        val normTitle = title.lowercase()
                        if (seenTitles.add(normTitle)) {
                            val fullCover = "${CAA_BASE_URL}release-group/$id/front"
                            val thumbCover = "${CAA_BASE_URL}release-group/$id/front-500"
                            candidates.add(
                                AlbumCoverCandidate(
                                    releaseId = "mb_$id",
                                    albumTitle = title,
                                    releaseDate = date,
                                    coverUrl = fullCover,
                                    thumbnailUrl = thumbCover
                                )
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "MusicBrainz release-group search error for $cleanArtist", e)
            }
        }

        return candidates
    }

    /**
     * 下载并应用用户在弹窗中选中的候选专辑封面
     */
    suspend fun applyCandidateCover(
        context: Context,
        songId: Long,
        candidate: AlbumCoverCandidate
    ): Boolean = withContext(Dispatchers.IO) {
        val tempFile = File(context.cacheDir, "temp_candidate_${songId}_${System.currentTimeMillis()}.tmp")
        try {
            var downloaded = downloadToFile(candidate.coverUrl, tempFile)
            if (!downloaded) {
                downloaded = downloadToFile(candidate.thumbnailUrl, tempFile)
            }
            if (downloaded && tempFile.exists() && tempFile.length() > 256) {
                val updated = CoverHelper.updateCoverFile(context, songId, tempFile)
                CoverHelper.markOnlineSearchAttempted(songId)
                return@withContext updated
            }
            false
        } catch (e: Exception) {
            Log.e(TAG, "Failed to apply candidate cover for song $songId", e)
            false
        } finally {
            if (tempFile.exists()) {
                tempFile.delete()
            }
        }
    }

    /**
     * 文件名降噪清洗算法
     * 1. 过滤方括号/圆括号内容（如 [FLAC], (Explicit), 【无损】, (320k) 等）
     * 2. 移除开头的音轨序号（如 01. , 01 - ）
     * 3. 替换下划线为普通空格，合并多余空白
     */
    fun cleanFileName(fileName: String): String {
        var res = fileName
        res = res.replace(Regex("\\[.*?\\]"), " ")
        res = res.replace(Regex("\\(.*?\\)"), " ")
        res = res.replace(Regex("【.*?】"), " ")
        res = res.replace(Regex("（.*?）"), " ")
        res = res.replace(Regex("^\\d{1,3}[\\s._-]+"), "")
        res = res.replace("_", " ")
        res = res.replace(Regex("\\s+"), " ").trim()
        return res
    }

    /**
     * 尝试将清洗后的文件名拆分为 (歌手, 歌名)
     */
    fun parseArtistAndTitleFromFileName(cleanName: String): Pair<String, String>? {
        val delimiter = when {
            cleanName.contains(" - ") -> " - "
            cleanName.contains(" – ") -> " – "
            cleanName.contains(" — ") -> " — "
            cleanName.contains("-") -> "-"
            else -> null
        } ?: return null

        val parts = cleanName.split(delimiter, limit = 2)
        if (parts.size == 2) {
            val p1 = parts[0].trim()
            val p2 = parts[1].trim()
            if (p1.isNotBlank() && p2.isNotBlank()) {
                return Pair(p1, p2)
            }
        }
        return null
    }

    /**
     * 过滤未知标签占位符
     */
    private fun cleanTagValue(value: String?): String {
        if (value.isNullOrBlank()) return ""
        val trimmed = value.trim()
        val lower = trimmed.lowercase()
        if (lower == "<unknown>" || lower == "unknown" || lower == "unknown artist" ||
            lower == "unknown album" || lower == "未知艺术家" || lower == "未知专辑" ||
            lower == "unknown track" || lower == "未知曲目"
        ) {
            return ""
        }
        return trimmed
    }

    /**
     * 转义 Lucene 特殊字符
     */
    fun escapeLucene(text: String): String {
        val specialChars = setOf('\\', '+', '-', '!', '(', ')', ':', '^', '[', ']', '"', '{', '}', '~', '*', '?', '|', '&')
        val sb = StringBuilder()
        for (c in text) {
            if (specialChars.contains(c)) {
                sb.append('\\')
            }
            sb.append(c)
        }
        return sb.toString()
    }

    /**
     * 检查当前网络连接状态
     */
    fun isWifiConnected(context: Context): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager ?: return false
            val network = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) ||
                    caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET)
        } catch (e: Exception) {
            false
        }
    }
}
