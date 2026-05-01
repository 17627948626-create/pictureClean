package com.yihua.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.yihua.app.ui.navigation.AppNavigation
import com.yihua.app.ui.theme.YiHuaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            YiHuaTheme {
                AppNavigation()
            }
        }
    }
}
