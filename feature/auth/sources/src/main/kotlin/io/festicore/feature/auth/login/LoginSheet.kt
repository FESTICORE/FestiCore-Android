package io.festicore.feature.auth.login

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.festicore.feature.auth.AuthRoute
import io.festicore.feature.auth.LaunchLoginResult
import io.festicore.feature.auth.LoginContext
import io.festicore.ui.designkit.R as DesignKitR
import io.festicore.ui.designkit.component.action.FCLoginButton
import io.festicore.ui.designkit.component.action.FCLoginButtonProvider
import io.festicore.ui.designkit.component.modal.FCSheet
import io.festicore.ui.designkit.component.modal.LocalFCSheetDismiss
import io.festicore.ui.designkit.tokens.FestiCoreSemanticColor
import io.festicore.ui.designkit.tokens.FestiCoreSpacing
import io.festicore.ui.designkit.tokens.FestiCoreTextStyle
import io.festicore.ui.designkit.typography.textStyle
import io.festicore.ui.designkit.typography.textStyleWithoutLineBox
import org.koin.compose.viewmodel.koinViewModel
import org.orbitmvi.orbit.compose.collectAsState
import org.orbitmvi.orbit.compose.collectSideEffect

// iOS `LoginModalView.Metric`. 토큰에 없는 값은 iOS와 같은 리터럴이다.
private val MarkSize = 56.dp
private val IconSize = 24.dp

/**
 * 메인 탭 위 문맥별 로그인 시트(iOS `LoginModalView`, `fcModal(height: .auto)`). [context]가 있으면 띄운다.
 * 로그인 → 계정 확인은 첫 진입 로그인과 같은 [LaunchLoginViewModel]을 쓴다(설계 쟁점 F1 a).
 * 시트를 열 때마다 새 로그인 상태로 시작하도록 [presentationId]마다 ViewModel을 따로 만든다.
 * "나중에 할게요" · 딤 탭 · 끌기 · 뒤로는 시트가 내려간 뒤 [onDismiss]를 부른다. 로그인 중에는 닫히지 않는다(닫으면 흐름이 취소된다).
 * 로그인 결과는 [onResult](완료 · 세션 잃음), 신규 · 조건 미달 계정은 [onSignUpRequired]로 알린다.
 * [startsSignIn]이면 시트를 띄우지 않은 채 이 로그인 상태로 바로 로그인을 시작하고(마이 Google 바로 로그인, 설계 쟁점 M12),
 * 실패는 [onSignInFailed]로 알린다. 상위가 같은 [presentationId]로 [context]를 주면 실패 문구가 시트에 그대로 보인다.
 */
@Composable
fun LoginSheet(
    context: LoginContext?,
    presentationId: Int,
    onResult: (LaunchLoginResult) -> Unit,
    onSignUpRequired: (AuthRoute.SignUp) -> Unit,
    onDismiss: () -> Unit,
    startsSignIn: Boolean = false,
    onSignInFailed: (showsMessage: Boolean) -> Unit = {},
) {
    val viewModel: LaunchLoginViewModel = koinViewModel(key = "loginSheet-$presentationId")
    val state by viewModel.collectAsState()

    viewModel.collectSideEffect { sideEffect ->
        when (sideEffect) {
            is LaunchLoginSideEffect.Finished -> onResult(sideEffect.result)
            is LaunchLoginSideEffect.SignUpRequired -> onSignUpRequired(sideEffect.route)
            is LaunchLoginSideEffect.SignInFailed -> onSignInFailed(sideEffect.showsMessage)
        }
    }
    // 진행 중인 로그인이 있으면 ViewModel이 다시 누르기를 무시한다(화면 회전 등으로 다시 실행돼도 한 번만 시작한다).
    LaunchedEffect(presentationId, startsSignIn) { if (startsSignIn) viewModel.onSignInTapped() }

    FCSheet(item = context, onDismissRequest = onDismiss, isDismissible = !state.isSigningIn) { shown ->
        val dismiss = LocalFCSheetDismiss.current
        LoginSheetContent(
            context = shown,
            state = state,
            onSignInTapped = viewModel::onSignInTapped,
            onCloseTapped = dismiss,
        )
    }
}

/** iOS `LoginModalView.body`: 시트 내용(위 8 · 아래 20, 항목 간격 16) 아래에 오류 문구. */
@Composable
internal fun LoginSheetContent(
    context: LoginContext,
    state: LaunchLoginState,
    onSignInTapped: () -> Unit,
    onCloseTapped: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        SheetBody(context = context, state = state, onSignInTapped = onSignInTapped, onCloseTapped = onCloseTapped)
        state.errorMessage?.let { message ->
            // iOS `LoginModalView`는 시트 내용 아래에 아래 여백 16으로 둔다. 글자 모양은 첫 진입 로그인 화면과 같게 둔다.
            BasicText(
                text = message,
                style =
                    FestiCoreTextStyle.baseFootnoteMedium.textStyleWithoutLineBox.copy(
                        color = FestiCoreSemanticColor.statusNegative.color,
                        textAlign = TextAlign.Center,
                    ),
                modifier = Modifier.padding(bottom = FestiCoreSpacing.paddingP16),
            )
        }
    }
}

@Composable
private fun SheetBody(
    context: LoginContext,
    state: LaunchLoginState,
    onSignInTapped: () -> Unit,
    onCloseTapped: () -> Unit,
) {
    val copy = context.sheetCopy
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(top = FestiCoreSpacing.paddingP8, bottom = FestiCoreSpacing.paddingP20),
        verticalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP16),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier =
                Modifier
                    .size(MarkSize)
                    .background(FestiCoreSemanticColor.staticLime.color, CircleShape)
                    .clearAndSetSemantics {},
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(context.markIcon),
                contentDescription = null,
                modifier = Modifier.size(IconSize),
                colorFilter = ColorFilter.tint(FestiCoreSemanticColor.staticInk.color),
            )
        }
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = FestiCoreSpacing.paddingP20),
            verticalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP8),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BasicText(
                text = copy.title,
                style =
                    FestiCoreTextStyle.baseTitle3Bold.textStyle.copy(
                        color = FestiCoreSemanticColor.labelNormal.color,
                        textAlign = TextAlign.Center,
                    ),
                modifier = Modifier.semantics { heading() },
            )
            BasicText(
                text = copy.message,
                style =
                    FestiCoreTextStyle.baseSubheadlineRegular.textStyle.copy(
                        color = FestiCoreSemanticColor.labelNeutral.color,
                        textAlign = TextAlign.Center,
                    ),
            )
        }
        FCLoginButton(
            provider = FCLoginButtonProvider.Google,
            modifier =
                Modifier.padding(
                    horizontal = FestiCoreSpacing.paddingP20,
                    vertical = FestiCoreSpacing.paddingP8,
                ),
            onClick = { if (!state.isSigningIn) onSignInTapped() },
        )
        BasicText(
            text = "나중에 할게요",
            style = FestiCoreTextStyle.baseFootnoteMedium.textStyle.copy(color = FestiCoreSemanticColor.labelAssistive.color),
            modifier =
                Modifier.clickable(
                    enabled = !state.isSigningIn,
                    interactionSource = null,
                    indication = null,
                    role = Role.Button,
                    onClick = onCloseTapped,
                ),
        )
    }
}

/** 시트 제목과 본문(iOS `LoginModalView.copy`). */
internal data class LoginSheetCopy(
    val title: String,
    val message: String,
)

internal val LoginContext.sheetCopy: LoginSheetCopy
    get() =
        when (this) {
            LoginContext.Profile ->
                LoginSheetCopy("계정 정보를 보려면 로그인이 필요해요", "로그인 후 프로필을 확인할 수 있어요.")

            LoginContext.Flag, LoginContext.Follow ->
                LoginSheetCopy(
                    "깃발을 팔로우 하려면 로그인이 필요해요",
                    "내 취향의 깃발을 팔로우하고,\n함께 페스티벌을 즐길 사람들과 크루를 만들어봐요.",
                )

            LoginContext.Crew ->
                LoginSheetCopy(
                    "크루와 함께 보려면 로그인이 필요해요",
                    "크루원끼리 지금 어느 존에 있는지 서로 볼 수 있어요.\n공연과 타임테이블은 로그인 없이 그대로 쓸 수 있어요.",
                )

            is LoginContext.CrewJoin ->
                LoginSheetCopy(
                    "크루에 들어가려면 로그인이 필요해요",
                    "로그인하면 바로 ${crewName ?: "크루"}에 들어가요.\n크루원끼리만 서로의 존과 상태를 볼 수 있어요.",
                )

            LoginContext.Bookmark ->
                LoginSheetCopy("축제를 저장하려면 로그인이 필요해요", "로그인 후 선택한 축제를 저장할 수 있어요.")

            LoginContext.Report ->
                LoginSheetCopy("깃발을 신고하려면 로그인이 필요해요", "로그인 후 문제가 있는 깃발을 신고할 수 있어요.")
        }

/** 크루 문맥은 사람 아이콘, 그 외는 깃발(iOS `store.context == .crew ? .users24 : .flag`). */
@get:DrawableRes
internal val LoginContext.markIcon: Int
    get() = if (this == LoginContext.Crew) DesignKitR.drawable.ic_users_24 else DesignKitR.drawable.ic_flag

@Preview(showBackground = true)
@Composable
private fun LoginSheetContentPreview() {
    LoginSheetContent(
        context = LoginContext.CrewJoin(crewName = "미리보기 크루"),
        state = LaunchLoginState(errorMessage = LaunchLoginViewModel.SIGN_IN_FAILED),
        onSignInTapped = {},
        onCloseTapped = {},
    )
}
