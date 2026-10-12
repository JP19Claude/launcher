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
        versionCode = 185
        // Small updates (fixes, little things) +0.1, big ones (many features, redesign) +0.5.
        versionName = "20.5"
        // Hearth One (everything in one app) says so; the separate apps don't.
        buildConfigField("boolean", "ALL_IN_ONE", "false")
        // ZENITH Smart: the one app that is "Be Smarter" from the start, with Smart Launcher's settings.
        buildConfigField("boolean", "SMART", "false")
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
    // Clawd (his widgets for any launcher) and the Duftschrank (his perfume advisor). They talk
    // through a signature-protected bridge.
    // (The separate control center app is discontinued; Hearth keeps its own.)
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
        // Clawd on his own: his widgets for any launcher (Samsung's too), his pet, his chat.
        create("clawd") {
            dimension = "app"
            applicationId = "dev.hearth.clawd"
        }
        // Duftschrank: Clawd's perfume advisor on its own – your perfumes, today's choice for the
        // weather, a widget for any launcher.
        create("duft") {
            dimension = "app"
            applicationId = "dev.hearth.duft"
        }
        // Hearth One: Hearth with Glimmer and Clawd built in, one app. The same package as
        // Hearth, so it installs over it as an update and keeps everything.
        create("one") {
            dimension = "app"
            applicationId = "dev.hearth.launcher"
            buildConfigField("boolean", "ALL_IN_ONE", "true")
        }
        // ZENITH Smart: ZENITH as the Smart Launcher 6 look-alike from the start – "Be Smarter" on, the settings
        // in Smart Launcher's style with ClaudeOS and ZENITH at their core. An app of its own next to ZENITH
        // (its own package, its own settings and data), with its own update line.
        create("smart") {
            dimension = "app"
            applicationId = "dev.hearth.smart"
            buildConfigField("boolean", "ALL_IN_ONE", "true")
            buildConfigField("boolean", "SMART", "true")
        }
    }

    // Clawd's widgets and his app's screen: in the Clawd app and built into Hearth One.
    sourceSets {
        getByName("clawd") {
            java.srcDirs("src/clawdShared/java")
            res.srcDirs("src/clawdShared/res")
        }
        getByName("one") {
            java.srcDirs("src/clawdShared/java")
            res.srcDirs("src/clawdShared/res")
        }
        // ZENITH Smart is ZENITH's manifest and code with a name of its own (src/smart/res).
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
