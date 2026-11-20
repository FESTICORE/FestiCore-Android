import io.festicore.buildlogic.catalog
import io.festicore.buildlogic.lib

plugins {
    id("festicore.android.compose")
}

dependencies {
    val libs = catalog
    api(platform(libs.lib("compose-bom")))
    api(libs.lib("compose-ui"))
    api(libs.lib("compose-foundation"))
    implementation(libs.lib("compose-material3"))
    testImplementation(libs.lib("junit"))
}
