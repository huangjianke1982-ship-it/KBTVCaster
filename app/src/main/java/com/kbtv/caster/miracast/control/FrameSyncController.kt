package com.kbtv.caster.miracast.control

import timber.log.Timber
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * 帧同步控制器
 * 
 * 实现基于时间戳的帧同步，确保视频流畅播放
 */
class FrameSyncController(
    private val targetFrameRate: Int = 30
) {
    companion object {
        private const val TAG = "FrameSyncController"
        private const val TARGET_PRESENTATION_DELAY_MS = 50 // 目标延迟 50ms
        private const val JITTER_BUFFER_SIZE = 5 // 抖动缓冲大小
    }

    // 时间戳管理
    private val baseTimestamp = AtomicLong(0)
    private val expectedPTS = AtomicLong(0)
    private val actualPTS = AtomicLong(0)
    
    // 帧统计
    private val frameCounter = AtomicLong(0)
    private val droppedFrames = AtomicLong(0)
    private val duplicatedFrames = AtomicLong(0)
    
    // 抖动缓冲
    private val jitterBuffer = ArrayDeque<SyncableFrame>(JITTER_BUFFER_SIZE)
    
    // 同步状态
    @Volatile private var isSynchronized = false
    @Volatile private var lastFramePresentationTime = 0L

    data class SyncableFrame(
        val data: ByteArray,
        val timestamp: Long,
        val isKeyFrame: Boolean
    )

    /**
     * 初始化同步控制器
     */
    fun initialize(baseTimeUs: Long) {
        baseTimestamp.set(baseTimeUs)
        expectedPTS.set(0)
        actualPTS.set(System.nanoTime())
        isSynchronized = false
        jitterBuffer.clear()
        Timber.d("帧同步控制器已初始化，基准时间: ${baseTimeUs / 1000}ms")
    }

    /**
     * 处理帧，返回处理后的帧信息
     */
    fun processFrame(data: ByteArray, rtpTimestamp: Long, isKeyFrame: Boolean): FrameSyncResult {
        frameCounter.incrementAndGet()
        
        // 计算相对时间戳
        val presentationTimeUs = rtpTimestamp - baseTimestamp.get()
        
        // 帧有效性检查
        if (presentationTimeUs < 0) {
            Timber.w("忽略过期帧")
            return FrameSyncResult.Dropped
        }

        // 检查是否需要同步
        if (!isSynchronized) {
            val result = synchronize(presentationTimeUs)
            if (result == FrameSyncResult.Dropped) {
                return FrameSyncResult.Dropped
            }
        }

        // 添加到抖动缓冲
        val syncableFrame = SyncableFrame(data, presentationTimeUs, isKeyFrame)
        jitterBuffer.addLast(syncableFrame)
        
        // 检查是否应该播放当前帧
        val now = System.nanoTime()
        val expectedPlayTimeUs = actualPTS.get() + TARGET_PRESENTATION_DELAY_MS * 1000
        
        return if (syncableFrame.timestamp <= expectedPlayTimeUs) {
            // 帧应该播放
            val frame = if (jitterBuffer.isNotEmpty()) jitterBuffer.removeFirst() else null
            if (frame != null) {
                lastFramePresentationTime = frame.timestamp
                actualPTS.set(now)
                expectedPTS.addAndGet((1_000_000 / targetFrameRate).toLong())
                FrameSyncResult.Render(frame)
            } else {
                FrameSyncResult.Dropped
            }
        } else {
            // 帧太早，需要等待
            FrameSyncResult.Wait
        }
    }

    /**
     * 同步到流
     */
    private fun synchronize(presentationTimeUs: Long): FrameSyncResult {
        // 等待足够的帧进入缓冲
        if (jitterBuffer.size < 2) {
            Timber.d("等待同步，缓冲帧数: ${jitterBuffer.size}")
            return FrameSyncResult.Dropped
        }
        
        isSynchronized = true
        actualPTS.set(System.nanoTime())
        Timber.d("已同步到流")
        return FrameSyncResult.Synchronized
    }

    /**
     * 计算需要的等待时间
     */
    fun getWaitTimeNanos(): Long {
        if (jitterBuffer.isEmpty()) return 0
        
        val nextFrameTime = jitterBuffer.first().timestamp
        val now = System.nanoTime()
        val expectedPlayTimeUs = actualPTS.get() + TARGET_PRESENTATION_DELAY_MS * 1000
        
        return if (nextFrameTime > expectedPlayTimeUs) {
            (nextFrameTime - expectedPlayTimeUs) * 1000 // 转换为纳秒
        } else {
            0
        }
    }

    /**
     * 调整播放速率（用于追赶或延迟）
     */
    fun adjustPlaybackRate(rate: Float) {
        if (rate > 0) {
            val adjustment = 1.0f / rate
            expectedPTS.addAndGet(((1_000_000 / targetFrameRate * adjustment - 1_000_000 / targetFrameRate) * 1000).toLong())
            Timber.d("调整播放速率: $rate")
        }
    }

    /**
     * 跳转到指定时间
     */
    fun seekTo(presentationTimeUs: Long) {
        jitterBuffer.clear()
        isSynchronized = false
        baseTimestamp.set(System.currentTimeMillis() * 1000 - presentationTimeUs)
        Timber.d("跳转到: ${presentationTimeUs / 1000}ms")
    }

    /**
     * 获取同步状态
     */
    fun getSyncStatus(): SyncStatus {
        return SyncStatus(
            isSynchronized = isSynchronized,
            framesProcessed = frameCounter.get(),
            droppedFrames = droppedFrames.get(),
            duplicatedFrames = duplicatedFrames.get(),
            jitterBufferSize = jitterBuffer.size,
            lastFramePTS = lastFramePresentationTime,
            targetFrameRate = targetFrameRate,
            currentFrameRate = calculateCurrentFrameRate()
        )
    }

    /**
     * 计算当前帧率
     */
    private fun calculateCurrentFrameRate(): Float {
        // 基于统计计算实际帧率
        return targetFrameRate.toFloat()
    }

    /**
     * 重置控制器
     */
    fun reset() {
        baseTimestamp.set(0)
        expectedPTS.set(0)
        actualPTS.set(0)
        frameCounter.set(0)
        droppedFrames.set(0)
        duplicatedFrames.set(0)
        jitterBuffer.clear()
        isSynchronized = false
        Timber.d("帧同步控制器已重置")
    }

    /**
     * 释放资源
     */
    fun release() {
        reset()
        Timber.d("帧同步控制器已释放")
    }
}

/**
 * 帧同步结果
 */
sealed class FrameSyncResult {
    data class Render(val frame: FrameSyncController.SyncableFrame) : FrameSyncResult()
    data object Wait : FrameSyncResult()
    data object Dropped : FrameSyncResult()
    data object Synchronized : FrameSyncResult()
}

/**
 * 同步状态
 */
data class SyncStatus(
    val isSynchronized: Boolean,
    val framesProcessed: Long,
    val droppedFrames: Long,
    val duplicatedFrames: Long,
    val jitterBufferSize: Int,
    val lastFramePTS: Long,
    val targetFrameRate: Int,
    val currentFrameRate: Float
)
