import io.festicore.buildlogic.catalog
import io.festicore.buildlogic.lib

plugins {
    id("festicore.android.library")
    id("festicore.kotlin.serialization")
}

dependencies {
    val libs = catalog
    api(project(":service:pack:interface"))
    implementation(project(":core:network:sources"))
    implementation(project(":service:api"))
    implementation(libs.lib("kotlinx-coroutines-core"))

    implementation(platform(libs.lib("koin-bom")))
    implementation(libs.lib("koin-android"))

    testImplementation(libs.lib("junit"))
    testImplementation(libs.lib("robolectric"))
    testImplementation(libs.lib("kotlinx-coroutines-test"))
}
