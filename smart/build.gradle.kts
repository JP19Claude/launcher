plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// ZENITH Smart: an app of its own, with its own code – the state of ZENITH 20.5 (the Smart Launcher
// look-alike, its settings in Smart's style), kept as it was. ZENITH's own "Be Smarter" (app/) goes on
// without it. Its package, settings, data and update line are its own (versions.json, "smart").
android {
    namespace = "dev.hearth.launcher"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.hearth.smart"
        minSdk = 28
        targetSdk = 35
        // Its own numbers. A hotfix keeps the version and raises the build; CI reads both from here.
        versionCode = 190
        versionName = "20.5"
        buildConfigField("boolean", "ALL_IN_ONE", "true")
        buildConfigField("boolean", "SMART", "true")
    }

    // The same fixed debug key as ZENITH's, so the apps of the family trust each other's bridge.
    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    flavorDimensions += "app"
    productFlavors {
        create("smart") {
            dimension = "app"
        }
    }

    // ZENITH's manifest and code (Clawd's widgets and screen built in) with a name of its own (src/smart/res).
    sourceSets {
        getByName("smart") {
            manifest.srcFile("src/one/AndroidManifest.xml")
            java.srcDirs("src/clawdShared/java")
            res.srcDirs("src/clawdShared/res")
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
        buildConfig = true
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
    // Shizuku: system switches (Wi-Fi, Bluetooth, mobile data …) with ADB rights, no root.
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")
    // Glimmer Drop: the fast direct connection between two phones held together.
    implementation("com.google.android.gms:play-services-nearby:19.3.0")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
