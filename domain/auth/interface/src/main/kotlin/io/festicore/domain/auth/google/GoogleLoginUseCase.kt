package io.festicore.domain.auth.google

import io.festicore.domain.auth.entity.AuthDomainError
import io.festicore.domain.auth.entity.AuthLoginResult

/** Google 계정 선택 → 서버 로그인 → 세션 저장. 실패는 [AuthDomainError]. iOS `OAuthUseCaseInterface`(Google만). */
interface GoogleLoginUseCase {
    suspend operator fun invoke(): AuthLoginResult
}
