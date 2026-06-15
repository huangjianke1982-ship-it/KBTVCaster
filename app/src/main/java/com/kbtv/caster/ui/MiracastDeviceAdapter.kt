package com.kbtv.caster.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.kbtv.caster.R
import com.kbtv.caster.miracast.discovery.ConnectionType
import com.kbtv.caster.miracast.discovery.DeviceStatus
import com.kbtv.caster.miracast.discovery.MiracastDevice

/**
 * Miracast 设备列表适配器
 */
class MiracastDeviceAdapter(
    private val onDeviceClick: (MiracastDevice) -> Unit
) : ListAdapter<MiracastDevice, MiracastDeviceAdapter.DeviceViewHolder>(DeviceDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_miracast_device, parent, false)
        return DeviceViewHolder(view)
    }

    override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
        holder.bind(getItem(position), onDeviceClick)
    }

    class DeviceViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val deviceIcon: ImageView = itemView.findViewById(R.id.deviceIcon)
        private val deviceName: TextView = itemView.findViewById(R.id.deviceName)
        private val deviceType: TextView = itemView.findViewById(R.id.deviceType)
        private val signalStrength: View = itemView.findViewById(R.id.signalStrength)
        private val deviceStatus: TextView = itemView.findViewById(R.id.deviceStatus)

        fun bind(device: MiracastDevice, onClick: (MiracastDevice) -> Unit) {
            deviceName.text = device.name
            deviceType.text = getDeviceTypeText(device.connectionType)

            // 设置状态
            val (statusText, statusColor, indicatorDrawable) = when (device.status) {
                DeviceStatus.AVAILABLE -> Triple("可用", "#4CAF50", R.drawable.status_indicator_online)
                DeviceStatus.CONNECTED -> Triple("已连接", "#2196F3", R.drawable.status_indicator_online)
                DeviceStatus.INVITED -> Triple("已邀请", "#FF9800", R.drawable.status_indicator_connecting)
                else -> Triple("离线", "#757575", R.drawable.status_indicator_offline)
            }
            deviceStatus.text = statusText
            deviceStatus.setTextColor(android.graphics.Color.parseColor(statusColor))
            signalStrength.setBackgroundResource(indicatorDrawable)

            // 根据设备类型设置图标
            val iconRes = when (device.connectionType) {
                ConnectionType.TV -> R.drawable.ic_tv
                ConnectionType.DISPLAY -> R.drawable.ic_tv
                ConnectionType.STICK -> R.drawable.ic_cast
                else -> R.drawable.ic_tv
            }
            deviceIcon.setImageResource(iconRes)

            itemView.setOnClickListener {
                if (device.isValidTarget()) {
                    onClick(device)
                }
            }
        }

        private fun getDeviceTypeText(type: ConnectionType): String {
            return when (type) {
                ConnectionType.TV -> "智能电视"
                ConnectionType.DISPLAY -> "显示器"
                ConnectionType.STICK -> "电视棒"
                ConnectionType.OTHER -> "其他设备"
            }
        }
    }

    class DeviceDiffCallback : DiffUtil.ItemCallback<MiracastDevice>() {
        override fun areItemsTheSame(oldItem: MiracastDevice, newItem: MiracastDevice): Boolean {
            return oldItem.address == newItem.address
        }

        override fun areContentsTheSame(oldItem: MiracastDevice, newItem: MiracastDevice): Boolean {
            return oldItem == newItem
        }
    }
}
