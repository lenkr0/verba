plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
}

val appVersion: String = providers.gradleProperty("verba.version").get()

/** "1.2.3" -> 10203. Keeps versionCode growing with every release. */
fun versionCodeOf(version: String): Int {
    val parts = version.split(".").map(String::toInt)
    require(parts.size == 3 && parts[1] < 100 && parts[2] < 100) { "Expected x.y.z with y, z < 100: $version" }
    return parts[0] * 10_000 + parts[1] * 100 + parts[2]
}

// Release signing comes from environment variables (GitHub secrets in CI, see
// docs/release-signing.md). Without them the release APK is built unsigned.
val releaseKeystore = providers.environmentVariable("VERBA_KEYSTORE")
val releaseKeystorePassword = providers.environmentVariable("VERBA_KEYSTORE_PASSWORD")
val releaseKeyAlias = providers.environmentVariable("VERBA_KEY_ALIAS")

android {
    namespace = "io.github.lenkr0.verba"
    // Compile against the newest SDK (Compose requires it); behavior follows targetSdk 36
    compileSdk {
        version = release(37) {
            minorApiLevel = 2
        }
    }

    defaultConfig {
        applicationId = "io.github.lenkr0.verba"
        minSdk = 33
        targetSdk = 36
        versionCode = versionCodeOf(appVersion)
        versionName = appVersion
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (releaseKeystore.isPresent) {
            create("release") {
                storeFile = file(releaseKeystore.get())
                storePassword = releaseKeystorePassword.get()
                keyAlias = releaseKeyAlias.get()
                // PKCS12 keystores use the store password for the key as well
                keyPassword = releaseKeystorePassword.get()
            }
        }
    }

    buildTypes {
        debug {
            // Debug and release builds can be installed side by side
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    androidResources {
        // Generates locales_config.xml for the per-app language setting
        generateLocaleConfig = true
    }

    // No Google Play dependency metadata blob in the APK
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = false
        disable += setOf("GradleDependency", "NewerVersionAvailable", "AndroidGradlePluginVersion")
        // targetSdk stays at 36 (Android 16) on purpose: it is what the app is tested on
        disable += "OldTargetApi"
        warningsAsErrors = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.kotlinx.serialization.core)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
}
