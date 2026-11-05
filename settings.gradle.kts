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
        // Only the Silero VAD wrapper comes from JitPack.
        maven {
            url = uri("https://jitpack.io")
            content { includeGroup("com.github.gkonovalov.android-vad") }
        }
    }
}

rootProject.name = "LiveN"
include(":app")
