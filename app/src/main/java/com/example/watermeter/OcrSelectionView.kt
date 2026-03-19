package com.example.watermeter

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import com.google.mlkit.vision.text.Text

class OcrSelectionView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var imageBitmap: Bitmap? = null
    private var textElements: List<Text.Element> = emptyList()
    private var onTextSelected: ((String) -> Unit)? = null

    private var scaleFactor = 1f
    private var offsetLeft = 0f
    private var offsetTop = 0f

    private val boxPaint = Paint().apply {
        color = Color.parseColor("#40FFEB3B") 
        style = Paint.Style.FILL
    }

    private val lassoPaint = Paint().apply {
        color = Color.CYAN
        style = Paint.Style.STROKE
        strokeWidth = 5f
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }

    private val textPaint = Paint().apply {
        color = Color.BLACK
        textSize = 30f
        textAlign = Paint.Align.CENTER
    }

    private val drawPath = Path()
    private val matrix = Matrix()
    private val tempRectF = RectF()
    private val destRect = RectF()

    init {
        isClickable = true
        isFocusable = true
    }

    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean {
            return true
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            val imgX = (e.x - offsetLeft) / scaleFactor
            val imgY = (e.y - offsetTop) / scaleFactor

            textElements.forEach { element ->
                element.boundingBox?.let { box ->
                    if (box.contains(imgX.toInt(), imgY.toInt())) {
                        onTextSelected?.invoke(element.text)
                        return true
                    }
                }
            }
            return false
        }
        
        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            // Optional: single tap to select if lasso wasn't used
            return false
        }
    })

    fun setContent(bitmap: Bitmap, elements: List<Text.Element>, onSelected: (String) -> Unit) {
        this.imageBitmap = bitmap
        this.textElements = elements.filter { it.text.matches(Regex("[0-9,.]+")) }
        this.onTextSelected = onSelected
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        imageBitmap?.let { bitmap ->
            scaleFactor = (width.toFloat() / bitmap.width).coerceAtMost(height.toFloat() / bitmap.height)
            val scaledWidth = bitmap.width * scaleFactor
            val scaledHeight = bitmap.height * scaleFactor
            offsetLeft = (width - scaledWidth) / 2
            offsetTop = (height - scaledHeight) / 2

            destRect.set(offsetLeft, offsetTop, offsetLeft + scaledWidth, offsetTop + scaledHeight)
            canvas.drawBitmap(bitmap, null, destRect, null)

            matrix.reset()
            matrix.postScale(scaleFactor, scaleFactor)
            matrix.postTranslate(offsetLeft, offsetTop)

            textElements.forEach { element ->
                element.boundingBox?.let { box ->
                    tempRectF.set(box)
                    matrix.mapRect(tempRectF)
                    canvas.drawRect(tempRectF, boxPaint)
                    canvas.drawText(element.text, tempRectF.centerX(), tempRectF.centerY() + 10, textPaint)
                }
            }

            canvas.drawPath(drawPath, lassoPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val handled = gestureDetector.onTouchEvent(event)
        
        val x = event.x
        val y = event.y

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                drawPath.reset()
                drawPath.moveTo(x, y)
            }
            MotionEvent.ACTION_MOVE -> {
                drawPath.lineTo(x, y)
            }
            MotionEvent.ACTION_UP -> {
                processLassoSelection()
                drawPath.reset()
                performClick()
            }
        }
        invalidate()
        return handled || true
    }

    private fun processLassoSelection() {
        val bounds = RectF()
        drawPath.computeBounds(bounds, true)
        
        if (bounds.width() < 20 && bounds.height() < 20) return

        val imgLeft = (bounds.left - offsetLeft) / scaleFactor
        val imgTop = (bounds.top - offsetTop) / scaleFactor
        val imgRight = (bounds.right - offsetLeft) / scaleFactor
        val imgBottom = (bounds.bottom - offsetTop) / scaleFactor
        val imgSelectionRect = Rect(imgLeft.toInt(), imgTop.toInt(), imgRight.toInt(), imgBottom.toInt())

        var bestMatch: Text.Element? = null
        var maxIntersection = 0

        textElements.forEach { element ->
            element.boundingBox?.let { box ->
                val intersection = Rect(box)
                if (intersection.intersect(imgSelectionRect)) {
                    val area = intersection.width() * intersection.height()
                    if (area > maxIntersection) {
                        maxIntersection = area
                        bestMatch = element
                    }
                }
            }
        }

        bestMatch?.let { onTextSelected?.invoke(it.text) }
    }

    override fun performClick(): Boolean {
        return super.performClick()
    }
}
