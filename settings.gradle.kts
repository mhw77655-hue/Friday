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
        // Repo-relative so the committed local-repo directory resolves from
        // the Android app checkout and from a clean CI clone.
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
