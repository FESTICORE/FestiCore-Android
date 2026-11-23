package io.festicore.feature.profile.main

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.festicore.domain.festival.entity.FestivalReceivedPack
import io.festicore.feature.profile.AccountState
import io.festicore.ui.designkit.R
import io.festicore.ui.designkit.component.action.FCButton
import io.festicore.ui.designkit.component.action.FCButtonSize
import io.festicore.ui.designkit.component.action.FCButtonStyle
import io.festicore.ui.designkit.component.action.FCLoginButton
import io.festicore.ui.designkit.component.action.FCLoginButtonProvider
import io.festicore.ui.designkit.component.card.FCCardAction
import io.festicore.ui.designkit.component.card.FCEmptyCard
import io.festicore.ui.designkit.component.layout.fcScrollBottomInset
import io.festicore.ui.designkit.component.list.FCPackRow
import io.festicore.ui.designkit.component.navigation.FCTopBar
import io.festicore.ui.designkit.component.navigation.FCTopBarStyle
import io.festicore.ui.designkit.component.skeleton.FCSkeletonShape
import io.festicore.ui.designkit.component.skeleton.FCSkeletonView
import io.festicore.ui.designkit.tokens.FestiCoreRadius
import io.festicore.ui.designkit.tokens.FestiCoreSemanticColor
import io.festicore.ui.designkit.tokens.FestiCoreSpacing
import io.festicore.ui.designkit.tokens.FestiCoreTextStyle
import io.festicore.ui.designkit.typography.textStyle
import io.festicore.ui.designkit.typography.textStyleWithoutLineBox
import io.festicore.ui.sharedui.remoteimage.FCRemoteImage
import org.orbitmvi.orbit.compose.collectAsState
import org.orbitmvi.orbit.compose.collectSideEffect

private val PackThumbnailSize = 44.dp

// iOS `Metric.packThumbnailRadius`(12 × 44 / 56).
private val PackThumbnailRadius = (12f * 44f / 56f).dp
private val SettingsIconSize = 24.dp
private val SettingsButtonSize = 44.dp
private val SkeletonTitleWidth = 140.dp
private val SkeletonTitleHeight = 16.dp
private val SkeletonMetaWidth = 180.dp
private val SkeletonMetaHeight = 12.dp
private val DividerHeight = 1.dp

/**
 * 마이(iOS `ProfileView`, Figma More). ViewModel을 잇고 [account]가 바뀌면 ViewModel에 알린다.
 * 이동은 콜백으로만 알리고 Coordinator가 정한다(T1). 처음 나타날 때마다 받은 팩을 다시 읽는다(iOS `onAppear`).
 */
@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel,
    account: AccountState,
    onLoginRequested: () -> Unit,
    onGoogleLoginRequested: () -> Unit,
    onFestivalListRequested: () -> Unit,
    onSettingsRequested: () -> Unit,
    onPackDetailRequested: (festivalId: String, title: String) -> Unit,
    onSessionLost: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.collectAsState()
    viewModel.collectSideEffect { sideEffect ->
        when (sideEffect) {
            ProfileSideEffect.LoginRequested -> onLoginRequested()
            ProfileSideEffect.GoogleLoginRequested -> onGoogleLoginRequested()
            ProfileSideEffect.FestivalListRequested -> onFestivalListRequested()
            ProfileSideEffect.SettingsRequested -> onSettingsRequested()
            is ProfileSideEffect.PackDetailRequested -> onPackDetailRequested(sideEffect.festivalId, sideEffect.title)
            ProfileSideEffect.SessionLost -> onSessionLost()
        }
    }
    LaunchedEffect(viewModel, account) { viewModel.onAccountChanged(account) }
    LaunchedEffect(viewModel) { viewModel.onAppear() }
    ProfileContent(
        state = state,
        modifier = modifier,
        onSettingsTapped = viewModel::onSettingsTapped,
        onGoogleLoginTapped = viewModel::onGoogleLoginTapped,
        onPacksRetryTapped = viewModel::onPacksRetryTapped,
        onBrowseFestivalsTapped = viewModel::onBrowseFestivalsTapped,
        onPackTapped = viewModel::onPackTapped,
    )
}

/** 마이(순수 UI). */
@Composable
fun ProfileContent(
    state: ProfileState,
    modifier: Modifier = Modifier,
    onSettingsTapped: () -> Unit = {},
    onGoogleLoginTapped: () -> Unit = {},
    onPacksRetryTapped: () -> Unit = {},
    onBrowseFestivalsTapped: () -> Unit = {},
    onPackTapped: (String) -> Unit = {},
) {
    Column(modifier = modifier.fillMaxSize().background(FestiCoreSemanticColor.backgroundNormal.color)) {
        TopBar(onSettingsTapped = onSettingsTapped)
        state.guestMessage?.let { message ->
            Text(
                text = message,
                style = FestiCoreTextStyle.baseSubheadlineRegular.textStyleWithoutLineBox,
                color = FestiCoreSemanticColor.statusNegative.color,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = FestiCoreSpacing.paddingP20)
                        .semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = FestiCoreSpacing.paddingP16)
                    .padding(top = FestiCoreSpacing.paddingP8)
                    .fcScrollBottomInset(),
            verticalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP24),
        ) {
            if (state.showsLoginPrompt) LoginPrompt(onGoogleLoginTapped = onGoogleLoginTapped)
            // 기수 카드 · 내 깃발 카드는 기수 흐름을 옮길 때까지 숨긴다(설계 쟁점 M7).
            ReceivedPacks(
                state = state,
                onPacksRetryTapped = onPacksRetryTapped,
                onBrowseFestivalsTapped = onBrowseFestivalsTapped,
                onPackTapped = onPackTapped,
            )
        }
    }
}

/** Figma More: 오른쪽 위 설정 버튼(gear 24)이 설정 홈을 연다. iOS처럼 큰 상단 바 위에 겹쳐 그린다. */
@Composable
private fun TopBar(onSettingsTapped: () -> Unit) {
    Box {
        FCTopBar(style = FCTopBarStyle.Large, title = "마이")
        Box(
            modifier = Modifier.matchParentSize().windowInsetsPadding(WindowInsets.statusBars),
            contentAlignment = Alignment.CenterEnd,
        ) {
            // Figma 설정 버튼(24)은 오른쪽 끝에서 16 안쪽이다. 누르는 영역 44의 남는 10을 빼 6을 둔다.
            Box(
                modifier =
                    Modifier
                        .padding(end = FestiCoreSpacing.marginM6)
                        .size(SettingsButtonSize)
                        .clickable(role = Role.Button, onClick = onSettingsTapped)
                        .semantics { contentDescription = "설정" },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_gear_24),
                    contentDescription = null,
                    tint = FestiCoreSemanticColor.labelNormal.color,
                    modifier = Modifier.size(SettingsIconSize),
                )
            }
        }
    }
}

/** 로그인하지 않았을 때 맨 위에 두는 로그인 카드. Apple 버튼만 빼고 iOS와 같다(설계 쟁점 M1). */
@Composable
private fun LoginPrompt(onGoogleLoginTapped: () -> Unit) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(FestiCoreSemanticColor.backgroundElevated.color, RoundedCornerShape(FestiCoreRadius.r16))
                .padding(horizontal = FestiCoreSpacing.paddingP16, vertical = FestiCoreSpacing.paddingP20),
        verticalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP12),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP4)) {
            Text(
                text = "로그인하고 더 함께 즐겨요",
                style = FestiCoreTextStyle.baseHeadlineBold.textStyle,
                color = FestiCoreSemanticColor.labelNormal.color,
            )
            Text(
                text = "깃발 팔로우 · 크루 · 기수는 로그인이 필요해요. 타임테이블과 팩은 로그인 없이도 쓸 수 있어요.",
                style = FestiCoreTextStyle.baseFootnoteRegular.textStyle,
                color = FestiCoreSemanticColor.labelNeutral.color,
            )
        }
        FCLoginButton(provider = FCLoginButtonProvider.Google, onClick = onGoogleLoginTapped)
    }
}

@Composable
private fun ReceivedPacks(
    state: ProfileState,
    onPacksRetryTapped: () -> Unit,
    onBrowseFestivalsTapped: () -> Unit,
    onPackTapped: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP8)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = FestiCoreSpacing.paddingP4),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SectionTitle("내가 받은 팩")
            Spacer(modifier = Modifier.weight(1f))
            if (state.packLoad == ProfilePackLoad.Loaded) {
                Text(
                    text = "${state.packs.size}개",
                    style = FestiCoreTextStyle.baseFootnoteRegular.textStyle,
                    color = FestiCoreSemanticColor.labelAssistive.color,
                )
            }
        }
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(FestiCoreRadius.r16))
                    .background(FestiCoreSemanticColor.backgroundElevated.color),
        ) {
            when {
                state.packLoad == ProfilePackLoad.Idle || state.packLoad == ProfilePackLoad.Loading -> PackSkeletonRow()

                state.packLoad == ProfilePackLoad.Failed -> PackFailure(onPacksRetryTapped = onPacksRetryTapped)

                state.packs.isEmpty() ->
                    // Figma More C: 빈 카드(ticket 24)의 "페스티벌 둘러보기"(Primary)가 페스티벌 목록을 연다.
                    FCEmptyCard(
                        title = "아직 받은 팩이 없어요",
                        message = "페스티벌을 고르면 시간표 · 지도 · 가는 길을 오프라인으로 받아 둘 수 있어요.",
                        icon = R.drawable.ic_ticket_24,
                        action = FCCardAction(title = "페스티벌 둘러보기", style = FCButtonStyle.Primary, onClick = onBrowseFestivalsTapped),
                    )

                else ->
                    state.packs.forEachIndexed { index, pack ->
                        if (index > 0) RowDivider()
                        PackRow(pack = pack, changedSets = state.packChangedSets[pack.id], onPackTapped = onPackTapped)
                    }
            }
        }
    }
}

@Composable
private fun PackRow(
    pack: FestivalReceivedPack,
    changedSets: Int?,
    onPackTapped: (String) -> Unit,
) {
    Box {
        FCPackRow(
            name = pack.name,
            meta = ProfilePresentation.packMeta(pack, changedSets),
            hasUpdate = pack.hasUpdate,
            onClick = { onPackTapped(pack.id) },
        )
        // FCPackRow는 로컬 이미지만 받으므로 서버 썸네일은 같은 자리에 겹쳐 그린다.
        pack.thumbnailUrl?.let { url ->
            FCRemoteImage(
                url = url,
                modifier =
                    Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = FestiCoreSpacing.paddingP16)
                        .size(PackThumbnailSize)
                        .clip(RoundedCornerShape(PackThumbnailRadius))
                        .clearAndSetSemantics {},
            )
        }
    }
}

@Composable
private fun PackSkeletonRow() {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = FestiCoreSpacing.paddingP12, horizontal = FestiCoreSpacing.paddingP16),
        horizontalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FCSkeletonView(shape = FCSkeletonShape.Circle, modifier = Modifier.size(PackThumbnailSize))
        Column(verticalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP4)) {
            FCSkeletonView(
                shape = FCSkeletonShape.Round(cornerRadius = FestiCoreRadius.r4),
                modifier = Modifier.size(width = SkeletonTitleWidth, height = SkeletonTitleHeight),
            )
            FCSkeletonView(
                shape = FCSkeletonShape.Round(cornerRadius = FestiCoreRadius.r4),
                modifier = Modifier.size(width = SkeletonMetaWidth, height = SkeletonMetaHeight),
            )
        }
    }
}

@Composable
private fun PackFailure(onPacksRetryTapped: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = FestiCoreSpacing.paddingP16),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP8),
    ) {
        Text(
            text = "받은 팩을 불러오지 못했어요",
            style = FestiCoreTextStyle.baseSubheadlineRegular.textStyleWithoutLineBox,
            color = FestiCoreSemanticColor.labelNeutral.color,
        )
        FCButton(
            title = "다시 시도",
            style = FCButtonStyle.Secondary,
            size = FCButtonSize.Medium,
            onClick = onPacksRetryTapped,
        )
    }
}

/** Figma More 그룹 제목: Pretendard Bold 13, 줄 높이 18. */
@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        style = FestiCoreTextStyle.baseFootnoteBold.textStyle,
        color = FestiCoreSemanticColor.labelAssistive.color,
    )
}

/** Figma More 구분선: 좌우 16 안쪽. */
@Composable
private fun RowDivider() {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = FestiCoreSpacing.paddingP16)
                .height(DividerHeight)
                .background(FestiCoreSemanticColor.lineNormal.color),
    )
}
