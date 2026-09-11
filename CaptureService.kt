package com.example.foldmorph

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class CaptureService : Service() {

    inner class LocalBinder : Binder() {
        fun getService(): CaptureService = this@CaptureService
    }

    private val binder = LocalBinder()
    var snapshotEngine: ScreenSnapshotEngine? = null
        private set

    private var mediaProjection: MediaProjection? = null

    override fun onBind(intent: Intent?): IBinder = binder

    fun initializeProjection(resultCode: Int, data: Intent) {
        createNotificationChannel()
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("FoldMorph Engine Active")
            .setContentText("Monitoring screen buffer and hinge sensor")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = projectionManager.getMediaProjection(resultCode, data)
        mediaProjection?.let {
            snapshotEngine = ScreenSnapshotEngine(this, it)
            snapshotEngine?.startStreaming()
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Capture Service Channel",
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    override fun onDestroy() {
        super.onDestroy()
        snapshotEngine?.stop()
        mediaProjection?.stop()
    }

    companion object {
        private const val CHANNEL_ID = "fold_morph_channel"
        private const val NOTIFICATION_ID = 101
    }
}
