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
    implementation(project(":domain:auth:sources"))
    implementation(project(":ui:designkit"))
    implementation(project(":ui:animation"))
    implementation(project(":feature:onboarding:sources"))
    implementation(project(":feature:auth:sources"))
    implementation(project(":feature:web:sources"))
    implementation(libs.lib("core-splashscreen"))
    implementation(libs.lib("lifecycle-runtime-compose"))

    testImplementation(libs.lib("robolectric"))
    testImplementation(libs.lib("compose-ui-test-junit4"))
    debugImplementation(libs.lib("compose-ui-test-manifest"))
}
