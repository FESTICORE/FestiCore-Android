import io.festicore.buildlogic.catalog
import io.festicore.buildlogic.lib

plugins {
    id("festicore.android.library")
}

dependencies {
    val libs = catalog
    implementation(project(":service:apiendpoint"))
    implementation(project(":service:auth"))
    implementation(project(":service:device"))
    implementation(project(":service:festivalcatalog"))
    implementation(project(":service:pack"))
    implementation(platform(libs.lib("koin-bom")))
    implementation(libs.lib("koin-core"))
}
