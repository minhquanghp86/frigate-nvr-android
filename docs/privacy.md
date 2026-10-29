# FriCam — Chính sách quyền riêng tư / Privacy Policy

Cập nhật: **29/10/2026**

FriCam là ứng dụng **không chính thức** để xem và quản trị máy chủ **Frigate NVR** do chính bạn vận hành. Ứng dụng **không thu thập, không gửi dữ liệu về máy chủ của nhà phát triển** và không có quảng cáo.

## 1. Dữ liệu được lưu trên thiết bị

Ứng dụng lưu cục bộ (vùng riêng của app, không chia sẻ với ứng dụng khác):

- Địa chỉ máy chủ Frigate bạn nhập (ví dụ `https://192.168.1.10:8971`)
- Tên đăng nhập; mật khẩu **chỉ khi bạn bật “Ghi nhớ mật khẩu”**
- Token phiên (JWT) do máy chủ Frigate cấp
- Tuỳ chọn giao diện: thông báo bật/tắt, bộ lọc camera/nhãn, mức chất lượng xem, địa chỉ go2rtc
- Nhật ký lỗi ứng dụng (chỉ trên máy, dùng để bạn tự chẩn đoán)

Bạn có thể xoá toàn bộ bằng cách **Đăng xuất** trong ứng dụng hoặc xoá dữ liệu ứng dụng trong Cài đặt Android.

## 2. Dữ liệu truyền qua mạng

Ứng dụng chỉ kết nối tới:

- Máy chủ Frigate do bạn cấu hình (LAN/Internet/VPN của bạn)
- Nếu bạn chọn **“Dùng bản demo”**: máy chủ công khai `https://demo.frigate.video` do dự án Frigate vận hành

Mọi yêu cầu gửi kèm thông tin đăng nhập của bạn tới máy chủ đó. Ứng dụng không gửi dữ liệu tới bất kỳ bên thứ ba nào khác, không dùng SDK quảng cáo/phân tích.

## 3. Ảnh và video

- Ảnh/clip bạn xem được tải trực tiếp từ máy chủ Frigate của bạn.
- Khi bạn chọn **Tải**, tệp được lưu vào thư mục `Downloads/FriCam` trên thiết bị (qua MediaStore).
- Ứng dụng không tải ảnh lên bất kỳ đâu, trừ khi bạn chủ động dùng chức năng gửi ảnh để đăng ký khuôn mặt vào **chính máy chủ Frigate của bạn**.

## 4. Quyền ứng dụng

| Quyền | Mục đích |
|---|---|
| `INTERNET`, `ACCESS_NETWORK_STATE` | Kết nối máy chủ Frigate của bạn |
| `POST_NOTIFICATIONS` | Hiển thị thông báo sự kiện (bạn có thể tắt) |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_DATA_SYNC` | Dịch vụ nền theo dõi sự kiện khi bạn bật thông báo |

Ứng dụng **không** yêu cầu quyền truy cập danh bạ, vị trí, micro, camera hay bộ nhớ ngoài.

## 5. Trẻ em

Ứng dụng dành cho người dùng trưởng thành quản trị hệ thống camera giám sát; không hướng tới trẻ em.

## 6. Thay đổi chính sách

Chính sách có thể được cập nhật khi ứng dụng thêm tính năng. Bản mới nhất luôn nằm tại trang này và trong mục **Cài đặt → Ứng dụng**.

## 7. Liên hệ

Mở issue tại: <https://github.com/minhquanghp86/frigate-nvr-android/issues>

---

# Privacy Policy (English)

Last updated: **2026-10-29**

FriCam is an **unofficial** client for a **Frigate NVR** server that you operate yourself. The app does **not** collect or send any data to the developer’s servers and contains no ads.

- **Stored on device:** server URL, username, password (only if “Remember password” is enabled), session token, UI preferences, local crash log. Removed by logging out or clearing app data.
- **Network:** requests are sent only to your Frigate server, or to `https://demo.frigate.video` if you choose “Use demo”. No third-party analytics or advertising SDKs.
- **Media:** images/clips are streamed from your server; downloads are saved to `Downloads/FriCam` via MediaStore. Nothing is uploaded except when you explicitly register a face image to your own Frigate server.
- **Permissions:** network access, notifications (optional), foreground service for event polling when notifications are enabled.
- **Children:** not directed to children.
- **Contact:** <https://github.com/minhquanghp86/frigate-nvr-android/issues>
