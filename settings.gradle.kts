@file:Suppress("UnstableApiUsage")

dependencyResolutionManagement {
    repositories {
        google()
        maven(url = "https://maven.aliyun.com/repository/central")
        maven(url = "https://maven.aliyun.com/repository/public")
        maven(url = "https://jitpack.io")
    }
}

pluginManagement {
    repositories {
        google()
        maven(url = "https://maven.aliyun.com/repository/central")
        maven(url = "https://maven.aliyun.com/repository/public")
        maven(url = "https://maven.aliyun.com/repository/gradle-plugin")
    }
}

rootProject.buildFileName = "build.gradle.kts"

include(":app")
include(":tools:changelog-generator")
