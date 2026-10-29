package com.minhquanghp86.frigatenvr.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.minhquanghp86.frigatenvr.ui.IOSBlue
import com.minhquanghp86.frigatenvr.ui.IOSLabel
import com.minhquanghp86.frigatenvr.ui.IOSSecondaryLabel
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.minhquanghp86.frigatenvr.data.FrigateRepository
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(repo: FrigateRepository) {
    var baseUrl by remember { mutableStateOf("https://192.168.10.2:8071") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var keepPass by remember { mutableStateOf(true) }
    var trust by remember { mutableStateOf(true) }
    var showPassword by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val busy by repo.busy.collectAsState()
    val scope = rememberCoroutineScope()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "FriCam",
            style = MaterialTheme.typography.headlineMedium,
            color = IOSLabel,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
        )
        Text(
            "Trình xem Frigate NVR (không chính thức)",
            style = MaterialTheme.typography.bodyMedium,
            color = IOSSecondaryLabel,
        )
        Spacer(Modifier.height(24.dp))

        OutlinedTextField(
            value = baseUrl,
            onValueChange = { baseUrl = it },
            label = { Text("Địa chỉ server (http://ip:8971)") },
            singleLine = true,
            colors = loginFieldColors(),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            label = { Text("Tài khoản") },
            singleLine = true,
            colors = loginFieldColors(),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Mật khẩu") },
            singleLine = true,
            colors = loginFieldColors(),
            visualTransformation = if (showPassword) VisualTransformation.None
            else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            trailingIcon = {
                IconButton(onClick = { showPassword = !showPassword }) {
                    Icon(
                        imageVector = if (showPassword) Icons.Outlined.VisibilityOff
                        else Icons.Outlined.Visibility,
                        contentDescription = "Hiện mật khẩu",
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = keepPass,
                onCheckedChange = { keepPass = it },
                colors = loginCheckboxColors(),
            )
            Text(
                "Ghi nhớ mật khẩu",
                style = MaterialTheme.typography.bodyMedium,
                color = IOSLabel,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = trust,
                onCheckedChange = { trust = it },
                colors = loginCheckboxColors(),
            )
            Text(
                "Tin chứng chỉ tự ký (HTTPS nội bộ)",
                style = MaterialTheme.typography.bodyMedium,
                color = IOSLabel,
            )
        }

        error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        Spacer(Modifier.height(20.dp))
        Button(
            onClick = {
                error = null
                scope.launch {
                    error = repo.signIn(baseUrl, username, password, keepPass, trust)
                }
            },
            enabled = !busy,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
        ) {
            if (busy) {
                CircularProgressIndicator(Modifier.width(20.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(12.dp))
            }
            Text(if (busy) "Đang đăng nhập..." else "Đăng nhập")
        }

        Spacer(Modifier.height(10.dp))
        OutlinedButton(
            onClick = {
                error = null
                baseUrl = "https://demo.frigate.video"
                username = ""
                password = ""
                trust = false
                scope.launch {
                    error = repo.signIn(baseUrl, "", "", keepPass, false)
                }
            },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Dùng bản demo (không cần tài khoản)")
        }

        Spacer(Modifier.height(16.dp))
        Text(
            "Port thường dùng: 8971 (có xác thực), 5000 (không xác thực, trong mạng). " +
                "Frigate tắt xác thực thì app tự dùng chế độ ẩn danh. " +
                "Bản demo dùng máy chủ công khai demo.frigate.video.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}


@Composable
private fun loginCheckboxColors() = androidx.compose.material3.CheckboxDefaults.colors(
    checkedColor = IOSBlue,
    uncheckedColor = IOSSecondaryLabel,
    checkmarkColor = androidx.compose.ui.graphics.Color.White,
)

@Composable
private fun loginFieldColors() = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
    focusedTextColor = IOSLabel,
    unfocusedTextColor = IOSLabel,
    focusedLabelColor = IOSBlue,
    unfocusedLabelColor = IOSSecondaryLabel,
    cursorColor = IOSBlue,
    focusedBorderColor = IOSBlue,
    unfocusedBorderColor = androidx.compose.ui.graphics.Color(0x33FFFFFF),
)
