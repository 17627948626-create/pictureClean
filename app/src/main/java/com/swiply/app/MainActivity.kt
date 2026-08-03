package com.swiply.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.swiply.app.ui.navigation.AppNavigation
import com.swiply.app.ui.theme.SwiplyTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            SwiplyTheme {
                AppNavigation()
            }
        }
    }
}
