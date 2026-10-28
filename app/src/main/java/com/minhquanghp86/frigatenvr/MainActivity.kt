package com.minhquanghp86.frigatenvr

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import com.minhquanghp86.frigatenvr.notify.DeepLinks
import com.minhquanghp86.frigatenvr.notify.Notify
import com.minhquanghp86.frigatenvr.ui.AppRoot
import com.minhquanghp86.frigatenvr.ui.FrigateTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Trạng thái Picture-in-Picture để UI ẩn thanh công cụ khi cửa sổ thu nhỏ. */
object PipState {
    private val _visible = MutableStateFlow(false)
    val visible: StateFlow<Boolean> = _visible

    fun set(value: Boolean) {
        _visible.value = value
    }
}

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        Notify.ensureChannels(this)
        handleIntent(intent)
        setContent {
            FrigateTheme {
                AppRoot()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration,
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        PipState.set(isInPictureInPictureMode)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        val camera = intent.getStringExtra(EXTRA_CAMERA)
        if (camera != null) {
            DeepLinks.openCamera(camera)
            intent.removeExtra(EXTRA_CAMERA)
            intent.removeExtra(EXTRA_EVENT_ID)
            return
        }
        val eventId = intent.getStringExtra(EXTRA_EVENT_ID) ?: return
        DeepLinks.openEvent(eventId)
        intent.removeExtra(EXTRA_EVENT_ID)
    }

    companion object {
        const val EXTRA_EVENT_ID = "frigate_event_id"
        const val EXTRA_CAMERA = "frigate_camera"
    }
}
