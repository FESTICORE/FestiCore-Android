package io.festicore.domain.bearer.sources.model.dto

import io.festicore.domain.bearer.entity.BearerActivity
import io.festicore.domain.bearer.entity.BearerStatusChange
import io.festicore.domain.bearer.entity.BearerStatusRequest
import io.festicore.domain.bearer.sources.outbox.BearerOutboxItem
import java.time.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 파일에 남기는 대기열 한 건. 같은 멱등키와 누른 시각으로 다시 보내려고 그대로 보관한다. iOS `BearerOutboxRecordDTO`.
 * 키 이름은 iOS와 같다. `clientAt`은 epoch 밀리초다(iOS는 `Date` 기본 인코딩). [clockAdjusted]는 Android만 둔다(시계 오차로 한 번 당긴 건).
 */
@Serializable
internal data class BearerOutboxRecordDto(
    @SerialName("flagID") val flagId: String,
    @SerialName("ownerUserID") val ownerUserId: String? = null,
    val idempotencyKey: String,
    val clientAt: Long,
    val kind: String,
    @SerialName("zoneID") val zoneId: String? = null,
    val activity: String? = null,
    val clockAdjusted: Boolean = false,
)

internal fun BearerOutboxItem.toRecord(): BearerOutboxRecordDto {
    val (kind, zoneId, activity) = when (val change = request.change) {
        is BearerStatusChange.Start -> Triple("start", change.zoneId, change.activity.value)
        is BearerStatusChange.Move -> Triple("move", change.zoneId, null)
        is BearerStatusChange.Activity -> Triple("activity", null, change.activity.value)
        BearerStatusChange.Rest -> Triple("rest", null, null)
        BearerStatusChange.End -> Triple("end", null, null)
        is BearerStatusChange.Restart -> Triple("restart", change.zoneId, change.activity.value)
    }
    return BearerOutboxRecordDto(
        flagId = flagId,
        ownerUserId = ownerUserId,
        idempotencyKey = request.idempotencyKey,
        clientAt = request.clientAt.toEpochMilli(),
        kind = kind,
        zoneId = zoneId,
        activity = activity,
        clockAdjusted = clockAdjusted,
    )
}

/** 사용자를 모르는 건은 다른 계정으로 보낼 수 있어 읽지 않는다. 종류 · 존 · 활동 조합이 틀리면 null(iOS `BOR:42-55`). */
internal fun BearerOutboxRecordDto.toItem(): BearerOutboxItem? {
    val owner = ownerUserId ?: return null
    val activity = BearerActivity.from(activity)
    val change = when (kind) {
        "start" -> if (zoneId != null && activity != null) BearerStatusChange.Start(zoneId, activity) else null
        "move" -> zoneId?.let { BearerStatusChange.Move(it) }
        "activity" -> activity?.let { BearerStatusChange.Activity(it) }
        "rest" -> BearerStatusChange.Rest
        "end" -> BearerStatusChange.End
        "restart" -> if (zoneId != null && activity != null) BearerStatusChange.Restart(zoneId, activity) else null
        else -> null
    } ?: return null
    return BearerOutboxItem(
        flagId = flagId,
        ownerUserId = owner,
        request = BearerStatusRequest(change, idempotencyKey, Instant.ofEpochMilli(clientAt)),
        clockAdjusted = clockAdjusted,
    )
}
