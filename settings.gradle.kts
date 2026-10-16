pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Hearth"
include(":app")
// ZENITH Smart: an app of its own with its own code (the state of ZENITH 20.5).
include(":smart")
// ZENITH Hintergründe: every scene of the Super-Wallpaper as a live-wallpaper app of its own, one APK each.
include(":wallpapers")
