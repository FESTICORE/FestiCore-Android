package io.festicore.ui.designkit.component.card

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.festicore.ui.designkit.component.FCPreviewSurface
import io.festicore.ui.designkit.component.FCPreviews
import io.festicore.ui.designkit.component.action.FCButtonStyle
import io.festicore.ui.designkit.component.label.FCStatusChipStatus
import io.festicore.ui.designkit.tokens.FestiCoreSemanticColor
import io.festicore.ui.designkit.tokens.FestiCoreSpacing

@FCPreviews
@Composable
private fun FCActionCardsPreview() {
    FCPreviewSurface {
        FCNextSetCard(artist = "실리카겔", meta = "메인 스테이지 · 19:30", remain = "12분", updated = "방금 갱신", progress = 0.6)
        FCHomeCard(
            remain = "막차까지 1시간 20분",
            shuttle = "셔틀 23:10",
            train = "막차 23:48",
            actions = listOf(FCCardAction("길 찾기") {}, FCCardAction("알림 받기", style = FCButtonStyle.Primary) {}),
        )
        FCDoneCard(
            title = "오늘도 즐거웠나요?",
            sub = "오늘의 기록",
            stats = listOf(FCDoneCardStat("5", "본 공연"), FCDoneCardStat("2", "깃발")),
        )
        FCEmptyCard(title = "아직 팔로우한 깃발이 없어요", message = "깃발을 팔로우하면 여기에서 볼 수 있어요", action = FCCardAction("깃발 찾기") {}, onClose = {})
        FCGuideSection(title = "처음이신가요?", message = "깃발을 팔로우해 보세요", actions = listOf(FCCardAction("둘러보기") {}))
    }
}

@FCPreviews
@Composable
private fun FCFestivalCardPreview() {
    FCPreviewSurface {
        FCFestivalImage(null)
        FCFestivalCard(
            title = "펜타포트 락 페스티벌",
            meta = "8월 1일 - 3일 · 송도",
            status = FCFestivalCardStatus.DDay("D-12"),
            isBooked = true,
            onBookmark = {},
            onTicketLink = {},
        )
        FCFestivalCard(
            title = "펜타포트 락 페스티벌",
            meta = "8월 1일 - 3일 · 송도",
            status = FCFestivalCardStatus.Ended(),
            pack = FCFestivalCardPack.Received("팩을 받았어요"),
        )
    }
}

@FCPreviews
@Composable
private fun FCFlagCardPreview() {
    FCPreviewSurface {
        FCFlagCard(
            name = "호락호락",
            status = FCStatusChipStatus.Active,
            zone = "리벳 앞 왼쪽",
            context = FCFlagCardContext.Board(activity = "슬램", time = "4분 전"),
        )
        FCFlagCard(
            name = "호락호락",
            status = FCStatusChipStatus.Active,
            zone = "리벳 앞 왼쪽",
            context = FCFlagCardContext.Directory("팔로워 1,284", listOf("슬램", "떼창", "초보 환영"), isFollowing = false) {},
        )
    }
}

@FCPreviews
@Composable
private fun FCFlagHeroPreview() {
    FCFlagHero(
        name = "호락호락",
        followers = "팔로워 1,284",
        tags = listOf("슬램", "떼창"),
        status = FCStatusChipStatus.Active,
        intro = "리벳 스테이지 앞에서 슬램을 즐기는 깃발이에요",
        isFollowing = false,
        onFollow = {},
    ) {
        Box(modifier = Modifier.fillMaxSize().background(FestiCoreSemanticColor.fillStrong.color))
    }
}

@FCPreviews
@Composable
private fun FCFlagNowCardPreview() {
    FCPreviewSurface {
        FCFlagNowCard(FCFlagNowCardState.Active(zone = "리벳 앞", activity = "슬램", source = "기수 · 4분 전"))
        FCFlagNowCard(FCFlagNowCardState.Muted(zone = "리벳 앞", activity = "슬램", source = "기수 · 40분 전"))
        FCFlagNowCard(FCFlagNowCardState.Ended())
    }
}

@FCPreviews
@Composable
private fun FCFlagTilePreview() {
    FCPreviewSurface {
        Row(horizontalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP12)) {
            FCFlagTile(
                name = "호락호락",
                status = FCStatusChipStatus.Active,
                zone = "리벳 앞",
                tags = "슬램 · 떼창",
                time = "4분 전",
                modifier = Modifier.width(160.dp),
            ) {}
        }
    }
}

@FCPreviews
@Composable
private fun FCInfoCardsPreview() {
    FCPreviewSurface {
        FCPlanPrivate()
        FCCountdownCard(label = "개장까지", time = "02:14:30", sub = "12:00 개장")
        FCLastRideCard(shuttle = "23:10", train = "23:48")
        FCBearerHeader(name = "호락호락 기수", status = FCStatusChipStatus.Active, statusText = "리벳 앞에서 활동 중", followers = "팔로워 1,284")
    }
}
