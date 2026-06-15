package com.kbtv.caster.miracast.presentation

import android.app.Presentation
import android.content.Context
import android.hardware.display.DisplayManager
import android.view.Display
import timber.log.Timber

/**
 * Miracast 显示管理器
 * 
 * 管理 Presentation 的创建、显示和销毁
 */
class MiracastDisplayManager(
    private val context: Context
) {
    companion object {
        private const val TAG = "MiracastDisplayManager"
    }

    private val displayManager: DisplayManager by lazy {
        context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
    }

    private var currentPresentation: MiracastPresentation? = null
    private var activeDisplay: Display? = null

    /**
     * 获取所有可用的 Presentation 显示器
     */
    fun getPresentationDisplays(): List<Display> {
        return displayManager.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION).toList()
    }

    /**
     * 获取默认显示器
     */
    fun getDefaultDisplay(): Display {
        return displayManager.getDisplay(Display.DEFAULT_DISPLAY)
    }

    /**
     * 在指定显示器上开始显示
     */
    fun startPresentation(display: Display): MiracastPresentation? {
        // 如果已在该显示器上显示，不重复创建
        if (currentPresentation?.display?.displayId == display.displayId) {
            Timber.d("已在该显示器上显示")
            return currentPresentation
        }

        // 停止当前显示
        stopPresentation()

        // 检查显示器是否可用
        if (!display.isValid) {
            Timber.e("显示器不可用")
            return null
        }

        Timber.d("在显示器 ${display.name} 上创建 Presentation")

        try {
            currentPresentation = MiracastPresentation(context, display)
            currentPresentation?.show()
            activeDisplay = display
            
            Timber.i("Presentation 已显示在 ${display.name}")
            return currentPresentation
        } catch (e: Exception) {
            Timber.e(e, "创建 Presentation 失败")
            return null
        }
    }

    /**
     * 在默认显示器上开始显示
     */
    fun startOnDefaultDisplay(): MiracastPresentation? {
        val defaultDisplay = getDefaultDisplay()
        return startPresentation(defaultDisplay)
    }

    /**
     * 停止当前显示
     */
    fun stopPresentation() {
        currentPresentation?.dismiss()
        currentPresentation = null
        activeDisplay = null
        Timber.d("Presentation 已停止")
    }

    /**
     * 启动视频流
     */
    fun startStreaming(rtpPort: Int = 50000) {
        currentPresentation?.startStreaming(rtpPort)
    }

    /**
     * 停止视频流
     */
    fun stopStreaming() {
        currentPresentation?.stopStreaming()
    }

    /**
     * 更新状态信息
     */
    fun updateStatus(message: String) {
        currentPresentation?.let {
            // 可以添加状态更新逻辑
        }
    }

    /**
     * 获取当前显示器的显示模式
     */
    fun getCurrentDisplayMode(): Display.Mode? {
        return activeDisplay?.mode
    }

    /**
     * 获取当前显示器分辨率
     */
    fun getCurrentResolution(): Pair<Int, Int>? {
        val display = activeDisplay ?: return null
        val mode = display.mode ?: return null
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            Pair(mode.physicalWidth, mode.physicalHeight)
        } else {
            @Suppress("DEPRECATION")
            Pair(display.width, display.height)
        }
    }

    /**
     * 检查是否正在显示
     */
    fun isShowing(): Boolean {
        return currentPresentation != null && currentPresentation?.isShowing == true
    }

    /**
     * 获取当前 Presentation
     */
    fun getCurrentPresentation(): MiracastPresentation? = currentPresentation

    /**
     * 释放资源
     */
    fun release() {
        stopPresentation()
        Timber.d("DisplayManager 资源已释放")
    }
}
