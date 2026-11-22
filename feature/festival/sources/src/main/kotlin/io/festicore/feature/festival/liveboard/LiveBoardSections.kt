package io.festicore.feature.festival.liveboard

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.festicore.domain.festival.FestivalLiveBoardSnapshot
import io.festicore.domain.festival.FestivalOfficialNews
import io.festicore.domain.festival.FestivalPackDocument
import io.festicore.feature.festival.FestivalAccessibilityId
import io.festicore.ui.designkit.R
import io.festicore.ui.designkit.component.action.FCButton
import io.festicore.ui.designkit.component.action.FCButtonSize
import io.festicore.ui.designkit.component.action.FCButtonStyle
import io.festicore.ui.designkit.component.card.FCCardAction
import io.festicore.ui.designkit.component.card.FCEmptyCard
import io.festicore.ui.designkit.component.label.FCLiveBadge
import io.festicore.ui.designkit.component.label.FCLiveBadgeKind
import io.festicore.ui.designkit.component.label.FCSourceLine
import io.festicore.ui.designkit.component.label.FCStatusChip
import io.festicore.ui.designkit.component.label.FCStatusChipStatus
import io.festicore.ui.designkit.component.label.FCTag
import io.festicore.ui.designkit.component.label.FCTagTone
import io.festicore.ui.designkit.component.label.FCZoneChip
import io.festicore.ui.designkit.component.list.FCListRow
import io.festicore.ui.designkit.component.list.FCSectionHeader
import io.festicore.ui.designkit.component.media.FCAvatar
import io.festicore.ui.designkit.component.media.FCAvatarTint
import io.festicore.ui.designkit.component.skeleton.FCSkeletonShape
import io.festicore.ui.designkit.component.skeleton.FCSkeletonView
import io.festicore.ui.designkit.tokens.FestiCoreRadius
import io.festicore.ui.designkit.tokens.FestiCoreSemanticColor
import io.festicore.ui.designkit.tokens.FestiCoreSpacing
import io.festicore.ui.designkit.tokens.FestiCoreTextStyle
import io.festicore.ui.designkit.typography.textStyle
import io.festicore.ui.sharedui.remoteimage.FCRemoteImage
import java.time.Instant

private val CardShape = RoundedCornerShape(FestiCoreRadius.r16)
private val ImageShape = RoundedCornerShape(FestiCoreRadius.r12)
private val TileWidth = 156.dp
private val TileMinHeight = 204.dp
private val NewsImageHeight = 132.dp
private val FlagTileImageHeight = 88.dp
private val FlagRowThumbnailSize = 40.dp
private val Icon16 = 16.dp
private val Icon20 = 20.dp
private val Icon24 = 24.dp
private val GatherReservedWidth = 76.dp
private val SkeletonCardHeight = 144.dp
private val SkeletonTitleWidth = 120.dp
private val SkeletonTitleHeight = 22.dp
private val SkeletonRowHeight = 88.dp
private val CrewSkeletonHeight = 228.dp
private val FollowedSkeletonHeight = 204.dp
private const val LEAVING_ALPHA = 0.6f
private const val LOADING_MESSAGE = "실시간 정보를 불러오는 중입니다."
private const val FAILED_MESSAGE = "실시간 정보를 불러올 수 없습니다. 잠시 후 다시 시도합니다."

/** 크루 아바타 색 순환(iOS `FestivalLiveBoardCrewCard.tints`). */
private val CrewTints =
    listOf(FCAvatarTint.Blue, FCAvatarTint.Pink, FCAvatarTint.Orange, FCAvatarTint.Green, FCAvatarTint.Purple, FCAvatarTint.Yellow)

private fun loadingMessage(isUnavailable: Boolean) = if (isUnavailable) FAILED_MESSAGE else LOADING_MESSAGE

@Composable
private fun RoundSkeleton(height: Dp, label: String) {
    FCSkeletonView(
        shape = FCSkeletonShape.Round(FestiCoreRadius.r16),
        modifier = Modifier.fillMaxWidth().height(height).clearAndSetSemantics { contentDescription = label },
    )
}

/** 공연 카드와 무대 목록을 불러오는 동안(iOS `FestivalLiveBoardSkeletonView`). */
@Composable
internal fun LiveBoardSkeleton(modifier: Modifier = Modifier) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .testTag(FestivalAccessibilityId.LIVE_BOARD_SKELETON)
                .clearAndSetSemantics { contentDescription = "라이브 보드를 불러오는 중" },
        verticalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP24),
    ) {
        FCSkeletonView(FCSkeletonShape.Round(FestiCoreRadius.r16), Modifier.fillMaxWidth().height(SkeletonCardHeight))
        Column(verticalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP12)) {
            FCSkeletonView(FCSkeletonShape.Round(), Modifier.width(SkeletonTitleWidth).height(SkeletonTitleHeight))
            repeat(2) { FCSkeletonView(FCSkeletonShape.Round(FestiCoreRadius.r16), Modifier.fillMaxWidth().height(SkeletonRowHeight)) }
        }
    }
}

// MARK: - 지금 / 다음 무대

/** `지금 / 다음 무대`(iOS `FestivalLiveBoardView`). 무대 카드 한 장 안에 무대별 행을 구분선으로 나눠 그린다. */
@Composable
internal fun LiveBoardStageSection(
    rows: List<LiveBoardPresentation.StageRow>,
    updated: String?,
    source: String?,
) {
    Column(verticalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP16)) {
        FCSectionHeader(title = "지금 / 다음 무대", actionTitle = updated)
        if (rows.isEmpty()) {
            Text(
                text = "현재 진행 중이거나 예정된 공연 정보가 없습니다.",
                style = FestiCoreTextStyle.baseSubheadlineRegular.textStyle,
                color = FestiCoreSemanticColor.labelNeutral.color,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(FestiCoreSemanticColor.backgroundElevated.color, CardShape)
                        .padding(FestiCoreSpacing.paddingP20),
            )
        } else {
            Column(modifier = Modifier.fillMaxWidth().clip(CardShape).background(FestiCoreSemanticColor.backgroundElevated.color)) {
                rows.forEachIndexed { index, row ->
                    if (index > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(FestiCoreSemanticColor.lineNormal.color))
                    StageRow(row)
                }
            }
        }
        source?.let { FCSourceLine(source = it, isInset = false) }
    }
}

@Composable
private fun StageRow(row: LiveBoardPresentation.StageRow) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(if (row.isMine) FestiCoreSemanticColor.accentBackgroundLime.color else Color.Transparent)
                .padding(FestiCoreSpacing.paddingP16)
                .semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP12),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP8), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = row.name,
                style = FestiCoreTextStyle.baseFootnoteBold.textStyle,
                color = FestiCoreSemanticColor.labelNeutral.color,
                modifier = Modifier.weight(1f),
            )
            if (row.isMine) FCTag(label = "내 일정", tone = FCTagTone.Lime)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP8), verticalAlignment = Alignment.CenterVertically) {
            row.now?.let { StageSlot(it, FCLiveBadgeKind.Now, Modifier.weight(1f)) }
            if (row.now != null && row.next != null) {
                Image(
                    painter = painterResource(R.drawable.ic_chevron_right_16),
                    contentDescription = null,
                    modifier = Modifier.size(Icon16),
                    colorFilter = ColorFilter.tint(FestiCoreSemanticColor.labelAssistive.color),
                )
            }
            row.next?.let { StageSlot(it, FCLiveBadgeKind.Next, Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun StageSlot(
    slot: LiveBoardPresentation.StageRow.Slot,
    kind: FCLiveBadgeKind,
    modifier: Modifier,
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP8)) {
        FCLiveBadge(kind = kind)
        Column(verticalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP4)) {
            Text(
                text = slot.artist,
                style = (
                    if (kind ==
                        FCLiveBadgeKind.Now
                    ) {
                        FestiCoreTextStyle.baseBodyBold
                    } else {
                        FestiCoreTextStyle.baseCalloutMedium
                    }
                    ).textStyle,
                color = FestiCoreSemanticColor.labelNormal.color,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = slot.time,
                style = FestiCoreTextStyle.baseCaption1Regular.textStyle,
                color = FestiCoreSemanticColor.labelAssistive.color,
                maxLines = 1,
            )
        }
    }
}

// MARK: - 공식 인스타그램

/** `공식 인스타그램`(iOS `FestivalLiveBoardOfficialNewsSection`). 게시물이 있을 때만 보인다. */
@Composable
internal fun LiveBoardOfficialNewsSection(
    news: FestivalOfficialNews?,
    now: Instant,
    onPostTapped: (String) -> Unit,
) {
    if (news == null || !news.isVisible) return
    Column(verticalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP16)) {
        FCSectionHeader(title = "공식 인스타그램")
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()).padding(end = FestiCoreSpacing.paddingP16),
            horizontalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP12),
            verticalAlignment = Alignment.Top,
        ) {
            news.posts.forEach { post -> OfficialNewsCard(post, now) { onPostTapped(post.permalink) } }
        }
    }
}

@Composable
private fun OfficialNewsCard(
    post: FestivalOfficialNews.Post,
    now: Instant,
    onClick: () -> Unit,
) {
    val posted = LiveBoardPresentation.relativeTime(post.postedAt, now) ?: LiveBoardPresentation.monthDayTime(post.postedAt)
    Column(
        modifier =
            Modifier
                .width(TileWidth)
                .clip(CardShape)
                .background(FestiCoreSemanticColor.backgroundElevated.color)
                .clickable(onClick = onClick)
                .semantics(mergeDescendants = true) {
                    onClick(label = "인스타그램에서 게시물을 열어요") {
                        onClick()
                        true
                    }
                }
                .padding(FestiCoreSpacing.paddingP12),
        verticalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP8),
    ) {
        Thumbnail(
            url = post.imageUrl,
            icon = R.drawable.ic_music_24,
            iconSize = Icon24,
            modifier = Modifier.fillMaxWidth().height(NewsImageHeight).clip(ImageShape),
        )
        Text(
            text = post.caption ?: "새 게시물",
            style = FestiCoreTextStyle.baseFootnoteRegular.textStyle,
            color = FestiCoreSemanticColor.labelNormal.color,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(text = posted, style = FestiCoreTextStyle.baseCaption1Regular.textStyle, color = FestiCoreSemanticColor.labelAssistive.color)
    }
}

/** 원격 이미지, 주소가 없으면 `fillStrong` 바탕 + 아이콘(iOS `FestivalLiveBoardFlagThumbnail` · 소식 카드 썸네일). */
@Composable
private fun Thumbnail(
    url: String?,
    icon: Int,
    iconSize: Dp,
    modifier: Modifier,
) {
    if (url != null) {
        FCRemoteImage(url = url, modifier = modifier, placeholderIcon = icon, placeholderIconSize = iconSize)
    } else {
        Box(modifier = modifier.background(FestiCoreSemanticColor.fillStrong.color), contentAlignment = Alignment.Center) {
            Image(
                painter = painterResource(icon),
                contentDescription = null,
                modifier = Modifier.size(iconSize),
                colorFilter = ColorFilter.tint(FestiCoreSemanticColor.labelAssistive.color),
            )
        }
    }
}

// MARK: - 내 크루

/** `내 크루`(iOS `FestivalLiveBoardCrewSection`). */
@Composable
internal fun LiveBoardCrewSection(
    snapshot: FestivalLiveBoardSnapshot?,
    isUnavailable: Boolean,
    pack: FestivalPackDocument?,
    hasSession: Boolean,
    sessionUserId: String?,
    showsGather: Boolean,
    showsOfflineStatus: Boolean,
    now: Instant,
    onCrewLoginTapped: () -> Unit,
    onGatherCrewTapped: () -> Unit,
    onZoneChipTapped: (String) -> Unit,
    onCrewTapped: () -> Unit,
    onCreateCrewTapped: () -> Unit,
    onRetryTapped: () -> Unit,
) {
    val crew = snapshot?.crew
    when {
        !hasSession -> LoginCard(title = "크루와 함께 보려면 로그인", onClick = onCrewLoginTapped)

        crew != null ->
            CrewCard(
                crew = LiveBoardPresentation.crew(crew, sessionUserId, { pack?.zoneName(it) }, now),
                showsGather = showsGather,
                canGather = !showsOfflineStatus && (crew.gatherAvailableAt?.let { it <= now } ?: true),
                onGather = onGatherCrewTapped,
                onZoneTap = onZoneChipTapped,
                onOpen = onCrewTapped,
            )

        snapshot == null && !isUnavailable -> RoundSkeleton(CrewSkeletonHeight, "내 크루를 불러오는 중")

        snapshot == null ->
            FCEmptyCard(
                title = "내 크루를 불러오지 못했어요",
                message = loadingMessage(isUnavailable),
                action = FCCardAction("다시 시도", onClick = onRetryTapped),
            )

        else ->
            FCEmptyCard(
                title = "아직 크루가 없어요",
                message = "친구를 초대하면 서로 어느 존에 있는지 바로 볼 수 있어요.",
                action = FCCardAction("크루 만들기", style = FCButtonStyle.Primary, onClick = onCreateCrewTapped),
            )
    }
}

@Composable
private fun LoginCard(title: String, onClick: () -> Unit) {
    Box(modifier = Modifier.fillMaxWidth().clip(CardShape).background(FestiCoreSemanticColor.backgroundElevated.color)) {
        FCListRow(label = title, onClick = onClick)
    }
}

@Composable
private fun CrewCard(
    crew: LiveBoardPresentation.Crew,
    showsGather: Boolean,
    canGather: Boolean,
    onGather: () -> Unit,
    onZoneTap: (String) -> Unit,
    onOpen: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(CardShape)
                .background(FestiCoreSemanticColor.backgroundElevated.color)
                // 모여! 버튼 밖을 누르면 크루 화면(S07)을 연다.
                .clickable(onClick = onOpen)
                .semantics {
                    customActions = listOf(
                        CustomAccessibilityAction("크루 화면 열기") {
                            onOpen()
                            true
                        },
                    )
                },
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(end = if (showsGather) GatherReservedWidth else 0.dp)
                        .padding(
                            start = FestiCoreSpacing.paddingP16,
                            top = FestiCoreSpacing.paddingP16,
                            end = FestiCoreSpacing.paddingP16,
                            bottom = FestiCoreSpacing.paddingP8,
                        ),
                verticalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP4),
            ) {
                Text(
                    text = crew.title,
                    style = FestiCoreTextStyle.baseHeadlineBold.textStyle,
                    color = FestiCoreSemanticColor.labelNormal.color,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = crew.subtitle,
                    style = FestiCoreTextStyle.baseFootnoteRegular.textStyle,
                    color = FestiCoreSemanticColor.labelAssistive.color,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (showsGather) {
                FCButton(
                    title = "모여!",
                    modifier = Modifier.align(Alignment.TopEnd).padding(FestiCoreSpacing.paddingP16),
                    style = FCButtonStyle.Inverse,
                    size = FCButtonSize.Medium,
                    isEnabled = canGather,
                    onClick = onGather,
                )
            }
        }
        crew.members.forEachIndexed { index, member -> CrewMemberRow(member, CrewTints[index % CrewTints.size], onZoneTap) }
    }
}

@Composable
private fun CrewMemberRow(
    member: LiveBoardPresentation.CrewMember,
    tint: FCAvatarTint,
    onZoneTap: (String) -> Unit,
) {
    val primary = if (member.isLeaving) FestiCoreSemanticColor.labelAssistive else FestiCoreSemanticColor.labelNormal
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .alpha(if (member.isLeaving) LEAVING_ALPHA else 1f)
                .padding(vertical = FestiCoreSpacing.paddingP12, horizontal = FestiCoreSpacing.paddingP16)
                .semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP16),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FCAvatar(initial = member.nickname, tint = tint)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP8)) {
            Row(horizontalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP4), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = member.nickname,
                    style = FestiCoreTextStyle.baseCalloutBold.textStyle,
                    color = primary.color,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (member.isMe) FCTag(label = "나")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP8), verticalAlignment = Alignment.CenterVertically) {
                member.zone?.let { zone -> FCZoneChip(zone = zone, onClick = { member.zoneId?.let(onZoneTap) }) }
                member.status?.let {
                    Text(text = it, style = FestiCoreTextStyle.baseFootnoteBold.textStyle, color = primary.color, maxLines = 1)
                }
            }
        }
        member.time?.let {
            Text(
                text = it,
                style = FestiCoreTextStyle.baseCaption1Regular.textStyle,
                color = (if (member.isLeaving) FestiCoreSemanticColor.labelDisable else FestiCoreSemanticColor.labelAssistive).color,
            )
        }
    }
}

// MARK: - 팔로우한 깃발

/** `팔로우한 깃발`(iOS `FestivalLiveBoardFollowedFlagSection`). 로그인 · 수신 · 팔로우 없음 · 안내 숨김이면 섹션을 숨긴다. */
@Composable
internal fun LiveBoardFollowedFlagSection(
    snapshot: FestivalLiveBoardSnapshot?,
    isUnavailable: Boolean,
    pack: FestivalPackDocument?,
    hasSession: Boolean,
    showsFollowGuide: Boolean,
    now: Instant,
    festivalEnded: Boolean,
    onFollowLoginTapped: () -> Unit,
    onFindFlagTapped: () -> Unit,
    onHideFollowGuideTapped: () -> Unit,
    onRetryTapped: () -> Unit,
) {
    if (hasSession && snapshot != null && snapshot.followedFlags.isEmpty() && !showsFollowGuide) return
    val followed = snapshot?.followedFlags.orEmpty()
    Column(verticalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP16)) {
        FCSectionHeader(title = "팔로우한 깃발")
        when {
            !hasSession -> LoginCard(title = "깃발을 팔로우하려면 로그인", onClick = onFollowLoginTapped)

            followed.isNotEmpty() ->
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()).padding(end = FestiCoreSpacing.paddingP16),
                    horizontalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP12),
                    verticalAlignment = Alignment.Top,
                ) {
                    followed.forEach { flag ->
                        FlagTile(LiveBoardPresentation.flag(flag, { pack?.zoneName(it) }, now, festivalEnded))
                    }
                }

            snapshot == null && !isUnavailable -> RoundSkeleton(FollowedSkeletonHeight, "팔로우한 깃발을 불러오는 중")

            snapshot == null ->
                FCEmptyCard(
                    title = "팔로우한 깃발을 불러오지 못했어요",
                    message = loadingMessage(isUnavailable),
                    icon = R.drawable.ic_flag_20,
                    action = FCCardAction("다시 시도", onClick = onRetryTapped),
                )

            showsFollowGuide ->
                // Figma S01 D: 팔로우 안내 아이콘은 flag 24, 닫기는 xmark 16(Label/Assistive) 터치 44.
                FCEmptyCard(
                    title = "만나보고 싶은 깃발이 있나요?",
                    message = "팔로우하면 깃발이 움직일 때 조용히 알려 드려요.",
                    icon = R.drawable.ic_flag,
                    action = FCCardAction("깃발 찾기", style = FCButtonStyle.Primary, onClick = onFindFlagTapped),
                    onClose = onHideFollowGuideTapped,
                )
        }
    }
}

@Composable
private fun FlagTile(flag: LiveBoardPresentation.Flag) {
    Column(
        modifier =
            Modifier
                .width(TileWidth)
                .defaultMinSize(minHeight = TileMinHeight)
                .background(FestiCoreSemanticColor.backgroundElevated.color, CardShape)
                .padding(FestiCoreSpacing.paddingP12)
                .semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP8),
    ) {
        Thumbnail(flag.thumbnailUrl, R.drawable.ic_flag_20, Icon20, Modifier.fillMaxWidth().height(FlagTileImageHeight).clip(ImageShape))
        Row(horizontalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP4), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = flag.name,
                style = FestiCoreTextStyle.baseCalloutBold.textStyle,
                color = FestiCoreSemanticColor.labelNormal.color,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            flag.status?.let { FCStatusChip(status = it.chip) }
        }
        // 타일 안 존 칩은 누르지 않는다(iOS `allowsHitTesting(false)`).
        flag.zone?.let { FCZoneChip(zone = it, modifier = Modifier.clearAndSetSemantics { contentDescription = it }, onClick = {}) }
        Row(horizontalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP8), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = flag.tags,
                style = FestiCoreTextStyle.baseFootnoteRegular.textStyle,
                color = FestiCoreSemanticColor.labelNeutral.color,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            flag.time?.let {
                Text(
                    text = it,
                    style = FestiCoreTextStyle.baseCaption1Regular.textStyle,
                    color = FestiCoreSemanticColor.labelAssistive.color,
                    maxLines = 1,
                )
            }
        }
    }
}

// MARK: - 활성 깃발

/** `활성 깃발`(iOS `FestivalLiveBoardActiveFlagSection`). 개장 전이고 팩이 있으면 도착 예정 순, 아니면 최근 업데이트 순. */
@Composable
internal fun LiveBoardActiveFlagSection(
    snapshot: FestivalLiveBoardSnapshot?,
    isUnavailable: Boolean,
    pack: FestivalPackDocument?,
    now: Instant,
    isBeforeOpening: Boolean,
    onRetryTapped: () -> Unit,
) {
    val ordersByArrival = isBeforeOpening && pack != null
    val active = snapshot?.activeFlags.orEmpty()
    Column(verticalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP16)) {
        FCSectionHeader(title = "활성 깃발", actionTitle = if (ordersByArrival) "도착 예정 순" else "최근 업데이트 순")
        when {
            active.isNotEmpty() -> {
                val sorted =
                    if (ordersByArrival && pack != null) {
                        LiveBoardPresentation.sortedActiveFlagsByArrival(active, pack, now)
                    } else {
                        LiveBoardPresentation.sortedActiveFlags(active)
                    }
                Column(modifier = Modifier.fillMaxWidth().clip(CardShape).background(FestiCoreSemanticColor.backgroundElevated.color)) {
                    sorted.forEach { ActiveFlagRow(LiveBoardPresentation.flag(it, { zone -> pack?.zoneName(zone) }, now)) }
                }
            }

            snapshot == null && !isUnavailable -> RoundSkeleton(CrewSkeletonHeight, "활성 깃발을 불러오는 중")

            snapshot == null ->
                FCEmptyCard(
                    title = "활성 깃발을 불러오지 못했어요",
                    message = loadingMessage(isUnavailable),
                    icon = R.drawable.ic_flag_20,
                    action = FCCardAction("다시 시도", onClick = onRetryTapped),
                )

            else ->
                FCEmptyCard(
                    title = "활동 중인 깃발이 없어요",
                    message = "깃발이 움직이면 여기에서 바로 보여 드려요.",
                    icon = R.drawable.ic_flag_20,
                )
        }
    }
}

@Composable
private fun ActiveFlagRow(flag: LiveBoardPresentation.Flag) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = FestiCoreSpacing.paddingP12, horizontal = FestiCoreSpacing.paddingP16)
                .semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Thumbnail(flag.thumbnailUrl, R.drawable.ic_flag_20, Icon20, Modifier.size(FlagRowThumbnailSize).clip(ImageShape))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP4)) {
            Text(
                text = flag.name,
                style = FestiCoreTextStyle.baseCalloutBold.textStyle,
                color = FestiCoreSemanticColor.labelNormal.color,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (flag.meta.isNotEmpty()) {
                Text(
                    text = flag.meta,
                    style = FestiCoreTextStyle.baseFootnoteRegular.textStyle,
                    color = FestiCoreSemanticColor.labelAssistive.color,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP4)) {
            flag.status?.let { FCStatusChip(status = it.chip) }
            flag.time?.let {
                Text(
                    text = it,
                    style = FestiCoreTextStyle.baseCaption1Regular.textStyle,
                    color = FestiCoreSemanticColor.labelAssistive.color,
                )
            }
        }
    }
}

private val LiveBoardPresentation.FlagStatus.chip: FCStatusChipStatus
    get() =
        when (this) {
            LiveBoardPresentation.FlagStatus.Active -> FCStatusChipStatus.Active
            LiveBoardPresentation.FlagStatus.Resting -> FCStatusChipStatus.Resting
            LiveBoardPresentation.FlagStatus.Ended -> FCStatusChipStatus.EndedToday
        }
