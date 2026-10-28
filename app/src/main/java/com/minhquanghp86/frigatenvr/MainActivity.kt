package com.minhquanghp86.frigatenvr

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import com.minhquanghp86.frigatenvr.ui.AppRoot
import com.minhquanghp86.frigatenvr.ui.FrigateTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        com.minhquanghp86.frigatenvr.notify.Notify.ensureChannels(this)
        setContent {
            FrigateTheme {
                AppRoot()
            }
        }
    }
}
