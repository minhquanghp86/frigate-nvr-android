package com.minhquanghp86.frigatenvr.ui.cast

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cast
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.mediarouter.media.MediaRouteSelector
import androidx.mediarouter.media.MediaRouter
import com.google.android.gms.cast.CastMediaControlIntent
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManagerListener
import kotlinx.coroutines.launch
import com.minhquanghp86.frigatenvr.ui.IOSBlue
import com.minhquanghp86.frigatenvr.ui.IOSGreen
import com.minhquanghp86.frigatenvr.ui.IOSLabel
import com.minhquanghp86.frigatenvr.ui.IOSRed
import com.minhquanghp86.frigatenvr.ui.IOSSecondaryLabel

/**
 * Trạng thái Google Cast cho màn live.
 *
 * Frigate API không có endpoint cast, nên app cast HLS của go2rtc tới
 * Default Media Receiver của TV/Chromecast trong cùng LAN.
 */
@Composable
fun rememberCastSession(
    context: Context,
    streamUrl: () -> String?,
    title: () -> String,
): CastHandle {
    val castContext = remember {
        runCatching { CastContext.getSharedInstance(context) }.getOrNull()
    }
    var connected by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun load(session: CastSession) {
        val url = streamUrl() ?: return
        // 21.4.0: MediaMetadata không có Builder — dùng constructor + putString.
        val metadata = MediaMetadata(MediaMetadata.MEDIA_TYPE_GENERIC).apply {
            putString(MediaMetadata.KEY_TITLE, title())
            putString(MediaMetadata.KEY_SUBTITLE, "FriCam · live")
        }
        val info = MediaInfo.Builder(url)
            .setStreamType(MediaInfo.STREAM_TYPE_LIVE)
            .setContentType("application/x-mpegURL")
            .setMetadata(metadata)
            .build()
        session.remoteMediaClient?.load(info)
    }

    DisposableEffect(castContext) {
        val supervisor = castContext?.sessionManager
        val listener = object : SessionManagerListener<CastSession> {
            override fun onSessionStarted(session: CastSession, sessionId: String) {
                connected = true
                error = null
                load(session)
            }

            override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) {
                connected = true
                load(session)
            }

            override fun onSessionEnded(session: CastSession, errorCode: Int) {
                connected = false
            }

            override fun onSessionStartFailed(session: CastSession, errorCode: Int) {
                connected = false
                error = "Không kết nối được thiết bị cast (mã $errorCode)"
            }

            override fun onSessionResumeFailed(session: CastSession, errorCode: Int) {
                connected = false
                error = "Không khôi phục được phiên cast (mã $errorCode)"
            }

            override fun onSessionStarting(session: CastSession) = Unit
            override fun onSessionEnding(session: CastSession) = Unit
            override fun onSessionResuming(session: CastSession, sessionId: String) = Unit
            override fun onSessionSuspended(session: CastSession, reason: Int) = Unit
        }
        supervisor?.addSessionManagerListener(listener, CastSession::class.java)
        // Nếu đang có phiên từ trước, hiển thị đúng trạng thái.
        connected = supervisor?.currentCastSession?.isConnected == true
        onDispose {
            supervisor?.removeSessionManagerListener(listener, CastSession::class.java)
        }
    }

    return CastHandle(
        available = castContext != null,
        connected = connected,
        error = error,
        clearError = { error = null },
        stop = {
            runCatching { castContext?.sessionManager?.endCurrentSession(true) }
            connected = false
        },
    )
}

data class CastHandle(
    val available: Boolean,
    val connected: Boolean,
    val error: String?,
    val clearError: () -> Unit,
    val stop: () -> Unit,
)

/** Hộp thoại chọn thiết bị cast trong LAN (MediaRouter discovery). */
@Composable
fun CastDeviceDialog(
    onDismiss: () -> Unit,
    onStop: (() -> Unit)? = null,
    haConfigured: Boolean = false,
    onLoadHaPlayers: (suspend () -> List<Pair<String, String>>)? = null,
    onCastHa: (suspend (String) -> String)? = null,
) {
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var status by remember { mutableStateOf<String?>(null) }
    var players by remember { mutableStateOf<List<Pair<String, String>>?>(null) }
    var haError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(haConfigured, onLoadHaPlayers) {
        if (!haConfigured || onLoadHaPlayers == null) return@LaunchedEffect
        try {
            players = onLoadHaPlayers()
        } catch (e: Exception) {
            haError = e.message ?: "Không tải được media_player của HA"
        }
    }
    val context = androidx.compose.ui.platform.LocalContext.current
    val castContext = remember {
        runCatching { com.google.android.gms.cast.framework.CastContext.getSharedInstance(context) }
            .getOrNull()
    }
    // mergedSelector của CastContext gồm cả CAST + remote playback — chuẩn theo tài liệu Cast SDK.
    val selector = remember(castContext) {
        castContext?.mergedSelector ?: MediaRouteSelector.Builder()
            .addControlCategory(
                CastMediaControlIntent.categoryForCast(
                    CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID,
                ),
            )
            .build()
    }
    val router = remember { MediaRouter.getInstance(context) }
    var routes by remember { mutableStateOf<List<MediaRouter.RouteInfo>>(emptyList()) }

    // Một số mạng/OEM chặn mDNS nếu app không giữ multicast lock trong lúc dò.
    DisposableEffect(Unit) {
        val wifi = context.applicationContext
            .getSystemService(Context.WIFI_SERVICE) as? android.net.wifi.WifiManager
        val lock = runCatching {
            wifi?.createMulticastLock("fricam-cast")?.apply {
                setReferenceCounted(false)
                acquire()
            }
        }.getOrNull()
        onDispose { runCatching { if (lock?.isHeld == true) lock.release() } }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Truyền màn hình") },
        text = {
            Column(
                Modifier
                    .heightIn(max = 340.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                val hasAny = routes.isNotEmpty() || !players.isNullOrEmpty()
                if (!hasAny && haError == null) {
                    Text(
                        "Không tìm thấy thiết bị nào.",
                        color = IOSSecondaryLabel,
                        fontSize = 13.sp,
                    )
                }
                routes.forEach { route ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                runCatching { router.selectRoute(route) }
                                onDismiss()
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Outlined.Cast,
                            contentDescription = null,
                            tint = IOSBlue,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(route.name, color = IOSLabel, fontSize = 15.sp, maxLines = 1)
                    }
                }
                players.orEmpty().forEach { (entityId, name) ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                scope.launch {
                                    try {
                                        val method = onCastHa?.invoke(entityId)
                                        status = "Đã truyền tới $name · $method"
                                    } catch (e: Exception) {
                                        status = e.message ?: "HA từ chối lệnh"
                                    }
                                }
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Outlined.Tv,
                            contentDescription = null,
                            tint = IOSGreen,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(name, color = IOSLabel, fontSize = 15.sp, maxLines = 1)
                    }
                }
                haError?.let {
                    Text(it, color = IOSRed, fontSize = 11.sp)
                }
                status?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(it, color = IOSSecondaryLabel, fontSize = 11.sp)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Đóng") }
        },
        dismissButton = if (onStop != null) {
            {
                TextButton(
                    onClick = {
                        onStop()
                        onDismiss()
                    },
                ) { Text("Ngắt cast", color = IOSRed) }
            }
        } else {
            null
        },
    )
}
