# iOS 구조 맞추기 리팩터링 설계

- 날짜: 2026-11-23
- 기준: Android `develop` `5cc901f`, iOS `FestiCore-iOS` 작업 트리(읽기 전용)
- 결정(사용자): Android의 모듈 · 폴더 · 파일 · 의존 배치를 iOS와 "완전히 같게" 맞춘다.
- 범위: 파일 이동 · 이름 변경 · Gradle 의존 · Koin 등록 위치 · 경계 검사만 바꾼다. 동작 · 로직 · 공개 API 의미는 바꾸지 않는다.
- 이 문서는 설계만 담는다. 코드 수정 · 커밋은 각 PR 이슈에서 한다.

## 0. 근거 규칙

- Android `CLAUDE.md` 규칙 9: 기능 모듈 이름은 iOS와 같게 둔다(`Projects/Feature/Auth` ↔ `feature/auth`).
- Android `.claude/docs/architecture.md` "iOS 모듈 대응": 모듈 이름은 iOS 기능 이름을 소문자로 쓴다. 지금은 조립 모듈을 두지 않고(`FeatureAssembly` · `ServiceAssembly` · `CoreAssembly` → 각 모듈 `di/` + app 목록), 축제 DTO를 의존 방향 때문에 `service/api`에 둔다고 적혀 있다. 이 두 줄이 이번 리팩터링으로 바뀐다.
- Android `.claude/docs/dependency-injection.md`: 구현을 가진 모듈이 자기 구현을 직접 등록한다. app은 목록만 합친다. 이 원칙은 유지한다.
- iOS `CLAUDE.md:76` 규칙 10: "모든 구조와 API · DI · UseCase · Repository의 배치와 주입은 Attendance_iOS(읽기 전용)를 따른다."
- iOS `.claude/docs/architecture.md:23`: 모든 구조(모듈 · 폴더 · 파일 이름 · 코디네이터 · 테스트 배치)는 Attendance_iOS를 따르고 `Projects/Domain/AttendanceDomain` · `Projects/Service/{API,APIEndpoint}`를 대조한다.
- iOS `.claude/docs/domain-modules.md:9-15`: Domain 폴더 구성은 `Interface/Sources/Entity`, `Interface/Sources/<기능>`(Repository · UseCase 계약), `Sources/<기능>/<X>UseCaseImpl`, `Sources/Repository/<X>RepositoryImpl`(로컬은 `Sources/Repository/Local`), `Sources/Model/DTO` · `Sources/Model/Mapper`, 등록은 `Sources/<X>LiveDependencies.swift`.
- iOS `.claude/docs/dependency-injection.md:19`: UseCaseImpl은 Repository Interface만 주입받는다. "지금 UseCaseImpl이 Service·Store·Client를 직접 주입받는 곳은 Repository나 LocalDataSource 뒤로 옮길 이전 대상"이다.

즉 iOS가 따르는 원천은 Attendance_iOS 구조이고, Android는 그 결과인 iOS 현재 배치를 따른다. iOS 문서가 "이전 대상"으로 적은 곳은 iOS 현재 모양이 아니라 iOS 목표 모양을 따른다(쟁점 E4).

## 1. 대응표 (현재)

표기: 같음 / 이름만 다름 / 배치 다름 / Android에만 / iOS에만.

### 1.1 계층 · 모듈

| iOS `Projects/`                                                                                             | Android 현재                                                            | 판정                                             | 근거                                                             |
| ----------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------- | ------------------------------------------------ | ---------------------------------------------------------------- |
| `App`                                                                                                       | `app`                                                                   | 같음                                             | `app/src/main/kotlin/io/festicore/app/FestiCoreApplication.kt`   |
| `Feature/AppNavigation`                                                                                     | `app`(`AppCoordinator` · `MainTabCoordinator`)                          | 배치 다름                                        | `app/.../di/AppModules.kt:36-55`                                 |
| `Feature/FeatureAssembly`(`FeatureOAuthURLHandler.swift`)                                                   | 없음(`app` 목록)                                                        | 배치 다름                                        | `AppModules.kt:56`                                               |
| `Feature/{Auth,Onboarding,Festival,Timetable,Web}`                                                          | `feature/{auth,onboarding,festival,timetable,web}/{interface,sources}`  | 같음                                             | `settings.gradle.kts` 자동 포함                                  |
| `Feature/{Bearer,Profile,Home,Login,FestiCoreHome}`                                                         | 없음                                                                    | iOS에만(이식 전)                                 | 이번 범위 밖                                                     |
| `Feature/FestiCoreScaffoldCheck` · `Domain/FestiCoreScaffoldCheck` · `Domain/FestiCoreDomain`               | 없음                                                                    | iOS에만(Tuist 생성 점검용, `Project.swift` 없음) | `Domain/FestiCoreDomain/`에 `.xcodeproj` · `Derived`만 있음      |
| `Domain/{Auth,Festival,Timetable,UserNotification}Domain/{Interface,Sources}`                               | `domain/{auth,festival,timetable,usernotification}/{interface,sources}` | 이름만 다름(`Domain` 접미 제거 규칙)             | 위 목록                                                          |
| `Domain/{Bearer,Notice}Domain`                                                                              | 없음                                                                    | iOS에만(이식 전)                                 |                                                                  |
| `Service/API` + `Service/APIEndpoint`                                                                       | `service/api` 하나                                                      | 배치 다름                                        | `service/api/.../service/api/*.kt` 22개                          |
| `Service/FestiCoreFestivalCatalog`                                                                          | `service/festivalcatalog`                                               | 이름만 다름 + 배치 다름(아래 1.3)                | `service/festivalcatalog/build.gradle.kts`                       |
| `Service/FestiCorePack` (`Interface` · `Sources`)                                                           | `service/pack` 하나                                                     | 이름만 다름 + Interface 분리 없음                | iOS `FestiCorePack/Interface/PackSyncService.swift`              |
| `Service/FestiCoreDevice` (`Interface` 3 · `Sources` 3)                                                     | `service/device`(`DeviceInstallationService.kt`만)                      | 이름만 다름 + Interface 분리 없음                |                                                                  |
| `Service/FestiCoreAuth` (`Interface` · `Sources/{Expiry,Factory,Refresh,Store,Token}`)                      | `service/auth`(평평한 5파일)                                            | 이름만 다름 + 폴더 다름                          | `service/auth/.../AuthSession.kt` 등                             |
| `Service/FestiCoreConfig`(`FirebaseConfiguration.swift`)                                                    | 없음                                                                    | iOS에만                                          | 쟁점 E6                                                          |
| `Service/FestiCoreAnalytics`                                                                                | 없음(문서에는 `service/analytics`로 적혀 있음)                          | iOS에만 · 문서 불일치                            | `architecture.md` 대응표 vs 실제 모듈 목록                       |
| `Service/ServiceAssembly`(`DependencyValues+*.swift` 11 · `NetworkContainer` · `ServiceDependencyAssembly`) | 없음(`app` 목록)                                                        | 배치 다름                                        |                                                                  |
| `Core/CoreAssembly`(`NetworkAssembly` · `StorageAssembly`)                                                  | 없음(`app` 목록)                                                        | 배치 다름                                        |                                                                  |
| `Core/FestiCoreNetwork` (`Interface` 분리)                                                                  | `core/network`                                                          | 이름만 다름 + Interface 분리 없음                | iOS `FestivalDomain/Project.swift` `.core(.network, .interface)` |
| `Core/FestiCoreStorage`                                                                                     | `core/datastore`                                                        | 이름 다름                                        | check-boundaries 규칙 1 · 2                                      |
| `Core/FestiCoreLogger` · `FestiCoreUtility`                                                                 | `core/logger` · `core/utility`                                          | 이름만 다름                                      |                                                                  |
| `Core/FestiCoreLocation`                                                                                    | 없음(문서에는 있음)                                                     | iOS에만 · 문서 불일치                            |                                                                  |
| (없음)                                                                                                      | `core/database`(문서 · check-boundaries 규칙 3에만, 모듈 없음)          | 문서에만                                         | `.claude/scripts/check-boundaries`                               |
| `UI/FestiCoreDesignKit` + `UI/FestiCoreDesignSystem`                                                        | `ui/designkit` 하나                                                     | 배치 다름                                        | 쟁점 E7                                                          |
| `UI/FestiCoreSharedUI` · `FestiCoreAnimation`                                                               | `ui/sharedui` · `ui/animation`                                          | 이름만 다름                                      |                                                                  |
| `UI/FestiCoreLiveActivity`                                                                                  | 없음                                                                    | iOS에만(대응 별도 결정)                          |                                                                  |

### 1.2 의존 방향의 차이 (가장 큰 불일치)

iOS(`Project.swift` 근거):

```
Domain/FestivalDomain(Sources) → Service/APIEndpoint · FestiCorePack(Interface) · FestiCoreDevice(Interface) · FestiCoreAuth(Interface) · Core/Network(Interface) · Core/Utility
Service/FestiCoreFestivalCatalog → Domain/FestivalDomain(Interface + Sources) · FestiCoreAuth(Interface) · FestiCoreDevice(Interface) · Core/Network(Interface)
Service/ServiceAssembly → Domain/FestivalDomain(Interface) · Service/{Auth,Device,FestivalCatalog,Pack} · Core/{Network,Storage,Assembly}
```

- 서비스 계약 `FestivalCatalogService` · `FestivalLiveBoardService` · `FestivalOfficialNewsService` · `FestivalScheduleService` · `FestivalFlagFollowService`는 `Domain/FestivalDomain/Interface/Sources/{Catalog,LiveBoard,Schedule,FlagFollow}`에 있다. 구현(`NetworkFestival*Service.swift`)은 `Service/FestiCoreFestivalCatalog/Sources`에 있고, 응답 DTO는 `FestivalDomain/Sources/Model/DTO`(14개)에서 가져온다(`import FestivalDomain` 5곳).
- `PackSyncService`(`Service/FestiCorePack/Interface`) · `AuthService`(`FestiCoreAuth/Interface`) · `DeviceRegistrationService`(`FestiCoreDevice/Interface`)는 서비스 자기 Interface에 있다.

Android(`build.gradle.kts` 근거):

```
domain/festival/sources → domain/festival/interface · domain/auth/interface · service/festivalcatalog · service/pack · core/datastore
service/festivalcatalog → service/api · service/auth · service/device
service/api → core/network
```

- 서비스 계약과 구현이 모두 `service/festivalcatalog`에 있고, 응답 DTO는 `service/api`에 있다. 방향이 iOS와 반대다(Android domain → service, iOS service → domain).

### 1.3 Repository가 부르는 대상

| Repository                          | iOS 주입                                                                                                                                                        | Android 주입                                                                                        | 판정                                       |
| ----------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------- | ------------------------------------------ |
| Catalog                             | `festivalCatalogService`                                                                                                                                        | `FestivalCatalogService`                                                                            | 같음                                       |
| Crew                                | `networkClient` + `CrewRequest`                                                                                                                                 | `FestivalCrewService`(service/festivalcatalog)                                                      | 배치 다름 → API 호출을 domain Repository로 |
| FlagDetail                          | `networkClient` + `festivalCatalogService`                                                                                                                      | `FestivalFlagDetailService` + `FestivalCatalogService`                                              | 배치 다름 → API 호출을 domain Repository로 |
| ZoneMap                             | `networkClient`                                                                                                                                                 | `FestivalApi`(service/api)                                                                          | 같은 역할, 주입 타입만 다름                |
| Pack                                | `networkClient`                                                                                                                                                 | `PackSyncService` + `Json`                                                                          | 확인 필요(7절)                             |
| PackSync                            | `packSyncService`                                                                                                                                               | (Pack에 합쳐짐)                                                                                     | iOS에만 `FestivalPackSyncRepositoryImpl`   |
| LiveBoard · Schedule · Flag(팔로우) | Repository 없음. UseCaseImpl이 `festivalLiveBoardService` · `festivalOfficialNewsService` · `festivalScheduleService` · `festivalFlagFollowService`를 직접 주입 | `FestivalLiveBoardRepositoryImpl` · `FestivalScheduleRepositoryImpl` · `FestivalFlagRepositoryImpl` | Android에만(쟁점 E4)                       |
| UserNotification                    | `networkClient`(`Sources/Repository`)                                                                                                                           | `UserNotificationApi`(service/api)                                                                  | 같은 역할                                  |

### 1.4 폴더 · 패키지

| 위치             | iOS                                                                                                                                                                                                                                                                                           | Android 현재                                                                                          | 판정                             |
| ---------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------- | -------------------------------- |
| domain interface | `Interface/Sources/Entity` + `<기능>`(21개 폴더: Catalog · Crew · Flag · FlagDetail · FlagFollow · LiveBoard · Pack · PackDetail · ReceivedPack · Schedule · Selection · ZoneMap · Library · RecentSearch · Image · Guide · Bookmark · NotificationSettings · LiveActivity · Widget · Entity) | `io.festicore.domain.festival` 한 패키지에 평평하게                                                   | 배치 다름                        |
| domain sources   | `Sources/<기능>/<X>UseCaseImpl` · `Sources/Repository` · `Sources/Model/{DTO,Mapper}` · `FestivalLiveDependencies.swift`                                                                                                                                                                      | `io.festicore.domain.festival.sources` 평평한 30파일(`*Mapping.kt` · `*ErrorMapping.kt` 포함) + `di/` | 배치 다름                        |
| 응답 DTO         | `Domain/<X>Domain/Sources/Model/DTO`                                                                                                                                                                                                                                                          | `service/api`(`FestivalDto.kt` 등)                                                                    | 배치 다름                        |
| Mapper           | `Sources/Model/Mapper/<DTO>+.swift`                                                                                                                                                                                                                                                           | `domain/*/sources/*Mapping.kt`                                                                        | 이름 다름 + 배치 다름            |
| API 경로         | `Service/API/Sources/<기능>/<X>API.swift`(`enum CrewAPI { case create ... }`)                                                                                                                                                                                                                 | `service/api/*ApiPath.kt`                                                                             | 배치 다름(폴더 없음)             |
| 요청 · Body      | `Service/APIEndpoint/Sources/<기능>/<X>Request.swift` · `<X>RequestBody.swift`                                                                                                                                                                                                                | `service/api/*Api.kt` · `*Dto.kt` 안 요청 타입                                                        | 배치 다름                        |
| feature festival | `Sources/{FestivalList,LiveBoard,NotificationInbox,Flag,FlagDetail,ZoneMap,Crew,Common,Components,Accessibility}`                                                                                                                                                                             | 루트(목록) · `liveboard` · `inbox` · `flag` · `flagdetail` · `zonemap`                                | 이름 다름(`inbox`) · 목록이 루트 |
| DI 등록          | `<X>LiveDependencies.swift` · `ServiceAssembly/DependencyValues+*.swift`                                                                                                                                                                                                                      | 각 모듈 `di/<계층><이름>Module.kt`                                                                    | 역할 같음(Koin 관례)             |

## 2. 목표 구조

### 2.1 이름 규칙 (1:1)

Gradle 경로와 Kotlin 패키지는 소문자만 쓴다(ktlint `package-name`, 기존 `settings.gradle.kts` 규칙). 그래서 대응 규칙을 하나로 고정한다.

1. iOS 폴더 이름을 소문자로 바꾼다(`APIEndpoint` → `apiendpoint`, `ServiceAssembly` → `serviceassembly`).
2. 앞의 `FestiCore`와 Domain 뒤의 `Domain`은 뗀다(`FestiCoreFestivalCatalog` → `festivalcatalog`, `FestivalDomain` → `domain/festival`). 패키지가 `io.festicore.service.festicorefestivalcatalog`처럼 겹치지 않게 하려는 것이다(쟁점 E1).
3. iOS `Interface` · `Sources` 타깃은 `interface` · `sources` Gradle 모듈로 나눈다.

### 2.2 목표 모듈 트리

```
app/                                   Application · MainActivity · Koin 목록 합치기
app/navigation                         AppCoordinator · MainTabScreen · MainTabCoordinator · AppFlow (app에 남김 — R2 결정, iOS App/Sources/Navigation 대응)
feature/featureassembly                featureModules 목록 (+ FeatureOAuthURLHandler 대응 없음)
feature/{auth,onboarding,festival,timetable,web}/{interface,sources}
domain/{auth,festival,timetable,usernotification}/{interface,sources}
service/api                            경로 enum(<X>ApiPath) · 인증 정책 · Base
service/apiendpoint                    Ktor 요청(<X>Request) · 요청 Body(<X>RequestBody) · 멱등 키
service/festivalcatalog                NetworkFestival{Catalog,LiveBoard,Schedule,OfficialNews,FlagFollow}Service · 캐시 · URL · Endpoint · Error
service/pack/{interface,sources}       PackSyncService 계약 / NetworkPackSyncService · PackWire · PackCache · ReconnectBackoff
service/device/{interface,sources}
service/auth/{interface,sources}
service/serviceassembly                serviceModules(baseUrl) 목록
core/network/{interface,sources}
core/storage                           (core/datastore 이름 변경)
core/logger · core/utility
core/coreassembly                      coreModules 목록
ui/designkit · ui/sharedui · ui/animation   (ui/designsystem은 쟁점 E7)
```

### 2.3 목표 의존 방향 (iOS와 같게)

```
app → feature/featureassembly · service/serviceassembly · core/coreassembly · domain/*/sources
feature/*/sources → feature/*/interface · domain/*/interface · ui/*
domain/festival/sources → domain/festival/interface · domain/auth/interface
                        → service/apiendpoint · service/pack/interface · service/device/interface · service/auth/interface
                        → core/network/interface · core/storage · core/utility
service/festivalcatalog → domain/festival/interface · domain/festival/sources(DTO)
                        → service/apiendpoint · service/auth/interface · service/device/interface · core/network/interface
service/apiendpoint → service/api · core/network/interface
service/serviceassembly → service/* (Koin 모듈 목록)
```

- 순환 확인: `domain/festival/sources`가 `service/festivalcatalog`를 더 이상 보지 않으므로(계약이 `domain/festival/interface`로 옴) `service/festivalcatalog → domain/festival/sources`가 순환을 만들지 않는다. Gradle은 순환이면 설정 단계에서 실패하므로 R2 PR에서 `./gradlew projects`로 바로 확인된다.

### 2.4 파일 배치

**service/api** (iOS `Service/API/Sources/<기능>/<X>API.swift`)

- `api/{auth,crew,festival,flag,usernotification,base}/` 패키지로 나눈다. `AuthApiPath` · `CrewApiPath` · `FestivalApiPath` · `FlagApiPath` · `UserNotificationApiPath` · `ApiAuthorization`(Base)를 둔다.
- 이름은 iOS `CrewAPI`와 맞춰 `CrewApi`로 바꾸고 싶지만 Android `CrewApi`가 이미 요청 함수 클래스 이름이다. R1에서 요청 함수 클래스를 `CrewRequest`로 바꾼 뒤 경로를 `CrewApi`로 바꾼다(iOS 이름과 같아진다). ktlint 이름 규칙상 `API` 대문자 축약은 `Api`로 쓴다(`code-convention.md`).

**service/apiendpoint** (iOS `Service/APIEndpoint/Sources/<기능>/`)

- `apiendpoint/{auth,crew,festival,flag,usernotification}/`에 `<X>Request.kt`(지금 `*Api.kt`의 Ktor 호출) · `<X>RequestBody.kt`(지금 `*Dto.kt` 안 요청 타입) · `IdempotencyKey.kt`(flag · crew 공용이면 `common/`).
- `FestivalPackSocketRequest`(iOS `APIEndpoint/Sources/Festival`) 대응은 `service/pack`의 WebSocket 경로 구성을 이쪽으로 옮길지 R1에서 확인한다(7절).

**domain/festival/interface** (iOS `Interface/Sources/...`)

- 패키지 `io.festicore.domain.festival.{entity,catalog,crew,flag,flagdetail,flagfollow,liveboard,pack,schedule,selection,zonemap,library,recentsearch}`.
- `entity/`: 공개 값 · 오류(`FestivalCatalogItem` · `FestivalPackDocument` · `FestivalCatalogError` …).
- `<기능>/`: `<X>Repository` · `<X>UseCase` 계약, 그리고 iOS처럼 서비스 계약(`catalog/FestivalCatalogService` · `liveboard/FestivalLiveBoardService` · `liveboard/FestivalOfficialNewsService` · `schedule/FestivalScheduleService` · `flagfollow/FestivalFlagFollowService`).
- iOS의 `Bookmark` · `Guide` · `Image` · `NotificationSettings` · `LiveActivity` · `Widget` · `PackDetail` · `ReceivedPack`는 Android에 아직 기능이 없으므로 빈 패키지를 만들지 않는다.

**domain/festival/sources** (iOS `Sources/...`)

- `<기능>/<X>UseCaseImpl.kt`(예: `crew/FestivalCrewUseCaseImpl.kt`)
- `repository/<X>RepositoryImpl.kt`, 로컬 저장은 `repository/local/<X>LocalDataSource.kt`(지금 `FestivalBoardSelectionStore` 등 `*Store`는 이름을 바꾸지 않고 `repository/local/`로만 옮긴다 — 이름 변경은 쟁점 E5).
- `model/dto/`: `service/api`에서 옮긴 응답 DTO(`FestivalItemDto` · `FestivalPackDocumentDto` · `FestivalLiveBoardDto` · `FestivalFlagsDto` · `FestivalPackVersionDto` · `FestivalOfficialNewsDto` · `FestivalScheduleDto` · Flag · Crew 응답).
- `model/mapper/`: 지금 `*Mapping.kt` · `*ErrorMapping.kt`. 파일 이름은 iOS `FestivalCrewDataDTO+.swift`에 맞춰 `FestivalCrewDataDtoMapper.kt`처럼 `<DTO>Mapper.kt`로 바꾼다(Kotlin 파일 이름에 `+`를 쓸 수 없다).
- `di/DomainFestivalModule.kt`는 그대로 둔다(iOS `FestivalLiveDependencies.swift` 역할, 쟁점 E3).

**service/festivalcatalog** (iOS `Service/FestiCoreFestivalCatalog/Sources`)

- 루트: `NetworkFestivalCatalogService` · `NetworkFestivalLiveBoardService` · `NetworkFestivalScheduleService` · `NetworkFestivalFlagFollowService` · `NetworkFestivalOfficialNewsService`(지금 `Festival*Service.kt`의 구현 부분, 이름에 `Network` 접두).
- `endpoint/` · `error/` · `storage/FestivalCatalogCache.kt` · `url/FestivalImageUrlResolver.kt`.
- `FestivalCrewService` · `FestivalFlagDetailService`는 iOS에 없다. 호출 코드를 그대로 `domain/festival/sources/repository/FestivalCrewRepositoryImpl` · `FestivalFlagDetailRepositoryImpl` 안으로 옮기고 파일은 지운다(iOS 1.3 표).

**domain/usernotification · domain/auth** 도 같은 규칙(`entity/` · `<기능>/` · `repository/` · `model/{dto,mapper}/`)을 따른다. iOS `AuthDomain/Sources/Google`은 Android `CredentialManagerGoogleSignInClient` · `GoogleLoginUseCaseImpl`을 `google/`로, `Sources/Auth`는 `auth/`로 옮긴다.

**feature/festival/sources**: 패키지 `festivallist`(루트 목록 파일) · `liveboard` · `notificationinbox`(`inbox` 이름 변경) · `flag` · `flagdetail` · `zonemap` · `common`(`FestivalTransitions` 등) · `accessibility`(`FestivalAccessibilityId`). `FestivalFlow` · `FestivalCoordinator`는 iOS에서 위치를 확인하고 같게 둔다(7절).

### 2.5 조립(Assembly) 위치

| iOS                                                        | Android 목표                                                                           | 내용                                                                                                                                                                                  |
| ---------------------------------------------------------- | -------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `Core/CoreAssembly`(`NetworkAssembly` · `StorageAssembly`) | `core/coreassembly` → `val coreModules = listOf(coreNetworkModule, coreStorageModule)` | 목록만. 정의는 각 core 모듈 `di/`                                                                                                                                                     |
| `Service/ServiceAssembly`(`DependencyValues+*`)            | `service/serviceassembly` → `fun serviceModules(baseUrl: String): List<Module>`        | 목록만. 각 서비스 모듈이 `bind`로 domain 계약에 자기 구현을 등록한다(Koin은 구현 모듈이 계약 모듈을 보면 바로 bind할 수 있어 iOS처럼 조립 모듈이 liveValue를 대신 등록할 필요가 없다) |
| `Feature/FeatureAssembly`                                  | `feature/featureassembly` → `fun featureModules(legalBaseUrl: String): List<Module>`   | 목록만. `FeatureOAuthURLHandler`는 Android Credential Manager에 URL 콜백이 없어 대응 파일 없음                                                                                        |
| (domain은 iOS도 조립 모듈 없음)                            | `app`이 `domainModules`를 직접 나열                                                    | 그대로                                                                                                                                                                                |

`app/di/AppModules.kt`는 `coreModules + serviceModules(BASE_URL) + domainModules + featureModules(LEGAL_BASE_URL) + appModule`만 합친다. `appModule`(AppCoordinator · MainTabCoordinator 등 루트 흐름 정의, domain/auth/sources 연결)은 app에 남는다(R2 결정).

### 2.6 FestiCoreConfig · FestiCoreAnalytics

- `FestiCoreConfig`: iOS 내용은 `FirebaseConfiguration.configure()` 한 파일이다. Android는 `google-services` 플러그인이 Firebase를 자동 초기화하므로 옮길 코드가 없다. 빈 모듈을 만들지 않고 대응표에 "대응 없음(자동 초기화)"로 적는다(쟁점 E6).
- `FestiCoreAnalytics`: Android에 아직 코드가 없다. 이번 리팩터링에서 만들지 않고, 분석을 옮기는 PR에서 `service/analytics/{interface,sources}`(iOS `Interface/{Event,UseCase}` · `Sources/{Configuration,Log,Trace,UseCase}`)로 만든다. `architecture.md`의 "이미 있음" 서술은 R0에서 고친다.

## 3. Koin · check-boundaries · build-logic 변경

### Koin

- 모듈 변수 이름은 그대로 두고 import 경로만 바뀐다. 새 변수는 `coreModules` · `serviceModules(...)` · `featureModules(...)` 셋.
- 서비스 계약이 domain interface로 옮겨지므로 `serviceFestivalCatalogModule`의 `bind` 대상 타입 import가 `io.festicore.domain.festival.catalog.FestivalCatalogService` 등으로 바뀐다.
- `FestivalCrewService` · `FestivalFlagDetailService` 정의는 지우고, `DomainFestivalModule`의 Repository 정의가 `Crew` · `Flag` 요청 객체(`service/apiendpoint`)를 받는다.
- `app` 단위 테스트(`app/src/test` 6개)에 Koin `verify()`가 있으면 그대로 통과해야 한다. 없으면 R3에서 `appModules.verify()` 테스트를 추가하지 않는다(범위 밖). 대신 `assembleStageDebug` + 앱 실행 확인을 PR 본문에 적는다.

### check-boundaries (`.claude/scripts/check-boundaries` `RULES`)

- 규칙 1 · 2 허용 경로 `core/datastore/**` → `core/storage/**`.
- 규칙 3 `core/database/**` → `core/storage/**`(iOS `FestiCoreStorage`가 DB도 가진다).
- 추가:
  - "domain interface는 Koin · Ktor · Android를 import하지 않는다" — `domain/*/interface/**/*.kt`, 금지 `\bimport (org\.koin|io\.ktor|android)\.`. 서비스 계약을 interface로 옮기며 Ktor 타입이 새지 않게 막는다.
  - "service · core는 feature를 의존하지 않는다" — `service/**/build.gradle.kts` · `core/**/build.gradle.kts`, 금지 `projects\.feature\b|:feature:`.
  - "domain sources는 service 구현 모듈을 의존하지 않는다" — `domain/**/build.gradle.kts`, 금지 `:service:festivalcatalog`(iOS와 같은 방향 고정).
  - "assembly 모듈에는 정의를 두지 않는다" — `**/*assembly/**/*.kt`, 금지 `\b(single|factory|viewModel)(Of)?\s*[({]`.
- 문서 `.claude/docs/harness.md` 규칙 표를 같은 PR에서 고친다.

### build-logic

- convention plugin(`festicore.android.library` · `festicore.android.feature` · `festicore.kotlin.serialization` · `festicore.android.compose` · `festicore.android.application`)은 바꾸지 않는다.
- `*/interface` 모듈 중 Android가 필요 없는 것(`service/pack/interface` 등)은 지금 `domain/*/interface`가 쓰는 plugin을 그대로 쓴다(어떤 plugin인지 R5에서 확인, 7절). 새 plugin은 만들지 않는다.
- 새 모듈의 `namespace`는 패키지와 같게(`io.festicore.service.apiendpoint`).
- `settings.gradle.kts`는 폴더를 자동 포함하므로 고치지 않는다.

## 4. 동작 불변 보장

- 각 PR은 파일 이동(`git mv`) · 패키지 선언 · import · Gradle 의존 · Koin 등록 위치만 바꾼다. 함수 본문을 고치지 않는다. 예외는 Crew · FlagDetail 서비스 → Repository 합치기(R2)로, 메서드 본문을 그대로 붙이고 위임 한 겹만 없앤다.
- 테스트도 같이 옮긴다(`src/test` 경로와 패키지를 대상 코드와 같게). 테스트 본문은 import · 패키지만 바꾼다.
- 테스트 수 비교: PR 전후로 아래 값을 PR 본문에 적는다. 두 값이 같아야 한다.

```bash
./gradlew testStageDebugUnitTest
find . -path '*/build/test-results/testStageDebugUnitTest/*.xml' -print0 | xargs -0 grep -ho 'tests="[0-9]*"' | grep -o '[0-9]*' | paste -sd+ - | bc
```

현재 소스 기준 `@Test` 수는 813개(`grep -r "@Test" --include='*Test.kt'`, build-logic 제외)다. 실행 기준 값은 R1 시작 때 다시 잰다.

- 각 PR 검증: `.claude/scripts/verify --all`(ktlint · check-boundaries · 단위 테스트 · stage 빌드) + 테스트 수 동일 + `./gradlew :app:assembleStageDebug`. Koin 등록 누락은 컴파일로 잡히지 않으므로 R2 · R3는 에뮬레이터에서 스플래시 → 탭 → 축제 목록 → 보드 → 깃발 상세 → 크루까지 열어 보고, 확인한 범위만 보고한다(규칙 7).
- 직렬화 이름: DTO를 옮겨도 `@SerialName`은 그대로다. 클래스 FQCN이 바뀌어도 JSON 형식에는 영향이 없다. 단 `PackCache` · `FestivalCatalogCache` 파일 캐시가 클래스 이름이 아니라 JSON만 저장하는지 R1에서 확인한다(7절).

## 5. PR 묶음

각 PR은 이슈 하나, 브랜치 `feature/<작업명>`, 대상 `develop`. 앞 PR이 머지된 뒤 다음을 시작한다.

| PR                            | 내용                                                                                                                                                                                                               | 바뀌는 곳                                                                          | 검증                                            |
| ----------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ | ---------------------------------------------------------------------------------- | ----------------------------------------------- |
| R0 문서                       | 이 설계 확정 · `architecture.md` 대응표의 없는 모듈(`service/analytics` · `config` · `core/location` · `core/database`) 서술 정정                                                                                  | 문서만                                                                             | 없음(문서)                                      |
| R1 API/APIEndpoint 분리       | `service/apiendpoint` 생성, `*Api.kt` → `<X>Request.kt`, 요청 Body 분리, `*ApiPath` → `<X>Api` 이름, 기능 폴더                                                                                                     | `service/api` · `service/apiendpoint` · 사용처 import                              | verify --all · 테스트 수 동일                   |
| R2 domain Repository로 이동   | 서비스 계약 → `domain/festival/interface`, 응답 DTO → `domain/*/sources/model/dto`, `*Mapping` → `model/mapper`, Crew · FlagDetail 호출 → Repository, `service/festivalcatalog` 의존 반전 · `Network*Service` 이름 | domain/festival · domain/usernotification · service/festivalcatalog · service/pack | verify --all · 테스트 수 동일 · 에뮬레이터 흐름 |
| R3 Assembly · Config          | `core/coreassembly` · `service/serviceassembly` · `feature/featureassembly` 생성(appnavigation 모듈은 만들지 않음), app 목록 정리, check-boundaries 규칙 추가                                                              | app · 새 모듈 3 · check-boundaries · harness.md                                    | verify --all · 테스트 수 동일 · 에뮬레이터 실행 |
| R4 패키지 · 폴더 이름         | domain interface/sources 기능 패키지, feature/festival 하위 패키지(`inbox` → `notificationinbox` 등), service/auth 하위 폴더                                                                                       | 패키지 선언 · import만                                                             | verify --all · 테스트 수 동일                   |
| R5 Interface 분리 · core 이름 | `service/{pack,device,auth}` · `core/network`를 `interface` · `sources`로, `core/datastore` → `core/storage`, check-boundaries 허용 경로                                                                           | 모듈 경로 · Gradle 의존                                                            | verify --all · 테스트 수 동일                   |

R2가 가장 크다. 커지면 R2a(계약 · DTO · Mapper 이동, 의존 반전) · R2b(Crew · FlagDetail 서비스 합치기)로 나눈다.

## 6. 쟁점과 추천

| #   | 쟁점                                                                                                                | 추천                                                                                                   | 근거                                                                                                                                      |
| --- | ------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------ | ----------------------------------------------------------------------------------------------------------------------------------------- |
| E1  | `FestiCore` 접두 · `Domain` 접미까지 그대로 쓸지                                                                    | 뗀다(2.1 규칙 2). 규칙이 결정적이라 1:1은 유지된다                                                     | 패키지 `io.festicore.service.festicore...` 중복, 기존 모듈 이름 전부 변경 비용                                                            |
| E2  | 응답 DTO를 iOS처럼 `domain/*/sources`에 두면 `service/festivalcatalog`가 `domain/festival/sources`를 본다           | 따른다. Gradle에서 순환이 없다(2.3)                                                                    | iOS `FestiCoreFestivalCatalog/Project.swift`가 `.domain(.festival)`을 본다                                                                |
| E3  | `<X>LiveDependencies.swift`에 맞춰 `di/` 파일 이름을 바꿀지                                                         | 바꾸지 않는다. Koin 모듈은 `di/<계층><이름>Module.kt` 관례를 유지                                      | Android `dependency-injection.md` 원칙, 역할은 같음                                                                                       |
| E4  | iOS LiveBoard · Schedule · FlagFollow는 Repository 없이 UseCase가 서비스를 직접 부른다. Android는 Repository가 있다 | Android Repository를 유지한다                                                                          | iOS `dependency-injection.md:19`가 직접 주입을 "Repository 뒤로 옮길 이전 대상"으로 적었다. 지우면 iOS 목표와 멀어지고 로직 이동이 생긴다 |
| E5  | `*Store`(로컬 저장)를 `<X>LocalDataSource`로 이름 바꿀지                                                            | R4에서 폴더만 `repository/local/`로 옮기고 이름은 iOS가 같은 파일을 LocalDataSource로 둔 경우만 바꾼다 | iOS 현재 이름 확인 안 됨(7절)                                                                                                             |
| E6  | `FestiCoreConfig` 대응 모듈                                                                                         | 만들지 않는다                                                                                          | 옮길 코드 없음(Firebase 자동 초기화). 빈 모듈은 단순함 원칙에 어긋난다                                                                    |
| E7  | `ui/designkit`를 `designkit` · `designsystem`으로 나눌지                                                            | 이번 범위에서 빼고 iOS `FestiCoreDesignSystem` 내용 확인 후 별도 결정                                  | 토큰 원천 규칙(CLAUDE.md 규칙 10)과 생성 경로에 영향                                                                                      |
| E8  | Interface 분리(R5)로 모듈 수 증가 · 빌드 시간                                                                       | 한다. 마지막에 두어 되돌리기 쉽게 한다                                                                 | 사용자 결정 "완전히 같게", iOS `.service(.pack, .interface)` 의존을 Android에서도 강제 가능                                               |

## 7. 확인 못 한 것

- iOS `FestivalPackRepositoryImpl`이 `networkClient`로 무엇을 부르는지, Android `FestivalPackRepositoryImpl`(`PackSyncService` + `Json`)과 iOS `FestivalPackSyncRepositoryImpl`의 나눔이 1:1인지.
- Android `*Dto.kt` 파일마다 요청 Body와 응답 DTO가 어떻게 섞여 있는지(R1 · R2 분리 목록은 착수 때 파일별로 만든다).
- `service/pack`이 `service/api`의 어떤 타입을 쓰는지, iOS `FestivalPackSocketRequest`를 `service/apiendpoint`로 옮길 수 있는지.
- `PackCache` · `FestivalCatalogCache`가 저장하는 형식이 클래스 이름에 묶이지 않는지.
- iOS `Feature/Festival`에서 `FestivalFlow` · `FestivalCoordinator`의 위치, `Feature/AppNavigation` 내부 폴더 구성.
- iOS `FestiCoreStorage` · `FestiCoreDesignSystem` · `FestiCoreNetwork` 내부 폴더 구성과 Interface 범위.
- iOS `*Store` · `LocalDataSource` 이름(E5), `UserNotificationDomain` · `AuthDomain` `Sources` 하위 파일 이름 대조.
- Android `domain/*/interface`가 쓰는 convention plugin 종류, `app/src/test`에 Koin `verify()`가 있는지.
- 테스트 실행 기준 수(소스 `@Test` 813개는 추정치, Gradle 결과로 다시 잰다).
- 이 문서는 Codex 교차 검토를 거치지 않았다.

## 결정 (호스트, 2026-11-23 — 사용자 결정 "iOS와 모듈 · 배치를 완전히 같게")
- E1~E8 · Assembly 추천안대로. PR R0~R5 순서로 진행, 각 PR은 로직 변경 없이 이동 · 이름 변경 · 의존 반전만, verify --all과 테스트 수 동일로 검증.
- (Codex R1 반영 — R2 결정으로 대체됨, 아래 참조)
- (Codex R2 반영, 위 R1 반영을 대체) AppCoordinator · MainTabCoordinator · AppFlow는 `feature/appnavigation`으로 옮기지 않고 **app에 남긴다**. 루트 흐름은 iOS도 App 타깃(App/Sources/Navigation)에서 조립하고, 이 클래스들은 core(datastore · utility · logger) · service/auth 구현 타입을 직접 쓰므로 feature 규칙 안에 둘 수 없다. 그래서 R3는 `coreassembly` · `serviceassembly` · `featureassembly`(목록 모듈)만 만들고 `appnavigation` 모듈은 만들지 않는다. app 폴더는 iOS App/Sources 구성(Application · Navigation)에 맞춘 패키지로만 정리한다. check-boundaries 변경은 assembly 모듈 규칙만.
- (R2 착수 조정) 2.3의 "순환 없음"은 서비스 계약이 Entity를 돌려준다는 가정이었다. Android 서비스는 DTO를 돌려주고 매핑은 Repository가 하므로, 계약을 `domain/festival/interface`에 두면 interface가 sources의 DTO를 봐야 해 순환이 생긴다. 로직 이동(매핑을 서비스 구현으로 옮기기) 없이 맞추는 최소 조정으로 **서비스 계약 · `FestivalLiveBoardFetch` · 서비스 오류는 DTO와 같은 `domain/festival/sources/service`에 둔다**(DTO를 interface로 올리지 않는다). 의존 방향은 iOS와 같다: `service/festivalcatalog → domain/festival/sources`, `domain/festival/sources`는 `service/festivalcatalog`를 보지 않는다(check-boundaries가 막는다). `service/apiendpoint`의 축제 · 크루 · 깃발 · 알림 `<X>Request`는 응답을 `reified T`로 받아 호출하는 쪽이 디코딩한다(iOS APIEndpoint와 같음). 인증 응답 `AuthDto.kt`는 `service/auth`(토큰 갱신)가 써서 옮기면 `service/auth ↔ domain/auth/sources` 순환이 생기므로 `service/api`에 남긴다. 계약을 iOS처럼 interface로 올리는 일(서비스가 Entity를 돌려주게 바꾸기)은 매핑 위치가 바뀌는 로직 이동이라 이 리팩터링 범위 밖이다.
- (R2 구현) 실제 `Network*Service`를 domain `internal` Repository · UseCase에 붙여 검증하는 테스트 4개(`FestivalLiveBoardCacheTest` · `FestivalFlagUseCaseTest` · `FestivalScheduleUseCaseTest` · `FestivalFlagDetailRepositoryTest`) 때문에 `domain/festival/sources`에 `testImplementation(:service:festivalcatalog)`만 남긴다(main 의존 없음, check-boundaries는 main 의존만 막는다). apiendpoint 요청 테스트 중 DTO 디코딩을 검증하는 4개는 호출하는 쪽인 `domain/festival/sources/src/test/.../request`로 옮겼다.
- (R3 · R4 구현) `core/coreassembly`(`coreModules`) · `service/serviceassembly`(`serviceModules(baseUrl)`) · `feature/featureassembly`(`featureModules(legalBaseUrl)`)를 만들고 `appModules`는 이 목록 · domain 목록 · `appModule`만 합친다(등록 순서 그대로). check-boundaries는 assembly 정의 금지 규칙과 "feature sources 의존" 규칙의 `feature/featureassembly` 허용만 바꿨다. 설계와 다르게 둔 것: `MainActivity`는 런처 컴포넌트 이름(설치된 기기의 홈 화면 바로가기)을 바꾸지 않으려고 `io.festicore.app`에 남겼다(`FestiCoreApplication` · `MainActivityProvider`는 `application/`, `AppDeeplinkBridge`는 iOS `App/Sources/Deeplink`처럼 `deeplink/`). `FestivalBoardSelectionStore`는 iOS `Sources/Selection/FestivalSelectionStore+Live`에 맞춰 `repository/local/`이 아닌 `selection/`에 두었다(`UserNotificationBadgeStore`는 iOS 대응이 없어 `repository/local/`). `FestivalFlagUseCase` · `FestivalFlagRepository`는 `FestivalFlagDetail*` 파일 안에 함께 있어 파일을 나누지 않고 `flagdetail/`에 두었다. `FestivalCoordinator` · `FestivalFlow`(iOS `App/Sources/Navigation/Festival`)는 feature 루트에 남겼다. domain · service 테스트는 원래 패키지에 두고 import만 더했다.
