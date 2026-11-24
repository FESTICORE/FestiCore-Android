package io.festicore.core.location.client

import android.app.Activity

/** 위치 권한 대화상자를 띄울 지금 화면의 Activity. app이 등록한다(`domain/auth/sources` `ActivityProvider`와 같은 방식). */
fun interface LocationActivityProvider {
    fun currentActivity(): Activity?
}
