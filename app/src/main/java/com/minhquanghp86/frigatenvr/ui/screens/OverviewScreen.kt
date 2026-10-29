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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
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
    var selectedNotice by remember { mutableStateOf<JsonObject?>(null) }
    var noticeMessage by remember { mutableStateOf<String?>(null) }
    var deleteExportTarget by remember { mutableStateOf<JsonObject?>(null) }

    // Chuỗi mẫu cho biểu đồ bộ phát hiện (poll /api/stats mỗi 5s).
    val inference = remember { mutableListOf<Float>() }
    val cpu = remember { mutableListOf<Float>() }
    val mem = remember { mutableListOf<Float>() }

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
            // ---- Hero: phiên bản + chỉ số nhanh
            item {
                HeroCard(
                    version = version,
                    uptime = service?.d("uptime"),
                    cpuUsage = service?.d("cpu_usage"),
                    memUsage = service?.d("mem_usage"),
                    detectionFps = stats?.d("detection_fps"),
                )
            }

            // ---- Bộ phát hiện
            if (detector != null) {
                item {
                    SectionTitle("Bộ phát hiện · $detectorName")
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(IOSCard)
                            .padding(vertical = 4.dp),
                    ) {
                        BarChart(
                            title = "Tốc độ suy luận",
                            current = detector.d("inference_speed")
                                ?.let { "%.2f ms".format(it) } ?: "—",
                            values = inference,
                        )
                        IosDivider(16)
                        BarChart(
                            title = "CPU",
                            current = detector.d("cpu")?.let { "%.1f%%".format(it) } ?: "—",
                            values = cpu,
                        )
                        IosDivider(16)
                        BarChart(
                            title = "Bộ nhớ",
                            current = detector.d("mem")?.let { "%.1f%%".format(it) } ?: "—",
                            values = mem,
                        )
                    }
                }
            }

            // ---- Dung lượng
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
                            StorageCard(
                                title = if (path.contains("frigate")) "Bản ghi" else path,
                                used = used,
                                total = total,
                                percent = pct,
                                footnote = if (path.contains("frigate") && earliestDay != null) {
                                    "Bản ghi sớm nhất: $earliestDay"
                                } else {
                                    null
                                },
                            )
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
                            Text(
                                "Camera",
                                fontSize = 11.sp,
                                color = IOSSecondaryLabel,
                                modifier = Modifier.weight(1.2f),
                            )
                            Text(
                                "Lưu trữ",
                                fontSize = 11.sp,
                                color = IOSSecondaryLabel,
                                modifier = Modifier.weight(0.9f),
                            )
                            Text(
                                "%",
                                fontSize = 11.sp,
                                color = IOSSecondaryLabel,
                                modifier = Modifier.weight(0.7f),
                            )
                            Text(
                                "MiB/giờ",
                                fontSize = 11.sp,
                                color = IOSSecondaryLabel,
                                modifier = Modifier.weight(1f),
                            )
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
                                Row(
                                    Modifier.weight(1.2f),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Box(
                                        Modifier
                                            .size(8.dp)
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
                                    "%.1f%%".format(pct),
                                    fontSize = 12.sp,
                                    color = IOSSecondaryLabel,
                                    modifier = Modifier.weight(0.7f),
                                )
                                Text(
                                    "%.1f".format(bandwidth),
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

            // ---- Lối tắt
            item {
                SectionTitle("Hệ thống")
                IosGroup {
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
                                chevron = obj != null,
                                onClick = if (obj != null) {
                                    { selectedNotice = obj }
                                } else {
                                    null
                                },
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

            // ---- Clip đã xuất (tải + xoá)
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
                                    .padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        name,
                                        color = IOSLabel,
                                        style = MaterialTheme.typography.bodyLarge,
                                        maxLines = 1,
                                    )
                                    Text(
                                        "$camera · ${fmtDateTime(date)}" +
                                            if (inProgress) " · đang xử lý" else "",
                                        color = IOSSecondaryLabel,
                                        fontSize = 12.sp,
                                        maxLines = 1,
                                    )
                                }
                                if (inProgress) {
                                    CircularProgressIndicator(
                                        Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                    )
                                } else {
                                    IconButton(onClick = { downloadExport(row) }) {
                                        Icon(
                                            Icons.Outlined.Download,
                                            contentDescription = "Tải",
                                            tint = IOSBlue,
                                        )
                                    }
                                    IconButton(onClick = { deleteExportTarget = row }) {
                                        Icon(
                                            Icons.Outlined.Delete,
                                            contentDescription = "Xoá clip",
                                            tint = IOSRed,
                                        )
                                    }
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

    deleteExportTarget?.let { row ->
        val id = row.s("id")
        AlertDialog(
            onDismissRequest = { deleteExportTarget = null },
            title = { Text("Xoá clip đã xuất?") },
            text = {
                Text("File sẽ bị xoá khỏi máy chủ Frigate để giải phóng dung lượng.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleteExportTarget = null
                        if (id == null) return@TextButton
                        scope.launch {
                            try {
                                repo.deleteExport(id)
                                message = "Đã xoá clip"
                                exports = runCatching { repo.exportsList() }
                                    .getOrDefault(exports)
                                storage = runCatching { repo.storage() as? JsonObject }.getOrNull()
                            } catch (e: Exception) {
                                message = e.message ?: "Không xoá được clip"
                            }
                        }
                    },
                ) { Text("Xoá", color = IOSRed) }
            },
            dismissButton = {
                TextButton(onClick = { deleteExportTarget = null }) { Text("Huỷ") }
            },
        )
    }

    selectedNotice?.let { notice ->
        val noticeId = notice.s("id") ?: notice.s("notice_id")
        AlertDialog(
            onDismissRequest = { selectedNotice = null },
            title = { Text("Cảnh báo") },
            text = {
                Column {
                    Text(
                        notice.s("text") ?: notice.s("message") ?: notice.s("title")
                        ?: (notice.s("type") ?: "Cảnh báo"),
                        fontSize = 13.sp,
                    )
                    noticeMessage?.let {
                        Spacer(Modifier.height(6.dp))
                        Text(it, fontSize = 11.sp, color = IOSGreen)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (noticeId == null) return@TextButton
                        scope.launch {
                            try {
                                repo.acknowledgeNotice(noticeId)
                                notices = runCatching { repo.notices() as? JsonArray }.getOrNull()
                                selectedNotice = null
                            } catch (e: Exception) {
                                noticeMessage = e.message ?: "Không đánh dấu được"
                            }
                        }
                    },
                ) { Text("Đã hiểu") }
            },
            dismissButton = {
                Row {
                    TextButton(
                        onClick = {
                            if (noticeId == null) return@TextButton
                            scope.launch {
                                try {
                                    repo.muteNotice(noticeId)
                                    notices = runCatching { repo.notices() as? JsonArray }.getOrNull()
                                    selectedNotice = null
                                } catch (e: Exception) {
                                    noticeMessage = e.message ?: "Không ẩn được"
                                }
                            }
                        },
                    ) { Text("Ẩn vĩnh viễn", color = IOSRed) }
                    TextButton(onClick = { selectedNotice = null }) { Text("Đóng") }
                }
            },
        )
    }
}

/** Thẻ tổng quan: phiên bản + thời gian chạy + 3 chỉ số tức thời. */
@Composable
private fun HeroCard(
    version: String,
    uptime: Double?,
    cpuUsage: Double?,
    memUsage: Double?,
    detectionFps: Double?,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF0F1A2C), Color(0xFF16294A))))
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "FRIGATE NVR",
                    color = Color(0x99FFFFFF),
                    fontSize = 10.sp,
                    letterSpacing = 1.2.sp,
                )
                Text(
                    version,
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
            }
            Box(
                Modifier
                    .clip(RoundedCornerShape(99.dp))
                    .background(Color(0x33FFFFFF))
                    .padding(horizontal = 10.dp, vertical = 5.dp),
            ) {
                Text(
                    uptime?.let { fmtDuration(it) } ?: "—",
                    color = Color.White,
                    fontSize = 11.sp,
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HeroMetric("CPU", cpuUsage?.let { "%.0f%%".format(it) } ?: "—", Modifier.weight(1f))
            HeroMetric("RAM", memUsage?.let { "%.0f%%".format(it) } ?: "—", Modifier.weight(1f))
            HeroMetric(
                "Phát hiện",
                detectionFps?.let { "%.0f fps".format(it) } ?: "—",
                Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun HeroMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0x1FFFFFFF))
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Text(label, color = Color(0x99FFFFFF), fontSize = 10.sp)
        Spacer(Modifier.height(2.dp))
        Text(
            value,
            color = Color.White,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

/** Thẻ dung lượng: thanh tiến trình dày gấp đôi bản cũ (14dp thay vì 7dp). */
@Composable
private fun StorageCard(
    title: String,
    used: Double,
    total: Double,
    percent: Double,
    footnote: String?,
) {
    val tint = when {
        percent >= 90 -> IOSRed
        percent >= 70 -> IOSOrange
        else -> IOSBlue
    }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(IOSCard)
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = IOSLabel,
                modifier = Modifier.weight(1f),
            )
            Text(
                "%.1f%%".format(percent),
                color = tint,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "${formatMb(used)} / ${formatMb(total)}",
            fontSize = 12.sp,
            color = IOSSecondaryLabel,
        )
        Spacer(Modifier.height(10.dp))
        LinearProgressIndicator(
            progress = { (percent / 100.0).toFloat().coerceIn(0f, 1f) },
            color = tint,
            trackColor = Color(0x26FFFFFF),
            modifier = Modifier
                .fillMaxWidth()
                .height(14.dp)
                .clip(RoundedCornerShape(99.dp)),
        )
        if (footnote != null) {
            Spacer(Modifier.height(8.dp))
            Text(footnote, fontSize = 11.sp, color = IOSSecondaryLabel)
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
            Text(
                current,
                fontSize = 12.sp,
                color = IOSSecondaryLabel,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(Modifier.height(6.dp))
        val maxValue = (values.maxOrNull() ?: 0f).coerceAtLeast(1f)
        Canvas(Modifier.fillMaxWidth().height(64.dp)) {
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
