package com.pocketagent.mobile.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import dagger.hilt.android.AndroidEntryPoint

/**
 * Main activity for the Pocket Agent application.
 * 
 * This activity serves as the entry point for the user interface and hosts
 * the main navigation and screens.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            PocketAgentTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // This will be replaced with proper navigation
                    PlaceholderContent()
                }
            }
        }
    }
}

@Composable
fun PlaceholderContent() {
    Text(
        text = "Pocket Agent - Coming Soon",
        style = MaterialTheme.typography.headlineMedium
    )
}

@Composable
fun PocketAgentTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        content = content
    )
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    PocketAgentTheme {
        PlaceholderContent()
    }
}