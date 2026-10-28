package com.minhquanghp86.frigatenvr.notify

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Ghi lại lỗi không bắt được ra file để xem trong app (Cài đặt → Nhật ký → Ứng dụng). */
object CrashLog {

    private const val FILE_NAME = "crash.log"
    private const val MAX_CHARS = 40_000

    private fun file(context: Context): File = File(context.filesDir, FILE_NAME)

    fun install(context: Context) {
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                    .format(Date())
                val text = buildString {
                    append("\n===== $stamp · ${thread.name} =====\n")
                    append(throwable.stackTraceToString())
                    append("\n")
                }
                val f = file(appContext)
                val old = runCatching { f.readText() }.getOrDefault("")
                f.writeText((text + old).take(MAX_CHARS))
            }
            previous?.uncaughtException(thread, throwable)
        }
    }

    fun read(context: Context): String =
        runCatching { file(context).readText() }.getOrDefault("")

    fun clear(context: Context) {
        runCatching { file(context).delete() }
    }
}
