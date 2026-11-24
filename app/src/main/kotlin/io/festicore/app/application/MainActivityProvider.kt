package io.festicore.app.application
import android.app.Activity
import io.festicore.app.MainActivity
import io.festicore.core.location.client.LocationActivityProvider
import io.festicore.domain.auth.sources.google.ActivityProvider
import java.lang.ref.WeakReference

/** Credential Manager 계정 선택 창 · 위치 권한 대화상자를 띄울 [MainActivity]. Activity를 붙잡지 않도록 약한 참조로 둔다. */
class MainActivityProvider :
    ActivityProvider,
    LocationActivityProvider {
    @Volatile
    private var current: WeakReference<Activity>? = null

    fun attach(activity: Activity) {
        current = WeakReference(activity)
    }

    /** 다른 Activity가 이미 붙었으면 그대로 둔다(회전 등으로 새 Activity가 먼저 붙는 경우). */
    fun detach(activity: Activity) {
        if (current?.get() === activity) current = null
    }

    override fun currentActivity(): Activity? = current?.get()
}
