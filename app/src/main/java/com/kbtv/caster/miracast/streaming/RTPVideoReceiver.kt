package com.kbtv.caster.miracast.streaming

import timber.log.Timber
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.SocketException
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean

/**
 * RTP 视频流接收器
 * 
 * 接收并解析 RTP 包，提取 H.264 NALU 单元
 */
class RTPVideoReceiver(
    private val localPort: Int = 50000
) {
    companion object {
        private const val TAG = "RTPVideoReceiver"
        private const val BUFFER_SIZE = 65535
        private const val RTP_HEADER_SIZE = 12
    }

    private var socket: DatagramSocket? = null
    private val isRunning = AtomicBoolean(false)
    
    private var onFrameReceived: ((ByteArray, Long) -> Unit)? = null
    private var onError: ((Exception) -> Unit)? = null

    /**
     * 开始接收 RTP 流
     */
    fun start(
        onFrame: (ByteArray, Long) -> Unit,
        onErrorCallback: ((Exception) -> Unit)? = null
    ): Boolean {
        onFrameReceived = onFrame
        onError = onErrorCallback

        try {
            socket = DatagramSocket(localPort).apply {
                soTimeout = 5000 // 5秒超时
            }
            
            if (isRunning.compareAndSet(false, true)) {
                Thread {
                    android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_VIDEO)
                    receiveLoop()
                }.apply { isDaemon = true }.start()
                
                Timber.i("RTP 视频接收器已启动，端口: $localPort")
                return true
            }
            return false
        } catch (e: SocketException) {
            Timber.e(e, "RTP 接收器启动失败，端口: $localPort")
            onError?.invoke(e)
            return false
        }
    }

    /**
     * 停止接收
     */
    fun stop() {
        isRunning.set(false)
        socket?.close()
        socket = null
        Timber.d("RTP 视频接收器已停止")
    }

    /**
     * 接收循环
     */
    private fun receiveLoop() {
        val buffer = ByteArray(BUFFER_SIZE)
        
        while (isRunning.get() && socket != null) {
            try {
                val packet = DatagramPacket(buffer, buffer.size)
                socket?.receive(packet)
                
                if (packet.length > 0) {
                    val data = packet.data.copyOf(packet.length)
                    processRTPPacket(data)
                }
            } catch (e: java.net.SocketTimeoutException) {
                // 超时，继续等待
                continue
            } catch (e: Exception) {
                if (isRunning.get()) {
                    Timber.e(e, "RTP 接收错误")
                    onError?.invoke(e)
                }
                break
            }
        }
    }

    /**
     * 处理 RTP 包
     */
    private fun processRTPPacket(data: ByteArray) {
        if (data.size < RTP_HEADER_SIZE) {
            Timber.w("RTP 包太小: ${data.size} bytes")
            return
        }

        val buffer = ByteBuffer.wrap(data)
        
        // 解析 RTP 头部
        val version = (buffer.get().toInt() shr 6) and 0x03
        if (version != 2) {
            Timber.w("无效的 RTP 版本: $version")
            return
        }

        val hasPadding = (buffer.get().toInt() shr 5) and 0x01
        val extension = (buffer.get().toInt() shr 4) and 0x0F
        val csrcCount = buffer.get().toInt() and 0x0F

        val marker = buffer.get().toInt() and 0x80
        val payloadType = buffer.get().toInt() and 0x7F
        
        val sequenceNumber = buffer.short.toInt() and 0xFFFF
        val timestamp = buffer.int.toLong() and 0xFFFFFFFFL
        val ssrc = buffer.int.toLong() and 0xFFFFFFFFL

        // 跳过 CSRC 列表
        buffer.position(buffer.position() + csrcCount * 4)

        // 处理扩展头部（如果有）
        var extensionLength = 0
        if (extension > 0) {
            val profile = buffer.short.toInt() and 0xFFFF
            extensionLength = buffer.short.toInt() and 0xFFFF
            buffer.position(buffer.position() + extensionLength * 4 + 2)
        }

        // 提取 RTP 负载（H.264 NALU）
        val payload = ByteArray(buffer.remaining())
        buffer.get(payload)

        // 处理 FU-A 分片（如果需要）
        val nalUnit = if (payloadType == 28 && payload.size > 2) {
            // FU-A 分片
            processFUAFragmentation(payload)
        } else {
            // 完整的 NALU
            payload
        }

        // 回调
        onFrameReceived?.invoke(nalUnit, timestamp)
    }

    /**
     * 处理 FU-A 分片
     * 
     * 当 NALU 大小超过 MTU 时，会被分成多个 FU-A 包
     */
    private fun processFUAFragmentation(payload: ByteArray): ByteArray {
        if (payload.size < 2) return payload

        val fuIndicator = payload[0]
        val fuHeader = payload[1]

        // 检查是否为分片的开始或结束
        val startBit = (fuHeader.toInt() shr 7) and 0x01
        val endBit = (fuHeader.toInt() shr 6) and 0x01
        val nalType = fuIndicator.toInt() and 0x1F

        // 重建 NALU 头部
        val reconstructedHeader = byteArrayOf(
            (nalType or 0).toByte(),
            *payload.copyOfRange(2, payload.size)
        )

        return reconstructedHeader
    }

    /**
     * 获取本地端口
     */
    fun getLocalPort(): Int = localPort

    /**
     * 检查是否正在运行
     */
    fun isRunning(): Boolean = isRunning.get()
}
