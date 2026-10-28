package com.minhquanghp86.frigatenvr.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import com.minhquanghp86.frigatenvr.data.MjpegSource
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
            is UiState.Ok -> CameraGrid(s.data, tick, onOpenCamera)
        }
    }
}

@Composable
private fun CameraGrid(config: FrigateConfig, tick: Int, onOpenCamera: (String) -> Unit) {
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
                onClick = { onOpenCamera(name) },
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface,
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

@Composable
fun CameraLiveScreen(
    modifier: Modifier = Modifier,
    camera: String,
    onBack: () -> Unit,
) {
    val repo = rememberRepo()
    var modeName by rememberSaveable { mutableStateOf(LiveMode.WEBRTC.name) }
    var fps by rememberSaveable { mutableIntStateOf(8) }
    var height by rememberSaveable { mutableIntStateOf(480) }
    var streamName by remember(camera) { mutableStateOf(camera) }
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var measuredFps by remember { mutableDoubleStateOf(0.0) }

    val mode = runCatching { LiveMode.valueOf(modeName) }.getOrDefault(LiveMode.MJPEG)

    // go2rtc stream name theo config (live.streams) — thường trùng tên camera.
    LaunchedEffect(camera) {
        val cfg = runCatching { repo.call { config() } }.getOrNull()
        streamName = cfg?.cameras?.get(camera)?.liveStreamNames?.firstOrNull() ?: camera
    }

    DisposableEffect(camera) {
        onDispose { bitmap = null }
    }

    LaunchedEffect(camera, mode, fps, height) {
        bitmap = null
        error = null
        measuredFps = 0.0
        if (mode != LiveMode.MJPEG) return@LaunchedEffect

        val source = MjpegSource(repo.okhttp(), repo.cameraMjpeg(camera, fps, height))
        var windowStart = System.currentTimeMillis()
        var frames = 0
        try {
            source.frames().collect { frame ->
                bitmap = frame
                frames++
                val now = System.currentTimeMillis()
                if (now - windowStart >= 1000) {
                    measuredFps = frames * 1000.0 / (now - windowStart)
                    frames = 0
                    windowStart = now
                }
            }
            if (error == null) error = "Luồng MJPEG đã đóng."
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.message ?: "Không mở được luồng MJPEG."
        }
    }

    val streamUrl = when (mode) {
        LiveMode.MJPEG, LiveMode.WEBRTC -> null
        LiveMode.HLS -> repo.go2rtcHls(streamName)
        LiveMode.MP4 -> repo.go2rtcMp4(streamName)
    }

    Column(modifier.fillMaxSize()) {
        ScreenHeader(
            title = camera,
            subtitle = when (mode) {
                LiveMode.WEBRTC -> "WebRTC (qua Frigate) · $streamName"
                LiveMode.MJPEG -> "MJPEG · ${height}p · ${fps} fps"
                LiveMode.HLS -> "HLS go2rtc · $streamName"
                LiveMode.MP4 -> "MP4 go2rtc · $streamName"
            },
            actions = {
                IconButton(onClick = onBack) {
                    Icon(Icons.Outlined.ArrowBack, contentDescription = "Quay lại")
                }
            },
        )

        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DropdownField(
                label = "Luồng",
                value = mode.label,
                options = LiveMode.values().map { it.name to it.label },
                onSelect = { modeName = it },
            )
            if (mode == LiveMode.MJPEG) {
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

        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(Color(0xFF000000)),
            contentAlignment = Alignment.Center,
        ) {
            if (mode == LiveMode.WEBRTC) {
                com.minhquanghp86.frigatenvr.ui.WebRtcLiveView(
                    endpoint = repo.webRtcEndpoint(streamName),
                    modifier = Modifier.fillMaxSize(),
                )
            } else if (streamUrl != null) {
                com.minhquanghp86.frigatenvr.ui.VideoPlayer(
                    url = streamUrl,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                val current = bitmap
                if (current != null) {
                    Image(
                        bitmap = current.asImageBitmap(),
                        contentDescription = "Camera $camera",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else if (error == null) {
                    CircularProgressIndicator()
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(16.dp),
                    ) {
                        Text(
                            error ?: "Không có dữ liệu",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = { modeName = LiveMode.HLS.name }) {
                            Text("Thử HLS (go2rtc)")
                        }
                    }
                }

                if (current != null) {
                    Row(
                        Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier
                                .background(Color(0xAA111111), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 3.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    Modifier
                                        .size(7.dp)
                                        .background(Color(0xFFF87171), RoundedCornerShape(4.dp)),
                                )
                                Spacer(Modifier.width(5.dp))
                                Text("LIVE", color = Color.White, fontSize = 11.sp)
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            String.format(Locale.getDefault(), "%.1f fps", measuredFps),
                            color = Color.White,
                            fontSize = 11.sp,
                            modifier = Modifier
                                .background(Color(0xAA111111), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 3.dp),
                        )
                    }
                }
            }
        }

        if (mode == LiveMode.MJPEG) {
            Text(
                "MJPEG lấy từ luồng detect của Frigate nên fps bị giới hạn theo luồng đó. " +
                    "Chọn HLS/MP4 (go2rtc) để xem fps gốc của camera.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}

private enum class LiveMode(val label: String) {
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
