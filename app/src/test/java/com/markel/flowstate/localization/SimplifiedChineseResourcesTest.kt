package com.markel.flowstate.localization

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

class SimplifiedChineseResourcesTest {

    @Test
    fun userFacingBrand_isThirtyDayPlanAcrossEveryLocale() {
        val root = repositoryRoot()
        val brandedResourceFiles = listOf(
            "app/src/main/res/values/strings.xml",
            "app/src/main/res/values-en/strings.xml",
            "app/src/main/res/values-es/strings.xml",
            "app/src/main/res/values-zh-rCN/strings.xml",
            "feature/flow/src/main/res/values/strings.xml",
            "feature/flow/src/main/res/values-en/strings.xml",
            "feature/flow/src/main/res/values-es/strings.xml",
            "feature/flow/src/main/res/values-zh-rCN/strings.xml",
            "feature/settings/src/main/res/values/strings.xml",
            "feature/settings/src/main/res/values-en/strings.xml",
            "feature/settings/src/main/res/values-es/strings.xml",
            "feature/settings/src/main/res/values-zh-rCN/strings.xml",
        )
        val violations = brandedResourceFiles.mapNotNull { relativePath ->
            val file = File(root, relativePath)
            when {
                !file.isFile -> "$relativePath is missing"
                "FlowState" in file.readText() -> "$relativePath still exposes the old FlowState brand"
                "30天计划" !in file.readText() -> "$relativePath does not expose the 30天计划 brand"
                else -> null
            }
        }.toMutableList()

        brandedResourceFiles.take(4).forEach { relativePath ->
            val file = File(root, relativePath)
            if (!file.isFile) return@forEach
            val appName = parseResources(file.parentFile)
                .get(ResourceKey(ResourceKind.STRING, "app_name"))
                ?.values
                ?.get(SINGLE_VALUE)
            if (appName != APP_NAME) {
                violations += "$relativePath defines app_name as '$appName' instead of '$APP_NAME'"
            }
        }

        val manifest = newDocumentBuilderFactory().newDocumentBuilder()
            .parse(File(root, "app/src/main/AndroidManifest.xml"))
        val application = manifest.getElementsByTagName("application").item(0) as? Element
        if (application?.getAttribute("android:label") != APP_NAME_REFERENCE) {
            violations += "AndroidManifest application label must reference $APP_NAME_REFERENCE"
        }
        val mainActivity = manifest.getElementsByTagName("activity")
            .let { activities ->
                (0 until activities.length)
                    .mapNotNull { activities.item(it) as? Element }
                    .firstOrNull { it.getAttribute("android:name") == ".MainActivity" }
            }
        if (mainActivity?.getAttribute("android:label") != APP_NAME_REFERENCE) {
            violations += "AndroidManifest MainActivity label must reference $APP_NAME_REFERENCE"
        }

        assertTrue(
            buildString {
                appendLine("User-facing brand contract violations (${violations.size}):")
                violations.forEach { appendLine("- $it") }
            },
            violations.isEmpty(),
        )
    }

    @Test
    fun simplifiedChineseResources_coverEveryTranslatableDefaultResource() {
        val root = repositoryRoot()
        val violations = mutableListOf<String>()
        val localizedModules = defaultValuesDirectories(root)
            .mapNotNull { defaultDirectory ->
                val defaults = parseResources(defaultDirectory)
                if (defaults.isEmpty()) null else LocalizedModule(defaultDirectory, defaults)
            }

        if (localizedModules.isEmpty()) {
            violations += "No translatable default resources were discovered under src/main/res/values"
        }

        localizedModules.forEach { module ->
            val moduleName = moduleName(root, module.defaultDirectory)
            val chineseDirectory = File(module.defaultDirectory.parentFile, "values-zh-rCN")
            val chinese = if (chineseDirectory.isDirectory) {
                parseResources(chineseDirectory)
            } else {
                violations += "$moduleName: missing src/main/res/values-zh-rCN"
                emptyMap()
            }

            (module.defaults.keys - chinese.keys)
                .sortedWith(compareBy<ResourceKey>({ it.kind.tagName }, { it.name }))
                .forEach { key ->
                    violations += "$moduleName: missing ${key.kind.tagName}/${key.name}"
                }

            module.defaults.forEach { (key, defaultEntry) ->
                val chineseEntry = chinese[key] ?: return@forEach
                when (key.kind) {
                    ResourceKind.STRING -> validateString(
                        moduleName = moduleName,
                        key = key,
                        defaultEntry = defaultEntry,
                        chineseEntry = chineseEntry,
                        violations = violations,
                    )

                    ResourceKind.PLURALS -> validatePlurals(
                        moduleName = moduleName,
                        key = key,
                        defaultEntry = defaultEntry,
                        chineseEntry = chineseEntry,
                        violations = violations,
                    )

                    ResourceKind.STRING_ARRAY -> validateStringArray(
                        moduleName = moduleName,
                        key = key,
                        defaultEntry = defaultEntry,
                        chineseEntry = chineseEntry,
                        violations = violations,
                    )
                }
            }
        }

        assertTrue(
            buildString {
                appendLine("Simplified Chinese resource contract violations (${violations.size}):")
                violations.sorted().forEach { appendLine("- $it") }
            },
            violations.isEmpty(),
        )
    }

    @Test
    fun productionComposeAndGlance_doNotHardcodeEnglishUiText() {
        val root = repositoryRoot()
        val violations = productionKotlinFiles(root)
            .flatMap { file -> hardcodedUiText(file, root) }
            .sorted()

        assertTrue(
            buildString {
                appendLine("Hardcoded English UI text found (${violations.size}).")
                appendLine("Move each value to an Android string resource and use stringResource/getString:")
                violations.forEach { appendLine("- $it") }
            },
            violations.isEmpty(),
        )
    }

    private fun validateString(
        moduleName: String,
        key: ResourceKey,
        defaultEntry: ResourceEntry,
        chineseEntry: ResourceEntry,
        violations: MutableList<String>,
    ) {
        val defaultValue = defaultEntry.values.getValue(SINGLE_VALUE)
        val chineseValue = chineseEntry.values.getValue(SINGLE_VALUE)
        validateLocalizedValue(
            location = "$moduleName:${key.kind.tagName}/${key.name}",
            defaultValue = defaultValue,
            chineseValue = chineseValue,
            violations = violations,
        )
    }

    private fun validatePlurals(
        moduleName: String,
        key: ResourceKey,
        defaultEntry: ResourceEntry,
        chineseEntry: ResourceEntry,
        violations: MutableList<String>,
    ) {
        val location = "$moduleName:${key.kind.tagName}/${key.name}"
        val defaultOther = defaultEntry.values[OTHER_QUANTITY]

        if (chineseEntry.values[OTHER_QUANTITY] == null) {
            violations += "$location: missing required quantity=other"
        }

        chineseEntry.values.forEach { (quantity, chineseValue) ->
            val defaultValue = defaultEntry.values[quantity] ?: defaultOther
            if (defaultValue == null) {
                violations += "$location[$quantity]: no default quantity to validate against"
            } else {
                validateLocalizedValue(
                    location = "$location[$quantity]",
                    defaultValue = defaultValue,
                    chineseValue = chineseValue,
                    violations = violations,
                )
            }
        }
    }

    private fun validateStringArray(
        moduleName: String,
        key: ResourceKey,
        defaultEntry: ResourceEntry,
        chineseEntry: ResourceEntry,
        violations: MutableList<String>,
    ) {
        val location = "$moduleName:${key.kind.tagName}/${key.name}"
        if (defaultEntry.values.size != chineseEntry.values.size) {
            violations += "$location: expected ${defaultEntry.values.size} items, found ${chineseEntry.values.size}"
        }

        defaultEntry.values.forEach { (index, defaultValue) ->
            val chineseValue = chineseEntry.values[index]
            if (chineseValue == null) {
                violations += "$location[$index]: missing item"
            } else {
                validateLocalizedValue(
                    location = "$location[$index]",
                    defaultValue = defaultValue,
                    chineseValue = chineseValue,
                    violations = violations,
                )
            }
        }
    }

    private fun validateLocalizedValue(
        location: String,
        defaultValue: String,
        chineseValue: String,
        violations: MutableList<String>,
    ) {
        if (chineseValue.isBlank()) {
            violations += "$location: blank Simplified Chinese value"
        }

        val expectedPlaceholders = placeholders(defaultValue)
        val actualPlaceholders = placeholders(chineseValue)
        if (expectedPlaceholders != actualPlaceholders) {
            violations += "$location: placeholders changed; expected $expectedPlaceholders, found $actualPlaceholders"
        }
    }

    private fun defaultValuesDirectories(root: File): List<File> {
        return root.walkTopDown()
            .onEnter { directory -> directory == root || directory.name !in IGNORED_DIRECTORIES }
            .filter { directory ->
                directory.isDirectory &&
                    directory.name == "values" &&
                    directory.parentFile?.name == "res" &&
                    directory.parentFile?.parentFile?.name == "main" &&
                    directory.parentFile?.parentFile?.parentFile?.name == "src"
            }
            .sortedBy { it.relativeTo(root).invariantSeparatorsPath }
            .toList()
    }

    private fun productionKotlinFiles(root: File): List<File> {
        return root.walkTopDown()
            .onEnter { directory -> directory == root || directory.name !in IGNORED_DIRECTORIES }
            .filter { file ->
                file.isFile &&
                    file.extension == "kt" &&
                    "/src/main/" in "/${file.relativeTo(root).invariantSeparatorsPath}"
            }
            .sortedBy { it.relativeTo(root).invariantSeparatorsPath }
            .toList()
    }

    private fun hardcodedUiText(file: File, root: File): List<String> {
        val relativePath = file.relativeTo(root).invariantSeparatorsPath
        return file.readLines().flatMapIndexed { index, line ->
            val trimmed = line.trimStart()
            if (trimmed.startsWith("//") || trimmed.startsWith("/*") || trimmed.startsWith("*")) {
                emptyList()
            } else {
                UI_LITERAL_PATTERNS.flatMap { pattern ->
                    pattern.findAll(line).map { match ->
                        "$relativePath:${index + 1}: \"${match.groupValues[1]}\""
                    }.toList()
                }
            }
        }.distinct()
    }

    private fun parseResources(directory: File): Map<ResourceKey, ResourceEntry> {
        val resources = linkedMapOf<ResourceKey, ResourceEntry>()
        directory.listFiles { file -> file.isFile && file.extension.equals("xml", ignoreCase = true) }
            .orEmpty()
            .sortedBy { it.name }
            .forEach { file ->
                val document = newDocumentBuilderFactory().newDocumentBuilder().parse(file)
                val nodes = document.documentElement.childNodes

                for (index in 0 until nodes.length) {
                    val element = nodes.item(index) as? Element ?: continue
                    if (element.getAttribute("translatable") == "false") continue

                    val kind = ResourceKind.fromTagName(element.tagName) ?: continue
                    val name = element.getAttribute("name").takeIf { it.isNotBlank() } ?: continue
                    if (name in NON_LOCALIZABLE_RESOURCE_NAMES) continue
                    val key = ResourceKey(kind, name)
                    resources[key] = ResourceEntry(key, resourceValues(element, kind))
                }
            }
        return resources
    }

    private fun resourceValues(element: Element, kind: ResourceKind): Map<String, String> {
        if (kind == ResourceKind.STRING) {
            return mapOf(SINGLE_VALUE to element.textContent.trim())
        }

        val values = linkedMapOf<String, String>()
        val nodes = element.childNodes
        var arrayIndex = 0
        for (index in 0 until nodes.length) {
            val item = nodes.item(index) as? Element ?: continue
            if (item.tagName != "item") continue

            val selector = when (kind) {
                ResourceKind.PLURALS -> item.getAttribute("quantity")
                ResourceKind.STRING_ARRAY -> (arrayIndex++).toString()
                ResourceKind.STRING -> error("String resources do not have item children")
            }
            values[selector] = item.textContent.trim()
        }
        return values
    }

    private fun newDocumentBuilderFactory(): DocumentBuilderFactory {
        return DocumentBuilderFactory.newInstance().apply {
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            setAttribute("http://javax.xml.XMLConstants/property/accessExternalDTD", "")
            setAttribute("http://javax.xml.XMLConstants/property/accessExternalSchema", "")
        }
    }

    private fun placeholders(value: String): List<String> {
        return FORMAT_PLACEHOLDER.findAll(value)
            .map { it.value }
            .sorted()
            .toList()
    }

    private fun repositoryRoot(): File {
        val userDirectory = requireNotNull(System.getProperty("user.dir")) { "user.dir is not set" }
        var directory: File? = File(userDirectory).canonicalFile
        while (directory != null) {
            if (directory.resolve("settings.gradle.kts").isFile) return directory
            directory = directory.parentFile
        }
        error("Could not find repository root from ${System.getProperty("user.dir")}")
    }

    private fun moduleName(root: File, defaultDirectory: File): String {
        return defaultDirectory.relativeTo(root).invariantSeparatorsPath
            .removeSuffix("/src/main/res/values")
            .ifBlank { ":" }
    }

    private data class LocalizedModule(
        val defaultDirectory: File,
        val defaults: Map<ResourceKey, ResourceEntry>,
    )

    private data class ResourceKey(
        val kind: ResourceKind,
        val name: String,
    )

    private data class ResourceEntry(
        val key: ResourceKey,
        val values: Map<String, String>,
    )

    private enum class ResourceKind(val tagName: String) {
        STRING("string"),
        PLURALS("plurals"),
        STRING_ARRAY("string-array");

        companion object {
            fun fromTagName(tagName: String): ResourceKind? = entries.firstOrNull { it.tagName == tagName }
        }
    }

    private companion object {
        const val APP_NAME = "30天计划"
        const val APP_NAME_REFERENCE = "@string/app_name"
        const val SINGLE_VALUE = "value"
        const val OTHER_QUANTITY = "other"

        val IGNORED_DIRECTORIES = setOf(".git", ".gradle", "build")
        val NON_LOCALIZABLE_RESOURCE_NAMES = setOf(
            "com_google_android_gms_fonts_certs_dev",
            "com_google_android_gms_fonts_certs_prod",
        )
        val FORMAT_PLACEHOLDER = Regex("%(?:\\d+\\$)?[-#+ 0,(<]*\\d*(?:\\.\\d+)?[tT]?[a-zA-Z]")
        val UI_LITERAL_PATTERNS = listOf(
            Regex("\\bText\\s*\\(\\s*\"[ \\t]*([A-Za-z][^\"\\r\\n]*)\""),
            Regex("\\b(?:text|contentDescription)\\s*=\\s*\"[ \\t]*([A-Za-z][^\"\\r\\n]*)\""),
        )
    }
}
