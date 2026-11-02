package com.minhquanghp86.frigatenvr.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.minhquanghp86.frigatenvr.ui.IOSLabel
import com.minhquanghp86.frigatenvr.ui.IOSSecondaryLabel
import com.minhquanghp86.frigatenvr.ui.IosDivider
import com.minhquanghp86.frigatenvr.ui.IosGroup
import com.minhquanghp86.frigatenvr.ui.IosSwitchRow
import com.minhquanghp86.frigatenvr.ui.LargeTitleHeader
import com.minhquanghp86.frigatenvr.ui.SectionTitle
import com.minhquanghp86.frigatenvr.ui.rememberRepo
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

private data class AiCamera(
    val name: String,
    val lpr: Boolean,
    val face: Boolean,
)

/**
 * Bật/tắt LPR và nhận diện khuôn mặt NGAY trên app (không cần sửa YAML / restart).
 * Dùng PUT /api/config/set với update_topic `config/cameras/{cam}/{feature}` hoặc `config/{feature}`.
 */
@Composable
fun AiFeaturesScreen(onBack: () -> Unit = {}) {
    val repo = rememberRepo()
    val scope = rememberCoroutineScope()

    var loading by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var globalLpr by remember { mutableStateOf(false) }
    var globalFace by remember { mutableStateOf(false) }
    var cameras by remember { mutableStateOf<List<AiCamera>>(emptyList()) }

    fun obj(o: JsonObject?, key: String) = o?.get(key) as? JsonObject
    fun flag(o: JsonObject?, key: String): Boolean =
        ((o?.get(key) as? JsonPrimitive)?.content)?.toBooleanStrictOrNull() ?: false

    suspend fun reload() {
        val root = repo.configJson() as? JsonObject ?: return
        globalLpr = flag(obj(root, "lpr"), "enabled")
        globalFace = flag(obj(root, "face_recognition"), "enabled")
        val camObj = obj(root, "cameras")
        cameras = camObj?.keys?.sorted()?.map { name ->
            val cam = obj(camObj, name)
            AiCamera(
                name = name,
                lpr = flag(obj(cam, "lpr"), "enabled"),
                face = flag(obj(cam, "face_recognition"), "enabled"),
            )
        } ?: emptyList()
    }

    LaunchedEffect(Unit) {
        runCatching { reload() }.onFailure { message = it.message }
        loading = false
    }

    fun featureOf(cam: AiCamera, feature: String) =
        if (feature == "lpr") cam.lpr else cam.face

    fun updateCam(cam: AiCamera, feature: String, value: Boolean) {
        val previous = cameras
        cameras = cameras.map {
            if (it.name != cam.name) it
            else if (feature == "lpr") it.copy(lpr = value) else it.copy(face = value)
        }
        busy = true
        scope.launch {
            try {
                repo.setCameraAi(cam.name, feature, value)
                message = "${cam.name}: ${if (feature == "lpr") "LPR" else "Khuôn mặt"} " +
                    if (value) "đã bật" else "đã tắt"
            } catch (e: Exception) {
                cameras = previous
                message = e.message ?: "Không đổi được cấu hình"
            } finally {
                busy = false
            }
        }
    }

    fun updateGlobal(feature: String, value: Boolean) {
        if (feature == "lpr") globalLpr = value else globalFace = value
        busy = true
        scope.launch {
            try {
                if (!value) {
                    // Frigate yêu cầu tắt ở mọi camera trước khi tắt global.
                    cameras.filter { featureOf(it, feature) }.forEach { cam ->
                        repo.setCameraAi(cam.name, feature, false)
                    }
                    cameras = cameras.map {
                        if (feature == "lpr") it.copy(lpr = false) else it.copy(face = false)
                    }
                }
                repo.setGlobalAi(feature, value)
                reload()
                message = "Đã ${if (value) "bật" else "tắt"} " +
                    (if (feature == "lpr") "LPR" else "nhận diện khuôn mặt") + " toàn hệ thống"
            } catch (e: Exception) {
                if (feature == "lpr") globalLpr = !value else globalFace = !value
                message = e.message ?: "Không đổi được cấu hình"
            } finally {
                busy = false
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        LargeTitleHeader(
            title = "Tính năng AI",
            subtitle = "LPR · nhận diện khuôn mặt",
            trailing = {
                IconButton(onClick = onBack) {
                    Icon(Icons.Outlined.ArrowBack, contentDescription = "Quay lại", tint = IOSLabel)
                }
            },
        )

        if (loading) {
            Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
            return@Column
        }

        LazyColumn(contentPadding = PaddingValues(bottom = 28.dp)) {
            item {
                SectionTitle("Toàn hệ thống")
                IosGroup {
                    IosSwitchRow(
                        title = "License Plate Recognition",
                        subtitle = "Đọc biển số xe (LPR)",
                        checked = globalLpr,
                        onChange = { if (!busy) updateGlobal("lpr", it) },
                    )
                    IosDivider(16)
                    IosSwitchRow(
                        title = "Nhận diện khuôn mặt",
                        subtitle = "Face recognition",
                        checked = globalFace,
                        onChange = { if (!busy) updateGlobal("face_recognition", it) },
                    )
                }
            }

            message?.let { text ->
                item {
                    Text(
                        text,
                        color = IOSSecondaryLabel,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    )
                }
            }

            item { SectionTitle("Theo camera") }

            items(cameras) { cam ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surface),
                ) {
                    Text(
                        cam.name,
                        color = IOSLabel,
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(start = 16.dp, top = 12.dp),
                    )
                    IosSwitchRow(
                        title = "LPR",
                        subtitle = if (globalLpr) null else "Bật LPR toàn hệ thống trước",
                        checked = cam.lpr,
                        onChange = { if (!busy && globalLpr) updateCam(cam, "lpr", it) },
                    )
                    IosDivider(16)
                    IosSwitchRow(
                        title = "Khuôn mặt",
                        subtitle = if (globalFace) null else "Bật nhận diện khuôn mặt toàn hệ thống trước",
                        checked = cam.face,
                        onChange = { if (!busy && globalFace) updateCam(cam, "face_recognition", it) },
                    )
                }
            }
        }
    }
}
