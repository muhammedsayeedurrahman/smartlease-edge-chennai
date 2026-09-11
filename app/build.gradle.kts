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
            // four takes the APK to ~295 MB. The iQOO 15 (and every demo handset we use) is
            // arm64-v8a, so ship only that. Add "x86_64" here if you need an x86 emulator.
            abiFilters += "arm64-v8a"
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
    }

    androidResources {
        // .ptl model files are already compressed archives; letting aapt re-compress them
        // breaks LiteModuleLoader's ability to mmap them straight out of the APK.
        noCompress += "ptl"
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

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // On-device inference for the team's trained .ptl models
    implementation(libs.pytorch.android.lite)
    implementation(libs.pytorch.torchvision.lite)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
