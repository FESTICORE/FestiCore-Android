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
    implementation(project(":core:utility"))
    implementation(project(":service:apiendpoint"))
    implementation(project(":service:auth"))
    implementation(project(":service:device"))
    implementation(project(":service:festivalcatalog"))
    implementation(project(":service:pack"))
    implementation(project(":domain:auth:sources"))
    implementation(project(":domain:festival:sources"))
    implementation(project(":domain:usernotification:sources"))
    implementation(project(":ui:designkit"))
    implementation(project(":ui:animation"))
    implementation(project(":ui:sharedui"))
    implementation(project(":feature:onboarding:sources"))
    implementation(project(":feature:auth:sources"))
    implementation(project(":feature:web:sources"))
    implementation(project(":feature:festival:sources"))
    implementation(libs.lib("core-splashscreen"))
    implementation(libs.lib("lifecycle-runtime-compose"))

    testImplementation(libs.lib("robolectric"))
    testImplementation(libs.lib("compose-ui-test-junit4"))
    debugImplementation(libs.lib("compose-ui-test-manifest"))
}
