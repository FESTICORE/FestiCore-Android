package io.festicore.feature.bearer.profile

import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.festicore.domain.bearer.entity.BearerFlagProfile
import io.festicore.domain.bearer.entity.BearerFlagTag
import io.festicore.ui.designkit.R
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
import io.festicore.ui.designkit.component.skeleton.FCSkeletonShape
import io.festicore.ui.designkit.component.skeleton.FCSkeletonView
import io.festicore.ui.designkit.tokens.FestiCoreRadius
import io.festicore.ui.designkit.tokens.FestiCoreSemanticColor
import io.festicore.ui.designkit.tokens.FestiCoreSpacing
import io.festicore.ui.designkit.tokens.FestiCoreTextStyle
import io.festicore.ui.designkit.typography.textStyle
import io.festicore.ui.designkit.typography.textStyleWithoutLineBox
import io.festicore.ui.sharedui.remoteimage.FCRemoteImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.orbitmvi.orbit.compose.collectAsState
import org.orbitmvi.orbit.compose.collectSideEffect

// iOS `BearerProfileView.Metric`: Figma S10 섹션 사이 28, 라벨 · 내용 · 안내 사이 12, 완료 화면 히어로와 미리보기 사이 40
private val SectionSpacing = 28.dp
private val GroupSpacing = 12.dp
private val DoneSectionSpacing = 40.dp

// iOS 스켈레톤 높이 64 · 88×88 · 52(`BPV:123-126`), 카드 테두리 1, 미리보기 자리표시 아이콘 20
private val SkeletonHeaderHeight = 64.dp
private val SkeletonPhotoSize = 88.dp
private val SkeletonFieldHeight = 52.dp
private val CardBorderWidth = 1.dp
private val PlaceholderIconSize = 20.dp

// 디코딩 크기: `FCPhotoPicker` 사진 칸 88, `FCFlagCard` 썸네일 56
private val PickerPhotoSize = 88.dp
private val PreviewThumbnailSize = 56.dp

/**
 * 깃발 프로필(iOS `BearerProfileView`, Figma S10 A~E). ViewModel을 잇는다.
 * 사진은 Android Photo Picker(`PickVisualMedia(ImageOnly)`)로 하나만 받아 권한을 선언 · 요청하지 않는다. 고른 사진 바이트는 IO에서 읽는다.
 * 결과는 콜백으로만 알린다. 경로 연결 · 오늘 계획 결과 전달은 BC1(`BearerEntryCoordinator`)이다. 시스템 뒤로는 상단 뒤로와 같다.
 */
@Composable
fun BearerProfileScreen(
    viewModel: BearerProfileViewModel,
    onFinished: (flagId: String) -> Unit,
    onPlanRequested: (festivalId: String) -> Unit,
    onCloseRequested: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.collectAsState()
    viewModel.collectSideEffect { sideEffect ->
        when (sideEffect) {
            is BearerProfileSideEffect.Finished -> onFinished(sideEffect.flagId)
            is BearerProfileSideEffect.PlanRequested -> onPlanRequested(sideEffect.festivalId)
            BearerProfileSideEffect.CloseRequested -> onCloseRequested()
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
    BearerProfileContent(
        state = state,
        modifier = modifier,
        callbacks =
            BearerProfileCallbacks(
                onBackTapped = viewModel::onBackTapped,
                onRetryTapped = viewModel::onRetryTapped,
                onNameChanged = viewModel::onNameChanged,
                onBioChanged = viewModel::onBioChanged,
                onTagTapped = viewModel::onTagTapped,
                onZoneTapped = viewModel::onZoneTapped,
                onPlanVisibleChanged = viewModel::onPlanVisibleChanged,
                onPlanTapped = viewModel::onPlanTapped,
                onPhotoTapped = {
                    photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                onPhotoCancelTapped = viewModel::onPhotoCancelTapped,
                onInviteTapped = viewModel::onInviteTapped,
                onSaveTapped = viewModel::onSaveTapped,
                onDoneTapped = viewModel::onDoneTapped,
                onAlertDismissed = viewModel::onAlertDismissed,
            ),
    )
}

/** 순수 UI가 부르는 동작. */
data class BearerProfileCallbacks(
    val onBackTapped: () -> Unit = {},
    val onRetryTapped: () -> Unit = {},
    val onNameChanged: (String) -> Unit = {},
    val onBioChanged: (String) -> Unit = {},
    val onTagTapped: (BearerFlagTag) -> Unit = {},
    val onZoneTapped: (String) -> Unit = {},
    val onPlanVisibleChanged: (Boolean) -> Unit = {},
    val onPlanTapped: () -> Unit = {},
    val onPhotoTapped: () -> Unit = {},
    val onPhotoCancelTapped: () -> Unit = {},
    val onInviteTapped: () -> Unit = {},
    val onSaveTapped: () -> Unit = {},
    val onDoneTapped: () -> Unit = {},
    val onAlertDismissed: () -> Unit = {},
)

/** 깃발 프로필(순수 UI). 저장을 마치면 완료 화면(E)을, 아니면 입력 화면(A~D)을 그린다. 배경은 Background/Elevated다. */
@Composable
fun BearerProfileContent(
    state: BearerProfileState,
    modifier: Modifier = Modifier,
    callbacks: BearerProfileCallbacks = BearerProfileCallbacks(),
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(FestiCoreSemanticColor.backgroundElevated.color)
                .imePadding(),
    ) {
        val saved = state.savedProfile
        if (saved != null) {
            DoneContent(state = state, profile = saved, callbacks = callbacks)
        } else {
            FormContent(state = state, callbacks = callbacks)
        }
    }
    state.alert?.let { alert -> ProfileAlert(alert = alert, onDismiss = callbacks.onAlertDismissed) }
}

@Composable
private fun ColumnScope.FormContent(state: BearerProfileState, callbacks: BearerProfileCallbacks) {
    FCTopBar(style = FCTopBarStyle.Back, title = "깃발 프로필", onBack = callbacks.onBackTapped)
    if (state.isLoading) {
        LoadingContent(loadFailed = state.loadFailed, onRetryTapped = callbacks.onRetryTapped)
        return
    }
    Column(
        modifier =
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(FestiCoreSpacing.paddingP16)
                // Figma S10 Contents 아래 여백 32 = 공통 16 + 16
                .padding(bottom = FestiCoreSpacing.paddingP16),
        verticalArrangement = Arrangement.spacedBy(SectionSpacing),
    ) {
        Header(state)
        PhotoSection(state, callbacks)
        FCTextField(
            text = state.name,
            onTextChange = callbacks.onNameChanged,
            placeholder = "예) 호락호락",
            label = "깃발 이름",
            maxLength = BearerProfileState.NAME_MAX,
            helper = "2~20자 · 이 페스티벌에서 하나뿐인 이름이어야 해요",
            error = state.nameError,
        )
        FCTextField(
            text = state.bio,
            onTextChange = callbacks.onBioChanged,
            placeholder = "어떤 깃발인지 한 줄로 알려 주세요",
            label = "한 줄 소개 (선택)",
            maxLength = BearerProfileState.BIO_LIMIT,
        )
        TagSection(state, callbacks.onTagTapped)
        if (state.zones.isNotEmpty()) {
            ZoneSection(state, callbacks.onZoneTapped)
        }
        SettingSection(state, callbacks)
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
        FCPrimaryButton(title = saveTitle(state), isEnabled = state.canSave, onClick = callbacks.onSaveTapped)
    }
}

@Composable
private fun ColumnScope.LoadingContent(loadFailed: Boolean, onRetryTapped: () -> Unit) {
    if (loadFailed) {
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP12, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BasicText(
                text = "깃발 프로필을 불러오지 못했어요",
                style = FestiCoreTextStyle.baseBodyMedium.textStyleWithoutLineBox.copy(color = FestiCoreSemanticColor.labelNeutral.color),
            )
            FCButton(title = "다시 시도", style = FCButtonStyle.Secondary, size = FCButtonSize.Medium, onClick = onRetryTapped)
        }
    } else {
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth().padding(FestiCoreSpacing.paddingP16),
            verticalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP24),
        ) {
            FCSkeletonView(FCSkeletonShape.Round(), Modifier.fillMaxWidth().height(SkeletonHeaderHeight))
            FCSkeletonView(FCSkeletonShape.Round(), Modifier.size(SkeletonPhotoSize))
            FCSkeletonView(FCSkeletonShape.Round(), Modifier.fillMaxWidth().height(SkeletonFieldHeight))
            FCSkeletonView(FCSkeletonShape.Round(), Modifier.fillMaxWidth().height(SkeletonFieldHeight))
        }
    }
}

@Composable
private fun Header(state: BearerProfileState) {
    Column(verticalArrangement = Arrangement.spacedBy(FestiCoreSpacing.marginM8)) {
        BasicText(
            text = headerTitle(state),
            style = FestiCoreTextStyle.baseTitle2Bold.textStyle.copy(color = FestiCoreSemanticColor.labelNormal.color),
        )
        BasicText(
            text = "팔로워에게 보일 깃발 프로필이에요.\n언제든 고칠 수 있어요.",
            style = FestiCoreTextStyle.baseCalloutRegular.textStyle.copy(color = FestiCoreSemanticColor.labelNeutral.color),
        )
    }
}

@Composable
private fun PhotoSection(state: BearerProfileState, callbacks: BearerProfileCallbacks) {
    Column(verticalArrangement = Arrangement.spacedBy(FestiCoreSpacing.marginM8)) {
        FCPhotoPicker(
            state = photoState(state),
            buttonTitle = if (hasRemotePhotoOnly(state)) "바꾸기" else null,
            onClick = if (state.isUploadingPhoto) callbacks.onPhotoCancelTapped else callbacks.onPhotoTapped,
        )
        (state.photoUpload as? BearerPhotoUpload.Failed)?.let { failed ->
            BasicText(
                text = failed.message,
                style = FestiCoreTextStyle.baseFootnoteRegular.textStyleWithoutLineBox.copy(
                    color = FestiCoreSemanticColor.statusNegative.color,
                ),
            )
        }
    }
}

@Composable
private fun TagSection(state: BearerProfileState, onTagTapped: (BearerFlagTag) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(GroupSpacing)) {
        SectionLabel(title = "성향 태그", count = "${state.tags.size} / ${BearerProfileState.TAG_LIMIT}")
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(FestiCoreSpacing.marginM8),
        ) {
            BearerFlagTag.entries.forEach { tag ->
                FCChip(label = tag.title, isSelected = tag in state.tags, onClick = { onTagTapped(tag) })
            }
        }
        Helper("1~3개 고르면 깃발 디렉토리 필터에 걸려요.")
    }
}

@Composable
private fun ZoneSection(state: BearerProfileState, onZoneTapped: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(GroupSpacing)) {
        SectionLabel(title = "주로 있는 존", count = null)
        // iOS LazyVGrid 2열(열 사이 M8, 행 사이 12)
        Column(verticalArrangement = Arrangement.spacedBy(GroupSpacing)) {
            state.zones.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(FestiCoreSpacing.marginM8)) {
                    row.forEach { zone ->
                        BearerZoneButton(
                            name = zone.name,
                            isSelected = state.homeZoneId == zone.id,
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
private fun SettingSection(state: BearerProfileState, callbacks: BearerProfileCallbacks) {
    Column(verticalArrangement = Arrangement.spacedBy(GroupSpacing)) {
        SectionLabel(title = "오늘 계획 · 함께하는 기수", count = null)
        // Figma S10 Card: 모서리 16 안에 행을 자르고 1pt 테두리를 두른다.
        val shape = RoundedCornerShape(FestiCoreRadius.r16)
        Column(modifier = Modifier.fillMaxWidth().clip(shape).border(CardBorderWidth, FestiCoreSemanticColor.lineNormal.color, shape)) {
            FCListRow(
                label = "오늘 계획",
                value = if (state.plan.isEmpty()) "고르기" else "${state.plan.size}개 선택",
                onClick = callbacks.onPlanTapped,
            )
            FCListRow(label = "팔로워에게 오늘 계획 보여주기", isOn = state.planVisible, onIsOnChange = callbacks.onPlanVisibleChanged)
            FCListRow(label = "공동 기수 초대", value = state.coBearerText, onClick = callbacks.onInviteTapped)
        }
        Helper("오늘 계획은 내 일정에서 고른 셋이 그대로 보여요.")
    }
}

@Composable
private fun ColumnScope.DoneContent(
    state: BearerProfileState,
    profile: BearerFlagProfile,
    callbacks: BearerProfileCallbacks,
) {
    // Figma S10-E: 내용(히어로 · 미리보기, 사이 40)을 위아래 24 여백 안에서 세로 가운데에 둔다.
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
            FCDoneHero(
                title = "깃발이 준비됐어요",
                message = "공연 당일 기수 보드에서 깃발을 올리면\n팔로워에게 알림이 가요.",
                modifier = Modifier.fillMaxWidth(),
            )
            Column(verticalArrangement = Arrangement.spacedBy(FestiCoreSpacing.marginM12)) {
                BasicText(
                    text = "깃발 목록에서 이렇게 보여요",
                    style = FestiCoreTextStyle.baseFootnoteBold.textStyle.copy(color = FestiCoreSemanticColor.labelAssistive.color),
                )
                PreviewCard(state = state, profile = profile)
            }
        }
    }
    ActionBar {
        FCPrimaryButton(title = "완료", onClick = callbacks.onDoneTapped)
        FCButton(title = "공동 기수 초대하기", style = FCButtonStyle.Ghost, isEnabled = !state.isInviting, onClick = callbacks.onInviteTapped)
    }
}

/** Figma S10 Preview: 깃발 목록과 같은 Flag Card(디렉토리)로 보여 준다. 팔로우 버튼은 숨긴다(내 깃발이라 팔로우할 수 없다). */
@Composable
private fun PreviewCard(state: BearerProfileState, profile: BearerFlagProfile) {
    val zone = state.zones.firstOrNull { it.id == profile.homeZoneId }
    val uploaded = rememberImageBitmap(state.uploadedPhotoData, PreviewThumbnailSize)
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
            when {
                uploaded != null -> Image(
                    bitmap = uploaded,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )

                state.photoUrl != null ->
                    FCRemoteImage(
                        url = state.photoUrl,
                        modifier = Modifier.fillMaxSize(),
                        placeholderIcon = R.drawable.ic_flag_20,
                        placeholderIconSize = PlaceholderIconSize,
                    )

                else -> FCThumbnail(null)
            }
        },
    )
}

/** Figma S10 Bottom Bar: 버튼 사이 4, 위 12 · 아래 8. iOS는 흐림 위에 `materialGlass`를 얹지만 Compose 표준 흐림이 없어 단색만 쓴다(설계 적응 표). */
@Composable
private fun ActionBar(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(FestiCoreSemanticColor.materialGlass.color)
                .navigationBarsPadding()
                .padding(horizontal = FestiCoreSpacing.paddingP16)
                .padding(top = FestiCoreSpacing.paddingP12, bottom = FestiCoreSpacing.paddingP8),
        verticalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP4),
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content,
    )
}

@Composable
private fun SectionLabel(title: String, count: String?) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        BasicText(
            text = title,
            modifier = Modifier.weight(1f),
            style = FestiCoreTextStyle.baseFootnoteBold.textStyle.copy(color = FestiCoreSemanticColor.labelNormal.color),
        )
        count?.let {
            BasicText(
                text = it,
                // iOS `.monospacedDigit()`
                style =
                    FestiCoreTextStyle.baseFootnoteRegular.textStyle.copy(
                        color = FestiCoreSemanticColor.labelAssistive.color,
                        fontFeatureSettings = "tnum",
                    ),
            )
        }
    }
}

/** Figma 안내 줄: 13pt, 줄 상자 18 */
@Composable
private fun Helper(text: String) {
    BasicText(
        text = text,
        style = FestiCoreTextStyle.baseFootnoteRegular.textStyle.copy(color = FestiCoreSemanticColor.labelAssistive.color),
    )
}

@Composable
private fun ProfileAlert(alert: BearerProfileAlert, onDismiss: () -> Unit) {
    val item =
        remember(alert) {
            when (alert) {
                is BearerProfileAlert.Invite ->
                    FCDialogItem(
                        title = "공동 기수 초대 코드",
                        message = "${alert.code}\n24시간 안에 함께할 기수가 기수 인증 코드에 입력하면 돼요.",
                        confirmTitle = "확인",
                        cancelTitle = "닫기",
                        onConfirm = {},
                    )

                is BearerProfileAlert.InviteFailed ->
                    FCDialogItem(title = "초대하지 못했어요", message = alert.message, confirmTitle = "확인", cancelTitle = "닫기", onConfirm = {})
            }
        }
    FCDialog(item = item, onDismiss = onDismiss)
}

@Composable
private fun photoState(state: BearerProfileState): FCPhotoPickerState {
    val upload = state.photoUpload
    val uploading = rememberImageBitmap((upload as? BearerPhotoUpload.Uploading)?.data, PickerPhotoSize)
    val uploaded = rememberImageBitmap(state.uploadedPhotoData, PickerPhotoSize)
    return when {
        upload is BearerPhotoUpload.Uploading && uploading != null -> FCPhotoPickerState.Uploading(
            image = BitmapPainter(uploading),
            progress = upload.progress,
        )

        uploaded != null -> FCPhotoPickerState.Filled(image = BitmapPainter(uploaded))

        else -> FCPhotoPickerState.Empty
    }
}

/**
 * iOS `UIImage(data:)`. 메인 스레드를 막지 않도록 [Dispatchers.Default]에서 [side](표시 한 변) 픽셀에 맞춰 줄여 디코딩한다.
 * 디코딩 전 · 읽지 못하는 바이트는 null이라 iOS 이미지 없음과 같은 자리 표시(빈 칸 · 기본 썸네일)를 그린다.
 */
@Composable
private fun rememberImageBitmap(data: ByteArray?, side: Dp): ImageBitmap? {
    val sidePx = with(LocalDensity.current) { side.roundToPx() }
    val bitmap by produceState<ImageBitmap?>(initialValue = null, data, sidePx) {
        value = data?.let { bytes -> withContext(Dispatchers.Default) { decodeSampled(bytes, sidePx)?.asImageBitmap() } }
    }
    return bitmap
}

/** 짧은 변이 [sidePx] 아래로 내려가지 않는 가장 큰 2의 거듭제곱으로 줄여 디코딩한다. */
internal fun decodeSampled(bytes: ByteArray, sidePx: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    val options = BitmapFactory.Options().apply { inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, sidePx) }
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
}

internal fun sampleSize(
    width: Int,
    height: Int,
    sidePx: Int,
): Int {
    var sample = 1
    val shortSide = minOf(width, height)
    while (sidePx > 0 && shortSide / (sample * 2) >= sidePx) sample *= 2
    return sample
}

/** 서버에 사진은 있지만 이 기기에서 고른 사진이 없을 때. 사진 칸은 비우고 버튼만 "바꾸기"로 둔다. */
private fun hasRemotePhotoOnly(state: BearerProfileState): Boolean =
    state.uploadedPhotoData == null && state.photoUrl != null && !state.isUploadingPhoto

private fun headerTitle(state: BearerProfileState): String =
    if (state.isNewBearer) "${state.festivalName ?: "페스티벌"} 기수가 됐어요" else "깃발 프로필을 고쳐요"

private fun saveTitle(state: BearerProfileState): String = when {
    state.isUploadingPhoto -> "사진 올리는 중이에요"
    state.isSaving -> "저장하는 중이에요"
    state.isNewBearer -> "등록하고 기수 시작하기"
    else -> "저장하기"
}

/** iOS `BearerFlagTag.title`. */
internal val BearerFlagTag.title: String
    get() = when (this) {
        BearerFlagTag.Slam -> "슬램"
        BearerFlagTag.Sing -> "떼창"
        BearerFlagTag.Train -> "기차놀이"
        BearerFlagTag.Calm -> "차분"
        BearerFlagTag.Newbie -> "초보 환영"
    }
