package com.minhquanghp86.frigatenvr.notify

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Yêu cầu điều hướng từ ngoài UI (thông báo, deep link). */
object DeepLinks {
    private val _route = MutableStateFlow<String?>(null)
    val route: StateFlow<String?> = _route.asStateFlow()

    fun openEvent(eventId: String) {
        _route.value = "event/$eventId"
    }

    fun clear() {
        _route.value = null
    }
}
