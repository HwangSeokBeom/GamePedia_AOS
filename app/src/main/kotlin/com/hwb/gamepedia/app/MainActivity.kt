package com.hwb.gamepedia.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.hwb.gamepedia.core.designsystem.theme.GamePediaTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val appContainer = (application as GamePediaApplication).appContainer
        setContent {
            GamePediaTheme {
                GamePediaNavHost(appContainer)
            }
        }
    }
}
