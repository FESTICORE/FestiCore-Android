import io.festicore.buildlogic.catalog
import io.festicore.buildlogic.lib

plugins {
    id("festicore.android.library")
}

dependencies {
    val libs = catalog
    // 매퍼가 팩 문서를 입력으로 받는다. 순수 Kotlin 모듈이다.
    api(project(":domain:festival:interface"))

    testImplementation(libs.lib("junit"))
}
