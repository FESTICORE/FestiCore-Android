package io.festicore.domain.bearer.sources.model.mapper

import io.festicore.domain.bearer.entity.BearerProfileChange
import io.festicore.domain.bearer.entity.BearerRegisterRequest
import io.festicore.domain.bearer.entity.BearerStatusChange
import io.festicore.domain.bearer.entity.BearerStatusRequest
import io.festicore.service.apiendpoint.flag.BearerRegisterBody
import io.festicore.service.apiendpoint.flag.FlagProfileBody
import io.festicore.service.apiendpoint.flag.FlagStatusBody
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** `clientAt` 본문 값. UTC 밀리초 ISO(`2026-10-21T00:00:00.000Z`). iOS `BearerDateFormat.string`. */
private val clientAtFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC)

/** 서버 전이 규칙의 본문. `status`와 `activity`를 함께 보내지 않고, 종료에는 존을 넣지 않는다. iOS `BearerStatusRequest.body`. */
internal fun BearerStatusRequest.toBody(): FlagStatusBody {
    val clientAt = clientAtFormat.format(clientAt)
    return when (val change = change) {
        is BearerStatusChange.Start -> FlagStatusBody(clientAt, zoneId = change.zoneId, activity = change.activity.value)

        is BearerStatusChange.Move -> FlagStatusBody(clientAt, zoneId = change.zoneId)

        is BearerStatusChange.Activity -> FlagStatusBody(clientAt, activity = change.activity.value)

        BearerStatusChange.Rest -> FlagStatusBody(clientAt, status = "resting")

        BearerStatusChange.End -> FlagStatusBody(clientAt, status = "ended")

        is BearerStatusChange.Restart ->
            FlagStatusBody(clientAt, zoneId = change.zoneId, activity = change.activity.value, restart = true)
    }
}

/** 앞뒤 공백을 지우고, 비운 소개 · 존은 null로 보낸다. iOS `BearerProfileChange.body`. */
internal fun BearerProfileChange.toBody(): FlagProfileBody {
    val trimmedBio = bio?.trim()
    return FlagProfileBody(
        name = name.trim(),
        bio = trimmedBio?.takeIf { it.isNotEmpty() },
        tags = tags.map { it.value },
        planVisible = planVisible,
        homeZoneId = homeZoneId,
        plan = plan,
    )
}

/** 프로필 본문과 같은 규칙으로 다듬고, 비운 소개 · 존 · 고르지 않은 계획은 키를 보내지 않는다. iOS `BearerRegisterRequest.body`. */
internal fun BearerRegisterRequest.toBody(): BearerRegisterBody {
    val profile = change.toBody()
    return BearerRegisterBody(
        festivalId = festivalId,
        name = profile.name,
        bio = profile.bio,
        tags = profile.tags,
        planVisible = profile.planVisible,
        homeZoneId = profile.homeZoneId,
        plan = profile.plan,
    )
}
