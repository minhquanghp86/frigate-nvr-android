package com.minhquanghp86.frigatenvr.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.minhquanghp86.frigatenvr.ui.IOSBlue
import com.minhquanghp86.frigatenvr.ui.IOSCard
import com.minhquanghp86.frigatenvr.ui.IOSGroupedBackground
import com.minhquanghp86.frigatenvr.ui.IOSLabel
import com.minhquanghp86.frigatenvr.ui.IOSSecondaryLabel
import com.minhquanghp86.frigatenvr.ui.IosSwitchRow
import com.minhquanghp86.frigatenvr.ui.SegmentedControl
import com.minhquanghp86.frigatenvr.ui.rememberRepo
import kotlinx.coroutines.delay

private val SERVICES = listOf("frigate", "go2rtc", "app")

private fun serviceLabel(key: String): String = when (key) {
    "frigate" -> "Frigate"
    "go2rtc" -> "go2rtc"
    else -> "Ứng dụng"
}

@Composable
fun LogsScreen(onBack: () -> Unit) {
    val repo = rememberRepo()
    val context = androidx.compose.ui.platform.LocalContext.current
    var serviceIndex by remember { mutableStateOf(0) }
    var follow by remember { mutableStateOf(true) }
    var text by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    suspend fun load() {
        try {
            val service = SERVICES[serviceIndex]
            text = if (service == "app") {
                com.minhquanghp86.frigatenvr.notify.CrashLog.read(context)
                    .ifBlank { "Chưa có lỗi nào được ghi." }
            } else {
                repo.logs(service)
            }
            error = null
        } catch (e: Exception) {
            error = e.message ?: "Không đọc được log"
        } finally {
            loading = false
        }
    }

    LaunchedEffect(serviceIndex) {
        loading = true
        load()
    }
    LaunchedEffect(serviceIndex, follow) {
        if (!follow) return@LaunchedEffect
        while (true) {
            delay(3_000)
            load()
        }
    }

    Column(Modifier.fillMaxSize().background(IOSGroupedBackground)) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 6.dp, end = 10.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Outlined.ArrowBack, contentDescription = "Quay lại", tint = IOSLabel)
            }
            Text(
                "Nhật ký",
                style = MaterialTheme.typography.headlineSmall,
                color = IOSLabel,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { loading = true }) {
                Icon(Icons.Outlined.Refresh, contentDescription = "Tải lại", tint = IOSBlue)
            }
        }

        SegmentedControl(
            options = SERVICES.map { serviceLabel(it) },
            selectedIndex = serviceIndex,
            onSelect = { serviceIndex = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )

        IosSwitchRow(
            title = "Tự cập nhật",
            subtitle = "Làm mới mỗi 3 giây",
            checked = follow,
            onChange = { follow = it },
        )

        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(12.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(IOSCard),
        ) {
            val body = when {
                error != null -> error!!
                loading && text.isBlank() -> "Đang tải…"
                text.isBlank() -> "Không có log."
                else -> text
            }
            Text(
                body,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                lineHeight = 15.sp,
                color = IOSLabel,
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .horizontalScroll(rememberScrollState())
                    .padding(10.dp),
            )
        }

        Text(
            if (error != null) "" else "Nguồn: /api/logs/${SERVICES[serviceIndex]}",
            style = MaterialTheme.typography.labelSmall,
            color = IOSSecondaryLabel,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
        )
        Spacer(Modifier.padding(bottom = 4.dp))
    }
}
