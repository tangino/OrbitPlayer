package com.orbit.music.ui.utils

import android.content.Context
import android.os.Build
import android.util.DisplayMetrics
import android.view.WindowManager

/**
 * 界面与车机低 DPI 高分屏自适应缩放计算工具
 */
object UiScaleHelper {

    const val MODE_AUTO = "auto"
    const val MODE_080 = "0.8"
    const val MODE_090 = "0.9"
    const val MODE_100 = "1.0"
    const val MODE_110 = "1.1"
    const val MODE_125 = "1.25"
    const val MODE_150 = "1.5"
    const val MODE_175 = "1.75"
    const val MODE_200 = "2.0"
    const val MODE_225 = "2.25"

    /**
     * 根据当前选择的缩放模式与屏幕物理指标，计算实际 UI 缩放比例因子
     *
     * @param mode 缩放模式: "auto", "0.8", "0.9", "1.0", "1.1", "1.25", "1.5", "1.75", "2.0", "2.25"
     * @param widthPixels 物理屏幕宽
     * @param heightPixels 物理屏幕高
     * @param systemDensityDpi 系统报告的原始屏幕密度 DPI
     * @return UI 缩放倍率 (例如 0.8f, 1.0f, 1.75f 等)
     */
    fun calculateScaleFactor(
        mode: String,
        widthPixels: Int,
        heightPixels: Int,
        systemDensityDpi: Int
    ): Float {
        if (mode != MODE_AUTO) {
            return mode.toFloatOrNull()?.coerceIn(0.6f, 3.0f) ?: 1.0f
        }

        val longEdge = maxOf(widthPixels, heightPixels)
        val shortEdge = minOf(widthPixels, heightPixels)

        // 智能车机与低 DPI 高分屏检测算法:
        // 当系统密度配置为 mdpi (<= 170 DPI)，但屏幕物理像素较大时 (如 2560x1600, 2560x1440, 1920x1080 车机)
        if (systemDensityDpi <= 175) {
            return when {
                longEdge >= 3200 -> 2.25f // 4K 级别大屏 (等效 360 DPI)
                longEdge >= 2200 -> 1.75f // 2.5K / 2K 屏幕 (如 2560x1600/1440，等效 280 DPI，最佳车机触控尺寸)
                longEdge >= 1800 -> 1.40f // 1080P/1200P 屏幕 (等效 224 DPI)
                else -> 1.0f
            }
        } else if (systemDensityDpi <= 215) {
            // 中低密度 2K/4K 车机补偿
            return when {
                longEdge >= 3200 -> 1.80f
                longEdge >= 2400 -> 1.35f
                else -> 1.0f
            }
        }

        // 正常手机或已配置合理 DPI 的设备保持原生 1.0x
        return 1.0f
    }

    /**
     * 获取设备物理真实像素 DisplayMetrics
     */
    fun getRealDisplayMetrics(context: Context): DisplayMetrics {
        val metrics = DisplayMetrics()
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                context.display?.getRealMetrics(metrics)
            } catch (_: Exception) {
                @Suppress("DEPRECATION")
                wm?.defaultDisplay?.getRealMetrics(metrics)
            }
        } else {
            @Suppress("DEPRECATION")
            wm?.defaultDisplay?.getRealMetrics(metrics)
        }
        if (metrics.widthPixels == 0 || metrics.heightPixels == 0) {
            val dm = context.resources.displayMetrics
            metrics.widthPixels = dm.widthPixels
            metrics.heightPixels = dm.heightPixels
            metrics.density = dm.density
            metrics.densityDpi = dm.densityDpi
            metrics.scaledDensity = dm.scaledDensity
        }
        return metrics
    }

    /**
     * 将缩放比例同步应用到 Activity 与 Application 的 Resources / DisplayMetrics 中，
     * 确保所有的系统级子窗口 (如 Dialog、DropdownMenu、PopupWindow、Toast 等) 均自动生效缩放
     */
    fun applyActivityDensity(activity: android.app.Activity, scaleFactor: Float) {
        try {
            val realMetrics = getRealDisplayMetrics(activity)
            val baseDensityDpi = if (realMetrics.densityDpi > 0) realMetrics.densityDpi else 160
            val targetDensityDpi = (baseDensityDpi * scaleFactor).toInt()
            val targetDensity = (baseDensityDpi / 160f) * scaleFactor
            val fontScale = activity.resources.configuration.fontScale

            val config = android.content.res.Configuration(activity.resources.configuration).apply {
                this.densityDpi = targetDensityDpi
                if (targetDensity > 0f) {
                    val isPortrait = orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT
                    val wPx = if (isPortrait) minOf(realMetrics.widthPixels, realMetrics.heightPixels) else maxOf(realMetrics.widthPixels, realMetrics.heightPixels)
                    val hPx = if (isPortrait) maxOf(realMetrics.widthPixels, realMetrics.heightPixels) else minOf(realMetrics.widthPixels, realMetrics.heightPixels)
                    screenWidthDp = (wPx / targetDensity).toInt()
                    screenHeightDp = (hPx / targetDensity).toInt()
                    smallestScreenWidthDp = minOf(screenWidthDp, screenHeightDp)
                }
            }

            val dm = activity.resources.displayMetrics
            dm.density = targetDensity
            dm.densityDpi = targetDensityDpi
            dm.scaledDensity = targetDensity * fontScale

            @Suppress("DEPRECATION")
            activity.resources.updateConfiguration(config, dm)

            // 同步 Application 级 Resources
            val appDm = activity.applicationContext.resources.displayMetrics
            appDm.density = targetDensity
            appDm.densityDpi = targetDensityDpi
            appDm.scaledDensity = targetDensity * fontScale
            val appConfig = android.content.res.Configuration(activity.applicationContext.resources.configuration).apply {
                this.densityDpi = targetDensityDpi
            }
            @Suppress("DEPRECATION")
            activity.applicationContext.resources.updateConfiguration(appConfig, appDm)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

