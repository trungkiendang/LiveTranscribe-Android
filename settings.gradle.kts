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
        // sherpa-onnx publishes its Android AAR only as a GitHub release asset.
        ivy {
            url = uri("https://github.com/k2-fsa/sherpa-onnx/releases/download/v2.0.1")
            patternLayout { artifact("[artifact]-[revision].[ext]") }
            metadataSources { artifact() }
            content { includeModule("sherpa", "sherpa-onnx-static-link-onnxruntime") }
        }
        // Only the Silero VAD wrapper comes from JitPack.
        maven {
            url = uri("https://jitpack.io")
            content { includeGroup("com.github.gkonovalov.android-vad") }
        }
    }
}

rootProject.name = "LiveN"
include(":app")
