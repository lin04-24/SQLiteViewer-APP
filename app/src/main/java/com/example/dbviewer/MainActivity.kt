package com.example.dbviewer

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.IntentCompat
import com.example.dbviewer.ui.DbViewerApp

class MainActivity : ComponentActivity() {

    /** Kept as state so a file opened while the app is already running is picked up as well. */
    private val incomingUri = mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestHighRefreshRate()
        incomingUri.value = sharedUri(intent)
        setContent { DbViewerApp(incomingUri.value) }
    }

    /** Prefer the panel's 120 Hz mode while allowing Android to fall back when unavailable. */
    private fun requestHighRefreshRate() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.attributes = window.attributes.apply { preferredRefreshRate = 120f }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        sharedUri(intent)?.let { incomingUri.value = it }
    }

    private fun sharedUri(intent: Intent?): Uri? = intent?.data ?: intent?.let { IntentCompat.getParcelableExtra(it, Intent.EXTRA_STREAM, Uri::class.java) }
}
