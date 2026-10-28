package com.minhquanghp86.frigatenvr.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FullscreenExit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.minhquanghp86.frigatenvr.ui.PlayerStore
import com.minhquanghp86.frigatenvr.ui.VideoPlayer

/** Trình phát toàn màn hình: xoay ngang, ẩn thanh hệ thống, thoát bằng nút hoặc Back. */
@Composable
fun FullscreenPlayerScreen(onExit: () -> Unit) {
    val context = LocalContext.current
    val activity = context as? android.app.Activity
    val view = LocalView.current

    // Chụp request 1 lần khi vào màn: tránh việc store bị clear giữa recomposition.
    val request = remember { PlayerStore.request.value }

    DisposableEffect(Unit) {
        // Đổi orientation/insets phải chạy sau khi view đã attach; bọc runCatching vì
        // một số máy ném lỗi nếu view chưa attach (nguyên nhân crash trước đây).
        val apply = Runnable {
            runCatching {
                activity?.requestedOrientation =
                    android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            }
            if (view.isAttachedToWindow) {
                runCatching {
                    activity?.window?.let { window ->
                        val controller = WindowCompat.getInsetsController(window, view)
                        controller.systemBarsBehavior =
                            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                        controller.hide(WindowInsetsCompat.Type.systemBars())
                    }
                }
            }
        }
        view.post(apply)

        onDispose {
            runCatching {
                activity?.requestedOrientation =
                    android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
            runCatching {
                activity?.window?.let { window ->
                    WindowCompat.getInsetsController(window, view)
                        .show(WindowInsetsCompat.Type.systemBars())
                }
            }
            PlayerStore.clear()
        }
    }

    BackHandler { onExit() }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        request?.let { req ->
            VideoPlayer(
                url = req.url,
                startPositionMs = req.startPositionMs,
                modifier = Modifier.fillMaxSize(),
            )
        }

        IconButton(
            onClick = onExit,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp)
                .size(40.dp)
                .background(Color(0x99202429), RoundedCornerShape(10.dp)),
        ) {
            Icon(
                Icons.Outlined.FullscreenExit,
                contentDescription = "Thoát toàn màn hình",
                tint = Color.White,
            )
        }
    }
}
