# Bàn giao — Frigate NVR Android

Ngày tạo: **28/10/2026**. Tài liệu này để phiên làm việc sau đọc là hiểu ngay trạng thái dự án.

## 1. Tóm tắt

App Android (Kotlin + Jetpack Compose) điều khiển/xem **Frigate NVR** qua HTTP API chính thức. Repo: [minhquanghp86/frigate-nvr-android](https://github.com/minhquanghp86/frigate-nvr-android) (public → CI dùng runner GitHub miễn phí).

- APK tự build bởi GitHub Actions: xem tab **Actions** → run mới nhất → **Artifacts**, hoặc **Releases** nếu push tag `v*`.
- Ứng dụng không lưu/bake sẵn địa chỉ server hay tài khoản — người dùng nhập ở màn đăng nhập, lưu trong DataStore của app.

## 2. Đã hoàn thành (GĐ1 + GĐ2)

| Màn hình | Chức năng | Endpoint |
|---|---|---|
| Đăng nhập | JWT từ `Set-Cookie: frigate_token`, tự login lại khi 401, chế độ ẩn danh khi `auth.enabled=false`, tuỳ chọn tin TLS self-signed | `POST /api/login`, `GET /api/profile` |
| Camera | Lưới `latest.jpg` refresh 5s; live **MJPEG** chọn fps/height, đo fps thực, fallback ảnh tĩnh 2s | `GET /api/config`, `GET /api/{cam}`, `GET /api/{cam}/latest.jpg` |
| Sự kiện | Lưới thumbnail, lọc camera/nhãn/chỉ-có-clip, phân trang bằng `before`, chi tiết snapshot+clip | `GET /api/events`, `/events/{id}`, `thumbnail.jpg`, `snapshot.jpg?bbox=1`, `clip.mp4` |
| Cần xem | Danh sách review chưa/đã xem, phát clip, đánh dấu đã xem | `GET /api/review`, `POST /api/reviews/viewed`, `GET /api/review/{id}/clip.mp4` |
| Xem lại | Ngày có ghi hình → khung giờ → phát HLS | `GET /api/recordings/summary`, `/api/{cam}/recordings/summary`, HLS `/api/vod/{yyyy-mm}/{dd}/{hh}/{cam}` |
| Cài đặt | Sửa server/tài khoản/mật khẩu/tin cert, phiên bản + sức khỏe, đăng xuất có xác nhận | `GET /api/version`, `GET /api/stats`, `GET /api/logout` |

Chi tiết API: [API-FRIGATE.md](API-FRIGATE.md).

## 3. Đã kiểm chứng

- **Build local** (JDK 17, Android SDK 34): `./gradlew assembleDebug assembleRelease` → xanh; `app-debug.apk` ≈ 21 MB, `app-release.apk` ≈ 15 MB (ký debug key).
- **Frigate thật của người dùng**: `https://<ip-lan>:8971` — TLS self-signed, auth bật. `POST /api/login` trả 401 JSON khi sai mật khẩu (đúng hành vi), `/api/version` 401 khi không có token (nginx `auth_request`), `/api/login` được miễn auth. Port 5000 không mở ra LAN → app phải dùng 8971 + bật "tin chứng chỉ tự ký" (mặc định bật).
- **CI**: chạy trên `ubuntu-latest` của GitHub, artifact `frigate-nvr-android-<version>` (≈ 14 MB) — run đầu tiên xanh ngày 28/10/2026.

### Ghi chú sự cố CI đã gặp (đừng lặp lại)

1. Repo **private** trên gói free → job không được cấp runner (fail sau ~3 s, 0 step) vì hết phút Actions. Đã chuyển repo sang **public** → dùng runner GitHub miễn phí.
2. `android-actions/setup-android@v3` **đang hỏng**: nó gọi `sdkmanager tools`, package `tools` không còn trong SDK repo → `Failed to find package 'tools'`. Đã bỏ step này, chỉ ghi `sdk.dir` từ `ANDROID_SDK_ROOT` (runner ubuntu-latest có sẵn SDK + licenses, AGP tự tải package thiếu).

## 3b. Cập nhật 28/10/2026 — sửa lỗi tương thích Frigate 0.18

Người dùng cài bản v0.1.0 và 4 tab đỏ. Nguyên nhân (đối chiếu source `v0.18.2`):

| Lỗi hiển thị | Nguyên nhân | Sửa |
|---|---|---|
| `Unexpected JSON token ... $.cameras['x'].live.streams` | 0.18 đổi `live.streams` từ **mảng** sang **object** | `/api/config` đọc lỏng: `FrigateCameraConfig` giữ `record/audio/detect/live/ffmpeg` là `JsonObject`, UI dùng `recording`/`hasAudio` |
| `Unexpected JSON token at offset 0 ... had '0'` (tab Cài đặt) | `GET /api/version` là `PlainTextResponse` (`0.18.2-cfa08c5`) | `version()` trả `ResponseBody`; `parseVersion()` nhận cả chuỗi và JSON |
| `Server trả lỗi HTTP 422` (tab Cần xem) | 0.18 đổi `reviewed` thành **int 0/1** và body `/reviews/viewed` bắt buộc `{ids, reviewed}` | gửi `reviewed=0/1`, `IdsBody` thêm `reviewed: Boolean = true` |
| (ẩn) mốc thời gian review hiện "—" | `ReviewSegmentResponse.start_time` là **datetime ISO**, không phải epoch | `asEpoch()` parse ISO có/không offset |

Đã thêm `ModelsTest` (5 test) khoá các shape này và CI chạy `testReleaseUnitTest` cùng lúc build.



## 3c. Cập nhật 28/10/2026 (lần 2) — live nhiều luồng + sửa HLS

- **Xem lại lỗi `ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED`**: app gọi `/api/vod/...` (endpoint này trả JSON mapping cho nginx-vod, không phải playlist). Chuyển sang `/vod/{yyyy-mm}/{dd}/{hh}/{cam}/{tz}/master.m3u8`.
- **Live có 3 luồng** (dropdown): MJPEG (Frigate, fps + độ phân giải chọn được) · HLS(go2rtc) · MP4(go2rtc).
  MJPEG lấy từ luồng *detect* nên thường chỉ ~5 fps; HLS/MP4 đi thẳng go2rtc (port 1984) nên giữ fps gốc của camera.
- Thêm ô **go2rtc URL** trong Cài đặt (mặc định tự suy ra `http://<ip-frigate>:1984`).
- fps chọn được: 1/2/3/5/8/10/15; độ phân giải: 240/360/480/720/1080.

## 3d. Cập nhật 28/10/2026 (lần 3) — WebRTC qua Frigate

- Thêm chế độ live **WebRTC** (mặc định): `WebRtcLiveView` gửi SDP offer tới
  `POST /api/go2rtc/webrtc?src=<stream>` (Bearer JWT, Content-Type: application/sdp),
  chờ ICE gathering (non-trickle, tối đa 3s) rồi nhận SDP answer.
  Không cần mở port 1984, giữ fps gốc, độ trễ thấp.
- Thư viện: `io.github.webrtc-sdk:android:137.7151.05`; `abiFilters` chỉ arm64-v8a + armeabi-v7a
  → APK ~32 MB.
- Vẫn giữ MP4/HLS (go2rtc 1984) và MJPEG (Frigate) để so sánh/fallback.
- Lưu ý bảo mật: go2rtc 1984 đang mở, `/api/streams` lộ URL RTSP kèm user/mật khẩu camera.

## 3e. Cập nhật 28/10/2026 (lần 4) — ký cố định + sửa khung hình live

- **Không cập nhật được APK (xung đột gói)**: release trước ký bằng *debug key*, mà runner CI
  tạo debug keystore mới mỗi lần → chữ ký khác nhau. Thêm `keystore/frigate-release.jks`
  (alias `frigate`) và `signingConfigs.release`; bản v0.1.5 phải gỡ cài lại 1 lần, sau đó cập nhật đè được.
- **Live bị phóng/cắt viền**: `SurfaceViewRenderer` để mặc định FILL; đổi sang
  `SCALE_ASPECT_FIT` + tắt hardware scaler. ExoPlayer (MP4/HLS/clip) đặt `RESIZE_MODE_FIT`.
- Chặn port go2rtc 1984: thêm vào `go2rtc:` của Frigate config
  `api: {listen: "127.0.0.1:1984"}` (nginx vẫn nối 127.0.0.1, LAN bị chặn) —
  khi đó MP4/HLS trong app ngừng dùng được, WebRTC/MJPEG vẫn chạy.

## 3f. Cập nhật 28/10/2026 (lần 5) — ép tỉ lệ khung live + hiện version app

- WebRTC: đọc `VideoFrame.rotatedWidth/Height` từ frame đầu để biết tỉ lệ thật, ép `Modifier.aspectRatio`
  cho SurfaceViewRenderer (không còn phụ thuộc chế độ scale của renderer) + vẫn để `SCALE_ASPECT_FIT`.
- Cài đặt hiện thêm `app v<versionName>` (BuildConfig.VERSION_NAME) để xác nhận bản đang cài.

## 3g. Cập nhật 28/10/2026 (lần 6) — giao diện player kiểu Frigate

- Thanh công cụ nổi: quay lại, kết nối lại, fit/crop, tắt/bật tiếng (WebRTC), toàn màn hình, cài đặt.
- Sheet cài đặt kiểu Frigate: chọn Luồng, FPS + Chất lượng (MJPEG), công tắc Hiện thống kê / Tắt tiếng /
  Vừa khung, nút Kết nối lại.
- WebRTC: thêm audio (transceiver RECV_ONLY + `setAudioPlayout`), `getStats` 1s/lần để hiện
  fps · độ phân giải · kbps; chip thống kê góc dưới-trái, không đè nút.
- VideoPlayer (MP4/HLS): thêm tham số `fit` (RESIZE_MODE_FIT/ZOOM), cập nhật qua `AndroidView.update`.

## 3h. Cập nhật 28/10/2026 (lần 7) — công tắc camera (admin)

- Thêm `PUT /api/camera/{cam}/set/{feature}` body `{"value":"ON"|"OFF"}` cho 4 tính năng:
  `enabled` (Camera), `detect`, `recordings`, `snapshots`. UI: sheet cài đặt của màn live.
- API này yêu cầu role **admin**. Tài khoản `Minhquang` trong Frigate đã là admin;
  nếu app hiển thị viewer thì đăng xuất/đăng nhập lại (role nằm trong JWT lúc login).
- Mật khẩu admin của Frigate có thể reset bằng `auth: reset_admin_password: true` + restart
  (in ra trong log add-on), sau đó nhớ đặt lại `false`.

## 3i. Cập nhật 28/10/2026 (lần 8) — iOS UI, live nhanh, thông báo, hành động sự kiện

**Live mở nhanh hơn**
- Hiện ảnh `latest.jpg` (poster) ngay khi vào camera, video WebRTC phủ lên khi có frame đầu.
- Chờ ICE gathering giảm 3000ms → **700ms**; endpoint WebRTC dùng luôn tên camera (không chờ /api/config).
- `configCached()` (TTL 60s) cho màn live/camera/events; `refreshProfile()` đồng bộ role khi mở app.

**Giao diện iOS** (`ui/Theme.kt`, `ui/Ios.kt`): palette iOS dark, typography (large title 34), shape 14-18dp,
`IosGroup/IosRow/IosSwitchRow/SectionTitle/Pill/SegmentedControl`; tab bar trong mờ; Cài đặt dạng inset-grouped.

**Chức năng mới**
- Thông báo sự kiện: `notify/EventWatchService` (foreground service, poll `/api/events?after=…` 10s/lần,
  dedupe theo id, POST_NOTIFICATIONS, kênh HIGH; tự chạy lại khi app mở nếu đang bật).
  Không dùng Web Push/VAPID của Frigate vì cần FCM/Google services, không hợp môi trường LAN.
- Sự kiện: giữ/bỏ giữ (retain), xoá (có xác nhận), tải clip về `Downloads/Frigate` (OkHttp + MediaStore,
  giữ được JWT + TLS self-signed — DownloadManager hệ thống sẽ fail với cert tự ký).
- Nhật ký: `/api/logs/{frigate|go2rtc}` (tự cập nhật 3s).
- Lưu trữ: `/api/recordings/storage` (admin) — thanh % theo camera.
- Bộ lọc label lấy động từ `/api/labels`.

## 4. Việc còn lại

1. **Test thật trên điện thoại**: cài APK, đăng nhập, kiểm tra 3 luồng nặng nhất — MJPEG live, phát clip sự kiện, HLS "Xem lại" (HLS cần server bật record).
2. **GĐ3** (chưa làm): thông báo Web Push/VAPID, điều khiển PTZ `/api/camera/{name}/set/{feature}`, tìm kiếm nâng cao (`/api/events/search`, motion search), export (`/api/exports`), nhiều server, live WebRTC qua go2rtc.
3. Nếu muốn build không tốn phút Actions khi repo chuyển private: thêm self-hosted runner (`runs-on: [self-hosted, linux, x64]`) — nay repo public nên không cần.

## 5. Ghi chú kỹ thuật quan trọng

- Frigate trả JWT **chỉ trong `Set-Cookie`** (body rỗng) → `FrigateRepository.signIn()` đọc header, không đọc body.
- nginx của Frigate dùng `auth_request` và **forward `Authorization`** → dùng `Bearer` cho mọi request là đủ, kể cả `/vod/` (HLS) và ảnh; app vẫn dùng chung `OkHttpClient` cho Coil và ExoPlayer.
- `ReviewSegment.start_time` có thể là số epoch **hoặc** chuỗi ISO tuỳ version → model dùng `JsonElement` + hàm chuyển đổi.
- `/api/stats` đổi schema theo version → đọc bằng `JsonObject` và `doubleOrNull`, không hardcode.
- Ảnh Coil cần `ImageLoader` có `OkHttpClient` của app (interceptor gắn Bearer + TLS tự ký).
- `usesCleartextTraffic="true"` để dùng được `http://` trong LAN.

## 6. Cách làm việc tiếp

```bash
cd /root/work/frigate-android
./gradlew assembleDebug          # build thử
git push origin main             # CI tự build APK
git tag v0.2.0 && git push --tags  # tạo Release kèm APK
```

Repo local dùng remote SSH `github-frigate` (deploy key riêng, quyền ghi) — xem `~/.ssh/config`.
