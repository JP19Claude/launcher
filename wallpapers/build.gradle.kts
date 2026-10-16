plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// ZENITH Hintergründe: every scene of the Super-Wallpaper as an app of its own (a live wallpaper) – to install, keep and
// pass on one by one. All of them are built from one code base and the very renderer of ZENITH (shared/wallpaper/java),
// and each takes only its own pictures (shared/wallpaper/assets/<scene>), so an app is small. Their numbers are their
// own: raise the version here when one of them changes (CI reads it from here).

// Flavor and package ending, the scene's name in SuperScene, the name in the app's label.
val scenes = listOf(
    Triple("moon", "Moon", "Mond"),
    Triple("earth", "Earth", "Erde"),
    Triple("mars", "Mars", "Mars"),
    Triple("venus", "Venus", "Venus"),
    Triple("jupiter", "Jupiter", "Jupiter"),
    Triple("saturn", "Saturn", "Saturn"),
    Triple("neptune", "Neptune", "Neptun"),
    Triple("pluto", "Pluto", "Pluto"),
    Triple("io", "Io", "Io"),
    Triple("europa", "Europa", "Europa"),
    Triple("ganymede", "Ganymede", "Ganymed"),
    Triple("geometry", "Geometry", "Geometrie"),
)

android {
    namespace = "dev.hearth.wallpaper"
    compileSdk = 35

    defaultConfig {
        minSdk = 28
        targetSdk = 35
        versionCode = 2
        versionName = "1.1"
    }

    // The same fixed debug key as ZENITH's, so every CI build installs as an update over the last one.
    signingConfigs {
        getByName("debug") {
            storeFile = file("../app/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    flavorDimensions += "scene"
    productFlavors {
        scenes.forEach { (id, scene, label) ->
            create(id) {
                dimension = "scene"
                applicationId = "dev.hearth.wallpaper.$id"
                buildConfigField("String", "SCENE", "\"$scene\"")
                manifestPlaceholders["label"] = "ZENITH $label"
            }
        }
    }

    sourceSets {
        getByName("main") {
            java.srcDirs("../shared/wallpaper/java")
            // The credits of the pictures go into every app.
            assets.srcDirs("../shared/wallpaper/assets/credits")
        }
        scenes.forEach { (id, _, _) ->
            val dir = file("../shared/wallpaper/assets/$id")
            if (dir.isDirectory) {
                getByName(id) {
                    assets.srcDirs(dir)
                }
            }
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
        buildConfig = true
    }
}
