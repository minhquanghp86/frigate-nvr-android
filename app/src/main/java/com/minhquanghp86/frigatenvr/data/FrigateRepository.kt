package com.minhquanghp86.frigatenvr.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.HttpException

/** Lỗi API đã được dịch sang tiếng Việt cho người dùng. */
class FrigateApiException(message: String) : Exception(message)

class FrigateRepository(
    private val context: Context,
    private val store: DataStore<Preferences>,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow<SessionState>(SessionState.Loading)
    val state: StateFlow<SessionState> = _state.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    /** Thông báo toàn cục (đăng nhập thất bại, phiên hết hạn...) để UI hiển thị. */
    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice.asStateFlow()

    private val reauthInFlight = AtomicBoolean(false)

    @Volatile
    private var client: OkHttpClient? = null

    @Volatile
    private var clientKey: String? = null

    @Volatile
    private var api: FrigateApi? = null

    @Volatile
    private var apiKey: String? = null

    /** Cache /api/config để màn live mở tức thì (khỏi chờ roundtrip). */
    @Volatile
    private var cfgCache: Pair<Long, FrigateConfig>? = null
    private val cfgTtlMs = 60_000L

    private val _notifyEnabled = MutableStateFlow(false)
    val notifyEnabled: StateFlow<Boolean> = _notifyEnabled.asStateFlow()

    private val _notifyCameras = MutableStateFlow<Set<String>>(emptySet())
    val notifyCameras: StateFlow<Set<String>> = _notifyCameras.asStateFlow()

    data class Integrations(
        val darboardUrl: String = "",
        val haUrl: String = "",
        val haToken: String = "",
    )

    private val _integrations = MutableStateFlow(Integrations())
    val integrations: StateFlow<Integrations> = _integrations.asStateFlow()

    private val _notifyLabels = MutableStateFlow<Set<String>>(emptySet())
    val notifyLabels: StateFlow<Set<String>> = _notifyLabels.asStateFlow()

    /** Tăng mỗi khi dữ liệu sự kiện thay đổi (xoá...) để các danh sách tự làm mới. */
    private val _dataVersion = MutableStateFlow(0)
    val dataVersion: StateFlow<Int> = _dataVersion.asStateFlow()

    private val current: ServerSession?
        get() = (state.value as? SessionState.Ready)?.session

    // ------------------------------------------------------------------ phiên

    init {
        scope.launch { loadStoredSession() }
    }

    suspend fun configCached(): FrigateConfig {
        val now = System.currentTimeMillis()
        cfgCache?.let { (at, cfg) -> if (now - at < cfgTtlMs) return cfg }
        val cfg = call { config() }
        cfgCache = now to cfg
        return cfg
    }

    suspend fun deleteEvent(id: String) {
        call { deleteEvent(id) }
        _dataVersion.value = _dataVersion.value + 1
    }

    suspend fun setRetained(id: String, retained: Boolean) {
        call { if (retained) retainEvent(id) else unretenEvent(id) }
    }

    suspend fun labels(): List<String> = call { labels() }

    suspend fun logs(service: String): String = call { logs(service) }.string()

    suspend fun storage(): kotlinx.serialization.json.JsonElement = call { storage() }

    suspend fun notices(): kotlinx.serialization.json.JsonElement = call { notices() }

    suspend fun versionText(): String = call { version() }.string().trim()

    suspend fun earliestRecordingDay(): String? = runCatching {
        call { recordingsSummary(java.util.TimeZone.getDefault().id) }.keys.minOrNull()
    }.getOrNull()

    suspend fun statsJson(): kotlinx.serialization.json.JsonObject? =
        runCatching {
            val body = call { stats() }.string()
            kotlinx.serialization.json.Json.parseToJsonElement(body)
                as? kotlinx.serialization.json.JsonObject
        }.getOrNull()

    suspend fun setNotifyEnabled(enabled: Boolean) {
        store.edit { it[KEY_NOTIFY] = enabled }
        _notifyEnabled.value = enabled
    }

    suspend fun setNotifyLabels(labels: Set<String>) {
        store.edit { it[KEY_NOTIFY_LABELS] = labels.joinToString(",") }
        _notifyLabels.value = labels
    }

    suspend fun setNotifyCameras(cameras: Set<String>) {
        store.edit { it[KEY_NOTIFY_CAMS] = cameras.joinToString(",") }
        _notifyCameras.value = cameras
    }

    /** Làm mới profile — role có thể đã đổi sau lần đăng nhập trước. */
    fun refreshProfile() {
        val session = current ?: return
        if (session.token.isNullOrBlank()) return
        scope.launch {
            val profile = runCatching { apiFor(session).profile().body() }.getOrNull() ?: return@launch
            val updated = session.copy(profile = profile)
            runCatching { persist(updated) }
            _state.value = SessionState.Ready(updated)
        }
    }

    private suspend fun loadStoredSession() {
        val prefs = store.data.first()
        _notifyEnabled.value = prefs[KEY_NOTIFY] ?: false
        _notifyCameras.value = prefs[KEY_NOTIFY_CAMS]
            ?.split(",")
            ?.filter { it.isNotBlank() }
            ?.toSet()
            ?: emptySet()
        _integrations.value = Integrations(
            darboardUrl = prefs[KEY_DARBOARD].orEmpty(),
            haUrl = prefs[KEY_HA_URL].orEmpty(),
            haToken = prefs[KEY_HA_TOKEN].orEmpty(),
        )
        _notifyLabels.value = prefs[KEY_NOTIFY_LABELS]
            ?.split(",")
            ?.filter { it.isNotBlank() }
            ?.toSet()
            ?: emptySet()
        val base = prefs[KEY_BASE].orEmpty()
        if (base.isBlank()) {
            _state.value = SessionState.Anonymous
            return
        }
        val session = ServerSession(
            baseUrl = base,
            username = prefs[KEY_USER] ?: "",
            password = prefs[KEY_PASS] ?: "",
            rememberPassword = prefs[KEY_REMEMBER] ?: false,
            trustSelfSigned = prefs[KEY_TRUST] ?: true,
            token = prefs[KEY_TOKEN]?.takeIf { it.isNotBlank() },
            authDisabled = prefs[KEY_AUTH_OFF] ?: false,
            go2rtcUrl = prefs[KEY_GO2RTC]?.takeIf { it.isNotBlank() },
        )
        _state.value = if (session.loggedIn) SessionState.Ready(session) else SessionState.Anonymous
        if (session.loggedIn) refreshProfile()
    }

    private suspend fun persist(s: ServerSession) {
        store.edit { p ->
            p[KEY_BASE] = s.baseUrl
            p[KEY_USER] = s.username
            if (s.rememberPassword) p[KEY_PASS] = s.password else p.remove(KEY_PASS)
            p[KEY_REMEMBER] = s.rememberPassword
            p[KEY_TRUST] = s.trustSelfSigned
            p[KEY_TOKEN] = s.token.orEmpty()
            p[KEY_AUTH_OFF] = s.authDisabled
            if (s.go2rtcUrl.isNullOrBlank()) p.remove(KEY_GO2RTC) else p[KEY_GO2RTC] = s.go2rtcUrl
        }
    }

    fun noticeShown() {
        _notice.value = null
    }

    // ---------------------------------------------------------------- đăng nhập

    /**
     * Đăng nhập Frigate: POST /api/login rồi lấy JWT từ Set-Cookie (body rỗng).
     * Trả về null nếu thành công, ngược lại là thông báo lỗi tiếng Việt.
     */
    suspend fun signIn(
        baseUrlRaw: String,
        username: String,
        password: String,
        remember: Boolean,
        trustSelfSigned: Boolean,
    ): String? = withContext(Dispatchers.IO) {
        val base = FrigateNet.normalizeBaseUrl(baseUrlRaw)
        if (base.isBlank()) return@withContext "Chưa nhập địa chỉ server Frigate."

        _busy.value = true
        try {
            val client = FrigateNet.client(NetConfig(base, null, trustSelfSigned), { null }, {})
            val body = Json.encodeToString(LoginRequest(username.trim(), password))
                .toRequestBody("application/json".toMediaType())
            val request = Request.Builder().url("$base/api/login").post(body).build()

            val (code, text, token) = client.newCall(request).execute().use { resp ->
                val content = resp.body?.string().orEmpty()
                Triple(resp.code, content, tokenFrom(resp.headers.values("Set-Cookie"), content))
            }

            when {
                code == 401 || (code == 403) ->
                    "Sai tài khoản hoặc mật khẩu (Frigate trả ${code})."

                code == 404 && text.contains("Authentication is disabled", ignoreCase = true) -> {
                    // Frigate tắt xác thực -> dùng ẩn danh (port 5000 hoặc auth.enabled=false)
                    val session = ServerSession(
                        baseUrl = base,
                        username = username.trim().ifBlank { "Ẩn danh" },
                        password = password,
                        rememberPassword = remember,
                        trustSelfSigned = trustSelfSigned,
                        token = null,
                        authDisabled = true,
                        go2rtcUrl = current?.go2rtcUrl,
                    )
                    persist(session)
                    _state.value = SessionState.Ready(session)
                    null
                }

                !resp2xx(code) -> "Server trả lỗi HTTP $code khi đăng nhập."

                token == null ->
                    "Đăng nhập thành công nhưng không lấy được JWT — kiểm tra lại địa chỉ server."

                else -> {
                    val cfg = NetConfig(base, token, trustSelfSigned)
                    val api = FrigateNet.api(FrigateNet.client(cfg, { token }, {}), base)
                    val profile = runCatching { api.profile().body() }.getOrNull()
                    val session = ServerSession(
                        baseUrl = base,
                        username = username.trim(),
                        password = password,
                        rememberPassword = remember,
                        trustSelfSigned = trustSelfSigned,
                        token = token,
                        authDisabled = false,
                        profile = profile,
                        go2rtcUrl = current?.go2rtcUrl,
                    )
                    persist(session)
                    _state.value = SessionState.Ready(session)
                    null
                }
            }
        } catch (e: IOException) {
            "Không kết nối được $base: ${e.message ?: "lỗi mạng"}."
        } catch (e: Exception) {
            "Lỗi đăng nhập: ${e.message ?: e.javaClass.simpleName}"
        } finally {
            _busy.value = false
        }
    }

    private fun resp2xx(code: Int) = code in 200..299

    private fun tokenFrom(cookies: List<String>, body: String): String? {
        var fallback: String? = null
        for (cookie in cookies) {
            val pair = cookie.substringBefore(';')
            val name = pair.substringBefore('=').trim()
            val value = pair.substringAfter('=', "").trim()
            if (value.isBlank()) continue
            if (name == "frigate_token" || name.endsWith("_token")) return value
            if (fallback == null && name.isNotBlank()) fallback = value
        }
        // Một số reverse-proxy đặt token vào body JSON.
        return fallback ?: runCatching {
            Json.decodeFromString<LoginBody>(body).access_token
        }.getOrNull()
    }

    suspend fun signOut() {
        val session = current ?: return
        withContext(Dispatchers.IO) {
            runCatching {
                val c = clientFor(session)
                c.newCall(Request.Builder().url("${session.baseUrl}/api/logout").build())
                    .execute().close()
            }
        }
        store.edit { it.clear() }
        client = null
        clientKey = null
        api = null
        apiKey = null
        _state.value = SessionState.Anonymous
    }

    /** Cập nhật cài đặt server mà không cần đăng nhập lại (tín hiệu, mật khẩu...). */
    suspend fun updateSettings(
        baseUrlRaw: String? = null,
        username: String? = null,
        password: String? = null,
        remember: Boolean? = null,
        trustSelfSigned: Boolean? = null,
        go2rtcUrl: String? = null,
    ): String? {
        val session = current ?: return "Chưa đăng nhập."
        val base = baseUrlRaw?.let { FrigateNet.normalizeBaseUrl(it) } ?: session.baseUrl
        if (base.isBlank()) return "Địa chỉ server không được rỗng."
        val changed = base != session.baseUrl
        val updated = session.copy(
            baseUrl = base,
            username = username ?: session.username,
            password = password ?: session.password,
            rememberPassword = remember ?: session.rememberPassword,
            trustSelfSigned = trustSelfSigned ?: session.trustSelfSigned,
            go2rtcUrl = go2rtcUrl?.trim()?.ifBlank { null } ?: session.go2rtcUrl,
        )
        persist(updated)
        client = null
        clientKey = null
        api = null
        apiKey = null
        _state.value = SessionState.Ready(updated)
        return null
    }

    // ---------------------------------------------------------------- mạng API

    private fun clientFor(session: ServerSession): OkHttpClient {
        val cfg = NetConfig(session.baseUrl, session.authHeader, session.trustSelfSigned)
        val key = "$cfg"
        if (client != null && clientKey == key) return client!!
        val built = FrigateNet.client(cfg, { current?.authHeader }, ::handleUnauthorized)
        client = built
        clientKey = key
        return built
    }

    fun okhttp(): OkHttpClient {
        val session = current ?: throw FrigateApiException("Chưa đăng nhập server Frigate.")
        return clientFor(session)
    }

    fun session(): ServerSession? = current

    private fun apiFor(session: ServerSession): FrigateApi {
        val key = "${session.baseUrl}|${session.authHeader}|${session.trustSelfSigned}"
        if (api != null && apiKey == key) return api!!
        val built = FrigateNet.api(clientFor(session), session.baseUrl)
        api = built
        apiKey = key
        return built
    }

    /** Nội dung lỗi FastAPI (field nào sai...) để hiển thị thay vì chỉ mã HTTP. */
    private fun errorDetail(e: HttpException): String {
        val body = runCatching { e.response()?.errorBody()?.string() }.getOrNull() ?: return ""
        val compact = body.replace(Regex("\\s+"), " ").trim()
        if (compact.isBlank()) return ""
        val message = runCatching {
            val obj = kotlinx.serialization.json.Json.parseToJsonElement(compact)
                as? kotlinx.serialization.json.JsonObject
            (obj?.get("message") ?: obj?.get("detail"))
                ?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }
        }.getOrNull()
        return " — " + (message?.takeIf { it.isNotBlank() } ?: compact.take(240))
    }

    /** Chạy một lệnh API với phiên hiện tại; lỗi 401 -> yêu cầu đăng nhập lại. */
    suspend fun <T> call(block: suspend FrigateApi.() -> T): T {
        val session = current ?: throw FrigateApiException("Chưa đăng nhập server Frigate.")
        return try {
            apiFor(session).block()
        } catch (e: HttpException) {
            val detail = errorDetail(e)
            when (e.code()) {
                401 -> {
                    handleUnauthorized()
                    throw FrigateApiException("Phiên đăng nhập hết hạn — đăng nhập lại.")
                }
                403 -> throw FrigateApiException(
                    "Tài khoản không có quyền trên camera này (403).$detail",
                )
                404 -> throw FrigateApiException("Không tìm thấy dữ liệu (404).$detail")
                400 -> throw FrigateApiException("Yêu cầu không hợp lệ (400).$detail")
                422 -> throw FrigateApiException("Server từ chối tham số (422).$detail")
                else -> throw FrigateApiException("Server trả lỗi HTTP ${e.code()}.$detail")
            }
        } catch (e: FrigateApiException) {
            throw e
        } catch (e: Exception) {
            throw FrigateApiException(e.message ?: "Lỗi kết nối tới Frigate.")
        }
    }

    /**
     * Token không còn hợp lệ (401): nếu có mật khẩu đã lưu -> đăng nhập lại âm thầm,
     * ngược lại chuyển về màn hình đăng nhập.
     */
    private fun handleUnauthorized() {
        val session = current ?: return
        if (session.authDisabled) return
        if (!reauthInFlight.compareAndSet(false, true)) return

        scope.launch {
            try {
                if (session.rememberPassword && session.password.isNotBlank()) {
                    val err = signIn(
                        session.baseUrl,
                        session.username,
                        session.password,
                        true,
                        session.trustSelfSigned,
                    )
                    if (err == null) {
                        _notice.value = "Đã tự động đăng nhập lại."
                        return@launch
                    }
                }
                val cleared = session.copy(token = null)
                persist(cleared)
                _state.value = SessionState.Anonymous
                _notice.value = "Phiên đăng nhập hết hạn — vui lòng đăng nhập lại."
            } finally {
                reauthInFlight.set(false)
            }
        }
    }

    // --------------------------------------------------------------- media URL

    fun url(path: String): String {
        val session = current ?: throw FrigateApiException("Chưa đăng nhập server Frigate.")
        return session.baseUrl.trimEnd('/') + path
    }

    fun eventThumbnail(id: String) = url("/api/events/$id/thumbnail.jpg")

    /** Ảnh chụp từ bản ghi tại thời điểm bất kỳ (dùng để xem trước khi tua timeline). */
    fun recordingSnapshot(camera: String, epoch: Double, height: Int = 180) =
        url("/api/$camera/recordings/$epoch/snapshot.jpg?height=$height")

    suspend fun reviewsList(
        limit: Int = 80,
        reviewed: Boolean? = null,
        severity: String? = null,
    ): List<ReviewItem> {
        val params = mutableMapOf("limit" to limit.toString())
        reviewed?.let { params["reviewed"] = if (it) "1" else "0" }
        severity?.let { params["severity"] = it }
        return call { reviews(params) }
    }

    suspend fun markReviewsViewed(ids: List<String>, reviewed: Boolean = true) {
        call { markViewed(IdsBody(ids, reviewed)) }
    }
    fun eventSnapshot(id: String) = url("/api/events/$id/snapshot.jpg?bbox=1")

    /** Snapshot gốc, không vẽ box (dùng để train khuôn mặt). */
    fun eventSnapshotRaw(id: String) = url("/api/events/$id/snapshot.jpg")
    fun eventClip(id: String) = url("/api/events/$id/clip.mp4")
    fun cameraLatest(camera: String) = url("/api/$camera/latest.jpg")
    fun cameraMjpeg(camera: String, fps: Int, height: Int, boxes: Boolean = false) =
        url("/api/$camera?fps=$fps&height=$height" + if (boxes) "&bbox=1" else "")
    fun reviewClip(id: String) = url("/api/review/$id/clip.mp4")

    /** mp4 của một khoảng thời gian (dùng để tải 1 khung giờ ghi hình). */
    fun clipRange(camera: String, startSec: Long, endSec: Long): String =
        url("/api/$camera/start/$startSec/end/$endSec/clip.mp4")

    /**
     * HLS của một khung giờ ghi hình. Frigate phục vụ HLS qua nginx-vod ở /vod/...
     * (KHÔNG phải /api/vod/... — đường đó chỉ trả JSON mapping nên player báo
     * ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED). Kèm timezone để khớp giờ app hiển thị.
     */
    fun vodHour(day: String, hour: String, camera: String): String {
        val (y, m, d) = day.split("-")
        val tz = java.util.TimeZone.getDefault().id.replace("/", ",")
        return url("/vod/$y-$m/$d/$hour/$camera/$tz/master.m3u8")
    }

    // ------------------------------------------------------------ advanced search

    /** Tìm các sự kiện có ảnh giống sự kiện đã cho (thumbnail search của Frigate). */
    suspend fun similarEvents(eventId: String, limit: Int = 12): List<FrigateEvent> =
        call {
            eventsSearch(
                mapOf(
                    "event_id" to eventId,
                    "search_type" to "thumbnail",
                    "limit" to limit.toString(),
                    "include_thumbnails" to "0",
                ),
            )
        }

    /** Danh sách biển số đã nhận diện (LPR), đã tách các biển ghép. */
    suspend fun licensePlates(): List<String> {
        val element = call { recognizedLicensePlates("1") }
        return (element as? kotlinx.serialization.json.JsonArray)
            ?.mapNotNull { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }
            ?.filter { it.isNotBlank() }
            ?: emptyList()
    }

    /** Số sự kiện hôm nay theo từng camera (dùng /api/events/summary). */
    suspend fun eventsToday(): Map<String, Int> = runCatching {
        val element = call { eventsSummary(java.util.TimeZone.getDefault().id) }
        val rows = element as? kotlinx.serialization.json.JsonArray ?: return emptyMap()
        val today = java.time.LocalDate.now().toString()
        val counts = mutableMapOf<String, Int>()
        rows.mapNotNull { it as? kotlinx.serialization.json.JsonObject }
            .filter { it.str("day") == today }
            .forEach { row ->
                val camera = row.str("camera") ?: return@forEach
                val count = (row["count"] as? kotlinx.serialization.json.JsonPrimitive)
                    ?.content?.toIntOrNull() ?: 0
                counts[camera] = (counts[camera] ?: 0) + count
            }
        counts
    }.getOrDefault(emptyMap())

    suspend fun users(): List<FrigateUser> = runCatching { call { users() } }
        .getOrDefault(emptyList())

    suspend fun createUser(username: String, password: String, role: String) {
        call { createUser(NewUserBody(username, password, role)) }
    }

    suspend fun deleteUser(username: String) {
        call { deleteUser(username) }
    }

    suspend fun acknowledgeNotice(id: String) {
        call { acknowledgeNotice(id) }
    }

    suspend fun muteNotice(id: String) {
        call { muteNotice(id) }
    }

    data class ProfileState(val profiles: List<String>, val active: String?)

    suspend fun profiles(): ProfileState = runCatching {
        val obj = call { profiles() } as? kotlinx.serialization.json.JsonObject
        val list = (obj?.get("profiles") as? kotlinx.serialization.json.JsonArray)
            ?.mapNotNull { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }
            ?: emptyList()
        val active = (obj?.get("active_profile")
                as? kotlinx.serialization.json.JsonPrimitive)?.content
        ProfileState(list, active?.takeIf { it != "none" })
    }.getOrDefault(ProfileState(emptyList(), null))

    /** Kích hoạt profile cấu hình (null = tắt profile). */
    suspend fun setProfile(name: String?) {
        call { setCameraFeature("*", "profile", CameraSetBody(name ?: "none")) }
    }

    suspend fun setEventSubLabel(eventId: String, subLabel: String) {
        call { setSubLabel(eventId, SubLabelBody(subLabel)) }
    }

    suspend fun setEventPlate(eventId: String, plate: String) {
        call { setPlate(eventId, PlateBody(plate)) }
    }

    suspend fun setEventAttributes(eventId: String, attributes: List<String>) {
        call { setAttributes(eventId, AttributesBody(attributes)) }
    }

    suspend fun markFalsePositive(eventId: String) {
        call { falsePositive(eventId) }
    }

    suspend fun renameFace(oldName: String, newName: String) {
        call { renameFace(oldName, RenameFaceBody(newName)) }
    }

    suspend fun setUserRole(username: String, role: String) {
        call { setUserRole(username, RoleBody(role)) }
    }

    suspend fun setUserPassword(username: String, password: String) {
        call { setUserPassword(username, PasswordBody(password)) }
    }

    // ------------------------------------------------------- cast ngoài LAN

    suspend fun setIntegrations(darboardUrl: String, haUrl: String, haToken: String) {
        val clean = Integrations(
            darboardUrl = darboardUrl.trim().trimEnd('/'),
            haUrl = haUrl.trim().trimEnd('/'),
            haToken = haToken.trim(),
        )
        store.edit {
            it[KEY_DARBOARD] = clean.darboardUrl
            it[KEY_HA_URL] = clean.haUrl
            it[KEY_HA_TOKEN] = clean.haToken
        }
        _integrations.value = clean
    }

    private fun plainClient(): okhttp3.OkHttpClient =
        FrigateNet.client(NetConfig("https://localhost", null, true), { null }, {})

    private fun plainCall(request: okhttp3.Request): String =
        plainClient().newCall(request).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw FrigateApiException("HTTP ${resp.code}: ${body.take(200)}")
            }
            body
        }

    /** Danh sách panel Darboard đang kết nối (id -> tên). */
    suspend fun darboardScreens(): List<Pair<String, String>> = withContext(Dispatchers.IO) {
        val base = _integrations.value.darboardUrl
        if (base.isBlank()) throw FrigateApiException("Chưa cấu hình Darboard server.")
        val body = plainCall(okhttp3.Request.Builder().url("$base/screens/list").build())
        val arr = kotlinx.serialization.json.Json.parseToJsonElement(body)
            .let { (it as? kotlinx.serialization.json.JsonObject)?.get("screens") }
            as? kotlinx.serialization.json.JsonArray ?: return@withContext emptyList()
        arr.mapNotNull { it as? kotlinx.serialization.json.JsonObject }.mapNotNull { obj ->
            val id = (obj["id"] as? kotlinx.serialization.json.JsonPrimitive)?.content ?: return@mapNotNull null
            val name = (obj["name"] as? kotlinx.serialization.json.JsonPrimitive)?.content ?: id
            val online = (obj["online"] as? kotlinx.serialization.json.JsonPrimitive)
                ?.content?.toBooleanStrictOrNull() ?: true
            if (online) id to name else null
        }
    }

    /** Mở URL trên một panel Darboard (`POST /screens/act {act:"url"}`). */
    suspend fun darboardOpen(deviceId: String, url: String) = withContext(Dispatchers.IO) {
        val base = _integrations.value.darboardUrl
        if (base.isBlank()) throw FrigateApiException("Chưa cấu hình Darboard server.")
        val payload = kotlinx.serialization.json.buildJsonObject {
            put("id", kotlinx.serialization.json.JsonPrimitive(deviceId))
            put("act", kotlinx.serialization.json.JsonPrimitive("url"))
            put("url", kotlinx.serialization.json.JsonPrimitive(url))
        }.toString()
        plainCall(
            okhttp3.Request.Builder()
                .url("$base/screens/act")
                .post(payload.toRequestBody("application/json".toMediaType()))
                .build(),
        )
    }

    data class CastCheck(val label: String, val ok: Boolean, val detail: String)

    /** Kiểm tra nhanh cấu hình Darboard: URL + số panel online. */
    suspend fun testDarboard(): CastCheck = runCatching {
        val base = _integrations.value.darboardUrl
        if (base.isBlank()) return@runCatching CastCheck(
            "Darboard",
            false,
            "Chưa cấu hình URL Darboard (Cài đặt → Phát lên màn hình khác).",
        )
        val body = plainCall(okhttp3.Request.Builder().url("$base/screens/list").build())
        val arr = (kotlinx.serialization.json.Json.parseToJsonElement(body)
                as? kotlinx.serialization.json.JsonObject)
            ?.get("screens") as? kotlinx.serialization.json.JsonArray
        val names = arr?.mapNotNull { (it as? kotlinx.serialization.json.JsonObject)
            ?.get("name")?.let { n -> (n as? kotlinx.serialization.json.JsonPrimitive)?.content } }
            .orEmpty()
        CastCheck(
            "Darboard",
            true,
            "$base/screens/list → HTTP 200 · ${arr?.size ?: 0} panel" +
                if (names.isNotEmpty()) " (${names.joinToString(", ")})" else "",
        )
    }.getOrElse { CastCheck("Darboard", false, it.message ?: "Lỗi không xác định") }

    /** Kiểm tra cấu hình HA: token + số media_player. */
    suspend fun testHomeAssistant(): CastCheck = runCatching {
        val cfg = _integrations.value
        if (cfg.haUrl.isBlank() || cfg.haToken.isBlank()) {
            return@runCatching CastCheck(
                "Home Assistant",
                false,
                "Chưa nhập HA URL hoặc token (Cài đặt → Phát lên màn hình khác).",
            )
        }
        val players = haMediaPlayers()
        CastCheck(
            "Home Assistant",
            true,
            "${cfg.haUrl}/api/states → ${players.size} media_player" +
                if (players.isNotEmpty()) {
                    " (${players.take(5).joinToString(", ") { it.second }})"
                } else {
                    " — chưa có media_player nào trong HA"
                },
        )
    }.getOrElse { CastCheck("Home Assistant", false, it.message ?: "Lỗi không xác định") }

    /** URL trang camera trên Darboard (server tự proxy go2rtc). */
    fun darboardCamUrl(camera: String): String? {
        val base = _integrations.value.darboardUrl.ifBlank { return null }
        return "$base/cam.html?src=" + java.net.URLEncoder.encode(camera, "UTF-8")
    }

    /** media_player của Home Assistant (entity_id -> tên). */
    suspend fun haMediaPlayers(): List<Pair<String, String>> = withContext(Dispatchers.IO) {
        val cfg = _integrations.value
        if (cfg.haUrl.isBlank() || cfg.haToken.isBlank()) {
            throw FrigateApiException("Chưa cấu hình Home Assistant URL + token.")
        }
        val body = plainCall(
            okhttp3.Request.Builder()
                .url("${cfg.haUrl}/api/states")
                .header("Authorization", "Bearer ${cfg.haToken}")
                .build(),
        )
        val arr = kotlinx.serialization.json.Json.parseToJsonElement(body)
            as? kotlinx.serialization.json.JsonArray ?: return@withContext emptyList()
        arr.mapNotNull { it as? kotlinx.serialization.json.JsonObject }
            .filter {
                (it["entity_id"] as? kotlinx.serialization.json.JsonPrimitive)
                    ?.content?.startsWith("media_player.") == true
            }
            .mapNotNull { obj ->
                val id = (obj["entity_id"] as? kotlinx.serialization.json.JsonPrimitive)
                    ?.content ?: return@mapNotNull null
                val attrs = obj["attributes"] as? kotlinx.serialization.json.JsonObject
                val name = (attrs?.get("friendly_name")
                        as? kotlinx.serialization.json.JsonPrimitive)?.content ?: id
                val state = (obj["state"] as? kotlinx.serialization.json.JsonPrimitive)
                    ?.content.orEmpty()
                if (state == "unavailable") null else id to name
            }
    }

    /** Gọi HA phát URL HLS trên một media_player (Chromecast/Android TV). */
    suspend fun haPlayMedia(entityId: String, url: String) = withContext(Dispatchers.IO) {
        val cfg = _integrations.value
        if (cfg.haUrl.isBlank() || cfg.haToken.isBlank()) {
            throw FrigateApiException("Chưa cấu hình Home Assistant URL + token.")
        }
        val payload = kotlinx.serialization.json.buildJsonObject {
            put("entity_id", kotlinx.serialization.json.JsonPrimitive(entityId))
            put("media_content_id", kotlinx.serialization.json.JsonPrimitive(url))
            put("media_content_type", kotlinx.serialization.json.JsonPrimitive("video"))
        }.toString()
        plainCall(
            okhttp3.Request.Builder()
                .url("${cfg.haUrl}/api/services/media_player/play_media")
                .header("Authorization", "Bearer ${cfg.haToken}")
                .post(payload.toRequestBody("application/json".toMediaType()))
                .build(),
        )
    }

    // ------------------------------------------------------------ khuôn mặt

    /**
     * Sự kiện có khuôn mặt: person events có sub_label ("unknown" hoặc tên đã đăng ký).
     * after = epoch giây (null = lấy gần nhất).
     */
    suspend fun faceEvents(after: Long? = null, limit: Int = 80): List<FrigateEvent> {
        val params = mutableMapOf(
            "label" to "person",
            "has_snapshot" to "1",
            "limit" to limit.toString(),
            "include_thumbnails" to "1",
        )
        after?.let { params["after"] = it.toString() }
        return call { events(params) }.filter { it.isFaceEvent }
    }

    suspend fun createFace(name: String) {
        call { createFace(name) }
    }

    suspend fun registerFace(name: String, bytes: ByteArray, fileName: String, mime: String) {
        val body = bytes.toRequestBody(mime.toMediaTypeOrNull())
        val part = okhttp3.MultipartBody.Part.createFormData("file", fileName, body)
        call { registerFace(name, part) }
    }

    suspend fun deleteFaceImages(name: String, ids: List<String>) {
        call { deleteFaceImages(name, FaceDeleteBody(ids)) }
    }

    suspend fun reclassifyFace(name: String, imageId: String, newName: String) {
        call { reclassifyFace(name, FaceReclassifyBody(imageId, newName)) }
    }

    /** Train khuôn mặt từ một sự kiện (Frigate tự cắt khuôn mặt trong ảnh sự kiện). */
    suspend fun trainFaceFromEvent(name: String, eventId: String) {
        call { trainFace(name, FaceTrainBody(event_id = eventId)) }
    }

    /** Khuôn mặt đã đăng ký: tên -> danh sách file ảnh. */
    suspend fun faces(): Map<String, List<String>> {
        val element = call { facesApi() }
        val obj = element as? kotlinx.serialization.json.JsonObject ?: return emptyMap()
        return obj.mapValues { (_, value) ->
            (value as? kotlinx.serialization.json.JsonArray)
                ?.mapNotNull { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }
                ?: emptyList()
        }
    }

    // ------------------------------------------------------------------ export

    data class ExportProgress(val status: String, val step: String, val percent: Double)

    /** Tạo job export; trả về export id. */
    suspend fun createExport(camera: String, startSec: Long, endSec: Long): String? {
        val res = call {
            createExport(camera, startSec.toString(), endSec.toString(), ExportBody())
        }
        val obj = res as? kotlinx.serialization.json.JsonObject ?: return null
        val ok = obj.str("success")?.toBooleanStrictOrNull() ?: true
        if (!ok) return null
        return obj.str("export_id")
    }

    suspend fun exportProgress(exportId: String): ExportProgress? {
        val obj = call { exportJob(exportId) } as? kotlinx.serialization.json.JsonObject
            ?: return null
        return ExportProgress(
            status = obj.str("status") ?: "unknown",
            step = obj.str("current_step") ?: "",
            percent = obj.num("progress_percent") ?: 0.0,
        )
    }

    /** URL tải file export đã hoàn tất (nginx phục vụ /exports/<tên file>). */
    suspend fun exportDownloadUrl(exportId: String): String? {
        val arr = call { exports() } as? kotlinx.serialization.json.JsonArray ?: return null
        val row = arr.mapNotNull { it as? kotlinx.serialization.json.JsonObject }
            .firstOrNull { it.str("id") == exportId } ?: return null
        val path = row.str("video_path") ?: return null
        return url("/exports/" + path.substringAfterLast('/'))
    }

    suspend fun deleteExport(exportId: String) {
        call { deleteExports(ExportDeleteBody(listOf(exportId))) }
    }

    suspend fun exportsList(): List<kotlinx.serialization.json.JsonObject> {
        val arr = call { exports() } as? kotlinx.serialization.json.JsonArray ?: return emptyList()
        return arr.mapNotNull { it as? kotlinx.serialization.json.JsonObject }
    }

    // ------------------------------------------------------------------ go2rtc

    /** go2rtc (mặc định http://<host-frigate>:1984) — luồng gốc, fps đầy đủ. */
    fun go2rtcBase(): String {
        val session = current ?: throw FrigateApiException("Chưa đăng nhập server Frigate.")
        session.go2rtcUrl?.takeIf { it.isNotBlank() }?.let {
            return FrigateNet.normalizeBaseUrl(it)
        }
        val host = runCatching { java.net.URI(session.baseUrl).host }.getOrNull()
            ?: return FrigateNet.normalizeBaseUrl(session.baseUrl)
        return "http://$host:1984"
    }

    /** WHEP qua proxy có xác thực của Frigate (không cần mở port 1984). */
    fun webRtcEndpoint(stream: String) = url("/api/go2rtc/webrtc?src=$stream")

    /** Bật/tắt tính năng camera (API yêu cầu role admin). */
    suspend fun setCameraFeature(camera: String, feature: String, on: Boolean) {
        call { setCameraFeature(camera, feature, CameraSetBody(if (on) "ON" else "OFF")) }
    }

    fun go2rtcHls(stream: String) = "${go2rtcBase()}/api/stream.m3u8?src=$stream"

    fun go2rtcMp4(stream: String) = "${go2rtcBase()}/api/stream.mp4?src=$stream"

    /**
     * Làm nóng producer của go2rtc trước khi mở live: go2rtc chỉ khởi động luồng khi có consumer,
     * nên mở sớm (rồi hủy sau ~400ms) giúp camera bắt đầu kết nối RTSP trong lúc điều hướng.
     */
    fun warmStream(stream: String) {
        scope.launch {
            runCatching {
                val client = clientFor(current ?: return@launch)
                val call = client.newCall(
                    okhttp3.Request.Builder().url(go2rtcMp4(stream)).build(),
                )
                val job = launch {
                    runCatching { call.execute().use { it.body?.byteStream()?.read() } }
                }
                // Giữ consumer ~6s: đủ để WebRTC nối vào và dùng chung producer,
                // sau đó nhả để không tốn băng thông.
                kotlinx.coroutines.delay(6_000)
                call.cancel()
                job.cancel()
            }
        }
    }

    companion object {
        private val KEY_BASE = stringPreferencesKey("base_url")
        private val KEY_USER = stringPreferencesKey("username")
        private val KEY_PASS = stringPreferencesKey("password")
        private val KEY_TOKEN = stringPreferencesKey("token")
        private val KEY_REMEMBER = booleanPreferencesKey("remember_password")
        private val KEY_TRUST = booleanPreferencesKey("trust_self_signed")
        private val KEY_AUTH_OFF = booleanPreferencesKey("auth_disabled")
        private val KEY_GO2RTC = stringPreferencesKey("go2rtc_url")
        private val KEY_NOTIFY = booleanPreferencesKey("notify_enabled")
        private val KEY_NOTIFY_CAMS = stringPreferencesKey("notify_cameras")
        private val KEY_NOTIFY_LABELS = stringPreferencesKey("notify_labels")
        private val KEY_DARBOARD = stringPreferencesKey("darboard_url")
        private val KEY_HA_URL = stringPreferencesKey("ha_url")
        private val KEY_HA_TOKEN = stringPreferencesKey("ha_token")
    }
}
