---
spec: timetable-screen
modules: [feature/timetable, ui/sharedui, domain/festival, domain/auth]
---

# 타임테이블 화면 Spec

> 라이브 보드에서 고른 축제의 팩을 날짜별 무대 × 시간 격자로 보여 주고, 블록을 누르면 공연 상세 시트를 띄운다.

iOS 대응: `Projects/Feature/Timetable/Sources/{Main,SetDetail,SetList}/**`(`Main/Reducer/TimetableFeature+Schedule.swift` 동기화 포함), `UI/FestiCoreSharedUI/Sources/Sheet/FCSetSheet.swift`, `App/Sources/Navigation/MainTab/MainTabCoordinator+Sync.swift`(입력 쪽), `App/Sources/Navigation/Timetable/TimetableCoordinator{,View}.swift`(탭 흐름). 설계: `.claude/docs/specs/2026-11-22-timetable-android-design.md`(G1 · S1 · S2 · S3 · 탭 연결 #198 결과 반영). 고르기 화면은 `schedule-pick-screen.md`.

## 개요

- `TimetableViewModel`(iOS `TimetableFeature`)이 격자 · 공개 전 · 시트 상태를 모두 가진다(설계 T7). 시트 내용 · 겹침은 상태가 바뀔 때마다 ViewModel이 다시 계산해 넣는다.
- 입력은 앱 범위 공유 상태 `FestivalBoardSelectionUseCase.selection`(보드가 쓴다, 설계 T1)이고, 팩 → 날짜 변환은 `FestivalPackTimetableMapper`(`domain/timetable/.spec/timetable-mapping.md`)가 한다.
- 내 일정은 서버와 맞춘다(`FestivalScheduleUseCase` 읽기 · 저장 · 저장 알림). 계정은 `CurrentUserProvider.currentUserId`로 본다.
- 탭 흐름은 `TimetableCoordinator` · `TimetableFlow`(아래 "탭 흐름")가 맡고, app `MainTabScreen` 타임테이블 자리에 그린다(#198).
- 범위 밖: "알림 받기" 처리(A1, 푸시 P0 뒤), 공연 딥링크 · 10분 전 푸시(P0), 준비 안내 연결(PrepGuide).
- **P0 전 임시 숨김**: `TimetablePushGate.IS_PUSH_READY = false`인 동안 공연 시트 기본 도움말 "공연 10분 전에 알려 드려요"(줄 자체를 숨김)와 공개 전 카드 "알림 받기" 버튼을 숨긴다. P0 · A1이 들어오면 이 값 하나만 `true`로 바꾼다. 공개 전 카드 제목 "시간표가 공개되면 알려 드릴게요"는 iOS 문구 그대로 둔다.

## 동작 규칙

### 입력 (iOS `syncTimetable`)

| 조건                                                                                   | `contentStatus` |
| -------------------------------------------------------------------------------------- | --------------- |
| 공유 선택 없음                                                                         | `NoFestival`    |
| 문서(ID가 선택 축제와 같을 때만)가 있고 `performances` 비어 있음                       | `Unpublished`   |
| 문서 있고 공연 있음                                                                    | `Published`     |
| 문서 없음 ∧ (온라인 상세 로딩 ∨ (연결 `Loading` ∧ 상세 실패 아님 ∧ 팩 감시 실패 아님)) | `Loading`       |
| 그 밖                                                                                  | `Failed`        |

- 같은 축제면 직전 `days`를 매퍼에 넘겨 시작 시각 변경(`previousStart`)을 잇는다. 다른 축제면 빈 목록을 넘긴다.
- 팩 값(날짜 · 라인업 · 출처 · 상태 · 오프라인 안내)을 먼저 옮기고(`packSynced`, 고른 날은 같은 날짜 ID, 없으면 0), 축제가 바뀌었으면 `festivalChanged`, 같은 축제에서 메모를 뺀 날짜가 바뀌었으면 `packUpdated`(아래 "내 일정 동기화").
- 오프라인 안내: 연결 `Offline`이고 문서가 있을 때만. 팩 확인 시각이 있으면 분 = max(0, 지금 − 확인 시각) → 0분 "오프라인 · 방금 확인한 정보", 아니면 "오프라인 · {분}분 전 정보", 확인 시각이 없으면 "오프라인 · 저장된 정보".
- "다시 시도" → 공유 상태의 `requestRetry(선택 축제 ID)`(보드가 온라인 상세를 다시 읽는다).

### 시계

- 화면이 `STARTED`인 동안 1초마다 현재 시각을 읽는다(`LifecycleStartEffect` → `onClockStarted` · `onClockStopped`).
- `nowMinute` = floor((지금 − 고른 날 0시)/1분), 범위 [0, max(1440, 그날 최대 종료)) 밖이면 null. 날짜를 바꾸면 마지막 시각으로 다시 계산한다.
- 상태에는 분 단위로 자른 `clockAt`만 둔다. 같은 분이면 1초마다 상태가 바뀌지 않는다. 시트의 진행 상태도 이 값으로 다시 계산한다(iOS `TimelineView(.everyMinute)`).

### 격자 (`TimetableBoard`)

- 수치는 iOS `TimetableBoard.Metric` 그대로: 1분 2dp × 배율, 무대 > 2면 넓은 보드(열 140, 너비 56 + 140n + 8(n−1) + 16), 왼쪽 56, 시 줄 52, 겹침 막대 46 · 3, 블록 위아래 2 · 최소 8, 격자 위 8, 아래 20, 지금 줄 높이 20, 투명도 "내 일정만"의 남 공연 0.2 · 끝난 공연 0.4, 무대 점 색 라임 · 보라 · 파랑 반복.
- 시간 범위: 시작 = 최소 시작/60(없으면 12시), 끝 = max(시작+1, ceil(최대 종료/60))(없으면 24시).
- 블록 상태: 변경 → (경고 중이고 다른 내 일정과 겹치는 내 공연) 겹침 → 내 일정 → 기본.
- 겹침 띠: 경고 중일 때만, "{분}분\n겹침", 접근성 "내 일정 {분}분 겹침".
- 스크롤: 세로 `ScrollState` 하나, 넓은 보드는 가로 `ScrollState` 하나. 무대 머리글은 세로 스크롤 밖(고정)이고 가로 값만큼 옮긴다(입력은 받지 않음).
- 날짜가 바뀌면(`day.id`) `nowMinute`가 있을 때 그 시 줄 위치로 세로 스크롤한다.
- 확대: 손가락 2개 이상일 때만 배율을 곱하고 그 이벤트를 소비, 손을 모두 떼면 `clamp(zoom × 누적, 0.5, 2)`를 `onZoomChanged`로 보낸다. 날짜를 바꿔도 유지한다.
- 날짜 넘기기: 세로 전용 보드만, 한 손가락 누적 이동 30dp 이상이고 |가로| > 80 ∧ |가로| > |세로| × 1.5면 왼쪽 = 다음 날, 오른쪽 = 전날(`dayOffsetForSwipe`). 범위 밖은 무시.

### 화면 (`TimetableContent`)

- 상단 "타임테이블"(Large) + 검색 아이콘(접근성 "검색"), 날짜가 있으면 3일 이하 세그먼트 · 4일 이상 칩 줄(고른 칩을 가운데로), 오프라인 띠, 내용, 출처(탭 바 바로 위).
- 로딩: 스켈레톤 3개(r16, 높이 120, 간격 · 여백 16). 실패 · 축제 없음 · 공연 없음은 iOS 문구의 `FCEmptyCard`.
- 필터 줄: "내 일정만" 칩, 오른쪽 "내 일정 {고른 날 수}개" 또는 경고 중 "겹치는 일정 {N}개"(주황), 접근성 "내 일정 고르기", `canPickSchedule`(축제 있음 ∧ 공개 ∧ 동기화 `Ready`)일 때만 누를 수 있다. 고른 날 변경이 있으면 "시간표가 바뀌었어요 · {N}개 변경" 배너.
- 공개 전: "시간표가 공개되면 알려 드릴게요" 카드(알림 상태별 문구, 저장 중 "설정 중"이면 무동작) + 라인업 구역(`%02d` 번호 · 썸네일 자리 music20).

### 공연 상세 시트 (S03)

- 블록을 누르면 그 공연 내용으로 연다(없는 ID면 닫힘). 다른 공연을 열면 토스트를 멈춘다.
- 내용: 아티스트, 무대(없으면 "무대 미정"), `timeRangeWithDuration`, 진행 상태, 내 일정 여부, 첫 겹침, 변경 전 시각 · 변경 문구, 사진, 메모, 출처.
- 시트 상태 우선순위: 끝남 → 겹침 → 변경 → 기본. 도움말: 읽는 중 "내 일정을 불러오는 중이에요"(토글 꺼짐) / 한도 "내 일정은 최대 60개까지 저장할 수 있어요" / 기본 "공연 10분 전에 알려 드려요"(P0 전 임시 숨김 — 줄 없음).
- 토글: 동기화 `Loading`이면 무시. 빼면 한도 안내 끔. 넣을 때 끝난 공연은 거절, 이미 60개면 한도 안내만 켬. 바꾼 뒤 겹침이 있으면 격자 경고를 켠다. 넣으면 "내 일정에 추가됐어요" 토스트 2초(다시 넣으면 처음부터), 빼거나 시트를 닫거나 다른 공연 · 축제로 바뀌면 바로 숨긴다. 동기화 `Ready`일 때만 서버에 저장한다(`LocalOnly` · `Idle`은 이 기기 표시만).
- 닫으면 한도 안내를 끈다. 시트는 `FCSheet`(창 위라 탭 바까지 덮음), 토스트는 시트 위 accessory(아래에서 올라오며 0.25초).

### 공연 검색 · 변경 목록 (`TimetableSetListViewModel`)

- 검색: 앞뒤 공백을 뺀 검색어가 비었거나 아티스트 · 무대 이름에 대소문자 무시로 포함. 변경: `previousStart != null`. 공연이 없는 날은 뺀다.
- 결과에 있는 공연을 누르면 `SetSelected(id)`, 뒤로는 `Close`. 날짜는 열 때 타임테이블이 가진 값을 받고, 같은 축제 팩이 바뀌면 `onDaysChanged`로 검색어는 두고 날짜만 바꾼다(iOS `syncTimetable`).

### 내 일정 동기화 (iOS `TimetableFeature+Schedule`)

상태: `scheduleSync`(Idle · Loading · Ready · LocalOnly), `loadRequestId`, `accountGeneration`, `isSaving`, `needsSave`, `lastClientAt`, `uncertainSave(setIds · clientAt · generation)`, `isPickOpen`. 한도 60.

- 읽기: 축제가 있고 `Loading`이 아니면 `Loading` + 요청 ID +1 후 `mySchedule`. 새 읽기는 이전 읽기를 취소한다. 결과는 축제 · 요청 ID가 같고 `Loading`일 때만 받는다. 성공 = 서버 ID ∩ 팩 공연, 경고 끔, `Ready`. 실패(모든 오류) = `LocalOnly`.
- 저장: 저장 중이면 `needsSave`만 켠다. 결과를 모르는 저장이 있으면 지우고, 목록이 같으면 같은 요청(같은 `clientAt`)을 다시, 다르면 서버를 먼저 읽는다. 그 밖은 `clientAt = max(지금, 마지막 + 1ms)`, ID 정렬, 지금 계정 세대로 보낸다.
- 저장 결과는 축제 · 계정 세대가 같고 저장 중일 때만 받는다. 저장 중 · 대기 표시를 끄고: 성공 → 보낸 목록으로 저장 알림(`notifyMyScheduleSaved`), 대기가 있었으면 지금 목록으로 이어 저장 / `Stale(server)` → 서버 목록 ∩ 팩 채택 · 경고 끔 · 그 목록으로 알림(대기 버림) / `Unauthorized` → `LocalOnly` / `Rejected` → 다시 읽기 / `Unavailable`(그 밖 예외 포함) → `uncertainSave`로 보관.
- 축제 변경(`festivalChanged`): 같으면 무시. 고르기 닫기(`CloseSchedulePick`), 시트 닫기, 읽기 · 저장 취소, 내 일정 · 경고 · 한도 · 저장 상태 · `lastClientAt` · 불확실 저장 비우기(`Idle`) 후 읽기.
- 계정 변경: `currentUserId`가 이전 값이 있다가 다른 값 · null로 바뀌면 시트를 닫고 `accountChanged`(세대 +1, 요청 ID +1, 고르기 닫기, 취소 · 비우기 · 읽기). 로그인(null → 값)은 보내지 않는다(다음 진입이 읽는다). 시작 값은 ViewModel을 만들 때 읽는다.
- 팩 갱신(`packUpdated`): 시각 다시 계산, 고르기 닫기, 시트 공연이 사라지면 닫기, 팩에 없는 공연을 내 일정에서 빼고 뺐으면 경고 끄고 `Ready`면 저장.
- 탭 다시 들어옴(`onTimetableAppeared`): 축제 있음 ∧ 저장 중 아님 ∧ 고르기 닫힘일 때, 불확실 저장이 있으면 저장, 없으면 읽기.
- 다른 화면 저장(`onMySetIdsChanged`): 내 일정만 바꾼다(준비 안내 연결 때 쓴다).

### 내 일정 고르기 열기 · 결과

- "내 일정 N개": 고르기가 열려 있지 않고 `canPickSchedule`(축제 있음 ∧ 공개 ∧ `Ready` ∧ 저장 중 아님)일 때만 `isPickOpen` + `SchedulePick(TimetableRoute.SchedulePick(축제, 지금 내 일정 정렬, lastClientAt 밀리초))`.
- 결과(`onSchedulePickResult`, Coordinator가 넘긴 대기 값 · 시스템 뒤로는 `Skipped`): 열려 있지 않으면 버린다(닫힌 고르기의 늦은 결과). 닫고, `Saved`이고 같은 축제면 내 일정 = 결과 ∩ 팩(바뀌었으면 경고 다시 계산), `Ready`, 한도 · 불확실 · 저장 중 · 대기 해제, 진행 중 저장 취소, `lastClientAt = max(지금, 마지막 + 1ms)`, 저장 알림. `Skipped` · `Later`는 닫기만.
- 화면(`TimetableScreen`)은 `SchedulePick` → `onSchedulePick(route)`, `CloseSchedulePick` → `onCloseSchedulePick()`, `schedulePickResult`가 오면 ViewModel에 넘기고 `onSchedulePickResultConsumed()`.

### 탭 흐름 (`TimetableCoordinator` · `TimetableFlow`, iOS `TimetableCoordinator`)

- 스택: `[Timetable]` 위에 `SetList(mode)` · `SchedulePick`을 쌓는다. 전환은 `TimetableTransitions`(350ms `FastOutSlowInEasing`, 다른 탭과 같음). 화면은 자기 `BackHandler`를 두지 않는다.
- 검색 아이콘 · 변경 배너 → `SetList(Search | Changes)` push. 고르기나 목록이 이미 있으면 무시한다(iOS `guard destination == nil`).
- 목록에서 공연 고름 → 목록 pop + `pendingSetId` → 흐름이 `TimetableViewModel.onSetClicked(id)` 후 비운다. 목록 뒤로 · 시스템 뒤로 = pop.
- `SchedulePick` side effect → push, `CloseSchedulePick` → 결과 없이 pop. 고르기 화면 결과 → pop + `pendingPickResult`(고르기가 없으면 버림). 시스템 뒤로로 고르기를 pop하면 `Skipped`.
- 타임테이블 축제가 처음 받은 값에서 바뀌면 검색 · 변경 목록을 닫는다(iOS `sameFestival ? … : nil`).
- 탭 바: `routes.size > 1 ∨ 공연 시트 있음`이면 숨긴다(iOS `MTC:101-104`). 자리 예약 없음.
- 수명: Coordinator · `TimetableViewModel`은 app 메인 탭 범위(`HomeWithCovers`)에 두어 탭을 바꿔도 남는다. 탭을 고를 때마다 흐름이 다시 그려져 `onTimetableAppeared`를 부른다(iOS `appearing: true`).
- 보드와: 보드가 공유 선택을 쓰고, 보드 경로가 스택에서 빠지면(ViewModel `onCleared`) 자기가 쓴 값일 때만 비운다(→ `NoFestival`). "다시 시도"는 `requestRetry` → 보드가 보이지 않아도 온라인 상세를 다시 읽는다(iOS `onlineDetailOnAppear`, 실패 표시는 iOS처럼 보드가 보일 때만). 저장 알림은 `notifyMyScheduleSaved` → 보드 `savedSchedules`.

## 엣지 케이스

- 자정을 넘는 공연이 끝날 때까지 `nowMinute`가 이어진다(1440 이상).
- 공유 상태 문서 ID가 선택 축제와 다르면 문서가 없는 것으로 본다.
- 공연은 있지만 모두 무효라 날짜가 0개면 `Published` + "등록된 공연이 없어요".
- 이전 축제 · 이전 요청 · 이전 계정의 읽기 · 저장 응답은 취소되고, 늦게 와도 버린다.
- 고르기 저장 뒤에 도착한 이전 타임테이블 저장 응답은 버린다(저장 중이 아님).
- iOS는 시트 닫기를 상위(`clearAccountDisplayData`)가 하고 Android는 같은 ViewModel이 계정 입력에서 한다. 결과 화면은 같다.

## 제약 / 가정

- 접근성 식별자(`testTag`)는 iOS에 없어 두지 않는다. 접근성 라벨은 iOS 문구 그대로.
- 분석 이벤트(`screenView` · `timetableAction`)는 `service/analytics`가 없어 뺀다(설계 T10).
- 격자 머리글은 끌어서 가로로 넘기지 않는다(iOS는 끌린다).

## 작업 모듈

| 모듈                          | 계층    | 변경 내용                                                                                                     |
| ----------------------------- | ------- | ------------------------------------------------------------------------------------------------------------- |
| `feature/timetable/interface` | feature | `TimetableRoute`(`Timetable` · `SetList(mode)` · `SchedulePick`) · `TimetableSetListMode` · `SchedulePickResult` |
| `feature/timetable/sources`   | feature | `TimetableCoordinator` · `TimetableFlow` · `TimetableTransitions` · `main/`(Contract · ViewModel · Screen/Content · Board · 공개 전 · 프리뷰) · `setdetail/` · `setlist/` · `schedulepick/` · `di/` |
| `ui/sharedui`                 | ui      | `FCSetSheet` · `FCToastItems.scheduleAdded` · 프리뷰                                                          |
