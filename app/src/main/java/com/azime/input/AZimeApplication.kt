package com.azime.input

import android.app.Application
import com.azime.input.core.keyboard.KeyboardManager
import com.azime.input.core.storage.StorageManager
import com.azime.input.core.rime.RimeManager

class AZimeApplication : Application() {
    
    companion object {
        lateinit var instance: AZimeApplication
            private set
    }
    
    override fun onCreate() {
        super.onCreate()
        instance = this
        
        // Initialize storage directories
        StorageManager.initializeDirectories(this)

        // 键盘布局持久化（设置页/编辑器可能先于输入法服务启动）
        KeyboardManager.initialize(this)

        // 轮19.83：**日志系统**（Documents/Oime/logs/）+ 全局崩溃捕获
        com.azime.input.core.diag.Diag.init(StorageManager.logsDir, KeyboardManager.verboseLog())
        com.azime.input.core.diag.Diag.installCrashHandler()
        com.azime.input.core.diag.Diag.info(
            "App",
            "Application.onCreate · pkg=${'$'}{packageName} · logs=${'$'}{StorageManager.logsDir.absolutePath}" +
                " · verbose=${'$'}{KeyboardManager.verboseLog()}",
        )
        
        // Initialize RIME engine (placeholder)
        // RimeManager.initialize(this)
    }
}
