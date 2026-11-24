import io.festicore.buildlogic.catalog
import io.festicore.buildlogic.lib

plugins {
    id("festicore.android.library")
}

android {
    testFixtures {
        enable = true
    }
}

dependencies {
    val libs = catalog
    api(libs.lib("kotlinx-coroutines-core"))
}
