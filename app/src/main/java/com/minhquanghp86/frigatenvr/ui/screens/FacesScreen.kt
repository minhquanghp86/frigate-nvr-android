package com.minhquanghp86.frigatenvr.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.minhquanghp86.frigatenvr.ui.FrigateImage
import com.minhquanghp86.frigatenvr.ui.IOSBlue
import com.minhquanghp86.frigatenvr.ui.IOSGreen
import com.minhquanghp86.frigatenvr.ui.IOSLabel
import com.minhquanghp86.frigatenvr.ui.IOSRed
import com.minhquanghp86.frigatenvr.ui.IOSSecondaryLabel
import com.minhquanghp86.frigatenvr.ui.LargeTitleHeader
import com.minhquanghp86.frigatenvr.ui.rememberRepo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun FacesScreen(onBack: () -> Unit) {
    val repo = rememberRepo()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var faces by remember { mutableStateOf<Map<String, List<String>>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    var showCreate by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var selectedName by remember { mutableStateOf<String?>(null) }
    var pendingName by remember { mutableStateOf<String?>(null) }

    fun reload() {
        scope.launch {
            try {
                faces = repo.faces()
            } catch (e: Exception) {
                error = e.message ?: "Không tải được thư viện khuôn mặt"
                faces = emptyMap()
            }
        }
    }

    val pickImage = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri ->
        val name = pendingName ?: return@rememberLauncherForActivityResult
        pendingName = null
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
                message = "Đã thêm ảnh cho $name"
                reload()
            } catch (e: Exception) {
                error = e.message ?: "Không thêm được ảnh"
            } finally {
                busy = false
            }
        }
    }

    LaunchedEffect(Unit) { reload() }

    Column(Modifier.fillMaxSize()) {
        LargeTitleHeader(
            title = "Khuôn mặt",
            subtitle = faces?.let { "${it.size} người đã đăng ký" } ?: "Đang tải…",
            trailing = {
                IconButton(
                    onClick = {
                        newName = ""
                        showCreate = true
                    },
                ) {
                    Icon(Icons.Outlined.PersonAdd, contentDescription = "Thêm người", tint = IOSBlue)
                }
                IconButton(onClick = onBack) {
                    Icon(Icons.Outlined.ArrowBack, contentDescription = "Quay lại", tint = IOSLabel)
                }
            },
        )

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

        val data = faces
        when {
            data == null -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                CircularProgressIndicator()
            }

            data.isEmpty() -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text(
                    "Chưa có khuôn mặt nào.\nBật Face Recognition trong Frigate rồi bấm + để thêm người/ảnh.",
                    color = IOSSecondaryLabel,
                    fontSize = 13.sp,
                )
            }

            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(100.dp),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(data.keys.toList()) { name ->
                    val files = data[name].orEmpty()
                    val file = files.firstOrNull()
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable { selectedName = name },
                    ) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C1E)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f),
                        ) {
                            if (file != null) {
                                FrigateImage(
                                    url = repo.url("/clips/faces/$name/$file"),
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop,
                                )
                            }
                        }
                        Text(
                            name.replace('_', ' '),
                            color = IOSLabel,
                            fontSize = 12.sp,
                            maxLines = 1,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                        Text(
                            "${files.size} ảnh",
                            color = IOSSecondaryLabel,
                            fontSize = 10.sp,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }

    if (showCreate) {
        AlertDialog(
            onDismissRequest = { showCreate = false },
            title = { Text("Thêm người") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("Tên (vd. Nguyen Van A)", fontSize = 13.sp) },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val name = newName.trim()
                        if (name.isBlank()) return@TextButton
                        showCreate = false
                        scope.launch {
                            try {
                                repo.createFace(name)
                                message = "Đã tạo $name"
                                reload()
                            } catch (e: Exception) {
                                error = e.message ?: "Không tạo được"
                            }
                        }
                    },
                ) { Text("Tạo") }
            },
            dismissButton = {
                TextButton(onClick = { showCreate = false }) { Text("Huỷ") }
            },
        )
    }

    val current = selectedName
    if (current != null) {
        val files = faces?.get(current).orEmpty()
        AlertDialog(
            onDismissRequest = { selectedName = null },
            title = { Text(current.replace('_', ' ')) },
            text = {
                Column {
                    Text("${files.size} ảnh đã đăng ký", fontSize = 12.sp, color = IOSSecondaryLabel)
                    Spacer(Modifier.height(8.dp))
                    TextButton(
                        onClick = {
                            pendingName = current
                            selectedName = null
                            pickImage.launch("image/*")
                        },
                    ) { Text("Thêm ảnh từ thư viện") }
                    TextButton(
                        onClick = {
                            selectedName = null
                            busy = true
                            scope.launch {
                                try {
                                    repo.deleteFaceImages(current, files)
                                    message = "Đã xoá $current"
                                    reload()
                                } catch (e: Exception) {
                                    error = e.message ?: "Không xoá được"
                                } finally {
                                    busy = false
                                }
                            }
                        },
                    ) { Text("Xoá người này", color = IOSRed) }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedName = null }) { Text("Đóng") }
            },
        )
    }
}
