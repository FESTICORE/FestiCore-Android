package io.festicore.service.api

import kotlinx.serialization.Serializable

/** `GET api/v1/me/schedule/{id}` 응답 `data`. iOS `FestivalScheduleDataDTO`. */
@Serializable
data class FestivalScheduleDto(val setIds: List<String>)

/** `PUT api/v1/me/schedule/{id}` 본문. 내 일정 전체를 바꾼다. [clientAt]은 서울 오프셋 + 소수 3자리 ISO. iOS `SchedulePutRequest.Body`. */
@Serializable
data class FestivalScheduleReplaceBody(
    val setIds: List<String>,
    val clientAt: String,
)
