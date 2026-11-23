---
spec: account-operations
modules: [service/api, service/apiendpoint, domain/auth]
---

# 계정 작업 Spec

> 로그아웃 · 회원 탈퇴 · 닉네임 변경을 서버에 보내고, 결과에 맞춰 로컬 세션을 정리한다.

iOS 대응: `Projects/Domain/AuthDomain/Sources/Repository/AuthRepositoryImpl.swift`(`signOut` · `withdraw` · `updateNickname`), `Projects/Service/APIEndpoint/Sources/Auth/AuthRequest.swift`(`logout` · `withdraw` · `updateNickname`).
설계: `.claude/docs/specs/2026-11-23-my-tab-android-design.md` "서버 계약", 쟁점 M2(b 단순화), PR 묶음 D1.

## 개요

마이 탭의 로그아웃 · 탈퇴 · 닉네임 변경이 쓰는 도메인 계약이다. 화면 · 앱 연결(`AccountCoordinator`)은 A1 · S3에서 붙인다.

## 동작 규칙

1. 요청(모두 Bearer, 시작한 세션에 묶음, 재시도 없음)
   - 로그아웃: `POST api/v1/auth/logout`, 본문 없음 → 200 `{success: true}`.
   - 탈퇴: `DELETE api/v1/me`, 본문 없음 → 204 빈 본문. 2xx면 본문을 보지 않는다.
   - 닉네임: `PATCH api/v1/me {nickname}` → 200 사용자.
2. `AuthRepository.signOut()`
   - 세션이 없으면 요청하지 않고 끝낸다.
   - 성공(`success == true`) · 401이면 요청한 세션만 지우고(`clearIfCurrent`) 끝낸다.
   - 그 외 실패(전송 실패 · 5xx · `success == false`)는 세션을 두고 `AuthDomainError`를 던진다. 단, 그 사이 토큰 갱신 실패로 세션이 이미 없으면 끝난 것으로 본다.
3. `AuthRepository.withdraw()`
   - 세션이 없으면 요청하지 않고 `NotAuthenticated`.
   - 성공이면 요청한 세션만 지운다. 401이면 요청한 세션만 지우고 `NotAuthenticated`.
   - 그 외 실패(전송 실패 · 409 마지막 owner · 5xx)는 세션을 두고 오류를 던진다.
4. `AuthRepository.updateNickname(nickname)`
   - 앞뒤 공백을 빼고 `AuthNicknameRule`(1~12자)을 어기면 요청하지 않고 `InvalidNickname`.
   - 세션이 없으면 `NotAuthenticated`. 401이면 요청한 세션만 지우고 `NotAuthenticated`. 성공이면 응답 사용자를 돌려준다.
5. 요청 중 다른 로그인이 새 세션을 저장했으면 이전 요청의 결과는 새 세션을 지우지 않는다.

## 엣지 케이스

- 세션 불일치로 요청 전에 막히면(`AuthSessionError`) 401과 같이 다룬다.
- 로그아웃 서버 처리 뒤 로컬 정리 전에 프로세스가 죽으면, 다음 토큰 갱신이 401로 끝나 세션 만료로 정리된다고 본다(설계 위험 절, stage 미확인).

## 제약 / 가정

- iOS의 "되돌림 차단" 디스크 기록 상태 기계(`revocationOutcomeUnknown` · `localSessionClearFailed` · 재시작 뒤 정리)는 옮기지 않는다(쟁점 M2 b). Android 세션 삭제는 DataStore 쓰기 한 번이다.
- iOS 닉네임 변경은 네트워크 오류를 그대로 던지지만, Android는 다른 요청과 같이 `AuthDomainError`로 바꾼다.

## 작업 모듈

| 모듈                  | 계층   | 변경 내용                                                                                          |
| --------------------- | ------ | -------------------------------------------------------------------------------------------------- |
| `service/api`         | 서비스 | `AuthApi.LOGOUT`, `LogoutResponse` (#163)                                                          |
| `service/apiendpoint` | 서비스 | `AuthRequest.logout` · `withdraw` · `patchNickname`, `NicknameBody` (#163)                         |
| `domain/auth`         | 도메인 | `AuthRepository.signOut` · `withdraw` · `updateNickname`, `AuthDomainError.InvalidNickname` (#163) |
