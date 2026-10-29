package com.minhquanghp86.frigatenvr.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.minhquanghp86.frigatenvr.ui.IosSwitchRow
import com.minhquanghp86.frigatenvr.ui.LargeTitleHeader
import com.minhquanghp86.frigatenvr.ui.SectionTitle
import com.minhquanghp86.frigatenvr.ui.rememberRepo
import kotlinx.coroutines.launch

data class CameraFeatures(
    val detect: Boolean,
    val record: Boolean,
    val snapshots: Boolean,
    val audio: Boolean,
)

@Composable
fun CameraConfigScreen(onBack: () -> Unit) {
    val repo = rememberRepo()
    val scope = rememberCoroutineScope()
    var cameras by remember { mutableStateOf<List<Pair<String, CameraFeatures>>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf<String?>(null) }

    fun featuresOf(config: FrigateCameraConfig) = CameraFeatures(
        detect = config.detectOn,
        record = config.recording,
        snapshots = config.snapshotsOn,
        audio = config.audioOn,
    )

    LaunchedEffect(Unit) {
        cameras = runCatching {
            val config = repo.configCached()
            config.enabledCameras.map { name ->
                name to featuresOf(config.cameras[name] ?: FrigateCameraConfig())
            }
        }.getOrDefault(emptyList())
    }

    fun update(name: String, key: String, value: Boolean) {
        val previous = cameras ?: return
        cameras = previous.map { (cam, features) ->
            if (cam != name) return@map cam to features
            cam to when (key) {
                "detect" -> features.copy(detect = value)
                "record" -> features.copy(record = value)
                "snapshots" -> features.copy(snapshots = value)
                else -> features.copy(audio = value)
            }
        }
        saving = "$name/$key"
        scope.launch {
            val result = runCatching { repo.setCameraFeature(name, key, value) }
            if (result.isFailure) {
                error = "$name: ${result.exceptionOrNull()?.message ?: "không đổi được"}"
                cameras = previous
            }
            saving = null
        }
    }

    Column(Modifier.fillMaxSize()) {
        LargeTitleHeader(
            title = "Cấu hình Camera",
            subtitle = "Bật/tắt nhanh tính năng (cần quyền admin)",
            trailing = {
                IconButton(onClick = onBack) {
                    Icon(Icons.Outlined.ArrowBack, contentDescription = "Quay lại", tint = IOSLabel)
                }
            },
        )

        val data = cameras
        if (data == null) {
            LazyColumn(Modifier.fillMaxSize()) {
                item {
                    Column(Modifier.padding(24.dp)) { CircularProgressIndicator() }
                }
            }
            return@Column
        }

        if (data.isEmpty()) {
            Text(
                "Không có camera nào được bật.",
                color = IOSSecondaryLabel,
                fontSize = 13.sp,
                modifier = Modifier.padding(24.dp),
            )
            return@Column
        }

        LazyColumn(
            contentPadding = PaddingValues(bottom = 28.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            data.forEach { (name, features) ->
                item(key = name) {
                    SectionTitle(name)
                    IosGroup {
                        IosSwitchRow(
                            "Phát hiện đối tượng",
                            subtitle = if (saving == "$name/detect") "Đang lưu…" else null,
                            checked = features.detect,
                            onChange = { update(name, "detect", it) },
                        )
                        IosDivider(16)
                        IosSwitchRow(
                            "Ghi hình",
                            subtitle = if (saving == "$name/record") "Đang lưu…" else null,
                            checked = features.record,
                            onChange = { update(name, "record", it) },
                        )
                        IosDivider(16)
                        IosSwitchRow(
                            "Chụp ảnh sự kiện",
                            subtitle = if (saving == "$name/snapshots") "Đang lưu…" else null,
                            checked = features.snapshots,
                            onChange = { update(name, "snapshots", it) },
                        )
                        IosDivider(16)
                        IosSwitchRow(
                            "Âm thanh",
                            subtitle = if (saving == "$name/audio") "Đang lưu…" else null,
                            checked = features.audio,
                            onChange = { update(name, "audio", it) },
                        )
                    }
                }
            }
            error?.let { message ->
                item {
                    Text(
                        message,
                        color = IOSRed,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    )
                }
            }
        }
    }
}
