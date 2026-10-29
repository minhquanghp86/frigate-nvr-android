# FriCam — Ứng dụng Android cho Frigate NVR

**FriCam** là ứng dụng Android **không chính thức** kết nối trực tiếp tới **HTTP API của Frigate NVR**
(thường chạy như add-on trên Home Assistant) để xem camera, theo dõi sự kiện, xem lại bản ghi và quản trị hệ thống.

- Ngôn ngữ: **Kotlin** · Giao diện **Jetpack Compose (Material 3)** theo phong cách iOS
- **Giao diện sáng/tối** (chuyển nhanh trên tab Camera, có lưu lựa chọn)
- `minSdk 26` · `compileSdk/targetSdk 36` (Android 16 — đáp ứng yêu cầu Google Play từ 31/08/2026)
- Tự build **APK + AAB** bằng GitHub Actions; mỗi tag `v*` tạo GitHub Release

> Ứng dụng không kèm máy chủ. Bạn cần một máy chủ Frigate của riêng mình.
> Có thể dùng thử ngay bằng nút **“Dùng bản demo”** (`https://demo.frigate.video`, không cần tài khoản).

---

## 1. Tính năng

### Camera
- Lưới camera với ảnh mới nhất, nhãn **LIVE/REC**, fps, số sự kiện hôm nay, sự kiện gần nhất.
- Header cố định: logo, trạng thái `N camera · M đang ghi`, chip **Thông báo**, nút **sáng/tối**, tải lại.
- Live 4 chế độ:
  - **WebRTC/WHEP** qua proxy có xác thực của Frigate (độ trễ thấp nhất, fps gốc)
  - **MP4** và **HLS** qua go2rtc
  - **MJPEG** (Frigate, chọn fps/độ phân giải, đo fps thực)
- Toàn màn hình ngang, Picture-in-Picture, fit/crop, mute, overlay fps/bitrate.
- Bottom sheet quản trị nhanh: Camera, Phát hiện, Ghi hình, Chụp ảnh.
- **Cast lên TV/Chromecast** 3 cách:
  - **Google Cast (LAN)**: Cast SDK + Default Media Receiver, phát HLS go2rtc (cần điện thoại cùng WiFi).
  - **Qua Home Assistant**: nhập HA URL + long-lived token; app gọi `media_player.play_media` với URL HLS
    → cast được cả khi điện thoại ở ngoài LAN (miễn tới được HA).
  - **Panel Darboard**: nhập Darboard server URL; app gọi `POST /screens/act {act:"url"}` để mở
    `cam.html?src=<camera>` trên panel (server tự proxy go2rtc).

### Sự kiện
- Lưới thumbnail; lọc **camera / nhãn / vùng / thời gian (1h, 24h, 7 ngày) / chỉ có clip**.
- Tìm kiếm text (`/api/events/search`) và **sự kiện tương tự** (thumbnail search).
- Chi tiết: snapshot có box nhận diện, clip, điểm, vùng, thuộc tính; giữ/xoá, tải clip/ảnh.
- **Sửa thông tin sự kiện**: nhãn phụ, biển số, thuộc tính, đánh dấu false positive.
- **Thêm vào thư viện khuôn mặt**: train bằng `event_id`, hoặc **cắt quanh box nhận diện +20% viền**
  rồi upload (`/api/faces/{name}/register`) — dùng khi Frigate không tự tìm thấy mặt trong ảnh toàn cảnh.

### Xem lại
- Timeline 24h kiểu Frigate: vạch giờ, dải ghi hình, tick sự kiện theo nhãn, playhead kéo độc lập.
- Mở tab → phát **tại thời điểm hiện tại** (ngày cũ: 00:00 hoặc khung sớm nhất có dữ liệu).
- Không tua vượt thời điểm hiện tại (tránh vùng chưa ghi hình / lỗi “Không phát được video”).
- Kéo playhead → hiện **ảnh xem trước** chụp từ bản ghi đúng thời điểm.
- Hai kiểu tải:
  - **Tải**: export job trên Frigate (tiến trình 2 bước) → file MP4 hoàn chỉnh.
  - **Tải nhanh**: cắt trực tiếp `clip.mp4`.
  - Cả hai lưu vào `Downloads/FriCam`, có notification tiến trình trên thanh trạng thái.

### Khuôn mặt
- Thư viện: tạo người, đăng ký ảnh từ thư viện, xoá, **đổi tên**.
- Chi tiết từng người: xem **toàn bộ ảnh đã train**, xoá từng ảnh.
- **Sự kiện khuôn mặt** + thẻ theo dõi trên trang chủ (đã biết / chưa rõ theo `sub_label`),
  chạm để mở sự kiện hoặc gán tên (train).
- Phát hiện `face_recognition.enabled = false` → cảnh báo và chặn thao tác liên quan.

### Cảnh báo (Review)
- Lọc **Chưa xem / Alert / Detection / Đã xem / Tất cả**, phát clip, đánh dấu đã xem
  (`POST /api/reviews/viewed`).

### Hệ thống
- **Hero**: phiên bản Frigate, uptime, CPU/RAM (`cpu_usages["frigate.full_system"]`), tốc độ phát hiện.
- **Bộ phát hiện**: 3 biểu đồ (suy luận ms, CPU, RAM), cửa sổ 60 điểm, poll 3 giây.
- **Dung lượng**: bản ghi, ảnh & clip, `/tmp/cache`, `/dev/shm` — thanh tiến trình dày, màu theo ngưỡng.
- **Lưu trữ Camera**: dung lượng + % + băng thông MiB/giờ.
- **Cảnh báo**: xem, **Đã hiểu**, **Ẩn vĩnh viễn** (`acknowledge` / `mute`).
- **Clip đã xuất**: tải hoặc **xoá** (`POST /api/exports/delete`).

### Quản trị khác
- **Cấu hình Camera**: 10 switch (enabled, detect, motion, recordings, snapshots, audio, notifications,
  review_alerts, review_detections, object_descriptions) + **profile cấu hình** (`/api/profiles`).
- **Người dùng**: thêm (mật khẩu ≥ 12 ký tự, chọn role), đổi vai trò, đặt mật khẩu, xoá (trừ `admin`).
- **Biển số (LPR)**: danh sách biển đã nhận diện, chạm để tìm sự kiện.
- **Nhật ký** 3 nguồn (frigate/go2rtc/app) có Copy/Share; **Dung lượng** chi tiết.

### Thông báo
- Foreground service theo dõi sự kiện mới; lọc theo **camera** và **nhãn**.
- Bật/tắt nhanh bằng chip trên tab Camera hoặc trong Cài đặt.
- **Ảnh phát hiện trong thông báo** (BigPicture + large icon, thu nhỏ ≤1080px),
  tiêu đề “Phát hiện người” / “Phát hiện người: \<tên\>”.
- Icon thông báo dùng **logo FriCam** (vector monochrome).
- Chạm thông báo → mở **live camera kèm panel sự kiện** của camera đó.

---

## 2. Cài đặt

1. Tải APK mới nhất ở [**Releases**](https://github.com/minhquanghp86/frigate-nvr-android/releases).
2. Cài lên điện thoại (cho phép cài từ nguồn không xác định).
   - APK release ký bằng keystore cố định trong repo → các bản sau **cập nhật đè được**.
   - Người dùng bản rất cũ (≤ v0.1.4) cần gỡ một lần.
3. Mở app → nhập địa chỉ máy chủ (mặc định `https://192.168.10.2:8971`) và tài khoản Frigate,
   hoặc bấm **“Dùng bản demo”**.

### Kết nối Frigate

| Trường hợp | Ghi chú |
|---|---|
| Port **8971** (khuyến nghị) | Cổng có xác thực + TLS; bật **“Tin chứng chỉ tự ký”** nếu dùng cert tự ký |
| Port **5000** | Cổng nội bộ không xác thực, thường không mở ra LAN |
| Reverse proxy / Nabu Casa | Nhập URL proxy, app không bắt buộc kết nối trực tiếp |
| Auth disabled | App tự dùng phiên ẩn danh |

Tài khoản: **Frigate → Settings → Users** (mật khẩu tối thiểu 12 ký tự).

---

## 3. Build & phát hành

```bash
./gradlew assembleDebug        # APK debug (applicationId .debug)
./gradlew assembleRelease      # APK release
./gradlew bundleRelease        # AAB để nộp Google Play
./gradlew testReleaseUnitTest  # unit test hợp đồng JSON với Frigate
```

Yêu cầu: **JDK 17**, **Android SDK 36** + **Build-Tools 36.0.0** (`local.properties` → `sdk.dir`).

### CI — GitHub Actions

`.github/workflows/build-apk.yml` chạy trên `ubuntu-latest`:

- Trigger: push `main`, pull request, tag `v*`, hoặc chạy tay.
- Cài `platforms;android-36` / `build-tools;36.0.0` nếu runner thiếu.
- Build **APK + AAB** và chạy unit test.
- Upload artifact `…-apk` và `…-aab`; tag `v*` tạo **GitHub Release** kèm cả hai tệp.
- `versionName` = tên tag bỏ tiền tố `v`; `versionCode` = `GITHUB_RUN_NUMBER` (luôn tăng).

### Phát hành Google Play (trạng thái)

| Hạng mục | Trạng thái |
|---|---|
| targetSdk 36 / AAB / Play App Signing sẵn sàng | ✅ |
| 16 KB page size (WebRTC `p_align=0x4000`) | ✅ đã kiểm chứng |
| Privacy policy trong app **và** `docs/privacy.md` | ✅ (cần bật GitHub Pages để có URL HTTPS) |
| Demo mode cho reviewer | ✅ nút “Dùng bản demo” |
| Phí đăng ký Play Console | 25 USD **một lần**, dùng cho nhiều app |
| Tài khoản **cá nhân** tạo sau 13/11/2023 | cần **closed test 12 tester / 14 ngày liên tục** + xác minh thiết bị |
| Tài khoản **tổ chức** | không bắt buộc bước 12 tester, cần hồ sơ doanh nghiệp |

---

## 4. Quyền riêng tư

- Không thu thập, không gửi dữ liệu về máy chủ nhà phát triển; **không quảng cáo**, không SDK analytics.
- Dữ liệu lưu cục bộ: URL máy chủ, tài khoản, (tuỳ chọn) mật khẩu, token, tuỳ chọn UI, crash log.
- Ảnh/clip tải về lưu ở `Downloads/FriCam` (MediaStore).
- Chi tiết: [`docs/privacy.md`](docs/privacy.md) và màn **Cài đặt → Ứng dụng → Chính sách quyền riêng tư**.

---

## 5. Cấu trúc mã

```
app/src/main/java/com/minhquanghp86/frigatenvr/
├── FrigateApp.kt              # Application + container
├── MainActivity.kt            # edge-to-edge, deep link, PiP
├── data/
│   ├── Models.kt              # Event, ReviewItem, Config (đọc lỏng 0.18), Faces, Exports…
│   ├── Session.kt             # ServerSession, trạng thái phiên
│   ├── Network.kt             # OkHttp (JWT, TLS tự ký), Retrofit FrigateApi
│   ├── FrigateRepository.kt   # login/refresh, DataStore, URL media, export, faces, users
│   ├── MediaSaver.kt          # tải về Downloads + notification tiến trình
│   └── MjpegSource.kt         # giải mã MJPEG → Flow<Bitmap>
├── notify/
│   ├── EventWatchService.kt   # foreground service + thông báo có ảnh
│   ├── DeepLinks.kt           # điều hướng từ thông báo
│   └── CrashLog.kt            # ghi crash kèm phiên bản/thiết bị
└── ui/
    ├── Theme.kt               # palette sáng/tối (IOS*) + ThemeState
    ├── Ios.kt                 # IosGroup, IosRow, IosSwitchRow, LargeTitleHeader…
    ├── Common.kt              # FrigateImage (có JWT), VideoPlayer
    ├── AppRoot.kt             # NavHost + tab bar
    ├── ViewModels.kt          # Camera / Events / Timeline / Settings
    └── screens/               # 18 màn: Login, Cameras, Events, Timeline, Faces, Users, System…
```

---

## 6. Khả năng tương thích API

Đã kiểm chứng với **Frigate 0.18.2** và xử lý các khác biệt schema:

- `live.streams` là object (không phải mảng)
- `/api/version` trả **plain text**, không phải JSON
- `reviewed` là **int 0/1**; body `/reviews/viewed` bắt buộc `{ids, reviewed}`
- `start_time` dạng **ISO-8601** hoặc epoch tuỳ endpoint
- `detectors.cpu/mem`, `cpu_usages.*` là **chuỗi số**; CPU/RAM hệ thống nằm ở
  `cpu_usages["frigate.full_system"]`
- Tên feature camera: `recordings` (không phải `record`), `snapshots`, `motion`, `audio`, …
- `/api/faces` trả `{}` khi chưa có ai; các thao tác face trả **400** nếu `face_recognition` tắt

Chi tiết endpoint đã dùng: [`docs/API-FRIGATE.md`](docs/API-FRIGATE.md) ·
Bàn giao & lịch sử thay đổi: [`docs/BAN-GIAO.md`](docs/BAN-GIAO.md) ·
Phân tích API & lộ trình Play: [`docs/PHAN-TICH-2026-10-29.md`](docs/PHAN-TICH-2026-10-29.md)

---

## 7. Xử lý sự cố

| Hiện tượng | Cách xử lý |
|---|---|
| Không đăng nhập được | Kiểm tra port 8971, bật “Tin chứng chỉ tự ký”, tài khoản ≥ 12 ký tự |
| Live tải chậm / đen | Ưu tiên WebRTC; kiểm tra go2rtc URL trong Cài đặt nếu dùng MP4/HLS |
| “Không phát được video” ở Xem lại | Chọn khung giờ có dải ghi hình; app chặn tua vượt thời điểm hiện tại |
| Không có thông báo | Bật chip **Thông báo**, cấp quyền POST_NOTIFICATIONS, kiểm tra bộ lọc camera/nhãn |
| Khuôn mặt trống | Bật `face_recognition.enabled` trong Frigate rồi khởi động lại |
| Cấu hình Camera báo lỗi | Cần tài khoản **admin** (API trả 400/403) |
| Cast không thấy TV | TV/Chromecast cùng WiFi; go2rtc port 1984 mở, không đặt auth; HLS phải được hỗ trợ |
| Muốn xem log | Cài đặt → Cấu hình Frigate → **Nhật ký** → nguồn `app` (có Copy/Share) |

---

## 8. Lộ trình

- [x] WebRTC-first live, timeline 24h + ảnh xem trước, export job
- [x] Khuôn mặt (thư viện, train, sự kiện), LPR, cảnh báo/review
- [x] Quản trị: camera features, profile, người dùng, notices, exports
- [x] Giao diện sáng/tối, thông báo có ảnh, Play-ready (SDK 36, AAB)
- [ ] i18n tiếng Anh toàn bộ UI (tách chuỗi sang `strings.xml`)
- [ ] Widget / Quick Settings tile, chia sẻ GIF sự kiện
- [ ] Timeline từ `/api/timeline`, tìm chuyển động (`/search/motion`)
- [ ] Phát hành Google Play (closed test 12 tester → production)

---

## 9. Tham khảo

- [Frigate HTTP API](https://docs.frigate.video/integrations/api/frigate-http-api/)
- [Frigate Authentication](https://docs.frigate.video/configuration/authentication/)
- [Face recognition](https://docs.frigate.video/configuration/face_recognition/)
- OpenAPI gốc: [docs/static/frigate-api.yaml](https://github.com/blakeblackshear/frigate/blob/dev/docs/static/frigate-api.yaml)

## 10. Giấy phép & miễn trừ

Mã nguồn phát hành công khai cho mục đích cá nhân/học tập. FriCam **không chính thức**, không liên kết
với dự án Frigate; tên “Frigate” thuộc về tác giả của nó. Ứng dụng không kèm máy chủ hay dữ liệu camera.
