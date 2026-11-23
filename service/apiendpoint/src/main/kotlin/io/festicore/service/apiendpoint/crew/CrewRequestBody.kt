package io.festicore.service.apiendpoint.crew

import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlinx.serialization.Serializable

/** 크루 생성 본문. iOS는 `nickname`을 보내지 않으므로 두지 않는다. iOS `CrewCreateBody`. */
@Serializable
data class CrewCreateBody(
    val festivalId: String,
    val name: String,
)

/** 내 상태 공유 본문. [zoneId]가 없으면 서버는 존을 바꾸지 않는다. [clientAt]은 [crewClientAt]. iOS `CrewStatusBody`. */
@Serializable
data class CrewStatusBody(
    val zoneId: String? = null,
    val state: String,
    val clientAt: String,
)

/** iOS `CrewRenameBody`. */
@Serializable
data class CrewRenameBody(val name: String)

/** iOS `CrewTransferBody`. */
@Serializable
data class CrewTransferBody(val toUserId: String)

/** iOS `CrewJoinBody`. */
@Serializable
data class CrewJoinBody(val inviteCode: String)

/** iOS `CrewGatherBody`. */
@Serializable
data class CrewGatherBody(val zoneId: String)

/** [response]는 `going`(가는 중) 또는 `ack`(확인). iOS `CrewGatherResponseBody`. */
@Serializable
data class CrewGatherResponseBody(val response: String)

private val clientAtFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC)

/** 서버가 받는 `clientAt`: 밀리초가 있는 ISO 8601 UTC 문자열(`2026-11-23T01:02:03.456Z`). iOS `ISO8601DateFormatter` + `withFractionalSeconds`. */
fun crewClientAt(instant: Instant): String = clientAtFormatter.format(instant)
