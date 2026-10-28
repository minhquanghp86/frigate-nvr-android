package com.minhquanghp86.frigatenvr.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.minhquanghp86.frigatenvr.ui.LargeTitleHeader
import com.minhquanghp86.frigatenvr.ui.SegmentedControl

/**
 * Gộp "Sự kiện" và "Cần xem" vào một tab với segmented control — hai màn trước đây
 * gần như trùng nhau, giờ chỉ khác nhau ở bộ lọc.
 */
@Composable
fun MediaScreen(
    modifier: Modifier = Modifier,
    onOpenEvent: (String) -> Unit,
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }

    Column(modifier.fillMaxSize()) {
        LargeTitleHeader(
            title = "Sự kiện",
            subtitle = if (tab == 0) "Tất cả sự kiện" else "Mục Frigate đánh dấu cần xem",
        )
        SegmentedControl(
            options = listOf("Sự kiện", "Cần xem"),
            selectedIndex = tab,
            onSelect = { tab = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
        )
        if (tab == 0) {
            EventsScreen(modifier = Modifier, onOpenEvent = onOpenEvent, showHeader = false)
        } else {
            ReviewScreen(modifier = Modifier, showHeader = false)
        }
    }
}
