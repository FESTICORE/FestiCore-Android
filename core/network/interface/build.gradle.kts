import io.festicore.buildlogic.catalog
import io.festicore.buildlogic.lib

plugins {
    id("festicore.android.library")
    id("festicore.kotlin.serialization")
}

dependencies {
    val libs = catalog
    api(libs.lib("ktor-client-core"))
    api(libs.lib("kotlinx-serialization-json"))
}
