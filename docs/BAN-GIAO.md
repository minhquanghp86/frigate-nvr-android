# Bàn giao — FriCam (Frigate NVR Android)

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

## 3j. Cập nhật 28/10/2026 (lần 9) — deep link thông báo + tiện ích

- **Thông báo mở đúng sự kiện**: notification mang `EXTRA_EVENT_ID`, MainActivity (`singleTop`)
  đẩy vào `notify/DeepLinks`, `MainScaffold` điều hướng `event/{id}` (kể cả khi app cold-start
  hoặc đang ở màn đăng nhập -> điều hướng sau khi login).
- **Camera**: thẻ camera hiện sự kiện mới nhất (label + thời gian tương đối) lấy từ `/api/events?cameras=X&limit=1`.
- **Sự kiện**: thêm nút **Lưu ảnh** (snapshot) cạnh Lưu clip/Xoá/Giữ.
- **Xem lại**: nút **Tải** tải trọn 1 khung giờ ghi hình (`/api/{cam}/start/{s}/end/{e}/clip.mp4`,
  mốc giờ tính theo timezone máy) về `Downloads/Frigate`.

## 3k. Cập nhật 28/10/2026 (lần 10) — tìm hiểu app tham khảo + live "tức thì"

**App tham khảo (sp-engineering/frigate-viewer) phát live thế nào?**
- KHÔNG phát video cho live. `views/camera-preview/LivePreview.tsx` chỉ là `<Image>` trỏ tới
  `/api/{camera}/latest.jpg?bbox=1&ts=…` và refresh theo `refreshFrequency` (mặc định **10 giây**).
- Thư viện `@lunarr/vlc-player` (libVLC) chỉ dùng ở `views/camera-event-clip` để phát **clip**.
- Vì vậy nó "hiện ngay" vì đó là một ảnh JPEG (~12ms), không phải luồng video.

**Cách của app ta (tốt hơn cả hai)**
- Thêm chế độ **AUTO (mặc định)**: MJPEG của Frigate hiện ngay (~0.2s, ảnh động ~5fps theo luồng detect),
  đồng thời WebRTC nối song song; **khi WebRTC có frame đầu** thì phủ lên toàn màn (không có khoảng đen,
  nhờ mount WebRTC ở 1dp trong lúc chờ). Poster `latest.jpg` vẫn hiện trước frame MJPEG đầu tiên.
- Warm producer go2rtc khi chạm camera (giữ consumer ~6s) → WebRTC vào nhanh hơn.
- Bỏ chữ "Đang trao đổi SDP…", chỉ còn chip nhỏ ở đáy.
- Lý do MP4 chậm: `api/stream.mp4` TTFB ~4.2s (chờ keyframe), sau warm còn ~2.5s.

**Khác**
- Gộp tab: "Sự kiện" + "Cần xem" → một tab **Sự kiện** với segmented control (còn 4 tab).
- Tìm kiếm sự kiện bằng AI: `GET /api/events/search?query=…` (ô tìm kiếm trong tab Sự kiện).
- Lọc thông báo theo camera (Cài đặt → Thông báo → Camera nhận thông báo).

## 3l. Cập nhật 28/10/2026 (lần 11) — toàn màn hình, PiP, khung nhận diện

- **Toàn màn hình thật**: nút vuông giờ ẩn system bars (`WindowInsetsControllerCompat`) + thanh công cụ;
  bấm lại / chạm video để thoát.
- **Picture-in-Picture**: nút cạnh thay cho nút fit (fit vẫn còn trong sheet cài đặt).
  Manifest `supportsPictureInPicture`, `configChanges` thêm screenLayout/smallestScreenSize;
  `PipState` (StateFlow) để UI ẩn thanh công cụ khi ở cửa sổ nhỏ.
- **Khung nhận diện**: Frigate vẽ box phía server trong **MJPEG** (`/api/{camera}?bbox=1`) và snapshot —
  WebRTC/MP4/HLS là luồng gốc nên KHÔNG có box. Công tắc "Khung nhận diện" trong sheet (mặc định bật);
  ở chế độ Tự động, bật box sẽ giữ MJPEG thay vì nâng lên WebRTC.

## 3m. Cập nhật 28/10/2026 (lần 12) — live theo ý người dùng

- **Bỏ khung nhận diện** trên live (ưu tiên fps): xoá công tắc box; MJPEG chỉ còn là lớp ảnh
  tức thời lúc mở (AUTO), sau đó WebRTC phủ lên.
- **Toàn màn hình đúng nghĩa**: xoay ngang (`SCREEN_ORIENTATION_SENSOR_LANDSCAPE`), ẩn system bars,
  video phủ kín; thoát bằng nút/chạm/Back, khôi phục orientation khi rời màn.
- **Live mỗi camera**: bỏ nút refresh; thêm hàng điều khiển **Sự kiện** (mở panel danh sách sự kiện
  ngay dưới video, chạm để mở chi tiết), **Xem lại** (mở timeline đúng camera qua `timeline?camera=`),
  và **switch thông báo** cho riêng camera đó (lưu trong `notifyCameras`; tập rỗng = tất cả).
- **Bấm thông báo → mở live camera** (không mở chi tiết sự kiện) — `EXTRA_CAMERA` + `DeepLinks.openCamera`.
- **Tab Sự kiện**: bỏ mục "Cần xem" (xoá `ReviewScreen`), chỉ còn ô tìm kiếm + bộ lọc + lưới sự kiện.
- **Sửa nút Xoá bị cắt**: hàng nút hành động đổi sang `FlowRow` (tự xuống dòng).
- **Danh sách tự làm mới sau khi xoá**: `FrigateRepository.dataVersion` tăng khi xoá sự kiện;
  `EventsViewModel` và panel sự kiện trong live theo dõi để refresh (hết cảnh 404 khi bấm ảnh cũ).

## 3n. Cập nhật 28/10/2026 (lần 13) — xem lại kiểu mới, fullscreen, tải có tiến trình

- **Xem lại**: bấm "Xem lại" trong live mở **route riêng `timeline/{camera}`** (có nút back, ẩn tab bar)
  thay vì nhảy sang tab.
- **Thiết kế lại trình xem lại**: chọn **ngày bằng dropdown**; **slider 0–1439 phút** chọn thời điểm
  (thả tay -> phát đúng giờ, tua tới đúng phút); nút Phát / Tải khung giờ; hiện số khung giờ có ghi.
- **Sửa lỗi đổi khung giờ bị dừng**: `VideoPlayer` gán player trong `AndroidView.update`
  (trước chỉ gán 1 lần ở factory nên đổi URL vẫn trỏ player đã release). Thêm `startPositionMs` để seek.
- **Tải media có tiến trình**: `MediaSaver` hiện notification "Đang tải…" (%), rồi "Đã lưu" (kèm đường dẫn)
  hoặc "Không tải được"; kênh `frigate_downloads`.
- **Fullscreen**: giữ đủ thanh công cụ ở chế độ ngang; chạm màn hình chỉ ẩn/hiện nút, **không thoát** fullscreen;
  thoát bằng nút Toàn màn hình (quay về dọc).
- **Trả lại 4 switch tính năng camera** trong sheet bánh răng của live (Camera/Phát hiện/Ghi hình/Ảnh chụp).
- **Nút hành động sự kiện**: 1 hàng 4 nút gọn (Giữ · Clip · Ảnh · Xoá) thay vì xuống dòng.

## 3o. Cập nhật 28/10/2026 (lần 14) — toàn màn hình cho mọi trình phát

- Thêm `PlayerStore` + route `player`: mọi `VideoPlayer` (clip sự kiện, khung giờ xem lại) có nút
  toàn màn hình ở góc phải; bấm sẽ mở màn hình player riêng, **xoay ngang**, ẩn system bars,
  thoát bằng nút hoặc Back (khôi phục dọc).
- `VideoPlayer` nhận thêm `onFullscreen` (vẽ nút khi có) và `startPositionMs` (giữ đúng vị trí đang xem).
- Live vẫn dùng cơ chế riêng (giữ thanh công cụ khi ngang).

## 3p. Cập nhật 28/10/2026 (lần 15) — điều khiển tự viết, timeline kiểu Frigate

- **Bỏ nút fullscreen nổi**: `VideoPlayer` dùng thanh điều khiển tự viết (play/pause, seekbar, thời gian,
  fullscreen) hiện/ẩn cùng nhau, tự ẩn sau 4s khi đang phát; chạm video để hiện lại. Thay cho controller
  mặc định của Media3 (nguyên nhân bị cắt hai đầu trên máy hẹp).
- **Responsive**: hàng nút sự kiện dùng `BoxWithConstraints` + `FlowRow` (máy < 340dp → 2 nút/hàng,
  rộng hơn → 1 hàng 4 nút); hàng bộ lọc và hàng điều khiển live cuộn ngang nên không bị chèn.
- **Timeline kiểu Frigate**: thay slider bằng dải thời gian **cuộn ngang 24h** vẽ bằng Canvas:
  vạch giờ, vùng có ghi hình (xanh), **vạch sự kiện tô màu theo nhãn** (người xanh dương, xe cam, khác đỏ),
  playhead nét đứt + bong bóng giờ:phút. Chạm vào dải để chọn thời điểm và phát; nút Phát/Tải giữ nguyên.
  Dữ liệu sự kiện trong ngày lấy từ `/api/events?after=…&before=…` (TimelineViewModel.loadDayEvents).

## 3q. Cập nhật 28/10/2026 (lần 16) — kéo playhead, icon tab, tiến trình tải

- **Timeline 2 thao tác**: chạm thanh thời gian (playhead, có tay nắm tròn) → kéo riêng, dải đứng im
  (sự kiện bị consume); chạm/kéo **ngoài** tay nắm → dải cuộn như cũ, tap (không di chuyển) → chọn giờ.
  Dùng `awaitEachGesture` + `awaitFirstDown` + kiểm tra `isConsumed`/`touchSlop`.
- **Icon tab**: "Xem lại" dùng icon `History` (trước bị nhầm thành bánh răng của Cài đặt).
- **Tiến trình tải**: thêm `onProgress` cho MediaSaver; app hiện "Đang tải… x%" ngay trong màn hình,
  xin quyền POST_NOTIFICATIONS khi bấm Tải; kênh `frigate_downloads` nâng lên IMPORTANCE_DEFAULT,
  thêm CATEGORY_PROGRESS, cập nhật mỗi 250ms; gọi `Notify.ensureChannels` trước khi đăng ký.

## 3r. Cập nhật 28/10/2026 (lần 17) — đổi tên FriCam + logo mới

- Đổi tên hiển thị: **FriCam** (bỏ chữ "NVR" gây hiểu nhầm là đầu ghi/server); applicationId giữ nguyên
  `com.minhquanghp86.frigatenvr` để app vẫn cập nhật đè được (đổi id = app mới).
- Logo mới: chim biển cách điệu (silhouette, cánh thuôn + đuôi chẻ) trên nền xanh đậm gradient,
  có `monochrome` cho themed icon Android 13. Xem trước: `docs/icon.png`.
- Ghi chú: repo vẫn tên `frigate-nvr-android` (GitHub sẽ redirect nếu đổi tên sau này).

## 3s. Cập nhật 28/10/2026 (lần 18) — log chọn/copy được

- Khung log bọc `SelectionContainer` nên nhấn giữ chọn/copy từng đoạn như văn bản thường.
- Thêm nút **Copy** (clipboard), **Chia sẻ** (ACTION_SEND), và **Xoá log** (chỉ ở nguồn "Ứng dụng").
- Xem log "Ứng dụng" tự tắt chế độ tự cập nhật để không phá vùng bôi đen.

## 3t. Cập nhật 29/10/2026 — sửa crash Canvas Timeline (từ crash log)

Log người dùng gửi: `IllegalArgumentException: maxHeight(-4) must be >= than minHeight(0)` tại
`TimelineScreen.kt:383` (drawText). Nguyên nhân: khi xoay màn hình/measure lại, Canvas bị đo cao 0
nhưng vẫn vẽ nhãn giờ ở y=4 -> `maxHeight = 0 - 4 = -4`. Đã sửa:
- `if (w <= 0f || h <= 4f) return@Canvas`
- chỉ vẽ nhãn giờ khi `h > 20f`; bong bóng thời gian khi `h > bh + 12f`
- thêm `heightIn(min = 40.dp)` cho Canvas
(Đây chính là crash khi bấm Toàn màn hình ở trình xem lại — xoay ngang làm Canvas measure lại.)

## 3u. Cập nhật 29/10/2026 — bấm thông báo mở live camera + panel sự kiện

- Notification mang `EXTRA_CAMERA` + `EXTRA_OPEN_EVENTS`; `MainActivity` gọi
  `DeepLinks.openCamera(camera, openEvents = true)`.
- Route `camera/{name}?panel=events` -> `CameraLiveScreen(initialPanel = 1)`: mở **live của camera
  đang báo** kèm **panel sự kiện của camera đó** bên dưới (không mở trình xem sự kiện).
- Deep link `event/{id}` vẫn giữ cho các luồng khác (ví dụ từ panel sự kiện).

## 3v. Cập nhật 29/10/2026 — hoàn tất các việc còn treo

- **Màn Tổng quan** (`overview`, mở từ Cài đặt → Hệ thống): phiên bản, uptime/CPU/RAM, detection fps,
  cảnh báo (`/api/notices`), fps từng camera, dung lượng theo camera, và **danh sách clip đã xuất**
  (`/api/exports`) có nút tải; lối tắt tới Nhật ký/Dung lượng.
- **Export có theo dõi job**: nút Tải ở Xem lại tạo `POST /api/export/{cam}/start/{s}/end/{e}`,
  poll `GET /api/jobs/export/{id}` (dùng `current_step` + `progress_percent`) rồi tải file qua
  `/exports/<tên>`; nếu job lỗi/không có thì **fallback** cắt trực tiếp `clip.mp4`.
- **Lọc sự kiện nâng cao**: thêm pill **thời gian** (Mọi lúc/1 giờ/24 giờ/7 ngày → `after`)
  và **vùng** (`zones` lấy từ config camera → `zones=`).
- **Thông báo theo nhãn**: Cài đặt → Thông báo → "Nhãn nhận thông báo" (DataStore + service lọc `e.label`).
- **Sự kiện 404**: nếu sự kiện đã bị xoá, màn chi tiết hiện nút "Mở live camera <cam>"
  (deep link thông báo giờ mang kèm camera).

## 3w. Cập nhật 29/10/2026 — màn Hệ thống kiểu Frigate (biểu đồ + dung lượng)

- Poll `/api/stats` mỗi 5s, giữ 160 mẫu cho bộ phát hiện đầu tiên:
  `detectors.<name>.inference_speed` (ms), `.cpu` (%), `.mem` (%) → **3 biểu đồ cột**
  (cột xanh, cam khi gần đỉnh), kèm giá trị hiện tại — giống trang System của Frigate.
- **Dung lượng**: `service.storage` (Bản ghi, /tmp/cache, /dev/shm) với used/total + % + thanh tiến trình;
  thêm "Bản ghi sớm nhất" lấy từ key nhỏ nhất của `/api/recordings/summary`.
- **Bảng Lưu trữ Camera**: từ `/api/recordings/storage` — camera, dung lượng, %, **băng thông MiB/giờ**
  (field `bandwidth`).
- Giữ nguyên các mục cũ: hệ thống/uptime/CPU/RAM, camera fps, cảnh báo, clip đã xuất (tải được).

## 3x. Cập nhật 29/10/2026 — tự phát Xem lại + tìm tương tự + LPR/Faces

- **Tab Xem lại tự phát khi mở**: vào tab (hoặc đổi camera/ngày) là phát ngay **00:00** của camera;
  nếu khung 00h không có bản ghi thì phát **khung sớm nhất có dữ liệu** (tránh màn hình lỗi 404).
  Chỉ tự phát một lần cho mỗi cặp (ngày, camera) — không giành quyền điều khiển khi người dùng đã chọn giờ.
- **Tìm sự kiện tương tự**: nút trong trang sự kiện gọi `/api/events/search?event_id=...&search_type=thumbnail`,
  hiện dải thumbnail bên dưới; bấm để mở sự kiện đó.
- **Biển số xe (LPR)**: Cài đặt → Biển số đã nhận diện → `/api/recognized_license_plates?split_joined=1`;
  bấm một biển số sẽ nhảy sang tab Sự kiện với truy vấn đã điền (`SearchStore`).
- **Khuôn mặt**: Cài đặt → Khuôn mặt đã đăng ký → `/api/faces` + ảnh `/clips/faces/<tên>/<file>`
  (dùng OkHttp có Bearer nên ảnh qua được auth).

## 3y. Cập nhật 29/10/2026 — thiết kế lại tab Camera (trang chủ)

- **Header**: tiêu đề lớn "Camera" + dòng trạng thái "N camera · M đang ghi" (chấm xanh) + nút tải lại.
- **Carousel camera nổi bật**: thẻ 16:9 rộng 84% màn hình (lộ mép thẻ kế tiếp để vuốt), ảnh `latest.jpg`
  làm nền + gradient tối, nhãn **LIVE** (đỏ), **REC**, chip **fps** (từ `/api/stats`), tên camera
  và sự kiện gần nhất ("person · 5 phút trước"), nút chevron tròn. Chạm để mở live (kèm warmStream).
- **3 thẻ số liệu nhanh**: sự kiện **hôm nay** (tổng `/api/events/summary` hôm nay), **đang ghi** x/y camera,
  **lưu trữ %** (used/total lớn nhất trong `service.storage`).
- **Danh sách "Tất cả camera"** (inset-grouped iOS): thumbnail, tên, chấm REC, fps, số sự kiện hôm nay,
  icon audio nếu có, sự kiện gần nhất, chevron.
- Dữ liệu tự làm mới mỗi 8 giây (stats + events summary + ảnh), tải lại thủ công bằng nút ở header.

## 3z. Cập nhật 29/10/2026 — nút bật/tắt thông báo nhanh trên tab Camera

- Chip cạnh nút tải lại ở header tab Camera: **"Thông báo"** (xanh dương khi bật) / **"Đã tắt"** (xám).
- Chạm để bật: xin quyền POST_NOTIFICATIONS (Android 13+) rồi start `EventWatchService` + `setNotifyEnabled(true)`;
  tắt: `stopService` + `setNotifyEnabled(false)`. Dùng chung logic với Cài đặt nên trạng thái luôn đồng bộ.

## 3za. Cập nhật 29/10/2026 — tab Camera dạng lưới gọn

- Bỏ carousel vuốt ngang (thẻ quá to) và bỏ mục "Tất cả camera" ở dưới.
- Toàn bộ tab là **lưới thẻ 16:9** (`LazyVerticalGrid`, `Adaptive(158dp)`), header và 3 thẻ số liệu
  chiếm toàn chiều ngang (`GridItemSpan(maxLineSpan)`).
- Mỗi thẻ: ảnh latest + LIVE/REC + chip fps + tên + sự kiện gần nhất + số sự kiện hôm nay
  (+ icon audio nếu có) — đủ thông tin nên không cần danh sách riêng.

## 3zb. Cập nhật 29/10/2026 — header thương hiệu trên tab Camera

- Thêm hàng brand trên cùng tab Camera: icon app (bo góc 7dp) + **FriCam** (đậm) + "Frigate NVR" (mờ)
  và phiên bản `v<versionName>` bên phải; nằm trên tiêu đề lớn "Camera".

## 3zc. Cập nhật 29/10/2026 — thiết kế lại trang Cài đặt

Bố cục mới theo nhóm chức năng:
1. **Tài khoản**: người dùng hiện tại, vai trò, "Danh sách người dùng" (`/api/users`, chỉ admin thấy dữ liệu),
   Đăng xuất (có hộp thoại xác nhận).
2. **Kết nối** (thu gọn mặc định, chạm để mở): địa chỉ server, tài khoản, mật khẩu, go2rtc URL,
   ghi nhớ mật khẩu, tin chứng chỉ tự ký, nút "Lưu & kết nối". Header hiển thị host hiện tại.
3. **Cấu hình Frigate** (nhóm mới, đặt tên theo chức năng): Tổng quan hệ thống, **Cấu hình Camera**
   (màn mới: bật/tắt Phát hiện / Ghi hình / Chụp ảnh / Âm thanh cho từng camera qua
   `PUT /api/camera/{cam}/set/{feature}`, cập nhật lạc quan + hoàn tác khi lỗi), **Thư viện khuôn mặt**,
   Biển số đã nhận diện, Nhật ký, Dung lượng ghi hình, Phiên bản Frigate.
4. **Thông báo**: bật/tắt, lọc theo camera, lọc theo nhãn.
5. **Ứng dụng**: phiên bản FriCam (versionCode), mở giao diện web Frigate.

## 3zd. Hotfix 29/10/2026 — crash tab Camera sau ~2 giây

- Nguyên nhân: header brand dùng `painterResource(R.mipmap.ic_launcher)` — đây là **adaptive icon**
  (`mipmap-anydpi-v26/ic_launcher.xml`), Compose không vẽ được trực tiếp nên ném lỗi khi header
  được compose (header chỉ render sau khi `/api/config` xong, tức ~2 giây sau khi mở app).
- Sửa: thay bằng logo vẽ trực tiếp (Box 26dp, gradient xanh `IOSBlue → #5E5CE6`, icon Videocam trắng).
  Không còn phụ thuộc resource mipmap.

## 3ze. Cập nhật 29/10/2026 — quản lý người dùng & khuôn mặt ngay trong app

### Người dùng (cần admin)
- `GET /api/users` để liệt kê; **thêm** `POST /api/users` (username/password ≥ 12 ký tự/role),
  **đổi vai trò** `PUT /api/users/{u}/role`, **đặt mật khẩu** `PUT /api/users/{u}/password`,
  **xoá** `DELETE /api/users/{u}` (không xoá được `admin` mặc định).
- UI: nút + trên header, chạm một dòng để mở hộp thoại hành động; lỗi server hiển thị nguyên văn
  (đã bổ sung đọc `message`/`detail` trong JSON lỗi, thêm nhánh 400).

### Khuôn mặt (Face Recognition)
- **Tạo người**: `POST /api/faces/{name}/create`.
- **Đăng ký ảnh**: `POST /api/faces/{name}/register` (multipart field `file`, chọn ảnh từ thư viện).
- **Xoá người**: `POST /api/faces/{name}/delete` với toàn bộ `ids` trong thư mục.
- **Train từ sự kiện**: trong trang sự kiện → "Gán khuôn mặt cho sự kiện" →
  `POST /api/faces/train/{name}/classify` body `{event_id}` (Frigate tự cắt khuôn mặt từ ảnh sự kiện).
- `/api/faces` và `/api/recognized_license_plates` giờ **ném lỗi ra UI** thay vì trả rỗng im lặng,
  nên nếu Frigate báo "Face recognition is not enabled" bạn sẽ thấy đúng thông báo.
- LPR: Frigate 0.18 **không có API thư viện biển số** để thêm/sửa; biển số sinh ra từ sự kiện
  (`event.data.recognized_license_plate`), chỉ có `POST /api/lpr/reprocess` để xử lý lại. App giữ
  màn danh sách + bấm để tìm sự kiện; muốn sửa dữ liệu biển phải qua Frigate/DB.

## 3zf. Cập nhật 29/10/2026 — theo dõi sự kiện khuôn mặt

Dữ liệu: Frigate gắn `sub_label` cho sự kiện `person` — tên đã đăng ký, hoặc chuỗi `"unknown"`
khi không khớp (ngưỡng `unknown_score`). Vì vậy app phân loại trực tiếp từ sub_label.

- **Thẻ "Khuôn mặt" trên trang chủ Camera** (nằm dưới 3 thẻ số liệu):
  - Hai chip đếm **Đã biết N** (xanh) / **Chưa rõ M** (cam), chạm để lọc dải ảnh, chạm lần nữa để bỏ lọc.
  - Dải thumbnail ngang (64dp) của khuôn mặt hôm nay; mỗi ảnh có nhãn tên (xanh) hoặc "Chưa rõ" (cam).
  - "Xem tất cả" → màn Sự kiện khuôn mặt; chạm ảnh → mở sự kiện.
- **Màn "Sự kiện khuôn mặt"**: lọc Tất cả / Đã biết / Chưa rõ, lưới ảnh 1:1 có nhãn tên,
  chạm một ảnh → hộp thoại "Mở sự kiện" hoặc **gán tên** (train từ ảnh sự kiện bằng
  `POST /api/faces/train/{name}/classify` với `event_id`), danh sách tên có sẵn hiển thị để chọn nhanh.
- Dữ liệu lấy qua `/api/events?label=person&has_snapshot=1` rồi lọc client-side theo sub_label.

## 3zg. Cập nhật 29/10/2026 — sửa nhãn phiên bản bị "vv0.13.0"

- Nguyên nhân: CI đặt `APP_VERSION_NAME` = tên tag (`v0.13.0`) nên `BuildConfig.VERSION_NAME` đã có "v",
  trong khi UI lại ghép thêm `"v..."` -> "vv0.13.0".
- Thêm `ui/appVersionLabel`: bỏ tiền tố v/V rồi ghép đúng một chữ **V** (vd. `V0.13.0`);
  dùng ở header Cài đặt, hàng "Phiên bản FriCam" và header brand tab Camera; bỏ hậu tố `(build code)`.

## 3zh. Cập nhật 29/10/2026 — sửa tiêu đề hàng cài đặt bị xuống dòng

- Bỏ giá trị dài "phát hiện · ghi hình · âm thanh" ở hàng **Cấu hình Camera**.
- `IosRow`: tiêu đề `maxLines = 1` + ellipsis, subtitle tối đa 2 dòng, giá trị bên phải giới hạn
  `widthIn(max = 200.dp)` + ellipsis — không còn cảnh tiêu đề bị bóp thành 4 dòng.

## 4a. Cập nhật 29/10/2026 — GĐ A (Play-ready) + GĐ B (P0)

### GĐ A — sẵn sàng Google Play
- **targetSdk/compileSdk 36** (bắt buộc với app mới/cập nhật từ 31/08/2026), AGP **8.10.1**, Gradle **8.11.1**.
- Edge-to-edge: `enableEdgeToEdge` với `SystemBarStyle.dark` (icon thanh hệ thống luôn sáng cho nền tối).
- CI: cài `platforms;android-36` + `build-tools;36.0.0` nếu thiếu, build **APK + AAB** (`bundleRelease`),
  upload cả hai artifact, tag `v*` đính cả APK và AAB; `APP_VERSION_NAME` bỏ tiền tố `v` (semver số).
- **Demo mode**: nút “Dùng bản demo” ở màn đăng nhập → `https://demo.frigate.video` (Frigate 0.18.2 công khai,
  `auth.enabled=false`); `signIn` cho phép tài khoản trống và dùng phiên ẩn danh.
- **Privacy policy** `docs/privacy.md` (vi + en) + hàng “Chính sách quyền riêng tư” và “Mã nguồn / Báo lỗi”
  trong Cài đặt → Ứng dụng. Cần bật GitHub Pages (Settings → Pages → main /docs) để có URL HTTPS.
- 16 KB page size: đã kiểm tra AAR WebRTC 137.7151.05 (`p_align = 0x4000`) → đạt.

### GĐ B — tính năng P0
- **Cấu hình Camera mở rộng**: enabled, detect, motion, recordings, snapshots, audio, notifications,
  review_alerts, review_detections, object_descriptions + **profile cấu hình** (`GET /api/profiles`,
  `PUT /api/camera/*/set/profile`). Sửa lỗi tên feature: `record` → **`recordings`**.
- **Sửa dữ liệu sự kiện**: nhãn phụ (`/events/{id}/sub_label`), biển số
  (`/events/{id}/recognized_license_plate`), thuộc tính (`/events/{id}/attributes`),
  false positive (`/events/{id}/false_positive`).
- **Đổi tên khuôn mặt**: `POST /api/faces/{old_name}/rename`.
- **Cảnh báo**: chạm vào cảnh báo trong Hệ thống để **Đã hiểu** (`/notices/{id}/acknowledge`)
  hoặc **Ẩn vĩnh viễn** (`/notices/{id}/mute`).

### Việc còn lại cho Play
1. Bật GitHub Pages (Settings → Pages → Deploy from a branch → `main` / `/docs`) để có URL privacy.
2. Tạo tài khoản Play Console (25 USD, xác minh danh tính) → tạo app → upload AAB.
3. App content: privacy URL, ads = Không, **Sign-in details** = hướng dẫn “Dùng bản demo”
   (hoặc URL `https://demo.frigate.video`, không cần tài khoản), target audience 18+, content rating, Data safety.
4. Closed test 12 tester / 14 ngày liên tục → apply production.
5. Store assets: icon 512, feature graphic 1024x500, screenshot điện thoại.

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
