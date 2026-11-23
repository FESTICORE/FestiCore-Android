import io.festicore.buildlogic.catalog
import io.festicore.buildlogic.lib

plugins {
    id("festicore.android.library")
    id("festicore.kotlin.serialization")
}

dependencies {
    val libs = catalog
    api(project(":domain:auth:interface"))
    api(project(":feature:web:interface"))
    api(libs.lib("navigation3-runtime"))
    api(libs.lib("kotlinx-serialization-core"))
}
