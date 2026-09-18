package com.tekmoon.kompasskmp

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.tekmoon.kompass.DeepLinkChannel

class MainActivity : ComponentActivity() {

    /**
     * Carries a deep link that arrives while the Activity is already alive.
     *
     * A cold start has no composition yet, so its URI goes straight into [App] instead. A warm start
     * reaches [onNewIntent], where the composition is running and a channel is the only way in.
     */
    private val deepLinkChannel = DeepLinkChannel()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            App(
                deepLinkUri = intent?.dataString,
                deepLinkChannel = deepLinkChannel,
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // setIntent so a later read of this.intent sees the new URI and not the launching one.
        setIntent(intent)
        intent.dataString?.let(deepLinkChannel::send)
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}
