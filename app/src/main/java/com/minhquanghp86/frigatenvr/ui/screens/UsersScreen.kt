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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.minhquanghp86.frigatenvr.data.FrigateUser
import com.minhquanghp86.frigatenvr.ui.IOSBlue
import com.minhquanghp86.frigatenvr.ui.IOSCard
import com.minhquanghp86.frigatenvr.ui.IOSGreen
import com.minhquanghp86.frigatenvr.ui.IOSLabel
import com.minhquanghp86.frigatenvr.ui.IOSRed
import com.minhquanghp86.frigatenvr.ui.IOSSecondaryLabel
import com.minhquanghp86.frigatenvr.ui.LargeTitleHeader
import com.minhquanghp86.frigatenvr.ui.rememberRepo
import kotlinx.coroutines.launch

@Composable
fun UsersScreen(
    currentUsername: String,
    onBack: () -> Unit,
) {
    val repo = rememberRepo()
    val scope = rememberCoroutineScope()
    var users by remember { mutableStateOf<List<FrigateUser>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }

    var showCreate by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<FrigateUser?>(null) }
    var showActions by remember { mutableStateOf(false) }
    var showRole by remember { mutableStateOf(false) }
    var showPassword by remember { mutableStateOf(false) }

    var newUsername by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var newRole by remember { mutableStateOf("viewer") }
    var passwordInput by remember { mutableStateOf("") }

    fun reload() {
        scope.launch {
            users = runCatching { repo.users() }.getOrDefault(emptyList())
        }
    }

    fun runAction(successMessage: String, block: suspend () -> Unit) {
        error = null
        scope.launch {
            try {
                block()
                message = successMessage
                reload()
            } catch (e: Exception) {
                error = e.message ?: "Không thực hiện được"
            }
        }
    }

    LaunchedEffect(Unit) { reload() }

    Column(Modifier.fillMaxSize()) {
        LargeTitleHeader(
            title = "Người dùng",
            subtitle = users?.let { "${it.size} tài khoản" } ?: "Đang tải…",
            trailing = {
                IconButton(
                    onClick = {
                        newUsername = ""
                        newPassword = ""
                        newRole = "viewer"
                        showCreate = true
                    },
                ) {
                    Icon(Icons.Outlined.PersonAdd, contentDescription = "Thêm người dùng", tint = IOSBlue)
                }
                IconButton(onClick = onBack) {
                    Icon(Icons.Outlined.ArrowBack, contentDescription = "Quay lại", tint = IOSLabel)
                }
            },
        )

        val data = users
        if (data == null) {
            Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
            return@Column
        }

        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            error?.let { text ->
                item {
                    Text(
                        text,
                        color = IOSRed,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
            }
            message?.let { text ->
                item {
                    Text(
                        text,
                        color = IOSGreen,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
            }
            if (data.isEmpty()) {
                item {
                    Text(
                        "Không có dữ liệu. Cần tài khoản admin để xem và quản lý người dùng.",
                        color = IOSSecondaryLabel,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
            }
            items(data) { user ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(IOSCard)
                        .clickable {
                            selected = user
                            showActions = true
                        }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            user.username + if (user.username == currentUsername) " (bạn)" else "",
                            color = IOSLabel,
                            fontSize = 15.sp,
                        )
                        Text(
                            if (user.role == "admin") "Quản trị viên" else "Người xem",
                            color = IOSSecondaryLabel,
                            fontSize = 11.sp,
                        )
                    }
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(99.dp))
                            .background(
                                if (user.role == "admin") {
                                    IOSBlue.copy(alpha = 0.18f)
                                } else {
                                    IOSGreen.copy(alpha = 0.16f)
                                },
                            )
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            user.role,
                            color = if (user.role == "admin") IOSBlue else IOSGreen,
                            fontSize = 10.sp,
                        )
                    }
                    Spacer(Modifier.width(2.dp))
                }
            }
        }
    }

    // ---------- Tạo người dùng
    if (showCreate) {
        AlertDialog(
            onDismissRequest = { showCreate = false },
            title = { Text("Thêm người dùng") },
            text = {
                Column {
                    OutlinedTextField(
                        value = newUsername,
                        onValueChange = { newUsername = it },
                        label = { Text("Tên đăng nhập", fontSize = 13.sp) },
                        singleLine = true,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newPassword,
                        onValueChange = { newPassword = it },
                        label = { Text("Mật khẩu (≥ 12 ký tự)", fontSize = 13.sp) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = newRole == "viewer",
                            onClick = { newRole = "viewer" },
                            label = { Text("Người xem", fontSize = 12.sp) },
                        )
                        FilterChip(
                            selected = newRole == "admin",
                            onClick = { newRole = "admin" },
                            label = { Text("Quản trị", fontSize = 12.sp) },
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val name = newUsername.trim()
                        if (name.isBlank() || newPassword.length < 12) {
                            error = "Tên không được trống và mật khẩu phải ≥ 12 ký tự"
                            return@TextButton
                        }
                        showCreate = false
                        runAction("Đã tạo người dùng $name") {
                            repo.createUser(name, newPassword, newRole)
                        }
                    },
                ) { Text("Tạo") }
            },
            dismissButton = {
                TextButton(onClick = { showCreate = false }) { Text("Huỷ") }
            },
        )
    }

    // ---------- Hành động cho người dùng đã chọn
    val target = selected
    if (showActions && target != null) {
        AlertDialog(
            onDismissRequest = { showActions = false },
            title = { Text(target.username) },
            text = {
                Column {
                    TextButton(
                        onClick = {
                            showActions = false
                            showRole = true
                        },
                    ) { Text("Đổi vai trò hiện tại: ${target.role}") }
                    TextButton(
                        onClick = {
                            passwordInput = ""
                            showActions = false
                            showPassword = true
                        },
                    ) { Text("Đặt mật khẩu mới") }
                    if (target.username != "admin") {
                        TextButton(
                            onClick = {
                                showActions = false
                                runAction("Đã xoá ${target.username}") {
                                    repo.deleteUser(target.username)
                                }
                            },
                        ) { Text("Xoá người dùng", color = IOSRed) }
                    } else {
                        Text(
                            "Không thể xoá tài khoản admin mặc định.",
                            fontSize = 11.sp,
                            color = IOSSecondaryLabel,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showActions = false }) { Text("Đóng") }
            },
        )
    }

    if (showRole && target != null) {
        AlertDialog(
            onDismissRequest = { showRole = false },
            title = { Text("Vai trò của ${target.username}") },
            text = {
                Column {
                    FilterChip(
                        selected = target.role == "viewer",
                        onClick = {
                            showRole = false
                            runAction("Đã đổi vai trò") { repo.setUserRole(target.username, "viewer") }
                        },
                        label = { Text("Người xem") },
                    )
                    Spacer(Modifier.height(6.dp))
                    FilterChip(
                        selected = target.role == "admin",
                        onClick = {
                            showRole = false
                            runAction("Đã đổi vai trò") { repo.setUserRole(target.username, "admin") }
                        },
                        label = { Text("Quản trị viên") },
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showRole = false }) { Text("Đóng") }
            },
        )
    }

    if (showPassword && target != null) {
        AlertDialog(
            onDismissRequest = { showPassword = false },
            title = { Text("Mật khẩu mới cho ${target.username}") },
            text = {
                OutlinedTextField(
                    value = passwordInput,
                    onValueChange = { passwordInput = it },
                    label = { Text("Mật khẩu (≥ 12 ký tự)", fontSize = 13.sp) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (passwordInput.length < 12) {
                            error = "Mật khẩu phải ≥ 12 ký tự"
                            return@TextButton
                        }
                        showPassword = false
                        runAction("Đã đổi mật khẩu") {
                            repo.setUserPassword(target.username, passwordInput)
                        }
                    },
                ) { Text("Lưu") }
            },
            dismissButton = {
                TextButton(onClick = { showPassword = false }) { Text("Huỷ") }
            },
        )
    }
}
