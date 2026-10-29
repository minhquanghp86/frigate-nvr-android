package com.minhquanghp86.frigatenvr.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.minhquanghp86.frigatenvr.data.MediaSaver
import com.minhquanghp86.frigatenvr.ui.IOSBlue
import com.minhquanghp86.frigatenvr.ui.IOSCard
import com.minhquanghp86.frigatenvr.ui.IOSGreen
import com.minhquanghp86.frigatenvr.ui.IOSLabel
import com.minhquanghp86.frigatenvr.ui.IOSOrange
import com.minhquanghp86.frigatenvr.ui.IOSRed
import com.minhquanghp86.frigatenvr.ui.IOSSecondaryLabel
import com.minhquanghp86.frigatenvr.ui.IosDivider
import com.minhquanghp86.frigatenvr.ui.IosGroup
import com.minhquanghp86.frigatenvr.ui.IosRow
import com.minhquanghp86.frigatenvr.ui.LargeTitleHeader
import com.minhquanghp86.frigatenvr.ui.SectionTitle
import com.minhquanghp86.frigatenvr.ui.fmtDateTime
import com.minhquanghp86.frigatenvr.ui.fmtDuration
import com.minhquanghp86.frigatenvr.ui.rememberRepo
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull

@Composable
fun OverviewScreen(
    onBack: () -> Unit,
    onOpenLogs: () -> Unit = {},
    onOpenStorage: () -> Unit = {},
) {
    val repo = rememberRepo()
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()

    var stats by remember { mutableStateOf<JsonObject?>(null) }
    var notices by remember { mutableStateOf<JsonArray?>(null) }
    var storage by remember { mutableStateOf<JsonObject?>(null) }
    var exports by remember { mutableStateOf<List<JsonObject>>(emptyList()) }
    var version by remember { mutableStateOf("—") }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        loading = true
        error = null
        version = runCatching { repo.versionText() }.getOrDefault("—")
        stats = runCatching { repo.statsJson() }.getOrNull()
        notices = runCatching { repo.notices() as? JsonArray }.getOrNull()
        storage = runCatching { repo.storage() as? JsonObject }.getOrNull()
        exports = runCatching { repo.exportsList() }.getOrDefault(emptyList())
        loading = false
    }

    fun downloadExport(row: JsonObject) {
        val id = (row["id"] as? JsonPrimitive)?.content ?: return
        scope.launch {
            message = "Đang chuẩn bị tải…"
            val url = runCatching { repo.exportDownloadUrl(id) }.getOrNull()
            if (url == null) {
                message = "Không tìm thấy file export"
                return@launch
            }
            val name = (row["name"] as? JsonPrimitive)?.content ?: "export-$id"
            val saved = MediaSaver.save(
                context = context,
                client = repo.okhttp(),
                url = url,
                fileName = "$name.mp4",
                mime = "video/mp4",
                onProgress = { percent ->
                    message = if (percent == null) "Đang tải…" else "Đang tải… $percent%"
                },
            )
            message = saved?.let { "Đã lưu: $it" } ?: "Không tải được export"
        }
    }

    Column(Modifier.fillMaxSize()) {
        LargeTitleHeader(
            title = "Tổng quan",
            subtitle = "Frigate $version",
            trailing = {
                IconButton(onClick = onBack) {
                    Icon(Icons.Outlined.ArrowBack, contentDescription = "Quay lại", tint = IOSLabel)
                }
            },
        )

        if (loading) {
            Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
            return@Column
        }

        error?.let {
            Text(it, color = IOSRed, modifier = Modifier.padding(20.dp))
            return@Column
        }

        LazyColumn(contentPadding = PaddingValues(bottom = 28.dp)) {
            // Hệ thống
            item {
                SectionTitle("Hệ thống")
                IosGroup {
                    val service = stats?.get("service") as? JsonObject
                    IosRow("Phiên bản", value = version)
                    if (service != null) {
                        IosDivider(16)
                        IosRow("Thời gian chạy", value = service.d("uptime")?.let { fmtDuration(it) })
                        IosDivider(16)
                        IosRow("Bộ nhớ", value = service.d("mem_usage")?.let { "%.0f%%".format(it) })
                        IosDivider(16)
                        IosRow("CPU", value = service.d("cpu_usage")?.let { "%.0f%%".format(it) })
                    }
                    IosDivider(16)
                    IosRow("Tốc độ phát hiện", value = stats?.d("detection_fps")?.let { "%.1f fps".format(it) })
                    IosDivider(16)
                    IosRow("Nhật ký hệ thống", chevron = true, onClick = onOpenLogs)
                    IosDivider(56)
                    IosRow("Dung lượng ghi hình", chevron = true, onClick = onOpenStorage)
                }
            }

            // Cảnh báo
            item {
                SectionTitle("Cảnh báo")
                IosGroup {
                    val list = notices.orEmpty()
                    if (list.isEmpty()) {
                        IosRow("Không có cảnh báo")
                    } else {
                        list.take(5).forEachIndexed { index, item ->
                            val obj = item as? JsonObject
                            IosRow(
                                title = obj?.s("text") ?: obj?.s("message") ?: obj?.s("title")
                                ?: (obj?.s("type") ?: "Cảnh báo"),
                                subtitle = obj?.s("level"),
                            )
                            if (index != minOf(list.size, 5) - 1) IosDivider(16)
                        }
                    }
                }
            }

            // Camera
            val cameras = (stats?.get("cameras") as? JsonObject)
            if (cameras != null) {
                item {
                    SectionTitle("Camera")
                    IosGroup {
                        val entries = cameras.entries.toList()
                        entries.forEachIndexed { index, (name, value) ->
                            val obj = value as? JsonObject
                            IosRow(
                                name,
                                value = obj?.d("camera_fps")?.let { "%.1f fps".format(it) },
                            )
                            if (index != entries.lastIndex) IosDivider(16)
                        }
                    }
                }
            }

            // Lưu trữ
            val storageObj = storage
            if (storageObj != null && storageObj.isNotEmpty()) {
                item {
                    SectionTitle("Lưu trữ")
                    IosGroup {
                        val entries = storageObj.entries.toList()
                        entries.forEachIndexed { index, (name, value) ->
                            val obj = value as? JsonObject
                            val pct = obj?.d("usage_percent") ?: 0.0
                            val usage = obj?.d("usage") ?: 0.0
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(name, color = IOSLabel, style = MaterialTheme.typography.bodyLarge)
                                    Spacer(Modifier.weight(1f))
                                    Text(
                                        formatMb(usage) + " · %.1f%%".format(pct),
                                        color = IOSSecondaryLabel,
                                        fontSize = 12.sp,
                                    )
                                }
                                Spacer(Modifier.height(6.dp))
                                LinearProgressIndicator(
                                    progress = { (pct / 100.0).toFloat().coerceIn(0f, 1f) },
                                    color = when {
                                        pct >= 90 -> IOSRed
                                        pct >= 70 -> IOSOrange
                                        else -> IOSGreen
                                    },
                                    trackColor = IOSCard,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(5.dp)
                                        .clip(RoundedCornerShape(99.dp)),
                                )
                            }
                            if (index != entries.lastIndex) IosDivider(16)
                        }
                    }
                }
            }

            // Export
            item {
                SectionTitle("Clip đã xuất")
                IosGroup {
                    if (exports.isEmpty()) {
                        IosRow("Chưa có clip nào được xuất")
                    } else {
                        exports.take(10).forEachIndexed { index, row ->
                            val name = row.s("name") ?: "Export"
                            val camera = row.s("camera").orEmpty()
                            val date = row.d("date") ?: 0.0
                            val inProgress = (row["in_progress"] as? JsonPrimitive)?.booleanOrNull
                                ?: false
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .then(
                                        if (!inProgress) {
                                            Modifier.clickable { downloadExport(row) }
                                        } else {
                                            Modifier
                                        },
                                    )
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(name, color = IOSLabel, style = MaterialTheme.typography.bodyLarge)
                                    Text(
                                        "$camera · ${fmtDateTime(date)}" +
                                            if (inProgress) " · đang xử lý" else "",
                                        color = IOSSecondaryLabel,
                                        fontSize = 12.sp,
                                    )
                                }
                                if (!inProgress) {
                                    Icon(
                                        Icons.Outlined.Download,
                                        contentDescription = "Tải",
                                        tint = IOSBlue,
                                    )
                                } else {
                                    CircularProgressIndicator(
                                        Modifier.width(16.dp),
                                        strokeWidth = 2.dp,
                                    )
                                }
                            }
                            if (index != minOf(exports.size, 10) - 1) IosDivider(56)
                        }
                    }
                }
                message?.let {
                    Text(
                        it,
                        color = IOSGreen,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    )
                }
            }
        }
    }
}

private fun JsonObject.s(key: String): String? =
    (this[key] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() && it != "null" }

private fun JsonObject.d(key: String): Double? =
    (this[key] as? JsonPrimitive)?.doubleOrNull

private fun formatMb(mb: Double): String =
    if (mb >= 1024) "%.1f GB".format(mb / 1024.0) else "%.0f MB".format(mb)
