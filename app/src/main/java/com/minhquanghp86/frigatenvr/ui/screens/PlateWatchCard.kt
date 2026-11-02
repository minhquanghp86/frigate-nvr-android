package com.minhquanghp86.frigatenvr.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.minhquanghp86.frigatenvr.data.FrigateEvent
import com.minhquanghp86.frigatenvr.ui.FrigateImage
import com.minhquanghp86.frigatenvr.ui.IOSBlue
import com.minhquanghp86.frigatenvr.ui.IOSCard
import com.minhquanghp86.frigatenvr.ui.IOSGreen
import com.minhquanghp86.frigatenvr.ui.IOSLabel
import com.minhquanghp86.frigatenvr.ui.IOSSecondaryLabel
import com.minhquanghp86.frigatenvr.ui.rememberRepo

/** Thẻ biển số đã nhận diện trên trang chủ Camera (ngay dưới thẻ Khuôn mặt). */
@Composable
fun PlateWatchCard(
    events: List<FrigateEvent>,
    modifier: Modifier = Modifier,
    onOpenAll: () -> Unit = {},
    onOpenEvent: (String) -> Unit = {},
) {
    val repo = rememberRepo()
    val plates = events.mapNotNull { it.recognizedPlate }.distinct()

    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(IOSCard)
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Outlined.DirectionsCar,
                contentDescription = null,
                tint = IOSGreen,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "Biển số",
                color = IOSLabel,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.weight(1f))
            Row(
                Modifier
                    .clip(RoundedCornerShape(99.dp))
                    .clickable(onClick = onOpenAll)
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Xem tất cả", color = IOSBlue, fontSize = 12.sp)
                Icon(
                    Icons.Outlined.ChevronRight,
                    contentDescription = null,
                    tint = IOSBlue,
                    modifier = Modifier.size(16.dp),
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(99.dp))
                    .background(IOSGreen.copy(alpha = 0.18f))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Text(
                    "Hôm nay ${plates.size} biển",
                    color = IOSGreen,
                    fontSize = 11.sp,
                )
            }
            plates.firstOrNull()?.let { plate ->
                Box(
                    Modifier
                        .clip(RoundedCornerShape(99.dp))
                        .background(Color(0x22FFFFFF))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Text(plate, color = IOSLabel, fontSize = 11.sp)
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        if (events.isEmpty()) {
            Text(
                "Hôm nay chưa nhận diện biển số nào.",
                color = IOSSecondaryLabel,
                fontSize = 12.sp,
            )
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(events.take(14), key = { it.id }) { event ->
                    Box(
                        Modifier
                            .size(width = 96.dp, height = 58.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.Black)
                            .clickable { onOpenEvent(event.id) },
                    ) {
                        FrigateImage(
                            url = repo.eventSnapshot(event.id),
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                            fallbackUrl = repo.bestSnapshot(event.camera, event.label),
                        )
                        Box(
                            Modifier
                                .align(Alignment.BottomStart)
                                .fillMaxWidth()
                                .background(Color(0xCC000000))
                                .padding(horizontal = 4.dp, vertical = 2.dp),
                        ) {
                            Text(
                                event.recognizedPlate ?: "—",
                                color = IOSGreen,
                                fontSize = 9.sp,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }
}
