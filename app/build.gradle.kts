plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
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

        vectorDrawables {
            useSupportLibrary = true
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
    }
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
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

    // QR rendering of the findings digest (ReportScreen + last PDF page)
    implementation(libs.zxing.core)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // On-device inference for the team's trained .ptl models
    implementation(libs.pytorch.android.lite)
    implementation(libs.pytorch.torchvision.lite)

    testImplementation(libs.junit)
    // Real org.json for unit tests: the android.jar stub throws on every method, so the
    // acoustic model bundle could not be parsed off-device without it.
    testImplementation("org.json:json:20240303")
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
