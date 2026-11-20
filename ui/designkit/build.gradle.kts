import io.festicore.buildlogic.catalog
import io.festicore.buildlogic.lib

plugins {
    id("festicore.android.compose")
}

android {
    testOptions.unitTests.isIncludeAndroidResources = true
}

dependencies {
    val libs = catalog
    api(platform(libs.lib("compose-bom")))
    api(libs.lib("compose-ui"))
    api(libs.lib("compose-foundation"))
    implementation(libs.lib("compose-material3"))
    implementation(libs.lib("compose-ui-tooling-preview"))
    debugImplementation(libs.lib("compose-ui-tooling"))
    testImplementation(libs.lib("junit"))
    testImplementation(libs.lib("robolectric"))
    testImplementation(libs.lib("compose-ui-test-junit4"))
    debugImplementation(libs.lib("compose-ui-test-manifest"))
}
