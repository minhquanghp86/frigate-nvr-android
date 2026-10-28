package com.minhquanghp86.frigatenvr.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive

@Serializable
data class LoginRequest(val user: String, val password: String)

@Serializable
data class LoginBody(val message: String? = null, val access_token: String? = null)

@Serializable
data class CameraSetBody(val value: String)

@Serializable
data class IdsBody(
    val ids: List<String>,
    // Frigate 0.18 yêu cầu field này khi đánh dấu đã xem/bỏ đánh dấu.
    val reviewed: Boolean = true,
)

@Serializable
data class FrigateEvent(
    val id: String,
    val camera: String = "",
    val label: String = "",
    @SerialName("sub_label") val subLabel: JsonElement? = null,
    @SerialName("start_time") val startTime: Double = 0.0,
    @SerialName("end_time") val endTime: Double? = null,
    @SerialName("has_clip") val hasClip: Boolean = false,
    @SerialName("has_snapshot") val hasSnapshot: Boolean = false,
    @SerialName("top_score") val topScore: Double? = null,
    @SerialName("plus_id") val plusId: String? = null,
    val score: Double? = null,
    val zones: List<String> = emptyList(),
    val attributes: List<String> = emptyList(),
    val data: EventExtra? = null,
) {
    val subLabelDisplay: String?
        get() = when (subLabel) {
            is kotlinx.serialization.json.JsonArray ->
                subLabel.firstOrNull()?.let { it.asText() }
            null -> null
            else -> subLabel.asText()
        }

    val bestScore: Double
        get() = topScore ?: data?.topScore ?: score ?: 0.0
}

private fun JsonElement.asText(): String? = try {
    jsonPrimitive.content.takeIf { it.isNotBlank() && it != "null" }
} catch (_: Exception) {
    null
}

@Serializable
data class EventExtra(
    @SerialName("top_score") val topScore: Double? = null,
    val score: Double? = null,
    val attributes: List<String> = emptyList(),
    val box: List<Double>? = null,
)

/**
 * /api/config thay đổi khá nhiều giữa các bản Frigate (vd 0.18 đổi live.streams từ mảng sang object),
 * nên chỉ giữ phần app dùng và đọc các khối con dạng JsonObject để không vỡ khi schema đổi.
 */
@Serializable
data class FrigateConfig(
    val cameras: Map<String, FrigateCameraConfig> = emptyMap(),
) {
    val enabledCameras: List<String>
        get() = cameras.filter { it.value.enabled != false }.keys.toList()
}

@Serializable
data class FrigateCameraConfig(
    val enabled: Boolean = true,
    val name: String? = null,
    val record: JsonObject? = null,
    val audio: JsonObject? = null,
    val detect: JsonObject? = null,
    val live: JsonObject? = null,
    val ffmpeg: JsonObject? = null,
    val snapshots: JsonObject? = null,
) {
    val detectOn: Boolean get() = detect.flag("enabled", default = true)
    val snapshotsOn: Boolean get() = snapshots.flag("enabled")
    val recording: Boolean get() = record.flag("enabled")
    val hasAudio: Boolean get() = audio.flag("enabled")

    /** Tên stream go2rtc khai báo trong live.streams (0.18: object, bản cũ: mảng). */
    val liveStreamNames: List<String>
        get() {
            val streams = live?.get("streams") ?: return emptyList()
            return when (streams) {
                is JsonObject -> streams.values.mapNotNull { it.textOrNull() }
                is kotlinx.serialization.json.JsonArray -> streams.mapNotNull { it.textOrNull() }
                else -> emptyList()
            }
        }
}

private fun JsonElement.textOrNull(): String? = try {
    jsonPrimitive.content.takeIf { it.isNotBlank() }
} catch (_: Exception) {
    null
}

private fun JsonObject?.flag(key: String, default: Boolean = false): Boolean =
    this?.get(key)?.jsonPrimitive?.booleanOrNull ?: default

@Serializable
data class VersionInfo(
    val version: String? = null,
    val branch: String? = null,
    val commit: String? = null,
    val date: String? = null,
    val install_type: String? = null,
)

@Serializable
data class ReviewItem(
    val id: String,
    val camera: String = "",
    @SerialName("start_time") val startTime: JsonElement? = null,
    @SerialName("end_time") val endTime: JsonElement? = null,
    @SerialName("has_been_reviewed") val reviewed: Boolean = false,
    val severity: String = "detection",
    @SerialName("thumb_path") val thumbPath: String = "",
    val data: ReviewData? = null,
) {
    /** start_time có thể về dạng epoch (số) hoặc chuỗi ISO tùy version Frigate. */
    val startEpoch: Double
        get() = startTime.asEpoch() ?: 0.0

    val endEpoch: Double
        get() = endTime.asEpoch() ?: startEpoch

    val eventIds: List<String>
        get() = data?.detections.orEmpty().filter { it.isNotBlank() }

    val description: String?
        get() = data?.description
}

private fun JsonElement?.asEpoch(): Double? = when (this) {
    null -> null
    is kotlinx.serialization.json.JsonPrimitive -> doubleOrNull ?: content.parseEpoch()
    else -> null
}

/** Chuỗi thời gian có thể là epoch, ISO-8601 có offset (Frigate 0.18 dùng datetime) hoặc không offset (UTC). */
private fun String.parseEpoch(): Double? {
    if (isBlank()) return null
    toDoubleOrNull()?.let { return it }
    val instant = runCatching { java.time.Instant.parse(this) }.getOrNull()
        ?: runCatching { java.time.OffsetDateTime.parse(this).toInstant() }.getOrNull()
        ?: runCatching {
            java.time.LocalDateTime.parse(this).toInstant(java.time.ZoneOffset.UTC)
        }.getOrNull()
    return instant?.toEpochMilli()?.div(1000.0)
}

@Serializable
data class ReviewData(
    val detections: List<String> = emptyList(),
    val motion: List<String> = emptyList(),
    val audio: List<String> = emptyList(),
    val description: String? = null,
)

@Serializable
data class DayRecordings(
    val day: String = "",
    val events: Int = 0,
    val hours: List<HourRecording> = emptyList(),
)

@Serializable
data class HourRecording(
    val hour: String = "",
    val events: Int = 0,
    val duration: Int = 0,
    val motion: Int = 0,
    val objects: Int = 0,
)

/** Số thập phân từ JSON bất kỳ (dùng cho /api/stats có cấu hình đổi theo version). */
fun JsonObject.num(key: String): Double? =
    this[key]?.jsonPrimitive?.doubleOrNull

fun JsonObject.str(key: String): String? =
    this[key]?.jsonPrimitive?.let { p ->
        try {
            p.content.takeIf { it.isNotBlank() && it != "null" }
        } catch (_: Exception) {
            null
        }
    }
