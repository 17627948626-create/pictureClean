package com.yihua.app.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SwipePresentationContractTest {
    @Test
    fun `horizontal edge drag uses resistance instead of staying fixed`() {
        val resisted = edgeResistedDrag(totalDrag = -120f, resistance = 0.28f)

        assertTrue(resisted < 0f)
        assertTrue(resisted > -120f)
        assertEquals(-33.6f, resisted, 0.01f)
    }

    @Test
    fun `accepted upward fly out starts from the drag preview scale`() {
        val transform = flyOutTransform(
            motion = CardMotion.FlyToTop,
            progress = 0f,
            width = 400f,
            height = 800f,
            startX = 0f,
            startY = -140f,
            startScale = 0.90f
        )

        assertEquals(-140f, transform.translationY, 0.01f)
        assertEquals(0.90f, transform.scale, 0.01f)
    }

    @Test
    fun `coil image request disables original size loading`() {
        val options = photoImageRequestOptions()

        assertTrue(options.allowHardware)
        assertTrue(options.precisionInexact)
        assertEquals("pictureclean-photo", options.memoryCacheKeyPrefix)
    }
}
