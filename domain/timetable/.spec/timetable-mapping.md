---
spec: timetable-mapping
modules: [domain/timetable]
---

# 시간표 매핑 Spec

> 팩 문서를 날짜별 무대 · 공연(분 단위)과 공개 전 라인업 · 출처 문구로 바꾸고, 내 일정 겹침을 계산한다.

iOS 대응: `Projects/Domain/TimetableDomain/Interface/Sources/Entity/{TimetableDay,TimetableOverlap,TimetableSetDetail,TimetableLineupDay}.swift`, 매퍼 `Projects/App/Sources/Navigation/MainTab/FestivalPackTimetableMapper.swift`, 겹침 `Projects/Feature/Timetable/Sources/Main/Reducer/TimetableFeature.swift:124-208` · `SchedulePick/Reducer/SchedulePickFeature.swift:55-66`, 날짜 형식 `Projects/Core/FestiCoreUtility/Sources/Date/AppDateFormat.swift`.
설계: `.claude/docs/specs/2026-11-22-timetable-android-design.md`(PR D1, 결정 T3). 아래는 Android에서 참이어야 하는 것과 iOS와 다른 점만 적는다.

## 개요

`domain/timetable/interface`는 엔티티 · 시각 문구와 `FestivalPackTimetableMapper` · `TimetableOverlapCalculator` 인터페이스를 둔다(순수 Kotlin, `java.time`). 구현은 `domain/timetable/sources`에 있고 Koin `domainTimetableModule`이 묶는다. iOS는 매퍼를 App에 두지만 Android는 app에 로직을 두지 않아 domain으로 옮겼다(T3). 사진 주소는 목록과 같은 결합 규칙(`FestivalCatalogUseCase.imageUrl`)을 주입받는다.

## 동작 규칙

1. 시간대는 `Asia/Seoul`(`TimetableDay.ZONE`). 날짜 = 공연 시작 시각의 서울 0시, 날짜 순. 날짜 ID `yyyy-MM-dd`, 제목 `M/d(E)`(ko).
2. 공연은 `FestivalPackDocument.performances`(timetable → sets 대체)에서 시작 · 종료가 ISO(`FestivalIsoTime`)로 읽히고 종료 > 시작인 것만 쓴다.
3. 원본 공연 목록이 비었을 때만 `days`(`date ?: startsAt`, `yyyy-MM-dd` 엄격 또는 ISO)로 공연 없는 날을 만든다. 공연이 있지만 모두 무효면 날짜 0개다.
4. 무대 = 그날 시작순으로 처음 나온 순서, ID `stageId ?: stageName ?: "stage-unassigned"`, 이름 없으면 "무대 미정". 팩 `stages.order`는 쓰지 않는다.
5. 공연 = id · 아티스트(없으면 "출연진 미정") · 시작/종료 분(그날 0시부터, 자정을 넘기면 1440 이상, 초는 버림) · 사진 주소 · 메모.
6. `previousStart`: 직전 결과(같은 축제)에서 같은 날 · 같은 ID 공연이 있으면, 시작이 같으면 직전 `previousStart`를 잇고 다르면 직전 `start`. 직전 결과가 없으면 null. 서버 필드가 아니다.
7. 라인업: 이름이 빈 아티스트를 빼고, 남으면 한 묶음(ID = 축제 ID, 제목 "공개된 라인업"), 없으면 빈 목록.
8. 출처: 이름 없거나 비면 "", `verifiedAt`을 읽지 못하면 "출처: {이름}", 읽으면 "출처: {이름} · 확인 {MM/dd HH:mm}"(서울).
9. 시각 문구: `clock`은 `minute % 1440`을 `HH:mm`, `timeRange`는 자정을 넘기면 끝 앞에 "다음날 ", `timeRangeWithDuration` "… · N분", `startChange` "N분 미뤄짐/당겨짐". 진행 상태는 끝 이후 `Ended`, 시작 이후 `Live`, 아니면 `Upcoming(남은 분 올림)`. 상대 시각 "N분 후" · "N시간 후" · "N시간 M분 후" · "공연 중" · "종료".
10. 겹침 경고 조사: 이름을 NFC로 바꾼 뒤 끝 글자가 한글 완성형이면 받침 없음 "와" · 있음 "과", 아니면 "와".
11. 겹침 계산:
    - 격자 띠(`overlaps`): 모든 날의 내 일정을 실제 시각순으로 놓고, 둘 중 하나라도 선택한 날 공연인 쌍의 0보다 긴 공통 구간을 선택한 날 0시부터의 분(버림)으로.
    - 시트(`setOverlaps`): 공연과 0보다 길게 겹치는 다른 내 일정, 시작순. 무대 이름(없으면 "무대 미정") · `timeRange` · 겹친 분(반올림).
    - 고르기(`pickOverlap`): 공연이 골라져 있을 때 같은 날 먼저(또는 같은 시각에) 시작하는 다른 고른 공연 중 가장 많이 겹친 것(같으면 앞의 것).

## 엣지 케이스

- 종료 = 시작 · 종료 < 시작 · 시각 형식 오류 공연은 버린다.
- 메모만 바뀐 팩은 `ignoringNotes`로 같게 본다(고르기 화면을 닫지 않는 판단용).
- 자정을 넘긴 공연은 시작한 날에 속하고, 다음 날 공연과의 겹침은 두 날 모두 띠로 그린다.

## 제약 / 가정

- interface는 `domain/festival/interface`(팩 문서)만 의존한다. Koin 목록 등록은 탭 연결 PR(C1)에서 한다.
- `timetableStatus` · `timezone` 서버 필드는 읽지 않는다(iOS와 같음).

## 작업 모듈

| 모듈 | 계층 | 변경 내용 |
| --- | --- | --- |
| `domain/timetable/interface` | domain | 엔티티 · 문구 · 매퍼/겹침 인터페이스 신규 |
| `domain/timetable/sources` | domain | `FestivalPackTimetableMapperImpl` · `TimetableOverlapCalculatorImpl` · `di/` 신규 |

## 영향 범위

| 모듈 | 계층 | 영향 이유 |
| --- | --- | --- |
| `feature/timetable`(G1 이후) | feature | 격자 · 시트 · 고르기가 이 결과를 쓴다 |

## 미해결 질문

- 무대 3개 이상 · 4일 이상 · 자정 넘김 · 메모 · 시간 변경 실데이터는 stage에서 찾지 못해 테스트 자료로만 확인했다.

## 변경 이력

- 타임테이블 D1: 매핑 · 문구 · 겹침 규칙 추가
