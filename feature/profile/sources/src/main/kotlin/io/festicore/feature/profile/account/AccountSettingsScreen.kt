package io.festicore.feature.profile.account

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.festicore.domain.auth.entity.AuthNicknameRule
import io.festicore.domain.auth.entity.AuthUser
import io.festicore.feature.profile.AccountState
import io.festicore.ui.designkit.component.action.FCButton
import io.festicore.ui.designkit.component.action.FCButtonSize
import io.festicore.ui.designkit.component.action.FCButtonStyle
import io.festicore.ui.designkit.component.layout.fcScrollBottomInset
import io.festicore.ui.designkit.component.list.FCListRow
import io.festicore.ui.designkit.component.modal.FCSheet
import io.festicore.ui.designkit.component.navigation.FCTopBar
import io.festicore.ui.designkit.component.navigation.FCTopBarStyle
import io.festicore.ui.designkit.component.popup.FCDialog
import io.festicore.ui.designkit.component.popup.FCDialogItem
import io.festicore.ui.designkit.component.popup.FCDialogTextAlign
import io.festicore.ui.designkit.component.selection.FCTextField
import io.festicore.ui.designkit.component.skeleton.FCSkeletonShape
import io.festicore.ui.designkit.component.skeleton.FCSkeletonView
import io.festicore.ui.designkit.tokens.FestiCoreRadius
import io.festicore.ui.designkit.tokens.FestiCoreSemanticColor
import io.festicore.ui.designkit.tokens.FestiCoreSpacing
import io.festicore.ui.designkit.tokens.FestiCoreTextStyle
import io.festicore.ui.designkit.typography.textStyle
import org.orbitmvi.orbit.compose.collectAsState
import org.orbitmvi.orbit.compose.collectSideEffect

// iOS `AccountSettingsView`의 리터럴 값.
private val RowMinHeight = 56.dp
private val SkeletonHeight = 113.dp
private val DividerHeight = 1.dp

/**
 * 계정(iOS `AccountSettingsView`, Figma S13-D). ViewModel을 잇고 [account]가 바뀌면 ViewModel에 알린다.
 * 요청은 콜백으로만 알린다. 로그아웃 · 탈퇴 · 재확인 실행은 앱(A1), 경로 연결은 T1이다.
 */
@Composable
fun AccountSettingsScreen(
    viewModel: AccountSettingsViewModel,
    account: AccountState,
    onBackRequested: () -> Unit,
    onLogoutRequested: () -> Unit,
    onWithdrawRequested: () -> Unit,
    onRefreshRequested: () -> Unit,
    onNicknameUpdated: (AuthUser) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.collectAsState()
    viewModel.collectSideEffect { sideEffect ->
        when (sideEffect) {
            AccountSettingsSideEffect.BackRequested -> onBackRequested()
            AccountSettingsSideEffect.LogoutRequested -> onLogoutRequested()
            AccountSettingsSideEffect.WithdrawRequested -> onWithdrawRequested()
            AccountSettingsSideEffect.RefreshRequested -> onRefreshRequested()
            is AccountSettingsSideEffect.NicknameUpdated -> onNicknameUpdated(sideEffect.user)
        }
    }
    LaunchedEffect(viewModel, account) { viewModel.onAccountChanged(account) }
    AccountSettingsContent(
        state = state,
        modifier = modifier,
        onBackTapped = viewModel::onBackTapped,
        onNicknameTapped = viewModel::onNicknameTapped,
        onRefreshTapped = viewModel::onRefreshTapped,
        onWithdrawTapped = viewModel::onWithdrawTapped,
        onLogoutTapped = viewModel::onLogoutTapped,
    )
    val nickname = (state.destination as? AccountSettingsDestination.Nickname)?.edit
    FCSheet(
        item = nickname,
        onDismissRequest = viewModel::onNicknameDismissed,
        // 저장 중에 닫으면 결과를 보여 줄 곳이 없어 닫기를 막는다.
        isDismissible = nickname?.isSaving != true,
    ) { edit ->
        NicknameEditContent(
            state = edit,
            onNicknameChanged = viewModel::onNicknameChanged,
            onSaveTapped = viewModel::onNicknameSaveTapped,
        )
    }
    (state.destination as? AccountSettingsDestination.Alert)?.let { alert ->
        FCDialog(
            item =
                FCDialogItem(
                    title = alert.confirmation.title,
                    message = alert.confirmation.message,
                    confirmTitle = alert.confirmation.confirmTitle,
                    onConfirm = viewModel::onConfirmationConfirmed,
                ),
            onDismiss = viewModel::onConfirmationDismissed,
            // Figma S13-E: 확인 팝업 제목 · 설명은 가운데 정렬
            textAlign = FCDialogTextAlign.Center,
        )
    }
}

/** 계정(순수 UI). 내 정보 · 계정 삭제 · 결과 문구를 스크롤하고 아래에 로그아웃 버튼을 고정한다. */
@Composable
fun AccountSettingsContent(
    state: AccountSettingsState,
    modifier: Modifier = Modifier,
    onBackTapped: () -> Unit = {},
    onNicknameTapped: () -> Unit = {},
    onRefreshTapped: () -> Unit = {},
    onWithdrawTapped: () -> Unit = {},
    onLogoutTapped: () -> Unit = {},
) {
    Column(modifier = modifier.fillMaxSize().background(FestiCoreSemanticColor.backgroundNormal.color)) {
        FCTopBar(style = FCTopBarStyle.Back, title = "계정", onBack = onBackTapped)
        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = FestiCoreSpacing.paddingP16)
                    .padding(top = FestiCoreSpacing.paddingP16)
                    .fcScrollBottomInset(),
            verticalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP24),
        ) {
            MyInfo(state = state, onNicknameTapped = onNicknameTapped, onRefreshTapped = onRefreshTapped)
            Withdrawal(isEnabled = !state.isLoading, onWithdrawTapped = onWithdrawTapped)
            state.message?.takeIf { it.isNotEmpty() && !state.isLoading }?.let { message ->
                Text(
                    text = message,
                    style = FestiCoreTextStyle.baseSubheadlineRegular.textStyle,
                    color = FestiCoreSemanticColor.statusNegative.color,
                )
            }
        }
        // Figma S13-D Logout: Button ghost · M
        FCButton(
            title = "로그아웃",
            style = FCButtonStyle.Ghost,
            size = FCButtonSize.Medium,
            isEnabled = !state.isLoading,
            onClick = onLogoutTapped,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .wrapContentWidth(Alignment.CenterHorizontally)
                    .padding(horizontal = FestiCoreSpacing.paddingP16)
                    .padding(bottom = FestiCoreSpacing.paddingP16),
        )
    }
}

@Composable
private fun MyInfo(
    state: AccountSettingsState,
    onNicknameTapped: () -> Unit,
    onRefreshTapped: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP8)) {
        SectionTitle("내 정보")
        val user = state.profile?.user
        when {
            user != null ->
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(FestiCoreRadius.r16))
                            .background(FestiCoreSemanticColor.backgroundElevated.color),
                ) {
                    FCListRow(label = "닉네임", value = user.nickname ?: "설정 안 됨", onClick = onNicknameTapped)
                    // Figma S13-D Divider: 카드 너비를 채운다.
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(DividerHeight)
                            .background(FestiCoreSemanticColor.lineNormal.color),
                    )
                    InfoRow(title = "로그인", value = accountProviderName(user.provider))
                }

            state.isLoading ->
                FCSkeletonView(
                    shape = FCSkeletonShape.Round(cornerRadius = FestiCoreRadius.r16),
                    modifier = Modifier.fillMaxWidth().height(SkeletonHeight),
                )

            else ->
                Column(verticalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP8)) {
                    Text(
                        text = "계정 정보를 확인할 수 없습니다",
                        style = FestiCoreTextStyle.baseSubheadlineRegular.textStyle,
                        color = FestiCoreSemanticColor.labelNeutral.color,
                    )
                    FCButton(
                        title = "계정 정보 다시 확인",
                        style = FCButtonStyle.Secondary,
                        size = FCButtonSize.Medium,
                        onClick = onRefreshTapped,
                    )
                }
        }
    }
}

@Composable
private fun Withdrawal(isEnabled: Boolean, onWithdrawTapped: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP8)) {
        Box(
            contentAlignment = Alignment.CenterStart,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = RowMinHeight)
                    .clip(RoundedCornerShape(FestiCoreRadius.r16))
                    .background(FestiCoreSemanticColor.backgroundElevated.color)
                    .clickable(enabled = isEnabled, role = Role.Button, onClick = onWithdrawTapped)
                    .padding(horizontal = FestiCoreSpacing.paddingP16),
        ) {
            Text(
                text = "계정 삭제",
                style = FestiCoreTextStyle.baseBodyRegular.textStyle,
                color = FestiCoreSemanticColor.statusNegative.color,
            )
        }
        Text(
            text = "계정을 삭제하면 내 일정, 크루, 깃발 기록이 모두 지워지고 되돌릴 수 없어요.",
            style = FestiCoreTextStyle.baseFootnoteRegular.textStyle,
            color = FestiCoreSemanticColor.labelAssistive.color,
            modifier = Modifier.padding(horizontal = FestiCoreSpacing.paddingP4),
        )
    }
}

@Composable
private fun InfoRow(title: String, value: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = RowMinHeight)
                .padding(horizontal = FestiCoreSpacing.paddingP16)
                .semantics(mergeDescendants = true) {},
    ) {
        Text(
            text = title,
            style = FestiCoreTextStyle.baseBodyRegular.textStyle,
            color = FestiCoreSemanticColor.labelNormal.color,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = FestiCoreTextStyle.baseBodyRegular.textStyle,
            color = FestiCoreSemanticColor.labelNeutral.color,
        )
    }
}

/** 그룹 제목(Pretendard Bold 13, 줄 높이 18, 좌우 4). */
@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        style = FestiCoreTextStyle.baseFootnoteBold.textStyle,
        color = FestiCoreSemanticColor.labelAssistive.color,
        modifier = Modifier.padding(horizontal = FestiCoreSpacing.paddingP4).semantics { heading() },
    )
}

/** 닉네임 변경 시트 내용(iOS `NicknameEditView`). 가입 화면과 같은 닉네임 입력을 쓴다. */
@Composable
fun NicknameEditContent(
    state: NicknameEditState,
    modifier: Modifier = Modifier,
    onNicknameChanged: (String) -> Unit = {},
    onSaveTapped: () -> Unit = {},
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = FestiCoreSpacing.paddingP20)
                .padding(bottom = FestiCoreSpacing.paddingP24),
        verticalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP24),
    ) {
        Text(
            text = "닉네임 변경",
            style = FestiCoreTextStyle.baseTitle2Bold.textStyle,
            color = FestiCoreSemanticColor.labelNormal.color,
            modifier = Modifier.semantics { heading() },
        )
        FCTextField(
            text = state.nickname,
            onTextChange = onNicknameChanged,
            placeholder = "닉네임",
            label = "닉네임",
            counter = AuthNicknameRule.counterText(state.nickname),
            helper = NicknameEditState.HelperText,
            error = state.errorMessage,
        )
        FCButton(
            title = if (state.isSaving) "저장하는 중" else "저장",
            style = FCButtonStyle.Primary,
            isFullWidth = true,
            isEnabled = !state.isSaving,
            onClick = onSaveTapped,
        )
    }
}
