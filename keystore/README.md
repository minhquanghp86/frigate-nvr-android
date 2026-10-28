# Keystore ký APK

`frigate-release.jks` (PKCS12) dùng để ký mọi bản release → cài đè/cập nhật được qua nhau.
Trước đây CI ký bằng debug key sinh mới mỗi lần nên Android báo "xung đột gói".

- alias: `frigate`
- store/key password mặc định: `frigate-nvr` (đổi bằng biến môi trường
  `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD` nếu muốn).

Lưu ý: repo public nên keystore này công khai. Nếu muốn giữ kín, chuyển keystore sang
GitHub Secrets và set các biến môi trường tương ứng trong workflow.
