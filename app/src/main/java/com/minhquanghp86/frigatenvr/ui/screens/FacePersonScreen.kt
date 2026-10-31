package com.minhquanghp86.frigatenvr.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.minhquanghp86.frigatenvr.ui.IOSCard
import com.minhquanghp86.frigatenvr.ui.FrigateImage
import com.minhquanghp86.frigatenvr.ui.IOSGreen
import com.minhquanghp86.frigatenvr.ui.IOSLabel
import com.minhquanghp86.frigatenvr.ui.IOSRed
import com.minhquanghp86.frigatenvr.ui.IOSSecondaryLabel
import com.minhquanghp86.frigatenvr.ui.LargeTitleHeader
import com.minhquanghp86.frigatenvr.ui.rememberRepo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Chi tiết một người trong thư viện khuôn mặt: xem toàn bộ ảnh đã train + quản lý. */
@Composable
fun FacePersonScreen(
    name: String,
    onBack: () -> Unit,
) {
    val repo = rememberRepo()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var files by remember { mutableStateOf<List<String>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<String?>(null) }
    var confirmDeleteAll by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }
    var moveImage by remember { mutableStateOf<String?>(null) }
    var moveTarget by remember { mutableStateOf("") }
    var renameInput by remember { mutableStateOf(name) }

    fun reload() {
        scope.launch {
            try {
                files = repo.faces()[name].orEmpty()
            } catch (e: Exception) {
                error = e.message ?: "Không tải được ảnh"
                files = emptyList()
            }
        }
    }

    val pickImage = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true
        error = null
        scope.launch {
            try {
                val bytes = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                } ?: throw IllegalStateException("Không đọc được ảnh")
                val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
                val fileName = uri.lastPathSegment?.substringAfterLast('/') ?: "face.jpg"
                repo.registerFace(name, bytes, fileName, mime)
                message = "Đã thêm ảnh"
                reload()
            } catch (e: Exception) {
                error = e.message ?: "Không thêm được ảnh"
            } finally {
                busy = false
            }
        }
    }

    LaunchedEffect(name) { reload() }

    Column(Modifier.fillMaxSize()) {
        LargeTitleHeader(
            title = name.replace('_', ' '),
            subtitle = files?.let { "${it.size} ảnh đã đăng ký" } ?: "Đang tải…",
            trailing = {
                IconButton(onClick = { pickImage.launch("image/*") }, enabled = !busy) {
                    Icon(
                        Icons.Outlined.AddPhotoAlternate,
                        contentDescription = "Thêm ảnh",
                        tint = IOSGreen,
                    )
                }
                IconButton(onClick = onBack) {
                    Icon(Icons.Outlined.ArrowBack, contentDescription = "Quay lại", tint = IOSLabel)
                }
            },
        )

        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(onClick = { pickImage.launch("image/*") }, enabled = !busy) {
                Icon(Icons.Outlined.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Thêm ảnh", fontSize = 12.sp)
            }
            OutlinedButton(
                onClick = {
                    renameInput = name
                    showRename = true
                },
                enabled = !busy,
            ) {
                Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Đổi tên", fontSize = 12.sp)
            }
            OutlinedButton(onClick = { confirmDeleteAll = true }, enabled = !busy) {
                Icon(Icons.Outlined.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Xoá người", fontSize = 12.sp)
            }
        }

        error?.let {
            Text(it, color = IOSRed, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
        }
        message?.let {
            Text(it, color = IOSGreen, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
        }
        if (busy) {
            Text(
                "Đang xử lý…",
                color = IOSSecondaryLabel,
                fontSize = 11.sp,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
        }

        val data = files
        when {
            data == null -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }

            data.isEmpty() -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text(
                    "Người này chưa có ảnh nào.",
                    color = IOSSecondaryLabel,
                    fontSize = 13.sp,
                )
            }

            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(110.dp),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(data) { file ->
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(IOSCard),
                    ) {
                        FrigateImage(
                            url = repo.url("/clips/faces/$name/$file"),
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                        Box(
                            Modifier
                                .align(Alignment.TopEnd)
                                .padding(4.dp)
                                .size(24.dp)
                                .background(Color(0xAA000000), RoundedCornerShape(99.dp))
                                .clickable { pendingDelete = file },
                            Alignment.Center,
                        ) {
                            Icon(
                                Icons.Outlined.Close,
                                contentDescription = "Xoá ảnh",
                                tint = Color.White,
                                modifier = Modifier.size(14.dp),
                            )
                        }
                    }
                }
            }
        }
    }

    moveImage?.let { file ->
        AlertDialog(
            onDismissRequest = { moveImage = null },
            title = { Text("Chuyển ảnh sang người khác") },
            text = {
                Column {
                    Text(
                        "Chỉ dùng khi ảnh rõ và thực sự là người đó. Frigate không có API " +
                            "\"không phải người này\" — ảnh mờ nên xoá thay vì chuyển.",
                        fontSize = 11.sp,
                        color = IOSSecondaryLabel,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = moveTarget,
                        onValueChange = { moveTarget = it },
                        label = { Text("Tên người đích", fontSize = 13.sp) },
                        singleLine = true,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val to = moveTarget.trim()
                        if (to.isBlank()) return@TextButton
                        moveImage = null
                        busy = true
                        scope.launch {
                            try {
                                repo.reclassifyFace(name, file, to)
                                message = "Đã chuyển ảnh sang $to"
                                reload()
                            } catch (e: Exception) {
                                error = e.message ?: "Không chuyển được ảnh"
                            } finally {
                                busy = false
                            }
                        }
                    },
                ) { Text("Chuyển") }
            },
            dismissButton = {
                TextButton(onClick = { moveImage = null }) { Text("Huỷ") }
            },
        )
    }

    pendingDelete?.let { file ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Xoá ảnh này?") },
            text = { Text("Ảnh sẽ bị xoá khỏi thư viện train của $name.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDelete = null
                        busy = true
                        scope.launch {
                            try {
                                repo.deleteFaceImages(name, listOf(file))
                                message = "Đã xoá 1 ảnh"
                                reload()
                            } catch (e: Exception) {
                                error = e.message ?: "Không xoá được ảnh"
                            } finally {
                                busy = false
                            }
                        }
                    },
                ) { Text("Xoá", color = IOSRed) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Huỷ") }
            },
        )
    }

    if (confirmDeleteAll) {
        AlertDialog(
            onDismissRequest = { confirmDeleteAll = false },
            title = { Text("Xoá toàn bộ người này?") },
            text = { Text("Toàn bộ ${files?.size ?: 0} ảnh của $name sẽ bị xoá khỏi thư viện.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDeleteAll = false
                        val all = files.orEmpty()
                        busy = true
                        scope.launch {
                            try {
                                repo.deleteFaceImages(name, all)
                                onBack()
                            } catch (e: Exception) {
                                error = e.message ?: "Không xoá được"
                                busy = false
                            }
                        }
                    },
                ) { Text("Xoá tất cả", color = IOSRed) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteAll = false }) { Text("Huỷ") }
            },
        )
    }

    if (showRename) {
        AlertDialog(
            onDismissRequest = { showRename = false },
            title = { Text("Đổi tên") },
            text = {
                OutlinedTextField(
                    value = renameInput,
                    onValueChange = { renameInput = it },
                    label = { Text("Tên mới", fontSize = 13.sp) },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val to = renameInput.trim()
                        if (to.isBlank() || to == name) return@TextButton
                        showRename = false
                        busy = true
                        scope.launch {
                            try {
                                repo.renameFace(name, to)
                                onBack()
                            } catch (e: Exception) {
                                error = e.message ?: "Không đổi tên được"
                                busy = false
                            }
                        }
                    },
                ) { Text("Lưu") }
            },
            dismissButton = {
                TextButton(onClick = { showRename = false }) { Text("Huỷ") }
            },
        )
    }
}
