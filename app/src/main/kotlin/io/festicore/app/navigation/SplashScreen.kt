package io.festicore.app.navigation

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import io.festicore.app.R
import io.festicore.ui.designkit.R as DesignKitR
import io.festicore.ui.designkit.tokens.FestiCoreSemanticColor

/** iOS `SplashView`. Lottie 대신 정적 로고를 보인다(설계 문서 "스플래시"). 시간은 [AppCoordinator]가 잰다. */
@Composable
fun SplashScreen() {
    val window = LocalActivity.current?.window
    val view = LocalView.current
    DisposableEffect(window, view) {
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        controller?.hide(WindowInsetsCompat.Type.statusBars())
        onDispose { controller?.show(WindowInsetsCompat.Type.statusBars()) }
    }
    Box(
        modifier = Modifier.fillMaxSize().background(FestiCoreSemanticColor.staticInk.color),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(DesignKitR.drawable.logo),
            contentDescription = stringResource(R.string.app_name),
            colorFilter = ColorFilter.tint(FestiCoreSemanticColor.staticWhite.color),
        )
    }
}
