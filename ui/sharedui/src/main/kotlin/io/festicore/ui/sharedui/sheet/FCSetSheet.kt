package io.festicore.ui.sharedui.sheet

import android.provider.Settings
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.festicore.ui.designkit.R
import io.festicore.ui.designkit.component.label.FCSourceLine
import io.festicore.ui.designkit.component.label.FCTag
import io.festicore.ui.designkit.component.label.FCTagTone
import io.festicore.ui.designkit.component.selection.FCToggle
import io.festicore.ui.designkit.tokens.FestiCoreRadius
import io.festicore.ui.designkit.tokens.FestiCoreSemanticColor
import io.festicore.ui.designkit.tokens.FestiCoreSpacing
import io.festicore.ui.designkit.tokens.FestiCoreState
import io.festicore.ui.designkit.tokens.FestiCoreTextStyle
import io.festicore.ui.designkit.typography.textStyle
import io.festicore.ui.designkit.typography.textStyleWithoutLineBox
import io.festicore.ui.sharedui.remoteimage.FCRemoteImage

private val IconSize = 20.dp
private val SmallIconSize = 16.dp
private val ArtistImageHeight = 180.dp
private val PlaceholderIconSize = 36.dp
private const val TAG_ANIMATION_MILLIS = 150
private const val STATUS_ANIMATION_MILLIS = 250

/** 겹침 · 변경 · 종료 상태. iOS `FCSetSheet.State`. */
sealed interface FCSetSheetState {
    data object Default : FCSetSheetState

    /** 내 일정끼리 겹침. [warning]에 겹치는 공연을 적는다. */
    data class Overlap(val warning: String) : FCSetSheetState

    /** 시간 변경. */
    data class Changed(
        val oldTime: String,
        val change: String,
    ) : FCSetSheetState

    /** 공연 종료. 내 일정에 넣을 수 없다. */
    data object Ended : FCSetSheetState
}

/**
 * 공연 상세 바텀시트 내용(iOS `FestiCoreSharedUI` `FCSetSheet`). designkit `FCSheet`로 띄운다.
 * 내 일정 여부는 [isScheduled]로, 겹침 · 변경 · 종료는 [state]로 정한다. iOS 체이닝 설정은 같은 이름의 인자로 받는다.
 *
 * @param artistImageUrl 출연진 사진(iOS `artistImage(_:)`). null이면 이름부터 보인다.
 * @param memo 한 줄 메모(iOS `memo(_:)`). null이면 숨긴다.
 * @param source 아래 출처 줄(iOS `source(_:)`). null이면 숨긴다.
 * @param onStageClick 무대 줄을 누를 수 있게 한다(iOS `stageAction(_:)`).
 * @param isScheduleDisabled 내 일정 토글을 잠시 끈다(iOS `scheduleDisabled(_:)`, 불러오는 중 등).
 * @param scheduleHelper 내 일정 토글 아래 안내. null이면 그 줄을 숨긴다(Android 푸시 전 알림 약속 숨김).
 * @param bottomStatus 하단 상태 자리. 숨겨도 측정 높이를 유지해 일정 토글 위치가 바뀌지 않는다.
 */
@Composable
fun FCSetSheet(
    artist: String,
    stage: String,
    time: String,
    relativeTime: String,
    isScheduled: Boolean,
    onIsScheduledChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    state: FCSetSheetState = FCSetSheetState.Default,
    memo: String? = null,
    artistImageUrl: String? = null,
    source: String? = null,
    onStageClick: (() -> Unit)? = null,
    scheduleTitle: String = "내 일정에 넣기",
    scheduleHelper: String? = "공연 10분 전에 알려 드려요",
    endedTitle: String = "공연 종료",
    endedHelper: String = "이미 종료된 공연이에요",
    isScheduleDisabled: Boolean = false,
    bottomStatus: (@Composable () -> Unit)? = null,
    isBottomStatusVisible: Boolean = true,
) {
    val isEnded = state == FCSetSheetState.Ended
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(
                    top = FestiCoreSpacing.paddingP16,
                    start = FestiCoreSpacing.paddingP20,
                    end = FestiCoreSpacing.paddingP20,
                    bottom = if (bottomStatus != null) 0.dp else FestiCoreSpacing.paddingP20,
                ),
        verticalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP16),
    ) {
        // 사진이 없는 공연은 시안대로 이름부터 보인다.
        if (artistImageUrl != null) ArtistImage(artistImageUrl)
        val tags = setSheetTags(state, isScheduled)
        if (tags.isNotEmpty()) TagRow(tags)
        TitleRow(artist = artist, badge = if (isEnded) endedTitle else relativeTime, isEnded = isEnded)
        Column(verticalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP8)) {
            StageRow(stage, onStageClick)
            TimeRow(time, state)
        }
        if (state is FCSetSheetState.Overlap) WarningBox(state.warning)
        if (memo != null) MemoBox(memo)
        ScheduleRow(
            title = scheduleTitle,
            helper = if (isEnded) endedHelper else scheduleHelper,
            isScheduled = isScheduled,
            isDisabled = isEnded || isScheduleDisabled,
            onIsScheduledChange = onIsScheduledChange,
        )
        if (source != null) {
            // 시안 S03 시트의 출처 줄은 배경 없이 위아래 12 여백을 둔다.
            FCSourceLine(source, modifier = Modifier.padding(vertical = FestiCoreSpacing.paddingP12), isInset = false)
        }
        if (bottomStatus != null) {
            val bringIntoViewRequester = remember { BringIntoViewRequester() }
            LaunchedEffect(isBottomStatusVisible) {
                if (isBottomStatusVisible) bringIntoViewRequester.bringIntoView()
            }
            val reduceMotion = rememberReduceMotion()
            val statusProgress by animateFloatAsState(
                targetValue = if (isBottomStatusVisible) 1f else 0f,
                animationSpec = if (reduceMotion) snap() else tween(STATUS_ANIMATION_MILLIS, easing = EaseOut),
                label = "setSheetStatus",
            )
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .bringIntoViewRequester(bringIntoViewRequester)
                        .padding(bottom = FestiCoreSpacing.paddingP16),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier =
                        Modifier
                            .graphicsLayer {
                                alpha = statusProgress
                                translationY = if (reduceMotion) 0f else (1f - statusProgress) * size.height
                            }
                            .then(if (isBottomStatusVisible) Modifier else Modifier.clearAndSetSemantics {}),
                ) {
                    bottomStatus()
                }
            }
        }
    }
}

/** 태그 한 칸. 숨긴 태그도 자리를 차지해 토글할 때 시트 높이와 다른 태그 위치가 바뀌지 않는다. */
internal data class FCSetSheetTag(
    val label: String,
    val tone: FCTagTone,
    val isVisible: Boolean,
)

/** 기본 · 겹침 상태는 "내 일정" 칸을 항상 두고, 저장하지 않았으면 투명하게 숨긴다(iOS `tags`). */
internal fun setSheetTags(state: FCSetSheetState, isScheduled: Boolean): List<FCSetSheetTag> {
    val mine = FCSetSheetTag("내 일정", FCTagTone.Lime, isScheduled)
    return when (state) {
        FCSetSheetState.Default -> listOf(mine)
        is FCSetSheetState.Overlap -> listOf(mine, FCSetSheetTag("겹침", FCTagTone.Warning, true))
        is FCSetSheetState.Changed -> listOf(FCSetSheetTag(state.change, FCTagTone.Warning, true))
        FCSetSheetState.Ended -> emptyList()
    }
}

@Composable
private fun TagRow(tags: List<FCSetSheetTag>) {
    val reduceMotion = rememberReduceMotion()
    Row(horizontalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP4)) {
        tags.forEach { tag ->
            val alpha by animateFloatAsState(
                targetValue = if (tag.isVisible) 1f else 0f,
                animationSpec = if (reduceMotion) snap() else tween(TAG_ANIMATION_MILLIS, easing = EaseOut),
                label = "setSheetTag",
            )
            val hidden = if (tag.isVisible) Modifier else Modifier.clearAndSetSemantics {}
            FCTag(tag.label, modifier = Modifier.alpha(alpha).then(hidden), tone = tag.tone)
        }
    }
}

@Composable
private fun TitleRow(
    artist: String,
    badge: String,
    isEnded: Boolean,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(
            text = artist,
            modifier = Modifier.weight(1f).semantics { heading() },
            style = FestiCoreTextStyle.baseTitle1Bold.textStyle.copy(color = FestiCoreSemanticColor.labelNormal.color),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        BasicText(
            text = badge,
            modifier =
                Modifier
                    .background(
                        (if (isEnded) FestiCoreSemanticColor.fillNormal else FestiCoreSemanticColor.inverseBackground).color,
                        CircleShape,
                    ).padding(vertical = FestiCoreSpacing.paddingP4, horizontal = FestiCoreSpacing.paddingP8),
            style =
                FestiCoreTextStyle.baseFootnoteBold.textStyle.copy(
                    color = (if (isEnded) FestiCoreSemanticColor.labelAssistive else FestiCoreSemanticColor.inverseLabel).color,
                ),
        )
    }
}

@Composable
private fun StageRow(stage: String, onStageClick: (() -> Unit)?) {
    if (onStageClick == null) {
        StageLabel(stage)
        return
    }
    Row(
        modifier = Modifier.clickable(onClick = onStageClick),
        horizontalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StageLabel(stage)
        Image(
            painter = painterResource(R.drawable.ic_chevron_right_16),
            contentDescription = null,
            modifier = Modifier.size(SmallIconSize),
            colorFilter = ColorFilter.tint(FestiCoreSemanticColor.labelAssistive.color),
        )
    }
}

@Composable
private fun StageLabel(stage: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RowIcon(R.drawable.ic_location_dot_20)
        BasicText(
            text = stage,
            style = FestiCoreTextStyle.baseBodyRegular.textStyle.copy(color = FestiCoreSemanticColor.labelNormal.color),
        )
    }
}

@Composable
private fun TimeRow(time: String, state: FCSetSheetState) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RowIcon(R.drawable.ic_clock_20)
        if (state is FCSetSheetState.Changed) {
            val old = FestiCoreTextStyle.baseBodyRegular.textStyleWithoutLineBox.copy(color = FestiCoreSemanticColor.labelAssistive.color)
            BasicText(text = state.oldTime, style = old)
            BasicText(text = "→", style = old)
            BasicText(
                text = time,
                style = FestiCoreTextStyle.baseHeadlineBold.textStyle.copy(color = FestiCoreSemanticColor.accentForegroundOrange.color),
            )
        } else {
            BasicText(
                text = time,
                style = FestiCoreTextStyle.baseBodyRegular.textStyle.copy(color = FestiCoreSemanticColor.labelNormal.color),
            )
        }
    }
}

@Composable
private fun WarningBox(warning: String) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(FestiCoreSemanticColor.accentBackgroundOrange.color, RoundedCornerShape(FestiCoreRadius.r12))
                .padding(vertical = FestiCoreSpacing.paddingP12, horizontal = FestiCoreSpacing.paddingP16),
        horizontalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_triangle_exclamation_16),
            contentDescription = null,
            modifier = Modifier.size(SmallIconSize),
            colorFilter = ColorFilter.tint(FestiCoreSemanticColor.accentForegroundOrange.color),
        )
        BasicText(
            text = warning,
            modifier = Modifier.weight(1f),
            style = FestiCoreTextStyle.baseFootnoteMedium.textStyle.copy(color = FestiCoreSemanticColor.labelNormal.color),
        )
    }
}

@Composable
private fun ArtistImage(url: String) {
    // 사진 비율과 상관없이 시트 폭을 넘지 않도록 고정 틀 위에 채운다. 불러오지 못하면 기본 모양(music)을 둔다.
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(ArtistImageHeight)
                .clip(RoundedCornerShape(FestiCoreRadius.r12))
                .clearAndSetSemantics {},
    ) {
        FCRemoteImage(
            url = url,
            modifier = Modifier.fillMaxWidth().height(ArtistImageHeight),
            placeholderIcon = R.drawable.ic_music_24,
            placeholderIconSize = PlaceholderIconSize,
        )
    }
}

@Composable
private fun MemoBox(memo: String) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(FestiCoreSemanticColor.fillNormal.color, RoundedCornerShape(FestiCoreRadius.r16))
                .padding(FestiCoreSpacing.paddingP16),
        verticalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP4),
    ) {
        BasicText(
            text = "한 줄 메모",
            style = FestiCoreTextStyle.baseCaption1Regular.textStyle.copy(color = FestiCoreSemanticColor.labelAssistive.color),
        )
        BasicText(
            text = memo,
            style = FestiCoreTextStyle.baseCalloutRegular.textStyle.copy(color = FestiCoreSemanticColor.labelNormal.color),
        )
    }
}

@Composable
private fun ScheduleRow(
    title: String,
    helper: String?,
    isScheduled: Boolean,
    isDisabled: Boolean,
    onIsScheduledChange: (Boolean) -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP4)) {
            BasicText(
                text = title,
                style = FestiCoreTextStyle.baseCalloutBold.textStyle.copy(color = FestiCoreSemanticColor.labelNormal.color),
            )
            if (helper != null) {
                BasicText(
                    text = helper,
                    style = FestiCoreTextStyle.baseFootnoteRegular.textStyle.copy(color = FestiCoreSemanticColor.labelAssistive.color),
                )
            }
        }
        FCToggle(
            isOn = isScheduled,
            onIsOnChange = { if (!isDisabled) onIsScheduledChange(it) },
            accessibilityLabel = title,
            modifier = if (isDisabled) Modifier.alpha(FestiCoreState.disabledOpacity) else Modifier,
        )
    }
}

@Composable
private fun RowIcon(icon: Int) {
    Image(
        painter = painterResource(icon),
        contentDescription = null,
        modifier = Modifier.size(IconSize),
        colorFilter = ColorFilter.tint(FestiCoreSemanticColor.labelNeutral.color),
    )
}

/** iOS `accessibilityReduceMotion` 대응: 시스템 애니메이션 배율이 0이면 태그 전환을 끈다(designkit `FCLivePulse`와 같은 판정). */
@Composable
private fun rememberReduceMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    return remember(resolver) { Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
}
