---
spec: live-board
modules: [domain/festival, service/festivalcatalog, service/api, service/device, core/network]
---

# 라이브 보드 데이터 Spec

> 라이브 보드가 쓰는 축제 상세 · 라이브 상태 · 공개 깃발 · 공식 인스타그램 · 내 일정을 받아 도메인 값으로 바꾸고, 폴링 간격과 서버 시각 보정 규칙을 정한다.

iOS 대응: `Projects/Domain/FestivalDomain/Interface/Sources/{Entity/FestivalLiveBoardSnapshot,Entity/FestivalPackDocument,Entity/FestivalLiveBoardSchedule,Entity/FestivalOfficialNews,Entity/FestivalSchedule,LiveBoard/*}.swift`, `Sources/Model/Mapper/{FestivalLiveBoardDataDTO+,FestivalOfficialNewsDataDTO+}.swift`, `Projects/Service/FestiCoreFestivalCatalog/Sources/{NetworkFestivalLiveBoardService,NetworkFestivalOfficialNewsService,NetworkFestivalScheduleService}.swift` · `NetworkFestivalCatalogService.festivalDetail`, `Projects/Service/FestiCoreDevice/Sources/NetworkDeviceRegistrationService.swift:86-95`, 폴링 `Projects/Feature/Festival/Sources/LiveBoard/Reducer/FestivalLiveBoardFeature.swift:112 · 226-251 · 273-337`, 시각 보정 `LiveBoard/Model/FestivalLiveBoardPresentation.swift:144-149`.
설계: `.claude/docs/specs/2026-11-22-live-board-android-design.md`(서버 계약 · 캐시 · PR L1). 아래는 Android에서 참이어야 하는 것과 iOS와 다른 점만 적는다.

## 개요

화면은 `FestivalLiveBoardUseCase`(liveBoard · officialNews · festivalDetail)와 `FestivalScheduleUseCase.mySchedule`만 본다. 구현은 Repository(`FestivalLiveBoardRepositoryImpl` · `FestivalScheduleRepositoryImpl`) → service/festivalcatalog(`FestivalLiveBoardService` · `FestivalOfficialNewsService` · `FestivalScheduleService` · `FestivalCatalogService.festivalDetail`) → `FestivalApi` 순서다. DTO는 service/api에 두고 domain/sources 밖으로 내보내지 않는다. iOS는 서비스가 스냅샷까지 만들지만 Android는 의존 방향 때문에 서비스가 DTO를 돌려주고 Repository가 합친다.

## 동작 규칙

1. 요청(모두 GET, 재시도 0, 저장하지 않음):
   - 상세 `api/v1/festivals/{id}` 인증 없음. ID가 비었거나 `/` `?` `#`가 있거나 응답 `id`가 다르면 `InvalidResponse`.
   - 라이브 `.../{id}/live` `X-Device-Id` 필수. 로그인 세션이 있을 때만 Bearer(그 세션 ID에 묶음)와 `If-None-Match`(이전 ETag)를 보낸다.
   - 깃발 `.../{id}/flags` · 공식 소식 `.../{id}/instagram` 인증 없음. 내 일정 `api/v1/me/schedule/{id}` Bearer.
   - 라이브 · 공식 소식 · 내 일정의 축제 ID는 `[A-Za-z0-9_-]`, 비지 않아야 한다. 아니면 요청하지 않고 `InvalidFestivalId`(내 일정은 `Rejected`).
2. 설치 기기 ID: 처음 쓸 때 소문자 UUID를 만들어 `SecureStorage` 키 `deviceID`에 저장하고 이후 같은 값을 쓴다(동시 호출에도 한 번만 만든다). 서버 등록은 하지 않는다.
3. 라이브 응답 해석(본문을 상태 코드보다 먼저 본다): 본문 `code == "FESTIVAL_NOT_FOUND"` → 미공개(`FestivalNotFound`, 그 축제 ETag 캐시 삭제) · 200 → 새 값(응답 `festivalId`가 요청과 다르면 `InvalidResponse`) · 304 → 세션 캐시의 이전 값(세션이 없거나 캐시가 없으면 `InvalidResponse`) · 429 → `RateLimited` · 그 밖 → `Unavailable`(401 포함, 토큰 갱신은 인증 플러그인).
4. 429: `Retry-After` 초 값(없거나 숫자가 아니면 35초, 1초 미만이면 1초) 동안 그 축제는 요청하지 않고 남은 시간으로 `RateLimited`를 던진다. 성공하면 대기를 지운다.
5. 세션 캐시: ETag · 이전 응답 · 429 대기는 메모리에만, 로그인 세션 ID별로 둔다. 세션 ID가 바뀌면 모두 비운다. 응답을 받은 뒤 세션이 바뀌었으면 결과를 버리고 `InvalidResponse`. 304로 이전 응답을 쓸 때도 ETag는 처음 받은 값을 유지한다.
6. `receivedAt`은 라이브 응답을 받은 시각(깃발 프로필 요청 전). 304여도 새로 기록한다.
7. 깃발 병합: `serverAt` 또는 활성 깃발의 `updatedAt`을 해석하지 못하면 `InvalidResponse`. 프로필은 같은 ID 중 첫 것. 이름 = 프로필 이름 ?: 깃발 ID, 썸네일 = 목록과 같은 이미지 주소 결합, 태그 · 계획 없으면 빈 목록. 팔로우 = 활성 깃발 그대로, 없으면 프로필로 만든 상태 `unknown` 깃발. 공개 목록 = 프로필 순서(중복 제거)에 활성 상태를 합치고, 빠진 깃발은 활성 목록이 50개 미만이면 `scheduled`, 아니면 `unknown`.
8. 크루: 멤버 시각은 틀리면 null. 모여 요청은 `at`이 틀리면 없음, 응답 줄은 모르는 응답 값(`going` · `ack` 밖)이나 틀린 날짜만 버린다. 요약이 없으면 응답 줄로 센다.
9. 공식 소식: `available == false`면 `Unavailable`(계정 · 게시물 없음). 게시물 링크 · 계정 주소는 https + `instagram.com` · `www.instagram.com`만, 이미지는 https 절대 주소만. 링크나 `postedAt`이 틀린 게시물은 버리고, 캡션은 trim해 비면 null. 섹션은 받을 수 있고 게시물이 있을 때만 보인다(`isVisible`). 다른 축제 응답 · 해석 실패는 `InvalidResponse`.
10. 팩 문서: `eventPeriod` · `source`(각 필드) · 공연 `note`는 형식이 틀리면 null, `zones` · `ticketLinks`는 손상 원소만 버린다. `days` · `artists` · `timetable` · `sets` · `stages` 원소가 틀리면 문서 전체 해석 실패. 공연 목록은 `timetable`이 비지 않으면 그것, 아니면 `sets`에 첫 무대 정의의 이름을 채운다. 메모는 trim해 비면 null.
11. 일정 계산(`FestivalLiveBoardSchedule`): 시작 시각이 `^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(\.\d+)?(Z|[+-]\d{2}:\d{2})$`, 아티스트가 공백이 아니고, 무대(ID ?: 이름)가 있고, 종료가 있으면 시작보다 늦은 공연만 쓴다. `entries`는 시작 → ID 순. `stages`는 무대가 팩에 처음 나온 순서, 시작 포함 · 종료 제외로 current(종료를 아는 공연만) · previous · next를 정하고 current도 next도 없는 무대는 뺀다.
12. 폴링 규칙(`FestivalLiveBoardUseCase` companion): 라이브 35초, 429면 `max(35초, retryAfter)`, 공식 소식 300초, 화면 시각 30초마다 다시 계산.
13. 서버 시각 보정: 새 스냅샷의 `serverAt`이 이전과 다르거나 기준점이 없으면 기준점 = 기기 지금, 같으면 유지. 화면 시각 = `serverAt + (기기 지금 − 기준점)`, 둘 중 하나라도 없으면 기기 지금.
14. 오류(`FestivalLiveBoardError`): 미공개 `FestivalNotFound`(상세의 `Http` 본문 `FESTIVAL_NOT_FOUND` 포함), `InvalidFestivalId`, `InvalidResponse`(해석 실패 · `code != SUCCESS` 포함), `RateLimited(retryAfter)`, 그 밖(연결 · 시간 초과 · 5xx · 401) `Unavailable`. 내 일정(`FestivalScheduleError`): 401 · 세션 없음 `Unauthorized`, 400 · 잘못된 ID `Rejected`, 나머지 `Unavailable`. 취소는 그대로 던진다.

## 엣지 케이스

- 비로그인 304: 서버는 비로그인에도 304를 줄 수 있지만 ETag를 보내지 않으므로 오지 않는다. 오면 `InvalidResponse`(iOS와 같음).
- 200 본문이 `FESTIVAL_NOT_FOUND`여도 미공개로 본다.
- 깃발 프로필 요청 실패: 라이브 조회 전체 실패(해석 실패는 `InvalidResponse`, 그 밖은 `Unavailable`). 이때 ETag 캐시는 갱신하지 않는다.
- 저장한 기기 ID를 복호화하지 못하면 `SecureStorage`가 지우고 새 UUID를 만든다.

## 제약 / 가정

- `/live`의 `setOverrides` · `/flags`의 `photoUrl` · `bio` · `planVisible` · 상세의 `timezone` · `packVersion` 등은 iOS가 읽지 않아 받지 않는다(`ignoreUnknownKeys`). 귀가용 `directions`는 `lastShuttleAt` · `lastTrainAt` · `shuttle.stopName` · `transit.stationName` · `transit.walkMinutes`만 `FestivalPackDocument.Directions`로 받고, 형식이 틀리면 통째로 null이다(iOS `FestivalPackDetailDTO` `try?`). 보드는 받은 팩의 값만 쓴다.
- iOS `FestivalLiveBoardError.deviceNotRegistered`는 깃발 화면에서만 쓰여 옮기지 않았다. 대신 연결 · 서버 실패를 `Unavailable`로 묶었다.
- 내 일정 저장(PUT)과 `stale` 오류는 Timetable 문서에서 옮긴다.
- 로그인 응답(`me` · 크루 · 팔로우 · 기수 · 내 일정), 429 `Retry-After` 실제 값, 게시물 있는 인스타그램 응답은 stage에서 보지 못했다. 테스트는 iOS DTO 형태의 가짜 값이다.

## 작업 모듈

| 모듈 | 계층 | 변경 내용 |
| --- | --- | --- |
| `core/network` | core | `festiCoreExchange`(상태 코드 · 헤더를 직접 다루는 요청) |
| `service/api` | service | 경로(상세 · live · flags · instagram · me/schedule), DTO(`FestivalPackDocumentDto` · `FestivalLiveBoardDto` · `FestivalFlagsDto` · `FestivalOfficialNewsDto` · `FestivalScheduleDto`), `FestivalApi` 요청, `FestivalLiveResponse` |
| `service/device` | service | 신규. `DeviceInstallationService`(설치 ID) |
| `service/festivalcatalog` | service | `FestivalLiveBoardService` · `FestivalOfficialNewsService` · `FestivalScheduleService` · `festivalDetail`, `FestivalLiveBoardServiceError` |
| `domain/festival` | domain | 엔티티 · 오류 · `FestivalLiveBoardSchedule` · `FestivalIsoTime`, Repository · UseCase, 매퍼 · 오류 매핑, Koin |
| `app` | app | `serviceDeviceModule` 목록 연결 |

## 영향 범위

| 모듈 | 계층 | 영향 이유 |
| --- | --- | --- |
| `feature/festival` | feature | L4 보드 ViewModel이 이 UseCase · 규칙을 쓴다(이번 PR은 화면 변경 없음) |
| `service/festivalcatalog` | service | `service/auth` · `service/device`를 새로 의존 |

## 미해결 질문

- 서버가 나중에 등록된 기기만 `/live`를 받으면 `DEVICE_NOT_REGISTERED`가 생길 수 있다(설계 위험 4). 그때 등록 흐름을 정한다.

## 변경 이력

- 라이브 보드 데이터 계층(PR L1) 추가
