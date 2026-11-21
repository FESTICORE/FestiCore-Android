import io.festicore.buildlogic.catalog
import io.festicore.buildlogic.lib

plugins {
    id("festicore.android.feature")
}

dependencies {
    val libs = catalog
    api(project(":feature:web:interface"))
    implementation(project(":ui:designkit"))
    implementation(libs.lib("compose-foundation"))
}
