package com.antigravity.equalizer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import coil.size.Size
import com.antigravity.equalizer.data.model.AlbumItem
import com.antigravity.equalizer.ui.theme.*
import com.antigravity.equalizer.ui.viewmodel.LibraryViewMode

/**
 * 专辑卡片/列表项组件：
 * 1. 完美支持全 6 档 Pinch 视图缩放模式 (3 种单列列表 + 3 种多列网格)
 * 2. 视觉规范、间距与形变动效与 SongItem 保持 100% 一致
 */
@Composable
fun AlbumItem(
    album: AlbumItem,
    viewMode: LibraryViewMode,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isCurrent: Boolean = false,
    coverVersion: Long = 0L
) {
    val context = LocalContext.current
    val artUri = album.albumArtUri ?: com.antigravity.equalizer.data.provider.AudioCoverProvider.buildSongCoverUri(album.id, "", album.title)
    val isGrid = viewMode == LibraryViewMode.GRID_2_COL ||
            viewMode == LibraryViewMode.GRID_3_COL ||
            viewMode == LibraryViewMode.GRID_4_COL

    val isNoArt = viewMode == LibraryViewMode.LIST_NO_ART
    val colors = OrbitTheme.colors

    val itemBg = if (isCurrent) {
        colors.primary.copy(alpha = 0.14f)
    } else if (viewMode == LibraryViewMode.LIST_LARGE_ART || isGrid) {
        colors.surfaceCard
    } else {
        Color.Transparent
    }

    val itemShape = RoundedCornerShape(if (viewMode == LibraryViewMode.LIST_LARGE_ART || isGrid) 12.dp else 8.dp)

    val itemPadding = when (viewMode) {
        LibraryViewMode.GRID_4_COL -> 4.dp
        LibraryViewMode.GRID_3_COL -> 6.dp
        LibraryViewMode.GRID_2_COL -> 8.dp
        LibraryViewMode.LIST_LARGE_ART -> 8.dp
        else -> 6.dp
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(itemShape)
            .background(itemBg)
            .clickable(onClick = onClick)
            .padding(itemPadding)
    ) {
        if (isGrid) {
            // ========== 网格模式 (GRID_2_COL / GRID_3_COL / GRID_4_COL) ==========
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.Start
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(colors.surfaceCard),
                    contentAlignment = Alignment.Center
                ) {
                    if (artUri.isNotBlank()) {
                        SubcomposeAsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(artUri)
                                .memoryCacheKey("${artUri}_$coverVersion")
                                .diskCacheKey("${artUri}_$coverVersion")
                                .size(
                                    when (viewMode) {
                                        LibraryViewMode.GRID_4_COL -> Size(120, 120)
                                        LibraryViewMode.GRID_3_COL -> Size(200, 200)
                                        else -> Size(300, 300)
                                    }
                                )
                                .allowHardware(true)
                                .crossfade(false)
                                .build(),
                            contentDescription = album.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                            loading = {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Album,
                                        contentDescription = null,
                                        tint = TextSecondary.copy(alpha = 0.45f),
                                        modifier = Modifier.size(if (viewMode == LibraryViewMode.GRID_4_COL) 20.dp else 34.dp)
                                    )
                                }
                            },
                            error = {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Album,
                                        contentDescription = null,
                                        tint = TextSecondary.copy(alpha = 0.45f),
                                        modifier = Modifier.size(if (viewMode == LibraryViewMode.GRID_4_COL) 20.dp else 34.dp)
                                    )
                                }
                            }
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Album,
                            contentDescription = null,
                            tint = TextSecondary,
                            modifier = Modifier.size(if (viewMode == LibraryViewMode.GRID_4_COL) 20.dp else 34.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = album.title,
                    fontSize = when (viewMode) {
                        LibraryViewMode.GRID_4_COL -> 10.sp
                        LibraryViewMode.GRID_3_COL -> 11.sp
                        else -> 13.sp
                    },
                    fontWeight = FontWeight.Bold,
                    color = if (isCurrent) colors.primary else colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (viewMode != LibraryViewMode.GRID_4_COL) {
                    Spacer(modifier = Modifier.height(1.dp))
                    Text(
                        text = "${album.artist} • ${album.songCount} tracks",
                        fontSize = if (viewMode == LibraryViewMode.GRID_3_COL) 10.sp else 11.sp,
                        color = colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                } else {
                    Spacer(modifier = Modifier.height(1.dp))
                    Text(
                        text = "${album.songCount} tracks",
                        fontSize = 9.sp,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        } else {
            // ========== 列表模式 (LIST_NO_ART / LIST_SMALL_ART / LIST_LARGE_ART) ==========
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!isNoArt) {
                    val coverSize = if (viewMode == LibraryViewMode.LIST_LARGE_ART) 64.dp else 46.dp

                    Box(
                        modifier = Modifier
                            .size(coverSize)
                            .clip(RoundedCornerShape(8.dp))
                            .background(colors.surfaceCard),
                        contentAlignment = Alignment.Center
                    ) {
                        if (artUri.isNotBlank()) {
                            SubcomposeAsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(artUri)
                                    .memoryCacheKey("${artUri}_$coverVersion")
                                    .diskCacheKey("${artUri}_$coverVersion")
                                    .size(if (viewMode == LibraryViewMode.LIST_LARGE_ART) Size(180, 180) else Size(120, 120))
                                    .allowHardware(true)
                                    .crossfade(false)
                                    .build(),
                                contentDescription = album.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                                loading = {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Album,
                                            contentDescription = null,
                                            tint = TextSecondary.copy(alpha = 0.45f),
                                            modifier = Modifier.size(if (viewMode == LibraryViewMode.LIST_LARGE_ART) 28.dp else 22.dp)
                                        )
                                    }
                                },
                                error = {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Album,
                                            contentDescription = null,
                                            tint = TextSecondary.copy(alpha = 0.45f),
                                            modifier = Modifier.size(if (viewMode == LibraryViewMode.LIST_LARGE_ART) 28.dp else 22.dp)
                                        )
                                    }
                                }
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Album,
                                contentDescription = null,
                                tint = TextSecondary,
                                modifier = Modifier.size(if (viewMode == LibraryViewMode.LIST_LARGE_ART) 28.dp else 22.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = album.title,
                        fontSize = if (viewMode == LibraryViewMode.LIST_LARGE_ART) 15.sp else 14.sp,
                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.SemiBold,
                        color = if (isCurrent) colors.primary else colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${album.artist} • ${album.songCount} tracks",
                        fontSize = 11.sp,
                        color = colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = colors.textSecondary.copy(alpha = 0.6f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
