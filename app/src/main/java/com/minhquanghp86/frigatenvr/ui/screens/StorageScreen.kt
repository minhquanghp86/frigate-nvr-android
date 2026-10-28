package com.minhquanghp86.frigatenvr.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.unit.dp
import com.minhquanghp86.frigatenvr.ui.IOSBlue
import com.minhquanghp86.frigatenvr.ui.IOSCard
import com.minhquanghp86.frigatenvr.ui.IOSGroupedBackground
import com.minhquanghp86.frigatenvr.ui.IOSGreen
import com.minhquanghp86.frigatenvr.ui.IOSLabel
import com.minhquanghp86.frigatenvr.ui.IOSOrange
import com.minhquanghp86.frigatenvr.ui.IOSRed
import com.minhquanghp86.frigatenvr.ui.IOSSecondaryLabel
import com.minhquanghp86.frigatenvr.ui.IosDivider
import com.minhquanghp86.frigatenvr.ui.IosGroup
import com.minhquanghp86.frigatenvr.ui.SectionTitle
import com.minhquanghp86.frigatenvr.ui.rememberRepo
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive

private data class CamUsage(val camera: String, val usageMb: Double, val percent: Double)

@Composable
fun StorageScreen(onBack: () -> Unit) {
    val repo = rememberRepo()
    var items by remember { mutableStateOf<List<CamUsage>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    suspend fun load() {
        loading = true
        try {
            val obj = repo.storage() as? JsonObject ?: JsonObject(emptyMap())
            items = obj.entries.mapNotNull { (cam, v) ->
                val o = v as? JsonObject ?: return@mapNotNull null
                val usage = o["usage"]?.jsonPrimitive?.doubleOrNull ?: 0.0
                val pct = o["usage_percent"]?.jsonPrimitive?.doubleOrNull ?: 0.0
                CamUsage(cam, usage, pct)
            }.sortedByDescending { it.usageMb }
            error = null
        } catch (e: Exception) {
            error = e.message ?: "Không đọc được dung lượng"
        } finally {
            loading = false
        }
    }

    LaunchedEffect(Unit) { load() }

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
                "Lưu trữ",
                style = MaterialTheme.typography.headlineSmall,
                color = IOSLabel,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = {
                // tải lại
                items = items
            }) {
                Icon(
                    Icons.Outlined.Refresh,
                    contentDescription = "Tải lại",
                    tint = IOSBlue,
                    modifier = Modifier.padding(0.dp),
                )
            }
        }

        when {
            loading && items.isEmpty() -> Text(
                "Đang tải…",
                color = IOSSecondaryLabel,
                modifier = Modifier.padding(20.dp),
            )

            error != null -> Text(
                error!!,
                color = IOSRed,
                modifier = Modifier.padding(20.dp),
            )

            items.isEmpty() -> Text(
                "Chưa có dữ liệu ghi hình.",
                color = IOSSecondaryLabel,
                modifier = Modifier.padding(20.dp),
            )

            else -> {
                SectionTitle("Dung lượng ghi hình theo camera")
                LazyColumn(
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(0.dp),
                ) {
                    item {
                        IosGroup {
                            items.forEachIndexed { index, cam ->
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(cam.camera, style = MaterialTheme.typography.bodyLarge, color = IOSLabel)
                                        Spacer(Modifier.height(6.dp))
                                        LinearProgressIndicator(
                                            progress = { (cam.percent / 100f).toFloat().coerceIn(0f, 1f) },
                                            color = when {
                                                cam.percent >= 90 -> IOSRed
                                                cam.percent >= 70 -> IOSOrange
                                                else -> IOSGreen
                                            },
                                            trackColor = IOSCard,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(5.dp)
                                                .clip(RoundedCornerShape(99.dp)),
                                        )
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            formatMb(cam.usageMb),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = IOSLabel,
                                        )
                                        Text(
                                            "%.1f%%".format(cam.percent),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = IOSSecondaryLabel,
                                        )
                                    }
                                }
                                if (index != items.lastIndex) IosDivider(startIndent = 16)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatMb(mb: Double): String =
    if (mb >= 1024) "%.1f GB".format(mb / 1024.0) else "%.0f MB".format(mb)
