plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "dev.hearth.launcher"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.hearth.launcher"
        minSdk = 28
        targetSdk = 35
        versionCode = 68
        versionName = "7.5"
    }

    // Fixed debug key in the repo, so every CI build installs as an update over the last one.
    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    // Four apps from one code base: Hearth (the launcher), Glimmer (the island, lock screen),
    // the control center over other apps and Clawd (his widgets for any launcher). They talk through a signature-protected bridge.
    flavorDimensions += "app"
    productFlavors {
        create("hearth") {
            dimension = "app"
            applicationId = "dev.hearth.launcher"
        }
        create("glimmer") {
            dimension = "app"
            applicationId = "dev.hearth.glimmer"
        }
        create("controls") {
            dimension = "app"
            applicationId = "dev.hearth.controls"
        }
        // Clawd on his own: his widgets for any launcher (Samsung's too), his pet, his chat.
        create("clawd") {
            dimension = "app"
            applicationId = "dev.hearth.clawd"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
