package com.example.shichak

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View

class FloatingToggleView(
    context: Context,
    private var isOverlayVisible: Boolean,
    private val onToggle: () -> Unit,
    private val onDrag: (dx: Float, dy: Float) -> Unit,
    private val onDragFinished: () -> Unit
) : View(context) {

    private val density = resources.displayMetrics.density
    private val buttonBounds = RectF()
    private val touchSlopPx = 8f * density

    private var isDragging = false
    private var dragStarted = false
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var downX = 0f
    private var downY = 0f

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0xCC1A5C1A.toInt()
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        textSize = 14f * density
        textAlign = Paint.Align.CENTER
    }

    init {
        setWillNotDraw(false)
    }

    fun setOverlayVisible(visible: Boolean) {
        isOverlayVisible = visible
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        buttonBounds.set(0f, 0f, w.toFloat(), h.toFloat())
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val radius = buttonBounds.height() / 2f
        canvas.drawRoundRect(buttonBounds, radius, radius, bgPaint)
        val label = if (isOverlayVisible) {
            context.getString(R.string.action_conceal)
        } else {
            context.getString(R.string.action_reveal)
        }
        val textY = buttonBounds.centerY() - (textPaint.descent() + textPaint.ascent()) / 2f
        canvas.drawText(label, buttonBounds.centerX(), textY, textPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (!buttonBounds.contains(event.x, event.y)) return false
                isDragging = false
                dragStarted = false
                downX = event.x
                downY = event.y
                lastTouchX = event.x
                lastTouchY = event.y
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - lastTouchX
                val dy = event.y - lastTouchY
                if (!dragStarted) {
                    val totalDx = event.x - downX
                    val totalDy = event.y - downY
                    if (kotlin.math.sqrt(totalDx * totalDx + totalDy * totalDy) >= touchSlopPx) {
                        dragStarted = true
                        isDragging = true
                    }
                }
                if (isDragging) {
                    onDrag(dx, dy)
                    lastTouchX = event.x
                    lastTouchY = event.y
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isDragging) {
                    onDragFinished()
                } else if (buttonBounds.contains(event.x, event.y)) {
                    onToggle()
                }
                isDragging = false
                dragStarted = false
                return true
            }
        }
        return super.onTouchEvent(event)
    }
}
