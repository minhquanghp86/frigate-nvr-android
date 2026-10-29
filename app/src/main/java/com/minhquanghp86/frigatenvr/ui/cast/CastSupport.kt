package com.minhquanghp86.frigatenvr.ui.cast

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
    streamUrl: String? = null,
    onStop: (() -> Unit)? = null,
    darboardConfigured: Boolean = false,
    onLoadPanels: (suspend () -> List<Pair<String, String>>)? = null,
    onCastPanel: (suspend (String) -> Unit)? = null,
    haConfigured: Boolean = false,
    onLoadHaPlayers: (suspend () -> List<Pair<String, String>>)? = null,
    onCastHa: (suspend (String) -> Unit)? = null,
) {
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var status by remember { mutableStateOf<String?>(null) }
    var panels by remember { mutableStateOf<List<Pair<String, String>>?>(null) }
    var players by remember { mutableStateOf<List<Pair<String, String>>?>(null) }
    var panelError by remember { mutableStateOf<String?>(null) }
    var haError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(darboardConfigured, onLoadPanels) {
        if (!darboardConfigured || onLoadPanels == null) return@LaunchedEffect
        try {
            panels = onLoadPanels()
        } catch (e: Exception) {
            panelError = e.message ?: "Không tải được danh sách panel"
        }
    }
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
    var onWifi by remember { mutableStateOf<Boolean?>(null) }

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

    // Chẩn đoán: cast cần điện thoại ở cùng WiFi với TV (không phải 4G/VPN).
    LaunchedEffect(Unit) {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE)
            as? android.net.ConnectivityManager
        val caps = cm?.getNetworkCapabilities(cm.activeNetwork)
        onWifi = caps?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI)
    }

    DisposableEffect(router, selector) {
        val callback = object : MediaRouter.Callback() {
            override fun onRouteAdded(mediaRouter: MediaRouter, route: MediaRouter.RouteInfo) {
                routes = mediaRouter.routes.filter { it.matchesSelector(selector) }
            }

            override fun onRouteRemoved(mediaRouter: MediaRouter, route: MediaRouter.RouteInfo) {
                routes = mediaRouter.routes.filter { it.matchesSelector(selector) }
            }

            override fun onRouteChanged(mediaRouter: MediaRouter, route: MediaRouter.RouteInfo) {
                routes = mediaRouter.routes.filter { it.matchesSelector(selector) }
            }
        }
        router.addCallback(selector, callback, MediaRouter.CALLBACK_FLAG_REQUEST_DISCOVERY)
        routes = router.routes.filter { it.matchesSelector(selector) }
        onDispose { router.removeCallback(callback) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Phát lên thiết bị") },
        text = {
            Column(Modifier.height(260.dp).verticalScroll(rememberScrollState())) {
                Text(
                    "Thiết bị cast phải truy cập được go2rtc (port 1984) trong cùng mạng LAN.",
                    fontSize = 11.sp,
                    color = IOSSecondaryLabel,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    when (onWifi) {
                        true -> "Điện thoại đang dùng WiFi — nên thấy được TV/Chromecast."
                        false -> "Điện thoại KHÔNG ở WiFi (đang 4G/VPN). " +
                            "Cast không dò được thiết bị — hãy bật WiFi cùng mạng với TV."
                        null -> "Đang kiểm tra kết nối mạng…"
                    },
                    fontSize = 11.sp,
                    color = if (onWifi == false) IOSRed else IOSGreen,
                )
                if (streamUrl != null) {
                    Spacer(Modifier.height(6.dp))
                    TextButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE)
                                as? android.content.ClipboardManager
                            clipboard?.setPrimaryClip(
                                android.content.ClipData.newPlainText("HLS", streamUrl),
                            )
                        },
                    ) { Text("Sao chép URL luồng HLS", fontSize = 12.sp) }
                }
                Spacer(Modifier.height(8.dp))
                if (routes.isEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("Đang tìm thiết bị…", fontSize = 12.sp, color = IOSSecondaryLabel)
                    }
                    Spacer(Modifier.height(6.dp))
                }
                if (routes.isEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Outlined.Tv,
                            contentDescription = null,
                            tint = IOSSecondaryLabel,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Chưa thấy thiết bị nào. Bật TV/Chromecast, đảm bảo điện thoại cùng WiFi và router cho phép mDNS (không bật AP isolation).",
                            fontSize = 12.sp,
                            color = IOSSecondaryLabel,
                        )
                    }
                }
                if (darboardConfigured) {
                    Spacer(Modifier.height(10.dp))
                    Text("Màn hình Darboard", color = IOSLabel, fontSize = 12.sp)
                    when {
                        panelError != null -> Text(
                            panelError!!,
                            color = IOSRed,
                            fontSize = 11.sp,
                        )
                        panels == null -> Text(
                            "Đang tải panel…",
                            fontSize = 11.sp,
                            color = IOSSecondaryLabel,
                        )
                        panels!!.isEmpty() -> Text(
                            "Không có panel nào đang kết nối (kiểm tra Darboard URL).",
                            fontSize = 11.sp,
                            color = IOSSecondaryLabel,
                        )
                        else -> panels!!.forEach { (id, name) ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        scope.launch {
                                            try {
                                                onCastPanel?.invoke(id)
                                                status = "Đã mở camera trên panel $name"
                                            } catch (e: Exception) {
                                                status = e.message ?: "Không gửi được lệnh"
                                            }
                                        }
                                    }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    Icons.Outlined.Tv,
                                    contentDescription = null,
                                    tint = IOSBlue,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(name, color = IOSLabel, fontSize = 14.sp)
                            }
                        }
                    }
                }

                if (haConfigured) {
                    Spacer(Modifier.height(10.dp))
                    Text("Qua Home Assistant", color = IOSLabel, fontSize = 12.sp)
                    when {
                        haError != null -> Text(haError!!, color = IOSRed, fontSize = 11.sp)
                        players == null -> Text(
                            "Đang tải media_player…",
                            fontSize = 11.sp,
                            color = IOSSecondaryLabel,
                        )
                        players!!.isEmpty() -> Text(
                            "Không có media_player khả dụng trong HA.",
                            fontSize = 11.sp,
                            color = IOSSecondaryLabel,
                        )
                        else -> players!!.forEach { (entityId, name) ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        scope.launch {
                                            try {
                                                onCastHa?.invoke(entityId)
                                                status = "Đã gửi lệnh phát tới $name"
                                            } catch (e: Exception) {
                                                status = e.message ?: "HA từ chối lệnh"
                                            }
                                        }
                                    }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    Icons.Outlined.Tv,
                                    contentDescription = null,
                                    tint = IOSGreen,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(name, color = IOSLabel, fontSize = 14.sp, maxLines = 1)
                                    Text(entityId, color = IOSSecondaryLabel, fontSize = 10.sp)
                                }
                            }
                        }
                    }
                }

                if (!darboardConfigured) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Chưa cấu hình Darboard server → Cài đặt → Phát lên màn hình khác.",
                        color = IOSSecondaryLabel,
                        fontSize = 11.sp,
                    )
                }
                if (!haConfigured) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Chưa cấu hình Home Assistant URL/token → Cài đặt → Phát lên màn hình khác.",
                        color = IOSSecondaryLabel,
                        fontSize = 11.sp,
                    )
                }

                status?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = IOSSecondaryLabel, fontSize = 11.sp)
                }

                routes.forEach { route ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                runCatching { router.selectRoute(route) }
                                onDismiss()
                            }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Outlined.Cast,
                            contentDescription = null,
                            tint = IOSBlue,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                route.name,
                                color = IOSLabel,
                                fontSize = 14.sp,
                                maxLines = 1,
                            )
                            route.description?.takeIf { it.isNotBlank() }?.let {
                                Text(it, color = IOSSecondaryLabel, fontSize = 11.sp, maxLines = 1)
                            }
                        }
                    }
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
