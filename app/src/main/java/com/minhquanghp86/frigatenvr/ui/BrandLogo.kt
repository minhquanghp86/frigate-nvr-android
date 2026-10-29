package com.minhquanghp86.frigatenvr.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Logo FriCam vẽ trực tiếp bằng Canvas (chim cách điệu + nền navy).
 *
 * Vẽ bằng code thay vì painterResource để tránh lỗi
 * "Only VectorDrawables and rasterized asset types are supported" khi resource đổi định dạng
 * (từng crash ở bản v0.11.0 do dùng adaptive-icon `mipmap/ic_launcher`).
 */
@Composable
fun FriCamLogo(size: Dp = 28.dp, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.28f))
            .background(Brush.linearGradient(listOf(Color(0xFF0A1526), Color(0xFF12375E)))),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val k = this.size.minDimension / 108f
            // Nhóm gốc scale 0.72 quanh (52.5, 55) như vector launcher.
            fun x(v: Float) = (52.5f + (v - 52.5f) * 0.72f) * k
            fun y(v: Float) = (55f + (v - 55f) * 0.72f) * k

            val brush = Brush.linearGradient(
                colors = listOf(Color.White, Color(0xFF8FBCFF)),
                start = Offset(x(20f), y(25f)),
                end = Offset(x(95f), y(85f)),
            )

            // Cánh trên
            val top = Path().apply {
                moveTo(x(50f), y(55f))
                cubicTo(x(62f), y(45f), x(76f), y(37f), x(98f), y(29f))
                cubicTo(x(85f), y(48f), x(68f), y(59f), x(53f), y(61f))
                close()
            }
            // Cánh dưới
            val bottom = Path().apply {
                moveTo(x(50f), y(55f))
                cubicTo(x(36f), y(63f), x(22f), y(71f), x(7f), y(81f))
                cubicTo(x(21f), y(84f), x(37f), y(78f), x(58f), y(64f))
                close()
            }
            // Thân + đuôi
            val body = Path().apply {
                moveTo(x(48f), y(50f))
                cubicTo(x(58f), y(52f), x(64f), y(58f), x(67f), y(63f))
                lineTo(x(79f), y(64f))
                lineTo(x(69f), y(67f))
                lineTo(x(78f), y(70f))
                cubicTo(x(66f), y(73f), x(54f), y(69f), x(44f), y(58f))
                close()
            }
            // Đầu
            val head = Path().apply {
                addOval(
                    androidx.compose.ui.geometry.Rect(
                        center = Offset(x(48f), y(50f)),
                        radius = 3.6f * 0.72f * k,
                    ),
                )
            }
            listOf(top, bottom, body, head).forEach { drawPath(it, brush) }
        }
    }
}
