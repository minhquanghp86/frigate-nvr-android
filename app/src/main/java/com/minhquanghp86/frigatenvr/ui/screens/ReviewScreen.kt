package com.minhquanghp86.frigatenvr.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Done
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.minhquanghp86.frigatenvr.data.ReviewItem
import com.minhquanghp86.frigatenvr.ui.ErrorBox
import com.minhquanghp86.frigatenvr.ui.FrigateImage
import com.minhquanghp86.frigatenvr.ui.LoadingBox
import com.minhquanghp86.frigatenvr.ui.ReviewViewModel
import com.minhquanghp86.frigatenvr.ui.ScreenHeader
import com.minhquanghp86.frigatenvr.ui.VideoPlayer
import com.minhquanghp86.frigatenvr.ui.fmtDateTime
import com.minhquanghp86.frigatenvr.ui.fmtDuration
import com.minhquanghp86.frigatenvr.ui.rememberRepo

@Composable
fun ReviewScreen(modifier: Modifier = Modifier) {
    val vm: ReviewViewModel = viewModel()
    val items by vm.items.collectAsState()
    val loading by vm.loading.collectAsState()
    val error by vm.error.collectAsState()
    val unreadOnly by vm.unreadOnly.collectAsState()

    Column(modifier.fillMaxSize()) {
        ScreenHeader(
            title = "Cần xem",
            subtitle = "${items.size} mục " + if (unreadOnly) "chưa xem" else "đã xem",
            actions = {
                IconButton(onClick = { vm.refresh() }) {
                    Icon(Icons.Outlined.Refresh, contentDescription = "Tải lại")
                }
            },
        )

        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            AssistChip(
                onClick = { vm.setUnreadOnly(true) },
                label = { Text(if (unreadOnly) "Chưa xem ✓" else "Chưa xem", fontSize = 12.sp) },
            )
            AssistChip(
                onClick = { vm.setUnreadOnly(false) },
                label = { Text(if (!unreadOnly) "Đã xem ✓" else "Đã xem", fontSize = 12.sp) },
            )
        }

        when {
            loading && items.isEmpty() -> LoadingBox()
            error != null && items.isEmpty() -> ErrorBox(error!!, onRetry = { vm.refresh() })
            items.isEmpty() -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text(
                    if (unreadOnly) "Không còn mục nào chưa xem. Tốt lắm!" else "Không có mục nào.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            else -> LazyColumn(
                contentPadding = PaddingValues(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(items, key = { it.id }) { item ->
                    ReviewRow(
                        item = item,
                        onMarkViewed = { vm.markViewed(item) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ReviewRow(item: ReviewItem, onMarkViewed: () -> Unit) {
    val repo = rememberRepo()
    var expanded by remember(item.id) { mutableStateOf(false) }
    val alert = item.severity == "alert"
    val thumbEvent = item.eventIds.firstOrNull()

    Card(
        onClick = { expanded = !expanded },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .width(92.dp)
                        .height(62.dp)
                        .background(Color(0xFF000000), RoundedCornerShape(6.dp)),
                ) {
                    if (thumbEvent != null) {
                        FrigateImage(
                            url = repo.eventThumbnail(thumbEvent),
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                    } else {
                        Box(Modifier.fillMaxSize(), Alignment.Center) {
                            Text("—", color = Color(0xFF777777))
                        }
                    }
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (alert) "Cảnh báo" else "Phát hiện",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (alert) Color(0xFFFBBF24) else MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            item.camera,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        fmtDateTime(item.startEpoch),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    val duration = (item.endEpoch - item.startEpoch).coerceAtLeast(0.0)
                    if (duration > 0) {
                        Text(
                            "kéo dài ${fmtDuration(duration)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    item.description?.takeIf { it.isNotBlank() }?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            if (expanded) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                ) {
                    if (item.id.isNotBlank()) {
                        VideoPlayer(
                            url = repo.reviewClip(item.id),
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(16f / 9f),
                        )
                    } else {
                        Text(
                            "Mục này không có clip.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                    }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(onClick = onMarkViewed) {
                            Icon(
                                Icons.Outlined.Done,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("Đánh dấu đã xem", fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}
