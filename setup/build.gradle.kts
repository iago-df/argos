import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

/**
 * Argos Setup: a small helper that ships inside Argos.apk. it pairs with the phone's own wireless debugging,
 * runs the two adb commands and removes itself. it is the only part of Argos with the internet permission,
 * and it only talks to the phone itself
 */
android {
    namespace = "com.iagodf.argos.setup"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.iagodf.argos.setup"
        minSdk = 30
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        // phones and the emulator. it travels inside Argos.apk, so every megabyte counts
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64") }
    }

    val keys = rootProject.file("keystore.properties").takeIf { it.exists() }?.let { f -> Properties().apply { f.inputStream().use { load(it) } } }
    signingConfigs {
        if (keys != null) create("release") {
            storeFile = rootProject.file(keys.getProperty("storeFile"))
            storePassword = keys.getProperty("storePassword")
            keyAlias = keys.getProperty("keyAlias")
            keyPassword = keys.getProperty("keyPassword")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = if (keys != null) signingConfigs.getByName("release") else signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.core.ktx)
    implementation(libs.libadb)
    implementation(libs.sun.security)
    implementation(libs.conscrypt)
}
