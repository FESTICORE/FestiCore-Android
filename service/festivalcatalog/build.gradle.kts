import io.festicore.buildlogic.catalog
import io.festicore.buildlogic.lib

plugins {
    id("festicore.android.library")
}

dependencies {
    val libs = catalog
    implementation(project(":domain:festival:sources"))
    api(project(":service:apiendpoint"))
    implementation(project(":service:auth:interface"))
    implementation(project(":service:device:interface"))
    implementation(libs.lib("kotlinx-coroutines-core"))

    implementation(platform(libs.lib("koin-bom")))
    implementation(libs.lib("koin-android"))

    testImplementation(project(":core:network:sources"))
    testImplementation(libs.lib("junit"))
    testImplementation(libs.lib("robolectric"))
    testImplementation(libs.lib("ktor-client-mock"))
    testImplementation(libs.lib("kotlinx-coroutines-test"))
}
