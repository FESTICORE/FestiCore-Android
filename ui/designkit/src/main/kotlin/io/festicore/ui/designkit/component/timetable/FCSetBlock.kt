package io.festicore.ui.designkit.component.timetable

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.festicore.ui.designkit.R
import io.festicore.ui.designkit.tokens.FestiCoreRadius
import io.festicore.ui.designkit.tokens.FestiCoreSemanticColor
import io.festicore.ui.designkit.tokens.FestiCoreSpacing
import io.festicore.ui.designkit.tokens.FestiCoreTextStyle
import io.festicore.ui.designkit.typography.textStyleWithoutLineBox

private val PointsPerMinute = 2.dp
private val BorderWidth = 1.dp
private val ChangedBorderWidth = 3.dp
private val CheckSize = 20.dp
private val CheckIconSize = 12.dp

/** iOS `FCSetBlock.State` */
sealed interface FCSetBlockState {
    data object Default : FCSetBlockState

    /** 내 일정 */
    data object Mine : FCSetBlockState

    /** 시간 변경 */
    data class Changed(
        val oldTime: String,
        val change: String,
    ) : FCSetBlockState

    /** 내 일정끼리 겹침 */
    data class Overlap(val label: String = "겹침") : FCSetBlockState
}

/** 공연 길이에 맞는 블록 높이(iOS `FCSetBlock.height(minutes:)`). 0 이하는 0이다. */
fun fcSetBlockHeight(minutes: Int): Dp = PointsPerMinute * maxOf(0, minutes)

/** iOS `FCSetBlock.accessibilityValue` */
internal fun fcSetBlockAccessibilityValue(time: String, state: FCSetBlockState): String = when (state) {
    FCSetBlockState.Default -> time
    FCSetBlockState.Mine -> "$time, 내 일정"
    is FCSetBlockState.Changed -> "$time, 변경 전 ${state.oldTime}, ${state.change}"
    is FCSetBlockState.Overlap -> "$time, ${state.label}"
}

/**
 * 타임테이블의 셋 블록. 높이는 공연 길이(1분 2dp, [fcSetBlockHeight])이고 부모가 정한다.
 *
 * 블록이 짧으면 태그 → 시간 → 여백 순으로 덜어 내고, 24dp 미만이면 글자를 그리지 않는다.
 *
 * @param state 상태(iOS `state(_:)`).
 */
@Composable
fun FCSetBlock(
    artist: String,
    time: String,
    modifier: Modifier = Modifier,
    state: FCSetBlockState = FCSetBlockState.Default,
) {
    val isOnLime = state == FCSetBlockState.Mine || state is FCSetBlockState.Overlap
    val shape = RoundedCornerShape(FestiCoreRadius.r12)
    val changedColor = FestiCoreSemanticColor.accentPointOrange.color
    val artistColor = if (isOnLime) FestiCoreSemanticColor.staticInk else FestiCoreSemanticColor.labelNormal
    val contentInsets =
        PaddingValues(
            start = FestiCoreSpacing.paddingP12,
            top = FestiCoreSpacing.paddingP12,
            end = if (state == FCSetBlockState.Mine) FestiCoreSpacing.paddingP24 else FestiCoreSpacing.paddingP12,
            bottom = FestiCoreSpacing.paddingP12,
        )
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .clip(shape)
                .background(if (isOnLime) FestiCoreSemanticColor.accentPointLime.color else FestiCoreSemanticColor.backgroundElevated.color)
                .drawWithContent {
                    drawContent()
                    if (state is FCSetBlockState.Changed) {
                        drawRect(changedColor, size = Size(ChangedBorderWidth.toPx(), size.height))
                    }
                }.then(
                    if (state == FCSetBlockState.Default) {
                        Modifier.border(BorderWidth, FestiCoreSemanticColor.lineNormal.color, shape)
                    } else {
                        Modifier
                    },
                ).clearAndSetSemantics {
                    contentDescription = artist
                    stateDescription = fcSetBlockAccessibilityValue(time, state)
                },
    ) {
        // iOS ViewThatFits(in: .vertical): 세로로 들어가는 첫 후보만 그리고, 모두 넘치면 비운다.
        Layout(
            contents =
                listOf(
                    { SetBlockContent(artist, time, state, artistColor, showsTime = true, showsTag = true, contentInsets) },
                    { SetBlockContent(artist, time, state, artistColor, showsTime = true, showsTag = false, contentInsets) },
                    { SetBlockContent(artist, time, state, artistColor, showsTime = false, showsTag = false, contentInsets) },
                    {
                        ArtistText(
                            artist = artist,
                            style = FestiCoreTextStyle.baseCaption1Bold.textStyleWithoutLineBox,
                            color = artistColor,
                            modifier =
                                Modifier.padding(
                                    vertical = FestiCoreSpacing.paddingP4,
                                    horizontal = FestiCoreSpacing.paddingP12,
                                ),
                        )
                    },
                ),
            modifier = Modifier.fillMaxSize(),
        ) { measurables, constraints ->
            val candidateConstraints =
                Constraints(
                    minWidth = if (constraints.hasBoundedWidth) constraints.maxWidth else 0,
                    maxWidth = constraints.maxWidth,
                )
            val placeables = measurables.map { it.first().measure(candidateConstraints) }
            // 부모가 높이를 정하지 않으면 첫 후보 높이를 쓴다.
            val height = if (constraints.hasBoundedHeight) constraints.maxHeight else placeables.first().height
            val fitting = placeables.firstOrNull { it.height <= height }
            val width = if (constraints.hasBoundedWidth) constraints.maxWidth else placeables.first().width
            layout(width, height) {
                fitting?.place(0, 0)
            }
        }
        if (state == FCSetBlockState.Mine) {
            Box(
                modifier =
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(FestiCoreSpacing.paddingP8)
                        .size(CheckSize)
                        .background(FestiCoreSemanticColor.staticInk.color, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_check),
                    contentDescription = null,
                    modifier = Modifier.size(CheckIconSize),
                    colorFilter = ColorFilter.tint(FestiCoreSemanticColor.staticLime.color),
                )
            }
        }
    }
}

@Composable
private fun SetBlockContent(
    artist: String,
    time: String,
    state: FCSetBlockState,
    artistColor: FestiCoreSemanticColor,
    showsTime: Boolean,
    showsTag: Boolean,
    contentInsets: PaddingValues,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(contentInsets),
        verticalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP4),
    ) {
        ArtistText(artist = artist, style = FestiCoreTextStyle.baseCalloutBold.textStyleWithoutLineBox, color = artistColor)
        if (showsTime) TimeRow(time = time, state = state)
        if (showsTag) Tag(state = state)
    }
}

@Composable
private fun ArtistText(
    artist: String,
    style: TextStyle,
    color: FestiCoreSemanticColor,
    modifier: Modifier = Modifier,
) {
    BasicText(
        text = artist,
        modifier = modifier.fillMaxWidth(),
        style = style.copy(color = color.color),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun TimeRow(time: String, state: FCSetBlockState) {
    if (state is FCSetBlockState.Changed) {
        val assistive = FestiCoreTextStyle.baseCaption1Regular.textStyleWithoutLineBox.copy(
            color = FestiCoreSemanticColor.labelAssistive.color,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP4)) {
            BasicText(
                text = state.oldTime,
                style = assistive.copy(textDecoration = TextDecoration.LineThrough),
                maxLines = 1,
                softWrap = false,
            )
            BasicText(text = "→", style = assistive, maxLines = 1, softWrap = false)
            BasicText(
                text = time,
                style = FestiCoreTextStyle.baseCaption1Bold.textStyleWithoutLineBox.copy(
                    color = FestiCoreSemanticColor.accentForegroundOrange.color,
                ),
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
        }
    } else {
        val isOnLime = state == FCSetBlockState.Mine || state is FCSetBlockState.Overlap
        BasicText(
            text = time,
            style =
                FestiCoreTextStyle.baseCaption1Regular.textStyleWithoutLineBox.copy(
                    color = if (isOnLime) FestiCoreSemanticColor.staticalphaBlack60.color else FestiCoreSemanticColor.labelAssistive.color,
                ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun Tag(state: FCSetBlockState) {
    when (state) {
        is FCSetBlockState.Changed ->
            TagText(
                text = state.change,
                foreground = FestiCoreSemanticColor.accentForegroundOrange,
                background = FestiCoreSemanticColor.accentBackgroundOrange,
                radius = FestiCoreRadius.r4,
            )

        is FCSetBlockState.Overlap ->
            TagText(
                text = state.label,
                foreground = FestiCoreSemanticColor.staticInk,
                background = FestiCoreSemanticColor.accentPointOrange,
                radius = FestiCoreRadius.r8,
            )

        FCSetBlockState.Default, FCSetBlockState.Mine -> Unit
    }
}

@Composable
private fun TagText(
    text: String,
    foreground: FestiCoreSemanticColor,
    background: FestiCoreSemanticColor,
    radius: Dp,
) {
    BasicText(
        text = text,
        modifier =
            Modifier
                .background(background.color, RoundedCornerShape(radius))
                .padding(vertical = FestiCoreSpacing.marginM2, horizontal = FestiCoreSpacing.marginM6),
        style = FestiCoreTextStyle.baseCaption2Bold.textStyleWithoutLineBox.copy(color = foreground.color),
        maxLines = 1,
    )
}
