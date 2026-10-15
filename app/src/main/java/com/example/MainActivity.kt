package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.ui.CloudOrbScreen
import com.example.ui.CloudOrbViewModel
import com.example.ui.theme.CloudOrbTheme
import com.example.ui.theme.PureWhite

class MainActivity : ComponentActivity() {

    private val viewModel: CloudOrbViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CloudOrbTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = PureWhite
                ) {
                    CloudOrbScreen(viewModel = viewModel)
                }
            }
        }
    }
}
