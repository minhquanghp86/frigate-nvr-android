package com.minhquanghp86.frigatenvr.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material.icons.outlined.CropFree
import androidx.compose.material.icons.outlined.FitScreen
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.FullscreenExit
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.VolumeOff
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.key
import androidx.compose.foundation.background
import androidx.compose.ui.platform.LocalContext
import com.minhquanghp86.frigatenvr.ui.IOSCardHigh
import com.minhquanghp86.frigatenvr.ui.fmtDateTime
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.ui.platform.LocalView
import com.minhquanghp86.frigatenvr.ui.IOSGreen
import com.minhquanghp86.frigatenvr.ui.IOSGroupedBackground
import com.minhquanghp86.frigatenvr.ui.IOSLabel
import com.minhquanghp86.frigatenvr.ui.IOSTertiaryLabel
import com.minhquanghp86.frigatenvr.ui.Pill
import androidx.compose.material.icons.outlined.PictureInPictureAlt
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.minhquanghp86.frigatenvr.data.FrigateConfig
import com.minhquanghp86.frigatenvr.data.FrigateEvent
import com.minhquanghp86.frigatenvr.ui.relTime
import com.minhquanghp86.frigatenvr.ui.IOSSecondaryLabel
import com.minhquanghp86.frigatenvr.data.MjpegSource
import com.minhquanghp86.frigatenvr.ui.IOSCard
import com.minhquanghp86.frigatenvr.ui.CamerasViewModel
import com.minhquanghp86.frigatenvr.ui.CenteredMessage
import com.minhquanghp86.frigatenvr.ui.ErrorBox
import com.minhquanghp86.frigatenvr.ui.FrigateImage
import com.minhquanghp86.frigatenvr.ui.LoadingBox
import com.minhquanghp86.frigatenvr.ui.ScreenHeader
import com.minhquanghp86.frigatenvr.ui.UiState
import com.minhquanghp86.frigatenvr.ui.rememberRepo
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.withContext
import okhttp3.Request

private val FPS_OPTIONS = listOf(1, 2, 3, 5, 8, 10, 15)
private val HEIGHT_OPTIONS = listOf(240, 360, 480, 720, 1080)

@Composable
fun CamerasScreen(
    modifier: Modifier = Modifier,
    onOpenCamera: (String) -> Unit,
) {
    val vm: CamerasViewModel = viewModel()
    val state by vm.state.collectAsState()
    val latest by vm.latest.collectAsState()
    var tick by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(5_000)
            tick++
        }
    }

    Column(modifier.fillMaxSize()) {
        ScreenHeader(
            title = "Camera",
            subtitle = when (val s = state) {
                is UiState.Ok -> "${s.data.enabledCameras.size} camera đã bật"
                UiState.Loading -> "Đang tải..."
                is UiState.Error -> "Lỗi tải cấu hình"
            },
            actions = {
                IconButton(onClick = { vm.refresh() }) {
                    Icon(Icons.Outlined.Refresh, contentDescription = "Tải lại")
                }
            },
        )

        when (val s = state) {
            UiState.Loading -> LoadingBox()
            is UiState.Error -> ErrorBox(s.message, onRetry = { vm.refresh() })
            is UiState.Ok -> CameraGrid(s.data, tick, latest, onOpenCamera)
        }
    }
}

@Composable
private fun CameraGrid(
    config: FrigateConfig,
    tick: Int,
    latest: Map<String, FrigateEvent>,
    onOpenCamera: (String) -> Unit,
) {
    val repo = rememberRepo()
    val cameras = config.enabledCameras

    if (cameras.isEmpty()) {
        CenteredMessage("Không có camera nào được bật trong cấu hình Frigate.")
        return
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 156.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(cameras, key = { it }) { name ->
            val cam = config.cameras[name]
            Card(
                onClick = {
                    runCatching { repo.warmStream(name) }
                    onOpenCamera(name)
                },
                colors = CardDefaults.cardColors(
                    containerColor = IOSCard,
                ),
                shape = RoundedCornerShape(10.dp),
            ) {
                Column {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                            .background(Color(0xFF000000)),
                    ) {
                        FrigateImage(
                            url = repo.cameraLatest(name),
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                            cacheKey = tick.toString(),
                        )
                        if (cam?.recording == true) {
                            Row(
                                Modifier
                                    .align(Alignment.TopStart)
                                    .padding(6.dp)
                                    .background(
                                        Color(0xAA111111),
                                        RoundedCornerShape(4.dp),
                                    )
                                    .padding(horizontal = 5.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    Modifier
                                        .size(6.dp)
                                        .background(Color(0xFFF87171), RoundedCornerShape(3.dp)),
                                )
                                Spacer(Modifier.width(4.dp))
                                Text("REC", color = Color.White, fontSize = 9.sp)
                            }
                        }
                    }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            name,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                        )
                        if (latest[name] != null) {
                            Text(
                                "${latest[name]!!.label} · ${relTime(latest[name]!!.startTime)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = IOSSecondaryLabel,
                                maxLines = 1,
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }
                        if (cam?.hasAudio == true) {
                            Icon(
                                Icons.Outlined.MusicNote,
                                contentDescription = "Có audio",
                                modifier = Modifier.size(15.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraLiveScreen(
    modifier: Modifier = Modifier,
    camera: String,
    onBack: () -> Unit,
    onOpenEvent: (String) -> Unit = {},
    onOpenTimeline: (String) -> Unit = {},
) {
    val repo = rememberRepo()
    val context = LocalContext.current
    val activity = context as? android.app.Activity
    val rootView = LocalView.current
    val scope = rememberCoroutineScope()
    val isPip by com.minhquanghp86.frigatenvr.PipState.visible.collectAsState()
    val notifySet by repo.notifyCameras.collectAsState()
    val dataVersion by repo.dataVersion.collectAsState()

    var modeName by rememberSaveable { mutableStateOf(LiveMode.AUTO.name) }
    var fps by rememberSaveable { mutableIntStateOf(8) }
    var height by rememberSaveable { mutableIntStateOf(480) }
    var fit by rememberSaveable { mutableStateOf(true) }
    var muted by rememberSaveable { mutableStateOf(true) }
    var showStats by rememberSaveable { mutableStateOf(false) }
    var chromeVisible by rememberSaveable { mutableStateOf(true) }
    var immersive by rememberSaveable { mutableStateOf(false) }
    var panel by rememberSaveable { mutableIntStateOf(0) }
    var showSettings by remember { mutableStateOf(false) }
    var reloadKey by remember { mutableIntStateOf(0) }
    var streamName by remember(camera) { mutableStateOf(camera) }
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var bitmapSize by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var measuredFps by remember { mutableDoubleStateOf(0.0) }
    var webrtcReady by remember(camera) { mutableStateOf(false) }
    var events by remember(camera) { mutableStateOf<List<FrigateEvent>>(emptyList()) }
    var onEnabled by remember(camera) { mutableStateOf(true) }
    var onDetect by remember(camera) { mutableStateOf(true) }
    var onRecord by remember(camera) { mutableStateOf(false) }
    var onSnapshot by remember(camera) { mutableStateOf(false) }
    var featureError by remember { mutableStateOf<String?>(null) }
    var eventsLoading by remember { mutableStateOf(false) }
    var allCameras by remember { mutableStateOf<List<String>>(emptyList()) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val mode = runCatching { LiveMode.valueOf(modeName) }.getOrDefault(LiveMode.AUTO)
    val notifyOnForCamera = notifySet.isEmpty() || camera in notifySet

    fun applyFullscreen(on: Boolean) {
        immersive = on
        chromeVisible = true
        panel = if (on) 0 else panel
        val act = activity ?: return
        act.requestedOrientation = if (on) {
            android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
        val controller = androidx.core.view.WindowCompat.getInsetsController(act.window, rootView)
        if (on) {
            controller.systemBarsBehavior =
                androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        } else {
            controller.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            activity?.requestedOrientation =
                android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    androidx.activity.compose.BackHandler(enabled = immersive) { applyFullscreen(false) }

    val notifyPermLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            scope.launch {
                androidx.core.content.ContextCompat.startForegroundService(
                    context,
                    android.content.Intent(
                        context,
                        com.minhquanghp86.frigatenvr.notify.EventWatchService::class.java,
                    ),
                )
                repo.setNotifyEnabled(true)
            }
        }
    }

    fun setCameraNotify(on: Boolean) {
        scope.launch {
            val newSet: Set<String> = when {
                on -> if (notifySet.isEmpty()) emptySet() else notifySet + camera
                else -> if (notifySet.isEmpty()) allCameras.toSet() - camera else notifySet - camera
            }
            repo.setNotifyCameras(newSet)
            if (on) {
                val needPerm = android.os.Build.VERSION.SDK_INT >= 33 &&
                    androidx.core.content.ContextCompat.checkSelfPermission(
                        context,
                        android.Manifest.permission.POST_NOTIFICATIONS,
                    ) != android.content.pm.PackageManager.PERMISSION_GRANTED
                if (needPerm) {
                    notifyPermLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    androidx.core.content.ContextCompat.startForegroundService(
                        context,
                        android.content.Intent(
                            context,
                            com.minhquanghp86.frigatenvr.notify.EventWatchService::class.java,
                        ),
                    )
                    repo.setNotifyEnabled(true)
                }
            }
        }
    }

    LaunchedEffect(camera) {
        runCatching { repo.warmStream(camera) }
        val cfg = runCatching { repo.configCached() }.getOrNull()
        val cam = cfg?.cameras?.get(camera)
        streamName = cam?.liveStreamNames?.firstOrNull() ?: camera
        allCameras = cfg?.enabledCameras.orEmpty()
        onEnabled = cam?.enabled ?: true
        onDetect = cam?.detectOn ?: true
        onRecord = cam?.recording ?: false
        onSnapshot = cam?.snapshotsOn ?: false
    }

    LaunchedEffect(panel, camera, dataVersion) {
        if (panel != 1) return@LaunchedEffect
        eventsLoading = true
        events = runCatching {
            repo.call {
                events(
                    mapOf(
                        "cameras" to camera,
                        "limit" to "30",
                        "include_thumbnails" to "0",
                    ),
                )
            }
        }.getOrDefault(emptyList())
        eventsLoading = false
    }

    LaunchedEffect(camera, mode, fps, height, reloadKey, webrtcReady) {
        bitmap = null
        error = null
        measuredFps = 0.0
        if (mode != LiveMode.MJPEG && mode != LiveMode.AUTO) return@LaunchedEffect
        if (mode == LiveMode.AUTO && webrtcReady) return@LaunchedEffect

        val source = MjpegSource(repo.okhttp(), repo.cameraMjpeg(camera, fps, height))
        var windowStart = System.currentTimeMillis()
        var frames = 0
        try {
            source.frames().collect { frame ->
                bitmap = frame
                if (bitmapSize.isEmpty()) bitmapSize = "${frame.width}x${frame.height}"
                frames++
                val now = System.currentTimeMillis()
                if (now - windowStart >= 1000) {
                    measuredFps = frames * 1000.0 / (now - windowStart)
                    frames = 0
                    windowStart = now
                }
            }
            if (error == null && mode == LiveMode.MJPEG) error = "Luồng MJPEG đã đóng."
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (mode == LiveMode.MJPEG) error = e.message ?: "Không mở được luồng MJPEG."
        }
    }

    fun toggleFeature(feature: String, value: Boolean, revert: () -> Unit) {
        scope.launch {
            try {
                repo.setCameraFeature(camera, feature, value)
                featureError = null
            } catch (e: Exception) {
                revert()
                featureError = e.message ?: "Không đổi được $feature"
            }
        }
    }

    val streamUrl = when (mode) {
        LiveMode.MJPEG, LiveMode.WEBRTC, LiveMode.AUTO -> null
        LiveMode.HLS -> repo.go2rtcHls(streamName)
        LiveMode.MP4 -> repo.go2rtcMp4(streamName)
    }

    Column(modifier.fillMaxSize().background(Color.Black)) {
        Box(
            Modifier
                .then(if (panel == 1) Modifier.weight(0.45f) else Modifier.weight(1f))
                .fillMaxWidth(),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {
                        // Ở chế độ ngang, chạm chỉ ẩn/hiện nút; thoát bằng nút Toàn màn hình.
                        chromeVisible = !chromeVisible
                    },
                contentAlignment = Alignment.Center,
            ) {
                when {
                    mode == LiveMode.AUTO -> key(reloadKey) {
                        Box(Modifier.fillMaxSize()) {
                            if (!webrtcReady) {
                                val current = bitmap
                                if (current != null) {
                                    Image(
                                        bitmap = current.asImageBitmap(),
                                        contentDescription = "Camera $camera",
                                        contentScale = if (fit) ContentScale.Fit else ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                } else {
                                    FrigateImage(
                                        url = repo.cameraLatest(camera),
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = if (fit) ContentScale.Fit else ContentScale.Crop,
                                    )
                                }
                            }
                            com.minhquanghp86.frigatenvr.ui.WebRtcLiveView(
                                endpoint = repo.webRtcEndpoint(camera),
                                modifier = if (webrtcReady) Modifier.fillMaxSize() else Modifier.size(1.dp),
                                fit = fit,
                                muted = muted,
                                showStats = showStats,
                                onFirstFrame = { webrtcReady = true },
                            )
                        }
                    }

                    mode == LiveMode.WEBRTC -> key(reloadKey) {
                        com.minhquanghp86.frigatenvr.ui.WebRtcLiveView(
                            endpoint = repo.webRtcEndpoint(camera),
                            modifier = Modifier.fillMaxSize(),
                            fit = fit,
                            muted = muted,
                            showStats = showStats,
                        )
                    }

                    streamUrl != null -> key(reloadKey) {
                        com.minhquanghp86.frigatenvr.ui.VideoPlayer(
                            url = streamUrl,
                            modifier = Modifier.fillMaxSize(),
                            fit = fit,
                        )
                    }

                    else -> {
                        val current = bitmap
                        if (current != null) {
                            Image(
                                bitmap = current.asImageBitmap(),
                                contentDescription = "Camera $camera",
                                contentScale = if (fit) ContentScale.Fit else ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else if (error == null) {
                            FrigateImage(
                                url = repo.cameraLatest(camera),
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit,
                            )
                        }
                    }
                }

                if (error != null && mode == LiveMode.MJPEG) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(16.dp),
                    ) {
                        Text(
                            error ?: "",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = { modeName = LiveMode.WEBRTC.name }) {
                            Text("Thử WebRTC")
                        }
                    }
                }

                if (showStats && bitmap != null && (mode == LiveMode.MJPEG || mode == LiveMode.AUTO)) {
                    Text(
                        buildString {
                            append(String.format(Locale.getDefault(), "%.1f fps", measuredFps))
                            if (bitmapSize.isNotEmpty()) append(" · $bitmapSize")
                        },
                        color = Color.White,
                        fontSize = 11.sp,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(8.dp)
                            .background(Color(0xAA111111), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 3.dp),
                    )
                }
            }

            if (chromeVisible && !isPip) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    LiveIconButton(Icons.Outlined.ArrowBack, "Quay lại", onBack)
                    Spacer(Modifier.weight(1f))
                    LiveIconButton(Icons.Outlined.PictureInPictureAlt, "Hình trong hình") {
                        if (android.os.Build.VERSION.SDK_INT >= 26 && activity != null) {
                            runCatching {
                                activity.enterPictureInPictureMode(
                                    android.app.PictureInPictureParams.Builder()
                                        .setAspectRatio(android.util.Rational(16, 9))
                                        .build(),
                                )
                            }
                        }
                    }
                    LiveIconButton(
                        if (immersive) Icons.Outlined.FullscreenExit else Icons.Outlined.Fullscreen,
                        "Toàn màn hình",
                    ) { applyFullscreen(!immersive) }
                    LiveIconButton(Icons.Outlined.Settings, "Cài đặt") { showSettings = true }
                }
            }
        }

        if (!isPip && !immersive) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Pill("Sự kiện", panel == 1, onClick = { panel = if (panel == 1) 0 else 1 })
                Pill("Xem lại", false, onClick = { onOpenTimeline(camera) })
                Spacer(Modifier.weight(1f))
                Text(
                    "Thông báo",
                    style = MaterialTheme.typography.labelMedium,
                    color = IOSSecondaryLabel,
                )
                Switch(
                    checked = notifyOnForCamera,
                    onCheckedChange = { setCameraNotify(it) },
                    colors = androidx.compose.material3.SwitchDefaults.colors(
                        checkedTrackColor = IOSGreen,
                        checkedThumbColor = Color.White,
                        uncheckedTrackColor = IOSCardHigh,
                        uncheckedThumbColor = Color.White,
                    ),
                )
            }
        }

        if (panel == 1 && !isPip && !immersive) {
            if (eventsLoading && events.isEmpty()) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .weight(0.55f),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }
            } else if (events.isEmpty()) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .weight(0.55f),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Chưa có sự kiện cho camera này.", color = IOSSecondaryLabel)
                }
            } else {
                LazyColumn(
                    Modifier
                        .fillMaxWidth()
                        .weight(0.55f)
                        .background(IOSGroupedBackground),
                ) {
                    items(events, key = { it.id }) { e ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onOpenEvent(e.id) }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                Modifier
                                    .size(width = 96.dp, height = 54.dp)
                                    .background(Color(0xFF000000), RoundedCornerShape(6.dp)),
                            ) {
                                if (e.hasSnapshot) {
                                    FrigateImage(
                                        url = repo.eventThumbnail(e.id),
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop,
                                    )
                                }
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    e.label + (e.subLabelDisplay?.let { " · $it" } ?: ""),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = IOSLabel,
                                    maxLines = 1,
                                )
                                Text(
                                    "${relTime(e.startTime)} · ${fmtDateTime(e.startTime)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = IOSSecondaryLabel,
                                )
                            }
                            Icon(
                                Icons.Outlined.ChevronRight,
                                contentDescription = null,
                                tint = IOSTertiaryLabel,
                            )
                        }
                    }
                }
            }
        }
    }

    if (showSettings) {
        ModalBottomSheet(
            onDismissRequest = { showSettings = false },
            sheetState = sheetState,
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Camera $camera", style = MaterialTheme.typography.titleMedium)

                ToggleRow("Camera", onEnabled) { v ->
                    onEnabled = v
                    toggleFeature("enabled", v) { onEnabled = !v }
                }
                ToggleRow("Phát hiện đối tượng", onDetect) { v ->
                    onDetect = v
                    toggleFeature("detect", v) { onDetect = !v }
                }
                ToggleRow("Ghi hình", onRecord) { v ->
                    onRecord = v
                    toggleFeature("recordings", v) { onRecord = !v }
                }
                ToggleRow("Ảnh chụp", onSnapshot) { v ->
                    onSnapshot = v
                    toggleFeature("snapshots", v) { onSnapshot = !v }
                }
                featureError?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }
                androidx.compose.material3.HorizontalDivider()

                DropdownField(
                    label = "Luồng",
                    value = mode.label,
                    options = LiveMode.values().map { it.name to it.label },
                    onSelect = { modeName = it },
                )
                if (mode == LiveMode.MJPEG) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DropdownField(
                            label = "FPS",
                            value = "$fps",
                            options = FPS_OPTIONS.map { it.toString() to "$it fps" },
                            onSelect = { fps = it.toIntOrNull() ?: fps },
                        )
                        DropdownField(
                            label = "Chất lượng",
                            value = "${height}p",
                            options = HEIGHT_OPTIONS.map { it.toString() to "${it}p" },
                            onSelect = { height = it.toIntOrNull() ?: height },
                        )
                    }
                }
                ToggleRow("Hiện thống kê", showStats) { showStats = it }
                if (mode == LiveMode.WEBRTC || mode == LiveMode.AUTO) {
                    ToggleRow("Tắt tiếng", muted) { muted = it }
                }
                ToggleRow("Vừa khung (fit)", fit) { fit = it }

                OutlinedButton(
                    onClick = {
                        reloadKey++
                        showSettings = false
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Kết nối lại")
                }
            }
        }
    }
}

@Composable
private fun LiveIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(40.dp)
            .background(Color(0x99202429), RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center,
    ) {
        IconButton(onClick = onClick) {
            Icon(icon, contentDescription = label, tint = Color.White)
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

private enum class LiveMode(val label: String) {
    AUTO("Tự động (ảnh → WebRTC)"),
    WEBRTC("WebRTC (Frigate)"),
    MP4("MP4 (go2rtc)"),
    HLS("HLS (go2rtc)"),
    MJPEG("MJPEG (Frigate)"),
}

@Composable
private fun DropdownField(
    label: String,
    value: String,
    options: List<Pair<String, String>>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        AssistChip(
            onClick = { open = true },
            label = { Text("$label: $value", fontSize = 12.sp) },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { (key, text) ->
                DropdownMenuItem(
                    text = { Text(text) },
                    onClick = {
                        open = false
                        onSelect(key)
                    },
                )
            }
        }
    }
}
