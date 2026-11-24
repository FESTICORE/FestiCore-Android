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
    api(project(":feature:auth:interface"))
    implementation(project(":service:analytics:interface"))
    implementation(project(":domain:auth:interface"))
    implementation(project(":ui:designkit"))
    implementation(libs.lib("compose-foundation"))
    implementation(libs.lib("activity-compose"))

    testImplementation(libs.lib("robolectric"))
}
