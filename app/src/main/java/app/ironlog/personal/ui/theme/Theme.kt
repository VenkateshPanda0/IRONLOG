package app.ironlog.personal.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Monochrome surfaces with one saturated accent, used for progress and highlights only. */
@Immutable
data class IronColors(
    val accent: Color,
    val onAccent: Color,
    val protein: Color,
    val carbs: Color,
    val fat: Color,
    val muted: Color,
    val success: Color,
)

private val DarkIron =
    IronColors(
        accent = Color(0xFFD9FF4F),
        onAccent = Color(0xFF0B0B0B),
        protein = Color(0xFF6FA8FF),
        carbs = Color(0xFFFFC24B),
        fat = Color(0xFFFF7A6B),
        muted = Color(0xFF8C8C8C),
        success = Color(0xFF5BD68A),
    )

private val LightIron =
    IronColors(
        accent = Color(0xFF5B7A00),
        onAccent = Color(0xFFFFFFFF),
        protein = Color(0xFF2F6FD6),
        carbs = Color(0xFFC88A00),
        fat = Color(0xFFD1453A),
        muted = Color(0xFF6B6B6B),
        success = Color(0xFF1E8C4E),
    )

val LocalIronColors = staticCompositionLocalOf { DarkIron }

private val Dark =
    darkColorScheme(
        primary = Color(0xFFFFFFFF),
        onPrimary = Color(0xFF000000),
        secondary = Color(0xFFD9FF4F),
        onSecondary = Color(0xFF0B0B0B),
        tertiary = Color(0xFFD9FF4F),
        background = Color(0xFF000000),
        onBackground = Color(0xFFFFFFFF),
        surface = Color(0xFF000000),
        onSurface = Color(0xFFFFFFFF),
        surfaceVariant = Color(0xFF151515),
        onSurfaceVariant = Color(0xFF9A9A9A),
        surfaceContainer = Color(0xFF111111),
        surfaceContainerHigh = Color(0xFF1B1B1B),
        surfaceContainerHighest = Color(0xFF232323),
        surfaceContainerLow = Color(0xFF0C0C0C),
        outline = Color(0xFF2E2E2E),
        outlineVariant = Color(0xFF222222),
        error = Color(0xFFFF5A5A),
        secondaryContainer = Color(0xFF262626),
        onSecondaryContainer = Color(0xFFFFFFFF),
    )

private val Light =
    lightColorScheme(
        primary = Color(0xFF000000),
        onPrimary = Color(0xFFFFFFFF),
        secondary = Color(0xFF5B7A00),
        onSecondary = Color(0xFFFFFFFF),
        tertiary = Color(0xFF5B7A00),
        background = Color(0xFFF4F4F2),
        onBackground = Color(0xFF0B0B0B),
        surface = Color(0xFFF4F4F2),
        onSurface = Color(0xFF0B0B0B),
        surfaceVariant = Color(0xFFFFFFFF),
        onSurfaceVariant = Color(0xFF666666),
        surfaceContainer = Color(0xFFFFFFFF),
        surfaceContainerHigh = Color(0xFFEDEDEA),
        surfaceContainerHighest = Color(0xFFE4E4E0),
        surfaceContainerLow = Color(0xFFFAFAF8),
        outline = Color(0xFFD6D6D2),
        outlineVariant = Color(0xFFE4E4E0),
        secondaryContainer = Color(0xFFE4E4E0),
        onSecondaryContainer = Color(0xFF0B0B0B),
    )

private val base = Typography()

private val IronTypography =
    Typography(
        displayLarge =
            base.displayLarge.copy(fontWeight = FontWeight.Black, letterSpacing = (-1).sp),
        displayMedium =
            base.displayMedium.copy(fontWeight = FontWeight.Black, letterSpacing = (-1).sp),
        displaySmall =
            base.displaySmall.copy(fontWeight = FontWeight.Black, letterSpacing = (-0.5).sp),
        headlineLarge =
            base.headlineLarge.copy(fontWeight = FontWeight.Black, letterSpacing = (-0.5).sp),
        headlineMedium =
            base.headlineMedium.copy(fontWeight = FontWeight.Black, letterSpacing = (-0.5).sp),
        headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.Bold),
        titleSmall = base.titleSmall.copy(fontWeight = FontWeight.Bold),
        labelLarge =
            base.labelLarge.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp),
        labelMedium = base.labelMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
        labelSmall = base.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
    )

/** Small uppercase label used above cards and sections. */
val EyebrowStyle: TextStyle
    @Composable
    get() =
        MaterialTheme.typography.labelMedium.copy(
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = 1.5.sp,
        )

private val IronShapes =
    Shapes(
        extraSmall = RoundedCornerShape(6.dp),
        small = RoundedCornerShape(10.dp),
        medium = RoundedCornerShape(16.dp),
        large = RoundedCornerShape(22.dp),
        extraLarge = RoundedCornerShape(28.dp),
    )

object IronTheme {
    val colors: IronColors
        @Composable get() = LocalIronColors.current
}

@Composable
fun IronlogTheme(dark: Boolean = true, content: @Composable () -> Unit) {
    androidx.compose.runtime.CompositionLocalProvider(
        LocalIronColors provides if (dark) DarkIron else LightIron
    ) {
        MaterialTheme(
            colorScheme = if (dark) Dark else Light,
            typography = IronTypography,
            shapes = IronShapes,
            content = content,
        )
    }
}
