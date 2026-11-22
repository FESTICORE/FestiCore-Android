import io.festicore.buildlogic.catalog
import io.festicore.buildlogic.lib

plugins {
    id("festicore.android.library")
}

dependencies {
    val libs = catalog
    // 배지 공유 상태(StateFlow). 순수 Kotlin 라이브러리다.
    api(libs.lib("kotlinx-coroutines-core"))
}
