package com.orbit.music.car

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * 车载系统广播接收器 (适配吉利/领克 Flyme Auto / 亿咖通专用系统广播)
 * 参考 Flyme Auto 版 QQ音乐 BootBroadReceiver 设计
 *
 * 核心功能：
 * 1. 监听车机中控源切换广播 BROADCAST_MEDIA_CENTER
 * 2. 监听车机开机/休眠唤醒广播 (STRMODE / BOOT_COMPLETED)
 * 3. 实时激活车载桥接并同步播放焦点
 */
class OrbitCarBootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null) return
        val action = intent.action ?: return
        Log.i(TAG, "★ 收到车机系统广播: action=$action")

        // 确保车载媒体中心桥接已初始化
        EcarxMediaBridge.init(context)

        when (action) {
            ACTION_BROADCAST_MEDIA_CENTER -> {
                val sourceType = intent.getIntExtra("sourceType", -1)
                Log.i(TAG, "★ 车机中控音源广播切换: sourceType=$sourceType")
                EcarxMediaBridge.onCarSourceSwitched(sourceType)
            }

            ACTION_STRMODE,
            Intent.ACTION_BOOT_COMPLETED -> {
                Log.i(TAG, "车机开机/熄火唤醒，同步车载媒体中心状态")
                EcarxMediaBridge.pushCurrentStateToCar()
            }
        }
    }

    companion object {
        private const val TAG = "OrbitCarBootReceiver"
        const val ACTION_BROADCAST_MEDIA_CENTER = "ecarx.xsf.mediacenter.action.BROADCAST_MEDIA_CENTER"
        const val ACTION_BROADCAST_MEDIA_CENTER_SEMANTIC = "ecarx.xsf.mediacenter.action.BROADCAST_MEDIA_CENTER.SEMANTIC"
        const val ACTION_STRMODE = "ecarx.intent.action.power.STRMODE"
    }
}
