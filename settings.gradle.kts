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
        maven {
            url = uri("/storage/emulated/0/jarvis-repo/local-repo")
            metadataSources {
                artifact()
            }
        }
    }
}

rootProject.name = "jarvis"
include(":mobile:app")
