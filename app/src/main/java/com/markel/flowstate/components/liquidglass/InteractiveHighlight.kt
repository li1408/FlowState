/*
 * Adapted from AndroidLiquidGlass tag 1.0.2.
 * Copyright 2025 Kyant. Licensed under Apache-2.0.
 * Modified for FlowState package naming.
 */
package com.markel.flowstate.components.liquidglass

import android.graphics.RuntimeShader
import android.os.Build
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.toArgb

internal class InteractiveHighlight(
    private val progress: () -> Float,
    private val position: (size: Size, offset: Offset) -> Offset = { _, offset -> offset },
) {
    private val shader = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        RuntimeShader(
            """
            uniform float2 size;
            layout(color) uniform half4 color;
            uniform float radius;
            uniform float2 position;

            half4 main(float2 coord) {
                float dist = distance(coord, position);
                float intensity = smoothstep(radius, radius * 0.5, dist);
                return color * intensity;
            }
            """.trimIndent(),
        )
    } else {
        null
    }

    val modifier: Modifier = Modifier.drawWithContent {
        val pressProgress = progress()
        if (pressProgress > 0f) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && shader != null) {
                drawRect(Color.White.copy(0.08f * pressProgress), blendMode = BlendMode.Plus)
                shader.apply {
                    val highlightPosition = position(size, Offset.Zero)
                    setFloatUniform("size", size.width, size.height)
                    setColorUniform("color", Color.White.copy(0.15f * pressProgress).toArgb())
                    setFloatUniform("radius", size.minDimension * 1.5f)
                    setFloatUniform(
                        "position",
                        highlightPosition.x.coerceIn(0f, size.width),
                        highlightPosition.y.coerceIn(0f, size.height),
                    )
                }
                drawRect(ShaderBrush(shader), blendMode = BlendMode.Plus)
            } else {
                drawRect(Color.White.copy(0.25f * pressProgress), blendMode = BlendMode.Plus)
            }
        }
        drawContent()
    }
}
