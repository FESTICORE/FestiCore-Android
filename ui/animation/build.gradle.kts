import io.festicore.buildlogic.catalog
import io.festicore.buildlogic.lib

plugins {
    id("festicore.android.compose")
}

dependencies {
    val libs = catalog
    api(platform(libs.lib("compose-bom")))
    api(libs.lib("compose-ui"))
    implementation(libs.lib("compose-foundation"))
    implementation(libs.lib("lottie-compose"))
    implementation(libs.lib("compose-ui-tooling-preview"))
    debugImplementation(libs.lib("compose-ui-tooling"))
    testImplementation(libs.lib("junit"))
}
