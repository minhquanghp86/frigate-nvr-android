package com.minhquanghp86.frigatenvr.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Article
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Face
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.minhquanghp86.frigatenvr.data.ServerSession
import com.minhquanghp86.frigatenvr.notify.EventWatchService
import com.minhquanghp86.frigatenvr.ui.IOSBlue
import com.minhquanghp86.frigatenvr.ui.IOSGreen
import com.minhquanghp86.frigatenvr.ui.IOSLabel
import com.minhquanghp86.frigatenvr.ui.IOSOrange
import com.minhquanghp86.frigatenvr.ui.IOSSecondaryLabel
import com.minhquanghp86.frigatenvr.ui.IosDivider
import com.minhquanghp86.frigatenvr.ui.IosGroup
import com.minhquanghp86.frigatenvr.ui.IosRow
import com.minhquanghp86.frigatenvr.ui.IosSwitchRow
import com.minhquanghp86.frigatenvr.ui.LargeTitleHeader
import com.minhquanghp86.frigatenvr.ui.SectionTitle
import com.minhquanghp86.frigatenvr.ui.SettingsViewModel
import com.minhquanghp86.frigatenvr.ui.UiState
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    session: ServerSession,
    onOpenLogs: () -> Unit = {},
    onOpenStorage: () -> Unit = {},
    onOpenOverview: () -> Unit = {},
    onOpenPlates: () -> Unit = {},
    onOpenFaces: () -> Unit = {},
    onOpenUsers: () -> Unit = {},
    onOpenCameraConfig: () -> Unit = {},
    onOpenAlerts: () -> Unit = {},
    onOpenPrivacy: () -> Unit = {},
) {
    val vm: SettingsViewModel = viewModel()
    val repo = vm.repo()
    val version by vm.version.collectAsState()
    val busy by vm.busy.collectAsState()
    val message by vm.message.collectAsState()
    val notifyEnabled by repo.notifyEnabled.collectAsState()
    val notifyCameras by repo.notifyCameras.collectAsState()
    val notifyLabels by repo.notifyLabels.collectAsState()

    var showCameraPicker by remember { mutableStateOf(false) }
    var showLabelPicker by remember { mutableStateOf(false) }
    var allCameras by remember { mutableStateOf<List<String>>(emptyList()) }
    var allLabels by remember { mutableStateOf<List<String>>(emptyList()) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var connectionOpen by rememberSaveable { mutableStateOf(false) }
    var baseUrl by remember(session.baseUrl) { mutableStateOf(session.baseUrl) }
    var username by remember(session.username) { mutableStateOf(session.username) }
    var passInput by remember { mutableStateOf("") }
    var go2rtcUrl by remember(session.go2rtcUrl) { mutableStateOf(session.go2rtcUrl.orEmpty()) }
    var keepPass by remember { mutableStateOf(session.rememberPassword) }
    var trustCert by remember { mutableStateOf(session.trustSelfSigned) }
    var confirmLogout by remember { mutableStateOf(false) }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        allCameras = runCatching { repo.configCached().enabledCameras }.getOrDefault(emptyList())
        allLabels = runCatching { repo.labels() }.getOrDefault(emptyList())
    }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        scope.launch {
            if (granted) {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, EventWatchService::class.java),
                )
                repo.setNotifyEnabled(true)
            } else {
                repo.setNotifyEnabled(false)
            }
        }
    }

    fun setNotifications(on: Boolean) {
        if (!on) {
            context.stopService(Intent(context, EventWatchService::class.java))
            scope.launch { repo.setNotifyEnabled(false) }
            return
        }
        val needPerm = Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        if (needPerm) {
            permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            ContextCompat.startForegroundService(context, Intent(context, EventWatchService::class.java))
            scope.launch { repo.setNotifyEnabled(true) }
        }
    }

    Column(modifier.fillMaxSize()) {
        LargeTitleHeader(
            title = "Cài đặt",
            subtitle = "${session.username} · ${session.role} · ${com.minhquanghp86.frigatenvr.ui.appVersionLabel}",
        )

        LazyColumn(contentPadding = PaddingValues(bottom = 28.dp)) {
            // ---------- Tài khoản
            item {
                SectionTitle("Tài khoản")
                IosGroup {
                    IosRow(
                        "Người dùng",
                        icon = Icons.Outlined.Person,
                        value = session.username.ifBlank { "—" },
                    )
                    IosDivider(56)
                    IosRow(
                        "Vai trò",
                        icon = Icons.Outlined.Info,
                        value = if (session.role == "admin") "Quản trị viên" else "Người xem",
                    )
                    IosDivider(56)
                    IosRow(
                        "Danh sách người dùng",
                        icon = Icons.Outlined.Group,
                        chevron = true,
                        onClick = onOpenUsers,
                    )
                    IosDivider(56)
                    IosRow(
                        "Đăng xuất",
                        icon = Icons.Outlined.Logout,
                        iconTint = MaterialTheme.colorScheme.error,
                        destructive = true,
                        onClick = { confirmLogout = true },
                    )
                }
            }

            // ---------- Kết nối (thu gọn)
            item {
                SectionTitle("Kết nối")
                IosGroup {
                    val arrowRotation by animateFloatAsState(
                        targetValue = if (connectionOpen) 180f else 0f,
                        label = "arrow",
                    )
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { connectionOpen = !connectionOpen }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "Máy chủ",
                            color = IOSLabel,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            session.baseUrl,
                            color = IOSSecondaryLabel,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(end = 6.dp),
                        )
                        Icon(
                            Icons.Outlined.KeyboardArrowDown,
                            contentDescription = if (connectionOpen) "Thu gọn" else "Mở rộng",
                            tint = IOSSecondaryLabel,
                            modifier = Modifier.rotate(arrowRotation),
                        )
                    }

                    AnimatedVisibility(
                        visible = connectionOpen,
                        enter = expandVertically(),
                        exit = shrinkVertically(),
                    ) {
                        Column {
                            IosDivider(16)
                            IosTextField(baseUrl, { baseUrl = it }, "Địa chỉ server", "http://ip:8971")
                            IosDivider(16)
                            IosTextField(username, { username = it }, "Tài khoản")
                            IosDivider(16)
                            IosTextField(
                                passInput,
                                { passInput = it },
                                if (session.rememberPassword && session.password.isNotBlank()) {
                                    "Mật khẩu (đã lưu — để trống nếu không đổi)"
                                } else {
                                    "Mật khẩu"
                                },
                                password = true,
                            )
                            IosDivider(16)
                            IosTextField(
                                go2rtcUrl,
                                { go2rtcUrl = it },
                                "go2rtc URL (tuỳ chọn)",
                                "http://ip:1984",
                            )
                            IosDivider(16)
                            IosSwitchRow(
                                "Ghi nhớ mật khẩu",
                                checked = keepPass,
                                onChange = { keepPass = it },
                            )
                            IosDivider(16)
                            IosSwitchRow(
                                "Tin chứng chỉ tự ký",
                                subtitle = "Bật khi Frigate dùng HTTPS nội bộ",
                                checked = trustCert,
                                onChange = { trustCert = it },
                            )
                            Spacer(Modifier.height(10.dp))
                            Button(
                                onClick = {
                                    vm.save(
                                        baseUrl = baseUrl,
                                        username = username,
                                        password = passInput,
                                        remember = keepPass,
                                        trust = trustCert,
                                        go2rtcUrl = go2rtcUrl,
                                    )
                                    passInput = ""
                                },
                                enabled = !busy,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp)
                                    .height(48.dp),
                            ) {
                                if (busy) {
                                    CircularProgressIndicator(
                                        Modifier.height(18.dp),
                                        strokeWidth = 2.dp,
                                    )
                                }
                                Text("Lưu & kết nối", style = MaterialTheme.typography.labelLarge)
                            }
                            message?.let {
                                Text(
                                    it,
                                    color = IOSGreen,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                        }
                    }
                }
            }

            // ---------- Cấu hình Frigate
            item {
                SectionTitle("Cấu hình Frigate")
                IosGroup {
                    IosRow(
                        "Tổng quan hệ thống",
                        icon = Icons.Outlined.Insights,
                        chevron = true,
                        onClick = onOpenOverview,
                    )
                    IosDivider(56)
                    IosRow(
                        "Cảnh báo (review)",
                        icon = Icons.Outlined.NotificationsActive,
                        chevron = true,
                        onClick = onOpenAlerts,
                    )
                    IosDivider(56)
                    IosRow(
                        "Cấu hình Camera",
                        icon = Icons.Outlined.Videocam,
                        chevron = true,
                        onClick = onOpenCameraConfig,
                    )
                    IosDivider(56)
                    IosRow(
                        "Thư viện khuôn mặt",
                        icon = Icons.Outlined.Face,
                        chevron = true,
                        onClick = onOpenFaces,
                    )
                    IosDivider(56)
                    IosRow(
                        "Biển số đã nhận diện",
                        icon = Icons.Outlined.DirectionsCar,
                        chevron = true,
                        onClick = onOpenPlates,
                    )
                    IosDivider(56)
                    IosRow(
                        "Nhật ký",
                        icon = Icons.Outlined.Article,
                        chevron = true,
                        onClick = onOpenLogs,
                    )
                    IosDivider(56)
                    IosRow(
                        "Dung lượng ghi hình",
                        icon = Icons.Outlined.Storage,
                        iconTint = IOSOrange,
                        chevron = true,
                        onClick = onOpenStorage,
                    )
                    IosDivider(56)
                    IosRow(
                        "Phiên bản Frigate",
                        icon = Icons.Outlined.Info,
                        value = when (val v = version) {
                            is UiState.Ok -> v.data.version ?: "—"
                            is UiState.Error -> "lỗi"
                            else -> "…"
                        },
                    )
                }
            }

            // ---------- Thông báo
            item {
                SectionTitle("Thông báo")
                IosGroup {
                    IosSwitchRow(
                        "Thông báo sự kiện",
                        subtitle = "Chạy nền, báo khi Frigate phát hiện đối tượng",
                        checked = notifyEnabled,
                        onChange = { setNotifications(it) },
                    )
                    IosDivider(16)
                    IosRow(
                        "Camera nhận thông báo",
                        value = if (notifyCameras.isEmpty()) "Tất cả" else "${notifyCameras.size} camera",
                        chevron = true,
                        onClick = { showCameraPicker = true },
                    )
                    IosDivider(16)
                    IosRow(
                        "Nhãn nhận thông báo",
                        value = if (notifyLabels.isEmpty()) "Tất cả" else notifyLabels.joinToString(", "),
                        chevron = true,
                        onClick = { showLabelPicker = true },
                    )
                }
            }

            // ---------- Ứng dụng
            item {
                SectionTitle("Ứng dụng")
                IosGroup {
                    IosRow(
                        "Phiên bản FriCam",
                        icon = Icons.Outlined.Info,
                        value = com.minhquanghp86.frigatenvr.ui.appVersionLabel,
                    )
                    IosDivider(56)
                    IosRow(
                        "Chính sách quyền riêng tư",
                        icon = Icons.Outlined.PrivacyTip,
                        chevron = true,
                        onClick = onOpenPrivacy,
                    )
                    IosDivider(56)
                    IosRow(
                        "Mở giao diện web Frigate",
                        icon = Icons.Outlined.OpenInNew,
                        chevron = true,
                        onClick = {
                            runCatching {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse(session.baseUrl)),
                                )
                            }
                        },
                    )
                }
            }
        }
    }

    if (showCameraPicker) {
        AlertDialog(
            onDismissRequest = { showCameraPicker = false },
            title = { Text("Camera nhận thông báo") },
            text = {
                Column {
                    Text(
                        "Bỏ chọn hết = nhận thông báo cho tất cả camera.",
                        fontSize = 12.sp,
                        color = IOSSecondaryLabel,
                    )
                    allCameras.forEach { cam ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            androidx.compose.material3.Checkbox(
                                checked = cam in notifyCameras,
                                onCheckedChange = { on ->
                                    scope.launch {
                                        repo.setNotifyCameras(
                                            if (on) notifyCameras + cam else notifyCameras - cam,
                                        )
                                    }
                                },
                            )
                            Text(cam)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showCameraPicker = false }) { Text("Xong") }
            },
        )
    }

    if (showLabelPicker) {
        AlertDialog(
            onDismissRequest = { showLabelPicker = false },
            title = { Text("Nhãn nhận thông báo") },
            text = {
                Column {
                    Text(
                        "Bỏ chọn hết = nhận mọi nhãn.",
                        fontSize = 12.sp,
                        color = IOSSecondaryLabel,
                    )
                    allLabels.forEach { label ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            androidx.compose.material3.Checkbox(
                                checked = label in notifyLabels,
                                onCheckedChange = { on ->
                                    scope.launch {
                                        repo.setNotifyLabels(
                                            if (on) notifyLabels + label else notifyLabels - label,
                                        )
                                    }
                                },
                            )
                            Text(label)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLabelPicker = false }) { Text("Xong") }
            },
        )
    }

    if (confirmLogout) {
        AlertDialog(
            onDismissRequest = { confirmLogout = false },
            title = { Text("Đăng xuất?") },
            text = { Text("Phiên và token sẽ bị xoá — bạn phải đăng nhập lại.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmLogout = false
                    scope.launch { vm.logout() }
                }) { Text("Đăng xuất") }
            },
            dismissButton = {
                TextButton(onClick = { confirmLogout = false }) { Text("Huỷ") }
            },
        )
    }
}

@Composable
private fun IosTextField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    placeholder: String? = null,
    password: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label, fontSize = 13.sp) },
        placeholder = placeholder?.let { { Text(it, fontSize = 14.sp) } },
        singleLine = true,
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = if (password) KeyboardOptions(keyboardType = KeyboardType.Password) else KeyboardOptions.Default,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            unfocusedBorderColor = Color.Transparent,
            focusedBorderColor = IOSBlue,
            unfocusedLabelColor = IOSSecondaryLabel,
            focusedLabelColor = IOSBlue,
            unfocusedTextColor = IOSLabel,
            focusedTextColor = IOSLabel,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}
