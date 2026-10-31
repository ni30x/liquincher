package org.fossify.home.liquidglass

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi

/**
 * Jetpack Compose and Android Graphics utility for applying the Snell's Law
 * physical refraction liquid glass shader to render nodes and views.
 */
object LiquidGlassEffect {

    /**
     * Creates an Android [RenderEffect] from the Snell's Law AGSL RuntimeShader.
     * Can be passed directly to View.setRenderEffect() or to Compose Modifier.graphicsLayer { renderEffect = ... }
     */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    fun createRenderEffect(
        width: Float,
        height: Float,
        cornerRadius: Float,
        lensHeight: Float,
        lensAmount: Float,
        refractiveIndex: Float = 1.52f,
        chromaticAberration: Boolean = true,
        depthEffect: Boolean = true,
        blurRadius: Float = 16f,
        tintColor: FloatArray = floatArrayOf(0.07f, 0.07f, 0.07f, 0.20f),
        saturation: Float = 1.35f,
        offsetX: Float = 0f,
        offsetY: Float = 0f
    ): RenderEffect {
        val shader = RuntimeShader(LiquidGlassShader.AGSL_SOURCE)
        shader.setFloatUniform("uSize", width, height)
        shader.setFloatUniform("uCornerRadii", cornerRadius, cornerRadius, cornerRadius, cornerRadius)
        shader.setFloatUniform("uLensHeight", lensHeight)
        shader.setFloatUniform("uLensAmount", lensAmount)
        shader.setFloatUniform("uRefractiveIndex", refractiveIndex)
        shader.setFloatUniform("uChromaticAberration", if (chromaticAberration) 1f else 0f)
        shader.setFloatUniform("uDepthEffect", if (depthEffect) 1f else 0f)
        shader.setFloatUniform("uOffset", offsetX, offsetY)
        shader.setFloatUniform("uBlurRadius", blurRadius)
        shader.setFloatUniform("uTintColor", tintColor[0], tintColor[1], tintColor[2], tintColor[3])
        shader.setFloatUniform("uSaturation", saturation)

        return RenderEffect.createRuntimeShaderEffect(shader, "content")
    }
}
