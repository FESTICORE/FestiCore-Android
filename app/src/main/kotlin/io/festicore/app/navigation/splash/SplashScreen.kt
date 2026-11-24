package io.festicore.app.navigation.splash

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import io.festicore.app.navigation.main.AppCoordinator
import io.festicore.ui.animation.lottie.FestiCoreAnimationAsset
import io.festicore.ui.animation.lottie.FestiCoreLottieView
import io.festicore.ui.designkit.tokens.FestiCoreSemanticColor
import kotlin.time.Duration.Companion.seconds

/**
 * iOS `SplashView`. staticInk 배경 위에 Lottie를 2초에 맞춰 한 번, 화면을 채워(Crop) 재생한다.
 * Lottie를 읽어 그릴 수 있게 되면 [onAppear]를 보낸다(iOS는 번들 Lottie를 바로 그린다). 읽기에 실패해도 바로 보내 흐름이 멈추지 않는다.
 * 2초는 [AppCoordinator]가 그때부터 잰다. 그 전까지는 시스템 스플래시(같은 staticInk)가 남는다(`MainActivity`).
 */
@Composable
fun SplashScreen(onAppear: () -> Unit) {
    val window = LocalActivity.current?.window
    val view = LocalView.current
    DisposableEffect(window, view) {
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        controller?.hide(WindowInsetsCompat.Type.statusBars())
        onDispose { controller?.show(WindowInsetsCompat.Type.statusBars()) }
    }
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(FestiCoreSemanticColor.staticInk.color)
                .testTag("splash_root"),
    ) {
        FestiCoreLottieView(
            asset = FestiCoreAnimationAsset.Splash,
            modifier = Modifier.fillMaxSize().clipToBounds(),
            duration = 2.seconds,
            playOnce = true,
            contentScale = ContentScale.Crop,
            onLoadFinished = onAppear,
        )
    }
}
