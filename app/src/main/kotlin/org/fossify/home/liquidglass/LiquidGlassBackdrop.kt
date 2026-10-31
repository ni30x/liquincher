package org.fossify.home.liquidglass

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.view.View

object LiquidGlassBackdrop {

    private var cachedWallpaperBitmap: Bitmap? = null
    private var compositeBitmap: Bitmap? = null
    private var compositeCanvas: Canvas? = null
    private val compositePaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private var lastScreenWidth: Int = 0
    private var lastScreenHeight: Int = 0

    // Reference to the desktop view for dynamic scene composition during swipe
    var desktopView: View? = null
    var drawerProgress: Float = 0f

    private fun getWallpaperBitmap(context: Context, screenWidth: Int, screenHeight: Int): Bitmap? {
        if (screenWidth <= 0 || screenHeight <= 0) return cachedWallpaperBitmap

        val existing = cachedWallpaperBitmap
        if (existing != null && !existing.isRecycled && lastScreenWidth == screenWidth && lastScreenHeight == screenHeight) {
            return existing
        }

        try {
            val wallpaperManager = WallpaperManager.getInstance(context)
            val wallpaperDrawable: Drawable? = try {
                wallpaperManager.drawable
            } catch (_: SecurityException) {
                null
            }

            if (wallpaperDrawable != null) {
                val bitmap = Bitmap.createBitmap(screenWidth, screenHeight, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                wallpaperDrawable.setBounds(0, 0, screenWidth, screenHeight)
                wallpaperDrawable.draw(canvas)

                cachedWallpaperBitmap?.recycle()
                cachedWallpaperBitmap = bitmap
                lastScreenWidth = screenWidth
                lastScreenHeight = screenHeight
                return bitmap
            }
        } catch (_: Throwable) {
        }

        return cachedWallpaperBitmap
    }

    fun getBackdropBitmap(context: Context, screenWidth: Int, screenHeight: Int): Bitmap? {
        val wallpaper = getWallpaperBitmap(context, screenWidth, screenHeight) ?: return null

        val view = desktopView
        val progress = drawerProgress

        // If drawer is fully open or desktop is gone, return pure wallpaper
        if (view == null || progress >= 1f || view.width <= 0 || view.height <= 0) {
            return wallpaper
        }

        // Composite wallpaper + desktop icons during swipe so the leading edge bends apps and wallpaper
        if (compositeBitmap == null || compositeBitmap?.width != screenWidth || compositeBitmap?.height != screenHeight) {
            compositeBitmap?.recycle()
            compositeBitmap = Bitmap.createBitmap(screenWidth, screenHeight, Bitmap.Config.ARGB_8888)
            compositeCanvas = Canvas(compositeBitmap!!)
        }

        val canvas = compositeCanvas ?: return wallpaper
        canvas.drawBitmap(wallpaper, 0f, 0f, null)

        val alpha = ((1f - progress).coerceIn(0f, 1f) * 255).toInt()
        if (alpha > 0) {
            compositePaint.alpha = alpha
            canvas.save()
            view.draw(canvas)
            canvas.restore()
        }

        return compositeBitmap
    }

    fun invalidate() {
        cachedWallpaperBitmap?.recycle()
        cachedWallpaperBitmap = null
        compositeBitmap?.recycle()
        compositeBitmap = null
        compositeCanvas = null
        lastScreenWidth = 0
        lastScreenHeight = 0
    }
}
