package com.example.watermeter

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

class UsageGraphView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var data: List<UsageEntry> = emptyList()
    
    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#4DFFFFFF") // 30% white
        style = Paint.Style.FILL
    }

    private val activeBarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFEB3B") // Yellow for current/max
        style = Paint.Style.FILL
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 28f
        textAlign = Paint.Align.CENTER
    }

    private val datePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#CCFFFFFF") // 80% white
        textSize = 24f
        textAlign = Paint.Align.CENTER
    }

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        strokeWidth = 2f
        style = Paint.Style.STROKE
    }

    fun setData(newData: List<UsageEntry>) {
        this.data = newData
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (data.isEmpty()) return

        val padding = 50f
        val bottomPadding = 80f 
        val topPadding = 60f
        val graphWidth = width - 2 * padding
        val graphHeight = height - topPadding - bottomPadding

        val maxUsage = data.maxOfOrNull { it.usage } ?: 10.0
        val displayMax = if (maxUsage == 0.0) 10.0 else maxUsage * 1.2
        
        val barCount = data.size
        val barWidth = (graphWidth / barCount) * 0.7f
        val spacing = (graphWidth / barCount) * 0.3f

        for (i in data.indices) {
            val usage = data[i].usage
            val barHeight = (usage / displayMax) * graphHeight
            
            val left = padding + i * (barWidth + spacing) + spacing / 2
            val top = (height - bottomPadding - barHeight).toFloat()
            val right = left + barWidth
            val bottom = height - bottomPadding

            val rect = RectF(left, top, right, bottom)
            
            val paint = if (i == data.size - 1) activeBarPaint else barPaint
            
            canvas.drawRoundRect(rect, 10f, 10f, paint)

            canvas.drawText(String.format("%.1f", usage), left + barWidth / 2, top - 15f, textPaint)

            canvas.drawText(data[i].date, left + barWidth / 2, height - bottomPadding + 40f, datePaint)
        }

        canvas.drawLine(padding, height - bottomPadding, width - padding, height - bottomPadding, linePaint)
    }
}
