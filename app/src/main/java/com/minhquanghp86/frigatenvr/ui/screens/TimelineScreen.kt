package com.minhquanghp86.frigatenvr.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.minhquanghp86.frigatenvr.data.HourRecording
import com.minhquanghp86.frigatenvr.ui.IOSCard
import com.minhquanghp86.frigatenvr.ui.ErrorBox
import com.minhquanghp86.frigatenvr.ui.LoadingBox
import com.minhquanghp86.frigatenvr.ui.ScreenHeader
import com.minhquanghp86.frigatenvr.ui.TimelineViewModel
import com.minhquanghp86.frigatenvr.ui.VideoPlayer
import com.minhquanghp86.frigatenvr.ui.fmtDuration
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dayFmt = DateTimeFormatter.ofPattern("dd/MM", Locale.getDefault())
private val dayFullFmt = DateTimeFormatter.ofPattern("EEEE dd/MM/yyyy", Locale.getDefault())

@Composable
fun TimelineScreen(modifier: Modifier = Modifier) {
    val vm: TimelineViewModel = viewModel()
    val cameras by vm.cameras.collectAsState()
    val camera by vm.camera.collectAsState()
    val days by vm.days.collectAsState()
    val hours by vm.hours.collectAsState()
    val loading by vm.loading.collectAsState()
    val error by vm.error.collectAsState()
    val playing by vm.playing.collectAsState()

    var selectedDay by remember { mutableStateOf<String?>(null) }
    var playingSpec by remember { mutableStateOf<Pair<String, String>?>(null) }
    var saving by remember { mutableStateOf(false) }
    var saveMsg by remember { mutableStateOf<String?>(null) }
    val repo = com.minhquanghp86.frigatenvr.ui.rememberRepo()
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(days) {
        if (selectedDay == null || selectedDay !in days) selectedDay = days.firstOrNull()
    }

    Column(modifier.fillMaxSize()) {
        ScreenHeader(
            title = "Xem lại",
            subtitle = camera?.let { "Ghi hình · $it" } ?: "Ghi hình",
            actions = {
                IconButton(onClick = { vm.refresh() }) {
                    Icon(Icons.Outlined.Refresh, contentDescription = "Tải lại")
                }
            },
        )

        if (cameras.size > 1) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(cameras) { name ->
                    AssistChip(
                        onClick = { vm.setCamera(name) },
                        label = {
                            Text(
                                if (name == camera) "$name ✓" else name,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                    )
                }
            }
        }

        val stream = playing
        if (stream != null) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF000000)),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Column {
                    VideoPlayer(
                        url = stream,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp),
                    )
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            selectedDay?.let { "Phát $it" } ?: "Đang phát",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f),
                        )
                        playingSpec?.let { spec ->
                            OutlinedButton(
                                onClick = {
                                    scope.launch {
                                        saving = true
                                        val start = java.time.ZonedDateTime.of(
                                            java.time.LocalDate.parse(spec.first),
                                            java.time.LocalTime.of(spec.second.toIntOrNull() ?: 0, 0),
                                            java.time.ZoneId.systemDefault(),
                                        ).toEpochSecond()
                                        val saved = com.minhquanghp86.frigatenvr.data.MediaSaver.save(
                                            context = context,
                                            client = repo.okhttp(),
                                            url = repo.clipRange(
                                                camera ?: "",
                                                start,
                                                start + 3600,
                                            ),
                                            fileName = "frigate-${camera}-${spec.first}-${spec.second}.mp4",
                                            mime = "video/mp4",
                                        )
                                        saveMsg = saved?.let { "Đã lưu: $it" } ?: "Không tải được"
                                        saving = false
                                    }
                                },
                                enabled = !saving,
                            ) {
                                Text(if (saving) "Đang tải…" else "Tải", fontSize = 12.sp)
                            }
                            Spacer(Modifier.width(6.dp))
                        }
                        OutlinedButton(onClick = { vm.stop() }) {
                            Icon(
                                Icons.Outlined.Close,
                                contentDescription = "Đóng",
                                modifier = Modifier.width(16.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("Đóng", fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        when {
            loading && days.isEmpty() -> LoadingBox()
            error != null && days.isEmpty() -> ErrorBox(error!!, onRetry = { vm.refresh() })
            days.isEmpty() -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text(
                    "Frigate chưa có bản ghi nào.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            else -> {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(days) { day ->
                        AssistChip(
                            onClick = { selectedDay = day; vm.stop() },
                            label = {
                                Text(
                                    if (day == selectedDay) "$day ✓" else day,
                                    fontSize = 12.sp,
                                )
                            },
                        )
                    }
                }

                val day = selectedDay
                if (day == null) {
                    Box(Modifier.fillMaxSize(), Alignment.Center) {
                        Text("Chọn một ngày.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    val dayHours = hours.firstOrNull { it.day == day }?.hours.orEmpty()
                    if (dayHours.isEmpty()) {
                        Box(Modifier.fillMaxSize(), Alignment.Center) {
                            Text(
                                "Không có ghi hình cho ngày $day (camera ${camera ?: ""}).",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        Column(Modifier.fillMaxSize()) {
                            Text(
                                dayLabel(day),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                            )
                            LazyVerticalGrid(
                                columns = GridCells.Adaptive(minSize = 104.dp),
                                contentPadding = PaddingValues(8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                items(dayHours, key = { "${day}-${it.hour}" }) { hour ->
                                    HourCard(day = day, hour = hour) {
                                        vm.play(day, hour.hour)
                                        playingSpec = day to hour.hour
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HourCard(day: String, hour: HourRecording, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = IOSCard),
        shape = RoundedCornerShape(10.dp),
    ) {
        Column(Modifier.padding(10.dp)) {
            Text(
                "%02d:00".format(hour.hour.toIntOrNull() ?: 0),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "${hour.events} sự kiện",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
            if (hour.duration > 0) {
                Text(
                    "ghi ${fmtDuration(hour.duration.toDouble())}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun dayLabel(day: String): String = try {
    LocalDate.parse(day).format(dayFullFmt)
} catch (_: Exception) {
    day
}
