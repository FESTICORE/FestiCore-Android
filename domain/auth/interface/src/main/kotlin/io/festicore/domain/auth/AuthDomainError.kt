package io.festicore.domain.auth

/** 인증 도메인 오류. 화면 문구는 이 종류로 고른다. iOS `AuthDomainError`(Apple · 로그아웃 관련 케이스 제외). */
sealed class AuthDomainError : Exception() {
    /** 사용자가 Google 계정 선택을 닫았다. 문구 없음. */
    class UserCancelled : AuthDomainError()

    /** Google 로그인이 이미 진행 중이다. 문구 없음. */
    class RequestInProgress : AuthDomainError()

    class Offline : AuthDomainError()

    /** 세션이 없거나 갱신까지 실패했다(401). */
    class NotAuthenticated : AuthDomainError()

    /** 가입 중 약관 버전이 바뀌었다(`422 TERMS_VERSION_OUTDATED`). */
    class TermsVersionOutdated : AuthDomainError()

    /** 로그인 토큰을 기기에 저장하지 못했다. */
    class CredentialSaveFailed : AuthDomainError()

    /** 그 외 실패(서버 오류 · 잘못된 응답 · Google 로그인 실패). */
    class Unavailable : AuthDomainError()
}
