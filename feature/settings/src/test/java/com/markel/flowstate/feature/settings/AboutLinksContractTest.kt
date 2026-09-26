package com.markel.flowstate.feature.settings

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class AboutLinksContractTest {

    @Test
    fun aboutScreen_linksToMaintainerRepositoryAndGitHubProfile() {
        val root = repositoryRoot()
        val source = File(
            root,
            "feature/settings/src/main/java/com/markel/flowstate/feature/settings/AboutScreen.kt",
        ).readText()
        val localizedStrings = listOf(
            "feature/settings/src/main/res/values/strings.xml",
            "feature/settings/src/main/res/values-en/strings.xml",
            "feature/settings/src/main/res/values-es/strings.xml",
            "feature/settings/src/main/res/values-zh-rCN/strings.xml",
        ).map { File(root, it) }

        assertTrue(
            "The app card must open the maintainer's FlowState fork",
            "private const val REPO_URL = \"https://github.com/li1408/FlowState\"" in source,
        )
        assertTrue(
            "The maintainer card must open the user's GitHub homepage",
            "private const val DEVELOPER_GITHUB_URL = \"https://github.com/li1408\"" in source,
        )
        localizedStrings.forEach { file ->
            assertTrue(
                "${file.relativeTo(root).invariantSeparatorsPath} must identify the GitHub maintainer",
                "<string name=\"about_developer_name\">li1408</string>" in file.readText(),
            )
        }
    }

    private fun repositoryRoot(): File {
        var directory: File? = File(requireNotNull(System.getProperty("user.dir"))).canonicalFile
        while (directory != null) {
            if (directory.resolve("settings.gradle.kts").isFile) return directory
            directory = directory.parentFile
        }
        error("Could not locate the repository root")
    }
}
