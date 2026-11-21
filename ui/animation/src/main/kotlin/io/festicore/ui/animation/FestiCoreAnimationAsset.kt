package io.festicore.ui.animation

import androidx.annotation.RawRes

/** iOS `FestiCoreAnimationAsset`. 이 모듈 `res/raw`에 넣은 Lottie 파일이다(iOS `Resources`와 같은 JSON). */
enum class FestiCoreAnimationAsset(@param:RawRes val resId: Int) {
    Splash(R.raw.splash),
}
