---
spec: notice
modules: [domain/notice, service/api, service/apiendpoint, core/utility]
---

# 공지 데이터 Spec

> 게시된 공지 목록 · 상세를 로그인 없이 서버에서 읽어 엔티티로 돌려준다.

iOS 대응: `Projects/Domain/NoticeDomain/**`, 요청 `Projects/Service/API/Sources/Notice/NoticeAPI.swift` · `Projects/Service/APIEndpoint/Sources/Notice/NoticeRequest.swift`, 날짜 `Projects/Core/FestiCoreUtility/Sources/Date/AppDateFormat.swift`(`noticeDay` · `isoDate`).
설계: `.claude/docs/specs/2026-11-23-my-tab-android-design.md`(화면 8 · 서버 계약 · PR D2).

## 개요

화면은 `NoticeUseCase`(notices · notice)만 본다. 저장소는 `NoticeRepositoryImpl`(`NoticeRequest`). 로컬 저장은 없다. 날짜 문구(`yyyy.MM.dd`, 서울 기준)는 화면이 `core/utility` `AppDateFormat.noticeDay`로 만든다.

## 동작 규칙

1. 요청은 모두 인증 없는 GET, 본문 없음, 재시도 0.
   - 목록 `GET api/v1/notices` → `data.items[{id, title, publishedAt}]`(서버가 최신 100건, 쪽 나눔 없음).
   - 상세 `GET api/v1/notices/{id}` → `data{id, title, content, publishedAt}`.
2. `publishedAt`이 ISO-8601(`yyyy-MM-ddTHH:mm:ss[.S…](Z|±HH:mm)`)이 아니면 `NoticeError.InvalidResponse`. 목록은 한 항목이라도 틀리면 전체 실패.
3. 오류: `code != "SUCCESS"` · 본문 해석 실패(필수 문자열 null 포함) → `InvalidResponse`, 그 밖(오프라인 · 시간 초과 · 404 · 5xx 등) → `Unavailable`. 취소는 그대로 올린다.

## 엣지 케이스

- 없거나 미게시 공지 · UUID가 아닌 ID는 서버가 404를 준다 → `Unavailable`.
- `noticeDay`는 UTC 15:00(서울 자정)을 넘으면 다음 날이다(`2026-11-03T15:00:00Z` → `2026.11.04`).

## 제약 / 가정

- stage 공지가 0건이라 실제 응답 본문은 보지 못했다. 백엔드 Swagger 예시와 iOS 테스트 본문(MockEngine)만 근거다.
- iOS는 네트워크 오류를 `NoticeError`로 바꾸지 않고 그대로 올린다. Android는 다른 domain처럼 `NoticeError`로 바꾼다.

## 작업 모듈

| 모듈 | 계층 | 변경 내용 |
| --- | --- | --- |
| `domain/notice` | domain | 신규. 엔티티 · 저장소 · UseCase · DTO · 매퍼 · Koin |
| `service/api` · `service/apiendpoint` | service | `NoticeApi` · `NoticeRequest`, `serviceApiEndpointModule` 등록 |
| `core/utility` | core | `date/AppDateFormat`(`noticeDay` · `isoInstant`) |
| `app` | app | `domainNoticeModule` 등록 |

## 영향 범위

| 모듈 | 계층 | 영향 이유 |
| --- | --- | --- |
| `feature/profile`(예정) | feature | 공지 목록 · 상세 화면이 `NoticeUseCase` · `AppDateFormat.noticeDay`를 쓴다 |

## 미해결 질문

(없음)

## 변경 이력

- #165 공지 목록 · 상세 데이터와 공지 날짜 형식을 옮겼다
