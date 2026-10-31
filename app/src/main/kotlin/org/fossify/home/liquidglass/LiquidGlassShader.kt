package org.fossify.home.liquidglass

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi

object LiquidGlassShader {

    const val AGSL_SOURCE = """
        uniform shader content;
        uniform float2 uSize;
        uniform float4 uCornerRadii; // topLeft, topRight, bottomRight, bottomLeft
        uniform float uLensHeight;
        uniform float uLensAmount;
        uniform float uRefractiveIndex;
        uniform float uChromaticAberration;
        uniform float uDepthEffect;
        uniform float2 uOffset;
        uniform float uBlurRadius;
        uniform vec4 uTintColor;
        uniform float uSaturation;

        float sdRoundedBox(float2 p, float2 b, float4 r) {
            r.xy = (p.x > 0.0) ? r.xy : r.zw;
            r.x  = (p.y > 0.0) ? r.x  : r.y;
            float2 q = abs(p) - b + r.x;
            return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - r.x;
        }

        vec4 main(float2 fragCoord) {
            float2 halfSize = uSize * 0.5;
            float2 p = fragCoord - halfSize;
            float dist = sdRoundedBox(p, halfSize, uCornerRadii);

            if (dist > 0.0) {
                return vec4(0.0);
            }

            float d = -dist;

            float eps = 1.0;
            float dx = sdRoundedBox(p + float2(eps, 0.0), halfSize, uCornerRadii) -
                       sdRoundedBox(p - float2(eps, 0.0), halfSize, uCornerRadii);
            float dy = sdRoundedBox(p + float2(0.0, eps), halfSize, uCornerRadii) -
                       sdRoundedBox(p - float2(0.0, eps), halfSize, uCornerRadii);
            float2 grad = normalize(float2(dx, dy));

            float2 disp = float2(0.0);
            float rimFactor = 0.0;

            if (d < uLensHeight && uLensHeight > 0.0) {
                float t = clamp(d / uLensHeight, 0.0, 1.0);
                float h = sqrt(max(0.0, 1.0 - (1.0 - t) * (1.0 - t)));
                float dh = (1.0 - t) / max(0.01, h);

                float slope = (uLensAmount / uLensHeight);
                vec3 N = normalize(vec3(-grad * dh * slope, 1.0));
                vec3 I = vec3(0.0, 0.0, -1.0);

                float eta = 1.0 / max(1.0, uRefractiveIndex);
                float cosI = -dot(I, N);
                float sin2T = eta * eta * (1.0 - cosI * cosI);

                if (sin2T <= 1.0) {
                    float cosT = sqrt(1.0 - sin2T);
                    vec3 R = eta * I + (eta * cosI - cosT) * N;
                    disp = R.xy * uLensHeight;
                }

                vec3 L = normalize(vec3(-0.707, -0.707, 0.5));
                vec3 R_refl = reflect(I, N);
                float spec = max(0.0, dot(R_refl, L));
                float fresnel = 0.04 + 0.96 * pow(1.0 - cosI, 4.0);
                rimFactor = pow(spec, 12.0) * fresnel * (1.0 - t);
            }

            float2 sampleCoord = fragCoord + uOffset + disp;

            vec4 col = vec4(0.0);
            float r = max(0.0, uBlurRadius);
            if (r > 0.5) {
                col += content.eval(sampleCoord) * 0.28;
                col += content.eval(sampleCoord + float2(-r, 0.0)) * 0.12;
                col += content.eval(sampleCoord + float2( r, 0.0)) * 0.12;
                col += content.eval(sampleCoord + float2(0.0, -r)) * 0.12;
                col += content.eval(sampleCoord + float2(0.0,  r)) * 0.12;
                col += content.eval(sampleCoord + float2(-r * 0.707, -r * 0.707)) * 0.06;
                col += content.eval(sampleCoord + float2( r * 0.707, -r * 0.707)) * 0.06;
                col += content.eval(sampleCoord + float2(-r * 0.707,  r * 0.707)) * 0.06;
                col += content.eval(sampleCoord + float2( r * 0.707,  r * 0.707)) * 0.06;
            } else {
                col = content.eval(sampleCoord);
            }

            if (uChromaticAberration > 0.5 && length(disp) > 0.5) {
                float rR = content.eval(sampleCoord + disp * 0.04).r;
                float bB = content.eval(sampleCoord - disp * 0.04).b;
                col.r = mix(col.r, rR, 0.6);
                col.b = mix(col.b, bB, 0.6);
            }

            float luma = dot(col.rgb, vec3(0.2126, 0.7152, 0.0722));
            col.rgb = mix(vec3(luma), col.rgb, uSaturation);

            col.rgb = mix(col.rgb, uTintColor.rgb, uTintColor.a);

            if (uDepthEffect > 0.5 && d < uLensHeight) {
                float t = clamp(d / uLensHeight, 0.0, 1.0);
                col.rgb *= (0.88 + 0.12 * t);
            }

            col.rgb += vec3(rimFactor * 0.6);

            if (d < 1.2) {
                col.rgb += vec3(0.22 * (1.2 - d));
            }

            return vec4(col.rgb, 1.0);
        }
    """

    private var cachedRuntimeShader: RuntimeShader? = null

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    fun getShader(): RuntimeShader {
        var shader = cachedRuntimeShader
        if (shader == null) {
            shader = RuntimeShader(AGSL_SOURCE)
            cachedRuntimeShader = shader
        }
        return shader
    }

    fun isRefractionSupported(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    }

    fun drawGlass(
        canvas: Canvas,
        backdropBitmap: Bitmap?,
        width: Float,
        height: Float,
        cornerRadii: FloatArray, // 4 floats: [topLeft, topRight, bottomRight, bottomLeft]
        offsetX: Float,
        offsetY: Float,
        lensHeight: Float,
        lensAmount: Float,
        refractiveIndex: Float = 1.52f,
        chromaticAberration: Boolean = true,
        depthEffect: Boolean = true,
        blurRadius: Float = 0f,
        tintColor: FloatArray = floatArrayOf(0.07f, 0.07f, 0.07f, 0.20f),
        saturation: Float = 1.35f,
        paint: Paint
    ) {
        if (backdropBitmap == null || width <= 0f || height <= 0f) return

        if (isRefractionSupported()) {
            val runtimeShader = getShader()
            val contentShader = BitmapShader(
                backdropBitmap,
                Shader.TileMode.CLAMP,
                Shader.TileMode.CLAMP
            )

            runtimeShader.setInputShader("content", contentShader)
            runtimeShader.setFloatUniform("uSize", width, height)
            runtimeShader.setFloatUniform(
                "uCornerRadii",
                cornerRadii[0],
                cornerRadii[1],
                cornerRadii[2],
                cornerRadii[3]
            )
            runtimeShader.setFloatUniform("uLensHeight", lensHeight)
            runtimeShader.setFloatUniform("uLensAmount", lensAmount)
            runtimeShader.setFloatUniform("uRefractiveIndex", refractiveIndex)
            runtimeShader.setFloatUniform("uChromaticAberration", if (chromaticAberration) 1f else 0f)
            runtimeShader.setFloatUniform("uDepthEffect", if (depthEffect) 1f else 0f)
            runtimeShader.setFloatUniform("uOffset", offsetX, offsetY)
            runtimeShader.setFloatUniform("uBlurRadius", blurRadius)
            runtimeShader.setFloatUniform("uTintColor", tintColor[0], tintColor[1], tintColor[2], tintColor[3])
            runtimeShader.setFloatUniform("uSaturation", saturation)

            paint.shader = runtimeShader
            canvas.drawRect(0f, 0f, width, height, paint)
            paint.shader = null
        } else {
            // Fallback for Android 12 and below: translucent blur layer with hairline
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

            val contentShader = BitmapShader(
                backdropBitmap,
                Shader.TileMode.CLAMP,
                Shader.TileMode.CLAMP
            )
            paint.shader = contentShader
            canvas.translate(-offsetX, -offsetY)
            canvas.drawRect(offsetX, offsetY, offsetX + width, offsetY + height, paint)
            canvas.restore()
            paint.shader = null

            // Draw translucent tint
            val tintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(
                    (tintColor[3] * 255).toInt(),
                    (tintColor[0] * 255).toInt(),
                    (tintColor[1] * 255).toInt(),
                    (tintColor[2] * 255).toInt()
                )
            }
            canvas.drawPath(path, tintPaint)

            // Draw hairline edge
            val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 2f
                color = Color.argb(45, 255, 255, 255)
            }
            canvas.drawPath(path, rimPaint)
        }
    }
}
