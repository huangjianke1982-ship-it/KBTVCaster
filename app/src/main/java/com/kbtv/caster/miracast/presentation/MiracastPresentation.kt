package com.kbtv.caster.miracast.presentation

import android.app.Presentation
import android.content.Context
import android.os.Bundle
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import com.kbtv.caster.R
import com.kbtv.caster.miracast.control.FrameSyncController
import com.kbtv.caster.miracast.renderer.SurfaceManager
import com.kbtv.caster.miracast.streaming.RTPVideoReceiver
import com.kbtv.caster.miracast.streaming.VideoStreamProcessor
import timber.log.Timber

/**
 * Miracast Presentation
 *
 * 在第二屏幕上显示 Miracast 视频流
 * 整合了完整的视频接收 -> 解码 -> 渲染管线
 */
class MiracastPresentation(
    context: Context,
    private val display: android.view.Display,
    private val deviceName: String = "Miracast 设备",
    private val onBackClick: (() -> Unit)? = null
) : Presentation(context, display) {

    companion object {
        private const val TAG = "MiracastPresentation"
    }

    // UI 组件
    private var surfaceView: SurfaceView? = null
    private var statusText: TextView? = null
    private var infoText: TextView? = null
    private var deviceNameText: TextView? = null
    private var connectionQualityText: TextView? = null
    private var statusIcon: ImageView? = null
    private var btnPause: ImageButton? = null
    private var btnStop: ImageButton? = null
    private var btnBack: ImageButton? = null
    private var topBar: View? = null
    private var bottomBar: View? = null

    // 管理器
    private var surfaceManager: SurfaceManager? = null
    private var streamProcessor: VideoStreamProcessor? = null
    private var frameSyncController: FrameSyncController? = null
    private var rtpReceiver: RTPVideoReceiver? = null

    // 状态
    private var isInitialized = false
    private var isPlaying = false
    private var isStreaming = false
    private var isPaused = false

    // 统计
    private var startTime: Long = 0
    private var receivedFrames: Int = 0

    // 回调
    var onStopClick: (() -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.miracast_presentation)

        setupViews()
        setupSurface()
    }

    private fun setupViews() {
        surfaceView = findViewById(R.id.miracast_surface_view)
        statusText = findViewById(R.id.miracast_status_text)
        infoText = findViewById(R.id.miracast_info_text)
        deviceNameText = findViewById(R.id.miracast_device_name)
        connectionQualityText = findViewById(R.id.miracast_connection_quality)
        statusIcon = findViewById(R.id.miracast_status_icon)
        btnPause = findViewById(R.id.miracast_btn_pause)
        btnStop = findViewById(R.id.miracast_btn_stop)
        btnBack = findViewById(R.id.miracast_btn_back)
        topBar = findViewById(R.id.miracast_top_bar)
        bottomBar = findViewById(R.id.miracast_bottom_bar)

        // 设置设备名称
        deviceNameText?.text = deviceName

        // 按钮点击事件
        btnPause?.setOnClickListener { togglePause() }
        btnStop?.setOnClickListener {
            onStopClick?.invoke()
            stopStreaming()
        }
        btnBack?.setOnClickListener {
            onBackClick?.invoke()
        }

        updateStatus("初始化中...")
        updateInfo("")
    }

    private fun setupSurface() {
        val view = surfaceView ?: return
        surfaceManager = SurfaceManager(view)
        surfaceManager?.addCallback(object : SurfaceManager.SurfaceCallback {
            override fun onSurfaceCreated(surface: android.view.Surface, width: Int, height: Int) {
                Timber.d("Surface 创建完成: ${width}x${height}")
                initializeVideoPipeline(surface, width, height)
            }

            override fun onSurfaceChanged(surface: android.view.Surface, width: Int, height: Int) {
                Timber.d("Surface 变化: ${width}x${height}")
            }

            override fun onSurfaceDestroyed(surface: android.view.Surface) {
                Timber.d("Surface 销毁")
                releaseVideoPipeline()
            }
        })
    }

    private fun initializeVideoPipeline(surface: android.view.Surface, width: Int, height: Int) {
        try {
            frameSyncController = FrameSyncController(30)
            frameSyncController?.initialize(System.currentTimeMillis() * 1000)
            
            streamProcessor = VideoStreamProcessor(surface)
            val initialized = streamProcessor?.initialize(width, height) ?: false
            
            if (initialized) {
                isInitialized = true
                updateStatus("已就绪")
                Timber.d("视频管道初始化完成")
            } else {
                updateStatus("管道初始化失败")
            }
        } catch (e: Exception) {
            Timber.e(e, "视频管道初始化失败")
            updateStatus("初始化错误: ${e.message}")
        }
    }

    /**
     * 切换暂停/播放
     */
    private fun togglePause() {
        isPaused = !isPaused
        if (isPaused) {
            rtpReceiver?.stop()
            updateStatus("已暂停")
            btnPause?.setImageResource(R.drawable.ic_play)
            connectionQualityText?.text = "已暂停"
        } else {
            // 重新开始接收
            rtpReceiver?.start(
                onFrame = { data, timestamp ->
                    receivedFrames++
                    streamProcessor?.processRTPPacket(data, timestamp)
                },
                onErrorCallback = { error ->
                    Timber.e(error, "RTP 接收错误")
                    updateStatus("接收错误: ${error.message ?: "未知错误"}")
                }
            )
            updateStatus("正在播放...")
            btnPause?.setImageResource(R.drawable.ic_pause)
            connectionQualityText?.text = "已连接"
        }
    }

    /**
     * 开始接收视频流
     */
    fun startStreaming(rtpPort: Int = 50000) {
        if (!isInitialized) {
            Timber.w("视频管道未初始化，无法开始流传输")
            return
        }

        startTime = System.currentTimeMillis()
        receivedFrames = 0
        isPaused = false

        rtpReceiver = RTPVideoReceiver(rtpPort)
        rtpReceiver?.start(
            onFrame = { data, timestamp ->
                receivedFrames++
                streamProcessor?.processRTPPacket(data, timestamp)
            },
            onErrorCallback = { error ->
                Timber.e(error, "RTP 接收错误")
                updateStatus("接收错误: ${error.message ?: "未知错误"}")
            }
        )

        isStreaming = true
        isPlaying = true
        updateStatus("正在播放...")
        connectionQualityText?.text = "已连接"
        statusIcon?.setImageResource(R.drawable.status_indicator_online)
        startStatisticsUpdate()

        Timber.d("开始接收视频流，端口: $rtpPort")
    }

    /**
     * 停止视频流
     */
    fun stopStreaming() {
        isPlaying = false
        isStreaming = false
        isPaused = false

        rtpReceiver?.stop()
        rtpReceiver = null

        frameSyncController?.release()
        frameSyncController = null

        streamProcessor?.release()
        streamProcessor = null

        updateStatus("已停止")
        updateInfo("")
        connectionQualityText?.text = "已断开"
        statusIcon?.setImageResource(R.drawable.status_indicator_offline)
        btnPause?.setImageResource(R.drawable.ic_play)
        Timber.d("视频流已停止")
    }

    /**
     * 请求关键帧
     */
    fun requestKeyFrame() {
        streamProcessor?.requestKeyFrame()
    }

    /**
     * 跳转到指定时间
     */
    fun seekTo(positionMs: Long) {
        frameSyncController?.seekTo(positionMs * 1000)
        Timber.d("跳转到: ${positionMs}ms")
    }

    /**
     * 设置播放速率
     */
    fun setPlaybackRate(rate: Float) {
        frameSyncController?.adjustPlaybackRate(rate)
    }

    private fun updateStatus(message: String) {
        statusText?.post {
            statusText?.text = message
            statusText?.visibility = if (message.isEmpty()) View.GONE else View.VISIBLE
        }
    }

    private fun updateInfo(info: String) {
        infoText?.post {
            infoText?.text = info
            infoText?.visibility = if (info.isEmpty()) View.GONE else View.VISIBLE
        }
    }

    private fun startStatisticsUpdate() {
        val updateRunnable = object : Runnable {
            override fun run() {
                if (!isPlaying) return
                
                val syncStatus = frameSyncController?.getSyncStatus()
                val processorStats = streamProcessor?.getStatistics()
                
                val elapsed = System.currentTimeMillis() - startTime
                val fps = if (elapsed > 0) receivedFrames * 1000 / elapsed else 0
                
                val info = buildString {
                    appendLine("帧率: ${fps}fps")
                    appendLine("接收: ${receivedFrames}")
                    processorStats?.let {
                        appendLine("解码: ${it.framesDecoded}")
                    }
                    syncStatus?.let {
                        appendLine("同步: ${if (it.isSynchronized) "是" else "否"}")
                        appendLine("缓冲: ${it.jitterBufferSize}")
                    }
                }
                
                updateInfo(info)
                statusText?.postDelayed(this, 1000)
            }
        }
        
        statusText?.post(updateRunnable)
    }

    private fun releaseVideoPipeline() {
        stopStreaming()
        surfaceManager?.release()
        surfaceManager = null
        isInitialized = false
        Timber.d("Presentation 管道已释放")
    }

    private fun releaseResources() {
        stopStreaming()
        releaseVideoPipeline()
        Timber.d("Presentation 资源已释放")
    }

    override fun onDisplayRemoved() {
        releaseResources()
        super.onDisplayRemoved()
    }

    override fun onStop() {
        releaseResources()
        super.onStop()
    }

    fun isInitialized(): Boolean = isInitialized
    fun isPlaying(): Boolean = isPlaying
    fun isStreaming(): Boolean = isStreaming
    fun getReceivedFrames(): Int = receivedFrames
}
