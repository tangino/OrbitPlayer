package com.antigravity.equalizer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.antigravity.equalizer.R
import com.antigravity.equalizer.data.model.AudioTechSpecs
import com.antigravity.equalizer.data.model.Song
import com.antigravity.equalizer.data.model.SongMetadata
import com.antigravity.equalizer.data.model.SongMetadataHelper
import com.antigravity.equalizer.ui.theme.OrbitTheme

/**
 * 现代 Poweramp 风格全套歌曲元数据编辑对话框
 * 覆盖：文件路径展示、音频技术规格徽标与参数摘要、标题、音轨、年份、流派(带下拉)、艺术家、专辑、专辑艺术家、作曲家、注释
 */
@Composable
fun EditSongTagsDialog(
    song: Song,
    initialMetadata: SongMetadata,
    onDismissRequest: () -> Unit,
    onSaveMetadata: (SongMetadata) -> Unit
) {
    var title by remember(initialMetadata) { mutableStateOf(initialMetadata.title) }
    var track by remember(initialMetadata) { mutableStateOf(initialMetadata.track) }
    var year by remember(initialMetadata) { mutableStateOf(initialMetadata.year) }
    var genre by remember(initialMetadata) { mutableStateOf(initialMetadata.genre) }
    var artist by remember(initialMetadata) { mutableStateOf(initialMetadata.artist) }
    var album by remember(initialMetadata) { mutableStateOf(initialMetadata.album) }
    var albumArtist by remember(initialMetadata) { mutableStateOf(initialMetadata.albumArtist) }
    var composer by remember(initialMetadata) { mutableStateOf(initialMetadata.composer) }
    var comment by remember(initialMetadata) { mutableStateOf(initialMetadata.comment) }

    var genreDropdownExpanded by remember { mutableStateOf(false) }

    val techSpecs = remember(song.path, song.durationMs, song.size) {
        SongMetadataHelper.extractTechSpecs(song)
    }

    val displayPath = remember(song.path) {
        SongMetadataHelper.formatDisplayPath(song.path)
    }

    val commonGenres = remember {
        listOf(
            "流行 (Pop)", "摇滚 (Rock)", "民谣 (Folk)", "爵士 (Jazz)", "古典 (Classical)",
            "电子 (Electronic)", "嘻哈 (Hip-Hop)", "金属 (Metal)", "轻音乐 (Easy Listening)",
            "节奏布鲁斯 (R&B)", "原声带 (Soundtrack)", "古风 / ACG", "蓝调 (Blues)"
        )
    }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .fillMaxWidth(0.92f)
            .heightIn(max = 680.dp),
        title = {
            Text(
                text = stringResource(R.string.edit_tags_title),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = OrbitTheme.colors.textPrimary
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                // 1. 顶部文件路径
                Text(
                    text = displayPath,
                    fontSize = 11.sp,
                    color = OrbitTheme.colors.textSecondary,
                    lineHeight = 15.sp,
                    modifier = Modifier.padding(bottom = 6.dp)
                )

                // 2. 音频规格参数行（带小格式徽标）
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 绿色小格式徽标 (例如 flac, mp3, wav)
                    Surface(
                        shape = RoundedCornerShape(3.dp),
                        color = OrbitTheme.colors.primary.copy(alpha = 0.2f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, OrbitTheme.colors.primary.copy(alpha = 0.6f)),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Text(
                            text = techSpecs.format.lowercase(),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = OrbitTheme.colors.primary,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }

                    // 规格详情：FLAC, 255 秒 (4:15), 44100 HZ, 16 BIT, 立体声, 892 KBPS, GAPLESS, 27771KB
                    Text(
                        text = techSpecs.specsSummary,
                        fontSize = 11.sp,
                        color = OrbitTheme.colors.textSecondary,
                        lineHeight = 15.sp
                    )
                }

                HorizontalDivider(color = Color.White.copy(alpha = 0.08f), modifier = Modifier.padding(bottom = 14.dp))

                // 3. 可编辑字段列表
                // 3.1 标题
                PowerampFieldItem(
                    label = stringResource(R.string.tag_title_label),
                    value = title,
                    onValueChange = { title = it }
                )

                Spacer(modifier = Modifier.height(12.dp))

                // 3.2 音轨 与 年份（双列并排）
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        PowerampFieldItem(
                            label = stringResource(R.string.tag_track_label),
                            value = track,
                            onValueChange = { track = it },
                            keyboardType = KeyboardType.Number
                        )
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        PowerampFieldItem(
                            label = stringResource(R.string.tag_year_label),
                            value = year,
                            onValueChange = { year = it },
                            keyboardType = KeyboardType.Number
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 3.3 流派 (带下拉选择)
                Box(modifier = Modifier.fillMaxWidth()) {
                    PowerampFieldItem(
                        label = stringResource(R.string.tag_genre_label),
                        value = genre,
                        onValueChange = { genre = it },
                        trailingIcon = {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = "Select Genre",
                                tint = OrbitTheme.colors.primary,
                                modifier = Modifier
                                    .size(24.dp)
                                    .clickable { genreDropdownExpanded = true }
                            )
                        }
                    )

                    DropdownMenu(
                        expanded = genreDropdownExpanded,
                        onDismissRequest = { genreDropdownExpanded = false },
                        modifier = Modifier.background(OrbitTheme.colors.surfaceDialog)
                    ) {
                        commonGenres.forEach { g ->
                            val cleanGenreName = g.substringBefore(" (")
                            DropdownMenuItem(
                                text = { Text(g, fontSize = 13.sp, color = OrbitTheme.colors.textPrimary) },
                                onClick = {
                                    genre = cleanGenreName
                                    genreDropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 3.4 艺术家
                PowerampFieldItem(
                    label = stringResource(R.string.tag_artist_label),
                    value = artist,
                    onValueChange = { artist = it }
                )

                Spacer(modifier = Modifier.height(12.dp))

                // 3.5 专辑
                PowerampFieldItem(
                    label = stringResource(R.string.tag_album_label),
                    value = album,
                    onValueChange = { album = it }
                )

                Spacer(modifier = Modifier.height(12.dp))

                // 3.6 专辑艺术家
                PowerampFieldItem(
                    label = stringResource(R.string.tag_album_artist_label),
                    value = albumArtist,
                    onValueChange = { albumArtist = it }
                )

                Spacer(modifier = Modifier.height(12.dp))

                // 3.7 作曲家
                PowerampFieldItem(
                    label = stringResource(R.string.tag_composer_label),
                    value = composer,
                    onValueChange = { composer = it }
                )

                Spacer(modifier = Modifier.height(12.dp))

                // 3.8 注释
                PowerampFieldItem(
                    label = stringResource(R.string.tag_comment_label),
                    value = comment,
                    onValueChange = { comment = it }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val finalTitle = title.trim().ifBlank { song.title }
                    val finalArtist = artist.trim().ifBlank { song.artist }
                    val finalAlbum = album.trim().ifBlank { song.album }
                    onSaveMetadata(
                        SongMetadata(
                            title = finalTitle,
                            track = track.trim(),
                            year = year.trim(),
                            genre = genre.trim(),
                            artist = finalArtist,
                            album = finalAlbum,
                            albumArtist = albumArtist.trim(),
                            composer = composer.trim(),
                            comment = comment.trim()
                        )
                    )
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = OrbitTheme.colors.primary,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(stringResource(R.string.btn_save), fontSize = 14.sp)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(
                    stringResource(R.string.btn_cancel),
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
 * 经典 Poweramp 风格单字段输入项：标签文字置于上方，输入内容置于下方，底部带高亮下划线
 */
@Composable
private fun PowerampFieldItem(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    keyboardType: KeyboardType = KeyboardType.Text,
    trailingIcon: (@Composable () -> Unit)? = null
) {
    var isFocused by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        // 字段 Label
        Text(
            text = label,
            fontSize = 12.sp,
            color = OrbitTheme.colors.textSecondary.copy(alpha = 0.85f),
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(bottom = 2.dp)
        )

        // 输入与右侧小图标
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                textStyle = TextStyle(
                    color = OrbitTheme.colors.textPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Normal
                ),
                cursorBrush = SolidColor(OrbitTheme.colors.primary),
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 4.dp)
                    .onFocusChanged { isFocused = it.isFocused }
            )

            if (trailingIcon != null) {
                trailingIcon()
            }
        }

        // 下划线
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (isFocused) 2.dp else 1.dp)
                .background(if (isFocused) OrbitTheme.colors.primary else OrbitTheme.colors.primary.copy(alpha = 0.45f))
        )
    }
}
