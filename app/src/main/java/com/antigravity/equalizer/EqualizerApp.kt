package com.antigravity.equalizer

import android.app.Application
import android.util.Log
import com.antigravity.equalizer.native.NativeDSP
import com.antigravity.equalizer.service.EqualizerService

class EqualizerApp : Application() {

    override fun onCreate() {
        super.onCreate()
        Log.i("EqualizerApp", "Initializing Equalizer Application")
        
        // 预加载 NativeDSP 引擎
        NativeDSP.instance

        // 启动后台常驻音频监听服务
        EqualizerService.start(this)
    }
}
