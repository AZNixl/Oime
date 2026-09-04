package com.azime.input

import android.app.Application
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
        
        // Initialize RIME engine (placeholder)
        // RimeManager.initialize(this)
    }
}
