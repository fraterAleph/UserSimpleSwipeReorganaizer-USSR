pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
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

rootProject.name = "ussr"
include(":core")

// :app needs the Android SDK and the Google Maven repository. Including it unconditionally
// would break `./gradlew :core:test` on a machine that has neither, which is exactly where
// the analysis logic gets exercised, so the Android module opts in when an SDK is present.
val androidSdkAvailable = file("local.properties").exists() ||
    System.getenv("ANDROID_HOME") != null ||
    System.getenv("ANDROID_SDK_ROOT") != null

if (androidSdkAvailable) {
    include(":app")
} else {
    logger.lifecycle("No Android SDK found - skipping :app. Core analysis still builds and tests.")
}
