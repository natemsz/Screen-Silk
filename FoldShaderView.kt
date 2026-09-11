package com.example.foldmorph

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View

class FoldShaderView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val shaderSource = """
        uniform shader screenTex;
        uniform float2 resolution;
        uniform float progress;      // 0.0 (closed) to 1.0 (flat open)
        uniform float isCoverScreen; // 1.0 = cover display, 0.0 = inner display

        half4 main(float2 fragCoord) {
            float2 uv = fragCoord / resolution;

            if (isCoverScreen > 0.5) {
                // Cover Display: Non-linear warp pulling toward the hinge edge
                float factor = (1.0 - progress);
                float nonLinearX = pow(uv.x, 1.0 + factor * 2.2);
                float2 warpedUV = float2(nonLinearX, uv.y);

                half4 color = screenTex.eval(warpedUV * resolution);
                
                // Vignette shadow along the hinge boundary
                float shadow = mix(1.0, smoothstep(0.0, 0.35, uv.x), factor * 0.65);
                return color * shadow;
            } else {
                // Inner Display: Horizontal compression folding into the central spine
                float distFromCenter = uv.x - 0.5;
                float foldCompression = mix(1.0, 0.15, 1.0 - progress);
                float compressedX = 0.5 + (distFromCenter * foldCompression);

                if (compressedX < 0.0 || compressedX > 1.0) {
                    return half4(0.0, 0.0, 0.0, 1.0);
                }

                half4 color = screenTex.eval(float2(compressedX, uv.y) * resolution);
                
                // Crease shadow near center
                float spineShadow = smoothstep(0.0, 0.12, abs(distFromCenter));
                float shadowAmount = mix(1.0, spineShadow, (1.0 - progress) * 0.75);
                return color * shadowAmount;
            }
        }
    """

    private val runtimeShader = RuntimeShader(shaderSource)
    private val paint = Paint()
    private var isCoverScreen: Boolean = false

    fun setIsCover(isCover: Boolean) {
        this.isCoverScreen = isCover
        runtimeShader.setFloatUniform("isCoverScreen", if (isCover) 1.0f else 0.0f)
    }

    fun setTexture(bitmap: Bitmap) {
        val bitmapShader = BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        runtimeShader.setInputShader("screenTex", bitmapShader)
        paint.shader = runtimeShader
        invalidate()
    }

    fun updateProgress(progress: Float) {
        runtimeShader.setFloatUniform("progress", progress)
        runtimeShader.setFloatUniform("resolution", width.toFloat(), height.toFloat())
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (paint.shader != null) {
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        }
    }
}
