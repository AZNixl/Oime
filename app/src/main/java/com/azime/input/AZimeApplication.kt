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
        
        // Initialize RIME engine (placeholder)
        // RimeManager.initialize(this)
    }
}
