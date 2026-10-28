package com.minhquanghp86.frigatenvr.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.minhquanghp86.frigatenvr.data.MediaSaver
import com.minhquanghp86.frigatenvr.ui.ErrorBox
import com.minhquanghp86.frigatenvr.ui.IOSBlue
import com.minhquanghp86.frigatenvr.ui.IOSCard
import com.minhquanghp86.frigatenvr.ui.IOSGreen
import com.minhquanghp86.frigatenvr.ui.IOSLabel
import com.minhquanghp86.frigatenvr.ui.IOSOrange
import com.minhquanghp86.frigatenvr.ui.IOSRed
import com.minhquanghp86.frigatenvr.ui.IOSSecondaryLabel
import com.minhquanghp86.frigatenvr.ui.IosDropdown
import com.minhquanghp86.frigatenvr.ui.IosGroup
import com.minhquanghp86.frigatenvr.ui.LargeTitleHeader
import com.minhquanghp86.frigatenvr.ui.Pill
import com.minhquanghp86.frigatenvr.ui.SectionTitle
import com.minhquanghp86.frigatenvr.ui.TimelineViewModel
import com.minhquanghp86.frigatenvr.ui.VideoPlayer
import com.minhquanghp86.frigatenvr.ui.rememberRepo
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dayFmt = DateTimeFormatter.ofPattern("EEEE, dd/MM/yyyy", Locale.getDefault())
private const val HOUR_DP = 88f

@Composable
fun TimelineScreen(
    modifier: Modifier = Modifier,
    initialCamera: String? = null,
    showBack: Boolean = false,
    onBack: () -> Unit = {},
    onFullscreen: (String, Long) -> Unit = { _, _ -> },
) {
    val vm: TimelineViewModel = viewModel()
    val cameras by vm.cameras.collectAsState()
    val camera by vm.camera.collectAsState()
    val days by vm.days.collectAsState()
    val hours by vm.hours.collectAsState()
    val dayEvents by vm.dayEvents.collectAsState()
    val loading by vm.loading.collectAsState()
    val error by vm.error.collectAsState()
    val playing by vm.playing.collectAsState()

    val repo = rememberRepo()
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val stripScroll = rememberScrollState()

    var selectedDay by rememberSaveable { mutableStateOf<String?>(null) }
    var minutes by rememberSaveable { mutableFloatStateOf(12f * 60f) }
    var startPosMs by rememberSaveable { mutableLongStateOf(0L) }
    var saving by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(cameras, initialCamera) {
        if (initialCamera != null && cameras.contains(initialCamera) && camera != initialCamera) {
            vm.setCamera(initialCamera)
        }
    }

    LaunchedEffect(days) {
        if (selectedDay == null || selectedDay !in days) selectedDay = days.firstOrNull()
    }

    LaunchedEffect(selectedDay, camera) {
        vm.stop()
        val day = selectedDay ?: return@LaunchedEffect
        vm.loadDayEvents(day)
        val firstHour = hours.firstOrNull { it.day == day }?.hours?.firstOrNull()?.hour?.toIntOrNull()
        minutes = ((firstHour ?: 0) * 60 + 5).toFloat().coerceIn(0f, 1439f)
    }

    fun playAt(value: Float) {
        val day = selectedDay ?: return
        val total = value.toInt().coerceIn(0, 1439)
        val hour = total / 60
        startPosMs = ((total % 60) * 60L) * 1000L
        vm.play(day, hour.toString())
    }

    val notifyPermLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
    ) { }

    fun ensureNotifyPermission() {
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            val granted = androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS,
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notifyPermLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    fun downloadAt(value: Float) {
        val day = selectedDay ?: return
        val cam = camera ?: return
        val hour = value.toInt().coerceIn(0, 1439) / 60
        scope.launch {
            saving = true
            message = "Đang tải…"
            ensureNotifyPermission()
            val start = ZonedDateTime.of(
                LocalDate.parse(day),
                LocalTime.of(hour, 0),
                ZoneId.systemDefault(),
            ).toEpochSecond()
            val saved = MediaSaver.save(
                context = context,
                client = repo.okhttp(),
                url = repo.clipRange(cam, start, start + 3600),
                fileName = "frigate-$cam-$day-$hour.mp4",
                mime = "video/mp4",
                onProgress = { percent ->
                    message = if (percent == null) "Đang tải…" else "Đang tải… $percent%"
                },
            )
            message = saved?.let { "Đã lưu: $it" } ?: "Không tải được khung giờ này"
            saving = false
        }
    }

    Column(modifier.fillMaxSize()) {
        LargeTitleHeader(
            title = "Xem lại",
            subtitle = camera?.let { "Ghi hình · $it" } ?: "Ghi hình",
            trailing = {
                if (showBack) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = "Quay lại", tint = IOSLabel)
                    }
                } else {
                    IconButton(onClick = { vm.refresh() }) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "Tải lại", tint = IOSBlue)
                    }
                }
            },
        )

        if (!showBack && cameras.size > 1) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(cameras) { name ->
                    Pill(name, name == camera, onClick = { vm.setCamera(name) })
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        when {
            loading && days.isEmpty() -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                CircularProgressIndicator()
            }

            error != null && days.isEmpty() -> ErrorBox(error!!, onRetry = { vm.refresh() })

            days.isEmpty() -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text("Frigate chưa có bản ghi nào.", color = IOSSecondaryLabel)
            }

            else -> {
                SectionTitle("Ngày ghi hình")
                IosGroup {
                    val dayValue = selectedDay?.let {
                        runCatching { LocalDate.parse(it).format(dayFmt) }.getOrDefault(it)
                    } ?: "—"
                    IosDropdown(
                        label = "Ngày",
                        value = dayValue,
                        options = days.map { day ->
                            day to runCatching {
                                LocalDate.parse(day).format(dayFmt)
                            }.getOrDefault(day)
                        },
                        onSelect = { selectedDay = it },
                    )
                }

                Spacer(Modifier.height(10.dp))

                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .aspectRatio(16f / 9f)
                        .background(Color.Black),
                    contentAlignment = Alignment.Center,
                ) {
                    val stream = playing
                    if (stream != null) {
                        VideoPlayer(
                            url = stream,
                            startPositionMs = startPosMs,
                            modifier = Modifier.fillMaxSize(),
                            onFullscreen = { onFullscreen(stream, startPosMs) },
                        )
                    } else {
                        Text(
                            "Chạm vào timeline bên dưới để xem",
                            color = IOSSecondaryLabel,
                            fontSize = 13.sp,
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))

                // Timeline cuộn kiểu Frigate: vạch giờ, vùng có ghi hình, sự kiện tô màu.
                val day = selectedDay
                val hoursForDay = day?.let { d -> hours.firstOrNull { it.day == d }?.hours.orEmpty() }
                    .orEmpty()
                val eventsForDay = dayEvents
                val dayStartEpoch = day?.let {
                    ZonedDateTime.of(LocalDate.parse(it), LocalTime.MIDNIGHT, ZoneId.systemDefault())
                        .toEpochSecond()
                } ?: 0L
                val textMeasurer = rememberTextMeasurer()

                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .background(IOSCard, RoundedCornerShape(12.dp))
                        .horizontalScroll(stripScroll),
                ) {
                    Canvas(
                        Modifier
                            .width((24 * HOUR_DP).dp)
                            .height(104.dp)
                            .pointerInput(day) {
                                awaitEachGesture {
                                    val down = awaitFirstDown(requireUnconsumed = false)
                                    val width = size.width.toFloat()
                                    val handleX = (minutes / 1440f) * width
                                    val onHandle =
                                        kotlin.math.abs(down.position.x - handleX) <= 28.dp.toPx()
                                    if (onHandle) {
                                        // Kéo riêng thanh thời gian; dải đứng im (consume sự kiện).
                                        var changed = false
                                        while (true) {
                                            val event = awaitPointerEvent()
                                            val change = event.changes
                                                .firstOrNull { it.id == down.id } ?: break
                                            if (!change.pressed) break
                                            val dx = change.position.x - change.previousPosition.x
                                            if (dx != 0f) {
                                                minutes = (minutes + dx / width * 1440f)
                                                    .coerceIn(0f, 1439f)
                                                changed = true
                                                change.consume()
                                            }
                                        }
                                        if (changed) playAt(minutes)
                                    } else {
                                        // Không chạm thanh: nếu là tap thì chọn thời điểm,
                                        // nếu là kéo thì nhường cho dải cuộn.
                                        var moved = false
                                        while (true) {
                                            val event = awaitPointerEvent()
                                            val change = event.changes
                                                .firstOrNull { it.id == down.id } ?: break
                                            if (!change.pressed) break
                                            if (change.isConsumed) {
                                                moved = true
                                                break
                                            }
                                            if ((change.position - down.position).getDistance() >
                                                viewConfiguration.touchSlop
                                            ) {
                                                moved = true
                                                break
                                            }
                                        }
                                        if (!moved) {
                                            minutes = ((down.position.x / width) * 1440f)
                                                .coerceIn(0f, 1439f)
                                            playAt(minutes)
                                        }
                                    }
                                }
                            },
                    ) {
                        val w = size.width
                        val h = size.height
                        val pxPerMin = w / 1440f
                        val top = 26f
                        val bottom = h - 12f

                        // vùng có ghi hình
                        for (rec in hoursForDay) {
                            val hour = rec.hour.toIntOrNull() ?: continue
                            val x = hour * 60 * pxPerMin
                            val frac = (rec.duration.toFloat() / 3600f).coerceIn(0.03f, 1f)
                            drawRoundRect(
                                color = IOSGreen.copy(alpha = 0.22f),
                                topLeft = Offset(x, top),
                                size = Size(60 * pxPerMin * frac, bottom - top),
                                cornerRadius = CornerRadius(3f, 3f),
                            )
                        }

                        // vạch giờ + nhãn mỗi 2 giờ
                        for (hour in 0..24) {
                            val x = hour * 60 * pxPerMin
                            drawLine(
                                color = Color(0x22FFFFFF),
                                start = Offset(x, top),
                                end = Offset(x, bottom),
                                strokeWidth = 1f,
                            )
                            if (hour % 2 == 0 && hour < 24) {
                                drawText(
                                    textMeasurer = textMeasurer,
                                    text = "%02d:00".format(hour),
                                    topLeft = Offset(x + 3f, 4f),
                                    style = TextStyle(color = IOSSecondaryLabel, fontSize = 9.sp),
                                )
                            }
                        }

                        // sự kiện: vạch màu theo nhãn
                        for (e in eventsForDay) {
                            val minuteOfDay = ((e.startTime - dayStartEpoch) / 60.0).toFloat()
                            if (minuteOfDay < 0f || minuteOfDay > 1440f) continue
                            val x = minuteOfDay * pxPerMin
                            val color = when (e.label) {
                                "person" -> IOSBlue
                                "car", "truck", "bus", "motorcycle", "bicycle" -> IOSOrange
                                "dog", "cat", "bird", "horse" -> IOSGreen
                                else -> IOSRed
                            }
                            drawLine(
                                color = color.copy(alpha = 0.85f),
                                start = Offset(x, top + 4f),
                                end = Offset(x, bottom - 4f),
                                strokeWidth = 3f,
                            )
                        }

                        // playhead + bong bóng thời gian
                        val px = minutes * pxPerMin
                        drawLine(
                            color = IOSOrange,
                            start = Offset(px, 0f),
                            end = Offset(px, h),
                            strokeWidth = 2f,
                            pathEffect = androidx.compose.ui.graphics.PathEffect
                                .dashPathEffect(floatArrayOf(8f, 6f), 0f),
                        )
                        val label = "%02d:%02d".format(minutes.toInt() / 60, minutes.toInt() % 60)
                        val bubble = textMeasurer.measure(
                            label,
                            style = TextStyle(color = Color.White, fontSize = 11.sp),
                        )
                        val bw = bubble.size.width + 16f
                        val bh = bubble.size.height + 8f
                        val bx = (px - bw / 2).coerceIn(0f, w - bw)
                        drawRoundRect(
                            color = IOSRed,
                            topLeft = Offset(bx, h - bh - 2f),
                            size = Size(bw, bh),
                            cornerRadius = CornerRadius(bh / 2, bh / 2),
                        )
                        drawText(
                            textMeasurer = textMeasurer,
                            text = label,
                            topLeft = Offset(bx + 8f, h - bh + 2f),
                            style = TextStyle(color = Color.White, fontSize = 11.sp),
                        )
                        // tay nắm để kéo thanh thời gian
                        drawCircle(
                            color = IOSOrange,
                            radius = 7f,
                            center = Offset(px, top - 8f),
                        )
                    }
                }

                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "%02d:%02d".format(minutes.toInt() / 60, minutes.toInt() % 60),
                        style = MaterialTheme.typography.titleMedium,
                        color = IOSLabel,
                    )
                    Spacer(Modifier.weight(1f))
                    OutlinedButton(onClick = { playAt(minutes) }) { Text("Phát", fontSize = 13.sp) }
                    OutlinedButton(onClick = { downloadAt(minutes) }, enabled = !saving) {
                        if (saving) {
                            CircularProgressIndicator(Modifier.width(14.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(6.dp))
                        }
                        Text("Tải", fontSize = 13.sp)
                    }
                }

                message?.let {
                    Text(
                        it,
                        fontSize = 12.sp,
                        color = IOSGreen,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }

                Text(
                    "Chạm vào timeline để chọn thời điểm · vạch xanh = có ghi hình, " +
                        "vạch màu = sự kiện (xanh dương: người, cam: xe, đỏ: khác).",
                    fontSize = 11.sp,
                    color = IOSSecondaryLabel,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
        }
    }
}
