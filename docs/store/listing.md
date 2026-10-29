# FriCam — Nội dung Google Play (store listing)

## Thông tin chung

| Trường | Giá trị |
|---|---|
| Tên ứng dụng (≤30) | **FriCam — Frigate NVR client** |
| Danh mục | Tools (hoặc Video Players & Editors) |
| Ngôn ngữ mặc định | Tiếng Việt; thêm English (en-US) |
| Loại | Ứng dụng, miễn phí, không quảng cáo |
| Liên hệ | https://github.com/minhquanghp86/frigate-nvr-android/issues |
| Chính sách riêng tư | https://minhquanghp86.github.io/frigate-nvr-android/privacy.html |

## Mô tả ngắn (≤80 ký tự)

- VI: `Xem và quản trị Frigate NVR: live, sự kiện, xem lại, khuôn mặt.`
- EN: `Unofficial Frigate NVR client: live, events, review, faces.`

## Mô tả đầy đủ (VI)

```
FriCam là ứng dụng KHÔNG CHÍNH THỨC để xem và quản trị máy chủ Frigate NVR do chính bạn vận hành.

YÊU CẦU: bạn cần một máy chủ Frigate (https://frigate.video) mà app kết nối tới. Ứng dụng không kèm máy chủ.

TÍNH NĂNG CHÍNH
• Live nhiều chế độ: WebRTC độ trễ thấp (qua proxy có xác thực của Frigate), MJPEG và HLS
• Toàn màn hình ngang, Picture-in-Picture, chọn chất lượng, đo fps thực
• Sự kiện: lưới ảnh, lọc camera/nhãn/vùng/thời gian, tìm kiếm, sự kiện tương tự
• Xem lại: timeline 24h kiểu Frigate, kéo playhead có ảnh xem trước, tải clip (export có tiến trình)
• Khuôn mặt: thư viện, đăng ký ảnh, train từ sự kiện, theo dõi người đã biết/chưa rõ
• Thông báo sự kiện (bật/tắt, lọc theo camera và nhãn), bấm thông báo mở đúng camera kèm danh sách sự kiện
• Cảnh báo/Review, nhật ký hệ thống, dung lượng, sức khoẻ bộ phát hiện, quản lý người dùng
• Giao diện tối kiểu iOS, tiếng Việt

DÙNG THỬ KHÔNG CẦN MÁY CHỦ
Mở app → bấm “Dùng bản demo” để kết nối máy chủ demo công khai của dự án Frigate.

GHI CHÚ
• Ứng dụng không chính thức, không liên kết với dự án Frigate. Tên Frigate thuộc về tác giả của nó.
• Dữ liệu của bạn chỉ đi tới máy chủ Frigate bạn cấu hình; app không gửi dữ liệu về nhà phát triển.
```

## Full description (EN)

```
FriCam is an UNOFFICIAL client for your self-hosted Frigate NVR server.

REQUIREMENT: you must run a Frigate server (https://frigate.video). The app does not include one.

FEATURES
• Live view: low-latency WebRTC through Frigate’s authenticated proxy, plus MJPEG and HLS
• Landscape fullscreen, Picture-in-Picture, quality selection, real fps measurement
• Events: thumbnail grid, filters (camera/label/zone/time), text search, similar-event search
• Review: Frigate-style 24h timeline with frame preview while scrubbing, clip export with progress
• Faces: library, register photos, train from events, known/unknown tracking
• Notifications: toggle, filter by camera and label, tap opens the right camera with its event list
• Alerts/Review, system logs, storage, detector health, user management
• Dark iOS-style UI

TRY WITHOUT A SERVER
On the login screen tap “Use demo” to connect to the project’s public Frigate demo.

NOTES
• Unofficial app, not affiliated with the Frigate project. “Frigate” is the property of its authors.
• Your data goes only to the Frigate server you configure; nothing is sent to the developer.
```

## App content (Play Console)

| Mục | Trả lời |
|---|---|
| Ads | Không |
| Sign-in details | “Mở app → bấm **Dùng bản demo** (hoặc nhập `https://demo.frigate.video`, để trống tài khoản/mật khẩu).” |
| Target audience | 18+ (giám sát an ninh) |
| Content rating | IARC: không bạo lực/ma tuý/cờ bạc; có nội dung camera an ninh |
| Data safety | Không thu thập/gửi dữ liệu về nhà phát triển; dữ liệu kết nối lưu cục bộ; ảnh/clip do người dùng tải về máy |
| Permissions | Internet, Network state, Notifications, Foreground service (data sync) |
| Chính sách riêng tư | URL ở trên |
| Quốc gia | Việt Nam trước, mở rộng sau |

## Tài sản

| Tệp | Kích thước | Ghi chú |
|---|---|---|
| `docs/store/icon-512.png` | 512×512 | Icon Play (nền tối + logo chim trắng) |
| `docs/store/feature-graphic-1024x500.png` | 1024×500 | Feature graphic |
| Screenshot | ≥2 ảnh điện thoại | Chụp: Camera (lưới), Live, Sự kiện, Timeline, Hệ thống |

## Ghi chú i18n

Toàn bộ chuỗi UI hiện nằm trực tiếp trong Compose (tiếng Việt). Bản EN hiện chỉ gồm store listing;
muốn UI song ngữ cần tách ~hundreds chuỗi sang `strings.xml` (đề xuất làm ở vòng riêng).
