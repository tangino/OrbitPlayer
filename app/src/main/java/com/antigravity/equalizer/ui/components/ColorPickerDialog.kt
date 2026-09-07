package com.antigravity.equalizer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.antigravity.equalizer.R
import com.antigravity.equalizer.ui.theme.OrbitTheme

/**
 * 殿堂级专业 HSV 色彩拾取与自定义颜色库管理对话框
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ColorPickerDialog(
    initialColor: Long,
    customColors: List<Long>,
    onColorConfirmed: (Long) -> Unit,
    onSaveToCustomColors: (Long) -> Unit,
    onRemoveCustomColor: ((Long) -> Unit)? = null,
    onDismissRequest: () -> Unit
) {
    // 解析初始颜色的 HSV
    val initialHsv = remember(initialColor) {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV((initialColor and 0xFFFFFFFFL).toInt(), hsv)
        hsv
    }

    var hue by remember { mutableFloatStateOf(initialHsv[0]) } // 0f ~ 360f
    var saturation by remember { mutableFloatStateOf(initialHsv[1].coerceIn(0.1f, 1f)) } // 0f ~ 1f
    var value by remember { mutableFloatStateOf(initialHsv[2].coerceIn(0.1f, 1f)) } // 0f ~ 1f

    val currentColorInt = remember(hue, saturation, value) {
        android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, value))
    }
    val currentColorLong = remember(currentColorInt) {
        currentColorInt.toLong() and 0xFFFFFFFFL
    }
    val hexString = remember(currentColorInt) {
        String.format("#%06X", 0xFFFFFF and currentColorInt)
    }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .widthIn(max = 480.dp)
                .wrapContentHeight(),
            shape = RoundedCornerShape(26.dp),
            color = OrbitTheme.colors.surfaceDialog,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 1. 顶部标题栏与关闭
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(R.string.color_picker_title),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = OrbitTheme.colors.textPrimary
                    )
                    IconButton(
                        onClick = onDismissRequest,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = OrbitTheme.colors.textSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // 2. 当前色彩大预览圆盘 + HEX 代码
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .background(Color(currentColorLong))
                            .border(2.5.dp, OrbitTheme.colors.textPrimary.copy(alpha = 0.35f), CircleShape)
                    )

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.color_hex_label),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = OrbitTheme.colors.textSecondary
                        )
                        Text(
                            text = hexString,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold,
                            fontFamily = FontFamily.Monospace,
                            color = OrbitTheme.colors.textPrimary
                        )
                    }

                    // 添加到自定义色卡快捷按钮
                    FilledTonalButton(
                        onClick = { onSaveToCustomColors(currentColorLong) },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = OrbitTheme.colors.surface,
                            contentColor = OrbitTheme.colors.primary
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = stringResource(R.string.add_to_custom_colors),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // 3. 色相滑动调色条 (Hue 0° ~ 360°)
                val rainbowColors = remember {
                    listOf(
                        Color.Red,
                        Color.Yellow,
                        Color.Green,
                        Color.Cyan,
                        Color.Blue,
                        Color.Magenta,
                        Color.Red
                    )
                }
                Text(
                    text = "色相 (Hue): ${hue.toInt()}°",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = OrbitTheme.colors.textSecondary,
                    modifier = Modifier.align(Alignment.Start)
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(18.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(Brush.horizontalGradient(rainbowColors))
                )
                Slider(
                    value = hue,
                    onValueChange = { hue = it },
                    valueRange = 0f..360f,
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = Color.Transparent,
                        inactiveTrackColor = Color.Transparent
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .offset(y = (-10).dp)
                )

                // 4. 饱和度滑动调节 (Saturation)
                Text(
                    text = "饱和度 (Saturation): ${(saturation * 100).toInt()}%",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = OrbitTheme.colors.textSecondary,
                    modifier = Modifier.align(Alignment.Start)
                )
                Slider(
                    value = saturation,
                    onValueChange = { saturation = it },
                    valueRange = 0.05f..1f,
                    colors = SliderDefaults.colors(
                        thumbColor = OrbitTheme.colors.primary,
                        activeTrackColor = OrbitTheme.colors.primary,
                        inactiveTrackColor = OrbitTheme.colors.primary.copy(alpha = 0.25f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                // 5. 明度滑动调节 (Value / Brightness)
                Text(
                    text = "明度 (Brightness): ${(value * 100).toInt()}%",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = OrbitTheme.colors.textSecondary,
                    modifier = Modifier.align(Alignment.Start)
                )
                Slider(
                    value = value,
                    onValueChange = { value = it },
                    valueRange = 0.15f..1f,
                    colors = SliderDefaults.colors(
                        thumbColor = OrbitTheme.colors.tertiary,
                        activeTrackColor = OrbitTheme.colors.tertiary,
                        inactiveTrackColor = OrbitTheme.colors.tertiary.copy(alpha = 0.25f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                // 6. 已保存的自定义色彩列表 (水平滑动色卡)
                if (customColors.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.custom_colors_title),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = OrbitTheme.colors.textSecondary,
                        modifier = Modifier.align(Alignment.Start)
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        items(customColors) { colorVal ->
                            val isCurrentSelected = (colorVal == currentColorLong)
                            val itemHsv = FloatArray(3)
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(Color(colorVal))
                                    .border(
                                        width = if (isCurrentSelected) 3.dp else 1.dp,
                                        color = if (isCurrentSelected) OrbitTheme.colors.primary else Color.White.copy(alpha = 0.35f),
                                        shape = CircleShape
                                    )
                                    .combinedClickable(
                                        onClick = {
                                            android.graphics.Color.colorToHSV((colorVal and 0xFFFFFFFFL).toInt(), itemHsv)
                                            hue = itemHsv[0]
                                            saturation = itemHsv[1]
                                            value = itemHsv[2]
                                        },
                                        onLongClick = {
                                            onRemoveCustomColor?.invoke(colorVal)
                                        }
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isCurrentSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = if (value > 0.6f && saturation < 0.4f) Color.Black else Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // 7. 确认应用按钮
                Button(
                    onClick = {
                        onColorConfirmed(currentColorLong)
                        onDismissRequest()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = OrbitTheme.colors.primary,
                        contentColor = OrbitTheme.colors.background
                    )
                ) {
                    Text(
                        text = stringResource(R.string.color_picker_confirm),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
