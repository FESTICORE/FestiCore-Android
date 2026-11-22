---
spec: timetable-screen
modules: [feature/timetable, ui/sharedui]
---

# 타임테이블 화면 Spec

> 라이브 보드에서 고른 축제의 팩을 날짜별 무대 × 시간 격자로 보여 주고, 블록을 누르면 공연 상세 시트를 띄운다.

iOS 대응: `Projects/Feature/Timetable/Sources/{Main,SetDetail,SetList}/**`, `UI/FestiCoreSharedUI/Sources/Sheet/FCSetSheet.swift`, `App/Sources/Navigation/MainTab/MainTabCoordinator+Sync.swift`(입력 쪽). 설계: `.claude/docs/specs/2026-11-22-timetable-android-design.md`(G1 · S1).

## 개요

- `TimetableViewModel`(iOS `TimetableFeature`)이 격자 · 공개 전 · 시트 상태를 모두 가진다(설계 T7). 시트 내용 · 겹침은 상태가 바뀔 때마다 ViewModel이 다시 계산해 넣는다.
- 입력은 앱 범위 공유 상태 `FestivalBoardSelectionUseCase.selection`(보드가 쓴다, 설계 T1)이고, 팩 → 날짜 변환은 `FestivalPackTimetableMapper`(`domain/timetable/.spec/timetable-mapping.md`)가 한다.
- 이번 범위 밖: 내 일정 서버 읽기 · 저장(S2), 내 일정 고르기(S3), "알림 받기" 처리(A1), Coordinator · 탭 연결 · app Koin 등록(C1).

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
- 팩 값(날짜 · 라인업 · 출처 · 상태 · 오프라인 안내)을 먼저 옮기고(`packSynced`, 고른 날은 같은 날짜 ID, 없으면 0), 축제가 바뀌었으면 `festivalChanged`(시트 닫기, 내 일정 · 경고 · 한도 비우기, 동기화 `Idle`), 같은 축제에서 메모를 뺀 날짜가 바뀌었으면 `packUpdated`(시각 다시 계산, 팩에 없는 공연을 내 일정 · 시트에서 뺌, 뺐으면 경고 끔).
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
- 시트 상태 우선순위: 끝남 → 겹침 → 변경 → 기본. 도움말: 읽는 중 "내 일정을 불러오는 중이에요"(토글 꺼짐) / 한도 "내 일정은 최대 60개까지 저장할 수 있어요" / 기본 "공연 10분 전에 알려 드려요".
- 토글: 동기화 `Loading`이면 무시. 빼면 한도 안내 끔. 넣을 때 끝난 공연은 거절, 이미 60개면 한도 안내만 켬. 바꾼 뒤 겹침이 있으면 격자 경고를 켠다. 넣으면 "내 일정에 추가됐어요" 토스트 2초(다시 넣으면 처음부터), 빼거나 시트를 닫거나 다른 공연 · 축제로 바뀌면 바로 숨긴다. 서버 저장은 S2.
- 닫으면 한도 안내를 끈다. 시트는 `FCSheet`(창 위라 탭 바까지 덮음), 토스트는 시트 위 accessory(아래에서 올라오며 0.25초).

### 공연 검색 · 변경 목록 (`TimetableSetListViewModel`)

- 검색: 앞뒤 공백을 뺀 검색어가 비었거나 아티스트 · 무대 이름에 대소문자 무시로 포함. 변경: `previousStart != null`. 공연이 없는 날은 뺀다.
- 결과에 있는 공연을 누르면 `SetSelected(id)`, 뒤로는 `Close`. 날짜는 열 때 타임테이블이 가진 값을 받는다.

## 엣지 케이스

- 자정을 넘는 공연이 끝날 때까지 `nowMinute`가 이어진다(1440 이상).
- 공유 상태 문서 ID가 선택 축제와 다르면 문서가 없는 것으로 본다.
- 공연은 있지만 모두 무효라 날짜가 0개면 `Published` + "등록된 공연이 없어요".

## 제약 / 가정

- 접근성 식별자(`testTag`)는 iOS에 없어 두지 않는다. 접근성 라벨은 iOS 문구 그대로.
- 분석 이벤트(`screenView` · `timetableAction`)는 `service/analytics`가 없어 뺀다(설계 T10).
- 격자 머리글은 끌어서 가로로 넘기지 않는다(iOS는 끌린다).

## 작업 모듈

| 모듈                          | 계층    | 변경 내용                                                                                                     |
| ----------------------------- | ------- | ------------------------------------------------------------------------------------------------------------- |
| `feature/timetable/interface` | feature | `TimetableRoute`(`Timetable` · `SetList(mode)`) · `TimetableSetListMode`                                      |
| `feature/timetable/sources`   | feature | `main/`(Contract · ViewModel · Screen/Content · Board · 공개 전 · 프리뷰) · `setdetail/` · `setlist/` · `di/` |
| `ui/sharedui`                 | ui      | `FCSetSheet` · `FCToastItems.scheduleAdded` · 프리뷰                                                          |
