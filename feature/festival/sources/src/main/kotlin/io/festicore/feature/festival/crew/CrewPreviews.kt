package io.festicore.feature.festival.crew

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import io.festicore.domain.festival.entity.FestivalCrew
import io.festicore.domain.festival.entity.FestivalCrewInvite
import io.festicore.domain.festival.entity.FestivalLiveBoardSnapshot
import io.festicore.domain.festival.entity.FestivalPackDocument
import io.festicore.feature.festival.crewcreate.CrewCreateContent
import io.festicore.feature.festival.crewcreate.CrewCreateState
import io.festicore.feature.festival.crewcreate.CrewCreateStep
import io.festicore.feature.festival.crewgather.CrewGatherContent
import io.festicore.feature.festival.crewgather.CrewGatherState
import io.festicore.feature.festival.crewjoin.CrewJoinContent
import io.festicore.feature.festival.crewjoin.CrewJoinLoad
import io.festicore.feature.festival.crewjoin.CrewJoinState
import java.time.Instant

// 프리뷰 전용 가짜 값.
private val PreviewNow = Instant.parse("2026-11-07T12:00:00Z")
private val PreviewZones = listOf(FestivalPackDocument.Zone("zone_a", "슬램 존"), FestivalPackDocument.Zone("zone_b", "메인 홀"))

private val PreviewLive =
    FestivalLiveBoardSnapshot.Crew(
        id = "crew_preview",
        name = "락린이들",
        members =
            listOf(
                FestivalLiveBoardSnapshot.Crew.Member("user_me", "민지", "here", "zone_a", PreviewNow.minusSeconds(120)),
                FestivalLiveBoardSnapshot.Crew.Member("user_b", "준호", "eating", "zone_b", PreviewNow.minusSeconds(600)),
                FestivalLiveBoardSnapshot.Crew.Member("user_c", "서연", "leaving", null, PreviewNow.minusSeconds(4_000)),
            ),
        ownerId = "user_me",
        gatherRequest =
            FestivalLiveBoardSnapshot.Crew.GatherRequest(
                id = "gth_preview",
                zoneId = "zone_a",
                byUserId = "user_b",
                at = PreviewNow.minusSeconds(60),
                expiresAt = PreviewNow.plusSeconds(540),
            ),
    )

private val PreviewCrew =
    FestivalCrew(
        id = "crew_preview",
        festivalId = "fst_preview",
        name = "락린이들",
        ownerId = "user_me",
        members = emptyList(),
        inviteCode = "K7P2QX",
        inviteExpiresAt = PreviewNow.plusSeconds(172_800),
        live = PreviewLive,
    )

private val PreviewState =
    CrewState(
        festivalId = "fst_preview",
        crewId = "crew_preview",
        zones = PreviewZones,
        archivesAt = PreviewNow.plusSeconds(86_400),
        festivalName = "원더리벳 2026",
        sessionUserId = "user_me",
        crew = PreviewCrew,
        live = PreviewLive,
        load = CrewLoad.Loaded,
        now = PreviewNow,
    )

@Preview(name = "Crew · 모여 받음", heightDp = 1000)
@Composable
private fun CrewGatherReceivedPreview() {
    CrewContent(state = PreviewState)
}

@Preview(name = "Crew · 끝난 크루")
@Composable
private fun CrewArchivedPreview() {
    CrewContent(state = PreviewState.copy(crew = PreviewCrew.copy(archivedAt = PreviewNow)))
}

@Preview(name = "Crew · 불러오는 중")
@Composable
private fun CrewLoadingPreview() {
    CrewContent(state = PreviewState.copy(crew = null, live = null, load = CrewLoad.Loading))
}

@Preview(name = "Crew · 크루장 메뉴")
@Composable
private fun CrewMenuPreview() {
    CrewMenuContent(menu = CrewMenuState(crewName = "락린이들", members = listOf(CrewMenuState.Member("user_b", "준호"))))
}

@Preview(name = "CrewCreate · 이름")
@Composable
private fun CrewCreateNamingPreview() {
    CrewCreateContent(state = CrewCreateState(festivalId = "fst_preview", festivalName = "원더리벳 2026", name = "락린이들"))
}

@Preview(name = "CrewCreate · 만든 뒤")
@Composable
private fun CrewCreateCreatedPreview() {
    CrewCreateContent(state = CrewCreateState(festivalId = "fst_preview", step = CrewCreateStep.Created(PreviewCrew)))
}

@Preview(name = "CrewGather · 존")
@Composable
private fun CrewGatherPreview() {
    CrewGatherContent(state = CrewGatherState(crewId = "crew_preview", zones = PreviewZones, recipientCount = 2))
}

private val PreviewInvite =
    FestivalCrewInvite(
        code = "K7P2QX",
        crewName = "펜타 크루",
        festivalId = "fst_preview",
        festivalName = "펜타포트",
        inviterNickname = "민지",
        memberCount = 5,
        expiresAt = PreviewNow.plusSeconds(172_800),
        memberPreview =
            listOf(
                FestivalCrewInvite.Member("민지", null),
                FestivalCrewInvite.Member("준호", null),
                FestivalCrewInvite.Member("서연", null),
            ),
    )

private val PreviewJoinState = CrewJoinState(code = "K7P2QX", hasSession = true, invite = PreviewInvite, load = CrewJoinLoad.Loaded)

@Preview(name = "CrewJoin · 초대", heightDp = 800)
@Composable
private fun CrewJoinPreview() {
    CrewJoinContent(state = PreviewJoinState, now = PreviewNow)
}

@Preview(name = "CrewJoin · 만료", heightDp = 800)
@Composable
private fun CrewJoinExpiredPreview() {
    CrewJoinContent(state = PreviewJoinState, now = PreviewInvite.expiresAt)
}

@Preview(name = "CrewJoin · 정원 초과", heightDp = 800)
@Composable
private fun CrewJoinFullPreview() {
    CrewJoinContent(state = PreviewJoinState.copy(invite = PreviewInvite.copy(memberCount = FestivalCrew.MAX_MEMBERS)), now = PreviewNow)
}

@Preview(name = "CrewJoin · 불러오지 못함")
@Composable
private fun CrewJoinFailedPreview() {
    CrewJoinContent(state = CrewJoinState(code = "K7P2QX", hasSession = false, load = CrewJoinLoad.Failed), now = PreviewNow)
}
