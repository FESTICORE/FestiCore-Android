import io.festicore.buildlogic.catalog
import io.festicore.buildlogic.lib

plugins {
    id("festicore.android.library")
}

dependencies {
    val libs = catalog
    api(project(":domain:festival:interface"))
    implementation(project(":service:festivalcatalog"))
    implementation(project(":service:pack"))
    implementation(project(":core:datastore"))

    implementation(platform(libs.lib("koin-bom")))
    implementation(libs.lib("koin-core"))

    testImplementation(libs.lib("junit"))
    testImplementation(libs.lib("kotlinx-coroutines-test"))
}
