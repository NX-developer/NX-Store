package com.nxteam.nxstore

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.nxteam.nxstore.ui.NxStoreApp
import com.nxteam.nxstore.ui.theme.NxStoreTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            NxStoreTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    NxStoreApp()
                }
            }
        }
    }
}
