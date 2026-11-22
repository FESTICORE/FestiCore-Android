---
spec: schedule-pick-screen
modules: [feature/timetable, domain/festival]
---

# 내 일정 고르기 화면 Spec

> S15-1. 고른 날 공연을 골라 서버 내 일정 전체로 저장하고, 결과(저장 · 건너뛰기 · 나중에)를 부르는 쪽에 돌려준다.

iOS 대응: `Projects/Feature/Timetable/Sources/SchedulePick/{Reducer/SchedulePickFeature.swift,View/SchedulePickView.swift}`, `Interface/Sources/SchedulePickDelegate.swift`. 설계: `.claude/docs/specs/2026-11-22-timetable-android-design.md`(화면 4 · S3 · 결정 T8 · SchedulePick 결과 공통 계약).

## 개요

- 경로 `TimetableRoute.SchedulePick(festivalId, selectedSetIds, previousClientAtEpochMillis)`(최대 60 ID). 날짜는 경로에 싣지 않고 보드 공유 상태(`FestivalBoardSelectionUseCase.selection`)의 같은 축제 문서를 `FestivalPackTimetableMapper.days`로 다시 옮긴다(문서가 없거나 다른 축제면 빈 목록).
- `SchedulePickScreen(route, onResult)`가 `SchedulePickResult`(`Saved(festivalId, setIds)` · `Skipped` · `Later`)를 돌려준다. 타임테이블 경로는 Coordinator가 pop 후 타임테이블에 넘기고(`timetable-screen.md`), 준비 안내(PrepGuide)는 같은 화면을 라이브 스택에서 쓴다(연결은 PrepGuide PR).
- 이 화면은 저장 알림(`notifyMyScheduleSaved`)을 보내지 않는다. 부르는 쪽이 결과로 보낸다(iOS 타임테이블 `scheduleSaved` · `prepScheduleSaved`).

## 동작 규칙

- 날짜(`onDayTapped`): 범위 안만 바꾼다.
- 공연(`onSetTapped`): 저장 중이면 무시. 골랐으면 빼고, 아니면 넣는다. 이미 60개면 넣지 않고 "내 일정은 60개까지 고를 수 있어요."를 띄운다. 넣거나 빼면 안내를 지운다.
- 다음(`onNextTapped`): `canSubmit`(저장 중 아님 ∧ 1개 이상)일 때만. 저장 중 표시 · 안내 지움 후 `saveMySchedule(축제, 고른 ID 정렬, clientAt)`. `clientAt = max(지금, 경로의 이전 시각 + 1ms)`(없으면 지금). 새 저장은 이전 저장을 취소한다.
- 성공: 고른 공연 = 서버 목록, `Saved(축제, 그 목록)`. `Stale(server)`: 고른 공연 = 서버 목록 + "다른 기기에서 바뀐 일정을 불러왔어요. 확인하고 다시 눌러 주세요.". 그 밖(`Unauthorized` · `Rejected` · `Unavailable` · 예외): "내 일정을 저장하지 못했어요. 다시 시도해 주세요.". 실패하면 결과를 돌려주지 않는다(화면이 남는다).
- 건너뛰기 · 나중에: 저장 중이면 무시, 아니면 `Skipped` · `Later`.
- 겹침 안내: 고른 공연이면, 같은 날 먼저(또는 같은 시각) 시작한 다른 고른 공연과 겹친 분이 가장 큰 것(`TimetableOverlapCalculator.pickOverlap`) → "{아티스트}{와/과} {분}분 겹쳐요". 조사는 마지막 글자가 받침 없는 한글이면 "와", 그 밖(한글이 아님 포함) "과"(설계 T11, 시트 경고와 다름).

## 화면 (`SchedulePickContent`)

- 배경 `backgroundElevated`, 위 상태 표시줄 여백. `FCStepHeader(step = 1)` + "나중에 할게요" → `Later`.
- 세로 스크롤, 여백 16 · 간격 20: 머리("Step 1. 내 일정 고르기" `baseCaption1Medium` `labelAssistive` / "보고 싶은 무대를 골라 주세요" `baseTitle2Bold` `labelNormal` 머리글 / "고른 무대는 시작 10분 전에 알려 드려요.\n나중에 타임테이블에서 언제든 바꿀 수 있어요." `baseSubheadlineRegular` `labelNeutral`, 간격 8), `FCSegmented`(날짜 수 제한 없음), 목록.
- 목록(간격 8, 오른쪽 정렬): "{N}개 선택"(`baseFootnoteBold`), 고른 날 공연 시작순 `FCSetSelectRow(artist, 무대(없으면 "무대 미정"), HH:mm, HH:mm, isSelected, overlapText)`, 행 사이 1 `lineNormal`, 묶음 `r16` 클립 + 1 `lineNormal` 테두리. 시각은 자정을 넘기면 다음 날 시각.
- 빈 날: "이 날은 아직 공연 시간이 없어요."(`baseSubheadlineRegular` `labelAssistive`, 가운데, 위아래 24). 안내: `baseCaption1Regular` `statusNegative`, 왼쪽.
- 아래 바: 위 1 `lineNormal`, `FCButton("건너뛰기", Ghost)` + `FCButton(저장 중 "저장하는 중" / "다음 · {N}개 선택", 가득, isEnabled = canSubmit)`, 간격 8, 좌우 16 · 위 12 · 아래 8, 배경 `materialGlass`(아래 내비게이션 바 영역까지).

## 엣지 케이스

- 공유 상태에 같은 축제 문서가 없으면 날짜가 없어 빈 날 안내만 보인다.
- 경로의 이전 시각은 밀리초로 자른 값이라, 다음 시각은 실제 이전 요청보다 항상 늦다.

## 제약 / 가정

- iOS 아래 바의 흐림(`ultraThinMaterial`)은 Compose 표준 흐림이 없어 `materialGlass` 단색만 쓴다(설계 적응 표). 스크롤 내용은 바 아래로 지나가지 않고 바 위에서 끝난다.
- 접근성 식별자(`testTag`)는 iOS에 없어 두지 않는다.

## 작업 모듈

| 모듈                          | 계층    | 변경 내용                                                                          |
| ----------------------------- | ------- | ---------------------------------------------------------------------------------- |
| `feature/timetable/interface` | feature | `TimetableRoute.SchedulePick` · `SchedulePickResult`                               |
| `feature/timetable/sources`   | feature | `schedulepick/`(Contract · ViewModel · Screen/Content) · 프리뷰 · `di/` 등록       |
