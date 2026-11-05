@file:Suppress("UnstableApiUsage")


buildscript {
    repositories {
        google()
        maven(url = "https://maven.aliyun.com/repository/central")
        maven(url = "https://maven.aliyun.com/repository/public")
    }
    dependencies {
        classpath("com.google.devtools.ksp:symbol-processing-gradle-plugin:2.3.6")
    }
}

plugins {
    alias(libs.plugins.androidGradlePlugin)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.parcelize)
}
apply(plugin = "com.google.devtools.ksp")

val appName = "Magen Audio"

// Signing key and secrets
val keystoreFile = System.getenv("RELEASE_KEYSTORE_FILE") ?: project.findProperty("release.keystoreFile")?.toString()
val keystorePassword = System.getenv("RELEASE_KEYSTORE_PASSWORD") ?: project.findProperty("release.keystorePassword")?.toString()
val keyAliasValue = System.getenv("RELEASE_KEY_ALIAS") ?: project.findProperty("release.keyAlias")?.toString()
val keyPasswordValue = System.getenv("RELEASE_KEY_PASSWORD") ?: project.findProperty("release.keyPassword")?.toString()
val isKeystoreAvailable = !keystoreFile.isNullOrEmpty() && !keystorePassword.isNullOrEmpty()

android {
    compileSdk {
        version = release(37) {
            minorApiLevel = 0
        }
    }
    buildToolsVersion = "37.0.0"
    namespace = "player.phonograph"

    defaultConfig {
        minSdk = 26
        targetSdk = 36

        applicationId = "player.phonograph.plus"
        versionCode = 29100
        versionName = "2.9.1"

        proguardFiles(File("proguard-rules-base.pro"), File("proguard-rules-app.pro"))

        manifestPlaceholders["GIT_COMMIT_HASH"] = "-"
    }

    buildFeatures {
        resValues = true
        buildConfig = true
        viewBinding = true
        compose = true
    }

    signingConfigs {
        create("release") {
            if (isKeystoreAvailable) {
                storeFile = file(keystoreFile!!)
                storePassword = keystorePassword
                keyAlias = keyAliasValue
                keyPassword = keyPasswordValue
                // Enable v1+v2+v3 signing so APK installs on Android 14 (Honor X6c NIC-LX2)
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
            } else {
                print("No signing key configured!")
            }
        }
    }

    buildTypes {
        getByName("release") {
            // signing
            if (isKeystoreAvailable) signingConfig = signingConfigs.getByName("release")

            // shrink
            isMinifyEnabled = false
            isShrinkResources = false

            // git revision tracker
            manifestPlaceholders["GIT_COMMIT_HASH"] = "n/a"
            vcsInfo.include = false // we have our means
        }
        create("intermediateRelease") {            // Variants for key rotation
            initWith(getByName("release"))
            signingConfig = null
        }
        getByName("debug") {
            // signing as well
            if (isKeystoreAvailable) signingConfig = signingConfigs.getByName("release")

            // package name
            applicationIdSuffix = ".debug"
        }
    }

    flavorDimensions += listOf("target", "channel")
    productFlavors {
        // Stable or LTS release
        create("stable") {
            dimension = "channel"

            resValue("string", "app_name", appName)
        }
        // Preview release
        create("preview") {
            dimension = "channel"
            matchingFallbacks.add("stable")

            resValue("string", "app_name", "$appName Preview")
            applicationIdSuffix = ".preview"

            isDefault = true
        }
        // Early Access Preview / Next Generation Preview release
        create("next") {
            dimension = "channel"
            matchingFallbacks.add("stable")

            resValue("string", "app_name", "$appName EAP")
            applicationIdSuffix = ".eap"
        }
        // F-droid special variant, base on Stable, for reproducibility
        create("fdroid") {
            dimension = "channel"
            matchingFallbacks.add("stable")

            resValue("string", "app_name", appName)
        }
        // for checkout to locate a bug and ci etc.
        create("checkout") {
            dimension = "channel"
            matchingFallbacks.add("stable")

            resValue("string", "app_name", "$appName Checkout")
            applicationIdSuffix = ".checkout"

            minSdk = 24

            manifestPlaceholders["GIT_COMMIT_HASH"] = "n/a"
        }

        create("modern") {
            dimension = "target"

            isDefault = true
        }
        create("legacy") {
            dimension = "target"
            matchingFallbacks.add("modern")

            targetSdk = 28
            minSdk = 24
        }

    }

    lint {
        abortOnError = false
        disable.add("MissingTranslation")
        disable.add("InvalidPackage")

        checkReleaseBuilds = false
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

}

kotlin {
    jvmToolchain(21)
}

project.extensions.configure<com.google.devtools.ksp.gradle.KspExtension>("ksp") {
    arg("room.schemaLocation", "$projectDir/schemas")
    // arg("room.incremental", "true")
}

androidComponents {

    val moduleName = project.name
    onVariants(selector().all()) { variant ->
        // Rename
        for (output in variant.outputs) {
            val outputImpl = output as? com.android.build.api.variant.impl.VariantOutputImpl ?: continue
            val origin = outputImpl.outputFileName.get()
            val new = origin.replace(moduleName, "MagenAudio-${output.versionName.get()}")
            outputImpl.outputFileName.set(new)
        }
    }

    // (publish tasks removed for build compatibility)
}

// (androidPublish removed)

tasks.whenTaskAdded {
    // disable due to reproducible build issues
    if (name.contains("Fdroid") && name.contains("ArtProfile")) {
        enabled = false
    }
}

dependencies {

    implementation(libs.androidx.core)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.fragment)
    implementation(libs.androidx.lifecycle.runtime)

    implementation(libs.androidx.annotation)
    implementation(libs.androidx.preference)

    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.viewpager2)

    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.percentlayout)
    implementation(libs.androidx.swiperefreshlayout)

    implementation(libs.androidx.media)
    implementation(libs.androidx.cardview)
    implementation(libs.androidx.palette)
    implementation(libs.bundles.androidx.room)
    implementation(libs.bundles.androidx.datastore)
    "ksp"(libs.androidx.room.compiler)

    testImplementation("junit:junit:4.13.2")

    implementation(libs.google.material)

    implementation(libs.bundles.compose)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.bundles.themeUtil)

    implementation(libs.storageUtilities)
    implementation(libs.musicMetadataSource)
    implementation(libs.menuDsl)
    implementation(libs.seekArc)
    implementation(libs.slidingUpPanel)

    implementation(libs.composeMaterialDialogs)

    implementation(libs.okhttp3)
    implementation(libs.retrofit2)
    implementation(libs.coil)
    implementation(libs.coil.compose)
    implementation(libs.koin)

    implementation(libs.kotlinx.coroutines)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.licensesdialog)
    implementation(libs.jaudiotagger)
    implementation(libs.observablescrollview)
    implementation(libs.appIntro)
    implementation(libs.advrecyclerview)
    implementation(libs.recyclerviewFastscroll)
    implementation(libs.composeReorderable)
    implementation(libs.composePipette)
    implementation(libs.statusBarLyricsApi)
    implementation(libs.lyricsGetterAPi)

}
