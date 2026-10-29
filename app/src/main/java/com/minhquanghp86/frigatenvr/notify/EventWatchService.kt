package com.minhquanghp86.frigatenvr.notify

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.minhquanghp86.frigatenvr.FrigateApp
import com.minhquanghp86.frigatenvr.MainActivity
import com.minhquanghp86.frigatenvr.data.FrigateEvent
import com.minhquanghp86.frigatenvr.ui.fmtDateTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

object Notify {
    const val CHANNEL_EVENTS = "frigate_events"
    const val CHANNEL_SERVICE = "frigate_watch"
    const val CHANNEL_DOWNLOADS = "frigate_downloads"
    const val FOREGROUND_ID = 1001

    fun ensureChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_EVENTS,
                "Sự kiện camera",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply { description = "Thông báo khi Frigate phát hiện đối tượng" },
        )
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_DOWNLOADS,
                "Tải media",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = "Tiến trình tải clip/ảnh về máy" },
        )
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_SERVICE,
                "Theo dõi sự kiện",
                NotificationManager.IMPORTANCE_LOW,
            ).apply { description = "Thông báo thường trực khi bật theo dõi sự kiện" },
        )
    }

    fun openAppIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /** Mở đúng sự kiện đã báo (kèm camera để dự phòng nếu sự kiện đã bị xoá). */
    fun openEventIntent(context: Context, eventId: String, camera: String): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(MainActivity.EXTRA_EVENT_ID, eventId)
            putExtra(MainActivity.EXTRA_CAMERA, camera)
        }
        return PendingIntent.getActivity(
            context,
            eventId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    fun foregroundNotification(context: Context): Notification {
        val stop = PendingIntent.getService(
            context,
            1,
            Intent(context, EventWatchService::class.java).setAction(EventWatchService.ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(context, CHANNEL_SERVICE)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentTitle("Đang theo dõi sự kiện")
            .setContentText("Frigate NVR · thông báo khi có đối tượng mới")
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(openAppIntent(context))
            .addAction(0, "Dừng", stop)
            .build()
    }
}

/** Foreground service: poll /api/events và bắn thông báo cho sự kiện mới. */
class EventWatchService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        Notify.ensureChannels(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        startForeground(Notify.FOREGROUND_ID, Notify.foregroundNotification(this))
        scope.launch { watch() }
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private suspend fun watch() {
        val repo = (application as FrigateApp).container.repository
        var lastAfter = System.currentTimeMillis() / 1000.0
        val seen = HashSet<String>()

        while (currentCoroutineContext().isActive) {
            if (!repo.notifyEnabled.value) break

            val session = repo.session()
            if (session == null || !session.loggedIn) {
                delay(5_000)
                continue
            }

            try {
                val events = repo.call {
                    events(
                        mapOf(
                            "after" to lastAfter.toString(),
                            "limit" to "30",
                            "include_thumbnails" to "0",
                        ),
                    )
                }
                val cameraFilter = repo.notifyCameras.value
                for (e in events.sortedBy { it.startTime }) {
                    if (!seen.add(e.id)) continue
                    if (cameraFilter.isNotEmpty() && e.camera !in cameraFilter) continue
                    if (e.startTime > lastAfter) lastAfter = e.startTime
                    if (e.hasSnapshot || e.hasClip) postEvent(e)
                }
            } catch (_: Exception) {
                // mất mạng / 401 tạm thời: thử lại vòng sau
            }
            delay(10_000)
        }
        stopSelf()
    }

    private fun postEvent(e: FrigateEvent) {
        val nm = NotificationManagerCompat.from(this)
        if (!nm.areNotificationsEnabled()) return
        val text = buildString {
            append(e.camera)
            append(" · ")
            append(e.label)
            if (e.subLabelDisplay != null) append(" (${e.subLabelDisplay})")
            append(" · ")
            append(fmtDateTime(e.startTime))
        }
        val notification = NotificationCompat.Builder(this, Notify.CHANNEL_EVENTS)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentTitle(if (e.hasClip) "Sự kiện mới" else "Phát hiện mới")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(Notify.openEventIntent(this, e.id, e.camera))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        runCatching { nm.notify(e.id.hashCode(), notification) }
    }

    companion object {
        const val ACTION_STOP = "com.minhquanghp86.frigatenvr.STOP_WATCH"
    }
}
