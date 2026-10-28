package com.minhquanghp86.frigatenvr.ui.screens

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.minhquanghp86.frigatenvr.data.ServerSession
import com.minhquanghp86.frigatenvr.ui.ScreenHeader
import com.minhquanghp86.frigatenvr.ui.SettingsViewModel
import com.minhquanghp86.frigatenvr.ui.UiState
import com.minhquanghp86.frigatenvr.ui.fmtDuration
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull

private fun JsonObject.numOf(key: String): Double? =
    (this[key] as? JsonPrimitive)?.doubleOrNull

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    session: ServerSession,
) {
    val vm: SettingsViewModel = viewModel()
    val version by vm.version.collectAsState()
    val stats by vm.stats.collectAsState()
    val busy by vm.busy.collectAsState()
    val message by vm.message.collectAsState()

    var baseUrl by remember(session.baseUrl) { mutableStateOf(session.baseUrl) }
    var username by remember(session.username) { mutableStateOf(session.username) }
    var passInput by remember { mutableStateOf("") }
    var go2rtcUrl by remember(session.go2rtcUrl) { mutableStateOf(session.go2rtcUrl.orEmpty()) }
    var keepPass by remember { mutableStateOf(session.rememberPassword) }
    var trustCert by remember { mutableStateOf(session.trustSelfSigned) }
    var confirmLogout by remember { mutableStateOf(false) }

    Column(modifier.fillMaxSize()) {
        ScreenHeader(
            title = "Cài đặt",
            subtitle = "${session.baseUrl} · vai trò ${session.role} · app v${com.minhquanghp86.frigatenvr.BuildConfig.VERSION_NAME}",
        )

        LazyColumn(
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                ) {
                    Column(Modifier.padding(12.dp)) {
                        OutlinedTextField(
                            value = baseUrl,
                            onValueChange = { baseUrl = it },
                            label = { Text("Địa chỉ server") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(
                            value = username,
                            onValueChange = { username = it },
                            label = { Text("Tài khoản") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(
                            value = passInput,
                            onValueChange = { passInput = it },
                            label = {
                                Text(
                                    if (session.rememberPassword && session.password.isNotBlank())
                                        "Mật khẩu (đã lưu — để trống nếu không đổi)"
                                    else "Mật khẩu",
                                )
                            },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(
                            value = go2rtcUrl,
                            onValueChange = { go2rtcUrl = it },
                            label = { Text("go2rtc URL (tuỳ chọn)") },
                            placeholder = { Text("http://192.168.1.100:1984") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            "Để trống = tự suy ra http://<ip-frigate>:1984. Dùng cho luồng HLS/MP4 " +
                                "fps gốc. Lưu ý: go2rtc không có xác thực.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = keepPass, onCheckedChange = { keepPass = it })
                            Text("Ghi nhớ mật khẩu", style = MaterialTheme.typography.bodyMedium)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = trustCert, onCheckedChange = { trustCert = it })
                            Text(
                                "Tin chứng chỉ tự ký (HTTPS nội bộ)",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
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
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            if (busy) {
                                CircularProgressIndicator(
                                    Modifier.width(18.dp),
                                    strokeWidth = 2.dp,
                                )
                                Spacer(Modifier.width(10.dp))
                            }
                            Text("Lưu cài đặt")
                        }
                        message?.let {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                it,
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 12.sp,
                            )
                        }
                    }
                }
            }

            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Frigate", style = MaterialTheme.typography.titleSmall)
                        when (val v = version) {
                            UiState.Loading -> Text("Đang đọc...", fontSize = 12.sp)
                            is UiState.Error -> Text(
                                v.message,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.error,
                            )

                            is UiState.Ok -> Column {
                                Text(
                                    "Phiên bản: ${v.data.version ?: "—"}",
                                    fontSize = 13.sp,
                                )
                                Text(
                                    "Nhánh: ${v.data.branch ?: "—"} · cài đặt: " +
                                        (v.data.install_type ?: "—"),
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                v.data.date?.let { Text("Build: $it", fontSize = 12.sp) }
                            }

                            null -> Unit
                        }
                    }
                }
            }

            stats?.let { s ->
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                        ),
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text("Sức khỏe hệ thống", style = MaterialTheme.typography.titleSmall)
                            val service = s["service"] as? JsonObject
                            if (service != null) {
                                StatRow(
                                    "Bộ nhớ",
                                    service.numOf("mem_usage")?.let { "%.1f%%".format(it) },
                                )
                                StatRow(
                                    "CPU",
                                    service.numOf("cpu_usage")?.let { "%.1f%%".format(it) },
                                )
                                StatRow(
                                    "Thời gian chạy",
                                    service.numOf("uptime")?.let { fmtDuration(it) },
                                )
                            }
                            StatRow(
                                "Tốc độ phát hiện",
                                s.numOf("detection_fps")?.let { "%.1f fps".format(it) },
                            )
                            (s["cameras"] as? JsonObject)?.forEach { (name, value) ->
                                val camStats = value as? JsonObject ?: return@forEach
                                StatRow(
                                    "Camera $name",
                                    camStats.numOf("camera_fps")?.let { "%.1f fps".format(it) },
                                )
                            }
                        }
                    }
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { vm.refresh() }) {
                        Text("Tải lại số liệu")
                    }
                    OutlinedButton(onClick = { confirmLogout = true }) {
                        Icon(
                            Icons.Outlined.Logout,
                            contentDescription = null,
                            modifier = Modifier.width(16.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Đăng xuất")
                    }
                }
            }
        }
    }

    if (confirmLogout) {
        AlertDialog(
            onDismissRequest = { confirmLogout = false },
            title = { Text("Đăng xuất?") },
            text = {
                Text("Phiên và token sẽ bị xoá — bạn phải đăng nhập lại để xem camera.")
            },
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
private fun StatRow(label: String, value: String?) {
    if (value == null) return
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
    ) {
        Text(
            label,
            modifier = Modifier.width(150.dp),
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(value, fontSize = 12.sp)
    }
}
