package io.festicore.feature.bearer.register

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.festicore.domain.bearer.entity.BearerFlagTag
import io.festicore.feature.bearer.profile.ActionBar
import io.festicore.feature.bearer.profile.BearerProfileState
import io.festicore.feature.bearer.profile.BearerZoneButton
import io.festicore.feature.bearer.profile.Helper
import io.festicore.feature.bearer.profile.SectionLabel
import io.festicore.feature.bearer.profile.rememberImageBitmap
import io.festicore.feature.bearer.profile.title
import io.festicore.ui.designkit.component.action.FCButton
import io.festicore.ui.designkit.component.action.FCButtonSize
import io.festicore.ui.designkit.component.action.FCButtonStyle
import io.festicore.ui.designkit.component.action.FCChip
import io.festicore.ui.designkit.component.button.FCPrimaryButton
import io.festicore.ui.designkit.component.card.FCFlagCard
import io.festicore.ui.designkit.component.card.FCFlagCardContext
import io.festicore.ui.designkit.component.label.FCStatusChipStatus
import io.festicore.ui.designkit.component.list.FCListRow
import io.festicore.ui.designkit.component.media.FCThumbnail
import io.festicore.ui.designkit.component.navigation.FCTopBar
import io.festicore.ui.designkit.component.navigation.FCTopBarStyle
import io.festicore.ui.designkit.component.overlay.FCDoneHero
import io.festicore.ui.designkit.component.popup.FCDialog
import io.festicore.ui.designkit.component.popup.FCDialogItem
import io.festicore.ui.designkit.component.selection.FCPhotoPicker
import io.festicore.ui.designkit.component.selection.FCPhotoPickerState
import io.festicore.ui.designkit.component.selection.FCTextField
import io.festicore.ui.designkit.tokens.FestiCoreRadius
import io.festicore.ui.designkit.tokens.FestiCoreSemanticColor
import io.festicore.ui.designkit.tokens.FestiCoreSpacing
import io.festicore.ui.designkit.tokens.FestiCoreTextStyle
import io.festicore.ui.designkit.typography.textStyle
import io.festicore.ui.designkit.typography.textStyleWithoutLineBox
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.orbitmvi.orbit.compose.collectAsState
import org.orbitmvi.orbit.compose.collectSideEffect

// iOS `BearerRegisterView.Metric`(`BRV:470-475`): Figma S14 D 섹션 사이 28(S10과 같다), 라벨 · 내용 · 안내 사이 12, 완료 화면 히어로와 미리보기 사이 40
private val SectionSpacing = 28.dp
private val GroupSpacing = 12.dp
private val DoneSectionSpacing = 40.dp

// iOS 설정 카드 테두리 1(`BRV:304-307`). 페스티벌 스켈레톤 높이 44(`BRV:161`)는 `BearerFestivalPicker`에 있다.
private val CardBorderWidth = 1.dp

// 디코딩 크기: `FCPhotoPicker` 사진 칸 88, `FCFlagCard` 썸네일 56
private val PickerPhotoSize = 88.dp
private val PreviewThumbnailSize = 56.dp

/**
 * 깃발 등록(iOS `BearerRegisterView`, Figma S14 D1 · D2 · E1 · E2). ViewModel을 잇는다.
 * 사진은 Android Photo Picker(`PickVisualMedia(ImageOnly)`)로 하나만 받고 바이트는 IO에서 읽는다. 읽지 못하면 프로필처럼 사진 칸 아래에 안내한다(iOS는 조용히 넘어감 `BRV:44`).
 * 결과는 콜백으로만 알린다. 경로 연결 · 핀은 BC1(`BearerEntryCoordinator`)이다. 시스템 뒤로는 상단 뒤로와 같다.
 */
@Composable
fun BearerRegisterScreen(
    viewModel: BearerRegisterViewModel,
    onRegistered: (flagId: String, festivalId: String) -> Unit,
    onPackRequested: (festivalId: String) -> Unit,
    onFinished: (festivalId: String) -> Unit,
    onClaimRequested: (festivalId: String) -> Unit,
    onBackRequested: () -> Unit,
    onCloseRequested: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.collectAsState()
    viewModel.collectSideEffect { sideEffect ->
        when (sideEffect) {
            is BearerRegisterSideEffect.Registered -> onRegistered(sideEffect.flagId, sideEffect.festivalId)
            is BearerRegisterSideEffect.PackRequested -> onPackRequested(sideEffect.festivalId)
            is BearerRegisterSideEffect.Finished -> onFinished(sideEffect.festivalId)
            is BearerRegisterSideEffect.ClaimRequested -> onClaimRequested(sideEffect.festivalId)
            BearerRegisterSideEffect.BackRequested -> onBackRequested()
            BearerRegisterSideEffect.CloseRequested -> onCloseRequested()
        }
    }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val photoPicker =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            scope.launch {
                val data =
                    withContext(Dispatchers.IO) {
                        runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
                    }
                if (data == null) viewModel.onPhotoLoadFailed() else viewModel.onPhotoPicked(data)
            }
        }
    BackHandler(onBack = viewModel::onBackTapped)
    // iOS `.onAppear { send(.onAppear) }`
    LaunchedEffect(viewModel) { viewModel.onAppear() }
    BearerRegisterContent(
        state = state,
        modifier = modifier,
        callbacks =
            BearerRegisterCallbacks(
                onBackTapped = viewModel::onBackTapped,
                onRetryTapped = viewModel::onRetryTapped,
                onFestivalTapped = viewModel::onFestivalTapped,
                onShowMoreTapped = viewModel::onShowMoreTapped,
                onNameChanged = viewModel::onNameChanged,
                onBioChanged = viewModel::onBioChanged,
                onTagTapped = viewModel::onTagTapped,
                onZoneTapped = viewModel::onZoneTapped,
                onPlanVisibleChanged = viewModel::onPlanVisibleChanged,
                onPhotoTapped = {
                    photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                onSaveTapped = viewModel::onSaveTapped,
                onClaimTapped = viewModel::onClaimTapped,
                onLaterTapped = viewModel::onLaterTapped,
                onPrimaryDoneTapped = viewModel::onPrimaryDoneTapped,
                onSecondaryDoneTapped = viewModel::onSecondaryDoneTapped,
                onAlertDismissed = viewModel::onAlertDismissed,
            ),
    )
}

/** 순수 UI가 부르는 동작. */
data class BearerRegisterCallbacks(
    val onBackTapped: () -> Unit = {},
    val onRetryTapped: () -> Unit = {},
    val onFestivalTapped: (String) -> Unit = {},
    val onShowMoreTapped: () -> Unit = {},
    val onNameChanged: (String) -> Unit = {},
    val onBioChanged: (String) -> Unit = {},
    val onTagTapped: (BearerFlagTag) -> Unit = {},
    val onZoneTapped: (String) -> Unit = {},
    val onPlanVisibleChanged: (Boolean) -> Unit = {},
    val onPhotoTapped: () -> Unit = {},
    val onSaveTapped: () -> Unit = {},
    val onClaimTapped: () -> Unit = {},
    val onLaterTapped: () -> Unit = {},
    val onPrimaryDoneTapped: () -> Unit = {},
    val onSecondaryDoneTapped: () -> Unit = {},
    val onAlertDismissed: () -> Unit = {},
)

/** 깃발 등록(순수 UI). 저장을 마치면 완료 화면(E)을, 아니면 입력 화면(D)을 그린다. 배경은 Background/Normal이다(`BRV:34`). */
@Composable
fun BearerRegisterContent(
    state: BearerRegisterState,
    modifier: Modifier = Modifier,
    callbacks: BearerRegisterCallbacks = BearerRegisterCallbacks(),
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(FestiCoreSemanticColor.backgroundNormal.color)
                .imePadding(),
    ) {
        val completion = state.completion
        if (completion != null) {
            DoneContent(state = state, completion = completion, callbacks = callbacks)
        } else {
            FormContent(state = state, callbacks = callbacks)
        }
    }
    state.alert?.let { alert -> RegisterAlert(alert = alert, onDismiss = callbacks.onAlertDismissed) }
}

@Composable
private fun ColumnScope.FormContent(state: BearerRegisterState, callbacks: BearerRegisterCallbacks) {
    FCTopBar(style = FCTopBarStyle.Back, title = "깃발 프로필", onBack = callbacks.onBackTapped)
    Column(
        modifier =
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(FestiCoreSpacing.paddingP16),
        verticalArrangement = Arrangement.spacedBy(SectionSpacing),
    ) {
        Header()
        FestivalSection(state, callbacks)
        PhotoSection(state, callbacks.onPhotoTapped)
        FCTextField(
            text = state.form.name,
            onTextChange = callbacks.onNameChanged,
            placeholder = "예) 호락호락",
            label = "깃발 이름",
            maxLength = BearerProfileState.NAME_MAX,
            helper = "2~20자 · 이 페스티벌에서 하나뿐인 이름이어야 해요",
            error = state.nameError,
        )
        FCTextField(
            text = state.form.bio,
            onTextChange = callbacks.onBioChanged,
            placeholder = "어떤 깃발인지 한 줄로 알려 주세요",
            label = "한 줄 소개 (선택)",
            maxLength = BearerProfileState.BIO_LIMIT,
        )
        TagSection(state, callbacks.onTagTapped)
        if (state.zones.isNotEmpty()) {
            ZoneSection(state, callbacks.onZoneTapped)
        }
        SettingSection(state, callbacks.onPlanVisibleChanged)
        // iOS `.frame(maxWidth: .infinity)`: 버튼은 제 폭, 가운데
        FCButton(
            title = "공동 기수 초대 코드가 있어요",
            modifier = Modifier.align(Alignment.CenterHorizontally),
            style = FCButtonStyle.Ghost,
            size = FCButtonSize.Medium,
            isEnabled = state.selectedFestivalId != null,
            onClick = callbacks.onClaimTapped,
        )
        state.saveError?.let { message ->
            BasicText(
                text = message,
                style = FestiCoreTextStyle.baseFootnoteRegular.textStyleWithoutLineBox.copy(
                    color = FestiCoreSemanticColor.statusNegative.color,
                ),
            )
        }
    }
    ActionBar {
        FCPrimaryButton(
            title = if (state.isSaving) "만드는 중이에요" else "등록하고 기수 시작하기",
            isEnabled = state.canSave,
            onClick = callbacks.onSaveTapped,
        )
        FCButton(
            title = "나중에 할게요 · 마이에서 언제든 만들 수 있어요",
            style = FCButtonStyle.Ghost,
            size = FCButtonSize.Medium,
            onClick = callbacks.onLaterTapped,
        )
    }
}

@Composable
private fun Header() {
    Column(verticalArrangement = Arrangement.spacedBy(FestiCoreSpacing.marginM8)) {
        BasicText(
            text = "내 깃발을 만들어요",
            style = FestiCoreTextStyle.baseTitle2Bold.textStyle.copy(color = FestiCoreSemanticColor.labelNormal.color),
        )
        BasicText(
            text = "팔로워에게 보일 깃발 프로필이에요.\n지금 다 채우지 않아도 언제든 고칠 수 있어요.",
            style = FestiCoreTextStyle.baseCalloutRegular.textStyle.copy(color = FestiCoreSemanticColor.labelNeutral.color),
        )
    }
}

@Composable
private fun FestivalSection(state: BearerRegisterState, callbacks: BearerRegisterCallbacks) {
    BearerFestivalPicker(
        title = "어느 페스티벌의 깃발인가요?",
        festivals = state.festivals,
        selectedFestivalId = state.selectedFestivalId,
        failedMessage = "깃발을 만들 수 있는 페스티벌을 불러오지 못했어요.",
        helper = { hasReceived ->
            if (hasReceived) {
                "로그인 전에 받은 팩·핀한 페스티벌이 먼저 보여요. 깃발은 페스티벌마다 따로 만들어요."
            } else {
                "앞으로 열리는 페스티벌이에요. 깃발은 페스티벌마다 따로 만들어요."
            }
        },
        onFestivalTapped = callbacks.onFestivalTapped,
        onRetryTapped = callbacks.onRetryTapped,
        onShowMoreTapped = callbacks.onShowMoreTapped,
    )
}

@Composable
private fun PhotoSection(state: BearerRegisterState, onPhotoTapped: () -> Unit) {
    val image = rememberImageBitmap(state.photoData, PickerPhotoSize)
    // 프로필 사진 칸과 같은 배치: 사진 칸 아래 M8에 실패 안내(Android 보완)
    Column(verticalArrangement = Arrangement.spacedBy(FestiCoreSpacing.marginM8)) {
        FCPhotoPicker(
            state = if (image != null) FCPhotoPickerState.Filled(image = BitmapPainter(image)) else FCPhotoPickerState.Empty,
            onClick = onPhotoTapped,
        )
        state.photoError?.let { message ->
            BasicText(
                text = message,
                style = FestiCoreTextStyle.baseFootnoteRegular.textStyleWithoutLineBox.copy(
                    color = FestiCoreSemanticColor.statusNegative.color,
                ),
            )
        }
    }
}

@Composable
private fun TagSection(state: BearerRegisterState, onTagTapped: (BearerFlagTag) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(GroupSpacing)) {
        SectionLabel(title = "성향 태그", count = "${state.form.tags.size} / ${BearerProfileState.TAG_LIMIT}")
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(FestiCoreSpacing.marginM8),
        ) {
            BearerFlagTag.entries.forEach { tag ->
                FCChip(label = tag.title, isSelected = tag in state.form.tags, onClick = { onTagTapped(tag) })
            }
        }
        Helper("1~3개 고르면 깃발 디렉토리 필터에 걸려요.")
    }
}

@Composable
private fun ZoneSection(state: BearerRegisterState, onZoneTapped: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(GroupSpacing)) {
        SectionLabel(title = "주로 있는 존", count = null)
        // iOS LazyVGrid 2열(열 사이 M8, 행 사이 12)
        Column(verticalArrangement = Arrangement.spacedBy(GroupSpacing)) {
            state.zones.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(FestiCoreSpacing.marginM8)) {
                    row.forEach { zone ->
                        BearerZoneButton(
                            name = zone.name,
                            isSelected = state.form.homeZoneId == zone.id,
                            modifier = Modifier.weight(1f),
                            onClick = { onZoneTapped(zone.id) },
                        )
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
        Helper("당일 첫 상태를 올리기 전까지 이 존에 \"예정\"으로 보여요.")
    }
}

@Composable
private fun SettingSection(state: BearerRegisterState, onPlanVisibleChanged: (Boolean) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(GroupSpacing)) {
        SectionLabel(title = "오늘 계획 · 함께하는 기수", count = null)
        // iOS: 행 하나를 모서리 16으로 자르고 1pt 테두리를 두른다(`BRV:294-308`).
        val shape = RoundedCornerShape(FestiCoreRadius.r16)
        Column(modifier = Modifier.fillMaxWidth().clip(shape).border(CardBorderWidth, FestiCoreSemanticColor.lineNormal.color, shape)) {
            FCListRow(label = "팔로워에게 오늘 계획 보여주기", isOn = state.form.planVisible, onIsOnChange = onPlanVisibleChanged)
        }
        Helper("오늘 계획과 공동 기수 초대는 깃발을 만든 뒤 깃발 프로필에서 고를 수 있어요.")
    }
}

@Composable
private fun ColumnScope.DoneContent(
    state: BearerRegisterState,
    completion: BearerRegisterCompletion,
    callbacks: BearerRegisterCallbacks,
) {
    // Figma S14 E: 내용(히어로 · 미리보기, 사이 40)을 위아래 24 여백 안에서 세로 가운데에 둔다(`BRV:314-351`).
    BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
        val minHeight = maxHeight
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .heightIn(min = minHeight)
                    .padding(horizontal = FestiCoreSpacing.paddingP16)
                    .padding(vertical = FestiCoreSpacing.paddingP24),
            verticalArrangement = Arrangement.spacedBy(DoneSectionSpacing, Alignment.CenterVertically),
        ) {
            FCDoneHero(title = "깃발이 준비됐어요", message = doneMessage(completion), modifier = Modifier.fillMaxWidth())
            Column(verticalArrangement = Arrangement.spacedBy(FestiCoreSpacing.marginM12)) {
                BasicText(
                    text = "깃발 목록에서 이렇게 보여요",
                    style = FestiCoreTextStyle.baseFootnoteBold.textStyle.copy(color = FestiCoreSemanticColor.labelAssistive.color),
                )
                PreviewCard(state = state, completion = completion)
                if (completion.photo == BearerRegisterPhoto.Failed) {
                    Helper("사진을 올리지 못했어요. 깃발 프로필에서 다시 골라 주세요.")
                }
            }
        }
    }
    val hasPack = completion.festival.hasPack
    ActionBar {
        FCPrimaryButton(
            title = if (hasPack) "완료" else "${completion.festival.name} 팩 받기",
            onClick = callbacks.onPrimaryDoneTapped,
        )
        FCButton(
            title = if (hasPack) "공동 기수 초대하기" else "나중에 받을게요",
            style = FCButtonStyle.Ghost,
            isEnabled = !completion.isInviting,
            onClick = callbacks.onSecondaryDoneTapped,
        )
    }
}

private fun doneMessage(completion: BearerRegisterCompletion): String = if (completion.festival.hasPack) {
    "공연 당일 기수 보드에서 깃발을 올리면\n팔로워에게 알림이 가요."
} else {
    "현장에서 기수 보드를 쓰려면 ${completion.festival.name} 팩이 필요해요. 지금 받아 두면 통신이 끊겨도 쓸 수 있어요."
}

/** Figma S14 E 미리보기: 깃발 목록과 같은 Flag Card(디렉토리), 팔로우 버튼 없음(`BRV:377-397`). */
@Composable
private fun PreviewCard(state: BearerRegisterState, completion: BearerRegisterCompletion) {
    val profile = completion.claim.profile
    val zone = state.zones.firstOrNull { it.id == profile.homeZoneId }
    val image = rememberImageBitmap(state.photoData, PreviewThumbnailSize)
    FCFlagCard(
        name = profile.name,
        status = FCStatusChipStatus.Scheduled,
        zone = zone?.name ?: "",
        context =
            FCFlagCardContext.Directory(
                followers = "팔로워 ${profile.followerCount}",
                tags = profile.tags.map { it.title },
                isFollowing = false,
                onFollow = {},
            ),
        showsFollow = false,
        showsZone = zone != null,
        thumbnail = {
            if (image != null) {
                Image(bitmap = image, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else {
                FCThumbnail(null)
            }
        },
    )
}

@Composable
private fun RegisterAlert(alert: BearerRegisterAlert, onDismiss: () -> Unit) {
    val item =
        remember(alert) {
            when (alert) {
                BearerRegisterAlert.NameTaken ->
                    FCDialogItem(
                        title = "이미 있는 깃발 이름이에요",
                        message = "이 페스티벌에서 하나뿐인 이름이어야 해요. 다른 이름을 지어 주세요.",
                        confirmTitle = "확인",
                        cancelTitle = "닫기",
                        onConfirm = {},
                    )

                is BearerRegisterAlert.BearerLimit ->
                    FCDialogItem(
                        title = "이미 이 페스티벌의 기수예요",
                        message = "${alert.festivalName ?: "이 페스티벌"}에는 깃발을 하나만 만들 수 있어요. 마이의 내 깃발에서 확인해 주세요.",
                        confirmTitle = "확인",
                        cancelTitle = "닫기",
                        onConfirm = {},
                    )

                is BearerRegisterAlert.Invite ->
                    FCDialogItem(
                        title = "공동 기수 초대 코드",
                        message = "${alert.code}\n24시간 안에 함께할 기수가 기수 인증 코드에 입력하면 돼요.",
                        confirmTitle = "확인",
                        cancelTitle = "닫기",
                        onConfirm = {},
                    )

                is BearerRegisterAlert.InviteFailed ->
                    FCDialogItem(title = "초대하지 못했어요", message = alert.message, confirmTitle = "확인", cancelTitle = "닫기", onConfirm = {})
            }
        }
    FCDialog(item = item, onDismiss = onDismiss)
}
