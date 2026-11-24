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
    api(project(":feature:web:interface"))
    implementation(project(":service:analytics:interface"))
    implementation(project(":ui:designkit"))
    implementation(libs.lib("compose-foundation"))

    testImplementation(libs.lib("robolectric"))
}
