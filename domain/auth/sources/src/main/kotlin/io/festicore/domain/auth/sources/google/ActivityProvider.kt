package io.festicore.domain.auth.sources.google

import android.app.Activity

/** Credential Manager 계정 선택 창을 띄울 현재 Activity. app이 구현해 등록한다. */
fun interface ActivityProvider {
    fun currentActivity(): Activity?
}
