package com.minhquanghp86.frigatenvr.notify

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Yêu cầu điều hướng từ ngoài UI (thông báo, deep link). */
object DeepLinks {
    private val _route = MutableStateFlow<String?>(null)
    val route: StateFlow<String?> = _route.asStateFlow()

    fun openCamera(camera: String, openEvents: Boolean = false) {
        _route.value = if (openEvents) "camera/$camera?panel=events" else "camera/$camera"
    }

    fun openEvent(eventId: String, camera: String? = null) {
        _route.value = if (camera.isNullOrBlank()) {
            "event/$eventId"
        } else {
            "event/$eventId?camera=$camera"
        }
    }

    fun clear() {
        _route.value = null
    }
}
