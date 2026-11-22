# 타임테이블 탭(Timetable · SetDetail · SetList · SchedulePick) Android 이식 설계

- 날짜: 2026-11-22
- 이슈: 미정(구현 이슈를 만들 때 적는다)
- 상태: 제안
- 기준: Android `develop` dd3a6fb, iOS `09eda3b8`(로컬 `../FestiCore-iOS`, 읽기 전용). 아래 iOS 행 번호는 모두 `09eda3b8`에서 열어 확인했다.
- 범위: `feature/timetable`(신규) · `domain/timetable`(신규) · `domain/festival` · `service/api` · `service/festivalcatalog` · `ui/designkit`(또는 `ui/sharedui`) · `feature/festival`(보드가 선택 축제를 공유) · `app`(`MainTabScreen` 타임테이블 자리)

## 목표

`MainTabScreen`의 타임테이블 탭 빈 자리(`TabRoot`, `app/.../navigation/MainTabScreen.kt:47 · 57-65`)를 iOS 타임테이블 탭 전체로 채운다.

1. **Timetable(S02)** — 라이브 보드에서 고른 축제의 팩을 날짜별 무대 × 시간 격자로 보여 준다. 공개 전이면 라인업, 축제가 없거나 팩을 못 읽으면 안내.
2. **SetDetail(S03 공연 상세 시트)** — 블록을 누르면 아래 시트. "내 일정에 넣기" 토글 · 겹침 경고 · 변경 안내 · 메모 · 출처.
3. **SetList(검색 · 시간표 변경)** — 상단 검색 아이콘, 변경 배너에서 push.
4. **SchedulePick(S15-1 내 일정 고르기)** — "내 일정 N개" 버튼에서 push. 라이브 탭 준비 안내(PrepGuide) 1단계도 이 화면을 쓴다(`iOS/App/Sources/Navigation/Festival/FestivalCoordinator.swift:329-333`, `FestivalCoordinatorView.swift:33-34`).
5. **내 일정 서버 동기화** — 읽기(이미 있음) + 저장(PUT, 신규), 겹침 계산, 60개 한도.

시각(수치 · 문구 · 토큰 · 에셋 · 접근성 라벨)은 iOS View와 같게, 플랫폼 관례(뒤로 · 시트 · 권한 · 알림)는 Android 방식을 따른다.

iOS 원본 약어(`iOS` = `../FestiCore-iOS/Projects`, `TT` = `iOS/Feature/Timetable/Sources`):

| 약어                                      | 경로                                                                                                                                                       |
| ----------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `TF` · `TFS`                              | `TT/Main/Reducer/TimetableFeature.swift` · `TimetableFeature+Schedule.swift`                                                                               |
| `TV` · `TB` · `TU`                        | `TT/Main/View/TimetableView.swift` · `Main/Components/TimetableBoard.swift` · `Main/Components/TimetableUnpublishedView.swift`                             |
| `SDF` · `SDV` · `STo`                     | `TT/SetDetail/Reducer/TimetableSetDetailFeature.swift` · `View/TimetableSetDetailView.swift` · `Components/TimetableScheduleToast.swift`                   |
| `SLF` · `SLV`                             | `TT/SetList/Reducer/TimetableSetListFeature.swift` · `View/TimetableSetListView.swift`                                                                     |
| `SPF` · `SPV`                             | `TT/SchedulePick/Reducer/SchedulePickFeature.swift` · `View/SchedulePickView.swift`                                                                        |
| `TI`                                      | `iOS/Feature/Timetable/Interface/Sources/{TimetableDelegate,SchedulePickDelegate}.swift`                                                                   |
| `TD`                                      | `iOS/Domain/TimetableDomain/Interface/Sources/Entity/{TimetableDay,TimetableOverlap,TimetableSetDetail,TimetableLineupDay}.swift`                          |
| `TCo` · `TCoV`                            | `iOS/App/Sources/Navigation/Timetable/TimetableCoordinator.swift` · `TimetableCoordinatorView.swift`                                                       |
| `MTC` · `MTCSy` · `MTCSc` · `MTV` · `PTM` | `iOS/App/Sources/Navigation/MainTab/MainTabCoordinator.swift` · `+Sync.swift` · `+Scope.swift` · `MainTabView.swift` · `FestivalPackTimetableMapper.swift` |
| `FSS`                                     | `iOS/Service/FestiCoreFestivalCatalog/Sources/NetworkFestivalScheduleService.swift`                                                                        |
| `FSE`                                     | `iOS/Domain/FestivalDomain/Interface/Sources/Entity/FestivalSchedule.swift`                                                                                |
| `FCS`                                     | `iOS/UI/FestiCoreSharedUI/Sources/Sheet/FCSetSheet.swift`                                                                                                  |
| `ADF`                                     | `iOS/Core/FestiCoreUtility/Sources/Date/AppDateFormat.swift`                                                                                               |

Android 약어: `MTS` = `app/src/main/kotlin/io/festicore/app/navigation/MainTabScreen.kt`, `FCoA` = `feature/festival/sources/.../FestivalCoordinator.kt`, `FFl` = `feature/festival/sources/.../FestivalFlow.kt`, `LBC` · `LBV` = `feature/festival/sources/.../liveboard/LiveBoard{Contract,ViewModel}.kt`, `DF` = `domain/festival/interface/src/main/kotlin/io/festicore/domain/festival`.

## 지금 Android 상태 (경계)

| 항목                              | Android 현재                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                       | 근거                                                                                                                                         |
| --------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ | -------------------------------------------------------------------------------------------------------------------------------------------- |
| 타임테이블 탭                     | 탭 이름만 그리는 `TabRoot`                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                         | `MTS:47 · 57-65`                                                                                                                             |
| 탭 바 숨김                        | 라이브 탭만 흐름이 알린다(`hidesLiveTabBar`)                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                       | `MTS:40 · 51`                                                                                                                                |
| 내 일정 읽기                      | `FestivalScheduleUseCase.mySchedule` · `FestivalScheduleService` · `FestivalApi.mySchedule`(Bearer, 세션 고정) — 보드가 사용                                                                                                                                                                                                                                                                                                                                                                                                                                       | `DF/FestivalScheduleUseCase.kt:3-6`, `service/festivalcatalog/.../FestivalScheduleService.kt:11-16`, `service/api/.../FestivalApi.kt:96-103` |
| 내 일정 저장                      | 없음. 오류에 `Stale`이 없다("저장 때 옮긴다")                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                      | `DF/FestivalSchedule.kt:6-16`                                                                                                                |
| 팩 문서                           | `FestivalPackDocument.performances`(timetable → sets 대체) · `days` · `artists` · `source` · `stages` 있음                                                                                                                                                                                                                                                                                                                                                                                                                                                         | `DF/FestivalPackDocument.kt:7-126`                                                                                                           |
| 보드 선택 축제 · 표시 문서 · 연결 | 보드 ViewModel 상태에만 있다(`festivalId` · `displayDocument` · `connection` · `cachedPacks`). 다른 탭이 읽을 길 없음                                                                                                                                                                                                                                                                                                                                                                                                                                              | `LBC:44-95`                                                                                                                                  |
| 축제 이미지 주소                  | `FestivalCatalogUseCase.imageUrl(value)`                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                           | `DF/FestivalCatalogUseCase.kt:14`                                                                                                            |
| 알림                              | 푸시(FCM) · 기기 등록 · `POST_NOTIFICATIONS` 없음. 매니페스트 권한은 `INTERNET`뿐, targetSdk 36                                                                                                                                                                                                                                                                                                                                                                                                                                                                    | `app/src/main/AndroidManifest.xml:4`, `build-logic/.../Catalog.kt:17`, `architecture.md` `service/device` 행("서버 기기 등록은 아직")        |
| 분석                              | `service/analytics` 모듈 없음(폴더 없음)                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                           | `ls service`                                                                                                                                 |
| 시간표 designkit                  | `FCSetBlock`(1분 2dp, `fcSetBlockHeight`) · `FCStageHeader(dotColor)` · `FCNowLine(time)` · `FCOverlapHighlight` · `FCSegmented` · `FCChip` · `FCSetSelectRow(overlapText)` · `FCStepHeader` · `FCSearchField` · `FCSearchEmpty(message)` · `FCSearchResultRow(Artist(initial))` · `FCLineupRow(thumbnail)` · `FCSourceLine(isInset)` · `FCEmptyCard(icon, action)` · `FCBanner` · `FCToast` · `FCTopBar(Large/Back, trailingIcon)` · `FCOfflineBadge` · `FCSkeletonView` · `FCButton` · `FCSheet(item, accessory)` · `FCToggle` · `FCTag(Lime/Warning)` 모두 있음 | `ui/designkit/src/main/kotlin/io/festicore/ui/designkit/component/**` 검색                                                                   |
| 없는 컴포넌트                     | **`FCSetSheet`**(iOS SharedUI, 436줄)                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                              | `FCS`, Android 검색 0건                                                                                                                      |

## 수용 기준

1. 라이브 탭에서 stage `fst_00e0f4f1d8065a93`(WONDERLIVET 2026)을 고르고 타임테이블 탭으로 가면: 상단 "타임테이블"(Large) + 검색 아이콘, 날짜 세그먼트 3칸("11/20(금)" · "11/21(토)" · "11/22(일)", `ADF:13 · 29`), "내 일정만" 칩 · "내 일정 0개", 무대 머리글 2개(LIVET STAGE 라임 점 · WONDER STAGE 보라 점), 12:00~22:00 격자에 공연 14개, 맨 아래 "출처: 공개 수집 카탈로그 · 확인 11/01 12:08"(verifiedAt `2026-11-01T03:08:48Z` → 서울).
2. 블록을 누르면 공연 상세 시트가 탭 바까지 덮고 올라온다. 로그인 상태면 토글로 내 일정에 넣고 "내 일정에 추가됐어요" 토스트가 2초 보인다. 넣은 블록은 라임(`Mine`), 겹치면 `Overlap` + 격자 주황 띠 "N분\n겹침".
3. 비로그인이면 토글을 바꾸면 이 기기에서만 표시되고(서버 저장 없음), "내 일정 N개" 버튼은 눌리지 않는다(`TF:112-114`).
4. 공개 전 축제(stage `fst_c39663182b10a105`, `timetableStatus pending`, 공연 0 · 아티스트 20)는 "시간표가 공개되면 알려 드릴게요" 카드 + "공개된 라인업" 20행.
5. 검색 아이콘 → "공연 검색" push, 탭 바 숨김, 결과 행을 누르면 pop 후 그 공연 시트. 시스템 뒤로 = pop.
6. "내 일정 N개" → 내 일정 고르기 push, 저장하면 pop + 격자 · 보드 내 일정 갱신.
7. 계정이 바뀌거나 로그아웃하면 내 일정 · 시트 · 고르기가 비워지고 서버 일정을 다시 읽는다.
8. 단위 테스트(JVM): iOS `TimetableFeatureTests`(25) · `TimetableScheduleTests`(30) · `TimetableSchedulePickTests`(13) · `SchedulePickFeatureTests`(9) · `TimetableEntityTests`(8) · `TimetableRoutingTests`(13, 매퍼 · 라우팅)를 같은 이름으로 옮긴다(`iOS/Feature/Timetable/Tests/Sources/*` · `iOS/Domain/TimetableDomain/Tests/Sources/TimetableEntityTests.swift` · `iOS/App/Tests/Sources/TimetableRoutingTests.swift`). 분석 이벤트 테스트(`mineOnlyAndDayChangesTrackAnalyticsEvents` · `togglingScheduleTracksAnalyticsEvent`)는 분석 모듈이 없어 뺀다(결정 T10). 실서버를 부르지 않는다(Ktor `MockEngine`).
9. `.claude/scripts/verify --all` · PR CI · `assembleStageDebug` 통과. 저장(PUT) · 로그인 흐름은 stage 로그인 수동 확인을 연결 PR에 기록한다.

## 모듈과 책임

| 모듈                                | iOS 대응                                                                                    | 내용                                                                                                                                                                                                                                                                                                                  |
| ----------------------------------- | ------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `domain/timetable/interface`(신규)  | `TD`                                                                                        | `TimetableDay` · `TimetableStage` · `TimetableSet` · `TimetableOverlap` · `TimetableSetDetail`(+`Status`) · `TimetableSetOverlap` · `TimetableLineupDay`/`Artist`, 시각 문구(`clock` · `timeRange` · `timeRangeWithDuration` · `previousTime` · `startChange` · `relativeTime` · `warning`). 순수 Kotlin(`java.time`) |
| `domain/timetable/sources`(신규)    | `PTM`(iOS는 App에 둠)                                                                       | 팩 → 날짜 매퍼(`FestivalPackTimetableMapper` 같은 이름), 겹침 계산, `di/`. `domain/festival:interface` 의존(팩 문서 · 이미지 주소)                                                                                                                                                                                    |
| `domain/festival`                   | `FSE` · `FestivalScheduleUseCaseInterface` · `FestivalNotificationSettingsUseCaseInterface` | `FestivalScheduleError.Stale(schedule)` 추가, `FestivalScheduleUseCase.saveMySchedule(festivalId, setIds, clientAt)`, 저장 알림(결정 T2), 보드 선택 공유 상태(결정 T1), (알림 PR) 알림 설정 UseCase                                                                                                                   |
| `service/api`                       | `FSS` 요청                                                                                  | `FestivalApi.replaceMySchedule`(PUT, Bearer, 세션 고정), `FestivalScheduleReplaceBody(setIds, clientAt)`                                                                                                                                                                                                              |
| `service/festivalcatalog`           | `FSS`                                                                                       | `FestivalScheduleService.replace`: 409 `STATUS_STALE`면 다시 읽어 `Stale` 재료로 돌려줌                                                                                                                                                                                                                               |
| `feature/timetable/interface`(신규) | `TI`                                                                                        | `TimetableRoute`(`Timetable` · `SetList(mode)` · `SchedulePick(...)`), `SchedulePickResult`(saved · skipped · later)                                                                                                                                                                                                  |
| `feature/timetable/sources`(신규)   | `TT/**` · `TCo` · `TCoV`                                                                    | `TimetableCoordinator` · `TimetableFlow` · `main/`(ViewModel · Contract · Screen/Content · `TimetableBoard` · `TimetableUnpublished`) · `setdetail/`(시트) · `setlist/` · `schedulepick/` · `di/`                                                                                                                     |
| `ui/sharedui`(또는 designkit)       | `FCS` · `iOS/UI/FestiCoreSharedUI/Sources/Toast/FCToast+FestiCore.swift:25`                 | `FCSetSheet` 신규(iOS와 같은 SharedUI 자리), `FCToastItem` 프리셋 "내 일정에 추가됐어요"                                                                                                                                                                                                                              |
| `feature/festival`                  | `MTCSy:75-165` 입력 쪽                                                                      | 보드 ViewModel이 선택 축제 · 표시 문서 · 연결 · 팩 확인 시각을 공유 상태에 쓴다(T1), 저장 알림을 받아 보드 내 일정 갱신(T2)                                                                                                                                                                                           |
| `app`                               | `MTC` · `MTV`                                                                               | `MainTabScreen`에 `timetableContent` 슬롯 + 타임테이블 탭 바 숨김, Koin 목록에 새 모듈, 준비 안내 1단계 화면 슬롯(PrepGuide PR)                                                                                                                                                                                       |

`architecture.md` 대응표에 `Feature/Timetable` ↔ `feature/timetable`, `Domain/…Timetable…` ↔ `domain/timetable` 이름은 이미 있다(행 137 · 141). iOS `TimetableDomain`에는 엔티티만 있고 매퍼는 App에 있다(`PTM`). Android는 app에 로직을 두지 않으므로 매퍼를 `domain/timetable/sources`로 옮긴다(결정 T3).

---

## 화면 1: Timetable (S02)

### 화면 구성 (`TV`)

| 요소                             | iOS 값                                                                                                                                                                                                                                                                                           | 근거                                      |
| -------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ | ----------------------------------------- |
| 뼈대                             | 세로: 머리 → (오프라인이면) `FCOfflineBadge(message)` → 내용 → `Spacer` → (출처 있으면) `FCSourceLine`(탭 바 바로 위). 배경 `backgroundNormal`                                                                                                                                                   | `TV:22-38`                                |
| 머리                             | `FCTopBar(.large, "타임테이블")` + 오른쪽 `magnifyingGlass` 접근성 라벨 "검색" → 검색. 날짜가 있으면 날짜 고르기(공개 전이어도 `days`가 있으면 보인다)                                                                                                                                           | `TV:115-128`                              |
| 날짜 고르기                      | 3일 이하 `FCSegmented`(좌우 `paddingP16`), 4일 이상 가로 스크롤 `FCChip` 줄(간격 `paddingP8`, 좌우 `paddingP16`, 스크롤 막대 숨김, 처음 · 바뀔 때 고른 칩을 가운데로)                                                                                                                            | `TV:130-163 · 193-196`                    |
| 로딩                             | 스켈레톤 3개(`r16`, 높이 120, 간격 · 여백 `paddingP16`)                                                                                                                                                                                                                                          | `TV:105-113`                              |
| 실패                             | `FCEmptyCard(bell, "시간표를 불러오지 못했어요", "연결 상태를 확인하고 다시 시도해 주세요.")` + "다시 시도"(primary) → 상위에 재시도, 여백 `paddingP16`                                                                                                                                          | `TV:50-63`                                |
| 축제 없음                        | `FCEmptyCard(magnifyingGlass, "축제를 선택해 주세요", "참여할 축제를 선택하면 시간표를 볼 수 있어요.")`                                                                                                                                                                                          | `TV:64-70`                                |
| 공연 없음(공개됨 · 고른 날 없음) | `FCEmptyCard(bell, "등록된 공연이 없어요", "공연 정보가 등록되면 시간표에서 확인할 수 있어요.")`                                                                                                                                                                                                 | `TV:94-101`                               |
| 필터 줄                          | 왼쪽 `FCChip("내 일정만", isSelected)`, 오른쪽 버튼: 겹침 경고가 없으면 "내 일정 {고른 날 내 공연 수}개"(`labelAssistive`), 있으면 "겹치는 일정 {N}개"(`accentPointOrange`), `baseFootnoteRegular` + 줄 박스, 접근성 라벨 "내 일정 고르기", `canPickSchedule`가 아니면 비활성, 여백 `paddingP16` | `TV:165-189`, `TF:116-118`                |
| 변경 배너                        | 고른 날 변경 수 > 0이면 `FCBanner("시간표가 바뀌었어요 · {N}개 변경")` → 변경 목록, 좌우 · 아래 `paddingP16`                                                                                                                                                                                     | `TV:80-86`, `TD/TimetableDay.swift:41-44` |
| 격자                             | `TimetableBoard`(아래)                                                                                                                                                                                                                                                                           | `TV:87-93`                                |
| 아이콘                           | `bell` → `ic_bell`, `magnifyingGlass` → `ic_magnifying_glass`, 라인업 자리 `music20` → `ic_music_20`(모두 `ui/designkit/src/main/res/drawable`에 있음)                                                                                                                                           |                                           |
| 접근성 식별자                    | iOS 타임테이블 · 좌표 화면에 `accessibilityIdentifier` 0건(`TT` · `iOS/App/Sources/Navigation/Timetable` 검색)                                                                                                                                                                                   |                                           |

### 격자 (`TB`)

| 항목         | iOS 값                                                                                                                                                                                                                                        | 근거                                                                                     |
| ------------ | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------- |
| 넓은 보드    | 무대 수 > 2면 가로 · 세로 스크롤, 아니면 세로만(열이 화면 너비를 나눔)                                                                                                                                                                        | `TB:30 · 89-91 · 272`                                                                    |
| 넓은 너비    | `56 + 140×n + 8×(n−1) + 16`                                                                                                                                                                                                                   | `TB:114-118 · 278 · 282`                                                                 |
| 머리글       | `FCStageHeader(name, dotColor = [accentPointLime, accentPointPurple, accentPointBlue][i % 3])`, 간격 `paddingP8`, 왼쪽 56 · 오른쪽 `paddingP16`, 배경 `backgroundNormal`, 아래 1 `lineNormal`. 세로로 고정(pinned), 가로는 격자와 같이 움직임 | `TB:31-40 · 120-135 · 289`                                                               |
| 시간 범위    | 시작 = 공연 최소 시작/60(없으면 12시), 끝 = max(시작+1, ceil(최대 종료/60))(없으면 24시)                                                                                                                                                      | `TB:101-108 · 276-277`                                                                   |
| 세로 좌표    | `y(분) = (분 − 시작시×60) × 2 × zoom`(`FCSetBlock.height(minutes: 1)` = 2)                                                                                                                                                                    | `TB:261-263 · 268`, `iOS/UI/FestiCoreDesignKit/Sources/Component/*/FCSetBlock.swift:218` |
| 시 줄        | 시마다 1 `lineNormal`(왼쪽 52, 오른쪽 `paddingP16`) + 라벨 `HH:mm`(`baseCaption1Regular` `labelAssistive`, 왼쪽 `paddingP12`, 위로 8)                                                                                                         | `TB:158-174 · 279 · 285`                                                                 |
| 격자 위 여백 | 8(첫 라벨이 머리글에 가리지 않게)                                                                                                                                                                                                             | `TB:154-155`                                                                             |
| 블록         | 무대 열마다 `FCSetBlock(artist, time = timeRange)`, 위치 `y(start) + 2`, 높이 `max(8, y(end) − y(start) − 4)`                                                                                                                                 | `TB:176-209 · 271 · 283`                                                                 |
| 블록 상태    | 변경(previousStart 있음) → `Changed(oldTime, change)` 우선, 그다음 겹침 경고 중이면서 내 공연이 겹치면 `Overlap()`, 내 일정 `Mine`, 아니면 `Default`                                                                                          | `TB:238-249`, `TF:146-149`                                                               |
| 투명도       | "내 일정만"인데 내 공연 아님 0.2, 끝난 공연(`end ≤ nowMinute`) 0.4                                                                                                                                                                            | `TB:251-259 · 287-288`                                                                   |
| 겹침 띠      | 보이는 겹침마다 `FCOverlapHighlight`(왼쪽 위 · 아래 `r4`, 왼쪽 52부터) + 주황 막대 3(왼쪽 46, 둥근 3) + "{분}분\n겹침"(`baseCaption2Bold` `accentPointOrange`, 왼쪽 `paddingP12`). 접근성: 자식 무시, 라벨 "내 일정 {분}분 겹침"              | `TB:211-236 · 280-281`                                                                   |
| 지금 줄      | `nowMinute`가 범위 안이면 `FCNowLine(time = clock(now))`, 위치 `y(now) − 10`(높이 20)                                                                                                                                                         | `TB:147-150 · 286`                                                                       |
| 첫 스크롤    | 날짜가 바뀔 때(`task(id: day.id)`) `nowMinute`가 있으면 `now/60` 시 줄을 위로 맞춤                                                                                                                                                            | `TB:46-53`                                                                               |
| 확대         | 두 손가락, 0.5~2배, 움직이는 동안은 화면 값만, 끝날 때만 확정값 전송(`zoomChanged`). 날짜를 바꿔도 유지                                                                                                                                       | `TB:25-26 · 55-63 · 81-87 · 269-270`, `TF:70-71 · 404-406`                               |
| 날짜 넘기기  | 세로 전용 보드에서만: 최소 거리 30, 가로 > 80 그리고 가로 > 세로×1.5면 왼쪽 = 다음 날(+1), 오른쪽 = 전날(−1). 범위 밖은 무시(`daySelected` 가드)                                                                                              | `TB:64-77 · 93-99 · 273-275`, `TF:368-369`                                               |
| 아래 여백    | 20(`fcScrollBottomInset`)                                                                                                                                                                                                                     | `TB:44-45`                                                                               |

### 격자 렌더링 (Compose 구현안, 결정 T5 · T6)

- **배치**: 한 날짜의 격자는 높이가 `y(끝시×60)`으로 정해진 고정 캔버스다. `Box(Modifier.height(gridHeight))` 안에 시 줄 · 겹침 띠 · 무대 열(`Row`, 열마다 `Box` + 블록 `Modifier.offset(y)` · `height`) · 지금 줄을 겹쳐 그린다(iOS `ZStack` + `.offset`과 같은 절대 배치). 블록이 수십 개(stage 하루 14개)라 Lazy가 필요 없다.
- **스크롤**: 세로 `rememberScrollState()` 하나, 넓은 보드는 가로 `rememberScrollState()` 하나. 격자 `Modifier.verticalScroll(v)` 안에 `Modifier.horizontalScroll(h)` 내용. 머리글은 세로 스크롤 밖(위)에 두어 고정하고, 가로는 같은 `h.value`만큼 `Modifier.layout`/`graphicsLayer { translationX = -h.value }`로 옮긴다(+`clipToBounds`). 같은 `ScrollState`를 두 `horizontalScroll`에 붙이지 않는다(두 측정이 `maxValue`를 다투는 것을 피함). 머리글은 입력을 받지 않으므로 머리글을 끌어 가로로 넘기는 동작은 없다 — iOS는 머리글이 스크롤 내용 안이라 끌린다(`TB:31-40`). 차이를 PR에 적고, 필요하면 머리글에도 `Modifier.scrollable(h, Horizontal)`을 붙인다(같은 상태를 입력으로만 공유).
- **첫 스크롤**: `LaunchedEffect(day.id)`에서 `v.scrollTo(y(now/60×60) + 8)`(iOS는 시 줄 뷰 위쪽 기준 `anchor: .top`). 정확한 px은 PR에서 iOS 스크린샷과 대조.
- **확대**: `Modifier.pointerInput` + `awaitEachGesture`로 손가락 2개 이상일 때만 `calculateZoom()`을 곱해 화면 값 `gestureZoom`을 바꾸고 그 이벤트만 소비, 손을 떼면 `onZoomEnd(clamp(zoom × gesture))`. 손가락 1개 이벤트는 소비하지 않아 스크롤이 그대로 동작한다. `Modifier.transformable`은 한 손가락 이동도 상태로 가져가 스크롤과 겹칠 수 있어 쓰지 않는다(T6, 동작은 PR에서 기기 확인).
- **날짜 넘기기**: 세로 전용 보드에만 `pointerInput`(`PointerEventPass.Initial`, 소비하지 않고 누적 이동만 관찰)으로 판정하고, 판정 함수 `dayOffsetForSwipe(dx, dy)`는 순수 함수로 두어 iOS `daySwipeNeedsMostlyHorizontalDrag`를 그대로 옮긴다. 최소 거리 30은 iOS `DragGesture(minimumDistance:)`에 대응(`TB:94`).
- **현재 시각**: iOS는 1초 타이머(`TF:517-526`). ViewModel이 `intent` 안에서 `Clock` 주입 1초 루프를 돌리고, 화면이 `STARTED` 밖이면 멈춘다(`clockStarted` · `clockStopped`, `TV:39-40`). 상태에는 분 단위 `nowMinute`만 두어 1초마다 같은 값이면 재구성이 일어나지 않는다.

### 공개 전 (`TU`)

| 요소        | iOS 값                                                                                                                                                                                                                                                                                                                                           | 근거        |
| ----------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ | ----------- |
| 뼈대        | 세로 스크롤, 간격 `paddingP24 + marginM2`, 좌우 · 위 `paddingP16`, 아래 여백 20                                                                                                                                                                                                                                                                  | `TU:20-59`  |
| 카드        | `FCEmptyCard(bell, "시간표가 공개되면 알려 드릴게요", 메시지)` + 버튼 저장 중 "설정 중", 아니면 "알림 받기"(primary, 저장 중엔 무동작), 아래 `marginM6`, 배경 `backgroundElevated` `r16`                                                                                                                                                         | `TU:25-44`  |
| 메시지      | idle "대략적인 라인업은 먼저 확인할 수 있어요.\n시간표 공개 후 원하는 무대를 선택해 내 일정에 넣어 보세요." / saving "알림 설정을 저장하고 있어요." / enabled "팩 업데이트 알림을 켰어요." / permissionRequired "팩 업데이트 알림을 켰어요. 기기 설정에서도 알림을 허용해 주세요." / failed "알림 설정을 저장하지 못했어요. 다시 시도해 주세요." | `TU:64-72`  |
| 라인업 구역 | 제목(`baseFootnoteBold` `labelNeutral`, 머리글) + `FCLineupRow(number = "%02d", artist, showsSeparator = 마지막 아님)` + 썸네일 `FCRemoteImage(fill, placeholder music20 크기 20)`, 묶음 `r16` 클립, 간격 `paddingP12`                                                                                                                           | `TU:75-102` |
| 라인업 자료 | 팩 `artists`에서 이름 빈 값 제외, 하나도 없으면 구역 없음. 구역 하나 "공개된 라인업"(id = 팩 ID)                                                                                                                                                                                                                                                 | `PTM:76-94` |

### 팩 → 시간표 매퍼 (`PTM`)

- 공연: `performances`(Android `FestivalPackDocument.performances`, `DF/FestivalPackDocument.kt:36-42`)에서 시작 · 종료가 ISO로 읽히고 종료 > 시작인 것만(`PTM:16-21`).
- 공연이 하나도 없으면(`pack.performances.isEmpty`) `days`에서 날짜(`date ?? startsAt`, `yyyy-MM-dd` 엄격 또는 ISO)로 빈 날을 만든다(`PTM:23-36`). 주의: 조건은 원본 공연 목록이 비었는지이고, 공연이 있지만 모두 무효면 날짜 0개다.
- 날짜 = 시작 시각의 서울 자정(`ADF:18-24`), 날짜 순. 날짜 ID `yyyy-MM-dd`, 제목 `M/d(E)`(ko_KR), `PTM:38-41 · 66-71`.
- 무대 = 그날 시작순으로 처음 나온 순서(`stageId ?? stageName ?? "stage-unassigned"`, 이름 없으면 "무대 미정"). 팩 `stages.order`는 쓰지 않는다(`PTM:43-50`).
- 공연 = id · 아티스트(없으면 "출연진 미정") · 시작/종료 분(자정 넘기면 1440 이상) · 이미지(`imageUrl` → 서버 주소) · 메모(`PTM:51-64`).
- `previousStart`(시간 변경): 같은 축제의 직전 `days`에서 같은 날 · 같은 ID 공연을 찾아, 시작이 같으면 직전 `previousStart`를 이어받고 다르면 직전 `start`(`PTM:52 · 60`). 앱 안 메모리 비교이고 서버 필드가 아니다. 축제가 바뀌면 직전 값 없음(`MTCSy:82-83`).
- 출처: 이름 없으면 "", `verifiedAt` 없으면 "출처: {이름}", 있으면 "출처: {이름} · 확인 {MM/dd HH:mm}"(서울, en_US_POSIX) (`PTM:96-100`, `ADF:15 · 30`).

### 내용 상태 판정 (`MTCSy:99-109`)

| 조건                                                                                 | `contentStatus` |
| ------------------------------------------------------------------------------------ | --------------- |
| 보드 선택 축제 없음                                                                  | `noFestival`    |
| 표시 문서(온라인 상세 ?: 받은 팩, 단 ID가 선택 축제와 같을 때)가 있고 공연 0         | `unpublished`   |
| 표시 문서 있고 공연 있음                                                             | `published`     |
| 문서 없음 ∧ (온라인 상세 로딩 중 ∨ (연결 로딩 ∧ 상세 실패 아님 ∧ 팩 감시 실패 아님)) | `loading`       |
| 그 밖                                                                                | `failed`        |

오프라인 안내(`MTCSy:110-123`): 연결 `offline`이고 문서가 있을 때만. 받은 팩 `checkedAt`이 있으면 분 = max(0, (지금 − checkedAt)/60) → 0이면 "오프라인 · 방금 확인한 정보", 아니면 "오프라인 · {분}분 전 정보", 없으면 "오프라인 · 저장된 정보".

### 상태 (`TimetableContract.kt`, iOS `TF:58-207`)

| 필드                                                                                                                           | iOS                                                                     | 비고                 |
| ------------------------------------------------------------------------------------------------------------------------------ | ----------------------------------------------------------------------- | -------------------- |
| `notificationStatus`                                                                                                           | idle · saving · enabled · permissionRequired · failed                   | `TF:47-49 · 60`      |
| `contentStatus`                                                                                                                | loading · failed · noFestival · unpublished · published(기본 published) | `TF:39-45 · 61`      |
| `days` · `lineup` · `selectedDayIndex` · `source` · `offlineMessage` · `festivalId`                                            | 팩 동기화로 받음                                                        | `TF:62-78`           |
| `mySetIds` · `showsMineOnly` · `shouldShowOverlapWarning` · `zoom`(1) · `nowMinute` · `clockAt`                                |                                                                         | `TF:66-74`           |
| `setDetail: SetDetailState?`                                                                                                   | 시트(`setId` · `content` · `isToastVisible`)                            | `TF:80`, `SDF:17-43` |
| `scheduleSync`                                                                                                                 | idle · loading · ready · localOnly                                      | `TF:22-31 · 83`      |
| `isScheduleLimitReached` · `loadRequestId` · `accountGeneration` · `isSaving` · `needsSave` · `lastClientAt` · `uncertainSave` | 동기화 내부                                                             | `TF:84-92`           |

고르기(`destination`)는 Android에서 ViewModel 상태가 아니라 Coordinator 경로다(결정 T8). 대신 `isPickOpen`(Coordinator가 알림)을 두어 `timetableAppeared` · 팩 갱신 규칙(`TF:445-452`)을 지킨다.

파생 값: `canPickSchedule = festivalId != null ∧ published ∧ scheduleSync == ready ∧ !isSaving`(`TF:112-114`), `mySetCount`(고른 날만, `TF:116-118`), `overlaps`(내 공연 전부를 시작순 정렬해 쌍마다 교집합 > 0, 고른 날 공연이 낀 쌍만, 분은 고른 날 자정 기준 — 자정 넘김 포함, `TF:124-140 · 180-186`), `visibleOverlaps`(`TF:142-144`).

### 동작 (`TimetableViewModel`, 행 번호는 `TF` · `TFS`)

| Android 함수                                | iOS Action                                        | 규칙                                                                                                                                                                                                                                 |
| ------------------------------------------- | ------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| `onClockStarted` / `onClockStopped`         | `clockStarted` · `clockStopped`                   | 시각 갱신 후 1초 루프 / 루프 취소(`:351-363`). 분석 `screenView`는 T10                                                                                                                                                               |
| `onRetryClicked`                            | `retryTapped`                                     | 상위 → 보드 온라인 상세 다시 읽기(`:365-366`, `MTCSc:156-163`)                                                                                                                                                                       |
| `onDaySelected(i)`                          | `daySelected`                                     | 범위 밖 무시, 다른 날이면 겹침 경고 끔, 시각 다시 계산(`:368-386`)                                                                                                                                                                   |
| `onMineOnlyToggled`                         | `mineOnlyToggled`                                 | `:388-396`                                                                                                                                                                                                                           |
| `onSetClicked(id)`                          | `setTapped`                                       | 시트 내용 계산해 열기(없는 ID면 닫힘) (`:398-402`)                                                                                                                                                                                   |
| `onZoomChanged(z)`                          | `zoomChanged`                                     | `:404-406`                                                                                                                                                                                                                           |
| `onChangeBannerClicked` · `onSearchClicked` | → 변경 목록 · 검색                                | SideEffect → Coordinator(`:408-412`)                                                                                                                                                                                                 |
| `onNotifyClicked`                           | `notifyTapped` → 상위 → `enablePackNotifications` | 저장 중이면 무시 → 설정 읽기 → `packUpdates`가 꺼져 있으면 그것만 켬 → 권한 읽기 → 허용 enabled, 아니면 permissionRequired, 예외 failed(`:414-420 · 499-515`, `TCo:116-117`)                                                         |
| `onSchedulePickClicked`                     | `schedulePickTapped`                              | 열려 있지 않고 `canPickSchedule`면 고르기 push(`:422-430`)                                                                                                                                                                           |
| `onScheduleToggled`(시트)                   | `setDetail.delegate.scheduleToggled`              | `TFS:57-82`: 로딩 중 무시. 빼기는 한도 표시 끔. 넣기는 끝난 공연 거절, 60개면 한도 표시, 넣으면 토스트 2초. 겹침 경고 = 겹침 있음. `ready`일 때만 저장                                                                               |
| `onSetDetailDismissed`                      | `setDetail.dismiss`                               | 한도 표시 끔(`:590-592`)                                                                                                                                                                                                             |
| `onSchedulePickResult(r)`                   | `handleSchedulePickDelegate`                      | saved(같은 축제): 내 일정 = 결과 ∩ 팩, 바뀌었으면 겹침 경고 갱신, `ready`, 한도 · 불확실 · 저장 중 · 대기 저장 해제, 진행 중 저장 취소, `lastClientAt = max(now, last + 1ms)`, 보드에 저장 알림. skipped · later: 무동작(`:599-629`) |
| (입력) 축제 변경                            | `festivalChanged`                                 | 같으면 무시, 시트 · 고르기 닫기, 읽기 · 저장 취소, 초기화 후 읽기(`TFS:17-31`)                                                                                                                                                       |
| (입력) 계정 변경                            | `accountChanged`                                  | 세대 +1, 요청 ID +1, 고르기 닫기, 취소 · 초기화 · 읽기(`TFS:33-43`). 보내는 조건은 아래                                                                                                                                              |
| (입력) 팩 갱신                              | `packUpdated`                                     | 시각 갱신, 고르기 닫기, 팩에 없는 공연 빼고 `ready`면 저장, 시트의 공연이 사라지면 닫기(`:438-447`, `TFS:84-96`)                                                                                                                     |
| (입력) 탭 다시 들어옴                       | `timetableAppeared`                               | 축제 있음 ∧ 저장 중 아님 ∧ 고르기 닫힘일 때, 불확실 저장이 있으면 저장, 없으면 읽기(`:449-452`)                                                                                                                                      |
| (입력) 팩 값                                | `packSynced`                                      | 날짜 · 라인업 · 출처 · 상태 · 오프라인 교체, 고른 날은 같은 날짜 ID, 없으면 0(`:454-468`)                                                                                                                                            |
| (입력) 다른 화면 저장                       | `mySetIdsChanged`                                 | 준비 안내 1단계 저장 결과(`:470-472`, `MTCSc:111-122`)                                                                                                                                                                               |

매 동작 뒤 열린 시트 내용이 바뀌었으면 시트에 새 내용을 먼저 반영한다(`TF:335-336`, `TFS:98-104`). Android는 시트 상태가 같은 ViewModel에 있어 `reduce` 안에서 같이 계산한다(결정 T7).

### 저장 규칙 (`TFS:106-192`)

- 읽기: 축제 있고 로딩 중 아님 → `loading`, 요청 ID +1. 결과는 축제 · 요청 ID · `loading`이 모두 맞을 때만. 성공 = 서버 ID ∩ 팩, 경고 끔, `ready`. 실패 = `localOnly`(`:106-111 · 131-150`).
- 저장: 저장 중이면 `needsSave`만. 불확실 저장이 있으면 목록이 같을 때 같은 요청을 다시, 다르면 서버를 먼저 읽음. 아니면 `clientAt = max(now, last + 1ms)`, ID 정렬, 계정 세대 포함(`:113-129`).
- 결과는 축제 · 세대 · 저장 중이 맞을 때만. 성공 → 저장 알림(대기 있으면 이어서 저장). `stale(server)` → 서버 목록 채택 + 알림(대기 버림). `unauthorized` → `localOnly`. `rejected` → 다시 읽기. `unavailable` → 불확실 저장으로 보관(`:152-181`).
- 한도 60(`TF:19-20`).

### 계정 변경 입력 (`MTC:488-503 · 527-536`)

- 이전 사용자 ID가 있고 바뀌었거나(다른 계정), 세션이 있다가 없어지면(로그아웃) `accountChanged`. 로그인(없음 → 있음)은 보내지 않는다 — `localOnly` 상태라 다음 탭 진입 `timetableAppeared`가 다시 읽는다.
- 그 전에 동기로 화면 값을 비운다: 내 일정 · `idle` · 한도 · 시트 · 고르기(`MTC:530-534`).
- Android: `TimetableViewModel`이 `CurrentUserProvider.currentUserId`(`domain/auth/.spec/current-user.md`)를 받아 같은 조건으로 처리한다(보드 `FestivalCoordinator`의 `lastUserId` 선례, `FCoA:51-61`). "세션 있음/없음"은 `currentUserId` null 여부로 본다.

---

## 화면 2: SetDetail (S03 시트)

### 내용 (`TF:151-178`, `SDF`, `SDV`)

| 항목                                                                    | 값                                                                                                                                                                                                     | 근거                                                                                                    |
| ----------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ | ------------------------------------------------------------------------------------------------------- |
| 아티스트 · 무대(없으면 "무대 미정") · 시간 `HH:mm–[다음날 ]HH:mm · N분` |                                                                                                                                                                                                        | `TF:159-163`, `TD/TimetableDay.swift:110-140`                                                           |
| 상태                                                                    | 지금 ≥ 끝 `ended`, ≥ 시작 `live`, 아니면 `upcoming(ceil(남은 분))`. 화면은 매분 다시 계산                                                                                                              | `TD/TimetableSetDetail.swift:73-87`, `SDV:23-27`                                                        |
| 상대 시각                                                               | "N분 후"(< 60) · "H시간 후" · "H시간 M분 후" · "공연 중" · "종료"                                                                                                                                      | `TD/TimetableSetDetail.swift:57-70`                                                                     |
| 겹침                                                                    | 다른 내 공연과 교집합 > 0인 것 중 첫째(시작순): "{아티스트}({무대} {HH:mm–HH:mm}){와/과} {분}분 겹쳐요". 조사: 끝 글자가 한글이면 받침 없음 "와"/있음 "과", 한글 아니면 "와"(NFC 정규화 후)            | `TF:188-206`, `TD/TimetableSetDetail.swift:110-121`                                                     |
| 시트 상태 우선순위                                                      | 끝남 → 겹침 → 변경 → 기본                                                                                                                                                                              | `SDV:53-64`                                                                                             |
| 일정 문구                                                               | 제목 "내 일정에 넣기", 도움말: 로딩 중 "내 일정을 불러오는 중이에요" / 한도 "내 일정은 최대 60개까지 저장할 수 있어요" / 기본 "공연 10분 전에 알려 드려요". 로딩 중이면 토글 비활성                    | `SDV:43-47 · 66-75`                                                                                     |
| 토스트                                                                  | 넣은 직후 `FCToast(scheduleAdded)` "내 일정에 추가됐어요" 2초, 다시 넣으면 처음부터. 빼거나 서버 결과가 되돌리면 바로 숨김. 시트 위쪽 accessory, 등장 · 퇴장 `easeOut` 0.25초 + 아래에서 이동 · 투명도 | `SDF:89-91 · 160-185`, `STo:20-35`, `iOS/UI/FestiCoreSharedUI/Sources/Toast/FCToast+FestiCore.swift:25` |
| 표시 위치                                                               | 탭 바까지 덮도록 탭 화면 위에서 `fcModal`(높이 auto, 손잡이 있음)                                                                                                                                      | `MTV:67-79`, `iOS/UI/FestiCoreSharedUI/Sources/Modal/FCModal.swift:20-61`                               |

### `FCSetSheet` (신규 컴포넌트, `FCS`)

- 세로 간격 `paddingP16`, 위 `paddingP16` · 좌우 · 아래 `paddingP20`. 순서: 사진(있으면, 높이 180) → 태그 줄(간격 `paddingP4`) → 제목 줄 → 무대 · 시각 줄(간격 `paddingP8`) → 겹침 경고 상자 → "한 줄 메모" 상자 → 일정 줄 → 출처(배경 없음, 위아래 `paddingP12`) (`FCS:55-104 · 253-284`).
- 태그: 기본 [내 일정(Lime, 저장 안 했으면 투명하게 자리만)], 겹침 [내 일정, 겹침(Warning)], 변경 [{변경}(Warning)], 끝남 없음. 투명 태그가 자리를 지켜 토글해도 높이가 그대로(`FCS:121-150`), 토글 시 0.15초(모션 줄이기면 없음).
- 일정 줄: 제목 `baseCalloutBold`, 도움말 `baseFootnoteRegular` `labelAssistive`, `FCToggle(accessibilityLabel = 제목)`, 끝남이면 "공연 종료"/"이미 종료된 공연이에요" + 비활성 0.3(`FCS:37-40 · 289-311 · 383`).
- 아이콘 20 · 작은 아이콘 16(`FCS:380-381`). 나머지 세부 수치는 S1 PR에서 `FCS` 전체를 열어 옮긴다.
- 자리: iOS와 같게 `ui/sharedui`(`FCRemoteImage`로 사진을 그리므로 designkit이 아니라 sharedui, `design-system.md` "컴포넌트"). 시트 틀은 designkit `FCSheet(item, accessory)`(Material3 `ModalBottomSheet`, 창 위라 탭 바까지 덮음, `ui/designkit/.../modal/FCSheet.kt:125-145`).

---

## 화면 3: SetList (검색 · 시간표 변경)

| 요소       | iOS 값                                                                                                                                           | 근거                       |
| ---------- | ------------------------------------------------------------------------------------------------------------------------------------------------ | -------------------------- |
| 상단       | `FCTopBar(.back, 검색 "공연 검색" / 변경 "시간표 변경")`, 뒤로 → pop                                                                             | `SLV:15-19`                |
| 검색칸     | 검색 모드만 `FCSearchField(placeholder "아티스트, 무대 검색")`, 여백 `paddingP16`                                                                | `SLV:20-29`                |
| 결과       | 날짜별로 이어 붙인 `FCSearchResultRow(Artist(initial = 첫 글자), title = 아티스트, meta = "{날짜 제목} · {timeRange}[ · {변경}]")`, 아래 여백 20 | `SLV:44-58`                |
| 빈 결과    | `FCSearchEmpty(query)` + 메시지: 변경 "확인된 시간표 변경이 없어요", 검색 "아티스트 이름이나 무대로 다시 찾아보세요"                             | `SLV:40-42`                |
| 거르기     | 변경: `previousStart != null`. 검색: 공백 제거한 검색어가 비었거나, 아티스트 · 무대 이름에 대소문자 무시 포함. 공연 없는 날은 뺌                 | `SLF:27-39`                |
| 행 탭      | 결과에 있는 ID면 pop + 타임테이블에서 그 공연 시트 열기                                                                                          | `SLF:84-86`, `TCo:123-125` |
| 열기 조건  | 고르기가 열려 있으면 무시. 현재 `days`를 복사해 연다                                                                                             | `TCo:97-111`               |
| 팩 갱신 중 | 같은 축제면 새 `days`로 목록 갱신(모드 · 검색어 유지), 다른 축제면 닫음                                                                          | `MTCSy:138-146`            |
| 배경       | `backgroundNormal`                                                                                                                               | `SLV:33`                   |

Android: `FCSearchResultRow`의 `highlightedQuery` 인자는 iOS가 넘기지 않으므로 기본값 그대로 둔다.

## 화면 4: SchedulePick (S15-1)

| 요소      | iOS 값                                                                                                                                                                                                                                                                                        | 근거                             |
| --------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | -------------------------------- |
| 상단      | `FCStepHeader(step: 1)` + 나중에 → `later`                                                                                                                                                                                                                                                    | `SPV:24-25`                      |
| 본문      | 세로 스크롤, 간격 `paddingP20`, 여백 `paddingP16`, 배경 `backgroundElevated`                                                                                                                                                                                                                  | `SPV:26-41`                      |
| 머리      | "Step 1. 내 일정 고르기"(`baseCaption1Medium` `labelAssistive`) / "보고 싶은 무대를 골라 주세요"(`baseTitle2Bold` `labelNormal`, 머리글) / "고른 무대는 시작 10분 전에 알려 드려요.\n나중에 타임테이블에서 언제든 바꿀 수 있어요."(`baseSubheadlineRegular` `labelNeutral`), 간격 `paddingP8` | `SPV:46-62`                      |
| 날짜      | `FCSegmented`(날짜 수 제한 없음 — 타임테이블과 다름)                                                                                                                                                                                                                                          | `SPV:29`                         |
| 목록      | 오른쪽 정렬 "{N}개 선택"(`baseFootnoteBold`), 고른 날 공연 시작순 `FCSetSelectRow(artist, stage("무대 미정"), start, end, isSelected)`, 행 사이 1 `lineNormal`, 묶음 `r16` 클립 + 1 `lineNormal` 테두리, 간격 `paddingP8`                                                                     | `SPV:64-83 · 100-111`            |
| 시각      | `HH:mm`(자정 넘기면 다음 날 시각)                                                                                                                                                                                                                                                             | `SPV:149-151`                    |
| 겹침 문구 | 고른 공연이면, 같은 날 먼저(또는 같은 시각) 시작한 다른 고른 공연과 겹친 분이 가장 큰 것: "{아티스트}{와/과} {분}분 겹쳐요". 조사: 한글 받침 없음 "와"/있음 "과", **한글이 아니면 "과"**(시트 경고와 다름, T11)                                                                               | `SPF:55-65`, `SPV:110 · 154-157` |
| 빈 날     | "이 날은 아직 공연 시간이 없어요."(`baseSubheadlineRegular` `labelAssistive`, 가운데, 위아래 `paddingP24`)                                                                                                                                                                                    | `SPV:84-90`                      |
| 오류      | `baseCaption1Regular` `statusNegative`                                                                                                                                                                                                                                                        | `SPV:91-96`                      |
| 아래 바   | `FCButton("건너뛰기", ghost)` + `FCButton(저장 중 "저장하는 중" / "다음 · {N}개 선택", 가득, isEnabled = canSubmit)`, 간격 `paddingP8`, 좌우 `paddingP16` · 위 `paddingP12` · 아래 `paddingP8`, 위 1 `lineNormal`, 배경 `ultraThinMaterial` + `materialGlass`(아래 안전 영역까지)             | `SPV:113-136`                    |

동작(`SPF:128-215`): 날짜 탭(범위 안만), 공연 탭(저장 중 무시, 빼기 또는 넣기 — 60개면 "내 일정은 60개까지 고를 수 있어요.", 넣거나 빼면 오류 지움), 다음(`canSubmit = !isSaving ∧ 선택 ≥ 1`, `clientAt = max(now, previousClientAt + 1ms)`, 진행 중 저장 취소), 건너뛰기 · 나중에(저장 중 무시). 성공 → 선택 = 서버 목록, `saved(festivalId, setIds)`. `stale(server)` → 선택 = 서버 목록 + "다른 기기에서 바뀐 일정을 불러왔어요. 확인하고 다시 눌러 주세요.", 그 밖 → "내 일정을 저장하지 못했어요. 다시 시도해 주세요."

진입 값(`TF:422-430`): 축제 ID · 날짜 · 현재 내 일정 · 마지막 저장 시각. 준비 안내는 서버에서 읽은 일정으로 연다(`FestivalCoordinator.swift:290-333`).

---

## 서버 계약

stage `https://festicore-dev.up.railway.app/`에서 인증 없이 GET만 확인했다(2026-11-22).

| 요청                                                                                      | 인증                              | 확인 결과                                                                                                                                                                                                                                                                                                                                                                                                                                         |
| ----------------------------------------------------------------------------------------- | --------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `GET api/v1/festivals/fst_00e0f4f1d8065a93`                                               | 없음                              | 200(35,041B). `timetable` 42 · `sets` 42(필드 `id,stageId,artist,startsAt,endsAt,imageUrl,note`, `timetable`에는 `date,stageName` 추가) · `stages` 2(`id,name,order`) · `days` 3(`date,startsAt,endsAt`) · `artists` 42(`name,imageUrl` 상대 경로) · `source{name:"공개 수집 카탈로그",url,verifiedAt:"2026-11-01T03:08:48Z"}` · `timetableStatus "available"` · `timezone "Asia/Seoul"`. 날짜별 14공연, 12:00~22:00(+09:00), 자정 넘김 0, 메모 0 |
| `GET api/v1/festivals/fst_c39663182b10a105`                                               | 없음                              | 200. `timetableStatus "pending"`, 공연 0 · 무대 0 · `days` 0 · `artists` 20 → 공개 전 · 날짜 고르기 없음                                                                                                                                                                                                                                                                                                                                          |
| `GET api/v1/festivals/{fst_eacde26f05a55f27, fst_7c88336ea871e178, fst_6fdcc4a6be32df3a}` | 없음                              | 무대 1 · 2 · 1, 날짜 1 · 1 · 2. 무대 3개 이상(넓은 보드) · 4일 이상(칩 줄) · 자정 넘김 축제는 stage에서 찾지 못했다                                                                                                                                                                                                                                                                                                                               |
| `GET api/v1/me/schedule/fst_00e0f4f1d8065a93`                                             | Bearer                            | 비로그인 401 `{"code":"UNAUTHORIZED","data":null,"message":"인증 토큰이 없습니다."}`                                                                                                                                                                                                                                                                                                                                                              |
| `PUT api/v1/me/schedule/{id}`                                                             | Bearer                            | 쓰기라 호출하지 않음                                                                                                                                                                                                                                                                                                                                                                                                                              |
| `GET api/v1/devices/notification-settings`                                                | 기기(`X-Device-Id` + 선택 Bearer) | 헤더 없이 401, 같은 본문                                                                                                                                                                                                                                                                                                                                                                                                                          |
| `PUT api/v1/devices/notification-settings`                                                | 기기                              | 쓰기라 호출하지 않음                                                                                                                                                                                                                                                                                                                                                                                                                              |

`timetableStatus` · `timezone`은 iOS가 읽지 않는다(`iOS/Projects` 검색 0건). Android도 읽지 않는다.

iOS 요청 정의:

- 읽기 `GET api/v1/me/schedule/{id}` → `BaseResponse{setIds:[String]}`(`FSS:95-105`, `iOS/Domain/FestivalDomain/Sources/Model/DTO/FestivalScheduleResponseDTO.swift:11-15`). Android 이미 있음.
- 저장 `PUT api/v1/me/schedule/{id}` 본문 `{"setIds":[…],"clientAt":"…"}`(`FSS:107-128`). `clientAt`은 서울 오프셋 + 소수 3자리 ISO(`ADF:57-63 · 84-86`, 예 `2026-11-22T15:01:26.123+09:00`). Android: `DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX")` + `ZoneId.of("Asia/Seoul")`.
- 축제 ID 검증: 비었거나 `[0-9A-Za-z_-]` 밖이면 `rejected`(`FSS:68-75`, Android `isValidFestivalId` 이미 있음).
- 오류(`FSS:82-92 · 47-54`): 409 + `code == "STATUS_STALE"`면 서버 일정을 다시 읽어 `stale(server)`, 401 `unauthorized`, 400 `rejected`, 그 밖(429 포함) · 연결 `unavailable`, `code != "SUCCESS"` `unavailable`(`FSS:78`).
- Android: `FestivalApi.replaceMySchedule(festivalId, body, expectedSessionId)`(`festiCoreRequest` + `bearerAuthorization`), 실패는 `NetworkError.Http(status, code, message)`(`network-storage.md` 표)로 와서 409 · 코드를 그대로 볼 수 있다. 세션 없음은 `AuthSessionError` → `Unauthorized`(`FestivalLiveBoardErrorMapping.kt:44-58` 기존 매핑에 409 분기만 추가).

## 오프라인 · 비로그인 · 캐시

| 경우               | 동작                                                                                                                                  | 근거                                  |
| ------------------ | ------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------- |
| 오프라인 · 팩 있음 | 받은 팩으로 격자 그대로 + 위 오프라인 안내. 내 일정 읽기 실패 → `localOnly`(토글은 이 기기에서만, 고르기 버튼 비활성)                 | `MTCSy:110-123`, `TFS:146-148`        |
| 오프라인 · 팩 없음 | 온라인 상세도 없으면 `failed` + 다시 시도                                                                                             | `MTCSy:104-109`                       |
| 저장 중 끊김       | `unavailable` → 불확실 저장 보관, 다음 탭 진입 때 같은 요청 재전송(목록이 같을 때)                                                    | `TFS:118-124 · 177-179`, `TF:449-452` |
| 비로그인           | 읽기 `unauthorized` → `localOnly`. 토글 가능(기기 표시만), 고르기 · 서버 저장 없음. 시트 도움말은 "공연 10분 전에 알려 드려요" 그대로 | `TFS:80 · 146-148`, `TF:112-114`      |
| 저장 위치          | 내 일정은 서버만. 기기 저장 없음(iOS `TT` · `FSS` 전체에 로컬 저장 없음). 팩은 기존 `PackCache` 파일                                  |                                       |

---

## 화면 전환

| 전환             | iOS                                                                         | 근거                                | Android                                                                                                               |
| ---------------- | --------------------------------------------------------------------------- | ----------------------------------- | --------------------------------------------------------------------------------------------------------------------- |
| 탭 루트          | `TCo` 루트 `.timetable`(네비게이션 바 숨김)                                 | `TCo:15-29`, `TCoV:15-20`           | `TimetableFlow` `NavDisplay(routes)` 첫 항목 `TimetableRoute.Timetable`                                               |
| 검색 · 변경      | `setList` push(고르기 열림이면 무시), 스와이프 뒤로 숨김                    | `TCo:97-111`, `TCoV:21-23`          | `push(SetList(mode))`, 상단 뒤로 · 시스템 뒤로 = `goBack`                                                             |
| 검색 결과 → 시트 | 목록 닫고 `setTapped(id)`                                                   | `TCo:119-125`                       | `goBack` + 대기 값 `pendingSetId` → 타임테이블 Screen이 `onSetClicked` 후 소비(보드 E8과 같은 방식)                   |
| 고르기           | `TF.destination` → 경로 push, 스와이프 뒤로 숨김. 경로에서 빠지면 `dismiss` | `TCo:22-24 · 79-86`, `TCoV:24-26`   | `push(SchedulePick(...))`, 시스템 뒤로 = pop = 결과 없음(skipped와 같음, `TF:626-627`)                                |
| 고르기 결과      | `saved` → 닫고 내 일정 갱신 + 상위 저장 알림                                | `TF:599-625`, `TCo:88-95`           | Coordinator pop + 대기 값 `pendingPickResult` → 타임테이블 ViewModel(T8)                                              |
| 다시 시도        | 상위 → 보드 `onlineDetailOnAppear`                                          | `TCo:113-114`, `MTCSc:156-163`      | 공유 상태(T1)에 재시도 요청 → 보드가 상세 다시 읽기                                                                   |
| 시트             | 탭 화면 위 `fcModal`                                                        | `MTV:67-79`                         | `TimetableScreen` 안 `FCSheet(item = state.setDetail)`(창 위라 탭 바까지 덮음)                                        |
| 공연 알림 딥링크 | 축제 고르기 → 타임테이블 탭 → `setTapped`                                   | `MTC:371-377`                       | 푸시 · 딥링크가 없어 경계만(T9). `TimetableViewModel.onSetClicked`가 진입점                                           |
| 준비 안내 1단계  | 라이브 스택에 `prepSchedule(SchedulePickFeature)` push                      | `FestivalCoordinator.swift:329-333` | PrepGuide PR에서 app이 `SchedulePickScreen`을 `FestivalFlow` 슬롯으로 넘긴다(`architecture.md` `feature/web` 행 선례) |

전환 애니메이션: iOS는 라이브 탭과 같은 TCAFlow push. Android `FestivalTransitions`(`feature/festival/.../FestivalTransitions.kt:16-22`, `internal`, 350ms)와 같은 값을 `TimetableTransitions`로 둔다(T12). 화면은 자기 `BackHandler`를 두지 않는다(`navigation.md:105`).

## 탭 바 규칙

iOS(`MTC:101-104`): 타임테이블 탭은 고르기 · 시트 · 검색/변경 목록이 떠 있으면 탭 바를 숨긴다. 자리 예약(`MTC:64-75`)은 Android에 해당 문제가 없어 옮기지 않는다(live-extras "탭 바 표시"와 같음).

Android:

```
TimetableCoordinatorState.hidesTabBar =
    routes.size > 1                // SetList · SchedulePick
    || isSetDetailPresented        // 시트(Screen이 알림)
```

`MainTabScreen`은 `timetableContent(onHidesTabBarChanged)` 슬롯을 받아 `hidesTimetableTabBar`(기본 false)를 두고 `selection == Timetable && hidesTimetableTabBar`면 탭 바를 그리지 않는다(`MTS:40 · 51`와 같은 방식). 시트는 창 위라 숨기지 않아도 가려지지만 iOS와 같게 숨긴다.

탭 수명: `MainTabScreen`은 고른 탭만 그리고 나머지는 상태 저장만 한다(`MTS:44-49`). `TimetableCoordinator` · `TimetableViewModel`은 `FestivalFlow`처럼 메인 탭 항목 범위 `koinViewModel()`로 두어 탭을 바꿔도 남는다(`FFl:20-27 · 33-34`). 탭을 고를 때마다 흐름이 다시 그려지므로 `LaunchedEffect(Unit)`에서 `onTimetableAppeared`를 부른다(`FFl:43-44` 선례, iOS `MTC:337-341`).

## 로컬 알림 · 권한 (결정 T4)

**iOS에는 공연 10분 전 로컬 알림이 없다.** `UNTimeIntervalNotificationTrigger` · `UNCalendarNotificationTrigger` · `UNNotificationRequest(` 검색 0건, `UNUserNotificationCenter`는 권한 읽기 · 요청과 푸시 수신 대리자뿐이다(`iOS/App/Sources/Push/NotificationAuthorizationClient.swift:25-28`, `iOS/App/Sources/Application/AppDelegate+Push.swift:17 · 59-70`). "공연 10분 전" 알림은 서버가 저장된 내 일정으로 보내는 푸시다(`SDV:66` 주석 "서버가 저장한 공연을 시작 10분 전에 알린다", 알림 종류 `setReminders` `iOS/Domain/FestivalDomain/Interface/Sources/Entity/FestivalNotificationSettings.swift:43-51`, 푸시가 여는 딥링크 `festicore://festival/{id}/set/{setId}` `iOS/Core/FestiCoreUtility/Sources/Deeplink/FestiCoreDeeplink.swift:18-22 · 35-39`, 처리 `MTC:371-377`).

타임테이블 탭이 직접 하는 알림 작업은 공개 전 화면의 "알림 받기" 하나다: 서버 기기 알림 설정에서 `packUpdates`만 켜고(`TF:499-511`), 기기 권한은 **읽기만** 한다(요청하지 않음). 서버 설정은 기기 등록이 필요하다(`iOS/Service/FestiCoreDevice/Sources/NetworkDeviceNotificationSettingsService.swift:30-40 · 54-75`: 등록 없으면 `deviceNotRegistered` → 화면 `failed`).

| 항목                  | iOS                                                                            | Android 안                                                                                                                                                            | 비고                                                                                                                                                                                                                                            |
| --------------------- | ------------------------------------------------------------------------------ | --------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 공연 10분 전          | 서버 푸시(APNs)                                                                | (a) 서버 푸시(FCM) — 별도 푸시 · 기기 등록 문서(추천) / (b) `AlarmManager.setExactAndAllowWhileIdle` 로컬 예약 / (c) `WorkManager` `OneTimeWorkRequest(initialDelay)` | (b) Android 12+ 정확 알람은 `SCHEDULE_EXACT_ALARM`(사용자 허용) 또는 `USE_EXACT_ALARM`(정책 제한), 재부팅 시 `BOOT_COMPLETED` 재예약, 내 일정 · 팩 변경마다 예약 동기화, 푸시가 생기면 중복. (c) 지연 · 묶음 실행이라 "10분 전"을 보장하지 못함 |
| 알림 권한             | 권한 상태 읽기(`permission()`), 요청은 다른 흐름                               | `NotificationManagerCompat.areNotificationsEnabled()` 읽기만. API 33+ `POST_NOTIFICATIONS` 런타임 요청은 푸시 문서에서(매니페스트 선언 포함)                          | 지금 매니페스트에 없음                                                                                                                                                                                                                          |
| 팩 업데이트 알림 켜기 | `GET` → 필요하면 `PUT api/v1/devices/notification-settings {packUpdates:true}` | 같은 API를 `service/device`에 추가. 기기 등록 전에는 `DeviceNotRegistered` → `failed` 문구(iOS와 같은 결과)                                                           | 기기 등록(`api/v1/devices`, iOS 본문 `apnsToken` `NetworkDeviceRegistrationService.swift:325-352`)의 Android 필드는 확인 못 함                                                                                                                  |
| 알림 채널             | 없음(iOS)                                                                      | 푸시 문서에서 채널 정의                                                                                                                                               | `ios-parity.md` "다르게 두는 것"                                                                                                                                                                                                                |
| 알림 → 공연 상세      | 딥링크 `set`                                                                   | 푸시 문서에서 `MainActivity` 인텐트 → 타임테이블 탭 + `onSetClicked`                                                                                                  | 진입 함수는 이 문서에서 둔다                                                                                                                                                                                                                    |

## iOS → Android 적응

| 항목                              | iOS                                                                                   | Android                                                                                         | 이유                                                                        |
| --------------------------------- | ------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------- |
| 화면 상태 소유                    | `TimetableFeature` + 자식 `SetDetailFeature`, 부모가 시트 내용을 밀어 넣음            | `TimetableViewModel` 하나가 시트 상태까지(`setDetail` 필드), 시트는 `Content` 순수 UI           | 시트 내용이 전부 부모 계산(`SDF:12`), 항목 ViewModel끼리 호출 경로 없음(T7) |
| 고르기                            | `destination`(부모 상태 안) → Coordinator가 경로로 그림                               | Coordinator 경로 + 별도 `SchedulePickViewModel`, 결과는 Coordinator 대기 값                     | PrepGuide가 같은 화면을 라이브 스택에서 쓰므로 독립 화면이 맞다(T8)         |
| 타임테이블 입력(선택 축제 · 팩)   | 부모가 형제 상태(보드)를 직접 읽어 `packSynced`                                       | 보드가 공유 `StateFlow`에 쓰고 타임테이블이 구독(T1)                                            | 보드 ViewModel이 다른 탭에서 보이지 않음                                    |
| 저장 알림 → 보드                  | `festival(.myScheduleSaved)`                                                          | 도메인 이벤트 `Flow`(T2)                                                                        | 같은 이유                                                                   |
| 격자                              | `ScrollView([.horizontal,.vertical])` + `LazyVStack(pinnedViews)` + `ZStack/offset`   | 고정 높이 `Box` 절대 배치 + `verticalScroll`/`horizontalScroll`, 머리글은 밖에서 가로 값 따라감 | 위 "격자 렌더링"(T5)                                                        |
| 지금 시각 위치로 스크롤           | `ScrollViewReader.scrollTo(hour, .top)`                                               | `ScrollState.scrollTo(px)` 계산                                                                 | Compose 앵커 스크롤 없음                                                    |
| 확대                              | `MagnifyGesture` + `@GestureState`                                                    | `pointerInput` 2손가락 `calculateZoom`                                                          | 스크롤과 공존(T6)                                                           |
| 날짜 스와이프                     | `simultaneousGesture(DragGesture)`                                                    | `pointerInput`(Initial, 소비 없음)                                                              | 같은 판정 함수                                                              |
| 칩 줄 가운데                      | `ScrollViewReader.scrollTo(anchor: .center)`                                          | `LazyRow` + `animateScrollToItem(i, -(뷰포트 − 항목)/2)`                                        |                                                                             |
| 1초 시계 · 매분 시트              | `clock.timer` · `TimelineView(.everyMinute)`                                          | ViewModel 루프(주입 `Clock`), 시트도 같은 `nowMinute`로 계산                                    | 보드 30초 선례                                                              |
| 시트                              | `fcModal(height: .auto) + accessory`                                                  | `FCSheet(item, accessory)`                                                                      | designkit 기존                                                              |
| 토스트 2초                        | `clock.sleep(2)` 취소 가능                                                            | ViewModel 작업(`Job` 교체), `FCToast` accessory                                                 |                                                                             |
| 아래 바 흐림                      | `.ultraThinMaterial` + `materialGlass`                                                | `materialGlass` 단색                                                                            | Compose 표준 흐림 없음(live-extras와 같음)                                  |
| 스와이프 뒤로 숨김(목록 · 고르기) | `swipeBackButtonHidden()`                                                             | 시스템 뒤로 그대로 = pop                                                                        | Android 표준 뒤로(보드 D7)                                                  |
| 날짜 형식                         | `DateFormatter` ko_KR `M/d(E)` · en_US_POSIX `MM/dd HH:mm` · ISO `.SSSXXX` 서울       | `java.time` `DateTimeFormatter` 같은 패턴 · 같은 로케일 · `Asia/Seoul`                          | minSdk 26                                                                   |
| 로컬 알림                         | 없음(서버 푸시)                                                                       | 없음(T4). 권한 읽기만                                                                           | 위 표                                                                       |
| 분석                              | `screenView(.timetable)` · `timetableAction(daySelect/mineOnlyToggle/scheduleToggle)` | 없음                                                                                            | `service/analytics` 미구현(T10)                                             |

## PR 묶음 (데이터 → 그리드 → 내 일정/고르기 → 알림 → 연결)

| PR                | 내용                                                                                                                                                                                               | 검증                                                                                                                                                                                                                                                                                                                                                                                              |
| ----------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| D1 시간표 도메인  | `domain/timetable` interface(엔티티 · 문구) · sources(`FestivalPackTimetableMapper` · 겹침 계산 · `di/`)                                                                                           | `TimetableEntityTests` 8건, 매퍼(`mapperOnlyMarksChangesWhenPreviousPerformanceExists` · `mapperPassesPerformanceNoteToSet` · `unpublishedLineupDoesNotInventArtistDates` · `unpublishedLineupResolvesArtistImage`), stage 상세 축약 JSON → 3일 14공연 · 무대 순서 · 서울 자정                                                                                                                    |
| D2 내 일정 저장   | `service/api` PUT · 본문, `service/festivalcatalog` `replace`(409 재조회), `domain/festival` `Stale` · `saveMySchedule` · 저장 이벤트(T2) · 보드 선택 공유 상태(T1)                                | MockEngine: 성공 · 409 `STATUS_STALE` · 400 · 401 · 429 · `code != SUCCESS` · 잘못된 ID · `clientAt` 형식                                                                                                                                                                                                                                                                                         |
| G1 격자 화면      | `feature/timetable` 모듈 · `TimetableContract` · `TimetableViewModel`(팩 입력 · 날짜 · 필터 · 확대 · 시계 · 상태 판정) · Screen/Content · `TimetableBoard` · 공개 전 · 스켈레톤 · 빈/실패 · 프리뷰 | `TimetableFeatureTests` 중 화면 · 시계 · 팩 동기화 사례(`clockRefreshes…` · `currentTimeContinuesThroughCrossMidnightSet` · `contentStatusDoesNotInferPublication…` · `outOfRangeDayIsIgnored` · `swipingPastFirstDayIsIgnored` · `daySwipeNeedsMostlyHorizontalDrag` · `pinchZoomIsKeptAcrossDayChanges` · `changedSetsAreCounted` · `unpublishedStateShowsLineup` · `packSync…` 2건 등), 프리뷰 |
| S1 시트 · 목록    | sharedui `FCSetSheet` + 프리뷰, 토스트 프리셋, 시트 상태 · 토글 · 토스트, `SetList` 화면                                                                                                           | `TimetableScheduleTests` 시트 · 토스트 사례 · `setListSearchAndChangesUseActualPerformances` · `tappingSetOpensDetailWithoutChangingSchedule` · `detailStatusFollowsCurrentTime`                                                                                                                                                                                                                  |
| S2 내 일정 동기화 | 읽기 · 저장 · 불확실 · stale · 한도 · 계정 변경 · 축제 변경 · 팩 갱신 정리                                                                                                                         | `TimetableScheduleTests` 나머지(30건 전체 완료)                                                                                                                                                                                                                                                                                                                                                   |
| S3 내 일정 고르기 | `TimetableRoute.SchedulePick` · `SchedulePickViewModel` · Screen, 결과 반영                                                                                                                        | `SchedulePickFeatureTests` 9건 · `TimetableSchedulePickTests` 13건                                                                                                                                                                                                                                                                                                                                |
| A1 팩 알림 켜기   | `service/device` 알림 설정 GET/PUT(기기 인증) · `FestivalNotificationSettingsUseCase` · 권한 읽기                                                                                                  | `enablingPackNotificationsOnlyChangesPackUpdates` · `enabledSubscriptionStillExplainsMissingDevicePermission`, MockEngine(기기 미등록 → failed) · **게이트: P0 머지 뒤에만 머지** |
| C1 탭 연결        | `TimetableCoordinator` · `TimetableFlow`, `MainTabScreen` 슬롯 · 탭 바, 보드 → 공유 상태 쓰기 · 재시도 · 저장 이벤트 받기, Koin 목록                                                               | `TimetableRoutingTests` 라우팅 사례(`searchPushHidesTabBarAndBackRestoresIt` · `schedulePick…` 6건 · `selectedFestival…` 2건), `TimetableCoordinatorTest`, stage 수동 확인(수용 기준 1~7) · **게이트: P0 머지 뒤에만 머지** |

순서: D1 → D2 → G1 → S1 → S2 → S3 → **P0(푸시 · 기기 등록, 별도 설계)** → A1 → C1. A1 · C1과 PrepGuide 연결(live-extras P2 이후)은 **P0이 develop에 머지된 뒤에만** 머지한다(각 PR 검증 기준에 "P0 머지 확인" 포함). 푸시(10분 전 · FCM · 채널 · `POST_NOTIFICATIONS` 요청 · 딥링크)는 P0 문서가 맡는다. PrepGuide 1단계 연결은 live-extras P2에서 이 문서의 S3 화면을 쓴다. 각 PR은 `verify --all` · CI 통과 후 squash merge.

### 기능 spec (`.spec/`)

| PR           | spec 파일                                                                                                              | 담는 내용                                                      |
| ------------ | ---------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------- |
| D1           | `domain/timetable/.spec/timetable-mapping.md`                                                                          | 날짜 · 무대 순서 · 분 · 변경 · 라인업 · 출처                   |
| D2           | `domain/festival/.spec/my-schedule.md`                                                                                 | 읽기 · 저장 · 오류 · `clientAt` · 공유 선택 상태 · 저장 이벤트 |
| G1 · S1 · S2 | `feature/timetable/.spec/timetable-screen.md`                                                                          | 상태 판정 · 격자 · 겹침 · 시트 · 동기화 규칙                   |
| S3           | `feature/timetable/.spec/schedule-pick-screen.md`                                                                      |                                                                |
| C1           | `feature/timetable/.spec/timetable-screen.md` 갱신, `feature/festival/.spec/live-board-screen.md` 갱신(공유 상태 쓰기) | 전환 · 탭 바                                                   |

## 새 라이브러리 후보

| 후보                                                                                                                                                      | 용도               | 라이선스             | 판단                                     |
| --------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------ | -------------------- | ---------------------------------------- |
| 없음(Compose foundation `verticalScroll` · `horizontalScroll` · `pointerInput` · `LazyRow`, BOM `2026.09.00` `gradle/libs.versions.toml:11`, `java.time`) | 격자 · 확대 · 날짜 | Apache-2.0(AndroidX) | 추천                                     |
| Compose `Modifier.transformable`(foundation 포함)                                                                                                         | 확대               | Apache-2.0           | 쓰지 않음(T6)                            |
| 시간표 · 캘린더 라이브러리(예: 서드파티 schedule view)                                                                                                    | 격자               | 확인 안 함           | 불필요. 블록 수가 적고 iOS도 직접 그린다 |
| `androidx.work:work-runtime`                                                                                                                              | 로컬 알림          | Apache-2.0           | 쓰지 않음(T4, iOS에 로컬 알림 없음)      |
| Firebase Cloud Messaging                                                                                                                                  | 10분 전 푸시       | Firebase 약관        | 이 문서 밖(푸시 문서)                    |

새 의존성 없음. 라이선스 승인 대상 없음.

## 위험

1. **공유 선택 상태(T1)가 보드 동작에 손을 댄다**: 보드 ViewModel이 쓰기만 추가하지만 보드 수명(스택 항목 범위, `FFl:24`)이 끝나면 값을 비울지 남길지 정해야 한다. iOS는 보드 경로가 없으면 `noFestival`(`MTCSy:79-80 · 100-101`)이므로 보드가 pop되면 비운다.
2. **보드가 다른 탭에서도 살아 있는지**: 탭을 바꾸면 `FestivalFlow`가 컴포지션에서 빠진다(`MTS:44-49`). 스택 항목 ViewModel이 그때도 남는지(=공유 상태를 계속 갱신하는지) 확인하지 않았다. 남지 않으면 타임테이블 탭에서는 마지막 값으로 보이고 연결 상태 · 팩 갱신이 멈춘다 — C1에서 기기로 확인한다.
3. **격자 스크롤 · 확대 제스처**: 가로 · 세로 · 2손가락 · 날짜 스와이프가 한 영역에 겹친다. 기기에서 스크롤 끊김이 없는지 G1에서 확인.
4. **넓은 보드 · 칩 줄 · 자정 넘김 실데이터 없음**: stage에 무대 3개 이상 · 4일 이상 · 자정 넘김 축제가 없어 테스트 자료로만 확인한다.
5. **저장 미검증**: PUT · 409 응답 본문을 stage에서 보지 못했다. iOS 코드만 근거다.
6. **10분 전 알림 부재**: 해소 — 알림은 iOS처럼 서버 푸시이고, 푸시 · 기기 등록(P0)이 머지되기 전에는 이 탭(C1)과 PrepGuide 연결을 develop에 넣지 않아 "공연 10분 전에 알려 드려요" 문구가 알림 없이 노출되지 않는다(결정 절).

## 쟁점 (추천안 포함)

- T1. 보드 선택 → 타임테이블 입력: (a) `domain/festival`에 `FestivalBoardSelection`(선택 축제 ID · 표시 문서 · 온라인 상세 로딩/실패 · 팩 감시 실패 · 오프라인 여부 · 팩 `checkedAt`) `StateFlow`와 재시도 요청을 두고, 보드 ViewModel이 쓰고 타임테이블 ViewModel이 구독(추천, live-extras E7 공유 저장소 방식). (b) `FestivalFlow` 콜백 → `MainTabScreen` → 타임테이블 슬롯 인자로 중계 — 탭이 그려지지 않을 때 값 보관 자리가 Compose 상태뿐. (c) 타임테이블이 저장된 선택 축제 ID(`FestivalLiveBoardPreferenceUseCase.savedFestivalId`) · `FestivalPackUseCase.state` · 자체 상세 조회로 독립 계산 — iOS와 상태가 어긋난다(보드에서 아직 저장 전인 선택, 보드 연결 상태).
- T2. 타임테이블 저장 → 보드 내 일정: (a) `FestivalScheduleUseCase`에 `savedSchedules: Flow<FestivalScheduleSaved(festivalId, setIds)>`를 두고 저장 성공 · stale 채택 · 고르기 저장 때 보내며 보드가 같은 축제면 `mySchedule` 교체(추천, iOS `myScheduleSaved`와 같은 이벤트). (b) 보드가 다시 보일 때 재조회에만 맡김 — 탭 전환 시 보드 ViewModel 수명(위험 2)에 달려 있다.
- T3. 매퍼 위치: (a) `domain/timetable/sources`(추천, 테스트 가능 · app에 로직 없음). (b) `app`(iOS 위치 그대로) — `.claude/rules/app.md` 취지와 어긋남. (c) `domain/festival` — 시간표 엔티티가 festival에 섞인다.
- T4. 공연 10분 전 알림: (a) iOS와 같게 로컬 알림을 만들지 않고 푸시 문서로 넘김, 문구는 iOS 그대로(추천). (b) Android만 `AlarmManager` 로컬 예약 — 정확 알람 권한 · 재부팅 재예약 · 이후 푸시와 중복. (c) `WorkManager` — 시각 보장 불가. 문구: (a′) 그대로(추천, 푸시 도입 전 차이를 PR에 적음) / (b′) 푸시 전까지 도움말을 바꿈 — iOS와 달라짐. → **결정**: (a) + (a′), 단 P0 게이트로 출시 빌드에서 문구와 실제 알림이 어긋나지 않게 한다(결정 절).
- T5. 격자 그리기: (a) 고정 높이 `Box` 절대 배치 + `ScrollState` 2개, 머리글은 가로 값만 따라감(추천). (b) `LazyColumn` + `stickyHeader` — 블록이 시 줄을 가로질러 겹쳐 그려져 행 단위 Lazy와 맞지 않음. (c) 직접 `Layout`/`Canvas` — 접근성 · 클릭을 다시 만들어야 함.
- T6. 확대: (a) `pointerInput` 2손가락 전용(추천). (b) `transformable` — 한 손가락 이동이 스크롤과 다툼(기기 확인 전 가정).
- T7. 시트 상태: (a) `TimetableViewModel` 안 필드(추천, iOS도 내용을 부모가 계산). (b) 시트 전용 ViewModel — 내 일정 · 겹침을 다시 받아야 함.
- T8. 고르기 경로 값: (a) `SchedulePick(festivalId, selectedSetIds: List<String>, previousClientAtEpochMillis: Long?)`, 날짜는 화면이 T1 공유 팩 + D1 매퍼로 다시 계산, 결과는 Coordinator `pendingPickResult` → 타임테이블 Screen이 소비(추천, 최대 60 ID라 경로 값이 작음 · PrepGuide도 같은 경로). (b) 날짜까지 경로에 — `navigation.md` "큰 객체는 다시 조회"와 어긋남. (c) 공유 메모리 홀더로 넘김.
- T9. 공연 알림 딥링크: (a) 진입 함수(`onSetClicked`)만 두고 연결은 푸시 문서(추천). (b) 지금 인텐트 처리까지 — 보낼 주체(푸시)가 없다.
- T10. 분석 이벤트: (a) 빼고 `service/analytics` 도입 때 같은 자리에 추가(추천, 테스트 2건 보류). (b) 빈 인터페이스를 먼저 둠 — 쓰지 않는 추상화.
- T11. 조사 규칙 불일치: iOS 고르기는 한글이 아닌 이름에 "과"(`SPV:154-157`), 시트 경고는 "와"(`TD/TimetableSetDetail.swift:115-121`, NFC 정규화 포함). (a) 두 규칙을 각자 그대로 옮기고 iOS 이슈로 남김(추천, 테스트 `particleFollowsLastHangulSyllable` · `overlapWarningUsesParticleForNonHangulName`이 각각 고정). (b) 하나로 통일 — iOS와 달라짐.
- T12. 전환 값: (a) `FestivalTransitions`와 같은 값을 `TimetableTransitions`로 두고 상수 테스트(추천, `feature/festival` 무변경). (b) `ui/designkit`으로 옮겨 공유 — 라이브 탭 파일까지 바뀜.
- T13. 시트가 열릴 때 탭 바: (a) iOS처럼 숨김(추천). (b) 창 위 시트라 그대로 둠 — 시트를 닫는 순간 탭 바가 다시 나타나는 차이가 없어짐 대신 iOS와 달라짐.

**결정 사항(iOS 값 그대로)**

- 한도 60 · 시계 1초 · 토스트 2초 · 확대 0.5~2 · 스와이프 30/80/1.5 · 넓은 보드 무대 > 2 · 세그먼트 3일 이하 · `clientAt` +1ms · 블록 최소 8 · 투명도 0.4/0.2.
- 경로: `TimetableRoute.Timetable`(값 없음) · `SetList(mode: Search/Changes)` · `SchedulePick(...)`(T8).
- 접근성 식별자: iOS에 없다. 스켈레톤 · 격자에 `testTag`를 둘지는 G1에서 정한다(보드 `FestivalAccessibilityId.kt` 선례). 접근성 라벨은 위 표의 iOS 문구 그대로("검색" · "내 일정 고르기" · "내 일정 N분 겹침" · 토글 "내 일정에 넣기").

## 확인 못 한 것

- 로그인 응답: `GET api/v1/me/schedule/{id}` 성공 본문, `PUT` 성공 · 409 `STATUS_STALE` · 400 본문. stage는 비로그인 401만 봤다.
- iOS `FestiCoreEndpoint` 기본 재시도 3회(`iOS/Core/FestiCoreNetwork/Interface/Endpoint/FestiCoreEndpoint.swift:52-54`)가 PUT 저장에도 적용되는지(재시도 정책 구현을 열지 않음). Android `core/network`는 재시도가 없다고 보고 있으나 D2에서 다시 확인.
- 무대 3개 이상 · 날짜 4일 이상 · 자정 넘김 · 메모 있는 공연 · 시간 변경(`previousStart`) 실데이터. stage 31개 중 5개만 열었다.
- WebSocket 팩 본문에 `timetable`/`sets` · `artists` · `source`가 상세(HTTP)와 같은지(live-extras 위험 1과 같은 성격).
- 탭을 바꾼 뒤에도 보드 스택 항목 ViewModel이 남는지(위험 2).
- Compose `pointerInput` 2손가락 확대와 `verticalScroll` · `horizontalScroll` 공존, 머리글 가로 동기화의 실제 동작 — G1에서 기기로 확인.
- Android 기기 등록 API 본문(iOS `apnsToken`의 FCM 대응 필드)과 `notification-settings`가 FCM 기기도 받는지.
- iOS `FCSetSheet`의 제목 줄 · 무대/시각 줄 · 경고 · 메모 상자 세부 수치(`FCS:150-284`)는 이 문서에 옮기지 않았다. S1에서 연다.
- iOS 타임테이블 스냅샷 · 시각 테스트 유무(`App/Tests` 전체를 보지 않았다).

## 결정 (호스트, 2026-11-22 — 사용자 지시 "묻지 말고 iOS와 같게")

- T1~T13: 추천안대로. 위험 2(탭을 바꾸면 보드 ViewModel이 사라져 T1 공유 상태가 멈춤)는 공유 선택 값을 보드 ViewModel이 아니라 `domain/festival` 저장소(앱 범위, 선택 축제 저장 키와 같은 원천)에 두어 피한다. 보드는 쓰기만, 타임테이블은 읽기만 한다.
- 10분 전 알림은 iOS처럼 서버 푸시이므로 로컬 알림을 만들지 않는다. 푸시(FCM) · 기기 등록은 라이브 액티비티 단계에서 함께 다룬다.
- (Codex R1 TT-01 · TT-02 반영) 알림은 iOS와 같게 서버 푸시로 제공하고, 그 전제인 푸시 · 기기 등록을 이 탭 출시 조건으로 묶는다.
  - 새 선행 PR **P0 푸시 기반**(별도 설계 문서 `2026-11-22-push-device-android-design.md`, 라이브 액티비티 단계 문서와 공유): FCM 토큰 · 서버 기기 등록(iOS `apnsToken`에 대응하는 필드를 iOS 기기 등록 코드와 stage 계약으로 확인) · POST_NOTIFICATIONS 권한 · 딥링크 `festicore://festival/{id}/set/{setId}` 처리.
  - Firebase 프로젝트 설정 파일(`google-services.json`)은 추적 파일에 넣지 않고 CI 비밀 · 로컬로 받는다(CLAUDE.md Hard 8). 사용자 조치 항목으로 둔다.
  - A1(알림 받기)은 P0 뒤에 둔다. P0이 들어오기 전 빌드에서 "알림 받기" · "공연 10분 전에 알려 드려요" 문구는 iOS 미등록 상태와 같은 실패 · 안내 문구로 처리하지 않고, **P0이 머지되기 전에는 C1(탭 연결)을 develop에 넣지 않는다** — 사용자가 알림을 약속받고도 못 받는 상태가 출시 빌드에 생기지 않게 한다.
  - PR 순서: D1 → D2 → G1 → S1 → S2 → S3 → P0 → A1 → C1.
  - PrepGuide(live-extras P1~P3)도 S3 SchedulePickScreen을 쓰므로 같은 게이트를 따른다: P0이 머지되기 전에는 PrepGuide 연결을 develop에 넣지 않는다(live-extras 문서 결정 E10에도 기록).
- (Codex R4 TT-05 반영) **SchedulePick 결과 공통 계약**: iOS `SchedulePickDelegate`(`Feature/Timetable/Interface/Sources/SchedulePickDelegate.swift`)와 같게 `feature/timetable/interface`에 `sealed interface SchedulePickResult { data class Saved(festivalId, setIds); data object Skipped; data object Later }`를 두고, `SchedulePickScreen(route, onResult: (SchedulePickResult) -> Unit)`로 호출자에게 돌려준다(S3).
  - 타임테이블 Coordinator: `Saved` → pop + 격자 · 보드 내 일정 갱신(T8의 대기 값), `Skipped` · `Later`는 타임테이블 진입에서는 쓰지 않는다(iOS 타임테이블 경로와 같음).
  - Festival Coordinator(PrepGuide, live-extras P2): iOS `FestivalCoordinator.swift:336-404`와 같게 `Saved` → 2단계(깃발) push + 저장 결과를 보드 · 타임테이블에 알림(`prepScheduleSaved`), `Skipped` → 2단계 push(저장 없음), `Later` → 준비 안내 경로 전체를 스택에서 뺀다.
  - feature/festival은 feature/timetable의 interface만 의존하고 화면은 app이 슬롯으로 넘긴다(약관 웹 화면과 같은 방식).
