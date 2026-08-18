package com.spatialapps.wallstickies.content

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HandPinchStateTest {
    @Test
    fun `pinch starts only inside the start threshold`() {
        assertTrue(nextPinchState(wasPinched = false, fingertipDistanceMetres = 0.021f))
        assertFalse(nextPinchState(wasPinched = false, fingertipDistanceMetres = 0.023f))
    }

    @Test
    fun `pinch remains active until the wider release threshold`() {
        assertTrue(nextPinchState(wasPinched = true, fingertipDistanceMetres = 0.030f))
        assertFalse(nextPinchState(wasPinched = true, fingertipDistanceMetres = 0.036f))
    }
}
