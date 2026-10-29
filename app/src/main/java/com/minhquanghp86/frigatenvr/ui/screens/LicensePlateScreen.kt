package com.minhquanghp86.frigatenvr.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.minhquanghp86.frigatenvr.ui.IOSBlue
import com.minhquanghp86.frigatenvr.ui.IOSCard
import com.minhquanghp86.frigatenvr.ui.IOSLabel
import com.minhquanghp86.frigatenvr.ui.IOSSecondaryLabel
import com.minhquanghp86.frigatenvr.ui.LargeTitleHeader
import com.minhquanghp86.frigatenvr.ui.rememberRepo

@Composable
fun LicensePlateScreen(
    onBack: () -> Unit,
    onSearch: (String) -> Unit = {},
) {
    val repo = rememberRepo()
    var plates by remember { mutableStateOf<List<String>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var filter by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        try {
            plates = repo.licensePlates()
        } catch (e: Exception) {
            error = e.message ?: "Không tải được biển số"
            plates = emptyList()
        }
    }

    Column(Modifier.fillMaxSize()) {
        LargeTitleHeader(
            title = "Biển số xe",
            subtitle = plates?.let { "${it.size} biển số đã nhận diện" } ?: "Đang tải…",
            trailing = {
                IconButton(onClick = onBack) {
                    Icon(Icons.Outlined.ArrowBack, contentDescription = "Quay lại", tint = IOSLabel)
                }
            },
        )

        OutlinedTextField(
            value = filter,
            onValueChange = { filter = it },
            placeholder = { Text("Tìm biển số…", fontSize = 14.sp) },
            singleLine = true,
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        )

        val data = plates
        when {
            data == null -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                CircularProgressIndicator()
            }

            data.isEmpty() -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text(
                    error ?: "Chưa có biển số nào được nhận diện.\nKiểm tra LPR đã bật trong Frigate và đã có sự kiện xe.",
                    color = IOSSecondaryLabel,
                    fontSize = 13.sp,
                )
            }

            else -> {
                val shown = data.filter { filter.isBlank() || it.contains(filter, ignoreCase = true) }
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(shown) { plate ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(IOSCard)
                                .clickable { onSearch(plate) }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                plate,
                                color = IOSLabel,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f),
                            )
                            Text("Xem sự kiện", color = IOSBlue, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}
