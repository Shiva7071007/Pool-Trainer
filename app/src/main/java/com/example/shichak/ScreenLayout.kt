package com.example.shichak

import android.content.Context
import android.graphics.PointF
import android.graphics.RectF
import android.view.WindowManager

/**
 * Distances in px from the current screen's left, top, right, and bottom edges.
 * The same numeric margins are reused after rotation (screen edges move on the device,
 * but the offset from each on-screen edge stays constant).
 */
data class ScreenMargins(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
)

object ScreenLayout {

    fun screenSize(context: Context): Pair<Int, Int> {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val size = android.graphics.Point()
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealSize(size)
        return size.x to size.y
    }

    fun rectToMargins(
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        screenW: Int,
        screenH: Int
    ): ScreenMargins {
        return ScreenMargins(
            left = left.coerceAtLeast(0f),
            top = top.coerceAtLeast(0f),
            right = (screenW - right).coerceAtLeast(0f),
            bottom = (screenH - bottom).coerceAtLeast(0f)
        )
    }

    fun boundsToMargins(bounds: OverlayPanelBounds, screenW: Int, screenH: Int): ScreenMargins {
        return rectToMargins(
            bounds.x,
            bounds.y,
            bounds.x + bounds.width,
            bounds.y + bounds.height,
            screenW,
            screenH
        )
    }

    fun marginsToRect(margins: ScreenMargins, screenW: Int, screenH: Int): RectF {
        val width = (screenW - margins.left - margins.right).coerceAtLeast(0f)
        val height = (screenH - margins.top - margins.bottom).coerceAtLeast(0f)
        return RectF(
            margins.left,
            margins.top,
            margins.left + width,
            margins.top + height
        )
    }

    fun marginsToBounds(margins: ScreenMargins, screenW: Int, screenH: Int): OverlayPanelBounds {
        val rect = marginsToRect(margins, screenW, screenH)
        return OverlayPanelBounds(rect.left, rect.top, rect.width(), rect.height())
    }

    fun pointToMargins(x: Float, y: Float, screenW: Int, screenH: Int): ScreenMargins {
        return ScreenMargins(
            left = x.coerceAtLeast(0f),
            top = y.coerceAtLeast(0f),
            right = (screenW - x).coerceAtLeast(0f),
            bottom = (screenH - y).coerceAtLeast(0f)
        )
    }

    fun marginsToPoint(margins: ScreenMargins): PointF {
        return PointF(margins.left, margins.top)
    }
}
