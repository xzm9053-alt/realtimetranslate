plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.xzm.realtimetranslate"
    compileSdk {
        version = release(37) {
            minorApiLevel = 0
        }
    }
    buildToolsVersion = "37.0.0"

    defaultConfig {
        applicationId = "com.xzm.realtimetranslate"
        minSdk = 29
        targetSdk = 35
        // CI can override: -PVERSION_CODE=2 -PVERSION_NAME=0.1.1
        versionCode = (findProperty("VERSION_CODE") as String?)?.toIntOrNull() ?: 1
        versionName = (findProperty("VERSION_NAME") as String?) ?: "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // Open-source CI: ship an installable APK with the debug keystore
            // until a real upload key is provided via secrets.
            signingConfig = signingConfigs.getByName("debug")
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
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

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    // Bundled ASR models (assets/models): store the 239MB ONNX uncompressed so
    // aapt2 doesn't waste CPU/memory on a near-incompressible binary and the
    // first-use unpack is a plain copy.
    androidResources {
        noCompress += listOf("onnx")
    }
}

// Guard: a release build without the bundled models would silently ship an APK
// that falls back to downloading. Fail fast instead.
tasks.configureEach {
    if (name == "preReleaseBuild") {
        doFirst {
            val base = file("src/main/assets/models")
            require(base.resolve("sensevoice/model.int8.onnx").isFile) {
                "内置模型缺失：请先运行 scripts/fetch-models.sh 再构建 release。"
            }
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    // Align with MIUIX 0.9.x (Compose Multiplatform 1.11 / Kotlin 2.4)
    val composeBom = platform("androidx.compose:compose-bom:2025.05.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.runtime:runtime")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.9.0")
    implementation("androidx.datastore:datastore-preferences:1.1.3")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.savedstate:savedstate-ktx:1.3.0")

    implementation("top.yukonga.miuix.kmp:miuix-ui:0.9.3")
    implementation("top.yukonga.miuix.kmp:miuix-preference:0.9.3")
    implementation("top.yukonga.miuix.kmp:miuix-squircle:0.9.3")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")

    // Local ASR: sherpa-onnx (SenseVoice + Silero VAD). Verify latest at https://jitpack.io/#k2-fsa/sherpa-onnx
    implementation("com.github.k2-fsa:sherpa-onnx:1.13.4")

    // Screen-region OCR: ML Kit Text Recognition v2 (BUNDLED models — fully offline,
    // no Google Play Services, works on Chinese-market devices).
    implementation("com.google.mlkit:text-recognition-chinese:16.0.1")
    implementation("com.google.mlkit:text-recognition-japanese:16.0.1")
    implementation("com.google.mlkit:text-recognition-korean:16.0.1")
    // Task.await() for ML Kit's async recognizer inside our coroutines loop.
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.10.2")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
