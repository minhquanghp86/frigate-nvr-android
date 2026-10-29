package com.minhquanghp86.frigatenvr.ui.screens

import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull

private const val MAX_SAMPLES = 160

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
    var earliestDay by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var message by remember { mutableStateOf<String?>(null) }

    // Chuỗi mẫu cho biểu đồ bộ phát hiện (poll /api/stats mỗi 5s).
    val inference = remember { mutableListOf<Float>() }
    val cpu = remember { mutableListOf<Float>() }
    val mem = remember { mutableListOf<Float>() }
    var tick by remember { mutableStateOf(0) }

    suspend fun loadSlow() {
        version = runCatching { repo.versionText() }.getOrDefault("—")
        notices = runCatching { repo.notices() as? JsonArray }.getOrNull()
        storage = runCatching { repo.storage() as? JsonObject }.getOrNull()
        exports = runCatching { repo.exportsList() }.getOrDefault(emptyList())
        earliestDay = runCatching { repo.earliestRecordingDay() }.getOrNull()
    }

    LaunchedEffect(Unit) {
        loadSlow()
        loading = false
        var i = 0
        while (true) {
            val s = runCatching { repo.statsJson() }.getOrNull()
            if (s != null) {
                stats = s
                val detector = (s["detectors"] as? JsonObject)?.values
                    ?.firstOrNull() as? JsonObject
                if (detector != null) {
                    detector.d("inference_speed")?.let {
                        inference += it.toFloat()
                        if (inference.size > MAX_SAMPLES) inference.removeAt(0)
                    }
                    detector.d("cpu")?.let {
                        cpu += it.toFloat()
                        if (cpu.size > MAX_SAMPLES) cpu.removeAt(0)
                    }
                    detector.d("mem")?.let {
                        mem += it.toFloat()
                        if (mem.size > MAX_SAMPLES) mem.removeAt(0)
                    }
                }
                tick++
            }
            i++
            if (i % 6 == 0) loadSlow()
            delay(5_000)
        }
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
            title = "Hệ thống",
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

        val service = stats?.get("service") as? JsonObject
        val detector = (stats?.get("detectors") as? JsonObject)
            ?.entries?.firstOrNull()?.value as? JsonObject
        val detectorName = (stats?.get("detectors") as? JsonObject)?.keys?.firstOrNull() ?: "detector"

        LazyColumn(contentPadding = PaddingValues(bottom = 28.dp)) {
            // ---- Bộ phát hiện
            if (detector != null) {
                item {
                    SectionTitle("Bộ phát hiện · $detectorName")
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(IOSCard)
                            .padding(vertical = 6.dp),
                    ) {
                        BarChart(
                            title = "Tốc độ suy luận",
                            current = detector.d("inference_speed")?.let { "%.2f ms".format(it) } ?: "—",
                            values = inference,
                        )
                        IosDivider(16)
                        BarChart(
                            title = "Mức sử dụng CPU",
                            current = detector.d("cpu")?.let { "%.1f%%".format(it) } ?: "—",
                            values = cpu,
                        )
                        IosDivider(16)
                        BarChart(
                            title = "Mức sử dụng bộ nhớ",
                            current = detector.d("mem")?.let { "%.1f%%".format(it) } ?: "—",
                            values = mem,
                        )
                    }
                }
            }

            // ---- Dung lượng lưu trữ
            val storagePaths = service?.get("storage") as? JsonObject
            if (storagePaths != null && storagePaths.isNotEmpty()) {
                item {
                    SectionTitle("Dung lượng")
                    Column(
                        Modifier.padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        storagePaths.entries.forEach { (path, value) ->
                            val obj = value as? JsonObject ?: return@forEach
                            val total = obj.d("total") ?: 0.0
                            val used = obj.d("used") ?: 0.0
                            val pct = if (total > 0) used / total * 100 else 0.0
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(IOSCard)
                                    .padding(12.dp),
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        if (path.contains("frigate")) "Bản ghi" else path,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = IOSLabel,
                                    )
                                    Spacer(Modifier.weight(1f))
                                    Text(
                                        "%.1f%%".format(pct),
                                        fontSize = 12.sp,
                                        color = IOSSecondaryLabel,
                                    )
                                }
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    "${formatMb(used)} / ${formatMb(total)}",
                                    fontSize = 13.sp,
                                    color = IOSSecondaryLabel,
                                )
                                Spacer(Modifier.height(6.dp))
                                LinearProgressIndicator(
                                    progress = { (pct / 100.0).toFloat().coerceIn(0f, 1f) },
                                    color = if (pct >= 90) IOSRed else IOSBlue,
                                    trackColor = Color(0x33FFFFFF),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(7.dp)
                                        .clip(RoundedCornerShape(99.dp)),
                                )
                                if (path.contains("frigate") && earliestDay != null) {
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        "Bản ghi sớm nhất: $earliestDay",
                                        fontSize = 12.sp,
                                        color = IOSSecondaryLabel,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ---- Lưu trữ theo camera (kèm băng thông)
            val storageObj = storage
            if (storageObj != null && storageObj.isNotEmpty()) {
                item {
                    SectionTitle("Lưu trữ Camera")
                    IosGroup {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                        ) {
                            Text("Camera", fontSize = 11.sp, color = IOSSecondaryLabel, modifier = Modifier.weight(1.2f))
                            Text("Lưu trữ", fontSize = 11.sp, color = IOSSecondaryLabel, modifier = Modifier.weight(0.9f))
                            Text("%", fontSize = 11.sp, color = IOSSecondaryLabel, modifier = Modifier.weight(0.7f))
                            Text("MiB/giờ", fontSize = 11.sp, color = IOSSecondaryLabel, modifier = Modifier.weight(1f))
                        }
                        IosDivider(12)
                        val entries = storageObj.entries.toList()
                        entries.forEachIndexed { index, (name, value) ->
                            val obj = value as? JsonObject
                            val usage = obj?.d("usage") ?: 0.0
                            val pct = obj?.d("usage_percent") ?: 0.0
                            val bandwidth = obj?.d("bandwidth") ?: 0.0
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Row(Modifier.weight(1.2f), verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        Modifier
                                            .width(8.dp)
                                            .height(8.dp)
                                            .clip(RoundedCornerShape(99.dp))
                                            .background(IOSBlue),
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(name, fontSize = 13.sp, color = IOSLabel, maxLines = 1)
                                }
                                Text(
                                    formatMb(usage),
                                    fontSize = 12.sp,
                                    color = IOSLabel,
                                    modifier = Modifier.weight(0.9f),
                                )
                                Text(
                                    "%.2f%%".format(pct),
                                    fontSize = 12.sp,
                                    color = IOSSecondaryLabel,
                                    modifier = Modifier.weight(0.7f),
                                )
                                Text(
                                    "%.2f".format(bandwidth),
                                    fontSize = 12.sp,
                                    color = IOSSecondaryLabel,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            if (index != entries.lastIndex) IosDivider(12)
                        }
                    }
                }
            }

            // ---- Hệ thống
            item {
                SectionTitle("Hệ thống")
                IosGroup {
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
                    IosRow(
                        "Tốc độ phát hiện",
                        value = stats?.d("detection_fps")?.let { "%.1f fps".format(it) },
                    )
                    IosDivider(16)
                    IosRow("Nhật ký hệ thống", chevron = true, onClick = onOpenLogs)
                    IosDivider(56)
                    IosRow("Dung lượng ghi hình", chevron = true, onClick = onOpenStorage)
                }
            }

            // ---- Cảnh báo
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

            // ---- Camera fps
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

            // ---- Export
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
                                    CircularProgressIndicator(Modifier.width(16.dp), strokeWidth = 2.dp)
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

/** Biểu đồ cột nhỏ kiểu Frigate: giá trị hiện tại + 160 mẫu gần nhất. */
@Composable
private fun BarChart(title: String, current: String, values: List<Float>) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = IOSLabel)
            Spacer(Modifier.weight(1f))
            Text(current, fontSize = 12.sp, color = IOSSecondaryLabel)
        }
        Spacer(Modifier.height(6.dp))
        val maxValue = (values.maxOrNull() ?: 0f).coerceAtLeast(1f)
        Canvas(Modifier.fillMaxWidth().height(72.dp)) {
            if (values.isEmpty()) return@Canvas
            val n = values.size
            val barW = size.width / n
            values.forEachIndexed { index, v ->
                val h = ((v / maxValue) * size.height).coerceIn(0f, size.height)
                drawRect(
                    color = if (v >= maxValue * 0.85f) IOSOrange else IOSGreen,
                    topLeft = Offset(index * barW, size.height - h),
                    size = Size((barW * 0.7f).coerceAtLeast(1f), h),
                )
            }
        }
        Row(Modifier.fillMaxWidth()) {
            Text("0", fontSize = 10.sp, color = IOSSecondaryLabel)
            Spacer(Modifier.weight(1f))
            Text("%.1f".format(maxValue), fontSize = 10.sp, color = IOSSecondaryLabel)
        }
    }
}

private fun JsonObject.s(key: String): String? =
    (this[key] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() && it != "null" }

private fun JsonObject.d(key: String): Double? =
    (this[key] as? JsonPrimitive)?.doubleOrNull

private fun formatMb(mb: Double): String =
    if (mb >= 1024) "%.1f GiB".format(mb / 1024.0) else "%.0f MiB".format(mb)
