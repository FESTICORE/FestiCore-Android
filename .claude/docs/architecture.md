# 아키텍처

현재 Gradle 설정과 소스를 기준으로 작업한다. 문서의 예시보다 실제 `settings.gradle.kts` · `build.gradle.kts`가 우선한다.

## 앱

- 앱 ID: `io.festiCore.co`(prod), `io.festiCore.co.stage`(stage). iOS 번들 ID와 같은 값을 쓴다.
- `stage` · `prod` product flavor 두 개만 둔다. iOS `Config/` Stage · Prod와 대응한다.

## 모듈 배치

```
app/                              application/(Application · Activity 제공) · deeplink/ · navigation/(루트 Coordinator) · di/(목록 합치기)
feature/{기능}/interface          NavKey 경로 · 진입 인자 · 결과 타입 (Compose · Koin 없음)
feature/{기능}/sources            Coordinator · Screen · ViewModel · Contract · di/ (화면별 하위 패키지, iOS `Sources/<화면>`)
feature/onboarding                Interface 없는 단일 모듈(iOS `Feature/Onboarding`). 경로 · 결과 타입도 이 모듈에 둔다
feature/featureassembly           feature Koin 목록 (iOS `Feature/FeatureAssembly`)
domain/{기능}/interface           entity/ · <기능>/(Repository · UseCase 인터페이스) (순수 Kotlin)
domain/{기능}/sources             <기능>/(UseCase 구현) · repository/ · model/{dto,mapper} · di/
service/{이름}                    API 경로 · 인증 세션 · 분석 등 앱 서비스
service/{pack,device,auth}/interface  계약 · 공개 타입 (iOS `Service/*/Interface`)
service/{pack,device,auth}/sources    구현 · di/ (iOS `Service/*/Sources`)
service/serviceassembly           service Koin 목록 (iOS `Service/ServiceAssembly`)
service/config                    Firebase 초기화 `FirebaseConfiguration` (iOS `Service/FestiCoreConfig`, Koin 등록 없음)
core/{이름}                       network · storage · logger · utility
core/network/{interface,sources}  요청 함수 · 오류 · JSON · WebSocket 계약 / HttpClient · WebSocket 구현 · di/
core/storage/{interface,sources}  secure/ · shared/ · onboarding/ 저장소 계약 / DataStore · Keystore 구현 · di/ (iOS `FestiCoreStorage/{Interface,Sources}`)
core/coreassembly                 core Koin 목록 (iOS `Core/CoreAssembly`)
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
                       → service/apiendpoint · service/{auth,pack,device}/interface → core/network/interface · core/*
service/festivalcatalog → domain/festival/sources (서비스 계약 · 응답 DTO, iOS `FestiCoreFestivalCatalog → FestivalDomain`)
app → core/coreassembly · service/serviceassembly · feature/featureassembly  (Koin 목록만, 정의 없음)
app → service/auth/interface · core/storage/interface · core/*  (루트 Coordinator가 세션 · 저장소를 직접 쓴다)
app → feature/onboarding  (interface 없는 단일 feature, iOS처럼 app이 직접 의존)
app → service/config  (`FestiCoreApplication.onCreate`가 `FirebaseConfiguration.configure`를 부른다)
*assembly → service/*/sources · core/{network,storage}/sources
service/auth/sources · service/pack/sources → core/network/sources (createFestiCoreHttpClient · createFestiCoreWebSocketClient)
```

- `service/{pack,device,auth}` · `core/{network,storage}`의 `sources`는 main(`implementation`/`api`)으로 `*assembly` · app · `service/auth/sources` · `service/pack/sources`만 의존한다. 테스트는 `testImplementation(...:sources)` 허용(`check-boundaries`). 패키지는 interface · sources가 같은 `io.festicore.service.pack` 등을 같이 쓴다. `core/storage`는 iOS 폴더처럼 `io.festicore.core.storage.{secure,shared,onboarding}`로 나누고, 구현 파일 이름은 `DataStore<X>.kt`다.
- interface 없는 단일 feature 모듈(`feature/onboarding`)은 app · `feature/featureassembly`만 의존한다(`check-boundaries`).

- 응답 DTO는 `domain/*/sources/model/dto`, DTO → Entity 매핑은 `model/mapper`(iOS `Sources/Model/{DTO,Mapper}`). `service/apiendpoint`의 `<X>Request`는 요청만 만들고 응답은 `reified T`로 받아, 호출하는 쪽(Repository · 서비스 구현)이 DTO 타입으로 디코딩한다. 인증 응답(`AuthDto.kt`)만 `service/auth`(토큰 갱신)가 쓰므로 `service/api`에 남는다.
- 서비스 계약(`FestivalCatalogService` · `FestivalLiveBoardService` · `FestivalOfficialNewsService` · `FestivalScheduleService` · `FestivalFlagFollowService`)과 서비스 오류는 iOS는 `FestivalDomain/Interface`에 있지만 Android 계약은 DTO를 돌려주므로 DTO와 같은 `domain/festival/sources/service`에 둔다(interface에 두면 interface → sources 순환). 구현은 `service/festivalcatalog`의 `NetworkFestival*Service`.

- feature는 다른 feature의 `interface`만 본다. `sources`를 보는 것은 app뿐이다.
- feature는 domain `interface`만 본다. Repository 구현 · DTO · Ktor를 모른다.
- domain `interface`는 Android · Koin · Ktor를 모른다.
- ui는 feature · domain · service를 모른다.
- 위반은 `.claude/scripts/check-boundaries`가 `build.gradle.kts` · import로 검사한다(규칙 표는 [하네스](harness.md)).

## iOS 모듈 대응

모듈 이름은 iOS와 같은 기능 이름을 소문자로 쓴다. 표에 없는 iOS 모듈은 이식 PR에서 iOS 사용처를 확인하고 이 표에 추가한다.

| iOS (`Projects/`) | Android | 비고 |
|-------------------|---------|------|
| `App` · `Feature/AppNavigation` | `app` | 루트 Coordinator(스플래시 · 온보딩 · 로그인 · 탭). `navigation/`은 iOS `Navigation/` 하위 폴더처럼 `main/`(`AppCoordinator` · `AppFlow` · `AppRoute`) · `splash/` · `account/` · `maintab/`로 나눈다 |
| `Feature/FeatureAssembly` · `Service/ServiceAssembly` · `Core/CoreAssembly` | `feature/featureassembly` · `service/serviceassembly` · `core/coreassembly` | 목록만(`featureModules(legalBaseUrl)` · `serviceModules(baseUrl)` · `coreModules`). 정의는 각 모듈 `di/`. domain 목록은 iOS처럼 조립 모듈 없이 `app`이 나열 |
| `Feature/Auth` | `feature/auth` | Google 로그인만. Apple 로그인 제외. 화면 패키지는 iOS `Sources/{Login,SignUp}`처럼 `login/` · `signup/`, `AuthCoordinator` · `AuthTransitions`는 루트 |
| `Feature/Onboarding` | `feature/onboarding` | 단일 모듈(iOS도 Interface 없음). `OnboardingRoute` · `OnboardingResult`도 이 모듈에 있고 app이 직접 의존한다 |
| `Feature/Festival` | `feature/festival` | 목록 · 라이브 보드 · 알림함. S00 목록(`FestivalFlow` 라이브 탭 루트 · `FestivalCoordinator` · `FestivalListViewModel` · `FestivalListPresentation`)과 S01 라이브 보드(`liveboard/`: `FestivalRoute.LiveBoard` · `LiveBoardViewModel` · `LiveBoardPresentation` · `LiveBoardScreenModel` · `HomeDeparture` · `FestivalSwitcherSheet`)를 옮겼다. 예매처 · 인스타그램은 Custom Tabs(`ui/sharedui` `openExternalUrl`). S06 존 지도(`zonemap/`: `FestivalRoute.ZoneMap` · `ZoneMapViewModel` · `ZoneMapModel` · `ZoneMapCanvas`(비율 좌표 Compose 레이아웃, 세로 스크롤만))는 보드 크루 존 칩에서 push하고 계정이 바뀌면 Coordinator가 뺀다. 존 지도의 팔로우 안내는 `FestivalFlow(onFlagTabRequested)` → `MainTabScreen`이 깃발 탭으로 바꾼다. 탭 바는 `FestivalCoordinatorState.hidesTabBar`(목록뿐 · 보드 시트/다이얼로그 · 알림함 맨 위)일 때 `MainTabScreen`이 숨긴다(존 지도는 보임). 알림함(`notificationinbox/`: `FestivalRoute.NotificationInbox` · `NotificationInboxViewModel` · `NotificationInboxPresentation`)은 보드 종 아이콘 · 새 알림 배너에서 push하고, 라이브 보드 알림을 열면 Coordinator 대기 값(`pendingBoardFestivalId`)으로 보드 축제를 바꾸며, 계정이 바뀌면 Coordinator가 뺀다. 보드 배지 · 배너는 `domain/usernotification` 공유 배지를 따른다. 크루 등은 보드가 콜백만 보낸다. 크루 화면은 iOS처럼 `crew/`(참여 `CrewJoin*` 포함), 크루 만들기 · 모으기는 `liveboard/crewcreate` · `liveboard/crewgather`에 둔다. 깃발 탭은 목록(`flag/`: `FlagViewModel` · `FlagDirectory`)과 상세(`flagdetail/`: `FlagDetailViewModel` · `FlagDetailContext`)를 옮겼고, `FlagFlow`(깃발 탭 루트) · `FlagCoordinator`가 목록 위에 상세 하나를 push한다(`FlagRoute`, 탭 연결은 아직). 동작은 `feature/festival/.spec/festival-list-screen.md` · `live-board-screen.md` · `zone-map-screen.md` · `notification-inbox-screen.md` · `flag-tab.md` |
| `Feature/Timetable` | `feature/timetable` | S02 타임테이블(`main/`: `TimetableRoute` · `TimetableViewModel` · `TimetableBoard`(고정 높이 `Box` 절대 배치 + 세로 · 가로 `ScrollState`, 무대 머리글은 가로 값만 따름, 두 손가락 확대) · 공개 전 라인업)과 S03 공연 상세 시트(`setdetail/`, 상태는 `TimetableViewModel`이 가짐) · 공연 검색 · 변경 목록(`setlist/`: `TimetableSetListViewModel`)을 옮겼다. 입력은 보드가 쓴 `FestivalBoardSelectionUseCase` 공유 상태를 읽어 `domain/timetable` 매퍼로 옮긴다. 내 일정 서버 동기화 · 고르기 · 알림 켜기 · 탭 연결(Coordinator · `MainTabScreen` 슬롯 · app Koin 등록)은 아직. 동작은 `feature/timetable/.spec/timetable-screen.md` |
| `Feature/Profile` | `feature/profile` | S1 마이(`main/`: `ProfileViewModel` · `ProfileScreen` · `ProfilePresentation`)와 S2 설정 홈(`settings/`) · 화면 모드(`appearance/`)를 옮겼다. S5 팩 상세(`packdetail/`: `PackDetailViewModel` · `PackDetailScreen` · `PackDetailPresentation` · 지도 앱 설치 확인 `MapApp` · `MapAppClient`, 패키지 이름은 app 매니페스트 `<queries>`)는 기기에 받은 팩만 읽고 링크는 `ui/sharedui` `openExternalUrl`로 연다. 화면 모드 저장소 `AppearanceModeSettings`는 app이 `core/storage` `AppearanceModeStore`를 감싸 등록한다. 계정 입력 `AccountState`는 `interface`(앱이 소유, 설계 쟁점 M13). 로그인 카드는 Google만(M1), 기수 · 내 깃발 카드는 숨김(M7). 이동은 SideEffect 콜백만 있고 `ProfileCoordinator` · `ProfileFlow` · 탭 연결은 아직. 동작은 `feature/profile/.spec/my-tab.md` |
| `Feature/Bearer` | 없음 | 아직 옮기지 않았다. 옮길 때 `feature/bearer`로 만든다 |
| `Feature/Web` | `feature/web` | `WebScreen`(앱 안 웹 화면, `android.webkit.WebView`). 진입 인자 `WebPage`와 문서 주소 `LegalDocumentUrls`(가입 · 설정 홈 공용, https · host 검증)는 `interface`. 문서(`document`) 범위만 옮겼다. 이동 정책은 `policy/WebNavigationPolicy`(iOS `Policy/`). 다른 feature 스택에 넣을 때는 app이 화면을 슬롯으로 넘긴다(예: `LaunchLoginFlow(legalDocument = ...)`) |
| `Domain/{Auth,Festival,Notice,Timetable,UserNotification}Domain` | `domain/{auth,festival,notice,timetable,usernotification}` | `domain/festival`: 목록(카탈로그) 엔티티 · `FestivalCatalogRepository` · `FestivalCatalogUseCase` · `FestivalLibraryUseCase`(최근 검색), 라이브 보드 데이터(`FestivalPackDocument` · `FestivalLiveBoardSnapshot` · `FestivalOfficialNews` · `FestivalLiveBoardSchedule` · `FestivalLiveBoardUseCase` · `FestivalScheduleUseCase.mySchedule`), 팩(`FestivalPackRepository` · `FestivalPackUseCase`(`syncPack` 포함), 목록 · 보드가 함께 보는 `FestivalPackState` `StateFlow`), 존 지도(`FestivalZoneMap` · `FestivalZoneMapRepository` · `FestivalZoneMapUseCase`, 받은 팩 문서에서 읽음). 받은 팩 · 팩 상세(`FestivalReceivedPack` · `FestivalPackDetail` · `FestivalPackChanges` · `FestivalChecklist` · `FestivalReceivedPackUseCase` · `FestivalPackDetailUseCase` · `FestivalPackChangesRepository` · `FestivalWiFiStatus`, 30일 지난 팩 삭제 `FestivalPackUseCase.deleteExpiredPacks`)는 `domain/festival/.spec/pack-detail.md`, 준비물 · 자동 받기 저장은 `core/storage` `FestivalPackLocalStore`. 동작은 `domain/festival/.spec/festival-catalog.md` · `recent-search.md` · `live-board.md` · `zone-map.md`. 내 일정 저장 · 저장 알림 · 보드 선택 공유(`FestivalScheduleUseCase.saveMySchedule` · `savedSchedules` · `FestivalBoardSelectionUseCase`)는 `domain/festival/.spec/my-schedule.md`. 깃발 상세 · 팔로우 · 신고 · 대표 깃발(`FestivalFlagDetail` · `FestivalFlagDetailUseCase` · `FestivalFlagUseCase.setFollowing`)은 `domain/festival/.spec/flag-detail-follow.md`, 크루(`FestivalCrew` · `FestivalCrewInvite` · `FestivalCrewUseCase`, 입력 검증 포함)는 `domain/festival/.spec/crew.md`. 크루 나가기의 현재 사용자 ID 때문에 sources가 `domain/auth/interface`(`CurrentUserProvider`)를 본다. `domain/timetable`: 시간표 엔티티 · 문구(interface `entity/`, iOS `Entity/`), 팩 → 시간표 `FestivalPackTimetableMapper`(iOS는 App에 둠) · 겹침 `TimetableOverlapCalculator`(`domain/timetable/.spec/timetable-mapping.md`). `domain/auth`: `CurrentUserProvider`(`domain/auth/.spec/current-user.md`), sources `auth/CurrentUserStore` · `google/ActivityProvider`(iOS `Sources/{Auth,Google}`). `domain/usernotification`: `UserNotificationItem` · `UserNotificationPage` · `UserNotificationError` · `UserNotificationRepository` · `UserNotificationUseCase`(목록 · 안 읽은 수 · 읽음 · 삭제, 100개씩 분할), 보드 · 알림함 공유 배지 `UserNotificationBadgeUseCase`(`StateFlow`, 앱 범위 `CoroutineScope`에서 읽음 요청, 설계 E7). sources는 `service/apiendpoint` · `service/auth`(`AuthSession.currentSessionId`) 의존. 동작은 `domain/usernotification/.spec/user-notification.md`. `domain/notice`: `NoticeSummary` · `NoticeDetail` · `NoticeError` · `NoticeRepository` · `NoticeUseCase`(공지 목록 · 상세, 인증 없음). sources는 `service/apiendpoint` · `core/utility`(ISO 시각 해석) 의존 |
| `Domain/BearerDomain` | 없음 | 아직 옮기지 않았다 |
| `Service/API` | `service/api` | 기능 폴더(`auth` · `crew` · `festival` · `flag` · `notice` · `usernotification`)마다 경로 `<X>Api`(iOS `<X>API`, 예: `crew/CrewApi`), `base/ApiAuthorization.kt`(`bearerAuthorization` · `AuthSessionError`). 응답 DTO는 `domain/*/sources/model/dto`로 옮겼고, 인증 응답 `AuthDto.kt`만 `service/auth`가 쓰므로 루트 패키지 `io.festicore.service.api`에 남는다 |
| `Service/APIEndpoint` | `service/apiendpoint` | 같은 기능 폴더에 Ktor 요청 `<X>Request`(iOS `<X>Request`, 예: `crew/CrewRequest`)와 요청 본문 `<X>RequestBody.kt`(한 클래스면 클래스 이름 파일, 예: `festival/FestivalScheduleReplaceBody.kt`), 쓰기 요청 멱등 키 `common/IdempotencyKey.kt`. 축제 요청은 iOS처럼 팩 요청 `festival/FestivalPackRequest`만 두고, 목록 · 상세 · 라이브 · 깃발 목록 · 공식 소식 · 내 일정 요청은 `service/festivalcatalog`의 `endpoint/`에 둔다. Koin은 `serviceApiEndpointModule`. `service/api`를 `api`로 노출한다 |
| `Service/FestiCoreAuth` | `service/auth/{interface,sources}` | 세션 · 토큰 갱신. interface: `AuthSession` · `SessionExpiry` · `SessionExpiryEvents`(iOS `AuthSessionExpiryClient`). sources: `AuthSessionImpl` · `SessionExpiryEventsImpl` · `CredentialStore` · `TokenRefresher` · 인증 플러그인 · `serviceAuthModule` |
| `Service/FestiCoreAnalytics` | 없음 | 아직 옮기지 않았다. 분석을 옮기는 PR에서 `service/analytics`로 만든다 |
| `Service/FestiCoreConfig` | `service/config` | `FirebaseConfiguration.configure(context, FirebaseValues)`. iOS는 `GoogleService-Info.plist`가 없으면 건너뛰고, Android는 `google-services` 플러그인 · `google-services.json` 없이 `BuildConfig.FIREBASE_{API_KEY,APP_ID,PROJECT_ID,SENDER_ID}`(flavor별 `FESTICORE_FIREBASE_{STAGE,PROD}_*`, `local.properties` · 환경 변수, 없으면 빈 값)가 모두 있을 때만 `FirebaseApp.initializeApp`, 하나라도 비면 로그 한 줄만 남긴다. 의존은 `firebase-bom` · `firebase-common`(Apache-2.0). 초기화는 이 모듈에서만(`check-boundaries`) |
| `Service/FestiCoreDevice` · `FestiCorePack` | `service/{device,pack}/{interface,sources}` | `service/device`: 설치 기기 ID(interface `DeviceInstallationService`, sources `DeviceInstallationServiceImpl`, `SecureStorage` 키 `deviceID`)만 옮겼다. 서버 기기 등록은 아직. `service/pack`: 팩 WebSocket 동기화(interface `PackSyncService.kt` 계약 · 결과, `PackSyncError.kt` 오류, sources `NetworkPackSyncService` · `PackWire` · `ReconnectBackoff`)와 축제별 JSON 파일 저장(`PackCache`, `AtomicFile`, `filesDir/festicore/packs/{id}.json`). `servicePackModule(baseUrl)`. 규칙은 `service/pack/.spec/pack-sync.md` |
| `Service/FestiCoreFestivalCatalog` | `service/festivalcatalog` | `domain/festival/sources/service`의 계약 구현 `NetworkFestivalCatalogService`(목록 조회 · 전체 갱신 · 상세 `festivalDetail`) · `NetworkFestivalLiveBoardService`(세션별 ETag · 429 대기 · 마지막 조회 결과(`cachedFetch`) 메모리 캐시) · `NetworkFestivalOfficialNewsService` · `NetworkFestivalScheduleService`(내 일정 읽기 · 바꾸기) · `NetworkFestivalFlagFollowService`, JSON 파일 캐시 `storage/FestivalCatalogCache`(`AtomicFile`, `filesDir/festicore/festival-catalog.json`) · 썸네일 주소 결합 `url/FestivalImageUrlResolver` · 요청 `endpoint/`(`FestivalListRequest` · `FestivalDetailRequest` · `FestivalLiveBoardRequest`(라이브 · 깃발 목록) · `FestivalOfficialNewsRequest` · `FestivalScheduleRequest`, iOS `Sources/Endpoint`). `domain/festival/sources`(계약 · DTO) · `service/auth` · `service/device` 의존. 크루 · 깃발 상세 · 신고 · 대표 깃발 요청은 iOS처럼 `FestivalCrewRepositoryImpl` · `FestivalFlagDetailRepositoryImpl`이 `CrewRequest` · `FlagRequest`를 직접 부른다. 캐시 규칙은 `service/festivalcatalog/.spec/catalog-cache.md`, 라이브 규칙은 `domain/festival/.spec/live-board.md` |
| `Core/FestiCoreNetwork` | `core/network/{interface,sources}` | Ktor. interface: `response/BaseResponse` · `error/NetworkError` · `endpoint/`(`festiCoreRequest` · `festiCoreExchange`) · `websocket/`(`FestiCoreWebSocket` · `FestiCoreWebSocketClient` · `WebSocketError`) · 루트 `FestiCoreJson`. sources: `client/createFestiCoreHttpClient` · `websocket/createFestiCoreWebSocketClient`(Ktor `WebSockets` 플러그인 + OkHttp 엔진) · `di/coreNetworkModule`. 폴더 이름은 iOS `Interface` · `Sources` 하위 폴더와 같다 |
| `Core/FestiCoreStorage` | `core/storage/{interface,sources}` | 보안 값 · 설정. interface `secure/`(`SecureStorage` · `SecureStorageException`) · `shared/`(설정 값 저장소, iOS `Shared/*SharedKey` 자리) · `onboarding/`(`FirstInstallOnboardingStore`), sources는 같은 패키지의 `DataStore*` 구현 · `KeystoreValueCipher` · `di/coreStorageModule`(iOS `Factory/StorageFactory` 역할). 다른 모듈은 interface만 보고 sources는 `core/coreassembly`만 본다. Room(DB)도 sources에 두되 아직 없다(iOS `Database/` 빈 패키지는 만들지 않음). 라이브 보드 선택 축제 · 팔로우 안내 숨김은 `LiveBoardPreferenceStore`(iOS `UserDefaults` 키 그대로), 대기 딥링크는 `PendingDeeplinkStore`(키 `FestiCore.pendingDeeplink`), 받은 팩 준비물 · 와이파이 자동 받기는 `FestivalPackLocalStore`(키 `FestiCore.pack.checklist.<id>` · `FestiCore.pack.autoDownloadOnWiFi`) |
| `Core/FestiCoreLogger` · `FestiCoreUtility` | `core/{logger,utility}` | `core/logger`: `FestiCoreLogger` · `FestiCoreLogCategory` · `FestiCoreLogLevel` · `FestiCoreLogSink` · `FestiCoreLogEntry`(iOS와 같은 이름). `core/utility`: 딥링크 경로 · 파서(`FestiCoreDeeplink` · `FestiCoreDeeplinkParser`, 순수 Kotlin), 날짜 `date/AppDateFormat`(iOS `AppDateFormat`, 서울 시간대 · ISO 시각 해석, 지금은 공지 `noticeDay`만). 대기 딥링크는 `core/storage` `PendingDeeplinkStore`. 규칙은 `core/utility/.spec/deeplink.md` |
| `UI/FestiCoreDesignKit` · `UI/FestiCoreDesignSystem` | `ui/designkit` | 토큰은 FestiCore-DesignSystem에서 생성 |
| `UI/FestiCoreSharedUI` | `ui/sharedui` | `FCRemoteImage`(원격 이미지, Coil 3 `coil-compose` + `coil-network-okhttp`, Apache-2.0) · `FCSetSheet`(공연 상세 시트 내용) · 토스트 프리셋 `FCToastItems.scheduleAdded` · 신고 시트 `FCReportSheet`를 옮겼다. 외부 링크 열기 `openExternalUrl`(Custom Tabs, `androidx.browser`, Apache-2.0, iOS `openURL`)는 feature가 함께 쓴다. 로더는 Koin이 아닌 Coil 싱글톤이고 `app`이 `installFestiCoreImageLoader`로 설정한다. 동작은 `ui/sharedui/.spec/remote-image.md`. SVG 처리기 · 나머지 토스트 프리셋은 아직 옮기지 않았다 |
| `UI/FestiCoreAnimation` | `ui/animation` | `lottie/`(iOS `Sources/Lottie`) `FestiCoreLottieView` · `FestiCoreAnimationAsset`(`lottie-compose`, Apache-2.0). JSON은 iOS `Resources`와 같은 파일을 `res/raw`에 둔다. 원격 · `.lottie` 소스와 GIF(`FestiCoreAnimationView`)는 아직 옮기지 않았다 |
| `UI/FestiCoreLiveActivity` · `Widget/` · `Extension/` | 없음 | Android 대응은 별도 결정 |

## 모듈 추가

- 새 모듈은 폴더와 `build.gradle.kts`를 만들고 convention plugin(`festicore.android.library` · `festicore.android.feature` 등)만 적용한다. 설정을 복사하지 않는다(`.claude/docs/gradle.md`).
- 모듈 경계를 바꾸거나 새 계층을 만들 때는 설계 문서(`.claude/docs/specs/`)를 먼저 쓴다(`.claude/docs/spec-guide.md`).
