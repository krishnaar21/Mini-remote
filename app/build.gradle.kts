plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// CI passes a stable keystore (from GitHub secrets) so every APK has the same signature and
// installs over the previous one. Without it, Gradle's per-machine debug key is used.
val signingKeystore: String? = System.getenv("SIGNING_KEYSTORE_PATH")?.takeIf { it.isNotBlank() && file(it).exists() }
// Each CI run gets a higher versionCode, so Android accepts it as an update.
val ciRun: Int = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1

android {
    namespace = "com.krishna.miniremote"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.krishna.miniremote"
        minSdk = 26
        targetSdk = 35
        versionCode = ciRun
        versionName = "0.2.$ciRun"
    }

    signingConfigs {
        if (signingKeystore != null) create("stable") {
            storeFile = file(signingKeystore)
            storePassword = System.getenv("SIGNING_PASSWORD")
            keyAlias = "miniremote"
            keyPassword = System.getenv("SIGNING_PASSWORD")
        }
    }

    buildTypes {
        debug {
            if (signingKeystore != null) signingConfig = signingConfigs.getByName("stable")
        }
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}
