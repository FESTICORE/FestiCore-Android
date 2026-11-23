import io.festicore.buildlogic.catalog
import io.festicore.buildlogic.lib

plugins {
    id("festicore.android.library")
    id("festicore.kotlin.serialization")
}

dependencies {
    val libs = catalog
    api(project(":service:apiendpoint"))
    implementation(project(":core:datastore"))
    implementation(libs.lib("kotlinx-coroutines-core"))

    implementation(platform(libs.lib("koin-bom")))
    implementation(libs.lib("koin-core"))

    testImplementation(libs.lib("junit"))
    testImplementation(libs.lib("ktor-client-mock"))
    testImplementation(libs.lib("kotlinx-coroutines-test"))
}
