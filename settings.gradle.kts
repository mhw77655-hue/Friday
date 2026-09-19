pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven {
            url = uri("https://jitpack.io")
        }
        // Repo-relative, so it resolves on Termux (/sdcard/jarvis-repo/local-repo)
        // AND on a clean CI clone of the committed local-repo/ directory
        // (CI-BUILD-DEBUG-APK AC4: no absolute device path in build config).
        maven {
            url = uri("local-repo")
            metadataSources {
                artifact()
            }
        }
    }
}

rootProject.name = "jarvis"
include(":mobile:app")
