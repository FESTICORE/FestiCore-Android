import io.festicore.buildlogic.catalog
import io.festicore.buildlogic.lib

plugins {
    id("festicore.android.library")
}

dependencies {
    val libs = catalog
    implementation(project(":core:logger"))
    implementation(platform(libs.lib("firebase-bom")))
    implementation(libs.lib("firebase-common"))

    testImplementation(libs.lib("junit"))
    testImplementation(libs.lib("robolectric"))
}
