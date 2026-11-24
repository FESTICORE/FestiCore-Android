import io.festicore.buildlogic.catalog
import io.festicore.buildlogic.lib

plugins {
    id("festicore.android.feature")
}

android {
    testOptions.unitTests.isIncludeAndroidResources = true
}

dependencies {
    val libs = catalog
    api(project(":feature:festival:interface"))
    implementation(project(":service:analytics:interface"))
    implementation(project(":core:location:interface"))
    implementation(project(":domain:festival:interface"))
    implementation(project(":domain:auth:interface"))
    implementation(project(":domain:usernotification:interface"))
    implementation(project(":ui:designkit"))
    implementation(project(":ui:sharedui"))
    implementation(libs.lib("compose-foundation"))
    implementation(libs.lib("activity-compose"))
    implementation(libs.lib("lifecycle-runtime-compose"))

    testImplementation(libs.lib("robolectric"))
    testImplementation(libs.lib("compose-ui-test-junit4"))
    debugImplementation(libs.lib("compose-ui-test-manifest"))
}
