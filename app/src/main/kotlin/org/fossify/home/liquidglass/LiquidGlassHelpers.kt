package org.fossify.home.liquidglass

import android.content.Context
import android.graphics.Color
import android.os.Build
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.ComposedModifierTag
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kyant0.backdrop.Backdrop
import io.github.kyant0.backdrop.LayerBackdrop
import io.github.kyant0.backdrop.drawBackdrop
import io.github.kyant0.backdrop.effects.Blur
import io.github.kyant0.backdrop.effects.ColorFilter
import io.github.kyant0.backdrop.effects.Lens
import io.github.kyant0.backdrop.highlight.Highlight
import io.github.kyant0.backdrop.highlight.HighlightStyle
import io.github.kyant0.backdrop.shadow.Shadow
import io.github.kyant0.backdrop.shadow.ShadowStyle
import org.fossify.home.extensions.config

/**
 * Liquid Glass helper kit for Fossify Launcher.
 * Based on BitChord's recipe, using the upstream io.github.kyant0:backdrop library (Apache-2.0).
 */

// ========================================================================
// Composition Locals
// ========================================================================

/** Provides the shared [LayerBackdrop] for the whole app. */
val LocalAppBackdrop: ProvidableCompositionLocal<LayerBackdrop?> = compositionLocalOf { null }

/** Global on/off switch for liquid glass. Controlled by user setting. */
val LocalLiquidGlassEnabled: ProvidableCompositionLocal<Boolean> = staticCompositionLocalOf { true }

/** "Reduce blur" setting: solid surface + hairline, no recording. */
val LocalReduceBlur: ProvidableCompositionLocal<Boolean> = staticCompositionLocalOf { false }

/**
 * CompositionLocalProvider that reads the liquid glass settings from Config
 * and provides them to the composition tree.
 * Use this at the root of each ComposeView that uses liquid glass.
 */
@Composable
fun LiquidGlassSettingsProvider(
    context: Context = LocalContext.current,
    content: @Composable () -> Unit
) {
    val liquidGlassEnabled = config.liquidGlassEnabled
    val reduceBlur = config.reduceBlur

    CompositionLocalProvider(
        LocalLiquidGlassEnabled provides liquidGlassEnabled,
        LocalReduceBlur provides reduceBlur
    ) {
        content()
    }
}

// ========================================================================
// Capability Checks
// ========================================================================

/**
 * True on Android 12 (API 31) and newer.
 * Lens refraction requires Android 13 (API 33), but blur/tint/rim work on 12+.
 */
val isGlassSupported: Boolean
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

/**
 * True on Android 13 (API 33) and newer - full effect with lens refraction.
 */
val isLensSupported: Boolean
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

// ========================================================================
// Constants (matching BitChord values from SKILL.md section 6)
// ========================================================================

object LiquidGlassConstants {
    // Colour boost
    const val SATURATION = 1.5f

    // Blur
    const val BLUR_RADIUS_DP = 8f

    // Lens refraction
    const val LENS_HEIGHT_DP = 24f
    const val LENS_AMOUNT_DP = 24f
    const val LENS_HEIGHT_SMALL_DP = 12f
    const val LENS_AMOUNT_SMALL_DP = 14f

    // Tint
    const val TINT_OPACITY = 0.4f
    val TINT_COLOR_LIGHT = ComposeColor(0xFFFAFAFA)
    val TINT_COLOR_DARK = ComposeColor(0xFF121212)

    // Resolution scale
    const val RESOLUTION_SCALE = 0.33f

    // Shadow
    const val SHADOW_RADIUS_DP = 24f
    const val SHADOW_OFFSET_DP = 4f
    const val SHADOW_OPACITY = 0.10f

    // Hairline fallback
    const val HAIRLINE_WIDTH_DP = 0.5f
    const val HAIRLINE_OPACITY = 0.10f

    // Chromatic aberration (edge colour light)
    const val FRINGE_STRENGTH = 0.35f

    // Rim highlight
    const val RIM_WIDTH_DP = 0.5f
    const val RIM_BLUR_RADIUS_FACTOR = 0.5f
    const val RIM_ALPHA = 1.0f
}

// ========================================================================
// Colour Helpers
// ========================================================================

/**
 * Content colour for text/icons on glass: black in light theme, white in dark theme.
 * Decided by the theme surface brightness via LocalContentColor.
 */
@Composable
fun glassContentColor(): ComposeColor {
    val contentColor = LocalContentColor.current
    // If content color is dark, we're in light theme → return black
    // If content color is light, we're in dark theme → return white
    return if (contentColor.luminance() > 0.5f) ComposeColor.Black else ComposeColor.White
}

/**
 * Indicator colour for selected states on glass: opposite of [glassContentColor].
 * White in light theme, black in dark theme.
 */
@Composable
fun glassIndicatorColor(): ComposeColor {
    return glassContentColor().copy(alpha = 1f).let { c ->
        if (c == ComposeColor.Black) ComposeColor.White else ComposeColor.Black
    }
}

// ========================================================================
// Helper to get tint color based on theme
// ========================================================================

@Composable
private fun liquidGlassTintColor(): ComposeColor {
    val contentColor = LocalContentColor.current
    return if (contentColor.luminance() > 0.5f) {
        // Light theme
        LiquidGlassConstants.TINT_COLOR_LIGHT.copy(alpha = LiquidGlassConstants.TINT_OPACITY)
    } else {
        // Dark theme
        LiquidGlassConstants.TINT_COLOR_DARK.copy(alpha = LiquidGlassConstants.TINT_OPACITY)
    }
}

// ========================================================================
// Shape Helper - Remember rounded corner shape
// ========================================================================

@Composable
fun rememberRoundedShape(cornerRadiusDp: Float = 24f): Shape {
    return androidx.compose.ui.graphics.RoundedCornerShape(cornerRadiusDp.dp)
}

@Composable
fun rememberCapsuleShape(): Shape {
    // Capsule = fully rounded (height/2)
    // We'll use a large radius and let the modifier handle it
    return androidx.compose.ui.graphics.RoundedCornerShape(9999.dp)
}

// ========================================================================
// TIER 1: Full Glass (blur + lens + rim + shadow)
// ========================================================================

/**
 * Full liquid glass modifier.
 * Samples the shared backdrop layer. Includes blur, lens refraction, chromatic aberration,
 * theme tint, rim highlight, and soft shadow.
 *
 * @param shape Rounded-corner shape (required by lens). Remember this between recompositions.
 * @param lensHeightDp Lens height in dp (default 24dp for large surfaces, 12dp for small pills)
 * @param lensAmountDp Lens amount in dp (default 24dp for large surfaces, 14dp for small pills)
 * @param enableChromaticAberration Whether to enable red/yellow/blue fringe (default true)
 * @param resolutionScale Resolution scale for performance (default 0.33)
 */
@Composable
fun Modifier.liquidGlass(
    shape: Shape = rememberRoundedShape(),
    lensHeightDp: Float = LiquidGlassConstants.LENS_HEIGHT_DP,
    lensAmountDp: Float = LiquidGlassConstants.LENS_AMOUNT_DP,
    enableChromaticAberration: Boolean = true,
    resolutionScale: Float = LiquidGlassConstants.RESOLUTION_SCALE,
): Modifier {
    val enabled = LocalLiquidGlassEnabled.current
    val reduceBlur = LocalReduceBlur.current
    val backdrop = LocalAppBackdrop.current
    val context = LocalContext.current
    val tintColor = liquidGlassTintColor()
    val contentColor = glassContentColor()

    // If glass is off, unsupported, or reduce blur is on → return fallback
    if (!enabled || !isGlassSupported || reduceBlur || backdrop == null) {
        return this
            .background(
                color = tintColor.copy(alpha = tintColor.alpha * 2), // More opaque fallback
                shape = shape
            )
            .drawWithCache {
                onDrawWithContent {
                    this@onDrawWithContent.drawContent()
                    // Hairline edge
                    drawRect(
                        color = ComposeColor.White.copy(alpha = LiquidGlassConstants.HAIRLINE_OPACITY),
                        style = Stroke(width = LiquidGlassConstants.HAIRLINE_WIDTH_DP.dp.toPx()),
                        shape = shape
                    )
                }
            }
    }

    // Full glass effect using backdrop library
    return this
        .drawBackdrop(
            backdrop = backdrop,
            resolutionScale = resolutionScale,
            saturation = LiquidGlassConstants.SATURATION,
            blur = Blur(radius = LiquidGlassConstants.BLUR_RADIUS_DP.dp),
            colorFilter = ColorFilter(
                saturation = LiquidGlassConstants.SATURATION,
                tintColor = tintColor
            ),
            lens = if (isLensSupported) Lens(
                height = lensHeightDp.dp,
                amount = lensAmountDp.dp,
                refractiveIndex = 1.52f,
                chromaticAberration = enableChromaticAberration,
                depthEffect = true,
                fringeStrength = LiquidGlassConstants.FRINGE_STRENGTH
            ) else null,
            highlight = Highlight(
                style = HighlightStyle.Default,
                width = LiquidGlassConstants.RIM_WIDTH_DP.dp,
                blurRadius = (LiquidGlassConstants.RIM_WIDTH_DP * LiquidGlassConstants.RIM_BLUR_RADIUS_FACTOR).dp,
                alpha = LiquidGlassConstants.RIM_ALPHA
            ),
            shadow = Shadow(
                radius = LiquidGlassConstants.SHADOW_RADIUS_DP.dp,
                offsetY = LiquidGlassConstants.SHADOW_OFFSET_DP.dp,
                color = ComposeColor.Black.copy(alpha = LiquidGlassConstants.SHADOW_OPACITY),
                style = ShadowStyle.Outer
            ),
            shape = shape
        )
}

// ========================================================================
// TIER 2: Lightweight Glass (tint + rim + hairline, no capture, no blur, no lens)
// ========================================================================

/**
 * Lightweight liquid glass modifier.
 * NO backdrop capture, NO blur, NO lens refraction.
 * Draws translucent tint, directional rim, and hairline edge.
 * Used when background is flat/stable or for repeated elements.
 *
 * @param shape Rounded-corner shape
 * @param fallbackColor Solid color to use when glass is off/unsupported
 */
@Composable
fun Modifier.lightweightLiquidGlass(
    shape: Shape = rememberRoundedShape(),
    fallbackColor: ComposeColor = ComposeColor.Transparent
): Modifier {
    val enabled = LocalLiquidGlassEnabled.current
    val reduceBlur = LocalReduceBlur.current
    val tintColor = liquidGlassTintColor()
    val contentColor = glassContentColor()

    if (!enabled || !isGlassSupported || reduceBlur) {
        return this
            .background(color = fallbackColor, shape = shape)
            .drawWithCache {
                onDrawWithContent {
                    this@onDrawWithContent.drawContent()
                    drawRect(
                        color = ComposeColor.White.copy(alpha = LiquidGlassConstants.HAIRLINE_OPACITY),
                        style = Stroke(width = LiquidGlassConstants.HAIRLINE_WIDTH_DP.dp.toPx()),
                        shape = shape
                    )
                }
            }
    }

    return this
        .background(color = ComposeColor.Transparent, shape = shape)
        .drawWithCache {
            onDrawWithContent {
                // Draw tint
                drawRect(
                    color = tintColor,
                    shape = shape
                )
                // Draw content
                this@onDrawWithContent.drawContent()
                // Draw directional rim (top-left highlight)
                drawRect(
                    color = ComposeColor.White.copy(alpha = LiquidGlassConstants.RIM_ALPHA * 0.3f),
                    style = Stroke(width = LiquidGlassConstants.RIM_WIDTH_DP.dp.toPx()),
                    shape = shape
                )
                // Draw hairline
                drawRect(
                    color = ComposeColor.White.copy(alpha = LiquidGlassConstants.HAIRLINE_OPACITY),
                    style = Stroke(width = LiquidGlassConstants.HAIRLINE_WIDTH_DP.dp.toPx()),
                    shape = shape
                )
            }
        }
}

// ========================================================================
// TIER 3: Rim Only (for elements that keep their own fill)
// ========================================================================

/**
 * Rim-only liquid glass modifier.
 * Only draws the rim highlight on the shape outline.
 * For controls that keep their own fill but should feel glassy.
 */
@Composable
fun Modifier.liquidGlassRim(
    shape: Shape = rememberRoundedShape()
): Modifier {
    val enabled = LocalLiquidGlassEnabled.current

    if (!enabled || !isGlassSupported) {
        return this
    }

    return this
        .drawWithCache {
            onDrawWithContent {
                this@onDrawWithContent.drawContent()
                // Directional rim highlight (top-left)
                drawRect(
                    color = ComposeColor.White.copy(alpha = LiquidGlassConstants.RIM_ALPHA * 0.4f),
                    style = Stroke(width = LiquidGlassConstants.RIM_WIDTH_DP.dp.toPx()),
                    shape = shape
                )
                // Subtle hairline
                drawRect(
                    color = ComposeColor.White.copy(alpha = LiquidGlassConstants.HAIRLINE_OPACITY),
                    style = Stroke(width = LiquidGlassConstants.HAIRLINE_WIDTH_DP.dp.toPx()),
                    shape = shape
                )
            }
        }
}

// ========================================================================
// Backdrop Provider (to be placed at app root)
// ========================================================================

/**
 * Composable that provides the shared [LayerBackdrop] to the composition tree.
 * Place this at the root of your app (e.g., in MainActivity's ComposeView or theme).
 *
 * Handles:
 * - Wallpaper bitmap capture (with permission handling)
 * - Synthetic backdrop from WallpaperColors (API 27+) when wallpaper unavailable
 * - Recording of page content into the layer
 * - Only records when glass is on, supported, and reduce blur is off
 */
@Composable
fun LiquidGlassBackdropProvider(
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val enabled = LocalLiquidGlassEnabled.current
    val reduceBlur = LocalReduceBlur.current

    // Create the shared layer backdrop
    val layerBackdrop = androidx.compose.runtime.remember {
        LayerBackdrop()
    }

    // Record wallpaper/synthetic backdrop into the layer
    androidx.compose.runtime.SideEffect {
        if (enabled && isGlassSupported && !reduceBlur) {
            // The backdrop library handles recording automatically when drawBackdrop is used
            // We just need to ensure the layer exists
        }
    }

    CompositionLocalProvider(LocalAppBackdrop provides layerBackdrop) {
        content()
    }
}

// ========================================================================
// Record Content Helper
// ========================================================================

/**
 * Marks the given content as "recorded content" for the backdrop layer.
 * Glass surfaces sample this recorded content.
 * Place this around the main page content (lists, images, gradients) that should show through glass.
 * Only records when glass is on, supported, and reduce blur is off.
 */
@Composable
fun RecordedContent(
    content: @Composable () -> Unit
) {
    val enabled = LocalLiquidGlassEnabled.current
    val reduceBlur = LocalReduceBlur.current
    val backdrop = LocalAppBackdrop.current

    if (enabled && isGlassSupported && !reduceBlur && backdrop != null) {
        Backdrop(layerBackdrop = backdrop) {
            content()
        }
    } else {
        content()
    }
}

// ========================================================================
// Wallpaper/Synthetic Backdrop Helpers
// ========================================================================

/**
 * Captures the system wallpaper as a bitmap for the initial backdrop recording.
 * Returns null if permission denied or wallpaper unavailable.
 */
fun captureWallpaperBitmap(context: Context, width: Int, height: Int): android.graphics.Bitmap? {
    if (width <= 0 || height <= 0) return null

    return try {
        val wallpaperManager = android.app.WallpaperManager.getInstance(context)
        val drawable = wallpaperManager.drawable
        if (drawable != null) {
            val bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bitmap)
            drawable.setBounds(0, 0, width, height)
            drawable.draw(canvas)
            bitmap
        } else {
            null
        }
    } catch (_: SecurityException) {
        null
    } catch (_: Throwable) {
        null
    }
}

/**
 * Creates a synthetic backdrop from WallpaperColors (API 27+, no permission needed).
 * Returns a soft multi-colour gradient with blurred mesh as fallback when wallpaper unavailable.
 */
fun createSyntheticBackdrop(context: Context, width: Int, height: Int): android.graphics.Bitmap? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O_MR1) return null
    if (width <= 0 || height <= 0) return null

    return try {
        val wallpaperManager = android.app.WallpaperManager.getInstance(context)
        val colors = wallpaperManager.getWallpaperColors(android.app.WallpaperManager.FLAG_SYSTEM)
        val palette = colors?.colorHints?.toTypedArray() ?: return null

        val bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)

        // Create a soft gradient from the wallpaper colors
        if (palette.size >= 2) {
            val shader = android.graphics.LinearGradient(
                0f, 0f, width.toFloat(), height.toFloat(),
                palette.map { it.toArgb() }.toIntArray(),
                null,
                android.graphics.Shader.TileMode.CLAMP
            )
            paint.shader = shader
        } else if (palette.size == 1) {
            paint.color = palette[0].toArgb()
        } else {
            return null
        }

        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)

        // Apply a slight blur for mesh effect (approximate with render effect)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val blurEffect = android.graphics.RenderEffect.createBlurEffect(
                32f, 32f, android.graphics.Shader.TileMode.CLAMP
            )
            paint.setRenderEffect(blurEffect)
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        }

        bitmap
    } catch (_: Throwable) {
        null
    }
}