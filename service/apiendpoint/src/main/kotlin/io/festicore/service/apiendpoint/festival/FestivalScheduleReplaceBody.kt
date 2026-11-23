package io.festicore.service.apiendpoint.festival

import kotlinx.serialization.Serializable

/** `PUT api/v1/me/schedule/{id}` 본문. 내 일정 전체를 바꾼다. [clientAt]은 서울 오프셋 + 소수 3자리 ISO. iOS `SchedulePutRequest.Body`. */
@Serializable
data class FestivalScheduleReplaceBody(
    val setIds: List<String>,
    val clientAt: String,
)
