package com.caster.tv.dlna

import org.fourthline.cling.support.model.TransportState
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for DLNA transport state handling
 * Tests the TransportState enum from Cling UPnP stack
 */
class TransportStateTest {

    @Test
    fun `TransportState STOPPED should be valid`() {
        assertEquals(TransportState.STOPPED, TransportState.valueOf("STOPPED"))
    }

    @Test
    fun `TransportState PLAYING should be valid`() {
        assertEquals(TransportState.PLAYING, TransportState.valueOf("PLAYING"))
    }

    @Test
    fun `TransportState PAUSED_PLAYBACK should be valid`() {
        assertEquals(TransportState.PAUSED_PLAYBACK, TransportState.valueOf("PAUSED_PLAYBACK"))
    }

    @Test
    fun `TransportState NO_MEDIA_PRESENT should be valid`() {
        assertEquals(TransportState.NO_MEDIA_PRESENT, TransportState.valueOf("NO_MEDIA_PRESENT"))
    }

    @Test
    fun `TransportState TRANSITIONING should be valid`() {
        assertEquals(TransportState.TRANSITIONING, TransportState.valueOf("TRANSITIONING"))
    }

    @Test
    fun `TransportState values should not be null`() {
        assertNotNull(TransportState.STOPPED)
        assertNotNull(TransportState.PLAYING)
        assertNotNull(TransportState.PAUSED_PLAYBACK)
        assertNotNull(TransportState.NO_MEDIA_PRESENT)
        assertNotNull(TransportState.TRANSITIONING)
    }

    @Test
    fun `TransportState STOPPED should not equal PLAYING`() {
        assertNotEquals(TransportState.STOPPED, TransportState.PLAYING)
    }

    @Test
    fun `TransportState PAUSED_PLAYBACK should not equal PLAYING`() {
        assertNotEquals(TransportState.PAUSED_PLAYBACK, TransportState.PLAYING)
    }
}
