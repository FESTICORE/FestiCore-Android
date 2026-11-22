---
spec: my-schedule
modules: [domain/festival, service/festivalcatalog, service/api, feature/festival]
---

# 내 일정 Spec

> 로그인 사용자의 축제별 내 일정(공연 ID 목록)을 서버에서 읽고 통째로 저장하며, 저장 결과와 보드 선택을 앱 범위로 공유한다.

iOS 대응: `Projects/Domain/FestivalDomain/Interface/Sources/{Entity/FestivalSchedule,Schedule/FestivalScheduleUseCaseInterface}.swift`, `Projects/Service/FestiCoreFestivalCatalog/Sources/NetworkFestivalScheduleService.swift`, `Projects/Core/FestiCoreUtility/Sources/Date/AppDateFormat.swift:57-63 · 84-86`(`isoString`), 보드 반영 `Projects/Feature/Festival/Sources/FestivalList/Reducer/FestivalFeature.swift:1187-1201`(`myScheduleSaved`), 보드 → 타임테이블 입력 `Projects/App/Sources/Navigation/MainTab/MainTabCoordinator+Sync.swift:75-123`.
설계: `.claude/docs/specs/2026-11-22-timetable-android-design.md`(PR D2, 결정 T1 · T2). 아래는 Android에서 참이어야 하는 것과 iOS와 다른 점만 적는다.

## 개요

화면은 `FestivalScheduleUseCase`(읽기 · 저장 · 저장 알림)와 `FestivalBoardSelectionUseCase`(보드 선택 공유)만 본다. 구현은 `FestivalScheduleRepositoryImpl` → `FestivalScheduleService` → `FestivalApi` 순서다. 읽기 규칙 일부는 `live-board.md`에도 있다.

## 동작 규칙

1. 요청(재시도 0, 기기에 저장하지 않음):
   - 읽기 `GET api/v1/me/schedule/{id}` Bearer → `data.setIds`.
   - 저장 `PUT api/v1/me/schedule/{id}` Bearer, 본문 `{"setIds":[…],"clientAt":"…"}` → 저장된 `data.setIds`. PUT과 충돌 뒤 다시 읽기는 저장을 시작할 때의 세션에 묶는다(그사이 세션이 바뀌면 보내지 않고 `Unauthorized`).
   - 축제 ID가 비었거나 `[A-Za-z0-9_-]` 밖이면 요청하지 않고 `Rejected`.
2. `clientAt` = 서울 오프셋 + 소수 3자리 ISO(`yyyy-MM-dd'T'HH:mm:ss.SSSXXX`, 예 `2026-11-22T15:01:26.123+09:00`). 축제마다 이전 요청보다 1ms 이상 늦게 정하는 것은 호출한 화면 몫이다.
3. 오류: 409 + 본문 `code == "STATUS_STALE"` → 서버 일정을 다시 읽어 `Stale(서버 일정)`(다시 읽기가 실패하면 그 오류), 401 · 세션 없음 → `Unauthorized`, 400 → `Rejected`, 그 밖(429 · 5xx · 다른 409 · 2xx인데 `code != "SUCCESS"` · 연결 · 해석 실패) → `Unavailable`.
4. 저장 알림(T2): `savedSchedules`는 앱 범위 하나의 흐름이다. 저장 성공 · 충돌 뒤 서버 값 채택 · 고르기 저장 때 화면이 `notifyMyScheduleSaved(festivalId, setIds)`로 보낸다(보낼지는 화면이 정한다). 구독 전 알림은 남기지 않는다.
5. 라이브 보드는 저장 알림을 받으면 같은 축제이고 로그인 중일 때만 내 일정을 그 값으로 바꾸고 `myScheduleUnavailable`을 끄며, 진행 중인 읽기는 요청 ID를 올려 버린다.
6. 보드 선택 공유(T1): 보드 ViewModel이 자기 상태가 바뀔 때마다 `FestivalBoardSelection`(선택 축제 ID · 표시 문서(ID가 같을 때만) · 온라인 상세 로딩/실패 · 팩 감시 실패 · 연결 · 받은 팩 `checkedAt`)을 앱 범위 저장소에 쓴다. 탭을 바꿔 보드 화면이 빠져도 마지막 값이 남는다. 보드는 쓰기만(`update` · `clear`), 타임테이블은 읽기만(`selection` · `requestRetry`) 한다.

## 엣지 케이스

- 충돌 응답 본문은 쓰지 않는다(iOS도 다시 읽는다).
- 비로그인 저장은 인증 플러그인이 보내지 않아 `Unauthorized`.
- 보드 저장 알림이 다른 축제 것이거나 비로그인이면 무시한다.

## 제약 / 가정

- PUT · 409 · 400 응답 본문은 stage에서 보지 못했다(쓰기 요청 금지). 테스트는 iOS DTO 형태의 가짜 응답으로 쓴다.
- 보드 선택은 메모리에만 둔다(선택 축제 ID 저장은 `FestivalLiveBoardPreferenceUseCase`). 보드 경로가 스택에서 빠질 때 `clear`를 부르는 것과 타임테이블 "다시 시도" → 보드 상세 다시 읽기 연결은 탭 연결 PR(C1)에서 한다.

## 작업 모듈

| 모듈 | 계층 | 변경 내용 |
| --- | --- | --- |
| `service/api` | service | `FestivalApi.replaceMySchedule` · `FestivalScheduleReplaceBody` |
| `service/festivalcatalog` | service | `FestivalScheduleService.replace`(409 다시 읽기, 세션 고정) · `FestivalScheduleServiceError.Stale` |
| `domain/festival` | domain | `FestivalScheduleError.Stale` · `saveMySchedule` · 저장 알림 · `FestivalBoardSelection(UseCase)` |
| `feature/festival` | feature | 보드가 선택 공유 상태를 쓰고 저장 알림을 받는다 |

## 영향 범위

| 모듈 | 계층 | 영향 이유 |
| --- | --- | --- |
| `feature/timetable`(G1 이후) | feature | 선택 공유 상태를 읽고 저장 · 알림을 보낸다 |

## 미해결 질문

- 실제 PUT 성공 · 409 본문 형태(stage 로그인 수동 확인은 연결 PR).

## 변경 이력

- 타임테이블 D2: 저장 · 충돌 · 저장 알림 · 보드 선택 공유 추가
