package com.minhquanghp86.frigatenvr.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

/**
 * Đọc luồng MJPEG của Frigate (GET /api/{camera}?fps=&height=) -> Flow<Bitmap>.
 * Mỗi khung là một ảnh JPEG nối tiếp nhau, tách bằng marker SOI/EOI.
 */
class MjpegSource(
    private val client: OkHttpClient,
    private val url: String,
) {

    fun frames(): Flow<Bitmap> = callbackFlow {
        val request = Request.Builder().url(url).build()
        val call = client.newCall(request)
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                close(e)
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body
                if (!response.isSuccessful || body == null) {
                    close(IOException("MJPEG trả HTTP ${response.code}"))
                    response.close()
                    return
                }
                try {
                    parseFrames(body.byteStream()) { bitmap -> trySend(bitmap) }
                    close()
                } catch (e: Exception) {
                    close(e)
                } finally {
                    runCatching { body.close() }
                }
            }
        })
        awaitClose { call.cancel() }
    }.conflate()

    /** Quét byte tìm FF D8 ... FF D9 rồi giải mã thành bitmap. */
    private fun parseFrames(input: InputStream, emit: (Bitmap) -> Unit) {
        val buffer = ByteArray(16 * 1024)
        val frame = ByteArrayOutputStream(256 * 1024)
        var inFrame = false
        var prev = -1

        while (!Thread.currentThread().isInterrupted) {
            val read = input.read(buffer)
            if (read < 0) break
            for (i in 0 until read) {
                val b = buffer[i].toInt() and 0xFF
                if (!inFrame) {
                    if (prev == 0xFF && b == 0xD8) {
                        frame.reset()
                        frame.write(0xFF)
                        frame.write(0xD8)
                        inFrame = true
                        prev = -1
                    } else {
                        prev = b
                    }
                } else {
                    frame.write(b)
                    if (prev == 0xFF && b == 0xD9) {
                        val bytes = frame.toByteArray()
                        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        if (bitmap != null) emit(bitmap)
                        frame.reset()
                        inFrame = false
                        prev = -1
                    } else if (frame.size() > MAX_FRAME_BYTES) {
                        // Khung hỏng/quá lớn -> bỏ, tìm lại điểm bắt đầu.
                        frame.reset()
                        inFrame = false
                        prev = -1
                    } else {
                        prev = b
                    }
                }
            }
        }
    }

    companion object {
        private const val MAX_FRAME_BYTES = 8 * 1024 * 1024
    }
}
