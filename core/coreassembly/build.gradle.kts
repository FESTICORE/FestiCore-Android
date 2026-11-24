import io.festicore.buildlogic.catalog
import io.festicore.buildlogic.lib

plugins {
    id("festicore.android.library")
}

dependencies {
    val libs = catalog
    implementation(project(":core:location:sources"))
    implementation(project(":core:network:sources"))
    implementation(project(":core:storage:sources"))
    implementation(platform(libs.lib("koin-bom")))
    implementation(libs.lib("koin-core"))
}
