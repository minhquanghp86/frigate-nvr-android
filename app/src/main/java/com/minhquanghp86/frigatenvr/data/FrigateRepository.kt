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

    private val current: ServerSession?
        get() = (state.value as? SessionState.Ready)?.session

    // ------------------------------------------------------------------ phiên

    init {
        scope.launch { loadStoredSession() }
    }

    private suspend fun loadStoredSession() {
        val prefs = store.data.first()
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
        )
        _state.value = if (session.loggedIn) SessionState.Ready(session) else SessionState.Anonymous
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
        if (username.isBlank()) return@withContext "Chưa nhập tài khoản."

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
                        username = username.trim(),
                        password = password,
                        rememberPassword = remember,
                        trustSelfSigned = trustSelfSigned,
                        token = null,
                        authDisabled = true,
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

    /** Chạy một lệnh API với phiên hiện tại; lỗi 401 -> yêu cầu đăng nhập lại. */
    suspend fun <T> call(block: suspend FrigateApi.() -> T): T {
        val session = current ?: throw FrigateApiException("Chưa đăng nhập server Frigate.")
        return try {
            apiFor(session).block()
        } catch (e: HttpException) {
            when (e.code()) {
                401 -> {
                    handleUnauthorized()
                    throw FrigateApiException("Phiên đăng nhập hết hạn — đăng nhập lại.")
                }
                403 -> throw FrigateApiException("Tài khoản không có quyền trên camera này (403).")
                404 -> throw FrigateApiException("Không tìm thấy dữ liệu (404).")
                else -> throw FrigateApiException("Server trả lỗi HTTP ${e.code()}.")
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
    fun eventSnapshot(id: String) = url("/api/events/$id/snapshot.jpg?bbox=1")
    fun eventClip(id: String) = url("/api/events/$id/clip.mp4")
    fun cameraLatest(camera: String) = url("/api/$camera/latest.jpg")
    fun cameraMjpeg(camera: String, fps: Int, height: Int) =
        url("/api/$camera?fps=$fps&height=$height")
    fun reviewClip(id: String) = url("/api/review/$id/clip.mp4")
    fun vodHour(day: String, hour: String, camera: String): String {
        // day = "2026-10-28", hour = "14"
        val (y, m, d) = day.split("-")
        return url("/api/vod/$y-$m/$d/$hour/$camera")
    }

    companion object {
        private val KEY_BASE = stringPreferencesKey("base_url")
        private val KEY_USER = stringPreferencesKey("username")
        private val KEY_PASS = stringPreferencesKey("password")
        private val KEY_TOKEN = stringPreferencesKey("token")
        private val KEY_REMEMBER = booleanPreferencesKey("remember_password")
        private val KEY_TRUST = booleanPreferencesKey("trust_self_signed")
        private val KEY_AUTH_OFF = booleanPreferencesKey("auth_disabled")
    }
}
