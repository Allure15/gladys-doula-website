plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.ridgeline.dock"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.ridgeline.dock"
        minSdk = 30          // head unit runs Android 11
        targetSdk = 30       // keeps legacy storage / background-start behaviour of the unit
        versionCode = 1      // bump on every release: USB updater only installs HIGHER versionCode
        versionName = "1.0.0"
    }
    // DEV signing key committed on purpose so every build (CI or local) has the SAME signature,
    // which the in-app updater requires. Replace with a private keystore before sharing the APK.
    signingConfigs {
        create("dev") { storeFile = file("dev.keystore"); storePassword = "android"; keyAlias = "dev"; keyPassword = "android" }
    }
    buildTypes {
        getByName("debug") { signingConfig = signingConfigs.getByName("dev") }
        getByName("release") { signingConfig = signingConfigs.getByName("dev") }
    }
    buildFeatures { buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-ktx:1.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}
