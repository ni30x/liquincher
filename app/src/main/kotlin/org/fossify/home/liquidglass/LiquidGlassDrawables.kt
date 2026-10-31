package org.fossify.home.liquidglass

import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.view.RoundedCorner.POSITION_TOP_LEFT
import android.view.RoundedCorner.POSITION_TOP_RIGHT
import android.view.View
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.helpers.isSPlus

class LiquidGlassDrawerDrawable(
    private val hostView: View,
    private val screenWidth: Int,
    private val screenHeight: Int
) : Drawable() {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val locationOnScreen = IntArray(2)

    private val density = hostView.resources.displayMetrics.density
    private val lensHeight = 36f * density
    private val lensAmount = 52f * density
    private val blurRadius = 6.0f * density

    override fun draw(canvas: Canvas) {
        val width = bounds.width().toFloat()
        val height = bounds.height().toFloat()
        if (width <= 0f || height <= 0f) return

        hostView.getLocationOnScreen(locationOnScreen)
        val offsetX = locationOnScreen[0].toFloat()
        val offsetY = locationOnScreen[1].toFloat()

        var topLeftRadius = 28f * density
        var topRightRadius = 28f * density

        if (isSPlus()) {
            val insets = hostView.rootWindowInsets
            if (insets != null) {
                val tl = insets.getRoundedCorner(POSITION_TOP_LEFT)?.radius ?: 0
                val tr = insets.getRoundedCorner(POSITION_TOP_RIGHT)?.radius ?: 0
                if (tl > 0) topLeftRadius = tl.toFloat()
                if (tr > 0) topRightRadius = tr.toFloat()
            }
        }

        val cornerRadii = floatArrayOf(
            topLeftRadius,
            topRightRadius,
            0f,
            0f
        )

        val isDark = hostView.context.getProperTextColor() == Color.WHITE ||
                (hostView.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

        val tintColor = if (isDark) {
            floatArrayOf(0.07f, 0.07f, 0.07f, 0.20f)
        } else {
            floatArrayOf(0.98f, 0.98f, 0.98f, 0.20f)
        }

        val backdrop = LiquidGlassBackdrop.getBackdropBitmap(hostView.context, screenWidth, screenHeight)

        LiquidGlassShader.drawGlass(
            canvas = canvas,
            backdropBitmap = backdrop,
            width = width,
            height = height,
            cornerRadii = cornerRadii,
            offsetX = offsetX,
            offsetY = offsetY,
            lensHeight = lensHeight,
            lensAmount = lensAmount,
            refractiveIndex = 1.52f,
            chromaticAberration = true,
            depthEffect = true,
            blurRadius = blurRadius,
            tintColor = tintColor,
            saturation = 1.35f,
            paint = paint
        )
    }

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
        invalidateSelf()
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

class LiquidGlassSearchBarDrawable(
    private val hostView: View,
    private val screenWidth: Int,
    private val screenHeight: Int
) : Drawable() {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val locationOnScreen = IntArray(2)

    private val density = hostView.resources.displayMetrics.density
    private val lensHeight = 18f * density
    private val lensAmount = 26f * density
    private val blurRadius = 5.0f * density

    override fun draw(canvas: Canvas) {
        val width = bounds.width().toFloat()
        val height = bounds.height().toFloat()
        if (width <= 0f || height <= 0f) return

        hostView.getLocationOnScreen(locationOnScreen)
        val offsetX = locationOnScreen[0].toFloat()
        val offsetY = locationOnScreen[1].toFloat()

        val capsuleRadius = height / 2f
        val cornerRadii = floatArrayOf(
            capsuleRadius,
            capsuleRadius,
            capsuleRadius,
            capsuleRadius
        )

        val isDark = hostView.context.getProperTextColor() == Color.WHITE ||
                (hostView.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

        val tintColor = if (isDark) {
            floatArrayOf(0.12f, 0.12f, 0.12f, 0.24f)
        } else {
            floatArrayOf(0.95f, 0.95f, 0.95f, 0.24f)
        }

        val backdrop = LiquidGlassBackdrop.getBackdropBitmap(hostView.context, screenWidth, screenHeight)

        LiquidGlassShader.drawGlass(
            canvas = canvas,
            backdropBitmap = backdrop,
            width = width,
            height = height,
            cornerRadii = cornerRadii,
            offsetX = offsetX,
            offsetY = offsetY,
            lensHeight = lensHeight,
            lensAmount = lensAmount,
            refractiveIndex = 1.52f,
            chromaticAberration = true,
            depthEffect = true,
            blurRadius = blurRadius,
            tintColor = tintColor,
            saturation = 1.35f,
            paint = paint
        )
    }

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
        invalidateSelf()
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
