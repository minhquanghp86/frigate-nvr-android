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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.minhquanghp86.frigatenvr.data.FaceAttempt
import com.minhquanghp86.frigatenvr.ui.FrigateImage
import com.minhquanghp86.frigatenvr.ui.IOSBlue
import com.minhquanghp86.frigatenvr.ui.IOSCard
import com.minhquanghp86.frigatenvr.ui.IOSGreen
import com.minhquanghp86.frigatenvr.ui.IOSLabel
import com.minhquanghp86.frigatenvr.ui.IOSOrange
import com.minhquanghp86.frigatenvr.ui.IOSRed
import com.minhquanghp86.frigatenvr.ui.IOSSecondaryLabel
import com.minhquanghp86.frigatenvr.ui.LargeTitleHeader
import com.minhquanghp86.frigatenvr.ui.rememberRepo
import kotlinx.coroutines.launch

/**
 * “Huấn luyện” khuôn mặt — tương đương tab Recent Recognitions của Frigate:
 * ảnh các lần nhận diện gần đây (faces/train), gán được cho người đã có hoặc tạo người mới.
 * Hỗ trợ CHỌN NHIỀU để thao tác hàng loạt (gán / xoá / nhận diện lại) với số lượng lớn ảnh.
 */
@Composable
fun FaceTrainScreen(onBack: () -> Unit) {
    val repo = rememberRepo()
    val scope = rememberCoroutineScope()
    var attempts by remember { mutableStateOf<List<FaceAttempt>?>(null) }
    var names by remember { mutableStateOf<List<String>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    var target by remember { mutableStateOf<FaceAttempt?>(null) }
    var nameInput by remember { mutableStateOf("") }

    var selecting by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var bulkAssign by remember { mutableStateOf(false) }
    var bulkName by remember { mutableStateOf("") }
    var confirmBulkDelete by remember { mutableStateOf(false) }

    fun reload() {
        scope.launch {
            try {
                attempts = repo.faceAttempts()
                names = repo.faces().keys.filter { it != "train" }.sorted()
            } catch (e: Exception) {
                error = e.message ?: "Không tải được ảnh nhận diện"
                attempts = emptyList()
            }
        }
    }

    LaunchedEffect(Unit) { reload() }

    fun toggle(file: String) {
        selected = if (file in selected) selected - file else selected + file
    }

    /** Gán nhiều ảnh cho một tên; tự tạo người mới nếu chưa có. */
    fun assignSelected(name: String) {
        val files = selected.toList()
        if (files.isEmpty()) return
        busy = true
        error = null
        scope.launch {
            var ok = 0
            var fail = 0
            try {
                if (name !in names) repo.createFace(name)
                files.forEachIndexed { index, file ->
                    message = "Đang gán ${index + 1}/${files.size}…"
                    try {
                        repo.trainFaceFromTrainingFile(name, file)
                        ok++
                    } catch (_: Exception) {
                        fail++
                    }
                }
                message = "Đã gán $ok/${files.size} ảnh vào $name" +
                    if (fail > 0) " ($fail lỗi)" else ""
                selected = emptySet()
                selecting = false
                bulkAssign = false
                reload()
            } catch (e: Exception) {
                error = e.message ?: "Không huấn luyện được"
            } finally {
                busy = false
            }
        }
    }

    fun deleteSelected() {
        val files = selected.toList()
        if (files.isEmpty()) return
        busy = true
        error = null
        scope.launch {
            try {
                message = "Đang xoá ${files.size} ảnh…"
                repo.deleteFaceImages("train", files)
                message = "Đã xoá ${files.size} ảnh khỏi danh sách nhận diện"
                selected = emptySet()
                selecting = false
                confirmBulkDelete = false
                reload()
            } catch (e: Exception) {
                error = e.message ?: "Không xoá được ảnh"
            } finally {
                busy = false
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        LargeTitleHeader(
            title = "Huấn luyện khuôn mặt",
            subtitle = attempts?.let {
                if (selecting) "Đã chọn ${selected.size}/${it.size}" else "${it.size} ảnh gần đây"
            } ?: "Đang tải…",
            trailing = {
                if (attempts?.isNotEmpty() == true) {
                    TextButton(
                        onClick = {
                            selecting = !selecting
                            selected = emptySet()
                        },
                    ) {
                        Text(if (selecting) "Xong" else "Chọn", color = IOSBlue)
                    }
                }
                IconButton(onClick = onBack) {
                    Icon(Icons.Outlined.ArrowBack, contentDescription = "Quay lại", tint = IOSLabel)
                }
            },
        )

        if (selecting && attempts?.isNotEmpty() == true) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = {
                        val all = attempts.orEmpty().map { it.file }.toSet()
                        selected = if (selected.size == all.size) emptySet() else all
                    },
                ) {
                    Text(
                        if (selected.size == (attempts?.size ?: 0)) "Bỏ chọn hết" else "Chọn tất cả",
                        fontSize = 12.sp,
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    "${selected.size} ảnh",
                    color = IOSSecondaryLabel,
                    fontSize = 12.sp,
                    modifier = Modifier.align(Alignment.CenterVertically),
                )
            }
        }

        error?.let {
            Text(
                it,
                color = IOSRed,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
        }
        message?.let {
            Text(
                it,
                color = IOSGreen,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
        }
        if (busy) {
            Text(
                "Đang xử lý…",
                color = IOSSecondaryLabel,
                fontSize = 11.sp,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
        }

        val data = attempts
        when {
            data == null -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                CircularProgressIndicator()
            }

            data.isEmpty() -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text(
                    "Chưa có ảnh nhận diện gần đây.\n" +
                        "Bật save_attempts trong face_recognition của Frigate để lưu ảnh.",
                    color = IOSSecondaryLabel,
                    fontSize = 13.sp,
                )
            }

            else -> Box(Modifier.weight(1f).fillMaxWidth()) {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(112.dp),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(data, key = { it.file }) { attempt ->
                        val unknown = attempt.name.equals("unknown", ignoreCase = true)
                        val checked = attempt.file in selected
                        Column(
                            Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(IOSCard)
                                .clickable {
                                    if (selecting) toggle(attempt.file) else target = attempt
                                },
                        ) {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1f)
                                    .background(Color.Black),
                            ) {
                                FrigateImage(
                                    url = repo.faceAttemptUrl(attempt.file),
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop,
                                )
                                Text(
                                    if (unknown) "chưa rõ" else attempt.name,
                                    color = Color.White,
                                    fontSize = 9.sp,
                                    maxLines = 1,
                                    modifier = Modifier
                                        .align(Alignment.TopStart)
                                        .padding(5.dp)
                                        .background(
                                            if (unknown) IOSOrange.copy(alpha = 0.9f)
                                            else IOSGreen.copy(alpha = 0.9f),
                                            RoundedCornerShape(99.dp),
                                        )
                                        .padding(horizontal = 6.dp, vertical = 2.dp),
                                )
                                Text(
                                    "${(attempt.score * 100).toInt()}%",
                                    color = Color.White,
                                    fontSize = 9.sp,
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(5.dp)
                                        .background(Color(0xAA000000), RoundedCornerShape(99.dp))
                                        .padding(horizontal = 5.dp, vertical = 2.dp),
                                )
                                if (selecting) {
                                    Icon(
                                        Icons.Filled.CheckCircle,
                                        contentDescription = if (checked) "Đã chọn" else null,
                                        tint = if (checked) IOSBlue else Color(0x66FFFFFF),
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .padding(5.dp)
                                            .size(22.dp),
                                    )
                                    if (checked) {
                                        Box(
                                            Modifier
                                                .fillMaxSize()
                                                .background(IOSBlue.copy(alpha = 0.25f)),
                                        )
                                    }
                                }
                            }
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    if (selecting) (if (checked) "Đã chọn" else "Chạm để chọn")
                                    else "Thêm",
                                    color = if (selecting && checked) IOSBlue else IOSBlue,
                                    fontSize = 12.sp,
                                    modifier = Modifier.weight(1f),
                                )
                                Text("›", color = IOSBlue, fontSize = 14.sp)
                            }
                        }
                    }
                }

                // Thanh thao tác hàng loạt (chỉ hiện khi đang chọn và có ảnh được chọn).
                if (selecting && selected.isNotEmpty()) {
                    Row(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .background(IOSCard)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "${selected.size} ảnh",
                            color = IOSLabel,
                            fontSize = 12.sp,
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedButton(
                            onClick = {
                                bulkName = ""
                                bulkAssign = true
                            },
                            enabled = !busy,
                        ) { Text("Gán cho…", fontSize = 12.sp) }
                        OutlinedButton(
                            onClick = { confirmBulkDelete = true },
                            enabled = !busy,
                        ) { Text("Xoá", fontSize = 12.sp, color = IOSRed) }
                    }
                }
            }
        }
    }

    // ---------- Gán 1 ảnh
    val current = target
    if (current != null) {
        AlertDialog(
            onDismissRequest = { target = null },
            title = { Text("Gán khuôn mặt") },
            text = {
                Column {
                    Text(
                        "Frigate đoán: ${current.name} (${(current.score * 100).toInt()}%). " +
                            "Chọn người đã đăng ký hoặc nhập tên mới — chỉ làm khi ảnh rõ và đúng là " +
                            "người đó. Ảnh mờ/nhãn sai thì nên Xoá để không làm hỏng bộ train.",
                        fontSize = 11.sp,
                        color = IOSSecondaryLabel,
                    )
                    Spacer(Modifier.height(8.dp))
                    names.forEach { name ->
                        TextButton(
                            onClick = { nameInput = name },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                (if (nameInput == name) "✓ " else "") + name.replace('_', ' '),
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
                        target = null
                        busy = true
                        scope.launch {
                            try {
                                if (name !in names) repo.createFace(name)
                                repo.trainFaceFromTrainingFile(name, current.file)
                                message = "Đã thêm ảnh vào $name"
                                reload()
                            } catch (e: Exception) {
                                error = e.message ?: "Không huấn luyện được"
                            } finally {
                                busy = false
                            }
                        }
                    },
                ) { Text("Huấn luyện") }
            },
            dismissButton = {
                Row {
                    TextButton(
                        onClick = {
                            target = null
                            busy = true
                            scope.launch {
                                try {
                                    repo.deleteFaceImages("train", listOf(current.file))
                                    message = "Đã xoá ảnh khỏi danh sách nhận diện"
                                    reload()
                                } catch (e: Exception) {
                                    error = e.message ?: "Không xoá được ảnh"
                                } finally {
                                    busy = false
                                }
                            }
                        },
                    ) { Text("Xoá ảnh", color = IOSRed) }
                    TextButton(
                        onClick = {
                            target = null
                            busy = true
                            scope.launch {
                                try {
                                    val result = repo.reprocessFace(current.file)
                                    message = result?.let {
                                        "Nhận diện lại: ${it.first} (${(it.second * 100).toInt()}%)"
                                    } ?: "Không nhận diện lại được"
                                    reload()
                                } catch (e: Exception) {
                                    error = e.message ?: "Không nhận diện lại được"
                                } finally {
                                    busy = false
                                }
                            }
                        },
                    ) { Text("Nhận diện lại", color = IOSSecondaryLabel) }
                }
            },
        )
    }

    // ---------- Gán hàng loạt
    if (bulkAssign) {
        AlertDialog(
            onDismissRequest = { bulkAssign = false },
            title = { Text("Gán ${selected.size} ảnh") },
            text = {
                Column {
                    Text(
                        "Chọn người đã đăng ký hoặc nhập tên mới. Chỉ gán ảnh rõ, đúng người " +
                            "(ảnh mờ nên Xoá thay vì gán).",
                        fontSize = 11.sp,
                        color = IOSSecondaryLabel,
                    )
                    Spacer(Modifier.height(8.dp))
                    names.forEach { name ->
                        TextButton(
                            onClick = { bulkName = name },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                (if (bulkName == name) "✓ " else "") + name.replace('_', ' '),
                                color = if (bulkName == name) IOSBlue else IOSLabel,
                            )
                        }
                    }
                    OutlinedTextField(
                        value = bulkName,
                        onValueChange = { bulkName = it },
                        label = { Text("Tên khuôn mặt", fontSize = 12.sp) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val name = bulkName.trim()
                        if (name.isBlank()) return@TextButton
                        assignSelected(name)
                    },
                    enabled = !busy,
                ) { Text("Gán tất cả") }
            },
            dismissButton = {
                TextButton(onClick = { bulkAssign = false }) { Text("Huỷ") }
            },
        )
    }

    if (confirmBulkDelete) {
        AlertDialog(
            onDismissRequest = { confirmBulkDelete = false },
            title = { Text("Xoá ${selected.size} ảnh?") },
            text = { Text("Các ảnh này bị xoá khỏi danh sách nhận diện (faces/train).") },
            confirmButton = {
                TextButton(onClick = { deleteSelected() }, enabled = !busy) {
                    Text("Xoá", color = IOSRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmBulkDelete = false }) { Text("Huỷ") }
            },
        )
    }
}
