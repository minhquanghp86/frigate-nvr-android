package com.minhquanghp86.frigatenvr.ui

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Yêu cầu mở trình phát toàn màn hình (xoay ngang) từ bất kỳ màn hình nào. */
data class PlaybackRequest(val url: String, val startPositionMs: Long = 0L)

object PlayerStore {
    private val _request = MutableStateFlow<PlaybackRequest?>(null)
    val request: StateFlow<PlaybackRequest?> = _request.asStateFlow()

    fun open(url: String, startPositionMs: Long = 0L) {
        _request.value = PlaybackRequest(url, startPositionMs)
    }

    fun clear() {
        _request.value = null
    }
}
