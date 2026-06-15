package com.kbtv.caster.miracast

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.*
import org.mockito.junit.MockitoJUnitRunner

/**
 * H264Decoder 单元测试
 */
@RunWith(MockitoJUnitRunner::class)
class H264DecoderTest {

    private lateinit var decoder: com.kbtv.caster.miracast.renderer.H264Decoder

    @Before
    fun setup() {
        decoder = com.kbtv.caster.miracast.renderer.H264Decoder()
    }

    @Test
    fun `decoder should not be configured initially`() {
        assertFalse(decoder.isConfigured())
        assertFalse(decoder.isRunning())
    }

    @Test
    fun `decoder default resolution should be 1080p`() {
        val (width, height) = decoder.getResolution()
        assertEquals(1920, width)
        assertEquals(1080, height)
    }

    @Test
    fun `decoder default frame rate should be 30`() {
        assertEquals(30, decoder.getFrameRate())
    }

    @Test
    fun `start without configure should be no-op`() {
        decoder.start()
        assertFalse(decoder.isRunning())
    }

    @Test
    fun `release should reset all state`() {
        decoder.release()
        assertFalse(decoder.isConfigured())
        assertFalse(decoder.isRunning())
    }
}

/**
 * MiracastDevice 单元测试
 */
@RunWith(MockitoJUnitRunner::class)
class MiracastDeviceTest {

    @Test
    fun `device should be valid target when available`() {
        val mockDevice = mock(android.net.wifi.p2p.WifiP2pDevice::class.java)
        val device = com.kbtv.caster.miracast.discovery.MiracastDevice(
            device = mockDevice,
            name = "Test TV",
            address = "00:11:22:33:44:55",
            status = com.kbtv.caster.miracast.discovery.DeviceStatus.AVAILABLE,
            isWfdCapable = true
        )
        
        assertTrue(device.isValidTarget())
        assertEquals("可用", device.getStatusDescription())
    }

    @Test
    fun `device should have correct connection type for TV`() {
        val mockDevice = mock(android.net.wifi.p2p.WifiP2pDevice::class.java)
        val device = com.kbtv.caster.miracast.discovery.MiracastDevice(
            device = mockDevice,
            name = "Living Room TV",
            address = "00:11:22:33:44:55",
            status = com.kbtv.caster.miracast.discovery.DeviceStatus.AVAILABLE,
            isWfdCapable = true
        )
        
        assertEquals(com.kbtv.caster.miracast.discovery.ConnectionType.TV, device.connectionType)
    }

    @Test
    fun `devices with same address should be equal`() {
        val mockDevice1 = mock(android.net.wifi.p2p.WifiP2pDevice::class.java)
        val mockDevice2 = mock(android.net.wifi.p2p.WifiP2pDevice::class.java)

        val device1 = com.kbtv.caster.miracast.discovery.MiracastDevice(
            device = mockDevice1,
            name = "Device 1",
            address = "00:11:22:33:44:55",
            status = com.kbtv.caster.miracast.discovery.DeviceStatus.AVAILABLE,
            isWfdCapable = true
        )
        val device2 = com.kbtv.caster.miracast.discovery.MiracastDevice(
            device = mockDevice2,
            name = "Device 2",
            address = "00:11:22:33:44:55",
            status = com.kbtv.caster.miracast.discovery.DeviceStatus.CONNECTED,
            isWfdCapable = true
        )
        
        assertEquals(device1, device2)
        assertEquals(device1.hashCode(), device2.hashCode())
    }
}

/**
 * FrameSyncController 单元测试
 */
@RunWith(MockitoJUnitRunner::class)
class FrameSyncControllerTest {

    private lateinit var syncController: com.kbtv.caster.miracast.control.FrameSyncController

    @Before
    fun setup() {
        syncController = com.kbtv.caster.miracast.control.FrameSyncController(30)
    }

    @Test
    fun `sync controller should not be synchronized initially`() {
        val status = syncController.getSyncStatus()
        
        assertFalse(status.isSynchronized)
        assertEquals(0, status.framesProcessed)
    }

    @Test
    fun `sync controller should initialize correctly`() {
        syncController.initialize(1000000L)
        
        val status = syncController.getSyncStatus()
        assertFalse(status.isSynchronized) // 初始未同步
    }

    @Test
    fun `sync controller should handle frame processing`() {
        syncController.initialize(1000000L)
        
        val result = syncController.processFrame(
            byteArrayOf(0x00, 0x00, 0x00, 0x01),
            1001000L,
            true
        )
        
        // 第一次处理应该返回同步或等待
        assertNotNull(result)
    }

    @Test
    fun `sync controller should reset correctly`() {
        syncController.initialize(1000000L)
        syncController.processFrame(byteArrayOf(1, 2, 3), 1001000L, true)
        
        syncController.reset()
        
        val status = syncController.getSyncStatus()
        assertFalse(status.isSynchronized)
        assertEquals(0, status.framesProcessed)
    }

    @Test
    fun `sync controller should calculate wait time`() {
        syncController.initialize(1000000L)
        
        val waitTime = syncController.getWaitTimeNanos()
        
        assertTrue(waitTime >= 0)
    }

    @Test
    fun `sync controller should seek to position`() {
        syncController.initialize(1000000L)
        syncController.seekTo(5000000L)
        
        // 验证跳转后状态
        val status = syncController.getSyncStatus()
        assertFalse(status.isSynchronized)
    }

    @Test
    fun `sync controller should release correctly`() {
        syncController.initialize(1000000L)
        
        syncController.release()
        
        val status = syncController.getSyncStatus()
        assertFalse(status.isSynchronized)
    }
}
