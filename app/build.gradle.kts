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
    implementation(project(":core:storage:interface"))
    implementation(project(":core:utility"))
    implementation(project(":core:coreassembly"))
    implementation(project(":service:analytics:interface"))
    implementation(project(":service:analytics:sources"))
    implementation(project(":service:auth:interface"))
    implementation(project(":service:config"))
    implementation(project(":service:serviceassembly"))
    implementation(project(":domain:auth:sources"))
    implementation(project(":domain:festival:sources"))
    implementation(project(":domain:notice:sources"))
    implementation(project(":domain:timetable:sources"))
    implementation(project(":domain:usernotification:sources"))
    implementation(project(":ui:designkit"))
    implementation(project(":ui:animation"))
    implementation(project(":ui:sharedui"))
    implementation(project(":feature:onboarding"))
    implementation(project(":feature:auth:sources"))
    implementation(project(":feature:web:sources"))
    implementation(project(":feature:festival:sources"))
    implementation(project(":feature:profile:sources"))
    implementation(project(":feature:timetable:sources"))
    implementation(project(":feature:featureassembly"))
    implementation(libs.lib("core-splashscreen"))
    implementation(libs.lib("lifecycle-runtime-compose"))

    testImplementation(libs.lib("robolectric"))
    testImplementation(libs.lib("compose-ui-test-junit4"))
    debugImplementation(libs.lib("compose-ui-test-manifest"))
}
