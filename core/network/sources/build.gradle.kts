import io.festicore.buildlogic.catalog
import io.festicore.buildlogic.lib

plugins {
    id("festicore.android.library")
    id("festicore.kotlin.serialization")
}

dependencies {
    val libs = catalog
    api(project(":core:network:interface"))
    implementation(libs.lib("ktor-client-okhttp"))
    implementation(libs.lib("ktor-client-content-negotiation"))
    implementation(libs.lib("ktor-serialization-json"))

    implementation(platform(libs.lib("koin-bom")))
    implementation(libs.lib("koin-core"))

    testImplementation(libs.lib("junit"))
    testImplementation(libs.lib("ktor-client-mock"))
    testImplementation(libs.lib("kotlinx-coroutines-test"))
}
