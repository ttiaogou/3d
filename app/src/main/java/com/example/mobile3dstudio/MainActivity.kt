package com.example.mobile3dstudio

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.example.mobile3dstudio.ui.Mobile3DStudioApp
import com.example.mobile3dstudio.ui.StudioTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            StudioTheme {
                Mobile3DStudioApp()
            }
        }
    }
}
