import io.festicore.buildlogic.catalog
import io.festicore.buildlogic.lib

plugins {
    id("festicore.android.library")
}

dependencies {
    val libs = catalog
    api(project(":domain:usernotification:interface"))
    implementation(project(":service:apiendpoint"))
    implementation(project(":service:auth"))

    implementation(platform(libs.lib("koin-bom")))
    implementation(libs.lib("koin-core"))

    testImplementation(project(":core:datastore"))
    testImplementation(libs.lib("junit"))
    testImplementation(libs.lib("kotlinx-coroutines-test"))
    testImplementation(libs.lib("ktor-client-mock"))
}
