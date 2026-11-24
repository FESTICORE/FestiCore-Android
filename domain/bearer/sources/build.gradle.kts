import io.festicore.buildlogic.catalog
import io.festicore.buildlogic.lib

plugins {
    id("festicore.android.library")
    id("festicore.kotlin.serialization")
}

dependencies {
    val libs = catalog
    api(project(":domain:bearer:interface"))
    implementation(project(":service:apiendpoint"))
    implementation(project(":service:auth:interface"))
    implementation(project(":service:device:interface"))
    implementation(project(":service:pack:interface"))
    implementation(project(":core:utility"))
    implementation(project(":core:logger"))
    implementation(project(":domain:auth:interface"))

    implementation(platform(libs.lib("koin-bom")))
    implementation(libs.lib("koin-core"))

    testImplementation(project(":core:network:sources"))
    testImplementation(libs.lib("junit"))
    testImplementation(libs.lib("kotlinx-coroutines-test"))
    testImplementation(libs.lib("ktor-client-mock"))
    testImplementation(libs.lib("robolectric"))
}
