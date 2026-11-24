---
spec: bearer-status-outbox
modules: [domain/bearer, service/apiendpoint, core/network, app]
---

# 기수 상태 대기열 Spec

> 기수가 누른 상태 변경을 파일에 남겨 두고 누른 순서대로 같은 멱등키로 보낸다. 누른 사용자의 세션으로만 보내고, 오래된 변경과 기기 시계 오차를 정리한다.

iOS 대응: `Projects/Domain/BearerDomain/Sources/Outbox/BearerStatusOutboxImpl.swift` · `Sources/Model/DTO/BearerOutboxRecordDTO.swift` · `Interface/Sources/Entity/BearerOutboxEvent.swift` · `Interface/Sources/Bearer/BearerStatusOutboxInterface.swift`.
설계: `.claude/docs/specs/2026-11-24-bearer-android-design.md`("상태 대기열" 절 · PR BD3 · 쟁점 B3 · B4 · 검토 반영 R1~R4).

## 개요

보드는 `BearerUseCase.makeStatusRequest` → `enqueueStatus(flagId, ownerUserId, request)`로 넣고 `statusEvents(flagId)` · `pendingStatus(flagId)`로 보며 `retryPendingStatus()`로 다시 보낸다. 구현은 `domain/bearer/sources` `outbox/BearerStatusOutboxImpl`(Koin `single`, 앱 범위 `CoroutineScope`, 상태 보호 `Mutex`). 재개 시점은 `BearerStatusOutboxResumer`(Koin `createdAtStart`)가 현재 사용자(`CurrentUserProvider`)와 연결 복구(app `networkReconnects`)를 보고 정한다.

## 동작 규칙

1. 넣기: 누른 시각 순서로 끼워 넣되 한 번 보내 본 건(결과 모름 포함) 앞에는 넣지 않는다. 넣을 때마다 저장하고 `Pending` 알림 · 멈춤 해제 · 작업 시작.
2. 보내기: 한 건씩. 세션 S → 현재 사용자 U → 세션 재확인이 맞을 때만 주인이 U인 맨 앞 건을 `expectedSessionId = S`로 보낸다. 사용자 · 세션이 없으면 `NeedsLogin` 멈춤, 세션이 바뀌었으면 다시 고른다.
   - 2xx: 지우고 `Pending` · `Sent`. 그 사이 세션이 S가 아니면 알리지 않는다(지우기는 한다).
   - 취소 · 결과 모름: 지우지 않고 같은 멱등키 · `clientAt`으로 남긴다.
   - `Offline` → `Offline` 알림 · 5초 뒤, `RateLimited` → 15초 뒤, `NotAuthenticated` → `NeedsLogin` 멈춤, `TermsRequired` → `GaveUp` 멈춤.
   - 그 외 `BearerError`(`Unavailable` · `InvalidResponse` 제외, 409 `STATUS_STALE` 포함) → 그 깃발 · 그 주인 건을 모두 버리고 `Rejected`.
   - 나머지 → 2 · 4 · 8 · 16 · 32초 뒤, 5번 넘으면 `GaveUp` 멈춤.
3. 계정 격리: 다른 계정 건은 보내지 않고 남긴다(주인이 다시 로그인하면 보낸다). `pending` · `Pending` 알림은 현재 사용자 건만. 현재 사용자가 바뀌면 작업을 취소한다. 탈퇴가 성공하면 `AccountCoordinator`가 그 사용자 건을 지운다.
4. 만료: 서버 시각 추정값 기준 서울 날짜가 다르거나 24시간이 지난 건은 파일을 읽을 때 · 한 건을 고를 때 버린다. 현재 사용자 건이면 `Pending` · `Rejected(Stale)`로 알린다.
5. 시계 오차: `BearerServerClock` 오프셋 = 보드 · 상태 응답 `Date` − 받은 기기 시각(메모리, 모르면 0). `makeStatusRequest`의 `clientAt`과 만료가 이 시각을 쓴다. 상태 전송 400이면서 건 `clientAt` > 서버 시각 + 5분이면 그 깃발 건(결과 모르는 다른 시도 건 제외)의 `clientAt`을 같은 만큼 당겨 저장하고 같은 멱등키로 다시 보낸다. 한 건에 한 번만, 또 400이면 `Rejected`. 그 외 400은 `Rejected`.
6. 재개: 보드 표시 · 다시 보내기(`retryPendingStatus`) · 현재 사용자가 생길 때(앱 시작 뒤 확인 · 로그인)는 멈춤을 풀고 재개, 연결 복구는 사용자가 있고 멈추지 않았을 때만.
7. 파일: `filesDir/festicore/bearer-status-outbox.json`, `AtomicFile`로 통째 쓰기. 원소 `{flagID, ownerUserID, idempotencyKey, clientAt(epoch 밀리초), kind, zoneID, activity, clockAdjusted}`. 주인이 없거나 종류 조합이 틀린 원소는 읽지 않는다. 읽기 · 쓰기 실패는 오류 종류만 로그하고 메모리 대기열로 계속한다.

## 엣지 케이스

- 보드 밖에는 표시가 없다(화면 연결은 BS4).
- 연결 복구 콜백은 등록 직후 이미 연결돼 있으면 한 번 온다.
- 서버 시각은 1초 정밀도라 5분 허용치에 충분하다. `Date` 헤더가 없으면 기존 오프셋을 쓴다.

## 작업 모듈

`domain/bearer/{interface,sources}`(`BearerStatusOutbox` · `BearerOutboxEvent` · `outbox/` · `model/dto/BearerOutboxRecordDto`) · `service/apiendpoint`(`FlagRequest.status` · `bearer`의 `onServerDate`) · `core/network/interface`(`festiCoreRequest(onResponse, block)`) · `app`(`networkReconnects` · `AccountCoordinator` 탈퇴 정리 · Koin 연결).

## 테스트

- iOS 같은 이름(`BearerStatusOutboxTest`): `ordersConcurrentEnqueues` · `retriesOffline` · `retriesRateLimited` · `keepsAttemptedItemFirst` · `keepsQueueOnUnauthorized` · `backsOffThenGivesUp` · `keepsQueueOnTermsRequired` · `dropsRejected` · `keepsOtherAccountUntilOwnerReturns`(iOS `dropsOtherAccount`) · `persists`.
- Android 추가: 계정 · 세션 6건, 만료 3건, 시계 오차 6건, 재개 3건(설계 BD3 행의 이름). 대기 시간은 가상 시간이다.

## 변경 이력

- #218 상태 대기열을 옮겼다
