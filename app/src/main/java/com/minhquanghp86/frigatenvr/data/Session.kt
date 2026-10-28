package com.minhquanghp86.frigatenvr.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/** Toàn bộ thông tin kết nối tới một server Frigate + phiên đăng nhập hiện tại. */
@Serializable
data class ServerSession(
    val baseUrl: String,
    val username: String = "",
    val password: String = "",
    val rememberPassword: Boolean = false,
    val trustSelfSigned: Boolean = true,
    val token: String? = null,
    val authDisabled: Boolean = false,
    val profile: Profile? = null,
) {
    val loggedIn: Boolean get() = authDisabled || !token.isNullOrBlank()

    val role: String get() = profile?.role ?: if (authDisabled) "admin" else "viewer"

    /** Header Authorization cho mọi request (nginx của Frigate cũng đọc header này). */
    val authHeader: String? get() = token?.takeIf { it.isNotBlank() }?.let { "Bearer $it" }
}

@Serializable
data class Profile(
    val username: String? = null,
    val role: String? = null,
    val cameras: List<String>? = null,
    val name: String? = null,
)

/** Trạng thái đọc phiên từ DataStore khi app khởi động. */
sealed interface SessionState {
    data object Loading : SessionState
    data object Anonymous : SessionState
    data class Ready(val session: ServerSession) : SessionState
}
