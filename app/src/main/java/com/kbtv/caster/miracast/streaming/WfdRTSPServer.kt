package com.kbtv.caster.miracast.streaming

import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.Channel
import io.netty.channel.ChannelInitializer
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.handler.codec.http.HttpRequestDecoder
import io.netty.handler.codec.http.HttpResponseEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.StringWriter
import java.net.InetAddress
import javax.jmdns.JmDNS
import javax.jmdns.ServiceInfo

/**
 * Wi-Fi Display RTSP 服务器
 *
 * 实现 WFD 协议中的 RTSP 控制通道，处理 SETUP、PLAY、TEARDOWN 等命令
 * 同时注册 mDNS 服务让手机可以发现此设备
 */
class WfdRTSPServer(
    private val port: Int = 7236
) {
    companion object {
        private const val TAG = "WfdRTSPServer"
        private const val SERVICE_TYPE = "_wifi-display._tcp.local."
        private const val SERVICE_NAME = "凯机投屏设备"
    }

    private var serverChannel: Channel? = null
    private var bossGroup: NioEventLoopGroup? = null
    private var workerGroup: NioEventLoopGroup? = null

    private var jmDNS: JmDNS? = null
    private var serviceInfo: ServiceInfo? = null

    private var onSetupCallback: ((Int, Int) -> Unit)? = null
    private var onPlayCallback: (() -> Unit)? = null
    private var onTeardownCallback: (() -> Unit)? = null

    /**
     * 回调: RTSP SETUP 命令
     */
    fun onRTSPSetup(clientPort: Int, serverPort: Int) {
        Timber.d("RTSP SETUP: client_port=$clientPort, server_port=$serverPort")
        onSetupCallback?.invoke(clientPort, serverPort)
    }

    /**
     * 回调: RTSP PLAY 命令
     */
    fun onRTSPPlay() {
        Timber.d("RTSP PLAY")
        onPlayCallback?.invoke()
    }

    /**
     * 回调: RTSP TEARDOWN 命令
     */
    fun onRTSPTeardown() {
        Timber.d("RTSP TEARDOWN")
        onTeardownCallback?.invoke()
    }

    /**
     * 启动 RTSP 服务器
     */
    suspend fun start(): Boolean = withContext(Dispatchers.IO) {
        try {
            bossGroup = NioEventLoopGroup(1)
            workerGroup = NioEventLoopGroup()

            val bootstrap = ServerBootstrap()
            bootstrap.group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel::class.java)
                .childHandler(object : ChannelInitializer<io.netty.channel.socket.SocketChannel>() {
                    override fun initChannel(ch: io.netty.channel.socket.SocketChannel) {
                        ch.pipeline()
                            .addLast("decoder", HttpRequestDecoder())
                            .addLast("encoder", HttpResponseEncoder())
                            .addLast("handler", WfdRTSPHandler(this@WfdRTSPServer))
                    }
                })

            serverChannel = bootstrap.bind(port).sync().channel()
            Timber.i("WFD RTSP 服务器已启动，端口: $port")

            // 注册 mDNS 服务
            registerMDNSService()

            true
        } catch (e: Exception) {
            Timber.e(e, "WFD RTSP 服务器启动失败")
            false
        }
    }

    /**
     * 停止 RTSP 服务器
     */
    suspend fun stop() = withContext(Dispatchers.IO) {
        // 取消 mDNS 注册
        unregisterMDNSService()

        serverChannel?.close()
        bossGroup?.shutdownGracefully()
        workerGroup?.shutdownGracefully()
        serverChannel = null
        bossGroup = null
        workerGroup = null
        Timber.d("WFD RTSP 服务器已停止")
    }

    /**
     * 注册 mDNS 服务（让手机能够发现此设备）
     */
    private fun registerMDNSService() {
        try {
            // 获取本机 IP 地址 - 使用更可靠的方法
            val localAddress = getLocalIpAddress()

            // 创建 JmDNS 实例 - 不绑定特定地址，让它自动选择
            jmDNS = JmDNS.create(localAddress)

            // 构建 WFD 设备能力 TXT 记录
            val txtRecordMap = buildWfdTxtRecord()
            val txtRecordBytes = buildTxtRecordBytes(txtRecordMap)

            // 创建服务信息
            serviceInfo = ServiceInfo.create(
                SERVICE_TYPE,
                SERVICE_NAME,
                port,
                0,
                0,
                txtRecordBytes
            )

            // 注册服务
            jmDNS?.registerService(serviceInfo)

            Timber.i("mDNS 服务已注册: $SERVICE_NAME ($SERVICE_TYPE:$port) IP: $localAddress")
        } catch (e: Exception) {
            Timber.e(e, "mDNS 服务注册失败")
        }
    }

    /**
     * 将 TXT 记录 Map 转换为字节数组
     */
    private fun buildTxtRecordBytes(txtMap: Map<String, String>): ByteArray {
        val bytes = mutableListOf<Byte>()
        txtMap.forEach { (key, value) ->
            val entry = "$key=$value"
            bytes.add(entry.length.toByte())
            bytes.addAll(entry.toByteArray().toList())
        }
        return bytes.toByteArray()
    }

    /**
     * 获取本机 IP 地址
     */
    private fun getLocalIpAddress(): java.net.InetAddress {
        try {
            val interfaces = java.net.NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                val addresses = networkInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    // 跳过环回地址和链路本地地址
                    if (!address.isLoopbackAddress && address is java.net.Inet4Address) {
                        Timber.d("Found IP: ${address.hostAddress} on ${networkInterface.name}")
                        return address
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "获取本机 IP 失败")
        }
        // 默认返回任意地址
        return java.net.InetAddress.getByName("0.0.0.0")
    }

    /**
     * 取消 mDNS 服务注册
     */
    private fun unregisterMDNSService() {
        try {
            serviceInfo?.let {
                jmDNS?.unregisterService(it)
            }
            jmDNS?.close()
            jmDNS = null
            serviceInfo = null
            Timber.d("mDNS 服务已取消注册")
        } catch (e: Exception) {
            Timber.e(e, "取消 mDNS 注册失败")
        }
    }

    /**
     * 构建 WFD 设备能力描述
     * 告诉手机发送端此设备支持的能力
     * 根据 Wi-Fi Display 技术规范 v1.0.0 section 5.1.2
     */
    private fun buildWfdTxtRecord(): Map<String, String> {
        return mapOf(
            // RTSP 控制端口
            "wfd_rtsp_port" to port.toString(),
            // 设备类型: 1 = Sink (接收端)
            "wfd_device_type" to "1",
            // 设备名称
            "wfd_device_name" to SERVICE_NAME,
            // 会话管理: 0 = 正常
            "wfd_session_management" to "0",
            // 视频格式 - 支持 H.264 High Profile Level 4.2 1080p@30fps
            // 标准格式: profile-level-id (hex) + resolutions
            "wfd_video_formats" to "00 00 00 01 00000000 00000000 00 000000 00000000 none none",
            // 音频格式 - 支持 AAC
            "wfd_audio_formats" to "AAC 00000000 00",
            // 显示 EDID - 标准格式
            "wfd_display_edid" to "00 00000000 00000000 00 00",
            // 客户端 RTP 端口 - 标准格式
            "wfd_client_rtp_ports" to "RTP/AVP/UDP;unicast $port 0",
            // 连接器类型: 00 = HDMI
            "wfd_connector_type" to "00",
            // 内容保护: none = 不支持 HDCP
            "wfd_content_protection" to "none",
            // 待机/恢复能力
            "wfd_standby_resume_capability" to "none",
            // 3D 视频支持
            "wfd_3d_video_formats" to "none",
            // 屏幕覆盖支持
            "wfd_screen_coverage" to "0 100",
            // 延迟
            "wfd_display_latency" to "0 0",
            // 耦合sink支持
            "wfd_coupled_sink" to "none"
        )
    }

    /**
     * 获取服务名称
     */
    fun getServiceName(): String = SERVICE_NAME

    /**
     * 设置 SETUP 回调
     */
    fun setOnSetupCallback(callback: (clientRtpPort: Int, clientRtcpPort: Int) -> Unit) {
        onSetupCallback = callback
    }

    /**
     * 设置 PLAY 回调
     */
    fun setOnPlayCallback(callback: () -> Unit) {
        onPlayCallback = callback
    }

    /**
     * 设置 TEARDOWN 回调
     */
    fun setOnTeardownCallback(callback: () -> Unit) {
        onTeardownCallback = callback
    }
}
