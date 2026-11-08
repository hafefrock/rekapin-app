package com.example.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity

class AnalysisForegroundService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        activeInstance = this
        createNotificationChannels()
        acquireWakeLock()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        activeInstance = this
        val action = intent?.action

        if (action == ACTION_CANCEL) {
            Log.i(TAG, "Aksi Batalkan diterima dari notifikasi foreground")
            onCancelRequested?.invoke()
            stopServiceInternal()
            return START_NOT_STICKY
        }

        if (action == ACTION_STOP) {
            Log.i(TAG, "Aksi Stop diterima")
            stopServiceInternal()
            return START_NOT_STICKY
        }

        val title = intent?.getStringExtra(EXTRA_TITLE) ?: "Analisis Video Rekapin"
        val message = intent?.getStringExtra(EXTRA_MESSAGE) ?: "Sedang menganalisis video anime..."
        val model = intent?.getStringExtra(EXTRA_MODEL) ?: ""
        val currentSegment = intent?.getIntExtra(EXTRA_CURRENT_SEGMENT, 0) ?: 0
        val totalSegments = intent?.getIntExtra(EXTRA_TOTAL_SEGMENTS, 0) ?: 0

        val notification = buildRunningNotification(
            title = title,
            message = message,
            model = model,
            currentSegment = currentSegment,
            totalSegments = totalSegments
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceCompat.startForeground(
                    this,
                    NOTIFICATION_ID_RUNNING,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            } else {
                startForeground(NOTIFICATION_ID_RUNNING, notification)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Gagal startForeground: ${e.message}", e)
        }

        return START_NOT_STICKY
    }

    private fun buildRunningNotification(
        title: String,
        message: String,
        model: String,
        currentSegment: Int,
        totalSegments: Int
    ): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val cancelIntent = Intent(this, AnalysisForegroundService::class.java).apply {
            action = ACTION_CANCEL
        }
        val cancelPendingIntent = PendingIntent.getService(
            this,
            1,
            cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val formattedTitle = if (currentSegment > 0 && totalSegments > 0) {
            "Segmen $currentSegment dari $totalSegments"
        } else {
            title
        }

        val subText = if (model.isNotBlank()) "Model: $model" else null

        val builder = NotificationCompat.Builder(this, CHANNEL_ID_PROGRESS)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle(formattedTitle)
            .setContentText(message)
            .setOngoing(true)
            .setContentIntent(contentPendingIntent)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Batalkan",
                cancelPendingIntent
            )

        if (subText != null) {
            builder.setSubText(subText)
        }

        if (totalSegments > 0) {
            builder.setProgress(totalSegments, currentSegment, false)
        } else {
            builder.setProgress(0, 0, true)
        }

        return builder.build()
    }

    private fun acquireWakeLock() {
        if (wakeLock == null) {
            try {
                val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
                wakeLock = powerManager.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "Rekapin:AnalysisWakeLock"
                ).apply {
                    setReferenceCounted(false)
                    acquire(4 * 60 * 60 * 1000L) // Timeout pengaman 4 jam
                }
                Log.i(TAG, "PARTIAL_WAKE_LOCK berhasil dipegang")
            } catch (e: Exception) {
                Log.w(TAG, "Gagal mendapatkan WAKE_LOCK: ${e.message}")
            }
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
                Log.i(TAG, "PARTIAL_WAKE_LOCK berhasil dilepas")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Gagal melepas WAKE_LOCK: ${e.message}")
        } finally {
            wakeLock = null
        }
    }

    private fun stopServiceInternal() {
        releaseWakeLock()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (activeInstance === this) {
            activeInstance = null
        }
        releaseWakeLock()
        Log.i(TAG, "AnalysisForegroundService dihentikan")
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val progressChannel = NotificationChannel(
                CHANNEL_ID_PROGRESS,
                "Proses Analisis Video",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Menampilkan progres analisis video yang sedang berjalan"
                setShowBadge(false)
            }

            val alertsChannel = NotificationChannel(
                CHANNEL_ID_ALERTS,
                "Pemberitahuan Hasil Analisis",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Pemberitahuan saat analisis selesai, gagal, atau kuota habis"
                setShowBadge(true)
            }

            notificationManager.createNotificationChannel(progressChannel)
            notificationManager.createNotificationChannel(alertsChannel)
        }
    }

    companion object {
        private const val TAG = "RekapinService"

        @Volatile
        private var activeInstance: AnalysisForegroundService? = null

        const val CHANNEL_ID_PROGRESS = "rekapin_analysis_progress"
        const val CHANNEL_ID_ALERTS = "rekapin_analysis_alerts"

        const val NOTIFICATION_ID_RUNNING = 1001
        const val NOTIFICATION_ID_COMPLETED = 1002
        const val NOTIFICATION_ID_FAILED = 1003
        const val NOTIFICATION_ID_QUOTA_EXCEEDED = 1004

        const val ACTION_SHOW = "com.example.service.ACTION_SHOW"
        const val ACTION_STOP = "com.example.service.ACTION_STOP"
        const val ACTION_CANCEL = "com.example.service.ACTION_CANCEL"

        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_MESSAGE = "extra_message"
        const val EXTRA_MODEL = "extra_model"
        const val EXTRA_CURRENT_SEGMENT = "extra_current_segment"
        const val EXTRA_TOTAL_SEGMENTS = "extra_total_segments"

        @Volatile
        var onCancelRequested: (() -> Unit)? = null

        fun start(
            context: Context,
            model: String,
            title: String = "Memulai analisis video...",
            message: String = "Sedang menyiapkan video..."
        ) {
            val intent = Intent(context, AnalysisForegroundService::class.java).apply {
                action = ACTION_SHOW
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_MESSAGE, message)
                putExtra(EXTRA_MODEL, model)
            }
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (e: Exception) {
                Log.w(TAG, "Gagal memulai AnalysisForegroundService: ${e.message}")
            }
        }

        fun updateProgress(
            context: Context,
            currentSegment: Int,
            totalSegments: Int,
            model: String,
            message: String
        ) {
            val title = "Segmen $currentSegment dari $totalSegments"
            val instance = activeInstance
            if (instance != null) {
                try {
                    val notification = instance.buildRunningNotification(
                        title = title,
                        message = message,
                        model = model,
                        currentSegment = currentSegment,
                        totalSegments = totalSegments
                    )
                    val nm = instance.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    nm.notify(NOTIFICATION_ID_RUNNING, notification)
                    return
                } catch (e: Exception) {
                    Log.w(TAG, "Gagal direct notify progress: ${e.message}")
                }
            }
            val intent = Intent(context, AnalysisForegroundService::class.java).apply {
                action = ACTION_SHOW
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_MESSAGE, message)
                putExtra(EXTRA_MODEL, model)
                putExtra(EXTRA_CURRENT_SEGMENT, currentSegment)
                putExtra(EXTRA_TOTAL_SEGMENTS, totalSegments)
            }
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (e: Exception) {
                Log.w(TAG, "Gagal update progress AnalysisForegroundService: ${e.message}")
            }
        }

        fun updateStatus(
            context: Context,
            title: String,
            message: String,
            model: String
        ) {
            val instance = activeInstance
            if (instance != null) {
                try {
                    val notification = instance.buildRunningNotification(
                        title = title,
                        message = message,
                        model = model,
                        currentSegment = 0,
                        totalSegments = 0
                    )
                    val nm = instance.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    nm.notify(NOTIFICATION_ID_RUNNING, notification)
                    return
                } catch (e: Exception) {
                    Log.w(TAG, "Gagal direct notify status: ${e.message}")
                }
            }
            val intent = Intent(context, AnalysisForegroundService::class.java).apply {
                action = ACTION_SHOW
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_MESSAGE, message)
                putExtra(EXTRA_MODEL, model)
            }
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (e: Exception) {
                Log.w(TAG, "Gagal update status AnalysisForegroundService: ${e.message}")
            }
        }

        fun stop(context: Context) {
            val instance = activeInstance
            if (instance != null) {
                try {
                    instance.stopServiceInternal()
                } catch (e: Exception) {
                    Log.w(TAG, "Gagal direct stopServiceInternal: ${e.message}")
                }
            }
            try {
                val intent = Intent(context, AnalysisForegroundService::class.java).apply {
                    action = ACTION_STOP
                }
                context.startService(intent)
            } catch (e: Exception) {
                Log.w(TAG, "Gagal mengirim stop command: ${e.message}")
            }
        }

        fun notifyCompleted(context: Context, videoName: String) {
            if (!hasNotificationPermission(context)) return

            ensureChannelsCreated(context)
            val openAppIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                2,
                openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(context, CHANNEL_ID_ALERTS)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle("Analisis Video Selesai")
                .setContentText("Video \"$videoName\" selesai direkap. Ketuk untuk melihat hasil.")
                .setStyle(
                    NotificationCompat.BigTextStyle()
                        .bigText("Analisis seluruh segmen video \"$videoName\" berhasil diselesaikan. Narasi lengkap, lembar karakter, dan timeline adegan siap dilihat.")
                )
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build()

            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(NOTIFICATION_ID_COMPLETED, notification)
        }

        fun notifyFailed(context: Context, stage: String, errorMessage: String) {
            if (!hasNotificationPermission(context)) return

            ensureChannelsCreated(context)
            val openAppIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                3,
                openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val shortError = if (errorMessage.length > 80) errorMessage.take(80) + "..." else errorMessage

            val notification = NotificationCompat.Builder(context, CHANNEL_ID_ALERTS)
                .setSmallIcon(android.R.drawable.stat_notify_error)
                .setContentTitle("Analisis Video Gagal")
                .setContentText("Tahap: $stage. $shortError")
                .setStyle(
                    NotificationCompat.BigTextStyle()
                        .bigText("Gagal pada $stage.\n\n$errorMessage\n\nKetuk untuk membuka aplikasi dan melanjutkan analisis.")
                )
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build()

            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(NOTIFICATION_ID_FAILED, notification)
        }

        fun notifyQuotaExceeded(context: Context, model: String, waitTimeFormatted: String) {
            if (!hasNotificationPermission(context)) return

            ensureChannelsCreated(context)
            val openAppIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                4,
                openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(context, CHANNEL_ID_ALERTS)
                .setSmallIcon(android.R.drawable.stat_notify_error)
                .setContentTitle("Kuota Harian Gemini Habis")
                .setContentText("Model $model telah mencapai batas kuota harian.")
                .setStyle(
                    NotificationCompat.BigTextStyle()
                        .bigText("Model $model mencapai batas kuota gratis harian (HTTP 429). Perkiraan waktu pulih: $waitTimeFormatted. Ketuk untuk membuka aplikasi dan melanjutkan nanti.")
                )
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build()

            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(NOTIFICATION_ID_QUOTA_EXCEEDED, notification)
        }

        private fun hasNotificationPermission(context: Context): Boolean {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        }

        private fun ensureChannelsCreated(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

                val progressChannel = NotificationChannel(
                    CHANNEL_ID_PROGRESS,
                    "Proses Analisis Video",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Menampilkan progres analisis video yang sedang berjalan"
                    setShowBadge(false)
                }

                val alertsChannel = NotificationChannel(
                    CHANNEL_ID_ALERTS,
                    "Pemberitahuan Hasil Analisis",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Pemberitahuan saat analisis selesai, gagal, atau kuota habis"
                    setShowBadge(true)
                }

                notificationManager.createNotificationChannel(progressChannel)
                notificationManager.createNotificationChannel(alertsChannel)
            }
        }
    }
}
