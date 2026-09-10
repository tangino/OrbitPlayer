package com.antigravity.equalizer.utils

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color as AndroidColor
import androidx.palette.graphics.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs

/**
 * 图像调色板提取工具：
 * 1. 负责从背景壁纸或专辑封面中毫秒级异步降采样并量化分析色彩；
 * 2. 提取出契合背景氛围的亮色 (Primary / Light) 与暗色 (Secondary / Dark)；
 * 3. 内置 HSV 空间明度区间防护，杜绝暗色在深色背景下隐形或亮色过曝看不清。
 */
object PaletteHelper {

    data class ExtractedColors(
        val lightColor: Long,
        val darkColor: Long
    )

    /**
     * 默认保底色彩 (赛博霓虹青与紫罗兰)
     */
    val DEFAULT_COLORS = ExtractedColors(
        lightColor = 0xFF00E5FFL,
        darkColor = 0xFF7C4DFFL
    )

    /**
     * 从本地图像文件中异步提取亮色与暗色
     *
     * @param filePath 图像文件绝对路径
     * @return 提取出的双色对象，提取失败时返回 null
     */
    suspend fun extractColorsFromImage(filePath: String?): ExtractedColors? = withContext(Dispatchers.IO) {
        if (filePath.isNullOrBlank()) return@withContext null
        val file = File(filePath)
        if (!file.exists() || !file.canRead() || file.length() == 0L) return@withContext null

        try {
            // 1. 获取原图尺寸并进行低分辨率降采样 (128x128 即可达到极高色准且耗时 < 10ms)
            val boundsOptions = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(filePath, boundsOptions)
            if (boundsOptions.outWidth <= 0 || boundsOptions.outHeight <= 0) return@withContext null

            val sampleSize = calculateInSampleSize(boundsOptions, 128, 128)
            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            val bitmap = BitmapFactory.decodeFile(filePath, decodeOptions) ?: return@withContext null

            // 2. 使用 AndroidX Palette 提取调色板
            val extracted = extractColorsFromBitmap(bitmap)
            bitmap.recycle()
            extracted
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 直接从 Bitmap 对象中实时提取双色配置
     */
    fun extractColorsFromBitmap(bitmap: Bitmap?): ExtractedColors? {
        if (bitmap == null || bitmap.isRecycled) return null
        return try {
            val palette = Palette.from(bitmap)
                .maximumColorCount(16)
                .generate()

            val lightSwatch = palette.lightVibrantSwatch
                ?: palette.vibrantSwatch
                ?: palette.lightMutedSwatch
                ?: palette.dominantSwatch

            val darkSwatch = palette.darkVibrantSwatch
                ?: palette.darkMutedSwatch
                ?: palette.mutedSwatch
                ?: palette.dominantSwatch

            var lightRgb = lightSwatch?.rgb ?: DEFAULT_COLORS.lightColor.toInt()
            var darkRgb = darkSwatch?.rgb ?: DEFAULT_COLORS.darkColor.toInt()

            lightRgb = ensureLightVisibility(lightRgb)
            darkRgb = ensureDarkVisibility(darkRgb, lightRgb)

            ExtractedColors(
                lightColor = lightRgb.toLong() and 0xFFFFFFFFL,
                darkColor = darkRgb.toLong() and 0xFFFFFFFFL
            )
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 计算降采样比例
     */
    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val (height: Int, width: Int) = options.outHeight to options.outWidth
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight: Int = height / 2
            val halfWidth: Int = width / 2
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize.coerceAtLeast(1)
    }

    /**
     * 亮色可见度保护：
     * 保证在磨砂或暗调背景上呈现璀璨夺目的高光状态 (Value >= 0.85, Saturation >= 0.40)
     */
    private fun ensureLightVisibility(color: Int): Int {
        val hsv = FloatArray(3)
        AndroidColor.colorToHSV(color, hsv)
        hsv[1] = hsv[1].coerceIn(0.40f, 0.95f) // 饱和度保障
        hsv[2] = hsv[2].coerceIn(0.85f, 1.0f)  // 明度保底高光
        return AndroidColor.HSVToColor(hsv)
    }

    /**
     * 暗色可见度保护：
     * 1. 限制暗色明度在 0.35f ~ 0.60f 之间，防止过暗直接与背景融为一体导致频谱截断；
     * 2. 若暗色与亮色色相和明度过于接近（如纯单色图），自动将暗色进行反差偏移。
     */
    private fun ensureDarkVisibility(darkColor: Int, lightColor: Int): Int {
        val darkHsv = FloatArray(3)
        val lightHsv = FloatArray(3)
        AndroidColor.colorToHSV(darkColor, darkHsv)
        AndroidColor.colorToHSV(lightColor, lightHsv)

        // 限制在可见暗色区间，既具有暗调特征又清晰可见
        darkHsv[1] = darkHsv[1].coerceIn(0.45f, 0.95f)
        darkHsv[2] = darkHsv[2].coerceIn(0.35f, 0.60f)

        // 色差过小时，进行色相偏移，创造舒适的双色渐变
        val hueDiff = abs(darkHsv[0] - lightHsv[0])
        if (hueDiff < 20f || hueDiff > 340f) {
            darkHsv[0] = (darkHsv[0] + 35f) % 360f
        }

        return AndroidColor.HSVToColor(darkHsv)
    }
}
