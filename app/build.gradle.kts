plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.hiltAndroid)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.plugin.serialization)
    alias(libs.plugins.androidx.baselineprofile)
}

android {
    namespace = "com.markel.flowstate"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.markel.flowstate"
        minSdk = 31 // Android 12
        targetSdk = 36
        versionCode = 1
        versionName = "3.5.2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("debug")
        }
        // The Baseline Profile plugin configures these declared build types with
        // its required profileable/minification flags.
        create("benchmarkRelease") {
            applicationIdSuffix = ".benchmark"
        }
        create("nonMinifiedRelease") {
            applicationIdSuffix = ".benchmark"
        }
    }
    // Baseline Profile 1.5.0 copies the release Kotlin source set into its
    // derived build types. Under AGP 9's legacy DSL the lazy default directory
    // is otherwise copied as the invalid Windows path `provider(?)`.
    sourceSets.named("release") {
        kotlin.directories.clear()
        kotlin.directories.addAll(listOf("src/release/kotlin", "src/release/java"))
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    androidResources {
        generateLocaleConfig = true
    }
}

baselineProfile {
    automaticGenerationDuringBuild = false
    mergeIntoMain = true
    saveInSrc = true
}

dependencies {

    // Modules
    implementation(project(":feature:flow"))
    implementation(project(":feature:calendar"))
    implementation(project(":feature:habits"))
    implementation(project(":feature:settings"))
    implementation(project(":core:data"))
    implementation(project(":core:domain"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:notifications"))
    implementation(project(":core:widgets"))

    // Android Core & Lifecycle
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.profileinstaller)
    implementation(libs.kotlinx.serialization.json)

    // ViewModel
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.android.liquid.glass)
    implementation(libs.kyant.capsule)
    implementation(libs.konfetti.compose)

    // Navigation 3
    implementation(libs.navigation3.runtime)
    implementation(libs.navigation3.ui)
    implementation(libs.lifecycle.viewmodel.navigation3)

    // HILT (Inyección de Dependencias)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // Testing
    testImplementation(libs.junit)
    testImplementation(project(":core:testing"))
    testImplementation(libs.turbine)
    testImplementation(libs.mockk)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    baselineProfile(project(":benchmark"))
}
