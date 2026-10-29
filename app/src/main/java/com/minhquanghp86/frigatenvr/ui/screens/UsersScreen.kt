package com.minhquanghp86.frigatenvr.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.minhquanghp86.frigatenvr.data.FrigateUser
import com.minhquanghp86.frigatenvr.ui.IOSBlue
import com.minhquanghp86.frigatenvr.ui.IOSCard
import com.minhquanghp86.frigatenvr.ui.IOSGreen
import com.minhquanghp86.frigatenvr.ui.IOSLabel
import com.minhquanghp86.frigatenvr.ui.IOSSecondaryLabel
import com.minhquanghp86.frigatenvr.ui.LargeTitleHeader
import com.minhquanghp86.frigatenvr.ui.rememberRepo

@Composable
fun UsersScreen(
    currentUsername: String,
    onBack: () -> Unit,
) {
    val repo = rememberRepo()
    var users by remember { mutableStateOf<List<FrigateUser>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        try {
            users = repo.users()
        } catch (e: Exception) {
            error = e.message ?: "Không tải được danh sách người dùng"
            users = emptyList()
        }
    }

    Column(Modifier.fillMaxSize()) {
        LargeTitleHeader(
            title = "Người dùng",
            subtitle = users?.let { "${it.size} tài khoản" } ?: "Đang tải…",
            trailing = {
                IconButton(onClick = onBack) {
                    Icon(Icons.Outlined.ArrowBack, contentDescription = "Quay lại", tint = IOSLabel)
                }
            },
        )

        val data = users
        when {
            data == null -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                CircularProgressIndicator()
            }

            else -> LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                error?.let { message ->
                    item {
                        Text(
                            message,
                            color = IOSSecondaryLabel,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                    }
                }
                if (data.isEmpty()) {
                    item {
                        Text(
                            "Không có dữ liệu. Danh sách người dùng chỉ hiển thị với tài khoản quản trị (admin).",
                            color = IOSSecondaryLabel,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                    }
                }
                items(data) { user ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(IOSCard)
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                user.username + if (user.username == currentUsername) " (bạn)" else "",
                                color = IOSLabel,
                                fontSize = 15.sp,
                            )
                            Text(
                                if (user.role == "admin") "Quản trị viên" else "Người xem",
                                color = IOSSecondaryLabel,
                                fontSize = 11.sp,
                            )
                        }
                        Row(
                            Modifier
                                .clip(RoundedCornerShape(99.dp))
                                .background(
                                    if (user.role == "admin") {
                                        IOSBlue.copy(alpha = 0.18f)
                                    } else {
                                        IOSGreen.copy(alpha = 0.16f)
                                    },
                                )
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                user.role,
                                color = if (user.role == "admin") IOSBlue else IOSGreen,
                                fontSize = 10.sp,
                            )
                        }
                        Spacer(Modifier.width(2.dp))
                    }
                }
                item {
                    Text(
                        "Tạo/sửa tài khoản và mật khẩu cần làm trên Frigate (Settings → Người dùng).",
                        color = IOSSecondaryLabel,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 6.dp, start = 4.dp),
                    )
                }
            }
        }
    }
}
