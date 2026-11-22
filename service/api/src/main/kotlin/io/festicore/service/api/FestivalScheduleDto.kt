package io.festicore.service.api

import kotlinx.serialization.Serializable

/** `GET api/v1/me/schedule/{id}` 응답 `data`. iOS `FestivalScheduleDataDTO`. */
@Serializable
data class FestivalScheduleDto(val setIds: List<String>)
