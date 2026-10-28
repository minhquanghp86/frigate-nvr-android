# API Frigate đã dùng trong app

Nguồn: tài liệu chính thức `docs.frigate.video` + bản OpenAPI `docs/static/frigate-api.yaml` (repo blakeblackshear/frigate) + source `frigate/api/*.py` và `nginx.conf` (nhánh `dev`, ngày 28/10/2026).

Mọi endpoint đều nằm sau tiền tố `/api/` (nginx của Frigate mount FastAPI tại đó).

## 1. Xác thực

| Endpoint | Method | Ghi chú |
|---|---|---|
| `/api/login` | POST | Body `{"user","password"}`. **Trả JWT chỉ qua `Set-Cookie: frigate_token=...`** (body rỗng, `Response("", 200)`). Nếu `auth.enabled=false` → trả 404 `{"message":"Authentication is disabled"}` → app chuyển chế độ ẩn danh. Sai mật khẩu → 401 `{"message":"Login failed"}`. |
| `/api/auth` | GET | Tự refresh token (frontend gọi khi sắp hết hạn). |
| `/api/logout` | GET | Xoá cookie phiên. |
| `/api/profile` | GET | `{username, role, cameras?}` — kiểm tra token sau đăng nhập. |

- Port **8971** = TLS + xác thực (app dùng port này). Port **5000** = nội bộ không xác thực.
- Cookie name mặc định `frigate_token` (config `auth.cookie_name`).
- nginx bảo vệ bằng `auth_request` ở **hầu hết** route (`/api/*`, `/vod/*`, `/clips/*`, `/recordings/*`, `/exports/*`, `/live/*`, ws...); ngoại lệ: `/api/login`, `/api/logout`, `/api/auth/first_time_login`.
- Quan trọng: subrequest auth **có truyền `Authorization` và `Cookie`** (`auth_location.conf`), nên gọi bằng `Bearer` works everywhere — kể cả HLS và ảnh.

## 2. Camera / live

| Endpoint | Method | Params | Dùng trong app |
|---|---|---|---|
| `/api/config` | GET | — | Danh sách camera (`cameras.*.enabled`, `detect.width/height`, `record.enabled`, `audio.enabled`) |
| `/api/{camera}` | GET | `fps` (mặc định 3), `height` (360), `bbox`, `timestamp` | **MJPEG live** — app dùng `fps`+`height` người chọn |
| `/api/{camera}/latest.{ext}` | GET | — | Ảnh mới nhất → lưới camera + fallback tĩnh |
| `/api/go2rtc/streams` | GET | — | Nguồn stream thật (chưa dùng — GĐ3 WebRTC) |

## 3. Sự kiện (events)

`GET /api/events` — tham số: `cameras`, `labels`, `zones`, `limit`, `offset`, `after`, `before`, `time_range`, `has_clip`, `has_snapshot`, `in_progress`, `favorites`, `min_score`, `max_score`, `sort`, `timezone`, `include_thumbnails`, `attributes`, `sub_label`, `recognized_license_plate`, `is_submitted`, `min_length`, `max_length`.

App dùng: `limit=50`, `include_thumbnails=0`, `cameras`, `labels`, `has_clip=1`, `before=<start_time xa nhất>` (phân trang).

| Endpoint | Method | Dùng |
|---|---|---|
| `/api/events/{id}` | GET | Chi tiết |
| `/api/events/{id}/thumbnail.jpg` | GET | Thumbnail lưới sự kiện |
| `/api/events/{id}/snapshot.jpg?bbox=1` | GET | Ảnh có khung nhận diện |
| `/api/events/{id}/clip.mp4` | GET | Phát clip (mp4, hỗ trợ Range) |
| `/api/events/{id}/preview.gif` | GET | GIF xem nhanh (chưa dùng) |

Response (từ `model_to_dict`): `id, camera, label, sub_label (string hoặc [name, id]), start_time, end_time (null = đang diễn ra), has_clip, has_snapshot, top_score, zones[], attributes[], data{top_score, score, box, attributes}, plus_id`.

## 4. Review (mục cần xem)

| Endpoint | Method | Params / body | Dùng |
|---|---|---|---|
| `/api/review` | GET | `cameras`, `labels`, `zones`, `reviewed`, `severity`, `limit`, `before`, `after` | `reviewed=false` + `limit=50` |
| `/api/reviews/viewed` | POST | `{"ids": [...]}` | Đánh dấu đã xem |
| `/api/review/{id}/clip.mp4` | GET | — | Phát clip của mục review |
| `/api/review/{id}` | GET | — | Chi tiết |
| `/api/review/{id}/viewed` | DELETE | — | Bỏ đánh dấu đã xem |

Response: `id, camera, start_time, end_time (epoch **hoặc** chuỗi ISO tùy version → app nhận JsonElement), has_been_reviewed, severity (alert|detection), thumb_path (đường dẫn file nội bộ, **không** serve qua HTTP), data{detections[], motion[], audio[], description}`.
→ Thumbnail list lấy từ `/api/events/{event_id}/thumbnail.jpg` với `data.detections[0]`.

## 5. Xem lại / bản ghi

| Endpoint | Method | Params | Trả về |
|---|---|---|---|
| `/api/recordings/summary` | GET | `timezone`, `cameras` | `{"2026-10-28": true, ...}` — ngày nào có ghi |
| `/api/{camera}/recordings/summary` | GET | `timezone` | `[{day, events, hours: [{hour:"14", events, motion, objects, duration}]}]` (mới nhất trước) |
| `/vod/{yyyy-mm}/{dd}/{hh}/{camera}[/{tz}]/master.m3u8` | GET | — | **HLS thật** cho khung giờ (nginx-vod). `/api/vod/...` chỉ trả JSON mapping → player báo `PARSING_CONTAINER_UNSUPPORTED`. `tz` dạng `Asia,Ho_Chi_Minh` để khớp giờ app |
| `/api/vod/{camera}/start/{ts}/end/{ts}` | GET | — | HLS theo khoảng tùy ý |
| `/api/{camera}/start/{ts}/end/{ts}/clip.mp4` | GET | — | mp4 theo khoảng |
| `/api/recordings/storage` | GET | — | Dung lượng (GĐ3) |

## 6. Sức khỏe / hệ thống

| Endpoint | Method | Dùng |
|---|---|---|
| `/api/version` | GET | `version, branch, commit, date, install_type` |
| `/api/stats` | GET | JSON thay đổi theo version: `service{uptime, mem_usage, cpu_usage}, detection_fps, cameras{name}{camera_fps, process_fps, ...}` → app đọc bằng `JsonElement` (không hardcode schema) |
| `/api/notices` | GET | Cảnh báo hệ thống (GĐ3) |

## 7. Chưa dùng (ứng viên GĐ3)

- `POST /api/notifications/register` + `GET /api/notifications/pubkey` — Web Push.
- `/api/camera/{name}/set/{feature}` và `/{sub_command}` — điều khiển/PTZ.
- `POST /api/export/{camera}/start/{start}/end/{end}`, `/api/exports` — xuất clip.
- `POST /api/events/search`, `/api/{camera}/search/motion` — tìm kiếm theo vector/motion.
- `/api/review/activity/motion`, `/api/timeline`, `/api/stats/history`.
- go2rtc WebRTC: `/api/go2rtc/webrtc`, `/live/webrtc/api/ws` (thay MJPEG cho tiết kiệm băng thông).

## 8. Khác biệt của Frigate 0.18 (đã kiểm chứng bằng app + source v0.18.2)

Đây là các điểm **đã từng làm app crash/422**, sửa rồi thì đừng đổi lại:

| Chỗ | 0.18 | Xử lý trong app |
|---|---|---|
| `GET /api/version` | `PlainTextResponse`, trả `0.18.2-cfa08c5` (không phải JSON) | `version()` trả `ResponseBody`, tự nhận chuỗi hay object |
| `cameras.*.live.streams` | **object** `{"ten":"ten_go2rtc"}` (bản cũ là mảng) | `/api/config` đọc lỏng bằng `JsonObject`, chỉ lấy `enabled/record/audio` |
| `GET /api/review` | `reviewed` là **int** `0`/`1` (gửi `false` → 422) | gửi `reviewed=0` hoặc `1` |
| `POST /api/reviews/viewed` | body bắt buộc `{"ids":[...], "reviewed": true}` | `IdsBody` có field `reviewed` mặc định `true` |
| `ReviewSegmentResponse.start_time` | `datetime` → chuỗi ISO-8601 | `ReviewItem` nhận `JsonElement`, tự parse epoch/ISO |
| `data.detections` (review) | vẫn là danh sách **event id** (`objects` là tên nhãn) | dùng `detections[0]` cho thumbnail |
| `EventResponse` | `top_score` không còn ở top-level, nằm trong `data.top_score` | `bestScore` đọc `topScore ?: data.topScore` |
| `include_thumbnails` | deprecated nhưng vẫn nhận | vẫn gửi `0` |

Unit test khoá các shape này: `app/src/test/java/.../data/ModelsTest.kt` (`./gradlew testReleaseUnitTest`).

## 9. go2rtc (luồng fps gốc)

Frigate chạy kèm go2rtc ở **port 1984** (trong addon host_network là IP máy HA, không có xác thực):

| Endpoint | Dùng |
|---|---|
| `GET /api/streams` | Danh sách stream (vd `dahua1`, `ezviz_sub`...) |
| `GET /api/stream.m3u8?src=<stream>` | Master HLS → variant `hls/playlist.m3u8?id=...`; ExoPlayer phát trực tiếp |
| `GET /api/stream.mp4?src=<stream>` | fMP4 liên tục (H264/AAC passthrough), độ trễ thấp nhất |
| `POST /api/webrtc?src=<stream>` | WHEP (SDP offer/answer) |
| `POST /api/go2rtc/webrtc?src=<stream>` | WHEP **qua Frigate (có auth)** — app dùng cái này; GET bị 403 vì nginx chỉ cho POST, thiếu token → 401 |

App dùng HLS/MP4 với `src` = tên stream trong `cameras.<cam>.live.streams` (fallback: tên camera).
Bảo mật: go2rtc 1984 không có auth — nếu không muốn mở, phải chặn ở firewall và dùng MJPEG (qua Frigate).
