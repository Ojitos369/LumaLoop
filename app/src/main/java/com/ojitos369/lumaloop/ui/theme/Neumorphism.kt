package com.ojitos369.lumaloop.ui.theme

import android.graphics.BlurMaskFilter
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

object NeuDefaults {
    val cornerRadius = 20.dp
    val elevation = 6.dp
    val blur = 12.dp
}

private fun DrawScope.drawBlurredRoundRect(
    color: Color,
    offsetX: Float,
    offsetY: Float,
    radius: Float,
    blur: Float
) {
    drawIntoCanvas { canvas ->
        val paint = Paint()
        val frameworkPaint = paint.asFrameworkPaint()
        frameworkPaint.color = color.toArgb()
        if (blur > 0f) {
            frameworkPaint.maskFilter = BlurMaskFilter(blur, BlurMaskFilter.Blur.NORMAL)
        }
        canvas.drawRoundRect(
            left = offsetX,
            top = offsetY,
            right = offsetX + size.width,
            bottom = offsetY + size.height,
            radiusX = radius,
            radiusY = radius,
            paint = paint
        )
    }
}

/**
 * Raised (extruded) neumorphic surface: dual shadow — light from top-left,
 * dark towards bottom-right — over a fill matching the screen base color.
 */
fun Modifier.neumorphic(
    cornerRadius: Dp = NeuDefaults.cornerRadius,
    elevation: Dp = NeuDefaults.elevation,
    blur: Dp = NeuDefaults.blur,
    backgroundColor: Color = NeuBaseRaised,
    lightShadow: Color = NeuShadowLight,
    darkShadow: Color = NeuShadowDark
): Modifier = this
    .drawBehind {
        val r = cornerRadius.toPx()
        val o = elevation.toPx()
        val b = blur.toPx()
        drawBlurredRoundRect(darkShadow, o, o, r, b)
        drawBlurredRoundRect(lightShadow, -o, -o, r, b)
        drawRoundRect(color = backgroundColor, cornerRadius = CornerRadius(r, r))
    }
    .clip(RoundedCornerShape(cornerRadius))

/**
 * Inset (concave/pressed) neumorphic surface: inner shadows for inputs,
 * tracks and pressed states.
 */
fun Modifier.neumorphicInset(
    cornerRadius: Dp = NeuDefaults.cornerRadius,
    elevation: Dp = 3.dp,
    blur: Dp = 6.dp,
    backgroundColor: Color = NeuBaseInset,
    lightShadow: Color = NeuShadowLight,
    darkShadow: Color = NeuShadowDark
): Modifier = this
    .clip(RoundedCornerShape(cornerRadius))
    .background(backgroundColor)
    .drawBehind {
        val r = cornerRadius.toPx()
        val o = elevation.toPx()
        val b = blur.toPx()
        val clip = Path().apply {
            addRoundRect(RoundRect(0f, 0f, size.width, size.height, CornerRadius(r, r)))
        }
        clipPath(clip) {
            drawIntoCanvas { canvas ->
                val paint = Paint()
                val fp = paint.asFrameworkPaint()
                fp.style = android.graphics.Paint.Style.STROKE
                fp.strokeWidth = o * 2
                fp.maskFilter = BlurMaskFilter(b, BlurMaskFilter.Blur.NORMAL)

                fp.color = darkShadow.toArgb()
                canvas.drawRoundRect(
                    left = o, top = o,
                    right = size.width + o * 2, bottom = size.height + o * 2,
                    radiusX = r, radiusY = r, paint = paint
                )

                fp.color = lightShadow.toArgb()
                canvas.drawRoundRect(
                    left = -o * 2, top = -o * 2,
                    right = size.width - o, bottom = size.height - o,
                    radiusX = r, radiusY = r, paint = paint
                )
            }
        }
    }
