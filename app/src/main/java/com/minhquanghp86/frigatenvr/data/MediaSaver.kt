package com.minhquanghp86.frigatenvr.data

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.minhquanghp86.frigatenvr.notify.Notify
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/** Lưu media (clip/ảnh) từ Frigate về máy — dùng OkHttp để giữ được JWT + TLS self-signed. */
object MediaSaver {

    /**
     * Tải và lưu vào Downloads (Android 10+) hoặc thư mục riêng của app (8–9).
     * Hiện thông báo tiến trình trên thanh trạng thái; trả về đường dẫn đã lưu hoặc null.
     */
    suspend fun save(
        context: Context,
        client: OkHttpClient,
        url: String,
        fileName: String,
        mime: String,
    ): String? = withContext(Dispatchers.IO) {
        val nm = NotificationManagerCompat.from(context)
        val notificationId = (fileName.hashCode() and 0x7fffffff)
        val canNotify = nm.areNotificationsEnabled()

        fun showProgress(percent: Int?, text: String) {
            if (!canNotify) return
            val builder = NotificationCompat.Builder(context, Notify.CHANNEL_DOWNLOADS)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle("Đang tải $fileName")
                .setContentText(text)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
            if (percent != null) {
                builder.setProgress(100, percent, false)
            } else {
                builder.setProgress(0, 0, true)
            }
            runCatching { nm.notify(notificationId, builder.build()) }
        }

        fun showDone(path: String) {
            if (!canNotify) return
            val done = NotificationCompat.Builder(context, Notify.CHANNEL_DOWNLOADS)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle("Đã lưu $fileName")
                .setContentText(path)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(Notify.openAppIntent(context))
                .build()
            runCatching { nm.notify(notificationId, done) }
        }

        fun showFailed(message: String) {
            if (!canNotify) return
            val failed = NotificationCompat.Builder(context, Notify.CHANNEL_DOWNLOADS)
                .setSmallIcon(android.R.drawable.stat_notify_error)
                .setContentTitle("Không tải được $fileName")
                .setContentText(message)
                .setAutoCancel(true)
                .build()
            runCatching { nm.notify(notificationId, failed) }
        }

        showProgress(null, "Đang kết nối…")

        val response = runCatching {
            client.newCall(Request.Builder().url(url).build()).execute()
        }.getOrNull()
        if (response == null) {
            showFailed("Không kết nối được Frigate")
            return@withContext null
        }

        response.use { resp ->
            if (!resp.isSuccessful) {
                showFailed("Server trả HTTP ${resp.code}")
                return@withContext null
            }
            val body = resp.body
            if (body == null) {
                showFailed("Không có dữ liệu")
                return@withContext null
            }
            val total = body.contentLength()

            val output: java.io.OutputStream?
            val finalPath: String
            if (Build.VERSION.SDK_INT >= 29) {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, mime)
                    put(
                        MediaStore.MediaColumns.RELATIVE_PATH,
                        Environment.DIRECTORY_DOWNLOADS + "/Frigate",
                    )
                }
                val uri = runCatching {
                    context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                }.getOrNull()
                if (uri == null) {
                    showFailed("Không tạo được tệp trong Downloads")
                    return@withContext null
                }
                output = runCatching { context.contentResolver.openOutputStream(uri) }.getOrNull()
                finalPath = "Downloads/Frigate/$fileName"
            } else {
                val dir = File(context.getExternalFilesDir(null), "Frigate").apply { mkdirs() }
                val file = File(dir, fileName)
                output = runCatching { file.outputStream() }.getOrNull()
                finalPath = file.absolutePath
            }

            if (output == null) {
                showFailed("Không mở được tệp đích")
                return@withContext null
            }

            var written = 0L
            var lastNotified = 0L
            val buffer = ByteArray(64 * 1024)
            try {
                body.byteStream().use { input ->
                    output.use { out ->
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            out.write(buffer, 0, read)
                            written += read
                            val now = System.currentTimeMillis()
                            if (now - lastNotified > 400) {
                                lastNotified = now
                                val percent = if (total > 0) ((written * 100) / total).toInt() else null
                                showProgress(percent, formatBytes(written) + if (total > 0) " / ${formatBytes(total)}" else "")
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                showFailed(e.message ?: "Lỗi khi ghi tệp")
                return@withContext null
            }

            showDone(finalPath)
            return@withContext finalPath
        }
    }

    private fun formatBytes(bytes: Long): String = when {
        bytes >= 1024L * 1024 * 1024 -> "%.2f GB".format(bytes / 1024.0 / 1024 / 1024)
        bytes >= 1024L * 1024 -> "%.1f MB".format(bytes / 1024.0 / 1024)
        bytes >= 1024 -> "%.0f KB".format(bytes / 1024.0)
        else -> "$bytes B"
    }
}
