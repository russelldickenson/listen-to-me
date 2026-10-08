package com.listentome.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.listentome.app.ui.navigation.ListenToMeNavGraph
import com.listentome.app.ui.theme.ListenToMeTheme

class MainActivity : ComponentActivity() {
    private var openPlayerRequests by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handleIntent(intent)
        setContent {
            ListenToMeTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ListenToMeNavGraph(application, openPlayerRequests)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_OPEN_PLAYER, false) == true) {
            intent.removeExtra(EXTRA_OPEN_PLAYER)
            openPlayerRequests++
        }
    }

    companion object {
        const val EXTRA_OPEN_PLAYER = "open_player"
    }
}
