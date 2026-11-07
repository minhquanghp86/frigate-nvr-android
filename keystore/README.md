# Keystore ký APK — ĐÃ XOAY KEY, KHÔNG COMMIT KEY

## Cảnh báo: key cũ đã lộ

Keystore `frigate-release.jks` cũ đã từng được commit vào repo public,
kèm mật khẩu mặc định `frigate-nvr` ghi trong code. Coi như key đó đã lộ
hoàn toàn:

- **Không dùng lại key cũ cho bản release mới.**
- Ai có key cũ đều có thể ký APK giả mạo cập nhật đè lên app của bạn.
- Nếu app đã phát hành bằng key cũ, bạn phải tạo key mới và làm thủ tục
  xoay key / reset upload key với Google Play (Play App Signing).
  Người dùng đang cài bản cũ sẽ cần gỡ và cài lại một lần — không có cách
  cập nhật đè an toàn từ key đã lộ sang key mới.
- Xóa file khỏi working tree chưa đủ: file vẫn còn trong lịch sử git.
  Sau khi merge PR này, hãy purge lịch sử bằng `git filter-repo`
  hoặc BFG Repo-Cleaner, rồi force-push (cân nhắc kỹ vì làm thay đổi
  history của mọi người đang clone repo).

## Cách làm đúng từ nay

1. **Tạo key mới trên máy của bạn (không commit, không gửi qua chat/email):**

   ```bash
   keytool -genkeypair -v -keystore keystore/frigate-release.jks \
     -alias frigate -keyalg RSA -keysize 4096 -validity 10000 \
     -storetype PKCS12
   # Đặt mật khẩu mạnh, khác nhau cho store và key, và lưu vào password manager.
   ```

2. **Đưa key lên CI qua GitHub Secrets (repo Settings → Secrets → Actions):**

   ```bash
   base64 -w0 keystore/frigate-release.jks > /tmp/keystore.b64
   # Dán nội dung /tmp/keystore.b64 vào secret KEYSTORE_BASE64
   # Thêm các secret: KEYSTORE_PASSWORD, KEY_PASSWORD, KEY_ALIAS=frigate
   # Xóa file /tmp/keystore.b64 ngay sau đó.
   ```

   Workflow `.github/workflows/build-apk.yml` sẽ tự giải mã secret này
   thành `keystore/frigate-release.jks` trên runner, build xong runner bị
   hủy nên key không lưu lại đâu cả.

3. **Build local:** đặt `keystore/frigate-release.jks` vào thư mục này
   trên máy bạn, và đặt `KEYSTORE_PASSWORD` / `KEY_PASSWORD` qua biến
   môi trường. Không có keystore thì bản release sẽ ký bằng debug key
   (chỉ để test, không phát hành).

4. **Không bao giờ** commit file `.jks` / `.keystore` / `.p12`, không ghi
   mật khẩu mặc định vào code. `.gitignore` đã chặn `keystore/*.jks`.
