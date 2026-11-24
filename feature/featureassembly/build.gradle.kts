import io.festicore.buildlogic.catalog
import io.festicore.buildlogic.lib

plugins {
    id("festicore.android.library")
}

dependencies {
    val libs = catalog
    implementation(project(":feature:onboarding"))
    implementation(project(":feature:auth:sources"))
    implementation(project(":feature:web:sources"))
    implementation(project(":feature:festival:sources"))
    implementation(project(":feature:profile:sources"))
    implementation(project(":feature:timetable:sources"))
    implementation(platform(libs.lib("koin-bom")))
    implementation(libs.lib("koin-core"))
}
