import io.festicore.buildlogic.catalog
import io.festicore.buildlogic.lib

plugins {
    id("festicore.android.library")
}

dependencies {
    val libs = catalog
    api(project(":core:location:interface"))
    implementation(libs.lib("androidx-core"))
    implementation(libs.lib("activity"))
    implementation(libs.lib("kotlinx-coroutines-core"))

    implementation(platform(libs.lib("koin-bom")))
    implementation(libs.lib("koin-android"))

    testImplementation(libs.lib("junit"))
}
