# Frigate NVR cho Android

App Android kết nối thẳng tới **API của Frigate NVR** (chạy như add-on trên Home Assistant): đăng nhập bằng tài khoản Frigate, xem camera trực tiếp, xem danh sách sự kiện, mục cần xem (review) và phát lại bản ghi theo ngày/giờ.

- Ngôn ngữ: **Kotlin + Jetpack Compose (Material 3)**
- Giao diện nền tối, thanh điều hướng 5 mục
- Tự build APK bằng **GitHub Actions** trên mỗi lần push/tag

## Tính năng hiện tại

| Mục | Nội dung |
|---|---|
| **Đăng nhập** | `POST /api/login` → JWT (cookie `frigate_token`) → mọi request gắn `Authorization: Bearer`. Tự đăng nhập lại khi token hết hạn, hỗ trợ Frigate tắt xác thực (chế độ ẩn danh), tùy chọn "tin chứng chỉ tự ký" cho TLS nội bộ. |
| **Camera** | Lưới camera (`latest.jpg`, 5s). Xem live chọn từ dropdown 4 luồng: **WebRTC** (qua proxy Frigate, có xác thực, trễ thấp nhất), **MP4** và **HLS** (go2rtc, luồng gốc fps đầy đủ), **MJPEG** (Frigate, chọn fps + độ phân giải, đo fps thực). |
| **Sự kiện** | Lưới thumbnail từ `/api/events`, bộ lọc camera/nhãn/chỉ có clip, phân trang vô hạn; chi tiết: snapshot có khung nhận diện, **clip mp4**, điểm nhận diện, vùng, thời gian. |
| **Cần xem** | `/api/review` (chưa xem/đã xem), cảnh báo vs phát hiện, mô tả; bấm để **phát clip** và **đánh dấu đã xem** (`POST /api/reviews/viewed`). |
| **Xem lại** | Chọn camera → ngày có ghi hình (`/api/recordings/summary`) → khung giờ (`/api/{camera}/recordings/summary`) → **phát HLS** qua nginx-vod: `/vod/{yyyy-mm}/{dd}/{hh}/{camera}/{tz}/master.m3u8`. |
| **Thông báo** | Foreground service theo dõi `/api/events`, bắn thông báo khi có đối tượng mới; bật/tắt trong Cài đặt (kênh "Thông báo sự kiện", có hành động Dừng). |
| **Sự kiện (hành động)** | Giữ vĩnh viễn (`POST /api/events/{id}/retain`), bỏ giữ, xoá sự kiện (`DELETE /api/events/{id}`), tải clip về Downloads. |
| **Hệ thống** | Nhật ký (`/api/logs/{service}`) và dung lượng ghi hình theo camera (`/api/recordings/storage`, yêu cầu admin). |
| **Cài đặt** | Sửa URL server/tài khoản/mật khẩu/tín certificate, xem phiên bản Frigate (`/api/version`) và sức khỏe (`/api/stats`: CPU, bộ nhớ, fps từng camera), đăng xuất (có xác nhận). |

## Cài đặt

1. Tải APK mới nhất: **Actions → run gần nhất → Artifact** (hoặc **Releases** nếu build từ tag `v*`).
2. Cài lên điện thoại (cho phép cài từ nguồn không xác định).
   - APK release ký bằng keystore cố định (`keystore/frigate-release.jks`) nên các bản sau **cập nhật đè được**.
   - Riêng bản `v0.1.5` cần **gỡ app cũ một lần** (bản cũ ký debug key của runner), từ đó về sau không phải gỡ nữa.
3. Mở app → nhập địa chỉ server và tài khoản Frigate.

### Cấu hình Frigate để app kết nối được

- Dùng port **8971** (đã bật xác thực, có TLS): ví dụ `https://192.168.1.100:8971`
- Tạo tài khoản ở **Frigate → Settings → Users** (mật khẩu tối thiểu 12 ký tự).
- Nếu Frigate dùng chứng chỉ tự ký (mặc định) → bật mục **"Tin chứng chỉ tự ký"** trong app.
- Port `5000` là cổng nội bộ không xác thực, thường **không** xuất ra LAN.
- Muốn dùng qua reverse-proxy/Nabu Casa thì nhập URL của proxy đó, app không ép phải kết nối trực tiếp.

## Build lokal

```bash
./gradlew assembleDebug      # app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease    # app/build/outputs/apk/release/app-release.apk (ký debug key)
```

Yêu cầu: JDK 17, Android SDK 34 (`local.properties` trỏ `sdk.dir`).

## CI (GitHub Actions)

Workflow: `.github/workflows/build-apk.yml` — chạy trên `ubuntu-latest` của GitHub (repo public nên không tốn phút Actions).

- Chạy khi push vào `main`, mở PR, hoặc đánh tag `v*` (và chạy tay được qua `workflow_dispatch`).
- Setup Java 17 + Android SDK + cache Gradle → `./gradlew assembleRelease`.
- Luôn upload APK vào **Artifacts**; với tag `v*` thì tự tạo **GitHub Release** kèm APK.
- `versionName`: `0.1.0-dev.<run number>` với push thường, đúng tên tag với tag `v*`.
- `versionCode` lấy từ `GITHUB_RUN_NUMBER` → luôn tăng.
- Repo **không** chứa IP/credential nội bộ; mọi thông tin kết nối do người dùng nhập trong app.

## Cấu trúc mã

```
app/src/main/java/com/minhquanghp86/frigatenvr/
├── FrigateApp.kt            # Application + container (repository dùng chung)
├── MainActivity.kt
├── data/
│   ├── Models.kt            # Event, ReviewItem, Config, DayRecordings...
│   ├── Session.kt           # ServerSession + trạng thái phiên
│   ├── Network.kt           # OkHttp (JWT, TLS tự ký), Retrofit FrigateApi
│   ├── FrigateRepository.kt # đăng nhập, tự login lại, DataStore, URL media
│   └── MjpegSource.kt       # giải mã luồng MJPEG -> Flow<Bitmap>
└── ui/
    ├── AppRoot.kt           # NavHost + thanh tab
    ├── Common.kt            # ảnh có JWT, VideoPlayer (mp4/HLS), helper
    ├── ViewModels.kt        # Camera / Sự kiện / Review / Timeline / Cài đặt
    └── screens/             # Login, Cameras, Events, Review, Timeline, Settings
```

## Giao diện

Phong cách iOS: nền đen, nhóm cài đặt inset-grouped, tiêu đề lớn, pill filter, segmented control, thanh tab trong mờ — kết hợp tính năng native (WebRTC, ExoPlayer, foreground service).

## Lộ trình

- **GĐ3 (chưa làm):** thông báo (Web Push/VAPID của Frigate), điều khiển camera/PTZ (`/api/camera/{name}/set/{feature}`), tìm kiếm nâng cao (`/api/events/search`, motion search), export (`/api/exports`), nhiều server, live WebRTC/go2rtc thay MJPEG.

## Tham khảo

- [Frigate HTTP API](https://docs.frigate.video/integrations/api/frigate-http-api/)
- [Authentication](https://docs.frigate.video/configuration/authentication/)
- Bản OpenAPI gốc: `docs/static/frigate-api.yaml` trong repo [blakeblackshear/frigate](https://github.com/blakeblackshear/frigate)
- Chi tiết endpoint đã dùng: [docs/API-FRIGATE.md](docs/API-FRIGATE.md)
