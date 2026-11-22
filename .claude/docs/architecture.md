# 아키텍처

현재 Gradle 설정과 소스를 기준으로 작업한다. 문서의 예시보다 실제 `settings.gradle.kts` · `build.gradle.kts`가 우선한다.

## 앱

- 앱 ID: `io.festiCore.co`(prod), `io.festiCore.co.stage`(stage). iOS 번들 ID와 같은 값을 쓴다.
- `stage` · `prod` product flavor 두 개만 둔다. iOS `Config/` Stage · Prod와 대응한다.

## 모듈 배치

```
app/                              Application · MainActivity · 루트 Coordinator · Koin 목록
feature/{기능}/interface          NavKey 경로 · 진입 인자 · 결과 타입 (Compose · Koin 없음)
feature/{기능}/sources            Coordinator · Screen · ViewModel · Contract · di/
domain/{기능}/interface           Entity · Repository · UseCase 인터페이스 (순수 Kotlin)
domain/{기능}/sources             Repository · UseCase 구현 · DTO · di/
service/{이름}                    API 경로 · 인증 세션 · 분석 등 앱 서비스
core/{이름}                       network · datastore · database · logger · utility
ui/{이름}                         designkit(토큰 · 컴포넌트) · sharedui · animation
build-logic/                      convention plugin
```

`settings.gradle.kts`가 `build.gradle.kts`가 있는 폴더를 자동으로 포함한다. 모듈 경로는 폴더 경로와 같다(`:feature:auth:sources`).

## 의존 방향

```
app → feature/*/sources → feature/*/interface
                        → domain/*/interface
                        → ui/*
app → domain/*/sources → domain/*/interface
                       → service/* → core/*
app → service/* · core/*  (Koin 목록 합치기만)
```

- feature는 다른 feature의 `interface`만 본다. `sources`를 보는 것은 app뿐이다.
- feature는 domain `interface`만 본다. Repository 구현 · DTO · Ktor를 모른다.
- domain `interface`는 Android · Koin · Ktor를 모른다.
- ui는 feature · domain · service를 모른다.
- 위반은 `.claude/scripts/check-boundaries`가 `build.gradle.kts` · import로 검사한다(규칙 표는 [하네스](harness.md)).

## iOS 모듈 대응

모듈 이름은 iOS와 같은 기능 이름을 소문자로 쓴다. 표에 없는 iOS 모듈은 이식 PR에서 iOS 사용처를 확인하고 이 표에 추가한다.

| iOS (`Projects/`) | Android | 비고 |
|-------------------|---------|------|
| `App` · `Feature/AppNavigation` | `app` | 루트 Coordinator(스플래시 · 온보딩 · 로그인 · 탭) |
| `Feature/FeatureAssembly` · `Service/ServiceAssembly` · `Core/CoreAssembly` | 각 모듈 `di/` + `app` 목록 | 조립 모듈을 따로 두지 않는다 |
| `Feature/Auth` | `feature/auth` | Google 로그인만. Apple 로그인 제외 |
| `Feature/Onboarding` | `feature/onboarding` | |
| `Feature/Festival` | `feature/festival` | 목록 · 라이브 보드 · 알림함. S00 목록(`FestivalFlow` 라이브 탭 루트 · `FestivalCoordinator` · `FestivalListViewModel` · `FestivalListPresentation`)과 S01 라이브 보드(`liveboard/`: `FestivalRoute.LiveBoard` · `LiveBoardViewModel` · `LiveBoardPresentation` · `LiveBoardScreenModel` · `HomeDeparture` · `FestivalSwitcherSheet`)를 옮겼다. 예매처 · 인스타그램은 Custom Tabs(`androidx.browser`, Apache-2.0). S06 존 지도(`zonemap/`: `FestivalRoute.ZoneMap` · `ZoneMapViewModel` · `ZoneMapModel` · `ZoneMapCanvas`(비율 좌표 Compose 레이아웃, 세로 스크롤만))는 보드 크루 존 칩에서 push하고 계정이 바뀌면 Coordinator가 뺀다. 존 지도의 팔로우 안내는 `FestivalFlow(onFlagTabRequested)` → `MainTabScreen`이 깃발 탭으로 바꾼다. 탭 바는 `FestivalCoordinatorState.hidesTabBar`(목록뿐 · 보드 시트/다이얼로그 · 알림함 맨 위)일 때 `MainTabScreen`이 숨긴다(존 지도는 보임). 알림함(`inbox/`: `FestivalRoute.NotificationInbox` · `NotificationInboxViewModel` · `NotificationInboxPresentation`)은 보드 종 아이콘 · 새 알림 배너에서 push하고, 라이브 보드 알림을 열면 Coordinator 대기 값(`pendingBoardFestivalId`)으로 보드 축제를 바꾸며, 계정이 바뀌면 Coordinator가 뺀다. 보드 배지 · 배너는 `domain/usernotification` 공유 배지를 따른다. 크루 등은 보드가 콜백만 보낸다. 동작은 `feature/festival/.spec/festival-list-screen.md` · `live-board-screen.md` · `zone-map-screen.md` · `notification-inbox-screen.md` |
| `Feature/Timetable` | `feature/timetable` | S02 타임테이블(`main/`: `TimetableRoute` · `TimetableViewModel` · `TimetableBoard`(고정 높이 `Box` 절대 배치 + 세로 · 가로 `ScrollState`, 무대 머리글은 가로 값만 따름, 두 손가락 확대) · 공개 전 라인업)과 S03 공연 상세 시트(`setdetail/`, 상태는 `TimetableViewModel`이 가짐) · 공연 검색 · 변경 목록(`setlist/`: `TimetableSetListViewModel`)을 옮겼다. 입력은 보드가 쓴 `FestivalBoardSelectionUseCase` 공유 상태를 읽어 `domain/timetable` 매퍼로 옮긴다. 내 일정 서버 동기화 · 고르기 · 알림 켜기 · 탭 연결(Coordinator · `MainTabScreen` 슬롯 · app Koin 등록)은 아직. 동작은 `feature/timetable/.spec/timetable-screen.md` |
| `Feature/Profile` | `feature/profile` | |
| `Feature/Bearer` | `feature/bearer` | |
| `Feature/Web` | `feature/web` | `WebScreen`(앱 안 웹 화면, `android.webkit.WebView`). 진입 인자 `WebPage`는 `interface`. 문서(`document`) 범위만 옮겼다. 다른 feature 스택에 넣을 때는 app이 화면을 슬롯으로 넘긴다(예: `LaunchLoginFlow(legalDocument = ...)`) |
| `Domain/{Auth,Bearer,Festival,Notice,Timetable,UserNotification}Domain` | `domain/{auth,bearer,festival,notice,timetable,usernotification}` | `domain/festival`: 목록(카탈로그) 엔티티 · `FestivalCatalogRepository` · `FestivalCatalogUseCase` · `FestivalLibraryUseCase`(최근 검색), 라이브 보드 데이터(`FestivalPackDocument` · `FestivalLiveBoardSnapshot` · `FestivalOfficialNews` · `FestivalLiveBoardSchedule` · `FestivalLiveBoardUseCase` · `FestivalScheduleUseCase.mySchedule`), 팩(`FestivalPackRepository` · `FestivalPackUseCase`(`syncPack` 포함), 목록 · 보드가 함께 보는 `FestivalPackState` `StateFlow`), 존 지도(`FestivalZoneMap` · `FestivalZoneMapRepository` · `FestivalZoneMapUseCase`, 받은 팩 문서에서 읽음). 동작은 `domain/festival/.spec/festival-catalog.md` · `recent-search.md` · `live-board.md` · `zone-map.md`. 내 일정 저장 · 저장 알림 · 보드 선택 공유(`FestivalScheduleUseCase.saveMySchedule` · `savedSchedules` · `FestivalBoardSelectionUseCase`)는 `domain/festival/.spec/my-schedule.md`. `domain/timetable`: 시간표 엔티티 · 문구, 팩 → 시간표 `FestivalPackTimetableMapper`(iOS는 App에 둠) · 겹침 `TimetableOverlapCalculator`(`domain/timetable/.spec/timetable-mapping.md`). `domain/auth`: `CurrentUserProvider`(`domain/auth/.spec/current-user.md`). `domain/usernotification`: `UserNotificationItem` · `UserNotificationPage` · `UserNotificationError` · `UserNotificationRepository` · `UserNotificationUseCase`(목록 · 안 읽은 수 · 읽음 · 삭제, 100개씩 분할), 보드 · 알림함 공유 배지 `UserNotificationBadgeUseCase`(`StateFlow`, 앱 범위 `CoroutineScope`에서 읽음 요청, 설계 E7). sources는 `service/api` · `service/auth`(`AuthSession.currentSessionId`) 의존. 동작은 `domain/usernotification/.spec/user-notification.md` |
| `Service/API` · `Service/APIEndpoint` | `service/api` | 경로 상수 · 인증 정책 · 요청 DTO. iOS는 축제 DTO가 `FestivalDomain`에 있지만 Android는 의존 방향 때문에 여기 둔다(`FestivalApi` · `FestivalItemDto` · 상세 `FestivalPackDocumentDto` · 라이브 `FestivalLiveBoardDto` · 깃발 `FestivalFlagsDto` · 팩 버전 `FestivalPackVersionDto` · 공식 소식 `FestivalOfficialNewsDto` · 내 일정 `FestivalScheduleDto`). 내 알림은 iOS처럼 `UserNotificationApiPath` · `UserNotificationApi` · `UserNotification{List,Item,Count}Dto` · `UserNotificationIdsBody` |
| `Service/FestiCoreAuth` | `service/auth` | 세션 · 토큰 갱신 |
| `Service/FestiCoreAnalytics` | `service/analytics` | Sentry · Mixpanel |
| `Service/FestiCoreConfig` · `FestiCoreDevice` · `FestiCorePack` | `service/{config,device,pack}` | `service/device`: 설치 기기 ID(`DeviceInstallationService`, `SecureStorage` 키 `deviceID`)만 옮겼다. 서버 기기 등록은 아직. `service/pack`: 팩 WebSocket 동기화(`PackSyncService` · `NetworkPackSyncService` · `PackWire` · `ReconnectBackoff`)와 축제별 JSON 파일 저장(`PackCache`, `AtomicFile`, `filesDir/festicore/packs/{id}.json`). `servicePackModule(baseUrl)`. 규칙은 `service/pack/.spec/pack-sync.md` |
| `Service/FestiCoreFestivalCatalog` | `service/festivalcatalog` | 목록 쪽 조회 · 전체 갱신 · JSON 파일 캐시(`AtomicFile`, `filesDir/festicore/festival-catalog.json`) · 썸네일 주소 결합. 상세(`festivalDetail`) · 라이브(`FestivalLiveBoardService`, 세션별 ETag · 429 대기 · 마지막 조회 결과(`cachedFetch`) 메모리 캐시) · 공식 소식 · 내 일정 읽기도 둔다(`service/auth` · `service/device` 의존). 캐시 규칙은 `service/festivalcatalog/.spec/catalog-cache.md`, 라이브 규칙은 `domain/festival/.spec/live-board.md` |
| `Core/FestiCoreNetwork` | `core/network` | Ktor. WebSocket은 `FestiCoreWebSocketClient`(Ktor `WebSockets` 플러그인 + OkHttp 엔진) |
| `Core/FestiCoreStorage` | `core/datastore` · `core/database` | 보안 값 · 설정 / Room. 라이브 보드 선택 축제 · 팔로우 안내 숨김은 `LiveBoardPreferenceStore`(iOS `UserDefaults` 키 그대로) |
| `Core/FestiCoreLogger` · `FestiCoreUtility` · `FestiCoreLocation` | `core/{logger,utility,location}` | |
| `UI/FestiCoreDesignKit` · `UI/FestiCoreDesignSystem` | `ui/designkit` | 토큰은 FestiCore-DesignSystem에서 생성 |
| `UI/FestiCoreSharedUI` | `ui/sharedui` | `FCRemoteImage`(원격 이미지, Coil 3 `coil-compose` + `coil-network-okhttp`, Apache-2.0) · `FCSetSheet`(공연 상세 시트 내용) · 토스트 프리셋 `FCToastItems.scheduleAdded`를 옮겼다. 로더는 Koin이 아닌 Coil 싱글톤이고 `app`이 `installFestiCoreImageLoader`로 설정한다. 동작은 `ui/sharedui/.spec/remote-image.md`. SVG 처리기 · 나머지 토스트 프리셋은 아직 옮기지 않았다 |
| `UI/FestiCoreAnimation` | `ui/animation` | `FestiCoreLottieView` · `FestiCoreAnimationAsset`(`lottie-compose`, Apache-2.0). JSON은 iOS `Resources`와 같은 파일을 `res/raw`에 둔다. 원격 · `.lottie` 소스와 GIF(`FestiCoreAnimationView`)는 아직 옮기지 않았다 |
| `UI/FestiCoreLiveActivity` · `Widget/` · `Extension/` | 없음 | Android 대응은 별도 결정 |

## 모듈 추가

- 새 모듈은 폴더와 `build.gradle.kts`를 만들고 convention plugin(`festicore.android.library` · `festicore.android.feature` 등)만 적용한다. 설정을 복사하지 않는다(`.claude/docs/gradle.md`).
- 모듈 경계를 바꾸거나 새 계층을 만들 때는 설계 문서(`.claude/docs/specs/`)를 먼저 쓴다(`.claude/docs/spec-guide.md`).
