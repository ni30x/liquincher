package org.fossify.home.liquidglass

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ComposedModifierTag
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.AndroidCanvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RoundedCornerShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.fossify.home.liquidglass.LiquidGlassHelpers.LocalLiquidGlassEnabled
import org.fossify.home.liquidglass.LiquidGlassHelpers.LocalReduceBlur
import org.fossify.home.liquidglass.LiquidGlassHelpers.isGlassSupported

/**
 * Compose modifiers for liquid glass using the existing AGSL shader with bitmap backdrop.
 * This works with the current View-based architecture where the backdrop is a bitmap
 * captured from the wallpaper and home screen.
 */

// ========================================================================
// Constants matching the spec (SKILL.md section 6)
// ========================================================================

object LiquidGlassComposeConstants {
    // Colour boost
    const val SATURATION = 1.5f

    // Blur radius
    const val BLUR_RADIUS_DP = 8f

    // Lens refraction
    const val LENS_HEIGHT_DP = 24f
    const val LENS_AMOUNT_DP = 24f
    const val LENS_HEIGHT_SMALL_DP = 12f
    const val LENS_AMOUNT_SMALL_DP = 14f

    // Tint
    const val TINT_OPACITY = 0.4f
    val TINT_COLOR_LIGHT = Color(0xFFFAFAFA)
    val TINT_COLOR_DARK = Color(0xFF121212)

    // Resolution scale (for performance)
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
// Cached RuntimeShader (reused across frames)
// ========================================================================

private var cachedRuntimeShader: RuntimeShader? = null

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun getRuntimeShader(): RuntimeShader {
    var shader = cachedRuntimeShader
    if (shader == null) {
        shader = RuntimeShader(LiquidGlassShader.AGSL_SOURCE)
        cachedRuntimeShader = shader
    }
    return shader
}

// ========================================================================
// Helper: Get tint color based on theme
// ========================================================================

@Composable
private fun getTintColor(): Color {
    val context = LocalContext.current
    val isDark = (context.resources.configuration.uiMode and
        android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
        android.content.res.Configuration.UI_MODE_NIGHT_YES

    return if (isDark) {
        LiquidGlassComposeConstants.TINT_COLOR_DARK.copy(alpha = LiquidGlassComposeConstants.TINT_OPACITY)
    } else {
        LiquidGlassComposeConstants.TINT_COLOR_LIGHT.copy(alpha = LiquidGlassComposeConstants.TINT_OPACITY)
    }
}

// ========================================================================
// Main Liquid Glass Modifier (Tier 1 - Full Glass)
// ========================================================================

/**
 * Full liquid glass modifier using the AGSL shader with a bitmap backdrop.
 *
 * @param backdropBitmap The bitmap to sample (wallpaper + home screen composite)
 * @param shape Rounded-corner shape (required by lens). Remember between recompositions.
 * @param lensHeightDp Lens height in dp
 * @param lensAmountDp Lens amount in dp
 * @param enableChromaticAberration Enable red/yellow/blue fringe
 * @param resolutionScale Resolution scale for performance (0.33 default)
 * @param offsetX Horizontal offset for sampling (screen position)
 * @param offsetY Vertical offset for sampling (screen position)
 */
fun Modifier.liquidGlassWithBitmap(
    backdropBitmap: Bitmap?,
    shape: Shape = RoundedCornerShape(24.dp),
    lensHeightDp: Float = LiquidGlassComposeConstants.LENS_HEIGHT_DP,
    lensAmountDp: Float = LiquidGlassComposeConstants.LENS_AMOUNT_DP,
    enableChromaticAberration: Boolean = true,
    resolutionScale: Float = LiquidGlassComposeConstants.RESOLUTION_SCALE,
    offsetX: Float = 0f,
    offsetY: Float = 0f,
): Modifier = composed(
    factory = {
        // Capture the view to get screen position for sampling
        var viewOffsetX = offsetX
        var viewOffsetY = offsetY
        val view = LocalView.current
        view.getLocationOnScreen(arrayOf(0, 0).also { loc ->
            viewOffsetX = loc[0].toFloat()
            viewOffsetY = loc[1].toFloat()
        })
    },
    inspectorInfo = debugInspectorInfo { name = "liquidGlassWithBitmap" }
) { thisRef ->
    drawWithCache(
        onDrawWithContent = { drawScope ->
            // Check settings from CompositionLocals
            val enabled = LocalLiquidGlassEnabled.current
            val reduceBlur = LocalReduceBlur.current

            val canvas = (drawScope.drawContext.canvas as AndroidCanvas).canvas
            val width = drawScope.size.width.toFloat()
            val height = drawScope.size.height.toFloat()

            // If glass is disabled, not supported, or reduce blur is on, draw fallback
            if (!enabled || !isGlassSupported || reduceBlur || backdropBitmap == null || width <= 0f || height <= 0f) {
                // Draw fallback: solid tint with hairline
                val tintColor = getTintColor()
                drawScope.drawRect(
                    color = tintColor.copy(alpha = tintColor.alpha * 2),
                    shape = shape
                )
                drawScope.drawContent()
                // Hairline edge
                drawScope.drawRect(
                    color = Color.White.copy(alpha = LiquidGlassComposeConstants.HAIRLINE_OPACITY),
                    style = Stroke(width = LiquidGlassComposeConstants.HAIRLINE_WIDTH_DP.dp.toPx()),
                    shape = shape
                )
                return@drawWithCache
            }

            // Get screen position for backdrop sampling
            val location = IntArray(2)
            thisRef.getLocationOnScreen(location)
            val screenOffsetX = location[0].toFloat() + offsetX
            val screenOffsetY = location[1].toFloat() + offsetY

            // Get corner radii from shape
            val cornerRadii = getCornerRadii(shape, drawScope)

            // Get tint color based on theme
            val tintColor = getTintColor()

            // Draw glass effect using AGSL shader (Android 13+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                drawGlassWithShader(
                    canvas = canvas,
                    backdropBitmap = backdropBitmap,
                    width = width,
                    height = height,
                    cornerRadii = cornerRadii,
                    offsetX = screenOffsetX,
                    offsetY = screenOffsetY,
                    lensHeightDp = lensHeightDp,
                    lensAmountDp = lensAmountDp,
                    chromaticAberration = enableChromaticAberration,
                    depthEffect = true,
                    blurRadiusDp = LiquidGlassComposeConstants.BLUR_RADIUS_DP,
                    tintColor = tintColor,
                    saturation = LiquidGlassComposeConstants.SATURATION,
                    resolutionScale = resolutionScale
                )
            } else {
                // Fallback for Android 12 and below
                drawGlassFallback(
                    canvas = canvas,
                    backdropBitmap = backdropBitmap,
                    width = width,
                    height = height,
                    cornerRadii = cornerRadii,
                    offsetX = screenOffsetX,
                    offsetY = screenOffsetY,
                    tintColor = tintColor,
                    drawScope = drawScope
                )
            }

            // Draw content on top of glass
            drawScope.drawContent()
        }
    )
}

// ========================================================================
// Lightweight Liquid Glass Modifier (Tier 2)
// ========================================================================

/**
 * Lightweight liquid glass: tint + rim + hairline, no blur, no lens, no backdrop capture.
 * Used for repeated elements on flat backgrounds.
 */
@Composable
fun Modifier.lightweightLiquidGlass(
    shape: Shape = RoundedCornerShape(20.dp),
    fallbackColor: Color = Color.Transparent,
    pressed: Boolean = false
): Modifier = composed(
    inspectorInfo = debugInspectorInfo { name = "lightweightLiquidGlass" }
) { thisRef ->
    drawWithCache(
        onDrawWithContent = { drawScope ->
            // Check settings from CompositionLocals
            val enabled = LocalLiquidGlassEnabled.current
            val reduceBlur = LocalReduceBlur.current

            if (!enabled || !isGlassSupported || reduceBlur) {
                // Draw fallback
                drawScope.drawRect(
                    color = fallbackColor,
                    shape = shape
                )
                drawScope.drawContent()
                // Hairline edge
                drawScope.drawRect(
                    color = Color.White.copy(alpha = LiquidGlassComposeConstants.HAIRLINE_OPACITY),
                    style = Stroke(width = LiquidGlassComposeConstants.HAIRLINE_WIDTH_DP.dp.toPx()),
                    shape = shape
                )
                return@drawWithCache
            }

            val tintColor = getTintColor()
            val contentColor = glassContentColor()

            // Draw tint background
            drawScope.drawRect(
                color = if (pressed) tintColor.copy(alpha = tintColor.alpha * 1.5f) else tintColor,
                shape = shape
            )

            // Draw content
            drawScope.drawContent()

            // Draw directional rim (top-left highlight)
            drawScope.drawRect(
                color = Color.White.copy(alpha = LiquidGlassComposeConstants.RIM_ALPHA * 0.3f),
                style = Stroke(width = LiquidGlassComposeConstants.RIM_WIDTH_DP.dp.toPx()),
                shape = shape
            )

            // Draw hairline edge
            drawScope.drawRect(
                color = Color.White.copy(alpha = LiquidGlassComposeConstants.HAIRLINE_OPACITY),
                style = Stroke(width = LiquidGlassComposeConstants.HAIRLINE_WIDTH_DP.dp.toPx()),
                shape = shape
            )
        }
    )
}

// ========================================================================
// Rim Only Modifier (Tier 3)
// ========================================================================

/**
 * Rim only: for elements that keep their own fill but need glassy rim.
 */
@Composable
fun Modifier.liquidGlassRim(
    shape: Shape = RoundedCornerShape(16.dp)
): Modifier = composed(
    inspectorInfo = debugInspectorInfo { name = "liquidGlassRim" }
) { thisRef ->
    drawWithCache(
        onDrawWithContent = { drawScope ->
            // Check settings from CompositionLocals
            val enabled = LocalLiquidGlassEnabled.current

            if (!enabled || !isGlassSupported) {
                drawScope.drawContent()
                return@drawWithCache
            }

            drawScope.drawContent()

            // Directional rim highlight
            drawScope.drawRect(
                color = Color.White.copy(alpha = LiquidGlassComposeConstants.RIM_ALPHA * 0.4f),
                style = Stroke(width = LiquidGlassComposeConstants.RIM_WIDTH_DP.dp.toPx()),
                shape = shape
            )

            // Subtle hairline
            drawScope.drawRect(
                color = Color.White.copy(alpha = LiquidGlassComposeConstants.HAIRLINE_OPACITY),
                style = Stroke(width = LiquidGlassComposeConstants.HAIRLINE_WIDTH_DP.dp.toPx()),
                shape = shape
            )
        }
    )
}

// ========================================================================
// Helper: Get corner radii from shape
// ========================================================================

private fun getCornerRadii(shape: Shape, drawScope: DrawScope): FloatArray {
    val roundRect = shape.createOutline(
        Size(drawScope.size.width.toFloat(), drawScope.size.height.toFloat()),
        drawScope.layoutDirection,
        drawScope.drawContext.density
    ) as? RoundRect

    if (roundRect != null) {
        val radii = roundRect.cornersRadii
        return floatArrayOf(
            radii[0], // topLeft
            radii[1], // topRight
            radii[2], // bottomRight
            radii[3]  // bottomLeft
        )
    }

    // Default to uniform radius
    val radius = shape.getCornerRadius(Size(drawScope.size.width.toFloat(), drawScope.size.height.toFloat()))
    return floatArrayOf(radius, radius, radius, radius)
}

// ========================================================================
// AGSL Shader Drawing (Android 13+)
// ========================================================================

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun drawGlassWithShader(
    canvas: Canvas,
    backdropBitmap: Bitmap,
    width: Float,
    height: Float,
    cornerRadii: FloatArray,
    offsetX: Float,
    offsetY: Float,
    lensHeightDp: Float,
    lensAmountDp: Float,
    chromaticAberration: Boolean,
    depthEffect: Boolean,
    blurRadiusDp: Float,
    tintColor: Color,
    saturation: Float,
    resolutionScale: Float
) {
    val density = canvas.density

    // Convert dp to px and apply resolution scale
    val lensHeightPx = (lensHeightDp * density * resolutionScale)
    val lensAmountPx = (lensAmountDp * density * resolutionScale)
    val blurRadiusPx = (blurRadiusDp * density * resolutionScale)
    val cornerRadiiPx = cornerRadii.map { it * density * resolutionScale }.toFloatArray()

    val runtimeShader = getRuntimeShader()

    // Create bitmap shader for backdrop
    val contentShader = android.graphics.BitmapShader(
        backdropBitmap,
        android.graphics.Shader.TileMode.CLAMP,
        android.graphics.Shader.TileMode.CLAMP
    )

    runtimeShader.setInputShader("content", contentShader)
    runtimeShader.setFloatUniform("uSize", width * density * resolutionScale, height * density * resolutionScale)
    runtimeShader.setFloatUniform("uCornerRadii", cornerRadiiPx[0], cornerRadiiPx[1], cornerRadiiPx[2], cornerRadiiPx[3])
    runtimeShader.setFloatUniform("uLensHeight", lensHeightPx)
    runtimeShader.setFloatUniform("uLensAmount", lensAmountPx)
    runtimeShader.setFloatUniform("uRefractiveIndex", 1.52f)
    runtimeShader.setFloatUniform("uChromaticAberration", if (chromaticAberration) 1f else 0f)
    runtimeShader.setFloatUniform("uDepthEffect", if (depthEffect) 1f else 0f)
    runtimeShader.setFloatUniform("uOffset", offsetX * density * resolutionScale, offsetY * density * resolutionScale)
    runtimeShader.setFloatUniform("uBlurRadius", blurRadiusPx)
    runtimeShader.setFloatUniform("uTintColor", tintColor.red, tintColor.green, tintColor.blue, tintColor.alpha)
    runtimeShader.setFloatUniform("uSaturation", saturation)

    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = runtimeShader
    }

    canvas.drawRect(0f, 0f, width, height, paint)
    paint.shader = null
}

// ========================================================================
// Fallback Drawing (Android 12 and below)
// ========================================================================

private fun drawGlassFallback(
    canvas: Canvas,
    backdropBitmap: Bitmap,
    width: Float,
    height: Float,
    cornerRadii: FloatArray,
    offsetX: Float,
    offsetY: Float,
    tintColor: Color,
    drawScope: DrawScope
) {
    val path = Path()
    val radii8 = floatArrayOf(
        cornerRadii[0], cornerRadii[0],
        cornerRadii[1], cornerRadii[1],
        cornerRadii[2], cornerRadii[2],
        cornerRadii[3], cornerRadii[3]
    )
    path.addRoundRect(RectF(0f, 0f, width, height), radii8, Path.Direction.CW)

    canvas.save()
    canvas.clipPath(path)

    // Draw backdrop
    val contentShader = android.graphics.BitmapShader(
        backdropBitmap,
        android.graphics.Shader.TileMode.CLAMP,
        android.graphics.Shader.TileMode.CLAMP
    )
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = contentShader
    }
    canvas.translate(-offsetX, -offsetY)
    canvas.drawRect(offsetX, offsetY, offsetX + width, offsetY + height, paint)
    canvas.restore()
    paint.shader = null

    // Draw translucent tint
    val tintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = tintColor.toArgb()
    }
    canvas.drawPath(path, tintPaint)

    // Draw hairline edge
    val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = LiquidGlassComposeConstants.HAIRLINE_WIDTH_DP * canvas.density
        color = Color.White.copy(alpha = LiquidGlassComposeConstants.HAIRLINE_OPACITY).toArgb()
    }
    canvas.drawPath(path, rimPaint)
}

// ========================================================================
// Shadow Drawing Helper
// ========================================================================

/**
 * Draws a soft drop shadow behind a glass element.
 * Call this before drawing the glass element.
 */
fun Modifier.glassShadow(
    shape: Shape = RoundedCornerShape(24.dp),
    radiusDp: Float = LiquidGlassComposeConstants.SHADOW_RADIUS_DP,
    offsetYDp: Float = LiquidGlassComposeConstants.SHADOW_OFFSET_DP,
    opacity: Float = LiquidGlassComposeConstants.SHADOW_OPACITY
): Modifier = composed(
    inspectorInfo = debugInspectorInfo { name = "glassShadow" }
) { thisRef ->
    drawWithCache(
        onDrawWithContent = { drawScope ->
            // Check settings from CompositionLocals
            val enabled = LocalLiquidGlassEnabled.current

            if (!enabled || !isGlassSupported) {
                drawScope.drawContent()
                return@drawWithCache
            }

            // Draw shadow first
            val shadowPath = Path()
            val size = drawScope.size
            val radii8 = floatArrayOf(
                24f, 24f, 24f, 24f, 24f, 24f, 24f, 24f
            )
            shadowPath.addRoundRect(
                RectF(0f, 0f, size.width.toFloat(), size.height.toFloat()),
                radii8,
                Path.Direction.CW
            )

            val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.Black.copy(alpha = opacity).toArgb()
                setShadowLayer(
                    radiusDp * drawScope.density,
                    0f,
                    offsetYDp * drawScope.density,
                    Color.Black.copy(alpha = opacity).toArgb()
                )
            }
            (drawScope.drawContext.canvas as AndroidCanvas).canvas.drawPath(shadowPath, shadowPaint)

            // Draw content (glass element)
            drawScope.drawContent()
        }
    )
}