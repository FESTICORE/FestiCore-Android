package io.festicore.feature.auth

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.festicore.feature.auth.sources.R
import io.festicore.ui.designkit.component.action.FCLoginButton
import io.festicore.ui.designkit.component.action.FCLoginButtonProvider
import io.festicore.ui.designkit.tokens.FestiCoreSemanticColor
import io.festicore.ui.designkit.tokens.FestiCoreSpacing
import io.festicore.ui.designkit.tokens.FestiCoreTextStyle
import io.festicore.ui.designkit.typography.textStyle
import io.festicore.ui.designkit.typography.textStyleWithoutLineBox
import org.koin.compose.viewmodel.koinViewModel
import org.orbitmvi.orbit.compose.collectAsState
import org.orbitmvi.orbit.compose.collectSideEffect

// iOS LoginView 수치. 토큰에 없는 값은 iOS와 같은 리터럴이다.
private val LogoWidth = 280.dp
private val LogoHeight = 42.44.dp
private const val DESIGN_WIDTH = 393f
private const val DESIGN_HEIGHT = 852f

@Composable
fun LaunchLoginScreen(
    onResult: (LaunchLoginResult) -> Unit,
    onSignUpRequired: (AuthRoute.SignUp) -> Unit,
    viewModel: LaunchLoginViewModel = koinViewModel(),
) {
    val state by viewModel.collectAsState()

    viewModel.collectSideEffect { sideEffect ->
        when (sideEffect) {
            is LaunchLoginSideEffect.Finished -> onResult(sideEffect.result)
            is LaunchLoginSideEffect.SignUpRequired -> onSignUpRequired(sideEffect.route)
        }
    }

    LaunchLoginContent(
        state = state,
        onSignInTapped = viewModel::onSignInTapped,
        onSkipTapped = viewModel::onSkipTapped,
    )
}

/** iOS `LoginView`(S14 A). 장식 별은 화면 전체(안전 영역 포함)에 시안 비율로 놓고, 내용은 안전 영역 안에 둔다. */
@Composable
internal fun LaunchLoginContent(
    state: LaunchLoginState,
    onSignInTapped: () -> Unit,
    onSkipTapped: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(FestiCoreSemanticColor.backgroundNormal.color),
    ) {
        Column(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
            Introduction(modifier = Modifier.weight(1f).fillMaxWidth())
            Actions(state = state, onSignInTapped = onSignInTapped, onSkipTapped = onSkipTapped)
        }
        Decorations()
    }
}

/** 시안 Contents: 좌우 16 안에서 로고 아래 20 간격으로 제목과 설명을 둔다. */
@Composable
private fun Introduction(modifier: Modifier) {
    Column(
        modifier = modifier.padding(horizontal = FestiCoreSpacing.paddingP16),
        verticalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP20, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            painter = painterResource(R.drawable.logo_3d),
            contentDescription = "FestiCore",
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(width = LogoWidth, height = LogoHeight),
        )
        Column(
            modifier = Modifier.padding(horizontal = FestiCoreSpacing.paddingP16),
            verticalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP20),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BasicText(
                text = "가장 뜨거운 현장 속으로",
                style =
                    FestiCoreTextStyle.baseTitle2Bold.textStyle.copy(
                        color = FestiCoreSemanticColor.labelNormal.color,
                        textAlign = TextAlign.Center,
                    ),
                modifier = Modifier.semantics { heading() },
            )
            BasicText(
                text = "로그인하면 깃발을 팔로우하고 크루를 만들 수 있어요.\n타임테이블과 라이브 보드는 로그인 없이도 볼 수 있어요",
                style =
                    FestiCoreTextStyle.baseFootnoteRegular.textStyle.copy(
                        color = FestiCoreSemanticColor.labelNeutral.color,
                        textAlign = TextAlign.Center,
                    ),
            )
        }
    }
}

/** 시안 Actions: 위 12 · 아래 8, 버튼 · 건너뛰기 간격 12. 로그인 중에는 모두 비활성이다. */
@Composable
private fun Actions(
    state: LaunchLoginState,
    onSignInTapped: () -> Unit,
    onSkipTapped: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = FestiCoreSpacing.paddingP16)
                .padding(top = FestiCoreSpacing.paddingP12, bottom = FestiCoreSpacing.paddingP8),
        verticalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP12),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        state.errorMessage?.let { message ->
            // iOS `LoginView`는 오류 문구에 줄 박스를 쓰지 않는다(`festiCoreFont`만).
            BasicText(
                text = message,
                style =
                    FestiCoreTextStyle.baseFootnoteMedium.textStyleWithoutLineBox.copy(
                        color = FestiCoreSemanticColor.statusNegative.color,
                        textAlign = TextAlign.Center,
                    ),
            )
        }
        FCLoginButton(
            provider = FCLoginButtonProvider.Google,
            onClick = { if (!state.isSigningIn) onSignInTapped() },
        )
        BasicText(
            text = "지금은 건너뛸게요",
            style = FestiCoreTextStyle.baseFootnoteMedium.textStyle.copy(color = FestiCoreSemanticColor.labelAssistive.color),
            modifier =
                Modifier.testTag(LaunchLoginTestTag.SKIP).clickable(
                    enabled = !state.isSigningIn,
                    interactionSource = null,
                    indication = null,
                    role = Role.Button,
                    onClick = onSkipTapped,
                ),
        )
    }
}

/** 시안 393×852 기준 장식 별 중심 · 크기 · 회전(도)과 색. iOS `LoginView.decorations`. */
private enum class DecorationShape(@param:DrawableRes val drawable: Int) {
    Star8(R.drawable.sparkle_star8),
    Star4(R.drawable.sparkle_star4),
}

/** `Secondary`는 라이트에서 Secondary/Normal, 다크에서 Static/White다. */
private enum class DecorationTone { Lime, Pink, Purple, Secondary }

private data class Decoration(
    val shape: DecorationShape,
    val centerX: Float,
    val centerY: Float,
    val size: Dp,
    val rotation: Float,
    val tone: DecorationTone,
)

private val decorations =
    listOf(
        Decoration(DecorationShape.Star8, 48f, 96f, 22.dp, -100f, DecorationTone.Lime),
        Decoration(DecorationShape.Star4, 170f, 64f, 14.dp, 40f, DecorationTone.Secondary),
        Decoration(DecorationShape.Star8, 300f, 108f, 26.dp, 20f, DecorationTone.Pink),
        Decoration(DecorationShape.Star4, 362f, 196f, 16.dp, 100f, DecorationTone.Lime),
        Decoration(DecorationShape.Star4, 34f, 214f, 16.dp, -40f, DecorationTone.Purple),
        Decoration(DecorationShape.Star8, 112f, 170f, 20.dp, -20f, DecorationTone.Secondary),
        Decoration(DecorationShape.Star4, 252f, 184f, 12.dp, -100f, DecorationTone.Purple),
        Decoration(DecorationShape.Star8, 16f, 336f, 14.dp, 40f, DecorationTone.Lime),
        Decoration(DecorationShape.Star8, 376f, 318f, 20.dp, 20f, DecorationTone.Purple),
        Decoration(DecorationShape.Star8, 22f, 470f, 22.dp, 100f, DecorationTone.Pink),
        Decoration(DecorationShape.Star4, 372f, 532f, 14.dp, -40f, DecorationTone.Secondary),
        Decoration(DecorationShape.Star4, 70f, 592f, 18.dp, -20f, DecorationTone.Lime),
        Decoration(DecorationShape.Star4, 322f, 600f, 16.dp, 40f, DecorationTone.Pink),
    )

/** 화면 전체에 시안 비율대로 흩뿌린 장식 별. 터치와 읽기 도구에서는 숨긴다. */
@Composable
private fun Decorations() {
    val isDark = isSystemInDarkTheme()
    BoxWithConstraints(modifier = Modifier.fillMaxSize().clearAndSetSemantics {}) {
        decorations.forEach { item ->
            val color =
                when (item.tone) {
                    DecorationTone.Lime -> FestiCoreSemanticColor.staticLime
                    DecorationTone.Pink -> FestiCoreSemanticColor.accentPointPink
                    DecorationTone.Purple -> FestiCoreSemanticColor.accentPointPurple
                    DecorationTone.Secondary -> if (isDark) FestiCoreSemanticColor.staticWhite else FestiCoreSemanticColor.secondaryNormal
                }.color
            Image(
                painter = painterResource(item.shape.drawable),
                contentDescription = null,
                colorFilter = ColorFilter.tint(color),
                modifier =
                    Modifier
                        .offset(
                            x = maxWidth * (item.centerX / DESIGN_WIDTH) - item.size / 2,
                            y = maxHeight * (item.centerY / DESIGN_HEIGHT) - item.size / 2,
                        ).size(item.size)
                        .rotate(item.rotation),
            )
        }
    }
}

/** iOS `LoginView`(`LoginView.swift:283`)와 같은 UI 테스트 식별자. */
internal object LaunchLoginTestTag {
    const val SKIP = "launchLogin.skip"
}
