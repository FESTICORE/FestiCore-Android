import io.festicore.buildlogic.catalog
import io.festicore.buildlogic.lib

plugins {
    id("festicore.android.library")
}

dependencies {
    val libs = catalog
    api(project(":service:analytics:interface"))
    implementation(project(":core:logger"))
    implementation(project(":core:storage:interface"))
    implementation(platform(libs.lib("firebase-bom")))
    implementation(libs.lib("firebase-analytics"))
    implementation(libs.lib("mixpanel-android"))
    implementation(libs.lib("sentry-android"))

    implementation(platform(libs.lib("koin-bom")))
    implementation(libs.lib("koin-android"))

    testImplementation(libs.lib("junit"))
    testImplementation(libs.lib("robolectric"))
}
