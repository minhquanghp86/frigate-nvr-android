package com.minhquanghp86.frigatenvr.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import kotlinx.coroutines.delay
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.first
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.ui.PlayerView
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.minhquanghp86.frigatenvr.FrigateApp
import com.minhquanghp86.frigatenvr.data.FrigateRepository
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Trạng thái tải dữ liệu dùng chung cho các màn hình. */
sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Error(val message: String) : UiState<Nothing>
    data class Ok<T>(val data: T) : UiState<T>
}

fun <T> Result<T>.uiMessage(): String =
    exceptionOrNull()?.message ?: "Đã xảy ra lỗi không rõ."

@Composable
fun rememberRepo(): FrigateRepository {
    val context = LocalContext.current.applicationContext as FrigateApp
    return context.container.repository
}

private val zone: ZoneId get() = ZoneId.systemDefault()
private val dtFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM HH:mm:ss", Locale.getDefault())
private val dateFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.getDefault())
private val timeFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())

fun fmtDateTime(epoch: Double): String =
    if (epoch <= 0) "—" else Instant.ofEpochMilli((epoch * 1000).toLong()).atZone(zone).format(dtFmt)

fun fmtDate(epoch: Double): String =
    if (epoch <= 0) "—" else Instant.ofEpochMilli((epoch * 1000).toLong()).atZone(zone).format(dateFmt)

fun fmtTime(epoch: Double): String =
    if (epoch <= 0) "—" else Instant.ofEpochMilli((epoch * 1000).toLong()).atZone(zone).format(timeFmt)

fun fmtDuration(seconds: Double): String {
    val total = seconds.toInt()
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}

fun pct(v: Double): String = "%.0f%%".format(v * 100)

/** Ảnh từ Frigate (cần JWT) — dùng OkHttpClient của app để kèm header Authorization. */
@Composable
fun FrigateImage(
    url: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    cacheKey: String? = null,
) {
    val repo = rememberRepo()
    val context = LocalContext.current
    val client = remember(repo.okhttp()) { repo.okhttp() }
    val imageLoader = remember(client) {
        ImageLoader.Builder(context)
            .okHttpClient(client)
            .crossfade(true)
            .build()
    }
    val request = ImageRequest.Builder(context)
        .data(url)
        .apply {
            if (cacheKey != null) {
                memoryCacheKey("$url|$cacheKey")
                diskCacheKey("$url|$cacheKey")
            }
        }
        .build()
    AsyncImage(
        model = request,
        contentDescription = null,
        contentScale = contentScale,
        modifier = modifier,
        imageLoader = imageLoader,
    )
}

@Composable
fun CenteredMessage(message: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = message,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(24.dp),
        )
    }
}

@Composable
fun LoadingBox(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

/** Thông báo lỗi có nút thử lại. */
@Composable
fun ErrorBox(message: String, onRetry: (() -> Unit)? = null, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = message,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
        )
        if (onRetry != null) {
            IconButton(onClick = onRetry) {
                Icon(Icons.Outlined.Refresh, contentDescription = "Thử lại")
            }
        }
    }
}

/** Header nhỏ cho các màn hình (không dùng TopAppBar của Scaffold). */
@Composable
fun ScreenHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actions: @Composable () -> Unit = {},
) {
    LargeTitleHeader(title = title, subtitle = subtitle, trailing = actions)
}

/** Trình phát video (mp4/HLS) với thanh điều khiển tự viết — tự co giãn theo bề ngang. */
@Composable
fun VideoPlayer(
    url: String,
    modifier: Modifier = Modifier,
    playWhenReady: Boolean = true,
    fit: Boolean = true,
    startPositionMs: Long = 0L,
    onFullscreen: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val repo = rememberRepo()
    var error by remember(url) { mutableStateOf<String?>(null) }
    var showControls by remember(url) { mutableStateOf(true) }
    var isPlaying by remember(url) { mutableStateOf(playWhenReady) }
    var positionMs by remember(url) { mutableLongStateOf(0L) }
    var durationMs by remember(url) { mutableLongStateOf(0L) }
    var dragging by remember(url) { mutableStateOf(false) }
    var dragValue by remember(url) { mutableFloatStateOf(0f) }

    var retry by remember(url) { mutableIntStateOf(0) }
    // Lỗi giải mã phần cứng -> thử lại bằng FFmpeg (phần mềm); lỗi mạng -> tự thử lại 1 lần.
    var softwareOnly by remember(url) { mutableStateOf(false) }
    var autoRetried by remember(url) { mutableStateOf(false) }
    val player = remember(url, retry, softwareOnly) {
        val ok = repo.okhttp()
        val dataSourceFactory = OkHttpDataSource.Factory(ok)
        val mediaSource = if (url.contains(".m3u8")) {
            HlsMediaSource.Factory(dataSourceFactory)
                .createMediaSource(MediaItem.fromUri(url))
        } else {
            ProgressiveMediaSource.Factory(dataSourceFactory)
                .createMediaSource(MediaItem.fromUri(url))
        }
        // Renderer: ưu tiên giải mã phần cứng, tự chuyển sang FFmpeg (phần mềm) khi codec
        // không được hỗ trợ (vd. H.265 của một số camera).
        val renderers = io.github.anilbeesetti.nextlib.media3ext.ffdecoder.NextRenderersFactory(context)
            .setExtensionRendererMode(
                if (softwareOnly) {
                    androidx.media3.exoplayer.DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER
                } else {
                    androidx.media3.exoplayer.DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON
                },
            )
            .setEnableDecoderFallback(true)
        ExoPlayer.Builder(context, renderers).build().apply {
            setMediaSource(mediaSource)
            prepare()
            this.playWhenReady = playWhenReady
            addListener(object : Player.Listener {
                override fun onPlayerError(e: PlaybackException) {
                    val code = e.errorCode
                    val decoderProblem = code == PlaybackException.ERROR_CODE_DECODER_INIT_FAILED ||
                        code == PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED
                    if (decoderProblem && !softwareOnly) {
                        // Phần cứng không nhận codec (H.265, hoặc hết slot decoder) -> chuyển FFmpeg.
                        softwareOnly = true
                        retry++
                        return
                    }
                    val transient = code == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
                        code == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ||
                        code == PlaybackException.ERROR_CODE_IO_UNSPECIFIED ||
                        code == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS
                    if (transient && !autoRetried) {
                        autoRetried = true
                        android.os.Handler(android.os.Looper.getMainLooper())
                            .postDelayed({ retry++ }, 900)
                        return
                    }
                    error = when {
                        decoderProblem ->
                            "Không khởi tạo được bộ giải mã video (đã thử cả phần mềm)."
                        code == PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED ->
                            "Định dạng video không được hỗ trợ."
                        transient -> "Lỗi mạng khi tải video (${e.errorCodeName})."
                        else -> e.errorCodeName
                    }
                }
            })
        }
    }

    // Nhảy tới đúng thời điểm (timeline: chọn 10:35 -> phát từ phút 35).
    LaunchedEffect(player, startPositionMs) {
        if (startPositionMs <= 0) return@LaunchedEffect
        androidx.compose.runtime.snapshotFlow { player.playbackState }
            .first { it == Player.STATE_READY }
        player.seekTo(startPositionMs)
    }

    LaunchedEffect(player) {
        while (true) {
            if (!dragging) {
                positionMs = player.currentPosition.coerceAtLeast(0L)
                durationMs = player.duration.coerceAtLeast(0L)
            }
            isPlaying = player.isPlaying
            delay(350)
        }
    }

    // Đang phát thì tự ẩn thanh điều khiển (nút toàn màn hình cũng ẩn theo).
    LaunchedEffect(showControls, isPlaying, dragging) {
        if (showControls && isPlaying && !dragging) {
            delay(4_000)
            showControls = false
        }
    }

    DisposableEffect(player) {
        onDispose { player.release() }
    }

    Box(modifier) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply { useController = false }
            },
            modifier = Modifier.fillMaxSize(),
            update = { view ->
                if (view.player !== player) view.player = player
                view.resizeMode = if (fit) {
                    androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                } else {
                    androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                }
            },
        )

        // Chạm vào video để hiện/ẩn thanh điều khiển.
        Box(
            Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { showControls = !showControls },
        )

        if (showControls) {
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color(0xB3000000))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        formatClock(
                            if (dragging) (dragValue * durationMs).toLong() else positionMs,
                        ),
                        color = Color.White,
                        fontSize = 11.sp,
                    )
                    Slider(
                        value = when {
                            durationMs <= 0 -> 0f
                            dragging -> dragValue
                            else -> (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
                        },
                        onValueChange = { v ->
                            dragging = true
                            dragValue = v
                        },
                        onValueChangeFinished = {
                            if (durationMs > 0) player.seekTo((dragValue * durationMs).toLong())
                            dragging = false
                        },
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 6.dp),
                        colors = SliderDefaults.colors(
                            thumbColor = IOSBlue,
                            activeTrackColor = IOSBlue,
                            inactiveTrackColor = Color(0x66FFFFFF),
                        ),
                    )
                    Text(
                        formatClock(durationMs),
                        color = Color.White,
                        fontSize = 11.sp,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = {
                            if (player.isPlaying) player.pause() else player.play()
                            showControls = true
                        },
                    ) {
                        Icon(
                            if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = if (isPlaying) "Tạm dừng" else "Phát",
                            tint = Color.White,
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    if (onFullscreen != null) {
                        IconButton(onClick = onFullscreen) {
                            Icon(
                                Icons.Outlined.Fullscreen,
                                contentDescription = "Toàn màn hình",
                                tint = Color.White,
                            )
                        }
                    }
                }
            }
        }

        if (error != null) {
            Column(
                Modifier
                    .align(Alignment.Center)
                    .background(Color(0xAA000000), RoundedCornerShape(8.dp))
                    .padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "Không phát được video\n$error",
                    color = Color.White,
                    textAlign = TextAlign.Center,
                )
                androidx.compose.foundation.layout.Spacer(Modifier.size(8.dp))
                androidx.compose.material3.OutlinedButton(
                    onClick = {
                        error = null
                        autoRetried = false
                        retry++
                    },
                ) { Text("Thử lại", color = Color.White) }
            }
        }
    }
}

private fun formatClock(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}


fun relTime(epoch: Double): String {
    if (epoch <= 0) return "—"
    val diff = (System.currentTimeMillis() / 1000.0 - epoch).toLong().coerceAtLeast(0)
    return when {
        diff < 60 -> "vừa xong"
        diff < 3600 -> "${diff / 60} phút trước"
        diff < 86400 -> "${diff / 3600} giờ trước"
        else -> "${diff / 86400} ngày trước"
    }
}
