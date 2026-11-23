package io.festicore.domain.notice.sources.notice

import io.festicore.domain.notice.notice.NoticeRepository
import io.festicore.domain.notice.notice.NoticeUseCase

/** 저장소를 그대로 부른다. iOS `NoticeUseCaseImpl`. */
internal class NoticeUseCaseImpl(private val repository: NoticeRepository) : NoticeUseCase {
    override suspend fun notices() = repository.notices()

    override suspend fun notice(id: String) = repository.notice(id)
}
