package com.minhquanghp86.frigatenvr.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Face
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.minhquanghp86.frigatenvr.data.FrigateEvent
import com.minhquanghp86.frigatenvr.ui.FrigateImage
import com.minhquanghp86.frigatenvr.ui.IOSBlue
import com.minhquanghp86.frigatenvr.ui.IOSCard
import com.minhquanghp86.frigatenvr.ui.IOSGreen
import com.minhquanghp86.frigatenvr.ui.IOSLabel
import com.minhquanghp86.frigatenvr.ui.IOSOrange
import com.minhquanghp86.frigatenvr.ui.IOSSecondaryLabel
import com.minhquanghp86.frigatenvr.ui.LargeTitleHeader
import com.minhquanghp86.frigatenvr.ui.relTime
import com.minhquanghp86.frigatenvr.ui.rememberRepo
import kotlinx.coroutines.launch

/** Thẻ theo dõi khuôn mặt trên trang chủ Camera. */
@Composable
fun FaceWatchCard(
    events: List<FrigateEvent>,
    modifier: Modifier = Modifier,
    onOpenAll: () -> Unit = {},
    onOpenEvent: (String) -> Unit = {},
) {
    val repo = rememberRepo()
    var showUnknownOnly by remember { mutableStateOf<Boolean?>(null) }

    val known = events.filter { !it.isUnknownFace }
    val unknown = events.filter { it.isUnknownFace }
    val shown = when (showUnknownOnly) {
        true -> unknown
        false -> known
        null -> events
    }

    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(IOSCard)
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Face, contentDescription = null, tint = IOSBlue, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                "Khuôn mặt",
                color = IOSLabel,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.weight(1f))
            Row(
                Modifier
                    .clip(RoundedCornerShape(99.dp))
                    .clickable(onClick = onOpenAll)
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Xem tất cả", color = IOSBlue, fontSize = 12.sp)
                Icon(
                    Icons.Outlined.ChevronRight,
                    contentDescription = null,
                    tint = IOSBlue,
                    modifier = Modifier.size(16.dp),
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FaceCountChip(
                label = "Đã biết",
                count = known.size,
                tint = IOSGreen,
                selected = showUnknownOnly == false,
                onClick = { showUnknownOnly = if (showUnknownOnly == false) null else false },
            )
            FaceCountChip(
                label = "Chưa rõ",
                count = unknown.size,
                tint = IOSOrange,
                selected = showUnknownOnly == true,
                onClick = { showUnknownOnly = if (showUnknownOnly == true) null else true },
            )
        }

        Spacer(Modifier.height(10.dp))

        if (shown.isEmpty()) {
            Text(
                "Hôm nay chưa phát hiện khuôn mặt nào.",
                color = IOSSecondaryLabel,
                fontSize = 12.sp,
            )
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(shown.take(14), key = { it.id }) { event ->
                    val name = event.faceName
                    Box(
                        Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.Black)
                            .clickable { onOpenEvent(event.id) },
                    ) {
                        FrigateImage(
                            url = repo.eventSnapshot(event.id),
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                        Box(
                            Modifier
                                .align(Alignment.BottomStart)
                                .fillMaxWidth()
                                .background(Color(0xCC000000))
                                .padding(horizontal = 4.dp, vertical = 2.dp),
                        ) {
                            Text(
                                name ?: "Chưa rõ",
                                color = if (name != null) IOSGreen else IOSOrange,
                                fontSize = 9.sp,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FaceCountChip(
    label: String,
    count: Int,
    tint: Color,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .clip(RoundedCornerShape(99.dp))
            .background(if (selected) tint.copy(alpha = 0.22f) else Color(0x1FFFFFFF))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(6.dp).background(tint, RoundedCornerShape(99.dp)))
        Spacer(Modifier.width(5.dp))
        Text("$label $count", color = if (selected) tint else IOSSecondaryLabel, fontSize = 11.sp)
    }
}

/** Màn đầy đủ: tất cả sự kiện khuôn mặt, lọc đã biết/chưa rõ, gán tên cho người chưa rõ. */
@Composable
fun FaceEventsScreen(
    onBack: () -> Unit,
    onOpenEvent: (String) -> Unit = {},
) {
    val repo = rememberRepo()
    val scope = rememberCoroutineScope()
    var events by remember { mutableStateOf<List<FrigateEvent>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var filter by remember { mutableStateOf<String?>(null) }
    var showAssignFor by remember { mutableStateOf<FrigateEvent?>(null) }
    var faceNames by remember { mutableStateOf<List<String>>(emptyList()) }
    var nameInput by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }

    fun reload() {
        scope.launch {
            try {
                events = repo.faceEvents(limit = 120)
            } catch (e: Exception) {
                error = e.message ?: "Không tải được sự kiện khuôn mặt"
                events = emptyList()
            }
        }
    }

    LaunchedEffect(Unit) {
        reload()
        faceNames = runCatching { repo.faces().keys.toList() }.getOrDefault(emptyList())
    }

    Column(Modifier.fillMaxSize()) {
        LargeTitleHeader(
            title = "Sự kiện khuôn mặt",
            subtitle = events?.let { "${it.size} sự kiện gần đây" } ?: "Đang tải…",
            trailing = {
                IconButton(onClick = onBack) {
                    Icon(Icons.Outlined.ArrowBack, contentDescription = "Quay lại", tint = IOSLabel)
                }
            },
        )

        error?.let {
            Text(it, color = IOSOrange, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 20.dp))
        }
        message?.let {
            Text(it, color = IOSGreen, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 20.dp))
        }

        val data = events
        if (data == null) {
            Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
            return@Column
        }

        val known = data.filter { !it.isUnknownFace }
        val unknown = data.filter { it.isUnknownFace }
        val shown = when (filter) {
            "known" -> known
            "unknown" -> unknown
            else -> data
        }

        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = filter == null,
                onClick = { filter = null },
                label = { Text("Tất cả ${data.size}", fontSize = 12.sp) },
            )
            FilterChip(
                selected = filter == "known",
                onClick = { filter = "known" },
                label = { Text("Đã biết ${known.size}", fontSize = 12.sp) },
            )
            FilterChip(
                selected = filter == "unknown",
                onClick = { filter = "unknown" },
                label = { Text("Chưa rõ ${unknown.size}", fontSize = 12.sp) },
            )
        }

        if (shown.isEmpty()) {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text(
                    "Không có sự kiện khuôn mặt trong mục này.",
                    color = IOSSecondaryLabel,
                    fontSize = 13.sp,
                )
            }
            return@Column
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(150.dp),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(shown, key = { it.id }) { event ->
                val name = event.faceName
                Column(
                    Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(IOSCard)
                        .clickable { showAssignFor = event },
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .background(Color.Black),
                    ) {
                        FrigateImage(
                            url = repo.eventSnapshot(event.id),
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                        Text(
                            name ?: "Chưa rõ",
                            color = Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(6.dp)
                                .background(
                                    if (name != null) {
                                        IOSGreen.copy(alpha = 0.85f)
                                    } else {
                                        IOSOrange.copy(alpha = 0.9f)
                                    },
                                    RoundedCornerShape(99.dp),
                                )
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                    Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                        Text(
                            name ?: "Khuôn mặt chưa rõ",
                            color = IOSLabel,
                            fontSize = 13.sp,
                            maxLines = 1,
                        )
                        Text(
                            "${event.camera} · ${relTime(event.startTime)}",
                            color = IOSSecondaryLabel,
                            fontSize = 10.sp,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }

    val target = showAssignFor
    if (target != null) {
        AlertDialog(
            onDismissRequest = { showAssignFor = null },
            title = { Text(if (target.isUnknownFace) "Khuôn mặt chưa rõ" else "Khuôn mặt: ${target.faceName}") },
            text = {
                Column {
                    Text(
                        "${target.camera} · ${relTime(target.startTime)}",
                        fontSize = 11.sp,
                        color = IOSSecondaryLabel,
                    )
                    Spacer(Modifier.height(8.dp))
                    TextButton(
                        onClick = {
                            showAssignFor = null
                            onOpenEvent(target.id)
                        },
                    ) { Text("Mở sự kiện") }
                    Text(
                        "Gán tên (train khuôn mặt từ ảnh sự kiện):",
                        fontSize = 11.sp,
                        color = IOSSecondaryLabel,
                    )
                    faceNames.forEach { name ->
                        TextButton(
                            onClick = { nameInput = name },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                (if (nameInput == name) "✓ " else "") + name,
                                color = if (nameInput == name) IOSBlue else IOSLabel,
                            )
                        }
                    }
                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = { nameInput = it },
                        label = { Text("Tên khuôn mặt", fontSize = 12.sp) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val name = nameInput.trim()
                        if (name.isBlank()) return@TextButton
                        showAssignFor = null
                        scope.launch {
                            try {
                                repo.trainFaceFromEvent(name, target.id)
                                message = "Đã gán vào $name"
                                reload()
                                faceNames = runCatching { repo.faces().keys.toList() }
                                    .getOrDefault(faceNames)
                            } catch (e: Exception) {
                                error = e.message ?: "Không train được khuôn mặt"
                            }
                        }
                    },
                ) { Text("Train") }
            },
            dismissButton = {
                TextButton(onClick = { showAssignFor = null }) { Text("Đóng") }
            },
        )
    }
}
