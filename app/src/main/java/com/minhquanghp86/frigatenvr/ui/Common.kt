package com.minhquanghp86.frigatenvr.ui

import android.content.Context
import androidx.compose.foundation.background
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
    Row(
        modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        actions()
    }
}

/** Trình phát video (mp4/HLS) — tự giải phóng khi rời màn hình. */
@Composable
fun VideoPlayer(
    url: String,
    modifier: Modifier = Modifier,
    playWhenReady: Boolean = true,
    fit: Boolean = true,
) {
    val context = LocalContext.current
    val repo = rememberRepo()
    var error by remember(url) { mutableStateOf<String?>(null) }

    val player = remember(url) {
        val ok = repo.okhttp()
        val dataSourceFactory = OkHttpDataSource.Factory(ok)
        val mediaSource = if (url.contains(".m3u8")) {
            HlsMediaSource.Factory(dataSourceFactory)
                .createMediaSource(MediaItem.fromUri(url))
        } else {
            ProgressiveMediaSource.Factory(dataSourceFactory)
                .createMediaSource(MediaItem.fromUri(url))
        }
        ExoPlayer.Builder(context).build().apply {
            setMediaSource(mediaSource)
            prepare()
            this.playWhenReady = playWhenReady
            addListener(object : Player.Listener {
                override fun onPlayerError(e: PlaybackException) {
                    error = e.errorCodeName
                }
            })
        }
    }

    DisposableEffect(player) {
        onDispose { player.release() }
    }

    Box(modifier) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = player
                    useController = true
                    controllerShowTimeoutMs = 4000
                    resizeMode = if (fit) {
                        androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                    } else {
                        androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    }
                }
            },
            modifier = Modifier.fillMaxSize(),
            update = { view ->
                view.resizeMode = if (fit) {
                    androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                } else {
                    androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                }
            },
        )
        if (error != null) {
            Text(
                text = "Không phát được video ($error)",
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color(0xAA000000))
                    .padding(12.dp),
            )
        }
    }
}
