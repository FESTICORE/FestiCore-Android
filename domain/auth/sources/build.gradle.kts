import io.festicore.buildlogic.catalog
import io.festicore.buildlogic.lib

plugins {
    id("festicore.android.library")
}

dependencies {
    val libs = catalog
    api(project(":domain:auth:interface"))
    implementation(project(":service:apiendpoint"))
    implementation(project(":service:auth:interface"))
    implementation(libs.lib("credentials"))
    implementation(libs.lib("credentials-play-services-auth"))
    implementation(libs.lib("googleid"))

    implementation(platform(libs.lib("koin-bom")))
    implementation(libs.lib("koin-core"))

    testImplementation(project(":service:auth:sources"))
    testImplementation(project(":core:network:sources"))
    testImplementation(project(":core:storage"))
    testImplementation(libs.lib("junit"))
    testImplementation(libs.lib("ktor-client-mock"))
    testImplementation(libs.lib("kotlinx-coroutines-test"))
}
