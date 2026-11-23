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
    implementation(project(":ui:designkit"))
    api(platform(libs.lib("compose-bom")))
    api(libs.lib("compose-ui"))
    implementation(libs.lib("compose-foundation"))
    implementation(libs.lib("coil-compose"))
    implementation(libs.lib("coil-network-okhttp"))
    implementation(libs.lib("browser"))
    implementation(libs.lib("compose-ui-tooling-preview"))
    debugImplementation(libs.lib("compose-ui-tooling"))
    testImplementation(libs.lib("junit"))
    testImplementation(libs.lib("robolectric"))
    testImplementation(libs.lib("compose-ui-test-junit4"))
    testImplementation(libs.lib("coil-test"))
    debugImplementation(libs.lib("compose-ui-test-manifest"))
}
