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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.minhquanghp86.frigatenvr.data.FrigateCameraConfig
import com.minhquanghp86.frigatenvr.ui.IOSLabel
import com.minhquanghp86.frigatenvr.ui.IOSRed
import com.minhquanghp86.frigatenvr.ui.IOSSecondaryLabel
import com.minhquanghp86.frigatenvr.ui.IosDivider
import com.minhquanghp86.frigatenvr.ui.IosGroup
import com.minhquanghp86.frigatenvr.ui.IosRow
import com.minhquanghp86.frigatenvr.ui.IosSwitchRow
import com.minhquanghp86.frigatenvr.ui.LargeTitleHeader
import com.minhquanghp86.frigatenvr.ui.SectionTitle
import com.minhquanghp86.frigatenvr.ui.rememberRepo
import kotlinx.coroutines.launch

data class CameraFeatures(
    val enabled: Boolean = true,
    val detect: Boolean = true,
    val motion: Boolean = true,
    val recordings: Boolean = true,
    val snapshots: Boolean = true,
    val audio: Boolean = true,
    val notifications: Boolean = true,
    val reviewAlerts: Boolean = true,
    val reviewDetections: Boolean = true,
    val descriptions: Boolean = true,
)

data class CameraConfigEntry(
    val name: String,
    val features: CameraFeatures,
)

@Composable
fun CameraConfigScreen(onBack: () -> Unit) {
    val repo = rememberRepo()
    val scope = rememberCoroutineScope()
    var entries by remember { mutableStateOf<List<CameraConfigEntry>?>(null) }
    var profiles by remember { mutableStateOf<List<String>>(emptyList()) }
    var activeProfile by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }

    fun featuresOf(config: FrigateCameraConfig) = CameraFeatures(
        enabled = config.enabled != false,
        detect = config.detectOn,
        motion = config.motionOn,
        recordings = config.recording,
        snapshots = config.snapshotsOn,
        audio = config.audioOn,
    )

    fun loadProfiles() {
        scope.launch {
            val state = repo.profiles()
            profiles = state.profiles
            activeProfile = state.active
        }
    }

    LaunchedEffect(Unit) {
        entries = runCatching {
            val config = repo.configCached()
            config.enabledCameras.map { name ->
                CameraConfigEntry(name, featuresOf(config.cameras[name] ?: FrigateCameraConfig()))
            }
        }.getOrDefault(emptyList())
        loadProfiles()
    }

    fun update(name: String, feature: String, value: Boolean) {
        val previous = entries ?: return
        entries = previous.map { entry ->
            if (entry.name != name) return@map entry
            val f = entry.features
            entry.copy(
                features = when (feature) {
                    "enabled" -> f.copy(enabled = value)
                    "detect" -> f.copy(detect = value)
                    "motion" -> f.copy(motion = value)
                    "recordings" -> f.copy(recordings = value)
                    "snapshots" -> f.copy(snapshots = value)
                    "audio" -> f.copy(audio = value)
                    "notifications" -> f.copy(notifications = value)
                    "review_alerts" -> f.copy(reviewAlerts = value)
                    "review_detections" -> f.copy(reviewDetections = value)
                    else -> f.copy(descriptions = value)
                },
            )
        }
        error = null
        scope.launch {
            val result = runCatching { repo.setCameraFeature(name, feature, value) }
            if (result.isFailure) {
                error = "$name: ${result.exceptionOrNull()?.message ?: "không đổi được"}"
                entries = previous
            }
        }
    }

    fun activateProfile(name: String?) {
        scope.launch {
            try {
                repo.setProfile(name)
                activeProfile = name
                message = if (name == null) "Đã tắt profile" else "Đang dùng profile $name"
            } catch (e: Exception) {
                error = e.message ?: "Không đổi được profile"
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        LargeTitleHeader(
            title = "Cấu hình Camera",
            subtitle = "Bật/tắt tính năng (cần quyền admin)",
            trailing = {
                IconButton(onClick = onBack) {
                    Icon(Icons.Outlined.ArrowBack, contentDescription = "Quay lại", tint = IOSLabel)
                }
            },
        )

        val data = entries
        if (data == null) {
            Column(Modifier.padding(24.dp)) { CircularProgressIndicator() }
            return@Column
        }

        LazyColumn(
            contentPadding = PaddingValues(bottom = 28.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            if (profiles.isNotEmpty()) {
                item {
                    SectionTitle("Profile cấu hình")
                    IosGroup {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            FilterChip(
                                selected = activeProfile == null,
                                onClick = { activateProfile(null) },
                                label = { Text("Mặc định", fontSize = 12.sp) },
                            )
                        }
                        profiles.forEach { profile ->
                            IosRow(
                                profile,
                                value = if (activeProfile == profile) "đang dùng" else null,
                                chevron = activeProfile != profile,
                                onClick = { activateProfile(profile) },
                            )
                            IosDivider(16)
                        }
                    }
                }
            }

            items(data, key = { it.name }) { entry ->
                SectionTitle(entry.name)
                IosGroup {
                    IosSwitchRow(
                        "Camera hoạt động",
                        subtitle = "Tắt = dừng toàn bộ xử lý của camera",
                        checked = entry.features.enabled,
                        onChange = { update(entry.name, "enabled", it) },
                    )
                    IosDivider(16)
                    IosSwitchRow(
                        "Phát hiện đối tượng",
                        checked = entry.features.detect,
                        onChange = { update(entry.name, "detect", it) },
                    )
                    IosDivider(16)
                    IosSwitchRow(
                        "Phát hiện chuyển động",
                        checked = entry.features.motion,
                        onChange = { update(entry.name, "motion", it) },
                    )
                    IosDivider(16)
                    IosSwitchRow(
                        "Ghi hình",
                        checked = entry.features.recordings,
                        onChange = { update(entry.name, "recordings", it) },
                    )
                    IosDivider(16)
                    IosSwitchRow(
                        "Chụp ảnh sự kiện",
                        checked = entry.features.snapshots,
                        onChange = { update(entry.name, "snapshots", it) },
                    )
                    IosDivider(16)
                    IosSwitchRow(
                        "Âm thanh",
                        checked = entry.features.audio,
                        onChange = { update(entry.name, "audio", it) },
                    )
                }

                Spacer(Modifier.height(10.dp))
                IosGroup {
                    IosSwitchRow(
                        "Thông báo sự kiện",
                        subtitle = "Frigate có gửi thông báo cho camera này không",
                        checked = entry.features.notifications,
                        onChange = { update(entry.name, "notifications", it) },
                    )
                    IosDivider(16)
                    IosSwitchRow(
                        "Cảnh báo (review alerts)",
                        checked = entry.features.reviewAlerts,
                        onChange = { update(entry.name, "review_alerts", it) },
                    )
                    IosDivider(16)
                    IosSwitchRow(
                        "Theo dõi (review detections)",
                        checked = entry.features.reviewDetections,
                        onChange = { update(entry.name, "review_detections", it) },
                    )
                    IosDivider(16)
                    IosSwitchRow(
                        "Mô tả đối tượng (AI)",
                        subtitle = "Cần cấu hình GenAI trong Frigate",
                        checked = entry.features.descriptions,
                        onChange = { update(entry.name, "object_descriptions", it) },
                    )
                }
            }

            error?.let { text ->
                item {
                    Text(
                        text,
                        color = IOSRed,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    )
                }
            }
            message?.let { text ->
                item {
                    Text(
                        text,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                    )
                }
            }
            item {
                Text(
                    "Lưu ý: các thay đổi áp dụng ngay vào Frigate (MQTT command), không sửa config.yml.",
                    color = IOSSecondaryLabel,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
            }
        }
    }
}
