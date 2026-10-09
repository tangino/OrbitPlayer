package com.orbit.music

import android.app.Application
import android.util.Log
import com.orbit.music.native.NativeDSP
import com.orbit.music.service.EqualizerService

class EqualizerApp : Application() {

    companion object {
        lateinit var instance: EqualizerApp
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        Log.i("EqualizerApp", "Initializing Equalizer Application")
        
        // 预加载 NativeDSP 引擎
        NativeDSP.instance

        // 启动后台常驻音频监听服务
        EqualizerService.start(this)

        // 预初始化平台账号管理器，确保冷启动即可自动恢复 QQ/酷狗等平台的登录 Cookie 鉴权
        try {
            com.orbit.music.data.online.auth.PlatformAccountManager.getInstance(this)
        } catch (e: Exception) {
            Log.e("EqualizerApp", "初始化平台账号管理器失败: ${e.message}", e)
        }

        // 初始化车载媒体中心通信桥接 (适配吉利/领克/极氪/银河等车机系统播放源下拉菜单与控制)
        try {
            com.orbit.music.car.EcarxMediaBridge.init(this)
        } catch (e: Throwable) {
            Log.w("EqualizerApp", "初始化车载媒体中心桥接失败: ${e.message}")
        }
    }
}
