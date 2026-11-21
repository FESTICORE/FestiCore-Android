package io.festicore.feature.auth

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.festicore.core.logger.FestiCoreLogger
import io.festicore.core.logger.LogCategory
import io.festicore.domain.auth.AuthNicknameRule
import io.festicore.ui.designkit.component.button.FCPrimaryButton
import io.festicore.ui.designkit.component.divider.FCDivider
import io.festicore.ui.designkit.component.navigation.FCTopBar
import io.festicore.ui.designkit.component.navigation.FCTopBarStyle
import io.festicore.ui.designkit.component.selection.FCConsentAll
import io.festicore.ui.designkit.component.selection.FCConsentRow
import io.festicore.ui.designkit.component.selection.FCTextField
import io.festicore.ui.designkit.tokens.FestiCoreRadius
import io.festicore.ui.designkit.tokens.FestiCoreSemanticColor
import io.festicore.ui.designkit.tokens.FestiCoreSpacing
import io.festicore.ui.designkit.tokens.FestiCoreTextStyle
import io.festicore.ui.designkit.typography.textStyle
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import org.orbitmvi.orbit.compose.collectAsState
import org.orbitmvi.orbit.compose.collectSideEffect

private val ConsentBorderWidth = 1.dp

/**
 * iOS `SignUpView`(S14 B). 뒤로(시스템 뒤로 포함)는 세션을 지우고 첫 진입 로그인으로 돌아간다.
 * 약관 상세는 외부 브라우저로 연다(iOS 앱 안 Web 화면은 `feature/web` 단계).
 */
@Composable
fun SignUpScreen(
    route: AuthRoute.SignUp,
    onCompleted: () -> Unit,
    onReturnToLaunchLogin: () -> Unit,
    viewModel: SignUpViewModel = koinViewModel { parametersOf(route) },
    legalDocumentUrls: LegalDocumentUrls = koinInject(),
) {
    val state by viewModel.collectAsState()
    val context = LocalContext.current

    BackHandler(onBack = viewModel::onCloseTapped)

    viewModel.collectSideEffect { sideEffect ->
        when (sideEffect) {
            SignUpSideEffect.Completed -> onCompleted()

            SignUpSideEffect.Closed, SignUpSideEffect.SessionLost -> onReturnToLaunchLogin()

            is SignUpSideEffect.OpenLegalDocument -> {
                val intent = Intent(Intent.ACTION_VIEW, legalDocumentUrls.url(sideEffect.document))
                try {
                    context.startActivity(intent)
                } catch (_: ActivityNotFoundException) {
                    FestiCoreLogger.error("약관 상세를 열 앱이 없음", LogCategory.UI)
                }
            }
        }
    }

    SignUpContent(
        state = state,
        onNicknameChanged = viewModel::onNicknameChanged,
        onServiceTermsChanged = viewModel::onServiceTermsChanged,
        onPrivacyTermsChanged = viewModel::onPrivacyTermsChanged,
        onLocationTermsChanged = viewModel::onLocationTermsChanged,
        onAllConsentToggled = viewModel::onAllConsentToggled,
        onLegalDocumentTapped = viewModel::onLegalDocumentTapped,
        onSubmitTapped = viewModel::onSubmitTapped,
        onCloseTapped = viewModel::onCloseTapped,
    )
}

@Composable
internal fun SignUpContent(
    state: SignUpState,
    onNicknameChanged: (String) -> Unit,
    onServiceTermsChanged: (Boolean) -> Unit,
    onPrivacyTermsChanged: (Boolean) -> Unit,
    onLocationTermsChanged: (Boolean) -> Unit,
    onAllConsentToggled: () -> Unit,
    onLegalDocumentTapped: (LegalDocument) -> Unit,
    onSubmitTapped: () -> Unit,
    onCloseTapped: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(FestiCoreSemanticColor.backgroundNormal.color)
                .navigationBarsPadding()
                .imePadding(),
    ) {
        FCTopBar(style = FCTopBarStyle.Back, onBack = onCloseTapped)
        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(FestiCoreSpacing.paddingP16),
            verticalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP24),
        ) {
            BasicText(
                text = "반가워요!\n어떻게 불러 드릴까요?",
                style = FestiCoreTextStyle.baseTitle2Bold.textStyle.copy(color = FestiCoreSemanticColor.labelNormal.color),
                modifier = Modifier.fillMaxWidth(),
            )
            FCTextField(
                text = state.nickname,
                onTextChange = onNicknameChanged,
                placeholder = "닉네임",
                label = "닉네임",
                counter = AuthNicknameRule.counterText(state.nickname),
                helper = "크루원과 깃발에 보이는 이름이에요. 나중에 마이에서 바꿀 수 있어요.",
            )
            ConsentBox(
                state = state,
                onServiceTermsChanged = onServiceTermsChanged,
                onPrivacyTermsChanged = onPrivacyTermsChanged,
                onLocationTermsChanged = onLocationTermsChanged,
                onAllConsentToggled = onAllConsentToggled,
                onLegalDocumentTapped = onLegalDocumentTapped,
            )
            state.message?.let { message ->
                BasicText(
                    text = message,
                    style = FestiCoreTextStyle.baseCaption1Regular.textStyle.copy(color = FestiCoreSemanticColor.statusNegative.color),
                )
            }
        }
        FCPrimaryButton(
            title = "다음",
            isEnabled = state.canSubmit,
            onClick = onSubmitTapped,
            modifier =
                Modifier
                    .padding(horizontal = FestiCoreSpacing.paddingP16)
                    .padding(top = FestiCoreSpacing.paddingP12, bottom = FestiCoreSpacing.paddingP8),
        )
    }
}

/** 전체 동의 + 구분선 + 필수 3행(위아래 8). 테두리 1 · 반경 16. */
@Composable
private fun ConsentBox(
    state: SignUpState,
    onServiceTermsChanged: (Boolean) -> Unit,
    onPrivacyTermsChanged: (Boolean) -> Unit,
    onLocationTermsChanged: (Boolean) -> Unit,
    onAllConsentToggled: () -> Unit,
    onLegalDocumentTapped: (LegalDocument) -> Unit,
) {
    val shape = RoundedCornerShape(FestiCoreRadius.r16)
    Column(
        modifier =
            Modifier
                .clip(shape)
                .border(ConsentBorderWidth, FestiCoreSemanticColor.lineNormal.color, shape),
    ) {
        FCConsentAll(isChecked = state.isAllConsented, onClick = onAllConsentToggled)
        FCDivider()
        Column(modifier = Modifier.padding(vertical = FestiCoreSpacing.paddingP8)) {
            FCConsentRow(
                label = LegalDocument.Terms.title,
                isChecked = state.acceptedServiceTerms,
                tag = "필수",
                onDetail = { onLegalDocumentTapped(LegalDocument.Terms) },
                onClick = { onServiceTermsChanged(!state.acceptedServiceTerms) },
            )
            FCConsentRow(
                label = LegalDocument.Privacy.title,
                isChecked = state.acceptedPrivacyTerms,
                tag = "필수",
                onDetail = { onLegalDocumentTapped(LegalDocument.Privacy) },
                onClick = { onPrivacyTermsChanged(!state.acceptedPrivacyTerms) },
            )
            FCConsentRow(
                label = LegalDocument.Location.title,
                isChecked = state.acceptedLocationTerms,
                tag = "필수",
                onDetail = { onLegalDocumentTapped(LegalDocument.Location) },
                onClick = { onLocationTermsChanged(!state.acceptedLocationTerms) },
            )
        }
    }
}
