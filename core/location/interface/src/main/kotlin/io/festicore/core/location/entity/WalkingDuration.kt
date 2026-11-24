package io.festicore.core.location.entity

import kotlin.math.ceil

/** 지도가 계산한 도보 소요 시간. iOS `WalkingDuration`. */
data class WalkingDuration(val seconds: Double) {
    /**
     * 화면에 보일 분. 막차를 놓치지 않도록 남는 초는 1분으로 올리고 최소 1분이다.
     * 음수 · 무한대나 정수로 나타낼 수 없는 큰 값이면 null이다.
     */
    val minutes: Int?
        get() {
            if (!seconds.isFinite() || seconds < 0) return null
            val minutes = ceil(seconds / 60)
            if (minutes > Int.MAX_VALUE) return null
            return maxOf(1, minutes.toInt())
        }
}
