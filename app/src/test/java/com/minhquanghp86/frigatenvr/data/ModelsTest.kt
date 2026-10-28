package com.minhquanghp86.frigatenvr.data

import kotlinx.serialization.decodeFromString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Khoá lại các khác biệt API của Frigate 0.18 (đã từng làm app crash parse):
 * - live.streams là object (không phải mảng)
 * - review: start_time là chuỗi ISO, reviewed là số
 */
class ModelsTest {

    private val json = FrigateNet.json

    @Test
    fun config_018_live_streams_is_object() {
        val raw = """
            {"cameras":{"chuong_cua":{"enabled":true,
              "record":{"enabled":true,"retain":{"days":7}},
              "audio":{"enabled":false},
              "detect":{"width":1280,"height":720},
              "live":{"streams":{"chuong_cua":"chuong_cua"},"height":720,"quality":8},
              "ffmpeg":{"inputs":[{"path":"rtsp://cam/stream","roles":["detect"]}]}}}}
        """.trimIndent()

        val cfg = json.decodeFromString<FrigateConfig>(raw)
        val cam = cfg.cameras.getValue("chuong_cua")
        assertEquals(listOf("chuong_cua"), cfg.enabledCameras)
        assertTrue(cam.recording)
        assertFalse(cam.hasAudio)
    }

    @Test
    fun config_camera_disabled_is_filtered() {
        val raw = """{"cameras":{"a":{"enabled":true},"b":{"enabled":false},"c":{}}}"""
        val cfg = json.decodeFromString<FrigateConfig>(raw)
        assertEquals(listOf("a", "c"), cfg.enabledCameras)
    }

    @Test
    fun review_018_iso_times_and_detections() {
        val raw = """
            [{"id":"1730000000.123456-abc","camera":"chuong_cua",
              "start_time":"2026-10-28T10:11:12.345678+00:00",
              "end_time":"2026-10-28T10:11:42.345678+00:00",
              "has_been_reviewed":false,
              "severity":"alert",
              "thumb_path":"/media/frigate/clips/review/x.jpg",
              "data":{"detections":["1730000000.100000-aaa"],"objects":["person"],"audio":[]}}]
        """.trimIndent()

        val item = json.decodeFromString<List<ReviewItem>>(raw).first()
        assertEquals("chuong_cua", item.camera)
        assertEquals("alert", item.severity)
        assertEquals(listOf("1730000000.100000-aaa"), item.eventIds)
        assertTrue("startEpoch phải đọc được ISO", item.startEpoch > 1_700_000_000)
        assertTrue(item.endEpoch > item.startEpoch)
        assertNull(item.description)
    }

    @Test
    fun review_old_numeric_times_still_work() {
        val raw = """{"id":"1","camera":"c","start_time":1730000000.5,"end_time":1730000010.5,
            "has_been_reviewed":true,"severity":"detection","thumb_path":"","data":{}}"""
        val item = json.decodeFromString<ReviewItem>(raw)
        assertEquals(1730000000.5, item.startEpoch, 0.001)
        assertTrue(item.eventIds.isEmpty())
    }

    @Test
    fun event_018_shape() {
        val raw = """
            [{"id":"1730000000.1-abc","label":"person","sub_label":null,"camera":"chuong_cua",
              "start_time":1730000000.1,"end_time":null,"zones":["cong"],"has_clip":true,
              "has_snapshot":true,"retain_indefinitely":false,"plus_id":null,
              "data":{"top_score":0.82,"score":0.7,"attributes":["face"]}}]
        """.trimIndent()

        val event = json.decodeFromString<List<FrigateEvent>>(raw).first()
        assertEquals("person", event.label)
        assertEquals(0.82, event.bestScore, 0.001)
        assertNull(event.subLabelDisplay)
        assertNull(event.endTime)
        assertTrue(event.hasClip)
    }
}
