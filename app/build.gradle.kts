import io.festicore.buildlogic.catalog
import io.festicore.buildlogic.lib

plugins {
    id("festicore.android.application")
}

android {
    testOptions.unitTests.isIncludeAndroidResources = true
}

dependencies {
    val libs = catalog
    implementation(project(":core:logger"))
    implementation(project(":core:network"))
    implementation(project(":core:datastore"))
    implementation(project(":service:api"))
    implementation(project(":service:auth"))
    implementation(project(":ui:designkit"))
    implementation(project(":feature:onboarding:sources"))
    implementation(libs.lib("core-splashscreen"))

    testImplementation(libs.lib("robolectric"))
    testImplementation(libs.lib("compose-ui-test-junit4"))
    debugImplementation(libs.lib("compose-ui-test-manifest"))
}
