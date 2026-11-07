package com.minhquanghp86.frigatenvr.data

import okhttp3.Interceptor
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.Part
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.QueryMap
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import javax.net.ssl.HttpsURLConnection
import java.security.MessageDigest
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/** Cấu hình kết nối (URL + chứng chỉ + token) — key để tái tạo client khi thay đổi. */
class NetConfig(
    val baseUrl: String,
    val token: String?,
    val trustSelfSigned: Boolean,
    /** SHA-256 fingerprint (hex, chữ thường) của các cert tự ký đã được chấp nhận. */
    val acceptedCertFingerprints: Set<String> = emptySet(),
    /** Gọi khi gặp cert tự ký mới lần đầu (TOFU) — caller lưu fingerprint vào SecureStorage. */
    val onAcceptCertFingerprint: ((String) -> Unit)? = null,
) {
    // Callback không tham gia equals/hashCode: mỗi lần tạo lambda mới là một
    // object khác, nếu tính vào equals thì client sẽ bị build lại liên tục.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is NetConfig) return false
        return baseUrl == other.baseUrl &&
            token == other.token &&
            trustSelfSigned == other.trustSelfSigned &&
            acceptedCertFingerprints.map { it.lowercase() }.toSet() ==
            other.acceptedCertFingerprints.map { it.lowercase() }.toSet()
    }

    override fun hashCode(): Int {
        var result = baseUrl.hashCode()
        result = 31 * result + (token?.hashCode() ?: 0)
        result = 31 * result + trustSelfSigned.hashCode()
        result = 31 * result + acceptedCertFingerprints.map { it.lowercase() }.toSet().hashCode()
        return result
    }

    override fun toString(): String =
        "NetConfig(baseUrl=$baseUrl, trustSelfSigned=$trustSelfSigned, " +
            "fingerprints=${acceptedCertFingerprints.sorted()})"
}

interface FrigateApi {

    @GET("api/profile")
    suspend fun profile(): retrofit2.Response<Profile>

    @GET("api/auth")
    suspend fun refreshAuth(): retrofit2.Response<ResponseBody>

    @GET("api/config")
    suspend fun config(): FrigateConfig

    @GET("api/config")
    suspend fun configJson(): kotlinx.serialization.json.JsonElement

    @PUT("api/config/set")
    suspend fun configSet(@Body body: ConfigSetBody): retrofit2.Response<ResponseBody>

    @PUT("api/lpr/reprocess")
    suspend fun reprocessPlate(
        @Query("event_id") eventId: String,
    ): retrofit2.Response<ResponseBody>

    @GET("api/stats/history")
    suspend fun statsHistory(
        @Query("keys") keys: String? = null,
    ): kotlinx.serialization.json.JsonElement

    @GET("api/events")
    suspend fun events(
        @QueryMap params: Map<String, @JvmSuppressWildcards String>,
    ): List<FrigateEvent>

    @GET("api/events/summary")
    suspend fun eventsSummary(
        @Query("timezone") timezone: String,
    ): kotlinx.serialization.json.JsonElement

    @GET("api/events/search")
    suspend fun eventsSearch(
        @QueryMap params: Map<String, @JvmSuppressWildcards String>,
    ): List<FrigateEvent>

    @GET("api/events/{id}")
    suspend fun event(@Path("id") id: String): FrigateEvent

    @GET("api/review")
    suspend fun reviews(
        @QueryMap params: Map<String, @JvmSuppressWildcards String>,
    ): List<ReviewItem>

    @POST("api/reviews/viewed")
    suspend fun markViewed(@Body body: IdsBody): retrofit2.Response<ResponseBody>

    @GET("api/recordings/summary")
    suspend fun recordingsSummary(
        @Query("timezone") timezone: String,
        @Query("cameras") cameras: String = "all",
    ): Map<String, Boolean>

    @GET("api/{camera}/recordings")
    suspend fun recordings(
        @Path("camera") camera: String,
        @Query("after") after: Long,
        @Query("before") before: Long,
    ): kotlinx.serialization.json.JsonElement

    @GET("api/{camera}/recordings/summary")
    suspend fun cameraRecordingsSummary(
        @Path("camera") camera: String,
        @Query("timezone") timezone: String,
    ): List<DayRecordings>

    @DELETE("api/events/{id}")
    suspend fun deleteEvent(@Path("id") id: String): retrofit2.Response<ResponseBody>

    @POST("api/events/{id}/retain")
    suspend fun retainEvent(@Path("id") id: String): retrofit2.Response<ResponseBody>

    @DELETE("api/events/{id}/retain")
    suspend fun unretenEvent(@Path("id") id: String): retrofit2.Response<ResponseBody>

    @POST("api/export/{camera}/start/{start}/end/{end}")
    suspend fun createExport(
        @Path("camera") camera: String,
        @Path("start") start: String,
        @Path("end") end: String,
        @Body body: ExportBody,
    ): kotlinx.serialization.json.JsonElement

    @GET("api/jobs/export/{id}")
    suspend fun exportJob(@Path("id") id: String): kotlinx.serialization.json.JsonElement

    @GET("api/recognized_license_plates")
    suspend fun recognizedLicensePlates(
        @Query("split_joined") splitJoined: String? = null,
    ): kotlinx.serialization.json.JsonElement

    @POST("api/notices/{id}/acknowledge")
    suspend fun acknowledgeNotice(@Path("id") id: String): ResponseBody

    @POST("api/notices/{id}/mute")
    suspend fun muteNotice(@Path("id") id: String): ResponseBody

    @GET("api/profiles")
    suspend fun profiles(): kotlinx.serialization.json.JsonElement

    @POST("api/events/{id}/sub_label")
    suspend fun setSubLabel(
        @Path("id") id: String,
        @Body body: SubLabelBody,
    ): ResponseBody

    @POST("api/events/{id}/recognized_license_plate")
    suspend fun setPlate(
        @Path("id") id: String,
        @Body body: PlateBody,
    ): ResponseBody

    @POST("api/events/{id}/attributes")
    suspend fun setAttributes(
        @Path("id") id: String,
        @Body body: AttributesBody,
    ): ResponseBody

    @POST("api/events/{id}/false_positive")
    suspend fun falsePositive(@Path("id") id: String): ResponseBody

    @POST("api/faces/{old_name}/rename")
    suspend fun renameFace(
        @Path("old_name") oldName: String,
        @Body body: RenameFaceBody,
    ): ResponseBody

    @GET("api/users")
    suspend fun users(): List<FrigateUser>

    @POST("api/users")
    suspend fun createUser(@Body body: NewUserBody): ResponseBody

    @DELETE("api/users/{username}")
    suspend fun deleteUser(@Path("username") username: String): ResponseBody

    @PUT("api/users/{username}/role")
    suspend fun setUserRole(
        @Path("username") username: String,
        @Body body: RoleBody,
    ): ResponseBody

    @PUT("api/users/{username}/password")
    suspend fun setUserPassword(
        @Path("username") username: String,
        @Body body: PasswordBody,
    ): ResponseBody

    @POST("api/faces/{name}/create")
    suspend fun createFace(@Path("name") name: String): ResponseBody

    @Multipart
    @POST("api/faces/{name}/register")
    suspend fun registerFace(
        @Path("name") name: String,
        @Part file: MultipartBody.Part,
    ): ResponseBody

    @POST("api/faces/reprocess")
    suspend fun reprocessFace(@Body body: FaceTrainBody): ResponseBody

    @POST("api/faces/train/{name}/classify")
    suspend fun trainFace(
        @Path("name") name: String,
        @Body body: FaceTrainBody,
    ): ResponseBody

    @POST("api/faces/{name}/delete")
    suspend fun deleteFaceImages(
        @Path("name") name: String,
        @Body body: FaceDeleteBody,
    ): ResponseBody

    @POST("api/faces/{name}/reclassify")
    suspend fun reclassifyFace(
        @Path("name") name: String,
        @Body body: FaceReclassifyBody,
    ): ResponseBody

    @GET("api/faces")
    suspend fun facesApi(): kotlinx.serialization.json.JsonElement

    @POST("api/exports/delete")
    suspend fun deleteExports(@Body body: ExportDeleteBody): ResponseBody

    @GET("api/exports")
    suspend fun exports(): kotlinx.serialization.json.JsonElement

    @GET("api/labels")
    suspend fun labels(): List<String>

    @GET("api/logs/{service}")
    suspend fun logs(@Path("service") service: String): ResponseBody

    @GET("api/recordings/storage")
    suspend fun storage(): kotlinx.serialization.json.JsonElement

    @GET("api/notices")
    suspend fun notices(): kotlinx.serialization.json.JsonElement

    // Frigate 0.18 trả PlainTextResponse (vd "0.18.2-cfa08c5"), không phải JSON object.
    @GET("api/version")
    suspend fun version(): ResponseBody

    @PUT("api/camera/{camera}/set/{feature}")
    suspend fun setCameraFeature(
        @Path("camera") camera: String,
        @Path("feature") feature: String,
        @Body body: CameraSetBody,
    ): retrofit2.Response<ResponseBody>

    @GET("api/stats")
    suspend fun stats(): ResponseBody
}

class AuthInterceptor(
    private val tokenProvider: () -> String?,
    private val onUnauthorized: () -> Unit,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val builder = original.newBuilder()
        tokenProvider()?.let { builder.header("Authorization", it) }
        original.headers["X-Frigate-Client"]?.let { } // giữ nguyên header nếu có
        val response = chain.proceed(builder.build())
        if (response.code == 401) onUnauthorized()
        return response
    }
}

object FrigateNet {

    val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        explicitNulls = false
    }

    fun normalizeBaseUrl(raw: String): String {
        var url = raw.trim().trimEnd('/')
        if (url.isEmpty()) return url
        if (!url.contains("://")) url = "http://$url"
        return url
    }

    /** Chỉ cho phép HTTP (không mã hóa) với host LAN riêng tư / localhost. */
    fun isPrivateHost(host: String): Boolean {
        val h = host.trim().lowercase().removeSuffix(".")
        if (h == "localhost" || h.endsWith(".local") || h.endsWith(".lan")) return true
        if (h.startsWith("10.") || h.startsWith("192.168.")) return true
        if (h.startsWith("172.")) {
            val second = h.removePrefix("172.").substringBefore('.').toIntOrNull()
            if (second != null && second in 16..31) return true
        }
        // IPv6 loopback / ULA
        if (h == "::1" || h.startsWith("fd") || h.startsWith("fe80:")) return true
        return false
    }

    /** Từ chối HTTP tới host công khai — mật khẩu/token không được đi qua HTTP công khai. */
    fun requireHttpsForPublicHost(baseUrl: String) {
        val parsed = baseUrl.toHttpUrlOrNull() ?: return
        if (parsed.scheme == "http" && !isPrivateHost(parsed.host)) {
            throw IllegalArgumentException(
                "Chỉ cho phép HTTP với địa chỉ LAN riêng tư. " +
                    "Server công khai phải dùng HTTPS.",
            )
        }
    }

    private fun sha256Hex(cert: X509Certificate): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(cert.encoded)
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun isSelfSigned(cert: X509Certificate): Boolean {
        return try {
            if (cert.issuerX500Principal != cert.subjectX500Principal) return false
            cert.verify(cert.publicKey)
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun defaultTrustManager(): X509TrustManager {
        val factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        factory.init(null as java.security.KeyStore?)
        return factory.trustManagers.filterIsInstance<X509TrustManager>().first()
    }

    /**
     * Thay thế trust-all cũ.
     *
     * Trước đây: bất kỳ cert nào, bất kỳ host nào cũng được tin, và
     * hostnameVerifier luôn trả true — kẻ tấn công MITM chỉ cần một cert
     * tự ký bất kỳ là đọc được mật khẩu/token.
     *
     * Từ nay: thử trust hệ thống trước. Chỉ khi người dùng đã bật
     * "Tin chứng chỉ tự ký" VÀ cert là self-signed duy nhất, còn hạn,
     * thì mới chấp nhận theo cơ chế TOFU: lần đầu ghi lại fingerprint
     * SHA-256, các lần sau bắt buộc fingerprint phải khớp. Hostname vẫn
     * được kiểm tra bằng verifier mặc định, trừ khi fingerprint đã pin.
     */
    private fun scopedTrustManager(config: NetConfig): Pair<javax.net.ssl.SSLSocketFactory, X509TrustManager> {
        val defaultTm = defaultTrustManager()
        val trustManager = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
                defaultTm.checkClientTrusted(chain, authType)
            }

            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
                try {
                    defaultTm.checkServerTrusted(chain, authType)
                    return
                } catch (e: CertificateException) {
                    if (!config.trustSelfSigned) throw e
                    val certs = chain ?: throw e
                    if (certs.size != 1) throw e
                    val cert = certs[0]
                    if (!isSelfSigned(cert)) throw e
                    cert.checkValidity() // hết hạn thì từ chối
                    val fingerprint = sha256Hex(cert)
                    val accepted = config.acceptedCertFingerprints.map { it.lowercase() }.toSet()
                    if (fingerprint in accepted) return
                    // Lần đầu gặp cert này: chỉ chấp nhận nếu caller cho phép ghi TOFU.
                    // Caller (FrigateRepository) ghi fingerprint vào SecureStorage.
                    val onAccept = config.onAcceptCertFingerprint
                        ?: throw CertificateException(
                            "Chứng chỉ tự ký chưa được chấp nhận (SHA-256: $fingerprint). " +
                                "Bật 'Tin chứng chỉ tự ký' và đăng nhập lại để chấp nhận cert này.",
                            e,
                        )
                    onAccept(fingerprint)
                }
            }

            override fun getAcceptedIssuers(): Array<X509Certificate> = defaultTm.acceptedIssuers
        }
        val sslContext = SSLContext.getInstance("TLS").apply {
            init(null, arrayOf<TrustManager>(trustManager), null)
        }
        return sslContext.socketFactory to trustManager
    }

    fun client(
        config: NetConfig,
        tokenProvider: () -> String?,
        onUnauthorized: () -> Unit,
    ): OkHttpClient {
        requireHttpsForPublicHost(config.baseUrl)
        val builder = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(AuthInterceptor(tokenProvider, onUnauthorized))

        if (config.trustSelfSigned) {
            val (ssl, tm) = scopedTrustManager(config)
            builder.sslSocketFactory(ssl, tm)
            // Không dùng hostnameVerifier luôn-true nữa. Dùng verifier mặc định,
            // nhưng cho phép lệch hostname khi cert đã được pin bằng fingerprint.
            val defaultVerifier = HttpsURLConnection.getDefaultHostnameVerifier()
            builder.hostnameVerifier { hostname, session ->
                if (defaultVerifier.verify(hostname, session)) return@hostnameVerifier true
                return@hostnameVerifier try {
                    val cert = session.peerCertificates.firstOrNull() as? X509Certificate
                        ?: return@hostnameVerifier false
                    if (!isSelfSigned(cert)) return@hostnameVerifier false
                    val fingerprint = sha256Hex(cert)
                    fingerprint in config.acceptedCertFingerprints.map { it.lowercase() }.toSet()
                } catch (_: Exception) {
                    false
                }
            }
        }

        if (com.minhquanghp86.frigatenvr.BuildConfig.DEBUG) {
            builder.addInterceptor(
                HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BASIC
                },
            )
        }
        return builder.build()
    }

    fun api(client: OkHttpClient, baseUrl: String): FrigateApi =
        Retrofit.Builder()
            .baseUrl(baseUrl.trimEnd('/') + "/")
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(FrigateApi::class.java)
}
