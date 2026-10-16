package com.dailygallery.app.ui.viewer

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import org.junit.Assert.assertEquals
import org.junit.Test

class ZoomTest {
    private val box = IntSize(1000, 2000)

    @Test
    fun pinchingStaysBetweenActualSizeAndFiveTimes() {
        assertEquals(1f, Zoom().transformed(0.5f, Offset.Zero, box).scale)
        assertEquals(3f, Zoom().transformed(3f, Offset.Zero, box).scale)
        assertEquals(5f, Zoom(3f).transformed(4f, Offset.Zero, box).scale)
    }

    @Test
    fun atActualSizeThePictureCannotBeDragged() {
        assertEquals(Offset.Zero, Zoom().transformed(1f, Offset(300f, -200f), box).offset)
    }

    @Test
    fun zoomedInItMovesOnlyUntilItsEdgeReachesTheBoxs() {
        // At 2x the picture is 2000 x 4000, so it can move 500 sideways and 1000 up or down.
        assertEquals(Offset(500f, -1000f), Zoom(2f).transformed(1f, Offset(900f, -1500f), box).offset)
        assertEquals(Offset(-120f, 80f), Zoom(2f).transformed(1f, Offset(-120f, 80f), box).offset)
    }

    @Test
    fun zoomingOutBringsAMovedPictureBackInside() {
        val moved = Zoom(4f, Offset(1500f, 3000f))
        assertEquals(Offset(500f, 1000f), moved.transformed(0.5f, Offset.Zero, box).offset)
    }

    @Test
    fun aDoubleTapZoomsInOnTheTappedPointAndASecondOneZoomsOut() {
        val zoomed = Zoom().toggled(tap = Offset(700f, 1000f), size = box)
        assertEquals(Zoom.DOUBLE_TAP, zoomed.scale)
        // (centre 500 - tap 700) x 1.5: scaled about the centre, the point at x = 700 stays at 700.
        assertEquals(Offset(-300f, 0f), zoomed.offset)
        assertEquals(Zoom(), zoomed.toggled(Offset(10f, 10f), box))
    }
}
