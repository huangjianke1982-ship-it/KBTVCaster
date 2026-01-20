package com.kbtv.caster.service

import android.app.ActivityManager
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Binder
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.kbtv.caster.ui.MainActivity
import com.kbtv.caster.dlna.DLNAUtils
import com.zxt.dlna.dmr.ZxtMediaPlayer
import timber.log.Timber

/**
 * Main coordinator service that manages all casting services
 * Uses TVRemoteIME's DLNA implementation via DLNAUtils
 * Auto-restarts on errors
 */
class CastCoordinatorService : Service(), ZxtMediaPlayer.PlaybackListener {

    private val binder = LocalBinder()

    // Service status
    private val _serviceStatus = MutableLiveData<ServiceStatus>(ServiceStatus.STOPPED)
    val serviceStatus: LiveData<ServiceStatus> = _serviceStatus

    private val _deviceName = MutableLiveData<String>("")
    val deviceName: LiveData<String> = _deviceName

    private val _rendererReady = MutableLiveData<Boolean>(false)
    val rendererReady: LiveData<Boolean> = _rendererReady

    // Server state
    private var isRunning = false

    // ExoPlayer for direct playback
    private var exoPlayer: ExoPlayer? = null
    private var currentMediaUri: String? = null

    // Auto-restart handler
    private val restartHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var restartAttempts = 0
    private val maxRestartAttempts = 5
    private val restartDelayMillis = 5000L

    enum class ServiceStatus {
        STOPPED, STARTING, RUNNING, ERROR
    }

    inner class LocalBinder : Binder() {
        fun getService(): CastCoordinatorService = this@CastCoordinatorService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        Timber.d("CastCoordinatorService created")
        // 设备名称固定为"凯机投屏"
        _deviceName.value = "凯机投屏"
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> start()
            ACTION_STOP -> stop()
            else -> start() // Auto-start on default
        }
        return START_STICKY
    }

    override fun onDestroy() {
        stop()
        super.onDestroy()
    }

    /**
     * Start all casting services with auto-recovery
     * Uses TVRemoteIME's DLNA implementation
     */
    private fun start() {
        if (isRunning) return

        Timber.d("Starting CastCoordinatorService...")
        _serviceStatus.value = ServiceStatus.STARTING

        try {
            // Initialize ExoPlayer
            initializePlayer()

            // Initialize DLNA using TVRemoteIME's implementation
            initializeDlna()

            isRunning = true
            _serviceStatus.value = ServiceStatus.RUNNING

            Timber.d("CastCoordinatorService started successfully")
            showNotification("凯机投屏服务运行中", "点击打开遥控器")

        } catch (e: Exception) {
            Timber.e(e, "Failed to start CastCoordinatorService")
            _serviceStatus.value = ServiceStatus.ERROR
            scheduleRestart()
        }
    }

    /**
     * Initialize DLNA using TVRemoteIME's DLNAUtils
     */
    private fun initializeDlna() {
        try {
            // Set this service as the playback listener
            DLNAUtils.setPlaybackListener(this)

            // Use TVRemoteIME's DLNA implementation
            DLNAUtils.startDLNAService(this)
            _rendererReady.value = true
            Timber.d("TVRemoteIME DLNA initialized successfully")
        } catch (e: Exception) {
            Timber.e(e, "Failed to initialize TVRemoteIME DLNA")
            _rendererReady.value = false
        }
    }

    /**
     * Initialize ExoPlayer for media playback
     */
    private fun initializePlayer() {
        if (exoPlayer == null) {
            try {
                exoPlayer = ExoPlayer.Builder(this).build()
                exoPlayer?.addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        Timber.d("ExoPlayer playback state: $playbackState")
                    }

                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        Timber.d("ExoPlayer isPlaying: $isPlaying")
                    }
                })
                Timber.d("ExoPlayer initialized successfully")
            } catch (e: Exception) {
                Timber.e(e, "Failed to initialize ExoPlayer")
            }
        }
    }

    /**
     * Stop all casting services
     */
    private fun stop() {
        if (!isRunning) return

        Timber.d("Stopping CastCoordinatorService...")

        try {
            // Stop DLNA using TVRemoteIME's implementation
            try {
                DLNAUtils.stopDLNAService()
            } catch (e: Exception) {
                Timber.w("Error stopping DLNA: ${e.message}")
            }

            // Stop ExoPlayer
            try {
                exoPlayer?.stop()
                exoPlayer?.release()
                exoPlayer = null
                Timber.d("ExoPlayer stopped")
            } catch (e: Exception) {
                Timber.w("Error stopping ExoPlayer: ${e.message}")
            }

        } catch (e: Exception) {
            Timber.e(e, "Error during stop")
        }

        isRunning = false
        _serviceStatus.value = ServiceStatus.STOPPED
        _rendererReady.value = false

        Timber.d("CastCoordinatorService stopped")
    }

    /**
     * Get device suffix for unique naming
     */
    private fun getDeviceSuffix(): String {
        return try {
            val mac = android.provider.Settings.Secure.getString(
                contentResolver,
                android.provider.Settings.Secure.ANDROID_ID
            ) ?: "0000"
            mac.takeLast(4).uppercase()
        } catch (e: Exception) {
            "6ECA"
        }
    }

    /**
     * Show foreground service notification
     */
    private fun showNotification(title: String, content: String) {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    /**
     * Schedule restart after failure
     */
    private fun scheduleRestart() {
        if (restartAttempts < maxRestartAttempts) {
            restartAttempts++
            Timber.d("Scheduling restart attempt ${restartAttempts}/${maxRestartAttempts} in ${restartDelayMillis}ms")
            restartHandler.postDelayed({
                start()
            }, restartDelayMillis)
        } else {
            Timber.e("Max restart attempts reached, giving up")
        }
    }

    /**
     * Play media URL directly (for testing)
     */
    fun playMediaUrl(url: String) {
        try {
            currentMediaUri = url
            val mediaItem = MediaItem.fromUri(url)
            exoPlayer?.setMediaItem(mediaItem)
            exoPlayer?.prepare()
            exoPlayer?.play()
            Timber.d("Playing media: $url")
        } catch (e: Exception) {
            Timber.e(e, "Failed to play media")
        }
    }

    /**
     * Stop media playback
     */
    fun stopMedia() {
        try {
            exoPlayer?.stop()
            currentMediaUri = null
            Timber.d("Media stopped")
        } catch (e: Exception) {
            Timber.e(e, "Failed to stop media")
        }
    }

    /**
     * Get ExoPlayer instance for video rendering
     */
    fun getExoPlayer(): ExoPlayer? = exoPlayer

    // ===== ZxtMediaPlayer.PlaybackListener implementation =====

    override fun onPlay(uri: Uri) {
        Timber.d("PlaybackListener onPlay: $uri")
        runOnUiThread {
            try {
                currentMediaUri = uri.toString()
                val mediaItem = MediaItem.fromUri(uri)
                exoPlayer?.setMediaItem(mediaItem)
                exoPlayer?.prepare()
                exoPlayer?.play()
                Timber.d("ExoPlayer started playback: $uri")

                // 投屏时自动将Activity带到前台
                val activityManager = this@CastCoordinatorService.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                val isForeground = activityManager.runningAppProcesses
                    .find { proc -> proc.pid == android.os.Process.myPid() }?.importance
                    ?.equals(ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND)
                    ?: false

                if (isForeground == false) {
                    val intent = Intent(this@CastCoordinatorService, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    startActivity(intent)
                }

            } catch (e: Exception) {
                Timber.e(e, "Failed to play media from URI")
            }
        }
    }

    override fun onPause() {
        Timber.d("PlaybackListener onPause")
        runOnUiThread {
            exoPlayer?.pause()
        }
    }

    override fun onStop() {
        Timber.d("PlaybackListener onStop")
        runOnUiThread {
            exoPlayer?.stop()
        }
    }

    override fun onSeek(positionMs: Long) {
        Timber.d("PlaybackListener onSeek: $positionMs")
        runOnUiThread {
            exoPlayer?.seekTo(positionMs)
        }
    }

    /**
     * Run code on UI thread
     */
    private fun runOnUiThread(runnable: () -> Unit) {
        val handler = android.os.Handler(mainLooper)
        handler.post(runnable)
    }

    companion object {
        const val ACTION_START = "com.caster.tv.action.START"
        const val ACTION_STOP = "com.caster.tv.action.STOP"
        private const val NOTIFICATION_CHANNEL_ID = "castertv_channel"
        private const val NOTIFICATION_ID = 1001
        private const val NOTIFICATION_CHANNEL_NAME = "CasterTV Service"
    }
}
