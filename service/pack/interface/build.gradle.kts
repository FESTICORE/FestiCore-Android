import io.festicore.buildlogic.catalog
import io.festicore.buildlogic.lib

plugins {
    id("festicore.android.library")
}

dependencies {
    val libs = catalog
    api(project(":core:network:interface"))
    api(libs.lib("kotlinx-coroutines-core"))
}
