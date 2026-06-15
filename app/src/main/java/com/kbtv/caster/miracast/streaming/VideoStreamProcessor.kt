package com.kbtv.caster.miracast.streaming

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.view.Surface
import timber.log.Timber
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 视频流处理器
 * 
 * 处理 RTP 包重组、H.264 NALU 解析和帧边界检测
 */
class VideoStreamProcessor(
    private val outputSurface: Surface
) {
    companion object {
        private const val TAG = "VideoStreamProcessor"
        private const val MIME_TYPE = "video/avc"
        private const val RTP_HEADER_SIZE = 12
        private const val FU_A_HEADER_SIZE = 2
    }

    private var decoder: MediaCodec? = null
    private val isRunning = AtomicBoolean(false)
    
    // 分片重组缓冲区
    private val reassemblyBuffer = ByteArray(512 * 1024) // 512KB max NALU
    private var reassemblyOffset = 0
    private var currentFragmentation = false
    private var lastNaluHeader: Byte = 0
    
    // 统计
    private var totalPacketsReceived = 0
    private var totalFramesDecoded = 0

    /**
     * 初始化处理器
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
            }

            decoder = MediaCodec.createDecoderByType(MIME_TYPE)
            decoder?.configure(format, outputSurface, null, 0)
            decoder?.start()
            isRunning.set(true)
            
            Timber.i("视频流处理器已初始化: ${width}x${height}")
            return true
        } catch (e: Exception) {
            Timber.e(e, "视频流处理器初始化失败")
            try { decoder?.release() } catch (e2: Exception) { Timber.w(e2, "Failed to release decoder") }
            decoder = null
            return false
        }
    }

    /**
     * 处理接收到的 RTP 包
     */
    @Synchronized
    fun processRTPPacket(data: ByteArray, timestamp: Long): Boolean {
        if (!isRunning.get()) return false
        
        totalPacketsReceived++
        
        // 解析 RTP 头部
        if (data.size < RTP_HEADER_SIZE) {
            Timber.w("RTP 包太小: ${data.size}")
            return false
        }
        
        val buffer = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN)
        val byte0 = buffer.get().toInt() and 0xFF
        val version = (byte0 shr 6) and 0x03
        val hasExtension = (byte0 shr 4) and 0x01 == 1
        val csrcCount = byte0 and 0x0F

        val byte1 = buffer.get().toInt() and 0xFF
        val marker = (byte1 shr 7) and 0x01 == 1
        val payloadType = byte1 and 0x7F

        val sequenceNumber = buffer.short.toInt() and 0xFFFF
        val rtpTimestamp = buffer.int.toLong() and 0xFFFFFFFFL
        val ssrc = buffer.int

        // 跳过 CSRC
        if (csrcCount > 0) {
            buffer.position(buffer.position() + csrcCount * 4)
        }

        // 跳过扩展头部
        if (hasExtension) {
            val extLength = buffer.short.toInt()
            buffer.position(buffer.position() + extLength * 4)
        }

        // 提取负载
        val payload = ByteArray(buffer.remaining())
        buffer.get(payload)
        
        // 根据负载类型处理
        return when (payloadType) {
            96 -> processH264Payload(payload, rtpTimestamp)  // H.264
            97 -> processH265Payload(payload, rtpTimestamp)  // H.265 (如果支持)
            else -> {
                Timber.w("未知负载类型: $payloadType")
                false
            }
        }
    }

    /**
     * 处理 H.264 负载
     */
    private fun processH264Payload(payload: ByteArray, timestamp: Long): Boolean {
        val nalUnitType = payload[0].toInt() and 0x1F
        
        return when {
            // 单一 NALU
            nalUnitType in 1..23 -> {
                decodeFrame(payload, timestamp, nalUnitType == 5)
            }
            // FU-A 分片
            nalUnitType == 28 -> {
                processFU_AFragmentation(payload, timestamp)
            }
            // STAP-A (聚合包)
            nalUnitType == 24 -> {
                processSTAP_A(payload, timestamp)
            }
            else -> {
                Timber.d("忽略 NALU 类型: $nalUnitType")
                false
            }
        }
    }

    /**
     * 处理 FU-A 分片
     */
    @Synchronized
    private fun processFU_AFragmentation(payload: ByteArray, timestamp: Long): Boolean {
        if (payload.size < FU_A_HEADER_SIZE + 1) {
            Timber.w("FU-A 包太小")
            return false
        }
        
        val fuIndicator = payload[0]
        val fuHeader = payload[1]
        
        val startBit = (fuHeader.toInt() shr 7) and 0x01
        val endBit = (fuHeader.toInt() shr 6) and 0x01
        val nalType = fuHeader.toInt() and 0x1F
        
        // 重建 NALU 头部
        val reconstructedHeader = ((fuIndicator.toInt() and 0xE0) or nalType).toByte()
        
        val fragmentData = payload.copyOfRange(FU_A_HEADER_SIZE, payload.size)
        
        return when {
            // 分片开始
            startBit == 1 -> {
                reassemblyBuffer[0] = reconstructedHeader
                System.arraycopy(fragmentData, 0, reassemblyBuffer, 1, fragmentData.size)
                reassemblyOffset = 1 + fragmentData.size
                currentFragmentation = true
                lastNaluHeader = reconstructedHeader
                true
            }
            // 分片中间或结束
            currentFragmentation -> {
                System.arraycopy(fragmentData, 0, reassemblyBuffer, reassemblyOffset, fragmentData.size)
                reassemblyOffset += fragmentData.size
                
                if (endBit == 1) {
                    currentFragmentation = false
                    val completeNalu = reassemblyBuffer.copyOfRange(0, reassemblyOffset)
                    reassemblyOffset = 0
                    decodeFrame(completeNalu, timestamp, nalType == 5)
                } else {
                    true
                }
            }
            // 不应该出现没有开始的分片
            else -> false
        }
    }

    /**
     * 处理 STAP-A 聚合包
     */
    private fun processSTAP_A(payload: ByteArray, timestamp: Long): Boolean {
        var offset = 1 // 跳过 NALU 类型
        
        while (offset < payload.size) {
            if (offset + 2 > payload.size) break
            
            val nalSize = ((payload[offset].toInt() and 0xFF) shl 8) or (payload[offset + 1].toInt() and 0xFF)
            offset += 2
            
            if (offset + nalSize > payload.size) break
            
            val nalData = payload.copyOfRange(offset, offset + nalSize)
            decodeFrame(nalData, timestamp, false)
            offset += nalSize
        }
        
        return true
    }

    /**
     * 解码帧
     */
    private fun decodeFrame(data: ByteArray, timestamp: Long, isKeyFrame: Boolean): Boolean {
        val codec = decoder ?: return false
        
        try {
            val inputIndex = codec.dequeueInputBuffer(10000)
            if (inputIndex >= 0) {
                val inputBuffer = codec.getInputBuffer(inputIndex)
                inputBuffer?.clear()
                inputBuffer?.put(data)
                
                val flags = if (isKeyFrame) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0
                codec.queueInputBuffer(inputIndex, 0, data.size, timestamp, flags)
                totalFramesDecoded++
                return true
            }
        } catch (e: Exception) {
            Timber.e(e, "解码帧失败")
        }
        return false
    }

    /**
     * 处理 H.265 负载（预留）
     */
    private fun processH265Payload(payload: ByteArray, timestamp: Long): Boolean {
        // H.265 处理逻辑
        Timber.d("H.265 负载: ${payload.size} bytes")
        return false
    }

    /**
     * 强制刷新（关键帧请求）
     */
    fun requestKeyFrame() {
        // 发送关键帧请求
        Timber.d("请求关键帧")
    }

    /**
     * 获取处理统计
     */
    fun getStatistics(): StreamProcessorStatistics {
        return StreamProcessorStatistics(
            packetsReceived = totalPacketsReceived,
            framesDecoded = totalFramesDecoded,
            isRunning = isRunning.get()
        )
    }

    /**
     * 释放资源
     */
    @Synchronized
    fun release() {
        isRunning.set(false)
        reassemblyOffset = 0
        currentFragmentation = false
        
        try {
            decoder?.stop()
            decoder?.release()
        } catch (e: Exception) {
            Timber.e(e, "释放解码器失败")
        }
        decoder = null
        
        Timber.d("视频流处理器已释放")
    }
}

/**
 * 处理统计
 */
data class StreamProcessorStatistics(
    val packetsReceived: Int,
    val framesDecoded: Int,
    val isRunning: Boolean
)
