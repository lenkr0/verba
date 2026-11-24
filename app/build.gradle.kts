import java.net.URI
import java.security.MessageDigest
import java.util.zip.ZipFile

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

room {
    // Exported schemas are committed: they document the data model and are
    // the base for migration tests
    schemaDirectory("$projectDir/schemas")
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

// Text recognition runs on ncnn (Tencent, BSD-3-Clause): its official prebuilt
// Android release, pinned by version and SHA-256, kept in Gradle's cache.
val ncnnVersion = "20261020"
val ncnnSha256 = "62a9edb493ceb42a5c9cbf2d3a79b3f44bff06c625134ffb64d183dcc42c25fc"
val ncnnHome: File = gradle.gradleUserHomeDir.resolve("caches/verba/ncnn-$ncnnVersion")
val ncnnRoot: File = ncnnHome.resolve("ncnn-$ncnnVersion-android")

// The text recognition models: the app downloads them at runtime; instrumented
// tests get the same pack built into the test APK.
val ocrPack = "verba-ocr-ppocrv5-mobile-1.zip"
val ocrPackSha256 = "f7248d6ccef1fc1008632b2412819447f47d0de6191f64fab8a85c7019e2bf55"
val ocrTestAssets: Provider<Directory> = layout.buildDirectory.dir("generated/ocr-test-assets")

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

        ndk {
            // Phones and e-readers, 64 and 32 bit
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
        externalNativeBuild {
            cmake {
                arguments += listOf("-DNCNN_ROOT=${ncnnRoot.invariantSeparatorsPath}", "-DANDROID_STL=c++_static")
            }
        }
    }

    ndkVersion = "30.0.16248370"
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "4.1.2"
        }
    }

    sourceSets {
        getByName("androidTest").assets.directories += ocrTestAssets.get().asFile.path
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
            // Emulators (tests, CI); ncnn for x86_64 alone is 20 MB, so not in the release
            ndk { abiFilters += "x86_64" }
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
        // Compressed in the APK (unpacked on install): the text recognition library
        // shrinks from 7.6 to 3.8 MB of download
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

/** Downloads [url] to [target] once and checks its SHA-256; unzips it into [unzipTo] if given. */
fun TaskContainer.registerDownload(name: String, url: String, sha256: String, target: File, unzipTo: File? = null) =
    register(name) {
        outputs.file(target)
        doLast {
            if (target.isFile && (unzipTo == null || unzipTo.resolve(".done").isFile)) return@doLast
            target.parentFile.mkdirs()
            val partial = File(target.path + ".part")
            URI(url).toURL().openStream().use { input -> partial.outputStream().use { input.copyTo(it) } }
            val digest = MessageDigest.getInstance("SHA-256")
            partial.inputStream().use { input ->
                val buffer = ByteArray(1 shl 16)
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    digest.update(buffer, 0, n)
                }
            }
            val actual = digest.digest().joinToString("") { "%02x".format(it) }
            check(actual == sha256) { "Checksum mismatch for $url: $actual" }
            partial.renameTo(target)
            if (unzipTo != null) {
                ZipFile(target).use { zip ->
                    for (entry in zip.entries()) {
                        val out = unzipTo.resolve(entry.name)
                        check(out.canonicalPath.startsWith(unzipTo.canonicalPath)) { "Bad zip entry ${entry.name}" }
                        if (entry.isDirectory) {
                            out.mkdirs()
                        } else {
                            out.parentFile.mkdirs()
                            zip.getInputStream(entry).use { input -> out.outputStream().use { input.copyTo(it) } }
                        }
                    }
                }
                unzipTo.resolve(".done").writeText(sha256)
            }
        }
    }

val fetchNcnn = tasks.registerDownload(
    "fetchNcnn",
    "https://github.com/Tencent/ncnn/releases/download/$ncnnVersion/ncnn-$ncnnVersion-android.zip",
    ncnnSha256,
    ncnnHome.resolve("ncnn-$ncnnVersion-android.zip"),
    unzipTo = ncnnHome,
)

val fetchOcrTestPack = tasks.registerDownload(
    "fetchOcrTestPack",
    "https://github.com/lenkr0/verba/releases/download/ocr-models/$ocrPack",
    ocrPackSha256,
    ocrTestAssets.get().asFile.resolve("ocr/$ocrPack"),
)

tasks.configureEach {
    if (name.startsWith("configureCMake") || name.startsWith("buildCMake")) dependsOn(fetchNcnn)
    if (name.startsWith("merge") && name.endsWith("AndroidTestAssets")) dependsOn(fetchOcrTestPack)
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
    implementation(libs.kotlinx.serialization.json)
    // Pinned so the app and its tests (kotlinx-coroutines-test) use the same version
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.kotlinx.coroutines.test)
}
