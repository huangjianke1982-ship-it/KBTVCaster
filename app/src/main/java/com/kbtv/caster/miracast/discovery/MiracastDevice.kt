package com.kbtv.caster.miracast.discovery

import android.net.wifi.p2p.WifiP2pDevice

/**
 * Miracast 设备信息封装类
 * 
 * 存储发现的 Miracast 兼容设备信息
 */
data class MiracastDevice(
    val device: WifiP2pDevice,
    val name: String = device.deviceName.ifEmpty { "未知设备" },
    val address: String = device.deviceAddress,
    val status: DeviceStatus = DeviceStatus.fromCode(device.status),
    val isWfdCapable: Boolean = device.status == WifiP2pDevice.AVAILABLE || 
                                   device.status == WifiP2pDevice.CONNECTED
) {
    /**
     * 获取连接类型
     */
    val connectionType: ConnectionType
        get() = when {
            name.contains("TV", ignoreCase = true) -> ConnectionType.TV
            name.contains("Display", ignoreCase = true) -> ConnectionType.DISPLAY
            name.contains("Stick", ignoreCase = true) -> ConnectionType.STICK
            else -> ConnectionType.OTHER
        }
    
    /**
     * 判断是否为有效的 Miracast 目标设备
     */
    fun isValidTarget(): Boolean {
        return status == DeviceStatus.AVAILABLE || status == DeviceStatus.CONNECTED
    }
    
    /**
     * 获取设备状态描述
     */
    fun getStatusDescription(): String {
        return when (status) {
            DeviceStatus.AVAILABLE -> "可用"
            DeviceStatus.INVITED -> "已邀请"
            DeviceStatus.CONNECTED -> "已连接"
            DeviceStatus.FAILED -> "连接失败"
            DeviceStatus.UNKNOWN -> "未知"
            DeviceStatus.UNAVAILABLE -> "不可用"
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MiracastDevice) return false
        return address == other.address
    }

    override fun hashCode(): Int {
        return address.hashCode()
    }
}

/**
 * 设备状态枚举
 */
enum class DeviceStatus(val code: Int) {
    CONNECTED(0),   // WifiP2pDevice.CONNECTED
    INVITED(1),     // WifiP2pDevice.INVITED
    FAILED(2),      // WifiP2pDevice.FAILED
    AVAILABLE(3),   // WifiP2pDevice.AVAILABLE
    UNAVAILABLE(4), // WifiP2pDevice.UNAVAILABLE
    UNKNOWN(-1);

    companion object {
        fun fromCode(code: Int): DeviceStatus {
            return entries.find { it.code == code } ?: UNKNOWN
        }
    }
}

/**
 * 设备类型枚举
 */
enum class ConnectionType {
    TV,
    DISPLAY,
    STICK,
    OTHER
}
