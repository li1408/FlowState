package com.markel.flowstate.navigation

import androidx.compose.ui.Modifier
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope

/**
 * Reuses the last measured bottom-bar size in the outgoing navigation scene.
 * This keeps the movable bar's shared-element placeholder stable while the
 * incoming scene owns the real content.
 */
fun Modifier.cacheSize(useCachedSize: Boolean): Modifier =
    this.then(CacheSizeElement(useCachedSize))

private data class CacheSizeElement(
    val useCachedSize: Boolean,
) : ModifierNodeElement<CacheSizeNode>() {
    override fun create(): CacheSizeNode = CacheSizeNode(useCachedSize)

    override fun update(node: CacheSizeNode) {
        node.useCachedSize = useCachedSize
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "cacheSize"
        properties["useCachedSize"] = useCachedSize
    }
}

private class CacheSizeNode(
    var useCachedSize: Boolean,
) : Modifier.Node(), LayoutModifierNode {
    private var cachedSize: IntSize? = null

    override fun MeasureScope.measure(
        measurable: Measurable,
        constraints: Constraints,
    ): MeasureResult {
        val previousSize = if (useCachedSize) cachedSize else null
        val placeable = if (previousSize == null) {
            measurable.measure(constraints)
        } else {
            measurable.measure(Constraints.fixed(previousSize.width, previousSize.height))
        }
        if (!useCachedSize) cachedSize = IntSize(placeable.width, placeable.height)
        return layout(placeable.width, placeable.height) {
            placeable.place(0, 0)
        }
    }
}
