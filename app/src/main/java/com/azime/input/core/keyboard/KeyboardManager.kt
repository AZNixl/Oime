package com.azime.input.core.keyboard

import com.azime.input.data.model.KeyboardLayout

class KeyboardManager {
    
    private var currentLayout: KeyboardLayout? = null
    
    fun loadLayout(layoutName: String): KeyboardLayout? {
        // TODO: Load keyboard layout from storage
        return null
    }
    
    fun saveLayout(layout: KeyboardLayout) {
        // TODO: Save keyboard layout to storage
    }
    
    fun getDefaultLayout(): KeyboardLayout {
        return KeyboardLayout(
            name = "default",
            rows = emptyList()
        )
    }
}
