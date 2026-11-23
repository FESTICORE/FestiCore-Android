import io.festicore.buildlogic.catalog
import io.festicore.buildlogic.lib

plugins {
    id("festicore.android.library")
}

dependencies {
    testImplementation(catalog.lib("junit"))
}
