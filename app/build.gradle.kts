plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.smartlease.edge"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.smartlease.edge"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0-hackathon"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            // PyTorch ships libpytorch_jni_lite.so per-ABI at ~40-58 MB each; bundling all
            // four takes the APK to ~295 MB. The demo handset (iQOO 15) is arm64-v8a, so
            // that is all a shipping build carries.
            //
            // The Android emulator on an x86 host is x86_64, and an APK without a matching
            // ABI fails to install with INSTALL_FAILED_NO_MATCHING_ABIS. Rather than editing
            // this file to test locally, pass the extra ABI on the command line:
            //     ./gradlew :app:assembleDebug -PextraAbis=x86_64
            abiFilters += "arm64-v8a"
            val extraAbis = (project.findProperty("extraAbis") as String?)
                ?.split(",")
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() }
                ?: emptyList()
            abiFilters += extraAbis
        }

        // The backend's shared API key. NEVER a literal in this file or in source: it comes
        // from `-PsmartleaseApiKey=...`, from gradle.properties (git-ignored), or from the
        // SMARTLEASE_API_KEY environment variable, in that order.
        //
        // An empty value is the honest default and the safe one. It means this build has no
        // credential, every /reports call it makes would come back 401, and sync stays off
        // (see SyncConfig.syncEnabled, which is opt-in anyway).
        //
        // Worth being blunt about what this is NOT: a key compiled into an APK is extractable
        // by anyone willing to unzip it. It raises the cost of anonymous abuse; it is not user
        // authentication. See server/SECURITY.md.
        val smartleaseApiKey = (project.findProperty("smartleaseApiKey") as String?)
            ?: System.getenv("SMARTLEASE_API_KEY")
            ?: ""
        buildConfigField("String", "SMARTLEASE_API_KEY", "\"$smartleaseApiKey\"")
    }

    testOptions {
        unitTests {
            isReturnDefaultValues = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        // Needed for BuildConfig.DEBUG, which gates the training-data capture screen out
        // of release builds. AGP does not generate BuildConfig unless asked.
        buildConfig = true
    }

    androidResources {
        // .ptl model files are already compressed archives; letting aapt re-compress them
        // breaks LiteModuleLoader's ability to mmap them straight out of the APK.
        noCompress += "ptl"
        noCompress += "litertlm"
    }
}

// The LiteRT-LM AAR is built against Kotlin 2.4.0 and needs `kotlin-stdlib:2.4.0` (and its
// `kotlin-reflect:2.4.0`) at RUNTIME: the reflect artifact references
// `kotlin.jvm.internal.KotlinGenericDeclaration`, a class that exists ONLY in the 2.4.0 stdlib
// (verified: absent in 2.3.0, present in 2.4.0). Forcing the stdlib down to an older version
// -- an earlier attempt here -- compiled fine but crashed at model load with
// NoClassDefFoundError for that class. So we align UP: let the whole graph use the 2.4.0
// Kotlin runtime, and rely on `-Xskip-metadata-version-check` below so this project's Kotlin
// 2.1.0 compiler tolerates reading the newer 2.4.0 library metadata. A full toolchain bump to
// Kotlin 2.4 is the tidier fix, but KSP (which Room needs) has no 2.4.0 release yet, so that
// path is blocked; pinning the runtime libraries up is the change that actually works today.
configurations.all {
    resolutionStrategy {
        force("org.jetbrains.kotlin:kotlin-stdlib:2.4.0")
        force("org.jetbrains.kotlin:kotlin-stdlib-common:2.4.0")
        force("org.jetbrains.kotlin:kotlin-reflect:2.4.0")
    }
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xskip-metadata-version-check")
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.foundation)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    debugImplementation(libs.androidx.ui.tooling)

    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)

    implementation(libs.mlkit.text.recognition)
    implementation(libs.mlkit.barcode.scanning)

    // QR rendering of the findings digest (ReportScreen + last PDF page)
    implementation(libs.zxing.core)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.sqlcipher)
    implementation(libs.androidx.sqlite.ktx)

    implementation(libs.arcore)
    implementation(libs.sceneview)
    implementation(libs.arsceneview)

    // On-device inference for the team's trained .ptl models
    implementation(libs.pytorch.android.lite)
    implementation(libs.pytorch.torchvision.lite)

    // On-device report narration (com.smartlease.edge.narration). Runtime only: the Gemma
    // weights are not in the APK and are not redistributed with it -- see docs/GEMMA_SETUP.md.
    // The app builds, installs and produces reports with no model file present; narration
    // falls back to rule-based templating and says so on the page.
    implementation(libs.mediapipe.tasks.genai)
    // LiteRT-LM runtime: loads the .litertlm container (Gemma 4 / Gemma 3n) that the MediaPipe
    // runtime above cannot read. Same "weights are not in the APK" guarantee -- runtime only.
    // Its Kotlin 2.4.0 stdlib/reflect are aligned up by the resolutionStrategy above.
    implementation(libs.litertlm.android)

    // Backend sync client (com.smartlease.edge.sync) -- uploads a digest + metadata only.
    implementation(libs.retrofit.core)
    implementation(libs.retrofit.kotlinx.serialization.converter)
    implementation(libs.okhttp.core)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    // Real org.json for unit tests: the android.jar stub throws on every method, so the
    // acoustic model bundle could not be parsed off-device without it.
    testImplementation("org.json:json:20240303")
    // Pure-JVM HTTP fixture for sync client tests -- no device, no real network.
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    implementation("com.itextpdf:itextg:5.5.10")
    implementation("com.google.code.gson:gson:2.10.1")
}