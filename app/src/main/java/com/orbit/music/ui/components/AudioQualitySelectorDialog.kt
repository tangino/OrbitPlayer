package com.orbit.music.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Info
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
import com.orbit.music.data.model.Song
import com.orbit.music.ui.theme.OrbitTheme

data class AudioQualityOption(
    val key: String,
    val title: String,
    val tag: String,
    val specText: String,
    val description: String,
    val estimatedSize: String,
    val tagColor: Color,
    val tagBgColor: Color
)

/**
 * 播放页音频清晰度与音质选择弹窗 (精美极光毛玻璃底栏)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioQualitySelectorDialog(
    song: Song,
    currentQuality: String,
    onQualitySelect: (String) -> Unit,
    onDismissRequest: () -> Unit
) {
    val context = LocalContext.current
    val isOnline = song.path.startsWith("online://") || song.id < 0

    val qualityOptions = remember {
        listOf(
            AudioQualityOption(
                key = "flac24bit",
                title = "Hi-Res 母带无损",
                tag = "Hi-Res",
                specText = "24bit / 96kHz ~ 192kHz",
                description = "母带级极高解析度 · 还原录音棚纯净细节与广阔声场",
                estimatedSize = "约 30MB ~ 80MB / 首",
                tagColor = Color(0xFFFFD700),
                tagBgColor = Color(0xFFFFD700).copy(alpha = 0.16f)
            ),
            AudioQualityOption(
                key = "flac",
                title = "SQ 超品质无损",
                tag = "SQ",
                specText = "16bit / 44.1kHz FLAC",
                description = "CD 级纯净无损音质 · 层次分明无压缩失真",
                estimatedSize = "约 20MB ~ 40MB / 首",
                tagColor = Color(0xFF00E5FF),
                tagBgColor = Color(0xFF00E5FF).copy(alpha = 0.16f)
            ),
            AudioQualityOption(
                key = "320k",
                title = "HQ 高品质音频",
                tag = "HQ",
                specText = "320 kbps MP3",
                description = "接近无损的听觉体验 · 音质与省流的绝佳均衡",
                estimatedSize = "约 8MB ~ 12MB / 首",
                tagColor = Color(0xFFF59E0B),
                tagBgColor = Color(0xFFF59E0B).copy(alpha = 0.16f)
            ),
            AudioQualityOption(
                key = "128k",
                title = "标准流畅音质",
                tag = "标准",
                specText = "128 kbps MP3",
                description = "极速秒开 · 弱网流畅播放与最小流量消耗",
                estimatedSize = "约 3MB ~ 5MB / 首",
                tagColor = Color(0xFF94A3B8),
                tagBgColor = Color(0xFF94A3B8).copy(alpha = 0.16f)
            )
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = OrbitTheme.colors.surface.copy(alpha = 0.96f),
        dragHandle = {
            Surface(
                modifier = Modifier.padding(top = 10.dp, bottom = 6.dp),
                color = OrbitTheme.colors.textSecondary.copy(alpha = 0.35f),
                shape = CircleShape
            ) {
                Box(modifier = Modifier.size(width = 36.dp, height = 4.dp))
            }
        },
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // 1. 顶部标题与关闭按钮
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.HighQuality,
                        contentDescription = null,
                        tint = OrbitTheme.colors.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = "音频清晰度与音质",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = OrbitTheme.colors.textPrimary
                    )
                }

                IconButton(
                    onClick = onDismissRequest,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "关闭",
                        tint = OrbitTheme.colors.textSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // 2. 当前歌曲预览卡片
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = OrbitTheme.colors.surfaceBorder.copy(alpha = 0.25f),
                border = BorderStroke(1.dp, OrbitTheme.colors.surfaceBorder.copy(alpha = 0.5f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 封面
                    val coverReq = remember(song.id, song.albumArtUri, song.path) {
                        val art = song.albumArtUri?.takeIf { it.isNotBlank() } ?: song.path
                        ImageRequest.Builder(context)
                            .data(art)
                            .crossfade(true)
                            .build()
                    }
                    AsyncImage(
                        model = coverReq,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(8.dp))
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = song.title,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = OrbitTheme.colors.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = song.artist.ifBlank { "未知艺术家" },
                                fontSize = 12.sp,
                                color = OrbitTheme.colors.textSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            if (isOnline) {
                                val platformName = song.sourcePlatform?.displayName
                                    ?: song.originalPlatform?.displayName
                                    ?: song.sourceTag
                                    ?: "在线音源"
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = OrbitTheme.colors.primary.copy(alpha = 0.15f)
                                ) {
                                    Text(
                                        text = platformName,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = OrbitTheme.colors.primary,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            } else {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = Color(0xFF10B981).copy(alpha = 0.15f)
                                ) {
                                    Text(
                                        text = "本地原声直出",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = Color(0xFF10B981),
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (!isOnline) {
                // 本地歌曲说明卡片
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF10B981).copy(alpha = 0.08f),
                    border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.25f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = Color(0xFF10B981),
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "当前歌曲为本地存储音频，播放器已自动启用母带无损原音直出模式。您也可以在下方选择在线歌曲的默认偏好清晰度：",
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                            color = OrbitTheme.colors.textSecondary
                        )
                    }
                }
            }

            Text(
                text = if (isOnline) "选择当前播放与默认音质档位：" else "设置网络歌曲默认偏好清晰度：",
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Medium,
                color = OrbitTheme.colors.textSecondary,
                modifier = Modifier.padding(vertical = 6.dp, horizontal = 2.dp)
            )

            // 3. 音质档位列表
            qualityOptions.forEach { opt ->
                val isSelected = currentQuality.equals(opt.key, ignoreCase = true)

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (isSelected) {
                        OrbitTheme.colors.primary.copy(alpha = 0.14f)
                    } else {
                        OrbitTheme.colors.surfaceBorder.copy(alpha = 0.20f)
                    },
                    border = BorderStroke(
                        width = if (isSelected) 1.5.dp else 0.8.dp,
                        color = if (isSelected) OrbitTheme.colors.primary else OrbitTheme.colors.surfaceBorder.copy(alpha = 0.45f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 5.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable {
                            onQualitySelect(opt.key)
                            onDismissRequest()
                        }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 左侧音质药丸徽标
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = opt.tagBgColor,
                            border = BorderStroke(0.8.dp, opt.tagColor.copy(alpha = 0.6f))
                        ) {
                            Text(
                                text = opt.tag,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = opt.tagColor,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.5.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        // 中间详细信息
                        Column(
                            modifier = Modifier.weight(1f)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = opt.title,
                                    fontSize = 14.5.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                    color = if (isSelected) OrbitTheme.colors.primary else OrbitTheme.colors.textPrimary
                                )

                                Text(
                                    text = "•",
                                    fontSize = 10.sp,
                                    color = OrbitTheme.colors.textSecondary.copy(alpha = 0.4f)
                                )

                                Text(
                                    text = opt.specText,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = OrbitTheme.colors.textSecondary
                                )
                            }

                            Spacer(modifier = Modifier.height(2.5.dp))

                            Text(
                                text = opt.description,
                                fontSize = 11.5.sp,
                                lineHeight = 15.sp,
                                color = OrbitTheme.colors.textSecondary.copy(alpha = 0.85f)
                            )

                            Spacer(modifier = Modifier.height(2.dp))

                            Text(
                                text = opt.estimatedSize,
                                fontSize = 10.5.sp,
                                color = OrbitTheme.colors.textSecondary.copy(alpha = 0.6f)
                            )
                        }

                        // 右侧选中状态
                        if (isSelected) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .background(OrbitTheme.colors.primary),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "已选择",
                                    tint = Color.White,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}
