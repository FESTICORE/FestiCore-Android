package io.festicore.ui.animation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.tooling.preview.Preview
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.rememberLottieComposition
import kotlin.time.Duration
import kotlin.time.DurationUnit

/**
 * iOS `FestiCoreLottieView`. 로컬 Lottie를 재생한다.
 * [duration]을 주면 한 번 재생 길이를 그 시간에 맞춰 속도를 바꾼다(고유 길이 / 목표 길이).
 * iOS 기본값과 같이 반복 재생 · 원본 비율 맞춤(`scaleAspectFit` = [ContentScale.Fit])이다.
 * iOS처럼 읽기 도구에 노출하지 않는다(semantics 없음).
 * [onLoadFinished]는 파일 읽기가 끝나면(성공 · 실패 모두) 한 번 부른다. 성공이면 다음 프레임부터 그린다.
 */
@Composable
fun FestiCoreLottieView(
    asset: FestiCoreAnimationAsset,
    modifier: Modifier = Modifier,
    duration: Duration? = null,
    playOnce: Boolean = false,
    contentScale: ContentScale = ContentScale.Fit,
    onLoadFinished: () -> Unit = {},
) {
    val result = rememberLottieComposition(LottieCompositionSpec.RawRes(asset.resId))
    val composition by result
    val currentOnLoadFinished by rememberUpdatedState(onLoadFinished)
    val notifier = remember { LoadFinishedNotifier { currentOnLoadFinished() } }
    LaunchedEffect(result.isComplete) { notifier.update(result.isComplete) }
    LottieAnimation(
        composition = composition,
        modifier = modifier,
        speed = lottieSpeed(composition?.duration?.toDouble()?.div(MILLIS_PER_SECOND), duration),
        iterations = if (playOnce) 1 else LottieConstants.IterateForever,
        contentScale = contentScale,
    )
}

/** iOS `speed(for:)`. 고유 길이(초)와 목표 길이가 모두 유효하면 고유 / 목표, 아니면 1. */
internal fun lottieSpeed(intrinsicSeconds: Double?, target: Duration?): Float {
    val targetSeconds = target?.toDouble(DurationUnit.SECONDS) ?: return 1f
    if (intrinsicSeconds == null || !intrinsicSeconds.isFinite() || intrinsicSeconds <= 0 || targetSeconds <= 0) return 1f
    val result = intrinsicSeconds / targetSeconds
    return if (result.isFinite() && result > 0) result.toFloat() else 1f
}

private const val MILLIS_PER_SECOND = 1_000.0

@Preview
@Composable
private fun FestiCoreLottieViewPreview() {
    FestiCoreLottieView(
        asset = FestiCoreAnimationAsset.Splash,
        modifier = Modifier.fillMaxSize(),
        playOnce = true,
        contentScale = ContentScale.Crop,
    )
}

/** 읽기가 끝났다는 알림을 한 번만 보낸다(다시 컴포지션되거나 상태가 다시 바뀌어도 두 번 보내지 않는다). */
internal class LoadFinishedNotifier(private val onFinished: () -> Unit) {
    private var notified = false

    fun update(isComplete: Boolean) {
        if (!isComplete || notified) return
        notified = true
        onFinished()
    }
}
