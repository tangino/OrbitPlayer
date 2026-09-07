package com.antigravity.equalizer.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Shader
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.antigravity.equalizer.ui.theme.OrbitTheme
import java.io.File

/**
 * 全局沉浸式自定义背景层
 * 支持高斯柔光模糊 (Gaussian Blur) 与 亚克力磨砂玻璃质感 (Frosted Glass)
 */
@Composable
fun AppBackgroundLayer(
    customBackgroundPath: String?,
    blurRadius: Float,
    blurStyle: String,
    dimAlpha: Float,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isValidBg = remember(customBackgroundPath) {
        customBackgroundPath != null && File(customBackgroundPath).exists()
    }

    if (!isValidBg) {
        // 无自定义背景时回退为默认主题底色
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(OrbitTheme.colors.background)
        )
        return
    }

    val file = remember(customBackgroundPath) { File(customBackgroundPath!!) }
    val effectiveBlur = blurRadius.coerceIn(0f, 50f)
    val isFrosted = blurStyle == "frosted_glass"

    Box(modifier = modifier.fillMaxSize()) {
        // 1. 底层背景图片加载与高斯模糊
        Crossfade(
            targetState = file,
            animationSpec = tween(400),
            label = "BackgroundCrossfade"
        ) { targetFile ->
            val imageModifier = if (effectiveBlur > 0.5f) {
                Modifier
                    .fillMaxSize()
                    .blur(effectiveBlur.dp)
            } else {
                Modifier.fillMaxSize()
            }

            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(targetFile)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = imageModifier
            )
        }

        // 2. 磨砂玻璃 (Frosted Glass / Acrylic) 专属物理质感层
        if (isFrosted) {
            // 2.1 亚克力微光漫反射高光层与透光微雾渐变
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0x38FFFFFF), // 顶部微弱天光漫射
                                Color(0x18FFFFFF),
                                Color(0x08000000),
                                Color(0x2A000000)  // 底部微暗收敛
                            )
                        )
                    )
            )

            // 2.2 高密度真实物理喷砂磨砂微晶颗粒平铺纹理 (Sandblasted Glass Noise Texture)
            val frostedNoiseShader = remember {
                val texSize = 128
                val bitmap = Bitmap.createBitmap(texSize, texSize, Bitmap.Config.ARGB_8888)
                val pixels = IntArray(texSize * texSize)
                val random = java.util.Random(1337)
                for (i in pixels.indices) {
                    val r = random.nextFloat()
                    pixels[i] = when {
                        r < 0.15f -> {
                            // 亮点微晶 (次表面高反射率磨砂闪烁晶体)
                            val alpha = (random.nextFloat() * 65 + 45).toInt()
                            (alpha shl 24) or 0x00FFFFFF
                        }
                        r < 0.38f -> {
                            // 柔和微白颗粒 (磨砂漫散微颗粒)
                            val alpha = (random.nextFloat() * 30 + 18).toInt()
                            (alpha shl 24) or 0x00FFFFFF
                        }
                        r < 0.52f -> {
                            // 凹坑微阴影颗粒 (增强物理粗糙度微对比与立体颗粒感)
                            val alpha = (random.nextFloat() * 40 + 20).toInt()
                            (alpha shl 24) or 0x00000000
                        }
                        else -> 0
                    }
                }
                bitmap.setPixels(pixels, 0, texSize, 0, 0, texSize, texSize)
                BitmapShader(bitmap, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
            }

            Canvas(modifier = Modifier.fillMaxSize()) {
                drawIntoCanvas { canvas ->
                    val paint = android.graphics.Paint().apply {
                        shader = frostedNoiseShader
                        isAntiAlias = false
                        isFilterBitmap = false
                    }
                    canvas.nativeCanvas.drawRect(0f, 0f, size.width, size.height, paint)
                }
            }
        }

        // 3. 顶层暗度遮罩 (保证所有前台文字、按钮、波形具备极佳的可读性与对比度)
        val clampedDim = dimAlpha.coerceIn(0.0f, 0.85f)
        if (clampedDim > 0.01f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = clampedDim))
            )
        }
    }
}
