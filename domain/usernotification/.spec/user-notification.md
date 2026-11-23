---
spec: user-notification
modules: [domain/usernotification, service/api, service/auth]
---

# 내 알림 데이터 Spec

> 로그인 사용자의 알림함 목록 · 안 읽은 수 · 읽음 · 삭제를 서버에 요청하고, 보드 · 알림함이 함께 보는 배지(안 읽은 수 · 24시간 배너)를 앱 범위 저장소 하나에 둔다.

iOS 대응: `Projects/Domain/UserNotificationDomain/**`, 요청 `Projects/Service/API/Sources/UserNotification/UserNotificationAPI.swift` · `Projects/Service/APIEndpoint/Sources/UserNotification/UserNotificationRequest.swift`, 배지 `Projects/Feature/Festival/Sources/NotificationInbox/Reducer/FestivalFeature+NotificationUnreadCount.swift` · `FestivalFeature.swift`(`notificationUnreadCountLoaded` · `notificationBannerLoaded` · `notificationUnreadCountChanged`).
설계: `.claude/docs/specs/2026-11-22-live-extras-android-design.md`(화면 2 · 서버 계약 · 결정 E7 · PR N1).

## 개요

화면은 `UserNotificationUseCase`(notifications · unreadCount · markRead · delete)와 `UserNotificationBadgeUseCase`(badge `StateFlow` · refresh · refreshUnreadCount · markReadInBackground · clear)만 본다. 저장소는 `UserNotificationRepositoryImpl`(`UserNotificationApi` + `AuthSession.currentSessionId`), 배지는 `UserNotificationBadgeStore`(Koin `single`, 앱 범위 `CoroutineScope`). 푸시(FCM)와 관계없이 서버 조회만으로 동작한다. 로컬 저장은 없다.

## 동작 규칙

1. 요청(모두 Bearer, `bearerAuthorization(expectedSessionId)`, 재시도 0). 목록 · 안 읽은 수는 요청 때 현재 세션 ID로 묶고, 목록 쪽(`UserNotificationPage.sessionId`)에 그 세션을 싣는다. 읽음 · 삭제는 넘겨받은 `sessionId`(그 알림을 받은 쪽의 세션)로만 묶고 현재 세션을 다시 읽지 않는다(iOS `sessionBound`):
   - 목록 `GET api/v1/me/notifications`, 다음 쪽은 `?before={cursor}`(받은 값 그대로). 첫 쪽은 쿼리 없음.
   - 안 읽은 수 `GET api/v1/me/notifications/unread-count` → `{unreadCount}`.
   - 읽음 `POST …/read` · 삭제 `POST …/delete` 본문 `{"ids":[…]}`. 100개씩 나눠 차례로 보내고, 빈 목록이면 보내지 않는다. 2xx면 본문을 보지 않는다(204 포함).
2. 세션이 없거나 묶은 세션과 현재 세션이 다르면 인증 플러그인이 보내지 않고 `UserNotificationError.Unauthenticated`.
3. 오류: 인증 플러그인의 세션 오류 · 401 → `Unauthenticated`, `code != "SUCCESS"` · 본문 해석 실패 · 음수 안 읽은 수 · 시각 형식 오류 → `InvalidResponse`, 그 밖(오프라인 · 시간 초과 · 5xx 등) → `Unavailable`. 취소는 그대로 올린다.
4. 매핑: `createdAt`이 ISO-8601(`yyyy-MM-ddTHH:mm:ss[.S…](Z|±HH:mm)`)이 아니거나 `readAt`이 있는데 형식이 틀리면 쪽 전체 실패. `title` · `body` 등 필수 문자열이 null이면 해석 실패.
5. 배지 `refresh()`: 안 읽은 수를 읽어 반영(음수는 0, 0 이하면 배너 제거). 0보다 크면 첫 쪽에서 `readAt == null ∧ now − createdAt < 24시간`(미래 시각 포함) 중 `createdAt`이 가장 늦은 하나를 배너로. 어느 단계든 실패하면 마지막 값을 둔다.
6. `refreshUnreadCount()`: 안 읽은 수만 다시 읽어 5와 같이 반영(배너 후보는 다시 고르지 않음). 알림함 삭제 뒤 부른다.
7. `markReadInBackground(id, sessionId)`: 앱 범위 코루틴에서 `sessionId`에 묶어 읽음을 보내고 성공 · 실패와 관계없이 `refresh()`. 알림함 · 보드가 닫혀도 끝까지 간다.
8. `clear()`: 배지 0 · 배너 없음, 세대를 올려 그 전에 시작한 조회 · 읽음 뒤 재조회의 결과를 버린다. 보드가 계정이 바뀔 때 부른다.

## 엣지 케이스

- iOS 배지는 보드 State에 있어 보드마다 따로다. Android는 저장소 하나라 보드 · 알림함이 같은 값을 본다. 비로그인 보드는 값을 보이지 않는다(보드 쪽 규칙).
- 탭과 읽음 요청 실행 사이, 또는 읽음 요청 중 계정이 바뀌면 그 요청은 알림을 받은 이전 세션 ID로 묶여 있어 인증 플러그인이 보내지 않거나 실패하고, 조용히 버린다(새 계정 세션으로 나가지 않음). iOS는 effect를 취소한다.
- 앱 범위 코루틴은 프로세스가 끝나면 함께 끝난다(iOS 앱 종료와 같음). 다음 목록 조회가 서버 상태로 맞춘다.

## 제약 / 가정

- 로그인 응답(목록 · 커서 · `route` · `type` 종류 · 읽음/삭제 응답 본문)은 stage에서 보지 못했다. iOS 코드와 iOS DTO 형태의 가짜 응답(MockEngine)만 근거다.

## 작업 모듈

| 모듈 | 계층 | 변경 내용 |
| --- | --- | --- |
| `service/api` · `service/apiendpoint` | service | `UserNotificationApi`(경로) · `UserNotificationRequest` · DTO(`UserNotificationListDto` · `UserNotificationItemDto` · `UserNotificationCountDto` · `UserNotificationIdsBody`) · Koin |
| `domain/usernotification` | domain | 신규. interface: Entity · 오류 · Repository · UseCase · 배지. sources: 저장소 · 매퍼 · 배지 저장소 · Koin |

## 영향 범위

| 모듈 | 계층 | 영향 이유 |
| --- | --- | --- |
| `feature/festival` | feature | 보드 배지 · 배너, 알림함 화면 |
| `app` | app | Koin 목록에 `domainUserNotificationModule` |

## 미해결 질문

(없음)

## 변경 이력

- 내 알림 데이터 · 배지 공유 상태(PR N1) 추가
- 읽음 · 삭제를 알림을 받은 쪽의 세션에 묶는다(`UserNotificationPage.sessionId`, 요청 때 현재 세션을 다시 읽지 않음)
