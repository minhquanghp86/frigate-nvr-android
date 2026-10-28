package com.minhquanghp86.frigatenvr.data

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/** Lưu media (clip/ảnh) từ Frigate về máy — dùng OkHttp để giữ được JWT + TLS self-signed. */
object MediaSaver {

    suspend fun save(
        context: Context,
        client: OkHttpClient,
        url: String,
        fileName: String,
        mime: String,
    ): String? = withContext(Dispatchers.IO) {
        val response = runCatching {
            client.newCall(Request.Builder().url(url).build()).execute()
        }.getOrNull() ?: return@withContext null

        response.use { resp ->
            if (!resp.isSuccessful) return@withContext null
            val body = resp.body ?: return@withContext null

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
                }.getOrNull() ?: return@withContext null
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        body.byteStream().copyTo(out)
                    }
                }.onFailure { return@withContext null }
                return@withContext "Downloads/Frigate/$fileName"
            }

            // Android 8–9: lưu vào thư mục riêng của app (không cần quyền).
            val dir = File(context.getExternalFilesDir(null), "Frigate").apply { mkdirs() }
            val file = File(dir, fileName)
            runCatching {
                file.outputStream().use { out -> body.byteStream().copyTo(out) }
            }.getOrNull() ?: return@withContext null
            file.absolutePath
        }
    }
}
