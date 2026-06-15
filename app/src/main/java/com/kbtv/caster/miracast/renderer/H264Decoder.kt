package com.kbtv.caster.miracast.renderer

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.view.Surface
import android.view.SurfaceHolder
import timber.log.Timber
import java.nio.ByteBuffer

/**
 * H.264 视频解码器
 * 
 * 使用 MediaCodec 硬件解码器解码 H.264 视频流
 */
class H264Decoder {

    companion object {
        private const val TAG = "H264Decoder"
    }

    private var decoder: MediaCodec? = null
    private var surface: Surface? = null
    private var bufferInfo = MediaCodec.BufferInfo()
    private var isConfigured = false
    private var isRunning = false

    private var width: Int = 1920
    private var height: Int = 1080
    private var bitRate: Int = 20_000_000
    private var frameRate: Int = 30

    /**
     * 配置解码器
     */
    fun configure(
        surfaceHolder: SurfaceHolder,
        targetWidth: Int = 1920,
        targetHeight: Int = 1080,
        targetBitRate: Int = 20_000_000,
        targetFrameRate: Int = 30
    ) {
        width = targetWidth
        height = targetHeight
        bitRate = targetBitRate
        frameRate = targetFrameRate

        val format = MediaFormat.createVideoFormat(
            MediaFormat.MIMETYPE_VIDEO_AVC,
            width,
            height
        ).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, 
                MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
            setInteger(MediaFormat.KEY_FRAME_RATE, frameRate)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }

        try {
            decoder = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
            decoder?.configure(format, surfaceHolder.surface, null, 0)
            decoder?.start()
            surface = surfaceHolder.surface
            isConfigured = true
            Timber.d("H.264 解码器已配置: ${width}x${height} @ ${frameRate}fps")
        } catch (e: Exception) {
            Timber.e(e, "H.264 解码器配置失败")
            release()
        }
    }

    /**
     * 配置解码器（使用外部 Surface）
     */
    fun configureWithSurface(externalSurface: Surface, targetWidth: Int = 1920, targetHeight: Int = 1080) {
        width = targetWidth
        height = targetHeight

        val format = MediaFormat.createVideoFormat(
            MediaFormat.MIMETYPE_VIDEO_AVC,
            width,
            height
        ).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
            setInteger(MediaFormat.KEY_FRAME_RATE, frameRate)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }

        try {
            decoder = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
            decoder?.configure(format, externalSurface, null, 0)
            decoder?.start()
            surface = externalSurface
            isConfigured = true
            Timber.d("H.264 解码器已配置（外部 Surface）: ${width}x${height}")
        } catch (e: Exception) {
            Timber.e(e, "H.264 解码器配置失败")
            release()
        }
    }

    /**
     * 开始解码
     */
    fun start() {
        if (!isConfigured) {
            Timber.w("解码器未配置，无法启动")
            return
        }
        isRunning = true
        Timber.d("H.264 解码器已启动")
    }

    /**
     * 停止解码
     */
    fun stop() {
        isRunning = false
        Timber.d("H.264 解码器已停止")
    }

    /**
     * 解码单个 NALU 单元
     */
    fun decodeFrame(naluData: ByteArray, presentationTimeUs: Long = 0) {
        if (!isConfigured || !isRunning) return

        try {
            // 获取输入缓冲区
            val inputBufferIndex = decoder?.dequeueInputBuffer(10000) ?: -1
            if (inputBufferIndex >= 0) {
                val inputBuffer = decoder?.getInputBuffer(inputBufferIndex)
                inputBuffer?.clear()
                inputBuffer?.put(naluData)
                decoder?.queueInputBuffer(inputBufferIndex, 0, naluData.size, presentationTimeUs, 0)
            }

            // 获取输出并渲染到 Surface
            drainOutput()
        } catch (e: Exception) {
            Timber.e(e, "解码帧失败")
        }
    }

    /**
     * 解码并提取帧数据（用于录制或处理）
     */
    fun decodeFrameAndGetData(naluData: ByteArray): ByteArray? {
        if (!isConfigured || !isRunning) return null

        decodeFrame(naluData)
        
        return null // 如果需要录制，可以在这里返回解码后的数据
    }

    /**
     * 处理解码器输出
     */
    private fun drainOutput() {
        try {
            var outputBufferIndex = decoder?.dequeueOutputBuffer(bufferInfo, 10000) ?: -1
            
            while (outputBufferIndex >= 0 && isRunning) {
                // 输出已渲染到 Surface
                decoder?.releaseOutputBuffer(outputBufferIndex, true)
                
                // 检查是否还有更多输出
                outputBufferIndex = decoder?.dequeueOutputBuffer(bufferInfo, 0) ?: -1
            }

            // 处理格式变化
            if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                val newFormat = decoder?.outputFormat
                Timber.d("输出格式变化: $newFormat")
            }
        } catch (e: Exception) {
            Timber.e(e, "处理输出失败")
        }
    }

    /**
     * 释放资源
     */
    fun release() {
        isRunning = false
        isConfigured = false

        try {
            decoder?.stop()
            decoder?.release()
        } catch (e: Exception) {
            Timber.e(e, "释放解码器失败")
        }

        decoder = null
        surface = null
        Timber.d("H.264 解码器已释放")
    }

    /**
     * 检查解码器是否已配置
     */
    fun isConfigured(): Boolean = isConfigured

    /**
     * 检查解码器是否正在运行
     */
    fun isRunning(): Boolean = isRunning

    /**
     * 获取当前帧率
     */
    fun getFrameRate(): Int = frameRate

    /**
     * 获取分辨率
     */
    fun getResolution(): Pair<Int, Int> = width to height
}
