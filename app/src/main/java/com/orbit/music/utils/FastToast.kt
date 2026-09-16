package com.orbit.music.utils

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.annotation.StringRes

/**
 * 快速轻量 Toast 工具类
 *
 * 解决原生 Toast.LENGTH_SHORT（约2000ms）停留时间过长以及连续点击排队堆叠显示的体验痛点。
 * 默认显示时长约 800ms，连续点击时立即取消上一条并重置计时，实现即时更新与干脆利落的自动消失。
 */
object FastToast {
    private var activeToast: Toast? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var dismissRunnable: Runnable? = null

    /**
     * 弹出快速自动退场的 Toast
     *
     * @param context 上下文
     * @param text 显示文本
     * @param durationMs 停留时长（毫秒），默认为 800ms
     */
    fun show(context: Context, text: CharSequence, durationMs: Long = 800L) {
        dismissRunnable?.let { mainHandler.removeCallbacks(it) }
        try {
            activeToast?.cancel()
        } catch (_: Exception) {}

        val toast = Toast.makeText(context.applicationContext, text, Toast.LENGTH_SHORT)
        activeToast = toast
        toast.show()

        val runnable = Runnable {
            if (activeToast == toast) {
                try {
                    toast.cancel()
                } catch (_: Exception) {}
                activeToast = null
            }
        }
        dismissRunnable = runnable
        mainHandler.postDelayed(runnable, durationMs)
    }

    /**
     * 弹出快速自动退场的 Toast（资源 ID 格式）
     *
     * @param context 上下文
     * @param resId 字符串资源 ID
     * @param durationMs 停留时长（毫秒），默认为 800ms
     */
    fun show(context: Context, @StringRes resId: Int, durationMs: Long = 800L) {
        show(context, context.getString(resId), durationMs)
    }
}
