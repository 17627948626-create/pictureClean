package com.swiply.app.ui.screens

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
    fun`coil image request disables original size loading`() {
        val options = photoImageRequestOptions()

        assertTrue(options.allowHardware)
        assertTrue(options.precisionInexact)
        assertEquals("pictureclean-photo", options.memoryCacheKeyPrefix)
    }
}
