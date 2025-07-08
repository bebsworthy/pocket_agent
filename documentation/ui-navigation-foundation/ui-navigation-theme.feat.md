# UI Navigation & Foundation Feature Specification - Theme System
**For Android Mobile Application**

> **Navigation**: [Overview](./ui-navigation-overview.feat.md) | [Navigation](./ui-navigation-navigation.feat.md) | **Theme** | [Components](./ui-navigation-components.feat.md) | [Scaffolding](./ui-navigation-scaffolding.feat.md) | [State](./ui-navigation-state.feat.md) | [Testing](./ui-navigation-testing.feat.md) | [Implementation](./ui-navigation-implementation.feat.md) | [Index](./ui-navigation-index.md)

## Theme System

**Purpose**: Implements Material Design 3 theme system with dynamic color support, dark/light mode switching, and custom color schemes. Provides consistent visual styling across the app with proper elevation, shapes, and typography.

```kotlin
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Custom colors for Pocket Agent branding
object PocketAgentColors {
    val PrimaryGreenLight = Color(0xFF00C853)
    val PrimaryGreenDark = Color(0xFF00E676)
    val ErrorRed = Color(0xFFD32F2F)
    val SuccessGreen = Color(0xFF4CAF50)
    val WarningOrange = Color(0xFFFF6D00)
    val SurfaceLight = Color(0xFFF5F5F5)
    val SurfaceDark = Color(0xFF121212)
}

// Custom typography
val PocketAgentFontFamily = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold)
)

val PocketAgentTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = PocketAgentFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 57.sp,
        lineHeight = 64.sp,
        letterSpacing = (-0.25).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = PocketAgentFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 36.sp
    ),
    titleLarge = TextStyle(
        fontFamily = PocketAgentFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 22.sp,
        lineHeight = 28.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = PocketAgentFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp
    ),
    labelLarge = TextStyle(
        fontFamily = PocketAgentFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    )
)

// Theme configuration
data class ThemeConfig(
    val useDynamicColor: Boolean = true,
    val themeMode: ThemeMode = ThemeMode.SYSTEM
)

enum class ThemeMode {
    LIGHT, DARK, SYSTEM
}

// Theme state management
@Composable
fun rememberThemeConfig(): MutableState<ThemeConfig> {
    return remember { mutableStateOf(ThemeConfig()) }
}

@Composable
fun PocketAgentTheme(
    themeConfig: ThemeConfig = ThemeConfig(),
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val darkTheme = when (themeConfig.themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    
    val colorScheme = when {
        themeConfig.useDynamicColor && android.os.Build.VERSION.SDK_INT >= 31 -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> darkColorScheme(
            primary = PocketAgentColors.PrimaryGreenDark,
            onPrimary = Color.Black,
            primaryContainer = PocketAgentColors.PrimaryGreenDark.copy(alpha = 0.12f),
            secondary = Color(0xFF03DAC6),
            surface = PocketAgentColors.SurfaceDark,
            background = Color.Black,
            error = Color(0xFFFF5252),
            onSurface = Color.White,
            onBackground = Color.White
        )
        else -> lightColorScheme(
            primary = PocketAgentColors.PrimaryGreenLight,
            onPrimary = Color.White,
            primaryContainer = PocketAgentColors.PrimaryGreenLight.copy(alpha = 0.12f),
            secondary = Color(0xFF018786),
            surface = PocketAgentColors.SurfaceLight,
            background = Color.White,
            error = PocketAgentColors.ErrorRed,
            onSurface = Color.Black,
            onBackground = Color.Black
        )
    }
    
    MaterialTheme(
        colorScheme = colorScheme,
        typography = PocketAgentTypography,
        shapes = Shapes(
            small = RoundedCornerShape(4.dp),
            medium = RoundedCornerShape(8.dp),
            large = RoundedCornerShape(16.dp)
        ),
        content = content
    )
}

// Extension functions for semantic colors
@Composable
fun MaterialTheme.successColor(): Color {
    return if (isSystemInDarkTheme()) Color(0xFF4CAF50) else Color(0xFF2E7D32)
}

@Composable
fun MaterialTheme.warningColor(): Color {
    return if (isSystemInDarkTheme()) Color(0xFFFF6D00) else Color(0xFFE65100)
}

@Composable
fun MaterialTheme.codeBackgroundColor(): Color {
    return if (isSystemInDarkTheme()) Color(0xFF1E1E1E) else Color(0xFFF5F5F5)
}
```

## Theme Persistence

**Purpose**: DataStore implementation for persisting theme preferences across app restarts, ensuring user's theme choice is maintained.

```kotlin
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "theme_preferences")

@Singleton
class ThemePreferencesDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object PreferencesKeys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val USE_DYNAMIC_COLOR = booleanPreferencesKey("use_dynamic_color")
    }
    
    val themeConfigFlow: Flow<ThemeConfig> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            ThemeConfig(
                themeMode = ThemeMode.valueOf(
                    preferences[PreferencesKeys.THEME_MODE] ?: ThemeMode.SYSTEM.name
                ),
                useDynamicColor = preferences[PreferencesKeys.USE_DYNAMIC_COLOR] ?: true
            )
        }
    
    suspend fun updateThemeMode(mode: ThemeMode) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.THEME_MODE] = mode.name
        }
    }
    
    suspend fun updateDynamicColorPreference(useDynamicColor: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.USE_DYNAMIC_COLOR] = useDynamicColor
        }
    }
}

// Updated PreferencesManager interface
interface PreferencesManager {
    val themeMode: ThemeMode
    val useDynamicColors: Boolean
    suspend fun setThemeMode(mode: ThemeMode)
    suspend fun setUseDynamicColors(enabled: Boolean)
}

@Singleton
class PreferencesManagerImpl @Inject constructor(
    private val themePreferencesDataStore: ThemePreferencesDataStore
) : PreferencesManager {
    
    private var cachedThemeMode: ThemeMode = ThemeMode.SYSTEM
    private var cachedUseDynamicColors: Boolean = true
    
    init {
        // Initialize cached values from DataStore
        // This would be done in a coroutine scope in actual implementation
    }
    
    override val themeMode: ThemeMode
        get() = cachedThemeMode
    
    override val useDynamicColors: Boolean
        get() = cachedUseDynamicColors
    
    override suspend fun setThemeMode(mode: ThemeMode) {
        cachedThemeMode = mode
        themePreferencesDataStore.updateThemeMode(mode)
    }
    
    override suspend fun setUseDynamicColors(enabled: Boolean) {
        cachedUseDynamicColors = enabled
        themePreferencesDataStore.updateDynamicColorPreference(enabled)
    }
}
```