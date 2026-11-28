package io.festicore.feature.festival.flagdetail

import io.festicore.domain.festival.entity.FestivalFlagDetail
import io.festicore.domain.festival.entity.FestivalFlagReportReason
import io.festicore.domain.festival.flagdetail.FestivalFlagDetailUseCase
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/** 깃발 상세 가짜 구현. 대표 깃발 지정은 화면이 부르지 않으므로 부르면 실패한다. 부른 사유를 [reports]에 남긴다. */
internal class FakeFlagDetailUseCase(
    var detail: suspend (String) -> FestivalFlagDetail = { FlagDetailFixtures.detail() },
    var report: suspend (String, FestivalFlagReportReason) -> Unit = { _, _ -> },
    var pin: suspend (String?) -> String? = { it },
) : FestivalFlagDetailUseCase {
    val reports = mutableListOf<FestivalFlagReportReason>()

    override suspend fun detail(flagId: String): FestivalFlagDetail = detail.invoke(flagId)

    override suspend fun report(flagId: String, reason: FestivalFlagReportReason) {
        reports += reason
        report.invoke(flagId, reason)
    }

    val pins = mutableListOf<String?>()

    override suspend fun setPinnedFlag(flagId: String?): String? {
        pins += flagId
        return pin(flagId)
    }
}

/** 테스트가 시각을 옮기는 시계. */
internal class MutableClock(var instant: Instant) : Clock() {
    override fun getZone(): ZoneId = ZoneOffset.UTC

    override fun withZone(zone: ZoneId?): Clock = this

    override fun instant(): Instant = instant
}

/** iOS `FestivalFlagDetailFeatureTests`의 `state` · `detail`. */
internal object FlagDetailFixtures {
    /** 2026-11-07 21:00 KST. */
    val NOW: Instant = Instant.parse("2026-11-07T12:00:00Z")

    fun context(hasSession: Boolean = true, accountId: String? = "user-a") = FlagDetailContext(
        zoneNames = mapOf("zone_a" to "리벳 스테이지 앞 왼쪽"),
        performances =
            mapOf(
                "set_past" to
                    FlagDetailContext.Performance("Sunset Riot", "MAIN 스테이지", NOW.minusSeconds(3 * 3_600), NOW.minusSeconds(2 * 3_600)),
                "set_now" to FlagDetailContext.Performance("The Lime Flags", "SUB 스테이지", NOW.minusSeconds(600), NOW.plusSeconds(1_800)),
                "set_late" to
                    FlagDetailContext.Performance(
                        "모쉬핏",
                        "TENT 스테이지",
                        Instant.ofEpochSecond(1_794_060_000),
                        Instant.ofEpochSecond(1_794_063_600),
                    ),
            ),
        mySetIds = setOf("set_now"),
        hasSession = hasSession,
        accountId = if (hasSession) accountId else null,
    )

    fun state(hasSession: Boolean = true) = FlagDetailState(flagId = "flg_1", context = context(hasSession), now = NOW)

    fun loaded(detail: FestivalFlagDetail = detail(), hasSession: Boolean = true) =
        state(hasSession).copy(content = FlagDetailContent.Loaded(detail))

    fun detail(
        status: FestivalFlagDetail.Status = FestivalFlagDetail.Status.Active,
        activity: FestivalFlagDetail.Activity? = FestivalFlagDetail.Activity.Slam,
        updatedMinutesAgo: Long = 4,
        plan: List<String> = listOf("set_past", "set_now", "set_late"),
        followerCount: Int = 1_284,
        isFollowing: Boolean = false,
        zoneId: String? = "zone_a",
    ) = FestivalFlagDetail(
        id = "flg_1",
        name = "호락호락",
        thumbnailUrl = null,
        photoUrl = null,
        tags = listOf("slam", "sing", "newbie"),
        bio = "처음 와도 괜찮아요",
        followerCount = followerCount,
        isPlanVisible = true,
        planSetIds = plan,
        homeZoneId = null,
        status = status,
        zoneId = zoneId,
        activity = activity,
        statusUpdatedAt = NOW.minusSeconds(updatedMinutesAgo * 60),
        isFollowing = isFollowing,
        isBearer = false,
        isHidden = false,
    )
}
