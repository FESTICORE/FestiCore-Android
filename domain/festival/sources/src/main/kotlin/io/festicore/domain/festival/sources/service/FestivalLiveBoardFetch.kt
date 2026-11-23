package io.festicore.domain.festival.sources.service

import io.festicore.domain.festival.sources.model.dto.FestivalFlagProfileDto
import io.festicore.domain.festival.sources.model.dto.FestivalLiveBoardDto
import java.time.Instant

/** 라이브 상태 한 번 조회한 결과. [receivedAt]은 라이브 응답을 받은 시각(깃발 프로필 요청 전). */
data class FestivalLiveBoardFetch(
    val board: FestivalLiveBoardDto,
    val flags: List<FestivalFlagProfileDto>,
    val receivedAt: Instant,
)
