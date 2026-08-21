package com.listentome.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.listentome.app.ui.navigation.ListenToMeNavGraph
import com.listentome.app.ui.theme.ListenToMeTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ListenToMeTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ListenToMeNavGraph(application)
                }
            }
        }
    }
}
