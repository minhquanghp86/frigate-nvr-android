# Agent Handover — FriCam (frigate-nvr-android) Security Fix

> Tài liệu bàn giao cho agent tiếp theo làm việc trên repo này.
> Ngôn ngữ làm việc với user: **tiếng Việt**. User: GitHub `minhquanghp86`.
> Ngày làm việc: **2026-11-07**. Agent thực hiện: Muse (trợ lý cá nhân của user).
> **KHÔNG có mật khẩu, token, base64 keystore hay giá trị secret nào trong file này — chỉ có tên secret.**

## 1. Repo là gì (phân tích ngày 2026-11-07)

- **FriCam**: Android client không chính thức cho **Frigate NVR 0.18.2**, Kotlin + Jetpack
  Compose Material 3, giao diện kiểu iOS, sáng/tối, chỉ có tiếng Việt (chưa i18n).
- Commit gốc lúc phân tích: `920abc4` (2026-11-03). ~14.087 dòng Kotlin, 37 file Kotlin
  chính + 1 file test, 18 màn hình, 4 tab: Camera, Sự kiện, Xem lại, Cài đặt.
- Stack: Kotlin 2.2.10, AGP 8.10.1, Gradle 8.11.1, `minSdk 26`, `compileSdk/targetSdk 36`,
  Media3 1.11.1 + NextLib FFmpeg (H.265/HEVC), WebRTC `137.7151.05`, Google Cast 21.4.0,
  Retrofit 2.11 + OkHttp 4.12 + kotlinx-serialization, Coil, DataStore.
- Kiến trúc: single module, không Hilt/Koin/Room. `FrigateRepository.kt` (~1.124 dòng) gần
  như god-class. `Network.kt` định nghĩa `FrigateApi` ~50 endpoint, JSON parse lenient để
  chịu được thay đổi schema Frigate 0.18.
- Live 4 chế độ: WebRTC/WHEP (mặc định), MP4/HLS qua go2rtc port 1984, MJPEG. Timeline dùng
  recording segments thật từ `/api/{camera}/recordings` với độ chính xác `Double`.
- Tính năng: Events / Faces / LPR, Review, quản trị System, Users, cấu hình camera,
  profiles, notices, storage, logs, detector charts (poll 3s), notification qua foreground
  service `EventWatchService`, Cast qua Google Cast và qua Home Assistant.
- CI: `.github/workflows/build-apk.yml`, ubuntu-latest, JDK 17, SDK 36,
  `./gradlew assembleRelease bundleRelease testReleaseUnitTest`, tag `v*` tạo GitHub Release.
  `versionCode = GITHUB_RUN_NUMBER`.
- Chưa có: PTZ (Frigate 0.18 API chỉ có `GET /{camera}/ptz/info`), R8/minify, Room cache offline.
- Lưu ý: README từng ghi stable v1.1.3 (2026-10-31) trong khi tag đã là v1.2.17 — README
  stale so với code.

## 2. Hai lỗi bảo mật đã sửa (PR #1, đã merge)

### 2.1. Keystore ký release commit công khai trong repo
- `keystore/frigate-release.jks` từng nằm trong repo public, mật khẩu mặc định `frigate-nvr`
  được ghi trong `keystore/README.md` cũ → key cũ coi như **đã lộ**.
- Đã sửa: xóa file khỏi repo, `.gitignore` chặn `*.jks`, `app/build.gradle.kts` không còn
  mật khẩu mặc định (thiếu `KEYSTORE_PASSWORD`/`KEY_PASSWORD` thì fail rõ ràng thay vì
  fallback), CI khôi phục keystore từ secret `KEYSTORE_BASE64`.

### 2.2. Mật khẩu/token plaintext + trust-all SSL / cleartext
- Trước đây: mật khẩu Frigate, JWT, token Home Assistant lưu plaintext trong DataStore
  Preferences; `trustSelfSigned` mặc định `true` kèm trust-all SSL + hostnameVerifier
  luôn-true; manifest `usesCleartextTraffic=true`.
- Đã sửa:
  - Mới: `data/SecureStorage.kt` — EncryptedSharedPreferences (Android Keystore,
    MasterKey AES256_GCM), migration một lần từ DataStore plaintext rồi xóa key cũ,
    `signOut()` xóa secrets đã mã hóa.
  - `Network.kt`: bỏ trust-all; thử system trust trước, chỉ chấp nhận **một** cert
    self-signed duy nhất, còn hạn, pin bằng SHA-256 fingerprint (TOFU, lưu trong
    SecureStorage). Mặc định `trustSelfSigned=false`.
  - HTTP tới host công khai bị từ chối, chỉ HTTP tới LAN riêng tư được phép.
    `usesCleartextTraffic=false` + `network_security_config.xml`.
  - Thêm `SecurityTest.kt` (kiểm tra host riêng tư/công khai). Cập nhật README mục 4 và
    `docs/privacy.md`.

## 3. Lịch sử PR và CI (đã xác minh qua GitHub Actions)

| Hạng mục | Kết quả |
|---|---|
| PR #1 — `security: remove public keystore, encrypt credentials, pin self-signed certs` | Mở 2026-11-07, **user merge** vào `main` thành `752becf` (08:17 +07). 14 file, +405/−40, 6 commit. |
| File workflow `.github/workflows/build-apk.yml` | **Không đẩy được qua GitHub App** (403, App thiếu quyền `workflows`). Đã commit qua giao diện web bằng tài khoản user thành `e883fe6` (+16 dòng: bước khôi phục keystore từ secret + env `KEYSTORE_PASSWORD`/`KEY_PASSWORD`/`KEY_ALIAS`). Sau đó đưa vào `main` bằng PR #3 (xem mục 4). |
| CI run #174 (PR #1) | **Fail** — `app/build.gradle.kts`: `Val cannot be reassigned` ở `storePassword = storePassword` và `keyPassword = keyPassword`. |
| PR #2 — `fix: Val cannot be reassigned in release signing config` | Nhánh `fix/build-gradle-signing`, commit `4cbbc4f`: đổi biến local thành `storePass`/`keyPass`. Nguyên nhân: biến local `val` che property cùng tên của SigningConfig. |
| CI run #176 (PR #2) | **Fail** — `:app:compileReleaseKotlin`, `Network.kt:336`: `HttpUrl.parse` bị deprecated ở mức **error** trong OkHttp 4.12. |
| PR #2 — bổ sung commit `eb29fe4` | Thay bằng `baseUrl.toHttpUrlOrNull()` + import `okhttp3.HttpUrl.Companion.toHttpUrlOrNull`. Không còn chỗ nào dùng `HttpUrl.parse`. |
| CI run #177 (PR #2, commit `eb29fe4`) | **Success** trong 5m34s: `assembleRelease`, `bundleRelease`, `testReleaseUnitTest` đều pass, 0 error (3 warnings, 1 notice). Artifacts: APK 33,1 MB, AAB 32,5 MB. |
| PR #2 merge | **Đã merge** vào `main` thành `704f3e7` (08:45 +07). |
| PR #3 — workflow vào `main` | Nhánh `fix/security-keystore-encrypted-credentials` (commit `e883fe6`, chỉ 1 file workflow, +16 dòng) → `main`. **Đã merge** thành `daeb40d` (08:58 +07, merge commit, 2 parent `704f3e7` + `e883fe6`). CI run #180 trên `main` đang chạy lúc merge xong. |

**Cảnh báo quan trọng về run #177:** run này chạy trên nhánh PR #2, mà workflow trên
nhánh đó **chưa có** bước khôi phục keystore (bước đó chỉ có trên nhánh fix/security ở
commit `e883fe6`), nên APK/AAB của run #177 được ký bằng **debug key**, không phải
keystore mới. Chỉ sau khi PR #3 merge thì CI trên `main` mới ký bằng keystore mới.
Đừng nhầm artifacts của run #177 là bản release đã ký đúng key.

## 4. Trạng thái cuối cùng của `main` (cập nhật sau khi dọn dẹp 2026-11-07)

- HEAD `main`: `daeb40d` (merge PR #3 — workflow khôi phục keystore, 08:58 +07).
  Trước đó `704f3e7` (merge PR #2) rồi `752becf` (merge PR #1).
- Các nhánh feature `fix/security-keystore-encrypted-credentials` và
  `fix/build-gradle-signing` đã merge xong và **đã bị xóa** sau khi dọn dẹp. Chỉ còn `main`.
- Keystore mới: RSA 4096, validity 10000 ngày, alias `frigate`, JKS.
  SHA-256 fingerprint của cert: `86:65:0C:16:A3:F6:2A:6E:DF:FE:31:70:CC:3C:AA:91:E1:EF:B9:3B:BC:31:62:AE:C0:61:95:7F:09:C4:47:00`.
  **Không ghi mật khẩu ở đây.** Mật khẩu nằm trong file info user đã tải về và cất giữ.
  Bản base64 của keystore nằm trong GitHub Secret `KEYSTORE_BASE64` (xem mục 5).
- 4 GitHub Actions secrets đã có trong repo (chỉ tên, không giá trị):
  `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_PASSWORD`, `KEY_ALIAS`.
- Các bản copy keystore/mật khẩu trên máy ảo của agent đã bị xóa (shred) sau khi user
  xác nhận đã tải xong (2026-11-07 ~08:55 +07). File tạm chứa secret cũng đã xóa.
  Agent không còn giữ bản copy nào của keystore mới hay mật khẩu của nó.

## 5. Việc còn tồn đọng (CHƯA làm — agent tiếp theo cần biết)

1. **Purge keystore cũ khỏi lịch sử git.** Xóa file khỏi HEAD chưa đủ: key cũ vẫn nằm
   trong history của `main` (các commit trước PR #1). Cần `git filter-repo` hoặc BFG
   để xóa `keystore/frigate-release.jks` khỏi toàn bộ history, rồi force-push `main`.
   Việc này viết lại lịch sử — chỉ làm khi không ai đang làm việc trên repo, và thông
   báo trước cho mọi người đang clone repo. Chưa làm tới 2026-11-07.
2. **Xoay upload key trên Google Play Console** nếu app đã từng phát hành bằng key cũ
   (key cũ đã lộ công khai). Cần làm trong Play Console, agent không làm thay được.
3. **Kiểm tra CI trên `main` sau PR #3.** Merge PR #3 sẽ trigger workflow trên `main`;
   xác nhận run đó success và APK/AAB được ký bằng keystore mới (log có dòng
   "Đã khôi phục keystore từ secret."). Nếu run trên `main` fail, đọc log theo đúng
   quy trình mục 6.
4. Người dùng trên bản build ký bằng key cũ sẽ cần gỡ cài đặt và cài lại một lần khi
   chuyển sang key mới (key khác nhau không update đè được).

## 6. Bài học / cạm bẫy đã gặp (đừng lặp lại)

- **GitHub App của Muse không có quyền `workflows`**: mọi thao tác với
  `.github/workflows/*` qua API/App đều trả 403, kể cả `push_files` lẫn
  `create_or_update_file`. Phải sửa workflow qua **giao diện web GitHub** bằng tài khoản
  user (đã đăng nhập, có 2FA bằng authenticator app). `create_branch`, `push_files`,
  `create_or_update_file`, `delete_file`, `create_pull_request`, `merge_pull_request`
  qua App đều hoạt động với file thường.
- **GitHub Secrets là write-only**: đặt được, không đọc lại được giá trị, kể cả chủ repo.
  Vì vậy keystore mới phải được user tải về từ máy agent trước khi xóa bản trên máy agent.
- **Kotlin DSL `signingConfigs`**: đừng đặt tên biến local trùng tên property
  (`storePassword`, `keyPassword`) — compiler báo `Val cannot be reassigned`.
- **OkHttp 4.12**: `HttpUrl.parse` deprecated ở mức **error**, không phải warning.
  Dùng `String.toHttpUrlOrNull()` (`import okhttp3.HttpUrl.Companion.toHttpUrlOrNull`).
- **Kiểm tra CI bằng API commit-status không đủ**: endpoint `get_status` trả `pending`,
  `total_count: 0` dù Actions đang chạy/fail. Muốn biết kết quả CI phải xem tab
  **Actions** trên web GitHub (hoặc qua browser task đã đăng nhập).
- **Đừng xóa nhánh khi còn commit chưa vào `main`**: commit workflow `e883fe6` từng chỉ
  nằm trên nhánh `fix/security-keystore-encrypted-credentials` sau khi PR #1 đã merge;
  xóa nhánh lúc đó sẽ làm mất luôn bước khôi phục keystore. Luôn kiểm tra `list_branches`
  và diff của nhánh với `main` trước khi xóa.
- **PR #1 từng bị merge khi CI đang fail** (user merge tay). Sau đó `main` bị gãy build
  cho tới khi PR #2 merge xong. Nhắc user chờ CI xanh trước khi merge.
- Máy agent lúc đầu **không có Java/Android SDK** nên không chạy được `./gradlew` để
  kiểm chứng tại chỗ; CI là nơi kiểm chứng build/test đầu tiên. Sau đó đã cài JDK 17
  tạm (Temurin tarball, đã xóa) chỉ để có `keytool` tạo keystore mới.
- User đã cho **quyền đứng (standing permission)**: được thao tác trên git/GitHub mà
  không cần xin phép từng lần (2026-11-07). Các thao tác qua GitHub App vẫn hiện thẻ
  xác nhận của hệ thống — đó là cơ chế của runtime, không phải user chưa cho phép.

## 7. Cách kiểm chứng nhanh (cho agent tiếp theo)

```bash
# Trạng thái main và file workflow
git log --oneline -5 main
git show main:.github/workflows/build-apk.yml | grep -n "Khôi phục keystore"
# File keystore KHÔNG được xuất hiện trong tree của main
git ls-tree -r main --name-only | grep -i keystore || echo "khong co keystore trong main"
```

- PR #1: https://github.com/minhquanghp86/frigate-nvr-android/pull/1 (merged)
- PR #2: https://github.com/minhquanghp86/frigate-nvr-android/pull/2 (merged)
- CI run #177 (success): https://github.com/minhquanghp86/frigate-nvr-android/actions/runs/42325046862
- Secrets: Settings → Secrets and variables → Actions (4 secret, xem mục 4).
- Keystore mới: user giữ bản duy nhất; agent không còn bản copy nào.


## 8. Verification & fixes — agent tiếp theo (2026-11-07)

Đã kiểm chứng lại toàn bộ mục 2–5 và bổ sung sửa lỗi phát hiện khi review:

**Xác nhận đúng**: keystore đã rời HEAD; `.gitignore` chặn `*.jks`; signing không còn mật khẩu mặc định;
CI trên `main` (run 42326924511, commit `1954e4c`) success và log có `Đã khôi phục keystore từ secret.`;
`Network.kt` bỏ trust-all, TOFU pin SHA-256; `SecureStorage` EncryptedSharedPreferences + migration;
`SecurityTest` chạy pass local (`testReleaseUnitTest`).

**Lỗi phát hiện & đã sửa**:
1. `usesCleartextTraffic=false` + NSC base `false` chặn **toàn bộ HTTP**, làm hỏng go2rtc
   `http://<host>:1984` (HLS/MP4/MJPEG) và HA `http://192.168.x.x:8123`.
   → Đã cho phép cleartext ở tầng Android, giữ chặn HTTP công khai trong code
   (`requireHttpsForPublicHost`) và validate thêm `haUrl`, `go2rtcUrl`.
2. `isPrivateHost` thiếu **Tailscale 100.64.0.0/10**, `fc00::/7`, `*.ts.net` → đã bổ sung + test.
3. Release build thiếu keystore **âm thầm ký debug** → giờ `assembleRelease/bundleRelease` fail rõ ràng
   nếu thiếu `keystore/frigate-release.jks`.
4. `allowBackup="true"` với EncryptedSharedPreferences dễ lỗi khi restore sang máy khác → đặt `false`.

**Chính sách version từ 2026-11-07**: versionName mặc định **`2.0.1`**, không dùng `0.x` nữa;
bản phát hành security/key mới **v2.0.0 → v2.0.1** (breaking: đổi signing key buộc gỡ/cài lại).

**Đã hoàn tất sau mục này**:
- Purge `keystore/frigate-release.jks` khỏi **toàn bộ history + tags** bằng `git filter-branch`,
  force-push `main` và mọi tag; GitHub `commits?path=keystore/frigate-release.jks` = 0 commit.
  Backup mirror: `/root/work/backups/frigate-android-mirror-20261107-024127.git`.
- CI `main`/`v2.0.0`/`v2.0.1` đều success, ký bằng keystore mới từ Secrets; user đã cài v2.0.1.
- **Play Console chưa từng upload** → không cần xoay upload key; lần upload đầu tiên dùng key mới.

**Tồn dư**: commit cũ vẫn có thể truy cập qua SHA/PR refs trên GitHub (cache) — key cũ đã bị coi là lộ,
không dùng lại; chưa ảnh hưởng vì chưa phát hành Play. Việc còn lại là user-side (GitHub Pages privacy,
nộp Play, closed test 12 tester nếu tài khoản cá nhân).


## 9. Sự cố 09/11/2026 — cert Frigate đổi, app báo “Hostname … not verified” (đã sửa, v2.0.2)

Frigate tự sinh lại cert mặc định (`CN=*`, không SAN) → xảy ra 2 lỗi phối hợp: hostname verifier dùng
snapshot pin cũ trong khi trust manager vừa pin cert mới; và `apiFor()` cache không tính fingerprints
nên client cũ bị dùng lại. Đã sửa: tập pin live dùng chung, TOFU chỉ auto-pin khi chưa có pin, cache key
gồm fingerprints, thêm nút “Xoá chứng chỉ đã tin” trong Cài đặt → Kết nối. Xem mục 4av của BAN-GIAO.


## 10. UX chứng chỉ (09/11/2026, v2.0.3)

Cert mặc định của Frigate (`O=FRIGATE DEFAULT CERT`) bị xoay khi add-on recreate → app tự thay pin và
hiện thông báo nhẹ. Cert lần đầu/cert khác được hỏi bằng hộp thoại 1 chạm trước khi đăng nhập
(`certPromptFor` + `AlertDialog` trong LoginScreen), chấp nhận xong tự đăng nhập lại.
Metadata `O` lưu trong `SecureStorage` (`accepted_cert_orgs`). Vẫn giữ nút “Xoá chứng chỉ đã tin”.
