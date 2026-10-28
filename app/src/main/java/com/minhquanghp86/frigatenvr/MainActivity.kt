package com.minhquanghp86.frigatenvr

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import com.minhquanghp86.frigatenvr.notify.DeepLinks
import com.minhquanghp86.frigatenvr.notify.Notify
import com.minhquanghp86.frigatenvr.ui.AppRoot
import com.minhquanghp86.frigatenvr.ui.FrigateTheme

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

    private fun handleIntent(intent: Intent?) {
        val eventId = intent?.getStringExtra(EXTRA_EVENT_ID) ?: return
        DeepLinks.openEvent(eventId)
        intent.removeExtra(EXTRA_EVENT_ID)
    }

    companion object {
        const val EXTRA_EVENT_ID = "frigate_event_id"
    }
}
