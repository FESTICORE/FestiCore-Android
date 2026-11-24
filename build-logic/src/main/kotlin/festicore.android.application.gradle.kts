import io.festicore.buildlogic.AndroidConfig
import io.festicore.buildlogic.catalog
import io.festicore.buildlogic.lib
import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

fun environmentConfig(name: String): Properties = Properties().also {
    it.load(providers.fileContents(layout.projectDirectory.file("config/$name.properties")).asText.get().reader())
}

// 비밀 값은 추적하지 않는 local.properties 또는 CI 환경 변수에서 읽는다. 없으면 빈 문자열.
val localProperties = Properties().also { properties ->
    providers.fileContents(rootProject.layout.projectDirectory.file("local.properties")).asText.orNull?.let { properties.load(it.reader()) }
}

fun secretValue(key: String): String = providers.environmentVariable(key).orNull ?: localProperties.getProperty(key).orEmpty()

fun buildConfigString(value: String): String = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

android {
    namespace = "io.festicore.app"
    compileSdk = AndroidConfig.COMPILE_SDK

    defaultConfig {
        // iOS 번들 ID와 같은 값
        applicationId = AndroidConfig.APPLICATION_ID
        minSdk = AndroidConfig.MIN_SDK
        targetSdk = AndroidConfig.TARGET_SDK
        // 빌드 번호는 배포 때 -Pfesticore.versionCode=N으로 넣는다. 로컬 기본값 1
        versionCode = providers.gradleProperty("festicore.versionCode").map(String::toInt).getOrElse(1)
        versionName = AndroidConfig.VERSION_NAME
    }

    flavorDimensions += "environment"
    productFlavors {
        listOf("stage", "prod").forEach { name ->
            create(name) {
                val config = environmentConfig(name)
                dimension = "environment"
                applicationIdSuffix = config.getProperty("APPLICATION_ID_SUFFIX").orEmpty().ifEmpty { null }
                resValue("string", "app_name", config.getProperty("APP_NAME"))
                buildConfigField("Boolean", "IS_STAGE", (name == "stage").toString())
                buildConfigField("String", "BASE_URL", "\"${config.getProperty("BASE_URL").orEmpty()}\"")
                // 공개 값(웹 OAuth 클라이언트 ID · 약관 주소). 비밀 값은 여기에 두지 않는다.
                buildConfigField("String", "GOOGLE_SERVER_CLIENT_ID", "\"${config.getProperty("GOOGLE_SERVER_CLIENT_ID").orEmpty()}\"")
                buildConfigField("String", "LEGAL_BASE_URL", "\"${config.getProperty("LEGAL_BASE_URL").orEmpty()}\"")
                // Firebase 값(FESTICORE_FIREBASE_{STAGE,PROD}_*). 비면 service/config가 Firebase를 초기화하지 않는다.
                listOf("API_KEY", "APP_ID", "PROJECT_ID", "SENDER_ID").forEach { field ->
                    val value = secretValue("FESTICORE_FIREBASE_${name.uppercase()}_$field")
                    buildConfigField("String", "FIREBASE_$field", buildConfigString(value))
                }
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
        resValues = true
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

extensions.configure<KotlinAndroidProjectExtension> {
    jvmToolchain(AndroidConfig.JVM_TOOLCHAIN)
}

dependencies {
    val libs = catalog
    implementation(platform(libs.lib("compose-bom")))
    implementation(libs.lib("compose-ui"))
    implementation(libs.lib("compose-material3"))
    implementation(libs.lib("compose-ui-tooling-preview"))
    debugImplementation(libs.lib("compose-ui-tooling"))
    implementation(libs.lib("activity-compose"))

    implementation(platform(libs.lib("koin-bom")))
    implementation(libs.lib("koin-android"))
    implementation(libs.lib("koin-compose-viewmodel"))

    implementation(libs.lib("orbit-core"))
    implementation(libs.lib("orbit-viewmodel"))
    implementation(libs.lib("orbit-compose"))

    implementation(libs.lib("navigation3-runtime"))
    implementation(libs.lib("navigation3-ui"))
    implementation(libs.lib("lifecycle-viewmodel-navigation3"))
    implementation(libs.lib("kotlinx-serialization-json"))

    testImplementation(libs.lib("junit"))
    testImplementation(libs.lib("orbit-test"))
    testImplementation(libs.lib("kotlinx-coroutines-test"))
}
