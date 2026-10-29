package com.minhquanghp86.frigatenvr.ui

import kotlinx.coroutines.flow.MutableStateFlow

/** Chuyển truy vấn tìm kiếm giữa các màn (vd. bấm biển số -> tab Sự kiện). */
object SearchStore {
    val pending = MutableStateFlow<String?>(null)

    fun request(query: String) {
        pending.value = query
    }

    fun consume(): String? {
        val value = pending.value
        pending.value = null
        return value
    }
}
