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
- **CI**: build bằng GitHub-hosted runner `ubuntu-latest`, artifact `frigate-nvr-android-<version>`.

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
