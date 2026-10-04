plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "app.ironlog.personal"
    compileSdk = 35
    defaultConfig {
        applicationId = "app.ironlog.personal"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    ksp { arg("room.schemaLocation", "$projectDir/schemas") }
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

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.bundles.compose)
    implementation(libs.activity.compose)
    implementation(libs.androidx.core)
    implementation(libs.navigation.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.viewmodel.ktx)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    implementation(libs.datastore)
    implementation(libs.serialization.json)
    implementation(libs.coroutines.android)
    implementation(libs.code.scanner)
    // The scanner brings an old Fragment; 1.3+ is required for registerForActivityResult.
    implementation(libs.fragment)
    ksp(libs.room.compiler)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.junit)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.room.testing)
    testImplementation(libs.androidx.test.core)
}
