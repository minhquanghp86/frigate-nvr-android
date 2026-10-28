package com.minhquanghp86.frigatenvr.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.webrtc.DataChannel
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.MediaStreamTrack
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.RtpTransceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoFrame
import org.webrtc.VideoSink
import org.webrtc.VideoTrack

@Volatile
private var webrtcInitialized = false
private val webrtcInitLock = Any()

private fun ensureWebRtcInitialized(context: Context) {
    if (webrtcInitialized) return
    synchronized(webrtcInitLock) {
        if (webrtcInitialized) return
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(context.applicationContext)
                .createInitializationOptions(),
        )
        webrtcInitialized = true
    }
}

/**
 * Xem live bằng WebRTC (WHEP) qua proxy có xác thực của Frigate:
 * POST /api/go2rtc/webrtc?src=<stream>  (Content-Type: application/sdp, Bearer JWT)
 * -> độ trễ thấp, fps gốc, không phải mở port go2rtc 1984 ra LAN.
 */
@Composable
fun WebRtcLiveView(
    endpoint: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val repo = rememberRepo()
    val client = remember { repo.okhttp() }

    var status by remember(endpoint) { mutableStateOf("Đang kết nối WebRTC…") }
    var showOverlay by remember(endpoint) { mutableStateOf(true) }
    // Tỉ lệ khung hình thật của video (đọc từ frame đầu tiên) để ép View đúng tỉ lệ,
    // tránh việc renderer phóng/cắt khi view hình chữ nhật khác tỉ lệ.
    var videoAspect by remember(endpoint) { mutableStateOf<Float?>(null) }

    val eglBase = remember { EglBase.create() }
    val renderer = remember {
        SurfaceViewRenderer(context).apply {
            init(eglBase.eglBaseContext, null)
            // FIT: hiện đủ khung hình (có viền đen), không phóng/cắt như FILL.
            setScalingType(org.webrtc.RendererCommon.ScalingType.SCALE_ASPECT_FIT)
            setEnableHardwareScaler(false)
            setMirror(false)
        }
    }
    val factory = remember {
        ensureWebRtcInitialized(context)
        PeerConnectionFactory.builder()
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(eglBase.eglBaseContext))
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(eglBase.eglBaseContext, false, false))
            .createPeerConnectionFactory()
    }
    val pcHolder = remember { arrayOfNulls<PeerConnection>(1) }
    val sink = remember {
        object : VideoSink {
            override fun onFrame(frame: VideoFrame) {
                if (videoAspect == null) {
                    // rotatedWidth/Height đã tính cả rotation của frame.
                    val w = frame.rotatedWidth.toFloat()
                    val h = frame.rotatedHeight.toFloat()
                    if (w > 0 && h > 0) videoAspect = w / h
                }
                renderer.onFrame(frame)
            }
        }
    }

    DisposableEffect(endpoint) {
        onDispose {
            runCatching { pcHolder[0]?.close() }
            runCatching { pcHolder[0]?.dispose() }
            runCatching { factory.dispose() }
            runCatching { renderer.release() }
            runCatching { eglBase.release() }
        }
    }

    LaunchedEffect(endpoint) {
        try {
            val iceGathered = CompletableDeferred<Unit>()
            val observer = object : PeerConnection.Observer {
                override fun onSignalingChange(state: PeerConnection.SignalingState?) = Unit
                override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) = Unit
                override fun onIceConnectionReceivingChange(receiving: Boolean) = Unit
                override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {
                    if (state == PeerConnection.IceGatheringState.COMPLETE) {
                        iceGathered.complete(Unit)
                    }
                }

                override fun onIceCandidate(candidate: IceCandidate?) = Unit
                override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) = Unit
                override fun onAddStream(stream: MediaStream?) {
                    stream?.videoTracks?.firstOrNull()?.addSink(sink)
                }

                override fun onRemoveStream(stream: MediaStream?) = Unit
                override fun onDataChannel(channel: DataChannel?) = Unit
                override fun onRenegotiationNeeded() = Unit
                override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {
                    (receiver?.track() as? VideoTrack)?.addSink(sink)
                }

                override fun onTrack(transceiver: RtpTransceiver?) {
                    (transceiver?.receiver?.track() as? VideoTrack)?.addSink(sink)
                }

                override fun onConnectionChange(state: PeerConnection.PeerConnectionState?) {
                    when (state) {
                        PeerConnection.PeerConnectionState.CONNECTED -> {
                            showOverlay = false
                            status = "WebRTC đã kết nối"
                        }

                        PeerConnection.PeerConnectionState.FAILED,
                        PeerConnection.PeerConnectionState.CLOSED,
                        -> {
                            showOverlay = true
                            status = "WebRTC mất kết nối"
                        }

                        else -> Unit
                    }
                }
            }

            val config = PeerConnection.RTCConfiguration(emptyList<PeerConnection.IceServer>())
                .apply { sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN }
            val pc = factory.createPeerConnection(config, observer)
                ?: throw IllegalStateException("Không tạo được PeerConnection")
            pcHolder[0] = pc
            pc.addTransceiver(
                MediaStreamTrack.MediaType.MEDIA_TYPE_VIDEO,
                RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.RECV_ONLY),
            )

            val offer = pc.createOfferAwait()
            pc.setLocalDescriptionAwait(offer)
            // go2rtc dùng non-trickle: chờ gom ICE rồi mới gửi offer (tối đa 3s).
            withTimeoutOrNull(3_000) { iceGathered.await() }

            status = "Đang trao đổi SDP…"
            val answer = withContext(Dispatchers.IO) { postOffer(client, endpoint, offer.description) }
            pc.setRemoteDescriptionAwait(SessionDescription(SessionDescription.Type.ANSWER, answer))
            status = "Đang chờ hình ảnh…"
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            showOverlay = true
            status = e.message ?: "Lỗi WebRTC"
        }
    }

    Box(
        modifier
            .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        val aspect = videoAspect
        AndroidView(
            factory = { renderer },
            modifier = if (aspect != null) {
                Modifier
                    .align(Alignment.Center)
                    .aspectRatio(aspect)
            } else {
                Modifier.fillMaxSize()
            },
        )
        if (showOverlay) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color(0x88000000)),
                contentAlignment = Alignment.Center,
            ) {
                androidx.compose.foundation.layout.Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(20.dp),
                ) {
                    if (!status.contains("Lỗi") && !status.contains("mất kết nối")) {
                        CircularProgressIndicator()
                    }
                    Text(
                        status,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------- helpers WHEP

private fun postOffer(client: OkHttpClient, endpoint: String, sdp: String): String {
    val request = Request.Builder()
        .url(endpoint)
        .post(sdp.toRequestBody("application/sdp".toMediaType()))
        .build()
    client.newCall(request).execute().use { response ->
        val body = response.body?.string().orEmpty()
        if (!response.isSuccessful) {
            throw IllegalStateException("WebRTC HTTP ${response.code}: ${body.take(200)}")
        }
        if (body.isBlank()) throw IllegalStateException("WebRTC: server trả SDP rỗng")
        return body
    }
}

private suspend fun PeerConnection.createOfferAwait(): SessionDescription =
    suspendCancellableCoroutine { cont ->
        createOffer(object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription?) {
                if (sdp == null) {
                    if (cont.isActive) cont.resumeWithException(IllegalStateException("createOffer rỗng"))
                } else if (cont.isActive) {
                    cont.resume(sdp)
                }
            }

            override fun onCreateFailure(error: String?) {
                if (cont.isActive) cont.resumeWithException(IllegalStateException(error ?: "createOffer lỗi"))
            }

            override fun onSetSuccess() = Unit
            override fun onSetFailure(error: String?) = Unit
        }, MediaConstraints())
    }

private suspend fun PeerConnection.setLocalDescriptionAwait(sdp: SessionDescription) =
    suspendCancellableCoroutine { cont ->
        setLocalDescription(object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription?) = Unit
            override fun onCreateFailure(error: String?) = Unit
            override fun onSetSuccess() {
                if (cont.isActive) cont.resume(Unit)
            }

            override fun onSetFailure(error: String?) {
                if (cont.isActive) cont.resumeWithException(IllegalStateException(error ?: "setLocalDescription lỗi"))
            }
        }, sdp)
    }

private suspend fun PeerConnection.setRemoteDescriptionAwait(sdp: SessionDescription) =
    suspendCancellableCoroutine { cont ->
        setRemoteDescription(object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription?) = Unit
            override fun onCreateFailure(error: String?) = Unit
            override fun onSetSuccess() {
                if (cont.isActive) cont.resume(Unit)
            }

            override fun onSetFailure(error: String?) {
                if (cont.isActive) cont.resumeWithException(IllegalStateException(error ?: "setRemoteDescription lỗi"))
            }
        }, sdp)
    }
