---
spec: bearer-domain
modules: [domain/bearer, service/api, service/apiendpoint, app]
---

# 기수 도메인 Spec

> 기수(깃발을 든 사람)가 코드 인증 · 코드 없는 등록 · 기수 보드 · 프로필 수정 · 상태 전송 · 공동 기수 초대 · 해제와 내 깃발 · 대표 깃발 · 팔로우 목록을 로그인 세션에 묶어 서버와 주고받는다.

iOS 대응: `Projects/Domain/BearerDomain/**`(사진 `Photo/` · 대기열 `Outbox/` 제외), 요청 `Projects/Service/API/Sources/Flag/FlagAPI.swift` · `Projects/Service/APIEndpoint/Sources/Flag/FlagRequest.swift` · `FlagRequestBody.swift`.
설계: `.claude/docs/specs/2026-11-24-bearer-android-design.md`(도메인 규칙 · 서버 계약 · PR BD1 · 쟁점 B7).

## 개요

화면은 `BearerUseCase`만 본다. 저장소는 `BearerRepositoryImpl`(`FlagRequest`, 세션은 `AuthSession.currentSessionId`, 기기 ID는 `DeviceInstallationService`). 축제 이름 · 존 · 공연은 받은 팩 바이트(`PackSyncService.cachedPack`)를 기수 전용으로 따로 읽는다. 로컬 저장은 없다. 사진 업로드는 `bearer-photo.md`, 상태 대기열은 `bearer-status-outbox.md`.

## 동작 규칙

1. 모든 요청은 현재 세션에 묶은 Bearer 요청이고 재시도 0. 세션이 없으면 보내지 않고 `NotAuthenticated`. 팔로우 목록의 이름용 공개 상세 `GET flags/{id}`만 인증 없이 보낸다.
2. 요청

| 함수 | 요청 |
| --- | --- |
| `claim` | `POST bearer/claim {festivalId, code}` + `X-Device-Id`(설치 ID, 못 읽으면 `Unavailable`) |
| `register` | `POST bearer/register` + `Idempotency-Key`(`register-<uuid 소문자>`, 다시 보낼 때 같은 키) |
| `fetchBearerFlags` · `fetchFollowedFlags` | `GET me/bearer-flags` · `GET me/follows`, `festivalId`가 null이면 쿼리 생략 |
| `fetchPinnedFlag` | `GET me/pinned-flag`, `flag == null`이면 null |
| `fetchBoard` | `GET flags/{id}/bearer` |
| `updateProfile` | `PATCH flags/{id}` |
| `sendStatus` | `POST flags/{id}/status` + `Idempotency-Key`(`<uuid 소문자>`) |
| `inviteCoBearer` · `removeCoBearer` | `POST flags/{id}/co-bearers/invite` · `DELETE flags/{id}/co-bearers/{userId}`(204) |

3. 코드: 대문자로 바꾸고 `^[A-HJ-NP-Z2-9]{6}$`가 아니면 보내지 않고 `InvalidRequest`(시도 횟수를 쓰지 않게).
4. 본문
   - 상태: `clientAt`(UTC 밀리초 ISO) 필수. 시작 `{zoneId, activity}` · 이동 `{zoneId}` · 활동 `{activity}` · 쉬기 `{status:"resting"}` · 종료 `{status:"ended"}` · 재시작 `{zoneId, activity, restart:true}`. 없는 값은 키를 보내지 않는다.
   - 프로필: `name` · `bio` 앞뒤 공백 제거, 비운 `bio`와 없는 `homeZoneId`는 **null 명시**, `plan`은 null이면 키 생략.
   - 등록: 프로필과 같이 다듬되 `bio` · `homeZoneId` · `plan`은 없으면 키 생략. 태그 기본값(`["newbie"]`)은 화면이 정한다.
5. 응답 매핑
   - 날짜는 밀리초 있는 · 없는 ISO 둘 다(`AppDateFormat.isoInstant`).
   - 사진 주소: 절대 주소는 https만, 상대 경로는 `/`로 시작하고 `//`가 아니며 기준 주소가 https일 때만 붙인다. 아니면 null.
   - 프로필: 모르는 태그는 버리고 `followerCount ?: 0` · `planVisible ?: true` · `plan ?: []`.
   - 상태: 모르는 `status` · 시각 형식 오류는 `InvalidResponse`, 모르는 `activity`(`resting` 포함)는 null, `updatedBy.nickname ?: ""`.
   - 보드: `followerCount ?: 프로필 값`, `coBearers ?: []`, `hidden` · `restartUsedToday ?: false`.
   - 내 깃발: 모르는 상태는 null로 두고 깃발은 남긴다. `joinedAt` 형식 오류는 null.
   - 팔로우: 목록 뒤 깃발마다 공개 상세를 동시에 읽어 이름 · 썸네일을 붙이고 순서는 팔로우 목록. 상세가 404면 그 깃발만 빼고, 그 밖의 실패는 목록 전체 실패.
   - 팩: `name` · `zones`(id 비었거나 이름 없으면 버림) · 공연은 `timetable`이 비어 있지 않으면 그것, 아니면 `sets`. id 있음 · 시작 · 끝 해석됨 · 끝 > 시작만 시작 순. 무대 이름은 `stageName ?: stages[stageId].name`. 팩이 없거나 JSON 객체가 아니면 이름 null · 빈 목록.
6. 오류(`BearerError`): 연결 실패 · 시간 초과 → `Offline`(취소는 그대로), 해석 실패 · `code != SUCCESS` → `InvalidResponse`, 세션 불일치 → `NotAuthenticated`, 그 밖 → `Unavailable`. HTTP는 400 `InvalidRequest` · 401 `NotAuthenticated` · 403 `TERMS_REQUIRED`/`FLAG_NOT_OWNER`/그 외 → `TermsRequired`/`NotOwner`/`NotBearer` · 404 `ZONE_NOT_FOUND`/그 외 → `ZoneNotFound`/`NotFound` · 409 `FLAG_NAME_TAKEN` · `STATUS_STALE` · `FLAG_TRANSITION`·`FLAG_RESTART_REQUIRED` · `CO_BEARER_FULL` · `BEARER_LIMIT` · `FLAG_OWNER_REQUIRED`/그 외 → `NameTaken` · `Stale` · `TransitionRejected` · `CoBearerFull` · `BearerLimit` · `OwnerRequired`/`Conflict` · 413 `PhotoTooLarge` · 415 `UnsupportedPhoto` · 422 `InvalidCode(data.remainingAttempts)` · 423 `CodeLocked(data.retryAfter ?: 600)` · 429 `RateLimited` · 그 외 `Unavailable`.

## 엣지 케이스

- 코드 인증 실패 본문은 `ErrorBody`를 넓히지 않고 `FlagRequest.claim`이 돌려준 상태 · 원문에서 `data`를 직접 읽는다. `data: null`이면 남은 횟수 null.
- `BEARER_MODE_OFF`(409)는 `Conflict`, `IDEMPOTENCY_CONFLICT`(409)도 `Conflict`.

## 제약 / 가정

- 쓰기 · 인증 계약은 백엔드 코드(`flags.controller.ts`)와 iOS 요청 정의를 대조한 값이다. stage에는 인증 없는 GET만 보냈다(설계 "서버 계약").
- 대표 깃발 · 팔로우 목록은 도메인 API만 두고 화면에서는 아직 부르지 않는다(쟁점 B7).
- `makeStatusRequest`의 `clientAt`은 주입 `Clock`의 지금이다. 앱은 서버 시각 추정 시계(`BearerServerClock`, `bearer-status-outbox.md`)를 넣는다.

## 작업 모듈

| 모듈 | 계층 | 변경 내용 |
| --- | --- | --- |
| `domain/bearer` | domain | 신규. 엔티티 · 저장소 · UseCase · DTO · 매퍼 · Koin(`domainBearerModule(baseUrl)`) |
| `service/api` · `service/apiendpoint` | service | `FlagApi` 기수 경로, `FlagRequest` 기수 요청, `FlagRequestBody`(`FlagStatusBody` · `FlagProfileBody` · `BearerClaimBody` · `BearerRegisterBody`) |
| `app` | app | `domainBearerModule` 등록 |

## 영향 범위

| 모듈 | 계층 | 영향 이유 |
| --- | --- | --- |
| `feature/bearer`(예정) | feature | 기수 화면이 `BearerUseCase`를 쓴다 |
| `feature/profile`(예정) | feature | 마이 기수 카드 · 내 깃발이 `fetchBearerFlags`를 쓴다 |

## 미해결 질문

(없음)

## 변경 이력

- #214 기수 도메인과 깃발 요청을 옮겼다(사진 · 대기열 제외)
