import io.festicore.buildlogic.catalog
import io.festicore.buildlogic.lib

plugins {
    id("festicore.android.library")
    id("festicore.kotlin.serialization")
}

dependencies {
    val libs = catalog
    api(project(":domain:usernotification:interface"))
    implementation(project(":service:apiendpoint"))
    implementation(project(":service:auth:interface"))

    implementation(platform(libs.lib("koin-bom")))
    implementation(libs.lib("koin-core"))

    testImplementation(project(":service:auth:sources"))
    testImplementation(project(":core:network:sources"))
    testImplementation(project(":core:storage"))
    testImplementation(libs.lib("junit"))
    testImplementation(libs.lib("kotlinx-coroutines-test"))
    testImplementation(libs.lib("ktor-client-mock"))
}
