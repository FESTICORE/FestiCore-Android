---
spec: current-user
modules: [domain/auth, feature/auth, app]
---

# 현재 사용자 Spec

> 서버가 확인한 현재 사용자 ID를 `CurrentUserProvider.currentUserId: StateFlow<String?>`로 알린다.

iOS 대응: `Projects/App/Sources/Navigation/Shared/AuthFlowState.swift`(`verifiedUserID` · `forgetVerifiedAccount`), `Session/SessionCoordinator.swift`(`handleProfileLoaded` · `finishEligible`), `Auth/AuthCoordinator.swift`(`acceptLogin`).
설계: `.claude/docs/specs/2026-11-21-festival-list-android-design.md` 결정 D3, 계정 정리 대응은 `.claude/docs/specs/2026-11-21-launch-flow-parity-audit.md`.

## 개요

사용자별 기록(최근 검색 등)의 키로 쓴다. 인터페이스는 domain/auth/interface(순수 Kotlin), 구현 `CurrentUserStore`(메모리)는 domain/auth/sources이고 Koin `single`로 `CurrentUserProvider`에 묶는다. 값은 `AppCoordinator`만 `CurrentUserStore.update`로 바꾼다.

## 동작 규칙

1. 시작 값은 null이다. 앱을 다시 시작하면 진입 프로필 재확인이 다시 채운다(저장하지 않음).
2. 채움
   - 진입 · 포그라운드 프로필 재확인 성공: 현재 세션이 요청 세션과 같을 때 `profile.user.id`. 가입 조건 충족 여부와 관계없다.
   - 로그인 완료 · 가입 완료: `LaunchLoginResult.Completed(userId, sessionId)`. 현재 세션이 있고 `sessionId`와 같을 때만 넣는다. 로그인은 계정 확인 프로필의 ID(확인이 오프라인 · 서버 오류면 로그인 응답의 ID), 가입은 가입 화면 route의 `userId`(가입 응답이 같은 사용자인지 저장소가 확인한다).
3. 비움(null)
   - 세션 만료 이벤트: 현재 세션이 없거나 만료된 세션과 같을 때.
   - 재확인 401: 현재 세션이 없거나 요청 세션과 같을 때(만료 이벤트가 있어도).
   - 로그인 직후 401(`SessionLost`, 만료 이벤트 있음 · 없음 두 갈래): 현재 세션이 없거나 그 세션과 같을 때.
   - 로그아웃 · 탈퇴: Android에 아직 없다. 옮길 때 같은 방식으로 비운다.
4. 다른 세션의 결과(그 사이 세션이 바뀜)는 값을 바꾸지 않는다. 재확인 오프라인 · 서버 오류도 값을 그대로 둔다.

## 엣지 케이스

- iOS는 재확인을 시작할 때 `verifiedUserID`를 먼저 비운다. Android는 재확인 중 값을 유지해 최근 검색이 익명 목록으로 잠깐 바뀌지 않게 한다.

## 제약 / 가정

- `CurrentUserProvider`는 Android · Koin을 모른다(`kotlinx.coroutines` `StateFlow`만).

## 작업 모듈

| 모듈 | 계층 | 변경 내용 |
| --- | --- | --- |
| `domain/auth/interface` | domain | `CurrentUserProvider` |
| `domain/auth/sources` | domain | `CurrentUserStore` · Koin 등록 |
| `feature/auth/interface` · `sources` | feature | `LaunchLoginResult.Completed`가 `userId` · `sessionId`를 싣는다 |
| `app` | app | `AppCoordinator`가 채우고 비운다 |

## 영향 범위

| 모듈 | 계층 | 영향 이유 |
| --- | --- | --- |
| `feature/festival`(F4) | feature | 최근 검색 키로 구독한다 |

## 미해결 질문

(없음)

## 변경 이력

- F2 현재 사용자 흐름 추가
