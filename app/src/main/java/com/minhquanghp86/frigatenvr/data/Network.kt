package com.minhquanghp86.frigatenvr.data

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.QueryMap
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/** Cấu hình kết nối (URL + chứng chỉ + token) — key để tái tạo client khi thay đổi. */
data class NetConfig(
    val baseUrl: String,
    val token: String?,
    val trustSelfSigned: Boolean,
)

interface FrigateApi {

    @GET("api/profile")
    suspend fun profile(): retrofit2.Response<Profile>

    @GET("api/auth")
    suspend fun refreshAuth(): retrofit2.Response<ResponseBody>

    @GET("api/config")
    suspend fun config(): FrigateConfig

    @GET("api/events")
    suspend fun events(
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

    @GET("api/{camera}/recordings/summary")
    suspend fun cameraRecordingsSummary(
        @Path("camera") camera: String,
        @Query("timezone") timezone: String,
    ): List<DayRecordings>

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

    /** Chỉ build SSL "tin tất cả" khi người dùng chủ động bật cho server tự ký. */
    private fun trustAllSsl(): Pair<javax.net.ssl.SSLSocketFactory, X509TrustManager> {
        val trustManager = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        }
        val sslContext = SSLContext.getInstance("TLS").apply {
            init(null, arrayOf<TrustManager>(trustManager), SecureRandom())
        }
        return sslContext.socketFactory to trustManager
    }

    fun client(
        config: NetConfig,
        tokenProvider: () -> String?,
        onUnauthorized: () -> Unit,
    ): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(AuthInterceptor(tokenProvider, onUnauthorized))

        if (config.trustSelfSigned) {
            val (ssl, tm) = trustAllSsl()
            builder.sslSocketFactory(ssl, tm)
            builder.hostnameVerifier { _, _ -> true }
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
