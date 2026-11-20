package io.festicore.ui.designkit.component.card

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.festicore.ui.designkit.component.action.FCHeroFollowButton
import io.festicore.ui.designkit.component.label.FCStatusChip
import io.festicore.ui.designkit.component.label.FCStatusChipStatus
import io.festicore.ui.designkit.component.label.FCTag
import io.festicore.ui.designkit.component.label.FCTagTone
import io.festicore.ui.designkit.tokens.FestiCoreSemanticColor
import io.festicore.ui.designkit.tokens.FestiCoreSpacing
import io.festicore.ui.designkit.tokens.FestiCoreState
import io.festicore.ui.designkit.tokens.FestiCoreTextStyle
import io.festicore.ui.designkit.typography.textStyle

private val Height = 360.dp
private val DividerHeight = 1.dp

/** Figma S05 실측: 이름 줄과 태그 줄 사이. */
private val NameTagSpacing = 10.dp

/**
 * 깃발 상세 상단 히어로. [background]는 사진 등 배경이며 위에 어두운 그라디언트가 덮인다.
 * [isFollowEnabled]가 false면 팔로우 버튼만 끈다(오프라인 · 요청 중).
 */
@Composable
fun FCFlagHero(
    name: String,
    followers: String,
    tags: List<String>,
    status: FCStatusChipStatus,
    intro: String,
    isFollowing: Boolean,
    modifier: Modifier = Modifier,
    isFollowEnabled: Boolean = true,
    onFollow: () -> Unit,
    background: @Composable () -> Unit,
) {
    Box(modifier = modifier.fillMaxWidth().height(Height).clipToBounds()) {
        Box(modifier = Modifier.matchParentSize()) { background() }
        Box(
            modifier =
                Modifier.matchParentSize().background(
                    Brush.verticalGradient(
                        listOf(
                            FestiCoreSemanticColor.staticBlack.color.copy(alpha = 0f),
                            FestiCoreSemanticColor.staticalphaBlack80.color,
                        ),
                    ),
                ),
        )
        Column(
            modifier = Modifier.matchParentSize().padding(FestiCoreSpacing.paddingP16),
            verticalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP16),
        ) {
            Spacer(modifier = Modifier.weight(1f))
            Row(
                horizontalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP12),
                verticalAlignment = Alignment.Bottom,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(NameTagSpacing),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP8)) {
                        BasicText(
                            text = name,
                            modifier = Modifier.alignByBaseline().weight(1f, fill = false),
                            style = FestiCoreTextStyle.baseTitle2Bold.textStyle.copy(color = FestiCoreSemanticColor.staticWhite.color),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        BasicText(
                            text = followers,
                            modifier = Modifier.alignByBaseline(),
                            style =
                                FestiCoreTextStyle.baseFootnoteMedium.textStyle.copy(
                                    color = FestiCoreSemanticColor.staticalphaWhite80.color,
                                ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP4)) {
                        tags.forEach { FCTag(it, tone = FCTagTone.OnImage) }
                    }
                }
                FCHeroFollowButton(
                    isFollowing = isFollowing,
                    modifier =
                        Modifier
                            .alpha(if (isFollowEnabled) 1f else FestiCoreState.disabledOpacity)
                            .semantics { if (!isFollowEnabled) disabled() },
                    onClick = { if (isFollowEnabled) onFollow() },
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP12)) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(DividerHeight)
                            .background(FestiCoreSemanticColor.staticalphaWhite40.color),
                )
                BasicText(
                    text = intro,
                    style =
                        FestiCoreTextStyle.baseSubheadlineRegular.textStyle.copy(
                            color = FestiCoreSemanticColor.staticalphaWhite80.color,
                        ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        // Figma S05: 상태 칩은 사진 오른쪽 위에 두고, 화면 모드와 관계없이 시안처럼 밝은 칩으로 보인다.
        LightMode {
            FCStatusChip(status, modifier = Modifier.align(Alignment.TopEnd).padding(FestiCoreSpacing.paddingP16))
        }
    }
}

/** 시스템 다크 모드와 관계없이 [content]의 의미 색을 라이트 값으로 고정한다. */
@Composable
private fun LightMode(content: @Composable () -> Unit) {
    val configuration = LocalConfiguration.current
    val light =
        remember(configuration) {
            Configuration(configuration).apply {
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or Configuration.UI_MODE_NIGHT_NO
            }
        }
    CompositionLocalProvider(LocalConfiguration provides light, content = content)
}
