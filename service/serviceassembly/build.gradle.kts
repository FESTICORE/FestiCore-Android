import io.festicore.buildlogic.catalog
import io.festicore.buildlogic.lib

plugins {
    id("festicore.android.library")
}

dependencies {
    val libs = catalog
    implementation(project(":service:apiendpoint"))
    implementation(project(":service:auth:sources"))
    implementation(project(":service:device:sources"))
    implementation(project(":service:festivalcatalog"))
    implementation(project(":service:pack:sources"))
    implementation(platform(libs.lib("koin-bom")))
    implementation(libs.lib("koin-core"))
}
