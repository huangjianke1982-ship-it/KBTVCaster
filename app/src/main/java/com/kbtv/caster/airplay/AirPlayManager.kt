package com.kbtv.caster.airplay

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import timber.log.Timber
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import javax.jmdns.JmDNS
import javax.jmdns.ServiceInfo
import com.kbtv.caster.airplay.server.AirPlayRTSPServer

/**
 * AirPlay 主管理器
 *
 * 实现 AirPlay 视频投屏接收端：
 * - mDNS 注册 _airplay._tcp 服务（端口 7000）让 iOS 设备发现
 * - Netty HTTP 服务器处理 /play、/stop、/rate、/scrub、/photo 等请求
 * - 通过回调将播放指令交给 CastCoordinatorService 的 ExoPlayer 执行
 *
 * 使用方式：
 *   val manager = AirPlayManager.initialize(context)
 *   manager.onPlayUrl = { url -> ... }
 *   manager.start()
 */
class AirPlayManager private constructor(
    private val context: Context
) {
    companion object {
        @Volatile
        private var instance: AirPlayManager? = null

        private const val AIRPLAY_PORT = 7000
        private const val SERVICE_TYPE = "_airplay._tcp.local."
        private const val SERVICE_NAME = "凯机投屏"

        fun initialize(context: Context): AirPlayManager = instance ?: synchronized(this) {
            instance ?: AirPlayManager(context.applicationContext).also { instance = it }
        }

        fun getInstance(): AirPlayManager = instance
            ?: throw IllegalStateException("AirPlayManager not initialized")
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var rtspServer: AirPlayRTSPServer? = null
    private var jmDNS: JmDNS? = null
    @Volatile
    private var isRunning = false

    // Callbacks — set by CastCoordinatorService. All invoked on Netty IO thread,
    // receivers MUST hop to UI thread before touching ExoPlayer.
    var onPlayUrl: ((String) -> Unit)? = null
    var onStopPlayback: (() -> Unit)? = null
    var onPlayPause: ((Boolean) -> Unit)? = null  // true = playing, false = paused
    var onSeek: ((Long) -> Unit)? = null          // position in ms

    /**
     * 启动 AirPlay 服务：先拉起 HTTP 服务器，再注册 mDNS。
     * 非挂起函数 — Netty bind() 本身是同步的，调用方无需协程上下文。
     * @return true 表示服务器已成功监听端口
     */
    fun start(): Boolean {
        if (isRunning) return true

        return try {
            rtspServer = AirPlayRTSPServer(AIRPLAY_PORT, this)
            val started = rtspServer?.start() ?: false
            if (started) {
                registerMDNSService()
                isRunning = true
                Timber.i("AirPlay 服务已启动，端口: $AIRPLAY_PORT")
                true
            } else {
                Timber.e("AirPlay RTSP 服务器启动失败")
                false
            }
        } catch (e: Exception) {
            Timber.e(e, "AirPlay 启动失败")
            false
        }
    }

    /**
     * 注册 mDNS 服务，让 iOS 设备可以发现 "凯机投屏"。
     * TXT 记录模拟 AppleTV3,2，features 字段声明支持视频/图片，不支持镜像。
     */
    private fun registerMDNSService() {
        try {
            val localAddress = getLocalIpAddress()
            jmDNS = JmDNS.create(localAddress)

            val props = hashMapOf(
                "deviceid" to getMacAddress(),
                // features: MFP/SAPV1 bits — 视频 + 图片 + 音频，无屏幕镜像
                "features" to "0x5A7FFFF7,0x0",
                "model" to "AppleTV3,2",
                "srcvers" to "220.68",
                "vv" to "2",
                "pi" to "b08f5a79-db29-4384-b456-a4784d9e6055",
                "pk" to "99FD4299889422515FBD27949E4E1E21B2AF50A454499E3D4BE75A4E0F55FE63",
                "flags" to "0x04"
            )

            val serviceInfo = ServiceInfo.create(
                SERVICE_TYPE,
                SERVICE_NAME,
                AIRPLAY_PORT,
                0, 0,
                props as Map<String, Any>?
            )
            jmDNS?.registerService(serviceInfo)
            Timber.d("AirPlay mDNS 服务已注册: $SERVICE_NAME ($SERVICE_TYPE:$AIRPLAY_PORT) IP: $localAddress")
        } catch (e: Exception) {
            Timber.e(e, "AirPlay mDNS 注册失败")
        }
    }

    /**
     * 枚举网络接口，返回首个非环回 IPv4 地址。
     * 复用 WfdRTSPServer.getLocalIpAddress() 的实现。
     */
    private fun getLocalIpAddress(): InetAddress {
        return try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val intf = interfaces.nextElement()
                if (intf.isLoopback || !intf.isUp) continue
                val addrs = intf.inetAddresses
                while (addrs.hasMoreElements()) {
                    val addr = addrs.nextElement()
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        return addr
                    }
                }
            }
            InetAddress.getLocalHost()
        } catch (e: Exception) {
            InetAddress.getByName("127.0.0.1")
        }
    }

    /**
     * 获取本机 MAC 地址，用于 AirPlay deviceid 字段。
     * 失败时返回占位 MAC，iOS 客户端只校验格式不校验真实性。
     */
    private fun getMacAddress(): String {
        return try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val intf = interfaces.nextElement()
                val mac = intf.hardwareAddress ?: continue
                if (mac.isEmpty()) continue
                return mac.joinToString(":") { String.format("%02X", it) }
            }
            "00:00:00:00:00:00"
        } catch (e: Exception) {
            "00:00:00:00:00:00"
        }
    }

    /**
     * 停止 AirPlay 服务：先注销 mDNS（让客户端立即失联），再关闭 HTTP 服务器。
     * 可重入 — 重复调用安全。
     */
    fun stop() {
        if (!isRunning) return
        try {
            jmDNS?.unregisterAllServices()
            jmDNS?.close()
            jmDNS = null
        } catch (e: Exception) {
            Timber.w(e, "停止 mDNS 失败")
        }
        try {
            rtspServer?.stop()
        } catch (e: Exception) {
            Timber.w(e, "停止 RTSP 服务器失败")
        }
        rtspServer = null
        isRunning = false
        Timber.i("AirPlay 服务已停止")
    }

    /**
     * 释放单例：停止服务 + 取消协程作用域 + 清空 instance。
     * 调用后若想再用必须重新 initialize()。
     */
    fun release() {
        stop()
        scope.cancel()
        instance = null
    }
}
