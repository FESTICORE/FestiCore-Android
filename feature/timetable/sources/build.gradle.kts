import io.festicore.buildlogic.catalog
import io.festicore.buildlogic.lib

plugins {
    id("festicore.android.feature")
}

dependencies {
    val libs = catalog
    api(project(":feature:timetable:interface"))
    implementation(project(":domain:timetable:interface"))
    implementation(project(":domain:festival:interface"))
    implementation(project(":ui:designkit"))
    implementation(project(":ui:sharedui"))
    implementation(libs.lib("compose-foundation"))
    implementation(libs.lib("lifecycle-runtime-compose"))
}
