package com.example.shichak

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.min

class OverlayView(context: Context) : View(context) {

    var onHideRequested: (() -> Unit)? = null
    var onExitRequested: (() -> Unit)? = null
    var onPanelResize: ((PanelResizeEdge, Float, Float) -> Unit)? = null
    var onPanelResizeFinished: (() -> Unit)? = null

    var panelScreenX = 0f
        private set
    var panelScreenY = 0f
        private set

    private val density = resources.displayMetrics.density

    private val pocketRadiusPx = 6f * density
    private val handleVisualHalfPx = 10f * density
    private val handleHitHalfPx = 24f * density
    private val borderTouchPx = 28f * density
    private val panelEdgeTouchPx = 24f * density
    private val panelTopInsetPx = 52f * density
    private val panelContentMarginPx = 8f * density
    private val aimHandleRadiusPx = 8f * density
    private val aimHitRadiusPx = 24f * density
    private val aimFingerOffsetPx = 48f * density
    private val minRectSizePx = 80f * density
    private val aimExtensionThresholdPx = 8f * density
    private val dashEffect = DashPathEffect(floatArrayOf(20f * density, 10f * density), 0f)

    private var rectLeft = 0f
    private var rectTop = 0f
    private var rectRight = 0f
    private var rectBottom = 0f
    private var isRectLocked = false

    private var aimPoint1 = PointF(0f, 0f)
    private var aimPoint2 = PointF(0f, 0f)
    private var layoutRestoredOnce = false

    private var activeTarget = TouchTarget.NONE
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var aimGrabOffsetX = 0f
    private var aimGrabOffsetY = 0f
    private var rectDirty = false

    private val rectBounds = RectF()

    private val rectBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
        color = 0xB4FFFFFF.toInt()
    }
    private val pocketPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0xB4FFFFFF.toInt()
    }
    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0x99FFFFFF.toInt()
    }
    private val aimLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val aimExtensionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        pathEffect = dashEffect
        strokeCap = Paint.Cap.ROUND
    }
    private val aimStartCrossPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f * density
        color = 0xFFFF3333.toInt()
        strokeCap = Paint.Cap.ROUND
    }
    private val aimEndHandlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0xFF33CC33.toInt()
    }
    private val reboundPaints = Array(5) {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            pathEffect = dashEffect
            strokeCap = Paint.Cap.ROUND
        }
    }
    private val hudBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0xCC000000.toInt()
    }
    private val hudTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        textSize = 12f * density
    }

    private companion object {
        const val COLOR_AIM_LINE = 0xFFFFFFFF.toInt()
        val REBOUND_COLORS = intArrayOf(
            0xFFFFD700.toInt(),
            0xFFFF8C00.toInt(),
            0xFFFF3333.toInt(),
            0xFFAA66FF.toInt(),
            0xFF33CCCC.toInt()
        )
    }
    private val buttonBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0x99000000.toInt()
    }
    private val buttonTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        textSize = 14f * density
        textAlign = Paint.Align.CENTER
    }
    private val panelBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f * density
        color = 0xCC4FC3F7.toInt()
    }
    private val panelHandlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0xCC4FC3F7.toInt()
    }

    private val lockButtonBounds = RectF()
    private val exitButtonBounds = RectF()
    private val hideButtonBounds = RectF()
    private val resetButtonBounds = RectF()

    private enum class TouchTarget {
        NONE,
        HIDE,
        EXIT,
        LOCK,
        RESET,
        CONSUME_ALL,
        AIM_1,
        AIM_2,
        CORNER_TL,
        CORNER_TR,
        CORNER_BL,
        CORNER_BR,
        SIDE_TOP,
        SIDE_BOTTOM,
        SIDE_LEFT,
        SIDE_RIGHT,
        RECT_MOVE,
        PANEL_LEFT,
        PANEL_RIGHT,
        PANEL_TOP,
        PANEL_BOTTOM
    }

    init {
        setWillNotDraw(false)
        isClickable = true
        isFocusable = false
        isRectLocked = ShichakPrefs.isLayoutLocked(context)
    }

    fun setPanelScreenOrigin(x: Float, y: Float) {
        panelScreenX = x
        panelScreenY = y
        if (width > 0 && height > 0) {
            applyStoredLayout(width.toFloat(), height.toFloat())
            layoutButtons(width.toFloat(), height.toFloat())
            if (!isRectLocked) {
                clampTableRectToPanel(width.toFloat(), height.toFloat())
            }
            clampAimPointsToPanel(width.toFloat(), height.toFloat())
            invalidate()
        }
    }

    fun isLayoutLocked(): Boolean = isRectLocked

    fun reloadFromProfile() {
        layoutRestoredOnce = false
        isRectLocked = ShichakPrefs.isLayoutLocked(context)
        if (width > 0 && height > 0) {
            applyStoredLayout(width.toFloat(), height.toFloat())
            layoutButtons(width.toFloat(), height.toFloat())
            if (!isRectLocked) {
                clampTableRectToPanel(width.toFloat(), height.toFloat())
            }
            clampAimPointsToPanel(width.toFloat(), height.toFloat())
        }
        invalidate()
    }

    private data class AimGeometry(
        val reboundSegments: List<Pair<PointF, PointF>>,
        val firstHit: PointF?,
        val firstHitSide: Side?,
        val incomingDirection: PointF,
        val firstBounceDirection: PointF?,
        val distanceToFirstHit: Float
    )

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w <= 0 || h <= 0) return

        val panelW = w.toFloat()
        val panelH = h.toFloat()
        val sizeChanged = w != oldw || h != oldh
        if (!layoutRestoredOnce || sizeChanged) {
            applyStoredLayout(panelW, panelH)
            layoutRestoredOnce = true
        }

        layoutButtons(panelW, panelH)
        if (!isRectLocked) {
            clampTableRectToPanel(panelW, panelH)
        }
        clampAimPointsToPanel(panelW, panelH)
    }

    private fun applyStoredLayout(panelW: Float, panelH: Float) {
        if (panelW <= 0f || panelH <= 0f) return

        if (!restoreTableRect(panelW, panelH)) {
            setDefaultRect(panelW, panelH)
            isRectLocked = false
            saveRectPreferences()
        }

        val aimMargins = ShichakPrefs.loadAimMargins(context)
        if (aimMargins != null) {
            val (screenW, screenH) = ScreenLayout.screenSize(context)
            if (screenW > 0 && screenH > 0) {
                val p1 = ScreenLayout.marginsToPoint(aimMargins.first)
                val p2 = ScreenLayout.marginsToPoint(aimMargins.second)
                aimPoint1 = PointF(p1.x - panelScreenX, p1.y - panelScreenY)
                aimPoint2 = PointF(p2.x - panelScreenX, p2.y - panelScreenY)
            } else {
                setDefaultAimLine(panelW, panelH)
                saveAimPreferences()
            }
        } else {
            setDefaultAimLine(panelW, panelH)
            saveAimPreferences()
        }
    }

    private fun restoreTableRect(panelW: Float, panelH: Float): Boolean {
        ShichakPrefs.loadNormalizedTableRect(context)?.let { normalized ->
            applyNormalizedTableRect(normalized, panelW, panelH)
            return isTableRectValid(panelW, panelH)
        }

        val legacyMargins = ShichakPrefs.loadLegacyTableMargins(context) ?: return false
        val (screenW, screenH) = ScreenLayout.screenSize(context)
        if (screenW <= 0 || screenH <= 0) return false

        val screenRect = ScreenLayout.marginsToRect(legacyMargins, screenW, screenH)
        rectLeft = screenRect.left - panelScreenX
        rectTop = screenRect.top - panelScreenY
        rectRight = screenRect.right - panelScreenX
        rectBottom = screenRect.bottom - panelScreenY

        if (!isTableRectValid(panelW, panelH)) {
            return false
        }

        saveRectPreferences()
        return true
    }

    private fun applyNormalizedTableRect(normalized: NormalizedTableRect, panelW: Float, panelH: Float) {
        val rect = normalized.toRect(panelW, panelH)
        rectLeft = rect.left
        rectTop = rect.top
        rectRight = rect.right
        rectBottom = rect.bottom
    }

    private fun isTableRectValid(panelW: Float, panelH: Float): Boolean {
        val w = rectRight - rectLeft
        val h = rectBottom - rectTop
        if (w <= 0f || h <= 0f) return false
        val minTop = panelTopInsetPx + panelContentMarginPx
        val maxW = panelW - 2f * panelContentMarginPx
        val maxH = panelH - minTop - panelContentMarginPx
        if (maxW <= 0f || maxH <= 0f) return false
        val effectiveMinW = min(minRectSizePx, maxW)
        val effectiveMinH = min(minRectSizePx, maxH)
        return w >= effectiveMinW * 0.5f && h >= effectiveMinH * 0.5f
    }

    private fun screenTableRect(): RectF = RectF(
        panelScreenX + rectLeft,
        panelScreenY + rectTop,
        panelScreenX + rectRight,
        panelScreenY + rectBottom
    )

    private fun clampTableRectToPanel(panelW: Float, panelH: Float) {
        if (isRectLocked || panelW <= 0f || panelH <= 0f) return

        val minTop = panelTopInsetPx + panelContentMarginPx
        val maxW = panelW - 2f * panelContentMarginPx
        val maxH = panelH - minTop - panelContentMarginPx
        if (maxW <= 0f || maxH <= 0f) return

        val effectiveMinW = min(minRectSizePx, maxW)
        val effectiveMinH = min(minRectSizePx, maxH)

        var changed = false
        var rectW = rectRight - rectLeft
        var rectH = rectBottom - rectTop

        if (rectW < effectiveMinW) {
            val cx = (rectLeft + rectRight) / 2f
            rectLeft = cx - effectiveMinW / 2f
            rectRight = cx + effectiveMinW / 2f
            rectW = effectiveMinW
            changed = true
        }
        if (rectH < effectiveMinH) {
            val cy = (rectTop + rectBottom) / 2f
            rectTop = cy - effectiveMinH / 2f
            rectBottom = cy + effectiveMinH / 2f
            rectH = effectiveMinH
            changed = true
        }

        if (rectW > maxW) {
            val cx = (rectLeft + rectRight) / 2f
            val half = maxW / 2f
            rectLeft = cx - half
            rectRight = cx + half
            changed = true
        }
        if (rectH > maxH) {
            val cy = (rectTop + rectBottom) / 2f
            val half = maxH / 2f
            rectTop = cy - half
            rectBottom = cy + half
            changed = true
        }

        if (rectLeft < panelContentMarginPx) {
            val shift = panelContentMarginPx - rectLeft
            rectLeft += shift
            rectRight += shift
            changed = true
        }
        if (rectTop < minTop) {
            val shift = minTop - rectTop
            rectTop += shift
            rectBottom += shift
            changed = true
        }
        if (rectRight > panelW - panelContentMarginPx) {
            val shift = rectRight - (panelW - panelContentMarginPx)
            rectLeft -= shift
            rectRight -= shift
            changed = true
        }
        if (rectBottom > panelH - panelContentMarginPx) {
            val shift = rectBottom - (panelH - panelContentMarginPx)
            rectTop -= shift
            rectBottom -= shift
            changed = true
        }

        if (changed) {
            saveRectPreferences()
        }
    }

    private fun clampAimPointsToPanel(panelW: Float, panelH: Float) {
        if (panelW <= 0f || panelH <= 0f) return
        val minY = panelTopInsetPx
        aimPoint1 = PointF(
            aimPoint1.x.coerceIn(0f, panelW),
            aimPoint1.y.coerceIn(minY, panelH)
        )
        aimPoint2 = PointF(
            aimPoint2.x.coerceIn(0f, panelW),
            aimPoint2.y.coerceIn(minY, panelH)
        )
    }

    private fun setDefaultRect(width: Float, height: Float) {
        val landscape = width > height * 1.2f
        val marginX = width * if (landscape) 0.08f else 0.15f
        val marginY = height * if (landscape) 0.12f else 0.25f
        val minTop = panelTopInsetPx + panelContentMarginPx
        rectLeft = marginX
        rectTop = maxOf(marginY, minTop)
        rectRight = width - marginX
        rectBottom = height - marginY
    }

    private fun setDefaultAimLine(width: Float, height: Float) {
        aimPoint1 = PointF(width * 0.38f, height * 0.44f)
        aimPoint2 = PointF(width * 0.62f, height * 0.56f)
    }

    private fun resetAimLine() {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        setDefaultAimLine(w, h)
        saveAimPreferences(sync = true)
        invalidate()
    }

    private fun saveRectPreferences(sync: Boolean = false) {
        val panelW = width.toFloat()
        val panelH = height.toFloat()
        if (panelW <= 0f || panelH <= 0f) return

        val normalized = NormalizedTableRect.fromRect(
            rectLeft,
            rectTop,
            rectRight,
            rectBottom,
            panelW,
            panelH
        )
        ShichakPrefs.saveNormalizedTableRect(context, normalized, isRectLocked, sync)
    }

    private fun saveAimPreferences(sync: Boolean = false) {
        val (screenW, screenH) = ScreenLayout.screenSize(context)
        if (screenW <= 0 || screenH <= 0) return
        val m1 = ScreenLayout.pointToMargins(
            panelScreenX + aimPoint1.x,
            panelScreenY + aimPoint1.y,
            screenW,
            screenH
        )
        val m2 = ScreenLayout.pointToMargins(
            panelScreenX + aimPoint2.x,
            panelScreenY + aimPoint2.y,
            screenW,
            screenH
        )
        ShichakPrefs.saveAimMargins(context, m1, m2, sync)
    }

    private fun layoutButtons(width: Float, height: Float) {
        val pad = 12f * density
        val btnH = 40f * density
        val lockW = 52f * density
        val exitW = 56f * density
        val hideW = 72f * density
        val resetW = 52f * density
        val gap = 8f * density

        lockButtonBounds.set(pad, pad, pad + lockW, pad + btnH)
        exitButtonBounds.set(
            lockButtonBounds.right + gap,
            pad,
            lockButtonBounds.right + gap + exitW,
            pad + btnH
        )
        resetButtonBounds.set(width - pad - resetW, pad, width - pad, pad + btnH)
        val topBarCenter = (exitButtonBounds.right + resetButtonBounds.left) / 2f
        hideButtonBounds.set(
            topBarCenter - hideW / 2f,
            pad,
            topBarCenter + hideW / 2f,
            pad + btnH
        )
    }

    private fun currentRect(): RectBounds =
        RectBounds(rectLeft, rectTop, rectRight, rectBottom)

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        rectBounds.set(rectLeft, rectTop, rectRight, rectBottom)

        drawPanelBorder(canvas)
        drawTable(canvas)
        val geometry = computeAimGeometry()
        if (geometry.reboundSegments.isNotEmpty()) {
            drawRebounds(canvas, geometry.reboundSegments)
        }
        drawAimLine(canvas)
        if (ShichakPrefs.isAngleHudEnabled(context)) {
            drawAngleHud(canvas, geometry)
        }
        drawButtons(canvas)
    }

    private fun drawPanelBorder(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        canvas.drawRect(0f, 0f, w, h, panelBorderPaint)

        if (!isRectLocked) {
            drawPanelHandle(canvas, 0f, h / 2f)
            drawPanelHandle(canvas, w, h / 2f)
            drawPanelHandle(canvas, w / 2f, 0f)
            drawPanelHandle(canvas, w / 2f, h)
        }
    }

    private fun drawPanelHandle(canvas: Canvas, x: Float, y: Float) {
        canvas.drawRect(
            x - handleVisualHalfPx,
            y - handleVisualHalfPx,
            x + handleVisualHalfPx,
            y + handleVisualHalfPx,
            panelHandlePaint
        )
    }

    private fun drawTable(canvas: Canvas) {
        canvas.drawRect(rectBounds, rectBorderPaint)

        val cx = rectBounds.centerX()
        val cy = rectBounds.centerY()
        val pockets = arrayOf(
            PointF(rectLeft, rectTop),
            PointF(rectRight, rectTop),
            PointF(rectLeft, rectBottom),
            PointF(rectRight, rectBottom),
            PointF(rectLeft, cy),
            PointF(rectRight, cy)
        )
        for (pocket in pockets) {
            canvas.drawCircle(pocket.x, pocket.y, pocketRadiusPx, pocketPaint)
        }

        if (!isRectLocked) {
            drawHandle(canvas, rectLeft, rectTop)
            drawHandle(canvas, rectRight, rectTop)
            drawHandle(canvas, rectLeft, rectBottom)
            drawHandle(canvas, rectRight, rectBottom)
            drawHandle(canvas, cx, rectTop)
            drawHandle(canvas, cx, rectBottom)
            drawHandle(canvas, rectLeft, cy)
            drawHandle(canvas, rectRight, cy)
        }
    }

    private fun drawHandle(canvas: Canvas, x: Float, y: Float) {
        canvas.drawRect(
            x - handleVisualHalfPx,
            y - handleVisualHalfPx,
            x + handleVisualHalfPx,
            y + handleVisualHalfPx,
            handlePaint
        )
    }

    private fun resolveAimEndpoints(): Pair<PointF, PointF> {
        val rect = currentRect()
        val dist1 = rect.distanceTo(aimPoint1)
        val dist2 = rect.distanceTo(aimPoint2)
        return if (dist1 >= dist2) {
            Pair(aimPoint1, aimPoint2)
        } else {
            Pair(aimPoint2, aimPoint1)
        }
    }

    private fun applyLinePaintSettings() {
        val strokePx = ShichakPrefs.lineThicknessPx(context)
        aimLinePaint.strokeWidth = strokePx
        aimExtensionPaint.strokeWidth = strokePx
        reboundPaints.forEach { it.strokeWidth = strokePx }

        val aimAlpha = ShichakPrefs.aimLineAlpha(context)
        val reboundAlpha = ShichakPrefs.reboundAlpha(context)
        aimLinePaint.color = withAlpha(COLOR_AIM_LINE, aimAlpha)
        aimExtensionPaint.color = withAlpha(COLOR_AIM_LINE, aimAlpha)
        REBOUND_COLORS.forEachIndexed { index, color ->
            reboundPaints[index].color = withAlpha(color, reboundAlpha)
        }
    }

    private fun withAlpha(rgb: Int, alpha: Int): Int {
        return (rgb and 0x00FFFFFF) or (alpha.coerceIn(0, 255) shl 24)
    }

    private fun drawAimLine(canvas: Canvas) {
        applyLinePaintSettings()
        val (start, end) = resolveAimEndpoints()
        canvas.drawLine(start.x, start.y, end.x, end.y, aimLinePaint)
        drawAimExtensionToBoundary(canvas, start, end)
        drawStartCross(canvas, start.x, start.y)
        canvas.drawCircle(end.x, end.y, aimHandleRadiusPx, aimEndHandlePaint)
    }

    private fun drawStartCross(canvas: Canvas, cx: Float, cy: Float) {
        val arm = aimHandleRadiusPx
        canvas.drawLine(cx - arm, cy, cx + arm, cy, aimStartCrossPaint)
        canvas.drawLine(cx, cy - arm, cx, cy + arm, aimStartCrossPaint)
    }

    private fun isAimPoint1Start(): Boolean {
        val rect = currentRect()
        return rect.distanceTo(aimPoint1) >= rect.distanceTo(aimPoint2)
    }

    private fun updateAimPoint1(x: Float, y: Float, dx: Float, dy: Float) {
        val factor = if (isAimPoint1Start()) {
            ShichakPrefs.redDragFactor(context)
        } else {
            ShichakPrefs.greenDragFactor(context)
        }
        aimPoint1 = if (factor >= 0.999f) {
            PointF(x + aimGrabOffsetX, y + aimGrabOffsetY)
        } else {
            applyDrag(aimPoint1, x, y, dx, dy, factor)
        }
    }

    private fun updateAimPoint2(x: Float, y: Float, dx: Float, dy: Float) {
        val factor = if (!isAimPoint1Start()) {
            ShichakPrefs.redDragFactor(context)
        } else {
            ShichakPrefs.greenDragFactor(context)
        }
        aimPoint2 = if (factor >= 0.999f) {
            PointF(x + aimGrabOffsetX, y + aimGrabOffsetY)
        } else {
            applyDrag(aimPoint2, x, y, dx, dy, factor)
        }
    }

    private fun captureAimGrabOffset(aimPoint: PointF, touchX: Float, touchY: Float) {
        val fingerOffset = if (ShichakPrefs.isAimDragOffsetEnabled(context)) aimFingerOffsetPx else 0f
        aimGrabOffsetX = aimPoint.x - touchX
        aimGrabOffsetY = aimPoint.y - touchY - fingerOffset
    }

    private fun applyDrag(
        current: PointF,
        x: Float,
        y: Float,
        dx: Float,
        dy: Float,
        factor: Float
    ): PointF {
        return if (factor >= 0.999f) {
            PointF(x, y)
        } else {
            PointF(current.x + dx * factor, current.y + dy * factor)
        }
    }

    private fun drawAimExtensionToBoundary(canvas: Canvas, start: PointF, end: PointF) {
        val rect = currentRect()
        if (rect.width <= 0f || rect.height <= 0f) return

        val direction = normalize(end - start)
        if (direction.x == 0f && direction.y == 0f) return

        val hitResult = rayRectIntersection(start, direction, rect) ?: return
        val hit = hitResult.first

        val endAlongRay = (end.x - start.x) * direction.x + (end.y - start.y) * direction.y
        val hitAlongRay = (hit.x - start.x) * direction.x + (hit.y - start.y) * direction.y
        if (endAlongRay >= hitAlongRay - aimExtensionThresholdPx) return

        val gap = end.distanceTo(hit)
        if (gap <= aimExtensionThresholdPx) return

        canvas.drawLine(end.x, end.y, hit.x, hit.y, aimExtensionPaint)
    }

    private fun computeAimGeometry(): AimGeometry {
        val rect = currentRect()
        val empty = AimGeometry(emptyList(), null, null, PointF(0f, 0f), null, 0f)
        if (rect.width <= 0f || rect.height <= 0f) return empty

        val (p1, p2) = resolveAimEndpoints()
        val direction = normalize(p2 - p1)
        if (direction.x == 0f && direction.y == 0f) return empty

        val hit1Result = rayRectIntersection(p1, direction, rect) ?: return empty
        val hit1 = hit1Result.first
        val side1 = hit1Result.second
        val distanceToHit = p1.distanceTo(hit1)

        val maxCount = ShichakPrefs.reboundCount(context)
        if (maxCount <= 0) {
            return AimGeometry(emptyList(), hit1, side1, direction, null, distanceToHit)
        }

        val segments = mutableListOf<Pair<PointF, PointF>>()
        var currentDir = direction
        var prevHit = hit1
        var prevSide = side1
        var firstBounceDir: PointF? = null

        repeat(maxCount) { index ->
            val reflected = normalize(reflect(currentDir, prevSide))
            if (index == 0) firstBounceDir = reflected
            val origin = rayPointAt(prevHit, reflected, 2f)
            val nextResult = rayRectIntersection(origin, reflected, rect)
            val nextHit = if (nextResult != null) {
                nextResult.first
            } else {
                rayPointAt(prevHit, reflected, min(rect.width, rect.height) * 0.5f)
            }
            segments.add(prevHit to nextHit)
            if (nextResult == null) return@repeat
            prevHit = nextResult.first
            prevSide = nextResult.second
            currentDir = reflected
        }

        return AimGeometry(segments, hit1, side1, direction, firstBounceDir, distanceToHit)
    }

    private fun drawRebounds(canvas: Canvas, segments: List<Pair<PointF, PointF>>) {
        applyLinePaintSettings()
        segments.forEachIndexed { index, (from, to) ->
            if (index < reboundPaints.size) {
                canvas.drawLine(from.x, from.y, to.x, to.y, reboundPaints[index])
            }
        }
    }

    private fun drawAngleHud(canvas: Canvas, geometry: AimGeometry) {
        val side = geometry.firstHitSide ?: return

        val cutAngle = incidenceAngleDegrees(geometry.incomingDirection, side)
        val bankAngle = geometry.firstBounceDirection?.let { bankAngleDegrees(it) } ?: 0f
        val distPx = geometry.distanceToFirstHit

        val line1 = context.getString(R.string.hud_cut_angle, cutAngle)
        val line2 = context.getString(R.string.hud_bank_angle, bankAngle)
        val line3 = context.getString(R.string.hud_distance, distPx)

        val pad = 8f * density
        val lineHeight = hudTextPaint.textSize + 4f * density
        val textBlockHeight = lineHeight * 3f + pad * 2f
        val maxTextWidth = maxOf(
            hudTextPaint.measureText(line1),
            hudTextPaint.measureText(line2),
            hudTextPaint.measureText(line3)
        )
        val boxWidth = maxTextWidth + pad * 2f
        val boxLeft = pad
        val boxTop = height - textBlockHeight - pad

        canvas.drawRoundRect(
            boxLeft,
            boxTop,
            boxLeft + boxWidth,
            boxTop + textBlockHeight,
            6f * density,
            6f * density,
            hudBgPaint
        )

        var textY = boxTop + pad + hudTextPaint.textSize
        canvas.drawText(line1, boxLeft + pad, textY, hudTextPaint)
        textY += lineHeight
        canvas.drawText(line2, boxLeft + pad, textY, hudTextPaint)
        textY += lineHeight
        canvas.drawText(line3, boxLeft + pad, textY, hudTextPaint)
    }

    private fun drawButtons(canvas: Canvas) {
        drawPillButton(canvas, lockButtonBounds, if (isRectLocked) "🔒" else "🔓")
        drawPillButton(canvas, exitButtonBounds, context.getString(R.string.action_dismiss_short))
        drawPillButton(canvas, hideButtonBounds, context.getString(R.string.action_conceal))
        drawPillButton(canvas, resetButtonBounds, "↺")
    }

    private fun drawPillButton(canvas: Canvas, bounds: RectF, label: String) {
        val radius = bounds.height() / 2f
        canvas.drawRoundRect(bounds, radius, radius, buttonBgPaint)
        val textY = bounds.centerY() - (buttonTextPaint.descent() + buttonTextPaint.ascent()) / 2f
        canvas.drawText(label, bounds.centerX(), textY, buttonTextPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                activeTarget = hitTest(x, y)
                if (activeTarget == TouchTarget.NONE) {
                    activeTarget = TouchTarget.CONSUME_ALL
                    return true
                }
                lastTouchX = x
                lastTouchY = y
                rectDirty = false

                when (activeTarget) {
                    TouchTarget.AIM_1 -> captureAimGrabOffset(aimPoint1, x, y)
                    TouchTarget.AIM_2 -> captureAimGrabOffset(aimPoint2, x, y)
                    else -> Unit
                }

                when (activeTarget) {
                    TouchTarget.HIDE -> {
                        onHideRequested?.invoke()
                        activeTarget = TouchTarget.NONE
                        return true
                    }
                    TouchTarget.EXIT -> {
                        onExitRequested?.invoke()
                        activeTarget = TouchTarget.NONE
                        return true
                    }
                    TouchTarget.LOCK -> {
                        isRectLocked = !isRectLocked
                        saveRectPreferences(sync = true)
                        invalidate()
                        activeTarget = TouchTarget.NONE
                        return true
                    }
                    TouchTarget.RESET -> {
                        resetAimLine()
                        activeTarget = TouchTarget.NONE
                        return true
                    }
                    else -> return true
                }
            }

            MotionEvent.ACTION_MOVE -> {
                if (activeTarget == TouchTarget.NONE) return false
                if (activeTarget == TouchTarget.CONSUME_ALL) return true
                val dx = x - lastTouchX
                val dy = y - lastTouchY
                when (activeTarget) {
                    TouchTarget.AIM_1 -> updateAimPoint1(x, y, dx, dy)
                    TouchTarget.AIM_2 -> updateAimPoint2(x, y, dx, dy)
                    TouchTarget.CORNER_TL -> resizeCorner(dx, dy, moveLeft = true, moveTop = true)
                    TouchTarget.CORNER_TR -> resizeCorner(dx, dy, moveLeft = false, moveTop = true)
                    TouchTarget.CORNER_BL -> resizeCorner(dx, dy, moveLeft = true, moveTop = false)
                    TouchTarget.CORNER_BR -> resizeCorner(dx, dy, moveLeft = false, moveTop = false)
                    TouchTarget.SIDE_TOP -> {
                        rectTop = (rectTop + dy).coerceAtMost(rectBottom - minRectSizePx)
                        rectDirty = true
                    }
                    TouchTarget.SIDE_BOTTOM -> {
                        rectBottom = (rectBottom + dy).coerceAtLeast(rectTop + minRectSizePx)
                        rectDirty = true
                    }
                    TouchTarget.SIDE_LEFT -> {
                        rectLeft = (rectLeft + dx).coerceAtMost(rectRight - minRectSizePx)
                        rectDirty = true
                    }
                    TouchTarget.SIDE_RIGHT -> {
                        rectRight = (rectRight + dx).coerceAtLeast(rectLeft + minRectSizePx)
                        rectDirty = true
                    }
                    TouchTarget.RECT_MOVE -> {
                        rectLeft += dx
                        rectTop += dy
                        rectRight += dx
                        rectBottom += dy
                        rectDirty = true
                    }
                    TouchTarget.PANEL_LEFT -> onPanelResize?.invoke(PanelResizeEdge.LEFT, dx, 0f)
                    TouchTarget.PANEL_RIGHT -> onPanelResize?.invoke(PanelResizeEdge.RIGHT, dx, 0f)
                    TouchTarget.PANEL_TOP -> onPanelResize?.invoke(PanelResizeEdge.TOP, 0f, dy)
                    TouchTarget.PANEL_BOTTOM -> onPanelResize?.invoke(PanelResizeEdge.BOTTOM, 0f, dy)
                    else -> Unit
                }
                lastTouchX = x
                lastTouchY = y
                invalidate()
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val wasActive = activeTarget != TouchTarget.NONE
                val wasPanelResize = activeTarget == TouchTarget.PANEL_LEFT ||
                    activeTarget == TouchTarget.PANEL_RIGHT ||
                    activeTarget == TouchTarget.PANEL_TOP ||
                    activeTarget == TouchTarget.PANEL_BOTTOM
                val wasAimDrag = activeTarget == TouchTarget.AIM_1 ||
                    activeTarget == TouchTarget.AIM_2
                if (rectDirty) {
                    saveRectPreferences(sync = true)
                }
                if (wasAimDrag) {
                    saveAimPreferences(sync = true)
                }
                if (wasPanelResize) {
                    onPanelResizeFinished?.invoke()
                }
                activeTarget = TouchTarget.NONE
                rectDirty = false
                val w = width.toFloat()
                val h = height.toFloat()
                if (w > 0f && h > 0f) {
                    clampTableRectToPanel(w, h)
                    clampAimPointsToPanel(w, h)
                    layoutButtons(w, h)
                    invalidate()
                }
                return wasActive
            }
        }
        return false
    }

    private fun resizeCorner(dx: Float, dy: Float, moveLeft: Boolean, moveTop: Boolean) {
        if (moveLeft) {
            rectLeft = (rectLeft + dx).coerceAtMost(rectRight - minRectSizePx)
        } else {
            rectRight = (rectRight + dx).coerceAtLeast(rectLeft + minRectSizePx)
        }
        if (moveTop) {
            rectTop = (rectTop + dy).coerceAtMost(rectBottom - minRectSizePx)
        } else {
            rectBottom = (rectBottom + dy).coerceAtLeast(rectTop + minRectSizePx)
        }
        rectDirty = true
    }

    private fun hitTest(x: Float, y: Float): TouchTarget {
        if (hideButtonBounds.contains(x, y)) return TouchTarget.HIDE
        if (lockButtonBounds.contains(x, y)) return TouchTarget.LOCK
        if (exitButtonBounds.contains(x, y)) return TouchTarget.EXIT
        if (resetButtonBounds.contains(x, y)) return TouchTarget.RESET

        if (distance(x, y, aimPoint1.x, aimPoint1.y) <= aimHitRadiusPx) return TouchTarget.AIM_1
        if (distance(x, y, aimPoint2.x, aimPoint2.y) <= aimHitRadiusPx) return TouchTarget.AIM_2

        if (!isRectLocked && rectRight > rectLeft && rectBottom > rectTop) {
            val cx = (rectLeft + rectRight) / 2f
            val cy = (rectTop + rectBottom) / 2f

            hitHandle(x, y, rectLeft, rectTop)?.let { return it }
            hitHandle(x, y, rectRight, rectTop)?.let { return it }
            hitHandle(x, y, rectLeft, rectBottom)?.let { return it }
            hitHandle(x, y, rectRight, rectBottom)?.let { return it }
            hitSideHandle(x, y, cx, rectTop, TouchTarget.SIDE_TOP)?.let { return it }
            hitSideHandle(x, y, cx, rectBottom, TouchTarget.SIDE_BOTTOM)?.let { return it }
            hitSideHandle(x, y, rectLeft, cy, TouchTarget.SIDE_LEFT)?.let { return it }
            hitSideHandle(x, y, rectRight, cy, TouchTarget.SIDE_RIGHT)?.let { return it }

            if (isOnBorderBand(x, y)) return TouchTarget.RECT_MOVE
        }

        hitPanelEdge(x, y)?.let { return it }

        return TouchTarget.NONE
    }

    private fun hitHandle(x: Float, y: Float, hx: Float, hy: Float): TouchTarget? {
        if (distance(x, y, hx, hy) > handleHitHalfPx) return null
        return when {
            approx(hx, rectLeft) && approx(hy, rectTop) -> TouchTarget.CORNER_TL
            approx(hx, rectRight) && approx(hy, rectTop) -> TouchTarget.CORNER_TR
            approx(hx, rectLeft) && approx(hy, rectBottom) -> TouchTarget.CORNER_BL
            approx(hx, rectRight) && approx(hy, rectBottom) -> TouchTarget.CORNER_BR
            else -> null
        }
    }

    private fun hitSideHandle(x: Float, y: Float, hx: Float, hy: Float, target: TouchTarget): TouchTarget? {
        if (distance(x, y, hx, hy) <= handleHitHalfPx) return target
        return null
    }

    private fun hitPanelEdge(x: Float, y: Float): TouchTarget? {
        if (isRectLocked) return null
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return null

        // Only the cyan midpoint handles resize the panel — not full-edge strips (those blocked the table rect).
        if (distance(x, y, 0f, h / 2f) <= panelEdgeTouchPx) return TouchTarget.PANEL_LEFT
        if (distance(x, y, w, h / 2f) <= panelEdgeTouchPx) return TouchTarget.PANEL_RIGHT
        if (distance(x, y, w / 2f, 0f) <= panelEdgeTouchPx) return TouchTarget.PANEL_TOP
        if (distance(x, y, w / 2f, h) <= panelEdgeTouchPx) return TouchTarget.PANEL_BOTTOM
        return null
    }

    private fun approx(a: Float, b: Float): Boolean = abs(a - b) < 1f

    private fun isOnBorderBand(x: Float, y: Float): Boolean {
        val outer = rectLeft <= x && x <= rectRight && rectTop <= y && y <= rectBottom
        if (!outer) return false

        val innerLeft = rectLeft + borderTouchPx
        val innerTop = rectTop + borderTouchPx
        val innerRight = rectRight - borderTouchPx
        val innerBottom = rectBottom - borderTouchPx
        if (innerRight <= innerLeft || innerBottom <= innerTop) {
            return true
        }

        val insideInterior = innerLeft < x && x < innerRight && innerTop < y && y < innerBottom
        if (insideInterior) return false

        val distTop = abs(y - rectTop)
        val distBottom = abs(y - rectBottom)
        val distLeft = abs(x - rectLeft)
        val distRight = abs(x - rectRight)
        val minDist = min(min(distTop, distBottom), min(distLeft, distRight))
        return minDist <= borderTouchPx
    }

    private fun distance(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val dx = x1 - x2
        val dy = y1 - y2
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }
}
