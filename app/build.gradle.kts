import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.iagodf.argos"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.iagodf.argos"
        minSdk = 28
        targetSdk = 36
        versionCode = 7
        versionName = "0.2.0"
    }

    // release key lives outside the repo, in keystore.properties
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
            isMinifyEnabled = false
            if (keys != null) signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
}

// Argos Setup travels inside Argos.apk as an asset, so there is still one single apk to download
val setupAssets = layout.buildDirectory.dir("generated/setupAssets")
val bundleSetup = tasks.register<Copy>("bundleSetup") {
    dependsOn(":setup:assembleRelease")
    from(project(":setup").layout.buildDirectory.file("outputs/apk/release/setup-release.apk")) { rename { "argos-setup.apk" } }
    into(setupAssets)
}
android.sourceSets["main"].assets.srcDir(setupAssets)
tasks.named("preBuild") { dependsOn(bundleSetup) }

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.core.ktx)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
}
