package com.azime.input.ui.keyboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View

class KeyboardView(context: Context) : View(context) {
    
    private val paint = Paint().apply {
        color = Color.BLACK
        textSize = 48f
        isAntiAlias = true
    }
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // Draw keyboard layout
        canvas.drawColor(Color.LTGRAY)
        canvas.drawText("AZime Keyboard", 50f, 100f, paint)
    }
    
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = (width * 0.4).toInt() // 40% of screen width
        setMeasuredDimension(width, height)
    }
}
