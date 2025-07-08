# UI Navigation & Foundation Feature Specification - Base UI Components
**For Android Mobile Application**

> **Navigation**: [Overview](./ui-navigation-overview.feat.md) | [Navigation](./ui-navigation-navigation.feat.md) | [Theme](./ui-navigation-theme.feat.md) | **Components** | [Scaffolding](./ui-navigation-scaffolding.feat.md) | [State](./ui-navigation-state.feat.md) | [Testing](./ui-navigation-testing.feat.md) | [Implementation](./ui-navigation-implementation.feat.md) | [Index](./ui-navigation-index.md)

## Base UI Components

**Purpose**: Provides a library of reusable UI components that maintain consistent styling and behavior throughout the app. These components serve as building blocks for feature-specific screens and ensure design consistency.

```kotlin
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.time.Instant

// Primary action button with loading state
@Composable
fun PrimaryActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    icon: ImageVector? = null
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        enabled = enabled && !loading,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary
        )
    ) {
        AnimatedContent(
            targetState = loading,
            transitionSpec = {
                fadeIn() togetherWith fadeOut()
            }
        ) { isLoading ->
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp
                )
            } else {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    icon?.let {
                        Icon(
                            imageVector = it,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(text)
                }
            }
        }
    }
}

// Status indicator chip
@Composable
fun StatusChip(
    status: ConnectionStatus,
    modifier: Modifier = Modifier
) {
    val (backgroundColor, textColor, text) = when (status) {
        ConnectionStatus.CONNECTED -> Triple(
            MaterialTheme.successColor().copy(alpha = 0.12f),
            MaterialTheme.successColor(),
            "Connected"
        )
        ConnectionStatus.CONNECTING -> Triple(
            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
            MaterialTheme.colorScheme.primary,
            "Connecting"
        )
        ConnectionStatus.DISCONNECTED -> Triple(
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant,
            "Disconnected"
        )
        ConnectionStatus.ERROR -> Triple(
            MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
            MaterialTheme.colorScheme.error,
            "Error"
        )
    }
    
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = backgroundColor
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(textColor)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = textColor
            )
        }
    }
}

// Project card for list view
@Composable
fun ProjectCard(
    project: Project,
    connectionStatus: ConnectionStatus,
    onClick: () -> Unit,
    onQuickAction: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = project.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = project.serverProfile.name,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = project.projectPath,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                StatusChip(status = connectionStatus)
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Last active: ${project.lastActive.toRelativeTimeString()}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                IconButton(onClick = onQuickAction) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = "Quick Connect",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

// Loading overlay
@Composable
fun LoadingOverlay(
    visible: Boolean,
    text: String = "Loading...",
    onDismiss: (() -> Unit)? = null
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        Dialog(
            onDismissRequest = { onDismiss?.invoke() },
            properties = DialogProperties(
                dismissOnBackPress = onDismiss != null,
                dismissOnClickOutside = onDismiss != null
            )
        ) {
            Card(
                modifier = Modifier
                    .wrapContentSize()
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(48.dp),
                        strokeWidth = 4.dp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }
    }
}

// Empty state component
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    description: String,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        if (actionText != null && onAction != null) {
            Spacer(modifier = Modifier.height(24.dp))
            Button(onClick = onAction) {
                Text(actionText)
            }
        }
    }
}

// Error banner
@Composable
fun ErrorBanner(
    error: String,
    onDismiss: () -> Unit,
    onRetry: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.error,
        contentColor = MaterialTheme.colorScheme.onError
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Error,
                    contentDescription = "Error",
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row {
                onRetry?.let {
                    TextButton(
                        onClick = it,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.onError
                        )
                    ) {
                        Text("Retry")
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Dismiss"
                    )
                }
            }
        }
    }
}
```

## Accessibility Components

**Purpose**: Enhanced accessibility support including screen reader announcements, focus management, and keyboard navigation helpers to ensure the app is fully accessible to all users.

```kotlin
import android.content.Context
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp

// Screen reader announcement helper
@Composable
fun ScreenReaderAnnouncement(
    message: String,
    polite: Boolean = true
) {
    val context = LocalContext.current
    
    LaunchedEffect(message) {
        context.announceForAccessibility(message, polite)
    }
}

fun Context.announceForAccessibility(
    message: String,
    polite: Boolean = true
) {
    val accessibilityManager = getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
    if (accessibilityManager.isEnabled) {
        val event = AccessibilityEvent.obtain().apply {
            eventType = if (polite) {
                AccessibilityEvent.TYPE_ANNOUNCEMENT
            } else {
                AccessibilityEvent.TYPE_VIEW_FOCUSED
            }
            className = Context::class.java.name
            packageName = packageName
            text.add(message)
        }
        accessibilityManager.sendAccessibilityEvent(event)
    }
}

// Focus management for keyboard navigation
@Composable
fun FocusableGroup(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }
    
    Box(
        modifier = modifier
            .focusRequester(focusRequester)
            .focusable()
            .semantics {
                role = Role.RadioButton
                focused = true
            }
    ) {
        content()
    }
}

// Keyboard navigation helper
@Composable
fun KeyboardNavigationHandler(
    onEscape: () -> Unit = {},
    onEnter: () -> Unit = {},
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier.onKeyEvent { keyEvent ->
            when (keyEvent.nativeKeyEvent.keyCode) {
                android.view.KeyEvent.KEYCODE_ESCAPE -> {
                    onEscape()
                    true
                }
                android.view.KeyEvent.KEYCODE_ENTER -> {
                    onEnter()
                    true
                }
                else -> false
            }
        }
    ) {
        content()
    }
}

// Accessibility-enhanced list item
@Composable
fun AccessibleListItem(
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingContent: @Composable (() -> Unit)? = null,
    trailingContent: @Composable (() -> Unit)? = null
) {
    Surface(
        onClick = onClick,
        modifier = modifier.semantics {
            contentDescription = buildString {
                append(title)
                subtitle?.let { append(", $it") }
            }
            role = Role.Button
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            leadingContent?.invoke()
            
            Column(
                modifier = Modifier.weight(1f).padding(horizontal = 16.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge
                )
                subtitle?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            trailingContent?.invoke()
        }
    }
}

// Live region for dynamic content updates
@Composable
fun LiveRegion(
    text: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.semantics {
            liveRegion = LiveRegionMode.Polite
            contentDescription = text
        }
    ) {
        Text(text = text)
    }
}

// Skip to content button for screen readers
@Composable
fun SkipToContentButton(
    onSkip: () -> Unit
) {
    TextButton(
        onClick = onSkip,
        modifier = Modifier
            .semantics {
                contentDescription = "Skip to main content"
            }
            .focusable()
    ) {
        Text("Skip to content")
    }
}
```