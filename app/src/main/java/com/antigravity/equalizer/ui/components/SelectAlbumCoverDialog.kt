package com.antigravity.equalizer.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.antigravity.equalizer.R
import com.antigravity.equalizer.data.cover.MusicBrainzCoverService
import com.antigravity.equalizer.ui.theme.OrbitTheme

/**
 * 候选专辑封面选择对话框
 * 当无法精确检索到具体专辑时，弹窗展示该歌唱者名下的全部高清专辑封面供用户挑选
 */
@Composable
fun SelectAlbumCoverDialog(
    artistName: String,
    candidates: List<MusicBrainzCoverService.AlbumCoverCandidate>,
    isApplying: Boolean = false,
    onDismissRequest: () -> Unit,
    onConfirmSelection: (MusicBrainzCoverService.AlbumCoverCandidate) -> Unit
) {
    var selectedCandidate by remember(candidates) {
        mutableStateOf(candidates.firstOrNull())
    }

    AlertDialog(
        onDismissRequest = {
            if (!isApplying) onDismissRequest()
        },
        properties = DialogProperties(
            dismissOnBackPress = !isApplying,
            dismissOnClickOutside = !isApplying,
            usePlatformDefaultWidth = false
        ),
        modifier = Modifier
            .fillMaxWidth(0.92f)
            .heightIn(max = 560.dp),
        title = {
            Column {
                Text(
                    text = stringResource(R.string.select_album_cover_title),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = OrbitTheme.colors.textPrimary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.select_album_cover_subtitle, artistName),
                    fontSize = 13.sp,
                    color = OrbitTheme.colors.textSecondary,
                    lineHeight = 18.sp
                )
            }
        },
        text = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(340.dp)
            ) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(candidates, key = { it.releaseId }) { candidate ->
                        val isSelected = selectedCandidate?.releaseId == candidate.releaseId
                        AlbumCoverCard(
                            candidate = candidate,
                            isSelected = isSelected,
                            onClick = {
                                if (!isApplying) {
                                    selectedCandidate = candidate
                                }
                            }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    selectedCandidate?.let { onConfirmSelection(it) }
                },
                enabled = selectedCandidate != null && !isApplying,
                colors = ButtonDefaults.buttonColors(
                    containerColor = OrbitTheme.colors.primary,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                if (isApplying) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = stringResource(R.string.applying_cover), fontSize = 13.sp)
                } else {
                    Text(text = stringResource(R.string.btn_apply_cover), fontSize = 14.sp)
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismissRequest,
                enabled = !isApplying
            ) {
                Text(
                    text = stringResource(R.string.btn_cancel),
                    color = OrbitTheme.colors.textSecondary,
                    fontSize = 14.sp
                )
            }
        },
        containerColor = OrbitTheme.colors.surfaceDialog,
        shape = RoundedCornerShape(16.dp)
    )
}

/**
 * 候选封面单项卡片
 */
@Composable
private fun AlbumCoverCard(
    candidate: MusicBrainzCoverService.AlbumCoverCandidate,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val borderColor = if (isSelected) OrbitTheme.colors.primary else Color.White.copy(alpha = 0.08f)
    val borderWidth = if (isSelected) 2.dp else 1.dp

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = OrbitTheme.colors.surfaceCard,
        border = BorderStroke(borderWidth, borderColor),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable { onClick() }
    ) {
        Column(
            modifier = Modifier.padding(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.0f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(alpha = 0.3f))
            ) {
                AsyncImage(
                    model = candidate.thumbnailUrl,
                    contentDescription = candidate.albumTitle,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                // 选中指示徽章
                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(OrbitTheme.colors.primary.copy(alpha = 0.2f))
                    )
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .size(22.dp)
                            .background(OrbitTheme.colors.primary, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Selected",
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // 专辑标题
            Text(
                text = candidate.albumTitle,
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) OrbitTheme.colors.primary else OrbitTheme.colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            // 发行日期/年份
            val yearText = candidate.releaseDate.take(4).ifBlank { "Unknown" }
            Text(
                text = yearText,
                fontSize = 11.sp,
                color = OrbitTheme.colors.textSecondary.copy(alpha = 0.8f),
                maxLines = 1,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
