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
        // 기존 Firebase Android 등록의 패키지 이름
        applicationId = AndroidConfig.APPLICATION_ID
        minSdk = AndroidConfig.MIN_SDK
        targetSdk = AndroidConfig.TARGET_SDK
        // 빌드 번호는 배포 때 -Pfesticore.versionCode=N으로 넣는다. 로컬 기본값 1
        versionCode = providers.gradleProperty("festicore.versionCode").map(String::toInt).getOrElse(1)
        versionName = AndroidConfig.VERSION_NAME
        // Mixpanel 토큰(FESTICORE_MIXPANEL_TOKEN, iOS xcconfig와 같은 이름). 비면 service/analytics가 Mixpanel을 시작하지 않는다.
        buildConfigField("String", "MIXPANEL_TOKEN", buildConfigString(secretValue("FESTICORE_MIXPANEL_TOKEN")))
        buildConfigField("String", "SENTRY_DSN", buildConfigString(secretValue("FESTICORE_SENTRY_DSN")))
        // Google Routes API 키(FESTICORE_GOOGLE_ROUTES_API_KEY). 비면 귀가 카드는 도보만 직선거리로 어림한다(설계 R6 쟁점 F4).
        buildConfigField("String", "GOOGLE_ROUTES_API_KEY", buildConfigString(secretValue("FESTICORE_GOOGLE_ROUTES_API_KEY")))
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
                // Analytics는 기본 앱의 google_app_id 리소스를 읽는다. 완전한 기존 설정만 생성한다.
                val firebaseResources = mapOf(
                    "API_KEY" to "google_api_key",
                    "APP_ID" to "google_app_id",
                    "PROJECT_ID" to "project_id",
                    "SENDER_ID" to "gcm_defaultSenderId",
                )
                val firebaseValues = firebaseResources.mapValues { (field, _) ->
                    secretValue("FESTICORE_FIREBASE_${name.uppercase()}_$field").trim()
                }
                if (firebaseValues.values.all { it.isNotEmpty() }) {
                    firebaseResources.forEach { (field, resource) -> resValue("string", resource, firebaseValues.getValue(field)) }
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
