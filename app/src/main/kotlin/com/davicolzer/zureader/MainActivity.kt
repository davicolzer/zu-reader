package com.davicolzer.zureader

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.davicolzer.zureader.ui.home.HomeScreen
import com.davicolzer.zureader.ui.theme.ZuReaderTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ZuReaderTheme {
                HomeScreen()
            }
        }
    }
}
