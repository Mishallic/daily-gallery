package com.dailygallery.app.ui.viewer

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize

/**
 * How far the viewer is zoomed in and moved, in a box of [size]: between actual size and 5x,
 * never moved so far that the picture's edge leaves the box's.
 */
data class Zoom(val scale: Float = 1f, val offset: Offset = Offset.Zero) {

    /** After a pinch by [zoomChange] and a drag by [pan]. */
    fun transformed(zoomChange: Float, pan: Offset, size: IntSize): Zoom {
        val newScale = (scale * zoomChange).coerceIn(MIN, MAX)
        return Zoom(newScale, clamp(offset + pan, newScale, size))
    }

    /** A double tap: back to actual size, or in to [DOUBLE_TAP] with the [tap]ped point staying under the finger. */
    fun toggled(tap: Offset, size: IntSize): Zoom {
        if (scale > MIN) return Zoom()
        val centre = Offset(size.width / 2f, size.height / 2f)
        return Zoom(DOUBLE_TAP, clamp((centre - tap) * (DOUBLE_TAP - 1f), DOUBLE_TAP, size))
    }

    private fun clamp(offset: Offset, scale: Float, size: IntSize): Offset {
        if (scale <= MIN) return Offset.Zero // nowhere to move (and no -0.0 from clamping to ±0)
        val maxX = size.width * (scale - 1f) / 2f
        val maxY = size.height * (scale - 1f) / 2f
        return Offset(offset.x.coerceIn(-maxX, maxX), offset.y.coerceIn(-maxY, maxY))
    }

    companion object {
        const val MIN = 1f
        const val MAX = 5f
        const val DOUBLE_TAP = 2.5f
    }
}
