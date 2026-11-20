import io.festicore.buildlogic.catalog
import io.festicore.buildlogic.lib

plugins {
    id("festicore.android.library")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    buildFeatures {
        compose = true
    }
}

dependencies {
    val libs = catalog
    implementation(platform(libs.lib("compose-bom")))
    implementation(libs.lib("compose-ui"))
    implementation(libs.lib("compose-material3"))
    implementation(libs.lib("compose-ui-tooling-preview"))
    debugImplementation(libs.lib("compose-ui-tooling"))

    implementation(platform(libs.lib("koin-bom")))
    implementation(libs.lib("koin-android"))
    implementation(libs.lib("koin-compose-viewmodel"))

    implementation(libs.lib("orbit-core"))
    implementation(libs.lib("orbit-viewmodel"))
    implementation(libs.lib("orbit-compose"))

    implementation(libs.lib("navigation3-runtime"))
    implementation(libs.lib("navigation3-ui"))
    implementation(libs.lib("lifecycle-viewmodel-navigation3"))
    implementation(libs.lib("kotlinx-serialization-json"))

    implementation(project(":core:logger"))

    testImplementation(libs.lib("junit"))
    testImplementation(libs.lib("orbit-test"))
    testImplementation(libs.lib("kotlinx-coroutines-test"))
}
