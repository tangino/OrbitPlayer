package com.orbit.music.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.orbit.music.R
import com.orbit.music.data.model.AudioTechSpecs
import com.orbit.music.data.model.Song
import com.orbit.music.data.model.SongMetadata
import com.orbit.music.data.model.SongMetadataHelper
import com.orbit.music.ui.theme.OrbitTheme
import com.orbit.music.utils.CoverHelper
import com.orbit.music.utils.FastToast
import com.orbit.music.utils.LyricParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

/**
 * 歌曲详细信息弹窗 (遵循现代 Hi-Fi 暗黑质感与参考图布局)
 * 展示内容：大封面(带修改封面浮层)、歌名、歌手、专辑、序号、歌词预览、格式/大小/时长/码率/采样率/位深/声道、文件路径(可复制)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongDetailInfoDialog(
    song: Song,
    onDismissRequest: () -> Unit,
    onChangeCover: (Song) -> Unit,
    onViewLyrics: ((Song) -> Unit)? = null
) {
    val context = LocalContext.current
    val coverVersion by CoverHelper.coverVersion.collectAsState()

    var songMetadata by remember { mutableStateOf<SongMetadata?>(null) }
    var techSpecs by remember { mutableStateOf<AudioTechSpecs?>(null) }
    var firstLyricPreview by remember { mutableStateOf<String?>(null) }
    var fileSizeFormatted by remember { mutableStateOf("") }

    LaunchedEffect(song) {
        withContext(Dispatchers.IO) {
            val specs = SongMetadataHelper.extractTechSpecs(song)
            techSpecs = specs

            val meta = SongMetadataHelper.extractInitialMetadata(song, null)
            songMetadata = meta

            val file = File(song.path)
            val bytes = if (song.size > 0) song.size else if (file.exists()) file.length() else 0L
            val mb = bytes.toDouble() / (1024 * 1024)
            fileSizeFormatted = String.format(Locale.getDefault(), "%.2fMB", mb)

            val lyrics = LyricParser.loadLyricForSong(song.path)
            firstLyricPreview = lyrics.firstOrNull { it.text.isNotBlank() }?.text
        }
    }

    val displayPath = remember(song.path) {
        SongMetadataHelper.formatDisplayPath(song.path)
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        containerColor = OrbitTheme.colors.surfaceDialog,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(top = 12.dp, bottom = 32.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // 顶部关闭按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onDismissRequest,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = OrbitTheme.colors.textSecondary.copy(alpha = 0.85f),
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // 居中大封面与“修改封面”磨砂浮层
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .size(132.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(OrbitTheme.colors.surface)
                    .border(1.dp, OrbitTheme.colors.primary.copy(alpha = 0.15f), RoundedCornerShape(14.dp))
            ) {
                if (!song.albumArtUri.isNullOrBlank()) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(song.albumArtUri)
                            .memoryCacheKey("${song.albumArtUri}_$coverVersion")
                            .diskCacheKey("${song.albumArtUri}_$coverVersion")
                            .build(),
                        contentDescription = song.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = OrbitTheme.colors.primary.copy(alpha = 0.6f),
                            modifier = Modifier.size(54.dp)
                        )
                    }
                }

                // 封面底部修改浮层
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(30.dp)
                        .align(Alignment.BottomCenter)
                        .background(Color.Black.copy(alpha = 0.58f))
                        .clickable { onChangeCover(song) },
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = stringResource(R.string.change_cover),
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 基础信息列表
            DetailInfoRow(
                label = stringResource(R.string.label_song_title),
                value = song.title
            )

            Spacer(modifier = Modifier.height(14.dp))

            DetailInfoRow(
                label = stringResource(R.string.label_artist),
                value = song.artist
            )

            Spacer(modifier = Modifier.height(14.dp))

            DetailInfoRow(
                label = stringResource(R.string.label_album),
                value = song.album
            )

            Spacer(modifier = Modifier.height(14.dp))

            DetailInfoRow(
                label = stringResource(R.string.label_track_number),
                value = songMetadata?.track?.ifBlank { "0" } ?: "0"
            )

            Spacer(modifier = Modifier.height(14.dp))

            // 歌词预览行
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (onViewLyrics != null && !firstLyricPreview.isNullOrBlank()) {
                            Modifier.clickable { onViewLyrics(song) }
                        } else {
                            Modifier
                        }
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.label_lyrics),
                    fontSize = 14.sp,
                    color = OrbitTheme.colors.textPrimary.copy(alpha = 0.9f),
                    fontWeight = FontWeight.Normal,
                    modifier = Modifier.width(60.dp)
                )
                Text(
                    text = firstLyricPreview ?: stringResource(R.string.label_lyrics_empty),
                    fontSize = 14.sp,
                    color = if (!firstLyricPreview.isNullOrBlank()) OrbitTheme.colors.textPrimary else OrbitTheme.colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (onViewLyrics != null && !firstLyricPreview.isNullOrBlank()) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "View Lyrics",
                        tint = OrbitTheme.colors.textSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 更多信息小标题
            Text(
                text = stringResource(R.string.header_more_info),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = OrbitTheme.colors.textSecondary
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 技术规格参数网格 (3 行 x 3 列对称布局)
            val specs = techSpecs
            val formatStr = specs?.format?.lowercase() ?: "audio"
            val durationStr = specs?.durationFormatted ?: song.formattedDuration
            val bitrateStr = if ((specs?.bitrateKbps ?: 0) > 0) "${specs?.bitrateKbps}Kbps" else "—"
            val sampleRateStr = if ((specs?.sampleRateHz ?: 0) > 0) "${specs?.sampleRateHz}Hz" else "44100Hz"
            val bitDepthStr = "${specs?.bitDepth ?: 16}bit"
            val channelsStr = if (specs?.channelsText == "单声道") "1" else "2"

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 第 1 行：格式 · 时长 · 码率
                Row(modifier = Modifier.fillMaxWidth()) {
                    SpecItem(
                        label = stringResource(R.string.label_format),
                        value = formatStr,
                        modifier = Modifier.weight(1f)
                    )
                    SpecItem(
                        label = stringResource(R.string.label_duration),
                        value = durationStr,
                        modifier = Modifier.weight(1f)
                    )
                    SpecItem(
                        label = stringResource(R.string.label_bitrate),
                        value = bitrateStr,
                        modifier = Modifier.weight(1f)
                    )
                }

                // 第 2 行：大小 · 采样率 · 位深
                Row(modifier = Modifier.fillMaxWidth()) {
                    SpecItem(
                        label = stringResource(R.string.label_size),
                        value = fileSizeFormatted.ifBlank { "—" },
                        modifier = Modifier.weight(1f)
                    )
                    SpecItem(
                        label = stringResource(R.string.label_sample_rate),
                        value = sampleRateStr,
                        modifier = Modifier.weight(1f)
                    )
                    SpecItem(
                        label = stringResource(R.string.label_bit_depth),
                        value = bitDepthStr,
                        modifier = Modifier.weight(1f)
                    )
                }

                // 第 3 行：声道
                Row(modifier = Modifier.fillMaxWidth()) {
                    SpecItem(
                        label = stringResource(R.string.label_channels),
                        value = channelsStr,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.weight(2f))
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 文件路径 (可点击一键复制)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .clickable {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("Song File Path", song.path)
                        clipboard.setPrimaryClip(clip)
                        FastToast.show(context, context.getString(R.string.copied_path_toast))
                    }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = stringResource(R.string.label_file_path),
                    fontSize = 12.sp,
                    color = OrbitTheme.colors.textSecondary,
                    modifier = Modifier.padding(end = 4.dp)
                )
                Text(
                    text = displayPath,
                    fontSize = 12.sp,
                    color = OrbitTheme.colors.textSecondary.copy(alpha = 0.9f),
                    lineHeight = 16.sp,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun DetailInfoRow(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            color = OrbitTheme.colors.textPrimary.copy(alpha = 0.9f),
            fontWeight = FontWeight.Normal,
            modifier = Modifier.width(60.dp)
        )
        Text(
            text = value,
            fontSize = 14.sp,
            color = OrbitTheme.colors.textPrimary,
            fontWeight = FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun SpecItem(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            color = OrbitTheme.colors.textSecondary
        )
        Spacer(modifier = Modifier.width(2.dp))
        Text(
            text = value,
            fontSize = 12.sp,
            color = OrbitTheme.colors.textSecondary.copy(alpha = 0.9f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
