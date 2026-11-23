import io.festicore.buildlogic.catalog
import io.festicore.buildlogic.lib

plugins {
    id("festicore.android.library")
}

dependencies {
    val libs = catalog
    api(project(":service:apiendpoint"))
    implementation(project(":service:auth"))
    implementation(project(":service:device"))
    implementation(libs.lib("kotlinx-coroutines-core"))

    implementation(platform(libs.lib("koin-bom")))
    implementation(libs.lib("koin-android"))

    testImplementation(libs.lib("junit"))
    testImplementation(libs.lib("robolectric"))
    testImplementation(libs.lib("ktor-client-mock"))
    testImplementation(libs.lib("kotlinx-coroutines-test"))
}
