import io.festicore.buildlogic.catalog
import io.festicore.buildlogic.lib

plugins {
    id("festicore.android.library")
    id("festicore.kotlin.serialization")
}

dependencies {
    val libs = catalog
    api(project(":domain:festival:interface"))
    implementation(project(":domain:auth:interface"))
    implementation(project(":service:apiendpoint"))
    implementation(project(":service:auth:interface"))
    implementation(project(":service:pack:interface"))
    implementation(project(":core:storage"))

    implementation(platform(libs.lib("koin-bom")))
    implementation(libs.lib("koin-core"))

    // 실제 서비스 구현을 조립해 Repository · UseCase를 검증하는 테스트용(main 의존 아님).
    testImplementation(project(":core:network:sources"))
    testImplementation(project(":service:festivalcatalog"))
    testImplementation(libs.lib("junit"))
    testImplementation(libs.lib("kotlinx-coroutines-test"))
    testImplementation(libs.lib("ktor-client-mock"))
}
