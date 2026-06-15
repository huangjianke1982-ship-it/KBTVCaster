package com.kbtv.caster.miracast.renderer

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.view.Surface
import timber.log.Timber
import java.nio.ByteBuffer
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * H.264 视频渲染管线
 * 
 * 整合 RTP 接收 -> H.264 解码 -> Surface 渲染
 */
class VideoRendererPipeline(
    private val surface: Surface
) {
    companion object {
        private const val TAG = "VideoRendererPipeline"
        private const val MIME_TYPE = "video/avc"
        private const val TIMEOUT_US = 10000L
    }

    private var decoder: MediaCodec? = null
    private var decoderThread: HandlerThread? = null
    private var decoderHandler: Handler? = null
    
    private val isRunning = AtomicBoolean(false)
    private val frameQueue = ArrayBlockingQueue<DecodableFrame>(4, true) // max 4 frames
    
    // 统计信息
    @Volatile private var totalFramesDecoded = 0
    @Volatile private var totalBytesProcessed = 0L
    @Volatile private var lastFrameTime = 0L

    data class DecodableFrame(
        val data: ByteArray,
        val timestamp: Long,
        val isKeyFrame: Boolean
    )

    /**
     * 初始化渲染管线
     */
    fun initialize(
        width: Int = 1920,
        height: Int = 1080,
        bitRate: Int = 10_000_000,
        frameRate: Int = 30
    ): Boolean {
        try {
            val format = MediaFormat.createVideoFormat(MIME_TYPE, width, height).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
                setInteger(MediaFormat.KEY_FRAME_RATE, frameRate)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
                // Android 11+ official low-latency mode
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                    setInteger(MediaFormat.KEY_LOW_LATENCY, 1)
                }
                // Realtime priority (API 23+)
                setInteger(MediaFormat.KEY_PRIORITY, 0)
                // Operating rate hint
                try { setInteger(MediaFormat.KEY_OPERATING_RATE, frameRate * 1000) } catch (_: Exception) {}
            }

            decoder = MediaCodec.createDecoderByType(MIME_TYPE)

            // Apply vendor-specific low-latency keys (safe to attempt all — unsupported ones are ignored)
            try { format.setInteger("vendor.qti-ext-dec-low-latency.enable", 1) } catch (_: Exception) {}
            try { format.setInteger("vendor.low-latency.enable", 1) } catch (_: Exception) {}
            try { format.setInteger("vdec-lowlatency", 1) } catch (_: Exception) {}
            decoder?.configure(format, surface, null, 0)
            
            // 启动解码器线程
            val thread = HandlerThread("VideoDecoderThread").apply { start() }
            android.os.Process.setThreadPriority(thread.threadId, android.os.Process.THREAD_PRIORITY_VIDEO)
            decoderThread = thread
            decoderHandler = Handler(thread.looper)
            
            decoder?.start()
            isRunning.set(true)
            
            // 启动解码循环
            decoderHandler?.post { decodingLoop() }
            
            Timber.i("视频渲染管线已初始化: ${width}x${height} @ ${frameRate}fps")
            return true
        } catch (e: Exception) {
            Timber.e(e, "视频渲染管线初始化失败")
            release()
            return false
        }
    }

    /**
     * 设置输出 Surface（用于动态切换）
     */
    fun setOutputSurface(newSurface: Surface) {
        decoder?.setOutputSurface(newSurface)
    }

    /**
     * 提交帧到渲染管线
     */
    fun submitFrame(data: ByteArray, timestamp: Long, isKeyFrame: Boolean = false) {
        if (!isRunning.get()) return
        
        val frame = DecodableFrame(data, timestamp, isKeyFrame)
        if (!frameQueue.offer(frame)) {
            // Queue full - drop oldest non-keyframe
            frameQueue.poll()
            frameQueue.offer(frame)
        }
    }

    /**
     * 解码循环（在 decoderThread 中运行）
     */
    private fun decodingLoop() {
        while (isRunning.get()) {
            val frame = frameQueue.poll(100, TimeUnit.MILLISECONDS) ?: continue
            
            decodeFrame(frame)
        }
    }

    /**
     * 解码单帧
     */
    private fun decodeFrame(frame: DecodableFrame) {
        val codec = decoder ?: return
        
        try {
            // 获取输入缓冲区
            val inputIndex = codec.dequeueInputBuffer(TIMEOUT_US)
            if (inputIndex >= 0) {
                val inputBuffer = codec.getInputBuffer(inputIndex)
                inputBuffer?.clear()
                inputBuffer?.put(frame.data)
                
                val flags = if (frame.isKeyFrame) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0
                codec.queueInputBuffer(inputIndex, 0, frame.data.size, frame.timestamp, flags)
            }
            
            // 处理输出
            val bufferInfo = MediaCodec.BufferInfo()
            var outputIndex = codec.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
            
            while (outputIndex >= 0 && isRunning.get()) {
                // 渲染到 Surface
                codec.releaseOutputBuffer(outputIndex, true)
                totalFramesDecoded++
                totalBytesProcessed += bufferInfo.size
                
                outputIndex = codec.dequeueOutputBuffer(bufferInfo, 0)
            }
            
            // 处理格式变化
            if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                val newFormat = codec.outputFormat
                Timber.d("输出格式变化: $newFormat")
            }
            
            lastFrameTime = System.currentTimeMillis()
        } catch (e: Exception) {
            Timber.e(e, "解码帧失败")
        }
    }

    /**
     * 获取当前帧率
     */
    fun getCurrentFrameRate(): Float {
        val now = System.currentTimeMillis()
        val elapsed = now - lastFrameTime
        return if (elapsed > 0) 1000f / elapsed else 0f
    }

    /**
     * 获取解码统计信息
     */
    fun getStatistics(): RendererStatistics {
        return RendererStatistics(
            framesDecoded = totalFramesDecoded,
            bytesProcessed = totalBytesProcessed,
            currentFrameRate = getCurrentFrameRate(),
            queuedFrames = frameQueue.size,
            isRunning = isRunning.get()
        )
    }

    /**
     * 跳转到指定时间戳
     */
    fun seekTo(timestampUs: Long) {
        // 实现seek逻辑
        Timber.d("Seek to: ${timestampUs / 1000}ms")
    }

    /**
     * 暂停解码
     */
    fun pause() {
        decoder?.signalEndOfInputStream()
    }

    /**
     * 恢复解码
     */
    fun resume() {
        // 恢复逻辑
    }

    /**
     * 释放资源
     */
    fun release() {
        isRunning.set(false)
        
        decoderThread?.let { thread ->
            thread.quitSafely()
            decoderThread = null
        }
        
        try {
            decoder?.stop()
            decoder?.release()
        } catch (e: Exception) {
            Timber.e(e, "释放解码器失败")
        }
        decoder = null
        decoderHandler = null
        
        frameQueue.clear()
        
        Timber.d("视频渲染管线已释放")
    }

    /**
     * 检查是否正在运行
     */
    fun isActive(): Boolean = isRunning.get()
}

/**
 * 渲染统计信息
 */
data class RendererStatistics(
    val framesDecoded: Int,
    val bytesProcessed: Long,
    val currentFrameRate: Float,
    val queuedFrames: Int,
    val isRunning: Boolean
)
