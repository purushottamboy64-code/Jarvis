package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.arushi.ui.ArushiScreen
import com.example.arushi.ui.ArushiViewModel
import com.example.ui.theme.ArushiTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Configure Firebase App Check with launch intent before any UI or Firebase calls.
        configureAppCheck(this, intent)

        enableEdgeToEdge()
        setContent {
            ArushiTheme {
                val arushiViewModel: ArushiViewModel = viewModel()
                ArushiScreen(viewModel = arushiViewModel)
            }
        }
    }
}
