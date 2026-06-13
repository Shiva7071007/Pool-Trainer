package com.example.shichak

import kotlin.math.abs
import kotlin.math.sqrt

data class PointF(val x: Float, val y: Float) {
    fun distanceTo(other: PointF): Float {
        val dx = x - other.x
        val dy = y - other.y
        return sqrt(dx * dx + dy * dy)
    }

    operator fun plus(other: PointF) = PointF(x + other.x, y + other.y)

    operator fun minus(other: PointF) = PointF(x - other.x, y - other.y)

    operator fun times(scalar: Float) = PointF(x * scalar, y * scalar)
}

data class RectBounds(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width get() = right - left
    val height get() = bottom - top

    fun centerX() = (left + right) / 2f
    fun centerY() = (top + bottom) / 2f

    fun contains(x: Float, y: Float): Boolean =
        x >= left && x <= right && y >= top && y <= bottom

    fun distanceTo(point: PointF): Float {
        val closestX = point.x.coerceIn(left, right)
        val closestY = point.y.coerceIn(top, bottom)
        val dx = point.x - closestX
        val dy = point.y - closestY
        return sqrt(dx * dx + dy * dy)
    }
}

enum class Side { TOP, BOTTOM, LEFT, RIGHT }

fun sideNormal(side: Side): PointF = when (side) {
    Side.TOP -> PointF(0f, 1f)
    Side.BOTTOM -> PointF(0f, -1f)
    Side.LEFT -> PointF(1f, 0f)
    Side.RIGHT -> PointF(-1f, 0f)
}

fun normalize(v: PointF): PointF {
    val len = sqrt(v.x * v.x + v.y * v.y)
    if (len < 1e-6f) return PointF(0f, 0f)
    return PointF(v.x / len, v.y / len)
}

fun reflect(direction: PointF, side: Side): PointF {
    val normal = sideNormal(side)
    val dot = direction.x * normal.x + direction.y * normal.y
    return PointF(
        direction.x - 2f * dot * normal.x,
        direction.y - 2f * dot * normal.y
    )
}

fun rayRectIntersection(
    origin: PointF,
    direction: PointF,
    rect: RectBounds,
    minT: Float = 1e-4f
): Pair<PointF, Side>? {
    var closestT = Float.MAX_VALUE
    var closestPoint: PointF? = null
    var closestSide: Side? = null

    if (abs(direction.y) > 1e-6f) {
        val tTop = (rect.top - origin.y) / direction.y
        if (tTop > minT) {
            val x = origin.x + tTop * direction.x
            if (x >= rect.left && x <= rect.right && tTop < closestT) {
                closestT = tTop
                closestPoint = PointF(x, rect.top)
                closestSide = Side.TOP
            }
        }

        val tBottom = (rect.bottom - origin.y) / direction.y
        if (tBottom > minT) {
            val x = origin.x + tBottom * direction.x
            if (x >= rect.left && x <= rect.right && tBottom < closestT) {
                closestT = tBottom
                closestPoint = PointF(x, rect.bottom)
                closestSide = Side.BOTTOM
            }
        }
    }

    if (abs(direction.x) > 1e-6f) {
        val tLeft = (rect.left - origin.x) / direction.x
        if (tLeft > minT) {
            val y = origin.y + tLeft * direction.y
            if (y >= rect.top && y <= rect.bottom && tLeft < closestT) {
                closestT = tLeft
                closestPoint = PointF(rect.left, y)
                closestSide = Side.LEFT
            }
        }

        val tRight = (rect.right - origin.x) / direction.x
        if (tRight > minT) {
            val y = origin.y + tRight * direction.y
            if (y >= rect.top && y <= rect.bottom && tRight < closestT) {
                closestT = tRight
                closestPoint = PointF(rect.right, y)
                closestSide = Side.RIGHT
            }
        }
    }

    return if (closestPoint != null && closestSide != null) {
        Pair(closestPoint, closestSide)
    } else {
        null
    }
}

fun rayPointAt(origin: PointF, direction: PointF, t: Float): PointF {
    return PointF(origin.x + direction.x * t, origin.y + direction.y * t)
}

fun angleBetweenDegrees(a: PointF, b: PointF): Float {
    val lenA = sqrt(a.x * a.x + a.y * a.y)
    val lenB = sqrt(b.x * b.x + b.y * b.y)
    if (lenA < 1e-6f || lenB < 1e-6f) return 0f
    val dot = (a.x * b.x + a.y * b.y) / (lenA * lenB)
    return Math.toDegrees(kotlin.math.acos(dot.coerceIn(-1f, 1f).toDouble())).toFloat()
}

fun incidenceAngleDegrees(direction: PointF, side: Side): Float {
    val normal = sideNormal(side)
    val len = sqrt(direction.x * direction.x + direction.y * direction.y)
    if (len < 1e-6f) return 0f
    val dot = abs(direction.x * normal.x + direction.y * normal.y) / len
    return Math.toDegrees(kotlin.math.acos(dot.coerceIn(0f, 1f).toDouble())).toFloat()
}

fun bankAngleDegrees(direction: PointF): Float {
    val len = sqrt(direction.x * direction.x + direction.y * direction.y)
    if (len < 1e-6f) return 0f
    val angleRad = kotlin.math.atan2(direction.y.toDouble(), direction.x.toDouble())
    var degrees = Math.toDegrees(angleRad).toFloat()
    if (degrees < 0f) degrees += 360f
    return degrees
}
