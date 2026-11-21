package io.festicore.app.navigation

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import io.festicore.ui.animation.FestiCoreAnimationAsset
import io.festicore.ui.animation.FestiCoreLottieView
import io.festicore.ui.designkit.tokens.FestiCoreSemanticColor
import kotlin.time.Duration.Companion.seconds

/**
 * iOS `SplashView`. staticInk 배경 위에 Lottie를 2초에 맞춰 한 번, 화면을 채워(Crop) 재생한다.
 * 첫 컴포지션에서 [onAppear]를 보낸다(iOS `onAppear`). 2초는 [AppCoordinator]가 그때부터 잰다.
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
    LaunchedEffect(Unit) { onAppear() }
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
        )
    }
}
