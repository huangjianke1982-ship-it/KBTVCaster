package com.kbtv.caster.miracast.renderer

import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import timber.log.Timber

/**
 * Surface 管理器
 * 
 * 封装 SurfaceView 的创建、配置和生命周期管理
 */
class SurfaceManager(
    private val surfaceView: SurfaceView
) {
    companion object {
        private const val TAG = "SurfaceManager"
    }

    private var surface: Surface? = null
    private var isReady = false
    private var width: Int = 1920
    private var height: Int = 1080

    private val callbacks = mutableListOf<SurfaceCallback>()

    interface SurfaceCallback {
        fun onSurfaceCreated(surface: Surface, width: Int, height: Int)
        fun onSurfaceChanged(surface: Surface, width: Int, height: Int)
        fun onSurfaceDestroyed(surface: Surface)
    }

    init {
        surfaceView.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                Timber.d("Surface 创建完成")
                surface = holder.surface
                isReady = true
                notifySurfaceCreated(holder.surface, width, height)
            }

            override fun surfaceChanged(holder: SurfaceHolder, format: Int, w: Int, h: Int) {
                Timber.d("Surface 变化: ${w}x${h}")
                width = w
                height = h
                if (isReady) {
                    notifySurfaceChanged(holder.surface, w, h)
                }
            }

            override fun surfaceDestroyed(holder: SurfaceHolder) {
                Timber.d("Surface 销毁")
                isReady = false
                notifySurfaceDestroyed(holder.surface)
                surface = null
            }
        })
    }

    /**
     * 添加 Surface 回调
     */
    fun addCallback(callback: SurfaceCallback) {
        callbacks.add(callback)
    }

    /**
     * 移除 Surface 回调
     */
    fun removeCallback(callback: SurfaceCallback) {
        callbacks.remove(callback)
    }

    /**
     * 获取 Surface
     */
    fun getSurface(): Surface? = surface

    /**
     * 检查 Surface 是否就绪
     */
    fun isReady(): Boolean = isReady && surface != null

    /**
     * 获取当前尺寸
     */
    fun getSize(): Pair<Int, Int> = width to height

    /**
     * 设置固定尺寸
     */
    fun setFixedSize(w: Int, h: Int) {
        surfaceView.holder.setFixedSize(w, h)
        width = w
        height = h
    }

    /**
     * 设置保持长宽比
     */
    fun setKeepAspectRatio() {
        // 由调用者处理布局
    }

    /**
     * 设置缓冲区大小
     */
    fun setBufferSize(w: Int, h: Int) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            surfaceView.holder.setFixedSize(w, h)
        } else {
            @Suppress("DEPRECATION")
            surfaceView.holder.setFixedSize(w, h)
        }
    }

    /**
     * 设置帧率
     */
    fun setFrameRate(fps: Float) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            // API 30+ 支持 setFrameRate
        }
    }

    /**
     * 获取 SurfaceView
     */
    fun getSurfaceView(): SurfaceView = surfaceView

    private fun notifySurfaceCreated(surface: Surface, width: Int, height: Int) {
        callbacks.forEach { it.onSurfaceCreated(surface, width, height) }
    }

    private fun notifySurfaceChanged(surface: Surface, width: Int, height: Int) {
        callbacks.forEach { it.onSurfaceChanged(surface, width, height) }
    }

    private fun notifySurfaceDestroyed(surface: Surface) {
        callbacks.forEach { it.onSurfaceDestroyed(surface) }
    }

    /**
     * 释放资源
     */
    fun release() {
        callbacks.clear()
        isReady = false
        surface = null
        Timber.d("SurfaceManager 已释放")
    }
}
