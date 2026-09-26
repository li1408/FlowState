/*
 * Backdrop drawing mechanics adapted from AndroidLiquidGlass tag 1.0.2.
 * Copyright 2025 Kyant. Licensed under Apache-2.0.
 * Modified to record navigation content before drawing the liquid bar and to
 * exclude the bar itself from the captured layer.
 */
package com.markel.flowstate.components.liquidglass

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.DefaultCameraDistance
import androidx.compose.ui.graphics.DefaultShadowColor
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.DrawTransform
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.node.requireDensity
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.toIntSize
import com.kyant.backdrop.Backdrop
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * A navigation-scoped backdrop that records the current scene before its
 * liquid bar sibling is drawn. The recorder must never wrap a descendant that
 * samples this same backdrop: doing so creates a cyclic RenderNode graph in
 * HWUI. Recording before the real pass removes the stock recorder's one-frame
 * sampling lag while retaining a single source across tabs.
 */
internal class ScreenBackdrop internal constructor(
    internal val graphicsLayer: GraphicsLayer,
) : Backdrop {
    override val isCoordinatesDependent: Boolean = true

    internal var isRecording: Boolean = false
    internal var layerCoordinates: LayoutCoordinates? by mutableStateOf(null)

    private var coordinatesOwner: Any? = null

    private var inverseLayerScope: ScreenInverseLayerScope? = null

    override fun DrawScope.drawBackdrop(
        density: Density,
        coordinates: LayoutCoordinates?,
        layerBlock: (GraphicsLayerScope.() -> Unit)?,
    ) {
        // A structural sibling boundary is the primary protection. This guard
        // is a second line of defence for accidental nested/custom drawing.
        if (isRecording) return
        val glassCoordinates = coordinates ?: return
        val sourceCoordinates = layerCoordinates ?: return
        withTransform({
            if (layerBlock != null) {
                with(obtainInverseLayerScope()) {
                    inverseTransform(density, layerBlock)
                }
            }
            val offset = try {
                sourceCoordinates.localPositionOf(glassCoordinates)
            } catch (_: Exception) {
                glassCoordinates.positionInWindow() - sourceCoordinates.positionInWindow()
            }
            translate(-offset.x, -offset.y)
        }) {
            drawLayer(graphicsLayer)
        }
    }

    private fun obtainInverseLayerScope(): ScreenInverseLayerScope =
        inverseLayerScope?.apply { reset() }
            ?: ScreenInverseLayerScope().also { inverseLayerScope = it }

    internal fun updateCoordinates(owner: Any, coordinates: LayoutCoordinates) {
        coordinatesOwner = owner
        layerCoordinates = coordinates
    }

    internal fun clearCoordinates(owner: Any) {
        if (coordinatesOwner === owner) {
            coordinatesOwner = null
            layerCoordinates = null
        }
    }
}

@Composable
internal fun rememberScreenBackdrop(): ScreenBackdrop {
    val graphicsLayer = rememberGraphicsLayer()
    return remember(graphicsLayer) { ScreenBackdrop(graphicsLayer) }
}

internal fun Modifier.screenBackdrop(backdrop: ScreenBackdrop): Modifier =
    this then ScreenBackdropElement(backdrop)

private data class ScreenBackdropElement(
    val backdrop: ScreenBackdrop,
) : ModifierNodeElement<ScreenBackdropNode>() {
    override fun create(): ScreenBackdropNode = ScreenBackdropNode(backdrop)

    override fun update(node: ScreenBackdropNode) {
        node.backdrop = backdrop
        node.invalidateDraw()
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "screenBackdrop"
        properties["backdrop"] = backdrop
    }
}

private class ScreenBackdropNode(
    var backdrop: ScreenBackdrop,
) : Modifier.Node(), DrawModifierNode, GlobalPositionAwareModifierNode {
    override val shouldAutoInvalidate: Boolean = false

    override fun ContentDrawScope.draw() {
        if (backdrop.isRecording) {
            drawContent()
            return
        }
        backdrop.isRecording = true
        try {
            recordLayer(
                node = this@ScreenBackdropNode,
                layer = backdrop.graphicsLayer,
            ) {
                this@draw.drawContent()
            }
        } finally {
            backdrop.isRecording = false
        }

        // The real pass happens after capture, so the glass reads this frame's
        // page pixels instead of the previous frame's layer.
        drawContent()
    }

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        if (coordinates.isAttached) backdrop.updateCoordinates(this, coordinates)
    }

    override fun onDetach() {
        backdrop.clearCoordinates(this)
    }
}

private fun DrawScope.recordLayer(
    node: DelegatableNode,
    layer: GraphicsLayer,
    size: IntSize = this.size.toIntSize(),
    block: DrawScope.() -> Unit,
) {
    val density = node.requireDensity()
    layer.record(size) {
        val previousDensity = drawContext.density
        drawContext.density = density
        try {
            block()
        } finally {
            drawContext.density = previousDensity
        }
    }
}

private class ScreenInverseLayerScope : GraphicsLayerScope {
    override var size: Size = Size.Unspecified
    override var density: Float = 1f
    override var fontScale: Float = 1f
    override var scaleX: Float = 1f
    override var scaleY: Float = 1f
    override var alpha: Float = 1f
    override var translationX: Float = 0f
    override var translationY: Float = 0f
    override var shadowElevation: Float = 0f
    override var ambientShadowColor: Color = DefaultShadowColor
    override var spotShadowColor: Color = DefaultShadowColor
    override var rotationX: Float = 0f
    override var rotationY: Float = 0f
    override var rotationZ: Float = 0f
    override var cameraDistance: Float = DefaultCameraDistance
    override var transformOrigin: TransformOrigin = TransformOrigin.Center
    override var shape: Shape = RectangleShape
    override var clip: Boolean = false
    override var renderEffect: RenderEffect? = null
    override var blendMode: BlendMode = BlendMode.SrcOver
    override var colorFilter: ColorFilter? = null
    override var compositingStrategy: CompositingStrategy = CompositingStrategy.Auto

    private var matrix: Matrix? = null

    fun DrawTransform.inverseTransform(
        density: Density,
        layerBlock: GraphicsLayerScope.() -> Unit,
    ) {
        this@ScreenInverseLayerScope.size = size
        this@ScreenInverseLayerScope.density = density.density
        fontScale = density.fontScale
        layerBlock()
        inverseTransformAtTopLeft(rotationZ, scaleX, scaleY)
    }

    fun reset() {
        size = Size.Unspecified
        density = 1f
        fontScale = 1f
        scaleX = 1f
        scaleY = 1f
        alpha = 1f
        translationX = 0f
        translationY = 0f
        shadowElevation = 0f
        ambientShadowColor = DefaultShadowColor
        spotShadowColor = DefaultShadowColor
        rotationX = 0f
        rotationY = 0f
        rotationZ = 0f
        cameraDistance = DefaultCameraDistance
        transformOrigin = TransformOrigin.Center
        shape = RectangleShape
        clip = false
        renderEffect = null
        blendMode = BlendMode.SrcOver
        colorFilter = null
        compositingStrategy = CompositingStrategy.Auto
        matrix = null
    }

    private fun DrawTransform.inverseTransformAtTopLeft(
        rotationZ: Float,
        scaleX: Float,
        scaleY: Float,
    ) {
        if (rotationZ == 0f) {
            if (scaleX != 0f && scaleY != 0f) {
                scale(1f / scaleX, 1f / scaleY, Offset.Zero)
            }
            return
        }

        val transformMatrix = matrix ?: Matrix().also { matrix = it }
        if (transformMatrix.values.size < 16) return
        val radians = rotationZ * (PI / 180.0)
        val sine = sin(radians).toFloat()
        val cosine = cos(radians).toFloat()
        val a00 = cosine * scaleX
        val a01 = sine * scaleY
        val a10 = -sine * scaleX
        val a11 = cosine * scaleY
        val determinant = a00 * a11 - a01 * a10
        if (determinant == 0f) return
        val inverseDeterminant = 1f / determinant
        transformMatrix[0, 0] = a11 * inverseDeterminant
        transformMatrix[0, 1] = -a01 * inverseDeterminant
        transformMatrix[1, 0] = -a10 * inverseDeterminant
        transformMatrix[1, 1] = a00 * inverseDeterminant
        transform(transformMatrix)
    }
}
