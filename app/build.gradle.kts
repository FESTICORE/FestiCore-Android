plugins {
    id("festicore.android.application")
}

dependencies {
    implementation(project(":core:logger"))
    implementation(project(":core:network"))
    implementation(project(":core:datastore"))
    implementation(project(":service:api"))
    implementation(project(":service:auth"))
    implementation(project(":ui:designkit"))
}
