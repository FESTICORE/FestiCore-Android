package io.festicore.domain.usernotification.sources

import io.festicore.core.network.NetworkError
import io.festicore.domain.usernotification.UserNotificationError
import io.festicore.domain.usernotification.UserNotificationItem
import io.festicore.domain.usernotification.UserNotificationPage
import io.festicore.service.api.AuthSessionError
import io.festicore.service.api.UserNotificationItemDto
import io.festicore.service.api.UserNotificationListDto
import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException
import kotlin.coroutines.cancellation.CancellationException

/** 한 항목이라도 시각이 틀리면 쪽 전체를 실패로 본다(iOS `UserNotificationDTO+Mapper`). */
internal fun UserNotificationListDto.toDomain(): UserNotificationPage = UserNotificationPage(items.map { it.toDomain() }, nextCursor)

internal fun UserNotificationItemDto.toDomain(): UserNotificationItem {
    val created = IsoTime.parse(createdAt) ?: throw UserNotificationError.InvalidResponse()
    val read = readAt?.let { IsoTime.parse(it) ?: throw UserNotificationError.InvalidResponse() }
    return UserNotificationItem(
        id = id,
        type = type,
        festivalId = festivalId,
        title = title,
        body = body,
        createdAt = created,
        readAt = read,
        route = route,
    )
}

/** 요청 오류를 [UserNotificationError]로 바꾼다. 취소는 그대로 둔다. */
internal fun Throwable.toUserNotificationError(): Throwable = when (this) {
    is CancellationException, is UserNotificationError -> this
    is AuthSessionError -> UserNotificationError.Unauthenticated()
    is NetworkError.Http -> if (status == 401) UserNotificationError.Unauthenticated() else UserNotificationError.Unavailable()
    is NetworkError.InvalidResponse, is NetworkError.Decoding -> UserNotificationError.InvalidResponse()
    else -> UserNotificationError.Unavailable()
}

/** 서버 ISO 8601 시각. iOS `String.isoDate`(`withInternetDateTime`, 소수초 있음/없음). domain/festival `FestivalIsoTime`과 같은 규칙. */
internal object IsoTime {
    private val pattern = Regex("""^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(\.\d+)?(Z|[+-]\d{2}:\d{2})$""")

    fun parse(value: String): Instant? {
        if (!pattern.matches(value)) return null
        return try {
            OffsetDateTime.parse(value).toInstant()
        } catch (_: DateTimeParseException) {
            null
        }
    }
}
