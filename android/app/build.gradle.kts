import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

// Release signing, plus the Gemini API key the app talks with (HAVEN_GEMINI_API_KEY,
// from local.properties or the CI secret of that name). Never commit the key.
fun cfg(name: String, default: String = ""): String =
    (localProps.getProperty(name) ?: project.findProperty(name) as String? ?: System.getenv(name) ?: default)

android {
    namespace = "app.haven.companion"
    compileSdk = 36

    defaultConfig {
        applicationId = "app.haven.companion"
        minSdk = 26
        targetSdk = 36
        versionCode = 5
        versionName = "0.5.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "GEMINI_API_KEY", "\"${cfg("HAVEN_GEMINI_API_KEY").trim()}\"")
    }

    sourceSets {
        // The psychoeducation library ships inside the APK (assets/sources/*.md).
        getByName("main").assets.srcDir("../../knowledge")
    }

    signingConfigs {
        create("release") {
            val storeFilePath = cfg("HAVEN_KEYSTORE_FILE")
            if (storeFilePath.isNotBlank()) {
                storeFile = file(storeFilePath)
                storePassword = cfg("HAVEN_KEYSTORE_PASSWORD")
                keyAlias = cfg("HAVEN_KEY_ALIAS")
                keyPassword = cfg("HAVEN_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            resValue("string", "app_name", "Haven (debug)")
        }
        // The build to install and use: not debuggable, so its data can't be read over USB debugging.
        // Signed with the release key when one is configured, otherwise with the build machine's debug key
        // so it can still be sideloaded. (Code shrinking is switched on for the Play Store build.)
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            resValue("string", "app_name", "Haven")
            signingConfig = if (cfg("HAVEN_KEYSTORE_FILE").isNotBlank()) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }

    // CI runs the on-device tests against the release build (-PhavenTestBuildType=release).
    testBuildType = (findProperty("havenTestBuildType") as String?) ?: "debug"

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    androidResources {
        // Sound recordings are read straight from the APK, so they must stay uncompressed.
        noCompress += "ogg"
    }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.security.crypto)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    debugImplementation(libs.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    // On-device smoke tests, run on emulators from Android 8.1 (API 27) to Android 16 (API 36).
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
}
