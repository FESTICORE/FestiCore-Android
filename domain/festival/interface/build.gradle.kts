import io.festicore.buildlogic.catalog
import io.festicore.buildlogic.lib

plugins {
    id("festicore.android.library")
}

dependencies {
    val libs = catalog
    // 팩 상태 공유(StateFlow) · 진행 흐름(Flow). 순수 Kotlin 라이브러리다.
    api(libs.lib("kotlinx-coroutines-core"))
}
