import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Public configuration comes from android/local.properties, Gradle properties
// or environment variables. None of these are secrets: the Supabase anon key is
// designed to ship in clients. The OpenAI key NEVER goes here.
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

fun cfg(name: String, default: String = ""): String =
    (localProps.getProperty(name) ?: project.findProperty(name) as String? ?: System.getenv(name) ?: default)

android {
    namespace = "app.haven.companion"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.haven.companion"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        buildConfigField("String", "BACKEND_URL", "\"${cfg("HAVEN_BACKEND_URL", "http://10.0.2.2:8000")}\"")
        buildConfigField("String", "SUPABASE_URL", "\"${cfg("HAVEN_SUPABASE_URL")}\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"${cfg("HAVEN_SUPABASE_ANON_KEY")}\"")
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
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            resValue("string", "app_name", "Haven")
            if (cfg("HAVEN_KEYSTORE_FILE").isNotBlank()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

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
    implementation(libs.webrtc)
    debugImplementation(libs.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
