package com.minhquanghp86.frigatenvr.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive

@Serializable
data class LoginRequest(val user: String, val password: String)

@Serializable
data class LoginBody(val message: String? = null, val access_token: String? = null)

@Serializable
data class IdsBody(val ids: List<String>)

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

@Serializable
data class FrigateConfig(
    val cameras: Map<String, FrigateCameraConfig> = emptyMap(),
    val go2rtc: Go2rtcConfig? = null,
) {
    val enabledCameras: List<String>
        get() = cameras.filter { it.value.enabled != false }.keys.toList()
}

@Serializable
data class FrigateCameraConfig(
    val enabled: Boolean = true,
    val name: String? = null,
    val detect: DetectConfig? = null,
    val ffmpeg: FfmpegConfig? = null,
    val audio: AudioConfig? = null,
    val record: RecordConfig? = null,
    val live: LiveConfig? = null,
)

@Serializable
data class DetectConfig(
    val enabled: Boolean? = null,
    val width: Int? = null,
    val height: Int? = null,
)

@Serializable
data class FfmpegConfig(val inputs: List<FfmpegInput>? = null)

@Serializable
data class FfmpegInput(val path: String? = null, val roles: List<String>? = null)

@Serializable
data class AudioConfig(val enabled: Boolean? = null)

@Serializable
data class RecordConfig(val enabled: Boolean? = null)

@Serializable
data class LiveConfig(val streams: List<String>? = null)

@Serializable
data class Go2rtcConfig(val streams: Map<String, JsonElement> = emptyMap())

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
    is kotlinx.serialization.json.JsonPrimitive -> doubleOrNull ?: content.toDoubleOrNull()
    else -> null
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
