plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.minhquanghp86.frigatenvr"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.minhquanghp86.frigatenvr"
        minSdk = 26
        targetSdk = 36
        versionCode = (System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1)
        versionName = System.getenv("APP_VERSION_NAME")?.takeIf { it.isNotBlank() } ?: "2.0.2"
        vectorDrawables { useSupportLibrary = true }
        ndk {
            // WebRTC chỉ cần ABI của điện thoại — bỏ x86 để APK nhẹ hơn.
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }

    // Release packaging MUST have a real keystore: fail loudly instead of
    // silently producing a debug-signed "release" APK.
    val releaseKeystoreFile = rootProject.file("keystore/frigate-release.jks")
    val wantsReleasePackage = gradle.startParameter.taskNames.any { name ->
        listOf(
            "assembleRelease",
            "bundleRelease",
            "packageRelease",
            "installRelease",
            "publishRelease",
        ).any { name.contains(it) }
    }
    if (wantsReleasePackage && !releaseKeystoreFile.exists()) {
        throw GradleException(
            "Không có keystore/frigate-release.jks — không thể build bản release đúng key. " +
                "Đặt keystore vào keystore/ (không commit) hoặc để CI build bằng GitHub Secrets.",
        )
    }

    signingConfigs {
        // Release signing key comes ONLY from the local keystore file
        // (never committed — see keystore/README.md) plus env vars.
        // No default passwords: a missing password must fail loudly
        // instead of silently falling back to a well-known value.
        // CI restores the keystore from the KEYSTORE_BASE64 secret.
        create("release") {
            val ks = rootProject.file("keystore/frigate-release.jks")
            if (ks.exists()) {
                val storePass = System.getenv("KEYSTORE_PASSWORD")
                    ?: throw GradleException(
                        "KEYSTORE_PASSWORD is not set. " +
                            "Set it in local.properties / environment or GitHub Secrets.",
                    )
                val keyPass = System.getenv("KEY_PASSWORD")
                    ?: throw GradleException("KEY_PASSWORD is not set.")
                storeFile = ks
                storePassword = storePass
                keyAlias = System.getenv("KEY_ALIAS") ?: "frigate"
                keyPassword = keyPass
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            signingConfig = if (rootProject.file("keystore/frigate-release.jks").exists()) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.03")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.navigation:navigation-compose:2.8.2")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    // Encrypted storage for passwords / tokens (Android Keystore, AES256-GCM).
    // Plain DataStore must never hold secrets — see data/SecureStorage.kt.
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.compose.ui:ui-tooling-preview")

    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-kotlinx-serialization:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    implementation("io.coil-kt:coil-compose:2.7.0")

    implementation("androidx.media3:media3-exoplayer:1.11.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.11.1")
    implementation("androidx.media3:media3-ui:1.11.1")
    implementation("androidx.media3:media3-datasource-okhttp:1.11.1")
    // FFmpeg software decoders: cần cho camera H.265/HEVC (lỗi DECODER_INIT_FAILED) và codec lạ.
    implementation("io.github.anilbeesetti:nextlib-media3ext:1.11.1-0.17.0")

    // WebRTC (WHEP) để xem live fps gốc qua proxy có xác thực của Frigate.
    implementation("io.github.webrtc-sdk:android:137.7151.05")
    // Google Cast: phát live lên TV/Chromecast trong LAN (Frigate API không có endpoint cast).
    implementation("com.google.android.gms:play-services-cast-framework:21.4.0")

    testImplementation("junit:junit:4.13.2")
}
