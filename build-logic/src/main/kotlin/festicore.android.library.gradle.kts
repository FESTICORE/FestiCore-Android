import io.festicore.buildlogic.AndroidConfig
import io.festicore.buildlogic.androidNamespace
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension

plugins {
    id("com.android.library")
}

android {
    namespace = androidNamespace()
    compileSdk = AndroidConfig.COMPILE_SDK
    defaultConfig {
        minSdk = AndroidConfig.MIN_SDK
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

extensions.configure<KotlinAndroidProjectExtension> {
    jvmToolchain(AndroidConfig.JVM_TOOLCHAIN)
}
