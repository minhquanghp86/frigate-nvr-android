package com.minhquanghp86.frigatenvr.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Article
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
import com.minhquanghp86.frigatenvr.ui.fmtDuration
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull

private fun JsonObject.numOf(key: String): Double? =
    (this[key] as? JsonPrimitive)?.doubleOrNull

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    session: ServerSession,
    onOpenLogs: () -> Unit = {},
    onOpenStorage: () -> Unit = {},
) {
    val vm: SettingsViewModel = viewModel()
    val repo = vm.repo()
    val version by vm.version.collectAsState()
    val stats by vm.stats.collectAsState()
    val busy by vm.busy.collectAsState()
    val message by vm.message.collectAsState()
    val notifyEnabled by repo.notifyEnabled.collectAsState()
    val notifyCameras by repo.notifyCameras.collectAsState()
    var showCameraPicker by remember { mutableStateOf(false) }
    var allCameras by remember { mutableStateOf<List<String>>(emptyList()) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var baseUrl by remember(session.baseUrl) { mutableStateOf(session.baseUrl) }
    var username by remember(session.username) { mutableStateOf(session.username) }
    var passInput by remember { mutableStateOf("") }
    var go2rtcUrl by remember(session.go2rtcUrl) { mutableStateOf(session.go2rtcUrl.orEmpty()) }
    var keepPass by remember { mutableStateOf(session.rememberPassword) }
    var trustCert by remember { mutableStateOf(session.trustSelfSigned) }
    var confirmLogout by remember { mutableStateOf(false) }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        allCameras = runCatching { repo.configCached().enabledCameras }.getOrDefault(emptyList())
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
            subtitle = "${session.baseUrl} · ${session.role} · v${com.minhquanghp86.frigatenvr.BuildConfig.VERSION_NAME}",
        )

        LazyColumn(contentPadding = PaddingValues(bottom = 28.dp)) {
            item {
                SectionTitle("Kết nối")
                IosGroup {
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
                    IosTextField(go2rtcUrl, { go2rtcUrl = it }, "go2rtc URL (tuỳ chọn)", "http://ip:1984")
                }
            }

            item {
                SectionTitle("Bảo mật")
                IosGroup {
                    IosSwitchRow("Ghi nhớ mật khẩu", checked = keepPass, onChange = { keepPass = it })
                    IosDivider(16)
                    IosSwitchRow(
                        "Tin chứng chỉ tự ký",
                        subtitle = "Bật khi Frigate dùng HTTPS nội bộ",
                        checked = trustCert,
                        onChange = { trustCert = it },
                    )
                }
            }

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
                }
            }

            item {
                SectionTitle("Dữ liệu")
                IosGroup {
                    IosRow(
                        "Nhật ký hệ thống",
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
                }
            }

            item {
                Spacer(Modifier.height(14.dp))
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
                        .height(50.dp),
                ) {
                    if (busy) {
                        CircularProgressIndicator(Modifier.height(18.dp), strokeWidth = 2.dp)
                    }
                    Text("Lưu cài đặt", style = MaterialTheme.typography.labelLarge)
                }
                message?.let {
                    Text(
                        it,
                        color = IOSGreen,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    )
                }
            }

            item {
                SectionTitle("Hệ thống")
                IosGroup {
                    IosRow(
                        "Frigate",
                        icon = Icons.Outlined.Info,
                        value = when (val v = version) {
                            is UiState.Ok -> v.data.version ?: "—"
                            is UiState.Error -> "lỗi"
                            else -> "…"
                        },
                    )
                    stats?.let { s ->
                        val service = s["service"] as? JsonObject
                        if (service != null) {
                            IosDivider(56)
                            IosRow("Bộ nhớ", value = service.numOf("mem_usage")?.let { "%.0f%%".format(it) })
                            IosDivider(16)
                            IosRow("CPU", value = service.numOf("cpu_usage")?.let { "%.0f%%".format(it) })
                            IosDivider(16)
                            IosRow("Thời gian chạy", value = service.numOf("uptime")?.let { fmtDuration(it) })
                        }
                        IosDivider(16)
                        IosRow(
                            "Tốc độ phát hiện",
                            value = s.numOf("detection_fps")?.let { "%.1f fps".format(it) },
                        )
                    }
                }
            }

            item {
                SectionTitle("Tài khoản")
                IosGroup {
                    IosRow(
                        "Đăng xuất",
                        icon = Icons.Outlined.Logout,
                        iconTint = MaterialTheme.colorScheme.error,
                        destructive = true,
                        onClick = { confirmLogout = true },
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
                androidx.compose.foundation.layout.Column {
                    Text(
                        "Bỏ chọn hết = nhận thông báo cho tất cả camera.",
                        fontSize = 12.sp,
                        color = IOSSecondaryLabel,
                    )
                    allCameras.forEach { cam ->
                        androidx.compose.foundation.layout.Row(
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        ) {
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

    if (confirmLogout) {
        AlertDialog(
            onDismissRequest = { confirmLogout = false },
            title = { Text("Đăng xuất?") },
            text = { Text("Phiên và token sẽ bị xoá — bạn phải đăng nhập lại.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmLogout = false
                    vm.logout()
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
