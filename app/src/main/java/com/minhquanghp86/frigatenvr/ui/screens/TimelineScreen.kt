package com.minhquanghp86.frigatenvr.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.minhquanghp86.frigatenvr.data.MediaSaver
import com.minhquanghp86.frigatenvr.ui.ErrorBox
import com.minhquanghp86.frigatenvr.ui.IOSBlue
import com.minhquanghp86.frigatenvr.ui.IOSCard
import com.minhquanghp86.frigatenvr.ui.IOSGreen
import com.minhquanghp86.frigatenvr.ui.IOSLabel
import com.minhquanghp86.frigatenvr.ui.IOSSecondaryLabel
import com.minhquanghp86.frigatenvr.ui.IosDropdown
import com.minhquanghp86.frigatenvr.ui.IosGroup
import com.minhquanghp86.frigatenvr.ui.LargeTitleHeader
import com.minhquanghp86.frigatenvr.ui.Pill
import com.minhquanghp86.frigatenvr.ui.SectionTitle
import com.minhquanghp86.frigatenvr.ui.TimelineViewModel
import com.minhquanghp86.frigatenvr.ui.VideoPlayer
import com.minhquanghp86.frigatenvr.ui.fmtDuration
import com.minhquanghp86.frigatenvr.ui.rememberRepo
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dayFmt = DateTimeFormatter.ofPattern("EEEE, dd/MM/yyyy", Locale.getDefault())

@Composable
fun TimelineScreen(
    modifier: Modifier = Modifier,
    initialCamera: String? = null,
    showBack: Boolean = false,
    onBack: () -> Unit = {},
) {
    val vm: TimelineViewModel = viewModel()
    val cameras by vm.cameras.collectAsState()
    val camera by vm.camera.collectAsState()
    val days by vm.days.collectAsState()
    val hours by vm.hours.collectAsState()
    val loading by vm.loading.collectAsState()
    val error by vm.error.collectAsState()
    val playing by vm.playing.collectAsState()

    val repo = rememberRepo()
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()

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
        if (selectedDay == null || selectedDay !in days) {
            selectedDay = days.firstOrNull()
        }
    }

    // Đổi ngày/camera -> dừng video cũ để tránh phát nhầm dữ liệu.
    LaunchedEffect(selectedDay, camera) {
        vm.stop()
        val day = selectedDay
        if (day != null) {
            val firstHour = hours.firstOrNull { it.day == day }?.hours?.firstOrNull()?.hour?.toIntOrNull()
            minutes = ((firstHour ?: 0) * 60 + 30).toFloat().coerceIn(0f, 1439f)
        }
    }

    fun playAt(value: Float) {
        val day = selectedDay ?: return
        val total = value.toInt().coerceIn(0, 1439)
        val hour = total / 60
        val offsetSec = (total % 60) * 60
        startPosMs = offsetSec * 1000L
        vm.play(day, hour.toString())
    }

    fun hourStartEpoch(day: String, hour: Int): Long =
        ZonedDateTime.of(
            LocalDate.parse(day),
            LocalTime.of(hour, 0),
            ZoneId.systemDefault(),
        ).toEpochSecond()

    fun downloadAt(value: Float) {
        val day = selectedDay ?: return
        val cam = camera ?: return
        val total = value.toInt().coerceIn(0, 1439)
        val hour = total / 60
        scope.launch {
            saving = true
            message = null
            val start = hourStartEpoch(day, hour)
            val saved = MediaSaver.save(
                context = context,
                client = repo.okhttp(),
                url = repo.clipRange(cam, start, start + 3600),
                fileName = "frigate-$cam-$day-$hour.mp4",
                mime = "video/mp4",
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
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
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

                Spacer(Modifier.height(12.dp))

                // Trình phát
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
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
                        )
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "Chọn thời điểm bên dưới để phát",
                                color = IOSSecondaryLabel,
                                fontSize = 13.sp,
                            )
                            val day = selectedDay
                            val avail = day?.let { d ->
                                hours.firstOrNull { it.day == d }?.hours.orEmpty()
                            }.orEmpty()
                            if (avail.isNotEmpty()) {
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    "Có ${avail.size} khung giờ, mới nhất ${avail.first().hour}:00",
                                    color = IOSSecondaryLabel,
                                    fontSize = 12.sp,
                                )
                            }
                        }
                    }
                }

                // Thanh trượt chọn thời điểm trong ngày
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "%02d:%02d".format((minutes.toInt() / 60), (minutes.toInt() % 60)),
                            style = MaterialTheme.typography.titleMedium,
                            color = IOSLabel,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.weight(1f))
                        val day = selectedDay
                        val hoursForDay = day?.let { d ->
                            hours.firstOrNull { it.day == d }?.hours.orEmpty()
                        }.orEmpty()
                        Text(
                            if (hoursForDay.isEmpty()) "không có ghi hình" else "${hoursForDay.size} khung giờ",
                            fontSize = 12.sp,
                            color = IOSSecondaryLabel,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Slider(
                        value = minutes,
                        onValueChange = { minutes = it },
                        onValueChangeFinished = { playAt(minutes) },
                        valueRange = 0f..1439f,
                        colors = SliderDefaults.colors(
                            thumbColor = IOSBlue,
                            activeTrackColor = IOSBlue,
                            inactiveTrackColor = IOSCard,
                        ),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { playAt(minutes) },
                            modifier = Modifier.weight(1f),
                        ) { Text("Phát", fontSize = 13.sp) }
                        OutlinedButton(
                            onClick = { downloadAt(minutes) },
                            enabled = !saving,
                            modifier = Modifier.weight(1f),
                        ) {
                            if (saving) {
                                CircularProgressIndicator(Modifier.width(16.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(6.dp))
                            }
                            Text("Tải khung giờ", fontSize = 13.sp)
                        }
                    }
                    message?.let {
                        Spacer(Modifier.height(6.dp))
                        Text(it, fontSize = 12.sp, color = IOSGreen)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Kéo thanh trượt tới thời điểm cần xem trong ngày đã chọn.",
                        fontSize = 11.sp,
                        color = IOSSecondaryLabel,
                    )
                }
            }
        }
    }
}
