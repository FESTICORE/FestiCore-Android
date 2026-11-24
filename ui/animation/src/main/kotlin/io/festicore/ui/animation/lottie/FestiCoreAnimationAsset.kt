package io.festicore.ui.animation.lottie

import androidx.annotation.RawRes
import io.festicore.ui.animation.R

/** iOS `FestiCoreAnimationAsset`. 이 모듈 `res/raw`에 넣은 Lottie 파일이다(iOS `Resources`와 같은 JSON). */
enum class FestiCoreAnimationAsset(@param:RawRes val resId: Int) {
    Splash(R.raw.splash),
}
