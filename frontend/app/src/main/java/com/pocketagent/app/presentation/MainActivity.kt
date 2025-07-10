package com.pocketagent.app.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import dagger.hilt.android.AndroidEntryPoint

/**
 * Main activity for the Pocket Agent application
 * 
 * This activity serves as the entry point for the UI and is responsible for:
 * - Setting up the main compose content
 * - Handling system UI insets
 * - Managing activity lifecycle
 * - Coordinating with ViewModels through Hilt
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Enable edge-to-edge display
        enableEdgeToEdge()
        
        setContent {
            PocketAgentTheme {
                // Main app content
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Surface(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        // TODO: Replace with actual navigation and app content
                        PlaceholderContent()
                    }
                }
            }
        }
    }
}

@Composable
fun PocketAgentTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        content = content
    )
}

@Composable
fun PlaceholderContent() {
    Text(
        text = "Pocket Agent - Build system configured!",
        style = MaterialTheme.typography.headlineMedium
    )
}

@Preview(showBackground = true)
@Composable
fun PlaceholderContentPreview() {
    PocketAgentTheme {
        PlaceholderContent()
    }
}