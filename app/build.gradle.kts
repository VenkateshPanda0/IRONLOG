import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.room)
}

// One codebase for Android and iOS: shared code lives in src/commonMain, platform code in
// src/androidMain and src/iosMain. The iOS app (iosApp/) links the IronlogKit framework.
kotlin {
    androidTarget { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }
    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "IronlogKit"
            isStatic = true
        }
    }
    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(libs.mp.material.icons.extended)
            implementation(libs.mp.navigation.compose)
            implementation(libs.mp.lifecycle.viewmodel.compose)
            implementation(libs.mp.lifecycle.runtime.compose)
            implementation(libs.room.runtime)
            implementation(libs.datastore.core)
            implementation(libs.serialization.json)
            implementation(libs.coroutines.core)
            implementation(libs.datetime)
        }
        // Android has SQLite built in; iOS ships its own copy for Room.
        iosMain.dependencies { implementation(libs.sqlite.bundled) }
        androidMain.dependencies {
            implementation(project.dependencies.platform(libs.androidx.compose.bom))
            implementation(libs.compose.ui.tooling.preview)
            implementation(libs.activity.compose)
            implementation(libs.androidx.core)
            implementation(libs.lifecycle.viewmodel.ktx)
            implementation(libs.room.ktx)
            implementation(libs.datastore)
            implementation(libs.coroutines.android)
            implementation(libs.code.scanner)
            // The scanner brings an old Fragment; 1.3+ is required for registerForActivityResult.
            implementation(libs.fragment)
            // Reads steps and sleep from Health Connect (Android's on-device health data store).
            implementation(libs.health.connect)
        }
        getByName("androidUnitTest").dependencies {
            implementation(project.dependencies.platform(libs.androidx.compose.bom))
            implementation(libs.compose.ui.test.junit4)
            implementation(libs.roborazzi)
            implementation(libs.roborazzi.compose)
            implementation(libs.roborazzi.junit)
            implementation(libs.junit)
            implementation(libs.robolectric)
            implementation(libs.room.testing)
            implementation(libs.androidx.test.core)
        }
    }
}

room { schemaDirectory("$projectDir/schemas") }

android {
    namespace = "app.ironlog.personal"
    compileSdk = 35
    defaultConfig {
        applicationId = "app.ironlog.personal"
        minSdk = 26
        targetSdk = 35
        // CI builds number themselves so each published APK is newer than the last.
        versionCode = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
        versionName = "0.1." + (System.getenv("GITHUB_RUN_NUMBER") ?: "0")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    signingConfigs {
        // CI signs with a key from repository secrets when one is configured, so every published
        // APK installs over the previous one. Without it the machine's own debug key is used.
        getByName("debug") {
            System.getenv("IRONLOG_KEYSTORE")?.takeIf { file(it).isFile }?.let {
                storeFile = file(it)
                storePassword = System.getenv("IRONLOG_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("IRONLOG_KEY_ALIAS")
                keyPassword = System.getenv("IRONLOG_KEY_PASSWORD")
            }
        }
    }
    buildTypes {
        release {
            // R8 removes unused code and resources and obfuscates the rest.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true }
    // Exported Room schemas are needed by MigrationTestHelper in Robolectric tests.
    sourceSets { getByName("debug").assets.srcDir("$projectDir/schemas") }
    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            it.systemProperty("roborazzi.test.record", "true")
            it.systemProperty("robolectric.pixelCopyRenderMode", "hardware")
        }
    }
}

// Unit tests run against the debug variant only: Room migration tests need the schema files and
// Compose UI tests need the test activity from ui-test-manifest, both deliberately kept out of the
// release APK. The release build is checked by assembleRelease and lintVitalRelease instead.
androidComponents {
    beforeVariants(selector().withBuildType("release")) { it.enableUnitTest = false }
}

dependencies {
    listOf("kspAndroid", "kspIosArm64", "kspIosSimulatorArm64").forEach { add(it, libs.room.compiler) }
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)
}
