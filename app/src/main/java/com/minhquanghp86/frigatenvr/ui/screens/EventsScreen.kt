package com.minhquanghp86.frigatenvr.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.OutlinedTextField
import com.minhquanghp86.frigatenvr.ui.IOSBlue
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import com.minhquanghp86.frigatenvr.data.MediaSaver
import com.minhquanghp86.frigatenvr.ui.IOSCard
import com.minhquanghp86.frigatenvr.ui.IOSRed
import com.minhquanghp86.frigatenvr.ui.IOSSecondaryLabel
import kotlinx.coroutines.launch
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Videocam
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.minhquanghp86.frigatenvr.data.FrigateEvent
import com.minhquanghp86.frigatenvr.ui.EventsViewModel
import com.minhquanghp86.frigatenvr.ui.ErrorBox
import com.minhquanghp86.frigatenvr.ui.FrigateImage
import com.minhquanghp86.frigatenvr.ui.LoadingBox
import com.minhquanghp86.frigatenvr.ui.ScreenHeader
import com.minhquanghp86.frigatenvr.ui.UiState
import com.minhquanghp86.frigatenvr.ui.fmtDateTime
import com.minhquanghp86.frigatenvr.ui.fmtDuration
import com.minhquanghp86.frigatenvr.ui.pct
import com.minhquanghp86.frigatenvr.ui.rememberRepo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collect

private val LABEL_OPTIONS =
    listOf(null, "person", "car", "truck", "bicycle", "motorcycle", "dog", "cat", "bird")

private fun labelLabel(value: String?): String = value ?: "Tất cả"

@Composable
fun EventsScreen(
    modifier: Modifier = Modifier,
    onOpenEvent: (String) -> Unit,
    showHeader: Boolean = true,
) {
    val vm: EventsViewModel = viewModel()
    val items by vm.items.collectAsState()
    val loading by vm.loading.collectAsState()
    val error by vm.error.collectAsState()
    val hasMore by vm.hasMore.collectAsState()
    val camera by vm.camera.collectAsState()
    val label by vm.label.collectAsState()
    val apiLabels by vm.labels.collectAsState()
    val zones by vm.zones.collectAsState()
    val zone by vm.zone.collectAsState()
    val rangeHours by vm.rangeHours.collectAsState()
    val onlyClip by vm.onlyWithClip.collectAsState()
    val labelOptions = remember(apiLabels) {
        listOf<String?>(null) + (apiLabels.ifEmpty { LABEL_OPTIONS.filterNotNull() })
    }
    val gridState = rememberLazyGridState()

    LaunchedEffect(gridState, items.size) {
        if (items.isEmpty()) return@LaunchedEffect
        snapshotFlowOfEnd(gridState, items.size) {
            if (hasMore && !loading) vm.loadMore()
        }
    }

    Column(modifier.fillMaxSize()) {
        if (showHeader) ScreenHeader(
            title = "Sự kiện",
            subtitle = "${items.size} sự kiện" + if (onlyClip) " (chỉ có clip)" else "",
            actions = {
                IconButton(onClick = { vm.refresh() }) {
                    Icon(Icons.Outlined.Refresh, contentDescription = "Tải lại")
                }
            },
        )

        var searchInput by remember { mutableStateOf("") }
        val pendingQuery by com.minhquanghp86.frigatenvr.ui.SearchStore.pending.collectAsState()
        LaunchedEffect(pendingQuery) {
            val q = com.minhquanghp86.frigatenvr.ui.SearchStore.consume() ?: return@LaunchedEffect
            searchInput = q
            vm.setQuery(q)
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = searchInput,
                onValueChange = { searchInput = it },
                placeholder = { Text("Tìm sự kiện (AI)…", fontSize = 14.sp) },
                singleLine = true,
                shape = RoundedCornerShape(99.dp),
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { vm.setQuery(searchInput) }) {
                Icon(Icons.Outlined.Search, contentDescription = "Tìm", tint = IOSBlue)
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AssistChip(
                onClick = {
                    val options = listOf<String?>(null) + vm.cameras.value
                    val idx = options.indexOf(camera)
                    vm.setCamera(options[(idx + 1) % options.size])
                },
                label = {
                    Text(
                        camera ?: "Camera: tất cả",
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
            )
            AssistChip(
                onClick = {
                    val idx = labelOptions.indexOf(label)
                    vm.setLabel(labelOptions[(idx + 1) % labelOptions.size])
                },
                label = { Text("Nhãn: ${labelLabel(label)}", fontSize = 12.sp) },
            )
            AssistChip(
                onClick = { vm.setOnlyWithClip(!onlyClip) },
                label = { Text(if (onlyClip) "Có clip ✓" else "Có clip", fontSize = 12.sp) },
            )
            val ranges = listOf<Int?>(null, 1, 24, 168)
            AssistChip(
                onClick = {
                    val idx = ranges.indexOf(rangeHours)
                    vm.setRange(ranges[(idx + 1) % ranges.size])
                },
                label = {
                    Text(
                        when (rangeHours) {
                            null -> "Mọi lúc"
                            1 -> "1 giờ"
                            24 -> "24 giờ"
                            168 -> "7 ngày"
                            else -> "${rangeHours}h"
                        },
                        fontSize = 12.sp,
                    )
                },
            )
            if (zones.isNotEmpty()) {
                AssistChip(
                    onClick = {
                        val options = listOf<String?>(null) + zones
                        val idx = options.indexOf(zone)
                        vm.setZone(options[(idx + 1) % options.size])
                    },
                    label = { Text(zone ?: "Mọi vùng", fontSize = 12.sp) },
                )
            }
        }

        when {
            loading && items.isEmpty() -> LoadingBox()
            error != null && items.isEmpty() -> ErrorBox(error!!, onRetry = { vm.refresh() })
            items.isEmpty() -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text(
                    "Không có sự kiện nào khớp bộ lọc.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            else -> {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 150.dp),
                    state = gridState,
                    contentPadding = PaddingValues(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(items, key = { it.id }) { event ->
                        EventCard(event, onClick = { onOpenEvent(event.id) })
                    }
                    if (hasMore) {
                        item {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (loading) {
                                    Text(
                                        "Đang tải tiếp...",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 12.sp,
                                    )
                                } else {
                                    OutlinedButton(onClick = { vm.loadMore() }) {
                                        Text("Tải thêm", fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }
                if (error != null) {
                    Text(
                        error ?: "",
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    )
                }
            }
        }
    }
}

/** Gọi onEnd khi gần cuối lưới (tránh import thêm snapshotFlow trực tiếp). */
private suspend fun snapshotFlowOfEnd(
    state: LazyGridState,
    total: Int,
    onEnd: () -> Unit,
) {
    androidx.compose.runtime.snapshotFlow {
        state.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
    }.collect { last ->
        if (total > 0 && last >= total - 6) onEnd()
    }
}

@Composable
private fun EventCard(event: FrigateEvent, onClick: () -> Unit) {
    val repo = rememberRepo()
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = IOSCard),
        shape = RoundedCornerShape(10.dp),
    ) {
        Column {
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(3f / 2f)
                    .background(Color(0xFF000000)),
            ) {
                if (event.hasSnapshot) {
                    FrigateImage(
                        url = repo.eventThumbnail(event.id),
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Box(Modifier.fillMaxSize(), Alignment.Center) {
                        Text("Không có ảnh", color = Color(0xFF888888), fontSize = 11.sp)
                    }
                }
                if (event.hasClip) {
                    Icon(
                        Icons.Outlined.Videocam,
                        contentDescription = "Có clip",
                        tint = Color.White,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(6.dp)
                            .background(
                                Color(0xAA111111),
                                RoundedCornerShape(4.dp),
                            )
                            .padding(4.dp)
                            .size(14.dp),
                    )
                }
            }
            Column(Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                Text(
                    event.label + (event.subLabelDisplay?.let { " · $it" } ?: ""),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${event.camera} · ${fmtDateTime(event.startTime)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (event.bestScore > 0) {
                    Text(
                        "Điểm ${pct(event.bestScore)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

@Composable
fun EventDetailScreen(
    modifier: Modifier = Modifier,
    eventId: String,
    onBack: () -> Unit,
    cameraHint: String? = null,
    onOpenCamera: (String) -> Unit = {},
    onOpenEvent: (String) -> Unit = {},
    onFullscreen: (String, Long) -> Unit = { _, _ -> },
) {
    val repo = rememberRepo()
    var event by remember(eventId) { mutableStateOf<FrigateEvent?>(null) }
    var error by remember(eventId) { mutableStateOf<String?>(null) }
    var showClip by remember(eventId) { mutableStateOf(true) }
    var similar by remember(eventId) { mutableStateOf<List<FrigateEvent>?>(null) }
    var loadingSimilar by remember(eventId) { mutableStateOf(false) }
    val similarScope = androidx.compose.runtime.rememberCoroutineScope()

    LaunchedEffect(eventId) {
        event = null
        error = null
        event = try {
            repo.call { event(eventId) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.message ?: "Không tải được sự kiện."
            null
        }
    }

    Column(modifier.fillMaxSize()) {
        ScreenHeader(
            title = event?.label?.ifBlank { "Sự kiện" } ?: "Sự kiện",
            subtitle = event?.camera?.takeIf { it.isNotBlank() } ?: eventId,
            actions = {
                IconButton(onClick = onBack) {
                    Icon(Icons.Outlined.ArrowBack, contentDescription = "Quay lại")
                }
            },
        )

        val current = event
        when {
            current == null && error == null -> LoadingBox()
            current == null -> Column(
                Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                ErrorBox(error ?: "Không có dữ liệu", onRetry = onBack)
                if (cameraHint != null) {
                    OutlinedButton(onClick = { onOpenCamera(cameraHint) }) {
                        Text("Mở live camera $cameraHint")
                    }
                }
            }
            else -> LazyColumn(
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF000000)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (current.hasClip && showClip) {
                            com.minhquanghp86.frigatenvr.ui.VideoPlayer(
                                url = repo.eventClip(current.id),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(16f / 9f),
                                onFullscreen = { onFullscreen(repo.eventClip(current.id), 0L) },
                            )
                        } else if (current.hasSnapshot) {
                            FrigateImage(
                                url = repo.eventSnapshot(current.id),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(16f / 9f),
                                contentScale = ContentScale.Fit,
                            )
                        } else {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(180.dp),
                                Alignment.Center,
                            ) {
                                Text("Sự kiện không có ảnh/clip", color = Color(0xFF888888))
                            }
                        }
                    }
                }

                if (current.hasClip && current.hasSnapshot) {
                    item {
                        OutlinedButton(onClick = { showClip = !showClip }) {
                            Text(if (showClip) "Xem ảnh chụp" else "Xem clip")
                        }
                    }
                }

                item {
                    InfoCard(
                        rows = listOf(
                            "Camera" to current.camera,
                            "Đối tượng" to current.label,
                            "Nhãn phụ" to (current.subLabelDisplay ?: "—"),
                            "Điểm" to if (current.bestScore > 0) pct(current.bestScore) else "—",
                            "Bắt đầu" to fmtDateTime(current.startTime),
                            "Kết thúc" to (current.endTime?.let { fmtDateTime(it) } ?: "đang diễn ra"),
                            "Thời lượng" to (current.endTime?.let {
                                fmtDuration((it - current.startTime).coerceAtLeast(0.0))
                            } ?: "—"),
                            "Vùng" to (current.zones.joinToString().ifBlank { "—" }),
                            "Thuộc tính" to (current.attributes.joinToString().ifBlank { "—" }),
                        ),
                    )
                }

                item { EventActions(current, onBack = onBack) }

                item {
                    if (similar == null) {
                        OutlinedButton(
                            onClick = {
                                loadingSimilar = true
                                similarScope.launch {
                                    similar = runCatching {
                                        repo.similarEvents(current.id).filter { it.id != current.id }
                                    }.getOrDefault(emptyList())
                                    loadingSimilar = false
                                }
                            },
                            enabled = !loadingSimilar,
                        ) {
                            Text(if (loadingSimilar) "Đang tìm…" else "Tìm sự kiện tương tự")
                        }
                    } else {
                        val list = similar.orEmpty()
                        Text(
                            if (list.isEmpty()) {
                                "Không có sự kiện tương tự"
                            } else {
                                "Sự kiện tương tự (${list.size})"
                            },
                            color = Color(0xFF8E8E93),
                            fontSize = 12.sp,
                        )
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            items(list, key = { it.id }) { item ->
                                Column(
                                    Modifier
                                        .width(128.dp)
                                        .clickable { onOpenEvent(item.id) },
                                ) {
                                    Card(
                                        colors = CardDefaults.cardColors(
                                            containerColor = Color(0xFF1C1C1E),
                                        ),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .aspectRatio(16f / 9f),
                                    ) {
                                        FrigateImage(
                                            url = repo.eventSnapshot(item.id),
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop,
                                        )
                                    }
                                    Text(
                                        item.label.ifBlank { "sự kiện" },
                                        fontSize = 11.sp,
                                        color = Color(0xFFEBEBF5),
                                        maxLines = 1,
                                    )
                                    Text(
                                        fmtDateTime(item.startTime),
                                        fontSize = 10.sp,
                                        color = Color(0xFF8E8E93),
                                        maxLines = 1,
                                    )
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
fun InfoCard(rows: List<Pair<String, String>>, modifier: Modifier = Modifier) {
    Card(
        colors = CardDefaults.cardColors(containerColor = IOSCard),
        shape = RoundedCornerShape(10.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp)) {
            rows.forEach { (key, value) ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp),
                ) {
                    Text(
                        key,
                        modifier = Modifier.width(110.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        value,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}


@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun EventActions(event: FrigateEvent, onBack: () -> Unit) {
    val repo = rememberRepo()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var retained by remember(event.id) { mutableStateOf(event.retained) }
    var message by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
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

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        androidx.compose.foundation.layout.BoxWithConstraints {
            val perRow = if (maxWidth < 340.dp) 2 else 4
            androidx.compose.foundation.layout.FlowRow(
                modifier = Modifier.fillMaxWidth(),
                maxItemsInEachRow = perRow,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
            OutlinedButton(
                onClick = {
                    scope.launch {
                        busy = true
                        try {
                            repo.setRetained(event.id, !retained)
                            retained = !retained
                            message = if (retained) "Đã giữ sự kiện vĩnh viễn" else "Đã bỏ giữ"
                        } catch (e: Exception) {
                            message = e.message ?: "Không đổi được trạng thái giữ"
                        } finally {
                            busy = false
                        }
                    }
                },
                enabled = !busy,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 4.dp),
            ) {
                Icon(
                    if (retained) Icons.Outlined.Bookmark else Icons.Outlined.BookmarkBorder,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(if (retained) "Bỏ giữ" else "Giữ", fontSize = 12.sp)
            }

            if (event.hasClip) {
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            busy = true
                            ensureNotifyPermission()
                            message = "Đang tải…"
                            val saved = MediaSaver.save(
                                context = context,
                                client = repo.okhttp(),
                                url = repo.eventClip(event.id),
                                fileName = "frigate-${event.id}.mp4",
                                mime = "video/mp4",
                                onProgress = { percent ->
                                    message = if (percent == null) "Đang tải…" else "Đang tải… $percent%"
                                },
                            )
                            message = saved?.let { "Đã lưu: $it" } ?: "Không tải được clip"
                            busy = false
                        }
                    },
                    enabled = !busy,
                ) {
                    Icon(Icons.Outlined.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Clip", fontSize = 12.sp)
                }
            }

            if (event.hasSnapshot) {
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            busy = true
                            ensureNotifyPermission()
                            message = "Đang tải…"
                            val saved = MediaSaver.save(
                                context = context,
                                client = repo.okhttp(),
                                url = repo.eventSnapshot(event.id),
                                fileName = "frigate-${event.id}.jpg",
                                mime = "image/jpeg",
                                onProgress = { percent ->
                                    message = if (percent == null) "Đang tải…" else "Đang tải… $percent%"
                                },
                            )
                            message = saved?.let { "Đã lưu: $it" } ?: "Không tải được ảnh"
                            busy = false
                        }
                    },
                    enabled = !busy,
                ) {
                    Icon(Icons.Outlined.Image, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Ảnh", fontSize = 12.sp)
                }
            }

            OutlinedButton(onClick = { confirmDelete = true }, enabled = !busy) {
                Icon(Icons.Outlined.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Xoá", fontSize = 12.sp)
            }
            }
        }

        message?.let {
            Text(it, fontSize = 12.sp, color = IOSSecondaryLabel)
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Xoá sự kiện?") },
            text = { Text("Sự kiện và clip/snapshot sẽ bị xoá khỏi Frigate.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    scope.launch {
                        busy = true
                        try {
                            repo.deleteEvent(event.id)
                            onBack()
                        } catch (e: Exception) {
                            message = e.message ?: "Không xoá được sự kiện"
                            busy = false
                        }
                    }
                }) { Text("Xoá", color = IOSRed) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Huỷ") }
            },
        )
    }
}
