# 푸시 · 기기 등록 · 딥링크 기반(P0) Android 이식 설계

- 날짜: 2026-11-22
- 이슈: 미정(구현 이슈를 만들 때 적는다)
- 상태: 제안
- 기준: Android `develop` 8bb00af, iOS `09eda3b8`(로컬 `../FestiCore-iOS`, 읽기 전용). 아래 iOS 행 번호는 모두 `09eda3b8`에서 열어 확인했다. 서버는 stage `https://festicore-dev.up.railway.app/`에서 인증 없이 GET만 했다(2026-11-22).
- 범위: `service/device` · `service/api` · `core/utility`(딥링크) · `core/datastore`(대기 딥링크 · 기기 키) · `domain/auth/sources`(기기용 사용자 세션 제공) · `feature/festival`(축제 열기 진입 함수) · `app`(매니페스트 · `MainActivity` · `FestiCoreApplication` · `AppCoordinator` · `MainTabScreen` 탭 선택 · 메시지 서비스) · `build-logic`(Firebase 값 주입) · `gradle/libs.versions.toml`
- 선행 관계: 타임테이블 설계(`2026-11-22-timetable-android-design.md` 결정 절)의 **P0**. A1(팩 알림 켜기) · C1(타임테이블 탭 연결) · PrepGuide 연결은 이 문서의 PR이 모두 develop에 들어간 뒤에만 머지한다.

## 목표

1. **서버 기기 등록**: 설치 기기 ID로 익명 등록 → 기기 토큰 저장 → 로그인 계정에 연결 · 해제 · 복구(iOS `NetworkDeviceRegistrationService` 그대로).
2. **FCM 토큰**: iOS APNs 토큰 자리에 FCM 등록 토큰을 넣어 서버에 등록한다. Firebase 설정 값이 없는 빌드는 깨지지 않고 푸시만 꺼진다.
3. **알림 권한 · 채널**: API 33+ `POST_NOTIFICATIONS`를 iOS와 같은 시점(앱 시작)에 묻고, 거부면 홈에서 한 번 설정 안내.
4. **표시 푸시 가공**: iOS Notification Service Extension(묶음 · 부제목 · 이미지)을 `FirebaseMessagingService`에서 같은 규칙으로.
5. **무음 동기화**: iOS `content-available` → Android data 메시지로 화면이 떠 있을 때만 목록 · 보드를 다시 읽는다.
6. **딥링크**: iOS 4단(enum · 파서 / 대기 저장소 / 브리지 / 루트 Coordinator 라우팅) 그대로. `festicore://festival/{id}/set/{setId}` 포함.

목표가 아닌 것: 라이브 액티비티 토큰(`liveActivityPushToStartToken` · `api/v1/devices/live-activity`) 등록, 알림 설정 화면 · 축제별 구독(`api/v1/notifications/subscriptions`), 알림함 행 → 딥링크 변환(이미 `FestivalCoordinator.onNotificationFestivalOpened`가 있음), 로그아웃 · 탈퇴(Android에 아직 없음).

iOS 원본 약어(`iOS` = `../FestiCore-iOS/Projects`):

| 약어                         | 경로                                                                                                                                                                                     |
| ---------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `DRS`                        | `iOS/Service/FestiCoreDevice/Interface/DeviceRegistrationService.swift`                                                                                                                  |
| `NDRS`                       | `iOS/Service/FestiCoreDevice/Sources/NetworkDeviceRegistrationService.swift`                                                                                                             |
| `DNS` · `NDNS`               | `iOS/Service/FestiCoreDevice/{Interface/DeviceNotificationSettingsService,Sources/NetworkDeviceNotificationSettingsService}.swift`                                                       |
| `AD` · `ADP` · `ADR` · `ADC` | `iOS/App/Sources/Application/AppDelegate{,+Push,+DeviceRegistration,+Configuration}.swift`                                                                                               |
| `FA`                         | `iOS/App/Sources/Application/FestiCoreApp.swift`                                                                                                                                         |
| `PRS` · `NAC` · `RCR`        | `iOS/App/Sources/Push/{PushRegistrationService,NotificationAuthorizationClient,RemoteContentRefreshRequest}.swift`                                                                       |
| `ADS`                        | `iOS/App/Sources/Navigation/Shared/AccountDeviceSync.swift`                                                                                                                              |
| `FDL`                        | `iOS/Core/FestiCoreUtility/Sources/Deeplink/FestiCoreDeeplink.swift`                                                                                                                     |
| `PDS` · `BRG` · `PDK`        | `iOS/App/Sources/Deeplink/PendingDeeplinkStore.swift` · `iOS/App/Sources/Deeplink/AppDeeplinkBridge.swift` · `iOS/Core/FestiCoreStorage/Interface/Shared/PendingDeeplinkSharedKey.swift` |
| `AC` · `MTC` · `FC`          | `iOS/App/Sources/Navigation/{Main/AppCoordinator,MainTab/MainTabCoordinator,Festival/FestivalCoordinator}.swift`                                                                         |
| `NSE` · `RNE`                | `iOS/Extension/FestiCoreNotificationService/Extension/NotificationService.swift` · `iOS/Core/FestiCoreUtility/Sources/Notification/RemoteNotificationEnrichment.swift`                   |
| `SSK`                        | `iOS/Core/FestiCoreStorage/Interface/Secure/SecureStorageKey.swift`                                                                                                                      |

## 지금 Android 상태 (경계)

| 항목           | Android 현재                                                                                                                               | 근거                                                                                                                              |
| -------------- | ------------------------------------------------------------------------------------------------------------------------------------------ | --------------------------------------------------------------------------------------------------------------------------------- |
| 권한           | 매니페스트에 `INTERNET`뿐. `POST_NOTIFICATIONS` 없음. targetSdk 36 · minSdk 26                                                             | `app/src/main/AndroidManifest.xml:4`, `build-logic/.../Catalog.kt`                                                                |
| 진입 Activity  | `MainActivity` 하나, 인텐트 필터는 `MAIN/LAUNCHER`만, `launchMode` 기본, `onNewIntent` 없음                                                | `AndroidManifest.xml:17-24`, `app/.../MainActivity.kt:18-28`                                                                      |
| 기기 ID        | `DeviceInstallationService.installationDeviceId()`(`SecureStorage` 키 `deviceID`). 서버 등록 없음. 라이브 보드가 `X-Device-Id`로 씀        | `service/device/.../DeviceInstallationService.kt:13-24`, `service/festivalcatalog/.../di/ServiceFestivalCatalogModule.kt:22-28`   |
| 현재 사용자    | `CurrentUserStore`(메모리 `StateFlow`). 앱을 다시 켜면 프로필 재확인 전까지 `null`                                                         | `domain/auth/sources/.../CurrentUserStore.kt:8-17`, `app/.../navigation/AppCoordinator.kt:48-54`                                  |
| 탭 선택        | `MainTabScreen`의 `rememberSaveable` 지역 상태. 밖에서 탭을 바꿀 길은 라이브 흐름의 `onFlagTabRequested`뿐                                 | `app/.../navigation/MainTabScreen.kt:39 · 46`                                                                                     |
| 축제 열기      | 알림함 → 보드에 이미 있는 축제만 연다(`onNotificationFestivalOpened`). iOS `openWidgetFestival` 대응 없음                                  | `feature/festival/sources/.../FestivalCoordinator.kt:116-128`                                                                     |
| 공연 상세 열기 | `TimetableViewModel.onSetClicked(id)` 있음. 타임테이블 탭은 아직 `MainTabScreen`에 연결 전(C1)                                             | `feature/timetable/sources/.../main/TimetableViewModel.kt:111`                                                                    |
| 설정 값        | flavor별 `app/config/{stage,prod}.properties`(추적, 공개 값만) → `BuildConfig`. `google-services.json` · `local.properties`는 `.gitignore` | `build-logic/src/main/kotlin/festicore.android.application.gradle.kts:13-15 · 35-43`, `.gitignore`                                |
| JSON           | `FestiCoreJson`은 `explicitNulls = false`(null 필드를 보내지 않음)                                                                         | `.claude/docs/network-storage.md:21`                                                                                              |
| 알림 문구      | "공연 10분 전에 알려 드려요"(시트), 온보딩 "시작 10분 전도 알려 드려요"                                                                    | `feature/timetable/sources/.../setdetail/TimetableSetDetailSheet.kt:86-90`, `feature/onboarding/sources/.../OnboardingPage.kt:18` |

## 서버 계약

stage OpenAPI(`GET /api-docs` → `api-docs/swagger-ui-init.js`의 `swaggerDoc`, "FestiCore Public API 1.0.0")를 읽고, 인증 없는 GET만 보냈다.

| 요청                                                                    | 인증                                                                     | 스펙 · 확인 결과                                                                                                                                                                                                                                                                                                                                                                                                                                                                                 |
| ----------------------------------------------------------------------- | ------------------------------------------------------------------------ | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| `PUT api/v1/devices`                                                    | `X-Device-Id`(필수) + 선택 Bearer                                        | 설명: "처음 등록하는 UUID v4 기기는 Bearer 없이 요청할 수 있습니다 … 등록된 기기를 UUID만으로 갱신할 수 없습니다. 계정 기기는 계정 Bearer로 등록합니다." 본문 `RegisterDeviceDto` 필수 `apnsEnvironment`(enum `sandbox`·`production`) · `apnsToken`(nullable, "APNs 기기 토큰(hex). 알림 권한이 없으면 null") · `appVersion` · `osVersion` · `locale` · `timezone`, 선택 `liveActivityPushToStartToken`. 응답 `data{deviceId, userId, deviceAccessToken, subscriptions[]}`. 쓰기라 호출하지 않음 |
| `GET api/v1/devices`                                                    | 기기                                                                     | 헤더 없이 · 미등록 UUID만 넣어도 401 `{"code":"UNAUTHORIZED","data":null,"message":"인증 토큰이 없습니다."}`. 스펙 응답에 `apnsEnvironment`만 있고 플랫폼 필드 없음                                                                                                                                                                                                                                                                                                                              |
| `PUT` · `DELETE api/v1/devices/bind-user`, `DELETE …/bind-user/recover` | `X-Device-Id` + `X-Device-Access-Token` / `X-Device-Unbind-Key` + Bearer | 스펙의 200 `data`는 `nullable`로만 적혀 있다. iOS는 `{deviceId, userId?, deviceAccessToken?}`로 읽는다(`NDRS:317-321`). 쓰기라 호출하지 않음                                                                                                                                                                                                                                                                                                                                                     |
| `GET` · `PUT api/v1/devices/notification-settings`                      | 기기                                                                     | 헤더 없이 401. 필드 `flagStatus · crewStatus · crewGather · setReminders · homecoming · packUpdates · officialNews`(iOS `DNS:11-19`와 같다). **`setReminders` 설명: "저장한 공연 2시간 전 알림"**                                                                                                                                                                                                                                                                                                |
| `GET api/v1/health`                                                     | 없음                                                                     | 200 `{status:"ok", db:"up", redis:"up"}`                                                                                                                                                                                                                                                                                                                                                                                                                                                         |

**FCM · Android 지원 없음**: 스펙 전체에서 `fcm` · `firebase` · `android` · `platform` · `gcm` 검색 0건. 기기 본문은 APNs 전용(`apnsEnvironment` 필수 enum)이다. iOS 코드에도 FCM · platform 필드가 없다(`iOS/Projects` 검색 0건). → **iOS `apnsToken`에 대응하는 Android 필드는 지금 서버에 없다. 서버 팀 확인 · 추가가 필요하다**(쟁점 P1).

iOS 요청 정의(Android가 그대로 옮길 것):

- 등록 `PUT api/v1/devices`, 헤더 `X-Device-Id`, 계정에 묶이지 않았으면 저장한 기기 토큰을 `Authorization: Bearer`, 묶였으면 세션 Bearer(`NDRS:231-257`). 재시도 2회(`NDRS:246`). 본문은 `apnsToken` · `liveActivityPushToStartToken`이 없어도 **명시적 `null`**로 보낸다(`NDRS:346-358`, 테스트 "APNs 권한이 없어도 명시적인 null 토큰으로 등록 요청한다" `iOS/Service/FestiCoreDevice/Tests/DeviceRegistrationTests.swift:48`). 성공 조건 `code == "SUCCESS"` · `data.deviceId == 보낸 ID`(`NDRS:64-66`).
- 계정 연결 `PUT bind-user`(헤더 `X-Device-Access-Token`) · 해제 `DELETE bind-user`(`X-Device-Unbind-Key`) · 복구 `DELETE bind-user/recover`(키 + 옛 기기 토큰, 401이면 토큰 없이 한 번 더)(`NDRS:110-205 · 259-315`), 재시도 0.
- 저장 키(iOS Keychain): `DEVICE_ID` · `DEVICE_ACCESS_TOKEN` · `DEVICE_BOUND_USER_ID` · `DEVICE_UNBIND_KEY` · `APNS_DEVICE_TOKEN`(`SSK:22-26`). 로그아웃 때 지우는 목록(`SSK:29`)에 기기 키는 없다.
- 기기 인증(`DeviceAuthorization`, `iOS/Service/FestiCoreDevice/Interface/LiveActivityRegistrationService.swift:12`): 묶인 계정 = 현재 세션 사용자면 세션 Bearer, 아니면 기기 토큰. 다른 계정이면 `nil`(`NDRS:97-108`).
- 재로그인 토큰 복원: 해제 · 복구 뒤에는 서버가 푸시 토큰을 지우므로 다음 연결 때 마지막 등록 정보로 한 번 다시 등록(`NDRS:207-218`).

## 수용 기준

1. Firebase 값이 없는 로컬 · CI 빌드: `./gradlew testDebugUnitTest testStageDebugUnitTest :app:assembleStageDebug` 통과, 앱 실행 시 크래시 없음, 기기 등록(토큰 null)과 딥링크는 그대로 동작, 푸시 관련 로그 한 줄만 남김.
2. Firebase 값이 있는 stage 빌드(실기기 · API 33+): 첫 실행에 알림 권한 팝업 → 허용 시 FCM 토큰으로 `PUT api/v1/devices` 성공, 기기 토큰이 `SecureStorage`에 저장(서버가 Android 필드를 받은 뒤).
3. 거부 시 홈(첫 진입 로그인 덮개가 걷힌 뒤)에서 "알림이 꺼져 있어요" 안내 한 번, "설정 열기"는 앱 알림 설정 화면.
4. Firebase 콘솔 테스트 메시지(data: `festivalId` · `festivalName` · `imageUrl` · `deeplink`)가 앱 종료 · 백그라운드 · 포그라운드 모두에서 같은 모양(부제 = 축제 이름, 같은 축제 묶음, https 이미지)으로 보이고, 누르면 딥링크 화면으로 간다.
5. `adb shell am start -a android.intent.action.VIEW -d "festicore://festival/fst_00e0f4f1d8065a93"`로 라이브 보드가 그 축제로 열린다(스플래시 · 온보딩 · 로그인 덮개 중이면 걷힌 뒤).
6. 로그인 완료 → `bind-user` 연결, 세션 만료 → `bind-user/recover`, 다시 로그인 → 연결 + 마지막 등록 복원(MockEngine 테스트와 stage 로그로 확인).
7. 단위 테스트(JVM, MockEngine): iOS `DeviceRegistrationTests`(13) · `FestiCoreDeeplinkTests`(6) · `DeeplinkRoutingTests`(4 중 Android에 있는 경로) · 표시 가공 규칙을 같은 이름으로 옮긴다. 실서버를 부르지 않는다.

## 모듈과 책임

| 모듈                  | 추가 · 변경                                                                                                                                                                                                                                                                                                                                                                        | iOS 대응                                                                          |
| --------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------- |
| `core/utility`        | `FestiCoreDeeplink`(sealed: `FestivalList` · `Festival(id)` · `Flag(id)` · `Set(festivalId, setId)` · `CrewInvite(code)`) · `encoded` · `FestiCoreDeeplinkParser`(URL · 푸시 data `Map<String,String>` · `route`), `RemoteNotificationEnrichment`(순수 Kotlin)                                                                                                                     | `FDL`, `RNE`                                                                      |
| `core/datastore`      | `PendingDeeplinkStore`(DataStore Preferences 키 `FestiCore.pendingDeeplink`, 최신 하나 · 꺼내면 비움). 기기 키 4개는 기존 `SecureStorage`를 쓴다                                                                                                                                                                                                                                   | `PDS` · `PDK`                                                                     |
| `service/api`         | `DeviceApiPath` · `DeviceApi`(register · bind · unbind · recover), DTO. 등록 본문은 null을 명시해 보내는 별도 직렬화(아래 쟁점 P6)                                                                                                                                                                                                                                                 | `NDRS:231-371`                                                                    |
| `service/device`      | `DeviceRegistrationService`(register · authorization · linkDeviceToCurrentUser · unbindCurrentUser · recoverBoundDevice, Mutex), `DeviceUserSessionProvider` 인터페이스, `DeviceRegistrationMetadata`(Android 필드는 P1 결정 후)                                                                                                                                                   | `DRS` · `NDRS`                                                                    |
| `domain/auth/sources` | `DeviceUserSessionProvider` 구현(세션 ID = `AuthSession.currentSessionId`, 사용자 = `CurrentUserStore`) 등록                                                                                                                                                                                                                                                                       | `iOS/Service/ServiceAssembly/Sources/NetworkContainer.swift:51-62`                |
| `feature/festival`    | `FestivalCoordinator.onDeeplinkFestivalOpened(id)` · `onDeeplinkFestivalListOpened()`(iOS `openWidgetFestival` · `showFestivalList`)                                                                                                                                                                                                                                               | `MTC:357-369`                                                                     |
| `app`                 | 매니페스트(권한 · `festicore` 스킴 · 메시지 서비스), `FestiCoreApplication`(Firebase 수동 초기화 · 채널), `MainActivity`(권한 요청 · 인텐트 → 브리지 · `onNewIntent`), `AppDeeplinkBridge`, `FestiCoreMessagingService`, `PushRegistration`(토큰 → 등록 직렬 실행), `MainTabCoordinator`(탭 선택 · 딥링크 분기), `AppCoordinator`(대기 딥링크 꺼내기 · 계정 변경 → 기기 연결/복구) | `AD*` · `FA` · `BRG` · `AC` · `MTC` · `ADS`                                       |
| `build-logic`         | Firebase 값 4개를 `BuildConfig`로(없으면 빈 문자열)                                                                                                                                                                                                                                                                                                                                | `ADC:16`, `iOS/Service/FestiCoreConfig/Sources/FirebaseConfiguration.swift:14-27` |

의존 방향은 기존 규칙 그대로다: `service/device → service/api · service/auth · core/datastore`(라이브 보드 선례 `service/festivalcatalog`가 `service/device` · `service/auth`에 의존), `domain/auth/sources → service/device`(domain sources → service 허용). `service`는 `domain`을 보지 않으므로 사용자 ID는 `DeviceUserSessionProvider`로 거꾸로 주입한다.

## 설계

### 1. 기기 등록 (`service/device`)

`NDRS`를 그대로 옮긴다. 차이는 아래만.

- 저장: `SecureStorage` 키 `deviceAccessToken` · `deviceBoundUserID` · `deviceUnbindKey`(기존 `deviceID` 이름 규칙). APNs 토큰 저장(`ADP:33-44`)은 옮기지 않는다 — FCM은 `FirebaseMessaging.getInstance().token`으로 언제든 현재 토큰을 다시 받는다.
- 사용자 세션: iOS는 `auth.currentUserID`(`NetworkContainer.swift:54-60`)를 쓰지만 Android `CurrentUserStore`는 재확인 전 `null`이다. 그대로 옮기면 앱 재시작 직후 묶인 기기의 `register`가 `accountMismatch`(`NDRS:57`)로 실패한다. → 묶인 기기인데 사용자가 아직 `null`이면 **오류 대신 건너뛰고**, `currentUserId`가 채워지면 다시 등록한다(쟁점 P5).
- 등록 직렬화: iOS는 이전 등록 Task를 기다리고 같은 메타데이터면 건너뛴다(`ADR:132-153`). Android `PushRegistration`(app, 싱글턴)이 `Mutex` + 마지막 메타데이터로 같게 한다. 등록 성공 후 로그인 상태면 `linkDeviceToCurrentUser`(`ADR:140-149`).
- 메타데이터: `appVersion = BuildConfig.VERSION_NAME`, `osVersion = Build.VERSION.RELEASE`, `locale = Locale.getDefault().toLanguageTag()`(iOS `identifier(.bcp47)`), `timezone = ZoneId.systemDefault().id`(`ADR:117-124`). 푸시 환경 · 토큰 필드는 P1 결정대로.
- 등록 시점: 앱 시작(`ADC:19` → `ADR:30-34`, 저장 토큰으로 바로 등록), 새 토큰(`onNewToken`), 계정 사용자 채워짐.

### 2. FCM 도입 (쟁점 P2 추천안)

- 의존성: `com.google.firebase:firebase-bom:35.2.0` + `firebase-messaging`(BOM이 26.0.2로 맞춤, Google Maven `maven-metadata.xml` 2026-11-11 기준 최신). google-services Gradle 플러그인은 쓰지 않는다.
- 값: `local.properties`(비추적) 또는 CI 환경 변수에서 flavor별 `FESTICORE_FIREBASE_{STAGE,PROD}_{API_KEY,APP_ID,PROJECT_ID,SENDER_ID}`를 읽어 `BuildConfig.FIREBASE_*`로(`festicore.android.application.gradle.kts:35-43` 옆). 없으면 빈 문자열. 추적 파일 `app/config/*.properties`에는 넣지 않는다(Hard 8 취지 — `google-services.json`과 같은 값).
- 초기화: `FestiCoreApplication.onCreate`에서 4개가 모두 있으면 `FirebaseOptions.Builder().setApiKey().setApplicationId().setProjectId().setGcmSenderId()` → `FirebaseApp.initializeApp(context, options)`. 하나라도 없으면 초기화하지 않고 `FestiCoreLogger.notice("이 빌드는 푸시 설정이 없음")`(iOS `FirebaseConfiguration.swift:15-25`와 같은 처리). 이때 `PushRegistration`은 토큰 `null`로만 등록하고 메시지 서비스는 아무것도 하지 않는다.
- 토큰 받기: `FirebaseMessaging.getInstance().token`의 `Task`를 `suspendCancellableCoroutine`으로 감싼다(새 라이브러리 `kotlinx-coroutines-play-services` 없이).
- 로그: 토큰 값은 로그 · 분석 · 테스트 실패 메시지에 넣지 않는다(Hard 8).

### 3. 알림 권한 · 채널

iOS 시점(`ADP:14-21`, `FA:64-69 · 87-92`, `MTC:283-322`):

| 시점                 | iOS                                                                                                                                                                                                                                                                                                        | Android                                                                                                                                                                                            |
| -------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 앱 시작              | `didFinishLaunching`에서 바로 권한 요청(미결정이면 팝업). 결과와 무관하게 APNs 등록                                                                                                                                                                                                                        | `MainActivity.onCreate`(첫 생성, `savedInstanceState == null`)에서 API 33+이고 미허용이면 `ActivityResultContracts.RequestPermission`으로 `POST_NOTIFICATIONS` 요청. 결과와 무관하게 FCM 토큰 등록 |
| 홈 진입 · 포그라운드 | 홈일 때 권한 상태를 다시 풀어 결과를 홈에 넘김(진행 중 요청 재사용)                                                                                                                                                                                                                                        | 홈 진입 · `ON_START`에 `NotificationManagerCompat.areNotificationsEnabled()`(API 33+는 권한 상태도) 읽어 `AppCoordinator`에 넘김. 다시 묻지 않는다(아래)                                           |
| 거부                 | 홈에서 한 번(`hasShownNotificationSettingsAlert`, 메모리) 첫 진입 덮개가 걷힌 뒤 `FCAlert` "알림이 꺼져 있어요" / "알림을 받으려면 설정에서 알림을 허용해 주세요. 알림 없이도 앱을 사용할 수 있어요." / "설정 열기" · "나중에"(`iOS/App/Sources/Navigation/MainTab/FCAlertState+Notification.swift:13-19`) | 같은 문구 · 같은 조건. "설정 열기" = `Settings.ACTION_APP_NOTIFICATION_SETTINGS`(+`EXTRA_APP_PACKAGE`)                                                                                             |
| 결과 값              | `authorized` · `denied` · `unavailable`(`NAC:11-15`)                                                                                                                                                                                                                                                       | 같은 3값. API 32 이하는 런타임 권한이 없어 `areNotificationsEnabled()`만 본다                                                                                                                      |

- 홈 진입 때 iOS는 미결정이면 다시 요청할 수 있지만(`ADR:64-85`) Android는 앱 시작 요청과 겹쳐 팝업이 두 번 뜰 수 있어 읽기만 한다. 차이는 PR 본문에 적는다(`ios-parity.md` "다르게 두는 것").
- 채널: iOS에는 없다. Android는 `FestiCoreApplication`에서 채널 하나 `festicore_default`(이름 "알림", `IMPORTANCE_DEFAULT`)를 만든다(쟁점 P4). 종류별 끄기는 서버 알림 설정(`notification-settings`)이 맡는다(iOS와 같다).

### 4. 표시 푸시 가공 (iOS NSE 대응)

iOS 규칙(`NSE:20-57 · 62-99`, `RNE:12-39`): `festivalId` → 묶음 `festival.{id}`(비었을 때만), `festivalName` → 부제목(비었을 때만), `imageUrl`이 `https` · 호스트 있음이면 내려받아 200~299 · JPEG/PNG/GIF · 0 < 크기 ≤ 10MB만 첨부, 시간이 끝나면 지금까지 만든 내용으로 한 번만 전달. 앱을 보고 있을 때도 배너 · 소리 · 목록(`ADP:61-66`).

Android(`FestiCoreMessagingService : FirebaseMessagingService`):

- `onMessageReceived`에서 data의 `title` · `body`(키 이름은 P3 서버 확인)로 `NotificationCompat`을 만든다. `setGroup("festival.{id}")` · `setSubText(festivalName)` · 이미지는 같은 검사 후 `BigPictureStyle`(+ `setLargeIcon`), 실패 · 시간 초과면 이미지 없이 보낸다. 앱이 앞에 있어도 같은 알림을 올린다(iOS 포그라운드 배너와 같음).
- 누르면 `PendingIntent`(`FLAG_IMMUTABLE`)로 `MainActivity`에 data 전체를 extras로 → `AppDeeplinkBridge.handlePushPayload`(`ADP:69-74`).
- 알림 ID: 같은 묶음이 쌓이도록 메시지마다 다른 ID(서버 `messageId` 해시), 묶음 요약 알림 하나.
- **전제**: Android는 `notification` 페이로드가 있는 메시지를 앱이 백그라운드일 때 시스템이 바로 그려 `onMessageReceived`를 부르지 않는다. iOS처럼 항상 가공하려면 서버가 **data 전용 메시지**(높은 우선순위)로 보내야 한다(쟁점 P3).
- 가공 규칙은 `core/utility` `RemoteNotificationEnrichment`(순수 함수)로 두고 JVM 테스트한다. 다운로드는 OkHttp(이미 Ktor · Coil 엔진으로 들어와 있음)로, 서비스 실행 시간 제한 안에서 끝낸다.

### 5. 무음 동기화 (iOS `content-available` 대응)

- iOS: 씬(화면)이 있으면 `remoteContentChanged` → 라이브 탭 스택의 목록 또는 보드가 다시 읽고 끝나면 `newData`, 씬이 없으면 바로 `noData`(`ADR:16-28`, `FA:70-81`, `AC:256-258`, `FC:624-637`).
- Android: 표시 필드가 없는 data 메시지(구분 키는 P3 서버 확인) → `RemoteContentEvents`(app 싱글턴 `MutableSharedFlow`, 버퍼 0)에 보낸다. 구독자(`MainActivity`가 떠 있을 때 `AppCoordinator` → `FestivalCoordinator`가 목록 · 보드에 전달)가 없으면 버린다. `WorkManager`로 백그라운드 동기화하지 않는다(iOS도 화면 없으면 하지 않음, 다음 실행에 새로 받음).

### 6. 딥링크 (iOS 4단 그대로)

| 단             | iOS                                                                                                                                                                                                                                                                                                                                                                                                                 | Android                                                                                                                                                                                                            |
| -------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| 1. 경로 · 파싱 | `FestiCoreDeeplink` · `FestiCoreDeeplinkParser`(`FDL:11-171`): 스킴 `festicore`, user · password · port · query · fragment 없음, host = 자원, 조각은 퍼센트 디코딩 후 ASCII 영숫자 · `-` · `_`만. `festival` / `festival/{id}` / `festival/{id}/set/{setId}` / `flag/{id}` / `crew/{6자리}`(I·O·0·1 제외, 대문자로). 푸시는 `deeplink` → `route`(`/festivals/{id}...` · `/flags/{id}`) → `flagId` → `festivalId` 순 | `core/utility` 같은 이름 · 같은 규칙(`java.net.URI`), 푸시 입력은 `Map<String, String>`(FCM data)                                                                                                                  |
| 2. 대기        | `PendingDeeplinkStore`: 최신 하나만 URL 문자열로 영속 저장, 꺼내면 `nil`(`PDS:13-25`, `PDK:14-28`)                                                                                                                                                                                                                                                                                                                  | `core/datastore` DataStore 키 `FestiCore.pendingDeeplink`, `save` · `consume`(원자적 `edit`)                                                                                                                       |
| 3. 브리지      | `AppDeeplinkBridge`: URL · 푸시 → 대기 저장 + `Notification.Name.festiCoreDeeplink` 알림. 딥링크가 아닌 URL은 `false`(OAuth로 넘김)(`BRG:24-51`, `FA:55-63`)                                                                                                                                                                                                                                                        | `app` `AppDeeplinkBridge`: `MainActivity.onCreate` · `onNewIntent`의 `ACTION_VIEW` data와 푸시 extras → 저장 + `SharedFlow` 신호. Android Google 로그인은 Credential Manager라 URL 콜백이 없어 `false` 분기는 무시 |
| 4. 라우팅      | `AppCoordinator.deeplinkReceived`: 홈이고 덮개 없음일 때만 꺼냄. 홈 진입 · 첫 진입 덮개 닫힘 · 가입 덮개 닫힘 때 다시 시도(`AC:146-158 · 268-275`, `iOS/App/Sources/Navigation/Main/AppCoordinator+Route.swift:86-89`). 탭 분기 `MTC:352-396`                                                                                                                                                                       | `AppCoordinator.onDeeplinkReceived()`: `routes == [Home]` · `authCover == null`일 때만 `consume` → `MainTabCoordinator.open(deeplink)`. 홈 진입 · 덮개 닫힘(`withoutCover` 직후)에서도 호출                        |

탭 분기(`MTC:357-394`)의 Android 범위:

| 경로                            | iOS                                                                                                                                   | Android P0                                                                                                                  |
| ------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------- |
| `FestivalList`                  | 라이브 탭 + 목록                                                                                                                      | 라이브 탭 + `FestivalCoordinator.onDeeplinkFestivalListOpened()`                                                            |
| `Festival(id)`                  | 라이브 탭 + `openWidgetFestival(id)`(저장된 팩이 있는 축제만 보드, 테스트 `iOS/App/Tests/Sources/DeeplinkRoutingTests.swift:53 · 94`) | 라이브 탭 + `onDeeplinkFestivalOpened(id)`. `openWidgetFestival` 세부(팩 없음 · 캐시 오류 안내)는 P0b에서 iOS를 열어 옮긴다 |
| `Set(festivalId, setId)`        | 축제 열기 → 타임테이블 탭 → `setTapped(setId)`(`MTC:371-377`)                                                                         | 파싱 · 대기만. 라우팅은 **C1**(타임테이블 탭 연결)에서 `onSetClicked` 연결 — 탭이 아직 없다                                 |
| `Flag(id)` · `CrewInvite(code)` | 깃발 탭 상세 · 크루 초대                                                                                                              | 파싱만. 해당 기능이 없어 꺼낸 뒤 디버그 로그만 남긴다                                                                       |

- 탭 선택을 밖에서 바꾸려면 `MainTabScreen`의 지역 상태(`MainTabScreen.kt:39`)를 `app` `MainTabCoordinator`(iOS 같은 이름, `selectedTab` State)로 올린다(쟁점 P7).
- 매니페스트: `MainActivity`에 `ACTION_VIEW` · `BROWSABLE` · `DEFAULT` + `android:scheme="festicore"` 필터(iOS `CFBundleURLSchemes festicore`, `iOS/App/Derived/InfoPlists/FestiCore-Info.plist:34-37`), `android:launchMode="singleTop"` + `onNewIntent`(쟁점 P8).

### 7. 계정 변경 → 기기 등록 갱신 · 해제

| 계기                           | iOS                                                                                                                                            | Android 자리                                                                                                                        |
| ------------------------------ | ---------------------------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------- |
| 로그인 완료                    | `linkDeviceForCurrentUser`(`iOS/App/Sources/Navigation/Auth/AuthCoordinator.swift:261-267`)                                                    | `AppCoordinator`가 `updateCurrentUserId(userId)`를 부르는 곳(`AppCoordinator.kt:51-53` 목록: 재확인 성공 · 로그인 완료 · 가입 완료) |
| 세션 복원(재시작 · 포그라운드) | 로그인이면 연결, 아니면 `releaseSignedOutDevice`(`iOS/App/Sources/Navigation/Session/SessionCoordinator.swift:244-253`)                        | 위와 같음 + 재확인에서 비로그인이면 복구                                                                                            |
| 세션 만료                      | `releaseSignedOutDevice` = `recoverBoundDevice` + 라이브 액티비티 끝내기(`SessionCoordinator.swift:220-224`, `ADS:44-47`)                      | `updateCurrentUserId(null)` 자리(세션 만료 · 재확인 401 · 로그인 직후 401) → `recoverBoundDevice`. 라이브 액티비티 끝내기는 없음    |
| 로그아웃 · 탈퇴                | 요청 전 `unbindCurrentUser`, 실패해도 진행, 계정 요청 실패 시 다시 연결(`iOS/App/Sources/Navigation/Account/AccountCoordinator.swift:159-188`) | Android에 기능 없음. 옮기는 PR에서 같은 순서로 넣는다(이 문서 범위 밖)                                                              |

`AppCoordinator`에 `linkDevice: suspend () -> Unit` · `releaseDevice: suspend () -> Unit`를 생성자 주입하고(`appModule`의 기존 람다 주입 방식, `AppModules.kt:32-42`), 실패는 로그만 남기고 화면 흐름을 막지 않는다(`ADS:19-41`).

### 8. 라이브 액티비티 경계 (나중 단계)

- P0에서는 등록 본문 `liveActivityPushToStartToken`을 항상 `null`로 보낸다(스펙상 선택 필드). `api/v1/devices/live-activity`(`iOS/Service/FestiCoreDevice/Sources/NetworkLiveActivityRegistrationService.swift:24 · 32 · 56`) · `LiveActivityTokenSync`(`iOS/App/Sources/Application/LiveActivityTokenSync.swift`) · `endLocalLiveActivities`(`ADS:49-53`)는 옮기지 않는다.
- 다음 단계 문서가 쓸 접점: `DeviceRegistrationService.authorization()`(기기 인증)과 `PushRegistration`의 "메타데이터 바뀌면 재등록" 경로. Android 대응(진행 중 알림 등)은 `architecture.md` "Live Activity · Widget · Extension → 별도 결정"에 따른다.

## iOS → Android 적응

| 항목                   | iOS                                                                                    | Android                                                                             | 이유                                    |
| ---------------------- | -------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------- | --------------------------------------- |
| 푸시 토큰              | APNs hex, `APNS_DEVICE_TOKEN`에 저장 후 시작 때 재사용(`ADP:23-44`, `ADR:105-114`)     | FCM 등록 토큰, 저장하지 않고 `FirebaseMessaging.token`으로 다시 받음 · `onNewToken` | FCM이 토큰을 보관 · 갱신 알림           |
| 푸시 환경              | `FestiCoreAPNsEnvironment` Info.plist → `sandbox` · `production`(`PRS:22-27`)          | P1 서버 결정 대기                                                                   | 서버 enum이 APNs 전용                   |
| 설정 파일              | `GoogleService-Info.plist` 없으면 Firebase 건너뜀(`FirebaseConfiguration.swift:15-25`) | `BuildConfig.FIREBASE_*` 비면 건너뜀                                                | 같은 동작, 파일 대신 값                 |
| 권한 요청              | 시작 때 + 홈 · 활성 때 재요청 가능                                                     | 시작 때만 요청, 홈 · `ON_START`는 읽기                                              | API 33 팝업 중복 방지                   |
| 알림 채널              | 없음                                                                                   | 채널 1개                                                                            | Android 8+ 필수                         |
| 표시 가공              | NSE(앱 꺼져도 항상 실행)                                                               | `onMessageReceived`(data 전용 메시지일 때만 항상 실행)                              | 플랫폼 차이 → 서버 페이로드 형식        |
| 묶음 · 부제목 · 이미지 | `threadIdentifier` · `subtitle` · `UNNotificationAttachment`                           | `setGroup` · `setSubText` · `BigPictureStyle`                                       | 같은 규칙, 대응 API                     |
| 무음                   | `content-available` + 완료 콜백                                                        | data 메시지, 화면 있을 때만 이벤트                                                  | 같은 범위                               |
| 대기 저장              | `@Shared(.persistent)`(UserDefaults)                                                   | DataStore Preferences                                                               | `ios-parity.md` 대응표                  |
| 딥링크 신호            | `NotificationCenter`                                                                   | `SharedFlow`                                                                        | 같은 역할                               |
| URL 진입               | `onOpenURL`                                                                            | `ACTION_VIEW` 인텐트 필터 + `onNewIntent`                                           | 플랫폼 관례                             |
| 사용자 ID              | 저장된 세션 사용자                                                                     | 메모리 `CurrentUserStore`, 채워질 때 재등록                                         | Android 재시작 직후 `null`              |
| 본문 null              | `encodeNil` 명시                                                                       | 등록 DTO만 null 명시                                                                | `FestiCoreJson` `explicitNulls = false` |

## PR 묶음 (작게)

| PR                        | 내용                                                                                                                                                                                                                                                                                       | 검증                                                                                                                                                                                                   | 서버 의존                   |
| ------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ | --------------------------- |
| P0a 딥링크 기반           | `core/utility` `FestiCoreDeeplink` · 파서, `core/datastore` `PendingDeeplinkStore`, `app` `AppDeeplinkBridge` · 매니페스트 `festicore` 필터 · `singleTop` · `onNewIntent`, `MainTabCoordinator`(탭 선택 이동), `AppCoordinator.onDeeplinkReceived`, `FestivalCoordinator` 축제 · 목록 열기 | `FestiCoreDeeplinkTests` 6건 같은 이름, 대기 저장 왕복, `DeeplinkRoutingTests` 중 `Festival` · `FestivalList` 사례, `AppCoordinatorTest`(덮개 중 대기 → 닫히면 소비), `adb am start` 수동(수용 기준 5) | 없음                        |
| P0b 기기 등록 · 계정 연결 | `service/api` `DeviceApi` · DTO(등록 null 명시), `service/device` `DeviceRegistrationService` · 키 3개 · `DeviceUserSessionProvider`, `domain/auth/sources` 구현 등록, `AppCoordinator` link/release 연결, `PushRegistration`(토큰 null 등록)                                              | `DeviceRegistrationTests` 13건 같은 이름(MockEngine: 헤더 · 명시적 null · 저장 · 묶음 · 해제 · 복구 401 재시도 · 동시 연결 공유 · 계정 전환), 사용자 `null` 건너뛰기 테스트                            | **P1 확정 필요**(본문 필드) |
| P0c FCM · 권한 · 채널     | 카탈로그 `firebase-bom` · `firebase-messaging`, `build-logic` `FIREBASE_*`, 수동 초기화, 채널, `POST_NOTIFICATIONS` 선언 · 시작 요청, 홈 거부 안내, `onNewToken` → 재등록                                                                                                                  | 값 없는 빌드 `assembleStageDebug` · 실행 크래시 없음, 값 있는 빌드 실기기 토큰 등록(수용 기준 1~3), 권한 결과 → 안내 1회 `AppCoordinatorTest`                                                          | P1, 라이선스 승인           |
| P0d 표시 가공 · 무음 · 탭 | `FestiCoreMessagingService`, `RemoteNotificationEnrichment`, 이미지 검사 · 다운로드, 누름 → 브리지, `RemoteContentEvents` → 목록 · 보드 재조회                                                                                                                                             | 가공 규칙 JVM 테스트(묶음 · 부제목 비었을 때만, https만, MIME 3종, 10MB), 콘솔 테스트 메시지 3상태 수동(수용 기준 4)                                                                                   | **P3 확정 필요**(페이로드)  |

순서: P0a(서버와 무관, 먼저) → P0b → P0c → P0d. 타임테이블 문서의 "P0 머지" 게이트는 **P0a~P0d가 모두 develop에 들어간 상태**를 뜻한다. `Set` 딥링크 라우팅은 C1에서 연결한다(C1 검증에 "공연 알림 링크는 축제를 고르고 타임테이블에서 공연 상세를 연다" `DeeplinkRoutingTests.swift:32` 추가). 각 PR은 `.claude/scripts/verify --all` · CI 통과 후 squash merge.

기능 spec(`.spec/`): P0a `core/utility/.spec/deeplink.md`, P0b `service/device/.spec/device-registration.md`, P0c · P0d `app/.spec/push.md`.

## 새 라이브러리

| 후보                                                                     | 용도                            | 라이선스                                                                                                                                                                                               | 판단                                                  |
| ------------------------------------------------------------------------ | ------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ | ----------------------------------------------------- |
| `com.google.firebase:firebase-bom:35.2.0` · `firebase-messaging`(26.0.2) | FCM 토큰 · 수신                 | Apache-2.0(POM `The Apache Software License, Version 2.0`). **전이 의존 `play-services-cloud-messaging` 17.4.0 · `play-services-base` 18.9.0 등은 "Android Software Development Kit License"**(각 POM) | 추천. Hard 12에 따라 라이선스 승인 필요               |
| `com.google.gms:google-services` 플러그인 4.6.0                          | `google-services.json` → 리소스 | Apache-2.0로 알려졌으나 POM 미확인                                                                                                                                                                     | 쓰지 않음(P2)                                         |
| `kotlinx-coroutines-play-services`                                       | `Task.await()`                  | Apache-2.0                                                                                                                                                                                             | 쓰지 않음. `suspendCancellableCoroutine` 몇 줄로 충분 |
| `androidx.work`                                                          | 무음 동기화                     | Apache-2.0                                                                                                                                                                                             | 쓰지 않음(iOS도 화면 없으면 안 함)                    |

`NotificationCompat`(`androidx.core`) · `ActivityResultContracts`(`androidx.activity`) · DataStore · OkHttp는 이미 들어와 있다.

## 사용자 조치

1. **Firebase 콘솔**: iOS가 쓰는 Firebase 프로젝트(같은 프로젝트인지 확인)에 Android 앱 2개 등록 — `io.festiCore.co`(prod) · `io.festiCore.co.stage`(stage). FCM만 쓰면 SHA 인증서 지문은 필요 없다(Google 로그인은 별도 OAuth 클라이언트로 이미 동작).
2. **설정 값 전달**: 앱마다 API 키 · 앱 ID(`mobilesdk_app_id`) · 프로젝트 ID · 발신자 ID(`project_number`)를 로컬 `local.properties`의 `FESTICORE_FIREBASE_{STAGE,PROD}_*`에 넣는다. 저장소 · 이슈 · PR에 붙이지 않는다. CI에서 푸시 켠 빌드가 필요할 때만 GitHub Secrets에 같은 이름으로 둔다(지금 PR CI는 값 없이 통과해야 한다).
3. **서버 팀 요청**: (a) 기기 등록에 Android 필드 추가(P1), (b) FCM 발송 — Firebase 프로젝트의 서비스 계정 키(FCM HTTP v1)를 서버 비밀로 등록(레거시 서버 키가 아닌 HTTP v1 방식인지는 Firebase 문서로 서버 팀이 확인), (c) Android용 페이로드 형식 합의(P3), (d) `setReminders` 시점(스펙 "2시간 전" vs 앱 문구 "10분 전") 확인.
4. **라이선스 승인**: Firebase Messaging과 전이 Play 서비스 라이브러리(Android SDK License).
5. **실기기 확인**: API 33+ 기기 1대, Firebase 콘솔 "테스트 메시지"로 수용 기준 2~4.

## 위험

1. **서버가 Android를 모른다**: P0b~P0d가 서버 변경에 막힌다. 서버 변경 전 Android가 `apnsEnvironment`에 임의 값을 넣으면 서버가 FCM 토큰을 APNs로 보내거나 등록을 거절할 수 있다 → P1 확정 전 P0b를 머지하지 않는다.
2. **백그라운드 표시 차이**: 서버가 `notification` 페이로드로 보내면 백그라운드에서 묶음 · 부제목 · 이미지 가공이 빠지고 누름도 data extras로만 온다 → P3.
3. **재시작 직후 사용자 `null`**: 묶인 기기 등록 시점이 재확인 결과에 달린다. 재확인이 오프라인이면 등록이 늦어진다(푸시 토큰 갱신 지연).
4. **알림 시점 문구 불일치**: 서버 스펙 "2시간 전" vs iOS · Android 문구 "10분 전". 서버가 맞는지 문구가 맞는지 정해야 C1 게이트의 취지(약속한 알림이 실제로 온다)가 성립한다.
5. **탭 선택 상태 이동(P7)**: `MainTabScreen` 상태를 옮기면 라이브 흐름의 탭 바 숨김 · 깃발 탭 요청 경로도 함께 바뀐다.

## 쟁점 (추천안 포함)

- P1. Android 푸시 토큰 필드: (a) 서버에 `platform`(`ios`·`android`) + `fcmToken`(nullable)을 추가하고 Android는 `apnsEnvironment` · `apnsToken`을 보내지 않게(또는 서버가 Android면 선택으로) 바꿔 달라고 요청(추천, 스펙 · 서버 팀 확인 필요). (b) `apnsToken`에 FCM 토큰을 넣음 — 서버가 APNs로 보낸다, 금지. (c) 서버 변경 전 `apnsToken: null`로 익명 등록만 — `apnsEnvironment` 필수 enum에 맞는 Android 값이 없다.
- P2. FCM 초기화: (a) 플러그인 없이 `local.properties` · CI 환경 변수 → `BuildConfig` → `FirebaseOptions` 수동 초기화, 값 없으면 푸시만 끔(추천 — 추적 파일 없음, CI 무변경, flavor별 값이 `build-logic` 한 곳). (b) `google-services` 플러그인 + 비추적 `app/src/{stage,prod}/google-services.json` — 파일이 없으면 빌드 작업이 실패하는지 확인 필요(실패하면 CI · 새 clone이 깨지므로 CI 비밀에서 파일을 쓰는 단계가 필요), Firebase 문서 기본 경로라 콘솔 파일을 그대로 쓰는 장점.
- P3. 서버 푸시 페이로드: (a) data 전용 메시지(높은 우선순위), 키는 iOS userInfo와 같게(`title` · `body` · `deeplink` · `route` · `flagId` · `festivalId` · `festivalName` · `imageUrl`), 무음은 표시 키 없는 data + 구분 키(추천, iOS와 같은 가공을 모든 상태에서). (b) `notification` + `android.notification.image` · `tag` · `channel_id` — 서버 쪽 구성만으로 이미지 · 묶음 일부 가능, 백그라운드 누름 경로가 갈라지고 부제목(`subText`) 대응이 없다.
- P4. 채널: (a) 1개(추천, iOS에 구분 없음 · 종류별 끄기는 서버 설정). (b) 알림 설정 종류별 7개 — 시스템 설정과 앱 설정이 이중으로 생기고 서버 페이로드에 종류 키가 필요.
- P5. 재시작 직후 사용자 미확인: (a) 묶인 기기 + 사용자 `null`이면 등록을 건너뛰고 `currentUserId`가 채워질 때 재등록(추천, 기존 `CurrentUserStore` 그대로). (b) 묶인 사용자 ID를 세션과 함께 저장해 iOS처럼 즉시 비교 — 세션 저장 형식(`CredentialStore`) 변경.
- P6. 등록 본문 null: (a) 등록 DTO만 `buildJsonObject`(또는 `explicitNulls = true`인 전용 `Json`)로 null 명시(추천, iOS 테스트 `DeviceRegistrationTests.swift:48`과 스펙 필수 필드). (b) `FestiCoreJson` 전역 변경 — 다른 요청 서버 검증이 깨진다(`network-storage.md:21`).
- P7. 탭 선택: (a) `app` `MainTabCoordinator`(Orbit, `selectedTab` · 딥링크 분기, iOS 같은 이름)로 올림(추천, C1의 `Set` 분기 · 이후 깃발 탭도 같은 자리). (b) `AppCoordinatorState.selectedTab` — 루트 상태가 탭 세부까지 가진다. (c) `MainTabScreen` 인자로 "열 탭" 이벤트만 — 분기 로직이 Composable에 남는다.
- P8. 인텐트 수신: (a) `singleTop` + `onNewIntent` + 알림 `PendingIntent`에 `FLAG_ACTIVITY_SINGLE_TOP or CLEAR_TOP`(추천, 최소 변경). (b) `singleTask` — 외부 앱에서 링크를 열 때 태스크가 하나로 모이지만 뒤로 가기 동작이 바뀐다. 외부 링크의 태스크 동작은 기기에서 확인한다.
- P9. 홈 진입 재요청: (a) 시작 때만 요청, 이후 읽기(추천, 팝업 중복 없음). (b) iOS처럼 홈 · 포그라운드마다 미결정이면 요청 — 시작 요청과 겹친다.

## 확인 못 한 것

- 서버가 Android 기기 · FCM을 받을 계획과 필드 이름(스펙 · iOS 코드 모두 0건). **서버 팀 확인 필요.**
- 실제 푸시 페이로드 키와 무음 구분 키. iOS 파서(`FDL:53-67`) · 가공(`RNE:12-19`)이 읽는 키만 근거다. **서버 팀 확인 필요.**
- `setReminders` 발송 시점: 스펙 "저장한 공연 2시간 전" vs 앱 문구 "10분 전"(`iOS/UI/FestiCoreSharedUI/Sources/Sheet/FCSetSheet.swift:38`). **서버 팀 확인 필요.**
- `bind-user` 계열 200 응답 `data` 구조: 스펙은 `nullable`만 적었다. iOS 디코딩(`NDRS:317-321`)만 근거.
- 등록 · 연결 성공 · 오류 응답 본문(쓰기라 호출하지 않음). stage는 헤더 없는 401만 봤다.
- iOS와 같은 Firebase 프로젝트를 쓰는지, prod · stage가 프로젝트를 나누는지.
- google-services 플러그인 4.6.0이 파일 없을 때 실패하는지, 수동 초기화 때 기본 `FirebaseInitProvider`가 리소스 없이 조용히 넘어가는지 — P0c에서 빌드 · 실행으로 확인.
- `onMessageReceived` 실행 시간 제한 값과 높은 우선순위 data 메시지 제한 — P0d에서 Firebase 문서로 확인.
- iOS `openWidgetFestival`(팩 없는 축제 · 캐시 오류 처리, `DeeplinkRoutingTests.swift:13 · 53 · 94`) 세부 — P0a에서 `FC`를 열어 옮긴다.
- Android `DeviceInstallationService` 주석 "iOS Keychain 키 `deviceID`와 같은 이름"(`DeviceInstallationService.kt:21-22`)과 iOS 실제 값 `DEVICE_ID`(`SSK:22`)가 다르다. 저장소가 달라 동작 영향은 없고 주석만 틀렸다(이 문서는 코드를 고치지 않음).
- iOS 문서 `../FestiCore-iOS/.claude/docs/push-deeplink.md:5`는 NSE를 "통과 구현"이라 적지만 코드(`NSE:20-57`)는 이미 가공한다. 이 문서는 코드를 따랐다.

## 결정

호스트 결정 전. 정해지면 이 절에 날짜와 함께 쟁점 번호별 선택을 적는다.

## 결정 (2026-11-22)

- 사용자 승인: firebase-bom · firebase-messaging(Apache-2.0)과 전이 의존 play-services-base · cloud-messaging(Android SDK License) 도입 승인.
- 서버 Android 필드(P1) · FCM 서비스 계정 등록 · data 전용 페이로드(P3) · setReminders 시점 확인 · Firebase Android 앱 등록은 사용자가 서버 팀과 처리한다. P0b · P0d는 서버 계약이 확정되면 시작하고, 그 전에 P0a(딥링크) · P0c(FCM 수동 초기화 · 권한 · 채널, 값이 없으면 푸시 꺼짐)를 먼저 구현한다.
- P2~P9: 추천안대로.
- (Codex R1 PUSH-001 반영) 기기 복구 시점: `updateCurrentUserId(null)` 변화만 기다리지 않는다. 시작 시 세션 복원(`checkSession`)이 "세션 없음"을 확인하면 `enterHome()` 전에 기기 복구(`recoverIfBound`)를 명시적으로 부르고, 이후 로그아웃 · 만료 · 401로 비로그인이 될 때도 부른다. 복구는 멱등이다(묶여 있지 않으면 아무것도 하지 않음, 같은 상태로 여러 번 불러도 요청 1회). 테스트: 새 프로세스 + 세션 없음 + 묶인 기기 → 복구 1회, 반복 시작 → 추가 요청 없음(P0b).
- (Codex R1 PUSH-002 반영) 알림 누름 `PendingIntent`는 알림마다 고유 신원을 갖는다: requestCode = 메시지 ID 해시, intent data = `festicore-notification://{messageId}`(라우팅용 딥링크 · 페이로드는 extras 그대로). `FLAG_IMMUTABLE | FLAG_UPDATE_CURRENT`. 테스트: 서로 다른 두 알림의 PendingIntent가 같지 않음(`filterEquals` false), 오래된 알림 누름이 자기 딥링크로 라우팅(P0d).
- (Codex R2 PUSH-003 반영) P0c를 둘로 나눈다. **P0c1**(P1 전 가능): Firebase 수동 초기화(값 없으면 꺼짐) · `POST_NOTIFICATIONS` 요청 · 거부 안내 · 알림 채널 · FCM 토큰 읽기(메모리, 서버로 보내지 않음). 수용 기준: 설정 값 없으면 빌드 · 실행 정상 + 푸시 꺼짐, 값 있으면 토큰 발급 로그(값은 로그에 남기지 않음). **P0c2**(P0b · P1 뒤): `onNewToken` → 재등록, 시작 시 토큰으로 등록. PR 순서: P0a → P0c1 → (서버 P1 확정) → P0b → P0c2 → (P3 확정) → P0d. 타임테이블 게이트("P0 머지")는 P0a~P0d 전부다.
