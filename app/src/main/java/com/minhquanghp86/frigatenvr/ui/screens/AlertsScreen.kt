package com.minhquanghp86.frigatenvr.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.minhquanghp86.frigatenvr.data.ReviewItem
import com.minhquanghp86.frigatenvr.ui.FrigateImage
import com.minhquanghp86.frigatenvr.ui.IOSCard
import com.minhquanghp86.frigatenvr.ui.IOSGreen
import com.minhquanghp86.frigatenvr.ui.IOSLabel
import com.minhquanghp86.frigatenvr.ui.IOSOrange
import com.minhquanghp86.frigatenvr.ui.IOSRed
import com.minhquanghp86.frigatenvr.ui.IOSSecondaryLabel
import com.minhquanghp86.frigatenvr.ui.LargeTitleHeader
import com.minhquanghp86.frigatenvr.ui.fmtDateTime
import com.minhquanghp86.frigatenvr.ui.relTime
import com.minhquanghp86.frigatenvr.ui.rememberRepo
import kotlinx.coroutines.launch

private data class ReviewFilter(val label: String, val reviewed: Boolean?, val severity: String?)

/** Danh sách Cảnh báo/Review của Frigate (alert vs detection, đã xem hay chưa). */
@Composable
fun AlertsScreen(
    onBack: () -> Unit,
    onPlayClip: (String, String) -> Unit = { _, _ -> },
) {
    val repo = rememberRepo()
    val scope = rememberCoroutineScope()
    var items by remember { mutableStateOf<List<ReviewItem>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var filterIndex by remember { mutableStateOf(0) }

    val filters = listOf(
        ReviewFilter("Chưa xem", false, null),
        ReviewFilter("Alert", null, "alert"),
        ReviewFilter("Detection", null, "detection"),
        ReviewFilter("Đã xem", true, null),
        ReviewFilter("Tất cả", null, null),
    )
    val filter = filters[filterIndex]

    fun load() {
        scope.launch {
            error = null
            try {
                items = repo.reviewsList(
                    limit = 80,
                    reviewed = filter.reviewed,
                    severity = filter.severity,
                )
            } catch (e: Exception) {
                error = e.message ?: "Không tải được danh sách cảnh báo"
                items = emptyList()
            }
        }
    }

    LaunchedEffect(filterIndex) { load() }

    Column(Modifier.fillMaxSize()) {
        LargeTitleHeader(
            title = "Cảnh báo",
            subtitle = items?.let { "${it.size} mục" } ?: "Đang tải…",
            trailing = {
                IconButton(onClick = onBack) {
                    Icon(Icons.Outlined.ArrowBack, contentDescription = "Quay lại", tint = IOSLabel)
                }
            },
        )

        Row(
            Modifier.padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            filters.forEachIndexed { index, item ->
                FilterChip(
                    selected = index == filterIndex,
                    onClick = { filterIndex = index },
                    label = { Text(item.label, fontSize = 12.sp) },
                )
            }
        }

        error?.let {
            Text(it, color = IOSRed, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 20.dp))
        }
        message?.let {
            Text(it, color = IOSGreen, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 20.dp))
        }

        val data = items
        if (data == null) {
            Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
            return@Column
        }

        if (data.isEmpty()) {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text("Không có mục nào trong bộ lọc này.", color = IOSSecondaryLabel, fontSize = 13.sp)
            }
            return@Column
        }

        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(data, key = { it.id }) { review ->
                val thumbEvent = review.eventIds.firstOrNull()
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(IOSCard)
                        .clickable { onPlayClip(repo.reviewClip(review.id), review.id) }
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .width(112.dp)
                            .aspectRatio(16f / 9f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Black),
                    ) {
                        FrigateImage(
                            url = thumbEvent?.let { repo.eventThumbnail(it) }
                                ?: repo.cameraLatest(review.camera),
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                        Box(
                            Modifier
                                .align(Alignment.Center)
                                .size(26.dp)
                                .background(Color(0x88000000), RoundedCornerShape(99.dp)),
                            Alignment.Center,
                        ) {
                            Icon(
                                Icons.Outlined.PlayArrow,
                                contentDescription = "Phát",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                review.camera,
                                color = IOSLabel,
                                fontSize = 14.sp,
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                review.severity,
                                color = Color.White,
                                fontSize = 9.sp,
                                modifier = Modifier
                                    .background(
                                        if (review.severity == "alert") IOSRed else IOSOrange,
                                        RoundedCornerShape(99.dp),
                                    )
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                        Text(
                            (review.data?.description ?: "hoạt động")
                                .ifBlank { "hoạt động" },
                            color = IOSSecondaryLabel,
                            fontSize = 11.sp,
                            maxLines = 1,
                        )
                        Text(
                            relTime(review.startEpoch),
                            color = IOSSecondaryLabel,
                            fontSize = 10.sp,
                        )
                        Text(
                            fmtDateTime(review.startEpoch),
                            color = IOSSecondaryLabel,
                            fontSize = 10.sp,
                        )
                    }
                    if (!review.reviewed) {
                        TextButton(
                            onClick = {
                                scope.launch {
                                    try {
                                        repo.markReviewsViewed(listOf(review.id), true)
                                        items = data.map {
                                            if (it.id == review.id) it.copy(reviewed = true) else it
                                        }
                                        message = "Đã đánh dấu đã xem"
                                    } catch (e: Exception) {
                                        error = e.message ?: "Không đánh dấu được"
                                    }
                                }
                            },
                        ) {
                            Icon(
                                Icons.Outlined.CheckCircle,
                                contentDescription = "Đã xem",
                                tint = IOSGreen,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    } else {
                        Icon(
                            Icons.Outlined.CheckCircle,
                            contentDescription = "Đã xem",
                            tint = Color(0xFF3A3A3C),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }
    }
}
