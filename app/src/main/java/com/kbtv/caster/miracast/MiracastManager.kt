package com.kbtv.caster.miracast

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.p2p.WifiP2pManager
import android.view.Display
import android.view.SurfaceView
import com.kbtv.caster.R
import com.kbtv.caster.miracast.discovery.ConnectionState
import com.kbtv.caster.miracast.discovery.MiracastDevice
import com.kbtv.caster.miracast.discovery.MiracastDiscoveryManager
import com.kbtv.caster.miracast.presentation.MiracastDisplayManager
import com.kbtv.caster.miracast.presentation.MiracastPresentation
import com.kbtv.caster.miracast.renderer.VideoRendererPipeline
import com.kbtv.caster.miracast.streaming.RTPVideoReceiver
import com.kbtv.caster.miracast.streaming.WfdRTSPServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import timber.log.Timber

/**
 * Miracast 主管理器
 *
 * 电视端接收器：默认启动 RTSP 服务器，让手机可以发现并连接
 */
class MiracastManager private constructor(
    private val context: Context
) {
    companion object {
        private const val TAG = "MiracastManager"
        private const val RTSP_PORT = 7236
        private const val RTP_PORT = 50000

        @Volatile
        private var instance: MiracastManager? = null

        fun initialize(context: Context): MiracastManager {
            return instance ?: synchronized(this) {
                instance ?: MiracastManager(context.applicationContext).also {
                    instance = it
                    // 自动启动服务
                    it.startAsReceiver()
                }
            }
        }

        fun getInstance(): MiracastManager {
            return instance ?: throw IllegalStateException(
                "MiracastManager not initialized. Call initialize() first."
            )
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val discoveryManager: MiracastDiscoveryManager by lazy {
        MiracastDiscoveryManager.getInstance()
    }

    private val _displayManager: MiracastDisplayManager by lazy {
        MiracastDisplayManager(context)
    }

    private var wfdRTSPServer: WfdRTSPServer? = null
    private var rtpVideoReceiver: RTPVideoReceiver? = null
    private var videoRendererPipeline: VideoRendererPipeline? = null
    private var nsdRegistrationListener: NsdManager.RegistrationListener? = null

    // WifiP2pManager 用于 WiFi P2P 服务发现（iQOO 手机使用的标准方式）
    private val wifiP2pManager: WifiP2pManager? by lazy {
        context.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
    }
    private var p2pServiceRegistrationListener: WifiP2pManager.ActionListener? = null

    private var currentDevice: MiracastDevice? = null
    private var currentPresentation: MiracastPresentation? = null
    private var isReceiverStarted = false

    // 状态流 - 电视端主要关注连接状态
    val connectionState: StateFlow<ConnectionState> = discoveryManager.connectionState
    val isReceiverRunning: Boolean get() = isReceiverStarted

    private fun setupCallbacks() {
        // RTSP 服务器回调
        wfdRTSPServer?.setOnSetupCallback { clientPort, serverPort ->
            Timber.d("SETUP: client_port=$clientPort, server_port=$serverPort")
            // 显示画面（先创建 Presentation，Surface 才可用于解码）
            startDisplayIfNeeded()
            startVideoReceiver(serverPort)
        }

        wfdRTSPServer?.setOnPlayCallback {
            Timber.d("PLAY: 开始播放")
        }

        wfdRTSPServer?.setOnTeardownCallback {
            Timber.d("TEARDOWN: 停止播放")
            stopVideoReceiver()
        }
    }

    /**
     * 作为接收端启动（默认自动启动）
     */
    private fun startAsReceiver() {
        if (isReceiverStarted) return

        scope.launch {
            try {
                // 初始化 WiFi P2P 通道（让手机能够发现我们）
                discoveryManager.initializeChannel()

                wfdRTSPServer = WfdRTSPServer(RTSP_PORT)
                val started = wfdRTSPServer?.start() ?: false

                if (started) {
                    isReceiverStarted = true
                    // 注册 RTSP 回调（此时 wfdRTSPServer 已创建并启动）
                    setupCallbacks()
                    // 重新启用 NSD 服务注册
                    registerNsdService()
                    // 注册 WifiP2pManager 服务（iQOO 手机使用的标准方式）
                    registerWifiP2pService()
                    Timber.d("凯机投屏设备服务已启动，端口: $RTSP_PORT")
                } else {
                    Timber.e("WFD 服务启动失败")
                }
            } catch (e: Exception) {
                Timber.e(e, "启动镜像投屏服务失败")
            }
        }
    }

    /**
     * 注册 NSD 服务（WiFi Display 标准方式）
     */
    private fun registerNsdService() {
        try {
            val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager

            val serviceInfo = NsdServiceInfo().apply {
                serviceName = "凯机投屏设备"  // 服务名（手机搜索时显示）
                serviceType = "_wifi-display._tcp."
                setPort(RTSP_PORT)
            }

            nsdRegistrationListener = object : NsdManager.RegistrationListener {
                override fun onServiceRegistered(info: NsdServiceInfo) {
                    Timber.i("NSD 服务已注册: ${info.serviceName} (${info.serviceType}:${info.port})")
                }

                override fun onRegistrationFailed(info: NsdServiceInfo, errorCode: Int) {
                    Timber.e("NSD 注册失败: ${info.serviceName}, error=$errorCode")
                }

                override fun onServiceUnregistered(info: NsdServiceInfo) {
                    Timber.d("NSD 服务已取消注册")
                }

                override fun onUnregistrationFailed(info: NsdServiceInfo, errorCode: Int) {
                    Timber.e("NSD 取消注册失败: ${info.serviceName}, error=$errorCode")
                }
            }

            nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, nsdRegistrationListener)
        } catch (e: Exception) {
            Timber.e(e, "NSD 服务注册失败")
        }
    }

    /**
     * 取消注册 NSD 服务
     */
    private fun unregisterNsdService() {
        try {
            nsdRegistrationListener?.let {
                val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
                nsdManager.unregisterService(it)
                nsdRegistrationListener = null
            }
        } catch (e: Exception) {
            Timber.e(e, "取消 NSD 服务注册失败")
        }
    }

    /**
     * 注册 WifiP2pManager 本地服务（iQOO/手机使用的标准方式）
     * 这是 Android WiFi Display 的标准发现机制
     */
    private fun registerWifiP2pService() {
        try {
            val manager = wifiP2pManager ?: return
            val channel = discoveryManager.getChannel()

            if (channel == null) {
                Timber.w("WifiP2pManager Channel 未初始化")
                return
            }

            // 设置服务发现监听器（用于响应手机的搜索请求）
            val serviceResponseListener = object : WifiP2pManager.DnsSdServiceResponseListener {
                override fun onDnsSdServiceAvailable(
                    instanceName: String?,
                    registrationType: String?,
                    srcDevice: android.net.wifi.p2p.WifiP2pDevice?
                ) {
                    Timber.d("Service discovered by peer: $instanceName")
                }
            }

            val txtRecordListener = object : WifiP2pManager.DnsSdTxtRecordListener {
                override fun onDnsSdTxtRecordAvailable(
                    fullDomainName: String?,
                    txtRecord: Map<String, String>?,
                    srcDevice: android.net.wifi.p2p.WifiP2pDevice?
                ) {
                    Timber.d("TXT record available: $fullDomainName, data: $txtRecord")
                }
            }

            // 启用服务发现响应
            manager.setDnsSdResponseListeners(channel, serviceResponseListener, txtRecordListener)

            // 使用反射创建 WifiP2pServiceInfo（Android API 没有公开这个类）
            // 通过 NSD 注册已经在工作，这里只需设置监听器
            Timber.d("WifiP2P 服务发现监听器已设置")
        } catch (e: Exception) {
            Timber.e(e, "WifiP2P 服务注册异常")
        }
    }

    /**
     * 取消注册 WifiP2pManager 服务
     */
    private fun unregisterWifiP2pService() {
        try {
            discoveryManager.getChannel()?.let { channel ->
                wifiP2pManager?.clearLocalServices(channel, object : WifiP2pManager.ActionListener {
                    override fun onSuccess() {
                        Timber.d("WifiP2P 本地服务已清除")
                    }

                    override fun onFailure(reason: Int) {
                        Timber.e("清除 WifiP2P 服务失败: $reason")
                    }
                })
            }
            p2pServiceRegistrationListener = null
        } catch (e: Exception) {
            Timber.e(e, "取消 WifiP2P 服务注册失败")
        }
    }

    /**
     * 启动视频接收
     */
    private fun startVideoReceiver(port: Int) {
        // 尝试初始化渲染管线（依赖 Presentation 的 Surface）
        ensureRendererPipeline()
        rtpVideoReceiver = RTPVideoReceiver(port)
        rtpVideoReceiver?.start(
            onFrame = { frameData, timestamp ->
                // 懒初始化：Surface 可能在首帧到达后才就绪
                if (videoRendererPipeline == null) ensureRendererPipeline()
                videoRendererPipeline?.submitFrame(frameData, timestamp)
            },
            onErrorCallback = { error ->
                Timber.e(error, "视频接收错误")
            }
        )
        Timber.d("视频接收已启动: $port")
    }

    /**
     * 确保视频渲染管线已初始化
     * 从当前 Presentation 的 SurfaceView 获取 Surface 用于解码渲染
     */
    private fun ensureRendererPipeline() {
        if (videoRendererPipeline != null) return
        val presentation = currentPresentation ?: return
        try {
            val surfaceView = presentation.findViewById<SurfaceView>(R.id.miracast_surface_view)
            val surface = surfaceView?.holder?.surface
            if (surface == null || !surface.isValid) return
            val pipeline = VideoRendererPipeline(surface)
            if (pipeline.initialize()) {
                videoRendererPipeline = pipeline
                Timber.d("视频渲染管线已初始化")
            }
        } catch (e: Exception) {
            Timber.e(e, "初始化视频渲染管线失败")
        }
    }

    /**
     * 停止视频接收
     */
    private fun stopVideoReceiver() {
        rtpVideoReceiver?.stop()
        rtpVideoReceiver = null
        videoRendererPipeline?.release()
        videoRendererPipeline = null
        _displayManager.stopStreaming()
        Timber.d("视频接收已停止")
    }

    /**
     * 需要时启动显示
     */
    private fun startDisplayIfNeeded() {
        if (!_displayManager.isShowing()) {
            startDisplay()
        }
    }

    /**
     * 在默认显示器上启动显示
     */
    fun startDisplay(): Boolean {
        val presentation = _displayManager.startOnDefaultDisplay()
        if (presentation != null) {
            currentPresentation = presentation
            // 设置停止回调
            presentation.onStopClick = {
                stopVideoReceiver()
            }
            return true
        }
        return false
    }

    /**
     * 在指定显示器上启动显示
     */
    fun startDisplay(display: Display): Boolean {
        val presentation = _displayManager.startPresentation(display)
        if (presentation != null) {
            currentPresentation = presentation
            presentation.onStopClick = {
                stopVideoReceiver()
            }
            return true
        }
        return false
    }

    /**
     * 停止显示
     */
    fun stopDisplay() {
        _displayManager.stopPresentation()
        currentPresentation = null
    }

    /**
     * 获取显示管理器
     */
    fun getDisplayManager(): MiracastDisplayManager = _displayManager

    /**
     * 获取当前显示状态
     */
    fun isDisplaying(): Boolean = _displayManager.isShowing()

    /**
     * 释放资源
     */
    fun release() {
        // 同步执行停止服务，避免 scope.cancel() 抢占尚未完成的 stopServices()
        runBlocking { stopServices() }
        _displayManager.release()
        discoveryManager.release()
        scope.cancel()
        instance = null
        Timber.d("MiracastManager 已释放")
    }

    /**
     * 停止所有服务
     */
    private suspend fun stopServices() {
        // 取消 NSD 注册
        unregisterNsdService()
        // 取消 WifiP2pManager 注册
        unregisterWifiP2pService()
        // 停止 RTSP 服务器
        wfdRTSPServer?.stop()
        wfdRTSPServer = null
        // 停止视频接收
        stopVideoReceiver()
        isReceiverStarted = false
        Timber.d("Miracast 服务已停止")
    }

    /**
     * 获取当前连接的设备名称（用于显示）
     */
    fun getCurrentDeviceName(): String? = currentDevice?.name
}
