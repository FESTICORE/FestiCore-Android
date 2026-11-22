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
| `Feature/Festival` | `feature/festival` | 목록 · 라이브 보드 · 알림함. 지금은 S00 목록만 옮겼다: `FestivalFlow`(라이브 탭 루트) · `FestivalCoordinator` · `FestivalListViewModel` · `FestivalListPresentation`. 예매처는 Custom Tabs(`androidx.browser`, Apache-2.0). 라이브 스택이 목록뿐이면 `MainTabScreen`이 탭 바를 숨긴다. 동작은 `feature/festival/.spec/festival-list-screen.md` |
| `Feature/Timetable` | `feature/timetable` | |
| `Feature/Profile` | `feature/profile` | |
| `Feature/Bearer` | `feature/bearer` | |
| `Feature/Web` | `feature/web` | `WebScreen`(앱 안 웹 화면, `android.webkit.WebView`). 진입 인자 `WebPage`는 `interface`. 문서(`document`) 범위만 옮겼다. 다른 feature 스택에 넣을 때는 app이 화면을 슬롯으로 넘긴다(예: `LaunchLoginFlow(legalDocument = ...)`) |
| `Domain/{Auth,Bearer,Festival,Notice,Timetable,UserNotification}Domain` | `domain/{auth,bearer,festival,notice,timetable,usernotification}` | `domain/festival`: 목록(카탈로그) 엔티티 · `FestivalCatalogRepository` · `FestivalCatalogUseCase` · `FestivalLibraryUseCase`(최근 검색), 라이브 보드 데이터(`FestivalPackDocument` · `FestivalLiveBoardSnapshot` · `FestivalOfficialNews` · `FestivalLiveBoardSchedule` · `FestivalLiveBoardUseCase` · `FestivalScheduleUseCase.mySchedule`), 팩(`FestivalPackRepository` · `FestivalPackUseCase`, 목록 · 보드가 함께 보는 `FestivalPackState` `StateFlow`). 동작은 `domain/festival/.spec/festival-catalog.md` · `recent-search.md` · `live-board.md`. `domain/auth`: `CurrentUserProvider`(`domain/auth/.spec/current-user.md`) |
| `Service/API` · `Service/APIEndpoint` | `service/api` | 경로 상수 · 인증 정책 · 요청 DTO. iOS는 축제 DTO가 `FestivalDomain`에 있지만 Android는 의존 방향 때문에 여기 둔다(`FestivalApi` · `FestivalItemDto` · 상세 `FestivalPackDocumentDto` · 라이브 `FestivalLiveBoardDto` · 깃발 `FestivalFlagsDto` · 공식 소식 `FestivalOfficialNewsDto` · 내 일정 `FestivalScheduleDto`) |
| `Service/FestiCoreAuth` | `service/auth` | 세션 · 토큰 갱신 |
| `Service/FestiCoreAnalytics` | `service/analytics` | Sentry · Mixpanel |
| `Service/FestiCoreConfig` · `FestiCoreDevice` · `FestiCorePack` | `service/{config,device,pack}` | `service/device`: 설치 기기 ID(`DeviceInstallationService`, `SecureStorage` 키 `deviceID`)만 옮겼다. 서버 기기 등록은 아직. `service/pack`: 팩 WebSocket 동기화(`PackSyncService` · `NetworkPackSyncService` · `PackWire` · `ReconnectBackoff`)와 축제별 JSON 파일 저장(`PackCache`, `AtomicFile`, `filesDir/festicore/packs/{id}.json`). `servicePackModule(baseUrl)`. 규칙은 `service/pack/.spec/pack-sync.md` |
| `Service/FestiCoreFestivalCatalog` | `service/festivalcatalog` | 목록 쪽 조회 · 전체 갱신 · JSON 파일 캐시(`AtomicFile`, `filesDir/festicore/festival-catalog.json`) · 썸네일 주소 결합. 상세(`festivalDetail`) · 라이브(`FestivalLiveBoardService`, 세션별 ETag · 429 대기 메모리 캐시) · 공식 소식 · 내 일정 읽기도 둔다(`service/auth` · `service/device` 의존). 캐시 규칙은 `service/festivalcatalog/.spec/catalog-cache.md`, 라이브 규칙은 `domain/festival/.spec/live-board.md` |
| `Core/FestiCoreNetwork` | `core/network` | Ktor. WebSocket은 `FestiCoreWebSocketClient`(Ktor `WebSockets` 플러그인 + OkHttp 엔진) |
| `Core/FestiCoreStorage` | `core/datastore` · `core/database` | 보안 값 · 설정 / Room |
| `Core/FestiCoreLogger` · `FestiCoreUtility` · `FestiCoreLocation` | `core/{logger,utility,location}` | |
| `UI/FestiCoreDesignKit` · `UI/FestiCoreDesignSystem` | `ui/designkit` | 토큰은 FestiCore-DesignSystem에서 생성 |
| `UI/FestiCoreSharedUI` | `ui/sharedui` | `FCRemoteImage`(원격 이미지, Coil 3 `coil-compose` + `coil-network-okhttp`, Apache-2.0)만 옮겼다. 로더는 Koin이 아닌 Coil 싱글톤이고 `app`이 `installFestiCoreImageLoader`로 설정한다. 동작은 `ui/sharedui/.spec/remote-image.md`. SVG 처리기 · 토스트 프리셋은 아직 옮기지 않았다 |
| `UI/FestiCoreAnimation` | `ui/animation` | `FestiCoreLottieView` · `FestiCoreAnimationAsset`(`lottie-compose`, Apache-2.0). JSON은 iOS `Resources`와 같은 파일을 `res/raw`에 둔다. 원격 · `.lottie` 소스와 GIF(`FestiCoreAnimationView`)는 아직 옮기지 않았다 |
| `UI/FestiCoreLiveActivity` · `Widget/` · `Extension/` | 없음 | Android 대응은 별도 결정 |

## 모듈 추가

- 새 모듈은 폴더와 `build.gradle.kts`를 만들고 convention plugin(`festicore.android.library` · `festicore.android.feature` 등)만 적용한다. 설정을 복사하지 않는다(`.claude/docs/gradle.md`).
- 모듈 경계를 바꾸거나 새 계층을 만들 때는 설계 문서(`.claude/docs/specs/`)를 먼저 쓴다(`.claude/docs/spec-guide.md`).
