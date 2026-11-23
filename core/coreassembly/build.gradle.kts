import io.festicore.buildlogic.catalog
import io.festicore.buildlogic.lib

plugins {
    id("festicore.android.library")
}

dependencies {
    val libs = catalog
    implementation(project(":core:network"))
    implementation(project(":core:datastore"))
    implementation(platform(libs.lib("koin-bom")))
    implementation(libs.lib("koin-core"))
}
