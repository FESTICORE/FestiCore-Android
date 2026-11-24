import io.festicore.buildlogic.catalog
import io.festicore.buildlogic.lib

plugins {
    id("festicore.android.library")
    id("festicore.kotlin.serialization")
}

dependencies {
    val libs = catalog
    api(project(":core:location:interface"))
    // Routes API(외부 호스트) 호출용 HttpClient 생성. 경계 규칙 예외(설계 R6 쟁점 F4)
    implementation(project(":core:network:sources"))
    implementation(libs.lib("ktor-client-core"))
    implementation(libs.lib("kotlinx-serialization-json"))
    implementation(libs.lib("androidx-core"))
    implementation(libs.lib("activity"))
    implementation(libs.lib("kotlinx-coroutines-core"))

    implementation(platform(libs.lib("koin-bom")))
    implementation(libs.lib("koin-android"))

    testImplementation(libs.lib("junit"))
    testImplementation(libs.lib("kotlinx-coroutines-test"))
    testImplementation(libs.lib("ktor-client-mock"))
}
