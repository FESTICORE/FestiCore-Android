package io.festicore.ui.designkit.component.modal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.semantics.dismiss
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.festicore.ui.designkit.tokens.FestiCoreRadius
import io.festicore.ui.designkit.tokens.FestiCoreSemanticColor
import io.festicore.ui.designkit.tokens.FestiCoreSpacing
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val GrabberWidth = 36.dp
private val GrabberHeight = 5.dp
private val MediumDuration = 300.milliseconds
private const val CLOSE_LABEL = "닫기"

/** 바텀시트 높이. */
sealed interface FCSheetHeight {
    /** 쓸 수 있는 높이에 대한 비율. `Fraction(1f)`이면 전체 높이다. */
    data class Fraction(val fraction: Float) : FCSheetHeight

    /** 고정 높이. 쓸 수 있는 높이를 넘지 않는다. */
    data class Fixed(val height: Dp) : FCSheetHeight

    /** 내용 높이. */
    data object Auto : FCSheetHeight
}

/** 시트 안에서 닫기를 요청할 때 쓰는 동작. iOS `EnvironmentValues.fcSheetDismiss`. */
val LocalFCSheetDismiss = staticCompositionLocalOf<() -> Unit> { {} }

/**
 * 하위 어딘가에 [FCSheet]가 떠 있는지 상위 화면이 읽는 값. iOS `FCModalPresentedKey`.
 * 탭바처럼 시트 밖 영역을 가진 상위 화면이 만들어 [LocalFCSheetPresentation]으로 내려 주고 [isPresented]를 읽어 숨긴다.
 */
@Stable
class FCSheetPresentation {
    private var presentedCount by mutableIntStateOf(0)

    /** 중첩된 시트까지 포함해 하나라도 떠 있으면 true */
    val isPresented: Boolean get() = presentedCount > 0

    internal fun register() {
        presentedCount++
    }

    internal fun unregister() {
        presentedCount--
    }
}

/** 하위 시트가 열림을 알릴 [FCSheetPresentation]. 없으면 알리지 않는다. */
val LocalFCSheetPresentation = staticCompositionLocalOf<FCSheetPresentation?> { null }

/** 닫기 요청 뒤 [FCSheet]가 `onDismissRequest`를 부르기까지 기다리는 시간(iOS `fcSheetDismissalDelay`, 테스트에서만 바꾼다). */
internal val LocalFCSheetDismissalDelay = staticCompositionLocalOf { MediumDuration }

/**
 * 아래에서 올라오는 바텀시트(설계 문서의 `FCSheet(isPresented, onDismissRequest, …)`). 동작은 아이템 판과 같다.
 * 내용 교체 상태를 구별해야 하면 아이템 판을 쓴다.
 */
@Composable
fun FCSheet(
    isPresented: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    height: FCSheetHeight = FCSheetHeight.Auto,
    showsGrabber: Boolean = true,
    isDismissible: Boolean = true,
    accessory: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    FCSheet(
        item = if (isPresented) Unit else null,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        height = height,
        showsGrabber = showsGrabber,
        isDismissible = isDismissible,
        accessory = accessory?.let { slot -> { _: Unit -> slot() } },
        content = { content() },
    )
}

/**
 * [item]이 있으면 바텀시트를 띄운다(iOS `fcModal(item:height:showsGrabber:content:accessory:)`). 위쪽 손잡이, 반경 24 모서리, 딤 배경을 그린다.
 *
 * - 딤 탭 · 아래로 끌기 · 뒤로 가기 · [LocalFCSheetDismiss]로 닫으면 시트가 내려간 뒤 [onDismissRequest]를 부른다.
 *   [isDismissible]이 false면 딤 탭 · 끌기 · 뒤로 가기로는 닫히지 않는다(저장 중 닫기 막기, iOS `fcSheetDismissDisabled`).
 * - 닫히는 중에 [item]이 바뀌거나 다시 열리면 예약된 닫기를 취소한다. 다른 아이템으로 바뀌어도 닫았다 열지 않고 그 자리에서 내용만 바꾼다.
 * - 시트가 사라지면(컴포지션에서 빠지면) 예약된 닫기도 함께 취소된다.
 * - [accessory]는 시트 위쪽 가장자리 16 위에 붙어 시트와 함께 움직이고, 닫기가 시작되면 바로 감춰진다(토스트 등).
 * - 떠 있는 동안 [LocalFCSheetPresentation]에 알린다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T : Any> FCSheet(
    item: T?,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    height: FCSheetHeight = FCSheetHeight.Auto,
    showsGrabber: Boolean = true,
    isDismissible: Boolean = true,
    accessory: (@Composable (T) -> Unit)? = null,
    content: @Composable (T) -> Unit,
) {
    val presentation = LocalFCSheetPresentation.current
    if (presentation != null && item != null) {
        DisposableEffect(presentation) {
            presentation.register()
            onDispose { presentation.unregister() }
        }
    }

    val dismissalDelay = LocalFCSheetDismissalDelay.current
    val currentItem by rememberUpdatedState(item)
    val currentOnDismissRequest by rememberUpdatedState(onDismissRequest)
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var isShowing by remember { mutableStateOf(false) }
    // 닫히는 동안에도 마지막 내용을 그린다.
    var shownItem by remember { mutableStateOf(item) }
    var pendingDismissal by remember { mutableStateOf<Job?>(null) }

    LaunchedEffect(item) {
        // 닫히거나 다른 아이템으로 바뀌면 진행 중인 닫기를 취소한다(다시 열기가 이긴다).
        pendingDismissal?.cancel()
        pendingDismissal = null
        if (item != null) {
            shownItem = item
            if (isShowing) sheetState.show() else isShowing = true
        } else if (isShowing) {
            sheetState.hide()
            isShowing = false
        }
    }

    val dismiss: () -> Unit = {
        val dismissedItem = currentItem
        if (dismissedItem != null) {
            pendingDismissal?.cancel()
            pendingDismissal =
                scope.launch {
                    launch { sheetState.hide() }
                    delay(dismissalDelay)
                    pendingDismissal = null
                    // 그사이 다른 아이템으로 바뀌었으면 새 시트를 닫지 않는다.
                    if (currentItem == dismissedItem) currentOnDismissRequest()
                }
        }
    }

    val displayed = shownItem
    if (!isShowing || displayed == null) return
    ModalBottomSheet(
        onDismissRequest = {
            // 딤 탭 · 끌기 · 뒤로 가기로 이미 내려간 경우. 예약된 닫기나 바깥에서 닫은 경우는 따로 처리된다.
            if (pendingDismissal == null && currentItem != null) currentOnDismissRequest()
        },
        modifier = modifier,
        sheetState = sheetState,
        sheetMaxWidth = Dp.Unspecified,
        sheetGesturesEnabled = isDismissible,
        shape = RectangleShape,
        containerColor = FestiCoreSemanticColor.backgroundElevated.color.copy(alpha = 0f),
        tonalElevation = 0.dp,
        scrimColor = FestiCoreSemanticColor.materialDimmer.color,
        dragHandle = null,
        contentWindowInsets = { WindowInsets(0) },
        properties = ModalBottomSheetProperties(shouldDismissOnBackPress = isDismissible, shouldDismissOnClickOutside = isDismissible),
    ) {
        if (accessory != null) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = FestiCoreSpacing.paddingP16)
                        .alpha(if (pendingDismissal == null) 1f else 0f),
                contentAlignment = Alignment.BottomCenter,
            ) {
                accessory(displayed)
            }
        }
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = FestiCoreRadius.r24, topEnd = FestiCoreRadius.r24))
                    .background(FestiCoreSemanticColor.backgroundElevated.color)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .semantics {
                        // iOS 딤의 "닫기" 버튼. 닫기가 막혀 있으면 알리지 않는다.
                        if (isDismissible) {
                            dismiss(label = CLOSE_LABEL) {
                                dismiss()
                                true
                            }
                        }
                    },
            verticalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP16),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (showsGrabber) {
                Box(
                    modifier =
                        Modifier
                            .padding(top = FestiCoreSpacing.paddingP8)
                            .size(GrabberWidth, GrabberHeight)
                            .background(FestiCoreSemanticColor.lineNormal.color, CircleShape),
                )
            }
            val heightModifier =
                when (height) {
                    is FCSheetHeight.Fraction -> Modifier.fillMaxHeight(height.fraction.coerceIn(0f, 1f))
                    is FCSheetHeight.Fixed -> Modifier.height(height.height)
                    FCSheetHeight.Auto -> Modifier
                }
            Box(modifier = Modifier.fillMaxWidth().then(heightModifier), contentAlignment = Alignment.TopCenter) {
                CompositionLocalProvider(LocalFCSheetDismiss provides dismiss) {
                    content(displayed)
                }
            }
        }
    }
}
