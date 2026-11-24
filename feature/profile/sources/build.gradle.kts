import io.festicore.buildlogic.catalog
import io.festicore.buildlogic.lib

plugins {
    id("festicore.android.feature")
}

dependencies {
    val libs = catalog
    api(project(":feature:profile:interface"))
    implementation(project(":service:analytics:interface"))
    implementation(project(":core:location:interface"))
    implementation(project(":domain:festival:interface"))
    implementation(project(":domain:bearer:interface"))
    implementation(project(":domain:notice:interface"))
    implementation(project(":feature:web:interface"))
    implementation(project(":ui:designkit"))
    implementation(project(":ui:sharedui"))
    implementation(libs.lib("compose-foundation"))
}
