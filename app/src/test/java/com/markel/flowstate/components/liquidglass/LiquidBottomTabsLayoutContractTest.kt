package com.markel.flowstate.components.liquidglass

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class LiquidBottomTabsLayoutContractTest {

    @Test
    fun `droplet and selected content are vertically centered in the bar`() {
        val source = File(
            "src/main/java/com/markel/flowstate/components/liquidglass/LiquidBottomTabs.kt",
        ).readText()

        val barContainer = Regex(
            pattern = """Box\(\s*modifier = Modifier(?s:.*?)\.height\(LiquidBottomBarMotion\.BarHeightDp\.dp\)(?s:.*?),\s*contentAlignment = Alignment\.CenterStart,""",
        )

        assertTrue(
            "The 56dp droplet must be centered inside the 64dp bar instead of starting at its top edge",
            barContainer.containsMatchIn(source),
        )
    }
}
