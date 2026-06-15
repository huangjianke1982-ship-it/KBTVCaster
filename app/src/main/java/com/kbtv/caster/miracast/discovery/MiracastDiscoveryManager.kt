package com.kbtv.caster.miracast.discovery

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.WpsInfo
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pGroup
import android.net.wifi.p2p.WifiP2pManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import java.net.InetAddress
import javax.jmdns.JmDNS
import javax.jmdns.ServiceInfo

/**
 * Miracast 设备发现管理器
 * 
 * 使用 Wi-Fi P2P 进行 Miracast 兼容设备的发现和连接管理
 */
class MiracastDiscoveryManager private constructor(
    private val context: Context
) {
    companion object {
        private const val TAG = "MiracastDiscovery"
        private const val DISCOVERY_TIMEOUT_MS = 120_000L // 2分钟超时
        
        // mDNS 服务配置
        private const val MDNS_SERVICE_TYPE = "_wifi-display._tcp.local."
        private const val MDNS_SERVICE_NAME = "凯机投屏设备"
        private const val WFD_RTSP_PORT = 7236
        
        @Volatile
        private var instance: MiracastDiscoveryManager? = null
        
        private val requiredPermissions = arrayOf(
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            android.Manifest.permission.ACCESS_WIFI_STATE,
            android.Manifest.permission.CHANGE_WIFI_STATE
        )

        fun initialize(context: Context): MiracastDiscoveryManager {
            return instance ?: synchronized(this) {
                instance ?: MiracastDiscoveryManager(context.applicationContext).also {
                    instance = it
                }
            }
        }

        fun getInstance(): MiracastDiscoveryManager {
            return instance ?: throw IllegalStateException(
                "MiracastDiscoveryManager not initialized. Call initialize() first."
            )
        }
    }

    private val wifiP2pManager: WifiP2pManager? by lazy {
        context.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
    }

    private var channel: WifiP2pManager.Channel? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    
    // mDNS 服务注册
    private var jmDNS: JmDNS? = null
    private var mdnsServiceInfo: ServiceInfo? = null
    private var localIpAddress: InetAddress? = null

    // 设备状态流
    private val _devices = MutableStateFlow<List<MiracastDevice>>(emptyList())
    val devices: StateFlow<List<MiracastDevice>> = _devices.asStateFlow()

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.IDLE)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _isDiscovering = MutableStateFlow(false)
    val isDiscovering: StateFlow<Boolean> = _isDiscovering.asStateFlow()

    private var discoveryTimeoutRunnable: Runnable? = null

    // 广播接收器
    // 注意：只注册 WIFI_P2P_CONNECTION_CHANGED_ACTION，与 MirrorCast-SinkApp 保持一致
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                    handleConnectionChanged(intent)
                }
            }
        }
    }

    init {
        registerReceiver()
    }

    private fun registerReceiver() {
        val intentFilter = IntentFilter().apply {
            // 只注册连接更改广播，与 MirrorCast-SinkApp 保持一致
            addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
        }
        context.registerReceiver(receiver, intentFilter)
    }

    /**
     * 检查所需权限是否已授予
     */
    fun hasRequiredPermissions(): Boolean {
        return requiredPermissions.all { permission ->
            context.checkSelfPermission(permission) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * 获取缺失的权限列表
     */
    fun getMissingPermissions(): List<String> {
        return requiredPermissions.filter { permission ->
            context.checkSelfPermission(permission) != android.content.pm.PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * 初始化 Wi-Fi P2P 通道
     * 设置 WFD 信息并开始广播，让手机能够发现此设备
     * 
     * 按照 MirrorCast-SinkApp 的顺序：
     * 1. 先设置 WFD 信息
     * 2. WFD 设置成功后设置设备名称
     * 3. 设备名称设置成功后开始 discoverPeers()
     */
    fun initializeChannel() {
        if (channel == null) {
            channel = wifiP2pManager?.initialize(context, Looper.getMainLooper()) {
                Timber.w("Wi-Fi P2P 通道已断开")
                _connectionState.value = ConnectionState.DISCONNECTED
            }
            Timber.d("Wi-Fi P2P 通道初始化完成")

            // 设置 P2P device_type (关键！MirrorCast-SinkApp 设置了 10-0050F204-5)
            setP2pDeviceType()

            // 步骤 1: 设置 WFD 信息
            setEnableWFDWithCallback { wfdSuccess ->
                if (wfdSuccess) {
                    // 步骤 2: 设置设备名称
                    setP2pDeviceNameWithCallback { nameSuccess ->
                        if (nameSuccess) {
                            // 步骤 3: 开始广播
                            mainHandler.postDelayed({
                                startAdvertising()
                            }, 300)
                        } else {
                            Timber.e("设备名称设置失败，但仍尝试广播")
                            mainHandler.postDelayed({
                                startAdvertising()
                            }, 300)
                        }
                    }
                } else {
                    Timber.e("WFD 信息设置失败，但仍尝试设置设备名称")
                    setP2pDeviceNameWithCallback { nameSuccess ->
                        if (nameSuccess) {
                            mainHandler.postDelayed({
                                startAdvertising()
                            }, 300)
                        }
                    }
                }
            }
        }
    }

    /**
     * 开始广播 WFD 设备信息
     * 调用 discoverPeers() 让其他设备能够发现此 Sink 设备
     * 同时注册 mDNS 服务让手机能够通过服务发现找到此设备
     * 
     * 注意：不创建 P2P 组，而是等待手机（Source）创建组并连接过来
     * 这是 Miracast 的标准流程：手机发起投屏请求，电视被动接受连接
     */
    private fun startAdvertising() {
        val manager = wifiP2pManager ?: return
        val ch = channel ?: return

        Timber.d("开始广播 WFD 设备信息，等待手机连接...")
        
        // 注册 mDNS 服务（关键！让手机能够发现此设备）
        registerMDNSService()

        // discoverPeers 会触发 P2P 发现广播，其他设备可以扫描到此设备
        // 但我们不创建组，而是等待手机发起连接
        manager.discoverPeers(ch, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Timber.i("WFD 设备广播成功，等待手机发起投屏请求...")
                // 不再调用 createGroup()，等待 WIFI_P2P_CONNECTION_CHANGED_ACTION
            }

            override fun onFailure(reason: Int) {
                Timber.e("WFD 设备广播失败: $reason")
            }
        })
    }
    
    /**
     * 注册 mDNS 服务（让手机能够发现此设备）
     * 这是 Miracast 设备发现的关键步骤！
     */
    private fun registerMDNSService() {
        try {
            // 获取本机 IP 地址
            localIpAddress = getLocalIpAddress()
            if (localIpAddress == null) {
                Timber.e("无法获取本机 IP 地址，mDNS 服务注册失败")
                return
            }
            
            Timber.d("本机 IP 地址: ${localIpAddress?.hostAddress}")
            
            // 创建 JmDNS 实例
            jmDNS = JmDNS.create(localIpAddress)
            
            // 构建 WFD 设备能力 TXT 记录
            val txtRecord = buildWfdTxtRecord()
            
            // 创建服务信息
            mdnsServiceInfo = ServiceInfo.create(
                MDNS_SERVICE_TYPE,
                MDNS_SERVICE_NAME,
                WFD_RTSP_PORT,
                0,
                0,
                txtRecord
            )
            
            // 注册服务
            jmDNS?.registerService(mdnsServiceInfo)
            
            Timber.i("mDNS 服务注册成功: $MDNS_SERVICE_NAME ($MDNS_SERVICE_TYPE:$WFD_RTSP_PORT)")
        } catch (e: Exception) {
            Timber.e(e, "mDNS 服务注册失败")
        }
    }
    
    /**
     * 取消 mDNS 服务注册
     */
    private fun unregisterMDNSService() {
        try {
            mdnsServiceInfo?.let {
                jmDNS?.unregisterService(it)
            }
            jmDNS?.close()
            jmDNS = null
            mdnsServiceInfo = null
            Timber.d("mDNS 服务已取消注册")
        } catch (e: Exception) {
            Timber.e(e, "取消 mDNS 注册失败")
        }
    }
    
    /**
     * 获取本机 IP 地址
     */
    private fun getLocalIpAddress(): InetAddress? {
        try {
            val interfaces = java.net.NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                val addresses = networkInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    // 跳过环回地址和链路本地地址
                    if (!address.isLoopbackAddress && address is java.net.Inet4Address) {
                        Timber.d("找到 IP: ${address.hostAddress} on ${networkInterface.name}")
                        return address
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "获取本机 IP 失败")
        }
        return null
    }
    
    /**
     * 构建 WFD 设备能力描述 TXT 记录
     * 告诉手机发送端此设备支持的能力
     * 根据 Wi-Fi Display 技术规范 v1.0.0 section 5.1.2
     */
    private fun buildWfdTxtRecord(): Map<String, ByteArray> {
        return mapOf(
            // RTSP 控制端口
            "wfd_rtsp_port" to WFD_RTSP_PORT.toString().toByteArray(),
            // 设备类型: 1 = Sink (接收端)
            "wfd_device_type" to "1".toByteArray(),
            // 设备名称
            "wfd_device_name" to MDNS_SERVICE_NAME.toByteArray(),
            // 会话管理: 0 = 正常
            "wfd_session_management" to "0".toByteArray(),
            // 视频格式 - 支持 H.264 High Profile Level 4.2 1080p@30fps
            "wfd_video_formats" to "00 00 00 01 00000000 00000000 00 000000 00000000 none none".toByteArray(),
            // 音频格式 - 支持 AAC
            "wfd_audio_formats" to "AAC 00000000 00".toByteArray(),
            // 显示 EDID
            "wfd_display_edid" to "00 00000000 00000000 00 00".toByteArray(),
            // 客户端 RTP 端口
            "wfd_client_rtp_ports" to "RTP/AVP/UDP;unicast $WFD_RTSP_PORT 0".toByteArray(),
            // 连接器类型: 00 = HDMI
            "wfd_connector_type" to "00".toByteArray(),
            // 内容保护: none = 不支持 HDCP
            "wfd_content_protection" to "none".toByteArray()
        )
    }

    /**
     * 创建 P2P 组
     * 让电视成为 Group Owner (GO)，手机作为 Client 连接过来
     */
    @SuppressLint("PrivateApi")
    private fun createGroup() {
        val manager = wifiP2pManager ?: return
        val ch = channel ?: return

        Timber.d("尝试创建 P2P 组...")

        // 先清理现有的 P2P 组
        try {
            val removeGroupMethod = manager.javaClass.getMethod(
                "removeGroup",
                WifiP2pManager.Channel::class.java,
                WifiP2pManager.ActionListener::class.java
            )
            removeGroupMethod.invoke(manager, ch, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    Timber.d("已清理现有 P2P 组，尝试创建新组...")
                    doCreateGroup(manager, ch)
                }

                override fun onFailure(reason: Int) {
                    Timber.w("清理 P2P 组失败，尝试直接创建: $reason")
                    doCreateGroup(manager, ch)
                }
            })
        } catch (e: Exception) {
            Timber.e(e, "清理 P2P 组时出错")
            doCreateGroup(manager, ch)
        }
    }

    /**
     * 实际创建 P2P 组
     */
    @SuppressLint("PrivateApi")
    private fun doCreateGroup(manager: WifiP2pManager, ch: WifiP2pManager.Channel) {
        try {
            // 先设置设备类型为 PRIMARY_SINK
            val wfdInfoClass = Class.forName("android.net.wifi.p2p.WifiP2pWfdInfo")
            val wfdInfo = wfdInfoClass.getConstructor().newInstance()

            val setWfdEnabledMethod = wfdInfoClass.getMethod("setWfdEnabled", Boolean::class.javaPrimitiveType)
            setWfdEnabledMethod.invoke(wfdInfo, true)

            val setDeviceTypeMethod = wfdInfoClass.getMethod("setDeviceType", Int::class.javaPrimitiveType)
            setDeviceTypeMethod.invoke(wfdInfo, 1)  // PRIMARY_SINK

            val setSessionAvailableMethod = wfdInfoClass.getMethod("setSessionAvailable", Boolean::class.javaPrimitiveType)
            setSessionAvailableMethod.invoke(wfdInfo, true)

            val setMaxThroughputMethod = wfdInfoClass.getMethod("setMaxThroughput", Int::class.javaPrimitiveType)
            setMaxThroughputMethod.invoke(wfdInfo, 50)

            // 设置端口
            val deviceInfoField = wfdInfoClass.getDeclaredField("mDeviceInfo")
            deviceInfoField.isAccessible = true
            deviceInfoField.setInt(wfdInfo, (7236 shl 16) or 1)

            val setWFDInfoMethod = manager.javaClass.getMethod(
                "setWFDInfo",
                WifiP2pManager.Channel::class.java,
                wfdInfoClass,
                WifiP2pManager.ActionListener::class.java
            )
            setWFDInfoMethod.invoke(manager, ch, wfdInfo, null)

            // 创建 P2P 组，电视作为 GO (Group Owner)
            val createGroupMethod = manager.javaClass.getMethod(
                "createGroup",
                WifiP2pManager.Channel::class.java,
                WifiP2pManager.ActionListener::class.java
            )
            createGroupMethod.invoke(manager, ch, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    Timber.i("P2P 组创建成功，电视成为 Group Owner")
                }

                override fun onFailure(reason: Int) {
                    Timber.e("P2P 组创建失败: $reason")
                }
            })

            Timber.d("正在创建 P2P 组...")
        } catch (e: Exception) {
            Timber.e(e, "创建 P2P 组失败")
        }
    }

    /**
     * 反射调用 setWFDInfo，设置 WFD 设备信息（带回调）
     * 这让手机在搜索时能识别此设备为 Miracast 接收端
     */
    @SuppressLint("PrivateApi")
    private fun setEnableWFDWithCallback(callback: (Boolean) -> Unit) {
        try {
            val manager = wifiP2pManager ?: run { callback(false); return }
            val ch = channel ?: run { callback(false); return }

            // 反射创建 WifiP2pWfdInfo 对象
            val wfdInfoClass = Class.forName("android.net.wifi.p2p.WifiP2pWfdInfo")
            val wfdInfo = wfdInfoClass.getConstructor().newInstance()

            // 调用 setWfdEnabled(true)
            val setWfdEnabledMethod = wfdInfoClass.getMethod("setWfdEnabled", Boolean::class.javaPrimitiveType)
            setWfdEnabledMethod.invoke(wfdInfo, true)

            // 调用 setDeviceType(PRIMARY_SINK = 1)
            val setDeviceTypeMethod = wfdInfoClass.getMethod("setDeviceType", Int::class.javaPrimitiveType)
            setDeviceTypeMethod.invoke(wfdInfo, 1)  // 1 = PRIMARY_SINK

            // 调用 setSessionAvailable(true)
            val setSessionAvailableMethod = wfdInfoClass.getMethod("setSessionAvailable", Boolean::class.javaPrimitiveType)
            setSessionAvailableMethod.invoke(wfdInfo, true)

            // 调用 setMaxThroughput(50)
            val setMaxThroughputMethod = wfdInfoClass.getMethod("setMaxThroughput", Int::class.javaPrimitiveType)
            setMaxThroughputMethod.invoke(wfdInfo, 50)

            // 设置 WFD DeviceInfo (Device Type + Port)
            try {
                val deviceInfoField = wfdInfoClass.getDeclaredField("mDeviceInfo")
                deviceInfoField.isAccessible = true

                val port = 7236
                val deviceType = 1  // PRIMARY_SINK = Sink

                // DeviceInfo = (port << 16) | deviceType
                val deviceInfo = (port shl 16) or deviceType

                deviceInfoField.setInt(wfdInfo, deviceInfo)
                Timber.d("WFD mDeviceInfo 已直接设置为 0x${Integer.toHexString(deviceInfo)} (DeviceType=$deviceType, Port=$port)")
            } catch (e: Exception) {
                Timber.e(e, "无法设置 WFD DeviceInfo")
            }

            // 调用 WifiP2pManager.setWFDInfo()
            val setWFDInfoMethod = manager.javaClass.getMethod(
                "setWFDInfo",
                WifiP2pManager.Channel::class.java,
                wfdInfoClass,
                WifiP2pManager.ActionListener::class.java
            )
            setWFDInfoMethod.invoke(manager, ch, wfdInfo, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    Timber.i("WFD 设备信息设置成功")
                    callback(true)
                }

                override fun onFailure(reason: Int) {
                    Timber.e("WFD 设备信息设置失败: $reason")
                    callback(false)
                }
            })

            Timber.d("WFD 设备信息已设置: enable=true, deviceType=PRIMARY_SINK")
        } catch (e: Exception) {
            Timber.e(e, "设置 WFD 设备信息失败")
            callback(false)
        }
    }

    /**
     * 反射调用 setDeviceName，设置 WiFi P2P 设备名称（带回调）
     */
    @SuppressLint("PrivateApi")
    private fun setP2pDeviceNameWithCallback(callback: (Boolean) -> Unit) {
        try {
            val manager = wifiP2pManager ?: run { callback(false); return }
            val ch = channel ?: run { callback(false); return }

            val deviceName = generateDeviceName()

            val setDeviceNameMethod = manager.javaClass.getMethod(
                "setDeviceName",
                WifiP2pManager.Channel::class.java,
                String::class.java,
                WifiP2pManager.ActionListener::class.java
            )
            setDeviceNameMethod.invoke(manager, ch, deviceName, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    Timber.i("P2P 设备名称设置成功: $deviceName")
                    callback(true)
                }

                override fun onFailure(reason: Int) {
                    Timber.e("P2P 设备名称设置失败: $reason")
                    callback(false)
                }
            })

            Timber.d("P2P 设备名称: $deviceName")
        } catch (e: Exception) {
            Timber.e(e, "设置 P2P 设备名称失败")
            callback(false)
        }
    }

    /**
     * 生成 WiFi P2P 设备名称
     * 使用类似 MirrorCast-SinkApp 的名称格式
     */
    private fun generateDeviceName(): String {
        return "凯机投屏设备"
    }

    /**
     * 设置 WiFi P2P device_type
     * 这是 wpa_supplicant 的配置，不是 WFD 的配置
     * device_type 格式: category-OUI-subcategory
     * 10-0050F204-5 表示 DualRole (Source/Sink)
     */
    @SuppressLint("PrivateApi")
    private fun setP2pDeviceType() {
        try {
            val manager = wifiP2pManager ?: return
            val ch = channel ?: return

            Timber.d("设置 P2P device_type: 10-0050F204-5")

            // 尝试通过反射调用 WifiNative 的方法来设置 device_type
            val wifiNativeClass = Class.forName("android.net.wifi.WifiNative")
            val instanceMethod = wifiNativeClass.getMethod("getInstance", android.content.Context::class.java)
            val wifiNative = instanceMethod.invoke(null, context)

            if (wifiNative != null) {
                // 尝试调用 setP2pDeviceType 方法
                val setP2pDeviceTypeMethod = wifiNativeClass.getMethod("setP2pDeviceType", String::class.java)
                val result = setP2pDeviceTypeMethod.invoke(wifiNative, "10-0050F204-5")
                Timber.d("设置 P2P device_type 结果: $result")
            }
        } catch (e: Exception) {
            Timber.e(e, "无法设置 P2P device_type，尝试其他方法")
            // 尝试通过 wpa_supplicant 控制接口设置
            try {
                val runtime = Runtime.getRuntime()
                val process = runtime.exec("su")
                val outputStream = process.outputStream
                val writer = java.io.OutputStreamWriter(outputStream)
                writer.write("wpa_cli -i p2p0 SET device_type 10-0050F204-5\n")
                writer.flush()
                writer.write("exit\n")
                writer.flush()
                process.waitFor()
                val inputStream = process.inputStream
                val reader = java.io.BufferedReader(java.io.InputStreamReader(inputStream))
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    Timber.d("wpa_cli output: $line")
                }
            } catch (e2: Exception) {
                Timber.e(e2, "无法通过 wpa_cli 设置 device_type")
            }
        }
    }

    /**
     * 获取 Wi-Fi P2P 通道
     */
    fun getChannel(): WifiP2pManager.Channel? {
        return channel
    }

    /**
     * 开始发现 Miracast 设备
     */
    fun startDiscovery() {
        if (!hasRequiredPermissions()) {
            Timber.e("缺少必要权限")
            return
        }

        initializeChannel()

        _isDiscovering.value = true
        _connectionState.value = ConnectionState.DISCOVERING

        wifiP2pManager?.discoverPeers(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Timber.d("设备发现已开始")
                startDiscoveryTimeout()
            }

            override fun onFailure(reason: Int) {
                Timber.e("设备发现失败: $reason")
                _isDiscovering.value = false
                _connectionState.value = ConnectionState.ERROR
            }
        })
    }

    /**
     * 停止设备发现
     */
    fun stopDiscovery() {
        discoveryTimeoutRunnable?.let { mainHandler.removeCallbacks(it) }
        discoveryTimeoutRunnable = null

        wifiP2pManager?.stopPeerDiscovery(channel, null)
        _isDiscovering.value = false
        _connectionState.value = ConnectionState.IDLE
        Timber.d("设备发现已停止")
    }

    /**
     * 请求刷新设备列表
     */
    fun requestPeers() {
        wifiP2pManager?.requestPeers(channel) { peerList ->
            updateDevices(peerList.deviceList.toList())
        }
    }

    /**
     * 连接到指定设备
     */
    fun connect(device: MiracastDevice, callback: ((Boolean) -> Unit)? = null) {
        initializeChannel()

        val config = WifiP2pConfig().apply {
            deviceAddress = device.address
            wps.setup = WpsInfo.PBC
        }

        _connectionState.value = ConnectionState.CONNECTING

        wifiP2pManager?.connect(channel, config, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Timber.d("正在连接到 ${device.name}")
                callback?.invoke(true)
            }

            override fun onFailure(reason: Int) {
                Timber.e("连接失败: $reason")
                _connectionState.value = ConnectionState.ERROR
                callback?.invoke(false)
            }
        })
    }

    /**
     * 断开当前连接
     */
    fun disconnect() {
        wifiP2pManager?.removeGroup(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Timber.d("已断开连接")
                _connectionState.value = ConnectionState.DISCONNECTED
            }

            override fun onFailure(reason: Int) {
                Timber.e("断开连接失败: $reason")
            }
        })
    }

    /**
     * 释放资源
     */
    fun release() {
        stopDiscovery()
        disconnect()
        
        // 取消 mDNS 服务注册
        unregisterMDNSService()
        
        try {
            context.unregisterReceiver(receiver)
        } catch (e: IllegalArgumentException) {
            Timber.w("接收器未注册")
        }
        channel?.close()
        channel = null
        instance = null
        Timber.d("MiracastDiscoveryManager 已释放")
    }

    // ============ 私有方法 ============

    private fun handleP2PStateChanged(intent: Intent) {
        val state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1)
        val isEnabled = state == WifiP2pManager.WIFI_P2P_STATE_ENABLED

        Timber.d("Wi-Fi P2P 状态: ${if (isEnabled) "已启用" else "已禁用"}")

        if (!isEnabled) {
            _connectionState.value = ConnectionState.WIFI_DISABLED
            _devices.value = emptyList()
        }
    }

    private fun handlePeersChanged() {
        Timber.d("设备列表已更改，请求更新")
        requestPeers()
    }

    private fun handleConnectionChanged(intent: Intent) {
        val networkInfo = intent.getParcelableExtra<android.net.NetworkInfo>(
            WifiP2pManager.EXTRA_NETWORK_INFO
        )

        if (networkInfo?.isConnected == true) {
            _connectionState.value = ConnectionState.CONNECTED
            Timber.d("P2P 连接已建立，请求组信息...")

            // 请求组信息，检查我们是 GO 还是 client
            wifiP2pManager?.requestGroupInfo(channel) { group ->
                if (group != null) {
                    val isGroupOwner = group.isGroupOwner
                    val groupOwnerAddress = group.owner?.deviceAddress ?: "unknown"
                    val networkName = group.networkName

                    Timber.d("P2P 组信息:")
                    Timber.d("  网络名称: $networkName")
                    Timber.d("  是否为组拥有者: $isGroupOwner")
                    Timber.d("  组拥有者地址: $groupOwnerAddress")

                    // 获取客户端设备信息
                    val clientDevice = group.clientList.firstOrNull()
                    if (clientDevice != null) {
                        Timber.d("  客户端设备: ${clientDevice.deviceName}")
                        Timber.d("  客户端地址: ${clientDevice.deviceAddress}")

                        // 如果我们是 GO，客户端是手机
                        // 如果我们是 client，组拥有者是手机
                        handleMiracastConnection(isGroupOwner, group, clientDevice)
                    }
                }
            }
        } else {
            _connectionState.value = ConnectionState.DISCONNECTED
            Timber.d("P2P 连接已断开")
        }
    }

    /**
     * 处理 Miracast 连接
     * 当手机发起投屏请求时调用
     */
    private fun handleMiracastConnection(isGroupOwner: Boolean, group: WifiP2pGroup, clientDevice: WifiP2pDevice) {
        if (isGroupOwner) {
            // 电视是 GO，手机是 client
            // 电视作为 Sink，需要启动 RTSP 服务器并等待手机连接
            Timber.d("电视是组拥有者 (GO)，手机是客户端")
            Timber.d("手机地址: ${clientDevice.deviceAddress}")
            // TODO: 获取手机的 IP 地址（用于 ARPUtil 查询）
        } else {
            // 电视是 client，手机是 GO
            // 手机作为 Source 创建了组
            Timber.d("手机是组拥有者 (GO)，电视是客户端")
            Timber.d("组拥有者地址: ${group.owner?.deviceAddress}")
            // TODO: 获取手机的 IP 地址并连接其 RTSP 服务器
        }
    }

    private fun handleThisDeviceChanged(intent: Intent) {
        // 直接从 intent 获取设备信息，不依赖可能不存在的常量
        val extras = intent.extras
        if (extras != null && extras.containsKey("android.net.wifi.p2p.EXTRA_P2P_DEVICE")) {
            val thisDevice = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                extras.getParcelable("android.net.wifi.p2p.EXTRA_P2P_DEVICE", WifiP2pDevice::class.java)
            } else {
                @Suppress("DEPRECATION")
                extras.getParcelable("android.net.wifi.p2p.EXTRA_P2P_DEVICE")
            }
            Timber.d("本机设备信息: ${thisDevice?.deviceName}")
        } else {
            Timber.d("本机设备信息变更")
        }
    }

    private fun updateDevices(deviceList: List<WifiP2pDevice>) {
        val miracastDevices = deviceList
            .filter { device ->
                // 过滤 Miracast 兼容设备
                device.status == WifiP2pDevice.AVAILABLE ||
                device.status == WifiP2pDevice.CONNECTED ||
                device.deviceName.contains("Miracast", ignoreCase = true) ||
                device.deviceName.contains("Wireless Display", ignoreCase = true) ||
                device.deviceName.contains("Cast", ignoreCase = true) ||
                device.deviceName.contains("TV", ignoreCase = true)
            }
            .map { MiracastDevice(it) }
            .distinctBy { it.address }

        _devices.value = miracastDevices
        Timber.d("发现 ${miracastDevices.size} 个 Miracast 兼容设备")
    }

    private fun startDiscoveryTimeout() {
        discoveryTimeoutRunnable = Runnable {
            Timber.d("设备发现超时，自动停止")
            stopDiscovery()
        }.also { runnable ->
            mainHandler.postDelayed(runnable, DISCOVERY_TIMEOUT_MS)
        }
    }
}

/**
 * 连接状态枚举
 */
sealed class ConnectionState {
    data object IDLE : ConnectionState()
    data object DISCOVERING : ConnectionState()
    data object CONNECTING : ConnectionState()
    data object CONNECTED : ConnectionState()
    data object DISCONNECTED : ConnectionState()
    data object WIFI_DISABLED : ConnectionState()
    data object ERROR : ConnectionState()
}
