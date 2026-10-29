package com.minhquanghp86.frigatenvr.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.minhquanghp86.frigatenvr.ui.IOSBlue
import com.minhquanghp86.frigatenvr.ui.IOSLabel
import com.minhquanghp86.frigatenvr.ui.IOSSecondaryLabel
import com.minhquanghp86.frigatenvr.ui.LargeTitleHeader

private data class PolicySection(val heading: String, val body: String)

private val POLICY_VI = listOf(
    PolicySection(
        "1. Dữ liệu lưu trên thiết bị",
        "Ứng dụng lưu cục bộ (vùng riêng của app, không chia sẻ với ứng dụng khác): địa chỉ máy chủ " +
            "Frigate bạn nhập; tên đăng nhập; mật khẩu chỉ khi bạn bật “Ghi nhớ mật khẩu”; token phiên " +
            "do máy chủ cấp; tuỳ chọn giao diện (thông báo, bộ lọc camera/nhãn, chất lượng xem, địa chỉ " +
            "go2rtc); nhật ký lỗi ứng dụng. Bạn có thể xoá toàn bộ bằng cách Đăng xuất hoặc xoá dữ liệu " +
            "ứng dụng trong Cài đặt Android.",
    ),
    PolicySection(
        "2. Dữ liệu truyền qua mạng",
        "Ứng dụng chỉ kết nối tới máy chủ Frigate do bạn cấu hình (LAN/Internet/VPN của bạn) hoặc tới " +
            "https://demo.frigate.video nếu bạn chọn “Dùng bản demo”. Ứng dụng không gửi dữ liệu tới " +
            "máy chủ của nhà phát triển, không dùng SDK quảng cáo hay phân tích.",
    ),
    PolicySection(
        "3. Ảnh và video",
        "Ảnh/clip được tải trực tiếp từ máy chủ Frigate của bạn. Khi bạn chọn Tải, tệp được lưu vào " +
            "thư mục Downloads/FriCam trên thiết bị. Ứng dụng không tải ảnh lên bất kỳ đâu, trừ khi bạn " +
            "chủ động đăng ký ảnh khuôn mặt vào chính máy chủ Frigate của bạn.",
    ),
    PolicySection(
        "4. Quyền ứng dụng",
        "INTERNET và ACCESS_NETWORK_STATE: kết nối máy chủ Frigate. POST_NOTIFICATIONS: hiển thị thông " +
            "báo sự kiện (có thể tắt). FOREGROUND_SERVICE (data sync): theo dõi sự kiện khi bạn bật " +
            "thông báo. Ứng dụng không yêu cầu danh bạ, vị trí, micro, camera hay bộ nhớ ngoài.",
    ),
    PolicySection(
        "5. Trẻ em",
        "Ứng dụng dành cho người dùng trưởng thành quản trị hệ thống camera giám sát; không hướng tới trẻ em.",
    ),
    PolicySection(
        "6. Thay đổi chính sách",
        "Chính sách có thể được cập nhật khi ứng dụng thêm tính năng. Bản mới nhất luôn nằm trong mục " +
            "Cài đặt → Ứng dụng → Chính sách quyền riêng tư.",
    ),
    PolicySection(
        "7. Liên hệ",
        "Mở issue tại github.com/minhquanghp86/frigate-nvr-android/issues.",
    ),
)

private val POLICY_EN = listOf(
    PolicySection(
        "What is stored on the device",
        "Server URL, username, password (only if “Remember password” is enabled), session token, UI " +
            "preferences (notifications, camera/label filters, quality, go2rtc address) and a local " +
            "crash log. Removed by logging out or clearing app data.",
    ),
    PolicySection(
        "Network",
        "Requests go only to the Frigate server you configure, or to https://demo.frigate.video if you " +
            "choose “Use demo”. No data is sent to the developer, and there are no advertising or " +
            "analytics SDKs.",
    ),
    PolicySection(
        "Media",
        "Images and clips are streamed from your server. Downloads are saved to Downloads/FriCam on the " +
            "device. Nothing is uploaded except face images you explicitly register on your own server.",
    ),
    PolicySection(
        "Permissions",
        "Network access, notifications (optional) and a foreground data-sync service while notifications " +
            "are enabled. No contacts, location, microphone, camera or external storage access.",
    ),
    PolicySection(
        "Children",
        "Not directed to children; intended for adults managing a surveillance system.",
    ),
    PolicySection(
        "Contact",
        "https://github.com/minhquanghp86/frigate-nvr-android/issues",
    ),
)

@Composable
fun PrivacyScreen(onBack: () -> Unit) {
    val context = LocalContext.current

    Column(Modifier.fillMaxSize()) {
        LargeTitleHeader(
            title = "Quyền riêng tư",
            subtitle = "Cập nhật 29/10/2026",
            trailing = {
                IconButton(onClick = onBack) {
                    Icon(Icons.Outlined.ArrowBack, contentDescription = "Quay lại", tint = IOSLabel)
                }
            },
        )

        LazyColumn(
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item {
                Text(
                    "FriCam là ứng dụng KHÔNG CHÍNH THỨC để xem và quản trị máy chủ Frigate NVR do " +
                        "chính bạn vận hành. Ứng dụng không thu thập, không gửi dữ liệu về máy chủ của " +
                        "nhà phát triển và không có quảng cáo.",
                    color = IOSLabel,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(12.dp))
            }
            POLICY_VI.forEach { section ->
                item {
                    Text(
                        section.heading,
                        color = IOSLabel,
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        section.body,
                        color = IOSSecondaryLabel,
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                    )
                    Spacer(Modifier.height(14.dp))
                }
            }

            item {
                Spacer(Modifier.height(6.dp))
                Text(
                    "Privacy Policy (English)",
                    color = IOSLabel,
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(8.dp))
            }
            POLICY_EN.forEach { section ->
                item {
                    Text(section.heading, color = IOSLabel, style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        section.body,
                        color = IOSSecondaryLabel,
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                    )
                    Spacer(Modifier.height(14.dp))
                }
            }

            item {
                Spacer(Modifier.height(8.dp))
                Row {
                    TextButton(
                        onClick = {
                            runCatching {
                                context.startActivity(
                                    Intent(
                                        Intent.ACTION_VIEW,
                                        Uri.parse(
                                            "https://minhquanghp86.github.io/frigate-nvr-android/privacy.html",
                                        ),
                                    ),
                                )
                            }
                        },
                    ) {
                        Icon(
                            Icons.Outlined.OpenInNew,
                            contentDescription = null,
                            tint = IOSBlue,
                            modifier = Modifier.height(16.dp),
                        )
                        Spacer(Modifier.height(0.dp))
                        Text("Bản web", color = IOSBlue, fontSize = 13.sp)
                    }
                }
                Spacer(Modifier.height(20.dp))
            }
        }
    }
}
